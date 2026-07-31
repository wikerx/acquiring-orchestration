-- RG-P1-03 商户累计限额预占生命周期表。
-- 执行前必须完成备份、容量评估和变更审批；本文件不会由应用自动执行。

CREATE TABLE IF NOT EXISTS risk_merchant_limit_reservation (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    transaction_id VARCHAR(64) NOT NULL COMMENT '支付平台交易号',
    risk_record_no VARCHAR(64) NOT NULL COMMENT '首次创建预占的风控评估流水号',
    merchant_id VARCHAR(64) NOT NULL COMMENT '商户号',
    rule_id BIGINT NOT NULL COMMENT '商户累计限额规则 ID',
    limit_type VARCHAR(32) NOT NULL COMMENT 'DAILY/WEEKLY/MONTHLY',
    currency CHAR(3) NOT NULL COMMENT 'ISO 4217 币种',
    period_bucket VARCHAR(16) NOT NULL COMMENT '周期桶，例如 20260730',
    period_begin_time DATETIME(3) NOT NULL COMMENT '周期开始时间',
    period_end_time DATETIME(3) NOT NULL COMMENT '周期结束时间',
    amount_units BIGINT NOT NULL COMMENT '六位小数整数单位的预占金额',
    counter_mode VARCHAR(20) NOT NULL COMMENT '预占时 LEGACY/SHADOW/CLUSTER_SAFE',
    reservation_status VARCHAR(20) NOT NULL COMMENT 'PREPARING/RESERVED/CONFIRMED/CANCELLED',
    cancel_reason VARCHAR(256) NULL COMMENT '取消原因摘要',
    expires_at DATETIME(3) NOT NULL COMMENT 'Redis 周期投影预期过期时间',
    reserved_time DATETIME(3) NULL COMMENT '进入 RESERVED 时间',
    confirmed_time DATETIME(3) NULL COMMENT '进入 CONFIRMED 时间',
    cancelled_time DATETIME(3) NULL COMMENT '进入 CANCELLED 时间',
    version INT NOT NULL DEFAULT 0 COMMENT 'CAS 乐观锁版本',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标识',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_limit_reservation_business
        (transaction_id, rule_id, limit_type, period_bucket),
    KEY idx_risk_limit_reservation_status
        (reservation_status, update_time, id),
    KEY idx_risk_limit_reservation_record
        (risk_record_no, id),
    KEY idx_risk_limit_reservation_baseline
        (merchant_id, rule_id, currency, period_bucket, reservation_status, deleted),
    CONSTRAINT chk_risk_limit_reservation_amount
        CHECK (amount_units > 0),
    CONSTRAINT chk_risk_limit_reservation_status
        CHECK (reservation_status IN ('PREPARING', 'RESERVED', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT chk_risk_limit_reservation_counter_mode
        CHECK (counter_mode IN ('LEGACY', 'SHADOW', 'CLUSTER_SAFE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='商户累计限额 Redis 预占生命周期';
