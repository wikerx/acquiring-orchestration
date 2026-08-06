-- 商户端“交易配置管理”菜单与来源网址、IP 白名单细粒度权限迁移。
-- 本脚本只调整 RBAC 元数据，不修改交易配置业务数据；可重复执行。

SET NAMES utf8mb4;

START TRANSACTION;

UPDATE sys_menu
SET menu_name = '交易配置管理', status = 1
WHERE app_id = 2
  AND menu_code = 'merchant_access_config_catalog_v1'
  AND deleted = 0;

UPDATE sys_menu
SET menu_name = '商户来源网址',
    permission_code = 'merchant:access-config:source-url:list',
    status = 1
WHERE app_id = 2
  AND menu_code = 'merchant_source_url_v1'
  AND deleted = 0;

UPDATE sys_menu
SET menu_name = '商户IP白名单',
    permission_code = 'merchant:access-config:ip-whitelist:list',
    status = 1
WHERE app_id = 2
  AND menu_code = 'merchant_ip_whitelist_v1'
  AND deleted = 0;

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, status, deleted
)
SELECT 2, menu.id, item.permission_code, item.permission_name, item.permission_type,
       item.resource_method, item.resource_path, 1, 0
FROM (
    SELECT 'merchant_source_url_v1' menu_code,
           'merchant:access-config:source-url:list' permission_code,
           '商户来源网址查询' permission_name, 'MENU' permission_type,
           'GET' resource_method, '/merchant/access-config/source-urls' resource_path
    UNION ALL SELECT 'merchant_source_url_v1', 'merchant:access-config:source-url:detail',
           '商户来源网址详情', 'BUTTON', 'GET', '/merchant/access-config/source-urls'
    UNION ALL SELECT 'merchant_source_url_v1', 'merchant:access-config:source-url:submit',
           '商户来源网址提交', 'BUTTON', 'POST', '/merchant/access-config/source-urls'
    UNION ALL SELECT 'merchant_ip_whitelist_v1', 'merchant:access-config:ip-whitelist:list',
           '商户IP白名单查询', 'MENU', 'GET', '/merchant/access-config/ip-whitelists'
    UNION ALL SELECT 'merchant_ip_whitelist_v1', 'merchant:access-config:ip-whitelist:detail',
           '商户IP白名单详情', 'BUTTON', 'GET', '/merchant/access-config/ip-whitelists'
    UNION ALL SELECT 'merchant_ip_whitelist_v1', 'merchant:access-config:ip-whitelist:submit',
           '商户IP白名单提交', 'BUTTON', 'POST', '/merchant/access-config/ip-whitelists'
) item
JOIN sys_menu menu
  ON menu.app_id = 2
 AND menu.menu_code = item.menu_code
 AND menu.deleted = 0
ON DUPLICATE KEY UPDATE
    menu_id = VALUES(menu_id),
    permission_name = VALUES(permission_name),
    permission_type = VALUES(permission_type),
    resource_method = VALUES(resource_method),
    resource_path = VALUES(resource_path),
    status = 1,
    deleted = 0;

-- 先继承旧权限的角色授权，避免自定义角色升级后失去原有能力。
INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 2, old_grant.role_id, new_permission.id, 0
FROM sys_role_permission old_grant
JOIN sys_permission old_permission
  ON old_permission.id = old_grant.permission_id
 AND old_permission.app_id = old_grant.app_id
 AND old_permission.deleted = 0
JOIN sys_permission new_permission
  ON new_permission.app_id = old_permission.app_id
 AND new_permission.deleted = 0
 AND (
      (old_permission.permission_code = 'merchant:access-config:view'
       AND new_permission.permission_code IN (
           'merchant:access-config:source-url:list',
           'merchant:access-config:source-url:detail',
           'merchant:access-config:ip-whitelist:list',
           'merchant:access-config:ip-whitelist:detail'
       ))
      OR
      (old_permission.permission_code = 'merchant:access-config:submit'
       AND new_permission.permission_code IN (
           'merchant:access-config:source-url:submit',
           'merchant:access-config:ip-whitelist:submit'
       ))
 )
WHERE old_grant.app_id = 2
  AND old_grant.deleted = 0;

-- 内置商户角色保持原有默认能力：全部角色可查详情，管理员和操作员可提交。
INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 2, role.id, permission.id, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id AND permission.deleted = 0
WHERE role.app_id = 2
  AND role.deleted = 0
  AND role.role_code LIKE 'MERCHANT\_%'
  AND permission.permission_code IN (
      'merchant:access-config:source-url:list',
      'merchant:access-config:source-url:detail',
      'merchant:access-config:ip-whitelist:list',
      'merchant:access-config:ip-whitelist:detail'
  );

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 2, role.id, permission.id, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id AND permission.deleted = 0
WHERE role.app_id = 2
  AND role.deleted = 0
  AND (role.role_code LIKE 'MERCHANT_ADMIN\_%' OR role.role_code LIKE 'MERCHANT_OPERATOR\_%')
  AND permission.permission_code IN (
      'merchant:access-config:source-url:submit',
      'merchant:access-config:ip-whitelist:submit'
  );

UPDATE sys_role_permission role_permission
JOIN sys_permission permission
  ON permission.id = role_permission.permission_id
 AND permission.app_id = role_permission.app_id
SET role_permission.deleted = role_permission.id
WHERE role_permission.app_id = 2
  AND role_permission.deleted = 0
  AND permission.deleted = 0
  AND permission.permission_code IN (
      'merchant:access-config:view',
      'merchant:access-config:submit'
  );

UPDATE sys_permission
SET status = 0, deleted = id
WHERE app_id = 2
  AND deleted = 0
  AND permission_code IN (
      'merchant:access-config:view',
      'merchant:access-config:submit'
  );

COMMIT;

SELECT menu_code, menu_name, permission_code, status
FROM sys_menu
WHERE app_id = 2
  AND menu_code IN (
      'merchant_access_config_catalog_v1',
      'merchant_source_url_v1',
      'merchant_ip_whitelist_v1'
  )
  AND deleted = 0
ORDER BY sort_no, id;

SELECT permission_code, permission_name, permission_type, resource_method, resource_path, status
FROM sys_permission
WHERE app_id = 2
  AND permission_code LIKE 'merchant:access-config:%'
  AND deleted = 0
ORDER BY permission_code;
