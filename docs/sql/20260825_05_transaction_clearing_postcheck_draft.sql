-- 收单交易清分第三阶段迁移包 4/4：发布前只读核验草案。
-- 状态：仅供评审，禁止把查询结果未核清的季度加入 ShardingSphere physical-nodes。
-- 结果要求：missing_table_count、invalid_*_count 均为 0；模板与两个活动季度的结构计数必须分别一致。
-- 边界：本文件只读，不发布 Nacos，不创建 RocketMQ Topic，不启动清分消费者。

-- 1. 本阶段应存在 5 张固定表和 3 组模板/活动季度表，共 14 张表。
SELECT 14 - COUNT(*) AS missing_table_count
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

-- 2. 精确列出缺失表；结果必须为空。
SELECT expected.table_name AS missing_table
FROM (
    SELECT 'fee_tier_accumulator' AS table_name
    UNION ALL SELECT 'transaction_clearing_detail'
    UNION ALL SELECT 'transaction_clearing_detail_202603'
    UNION ALL SELECT 'transaction_clearing_detail_202604'
    UNION ALL SELECT 'transaction_reserve_clearing_detail'
    UNION ALL SELECT 'transaction_reserve_clearing_detail_202603'
    UNION ALL SELECT 'transaction_reserve_clearing_detail_202604'
    UNION ALL SELECT 'transaction_reserve_clearing_state'
    UNION ALL SELECT 'transaction_reserve_clearing_state_202603'
    UNION ALL SELECT 'transaction_reserve_clearing_state_202604'
    UNION ALL SELECT 'clearing_reserve_adjustment'
    UNION ALL SELECT 'clearing_tier_period_replay'
    UNION ALL SELECT 'clearing_tier_period_replay_item'
    UNION ALL SELECT 'settlement_candidate'
) expected
LEFT JOIN information_schema.tables actual
  ON actual.table_schema = DATABASE() AND actual.table_name = expected.table_name
WHERE actual.table_name IS NULL;

-- 3. 三组模板和物理表的列、索引、CHECK 数量必须组内一致。
SELECT target.table_group,
       target.table_name,
       (SELECT COUNT(*)
        FROM information_schema.columns c
        WHERE c.table_schema = DATABASE() AND c.table_name = target.table_name) AS column_count,
       (SELECT COUNT(DISTINCT s.index_name)
        FROM information_schema.statistics s
        WHERE s.table_schema = DATABASE() AND s.table_name = target.table_name) AS index_count,
       (SELECT COUNT(*)
        FROM information_schema.table_constraints tc
        WHERE tc.table_schema = DATABASE()
          AND tc.table_name = target.table_name
          AND tc.constraint_type = 'CHECK') AS check_count
FROM (
    SELECT 'transaction-clearing' AS table_group, 'transaction_clearing_detail' AS table_name
    UNION ALL SELECT 'transaction-clearing', 'transaction_clearing_detail_202603'
    UNION ALL SELECT 'transaction-clearing', 'transaction_clearing_detail_202604'
    UNION ALL SELECT 'reserve-clearing', 'transaction_reserve_clearing_detail'
    UNION ALL SELECT 'reserve-clearing', 'transaction_reserve_clearing_detail_202603'
    UNION ALL SELECT 'reserve-clearing', 'transaction_reserve_clearing_detail_202604'
    UNION ALL SELECT 'reserve-state', 'transaction_reserve_clearing_state'
    UNION ALL SELECT 'reserve-state', 'transaction_reserve_clearing_state_202603'
    UNION ALL SELECT 'reserve-state', 'transaction_reserve_clearing_state_202604'
) target
ORDER BY target.table_group, target.table_name;

-- 4. 季度表的分片列必须是 DATETIME(3)、非空且字符集必须与模板一致。
SELECT COUNT(*) AS invalid_sharding_column_count
FROM (
    SELECT 'transaction_clearing_detail' AS table_name
    UNION ALL SELECT 'transaction_clearing_detail_202603'
    UNION ALL SELECT 'transaction_clearing_detail_202604'
    UNION ALL SELECT 'transaction_reserve_clearing_detail'
    UNION ALL SELECT 'transaction_reserve_clearing_detail_202603'
    UNION ALL SELECT 'transaction_reserve_clearing_detail_202604'
    UNION ALL SELECT 'transaction_reserve_clearing_state'
    UNION ALL SELECT 'transaction_reserve_clearing_state_202603'
    UNION ALL SELECT 'transaction_reserve_clearing_state_202604'
) expected
LEFT JOIN information_schema.columns c
  ON c.table_schema = DATABASE()
 AND c.table_name = expected.table_name
 AND c.column_name = 'transaction_date_time'
WHERE c.column_name IS NULL
   OR c.data_type <> 'datetime'
   OR c.datetime_precision <> 3
   OR c.is_nullable <> 'NO';

SELECT COUNT(*) AS invalid_collation_count
FROM information_schema.tables t
WHERE t.table_schema = DATABASE()
  AND t.table_name IN (
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
  )
  AND t.table_collation <> 'utf8mb4_0900_ai_ci';

-- 5. 活动季度自增号必须位于各自 yyyyQQ + 12 位序列号段。
SELECT table_name, auto_increment,
       CASE
           WHEN table_name LIKE '%_202603'
                AND auto_increment BETWEEN 202603000000000001 AND 202603999999999999 THEN 'MATCHED'
           WHEN table_name LIKE '%_202604'
                AND auto_increment BETWEEN 202604000000000001 AND 202604999999999999 THEN 'MATCHED'
           ELSE 'MISMATCHED'
       END AS auto_increment_check_status
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'transaction_clearing_detail_202603',
      'transaction_clearing_detail_202604',
      'transaction_reserve_clearing_detail_202603',
      'transaction_reserve_clearing_detail_202604',
      'transaction_reserve_clearing_state_202603',
      'transaction_reserve_clearing_state_202604'
  )
ORDER BY table_name;

-- 6. 动作快照、Outbox 和清分表必须具备数据库幂等兜底索引；结果必须为 0。
SELECT COUNT(*) AS missing_required_unique_index_count
FROM (
    SELECT 'transaction_merchant_snapshot' AS table_name, 'uk_merchant_snapshot_transaction' AS index_name
    UNION ALL SELECT 'transaction_merchant_snapshot_202603', 'uk_merchant_snapshot_transaction'
    UNION ALL SELECT 'transaction_merchant_snapshot_202604', 'uk_merchant_snapshot_transaction'
    UNION ALL SELECT 'transaction_clearing_detail', 'uk_clearing_transaction_item'
    UNION ALL SELECT 'transaction_clearing_detail_202603', 'uk_clearing_transaction_item'
    UNION ALL SELECT 'transaction_clearing_detail_202604', 'uk_clearing_transaction_item'
    UNION ALL SELECT 'transaction_reserve_clearing_detail', 'uk_reserve_transaction_item'
    UNION ALL SELECT 'transaction_reserve_clearing_detail_202603', 'uk_reserve_transaction_item'
    UNION ALL SELECT 'transaction_reserve_clearing_detail_202604', 'uk_reserve_transaction_item'
    UNION ALL SELECT 'transaction_reserve_clearing_state', 'uk_reserve_state_original_transaction'
    UNION ALL SELECT 'transaction_reserve_clearing_state_202603', 'uk_reserve_state_original_transaction'
    UNION ALL SELECT 'transaction_reserve_clearing_state_202604', 'uk_reserve_state_original_transaction'
    UNION ALL SELECT 'clearing_reserve_adjustment', 'uk_reserve_adjustment_no'
    UNION ALL SELECT 'clearing_reserve_adjustment', 'uk_reserve_adjustment_request'
    UNION ALL SELECT 'clearing_tier_period_replay', 'uk_tier_replay_no'
    UNION ALL SELECT 'clearing_tier_period_replay', 'uk_tier_replay_request'
    UNION ALL SELECT 'clearing_tier_period_replay_item', 'uk_tier_replay_item_sequence'
    UNION ALL SELECT 'clearing_tier_period_replay_item', 'uk_tier_replay_item_finance'
    UNION ALL SELECT 'settlement_candidate', 'uk_settlement_candidate_no'
    UNION ALL SELECT 'settlement_candidate', 'uk_settlement_candidate_source'
) required_index
LEFT JOIN information_schema.statistics actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = required_index.table_name
 AND actual.index_name = required_index.index_name
 AND actual.non_unique = 0
WHERE actual.index_name IS NULL;

-- 7. 影子候选必须保留数值/影子边界和状态约束；结果必须为 0。
SELECT COUNT(*) AS missing_settlement_candidate_check_count
FROM (
    SELECT 'chk_settlement_candidate_value' AS constraint_name
    UNION ALL SELECT 'chk_settlement_candidate_state'
) required_constraint
LEFT JOIN information_schema.table_constraints actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = 'settlement_candidate'
 AND actual.constraint_name = required_constraint.constraint_name
 AND actual.constraint_type = 'CHECK'
 AND actual.enforced = 'YES'
WHERE actual.constraint_name IS NULL;

-- 8. 人工调整和阶梯重放固定表必须保留金额、双人复核、状态和失败恢复约束；结果必须为 0。
SELECT COUNT(*) AS missing_workflow_check_count
FROM (
    SELECT 'clearing_reserve_adjustment' AS table_name,
           'chk_reserve_adjustment_value' AS constraint_name
    UNION ALL SELECT 'clearing_reserve_adjustment', 'chk_reserve_adjustment_state'
    UNION ALL SELECT 'clearing_tier_period_replay', 'chk_tier_replay_value'
    UNION ALL SELECT 'clearing_tier_period_replay', 'chk_tier_replay_state'
    UNION ALL SELECT 'clearing_tier_period_replay_item', 'chk_tier_replay_item_value'
    UNION ALL SELECT 'clearing_tier_period_replay_item', 'chk_tier_replay_item_state'
) required_constraint
LEFT JOIN information_schema.table_constraints actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = required_constraint.table_name
 AND actual.constraint_name = required_constraint.constraint_name
 AND actual.constraint_type = 'CHECK'
 AND actual.enforced = 'YES'
WHERE actual.constraint_name IS NULL;

-- REPLAY_HOLD 必须是无批次归属的暂存状态，避免重放冻结被结算扫描认领；结果必须为 0。
SELECT COUNT(*) AS invalid_candidate_replay_hold_check_count
FROM information_schema.check_constraints cc
WHERE cc.constraint_schema = DATABASE()
  AND cc.constraint_name = 'chk_settlement_candidate_state'
  AND cc.check_clause NOT LIKE '%REPLAY_HOLD%';

-- 9. 保证金剩余 Gauge 的覆盖索引必须存在且列顺序一致；结果必须为 0。
-- Gauge 按所有 remaining_amount > 0 的负债统计，不能通过只筛 OPEN 状态换取性能。
SELECT COUNT(*) AS invalid_reserve_metrics_index_count
FROM (
    SELECT 'transaction_reserve_clearing_state' AS table_name
    UNION ALL SELECT 'transaction_reserve_clearing_state_202603'
    UNION ALL SELECT 'transaction_reserve_clearing_state_202604'
) expected
LEFT JOIN (
    SELECT s.table_name,
           s.index_name,
           GROUP_CONCAT(s.column_name ORDER BY s.seq_in_index SEPARATOR ',') AS index_columns
    FROM information_schema.statistics s
    WHERE s.table_schema = DATABASE()
      AND s.index_name = 'idx_reserve_state_metrics'
    GROUP BY s.table_name, s.index_name
) actual
  ON actual.table_name = expected.table_name
WHERE actual.index_name IS NULL
   OR actual.index_columns <> 'reserve_currency,transaction_date_time,remaining_amount';

-- 10. 三张保证金状态表必须同时具备双向累计调整列和新守恒式；两个计数必须均为 0。
SELECT COUNT(*) AS missing_reserve_adjustment_column_count
FROM (
    SELECT 'transaction_reserve_clearing_state' AS table_name,
           'debit_adjustment_amount' AS column_name
    UNION ALL SELECT 'transaction_reserve_clearing_state', 'credit_adjustment_amount'
    UNION ALL SELECT 'transaction_reserve_clearing_state_202603', 'debit_adjustment_amount'
    UNION ALL SELECT 'transaction_reserve_clearing_state_202603', 'credit_adjustment_amount'
    UNION ALL SELECT 'transaction_reserve_clearing_state_202604', 'debit_adjustment_amount'
    UNION ALL SELECT 'transaction_reserve_clearing_state_202604', 'credit_adjustment_amount'
) expected
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = expected.table_name
 AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL;

SELECT COUNT(*) AS invalid_reserve_conservation_check_count
FROM (
    SELECT 'transaction_reserve_clearing_state' AS table_name,
           'chk_reserve_state_amount_tpl' AS constraint_name
    UNION ALL SELECT 'transaction_reserve_clearing_state_202603', 'chk_reserve_state_amount_202603'
    UNION ALL SELECT 'transaction_reserve_clearing_state_202604', 'chk_reserve_state_amount_202604'
) expected
LEFT JOIN information_schema.table_constraints tc
  ON tc.table_schema = DATABASE()
 AND tc.table_name = expected.table_name
 AND tc.constraint_name = expected.constraint_name
LEFT JOIN information_schema.check_constraints cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
WHERE cc.constraint_name IS NULL
   OR cc.check_clause NOT LIKE '%debit_adjustment_amount%'
   OR cc.check_clause NOT LIKE '%credit_adjustment_amount%';

-- 11. CHECK 必须处于 ENFORCED；结果必须为空。
SELECT tc.table_name, tc.constraint_name, tc.enforced
FROM information_schema.table_constraints tc
WHERE tc.table_schema = DATABASE()
  AND tc.constraint_type = 'CHECK'
  AND (
      tc.table_name LIKE 'transaction_clearing_detail%'
      OR tc.table_name LIKE 'transaction_reserve_clearing_detail%'
      OR tc.table_name LIKE 'transaction_reserve_clearing_state%'
      OR tc.table_name LIKE 'transaction_finance_state%'
      OR tc.table_name LIKE 'transaction_event_outbox%'
      OR tc.table_name = 'settlement_candidate'
      OR tc.table_name = 'clearing_reserve_adjustment'
      OR tc.table_name = 'clearing_tier_period_replay'
      OR tc.table_name = 'clearing_tier_period_replay_item'
  )
  AND tc.enforced <> 'YES';

-- 12. 以上结果通过后仍只能生成 28 表 Nacos 候选；真实发布和滚动重启须另行审批。
