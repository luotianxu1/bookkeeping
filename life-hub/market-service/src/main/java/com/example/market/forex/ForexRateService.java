package com.example.market.forex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 汇率服务（2026-10-30 从 app-service ExchangeRateService 整体迁入）：
 * 主源 open.er-api.com（每币种基准 5 分钟缓存），失败降级为内置兜底汇率表。
 * 字段口径与原 app-service 一致：fromCurrency/toCurrency/rate(6位小数)/updatedAt/source。
 */
@Service
public class ForexRateService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long CACHE_MILLIS = 5 * 60_000L;
    private static final Map<String, BigDecimal> DEFAULT_USD_RATES = Map.of(
        "USD", BigDecimal.ONE,
        "CNY", new BigDecimal("7.20"),
        "EUR", new BigDecimal("0.92"),
        "JPY", new BigDecimal("155.00"),
        "HKD", new BigDecimal("7.82"),
        "GBP", new BigDecimal("0.79"),
        "AUD", new BigDecimal("1.52"),
        "CAD", new BigDecimal("1.36"),
        "SGD", new BigDecimal("1.35")
    );

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String latestApiUrl;
    private final Map<String, CachedRates> cachedRates = new ConcurrentHashMap<>();

    public ForexRateService(
        ObjectMapper objectMapper,
        @org.springframework.beans.factory.annotation.Value("${finance.exchange-rate.latest-api-url:https://open.er-api.com/v6/latest}") String latestApiUrl
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.latestApiUrl = latestApiUrl;
    }

    public Map<String, Object> getRate(String fromCurrency, String toCurrency) {
        String from = normalizeCurrency(fromCurrency, "USD");
        String to = normalizeCurrency(toCurrency, "CNY");

        CachedRates rates = fetchRates(from);
        BigDecimal rate = from.equals(to) ? BigDecimal.ONE : rates.rates().get(to);
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            rate = fallbackRate(from, to);
        }

        Map<String, Object> out = new ConcurrentHashMap<>();
        out.put("fromCurrency", from);
        out.put("toCurrency", to);
        out.put("rate", rate.setScale(6, RoundingMode.HALF_UP));
        out.put("updatedAt", rates.updatedAt().toString());
        out.put("source", rates.source());
        return out;
    }

    private CachedRates fetchRates(String baseCurrency) {
        long now = System.currentTimeMillis();
        CachedRates cached = cachedRates.get(baseCurrency);
        if (cached != null && now - cached.cachedAt() < CACHE_MILLIS) {
            return cached;
        }

        try {
            JsonNode root = fetchJson(latestApiUrl + "/" + baseCurrency);
            JsonNode ratesNode = root.path("rates");
            if (!ratesNode.isObject()) {
                throw new IllegalStateException("汇率数据格式错误");
            }

            Map<String, BigDecimal> rates = new ConcurrentHashMap<>();
            ratesNode.fields().forEachRemaining((entry) -> {
                JsonNode value = entry.getValue();
                if (value != null && value.isNumber()) {
                    rates.put(entry.getKey().toUpperCase(Locale.ROOT), value.decimalValue());
                }
            });

            CachedRates nextCache = new CachedRates(
                rates,
                extractUpdatedAt(root),
                now,
                "open.er-api"
            );
            cachedRates.put(baseCurrency, nextCache);
            return nextCache;
        } catch (Exception ex) {
            if (cached != null) {
                return cached;
            }
            CachedRates fallback = new CachedRates(
                fallbackRates(baseCurrency),
                LocalDateTime.now(DEFAULT_ZONE),
                now,
                "内置兜底汇率"
            );
            cachedRates.put(baseCurrency, fallback);
            return fallback;
        }
    }

    private JsonNode fetchJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", "Mozilla/5.0")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("汇率接口 HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private LocalDateTime extractUpdatedAt(JsonNode root) {
        JsonNode timestampNode = root.path("time_last_update_unix");
        if (!timestampNode.isNumber()) {
            return LocalDateTime.now(DEFAULT_ZONE);
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestampNode.asLong()), DEFAULT_ZONE);
    }

    private String normalizeCurrency(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, BigDecimal> fallbackRates(String baseCurrency) {
        BigDecimal baseToUsd = DEFAULT_USD_RATES.get(baseCurrency);
        if (baseToUsd == null || baseToUsd.compareTo(BigDecimal.ZERO) <= 0) {
            return Map.of(baseCurrency, BigDecimal.ONE);
        }

        Map<String, BigDecimal> rates = new ConcurrentHashMap<>();
        DEFAULT_USD_RATES.forEach((currency, usdRate) -> {
            BigDecimal rate = usdRate.divide(baseToUsd, 8, RoundingMode.HALF_UP);
            rates.put(currency, rate);
        });
        return rates;
    }

    private BigDecimal fallbackRate(String from, String to) {
        BigDecimal fromUsdRate = DEFAULT_USD_RATES.get(from);
        BigDecimal toUsdRate = DEFAULT_USD_RATES.get(to);
        if (fromUsdRate == null || toUsdRate == null || fromUsdRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return toUsdRate.divide(fromUsdRate, 8, RoundingMode.HALF_UP);
    }

    private record CachedRates(Map<String, BigDecimal> rates, LocalDateTime updatedAt, long cachedAt, String source) {
    }
}
