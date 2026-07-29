package com.share.spring_boot_demo1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户提交退货物流的请求。
 */
public record ReturnShipmentRequest(
        @NotBlank @Size(max = 80) String carrier,
        @NotBlank @Size(max = 100) String trackingNo
) {
}
