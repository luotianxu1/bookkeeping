package com.example.market.security;

import com.example.common.result.Result;
import com.example.common.security.InternalApiKeyFilter;
import com.example.common.security.UserStatusChecker;
import com.example.common.security.UserStatusCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * 用户状态的远程实现：经 auth-service 内部接口查询（X-Internal-Key 鉴权）。
 * 口径与 JdbcUserStatusChecker 一致：状态可信期 30 秒，查询失败且无有效状态时返回可重试的 503。
 */
@Component
public class RemoteUserStatusChecker implements UserStatusChecker {

    private final RestTemplate restTemplate;
    private final String authBaseUrl;
    private final String internalApiKey;
    private final UserStatusCache cache = new UserStatusCache();

    public RemoteUserStatusChecker(
        @Value("${market.auth-base-url:http://localhost:8081}") String authBaseUrl,
        @Value("${internal.api-key:dev-internal-key}") String internalApiKey
    ) {
        this.authBaseUrl = authBaseUrl.replaceAll("/$", "");
        this.internalApiKey = internalApiKey;
        this.restTemplate = buildRestTemplate();
    }

    private static RestTemplate buildRestTemplate() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }

    @Override
    public boolean isActive(long userId) {
        return cache.isActive(userId, id -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set(InternalApiKeyFilter.HEADER_NAME, internalApiKey);
                ResponseEntity<Result<Map<String, Object>>> response = restTemplate.exchange(
                    authBaseUrl + "/api/internal/auth/user-status?userId=" + id,
                    HttpMethod.GET,
                    new HttpEntity<Void>(headers),
                    new ParameterizedTypeReference<>() {
                    }
                );
                Result<Map<String, Object>> body = response.getBody();
                if (body == null || body.getCode() == null || body.getCode() != 0 || body.getData() == null) {
                    throw new IllegalStateException("user-status query failed");
                }
                if (!(body.getData().get("active") instanceof Boolean active)) {
                    throw new IllegalStateException("user-status response missing active");
                }
                return active;
            } catch (Exception ex) {
                throw new IllegalStateException("user-status query failed", ex);
            }
        });
    }
}
