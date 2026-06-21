-- 分表治理菜单权限 SQL。
-- 本脚本按“目录 -> 页面 -> 按钮 -> 权限资源”分阶段幂等插入，避免同一条 INSERT 中读取不到刚插入父菜单。

-- 1. 查看当前顶级菜单排序，确认“分表管理”会作为顶级目录展示。
SELECT id, menu_code, menu_name
FROM sys_menu
WHERE app_id = 1
  AND deleted = 0
  AND parent_id = 0
ORDER BY sort_no;

-- 2. 新增“分表管理”顶级目录。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT 1,
       0,
       'monitor_sharding',
       '分表管理',
       'CATALOG',
       '/monitor/sharding',
       'monitor/sharding/index',
       NULL,
       'Coin',
       1,
       0,
       0,
       85,
       1,
       0
WHERE NOT EXISTS (
      SELECT 1
      FROM sys_menu exists_menu
      WHERE exists_menu.app_id = 1
        AND exists_menu.menu_code = 'monitor_sharding'
        AND exists_menu.deleted = 0
  );

-- 历史环境如果已经把“分表管理”挂在“系统监控”下，则提升为顶级目录，保持左侧菜单二级结构。
UPDATE sys_menu
SET parent_id = 0,
    component_path = 'monitor/sharding/index',
    sort_no = 85
WHERE app_id = 1
  AND deleted = 0
  AND menu_code = 'monitor_sharding';

-- 3. 新增“分表管理”下的四个子页面。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT seed.app_id,
       parent.id,
       seed.menu_code,
       seed.menu_name,
       seed.menu_type,
       seed.route_path,
       seed.component_path,
       seed.permission_code,
       seed.icon,
       seed.visible,
       0,
       0,
       seed.sort_no,
       1,
       0
FROM (
    SELECT 1 AS app_id, 'monitor_sharding_rule' AS menu_code, '分表规则' AS menu_name, 'MENU' AS menu_type,
           '/monitor/sharding/rules' AS route_path, 'monitor/sharding/rules/index' AS component_path,
           'monitor:sharding:rule:list' AS permission_code, 'List' AS icon, 1 AS visible, 91 AS sort_no
    UNION ALL
    SELECT 1, 'monitor_sharding_physical', '物理表清单', 'MENU',
           '/monitor/sharding/physical-tables', 'monitor/sharding/physical-tables/index',
           'monitor:sharding:physical:list', 'Grid', 1, 92
    UNION ALL
    SELECT 1, 'monitor_sharding_task_log', '建表任务日志', 'MENU',
           '/monitor/sharding/table-create-logs', 'monitor/sharding/table-create-logs/index',
           'monitor:sharding:task:list', 'Document', 1, 93
    UNION ALL
    SELECT 1, 'monitor_sharding_id_rule', 'ID规则说明', 'MENU',
           '/monitor/sharding/id-rule', 'monitor/sharding/id-rule/index',
           'monitor:sharding:idRule:query', 'Key', 1, 94
) seed
JOIN sys_menu parent
  ON parent.app_id = seed.app_id
 AND parent.menu_code = 'monitor_sharding'
 AND parent.deleted = 0
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu exists_menu
    WHERE exists_menu.app_id = seed.app_id
      AND exists_menu.menu_code = seed.menu_code
      AND exists_menu.deleted = 0
);

-- 4. 新增按钮权限菜单。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT seed.app_id,
       parent.id,
       seed.menu_code,
       seed.menu_name,
       'BUTTON',
       NULL,
       NULL,
       seed.permission_code,
       NULL,
       0,
       0,
       0,
       seed.sort_no,
       1,
       0
FROM (
    SELECT 1 AS app_id, 'monitor_sharding_rule' AS parent_code, 'monitor_sharding_rule_query' AS menu_code,
           '分表规则详情' AS menu_name, 'monitor:sharding:rule:query' AS permission_code, 1 AS sort_no
    UNION ALL SELECT 1, 'monitor_sharding_physical', 'monitor_sharding_physical_query',
           '物理表详情', 'monitor:sharding:physical:query', 1
    UNION ALL SELECT 1, 'monitor_sharding_physical', 'monitor_sharding_physical_refresh',
           '物理表刷新', 'monitor:sharding:physical:refresh', 2
    UNION ALL SELECT 1, 'monitor_sharding_physical', 'monitor_sharding_physical_check',
           '结构检查', 'monitor:sharding:physical:check', 3
    UNION ALL SELECT 1, 'monitor_sharding_task_log', 'monitor_sharding_task_query',
           '建表任务详情', 'monitor:sharding:task:query', 1
    UNION ALL SELECT 1, 'monitor_sharding_task_log', 'monitor_sharding_task_dry_run',
           '建表预演', 'monitor:sharding:task:dryRun', 2
    UNION ALL SELECT 1, 'monitor_sharding_task_log', 'monitor_sharding_task_execute',
           '立即建表', 'monitor:sharding:task:execute', 3
) seed
JOIN sys_menu parent
  ON parent.app_id = seed.app_id
 AND parent.menu_code = seed.parent_code
 AND parent.deleted = 0
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu exists_menu
    WHERE exists_menu.app_id = seed.app_id
      AND exists_menu.permission_code = seed.permission_code
      AND exists_menu.deleted = 0
);

-- 5. 新增权限资源。
INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, status, deleted
)
SELECT seed.app_id,
       menu.id,
       seed.permission_code,
       seed.permission_name,
       seed.permission_type,
       seed.resource_method,
       seed.resource_path,
       1,
       0
FROM (
    SELECT 1 AS app_id, 'monitor_sharding_rule' AS menu_code, 'monitor:sharding:rule:list' AS permission_code,
           '分表规则查询' AS permission_name, 'MENU' AS permission_type, 'GET' AS resource_method,
           '/admin/monitor/sharding/rules' AS resource_path
    UNION ALL SELECT 1, 'monitor_sharding_rule', 'monitor:sharding:rule:query',
           '分表规则详情', 'BUTTON', 'GET', '/admin/monitor/sharding/rules/**'
    UNION ALL SELECT 1, 'monitor_sharding_physical', 'monitor:sharding:physical:list',
           '物理表清单查询', 'MENU', 'POST', '/admin/monitor/sharding/physical-tables/search'
    UNION ALL SELECT 1, 'monitor_sharding_physical', 'monitor:sharding:physical:query',
           '物理表详情', 'BUTTON', 'GET', '/admin/monitor/sharding/physical-tables/**'
    UNION ALL SELECT 1, 'monitor_sharding_physical', 'monitor:sharding:physical:refresh',
           '物理表刷新', 'BUTTON', 'POST', '/admin/monitor/sharding/physical-tables/refresh'
    UNION ALL SELECT 1, 'monitor_sharding_physical', 'monitor:sharding:physical:check',
           '结构检查', 'BUTTON', 'POST', '/admin/monitor/sharding/physical-tables/check-schema'
    UNION ALL SELECT 1, 'monitor_sharding_task_log', 'monitor:sharding:task:list',
           '建表任务日志查询', 'MENU', 'POST', '/admin/monitor/sharding/table-create/logs/search'
    UNION ALL SELECT 1, 'monitor_sharding_task_log', 'monitor:sharding:task:query',
           '建表任务详情', 'BUTTON', 'GET', '/admin/monitor/sharding/table-create/logs/**'
    UNION ALL SELECT 1, 'monitor_sharding_task_log', 'monitor:sharding:task:dryRun',
           '建表预演', 'BUTTON', 'POST', '/admin/monitor/sharding/table-create/dry-run'
    UNION ALL SELECT 1, 'monitor_sharding_task_log', 'monitor:sharding:task:execute',
           '立即建表', 'BUTTON', 'POST', '/admin/monitor/sharding/table-create/execute'
    UNION ALL SELECT 1, 'monitor_sharding_id_rule', 'monitor:sharding:idRule:query',
           'ID规则查询', 'MENU', 'GET', '/admin/monitor/sharding/id-rule'
) seed
JOIN sys_menu menu
  ON menu.app_id = seed.app_id
 AND menu.menu_code = seed.menu_code
 AND menu.deleted = 0
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission exists_permission
    WHERE exists_permission.app_id = seed.app_id
      AND exists_permission.permission_code = seed.permission_code
      AND exists_permission.deleted = 0
);
