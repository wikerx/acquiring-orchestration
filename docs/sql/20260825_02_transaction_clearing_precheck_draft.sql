-- 收单交易清分第三阶段迁移包 1/4：只读前检草案。
-- 状态：仅供评审，禁止未经 DBA 审批直接在 UAT/生产执行。
-- 约束：本文件只允许 SELECT，不修改表结构和数据；任一阻断查询返回记录时停止后续迁移。
-- 来源：20260825_01_transaction_clearing_schema_draft.sql 的只读门禁，拆分后仍以总设计草案解释字段口径。

-- ============================================================================
-- 1. 执行前只读核对
-- ============================================================================

-- finance state 应按动作级 transaction_id 唯一；唯一索引覆盖软删除行，以下查询不能过滤 deleted。
-- 三条查询必须均返回 0 行。
SELECT transaction_id, COUNT(*) AS duplicate_count
FROM transaction_finance_state
GROUP BY transaction_id
HAVING COUNT(*) > 1;

SELECT transaction_id, COUNT(*) AS duplicate_count
FROM transaction_finance_state_202603
GROUP BY transaction_id
HAVING COUNT(*) > 1;

SELECT transaction_id, COUNT(*) AS duplicate_count
FROM transaction_finance_state_202604
GROUP BY transaction_id
HAVING COUNT(*) > 1;

-- 动作费用快照按 transaction_id + transaction_date_time 唯一；以下三条查询必须均返回 0 行，
-- 否则不能创建动作快照幂等唯一索引，也不能部署自动清分服务。
SELECT transaction_id, transaction_date_time, COUNT(*) AS duplicate_count
FROM transaction_merchant_snapshot
GROUP BY transaction_id, transaction_date_time
HAVING COUNT(*) > 1;

SELECT transaction_id, transaction_date_time, COUNT(*) AS duplicate_count
FROM transaction_merchant_snapshot_202603
GROUP BY transaction_id, transaction_date_time
HAVING COUNT(*) > 1;

SELECT transaction_id, transaction_date_time, COUNT(*) AS duplicate_count
FROM transaction_merchant_snapshot_202604
GROUP BY transaction_id, transaction_date_time
HAVING COUNT(*) > 1;

-- 当前 operation_id 是生命周期关联号，允许同一生命周期包含多个动作。
-- 若 finance state 已有数据，先核对同一 operation_id 的记录是否属于不同 transaction_id。
SELECT operation_id, COUNT(DISTINCT transaction_id) AS action_count
FROM transaction_finance_state_202603
WHERE deleted = 0
GROUP BY operation_id
HAVING COUNT(DISTINCT transaction_id) > 1;

-- 新表名必须均不存在；若返回记录，停止迁移并先确认是否为失败残留或其它版本。
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'fee_tier_accumulator',
      'transaction_clearing_detail',
      'transaction_clearing_detail_202603',
      'transaction_clearing_detail_202604',
      'transaction_reserve_clearing_detail',
      'transaction_reserve_clearing_detail_202603',
      'transaction_reserve_clearing_detail_202604',
      'transaction_reserve_clearing_state',
      'transaction_reserve_clearing_state_202603',
      'transaction_reserve_clearing_state_202604',
      'clearing_reserve_adjustment',
      'clearing_tier_period_replay',
      'clearing_tier_period_replay_item',
      'settlement_candidate'
  );

SELECT operation_id, COUNT(DISTINCT transaction_id) AS action_count
FROM transaction_finance_state_202604
WHERE deleted = 0
GROUP BY operation_id
HAVING COUNT(DISTINCT transaction_id) > 1;

-- 现有平台费和保证金汇总按绝对值保存；以下计数必须均为0。
SELECT 'template' AS shard_name, COUNT(*) AS negative_summary_count
FROM transaction_finance_state
WHERE platform_fee_amount < 0 OR reserve_amount < 0
UNION ALL
SELECT '202603', COUNT(*)
FROM transaction_finance_state_202603
WHERE platform_fee_amount < 0 OR reserve_amount < 0
UNION ALL
SELECT '202604', COUNT(*)
FROM transaction_finance_state_202604
WHERE platform_fee_amount < 0 OR reserve_amount < 0;

-- ============================================================================
-- 2. 商户生效费用配置只读核对；禁止改变现有模板和商户配置口径
-- ============================================================================

-- 清分只允许使用商户方案当前已生效且不可变的版本。以下查询命中时必须停止灰度并修复配置，
-- 不能回退平台默认费率，也不能临时读取模板当前版本替代商户已冻结版本。
SELECT fp.id AS fee_plan_id,
       fp.merchant_id,
       fp.current_version_id,
       fp.current_version_no,
       fpv.version_status,
       fpv.effective_time,
       fpv.settlement_currency
FROM fee_plan fp
LEFT JOIN fee_plan_version fpv
       ON fpv.id = fp.current_version_id
      AND fpv.plan_id = fp.id
      AND fpv.deleted = 0
WHERE fp.plan_type = 'MERCHANT'
  AND fp.deleted = 0
  AND (
      fp.merchant_id IS NULL
      OR fp.merchant_id = ''
      OR fp.status <> 'ENABLED'
      OR fp.current_version_id IS NULL
      OR fp.current_version_no IS NULL
      OR fpv.id IS NULL
      OR fpv.version_status <> 'ACTIVE'
      OR fpv.version_no <> fp.current_version_no
      OR fpv.effective_time IS NULL
      OR fpv.settlement_currency IS NULL
  );

-- 现有费用配置的币种合同保持不变：百分比按动作标签币种计算，固定费、最低费和最高费为 USD。
-- 本草案不向 fee_plan_version、fee_rule、fee_rule_tier 或 Admin 试算表增加任何币种配置字段。
SELECT 'fee_rule' AS source_table,
       fr.id AS source_id,
       fr.plan_version_id,
       fr.fixed_amount_usd,
       fr.minimum_amount_usd,
       fr.maximum_amount_usd
FROM fee_rule fr
WHERE fr.deleted = 0
  AND (
      fr.fixed_amount_usd IS NULL
      OR fr.fixed_amount_usd < 0
      OR (fr.minimum_amount_usd IS NOT NULL AND fr.minimum_amount_usd < 0)
      OR (fr.maximum_amount_usd IS NOT NULL AND fr.maximum_amount_usd < 0)
      OR (fr.minimum_amount_usd IS NOT NULL
          AND fr.maximum_amount_usd IS NOT NULL
          AND fr.minimum_amount_usd > fr.maximum_amount_usd)
  )
UNION ALL
SELECT 'fee_rule_tier',
       frt.id,
       fr.plan_version_id,
       frt.fixed_amount_usd,
       frt.minimum_amount_usd,
       frt.maximum_amount_usd
FROM fee_rule_tier frt
JOIN fee_rule fr
  ON fr.id = frt.fee_rule_id
 AND fr.deleted = 0
WHERE frt.deleted = 0
  AND (
      frt.fixed_amount_usd IS NULL
      OR frt.fixed_amount_usd < 0
      OR (frt.minimum_amount_usd IS NOT NULL AND frt.minimum_amount_usd < 0)
      OR (frt.maximum_amount_usd IS NOT NULL AND frt.maximum_amount_usd < 0)
      OR (frt.minimum_amount_usd IS NOT NULL
          AND frt.maximum_amount_usd IS NOT NULL
          AND frt.minimum_amount_usd > frt.maximum_amount_usd)
  );
