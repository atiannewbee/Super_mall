package com.share.spring_boot_demo1.controller;

import com.share.spring_boot_demo1.common.PageResponse;
import com.share.spring_boot_demo1.dto.CreateOrderRequest;
import com.share.spring_boot_demo1.dto.OrderResponse;
import com.share.spring_boot_demo1.dto.PaymentLaunchResponse;
import com.share.spring_boot_demo1.dto.PaymentRequest;
import com.share.spring_boot_demo1.dto.PaymentResponse;
import com.share.spring_boot_demo1.security.CurrentUser;
import com.share.spring_boot_demo1.service.OrderService;
import com.share.spring_boot_demo1.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消费者订单、支付创建、取消与确认收货接口。
 */
@Validated
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;
    private final PaymentService paymentService;

    public OrderController(OrderService service, PaymentService paymentService) {
        this.service = service;
        this.paymentService = paymentService;
    }

    /**
     * 创建订单；客户端应为每次结算生成唯一 Idempotency-Key。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@AuthenticationPrincipal Jwt jwt,
                                @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                @Valid @RequestBody CreateOrderRequest request) {
        return service.create(CurrentUser.id(jwt), request, idempotencyKey);
    }

    @GetMapping
    public PageResponse<OrderResponse> list(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(required = false) @Size(max = 32) String status,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.list(CurrentUser.id(jwt), status, page, size);
    }

    @GetMapping("/{orderNo}")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable @Size(max = 32) String orderNo) {
        return service.getRequired(CurrentUser.id(jwt), orderNo);
    }

    /**
     * 先安全关闭外部支付交易，再进入本地取消与库存释放事务。
     */
    @PostMapping("/{orderNo}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable @Size(max = 32) String orderNo) {
        long userId = CurrentUser.id(jwt);
        paymentService.closePendingForOwnedOrder(userId, orderNo);
        return service.cancel(userId, orderNo);
    }

    @PostMapping("/{orderNo}/payments")
    public PaymentLaunchResponse createPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 32) String orderNo,
            @Valid @RequestBody PaymentRequest request
    ) {
        return paymentService.create(CurrentUser.id(jwt), orderNo, request);
    }

    @PostMapping("/{orderNo}/payments/sandbox")
    public PaymentResponse pay(@AuthenticationPrincipal Jwt jwt, @PathVariable @Size(max = 32) String orderNo,
                               @Valid @RequestBody PaymentRequest request) {
        return service.paySandbox(CurrentUser.id(jwt), orderNo, request);
    }

    @PostMapping("/{orderNo}/confirm-receipt")
    public OrderResponse confirmReceipt(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable @Size(max = 32) String orderNo) {
        return service.confirmReceipt(CurrentUser.id(jwt), orderNo);
    }
}
