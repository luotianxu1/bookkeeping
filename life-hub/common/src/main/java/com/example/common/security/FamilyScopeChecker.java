package com.example.common.security;

import java.util.Set;

/**
 * 家庭数据可见范围判定接口。
 * <p>
 * 规则：可读范围 = 自己 + 与自己同 family_id 的用户（家庭功能为只读视角，写操作始终仅限本人）。
 * <p>
 * 实现：
 * <ul>
 *   <li>{@link JdbcFamilyScopeChecker}：直查业务库（auth-service / app-service）</li>
 *   <li>market-service 等无库服务：走 auth-service 内部接口的远程实现</li>
 * </ul>
 */
public interface FamilyScopeChecker {

    /** 返回 selfId 可读的用户 ID 集合（含 selfId 本身），查询失败时仅返回 selfId */
    Set<Long> readableUserIds(long selfId);

    /** selfId 是否可读 targetId 的数据 */
    default boolean canRead(long selfId, long targetId) {
        if (selfId == targetId) {
            return true;
        }
        return readableUserIds(selfId).contains(targetId);
    }

    /** 读场景：资源归属用户必须在可读范围内，否则按“不存在”处理（不泄露资源存在性） */
    default void requireReadable(long selfId, Long resourceUserId, String notFoundMessage) {
        if (resourceUserId == null || !canRead(selfId, resourceUserId)) {
            throw new IllegalArgumentException(notFoundMessage);
        }
    }

    /** 写场景：资源归属用户必须是本人 */
    default void requireSelf(long selfId, Long resourceUserId, String notFoundMessage) {
        if (resourceUserId == null || resourceUserId != selfId) {
            throw new IllegalArgumentException(notFoundMessage);
        }
    }
}
