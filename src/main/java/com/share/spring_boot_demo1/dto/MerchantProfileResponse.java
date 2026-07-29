package com.share.spring_boot_demo1.dto;

import java.util.List;

/**
 * 当前商家员工、所属商户及角色信息。
 */
public record MerchantProfileResponse(
        long id,
        long merchantId,
        String merchantCode,
        String merchantName,
        String email,
        String name,
        List<String> roles,
        boolean forcePasswordChange
) {
    public MerchantProfileResponse {
        roles = List.copyOf(roles);
    }
}
