package com.example.auth.common.result;

import lombok.Data;

@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;
    private Long timestamp;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    protected static <T> Result<T> build(T data) {
        Result<T> result = new Result<>();
        if (data != null) {
            result.setData(data);
        }
        return result;
    }

    public static <T> Result<T> build(T data, Integer code, String message) {
        Result<T> result = build(data);
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> build(T data, ResultCodeEnum resultCodeEnum) {
        return build(data, resultCodeEnum.getCode(), resultCodeEnum.getMessage());
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        return build(data, ResultCodeEnum.SUCCESS);
    }

    public static <T> Result<T> fail() {
        return fail(null);
    }

    public static <T> Result<T> fail(T data) {
        return build(data, ResultCodeEnum.FAIL);
    }

    public Result<T> message(String message) {
        this.setMessage(message);
        return this;
    }

    public Result<T> code(Integer code) {
        this.setCode(code);
        return this;
    }
}
