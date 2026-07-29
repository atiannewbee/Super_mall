CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    email VARCHAR(100) NULL,
    phone VARCHAR(20) NULL,
    password_hash VARCHAR(255) NULL,
    avatar_url VARCHAR(500) NULL,
    birthday DATE NULL,
    gender VARCHAR(16) NOT NULL DEFAULT 'UNSPECIFIED',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_phone (phone),
    KEY idx_users_status_created (status, created_at),
    CONSTRAINT ck_users_identity CHECK (email IS NOT NULL OR phone IS NOT NULL),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_addresses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    recipient_name VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    province VARCHAR(50) NOT NULL,
    city VARCHAR(50) NOT NULL,
    district VARCHAR(50) NOT NULL,
    detail VARCHAR(255) NOT NULL,
    postal_code VARCHAR(20) NULL,
    tag VARCHAR(20) NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_addresses_user_default (user_id, is_default, deleted_at),
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NULL,
    slug VARCHAR(80) NOT NULL,
    name VARCHAR(80) NOT NULL,
    icon VARCHAR(100) NULL,
    description VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_slug (slug),
    KEY idx_categories_parent_sort (parent_id, sort_order),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id) ON DELETE SET NULL,
    CONSTRAINT ck_categories_status CHECK (status IN ('ACTIVE', 'HIDDEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE brands (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    logo_url VARCHAR(500) NULL,
    description VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_brands_code (code),
    KEY idx_brands_status_sort (status, sort_order),
    CONSTRAINT ck_brands_status CHECK (status IN ('ACTIVE', 'HIDDEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    brand_id BIGINT NULL,
    slug VARCHAR(120) NOT NULL,
    name VARCHAR(160) NOT NULL,
    tagline VARCHAR(255) NULL,
    description TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    base_price DECIMAL(12, 2) NOT NULL,
    original_price DECIMAL(12, 2) NULL,
    badge VARCHAR(50) NULL,
    accent_color CHAR(7) NULL,
    rating DECIMAL(2, 1) NOT NULL DEFAULT 0.0,
    review_count BIGINT NOT NULL DEFAULT 0,
    sold_count BIGINT NOT NULL DEFAULT 0,
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    is_new BOOLEAN NOT NULL DEFAULT FALSE,
    is_deal BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    published_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_products_slug (slug),
    KEY idx_products_category_status_sort (category_id, status, sort_order),
    KEY idx_products_brand_status (brand_id, status),
    KEY idx_products_featured_status (is_featured, status, sort_order),
    KEY idx_products_deal_status (is_deal, status, sort_order),
    KEY idx_products_new_status (is_new, status, sort_order),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands (id) ON DELETE SET NULL,
    CONSTRAINT ck_products_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_products_prices CHECK (base_price >= 0 AND (original_price IS NULL OR original_price >= 0)),
    CONSTRAINT ck_products_rating CHECK (rating >= 0 AND rating <= 5),
    CONSTRAINT ck_products_counts CHECK (review_count >= 0 AND sold_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    alt_text VARCHAR(255) NULL,
    image_type VARCHAR(20) NOT NULL DEFAULT 'GALLERY',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_product_images_product_sort (product_id, sort_order),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT ck_product_images_type CHECK (image_type IN ('COVER', 'GALLERY', 'DETAIL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_features (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    content VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_features_content (product_id, content),
    KEY idx_product_features_product_sort (product_id, sort_order),
    CONSTRAINT fk_product_features_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_attributes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_attributes_name (product_id, name),
    CONSTRAINT fk_product_attributes_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_attribute_values (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attribute_id BIGINT NOT NULL,
    value VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_attribute_values_value (attribute_id, value),
    CONSTRAINT fk_attribute_values_attribute FOREIGN KEY (attribute_id) REFERENCES product_attributes (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_skus (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    sku_code VARCHAR(80) NOT NULL,
    label VARCHAR(255) NOT NULL,
    barcode VARCHAR(80) NULL,
    price DECIMAL(12, 2) NOT NULL,
    original_price DECIMAL(12, 2) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_skus_code (sku_code),
    UNIQUE KEY uk_product_skus_barcode (barcode),
    KEY idx_product_skus_product_status (product_id, status, sort_order),
    CONSTRAINT fk_product_skus_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_product_skus_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_product_skus_prices CHECK (price >= 0 AND (original_price IS NULL OR original_price >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sku_attribute_values (
    sku_id BIGINT NOT NULL,
    attribute_value_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (sku_id, attribute_value_id),
    KEY idx_sku_attribute_values_value (attribute_value_id, sku_id),
    CONSTRAINT fk_sku_attribute_values_sku FOREIGN KEY (sku_id) REFERENCES product_skus (id) ON DELETE CASCADE,
    CONSTRAINT fk_sku_attribute_values_value FOREIGN KEY (attribute_value_id) REFERENCES product_attribute_values (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sku_inventory (
    sku_id BIGINT NOT NULL,
    available_quantity INT NOT NULL DEFAULT 0,
    locked_quantity INT NOT NULL DEFAULT 0,
    sold_quantity BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (sku_id),
    CONSTRAINT fk_sku_inventory_sku FOREIGN KEY (sku_id) REFERENCES product_skus (id) ON DELETE CASCADE,
    CONSTRAINT ck_sku_inventory_quantities CHECK (available_quantity >= 0 AND locked_quantity >= 0 AND sold_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inventory_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sku_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    available_delta INT NOT NULL DEFAULT 0,
    locked_delta INT NOT NULL DEFAULT 0,
    reference_type VARCHAR(30) NULL,
    reference_no VARCHAR(64) NULL,
    note VARCHAR(255) NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_inventory_transactions_sku_time (sku_id, created_at),
    KEY idx_inventory_transactions_reference (reference_type, reference_no),
    CONSTRAINT fk_inventory_transactions_sku FOREIGN KEY (sku_id) REFERENCES product_skus (id),
    CONSTRAINT fk_inventory_transactions_user FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_inventory_transactions_type CHECK (transaction_type IN ('IN', 'OUT', 'LOCK', 'UNLOCK', 'DEDUCT', 'RETURN', 'ADJUST'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE shopping_carts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shopping_carts_user (user_id),
    CONSTRAINT fk_shopping_carts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE shopping_cart_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cart_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_items_cart_sku (cart_id, sku_id),
    KEY idx_cart_items_sku (sku_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES shopping_carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_sku FOREIGN KEY (sku_id) REFERENCES product_skus (id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_favorites (
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, product_id),
    KEY idx_product_favorites_product (product_id, created_at),
    CONSTRAINT fk_product_favorites_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_favorites_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    address_id BIGINT NULL,
    order_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_status VARCHAR(32) NOT NULL DEFAULT 'UNPAID',
    fulfillment_status VARCHAR(32) NOT NULL DEFAULT 'UNFULFILLED',
    after_sale_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
    item_count INT NOT NULL,
    subtotal_amount DECIMAL(12, 2) NOT NULL,
    delivery_fee DECIMAL(12, 2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    payable_amount DECIMAL(12, 2) NOT NULL,
    paid_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    payment_channel VARCHAR(30) NULL,
    buyer_note VARCHAR(255) NULL,
    invoice_required BOOLEAN NOT NULL DEFAULT FALSE,
    recipient_name VARCHAR(50) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    recipient_province VARCHAR(50) NOT NULL,
    recipient_city VARCHAR(50) NOT NULL,
    recipient_district VARCHAR(50) NOT NULL,
    recipient_detail VARCHAR(255) NOT NULL,
    recipient_tag VARCHAR(20) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    paid_at DATETIME(3) NULL,
    shipped_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    cancelled_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_no (order_no),
    KEY idx_orders_user_status_created (user_id, order_status, created_at),
    KEY idx_orders_payment_status_created (payment_status, created_at),
    KEY idx_orders_fulfillment_status_created (fulfillment_status, created_at),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_address FOREIGN KEY (address_id) REFERENCES user_addresses (id) ON DELETE SET NULL,
    CONSTRAINT ck_orders_item_count CHECK (item_count > 0),
    CONSTRAINT ck_orders_amounts CHECK (subtotal_amount >= 0 AND delivery_fee >= 0 AND discount_amount >= 0 AND payable_amount >= 0 AND paid_amount >= 0),
    CONSTRAINT ck_orders_order_status CHECK (order_status IN ('PENDING_PAYMENT', 'PROCESSING', 'SHIPPED', 'COMPLETED', 'CANCELLED', 'AFTER_SALE')),
    CONSTRAINT ck_orders_payment_status CHECK (payment_status IN ('UNPAID', 'PAID', 'PARTIALLY_REFUNDED', 'REFUNDED', 'CLOSED')),
    CONSTRAINT ck_orders_fulfillment_status CHECK (fulfillment_status IN ('UNFULFILLED', 'PICKING', 'SHIPPED', 'DELIVERED', 'RETURNED')),
    CONSTRAINT ck_orders_after_sale_status CHECK (after_sale_status IN ('NONE', 'REQUESTED', 'PROCESSING', 'COMPLETED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NULL,
    sku_id BIGINT NULL,
    product_slug VARCHAR(120) NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    sku_code VARCHAR(80) NOT NULL,
    sku_label VARCHAR(255) NOT NULL,
    image_url VARCHAR(500) NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    quantity INT NOT NULL,
    line_amount DECIMAL(12, 2) NOT NULL,
    after_sale_quantity INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_order_items_order (order_id),
    KEY idx_order_items_product (product_id),
    KEY idx_order_items_sku (sku_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE SET NULL,
    CONSTRAINT fk_order_items_sku FOREIGN KEY (sku_id) REFERENCES product_skus (id) ON DELETE SET NULL,
    CONSTRAINT ck_order_items_values CHECK (unit_price >= 0 AND quantity > 0 AND line_amount >= 0 AND after_sale_quantity >= 0 AND after_sale_quantity <= quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    status_type VARCHAR(30) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    note VARCHAR(255) NULL,
    operator_type VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    operator_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_order_status_history_order_time (order_id, created_at),
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_status_history_user FOREIGN KEY (operator_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_order_status_history_type CHECK (status_type IN ('ORDER', 'PAYMENT', 'FULFILLMENT', 'AFTER_SALE')),
    CONSTRAINT ck_order_status_history_operator CHECK (operator_type IN ('SYSTEM', 'USER', 'ADMIN', 'AGENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_no VARCHAR(40) NOT NULL,
    order_id BIGINT NOT NULL,
    channel VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    amount DECIMAL(12, 2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    provider_trade_no VARCHAR(100) NULL,
    failure_code VARCHAR(50) NULL,
    failure_message VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    paid_at DATETIME(3) NULL,
    closed_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_payment_no (payment_no),
    UNIQUE KEY uk_payments_provider_trade_no (provider_trade_no),
    KEY idx_payments_order_status (order_id, status, created_at),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'CLOSED', 'PARTIALLY_REFUNDED', 'REFUNDED')),
    CONSTRAINT ck_payments_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE shipments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shipment_no VARCHAR(40) NOT NULL,
    order_id BIGINT NOT NULL,
    carrier_code VARCHAR(30) NULL,
    carrier_name VARCHAR(80) NULL,
    tracking_no VARCHAR(100) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    shipped_at DATETIME(3) NULL,
    delivered_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipments_shipment_no (shipment_no),
    UNIQUE KEY uk_shipments_tracking (carrier_code, tracking_no),
    KEY idx_shipments_order_status (order_id, status),
    CONSTRAINT fk_shipments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_shipments_status CHECK (status IN ('PENDING', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED', 'EXCEPTION', 'RETURNED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE shipment_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shipment_id BIGINT NOT NULL,
    event_code VARCHAR(30) NOT NULL,
    description VARCHAR(255) NOT NULL,
    location VARCHAR(100) NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_shipment_events_shipment_time (shipment_id, occurred_at),
    CONSTRAINT fk_shipment_events_shipment FOREIGN KEY (shipment_id) REFERENCES shipments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE after_sale_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    after_sale_no VARCHAR(40) NOT NULL,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    reason_code VARCHAR(50) NULL,
    reason_description VARCHAR(500) NOT NULL,
    requested_amount DECIMAL(12, 2) NOT NULL,
    refunded_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    customer_note VARCHAR(500) NULL,
    admin_note VARCHAR(500) NULL,
    return_carrier VARCHAR(80) NULL,
    return_tracking_no VARCHAR(100) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    approved_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    cancelled_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_after_sale_requests_no (after_sale_no),
    KEY idx_after_sale_user_status_created (user_id, status, created_at),
    KEY idx_after_sale_order (order_id, created_at),
    CONSTRAINT fk_after_sale_requests_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_after_sale_requests_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_after_sale_requests_type CHECK (type IN ('REFUND_ONLY', 'RETURN_REFUND', 'EXCHANGE')),
    CONSTRAINT ck_after_sale_requests_status CHECK (status IN ('REQUESTED', 'REVIEWING', 'APPROVED', 'REJECTED', 'RETURNING', 'REFUNDING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_after_sale_requests_amount CHECK (requested_amount >= 0 AND refunded_amount >= 0 AND refunded_amount <= requested_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE after_sale_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    after_sale_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    requested_amount DECIMAL(12, 2) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_after_sale_items_request_item (after_sale_id, order_item_id),
    KEY idx_after_sale_items_order_item (order_item_id),
    CONSTRAINT fk_after_sale_items_request FOREIGN KEY (after_sale_id) REFERENCES after_sale_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_after_sale_items_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT ck_after_sale_items_values CHECK (quantity > 0 AND requested_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE after_sale_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    after_sale_id BIGINT NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    description VARCHAR(255) NOT NULL,
    operator_type VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    operator_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_after_sale_events_request_time (after_sale_id, created_at),
    CONSTRAINT fk_after_sale_events_request FOREIGN KEY (after_sale_id) REFERENCES after_sale_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_after_sale_events_user FOREIGN KEY (operator_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_after_sale_events_operator CHECK (operator_type IN ('SYSTEM', 'USER', 'ADMIN', 'AGENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
