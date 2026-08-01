-- 风控评估明细补充执行阶段、匹配结果和频率规则快照字段。
-- 执行前请备份 risk_evaluation_hit_detail；本脚本只新增可空列和查询索引，不修改既有数据含义。

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_missing $$
CREATE PROCEDURE add_column_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_alter_sql TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @sql = p_alter_sql;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS add_index_if_missing $$
CREATE PROCEDURE add_index_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_create_sql TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND INDEX_NAME = p_index_name
    ) THEN
        SET @sql = p_create_sql;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

CALL add_column_if_missing(
    'risk_evaluation_hit_detail',
    'time_window_seconds',
    'ALTER TABLE risk_evaluation_hit_detail ADD COLUMN time_window_seconds INT NULL COMMENT ''频率规则统计窗口秒数'' AFTER decision_time'
);

CALL add_column_if_missing(
    'risk_evaluation_hit_detail',
    'threshold_count',
    'ALTER TABLE risk_evaluation_hit_detail ADD COLUMN threshold_count INT NULL COMMENT ''频率规则阈值次数'' AFTER time_window_seconds'
);

CALL add_column_if_missing(
    'risk_evaluation_hit_detail',
    'elements_json',
    'ALTER TABLE risk_evaluation_hit_detail ADD COLUMN elements_json JSON NULL COMMENT ''规则统计元素配置快照'' AFTER threshold_count'
);

CALL add_column_if_missing(
    'risk_evaluation_hit_detail',
    'current_count',
    'ALTER TABLE risk_evaluation_hit_detail ADD COLUMN current_count BIGINT NULL COMMENT ''频率规则当前计数'' AFTER elements_json'
);

CALL add_column_if_missing(
    'risk_evaluation_hit_detail',
    'stage_code',
    'ALTER TABLE risk_evaluation_hit_detail ADD COLUMN stage_code VARCHAR(64) NULL COMMENT ''风控执行阶段编码'' AFTER current_count'
);

CALL add_column_if_missing(
    'risk_evaluation_hit_detail',
    'stage_name',
    'ALTER TABLE risk_evaluation_hit_detail ADD COLUMN stage_name VARCHAR(128) NULL COMMENT ''风控执行阶段名称'' AFTER stage_code'
);

CALL add_column_if_missing(
    'risk_evaluation_hit_detail',
    'stage_order',
    'ALTER TABLE risk_evaluation_hit_detail ADD COLUMN stage_order INT NULL COMMENT ''风控执行阶段顺序'' AFTER stage_name'
);

CALL add_column_if_missing(
    'risk_evaluation_hit_detail',
    'match_result',
    'ALTER TABLE risk_evaluation_hit_detail ADD COLUMN match_result VARCHAR(32) NULL COMMENT ''匹配结果：HIT、MISS、PASS、SKIPPED'' AFTER stage_order'
);

CALL add_column_if_missing(
    'risk_evaluation_hit_detail',
    'decision_effect',
    'ALTER TABLE risk_evaluation_hit_detail ADD COLUMN decision_effect VARCHAR(32) NULL COMMENT ''当前明细对最终决策的影响：ALLOW、BLOCK、REVIEW、CHALLENGE、NONE'' AFTER match_result'
);

CALL add_index_if_missing(
    'risk_evaluation_hit_detail',
    'idx_risk_hit_record_stage',
    'CREATE INDEX idx_risk_hit_record_stage ON risk_evaluation_hit_detail (risk_record_no, stage_order, id)'
);

CALL add_index_if_missing(
    'risk_evaluation_record',
    'idx_risk_eval_payment_time',
    'CREATE INDEX idx_risk_eval_payment_time ON risk_evaluation_record (payment_order_no, evaluation_time, risk_record_no)'
);

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;
