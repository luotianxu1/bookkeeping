package com.example.app.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 行情中心（market-service）客户端：选股引擎的行情数据通道（日K + 全市场股票清单）。
 *
 * <p>原逻辑直接调用本地 {@code KlineService.sinaKline}（2026-10 行情代码迁出到独立行情中心后），
 * 改经 /api/internal/market/kline、/api/internal/market/stock-list（X-Internal-Key 鉴权）
 * 调用行情中心，新浪同源同封装。异常向上抛出，由调用方（重试/降级）处理。</p>
 */
@Component
public class MarketClient {

    private final RestTemplate restTemplate;
    private final RestTemplate longReadRestTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String internalApiKey;

    public MarketClient(
        ObjectMapper objectMapper,
        @Value("${market.center.base-url:http://localhost:8083}") String baseUrl,
        @Value("${internal.api-key:dev-internal-key}") String internalApiKey
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
        // 清单接口行情中心冷缓存时需分页抓取 5000+ 只（约 20~40s），用长读超时模板
        SimpleClientHttpRequestFactory longReadFactory = new SimpleClientHttpRequestFactory();
        longReadFactory.setConnectTimeout(3000);
        longReadFactory.setReadTimeout(60_000);
        this.longReadRestTemplate = new RestTemplate(longReadFactory);
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.internalApiKey = internalApiKey;
    }

    /**
     * 全市场 A 股清单（新浪 hs_a：沪深主板/创业板/科创板/北交所，停牌无效标的已过滤）。
     * 行字段：code/name/symbol/market。行情中心冷缓存时首次调用较慢（服务端分页抓源）。
     *
     * @throws IllegalStateException 行情中心不可用或响应异常
     */
    public List<Map<String, Object>> stockUniverse() {
        return fetchRows(baseUrl + "/api/internal/market/stock-list", longReadRestTemplate);
    }

    /**
     * 新浪日K线（与行情中心详情页同源）。返回行情行 Map 列表（day/open/high/low/close/volume）。
     *
     * @throws IllegalStateException 行情中心不可用或响应异常
     */
    public List<Map<String, Object>> sinaKline(String symbol, int datalen, String scale) {
        String url = baseUrl + "/api/internal/market/kline?symbol=" + symbol
            + "&datalen=" + datalen + "&scale=" + scale;
        return fetchRows(url, restTemplate);
    }

    private List<Map<String, Object>> fetchRows(String url, RestTemplate template) {
        JsonNode data = fetch(url, template);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode row : data) {
                rows.add(objectMapper.convertValue(row, Map.class));
            }
        }
        return rows;
    }

    private JsonNode fetch(String url, RestTemplate template) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Internal-Key", internalApiKey);
        org.springframework.http.ResponseEntity<String> response =
            template.exchange(url, org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers), String.class);
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.path("code").asInt(-1) != 0) {
                throw new IllegalStateException("行情中心返回错误: " + root.path("message").asText());
            }
            return root.path("data");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("行情中心响应解析失败", e);
        }
    }
}
