INSERT INTO sys_permission (app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
SELECT 1, menu.id, 'channel:limit:dimensionEdit', '渠道限额维度编辑', 'BUTTON', 'PUT', '/admin/channel/limits/dimension', 1, 0
FROM sys_menu menu
WHERE menu.app_id = 1
  AND menu.menu_code = 'admin_channel_limit_v1'
  AND menu.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_permission exists_permission
      WHERE exists_permission.app_id = 1
        AND exists_permission.permission_code = 'channel:limit:dimensionEdit'
        AND exists_permission.deleted = 0
  );

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, parent.id, 'admin_channel_limit_dimension_edit_v1', '渠道限额维度编辑', 'BUTTON', NULL, NULL, 'channel:limit:dimensionEdit', NULL, 0, 4, 1, 0
FROM sys_menu parent
WHERE parent.app_id = 1
  AND parent.menu_code = 'admin_channel_limit_v1'
  AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu exists_menu
      WHERE exists_menu.app_id = 1
        AND exists_menu.menu_code = 'admin_channel_limit_dimension_edit_v1'
        AND exists_menu.deleted = 0
  );

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 1, role.id, menu.id, 0
FROM sys_role role
JOIN sys_menu menu ON menu.app_id = role.app_id
WHERE role.app_id = 1
  AND role.role_code IN ('ADMIN_OPERATOR', 'ADMIN')
  AND role.deleted = 0
  AND menu.menu_code = 'admin_channel_limit_dimension_edit_v1'
  AND menu.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 1, role.id, permission.id, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id
WHERE role.app_id = 1
  AND role.role_code IN ('ADMIN_OPERATOR', 'ADMIN')
  AND role.deleted = 0
  AND permission.permission_code = 'channel:limit:dimensionEdit'
  AND permission.deleted = 0;
