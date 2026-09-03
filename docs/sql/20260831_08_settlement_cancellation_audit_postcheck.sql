-- Settlement batch cancellation audit post-deployment checks.
-- Every *_count result must be zero.

SELECT 1 - COUNT(*) AS missing_cancellation_audit_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'settlement_batch_cancellation_audit';

WITH expected_column AS (
    SELECT 'settlement_batch_no' column_name, 'varchar' data_type, 19 character_length, NULL datetime_precision
    UNION ALL SELECT 'request_key', 'varchar', 64, NULL
    UNION ALL SELECT 'expected_version', 'bigint', NULL, NULL
    UNION ALL SELECT 'merchant_id', 'varchar', 64, NULL
    UNION ALL SELECT 'batch_status_before', 'varchar', 24, NULL
    UNION ALL SELECT 'released_candidate_count', 'int', NULL, NULL
    UNION ALL SELECT 'operator_account_id', 'bigint', NULL, NULL
    UNION ALL SELECT 'operator_account_name', 'varchar', 128, NULL
    UNION ALL SELECT 'operator_role_snapshot', 'varchar', 1000, NULL
    UNION ALL SELECT 'client_ip', 'varchar', 64, NULL
    UNION ALL SELECT 'user_agent', 'varchar', 500, NULL
    UNION ALL SELECT 'reason', 'varchar', 400, NULL
    UNION ALL SELECT 'operation_time', 'datetime', NULL, 3
    UNION ALL SELECT 'cancelled_time', 'datetime', NULL, 3
    UNION ALL SELECT 'create_time', 'datetime', NULL, 3
)
SELECT COUNT(*) AS missing_or_invalid_cancellation_audit_column_count
FROM expected_column expected
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = 'settlement_batch_cancellation_audit'
 AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL
   OR actual.data_type <> expected.data_type
   OR (expected.character_length IS NOT NULL
       AND actual.character_maximum_length <> expected.character_length)
   OR (expected.datetime_precision IS NOT NULL
       AND actual.datetime_precision <> expected.datetime_precision);

SELECT 2 - COUNT(*) AS missing_cancellation_audit_unique_index_count
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'settlement_batch_cancellation_audit'
  AND non_unique = 0
  AND index_name IN ('uk_settlement_batch_cancellation_batch',
                     'uk_settlement_batch_cancellation_request');

SELECT COUNT(*) AS invalid_cancellation_audit_value_count
FROM settlement_batch_cancellation_audit audit
WHERE audit.expected_version < 0
   OR audit.released_candidate_count < 0
   OR audit.operator_account_id < 0
   OR (audit.operator_account_id = 0
       AND (audit.operator_account_name <> 'service-settlement'
            OR audit.operator_role_snapshot <> 'SYSTEM'))
   OR TRIM(audit.operator_account_name) = ''
   OR TRIM(audit.operator_role_snapshot) = ''
   OR TRIM(audit.client_ip) = ''
   OR TRIM(audit.user_agent) = ''
   OR TRIM(audit.reason) = ''
   OR audit.batch_status_before NOT IN ('CREATED', 'CLAIMING', 'CLAIMED', 'RATE_LOCKED',
                                        'CALCULATING', 'CALCULATED', 'FAILED_RETRYABLE');

SELECT COUNT(*) AS invalid_cancellation_batch_state_count
FROM settlement_batch_cancellation_audit audit
LEFT JOIN settlement_batch batch
  ON batch.settlement_batch_no = audit.settlement_batch_no
WHERE batch.id IS NULL
   OR batch.merchant_id <> audit.merchant_id
   OR batch.batch_status <> 'CANCELLED'
   OR batch.cancelled_time <> audit.cancelled_time
   OR batch.version <> audit.expected_version + 1;

SELECT COUNT(*) AS invalid_cancellation_release_count
FROM settlement_batch_cancellation_audit audit
LEFT JOIN (
    SELECT settlement_batch_no, COUNT(*) released_candidate_count
    FROM settlement_batch_candidate
    WHERE relation_status = 'RELEASED'
    GROUP BY settlement_batch_no
) relation ON relation.settlement_batch_no = audit.settlement_batch_no
WHERE COALESCE(relation.released_candidate_count, 0) <> audit.released_candidate_count;
