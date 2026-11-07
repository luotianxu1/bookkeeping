package com.example.common.security;

/** 无可信的用户状态时，应返回可重试的 503，而不是放行或清除登录态。 */
public class UserStatusUnavailableException extends RuntimeException {
    public UserStatusUnavailableException() {
        super("暂时无法验证用户状态，请稍后重试");
    }
}
