package com.share.spring_boot_demo1;

import com.alipay.api.internal.util.AlipaySignature;
import com.share.spring_boot_demo1.common.ApiException;
import com.share.spring_boot_demo1.config.AlipayProperties;
import com.share.spring_boot_demo1.payment.AlipayNotification;
import com.share.spring_boot_demo1.payment.AlipaySdkGateway;
import com.share.spring_boot_demo1.payment.ProviderPaymentStatus;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证支付宝适配器的配置门槛和通知协议校验。
 */
class AlipaySdkGatewayTests {

    @Test
    void verifiesRsa2NotificationAndRejectsTamperedAmount() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        AlipayProperties properties = new AlipayProperties(
                true,
                "https://openapi-sandbox.dl.alipaydev.com/gateway.do",
                "2026000000000000",
                privateKey,
                publicKey,
                "2088000000000000",
                "https://api.example.com/api/payments/alipay/notify",
                "https://shop.example.com/checkout/result"
        );
        AlipaySdkGateway gateway = new AlipaySdkGateway(properties);

        Map<String, String> parameters = signedParameters(privateKey);
        AlipayNotification notification = gateway.verifyNotification(parameters);

        assertThat(notification.paymentNo()).isEqualTo("PAY202607230001");
        assertThat(notification.providerTradeNo()).isEqualTo("2026072322000000000001");
        assertThat(notification.status()).isEqualTo(ProviderPaymentStatus.SUCCESS);
        assertThat(notification.amount()).isEqualByComparingTo("4299.00");
        assertThat(notification.payloadHash()).hasSize(64);

        parameters.put("total_amount", "0.01");
        assertThatThrownBy(() -> gateway.verifyNotification(parameters))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("INVALID_ALIPAY_NOTIFICATION");
    }

    private Map<String, String> signedParameters(String privateKey) throws Exception {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("app_id", "2026000000000000");
        parameters.put("seller_id", "2088000000000000");
        parameters.put("out_trade_no", "PAY202607230001");
        parameters.put("trade_no", "2026072322000000000001");
        parameters.put("trade_status", "TRADE_SUCCESS");
        parameters.put("total_amount", "4299.00");
        parameters.put("notify_id", "notify-202607230001");
        parameters.put("sign_type", "RSA2");
        String content = AlipaySignature.getSignCheckContentV1(parameters);
        parameters.put("sign", AlipaySignature.rsaSign(content, privateKey, "UTF-8", "RSA2"));
        return parameters;
    }
}
