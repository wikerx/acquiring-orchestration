-- =============================================================================
-- 商户管理 API 权限补齐
-- 说明：sys_menu 控制菜单/按钮，sys_permission + sys_role_permission 控制运行时 API 权限。
-- =============================================================================

START TRANSACTION;

DROP TABLE IF EXISTS bak_admin_merchant_api_perm_20260613_sys_permission;
DROP TABLE IF EXISTS bak_admin_merchant_api_perm_20260613_sys_role_permission;

CREATE TABLE bak_admin_merchant_api_perm_20260613_sys_permission AS
SELECT *
FROM sys_permission
WHERE app_id = 1
  AND permission_code LIKE 'merchant:%';

CREATE TABLE bak_admin_merchant_api_perm_20260613_sys_role_permission AS
SELECT rp.*
FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.app_id = 1
  AND p.permission_code LIKE 'merchant:%';

INSERT INTO sys_permission (app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
VALUES
    (1, 23, 'merchant:info:list', '商户分页查询', 'API', 'POST', '/admin/merchants/search', 1, 0),
    (1, 23, 'merchant:info:query', '商户详情查询', 'API', 'GET', '/admin/merchants/*', 1, 0),
    (1, 23, 'merchant:info:add', '商户新增', 'API', 'POST', '/admin/merchants', 1, 0),
    (1, 23, 'merchant:info:edit', '商户修改', 'API', 'PUT', '/admin/merchants/*', 1, 0),
    (1, 23, 'merchant:info:changeStatus', '商户状态修改', 'API', 'PUT', '/admin/merchants/*/status', 1, 0),
    (1, 23, 'merchant:material:view', '商户对接材料生成', 'API', 'POST', '/admin/merchants/*/security-material/provision', 1, 0),
    (1, 23, 'merchant:key:manage', '商户密钥查看管理', 'API', 'GET', '/admin/merchants/*/keys', 1, 0),
    (1, 23, 'merchant:key:rotate', '商户JWT密钥轮换', 'API', 'POST', '/admin/merchants/*/jwt-key/rotate', 1, 0),
    (1, 23, 'merchant:platform-payload-key:rotate', '平台请求体密钥轮换', 'API', 'POST', '/admin/merchants/*/platform-payload-key/rotate', 1, 0),
    (1, 23, 'merchant:response-key:update', '商户响应密钥更新', 'API', 'PUT', '/admin/merchants/*/response-key', 1, 0)
ON DUPLICATE KEY UPDATE
    menu_id = VALUES(menu_id),
    permission_name = VALUES(permission_name),
    permission_type = VALUES(permission_type),
    resource_method = VALUES(resource_method),
    resource_path = VALUES(resource_path),
    status = VALUES(status),
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 1, 1, id, 0
FROM sys_permission
WHERE app_id = 1
  AND permission_code IN (
      'merchant:info:list',
      'merchant:info:query',
      'merchant:info:add',
      'merchant:info:edit',
      'merchant:info:changeStatus',
      'merchant:material:view',
      'merchant:key:manage',
      'merchant:key:rotate',
      'merchant:platform-payload-key:rotate',
      'merchant:response-key:update'
  )
  AND deleted = 0;

COMMIT;
