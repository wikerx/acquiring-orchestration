-- 结算 Phase A 只读后检草案，版本：1.0.0-SNAPSHOT。
-- 本文件没有 DDL 或数据写操作；所有 missing/invalid/duplicate 结果必须为 0。

-- 商户档案、Phase A 七张批次表和清分交接候选表必须完整。
SELECT 9 - COUNT(*) AS missing_phase_a_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'settlement_batch_daily_sequence',
      'merchant_settlement_profile',
      'settlement_batch',
      'settlement_candidate',
      'settlement_candidate_dependency',
      'settlement_batch_candidate',
      'settlement_batch_rate',
      'settlement_result_item',
      'settlement_result_summary'
  );

-- 所有具体时间点必须保留毫秒精度。
SELECT COUNT(*) AS invalid_datetime_precision_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN (
      'merchant_settlement_profile', 'settlement_batch_daily_sequence', 'settlement_batch',
      'settlement_candidate_dependency', 'settlement_batch_candidate',
      'settlement_batch_rate', 'settlement_result_item', 'settlement_result_summary'
  )
  AND data_type = 'datetime'
  AND datetime_precision <> 3;

-- 资金幂等和批次唯一索引必须全部存在。
SELECT 9 - COUNT(*) AS missing_required_unique_index_count
FROM (
    SELECT table_name, index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND non_unique = 0
      AND (table_name, index_name) IN (
          ('settlement_batch', 'uk_settlement_batch_no'),
          ('settlement_batch', 'uk_settlement_create_request'),
          ('settlement_batch', 'uk_settlement_business_sequence'),
          ('settlement_candidate_dependency', 'uk_settlement_candidate_dependency'),
          ('settlement_batch_candidate', 'uk_settlement_batch_candidate_no'),
          ('settlement_batch_candidate', 'uk_settlement_batch_candidate_pair'),
          ('settlement_batch_rate', 'uk_settlement_batch_currency_rate'),
          ('settlement_result_item', 'uk_settlement_result_item_no'),
          ('settlement_result_item', 'uk_settlement_result_ledger_idempotency')
      )
    GROUP BY table_name, index_name
) required_unique_indexes;

-- 批次汇率必须为正数且同币种只能使用系统恒等汇率。
SELECT COUNT(*) AS invalid_locked_rate_count
FROM settlement_batch_rate
WHERE direct_rate <= 0
   OR rate_status <> 'LOCKED'
   OR (source_currency = target_currency
       AND (direct_rate <> 1 OR rate_source <> 'SYSTEM_IDENTITY'));

-- READY/SUPERSEDED 不得绑定批次，已认领状态必须绑定批次，影子候选不得被认领。
SELECT COUNT(*) AS invalid_candidate_claim_count
FROM settlement_candidate
WHERE (candidate_status IN ('READY', 'SUPERSEDED') AND settlement_batch_no IS NOT NULL)
   OR (candidate_status NOT IN ('READY', 'SUPERSEDED') AND settlement_batch_no IS NULL)
   OR (candidate_status = 'CLAIMED' AND shadow_mode <> 0);

-- 已认领或已入账候选必须有同批次审计关系。
SELECT COUNT(*) AS missing_claim_relation_count
FROM settlement_candidate candidate
LEFT JOIN settlement_batch_candidate relation
  ON relation.candidate_id = candidate.id
 AND relation.settlement_batch_no = candidate.settlement_batch_no
 AND relation.relation_status IN ('CLAIMED', 'POSTED', 'MANUAL_REVIEW')
WHERE candidate.candidate_status IN ('CLAIMED', 'POSTED', 'MANUAL_REVIEW')
  AND relation.id IS NULL;

-- 一个候选只能存在一个未释放的批次关系。
SELECT COUNT(*) AS duplicate_active_candidate_relation_count
FROM (
    SELECT candidate_id
    FROM settlement_batch_candidate
    WHERE relation_status IN ('CLAIMED', 'POSTED', 'MANUAL_REVIEW')
    GROUP BY candidate_id
    HAVING COUNT(*) > 1
) duplicate_relations;

-- Phase A 禁止资金化：该查询只展示现有结算资金流水基数，不据此更新、删除或补写数据。
SELECT COUNT(*) AS existing_settlement_ledger_count
FROM merchant_fund_ledger
WHERE business_type IN ('TRANSACTION_SETTLEMENT', 'RESERVE_SETTLEMENT');
