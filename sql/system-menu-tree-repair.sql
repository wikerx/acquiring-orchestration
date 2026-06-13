-- =============================================================================
-- 系统菜单树层级、路由与角色绑定二次修复脚本
-- DEPRECATED：不要再执行本文件。
-- 原因：真实数据库中用户管理/角色管理/菜单管理等菜单 ID 为 3/4/18，
--      不是本脚本早期假设的 211/212/213。
-- 请使用：sql/system-menu-tree-direct-db-fix.sql
-- =============================================================================
--
-- =============================================================================
-- 目标：MySQL 8，admin 应用 app_id = 1，admin 角色 role_id = 1
--
-- 使用说明：
-- 1. 本脚本只生成修复 SQL，不应由 Codex 直接执行。
-- 2. 执行前请先运行“修复前检查 SQL”确认影响范围。
-- 3. 本脚本不物理删除大量数据，只禁用历史旧菜单、软失效异常角色菜单绑定。
-- 4. 本脚本仅校正：
--    - sys_menu 菜单树层级
--    - sys_role_menu 角色菜单绑定
--    - 系统管理 / 系统监控 动态路由所需 path/component/permission
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- 0. 修复前检查 SQL（建议先单独执行这些 SELECT）
-- =============================================================================
-- 查看当前系统管理、系统监控及历史菜单层级：
-- SELECT id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
--        permission_code, visible, status, sort_no, deleted
-- FROM sys_menu
-- WHERE app_id = 1
--   AND deleted = 0
--   AND (
--       id BETWEEN 1 AND 184
--       OR id BETWEEN 210 AND 223
--       OR parent_id IN (210, 211, 212, 213, 214, 217, 220, 221, 222, 223)
--   )
-- ORDER BY parent_id, sort_no, id;
--
-- 查看会被视为根节点的异常菜单：
-- SELECT m.id, m.parent_id, m.menu_code, m.menu_name, m.menu_type, m.route_path,
--        m.visible, m.status, m.deleted
-- FROM sys_menu m
-- LEFT JOIN sys_menu p
--        ON p.id = m.parent_id
--       AND p.app_id = m.app_id
--       AND p.deleted = 0
--       AND p.status = 1
-- WHERE m.app_id = 1
--   AND m.deleted = 0
--   AND m.status = 1
--   AND m.parent_id <> 0
--   AND p.id IS NULL
-- ORDER BY m.parent_id, m.sort_no, m.id;

-- =============================================================================
-- 1. 备份本次涉及的数据
-- =============================================================================
DROP TABLE IF EXISTS bak_menu_tree_fix_20260613_sys_menu;
DROP TABLE IF EXISTS bak_menu_tree_fix_20260613_sys_role_menu;

CREATE TABLE bak_menu_tree_fix_20260613_sys_menu AS
SELECT *
FROM sys_menu
WHERE app_id = 1
  AND (
      id BETWEEN 1 AND 184
      OR id BETWEEN 210 AND 223
      OR id BETWEEN 300 AND 343
      OR parent_id IN (210, 211, 212, 213, 214, 217, 220, 221, 222, 223)
  );

CREATE TABLE bak_menu_tree_fix_20260613_sys_role_menu AS
SELECT rm.*
FROM sys_role_menu rm
LEFT JOIN sys_menu m
       ON m.id = rm.menu_id
      AND m.app_id = rm.app_id
WHERE rm.app_id = 1
  AND rm.deleted = 0
  AND (
      rm.menu_id BETWEEN 1 AND 184
      OR rm.menu_id BETWEEN 210 AND 223
      OR rm.menu_id BETWEEN 300 AND 343
      OR m.parent_id IN (210, 211, 212, 213, 214, 217, 220, 221, 222, 223)
  );

-- =============================================================================
-- 2. 禁用历史旧菜单，避免菜单管理树和登录菜单树出现重复层级
-- =============================================================================
-- 旧初始化版本集中在 1-184；当前标准菜单使用 210+。
-- 这里仅 status=0，不物理删除，便于回滚。
UPDATE sys_menu
SET status = 0,
    visible = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND deleted = 0
  AND id BETWEEN 1 AND 184;

-- 同步软失效旧菜单角色绑定，避免权限集合继续带出旧 permission_code。
UPDATE sys_role_menu
SET deleted = id
WHERE app_id = 1
  AND deleted = 0
  AND menu_id BETWEEN 1 AND 184;

-- =============================================================================
-- 3. 校正系统管理目录与五个子菜单
-- =============================================================================
INSERT INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
                      permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted)
VALUES
    (210, 1, 0,   'system_manage', '系统管理', 'CATALOG', '/system',       NULL,                NULL,               'Setting',        1, 0, 0, 10, 1, 0),
    (211, 1, 210, 'system_user',   '用户管理', 'MENU',    '/system/user',  'system/user/index', 'system:user:list', 'User',           1, 0, 0, 11, 1, 0),
    (212, 1, 210, 'system_role',   '角色管理', 'MENU',    '/system/role',  'system/role/index', 'system:role:list', 'UserFilled',     1, 0, 0, 12, 1, 0),
    (213, 1, 210, 'system_menu',   '菜单管理', 'MENU',    '/system/menu',  'system/menu/index', 'system:menu:list', 'Menu',           1, 0, 0, 13, 1, 0),
    (214, 1, 210, 'system_dept',   '部门管理', 'MENU',    '/system/dept',  'system/dept/index', 'system:dept:list', 'OfficeBuilding', 1, 0, 0, 14, 1, 0),
    (217, 1, 210, 'system_post',   '岗位管理', 'MENU',    '/system/post',  'system/post/index', 'system:post:list', 'Postcard',       1, 0, 0, 15, 1, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_code = VALUES(menu_code),
    menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type),
    route_path = VALUES(route_path),
    component_path = VALUES(component_path),
    permission_code = VALUES(permission_code),
    icon = VALUES(icon),
    visible = VALUES(visible),
    keep_alive = VALUES(keep_alive),
    external_link = VALUES(external_link),
    sort_no = VALUES(sort_no),
    status = VALUES(status),
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3);

-- 本次正确结构不包含“字典管理/参数设置/日志管理”等历史压缩菜单。
-- 不删除数据，仅从当前菜单树禁用，后续如需要可再按单独阶段恢复。
UPDATE sys_menu
SET status = 0,
    visible = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND deleted = 0
  AND id IN (215, 216, 218, 219);

UPDATE sys_role_menu
SET deleted = id
WHERE app_id = 1
  AND deleted = 0
  AND menu_id IN (215, 216, 218, 219);

-- =============================================================================
-- 4. 校正系统监控目录与三个子菜单
-- =============================================================================
INSERT INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
                      permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted)
VALUES
    (220, 1, 0,   'system_monitor', '系统监控', 'CATALOG', '/monitor',        NULL,                 NULL,                 'Monitor', 1, 0, 0, 20, 1, 0),
    (221, 1, 220, 'monitor_online', '在线用户', 'MENU',    '/monitor/online', 'monitor/online/index', 'system:online:list', 'User',    1, 0, 0, 21, 1, 0),
    (222, 1, 220, 'monitor_server', '服务监控', 'MENU',    '/monitor/server', 'monitor/server/index', 'system:server:list', 'Cpu',     1, 0, 0, 22, 1, 0),
    (223, 1, 220, 'monitor_cache',  '缓存监控', 'MENU',    '/monitor/cache',  'monitor/cache/index',  'system:cache:list',  'Coin',    1, 0, 0, 23, 1, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_code = VALUES(menu_code),
    menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type),
    route_path = VALUES(route_path),
    component_path = VALUES(component_path),
    permission_code = VALUES(permission_code),
    icon = VALUES(icon),
    visible = VALUES(visible),
    keep_alive = VALUES(keep_alive),
    external_link = VALUES(external_link),
    sort_no = VALUES(sort_no),
    status = VALUES(status),
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3);

-- =============================================================================
-- 5. 补齐按钮权限节点；按钮必须挂在对应 MENU 下，visible=0，status=1
-- =============================================================================
INSERT INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
                      permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted)
VALUES
    (300, 1, 211, 'system_user_query',         '用户查询', 'BUTTON', NULL, NULL, 'system:user:query',        NULL, 0, 0, 0, 1, 1, 0),
    (301, 1, 211, 'system_user_add',           '用户新增', 'BUTTON', NULL, NULL, 'system:user:add',          NULL, 0, 0, 0, 2, 1, 0),
    (302, 1, 211, 'system_user_edit',          '用户修改', 'BUTTON', NULL, NULL, 'system:user:edit',         NULL, 0, 0, 0, 3, 1, 0),
    (303, 1, 211, 'system_user_remove',        '用户删除', 'BUTTON', NULL, NULL, 'system:user:remove',       NULL, 0, 0, 0, 4, 1, 0),
    (304, 1, 211, 'system_user_export',        '用户导出', 'BUTTON', NULL, NULL, 'system:user:export',       NULL, 0, 0, 0, 5, 1, 0),
    (305, 1, 211, 'system_user_reset_pwd',     '重置密码', 'BUTTON', NULL, NULL, 'system:user:resetPwd',     NULL, 0, 0, 0, 6, 1, 0),
    (306, 1, 211, 'system_user_change_status', '用户状态', 'BUTTON', NULL, NULL, 'system:user:changeStatus', NULL, 0, 0, 0, 7, 1, 0),
    (307, 1, 211, 'system_user_assign_role',   '分配角色', 'BUTTON', NULL, NULL, 'system:user:assign-role',  NULL, 0, 0, 0, 8, 1, 0),

    (310, 1, 212, 'system_role_query',         '角色查询', 'BUTTON', NULL, NULL, 'system:role:query',        NULL, 0, 0, 0, 1, 1, 0),
    (311, 1, 212, 'system_role_add',           '角色新增', 'BUTTON', NULL, NULL, 'system:role:add',          NULL, 0, 0, 0, 2, 1, 0),
    (312, 1, 212, 'system_role_edit',          '角色修改', 'BUTTON', NULL, NULL, 'system:role:edit',         NULL, 0, 0, 0, 3, 1, 0),
    (313, 1, 212, 'system_role_remove',        '角色删除', 'BUTTON', NULL, NULL, 'system:role:remove',       NULL, 0, 0, 0, 4, 1, 0),
    (314, 1, 212, 'system_role_export',        '角色导出', 'BUTTON', NULL, NULL, 'system:role:export',       NULL, 0, 0, 0, 5, 1, 0),
    (315, 1, 212, 'system_role_change_status', '角色状态', 'BUTTON', NULL, NULL, 'system:role:changeStatus', NULL, 0, 0, 0, 6, 1, 0),
    (316, 1, 212, 'system_role_data_scope',    '角色授权', 'BUTTON', NULL, NULL, 'system:role:dataScope',    NULL, 0, 0, 0, 7, 1, 0),

    (320, 1, 213, 'system_menu_query',         '菜单查询', 'BUTTON', NULL, NULL, 'system:menu:query',        NULL, 0, 0, 0, 1, 1, 0),
    (321, 1, 213, 'system_menu_add',           '菜单新增', 'BUTTON', NULL, NULL, 'system:menu:add',          NULL, 0, 0, 0, 2, 1, 0),
    (322, 1, 213, 'system_menu_edit',          '菜单修改', 'BUTTON', NULL, NULL, 'system:menu:edit',         NULL, 0, 0, 0, 3, 1, 0),
    (323, 1, 213, 'system_menu_remove',        '菜单删除', 'BUTTON', NULL, NULL, 'system:menu:remove',       NULL, 0, 0, 0, 4, 1, 0),

    (324, 1, 214, 'system_dept_query',         '部门查询', 'BUTTON', NULL, NULL, 'system:dept:query',        NULL, 0, 0, 0, 1, 1, 0),
    (325, 1, 214, 'system_dept_add',           '部门新增', 'BUTTON', NULL, NULL, 'system:dept:add',          NULL, 0, 0, 0, 2, 1, 0),
    (326, 1, 214, 'system_dept_edit',          '部门修改', 'BUTTON', NULL, NULL, 'system:dept:edit',         NULL, 0, 0, 0, 3, 1, 0),
    (327, 1, 214, 'system_dept_remove',        '部门删除', 'BUTTON', NULL, NULL, 'system:dept:remove',       NULL, 0, 0, 0, 4, 1, 0),
    (344, 1, 214, 'system_dept_export',        '部门导出', 'BUTTON', NULL, NULL, 'system:dept:export',       NULL, 0, 0, 0, 5, 1, 0),

    (328, 1, 217, 'system_post_query',         '岗位查询', 'BUTTON', NULL, NULL, 'system:post:query',        NULL, 0, 0, 0, 1, 1, 0),
    (329, 1, 217, 'system_post_add',           '岗位新增', 'BUTTON', NULL, NULL, 'system:post:add',          NULL, 0, 0, 0, 2, 1, 0),
    (330, 1, 217, 'system_post_edit',          '岗位修改', 'BUTTON', NULL, NULL, 'system:post:edit',         NULL, 0, 0, 0, 3, 1, 0),
    (331, 1, 217, 'system_post_remove',        '岗位删除', 'BUTTON', NULL, NULL, 'system:post:remove',       NULL, 0, 0, 0, 4, 1, 0),
    (332, 1, 217, 'system_post_export',        '岗位导出', 'BUTTON', NULL, NULL, 'system:post:export',       NULL, 0, 0, 0, 5, 1, 0),

    (341, 1, 221, 'system_online_force_logout','强制下线', 'BUTTON', NULL, NULL, 'system:online:forceLogout', NULL, 0, 0, 0, 1, 1, 0),
    (342, 1, 223, 'system_cache_query',        '缓存查询', 'BUTTON', NULL, NULL, 'system:cache:query',       NULL, 0, 0, 0, 1, 1, 0),
    (343, 1, 223, 'system_cache_clear',        '缓存清理', 'BUTTON', NULL, NULL, 'system:cache:clear',       NULL, 0, 0, 0, 2, 1, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_code = VALUES(menu_code),
    menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type),
    route_path = VALUES(route_path),
    component_path = VALUES(component_path),
    permission_code = VALUES(permission_code),
    visible = VALUES(visible),
    keep_alive = VALUES(keep_alive),
    external_link = VALUES(external_link),
    sort_no = VALUES(sort_no),
    status = VALUES(status),
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3);

-- 禁用当前没有后端接口或仍是前端占位的按钮权限，避免授权树展示不可用权限。
UPDATE sys_menu
SET status = 0,
    visible = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND deleted = 0
  AND id IN (
      300, -- system:user:query 当前以后端 system:user:list 覆盖查询能力
      303, -- system:user:remove 当前无后端删除接口，前端仅占位提示
      304, -- system:user:export 当前无后端导出接口，前端仅占位提示
      310, -- system:role:query 当前以后端 system:role:list 覆盖查询能力
      314, -- system:role:export 当前无后端导出接口
      320, -- system:menu:query 当前以后端 system:menu:list 覆盖查询能力
      323  -- system:menu:remove 当前无后端删除接口
  );

UPDATE sys_role_menu
SET deleted = id
WHERE app_id = 1
  AND deleted = 0
  AND menu_id IN (300, 303, 304, 310, 314, 320, 323);

-- =============================================================================
-- 6. 软失效异常角色菜单绑定
-- =============================================================================
-- 软失效已禁用/已删除/不存在菜单的绑定，避免权限集合污染。
UPDATE sys_role_menu rm
LEFT JOIN sys_menu m
       ON m.id = rm.menu_id
      AND m.app_id = rm.app_id
SET rm.deleted = rm.id
WHERE rm.app_id = 1
  AND rm.deleted = 0
  AND (
      m.id IS NULL
      OR m.deleted <> 0
      OR m.status <> 1
  );

-- =============================================================================
-- 7. 补齐 admin 角色对目标菜单和按钮的绑定
-- =============================================================================
INSERT INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 1, 1, m.id, 0
FROM sys_menu m
WHERE m.app_id = 1
  AND m.deleted = 0
  AND m.status = 1
  AND m.id IN (
      210, 211, 212, 213, 214, 217,
      220, 221, 222, 223,
      301, 302, 305, 306, 307,
      311, 312, 313, 315, 316,
      321, 322,
      324, 325, 326, 327, 344,
      328, 329, 330, 331, 332,
      341, 342, 343
  )
ON DUPLICATE KEY UPDATE
    deleted = 0;

-- =============================================================================
-- 8. 修复后验证 SQL（建议执行脚本后运行）
-- =============================================================================
-- 期望只看到两个根目录：系统管理、系统监控。
-- SELECT id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
--        permission_code, visible, status, sort_no, deleted
-- FROM sys_menu
-- WHERE app_id = 1
--   AND deleted = 0
--   AND status = 1
--   AND (
--       id IN (210, 211, 212, 213, 214, 217, 220, 221, 222, 223)
--       OR parent_id IN (211, 212, 213, 214, 217, 221, 223)
--   )
-- ORDER BY parent_id, sort_no, id;
--
-- 期望没有返回记录。
-- SELECT m.id, m.parent_id, m.menu_code, m.menu_name, m.menu_type, m.route_path
-- FROM sys_menu m
-- LEFT JOIN sys_menu p
--        ON p.id = m.parent_id
--       AND p.app_id = m.app_id
--       AND p.deleted = 0
--       AND p.status = 1
-- WHERE m.app_id = 1
--   AND m.deleted = 0
--   AND m.status = 1
--   AND m.parent_id <> 0
--   AND p.id IS NULL;
--
-- 期望按钮全部 visible=0，不会进入侧边栏。
-- SELECT id, parent_id, menu_name, permission_code, visible, status
-- FROM sys_menu
-- WHERE app_id = 1
--   AND deleted = 0
--   AND status = 1
--   AND menu_type = 'BUTTON'
--   AND visible <> 0;
--
-- 期望不展示当前无后端接口的占位按钮。
-- SELECT id, menu_name, permission_code, status, deleted
-- FROM sys_menu
-- WHERE app_id = 1
--   AND id IN (300, 303, 304, 310, 314, 320, 323)
--   AND deleted = 0
--   AND status = 1;

SET FOREIGN_KEY_CHECKS = 1;
