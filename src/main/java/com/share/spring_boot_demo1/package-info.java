/**
 * SUPER MALL 后端根包。
 *
 * <p>项目采用按技术职责分包的模块化单体结构：Controller 负责 HTTP 协议，
 * Service 负责业务事务，Repository 负责数据读取，Security 负责身份边界，
 * Payment 负责第三方支付适配。新增业务时应保持依赖从接口层流向业务层，
 * 避免在 Controller 中直接编写 SQL 或业务状态变更。</p>
 */
package com.share.spring_boot_demo1;
