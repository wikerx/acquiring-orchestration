-- Post-deployment checks for 20260908_03_settlement_review_decision_recovery_migration.sql.
-- Every *_count result must be zero.

SELECT 1 - COUNT(*) AS missing_decision_recovery_audit_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'settlement_review_decision_recovery_audit';

WITH expected_column AS (
    SELECT 'task_no' column_name, 'varchar' data_type, 40 character_length, NULL datetime_precision
    UNION ALL SELECT 'review_order_no', 'varchar', 19, NULL
    UNION ALL SELECT 'request_key', 'varchar', 64, NULL
    UNION ALL SELECT 'expected_version', 'bigint', NULL, NULL
    UNION ALL SELECT 'task_status_before', 'varchar', 24, NULL
    UNION ALL SELECT 'processed_segment_count_before', 'int', NULL, NULL
    UNION ALL SELECT 'result_batch_count_before', 'int', NULL, NULL
    UNION ALL SELECT 'retry_count_before', 'int', NULL, NULL
    UNION ALL SELECT 'failure_code_before', 'varchar', 64, NULL
    UNION ALL SELECT 'failure_message_before', 'varchar', 500, NULL
    UNION ALL SELECT 'started_time_before', 'datetime', NULL, 3
    UNION ALL SELECT 'completed_time_before', 'datetime', NULL, 3
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
SELECT COUNT(*) AS missing_or_invalid_decision_recovery_audit_column_count
FROM expected_column expected
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = 'settlement_review_decision_recovery_audit'
 AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL
   OR actual.data_type <> expected.data_type
   OR (expected.character_length IS NOT NULL
       AND actual.character_maximum_length <> expected.character_length)
   OR (expected.datetime_precision IS NOT NULL
       AND actual.datetime_precision <> expected.datetime_precision);

SELECT 1 - COUNT(*) AS missing_decision_recovery_audit_unique_index_count
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'settlement_review_decision_recovery_audit'
  AND index_name = 'uk_settlement_review_decision_recovery_request'
  AND non_unique = 0;

SELECT 1 - COUNT(*) AS missing_decision_recovery_audit_task_index_count
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'settlement_review_decision_recovery_audit'
  AND index_name = 'idx_settlement_review_decision_recovery_task_time'
  AND seq_in_index = 1
  AND column_name = 'task_no';

SELECT COUNT(*) AS invalid_decision_recovery_audit_value_count
FROM settlement_review_decision_recovery_audit audit
WHERE audit.expected_version < 0
   OR audit.processed_segment_count_before < 0
   OR audit.result_batch_count_before < 0
   OR audit.result_batch_count_before > audit.processed_segment_count_before
   OR audit.retry_count_before < 0
   OR audit.task_status_before <> 'FAILED'
   OR TRIM(audit.failure_code_before) = ''
   OR audit.operator_account_id <= 0
   OR TRIM(audit.operator_account_name) = ''
   OR TRIM(audit.operator_role_snapshot) = ''
   OR TRIM(audit.client_ip) = ''
   OR TRIM(audit.user_agent) = ''
   OR TRIM(audit.reason) = ''
   OR audit.operation_time IS NULL
   OR audit.recovered_time IS NULL;

SELECT COUNT(*) AS invalid_decision_recovery_task_reference_count
FROM settlement_review_decision_recovery_audit audit
LEFT JOIN settlement_review_decision_task task
  ON task.task_no = audit.task_no
WHERE task.id IS NULL
   OR task.review_order_no <> audit.review_order_no
   OR task.version < audit.expected_version + 1;
