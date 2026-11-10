package com.example.auth.admin;

import com.example.common.result.Result;
import com.example.common.security.JwtTokenAuthenticationFilter;
import com.example.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import static com.example.auth.admin.AdminModels.*;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "PC 管理", description = "用户、角色与权限管理；逐请求校验角色权限")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) { this.service = service; }
    private Actor actor(HttpServletRequest request) {
        return new Actor(SecurityUtils.requireCurrentUserId(), (String) request.getAttribute(JwtTokenAuthenticationFilter.SESSION_ID_ATTRIBUTE));
    }

    @GetMapping("/session")
    public Result<SessionView> session(HttpServletRequest request) { return Result.ok(service.session(actor(request))); }

    @GetMapping("/users")
    public Result<Page<UserView>> users(HttpServletRequest request,
        @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "") String status,
        @RequestParam(defaultValue = "") String roleCode, @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.users(actor(request), keyword, status, roleCode, page, pageSize));
    }
    @PostMapping("/users")
    public Result<UserView> createUser(HttpServletRequest request, @Valid @RequestBody CreateUser body) {
        return Result.ok(service.createUser(actor(request), body));
    }
    @PutMapping("/users/{id}")
    public Result<UserView> updateUser(HttpServletRequest request, @PathVariable long id, @Valid @RequestBody UpdateUser body) {
        return Result.ok(service.updateUser(actor(request), id, body));
    }
    @PutMapping("/users/{id}/status")
    public Result<UserView> status(HttpServletRequest request, @PathVariable long id, @Valid @RequestBody UserStatus body) {
        return Result.ok(service.changeUserStatus(actor(request), id, body.status()));
    }
    @PutMapping("/users/{id}/role")
    public Result<UserView> role(HttpServletRequest request, @PathVariable long id, @Valid @RequestBody UserRole body) {
        return Result.ok(service.assignRole(actor(request), id, body.roleCode()));
    }
    @GetMapping("/roles/options")
    public Result<List<RoleView>> options(HttpServletRequest request) { return Result.ok(service.roleOptions(actor(request))); }
    @GetMapping("/roles")
    public Result<List<RoleView>> roles(HttpServletRequest request) { return Result.ok(service.roles(actor(request))); }
    @PostMapping("/roles")
    public Result<RoleView> createRole(HttpServletRequest request, @Valid @RequestBody CreateRole body) {
        return Result.ok(service.createRole(actor(request), body));
    }
    @PutMapping("/roles/{id}")
    public Result<RoleView> updateRole(HttpServletRequest request, @PathVariable long id, @Valid @RequestBody UpdateRole body) {
        return Result.ok(service.updateRole(actor(request), id, body));
    }
    @DeleteMapping("/roles/{id}")
    public Result<Void> deleteRole(HttpServletRequest request, @PathVariable long id) {
        service.deleteRole(actor(request), id); return Result.ok();
    }
    @GetMapping("/permissions")
    public Result<List<AdminPermissions.Permission>> permissions(HttpServletRequest request) { return Result.ok(service.permissions(actor(request))); }
    @PutMapping("/roles/{id}/permissions")
    public Result<RoleView> grant(HttpServletRequest request, @PathVariable long id, @Valid @RequestBody GrantPermissions body) {
        return Result.ok(service.grant(actor(request), id, body.permissions()));
    }
}
