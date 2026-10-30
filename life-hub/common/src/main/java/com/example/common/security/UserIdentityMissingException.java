package com.example.common.security;

/**
 * 当前请求缺少可信用户身份（token 中没有 userId claim）时抛出。
 * 全局异常处理器应将其映射为 401，引导客户端重新登录换取新格式 token。
 */
public class UserIdentityMissingException extends RuntimeException {

    public UserIdentityMissingException(String message) {
        super(message);
    }
}
