package com.share.spring_boot_demo1.payment;

import java.util.Map;

/**
 * 支付宝渠道端口。
 *
 * <p>业务层只依赖该接口，便于在测试中替换网关，也便于未来接入不同支付宝实现。</p>
 */
public interface AlipayGateway {
    /** 当前运行环境是否具备完整支付宝配置。 */
    boolean isConfigured();

    /** 创建支付页面表单。 */
    String createPagePayment(PaymentOrder payment);

    /** 主动查询交易状态。 */
    ProviderPaymentResult query(String paymentNo);

    /** 关闭尚未支付的交易。 */
    ProviderPaymentResult close(String paymentNo);

    /** 验证并解析支付宝异步通知。 */
    AlipayNotification verifyNotification(Map<String, String> parameters);
}
