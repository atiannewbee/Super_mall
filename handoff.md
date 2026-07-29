# Handoff — Spring Boot Demo1

## 项目概要

这是一个**教学性质**的 Spring Boot 项目，面向 Java 基础入门、零框架经验的新手。项目从零开始逐步构建，当前已打通 **Controller → Service → Repository → MySQL** 完整链路，实现了用户表的基础 CRUD。

- **Spring Boot 版本：** 4.1.0（注意：非 3.x，API 基于 Jakarta EE）
- **Java：** 17
- **构建工具：** Maven（wrapper 已就绪，`./mvnw`）
- **IDE：** IntelliJ IDEA

---

## 技术栈

| 层面 | 选型 | 说明 |
|------|------|------|
| 框架 | Spring Boot 4.1.0 | `spring-boot-starter-webmvc` |
| ORM | Spring Data JPA / Hibernate | `spring-boot-starter-data-jpa` |
| 数据库 | MySQL | 驱动 `mysql-connector-j` |
| 模板引擎 | 尚未引入 | 下一步计划用 Thymeleaf |
| 安全 | 尚未引入 | 之后会引入 Spring Security |

---

## 当前进度

### 已完成 ✅

- [x] 项目骨架生成（Spring Initializr）
- [x] Hello World 级别的 REST 接口（`HelloController` 已删除，仅用于演示）
- [x] 标准分层架构建立：Controller / Service / Repository / Entity
- [x] 用户 CRUD 完整接口：
  - `GET    /users`     — 查询全部
  - `GET    /users/{id}` — 查询单个（含 404 处理）
  - `POST   /users`     — 新增
  - `PUT    /users/{id}` — 更新
  - `DELETE /users/{id}` — 删除
- [x] MySQL 数据持久化（自动建表 `ddl-auto=update`）
- [x] **构造器注入**作为依赖注入方式（遵循 Spring 官方推荐，`private final` + 构造器）

### 待完成 📋

- [ ] DTO 层引入（目前 Entity 直接暴露给 Controller）
- [ ] 全局异常处理（`@ControllerAdvice`，替代 `RuntimeException` 裸抛）
- [ ] 参数校验（`@Valid` / `@NotBlank` 等 Bean Validation）
- [ ] 前端页面（Thymeleaf）
- [ ] 用户认证与授权（Spring Security）
- [ ] 单元测试覆盖

---

## 项目结构

```
src/main/java/com/share/spring_boot_demo1/
├── SpringBootDemo1Application.java    ← 启动入口，@SpringBootApplication
├── entity/
│   └── User.java                      ← @Entity，对应 MySQL users 表
├── repository/
│   └── UserRepository.java            ← 接口，继承 JpaRepository<User, Long>
├── Service/                           ← ⚠️ 注意：大写 S，非主流约定
│   └── UserService.java               ← @Service，业务逻辑层
└── Controller/                        ← ⚠️ 注意：大写 C，非主流约定
    └── UserController.java            ← @RestController，接待 HTTP 请求

src/main/resources/
├── application.properties             ← MySQL 连接 + JPA 配置
├── static/                            ← 静态资源（空）
└── templates/                         ← 模板（空，Thymeleaf 预留）
```

> **⚠️ 目录命名问题：** 包路径中 `Service` 和 `Controller` 用了大写首字母，不符合 Java 包名全小写的惯例。这是早期手误，Codex 接手后建议在合适的时机统一重命名为小写（`service`、`controller`），并同步修改所有 import 语句。

---

## 代码约定

| 约定 | 说明 |
|------|------|
| 依赖注入 | 一律**构造器注入**，`private final` 字段，不使用 `@Autowired` 字段注入 |
| 层级职责 | Controller 只做请求接待，Service 做业务逻辑，Repository 做数据访问 |
| 实体命名 | `@Entity` 类放在 `entity/` 包，表名用 `@Table` 显式指定 |
| 数据库 | `ddl-auto=update`，生产环境慎用，当前教学阶段可接受 |
| 异常处理 | 目前直接抛 `RuntimeException`，后续需要引入统一异常处理 |

---

## 数据库

- **数据库名：** `demo1`（连接串自带 `createDatabaseIfNotExist=true`，首次启动自动创建）
- **表名：** `users`（由 Hibernate 根据 `@Entity` 自动创建/更新）
- **字段：** `id`（BIGINT, PK, AUTO_INCREMENT）、`name`（VARCHAR, NOT NULL）、`email`（VARCHAR, UNIQUE）
- **密码：** `你的密码`（已脱敏，Codex 需告知用户替换为实际密码）

连接串：
```
jdbc:mysql://localhost:3306/demo1?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8&createDatabaseIfNotExist=true
```

---

## 已知问题

1. **包名大小写：** `Service/` 和 `Controller/` 大写，应统一为小写
2. **未使用的 import：** `UserService.java` 中有遗留的 `AtomicLong`、`Map`、`Collections` 导入（来自早期 Map 存储方案）
3. **方法命名：** `UserService.getByid` 应为 `getById`（驼峰不规范）
4. **构造器末尾多余分号：** `UserService` 构造器后有一个 `;`
5. **无参数校验：** `create` 和 `update` 未校验 `name`/`email` 是否为空
6. **无全局异常处理：** 直接抛 `RuntimeException`，前端收到 500 而非语义化的错误 JSON

---

## 学习路线上下文

这是一个初学者的教学项目，讲解节奏强调"先跑通、再理解、最后规范"。Codex 在接手时应：

- 保持代码增量小、每次只改一个概念
- 每个新概念（注解、设计模式）给出"大白话解释"
- 改动前告知用户要改哪些文件、各自的作用
- 不在非必要情况下引入新的第三方库或复杂抽象
- 用户偏好：**自己手动敲代码**，所以只给出需要创建/修改的文件和代码清单，不代为操作

---

## 下一步建议

1. **清理当前代码**：修正包名大小写、删除无用 import、统一方法命名
2. **引入 DTO**：创建 `dto/` 包，用 `CreateUserRequest` / `UpdateUserRequest` 隔离 Entity 与 Controller
3. **参数校验**：在 DTO 上加 `@NotBlank`、`@Email` 等注解，Controller 方法参数加 `@Valid`
4. **全局异常处理**：创建 `@ControllerAdvice` 类，统一返回错误 JSON
5. **Thymeleaf 前端**：`pom.xml` 引入 `spring-boot-starter-thymeleaf`，做简单的用户列表页面
