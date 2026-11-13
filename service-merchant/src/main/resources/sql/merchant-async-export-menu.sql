-- 商户系统下载中心页面、详情、下载、重试权限及现有交易/退款导出路径迁移。
SET NAMES utf8mb4;

START TRANSACTION;

SET @merchant_app_id = (
    SELECT id FROM sys_app WHERE app_code = 'MERCHANT' AND deleted = 0 ORDER BY id LIMIT 1
);
SET @merchant_system_catalog_id = (
    SELECT id FROM sys_menu
    WHERE app_id = @merchant_app_id AND menu_code = 'merchant_system_catalog_v1'
      AND menu_type = 'CATALOG' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT @merchant_app_id, @merchant_system_catalog_id, 'merchant_export_task_center_v1', '下载中心', 'MENU',
       '/operations/export-tasks', 'operations/export-task',
       'merchant:operations:export-task:list', 'Download', 1, 0, 0, 96, 1, 0
WHERE @merchant_app_id IS NOT NULL AND @merchant_system_catalog_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu
      WHERE app_id = @merchant_app_id
        AND menu_code = 'merchant_export_task_center_v1' AND deleted = 0
  );

UPDATE sys_menu
SET parent_id = @merchant_system_catalog_id, menu_name = BINARY '下载中心', menu_type = 'MENU',
    route_path = '/operations/export-tasks', component_path = 'operations/export-task',
    permission_code = 'merchant:operations:export-task:list', icon = 'Download',
    visible = 1, keep_alive = 0, external_link = 0, sort_no = 96, status = 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = @merchant_app_id
  AND menu_code = 'merchant_export_task_center_v1' AND deleted = 0
  AND @merchant_system_catalog_id IS NOT NULL;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, permission_code,
    visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT @merchant_app_id, page.id, item.menu_code, item.menu_name, 'BUTTON', item.permission_code,
       0, 0, 0, item.sort_no, 1, 0
FROM sys_menu page
JOIN (
    SELECT 'merchant_export_task_detail_v1' menu_code, '查看导出任务详情' menu_name,
           'merchant:operations:export-task:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'merchant_export_task_download_v1', '下载导出文件',
           'merchant:operations:export-task:download', 2
    UNION ALL SELECT 'merchant_export_task_retry_v1', '重试导出任务',
           'merchant:operations:export-task:retry', 3
) item
WHERE page.app_id = @merchant_app_id
  AND page.menu_code = 'merchant_export_task_center_v1' AND page.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = @merchant_app_id
        AND existing.menu_code = item.menu_code AND existing.deleted = 0
  );

UPDATE sys_menu menu
JOIN (
    SELECT 'merchant_export_task_detail_v1' menu_code, '查看导出任务详情' menu_name,
           'merchant:operations:export-task:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'merchant_export_task_download_v1', '下载导出文件',
           'merchant:operations:export-task:download', 2
    UNION ALL SELECT 'merchant_export_task_retry_v1', '重试导出任务',
           'merchant:operations:export-task:retry', 3
) item ON item.menu_code = menu.menu_code
JOIN sys_menu page ON page.app_id = menu.app_id
                  AND page.menu_code = 'merchant_export_task_center_v1' AND page.deleted = 0
SET menu.parent_id = page.id, menu.menu_name = BINARY item.menu_name, menu.menu_type = 'BUTTON',
    menu.route_path = NULL, menu.component_path = NULL, menu.permission_code = item.permission_code,
    menu.icon = NULL, menu.visible = 0, menu.keep_alive = 0, menu.external_link = 0,
    menu.sort_no = item.sort_no, menu.status = 1, menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.app_id = @merchant_app_id AND menu.deleted = 0;

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT @merchant_app_id, menu.id, item.permission_code, item.permission_name,
       item.permission_type, item.resource_method, item.resource_path, item.description, 1, 0
FROM (
    SELECT 'merchant_export_task_center_v1' menu_code,
           'merchant:operations:export-task:list' permission_code, '导出任务查询' permission_name,
           'MENU' permission_type, 'POST' resource_method,
           '/merchant/operations/export-tasks/search' resource_path,
           '查询当前商户账号创建的异步导出任务' description
    UNION ALL SELECT 'merchant_export_task_detail_v1',
           'merchant:operations:export-task:detail', '导出任务详情', 'BUTTON', 'GET',
           '/merchant/operations/export-tasks/*', '查看当前商户账号拥有的导出任务详情'
    UNION ALL SELECT 'merchant_export_task_download_v1',
           'merchant:operations:export-task:download', '导出文件下载', 'BUTTON', 'GET',
           '/merchant/operations/export-tasks/*/download', '下载当前商户账号拥有且未过期的导出文件'
    UNION ALL SELECT 'merchant_export_task_retry_v1',
           'merchant:operations:export-task:retry', '导出任务重试', 'BUTTON', 'POST',
           '/merchant/operations/export-tasks/*/retry', '按版本条件人工重试当前商户账号失败的导出任务'
) item
JOIN sys_menu menu ON menu.app_id = @merchant_app_id
                  AND menu.menu_code = item.menu_code AND menu.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission existing
    WHERE existing.app_id = @merchant_app_id
      AND existing.permission_code = item.permission_code AND existing.deleted = 0
);

UPDATE sys_permission permission
JOIN (
    SELECT 'merchant_export_task_center_v1' menu_code,
           'merchant:operations:export-task:list' permission_code, '导出任务查询' permission_name,
           'MENU' permission_type, 'POST' resource_method,
           '/merchant/operations/export-tasks/search' resource_path,
           '查询当前商户账号创建的异步导出任务' description
    UNION ALL SELECT 'merchant_export_task_detail_v1',
           'merchant:operations:export-task:detail', '导出任务详情', 'BUTTON', 'GET',
           '/merchant/operations/export-tasks/*', '查看当前商户账号拥有的导出任务详情'
    UNION ALL SELECT 'merchant_export_task_download_v1',
           'merchant:operations:export-task:download', '导出文件下载', 'BUTTON', 'GET',
           '/merchant/operations/export-tasks/*/download', '下载当前商户账号拥有且未过期的导出文件'
    UNION ALL SELECT 'merchant_export_task_retry_v1',
           'merchant:operations:export-task:retry', '导出任务重试', 'BUTTON', 'POST',
           '/merchant/operations/export-tasks/*/retry', '按版本条件人工重试当前商户账号失败的导出任务'
) item ON item.permission_code = permission.permission_code
JOIN sys_menu menu ON menu.app_id = permission.app_id
                  AND menu.menu_code = item.menu_code AND menu.deleted = 0
SET permission.menu_id = menu.id, permission.permission_name = BINARY item.permission_name,
    permission.permission_type = item.permission_type,
    permission.resource_method = item.resource_method, permission.resource_path = item.resource_path,
    permission.description = BINARY item.description, permission.status = 1,
    permission.updated_at = CURRENT_TIMESTAMP(3)
WHERE permission.app_id = @merchant_app_id AND permission.deleted = 0;

UPDATE sys_permission
SET resource_path = '/merchant/operations/export-tasks/transaction-operations',
    description = BINARY '提交当前登录商户交易动作异步导出任务',
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = @merchant_app_id
  AND permission_code = 'merchant:transaction:order:export' AND deleted = 0;

UPDATE sys_permission
SET resource_path = '/merchant/operations/export-tasks/refunds',
    description = BINARY '提交当前登录商户退款异步导出任务',
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = @merchant_app_id
  AND permission_code = 'merchant:transaction:refund:export' AND deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT @merchant_app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_menu menu ON menu.app_id = role.app_id AND menu.deleted = 0
WHERE role.app_id = @merchant_app_id AND role.deleted = 0
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
  AND menu.menu_code IN (
      'merchant_export_task_center_v1', 'merchant_export_task_detail_v1',
      'merchant_export_task_download_v1', 'merchant_export_task_retry_v1'
  );

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT @merchant_app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id AND permission.deleted = 0
WHERE role.app_id = @merchant_app_id AND role.deleted = 0
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
  AND permission.permission_code IN (
      'merchant:operations:export-task:list', 'merchant:operations:export-task:detail',
      'merchant:operations:export-task:download', 'merchant:operations:export-task:retry'
  );

INSERT IGNORE INTO sys_merchant_menu_grant (
    merchant_id, app_id, menu_id, grant_source, status, created_at, updated_at, deleted
)
SELECT merchant.merchant_id, @merchant_app_id, menu.id, 'SYSTEM', 1,
       CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM base_merchant_info merchant
JOIN sys_menu menu ON menu.app_id = @merchant_app_id AND menu.deleted = 0
WHERE merchant.deleted = 0
  AND menu.menu_code IN (
      'merchant_export_task_center_v1', 'merchant_export_task_detail_v1',
      'merchant_export_task_download_v1', 'merchant_export_task_retry_v1'
  );

INSERT IGNORE INTO sys_merchant_permission_grant (
    merchant_id, app_id, permission_id, grant_source, status, created_at, updated_at, deleted
)
SELECT merchant.merchant_id, @merchant_app_id, permission.id, 'SYSTEM', 1,
       CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM base_merchant_info merchant
JOIN sys_permission permission ON permission.app_id = @merchant_app_id AND permission.deleted = 0
WHERE merchant.deleted = 0
  AND permission.permission_code IN (
      'merchant:operations:export-task:list', 'merchant:operations:export-task:detail',
      'merchant:operations:export-task:download', 'merchant:operations:export-task:retry'
  );

COMMIT;
