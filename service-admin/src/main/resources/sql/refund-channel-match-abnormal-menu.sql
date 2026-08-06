SET NAMES utf8mb4;

-- 退款管理和勾兑异常交易菜单草案。仅供评审，发布时由变更平台执行。

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, item.menu_code, item.menu_name, 'MENU', item.route_path,
       item.component_path, item.permission_code, item.icon, 1, 1, 0, item.sort_no, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'admin_transaction_catalog_v1'
                    AND parent.deleted = 0
JOIN (
    SELECT 'admin_transaction_refund_v1' menu_code, '退款管理' menu_name,
           '/transaction/refund' route_path, 'transaction/refund' component_path,
           'transaction:refund:list' permission_code, 'RefreshLeft' icon, 61 sort_no
    UNION ALL
    SELECT 'admin_transaction_match_abnormal_v1', '勾兑异常交易',
           '/transaction/channel-match-abnormal', 'transaction/channel-match-abnormal',
           'transaction:match-abnormal:list', 'Warning', 62
) item
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu exists_menu
      WHERE exists_menu.app_id = app.id
        AND exists_menu.menu_code = item.menu_code
        AND exists_menu.deleted = 0
  );

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, item.menu_code, item.menu_name, 'BUTTON', NULL, NULL,
       item.permission_code, NULL, 0, 0, 0, item.sort_no, 1, 0
FROM sys_app app
JOIN (
    SELECT 'admin_transaction_refund_v1' parent_code, 'admin_transaction_refund_detail_v1' menu_code,
           '退款详情' menu_name, 'transaction:refund:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'admin_transaction_refund_v1', 'admin_transaction_refund_export_v1',
           '退款导出', 'transaction:refund:export', 2
    UNION ALL SELECT 'admin_transaction_refund_v1', 'admin_transaction_refund_approve_v1',
           '退款审批通过', 'transaction:refund:approve', 3
    UNION ALL SELECT 'admin_transaction_refund_v1', 'admin_transaction_refund_reject_v1',
           '退款审批拒绝', 'transaction:refund:reject', 4
    UNION ALL SELECT 'admin_transaction_match_abnormal_v1', 'admin_transaction_match_abnormal_detail_v1',
           '异常详情', 'transaction:match-abnormal:detail', 1
    UNION ALL SELECT 'admin_transaction_match_abnormal_v1', 'admin_transaction_match_abnormal_export_v1',
           '异常导出', 'transaction:match-abnormal:export', 2
    UNION ALL SELECT 'admin_transaction_match_abnormal_v1', 'admin_transaction_match_abnormal_assign_v1',
           '领取或转派', 'transaction:match-abnormal:assign', 3
    UNION ALL SELECT 'admin_transaction_match_abnormal_v1', 'admin_transaction_match_abnormal_requery_v1',
           '重新勾兑', 'transaction:match-abnormal:requery', 4
    UNION ALL SELECT 'admin_transaction_match_abnormal_v1', 'admin_transaction_match_abnormal_batch_requery_v1',
           '批量重新勾兑', 'transaction:match-abnormal:batch-requery', 5
    UNION ALL SELECT 'admin_transaction_match_abnormal_v1', 'admin_transaction_match_abnormal_resolve_v1',
           '处置异常', 'transaction:match-abnormal:resolve', 6
) item
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = item.parent_code
                    AND parent.deleted = 0
WHERE app.app_code = 'ADMIN'
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
    SELECT 'admin_transaction_refund_v1' menu_code, 'transaction:refund:list' permission_code,
           '退款查询' permission_name, 'MENU' permission_type, 'POST' resource_method,
           '/admin/transactions/refunds/search' resource_path, '查询退款和撤销记录' description
    UNION ALL SELECT 'admin_transaction_refund_detail_v1', 'transaction:refund:detail', '退款详情', 'BUTTON', 'GET', '/admin/transactions/refunds/*', '查询退款详情'
    UNION ALL SELECT 'admin_transaction_refund_export_v1', 'transaction:refund:export', '退款导出', 'BUTTON', 'POST', '/admin/transactions/refunds/export', '导出退款记录'
    UNION ALL SELECT 'admin_transaction_refund_approve_v1', 'transaction:refund:approve', '退款审批通过', 'BUTTON', 'POST', '/admin/transactions/refund-approvals/*/approve', '审批通过退款'
    UNION ALL SELECT 'admin_transaction_refund_reject_v1', 'transaction:refund:reject', '退款审批拒绝', 'BUTTON', 'POST', '/admin/transactions/refund-approvals/*/reject', '拒绝退款审批'
    UNION ALL SELECT 'admin_transaction_match_abnormal_v1', 'transaction:match-abnormal:list', '勾兑异常查询', 'MENU', 'POST', '/admin/transactions/channel-match-abnormalities/search', '查询勾兑异常案件'
    UNION ALL SELECT 'admin_transaction_match_abnormal_detail_v1', 'transaction:match-abnormal:detail', '勾兑异常详情', 'BUTTON', 'GET', '/admin/transactions/channel-match-abnormalities/*', '查询勾兑异常详情'
    UNION ALL SELECT 'admin_transaction_match_abnormal_export_v1', 'transaction:match-abnormal:export', '勾兑异常导出', 'BUTTON', 'POST', '/admin/transactions/channel-match-abnormalities/export', '导出勾兑异常案件'
    UNION ALL SELECT 'admin_transaction_match_abnormal_assign_v1', 'transaction:match-abnormal:assign', '领取或转派异常', 'BUTTON', 'POST', '/admin/transactions/channel-match-abnormalities/*/claim', '领取或转派勾兑异常'
    UNION ALL SELECT 'admin_transaction_match_abnormal_requery_v1', 'transaction:match-abnormal:requery', '重新勾兑', 'BUTTON', 'POST', '/admin/transactions/channel-match-abnormalities/*/requery', '单笔重新勾兑'
    UNION ALL SELECT 'admin_transaction_match_abnormal_batch_requery_v1', 'transaction:match-abnormal:batch-requery', '批量重新勾兑', 'BUTTON', 'POST', '/admin/transactions/channel-match-abnormalities/batch-requery', '批量重新勾兑'
    UNION ALL SELECT 'admin_transaction_match_abnormal_resolve_v1', 'transaction:match-abnormal:resolve', '处置勾兑异常', 'BUTTON', 'POST', '/admin/transactions/channel-match-abnormalities/*/resolve', '确认无需修改或忽略案件'
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

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT role.app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = role.app_id AND menu.deleted = 0
WHERE role.role_code = 'SUPER_ADMIN'
  AND role.deleted = 0
  AND menu.menu_code IN (
      'admin_transaction_refund_v1', 'admin_transaction_refund_detail_v1',
      'admin_transaction_refund_export_v1', 'admin_transaction_refund_approve_v1',
      'admin_transaction_refund_reject_v1', 'admin_transaction_match_abnormal_v1',
      'admin_transaction_match_abnormal_detail_v1', 'admin_transaction_match_abnormal_export_v1',
      'admin_transaction_match_abnormal_assign_v1', 'admin_transaction_match_abnormal_requery_v1',
      'admin_transaction_match_abnormal_batch_requery_v1',
      'admin_transaction_match_abnormal_resolve_v1'
  );

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT role.app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = role.app_id AND permission.deleted = 0
WHERE role.role_code = 'SUPER_ADMIN'
  AND role.deleted = 0
  AND (permission.permission_code LIKE 'transaction:refund:%'
       OR permission.permission_code LIKE 'transaction:match-abnormal:%');
