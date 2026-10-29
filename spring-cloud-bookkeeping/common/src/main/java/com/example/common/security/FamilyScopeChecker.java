package com.example.common.security;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 家庭数据可见范围判定。
 * <p>
 * 规则：可读范围 = 自己 + 与自己同 family_id 的用户（家庭功能为只读视角，写操作始终仅限本人）。
 * 四个服务共享同一个 MySQL 实例与 users 表，此处直接查询；查询失败时降级为仅本人可见（fail-closed）。
 */
@Component
public class FamilyScopeChecker {

    private final JdbcTemplate jdbcTemplate;

    public FamilyScopeChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 返回 selfId 可读的用户 ID 集合（含 selfId 本身），查询失败时仅返回 selfId */
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

    /** selfId 是否可读 targetId 的数据 */
    public boolean canRead(long selfId, long targetId) {
        if (selfId == targetId) {
            return true;
        }
        return readableUserIds(selfId).contains(targetId);
    }

    /** 读场景：资源归属用户必须在可读范围内，否则按“不存在”处理（不泄露资源存在性） */
    public void requireReadable(long selfId, Long resourceUserId, String notFoundMessage) {
        if (resourceUserId == null || !canRead(selfId, resourceUserId)) {
            throw new IllegalArgumentException(notFoundMessage);
        }
    }

    /** 写场景：资源归属用户必须是本人 */
    public void requireSelf(long selfId, Long resourceUserId, String notFoundMessage) {
        if (resourceUserId == null || resourceUserId != selfId) {
            throw new IllegalArgumentException(notFoundMessage);
        }
    }
}
