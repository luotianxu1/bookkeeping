package com.example.finance.service;

import com.example.finance.dto.GoldPriceResponse;
import com.example.finance.dto.GoldRealtimePriceResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GoldPriceService {

    private static final BigDecimal TROY_OUNCE_GRAMS = new BigDecimal("31.1034768");
    private static final BigDecimal DEFAULT_USD_CNY = new BigDecimal("7.20");
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long CACHE_MILLIS = 60_000L;
    private static final int CONNECT_TIMEOUT_MILLIS = 1500;
    private static final int READ_TIMEOUT_MILLIS = 2000;

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String exchangeRateApiUrl;
    private final String cngoldRealtimeApiUrl;
    private final String goldChartApiUrl;

    private CachedGoldPrice cachedCurrentPrice;
    private final Map<String, CachedChartPoints> cachedChartPoints = new HashMap<>();

    public GoldPriceService(
        ObjectMapper objectMapper,
        @Value("${finance.gold-price.exchange-rate-api-url:https://open.er-api.com/v6/latest/USD}") String exchangeRateApiUrl,
        @Value("${finance.gold-price.cngold-realtime-api-url:https://api.jijinhao.com/quoteCenter/realTime.htm}") String cngoldRealtimeApiUrl,
        @Value("${finance.gold-price.gold-chart-api-url:https://query1.finance.yahoo.com/v8/finance/chart/GC=F}") String goldChartApiUrl
    ) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);
        this.restClient = RestClient.builder()
            .requestFactory(requestFactory)
            .build();
        this.exchangeRateApiUrl = exchangeRateApiUrl;
        this.cngoldRealtimeApiUrl = cngoldRealtimeApiUrl;
        this.goldChartApiUrl = goldChartApiUrl;
    }

    public synchronized GoldPriceResponse getGoldPrice(String range) {
        String normalizedRange = normalizeRange(range);
        long now = System.currentTimeMillis();

        try {
            GoldPriceResponse response = getCurrentPrice(now);
            response.setChartPoints(getChartPoints(normalizedRange, now));
            return response;
        } catch (Exception ex) {
            throw new IllegalStateException("金价数据获取失败", ex);
        }
    }

    public synchronized GoldRealtimePriceResponse getRealtimePrice() {
        long now = System.currentTimeMillis();
        try {
            return toRealtimePrice(getCurrentPrice(now));
        } catch (Exception ex) {
            throw new IllegalStateException("实时金价获取失败", ex);
        }
    }

    public synchronized BigDecimal getCachedSpotPrice() {
        if (cachedCurrentPrice == null) {
            return null;
        }
        GoldPriceResponse.GoldMarketQuote spotGold = cachedCurrentPrice.response().getSpotGold();
        if (spotGold == null || spotGold.getPrice() == null || spotGold.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return spotGold.getPrice().setScale(2, RoundingMode.HALF_UP);
    }

    private GoldPriceResponse getCurrentPrice(long now) throws Exception {
        if (cachedCurrentPrice != null && now - cachedCurrentPrice.cachedAt() < CACHE_MILLIS) {
            return copyResponseWithoutChart(cachedCurrentPrice.response());
        }

        JsonNode quotes = fetchCngoldQuotes(cngoldCodes());
        GoldPriceResponse response = buildCurrentResponse(
            fetchUsdCny(),
            quotes
        );
        cachedCurrentPrice = new CachedGoldPrice(response, now);
        return copyResponseWithoutChart(response);
    }

    private GoldPriceResponse buildCurrentResponse(
        BigDecimal usdCny,
        JsonNode quotes
    ) {
        JsonNode goldNode = quotes.path("JO_92233");
        BigDecimal londonPrice = firstDecimal(goldNode, "price", "last", "last_price", "close", "ask");
        if (londonPrice == null) {
            londonPrice = firstDecimal(goldNode, "q63");
        }
        BigDecimal londonOpen = firstDecimal(goldNode, "open_price", "open", "openPrice");
        if (londonOpen == null) {
            londonOpen = firstDecimal(goldNode, "q1");
        }
        BigDecimal londonHigh = firstDecimal(goldNode, "high_price", "high", "highPrice");
        if (londonHigh == null) {
            londonHigh = firstDecimal(goldNode, "q3");
        }
        BigDecimal londonLow = firstDecimal(goldNode, "low_price", "low", "lowPrice");
        if (londonLow == null) {
            londonLow = firstDecimal(goldNode, "q4");
        }
        BigDecimal londonBuy = firstDecimal(goldNode, "q5");
        BigDecimal londonSell = firstDecimal(goldNode, "q6");
        BigDecimal londonChange = firstDecimal(goldNode, "change", "ch", "change_price", "changePrice");
        if (londonChange == null) {
            londonChange = firstDecimal(goldNode, "q70");
        }
        BigDecimal londonChangePercent = firstDecimal(goldNode, "change_percent", "chp", "change_margin", "changePercent");
        if (londonChangePercent == null) {
            londonChangePercent = firstDecimal(goldNode, "q80");
        }

        if (londonPrice == null || londonPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("金价数据缺少价格字段");
        }

        if (londonOpen == null) {
            londonOpen = londonPrice.subtract(defaultLondonDelta(londonPrice));
        }
        if (londonChange == null) {
            londonChange = londonPrice.subtract(londonOpen);
        }
        if (londonChangePercent == null && londonOpen.compareTo(BigDecimal.ZERO) > 0) {
            londonChangePercent = londonChange.divide(londonOpen, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        }
        if (londonHigh == null) {
            londonHigh = londonPrice.max(londonOpen).add(defaultLondonDelta(londonPrice));
        }
        if (londonLow == null) {
            londonLow = londonPrice.min(londonOpen).subtract(defaultLondonDelta(londonPrice));
        }

        LocalDateTime updatedAt = extractUpdatedAt(goldNode);
        BigDecimal spotPrice = usdOzToCnyGram(londonPrice, usdCny);
        BigDecimal spotChange = usdOzToCnyGram(londonChange, usdCny);
        BigDecimal spotOpen = usdOzToCnyGram(londonOpen, usdCny);
        BigDecimal spotHigh = usdOzToCnyGram(londonHigh, usdCny);
        BigDecimal spotLow = usdOzToCnyGram(londonLow, usdCny);
        BigDecimal spotBuy = londonBuy == null ? spotPrice.add(new BigDecimal("0.15")) : usdOzToCnyGram(londonBuy, usdCny);
        BigDecimal spotSell = londonSell == null ? spotPrice.subtract(new BigDecimal("0.15")) : usdOzToCnyGram(londonSell, usdCny);

        GoldPriceResponse response = new GoldPriceResponse();
        response.setSpotGold(marketQuote("现货金", "CNY/g", spotPrice, spotChange, scalePercent(londonChangePercent), updatedAt));
        response.setLondonGold(marketQuote("伦敦金", "USD/oz", scaleMoney(londonPrice), scaleMoney(londonChange), scalePercent(londonChangePercent), updatedAt));
        response.setStats(stats(spotOpen, spotHigh, spotLow, spotBuy, spotSell));
        response.setJewelryPrices(fetchJewelryPrices(quotes));
        response.setChartPoints(List.of());
        response.setUpdatedAt(updatedAt);
        response.setSource("金投网实时行情 + open.er-api 汇率换算");
        return response;
    }

    private JsonNode fetchJson(String url) throws Exception {
        String body = restClient.get()
            .uri(url)
            .retrieve()
            .body(String.class);
        return objectMapper.readTree(body);
    }

    private JsonNode fetchCngoldQuotes(String codes) throws Exception {
        String body = restClient.get()
            .uri(cngoldRealtimeApiUrl + "?codes=" + codes)
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
        if (root.has("JO_92233")) {
            return root;
        }
        throw new IllegalStateException("金投网行情缺少黄金报价数据");
    }

    private String cngoldCodes() {
        return "JO_92233,JO_42657,JO_42660,JO_42625,JO_42634,JO_42653,JO_42646,JO_52678,JO_42638";
    }

    private BigDecimal fetchUsdCny() {
        try {
            JsonNode exchangeNode = fetchJson(exchangeRateApiUrl);
            BigDecimal rate = decimalAt(exchangeNode, "rates", "CNY");
            return rate != null && rate.compareTo(BigDecimal.ZERO) > 0 ? rate : DEFAULT_USD_CNY;
        } catch (Exception ex) {
            return DEFAULT_USD_CNY;
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
        List<CngoldJewelryCode> codes = List.of(
            new CngoldJewelryCode("老凤祥", "JO_42657"),
            new CngoldJewelryCode("周大福", "JO_42660"),
            new CngoldJewelryCode("周生生", "JO_42625"),
            new CngoldJewelryCode("老庙黄金", "JO_42634"),
            new CngoldJewelryCode("周六福", "JO_42653"),
            new CngoldJewelryCode("六福珠宝", "JO_42646"),
            new CngoldJewelryCode("周大生", "JO_52678"),
            new CngoldJewelryCode("菜百", "JO_42638")
        );

        List<GoldPriceResponse.JewelryGoldPrice> prices = new ArrayList<>();
        for (CngoldJewelryCode code : codes) {
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

    private List<GoldPriceResponse.GoldChartPoint> getChartPoints(
        String range,
        long now
    ) {
        CachedChartPoints cached = cachedChartPoints.get(range);
        if (cached != null && now - cached.cachedAt() < CACHE_MILLIS) {
            return cached.points();
        }

        List<GoldPriceResponse.GoldChartPoint> points = fetchChartPoints(range);
        cachedChartPoints.put(range, new CachedChartPoints(points, now));
        return points;
    }

    private List<GoldPriceResponse.GoldChartPoint> fetchChartPoints(String range) {
        if ("1d".equals(range)) {
            List<GoldPriceResponse.GoldChartPoint> cngoldPoints = fetchCngoldChartPoints(range);
            if (!cngoldPoints.isEmpty()) {
                return compactChartPoints(cngoldPoints, range);
            }
        }

        try {
            JsonNode result = fetchJson(goldChartUrl(range))
                .path("chart")
                .path("result")
                .path(0);
            JsonNode timestamps = result.path("timestamp");
            JsonNode closes = result.path("indicators").path("quote").path(0).path("close");
            if (!timestamps.isArray() || !closes.isArray()) {
                return List.of();
            }

            BigDecimal usdCny = fetchUsdCny();
            List<GoldPriceResponse.GoldChartPoint> points = new ArrayList<>();
            for (int index = 0; index < timestamps.size() && index < closes.size(); index++) {
                JsonNode closeNode = closes.get(index);
                if (closeNode == null || closeNode.isNull() || !closeNode.isNumber()) {
                    continue;
                }

                GoldPriceResponse.GoldChartPoint point = new GoldPriceResponse.GoldChartPoint();
                LocalDateTime time = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(timestamps.get(index).asLong()),
                    DEFAULT_ZONE
                );
                point.setLabel(formatChartLabel(time, range));
                point.setPrice(usdOzToCnyGram(closeNode.decimalValue(), usdCny));
                points.add(point);
            }
            if (!points.isEmpty()) {
                return compactChartPoints(points, range);
            }
        } catch (Exception ex) {
            // Fall through to alternate providers when Yahoo chart data is blocked or unavailable.
        }

        List<GoldPriceResponse.GoldChartPoint> cngoldPoints = fetchCngoldChartPoints(range);
        if (!cngoldPoints.isEmpty()) {
            return compactChartPoints(cngoldPoints, range);
        }

        return List.of();
    }

    private List<GoldPriceResponse.GoldChartPoint> fetchCngoldChartPoints(String range) {
        try {
            return "1d".equals(range) ? fetchCngoldTodayMinPoints() : fetchCngoldKlinePoints(range);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<GoldPriceResponse.GoldChartPoint> fetchCngoldTodayMinPoints() throws Exception {
        String body = fetchCngoldText(cngoldChartUrl("todayMin.htm") + "?code=JO_92233");
        JsonNode root = extractJavascriptObject(body, "hq_str_ml");
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return List.of();
        }

        BigDecimal usdCny = fetchUsdCny();
        List<GoldPriceResponse.GoldChartPoint> points = new ArrayList<>();
        for (JsonNode item : data) {
            BigDecimal price = decimalAt(item, "price");
            Long timestamp = firstLong(item, "date");
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0 || timestamp == null) {
                continue;
            }

            LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), DEFAULT_ZONE);
            GoldPriceResponse.GoldChartPoint point = new GoldPriceResponse.GoldChartPoint();
            point.setLabel(formatChartLabel(time, "1d"));
            point.setPrice(usdOzToCnyGram(price, usdCny));
            points.add(point);
        }
        return points;
    }

    private List<GoldPriceResponse.GoldChartPoint> fetchCngoldKlinePoints(String range) throws Exception {
        String body = fetchCngoldText(cngoldChartUrl("kDataList.htm") + "?code=JO_92233&pageSize=400");
        JsonNode root = extractJavascriptObject(body, "KLC_KL");
        JsonNode seriesGroup = root.path("data");
        JsonNode data = seriesGroup.isArray() && !seriesGroup.isEmpty() ? seriesGroup.get(0) : null;
        if (data == null || !data.isArray()) {
            return List.of();
        }

        BigDecimal usdCny = fetchUsdCny();
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
            point.setPrice(usdOzToCnyGram(close, usdCny));
            points.add(point);
        }
        return points;
    }

    private String goldChartUrl(String range) {
        String yahooRange = switch (range) {
            case "7d" -> "7d";
            case "30d" -> "1mo";
            case "1y" -> "1y";
            default -> "1d";
        };
        String interval = switch (range) {
            case "7d" -> "30m";
            case "30d" -> "1h";
            case "1y" -> "1d";
            default -> "15m";
        };
        return goldChartApiUrl + "?range=" + yahooRange + "&interval=" + interval;
    }

    private String formatChartLabel(LocalDateTime time, String range) {
        return switch (range) {
            case "1d" -> String.format("%02d:%02d", time.getHour(), time.getMinute());
            case "1y" -> time.getMonthValue() + "月";
            default -> time.getMonthValue() + "/" + time.getDayOfMonth();
        };
    }

    private List<GoldPriceResponse.GoldChartPoint> compactChartPoints(
        List<GoldPriceResponse.GoldChartPoint> points,
        String range
    ) {
        int maxCount = switch (range) {
            case "1d" -> 24;
            case "7d" -> 42;
            case "30d" -> 48;
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

    private String cngoldChartUrl(String endpoint) {
        int quoteCenterIndex = cngoldRealtimeApiUrl.indexOf("/quoteCenter/");
        if (quoteCenterIndex >= 0) {
            return cngoldRealtimeApiUrl.substring(0, quoteCenterIndex) + "/sQuoteCenter/" + endpoint;
        }

        int sQuoteCenterIndex = cngoldRealtimeApiUrl.indexOf("/sQuoteCenter/");
        if (sQuoteCenterIndex >= 0) {
            return cngoldRealtimeApiUrl.substring(0, sQuoteCenterIndex) + "/sQuoteCenter/" + endpoint;
        }

        return "https://api.jijinhao.com/sQuoteCenter/" + endpoint;
    }

    private String fetchCngoldText(String url) {
        return restClient.get()
            .uri(url)
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
            case "7d" -> now - 7L * 24 * 60 * 60 * 1000;
            case "30d" -> now - 30L * 24 * 60 * 60 * 1000;
            case "1y" -> now - 365L * 24 * 60 * 60 * 1000;
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

    private String normalizeRange(String range) {
        if ("7d".equals(range) || "30d".equals(range) || "1y".equals(range)) {
            return range;
        }
        return "1d";
    }

    private record CngoldJewelryCode(String brandName, String code) {
    }

    private record CachedGoldPrice(GoldPriceResponse response, long cachedAt) {
    }

    private record CachedChartPoints(List<GoldPriceResponse.GoldChartPoint> points, long cachedAt) {
    }
}
