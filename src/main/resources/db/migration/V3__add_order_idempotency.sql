ALTER TABLE orders
    ADD COLUMN idempotency_key VARCHAR(64) NULL AFTER user_id,
    ADD UNIQUE KEY uk_orders_user_idempotency (user_id, idempotency_key);
