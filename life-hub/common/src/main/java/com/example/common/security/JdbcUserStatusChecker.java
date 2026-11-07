package com.example.common.security;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 用户状态回查的 JDBC 实现（带 30 秒本地缓存，统一 KeyedTtlCache：single-flight + 失败降级 + 容量上限）。
 * <p>
 * 仅由持有业务库连接的服务（auth-service / app-service）注册为 Bean；
 * 无库服务（market-service）应提供走内部接口的远程实现。
 * 查询失败且无仍在有效期内的可信状态时返回 503。
 */
public class JdbcUserStatusChecker implements UserStatusChecker {

    private final JdbcTemplate jdbcTemplate;
    private final UserStatusCache cache = new UserStatusCache();

    public JdbcUserStatusChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isActive(long userId) {
        return cache.isActive(userId, this::isActiveFresh);
    }

    @Override
    public boolean isActiveFresh(long userId) {
        try {
            List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM users WHERE id = ?", String.class, userId);
            return !statuses.isEmpty() && "active".equalsIgnoreCase(statuses.get(0));
        } catch (DataAccessException ex) {
            throw new UserStatusUnavailableException();
        }
    }
}
