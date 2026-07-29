/**
 * API 公共基础设施。
 *
 * <p>集中维护统一响应结构、分页模型、业务异常和全局异常映射。
 * 业务代码应抛出带稳定错误码的 {@code ApiException}，由这里统一转换为 HTTP 响应，
 * 不要在各个 Controller 中重复捕获并拼装错误 JSON。</p>
 */
package com.share.spring_boot_demo1.common;
