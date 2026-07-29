package com.share.spring_boot_demo1.service;

import com.share.spring_boot_demo1.common.ApiException;
import com.share.spring_boot_demo1.config.OrderProperties;
import com.share.spring_boot_demo1.dto.PaymentLaunchResponse;
import com.share.spring_boot_demo1.dto.PaymentRequest;
import com.share.spring_boot_demo1.dto.PaymentResponse;
import com.share.spring_boot_demo1.payment.AlipayGateway;
import com.share.spring_boot_demo1.payment.AlipayNotification;
import com.share.spring_boot_demo1.payment.PaymentRecord;
import com.share.spring_boot_demo1.payment.ProviderPaymentResult;
import com.share.spring_boot_demo1.payment.ProviderPaymentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 支付流程编排服务。
 *
 * <p>负责选择支付渠道、调用外部网关和驱动本地状态服务，不直接执行订单或库存 SQL。
 * 这样可以把不可控的网络调用与必须原子提交的数据库事务分离。</p>
 */
@Service
public class PaymentService {
    private final PaymentStateService stateService;
    private final AlipayGateway alipayGateway;
    private final OrderProperties orderProperties;

    public PaymentService(
            PaymentStateService stateService,
            AlipayGateway alipayGateway,
            OrderProperties orderProperties
    ) {
        this.stateService = stateService;
        this.alipayGateway = alipayGateway;
        this.orderProperties = orderProperties;
    }

    /**
     * 为待支付订单创建或复用支付宝支付单，返回前端跳转信息。
     */
    public PaymentLaunchResponse create(long userId, String orderNo, PaymentRequest request) {
        if (!"ALIPAY".equals(request.channel())) {
            throw new ApiException(
                    HttpStatus.NOT_IMPLEMENTED,
                    "PAYMENT_CHANNEL_NOT_AVAILABLE",
                    "当前只开放支付宝支付"
            );
        }
        requireAlipay();
        PaymentRecord payment = stateService.createAlipayPayment(
                userId,
                orderNo,
                LocalDateTime.now().plus(orderProperties.pendingPaymentTtl())
        );
        return new PaymentLaunchResponse(
                payment.paymentNo(),
                payment.orderNo(),
                payment.channel(),
                external(payment.status()),
                payment.amount(),
                payment.currency(),
                "redirect",
                "/api/payments/alipay/" + payment.paymentNo() + "/launch",
                payment.expiresAt()
        );
    }

    /**
     * 生成支付宝电脑网站支付页面；只接受仍可支付且未过期的支付单。
     */
    public String launchAlipay(String paymentNo) {
        requireAlipay();
        return alipayGateway.createPagePayment(stateService.findLaunchable(paymentNo).toPaymentOrder());
    }

    /**
     * 查询用户自己的支付单；待支付时主动向支付宝刷新一次最终状态。
     */
    public PaymentResponse getAndRefresh(long userId, String paymentNo) {
        PaymentRecord payment = stateService.findOwned(userId, paymentNo);
        if ("ALIPAY".equals(payment.channel()) && "PENDING".equals(payment.status())) {
            requireAlipay();
            ProviderPaymentResult result = alipayGateway.query(payment.paymentNo());
            payment = stateService.applyProviderResult(payment.paymentNo(), result, "支付宝主动查询");
        }
        return payment.toResponse();
    }

    /**
     * 验证并处理支付宝异步通知。
     *
     * <p>网关完成 RSA2 验签与商户身份校验，状态服务再完成金额、交易号和幂等校验。</p>
     */
    public void handleAlipayNotification(Map<String, String> parameters) {
        requireAlipay();
        AlipayNotification notification = alipayGateway.verifyNotification(parameters);
        if (notification.status() == ProviderPaymentStatus.UNKNOWN) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_ALIPAY_TRADE_STATUS",
                    "支付宝通知包含未知交易状态"
            );
        }
        stateService.applyNotification(notification);
    }

    /**
     * 用户取消订单前关闭其仍在进行的外部支付交易。
     */
    public void closePendingForOwnedOrder(long userId, String orderNo) {
        closePayments(stateService.pendingForOwnedOrder(userId, orderNo));
    }

    /**
     * 定时关单前关闭外部支付交易；只有确认没有待处理支付时才允许释放库存。
     */
    public boolean closePendingForExpiration(String orderNo) {
        List<PaymentRecord> payments = stateService.pendingForOrder(orderNo);
        if (payments.isEmpty()) {
            return true;
        }
        closePayments(payments);
        return stateService.pendingForOrder(orderNo).isEmpty();
    }

    private void closePayments(List<PaymentRecord> payments) {
        for (PaymentRecord payment : payments) {
            if (!"ALIPAY".equals(payment.channel())) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "PAYMENT_CHANNEL_CLOSE_UNSUPPORTED",
                        "订单存在尚不能自动关闭的支付渠道"
                );
            }
            requireAlipay();
            // 先查后关，避免用户刚完成付款而异步通知尚未到达时误关已支付订单。
            ProviderPaymentResult query = alipayGateway.query(payment.paymentNo());
            PaymentRecord refreshed = stateService.applyProviderResult(
                    payment.paymentNo(), query, "关闭前主动查询"
            );
            if ("SUCCESS".equals(refreshed.status())) {
                throw new ApiException(HttpStatus.CONFLICT, "ORDER_ALREADY_PAID", "订单已经支付，不能取消");
            }
            if (!"PENDING".equals(refreshed.status())) {
                continue;
            }
            ProviderPaymentResult closed = alipayGateway.close(payment.paymentNo());
            PaymentRecord afterClose = stateService.applyProviderResult(
                    payment.paymentNo(), closed, "关闭支付宝交易"
            );
            if ("PENDING".equals(afterClose.status())) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "ALIPAY_CLOSE_NOT_CONFIRMED",
                        "支付宝尚未确认关闭交易，请稍后重试"
                );
            }
        }
    }

    private void requireAlipay() {
        if (!alipayGateway.isConfigured()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PAYMENT_PROVIDER_NOT_CONFIGURED",
                    "支付宝沙箱尚未完成配置"
            );
        }
    }

    private String external(String status) {
        return status.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
