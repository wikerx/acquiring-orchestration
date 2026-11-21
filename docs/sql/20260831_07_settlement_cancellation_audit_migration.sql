-- Settlement batch pre-post cancellation immutable audit migration.
-- Prerequisites:
--   20260826_01_settlement_phase_a_schema_draft.sql
--   20260831_03_settlement_review_maker_checker_migration.sql

SET NAMES utf8mb4;

CREATE TABLE settlement_batch_cancellation_audit (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键' ,
    settlement_batch_no VARCHAR(19) NOT NULL COMMENT '关联结算批次号' ,
    request_key VARCHAR(64) NOT NULL COMMENT '操作请求持久化幂等键' ,
    expected_version BIGINT NOT NULL COMMENT '请求携带的预期版本号' ,
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号' ,
    batch_status_before VARCHAR(24) NOT NULL COMMENT '操作前结算批次状态快照' ,
    released_candidate_count INT NOT NULL COMMENT '取消批次时释放认领的候选数量' ,
    operator_account_id BIGINT NOT NULL COMMENT '操作人账号主键' ,
    operator_account_name VARCHAR(128) NOT NULL COMMENT '操作人账号名称快照' ,
    operator_role_snapshot VARCHAR(1000) NOT NULL COMMENT '操作人执行时的角色快照' ,
    client_ip VARCHAR(64) NOT NULL COMMENT '客户端地址，仅用于受控审计' ,
    user_agent VARCHAR(500) NOT NULL COMMENT '客户端用户代理信息' ,
    reason VARCHAR(400) NOT NULL COMMENT '操作原因或业务说明' ,
    operation_time DATETIME(3) NOT NULL COMMENT '业务操作发生时间' ,
    cancelled_time DATETIME(3) NOT NULL COMMENT '结算批次取消成功时间' ,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' ,
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_batch_cancellation_batch (settlement_batch_no),
    UNIQUE KEY uk_settlement_batch_cancellation_request (request_key),
    KEY idx_settlement_batch_cancellation_merchant_time (merchant_id, cancelled_time, id),
    CONSTRAINT chk_settlement_batch_cancellation_value CHECK (
        expected_version >= 0
        AND released_candidate_count >= 0
        AND (operator_account_id > 0
             OR (operator_account_id = 0
                 AND operator_account_name = 'service-settlement'
                 AND operator_role_snapshot = 'SYSTEM'))
        AND batch_status_before IN ('CREATED', 'CLAIMING', 'CLAIMED', 'RATE_LOCKED',
                                    'CALCULATING', 'CALCULATED', 'FAILED_RETRYABLE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='结算批次入账前取消操作的不可变审计记录';
