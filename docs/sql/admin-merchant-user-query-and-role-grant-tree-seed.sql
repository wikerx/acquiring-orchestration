-- 管理系统商户用户查询、商户角色弹窗授权补充菜单与权限。
-- 执行前确认 sys_app 中 ADMIN、MERCHANT 的 app_code 存在；本脚本不修改表结构。

-- 1. 管理系统：商户管理 -> 商户用户查询。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id,
       parent.id,
       'admin_merchant_user_query_v1',
       '商户用户查询',
       'MENU',
       '/merchant/user-query',
       'merchant/user-query',
       'admin:merchant:user:list',
       'User',
       1,
       0,
       0,
       23,
       1,
       0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
  AND parent.menu_code IN ('admin_merchant_catalog_v3', 'admin_merchant_center', 'merchant_manage', 'admin_merchant_catalog')
  AND parent.deleted = 0
ORDER BY CASE parent.menu_code
    WHEN 'admin_merchant_catalog_v3' THEN 1
    WHEN 'admin_merchant_center' THEN 2
    WHEN 'merchant_manage' THEN 3
    ELSE 4
END
LIMIT 1
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_name = VALUES(menu_name),
    route_path = VALUES(route_path),
    component_path = VALUES(component_path),
    permission_code = VALUES(permission_code),
    icon = VALUES(icon),
    visible = VALUES(visible),
    sort_no = VALUES(sort_no),
    status = VALUES(status);

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, status, deleted
)
SELECT app.id, menu.id, seed.permission_code, seed.permission_name, seed.permission_type,
       seed.resource_method, seed.resource_path, 1, 0
FROM sys_app app
JOIN sys_menu menu ON menu.app_id = app.id
JOIN (
    SELECT 'admin:merchant:user:list' AS permission_code, '商户用户查询' AS permission_name,
           'MENU' AS permission_type, 'GET' AS resource_method, '/admin/merchant-users' AS resource_path
    UNION ALL
    SELECT 'admin:merchant:user:detail', '商户用户详情',
           'BUTTON', 'GET', '/admin/merchant-users/*'
) seed
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
  AND menu.menu_code = 'admin_merchant_user_query_v1'
  AND menu.deleted = 0
ON DUPLICATE KEY UPDATE
    menu_id = VALUES(menu_id),
    permission_name = VALUES(permission_name),
    permission_type = VALUES(permission_type),
    resource_method = VALUES(resource_method),
    resource_path = VALUES(resource_path),
    status = VALUES(status);

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT app.id, role.id, menu.id, 0
FROM sys_app app
JOIN sys_role role ON role.app_id = app.id
JOIN sys_menu menu ON menu.app_id = app.id
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
  AND role.role_code = 'ADMIN_OPERATOR'
  AND role.deleted = 0
  AND menu.menu_code = 'admin_merchant_user_query_v1'
  AND menu.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT app.id, role.id, permission.id, 0
FROM sys_app app
JOIN sys_role role ON role.app_id = app.id
JOIN sys_permission permission ON permission.app_id = app.id
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
  AND role.role_code = 'ADMIN_OPERATOR'
  AND role.deleted = 0
  AND permission.permission_code IN ('admin:merchant:user:list', 'admin:merchant:user:detail')
  AND permission.deleted = 0;

-- 2. 商户系统角色管理：补齐详情、状态和合并授权树权限。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, role_menu.id, seed.menu_code, seed.menu_name, 'BUTTON', NULL, NULL,
       seed.permission_code, NULL, 0, 0, 0, seed.sort_no, 1, 0
FROM sys_app app
JOIN sys_menu role_menu ON role_menu.app_id = app.id
JOIN (
    SELECT 'merchant_system_role_detail_v1' AS menu_code, '角色详情' AS menu_name,
           'merchant:system:role:detail' AS permission_code, 940 AS sort_no
    UNION ALL
    SELECT 'merchant_system_role_status_v1', '启停角色',
           'merchant:system:role:status', 944
) seed
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND role_menu.menu_code = 'merchant_system_role_v1'
  AND role_menu.deleted = 0
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_name = VALUES(menu_name),
    permission_code = VALUES(permission_code),
    sort_no = VALUES(sort_no),
    status = VALUES(status);

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, status, deleted
)
SELECT app.id, menu.id, seed.permission_code, seed.permission_name, seed.permission_type,
       seed.resource_method, seed.resource_path, 1, 0
FROM sys_app app
JOIN (
    SELECT 'merchant_system_role_detail_v1' AS menu_code, 'merchant:system:role:detail' AS permission_code,
           '角色详情' AS permission_name, 'BUTTON' AS permission_type, 'GET' AS resource_method, '/merchant/system/roles/*' AS resource_path
    UNION ALL
    SELECT 'merchant_system_role_status_v1', 'merchant:system:role:status',
           '启停角色', 'BUTTON', 'PUT', '/merchant/system/roles/*/status'
    UNION ALL
    SELECT 'merchant_system_role_auth_v1', 'merchant:system:role:grantMenu',
           '角色菜单与功能授权查询保存', 'MENU', '*', '/merchant/system/roles/*/grant-tree'
) seed
JOIN sys_menu menu ON menu.app_id = app.id AND menu.menu_code = seed.menu_code
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND menu.deleted = 0
ON DUPLICATE KEY UPDATE
    menu_id = VALUES(menu_id),
    permission_name = VALUES(permission_name),
    permission_type = VALUES(permission_type),
    resource_method = VALUES(resource_method),
    resource_path = VALUES(resource_path),
    status = VALUES(status);

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT app.id, role.id, menu.id, 0
FROM sys_app app
JOIN sys_role role ON role.app_id = app.id
JOIN sys_menu menu ON menu.app_id = app.id
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND role.role_type = 'SYSTEM'
  AND role.deleted = 0
  AND menu.menu_code IN ('merchant_system_role_detail_v1', 'merchant_system_role_status_v1')
  AND menu.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT app.id, role.id, permission.id, 0
FROM sys_app app
JOIN sys_role role ON role.app_id = app.id
JOIN sys_permission permission ON permission.app_id = app.id
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND role.role_type = 'SYSTEM'
  AND role.deleted = 0
  AND permission.permission_code IN (
      'merchant:system:role:detail',
      'merchant:system:role:status',
      'merchant:system:role:grantMenu'
  )
  AND permission.deleted = 0;
