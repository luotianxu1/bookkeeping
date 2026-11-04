package com.example.market.security;

import com.example.common.result.Result;
import com.example.common.security.InternalApiKeyFilter;
import com.example.common.security.UserStatusChecker;
import com.example.common.util.KeyedTtlCache;
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
 * 口径与 JdbcUserStatusChecker 一致：30 秒本地缓存（KeyedTtlCache），查询失败时放行（fail-open）。
 */
@Component
public class RemoteUserStatusChecker implements UserStatusChecker {

    private static final long CACHE_TTL_MS = 30_000L;
    private static final long FAILURE_TTL_MS = 5_000L;
    private static final int MAX_SIZE = 10_000;

    private final RestTemplate restTemplate;
    private final String authBaseUrl;
    private final String internalApiKey;
    private final KeyedTtlCache<Long, Boolean> cache =
        new KeyedTtlCache<>(CACHE_TTL_MS, FAILURE_TTL_MS, MAX_SIZE);

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
        Boolean cached = cache.get(userId, id -> {
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
                return Boolean.TRUE.equals(body.getData().get("active"));
            } catch (Exception ex) {
                throw new IllegalStateException("user-status query failed", ex);
            }
        });
        // fail-open：远端抖动且无缓存可用时放行
        return cached == null || cached;
    }
}
