-- 支付路由指定商户多选：迁移后只读检查。
-- 以下异常计数均应为0。

SELECT COUNT(*) AS missing_rule_merchant_table_count
FROM (SELECT 'payment_route_rule_merchant' table_name) required_table
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.tables existing_table
    WHERE existing_table.table_schema = DATABASE()
      AND existing_table.table_name = required_table.table_name
);

SELECT COUNT(*) AS missing_rule_merchant_column_count
FROM (
    SELECT 'id' column_name
    UNION ALL SELECT 'rule_id'
    UNION ALL SELECT 'merchant_id'
    UNION ALL SELECT 'deleted'
) required_column
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.columns existing_column
    WHERE existing_column.table_schema = DATABASE()
      AND existing_column.table_name = 'payment_route_rule_merchant'
      AND existing_column.column_name = required_column.column_name
);

SELECT COUNT(*) AS missing_rule_merchant_index_count
FROM (
    SELECT 'uk_payment_route_rule_merchant_deleted' index_name
    UNION ALL SELECT 'idx_payment_route_rule_merchant_lookup'
) required_index
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics existing_index
    WHERE existing_index.table_schema = DATABASE()
      AND existing_index.table_name = 'payment_route_rule_merchant'
      AND existing_index.index_name = required_index.index_name
);

SELECT COUNT(*) AS unmigrated_legacy_merchant_count
FROM payment_route_rule rule_record
WHERE rule_record.deleted = 0
  AND rule_record.scope_type = 'MERCHANT'
  AND rule_record.merchant_id IS NOT NULL
  AND TRIM(rule_record.merchant_id) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM payment_route_rule_merchant relation_record
      WHERE relation_record.rule_id = rule_record.id
        AND relation_record.merchant_id = TRIM(rule_record.merchant_id)
        AND relation_record.deleted = 0
  );

SELECT COUNT(*) AS merchant_rule_without_relation_count
FROM payment_route_rule rule_record
WHERE rule_record.deleted = 0
  AND rule_record.scope_type = 'MERCHANT'
  AND NOT EXISTS (
      SELECT 1
      FROM payment_route_rule_merchant relation_record
      WHERE relation_record.rule_id = rule_record.id
        AND relation_record.deleted = 0
  );

SELECT COUNT(*) AS global_rule_with_relation_count
FROM payment_route_rule rule_record
JOIN payment_route_rule_merchant relation_record
  ON relation_record.rule_id = rule_record.id
 AND relation_record.deleted = 0
WHERE rule_record.deleted = 0
  AND rule_record.scope_type = 'GLOBAL';

SELECT COUNT(*) AS orphan_rule_merchant_relation_count
FROM payment_route_rule_merchant relation_record
LEFT JOIN payment_route_rule rule_record
  ON rule_record.id = relation_record.rule_id
 AND rule_record.deleted = 0
WHERE relation_record.deleted = 0
  AND rule_record.id IS NULL;

SELECT COUNT(*) AS duplicate_active_rule_merchant_count
FROM (
    SELECT rule_id, merchant_id
    FROM payment_route_rule_merchant
    WHERE deleted = 0
    GROUP BY rule_id, merchant_id
    HAVING COUNT(*) > 1
) duplicate_record;
