/**
 * 认证、授权和当前身份解析。
 *
 * <p>消费者与商家使用不同的密钥、签发方、受众和过滤器链。
 * JWT 通过签名后仍会检查数据库中的账号状态；商家令牌还校验 tokenVersion，
 * 从而支持锁定账号或改密后立即撤销已签发令牌。</p>
 */
package com.share.spring_boot_demo1.security;
