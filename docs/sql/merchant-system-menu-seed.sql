-- 商户系统基础功能菜单与权限种子数据。
-- 用途：为已有库补齐管理端“商户菜单授权”入口，以及商户端系统管理菜单授权范围。
-- 执行前请确认 sys_app 中 ADMIN=1、MERCHANT=2；如不同，请先按 app_code 调整 app_id。

-- 1. 管理系统：商户管理 -> 商户菜单授权。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT 1,
       parent.id,
       'admin_merchant_menu_grant_v3',
       '商户菜单授权',
       'MENU',
       '/merchant/menu-grant',
       'merchant/menu-grant',
       'merchant:menu-grant:list',
       'Menu',
       1,
       0,
       0,
       22,
       1,
       0
FROM sys_menu parent
WHERE parent.app_id = 1
  AND parent.menu_code IN ('merchant_manage', 'admin_merchant_catalog_v3', 'admin_merchant_center', 'admin_merchant_catalog')
  AND parent.deleted = 0
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
    sort_no = VALUES(sort_no),
    status = VALUES(status);

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, status, deleted
)
SELECT 1, menu.id, seed.permission_code, seed.permission_name, seed.permission_type,
       seed.resource_method, seed.resource_path, 1, 0
FROM sys_menu menu
JOIN (
    SELECT 'merchant:menu-grant:list' AS permission_code, '商户菜单授权查询' AS permission_name,
           'MENU' AS permission_type, 'GET' AS resource_method, '/admin/merchant-menu-grants/*' AS resource_path
    UNION ALL
    SELECT 'merchant:menu-grant:save', '商户菜单授权保存',
           'BUTTON', 'POST', '/admin/merchant-menu-grants/*'
) seed
WHERE menu.app_id = 1
  AND menu.menu_code = 'admin_merchant_menu_grant_v3'
  AND menu.deleted = 0
ON DUPLICATE KEY UPDATE
    menu_id = VALUES(menu_id),
    permission_name = VALUES(permission_name),
    permission_type = VALUES(permission_type),
    resource_method = VALUES(resource_method),
    resource_path = VALUES(resource_path),
    status = VALUES(status);

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 1, role.id, menu.id, 0
FROM sys_role role
JOIN sys_menu menu ON menu.app_id = role.app_id
WHERE role.app_id = 1
  AND role.role_code = 'ADMIN_OPERATOR'
  AND role.deleted = 0
  AND menu.menu_code = 'admin_merchant_menu_grant_v3'
  AND menu.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 1, role.id, permission.id, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id
WHERE role.app_id = 1
  AND role.role_code = 'ADMIN_OPERATOR'
  AND role.deleted = 0
  AND permission.permission_code IN ('merchant:menu-grant:list', 'merchant:menu-grant:save')
  AND permission.deleted = 0;

-- 2. 商户系统：可被平台授权给商户的系统管理菜单。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
VALUES
    (2, 0, 'merchant_system_catalog_v1', '系统管理', 'CATALOG', '/system', NULL, NULL, 'Setting', 1, 0, 0, 90, 1, 0)
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type),
    route_path = VALUES(route_path),
    component_path = VALUES(component_path),
    permission_code = VALUES(permission_code),
    icon = VALUES(icon),
    visible = VALUES(visible),
    sort_no = VALUES(sort_no),
    status = VALUES(status);

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT 2, parent.id, seed.menu_code, seed.menu_name, 'MENU', seed.route_path, seed.component_path,
       seed.permission_code, seed.icon, 1, 0, 0, seed.sort_no, 1, 0
FROM sys_menu parent
JOIN (
    SELECT 'merchant_system_dept_v1' AS menu_code, '组织架构' AS menu_name,
           '/system/dept' AS route_path, 'system/dept' AS component_path,
           'merchant:system:dept:list' AS permission_code, 'OfficeBuilding' AS icon, 91 AS sort_no
    UNION ALL
    SELECT 'merchant_system_post_v1', '岗位管理', '/system/post', 'system/post',
           'merchant:system:post:list', 'Postcard', 92
    UNION ALL
    SELECT 'merchant_system_account_v1', '员工账号', '/system/account', 'system/account',
           'merchant:system:account:list', 'User', 93
    UNION ALL
    SELECT 'merchant_system_role_v1', '角色管理', '/system/role', 'system/role',
           'merchant:system:role:list', 'Lock', 94
    UNION ALL
    SELECT 'merchant_system_role_auth_v1', '角色授权', '/system/role-auth', 'system/role-auth',
           'merchant:system:role:grantMenu', 'Unlock', 95
) seed
WHERE parent.app_id = 2
  AND parent.menu_code = 'merchant_system_catalog_v1'
  AND parent.deleted = 0
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type),
    route_path = VALUES(route_path),
    component_path = VALUES(component_path),
    permission_code = VALUES(permission_code),
    icon = VALUES(icon),
    visible = VALUES(visible),
    sort_no = VALUES(sort_no),
    status = VALUES(status);

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT 2, menu.id, seed.menu_code, seed.menu_name, 'BUTTON', NULL, NULL,
       seed.permission_code, NULL, 0, 0, 0, seed.sort_no, 1, 0
FROM (
    SELECT 'merchant_system_dept_add_v1' AS menu_code, '新增部门' AS menu_name,
           'merchant_system_dept_v1' AS parent_code, 'merchant:system:dept:add' AS permission_code, 911 AS sort_no
    UNION ALL SELECT 'merchant_system_dept_edit_v1', '编辑部门', 'merchant_system_dept_v1', 'merchant:system:dept:edit', 912
    UNION ALL SELECT 'merchant_system_dept_delete_v1', '删除部门', 'merchant_system_dept_v1', 'merchant:system:dept:delete', 913
    UNION ALL SELECT 'merchant_system_post_add_v1', '新增岗位', 'merchant_system_post_v1', 'merchant:system:post:add', 921
    UNION ALL SELECT 'merchant_system_post_edit_v1', '编辑岗位', 'merchant_system_post_v1', 'merchant:system:post:edit', 922
    UNION ALL SELECT 'merchant_system_post_delete_v1', '删除岗位', 'merchant_system_post_v1', 'merchant:system:post:delete', 923
    UNION ALL SELECT 'merchant_system_account_add_v1', '新增员工', 'merchant_system_account_v1', 'merchant:system:account:add', 931
    UNION ALL SELECT 'merchant_system_account_edit_v1', '编辑员工', 'merchant_system_account_v1', 'merchant:system:account:edit', 932
    UNION ALL SELECT 'merchant_system_account_delete_v1', '删除员工', 'merchant_system_account_v1', 'merchant:system:account:delete', 933
    UNION ALL SELECT 'merchant_system_account_status_v1', '启停员工', 'merchant_system_account_v1', 'merchant:system:account:status', 934
    UNION ALL SELECT 'merchant_system_account_assign_role_v1', '员工分配角色', 'merchant_system_account_v1', 'merchant:system:account:assignRole', 935
    UNION ALL SELECT 'merchant_system_role_add_v1', '新增角色', 'merchant_system_role_v1', 'merchant:system:role:add', 941
    UNION ALL SELECT 'merchant_system_role_edit_v1', '编辑角色', 'merchant_system_role_v1', 'merchant:system:role:edit', 942
    UNION ALL SELECT 'merchant_system_role_delete_v1', '删除角色', 'merchant_system_role_v1', 'merchant:system:role:delete', 943
    UNION ALL SELECT 'merchant_system_role_grant_permission_v1', '角色资源授权', 'merchant_system_role_auth_v1', 'merchant:system:role:grantPermission', 951
) seed
JOIN sys_menu menu
  ON menu.app_id = 2
 AND menu.menu_code = seed.parent_code
 AND menu.deleted = 0
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
SELECT 2, menu.id, seed.permission_code, seed.permission_name, seed.permission_type,
       seed.resource_method, seed.resource_path, 1, 0
FROM (
    SELECT 'merchant_system_dept_v1' AS menu_code, 'merchant:system:dept:list' AS permission_code,
           '组织架构查询' AS permission_name, 'MENU' AS permission_type, 'GET' AS resource_method, '/merchant/system/depts*' AS resource_path
    UNION ALL SELECT 'merchant_system_dept_v1', 'merchant:system:dept:add', '组织架构新增', 'BUTTON', 'POST', '/merchant/system/depts'
    UNION ALL SELECT 'merchant_system_dept_v1', 'merchant:system:dept:edit', '组织架构编辑', 'BUTTON', 'PUT', '/merchant/system/depts/*'
    UNION ALL SELECT 'merchant_system_dept_v1', 'merchant:system:dept:delete', '组织架构删除', 'BUTTON', 'DELETE', '/merchant/system/depts/*'
    UNION ALL SELECT 'merchant_system_post_v1', 'merchant:system:post:list', '岗位查询', 'MENU', 'GET', '/merchant/system/posts'
    UNION ALL SELECT 'merchant_system_post_v1', 'merchant:system:post:add', '岗位新增', 'BUTTON', 'POST', '/merchant/system/posts'
    UNION ALL SELECT 'merchant_system_post_v1', 'merchant:system:post:edit', '岗位编辑', 'BUTTON', 'PUT', '/merchant/system/posts/*'
    UNION ALL SELECT 'merchant_system_post_v1', 'merchant:system:post:delete', '岗位删除', 'BUTTON', 'DELETE', '/merchant/system/posts/*'
    UNION ALL SELECT 'merchant_system_account_v1', 'merchant:system:account:list', '员工账号查询', 'MENU', 'GET', '/merchant/system/accounts'
    UNION ALL SELECT 'merchant_system_account_v1', 'merchant:system:account:add', '员工账号新增', 'BUTTON', 'POST', '/merchant/system/accounts'
    UNION ALL SELECT 'merchant_system_account_v1', 'merchant:system:account:edit', '员工账号编辑', 'BUTTON', 'PUT', '/merchant/system/accounts/*'
    UNION ALL SELECT 'merchant_system_account_v1', 'merchant:system:account:delete', '员工账号删除', 'BUTTON', 'DELETE', '/merchant/system/accounts/*'
    UNION ALL SELECT 'merchant_system_account_v1', 'merchant:system:account:status', '员工账号状态', 'BUTTON', 'PUT', '/merchant/system/accounts/*/status'
    UNION ALL SELECT 'merchant_system_account_v1', 'merchant:system:account:assignRole', '员工分配角色', 'BUTTON', 'POST', '/merchant/system/accounts/*/roles'
    UNION ALL SELECT 'merchant_system_role_v1', 'merchant:system:role:list', '角色查询', 'MENU', 'GET', '/merchant/system/roles'
    UNION ALL SELECT 'merchant_system_role_v1', 'merchant:system:role:add', '角色新增', 'BUTTON', 'POST', '/merchant/system/roles'
    UNION ALL SELECT 'merchant_system_role_v1', 'merchant:system:role:edit', '角色编辑', 'BUTTON', 'PUT', '/merchant/system/roles/*'
    UNION ALL SELECT 'merchant_system_role_v1', 'merchant:system:role:delete', '角色删除', 'BUTTON', 'DELETE', '/merchant/system/roles/*'
    UNION ALL SELECT 'merchant_system_role_auth_v1', 'merchant:system:role:grantMenu', '角色菜单授权', 'MENU', '*', '/merchant/system/roles/*/menus'
    UNION ALL SELECT 'merchant_system_role_auth_v1', 'merchant:system:role:grantPermission', '角色资源授权', 'BUTTON', '*', '/merchant/system/roles/*/permissions'
) seed
JOIN sys_menu menu
  ON menu.app_id = 2
 AND menu.menu_code = COALESCE((
     SELECT button.menu_code
     FROM sys_menu button
     WHERE button.app_id = 2
       AND button.permission_code = seed.permission_code
       AND button.menu_type = 'BUTTON'
       AND button.deleted = 0
     LIMIT 1
 ), seed.menu_code)
 AND menu.deleted = 0
ON DUPLICATE KEY UPDATE
    menu_id = VALUES(menu_id),
    permission_name = VALUES(permission_name),
    permission_type = VALUES(permission_type),
    resource_method = VALUES(resource_method),
    resource_path = VALUES(resource_path),
    status = VALUES(status);
