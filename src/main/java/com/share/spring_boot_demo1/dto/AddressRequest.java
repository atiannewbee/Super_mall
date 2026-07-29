package com.share.spring_boot_demo1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新建或更新收货地址的请求。
 */
public record AddressRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Pattern(regexp = "^[0-9+() -]{6,20}$", message = "手机号格式不正确") String phone,
        @NotBlank @Size(max = 50) String province,
        @NotBlank @Size(max = 50) String city,
        @NotBlank @Size(max = 50) String district,
        @NotBlank @Size(max = 255) String detail,
        @Size(max = 20) String postalCode,
        @Size(max = 20) String tag,
        boolean isDefault
) {
}
