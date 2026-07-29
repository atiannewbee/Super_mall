package com.share.spring_boot_demo1.payment;

/**
 * 与具体渠道无关的支付状态，供业务状态机统一处理。
 */
public enum ProviderPaymentStatus {
    PENDING,
    SUCCESS,
    CLOSED,
    FAILED,
    NOT_FOUND,
    UNKNOWN
}
