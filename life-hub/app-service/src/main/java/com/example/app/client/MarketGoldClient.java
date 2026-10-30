package com.example.app.client;

import com.example.app.dto.GoldPriceResponse;
import com.example.app.dto.GoldRealtimePriceResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * 金价（行情中心 market-service）客户端：金投网金价逻辑 2026-10-30 整体迁出到
 * market-service（/api/market/gold-prices + /api/internal/gold/**）后的服务间通道。
 *
 * <p>与 {@link MarketClient} 同一套 X-Internal-Key 鉴权约定；响应体结构与服务内
 * DTO 字段一一对应，直接反序列化，数据口径与迁移前一致。</p>
 */
@Component
public class MarketGoldClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String internalApiKey;

    public MarketGoldClient(
        ObjectMapper objectMapper,
        @Value("${market.center.base-url:http://localhost:8083}") String baseUrl,
        @Value("${internal.api-key:dev-internal-key}") String internalApiKey
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        // 走势图接口在行情中心侧需多次调金投 history，放宽读超时
        factory.setReadTimeout(15_000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.internalApiKey = internalApiKey;
    }

    /** 金价行情（含可选走势图）。行情中心侧保留"强刷失败回退缓存"的原语义。 */
    public GoldPriceResponse getGoldPrice(String range, boolean forceRefreshCurrent, boolean includeChart) {
        String url = baseUrl + "/api/internal/gold/price?range=" + range
            + "&forceRefreshCurrent=" + forceRefreshCurrent
            + "&includeChart=" + includeChart;
        return fetch(url, GoldPriceResponse.class);
    }

    /** 实时金价。forceRefreshCurrent=true 且上游刷新失败时抛错（与迁移前语义一致）。 */
    public GoldRealtimePriceResponse getRealtimePrice(boolean forceRefreshCurrent) throws Exception {
        String url = baseUrl + "/api/internal/gold/realtime?forceRefreshCurrent=" + forceRefreshCurrent;
        return fetch(url, GoldRealtimePriceResponse.class);
    }

    /** 行情中心缓存中的现货金价格（CNY/g），缓存未预热时为 null。 */
    public BigDecimal getCachedSpotPrice() {
        JsonNode data = fetchNode(baseUrl + "/api/internal/gold/spot-price");
        if (data == null || data.isNull()) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(data.asText());
            return price.compareTo(BigDecimal.ZERO) > 0 ? price : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public boolean hasWarmCache() {
        return getCachedSpotPrice() != null;
    }

    /** 刷新行情中心的金价缓存；上游失败时抛错（供定时任务重试语义使用）。 */
    public void refreshCache() throws Exception {
        fetchNode(baseUrl + "/api/internal/gold/refresh");
    }

    private <T> T fetch(String url, Class<T> type) {
        JsonNode data = fetchNode(url);
        try {
            return objectMapper.treeToValue(data, type);
        } catch (Exception e) {
            throw new IllegalStateException("行情中心金价响应解析失败", e);
        }
    }

    private JsonNode fetchNode(String url) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Internal-Key", internalApiKey);
        org.springframework.http.ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers), String.class);
        } catch (Exception e) {
            throw new IllegalStateException("行情中心金价接口不可用: " + e.getMessage(), e);
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.path("code").asInt(-1) != 0) {
                throw new IllegalStateException("行情中心金价接口返回错误: " + root.path("message").asText());
            }
            return root.path("data");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("行情中心金价响应解析失败", e);
        }
    }
}
