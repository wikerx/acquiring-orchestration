-- Settlement reversal and three-layer projection post-deployment checks.
-- Every *_count result must be zero.

SELECT 2 - COUNT(*) AS missing_reversal_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('settlement_reversal_daily_sequence', 'settlement_reversal_order');

WITH expected_column AS (
    SELECT 'transaction_operation' table_name, 'settlement_amount' column_name, 'decimal' data_type, 24 numeric_precision, 8 numeric_scale
    UNION ALL SELECT 'transaction_operation', 'settlement_rate', 'decimal', 24, 12
    UNION ALL SELECT 'transaction_operation_202603', 'settlement_amount', 'decimal', 24, 8
    UNION ALL SELECT 'transaction_operation_202603', 'settlement_rate', 'decimal', 24, 12
    UNION ALL SELECT 'transaction_operation_202604', 'settlement_amount', 'decimal', 24, 8
    UNION ALL SELECT 'transaction_operation_202604', 'settlement_rate', 'decimal', 24, 12
    UNION ALL SELECT 'transaction_order', 'settlement_amount', 'decimal', 24, 8
    UNION ALL SELECT 'transaction_order', 'settlement_rate', 'decimal', 24, 12
    UNION ALL SELECT 'transaction_order_202603', 'settlement_amount', 'decimal', 24, 8
    UNION ALL SELECT 'transaction_order_202603', 'settlement_rate', 'decimal', 24, 12
    UNION ALL SELECT 'transaction_order_202604', 'settlement_amount', 'decimal', 24, 8
    UNION ALL SELECT 'transaction_order_202604', 'settlement_rate', 'decimal', 24, 12
    UNION ALL SELECT 'transaction_finance_state', 'settlement_amount', 'decimal', 24, 8
    UNION ALL SELECT 'transaction_finance_state', 'settlement_rate', 'decimal', 24, 12
    UNION ALL SELECT 'transaction_finance_state_202603', 'settlement_amount', 'decimal', 24, 8
    UNION ALL SELECT 'transaction_finance_state_202603', 'settlement_rate', 'decimal', 24, 12
    UNION ALL SELECT 'transaction_finance_state_202604', 'settlement_amount', 'decimal', 24, 8
    UNION ALL SELECT 'transaction_finance_state_202604', 'settlement_rate', 'decimal', 24, 12
    UNION ALL SELECT 'settlement_projection_task', 'settlement_amount', 'decimal', 24, 8
)
SELECT COUNT(*) AS invalid_settlement_decimal_shape_count
FROM expected_column expected
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = expected.table_name
 AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL
   OR actual.data_type <> expected.data_type
   OR actual.numeric_precision <> expected.numeric_precision
   OR actual.numeric_scale <> expected.numeric_scale;

WITH expected_column AS (
    SELECT 'transaction_operation' table_name, 'settlement_date' column_name,
           'date' data_type, CAST(NULL AS UNSIGNED) character_length
    UNION ALL SELECT 'transaction_operation', 'settlement_batch_no', 'varchar', 19
    UNION ALL SELECT 'transaction_operation_202603', 'settlement_date', 'date', NULL
    UNION ALL SELECT 'transaction_operation_202603', 'settlement_batch_no', 'varchar', 19
    UNION ALL SELECT 'transaction_operation_202604', 'settlement_date', 'date', NULL
    UNION ALL SELECT 'transaction_operation_202604', 'settlement_batch_no', 'varchar', 19
    UNION ALL SELECT 'transaction_order', 'settlement_date', 'date', NULL
    UNION ALL SELECT 'transaction_order', 'settlement_batch_no', 'varchar', 19
    UNION ALL SELECT 'transaction_order', 'settlement_transaction_id', 'varchar', 64
    UNION ALL SELECT 'transaction_order', 'settlement_transaction_date_time', 'datetime', NULL
    UNION ALL SELECT 'transaction_order_202603', 'settlement_date', 'date', NULL
    UNION ALL SELECT 'transaction_order_202603', 'settlement_batch_no', 'varchar', 19
    UNION ALL SELECT 'transaction_order_202603', 'settlement_transaction_id', 'varchar', 64
    UNION ALL SELECT 'transaction_order_202603', 'settlement_transaction_date_time', 'datetime', NULL
    UNION ALL SELECT 'transaction_order_202604', 'settlement_date', 'date', NULL
    UNION ALL SELECT 'transaction_order_202604', 'settlement_batch_no', 'varchar', 19
    UNION ALL SELECT 'transaction_order_202604', 'settlement_transaction_id', 'varchar', 64
    UNION ALL SELECT 'transaction_order_202604', 'settlement_transaction_date_time', 'datetime', NULL
    UNION ALL SELECT 'transaction_finance_state', 'settlement_batch_no', 'varchar', 19
    UNION ALL SELECT 'transaction_finance_state_202603', 'settlement_batch_no', 'varchar', 19
    UNION ALL SELECT 'transaction_finance_state_202604', 'settlement_batch_no', 'varchar', 19
    UNION ALL SELECT 'settlement_projection_task', 'settlement_date', 'date', NULL
)
SELECT COUNT(*) AS missing_or_invalid_projection_identity_column_count
FROM expected_column expected
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = expected.table_name
 AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL
   OR actual.data_type <> expected.data_type
   OR (expected.character_length IS NOT NULL
       AND actual.character_maximum_length <> expected.character_length);

SELECT COUNT(*) AS invalid_reversal_order_state_count
FROM settlement_reversal_order
WHERE (reversal_status = 'PENDING_APPROVAL'
       AND (reversal_batch_no IS NOT NULL OR decision_request_key IS NOT NULL
            OR decided_by_account_id IS NOT NULL OR decision_time IS NOT NULL))
   OR (reversal_status = 'APPROVED'
       AND (reversal_batch_no IS NULL OR decision_action <> 'APPROVE'
            OR decision_request_key IS NULL OR decision_time IS NULL
            OR submitted_by_account_id = decided_by_account_id))
   OR (reversal_status = 'REJECTED'
       AND (reversal_batch_no IS NOT NULL OR decision_action <> 'REJECT'
            OR decision_request_key IS NULL OR decision_time IS NULL
            OR submitted_by_account_id = decided_by_account_id));

SELECT COUNT(*) AS duplicate_active_original_batch_count
FROM (
    SELECT original_batch_no
    FROM settlement_reversal_order
    WHERE reversal_status IN ('PENDING_APPROVAL', 'APPROVED')
    GROUP BY original_batch_no
    HAVING COUNT(*) > 1
) duplicate_active;

SELECT COUNT(*) AS invalid_approved_reversal_batch_count
FROM settlement_reversal_order reversal_order
LEFT JOIN settlement_batch original_batch
  ON original_batch.settlement_batch_no = reversal_order.original_batch_no
LEFT JOIN settlement_batch reversal_batch
  ON reversal_batch.settlement_batch_no = reversal_order.reversal_batch_no
 AND reversal_batch.original_batch_no = reversal_order.original_batch_no
WHERE reversal_order.reversal_status = 'APPROVED'
  AND (original_batch.id IS NULL OR original_batch.batch_status <> 'REVERSED'
       OR reversal_batch.id IS NULL OR reversal_batch.batch_type <> 'REVERSAL'
       OR reversal_batch.batch_status <> 'POSTED');

SELECT COUNT(*) AS invalid_reversal_fund_ledger_audit_count
FROM settlement_reversal_order reversal_order
LEFT JOIN merchant_fund_ledger ledger
  ON ledger.settlement_batch_no = reversal_order.reversal_batch_no
 AND ledger.reversal_of_ledger_id = reversal_order.original_fund_ledger_id
WHERE reversal_order.reversal_status = 'APPROVED'
  AND (ledger.id IS NULL
       OR NOT (ledger.operation_mode <=> 'MANUAL')
       OR NOT (ledger.operator_id <=> reversal_order.submitted_by_account_id)
       OR NOT (ledger.reviewer_id <=> reversal_order.decided_by_account_id)
       OR ledger.operator_id = ledger.reviewer_id
       OR NOT (ledger.operation_reason <=> reversal_order.submit_reason)
       OR NOT (ledger.review_comment <=> reversal_order.decision_comment)
       OR NOT (ledger.submit_time <=> reversal_order.submitted_time)
       OR NOT (ledger.review_time <=> reversal_order.decision_time)
       OR NOT (ledger.request_id <=> reversal_order.reversal_order_no));

SELECT COUNT(*) AS non_transaction_candidate_projection_count
FROM settlement_projection_task task
LEFT JOIN settlement_batch_candidate relation
  ON relation.candidate_id = task.candidate_id
 AND relation.settlement_batch_no = COALESCE(task.original_batch_no, task.settlement_batch_no)
WHERE relation.id IS NULL OR relation.source_type <> 'CLEARING_REVISION';

WITH finance_state AS (
    SELECT transaction_id, transaction_date_time, settlement_status, settlement_currency,
           settlement_amount, settlement_rate, settlement_date, settlement_batch_no, deleted
    FROM transaction_finance_state
    UNION ALL
    SELECT transaction_id, transaction_date_time, settlement_status, settlement_currency,
           settlement_amount, settlement_rate, settlement_date, settlement_batch_no, deleted
    FROM transaction_finance_state_202603
    UNION ALL
    SELECT transaction_id, transaction_date_time, settlement_status, settlement_currency,
           settlement_amount, settlement_rate, settlement_date, settlement_batch_no, deleted
    FROM transaction_finance_state_202604
), operation_state AS (
    SELECT id, transaction_id, transaction_date_time, settlement_status, settlement_currency,
           settlement_amount, settlement_rate, settlement_date, settlement_batch_no, deleted
    FROM transaction_operation
    UNION ALL
    SELECT id, transaction_id, transaction_date_time, settlement_status, settlement_currency,
           settlement_amount, settlement_rate, settlement_date, settlement_batch_no, deleted
    FROM transaction_operation_202603
    UNION ALL
    SELECT id, transaction_id, transaction_date_time, settlement_status, settlement_currency,
           settlement_amount, settlement_rate, settlement_date, settlement_batch_no, deleted
    FROM transaction_operation_202604
)
SELECT COUNT(*) AS invalid_finance_operation_settlement_projection_count
FROM finance_state finance
LEFT JOIN operation_state operation
  ON operation.transaction_id = finance.transaction_id
 AND operation.transaction_date_time = finance.transaction_date_time
 AND operation.deleted = 0
WHERE finance.deleted = 0
  AND finance.settlement_status IN ('SETTLED', 'REVERSED')
  AND (operation.id IS NULL
       OR operation.settlement_status <> finance.settlement_status
       OR NOT (operation.settlement_currency <=> finance.settlement_currency)
       OR NOT (operation.settlement_amount <=> finance.settlement_amount)
       OR NOT (operation.settlement_rate <=> finance.settlement_rate)
       OR NOT (operation.settlement_date <=> finance.settlement_date)
       OR NOT (operation.settlement_batch_no <=> finance.settlement_batch_no));

WITH operation_state AS (
    SELECT id, operation_id, transaction_id, transaction_date_time, settlement_status,
           settlement_currency, settlement_amount, settlement_rate, settlement_date,
           settlement_batch_no, deleted
    FROM transaction_operation
    UNION ALL
    SELECT id, operation_id, transaction_id, transaction_date_time, settlement_status,
           settlement_currency, settlement_amount, settlement_rate, settlement_date,
           settlement_batch_no, deleted
    FROM transaction_operation_202603
    UNION ALL
    SELECT id, operation_id, transaction_id, transaction_date_time, settlement_status,
           settlement_currency, settlement_amount, settlement_rate, settlement_date,
           settlement_batch_no, deleted
    FROM transaction_operation_202604
), order_state AS (
    SELECT operation_id, settlement_transaction_id, settlement_transaction_date_time,
           settlement_status, settlement_currency, settlement_amount, settlement_rate,
           settlement_date, settlement_batch_no, deleted
    FROM transaction_order
    UNION ALL
    SELECT operation_id, settlement_transaction_id, settlement_transaction_date_time,
           settlement_status, settlement_currency, settlement_amount, settlement_rate,
           settlement_date, settlement_batch_no, deleted
    FROM transaction_order_202603
    UNION ALL
    SELECT operation_id, settlement_transaction_id, settlement_transaction_date_time,
           settlement_status, settlement_currency, settlement_amount, settlement_rate,
           settlement_date, settlement_batch_no, deleted
    FROM transaction_order_202604
)
SELECT COUNT(*) AS invalid_order_latest_action_settlement_projection_count
FROM order_state lifecycle
LEFT JOIN operation_state operation
  ON operation.operation_id = lifecycle.operation_id
 AND operation.transaction_id = lifecycle.settlement_transaction_id
 AND operation.transaction_date_time = lifecycle.settlement_transaction_date_time
 AND operation.deleted = 0
WHERE lifecycle.deleted = 0
  AND lifecycle.settlement_status IN ('SETTLED', 'REVERSED')
  AND (operation.id IS NULL
       OR operation.settlement_status <> lifecycle.settlement_status
       OR NOT (operation.settlement_currency <=> lifecycle.settlement_currency)
       OR NOT (operation.settlement_amount <=> lifecycle.settlement_amount)
       OR NOT (operation.settlement_rate <=> lifecycle.settlement_rate)
       OR NOT (operation.settlement_date <=> lifecycle.settlement_date)
       OR NOT (operation.settlement_batch_no <=> lifecycle.settlement_batch_no));

WITH finance_state AS (
    SELECT transaction_id, transaction_date_time, settlement_status, settlement_currency,
           settlement_amount, settlement_rate, settlement_date, settlement_batch_no, deleted
    FROM transaction_finance_state
    UNION ALL
    SELECT transaction_id, transaction_date_time, settlement_status, settlement_currency,
           settlement_amount, settlement_rate, settlement_date, settlement_batch_no, deleted
    FROM transaction_finance_state_202603
    UNION ALL
    SELECT transaction_id, transaction_date_time, settlement_status, settlement_currency,
           settlement_amount, settlement_rate, settlement_date, settlement_batch_no, deleted
    FROM transaction_finance_state_202604
), operation_state AS (
    SELECT transaction_id, transaction_date_time, transaction_currency, deleted
    FROM transaction_operation
    UNION ALL
    SELECT transaction_id, transaction_date_time, transaction_currency, deleted
    FROM transaction_operation_202603
    UNION ALL
    SELECT transaction_id, transaction_date_time, transaction_currency, deleted
    FROM transaction_operation_202604
)
SELECT COUNT(*) AS invalid_settlement_rate_source_count
FROM finance_state finance
JOIN operation_state operation
  ON operation.transaction_id = finance.transaction_id
 AND operation.transaction_date_time = finance.transaction_date_time
 AND operation.deleted = 0
LEFT JOIN settlement_batch projected_batch
  ON projected_batch.settlement_batch_no = finance.settlement_batch_no
LEFT JOIN settlement_batch_rate batch_rate
  ON batch_rate.settlement_batch_no = CASE
         WHEN finance.settlement_status = 'REVERSED' THEN projected_batch.original_batch_no
         ELSE finance.settlement_batch_no
     END
 AND batch_rate.source_currency = operation.transaction_currency
 AND batch_rate.target_currency = finance.settlement_currency
 AND batch_rate.rate_type = 'SETTLEMENT'
 AND batch_rate.rate_status = 'LOCKED'
WHERE finance.deleted = 0
  AND finance.settlement_status IN ('SETTLED', 'REVERSED')
  AND (finance.settlement_amount IS NULL OR finance.settlement_rate IS NULL
       OR finance.settlement_rate <= 0 OR batch_rate.id IS NULL
       OR batch_rate.direct_rate <> finance.settlement_rate);
