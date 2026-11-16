-- 仅回退下载中心菜单层级；页面、权限和历史任务继续保留。
SET NAMES utf8mb4;

START TRANSACTION;

UPDATE sys_menu page
JOIN sys_app app ON app.id = page.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu parent ON parent.id = page.parent_id AND parent.app_id = app.id
                    AND parent.menu_code = 'merchant_system_catalog_v1' AND parent.deleted = 0
SET page.parent_id = 0, page.sort_no = 10, page.updated_at = CURRENT_TIMESTAMP(3)
WHERE page.menu_code = 'merchant_export_task_center_v1' AND page.deleted = 0;

COMMIT;
