-- Route payment scope and MID dual-currency migration: read-only postcheck.
-- Every invalid count below must be zero.

SELECT DATABASE() AS current_database;

SELECT COUNT(*) AS missing_column_count
FROM (
    SELECT 'channel_mid_config' table_name, 'default_transaction_currency' column_name
    UNION ALL SELECT 'payment_route_method', 'card_brand'
    UNION ALL SELECT 'payment_route_candidate', 'card_brand'
) required_column
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.columns existing_column
    WHERE existing_column.table_schema = DATABASE()
      AND existing_column.table_name = required_column.table_name
      AND existing_column.column_name = required_column.column_name
);

SELECT COUNT(*) AS missing_index_count
FROM (
    SELECT 'payment_route_method' table_name, 'uk_payment_route_method_rule_scope_deleted' index_name
    UNION ALL SELECT 'payment_route_method', 'idx_payment_route_method_scope_rule'
    UNION ALL SELECT 'payment_route_candidate', 'uk_payment_route_candidate_mid_deleted'
    UNION ALL SELECT 'payment_route_candidate', 'uk_payment_route_candidate_priority_deleted'
    UNION ALL SELECT 'payment_route_candidate', 'idx_payment_route_candidate_scope'
) required_index
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics existing_index
    WHERE existing_index.table_schema = DATABASE()
      AND existing_index.table_name = required_index.table_name
      AND existing_index.index_name = required_index.index_name
);

SELECT COUNT(*) AS invalid_mid_default_transaction_currency_count
FROM channel_mid_config
WHERE deleted = 0
  AND (
      default_transaction_currency IS NULL
      OR TRIM(default_transaction_currency) = ''
      OR UPPER(TRIM(currency_scope)) <> 'ALL'
         AND FIND_IN_SET(default_transaction_currency, UPPER(REPLACE(currency_scope, ' ', ''))) = 0
  );

SELECT COUNT(*) AS invalid_active_route_method_scope_count
FROM payment_route_method
WHERE deleted = 0
  AND (
      payment_method = 'ALL'
      OR payment_method = 'BANK_CARD' AND card_brand IN ('ALL', 'NONE')
      OR payment_method <> 'BANK_CARD' AND card_brand <> 'NONE'
  );

SELECT COUNT(*) AS invalid_active_route_candidate_scope_count
FROM payment_route_candidate
WHERE deleted = 0
  AND (
      payment_method = 'ALL'
      OR payment_method = 'BANK_CARD' AND card_brand IN ('ALL', 'NONE')
      OR payment_method <> 'BANK_CARD' AND card_brand <> 'NONE'
  );

SELECT COUNT(*) AS duplicate_active_route_method_scope_count
FROM (
    SELECT rule_id, payment_method, card_brand
    FROM payment_route_method
    WHERE deleted = 0
    GROUP BY rule_id, payment_method, card_brand
    HAVING COUNT(*) > 1
) duplicate_scope;

SELECT COUNT(*) AS duplicate_active_route_candidate_mid_count
FROM (
    SELECT rule_id, payment_method, card_brand, mid_config_id
    FROM payment_route_candidate
    WHERE deleted = 0
    GROUP BY rule_id, payment_method, card_brand, mid_config_id
    HAVING COUNT(*) > 1
) duplicate_scope;

SELECT id, channel_code, channel_mid,
       currency_scope, default_transaction_currency, default_settlement_currency
FROM channel_mid_config
WHERE deleted = 0
ORDER BY id;

SELECT rule_id, payment_method, card_brand, mid_config_id,
       candidate_priority, candidate_status
FROM payment_route_candidate
WHERE deleted = 0
ORDER BY rule_id, payment_method, card_brand, candidate_priority;

SELECT COUNT(*) AS transaction_order_count,
       COALESCE(MAX(id), 0) AS transaction_order_max_id,
       MAX(update_time) AS transaction_order_max_update_time
FROM transaction_order;

SELECT COUNT(*) AS transaction_currency_conversion_count,
       COALESCE(MAX(id), 0) AS transaction_currency_conversion_max_id,
       MAX(create_time) AS transaction_currency_conversion_max_create_time
FROM transaction_currency_conversion;


-- Exact protected-fact audit across logical and physical shard tables.
SET SESSION group_concat_max_len = 1048576;

SELECT GROUP_CONCAT(
           CONCAT(
               'SELECT ',
               QUOTE(table_name),
               ' AS protected_fact_table_name, COUNT(*) AS row_count FROM ',
               CHAR(96),
               table_name,
               CHAR(96)
           )
           ORDER BY table_name
           SEPARATOR ' UNION ALL '
       )
INTO @protected_count_sql
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_type = 'BASE TABLE'
  AND (
      table_name REGEXP '^(transaction_order|transaction_currency_conversion)(_|$)'
      OR table_name REGEXP '^(clearing|settlement)'
  );

PREPARE protected_count_statement FROM @protected_count_sql;
EXECUTE protected_count_statement;
DEALLOCATE PREPARE protected_count_statement;

SELECT CONCAT(
           'CHECKSUM TABLE ',
           GROUP_CONCAT(CONCAT(CHAR(96), table_name, CHAR(96)) ORDER BY table_name SEPARATOR ','),
           ' EXTENDED'
       )
INTO @protected_checksum_sql
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_type = 'BASE TABLE'
  AND (
      table_name REGEXP '^(transaction_order|transaction_currency_conversion)(_|$)'
      OR table_name REGEXP '^(clearing|settlement)'
  );

PREPARE protected_checksum_statement FROM @protected_checksum_sql;
EXECUTE protected_checksum_statement;
DEALLOCATE PREPARE protected_checksum_statement;
