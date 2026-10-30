package com.example.tool.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * finance-service 内部接口客户端：tool-service 不再直写 finance 的 accounts/transactions/categories 表。
 * 错误处理与原直写语义一致——finance 返回的业务错误原样抛给调用方。
 */
@Component
public class FinanceClient {

    private static final Logger log = LoggerFactory.getLogger(FinanceClient.class);

    public record CashAccountView(Long id, String name) {
    }

    private final RestClient restClient;
    private final String internalApiKey;
    private final ObjectMapper objectMapper;

    public FinanceClient(
        @Value("${finance.service.url:http://localhost:8082}") String financeServiceUrl,
        @Value("${internal.api-key:dev-internal-key}") String internalApiKey,
        ObjectMapper objectMapper
    ) {
        this.restClient = RestClient.builder().baseUrl(financeServiceUrl).build();
        this.internalApiKey = internalApiKey;
        this.objectMapper = objectMapper;
    }

    /** 校验现金账户（归属+现金类型+可用），失败抛 IllegalArgumentException */
    public CashAccountView requireCashAccount(Long userId, Long accountId) {
        JsonNode data = getForData("/api/internal/finance/cash-accounts/" + accountId + "?userId=" + userId);
        return new CashAccountView(data.path("id").asLong(), data.path("name").asText(null));
    }

    /** 批量获取账户名称（展示用），查询失败返回空 map（不影响订单列表展示） */
    public Map<Long, String> loadAccountNames(Long userId, Set<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }
        String joined = String.join(",", accountIds.stream().map(String::valueOf).toList());
        try {
            JsonNode data = getForData("/api/internal/finance/cash-accounts?userId=" + userId + "&ids=" + joined);
            Map<Long, String> names = new LinkedHashMap<>();
            for (JsonNode item : data) {
                names.put(item.path("id").asLong(), item.path("name").asText(null));
            }
            return names;
        } catch (Exception ex) {
            log.warn("批量查询现金账户名称失败（降级为空）: userId={}, ids={}", userId, joined, ex);
            return Map.of();
        }
    }

    /** 创建摄影收入流水并原子加余额，返回流水 id */
    public Long createPhotographyIncome(
        Long userId,
        Long accountId,
        BigDecimal amount,
        LocalDateTime occurredAt,
        String title,
        String remark
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("accountId", accountId);
        body.put("amount", amount);
        body.put("occurredAt", occurredAt == null ? null : occurredAt.toString());
        body.put("title", title);
        body.put("remark", remark);

        JsonNode data = postForData("/api/internal/finance/photography-income", body);
        return data.path("transactionId").asLong();
    }

    /** 作废关联收入流水并原子扣回余额 */
    public void voidPhotographyIncome(Long transactionId, Long userId) {
        postForData("/api/internal/finance/photography-income/" + transactionId + "/void?userId=" + userId, null);
    }

    private JsonNode getForData(String pathWithQuery) {
        String raw = restClient.get()
            .uri(pathWithQuery)
            .header("X-Internal-Key", internalApiKey)
            .retrieve()
            .body(String.class);
        return unwrap(raw);
    }

    private JsonNode postForData(String pathWithQuery, Map<String, Object> body) {
        String raw = restClient.post()
            .uri(pathWithQuery)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Internal-Key", internalApiKey)
            .body(body == null ? "{}" : body)
            .retrieve()
            .body(String.class);
        return unwrap(raw);
    }

    /** 解包 Result{code,data,message}：code!=0 时把业务消息抛回调用方 */
    private JsonNode unwrap(String raw) {
        JsonNode root;
        try {
            root = objectMapper.readTree(raw == null ? "{}" : raw);
        } catch (Exception ex) {
            throw new IllegalStateException("资金服务响应解析失败", ex);
        }
        int code = root.path("code").asInt(0);
        if (code != 0) {
            throw new IllegalArgumentException(root.path("message").asText("资金服务处理失败"));
        }
        return root.path("data");
    }
}
