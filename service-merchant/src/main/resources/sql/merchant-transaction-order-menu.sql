SET NAMES utf8mb4;

-- 商户系统交易查询菜单与按钮权限。
-- 注意：本脚本只写 MERCHANT app，不影响管理系统菜单。

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, 0, 'merchant_transaction_catalog_v1', '交易管理', 'CATALOG', '/transaction', NULL,
       'merchant:transaction', 'Tickets', 1, 1, 0, 30, 1, 0
FROM sys_app app
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu exists_menu
      WHERE exists_menu.app_id = app.id
        AND exists_menu.menu_code = 'merchant_transaction_catalog_v1'
        AND exists_menu.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
SET menu.parent_id = 0,
    menu.menu_name = '交易管理',
    menu.menu_type = 'CATALOG',
    menu.route_path = '/transaction',
    menu.component_path = NULL,
    menu.permission_code = 'merchant:transaction',
    menu.icon = 'Tickets',
    menu.visible = 1,
    menu.keep_alive = 1,
    menu.external_link = 0,
    menu.sort_no = 30,
    menu.status = 1
WHERE menu.menu_code = 'merchant_transaction_catalog_v1'
  AND menu.deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, 'merchant_transaction_order_v1', '交易查询', 'MENU', '/transaction/order', 'transaction/order',
       'merchant:transaction:order:list', 'DocumentChecked', 1, 1, 0, 31, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_transaction_catalog_v1'
                    AND parent.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu exists_menu
      WHERE exists_menu.app_id = app.id
        AND exists_menu.menu_code = 'merchant_transaction_order_v1'
        AND exists_menu.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_transaction_catalog_v1'
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = '交易查询',
    menu.menu_type = 'MENU',
    menu.route_path = '/transaction/order',
    menu.component_path = 'transaction/order',
    menu.permission_code = 'merchant:transaction:order:list',
    menu.icon = 'DocumentChecked',
    menu.visible = 1,
    menu.keep_alive = 1,
    menu.external_link = 0,
    menu.sort_no = 31,
    menu.status = 1
WHERE menu.menu_code = 'merchant_transaction_order_v1'
  AND menu.deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, button.menu_code, button.menu_name, 'BUTTON', NULL, NULL,
       button.permission_code, NULL, 0, 0, 0, button.sort_no, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_transaction_order_v1'
                    AND parent.deleted = 0
JOIN (
    SELECT 'merchant_transaction_order_detail_v1' menu_code, '交易详情' menu_name, 'merchant:transaction:order:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'merchant_transaction_order_export_v1', '交易导出', 'merchant:transaction:order:export', 2
    UNION ALL SELECT 'merchant_transaction_order_refund_v1', '交易退款', 'merchant:transaction:order:refund', 3
) button
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu exists_menu
      WHERE exists_menu.app_id = app.id
        AND exists_menu.menu_code = button.menu_code
        AND exists_menu.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_transaction_order_v1'
                    AND parent.deleted = 0
JOIN (
    SELECT 'merchant_transaction_order_detail_v1' menu_code, '交易详情' menu_name, 'merchant:transaction:order:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'merchant_transaction_order_export_v1', '交易导出', 'merchant:transaction:order:export', 2
    UNION ALL SELECT 'merchant_transaction_order_refund_v1', '交易退款', 'merchant:transaction:order:refund', 3
) button ON button.menu_code = menu.menu_code
SET menu.parent_id = parent.id,
    menu.menu_name = button.menu_name,
    menu.menu_type = 'BUTTON',
    menu.route_path = NULL,
    menu.component_path = NULL,
    menu.permission_code = button.permission_code,
    menu.icon = NULL,
    menu.visible = 0,
    menu.keep_alive = 0,
    menu.external_link = 0,
    menu.sort_no = button.sort_no,
    menu.status = 1
WHERE menu.deleted = 0;

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT app.id, menu.id, permission.permission_code, permission.permission_name, permission.permission_type,
       permission.resource_method, permission.resource_path, permission.description, 1, 0
FROM sys_app app
JOIN (
    SELECT 'merchant_transaction_catalog_v1' menu_code, 'merchant:transaction' permission_code, '交易管理目录' permission_name, 'MENU' permission_type, 'GET' resource_method, '/transaction/**' resource_path, '商户交易管理菜单目录' description
    UNION ALL SELECT 'merchant_transaction_order_v1', 'merchant:transaction:order:list', '交易查询', 'MENU', 'POST', '/merchant/transactions/orders/operations/search', '查询当前登录商户交易动作流水'
    UNION ALL SELECT 'merchant_transaction_order_v1', 'merchant:transaction:dict:list', '交易字典查询', 'MENU', 'POST', '/merchant/system/dicts/data/search', '查询交易筛选和状态展示字典'
    UNION ALL SELECT 'merchant_transaction_order_v1', 'merchant:transaction:order:detail', '交易详情', 'BUTTON', 'GET', '/merchant/transactions/orders/*', '查询当前登录商户交易详情'
    UNION ALL SELECT 'merchant_transaction_order_v1', 'merchant:transaction:order:export', '交易导出', 'BUTTON', 'POST', '/merchant/transactions/orders/export', '导出当前登录商户交易动作流水'
    UNION ALL SELECT 'merchant_transaction_order_v1', 'merchant:transaction:order:refund', '交易退款', 'BUTTON', 'POST', '/merchant/transactions/orders/*/refund', '当前登录商户发起交易退款'
) permission
JOIN sys_menu menu ON menu.app_id = app.id AND menu.menu_code = permission.menu_code AND menu.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_permission exists_permission
      WHERE exists_permission.app_id = app.id
        AND exists_permission.permission_code = permission.permission_code
        AND exists_permission.deleted = 0
  );

UPDATE sys_permission permission
JOIN sys_app app ON app.id = permission.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN (
    SELECT 'merchant_transaction_catalog_v1' menu_code, 'merchant:transaction' permission_code, '交易管理目录' permission_name, 'MENU' permission_type, 'GET' resource_method, '/transaction/**' resource_path, '商户交易管理菜单目录' description
    UNION ALL SELECT 'merchant_transaction_order_v1', 'merchant:transaction:order:list', '交易查询', 'MENU', 'POST', '/merchant/transactions/orders/operations/search', '查询当前登录商户交易动作流水'
    UNION ALL SELECT 'merchant_transaction_order_v1', 'merchant:transaction:dict:list', '交易字典查询', 'MENU', 'POST', '/merchant/system/dicts/data/search', '查询交易筛选和状态展示字典'
    UNION ALL SELECT 'merchant_transaction_order_v1', 'merchant:transaction:order:detail', '交易详情', 'BUTTON', 'GET', '/merchant/transactions/orders/*', '查询当前登录商户交易详情'
    UNION ALL SELECT 'merchant_transaction_order_v1', 'merchant:transaction:order:export', '交易导出', 'BUTTON', 'POST', '/merchant/transactions/orders/export', '导出当前登录商户交易动作流水'
    UNION ALL SELECT 'merchant_transaction_order_v1', 'merchant:transaction:order:refund', '交易退款', 'BUTTON', 'POST', '/merchant/transactions/orders/*/refund', '当前登录商户发起交易退款'
) expected ON expected.permission_code = permission.permission_code
JOIN sys_menu menu ON menu.app_id = app.id AND menu.menu_code = expected.menu_code AND menu.deleted = 0
SET permission.menu_id = menu.id,
    permission.permission_name = expected.permission_name,
    permission.permission_type = expected.permission_type,
    permission.resource_method = expected.resource_method,
    permission.resource_path = expected.resource_path,
    permission.description = expected.description,
    permission.status = 1
WHERE permission.deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT role.app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = role.app_id AND menu.deleted = 0
WHERE role.deleted = 0
  AND (
      role.role_code = 'MERCHANT_ADMIN'
      OR role.role_code LIKE 'MERCHANT_ADMIN\_%'
  )
  AND menu.menu_code IN (
      'merchant_transaction_catalog_v1',
      'merchant_transaction_order_v1',
      'merchant_transaction_order_detail_v1',
      'merchant_transaction_order_export_v1',
      'merchant_transaction_order_refund_v1'
  );

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT role.app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = role.app_id AND permission.deleted = 0
WHERE role.deleted = 0
  AND (
      role.role_code = 'MERCHANT_ADMIN'
      OR role.role_code LIKE 'MERCHANT_ADMIN\_%'
  )
  AND permission.permission_code IN (
      'merchant:transaction',
      'merchant:transaction:order:list',
      'merchant:transaction:dict:list',
      'merchant:transaction:order:detail',
      'merchant:transaction:order:export',
      'merchant:transaction:order:refund'
  );

INSERT IGNORE INTO sys_merchant_menu_grant (
    merchant_id, app_id, menu_id, grant_source, status, created_at, updated_at, deleted
)
SELECT merchant.merchant_id, menu.app_id, menu.id, 'SYSTEM', 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM base_merchant_info merchant
JOIN sys_app app ON app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = app.id AND menu.deleted = 0
WHERE merchant.deleted = 0
  AND menu.menu_code IN (
      'merchant_transaction_catalog_v1',
      'merchant_transaction_order_v1',
      'merchant_transaction_order_detail_v1',
      'merchant_transaction_order_export_v1',
      'merchant_transaction_order_refund_v1'
  );

INSERT IGNORE INTO sys_merchant_permission_grant (
    merchant_id, app_id, permission_id, grant_source, status, created_at, updated_at, deleted
)
SELECT merchant.merchant_id, permission.app_id, permission.id, 'SYSTEM', 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM base_merchant_info merchant
JOIN sys_app app ON app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = app.id AND permission.deleted = 0
WHERE merchant.deleted = 0
  AND permission.permission_code IN (
      'merchant:transaction',
      'merchant:transaction:order:list',
      'merchant:transaction:dict:list',
      'merchant:transaction:order:detail',
      'merchant:transaction:order:export',
      'merchant:transaction:order:refund'
  );
