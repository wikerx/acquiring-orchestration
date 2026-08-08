-- 收银台 24 小时付款期限与商户回调密文快照迁移。
-- 支持重复执行；密钥只存在配置中心，数据库仅保存 AES-GCM 密文。

SET @schema_name = DATABASE();

SET @table_exists = (
    SELECT COUNT(1)
    FROM information_schema.tables
    WHERE table_schema = @schema_name
      AND table_name = 'payment_checkout_session'
);

SET @column_exists = (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'payment_checkout_session'
      AND column_name = 'merchant_notify_url_ciphertext'
);

SET @ddl = IF(@table_exists > 0 AND @column_exists = 0,
    'ALTER TABLE payment_checkout_session ADD COLUMN merchant_notify_url_ciphertext VARCHAR(1024) NULL COMMENT ''商户通知地址 AES-GCM 密文'' AFTER merchant_notify_url_hash',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @payer_column_exists = (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'payment_checkout_session'
      AND column_name = 'payer_info_ciphertext'
);

SET @payer_ddl = IF(@table_exists > 0 AND @payer_column_exists = 0,
    'ALTER TABLE payment_checkout_session ADD COLUMN payer_info_ciphertext TEXT NULL COMMENT ''付款人预填信息 AES-GCM 密文'' AFTER merchant_notify_url_ciphertext',
    'SELECT 1');
PREPARE stmt FROM @payer_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @billing_column_exists = (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'payment_checkout_session'
      AND column_name = 'billing_info_ciphertext'
);

SET @billing_ddl = IF(@table_exists > 0 AND @billing_column_exists = 0,
    'ALTER TABLE payment_checkout_session ADD COLUMN billing_info_ciphertext TEXT NULL COMMENT ''账单预填信息 AES-GCM 密文'' AFTER payer_info_ciphertext',
    'SELECT 1');
PREPARE stmt FROM @billing_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'payment_checkout_session'
      AND index_name = 'idx_checkout_expire_scan'
);

SET @index_ddl = IF(@table_exists > 0 AND @index_exists = 0,
    'CREATE INDEX idx_checkout_expire_scan ON payment_checkout_session (checkout_status, expire_time, deleted)',
    'SELECT 1');
PREPARE stmt FROM @index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
