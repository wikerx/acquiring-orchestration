-- Immutable audit for manually resuming failed segmented settlement review decisions.
-- Prerequisite: 20260905_01_manual_settlement_async_review_migration.sql has completed.

SET NAMES utf8mb4;

CREATE TABLE settlement_review_decision_recovery_audit (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键' ,
    task_no VARCHAR(40) NOT NULL COMMENT '持久化任务编号' ,
    review_order_no VARCHAR(19) NOT NULL COMMENT '结算预审单号' ,
    request_key VARCHAR(64) NOT NULL COMMENT '操作请求持久化幂等键' ,
    expected_version BIGINT NOT NULL COMMENT '请求携带的预期版本号' ,
    task_status_before VARCHAR(24) NOT NULL COMMENT '恢复前任务状态快照' ,
    processed_segment_count_before INT NOT NULL COMMENT '恢复前已处理分段数量' ,
    result_batch_count_before INT NOT NULL COMMENT '恢复前已生成结算批次数量' ,
    retry_count_before INT NOT NULL COMMENT '恢复前已重试次数' ,
    failure_code_before VARCHAR(64) NOT NULL COMMENT '恢复前失败原因码快照' ,
    failure_message_before VARCHAR(500) NULL COMMENT '恢复前失败说明快照' ,
    started_time_before DATETIME(3) NULL COMMENT '恢复前任务开始时间快照' ,
    completed_time_before DATETIME(3) NULL COMMENT '恢复前任务完成时间快照' ,
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
    UNIQUE KEY uk_settlement_review_decision_recovery_request (request_key),
    KEY idx_settlement_review_decision_recovery_task_time
        (task_no, recovered_time, id),
    KEY idx_settlement_review_decision_recovery_order_time
        (review_order_no, recovered_time, id),
    CONSTRAINT chk_settlement_review_decision_recovery_value CHECK (
        expected_version >= 0
        AND processed_segment_count_before >= 0
        AND result_batch_count_before BETWEEN 0 AND processed_segment_count_before
        AND retry_count_before >= 0
        AND task_status_before = 'FAILED'
        AND operator_account_id > 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='结算预审分段决策任务失败后人工恢复的不可变审计记录';
