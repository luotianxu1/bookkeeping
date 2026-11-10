package com.example.auth.admin;

import com.example.auth.service.AccountSecurityService;
import com.example.common.exception.GlobalExceptionHandler;
import com.example.common.security.JdbcSessionChecker;
import com.example.common.security.JwtTokenAuthenticationFilter;
import com.example.common.security.SecurityUtils;
import com.example.common.security.UserIdentityMissingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static com.example.auth.admin.AdminModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(AdminServiceTest.Config.class)
class AdminServiceTest {
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:admin_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", ""); }
        @Bean JdbcTemplate jdbc(DataSource data) { return new JdbcTemplate(data); }
        @Bean DataSourceTransactionManager transactionManager(DataSource data) { return new DataSourceTransactionManager(data); }
        @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(4); }
        @Bean AdminService service(JdbcTemplate jdbc, PasswordEncoder encoder) { return new AdminService(jdbc, encoder); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource data;
    @Autowired AdminService service;
    @Autowired PasswordEncoder encoder;
    @Autowired DataSourceTransactionManager transactionManager;
    Actor admin;
    Actor ordinary;
    MockMvc mvc;

    @BeforeEach void setup() throws Exception {
        jdbc.execute("DROP ALL OBJECTS");
        String users = Files.readString(Path.of("../../database/001_create_database_and_users.mysql.sql"));
        execute(users.substring(users.indexOf("CREATE TABLE"), users.indexOf("-- 首次初始化")));
        execute(Files.readString(Path.of("../../database/050_create_account_security.mysql.sql")));
        execute(Files.readString(Path.of("../../database/051_create_admin_roles.mysql.sql")));
        jdbc.update("INSERT INTO users(username,password_hash,display_name,role_name) VALUES('admin',?,'管理员','super_admin'),('reader',?,'普通用户','user')", encoder.encode("password"), encoder.encode("password"));
        admin = actor(1); ordinary = actor(2);
        mvc = MockMvcBuilders.standaloneSetup(new AdminController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    private void execute(String ddl) throws Exception {
        ddl = ddl.replaceAll("(?i)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", "");
        try (var connection = data.getConnection()) { ScriptUtils.executeSqlScript(connection, new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8))); }
    }
    private Actor actor(long id) {
        String session = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user_sessions(id,user_id,device_name,ip_address,created_at,last_seen_at,expires_at) VALUES(?,?,'test','127.0.0.1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,DATEADD('HOUR',1,CURRENT_TIMESTAMP))", session, id);
        return new Actor(id, session);
    }
    private RoleView role(String code, String... permissions) {
        RoleView role = service.createRole(admin, new CreateRole(code, code, "测试角色"));
        if (permissions.length > 0) role = service.grant(admin, role.id(), List.of(permissions));
        return role;
    }
    private void give(Actor actor, RoleView role) { service.assignRole(admin, actor.id(), role.code()); }

    @Test void userRolePermissionLifecyclePreservesPasswordsAndDefaultRole() {
        UserView created = service.createUser(admin, new CreateUser("new_user", " spaced password ", " 新用户 ", "", "person@example.com"));
        assertEquals("user", created.roleCode());
        assertEquals("新用户", created.displayName());
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, created.id());
        assertTrue(encoder.matches(" spaced password ", hash));
        assertFalse(encoder.matches("spaced password", hash));
        RoleView role = role("viewer", "pc.access", "user.read");
        service.assignRole(admin, created.id(), role.code());
        Actor viewer = actor(created.id());
        assertEquals(Set.of("pc.access", "user.read"), service.session(viewer).permissions());
        assertEquals(3, service.users(viewer, "", "", "", 1, 20).total());
        assertThrows(AccessDeniedException.class, () -> service.updateUser(viewer, created.id(), new UpdateUser("改名", null, null)));
        service.updateUser(admin, created.id(), new UpdateUser("新昵称", "123456789", null));
        assertEquals("新昵称", service.users(admin, "新昵称", "active", "viewer", 1, 1).items().get(0).displayName());
        assertThrows(IllegalArgumentException.class, () -> service.deleteRole(admin, role.id()));
        service.assignRole(admin, created.id(), "user");
        service.deleteRole(admin, role.id());
        assertFalse(service.roles(admin).stream().anyMatch(item -> item.id() == role.id()));
        assertThrows(AccessDeniedException.class, () -> service.session(viewer));
    }

    @Test void ordinaryUsersAndInvalidSessionsCannotAccessAdministration() {
        assertThrows(AccessDeniedException.class, () -> service.session(ordinary));
        assertThrows(AccessDeniedException.class, () -> service.users(ordinary, "", "", "", 1, 20));
        assertThrows(AccessDeniedException.class, () -> service.createRole(ordinary, new CreateRole("forged", "越权", "")));
        assertThrows(UserIdentityMissingException.class, () -> service.session(new Actor(admin.id(), ordinary.sessionId())));
        assertThrows(UserIdentityMissingException.class, () -> service.session(new Actor(admin.id(), null)));
    }

    @Test void currentRoleAndBuiltinRolesCannotBeModified() {
        assertThrows(IllegalArgumentException.class, () -> service.changeUserStatus(admin, admin.id(), "disabled"));
        assertThrows(IllegalArgumentException.class, () -> service.assignRole(admin, admin.id(), "user"));
        for (RoleView role : service.roles(admin)) {
            assertThrows(IllegalArgumentException.class, () -> service.deleteRole(admin, role.id()));
            assertThrows(IllegalArgumentException.class, () -> service.grant(admin, role.id(), List.of()));
            assertThrows(IllegalArgumentException.class, () -> service.updateRole(admin, role.id(), new UpdateRole("改名", "", "active")));
        }
        RoleView manager = role("manager", "pc.access", "role.read", "role.update", "permission.read", "permission.assign");
        give(ordinary, manager);
        assertThrows(IllegalArgumentException.class, () -> service.grant(ordinary, manager.id(), List.of("pc.access")));
    }

    @Test void lowerPrivilegeAdminsCannotEscalateOrManageHigherPrivilegeUsers() {
        RoleView manager = role("manager", "pc.access", "user.read", "user.update", "user.status", "user.assign-role", "role.read", "permission.read", "permission.assign");
        give(ordinary, manager);
        RoleView empty = role("empty_role");
        assertThrows(AccessDeniedException.class, () -> service.grant(ordinary, empty.id(), List.of("pc.access", "role.read", "role.delete")));
        assertThrows(AccessDeniedException.class, () -> service.updateUser(ordinary, admin.id(), new UpdateUser("越权", null, null)));
        assertThrows(AccessDeniedException.class, () -> service.changeUserStatus(ordinary, admin.id(), "disabled"));
        UserView target = service.createUser(admin, new CreateUser("target", "password", "目标", "", ""));
        assertThrows(AccessDeniedException.class, () -> service.assignRole(ordinary, target.id(), "super_admin"));
        assertFalse(service.roleOptions(ordinary).stream().anyMatch(item -> item.code().equals("super_admin")));
        assertEquals(Set.of(), service.roles(admin).stream().filter(item -> item.id() == empty.id()).findFirst().orElseThrow().permissions());
    }

    @Test void unknownAndIncompletePermissionsLeaveExistingGrantsIntact() {
        RoleView viewer = role("viewer", "pc.access", "user.read");
        assertThrows(IllegalArgumentException.class, () -> service.grant(admin, viewer.id(), List.of("pc.access", "made.up")));
        assertThrows(IllegalArgumentException.class, () -> service.grant(admin, viewer.id(), List.of("user.create")));
        assertThrows(IllegalArgumentException.class, () -> service.grant(admin, viewer.id(), List.of("pc.access", "permission.assign")));
        give(ordinary, viewer);
        assertEquals(Set.of("pc.access", "user.read"), service.session(ordinary).permissions());
    }

    @Test void permissionRevocationAndRoleDisableApplyToExistingSessionsImmediately() {
        RoleView viewer = role("viewer", "pc.access", "user.read");
        give(ordinary, viewer);
        service.users(ordinary, "", "", "", 1, 20);
        service.grant(admin, viewer.id(), List.of("pc.access"));
        assertThrows(AccessDeniedException.class, () -> service.users(ordinary, "", "", "", 1, 20));
        service.updateRole(admin, viewer.id(), new UpdateRole("查看者", "", "disabled"));
        assertThrows(AccessDeniedException.class, () -> service.session(ordinary));
        assertThrows(IllegalArgumentException.class, () -> service.assignRole(admin, ordinary.id(), viewer.code()));
    }

    @Test void disablingAndLockingRevokeSessionsAndReenableDoesNotRestoreThem() {
        Actor second = actor(ordinary.id());
        service.changeUserStatus(admin, ordinary.id(), "disabled");
        service.changeUserStatus(admin, ordinary.id(), "active");
        var sessions = new JdbcSessionChecker(jdbc);
        assertFalse(sessions.isValid(ordinary.id(), ordinary.sessionId()));
        assertFalse(sessions.isValid(second.id(), second.sessionId()));
        Actor fresh = actor(ordinary.id());
        assertTrue(sessions.isValid(fresh.id(), fresh.sessionId()));
        service.changeUserStatus(admin, ordinary.id(), "locked");
        assertFalse(sessions.isValid(fresh.id(), fresh.sessionId()));
    }

    @Test void paginationAndSearchUseSameFiltersAndTreatWildcardsLiterally() {
        service.createUser(admin, new CreateUser("under_score", "password", "用户百分%", null, null));
        assertEquals(1, service.users(admin, "%", "", "", 1, 20).total());
        assertEquals(1, service.users(admin, "_", "", "", 1, 20).total());
        Page<UserView> first = service.users(admin, "", "active", "user", 1, 1);
        Page<UserView> second = service.users(admin, "", "active", "user", 2, 1);
        assertEquals(2, first.total()); assertEquals(first.total(), second.total());
        assertNotEquals(first.items().get(0).id(), second.items().get(0).id());
        assertThrows(IllegalArgumentException.class, () -> service.users(admin, "", "", "", 0, 100));
        assertThrows(IllegalArgumentException.class, () -> service.users(admin, "", "", "", 1, 101));
    }

    @Test void duplicateIdentifiersAndInvalidPasswordsCannotCreatePartialUsers() {
        assertThrows(IllegalArgumentException.class, () -> service.createUser(admin, new CreateUser("admin", "password", "重复", null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.createUser(admin, new CreateUser("too_long", "密".repeat(30), "密码过长", null, null)));
        assertEquals(2, service.users(admin, "", "", "", 1, 20).total());
        assertThrows(IllegalArgumentException.class, () -> AccountSecurityService.validateNewPassword("      "));
    }

    @Test void mutationRechecksSessionAfterWaitingForUserLock() throws Exception {
        var transaction = new TransactionTemplate(transactionManager);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> revoke = executor.submit(() -> transaction.executeWithoutResult(status -> {
                jdbc.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, admin.id());
                locked.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); } catch (InterruptedException ex) { throw new RuntimeException(ex); }
                jdbc.update("UPDATE user_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE id = ?", admin.sessionId());
            }));
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            Future<?> write = executor.submit(() -> service.updateUser(admin, ordinary.id(), new UpdateUser("不应保存", null, null)));
            release.countDown(); revoke.get(10, TimeUnit.SECONDS);
            ExecutionException failure = assertThrows(ExecutionException.class, () -> write.get(10, TimeUnit.SECONDS));
            assertInstanceOf(UserIdentityMissingException.class, failure.getCause());
            assertEquals("普通用户", jdbc.queryForObject("SELECT display_name FROM users WHERE id = 2", String.class));
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void concurrentRoleDeletionAndAssignmentNeverLeaveDanglingRole() throws Exception {
        RoleView role = role("concurrent");
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> assign = executor.submit(() -> { start.await(); try { service.assignRole(admin, ordinary.id(), role.code()); return true; } catch (IllegalArgumentException ex) { return false; } });
            Future<Boolean> delete = executor.submit(() -> { start.await(); try { service.deleteRole(admin, role.id()); return true; } catch (IllegalArgumentException ex) { return false; } });
            start.countDown();
            assertNotEquals(assign.get(10, TimeUnit.SECONDS), delete.get(10, TimeUnit.SECONDS));
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM users u LEFT JOIN admin_roles r ON r.code = u.role_name WHERE r.id IS NULL", Long.class));
        } finally { executor.shutdownNow(); }
    }

    @Test void httpContractRejectsForgedIdentityAndValidationErrorsAndOmitsCredentials() throws Exception {
        mvc.perform(get("/api/admin/users").requestAttr(SecurityUtils.USER_ID_ATTRIBUTE, ordinary.id())
                .requestAttr(JwtTokenAuthenticationFilter.SESSION_ID_ATTRIBUTE, ordinary.sessionId()).param("userId", "1"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        mvc.perform(post("/api/admin/users").requestAttr(SecurityUtils.USER_ID_ATTRIBUTE, admin.id())
                .requestAttr(JwtTokenAuthenticationFilter.SESSION_ID_ATTRIBUTE, admin.sessionId())
                .contentType("application/json").content("{\"username\":\"valid_user\",\"password\":\"password\",\"displayName\":\"Test\",\"email\":\"invalid\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users").requestAttr(SecurityUtils.USER_ID_ATTRIBUTE, admin.id())
                .requestAttr(JwtTokenAuthenticationFilter.SESSION_ID_ATTRIBUTE, admin.sessionId()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].sessionId").doesNotExist());
    }
}
