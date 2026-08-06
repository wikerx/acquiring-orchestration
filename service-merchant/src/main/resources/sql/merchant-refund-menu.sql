SET NAMES utf8mb4;

-- 商户系统仅增加退款管理菜单和权限。

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, 'merchant_transaction_refund_v1', '退款管理', 'MENU',
       '/transaction/refund', 'transaction/refund', 'merchant:transaction:refund:list',
       'RefreshLeft', 1, 1, 0, 32, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_transaction_catalog_v1'
                    AND parent.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu exists_menu
      WHERE exists_menu.app_id = app.id
        AND exists_menu.menu_code = 'merchant_transaction_refund_v1'
        AND exists_menu.deleted = 0
  );

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, item.menu_code, item.menu_name, 'BUTTON', NULL, NULL,
       item.permission_code, NULL, 0, 0, 0, item.sort_no, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_transaction_refund_v1'
                    AND parent.deleted = 0
JOIN (
    SELECT 'merchant_transaction_refund_detail_v1' menu_code, '退款详情' menu_name,
           'merchant:transaction:refund:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'merchant_transaction_refund_export_v1', '退款导出',
           'merchant:transaction:refund:export', 2
) item
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu exists_menu
      WHERE exists_menu.app_id = app.id
        AND exists_menu.menu_code = item.menu_code
        AND exists_menu.deleted = 0
  );

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT app.id, menu.id, item.permission_code, item.permission_name, item.permission_type,
       item.resource_method, item.resource_path, item.description, 1, 0
FROM sys_app app
JOIN (
    SELECT 'merchant_transaction_refund_v1' menu_code, 'merchant:transaction:refund:list' permission_code,
           '退款查询' permission_name, 'MENU' permission_type, 'POST' resource_method,
           '/merchant/transactions/refunds/search' resource_path, '查询当前商户退款记录' description
    UNION ALL SELECT 'merchant_transaction_refund_detail_v1', 'merchant:transaction:refund:detail',
           '退款详情', 'BUTTON', 'GET', '/merchant/transactions/refunds/*', '查询当前商户退款详情'
    UNION ALL SELECT 'merchant_transaction_refund_export_v1', 'merchant:transaction:refund:export',
           '退款导出', 'BUTTON', 'POST', '/merchant/transactions/refunds/export', '导出当前商户退款记录'
) item
JOIN sys_menu menu ON menu.app_id = app.id
                  AND menu.menu_code = item.menu_code
                  AND menu.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission exists_permission
      WHERE exists_permission.app_id = app.id
        AND exists_permission.permission_code = item.permission_code
        AND exists_permission.deleted = 0
  );

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT role.app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = role.app_id AND menu.deleted = 0
WHERE role.deleted = 0
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
  AND menu.menu_code IN (
      'merchant_transaction_refund_v1',
      'merchant_transaction_refund_detail_v1',
      'merchant_transaction_refund_export_v1'
  );

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT role.app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = role.app_id AND permission.deleted = 0
WHERE role.deleted = 0
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
  AND permission.permission_code LIKE 'merchant:transaction:refund:%';

INSERT IGNORE INTO sys_merchant_menu_grant (
    merchant_id, app_id, menu_id, grant_source, status, created_at, updated_at, deleted
)
SELECT merchant.merchant_id, menu.app_id, menu.id, 'SYSTEM', 1,
       CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM base_merchant_info merchant
JOIN sys_app app ON app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = app.id AND menu.deleted = 0
WHERE merchant.deleted = 0
  AND menu.menu_code IN (
      'merchant_transaction_refund_v1',
      'merchant_transaction_refund_detail_v1',
      'merchant_transaction_refund_export_v1'
  );

INSERT IGNORE INTO sys_merchant_permission_grant (
    merchant_id, app_id, permission_id, grant_source, status, created_at, updated_at, deleted
)
SELECT merchant.merchant_id, permission.app_id, permission.id, 'SYSTEM', 1,
       CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM base_merchant_info merchant
JOIN sys_app app ON app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = app.id AND permission.deleted = 0
WHERE merchant.deleted = 0
  AND permission.permission_code LIKE 'merchant:transaction:refund:%';
