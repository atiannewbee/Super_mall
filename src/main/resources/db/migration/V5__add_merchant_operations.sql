CREATE TABLE merchants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchants_code (code),
    CONSTRAINT ck_merchants_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO merchants (code, name, status)
VALUES ('SUPER_MALL', 'SUPER MALL 自营', 'ACTIVE');

CREATE TABLE merchant_users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    token_version INT NOT NULL DEFAULT 0,
    force_password_change BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_users_email (email),
    KEY idx_merchant_users_merchant_status (merchant_id, status, deleted_at),
    CONSTRAINT fk_merchant_users_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id),
    CONSTRAINT ck_merchant_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    CONSTRAINT ck_merchant_users_token_version CHECK (token_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE merchant_user_roles (
    merchant_user_id BIGINT NOT NULL,
    role_code VARCHAR(30) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (merchant_user_id, role_code),
    KEY idx_merchant_user_roles_role (role_code, merchant_user_id),
    CONSTRAINT fk_merchant_user_roles_user
        FOREIGN KEY (merchant_user_id) REFERENCES merchant_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_merchant_user_roles_role
        CHECK (role_code IN ('OWNER', 'OPERATOR', 'WAREHOUSE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE warehouses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    contact_name VARCHAR(50) NULL,
    contact_phone VARCHAR(20) NULL,
    province VARCHAR(50) NULL,
    city VARCHAR(50) NULL,
    district VARCHAR(50) NULL,
    detail VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouses_merchant_code (merchant_id, code),
    KEY idx_warehouses_merchant_status (merchant_id, status),
    CONSTRAINT fk_warehouses_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id),
    CONSTRAINT ck_warehouses_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO warehouses (merchant_id, code, name, status)
SELECT id, 'DEFAULT', 'SUPER MALL 默认仓', 'ACTIVE'
FROM merchants
WHERE code = 'SUPER_MALL';

ALTER TABLE products
    ADD COLUMN merchant_id BIGINT NULL AFTER id;

UPDATE products
SET merchant_id = (SELECT id FROM merchants WHERE code = 'SUPER_MALL')
WHERE merchant_id IS NULL;

ALTER TABLE products
    MODIFY COLUMN merchant_id BIGINT NOT NULL,
    ADD KEY idx_products_merchant_status (merchant_id, status, sort_order),
    ADD CONSTRAINT fk_products_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id);

ALTER TABLE orders
    ADD COLUMN merchant_id BIGINT NULL AFTER id;

UPDATE orders
SET merchant_id = (SELECT id FROM merchants WHERE code = 'SUPER_MALL')
WHERE merchant_id IS NULL;

ALTER TABLE orders
    MODIFY COLUMN merchant_id BIGINT NOT NULL,
    ADD KEY idx_orders_merchant_fulfillment_created
        (merchant_id, fulfillment_status, created_at),
    ADD CONSTRAINT fk_orders_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id);

ALTER TABLE shipments
    ADD COLUMN warehouse_id BIGINT NULL AFTER order_id;

UPDATE shipments shipment
JOIN orders order_record ON order_record.id = shipment.order_id
JOIN warehouses warehouse
    ON warehouse.merchant_id = order_record.merchant_id AND warehouse.code = 'DEFAULT'
SET shipment.warehouse_id = warehouse.id
WHERE shipment.warehouse_id IS NULL;

ALTER TABLE shipments
    MODIFY COLUMN warehouse_id BIGINT NOT NULL,
    ADD KEY idx_shipments_warehouse_status (warehouse_id, status, created_at),
    ADD CONSTRAINT fk_shipments_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);

ALTER TABLE order_status_history
    ADD COLUMN merchant_operator_id BIGINT NULL AFTER operator_id,
    ADD KEY idx_order_status_history_merchant_operator (merchant_operator_id, created_at),
    ADD CONSTRAINT fk_order_status_history_merchant_user
        FOREIGN KEY (merchant_operator_id) REFERENCES merchant_users (id) ON DELETE SET NULL,
    DROP CHECK ck_order_status_history_operator,
    ADD CONSTRAINT ck_order_status_history_operator
        CHECK (operator_type IN ('SYSTEM', 'USER', 'MERCHANT', 'ADMIN', 'AGENT'));

ALTER TABLE inventory_transactions
    ADD COLUMN merchant_created_by BIGINT NULL AFTER created_by,
    ADD KEY idx_inventory_transactions_merchant_user (merchant_created_by, created_at),
    ADD CONSTRAINT fk_inventory_transactions_merchant_user
        FOREIGN KEY (merchant_created_by) REFERENCES merchant_users (id) ON DELETE SET NULL;

CREATE TABLE merchant_operation_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    merchant_user_id BIGINT NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id VARCHAR(80) NOT NULL,
    request_id VARCHAR(64) NULL,
    ip_address VARCHAR(45) NULL,
    detail JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_merchant_operation_logs_resource
        (merchant_id, resource_type, resource_id, created_at),
    KEY idx_merchant_operation_logs_actor (merchant_user_id, created_at),
    KEY idx_merchant_operation_logs_request (request_id),
    CONSTRAINT fk_merchant_operation_logs_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants (id),
    CONSTRAINT fk_merchant_operation_logs_user
        FOREIGN KEY (merchant_user_id) REFERENCES merchant_users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
