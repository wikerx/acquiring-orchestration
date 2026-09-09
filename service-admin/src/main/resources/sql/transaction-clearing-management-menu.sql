SET NAMES utf8mb4;

-- 管理系统交易清分菜单、受控人工操作权限及 SUPER_ADMIN 授权迁移草案。
-- 仅包含幂等 DML，必须由变更平台审核后执行；本脚本不启用清分、不修改清分业务数据。

START TRANSACTION;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, 'admin_transaction_clearing_v1', '交易清分', 'MENU',
       '/transaction/clearing', 'transaction/clearing', 'clearing:record:list',
       'Coin', 1, 1, 0, 63, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'admin_transaction_catalog_v1'
                    AND parent.deleted = 0
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu exists_menu
      WHERE exists_menu.app_id = app.id
        AND exists_menu.menu_code = 'admin_transaction_clearing_v1'
        AND exists_menu.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = menu.app_id
                    AND parent.menu_code = 'admin_transaction_catalog_v1'
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = BINARY '交易清分',
    menu.menu_type = 'MENU',
    menu.route_path = '/transaction/clearing',
    menu.component_path = 'transaction/clearing',
    menu.permission_code = 'clearing:record:list',
    menu.icon = 'Coin',
    menu.visible = 1,
    menu.keep_alive = 1,
    menu.external_link = 0,
    menu.sort_no = 63,
    menu.status = 1,
    menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.menu_code = 'admin_transaction_clearing_v1'
  AND menu.deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, item.menu_code, item.menu_name, 'BUTTON', NULL, NULL,
       item.permission_code, NULL, 0, 0, 0, item.sort_no, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'admin_transaction_clearing_v1'
                    AND parent.deleted = 0
JOIN (
    SELECT 'admin_transaction_clearing_detail_v1' menu_code, '清分详情' menu_name,
           'clearing:record:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'admin_transaction_clearing_retry_v1', '人工重试清分',
           'clearing:record:retry', 2
    UNION ALL SELECT 'admin_transaction_clearing_review_v1', '升级人工复核',
           'clearing:record:review', 3
    UNION ALL SELECT 'admin_transaction_clearing_recalculate_v1', '重算未结算清分',
           'clearing:record:recalculate', 4
    UNION ALL SELECT 'admin_transaction_reserve_adjustment_submit_v1', '提交保证金差额申请',
           'clearing:reserve-adjustment:submit', 5
    UNION ALL SELECT 'admin_transaction_reserve_adjustment_review_v1', '复核保证金差额申请',
           'clearing:reserve-adjustment:review', 6
    UNION ALL SELECT 'admin_transaction_tier_replay_submit_v1', '提交阶梯期间重放',
           'clearing:tier-period-replay:submit', 7
    UNION ALL SELECT 'admin_transaction_tier_replay_review_v1', '复核阶梯期间重放',
           'clearing:tier-period-replay:review', 8
) item
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu exists_menu
      WHERE exists_menu.app_id = app.id
        AND exists_menu.menu_code = item.menu_code
        AND exists_menu.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN (
    SELECT 'admin_transaction_clearing_detail_v1' menu_code, '清分详情' menu_name,
           'clearing:record:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'admin_transaction_clearing_retry_v1', '人工重试清分',
           'clearing:record:retry', 2
    UNION ALL SELECT 'admin_transaction_clearing_review_v1', '升级人工复核',
           'clearing:record:review', 3
    UNION ALL SELECT 'admin_transaction_clearing_recalculate_v1', '重算未结算清分',
           'clearing:record:recalculate', 4
    UNION ALL SELECT 'admin_transaction_reserve_adjustment_submit_v1', '提交保证金差额申请',
           'clearing:reserve-adjustment:submit', 5
    UNION ALL SELECT 'admin_transaction_reserve_adjustment_review_v1', '复核保证金差额申请',
           'clearing:reserve-adjustment:review', 6
    UNION ALL SELECT 'admin_transaction_tier_replay_submit_v1', '提交阶梯期间重放',
           'clearing:tier-period-replay:submit', 7
    UNION ALL SELECT 'admin_transaction_tier_replay_review_v1', '复核阶梯期间重放',
           'clearing:tier-period-replay:review', 8
) item ON item.menu_code = menu.menu_code
JOIN sys_menu parent ON parent.app_id = menu.app_id
                    AND parent.menu_code = 'admin_transaction_clearing_v1'
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = BINARY item.menu_name,
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
    SELECT 'admin_transaction_clearing_v1' menu_code, 'clearing:record:list' permission_code,
           '清分记录查询' permission_name, 'MENU' permission_type, 'POST' resource_method,
           '/admin/clearing/records/search' resource_path, '按单季度查询交易清分权威状态' description
    UNION ALL SELECT 'admin_transaction_clearing_detail_v1', 'clearing:record:detail',
           '清分记录详情', 'BUTTON', 'GET', '/admin/clearing/records/*',
           '查询当前修订的交易费用和保证金清分明细'
    UNION ALL SELECT 'admin_transaction_clearing_retry_v1', 'clearing:record:retry',
           '人工重试清分', 'BUTTON', 'POST', '/admin/clearing/records/*/retry',
           '按交易分片时间和版本 CAS 安排清分重试'
    UNION ALL SELECT 'admin_transaction_clearing_review_v1', 'clearing:record:review',
           '升级人工复核', 'BUTTON', 'POST', '/admin/clearing/records/*/review',
           '将未完成清分升级为人工复核异常案件'
    UNION ALL SELECT 'admin_transaction_clearing_recalculate_v1', 'clearing:record:recalculate',
           '重算未结算清分', 'BUTTON', 'POST', '/admin/clearing/records/*/recalculate',
           '使用指定不可变费用版本重算尚未结算的清分结果'
    UNION ALL SELECT 'admin_transaction_reserve_adjustment_submit_v1',
           'clearing:reserve-adjustment:submit', '提交保证金差额申请', 'BUTTON', 'POST',
           '/admin/clearing/reserve-adjustments',
           '冻结原保证金标签币种和状态版本并提交双人复核申请'
    UNION ALL SELECT 'admin_transaction_reserve_adjustment_review_v1',
           'clearing:reserve-adjustment:review', '复核保证金差额申请', 'BUTTON', 'POST',
           '/admin/clearing/reserve-adjustments/*/review',
           '由不同操作人批准或拒绝保证金差额申请'
    UNION ALL SELECT 'admin_transaction_tier_replay_submit_v1',
           'clearing:tier-period-replay:submit', '提交阶梯期间重放', 'BUTTON', 'POST',
           '/admin/clearing/tier-period-replays',
           '冻结商户不可变费用版本月度阶梯闭包并提交双人复核'
    UNION ALL SELECT 'admin_transaction_tier_replay_review_v1',
           'clearing:tier-period-replay:review', '复核阶梯期间重放', 'BUTTON', 'POST',
           '/admin/clearing/tier-period-replays/*/review',
           '由不同操作人批准或拒绝阶梯期间重放申请'
) item
JOIN sys_menu menu ON menu.app_id = app.id
                  AND menu.menu_code = item.menu_code
                  AND menu.deleted = 0
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_permission exists_permission
      WHERE exists_permission.app_id = app.id
        AND exists_permission.permission_code = item.permission_code
        AND exists_permission.deleted = 0
  );

UPDATE sys_permission permission
JOIN sys_app app ON app.id = permission.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN (
    SELECT 'admin_transaction_clearing_v1' menu_code, 'clearing:record:list' permission_code,
           '清分记录查询' permission_name, 'MENU' permission_type, 'POST' resource_method,
           '/admin/clearing/records/search' resource_path, '按单季度查询交易清分权威状态' description
    UNION ALL SELECT 'admin_transaction_clearing_detail_v1', 'clearing:record:detail',
           '清分记录详情', 'BUTTON', 'GET', '/admin/clearing/records/*',
           '查询当前修订的交易费用和保证金清分明细'
    UNION ALL SELECT 'admin_transaction_clearing_retry_v1', 'clearing:record:retry',
           '人工重试清分', 'BUTTON', 'POST', '/admin/clearing/records/*/retry',
           '按交易分片时间和版本 CAS 安排清分重试'
    UNION ALL SELECT 'admin_transaction_clearing_review_v1', 'clearing:record:review',
           '升级人工复核', 'BUTTON', 'POST', '/admin/clearing/records/*/review',
           '将未完成清分升级为人工复核异常案件'
    UNION ALL SELECT 'admin_transaction_clearing_recalculate_v1', 'clearing:record:recalculate',
           '重算未结算清分', 'BUTTON', 'POST', '/admin/clearing/records/*/recalculate',
           '使用指定不可变费用版本重算尚未结算的清分结果'
    UNION ALL SELECT 'admin_transaction_reserve_adjustment_submit_v1',
           'clearing:reserve-adjustment:submit', '提交保证金差额申请', 'BUTTON', 'POST',
           '/admin/clearing/reserve-adjustments',
           '冻结原保证金标签币种和状态版本并提交双人复核申请'
    UNION ALL SELECT 'admin_transaction_reserve_adjustment_review_v1',
           'clearing:reserve-adjustment:review', '复核保证金差额申请', 'BUTTON', 'POST',
           '/admin/clearing/reserve-adjustments/*/review',
           '由不同操作人批准或拒绝保证金差额申请'
    UNION ALL SELECT 'admin_transaction_tier_replay_submit_v1',
           'clearing:tier-period-replay:submit', '提交阶梯期间重放', 'BUTTON', 'POST',
           '/admin/clearing/tier-period-replays',
           '冻结商户不可变费用版本月度阶梯闭包并提交双人复核'
    UNION ALL SELECT 'admin_transaction_tier_replay_review_v1',
           'clearing:tier-period-replay:review', '复核阶梯期间重放', 'BUTTON', 'POST',
           '/admin/clearing/tier-period-replays/*/review',
           '由不同操作人批准或拒绝阶梯期间重放申请'
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
WHERE permission.deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT role.app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = role.app_id AND menu.deleted = 0
WHERE role.role_code = 'SUPER_ADMIN'
  AND role.deleted = 0
  AND menu.menu_code IN (
      'admin_transaction_clearing_v1',
      'admin_transaction_clearing_detail_v1',
      'admin_transaction_clearing_retry_v1',
      'admin_transaction_clearing_review_v1',
      'admin_transaction_clearing_recalculate_v1',
      'admin_transaction_reserve_adjustment_submit_v1',
      'admin_transaction_reserve_adjustment_review_v1',
      'admin_transaction_tier_replay_submit_v1',
      'admin_transaction_tier_replay_review_v1'
  );

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT role.app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = role.app_id AND permission.deleted = 0
WHERE role.role_code = 'SUPER_ADMIN'
  AND role.deleted = 0
  AND permission.permission_code LIKE 'clearing:%';

-- 结算批次管理菜单及最小查询、取消、冲正权限；所有命令仍由数据库状态 CAS 和唯一键兜底。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, 'admin_transaction_settlement_v1', '交易结算', 'MENU',
       '/transaction/settlement', 'transaction/settlement', 'settlement:batch:list',
       'Wallet', 1, 1, 0, 64, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'admin_transaction_catalog_v1'
                    AND parent.deleted = 0
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu exists_menu
      WHERE exists_menu.app_id = app.id
        AND exists_menu.menu_code = 'admin_transaction_settlement_v1'
        AND exists_menu.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = menu.app_id
                    AND parent.menu_code = 'admin_transaction_catalog_v1'
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = BINARY '交易结算',
    menu.menu_type = 'MENU',
    menu.route_path = '/transaction/settlement',
    menu.component_path = 'transaction/settlement',
    menu.permission_code = 'settlement:batch:list',
    menu.icon = 'Wallet',
    menu.visible = 1,
    menu.keep_alive = 1,
    menu.external_link = 0,
    menu.sort_no = 64,
    menu.status = 1,
    menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.menu_code = 'admin_transaction_settlement_v1'
  AND menu.deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, item.menu_code, item.menu_name, 'BUTTON', NULL, NULL,
       item.permission_code, NULL, 0, 0, 0, item.sort_no, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'admin_transaction_settlement_v1'
                    AND parent.deleted = 0
JOIN (
    SELECT 'admin_transaction_settlement_detail_v1' menu_code, '结算详情' menu_name,
           'settlement:batch:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'admin_transaction_settlement_cancel_v1', '取消未入账批次',
           'settlement:batch:cancel', 2
) item
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu exists_menu
      WHERE exists_menu.app_id = app.id
        AND exists_menu.menu_code = item.menu_code
        AND exists_menu.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN (
    SELECT 'admin_transaction_settlement_detail_v1' menu_code, '结算详情' menu_name,
           'settlement:batch:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'admin_transaction_settlement_cancel_v1', '取消未入账批次',
           'settlement:batch:cancel', 2
) item ON item.menu_code = menu.menu_code
JOIN sys_menu parent ON parent.app_id = menu.app_id
                    AND parent.menu_code = 'admin_transaction_settlement_v1'
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = BINARY item.menu_name,
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
    SELECT 'admin_transaction_settlement_v1' menu_code, 'settlement:batch:list' permission_code,
           '结算批次查询' permission_name, 'MENU' permission_type, 'POST' resource_method,
           '/admin/settlement/batches/search' resource_path,
           '按最多93天业务日期和主键游标查询结算批次' description
    UNION ALL SELECT 'admin_transaction_settlement_detail_v1', 'settlement:batch:detail',
           '结算批次详情', 'BUTTON', 'GET', '/admin/settlement/batches/*',
           '查询批次锁定汇率、聚合结果、唯一净入账和异步联动计数'
    UNION ALL SELECT 'admin_transaction_settlement_cancel_v1', 'settlement:batch:cancel',
           '取消未入账结算批次', 'BUTTON', 'POST', '/admin/settlement/batches/*/cancel',
           '通过状态和版本CAS取消未入账批次并释放全部候选'
) item
JOIN sys_menu menu ON menu.app_id = app.id
                  AND menu.menu_code = item.menu_code
                  AND menu.deleted = 0
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_permission exists_permission
      WHERE exists_permission.app_id = app.id
        AND exists_permission.permission_code = item.permission_code
        AND exists_permission.deleted = 0
  );

UPDATE sys_permission permission
JOIN sys_app app ON app.id = permission.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN (
    SELECT 'admin_transaction_settlement_v1' menu_code, 'settlement:batch:list' permission_code,
           '结算批次查询' permission_name, 'MENU' permission_type, 'POST' resource_method,
           '/admin/settlement/batches/search' resource_path,
           '按最多93天业务日期和主键游标查询结算批次' description
    UNION ALL SELECT 'admin_transaction_settlement_detail_v1', 'settlement:batch:detail',
           '结算批次详情', 'BUTTON', 'GET', '/admin/settlement/batches/*',
           '查询批次锁定汇率、聚合结果、唯一净入账和异步联动计数'
    UNION ALL SELECT 'admin_transaction_settlement_cancel_v1', 'settlement:batch:cancel',
           '取消未入账结算批次', 'BUTTON', 'POST', '/admin/settlement/batches/*/cancel',
           '通过状态和版本CAS取消未入账批次并释放全部候选'
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
WHERE permission.deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT role.app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = role.app_id AND menu.deleted = 0
WHERE role.role_code = 'SUPER_ADMIN'
  AND role.deleted = 0
  AND menu.menu_code IN (
      'admin_transaction_settlement_v1',
      'admin_transaction_settlement_detail_v1',
      'admin_transaction_settlement_cancel_v1'
  );

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT role.app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = role.app_id AND permission.deleted = 0
WHERE role.role_code = 'SUPER_ADMIN'
  AND role.deleted = 0
  AND permission.permission_code IN (
      'settlement:batch:list',
      'settlement:batch:detail',
      'settlement:batch:cancel'
  );

-- Direct reversal was replaced by the separate Maker-Checker reversal-order flow.
UPDATE sys_role_permission role_permission
JOIN sys_permission permission
  ON permission.app_id = role_permission.app_id
 AND permission.id = role_permission.permission_id
SET role_permission.deleted = role_permission.id
WHERE permission.permission_code = 'settlement:batch:reverse'
  AND permission.deleted = 0
  AND role_permission.deleted = 0;

UPDATE sys_permission
SET deleted = id, status = 0
WHERE permission_code = 'settlement:batch:reverse' AND deleted = 0;

UPDATE sys_role_menu role_menu
JOIN sys_menu menu
  ON menu.app_id = role_menu.app_id
 AND menu.id = role_menu.menu_id
SET role_menu.deleted = role_menu.id
WHERE menu.menu_code = 'admin_transaction_settlement_reverse_v1'
  AND menu.deleted = 0
  AND role_menu.deleted = 0;

UPDATE sys_menu
SET deleted = id, status = 0, visible = 0
WHERE menu_code = 'admin_transaction_settlement_reverse_v1' AND deleted = 0;

COMMIT;
