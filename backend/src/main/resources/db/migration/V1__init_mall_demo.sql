CREATE TABLE IF NOT EXISTS user_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id VARCHAR(64) NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    product_image VARCHAR(512) DEFAULT NULL,
    quantity INT NOT NULL,
    fiat_currency VARCHAR(16) NOT NULL,
    fiat_amount DECIMAL(18, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_order_order_id (order_id),
    KEY idx_user_order_status (status),
    KEY idx_user_order_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pay_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id VARCHAR(64) NOT NULL,
    payment_id VARCHAR(64) NOT NULL,
    checkout_url VARCHAR(512) NOT NULL,
    return_url VARCHAR(512) DEFAULT NULL,
    api_idempotency_key VARCHAR(128) DEFAULT NULL,
    fiat_currency VARCHAR(16) NOT NULL,
    fiat_amount DECIMAL(18, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    raw_create_resp TEXT,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pay_order_order_id (order_id),
    UNIQUE KEY uk_pay_order_payment_id (payment_id),
    KEY idx_pay_order_status (status),
    KEY idx_pay_order_idempotency (api_idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS webhook_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    webhook_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) DEFAULT NULL,
    payment_id VARCHAR(64) DEFAULT NULL,
    order_id VARCHAR(64) DEFAULT NULL,
    signature VARCHAR(256) DEFAULT NULL,
    retry_count INT DEFAULT 0,
    headers_json TEXT,
    raw_body TEXT,
    processed TINYINT NOT NULL DEFAULT 0,
    process_error TEXT,
    received_at DATETIME(3) NOT NULL,
    processed_at DATETIME(3) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_webhook_log_webhook_id (webhook_id),
    KEY idx_webhook_log_payment_id (payment_id),
    KEY idx_webhook_log_event_type (event_type),
    KEY idx_webhook_log_processed (processed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
