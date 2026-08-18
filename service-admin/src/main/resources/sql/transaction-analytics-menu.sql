SET NAMES utf8mb4;

-- 管理系统交易分析菜单与只读统计权限增量迁移。
START TRANSACTION;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, 'admin_transaction_analytics_v1', '交易分析', 'MENU',
       '/transaction/analytics', 'transaction/analytics', 'transaction:analytics:view',
       'DataAnalysis', 1, 1, 0, 55, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'admin_transaction_catalog_v1'
                    AND parent.deleted = 0
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu exists_menu
      WHERE exists_menu.app_id = app.id
        AND exists_menu.menu_code = 'admin_transaction_analytics_v1'
        AND exists_menu.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = menu.app_id
                    AND parent.menu_code = 'admin_transaction_catalog_v1'
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = '交易分析',
    menu.menu_type = 'MENU',
    menu.route_path = '/transaction/analytics',
    menu.component_path = 'transaction/analytics',
    menu.permission_code = 'transaction:analytics:view',
    menu.icon = 'DataAnalysis',
    menu.visible = 1,
    menu.keep_alive = 1,
    menu.external_link = 0,
    menu.sort_no = 55,
    menu.status = 1,
    menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.menu_code = 'admin_transaction_analytics_v1'
  AND menu.deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, item.menu_code, item.menu_name, 'BUTTON', NULL, NULL,
       item.permission_code, NULL, 0, 0, 0, item.sort_no, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'admin_transaction_analytics_v1'
                    AND parent.deleted = 0
JOIN (
    SELECT 'admin_transaction_analytics_overview_v1' menu_code, '交易总览' menu_name,
           'transaction:analytics:overview' permission_code, 1 sort_no
    UNION ALL SELECT 'admin_transaction_analytics_merchants_v1', '商户表现',
           'transaction:analytics:merchants', 2
    UNION ALL SELECT 'admin_transaction_analytics_failures_v1', '失败分析',
           'transaction:analytics:failures', 3
    UNION ALL SELECT 'admin_transaction_analytics_channels_v1', '渠道表现',
           'transaction:analytics:channels', 4
    UNION ALL SELECT 'admin_transaction_analytics_three_ds_v1', '3DS分析',
           'transaction:analytics:three-ds', 5
) item
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu exists_menu
      WHERE exists_menu.app_id = app.id
        AND exists_menu.menu_code = item.menu_code
        AND exists_menu.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN (
    SELECT 'admin_transaction_analytics_overview_v1' menu_code, '交易总览' menu_name,
           'transaction:analytics:overview' permission_code, 1 sort_no
    UNION ALL SELECT 'admin_transaction_analytics_merchants_v1', '商户表现',
           'transaction:analytics:merchants', 2
    UNION ALL SELECT 'admin_transaction_analytics_failures_v1', '失败分析',
           'transaction:analytics:failures', 3
    UNION ALL SELECT 'admin_transaction_analytics_channels_v1', '渠道表现',
           'transaction:analytics:channels', 4
    UNION ALL SELECT 'admin_transaction_analytics_three_ds_v1', '3DS分析',
           'transaction:analytics:three-ds', 5
) item ON item.menu_code = menu.menu_code
JOIN sys_menu parent ON parent.app_id = menu.app_id
                    AND parent.menu_code = 'admin_transaction_analytics_v1'
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = item.menu_name,
    menu.menu_type = 'BUTTON',
    menu.route_path = NULL,
    menu.component_path = NULL,
    menu.permission_code = item.permission_code,
    menu.icon = NULL,
    menu.visible = 0,
    menu.keep_alive = 0,
    menu.external_link = 0,
    menu.sort_no = item.sort_no,
    menu.status = 1,
    menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.deleted = 0;

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT app.id, menu.id, item.permission_code, item.permission_name, item.permission_type,
       item.resource_method, item.resource_path, item.description, 1, 0
FROM sys_app app
JOIN (
    SELECT 'admin_transaction_analytics_v1' menu_code, 'transaction:analytics:view' permission_code,
           '交易分析访问' permission_name, 'MENU' permission_type, NULL resource_method,
           NULL resource_path, '访问交易分析页面' description
    UNION ALL SELECT 'admin_transaction_analytics_overview_v1', 'transaction:analytics:overview',
           '交易总览', 'BUTTON', 'POST', '/admin/transactions/analytics/overview', '查询交易总览统计'
    UNION ALL SELECT 'admin_transaction_analytics_merchants_v1', 'transaction:analytics:merchants',
           '商户表现', 'BUTTON', 'POST', '/admin/transactions/analytics/merchants', '查询商户交易表现统计'
    UNION ALL SELECT 'admin_transaction_analytics_failures_v1', 'transaction:analytics:failures',
           '失败分析', 'BUTTON', 'POST', '/admin/transactions/analytics/failures', '查询后台失败原因统计'
    UNION ALL SELECT 'admin_transaction_analytics_channels_v1', 'transaction:analytics:channels',
           '渠道表现', 'BUTTON', 'POST', '/admin/transactions/analytics/channels', '查询渠道请求和最终交易表现'
    UNION ALL SELECT 'admin_transaction_analytics_three_ds_v1', 'transaction:analytics:three-ds',
           '3DS分析', 'BUTTON', 'POST', '/admin/transactions/analytics/three-ds', '查询3DS认证统计'
) item
JOIN sys_menu menu ON menu.app_id = app.id
                  AND menu.menu_code = item.menu_code
                  AND menu.deleted = 0
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission exists_permission
      WHERE exists_permission.app_id = app.id
        AND exists_permission.permission_code = item.permission_code
        AND exists_permission.deleted = 0
  );

UPDATE sys_permission permission
JOIN sys_app app ON app.id = permission.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN (
    SELECT 'admin_transaction_analytics_v1' menu_code, 'transaction:analytics:view' permission_code,
           '交易分析访问' permission_name, 'MENU' permission_type, NULL resource_method,
           NULL resource_path, '访问交易分析页面' description
    UNION ALL SELECT 'admin_transaction_analytics_overview_v1', 'transaction:analytics:overview',
           '交易总览', 'BUTTON', 'POST', '/admin/transactions/analytics/overview', '查询交易总览统计'
    UNION ALL SELECT 'admin_transaction_analytics_merchants_v1', 'transaction:analytics:merchants',
           '商户表现', 'BUTTON', 'POST', '/admin/transactions/analytics/merchants', '查询商户交易表现统计'
    UNION ALL SELECT 'admin_transaction_analytics_failures_v1', 'transaction:analytics:failures',
           '失败分析', 'BUTTON', 'POST', '/admin/transactions/analytics/failures', '查询后台失败原因统计'
    UNION ALL SELECT 'admin_transaction_analytics_channels_v1', 'transaction:analytics:channels',
           '渠道表现', 'BUTTON', 'POST', '/admin/transactions/analytics/channels', '查询渠道请求和最终交易表现'
    UNION ALL SELECT 'admin_transaction_analytics_three_ds_v1', 'transaction:analytics:three-ds',
           '3DS分析', 'BUTTON', 'POST', '/admin/transactions/analytics/three-ds', '查询3DS认证统计'
) item ON item.permission_code = permission.permission_code
JOIN sys_menu menu ON menu.app_id = permission.app_id
                  AND menu.menu_code = item.menu_code
                  AND menu.deleted = 0
SET permission.menu_id = menu.id,
    permission.permission_name = item.permission_name,
    permission.permission_type = item.permission_type,
    permission.resource_method = item.resource_method,
    permission.resource_path = item.resource_path,
    permission.description = item.description,
    permission.status = 1,
    permission.updated_at = CURRENT_TIMESTAMP(3)
WHERE permission.deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT role.app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = role.app_id
                  AND (menu.menu_code = 'admin_transaction_analytics_v1'
                       OR menu.parent_id = (
                           SELECT parent.id FROM sys_menu parent
                           WHERE parent.app_id = role.app_id
                             AND parent.menu_code = 'admin_transaction_analytics_v1'
                             AND parent.deleted = 0
                           LIMIT 1
                       ))
                  AND menu.deleted = 0
WHERE role.deleted = 0
  AND role.role_code = 'SUPER_ADMIN';

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT role.app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = role.app_id
                              AND (permission.permission_code = 'transaction:analytics:view'
                                   OR permission.permission_code LIKE 'transaction:analytics:%')
                              AND permission.deleted = 0
WHERE role.deleted = 0
  AND role.role_code = 'SUPER_ADMIN';

-- 兼容升级：原先拥有交易分析页面权限的角色继承全部五个功能权限和按钮菜单。
INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT parent_grant.app_id, parent_grant.role_id, child.id, 0
FROM sys_role_menu parent_grant
JOIN sys_menu parent ON parent.app_id = parent_grant.app_id
                    AND parent.id = parent_grant.menu_id
                    AND parent.menu_code = 'admin_transaction_analytics_v1'
                    AND parent.deleted = 0
JOIN sys_menu child ON child.app_id = parent.app_id
                   AND child.parent_id = parent.id
                   AND child.menu_type = 'BUTTON'
                   AND child.deleted = 0
WHERE parent_grant.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT parent_grant.app_id, parent_grant.role_id, child.id, 0
FROM sys_role_permission parent_grant
JOIN sys_permission parent ON parent.app_id = parent_grant.app_id
                          AND parent.id = parent_grant.permission_id
                          AND parent.permission_code = 'transaction:analytics:view'
                          AND parent.deleted = 0
JOIN sys_permission child ON child.app_id = parent.app_id
                         AND child.permission_code IN (
                             'transaction:analytics:overview',
                             'transaction:analytics:merchants',
                             'transaction:analytics:failures',
                             'transaction:analytics:channels',
                             'transaction:analytics:three-ds'
                         )
                         AND child.deleted = 0
WHERE parent_grant.deleted = 0;

COMMIT;
