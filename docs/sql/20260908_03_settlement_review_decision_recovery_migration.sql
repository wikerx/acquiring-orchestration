-- Immutable audit for manually resuming failed segmented settlement review decisions.
-- Prerequisite: 20260905_01_manual_settlement_async_review_migration.sql has completed.

SET NAMES utf8mb4;

CREATE TABLE settlement_review_decision_recovery_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_no VARCHAR(40) NOT NULL,
    review_order_no VARCHAR(19) NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    expected_version BIGINT NOT NULL,
    task_status_before VARCHAR(24) NOT NULL,
    processed_segment_count_before INT NOT NULL,
    result_batch_count_before INT NOT NULL,
    retry_count_before INT NOT NULL,
    failure_code_before VARCHAR(64) NOT NULL,
    failure_message_before VARCHAR(500) NULL,
    started_time_before DATETIME(3) NULL,
    completed_time_before DATETIME(3) NULL,
    operator_account_id BIGINT NOT NULL,
    operator_account_name VARCHAR(128) NOT NULL,
    operator_role_snapshot VARCHAR(1000) NOT NULL,
    client_ip VARCHAR(64) NOT NULL,
    user_agent VARCHAR(500) NOT NULL,
    reason VARCHAR(400) NOT NULL,
    operation_time DATETIME(3) NOT NULL,
    recovered_time DATETIME(3) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
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
  COMMENT='Immutable audit for manually resuming failed segmented settlement review decisions';
