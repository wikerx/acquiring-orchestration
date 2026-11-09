-- 异步导出与下载中心第一批回滚/隔离脚本。
-- 为避免丢失任务、下载审计和 MinIO 对象关联，本脚本只关闭入口和调度，不删除两张业务表。

SET NAMES utf8mb4;
START TRANSACTION;

SET @admin_app_id = (
    SELECT id FROM sys_app WHERE app_code = 'ADMIN' AND deleted = 0 ORDER BY id LIMIT 1
);

UPDATE sys_job_task
SET status = 'DISABLED', next_trigger_time = NULL,
    update_by = 'system-rollback', update_time = NOW(3)
WHERE job_code = 'DATA_EXPORT_TASK_DISPATCH' AND deleted = 0;

DELETE role_permission
FROM sys_role_permission role_permission
JOIN sys_permission permission ON permission.id = role_permission.permission_id
WHERE role_permission.app_id = @admin_app_id AND role_permission.deleted = 0
  AND permission.app_id = @admin_app_id AND permission.deleted = 0
  AND permission.permission_code IN (
      'operations:export-task:list', 'operations:export-task:detail',
      'operations:export-task:download', 'operations:export-task:retry'
  );

DELETE role_menu
FROM sys_role_menu role_menu
JOIN sys_menu menu ON menu.id = role_menu.menu_id
WHERE role_menu.app_id = @admin_app_id AND role_menu.deleted = 0
  AND menu.app_id = @admin_app_id AND menu.deleted = 0
  AND menu.menu_code IN (
      'admin_export_task_center_v1', 'admin_export_task_detail_v1',
      'admin_export_task_download_v1', 'admin_export_task_retry_v1'
  );

UPDATE sys_permission
SET status = 0, deleted = id, updated_at = NOW(3)
WHERE app_id = @admin_app_id AND deleted = 0
  AND permission_code IN (
      'operations:export-task:list', 'operations:export-task:detail',
      'operations:export-task:download', 'operations:export-task:retry'
  );

UPDATE sys_menu
SET visible = 0, status = 0, deleted = id, updated_at = NOW(3)
WHERE app_id = @admin_app_id AND deleted = 0
  AND menu_code IN (
      'admin_export_task_center_v1', 'admin_export_task_detail_v1',
      'admin_export_task_download_v1', 'admin_export_task_retry_v1'
  );

COMMIT;

-- 只有确认 data_export_task 和 data_export_download_audit 均为空、私有 Bucket 已独立清理后，
-- 才能由 DBA 在维护窗口手工 DROP TABLE；默认回滚禁止破坏审计事实。
