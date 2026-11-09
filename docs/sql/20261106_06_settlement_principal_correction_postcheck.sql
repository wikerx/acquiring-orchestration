-- 已入账历史标签本金差额调整通道结构与事实后检。
-- 每个计数均应返回0；本脚本只读，不创建或执行调整单。

-- 1. 结构门禁。
SELECT 2 - COUNT(*) AS missing_principal_correction_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'settlement_principal_correction_order',
      'settlement_principal_correction_item'
  );

-- 2. 主单状态、异人复核和零金额流水语义。
SELECT COUNT(*) AS invalid_principal_correction_order_state_count
FROM settlement_principal_correction_order correction
WHERE correction.item_count <= 0
   OR correction.correction_amount < 0
   OR correction.projection_completed_count < 0
   OR correction.projection_completed_count > correction.item_count
   OR (correction.correction_amount = 0 AND correction.correction_direction <> 'CREDIT')
   OR (correction.correction_status = 'PENDING_REVIEW'
       AND (correction.decision_request_key IS NOT NULL
            OR correction.correction_ledger_id IS NOT NULL))
   OR (correction.correction_status = 'EXECUTED'
       AND (correction.decision_action <> 'APPROVE'
            OR correction.submitted_by_account_id = correction.decided_by_account_id
            OR (correction.correction_amount = 0 AND correction.correction_ledger_id IS NOT NULL)
            OR (correction.correction_amount > 0 AND correction.correction_ledger_id IS NULL)))
   OR (correction.correction_status = 'REJECTED'
       AND (correction.decision_action <> 'REJECT'
            OR correction.submitted_by_account_id = correction.decided_by_account_id
            OR correction.correction_ledger_id IS NOT NULL));

-- 3. 每个主单的冻结条数和投影完成数必须与逐笔任务一致。
SELECT COUNT(*) AS invalid_principal_correction_item_count
FROM settlement_principal_correction_order correction
LEFT JOIN (
    SELECT correction_no,
           COUNT(*) AS item_count,
           SUM(projection_status = 'COMPLETED') AS completed_count
    FROM settlement_principal_correction_item
    GROUP BY correction_no
) item_count ON item_count.correction_no = correction.correction_no
WHERE COALESCE(item_count.item_count, 0) <> correction.item_count
   OR COALESCE(item_count.completed_count, 0) <> correction.projection_completed_count;

-- 4. 原批次、原结果项、原汇率和原投影任务身份必须仍可追溯且保持POSTED/COMPLETED。
SELECT COUNT(*) AS invalid_principal_correction_source_identity_count
FROM settlement_principal_correction_item item
JOIN settlement_principal_correction_order correction
  ON correction.correction_no = item.correction_no
LEFT JOIN settlement_batch batch
  ON batch.settlement_batch_no = correction.original_batch_no
LEFT JOIN settlement_result_item result_item
  ON result_item.id = item.original_result_item_id
LEFT JOIN settlement_batch_rate original_rate
  ON original_rate.id = item.original_locked_rate_id
LEFT JOIN settlement_batch_rate label_rate
  ON label_rate.id = item.label_locked_rate_id
LEFT JOIN settlement_projection_task projection
  ON projection.id = item.original_projection_task_id
LEFT JOIN merchant_fund_ledger original_ledger
  ON original_ledger.id = correction.original_fund_ledger_id
LEFT JOIN settlement_result_item net_result
  ON net_result.settlement_batch_no = correction.original_batch_no
 AND net_result.result_role = 'LEDGER_POSTING'
 AND net_result.result_item_type = 'NET_SETTLEMENT'
WHERE batch.id IS NULL
   OR batch.batch_status <> 'POSTED'
   OR batch.batch_type <> 'REGULAR'
   OR batch.version <> correction.original_batch_version
   OR batch.merchant_id <> correction.merchant_id
   OR batch.settlement_account_id <> correction.settlement_account_id
   OR batch.target_currency <> correction.target_currency
   OR batch.target_currency_exponent <> correction.target_currency_exponent
   OR original_ledger.id IS NULL
   OR original_ledger.account_id <> correction.settlement_account_id
   OR original_ledger.merchant_id <> correction.merchant_id
   OR original_ledger.business_type <> 'TRANSACTION_SETTLEMENT'
   OR original_ledger.business_no <> correction.original_batch_no
   OR original_ledger.settlement_batch_no <> correction.original_batch_no
   OR original_ledger.currency <> correction.target_currency
   OR net_result.id IS NULL
   OR net_result.merchant_id <> correction.merchant_id
   OR net_result.settlement_account_id <> correction.settlement_account_id
   OR net_result.target_currency <> correction.target_currency
   OR net_result.target_currency_exponent <> correction.target_currency_exponent
   OR net_result.direction <> original_ledger.direction
   OR net_result.target_amount <> original_ledger.amount
   OR net_result.ledger_idempotency_key <> original_ledger.idempotency_key
   OR result_item.id IS NULL
   OR result_item.settlement_batch_no <> correction.original_batch_no
   OR result_item.candidate_id <> item.candidate_id
   OR result_item.merchant_id <> item.merchant_id
   OR result_item.settlement_account_id <> correction.settlement_account_id
   OR result_item.result_item_type <> 'PRINCIPAL'
   OR result_item.result_role <> 'FINANCIAL_COMPONENT'
   OR result_item.direction <> item.direction
   OR result_item.source_detail_no <> item.clearing_detail_no
   OR result_item.source_amount <> item.original_source_amount
   OR result_item.source_currency <> item.original_source_currency
   OR result_item.source_currency_exponent <> item.original_source_currency_exponent
   OR result_item.settlement_batch_rate_id <> item.original_locked_rate_id
   OR result_item.target_amount <> item.original_target_amount
   OR result_item.target_currency <> item.target_currency
   OR result_item.target_currency_exponent <> item.target_currency_exponent
   OR result_item.rounding_mode <> item.rounding_mode
   OR original_rate.id IS NULL
   OR original_rate.settlement_batch_no <> correction.original_batch_no
   OR original_rate.source_currency <> item.original_source_currency
   OR original_rate.target_currency <> item.target_currency
   OR original_rate.source_currency_exponent <> item.original_source_currency_exponent
   OR original_rate.target_currency_exponent <> item.target_currency_exponent
   OR original_rate.direct_rate <> item.original_locked_direct_rate
   OR original_rate.rate_type <> 'SETTLEMENT'
   OR original_rate.rate_status <> 'LOCKED'
   OR label_rate.id IS NULL
   OR label_rate.settlement_batch_no <> correction.original_batch_no
   OR label_rate.source_currency <> item.label_currency
   OR label_rate.target_currency <> item.target_currency
   OR label_rate.source_currency_exponent <> item.label_currency_exponent
   OR label_rate.target_currency_exponent <> item.target_currency_exponent
   OR label_rate.direct_rate <> item.label_locked_direct_rate
   OR label_rate.rate_type <> 'SETTLEMENT'
   OR label_rate.rate_status <> 'LOCKED'
   OR projection.id IS NULL
   OR projection.task_no <> item.original_projection_task_no
   OR projection.settlement_batch_no <> correction.original_batch_no
   OR projection.projection_action <> 'SETTLE'
   OR projection.candidate_id <> item.candidate_id
   OR projection.transaction_id <> item.transaction_id
   OR projection.transaction_date_time <> item.transaction_date_time
   OR projection.clearing_revision <> item.clearing_revision
   OR projection.operation_id <> item.operation_id
   OR projection.merchant_id <> item.merchant_id
   OR projection.settlement_currency <> item.target_currency
   OR projection.settlement_amount <> item.original_projection_amount
   OR projection.settlement_date <> item.settlement_date
   OR projection.task_status <> 'COMPLETED';

-- 5. 非零调整只能对应一条完整人工差额流水；零调整不得制造零金额流水。
SELECT COUNT(*) AS invalid_principal_correction_ledger_count
FROM settlement_principal_correction_order correction
LEFT JOIN merchant_fund_ledger ledger
  ON ledger.id = correction.correction_ledger_id
WHERE correction.correction_status = 'EXECUTED'
  AND ((correction.correction_amount = 0 AND ledger.id IS NOT NULL)
       OR (correction.correction_amount > 0
           AND (ledger.id IS NULL
                OR ledger.account_id <> correction.settlement_account_id
                OR ledger.merchant_id <> correction.merchant_id
                OR ledger.business_type <> 'SETTLEMENT_PRINCIPAL_CORRECTION'
                OR ledger.business_no <> correction.correction_no
                OR ledger.settlement_batch_no <> correction.original_batch_no
                OR ledger.currency <> correction.target_currency
                OR ledger.direction <> correction.correction_direction
                OR ledger.amount <> correction.correction_amount
                OR ledger.operator_id <> correction.submitted_by_account_id
                OR ledger.reviewer_id <> correction.decided_by_account_id)));

-- 6. 逐笔目标金额必须严格使用冻结direct rate，并在目标exponent仅舍入一次。
SELECT COUNT(*) AS invalid_principal_correction_calculation_count
FROM settlement_principal_correction_item item
WHERE item.expected_target_amount < 0
   OR NOT REGEXP_LIKE(item.original_source_currency, '^[A-Z]{3}$', 'c')
   OR NOT REGEXP_LIKE(item.label_currency, '^[A-Z]{3}$', 'c')
   OR NOT REGEXP_LIKE(item.target_currency, '^[A-Z]{3}$', 'c')
   OR (item.rounding_mode = 'HALF_UP'
       AND item.expected_target_amount <>
           ROUND(item.label_amount * item.label_locked_direct_rate, item.target_currency_exponent))
   OR (item.rounding_mode = 'DOWN'
       AND item.expected_target_amount <>
           TRUNCATE(item.label_amount * item.label_locked_direct_rate,
                    item.target_currency_exponent))
   OR item.signed_delta_amount <>
      CASE item.direction
          WHEN 'CREDIT' THEN item.expected_target_amount - item.original_target_amount
          WHEN 'DEBIT' THEN item.original_target_amount - item.expected_target_amount
      END
   OR item.corrected_projection_amount <>
      item.original_projection_amount + item.signed_delta_amount;

-- 7. 执行终态与逐笔投影任务状态必须一致。
SELECT COUNT(*) AS invalid_principal_correction_projection_state_count
FROM settlement_principal_correction_item item
JOIN settlement_principal_correction_order correction
  ON correction.correction_no = item.correction_no
WHERE (correction.correction_status = 'PENDING_REVIEW'
       AND item.projection_status <> 'WAITING_REVIEW')
   OR (correction.correction_status = 'REJECTED'
       AND item.projection_status <> 'CANCELLED')
   OR (correction.correction_status = 'EXECUTED'
       AND item.projection_status NOT IN ('INIT', 'PROCESSING', 'FAILED', 'COMPLETED'));
