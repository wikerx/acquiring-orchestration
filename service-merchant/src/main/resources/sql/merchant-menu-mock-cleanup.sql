SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 商户系统菜单物理清理脚本：仅处理 MERCHANT app 下未接真实功能的菜单和权限。
-- 执行前请确认当前库 sys_app 中 MERCHANT 的 app_id 正确；本脚本不会匹配 ADMIN app。
CREATE TEMPORARY TABLE tmp_merchant_mock_menu_ids AS
SELECT menu.id
FROM sys_menu menu
JOIN sys_app app ON app.id = menu.app_id
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND (
      menu.menu_code IN (
          'merchant_dashboard',
          'merchant_transaction',
          'merchant_settlement',
          'merchant_account',
          'merchant_info',
          'merchant_store',
          'merchant_order',
          'merchant_refund',
          'merchant_api_key',
          'merchant_oper_log'
      )
      OR menu.menu_code LIKE 'merchant_store_%'
      OR menu.menu_code LIKE 'merchant_transaction_%'
      OR menu.menu_code LIKE 'merchant_order_%'
      OR menu.menu_code LIKE 'merchant_refund_%'
      OR menu.menu_code LIKE 'merchant_settlement_%'
      OR menu.menu_code LIKE 'merchant_account_%'
      OR menu.menu_code LIKE 'merchant_api_key_%'
      OR menu.menu_code LIKE 'merchant_oper_log_%'
  );

CREATE TEMPORARY TABLE tmp_merchant_mock_permission_ids AS
SELECT permission.id
FROM sys_permission permission
JOIN sys_app app ON app.id = permission.app_id
LEFT JOIN tmp_merchant_mock_menu_ids mock_menu ON mock_menu.id = permission.menu_id
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND (
      mock_menu.id IS NOT NULL
      OR permission.permission_code IN (
          'merchant:dashboard:view',
          'merchant:info:view',
          'merchant:store:view',
          'merchant:store:manage',
          'merchant:transaction:view',
          'merchant:order:view',
          'merchant:refund:apply',
          'merchant:settlement:view',
          'merchant:account:view',
          'merchant:oper-log:view',
          'merchant:api-key:view',
          'merchant:api-key:manage'
      )
      OR permission.permission_code LIKE 'merchant:store:%'
      OR permission.permission_code LIKE 'merchant:info:%'
      OR permission.permission_code LIKE 'merchant:transaction:%'
      OR permission.permission_code LIKE 'merchant:order:%'
      OR permission.permission_code LIKE 'merchant:refund:%'
      OR permission.permission_code LIKE 'merchant:settlement:%'
      OR permission.permission_code LIKE 'merchant:account:%'
      OR permission.permission_code LIKE 'merchant:api-key:%'
      OR permission.permission_code LIKE 'merchant:oper-log:%'
  );

DELETE grant_menu
FROM sys_merchant_menu_grant grant_menu
JOIN sys_app app ON app.id = grant_menu.app_id
JOIN tmp_merchant_mock_menu_ids mock_menu ON mock_menu.id = grant_menu.menu_id
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0;

DELETE grant_permission
FROM sys_merchant_permission_grant grant_permission
JOIN sys_app app ON app.id = grant_permission.app_id
JOIN tmp_merchant_mock_permission_ids mock_permission ON mock_permission.id = grant_permission.permission_id
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0;

DELETE role_menu
FROM sys_role_menu role_menu
JOIN sys_app app ON app.id = role_menu.app_id
JOIN tmp_merchant_mock_menu_ids mock_menu ON mock_menu.id = role_menu.menu_id
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0;

DELETE role_permission
FROM sys_role_permission role_permission
JOIN sys_app app ON app.id = role_permission.app_id
JOIN tmp_merchant_mock_permission_ids mock_permission ON mock_permission.id = role_permission.permission_id
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0;

DELETE permission
FROM sys_permission permission
JOIN sys_app app ON app.id = permission.app_id
JOIN tmp_merchant_mock_permission_ids mock_permission ON mock_permission.id = permission.id
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0;

DELETE menu
FROM sys_menu menu
JOIN sys_app app ON app.id = menu.app_id
JOIN tmp_merchant_mock_menu_ids mock_menu ON mock_menu.id = menu.id
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0;

DROP TEMPORARY TABLE IF EXISTS tmp_merchant_mock_permission_ids;
DROP TEMPORARY TABLE IF EXISTS tmp_merchant_mock_menu_ids;

-- 执行后校验：以下 SQL 应返回 0 行。若有结果，说明仍存在商户端 mock 菜单或权限残留。
SELECT menu.id, menu.menu_code, menu.route_path, menu.permission_code
FROM sys_menu menu
JOIN sys_app app ON app.id = menu.app_id
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND (
      menu.menu_code IN (
          'merchant_dashboard',
          'merchant_transaction',
          'merchant_settlement',
          'merchant_account',
          'merchant_info',
          'merchant_store',
          'merchant_order',
          'merchant_refund',
          'merchant_api_key',
          'merchant_oper_log'
      )
      OR menu.menu_code LIKE 'merchant_store_%'
      OR menu.menu_code LIKE 'merchant_transaction_%'
      OR menu.menu_code LIKE 'merchant_order_%'
      OR menu.menu_code LIKE 'merchant_refund_%'
      OR menu.menu_code LIKE 'merchant_settlement_%'
      OR menu.menu_code LIKE 'merchant_account_%'
      OR menu.menu_code LIKE 'merchant_api_key_%'
      OR menu.menu_code LIKE 'merchant_oper_log_%'
  );

SELECT permission.id, permission.permission_code, permission.resource_path
FROM sys_permission permission
JOIN sys_app app ON app.id = permission.app_id
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND (
      permission.permission_code IN (
          'merchant:dashboard:view',
          'merchant:info:view',
          'merchant:store:view',
          'merchant:store:manage',
          'merchant:transaction:view',
          'merchant:order:view',
          'merchant:refund:apply',
          'merchant:settlement:view',
          'merchant:account:view',
          'merchant:oper-log:view',
          'merchant:api-key:view',
          'merchant:api-key:manage'
      )
      OR permission.permission_code LIKE 'merchant:store:%'
      OR permission.permission_code LIKE 'merchant:info:%'
      OR permission.permission_code LIKE 'merchant:transaction:%'
      OR permission.permission_code LIKE 'merchant:order:%'
      OR permission.permission_code LIKE 'merchant:refund:%'
      OR permission.permission_code LIKE 'merchant:settlement:%'
      OR permission.permission_code LIKE 'merchant:account:%'
      OR permission.permission_code LIKE 'merchant:api-key:%'
      OR permission.permission_code LIKE 'merchant:oper-log:%'
  );

SET FOREIGN_KEY_CHECKS = 1;
