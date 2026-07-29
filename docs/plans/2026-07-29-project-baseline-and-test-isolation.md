# Super Mall 三端基线与测试隔离执行计划

## 目标

把消费者商城、商家运营端和 Spring Boot 后端纳入同一个 Git 仓库，并让自动化测试不再读写日常开发数据库，形成可以回滚、复现和继续部署的项目基线。

## 目录决策

后端暂时保留在仓库根目录，避免无价值地搬动 Maven Wrapper、Flyway 文档和现有启动脚本。

```text
Super_mall/
├─ src/                         Spring Boot 后端
├─ pom.xml
├─ apps/
│  ├─ storefront/              Vue 3 消费者商城
│  └─ merchant/                Vue 3 商家运营端
└─ docs/
```

## Step 1：接入远程基线并建立工作分支

- 输出：本地历史以 `origin/main` 为父提交，工作分支为 `codex/project-baseline`。
- 验证：`git log` 能看到远程初始提交，所有现有源码仍保留。

## Step 2：迁入两个 Vue 项目

- 输出：`apps/storefront` 与 `apps/merchant` 包含源码、公开资源、锁文件、环境模板和各自 README。
- 排除：`node_modules`、`dist`、运行日志和真实 `.env`。
- 验证：新目录文件与原目录一致，两个项目都能安装、测试并构建。

## Step 3：隔离后端测试数据库

- 输出：测试配置固定使用 `super_mall_test`，日常开发继续使用 `super_mall`。
- 原则：不清空、不复用生产或开发数据库；Flyway 自动初始化测试库。
- 验证：后端 26 项测试全部通过，开发库中的联调商品不再影响断言。

## Step 4：补齐仓库说明和忽略规则

- 输出：根 README 说明三端目录、启动方式、测试方式；忽略所有构建产物、依赖目录、日志和本地密钥。
- 验证：`git status` 中不出现密钥、`node_modules`、`dist` 或日志。

## Step 5：形成首个可交付提交

- 输出：在 `codex/project-baseline` 生成经过测试的基线提交并推送到 GitHub。
- 验证：提交包含三端源码，远程分支可见，三端测试与构建结果有记录。

## 后续批次

完成本批次后再依次推进：

1. 商家售后与退款处理；
2. 多 SKU、图片上传和完整商品后台；
3. 服务器部署、HTTPS、备份和 CI；
4. 支付宝真实沙箱联调；
5. Python AI 客服、运营 Agent 与数据中台。
