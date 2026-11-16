-- 未结算历史标签本金修复独立高风险权限；只授予 SUPER_ADMIN。
-- 本脚本不创建前端入口，也不修改任何清分、候选或结算业务数据。
SET NAMES utf8mb4;
START TRANSACTION;

SET @admin_app_id = (
    SELECT MIN(id)
    FROM sys_app
    WHERE app_code = 'ADMIN' AND deleted = 0
);

SET @clearing_menu_id = (
    SELECT MIN(id)
    FROM sys_menu
    WHERE app_id = @admin_app_id
      AND menu_code = 'admin_transaction_clearing_v1'
      AND status = 1
      AND deleted = 0
);

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT @admin_app_id, @clearing_menu_id, item.menu_code, item.menu_name, 'BUTTON', NULL, NULL,
       item.permission_code, NULL, 0, 0, 0, item.sort_no, 1, 0
FROM (
    SELECT 'admin_transaction_label_principal_repair_submit_v1' AS menu_code,
           '提交历史标签本金修复' AS menu_name,
           'clearing:principal-repair:submit' AS permission_code, 9 AS sort_no
    UNION ALL
    SELECT 'admin_transaction_label_principal_repair_review_v1',
           '复核历史标签本金修复', 'clearing:principal-repair:review', 10
) item
WHERE @admin_app_id IS NOT NULL
  AND @clearing_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu existing
      WHERE existing.app_id = @admin_app_id
        AND existing.menu_code = item.menu_code
        AND existing.deleted = 0
  );

UPDATE sys_menu menu
JOIN (
    SELECT 'admin_transaction_label_principal_repair_submit_v1' AS menu_code,
           '提交历史标签本金修复' AS menu_name,
           'clearing:principal-repair:submit' AS permission_code, 9 AS sort_no
    UNION ALL
    SELECT 'admin_transaction_label_principal_repair_review_v1',
           '复核历史标签本金修复', 'clearing:principal-repair:review', 10
) item ON item.menu_code = menu.menu_code
SET menu.parent_id = @clearing_menu_id,
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
WHERE menu.app_id = @admin_app_id
  AND menu.deleted = 0;

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT @admin_app_id, menu.id, item.permission_code, item.permission_name, 'BUTTON',
       'POST', item.resource_path, item.description, 1, 0
FROM (
    SELECT 'admin_transaction_label_principal_repair_submit_v1' AS menu_code,
           'clearing:principal-repair:submit' AS permission_code,
           '提交历史标签本金修复' AS permission_name,
           '/admin/clearing/principal-repairs' AS resource_path,
           '冻结未结算异常本金、清分修订和候选版本并提交双人复核' AS description
    UNION ALL
    SELECT 'admin_transaction_label_principal_repair_review_v1',
           'clearing:principal-repair:review', '复核历史标签本金修复',
           '/admin/clearing/principal-repairs/*/review',
           '由不同全商户数据范围操作人批准或拒绝历史标签本金修复'
) item
JOIN sys_menu menu
  ON menu.app_id = @admin_app_id
 AND menu.menu_code = item.menu_code
 AND menu.deleted = 0
WHERE @admin_app_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_permission existing
      WHERE existing.app_id = @admin_app_id
        AND existing.permission_code = item.permission_code
        AND existing.deleted = 0
  );

UPDATE sys_permission permission
JOIN (
    SELECT 'admin_transaction_label_principal_repair_submit_v1' AS menu_code,
           'clearing:principal-repair:submit' AS permission_code,
           '提交历史标签本金修复' AS permission_name,
           '/admin/clearing/principal-repairs' AS resource_path,
           '冻结未结算异常本金、清分修订和候选版本并提交双人复核' AS description
    UNION ALL
    SELECT 'admin_transaction_label_principal_repair_review_v1',
           'clearing:principal-repair:review', '复核历史标签本金修复',
           '/admin/clearing/principal-repairs/*/review',
           '由不同全商户数据范围操作人批准或拒绝历史标签本金修复'
) item ON item.permission_code = permission.permission_code
JOIN sys_menu menu
  ON menu.app_id = permission.app_id
 AND menu.menu_code = item.menu_code
 AND menu.deleted = 0
SET permission.menu_id = menu.id,
    permission.permission_name = BINARY item.permission_name,
    permission.permission_type = 'BUTTON',
    permission.resource_method = 'POST',
    permission.resource_path = item.resource_path,
    permission.description = BINARY item.description,
    permission.status = 1,
    permission.updated_at = CURRENT_TIMESTAMP(3)
WHERE permission.app_id = @admin_app_id
  AND permission.deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT @admin_app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_menu menu
  ON menu.app_id = role.app_id
 AND menu.menu_code IN (
     'admin_transaction_label_principal_repair_submit_v1',
     'admin_transaction_label_principal_repair_review_v1'
 )
 AND menu.deleted = 0
WHERE role.app_id = @admin_app_id
  AND role.role_code = 'SUPER_ADMIN'
  AND role.status = 1
  AND role.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT @admin_app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_permission permission
  ON permission.app_id = role.app_id
 AND permission.permission_code IN (
     'clearing:principal-repair:submit',
     'clearing:principal-repair:review'
 )
 AND permission.deleted = 0
WHERE role.app_id = @admin_app_id
  AND role.role_code = 'SUPER_ADMIN'
  AND role.status = 1
  AND role.deleted = 0;

COMMIT;
