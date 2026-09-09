-- 费用试算完整快照迁移草案。执行前请确认当前数据库、现有数据和备份策略。
-- 通过 information_schema 保持 MySQL 8.0 兼容，并允许脚本重复执行。
SET @add_fee_simulation_label_amount_usd := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_simulation_record ADD COLUMN label_amount_usd DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT ''标签金额按试算汇率归一后的USD快照'' AFTER label_to_usd_rate',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'fee_simulation_record'
      AND column_name = 'label_amount_usd'
);
PREPARE stmt FROM @add_fee_simulation_label_amount_usd;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fee_simulation_reserve_rate := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_simulation_record ADD COLUMN reserve_rate DECIMAL(12,8) NOT NULL DEFAULT 0 COMMENT ''试算使用的滚动保证金比例快照'' AFTER final_fee_usd',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'fee_simulation_record'
      AND column_name = 'reserve_rate'
);
PREPARE stmt FROM @add_fee_simulation_reserve_rate;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fee_simulation_net_formula := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_simulation_record ADD COLUMN net_settlement_formula_snapshot VARCHAR(1000) NOT NULL DEFAULT '''' COMMENT ''净结算计算公式快照'' AFTER formula_snapshot',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'fee_simulation_record'
      AND column_name = 'net_settlement_formula_snapshot'
);
PREPARE stmt FROM @add_fee_simulation_net_formula;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS fee_simulation_record_detail (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    simulation_record_id BIGINT NOT NULL COMMENT '费用试算记录ID',
    line_no INT NOT NULL COMMENT '同一试算内的稳定明细顺序',
    item_type VARCHAR(16) NOT NULL COMMENT '明细类型：FEE、RESERVE',
    fee_category VARCHAR(32) NOT NULL COMMENT '费用分类；保证金使用RESERVE',
    risk_service_type VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '风控类型；非风控使用NONE',
    calculation_status VARCHAR(24) NOT NULL COMMENT '计算状态：CALCULATED、NOT_APPLICABLE、NOT_CONFIGURED',
    included_in_fee_total TINYINT NOT NULL DEFAULT 0 COMMENT '是否计入费用合计：0否、1是',
    charge_trigger VARCHAR(32) NOT NULL DEFAULT 'NOT_APPLICABLE' COMMENT '收费触发快照',
    rule_name VARCHAR(128) NULL COMMENT '命中规则名称快照',
    fee_mode VARCHAR(16) NULL COMMENT '命中计费模式快照',
    matched_rule_id BIGINT NULL COMMENT '命中费用规则ID',
    matched_tier_id BIGINT NULL COMMENT '命中费用档位ID',
    percentage_fee_label DECIMAL(24,8) NULL COMMENT '标签币种百分比费用',
    percentage_fee_currency CHAR(3) NULL COMMENT '百分比费用标签币种',
    raw_fee_usd DECIMAL(24,8) NULL COMMENT '应用上下限前USD费用',
    final_fee_usd DECIMAL(24,8) NULL COMMENT '最终USD金额；未计算时为空',
    applied_limit VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '实际应用的上下限',
    formula_snapshot VARCHAR(1000) NULL COMMENT '单项计算公式快照',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fee_simulation_detail_line (simulation_record_id, line_no),
    KEY idx_fee_simulation_detail_rule (matched_rule_id, matched_tier_id),
    CONSTRAINT chk_fee_simulation_detail_type CHECK (item_type IN ('FEE', 'RESERVE')),
    CONSTRAINT chk_fee_simulation_detail_status CHECK (calculation_status IN ('CALCULATED', 'NOT_APPLICABLE', 'NOT_CONFIGURED')),
    CONSTRAINT chk_fee_simulation_detail_total CHECK (included_in_fee_total IN (0, 1)),
    CONSTRAINT chk_fee_simulation_detail_risk CHECK (risk_service_type IN ('NONE', 'INTERNAL', 'EXTERNAL', 'THREE_DS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='费用试算逐项审计快照表';
