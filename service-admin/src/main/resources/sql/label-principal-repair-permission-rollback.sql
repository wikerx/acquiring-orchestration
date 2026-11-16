-- 仅撤销未结算历史标签本金修复的两个按钮权限；不影响交易清分页面及其他权限。
SET NAMES utf8mb4;
START TRANSACTION;

SET @admin_app_id = (
    SELECT MIN(id)
    FROM sys_app
    WHERE app_code = 'ADMIN' AND deleted = 0
);

DELETE role_permission
FROM sys_role_permission role_permission
JOIN sys_permission permission
  ON permission.app_id = role_permission.app_id
 AND permission.id = role_permission.permission_id
WHERE role_permission.app_id = @admin_app_id
  AND permission.permission_code IN (
      'clearing:principal-repair:submit',
      'clearing:principal-repair:review'
  );

DELETE role_menu
FROM sys_role_menu role_menu
JOIN sys_menu menu
  ON menu.app_id = role_menu.app_id
 AND menu.id = role_menu.menu_id
WHERE role_menu.app_id = @admin_app_id
  AND menu.menu_code IN (
      'admin_transaction_label_principal_repair_submit_v1',
      'admin_transaction_label_principal_repair_review_v1'
  );

DELETE FROM sys_permission
WHERE app_id = @admin_app_id
  AND permission_code IN (
      'clearing:principal-repair:submit',
      'clearing:principal-repair:review'
  );

DELETE FROM sys_menu
WHERE app_id = @admin_app_id
  AND menu_code IN (
      'admin_transaction_label_principal_repair_submit_v1',
      'admin_transaction_label_principal_repair_review_v1'
  );

COMMIT;
