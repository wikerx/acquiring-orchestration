-- 支付路由阶段1A类型拆分迁移前检查，只读执行。

SELECT DATABASE() AS current_database;

SELECT required_table.table_name AS missing_table
FROM (
    SELECT 'payment_route_rule' table_name
    UNION ALL SELECT 'payment_route_strategy'
    UNION ALL SELECT 'payment_route_candidate'
) required_table
LEFT JOIN information_schema.tables actual
  ON actual.table_schema = DATABASE() AND actual.table_name = required_table.table_name
WHERE actual.table_name IS NULL;

SELECT rule_type, rule_status, COUNT(*) AS rule_count
FROM payment_route_rule
WHERE deleted = 0
GROUP BY rule_type, rule_status
ORDER BY rule_type, rule_status;

SELECT COUNT(*) AS legacy_standard_rule_count
FROM payment_route_rule
WHERE rule_type = 'STANDARD' AND deleted = 0;

SELECT COUNT(*) AS legacy_all_candidate_count
FROM payment_route_candidate
WHERE payment_method = 'ALL' AND deleted = 0;
