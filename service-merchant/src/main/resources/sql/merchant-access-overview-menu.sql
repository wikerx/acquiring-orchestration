SET NAMES utf8mb4;

-- 商户支付接入概览菜单和只读权限。脚本可重复执行，不修改 OpenAPI 或风控配置。
START TRANSACTION;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, 'merchant_access_overview_v1', '接入概览', 'MENU',
       '/access-config/overview', 'access-config/overview',
       'merchant:access-config:overview:view', 'Connection', 1, 1, 0, 81, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_access_config_catalog_v1'
                    AND parent.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu exists_menu
      WHERE exists_menu.app_id = app.id
        AND exists_menu.menu_code = 'merchant_access_overview_v1'
        AND exists_menu.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_access_config_catalog_v1'
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = '接入概览',
    menu.menu_type = 'MENU',
    menu.route_path = '/access-config/overview',
    menu.component_path = 'access-config/overview',
    menu.permission_code = 'merchant:access-config:overview:view',
    menu.icon = 'Connection',
    menu.visible = 1,
    menu.keep_alive = 1,
    menu.external_link = 0,
    menu.sort_no = 81,
    menu.status = 1
WHERE menu.menu_code = 'merchant_access_overview_v1'
  AND menu.deleted = 0;

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT app.id, menu.id, 'merchant:access-config:overview:view', '支付接入概览查询', 'MENU',
       'GET', '/merchant/access-config/overview',
       '查询当前认证商户的非敏感接入状态汇总', 1, 0
FROM sys_app app
JOIN sys_menu menu ON menu.app_id = app.id
                  AND menu.menu_code = 'merchant_access_overview_v1'
                  AND menu.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_permission exists_permission
      WHERE exists_permission.app_id = app.id
        AND exists_permission.permission_code = 'merchant:access-config:overview:view'
        AND exists_permission.deleted = 0
  );

UPDATE sys_permission permission
JOIN sys_app app ON app.id = permission.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = app.id
                  AND menu.menu_code = 'merchant_access_overview_v1'
                  AND menu.deleted = 0
SET permission.menu_id = menu.id,
    permission.permission_name = '支付接入概览查询',
    permission.permission_type = 'MENU',
    permission.resource_method = 'GET',
    permission.resource_path = '/merchant/access-config/overview',
    permission.description = '查询当前认证商户的非敏感接入状态汇总',
    permission.status = 1
WHERE permission.permission_code = 'merchant:access-config:overview:view'
  AND permission.deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT role.app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = role.app_id
                  AND menu.menu_code = 'merchant_access_overview_v1'
                  AND menu.deleted = 0
WHERE role.deleted = 0
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%');

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT role.app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = role.app_id
                              AND permission.permission_code = 'merchant:access-config:overview:view'
                              AND permission.deleted = 0
WHERE role.deleted = 0
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%');

INSERT IGNORE INTO sys_merchant_menu_grant (
    merchant_id, app_id, menu_id, grant_source, status, created_at, updated_at, deleted
)
SELECT merchant.merchant_id, menu.app_id, menu.id, 'SYSTEM', 1,
       CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM base_merchant_info merchant
JOIN sys_app app ON app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = app.id
                  AND menu.menu_code = 'merchant_access_overview_v1'
                  AND menu.deleted = 0
WHERE merchant.deleted = 0;

INSERT IGNORE INTO sys_merchant_permission_grant (
    merchant_id, app_id, permission_id, grant_source, status, created_at, updated_at, deleted
)
SELECT merchant.merchant_id, permission.app_id, permission.id, 'SYSTEM', 1,
       CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM base_merchant_info merchant
JOIN sys_app app ON app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = app.id
                              AND permission.permission_code = 'merchant:access-config:overview:view'
                              AND permission.deleted = 0
WHERE merchant.deleted = 0;

COMMIT;
