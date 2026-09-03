-- 结算 P0 一致性迁移后只读检查。所有 *_count 结果必须为 0。

SELECT 4 - COUNT(*) AS missing_reserve_adjustment_column_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'merchant_reserve_item'
        AND column_name IN ('debit_adjustment_amount', 'credit_adjustment_amount'))
       OR (table_name = 'merchant_reserve_action'
           AND column_name IN ('direction', 'reversal_of_action_id')));

SELECT 4 - COUNT(*) AS missing_settlement_p0_constraint_count
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_type = 'CHECK'
  AND enforced = 'YES'
  AND ((table_name = 'merchant_reserve_item'
        AND constraint_name IN ('chk_reserve_amount', 'chk_reserve_status'))
       OR (table_name = 'merchant_reserve_action'
           AND constraint_name = 'chk_reserve_action_value')
       OR (table_name = 'settlement_batch'
           AND constraint_name = 'chk_settlement_batch_enum'));

SELECT 1 - COUNT(DISTINCT index_name) AS missing_reserve_reversal_unique_index_count
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'merchant_reserve_action'
  AND index_name = 'uk_reserve_action_reversal_ref'
  AND non_unique = 0
  AND seq_in_index = 1
  AND column_name = 'reversal_of_action_id';

SELECT COUNT(*) AS invalid_reserve_responsibility_count
FROM merchant_reserve_item
WHERE retained_amount <= 0
   OR debit_adjustment_amount < 0
   OR credit_adjustment_amount < 0
   OR returned_amount < 0
   OR released_amount < 0
   OR reversed_amount < 0
   OR returned_amount + released_amount + credit_adjustment_amount + reversed_amount
        > retained_amount + debit_adjustment_amount;

SELECT COUNT(*) AS invalid_reserve_status_count
FROM merchant_reserve_item
WHERE reserve_status NOT IN (
    'HELD', 'PARTIALLY_RETURNED', 'RETURNED', 'RELEASABLE', 'RELEASED',
    'FROZEN', 'DEDUCTED', 'REVERSED', 'ADJUSTED'
);

SELECT COUNT(*) AS invalid_reserve_action_count
FROM merchant_reserve_action
WHERE amount <= 0
   OR direction NOT IN ('DEBIT', 'CREDIT')
   OR action_type NOT IN (
       'HOLD', 'RETURN', 'RELEASE', 'ADJUSTMENT',
       'REVERSAL_HOLD', 'REVERSAL_RETURN', 'REVERSAL_RELEASE', 'REVERSAL_ADJUSTMENT'
   )
   OR (action_type IN ('HOLD', 'RETURN', 'RELEASE', 'ADJUSTMENT')
       AND reversal_of_action_id IS NOT NULL)
   OR (action_type IN (
          'REVERSAL_HOLD', 'REVERSAL_RETURN', 'REVERSAL_RELEASE', 'REVERSAL_ADJUSTMENT'
       ) AND reversal_of_action_id IS NULL)
   OR (action_type = 'HOLD' AND direction <> 'DEBIT')
   OR (action_type IN ('RETURN', 'RELEASE') AND direction <> 'CREDIT')
   OR (action_type = 'REVERSAL_HOLD' AND direction <> 'CREDIT')
   OR (action_type IN ('REVERSAL_RETURN', 'REVERSAL_RELEASE') AND direction <> 'DEBIT');

SELECT COUNT(*) AS duplicate_reserve_action_reversal_count
FROM (
    SELECT reversal_of_action_id
    FROM merchant_reserve_action
    WHERE reversal_of_action_id IS NOT NULL
    GROUP BY reversal_of_action_id
    HAVING COUNT(*) > 1
) duplicate_reversal;

SELECT COUNT(*) AS invalid_reserve_reversal_reference_count
FROM merchant_reserve_action reversal_action
LEFT JOIN merchant_reserve_action original_action
  ON original_action.id = reversal_action.reversal_of_action_id
WHERE reversal_action.action_type IN (
          'REVERSAL_HOLD', 'REVERSAL_RETURN', 'REVERSAL_RELEASE', 'REVERSAL_ADJUSTMENT'
      )
  AND (original_action.id IS NULL
       OR reversal_action.action_type <> CONCAT('REVERSAL_', original_action.action_type)
       OR NOT ((reversal_action.direction = 'DEBIT' AND original_action.direction = 'CREDIT')
               OR (reversal_action.direction = 'CREDIT' AND original_action.direction = 'DEBIT')));

SELECT COUNT(*) AS invalid_settlement_batch_reference_count
FROM settlement_batch
WHERE (batch_type = 'REVERSAL' AND original_batch_no IS NULL)
   OR (batch_type IN ('REGULAR', 'RESERVE_RELEASE', 'ADJUSTMENT')
       AND original_batch_no IS NOT NULL);

-- SETTLE 任务关联本批真实交易候选；REVERSE 任务关联被冲正原批真实交易候选。
SELECT COUNT(*) AS non_transaction_projection_task_count
FROM settlement_projection_task task
LEFT JOIN settlement_batch_candidate relation_row
  ON relation_row.settlement_batch_no = COALESCE(task.original_batch_no, task.settlement_batch_no)
 AND relation_row.candidate_id = task.candidate_id
WHERE relation_row.candidate_id IS NULL
   OR relation_row.source_type <> 'CLEARING_REVISION';
