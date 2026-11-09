-- 异步导出与下载中心第一批迁移后检。

SET NAMES utf8mb4;

SELECT table_name, table_collation
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('data_export_task', 'data_export_download_audit')
ORDER BY table_name;

SELECT table_name, column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('data_export_task', 'data_export_download_audit')
  AND column_name IN (
      'task_no', 'request_id', 'query_snapshot', 'data_scope_snapshot', 'status',
      'lease_owner', 'lease_until', 'version', 'audit_no', 'result_status',
      'requested_at', 'downloaded_at'
  )
ORDER BY table_name, ordinal_position;

SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('data_export_task', 'data_export_download_audit')
GROUP BY table_name, index_name
ORDER BY table_name, index_name;

SELECT menu.menu_code, menu.menu_name, menu.component_path, menu.permission_code,
       menu.sort_no, parent.menu_code AS parent_menu_code
FROM sys_menu menu
LEFT JOIN sys_menu parent ON parent.id = menu.parent_id AND parent.deleted = 0
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
WHERE menu.menu_code IN (
    'admin_export_task_center_v1', 'admin_export_task_detail_v1',
    'admin_export_task_download_v1', 'admin_export_task_retry_v1'
) AND menu.deleted = 0
ORDER BY menu.sort_no, menu.menu_code;

SELECT permission_code, permission_name, permission_type, resource_method, resource_path, status
FROM sys_permission
WHERE permission_code IN (
    'operations:export-task:list', 'operations:export-task:detail',
    'operations:export-task:download', 'operations:export-task:retry'
) AND deleted = 0
ORDER BY permission_code;

SELECT job_code, handler_code, cron_expression, scheduler_mode, execute_mode,
       allow_concurrent, timeout_seconds, status, deleted
FROM sys_job_task
WHERE job_code = 'DATA_EXPORT_TASK_DISPATCH' AND deleted = 0;

SELECT COUNT(*) AS invalid_task_rows
FROM data_export_task
WHERE status NOT IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'EXPIRED')
   OR progress_percent NOT BETWEEN 0 AND 100
   OR retry_count < 0 OR max_retry_count <= 0 OR version < 0;

SELECT COUNT(*) AS invalid_audit_rows
FROM data_export_download_audit
WHERE result_status NOT IN ('REQUESTED', 'SUCCESS', 'FAILED');
