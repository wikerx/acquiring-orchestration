-- Admin RBAC repair script.
-- Scope: admin app (app_id = 1). This script is idempotent for local/dev data repair.

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Remove historical test roles from active RBAC.
UPDATE sys_role
SET status = 0,
    deleted = id,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND deleted = 0
  AND role_code IN ('CODE_TEST', 'ROLE_TETST');

-- 2. Remove virtual security-center menu entries that do not have real admin features.
UPDATE sys_permission
SET status = 0,
    deleted = id,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND deleted = 0
  AND (
      permission_code LIKE 'admin:security%'
      OR permission_code LIKE 'admin:login-session%'
      OR permission_code LIKE 'admin:jwt-key%'
      OR permission_code LIKE 'admin:api-access%'
      OR permission_code LIKE 'admin:operation-audit%'
      OR permission_code LIKE 'security:%'
  );

UPDATE sys_menu
SET visible = 0,
    status = 0,
    deleted = id,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND deleted = 0
  AND (
      menu_code LIKE 'admin_security%'
      OR menu_code LIKE 'security%'
      OR route_path = '/security'
      OR route_path LIKE '/security/%'
      OR component_path LIKE 'security/%'
  );

-- 3. Normalize catalog/menu metadata used by dynamic routes.
UPDATE sys_menu
SET permission_code = NULL,
    component_path = NULL,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND deleted = 0
  AND menu_type = 'CATALOG'
  AND menu_code IN ('system', 'monitor', 'merchant', 'base', 'permission');

-- 4. Restore the admin dashboard menu after historical RBAC cleanup scripts hid legacy rows.
INSERT INTO sys_menu (
    id,
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
    sort_no,
    status,
    deleted
)
SELECT 200, 1, 0, 'admin_home_catalog_v3', '首页', 'CATALOG', '/', NULL, NULL, 'House', 1, 1, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.app_id = 1 AND m.id = 200
);

INSERT INTO sys_menu (
    id,
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
    sort_no,
    status,
    deleted
)
SELECT 201, 1, 200, 'admin_dashboard_v3', '工作台', 'MENU', '/dashboard', 'dashboard', 'dashboard:view', 'House', 1, 2, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.app_id = 1 AND m.id = 201
);

UPDATE sys_menu
SET menu_name = '首页',
    menu_type = 'CATALOG',
    route_path = '/',
    component_path = NULL,
    permission_code = NULL,
    icon = 'House',
    visible = 1,
    sort_no = 1,
    status = 1,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 200;

UPDATE sys_menu
SET parent_id = 200,
    menu_name = '工作台',
    menu_type = 'MENU',
    route_path = '/dashboard',
    component_path = 'dashboard',
    permission_code = 'dashboard:view',
    icon = 'House',
    visible = 1,
    sort_no = 2,
    status = 1,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 201;

UPDATE sys_menu
SET visible = 0,
    status = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 1
  AND deleted = 0;

INSERT INTO sys_permission (
    id,
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
SELECT 200, 1, 201, 'dashboard:view', '工作台查看', 'MENU', 'GET', '/admin/auth/me', 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p WHERE p.app_id = 1 AND p.id = 200
);

UPDATE sys_permission
SET menu_id = 201,
    permission_code = 'dashboard:view',
    permission_name = '工作台查看',
    permission_type = 'MENU',
    resource_method = 'GET',
    resource_path = '/admin/auth/me',
    status = 1,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id = 200;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 1, r.id, m.id, 0
FROM sys_role r
JOIN sys_menu m ON m.app_id = r.app_id AND m.id IN (200, 201)
WHERE r.app_id = 1
  AND r.status = 1
  AND r.deleted = 0
  AND m.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu rm
      WHERE rm.app_id = r.app_id
        AND rm.role_id = r.id
        AND rm.menu_id = m.id
        AND rm.deleted = 0
  );

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 1, r.id, p.id, 0
FROM sys_role r
JOIN sys_permission p ON p.app_id = r.app_id AND p.permission_code = 'dashboard:view' AND p.deleted = 0
WHERE r.app_id = 1
  AND r.status = 1
  AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.app_id = r.app_id
        AND rp.role_id = r.id
        AND rp.permission_id = p.id
        AND rp.deleted = 0
  );

-- 5. Ensure each visible menu permission_code exists in sys_permission.
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
SELECT m.app_id,
       m.id,
       m.permission_code,
       CONCAT(m.menu_name, '查看'),
       'MENU',
       NULL,
       NULL,
       1,
       0
FROM sys_menu m
LEFT JOIN sys_permission p
       ON p.app_id = m.app_id
      AND p.permission_code = m.permission_code
      AND p.deleted = 0
WHERE m.app_id = 1
  AND m.deleted = 0
  AND m.visible = 1
  AND m.status = 1
  AND m.permission_code IS NOT NULL
  AND m.permission_code <> ''
  AND p.id IS NULL;

-- 6. Ensure system monitor API/button permissions exist.
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
SELECT 1, m.id, 'system:online:forceLogout', '在线用户强退', 'BUTTON', 'DELETE', '/admin/monitor/online/*', 1, 0
FROM sys_menu m
WHERE m.app_id = 1 AND m.menu_code = 'monitor_online' AND m.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission p
      WHERE p.app_id = 1 AND p.permission_code = 'system:online:forceLogout' AND p.deleted = 0
  );

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
SELECT 1, m.id, code.permission_code, code.permission_name, 'BUTTON', code.resource_method, code.resource_path, 1, 0
FROM sys_menu m
JOIN (
    SELECT 'system:user:changeStatus' AS permission_code, '用户状态切换' AS permission_name, 'POST' AS resource_method, '/admin/system/users/status' AS resource_path
    UNION ALL SELECT 'system:role:changeStatus', '角色状态切换', 'POST', '/admin/system/roles/status'
    UNION ALL SELECT 'system:dept:query', '部门详情', 'GET', '/admin/system/dept/*'
    UNION ALL SELECT 'system:dept:export', '部门导出', 'GET', '/admin/system/dept/export'
    UNION ALL SELECT 'system:post:query', '岗位详情', 'GET', '/admin/system/post/*'
    UNION ALL SELECT 'system:post:export', '岗位导出', 'GET', '/admin/system/post/export'
    UNION ALL SELECT 'system:dict:query', '字典详情', 'POST', '/admin/system/dicts/types/search'
    UNION ALL SELECT 'system:dict:remove', '字典删除', 'DELETE', '/admin/system/dicts/types/*'
    UNION ALL SELECT 'system:dict:export', '字典导出', 'POST', '/admin/system/dicts/types/export'
    UNION ALL SELECT 'system:dict:refresh', '字典刷新缓存', 'POST', '/admin/system/dicts/refresh-cache'
    UNION ALL SELECT 'system:dictData:list', '字典数据查询', 'POST', '/admin/system/dicts/data/search'
    UNION ALL SELECT 'system:dictData:query', '字典数据详情', 'GET', '/admin/system/dicts/data/**'
    UNION ALL SELECT 'system:dictData:add', '字典数据新增', 'POST', '/admin/system/dicts/data'
    UNION ALL SELECT 'system:dictData:edit', '字典数据修改', 'PUT', '/admin/system/dicts/data/**'
    UNION ALL SELECT 'system:dictData:remove', '字典数据删除', 'DELETE', '/admin/system/dicts/data/**'
    UNION ALL SELECT 'system:dictData:export', '字典数据导出', 'POST', '/admin/system/dicts/data/export'
    UNION ALL SELECT 'system:config:query', '参数详情', 'GET', '/admin/system/configs/*'
    UNION ALL SELECT 'system:config:remove', '参数删除', 'DELETE', '/admin/system/configs/*'
    UNION ALL SELECT 'system:config:export', '参数导出', 'POST', '/admin/system/configs/export'
    UNION ALL SELECT 'system:config:refresh', '参数刷新缓存', 'POST', '/admin/system/configs/refresh-cache'
    UNION ALL SELECT 'system:oper-log:list', '操作日志查询', 'POST', '/admin/system/oper-logs/search'
    UNION ALL SELECT 'merchant:key:view', '商户密钥查看', 'GET', '/admin/merchants/*/keys'
    UNION ALL SELECT 'merchant:platform-payload-key:view', '平台请求密钥查看', 'POST', '/admin/merchants/*/platform-payload-key/rotate'
    UNION ALL SELECT 'merchant:response-key:view', '商户响应公钥查看', 'PUT', '/admin/merchants/*/response-key'
    UNION ALL SELECT 'system:cache:query' AS permission_code, '缓存详情查询' AS permission_name, 'GET' AS resource_method, '/admin/monitor/cache/keys' AS resource_path
    UNION ALL SELECT 'system:cache:clear', '缓存删除', 'DELETE', '/admin/monitor/cache/key'
) code
WHERE m.app_id = 1
  AND (
      (m.menu_code = 'system_user' AND code.permission_code LIKE 'system:user:%')
      OR (m.menu_code = 'system_role' AND code.permission_code IN ('system:role:changeStatus'))
      OR (m.menu_code = 'system_dept' AND code.permission_code LIKE 'system:dept:%')
      OR (m.menu_code = 'system_post' AND code.permission_code LIKE 'system:post:%')
      OR (m.menu_code = 'system_dict' AND (code.permission_code LIKE 'system:dict:%' OR code.permission_code LIKE 'system:dictData:%'))
      OR (m.menu_code = 'system_config' AND code.permission_code LIKE 'system:config:%')
      OR (m.menu_code = 'system_oper_log' AND code.permission_code = 'system:oper-log:list')
      OR (m.menu_code = 'monitor_cache' AND code.permission_code LIKE 'system:cache:%')
      OR (m.menu_code = 'merchant_info' AND code.permission_code IN ('merchant:key:view', 'merchant:platform-payload-key:view', 'merchant:response-key:view'))
  )
  AND m.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission p
      WHERE p.app_id = 1 AND p.permission_code = code.permission_code AND p.deleted = 0
  );

UPDATE sys_menu
SET menu_type = 'MENU',
    route_path = '/monitor/datasource',
    component_path = 'monitor/datasource/index',
    external_link = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND menu_code = 'monitor_datasource'
  AND deleted = 0;

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
SELECT 1, m.id, code.permission_code, code.permission_name, 'BUTTON', code.resource_method, code.resource_path, 1, 0
FROM sys_menu m
JOIN (
    SELECT 'monitor:datasource:view' AS permission_code, '数据源监控查看' AS permission_name, NULL AS resource_method, NULL AS resource_path
    UNION ALL SELECT 'monitor:datasource:export', '数据源监控导出', 'GET', '/admin/monitor/datasource/export'
    UNION ALL SELECT 'monitor:rocketmq:view', 'RocketMQ 控制台查看', NULL, NULL
    UNION ALL SELECT 'monitor:nacos:view', 'Nacos 控制台查看', NULL, NULL
    UNION ALL SELECT 'monitor:job:query' AS permission_code, '任务详情' AS permission_name, 'POST' AS resource_method, '/admin/monitor/job/search' AS resource_path
    UNION ALL SELECT 'monitor:job:add', '任务新增', 'POST', '/admin/monitor/job'
    UNION ALL SELECT 'monitor:job:edit', '任务修改', 'PUT', '/admin/monitor/job/*'
    UNION ALL SELECT 'monitor:job:remove', '任务删除', 'DELETE', '/admin/monitor/job/*'
    UNION ALL SELECT 'monitor:job:run', '任务手动执行', 'POST', '/admin/monitor/job/*/trigger'
    UNION ALL SELECT 'monitor:job:start', '任务启用', 'PUT', '/admin/monitor/job/*/status'
    UNION ALL SELECT 'monitor:job:stop', '任务停用', 'PUT', '/admin/monitor/job/*/status'
    UNION ALL SELECT 'monitor:jobLog:query', '任务日志详情', 'POST', '/admin/monitor/job-log/search'
    UNION ALL SELECT 'monitor:jobLog:remove', '任务日志删除', 'DELETE', '/admin/monitor/job-log/*'
    UNION ALL SELECT 'monitor:jobLog:clean', '任务日志清空', 'DELETE', '/admin/monitor/job-log/clean'
    UNION ALL SELECT 'monitor:jobLog:export', '任务日志导出', 'GET', '/admin/monitor/job-log/export'
    UNION ALL SELECT 'monitor:jobNode:query', '任务节点详情', 'GET', '/admin/monitor/job-node/list'
    UNION ALL SELECT 'monitor:jobNode:refresh', '任务节点刷新', 'GET', '/admin/monitor/job-node/list'
) code
WHERE m.app_id = 1
  AND (
      (m.menu_code = 'monitor_datasource' AND code.permission_code IN ('monitor:datasource:view', 'monitor:datasource:export'))
      OR (m.menu_code = 'monitor_rocketmq' AND code.permission_code = 'monitor:rocketmq:view')
      OR (m.menu_code = 'monitor_nacos' AND code.permission_code = 'monitor:nacos:view')
      OR (m.menu_code = 'monitor_job' AND code.permission_code LIKE 'monitor:job:%')
      OR (m.menu_code = 'monitor_job_log' AND code.permission_code LIKE 'monitor:jobLog:%')
      OR (m.menu_code = 'monitor_job_node' AND code.permission_code LIKE 'monitor:jobNode:%')
  )
  AND m.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission p
      WHERE p.app_id = 1 AND p.permission_code = code.permission_code AND p.deleted = 0
  );

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
    sort_no,
    status,
    deleted
)
SELECT 1,
       m.id,
       code.menu_code,
       code.menu_name,
       'BUTTON',
       NULL,
       NULL,
       code.permission_code,
       NULL,
       0,
       code.sort_no,
       1,
       0
FROM sys_menu m
JOIN (
    SELECT 'monitor_job_query' AS menu_code, '任务详情' AS menu_name, 'monitor:job:query' AS permission_code, 1 AS sort_no
    UNION ALL SELECT 'monitor_job_add', '任务新增', 'monitor:job:add', 2
    UNION ALL SELECT 'monitor_job_edit', '任务修改', 'monitor:job:edit', 3
    UNION ALL SELECT 'monitor_job_remove', '任务删除', 'monitor:job:remove', 4
    UNION ALL SELECT 'monitor_job_run', '手动执行', 'monitor:job:run', 5
    UNION ALL SELECT 'monitor_job_start', '任务启用', 'monitor:job:start', 6
    UNION ALL SELECT 'monitor_job_stop', '任务停用', 'monitor:job:stop', 7
    UNION ALL SELECT 'monitor_job_log_query', '日志详情', 'monitor:jobLog:query', 1
    UNION ALL SELECT 'monitor_job_node_query', '节点详情', 'monitor:jobNode:query', 1
    UNION ALL SELECT 'monitor_job_node_refresh', '节点刷新', 'monitor:jobNode:refresh', 2
    UNION ALL SELECT 'monitor_datasource_export', '数据源监控导出', 'monitor:datasource:export', 1
) code
WHERE m.app_id = 1
  AND (
      (m.menu_code = 'monitor_job' AND code.permission_code LIKE 'monitor:job:%')
      OR (m.menu_code = 'monitor_job_log' AND code.permission_code LIKE 'monitor:jobLog:%')
      OR (m.menu_code = 'monitor_job_node' AND code.permission_code LIKE 'monitor:jobNode:%')
      OR (m.menu_code = 'monitor_datasource' AND code.permission_code = 'monitor:datasource:export')
  )
  AND m.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu child
      WHERE child.app_id = 1
        AND child.permission_code = code.permission_code
        AND child.deleted = 0
  );

-- 5. Align known monitor permission resource metadata.
UPDATE sys_permission p
JOIN sys_menu m ON m.id = p.menu_id AND m.app_id = p.app_id AND m.deleted = 0
SET p.resource_method = CASE p.permission_code
        WHEN 'monitor:online:list' THEN 'GET'
        WHEN 'monitor:server:list' THEN 'GET'
        WHEN 'monitor:cache:list' THEN 'GET'
        WHEN 'system:online:list' THEN 'GET'
        WHEN 'system:server:list' THEN 'GET'
        WHEN 'system:cache:list' THEN 'GET'
        WHEN 'system:cache:query' THEN 'GET'
        WHEN 'system:cache:clear' THEN 'DELETE'
        WHEN 'system:online:forceLogout' THEN 'DELETE'
        WHEN 'monitor:job:list' THEN 'POST'
        WHEN 'monitor:jobLog:list' THEN 'POST'
        WHEN 'monitor:jobNode:list' THEN 'GET'
        WHEN 'monitor:job:query' THEN 'POST'
        WHEN 'monitor:job:add' THEN 'POST'
        WHEN 'monitor:job:edit' THEN 'PUT'
        WHEN 'monitor:job:remove' THEN 'DELETE'
        WHEN 'monitor:job:run' THEN 'POST'
        WHEN 'monitor:job:start' THEN 'PUT'
        WHEN 'monitor:job:stop' THEN 'PUT'
        WHEN 'monitor:jobLog:query' THEN 'POST'
        WHEN 'monitor:jobNode:query' THEN 'GET'
        WHEN 'monitor:jobNode:refresh' THEN 'GET'
        WHEN 'monitor:datasource:export' THEN 'GET'
        ELSE p.resource_method
    END,
    p.resource_path = CASE p.permission_code
        WHEN 'monitor:online:list' THEN '/admin/monitor/online/list'
        WHEN 'monitor:server:list' THEN '/admin/monitor/server'
        WHEN 'monitor:cache:list' THEN '/admin/monitor/cache/**'
        WHEN 'system:online:list' THEN '/admin/monitor/online/list'
        WHEN 'system:server:list' THEN '/admin/monitor/server'
        WHEN 'system:cache:list' THEN '/admin/monitor/cache/info'
        WHEN 'system:cache:query' THEN '/admin/monitor/cache/keys'
        WHEN 'system:cache:clear' THEN '/admin/monitor/cache/key'
        WHEN 'system:online:forceLogout' THEN '/admin/monitor/online/*'
        WHEN 'monitor:job:list' THEN '/admin/monitor/job/search'
        WHEN 'monitor:jobLog:list' THEN '/admin/monitor/job-log/search'
        WHEN 'monitor:jobNode:list' THEN '/admin/monitor/job-node/list'
        WHEN 'monitor:job:query' THEN '/admin/monitor/job/search'
        WHEN 'monitor:job:add' THEN '/admin/monitor/job'
        WHEN 'monitor:job:edit' THEN '/admin/monitor/job/*'
        WHEN 'monitor:job:remove' THEN '/admin/monitor/job/*'
        WHEN 'monitor:job:run' THEN '/admin/monitor/job/*/trigger'
        WHEN 'monitor:job:start' THEN '/admin/monitor/job/*/status'
        WHEN 'monitor:job:stop' THEN '/admin/monitor/job/*/status'
        WHEN 'monitor:jobLog:query' THEN '/admin/monitor/job-log/search'
        WHEN 'monitor:jobNode:query' THEN '/admin/monitor/job-node/list'
        WHEN 'monitor:jobNode:refresh' THEN '/admin/monitor/job-node/list'
        WHEN 'monitor:datasource:export' THEN '/admin/monitor/datasource/export'
        ELSE p.resource_path
    END,
    p.status = 1
WHERE p.app_id = 1
  AND p.deleted = 0
  AND p.permission_code IN (
      'monitor:online:list',
      'monitor:server:list',
      'monitor:cache:list',
      'system:online:list',
      'system:server:list',
      'system:cache:list',
      'system:cache:query',
      'system:cache:clear',
      'system:online:forceLogout',
      'monitor:job:list',
      'monitor:jobLog:list',
      'monitor:jobNode:list',
      'monitor:job:query',
      'monitor:job:add',
      'monitor:job:edit',
      'monitor:job:remove',
      'monitor:job:run',
      'monitor:job:start',
      'monitor:job:stop',
      'monitor:jobLog:query',
      'monitor:jobNode:query',
      'monitor:jobNode:refresh',
      'monitor:datasource:export'
  );

-- 6. Migrate historical legacy permission codes to current canonical RBAC codes.
INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT old_p.app_id,
       rp.role_id,
       new_p.id,
       0
FROM sys_permission old_p
JOIN sys_role_permission rp
  ON rp.permission_id = old_p.id
 AND rp.app_id = old_p.app_id
 AND rp.deleted = 0
JOIN sys_permission new_p
  ON new_p.app_id = old_p.app_id
 AND new_p.deleted = 0
 AND (
      (old_p.permission_code = 'monitor:cache:delete' AND new_p.permission_code = 'system:cache:clear')
      OR (old_p.permission_code = 'monitor:job:trigger' AND new_p.permission_code = 'monitor:job:run')
      OR (old_p.permission_code = 'monitor:job:changeStatus' AND new_p.permission_code IN ('monitor:job:start', 'monitor:job:stop'))
 )
WHERE old_p.app_id = 1
  AND old_p.deleted = 0
  AND old_p.permission_code IN ('monitor:cache:delete', 'monitor:job:trigger', 'monitor:job:changeStatus');

UPDATE sys_permission
SET permission_code = 'system:user:resetPwd',
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND deleted = 0
  AND permission_code = 'system:user:reset-password'
  AND NOT EXISTS (
      SELECT 1
      FROM (
          SELECT id
          FROM sys_permission
          WHERE app_id = 1
            AND deleted = 0
            AND permission_code = 'system:user:resetPwd'
      ) existing_permission
  );

UPDATE sys_permission
SET permission_code = 'system:online:forceLogout',
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND deleted = 0
  AND permission_code = 'monitor:online:forceLogout'
  AND NOT EXISTS (
      SELECT 1
      FROM (
          SELECT id
          FROM sys_permission
          WHERE app_id = 1
            AND deleted = 0
            AND permission_code = 'system:online:forceLogout'
      ) existing_permission
  );

UPDATE sys_menu
SET menu_code = 'system_role_delete',
    permission_code = 'system:role:delete',
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND deleted = 0
  AND permission_code = 'system:role:remove';

UPDATE sys_menu
SET status = 0,
    visible = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND deleted = 0
  AND permission_code IN ('system:user:query', 'system:user:remove', 'system:user:export', 'system:role:query',
      'system:role:export', 'system:menu:query', 'system:menu:remove');

DELETE rp
FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id AND p.app_id = rp.app_id
WHERE rp.app_id = 1
  AND p.permission_code IN ('monitor:cache:delete', 'monitor:job:trigger', 'monitor:job:changeStatus', 'monitor:job:handler:list');

UPDATE sys_permission
SET status = 0,
    deleted = id,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND deleted = 0
  AND permission_code IN ('monitor:cache:delete', 'monitor:job:trigger', 'monitor:job:changeStatus', 'monitor:job:handler:list');

-- 7. Remove unreleased job log permissions and button menus.
DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.id = rm.menu_id AND m.app_id = rm.app_id
WHERE rm.app_id = 1
  AND m.permission_code IN ('monitor:jobLog:remove', 'monitor:jobLog:clean', 'monitor:jobLog:export');

DELETE rp
FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id AND p.app_id = rp.app_id
WHERE rp.app_id = 1
  AND p.permission_code IN ('monitor:jobLog:remove', 'monitor:jobLog:clean', 'monitor:jobLog:export');

DELETE FROM sys_menu
WHERE app_id = 1
  AND permission_code IN ('monitor:jobLog:remove', 'monitor:jobLog:clean', 'monitor:jobLog:export');

DELETE FROM sys_permission
WHERE app_id = 1
  AND permission_code IN ('monitor:jobLog:remove', 'monitor:jobLog:clean', 'monitor:jobLog:export');

-- 8. Ensure the admin operator role owns all active admin menus and permissions.
INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT r.app_id, r.id, m.id, 0
FROM sys_role r
JOIN sys_menu m ON m.app_id = r.app_id
WHERE r.app_id = 1
  AND r.role_code = 'ADMIN_OPERATOR'
  AND r.deleted = 0
  AND r.status = 1
  AND m.deleted = 0
  AND (m.visible = 1 OR m.menu_type = 'BUTTON')
  AND m.status = 1;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT r.app_id, r.id, p.id, 0
FROM sys_role r
JOIN sys_permission p ON p.app_id = r.app_id
WHERE r.app_id = 1
  AND r.role_code = 'ADMIN_OPERATOR'
  AND r.deleted = 0
  AND r.status = 1
  AND p.deleted = 0
  AND p.status = 1;

-- 9. Keep sys_user_role in sync with the actual login-account role relation.
INSERT IGNORE INTO sys_user_role (app_id, user_id, role_id, deleted)
SELECT ar.app_id, a.user_id, ar.role_id, 0
FROM sys_account_role ar
JOIN sys_account a ON a.id = ar.account_id
                  AND a.app_id = ar.app_id
                  AND a.deleted = 0
WHERE ar.app_id = 1
  AND ar.deleted = 0;

SET FOREIGN_KEY_CHECKS = 1;
