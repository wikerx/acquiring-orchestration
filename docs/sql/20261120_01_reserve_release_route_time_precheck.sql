-- 保证金释放候选路由时间修复；仅适用于当前已登记 202603/202604/202701 物理节点。
-- 2026-11-20 本地只读基线：70 个 MANUAL_REVIEW 批次、53190 个候选；其中 27793 个路由时间比唯一 ACTIVE RELEASE 事实早 1ms。
-- 修复仅更正候选来源时刻指针，不改事实、金额、币种、状态、候选号、幂等键、依赖和批次认领关系，不触发重试或入账。
-- 执行前重新核对节点清单、完整只读检查、无并发恢复、审批数量和备份；拓扑或数量变化时停止，不扩大匹配范围。
-- 本脚本只读。
WITH release_facts AS (
SELECT transaction_id, clearing_revision, finance_state_id, merchant_id, transaction_date_time
FROM transaction_reserve_clearing_detail_202603
WHERE record_status = 'ACTIVE' AND reserve_action_type = 'RELEASE'
UNION ALL
SELECT transaction_id, clearing_revision, finance_state_id, merchant_id, transaction_date_time
FROM transaction_reserve_clearing_detail_202604
WHERE record_status = 'ACTIVE' AND reserve_action_type = 'RELEASE'
UNION ALL
SELECT transaction_id, clearing_revision, finance_state_id, merchant_id, transaction_date_time
FROM transaction_reserve_clearing_detail_202701
WHERE record_status = 'ACTIVE' AND reserve_action_type = 'RELEASE'
), unique_facts AS (
    SELECT transaction_id, clearing_revision, MIN(finance_state_id) AS finance_state_id,
           MIN(merchant_id) AS merchant_id, MIN(transaction_date_time) AS fact_time
    FROM release_facts GROUP BY transaction_id, clearing_revision HAVING COUNT(*) = 1
), repair_targets AS (
    SELECT c.id AS candidate_id, c.candidate_no, c.settlement_batch_no, c.version AS candidate_version,
           b.version AS batch_version, c.source_transaction_date_time AS old_time, f.fact_time AS new_time
    FROM settlement_candidate c
    JOIN unique_facts f ON f.transaction_id = c.source_transaction_id
      AND f.clearing_revision = c.source_revision AND f.finance_state_id = c.source_business_id
      AND f.merchant_id = c.merchant_id
    JOIN settlement_batch b ON b.settlement_batch_no = c.settlement_batch_no
    WHERE c.source_type = 'RESERVE_RELEASE' AND c.candidate_status = 'MANUAL_REVIEW'
      AND b.batch_type = 'RESERVE_RELEASE' AND b.batch_status = 'MANUAL_REVIEW'
      AND b.last_failure_stage = 'FACT_LOADING'
      AND b.last_failure_code = 'SETTLEMENT_CLEARING_FACT_MISSING'
      AND b.result_fingerprint IS NULL AND b.rate_locked_time IS NULL AND b.posted_time IS NULL
      AND TIMESTAMPDIFF(MICROSECOND, c.source_transaction_date_time, f.fact_time) = 1000
      AND EXISTS (SELECT 1 FROM settlement_batch_candidate rel
          WHERE rel.candidate_id = c.id AND rel.settlement_batch_no = b.settlement_batch_no
            AND rel.relation_status = 'MANUAL_REVIEW'
            AND rel.source_type = c.source_type AND rel.source_business_id = c.source_business_id
            AND rel.source_revision = c.source_revision)
      AND NOT EXISTS (SELECT 1 FROM settlement_result_item i WHERE i.settlement_batch_no = b.settlement_batch_no)
      AND NOT EXISTS (SELECT 1 FROM merchant_fund_ledger l WHERE l.settlement_batch_no = b.settlement_batch_no)
)
SELECT COUNT(*) AS repairable_candidates, COUNT(DISTINCT settlement_batch_no) AS affected_batches,
       MIN(old_time) AS first_old_time, MAX(old_time) AS last_old_time
FROM repair_targets;
