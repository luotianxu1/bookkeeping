package com.example.market.security;

import com.example.common.result.Result;
import com.example.common.security.SessionChecker;
import com.example.common.security.UserStatusUnavailableException;
import com.example.common.security.InternalApiKeyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;

@Component
public class RemoteSessionChecker implements SessionChecker {
    private final RestTemplate http;
    private final String baseUrl;
    private final String apiKey;
    public RemoteSessionChecker(@Value("${market.auth-base-url:http://localhost:8081}") String baseUrl,
                                @Value("${internal.api-key:dev-internal-key}") String apiKey) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiKey = apiKey;
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = new RestTemplate(factory);
    }
    @Override
    public boolean isValid(long userId, String sessionId) {
        if (sessionId == null || !sessionId.matches("[0-9a-fA-F-]{36}")) return false;
        try {
            var headers = new HttpHeaders();
            headers.set(InternalApiKeyFilter.HEADER_NAME, apiKey);
            var response = http.exchange(baseUrl + "/api/internal/auth/session-status?userId={userId}&sessionId={sessionId}",
                HttpMethod.GET, new HttpEntity<Void>(headers), new ParameterizedTypeReference<Result<Boolean>>() {}, userId, sessionId);
            var body = response.getBody();
            if (body == null || body.getCode() == null || body.getCode() != 0 || body.getData() == null) {
                throw new UserStatusUnavailableException();
            }
            return body.getData();
        } catch (Exception ex) {
            throw new UserStatusUnavailableException();
        }
    }
}
