-- Settlement review Maker-Checker schema migration.
-- Prerequisite: 20260831_01_settlement_p0_consistency_migration.sql has completed.

SET NAMES utf8mb4;

-- Must return zero before migration. Candidate audit relations are the authority for projection counts.
SELECT COUNT(*) AS invalid_existing_batch_projection_count
FROM settlement_batch batch
LEFT JOIN (
    SELECT relation.settlement_batch_no,
           COUNT(*) AS relation_count,
           SUM(relation.source_type = 'CLEARING_REVISION') AS projectable_candidate_count
    FROM settlement_batch_candidate relation
    GROUP BY relation.settlement_batch_no
) projection ON projection.settlement_batch_no = batch.settlement_batch_no
WHERE COALESCE(projection.relation_count, 0) <> batch.candidate_count
   OR COALESCE(projection.projectable_candidate_count, 0) > batch.candidate_count;

CREATE TABLE settlement_review_daily_sequence (
    business_date DATE NOT NULL,
    current_sequence INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (business_date),
    CONSTRAINT chk_settlement_review_sequence CHECK (
        current_sequence BETWEEN 0 AND 99999999 AND version >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Settlement review database daily sequence';

CREATE TABLE settlement_review_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    review_order_no VARCHAR(19) NOT NULL,
    create_request_key VARCHAR(128) NOT NULL,
    selection_fingerprint CHAR(64) NOT NULL,
    review_type VARCHAR(24) NOT NULL,
    create_mode VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
    merchant_id VARCHAR(64) NOT NULL,
    settlement_profile_id BIGINT NOT NULL,
    settlement_account_id BIGINT NOT NULL,
    target_currency CHAR(3) NOT NULL,
    target_currency_exponent TINYINT NOT NULL,
    business_date DATE NOT NULL,
    business_time_zone VARCHAR(64) NOT NULL,
    cutoff_begin_time DATETIME(3) NOT NULL,
    cutoff_end_time DATETIME(3) NOT NULL,
    candidate_count INT NOT NULL,
    projectable_candidate_count INT NOT NULL DEFAULT 0,
    source_fingerprint CHAR(64) NOT NULL,
    rate_fingerprint CHAR(64) NOT NULL,
    result_fingerprint CHAR(64) NOT NULL,
    net_direction VARCHAR(8) NOT NULL,
    net_amount DECIMAL(24,8) NOT NULL,
    review_status VARCHAR(24) NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    created_by_account_name VARCHAR(128) NOT NULL,
    submitted_by_account_id BIGINT NOT NULL,
    submitted_by_account_name VARCHAR(128) NOT NULL,
    submitted_role_snapshot VARCHAR(1000) NOT NULL,
    submit_client_ip VARCHAR(64) NOT NULL,
    submit_user_agent VARCHAR(500) NOT NULL,
    submit_reason VARCHAR(400) NOT NULL,
    submitted_time DATETIME(3) NOT NULL,
    decided_by_account_id BIGINT NULL,
    decided_by_account_name VARCHAR(128) NULL,
    decided_role_snapshot VARCHAR(1000) NULL,
    decision_client_ip VARCHAR(64) NULL,
    decision_user_agent VARCHAR(500) NULL,
    decision_action VARCHAR(16) NULL,
    decision_request_key VARCHAR(128) NULL,
    review_comment VARCHAR(400) NULL,
    decision_time DATETIME(3) NULL,
    settlement_batch_no VARCHAR(19) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_review_no (review_order_no),
    UNIQUE KEY uk_settlement_review_create_request (create_request_key),
    UNIQUE KEY uk_settlement_review_decision_request (decision_request_key),
    UNIQUE KEY uk_settlement_review_batch (settlement_batch_no),
    KEY idx_settlement_review_merchant_status (merchant_id, review_status, create_time, id),
    KEY idx_settlement_review_status_time (review_status, submitted_time, id),
    CONSTRAINT chk_settlement_review_value CHECK (
        target_currency_exponent BETWEEN 0 AND 8
        AND cutoff_end_time > cutoff_begin_time
        AND candidate_count BETWEEN 1 AND 1000
        AND projectable_candidate_count BETWEEN 0 AND candidate_count
        AND net_amount >= 0
        AND version >= 0
    ),
    CONSTRAINT chk_settlement_review_state CHECK (
        review_type IN ('REGULAR', 'RESERVE_RELEASE', 'ADJUSTMENT')
        AND create_mode IN ('MANUAL', 'AUTO_REVIEW')
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
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Immutable settlement review order and Maker-Checker audit';

CREATE TABLE settlement_review_candidate (
    id BIGINT NOT NULL AUTO_INCREMENT,
    review_candidate_no VARCHAR(34) NOT NULL,
    review_order_no VARCHAR(19) NOT NULL,
    candidate_id BIGINT NOT NULL,
    candidate_no VARCHAR(64) NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    source_business_id VARCHAR(64) NOT NULL,
    source_revision INT NOT NULL,
    source_transaction_id VARCHAR(64) NOT NULL,
    source_transaction_date_time DATETIME(3) NOT NULL,
    locked_candidate_version BIGINT NOT NULL,
    clearing_fingerprint CHAR(64) NOT NULL,
    relation_status VARCHAR(16) NOT NULL,
    locked_time DATETIME(3) NOT NULL,
    consumed_time DATETIME(3) NULL,
    released_time DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_review_candidate_no (review_candidate_no),
    UNIQUE KEY uk_settlement_review_candidate (review_order_no, candidate_id),
    KEY idx_settlement_review_candidate_id (candidate_id, relation_status, id),
    CONSTRAINT chk_settlement_review_candidate_state CHECK (
        source_type IN ('CLEARING_REVISION', 'RESERVE_RELEASE', 'ADJUSTMENT')
        AND source_revision >= 1 AND locked_candidate_version >= 0 AND version >= 0
        AND relation_status IN ('LOCKED', 'CONSUMED', 'RELEASED')
        AND ((relation_status = 'LOCKED' AND consumed_time IS NULL AND released_time IS NULL)
             OR (relation_status = 'CONSUMED' AND consumed_time IS NOT NULL AND released_time IS NULL)
             OR (relation_status = 'RELEASED' AND consumed_time IS NULL AND released_time IS NOT NULL))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Immutable settlement review candidate snapshot';

CREATE TABLE settlement_review_rate (
    id BIGINT NOT NULL AUTO_INCREMENT,
    review_order_no VARCHAR(19) NOT NULL,
    source_currency CHAR(3) NOT NULL,
    target_currency CHAR(3) NOT NULL,
    direct_rate DECIMAL(24,12) NOT NULL,
    source_currency_exponent TINYINT NOT NULL,
    target_currency_exponent TINYINT NOT NULL,
    rate_source VARCHAR(64) NOT NULL,
    quote_id VARCHAR(128) NULL,
    source_quote_direction VARCHAR(16) NOT NULL,
    effective_time DATETIME(3) NOT NULL,
    locked_time DATETIME(3) NOT NULL,
    locked_by VARCHAR(128) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_review_rate_pair (review_order_no, source_currency, target_currency),
    CONSTRAINT chk_settlement_review_rate CHECK (
        direct_rate > 0
        AND source_currency_exponent BETWEEN 0 AND 8
        AND target_currency_exponent BETWEEN 0 AND 8
        AND source_quote_direction IN ('DIRECT', 'INVERSE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Immutable settlement review unified rate matrix';

CREATE TABLE settlement_review_summary (
    id BIGINT NOT NULL AUTO_INCREMENT,
    review_order_no VARCHAR(19) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    payment_type VARCHAR(32) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    result_item_type VARCHAR(32) NOT NULL,
    fee_category VARCHAR(32) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    source_currency CHAR(3) NOT NULL,
    target_currency CHAR(3) NOT NULL,
    transaction_count BIGINT NOT NULL,
    source_amount DECIMAL(24,8) NOT NULL,
    target_amount DECIMAL(24,8) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_review_summary (
        review_order_no, payment_type, payment_method, transaction_type,
        result_item_type, fee_category, direction, source_currency, target_currency
    ),
    CONSTRAINT chk_settlement_review_summary CHECK (
        direction IN ('CREDIT', 'DEBIT')
        AND transaction_count > 0 AND source_amount >= 0 AND target_amount >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Immutable settlement review financial summary';

-- MySQL 8 DROP CHECK requires the deployed symbol to exist; tolerate partial baselines safely.
DROP PROCEDURE IF EXISTS drop_settlement_review_check;
DELIMITER $$
CREATE PROCEDURE drop_settlement_review_check(
    IN target_table VARCHAR(64),
    IN target_constraint VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = target_table
          AND constraint_name = target_constraint
          AND constraint_type = 'CHECK'
    ) THEN
        SET @drop_review_check_sql = CONCAT(
            'ALTER TABLE `', target_table, '` DROP CHECK `', target_constraint, '`'
        );
        PREPARE drop_review_check_stmt FROM @drop_review_check_sql;
        EXECUTE drop_review_check_stmt;
        DEALLOCATE PREPARE drop_review_check_stmt;
    END IF;
END$$
DELIMITER ;

CALL drop_settlement_review_check('settlement_candidate', 'chk_settlement_candidate_state');
DROP PROCEDURE drop_settlement_review_check;

ALTER TABLE settlement_candidate
    ADD COLUMN review_order_no VARCHAR(19) NULL AFTER settlement_batch_no,
    ADD COLUMN review_locked_time DATETIME(3) NULL AFTER review_order_no,
    ADD KEY idx_settlement_candidate_review (review_order_no, candidate_status, id),
    ADD CONSTRAINT chk_settlement_candidate_state CHECK (
        source_type IN ('CLEARING_REVISION', 'RESERVE_RELEASE', 'ADJUSTMENT')
        AND candidate_status IN (
            'READY', 'REPLAY_HOLD', 'REVIEW_LOCKED', 'SUPERSEDED',
            'CLAIMED', 'POSTED', 'MANUAL_REVIEW', 'CANCELLED'
        )
        AND ((candidate_status IN ('READY', 'REPLAY_HOLD', 'SUPERSEDED')
              AND settlement_batch_no IS NULL AND review_order_no IS NULL)
             OR (candidate_status = 'REVIEW_LOCKED'
                 AND settlement_batch_no IS NULL AND review_order_no IS NOT NULL
                 AND review_locked_time IS NOT NULL)
             OR (candidate_status IN ('CLAIMED', 'POSTED', 'MANUAL_REVIEW', 'CANCELLED')
                 AND settlement_batch_no IS NOT NULL))
    );

ALTER TABLE settlement_batch
    ADD COLUMN review_order_no VARCHAR(19) NULL AFTER original_batch_no,
    ADD COLUMN create_mode VARCHAR(24) NOT NULL DEFAULT 'AUTO' AFTER review_order_no,
    ADD COLUMN projectable_candidate_count INT NOT NULL DEFAULT 0 AFTER candidate_count,
    ADD COLUMN result_fingerprint CHAR(64) NULL AFTER projectable_candidate_count,
    ADD COLUMN maker_account_id BIGINT NULL AFTER result_fingerprint,
    ADD COLUMN maker_account_name VARCHAR(128) NULL AFTER maker_account_id,
    ADD COLUMN maker_role_snapshot VARCHAR(1000) NULL AFTER maker_account_name,
    ADD COLUMN maker_client_ip VARCHAR(64) NULL AFTER maker_role_snapshot,
    ADD COLUMN maker_user_agent VARCHAR(500) NULL AFTER maker_client_ip,
    ADD COLUMN maker_reason VARCHAR(400) NULL AFTER maker_user_agent,
    ADD COLUMN maker_time DATETIME(3) NULL AFTER maker_reason,
    ADD COLUMN checker_account_id BIGINT NULL AFTER maker_time,
    ADD COLUMN checker_account_name VARCHAR(128) NULL AFTER checker_account_id,
    ADD COLUMN checker_role_snapshot VARCHAR(1000) NULL AFTER checker_account_name,
    ADD COLUMN checker_client_ip VARCHAR(64) NULL AFTER checker_role_snapshot,
    ADD COLUMN checker_user_agent VARCHAR(500) NULL AFTER checker_client_ip,
    ADD COLUMN checker_comment VARCHAR(400) NULL AFTER checker_user_agent,
    ADD COLUMN checker_time DATETIME(3) NULL AFTER checker_comment,
    ADD UNIQUE KEY uk_settlement_batch_review (review_order_no),
    ADD CONSTRAINT chk_settlement_batch_review_audit CHECK (
        create_mode IN ('AUTO', 'MANUAL_REVIEW')
        AND projectable_candidate_count BETWEEN 0 AND candidate_count
        AND ((create_mode = 'AUTO')
             OR (review_order_no IS NOT NULL AND result_fingerprint IS NOT NULL
                 AND maker_account_id IS NOT NULL AND maker_account_name IS NOT NULL
                 AND maker_role_snapshot IS NOT NULL AND maker_client_ip IS NOT NULL
                 AND maker_user_agent IS NOT NULL
                 AND maker_reason IS NOT NULL AND maker_time IS NOT NULL
                 AND checker_account_id IS NOT NULL AND checker_account_name IS NOT NULL
                 AND checker_role_snapshot IS NOT NULL AND checker_client_ip IS NOT NULL
                 AND checker_user_agent IS NOT NULL
                 AND checker_comment IS NOT NULL AND checker_time IS NOT NULL
                 AND maker_account_id <> checker_account_id))
    );

-- Backfill existing AUTO batches before application code starts maintaining both counters in one CAS.
UPDATE settlement_batch batch
LEFT JOIN (
    SELECT relation.settlement_batch_no,
           SUM(relation.source_type = 'CLEARING_REVISION') AS projectable_candidate_count
    FROM settlement_batch_candidate relation
    GROUP BY relation.settlement_batch_no
) projection ON projection.settlement_batch_no = batch.settlement_batch_no
SET batch.projectable_candidate_count = COALESCE(projection.projectable_candidate_count, 0);

ALTER TABLE settlement_batch_rate
    ADD COLUMN review_rate_id BIGINT NULL AFTER settlement_batch_no,
    ADD UNIQUE KEY uk_settlement_batch_review_rate (review_rate_id);
