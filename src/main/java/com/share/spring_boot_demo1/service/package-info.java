/**
 * 核心业务与事务层。
 *
 * <p>订单、库存、支付、售后和履约状态必须在带 {@code @Transactional} 的 Service
 * 方法中变更。涉及并发的流程先锁定主记录，再以条件更新修改库存，并同步写入状态历史或审计日志；
 * 任何一步失败都应回滚整个事务。</p>
 */
package com.share.spring_boot_demo1.service;
