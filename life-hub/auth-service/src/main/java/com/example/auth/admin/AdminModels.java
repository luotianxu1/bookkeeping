package com.example.auth.admin;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class AdminModels {
    public record Actor(long id, String sessionId) {}
    public record UserView(long id, String username, String displayName, String phone, String email,
                           String status, String roleCode, String roleName, LocalDateTime lastLoginAt,
                           LocalDateTime createdAt) {}
    public record RoleView(long id, String code, String name, String description, String status,
                           boolean builtIn, long userCount, Set<String> permissions) {}
    public record SessionView(UserView user, Set<String> permissions) {}
    public record Page<T>(List<T> items, long total, int page, int pageSize) {}
    public record CreateUser(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[a-zA-Z0-9_.-]{3,64}", message = "用户名需为 3–64 位字母、数字、下划线、点或短横线") String username,
        @NotBlank @Size(min = 6, max = 64) String password,
        @NotBlank @Size(max = 80) String displayName,
        @Size(max = 32) @Pattern(regexp = "[+0-9 ()-]*", message = "手机号格式不正确") String phone,
        @Email @Size(max = 255) String email
    ) {}
    public record UpdateUser(@NotBlank @Size(max = 80) String displayName,
                             @Size(max = 32) @Pattern(regexp = "[+0-9 ()-]*", message = "手机号格式不正确") String phone,
                             @Email @Size(max = 255) String email) {}
    public record UserStatus(@NotNull @Pattern(regexp = "active|disabled|locked") String status) {}
    public record UserRole(@NotBlank @Size(max = 64) String roleCode) {}
    public record CreateRole(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{2,63}", message = "角色编码需为 3–64 位小写字母、数字或下划线，以字母开头") String code,
        @NotBlank @Size(max = 80) String name,
        @NotNull @Size(max = 255) String description
    ) {}
    public record UpdateRole(@NotBlank @Size(max = 80) String name,
                             @NotNull @Size(max = 255) String description,
                             @NotNull @Pattern(regexp = "active|disabled") String status) {}
    public record GrantPermissions(@NotNull @Size(max = 100) List<@NotBlank String> permissions) {}
    private AdminModels() {}
}
