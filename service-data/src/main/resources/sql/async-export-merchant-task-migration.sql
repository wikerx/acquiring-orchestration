-- 商户端异步导出归属扩展：兼容回填现有 Admin 任务，再增加应用与商户隔离索引。
SET NAMES utf8mb4;

SET @schema_name = DATABASE();

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = @schema_name AND table_name = 'data_export_task'
             AND column_name = 'creator_app_code'),
    'SELECT 1',
    'ALTER TABLE data_export_task ADD COLUMN creator_app_code VARCHAR(32) NOT NULL DEFAULT ''ADMIN'' COMMENT ''创建账号所属应用ADMIN/MERCHANT'' AFTER source_service'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = @schema_name AND table_name = 'data_export_task'
             AND column_name = 'merchant_id'),
    'SELECT 1',
    'ALTER TABLE data_export_task ADD COLUMN merchant_id VARCHAR(64) NULL COMMENT ''商户任务所属商户号，管理任务为空'' AFTER creator_account_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE data_export_task
SET creator_app_code = 'ADMIN', merchant_id = NULL
WHERE source_service = 'service-admin'
  AND (creator_app_code IS NULL OR creator_app_code <> 'ADMIN' OR merchant_id IS NOT NULL);

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = @schema_name AND table_name = 'data_export_task'
             AND index_name = 'uk_data_export_task_request'),
    'ALTER TABLE data_export_task DROP INDEX uk_data_export_task_request',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE data_export_task
    ADD UNIQUE KEY uk_data_export_task_request (creator_app_code, creator_account_id, request_id);

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = @schema_name AND table_name = 'data_export_task'
             AND index_name = 'idx_data_export_task_owner_time'),
    'SELECT 1',
    'ALTER TABLE data_export_task ADD KEY idx_data_export_task_owner_time (creator_app_code, creator_account_id, merchant_id, create_time, id)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = @schema_name AND table_name = 'data_export_download_audit'
             AND column_name = 'operator_app_code'),
    'SELECT 1',
    'ALTER TABLE data_export_download_audit ADD COLUMN operator_app_code VARCHAR(32) NOT NULL DEFAULT ''ADMIN'' COMMENT ''下载操作人所属应用ADMIN/MERCHANT'' AFTER task_no'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = @schema_name AND table_name = 'data_export_download_audit'
             AND column_name = 'merchant_id'),
    'SELECT 1',
    'ALTER TABLE data_export_download_audit ADD COLUMN merchant_id VARCHAR(64) NULL COMMENT ''商户下载所属商户号，管理下载为空'' AFTER operator_account_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE data_export_download_audit
SET operator_app_code = 'ADMIN', merchant_id = NULL
WHERE operator_app_code IS NULL OR operator_app_code <> 'ADMIN';

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = @schema_name AND table_name = 'data_export_download_audit'
             AND index_name = 'idx_data_export_download_owner_time'),
    'SELECT 1',
    'ALTER TABLE data_export_download_audit ADD KEY idx_data_export_download_owner_time (operator_app_code, operator_account_id, merchant_id, requested_at, id)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
