package com.share.spring_boot_demo1.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建售后申请的请求；申请金额由服务端根据订单项计算。
 */
public record AfterSaleCreateRequest(
        @NotBlank @Size(max = 32) String orderNo,
        @NotBlank @Pattern(regexp = "^(REFUND_ONLY|RETURN_REFUND|EXCHANGE)$", message = "不支持该售后类型") String type,
        @Size(max = 50) String reasonCode,
        @NotBlank @Size(max = 500) String reasonDescription,
        @Size(max = 500) String customerNote,
        @NotEmpty @Size(max = 20) List<@Valid Item> items
) {
    public AfterSaleCreateRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record Item(
            @Min(1) long orderItemId,
            @Min(1) @Max(99) int quantity
    ) {
    }
}
