package com.share.spring_boot_demo1.controller;

import com.share.spring_boot_demo1.common.PageResponse;
import com.share.spring_boot_demo1.dto.MerchantDashboardResponse;
import com.share.spring_boot_demo1.dto.MerchantInventoryResponse;
import com.share.spring_boot_demo1.dto.MerchantOrderResponse;
import com.share.spring_boot_demo1.dto.MerchantProductRequest;
import com.share.spring_boot_demo1.dto.MerchantShipRequest;
import com.share.spring_boot_demo1.security.CurrentMerchant;
import com.share.spring_boot_demo1.service.MerchantOperationsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 商家运营中心的看板、履约和库存接口。
 *
 * <p>方法级权限限制只决定“谁能操作”，Service 中的 merchantId 条件继续负责“能操作哪家商户的数据”。</p>
 */
@Validated
@RestController
@RequestMapping("/api/merchant")
public class MerchantOperationsController {
    private final MerchantOperationsService service;

    public MerchantOperationsController(MerchantOperationsService service) {
        this.service = service;
    }

    /*
     * 商品写接口放在库存中心控制器中，避免为首版单 SKU 管理额外拆分模块。
     * Controller 只负责身份、权限和参数校验，租户隔离与事务统一由 Service 完成。
     */

    @GetMapping("/dashboard")
    public MerchantDashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        return service.dashboard(CurrentMerchant.merchantId(jwt));
    }

    @GetMapping("/orders")
    public PageResponse<MerchantOrderResponse> orders(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Size(max = 32) String fulfillmentStatus,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return service.listOrders(
                CurrentMerchant.merchantId(jwt),
                fulfillmentStatus,
                query,
                page,
                size
        );
    }

    @GetMapping("/orders/{orderNo}")
    public MerchantOrderResponse order(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 32) String orderNo
    ) {
        return service.getOrder(CurrentMerchant.merchantId(jwt), orderNo);
    }

    /**
     * 开始拣货；只允许主管或仓库角色。
     */
    @PostMapping("/orders/{orderNo}/picking")
    @PreAuthorize("hasAnyAuthority('SCOPE_OWNER', 'SCOPE_WAREHOUSE')")
    public MerchantOrderResponse startPicking(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 32) String orderNo
    ) {
        return service.startPicking(
                CurrentMerchant.merchantId(jwt),
                CurrentMerchant.userId(jwt),
                orderNo
        );
    }

    /**
     * 确认发货；只允许主管或仓库角色。
     */
    @PostMapping("/orders/{orderNo}/ship")
    @PreAuthorize("hasAnyAuthority('SCOPE_OWNER', 'SCOPE_WAREHOUSE')")
    public MerchantOrderResponse ship(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 32) String orderNo,
            @Valid @RequestBody MerchantShipRequest request
    ) {
        return service.ship(
                CurrentMerchant.merchantId(jwt),
                CurrentMerchant.userId(jwt),
                orderNo,
                request
        );
    }

    @GetMapping("/inventory")
    public PageResponse<MerchantInventoryResponse> inventory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(defaultValue = "false") boolean lowStock,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return service.listInventory(
                CurrentMerchant.merchantId(jwt),
                query,
                lowStock,
                page,
                size
        );
    }

    /**
     * 新增商品时同步创建一个默认 SKU、库存和封面图。
     */
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('SCOPE_OWNER', 'SCOPE_OPERATOR')")
    public void createProduct(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MerchantProductRequest request
    ) {
        service.createProduct(
                CurrentMerchant.merchantId(jwt),
                CurrentMerchant.userId(jwt),
                request
        );
    }

    /**
     * 从库存行修改商品资料、当前 SKU 和可售库存。
     */
    @PutMapping("/products/{productId}/skus/{skuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('SCOPE_OWNER', 'SCOPE_OPERATOR')")
    public void updateProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long productId,
            @PathVariable long skuId,
            @Valid @RequestBody MerchantProductRequest request
    ) {
        service.updateProduct(
                CurrentMerchant.merchantId(jwt),
                CurrentMerchant.userId(jwt),
                productId,
                skuId,
                request
        );
    }

    /**
     * 删除只做软删除，避免破坏历史订单里的商品快照。
     */
    @DeleteMapping("/products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('SCOPE_OWNER', 'SCOPE_OPERATOR')")
    public void deleteProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long productId
    ) {
        service.deleteProduct(
                CurrentMerchant.merchantId(jwt),
                CurrentMerchant.userId(jwt),
                productId
        );
    }
}
