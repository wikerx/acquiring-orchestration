-- 管理系统“商户系统菜单管理”入口与权限种子数据。
-- 用途：让后台操作员在管理系统维护 MERCHANT 应用下的目录、菜单、按钮权限。
-- 可重复执行；依赖 sys_app 中 ADMIN 应用、sys_role 中 ADMIN_OPERATOR 角色。

SET NAMES utf8mb4;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id,
       parent.id,
       'admin_merchant_menu_manage_v1',
       '商户系统菜单管理',
       'MENU',
       '/merchant/menu-manage',
       'merchant/menu-manage',
       'merchant:menu-manage:list',
       'Menu',
       1,
       0,
       0,
       21,
       1,
       0
FROM sys_app app
JOIN sys_menu parent
  ON parent.app_id = app.id
 AND parent.menu_code IN ('merchant_manage', 'admin_merchant_catalog_v3', 'admin_merchant_center', 'admin_merchant_catalog')
 AND parent.deleted = 0
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
ORDER BY CASE parent.menu_code
    WHEN 'merchant_manage' THEN 1
    WHEN 'admin_merchant_catalog_v3' THEN 2
    WHEN 'admin_merchant_center' THEN 3
    ELSE 4
END
LIMIT 1
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type),
    route_path = VALUES(route_path),
    component_path = VALUES(component_path),
    permission_code = VALUES(permission_code),
    icon = VALUES(icon),
    visible = VALUES(visible),
    keep_alive = VALUES(keep_alive),
    external_link = VALUES(external_link),
    sort_no = VALUES(sort_no),
    status = VALUES(status);

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, status, deleted
)
SELECT app.id,
       menu.id,
       seed.permission_code,
       seed.permission_name,
       seed.permission_type,
       seed.resource_method,
       seed.resource_path,
       1,
       0
FROM sys_app app
JOIN sys_menu menu
  ON menu.app_id = app.id
 AND menu.menu_code = 'admin_merchant_menu_manage_v1'
 AND menu.deleted = 0
JOIN (
    SELECT 'merchant:menu-manage:list' AS permission_code, '商户系统菜单查询' AS permission_name,
           'MENU' AS permission_type, 'POST' AS resource_method, '/admin/merchant/menus/tree' AS resource_path
    UNION ALL
    SELECT 'merchant:menu-manage:add', '商户系统菜单新增',
           'BUTTON', 'POST', '/admin/merchant/menus/create'
    UNION ALL
    SELECT 'merchant:menu-manage:edit', '商户系统菜单编辑',
           'BUTTON', 'POST', '/admin/merchant/menus/*'
    UNION ALL
    SELECT 'merchant:menu-manage:remove', '商户系统菜单删除',
           'BUTTON', 'POST', '/admin/merchant/menus/delete'
) seed
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
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
JOIN sys_role role
  ON role.app_id = app.id
 AND role.role_code = 'ADMIN_OPERATOR'
 AND role.deleted = 0
JOIN sys_menu menu
  ON menu.app_id = app.id
 AND menu.menu_code = 'admin_merchant_menu_manage_v1'
 AND menu.deleted = 0
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT app.id, role.id, permission.id, 0
FROM sys_app app
JOIN sys_role role
  ON role.app_id = app.id
 AND role.role_code = 'ADMIN_OPERATOR'
 AND role.deleted = 0
JOIN sys_permission permission
  ON permission.app_id = app.id
 AND permission.permission_code IN (
      'merchant:menu-manage:list',
      'merchant:menu-manage:add',
      'merchant:menu-manage:edit',
      'merchant:menu-manage:remove'
 )
 AND permission.deleted = 0
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0;
