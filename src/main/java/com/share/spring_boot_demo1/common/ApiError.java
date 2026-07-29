package com.share.spring_boot_demo1.common;

import java.time.Instant;
import java.util.Map;

/**
 * 所有 API 错误的统一响应结构。
 *
 * <p>code 供前端稳定判断错误类型，message 面向用户，details 承载字段级校验信息。</p>
 */
public record ApiError(
        String code,
        String message,
        Map<String, String> details,
        Instant timestamp,
        String path
) {
    public static ApiError of(String code, String message, Map<String, String> details, String path) {
        return new ApiError(code, message, details, Instant.now(), path);
    }
}
