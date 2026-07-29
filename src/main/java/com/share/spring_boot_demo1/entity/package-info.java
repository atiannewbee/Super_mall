/**
 * JPA 持久化实体与枚举。
 *
 * <p>当前 JPA 主要服务于用户和分类等基础模型；交易、支付和履约模块为了精确控制锁、
 * 条件更新与 SQL 执行顺序，主要使用 JDBC。实体不得直接作为 Controller 响应返回。</p>
 */
package com.share.spring_boot_demo1.entity;
