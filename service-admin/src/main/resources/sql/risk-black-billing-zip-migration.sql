-- 账单邮编黑名单字段和索引收敛。
-- 账单邮编不依赖国家字段，交易匹配按规范化邮编哈希精确检索。

SET @schema_name = DATABASE();

UPDATE risk_black_billing_zip
SET merchant_id = ''
WHERE merchant_id IS NULL;

UPDATE risk_black_billing_zip
SET match_value_masked = UPPER(TRIM(REGEXP_REPLACE(match_value_masked, '\\s+', ' ')))
WHERE match_value_masked IS NOT NULL;

SET @duplicate_count = (
    SELECT COUNT(1)
    FROM (
        SELECT merchant_scope, COALESCE(merchant_id, '') merchant_id, match_value_hash, deleted
        FROM risk_black_billing_zip
        WHERE deleted = 0
        GROUP BY merchant_scope, COALESCE(merchant_id, ''), match_value_hash, deleted
        HAVING COUNT(1) > 1
    ) duplicate_rows
);

SET @sql = IF(@duplicate_count > 0,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''risk_black_billing_zip has duplicate active records; clean duplicates before creating unique index''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_indexes = 'idx_risk_aml_card_hash,idx_risk_aml_card_merchant,idx_risk_aml_card_time,idx_risk_black_billing_zip_uniq_lookup,idx_risk_black_billing_zip_range_num,uk_black_billing_zip_scope_hash_deleted,idx_black_billing_zip_trade_lookup,idx_black_billing_zip_merchant_time,idx_black_billing_zip_time';

SET @drop_index_sql = (
    SELECT GROUP_CONCAT(CONCAT('DROP INDEX ', INDEX_NAME) SEPARATOR ', ')
    FROM (
        SELECT DISTINCT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'risk_black_billing_zip'
          AND FIND_IN_SET(INDEX_NAME, @old_indexes) > 0
    ) matched_indexes
);

SET @sql = IF(@drop_index_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE risk_black_billing_zip ', @drop_index_sql));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_columns = 'match_value_start,match_value_end,match_value_start_number,match_value_end_number,card_brand,country_alpha2,country_alpha3,country_numeric';

SET @drop_column_sql = (
    SELECT GROUP_CONCAT(CONCAT('DROP COLUMN ', COLUMN_NAME) SEPARATOR ', ')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'risk_black_billing_zip'
      AND FIND_IN_SET(COLUMN_NAME, @drop_columns) > 0
);

SET @sql = IF(@drop_column_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE risk_black_billing_zip ', @drop_column_sql));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE risk_black_billing_zip
    MODIFY merchant_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
    MODIFY match_value_masked VARCHAR(32) NOT NULL COMMENT '账单邮编展示值，按大写和单空格规范化',
    MODIFY match_value_hash VARCHAR(128) NOT NULL COMMENT '账单邮编检索哈希，按去除空格和短横线后的值生成',
    MODIFY match_value_cipher VARCHAR(1024) NULL COMMENT '预留密文字段，账单邮编默认不加密存储',
    MODIFY risk_level VARCHAR(32) NOT NULL DEFAULT 'HIGH' COMMENT '风险等级';

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_billing_zip' AND INDEX_NAME = 'uk_black_billing_zip_scope_hash_deleted'),
    'SELECT 1',
    'CREATE UNIQUE INDEX uk_black_billing_zip_scope_hash_deleted ON risk_black_billing_zip (merchant_scope, merchant_id, match_value_hash, deleted)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_billing_zip' AND INDEX_NAME = 'idx_black_billing_zip_trade_lookup'),
    'SELECT 1',
    'CREATE INDEX idx_black_billing_zip_trade_lookup ON risk_black_billing_zip (match_value_hash, merchant_scope, merchant_id, status, deleted, effective_time, expire_time)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_billing_zip' AND INDEX_NAME = 'idx_black_billing_zip_merchant_time'),
    'SELECT 1',
    'CREATE INDEX idx_black_billing_zip_merchant_time ON risk_black_billing_zip (merchant_scope, merchant_id, update_time, id)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_billing_zip' AND INDEX_NAME = 'idx_black_billing_zip_time'),
    'SELECT 1',
    'CREATE INDEX idx_black_billing_zip_time ON risk_black_billing_zip (update_time, id)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
