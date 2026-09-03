-- Admin settlement review and reversal menus and endpoint permissions.
-- High-risk create/approve permissions are granted only to SUPER_ADMIN by this migration.

START TRANSACTION;

SET @admin_app_id = (
    SELECT id FROM sys_app WHERE app_code = 'ADMIN' AND deleted = 0 ORDER BY id LIMIT 1
);

UPDATE sys_menu
SET parent_id = 0, menu_name = BINARY '结算管理', menu_type = 'CATALOG', route_path = '/settlement',
    component_path = NULL, permission_code = 'admin:settlement:view', icon = 'CreditCard',
    visible = 1, status = 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = @admin_app_id AND menu_code = 'admin_settlement' AND deleted = 0;

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT @admin_app_id, menu.id, 'admin:settlement:view', '结算管理查看', 'MENU',
       'GET', '/settlement', '访问结算管理目录', 1, 0
FROM sys_menu menu
WHERE menu.app_id = @admin_app_id AND menu.menu_code = 'admin_settlement' AND menu.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing
      WHERE existing.app_id = @admin_app_id
        AND existing.permission_code = 'admin:settlement:view'
        AND existing.deleted = 0
  );

UPDATE sys_permission permission
JOIN sys_menu menu ON menu.app_id = permission.app_id
                  AND menu.menu_code = 'admin_settlement'
                  AND menu.deleted = 0
SET permission.menu_id = menu.id,
    permission.permission_name = BINARY '结算管理查看',
    permission.permission_type = 'MENU',
    permission.resource_method = 'GET',
    permission.resource_path = '/settlement',
    permission.description = BINARY '访问结算管理目录',
    permission.status = 1,
    permission.updated_at = CURRENT_TIMESTAMP(3)
WHERE permission.app_id = @admin_app_id
  AND permission.permission_code = 'admin:settlement:view'
  AND permission.deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, sort_no, status, deleted
)
SELECT @admin_app_id, parent.id, item.menu_code, item.menu_name, 'MENU', item.route_path,
       item.component_path, item.permission_code, item.icon, 1, item.sort_no, 1, 0
FROM sys_menu parent
JOIN (
    SELECT 'admin_settlement_transaction_candidate_v1' menu_code, '交易结算候选' menu_name,
           '/settlement/transaction-candidates' route_path,
           'settlement/transaction-candidate' component_path,
           'settlement:transaction-candidate:list' permission_code, 'Tickets' icon, 1 sort_no
    UNION ALL SELECT 'admin_settlement_reserve_candidate_v1', '保证金结算候选',
           '/settlement/reserve-candidates', 'settlement/reserve-candidate',
           'settlement:reserve-candidate:list', 'Lock', 2
    UNION ALL SELECT 'admin_settlement_review_order_v1', '结算预审单',
           '/settlement/review-orders', 'settlement/review-order',
           'settlement:review-order:list', 'Checked', 3
    UNION ALL SELECT 'admin_settlement_batch_v1', '正式结算批次',
           '/settlement/batches', 'transaction/settlement',
           'settlement:batch:list', 'CollectionTag', 4
    UNION ALL SELECT 'admin_settlement_reversal_order_v1', '结算冲正单',
           '/settlement/reversal-orders', 'settlement/reversal-order',
           'settlement:reversal-order:list', 'RefreshLeft', 5
    UNION ALL SELECT 'admin_settlement_result_item_v1', '交易结算明细',
           '/settlement/result-items', 'settlement/result-item',
           'settlement:result-item:list', 'DocumentCopy', 6
    UNION ALL SELECT 'admin_settlement_reserve_item_v1', '保证金结算明细',
           '/settlement/reserve-items', 'settlement/reserve-item',
           'settlement:reserve-item:list', 'Key', 7
    UNION ALL SELECT 'admin_settlement_posting_v1', '结算入账记录',
           '/settlement/postings', 'settlement/posting',
           'settlement:posting:list', 'Document', 8
    UNION ALL SELECT 'admin_settlement_profile_v1', '结算档案',
           '/settlement/profiles', 'settlement/profile',
           'settlement:profile:list', 'Setting', 9
) item
WHERE parent.app_id = @admin_app_id AND parent.menu_code = 'admin_settlement' AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = @admin_app_id AND existing.menu_code = item.menu_code AND existing.deleted = 0
  );

UPDATE sys_menu menu
JOIN (
    SELECT 'admin_settlement_transaction_candidate_v1' menu_code, '交易结算候选' menu_name,
           '/settlement/transaction-candidates' route_path, 'settlement/transaction-candidate' component_path,
           'settlement:transaction-candidate:list' permission_code, 'Tickets' icon, 1 sort_no
    UNION ALL SELECT 'admin_settlement_reserve_candidate_v1', '保证金结算候选',
           '/settlement/reserve-candidates', 'settlement/reserve-candidate',
           'settlement:reserve-candidate:list', 'Lock', 2
    UNION ALL SELECT 'admin_settlement_review_order_v1', '结算预审单',
           '/settlement/review-orders', 'settlement/review-order',
           'settlement:review-order:list', 'Checked', 3
    UNION ALL SELECT 'admin_settlement_batch_v1', '正式结算批次',
           '/settlement/batches', 'transaction/settlement', 'settlement:batch:list', 'CollectionTag', 4
    UNION ALL SELECT 'admin_settlement_reversal_order_v1', '结算冲正单',
           '/settlement/reversal-orders', 'settlement/reversal-order',
           'settlement:reversal-order:list', 'RefreshLeft', 5
    UNION ALL SELECT 'admin_settlement_result_item_v1', '交易结算明细',
           '/settlement/result-items', 'settlement/result-item',
           'settlement:result-item:list', 'DocumentCopy', 6
    UNION ALL SELECT 'admin_settlement_reserve_item_v1', '保证金结算明细',
           '/settlement/reserve-items', 'settlement/reserve-item',
           'settlement:reserve-item:list', 'Key', 7
    UNION ALL SELECT 'admin_settlement_posting_v1', '结算入账记录',
           '/settlement/postings', 'settlement/posting', 'settlement:posting:list', 'Document', 8
    UNION ALL SELECT 'admin_settlement_profile_v1', '结算档案',
           '/settlement/profiles', 'settlement/profile', 'settlement:profile:list', 'Setting', 9
) item ON item.menu_code = menu.menu_code
JOIN sys_menu parent ON parent.app_id = menu.app_id
                    AND parent.menu_code = 'admin_settlement'
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = BINARY item.menu_name,
    menu.menu_type = 'MENU',
    menu.route_path = item.route_path,
    menu.component_path = item.component_path,
    menu.permission_code = item.permission_code,
    menu.icon = item.icon,
    menu.visible = 1,
    menu.sort_no = item.sort_no,
    menu.status = 1,
    menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.app_id = @admin_app_id AND menu.deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, permission_code,
    visible, sort_no, status, deleted
)
SELECT @admin_app_id, parent.id, item.menu_code, item.menu_name, 'BUTTON', item.permission_code,
       0, item.sort_no, 1, 0
FROM sys_menu parent
JOIN (
    SELECT 'admin_settlement_transaction_review_create_v1' menu_code, '提交交易预审' menu_name,
           'settlement:transaction-review:create' permission_code,
           'admin_settlement_transaction_candidate_v1' parent_code, 1 sort_no
    UNION ALL SELECT 'admin_settlement_transaction_candidate_detail_v1', '交易候选详情',
           'settlement:transaction-candidate:detail', 'admin_settlement_transaction_candidate_v1', 2
    UNION ALL SELECT 'admin_settlement_reserve_review_create_v1', '提交保证金预审',
           'settlement:reserve-review:create', 'admin_settlement_reserve_candidate_v1', 1
    UNION ALL SELECT 'admin_settlement_reserve_candidate_detail_v1', '保证金候选详情',
           'settlement:reserve-candidate:detail', 'admin_settlement_reserve_candidate_v1', 2
    UNION ALL SELECT 'admin_settlement_review_detail_v1', '预审单详情',
           'settlement:review-order:detail', 'admin_settlement_review_order_v1', 1
    UNION ALL SELECT 'admin_settlement_review_approve_v1', '审批通过预审',
           'settlement:review-order:approve', 'admin_settlement_review_order_v1', 2
    UNION ALL SELECT 'admin_settlement_review_reject_v1', '拒绝预审',
           'settlement:review-order:reject', 'admin_settlement_review_order_v1', 3
    UNION ALL SELECT 'admin_settlement_review_cancel_v1', '取消预审',
           'settlement:review-order:cancel', 'admin_settlement_review_order_v1', 4
    UNION ALL SELECT 'admin_settlement_review_export_v1', '导出预审单',
           'settlement:review-order:export', 'admin_settlement_review_order_v1', 5
    UNION ALL SELECT 'admin_settlement_batch_detail_v1', '批次详情',
           'settlement:batch:detail', 'admin_settlement_batch_v1', 1
    UNION ALL SELECT 'admin_settlement_batch_cancel_v1', '取消批次',
           'settlement:batch:cancel', 'admin_settlement_batch_v1', 2
    UNION ALL SELECT 'admin_settlement_reversal_detail_v1', '冲正单详情',
           'settlement:reversal-order:detail', 'admin_settlement_reversal_order_v1', 1
    UNION ALL SELECT 'admin_settlement_reversal_create_v1', '提交冲正申请',
           'settlement:reversal-order:create', 'admin_settlement_reversal_order_v1', 2
    UNION ALL SELECT 'admin_settlement_reversal_approve_v1', '审批通过冲正',
           'settlement:reversal-order:approve', 'admin_settlement_reversal_order_v1', 3
    UNION ALL SELECT 'admin_settlement_reversal_reject_v1', '拒绝冲正',
           'settlement:reversal-order:reject', 'admin_settlement_reversal_order_v1', 4
    UNION ALL SELECT 'admin_settlement_result_item_export_v1', '导出结算结果明细',
           'settlement:result-item:export', 'admin_settlement_result_item_v1', 1
    UNION ALL SELECT 'admin_settlement_reserve_item_export_v1', '导出保证金结算明细',
           'settlement:reserve-item:export', 'admin_settlement_reserve_item_v1', 1
    UNION ALL SELECT 'admin_settlement_posting_export_v1', '导出结算入账记录',
           'settlement:posting:export', 'admin_settlement_posting_v1', 1
    UNION ALL SELECT 'admin_settlement_profile_detail_v1', '结算档案详情',
           'settlement:profile:detail', 'admin_settlement_profile_v1', 1
    UNION ALL SELECT 'admin_settlement_profile_update_v1', '修改结算档案',
           'settlement:profile:update', 'admin_settlement_profile_v1', 2
) item ON item.parent_code = parent.menu_code
WHERE parent.app_id = @admin_app_id AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = @admin_app_id AND existing.menu_code = item.menu_code AND existing.deleted = 0
  );

UPDATE sys_menu menu
JOIN (
    SELECT 'admin_settlement_transaction_review_create_v1' menu_code, '提交交易预审' menu_name,
           'settlement:transaction-review:create' permission_code,
           'admin_settlement_transaction_candidate_v1' parent_code, 1 sort_no
    UNION ALL SELECT 'admin_settlement_transaction_candidate_detail_v1', '交易候选详情',
           'settlement:transaction-candidate:detail', 'admin_settlement_transaction_candidate_v1', 2
    UNION ALL SELECT 'admin_settlement_reserve_review_create_v1', '提交保证金预审',
           'settlement:reserve-review:create', 'admin_settlement_reserve_candidate_v1', 1
    UNION ALL SELECT 'admin_settlement_reserve_candidate_detail_v1', '保证金候选详情',
           'settlement:reserve-candidate:detail', 'admin_settlement_reserve_candidate_v1', 2
    UNION ALL SELECT 'admin_settlement_review_detail_v1', '预审单详情',
           'settlement:review-order:detail', 'admin_settlement_review_order_v1', 1
    UNION ALL SELECT 'admin_settlement_review_approve_v1', '审批通过预审',
           'settlement:review-order:approve', 'admin_settlement_review_order_v1', 2
    UNION ALL SELECT 'admin_settlement_review_reject_v1', '拒绝预审',
           'settlement:review-order:reject', 'admin_settlement_review_order_v1', 3
    UNION ALL SELECT 'admin_settlement_review_cancel_v1', '取消预审',
           'settlement:review-order:cancel', 'admin_settlement_review_order_v1', 4
    UNION ALL SELECT 'admin_settlement_review_export_v1', '导出预审单',
           'settlement:review-order:export', 'admin_settlement_review_order_v1', 5
    UNION ALL SELECT 'admin_settlement_batch_detail_v1', '批次详情',
           'settlement:batch:detail', 'admin_settlement_batch_v1', 1
    UNION ALL SELECT 'admin_settlement_batch_cancel_v1', '取消批次',
           'settlement:batch:cancel', 'admin_settlement_batch_v1', 2
    UNION ALL SELECT 'admin_settlement_reversal_detail_v1', '冲正单详情',
           'settlement:reversal-order:detail', 'admin_settlement_reversal_order_v1', 1
    UNION ALL SELECT 'admin_settlement_reversal_create_v1', '提交冲正申请',
           'settlement:reversal-order:create', 'admin_settlement_reversal_order_v1', 2
    UNION ALL SELECT 'admin_settlement_reversal_approve_v1', '审批通过冲正',
           'settlement:reversal-order:approve', 'admin_settlement_reversal_order_v1', 3
    UNION ALL SELECT 'admin_settlement_reversal_reject_v1', '拒绝冲正',
           'settlement:reversal-order:reject', 'admin_settlement_reversal_order_v1', 4
    UNION ALL SELECT 'admin_settlement_result_item_export_v1', '导出结算结果明细',
           'settlement:result-item:export', 'admin_settlement_result_item_v1', 1
    UNION ALL SELECT 'admin_settlement_reserve_item_export_v1', '导出保证金结算明细',
           'settlement:reserve-item:export', 'admin_settlement_reserve_item_v1', 1
    UNION ALL SELECT 'admin_settlement_posting_export_v1', '导出结算入账记录',
           'settlement:posting:export', 'admin_settlement_posting_v1', 1
    UNION ALL SELECT 'admin_settlement_profile_detail_v1', '结算档案详情',
           'settlement:profile:detail', 'admin_settlement_profile_v1', 1
    UNION ALL SELECT 'admin_settlement_profile_update_v1', '修改结算档案',
           'settlement:profile:update', 'admin_settlement_profile_v1', 2
) item ON item.menu_code = menu.menu_code
JOIN sys_menu parent ON parent.app_id = menu.app_id
                    AND parent.menu_code = item.parent_code
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = BINARY item.menu_name,
    menu.menu_type = 'BUTTON',
    menu.permission_code = item.permission_code,
    menu.visible = 0,
    menu.sort_no = item.sort_no,
    menu.status = 1,
    menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.app_id = @admin_app_id AND menu.deleted = 0;

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT @admin_app_id, menu.id, item.permission_code, item.permission_name, item.permission_type,
       item.resource_method, item.resource_path, item.description, 1, 0
FROM (
    SELECT 'admin_settlement_transaction_candidate_v1' menu_code,
           'settlement:transaction-candidate:list' permission_code, '交易结算候选查询' permission_name,
           'MENU' permission_type, 'POST' resource_method,
           '/admin/settlement/transaction-candidates/search' resource_path, '按Admin商户数据范围查询交易结算候选' description
    UNION ALL SELECT 'admin_settlement_transaction_candidate_detail_v1',
           'settlement:transaction-candidate:detail', '交易结算候选详情', 'BUTTON', 'GET',
           '/admin/settlement/transaction-candidates/*', '按Admin商户数据范围查询交易结算候选详情'
    UNION ALL SELECT 'admin_settlement_reserve_candidate_v1', 'settlement:reserve-candidate:list',
           '保证金结算候选查询', 'MENU', 'POST', '/admin/settlement/reserve-candidates/search', '按Admin商户数据范围查询保证金结算候选'
    UNION ALL SELECT 'admin_settlement_reserve_candidate_detail_v1',
           'settlement:reserve-candidate:detail', '保证金结算候选详情', 'BUTTON', 'GET',
           '/admin/settlement/reserve-candidates/*', '按Admin商户数据范围查询保证金结算候选详情'
    UNION ALL SELECT 'admin_settlement_review_order_v1', 'settlement:review-order:list',
           '预审单查询', 'MENU', 'POST', '/admin/settlement/review-orders/search', '按Admin商户数据范围查询结算预审单'
    UNION ALL SELECT 'admin_settlement_review_detail_v1', 'settlement:review-order:detail',
           '预审单详情', 'BUTTON', 'GET', '/admin/settlement/review-orders/*', '查询结算预审单不可变快照'
    UNION ALL SELECT 'admin_settlement_transaction_review_create_v1', 'settlement:transaction-review:create',
           '提交交易预审', 'BUTTON', 'POST', '/admin/settlement/transaction-review-orders', '锁定交易候选并提交Maker-Checker预审'
    UNION ALL SELECT 'admin_settlement_reserve_review_create_v1', 'settlement:reserve-review:create',
           '提交保证金预审', 'BUTTON', 'POST', '/admin/settlement/reserve-review-orders', '锁定保证金候选并提交Maker-Checker预审'
    UNION ALL SELECT 'admin_settlement_review_approve_v1', 'settlement:review-order:approve',
           '审批通过预审', 'BUTTON', 'POST', '/admin/settlement/review-orders/*/approve', 'Maker-Checker审批并创建正式批次'
    UNION ALL SELECT 'admin_settlement_review_reject_v1', 'settlement:review-order:reject',
           '拒绝预审', 'BUTTON', 'POST', '/admin/settlement/review-orders/*/reject', '拒绝预审并释放候选'
    UNION ALL SELECT 'admin_settlement_review_cancel_v1', 'settlement:review-order:cancel',
           '取消预审', 'BUTTON', 'POST', '/admin/settlement/review-orders/*/cancel', 'Maker取消自己的待审批预审'
    UNION ALL SELECT 'admin_settlement_review_export_v1', 'settlement:review-order:export',
           '导出预审单', 'BUTTON', 'POST', '/admin/settlement/review-orders/export', '按当前筛选和Admin商户数据范围导出预审单'
    UNION ALL SELECT 'admin_settlement_batch_v1', 'settlement:batch:list',
           '正式结算批次查询', 'MENU', 'POST', '/admin/settlement/batches/search', '按Admin商户数据范围查询正式批次'
    UNION ALL SELECT 'admin_settlement_batch_detail_v1', 'settlement:batch:detail',
           '正式结算批次详情', 'BUTTON', 'GET', '/admin/settlement/batches/*', '查询正式批次详情'
    UNION ALL SELECT 'admin_settlement_batch_cancel_v1', 'settlement:batch:cancel',
           '取消未入账批次', 'BUTTON', 'POST', '/admin/settlement/batches/*/cancel', '取消未入账正式批次'
    UNION ALL SELECT 'admin_settlement_result_item_v1', 'settlement:result-item:list',
           '结算结果明细查询', 'MENU', 'POST', '/admin/settlement/result-items/search', '按Admin商户数据范围查询不可变结算结果明细'
    UNION ALL SELECT 'admin_settlement_result_item_export_v1', 'settlement:result-item:export',
           '结算结果明细导出', 'BUTTON', 'POST', '/admin/settlement/result-items/export', '按当前筛选和Admin商户数据范围导出结算结果明细'
    UNION ALL SELECT 'admin_settlement_reserve_item_v1', 'settlement:reserve-item:list',
           '保证金结算明细查询', 'MENU', 'POST', '/admin/settlement/reserve-items/search', '按Admin商户数据范围查询保证金不可变动作和资金责任'
    UNION ALL SELECT 'admin_settlement_reserve_item_export_v1', 'settlement:reserve-item:export',
           '保证金结算明细导出', 'BUTTON', 'POST', '/admin/settlement/reserve-items/export', '按当前筛选和Admin商户数据范围导出保证金不可变动作'
    UNION ALL SELECT 'admin_settlement_posting_v1', 'settlement:posting:list',
           '结算入账记录查询', 'MENU', 'POST', '/admin/settlement/postings/search', '按Admin商户数据范围查询结算净额资金流水'
    UNION ALL SELECT 'admin_settlement_posting_export_v1', 'settlement:posting:export',
           '结算入账记录导出', 'BUTTON', 'POST', '/admin/settlement/postings/export', '按当前筛选和Admin商户数据范围导出结算净额资金流水'
    UNION ALL SELECT 'admin_settlement_reversal_order_v1', 'settlement:reversal-order:list',
           '结算冲正单查询', 'MENU', 'POST', '/admin/settlement/reversal-orders/search', '按Admin商户数据范围查询冲正单'
    UNION ALL SELECT 'admin_settlement_reversal_detail_v1', 'settlement:reversal-order:detail',
           '结算冲正单详情', 'BUTTON', 'GET', '/admin/settlement/reversal-orders/*', '查询冲正冻结资金身份和完整Maker-Checker审计'
    UNION ALL SELECT 'admin_settlement_reversal_create_v1', 'settlement:reversal-order:create',
           '提交结算冲正申请', 'BUTTON', 'POST', '/admin/settlement/reversal-orders', '冻结原批次资金身份并提交冲正申请'
    UNION ALL SELECT 'admin_settlement_reversal_approve_v1', 'settlement:reversal-order:approve',
           '审批通过结算冲正', 'BUTTON', 'POST', '/admin/settlement/reversal-orders/*/approve', '异账号复核后执行资金冲正'
    UNION ALL SELECT 'admin_settlement_reversal_reject_v1', 'settlement:reversal-order:reject',
           '拒绝结算冲正', 'BUTTON', 'POST', '/admin/settlement/reversal-orders/*/reject', '异账号复核并拒绝冲正申请'
    UNION ALL SELECT 'admin_settlement_profile_v1', 'settlement:profile:list',
           '结算档案查询', 'MENU', 'POST', '/admin/settlement/profiles/search', '按Admin商户数据范围查询结算档案'
    UNION ALL SELECT 'admin_settlement_profile_detail_v1', 'settlement:profile:detail',
           '结算档案详情', 'BUTTON', 'GET', '/admin/settlement/profiles/*', '按Admin商户数据范围查询结算档案详情'
    UNION ALL SELECT 'admin_settlement_profile_update_v1', 'settlement:profile:update',
           '修改结算档案', 'BUTTON', 'PUT', '/admin/settlement/profiles/*', '使用版本CAS修改后续结算处理模式、时区和日切时间'
) item
JOIN sys_menu menu ON menu.app_id = @admin_app_id AND menu.menu_code = item.menu_code AND menu.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission existing
    WHERE existing.app_id = @admin_app_id AND existing.permission_code = item.permission_code AND existing.deleted = 0
);

-- Converge permissions created by earlier transaction-settlement menu migrations onto the current menu tree.
UPDATE sys_permission permission
JOIN (
    SELECT 'admin_settlement_transaction_candidate_v1' menu_code,
           'settlement:transaction-candidate:list' permission_code, '交易结算候选查询' permission_name,
           'MENU' permission_type, 'POST' resource_method,
           '/admin/settlement/transaction-candidates/search' resource_path, '按Admin商户数据范围查询交易结算候选' description
    UNION ALL SELECT 'admin_settlement_transaction_candidate_detail_v1',
           'settlement:transaction-candidate:detail', '交易结算候选详情', 'BUTTON', 'GET',
           '/admin/settlement/transaction-candidates/*', '按Admin商户数据范围查询交易结算候选详情'
    UNION ALL SELECT 'admin_settlement_reserve_candidate_v1', 'settlement:reserve-candidate:list',
           '保证金结算候选查询', 'MENU', 'POST', '/admin/settlement/reserve-candidates/search', '按Admin商户数据范围查询保证金结算候选'
    UNION ALL SELECT 'admin_settlement_reserve_candidate_detail_v1',
           'settlement:reserve-candidate:detail', '保证金结算候选详情', 'BUTTON', 'GET',
           '/admin/settlement/reserve-candidates/*', '按Admin商户数据范围查询保证金结算候选详情'
    UNION ALL SELECT 'admin_settlement_review_order_v1', 'settlement:review-order:list',
           '预审单查询', 'MENU', 'POST', '/admin/settlement/review-orders/search', '按Admin商户数据范围查询结算预审单'
    UNION ALL SELECT 'admin_settlement_review_detail_v1', 'settlement:review-order:detail',
           '预审单详情', 'BUTTON', 'GET', '/admin/settlement/review-orders/*', '查询结算预审单不可变快照'
    UNION ALL SELECT 'admin_settlement_transaction_review_create_v1', 'settlement:transaction-review:create',
           '提交交易预审', 'BUTTON', 'POST', '/admin/settlement/transaction-review-orders', '锁定交易候选并提交Maker-Checker预审'
    UNION ALL SELECT 'admin_settlement_reserve_review_create_v1', 'settlement:reserve-review:create',
           '提交保证金预审', 'BUTTON', 'POST', '/admin/settlement/reserve-review-orders', '锁定保证金候选并提交Maker-Checker预审'
    UNION ALL SELECT 'admin_settlement_review_approve_v1', 'settlement:review-order:approve',
           '审批通过预审', 'BUTTON', 'POST', '/admin/settlement/review-orders/*/approve', 'Maker-Checker审批并创建正式批次'
    UNION ALL SELECT 'admin_settlement_review_reject_v1', 'settlement:review-order:reject',
           '拒绝预审', 'BUTTON', 'POST', '/admin/settlement/review-orders/*/reject', '拒绝预审并释放候选'
    UNION ALL SELECT 'admin_settlement_review_cancel_v1', 'settlement:review-order:cancel',
           '取消预审', 'BUTTON', 'POST', '/admin/settlement/review-orders/*/cancel', 'Maker取消自己的待审批预审'
    UNION ALL SELECT 'admin_settlement_review_export_v1', 'settlement:review-order:export',
           '导出预审单', 'BUTTON', 'POST', '/admin/settlement/review-orders/export', '按当前筛选和Admin商户数据范围导出预审单'
    UNION ALL SELECT 'admin_settlement_batch_v1', 'settlement:batch:list',
           '正式结算批次查询', 'MENU', 'POST', '/admin/settlement/batches/search', '按Admin商户数据范围查询正式批次'
    UNION ALL SELECT 'admin_settlement_batch_detail_v1', 'settlement:batch:detail',
           '正式结算批次详情', 'BUTTON', 'GET', '/admin/settlement/batches/*', '查询正式批次详情'
    UNION ALL SELECT 'admin_settlement_batch_cancel_v1', 'settlement:batch:cancel',
           '取消未入账批次', 'BUTTON', 'POST', '/admin/settlement/batches/*/cancel', '取消未入账正式批次'
    UNION ALL SELECT 'admin_settlement_result_item_v1', 'settlement:result-item:list',
           '结算结果明细查询', 'MENU', 'POST', '/admin/settlement/result-items/search', '按Admin商户数据范围查询不可变结算结果明细'
    UNION ALL SELECT 'admin_settlement_result_item_export_v1', 'settlement:result-item:export',
           '结算结果明细导出', 'BUTTON', 'POST', '/admin/settlement/result-items/export', '按当前筛选和Admin商户数据范围导出结算结果明细'
    UNION ALL SELECT 'admin_settlement_reserve_item_v1', 'settlement:reserve-item:list',
           '保证金结算明细查询', 'MENU', 'POST', '/admin/settlement/reserve-items/search', '按Admin商户数据范围查询保证金不可变动作和资金责任'
    UNION ALL SELECT 'admin_settlement_reserve_item_export_v1', 'settlement:reserve-item:export',
           '保证金结算明细导出', 'BUTTON', 'POST', '/admin/settlement/reserve-items/export', '按当前筛选和Admin商户数据范围导出保证金不可变动作'
    UNION ALL SELECT 'admin_settlement_posting_v1', 'settlement:posting:list',
           '结算入账记录查询', 'MENU', 'POST', '/admin/settlement/postings/search', '按Admin商户数据范围查询结算净额资金流水'
    UNION ALL SELECT 'admin_settlement_posting_export_v1', 'settlement:posting:export',
           '结算入账记录导出', 'BUTTON', 'POST', '/admin/settlement/postings/export', '按当前筛选和Admin商户数据范围导出结算净额资金流水'
    UNION ALL SELECT 'admin_settlement_reversal_order_v1', 'settlement:reversal-order:list',
           '结算冲正单查询', 'MENU', 'POST', '/admin/settlement/reversal-orders/search', '按Admin商户数据范围查询冲正单'
    UNION ALL SELECT 'admin_settlement_reversal_detail_v1', 'settlement:reversal-order:detail',
           '结算冲正单详情', 'BUTTON', 'GET', '/admin/settlement/reversal-orders/*', '查询冲正冻结资金身份和完整Maker-Checker审计'
    UNION ALL SELECT 'admin_settlement_reversal_create_v1', 'settlement:reversal-order:create',
           '提交结算冲正申请', 'BUTTON', 'POST', '/admin/settlement/reversal-orders', '冻结原批次资金身份并提交冲正申请'
    UNION ALL SELECT 'admin_settlement_reversal_approve_v1', 'settlement:reversal-order:approve',
           '审批通过结算冲正', 'BUTTON', 'POST', '/admin/settlement/reversal-orders/*/approve', '异账号复核后执行资金冲正'
    UNION ALL SELECT 'admin_settlement_reversal_reject_v1', 'settlement:reversal-order:reject',
           '拒绝结算冲正', 'BUTTON', 'POST', '/admin/settlement/reversal-orders/*/reject', '异账号复核并拒绝冲正申请'
    UNION ALL SELECT 'admin_settlement_profile_v1', 'settlement:profile:list',
           '结算档案查询', 'MENU', 'POST', '/admin/settlement/profiles/search', '按Admin商户数据范围查询结算档案'
    UNION ALL SELECT 'admin_settlement_profile_detail_v1', 'settlement:profile:detail',
           '结算档案详情', 'BUTTON', 'GET', '/admin/settlement/profiles/*', '按Admin商户数据范围查询结算档案详情'
    UNION ALL SELECT 'admin_settlement_profile_update_v1', 'settlement:profile:update',
           '修改结算档案', 'BUTTON', 'PUT', '/admin/settlement/profiles/*', '使用版本CAS修改后续结算处理模式、时区和日切时间'
) item ON item.permission_code = permission.permission_code
JOIN sys_menu menu ON menu.app_id = permission.app_id
                  AND menu.menu_code = item.menu_code
                  AND menu.deleted = 0
SET permission.menu_id = menu.id,
    permission.permission_name = BINARY item.permission_name,
    permission.permission_type = item.permission_type,
    permission.resource_method = item.resource_method,
    permission.resource_path = item.resource_path,
    permission.description = BINARY item.description,
    permission.status = 1,
    permission.updated_at = CURRENT_TIMESTAMP(3)
WHERE permission.app_id = @admin_app_id AND permission.deleted = 0;

-- The legacy single-step reversal route is permanently retired.
UPDATE sys_role_permission role_permission
JOIN sys_permission permission
  ON permission.app_id = role_permission.app_id
 AND permission.id = role_permission.permission_id
SET role_permission.deleted = role_permission.id
WHERE permission.app_id = @admin_app_id
  AND permission.permission_code = 'settlement:batch:reverse'
  AND permission.deleted = 0
  AND role_permission.deleted = 0;

UPDATE sys_permission
SET deleted = id, status = 0
WHERE app_id = @admin_app_id
  AND permission_code = 'settlement:batch:reverse'
  AND deleted = 0;

UPDATE sys_role_menu role_menu
JOIN sys_menu menu
  ON menu.app_id = role_menu.app_id
 AND menu.id = role_menu.menu_id
SET role_menu.deleted = role_menu.id
WHERE menu.app_id = @admin_app_id
  AND (menu.menu_code IN (
          'admin_settlement_batch_reverse_legacy_v1',
          'admin_transaction_settlement_reverse_v1'
      ) OR menu.permission_code = 'settlement:batch:reverse')
  AND menu.deleted = 0
  AND role_menu.deleted = 0;

UPDATE sys_menu
SET deleted = id, status = 0, visible = 0
WHERE app_id = @admin_app_id
  AND (menu_code IN (
          'admin_settlement_batch_reverse_legacy_v1',
          'admin_transaction_settlement_reverse_v1'
      ) OR permission_code = 'settlement:batch:reverse')
  AND deleted = 0;

-- Retire the duplicate transaction-management entry after its permissions have converged here.
UPDATE sys_role_menu role_menu
JOIN sys_menu menu
  ON menu.app_id = role_menu.app_id
 AND menu.id = role_menu.menu_id
SET role_menu.deleted = role_menu.id
WHERE menu.app_id = @admin_app_id
  AND menu.menu_code IN (
      'admin_transaction_settlement_v1',
      'admin_transaction_settlement_detail_v1',
      'admin_transaction_settlement_cancel_v1'
  )
  AND menu.deleted = 0
  AND role_menu.deleted = 0;

UPDATE sys_menu
SET deleted = id, status = 0, visible = 0
WHERE app_id = @admin_app_id
  AND menu_code IN (
      'admin_transaction_settlement_v1',
      'admin_transaction_settlement_detail_v1',
      'admin_transaction_settlement_cancel_v1'
  )
  AND deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT role.app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_menu menu ON menu.app_id = role.app_id AND menu.deleted = 0
WHERE role.app_id = @admin_app_id AND role.role_code = 'SUPER_ADMIN' AND role.deleted = 0
  AND (menu.menu_code = 'admin_settlement' OR menu.menu_code LIKE 'admin_settlement_%_v1');

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT role.app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id AND permission.deleted = 0
WHERE role.app_id = @admin_app_id AND role.role_code = 'SUPER_ADMIN' AND role.deleted = 0
  AND (permission.permission_code = 'admin:settlement:view'
       OR permission.permission_code LIKE 'settlement:%');

-- ADMIN_OPERATOR is not a default holder of high-risk reversal command permissions.
UPDATE sys_role_permission role_permission
JOIN sys_role role
  ON role.app_id = role_permission.app_id
 AND role.id = role_permission.role_id
JOIN sys_permission permission
  ON permission.app_id = role_permission.app_id
 AND permission.id = role_permission.permission_id
SET role_permission.deleted = role_permission.id
WHERE role.app_id = @admin_app_id
  AND role.role_code = 'ADMIN_OPERATOR'
  AND role.deleted = 0
  AND permission.deleted = 0
  AND permission.permission_code IN (
      'settlement:reversal-order:create',
      'settlement:reversal-order:approve',
      'settlement:reversal-order:reject'
  )
  AND role_permission.deleted = 0;

COMMIT;
