-- =============================================================================
-- 商户管理菜单与权限修复脚本
-- 说明：基于 2026-06-13 本地 payment_acquiring 真实菜单 ID。
-- 真实 ID：
--   商户管理=22，商户信息管理=23
--   按钮权限=377-387
-- =============================================================================

START TRANSACTION;

DROP TABLE IF EXISTS bak_admin_merchant_menu_20260613_sys_menu;
DROP TABLE IF EXISTS bak_admin_merchant_menu_20260613_sys_role_menu;

CREATE TABLE bak_admin_merchant_menu_20260613_sys_menu AS
SELECT *
FROM sys_menu
WHERE app_id = 1
  AND (
      id IN (22, 23)
      OR id BETWEEN 377 AND 387
      OR parent_id IN (22, 23)
  );

CREATE TABLE bak_admin_merchant_menu_20260613_sys_role_menu AS
SELECT rm.*
FROM sys_role_menu rm
WHERE rm.app_id = 1
  AND rm.deleted = 0
  AND (
      rm.menu_id IN (22, 23)
      OR rm.menu_id BETWEEN 377 AND 387
  );

UPDATE sys_menu
SET parent_id = 0,
    menu_code = 'merchant_manage',
    menu_name = '商户管理',
    menu_type = 'CATALOG',
    route_path = '/merchant',
    component_path = NULL,
    permission_code = NULL,
    icon = 'Shop',
    visible = 1,
    status = 1,
    sort_no = 40,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 22;

UPDATE sys_menu
SET parent_id = 22,
    menu_code = 'merchant_info_manage',
    menu_name = '商户信息管理',
    menu_type = 'MENU',
    route_path = '/merchant/info',
    component_path = 'merchant/info/index',
    permission_code = 'merchant:info:list',
    icon = 'Shop',
    visible = 1,
    status = 1,
    sort_no = 41,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 23;

INSERT INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
                      permission_code, icon, redirect, visible, keep_alive, external_link, sort_no, status,
                      created_at, updated_at, deleted)
VALUES
    (377, 1, 23, 'merchant_info_query', '商户详情', 'BUTTON', NULL, NULL, 'merchant:info:query', NULL, NULL, 0, 0, 0, 1, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    (378, 1, 23, 'merchant_info_add', '商户新增', 'BUTTON', NULL, NULL, 'merchant:info:add', NULL, NULL, 0, 0, 0, 2, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    (379, 1, 23, 'merchant_info_edit', '商户修改', 'BUTTON', NULL, NULL, 'merchant:info:edit', NULL, NULL, 0, 0, 0, 3, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    (380, 1, 23, 'merchant_info_change_status', '商户状态', 'BUTTON', NULL, NULL, 'merchant:info:changeStatus', NULL, NULL, 0, 0, 0, 4, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    (381, 1, 23, 'merchant_key_view', '商户密钥查看', 'BUTTON', NULL, NULL, 'merchant:key:view', NULL, NULL, 0, 0, 0, 5, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    (382, 1, 23, 'merchant_key_rotate', '商户密钥轮换', 'BUTTON', NULL, NULL, 'merchant:key:rotate', NULL, NULL, 0, 0, 0, 6, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    (383, 1, 23, 'merchant_platform_key_view', '平台请求密钥查看', 'BUTTON', NULL, NULL, 'merchant:platform-payload-key:view', NULL, NULL, 0, 0, 0, 7, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    (384, 1, 23, 'merchant_platform_key_rotate', '平台请求密钥轮换', 'BUTTON', NULL, NULL, 'merchant:platform-payload-key:rotate', NULL, NULL, 0, 0, 0, 8, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    (385, 1, 23, 'merchant_response_key_view', '商户响应公钥查看', 'BUTTON', NULL, NULL, 'merchant:response-key:view', NULL, NULL, 0, 0, 0, 9, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    (386, 1, 23, 'merchant_response_key_update', '商户响应公钥更新', 'BUTTON', NULL, NULL, 'merchant:response-key:update', NULL, NULL, 0, 0, 0, 10, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    (387, 1, 23, 'merchant_material_view', '对接材料查看', 'BUTTON', NULL, NULL, 'merchant:material:view', NULL, NULL, 0, 0, 0, 11, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
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
VALUES
    (1, 1, 22, 0),
    (1, 1, 23, 0),
    (1, 1, 377, 0),
    (1, 1, 378, 0),
    (1, 1, 379, 0),
    (1, 1, 380, 0),
    (1, 1, 381, 0),
    (1, 1, 382, 0),
    (1, 1, 383, 0),
    (1, 1, 384, 0),
    (1, 1, 385, 0),
    (1, 1, 386, 0),
    (1, 1, 387, 0)
ON DUPLICATE KEY UPDATE deleted = 0;

COMMIT;
