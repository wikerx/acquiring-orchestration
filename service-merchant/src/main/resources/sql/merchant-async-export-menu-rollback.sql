-- 回退说明：保留历史导出任务和文件，只撤销商户页面、授权与新提交入口元数据。
SET NAMES utf8mb4;

START TRANSACTION;

SET @merchant_app_id = (
    SELECT id FROM sys_app WHERE app_code = 'MERCHANT' AND deleted = 0 ORDER BY id LIMIT 1
);

UPDATE sys_merchant_permission_grant grant_row
JOIN sys_permission permission ON permission.id = grant_row.permission_id
SET grant_row.status = 0, grant_row.updated_at = CURRENT_TIMESTAMP(3)
WHERE grant_row.app_id = @merchant_app_id
  AND permission.permission_code LIKE 'merchant:operations:export-task:%'
  AND grant_row.deleted = 0;

UPDATE sys_merchant_menu_grant grant_row
JOIN sys_menu menu ON menu.id = grant_row.menu_id
SET grant_row.status = 0, grant_row.updated_at = CURRENT_TIMESTAMP(3)
WHERE grant_row.app_id = @merchant_app_id
  AND menu.menu_code LIKE 'merchant_export_task_%'
  AND grant_row.deleted = 0;

UPDATE sys_permission
SET status = 0, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = @merchant_app_id
  AND permission_code LIKE 'merchant:operations:export-task:%' AND deleted = 0;

UPDATE sys_menu
SET visible = 0, status = 0, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = @merchant_app_id
  AND menu_code LIKE 'merchant_export_task_%' AND deleted = 0;

COMMIT;
