# Simulated Payment Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Give a signed-in customer a visible, end-to-end mock Alipay or WeChat payment flow that creates a payment record and moves an order from unpaid to processing without contacting a real provider.

**Architecture:** Keep the existing real Alipay adapter untouched. The Vue application defaults to `mock` payment mode and calls the existing authenticated `/payments/sandbox` endpoint only after the customer explicitly confirms payment. Spring Boot remains the source of truth for amount, ownership, inventory, payment status, and order history; `PAYMENT_SANDBOX_ENABLED` continues to protect the endpoint.

**Tech Stack:** Vue 3, Vue Router, Vite environment variables, Vitest, Spring Boot, Spring Security, JDBC transactions, MySQL, MockMvc.

---

### Task 1: Protect the mock-payment client contract

**Files:**
- Modify: `D:/Files/super_mall_frontend/src/composables/useCommerce.spec.js`
- Verify: `D:/Files/super_mall_frontend/src/composables/useCommerce.js`

**Step 1:** Add a Vitest case that selects WeChat, posts only `{ channel: "WECHAT_PAY" }` to the sandbox endpoint, reloads the order, and observes `paid/processing`.

**Step 2:** Run `npm run test:run -- src/composables/useCommerce.spec.js`.

**Step 3:** Keep the existing `markOrderPaid` implementation if the contract passes; otherwise make the smallest implementation change required.

### Task 2: Add an explicit mock checkout and payment screen

**Files:**
- Modify: `D:/Files/super_mall_frontend/src/views/CheckoutView.vue`
- Modify: `D:/Files/super_mall_frontend/src/views/OrderResultView.vue`
- Modify: `D:/Files/super_mall_frontend/src/styles/commerce.css`
- Modify: `D:/Files/super_mall_frontend/.env.example`

**Step 1:** Enable mock Alipay and mock WeChat choices at checkout and label them as non-charging test methods.

**Step 2:** In `mock` mode, stop the result page from creating a real provider payment automatically.

**Step 3:** Show the selected method, payable server amount, a clear simulation warning, and an explicit “confirm mock payment” button.

**Step 4:** On confirmation, call `markOrderPaid`, reload the order, and render the paid state. Preserve the current redirect-and-poll behavior when `VITE_PAYMENT_MODE=alipay`.

### Task 3: Enable and document local simulation safely

**Files:**
- Modify: `D:/Files/Spring_boot_Demo1/.env.properties`
- Modify: `D:/Files/Spring_boot_Demo1/.env.properties.example`
- Modify: `D:/Files/Spring_boot_Demo1/README.md`
- Modify: `D:/Files/super_mall_frontend/README.md`

**Step 1:** Enable `PAYMENT_SANDBOX_ENABLED=true` only in the ignored local environment file and development example.

**Step 2:** Document that production must set the backend flag to `false`, and that the frontend switches to the real flow with `VITE_PAYMENT_MODE=alipay`.

### Task 4: Verify the complete flow

**Files:**
- Test: `D:/Files/Spring_boot_Demo1/src/test/java/com/share/spring_boot_demo1/CommerceFlowIntegrationTests.java`
- Test: `D:/Files/super_mall_frontend/src/composables/useCommerce.spec.js`

**Step 1:** Run `.\mvnw.cmd test` and expect all backend tests to pass, including sandbox payment inventory transitions.

**Step 2:** Run `npm run test:run` and expect all frontend tests to pass.

**Step 3:** Run `npm run build` and verify the production bundle succeeds.

No commit is included because the current backend worktree contains unrelated uncommitted work and the user did not request a commit.
