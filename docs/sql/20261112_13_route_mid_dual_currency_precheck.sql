-- Route payment scope and MID dual-currency migration: read-only precheck.
-- Expected database: payment_acquiring. This script never changes data.

SELECT DATABASE() AS current_database;

SELECT COUNT(*) AS missing_prerequisite_table_count
FROM (
    SELECT 'channel_mid_config' table_name
    UNION ALL SELECT 'channel_payment_capability'
    UNION ALL SELECT 'channel_capability_currency'
    UNION ALL SELECT 'payment_route_method'
    UNION ALL SELECT 'payment_route_candidate'
) required_table
WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.tables existing_table
    WHERE existing_table.table_schema = DATABASE()
      AND existing_table.table_name = required_table.table_name
);

SELECT COUNT(*) AS mid_without_deterministic_transaction_currency_count
FROM channel_mid_config mid_record
LEFT JOIN (
    SELECT mid_source.id AS mid_config_id
    FROM channel_mid_config mid_source
    JOIN channel_payment_capability capability
      ON capability.channel_id = mid_source.channel_id
     AND capability.business_type = mid_source.business_type
     AND capability.capability_status = 1
     AND capability.deleted = 0
     AND (
          UPPER(TRIM(mid_source.payment_method_scope)) = 'ALL'
          OR FIND_IN_SET(capability.payment_method, UPPER(REPLACE(mid_source.payment_method_scope, ' ', ''))) > 0
     )
    WHERE mid_source.deleted = 0
    GROUP BY mid_source.id
    HAVING COUNT(DISTINCT capability.default_transaction_currency) = 1
) currency_backfill ON currency_backfill.mid_config_id = mid_record.id
WHERE mid_record.deleted = 0
  AND currency_backfill.mid_config_id IS NULL;

SELECT COUNT(*) AS invalid_backfill_transaction_currency_scope_count
FROM channel_mid_config mid_record
JOIN (
    SELECT mid_source.id AS mid_config_id,
           MAX(UPPER(TRIM(capability.default_transaction_currency))) AS default_transaction_currency
    FROM channel_mid_config mid_source
    JOIN channel_payment_capability capability
      ON capability.channel_id = mid_source.channel_id
     AND capability.business_type = mid_source.business_type
     AND capability.capability_status = 1
     AND capability.deleted = 0
     AND (
          UPPER(TRIM(mid_source.payment_method_scope)) = 'ALL'
          OR FIND_IN_SET(capability.payment_method, UPPER(REPLACE(mid_source.payment_method_scope, ' ', ''))) > 0
     )
    WHERE mid_source.deleted = 0
    GROUP BY mid_source.id
    HAVING COUNT(DISTINCT UPPER(TRIM(capability.default_transaction_currency))) = 1
) currency_backfill ON currency_backfill.mid_config_id = mid_record.id
WHERE mid_record.deleted = 0
  AND UPPER(TRIM(mid_record.currency_scope)) <> 'ALL'
  AND FIND_IN_SET(
      currency_backfill.default_transaction_currency,
      UPPER(REPLACE(mid_record.currency_scope, ' ', ''))
  ) = 0;

SELECT COUNT(*) AS legacy_all_route_candidate_count
FROM payment_route_candidate
WHERE deleted = 0
  AND UPPER(TRIM(payment_method)) = 'ALL';

SELECT COUNT(*) AS legacy_bank_card_scope_count
FROM payment_route_candidate
WHERE deleted = 0
  AND UPPER(TRIM(payment_method)) = 'BANK_CARD';

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
