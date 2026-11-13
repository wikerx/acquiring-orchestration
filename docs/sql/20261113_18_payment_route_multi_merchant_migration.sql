-- 支付路由指定商户多选：前向迁移。
-- 新增规则与商户关联表，并无损回填旧单商户字段；阶段1A仍不接入真实支付链路。

CREATE TABLE IF NOT EXISTS payment_route_rule_merchant (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rule_id BIGINT NOT NULL COMMENT '支付路由规则ID',
    merchant_id VARCHAR(32) NOT NULL COMMENT '平台商户号',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_route_rule_merchant_deleted (rule_id, merchant_id, deleted),
    KEY idx_payment_route_rule_merchant_lookup (merchant_id, rule_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付路由规则指定商户关联表';

INSERT IGNORE INTO payment_route_rule_merchant (
    rule_id,
    merchant_id,
    create_by,
    create_time,
    update_by,
    update_time,
    deleted
)
SELECT
    rule_record.id,
    TRIM(rule_record.merchant_id),
    rule_record.create_by,
    COALESCE(rule_record.create_time, CURRENT_TIMESTAMP(3)),
    rule_record.update_by,
    COALESCE(rule_record.update_time, CURRENT_TIMESTAMP(3)),
    0
FROM payment_route_rule rule_record
WHERE rule_record.deleted = 0
  AND rule_record.scope_type = 'MERCHANT'
  AND rule_record.merchant_id IS NOT NULL
  AND TRIM(rule_record.merchant_id) <> '';
