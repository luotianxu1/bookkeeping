package com.example.auth.controller;

import com.example.auth.common.result.Result;
import com.example.auth.dto.CurrentUserResponse;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.LoginResponse;
import com.example.auth.model.UserAccount;
import com.example.auth.security.JwtService;
import com.example.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证", description = "登录与用户身份相关接口")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回 JWT 访问令牌")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        UserAccount user = authService.authenticate(request.username(), request.password()).orElse(null);
        if (user == null) {
            return Result.<LoginResponse>fail().code(401).message("用户名或密码错误");
        }
        authService.markLoginSuccess(user.id());
        String token = jwtService.generateToken(user.username());
        return Result.ok(new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds()));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前登录用户", description = "需要在 Authorization 头中传入 Bearer Token")
    public Result<CurrentUserResponse> me(@AuthenticationPrincipal String username) {
        UserAccount user = authService.getCurrentUser(username).orElse(null);
        if (user == null) {
            return Result.<CurrentUserResponse>fail().code(401).message("未登录或登录已过期");
        }

        return Result.ok(new CurrentUserResponse(
            user.id(),
            user.username(),
            user.phone(),
            user.email(),
            user.displayName(),
            user.avatarUrl(),
            user.roleName()
        ));
    }
}
