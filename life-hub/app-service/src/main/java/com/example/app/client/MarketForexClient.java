package com.example.app.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 汇率（行情中心 market-service）客户端：汇率取数 2026-10-30 收编到
 * market-service /api/internal/forex/** 后的服务间通道。
 *
 * <p>与 {@link MarketGoldClient} / {@link MarketQuoteClient} 同一套 X-Internal-Key 鉴权约定。
 * 上游主源 open.er-api.com，market-service 内部含 5 分钟缓存与内置兜底汇率。</p>
 */
@Component
public class MarketForexClient extends InternalMarketClientSupport {

    public MarketForexClient(
        ObjectMapper objectMapper,
        @Value("${market.center.base-url:http://localhost:8083}") String baseUrl,
        @Value("${internal.api-key:dev-internal-key}") String internalApiKey
    ) {
        super(objectMapper, baseUrl, internalApiKey, "汇率", 15_000);
    }

    /**
     * 汇率换算：{fromCurrency, toCurrency, rate, updatedAt(ISO-8601 文本), source}。
     * market-service 内部已含缓存与兜底，正常情况下不会失败。
     */
    public JsonNode rate(String fromCurrency, String toCurrency) {
        return fetchNode(baseUrl + "/api/internal/forex/rate"
            + "?from=" + encode(fromCurrency)
            + "&to=" + encode(toCurrency));
    }
}
