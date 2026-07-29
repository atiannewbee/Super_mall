package com.share.spring_boot_demo1.security;

import com.share.spring_boot_demo1.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 从商家 JWT 中提取员工身份和所属商户范围。
 */
public final class CurrentMerchant {
    private CurrentMerchant() {
    }

    /**
     * 返回当前商家员工主键，用于审计操作人。
     */
    public static long userId(Jwt jwt) {
        return requiredLong(jwt.getSubject());
    }

    /**
     * 返回当前员工所属商户主键，用于所有查询的租户隔离。
     */
    public static long merchantId(Jwt jwt) {
        Object claim = jwt.getClaim("merchant_id");
        return requiredLong(claim);
    }

    private static long requiredLong(Object value) {
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_MERCHANT_TOKEN", "商家登录凭证无效");
        }
    }
}
