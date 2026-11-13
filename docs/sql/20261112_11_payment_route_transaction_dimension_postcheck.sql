-- 支付路由阶段1A交易维度精简：迁移后只读检查。
-- 除 legacy_obsolete_dimension_count 为历史观察项外，其余异常计数均应为0。

SELECT COUNT(*) AS missing_transaction_type_table_count
FROM (SELECT 'payment_route_transaction_type' table_name) required_table
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.tables existing_table
    WHERE existing_table.table_schema = DATABASE()
      AND existing_table.table_name = required_table.table_name
);

SELECT COUNT(*) AS missing_column_count
FROM (
    SELECT 'id' column_name
    UNION ALL SELECT 'rule_id'
    UNION ALL SELECT 'transaction_type'
    UNION ALL SELECT 'deleted'
) required_column
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.columns existing_column
    WHERE existing_column.table_schema = DATABASE()
      AND existing_column.table_name = 'payment_route_transaction_type'
      AND existing_column.column_name = required_column.column_name
);

SELECT COUNT(*) AS missing_index_count
FROM (
    SELECT 'uk_payment_route_transaction_rule_type_deleted' index_name
    UNION ALL SELECT 'idx_payment_route_transaction_type_rule'
) required_index
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics existing_index
    WHERE existing_index.table_schema = DATABASE()
      AND existing_index.table_name = 'payment_route_transaction_type'
      AND existing_index.index_name = required_index.index_name
);

SELECT COUNT(*) AS unmigrated_legacy_transaction_type_count
FROM payment_route_rule rule_record
WHERE rule_record.deleted = 0
  AND rule_record.transaction_type IS NOT NULL
  AND TRIM(rule_record.transaction_type) <> ''
  AND UPPER(TRIM(rule_record.transaction_type)) <> 'ALL'
  AND NOT EXISTS (
      SELECT 1
      FROM payment_route_transaction_type transaction_record
      WHERE transaction_record.rule_id = rule_record.id
        AND transaction_record.transaction_type = UPPER(TRIM(rule_record.transaction_type))
        AND transaction_record.deleted = 0
  );

SELECT COUNT(*) AS invalid_active_transaction_type_count
FROM payment_route_transaction_type
WHERE deleted = 0
  AND (
      transaction_type = 'ALL'
      OR transaction_type NOT REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
  );

SELECT COUNT(*) AS orphan_active_transaction_type_count
FROM payment_route_transaction_type transaction_record
LEFT JOIN payment_route_rule rule_record
  ON rule_record.id = transaction_record.rule_id
 AND rule_record.deleted = 0
WHERE transaction_record.deleted = 0
  AND rule_record.id IS NULL;

SELECT COUNT(*) AS duplicate_active_transaction_type_count
FROM (
    SELECT rule_id, transaction_type
    FROM payment_route_transaction_type
    WHERE deleted = 0
    GROUP BY rule_id, transaction_type
    HAVING COUNT(*) > 1
) duplicate_record;

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
