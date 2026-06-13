-- =============================================================================
-- 商户 OpenAPI 密钥管理权限与响应私钥字段修复
-- 说明：
-- 1. 增加 base_merchant_response_key.private_key_pkcs8_base64，用于高权限查看/下载商户响应解密私钥。
-- 2. 增加 merchant:key:manage 按钮权限，专门控制“查看密钥”页面。
-- 3. 本脚本为 MySQL 8 兼容写法，执行前会备份相关数据。
-- =============================================================================

START TRANSACTION;

DROP TABLE IF EXISTS bak_admin_merchant_key_20260613_sys_menu;
DROP TABLE IF EXISTS bak_admin_merchant_key_20260613_sys_role_menu;
DROP TABLE IF EXISTS bak_admin_merchant_key_20260613_response_key;

CREATE TABLE bak_admin_merchant_key_20260613_sys_menu AS
SELECT *
FROM sys_menu
WHERE app_id = 1
  AND (id = 388 OR permission_code = 'merchant:key:manage');

CREATE TABLE bak_admin_merchant_key_20260613_sys_role_menu AS
SELECT *
FROM sys_role_menu
WHERE app_id = 1
  AND menu_id = 388;

CREATE TABLE bak_admin_merchant_key_20260613_response_key AS
SELECT *
FROM base_merchant_response_key;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'base_merchant_response_key'
      AND column_name = 'private_key_pkcs8_base64'
);

SET @ddl := IF(
    @column_exists = 0,
    'ALTER TABLE base_merchant_response_key ADD COLUMN private_key_pkcs8_base64 TEXT NULL COMMENT ''商户响应解密私钥PKCS8 Base64，高权限可见'' AFTER public_key_x509_base64',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
                      permission_code, icon, redirect, visible, keep_alive, external_link, sort_no, status,
                      created_at, updated_at, deleted)
VALUES
    (388, 1, 23, 'merchant_key_manage', '商户密钥管理', 'BUTTON', NULL, NULL, 'merchant:key:manage', NULL, NULL, 0, 0, 0, 12, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_code = VALUES(menu_code),
    menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type),
    permission_code = VALUES(permission_code),
    visible = VALUES(visible),
    sort_no = VALUES(sort_no),
    status = VALUES(status),
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO sys_role_menu (app_id, role_id, menu_id, deleted)
VALUES (1, 1, 388, 0)
ON DUPLICATE KEY UPDATE deleted = 0;

COMMIT;
