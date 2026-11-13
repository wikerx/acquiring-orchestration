-- 支付路由阶段1A交易维度精简：迁移前只读检查。
-- 本脚本不修改任何数据，用于确认旧单值交易类型可无损迁移。

SELECT DATABASE() AS current_database;

SELECT COUNT(*) AS missing_prerequisite_table
FROM (
    SELECT 'payment_route_rule' table_name
) required_table
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.tables existing_table
    WHERE existing_table.table_schema = DATABASE()
      AND existing_table.table_name = required_table.table_name
);

SELECT COUNT(*) AS legacy_transaction_type_count
FROM payment_route_rule
WHERE deleted = 0
  AND transaction_type IS NOT NULL
  AND TRIM(transaction_type) <> ''
  AND UPPER(TRIM(transaction_type)) <> 'ALL';

SELECT COUNT(*) AS invalid_legacy_transaction_type_count
FROM payment_route_rule
WHERE deleted = 0
  AND transaction_type IS NOT NULL
  AND TRIM(transaction_type) <> ''
  AND UPPER(TRIM(transaction_type)) <> 'ALL'
  AND UPPER(TRIM(transaction_type)) NOT REGEXP '^[A-Z][A-Z0-9_]{1,63}$';

SELECT COUNT(*) AS legacy_obsolete_dimension_count
FROM payment_route_rule
WHERE deleted = 0
  AND (
      card_brand IS NOT NULL
      OR label_currency IS NOT NULL
      OR amount_min IS NOT NULL
      OR amount_max IS NOT NULL
      OR COALESCE(NULLIF(issuer_country_scope, ''), 'ALL') <> 'ALL'
      OR COALESCE(NULLIF(billing_country_scope, ''), 'ALL') <> 'ALL'
  );
