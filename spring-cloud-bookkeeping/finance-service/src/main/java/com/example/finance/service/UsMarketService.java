package com.example.finance.service;

import com.example.finance.dto.UsMarketResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class UsMarketService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final ZoneId NEW_YORK_ZONE = ZoneId.of("America/New_York");
    private static final Charset GBK = Charset.forName("GBK");
    private static final int AUTO_REFRESH_INTERVAL_SECONDS = 60;
    private static final long CACHE_MILLIS = AUTO_REFRESH_INTERVAL_SECONDS * 1000L;
    private static final int CONNECT_TIMEOUT_MILLIS = 1500;
    private static final int READ_TIMEOUT_MILLIS = 2500;
    private static final String SINA_SPX_QUOTE_URL = "https://hq.sinajs.cn/list=gb_$inx";
    private static final String NFIN_NDX_CHART_URL = "https://api.nfin.dev/v1/quote/NDX/chart?assetclass=index";
    private static final String SPX_TREND_IMAGE_URL = "https://webquotepic.eastmoney.com/GetPic.aspx?nid=100.SPX&imageType=rs";
    private static final String SPX_KLINE_IMAGE_URL = "https://webquoteklinepic.eastmoney.com/GetPic.aspx?nid=100.SPX&imageType=ks";
    private static final String NDX_TREND_IMAGE_URL = "https://webquotepic.eastmoney.com/GetPic.aspx?nid=100.NDX&imageType=rs";
    private static final String NDX_KLINE_IMAGE_URL = "https://webquoteklinepic.eastmoney.com/GetPic.aspx?nid=100.NDX&imageType=ks";
    private static final DateTimeFormatter NFIN_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH);

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Map<String, CachedQuote> cachedQuotes = new HashMap<>();

    public UsMarketService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);
        this.restClient = RestClient.builder()
            .requestFactory(requestFactory)
            .build();
    }

    public synchronized UsMarketResponse getOverview() {
        List<UsMarketResponse.UsMarketIndexQuote> indices = new ArrayList<>();
        indices.add(resolveQuote("SPX", this::fetchSp500Quote));
        indices.add(resolveQuote("NDX", this::fetchNasdaq100Quote));

        UsMarketResponse response = new UsMarketResponse();
        response.setIndices(indices);
        response.setUpdatedAt(indices.stream()
            .map(UsMarketResponse.UsMarketIndexQuote::getUpdatedAt)
            .filter(java.util.Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(LocalDateTime.now(DEFAULT_ZONE)));
        response.setAutoRefreshIntervalSeconds(AUTO_REFRESH_INTERVAL_SECONDS);
        response.setSource("新浪财经国际指数 + Nasdaq 图表镜像 + 东方财富趋势图");
        return response;
    }

    private UsMarketResponse.UsMarketIndexQuote resolveQuote(
        String code,
        QuoteSupplier supplier
    ) {
        long now = System.currentTimeMillis();
        CachedQuote cached = cachedQuotes.get(code);

        if (cached != null && now - cached.cachedAt() < CACHE_MILLIS) {
            return copyQuote(cached.quote(), false);
        }

        try {
            UsMarketResponse.UsMarketIndexQuote quote = supplier.get();
            cachedQuotes.put(code, new CachedQuote(quote, now));
            return copyQuote(quote, false);
        } catch (Exception ex) {
            if (cached != null) {
                return copyQuote(cached.quote(), true);
            }
            throw new IllegalStateException("美股指数数据获取失败", ex);
        }
    }

    private UsMarketResponse.UsMarketIndexQuote fetchSp500Quote() throws Exception {
        byte[] body = restClient.get()
            .uri(SINA_SPX_QUOTE_URL)
            .header("Referer", "https://finance.sina.com.cn")
            .header("User-Agent", "Mozilla/5.0")
            .retrieve()
            .body(byte[].class);

        String decoded = new String(body == null ? new byte[0] : body, GBK);
        int start = decoded.indexOf('"');
        int end = decoded.lastIndexOf('"');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("新浪标普500行情格式错误");
        }

        String[] fields = decoded.substring(start + 1, end).split(",");
        if (fields.length < 27) {
            throw new IllegalStateException("新浪标普500行情字段不足");
        }

        BigDecimal price = decimal(fields[1]);
        BigDecimal changePercent = decimal(fields[2]);
        BigDecimal change = decimal(fields[4]);
        BigDecimal openPrice = decimal(fields[5]);
        BigDecimal highPrice = decimal(fields[6]);
        BigDecimal lowPrice = decimal(fields[7]);
        BigDecimal previousClose = decimal(fields[26]);
        if (price == null || change == null || changePercent == null) {
            throw new IllegalStateException("新浪标普500行情字段无效");
        }

        UsMarketResponse.UsMarketIndexQuote quote = baseQuote(
            "SPX",
            "标普500",
            "S&P 500",
            SPX_TREND_IMAGE_URL,
            SPX_KLINE_IMAGE_URL,
            "新浪财经国际指数"
        );
        quote.setPrice(scaleMoney(price));
        quote.setChange(scaleSignedMoney(change));
        quote.setChangePercent(scaleSignedPercent(changePercent));
        quote.setPreviousClose(scaleMoney(previousClose != null ? previousClose : price.subtract(change)));
        quote.setOpenPrice(scaleMoney(openPrice));
        quote.setHighPrice(scaleMoney(highPrice));
        quote.setLowPrice(scaleMoney(lowPrice));
        quote.setUpdatedAt(LocalDateTime.now(DEFAULT_ZONE));
        quote.setMarketTimeLabel(blankToNull(fields[25]));
        return quote;
    }

    private UsMarketResponse.UsMarketIndexQuote fetchNasdaq100Quote() throws Exception {
        JsonNode root = fetchJson(NFIN_NDX_CHART_URL);
        JsonNode dataNode = root.path("data").path("data");
        if (dataNode.isMissingNode() || dataNode.isNull()) {
            throw new IllegalStateException("纳指100行情缺少数据");
        }

        BigDecimal price = decimal(dataNode.path("lastSalePrice").asText());
        BigDecimal change = decimal(dataNode.path("netChange").asText());
        BigDecimal changePercent = percent(dataNode.path("percentageChange").asText());
        BigDecimal previousClose = decimal(dataNode.path("previousClose").asText());
        if (price == null || change == null || changePercent == null) {
            throw new IllegalStateException("纳指100行情字段无效");
        }

        BigDecimal openPrice = null;
        BigDecimal highPrice = null;
        BigDecimal lowPrice = null;
        JsonNode chartNode = dataNode.path("chart");
        if (chartNode.isArray() && !chartNode.isEmpty()) {
            for (JsonNode point : chartNode) {
                BigDecimal value = point.path("y").isNumber() ? point.path("y").decimalValue() : decimal(point.path("y").asText());
                if (value == null) {
                    continue;
                }
                if (openPrice == null) {
                    openPrice = value;
                }
                highPrice = highPrice == null ? value : highPrice.max(value);
                lowPrice = lowPrice == null ? value : lowPrice.min(value);
            }
        }

        UsMarketResponse.UsMarketIndexQuote quote = baseQuote(
            "NDX",
            "纳指100",
            "NASDAQ-100",
            NDX_TREND_IMAGE_URL,
            NDX_KLINE_IMAGE_URL,
            "Nasdaq 图表镜像"
        );
        quote.setPrice(scaleMoney(price));
        quote.setChange(scaleSignedMoney(change));
        quote.setChangePercent(scaleSignedPercent(changePercent));
        quote.setPreviousClose(scaleMoney(previousClose != null ? previousClose : price.subtract(change)));
        quote.setOpenPrice(scaleMoney(openPrice));
        quote.setHighPrice(scaleMoney(highPrice));
        quote.setLowPrice(scaleMoney(lowPrice));
        quote.setMarketTimeLabel(blankToNull(dataNode.path("timeAsOf").asText()));
        quote.setUpdatedAt(parseMarketTime(dataNode.path("timeAsOf").asText()));
        return quote;
    }

    private UsMarketResponse.UsMarketIndexQuote baseQuote(
        String code,
        String name,
        String alias,
        String trendImageUrl,
        String klineImageUrl,
        String source
    ) {
        UsMarketResponse.UsMarketIndexQuote quote = new UsMarketResponse.UsMarketIndexQuote();
        quote.setCode(code);
        quote.setName(name);
        quote.setAlias(alias);
        quote.setTrendImageUrl(trendImageUrl);
        quote.setKlineImageUrl(klineImageUrl);
        quote.setSource(source);
        return quote;
    }

    private JsonNode fetchJson(String url) throws Exception {
        String body = restClient.get()
            .uri(url)
            .header("User-Agent", "Mozilla/5.0")
            .retrieve()
            .body(String.class);
        return objectMapper.readTree(body);
    }

    private LocalDateTime parseMarketTime(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return LocalDateTime.now(DEFAULT_ZONE);
        }
        try {
            String withoutZone = normalized.replace(" ET", "");
            LocalDateTime localDateTime = LocalDateTime.parse(withoutZone, NFIN_TIME_FORMATTER);
            ZonedDateTime nyTime = localDateTime.atZone(NEW_YORK_ZONE);
            return nyTime.withZoneSameInstant(DEFAULT_ZONE).toLocalDateTime();
        } catch (Exception ex) {
            return LocalDateTime.now(DEFAULT_ZONE);
        }
    }

    private UsMarketResponse.UsMarketIndexQuote copyQuote(
        UsMarketResponse.UsMarketIndexQuote source,
        boolean stale
    ) {
        UsMarketResponse.UsMarketIndexQuote target = new UsMarketResponse.UsMarketIndexQuote();
        target.setCode(source.getCode());
        target.setName(source.getName());
        target.setAlias(source.getAlias());
        target.setPrice(source.getPrice());
        target.setChange(source.getChange());
        target.setChangePercent(source.getChangePercent());
        target.setPreviousClose(source.getPreviousClose());
        target.setOpenPrice(source.getOpenPrice());
        target.setHighPrice(source.getHighPrice());
        target.setLowPrice(source.getLowPrice());
        target.setMarketTimeLabel(source.getMarketTimeLabel());
        target.setTrendImageUrl(source.getTrendImageUrl());
        target.setKlineImageUrl(source.getKlineImageUrl());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setSource(source.getSource());
        target.setStale(stale);
        return target;
    }

    private BigDecimal decimal(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.replace(",", "").replace("%", "");
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal percent(String value) {
        return decimal(value);
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleSignedMoney(BigDecimal value) {
        return scaleMoney(value);
    }

    private BigDecimal scaleSignedPercent(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @FunctionalInterface
    private interface QuoteSupplier {
        UsMarketResponse.UsMarketIndexQuote get() throws Exception;
    }

    private record CachedQuote(UsMarketResponse.UsMarketIndexQuote quote, long cachedAt) {
    }
}
