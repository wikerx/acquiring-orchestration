-- 3DS规则管理结构化字段迁移草案。
-- 执行前请先备份 risk_rule_3ds，并确认管理端代码已发布到 3DS 专用 Mapper。
-- 本脚本用于把早期 LIKE risk_rule_template 创建的旧表收敛为 3DS 交易匹配专用结构。

SET @schema_name = DATABASE();

DROP PROCEDURE IF EXISTS add_3ds_column_if_missing;
DROP PROCEDURE IF EXISTS drop_3ds_column_if_exists;
DROP PROCEDURE IF EXISTS create_3ds_index_if_missing;

DELIMITER $$

CREATE PROCEDURE add_3ds_column_if_missing(
    IN p_column_name VARCHAR(64),
    IN p_add_sql TEXT
)
BEGIN
    SELECT COUNT(1) INTO @column_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'risk_rule_3ds'
      AND COLUMN_NAME = p_column_name;

    IF @column_exists = 0 THEN
        SET @sql = p_add_sql;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE drop_3ds_column_if_exists(
    IN p_column_name VARCHAR(64)
)
BEGIN
    SELECT COUNT(1) INTO @column_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'risk_rule_3ds'
      AND COLUMN_NAME = p_column_name;

    IF @column_exists > 0 THEN
        SET @sql = CONCAT('ALTER TABLE risk_rule_3ds DROP COLUMN ', p_column_name);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE create_3ds_index_if_missing(
    IN p_index_name VARCHAR(64),
    IN p_create_sql TEXT
)
BEGIN
    SELECT COUNT(1) INTO @index_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'risk_rule_3ds'
      AND INDEX_NAME = p_index_name;

    IF @index_exists = 0 THEN
        SET @sql = p_create_sql;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL add_3ds_column_if_missing(
    'rule_group_no',
    'ALTER TABLE risk_rule_3ds ADD COLUMN rule_group_no VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''规则组编号'' AFTER id'
);

CALL add_3ds_column_if_missing(
    'merchant_name',
    'ALTER TABLE risk_rule_3ds ADD COLUMN merchant_name VARCHAR(128) NULL COMMENT ''商户名称，管理端展示辅助快照'' AFTER merchant_id'
);

CALL add_3ds_column_if_missing(
    'rule_type',
    'ALTER TABLE risk_rule_3ds ADD COLUMN rule_type VARCHAR(32) NOT NULL DEFAULT ''RISK_STRATEGY'' COMMENT ''规则类型：RISK_STRATEGY风险策略、EXEMPTION_STRATEGY豁免策略、CHANNEL_POLICY渠道策略'' AFTER rule_name'
);

CALL add_3ds_column_if_missing(
    'channel_code',
    'ALTER TABLE risk_rule_3ds ADD COLUMN channel_code VARCHAR(64) NOT NULL DEFAULT ''ALL'' COMMENT ''收单渠道编码，ALL表示全部渠道'' AFTER rule_type'
);

CALL add_3ds_column_if_missing(
    'payment_method',
    'ALTER TABLE risk_rule_3ds ADD COLUMN payment_method VARCHAR(64) NOT NULL DEFAULT ''ALL'' COMMENT ''支付方式，ALL表示全部支付方式'' AFTER channel_code'
);

CALL add_3ds_column_if_missing(
    'card_brand',
    'ALTER TABLE risk_rule_3ds ADD COLUMN card_brand VARCHAR(64) NOT NULL DEFAULT ''ALL'' COMMENT ''卡品牌，ALL表示全部卡品牌'' AFTER payment_method'
);

CALL add_3ds_column_if_missing(
    'amount_match_type',
    'ALTER TABLE risk_rule_3ds ADD COLUMN amount_match_type VARCHAR(32) NOT NULL DEFAULT ''ALL'' COMMENT ''金额匹配类型：ALL、GE、LE、BETWEEN'' AFTER card_brand'
);

CALL add_3ds_column_if_missing(
    'risk_condition',
    'ALTER TABLE risk_rule_3ds ADD COLUMN risk_condition VARCHAR(32) NOT NULL DEFAULT ''ANY'' COMMENT ''风险条件：ANY、LOW_AND_ABOVE、MEDIUM_AND_ABOVE、HIGH_AND_ABOVE、CRITICAL_ONLY'' AFTER currency'
);

CALL add_3ds_column_if_missing(
    'trigger_action',
    'ALTER TABLE risk_rule_3ds ADD COLUMN trigger_action VARCHAR(32) NOT NULL DEFAULT ''FORCE_3DS'' COMMENT ''触发动作：FORCE_3DS、SKIP_3DS、FOLLOW_DEFAULT'' AFTER risk_condition'
);

CALL add_3ds_column_if_missing(
    'priority',
    'ALTER TABLE risk_rule_3ds ADD COLUMN priority INT NOT NULL DEFAULT 100 COMMENT ''优先级，数字越小越优先'' AFTER trigger_action'
);

UPDATE risk_rule_3ds
SET rule_group_no = REPLACE(UUID(), '-', '')
WHERE rule_group_no = '';

UPDATE risk_rule_3ds
SET merchant_id = ''
WHERE merchant_scope = 'GLOBAL' AND merchant_id IS NULL;

UPDATE risk_rule_3ds
SET channel_code = 'ALL'
WHERE channel_code IS NULL OR channel_code = '';

UPDATE risk_rule_3ds
SET payment_method = 'ALL'
WHERE payment_method IS NULL OR payment_method = '';

UPDATE risk_rule_3ds
SET card_brand = 'ALL'
WHERE card_brand IS NULL OR card_brand = '';

UPDATE risk_rule_3ds
SET currency = 'USD',
    amount_min = NULL,
    amount_max = NULL
WHERE amount_match_type = 'ALL';

UPDATE risk_rule_3ds
SET currency = 'USD'
WHERE currency IS NULL OR currency = '' OR currency = 'ALL';

-- 数据质量检查：如果有结果，说明历史金额超过 2 位小数，执行字段精度收敛前需人工确认处理方式。
SELECT id, amount_min, amount_max
FROM risk_rule_3ds
WHERE (amount_min IS NOT NULL AND amount_min <> ROUND(amount_min, 2))
   OR (amount_max IS NOT NULL AND amount_max <> ROUND(amount_max, 2));

ALTER TABLE risk_rule_3ds
    MODIFY COLUMN rule_group_no VARCHAR(64) NOT NULL COMMENT '规则组编号',
    MODIFY COLUMN merchant_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商户号；全局范围为空字符串',
    MODIFY COLUMN channel_code VARCHAR(64) NOT NULL DEFAULT 'ALL' COMMENT '收单渠道编码，ALL表示全部渠道',
    MODIFY COLUMN payment_method VARCHAR(64) NOT NULL DEFAULT 'ALL' COMMENT '支付方式，ALL表示全部支付方式',
    MODIFY COLUMN card_brand VARCHAR(64) NOT NULL DEFAULT 'ALL' COMMENT '卡品牌，ALL表示全部卡品牌',
    MODIFY COLUMN amount_min DECIMAL(18,2) NULL COMMENT '最小交易金额，固定USD且保留2位小数',
    MODIFY COLUMN amount_max DECIMAL(18,2) NULL COMMENT '最大交易金额，固定USD且保留2位小数',
    MODIFY COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'USD' COMMENT '交易币种，当前固定USD',
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';

CALL drop_3ds_column_if_exists('risk_level');
CALL drop_3ds_column_if_exists('decision_action');
CALL drop_3ds_column_if_exists('match_mode');
CALL drop_3ds_column_if_exists('match_value');
CALL drop_3ds_column_if_exists('limit_type');
CALL drop_3ds_column_if_exists('time_window_seconds');
CALL drop_3ds_column_if_exists('threshold_count');
CALL drop_3ds_column_if_exists('elements_json');

CALL create_3ds_index_if_missing(
    'uk_rule_3ds_dimension_deleted',
    'CREATE UNIQUE INDEX uk_rule_3ds_dimension_deleted ON risk_rule_3ds (merchant_scope, merchant_id, channel_code, payment_method, card_brand, amount_match_type, amount_min, amount_max, currency, risk_condition, trigger_action, deleted)'
);

CALL create_3ds_index_if_missing(
    'idx_rule_3ds_trade_lookup',
    'CREATE INDEX idx_rule_3ds_trade_lookup ON risk_rule_3ds (deleted, status, merchant_scope, merchant_id, channel_code, payment_method, card_brand, currency, priority, effective_time, expire_time)'
);

CALL create_3ds_index_if_missing(
    'idx_rule_3ds_merchant_time',
    'CREATE INDEX idx_rule_3ds_merchant_time ON risk_rule_3ds (merchant_scope, merchant_id, update_time, id)'
);

CALL create_3ds_index_if_missing(
    'idx_rule_3ds_time',
    'CREATE INDEX idx_rule_3ds_time ON risk_rule_3ds (update_time, id)'
);

DROP PROCEDURE IF EXISTS add_3ds_column_if_missing;
DROP PROCEDURE IF EXISTS drop_3ds_column_if_exists;
DROP PROCEDURE IF EXISTS create_3ds_index_if_missing;
