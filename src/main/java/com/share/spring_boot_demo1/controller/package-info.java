/**
 * HTTP 接口适配层。
 *
 * <p>Controller 只负责参数校验、提取当前身份、调用 Service 并选择 HTTP 状态码。
 * 消费者接口位于 {@code /api/**}，商家接口位于 {@code /api/merchant/**}；
 * 两套路径由不同的 JWT 解码器保护，不能共用登录凭证。</p>
 */
package com.share.spring_boot_demo1.controller;
