# SUPER MALL Vue Frontend Design

## Product direction

SUPER MALL is a responsive consumer-electronics storefront. The first release focuses on physical products and a familiar mainstream e-commerce journey: discover, search, compare, choose a SKU, add to cart, and preview checkout. The visual direction combines the information clarity of a large marketplace with the restraint of a premium technology store.

## Experience and visual system

- Warm neutral page background, white merchandise surfaces, graphite text, electric-blue primary actions, and restrained orange-red promotion accents.
- A compact service strip and sticky commerce header provide search, category navigation, account entry, and cart access.
- The first viewport combines a large editorial hero with smaller campaign cards instead of a generic full-width carousel.
- Product sections cover category shortcuts, limited offers, new releases, and recommendations.
- Responsive behavior preserves search, cart, product discovery, and customer-service access on mobile.

## Components and data flow

Vue components are split by responsibility: shell/header, hero, categories, product sections, product cards, product-detail dialog, cart drawer, toast feedback, and Agent customer-service panel. Mock catalog data is isolated from components and shaped like the later Spring Boot API response. Cart state is centralized in a composable and persisted in localStorage.

The prototype handles empty search results, unavailable SKUs, zero-stock products, empty carts, and invalid quantities. Later, mock reads will become `GET /api/products` calls and cart/order actions will become authenticated REST requests without redesigning the component tree.

## Validation

The release is complete when the production build passes, the page works at desktop and mobile widths, keyboard users can operate dialogs and drawers, product filtering works, cart totals are correct, and cart state survives refresh.
