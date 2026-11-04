package com.example.app.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 行情中心（market-service）internal 接口客户端公共基类：统一 RestTemplate 构建、
 * X-Internal-Key 鉴权头、{code,message,data} 响应封装解析。子类只声明读超时与业务端点。
 *
 * <p>约定：</p>
 * <ul>
 *   <li>path 中的查询参数必须已自行 URL 编码，请求一律传 {@link URI} 对象——
 *       RestTemplate 对 String url 会再按 URI 模板编码一次，中文关键词会被双重编码
 *       成 %25E7...（曾导致搜索恒为空）。</li>
 *   <li>面向客户端的异常消息不拼接原始异常细节（可能含上游 URL/响应片段），
 *       细节统一记入服务端日志。</li>
 * </ul>
 */
public abstract class InternalMarketClientSupport {

    private static final int CONNECT_TIMEOUT_MS = 3000;

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected final RestTemplate restTemplate;
    protected final ObjectMapper objectMapper;
    protected final String baseUrl;
    protected final String internalApiKey;
    /** 业务域标签，用于异常消息（如"报价"/"金价"/"汇率"/"行情"） */
    private final String domainLabel;

    protected InternalMarketClientSupport(
        ObjectMapper objectMapper,
        String baseUrl,
        String internalApiKey,
        String domainLabel,
        int readTimeoutMillis
    ) {
        this.restTemplate = newRestTemplate(readTimeoutMillis);
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.internalApiKey = internalApiKey;
        this.domainLabel = domainLabel;
    }

    /** 供需要第二个超时档位的子类使用（如 MarketClient 的清单长读模板） */
    protected static RestTemplate newRestTemplate(int readTimeoutMillis) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(readTimeoutMillis);
        return new RestTemplate(factory);
    }

    protected static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    protected JsonNode fetchNode(String url) {
        return fetchNode(url, restTemplate);
    }

    /** 请求指定 URL 并解析 {code,message,data} 封装，返回 data 节点。 */
    protected JsonNode fetchNode(String url, RestTemplate template) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Key", internalApiKey);
        ResponseEntity<String> response;
        try {
            // 必须传 URI 对象，避免 RestTemplate 二次 URL 编码（见类注释）
            response = template.exchange(URI.create(url), HttpMethod.GET, new HttpEntity<>(headers), String.class);
        } catch (Exception e) {
            log.warn("行情中心{}接口请求失败: url={}", domainLabel, url, e);
            throw new IllegalStateException("行情中心" + domainLabel + "接口不可用，请稍后重试", e);
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.path("code").asInt(-1) != 0) {
                String upstreamMessage = root.path("message").asText();
                log.warn("行情中心{}接口返回错误: {}", domainLabel, upstreamMessage);
                throw new IllegalStateException("行情中心" + domainLabel + "接口返回错误: " + upstreamMessage);
            }
            return root.path("data");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("行情中心{}响应解析失败", domainLabel, e);
            throw new IllegalStateException("行情中心" + domainLabel + "响应解析失败", e);
        }
    }
}
