package com.example.common.security;

import com.example.common.util.KeyedTtlCache;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 用户状态回查的 JDBC 实现（带 30 秒本地缓存，统一 KeyedTtlCache：single-flight + 失败降级 + 容量上限）。
 * <p>
 * 仅由持有业务库连接的服务（auth-service / app-service）注册为 Bean；
 * 无库服务（market-service）应提供走内部接口的远程实现。
 * 查询失败时放行（fail-open），避免数据库抖动导致全站不可用。
 */
public class JdbcUserStatusChecker implements UserStatusChecker {

    private static final long CACHE_TTL_MS = 30_000L;
    /** 失败降级窗口：窗口内回吐旧值（若有），窗口过后重试 */
    private static final long FAILURE_TTL_MS = 5_000L;
    /** 容量上限，防止异常流量下无界增长 */
    private static final int MAX_SIZE = 10_000;

    private final JdbcTemplate jdbcTemplate;
    private final KeyedTtlCache<Long, Boolean> cache =
        new KeyedTtlCache<>(CACHE_TTL_MS, FAILURE_TTL_MS, MAX_SIZE);

    public JdbcUserStatusChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
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
