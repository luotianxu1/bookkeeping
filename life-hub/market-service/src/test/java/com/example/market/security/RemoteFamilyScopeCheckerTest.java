package com.example.market.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RemoteFamilyScopeCheckerTest {
    @Test void expiredFamilyScopeCannotBeRenewedByRepeatedFailuresAndRecoversWithFreshMembers() {
        var clock = new AtomicLong();
        var rest = new RestTemplate();
        var server = MockRestServiceServer.bindTo(rest).build();
        var checker = new RemoteFamilyScopeChecker("http://auth/", "test-internal", rest, clock::get);
        server.expect(requestTo("http://auth/api/internal/auth/readable-user-ids?userId=1"))
            .andExpect(header("X-Internal-Key", "test-internal"))
            .andRespond(withSuccess("{\"code\":0,\"data\":[1,2]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://auth/api/internal/auth/readable-user-ids?userId=1"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("http://auth/api/internal/auth/readable-user-ids?userId=1"))
            .andRespond(withSuccess("{\"code\":500,\"data\":[1,2]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://auth/api/internal/auth/readable-user-ids?userId=1"))
            .andRespond(withSuccess("{\"code\":0,\"data\":[1,3]}", MediaType.APPLICATION_JSON));

        assertEquals(Set.of(1L, 2L), checker.readableUserIds(1));
        clock.set(59_999);
        assertTrue(checker.canRead(1, 2));
        clock.set(60_000);
        assertEquals(Set.of(1L), checker.readableUserIds(1));
        clock.set(69_999); // 故障退避期间不请求，但也不能重新信任旧范围。
        assertFalse(checker.canRead(1, 2));
        clock.set(70_000);
        assertEquals(Set.of(1L), checker.readableUserIds(1));
        clock.set(80_000);
        var refreshed = checker.readableUserIds(1);
        assertEquals(Set.of(1L, 3L), refreshed);
        assertThrows(UnsupportedOperationException.class, () -> refreshed.add(2L));
        assertFalse(checker.canRead(1, 2));
        server.verify();
    }

    @Test void firstFailureAllowsOnlySelfAndDoesNotAffectAnotherUsersScope() {
        var clock = new AtomicLong();
        var rest = new RestTemplate();
        var server = MockRestServiceServer.bindTo(rest).build();
        var checker = new RemoteFamilyScopeChecker("http://auth", "key", rest, clock::get);
        server.expect(requestTo("http://auth/api/internal/auth/readable-user-ids?userId=1"))
            .andRespond(withSuccess("{\"code\":0,\"data\":null}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://auth/api/internal/auth/readable-user-ids?userId=2"))
            .andRespond(withSuccess("{\"code\":0,\"data\":[2,3]}", MediaType.APPLICATION_JSON));
        assertEquals(Set.of(1L), checker.readableUserIds(1));
        assertEquals(Set.of(2L, 3L), checker.readableUserIds(2));
        assertEquals(Set.of(1L), checker.readableUserIds(1));
        server.verify();
    }
}
