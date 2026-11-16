-- 异步导出与下载中心第一批迁移前检；结果中的阻断项必须为 0。

SET NAMES utf8mb4;

SELECT DATABASE() AS current_database,
       @@character_set_database AS database_charset,
       @@collation_database AS database_collation;

SELECT COUNT(*) AS admin_app_missing
FROM (SELECT 1) seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_app WHERE app_code = 'ADMIN' AND deleted = 0
);

SELECT COUNT(*) AS home_catalog_missing
FROM (SELECT 1) seed
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu menu
    JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
    WHERE menu.menu_code = 'admin_home_catalog_v3'
      AND menu.menu_type = 'CATALOG' AND menu.deleted = 0
);

SELECT table_name, table_collation
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('data_export_task', 'data_export_download_audit');

SELECT permission_code, COUNT(*) AS active_count
FROM sys_permission
WHERE permission_code IN (
    'operations:export-task:list', 'operations:export-task:detail',
    'operations:export-task:download', 'operations:export-task:retry'
) AND deleted = 0
GROUP BY permission_code
HAVING COUNT(*) > 1;

SELECT job_code, handler_code, status, deleted
FROM sys_job_task
WHERE job_code = 'DATA_EXPORT_TASK_DISPATCH';
