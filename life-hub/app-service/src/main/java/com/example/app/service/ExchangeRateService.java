package com.example.app.service;

import com.example.app.client.MarketForexClient;
import com.example.app.dto.ExchangeRateResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;

/**
 * 汇率服务适配层（2026-10-30 汇率取数整体收编 market-service 后）：
 * 经 {@link MarketForexClient} → market-service /api/internal/forex/rate（open.er-api 主源，
 * market-service 内部 5 分钟缓存 + 兜底）。market-service 不可用时降级为本地内置汇率表，
 * 对外响应字段口径不变。
 */
@Service
public class ExchangeRateService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
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

    private final MarketForexClient marketForexClient;

    public ExchangeRateService(MarketForexClient marketForexClient) {
        this.marketForexClient = marketForexClient;
    }

    public ExchangeRateResponse getRate(String fromCurrency, String toCurrency) {
        String from = normalizeCurrency(fromCurrency, "USD");
        String to = normalizeCurrency(toCurrency, "CNY");

        try {
            return toResponse(marketForexClient.rate(from, to));
        } catch (Exception ex) {
            // 行情中心不可用：本地内置汇率兜底（与原降级行为一致）
            BigDecimal rate = from.equals(to) ? BigDecimal.ONE : fallbackRate(from, to);
            ExchangeRateResponse response = new ExchangeRateResponse();
            response.setFromCurrency(from);
            response.setToCurrency(to);
            response.setRate(rate.setScale(6, RoundingMode.HALF_UP));
            response.setUpdatedAt(LocalDateTime.now(DEFAULT_ZONE));
            response.setSource("内置兜底汇率");
            return response;
        }
    }

    private ExchangeRateResponse toResponse(JsonNode data) {
        ExchangeRateResponse response = new ExchangeRateResponse();
        response.setFromCurrency(textOr(data, "fromCurrency", "USD"));
        response.setToCurrency(textOr(data, "toCurrency", "CNY"));
        JsonNode rateNode = data.path("rate");
        response.setRate(rateNode.isNumber()
            ? rateNode.decimalValue().setScale(6, RoundingMode.HALF_UP)
            : new BigDecimal(rateNode.asText("1")).setScale(6, RoundingMode.HALF_UP));
        String updatedAtText = data.path("updatedAt").asText("");
        response.setUpdatedAt(updatedAtText.isEmpty() ? LocalDateTime.now(DEFAULT_ZONE) : LocalDateTime.parse(updatedAtText));
        response.setSource(data.path("source").asText("open.er-api"));
        return response;
    }

    private String textOr(JsonNode node, String field, String defaultValue) {
        String value = node.path(field).asText("");
        return value.isEmpty() ? defaultValue : value;
    }

    private String normalizeCurrency(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal fallbackRate(String from, String to) {
        BigDecimal fromUsdRate = DEFAULT_USD_RATES.get(from);
        BigDecimal toUsdRate = DEFAULT_USD_RATES.get(to);
        if (fromUsdRate == null || toUsdRate == null || fromUsdRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return toUsdRate.divide(fromUsdRate, 8, RoundingMode.HALF_UP);
    }
}
