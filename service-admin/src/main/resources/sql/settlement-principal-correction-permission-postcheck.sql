-- 已入账历史标签本金差额调整权限后检；每个检查结果必须为0。
SET @admin_app_id = (
    SELECT MIN(id) FROM sys_app WHERE app_code = 'ADMIN' AND deleted = 0
);

-- 1. 三项隐藏按钮、资源和父菜单绑定。
SELECT COUNT(*) AS missing_principal_correction_permission_count
FROM (
    SELECT 'admin_settlement_principal_correction_submit_v1' AS menu_code,
           'settlement:principal-correction:submit' AS permission_code,
           '/admin/settlement/principal-corrections' AS resource_path
    UNION ALL
    SELECT 'admin_settlement_principal_correction_approve_v1',
           'settlement:principal-correction:approve',
           '/admin/settlement/principal-corrections/*/approve'
    UNION ALL
    SELECT 'admin_settlement_principal_correction_reject_v1',
           'settlement:principal-correction:reject',
           '/admin/settlement/principal-corrections/*/reject'
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
 AND parent.menu_code = 'admin_settlement_reversal_order_v1'
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

-- 2. SUPER_ADMIN 授权完整。
SELECT COUNT(*) AS missing_principal_correction_super_admin_grant_count
FROM sys_role role
CROSS JOIN (
    SELECT 'admin_settlement_principal_correction_submit_v1' AS menu_code,
           'settlement:principal-correction:submit' AS permission_code
    UNION ALL
    SELECT 'admin_settlement_principal_correction_approve_v1',
           'settlement:principal-correction:approve'
    UNION ALL
    SELECT 'admin_settlement_principal_correction_reject_v1',
           'settlement:principal-correction:reject'
) expected
LEFT JOIN sys_menu menu
  ON menu.app_id = role.app_id AND menu.menu_code = expected.menu_code AND menu.deleted = 0
LEFT JOIN sys_permission permission
  ON permission.app_id = role.app_id
 AND permission.permission_code = expected.permission_code
 AND permission.deleted = 0
LEFT JOIN sys_role_menu role_menu
  ON role_menu.app_id = role.app_id AND role_menu.role_id = role.id
 AND role_menu.menu_id = menu.id AND role_menu.deleted = 0
LEFT JOIN sys_role_permission role_permission
  ON role_permission.app_id = role.app_id AND role_permission.role_id = role.id
 AND role_permission.permission_id = permission.id AND role_permission.deleted = 0
WHERE role.app_id = @admin_app_id
  AND role.role_code = 'SUPER_ADMIN'
  AND role.status = 1
  AND role.deleted = 0
  AND (role_menu.id IS NULL OR role_permission.id IS NULL);

-- 3. 其他活动角色不得被脚本自动授权。
SELECT COUNT(*) AS unauthorized_principal_correction_role_grant_count
FROM sys_role role
JOIN sys_role_permission role_permission
  ON role_permission.app_id = role.app_id
 AND role_permission.role_id = role.id
 AND role_permission.deleted = 0
JOIN sys_permission permission
  ON permission.app_id = role_permission.app_id
 AND permission.id = role_permission.permission_id
 AND permission.permission_code IN (
     'settlement:principal-correction:submit',
     'settlement:principal-correction:approve',
     'settlement:principal-correction:reject'
 )
 AND permission.deleted = 0
WHERE role.app_id = @admin_app_id
  AND role.role_code <> 'SUPER_ADMIN'
  AND role.status = 1
  AND role.deleted = 0;
