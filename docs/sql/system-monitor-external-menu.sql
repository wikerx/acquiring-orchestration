-- 系统监控外部控制台菜单 SQL 草案
-- 说明：
-- 1. 本脚本只提供草案，不直接执行。
-- 2. 请先确认 sys_menu 中“系统监控”目录的真实 ID，再替换下方变量或注释中的 parent_id。
-- 3. URL 仅使用本地开发示例地址，生产环境请改为实际配置地址，不要直接照搬。
-- 4. external_link = 0 表示系统内 iframe 承载；external_link = 1 表示新窗口打开。
-- 5. 运行时以 sys_menu.route_path 为唯一地址来源，不依赖 yml / Nacos 中的外部监控地址配置。
-- 6. 当前“数据源监控”已调整为系统内页面；RocketMQ / Nacos 控制台仍通过菜单管理或数据库修复脚本维护外部地址。

-- 第一步：查询“系统监控”目录 ID。
SELECT id, menu_code, menu_name
FROM sys_menu
WHERE app_id = 1
  AND deleted = 0
  AND menu_code = 'system_monitor';

-- 假设系统监控目录 ID 为 220。
-- 如果你的环境不同，请把 220 替换为查询结果。

-- 第二步：幂等新增系统监控菜单。
INSERT INTO sys_menu (
    app_id,
    parent_id,
    menu_code,
    menu_name,
    menu_type,
    route_path,
    component_path,
    permission_code,
    icon,
    visible,
    keep_alive,
    external_link,
    sort_no,
    status,
    deleted
)
SELECT 1,
       220,
       seed.menu_code,
       seed.menu_name,
       seed.menu_type,
       seed.route_path,
       seed.component_path,
       seed.permission_code,
       seed.icon,
       1,
       0,
       seed.external_link,
       seed.sort_no,
       1,
       0
FROM (
    SELECT 'monitor_datasource' AS menu_code,
           '数据源监控' AS menu_name,
           'MENU' AS menu_type,
           '/monitor/datasource' AS route_path,
           'monitor/datasource/index' AS component_path,
           'monitor:datasource:view' AS permission_code,
           'DataLine' AS icon,
           0 AS external_link,
           87 AS sort_no
    UNION ALL
    SELECT 'monitor_rocketmq',
           'RocketMQ 控制台',
           'LINK',
           'http://localhost:8088',
           NULL,
           'monitor:rocketmq:view',
           'Connection',
           1,
           88
    UNION ALL
    SELECT 'monitor_nacos',
           'Nacos 控制台',
           'LINK',
           'http://localhost:8848/nacos',
           NULL,
           'monitor:nacos:view',
           'Monitor',
           1,
           89
) seed
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu menu
    WHERE menu.app_id = 1
      AND menu.deleted = 0
      AND (menu.menu_code = seed.menu_code OR menu.permission_code = seed.permission_code)
);

-- 已存在菜单的环境可执行以下修正，将“数据源监控”从旧的 druid 外链占位切换为系统内页面。
UPDATE sys_menu
SET menu_type = 'MENU',
    route_path = '/monitor/datasource',
    component_path = 'monitor/datasource/index',
    external_link = 0
WHERE app_id = 1
  AND deleted = 0
  AND menu_code = 'monitor_datasource';

-- 第三步：幂等补齐菜单权限。
INSERT INTO sys_permission (
    app_id,
    menu_id,
    permission_code,
    permission_name,
    permission_type,
    resource_method,
    resource_path,
    status,
    deleted
)
SELECT 1,
       menu.id,
       seed.permission_code,
       seed.permission_name,
       'MENU',
       NULL,
       NULL,
       1,
       0
FROM sys_menu menu
JOIN (
    SELECT 'monitor_datasource' AS menu_code, 'monitor:datasource:view' AS permission_code, '数据源监控查看' AS permission_name
    UNION ALL SELECT 'monitor_datasource', 'monitor:datasource:export', '数据源监控导出'
    UNION ALL
    SELECT 'monitor_rocketmq', 'monitor:rocketmq:view', 'RocketMQ 控制台查看'
    UNION ALL
    SELECT 'monitor_nacos', 'monitor:nacos:view', 'Nacos 控制台查看'
) seed
    ON seed.menu_code = menu.menu_code
WHERE menu.app_id = 1
  AND menu.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_permission permission
      WHERE permission.app_id = 1
        AND permission.deleted = 0
        AND permission.permission_code = seed.permission_code
  );

-- 第四步：如需授予超级管理员菜单和权限，可按现有 RBAC 规范追加。
-- 授予菜单：
INSERT INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 1, 1, menu.id, 0
FROM sys_menu menu
WHERE menu.app_id = 1
  AND menu.deleted = 0
  AND menu.menu_code IN ('monitor_datasource', 'monitor_rocketmq', 'monitor_nacos')
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu role_menu
      WHERE role_menu.app_id = 1
        AND role_menu.role_id = 1
        AND role_menu.menu_id = menu.id
        AND role_menu.deleted = 0
  );

-- 授予权限：
INSERT INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 1, 1, permission.id, 0
FROM sys_permission permission
WHERE permission.app_id = 1
  AND permission.deleted = 0
  AND permission.permission_code IN ('monitor:datasource:view', 'monitor:datasource:export', 'monitor:rocketmq:view', 'monitor:nacos:view')
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission role_permission
      WHERE role_permission.app_id = 1
        AND role_permission.role_id = 1
        AND role_permission.permission_id = permission.id
        AND role_permission.deleted = 0
  );
