-- =============================================================================
-- RBAC 权限树修复脚本
-- 目标：管理员应用 (app_id = 1)
-- 将压缩的 V3 菜单拆分为标准 RuoYi 结构，新增系统监控目录和 BUTTON 子菜单
-- 用法：在已有 admin-system-schema.sql 数据的数据库上运行
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- 1. 修复前备份 RBAC 数据
-- =============================================================================
DROP TABLE IF EXISTS bak_rbac_fix_20260613_sys_menu;
DROP TABLE IF EXISTS bak_rbac_fix_20260613_sys_permission;
DROP TABLE IF EXISTS bak_rbac_fix_20260613_sys_role_menu;
DROP TABLE IF EXISTS bak_rbac_fix_20260613_sys_role_permission;

CREATE TABLE bak_rbac_fix_20260613_sys_menu        AS SELECT * FROM sys_menu;
CREATE TABLE bak_rbac_fix_20260613_sys_permission  AS SELECT * FROM sys_permission;
CREATE TABLE bak_rbac_fix_20260613_sys_role_menu   AS SELECT * FROM sys_role_menu;
CREATE TABLE bak_rbac_fix_20260613_sys_role_permission AS SELECT * FROM sys_role_permission;

-- =============================================================================
-- 2. CATALOG 菜单规范化（清除 permission_code 和 component_path）
-- =============================================================================
UPDATE sys_menu
SET permission_code = NULL,
    component_path  = NULL,
    updated_at      = CURRENT_TIMESTAMP(3)
WHERE app_id    = 1
  AND deleted   = 0
  AND menu_type = 'CATALOG';

-- =============================================================================
-- 3. 修复 V3 压缩菜单 —— 拆分为独立实体
-- =============================================================================

-- 214: 部门岗位 → 部门管理
UPDATE sys_menu
SET menu_code       = 'system_dept',
    menu_name       = '部门管理',
    route_path      = '/system/dept',
    component_path  = 'system/dept/index',
    permission_code = 'system:dept:list',
    icon            = 'OfficeBuilding',
    updated_at      = CURRENT_TIMESTAMP(3)
WHERE id      = 214
  AND app_id  = 1
  AND deleted = 0;

-- 215: 字典参数 → 字典管理
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

-- 216: 日志管理 → 登录日志
UPDATE sys_menu
SET menu_code       = 'system_login_log',
    menu_name       = '登录日志',
    route_path      = '/system/login-log',
    component_path  = 'system/login-log/index',
    permission_code = 'system:login-log:list',
    icon            = 'DocumentChecked',
    updated_at      = CURRENT_TIMESTAMP(3)
WHERE id      = 216
  AND app_id  = 1
  AND deleted = 0;

-- =============================================================================
-- 4. 软删除孤儿菜单（186、187 的 parent_id=100 属于已禁用的 V2 菜单）
-- =============================================================================
UPDATE sys_menu
SET deleted    = id,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE id IN (186, 187)
  AND app_id  = 1
  AND deleted = 0;

-- 清理孤儿菜单的角色授权
UPDATE sys_role_menu
SET deleted = id
WHERE menu_id IN (186, 187)
  AND app_id  = 1
  AND deleted = 0;

-- =============================================================================
-- 5. 新增独立菜单：岗位管理、参数设置、操作日志
-- =============================================================================

-- 217: 岗位管理
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted)
VALUES (217, 1, 210, 'system_post', '岗位管理', 'MENU', '/system/post', 'system/post/index', 'system:post:list', 'Postcard', 1, 0, 0, 15, 1, 0);

-- 218: 参数设置
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted)
VALUES (218, 1, 210, 'system_config', '参数设置', 'MENU', '/system/config', 'system/config/index', 'system:config:list', 'Setting', 1, 0, 0, 17, 1, 0);

-- 219: 操作日志
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted)
VALUES (219, 1, 210, 'system_oper_log', '操作日志', 'MENU', '/system/oper-log', 'system/oper-log/index', 'system:oper-log:list', 'Document', 1, 0, 0, 19, 1, 0);

-- =============================================================================
-- 6. 新增系统监控目录及子菜单
-- =============================================================================

-- 220: 系统监控 CATALOG
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted)
VALUES (220, 1, 0, 'system_monitor', '系统监控', 'CATALOG', '/monitor', NULL, NULL, 'Monitor', 1, 0, 0, 80, 1, 0);

-- 221: 在线用户
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted)
VALUES (221, 1, 220, 'monitor_online', '在线用户', 'MENU', '/monitor/online', 'monitor/online/index', 'system:online:list', 'User', 1, 0, 0, 81, 1, 0);

-- 222: 服务监控
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted)
VALUES (222, 1, 220, 'monitor_server', '服务监控', 'MENU', '/monitor/server', 'monitor/server/index', 'system:server:list', 'Cpu', 1, 0, 0, 82, 1, 0);

-- 223: 缓存监控
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted)
VALUES (223, 1, 220, 'monitor_cache', '缓存监控', 'MENU', '/monitor/cache', 'monitor/cache/index', 'system:cache:list', 'Coin', 1, 0, 0, 83, 1, 0);

-- =============================================================================
-- 7. 为所有菜单新增 BUTTON 子菜单
-- =============================================================================

-- 7.1 用户管理 (parent=211) BUTTON
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted) VALUES
(300, 1, 211, 'system_user_query',        '用户查询',   'BUTTON', NULL, NULL, 'system:user:query',        NULL, 0, 0, 0, 1, 1, 0),
(301, 1, 211, 'system_user_add',          '用户新增',   'BUTTON', NULL, NULL, 'system:user:add',          NULL, 0, 0, 0, 2, 1, 0),
(302, 1, 211, 'system_user_edit',         '用户修改',   'BUTTON', NULL, NULL, 'system:user:edit',         NULL, 0, 0, 0, 3, 1, 0),
(303, 1, 211, 'system_user_remove',       '用户删除',   'BUTTON', NULL, NULL, 'system:user:remove',       NULL, 0, 0, 0, 4, 1, 0),
(304, 1, 211, 'system_user_export',       '用户导出',   'BUTTON', NULL, NULL, 'system:user:export',       NULL, 0, 0, 0, 5, 1, 0),
(305, 1, 211, 'system_user_reset_pwd',    '重置密码',   'BUTTON', NULL, NULL, 'system:user:resetPwd',     NULL, 0, 0, 0, 6, 1, 0),
(306, 1, 211, 'system_user_change_status','修改状态',   'BUTTON', NULL, NULL, 'system:user:changeStatus', NULL, 0, 0, 0, 7, 1, 0),
(307, 1, 211, 'system_user_assign_role',  '分配角色',   'BUTTON', NULL, NULL, 'system:user:assign-role',  NULL, 0, 0, 0, 8, 1, 0);

-- 7.2 角色管理 (parent=212) BUTTON
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted) VALUES
(310, 1, 212, 'system_role_query',        '角色查询',   'BUTTON', NULL, NULL, 'system:role:query',        NULL, 0, 0, 0, 1, 1, 0),
(311, 1, 212, 'system_role_add',          '角色新增',   'BUTTON', NULL, NULL, 'system:role:add',          NULL, 0, 0, 0, 2, 1, 0),
(312, 1, 212, 'system_role_edit',         '角色修改',   'BUTTON', NULL, NULL, 'system:role:edit',         NULL, 0, 0, 0, 3, 1, 0),
(313, 1, 212, 'system_role_remove',       '角色删除',   'BUTTON', NULL, NULL, 'system:role:remove',       NULL, 0, 0, 0, 4, 1, 0),
(314, 1, 212, 'system_role_export',       '角色导出',   'BUTTON', NULL, NULL, 'system:role:export',       NULL, 0, 0, 0, 5, 1, 0),
(315, 1, 212, 'system_role_change_status','角色状态',   'BUTTON', NULL, NULL, 'system:role:changeStatus', NULL, 0, 0, 0, 6, 1, 0),
(316, 1, 212, 'system_role_data_scope',   '角色授权',   'BUTTON', NULL, NULL, 'system:role:dataScope',    NULL, 0, 0, 0, 7, 1, 0);

-- 7.3 菜单管理 (parent=213) BUTTON
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted) VALUES
(320, 1, 213, 'system_menu_query',  '菜单查询', 'BUTTON', NULL, NULL, 'system:menu:query',  NULL, 0, 0, 0, 1, 1, 0),
(321, 1, 213, 'system_menu_add',    '菜单新增', 'BUTTON', NULL, NULL, 'system:menu:add',    NULL, 0, 0, 0, 2, 1, 0),
(322, 1, 213, 'system_menu_edit',   '菜单修改', 'BUTTON', NULL, NULL, 'system:menu:edit',   NULL, 0, 0, 0, 3, 1, 0),
(323, 1, 213, 'system_menu_remove', '菜单删除', 'BUTTON', NULL, NULL, 'system:menu:remove', NULL, 0, 0, 0, 4, 1, 0);

-- 7.4 部门管理 (parent=214) BUTTON
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted) VALUES
(324, 1, 214, 'system_dept_query',  '部门查询', 'BUTTON', NULL, NULL, 'system:dept:query',  NULL, 0, 0, 0, 1, 1, 0),
(325, 1, 214, 'system_dept_add',    '部门新增', 'BUTTON', NULL, NULL, 'system:dept:add',    NULL, 0, 0, 0, 2, 1, 0),
(326, 1, 214, 'system_dept_edit',   '部门修改', 'BUTTON', NULL, NULL, 'system:dept:edit',   NULL, 0, 0, 0, 3, 1, 0),
(327, 1, 214, 'system_dept_remove', '部门删除', 'BUTTON', NULL, NULL, 'system:dept:remove', NULL, 0, 0, 0, 4, 1, 0);

-- 7.5 岗位管理 (parent=217) BUTTON
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted) VALUES
(328, 1, 217, 'system_post_query',  '岗位查询', 'BUTTON', NULL, NULL, 'system:post:query',  NULL, 0, 0, 0, 1, 1, 0),
(329, 1, 217, 'system_post_add',    '岗位新增', 'BUTTON', NULL, NULL, 'system:post:add',    NULL, 0, 0, 0, 2, 1, 0),
(330, 1, 217, 'system_post_edit',   '岗位修改', 'BUTTON', NULL, NULL, 'system:post:edit',   NULL, 0, 0, 0, 3, 1, 0),
(331, 1, 217, 'system_post_remove', '岗位删除', 'BUTTON', NULL, NULL, 'system:post:remove', NULL, 0, 0, 0, 4, 1, 0),
(332, 1, 217, 'system_post_export', '岗位导出', 'BUTTON', NULL, NULL, 'system:post:export', NULL, 0, 0, 0, 5, 1, 0);

-- 7.6 字典管理 (parent=215) BUTTON
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted) VALUES
(333, 1, 215, 'system_dict_query',  '字典查询', 'BUTTON', NULL, NULL, 'system:dict:query',  NULL, 0, 0, 0, 1, 1, 0),
(334, 1, 215, 'system_dict_add',    '字典新增', 'BUTTON', NULL, NULL, 'system:dict:add',    NULL, 0, 0, 0, 2, 1, 0),
(335, 1, 215, 'system_dict_edit',   '字典修改', 'BUTTON', NULL, NULL, 'system:dict:edit',   NULL, 0, 0, 0, 3, 1, 0),
(336, 1, 215, 'system_dict_remove', '字典删除', 'BUTTON', NULL, NULL, 'system:dict:remove', NULL, 0, 0, 0, 4, 1, 0);

-- 7.7 参数设置 (parent=218) BUTTON
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted) VALUES
(337, 1, 218, 'system_config_query',  '参数查询', 'BUTTON', NULL, NULL, 'system:config:query',  NULL, 0, 0, 0, 1, 1, 0),
(338, 1, 218, 'system_config_add',    '参数新增', 'BUTTON', NULL, NULL, 'system:config:add',    NULL, 0, 0, 0, 2, 1, 0),
(339, 1, 218, 'system_config_edit',   '参数修改', 'BUTTON', NULL, NULL, 'system:config:edit',   NULL, 0, 0, 0, 3, 1, 0),
(340, 1, 218, 'system_config_remove', '参数删除', 'BUTTON', NULL, NULL, 'system:config:remove', NULL, 0, 0, 0, 4, 1, 0);

-- 7.8 在线用户 (parent=221) BUTTON
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted) VALUES
(341, 1, 221, 'system_online_force_logout', '强制下线', 'BUTTON', NULL, NULL, 'system:online:forceLogout', NULL, 0, 0, 0, 1, 1, 0);

-- 7.9 缓存监控 (parent=223) BUTTON
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted) VALUES
(342, 1, 223, 'system_cache_query', '缓存查询', 'BUTTON', NULL, NULL, 'system:cache:query', NULL, 0, 0, 0, 1, 1, 0),
(343, 1, 223, 'system_cache_clear', '缓存清理', 'BUTTON', NULL, NULL, 'system:cache:clear', NULL, 0, 0, 0, 2, 1, 0);

-- =============================================================================
-- 8. 禁用 sys_permission 中的 *:*:* 通配符权限
-- =============================================================================
UPDATE sys_permission
SET status     = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id          = 1
  AND deleted         = 0
  AND permission_code = '*:*:*';

-- =============================================================================
-- 9. 修复孤儿权限（menu_id=0 的权限需要设置正确的 menu_id）
-- =============================================================================

-- 640: system:config:add → parent 218 (参数设置)
UPDATE sys_permission SET menu_id = 218, updated_at = CURRENT_TIMESTAMP(3)
WHERE id = 640 AND app_id = 1 AND menu_id = 0 AND deleted = 0;

-- 641: system:config:edit → parent 218 (参数设置)
UPDATE sys_permission SET menu_id = 218, updated_at = CURRENT_TIMESTAMP(3)
WHERE id = 641 AND app_id = 1 AND menu_id = 0 AND deleted = 0;

-- 642: system:dict:add → parent 215 (字典管理)
UPDATE sys_permission SET menu_id = 215, updated_at = CURRENT_TIMESTAMP(3)
WHERE id = 642 AND app_id = 1 AND menu_id = 0 AND deleted = 0;

-- 643: system:dict:edit → parent 215 (字典管理)
UPDATE sys_permission SET menu_id = 215, updated_at = CURRENT_TIMESTAMP(3)
WHERE id = 643 AND app_id = 1 AND menu_id = 0 AND deleted = 0;

-- =============================================================================
-- 10. 清理旧的 sys_role_menu（重新授权）
-- =============================================================================
DELETE FROM sys_role_menu
WHERE app_id  = 1
  AND deleted = 0;

-- =============================================================================
-- 11. 为 ADMIN_OPERATOR 角色授予全部活跃菜单和按钮
-- =============================================================================
INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 1, 1, m.id, 0
FROM sys_menu m
WHERE m.app_id  = 1
  AND m.deleted = 0
  AND m.status  = 1;

-- =============================================================================
-- 12. 清理旧的 sys_role_permission（重新授权）
-- =============================================================================
DELETE FROM sys_role_permission
WHERE app_id  = 1
  AND deleted = 0;

-- 为 ADMIN_OPERATOR 授予全部活跃权限
INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 1, 1, p.id, 0
FROM sys_permission p
WHERE p.app_id  = 1
  AND p.deleted = 0
  AND p.status  = 1;

-- =============================================================================
-- 13. 同步 sys_user_role（确保与 sys_account_role 一致）
-- =============================================================================
INSERT IGNORE INTO sys_user_role (app_id, user_id, role_id, deleted)
SELECT ar.app_id, a.user_id, ar.role_id, 0
FROM sys_account_role ar
JOIN sys_account a ON a.id = ar.account_id AND a.app_id = ar.app_id AND a.deleted = 0
WHERE ar.app_id = 1
  AND ar.deleted = 0;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 14. 验证查询（可选，取消注释以验证）
-- =============================================================================
-- SELECT id, parent_id, menu_code, menu_name, menu_type, permission_code, status
-- FROM sys_menu
-- WHERE app_id = 1 AND deleted = 0
-- ORDER BY sort_no, id;
--
-- SELECT id, menu_id, permission_code, permission_name, status
-- FROM sys_permission
-- WHERE app_id = 1 AND deleted = 0 AND menu_id = 0;
--
-- SELECT COUNT(*) AS admin_menu_count FROM sys_role_menu WHERE role_id = 1 AND app_id = 1 AND deleted = 0;
