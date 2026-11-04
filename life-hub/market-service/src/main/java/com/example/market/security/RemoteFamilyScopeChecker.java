package com.example.market.security;

import com.example.common.result.Result;
import com.example.common.security.FamilyScopeChecker;
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
import java.util.Set;

/**
 * 家庭可读范围的远程实现：经 auth-service 内部接口查询（X-Internal-Key 鉴权）。
 * market-service 不连业务库；查询失败时降级为仅本人可见（fail-closed，与 JDBC 实现口径一致）。
 */
@Component
public class RemoteFamilyScopeChecker implements FamilyScopeChecker {

    private final RestTemplate restTemplate;
    private final String authBaseUrl;
    private final String internalApiKey;
    /** 家庭范围变化频率极低，缓存 60 秒；失败降级窗口 10 秒内回吐旧值 */
    private final KeyedTtlCache<Long, Set<Long>> cache =
        new KeyedTtlCache<>(60_000L, 10_000L, 10_000);

    public RemoteFamilyScopeChecker(
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
    public Set<Long> readableUserIds(long selfId) {
        Set<Long> cached = cache.get(selfId, id -> {
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
                return body.getData();
            } catch (Exception ex) {
                throw new IllegalStateException("readable-user-ids query failed", ex);
            }
        });
        // fail-closed：远端不可用且无缓存时仅本人可见
        return cached == null ? Set.of(selfId) : cached;
    }
}
