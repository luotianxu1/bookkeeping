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

class RemoteSessionCheckerTest {
    @Test void checksEveryRequestAndFailsClosedWithoutStaleSuccess() {
        var checker = new RemoteSessionChecker("http://auth", "internal-test");
        var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(checker, "http")).build();
        String id = "11111111-1111-1111-1111-111111111111";
        String url = "http://auth/api/internal/auth/session-status?userId=1&sessionId=" + id;
        server.expect(requestTo(url)).andExpect(header("X-Internal-Key", "internal-test"))
            .andRespond(withSuccess("{\"code\":0,\"data\":true}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(url)).andRespond(withSuccess("{\"code\":0,\"data\":false}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(url)).andRespond(withServerError());
        assertTrue(checker.isValid(1, id));
        assertFalse(checker.isValid(1, id));
        assertThrows(UserStatusUnavailableException.class, () -> checker.isValid(1, id));
        assertFalse(checker.isValid(1, null));
        server.verify();
    }
}
