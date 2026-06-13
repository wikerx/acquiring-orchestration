-- =============================================================================
-- 字典管理 + 参数设置 拆分为独立菜单（仅处理这两个，不动其他已有菜单）
-- =============================================================================
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 更新 215: 字典参数 → 字典管理
UPDATE sys_menu
SET menu_code       = 'system_dict',
    menu_name       = '字典管理',
    route_path      = '/system/dict',
    component_path  = 'system/dict/index',
    permission_code = 'system:dict:list',
    icon            = 'Tickets',
    updated_at      = CURRENT_TIMESTAMP(3)
WHERE id      = 215
  AND app_id  = 1
  AND deleted = 0;

-- 2. 新增 218: 参数设置
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted)
VALUES (218, 1, 210, 'system_config', '参数设置', 'MENU', '/system/config', 'system/config/index', 'system:config:list', 'Setting', 1, 0, 0, 17, 1, 0);

-- 3. 把这两个菜单授权给 ADMIN_OPERATOR
INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 1, 1, id, 0 FROM sys_menu
WHERE app_id = 1 AND deleted = 0 AND status = 1 AND id IN (215, 218);

SET FOREIGN_KEY_CHECKS = 1;
