package com.example.common.security;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.Set;

/**
 * 家庭数据可见范围判定的 JDBC 实现。
 * <p>
 * 仅由持有业务库连接的服务（auth-service / app-service）注册为 Bean；
 * 无库服务（market-service）应提供走内部接口的远程实现。
 * 查询失败时降级为仅本人可见（fail-closed）。
 */
public class JdbcFamilyScopeChecker implements FamilyScopeChecker {

    private final JdbcTemplate jdbcTemplate;

    public JdbcFamilyScopeChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<Long> readableUserIds(long selfId) {
        try {
            return new HashSet<>(jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE id = ? "
                    + "OR (family_id IS NOT NULL AND family_id = (SELECT family_id FROM users WHERE id = ?))",
                Long.class,
                selfId,
                selfId
            ));
        } catch (DataAccessException ignored) {
            return Set.of(selfId);
        }
    }
}
