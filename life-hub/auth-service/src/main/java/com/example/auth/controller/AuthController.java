package com.example.auth.controller;

import com.example.auth.dto.CurrentUserResponse;
import com.example.auth.dto.FamilyOverviewResponse;
import com.example.auth.dto.JoinFamilyRequest;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.LoginResponse;
import com.example.auth.dto.RegisterRequest;
import com.example.auth.security.JwtService;
import com.example.auth.security.LoginRateLimiter;
import com.example.auth.security.RegisterRateLimiter;
import com.example.auth.service.AuthService;
import com.example.common.result.Result;
import com.example.common.security.JwtTokenAuthenticationFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证", description = "登录与用户身份相关接口")
public class AuthController {

    private final AuthService authService;
    private final com.example.auth.service.AccountSecurityService accountSecurityService;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;
    private final RegisterRateLimiter registerRateLimiter;

    /** https 部署时通过环境变量 AUTH_COOKIE_SECURE=true 开启 Secure 标记 */
    @Value("${auth.cookie.secure:false}")
    private boolean cookieSecure;

    public AuthController(
        AuthService authService,
        com.example.auth.service.AccountSecurityService accountSecurityService,
        JwtService jwtService,
        LoginRateLimiter loginRateLimiter,
        RegisterRateLimiter registerRateLimiter
    ) {
        this.authService = authService;
        this.accountSecurityService = accountSecurityService;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
        this.registerRateLimiter = registerRateLimiter;
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回 JWT 访问令牌并写入 httpOnly Cookie")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String login = request.username().trim();
        var attempt = loginRateLimiter.tryAcquire(login, httpRequest.getRemoteAddr());
        if (attempt == null) {
            return Result.<LoginResponse>fail().code(429).message("登录尝试次数过多，请 15 分钟后再试");
        }
        com.example.auth.service.AccountSecurityService.LoginSession user = null;
        try {
            user = accountSecurityService.login(login, request.password(), httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr()).orElse(null);
        } finally {
            attempt.complete(user != null);
        }
        if (user == null) {
            return Result.<LoginResponse>fail().code(401).message("用户名或密码错误");
        }
        String token = jwtService.generateToken(user.username(), user.userId(), user.sessionId());
        writeTokenCookie(httpResponse, token, jwtService.getExpirationSeconds());
        return Result.ok(new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds()));
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "清除登录 Cookie")
    public Result<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Long userId = com.example.common.security.SecurityUtils.currentUserId();
        String sessionId = (String) httpRequest.getAttribute(JwtTokenAuthenticationFilter.SESSION_ID_ATTRIBUTE);
        if (userId != null && sessionId != null) accountSecurityService.revokeSession(userId, sessionId, sessionId);
        writeTokenCookie(httpResponse, "", 0);
        return Result.<Void>ok().message("已登出");
    }

    private void writeTokenCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(JwtTokenAuthenticationFilter.ACCESS_TOKEN_COOKIE, token)
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .sameSite("Lax")
            .maxAge(maxAgeSeconds)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "使用用户名和密码注册新用户（IP 限流，防批量枚举）")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String rateLimitKey = "register|" + httpRequest.getRemoteAddr();
        if (registerRateLimiter.isBlocked(rateLimitKey)) {
            return Result.<Void>fail().code(429).message("注册尝试过于频繁，请 15 分钟后再试");
        }
        registerRateLimiter.record(rateLimitKey);
        authService.register(request);
        return Result.<Void>ok().message("注册成功");
    }

    @GetMapping("/family")
    @Operation(summary = "获取当前家庭信息", description = "返回邀请码、已绑定成员和成员总数")
    public Result<FamilyOverviewResponse> family(@AuthenticationPrincipal String username) {
        return Result.ok(authService.getFamilyOverview(username));
    }

    @PostMapping("/family/create")
    @Operation(summary = "创建当前用户家庭", description = "当前未加入任何家庭时，生成自己的邀请码和家庭")
    public Result<FamilyOverviewResponse> createFamily(@AuthenticationPrincipal String username) {
        return Result.ok(authService.createFamily(username));
    }

    @PostMapping("/family/join")
    @Operation(summary = "通过邀请码加入家庭", description = "用户未加入任何家庭时，可通过邀请码加入")
    public Result<FamilyOverviewResponse> joinFamily(
        @AuthenticationPrincipal String username,
        @Valid @RequestBody JoinFamilyRequest request
    ) {
        return Result.ok(authService.joinFamily(username, request));
    }

    @DeleteMapping("/family/members/{userId}")
    @Operation(summary = "解绑家庭成员", description = "仅家庭管理员可以解绑其他已绑定成员")
    public Result<FamilyOverviewResponse> unbindFamilyMember(
        @AuthenticationPrincipal String username,
        @PathVariable("userId") Long userId
    ) {
        return Result.ok(authService.unbindFamilyMember(username, userId));
    }

    @DeleteMapping("/account")
    @Operation(summary = "注销当前账号", description = "停用当前登录账号，并按规则处理家庭归属")
    public Result<Void> deleteAccount(@AuthenticationPrincipal String username, HttpServletResponse httpResponse) {
        authService.deleteAccount(username);
        // 账号已停用，立即清除登录 Cookie
        writeTokenCookie(httpResponse, "", 0);
        return Result.<Void>ok().message("账号已注销");
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前登录用户", description = "需要在 Authorization 头中传入 Bearer Token")
    @Parameter(
        name = HttpHeaders.AUTHORIZATION,
        description = "访问令牌，格式：Bearer {token}",
        in = ParameterIn.HEADER,
        required = true,
        example = "Bearer eyJhbGciOiJIUzM4NCJ9.xxx"
    )
    public Result<CurrentUserResponse> me(@AuthenticationPrincipal String username) {
        return Result.ok(accountSecurityService.currentUser(com.example.common.security.SecurityUtils.requireCurrentUserId()));
    }
}
