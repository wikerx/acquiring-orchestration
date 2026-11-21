-- 结算资金提交阶段执行前只读检查，版本：1.0.0-SNAPSHOT。
-- 所有 *_count 结果必须为 0；本文件不包含任何 DDL 或数据修复。

SELECT 12 - COUNT(*) AS missing_prerequisite_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'merchant_fund_account', 'merchant_fund_ledger', 'merchant_reserve_item',
      'settlement_candidate', 'merchant_settlement_profile', 'settlement_batch',
      'settlement_batch_candidate', 'settlement_batch_rate', 'settlement_result_item',
      'settlement_result_summary', 'transaction_finance_state', 'transaction_operation'
  );

SELECT COUNT(*) AS invalid_fund_account_count
FROM merchant_fund_account
WHERE deleted = 0
  AND (merchant_id IS NULL OR settlement_currency IS NULL
       OR account_version < 0 OR available_balance IS NULL);

SELECT COUNT(*) AS duplicate_ledger_idempotency_count
FROM (
    SELECT idempotency_key
    FROM merchant_fund_ledger
    GROUP BY idempotency_key
    HAVING COUNT(*) > 1
) duplicate_ledger;

SELECT COUNT(*) AS invalid_reserve_amount_count
FROM merchant_reserve_item
WHERE retained_amount <= 0
   OR released_amount < 0
   OR released_amount > retained_amount;

SELECT COUNT(*) AS duplicate_active_profile_source_count
FROM (
    SELECT merchant_id
    FROM merchant_fund_account
    WHERE deleted = 0 AND account_status = 'NORMAL'
    GROUP BY merchant_id
    HAVING COUNT(*) > 1
) duplicate_profile_source;
