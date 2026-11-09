package com.example.auth.controller;

import com.example.auth.dto.AccountSecurityRequests.*;
import com.example.auth.dto.CurrentUserResponse;
import com.example.auth.security.SecurityAttemptLimiter;
import com.example.auth.service.AccountSecurityService;
import com.example.common.result.Result;
import com.example.common.security.JwtTokenAuthenticationFilter;
import com.example.common.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AccountSecurityController {
    private final AccountSecurityService service;
    private final SecurityAttemptLimiter limiter;
    @Value("${auth.cookie.secure:false}") private boolean secure;
    public AccountSecurityController(AccountSecurityService service, SecurityAttemptLimiter limiter) {
        this.service = service;
        this.limiter = limiter;
    }

    @PutMapping("/profile")
    public Result<CurrentUserResponse> profile(@Valid @RequestBody Profile body, HttpServletRequest request) {
        return Result.ok(service.updateProfile(SecurityUtils.requireCurrentUserId(), session(request), body));
    }

    @PostMapping("/password/change")
    public Result<Void> password(@Valid @RequestBody Password body, HttpServletRequest request, HttpServletResponse response) {
        long userId = SecurityUtils.requireCurrentUserId();
        if (!limiter.allow("verify-password", Long.toString(userId), request.getRemoteAddr())) return limited(response);
        service.changePassword(userId, session(request), body.currentPassword(), body.newPassword());
        clearCookie(response);
        return Result.<Void>ok().message("密码已修改，请重新登录");
    }

    @GetMapping("/recovery-code")
    public Result<AccountSecurityService.RecoveryStatus> recoveryStatus() {
        return Result.ok(service.recoveryStatus(SecurityUtils.requireCurrentUserId()));
    }

    @PostMapping("/recovery-code")
    public Result<Map<String, String>> recoveryCode(@Valid @RequestBody RecoveryCode body, HttpServletRequest request, HttpServletResponse response) {
        long userId = SecurityUtils.requireCurrentUserId();
        if (!limiter.allow("verify-password", Long.toString(userId), request.getRemoteAddr())) return limited(response);
        String code = service.generateRecoveryCode(userId, session(request), body.currentPassword());
        response.setHeader("Cache-Control", "no-store");
        return Result.ok(Map.of("code", code));
    }

    @PostMapping("/password/reset")
    public Result<Void> reset(@Valid @RequestBody Reset body, HttpServletRequest request, HttpServletResponse response) {
        if (!limiter.allow("reset-password", body.username().trim().toLowerCase(Locale.ROOT), request.getRemoteAddr())) return limited(response);
        service.resetPassword(body.username(), body.recoveryCode(), body.newPassword());
        clearCookie(response);
        return Result.<Void>ok().message("密码已重设，请重新登录并生成新的恢复码");
    }

    @GetMapping("/sessions")
    public Result<List<AccountSecurityService.SessionView>> sessions(HttpServletRequest request) {
        return Result.ok(service.sessions(SecurityUtils.requireCurrentUserId(), session(request)));
    }

    @DeleteMapping("/sessions/{id}")
    public Result<Void> revoke(@PathVariable("id") String id, HttpServletRequest request, HttpServletResponse response) {
        service.revokeSession(SecurityUtils.requireCurrentUserId(), session(request), id);
        if (id.equals(session(request))) clearCookie(response);
        return Result.ok();
    }

    @PostMapping("/sessions/revoke-others")
    public Result<Void> revokeOthers(HttpServletRequest request) {
        service.revokeOtherSessions(SecurityUtils.requireCurrentUserId(), session(request));
        return Result.ok();
    }

    private String session(HttpServletRequest request) {
        return (String) request.getAttribute(JwtTokenAuthenticationFilter.SESSION_ID_ATTRIBUTE);
    }
    private <T> Result<T> limited(HttpServletResponse response) {
        response.setStatus(429);
        return Result.<T>fail().code(429).message("操作过于频繁，请 15 分钟后重试");
    }
    private void clearCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(JwtTokenAuthenticationFilter.ACCESS_TOKEN_COOKIE, "")
            .httpOnly(true).secure(secure).path("/").sameSite("Lax").maxAge(0).build().toString());
    }
}
