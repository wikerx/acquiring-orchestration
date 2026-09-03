-- Trusted settlement scheduler cancellation audit postcheck. Every count must be zero.

SELECT 1 - COUNT(*) AS missing_system_cancellation_check_count
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name = 'settlement_batch_cancellation_audit'
  AND constraint_name = 'chk_settlement_batch_cancellation_value'
  AND constraint_type = 'CHECK'
  AND enforced = 'YES';

SELECT COUNT(*) AS invalid_system_cancellation_operator_count
FROM settlement_batch_cancellation_audit audit
WHERE audit.operator_account_id < 0
   OR (audit.operator_account_id = 0
       AND (audit.operator_account_name <> 'service-settlement'
            OR audit.operator_role_snapshot <> 'SYSTEM'));
