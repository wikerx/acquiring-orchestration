-- Post-deployment checks for settlement batch recovery. Every *_count result must be zero.

SELECT 1 - COUNT(*) AS missing_recovery_audit_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'settlement_batch_recovery_audit';

WITH expected_column AS (
    SELECT 'settlement_batch_no' column_name, 'varchar' data_type, 19 character_length, NULL datetime_precision
    UNION ALL SELECT 'request_key', 'varchar', 64, NULL
    UNION ALL SELECT 'expected_version', 'bigint', NULL, NULL
    UNION ALL SELECT 'merchant_id', 'varchar', 64, NULL
    UNION ALL SELECT 'recovery_action', 'varchar', 32, NULL
    UNION ALL SELECT 'batch_status_before', 'varchar', 24, NULL
    UNION ALL SELECT 'failure_stage_before', 'varchar', 32, NULL
    UNION ALL SELECT 'failure_code_before', 'varchar', 64, NULL
    UNION ALL SELECT 'retry_count_before', 'int', NULL, NULL
    UNION ALL SELECT 'restored_candidate_count', 'int', NULL, NULL
    UNION ALL SELECT 'operator_account_id', 'bigint', NULL, NULL
    UNION ALL SELECT 'operator_account_name', 'varchar', 128, NULL
    UNION ALL SELECT 'operator_role_snapshot', 'varchar', 1000, NULL
    UNION ALL SELECT 'client_ip', 'varchar', 64, NULL
    UNION ALL SELECT 'user_agent', 'varchar', 500, NULL
    UNION ALL SELECT 'reason', 'varchar', 400, NULL
    UNION ALL SELECT 'operation_time', 'datetime', NULL, 3
    UNION ALL SELECT 'recovered_time', 'datetime', NULL, 3
    UNION ALL SELECT 'create_time', 'datetime', NULL, 3
)
SELECT COUNT(*) AS missing_or_invalid_recovery_audit_column_count
FROM expected_column expected
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = 'settlement_batch_recovery_audit'
 AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL
   OR actual.data_type <> expected.data_type
   OR (expected.character_length IS NOT NULL
       AND actual.character_maximum_length <> expected.character_length)
   OR (expected.datetime_precision IS NOT NULL
       AND actual.datetime_precision <> expected.datetime_precision);

SELECT 1 - COUNT(*) AS missing_recovery_audit_unique_index_count
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'settlement_batch_recovery_audit'
  AND index_name = 'uk_settlement_batch_recovery_request'
  AND non_unique = 0;

SELECT COUNT(*) AS invalid_recovery_audit_value_count
FROM settlement_batch_recovery_audit audit
WHERE audit.expected_version < 0
   OR audit.retry_count_before < 0
   OR audit.restored_candidate_count <= 0
   OR audit.operator_account_id <= 0
   OR TRIM(audit.operator_account_name) = ''
   OR TRIM(audit.operator_role_snapshot) = ''
   OR TRIM(audit.client_ip) = ''
   OR TRIM(audit.user_agent) = ''
   OR TRIM(audit.reason) = ''
   OR audit.recovery_action <> 'RETRY_RATE_LOCKING'
   OR audit.batch_status_before <> 'MANUAL_REVIEW'
   OR audit.failure_stage_before <> 'RATE_LOCKING'
   OR audit.failure_code_before <> 'SETTLEMENT_RETRY_EXHAUSTED';

SELECT COUNT(*) AS invalid_recovery_batch_reference_count
FROM settlement_batch_recovery_audit audit
LEFT JOIN settlement_batch batch
  ON batch.settlement_batch_no = audit.settlement_batch_no
WHERE batch.id IS NULL
   OR batch.merchant_id <> audit.merchant_id
   OR batch.version < audit.expected_version + 1;

SELECT COUNT(*) AS invalid_recovery_relation_count
FROM settlement_batch_recovery_audit audit
LEFT JOIN (
    SELECT settlement_batch_no, COUNT(*) relation_count
    FROM settlement_batch_candidate
    GROUP BY settlement_batch_no
) relation ON relation.settlement_batch_no = audit.settlement_batch_no
WHERE COALESCE(relation.relation_count, 0) <> audit.restored_candidate_count;

SELECT 1 - COUNT(*) AS missing_manual_review_cancellation_check_count
FROM information_schema.check_constraints constraint_row
WHERE constraint_row.constraint_schema = DATABASE()
  AND constraint_row.constraint_name = 'chk_settlement_batch_cancellation_value'
  AND UPPER(constraint_row.check_clause) LIKE '%MANUAL_REVIEW%';
