package com.example.app.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 汇率（行情中心 market-service）客户端：汇率取数 2026-10-30 收编到
 * market-service /api/internal/forex/** 后的服务间通道。
 *
 * <p>与 {@link MarketGoldClient} / {@link MarketQuoteClient} 同一套 X-Internal-Key 鉴权约定。
 * 上游主源 open.er-api.com，market-service 内部含 5 分钟缓存与内置兜底汇率。</p>
 */
@Component
public class MarketForexClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String internalApiKey;

    public MarketForexClient(
        ObjectMapper objectMapper,
        @Value("${market.center.base-url:http://localhost:8083}") String baseUrl,
        @Value("${internal.api-key:dev-internal-key}") String internalApiKey
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(15_000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.internalApiKey = internalApiKey;
    }

    /**
     * 汇率换算：{fromCurrency, toCurrency, rate, updatedAt(ISO-8601 文本), source}。
     * market-service 内部已含缓存与兜底，正常情况下不会失败。
     */
    public JsonNode rate(String fromCurrency, String toCurrency) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Key", internalApiKey);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(
                java.net.URI.create(baseUrl + "/api/internal/forex/rate"
                    + "?from=" + encode(fromCurrency)
                    + "&to=" + encode(toCurrency)),
                HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class);
        } catch (Exception e) {
            throw new IllegalStateException("行情中心汇率接口不可用: " + e.getMessage(), e);
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.path("code").asInt(-1) != 0) {
                throw new IllegalStateException("行情中心汇率接口返回错误: " + root.path("message").asText());
            }
            return root.path("data");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("行情中心汇率响应解析失败", e);
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
