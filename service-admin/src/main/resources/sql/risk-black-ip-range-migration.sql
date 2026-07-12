-- IP地址/区间黑名单字段和索引收敛。
-- 目标：保留起止IP字符串和ipNumber数值，增加IP版本分流字段，删除国家/地区和卡品牌无效字段。

SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_ip' AND COLUMN_NAME = 'ip_version'
        ),
        'SELECT 1',
        'ALTER TABLE risk_black_ip ADD COLUMN ip_version VARCHAR(8) NULL COMMENT ''IP版本：IPV4、IPV6'' AFTER match_value_end_number'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE risk_black_ip
SET ip_version = CASE
    WHEN match_value_start LIKE '%:%' THEN 'IPV6'
    WHEN match_value_start IS NOT NULL AND match_value_start <> '' THEN 'IPV4'
    ELSE ip_version
END
WHERE deleted = 0 AND (ip_version IS NULL OR ip_version = '');

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_ip' AND COLUMN_NAME = 'card_brand'
        ),
        'ALTER TABLE risk_black_ip DROP COLUMN card_brand',
        'SELECT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_ip' AND COLUMN_NAME = 'country_alpha2'
        ),
        'ALTER TABLE risk_black_ip DROP COLUMN country_alpha2',
        'SELECT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_ip' AND COLUMN_NAME = 'country_alpha3'
        ),
        'ALTER TABLE risk_black_ip DROP COLUMN country_alpha3',
        'SELECT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_ip' AND COLUMN_NAME = 'country_numeric'
        ),
        'ALTER TABLE risk_black_ip DROP COLUMN country_numeric',
        'SELECT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_ip' AND INDEX_NAME = 'idx_risk_black_ip_trade_lookup'
        ),
        'SELECT 1',
        'CREATE INDEX idx_risk_black_ip_trade_lookup ON risk_black_ip (ip_version, status, deleted, merchant_scope, merchant_id, match_value_start_number, match_value_end_number, effective_time, expire_time)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
