package com.example.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class UserIdentityScopeFilterTest {
    private final ObjectMapper json = new ObjectMapper();
    private final FamilyScopeChecker family = id -> Set.of(id, 2L);
    private final UserIdentityScopeFilter filter = new UserIdentityScopeFilter(json, family, new SecurityErrorWriter(json));
    @AfterEach void clear() { SecurityContextHolder.clearContext(); org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }

    private MockHttpServletRequest request(String method) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("self", null, java.util.List.of()));
        var request = new MockHttpServletRequest(method, "/api/finance/transactions");
        request.setAttribute(SecurityUtils.USER_ID_ATTRIBUTE, 1L);
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        return request;
    }
    @Test void allowsFamilyReadsButRejectsOutsiders() throws Exception {
        var request = request("GET");
        request.addParameter("userId", "2");
        var reached = new AtomicReference<HttpServletRequest>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> reached.set((HttpServletRequest) req));
        assertEquals("2", reached.get().getParameter("userId"));
        var outsider = request("GET");
        outsider.addParameter("userIds", "1,3");
        var response = new MockHttpServletResponse();
        filter.doFilter(outsider, response, (req, res) -> fail("outside family was allowed"));
        assertEquals(403, response.getStatus());
    }
    @Test void writesAlwaysUseAuthenticatedUser() throws Exception {
        var request = request("POST");
        request.addParameter("userId", "2");
        request.setContentType("application/json");
        request.setContent("{\"userId\":2,\"amount\":100}".getBytes());
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertEquals("1", req.getParameter("userId"));
            assertEquals(1, json.readTree(req.getInputStream()).get("userId").asLong());
        });
        assertThrows(IllegalArgumentException.class, () -> family.requireSelf(1, 2L, "只读"));
    }
}
