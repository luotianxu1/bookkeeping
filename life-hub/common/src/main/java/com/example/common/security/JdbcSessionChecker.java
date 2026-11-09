package com.example.common.security;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcSessionChecker implements SessionChecker {
    private final JdbcTemplate jdbc;
    public JdbcSessionChecker(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean isValid(long userId, String sessionId) {
        if (sessionId == null || sessionId.length() != 36) return false;
        try {
            Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_sessions s JOIN users u ON u.id = s.user_id
                WHERE s.id = ? AND s.user_id = ? AND s.revoked_at IS NULL
                  AND s.expires_at > CURRENT_TIMESTAMP AND u.status = 'active'
                """, Integer.class, sessionId, userId);
            if (count == null || count == 0) return false;
            // 活跃时间按五分钟粒度更新；不能复活已退出或过期的会话。
            jdbc.update("""
                UPDATE user_sessions SET last_seen_at = CURRENT_TIMESTAMP
                WHERE id = ? AND user_id = ? AND revoked_at IS NULL
                  AND expires_at > CURRENT_TIMESTAMP AND last_seen_at < ?
                """, sessionId, userId, java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(300)));
            return true;
        } catch (DataAccessException ex) {
            throw new UserStatusUnavailableException();
        }
    }
}
