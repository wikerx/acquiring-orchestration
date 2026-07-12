-- 高风险区域黑名单字段和索引收敛。
-- 目标：区域匹配统一使用国家 Alpha-3 + 州省名称 + 城市名称，移除区域表中的冗余编码字段。

SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_region' AND INDEX_NAME = 'idx_black_region_country'
        ),
        'DROP INDEX idx_black_region_country ON risk_black_region',
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
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_region' AND INDEX_NAME = 'idx_black_region_duplicate'
        ),
        'DROP INDEX idx_black_region_duplicate ON risk_black_region',
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
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_region' AND INDEX_NAME = 'uk_black_region_scope_area'
        ),
        'DROP INDEX uk_black_region_scope_area ON risk_black_region',
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
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_region' AND INDEX_NAME = 'idx_black_region_trade_lookup'
        ),
        'DROP INDEX idx_black_region_trade_lookup ON risk_black_region',
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
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_region' AND COLUMN_NAME = 'country_alpha2'
        ),
        'UPDATE risk_black_region r JOIN base_iso_country c ON c.alpha2_code = r.country_alpha2 AND c.deleted = 0 SET r.country_alpha3 = c.alpha3_code WHERE (r.country_alpha3 IS NULL OR r.country_alpha3 = '''')',
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
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_region' AND COLUMN_NAME = 'match_source'
        ),
        'ALTER TABLE risk_black_region DROP COLUMN match_source',
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
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_region' AND COLUMN_NAME = 'country_name'
        ),
        'ALTER TABLE risk_black_region DROP COLUMN country_name',
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
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_region' AND COLUMN_NAME = 'country_alpha2'
        ),
        'ALTER TABLE risk_black_region DROP COLUMN country_alpha2',
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
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_region' AND COLUMN_NAME = 'country_numeric'
        ),
        'ALTER TABLE risk_black_region DROP COLUMN country_numeric',
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
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_region' AND COLUMN_NAME = 'state_province_code'
        ),
        'ALTER TABLE risk_black_region DROP COLUMN state_province_code',
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
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_region' AND COLUMN_NAME = 'city_code'
        ),
        'ALTER TABLE risk_black_region DROP COLUMN city_code',
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
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_region' AND COLUMN_NAME = 'region_path_code'
        ),
        'ALTER TABLE risk_black_region DROP COLUMN region_path_code',
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
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_region' AND COLUMN_NAME = 'region_path_name'
        ),
        'ALTER TABLE risk_black_region DROP COLUMN region_path_name',
        'SELECT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE risk_black_region
    MODIFY country_alpha3 VARCHAR(3) NOT NULL COMMENT '国家或地区 Alpha-3 编码';

UPDATE risk_black_region
SET state_province_name = ''
WHERE state_province_name IS NULL;

UPDATE risk_black_region
SET city_name = ''
WHERE city_name IS NULL;

UPDATE risk_black_region
SET merchant_id = ''
WHERE merchant_id IS NULL;

ALTER TABLE risk_black_region
    MODIFY merchant_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
    MODIFY state_province_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '州省名称',
    MODIFY city_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '城市名称';

SET @duplicate_count = (
    SELECT COUNT(1)
    FROM (
        SELECT merchant_scope, merchant_id, region_match_level, country_alpha3, state_province_name, city_name, deleted
        FROM risk_black_region
        WHERE deleted = 0
        GROUP BY merchant_scope, merchant_id, region_match_level, country_alpha3, state_province_name, city_name, deleted
        HAVING COUNT(1) > 1
    ) duplicate_rows
);

SET @sql = IF(
    @duplicate_count > 0,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''risk_black_region has duplicate active region records; clean duplicates before creating unique index''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_region' AND INDEX_NAME = 'uk_black_region_scope_area'
        ),
        'SELECT 1',
        'CREATE UNIQUE INDEX uk_black_region_scope_area ON risk_black_region (merchant_scope, merchant_id, region_match_level, country_alpha3, state_province_name, city_name, deleted)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_black_region' AND INDEX_NAME = 'idx_black_region_trade_lookup'
        ),
        'SELECT 1',
        'CREATE INDEX idx_black_region_trade_lookup ON risk_black_region (merchant_scope, merchant_id, region_match_level, country_alpha3, state_province_name, city_name, status, deleted, effective_time, expire_time)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
