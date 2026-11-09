package com.example.auth.controller;

import com.example.common.result.Result;
import com.example.common.security.FamilyScopeChecker;
import com.example.common.security.UserStatusChecker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 服务间内部接口：用户状态与家庭可读范围查询。
 * 供无业务库连接的服务（market-service）远程调用，经 InternalApiKeyFilter 的 X-Internal-Key 鉴权。
 */
@RestController
@RequestMapping("/api/internal/auth")
@Tag(name = "内部 · 用户安全查询", description = "用户状态与家庭范围的服务间查询接口")
public class InternalAuthController {

    private final UserStatusChecker userStatusChecker;
    private final FamilyScopeChecker familyScopeChecker;
    private final com.example.common.security.SessionChecker sessionChecker;

    public InternalAuthController(UserStatusChecker userStatusChecker, FamilyScopeChecker familyScopeChecker, com.example.common.security.SessionChecker sessionChecker) {
        this.userStatusChecker = userStatusChecker;
        this.familyScopeChecker = familyScopeChecker;
        this.sessionChecker = sessionChecker;
    }

    @GetMapping("/session-status")
    public Result<Boolean> sessionStatus(@RequestParam("userId") long userId,
                                         @RequestParam("sessionId") String sessionId) {
        return Result.ok(sessionChecker.isValid(userId, sessionId));
    }

    @GetMapping("/user-status")
    @Operation(summary = "查询用户是否处于 active 状态")
    public Result<Map<String, Object>> userStatus(@RequestParam("userId") long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("active", userStatusChecker.isActiveFresh(userId));
        return Result.ok(data);
    }

    @GetMapping("/readable-user-ids")
    @Operation(summary = "查询用户可读的家庭用户 ID 集合")
    public Result<Set<Long>> readableUserIds(@RequestParam("userId") long userId) {
        return Result.ok(familyScopeChecker.readableUserIds(userId));
    }
}
