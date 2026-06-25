-- OpenAPI 商户密钥管理菜单与权限种子数据。
-- 用途：补齐管理端 OpenAPI 对接材料权限，以及商户端“商户信息管理/商户密钥管理”入口。
-- 可重复执行；仅写入菜单、权限和默认角色授权，不包含任何测试密钥。

SET NAMES utf8mb4;

-- 1. 管理系统：商户信息页 OpenAPI 对接材料按钮权限。
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
JOIN (
    SELECT candidate.id, candidate.app_id
    FROM sys_menu candidate
    JOIN sys_app candidate_app
      ON candidate_app.id = candidate.app_id
     AND candidate_app.app_code = 'ADMIN'
     AND candidate_app.deleted = 0
    WHERE candidate.menu_code IN ('merchant_info_manage', 'merchant_info', 'admin_merchant_info_v1', 'admin_merchant_info')
      AND candidate.deleted = 0
    ORDER BY CASE candidate.menu_code
        WHEN 'merchant_info_manage' THEN 1
        WHEN 'merchant_info' THEN 2
        WHEN 'admin_merchant_info_v1' THEN 3
        ELSE 3
    END
    LIMIT 1
) menu
  ON menu.app_id = app.id
JOIN (
    SELECT 'merchant:material:view' AS permission_code, 'OpenAPI对接材料查看' AS permission_name,
           'BUTTON' AS permission_type, 'GET' AS resource_method, '/admin/openapi/merchant-keys/*,/admin/merchants/*/keys' AS resource_path
    UNION ALL
    SELECT 'merchant:material:copy' AS permission_code, 'OpenAPI对接材料复制' AS permission_name,
           'BUTTON' AS permission_type, 'POST' AS resource_method, '/admin/openapi/merchant-keys/*/copy,/admin/openapi/merchant-keys/*/view' AS resource_path
    UNION ALL
    SELECT 'merchant:material:download', 'OpenAPI对接材料下载',
           'BUTTON', 'GET', '/admin/openapi/merchant-keys/*/download'
    UNION ALL
    SELECT 'merchant:material:logs', 'OpenAPI对接材料操作记录',
           'BUTTON', 'POST', '/admin/openapi/merchant-keys/*/logs'
    UNION ALL
    SELECT 'merchant:material:private', 'OpenAPI私钥材料导出',
           'BUTTON', '*', '/admin/openapi/merchant-keys/*'
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

-- 本地管理端默认角色需要可完成 OpenAPI 对接材料交付；生产环境可按岗位收回 merchant:material:private。
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
      'merchant:material:view',
      'merchant:material:copy',
      'merchant:material:download',
      'merchant:material:logs',
      'merchant:material:private'
 )
 AND permission.deleted = 0
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0;

-- 2. 商户系统：商户信息管理目录。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id,
       0,
       'merchant_info_catalog_v1',
       '商户信息管理',
       'CATALOG',
       '/merchant-info',
       NULL,
       NULL,
       'User',
       1,
       0,
       0,
       20,
       1,
       0
FROM sys_app app
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
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

-- 3. 商户系统：商户密钥管理页面。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id,
       parent.id,
       'merchant_openapi_key_manage_v1',
       '商户密钥管理',
       'MENU',
       '/merchant-info/openapi-keys',
       'merchant-info/openapi-keys',
       'merchant:openapi:key:view',
       'Key',
       1,
       0,
       0,
       21,
       1,
       0
FROM sys_app app
JOIN sys_menu parent
  ON parent.app_id = app.id
 AND parent.menu_code = 'merchant_info_catalog_v1'
 AND parent.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
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

-- 4. 商户系统：页面按钮权限。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id,
       page.id,
       seed.menu_code,
       seed.menu_name,
       'BUTTON',
       NULL,
       NULL,
       seed.permission_code,
       NULL,
       0,
       0,
       0,
       seed.sort_no,
       1,
       0
FROM sys_app app
JOIN sys_menu page
  ON page.app_id = app.id
 AND page.menu_code = 'merchant_openapi_key_manage_v1'
 AND page.deleted = 0
JOIN (
    SELECT 'merchant_openapi_key_copy_v1' AS menu_code, '复制接入材料' AS menu_name,
           'merchant:openapi:key:copy' AS permission_code, 211 AS sort_no
    UNION ALL
    SELECT 'merchant_openapi_key_download_v1', '下载接入材料',
           'merchant:openapi:key:download', 212
    UNION ALL
    SELECT 'merchant_openapi_key_download_private_v1', '导出敏感接入材料',
           'merchant:openapi:key:download-private', 213
    UNION ALL
    SELECT 'merchant_openapi_key_rotate_jwt_v1', '轮换JWT密钥',
           'merchant:openapi:key:rotate-jwt', 214
    UNION ALL
    SELECT 'merchant_openapi_key_rotate_response_v1', '轮换响应密钥',
           'merchant:openapi:key:rotate-response', 215
    UNION ALL
    SELECT 'merchant_openapi_key_log_v1', '查看操作记录',
           'merchant:openapi:key:log', 216
) seed
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type),
    route_path = VALUES(route_path),
    component_path = VALUES(component_path),
    permission_code = VALUES(permission_code),
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
       COALESCE(button.id, page.id),
       seed.permission_code,
       seed.permission_name,
       seed.permission_type,
       seed.resource_method,
       seed.resource_path,
       1,
       0
FROM sys_app app
JOIN sys_menu page
  ON page.app_id = app.id
 AND page.menu_code = 'merchant_openapi_key_manage_v1'
 AND page.deleted = 0
JOIN (
    SELECT 'merchant:openapi:key:view' AS permission_code, '商户OpenAPI密钥查看' AS permission_name,
           'MENU' AS permission_type, '*' AS resource_method, '/merchant/openapi/keys*' AS resource_path
    UNION ALL
    SELECT 'merchant:openapi:key:copy', '商户OpenAPI材料复制',
           'BUTTON', 'POST', '/merchant/openapi/keys/copy'
    UNION ALL
    SELECT 'merchant:openapi:key:download', '商户OpenAPI材料下载',
           'BUTTON', 'GET', '/merchant/openapi/keys/download'
    UNION ALL
    SELECT 'merchant:openapi:key:download-private', '商户OpenAPI敏感材料导出',
           'BUTTON', '*', '/merchant/openapi/keys/*'
    UNION ALL
    SELECT 'merchant:openapi:key:rotate-jwt', '商户OpenAPI JWT密钥轮换',
           'BUTTON', 'POST', '/merchant/openapi/keys/rotate'
    UNION ALL
    SELECT 'merchant:openapi:key:rotate-response', '商户OpenAPI响应密钥轮换',
           'BUTTON', 'POST', '/merchant/openapi/keys/rotate'
    UNION ALL
    SELECT 'merchant:openapi:key:log', '商户OpenAPI操作记录',
           'BUTTON', 'GET', '/merchant/openapi/keys/logs'
) seed
LEFT JOIN sys_menu button
  ON button.app_id = app.id
 AND button.permission_code = seed.permission_code
 AND button.menu_type = 'BUTTON'
 AND button.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
ON DUPLICATE KEY UPDATE
    menu_id = VALUES(menu_id),
    permission_name = VALUES(permission_name),
    permission_type = VALUES(permission_type),
    resource_method = VALUES(resource_method),
    resource_path = VALUES(resource_path),
    status = VALUES(status);

-- 默认商户管理员可以查看、复制、下载和轮换；私钥导出权限需人工单独授予。
INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT app.id, role.id, menu.id, 0
FROM sys_app app
JOIN sys_role role
  ON role.app_id = app.id
 AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
 AND role.deleted = 0
JOIN sys_menu menu
  ON menu.app_id = app.id
 AND menu.menu_code IN ('merchant_info_catalog_v1', 'merchant_openapi_key_manage_v1')
 AND menu.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT app.id, role.id, permission.id, 0
FROM sys_app app
JOIN sys_role role
  ON role.app_id = app.id
 AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
 AND role.deleted = 0
JOIN sys_permission permission
  ON permission.app_id = app.id
 AND permission.permission_code IN (
      'merchant:openapi:key:view',
      'merchant:openapi:key:copy',
      'merchant:openapi:key:download',
      'merchant:openapi:key:download-private',
      'merchant:openapi:key:rotate-jwt',
      'merchant:openapi:key:rotate-response',
      'merchant:openapi:key:log'
 )
 AND permission.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0;
