-- 统一待办真实数据库分页与处理历史分页：迁移前只读检查。
SET NAMES utf8mb4;

SELECT DATABASE() AS current_database,
       VERSION() AS mysql_version,
       @@character_set_database AS database_charset,
       @@collation_database AS database_collation;

SELECT COUNT(*) AS work_item_count
FROM admin_operational_work_item;

SELECT COUNT(*) AS work_item_history_count
FROM admin_operational_work_item_history;

SELECT source_type, COUNT(*) AS source_count
FROM admin_operational_work_item
GROUP BY source_type
ORDER BY source_type;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND (
      table_name REGEXP '^transaction_refund_approval(_[0-9]{6})?$'
      OR table_name REGEXP '^settlement_review_order(_[0-9]{6})?$'
      OR table_name REGEXP '^exchange_rate_alert_event(_[0-9]{6})?$'
      OR table_name REGEXP '^transaction_merchant_notification(_[0-9]{6})?$'
      OR table_name REGEXP '^transaction_abnormal_event(_[0-9]{6})?$'
      OR table_name REGEXP '^transaction_finance_state(_[0-9]{6})?$'
      OR table_name REGEXP '^settlement_batch(_[0-9]{6})?$'
      OR table_name REGEXP '^risk_evaluation_record(_[0-9]{6})?$'
  )
ORDER BY table_name;

SELECT column_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'admin_operational_work_item'
  AND column_name IN (
      'source_projection_version', 'merchant_id', 'merchant_name', 'business_no',
      'related_business_no', 'transaction_id', 'transaction_date_time', 'source_occurred_at'
  )
ORDER BY ordinal_position;
