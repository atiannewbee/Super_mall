# SUPER MALL Vue Frontend Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a polished, responsive Vue 3 storefront prototype for physical consumer-electronics products.

**Architecture:** Use a Vite-powered Vue 3 single-page application with small presentational components, isolated mock catalog data, and a cart composable backed by localStorage. Keep the data contract API-shaped so Spring Boot and MySQL can replace mock storage later without restructuring the UI.

**Tech Stack:** Vue 3, Vite, JavaScript, CSS, Vitest, Vue Test Utils

---

### Task 1: Scaffold and baseline

**Files:**
- Create: `package.json`
- Create: `vite.config.js`
- Create: `index.html`
- Create: `src/main.js`
- Create: `src/App.vue`

1. Scaffold a minimal Vue 3/Vite application in the empty project directory.
2. Install dependencies with npm and keep the generated lockfile.
3. Add `test` and `test:run` scripts using Vitest.
4. Run `npm run build`; expect a successful Vite production build.

### Task 2: Catalog contract and cart state

**Files:**
- Create: `src/data/catalog.js`
- Create: `src/composables/useCart.js`
- Create: `src/composables/useCatalog.js`
- Test: `src/composables/useCart.spec.js`

1. Write tests for adding the same SKU, changing quantity, removing an item, total calculation, and localStorage hydration.
2. Run the tests and verify they fail before the composable exists.
3. Implement the smallest cart and catalog state that satisfies the tests.
4. Run `npm run test:run`; expect all tests to pass.

### Task 3: Storefront shell and discovery

**Files:**
- Create: `src/components/SiteHeader.vue`
- Create: `src/components/HeroSection.vue`
- Create: `src/components/CategoryRail.vue`
- Create: `src/components/ProductSection.vue`
- Create: `src/components/ProductCard.vue`
- Modify: `src/App.vue`

1. Build the service strip, sticky search header, navigation, hero campaigns, category shortcuts, and product grids.
2. Connect search and category filters to the catalog composable.
3. Include meaningful empty-result feedback and reset behavior.
4. Verify product discovery manually and with a production build.

### Task 4: Product and cart purchase flow

**Files:**
- Create: `src/components/ProductDialog.vue`
- Create: `src/components/CartDrawer.vue`
- Create: `src/components/AppToast.vue`
- Modify: `src/App.vue`

1. Add product quick view, image selection, SKU selection, stock state, and add-to-cart behavior.
2. Add cart quantity controls, removal, subtotal, delivery threshold, and checkout preview.
3. Trap focus appropriately, close overlays with Escape, and restore focus to the opener.
4. Run tests and production build.

### Task 5: Agent customer-service preview

**Files:**
- Create: `src/components/AgentPanel.vue`
- Modify: `src/App.vue`

1. Add a floating customer-service entry and a conversational panel.
2. Implement safe scripted intents for product recommendations, delivery, returns, and order lookup preview.
3. Clearly label the assistant as a prototype and avoid pretending that real order data exists.
4. Verify keyboard and mobile interactions.

### Task 6: Visual system and final verification

**Files:**
- Create: `src/styles/base.css`
- Create: `src/styles/store.css`
- Modify: all Vue components as needed

1. Implement the responsive typography, color tokens, spacing, motion, focus states, and reduced-motion fallback.
2. Run `npm run test:run` and `npm run build`; both must pass.
3. Start the Vite preview and visually inspect desktop and mobile layouts.
4. Record any remaining backend/API assumptions for the MySQL design phase.
