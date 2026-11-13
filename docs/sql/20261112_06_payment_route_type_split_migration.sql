-- 支付路由阶段1A类型拆分前向迁移。
-- 仅升级管理端配置结构，不接入或修改真实支付选路。

CREATE TABLE IF NOT EXISTS payment_route_method (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rule_id BIGINT NOT NULL COMMENT '支付路由规则ID',
    payment_method VARCHAR(64) NOT NULL COMMENT '规则适用的收单支付方式',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_route_method_rule_method_deleted (rule_id, payment_method, deleted),
    KEY idx_payment_route_method_method_rule (payment_method, rule_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付路由规则适用支付方式表';

DROP PROCEDURE IF EXISTS migrate_payment_route_type_split;
DELIMITER $$
CREATE PROCEDURE migrate_payment_route_type_split()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_strategy'
          AND column_name = 'history_lookback_months'
    ) THEN
        ALTER TABLE payment_route_strategy
            ADD COLUMN history_lookback_months INT NULL
                COMMENT '历史成功MID路由回看月数，其他类型为空'
                AFTER strategy_priority;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_candidate'
          AND column_name = 'candidate_weight'
    ) THEN
        ALTER TABLE payment_route_candidate
            ADD COLUMN candidate_weight DECIMAL(5,2) NULL
                COMMENT 'WEIGHT类型候选权重，按支付方式合计100.00'
                AFTER candidate_priority;
    END IF;
END$$
DELIMITER ;

CALL migrate_payment_route_type_split();
DROP PROCEDURE migrate_payment_route_type_split;

-- 首版 STANDARD 实际只表达候选 MID 顺序，前向收敛为明确的 PRIORITY 类型。
UPDATE payment_route_rule
SET rule_type = 'PRIORITY'
WHERE rule_type = 'STANDARD';

-- 尝试从仅支持单一支付方式的 MID 修复首版 ALL 占位，无法无歧义判断的记录保留给后检阻断发布。
UPDATE payment_route_candidate candidate
JOIN channel_mid_config mid_config ON mid_config.id = candidate.mid_config_id AND mid_config.deleted = 0
SET candidate.payment_method = UPPER(TRIM(mid_config.payment_method_scope))
WHERE candidate.deleted = 0
  AND candidate.payment_method = 'ALL'
  AND mid_config.payment_method_scope IS NOT NULL
  AND TRIM(mid_config.payment_method_scope) <> ''
  AND UPPER(TRIM(mid_config.payment_method_scope)) <> 'ALL'
  AND LOCATE(',', mid_config.payment_method_scope) = 0;

INSERT IGNORE INTO payment_route_method (
    rule_id, payment_method, create_by, create_time, update_by, update_time, deleted
)
SELECT candidate.rule_id, UPPER(TRIM(candidate.payment_method)),
       COALESCE(candidate.create_by, 'migration'), candidate.create_time,
       COALESCE(candidate.update_by, 'migration'), candidate.update_time, 0
FROM payment_route_candidate candidate
WHERE candidate.deleted = 0
  AND candidate.payment_method IS NOT NULL
  AND TRIM(candidate.payment_method) <> ''
  AND UPPER(TRIM(candidate.payment_method)) <> 'ALL';

INSERT IGNORE INTO payment_route_method (
    rule_id, payment_method, create_by, create_time, update_by, update_time, deleted
)
SELECT rule_record.id, UPPER(TRIM(rule_record.payment_method)),
       COALESCE(rule_record.create_by, 'migration'), rule_record.create_time,
       COALESCE(rule_record.update_by, 'migration'), rule_record.update_time, 0
FROM payment_route_rule rule_record
WHERE rule_record.deleted = 0
  AND rule_record.payment_method IS NOT NULL
  AND TRIM(rule_record.payment_method) <> ''
  AND UPPER(TRIM(rule_record.payment_method)) <> 'ALL';

-- 类型专属配置与规则类型保持一致，旧 strategy_priority 仅保留结构兼容值。
UPDATE payment_route_strategy strategy
JOIN payment_route_rule rule_record ON rule_record.id = strategy.rule_id
SET strategy.strategy_type = rule_record.rule_type,
    strategy.strategy_priority = 100,
    strategy.history_lookback_months = CASE
        WHEN rule_record.rule_type = 'HISTORY_AFFINITY'
            THEN COALESCE(strategy.history_lookback_months, 6)
        ELSE NULL
    END
WHERE strategy.deleted = 0 AND rule_record.deleted = 0;
