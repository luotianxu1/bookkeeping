package com.example.common.security;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户状态回查（带 30 秒本地缓存）。
 * <p>
 * 用户被注销/禁用（users.status != 'active'）后，其 JWT 在最长 30 秒内失效，
 * 无需等待 token 自然过期。查询失败时放行（fail-open），避免数据库抖动导致全站不可用。
 */
@Component
public class UserStatusChecker {

    private static final long CACHE_TTL_MS = 30_000L;

    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentHashMap<Long, StatusEntry> cache = new ConcurrentHashMap<>();

    public UserStatusChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 用户是否存在且状态为 active */
    public boolean isActive(long userId) {
        long now = System.currentTimeMillis();
        StatusEntry entry = cache.get(userId);
        if (entry != null && now < entry.expireAt()) {
            return entry.active();
        }

        try {
            List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM users WHERE id = ?",
                String.class,
                userId
            );
            boolean active = !statuses.isEmpty() && "active".equalsIgnoreCase(statuses.get(0));
            cache.put(userId, new StatusEntry(active, now + CACHE_TTL_MS));
            return active;
        } catch (DataAccessException ignored) {
            // 数据库不可用时放行：此时业务库同样不可用，服务整体已无法正常工作
            return true;
        }
    }

    private record StatusEntry(boolean active, long expireAt) {
    }
}
