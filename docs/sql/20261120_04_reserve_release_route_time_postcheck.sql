-- 本地已批准执行 RRT-20261120T030042Z 的只读后检，不触发重试、状态推进或入账。
-- 执行后基线：27793 条审计、70 个批次、0 条未对齐指针、0 条结果、0 条资金流水。
-- 版本和人工复核状态检查适用于批次尚未经过后续独立授权的恢复操作时。
SET @repair_run_no = 'RRT-20261120T030042Z';

SELECT COUNT(*) AS audit_rows,
       COUNT(DISTINCT a.settlement_batch_no) AS affected_batches,
       SUM(TIMESTAMPDIFF(MICROSECOND, a.old_time, a.new_time) = 1000) AS exact_one_ms_rows,
       SUM(c.source_transaction_date_time = a.new_time
           AND c.version = a.candidate_version + 1
           AND c.candidate_status = 'MANUAL_REVIEW') AS aligned_and_unchanged_status_rows
FROM ops_reserve_release_route_time_audit a
JOIN settlement_candidate c ON c.id = a.candidate_id
WHERE a.run_no = @repair_run_no;

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
), fact_counts AS (
    SELECT a.candidate_id, COUNT(f.transaction_id) AS fact_count
    FROM ops_reserve_release_route_time_audit a
    JOIN settlement_candidate c ON c.id = a.candidate_id
    LEFT JOIN release_facts f ON f.transaction_id = c.source_transaction_id
      AND f.clearing_revision = c.source_revision
      AND f.finance_state_id = c.source_business_id AND f.merchant_id = c.merchant_id
      AND f.transaction_date_time = c.source_transaction_date_time
    WHERE a.run_no = @repair_run_no
    GROUP BY a.candidate_id
)
SELECT COUNT(*) AS checked_pointers,
       SUM(fact_count <> 1) AS missing_or_ambiguous_exact_fact_pointers
FROM fact_counts;

SELECT b.batch_status, b.last_failure_stage, b.last_failure_code,
       COUNT(*) AS batch_count,
       SUM(b.version <> a.batch_version) AS changed_batch_versions,
       SUM(b.retry_count) AS retry_count,
       SUM(b.result_fingerprint IS NOT NULL) AS result_fingerprints,
       SUM(b.rate_locked_time IS NOT NULL) AS locked_rate_batches,
       SUM(b.posted_time IS NOT NULL) AS posted_batches
FROM settlement_batch b
JOIN (
    SELECT settlement_batch_no, MIN(batch_version) AS batch_version
    FROM ops_reserve_release_route_time_audit
    WHERE run_no = @repair_run_no
    GROUP BY settlement_batch_no
) a ON a.settlement_batch_no = b.settlement_batch_no
GROUP BY b.batch_status, b.last_failure_stage, b.last_failure_code;

SELECT COUNT(*) AS result_item_rows
FROM settlement_result_item i
WHERE EXISTS (
    SELECT 1 FROM ops_reserve_release_route_time_audit a
    WHERE a.run_no = @repair_run_no AND a.settlement_batch_no = i.settlement_batch_no
);

SELECT COUNT(*) AS ledger_rows
FROM merchant_fund_ledger l
WHERE EXISTS (
    SELECT 1 FROM ops_reserve_release_route_time_audit a
    WHERE a.run_no = @repair_run_no AND a.settlement_batch_no = l.settlement_batch_no
);
