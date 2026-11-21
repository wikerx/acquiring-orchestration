-- 结算 Phase A 只读前检草案，版本：1.0.0-SNAPSHOT。
-- 本文件没有 DDL 或数据写操作；任一缺失数或异常数非 0 时停止迁移。

-- 清分交接前置表必须完整。
SELECT 4 - COUNT(*) AS missing_prerequisite_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'settlement_candidate',
      'transaction_clearing_detail',
      'transaction_reserve_clearing_detail',
      'transaction_reserve_clearing_state'
  );

-- settlement_candidate 的 Phase A 必需列必须完整。
SELECT 13 - COUNT(*) AS missing_candidate_column_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'settlement_candidate'
  AND column_name IN (
      'id', 'candidate_no', 'source_type', 'source_business_id', 'source_revision',
      'merchant_id', 'settlement_profile_id', 'target_currency', 'target_currency_exponent',
      'settlement_eligible_date', 'candidate_status', 'shadow_mode', 'version'
  );

-- 影子候选不允许绑定真实配置，真实候选必须绑定配置。
SELECT COUNT(*) AS invalid_candidate_mode_count
FROM settlement_candidate
WHERE (shadow_mode = 1 AND settlement_profile_id IS NOT NULL)
   OR (shadow_mode = 0 AND settlement_profile_id IS NULL);

-- 清分服务当前只允许生成影子候选；该结果用于审批真实候选激活方案，不在本阶段直接更新。
SELECT shadow_mode, candidate_status, COUNT(*) AS candidate_count
FROM settlement_candidate
GROUP BY shadow_mode, candidate_status
ORDER BY shadow_mode, candidate_status;
