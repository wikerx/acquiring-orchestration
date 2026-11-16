-- 统一运营待办第一批发布后核验；所有查询均应返回符合注释的结果。

SELECT table_name, table_rows
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('admin_operational_work_item', 'admin_operational_work_item_history')
ORDER BY table_name;

SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('admin_operational_work_item', 'admin_operational_work_item_history')
GROUP BY table_name, index_name
ORDER BY table_name, index_name;

-- 应返回首页目录下、紧跟工作台的 1 个页面和 7 个按钮。
SELECT menu.menu_code, menu.menu_name, menu.menu_type, menu.route_path, menu.component_path,
       menu.permission_code, menu.sort_no, parent.menu_code AS parent_menu_code,
       menu.status, menu.deleted
FROM sys_menu menu
LEFT JOIN sys_menu parent ON parent.id = menu.parent_id
WHERE menu.app_id = (SELECT id FROM sys_app WHERE app_code = 'ADMIN' AND deleted = 0 ORDER BY id LIMIT 1)
  AND menu.menu_code LIKE 'admin_operational_work_item%_v1'
  AND menu.deleted = 0
ORDER BY menu.menu_type, menu.sort_no, menu.menu_code;

-- 应返回 0；第一批不展示单独的运营中心目录。
SELECT COUNT(*) AS active_operations_catalog_count
FROM sys_menu
WHERE app_id = (SELECT id FROM sys_app WHERE app_code = 'ADMIN' AND deleted = 0 ORDER BY id LIMIT 1)
  AND menu_code = 'admin_operations_catalog_v1' AND status = 1 AND deleted = 0;

-- 应恰好返回 8 个启用权限，其中完成人工待办必须是独立权限。
SELECT permission_code, permission_type, resource_method, resource_path, status, deleted
FROM sys_permission
WHERE app_id = (SELECT id FROM sys_app WHERE app_code = 'ADMIN' AND deleted = 0 ORDER BY id LIMIT 1)
  AND permission_code LIKE 'operations:work-item:%'
ORDER BY permission_code;

SELECT COUNT(*) AS complete_permission_count
FROM sys_permission
WHERE app_id = (SELECT id FROM sys_app WHERE app_code = 'ADMIN' AND deleted = 0 ORDER BY id LIMIT 1)
  AND permission_code = 'operations:work-item:complete'
  AND resource_method = 'POST'
  AND resource_path = '/admin/operations/work-items/*/complete'
  AND status = 1 AND deleted = 0;

-- 默认授权只能命中 SUPER_ADMIN。
SELECT role.role_code, COUNT(DISTINCT permission.permission_code) AS permission_count
FROM sys_role_permission relation
JOIN sys_role role ON role.id = relation.role_id AND role.deleted = 0
JOIN sys_permission permission ON permission.id = relation.permission_id AND permission.deleted = 0
WHERE relation.deleted = 0
  AND permission.permission_code LIKE 'operations:work-item:%'
GROUP BY role.role_code
ORDER BY role.role_code;

-- 应返回唯一一条启用、禁止并发的系统任务。
SELECT job_code, handler_code, cron_expression, scheduler_mode, execute_mode,
       allow_concurrent, status, deleted
FROM sys_job_task
WHERE job_code = 'OPERATIONAL_WORK_ITEM_SYNC';

-- 应返回 0；发现重复来源或重复幂等请求时禁止发布。
SELECT source_type, source_key, COUNT(*) AS duplicate_count
FROM admin_operational_work_item
GROUP BY source_type, source_key
HAVING COUNT(*) > 1;

SELECT request_id, COUNT(*) AS duplicate_count
FROM admin_operational_work_item_history
GROUP BY request_id
HAVING COUNT(*) > 1;

-- 应返回 1，确认历史轨迹约束允许人工完成动作。
SELECT COUNT(*) AS complete_action_constraint_count
FROM information_schema.check_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name = 'chk_admin_operational_work_item_history_action'
  AND check_clause LIKE '%COMPLETE%';
