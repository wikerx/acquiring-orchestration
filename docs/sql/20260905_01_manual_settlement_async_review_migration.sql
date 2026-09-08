-- Manual transaction settlement review generation for large candidate sets.
-- Review membership is frozen in bounded transactions; one logical review order may contain many segments.

CREATE TABLE settlement_manual_review_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_no VARCHAR(40) NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    submit_request_key VARCHAR(128) NULL,
    review_order_no VARCHAR(19) NOT NULL,
    review_type VARCHAR(24) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    settlement_profile_id BIGINT NOT NULL,
    settlement_account_id BIGINT NOT NULL,
    target_currency CHAR(3) NOT NULL,
    target_currency_exponent TINYINT NOT NULL,
    payment_type VARCHAR(64) NULL,
    payment_method VARCHAR(64) NULL,
    business_date DATE NOT NULL,
    business_time_zone VARCHAR(64) NOT NULL,
    cutoff_begin_time DATETIME(3) NOT NULL,
    cutoff_end_time DATETIME(3) NOT NULL,
    snapshot_max_candidate_id BIGINT NOT NULL,
    expected_candidate_count INT NOT NULL,
    processed_candidate_count INT NOT NULL DEFAULT 0,
    locked_candidate_count INT NOT NULL DEFAULT 0,
    last_candidate_id BIGINT NOT NULL DEFAULT 0,
    initial_delay_unit CHAR(1) NOT NULL,
    initial_delay_days INT NOT NULL,
    regular_delay_days INT NOT NULL,
    settlement_frequency VARCHAR(16) NOT NULL,
    frequency_day INT NULL,
    preview_json JSON NOT NULL,
    task_status VARCHAR(24) NOT NULL,
    submitted_by_account_id BIGINT NOT NULL,
    submitted_by_account_name VARCHAR(128) NOT NULL,
    submitted_role_snapshot VARCHAR(1000) NOT NULL,
    submit_client_ip VARCHAR(64) NOT NULL,
    submit_user_agent VARCHAR(500) NOT NULL,
    submit_reason VARCHAR(400) NOT NULL,
    operation_time DATETIME(3) NOT NULL,
    processing_owner VARCHAR(128) NULL,
    processing_deadline DATETIME(3) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME(3) NOT NULL,
    last_failure_code VARCHAR(64) NULL,
    last_failure_message VARCHAR(500) NULL,
    started_time DATETIME(3) NULL,
    completed_time DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    active_profile_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN task_status IN ('PREVIEWED', 'QUEUED', 'PROCESSING', 'FINALIZING', 'CANCELLING')
             THEN settlement_profile_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_manual_review_task_no (task_no),
    UNIQUE KEY uk_manual_review_task_request (request_key),
    UNIQUE KEY uk_manual_review_task_submit_request (submit_request_key),
    UNIQUE KEY uk_manual_review_task_review (review_order_no),
    UNIQUE KEY uk_manual_review_task_active_profile (active_profile_id),
    KEY idx_manual_review_task_due (task_status, next_retry_time, id),
    KEY idx_manual_review_task_merchant (merchant_id, task_status, create_time, id),
    CONSTRAINT chk_manual_review_task_value CHECK (
        review_type = 'REGULAR'
        AND target_currency_exponent BETWEEN 0 AND 8
        AND cutoff_end_time > cutoff_begin_time
        AND snapshot_max_candidate_id >= 0
        AND expected_candidate_count >= 0
        AND processed_candidate_count BETWEEN 0 AND expected_candidate_count
        AND locked_candidate_count BETWEEN 0 AND expected_candidate_count
        AND last_candidate_id BETWEEN 0 AND snapshot_max_candidate_id
        AND initial_delay_unit IN ('T', 'D')
        AND initial_delay_days >= 1 AND regular_delay_days >= 1
        AND retry_count >= 0 AND version >= 0
    ),
    CONSTRAINT chk_manual_review_task_state CHECK (
        task_status IN ('PREVIEWED', 'QUEUED', 'PROCESSING', 'FINALIZING',
                        'COMPLETED', 'FAILED', 'CANCELLING', 'CANCELLED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Server-side frozen manual settlement review generation task';

CREATE TABLE settlement_review_segment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    segment_no VARCHAR(64) NOT NULL,
    review_order_no VARCHAR(19) NOT NULL,
    sequence_no INT NOT NULL,
    first_candidate_id BIGINT NOT NULL,
    last_candidate_id BIGINT NOT NULL,
    candidate_count INT NOT NULL,
    projectable_candidate_count INT NOT NULL,
    source_fingerprint CHAR(64) NOT NULL,
    result_fingerprint CHAR(64) NOT NULL,
    net_signed_amount DECIMAL(24,8) NOT NULL,
    settlement_batch_no VARCHAR(19) NULL,
    segment_status VARCHAR(16) NOT NULL DEFAULT 'LOCKED',
    processed_time DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_review_segment_no (segment_no),
    UNIQUE KEY uk_settlement_review_segment_sequence (review_order_no, sequence_no),
    UNIQUE KEY uk_settlement_review_segment_range
        (review_order_no, first_candidate_id, last_candidate_id),
    UNIQUE KEY uk_settlement_review_segment_batch (settlement_batch_no),
    KEY idx_settlement_review_segment_status (review_order_no, segment_status, sequence_no, id),
    CONSTRAINT chk_settlement_review_segment CHECK (
        sequence_no >= 1
        AND first_candidate_id > 0 AND last_candidate_id >= first_candidate_id
        AND candidate_count >= 1
        AND projectable_candidate_count BETWEEN 0 AND candidate_count
        AND segment_status IN ('LOCKED', 'CONSUMED', 'RELEASED')
        AND version >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Physical processing segments behind one logical settlement review order';

CREATE TABLE settlement_review_decision_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_no VARCHAR(40) NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    review_order_no VARCHAR(19) NOT NULL,
    expected_review_version BIGINT NOT NULL,
    decision_action VARCHAR(16) NOT NULL,
    decision_comment VARCHAR(400) NOT NULL,
    operator_account_id BIGINT NOT NULL,
    operator_account_name VARCHAR(128) NOT NULL,
    operator_role_snapshot VARCHAR(1000) NOT NULL,
    client_ip VARCHAR(64) NOT NULL,
    user_agent VARCHAR(500) NOT NULL,
    operation_time DATETIME(3) NOT NULL,
    total_segment_count INT NOT NULL,
    processed_segment_count INT NOT NULL DEFAULT 0,
    result_batch_count INT NOT NULL DEFAULT 0,
    first_settlement_batch_no VARCHAR(19) NULL,
    task_status VARCHAR(24) NOT NULL,
    processing_owner VARCHAR(128) NULL,
    processing_deadline DATETIME(3) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME(3) NOT NULL,
    last_failure_code VARCHAR(64) NULL,
    last_failure_message VARCHAR(500) NULL,
    started_time DATETIME(3) NULL,
    completed_time DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    active_review_order_no VARCHAR(19) GENERATED ALWAYS AS (
        CASE WHEN task_status IN ('QUEUED', 'PROCESSING', 'FAILED')
             THEN review_order_no ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_review_decision_task_no (task_no),
    UNIQUE KEY uk_settlement_review_decision_request (request_key),
    UNIQUE KEY uk_settlement_review_decision_active (active_review_order_no),
    KEY idx_settlement_review_decision_due (task_status, next_retry_time, id),
    KEY idx_settlement_review_decision_order (review_order_no, create_time, id),
    CONSTRAINT chk_settlement_review_decision_task CHECK (
        expected_review_version >= 0
        AND decision_action IN ('APPROVE', 'REJECT', 'CANCEL')
        AND total_segment_count >= 1
        AND processed_segment_count BETWEEN 0 AND total_segment_count
        AND result_batch_count BETWEEN 0 AND processed_segment_count
        AND retry_count >= 0 AND version >= 0
        AND task_status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')
        AND ((decision_action = 'APPROVE')
             OR (result_batch_count = 0 AND first_settlement_batch_no IS NULL))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Resumable segmented Maker-Checker decision task for large settlement reviews';

ALTER TABLE settlement_review_order
    DROP CHECK chk_settlement_review_value,
    DROP CHECK chk_settlement_review_state;

ALTER TABLE settlement_review_order
    ADD CONSTRAINT chk_settlement_review_value CHECK (
        target_currency_exponent BETWEEN 0 AND 8
        AND cutoff_end_time > cutoff_begin_time
        AND candidate_count >= 1
        AND projectable_candidate_count BETWEEN 0 AND candidate_count
        AND net_amount >= 0
        AND version >= 0
    ),
    ADD CONSTRAINT chk_settlement_review_state CHECK (
        review_type IN ('REGULAR', 'RESERVE_RELEASE', 'ADJUSTMENT')
        AND create_mode IN ('MANUAL', 'MANUAL_ASYNC', 'AUTO_REVIEW')
        AND net_direction IN ('CREDIT', 'DEBIT')
        AND review_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED')
        AND ((review_status = 'PENDING_APPROVAL'
              AND decided_by_account_id IS NULL AND decision_action IS NULL
              AND decision_request_key IS NULL AND decision_time IS NULL
              AND review_comment IS NULL
              AND settlement_batch_no IS NULL)
             OR (review_status = 'APPROVED'
                 AND decided_by_account_id IS NOT NULL AND decided_by_account_name IS NOT NULL
                 AND decided_role_snapshot IS NOT NULL AND decision_client_ip IS NOT NULL
                 AND decision_user_agent IS NOT NULL AND decision_action = 'APPROVE'
                 AND decision_request_key IS NOT NULL AND decision_time IS NOT NULL
                 AND review_comment IS NOT NULL
                 AND settlement_batch_no IS NOT NULL
                 AND submitted_by_account_id <> decided_by_account_id)
             OR (review_status = 'REJECTED'
                 AND decided_by_account_id IS NOT NULL AND decided_by_account_name IS NOT NULL
                 AND decided_role_snapshot IS NOT NULL AND decision_client_ip IS NOT NULL
                 AND decision_user_agent IS NOT NULL AND decision_action = 'REJECT'
                 AND decision_request_key IS NOT NULL AND decision_time IS NOT NULL
                 AND review_comment IS NOT NULL
                 AND submitted_by_account_id <> decided_by_account_id
                 AND settlement_batch_no IS NULL)
             OR (review_status = 'CANCELLED'
                 AND decided_by_account_id IS NOT NULL AND decided_by_account_name IS NOT NULL
                 AND decided_role_snapshot IS NOT NULL AND decision_client_ip IS NOT NULL
                 AND decision_user_agent IS NOT NULL AND decision_action = 'CANCEL'
                 AND decision_request_key IS NOT NULL AND decision_time IS NOT NULL
                 AND review_comment IS NOT NULL
                 AND submitted_by_account_id = decided_by_account_id
                 AND settlement_batch_no IS NULL)
             OR (review_status = 'EXPIRED'
                 AND decided_by_account_id IS NOT NULL AND decided_by_account_name IS NOT NULL
                 AND decided_role_snapshot IS NOT NULL AND decision_client_ip IS NOT NULL
                 AND decision_user_agent IS NOT NULL AND decision_action = 'EXPIRE'
                 AND decision_request_key IS NOT NULL AND decision_time IS NOT NULL
                 AND review_comment IS NOT NULL
                 AND settlement_batch_no IS NULL))
    );

ALTER TABLE settlement_candidate
    ADD KEY idx_settlement_candidate_manual_review
        (merchant_id, settlement_profile_id, target_currency, candidate_status,
         shadow_mode, settlement_eligible_date, create_time, id);

ALTER TABLE settlement_batch
    DROP INDEX uk_settlement_batch_review,
    ADD KEY idx_settlement_batch_review (review_order_no, batch_status, id);

ALTER TABLE settlement_batch_rate
    DROP INDEX uk_settlement_batch_review_rate,
    ADD UNIQUE KEY uk_settlement_batch_review_rate
        (settlement_batch_no, review_rate_id);
