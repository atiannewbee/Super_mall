package com.share.spring_boot_demo1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 支付渠道选择请求。
 */
public record PaymentRequest(
        @NotBlank @Pattern(regexp = "^(ALIPAY|WECHAT_PAY|UNION_PAY)$", message = "不支持该支付方式") String channel
) {
}
