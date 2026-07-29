package com.share.spring_boot_demo1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 商家员工改密请求。
 */
public record MerchantPasswordChangeRequest(
        @NotBlank(message = "请输入当前密码")
        @Size(max = 72, message = "当前密码长度不能超过 72 个字符")
        String currentPassword,

        @NotBlank(message = "请输入新密码")
        @Size(min = 12, max = 72, message = "新密码长度必须在 12 到 72 个字符之间")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "新密码必须同时包含字母和数字"
        )
        String newPassword
) {
}
