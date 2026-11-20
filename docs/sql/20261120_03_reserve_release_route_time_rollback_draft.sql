-- 保证金释放候选路由时间修复；仅适用于当前已登记 202603/202604/202701 物理节点。
-- 2026-11-20 本地只读基线：70 个 MANUAL_REVIEW 批次、53190 个候选；其中 27793 个路由时间比唯一 ACTIVE RELEASE 事实早 1ms。
-- 修复仅更正候选来源时刻指针，不改事实、金额、币种、状态、候选号、幂等键、依赖和批次认领关系，不触发重试或入账。
-- 执行前重新核对节点清单、完整只读检查、无并发恢复、审批数量和备份；拓扑或数量变化时停止，不扩大匹配范围。
-- 仅适用于修复后尚未推进状态/版本、没有任何结果/流水的原批次；默认回滚演练。
-- 审批后填写原 run_no；不得覆盖修复后新增的业务变化，不删除审计备份。
SET @repair_run_no = 'UNAPPROVED-20261120';
START TRANSACTION;
SELECT b.id FROM settlement_batch b
JOIN (SELECT DISTINCT settlement_batch_no FROM ops_reserve_release_route_time_audit WHERE run_no = @repair_run_no) a
  ON a.settlement_batch_no = b.settlement_batch_no ORDER BY b.id FOR UPDATE;
UPDATE settlement_candidate c
JOIN ops_reserve_release_route_time_audit a ON a.candidate_id = c.id AND a.run_no = @repair_run_no
JOIN settlement_batch b ON b.settlement_batch_no = a.settlement_batch_no
SET c.source_transaction_date_time = a.old_time, c.version = c.version + 1
WHERE c.version = a.candidate_version + 1 AND c.candidate_no = a.candidate_no
  AND c.source_transaction_date_time = a.new_time AND c.settlement_batch_no = a.settlement_batch_no
  AND c.source_type = 'RESERVE_RELEASE' AND c.candidate_status = 'MANUAL_REVIEW'
  AND b.version = a.batch_version AND b.batch_status = 'MANUAL_REVIEW'
  AND b.last_failure_code = 'SETTLEMENT_CLEARING_FACT_MISSING' AND b.last_failure_stage = 'FACT_LOADING'
  AND b.result_fingerprint IS NULL AND b.rate_locked_time IS NULL AND b.posted_time IS NULL
  AND NOT EXISTS (SELECT 1 FROM settlement_result_item i WHERE i.settlement_batch_no = b.settlement_batch_no)
  AND NOT EXISTS (SELECT 1 FROM merchant_fund_ledger l WHERE l.settlement_batch_no = b.settlement_batch_no);
SELECT ROW_COUNT() AS rollback_rows,
  (SELECT COUNT(*) FROM ops_reserve_release_route_time_audit WHERE run_no = @repair_run_no) AS expected_rows;
-- 两个计数不相等则停止；禁止部分回滚或回退 CAS 版本。
ROLLBACK;
