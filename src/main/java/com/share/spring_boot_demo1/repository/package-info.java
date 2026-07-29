/**
 * 数据访问层。
 *
 * <p>简单实体使用 Spring Data Repository；商品目录等聚合读取使用 JDBC，
 * 以避免 N+1 查询并保持响应结构可控。动态 SQL 只能拼接白名单片段，用户输入必须作为参数绑定。</p>
 */
package com.share.spring_boot_demo1.repository;
