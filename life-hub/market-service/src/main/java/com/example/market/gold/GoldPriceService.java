package com.example.market.gold;

import com.example.market.gold.dto.GoldPriceResponse;
import com.example.market.gold.dto.GoldRealtimePriceResponse;
import com.example.market.market.SinaClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 金价服务（2026-10-30 自 app-service GoldPriceService 迁入；数据源切回金投网）。
 *
 * <p>实时行情主源为金投网：黄金9999（JO_71，CNY/g）+ 伦敦金（JO_92233，USD/oz），
 * 现货与走势同源同口径；新浪财经（上金所现货9999 gds_AU9999 + 伦敦金现 hf_XAU）
 * 作为实时降级源，走势降级用新浪沪金连续 AU0（期现基差约 0.15%）。品牌首饰
 * 金价为金投网独家数据，与实时同一请求顺带返回。USD/CNY 汇率源
 * open.er-api.com 仅作金投缺人民币报价时的兜底换算。响应 DTO 与字段口径不变。</p>
 */
@Service
public class GoldPriceService {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceService.class);

    private static final BigDecimal TROY_OUNCE_GRAMS = new BigDecimal("31.1034768");
    /** 缓存最大有效期：超过后读路径触发懒刷新（单飞），定时兜底任务也按此判定 */
    private static final long CACHE_TTL_MILLIS = 60_000L;
    private static final String DOMESTIC_GOLD_CODE = "JO_71";
    private static final String LONDON_GOLD_CODE = "JO_92233";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int CONNECT_TIMEOUT_MILLIS = 1500;
    private static final int READ_TIMEOUT_MILLIS = 2000;
    private static final Charset GBK = Charset.forName("GBK");

    // 新浪主源：上金所现货 9999 + 伦敦金现（hq.sinajs.cn），
    // 走势用沪金连续 AU0 分钟线/日K（新浪无现货 9999 的历史K线，实测确认）
    private static final String SINA_SPOT_CODE = "gds_AU9999";
    private static final String SINA_LONDON_CODE = "hf_XAU";
    private static final String SINA_FUTURES_SYMBOL = "AU0";
    private static final String SINA_FUTURES_API =
        "https://stock2.finance.sina.com.cn/futures/api/jsonp.php/var%20_=/InnerFuturesNewService.";

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String exchangeRateApiUrl;
    private final String jijinhaoApiBaseUrl;

    /**
     * 缓存快照：volatile 保证读侧原子读取且拿到完整发布对象
     * （响应构建完成后不再变更，可安全跨线程共享，无需读锁）。
     */
    private volatile CachedGoldPrice cachedCurrentPrice;
    /** 强刷串行锁：仅覆盖 /refresh 与 forceRefreshCurrent 的主动回源，不覆盖读路径 */
    private final ReentrantLock refreshLock = new ReentrantLock();
    /** 懒刷新单飞标记：缓存过期瞬间并发请求只放一个进上游，其余直接用当前快照 */
    private final AtomicBoolean lazyRefreshing = new AtomicBoolean(false);

    public GoldPriceService(
        ObjectMapper objectMapper,
        @Value("${finance.gold-price.exchange-rate-api-url:https://open.er-api.com/v6/latest/USD}") String exchangeRateApiUrl,
        @Value("${finance.gold-price.cngold-api-url:https://api.jijinhao.com}") String cngoldApiUrl
    ) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);
        this.restClient = RestClient.builder()
            .requestFactory(requestFactory)
            .build();
        this.exchangeRateApiUrl = exchangeRateApiUrl;
        // Keep the existing config key for backward compatibility.
        this.jijinhaoApiBaseUrl = cngoldApiUrl;
    }

    public GoldPriceResponse getGoldPrice(String range) {
        return getGoldPrice(range, false, true);
    }

    public GoldPriceResponse getGoldPrice(String range, boolean forceRefreshCurrent) {
        return getGoldPrice(range, forceRefreshCurrent, true);
    }

    public GoldPriceResponse getGoldPrice(String range, boolean forceRefreshCurrent, boolean includeChart) {
        if (forceRefreshCurrent) {
            try {
                refreshCache();
            } catch (Exception ex) {
                log.warn("金价强制刷新失败，降级使用缓存: {}", ex.getMessage());
            }
        } else {
            ensureFresh();
        }
        CachedGoldPrice cached = cachedCurrentPrice;
        GoldPriceResponse response = cached == null
            ? emptyGoldPriceResponse()
            : copyResponseWithoutChart(cached.response());
        // 走势图与缓存无共享状态：放在锁外取数，慢请求（最长 8s+）不会阻塞其他金价调用
        response.setChartPoints(includeChart ? fetchChartPoints(normalizeRange(range)) : List.of());
        return response;
    }

    public GoldRealtimePriceResponse getRealtimePrice() {
        ensureFresh();
        CachedGoldPrice cached = cachedCurrentPrice;
        return cached == null
            ? emptyRealtimePriceResponse()
            : toRealtimePrice(copyResponseWithoutChart(cached.response()));
    }

    public GoldRealtimePriceResponse getRealtimePrice(boolean forceRefreshCurrent) throws Exception {
        if (forceRefreshCurrent) {
            refreshCache();
        }
        return getRealtimePrice();
    }

    public BigDecimal getCachedSpotPrice() {
        CachedGoldPrice cached = cachedCurrentPrice;
        if (cached == null) {
            return null;
        }
        GoldPriceResponse.GoldMarketQuote spotGold = cached.response().getSpotGold();
        if (spotGold == null || spotGold.getPrice() == null || spotGold.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return spotGold.getPrice().setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getStrictRealtimeSpotPrice() {
        return getCachedSpotPrice();
    }

    public boolean hasWarmCache() {
        return cachedCurrentPrice != null;
    }

    /**
     * 懒刷新：缓存缺失或超过 TTL 时回源。单飞保证过期瞬间的并发请求
     * 只放一个进上游，失败时保留旧缓存（陈旧但可用的降级语义）。
     */
    private void ensureFresh() {
        CachedGoldPrice cached = cachedCurrentPrice;
        if (cached != null && System.currentTimeMillis() - cached.cachedAt() < CACHE_TTL_MILLIS) {
            return;
        }
        if (!lazyRefreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            refreshCache();
        } catch (Exception ex) {
            log.warn("金价懒刷新失败，继续使用缓存: {}", ex.getMessage());
        } finally {
            lazyRefreshing.set(false);
        }
    }

    /**
     * 强制回源刷新（新浪+金投），供 /refresh 内部口与 forceRefreshCurrent 使用；
     * 并发强刷在此串行执行。读路径不经此方法，不会长期持锁。
     */
    public void refreshCache() throws Exception {
        refreshLock.lock();
        try {
            GoldPriceResponse currentPrice = fetchCurrentPrice();
            cachedCurrentPrice = new CachedGoldPrice(currentPrice, System.currentTimeMillis());
        } finally {
            refreshLock.unlock();
        }
    }

    /**
     * market-service 自身兜底刷新：app-service 停机或预热任务连续失败时，
     * 缓存也不会无限期陈旧。fresh 时为 no-op（ensureFresh 内部判定），不增加上游压力。
     */
    @Scheduled(initialDelay = 15_000L, fixedDelay = 30_000L)
    public void scheduledCacheRefresh() {
        ensureFresh();
    }

    private GoldPriceResponse fetchCurrentPrice() throws Exception {
        // 金投报价始终请求一次：实时主源 + 首饰金价唯一来源，同一请求无额外开销
        JsonNode jijinhaoQuotes = fetchJijinhaoQuotes(jijinhaoQuoteCodes());
        GoldPriceResponse response;
        try {
            response = buildCurrentResponse(this::fetchUsdCny, jijinhaoQuotes);
        } catch (Exception ex) {
            log.warn("金投金价不可用，降级新浪: {}", ex.getMessage());
            response = buildCurrentResponseFromSina(jijinhaoQuotes);
        }
        if (response == null) {
            throw new IllegalStateException("金投与新浪金价源均不可用");
        }
        return copyResponseWithoutChart(response);
    }

    /**
     * 新浪降级路径：gds_AU9999（上金所现货 9999）+ hf_XAU（伦敦金现）。
     * 字段布局（两种代码一致）：f0 最新价 / f4 最高 / f5 最低 / f6 时间 /
     * f7 昨收 / f8 今开 / f12 日期 / f13 名称。解析失败返回 null。
     */
    private GoldPriceResponse buildCurrentResponseFromSina(JsonNode jijinhaoQuotes) {
        Map<String, List<String>> quotes;
        try {
            quotes = SinaClient.batchQuotes(List.of(SINA_SPOT_CODE, SINA_LONDON_CODE));
        } catch (Exception ex) {
            return null;
        }

        List<String> spot = quotes.get(SINA_SPOT_CODE);
        if (spot == null || spot.size() < 14 || isBlank(spot.get(0))) {
            return null;
        }
        BigDecimal domesticPrice = sinaDecimal(spot.get(0));
        BigDecimal domesticPrevClose = sinaDecimal(spot.get(7));
        BigDecimal domesticOpen = sinaDecimal(spot.get(8));
        BigDecimal domesticHigh = sinaDecimal(spot.get(4));
        BigDecimal domesticLow = sinaDecimal(spot.get(5));
        if (domesticPrice == null || domesticPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal domesticChange = domesticPrice.subtract(domesticPrevClose == null ? domesticPrice : domesticPrevClose);
        BigDecimal domesticChangePercent = domesticPrevClose == null || domesticPrevClose.compareTo(BigDecimal.ZERO) <= 0
            ? BigDecimal.ZERO
            : domesticChange.divide(domesticPrevClose, 6, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        LocalDateTime updatedAt = sinaUpdatedAt(spot.get(12), spot.get(6));

        QuoteDelta domesticDelta = resolveQuoteDelta(domesticPrice, domesticChange, domesticChangePercent, domesticOpen);
        domesticChange = domesticDelta.change();
        domesticChangePercent = domesticDelta.changePercent();
        if (domesticOpen == null || domesticOpen.compareTo(BigDecimal.ZERO) <= 0) {
            domesticOpen = domesticPrice.subtract(domesticChange);
        }
        if (domesticHigh == null) {
            domesticHigh = domesticPrice.max(domesticOpen).add(defaultSpotDelta(domesticPrice));
        }
        if (domesticLow == null) {
            domesticLow = domesticPrice.min(domesticOpen).subtract(defaultSpotDelta(domesticPrice));
        }
        BigDecimal domesticBuy = domesticPrice.add(new BigDecimal("0.15"));
        BigDecimal domesticSell = domesticPrice.subtract(new BigDecimal("0.15"));

        GoldPriceResponse response = new GoldPriceResponse();
        response.setSpotGold(marketQuote("黄金9999", "CNY/g", scaleMoney(domesticPrice), scaleMoney(domesticChange), scalePercent(domesticChangePercent), updatedAt));
        response.setStats(stats(domesticOpen, domesticHigh, domesticLow, domesticBuy, domesticSell));
        response.setJewelryPrices(fetchJewelryPrices(jijinhaoQuotes));

        // 伦敦金可选：缺失不影响现货主数据
        List<String> london = quotes.get(SINA_LONDON_CODE);
        if (london != null && london.size() >= 14 && !isBlank(london.get(0))) {
            BigDecimal londonPrice = sinaDecimal(london.get(0));
            BigDecimal londonPrevClose = sinaDecimal(london.get(7));
            BigDecimal londonOpen = sinaDecimal(london.get(8));
            BigDecimal londonHigh = sinaDecimal(london.get(4));
            BigDecimal londonLow = sinaDecimal(london.get(5));
            if (londonPrice != null && londonPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal londonChange = londonPrice.subtract(londonPrevClose == null ? londonPrice : londonPrevClose);
                BigDecimal londonChangePercent = londonPrevClose == null || londonPrevClose.compareTo(BigDecimal.ZERO) <= 0
                    ? BigDecimal.ZERO
                    : londonChange.divide(londonPrevClose, 6, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
                QuoteDelta londonDelta = resolveQuoteDelta(londonPrice, londonChange, londonChangePercent, londonOpen);
                if (londonOpen == null || londonOpen.compareTo(BigDecimal.ZERO) <= 0) {
                    londonOpen = londonPrice.subtract(londonDelta.change());
                }
                if (londonHigh == null) {
                    londonHigh = londonPrice.max(londonOpen).add(defaultLondonDelta(londonPrice));
                }
                if (londonLow == null) {
                    londonLow = londonPrice.min(londonOpen).subtract(defaultLondonDelta(londonPrice));
                }
                response.setLondonGold(marketQuote("伦敦金", "USD/oz", scaleMoney(londonPrice), scaleMoney(londonDelta.change()), scalePercent(londonDelta.changePercent()), sinaUpdatedAt(london.get(12), london.get(6))));
            }
        }
        response.setChartPoints(List.of());
        response.setUpdatedAt(updatedAt);
        response.setSource("新浪黄金9999行情");
        return response;
    }

    private GoldPriceResponse buildCurrentResponse(java.util.function.Supplier<BigDecimal> usdCnySupplier, JsonNode quotes) {
        // 金投主路径（原迁移逻辑保留，口径与迁移前逐行一致）
        JsonNode domesticGoldNode = quotes.path(DOMESTIC_GOLD_CODE);
        JsonNode londonGoldNode = quotes.path(LONDON_GOLD_CODE);
        BigDecimal domesticPrice = firstDecimal(domesticGoldNode, "price", "last", "last_price", "close", "q63", "q2");
        BigDecimal domesticOpen = firstDecimal(domesticGoldNode, "open_price", "open", "openPrice", "q1");
        BigDecimal domesticHigh = firstDecimal(domesticGoldNode, "high_price", "high", "highPrice", "q3");
        BigDecimal domesticLow = firstDecimal(domesticGoldNode, "low_price", "low", "lowPrice", "q4");
        BigDecimal domesticBuy = firstDecimal(domesticGoldNode, "q5", "buy", "bid");
        BigDecimal domesticSell = firstDecimal(domesticGoldNode, "q6", "sell", "ask");
        BigDecimal domesticChange = firstDecimal(domesticGoldNode, "change", "ch", "change_price", "changePrice", "q70");
        BigDecimal domesticChangePercent = firstDecimal(domesticGoldNode, "change_percent", "chp", "change_margin", "changePercent", "q80");

        BigDecimal londonPrice = firstDecimal(londonGoldNode, "price", "last", "last_price", "close", "ask");
        if (londonPrice == null) {
            londonPrice = firstDecimal(londonGoldNode, "q63");
        }
        BigDecimal londonOpen = firstDecimal(londonGoldNode, "open_price", "open", "openPrice");
        if (londonOpen == null) {
            londonOpen = firstDecimal(londonGoldNode, "q1");
        }
        BigDecimal londonHigh = firstDecimal(londonGoldNode, "high_price", "high", "highPrice");
        if (londonHigh == null) {
            londonHigh = firstDecimal(londonGoldNode, "q3");
        }
        BigDecimal londonLow = firstDecimal(londonGoldNode, "low_price", "low", "lowPrice");
        if (londonLow == null) {
            londonLow = firstDecimal(londonGoldNode, "q4");
        }
        BigDecimal londonChange = firstDecimal(londonGoldNode, "change", "ch", "change_price", "changePrice");
        if (londonChange == null) {
            londonChange = firstDecimal(londonGoldNode, "q70");
        }
        BigDecimal londonChangePercent = firstDecimal(londonGoldNode, "change_percent", "chp", "change_margin", "changePercent");
        if (londonChangePercent == null) {
            londonChangePercent = firstDecimal(londonGoldNode, "q80");
        }

        if ((domesticPrice == null || domesticPrice.compareTo(BigDecimal.ZERO) <= 0)
            && (londonPrice == null || londonPrice.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalStateException("金价数据缺少价格字段");
        }

        if ((domesticPrice == null || domesticPrice.compareTo(BigDecimal.ZERO) <= 0)
            && londonPrice != null && londonPrice.compareTo(BigDecimal.ZERO) > 0) {
            // 仅在金投缺人民币报价时才请求汇率接口（USD/oz → CNY/g 兜底换算）
            BigDecimal usdCny = usdCnySupplier.get();
            if (usdCny != null && usdCny.compareTo(BigDecimal.ZERO) > 0) {
                domesticPrice = usdOzToCnyGram(londonPrice, usdCny);
                domesticChange = londonChange == null ? null : usdOzToCnyGram(londonChange, usdCny);
                domesticChangePercent = domesticChangePercent == null ? londonChangePercent : domesticChangePercent;
            }
        }
        if (domesticPrice == null || domesticPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("人民币金价数据缺少价格字段");
        }

        QuoteDelta domesticDelta = resolveQuoteDelta(domesticPrice, domesticChange, domesticChangePercent, domesticOpen);
        domesticChange = domesticDelta.change();
        domesticChangePercent = domesticDelta.changePercent();
        if (domesticOpen == null || domesticOpen.compareTo(BigDecimal.ZERO) <= 0) {
            domesticOpen = domesticChange == null
                ? domesticPrice.subtract(defaultSpotDelta(domesticPrice))
                : domesticPrice.subtract(domesticChange);
        }
        if (domesticHigh == null) {
            domesticHigh = domesticPrice.max(domesticOpen).add(defaultSpotDelta(domesticPrice));
        }
        if (domesticLow == null) {
            domesticLow = domesticPrice.min(domesticOpen).subtract(defaultSpotDelta(domesticPrice));
        }
        if (domesticBuy == null || domesticBuy.compareTo(BigDecimal.ZERO) <= 0) {
            domesticBuy = domesticPrice.add(new BigDecimal("0.15"));
        }
        if (domesticSell == null || domesticSell.compareTo(BigDecimal.ZERO) <= 0) {
            domesticSell = domesticPrice.subtract(new BigDecimal("0.15"));
        }

        if (londonPrice != null && londonPrice.compareTo(BigDecimal.ZERO) > 0) {
            QuoteDelta londonDelta = resolveQuoteDelta(londonPrice, londonChange, londonChangePercent, londonOpen);
            londonChange = londonDelta.change();
            londonChangePercent = londonDelta.changePercent();
            if (londonOpen == null || londonOpen.compareTo(BigDecimal.ZERO) <= 0) {
                londonOpen = londonChange == null
                    ? londonPrice.subtract(defaultLondonDelta(londonPrice))
                    : londonPrice.subtract(londonChange);
            }
        }
        if (londonPrice != null && londonPrice.compareTo(BigDecimal.ZERO) > 0 && londonHigh == null) {
            londonHigh = londonPrice.max(londonOpen).add(defaultLondonDelta(londonPrice));
        }
        if (londonPrice != null && londonPrice.compareTo(BigDecimal.ZERO) > 0 && londonLow == null) {
            londonLow = londonPrice.min(londonOpen).subtract(defaultLondonDelta(londonPrice));
        }

        LocalDateTime updatedAt = extractUpdatedAt(domesticGoldNode);
        LocalDateTime londonUpdatedAt = extractUpdatedAt(londonGoldNode);

        GoldPriceResponse response = new GoldPriceResponse();
        response.setSpotGold(marketQuote("黄金9999", "CNY/g", scaleMoney(domesticPrice), scaleMoney(domesticChange), scalePercent(domesticChangePercent), updatedAt));
        if (londonPrice != null && londonPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal normalizedLondonChange = londonChange == null ? BigDecimal.ZERO : londonChange;
            response.setLondonGold(marketQuote("伦敦金", "USD/oz", scaleMoney(londonPrice), scaleMoney(normalizedLondonChange), scalePercent(londonChangePercent), londonUpdatedAt));
        }
        response.setStats(stats(domesticOpen, domesticHigh, domesticLow, domesticBuy, domesticSell));
        response.setJewelryPrices(fetchJewelryPrices(quotes));
        response.setChartPoints(List.of());
        response.setUpdatedAt(updatedAt);
        response.setSource("金投网黄金9999行情");
        return response;
    }

    private JsonNode fetchJson(String url) throws Exception {
        String body = restClient.get()
            .uri(url)
            .retrieve()
            .body(String.class);
        return objectMapper.readTree(body);
    }

    private JsonNode fetchJijinhaoQuotes(String codes) throws Exception {
        String cacheBust = String.valueOf(System.currentTimeMillis());
        String body = restClient.get()
            .uri(jijinhaoApiBaseUrl + "/quoteCenter/realTime.htm?codes=" + codes + "&_=" + cacheBust)
            .header("Cache-Control", "no-cache, no-store, max-age=0")
            .header("Pragma", "no-cache")
            .header("Referer", "https://quote.cngold.org/gjs/gjhj_xhhj.html?key=au")
            .header("User-Agent", "Mozilla/5.0")
            .retrieve()
            .body(String.class);
        int start = body == null ? -1 : body.indexOf('{');
        int end = body == null ? -1 : body.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("金投网行情数据格式错误");
        }
        JsonNode root = objectMapper.readTree(body.substring(start, end + 1));
        if (root.has("quote_json") && root.path("quote_json").isObject()) {
            return root.path("quote_json");
        }
        if (root.has(DOMESTIC_GOLD_CODE) || root.has(LONDON_GOLD_CODE)) {
            return root;
        }
        throw new IllegalStateException("金投网行情缺少黄金报价数据");
    }

    private String jijinhaoQuoteCodes() {
        // 首饰 8 家为金投独家；JO_71/JO_92233 为实时主源，同一请求无额外开销
        return DOMESTIC_GOLD_CODE + "," + LONDON_GOLD_CODE
            + ",JO_42657,JO_42660,JO_42625,JO_42634,JO_42653,JO_42646,JO_52678,JO_42638";
    }

    private BigDecimal fetchUsdCny() {
        try {
            JsonNode exchangeNode = fetchJson(exchangeRateApiUrl);
            BigDecimal rate = decimalAt(exchangeNode, "rates", "CNY");
            return rate != null && rate.compareTo(BigDecimal.ZERO) > 0 ? rate : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private GoldPriceResponse.GoldMarketQuote marketQuote(
        String name,
        String unit,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changePercent,
        LocalDateTime updatedAt
    ) {
        GoldPriceResponse.GoldMarketQuote quote = new GoldPriceResponse.GoldMarketQuote();
        quote.setName(name);
        quote.setUnit(unit);
        quote.setPrice(price);
        quote.setChange(change);
        quote.setChangePercent(changePercent);
        quote.setUpdatedAt(updatedAt);
        return quote;
    }

    private QuoteDelta resolveQuoteDelta(
        BigDecimal price,
        BigDecimal change,
        BigDecimal changePercent,
        BigDecimal fallbackOpen
    ) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return new QuoteDelta(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        if (change != null) {
            BigDecimal percent = changePercent;
            if (percent == null) {
                BigDecimal referencePrice = price.subtract(change);
                if (referencePrice.compareTo(BigDecimal.ZERO) > 0) {
                    percent = change.divide(referencePrice, 6, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                }
            }
            return new QuoteDelta(change, percent == null ? BigDecimal.ZERO : percent);
        }

        if (fallbackOpen != null && fallbackOpen.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal resolvedChange = price.subtract(fallbackOpen);
            BigDecimal percent = changePercent == null
                ? resolvedChange.divide(fallbackOpen, 6, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : changePercent;
            return new QuoteDelta(resolvedChange, percent);
        }

        return new QuoteDelta(BigDecimal.ZERO, changePercent == null ? BigDecimal.ZERO : changePercent);
    }

    private GoldPriceResponse.GoldMarketStats stats(
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal buyPrice,
        BigDecimal sellPrice
    ) {
        GoldPriceResponse.GoldMarketStats stats = new GoldPriceResponse.GoldMarketStats();
        stats.setOpenPrice(openPrice);
        stats.setHighPrice(highPrice);
        stats.setLowPrice(lowPrice);
        stats.setBuyPrice(buyPrice.setScale(2, RoundingMode.HALF_UP));
        stats.setSellPrice(sellPrice.setScale(2, RoundingMode.HALF_UP));
        stats.setUnit("CNY/g");
        return stats;
    }

    private List<GoldPriceResponse.JewelryGoldPrice> fetchJewelryPrices(JsonNode quotes) {
        List<JewelryQuoteCode> codes = List.of(
            new JewelryQuoteCode("老凤祥", "JO_42657"),
            new JewelryQuoteCode("周大福", "JO_42660"),
            new JewelryQuoteCode("周生生", "JO_42625"),
            new JewelryQuoteCode("老庙黄金", "JO_42634"),
            new JewelryQuoteCode("周六福", "JO_42653"),
            new JewelryQuoteCode("六福珠宝", "JO_42646"),
            new JewelryQuoteCode("周大生", "JO_52678"),
            new JewelryQuoteCode("菜百", "JO_42638")
        );

        List<GoldPriceResponse.JewelryGoldPrice> prices = new ArrayList<>();
        for (JewelryQuoteCode code : codes) {
            JsonNode quote = quotes.path(code.code());
            BigDecimal price = firstDecimal(quote, "q63", "q1");
            if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                prices.add(jewelryPrice(code.brandName(), price, extractUpdatedAt(quote)));
            }
        }
        return prices;
    }

    private GoldPriceResponse.JewelryGoldPrice jewelryPrice(String brandName, BigDecimal price, LocalDateTime updatedAt) {
        GoldPriceResponse.JewelryGoldPrice item = new GoldPriceResponse.JewelryGoldPrice();
        item.setBrandName(brandName);
        item.setPrice(price.setScale(2, RoundingMode.HALF_UP));
        item.setUnit("CNY/g");
        item.setUpdatedAt(updatedAt);
        return item;
    }

    private List<GoldPriceResponse.GoldChartPoint> fetchChartPoints(String range) {
        // 走势主源金投 history（与现货 9999 同源同口径）；新浪 AU0 仅作降级
        List<GoldPriceResponse.GoldChartPoint> fallback = fetchUnifiedJijinhaoPoints(range);
        if (!fallback.isEmpty()) {
            return compactChartPoints(fallback, range);
        }
        try {
            List<GoldPriceResponse.GoldChartPoint> points = fetchSinaChartPoints(range);
            if (!points.isEmpty()) {
                return compactChartPoints(points, range);
            }
        } catch (Exception ignored) {
            // 金投与新浪K线均不可用时返回空
        }
        return List.of();
    }

    /**
     * 新浪降级走势：沪金连续 AU0。1d 用 1 分钟线，7d/30d/3m 用 60 分钟线，
     * 1y/3y 用日K（2008 年至今全量）。与现货 9999 同币种同单位，存在期现基差。
     */
    private List<GoldPriceResponse.GoldChartPoint> fetchSinaChartPoints(String range) {
        return switch (range) {
            case "1d" -> sinaFuturesChartPoints("getFewMinLine", "type=1", range);
            case "7d", "30d", "3m" -> sinaFuturesChartPoints("getFewMinLine", "type=60", range);
            case "1y", "3y" -> sinaFuturesChartPoints("getDailyKLine", null, range);
            default -> List.of();
        };
    }

    private List<GoldPriceResponse.GoldChartPoint> sinaFuturesChartPoints(String method, String query, String range) {
        try {
            String url = SINA_FUTURES_API + method + "?symbol=" + SINA_FUTURES_SYMBOL
                + (query == null ? "" : "&" + query);
            String text = SinaClient.getText(url, GBK, 8);
            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            if (start < 0 || end <= start) {
                return List.of();
            }
            JsonNode bars = objectMapper.readTree(text.substring(start, end + 1));
            if (!bars.isArray() || bars.isEmpty()) {
                return List.of();
            }

            long minTimestamp = minimumTimestampForRange(range);
            List<GoldPriceResponse.GoldChartPoint> points = new ArrayList<>();
            for (JsonNode bar : bars) {
                BigDecimal close = decimalAt(bar, "c");
                LocalDateTime time = parseSinaBarTime(textOf(bar, "d"));
                if (close == null || close.compareTo(BigDecimal.ZERO) <= 0 || time == null) {
                    continue;
                }
                if (time.atZone(DEFAULT_ZONE).toInstant().toEpochMilli() < minTimestamp) {
                    continue;
                }
                GoldPriceResponse.GoldChartPoint point = new GoldPriceResponse.GoldChartPoint();
                point.setLabel(formatChartLabel(time, range));
                point.setPrice(scaleMoney(close));
                points.add(point);
            }
            return points;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<GoldPriceResponse.GoldChartPoint> fetchUnifiedJijinhaoPoints(String range) {
        try {
            return switch (range) {
                case "1d" -> fetchJijinhaoHistoryPoints(1, range, 500);
                case "7d" -> fetchJijinhaoHistoryPoints(2, range);
                case "30d" -> fetchJijinhaoHistoryPoints(3, range);
                case "3m" -> fetchJijinhaoHistoryPoints(3, range, 120);
                case "1y" -> fetchJijinhaoHistoryPoints(3, range, 400);
                case "3y" -> fetchJijinhaoHistoryPoints(5, range, 40);
                default -> fetchJijinhaoHistoryPoints(6, range);
            };
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<GoldPriceResponse.GoldChartPoint> fetchJijinhaoHistoryPoints(int style, String range) {
        return fetchJijinhaoHistoryPoints(style, range, 200);
    }

    private List<GoldPriceResponse.GoldChartPoint> fetchJijinhaoHistoryPoints(int style, String range, int pageSize) {
        try {
            String body = fetchJijinhaoText(jijinhaoChartUrl("history.htm") + "?code=" + DOMESTIC_GOLD_CODE + "&style=" + style + "&pageSize=" + pageSize);
            JsonNode root = extractJavascriptObject(body, "KLC_KL");
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return List.of();
            }

            long minTimestamp = minimumTimestampForRange(range);
            List<GoldPriceResponse.GoldChartPoint> points = new ArrayList<>();
            for (JsonNode item : data) {
                BigDecimal close = decimalAt(item, "close");
                Long timestamp = firstLong(item, "date", "time");
                if (close == null || close.compareTo(BigDecimal.ZERO) <= 0 || timestamp == null || timestamp < minTimestamp) {
                    continue;
                }

                LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), DEFAULT_ZONE);
                GoldPriceResponse.GoldChartPoint point = new GoldPriceResponse.GoldChartPoint();
                point.setLabel(formatChartLabel(time, range));
                point.setPrice(scaleMoney(close));
                points.add(point);
            }
            return points;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private LocalDateTime parseSinaBarTime(String dateText) {
        if (dateText == null || dateText.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateText.trim().replace(' ', 'T'));
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return java.time.LocalDate.parse(dateText.trim()).atStartOfDay();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String formatChartLabel(LocalDateTime time, String range) {
        return switch (range) {
            case "1d" -> String.format("%02d:%02d", time.getHour(), time.getMinute());
            case "7d" -> String.format("%d/%d %02d:%02d", time.getMonthValue(), time.getDayOfMonth(), time.getHour(), time.getMinute());
            case "1y" -> time.getMonthValue() + "月";
            case "3y" -> time.getYear() + "/" + time.getMonthValue();
            default -> time.getMonthValue() + "/" + time.getDayOfMonth();
        };
    }

    private List<GoldPriceResponse.GoldChartPoint> compactChartPoints(
        List<GoldPriceResponse.GoldChartPoint> points,
        String range
    ) {
        if ("1d".equals(range) || "7d".equals(range)) {
            return points;
        }

        int maxCount = switch (range) {
            case "30d" -> 48;
            case "3m" -> 60;
            case "3y" -> 40;
            default -> 52;
        };
        if (points.size() <= maxCount) {
            return points;
        }

        List<GoldPriceResponse.GoldChartPoint> result = new ArrayList<>();
        double step = (double) (points.size() - 1) / (maxCount - 1);
        for (int index = 0; index < maxCount; index++) {
            result.add(points.get((int) Math.round(index * step)));
        }
        return result;
    }

    private String jijinhaoChartUrl(String endpoint) {
        return jijinhaoApiBaseUrl + "/sQuoteCenter/" + endpoint;
    }

    private String fetchJijinhaoText(String url) {
        String separator = url.contains("?") ? "&" : "?";
        return restClient.get()
            .uri(url + separator + "_=" + System.currentTimeMillis())
            .header("Cache-Control", "no-cache, no-store, max-age=0")
            .header("Pragma", "no-cache")
            .header("Referer", "https://quote.cngold.org/gjs/gjhj_xhhj.html?key=au")
            .header("User-Agent", "Mozilla/5.0")
            .retrieve()
            .body(String.class);
    }

    private JsonNode extractJavascriptObject(String body, String variableName) throws Exception {
        if (body == null) {
            throw new IllegalStateException("金投图表数据为空");
        }

        int variableIndex = body.indexOf(variableName);
        int start = variableIndex < 0 ? -1 : body.indexOf('{', variableIndex);
        int end = body.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("金投图表数据格式错误");
        }
        return objectMapper.readTree(body.substring(start, end + 1));
    }

    private long minimumTimestampForRange(String range) {
        long now = System.currentTimeMillis();
        return switch (range) {
            case "1d" -> LocalDateTime.now(DEFAULT_ZONE)
                .toLocalDate()
                .atStartOfDay(DEFAULT_ZONE)
                .toInstant()
                .toEpochMilli();
            case "7d" -> now - 7L * 24 * 60 * 60 * 1000;
            case "30d" -> now - 30L * 24 * 60 * 60 * 1000;
            case "3m" -> now - 90L * 24 * 60 * 60 * 1000;
            case "1y" -> now - 365L * 24 * 60 * 60 * 1000;
            case "3y" -> now - 3L * 365 * 24 * 60 * 60 * 1000;
            default -> 0L;
        };
    }

    private GoldPriceResponse copyResponseWithoutChart(GoldPriceResponse source) {
        GoldPriceResponse target = new GoldPriceResponse();
        target.setSpotGold(source.getSpotGold());
        target.setLondonGold(source.getLondonGold());
        target.setStats(source.getStats());
        target.setJewelryPrices(source.getJewelryPrices());
        target.setChartPoints(List.of());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setSource(source.getSource());
        return target;
    }

    private GoldRealtimePriceResponse toRealtimePrice(GoldPriceResponse source) {
        GoldRealtimePriceResponse target = new GoldRealtimePriceResponse();
        GoldPriceResponse.GoldMarketQuote spotGold = source.getSpotGold();
        if (spotGold != null) {
            target.setName(spotGold.getName());
            target.setUnit(spotGold.getUnit());
            target.setPrice(spotGold.getPrice());
            target.setChange(spotGold.getChange());
            target.setChangePercent(spotGold.getChangePercent());
            target.setUpdatedAt(spotGold.getUpdatedAt());
        } else {
            target.setUpdatedAt(source.getUpdatedAt());
        }
        target.setSource(source.getSource());
        return target;
    }

    private GoldPriceResponse emptyGoldPriceResponse() {
        GoldPriceResponse response = new GoldPriceResponse();
        response.setJewelryPrices(List.of());
        response.setChartPoints(List.of());
        return response;
    }

    private GoldRealtimePriceResponse emptyRealtimePriceResponse() {
        return new GoldRealtimePriceResponse();
    }

    private LocalDateTime extractUpdatedAt(JsonNode node) {
        Long timestamp = firstLong(node, "timestamp", "time", "updated_at", "update_time");
        if (timestamp == null) {
            return LocalDateTime.now(DEFAULT_ZONE);
        }
        if (timestamp < 10_000_000_000L) {
            timestamp = timestamp * 1000;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), DEFAULT_ZONE);
    }

    private LocalDateTime sinaUpdatedAt(String dateText, String timeText) {
        if (dateText != null && !dateText.isBlank() && timeText != null && !timeText.isBlank()) {
            try {
                return LocalDateTime.parse(dateText.trim() + "T" + timeText.trim());
            } catch (Exception ignored) {
                // fall through
            }
        }
        return LocalDateTime.now(DEFAULT_ZONE);
    }

    private BigDecimal sinaDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String textOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private BigDecimal firstDecimal(JsonNode node, String... names) {
        for (String name : names) {
            BigDecimal value = decimalAt(node, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long firstLong(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isNumber()) {
                return value.asLong();
            }
            if (value != null && value.isTextual()) {
                try {
                    return Long.parseLong(value.asText());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private BigDecimal decimalAt(JsonNode node, String... path) {
        JsonNode current = node;
        for (String part : path) {
            if (current == null) {
                return null;
            }
            current = current.get(part);
        }
        if (current == null || current.isNull()) {
            return null;
        }
        if (current.isNumber()) {
            return current.decimalValue();
        }
        if (current.isTextual()) {
            try {
                return new BigDecimal(current.asText().replace("%", "").trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal usdOzToCnyGram(BigDecimal value, BigDecimal usdCny) {
        return value.multiply(usdCny).divide(TROY_OUNCE_GRAMS, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scalePercent(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultLondonDelta(BigDecimal londonPrice) {
        return londonPrice.multiply(new BigDecimal("0.0025")).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultSpotDelta(BigDecimal spotPrice) {
        return spotPrice.multiply(new BigDecimal("0.0025")).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeRange(String range) {
        if ("7d".equals(range)
            || "30d".equals(range)
            || "3m".equals(range)
            || "1y".equals(range)
            || "3y".equals(range)) {
            return range;
        }
        return "1d";
    }

    private record JewelryQuoteCode(String brandName, String code) {
    }

    private record QuoteDelta(BigDecimal change, BigDecimal changePercent) {
    }

    private record CachedGoldPrice(GoldPriceResponse response, long cachedAt) {
    }
}
