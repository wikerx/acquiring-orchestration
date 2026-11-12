-- 商户端异步导出归属扩展预检：只读确认现有表、唯一键和异常任务归属。
SET NAMES utf8mb4;

SELECT table_name, table_collation
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('data_export_task', 'data_export_download_audit');

SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'data_export_task'
  AND index_name = 'uk_data_export_task_request'
GROUP BY index_name;

SELECT COUNT(*) AS invalid_existing_source_service_count
FROM data_export_task
WHERE source_service IS NULL OR source_service NOT IN ('service-admin', 'service-merchant');
