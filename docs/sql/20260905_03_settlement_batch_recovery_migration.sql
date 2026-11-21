-- Add immutable audit for operator-triggered rate-locking recovery and allow manual-review cancellation.
-- Prerequisites:
--   20260831_07_settlement_cancellation_audit_migration.sql
--   20260901_05_settlement_system_operator_cancellation_migration.sql

SET NAMES utf8mb4;

CREATE TABLE settlement_batch_recovery_audit (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键' ,
    settlement_batch_no VARCHAR(19) NOT NULL COMMENT '关联结算批次号' ,
    request_key VARCHAR(64) NOT NULL COMMENT '操作请求持久化幂等键' ,
    expected_version BIGINT NOT NULL COMMENT '请求携带的预期版本号' ,
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号' ,
    recovery_action VARCHAR(32) NOT NULL COMMENT '人工恢复动作类型' ,
    batch_status_before VARCHAR(24) NOT NULL COMMENT '操作前结算批次状态快照' ,
    failure_stage_before VARCHAR(32) NOT NULL COMMENT '恢复前失败阶段快照' ,
    failure_code_before VARCHAR(64) NOT NULL COMMENT '恢复前失败原因码快照' ,
    retry_count_before INT NOT NULL COMMENT '恢复前已重试次数' ,
    restored_candidate_count INT NOT NULL COMMENT '恢复时重新关联的候选数量' ,
    operator_account_id BIGINT NOT NULL COMMENT '操作人账号主键' ,
    operator_account_name VARCHAR(128) NOT NULL COMMENT '操作人账号名称快照' ,
    operator_role_snapshot VARCHAR(1000) NOT NULL COMMENT '操作人执行时的角色快照' ,
    client_ip VARCHAR(64) NOT NULL COMMENT '客户端地址，仅用于受控审计' ,
    user_agent VARCHAR(500) NOT NULL COMMENT '客户端用户代理信息' ,
    reason VARCHAR(400) NOT NULL COMMENT '操作原因或业务说明' ,
    operation_time DATETIME(3) NOT NULL COMMENT '业务操作发生时间' ,
    recovered_time DATETIME(3) NOT NULL COMMENT '人工恢复完成时间' ,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' ,
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_batch_recovery_request (request_key),
    KEY idx_settlement_batch_recovery_batch_time (settlement_batch_no, recovered_time, id),
    KEY idx_settlement_batch_recovery_merchant_time (merchant_id, recovered_time, id),
    CONSTRAINT chk_settlement_batch_recovery_value CHECK (
        expected_version >= 0
        AND retry_count_before >= 0
        AND restored_candidate_count > 0
        AND operator_account_id > 0
        AND recovery_action = 'RETRY_RATE_LOCKING'
        AND batch_status_before = 'MANUAL_REVIEW'
        AND failure_stage_before = 'RATE_LOCKING'
        AND failure_code_before = 'SETTLEMENT_RETRY_EXHAUSTED'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='结算批次汇率锁定重试耗尽后的人工恢复审计记录';

SET @cancellation_value_check_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'settlement_batch_cancellation_audit'
      AND constraint_name = 'chk_settlement_batch_cancellation_value'
      AND constraint_type = 'CHECK'
);

SET @drop_cancellation_value_check_sql = IF(
    @cancellation_value_check_exists > 0,
    'ALTER TABLE settlement_batch_cancellation_audit DROP CHECK chk_settlement_batch_cancellation_value',
    'SELECT 1'
);
PREPARE drop_cancellation_value_check_statement FROM @drop_cancellation_value_check_sql;
EXECUTE drop_cancellation_value_check_statement;
DEALLOCATE PREPARE drop_cancellation_value_check_statement;

ALTER TABLE settlement_batch_cancellation_audit
    ADD CONSTRAINT chk_settlement_batch_cancellation_value CHECK (
        expected_version >= 0
        AND released_candidate_count >= 0
        AND (operator_account_id > 0
             OR (operator_account_id = 0
                 AND operator_account_name = 'service-settlement'
                 AND operator_role_snapshot = 'SYSTEM'))
        AND batch_status_before IN ('CREATED', 'CLAIMING', 'CLAIMED', 'RATE_LOCKED',
                                    'CALCULATING', 'CALCULATED', 'FAILED_RETRYABLE', 'MANUAL_REVIEW')
    );
