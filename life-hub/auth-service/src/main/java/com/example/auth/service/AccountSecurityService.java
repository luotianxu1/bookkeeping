package com.example.auth.service;

import com.example.auth.dto.AccountSecurityRequests;
import com.example.auth.dto.CurrentUserResponse;
import com.example.auth.security.JwtService;
import com.example.common.security.UserIdentityMissingException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class AccountSecurityService {
    public record LoginSession(long userId, String username, String sessionId) {}
    public record SessionView(String id, String deviceName, String ipAddress, Instant createdAt,
                              Instant lastSeenAt, Instant expiresAt, boolean current) {}
    public record RecoveryStatus(boolean configured, Instant createdAt) {}
    private record User(long id, String username, String hash, String status) {}
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AccountSecurityService(JdbcTemplate jdbc, PasswordEncoder encoder, JwtService jwt) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("account-security-timing-equalizer");
    }

    // 密码验证和会话创建必须处于同一用户锁内，防止改密后并发登录重新签出旧凭证。
    @Transactional
    public Optional<LoginSession> login(String username, String password, String userAgent, String ip) {
        User user = findLoginLocked(username);
        boolean matches = matchesPassword(password, user == null ? dummyHash : user.hash());
        if (user == null || !"active".equals(user.status()) || !matches) return Optional.empty();
        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        jdbc.update("DELETE FROM user_sessions WHERE user_id = ? AND (revoked_at IS NOT NULL OR expires_at <= ?)", user.id(), Timestamp.from(now));
        List<String> existing = jdbc.queryForList("SELECT id FROM user_sessions WHERE user_id = ? ORDER BY created_at DESC", String.class, user.id());
        for (int i = 19; i < existing.size(); i++) {
            jdbc.update("UPDATE user_sessions SET revoked_at = ? WHERE id = ? AND user_id = ?", Timestamp.from(now), existing.get(i), user.id());
        }
        jdbc.update("""
            INSERT INTO user_sessions(id, user_id, device_name, ip_address, created_at, last_seen_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, id, user.id(), deviceName(userAgent), truncate(ip, 64), Timestamp.from(now), Timestamp.from(now),
            Timestamp.from(now.plusSeconds(jwt.getExpirationSeconds())));
        jdbc.update("UPDATE users SET last_login_at = ? WHERE id = ?", Timestamp.from(now), user.id());
        return Optional.of(new LoginSession(user.id(), user.username(), id));
    }

    public CurrentUserResponse currentUser(long userId) {
        return jdbc.query("""
            SELECT u.id, u.username, u.phone, u.email, u.display_name, u.avatar_url, u.role_name
            FROM users u WHERE u.id = ? AND u.status = 'active'
            """, (rs, row) -> new CurrentUserResponse(rs.getLong("id"), rs.getString("username"), rs.getString("phone"),
            rs.getString("email"), rs.getString("display_name"),
            rs.getString("avatar_url"),
            rs.getString("role_name")), userId).stream().findFirst()
            .orElseThrow(() -> new UserIdentityMissingException("未登录或登录已过期"));
    }

    @Transactional
    public CurrentUserResponse updateProfile(long userId, String sessionId, AccountSecurityRequests.Profile request) {
        requireLockedSession(userId, sessionId);
        String displayName = request.displayName().strip();
        if (displayName.isBlank() || displayName.length() > 80) throw new IllegalArgumentException("昵称需为 1 到 80 个字符");
        jdbc.update("UPDATE users SET display_name = ? WHERE id = ?", displayName, userId);
        return currentUser(userId);
    }

    @Transactional
    public void changePassword(long userId, String sessionId, String currentPassword, String newPassword) {
        User user = requireLockedSession(userId, sessionId);
        requirePassword(currentPassword, user.hash());
        validateNewPassword(newPassword);
        if (matchesPassword(newPassword, user.hash())) throw new IllegalArgumentException("新密码不能与当前密码相同");
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", encoder.encode(newPassword), userId);
        revokeAll(userId);
    }

    public RecoveryStatus recoveryStatus(long userId) {
        return jdbc.query("SELECT recovery_code_hash, recovery_created_at FROM user_security WHERE user_id = ?", (rs, row) ->
            new RecoveryStatus(rs.getString(1) != null, rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant()), userId)
            .stream().findFirst().orElse(new RecoveryStatus(false, null));
    }

    @Transactional
    public String generateRecoveryCode(long userId, String sessionId, String password) {
        User user = requireLockedSession(userId, sessionId);
        requirePassword(password, user.hash());
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        String raw = HexFormat.of().withUpperCase().formatHex(bytes);
        String code = String.join("-", raw.split("(?<=\\G.{8})"));
        jdbc.update("DELETE FROM user_security WHERE user_id = ?", userId);
        jdbc.update("INSERT INTO user_security(user_id, recovery_code_hash, recovery_created_at) VALUES (?, ?, ?)",
            userId, recoveryHash(code), Timestamp.from(Instant.now()));
        return code;
    }

    @Transactional
    public void resetPassword(String username, String code, String newPassword) {
        validateNewPassword(newPassword);
        User user = findLocked(username);
        List<String> stored = user == null ? List.of() : jdbc.queryForList(
            "SELECT recovery_code_hash FROM user_security WHERE user_id = ?", String.class, user.id());
        String expected = stored.isEmpty() || stored.get(0) == null ? "0".repeat(64) : stored.get(0);
        boolean matches = MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), recoveryHash(code).getBytes(StandardCharsets.US_ASCII));
        if (user == null || !"active".equals(user.status()) || !matches) {
            throw new IllegalArgumentException("账号或恢复码不正确，或恢复码已失效");
        }
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", encoder.encode(newPassword), user.id());
        jdbc.update("UPDATE user_security SET recovery_code_hash = NULL, recovery_created_at = NULL WHERE user_id = ?", user.id());
        revokeAll(user.id());
    }

    public List<SessionView> sessions(long userId, String currentSessionId) {
        return jdbc.query("""
            SELECT id, device_name, ip_address, created_at, last_seen_at, expires_at FROM user_sessions
            WHERE user_id = ? AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP
            ORDER BY CASE WHEN id = ? THEN 0 ELSE 1 END, last_seen_at DESC, id
            """, (rs, row) -> new SessionView(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant(),
                rs.getTimestamp(5).toInstant(), rs.getTimestamp(6).toInstant(), rs.getString(1).equals(currentSessionId)), userId, currentSessionId);
    }

    @Transactional
    public void revokeSession(long userId, String currentSessionId, String targetId) {
        requireLockedSession(userId, currentSessionId);
        jdbc.update("UPDATE user_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ? AND revoked_at IS NULL", targetId, userId);
    }

    @Transactional
    public void revokeOtherSessions(long userId, String currentSessionId) {
        requireLockedSession(userId, currentSessionId);
        jdbc.update("UPDATE user_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND id <> ? AND revoked_at IS NULL", userId, currentSessionId);
    }

    private void revokeAll(long userId) {
        jdbc.update("UPDATE user_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revoked_at IS NULL", userId);
    }

    private User requireLockedSession(long userId, String sessionId) {
        List<User> users = jdbc.query("SELECT id, username, password_hash, status FROM users WHERE id = ? FOR UPDATE", (rs, row) ->
            new User(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), userId);
        Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM user_sessions WHERE user_id = ? AND id = ? AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP", Integer.class, userId, sessionId);
        if (users.isEmpty() || !"active".equals(users.get(0).status()) || valid == null || valid == 0) {
            throw new UserIdentityMissingException("登录已失效，请重新登录");
        }
        return users.get(0);
    }

    private User findLoginLocked(String login) {
        return jdbc.query("SELECT id, username, password_hash, status FROM users WHERE status = 'active' AND (username = ? OR phone = ? OR email = ?) LIMIT 1 FOR UPDATE", (rs, row) ->
            new User(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), login.trim(), login.trim(), login.trim()).stream().findFirst().orElse(null);
    }

    private User findLocked(String username) {
        return jdbc.query("SELECT id, username, password_hash, status FROM users WHERE username = ? FOR UPDATE", (rs, row) ->
            new User(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), username.trim()).stream().findFirst().orElse(null);
    }

    public static void validateNewPassword(String password) {
        if (password == null || password.isBlank() || password.length() < 6 || password.length() > 64
            || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("密码需为 6 到 64 位，且 UTF-8 长度不超过 72 字节");
        }
    }

    private boolean matchesPassword(String password, String hash) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72 && hash != null && encoder.matches(password, hash);
    }
    private void requirePassword(String password, String hash) {
        if (!matchesPassword(password, hash)) throw new IllegalArgumentException("当前密码不正确");
    }
    private String recoveryHash(String code) {
        try {
            String normalized = code.replace("-", "").replace(" ", "").strip().toUpperCase(Locale.ROOT);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private String deviceName(String agent) {
        String ua = agent == null ? "" : agent;
        String os = ua.contains("Android") ? "Android" : ua.contains("iPhone") || ua.contains("iPad") ? "iOS"
            : ua.contains("Windows") ? "Windows" : ua.contains("Mac") ? "macOS" : ua.contains("Linux") ? "Linux" : "未知系统";
        String browser = ua.contains("Edg/") ? "Edge" : ua.contains("Firefox/") || ua.contains("FxiOS/") ? "Firefox"
            : ua.contains("Chrome/") || ua.contains("CriOS/") ? "Chrome" : ua.contains("Safari/") ? "Safari" : "其他浏览器";
        return os + " · " + browser;
    }
    private String truncate(String value, int max) {
        return value == null ? "" : value.substring(0, Math.min(max, value.length()));
    }
}
