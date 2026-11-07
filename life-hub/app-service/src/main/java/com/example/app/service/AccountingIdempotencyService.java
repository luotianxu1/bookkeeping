package com.example.app.service;

import com.example.app.mapper.AccountingRequestMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/** 调用方先持有用户账务锁；业务写入和幂等响应一起提交或一起回滚。 */
@Service
public class AccountingIdempotencyService {
    private final AccountingRequestMapper mapper;
    private final ObjectMapper json;

    public AccountingIdempotencyService(AccountingRequestMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json.copy().findAndRegisterModules();
    }

    public <T> T execute(Long userId, String operation, String requestId, Object request, Class<T> responseType, Supplier<T> action) {
        // 兼容旧客户端；新版表单必须为每次业务提交生成并复用请求 ID。
        if (requestId == null) return action.get();
        if (!requestId.matches("[A-Za-z0-9_-]{16,64}")) throw new IllegalArgumentException("请求 ID 格式无效");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("幂等记录必须与账务同事务");
        try {
            var replay = replay(userId, operation, requestId, request, responseType);
            if (replay.isPresent()) return replay.get();
            String hash = hash(request);
            T response = action.get();
            mapper.insert(userId, operation, requestId, hash, json.writeValueAsString(response));
            return response;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("交易请求处理失败", exception);
        }
    }

    /** 已提交的请求可直接重放，无需等待报价服务；首次写入仍须在用户锁内再次检查。 */
    public <T> Optional<T> replay(Long userId, String operation, String requestId, Object request, Class<T> responseType) {
        if (requestId == null) return Optional.empty();
        if (!requestId.matches("[A-Za-z0-9_-]{16,64}")) throw new IllegalArgumentException("请求 ID 格式无效");
        try {
            var stored = mapper.find(userId, operation, requestId);
            if (stored == null) return Optional.empty();
            if (!hash(request).equals(stored.requestHash())) throw new IllegalArgumentException("同一请求 ID 不能用于不同交易，请重新提交");
            return Optional.of(json.readValue(stored.responseJson(), responseType));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("交易请求读取失败", exception);
        }
    }

    private String hash(Object request) throws Exception {
        JsonNode payload = json.valueToTree(request);
        normalizeNumbers(payload);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(payload)));
    }

    private void normalizeNumbers(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.properties().forEach(entry -> {
                if (entry.getValue().isNumber()) object.set(entry.getKey(), DecimalNode.valueOf(entry.getValue().decimalValue().stripTrailingZeros()));
                else normalizeNumbers(entry.getValue());
            });
        } else if (node.isArray()) node.forEach(this::normalizeNumbers);
    }
}
