package com.example.market.forex;

import com.example.market.market.MarketUtils;
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
 *
 * <p>缓存语义（2026-10-30 修复）：兜底汇率不再写入正式缓存——上游失败时
 * 有历史真实值则返回旧值（30s 降级窗口后重试上游），完全没有时返回
 * 不落缓存的兜底值，恢复后下一次请求即重试主源，不会把兜底价当真实值用 5 分钟。</p>
 */
@Service
public class ForexRateService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long CACHE_MILLIS = 5 * 60_000L;
    private static final long FAILURE_TTL_MILLIS = 30_000L;
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
    private final MarketUtils.KeyedTtlCache<String, CachedRates> ratesCache =
        new MarketUtils.KeyedTtlCache<>(CACHE_MILLIS, FAILURE_TTL_MILLIS, 32);

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
        CachedRates cached = ratesCache.get(baseCurrency, this::fetchUpstreamRates);
        if (cached != null) {
            return cached;
        }
        // 上游失败且无历史真实值：返回不落缓存的兜底汇率，恢复后下一次请求即重试上游
        return new CachedRates(
            fallbackRates(baseCurrency),
            LocalDateTime.now(DEFAULT_ZONE),
            System.currentTimeMillis(),
            "内置兜底汇率"
        );
    }

    private CachedRates fetchUpstreamRates(String baseCurrency) {
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

        return new CachedRates(
            rates,
            extractUpdatedAt(root),
            System.currentTimeMillis(),
            "open.er-api"
        );
    }

    private JsonNode fetchJson(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", "Mozilla/5.0")
            .GET()
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("汇率接口 HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("汇率接口请求失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("汇率接口请求被中断", e);
        }
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
