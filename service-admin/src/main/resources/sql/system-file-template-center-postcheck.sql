-- 模板中心迁移后检查：expected_value 与 actual_value 必须相等。
SELECT 'template_tables' check_name, 3 expected_value, COUNT(*) actual_value
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('sys_file_template', 'sys_file_template_version', 'sys_file_template_asset');

SELECT 'immutability_triggers' check_name, 4 expected_value, COUNT(*) actual_value
FROM information_schema.triggers
WHERE trigger_schema = DATABASE()
  AND trigger_name IN (
      'trg_sys_file_template_stable_identity',
      'trg_sys_file_template_version_immutable',
      'trg_sys_file_template_asset_immutable_update',
      'trg_sys_file_template_asset_immutable_delete'
  );

SELECT 'admin_template_menu' check_name, 1 expected_value, COUNT(*) actual_value
FROM sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
WHERE menu.menu_code = 'admin_system_file_template_center_v1' AND menu.deleted = 0;

SELECT 'admin_template_menu_parent' check_name, 1 expected_value, COUNT(*) actual_value
FROM sys_menu menu
JOIN sys_menu parent ON parent.id = menu.parent_id
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
WHERE menu.menu_code = 'admin_system_file_template_center_v1' AND menu.deleted = 0
  AND parent.menu_code IN ('system_manage', 'admin_system_catalog_v3', 'admin_system_center')
  AND parent.menu_type = 'CATALOG' AND parent.status = 1 AND parent.deleted = 0;

SELECT 'admin_template_permissions' check_name, 8 expected_value, COUNT(*) actual_value
FROM sys_permission permission
JOIN sys_app app ON app.id = permission.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
WHERE permission.permission_code LIKE 'system:file-template:%' AND permission.deleted = 0;

SELECT 'merchant_template_permission' check_name, 1 expected_value, COUNT(*) actual_value
FROM sys_permission permission
JOIN sys_app app ON app.id = permission.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
WHERE permission.permission_code = 'merchant:file-template:download' AND permission.deleted = 0;

SELECT 'invalid_current_pointer' check_name, COUNT(*) issue_count
FROM sys_file_template template
LEFT JOIN sys_file_template_version version_row
       ON version_row.id = template.current_version_id AND version_row.template_id = template.id
WHERE template.current_version_id IS NOT NULL
  AND (version_row.id IS NULL OR version_row.publish_status <> 'PUBLISHED');

SELECT 'published_version_missing_locale' check_name, version_row.id, COUNT(DISTINCT asset.locale) locale_count
FROM sys_file_template_version version_row
LEFT JOIN sys_file_template_asset asset ON asset.template_version_id = version_row.id
WHERE version_row.publish_status = 'PUBLISHED'
GROUP BY version_row.id
HAVING COUNT(DISTINCT asset.locale) <> 2;

SELECT 'invalid_permission_scope' check_name, COUNT(*) issue_count
FROM sys_file_template
WHERE NOT (
    (owner_system = 'ADMIN' AND admin_permission_code IS NOT NULL AND merchant_permission_code IS NULL)
    OR (owner_system = 'MERCHANT' AND admin_permission_code IS NULL AND merchant_permission_code IS NOT NULL)
    OR (owner_system = 'SHARED' AND admin_permission_code IS NOT NULL AND merchant_permission_code IS NOT NULL)
);
