-- 支付路由阶段1A类型拆分迁移后检查。仅在 20261112_06 后、品牌维度迁移前执行。
-- 品牌维度迁移后同一 BANK_CARD 支付方式可有多条品牌记录，不能再用本脚本的去重口径判断。
-- 所有 missing_*、invalid_* 应为0或无结果。

SELECT 1 - COUNT(*) AS missing_method_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'payment_route_method';

SELECT required.column_name AS missing_column
FROM (
    SELECT 'payment_route_strategy' table_name, 'history_lookback_months' column_name
    UNION ALL SELECT 'payment_route_candidate', 'candidate_weight'
) required
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = required.table_name
 AND actual.column_name = required.column_name
WHERE actual.column_name IS NULL;

SELECT COUNT(*) AS invalid_rule_type_count
FROM payment_route_rule
WHERE deleted = 0
  AND rule_type NOT IN ('FIXED', 'PRIORITY', 'WEIGHT', 'HISTORY_AFFINITY');

SELECT COUNT(*) AS invalid_strategy_type_count
FROM payment_route_strategy strategy
JOIN payment_route_rule rule_record ON rule_record.id = strategy.rule_id AND rule_record.deleted = 0
WHERE strategy.deleted = 0
  AND strategy.strategy_type <> rule_record.rule_type;

SELECT COUNT(*) AS invalid_all_candidate_count
FROM payment_route_candidate
WHERE deleted = 0 AND payment_method = 'ALL';

SELECT COUNT(*) AS missing_rule_payment_method_count
FROM payment_route_rule rule_record
WHERE rule_record.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM payment_route_method method_record
      WHERE method_record.rule_id = rule_record.id AND method_record.deleted = 0
  );

SELECT rule_id, payment_method, COUNT(*) AS duplicate_method_count
FROM payment_route_method
WHERE deleted = 0
GROUP BY rule_id, payment_method
HAVING COUNT(*) > 1;

SELECT candidate.rule_id, candidate.payment_method,
       SUM(CASE WHEN candidate.candidate_status = 1 THEN candidate.candidate_weight ELSE 0 END) AS enabled_weight
FROM payment_route_candidate candidate
JOIN payment_route_rule rule_record ON rule_record.id = candidate.rule_id
WHERE candidate.deleted = 0 AND rule_record.deleted = 0 AND rule_record.rule_type = 'WEIGHT'
GROUP BY candidate.rule_id, candidate.payment_method
HAVING enabled_weight <> 100.00 OR enabled_weight IS NULL;
