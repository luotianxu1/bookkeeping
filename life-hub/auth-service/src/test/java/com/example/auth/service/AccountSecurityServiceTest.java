package com.example.auth.service;

import com.example.auth.dto.AccountSecurityRequests;
import com.example.auth.security.JwtService;
import com.example.auth.security.SecurityAttemptLimiter;
import com.example.common.security.JdbcSessionChecker;
import com.example.common.security.UserIdentityMissingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(AccountSecurityServiceTest.Config.class)
class AccountSecurityServiceTest {
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", ""); }
        @Bean JdbcTemplate jdbc(DataSource data) { return new JdbcTemplate(data); }
        @Bean DataSourceTransactionManager transactionManager(DataSource data) { return new DataSourceTransactionManager(data); }
        @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(4); }
        @Bean JwtService jwt() { return new JwtService("account-security-test-key-at-least-32-bytes", 3600); }
        @Bean AccountSecurityService service(JdbcTemplate jdbc, PasswordEncoder encoder, JwtService jwt) { return new AccountSecurityService(jdbc, encoder, jwt); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource data;
    @Autowired AccountSecurityService service;
    @Autowired PasswordEncoder encoder;
    private String session;
    @BeforeEach void setup() throws Exception {
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE users(id BIGINT UNSIGNED PRIMARY KEY, username VARCHAR(64) UNIQUE, password_hash VARCHAR(100), display_name VARCHAR(80), avatar_url VARCHAR(512), phone VARCHAR(32), email VARCHAR(255), role_name VARCHAR(64), status VARCHAR(20), last_login_at TIMESTAMP)");
        String ddl = Files.readString(Path.of("../../database/050_create_account_security.mysql.sql"))
            .replaceAll("(?i)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", "");
        try (var connection = data.getConnection()) { ScriptUtils.executeSqlScript(connection, new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8))); }
        for (int id = 1; id <= 2; id++) jdbc.update("INSERT INTO users(id,username,password_hash,display_name,status) VALUES(?,?,?,?, 'active')", id, "user" + id, encoder.encode("old password "), "user" + id);
        session = login("user1", "old password ");
    }
    private String login(String user, String password) {
        return service.login(user, password, "Mozilla/5.0 Macintosh Chrome/130.0", "127.0.0.1").orElseThrow().sessionId();
    }
    private boolean valid(long user, String sessionId) { return new JdbcSessionChecker(jdbc).isValid(user, sessionId); }

    @Test void passwordChangeRevokesAllDevicesAndPreservesPasswordWhitespace() {
        String second = login("user1", "old password ");
        assertThrows(IllegalArgumentException.class, () -> service.changePassword(1, session, "wrong", "new password "));
        assertTrue(valid(1, second));
        service.changePassword(1, session, "old password ", "new password ");
        assertFalse(valid(1, session)); assertFalse(valid(1, second));
        assertTrue(service.login("user1", "old password ", "", "").isEmpty());
        assertTrue(service.login("user1", "new password", "", "").isEmpty());
        assertTrue(valid(1, login("user1", "new password ")));
    }

    @Test void recoveryCodeIsHashedRotatableAndSingleUse() {
        String old = service.generateRecoveryCode(1, session, "old password ");
        String code = service.generateRecoveryCode(1, session, "old password ");
        assertNotEquals(code, jdbc.queryForObject("SELECT recovery_code_hash FROM user_security WHERE user_id = 1", String.class));
        assertTrue(service.recoveryStatus(1).configured());
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("user1", old, "new password"));
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("user2", code, "new password"));
        service.resetPassword("user1", code.toLowerCase(), "new password");
        assertFalse(valid(1, session));
        assertFalse(service.recoveryStatus(1).configured());
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("user1", code, "another password"));
        assertTrue(valid(1, login("user1", "new password")));
    }

    @Test void concurrentRecoveryConsumptionHasExactlyOneWinner() throws Exception {
        String code = service.generateRecoveryCode(1, session, "old password ");
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> reset = () -> {
            start.await();
            try { service.resetPassword("user1", code, "reset password"); return true; }
            catch (IllegalArgumentException e) { return false; }
        };
        try {
            var a = executor.submit(reset); var b = executor.submit(reset); start.countDown();
            assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertFalse(valid(1, session));
        } finally { executor.shutdownNow(); }
    }

    @Test void revokeOnlyOwnedSessionsAndCannotMutateWithRevokedSession() {
        String other = login("user1", "old password ");
        String outsider = login("user2", "old password ");
        service.revokeSession(1, session, outsider);
        assertTrue(valid(2, outsider));
        service.revokeOtherSessions(1, session);
        assertTrue(valid(1, session)); assertFalse(valid(1, other)); assertTrue(valid(2, outsider));
        assertThrows(UserIdentityMissingException.class, () -> service.generateRecoveryCode(1, other, "old password "));
        service.revokeSession(1, session, session);
        assertFalse(valid(1, session));
    }

    @Test void profileUsesOwnUserAndSessionRejectsExpiredOrDisabledIdentity() {
        var updated = service.updateProfile(1, session, new AccountSecurityRequests.Profile("  新昵称  "));
        assertEquals("新昵称", updated.displayName());
        assertEquals("user2", service.currentUser(2).displayName());
        assertThrows(UserIdentityMissingException.class, () -> service.updateProfile(2, session, new AccountSecurityRequests.Profile("越权")));
        jdbc.update("UPDATE user_sessions SET expires_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), session);
        assertFalse(valid(1, session));
        String fresh = login("user1", "old password ");
        jdbc.update("UPDATE users SET status = 'disabled' WHERE id = 1");
        assertFalse(valid(1, fresh));
        assertFalse(valid(1, null));
        assertTrue(service.login("user1", "old password ", "", "").isEmpty());
    }

    @Test void existingPhoneAndEmailLoginRemainSupported() {
        jdbc.update("UPDATE users SET phone = '13800001234', email = 'member@example.test' WHERE id = 1");
        assertTrue(valid(1, login("13800001234", "old password ")));
        assertTrue(valid(1, login("member@example.test", "old password ")));
    }

    @Test void credentialValidationAndRateLimitsAreBounded() {
        assertThrows(IllegalArgumentException.class, () -> AccountSecurityService.validateNewPassword("密".repeat(30)));
        assertThrows(IllegalArgumentException.class, () -> AccountSecurityService.validateNewPassword("      "));
        var limiter = new SecurityAttemptLimiter();
        for (int i = 0; i < 5; i++) assertTrue(limiter.allow("reset", "user", "ip" + i));
        assertFalse(limiter.allow("reset", "user", "another-ip"));
        for (int i = 0; i < 30; i++) assertTrue(limiter.allow("reset", "user" + i, "same-ip"));
        assertFalse(limiter.allow("reset", "another-user", "same-ip"));
    }
}
