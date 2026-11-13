-- 已安装下载中心的商户系统：将现有页面移入系统管理，保留路径与权限。
SET NAMES utf8mb4;

START TRANSACTION;

UPDATE sys_menu page
JOIN sys_app app ON app.id = page.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_system_catalog_v1'
                    AND parent.menu_type = 'CATALOG' AND parent.deleted = 0
SET page.parent_id = parent.id, page.sort_no = 96, page.updated_at = CURRENT_TIMESTAMP(3)
WHERE page.menu_code = 'merchant_export_task_center_v1' AND page.deleted = 0;

COMMIT;

SELECT CASE WHEN COUNT(*) = 1 THEN 'PASS' ELSE 'FAIL' END AS download_center_parent_check
FROM sys_menu page
JOIN sys_app app ON app.id = page.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu parent ON parent.id = page.parent_id AND parent.app_id = app.id AND parent.deleted = 0
WHERE page.menu_code = 'merchant_export_task_center_v1' AND page.deleted = 0
  AND parent.menu_code = 'merchant_system_catalog_v1' AND parent.menu_type = 'CATALOG';
