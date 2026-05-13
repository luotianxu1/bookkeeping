package com.example.common.result;

import lombok.Getter;

@Getter
public enum ResultCodeEnum {
    SUCCESS(0, "success"),
    FAIL(500, "fail"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有访问权限"),
    SERVER_ERROR(500, "服务器异常");

    private final Integer code;
    private final String message;

    ResultCodeEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
