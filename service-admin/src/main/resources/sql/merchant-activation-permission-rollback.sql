-- 仅撤销独立激活权限；商户冻结/解冻权限和商户数据保持不变。
SET NAMES utf8mb4;
START TRANSACTION;

SET @admin_app_id = (
    SELECT MIN(id)
    FROM sys_app
    WHERE app_code = 'ADMIN' AND deleted = 0
);
SET @merchant_activate_permission_id = (
    SELECT MIN(id)
    FROM sys_permission
    WHERE app_id = @admin_app_id
      AND permission_code = 'merchant:info:activate'
      AND deleted = 0
);
SET @merchant_activate_menu_id = (
    SELECT MIN(id)
    FROM sys_menu
    WHERE app_id = @admin_app_id
      AND menu_code = 'merchant_info_activate'
      AND deleted = 0
);

DELETE FROM sys_role_permission
WHERE app_id = @admin_app_id
  AND permission_id = @merchant_activate_permission_id;

DELETE FROM sys_role_menu
WHERE app_id = @admin_app_id
  AND menu_id = @merchant_activate_menu_id;

DELETE FROM sys_permission
WHERE app_id = @admin_app_id
  AND id = @merchant_activate_permission_id;

DELETE FROM sys_menu
WHERE app_id = @admin_app_id
  AND id = @merchant_activate_menu_id;

COMMIT;
