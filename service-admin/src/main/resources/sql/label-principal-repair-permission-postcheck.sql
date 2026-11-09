-- 未结算历史标签本金修复权限后检；每个检查结果都必须返回 0。
SET @admin_app_id = (
    SELECT MIN(id)
    FROM sys_app
    WHERE app_code = 'ADMIN' AND deleted = 0
);

-- 1. 菜单按钮、权限资源和父菜单绑定缺失数。
SELECT COUNT(*) AS missing_permission_contract_count
FROM (
    SELECT 'admin_transaction_label_principal_repair_submit_v1' AS menu_code,
           'clearing:principal-repair:submit' AS permission_code,
           '/admin/clearing/principal-repairs' AS resource_path
    UNION ALL
    SELECT 'admin_transaction_label_principal_repair_review_v1',
           'clearing:principal-repair:review',
           '/admin/clearing/principal-repairs/*/review'
) expected
LEFT JOIN sys_menu menu
       ON menu.app_id = @admin_app_id
      AND menu.menu_code = expected.menu_code
      AND menu.menu_type = 'BUTTON'
      AND menu.visible = 0
      AND menu.status = 1
      AND menu.deleted = 0
LEFT JOIN sys_menu parent
       ON parent.app_id = menu.app_id
      AND parent.id = menu.parent_id
      AND parent.menu_code = 'admin_transaction_clearing_v1'
      AND parent.deleted = 0
LEFT JOIN sys_permission permission
       ON permission.app_id = @admin_app_id
      AND permission.menu_id = menu.id
      AND permission.permission_code = expected.permission_code
      AND permission.permission_type = 'BUTTON'
      AND permission.resource_method = 'POST'
      AND permission.resource_path = expected.resource_path
      AND permission.status = 1
      AND permission.deleted = 0
WHERE menu.id IS NULL OR parent.id IS NULL OR permission.id IS NULL;

-- 2. SUPER_ADMIN 的按钮与资源授权缺失数。
SELECT COUNT(*) AS missing_super_admin_grant_count
FROM sys_role role
CROSS JOIN (
    SELECT 'admin_transaction_label_principal_repair_submit_v1' AS menu_code,
           'clearing:principal-repair:submit' AS permission_code
    UNION ALL
    SELECT 'admin_transaction_label_principal_repair_review_v1',
           'clearing:principal-repair:review'
) expected
LEFT JOIN sys_menu menu
       ON menu.app_id = role.app_id
      AND menu.menu_code = expected.menu_code
      AND menu.deleted = 0
LEFT JOIN sys_permission permission
       ON permission.app_id = role.app_id
      AND permission.permission_code = expected.permission_code
      AND permission.deleted = 0
LEFT JOIN sys_role_menu role_menu
       ON role_menu.app_id = role.app_id
      AND role_menu.role_id = role.id
      AND role_menu.menu_id = menu.id
      AND role_menu.deleted = 0
LEFT JOIN sys_role_permission role_permission
       ON role_permission.app_id = role.app_id
      AND role_permission.role_id = role.id
      AND role_permission.permission_id = permission.id
      AND role_permission.deleted = 0
WHERE role.app_id = @admin_app_id
  AND role.role_code = 'SUPER_ADMIN'
  AND role.status = 1
  AND role.deleted = 0
  AND (role_menu.id IS NULL OR role_permission.id IS NULL);

-- 3. 非 SUPER_ADMIN 活动角色被误授权数。
SELECT COUNT(*) AS unauthorized_role_grant_count
FROM (
    SELECT role.id AS role_id, permission.permission_code AS granted_code
    FROM sys_role role
    JOIN sys_role_permission role_permission
      ON role_permission.app_id = role.app_id
     AND role_permission.role_id = role.id
     AND role_permission.deleted = 0
    JOIN sys_permission permission
      ON permission.app_id = role_permission.app_id
     AND permission.id = role_permission.permission_id
     AND permission.permission_code IN (
         'clearing:principal-repair:submit',
         'clearing:principal-repair:review'
     )
     AND permission.deleted = 0
    WHERE role.app_id = @admin_app_id
      AND role.role_code <> 'SUPER_ADMIN'
      AND role.status = 1
      AND role.deleted = 0
    UNION ALL
    SELECT role.id, menu.menu_code
    FROM sys_role role
    JOIN sys_role_menu role_menu
      ON role_menu.app_id = role.app_id
     AND role_menu.role_id = role.id
     AND role_menu.deleted = 0
    JOIN sys_menu menu
      ON menu.app_id = role_menu.app_id
     AND menu.id = role_menu.menu_id
     AND menu.menu_code IN (
         'admin_transaction_label_principal_repair_submit_v1',
         'admin_transaction_label_principal_repair_review_v1'
     )
     AND menu.deleted = 0
    WHERE role.app_id = @admin_app_id
      AND role.role_code <> 'SUPER_ADMIN'
      AND role.status = 1
      AND role.deleted = 0
) unauthorized_grant;
