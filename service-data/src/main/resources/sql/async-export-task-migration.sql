-- 异步导出与下载中心第一批：任务、下载审计、管理端菜单权限和调度任务。
-- 执行前先运行 async-export-task-precheck.sql，执行后运行 postcheck；文件 Bucket 由部署流程创建。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS data_export_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    task_no VARCHAR(64) NOT NULL COMMENT '导出任务号',
    request_id VARCHAR(128) NOT NULL COMMENT '创建账号范围内的提交幂等键',
    task_type VARCHAR(64) NOT NULL COMMENT '任务类型',
    task_name VARCHAR(128) NOT NULL COMMENT '用户可见任务名称',
    source_service VARCHAR(64) NOT NULL COMMENT '创建任务的来源服务',
    creator_app_code VARCHAR(32) NOT NULL DEFAULT 'ADMIN' COMMENT '创建账号所属应用ADMIN/MERCHANT',
    creator_account_id BIGINT NOT NULL COMMENT '创建人账号ID',
    merchant_id VARCHAR(64) NULL COMMENT '商户任务所属商户号，管理任务为空',
    creator_account_name VARCHAR(128) NOT NULL COMMENT '创建人名称快照',
    locale VARCHAR(16) NOT NULL COMMENT '创建时语言标签',
    timezone VARCHAR(64) NOT NULL COMMENT '创建时时区',
    query_snapshot JSON NOT NULL COMMENT '来源查询条件冻结快照',
    data_scope_snapshot JSON NOT NULL COMMENT '服务端商户数据范围冻结快照',
    status VARCHAR(16) NOT NULL COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED/EXPIRED',
    progress_percent INT NOT NULL DEFAULT 0 COMMENT '任务进度0至100',
    processed_rows BIGINT NOT NULL DEFAULT 0 COMMENT '已写入数据行数',
    total_rows BIGINT NOT NULL DEFAULT 0 COMMENT '权威查询总行数',
    file_name VARCHAR(255) NULL COMMENT '成功文件名',
    content_type VARCHAR(128) NULL COMMENT '文件媒体类型',
    object_key VARCHAR(512) NULL COMMENT '私有Bucket对象键，不对浏览器返回',
    file_size_bytes BIGINT NULL COMMENT '文件字节数',
    file_sha256 CHAR(64) NULL COMMENT '文件SHA-256摘要',
    expires_at DATETIME(3) NULL COMMENT '文件到期时间',
    failure_code VARCHAR(128) NULL COMMENT '最近失败类型',
    failure_reason VARCHAR(500) NULL COMMENT '脱敏失败摘要',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '当前自动重试次数',
    max_retry_count INT NOT NULL COMMENT '任务创建时冻结的最大重试次数',
    next_retry_time DATETIME(3) NULL COMMENT '下次允许领取时间',
    lease_owner VARCHAR(192) NULL COMMENT '当前执行租约持有者',
    lease_until DATETIME(3) NULL COMMENT '当前执行租约到期时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '状态迁移CAS版本',
    started_at DATETIME(3) NULL COMMENT '首次开始时间',
    finished_at DATETIME(3) NULL COMMENT '成功或最终失败时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_export_task_no (task_no),
    UNIQUE KEY uk_data_export_task_request (creator_app_code, creator_account_id, request_id),
    KEY idx_data_export_task_dispatch (status, next_retry_time, lease_until, id),
    KEY idx_data_export_task_creator_time (creator_app_code, creator_account_id, merchant_id, create_time, id),
    KEY idx_data_export_task_expiry (status, expires_at, id),
    CONSTRAINT chk_data_export_task_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'EXPIRED')
        AND progress_percent BETWEEN 0 AND 100
        AND processed_rows >= 0 AND total_rows >= 0
        AND retry_count >= 0 AND max_retry_count > 0 AND version >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='异步导出任务';

CREATE TABLE IF NOT EXISTS data_export_download_audit (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    audit_no VARCHAR(64) NOT NULL COMMENT '下载审计号',
    task_no VARCHAR(64) NOT NULL COMMENT '导出任务号',
    operator_app_code VARCHAR(32) NOT NULL DEFAULT 'ADMIN' COMMENT '下载操作人所属应用ADMIN/MERCHANT',
    operator_account_id BIGINT NOT NULL COMMENT '下载操作人账号ID',
    merchant_id VARCHAR(64) NULL COMMENT '商户下载所属商户号，管理下载为空',
    operator_account_name VARCHAR(128) NULL COMMENT '下载操作人名称快照',
    client_ip VARCHAR(64) NULL COMMENT '浏览器连接来源IP',
    result_status VARCHAR(16) NOT NULL COMMENT 'REQUESTED/SUCCESS/FAILED',
    failure_reason VARCHAR(500) NULL COMMENT '脱敏失败摘要',
    file_sha256 CHAR(64) NULL COMMENT '下载文件SHA-256摘要',
    requested_at DATETIME(3) NOT NULL COMMENT '开始下载时间',
    downloaded_at DATETIME(3) NULL COMMENT '下载完成或失败时间',
    update_time DATETIME(3) NOT NULL COMMENT '状态更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_export_download_audit_no (audit_no),
    KEY idx_data_export_download_task_time (task_no, requested_at, id),
    KEY idx_data_export_download_operator_time (operator_app_code, operator_account_id, merchant_id, requested_at, id),
    CONSTRAINT chk_data_export_download_status CHECK (
        result_status IN ('REQUESTED', 'SUCCESS', 'FAILED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导出文件下载审计';

START TRANSACTION;

SET @admin_app_id = (
    SELECT id FROM sys_app WHERE app_code = 'ADMIN' AND deleted = 0 ORDER BY id LIMIT 1
);
SET @home_catalog_id = (
    SELECT id FROM sys_menu
    WHERE app_id = @admin_app_id AND menu_code = 'admin_home_catalog_v3'
      AND menu_type = 'CATALOG' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT @admin_app_id, @home_catalog_id, 'admin_export_task_center_v1', '下载中心', 'MENU',
       '/operations/export-tasks', 'operations/export-task',
       'operations:export-task:list', 'Download', 1, 0, 0, 4, 1, 0
WHERE @admin_app_id IS NOT NULL AND @home_catalog_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu
      WHERE app_id = @admin_app_id AND menu_code = 'admin_export_task_center_v1' AND deleted = 0
  );

UPDATE sys_menu
SET parent_id = @home_catalog_id, menu_name = BINARY '下载中心', menu_type = 'MENU',
    route_path = '/operations/export-tasks', component_path = 'operations/export-task',
    permission_code = 'operations:export-task:list', icon = 'Download', visible = 1,
    keep_alive = 0, external_link = 0, sort_no = 4, status = 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = @admin_app_id AND menu_code = 'admin_export_task_center_v1' AND deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, permission_code,
    visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT @admin_app_id, page.id, item.menu_code, item.menu_name, 'BUTTON', item.permission_code,
       0, 0, 0, item.sort_no, 1, 0
FROM sys_menu page
JOIN (
    SELECT 'admin_export_task_detail_v1' menu_code, '查看导出任务详情' menu_name,
           'operations:export-task:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'admin_export_task_download_v1', '下载导出文件',
           'operations:export-task:download', 2
    UNION ALL SELECT 'admin_export_task_retry_v1', '重试导出任务',
           'operations:export-task:retry', 3
) item
WHERE page.app_id = @admin_app_id AND page.menu_code = 'admin_export_task_center_v1'
  AND page.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = @admin_app_id AND existing.menu_code = item.menu_code AND existing.deleted = 0
  );

UPDATE sys_menu menu
JOIN (
    SELECT 'admin_export_task_detail_v1' menu_code, '查看导出任务详情' menu_name,
           'operations:export-task:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'admin_export_task_download_v1', '下载导出文件',
           'operations:export-task:download', 2
    UNION ALL SELECT 'admin_export_task_retry_v1', '重试导出任务',
           'operations:export-task:retry', 3
) item ON item.menu_code = menu.menu_code
JOIN sys_menu page ON page.app_id = menu.app_id
                  AND page.menu_code = 'admin_export_task_center_v1' AND page.deleted = 0
SET menu.parent_id = page.id, menu.menu_name = BINARY item.menu_name, menu.menu_type = 'BUTTON',
    menu.route_path = NULL, menu.component_path = NULL, menu.permission_code = item.permission_code,
    menu.icon = NULL, menu.visible = 0, menu.keep_alive = 0, menu.external_link = 0,
    menu.sort_no = item.sort_no, menu.status = 1, menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.app_id = @admin_app_id AND menu.deleted = 0;

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT @admin_app_id, menu.id, item.permission_code, item.permission_name,
       item.permission_type, item.resource_method, item.resource_path, item.description, 1, 0
FROM (
    SELECT 'admin_export_task_center_v1' menu_code,
           'operations:export-task:list' permission_code, '导出任务查询' permission_name,
           'MENU' permission_type, 'POST' resource_method,
           '/admin/operations/export-tasks/search' resource_path,
           '查询当前管理账号创建的异步导出任务' description
    UNION ALL SELECT 'admin_export_task_detail_v1',
           'operations:export-task:detail', '导出任务详情', 'BUTTON', 'GET',
           '/admin/operations/export-tasks/*', '查看当前管理账号创建的导出任务详情'
    UNION ALL SELECT 'admin_export_task_download_v1',
           'operations:export-task:download', '导出文件下载', 'BUTTON', 'GET',
           '/admin/operations/export-tasks/*/download', '下载当前管理账号拥有且未过期的导出文件'
    UNION ALL SELECT 'admin_export_task_retry_v1',
           'operations:export-task:retry', '导出任务重试', 'BUTTON', 'POST',
           '/admin/operations/export-tasks/*/retry', '按版本条件人工重试失败导出任务'
) item
JOIN sys_menu menu ON menu.app_id = @admin_app_id
                  AND menu.menu_code = item.menu_code AND menu.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission existing
    WHERE existing.app_id = @admin_app_id
      AND existing.permission_code = item.permission_code AND existing.deleted = 0
);

UPDATE sys_permission permission
JOIN (
    SELECT 'admin_export_task_center_v1' menu_code,
           'operations:export-task:list' permission_code, '导出任务查询' permission_name,
           'MENU' permission_type, 'POST' resource_method,
           '/admin/operations/export-tasks/search' resource_path,
           '查询当前管理账号创建的异步导出任务' description
    UNION ALL SELECT 'admin_export_task_detail_v1',
           'operations:export-task:detail', '导出任务详情', 'BUTTON', 'GET',
           '/admin/operations/export-tasks/*', '查看当前管理账号创建的导出任务详情'
    UNION ALL SELECT 'admin_export_task_download_v1',
           'operations:export-task:download', '导出文件下载', 'BUTTON', 'GET',
           '/admin/operations/export-tasks/*/download', '下载当前管理账号拥有且未过期的导出文件'
    UNION ALL SELECT 'admin_export_task_retry_v1',
           'operations:export-task:retry', '导出任务重试', 'BUTTON', 'POST',
           '/admin/operations/export-tasks/*/retry', '按版本条件人工重试失败导出任务'
) item ON item.permission_code = permission.permission_code
JOIN sys_menu menu ON menu.app_id = permission.app_id
                  AND menu.menu_code = item.menu_code AND menu.deleted = 0
SET permission.menu_id = menu.id, permission.permission_name = BINARY item.permission_name,
    permission.permission_type = item.permission_type,
    permission.resource_method = item.resource_method, permission.resource_path = item.resource_path,
    permission.description = BINARY item.description, permission.status = 1,
    permission.updated_at = CURRENT_TIMESTAMP(3)
WHERE permission.app_id = @admin_app_id AND permission.deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, created_by, deleted)
SELECT @admin_app_id, role.id, menu.id, NULL, 0
FROM sys_role role
JOIN sys_menu menu ON menu.app_id = role.app_id AND menu.deleted = 0
WHERE role.app_id = @admin_app_id AND role.role_code = 'SUPER_ADMIN' AND role.deleted = 0
  AND menu.menu_code IN (
      'admin_export_task_center_v1', 'admin_export_task_detail_v1',
      'admin_export_task_download_v1', 'admin_export_task_retry_v1'
  );

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, created_by, deleted)
SELECT @admin_app_id, role.id, permission.id, NULL, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id AND permission.deleted = 0
WHERE role.app_id = @admin_app_id AND role.role_code = 'SUPER_ADMIN' AND role.deleted = 0
  AND permission.permission_code IN (
      'operations:export-task:list', 'operations:export-task:detail',
      'operations:export-task:download', 'operations:export-task:retry'
  );

INSERT INTO sys_job_task (
    job_code, job_name, job_group, handler_code, cron_expression, scheduler_mode, trigger_mode,
    execute_mode, route_strategy, misfire_strategy, timeout_seconds, retry_count,
    retry_interval_seconds, allow_concurrent, params, status, description, next_trigger_time,
    version, deleted, create_by, create_time, update_by, update_time
)
SELECT 'DATA_EXPORT_TASK_DISPATCH', '异步导出任务调度', 'data',
       'dataExportTaskDispatch', '*/10 * * * * ?', 'DISTRIBUTED', 'CRON',
       'SYNC', 'LOCAL', 'FIRE_ONCE', 90, 2, 30, 0, '{}', 'ENABLED',
       '每十秒触发 Data 有界领取导出任务并清理到期文件；系统任务禁止删除或更换处理器。',
       DATE_ADD(NOW(3), INTERVAL 10 SECOND), 0, 0, 'system', NOW(3), 'system', NOW(3)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job_task WHERE job_code = 'DATA_EXPORT_TASK_DISPATCH' AND deleted = 0
);

UPDATE sys_job_task
SET job_name = '异步导出任务调度', job_group = 'data',
    handler_code = 'dataExportTaskDispatch', scheduler_mode = 'DISTRIBUTED',
    execute_mode = 'SYNC', allow_concurrent = 0,
    description = '每十秒触发 Data 有界领取导出任务并清理到期文件；系统任务禁止删除或更换处理器。',
    update_by = 'system-migration', update_time = NOW(3)
WHERE job_code = 'DATA_EXPORT_TASK_DISPATCH' AND deleted = 0;

COMMIT;
