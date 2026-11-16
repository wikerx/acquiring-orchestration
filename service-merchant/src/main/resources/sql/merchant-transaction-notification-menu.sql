SET NAMES utf8mb4;

-- 商户系统交易通知记录菜单、查询详情与人工重发权限。脚本可重复执行，不改变 OpenAPI callbackUrl 契约。
START TRANSACTION;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, 'merchant_transaction_notification_v1', '交易通知记录', 'MENU',
       '/transaction/notification', 'transaction/notification',
       'merchant:transaction:notification:list', 'Bell', 1, 1, 0, 33, 1, 0
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
        AND exists_menu.menu_code = 'merchant_transaction_notification_v1'
        AND exists_menu.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_transaction_catalog_v1'
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = '交易通知记录',
    menu.menu_type = 'MENU',
    menu.route_path = '/transaction/notification',
    menu.component_path = 'transaction/notification',
    menu.permission_code = 'merchant:transaction:notification:list',
    menu.icon = 'Bell',
    menu.visible = 1,
    menu.keep_alive = 1,
    menu.external_link = 0,
    menu.sort_no = 33,
    menu.status = 1
WHERE menu.menu_code = 'merchant_transaction_notification_v1'
  AND menu.deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, 'merchant_transaction_notification_detail_v1', '交易通知详情',
       'BUTTON', NULL, NULL, 'merchant:transaction:notification:detail',
       NULL, 0, 0, 0, 1, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_transaction_notification_v1'
                    AND parent.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu exists_menu
      WHERE exists_menu.app_id = app.id
        AND exists_menu.menu_code = 'merchant_transaction_notification_detail_v1'
        AND exists_menu.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_transaction_notification_v1'
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = '交易通知详情',
    menu.menu_type = 'BUTTON',
    menu.route_path = NULL,
    menu.component_path = NULL,
    menu.permission_code = 'merchant:transaction:notification:detail',
    menu.icon = NULL,
    menu.visible = 0,
    menu.keep_alive = 0,
    menu.external_link = 0,
    menu.sort_no = 1,
    menu.status = 1
WHERE menu.menu_code = 'merchant_transaction_notification_detail_v1'
  AND menu.deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, 'merchant_transaction_notification_retry_v1', '重发交易通知',
       'BUTTON', NULL, NULL, 'merchant:transaction:notification:retry',
       NULL, 0, 0, 0, 2, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_transaction_notification_v1'
                    AND parent.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu exists_menu
      WHERE exists_menu.app_id = app.id
        AND exists_menu.menu_code = 'merchant_transaction_notification_retry_v1'
        AND exists_menu.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_transaction_notification_v1'
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = '重发交易通知',
    menu.menu_type = 'BUTTON',
    menu.route_path = NULL,
    menu.component_path = NULL,
    menu.permission_code = 'merchant:transaction:notification:retry',
    menu.icon = NULL,
    menu.visible = 0,
    menu.keep_alive = 0,
    menu.external_link = 0,
    menu.sort_no = 2,
    menu.status = 1
WHERE menu.menu_code = 'merchant_transaction_notification_retry_v1'
  AND menu.deleted = 0;

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT app.id, menu.id, item.permission_code, item.permission_name, item.permission_type,
       item.resource_method, item.resource_path, item.description, 1, 0
FROM sys_app app
JOIN (
    SELECT 'merchant_transaction_notification_v1' menu_code,
           'merchant:transaction:notification:list' permission_code,
           '交易通知记录查询' permission_name, 'MENU' permission_type,
           'POST' resource_method, '/merchant/transactions/notifications/search' resource_path,
           '查询当前认证商户的交易通知任务' description
    UNION ALL
    SELECT 'merchant_transaction_notification_detail_v1',
           'merchant:transaction:notification:detail',
           '交易通知详情', 'BUTTON', 'GET', '/merchant/transactions/notifications/*',
           '查询当前认证商户的脱敏通知快照和投递日志'
    UNION ALL
    SELECT 'merchant_transaction_notification_retry_v1',
           'merchant:transaction:notification:retry',
           '重发交易通知', 'BUTTON', 'POST', '/merchant/transactions/notifications/retry',
           '将当前认证商户指定通知任务的人工重发命令可靠写入 MQ'
) item
JOIN sys_menu menu ON menu.app_id = app.id
                  AND menu.menu_code = item.menu_code
                  AND menu.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_permission exists_permission
      WHERE exists_permission.app_id = app.id
        AND exists_permission.permission_code = item.permission_code
        AND exists_permission.deleted = 0
  );

UPDATE sys_permission permission
JOIN sys_app app ON app.id = permission.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN (
    SELECT 'merchant_transaction_notification_v1' menu_code,
           'merchant:transaction:notification:list' permission_code,
           '交易通知记录查询' permission_name, 'MENU' permission_type,
           'POST' resource_method, '/merchant/transactions/notifications/search' resource_path,
           '查询当前认证商户的交易通知任务' description
    UNION ALL
    SELECT 'merchant_transaction_notification_detail_v1',
           'merchant:transaction:notification:detail',
           '交易通知详情', 'BUTTON', 'GET', '/merchant/transactions/notifications/*',
           '查询当前认证商户的脱敏通知快照和投递日志'
    UNION ALL
    SELECT 'merchant_transaction_notification_retry_v1',
           'merchant:transaction:notification:retry',
           '重发交易通知', 'BUTTON', 'POST', '/merchant/transactions/notifications/retry',
           '将当前认证商户指定通知任务的人工重发命令可靠写入 MQ'
) expected ON expected.permission_code = permission.permission_code
JOIN sys_menu menu ON menu.app_id = app.id
                  AND menu.menu_code = expected.menu_code
                  AND menu.deleted = 0
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
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
  AND menu.menu_code IN (
      'merchant_transaction_notification_v1',
      'merchant_transaction_notification_detail_v1',
      'merchant_transaction_notification_retry_v1'
  );

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT role.app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = role.app_id AND permission.deleted = 0
WHERE role.deleted = 0
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
  AND permission.permission_code IN (
      'merchant:transaction:notification:list',
      'merchant:transaction:notification:detail',
      'merchant:transaction:notification:retry'
  );

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
      'merchant_transaction_notification_v1',
      'merchant_transaction_notification_detail_v1',
      'merchant_transaction_notification_retry_v1'
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
  AND permission.permission_code IN (
      'merchant:transaction:notification:list',
      'merchant:transaction:notification:detail',
      'merchant:transaction:notification:retry'
  );

COMMIT;
