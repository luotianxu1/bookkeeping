package com.example.auth.controller;

import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.LoginResponse;
import com.example.auth.model.UserAccount;
import com.example.auth.security.JwtService;
import com.example.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "登录成功",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = LoginResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "accessToken": "eyJhbGciOiJIUzI1NiJ9.xxx.yyy",
                      "tokenType": "Bearer",
                      "expiresIn": 7200
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "用户名或密码错误",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "timestamp": "2026-05-01T10:00:00.000+00:00",
                      "status": 401,
                      "error": "Unauthorized",
                      "path": "/api/auth/login"
                    }
                    """)
            )
        )
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        UserAccount user = authService.authenticate(request.username(), request.password()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        authService.markLoginSuccess(user.id());
        String token = jwtService.generateToken(user.username());
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds()));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前登录用户", description = "需要在 Authorization 头中传入 Bearer Token")
    public ResponseEntity<Map<String, String>> me(@AuthenticationPrincipal String username) {
        return ResponseEntity.ok(Map.of("username", username));
    }
}
