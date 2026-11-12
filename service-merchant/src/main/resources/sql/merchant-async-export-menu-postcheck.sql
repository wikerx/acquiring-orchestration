-- 商户下载中心迁移后检：菜单、权限、管理员授权和商户授权必须完整。
SET NAMES utf8mb4;

SELECT menu.menu_code, CONVERT(menu.menu_name USING utf8mb4) AS menu_name,
       menu.menu_type, menu.route_path, menu.component_path, menu.permission_code,
       menu.sort_no, menu.visible, menu.status
FROM sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
WHERE menu.deleted = 0
  AND menu.menu_code IN (
      'merchant_export_task_center_v1', 'merchant_export_task_detail_v1',
      'merchant_export_task_download_v1', 'merchant_export_task_retry_v1'
  )
ORDER BY menu.sort_no;

SELECT permission.permission_code, CONVERT(permission.permission_name USING utf8mb4) AS permission_name,
       permission.resource_method, permission.resource_path, permission.status
FROM sys_permission permission
JOIN sys_app app ON app.id = permission.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
WHERE permission.deleted = 0
  AND permission.permission_code IN (
      'merchant:operations:export-task:list', 'merchant:operations:export-task:detail',
      'merchant:operations:export-task:download', 'merchant:operations:export-task:retry',
      'merchant:transaction:order:export', 'merchant:transaction:refund:export'
  )
ORDER BY permission.permission_code;

SELECT
    (SELECT COUNT(*) FROM sys_role_permission rp
     JOIN sys_role role ON role.id = rp.role_id AND role.deleted = 0
     JOIN sys_permission permission ON permission.id = rp.permission_id AND permission.deleted = 0
     WHERE rp.deleted = 0
       AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
       AND permission.permission_code LIKE 'merchant:operations:export-task:%') AS admin_role_permission_count,
    (SELECT COUNT(*) FROM sys_merchant_permission_grant grant_row
     JOIN sys_permission permission ON permission.id = grant_row.permission_id AND permission.deleted = 0
     WHERE grant_row.deleted = 0 AND grant_row.status = 1
       AND permission.permission_code LIKE 'merchant:operations:export-task:%') AS merchant_permission_grant_count;
