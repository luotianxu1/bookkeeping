package com.example.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtTokenAuthenticationFilter extends OncePerRequestFilter {

    /** 登录时由 auth-service 写入的 httpOnly cookie 名称 */
    public static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtTokenService jwtTokenService;
    private final UserStatusChecker userStatusChecker;

    public JwtTokenAuthenticationFilter(JwtTokenService jwtTokenService, UserStatusChecker userStatusChecker) {
        this.jwtTokenService = jwtTokenService;
        this.userStatusChecker = userStatusChecker;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtTokenService.parse(token);
            String username = claims.getSubject();
            Number userIdClaim = claims.get("userId", Number.class);
            // 旧格式 token（无 userId claim）不建立认证，引导客户端重新登录
            if (username != null && userIdClaim != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {
                long userId = userIdClaim.longValue();
                // 已注销/禁用的用户即使持有未过期 token 也拒绝认证（30 秒内生效）
                if (userStatusChecker.isActive(userId)) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    request.setAttribute(SecurityUtils.USER_ID_ATTRIBUTE, userId);
                }
            }
        } catch (JwtException ignored) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /** Authorization 头优先（兼容旧客户端），其次 httpOnly cookie */
    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }

        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if (ACCESS_TOKEN_COOKIE.equals(cookie.getName()) && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
