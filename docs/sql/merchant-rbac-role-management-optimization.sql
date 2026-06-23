-- 商户系统 RBAC 角色管理优化。
-- 用途：补齐商户端基础页面按钮权限，隐藏旧“角色授权”独立菜单，并保持已有授权范围可见。
-- 本脚本幂等执行，不删除历史菜单和权限。

-- 1. 隐藏旧角色授权菜单，保留路由和数据兼容。
UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id
SET menu.visible = 0,
    menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND menu.menu_code = 'merchant_system_role_auth_v1'
  AND menu.deleted = 0;

-- 2. 将角色授权能力收敛到角色管理菜单下，授权按钮使用后端接口真实校验的 grantMenu 权限码。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, role_menu.id, seed.menu_code, seed.menu_name, 'BUTTON', NULL, NULL,
       seed.permission_code, NULL, 0, 0, 0, seed.sort_no, 1, 0
FROM sys_app app
JOIN sys_menu role_menu ON role_menu.app_id = app.id
JOIN (
    SELECT 'merchant_system_role_grant_v1' AS menu_code, '角色授权' AS menu_name,
           'merchant:system:role:grantMenu' AS permission_code, 945 AS sort_no
    UNION ALL SELECT 'merchant_system_dept_status_v1', '启停部门',
           'merchant:system:dept:status', 914
    UNION ALL SELECT 'merchant_system_post_status_v1', '启停岗位',
           'merchant:system:post:status', 924
    UNION ALL SELECT 'merchant_system_account_detail_v1', '员工详情',
           'merchant:system:account:detail', 936
    UNION ALL SELECT 'merchant_system_account_reset_password_v1', '重置密码',
           'merchant:system:account:resetPassword', 937
) seed
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND role_menu.deleted = 0
  AND role_menu.menu_code = CASE
      WHEN seed.menu_code LIKE 'merchant_system_role_%' THEN 'merchant_system_role_v1'
      WHEN seed.menu_code LIKE 'merchant_system_dept_%' THEN 'merchant_system_dept_v1'
      WHEN seed.menu_code LIKE 'merchant_system_post_%' THEN 'merchant_system_post_v1'
      ELSE 'merchant_system_account_v1'
  END
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type),
    permission_code = VALUES(permission_code),
    visible = VALUES(visible),
    sort_no = VALUES(sort_no),
    status = VALUES(status);

-- 3. 补齐业务页面常用按钮权限。仅补菜单权限节点，不强造不存在的业务接口。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, seed.menu_code, seed.menu_name, 'BUTTON', NULL, NULL,
       seed.permission_code, NULL, 0, 0, 0, seed.sort_no, 1, 0
FROM sys_app app
JOIN (
    SELECT 'merchant_info_edit_v1' AS menu_code, '编辑商户信息' AS menu_name, 'merchant_info' AS parent_code, 'merchant:info:edit' AS permission_code, 201 AS sort_no
    UNION ALL SELECT 'merchant_store_list_v1', '查询店铺', 'merchant_store', 'merchant:store:list', 501
    UNION ALL SELECT 'merchant_store_add_v1', '新增店铺', 'merchant_store', 'merchant:store:add', 502
    UNION ALL SELECT 'merchant_store_edit_v1', '编辑店铺', 'merchant_store', 'merchant:store:edit', 503
    UNION ALL SELECT 'merchant_store_delete_v1', '删除店铺', 'merchant_store', 'merchant:store:delete', 504
    UNION ALL SELECT 'merchant_store_status_v1', '启停店铺', 'merchant_store', 'merchant:store:status', 505
    UNION ALL SELECT 'merchant_store_detail_v1', '店铺详情', 'merchant_store', 'merchant:store:detail', 506
    UNION ALL SELECT 'merchant_transaction_list_v1', '查询交易', 'merchant_transaction', 'merchant:transaction:list', 1001
    UNION ALL SELECT 'merchant_transaction_detail_v1', '交易详情', 'merchant_transaction', 'merchant:transaction:detail', 1002
    UNION ALL SELECT 'merchant_transaction_export_v1', '导出交易', 'merchant_transaction', 'merchant:transaction:export', 1003
    UNION ALL SELECT 'merchant_order_list_v1', '查询订单', 'merchant_order', 'merchant:order:list', 1101
    UNION ALL SELECT 'merchant_order_detail_v1', '订单详情', 'merchant_order', 'merchant:order:detail', 1102
    UNION ALL SELECT 'merchant_order_export_v1', '导出订单', 'merchant_order', 'merchant:order:export', 1103
    UNION ALL SELECT 'merchant_refund_list_v1', '查询退款', 'merchant_refund', 'merchant:refund:list', 1201
    UNION ALL SELECT 'merchant_refund_detail_v1', '退款详情', 'merchant_refund', 'merchant:refund:detail', 1202
    UNION ALL SELECT 'merchant_refund_export_v1', '导出退款', 'merchant_refund', 'merchant:refund:export', 1203
    UNION ALL SELECT 'merchant_settlement_list_v1', '查询结算', 'merchant_settlement', 'merchant:settlement:list', 2001
    UNION ALL SELECT 'merchant_settlement_detail_v1', '结算详情', 'merchant_settlement', 'merchant:settlement:detail', 2002
    UNION ALL SELECT 'merchant_settlement_export_v1', '导出结算', 'merchant_settlement', 'merchant:settlement:export', 2003
    UNION ALL SELECT 'merchant_account_list_v1', '查询账号', 'merchant_account', 'merchant:account:list', 3001
    UNION ALL SELECT 'merchant_account_edit_v1', '编辑账号', 'merchant_account', 'merchant:account:edit', 3002
    UNION ALL SELECT 'merchant_account_delete_v1', '删除账号', 'merchant_account', 'merchant:account:delete', 3003
    UNION ALL SELECT 'merchant_account_status_v1', '启停账号', 'merchant_account', 'merchant:account:status', 3004
    UNION ALL SELECT 'merchant_account_detail_v1', '账号详情', 'merchant_account', 'merchant:account:detail', 3005
    UNION ALL SELECT 'merchant_api_key_list_v1', '查询API密钥', 'merchant_api_key', 'merchant:api-key:list', 3101
    UNION ALL SELECT 'merchant_api_key_create_v1', '创建API密钥', 'merchant_api_key', 'merchant:api-key:create', 3102
    UNION ALL SELECT 'merchant_api_key_edit_v1', '编辑API密钥', 'merchant_api_key', 'merchant:api-key:edit', 3103
    UNION ALL SELECT 'merchant_api_key_delete_v1', '删除API密钥', 'merchant_api_key', 'merchant:api-key:delete', 3104
    UNION ALL SELECT 'merchant_api_key_enable_v1', '启用API密钥', 'merchant_api_key', 'merchant:api-key:enable', 3105
    UNION ALL SELECT 'merchant_api_key_disable_v1', '停用API密钥', 'merchant_api_key', 'merchant:api-key:disable', 3106
    UNION ALL SELECT 'merchant_api_key_detail_v1', 'API密钥详情', 'merchant_api_key', 'merchant:api-key:detail', 3107
    UNION ALL SELECT 'merchant_oper_log_list_v1', '查询操作日志', 'merchant_oper_log', 'merchant:oper-log:list', 3201
    UNION ALL SELECT 'merchant_oper_log_detail_v1', '操作日志详情', 'merchant_oper_log', 'merchant:oper-log:detail', 3202
    UNION ALL SELECT 'merchant_oper_log_export_v1', '导出操作日志', 'merchant_oper_log', 'merchant:oper-log:export', 3203
) seed
JOIN sys_menu parent
  ON parent.app_id = app.id
 AND parent.deleted = 0
 AND parent.menu_code IN (
     seed.parent_code,
     CONCAT(seed.parent_code, '_v1'),
     CASE seed.parent_code
         WHEN 'merchant_info' THEN 'merchant_info_v1'
         WHEN 'merchant_store' THEN 'merchant_store_v1'
         WHEN 'merchant_transaction' THEN 'merchant_transaction_v1'
         WHEN 'merchant_order' THEN 'merchant_order_v1'
         WHEN 'merchant_refund' THEN 'merchant_refund_v1'
         WHEN 'merchant_settlement' THEN 'merchant_settlement_v1'
         WHEN 'merchant_account' THEN 'merchant_account_v1'
         WHEN 'merchant_api_key' THEN 'merchant_api_key_v1'
         WHEN 'merchant_oper_log' THEN 'merchant_oper_log_v1'
     END
 )
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type),
    permission_code = VALUES(permission_code),
    visible = VALUES(visible),
    sort_no = VALUES(sort_no),
    status = VALUES(status);

-- 4. 为所有有权限码的 MERCHANT 菜单补 sys_permission，保证接口权限与按钮权限闭环。
INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, status, deleted
)
SELECT menu.app_id,
       menu.id,
       menu.permission_code,
       menu.menu_name,
       CASE WHEN menu.menu_type = 'BUTTON' THEN 'BUTTON' ELSE 'MENU' END,
       '*',
       NULL,
       1,
       0
FROM sys_menu menu
JOIN sys_app app ON app.id = menu.app_id
LEFT JOIN sys_permission permission
  ON permission.app_id = menu.app_id
 AND permission.permission_code = menu.permission_code
 AND permission.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND menu.deleted = 0
  AND menu.permission_code IS NOT NULL
  AND menu.permission_code <> ''
  AND permission.id IS NULL
ON DUPLICATE KEY UPDATE
    menu_id = VALUES(menu_id),
    permission_name = VALUES(permission_name),
    permission_type = VALUES(permission_type),
    status = VALUES(status);

UPDATE sys_permission permission
JOIN sys_app app ON app.id = permission.app_id
JOIN sys_menu menu
  ON menu.app_id = app.id
 AND menu.menu_code = 'merchant_system_role_grant_v1'
 AND menu.deleted = 0
SET permission.menu_id = menu.id,
    permission.permission_name = '角色授权',
    permission.permission_type = 'BUTTON',
    permission.resource_method = '*',
    permission.resource_path = '/merchant/system/roles/*/grant-tree',
    permission.status = 1,
    permission.updated_at = CURRENT_TIMESTAMP(3)
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND permission.permission_code = 'merchant:system:role:grantMenu'
  AND permission.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT new_permission.app_id, relation.role_id, new_permission.id, 0
FROM sys_role_permission relation
JOIN sys_permission old_permission
  ON old_permission.app_id = relation.app_id
 AND old_permission.id = relation.permission_id
 AND old_permission.permission_code = 'merchant:system:role:grant'
 AND old_permission.deleted = 0
JOIN sys_permission new_permission
  ON new_permission.app_id = relation.app_id
 AND new_permission.permission_code = 'merchant:system:role:grantMenu'
 AND new_permission.deleted = 0
WHERE relation.deleted = 0;

INSERT IGNORE INTO sys_merchant_permission_grant (
    app_id, merchant_id, permission_id, grant_source, status, created_at, deleted
)
SELECT new_permission.app_id,
       merchant_grant.merchant_id,
       new_permission.id,
       merchant_grant.grant_source,
       merchant_grant.status,
       CURRENT_TIMESTAMP(3),
       0
FROM sys_merchant_permission_grant merchant_grant
JOIN sys_permission old_permission
  ON old_permission.app_id = merchant_grant.app_id
 AND old_permission.id = merchant_grant.permission_id
 AND old_permission.permission_code = 'merchant:system:role:grant'
 AND old_permission.deleted = 0
JOIN sys_permission new_permission
  ON new_permission.app_id = merchant_grant.app_id
 AND new_permission.permission_code = 'merchant:system:role:grantMenu'
 AND new_permission.deleted = 0
WHERE merchant_grant.deleted = 0
  AND merchant_grant.status = 1;

UPDATE sys_permission permission
JOIN sys_app app ON app.id = permission.app_id
SET permission.status = 0,
    permission.deleted = permission.id,
    permission.updated_at = CURRENT_TIMESTAMP(3)
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND permission.permission_code = 'merchant:system:role:grant'
  AND permission.deleted = 0;

-- 5. 同步到平台已授权商户范围。已有商户授权过任一 MERCHANT 菜单/权限，则补齐新增项。
INSERT IGNORE INTO sys_merchant_menu_grant (
    app_id, merchant_id, menu_id, grant_source, status, created_at, deleted
)
SELECT app.id,
       merchant_scope.merchant_id,
       menu.id,
       'ADMIN',
       1,
       CURRENT_TIMESTAMP(3),
       0
FROM sys_app app
JOIN sys_menu menu ON menu.app_id = app.id
JOIN (
    SELECT DISTINCT merchant_id
    FROM sys_merchant_menu_grant
    WHERE deleted = 0
      AND status = 1
      AND merchant_id IS NOT NULL
) merchant_scope
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND menu.deleted = 0
  AND menu.status = 1;

INSERT IGNORE INTO sys_merchant_permission_grant (
    app_id, merchant_id, permission_id, grant_source, status, created_at, deleted
)
SELECT app.id,
       merchant_scope.merchant_id,
       permission.id,
       'ADMIN',
       1,
       CURRENT_TIMESTAMP(3),
       0
FROM sys_app app
JOIN sys_permission permission ON permission.app_id = app.id
JOIN (
    SELECT DISTINCT merchant_id
    FROM sys_merchant_permission_grant
    WHERE deleted = 0
      AND status = 1
      AND merchant_id IS NOT NULL
) merchant_scope
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND permission.deleted = 0
  AND permission.status = 1;

-- 6. 系统角色补齐菜单和资源权限，避免管理员刷新后丢失新增按钮能力。
INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT app.id, role.id, menu.id, 0
FROM sys_app app
JOIN sys_role role ON role.app_id = app.id
JOIN sys_menu menu ON menu.app_id = app.id
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND role.role_type = 'SYSTEM'
  AND role.deleted = 0
  AND menu.deleted = 0
  AND menu.status = 1;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT app.id, role.id, permission.id, 0
FROM sys_app app
JOIN sys_role role ON role.app_id = app.id
JOIN sys_permission permission ON permission.app_id = app.id
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND role.role_type = 'SYSTEM'
  AND role.deleted = 0
  AND permission.deleted = 0
  AND permission.status = 1;
