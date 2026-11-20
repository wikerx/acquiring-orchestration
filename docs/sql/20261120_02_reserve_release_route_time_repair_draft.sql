-- 保证金释放候选路由时间修复；仅适用于当前已登记 202603/202604/202701 物理节点。
-- 2026-11-20 本地只读基线：70 个 MANUAL_REVIEW 批次、53190 个候选；其中 27793 个路由时间比唯一 ACTIVE RELEASE 事实早 1ms。
-- 修复仅更正候选来源时刻指针，不改事实、金额、币种、状态、候选号、幂等键、依赖和批次认领关系，不触发重试或入账。
-- 执行前重新核对节点清单、完整只读检查、无并发恢复、审批数量和备份；拓扑或数量变化时停止，不扩大匹配范围。
-- 本模板默认关闭提交，任何新执行均须独立批准；默认回滚所有 DML。
-- 2026-11-20 本地已批准执行：RRT-20261120T030042Z，27793 条指针已更正；70 个批次仍为人工复核。
-- 执行后检查见 20261120_04_reserve_release_route_time_postcheck.sql；数据库审计表保留全部旧值和版本。
-- 获得明确批准后填入独立 run_no，并确认 expected_rows/expected_batches 与最新 precheck 一致。
-- 业务批次仍保持人工复核；后续恢复必须走既有服务校验和授权流程。
SET @repair_run_no = 'UNAPPROVED-20261120';
SET @expected_rows = 27793;
SET @expected_batches = 70;
SET @repair_approved = 0;

CREATE TABLE IF NOT EXISTS ops_reserve_release_route_time_audit (
    run_no VARCHAR(64) NOT NULL COMMENT '独立审批修复批号',
    candidate_id BIGINT NOT NULL COMMENT '候选主键',
    candidate_no VARCHAR(64) NOT NULL COMMENT '稳定候选号',
    settlement_batch_no VARCHAR(19) NOT NULL COMMENT '冻结的人工复核批次',
    candidate_version BIGINT NOT NULL COMMENT '修复前候选CAS版本',
    batch_version BIGINT NOT NULL COMMENT '审核冻结批次版本',
    old_time DATETIME(3) NOT NULL COMMENT '修复前候选来源路由时刻',
    new_time DATETIME(3) NOT NULL COMMENT '唯一ACTIVE释放事实的精确路由时刻',
    saved_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '备份时间',
    PRIMARY KEY (run_no, candidate_id)
) ENGINE=InnoDB COMMENT='候选路由时间指针修复备份；不保存金额或支付资料';

START TRANSACTION;
-- 锁定当前失败批次，阻止检查与更新期间被恢复或取消。
SELECT b.id FROM settlement_batch b
WHERE b.batch_type = 'RESERVE_RELEASE' AND b.batch_status = 'MANUAL_REVIEW'
  AND b.last_failure_stage = 'FACT_LOADING' AND b.last_failure_code = 'SETTLEMENT_CLEARING_FACT_MISSING'
ORDER BY b.id FOR UPDATE;

INSERT INTO ops_reserve_release_route_time_audit
(run_no, candidate_id, candidate_no, settlement_batch_no, candidate_version, batch_version, old_time, new_time)
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
SELECT @repair_run_no, candidate_id, candidate_no, settlement_batch_no, candidate_version, batch_version, old_time, new_time
FROM repair_targets WHERE @repair_approved = 1;

SET @backup_rows = (SELECT COUNT(*) FROM ops_reserve_release_route_time_audit WHERE run_no = @repair_run_no);
SET @backup_batches = (SELECT COUNT(DISTINCT settlement_batch_no) FROM ops_reserve_release_route_time_audit WHERE run_no = @repair_run_no);
UPDATE settlement_candidate c
JOIN ops_reserve_release_route_time_audit a ON a.candidate_id = c.id AND a.run_no = @repair_run_no
JOIN settlement_batch b ON b.settlement_batch_no = a.settlement_batch_no
SET c.source_transaction_date_time = a.new_time, c.version = c.version + 1
WHERE @repair_approved = 1 AND @backup_rows = @expected_rows AND @backup_batches = @expected_batches
  AND c.candidate_no = a.candidate_no AND c.version = a.candidate_version
  AND c.source_transaction_date_time = a.old_time AND c.settlement_batch_no = a.settlement_batch_no
  AND c.source_type = 'RESERVE_RELEASE' AND c.candidate_status = 'MANUAL_REVIEW'
  AND b.version = a.batch_version AND b.batch_status = 'MANUAL_REVIEW'
  AND b.last_failure_code = 'SETTLEMENT_CLEARING_FACT_MISSING' AND b.last_failure_stage = 'FACT_LOADING'
  AND b.result_fingerprint IS NULL AND b.rate_locked_time IS NULL AND b.posted_time IS NULL
  AND NOT EXISTS (SELECT 1 FROM settlement_result_item i WHERE i.settlement_batch_no = b.settlement_batch_no)
  AND NOT EXISTS (SELECT 1 FROM merchant_fund_ledger l WHERE l.settlement_batch_no = b.settlement_batch_no);
SET @changed_rows = ROW_COUNT();
SELECT @repair_approved AS approved, @backup_rows AS backup_rows, @backup_batches AS backup_batches,
       @changed_rows AS changed_rows, @expected_rows AS expected_rows;
-- 审核执行者只可在 changed_rows = backup_rows = expected_rows 且批次数一致后将下行改为 COMMIT。
-- 任意 CAS 未命中、结果行/资金流水出现或状态变化均必须整笔回滚。
ROLLBACK;
