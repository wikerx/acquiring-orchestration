-- 商户下载中心迁移预检：只读确认应用、商户管理员角色和现有导出权限。
SET NAMES utf8mb4;

SELECT id, app_code, app_name, status
FROM sys_app
WHERE app_code = 'MERCHANT' AND deleted = 0;

SELECT menu.id, menu.menu_code, menu.menu_name, menu.menu_type, menu.status
FROM sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
WHERE menu.menu_code = 'merchant_system_catalog_v1' AND menu.deleted = 0;

SELECT role_code, COUNT(*) AS role_count
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
WHERE role.deleted = 0
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
GROUP BY role_code;

SELECT permission.permission_code, permission.resource_method,
       permission.resource_path, permission.status
FROM sys_permission permission
JOIN sys_app app ON app.id = permission.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
WHERE permission.deleted = 0
  AND permission.permission_code IN (
      'merchant:transaction:order:export',
      'merchant:transaction:refund:export'
  );
