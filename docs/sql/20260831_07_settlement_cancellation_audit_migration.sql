-- Settlement batch pre-post cancellation immutable audit migration.
-- Prerequisites:
--   20260826_01_settlement_phase_a_schema_draft.sql
--   20260831_03_settlement_review_maker_checker_migration.sql

SET NAMES utf8mb4;

CREATE TABLE settlement_batch_cancellation_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    settlement_batch_no VARCHAR(19) NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    expected_version BIGINT NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    batch_status_before VARCHAR(24) NOT NULL,
    released_candidate_count INT NOT NULL,
    operator_account_id BIGINT NOT NULL,
    operator_account_name VARCHAR(128) NOT NULL,
    operator_role_snapshot VARCHAR(1000) NOT NULL,
    client_ip VARCHAR(64) NOT NULL,
    user_agent VARCHAR(500) NOT NULL,
    reason VARCHAR(400) NOT NULL,
    operation_time DATETIME(3) NOT NULL,
    cancelled_time DATETIME(3) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
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
  COMMENT='Immutable settlement batch pre-post cancellation audit';
