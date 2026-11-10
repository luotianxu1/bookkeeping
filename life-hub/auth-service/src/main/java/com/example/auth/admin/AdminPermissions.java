package com.example.auth.admin;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Only executable server permissions are assignable; arbitrary UI-only keys cannot grant access. */
public final class AdminPermissions {
    public record Permission(String code, String name, String group, String description, List<String> requires) {}

    public static final List<Permission> ALL = List.of(
        new Permission("pc.access", "访问管理端", "工作台", "登录 PC 管理端并查看项目功能总览", List.of()),
        permission("user.read", "查看用户", "用户管理", "查询用户列表、资料与状态"),
        permission("user.create", "新建用户", "用户管理", "创建普通用户账号", "user.read"),
        permission("user.update", "编辑用户", "用户管理", "修改用户昵称、手机号与邮箱", "user.read"),
        permission("user.delete", "删除用户", "用户管理", "永久删除用户及其个人业务数据，并撤销全部会话", "user.read"),
        permission("user.status", "变更用户状态", "用户管理", "启用、停用或锁定用户；停用时撤销登录会话", "user.read"),
        permission("user.assign-role", "分配用户角色", "用户管理", "为用户分配授权范围内的角色", "user.read"),
        permission("role.read", "查看角色", "角色管理", "查看角色、成员数量与授权"),
        permission("role.create", "新建角色", "角色管理", "新建自定义角色", "role.read"),
        permission("role.update", "编辑角色", "角色管理", "修改自定义角色资料与状态", "role.read"),
        permission("role.delete", "删除角色", "角色管理", "删除尚未关联用户的自定义角色", "role.read"),
        permission("permission.read", "查看权限", "权限管理", "查看系统权限目录与角色授权", "role.read"),
        permission("permission.assign", "配置权限", "权限管理", "配置自定义角色权限，不能授予自己未持有的权限", "permission.read", "role.read")
    );
    public static final Set<String> CODES = ALL.stream().map(Permission::code).collect(Collectors.toUnmodifiableSet());

    private static Permission permission(String code, String name, String group, String description, String... requires) {
        var dependencies = new java.util.ArrayList<>(List.of("pc.access"));
        dependencies.addAll(List.of(requires));
        return new Permission(code, name, group, description, List.copyOf(dependencies));
    }

    public static Set<String> validate(List<String> codes) {
        if (codes == null || codes.stream().anyMatch(code -> code == null || !CODES.contains(code))) {
            throw new IllegalArgumentException("包含未知权限，请刷新权限目录");
        }
        Set<String> permissions = Set.copyOf(codes);
        for (Permission permission : ALL) {
            if (permissions.contains(permission.code()) && !permissions.containsAll(permission.requires())) {
                throw new IllegalArgumentException(permission.name() + "缺少必要的查看或访问权限");
            }
        }
        return permissions;
    }

    private AdminPermissions() {}
}
