-- 模板中心迁移前检查：任何结果行都必须先处理。
SELECT app_code, COUNT(*) duplicate_count
FROM sys_app WHERE app_code IN ('ADMIN', 'MERCHANT') AND deleted = 0
GROUP BY app_code HAVING COUNT(*) <> 1;

SELECT 'admin_system_catalog_missing' issue
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu menu
    JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
    WHERE menu.menu_code IN ('system_manage', 'admin_system_catalog_v3', 'admin_system_center')
      AND menu.menu_type = 'CATALOG' AND menu.status = 1 AND menu.deleted = 0
);

SELECT 'admin_system_catalog_ambiguous' issue
WHERE (
    SELECT COUNT(*) FROM sys_menu menu
    JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
    WHERE menu.menu_code IN ('system_manage', 'admin_system_catalog_v3', 'admin_system_center')
      AND menu.menu_type = 'CATALOG' AND menu.status = 1 AND menu.deleted = 0
) <> 1;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('sys_file_template', 'sys_file_template_version', 'sys_file_template_asset');
