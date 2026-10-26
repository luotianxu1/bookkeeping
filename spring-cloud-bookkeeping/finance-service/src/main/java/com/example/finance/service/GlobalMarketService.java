package com.example.finance.service;

import com.example.finance.dto.GlobalMarketKLineItemResponse;
import com.example.finance.dto.GlobalMarketQuoteResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全球行情服务：以东方财富免费行情接口为统一主源（报价/日K/周K/月K/全球品种分钟K），
 * 浏览器直连会因 Referer 校验被拒，统一经服务端代理。
 * 按品种保留补充数据源（均为免费、无 key、无试用期，仅按回看深度与历史长度择优）：
 * - 新浪手机版分钟K：商品期货（hf_ 品种）与 A 股指数，回看约 1023 根，深于东财（约一周~两月）；
 * - 腾讯：中证红利的日K/分钟K（东财日K仅 2023-12 起，历史不足以支撑年K）；
 * - 美联储 FRED：美债 10 年收益率官方日度数据（1962 年起，远长于东财的 2023-05 起）。
 */
@Service
public class GlobalMarketService {

    private static final Logger log = LoggerFactory.getLogger(GlobalMarketService.class);

    /** 品种 -> 东财 secid（市场.代码）：全部品种报价与多数品种 K 线的统一映射 */
    private static final Map<String, String> EM_SECID = Map.ofEntries(
        Map.entry("SH000001", "1.000001"),
        Map.entry("CSI500", "1.000905"),
        Map.entry("CSI_DIVIDEND", "1.000922"),
        Map.entry("NDX", "100.NDX"),
        Map.entry("NDX100", "100.NDX100"),
        Map.entry("SPX", "100.SPX"),
        Map.entry("N225", "100.N225"),
        Map.entry("KS11", "100.KS11"),
        Map.entry("DINIW", "100.UDI"),
        Map.entry("hf_XAU", "122.XAU"),
        Map.entry("hf_XAG", "122.XAG"),
        Map.entry("hf_OIL", "112.B00Y"),
        Map.entry("US10Y", "171.US10Y")
    );

    /** 东财实时接口主机（批量报价），主域名被限流时切换备用域名 */
    private static final String EM_API_HOST = "https://push2delay.eastmoney.com";
    private static final String EM_API_HOST_FALLBACK = "https://push2.eastmoney.com";
    /** 东财历史K线专用主机：push2delay/push2 上请求同路径会返回空 klines，必须走 push2his */
    private static final String EM_KLINE_API_HOST = "https://push2his.eastmoney.com";
    /** 东财批量实时报价接口：一次请求拉取全部品种，降低触发限流的风险；fltt=2 时数值直接为小数 */
    private static final String EM_BATCH_QUOTE_API_PATH = "/api/qt/ulist.np/get?secids=";
    private static final String EM_BATCH_QUOTE_API_PARAMS = "&fields=f2,f12,f13,f14,f15,f16,f17,f18,f124&fltt=2";
    private static final String EM_QUOTE_API_REFERER = "https://quote.eastmoney.com";
    private static final String EM_QUOTE_API_SOURCE = "东方财富";
    /** 东财历史K线接口：klt 1/5/15/30/60 分钟、101 日、102 周、103 月；日/周/月K历史可回溯数十年 */
    private static final String EM_KLINE_API_PATH = "/api/qt/stock/kline/get?secid=";
    private static final String EM_KLINE_API_PARAMS = "&fqt=1&fields1=f1,f2,f3&fields2=f51,f52,f53,f54,f55,f56&end=20500101&lmt=";
    private static final int EM_KLT_DAILY = 101;
    private static final int EM_KLT_WEEKLY = 102;
    private static final int EM_KLT_MONTHLY = 103;

    /** 新浪手机版期货分钟K接口：type 1/5/15/30/60 分钟，约返回最近 1023 根 */
    private static final String SINA_FUTURES_MINUTE_API_URL = "https://gu.sina.cn/ft/api/jsonp.php/var%20t=/GlobalService.getMinK?symbol=";
    /** 新浪 A 股指数K线接口（CN_MarketDataService.getKLineData）：scale 为分钟周期，datalen 上限约 1023 根 */
    private static final String CHINA_KLINE_API_URL = "https://quotes.sina.cn/cn/api/jsonp_v2.php/var%20t=/CN_MarketDataService.getKLineData?symbol=";
    private static final String CHINA_KLINE_API_PARAMS = "&ma=no";
    private static final String SINA_API_REFERER = "https://finance.sina.com.cn";
    private static final String SINA_API_SOURCE = "新浪财经";
    /** 新浪接口返回 GBK 编码文本 */
    private static final Charset RESPONSE_CHARSET = Charset.forName("GBK");

    /** 腾讯K线接口（fqkline/get）查询指数日K，中证红利专用：返回 data.{code}.day 为 [日期, 开, 收, 高, 低, 成交量]（开收高低顺序） */
    private static final String TENCENT_DAILY_KLINE_API_URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=";
    /** 腾讯当日分时接口（web 主域部分路径会 301，统一走 finance.qq 代理域名） */
    private static final String TENCENT_MINUTE_API_URL = "https://proxy.finance.qq.com/ifzqgtimg/appstock/app/minute/query?code=";
    /** 腾讯分钟K接口：param=代码,m{周期},,,数量 */
    private static final String TENCENT_MKLINE_API_URL = "https://proxy.finance.qq.com/ifzqgtimg/appstock/app/kline/mkline?param=";
    private static final String TENCENT_API_REFERER = "https://gu.qq.com";
    /** 走腾讯K线接口的品种（中证红利：东财日K仅 2023-12 起，历史不足以支撑年K） */
    private static final Map<String, String> TENCENT_KLINE_SYMBOLS = Map.of(
        "CSI_DIVIDEND", "sh000922"
    );
    /** 走新浪 A 股K线服务的品种（分钟K多周期走新浪，回看深于东财） */
    private static final Map<String, String> CHINA_KLINE_SYMBOLS = Map.of(
        "SH000001", "sh000001",
        "CSI500", "sh000905"
    );

    /** 美债收益率官方日度数据（美联储 FRED 公开 CSV，无需 key，T-1 更新） */
    private static final String FRED_DGS10_CSV_URL = "https://fred.stlouisfed.org/graph/fredgraph.csv?id=DGS10";
    private static final String FRED_API_SOURCE = "美联储FRED";
    /** FRED 日度数据缓存 TTL：官方每日更新一次，拉全量历史后缓存 1 小时 */
    private static final long FRED_CACHE_TTL_MS = 3_600_000L;

    /** 提取新浪 K 线接口 var t=([...]); 中的 JSON 数组 */
    private static final Pattern KLINE_JSON_PATTERN = Pattern.compile("var\\s*t\\s*=\\s*\\((\\[.*\\])\\)\\s*;?", Pattern.DOTALL);

    /** 东财接口缓存 TTL：吸收前端轮询频率，降低触发限流的风险 */
    private static final long EM_CACHE_TTL_MS = 30_000L;
    /** 东财批量报价缓存：secid -> 报价（含过期数据，限流时兜底返回） */
    private final Map<String, GlobalMarketQuoteResponse> emQuoteCache = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long emQuoteCacheAt = 0L;
    private final Object emQuoteLock = new Object();
    /** FRED 美债日度全量缓存（官方每日更新一次） */
    private volatile List<GlobalMarketKLineItemResponse> fredDailyCache;
    private volatile long fredDailyCacheAt = 0L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RestClient restClient;
    /** FRED 专用客户端：该站点对 HTTP/1.1 指纹表现极差（请求挂起直到读超时），必须走 HTTP/2 */
    private final java.net.http.HttpClient fredHttpClient;

    public GlobalMarketService() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(8000);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.fredHttpClient = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_2)
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
    }

    /**
     * 查询全部品种实时报价：东财批量接口一次请求返回（含美债收益率的盘中变化）。
     * 美债收益率在东财缺失时回退 FRED 官方 T-1 收盘，失败不拖垮整页报价。
     */
    public List<GlobalMarketQuoteResponse> getQuotes(List<String> symbols) {
        Map<String, GlobalMarketQuoteResponse> quoteBySymbol = new LinkedHashMap<>(fetchEmQuotes(symbols));
        if (symbols.contains("US10Y") && !quoteBySymbol.containsKey("US10Y")) {
            try {
                quoteBySymbol.put("US10Y", fetchFredQuote());
            } catch (RuntimeException e) {
                log.warn("美债收益率报价获取失败: {}", e.getMessage());
            }
        }
        // 保持调用方传入的品种顺序
        List<GlobalMarketQuoteResponse> quotes = new ArrayList<>();
        for (String symbol : symbols) {
            GlobalMarketQuoteResponse quote = quoteBySymbol.get(symbol);
            if (quote != null) {
                quotes.add(quote);
            }
        }
        return quotes;
    }

    /**
     * 东财批量报价：合并为一次 ulist 请求（限流敏感），带 30 秒缓存；
     * 请求失败时回退到缓存（允许短暂过期），避免东财临时限流导致整页报错。
     * ulist 字段（fltt=2，数值为小数）：f2 现价 / f12 代码 / f13 市场 / f14 名称
     * / f15 最高 / f16 最低 / f17 今开 / f18 昨收 / f124 更新时间戳。
     */
    private Map<String, GlobalMarketQuoteResponse> fetchEmQuotes(List<String> symbols) {
        synchronized (emQuoteLock) {
            Map<String, GlobalMarketQuoteResponse> result = new LinkedHashMap<>();
            boolean cacheFresh = System.currentTimeMillis() - emQuoteCacheAt < EM_CACHE_TTL_MS;
            if (cacheFresh) {
                for (String symbol : symbols) {
                    GlobalMarketQuoteResponse cached = emQuoteCache.get(EM_SECID.get(symbol));
                    if (cached != null) {
                        result.put(symbol, cached);
                    }
                }
                if (result.size() == symbols.size()) {
                    return result;
                }
            }
            List<String> secids = new ArrayList<>();
            for (String symbol : symbols) {
                String secid = EM_SECID.get(symbol);
                if (secid == null) {
                    throw new IllegalArgumentException("不支持的行情品种: " + symbol);
                }
                secids.add(secid);
            }
            try {
                byte[] body = fetchEm(EM_BATCH_QUOTE_API_PATH + String.join(",", secids) + EM_BATCH_QUOTE_API_PARAMS,
                    "东财批量行情");
                if (body == null) {
                    throw new IllegalStateException("东财批量行情接口返回为空");
                }
                JsonNode diff;
                try {
                    diff = objectMapper.readTree(new String(body, java.nio.charset.StandardCharsets.UTF_8))
                        .path("data").path("diff");
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("东财批量行情数据解析失败", ex);
                }
                Map<String, GlobalMarketQuoteResponse> fetchedBySecid = new LinkedHashMap<>();
                if (diff.isArray()) {
                    for (JsonNode row : diff) {
                        parseEmQuoteRow(row, fetchedBySecid);
                    }
                } else if (diff.isObject()) {
                    parseEmQuoteRow(diff, fetchedBySecid);
                }
                if (fetchedBySecid.isEmpty()) {
                    throw new IllegalStateException("东财批量行情未解析到任何品种");
                }
                emQuoteCache.putAll(fetchedBySecid);
                emQuoteCacheAt = System.currentTimeMillis();
                for (String symbol : symbols) {
                    GlobalMarketQuoteResponse quote = fetchedBySecid.get(EM_SECID.get(symbol));
                    if (quote != null) {
                        result.put(symbol, quote);
                    }
                }
            } catch (RuntimeException ex) {
                // 东财临时限流时降级：回退缓存（允许短暂过期），无缓存则返回空，不影响错误提示
                log.warn("东财批量行情请求失败，回退缓存: {}", ex.getMessage());
                for (String symbol : symbols) {
                    GlobalMarketQuoteResponse cached = emQuoteCache.get(EM_SECID.get(symbol));
                    if (cached != null) {
                        result.put(symbol, cached);
                    }
                }
            }
            return result;
        }
    }

    /** 解析 ulist 响应中的一行报价，按「市场.代码」还原 secid 作为键 */
    private void parseEmQuoteRow(JsonNode row, Map<String, GlobalMarketQuoteResponse> fetchedBySecid) {
        String code = row.path("f12").asText(null);
        String market = row.path("f13").asText(null);
        if (code == null || market == null) {
            return;
        }
        String secid = market + "." + code;
        String symbol = symbolBySecid(secid);
        if (symbol == null) {
            return;
        }
        BigDecimal price = flttDecimal(row, "f2");
        BigDecimal preClose = flttDecimal(row, "f18");
        if (price == null || preClose == null || preClose.signum() <= 0) {
            log.warn("东财批量行情数据不完整: {}", symbol);
            return;
        }
        GlobalMarketQuoteResponse quote = new GlobalMarketQuoteResponse();
        quote.setSymbol(symbol);
        quote.setName(row.path("f14").asText(symbol));
        quote.setPrice(price);
        quote.setPreClose(preClose);
        quote.setChange(price.subtract(preClose));
        quote.setChangePercent(price.subtract(preClose)
            .multiply(BigDecimal.valueOf(100))
            .divide(preClose, 2, RoundingMode.HALF_UP));
        quote.setOpen(flttDecimal(row, "f17"));
        quote.setHigh(flttDecimal(row, "f15"));
        quote.setLow(flttDecimal(row, "f16"));
        long timestamp = row.path("f124").asLong(0);
        java.time.ZonedDateTime time = timestamp > 0
            ? java.time.Instant.ofEpochSecond(timestamp).atZone(java.time.ZoneId.of("Asia/Shanghai"))
            : java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        quote.setQuoteDate(time.toLocalDate().toString());
        quote.setQuoteTime(time.toLocalTime().withNano(0).toString());
        quote.setDataSource(EM_QUOTE_API_SOURCE);
        fetchedBySecid.put(secid, quote);
    }

    /** 反查 secid 对应的品种代码（如 1.000905 -> CSI500） */
    private String symbolBySecid(String secid) {
        for (Map.Entry<String, String> entry : EM_SECID.entrySet()) {
            if (entry.getValue().equals(secid)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** 解析东财 fltt=2 数值字段：直接为小数，"-" 或缺失返回 null */
    private BigDecimal flttDecimal(JsonNode row, String field) {
        JsonNode value = row.path(field);
        if (!value.isNumber()) {
            return null;
        }
        return value.decimalValue().stripTrailingZeros();
    }

    /** 东财 GET 请求：主域名失败自动切换备用域名重试一次 */
    private byte[] fetchEm(String pathAndQuery, String desc) {
        try {
            return emGet(EM_API_HOST + pathAndQuery);
        } catch (RuntimeException ex) {
            log.warn("{}主域名请求失败，切换备用域名: {}", desc, ex.getMessage());
            return emGet(EM_API_HOST_FALLBACK + pathAndQuery);
        }
    }

    private byte[] emGet(String url) {
        return restClient.get()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", EM_QUOTE_API_REFERER)
            .retrieve()
            .body(byte[].class);
    }

    /**
     * 查询品种的日 K 线历史数据。
     * 东财 secid 直接覆盖绝大多数品种（指数/商品/外汇，历史可回溯数十年）；
     * 美债走 FRED（1962 年起，网络不可达时回退东财）、中证红利走腾讯（东财仅 2023-12 起）。
     */
    public List<GlobalMarketKLineItemResponse> getDailyKLine(String symbol, int limit) {
        if (isTreasurySymbol(symbol)) {
            try {
                return getFredDailyKLine(limit);
            } catch (RuntimeException e) {
                log.warn("FRED日K获取失败，美债日K回退东财: {}", e.getMessage());
                return getEmKLine(symbol, EM_KLT_DAILY, limit);
            }
        }
        if (isTencentKlineSymbol(symbol)) {
            return getTencentIndexDailyKLine(symbol, limit);
        }
        return getEmKLine(symbol, EM_KLT_DAILY, limit);
    }

    /**
     * 查询分钟K线（1/5/15/30/60 分钟）。
     * 东财的分钟K回看有窗口（1 分钟仅当日、60 分钟约一周、5 分钟约两月），
     * 因此回看更深的新浪（商品期货约 1023 根、A股指数约 1023 根）与腾讯（中证红利）按品种保留。
     */
    public List<GlobalMarketKLineItemResponse> getMinuteKLine(String symbol, int minuteType, int limit) {
        if (symbol.startsWith("hf_")) {
            return getFuturesMinuteKLine(symbol, minuteType, limit);
        }
        if (isChinaIndexSymbol(symbol) && minuteType > 1) {
            return getChinaIndexKLine(symbol, minuteType, limit);
        }
        if (isTencentKlineSymbol(symbol)) {
            return getTencentMinuteKLine(symbol, minuteType, limit);
        }
        // A股分时、全球/美股指数、美元指数、美债收益率：东财分钟K
        return getEmKLine(symbol, minuteType, limit);
    }

    /**
     * 查询周K/月K/年K。
     * 周/月K东财已聚合好直接返回；年K由东财月K聚合（月K全量历史深于日K的单次 1 万根上限）；
     * 美债/中证红利维持各自数据源的日K聚合，美债在 FRED 不可达时回退东财。
     */
    public List<GlobalMarketKLineItemResponse> getAggregatedKLine(String symbol, AggregationPeriod period, int limit) {
        if (isTreasurySymbol(symbol)) {
            try {
                return aggregateDaily(getFredDailyKLine(Integer.MAX_VALUE), period, limit);
            } catch (RuntimeException e) {
                log.warn("FRED数据获取失败，美债周/月/年K回退东财: {}", e.getMessage());
                return emAggregated(symbol, period, limit);
            }
        }
        if (isTencentKlineSymbol(symbol)) {
            return aggregateDaily(getTencentIndexDailyKLine(symbol, Integer.MAX_VALUE), period, limit);
        }
        return emAggregated(symbol, period, limit);
    }

    /** 东财聚合K线：周/月K直接返回，年K由月K聚合 */
    private List<GlobalMarketKLineItemResponse> emAggregated(String symbol, AggregationPeriod period, int limit) {
        if (period == AggregationPeriod.WEEK) {
            return getEmKLine(symbol, EM_KLT_WEEKLY, limit);
        }
        if (period == AggregationPeriod.MONTH) {
            return getEmKLine(symbol, EM_KLT_MONTHLY, limit);
        }
        return aggregateMonthlyToYear(getEmKLine(symbol, EM_KLT_MONTHLY, 3000), limit);
    }

    /**
     * 东财历史K线（push2his 主机）。返回行格式：日期,开盘,收盘,最高,最低,成交量
     * （注意东财字段顺序为开、收、高、低），lmt 由服务端直接截断。
     */
    public List<GlobalMarketKLineItemResponse> getEmKLine(String symbol, int klt, int limit) {
        String secid = EM_SECID.get(symbol);
        if (secid == null) {
            throw new IllegalArgumentException("不支持的行情品种: " + symbol);
        }
        byte[] body = fetchWithRetry("东财K线 " + symbol,
            () -> restClient.get()
                .uri(URI.create(EM_KLINE_API_HOST + EM_KLINE_API_PATH + secid
                    + "&klt=" + klt + EM_KLINE_API_PARAMS + Math.max(1, limit)))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", EM_QUOTE_API_REFERER)
                .retrieve()
                .body(byte[].class));
        if (body == null || body.length == 0) {
            throw new IllegalStateException("东财K线接口返回为空");
        }
        JsonNode klines;
        try {
            klines = objectMapper.readTree(new String(body, java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("klines");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("东财K线数据解析失败: " + symbol, ex);
        }
        if (!klines.isArray()) {
            throw new IllegalStateException("东财K线接口返回格式无法解析: " + symbol);
        }
        List<GlobalMarketKLineItemResponse> items = new ArrayList<>();
        for (JsonNode row : klines) {
            String[] parts = row.asText().split(",", -1);
            if (parts.length < 5) {
                continue;
            }
            GlobalMarketKLineItemResponse item = new GlobalMarketKLineItemResponse();
            item.setDate(parts[0].trim());
            item.setOpen(parseDecimalText(parts[1]));
            item.setClose(parseDecimalText(parts[2]));
            item.setHigh(parseDecimalText(parts[3]));
            item.setLow(parseDecimalText(parts[4]));
            if (parts.length > 5) {
                item.setVolume(parseVolume(parts[5]));
            }
            if (item.getDate() != null && item.getClose() != null) {
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            throw new IllegalStateException("东财K线数据为空: " + symbol);
        }
        return items;
    }

    /** 美债收益率报价兜底（东财缺失时）：FRED 官方日度收盘，price=最新值，preClose=前一交易日值 */
    private GlobalMarketQuoteResponse fetchFredQuote() {
        List<GlobalMarketKLineItemResponse> daily = getFredDailyKLine(Integer.MAX_VALUE);
        if (daily.size() < 2) {
            throw new IllegalStateException("美债收益率数据不足");
        }
        GlobalMarketKLineItemResponse latest = daily.get(daily.size() - 1);
        GlobalMarketKLineItemResponse previous = daily.get(daily.size() - 2);
        BigDecimal price = latest.getClose();
        BigDecimal preClose = previous.getClose();
        if (price == null || preClose == null || preClose.signum() <= 0) {
            throw new IllegalStateException("美债收益率数据不完整");
        }
        GlobalMarketQuoteResponse quote = new GlobalMarketQuoteResponse();
        quote.setSymbol("US10Y");
        quote.setName("美债10年收益率");
        quote.setPrice(price);
        quote.setPreClose(preClose);
        quote.setChange(price.subtract(preClose));
        quote.setChangePercent(price.subtract(preClose)
            .multiply(BigDecimal.valueOf(100))
            .divide(preClose, 2, RoundingMode.HALF_UP));
        quote.setQuoteDate(latest.getDate());
        quote.setDataSource(FRED_API_SOURCE);
        return quote;
    }

    /** FRED CSV 下载：JDK HttpClient 走 HTTP/2（该站点对 HTTP/1.1 会直接挂起），失败重试一次 */
    private byte[] fetchFredCsv() {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(URI.create(FRED_DGS10_CSV_URL))
            .header("User-Agent", "Mozilla/5.0")
            .GET()
            .build();
        for (int attempt = 1; ; attempt++) {
            try {
                java.net.http.HttpResponse<byte[]> response =
                    fredHttpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200 || response.body() == null || response.body().length == 0) {
                    throw new IllegalStateException("FRED接口返回 " + response.statusCode());
                }
                return response.body();
            } catch (IOException e) {
                if (attempt >= 2) {
                    throw new IllegalStateException("FRED接口请求失败: " + e.getMessage(), e);
                }
                log.warn("FRED接口请求失败，重试一次: {}", e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("FRED接口请求被中断", e);
            }
        }
    }

    /** 美债10Y日K：FRED 官方日度收益率 CSV（全量历史），开高低均取收盘价，缓存 1 小时 */
    private List<GlobalMarketKLineItemResponse> getFredDailyKLine(int limit) {
        List<GlobalMarketKLineItemResponse> cached = fredDailyCache;
        if (cached == null || System.currentTimeMillis() - fredDailyCacheAt >= FRED_CACHE_TTL_MS) {
            try {
                byte[] body = fetchFredCsv();
                if (body == null || body.length == 0) {
                    throw new IllegalStateException("FRED接口返回为空");
                }
                List<GlobalMarketKLineItemResponse> items = new ArrayList<>();
                // CSV 格式：表头 observation_date,DGS10，行 2026-10-19,5.15；缺失值为 "."
                String[] lines = new String(body, java.nio.charset.StandardCharsets.UTF_8).split("\n");
                for (int i = 1; i < lines.length; i++) {
                    String[] cols = lines[i].trim().split(",", -1);
                    String value = cols.length > 1 ? cols[1].trim() : "";
                    if (cols[0].length() != 10 || value.isEmpty() || ".".equals(value)) {
                        continue;
                    }
                    BigDecimal close = parseDecimalText(value);
                    if (close == null) {
                        continue;
                    }
                    GlobalMarketKLineItemResponse item = new GlobalMarketKLineItemResponse();
                    item.setDate(cols[0]);
                    item.setOpen(close);
                    item.setHigh(close);
                    item.setLow(close);
                    item.setClose(close);
                    items.add(item);
                }
                if (items.isEmpty()) {
                    throw new IllegalStateException("FRED美债收益率数据为空");
                }
                fredDailyCache = items;
                fredDailyCacheAt = System.currentTimeMillis();
                cached = items;
            } catch (RuntimeException e) {
                // 网络偶发超时：官方数据每日仅更新一次，回退过期缓存不影响正确性
                if (cached == null) {
                    throw e;
                }
                log.warn("FRED数据刷新失败，回退过期缓存: {}", e.getMessage());
            }
        }
        int from = Math.max(0, cached.size() - Math.max(1, limit));
        return cached.subList(from, cached.size());
    }

    /**
     * 新浪 A 股指数K线（CN_MarketDataService.getKLineData），用于 A 股指数的分钟K。
     * scaleMinutes 为分钟周期（5/15/30/60），datalen 上限约 1023 根。
     * 返回 var t=([{day,open,high,low,close,volume},...]);
     */
    public List<GlobalMarketKLineItemResponse> getChinaIndexKLine(String symbol, int scaleMinutes, int limit) {
        String chinaSymbol = CHINA_KLINE_SYMBOLS.get(symbol);
        if (chinaSymbol == null) {
            throw new IllegalArgumentException("不支持的A股指数品种: " + symbol);
        }
        int dataLen = Math.max(1, Math.min(limit, 1023));
        byte[] body = restClient.get()
            .uri(URI.create(CHINA_KLINE_API_URL + chinaSymbol + "&scale=" + scaleMinutes
                + "&datalen=" + dataLen + CHINA_KLINE_API_PARAMS))
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", SINA_API_REFERER)
            .retrieve()
            .body(byte[].class);
        if (body == null) {
            throw new IllegalStateException("A股指数K线接口返回为空");
        }
        String text = new String(body, RESPONSE_CHARSET);
        Matcher matcher = KLINE_JSON_PATTERN.matcher(text);
        if (!matcher.find()) {
            throw new IllegalStateException("A股指数K线接口返回格式无法解析: " + chinaSymbol);
        }

        List<GlobalMarketKLineItemResponse> items = new ArrayList<>();
        try {
            JsonNode rows = objectMapper.readTree(matcher.group(1));
            for (JsonNode row : rows) {
                GlobalMarketKLineItemResponse item = new GlobalMarketKLineItemResponse();
                item.setDate(row.path("day").asText(null));
                item.setOpen(parseDecimalText(row.path("open").asText(null)));
                item.setHigh(parseDecimalText(row.path("high").asText(null)));
                item.setLow(parseDecimalText(row.path("low").asText(null)));
                item.setClose(parseDecimalText(row.path("close").asText(null)));
                item.setVolume(parseVolume(row.path("volume").asText(null)));
                if (item.getDate() != null && item.getClose() != null) {
                    items.add(item);
                }
            }
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("A股指数K线数据解析失败: " + chinaSymbol, ex);
        }
        int from = Math.max(0, items.size() - Math.max(1, limit));
        return items.subList(from, items.size());
    }

    /**
     * 腾讯K线接口（fqkline/get）查询指数日K，用于中证红利。
     * 返回 JSON，data.{code}.day 为二维数组：[日期, 开, 收, 高, 低, 成交量]（注意顺序是开收高低）。
     */
    public List<GlobalMarketKLineItemResponse> getTencentIndexDailyKLine(String symbol, int limit) {
        String tencentCode = TENCENT_KLINE_SYMBOLS.get(symbol);
        if (tencentCode == null) {
            throw new IllegalArgumentException("不支持的腾讯K线品种: " + symbol);
        }
        int dataLen = Math.max(1, Math.min(limit, 1023));
        // 腾讯接口可能返回 text/html 但内容为 JSON，按字节流拉取后自行解析，避免 Content-Type 转换失败
        byte[] body = fetchWithRetry("腾讯日K " + symbol,
            () -> restClient.get()
                .uri(URI.create(TENCENT_DAILY_KLINE_API_URL + tencentCode + ",day,,," + dataLen + ",qfq"))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", TENCENT_API_REFERER)
                .retrieve()
                .body(byte[].class));
        if (body == null) {
            throw new IllegalStateException("腾讯日K接口返回为空");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(new String(body, java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("腾讯日K数据解析失败: " + tencentCode, ex);
        }
        JsonNode rows = root.path("data").path(tencentCode).path("day");
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            throw new IllegalStateException("腾讯日K接口返回格式无法解析: " + tencentCode);
        }

        List<GlobalMarketKLineItemResponse> items = new ArrayList<>();
        for (JsonNode row : rows) {
            GlobalMarketKLineItemResponse item = new GlobalMarketKLineItemResponse();
            item.setDate(row.path(0).asText(null));
            item.setOpen(parseDecimalText(row.path(1).asText(null)));
            item.setClose(parseDecimalText(row.path(2).asText(null)));
            item.setHigh(parseDecimalText(row.path(3).asText(null)));
            item.setLow(parseDecimalText(row.path(4).asText(null)));
            item.setVolume(parseVolume(row.path(5).asText(null)));
            if (item.getDate() != null && item.getClose() != null) {
                items.add(item);
            }
        }
        int from = Math.max(0, items.size() - Math.max(1, limit));
        return items.subList(from, items.size());
    }

    /** 中证红利分钟K：分时(m1)用腾讯当日分时，5/15/30/60分钟用腾讯分钟K接口 */
    private List<GlobalMarketKLineItemResponse> getTencentMinuteKLine(String symbol, int minuteType, int limit) {
        String tencentCode = TENCENT_KLINE_SYMBOLS.get(symbol);
        if (tencentCode == null) {
            throw new IllegalArgumentException("不支持的腾讯K线品种: " + symbol);
        }
        if (minuteType <= 1) {
            return getTencentMinuteTrend(tencentCode, limit);
        }
        return getTencentMinuteBars(tencentCode, minuteType, limit);
    }

    /** 腾讯当日分时：data.data 为 "0930 5549.82 累计量 累计额" 数组，价格取第2列 */
    private List<GlobalMarketKLineItemResponse> getTencentMinuteTrend(String tencentCode, int limit) {
        byte[] body = fetchWithRetry("腾讯分时 " + tencentCode,
            () -> restClient.get()
                .uri(URI.create(TENCENT_MINUTE_API_URL + tencentCode))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", TENCENT_API_REFERER)
                .retrieve()
                .body(byte[].class));
        if (body == null) {
            throw new IllegalStateException("腾讯分时接口返回为空");
        }
        JsonNode rows;
        try {
            rows = objectMapper.readTree(new String(body, java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path(tencentCode).path("data").path("data");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("腾讯分时数据解析失败: " + tencentCode, ex);
        }
        String today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
        List<GlobalMarketKLineItemResponse> items = new ArrayList<>();
        for (JsonNode row : rows) {
            String[] parts = row.asText().trim().split("\\s+");
            if (parts.length < 2) {
                continue;
            }
            BigDecimal price = parseDecimalText(parts[1]);
            if (price == null || parts[0].length() != 4) {
                continue;
            }
            GlobalMarketKLineItemResponse item = new GlobalMarketKLineItemResponse();
            item.setDate(today + " " + parts[0].substring(0, 2) + ":" + parts[0].substring(2));
            item.setOpen(price);
            item.setHigh(price);
            item.setLow(price);
            item.setClose(price);
            items.add(item);
        }
        int from = Math.max(0, items.size() - Math.max(1, limit));
        return items.subList(from, items.size());
    }

    /** 腾讯分钟K：data.{code}.m{周期} 数组为 [时间, 开, 收, 高, 低, 量]，开收高低顺序与日K一致 */
    private List<GlobalMarketKLineItemResponse> getTencentMinuteBars(String tencentCode, int minuteType, int limit) {
        int dataLen = Math.max(1, Math.min(limit, 1023));
        byte[] body = fetchWithRetry("腾讯分钟K " + tencentCode,
            () -> restClient.get()
                .uri(URI.create(TENCENT_MKLINE_API_URL + tencentCode + ",m" + minuteType + ",,," + dataLen))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", TENCENT_API_REFERER)
                .retrieve()
                .body(byte[].class));
        if (body == null) {
            throw new IllegalStateException("腾讯分钟K接口返回为空");
        }
        JsonNode rows;
        try {
            rows = objectMapper.readTree(new String(body, java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path(tencentCode).path("m" + minuteType);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("腾讯分钟K数据解析失败: " + tencentCode, ex);
        }
        List<GlobalMarketKLineItemResponse> items = new ArrayList<>();
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                if (!row.isArray() || row.size() < 5) {
                    continue;
                }
                BigDecimal open = parseDecimalText(row.get(1).asText());
                BigDecimal close = parseDecimalText(row.get(2).asText());
                BigDecimal high = parseDecimalText(row.get(3).asText());
                BigDecimal low = parseDecimalText(row.get(4).asText());
                if (open == null || close == null || high == null || low == null) {
                    continue;
                }
                GlobalMarketKLineItemResponse item = new GlobalMarketKLineItemResponse();
                item.setDate(formatTencentMinuteDate(row.get(0).asText()));
                item.setOpen(open);
                item.setHigh(high);
                item.setLow(low);
                item.setClose(close);
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            throw new IllegalStateException("腾讯分钟K数据为空: " + tencentCode + " m" + minuteType);
        }
        int from = Math.max(0, items.size() - Math.max(1, limit));
        return items.subList(from, items.size());
    }

    /** 腾讯分钟K时间（202610211445）转为应用格式（2026-10-21 14:45），已是标准格式则原样返回 */
    private String formatTencentMinuteDate(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() == 12 && value.chars().allMatch(Character::isDigit)) {
            return value.substring(0, 4) + "-" + value.substring(4, 6) + "-" + value.substring(6, 8)
                + " " + value.substring(8, 10) + ":" + value.substring(10, 12);
        }
        return value;
    }

    /**
     * 查询商品期货品种分钟K线（1/5/15/30/60 分钟）。新浪手机版接口字段：
     * d 时间、o/h/l/c 开高低收、v 成交量，约返回最近 1023 根。
     */
    public List<GlobalMarketKLineItemResponse> getFuturesMinuteKLine(String symbol, int minuteType, int limit) {
        String klineSymbol = symbol.startsWith("hf_") ? symbol.substring("hf_".length()) : symbol;
        byte[] body = restClient.get()
            .uri(URI.create(SINA_FUTURES_MINUTE_API_URL + klineSymbol + "&type=" + minuteType))
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", SINA_API_REFERER)
            .retrieve()
            .body(byte[].class);
        if (body == null) {
            throw new IllegalStateException("分钟K接口返回为空");
        }
        String text = new String(body, RESPONSE_CHARSET);
        Matcher matcher = KLINE_JSON_PATTERN.matcher(text);
        if (!matcher.find()) {
            throw new IllegalStateException("分钟K接口返回格式无法解析: " + klineSymbol);
        }

        List<GlobalMarketKLineItemResponse> items = new ArrayList<>();
        try {
            JsonNode rows = objectMapper.readTree(matcher.group(1));
            for (JsonNode row : rows) {
                GlobalMarketKLineItemResponse item = new GlobalMarketKLineItemResponse();
                item.setDate(row.path("d").asText(null));
                item.setOpen(parseDecimalText(row.path("o").asText(null)));
                item.setHigh(parseDecimalText(row.path("h").asText(null)));
                item.setLow(parseDecimalText(row.path("l").asText(null)));
                item.setClose(parseDecimalText(row.path("c").asText(null)));
                item.setVolume(parseVolume(row.path("v").asText(null)));
                if (item.getDate() != null && item.getClose() != null) {
                    items.add(item);
                }
            }
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("分钟K数据解析失败: " + klineSymbol, ex);
        }
        int from = Math.max(0, items.size() - Math.max(1, limit));
        return items.subList(from, items.size());
    }

    /** 日K聚合为周/月/年K：按自然周（ISO，周一起始）/自然月/自然年分组 */
    private List<GlobalMarketKLineItemResponse> aggregateDaily(
        List<GlobalMarketKLineItemResponse> dailyItems, AggregationPeriod period, int limit
    ) {
        if (dailyItems.isEmpty()) {
            return dailyItems;
        }
        Map<String, GlobalMarketKLineItemResponse> grouped = new LinkedHashMap<>();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
        for (GlobalMarketKLineItemResponse item : dailyItems) {
            LocalDate date;
            try {
                date = LocalDate.parse(item.getDate(), formatter);
            } catch (RuntimeException ignored) {
                continue;
            }
            String key = switch (period) {
                case WEEK -> date.get(java.time.temporal.WeekFields.ISO.weekBasedYear())
                    + "-W" + date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
                case MONTH -> String.format("%04d-%02d", date.getYear(), date.getMonthValue());
                case YEAR -> String.valueOf(date.getYear());
            };
            mergeKLine(grouped, key, item);
        }
        return tailSlice(new ArrayList<>(grouped.values()), limit);
    }

    /** 月K聚合为年K：按年份前缀分组，日期取该年最后一根月K日期 */
    private List<GlobalMarketKLineItemResponse> aggregateMonthlyToYear(
        List<GlobalMarketKLineItemResponse> monthlyItems, int limit
    ) {
        Map<String, GlobalMarketKLineItemResponse> grouped = new LinkedHashMap<>();
        for (GlobalMarketKLineItemResponse item : monthlyItems) {
            String date = item.getDate();
            if (date == null || date.length() < 4) {
                continue;
            }
            mergeKLine(grouped, date.substring(0, 4), item);
        }
        return tailSlice(new ArrayList<>(grouped.values()), limit);
    }

    /** 聚合合并一根K线到分组：开盘取组内首根、收盘取最新、高低取极值、成交量求和 */
    private void mergeKLine(Map<String, GlobalMarketKLineItemResponse> grouped, String key,
                            GlobalMarketKLineItemResponse item) {
        GlobalMarketKLineItemResponse merged = grouped.get(key);
        if (merged == null) {
            merged = new GlobalMarketKLineItemResponse();
            merged.setDate(item.getDate());
            merged.setOpen(item.getOpen());
            merged.setHigh(item.getHigh());
            merged.setLow(item.getLow());
            merged.setClose(item.getClose());
            merged.setVolume(item.getVolume());
            grouped.put(key, merged);
            return;
        }
        merged.setDate(item.getDate());
        merged.setClose(item.getClose());
        if (item.getHigh() != null && (merged.getHigh() == null || item.getHigh().compareTo(merged.getHigh()) > 0)) {
            merged.setHigh(item.getHigh());
        }
        if (item.getLow() != null && (merged.getLow() == null || item.getLow().compareTo(merged.getLow()) < 0)) {
            merged.setLow(item.getLow());
        }
        if (item.getVolume() != null) {
            merged.setVolume((merged.getVolume() == null ? 0L : merged.getVolume()) + item.getVolume());
        }
    }

    private List<GlobalMarketKLineItemResponse> tailSlice(
        List<GlobalMarketKLineItemResponse> items, int limit
    ) {
        int from = Math.max(0, items.size() - Math.max(1, limit));
        return items.subList(from, items.size());
    }

    /** 周/月/年聚合周期 */
    public enum AggregationPeriod {
        WEEK, MONTH, YEAR
    }

    private BigDecimal parseDecimalText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long parseVolume(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 美债收益率品种：报价东财（缺失时 FRED 兜底），日K走美联储 FRED 官方日度数据 */
    private boolean isTreasurySymbol(String symbol) {
        return "US10Y".equals(symbol);
    }

    /** A 股指数品种（5/15/30/60 分钟K走新浪，回看深于东财） */
    private boolean isChinaIndexSymbol(String symbol) {
        return CHINA_KLINE_SYMBOLS.containsKey(symbol);
    }

    /** 走腾讯K线接口的品种（中证红利：东财日K仅 2023-12 起，历史不足以支撑年K） */
    private boolean isTencentKlineSymbol(String symbol) {
        return TENCENT_KLINE_SYMBOLS.containsKey(symbol);
    }

    /**
     * 外部行情请求重试一次。东财/腾讯服务器会主动关闭空闲 keep-alive 连接，
     * HttpURLConnection 复用旧连接时间歇性抛 Unexpected end of file，换新连接重试即可恢复。
     */
    private <T> T fetchWithRetry(String desc, java.util.function.Supplier<T> fetch) {
        try {
            return fetch.get();
        } catch (RestClientException ex) {
            log.warn("{}请求失败，重试一次: {}", desc, ex.getMessage());
            try {
                Thread.sleep(300);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw ex;
            }
            return fetch.get();
        }
    }
}
