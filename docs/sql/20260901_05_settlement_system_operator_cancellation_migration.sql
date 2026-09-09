-- Allow only the trusted settlement scheduler identity to use account id 0 in cancellation audit.
-- This migration is idempotent and preserves all existing immutable audit rows.

SET NAMES utf8mb4;

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
                                    'CALCULATING', 'CALCULATED', 'FAILED_RETRYABLE')
    );
