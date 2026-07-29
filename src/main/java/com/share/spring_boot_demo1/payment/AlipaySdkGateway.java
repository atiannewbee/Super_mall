package com.share.spring_boot_demo1.payment;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.share.spring_boot_demo1.common.ApiException;
import com.share.spring_boot_demo1.config.AlipayProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * 基于支付宝官方 SDK 的支付渠道实现。
 *
 * <p>本类只处理协议、签名和渠道状态转换，不修改本地订单。
 * 所有 SDK 异常会被转换为稳定业务错误码，避免把密钥、原始报文或调用栈暴露给客户端。</p>
 */
@Component
public class AlipaySdkGateway implements AlipayGateway {
    private static final String CHARSET = StandardCharsets.UTF_8.name();
    private static final String SIGN_TYPE = "RSA2";
    private static final String FORMAT = "json";
    private static final String TRADE_NOT_EXIST = "ACQ.TRADE_NOT_EXIST";

    private final AlipayProperties properties;
    private final AlipayClient client;

    public AlipaySdkGateway(AlipayProperties properties) {
        this.properties = properties;
        this.client = properties.configured()
                ? new DefaultAlipayClient(
                properties.gatewayUrl(),
                properties.appId(),
                properties.privateKey(),
                FORMAT,
                CHARSET,
                properties.alipayPublicKey(),
                SIGN_TYPE
        )
                : null;
    }

    @Override
    public boolean isConfigured() {
        return properties.configured() && client != null;
    }

    /**
     * 生成电脑网站支付表单 HTML，由浏览器跳转到支付宝收银台。
     */
    @Override
    public String createPagePayment(PaymentOrder payment) {
        requireConfigured();
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(payment.paymentNo());
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        model.setTotalAmount(payment.amount().setScale(2).toPlainString());
        model.setSubject("Super Mall 订单 " + payment.orderNo());
        model.setTimeoutExpress(timeoutExpression(payment));

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setBizModel(model);
        request.setNotifyUrl(properties.notifyUrl());
        request.setReturnUrl(UriComponentsBuilder.fromUriString(properties.returnUrl())
                .queryParam("orderNo", payment.orderNo())
                .queryParam("paymentNo", payment.paymentNo())
                .queryParam("status", "return")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString());
        try {
            AlipayTradePagePayResponse response = client.pageExecute(request);
            if (response == null || response.getBody() == null || response.getBody().isBlank()) {
                throw providerFailure("ALIPAY_EMPTY_RESPONSE", "支付宝没有返回支付跳转页面");
            }
            return response.getBody();
        } catch (AlipayApiException exception) {
            throw providerFailure("ALIPAY_PAGE_PAY_FAILED", "支付宝支付页面生成失败", exception);
        }
    }

    /**
     * 主动查询支付宝交易，并归一化为内部渠道状态。
     */
    @Override
    public ProviderPaymentResult query(String paymentNo) {
        requireConfigured();
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(paymentNo);
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizModel(model);
        try {
            AlipayTradeQueryResponse response = client.execute(request);
            if (response.isSuccess()) {
                return new ProviderPaymentResult(
                        providerStatus(response.getTradeStatus()),
                        blankToNull(response.getTradeNo()),
                        decimalOrNull(response.getTotalAmount()),
                        null,
                        null
                );
            }
            if (TRADE_NOT_EXIST.equals(response.getSubCode())) {
                return new ProviderPaymentResult(
                        ProviderPaymentStatus.NOT_FOUND, null, null,
                        response.getSubCode(), response.getSubMsg()
                );
            }
            throw providerFailure(response.getSubCode(), safeMessage(response.getSubMsg(), "支付宝交易查询失败"));
        } catch (AlipayApiException exception) {
            throw providerFailure("ALIPAY_QUERY_FAILED", "支付宝交易查询失败", exception);
        }
    }

    /**
     * 关闭未支付交易；支付宝返回“交易不存在”也视为已安全关闭。
     */
    @Override
    public ProviderPaymentResult close(String paymentNo) {
        requireConfigured();
        AlipayTradeCloseModel model = new AlipayTradeCloseModel();
        model.setOutTradeNo(paymentNo);
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        request.setBizModel(model);
        try {
            AlipayTradeCloseResponse response = client.execute(request);
            if (response.isSuccess() || TRADE_NOT_EXIST.equals(response.getSubCode())) {
                return ProviderPaymentResult.closed(blankToNull(response.getTradeNo()));
            }
            throw providerFailure(response.getSubCode(), safeMessage(response.getSubMsg(), "支付宝交易关闭失败"));
        } catch (AlipayApiException exception) {
            throw providerFailure("ALIPAY_CLOSE_FAILED", "支付宝交易关闭失败", exception);
        }
    }

    /**
     * 对异步通知执行 RSA2 验签，并校验 AppID、可选 SellerID、金额格式和必填字段。
     */
    @Override
    public AlipayNotification verifyNotification(Map<String, String> parameters) {
        requireConfigured();
        try {
            boolean valid = AlipaySignature.rsaCheckV1(
                    parameters, properties.alipayPublicKey(), CHARSET, SIGN_TYPE
            );
            if (!valid) {
                throw invalidNotification("支付宝通知签名无效");
            }
        } catch (AlipayApiException exception) {
            throw invalidNotification("支付宝通知验签失败", exception);
        }

        // 验签只证明报文来自支付宝，还必须确认通知确实属于当前应用和收款账号。
        String appId = required(parameters, "app_id");
        if (!properties.appId().equals(appId)) {
            throw invalidNotification("支付宝通知 AppID 不匹配");
        }
        String sellerId = parameters.get("seller_id");
        if (!properties.sellerId().isBlank() && !properties.sellerId().equals(sellerId)) {
            throw invalidNotification("支付宝通知商户账号不匹配");
        }

        String paymentNo = required(parameters, "out_trade_no");
        String tradeStatus = required(parameters, "trade_status");
        BigDecimal amount;
        try {
            amount = new BigDecimal(required(parameters, "total_amount"));
        } catch (NumberFormatException exception) {
            throw invalidNotification("支付宝通知金额格式不正确", exception);
        }
        // notify_id 缺失时使用规范化报文哈希作为幂等键，避免同一通知重复处理。
        String payloadHash = payloadHash(parameters);
        String notificationId = blankToNull(parameters.get("notify_id"));
        if (notificationId == null) {
            notificationId = payloadHash;
        }
        return new AlipayNotification(
                paymentNo,
                blankToNull(parameters.get("trade_no")),
                providerStatus(tradeStatus),
                amount,
                notificationId,
                tradeStatus,
                payloadHash
        );
    }

    private String timeoutExpression(PaymentOrder payment) {
        if (payment.expiresAt() == null) {
            return "30m";
        }
        long minutes = Math.max(1, java.time.Duration.between(java.time.LocalDateTime.now(), payment.expiresAt()).toMinutes());
        return minutes + "m";
    }

    private ProviderPaymentStatus providerStatus(String tradeStatus) {
        if (tradeStatus == null) {
            return ProviderPaymentStatus.UNKNOWN;
        }
        return switch (tradeStatus) {
            case "WAIT_BUYER_PAY" -> ProviderPaymentStatus.PENDING;
            case "TRADE_SUCCESS", "TRADE_FINISHED" -> ProviderPaymentStatus.SUCCESS;
            case "TRADE_CLOSED" -> ProviderPaymentStatus.CLOSED;
            default -> ProviderPaymentStatus.UNKNOWN;
        };
    }

    private BigDecimal decimalOrNull(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private String payloadHash(Map<String, String> parameters) {
        TreeMap<String, String> sorted = new TreeMap<>(parameters);
        StringBuilder canonical = new StringBuilder();
        sorted.forEach((key, value) -> canonical.append(key).append('=').append(value).append('&'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ALIPAY_CONFIGURATION_INVALID",
                    "支付宝支付配置不完整"
            );
        }
    }

    private String required(Map<String, String> parameters, String name) {
        String value = blankToNull(parameters.get(name));
        if (value == null) {
            throw invalidNotification("支付宝通知缺少字段：" + name);
        }
        return value;
    }

    private ApiException invalidNotification(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ALIPAY_NOTIFICATION", message);
    }

    private ApiException invalidNotification(String message, Exception cause) {
        ApiException exception = invalidNotification(message);
        exception.initCause(cause);
        return exception;
    }

    private ApiException providerFailure(String code, String message) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                code == null || code.isBlank() ? "ALIPAY_PROVIDER_ERROR" : code,
                message
        );
    }

    private ApiException providerFailure(String code, String message, Exception cause) {
        ApiException exception = providerFailure(code, message);
        exception.initCause(cause);
        return exception;
    }

    private String safeMessage(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
