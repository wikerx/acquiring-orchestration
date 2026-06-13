-- =============================================================================
-- 系统菜单树真实数据库修复脚本
-- 说明：本脚本来自 2026-06-13 直接连接本地 payment_acquiring 后确认的真实菜单 ID。
-- 注意：不要再使用假设 211/212/213 为用户/角色/菜单管理的脚本。
-- 真实 ID：
--   系统管理=210，用户=3，角色=4，菜单=18，部门=19，岗位=20
--   字典管理=10，参数设置=9，通知公告=21，日志管理=11
--   系统监控=220，在线用户=111，服务监控=112，缓存监控=113
--   基础数据=27，国家/地区=28，币种管理=29，地区币种配置=30
-- =============================================================================

START TRANSACTION;

DROP TABLE IF EXISTS bak_direct_menu_fix_20260613_sys_menu;
DROP TABLE IF EXISTS bak_direct_menu_fix_20260613_sys_role_menu;

CREATE TABLE bak_direct_menu_fix_20260613_sys_menu AS
SELECT *
FROM sys_menu
WHERE app_id = 1;

CREATE TABLE bak_direct_menu_fix_20260613_sys_role_menu AS
SELECT *
FROM sys_role_menu
WHERE app_id = 1;

UPDATE sys_menu
SET status = 0,
    visible = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND deleted = 0
  AND id IN (1, 2, 22, 31, 110);

UPDATE sys_role_menu
SET deleted = id
WHERE app_id = 1
  AND deleted = 0
  AND menu_id IN (1, 2, 22, 31, 110);

UPDATE sys_menu
SET parent_id = 0,
    menu_code = 'system_manage',
    menu_name = '系统管理',
    menu_type = 'CATALOG',
    route_path = '/system',
    component_path = NULL,
    permission_code = NULL,
    icon = 'Setting',
    visible = 1,
    status = 1,
    sort_no = 10,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 210;

UPDATE sys_menu
SET parent_id = 210,
    menu_code = 'system_user',
    menu_name = '用户管理',
    menu_type = 'MENU',
    route_path = '/system/user',
    component_path = 'system/user/index',
    permission_code = 'system:user:list',
    icon = 'User',
    visible = 1,
    status = 1,
    sort_no = 11,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 3;

UPDATE sys_menu
SET parent_id = 210,
    menu_code = 'system_role',
    menu_name = '角色管理',
    menu_type = 'MENU',
    route_path = '/system/role',
    component_path = 'system/role/index',
    permission_code = 'system:role:list',
    icon = 'UserFilled',
    visible = 1,
    status = 1,
    sort_no = 12,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 4;

UPDATE sys_menu
SET parent_id = 210,
    menu_code = 'system_menu',
    menu_name = '菜单管理',
    menu_type = 'MENU',
    route_path = '/system/menu',
    component_path = 'system/menu/index',
    permission_code = 'system:menu:list',
    icon = 'Menu',
    visible = 1,
    status = 1,
    sort_no = 13,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 18;

UPDATE sys_menu
SET parent_id = 210,
    menu_code = 'system_dept',
    menu_name = '部门管理',
    menu_type = 'MENU',
    route_path = '/system/dept',
    component_path = 'system/dept/index',
    permission_code = 'system:dept:list',
    icon = 'OfficeBuilding',
    visible = 1,
    status = 1,
    sort_no = 14,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 19;

UPDATE sys_menu
SET parent_id = 210,
    menu_code = 'system_post',
    menu_name = '岗位管理',
    menu_type = 'MENU',
    route_path = '/system/post',
    component_path = 'system/post/index',
    permission_code = 'system:post:list',
    icon = 'Postcard',
    visible = 1,
    status = 1,
    sort_no = 15,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 20;

UPDATE sys_menu
SET parent_id = 210,
    menu_code = 'system_dict',
    menu_name = '字典管理',
    menu_type = 'MENU',
    route_path = '/system/dict',
    component_path = 'system/dict/index',
    permission_code = 'system:dict:list',
    icon = 'Tickets',
    visible = 1,
    status = 1,
    sort_no = 16,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 10;

UPDATE sys_menu
SET parent_id = 210,
    menu_code = 'system_config',
    menu_name = '参数设置',
    menu_type = 'MENU',
    route_path = '/system/config',
    component_path = 'system/config/index',
    permission_code = 'system:config:list',
    icon = 'Setting',
    visible = 1,
    status = 1,
    sort_no = 17,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 9;

UPDATE sys_menu
SET parent_id = 210,
    menu_code = 'system_notice',
    menu_name = '通知公告',
    menu_type = 'MENU',
    route_path = '/system/notice',
    component_path = 'system/notice/index',
    permission_code = 'system:notice:list',
    icon = 'Bell',
    visible = 1,
    status = 1,
    sort_no = 18,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 21;

UPDATE sys_menu
SET parent_id = 210,
    menu_code = 'system_log',
    menu_name = '日志管理',
    menu_type = 'MENU',
    route_path = '/system/log',
    component_path = 'system/log/index',
    permission_code = 'system:login-log:list',
    icon = 'DocumentChecked',
    visible = 1,
    status = 1,
    sort_no = 19,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 11;

UPDATE sys_menu
SET parent_id = 0,
    menu_code = 'system_monitor',
    menu_name = '系统监控',
    menu_type = 'CATALOG',
    route_path = '/monitor',
    component_path = NULL,
    permission_code = NULL,
    icon = 'Monitor',
    visible = 1,
    status = 1,
    sort_no = 20,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 220;

UPDATE sys_menu
SET parent_id = 220,
    menu_code = 'monitor_online',
    menu_name = '在线用户',
    menu_type = 'MENU',
    route_path = '/monitor/online',
    component_path = 'monitor/online/index',
    permission_code = 'system:online:list',
    icon = 'User',
    visible = 1,
    status = 1,
    sort_no = 21,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 111;

UPDATE sys_menu
SET parent_id = 220,
    menu_code = 'monitor_server',
    menu_name = '服务监控',
    menu_type = 'MENU',
    route_path = '/monitor/server',
    component_path = 'monitor/server/index',
    permission_code = 'system:server:list',
    icon = 'Cpu',
    visible = 1,
    status = 1,
    sort_no = 22,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 112;

UPDATE sys_menu
SET parent_id = 220,
    menu_code = 'monitor_cache',
    menu_name = '缓存监控',
    menu_type = 'MENU',
    route_path = '/monitor/cache',
    component_path = 'monitor/cache/index',
    permission_code = 'system:cache:list',
    icon = 'Coin',
    visible = 1,
    status = 1,
    sort_no = 23,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 113;

UPDATE sys_menu SET parent_id = 3, visible = 0, status = 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND id BETWEEN 265 AND 272;

UPDATE sys_menu SET parent_id = 4, visible = 0, status = 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND id BETWEEN 273 AND 279;

UPDATE sys_menu SET parent_id = 18, visible = 0, status = 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND id BETWEEN 280 AND 283;

UPDATE sys_menu SET parent_id = 19, visible = 0, status = 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND id BETWEEN 284 AND 287;

UPDATE sys_menu SET parent_id = 20, visible = 0, status = 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND id BETWEEN 288 AND 292;

UPDATE sys_menu SET parent_id = 111, visible = 0, status = 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND id = 293;

UPDATE sys_menu SET parent_id = 113, visible = 0, status = 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND id IN (294, 295);

UPDATE sys_menu SET parent_id = 19, visible = 0, status = 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND id = 344;

UPDATE sys_menu SET parent_id = 10, visible = 0, status = 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND id BETWEEN 333 AND 336;

UPDATE sys_menu SET parent_id = 10, visible = 0, status = 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND id BETWEEN 345 AND 352;

UPDATE sys_menu SET parent_id = 9, visible = 0, status = 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND id BETWEEN 337 AND 340;

UPDATE sys_menu SET parent_id = 9, visible = 0, status = 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND id BETWEEN 353 AND 354;

-- 当前无后端接口或前端仍为占位功能的按钮先禁用，避免授权树展示无效权限。
UPDATE sys_menu
SET status = 0,
    visible = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND deleted = 0
  AND id IN (
      265, -- system:user:query 当前由 system:user:list 覆盖
      268, -- system:user:remove 当前无后端删除接口
      269, -- system:user:export 当前前端仅占位
      273, -- system:role:query 当前由 system:role:list 覆盖
      277, -- system:role:export 当前无后端导出接口
      280, -- system:menu:query 当前由 system:menu:list 覆盖
      283  -- system:menu:remove 当前无后端删除接口
  );

UPDATE sys_role_menu
SET deleted = id
WHERE app_id = 1
  AND deleted = 0
  AND menu_id IN (265, 268, 269, 273, 277, 280, 283);

UPDATE sys_menu
SET parent_id = 0,
    menu_code = 'base',
    menu_name = '基础数据',
    menu_type = 'CATALOG',
    route_path = '/base',
    component_path = NULL,
    permission_code = NULL,
    icon = 'DataLine',
    visible = 1,
    status = 1,
    sort_no = 30,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 27;

UPDATE sys_menu
SET parent_id = 27,
    menu_code = 'base_country',
    menu_name = '国家/地区',
    menu_type = 'MENU',
    route_path = '/base/country',
    component_path = 'base/country',
    permission_code = 'base:country:list',
    icon = 'Location',
    visible = 1,
    status = 1,
    sort_no = 31,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 28;

UPDATE sys_menu
SET parent_id = 27,
    menu_code = 'base_currency',
    menu_name = '币种管理',
    menu_type = 'MENU',
    route_path = '/base/currency',
    component_path = 'base/currency',
    permission_code = 'base:currency:list',
    icon = 'Coin',
    visible = 1,
    status = 1,
    sort_no = 32,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 29;

UPDATE sys_menu
SET parent_id = 27,
    menu_code = 'base_region_currency',
    menu_name = '地区币种配置',
    menu_type = 'MENU',
    route_path = '/base/region-currency',
    component_path = 'base/region-currency',
    permission_code = 'base:countryCurrency:list',
    icon = 'Connection',
    visible = 1,
    status = 1,
    sort_no = 33,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 30;

UPDATE sys_menu SET parent_id = 28, visible = 0, status = 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND id BETWEEN 355 AND 360;

UPDATE sys_menu SET parent_id = 29, visible = 0, status = 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND id BETWEEN 361 AND 366;

UPDATE sys_menu SET parent_id = 30, visible = 0, status = 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND id BETWEEN 367 AND 372;

INSERT INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
                      permission_code, icon, redirect, visible, keep_alive, external_link, sort_no, status,
                      created_at, updated_at, deleted)
VALUES
    (373, 1, 21, 'system_notice_add', '通知新增', 'BUTTON', NULL, NULL, 'system:notice:add', NULL, NULL, 0, 0, 0, 1, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    (374, 1, 21, 'system_notice_edit', '通知修改', 'BUTTON', NULL, NULL, 'system:notice:edit', NULL, NULL, 0, 0, 0, 2, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    (375, 1, 21, 'system_notice_remove', '通知删除', 'BUTTON', NULL, NULL, 'system:notice:remove', NULL, NULL, 0, 0, 0, 3, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    (376, 1, 11, 'system_oper_log_list', '操作日志查询', 'BUTTON', NULL, NULL, 'system:oper-log:list', NULL, NULL, 0, 0, 0, 1, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_code = VALUES(menu_code),
    menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type),
    route_path = VALUES(route_path),
    component_path = VALUES(component_path),
    permission_code = VALUES(permission_code),
    visible = VALUES(visible),
    sort_no = VALUES(sort_no),
    status = VALUES(status),
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3);

UPDATE sys_role_menu rm
JOIN sys_menu m
  ON m.id = rm.menu_id
 AND m.app_id = rm.app_id
SET rm.deleted = rm.id
WHERE rm.app_id = 1
  AND rm.deleted = 0
  AND (
      m.deleted <> 0
      OR m.status <> 1
  );

INSERT INTO sys_role_menu (app_id, role_id, menu_id, deleted)
VALUES
    (1, 1, 210, 0), (1, 1, 3, 0),   (1, 1, 4, 0),   (1, 1, 18, 0),  (1, 1, 19, 0),  (1, 1, 20, 0),
    (1, 1, 10, 0),  (1, 1, 9, 0),   (1, 1, 21, 0),  (1, 1, 11, 0),
    (1, 1, 220, 0), (1, 1, 111, 0), (1, 1, 112, 0), (1, 1, 113, 0),
    (1, 1, 260, 0), (1, 1, 261, 0), (1, 1, 262, 0),
    (1, 1, 27, 0),  (1, 1, 28, 0),  (1, 1, 29, 0),  (1, 1, 30, 0),
    (1, 1, 266, 0), (1, 1, 267, 0), (1, 1, 270, 0), (1, 1, 271, 0), (1, 1, 272, 0),
    (1, 1, 274, 0), (1, 1, 275, 0), (1, 1, 276, 0), (1, 1, 278, 0), (1, 1, 279, 0),
    (1, 1, 281, 0), (1, 1, 282, 0),
    (1, 1, 284, 0), (1, 1, 285, 0), (1, 1, 286, 0), (1, 1, 287, 0), (1, 1, 344, 0),
    (1, 1, 288, 0), (1, 1, 289, 0), (1, 1, 290, 0), (1, 1, 291, 0), (1, 1, 292, 0),
    (1, 1, 293, 0), (1, 1, 294, 0), (1, 1, 295, 0),
    (1, 1, 333, 0), (1, 1, 334, 0), (1, 1, 335, 0), (1, 1, 336, 0),
    (1, 1, 337, 0), (1, 1, 338, 0), (1, 1, 339, 0), (1, 1, 340, 0),
    (1, 1, 345, 0), (1, 1, 346, 0), (1, 1, 347, 0), (1, 1, 348, 0), (1, 1, 349, 0), (1, 1, 350, 0), (1, 1, 351, 0), (1, 1, 352, 0),
    (1, 1, 353, 0), (1, 1, 354, 0),
    (1, 1, 373, 0), (1, 1, 374, 0), (1, 1, 375, 0), (1, 1, 376, 0),
    (1, 1, 355, 0), (1, 1, 356, 0), (1, 1, 357, 0), (1, 1, 358, 0), (1, 1, 359, 0), (1, 1, 360, 0),
    (1, 1, 361, 0), (1, 1, 362, 0), (1, 1, 363, 0), (1, 1, 364, 0), (1, 1, 365, 0), (1, 1, 366, 0),
    (1, 1, 367, 0), (1, 1, 368, 0), (1, 1, 369, 0), (1, 1, 370, 0), (1, 1, 371, 0), (1, 1, 372, 0)
ON DUPLICATE KEY UPDATE deleted = 0;

COMMIT;
