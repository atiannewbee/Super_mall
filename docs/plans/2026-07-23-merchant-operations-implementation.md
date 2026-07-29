# Merchant Operations Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a separately authenticated merchant portal that lets SUPER MALL staff view paid orders, start picking, create shipments, and inspect inventory with complete authorization and audit coverage.

**Architecture:** A new Vue3 merchant application calls `/api/merchant/**` on the existing Spring Boot modular monolith. Merchant identities use their own JWT key and audience; MySQL remains the transactional source of truth. Existing products and orders are assigned to one seeded self-operated merchant.

**Tech Stack:** Java 17, Spring Boot 4.1, Spring Security OAuth2 Resource Server, JDBC transactions, MySQL/Flyway, Vue 3, Vue Router, Vite, Vitest.

---

### Task 1: Merchant schema and default tenant

**Files:**
- Create: `src/main/resources/db/migration/V5__add_merchant_operations.sql`
- Modify: `src/test/java/com/share/spring_boot_demo1/DatabaseSchemaIntegrationTests.java`

**Steps:**

1. Add failing schema assertions for `merchants`, `merchant_users`, `merchant_user_roles`, `warehouses`, and `merchant_operation_logs`.
2. Add assertions that products and orders have non-null `merchant_id` values and that one default merchant and warehouse exist.
3. Run `.\mvnw.cmd -Dtest=DatabaseSchemaIntegrationTests test` and confirm failure before migration.
4. Create V5 with foreign keys, unique indexes, fixed role/status checks, existing-data backfill, merchant audit references, and default merchant/warehouse rows.
5. Re-run the schema test and expect success.

### Task 2: Merchant authentication and security isolation

**Files:**
- Create: `security/MerchantSecurityProperties.java`
- Create: `security/CurrentMerchant.java`
- Create: `security/MerchantJwtTokenService.java`
- Create: `service/MerchantAuthService.java`
- Create: `controller/merchant/MerchantAuthController.java`
- Create: merchant login/profile DTOs
- Modify: `security/SecurityConfig.java`
- Modify: `application.properties`
- Modify: `.env.properties.example`
- Test: `MerchantSecurityIntegrationTests.java`

**Steps:**

1. Add tests proving merchant login works, merchant passwords are BCrypt hashes, locked merchant users are rejected, and consumer tokens cannot access merchant APIs.
2. Add an ordered `/api/merchant/**` filter chain with a dedicated decoder and active-account validator.
3. Issue merchant tokens with `merchant_id`, role scopes, a separate issuer/audience, and short expiry.
4. Bootstrap the first merchant owner only from environment credentials when no owner exists.
5. Run the security test and then the full existing security suite.

### Task 3: Merchant fulfillment and inventory API

**Files:**
- Create: `service/MerchantOperationsService.java`
- Create: `controller/merchant/MerchantOperationsController.java`
- Create: merchant dashboard, order, inventory, picking, and shipping DTOs
- Test: `MerchantOperationsIntegrationTests.java`

**Steps:**

1. Add an integration flow: consumer creates and pays an order; merchant sees it; warehouse user starts picking; warehouse user ships it; consumer sees tracking information.
2. Add negative cases for unpaid orders, duplicate picking, duplicate shipping, invalid tracking input, cross-merchant access, and insufficient roles.
3. Implement dashboard counters and paginated merchant-order queries scoped by merchant ID.
4. Implement transactional `UNFULFILLED → PICKING`.
5. Implement transactional shipment creation and `PROCESSING/PICKING → SHIPPED`.
6. Insert order history, shipment event, and merchant operation log rows for every mutation.
7. Add read-only inventory search with low-stock filtering.

### Task 4: Independent Vue3 merchant portal

**Files:**
- Create project: `D:/Files/super_mall_merchant_frontend`
- Create: login, dashboard, orders, order detail, and inventory views
- Create: API, session, router, components, styles, tests, README, and `.env.example`

**Steps:**

1. Scaffold Vue 3 + Vite without copying consumer application state.
2. Store merchant JWT under a separate local-storage key and protect merchant routes.
3. Build the dashboard and order queue.
4. Build order detail actions for starting picking and creating a shipment.
5. Build inventory search and low-stock display.
6. Add loading, empty, conflict, unauthorized, and retry states.
7. Add component/store tests for login, role visibility, picking, and shipping.

### Task 5: End-to-end verification

**Steps:**

1. Run `.\mvnw.cmd test`; all existing and merchant tests must pass.
2. Run consumer frontend tests/build to prove compatibility.
3. Run merchant frontend tests/build.
4. Start both applications and verify login → paid order → picking → shipping → consumer tracking in a browser.
5. Review screenshots for desktop and narrow layouts before delivery.

No Git commit is included because the current backend worktree contains unrelated uncommitted changes and the user did not request commits.
