package com.example.finance.service;

import com.example.finance.dto.GoldPriceResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GoldPriceService {

    private static final BigDecimal TROY_OUNCE_GRAMS = new BigDecimal("31.1034768");
    private static final BigDecimal DEFAULT_USD_CNY = new BigDecimal("7.20");
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long CACHE_MILLIS = 60_000L;

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String goldApiUrl;
    private final String exchangeRateApiUrl;
    private final String jewelryGoldApiUrl;
    private final String goldChartApiUrl;

    private CachedGoldPrice cachedCurrentPrice;
    private final Map<String, CachedChartPoints> cachedChartPoints = new HashMap<>();

    public GoldPriceService(
        ObjectMapper objectMapper,
        @Value("${finance.gold-price.gold-api-url:https://api.gold-api.com/price/XAU}") String goldApiUrl,
        @Value("${finance.gold-price.exchange-rate-api-url:https://open.er-api.com/v6/latest/USD}") String exchangeRateApiUrl,
        @Value("${finance.gold-price.jewelry-gold-api-url:https://api.iyuns.com/api/goldprice}") String jewelryGoldApiUrl,
        @Value("${finance.gold-price.gold-chart-api-url:https://query1.finance.yahoo.com/v8/finance/chart/GC=F}") String goldChartApiUrl
    ) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
        this.goldApiUrl = goldApiUrl;
        this.exchangeRateApiUrl = exchangeRateApiUrl;
        this.jewelryGoldApiUrl = jewelryGoldApiUrl;
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

    private GoldPriceResponse getCurrentPrice(long now) throws Exception {
        if (cachedCurrentPrice != null && now - cachedCurrentPrice.cachedAt() < CACHE_MILLIS) {
            return copyResponseWithoutChart(cachedCurrentPrice.response());
        }

        GoldPriceResponse response = buildCurrentResponse(
            fetchJson(goldApiUrl),
            fetchUsdCny(),
            fetchJewelryPrices()
        );
        cachedCurrentPrice = new CachedGoldPrice(response, now);
        return copyResponseWithoutChart(response);
    }

    private GoldPriceResponse buildCurrentResponse(
        JsonNode goldNode,
        BigDecimal usdCny,
        List<GoldPriceResponse.JewelryGoldPrice> jewelryPrices
    ) {
        BigDecimal londonPrice = firstDecimal(goldNode, "price", "last", "last_price", "close", "ask");
        BigDecimal londonOpen = firstDecimal(goldNode, "open_price", "open", "openPrice");
        BigDecimal londonHigh = firstDecimal(goldNode, "high_price", "high", "highPrice");
        BigDecimal londonLow = firstDecimal(goldNode, "low_price", "low", "lowPrice");
        BigDecimal londonChange = firstDecimal(goldNode, "change", "ch", "change_price", "changePrice");
        BigDecimal londonChangePercent = firstDecimal(goldNode, "change_percent", "chp", "change_margin", "changePercent");

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

        GoldPriceResponse response = new GoldPriceResponse();
        response.setSpotGold(marketQuote("现货金", "CNY/g", spotPrice, spotChange, scalePercent(londonChangePercent), updatedAt));
        response.setLondonGold(marketQuote("伦敦金", "USD/oz", scaleMoney(londonPrice), scaleMoney(londonChange), scalePercent(londonChangePercent), updatedAt));
        response.setStats(stats(spotOpen, spotHigh, spotLow, spotPrice));
        response.setJewelryPrices(filterReliableJewelryPrices(jewelryPrices, spotPrice));
        response.setChartPoints(List.of());
        response.setUpdatedAt(updatedAt);
        response.setSource("Gold API + open.er-api 汇率换算 + 爱云API门店金价");
        return response;
    }

    private JsonNode fetchJson(String url) throws Exception {
        String body = restClient.get()
            .uri(url)
            .retrieve()
            .body(String.class);
        return objectMapper.readTree(body);
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
        BigDecimal spotPrice
    ) {
        GoldPriceResponse.GoldMarketStats stats = new GoldPriceResponse.GoldMarketStats();
        stats.setOpenPrice(openPrice);
        stats.setHighPrice(highPrice);
        stats.setLowPrice(lowPrice);
        stats.setBuyPrice(spotPrice.add(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP));
        stats.setSellPrice(spotPrice.subtract(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP));
        stats.setUnit("CNY/g");
        return stats;
    }

    private List<GoldPriceResponse.JewelryGoldPrice> fetchJewelryPrices() {
        try {
            JsonNode root = fetchJson(jewelryGoldApiUrl);
            JsonNode pricesNode = root.path("data").path("precious_metal_price");
            if (!pricesNode.isArray()) {
                return List.of();
            }

            List<GoldPriceResponse.JewelryGoldPrice> prices = new ArrayList<>();
            for (JsonNode item : pricesNode) {
                String brandName = item.path("brand").asText("");
                BigDecimal goldPrice = decimalAt(item, "gold_price");
                if (brandName.isBlank() || goldPrice == null || goldPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                prices.add(jewelryPrice(
                    brandName,
                    goldPrice,
                    parseUpdatedDate(item.path("updated_date").asText(""))
                ));
            }

            return prioritizeJewelryBrands(prices);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private GoldPriceResponse.JewelryGoldPrice jewelryPrice(String brandName, BigDecimal price, LocalDateTime updatedAt) {
        GoldPriceResponse.JewelryGoldPrice item = new GoldPriceResponse.JewelryGoldPrice();
        item.setBrandName(brandName);
        item.setPrice(price.setScale(2, RoundingMode.HALF_UP));
        item.setUnit("CNY/g");
        item.setUpdatedAt(updatedAt);
        return item;
    }

    private List<GoldPriceResponse.JewelryGoldPrice> filterReliableJewelryPrices(
        List<GoldPriceResponse.JewelryGoldPrice> prices,
        BigDecimal spotPrice
    ) {
        if (prices.isEmpty() || spotPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        BigDecimal maxReasonablePrice = spotPrice.multiply(new BigDecimal("1.60"));
        return prices.stream()
            .filter(item -> item.getPrice().compareTo(spotPrice) >= 0)
            .filter(item -> item.getPrice().compareTo(maxReasonablePrice) <= 0)
            .toList();
    }

    private List<GoldPriceResponse.JewelryGoldPrice> prioritizeJewelryBrands(List<GoldPriceResponse.JewelryGoldPrice> prices) {
        List<String> preferredBrands = List.of(
            "周大福",
            "老凤祥",
            "六福珠宝",
            "周生生",
            "老庙黄金",
            "中国黄金",
            "潮宏基",
            "金至尊",
            "谢瑞麟",
            "周六福"
        );

        return prices.stream()
            .sorted(Comparator.comparingInt(item -> {
                int index = preferredBrands.indexOf(item.getBrandName());
                return index >= 0 ? index : preferredBrands.size();
            }))
            .limit(10)
            .toList();
    }

    private LocalDateTime parseUpdatedDate(String updatedDate) {
        if (updatedDate == null || updatedDate.isBlank()) {
            return LocalDateTime.now(DEFAULT_ZONE);
        }

        try {
            return LocalDate.parse(updatedDate).atStartOfDay();
        } catch (DateTimeParseException ex) {
            return LocalDateTime.now(DEFAULT_ZONE);
        }
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
            return compactChartPoints(points, range);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String goldChartUrl(String range) {
        String yahooRange = switch (range) {
            case "7d" -> "7d";
            case "30d" -> "1mo";
            case "1y" -> "1y";
            default -> "1d";
        };
        String interval = switch (range) {
            case "7d" -> "1h";
            case "30d" -> "1d";
            case "1y" -> "1mo";
            default -> "15m";
        };
        return goldChartApiUrl + "?range=" + yahooRange + "&interval=" + interval;
    }

    private String formatChartLabel(LocalDateTime time, String range) {
        return switch (range) {
            case "1d", "7d" -> String.format("%02d:%02d", time.getHour(), time.getMinute());
            case "1y" -> time.getMonthValue() + "月";
            default -> time.getMonthValue() + "/" + time.getDayOfMonth();
        };
    }

    private List<GoldPriceResponse.GoldChartPoint> compactChartPoints(
        List<GoldPriceResponse.GoldChartPoint> points,
        String range
    ) {
        int maxCount = switch (range) {
            case "1d" -> 12;
            case "7d" -> 14;
            case "30d" -> 15;
            default -> 12;
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

    private record CachedGoldPrice(GoldPriceResponse response, long cachedAt) {
    }

    private record CachedChartPoints(List<GoldPriceResponse.GoldChartPoint> points, long cachedAt) {
    }
}
