package com.share.spring_boot_demo1.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把业务异常、参数校验异常和未知异常映射为一致的 API 错误响应。
 *
 * <p>未知异常只在服务端记录完整日志，对客户端返回通用消息，避免泄露 SQL 或调用栈。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApiException(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(ApiError.of(
                exception.getCode(), exception.getMessage(), exception.getDetails(), request.getRequestURI()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                details.putIfAbsent(error.getField(), error.getDefaultMessage() == null ? "参数不合法" : error.getDefaultMessage())
        );
        return ResponseEntity.badRequest().body(ApiError.of(
                "VALIDATION_FAILED", "请求参数校验失败", details, request.getRequestURI()
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                details.put(violation.getPropertyPath().toString(), violation.getMessage())
        );
        return ResponseEntity.badRequest().body(ApiError.of(
                "VALIDATION_FAILED", "请求参数校验失败", details, request.getRequestURI()
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "INVALID_JSON", "请求体不是有效的 JSON", Map.of(), request.getRequestURI()
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("Database constraint rejected request at {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "DATA_CONFLICT", "数据与现有记录冲突", Map.of(), request.getRequestURI()
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(
                "FORBIDDEN", "没有权限执行该操作", Map.of(), request.getRequestURI()
        ));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        // 详细异常仅进入受控日志，响应中不返回 exception.getMessage()。
        log.error("Unhandled request failure at {}", request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(
                "INTERNAL_ERROR", "服务器暂时无法处理该请求", Map.of(), request.getRequestURI()
        ));
    }
}
