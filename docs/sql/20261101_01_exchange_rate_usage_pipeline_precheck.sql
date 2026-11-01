-- 汇率实际使用事件链路迁移前只读检查。所有 *_count 结果必须为 0。

SET NAMES utf8mb4;

SELECT 2 - COUNT(*) AS missing_required_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('exchange_rate_usage_snapshot', 'settlement_event_outbox');

SELECT COUNT(*) AS invalid_existing_snapshot_count
FROM exchange_rate_usage_snapshot
WHERE id IS NULL
   OR rate_type IS NULL
   OR usage_scene IS NULL
   OR business_type IS NULL
   OR business_no IS NULL
   OR base_currency IS NULL
   OR quote_currency IS NULL
   OR used_rate IS NULL
   OR applied_time IS NULL;

-- FXTR/FXST 为本迁移启用后的稳定事件号前缀；存量占用必须先人工核对来源。
SELECT COUNT(*) AS reserved_usage_event_prefix_collision_count
FROM settlement_event_outbox
WHERE event_no LIKE 'FXTR:%'
   OR event_no LIKE 'FXST:%';

SELECT COUNT(*) AS invalid_existing_settlement_outbox_count
FROM settlement_event_outbox
WHERE event_no IS NULL
   OR settlement_batch_no IS NULL
   OR candidate_id IS NULL
   OR topic IS NULL
   OR tag IS NULL
   OR message_key IS NULL
   OR payload_json IS NULL;
