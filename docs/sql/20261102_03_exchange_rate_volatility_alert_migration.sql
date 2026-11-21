-- 汇率波动与预警功能迁移。
-- 脚本可重复执行；来源编码只作为证据保存，不进入规则维度或活动事件唯一键。

CREATE TABLE IF NOT EXISTS exchange_rate_alert_rule (
    id bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rule_code varchar(64) NOT NULL COMMENT '稳定规则编码',
    rule_name varchar(128) NOT NULL COMMENT '规则名称',
    rate_type varchar(32) NOT NULL COMMENT '汇率类型：TRANSACTION_RATE/SETTLEMENT_RATE',
    base_currency varchar(3) NOT NULL DEFAULT 'ALL' COMMENT '原始币种，ALL表示所有币种',
    quote_currency varchar(3) NOT NULL DEFAULT 'ALL' COMMENT '目标币种，ALL表示所有币种',
    window_minutes int NOT NULL COMMENT '滚动窗口分钟数',
    trigger_threshold decimal(9,4) NOT NULL COMMENT '触发阈值百分比',
    recovery_threshold decimal(9,4) NOT NULL COMMENT '恢复阈值百分比',
    recovery_consecutive_count int NOT NULL DEFAULT 2 COMMENT '连续低于恢复阈值的评估次数',
    minimum_sample_count int NOT NULL DEFAULT 2 COMMENT '最小样本数',
    alert_level varchar(32) NOT NULL DEFAULT 'WARNING' COMMENT '预警等级：WARNING/ERROR/CRITICAL',
    system_mandatory tinyint NOT NULL DEFAULT 0 COMMENT '是否系统强制规则：0否，1是',
    rule_status tinyint NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    remark varchar(512) DEFAULT NULL COMMENT '备注',
    create_by varchar(64) DEFAULT NULL COMMENT '创建人',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by varchar(64) DEFAULT NULL COMMENT '更新人',
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted bigint NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exchange_rate_alert_rule_code_deleted (rule_code, deleted),
    KEY idx_exchange_rate_alert_rule_scope (rate_type, base_currency, quote_currency, rule_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率波动预警规则表';

CREATE TABLE IF NOT EXISTS exchange_rate_alert_event (
    id bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    event_no varchar(64) NOT NULL COMMENT '预警事件编号',
    active_event_key varchar(255) DEFAULT NULL COMMENT '活动事件唯一键，终态后置空',
    rule_id bigint NOT NULL COMMENT '规则ID',
    rule_code varchar(64) NOT NULL COMMENT '触发时规则编码快照',
    rule_name varchar(128) NOT NULL COMMENT '触发时规则名称快照',
    rate_type varchar(32) NOT NULL COMMENT '汇率类型',
    base_currency char(3) NOT NULL COMMENT '原始币种',
    quote_currency char(3) NOT NULL COMMENT '目标币种',
    alert_level varchar(32) NOT NULL COMMENT '预警等级快照',
    window_minutes int NOT NULL COMMENT '窗口分钟数快照',
    trigger_threshold decimal(9,4) NOT NULL COMMENT '触发阈值百分比快照',
    recovery_threshold decimal(9,4) NOT NULL COMMENT '恢复阈值百分比快照',
    window_start_time datetime(3) NOT NULL COMMENT '最近评估窗口开始时间',
    window_end_time datetime(3) NOT NULL COMMENT '最近评估窗口结束时间',
    sample_count int NOT NULL COMMENT '最近评估样本数',
    minimum_rate decimal(24,12) NOT NULL COMMENT '窗口最低汇率',
    minimum_rate_source varchar(64) DEFAULT NULL COMMENT '最低汇率来源证据',
    minimum_business_rate_id bigint DEFAULT NULL COMMENT '最低汇率业务汇率ID',
    maximum_rate decimal(24,12) NOT NULL COMMENT '窗口最高汇率',
    maximum_rate_source varchar(64) DEFAULT NULL COMMENT '最高汇率来源证据',
    maximum_business_rate_id bigint DEFAULT NULL COMMENT '最高汇率业务汇率ID',
    latest_rate decimal(24,12) NOT NULL COMMENT '窗口最新汇率',
    latest_rate_source varchar(64) DEFAULT NULL COMMENT '最新汇率来源证据',
    latest_business_rate_id bigint DEFAULT NULL COMMENT '最新汇率业务汇率ID',
    volatility_rate decimal(12,6) NOT NULL COMMENT '最近窗口波动率百分比',
    maximum_volatility_rate decimal(12,6) NOT NULL COMMENT '事件持续期间最大波动率百分比',
    recovery_streak int NOT NULL DEFAULT 0 COMMENT '连续低于恢复阈值次数',
    event_status varchar(32) NOT NULL COMMENT '预警事件状态：待确认、已确认、已恢复或已关闭',
    first_trigger_time datetime(3) NOT NULL COMMENT '首次触发时间',
    last_detected_time datetime(3) NOT NULL COMMENT '最近发现时间',
    resolved_time datetime(3) DEFAULT NULL COMMENT '自动恢复时间',
    acknowledged_by varchar(64) DEFAULT NULL COMMENT '确认人',
    acknowledged_time datetime(3) DEFAULT NULL COMMENT '确认时间',
    acknowledge_remark varchar(512) DEFAULT NULL COMMENT '确认备注',
    closed_by varchar(64) DEFAULT NULL COMMENT '关闭人',
    closed_time datetime(3) DEFAULT NULL COMMENT '关闭时间',
    close_remark varchar(512) DEFAULT NULL COMMENT '关闭备注',
    version int NOT NULL DEFAULT 0 COMMENT 'CAS版本',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted bigint NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exchange_rate_alert_event_no (event_no),
    UNIQUE KEY uk_exchange_rate_alert_active_key (active_event_key),
    KEY idx_exchange_rate_alert_event_status_time (event_status, first_trigger_time, id),
    KEY idx_exchange_rate_alert_event_scope_time (rate_type, base_currency, quote_currency, first_trigger_time, id),
    KEY idx_exchange_rate_alert_event_rule_time (rule_id, first_trigger_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率波动预警事件表';

CREATE TABLE IF NOT EXISTS exchange_rate_alert_evaluation (
    id bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    request_key varchar(128) NOT NULL COMMENT '调度请求幂等键',
    window_end_time datetime(3) NOT NULL COMMENT '本轮评估业务截止时间',
    evaluation_status varchar(32) NOT NULL COMMENT '评估执行状态：PROCESSING处理中，SUCCESS成功，FAILED失败',
    evaluated_rule_count int NOT NULL DEFAULT 0 COMMENT '评估规则数',
    evaluated_pair_count int NOT NULL DEFAULT 0 COMMENT '评估币种对数',
    triggered_event_count int NOT NULL DEFAULT 0 COMMENT '新触发事件数',
    updated_event_count int NOT NULL DEFAULT 0 COMMENT '更新活动事件数',
    resolved_event_count int NOT NULL DEFAULT 0 COMMENT '自动恢复事件数',
    insufficient_data_count int NOT NULL DEFAULT 0 COMMENT '样本不足维度数',
    error_message varchar(512) DEFAULT NULL COMMENT '失败摘要，不保存敏感信息',
    start_time datetime(3) NOT NULL COMMENT '开始时间',
    end_time datetime(3) DEFAULT NULL COMMENT '结束时间',
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exchange_rate_alert_evaluation_request (request_key),
    KEY idx_exchange_rate_alert_evaluation_time (window_end_time, evaluation_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率波动预警评估幂等记录表';

DROP PROCEDURE IF EXISTS migrate_exchange_rate_volatility_alert;
DELIMITER $$
CREATE PROCEDURE migrate_exchange_rate_volatility_alert()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'exchange_business_rate'
          AND index_name = 'idx_exchange_business_rate_volatility'
    ) THEN
        ALTER TABLE exchange_business_rate
            ADD KEY idx_exchange_business_rate_volatility
                (rate_type, base_currency, quote_currency, effective_time, deleted);
    END IF;
END$$
DELIMITER ;
CALL migrate_exchange_rate_volatility_alert();
DROP PROCEDURE migrate_exchange_rate_volatility_alert;

INSERT INTO exchange_rate_alert_rule (
    rule_code, rule_name, rate_type, base_currency, quote_currency, window_minutes,
    trigger_threshold, recovery_threshold, recovery_consecutive_count, minimum_sample_count,
    alert_level, system_mandatory, rule_status, remark, create_by, update_by, deleted
) VALUES
    ('SYSTEM_TRANSACTION_72H_3PCT', '交易汇率72小时强制波动预警', 'TRANSACTION_RATE', 'ALL', 'ALL', 4320,
     3.0000, 2.0000, 2, 2, 'WARNING', 1, 1, '系统强制基线：波动率大于3%触发，连续两次低于2%恢复。', 'system', 'system', 0),
    ('SYSTEM_SETTLEMENT_72H_3PCT', '结算汇率72小时强制波动预警', 'SETTLEMENT_RATE', 'ALL', 'ALL', 4320,
     3.0000, 2.0000, 2, 2, 'WARNING', 1, 1, '系统强制基线：波动率大于3%触发，连续两次低于2%恢复。', 'system', 'system', 0)
ON DUPLICATE KEY UPDATE
    rule_name = VALUES(rule_name),
    rate_type = VALUES(rate_type),
    base_currency = 'ALL',
    quote_currency = 'ALL',
    window_minutes = 4320,
    trigger_threshold = 3.0000,
    recovery_threshold = 2.0000,
    recovery_consecutive_count = 2,
    minimum_sample_count = 2,
    alert_level = 'WARNING',
    system_mandatory = 1,
    rule_status = 1,
    remark = VALUES(remark),
    deleted = 0,
    update_by = 'system';

INSERT INTO sys_job_task (
    job_code, job_name, job_group, handler_code, cron_expression, scheduler_mode, trigger_mode,
    execute_mode, route_strategy, misfire_strategy, timeout_seconds, retry_count,
    retry_interval_seconds, allow_concurrent, params, status, description, next_trigger_time,
    version, deleted, create_by, create_time, update_by, update_time
) SELECT
    'EXCHANGE_RATE_VOLATILITY_MONITOR', '汇率波动预警评估任务', 'exchange',
    'exchangeRateVolatilityMonitorJob', '0 */5 * * * ?', 'DISTRIBUTED', 'CRON',
    'SYNC', 'LOCAL', 'FIRE_ONCE', 120, 2, 30, 0, '{}', 'ENABLED',
    '每5分钟调用 service-admin 幂等评估交易汇率和结算汇率波动；系统任务禁止删除或更换处理器。',
    DATE_ADD(NOW(3), INTERVAL 5 MINUTE), 0, 0, 'system', NOW(3), 'system', NOW(3)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job_task
    WHERE job_code = 'EXCHANGE_RATE_VOLATILITY_MONITOR' AND deleted = 0
);

-- 系统任务身份属于不可编辑不变量；Cron、超时、重试和启停状态保留现有运维配置。
UPDATE sys_job_task
SET job_name = '汇率波动预警评估任务',
    job_group = 'exchange',
    handler_code = 'exchangeRateVolatilityMonitorJob',
    scheduler_mode = 'DISTRIBUTED',
    execute_mode = 'SYNC',
    allow_concurrent = 0,
    description = '每5分钟调用 service-admin 幂等评估交易汇率和结算汇率波动；系统任务禁止删除或更换处理器。',
    update_by = 'system-migration',
    update_time = NOW(3)
WHERE job_code = 'EXCHANGE_RATE_VOLATILITY_MONITOR'
  AND deleted = 0
  AND (
      job_name <> '汇率波动预警评估任务'
      OR job_group <> 'exchange'
      OR handler_code <> 'exchangeRateVolatilityMonitorJob'
      OR scheduler_mode <> 'DISTRIBUTED'
      OR execute_mode <> 'SYNC'
      OR allow_concurrent <> 0
      OR description <> '每5分钟调用 service-admin 幂等评估交易汇率和结算汇率波动；系统任务禁止删除或更换处理器。'
  );

-- 新菜单按稳定 menu_code 定位父子关系，重复执行不会产生重复菜单。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT 1, parent.id, child.menu_code, child.menu_name, 'MENU', child.route_path,
       child.component_path, child.permission_code, child.icon, 1, 0, 0, child.sort_no, 1, 0
FROM sys_menu parent
JOIN (
    SELECT 'exchange_rate_volatility' menu_code, '汇率波动' menu_name,
           '/exchange/volatility' route_path, 'exchange/volatility' component_path,
           'exchange:volatility:list' permission_code, 'DataLine' icon, 7 sort_no
    UNION ALL SELECT 'exchange_rate_alert_event', '汇率预警事件',
           '/exchange/alert-event', 'exchange/alert-event',
           'exchange:alert-event:list', 'Warning' icon, 8
    UNION ALL SELECT 'exchange_rate_alert_rule', '汇率预警规则',
           '/exchange/alert-rule', 'exchange/alert-rule',
           'exchange:alert-rule:list', 'Bell' icon, 9
) child ON parent.app_id = 1 AND parent.menu_code = 'exchange' AND parent.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu current_menu
    WHERE current_menu.app_id = 1 AND current_menu.menu_code = child.menu_code AND current_menu.deleted = 0
);

-- 回正已有菜单的兼容回退文案；正式显示仍由前端根据稳定 menu_code 做国际化。
UPDATE sys_menu
SET menu_name = CASE menu_code
    WHEN 'exchange_rate_volatility' THEN '汇率波动'
    WHEN 'exchange_rate_alert_event' THEN '汇率预警事件'
    WHEN 'exchange_rate_alert_rule' THEN '汇率预警规则'
END
WHERE app_id = 1
  AND deleted = 0
  AND menu_code IN ('exchange_rate_volatility', 'exchange_rate_alert_event', 'exchange_rate_alert_rule');

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT 1, parent.id, button.menu_code, button.menu_name, 'BUTTON', NULL, NULL,
       button.permission_code, NULL, 0, 0, 0, button.sort_no, 1, 0
FROM sys_menu parent
JOIN (
    SELECT 'exchange_rate_volatility' parent_code, 'exchange_rate_volatility_query' menu_code, '汇率波动详情' menu_name, 'exchange:volatility:query' permission_code, 101 sort_no
    UNION ALL SELECT 'exchange_rate_volatility', 'exchange_rate_volatility_export', '汇率波动导出', 'exchange:volatility:export', 102
    UNION ALL SELECT 'exchange_rate_alert_event', 'exchange_rate_alert_event_query', '汇率预警事件详情', 'exchange:alert-event:query', 101
    UNION ALL SELECT 'exchange_rate_alert_event', 'exchange_rate_alert_event_ack', '汇率预警事件确认', 'exchange:alert-event:ack', 102
    UNION ALL SELECT 'exchange_rate_alert_event', 'exchange_rate_alert_event_close', '汇率预警事件关闭', 'exchange:alert-event:close', 103
    UNION ALL SELECT 'exchange_rate_alert_event', 'exchange_rate_alert_event_export', '汇率预警事件导出', 'exchange:alert-event:export', 104
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange_rate_alert_rule_query', '汇率预警规则详情', 'exchange:alert-rule:query', 101
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange_rate_alert_rule_add', '汇率预警规则新增', 'exchange:alert-rule:add', 102
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange_rate_alert_rule_edit', '汇率预警规则修改', 'exchange:alert-rule:edit', 103
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange_rate_alert_rule_remove', '汇率预警规则删除', 'exchange:alert-rule:remove', 104
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange_rate_alert_rule_start', '汇率预警规则启用', 'exchange:alert-rule:start', 105
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange_rate_alert_rule_stop', '汇率预警规则停用', 'exchange:alert-rule:stop', 106
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange_rate_alert_rule_import', '汇率预警规则导入', 'exchange:alert-rule:import', 107
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange_rate_alert_rule_export', '汇率预警规则导出', 'exchange:alert-rule:export', 108
) button ON button.parent_code = parent.menu_code
WHERE parent.app_id = 1 AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu current_menu
      WHERE current_menu.app_id = 1 AND current_menu.menu_code = button.menu_code AND current_menu.deleted = 0
  );

UPDATE sys_menu
SET menu_name = CASE menu_code
    WHEN 'exchange_rate_volatility_query' THEN '汇率波动详情'
    WHEN 'exchange_rate_volatility_export' THEN '汇率波动导出'
    WHEN 'exchange_rate_alert_event_query' THEN '汇率预警事件详情'
    WHEN 'exchange_rate_alert_event_ack' THEN '汇率预警事件确认'
    WHEN 'exchange_rate_alert_event_close' THEN '汇率预警事件关闭'
    WHEN 'exchange_rate_alert_event_export' THEN '汇率预警事件导出'
    WHEN 'exchange_rate_alert_rule_query' THEN '汇率预警规则详情'
    WHEN 'exchange_rate_alert_rule_add' THEN '汇率预警规则新增'
    WHEN 'exchange_rate_alert_rule_edit' THEN '汇率预警规则修改'
    WHEN 'exchange_rate_alert_rule_remove' THEN '汇率预警规则删除'
    WHEN 'exchange_rate_alert_rule_start' THEN '汇率预警规则启用'
    WHEN 'exchange_rate_alert_rule_stop' THEN '汇率预警规则停用'
    WHEN 'exchange_rate_alert_rule_import' THEN '汇率预警规则导入'
    WHEN 'exchange_rate_alert_rule_export' THEN '汇率预警规则导出'
END
WHERE app_id = 1
  AND deleted = 0
  AND menu_code IN (
      'exchange_rate_volatility_query', 'exchange_rate_volatility_export',
      'exchange_rate_alert_event_query', 'exchange_rate_alert_event_ack',
      'exchange_rate_alert_event_close', 'exchange_rate_alert_event_export',
      'exchange_rate_alert_rule_query', 'exchange_rate_alert_rule_add',
      'exchange_rate_alert_rule_edit', 'exchange_rate_alert_rule_remove',
      'exchange_rate_alert_rule_start', 'exchange_rate_alert_rule_stop',
      'exchange_rate_alert_rule_import', 'exchange_rate_alert_rule_export'
  );

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT 1, menu.id, permission.permission_code, permission.permission_name,
       permission.permission_type, permission.resource_method, permission.resource_path,
       permission.permission_name, 1, 0
FROM (
    SELECT 'exchange_rate_volatility' menu_code, 'exchange:volatility:list' permission_code, '汇率波动查询' permission_name, 'MENU' permission_type, 'POST' resource_method, '/admin/exchange/volatility/overview' resource_path
    UNION ALL SELECT 'exchange_rate_volatility_query', 'exchange:volatility:query', '汇率波动详情', 'BUTTON', 'POST', '/admin/exchange/volatility/trend'
    UNION ALL SELECT 'exchange_rate_volatility_export', 'exchange:volatility:export', '汇率波动导出', 'BUTTON', 'POST', '/admin/exchange/volatility/export'
    UNION ALL SELECT 'exchange_rate_alert_event', 'exchange:alert-event:list', '汇率预警事件查询', 'MENU', 'POST', '/admin/exchange/alert-events/search'
    UNION ALL SELECT 'exchange_rate_alert_event_query', 'exchange:alert-event:query', '汇率预警事件详情', 'BUTTON', 'GET', '/admin/exchange/alert-events/*'
    UNION ALL SELECT 'exchange_rate_alert_event_ack', 'exchange:alert-event:ack', '汇率预警事件确认', 'BUTTON', 'PUT', '/admin/exchange/alert-events/*/acknowledge'
    UNION ALL SELECT 'exchange_rate_alert_event_close', 'exchange:alert-event:close', '汇率预警事件关闭', 'BUTTON', 'PUT', '/admin/exchange/alert-events/*/close'
    UNION ALL SELECT 'exchange_rate_alert_event_export', 'exchange:alert-event:export', '汇率预警事件导出', 'BUTTON', 'POST', '/admin/exchange/alert-events/export'
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange:alert-rule:list', '汇率预警规则查询', 'MENU', 'POST', '/admin/exchange/alert-rules/search'
    UNION ALL SELECT 'exchange_rate_alert_rule_query', 'exchange:alert-rule:query', '汇率预警规则详情', 'BUTTON', 'GET', '/admin/exchange/alert-rules/*'
    UNION ALL SELECT 'exchange_rate_alert_rule_add', 'exchange:alert-rule:add', '汇率预警规则新增', 'BUTTON', 'POST', '/admin/exchange/alert-rules'
    UNION ALL SELECT 'exchange_rate_alert_rule_edit', 'exchange:alert-rule:edit', '汇率预警规则修改', 'BUTTON', 'PUT', '/admin/exchange/alert-rules/*'
    UNION ALL SELECT 'exchange_rate_alert_rule_remove', 'exchange:alert-rule:remove', '汇率预警规则删除', 'BUTTON', 'DELETE', '/admin/exchange/alert-rules/*'
    UNION ALL SELECT 'exchange_rate_alert_rule_start', 'exchange:alert-rule:start', '汇率预警规则启用', 'BUTTON', 'PUT', '/admin/exchange/alert-rules/*/enable'
    UNION ALL SELECT 'exchange_rate_alert_rule_stop', 'exchange:alert-rule:stop', '汇率预警规则停用', 'BUTTON', 'PUT', '/admin/exchange/alert-rules/*/disable'
    UNION ALL SELECT 'exchange_rate_alert_rule_import', 'exchange:alert-rule:import', '汇率预警规则导入', 'BUTTON', 'POST', '/admin/exchange/alert-rules/import'
    UNION ALL SELECT 'exchange_rate_alert_rule_export', 'exchange:alert-rule:export', '汇率预警规则导出', 'BUTTON', 'POST', '/admin/exchange/alert-rules/export'
) permission
JOIN sys_menu menu ON menu.app_id = 1 AND menu.menu_code = permission.menu_code AND menu.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission current_permission
    WHERE current_permission.app_id = 1
      AND current_permission.permission_code = permission.permission_code
      AND current_permission.deleted = 0
);

-- 权限名称是操作日志和无国际化回退场景的稳定中文文案，迁移重跑必须能够修复乱码。
UPDATE sys_permission current_permission
JOIN (
    SELECT 'exchange:volatility:list' permission_code, '汇率波动查询' permission_name
    UNION ALL SELECT 'exchange:volatility:query', '汇率波动详情'
    UNION ALL SELECT 'exchange:volatility:export', '汇率波动导出'
    UNION ALL SELECT 'exchange:alert-event:list', '汇率预警事件查询'
    UNION ALL SELECT 'exchange:alert-event:query', '汇率预警事件详情'
    UNION ALL SELECT 'exchange:alert-event:ack', '汇率预警事件确认'
    UNION ALL SELECT 'exchange:alert-event:close', '汇率预警事件关闭'
    UNION ALL SELECT 'exchange:alert-event:export', '汇率预警事件导出'
    UNION ALL SELECT 'exchange:alert-rule:list', '汇率预警规则查询'
    UNION ALL SELECT 'exchange:alert-rule:query', '汇率预警规则详情'
    UNION ALL SELECT 'exchange:alert-rule:add', '汇率预警规则新增'
    UNION ALL SELECT 'exchange:alert-rule:edit', '汇率预警规则修改'
    UNION ALL SELECT 'exchange:alert-rule:remove', '汇率预警规则删除'
    UNION ALL SELECT 'exchange:alert-rule:start', '汇率预警规则启用'
    UNION ALL SELECT 'exchange:alert-rule:stop', '汇率预警规则停用'
    UNION ALL SELECT 'exchange:alert-rule:import', '汇率预警规则导入'
    UNION ALL SELECT 'exchange:alert-rule:export', '汇率预警规则导出'
) expected ON expected.permission_code = current_permission.permission_code
SET current_permission.permission_name = expected.permission_name,
    current_permission.description = expected.permission_name
WHERE current_permission.app_id = 1
  AND current_permission.deleted = 0;

-- 仅为现有本地管理员角色授予新权限；其他角色继续由权限中心显式授权。
INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 1, role.id, menu.id, 0
FROM sys_role role
JOIN sys_menu menu ON menu.app_id = role.app_id
WHERE role.app_id = 1
  AND role.role_code = 'ADMIN_OPERATOR'
  AND role.deleted = 0
  AND menu.menu_code IN (
      'exchange_rate_volatility', 'exchange_rate_volatility_query', 'exchange_rate_volatility_export',
      'exchange_rate_alert_event', 'exchange_rate_alert_event_query', 'exchange_rate_alert_event_ack',
      'exchange_rate_alert_event_close', 'exchange_rate_alert_event_export',
      'exchange_rate_alert_rule', 'exchange_rate_alert_rule_query', 'exchange_rate_alert_rule_add',
      'exchange_rate_alert_rule_edit', 'exchange_rate_alert_rule_remove', 'exchange_rate_alert_rule_start',
      'exchange_rate_alert_rule_stop', 'exchange_rate_alert_rule_import', 'exchange_rate_alert_rule_export'
  )
  AND menu.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 1, role.id, permission.id, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id
WHERE role.app_id = 1
  AND role.role_code = 'ADMIN_OPERATOR'
  AND role.deleted = 0
  AND (
      permission.permission_code LIKE 'exchange:volatility:%'
      OR permission.permission_code LIKE 'exchange:alert-event:%'
      OR permission.permission_code LIKE 'exchange:alert-rule:%'
  )
  AND permission.deleted = 0;
