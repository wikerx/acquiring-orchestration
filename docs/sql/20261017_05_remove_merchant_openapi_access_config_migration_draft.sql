-- 商户 OpenAPI 旧准入开关清理迁移草案（MySQL 8.4，可重复执行）。
-- 仅供评审，未由 Codex 执行。执行前必须完成备份、权限影响和回滚方案评审。
-- 审核通过、启用且未删除的 merchant_ip_whitelist 记录是商户 API 唯一接入白名单。

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `payment_acquiring`;

DELETE role_permission
FROM sys_role_permission role_permission
JOIN sys_permission permission
  ON permission.id = role_permission.permission_id
 AND permission.app_id = role_permission.app_id
WHERE permission.app_id = 1
  AND permission.permission_code = 'merchant:ip-whitelist:config';

DELETE role_menu
FROM sys_role_menu role_menu
JOIN sys_menu menu
  ON menu.id = role_menu.menu_id
 AND menu.app_id = role_menu.app_id
WHERE menu.app_id = 1
  AND menu.menu_code = 'merchant_ip_whitelist_config_v1';

DELETE FROM sys_permission
WHERE app_id = 1
  AND permission_code = 'merchant:ip-whitelist:config';

DELETE FROM sys_menu
WHERE app_id = 1
  AND menu_code = 'merchant_ip_whitelist_config_v1';

DROP TABLE IF EXISTS merchant_openapi_access_config;

-- 后检：旧权限、菜单和配置表必须全部不存在。
SELECT COUNT(*) AS remaining_permission_count
FROM sys_permission
WHERE app_id = 1
  AND permission_code = 'merchant:ip-whitelist:config';

SELECT COUNT(*) AS remaining_menu_count
FROM sys_menu
WHERE app_id = 1
  AND menu_code = 'merchant_ip_whitelist_config_v1';

SELECT COUNT(*) AS remaining_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'merchant_openapi_access_config';
