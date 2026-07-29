package com.share.spring_boot_demo1.security;

import com.share.spring_boot_demo1.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 从已通过安全过滤器验证的消费者 JWT 中提取当前用户 ID。
 */
public final class CurrentUser {
    private CurrentUser() {
    }

    /**
     * 返回 JWT subject 中的用户主键；声明格式异常时统一视为未授权。
     */
    public static long id(Jwt jwt) {
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "登录凭证无效");
        }
    }
}
