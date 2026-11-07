package com.example.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenAuthenticationFilterTest {
    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void unavailableStatusReturns503AndPreservesCookie() throws Exception {
        var jwt = new JwtTokenService(SECRET);
        var filter = new JwtTokenAuthenticationFilter(jwt, id -> { throw new UserStatusUnavailableException(); }, new SecurityErrorWriter(new ObjectMapper()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/finance/accounts");
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder().subject("alice").claim("userId", 1L).issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
        request.setCookies(new jakarta.servlet.http.Cookie("access_token", token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> reached.set(true));
        assertEquals(503, response.getStatus());
        assertEquals("5", response.getHeader("Retry-After"));
        assertFalse(reached.get());
        assertNull(response.getHeader("Set-Cookie"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
