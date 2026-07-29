package com.share.spring_boot_demo1.dto;

/**
 * 收货地址响应，不包含软删除等内部字段。
 */
public record AddressResponse(
        Long id,
        String name,
        String phone,
        String province,
        String city,
        String district,
        String detail,
        String postalCode,
        String tag,
        boolean isDefault
) {
}
