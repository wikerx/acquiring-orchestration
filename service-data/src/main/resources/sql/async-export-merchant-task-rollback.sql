-- 回退说明：仅在尚未创建任何 MERCHANT 任务时执行；存在商户任务时必须采用向前修复。
SET NAMES utf8mb4;

SELECT COUNT(*) AS merchant_task_count
FROM data_export_task
WHERE creator_app_code = 'MERCHANT' OR merchant_id IS NOT NULL;

-- 确认 merchant_task_count = 0 后，人工审核并依次执行：
-- ALTER TABLE data_export_task DROP INDEX uk_data_export_task_request;
-- ALTER TABLE data_export_task ADD UNIQUE KEY uk_data_export_task_request (creator_account_id, request_id);
-- ALTER TABLE data_export_task DROP INDEX idx_data_export_task_owner_time;
-- ALTER TABLE data_export_task DROP COLUMN merchant_id, DROP COLUMN creator_app_code;
-- ALTER TABLE data_export_download_audit DROP INDEX idx_data_export_download_owner_time;
-- ALTER TABLE data_export_download_audit DROP COLUMN merchant_id, DROP COLUMN operator_app_code;
