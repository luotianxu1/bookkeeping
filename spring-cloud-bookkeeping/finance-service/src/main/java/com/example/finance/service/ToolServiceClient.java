package com.example.finance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * tool-service 内部接口客户端：替代直查 photography_orders 表。
 * 调用失败按 0 处理（与原 fail-open 行为一致），仅记录告警。
 */
@Component
public class ToolServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ToolServiceClient.class);

    private final RestClient restClient;
    private final String internalApiKey;

    public ToolServiceClient(
        @Value("${tool.service.url:http://localhost:8083}") String toolServiceUrl,
        @Value("${internal.api-key:dev-internal-key}") String internalApiKey
    ) {
        this.restClient = RestClient.builder().baseUrl(toolServiceUrl).build();
        this.internalApiKey = internalApiKey;
    }

    /** 统计引用该现金账户的摄影订单数；调用失败返回 0（fail-open） */
    public long countPhotographyOrderAccountReferences(Long accountId) {
        if (accountId == null) {
            return 0L;
        }
        try {
            Map<String, Object> body = restClient.get()
                .uri("/api/internal/tool/photography-orders/account-references?accountId={id}", accountId)
                .header("X-Internal-Key", internalApiKey)
                .retrieve()
                .body(Map.class);
            Object count = body == null ? null : body.get("count");
            return count instanceof Number number ? number.longValue() : 0L;
        } catch (Exception ex) {
            log.warn("查询摄影订单账户引用失败（fail-open 返回 0）: accountId={}", accountId, ex);
            return 0L;
        }
    }
}
