package com.example.common.security;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 当前请求用户身份工具。
 * <p>
 * userId 由 JwtTokenAuthenticationFilter 从 JWT 的 userId claim 中解出，
 * 写入请求属性；业务代码一律通过本类获取，禁止信任前端传入的 userId。
 */
public final class SecurityUtils {

    /** 请求属性 key：当前登录用户 ID */
    public static final String USER_ID_ATTRIBUTE = "app.security.userId";

    private SecurityUtils() {
    }

    /** 当前登录用户 ID，未登录或身份缺失时返回 null */
    public static Long currentUserId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        Object value = attributes.getRequest().getAttribute(USER_ID_ATTRIBUTE);
        return value instanceof Long userId ? userId : null;
    }

    /** 当前登录用户 ID，缺失时抛出异常（由全局异常处理器转为 401） */
    public static Long requireCurrentUserId() {
        Long userId = currentUserId();
        if (userId == null) {
            throw new UserIdentityMissingException("未登录或登录已过期");
        }
        return userId;
    }
}
