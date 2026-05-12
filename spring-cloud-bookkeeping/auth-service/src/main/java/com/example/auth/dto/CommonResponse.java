package com.example.auth.dto;

public record CommonResponse<T>(
    int code,
    T data,
    String message,
    long timestamp
) {
    public static <T> CommonResponse<T> success(T data) {
        return new CommonResponse<>(0, data, "success", System.currentTimeMillis());
    }

    public static <T> CommonResponse<T> error(int code, String message) {
        return new CommonResponse<>(code, null, message, System.currentTimeMillis());
    }
}
