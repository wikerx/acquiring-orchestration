-- 结算资金提交阶段执行后只读检查，版本：1.0.0-SNAPSHOT。

SELECT 4 - COUNT(*) AS missing_posting_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'merchant_reserve_action', 'settlement_projection_task',
      'settlement_event_outbox', 'merchant_settlement_profile'
  );

SELECT 3 - COUNT(*) AS missing_reserve_extension_column_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'merchant_reserve_item'
  AND column_name IN ('returned_amount', 'reversed_amount', 'version');

SELECT 2 - COUNT(DISTINCT index_name) AS missing_posting_unique_index_count
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND ((table_name = 'settlement_batch' AND index_name = 'uk_settlement_single_reversal')
       OR (table_name = 'settlement_result_item'
           AND index_name = 'uk_settlement_one_ledger_posting'));

SELECT COUNT(*) AS invalid_reserve_aggregate_count
FROM merchant_reserve_item
WHERE retained_amount <= 0
   OR returned_amount < 0
   OR released_amount < 0
   OR reversed_amount < 0
   OR returned_amount + released_amount + reversed_amount > retained_amount;

SELECT COUNT(*) AS normal_account_without_active_profile_count
FROM merchant_fund_account account
LEFT JOIN merchant_settlement_profile profile
       ON profile.settlement_account_id = account.id
      AND profile.merchant_id = account.merchant_id
      AND profile.target_currency = account.settlement_currency
      AND profile.profile_status = 'ACTIVE'
      AND profile.active_slot = 1
WHERE account.deleted = 0
  AND account.account_status = 'NORMAL'
  AND profile.id IS NULL;

SELECT COUNT(*) AS duplicate_batch_ledger_posting_count
FROM (
    SELECT settlement_batch_no
    FROM settlement_result_item
    WHERE result_role = 'LEDGER_POSTING'
    GROUP BY settlement_batch_no
    HAVING COUNT(*) > 1
) duplicate_posting;

SELECT COUNT(*) AS orphan_settlement_ledger_count
FROM merchant_fund_ledger ledger
LEFT JOIN settlement_batch batch ON batch.settlement_batch_no = ledger.settlement_batch_no
WHERE ledger.business_type IN ('TRANSACTION_SETTLEMENT', 'RESERVE_SETTLEMENT')
  AND ledger.settlement_batch_no IS NOT NULL
  AND batch.id IS NULL;
