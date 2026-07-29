package com.share.spring_boot_demo1.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 商家员工登录请求。
 */
public record MerchantLoginRequest(
        @NotBlank(message = "请输入邮箱")
        @Email(message = "邮箱格式不正确")
        @Size(max = 255, message = "邮箱长度不能超过 255 个字符")
        String email,

        @NotBlank(message = "请输入密码")
        @Size(min = 8, max = 72, message = "密码长度必须在 8 到 72 个字符之间")
        String password
) {
}
