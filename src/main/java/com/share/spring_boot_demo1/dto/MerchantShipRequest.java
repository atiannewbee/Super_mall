package com.share.spring_boot_demo1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 商家确认发货请求，记录仓库、承运商和运单号。
 */
public record MerchantShipRequest(
        Long warehouseId,

        @NotBlank(message = "请输入物流公司代码")
        @Size(max = 30, message = "物流公司代码不能超过 30 个字符")
        String carrierCode,

        @NotBlank(message = "请输入物流公司名称")
        @Size(max = 80, message = "物流公司名称不能超过 80 个字符")
        String carrierName,

        @NotBlank(message = "请输入物流单号")
        @Size(max = 100, message = "物流单号不能超过 100 个字符")
        String trackingNo
) {
}
