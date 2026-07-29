/**
 * 外部化配置模型。
 *
 * <p>这里的 record 与 {@code application.properties} 中的配置前缀绑定。
 * 密钥和商户凭据只允许通过环境变量或本地忽略文件注入，禁止写入源码和数据库迁移。</p>
 */
package com.share.spring_boot_demo1.config;
