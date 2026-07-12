-- 商户交易限额表结构收敛迁移草案：限额币种固定 USD，金额只保留 2 位小数，并下线无用通用规则字段。
-- 执行前请先备份 risk_rule_merchant_limit，并确认管理端代码已发布到专用 Mapper，不再读写 match_mode、time_window_seconds 等字段。
-- 本脚本不应直接在生产执行；请先运行数据质量检查 SQL，处理历史异常数据后再修改表结构。

-- 数据质量检查：如果有结果，说明历史数据存在真实超过 2 位的小数，必须先由业务确认处理方式。
SELECT id, merchant_id, limit_type, amount_min, amount_max
FROM risk_rule_merchant_limit
WHERE deleted = 0
  AND (
      (amount_min IS NOT NULL AND amount_min <> ROUND(amount_min, 2))
      OR (amount_max IS NOT NULL AND amount_max <> ROUND(amount_max, 2))
  );

UPDATE risk_rule_merchant_limit
SET merchant_id = ''
WHERE merchant_id IS NULL;

UPDATE risk_rule_merchant_limit
SET match_value = ''
WHERE match_value IS NULL;

DROP PROCEDURE IF EXISTS ensure_merchant_limit_currency_column;

DELIMITER $$

CREATE PROCEDURE ensure_merchant_limit_currency_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'risk_rule_merchant_limit'
          AND COLUMN_NAME = 'currency'
    ) THEN
        ALTER TABLE risk_rule_merchant_limit
            ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'USD' COMMENT '限额币种，当前固定USD' AFTER amount_max;
    END IF;
END$$

DELIMITER ;

CALL ensure_merchant_limit_currency_column();

DROP PROCEDURE IF EXISTS ensure_merchant_limit_currency_column;

UPDATE risk_rule_merchant_limit
SET currency = 'USD'
WHERE currency IS NULL OR currency = '';

ALTER TABLE risk_rule_merchant_limit
    MODIFY merchant_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
    MODIFY match_value VARCHAR(512) NOT NULL DEFAULT '' COMMENT '限额场景，可为空字符串；用于区分同一商户下不同交易场景',
    MODIFY limit_type VARCHAR(64) NOT NULL COMMENT '限额类型：SINGLE_MIN、SINGLE_MAX、DAILY、WEEKLY、MONTHLY',
    MODIFY amount_min DECIMAL(18,2) NULL COMMENT '最小金额，单笔最低限额使用，固定USD且保留2位小数',
    MODIFY amount_max DECIMAL(18,2) NULL COMMENT '最大金额，单笔最高、日、周、月限额使用，固定USD且保留2位小数',
    MODIFY currency VARCHAR(3) NOT NULL DEFAULT 'USD' COMMENT '限额币种，当前固定USD';

-- 数据质量检查：如果有结果，说明同一范围、商户、场景、限额类型存在重复，必须先由业务合并。
SELECT merchant_scope, merchant_id, COALESCE(match_value, '') AS match_value, limit_type, COUNT(1) duplicate_count
FROM risk_rule_merchant_limit
WHERE deleted = 0
GROUP BY merchant_scope, merchant_id, COALESCE(match_value, ''), limit_type
HAVING COUNT(1) > 1;

DROP PROCEDURE IF EXISTS drop_merchant_limit_legacy_structure;
DROP PROCEDURE IF EXISTS create_merchant_limit_index_if_missing;

DELIMITER $$

CREATE PROCEDURE drop_merchant_limit_legacy_structure()
BEGIN
    SET @schema_name = DATABASE();

    SET @legacy_indexes = 'idx_risk_rule_scope,idx_risk_rule_time,idx_risk_rule_currency';
    SET @drop_index_sql = (
        SELECT GROUP_CONCAT(CONCAT('DROP INDEX ', INDEX_NAME) SEPARATOR ', ')
        FROM (
            SELECT DISTINCT INDEX_NAME
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'risk_rule_merchant_limit'
              AND FIND_IN_SET(INDEX_NAME, @legacy_indexes) > 0
        ) matched_indexes
    );
    SET @sql = IF(@drop_index_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE risk_rule_merchant_limit ', @drop_index_sql));
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @legacy_columns = 'match_mode,time_window_seconds,threshold_count,elements_json';
    SET @drop_column_sql = (
        SELECT GROUP_CONCAT(CONCAT('DROP COLUMN ', COLUMN_NAME) SEPARATOR ', ')
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'risk_rule_merchant_limit'
          AND FIND_IN_SET(COLUMN_NAME, @legacy_columns) > 0
    );
    SET @sql = IF(@drop_column_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE risk_rule_merchant_limit ', @drop_column_sql));
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END$$

CREATE PROCEDURE create_merchant_limit_index_if_missing(
    IN p_index_name VARCHAR(64),
    IN p_create_sql TEXT
)
BEGIN
    SELECT COUNT(1) INTO @index_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'risk_rule_merchant_limit'
      AND INDEX_NAME = p_index_name;

    IF @index_exists = 0 THEN
        SET @sql = p_create_sql;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL drop_merchant_limit_legacy_structure();

DROP PROCEDURE IF EXISTS drop_merchant_limit_legacy_structure;

CALL create_merchant_limit_index_if_missing(
    'uk_rule_merchant_limit_scope_type_scene_deleted',
    'CREATE UNIQUE INDEX uk_rule_merchant_limit_scope_type_scene_deleted ON risk_rule_merchant_limit (merchant_scope, merchant_id, match_value, limit_type, deleted)'
);

CALL create_merchant_limit_index_if_missing(
    'idx_rule_merchant_limit_trade_lookup',
    'CREATE INDEX idx_rule_merchant_limit_trade_lookup ON risk_rule_merchant_limit (merchant_scope, merchant_id, match_value, limit_type, status, deleted, effective_time, expire_time)'
);

CALL create_merchant_limit_index_if_missing(
    'idx_rule_merchant_limit_merchant_time',
    'CREATE INDEX idx_rule_merchant_limit_merchant_time ON risk_rule_merchant_limit (merchant_scope, merchant_id, update_time, id)'
);

CALL create_merchant_limit_index_if_missing(
    'idx_rule_merchant_limit_time',
    'CREATE INDEX idx_rule_merchant_limit_time ON risk_rule_merchant_limit (update_time, id)'
);

DROP PROCEDURE IF EXISTS create_merchant_limit_index_if_missing;
