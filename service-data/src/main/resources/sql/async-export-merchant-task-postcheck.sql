-- 商户端异步导出归属扩展后检：结构、索引、历史回填和归属组合必须全部通过。
SET NAMES utf8mb4;

SELECT column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'data_export_task'
  AND column_name IN ('creator_app_code', 'creator_account_id', 'merchant_id')
ORDER BY ordinal_position;

SELECT index_name, non_unique, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'data_export_task'
  AND index_name IN ('uk_data_export_task_request', 'idx_data_export_task_owner_time')
GROUP BY index_name, non_unique;

SELECT
    SUM(creator_app_code = 'ADMIN' AND merchant_id IS NOT NULL) AS invalid_admin_owner_count,
    SUM(creator_app_code = 'MERCHANT' AND (merchant_id IS NULL OR merchant_id = '')) AS invalid_merchant_owner_count,
    SUM(creator_app_code NOT IN ('ADMIN', 'MERCHANT')) AS invalid_app_code_count
FROM data_export_task;

SELECT
    SUM(operator_app_code = 'ADMIN' AND merchant_id IS NOT NULL) AS invalid_admin_audit_count,
    SUM(operator_app_code = 'MERCHANT' AND (merchant_id IS NULL OR merchant_id = '')) AS invalid_merchant_audit_count,
    SUM(operator_app_code NOT IN ('ADMIN', 'MERCHANT')) AS invalid_audit_app_code_count
FROM data_export_download_audit;
