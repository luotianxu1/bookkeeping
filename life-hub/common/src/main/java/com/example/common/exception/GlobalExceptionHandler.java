package com.example.common.exception;

import com.example.common.result.Result;
import com.example.common.result.ResultCodeEnum;
import com.example.common.security.UserIdentityMissingException;
import com.example.common.security.UserStatusUnavailableException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage() == null ? "请求参数错误" : error.getDefaultMessage())
            .orElse("请求参数错误");
        return ResponseEntity.badRequest().body(Result.<Void>build(null, ResultCodeEnum.BAD_REQUEST).message(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Result.<Void>build(null, ResultCodeEnum.BAD_REQUEST).message("请求体格式错误"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(Result.<Void>build(null, ResultCodeEnum.BAD_REQUEST).message(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(Result.<Void>build(null, ResultCodeEnum.BAD_REQUEST).message("请求参数格式错误"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        // IAE/ISE 是业务校验/状态提示的主要载体，返回 message 前 先在服务端留痕便于排查
        log.warn("业务校验异常: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Result.<Void>build(null, ResultCodeEnum.BAD_REQUEST).message(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Result<Void>> handleIllegalState(IllegalStateException ex) {
        // ISE 同时承载业务状态提示与上游/内部错误（客户端类包装行情中心响应失败等），
        // message 仅由服务端代码拼装（不得内嵌原始异常细节），完整堆栈与原始 cause 只留日志
        log.warn("业务状态异常: {}", ex.getMessage(), ex);
        return ResponseEntity.badRequest().body(Result.<Void>build(null, ResultCodeEnum.BAD_REQUEST).message(ex.getMessage()));
    }

    @ExceptionHandler(UserIdentityMissingException.class)
    public ResponseEntity<Result<Void>> handleUserIdentityMissing(UserIdentityMissingException ex) {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(Result.<Void>fail().code(401).message(ex.getMessage()));
    }

    @ExceptionHandler(UserStatusUnavailableException.class)
    public ResponseEntity<Result<Void>> handleUserStatusUnavailable(UserStatusUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", "5")
            .body(Result.<Void>fail().code(HttpStatus.SERVICE_UNAVAILABLE.value()).message(ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("数据库约束异常", ex);
        return ResponseEntity.badRequest().body(Result.<Void>build(null, ResultCodeEnum.BAD_REQUEST).message("提交的数据不符合约束，请检查账户、持仓或关联记录"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        // 未匹配到任何路由/静态资源：返回 404 而不是落进 Exception 兜底的 500，避免排障时误导
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Result.build(null, ResultCodeEnum.NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex) {
        log.error("未处理异常", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Result.build(null, ResultCodeEnum.SERVER_ERROR));
    }
}
