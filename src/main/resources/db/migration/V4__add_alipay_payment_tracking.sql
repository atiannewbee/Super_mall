ALTER TABLE payments
    ADD COLUMN expires_at DATETIME(3) NULL AFTER failure_message,
    ADD COLUMN last_queried_at DATETIME(3) NULL AFTER expires_at;

CREATE TABLE payment_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    channel VARCHAR(30) NOT NULL,
    notification_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    provider_trade_no VARCHAR(100) NULL,
    payload_hash CHAR(64) NOT NULL,
    received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_notifications_channel_event (channel, notification_id),
    KEY idx_payment_notifications_payment_time (payment_id, received_at),
    CONSTRAINT fk_payment_notifications_payment
        FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
