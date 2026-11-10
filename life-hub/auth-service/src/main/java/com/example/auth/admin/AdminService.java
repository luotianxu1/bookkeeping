package com.example.auth.admin;

import com.example.auth.service.AccountSecurityService;
import com.example.common.security.UserIdentityMissingException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import static com.example.auth.admin.AdminModels.*;

@Service
public class AdminService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private static final String USER_SELECT = """
        SELECT u.id, u.username, u.display_name, u.phone, u.email, u.status, u.role_name AS role_code,
               COALESCE(r.name, u.role_name) AS role_label, u.last_login_at, u.created_at
        FROM users u LEFT JOIN admin_roles r ON r.code = u.role_name
        """;
    private static final RowMapper<UserView> USER_MAPPER = (rs, row) -> new UserView(
        rs.getLong("id"), rs.getString("username"), rs.getString("display_name"), rs.getString("phone"),
        rs.getString("email"), rs.getString("status"), rs.getString("role_code"), rs.getString("role_label"),
        date(rs.getTimestamp("last_login_at")), date(rs.getTimestamp("created_at")));
    private record Role(long id, String code, String name, String description, String status, boolean builtIn) {}
    private static final RowMapper<Role> ROLE_MAPPER = (rs, row) -> new Role(rs.getLong("id"), rs.getString("code"),
        rs.getString("name"), rs.getString("description"), rs.getString("status"), rs.getBoolean("built_in"));

    public AdminService(JdbcTemplate jdbc, PasswordEncoder encoder) { this.jdbc = jdbc; this.encoder = encoder; }

    @Transactional(readOnly = true)
    public SessionView session(Actor actor) { return require(actor, "pc.access"); }

    @Transactional(readOnly = true)
    public Page<UserView> users(Actor actor, String keyword, String status, String roleCode, int page, int pageSize) {
        require(actor, "user.read");
        if (page < 1 || page > 1_000_000 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("分页参数不正确");
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (keyword != null && !keyword.isBlank()) {
            if (keyword.length() > 100) throw new IllegalArgumentException("搜索内容过长");
            String like = "%" + keyword.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            where.append(" AND (u.username LIKE ? ESCAPE '!' OR u.display_name LIKE ? ESCAPE '!' OR u.phone LIKE ? ESCAPE '!' OR u.email LIKE ? ESCAPE '!')");
            args.addAll(List.of(like, like, like, like));
        }
        if (status != null && !status.isBlank()) {
            validateUserStatus(status);
            where.append(" AND u.status = ?"); args.add(status);
        }
        if (roleCode != null && !roleCode.isBlank()) {
            if (roleCode.length() > 64) throw new IllegalArgumentException("角色编码过长");
            where.append(" AND u.role_name = ?"); args.add(roleCode);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM users u" + where, Long.class, args.toArray());
        args.add(pageSize); args.add((page - 1) * pageSize);
        return new Page<>(jdbc.query(USER_SELECT + where + " ORDER BY u.created_at DESC, u.id DESC LIMIT ? OFFSET ?",
            USER_MAPPER, args.toArray()), total, page, pageSize);
    }

    @Transactional
    public UserView createUser(Actor actor, CreateUser request) {
        lock(actor, null);
        require(actor, "user.create");
        AccountSecurityService.validateNewPassword(request.password());
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("""
                    INSERT INTO users(username, password_hash, display_name, phone, email, role_name, status)
                    VALUES (?, ?, ?, ?, ?, 'user', 'active')
                    """, new String[]{"id"});
                statement.setString(1, request.username().strip());
                statement.setString(2, encoder.encode(request.password()));
                statement.setString(3, requiredName(request.displayName()));
                statement.setString(4, optional(request.phone()));
                statement.setString(5, optional(request.email()));
                return statement;
            }, keys);
        } catch (DuplicateKeyException ex) { throw new IllegalArgumentException("用户名、手机号或邮箱已被使用"); }
        return user(Objects.requireNonNull(keys.getKey()).longValue());
    }

    @Transactional
    public UserView updateUser(Actor actor, long id, UpdateUser request) {
        lock(actor, id);
        SessionView session = require(actor, "user.update");
        manageableUser(session, user(id));
        try {
            jdbc.update("UPDATE users SET display_name = ?, phone = ?, email = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                requiredName(request.displayName()), optional(request.phone()), optional(request.email()), id);
        } catch (DuplicateKeyException ex) { throw new IllegalArgumentException("手机号或邮箱已被使用"); }
        return user(id);
    }

    @Transactional
    public UserView changeUserStatus(Actor actor, long id, String status) {
        lock(actor, id);
        SessionView session = require(actor, "user.status");
        validateUserStatus(status);
        if (actor.id() == id) throw new IllegalArgumentException("不能变更自己的账号状态");
        manageableUser(session, user(id));
        jdbc.update("UPDATE users SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status, id);
        if (!"active".equals(status)) {
            jdbc.update("UPDATE user_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revoked_at IS NULL", id);
        }
        return user(id);
    }

    @Transactional
    public UserView assignRole(Actor actor, long id, String code) {
        lock(actor, id);
        SessionView session = require(actor, "user.assign-role");
        if (actor.id() == id) throw new IllegalArgumentException("不能修改自己的角色");
        manageableUser(session, user(id));
        Role role = roleByCode(code);
        if (!"active".equals(role.status())) throw new IllegalArgumentException("不能分配已停用的角色");
        manageableRole(session, role);
        jdbc.update("UPDATE users SET role_name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", role.code(), id);
        return user(id);
    }

    @Transactional(readOnly = true)
    public List<RoleView> roleOptions(Actor actor) {
        SessionView session = require(actor, "user.read");
        return roleViews().stream().filter(role -> "active".equals(role.status()))
            .filter(role -> isSuper(session) || (!"super_admin".equals(role.code()) && session.permissions().containsAll(role.permissions())))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleView> roles(Actor actor) { require(actor, "role.read"); return roleViews(); }

    @Transactional
    public RoleView createRole(Actor actor, CreateRole request) {
        lock(actor, null);
        require(actor, "role.create");
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var statement = connection.prepareStatement(
                    "INSERT INTO admin_roles(code, name, description) VALUES (?, ?, ?)", new String[]{"id"});
                statement.setString(1, request.code());
                statement.setString(2, requiredName(request.name()));
                statement.setString(3, request.description().strip());
                return statement;
            }, keys);
        } catch (DuplicateKeyException ex) { throw new IllegalArgumentException("角色编码已存在"); }
        return roleView(role(Objects.requireNonNull(keys.getKey()).longValue()));
    }

    @Transactional
    public RoleView updateRole(Actor actor, long id, UpdateRole request) {
        lock(actor, null);
        SessionView session = require(actor, "role.update");
        Role role = role(id);
        mutableRole(session, role);
        if (!Set.of("active", "disabled").contains(request.status())) throw new IllegalArgumentException("角色状态不正确");
        jdbc.update("UPDATE admin_roles SET name = ?, description = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            requiredName(request.name()), request.description().strip(), request.status(), id);
        return roleView(role(id));
    }

    @Transactional
    public void deleteRole(Actor actor, long id) {
        lock(actor, null);
        SessionView session = require(actor, "role.delete");
        Role role = role(id);
        mutableRole(session, role);
        if (userCount(role.code()) > 0) throw new IllegalArgumentException("该角色仍有关联用户，请先为用户重新分配角色");
        jdbc.update("DELETE FROM admin_roles WHERE id = ?", id);
    }

    @Transactional(readOnly = true)
    public List<AdminPermissions.Permission> permissions(Actor actor) {
        require(actor, "permission.read");
        return AdminPermissions.ALL;
    }

    @Transactional
    public RoleView grant(Actor actor, long id, List<String> codes) {
        lock(actor, null);
        SessionView session = require(actor, "permission.assign");
        Role role = role(id);
        mutableRole(session, role);
        Set<String> permissions = AdminPermissions.validate(codes);
        if (!session.permissions().containsAll(permissions)) throw new AccessDeniedException("不能授予自己未持有的权限");
        jdbc.update("DELETE FROM admin_role_permissions WHERE role_id = ?", id);
        for (String code : permissions) jdbc.update("INSERT INTO admin_role_permissions(role_id, permission_code) VALUES (?, ?)", id, code);
        jdbc.update("UPDATE admin_roles SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", id);
        return roleView(role);
    }

    /** Serialize role policy changes before taking user locks. Every mutation rechecks the live session and permissions. */
    private void lock(Actor actor, Long targetId) {
        if (jdbc.queryForList("SELECT id FROM admin_roles WHERE code = 'super_admin' FOR UPDATE", Long.class).isEmpty()) {
            throw new IllegalStateException("管理角色尚未初始化，请先执行数据库 051 初始化文件");
        }
        jdbc.queryForList("SELECT id FROM users WHERE id IN (?, ?) ORDER BY id FOR UPDATE", Long.class,
            actor.id(), targetId == null ? actor.id() : targetId);
    }

    private SessionView require(Actor actor, String permission) {
        Long valid = jdbc.queryForObject("""
            SELECT COUNT(*) FROM user_sessions s JOIN users u ON u.id = s.user_id
            WHERE s.user_id = ? AND s.id = ? AND s.revoked_at IS NULL AND s.expires_at > CURRENT_TIMESTAMP AND u.status = 'active'
            """, Long.class, actor.id(), actor.sessionId());
        if (valid == null || valid == 0) throw new UserIdentityMissingException("登录已失效，请重新登录");
        UserView user = user(actor.id());
        var roles = jdbc.query("SELECT * FROM admin_roles WHERE code = ? AND status = 'active'", ROLE_MAPPER, user.roleCode());
        Set<String> permissions = roles.isEmpty() ? Set.of() : permissions(roles.get(0));
        if (!permissions.contains("pc.access") || !permissions.contains(permission)) throw new AccessDeniedException("没有此操作的权限");
        return new SessionView(user, permissions);
    }

    private void manageableUser(SessionView session, UserView user) {
        var roles = jdbc.query("SELECT * FROM admin_roles WHERE code = ?", ROLE_MAPPER, user.roleCode());
        if (!roles.isEmpty()) manageableRole(session, roles.get(0));
        else if (!isSuper(session)) throw new AccessDeniedException("该用户的历史角色需由超级管理员处理");
    }
    private void manageableRole(SessionView session, Role role) {
        if (!isSuper(session) && ("super_admin".equals(role.code()) || !session.permissions().containsAll(permissions(role)))) {
            throw new AccessDeniedException("不能管理权限高于自己的用户或角色");
        }
    }
    private void mutableRole(SessionView session, Role role) {
        if (role.builtIn()) throw new IllegalArgumentException("内置角色不可修改或删除，请新建自定义角色");
        if (role.code().equals(session.user().roleCode())) throw new IllegalArgumentException("不能修改自己当前所属的角色");
        manageableRole(session, role);
    }
    private boolean isSuper(SessionView session) { return "super_admin".equals(session.user().roleCode()); }
    private UserView user(long id) {
        return jdbc.query(USER_SELECT + " WHERE u.id = ?", USER_MAPPER, id).stream().findFirst()
            .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }
    private Role role(long id) {
        return jdbc.query("SELECT * FROM admin_roles WHERE id = ?", ROLE_MAPPER, id).stream().findFirst()
            .orElseThrow(() -> new IllegalArgumentException("角色不存在"));
    }
    private Role roleByCode(String code) {
        return jdbc.query("SELECT * FROM admin_roles WHERE code = ?", ROLE_MAPPER, code).stream().findFirst()
            .orElseThrow(() -> new IllegalArgumentException("角色不存在"));
    }
    private Set<String> permissions(Role role) {
        if ("super_admin".equals(role.code())) return AdminPermissions.CODES;
        return Set.copyOf(jdbc.queryForList("SELECT permission_code FROM admin_role_permissions WHERE role_id = ?", String.class, role.id())
            .stream().filter(AdminPermissions.CODES::contains).toList());
    }
    private long userCount(String code) { return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role_name = ?", Long.class, code); }
    private RoleView roleView(Role role) {
        return new RoleView(role.id(), role.code(), role.name(), role.description(), role.status(), role.builtIn(), userCount(role.code()), permissions(role));
    }
    private List<RoleView> roleViews() {
        return jdbc.query("SELECT * FROM admin_roles ORDER BY built_in DESC, id", ROLE_MAPPER).stream().map(this::roleView).toList();
    }
    private static LocalDateTime date(Timestamp timestamp) { return timestamp == null ? null : timestamp.toLocalDateTime(); }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static String requiredName(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 80) throw new IllegalArgumentException("名称需为 1–80 个字符");
        return value.strip();
    }
    private static void validateUserStatus(String status) {
        if (status == null || !Set.of("active", "disabled", "locked").contains(status)) throw new IllegalArgumentException("用户状态不正确");
    }
}
