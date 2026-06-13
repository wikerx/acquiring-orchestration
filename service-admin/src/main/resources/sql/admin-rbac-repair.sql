-- Admin RBAC repair script.
-- Scope: admin app (app_id = 1). This script is idempotent for local/dev data repair.

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Remove historical test roles from active RBAC.
UPDATE sys_role
SET status = 0,
    deleted = id,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND deleted = 0
  AND role_code IN ('CODE_TEST', 'ROLE_TETST');

-- 2. Normalize catalog/menu metadata used by dynamic routes.
UPDATE sys_menu
SET permission_code = NULL,
    component_path = NULL,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND deleted = 0
  AND menu_type = 'CATALOG'
  AND menu_code IN ('system', 'monitor', 'merchant', 'base', 'permission', 'security');

-- 3. Ensure each visible menu permission_code exists in sys_permission.
INSERT INTO sys_permission (
    app_id,
    menu_id,
    permission_code,
    permission_name,
    permission_type,
    resource_method,
    resource_path,
    status,
    deleted
)
SELECT m.app_id,
       m.id,
       m.permission_code,
       CONCAT(m.menu_name, '查看'),
       'MENU',
       NULL,
       NULL,
       1,
       0
FROM sys_menu m
LEFT JOIN sys_permission p
       ON p.app_id = m.app_id
      AND p.permission_code = m.permission_code
      AND p.deleted = 0
WHERE m.app_id = 1
  AND m.deleted = 0
  AND m.visible = 1
  AND m.status = 1
  AND m.permission_code IS NOT NULL
  AND m.permission_code <> ''
  AND p.id IS NULL;

-- 4. Ensure system monitor API/button permissions exist.
INSERT INTO sys_permission (
    app_id,
    menu_id,
    permission_code,
    permission_name,
    permission_type,
    resource_method,
    resource_path,
    status,
    deleted
)
SELECT 1, m.id, 'monitor:online:forceLogout', '在线用户强退', 'BUTTON', 'DELETE', '/admin/monitor/online/*', 1, 0
FROM sys_menu m
WHERE m.app_id = 1 AND m.menu_code = 'monitor_online' AND m.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission p
      WHERE p.app_id = 1 AND p.permission_code = 'monitor:online:forceLogout' AND p.deleted = 0
  );

INSERT INTO sys_permission (
    app_id,
    menu_id,
    permission_code,
    permission_name,
    permission_type,
    resource_method,
    resource_path,
    status,
    deleted
)
SELECT 1, m.id, 'monitor:cache:delete', '缓存删除', 'BUTTON', 'DELETE', '/admin/monitor/cache/**', 1, 0
FROM sys_menu m
WHERE m.app_id = 1 AND m.menu_code = 'monitor_cache' AND m.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission p
      WHERE p.app_id = 1 AND p.permission_code = 'monitor:cache:delete' AND p.deleted = 0
  );

-- 5. Align known monitor permission resource metadata.
UPDATE sys_permission p
JOIN sys_menu m ON m.id = p.menu_id AND m.app_id = p.app_id AND m.deleted = 0
SET p.resource_method = CASE p.permission_code
        WHEN 'monitor:online:list' THEN 'GET'
        WHEN 'monitor:server:list' THEN 'GET'
        WHEN 'monitor:cache:list' THEN 'GET'
        WHEN 'monitor:online:forceLogout' THEN 'DELETE'
        WHEN 'monitor:cache:delete' THEN 'DELETE'
        ELSE p.resource_method
    END,
    p.resource_path = CASE p.permission_code
        WHEN 'monitor:online:list' THEN '/admin/monitor/online/list'
        WHEN 'monitor:server:list' THEN '/admin/monitor/server'
        WHEN 'monitor:cache:list' THEN '/admin/monitor/cache/**'
        WHEN 'monitor:online:forceLogout' THEN '/admin/monitor/online/*'
        WHEN 'monitor:cache:delete' THEN '/admin/monitor/cache/**'
        ELSE p.resource_path
    END,
    p.status = 1
WHERE p.app_id = 1
  AND p.deleted = 0
  AND p.permission_code IN (
      'monitor:online:list',
      'monitor:server:list',
      'monitor:cache:list',
      'monitor:online:forceLogout',
      'monitor:cache:delete'
  );

-- 6. Ensure the admin operator role owns all active admin menus and permissions.
INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT r.app_id, r.id, m.id, 0
FROM sys_role r
JOIN sys_menu m ON m.app_id = r.app_id
WHERE r.app_id = 1
  AND r.role_code = 'ADMIN_OPERATOR'
  AND r.deleted = 0
  AND r.status = 1
  AND m.deleted = 0
  AND m.visible = 1
  AND m.status = 1;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT r.app_id, r.id, p.id, 0
FROM sys_role r
JOIN sys_permission p ON p.app_id = r.app_id
WHERE r.app_id = 1
  AND r.role_code = 'ADMIN_OPERATOR'
  AND r.deleted = 0
  AND r.status = 1
  AND p.deleted = 0
  AND p.status = 1;

-- 7. Keep sys_user_role in sync with the actual login-account role relation.
INSERT IGNORE INTO sys_user_role (app_id, user_id, role_id, deleted)
SELECT ar.app_id, a.user_id, ar.role_id, 0
FROM sys_account_role ar
JOIN sys_account a ON a.id = ar.account_id
                  AND a.app_id = ar.app_id
                  AND a.deleted = 0
WHERE ar.app_id = 1
  AND ar.deleted = 0;

SET FOREIGN_KEY_CHECKS = 1;
