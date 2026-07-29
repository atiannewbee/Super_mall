package com.share.spring_boot_demo1.controller;

import com.share.spring_boot_demo1.common.PageResponse;
import com.share.spring_boot_demo1.dto.AfterSaleCreateRequest;
import com.share.spring_boot_demo1.dto.AfterSaleResponse;
import com.share.spring_boot_demo1.dto.ReturnShipmentRequest;
import com.share.spring_boot_demo1.security.CurrentUser;
import com.share.spring_boot_demo1.service.AfterSaleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消费者售后申请、取消和退货物流接口。
 */
@Validated
@RestController
@RequestMapping("/api/after-sales")
public class AfterSaleController {
    private final AfterSaleService service;

    public AfterSaleController(AfterSaleService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AfterSaleResponse create(@AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody AfterSaleCreateRequest request) {
        return service.create(CurrentUser.id(jwt), request);
    }

    @GetMapping
    public PageResponse<AfterSaleResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(required = false) @Size(max = 30) String status,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.list(CurrentUser.id(jwt), status, page, size);
    }

    @GetMapping("/{afterSaleNo}")
    public AfterSaleResponse get(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable @Size(max = 40) String afterSaleNo) {
        return service.getRequired(CurrentUser.id(jwt), afterSaleNo);
    }

    @PostMapping("/{afterSaleNo}/cancel")
    public AfterSaleResponse cancel(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable @Size(max = 40) String afterSaleNo) {
        return service.cancel(CurrentUser.id(jwt), afterSaleNo);
    }

    @PatchMapping("/{afterSaleNo}/return-shipment")
    public AfterSaleResponse submitReturnShipment(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable @Size(max = 40) String afterSaleNo,
                                                   @Valid @RequestBody ReturnShipmentRequest request) {
        return service.submitReturnShipment(CurrentUser.id(jwt), afterSaleNo, request);
    }
}
