-- 支付路由指定商户多选：迁移前只读检查。
-- 本脚本不修改数据；merchant_scope_without_merchant_count 必须为0。

SELECT DATABASE() AS current_database;

SELECT COUNT(*) AS missing_route_rule_table_count
FROM (SELECT 'payment_route_rule' table_name) required_table
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.tables existing_table
    WHERE existing_table.table_schema = DATABASE()
      AND existing_table.table_name = required_table.table_name
);

SELECT COUNT(*) AS merchant_scope_without_merchant_count
FROM payment_route_rule
WHERE deleted = 0
  AND scope_type = 'MERCHANT'
  AND (merchant_id IS NULL OR TRIM(merchant_id) = '');

SELECT COUNT(*) AS global_scope_with_merchant_count
FROM payment_route_rule
WHERE deleted = 0
  AND scope_type = 'GLOBAL'
  AND merchant_id IS NOT NULL
  AND TRIM(merchant_id) <> '';

SELECT COUNT(*) AS legacy_merchant_route_count
FROM payment_route_rule
WHERE deleted = 0
  AND scope_type = 'MERCHANT'
  AND merchant_id IS NOT NULL
  AND TRIM(merchant_id) <> '';
