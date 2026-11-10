package com.example.app.service;

import com.example.common.security.SecurityUtils;
import com.example.common.security.UserStatusUnavailableException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AdminAccessService {
    private final JdbcTemplate jdbc;

    public AdminAccessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 每次从数据库核验角色，降权立即生效，不信任客户端或旧 JWT 中的角色。 */
    public long requireAdmin() {
        long userId = SecurityUtils.requireCurrentUserId();
        Integer allowed;
        try {
            allowed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM users
                WHERE id = ? AND status = 'active' AND role_name IN ('admin', 'super_admin')
                """, Integer.class, userId);
        } catch (DataAccessException ex) {
            throw new UserStatusUnavailableException();
        }
        if (allowed == null || allowed == 0) throw new AccessDeniedException("仅管理员可管理全局定时任务");
        return userId;
    }
}
