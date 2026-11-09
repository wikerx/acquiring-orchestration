-- 统一运营待办：运营元数据、处理轨迹、菜单权限与同步任务。
-- 来源业务事实仍由退款、结算、汇率、商户通知、渠道勾兑异常和清分原表维护。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS admin_operational_work_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    work_item_no VARCHAR(32) NOT NULL COMMENT '统一待办编号',
    source_type VARCHAR(48) NOT NULL COMMENT '来源类型',
    source_key VARCHAR(128) NOT NULL COMMENT '来源稳定业务键',
    work_status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/PROCESSING/CLOSED',
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'CRITICAL/HIGH/NORMAL/LOW',
    owner_account_id BIGINT NULL COMMENT '当前负责人管理账号ID',
    owner_account_name VARCHAR(128) NULL COMMENT '当前负责人名称快照',
    sla_due_at DATETIME(3) NOT NULL COMMENT '当前待办SLA到期时间',
    first_detected_at DATETIME(3) NOT NULL COMMENT '首次发现来源活动状态时间',
    last_synced_at DATETIME(3) NOT NULL COMMENT '最近来源同步时间',
    closed_at DATETIME(3) NULL COMMENT '待办完成时间',
    close_reason VARCHAR(256) NULL COMMENT '完成原因',
    latest_remark VARCHAR(1000) NULL COMMENT '最近人工处理备注',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '人工命令和状态同步CAS版本',
    source_projection_version INT NOT NULL DEFAULT 1 COMMENT '来源检索投影版本',
    merchant_id VARCHAR(64) NULL COMMENT '来源商户号检索投影',
    merchant_name VARCHAR(128) NULL COMMENT '来源商户名称检索投影',
    business_no VARCHAR(128) NULL COMMENT '来源主业务单号检索投影',
    related_business_no VARCHAR(128) NULL COMMENT '来源关联业务单号检索投影',
    transaction_id VARCHAR(64) NULL COMMENT '来源系统订单号检索投影',
    transaction_date_time DATETIME(3) NULL COMMENT '来源交易时间检索投影',
    source_occurred_at DATETIME(3) NULL COMMENT '来源事件时间检索投影',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_operational_work_item_no (work_item_no),
    UNIQUE KEY uk_admin_operational_work_item_source (source_type, source_key),
    KEY idx_admin_operational_work_item_queue (work_status, priority, sla_due_at, id),
    KEY idx_admin_operational_work_item_owner (owner_account_id, work_status, sla_due_at, id),
    KEY idx_admin_operational_work_item_detected (first_detected_at, id),
    KEY idx_admin_operational_work_item_projection_queue
        (source_projection_version, work_status, priority, sla_due_at, first_detected_at, id),
    KEY idx_admin_operational_work_item_projection_merchant
        (source_projection_version, merchant_id, work_status, first_detected_at, id),
    CONSTRAINT chk_admin_operational_work_item_state CHECK (
        source_type IN ('REFUND_APPROVAL', 'SETTLEMENT_REVIEW',
                        'EXCHANGE_RATE_ALERT', 'MERCHANT_NOTIFICATION_FAILURE',
                        'CHANNEL_MATCH_ABNORMAL', 'CLEARING_BACKLOG',
                        'SETTLEMENT_BACKLOG', 'HIGH_RISK_EVENT')
        AND work_status IN ('OPEN', 'PROCESSING', 'CLOSED')
        AND priority IN ('CRITICAL', 'HIGH', 'NORMAL', 'LOW')
        AND version >= 0
        AND ((owner_account_id IS NULL AND owner_account_name IS NULL)
             OR (owner_account_id IS NOT NULL AND owner_account_name IS NOT NULL))
        AND ((work_status = 'CLOSED' AND closed_at IS NOT NULL AND close_reason IS NOT NULL)
             OR (work_status <> 'CLOSED' AND closed_at IS NULL AND close_reason IS NULL))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一运营待办元数据';

CREATE TABLE IF NOT EXISTS admin_operational_work_item_history (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    work_item_id BIGINT NOT NULL COMMENT '待办主键',
    work_item_no VARCHAR(32) NOT NULL COMMENT '待办编号快照',
    request_id VARCHAR(128) NOT NULL COMMENT '人工命令或同步动作幂等键',
    action_type VARCHAR(24) NOT NULL COMMENT 'CREATE/REOPEN/CLAIM/ASSIGN/TRANSFER/COMPLETE/REMARK/SOURCE_SYNC/AUTO_CLOSE',
    before_status VARCHAR(16) NULL COMMENT '操作前待办状态',
    after_status VARCHAR(16) NOT NULL COMMENT '操作后待办状态',
    before_owner_account_id BIGINT NULL COMMENT '操作前负责人账号ID',
    before_owner_account_name VARCHAR(128) NULL COMMENT '操作前负责人名称',
    after_owner_account_id BIGINT NULL COMMENT '操作后负责人账号ID',
    after_owner_account_name VARCHAR(128) NULL COMMENT '操作后负责人名称',
    operator_account_id BIGINT NULL COMMENT '人工操作账号ID，系统动作为空',
    operator_account_name VARCHAR(128) NOT NULL COMMENT '操作人或SYSTEM',
    remark VARCHAR(1000) NULL COMMENT '处理说明',
    operated_at DATETIME(3) NOT NULL COMMENT '操作时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_operational_work_item_history_request (request_id),
    KEY idx_admin_operational_work_item_history_item (work_item_id, operated_at, id),
    KEY idx_admin_operational_work_item_history_no (work_item_no, operated_at, id),
    KEY idx_admin_operational_work_item_history_page (work_item_id, operated_at DESC, id DESC),
    CONSTRAINT chk_admin_operational_work_item_history_action CHECK (
        action_type IN ('CREATE', 'REOPEN', 'CLAIM', 'ASSIGN', 'TRANSFER', 'COMPLETE', 'REMARK',
                        'SOURCE_SYNC', 'AUTO_CLOSE')
        AND after_status IN ('OPEN', 'PROCESSING', 'CLOSED')
        AND (before_status IS NULL OR before_status IN ('OPEN', 'PROCESSING', 'CLOSED'))
        AND ((before_owner_account_id IS NULL AND before_owner_account_name IS NULL)
             OR (before_owner_account_id IS NOT NULL AND before_owner_account_name IS NOT NULL))
        AND ((after_owner_account_id IS NULL AND after_owner_account_name IS NULL)
             OR (after_owner_account_id IS NOT NULL AND after_owner_account_name IS NOT NULL))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一运营待办处理轨迹';

START TRANSACTION;

SET @admin_app_id = (
    SELECT id FROM sys_app WHERE app_code = 'ADMIN' AND deleted = 0 ORDER BY id LIMIT 1
);

SET @admin_home_catalog_id = (
    SELECT id FROM sys_menu
    WHERE app_id = @admin_app_id
      AND menu_code = 'admin_home_catalog_v3'
      AND menu_type = 'CATALOG'
      AND deleted = 0
    ORDER BY id LIMIT 1
);

-- 第一批仅有统一待办页面，直接放在首页目录的工作台之后；预留目录不向用户展示。
DELETE role_menu
FROM sys_role_menu role_menu
JOIN sys_menu menu ON menu.id = role_menu.menu_id
WHERE role_menu.app_id = @admin_app_id
  AND menu.app_id = @admin_app_id
  AND menu.menu_code = 'admin_operations_catalog_v1';

UPDATE sys_menu
SET visible = 0, status = 0, deleted = 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = @admin_app_id AND menu_code = 'admin_operations_catalog_v1' AND deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT @admin_app_id, @admin_home_catalog_id, 'admin_operational_work_item_v1', '统一待办', 'MENU',
       '/operations/work-items', 'operations/work-item',
       'operations:work-item:list', 'List', 1, 0, 0, 3, 1, 0
WHERE @admin_home_catalog_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu
      WHERE app_id = @admin_app_id AND menu_code = 'admin_operational_work_item_v1' AND deleted = 0
  );

UPDATE sys_menu page
SET page.parent_id = @admin_home_catalog_id,
    page.menu_name = BINARY '统一待办', page.menu_type = 'MENU',
    page.route_path = '/operations/work-items', page.component_path = 'operations/work-item',
    page.permission_code = 'operations:work-item:list', page.icon = 'List',
    page.visible = 1, page.keep_alive = 0, page.external_link = 0, page.deleted = 0,
    page.sort_no = 3, page.status = 1, page.updated_at = CURRENT_TIMESTAMP(3)
WHERE page.app_id = @admin_app_id
  AND page.menu_code = 'admin_operational_work_item_v1'
  AND @admin_home_catalog_id IS NOT NULL;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, permission_code,
    visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT @admin_app_id, page.id, item.menu_code, item.menu_name, 'BUTTON', item.permission_code,
       0, 0, 0, item.sort_no, 1, 0
FROM sys_menu page
JOIN (
    SELECT 'admin_operational_work_item_detail_v1' menu_code, '查看待办详情' menu_name,
           'operations:work-item:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'admin_operational_work_item_claim_v1', '认领待办',
           'operations:work-item:claim', 2
    UNION ALL SELECT 'admin_operational_work_item_assign_v1', '分派待办',
           'operations:work-item:assign', 3
    UNION ALL SELECT 'admin_operational_work_item_transfer_v1', '转交待办',
           'operations:work-item:transfer', 4
    UNION ALL SELECT 'admin_operational_work_item_complete_v1', '完成待办',
           'operations:work-item:complete', 5
    UNION ALL SELECT 'admin_operational_work_item_remark_v1', '备注待办',
           'operations:work-item:remark', 6
    UNION ALL SELECT 'admin_operational_work_item_export_v1', '导出待办',
           'operations:work-item:export', 7
) item
WHERE page.app_id = @admin_app_id
  AND page.menu_code = 'admin_operational_work_item_v1' AND page.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = @admin_app_id
        AND existing.menu_code = item.menu_code AND existing.deleted = 0
  );

UPDATE sys_menu menu
JOIN (
    SELECT 'admin_operational_work_item_detail_v1' menu_code, '查看待办详情' menu_name,
           'operations:work-item:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'admin_operational_work_item_claim_v1', '认领待办',
           'operations:work-item:claim', 2
    UNION ALL SELECT 'admin_operational_work_item_assign_v1', '分派待办',
           'operations:work-item:assign', 3
    UNION ALL SELECT 'admin_operational_work_item_transfer_v1', '转交待办',
           'operations:work-item:transfer', 4
    UNION ALL SELECT 'admin_operational_work_item_complete_v1', '完成待办',
           'operations:work-item:complete', 5
    UNION ALL SELECT 'admin_operational_work_item_remark_v1', '备注待办',
           'operations:work-item:remark', 6
    UNION ALL SELECT 'admin_operational_work_item_export_v1', '导出待办',
           'operations:work-item:export', 7
) item ON item.menu_code = menu.menu_code
JOIN sys_menu page ON page.app_id = menu.app_id
                  AND page.menu_code = 'admin_operational_work_item_v1'
                  AND page.deleted = 0
SET menu.parent_id = page.id, menu.menu_name = BINARY item.menu_name,
    menu.menu_type = 'BUTTON', menu.route_path = NULL, menu.component_path = NULL,
    menu.permission_code = item.permission_code, menu.icon = NULL,
    menu.visible = 0, menu.keep_alive = 0, menu.external_link = 0,
    menu.sort_no = item.sort_no, menu.status = 1, menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.app_id = @admin_app_id AND menu.deleted = 0;

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT @admin_app_id, menu.id, item.permission_code, item.permission_name,
       item.permission_type, item.resource_method, item.resource_path,
       item.description, 1, 0
FROM (
    SELECT 'admin_operational_work_item_v1' menu_code,
           'operations:work-item:list' permission_code, '统一待办查询' permission_name,
           'MENU' permission_type, 'POST' resource_method,
           '/admin/operations/work-items/search' resource_path,
           '查询权限和商户数据范围内的统一待办' description
    UNION ALL SELECT 'admin_operational_work_item_detail_v1',
           'operations:work-item:detail', '统一待办详情', 'BUTTON', 'GET',
           '/admin/operations/work-items/*', '查看待办实时来源摘要和处理轨迹'
    UNION ALL SELECT 'admin_operational_work_item_claim_v1',
           'operations:work-item:claim', '统一待办认领', 'BUTTON', 'POST',
           '/admin/operations/work-items/*/claim', '认领未分派活动待办'
    UNION ALL SELECT 'admin_operational_work_item_assign_v1',
           'operations:work-item:assign', '统一待办分派', 'BUTTON', 'POST',
           '/admin/operations/work-items/*/assign', '将活动待办分派给有效管理账号'
    UNION ALL SELECT 'admin_operational_work_item_transfer_v1',
           'operations:work-item:transfer', '统一待办转交', 'BUTTON', 'POST',
           '/admin/operations/work-items/*/transfer', '将已分派待办转交给其他管理账号'
    UNION ALL SELECT 'admin_operational_work_item_complete_v1',
           'operations:work-item:complete', '统一待办完成', 'BUTTON', 'POST',
           '/admin/operations/work-items/*/complete', '以处理结论完成人工待办并保留审计轨迹'
    UNION ALL SELECT 'admin_operational_work_item_remark_v1',
           'operations:work-item:remark', '统一待办备注', 'BUTTON', 'POST',
           '/admin/operations/work-items/*/remark', '追加待办处理备注'
    UNION ALL SELECT 'admin_operational_work_item_export_v1',
           'operations:work-item:export', '统一待办导出', 'BUTTON', 'POST',
           '/admin/operations/work-items/export', '导出权限和数据范围内的待办'
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
    SELECT 'admin_operational_work_item_v1' menu_code,
           'operations:work-item:list' permission_code, '统一待办查询' permission_name,
           'MENU' permission_type, 'POST' resource_method,
           '/admin/operations/work-items/search' resource_path,
           '查询权限和商户数据范围内的统一待办' description
    UNION ALL SELECT 'admin_operational_work_item_detail_v1',
           'operations:work-item:detail', '统一待办详情', 'BUTTON', 'GET',
           '/admin/operations/work-items/*', '查看待办实时来源摘要和处理轨迹'
    UNION ALL SELECT 'admin_operational_work_item_claim_v1',
           'operations:work-item:claim', '统一待办认领', 'BUTTON', 'POST',
           '/admin/operations/work-items/*/claim', '认领未分派活动待办'
    UNION ALL SELECT 'admin_operational_work_item_assign_v1',
           'operations:work-item:assign', '统一待办分派', 'BUTTON', 'POST',
           '/admin/operations/work-items/*/assign', '将活动待办分派给有效管理账号'
    UNION ALL SELECT 'admin_operational_work_item_transfer_v1',
           'operations:work-item:transfer', '统一待办转交', 'BUTTON', 'POST',
           '/admin/operations/work-items/*/transfer', '将已分派待办转交给其他管理账号'
    UNION ALL SELECT 'admin_operational_work_item_complete_v1',
           'operations:work-item:complete', '统一待办完成', 'BUTTON', 'POST',
           '/admin/operations/work-items/*/complete', '以处理结论完成人工待办并保留审计轨迹'
    UNION ALL SELECT 'admin_operational_work_item_remark_v1',
           'operations:work-item:remark', '统一待办备注', 'BUTTON', 'POST',
           '/admin/operations/work-items/*/remark', '追加待办处理备注'
    UNION ALL SELECT 'admin_operational_work_item_export_v1',
           'operations:work-item:export', '统一待办导出', 'BUTTON', 'POST',
           '/admin/operations/work-items/export', '导出权限和数据范围内的待办'
) item ON item.permission_code = permission.permission_code
JOIN sys_menu menu ON menu.app_id = permission.app_id
                  AND menu.menu_code = item.menu_code AND menu.deleted = 0
SET permission.menu_id = menu.id,
    permission.permission_name = BINARY item.permission_name,
    permission.permission_type = item.permission_type,
    permission.resource_method = item.resource_method,
    permission.resource_path = item.resource_path,
    permission.description = BINARY item.description,
    permission.status = 1,
    permission.updated_at = CURRENT_TIMESTAMP(3)
WHERE permission.app_id = @admin_app_id AND permission.deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, created_by, deleted)
SELECT @admin_app_id, role.id, menu.id, NULL, 0
FROM sys_role role
JOIN sys_menu menu ON menu.app_id = role.app_id AND menu.deleted = 0
WHERE role.app_id = @admin_app_id AND role.role_code = 'SUPER_ADMIN' AND role.deleted = 0
  AND menu.menu_code IN (
      'admin_operational_work_item_v1',
      'admin_operational_work_item_detail_v1', 'admin_operational_work_item_claim_v1',
      'admin_operational_work_item_assign_v1', 'admin_operational_work_item_transfer_v1',
      'admin_operational_work_item_complete_v1', 'admin_operational_work_item_remark_v1',
      'admin_operational_work_item_export_v1'
  );

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, created_by, deleted)
SELECT @admin_app_id, role.id, permission.id, NULL, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id AND permission.deleted = 0
WHERE role.app_id = @admin_app_id AND role.role_code = 'SUPER_ADMIN' AND role.deleted = 0
  AND permission.permission_code IN (
      'operations:work-item:list', 'operations:work-item:detail',
      'operations:work-item:claim', 'operations:work-item:assign',
      'operations:work-item:transfer', 'operations:work-item:complete',
      'operations:work-item:remark',
      'operations:work-item:export'
  );

INSERT INTO sys_job_task (
    job_code, job_name, job_group, handler_code, cron_expression, scheduler_mode, trigger_mode,
    execute_mode, route_strategy, misfire_strategy, timeout_seconds, retry_count,
    retry_interval_seconds, allow_concurrent, params, status, description, next_trigger_time,
    version, deleted, create_by, create_time, update_by, update_time
)
SELECT 'OPERATIONAL_WORK_ITEM_SYNC', '统一运营待办同步任务', 'operations',
       'operationalWorkItemSync', '30 * * * * ?', 'DISTRIBUTED', 'CRON',
       'SYNC', 'LOCAL', 'FIRE_ONCE', 120, 2, 30, 0, '{}', 'ENABLED',
       '每分钟幂等同步退款审批、结算复核、汇率预警、终态商户通知失败、渠道勾兑异常、清分异常积压、结算异常积压和高风险事件；系统任务禁止删除或更换处理器。',
       DATE_ADD(NOW(3), INTERVAL 1 MINUTE), 0, 0, 'system', NOW(3), 'system', NOW(3)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job_task
    WHERE job_code = 'OPERATIONAL_WORK_ITEM_SYNC' AND deleted = 0
);

-- 任务身份不可变，保留运维人员已维护的 Cron、超时、重试和启停状态。
UPDATE sys_job_task
SET job_name = '统一运营待办同步任务', job_group = 'operations',
    handler_code = 'operationalWorkItemSync', scheduler_mode = 'DISTRIBUTED',
    execute_mode = 'SYNC', allow_concurrent = 0,
    description = '每分钟幂等同步退款审批、结算复核、汇率预警、终态商户通知失败、渠道勾兑异常、清分异常积压、结算异常积压和高风险事件；系统任务禁止删除或更换处理器。',
    update_by = 'system-migration', update_time = NOW(3)
WHERE job_code = 'OPERATIONAL_WORK_ITEM_SYNC' AND deleted = 0;

COMMIT;
