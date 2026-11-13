-- 支付路由阶段1A交易维度精简：前向迁移。
-- 新增交易类型多选关系表，并迁移旧规则表中的单值交易类型。

CREATE TABLE IF NOT EXISTS payment_route_transaction_type (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rule_id BIGINT NOT NULL COMMENT '支付路由规则ID',
    transaction_type VARCHAR(64) NOT NULL COMMENT '规则适用的收单交易类型',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_route_transaction_rule_type_deleted (rule_id, transaction_type, deleted),
    KEY idx_payment_route_transaction_type_rule (transaction_type, rule_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付路由规则适用交易类型表；无有效记录表示全部交易类型';

INSERT IGNORE INTO payment_route_transaction_type (
    rule_id,
    transaction_type,
    create_by,
    create_time,
    update_by,
    update_time,
    deleted
)
SELECT
    rule_record.id,
    UPPER(TRIM(rule_record.transaction_type)),
    rule_record.create_by,
    COALESCE(rule_record.create_time, CURRENT_TIMESTAMP(3)),
    rule_record.update_by,
    COALESCE(rule_record.update_time, CURRENT_TIMESTAMP(3)),
    0
FROM payment_route_rule rule_record
WHERE rule_record.deleted = 0
  AND rule_record.transaction_type IS NOT NULL
  AND TRIM(rule_record.transaction_type) <> ''
  AND UPPER(TRIM(rule_record.transaction_type)) <> 'ALL';
