/**
 * 第三方支付渠道适配层。
 *
 * <p>该包只负责与支付宝 SDK 交互、验签和把渠道状态转换为内部状态，
 * 不直接修改订单或库存。所有业务状态变更统一交给
 * {@code com.share.spring_boot_demo1.service.PaymentStateService} 在事务中完成。</p>
 */
package com.share.spring_boot_demo1.payment;
