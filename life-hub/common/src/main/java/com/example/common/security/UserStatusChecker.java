package com.example.common.security;

import com.example.common.util.KeyedTtlCache;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户状态回查（带 30 秒本地缓存，统一 KeyedTtlCache：single-flight + 失败降级 + 容量上限）。
 * <p>
 * 用户被注销/禁用（users.status != 'active'）后，其 JWT 在最长 30 秒内失效，
 * 无需等待 token 自然过期。查询失败时放行（fail-open），避免数据库抖动导致全站不可用。
 */
@Component
public class UserStatusChecker {

    private static final long CACHE_TTL_MS = 30_000L;
    /** 失败降级窗口：窗口内回吐旧值（若有），窗口过后重试 */
    private static final long FAILURE_TTL_MS = 5_000L;
    /** 容量上限，防止异常流量下无界增长 */
    private static final int MAX_SIZE = 10_000;

    private final JdbcTemplate jdbcTemplate;
    private final KeyedTtlCache<Long, Boolean> cache =
        new KeyedTtlCache<>(CACHE_TTL_MS, FAILURE_TTL_MS, MAX_SIZE);

    public UserStatusChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 用户是否存在且状态为 active */
    public boolean isActive(long userId) {
        Boolean cached = cache.get(userId, id -> {
            try {
                List<String> statuses = jdbcTemplate.queryForList(
                    "SELECT status FROM users WHERE id = ?",
                    String.class,
                    id
                );
                return !statuses.isEmpty() && "active".equalsIgnoreCase(statuses.get(0));
            } catch (DataAccessException ex) {
                // 数据库不可用：抛给 KeyedTtlCache 走失败降级（有旧值回吐旧值，无旧值返回 null）
                throw new IllegalStateException("user status check failed", ex);
            }
        });
        // fail-open：DB 抖动且无缓存可用时放行——此时业务库同样不可用，服务整体已无法正常工作
        return cached == null || cached;
    }
}
