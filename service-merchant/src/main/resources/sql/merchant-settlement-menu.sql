-- Merchant settlement bill and reserve detail menus, permissions and default merchant-admin grants.
-- This migration contains read/export permissions only and is safe to re-run.

START TRANSACTION;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, item.menu_code, item.menu_name, 'MENU', item.route_path,
       item.component_path, item.permission_code, item.icon, 1, 1, 0, item.sort_no, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_finance_catalog_v1'
                    AND parent.deleted = 0
JOIN (
    SELECT 'merchant_settlement_batch_v1' menu_code, '结算账单' menu_name,
           '/finance/settlements' route_path, 'finance/settlement' component_path,
           'merchant:settlement:batch:list' permission_code, 'Landmark' icon, 3 sort_no
    UNION ALL SELECT 'merchant_settlement_reserve_v1', '保证金明细',
           '/finance/reserves', 'finance/reserve',
           'merchant:settlement:reserve-item:list', 'ShieldCheck', 4
) item
WHERE app.app_code = 'MERCHANT' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = app.id AND existing.menu_code = item.menu_code AND existing.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = menu.app_id
                    AND parent.menu_code = 'merchant_finance_catalog_v1'
                    AND parent.deleted = 0
JOIN (
    SELECT 'merchant_settlement_batch_v1' menu_code, '结算账单' menu_name,
           '/finance/settlements' route_path, 'finance/settlement' component_path,
           'merchant:settlement:batch:list' permission_code, 'Landmark' icon, 3 sort_no
    UNION ALL SELECT 'merchant_settlement_reserve_v1', '保证金明细',
           '/finance/reserves', 'finance/reserve',
           'merchant:settlement:reserve-item:list', 'ShieldCheck', 4
) item ON item.menu_code = menu.menu_code
SET menu.parent_id = parent.id, menu.menu_name = BINARY item.menu_name, menu.menu_type = 'MENU',
    menu.route_path = item.route_path, menu.component_path = item.component_path,
    menu.permission_code = item.permission_code, menu.icon = item.icon,
    menu.visible = 1, menu.keep_alive = 1, menu.sort_no = item.sort_no,
    menu.status = 1, menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, permission_code,
    visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, item.menu_code, item.menu_name, 'BUTTON', item.permission_code,
       0, 0, 0, item.sort_no, 1, 0
FROM sys_app app
JOIN (
    SELECT 'merchant_settlement_batch_detail_v1' menu_code, '结算账单详情' menu_name,
           'merchant:settlement:batch:detail' permission_code, 'merchant_settlement_batch_v1' parent_code, 101 sort_no
    UNION ALL SELECT 'merchant_settlement_batch_export_v1', '结算账单导出',
           'merchant:settlement:batch:export', 'merchant_settlement_batch_v1', 102
    UNION ALL SELECT 'merchant_settlement_transaction_item_v1', '交易结算明细',
           'merchant:settlement:transaction-item:list', 'merchant_settlement_batch_v1', 103
    UNION ALL SELECT 'merchant_settlement_transaction_export_v1', '交易结算明细导出',
           'merchant:settlement:transaction-item:export', 'merchant_settlement_batch_v1', 104
    UNION ALL SELECT 'merchant_settlement_reserve_export_v1', '保证金明细导出',
           'merchant:settlement:reserve-item:export', 'merchant_settlement_reserve_v1', 101
) item
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = item.parent_code
                    AND parent.deleted = 0
WHERE app.app_code = 'MERCHANT' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = app.id AND existing.menu_code = item.menu_code AND existing.deleted = 0
  );

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT app.id, menu.id, item.permission_code, item.permission_name, item.permission_type,
       item.resource_method, item.resource_path, item.description, 1, 0
FROM sys_app app
JOIN (
    SELECT 'merchant_settlement_batch_v1' menu_code, 'merchant:settlement:batch:list' permission_code,
           '结算账单查询' permission_name, 'MENU' permission_type, 'POST' resource_method,
           '/merchant/settlements/search' resource_path, '查询当前认证商户正式结算账单' description
    UNION ALL SELECT 'merchant_settlement_batch_detail_v1', 'merchant:settlement:batch:detail',
           '结算账单详情', 'BUTTON', 'GET', '/merchant/settlements/*', '查询当前认证商户结算账单详情'
    UNION ALL SELECT 'merchant_settlement_batch_export_v1', 'merchant:settlement:batch:export',
           '结算账单导出', 'BUTTON', 'POST', '/merchant/settlements/export', '导出当前认证商户结算账单'
    UNION ALL SELECT 'merchant_settlement_transaction_item_v1', 'merchant:settlement:transaction-item:list',
           '交易结算明细查询', 'BUTTON', 'POST', '/merchant/settlements/transaction-items/search',
           '查询当前认证商户真实交易逐笔结算明细'
    UNION ALL SELECT 'merchant_settlement_transaction_export_v1', 'merchant:settlement:transaction-item:export',
           '交易结算明细导出', 'BUTTON', 'POST', '/merchant/settlements/transaction-items/export',
           '导出当前认证商户真实交易逐笔结算明细'
    UNION ALL SELECT 'merchant_settlement_reserve_v1', 'merchant:settlement:reserve-item:list',
           '保证金明细查询', 'MENU', 'POST', '/merchant/settlements/reserve-items/search',
           '查询当前认证商户保证金不可变动作和当前责任'
    UNION ALL SELECT 'merchant_settlement_reserve_export_v1', 'merchant:settlement:reserve-item:export',
           '保证金明细导出', 'BUTTON', 'POST', '/merchant/settlements/reserve-items/export',
           '导出当前认证商户保证金不可变动作'
) item
JOIN sys_menu menu ON menu.app_id = app.id AND menu.menu_code = item.menu_code AND menu.deleted = 0
WHERE app.app_code = 'MERCHANT' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing
      WHERE existing.app_id = app.id AND existing.permission_code = item.permission_code AND existing.deleted = 0
  );

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT role.app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = role.app_id
                  AND menu.menu_code LIKE 'merchant_settlement_%'
                  AND menu.deleted = 0
WHERE role.role_type = 'SYSTEM'
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
  AND role.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT role.app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = role.app_id
                              AND permission.permission_code LIKE 'merchant:settlement:%'
                              AND permission.deleted = 0
WHERE role.role_type = 'SYSTEM'
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
  AND role.deleted = 0;

INSERT IGNORE INTO sys_merchant_menu_grant (
    merchant_id, app_id, menu_id, grant_source, status, created_at, updated_at, deleted
)
SELECT merchant.merchant_id, menu.app_id, menu.id, 'SYSTEM', 1,
       CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM base_merchant_info merchant
JOIN sys_app app ON app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = app.id
                  AND (menu.menu_code = 'merchant_finance_catalog_v1'
                       OR menu.menu_code LIKE 'merchant_settlement_%')
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
                              AND permission.permission_code LIKE 'merchant:settlement:%'
                              AND permission.deleted = 0
WHERE merchant.deleted = 0;

COMMIT;
