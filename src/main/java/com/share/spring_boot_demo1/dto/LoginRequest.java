package com.share.spring_boot_demo1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 消费者登录请求。
 */
public record LoginRequest(
        @NotBlank(message = "账号不能为空") String account,
        @NotBlank(message = "密码不能为空")
        @Size(max = 72, message = "密码不能超过72个字符") String password
) {
}
