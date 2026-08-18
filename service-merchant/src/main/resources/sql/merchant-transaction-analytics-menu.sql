SET NAMES utf8mb4;

-- 商户系统交易分析菜单与只读统计权限增量迁移。
START TRANSACTION;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, 'merchant_transaction_analytics_v1', '交易分析', 'MENU',
       '/transaction/analytics', 'transaction/analytics', 'merchant:transaction:analytics:view',
       'DataAnalysis', 1, 1, 0, 30, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_transaction_catalog_v1'
                    AND parent.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu exists_menu
      WHERE exists_menu.app_id = app.id
        AND exists_menu.menu_code = 'merchant_transaction_analytics_v1'
        AND exists_menu.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = menu.app_id
                    AND parent.menu_code = 'merchant_transaction_catalog_v1'
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = '交易分析',
    menu.menu_type = 'MENU',
    menu.route_path = '/transaction/analytics',
    menu.component_path = 'transaction/analytics',
    menu.permission_code = 'merchant:transaction:analytics:view',
    menu.icon = 'DataAnalysis',
    menu.visible = 1,
    menu.keep_alive = 1,
    menu.external_link = 0,
    menu.sort_no = 30,
    menu.status = 1,
    menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.menu_code = 'merchant_transaction_analytics_v1'
  AND menu.deleted = 0;

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT app.id, menu.id, 'merchant:transaction:analytics:view', '交易分析查询', 'MENU',
       'POST', '/merchant/transactions/analytics/**', '查询当前登录商户交易总览和失败分析', 1, 0
FROM sys_app app
JOIN sys_menu menu ON menu.app_id = app.id
                  AND menu.menu_code = 'merchant_transaction_analytics_v1'
                  AND menu.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission exists_permission
      WHERE exists_permission.app_id = app.id
        AND exists_permission.permission_code = 'merchant:transaction:analytics:view'
        AND exists_permission.deleted = 0
  );

UPDATE sys_permission permission
JOIN sys_app app ON app.id = permission.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = app.id
                  AND menu.menu_code = 'merchant_transaction_analytics_v1'
                  AND menu.deleted = 0
SET permission.menu_id = menu.id,
    permission.permission_name = '交易分析查询',
    permission.permission_type = 'MENU',
    permission.resource_method = 'POST',
    permission.resource_path = '/merchant/transactions/analytics/**',
    permission.description = '查询当前登录商户交易总览和失败分析',
    permission.status = 1,
    permission.updated_at = CURRENT_TIMESTAMP(3)
WHERE permission.permission_code = 'merchant:transaction:analytics:view'
  AND permission.deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT role.app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = role.app_id
                  AND menu.menu_code = 'merchant_transaction_analytics_v1'
                  AND menu.deleted = 0
WHERE role.deleted = 0
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%');

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT role.app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = role.app_id
                              AND permission.permission_code = 'merchant:transaction:analytics:view'
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
                  AND menu.menu_code = 'merchant_transaction_analytics_v1'
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
                              AND permission.permission_code = 'merchant:transaction:analytics:view'
                              AND permission.deleted = 0
WHERE merchant.deleted = 0;

COMMIT;
