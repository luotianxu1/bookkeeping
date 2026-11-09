package com.example.common.security;

/** 会话撤销必须在各服务即时生效，不缓存成功结果。 */
public interface SessionChecker {
    boolean isValid(long userId, String sessionId);
}
