package com.example.market.security;

import com.example.common.security.UserStatusUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RemoteUserStatusCheckerTest {
    @Test void freshRecheckBypassesDisabledCacheAndFailsClosedOnUnavailableIdentityService() {
        var checker = new RemoteUserStatusChecker("http://auth", "internal-test");
        var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(checker, "restTemplate")).build();
        String url = "http://auth/api/internal/auth/user-status?userId=1";
        server.expect(requestTo(url)).andExpect(header("X-Internal-Key", "internal-test"))
            .andRespond(withSuccess("{\"code\":0,\"data\":{\"active\":false}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(url)).andRespond(withSuccess("{\"code\":0,\"data\":{\"active\":true}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(url)).andRespond(withServerError());
        assertFalse(checker.isActive(1));
        assertFalse(checker.isActive(1));
        assertTrue(checker.isActiveFresh(1));
        assertThrows(UserStatusUnavailableException.class, () -> checker.isActiveFresh(1));
        server.verify();
    }
}
