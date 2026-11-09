-- 仅撤销历史标签本金差额调整的三个隐藏按钮和资源权限。
SET NAMES utf8mb4;
START TRANSACTION;

SET @admin_app_id = (
    SELECT MIN(id) FROM sys_app WHERE app_code = 'ADMIN' AND deleted = 0
);

DELETE role_permission
FROM sys_role_permission role_permission
JOIN sys_permission permission
  ON permission.app_id = role_permission.app_id
 AND permission.id = role_permission.permission_id
WHERE role_permission.app_id = @admin_app_id
  AND permission.permission_code IN (
      'settlement:principal-correction:submit',
      'settlement:principal-correction:approve',
      'settlement:principal-correction:reject'
  );

DELETE role_menu
FROM sys_role_menu role_menu
JOIN sys_menu menu
  ON menu.app_id = role_menu.app_id
 AND menu.id = role_menu.menu_id
WHERE role_menu.app_id = @admin_app_id
  AND menu.menu_code IN (
      'admin_settlement_principal_correction_submit_v1',
      'admin_settlement_principal_correction_approve_v1',
      'admin_settlement_principal_correction_reject_v1'
  );

DELETE FROM sys_permission
WHERE app_id = @admin_app_id
  AND permission_code IN (
      'settlement:principal-correction:submit',
      'settlement:principal-correction:approve',
      'settlement:principal-correction:reject'
  );

DELETE FROM sys_menu
WHERE app_id = @admin_app_id
  AND menu_code IN (
      'admin_settlement_principal_correction_submit_v1',
      'admin_settlement_principal_correction_approve_v1',
      'admin_settlement_principal_correction_reject_v1'
  );

COMMIT;
