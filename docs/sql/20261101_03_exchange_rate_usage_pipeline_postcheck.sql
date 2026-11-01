-- 汇率实际使用事件链路迁移后只读检查。所有 *_count 结果必须为 0。

SELECT 6 - COUNT(*) AS missing_usage_pipeline_column_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'exchange_rate_usage_snapshot'
        AND column_name IN (
            'usage_event_id', 'producer_service', 'rate_source',
            'source_quote_direction', 'rate_effective_time'
        ))
       OR (table_name = 'settlement_event_outbox' AND column_name = 'rate_usage_id'));

SELECT COUNT(*) AS nullable_usage_event_id_column_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'exchange_rate_usage_snapshot'
  AND column_name = 'usage_event_id'
  AND is_nullable <> 'NO';

SELECT COUNT(*) AS nullable_producer_service_column_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'exchange_rate_usage_snapshot'
  AND column_name = 'producer_service'
  AND is_nullable <> 'NO';

SELECT 2 - COUNT(*) AS invalid_settlement_outbox_source_column_shape_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'settlement_event_outbox'
  AND column_name IN ('candidate_id', 'rate_usage_id')
  AND is_nullable = 'YES';

SELECT 2 - COUNT(DISTINCT CONCAT(table_name, ':', index_name))
    AS missing_usage_pipeline_unique_index_count
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND non_unique = 0
  AND ((table_name = 'exchange_rate_usage_snapshot'
        AND index_name = 'uk_exchange_snapshot_usage_event')
       OR (table_name = 'settlement_event_outbox'
           AND index_name = 'uk_settlement_event_rate_usage'));

SELECT COUNT(*) AS missing_usage_event_id_count
FROM exchange_rate_usage_snapshot
WHERE usage_event_id IS NULL OR usage_event_id = '';

SELECT COUNT(*) AS duplicate_usage_event_id_count
FROM (
    SELECT usage_event_id
    FROM exchange_rate_usage_snapshot
    GROUP BY usage_event_id
    HAVING COUNT(*) > 1
) duplicate_usage;

SELECT COUNT(*) AS invalid_new_usage_snapshot_count
FROM exchange_rate_usage_snapshot
WHERE usage_event_id NOT LIKE 'LEGACY:%'
  AND (producer_service NOT IN ('service-payment', 'service-settlement')
       OR rate_type NOT IN ('TRANSACTION_RATE', 'SETTLEMENT_RATE')
       OR (rate_type = 'TRANSACTION_RATE'
           AND business_type NOT IN (
               'PAYMENT', 'AUTHORIZATION', 'PRE_AUTHORIZATION', 'CAPTURE',
               'PRE_AUTH_COMPLETION', 'INCREMENTAL_AUTHORIZATION', 'REFUND'
           ))
       OR (rate_type = 'SETTLEMENT_RATE' AND business_type <> 'SETTLEMENT')
       OR base_currency = quote_currency
       OR used_rate <= 0
       OR business_rate_id IS NULL
       OR business_rate_id <= 0
       OR rate_source IS NULL
       OR source_quote_direction NOT IN ('DIRECT', 'INVERSE')
       OR rate_effective_time IS NULL
       OR applied_time < rate_effective_time);

SELECT COUNT(*) AS invalid_settlement_rate_usage_outbox_count
FROM settlement_event_outbox
WHERE topic = 'acquiring_exchange_rate_usage_topic'
  AND (candidate_id IS NOT NULL OR rate_usage_id IS NULL OR rate_usage_id <= 0);

SELECT COUNT(*) AS invalid_settlement_projection_outbox_count
FROM settlement_event_outbox
WHERE topic <> 'acquiring_exchange_rate_usage_topic'
  AND (candidate_id IS NULL OR rate_usage_id IS NOT NULL);
