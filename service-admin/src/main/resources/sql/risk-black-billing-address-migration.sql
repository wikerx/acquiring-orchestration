-- 账单地址黑名单字段和索引收敛。
-- 账单地址按明文展示值和哈希匹配，不需要国家、区间、卡品牌字段。

SET @schema_name = DATABASE();

UPDATE risk_black_billing_address
SET merchant_id = ''
WHERE merchant_id IS NULL;

SET @duplicate_count = (
    SELECT COUNT(1)
    FROM (
        SELECT merchant_scope, COALESCE(merchant_id, '') merchant_id, match_value_hash, deleted
        FROM risk_black_billing_address
        WHERE deleted = 0
        GROUP BY merchant_scope, COALESCE(merchant_id, ''), match_value_hash, deleted
        HAVING COUNT(1) > 1
    ) duplicate_rows
);

DROP PROCEDURE IF EXISTS assert_risk_black_billing_address_unique;
DELIMITER $$
CREATE PROCEDURE assert_risk_black_billing_address_unique()
BEGIN
    IF @duplicate_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'risk_black_billing_address has duplicate active records; clean duplicates before creating unique index';
    END IF;
END$$
DELIMITER ;

CALL assert_risk_black_billing_address_unique();
DROP PROCEDURE IF EXISTS assert_risk_black_billing_address_unique;

SET @old_indexes = 'idx_risk_aml_card_hash,idx_risk_aml_card_merchant,idx_risk_aml_card_time,idx_risk_black_billing_address_uniq_lookup,idx_risk_black_billing_address_range_num,uk_black_billing_address_scope_hash_deleted,idx_black_billing_address_trade_lookup,idx_black_billing_address_merchant_time,idx_black_billing_address_time';

SET @drop_index_sql = (
    SELECT GROUP_CONCAT(CONCAT('DROP INDEX ', INDEX_NAME) SEPARATOR ', ')
    FROM (
        SELECT DISTINCT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'risk_black_billing_address'
          AND FIND_IN_SET(INDEX_NAME, @old_indexes) > 0
    ) matched_indexes
);

SET @sql = IF(@drop_index_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE risk_black_billing_address ', @drop_index_sql));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_columns = 'match_value_start,match_value_end,match_value_start_number,match_value_end_number,card_brand,country_alpha2,country_alpha3,country_numeric';

SET @drop_column_sql = (
    SELECT GROUP_CONCAT(CONCAT('DROP COLUMN ', COLUMN_NAME) SEPARATOR ', ')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'risk_black_billing_address'
      AND FIND_IN_SET(COLUMN_NAME, @drop_columns) > 0
);

SET @sql = IF(@drop_column_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE risk_black_billing_address ', @drop_column_sql));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE risk_black_billing_address
    MODIFY merchant_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
    MODIFY match_value_masked VARCHAR(255) NOT NULL COMMENT '账单地址明文展示值',
    MODIFY match_value_hash VARCHAR(128) NOT NULL COMMENT '账单地址归一化哈希，用于交易检索和重复校验',
    MODIFY match_value_cipher VARCHAR(1024) NULL COMMENT '账单地址明文展示，默认不加密存储',
    MODIFY risk_level VARCHAR(32) NOT NULL DEFAULT 'HIGH' COMMENT '风险等级';

CREATE UNIQUE INDEX uk_black_billing_address_scope_hash_deleted
    ON risk_black_billing_address (merchant_scope, merchant_id, match_value_hash, deleted);

CREATE INDEX idx_black_billing_address_trade_lookup
    ON risk_black_billing_address (match_value_hash, merchant_scope, merchant_id, status, deleted, effective_time, expire_time);

CREATE INDEX idx_black_billing_address_merchant_time
    ON risk_black_billing_address (merchant_scope, merchant_id, update_time, id);

CREATE INDEX idx_black_billing_address_time
    ON risk_black_billing_address (update_time, id);
