package com.example.common.security;

/**
 * 用户状态回查接口。
 * <p>
 * 用户被注销/禁用（users.status != 'active'）后，其 JWT 应在短时间内失效，
 * 无需等待 token 自然过期。
 * <p>
 * 实现：
 * <ul>
 *   <li>{@link JdbcUserStatusChecker}：直查业务库（auth-service / app-service）</li>
 *   <li>market-service 等无库服务：走 auth-service 内部接口的远程实现</li>
 * </ul>
 */
public interface UserStatusChecker {

    /** 用户是否存在且状态为 active */
    boolean isActive(long userId);
}
