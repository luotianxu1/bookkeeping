package com.example.auth.controller;

import com.example.auth.dto.LoginRequest;
import com.example.auth.security.*;
import com.example.auth.service.AccountSecurityService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthLoginTest {
    @Test void loginNormalizesAccountButPreservesPasswordAndBlocksWhitespaceBypass() {
        var security = mock(AccountSecurityService.class);
        when(security.login(anyString(), anyString(), any(), any())).thenReturn(Optional.empty());
        var controller = new AuthController(null, security, null, new LoginRateLimiter(), new RegisterRateLimiter());
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.9");
        var response = new MockHttpServletResponse();
        for (int i = 0; i < 5; i++) {
            assertEquals(401, controller.login(new LoginRequest(" user1 ", " password "), request, response).getCode());
        }
        assertEquals(429, controller.login(new LoginRequest("user1\t", " password "), request, response).getCode());
        verify(security, times(5)).login(eq("user1"), eq(" password "), any(), eq("127.0.0.9"));
    }
}
