package com.example.market.security;

import com.example.common.result.Result;
import com.example.common.security.FamilyScopeChecker;
import com.example.common.util.KeyedTtlCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Set;
import java.util.HashSet;
import java.util.function.LongSupplier;

/**
 * 家庭可读范围的远程实现：经 auth-service 内部接口查询（X-Internal-Key 鉴权）。
 * market-service 不连业务库；查询失败时降级为仅本人可见（fail-closed，与 JDBC 实现口径一致）。
 */
@Component
public class RemoteFamilyScopeChecker implements FamilyScopeChecker {

    private final RestTemplate restTemplate;
    private final String authBaseUrl;
    private final String internalApiKey;
    private static final long TRUST_MILLIS = 60_000L;
    private record VerifiedScope(Set<Long> userIds, long expiresAt) {}
    private final LongSupplier clock;
    // 失败缓存只限制回源频率，不能延长已验证家庭权限的有效期。
    private final KeyedTtlCache<Long, VerifiedScope> cache;

    @Autowired
    public RemoteFamilyScopeChecker(
        @Value("${market.auth-base-url:http://localhost:8081}") String authBaseUrl,
        @Value("${internal.api-key:dev-internal-key}") String internalApiKey
    ) {
        this(authBaseUrl, internalApiKey, buildRestTemplate(), System::currentTimeMillis);
    }

    RemoteFamilyScopeChecker(String authBaseUrl, String internalApiKey, RestTemplate restTemplate, LongSupplier clock) {
        this.authBaseUrl = authBaseUrl.replaceAll("/$", "");
        this.internalApiKey = internalApiKey;
        this.restTemplate = restTemplate;
        this.clock = clock;
        this.cache = new KeyedTtlCache<>(TRUST_MILLIS, 10_000L, 10_000, clock);
    }

    private static RestTemplate buildRestTemplate() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }

    @Override
    public Set<Long> readableUserIds(long selfId) {
        VerifiedScope cached = cache.get(selfId, id -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set(com.example.common.security.InternalApiKeyFilter.HEADER_NAME, internalApiKey);
                ResponseEntity<Result<Set<Long>>> response = restTemplate.exchange(
                    authBaseUrl + "/api/internal/auth/readable-user-ids?userId=" + id,
                    HttpMethod.GET,
                    new HttpEntity<Void>(headers),
                    new ParameterizedTypeReference<>() {
                    }
                );
                Result<Set<Long>> body = response.getBody();
                if (body == null || body.getCode() == null || body.getCode() != 0 || body.getData() == null) {
                    throw new IllegalStateException("readable-user-ids query failed");
                }
                Set<Long> userIds = new HashSet<>(body.getData());
                userIds.add(id);
                return new VerifiedScope(Set.copyOf(userIds), clock.getAsLong() + TRUST_MILLIS);
            } catch (Exception ex) {
                throw new IllegalStateException("readable-user-ids query failed", ex);
            }
        });
        // 缓存工具可能反复回吐旧值；权限始终以最后一次成功验证的期限为准。
        return cached == null || clock.getAsLong() >= cached.expiresAt() ? Set.of(selfId) : cached.userIds();
    }
}
