-- 收货地址、收货邮编黑名单字段和索引收敛。
-- 收货地址按明文展示值和哈希匹配；收货邮编按规范化邮编哈希精确匹配，均不需要国家、区间、卡品牌字段。

SET @schema_name = DATABASE();

UPDATE risk_black_shipping_address SET merchant_id = '' WHERE merchant_id IS NULL;
UPDATE risk_black_shipping_zip SET merchant_id = '' WHERE merchant_id IS NULL;
UPDATE risk_black_shipping_zip
SET match_value_masked = UPPER(TRIM(REGEXP_REPLACE(match_value_masked, '\\s+', ' ')))
WHERE match_value_masked IS NOT NULL;

SET @duplicate_address_count = (
    SELECT COUNT(1)
    FROM (
        SELECT merchant_scope, COALESCE(merchant_id, '') merchant_id, match_value_hash, deleted
        FROM risk_black_shipping_address
        WHERE deleted = 0
        GROUP BY merchant_scope, COALESCE(merchant_id, ''), match_value_hash, deleted
        HAVING COUNT(1) > 1
    ) duplicate_rows
);

SET @duplicate_zip_count = (
    SELECT COUNT(1)
    FROM (
        SELECT merchant_scope, COALESCE(merchant_id, '') merchant_id, match_value_hash, deleted
        FROM risk_black_shipping_zip
        WHERE deleted = 0
        GROUP BY merchant_scope, COALESCE(merchant_id, ''), match_value_hash, deleted
        HAVING COUNT(1) > 1
    ) duplicate_rows
);

SET @sql = IF(@duplicate_address_count > 0,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''risk_black_shipping_address has duplicate active records; clean duplicates before creating unique index''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(@duplicate_zip_count > 0,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''risk_black_shipping_zip has duplicate active records; clean duplicates before creating unique index''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_address_indexes = 'idx_risk_aml_card_hash,idx_risk_aml_card_merchant,idx_risk_aml_card_time,idx_risk_black_shipping_address_uniq_lookup,idx_risk_black_shipping_address_range_num,uk_black_shipping_address_scope_hash_deleted,idx_black_shipping_address_trade_lookup,idx_black_shipping_address_merchant_time,idx_black_shipping_address_time';
SET @old_zip_indexes = 'idx_risk_aml_card_hash,idx_risk_aml_card_merchant,idx_risk_aml_card_time,idx_risk_black_shipping_zip_uniq_lookup,idx_risk_black_shipping_zip_range_num,uk_black_shipping_zip_scope_hash_deleted,idx_black_shipping_zip_trade_lookup,idx_black_shipping_zip_merchant_time,idx_black_shipping_zip_time';

SET @drop_address_index_sql = (
    SELECT GROUP_CONCAT(CONCAT('DROP INDEX ', INDEX_NAME) SEPARATOR ', ')
    FROM (
        SELECT DISTINCT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'risk_black_shipping_address'
          AND FIND_IN_SET(INDEX_NAME, @old_address_indexes) > 0
    ) matched_indexes
);

SET @sql = IF(@drop_address_index_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE risk_black_shipping_address ', @drop_address_index_sql));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_zip_index_sql = (
    SELECT GROUP_CONCAT(CONCAT('DROP INDEX ', INDEX_NAME) SEPARATOR ', ')
    FROM (
        SELECT DISTINCT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'risk_black_shipping_zip'
          AND FIND_IN_SET(INDEX_NAME, @old_zip_indexes) > 0
    ) matched_indexes
);

SET @sql = IF(@drop_zip_index_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE risk_black_shipping_zip ', @drop_zip_index_sql));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_columns = 'match_value_start,match_value_end,match_value_start_number,match_value_end_number,card_brand,country_alpha2,country_alpha3,country_numeric';

SET @drop_address_column_sql = (
    SELECT GROUP_CONCAT(CONCAT('DROP COLUMN ', COLUMN_NAME) SEPARATOR ', ')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'risk_black_shipping_address'
      AND FIND_IN_SET(COLUMN_NAME, @drop_columns) > 0
);

SET @sql = IF(@drop_address_column_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE risk_black_shipping_address ', @drop_address_column_sql));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_zip_column_sql = (
    SELECT GROUP_CONCAT(CONCAT('DROP COLUMN ', COLUMN_NAME) SEPARATOR ', ')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'risk_black_shipping_zip'
      AND FIND_IN_SET(COLUMN_NAME, @drop_columns) > 0
);

SET @sql = IF(@drop_zip_column_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE risk_black_shipping_zip ', @drop_zip_column_sql));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE risk_black_shipping_address
    MODIFY merchant_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
    MODIFY match_value_masked VARCHAR(255) NOT NULL COMMENT '收货地址明文展示值',
    MODIFY match_value_hash VARCHAR(128) NOT NULL COMMENT '收货地址归一化哈希，用于交易检索和重复校验',
    MODIFY match_value_cipher VARCHAR(1024) NULL COMMENT '收货地址明文展示，默认不加密存储',
    MODIFY risk_level VARCHAR(32) NOT NULL DEFAULT 'HIGH' COMMENT '风险等级';

ALTER TABLE risk_black_shipping_zip
    MODIFY merchant_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
    MODIFY match_value_masked VARCHAR(32) NOT NULL COMMENT '收货邮编展示值，按大写和单空格规范化',
    MODIFY match_value_hash VARCHAR(128) NOT NULL COMMENT '收货邮编检索哈希，按去除空格和短横线后的值生成',
    MODIFY match_value_cipher VARCHAR(1024) NULL COMMENT '预留密文字段，收货邮编默认不加密存储',
    MODIFY risk_level VARCHAR(32) NOT NULL DEFAULT 'HIGH' COMMENT '风险等级';

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_shipping_address' AND INDEX_NAME = 'uk_black_shipping_address_scope_hash_deleted'),
    'SELECT 1',
    'CREATE UNIQUE INDEX uk_black_shipping_address_scope_hash_deleted ON risk_black_shipping_address (merchant_scope, merchant_id, match_value_hash, deleted)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_shipping_address' AND INDEX_NAME = 'idx_black_shipping_address_trade_lookup'),
    'SELECT 1',
    'CREATE INDEX idx_black_shipping_address_trade_lookup ON risk_black_shipping_address (match_value_hash, merchant_scope, merchant_id, status, deleted, effective_time, expire_time)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_shipping_address' AND INDEX_NAME = 'idx_black_shipping_address_merchant_time'),
    'SELECT 1',
    'CREATE INDEX idx_black_shipping_address_merchant_time ON risk_black_shipping_address (merchant_scope, merchant_id, update_time, id)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_shipping_address' AND INDEX_NAME = 'idx_black_shipping_address_time'),
    'SELECT 1',
    'CREATE INDEX idx_black_shipping_address_time ON risk_black_shipping_address (update_time, id)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_shipping_zip' AND INDEX_NAME = 'uk_black_shipping_zip_scope_hash_deleted'),
    'SELECT 1',
    'CREATE UNIQUE INDEX uk_black_shipping_zip_scope_hash_deleted ON risk_black_shipping_zip (merchant_scope, merchant_id, match_value_hash, deleted)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_shipping_zip' AND INDEX_NAME = 'idx_black_shipping_zip_trade_lookup'),
    'SELECT 1',
    'CREATE INDEX idx_black_shipping_zip_trade_lookup ON risk_black_shipping_zip (match_value_hash, merchant_scope, merchant_id, status, deleted, effective_time, expire_time)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_shipping_zip' AND INDEX_NAME = 'idx_black_shipping_zip_merchant_time'),
    'SELECT 1',
    'CREATE INDEX idx_black_shipping_zip_merchant_time ON risk_black_shipping_zip (merchant_scope, merchant_id, update_time, id)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_shipping_zip' AND INDEX_NAME = 'idx_black_shipping_zip_time'),
    'SELECT 1',
    'CREATE INDEX idx_black_shipping_zip_time ON risk_black_shipping_zip (update_time, id)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
