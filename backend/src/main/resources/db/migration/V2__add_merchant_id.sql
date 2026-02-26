-- Add merchant_id column to user_order table
ALTER TABLE user_order ADD COLUMN merchant_id VARCHAR(64) DEFAULT 'merchant_a' NOT NULL AFTER order_id;
ALTER TABLE user_order ADD INDEX idx_user_order_merchant_id (merchant_id);

-- Add merchant_id column to pay_order table
ALTER TABLE pay_order ADD COLUMN merchant_id VARCHAR(64) DEFAULT 'merchant_a' NOT NULL AFTER order_id;
ALTER TABLE pay_order ADD INDEX idx_pay_order_merchant_id (merchant_id);
