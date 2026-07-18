SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS channel_alert_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rule_code VARCHAR(64) NOT NULL COMMENT '规则编码，用于事件和通知日志关联',
    rule_group_code VARCHAR(64) NOT NULL COMMENT '规则分组编码，同一次批量配置共用',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    channel_id BIGINT NOT NULL COMMENT '渠道ID，关联 channel_info.id',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    business_type VARCHAR(32) NOT NULL COMMENT '业务类型：ACQUIRING/PAYOUT',
    payment_method VARCHAR(64) NOT NULL DEFAULT 'ALL' COMMENT '支付方式，ALL表示全部',
    card_brand VARCHAR(255) NOT NULL DEFAULT 'ALL' COMMENT '卡品牌范围，ALL表示全部；多选时使用逗号分隔保存为一个预警范围',
    rule_type VARCHAR(64) NOT NULL COMMENT '规则类型：CONTINUOUS_FAILURE/SUCCESS_RATE_LOW/TECH_ERROR_RATE_HIGH/LATENCY_HIGH',
    window_minutes INT NOT NULL COMMENT '统计时间窗口，单位分钟',
    threshold_count INT NULL COMMENT '笔数阈值，用于连续失败类规则',
    threshold_rate DECIMAL(10,4) NULL COMMENT '比例阈值，按百分比保存',
    threshold_millis INT NULL COMMENT '延迟阈值，单位毫秒',
    minimum_sample_count INT NOT NULL DEFAULT 1 COMMENT '最小样本数，避免小样本误触发',
    alert_level VARCHAR(32) NOT NULL COMMENT '预警级别：L1_WARNING/L2_DEGRADED/L3_CIRCUIT_BREAK',
    rule_description VARCHAR(1000) NULL COMMENT '规则说明，进入事件快照和邮件变量',
    auto_degrade TINYINT NOT NULL DEFAULT 0 COMMENT '是否自动降级：0否，1是；当前仅保存配置',
    auto_circuit_break TINYINT NOT NULL DEFAULT 0 COMMENT '是否自动熔断：0否，1是；当前仅保存配置',
    rule_status TINYINT NOT NULL DEFAULT 1 COMMENT '规则状态：0停用，1启用',
    notify_type VARCHAR(32) NOT NULL DEFAULT 'EMAIL' COMMENT '通知方式，当前仅支持 EMAIL',
    email_recipients VARCHAR(1000) NOT NULL COMMENT '邮件收件人，多个邮箱逗号分隔',
    email_cc VARCHAR(1000) NULL COMMENT '邮件抄送人，多个邮箱逗号分隔',
    email_template_code VARCHAR(80) NULL COMMENT '邮件模板编码',
    email_scene_code VARCHAR(64) NOT NULL DEFAULT 'CHANNEL_ALERT' COMMENT '邮件场景编码',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_alert_rule_code_deleted (rule_code, deleted),
    KEY idx_channel_alert_rule_group (rule_group_code, deleted),
    UNIQUE KEY uk_channel_alert_rule_scope_deleted (channel_id, business_type, payment_method, card_brand, rule_type, deleted),
    KEY idx_channel_alert_rule_channel (channel_id, deleted),
    KEY idx_channel_alert_rule_code (channel_code, deleted),
    KEY idx_channel_alert_rule_status (rule_status, deleted),
    KEY idx_channel_alert_rule_type_level (rule_type, alert_level, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道预警规则配置表';

SET @channel_alert_rule_group_column_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'channel_alert_rule'
      AND column_name = 'rule_group_code'
);
SET @channel_alert_rule_group_column_sql := IF(
    @channel_alert_rule_group_column_exists = 0,
    'ALTER TABLE channel_alert_rule ADD COLUMN rule_group_code VARCHAR(64) NULL COMMENT ''规则分组编码，同一次批量配置共用'' AFTER rule_code',
    'SELECT 1'
);
PREPARE channel_alert_rule_group_column_stmt FROM @channel_alert_rule_group_column_sql;
EXECUTE channel_alert_rule_group_column_stmt;
DEALLOCATE PREPARE channel_alert_rule_group_column_stmt;

UPDATE channel_alert_rule
SET rule_group_code = rule_code
WHERE rule_group_code IS NULL OR rule_group_code = '';

SET @channel_alert_rule_group_not_null_sql := IF(
    @channel_alert_rule_group_column_exists = 0,
    'ALTER TABLE channel_alert_rule MODIFY COLUMN rule_group_code VARCHAR(64) NOT NULL COMMENT ''规则分组编码，同一次批量配置共用''',
    'SELECT 1'
);
PREPARE channel_alert_rule_group_not_null_stmt FROM @channel_alert_rule_group_not_null_sql;
EXECUTE channel_alert_rule_group_not_null_stmt;
DEALLOCATE PREPARE channel_alert_rule_group_not_null_stmt;

SET @channel_alert_rule_group_index_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'channel_alert_rule'
      AND index_name = 'idx_channel_alert_rule_group'
);
SET @channel_alert_rule_group_index_sql := IF(
    @channel_alert_rule_group_index_exists = 0,
    'ALTER TABLE channel_alert_rule ADD KEY idx_channel_alert_rule_group (rule_group_code, deleted)',
    'SELECT 1'
);
PREPARE channel_alert_rule_group_index_stmt FROM @channel_alert_rule_group_index_sql;
EXECUTE channel_alert_rule_group_index_stmt;
DEALLOCATE PREPARE channel_alert_rule_group_index_stmt;

SET @channel_alert_rule_card_brand_length := (
    SELECT CHARACTER_MAXIMUM_LENGTH
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'channel_alert_rule'
      AND column_name = 'card_brand'
);
SET @channel_alert_rule_card_brand_sql := IF(
    @channel_alert_rule_card_brand_length IS NOT NULL AND @channel_alert_rule_card_brand_length < 255,
    'ALTER TABLE channel_alert_rule MODIFY COLUMN card_brand VARCHAR(255) NOT NULL DEFAULT ''ALL'' COMMENT ''卡品牌范围，ALL表示全部；多选时使用逗号分隔保存为一个预警范围''',
    'SELECT 1'
);
PREPARE channel_alert_rule_card_brand_stmt FROM @channel_alert_rule_card_brand_sql;
EXECUTE channel_alert_rule_card_brand_stmt;
DEALLOCATE PREPARE channel_alert_rule_card_brand_stmt;

CREATE TABLE IF NOT EXISTS channel_alert_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    event_code VARCHAR(64) NOT NULL COMMENT '预警事件编码',
    rule_id BIGINT NOT NULL COMMENT '触发规则ID',
    rule_code VARCHAR(64) NOT NULL COMMENT '触发规则编码',
    rule_name VARCHAR(128) NOT NULL COMMENT '触发时规则名称快照',
    channel_id BIGINT NOT NULL COMMENT '渠道ID',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    business_type VARCHAR(32) NOT NULL COMMENT '业务类型',
    payment_method VARCHAR(64) NOT NULL DEFAULT 'ALL' COMMENT '支付方式',
    card_brand VARCHAR(255) NOT NULL DEFAULT 'ALL' COMMENT '卡品牌范围，ALL表示全部；多选时使用逗号分隔保存为一个预警范围',
    rule_type VARCHAR(64) NOT NULL COMMENT '规则类型',
    alert_level VARCHAR(32) NOT NULL COMMENT '预警级别',
    window_minutes INT NOT NULL COMMENT '统计窗口分钟数',
    window_start_time DATETIME(3) NOT NULL COMMENT '统计窗口开始时间',
    window_end_time DATETIME(3) NOT NULL COMMENT '统计窗口结束时间',
    sample_count INT NOT NULL DEFAULT 0 COMMENT '窗口样本数',
    failure_count INT NOT NULL DEFAULT 0 COMMENT '窗口失败笔数',
    success_count INT NOT NULL DEFAULT 0 COMMENT '窗口成功笔数',
    success_rate DECIMAL(10,4) NULL COMMENT '窗口成功率百分比',
    error_rate DECIMAL(10,4) NULL COMMENT '窗口异常率百分比',
    max_continuous_failure_count INT NULL COMMENT '最大连续失败笔数',
    average_latency_millis INT NULL COMMENT '平均渠道响应耗时，单位毫秒',
    trigger_value_count INT NULL COMMENT '触发值笔数',
    trigger_value_rate DECIMAL(10,4) NULL COMMENT '触发值比例',
    trigger_value_millis INT NULL COMMENT '触发值耗时，单位毫秒',
    threshold_snapshot JSON NULL COMMENT '触发时阈值快照',
    event_status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT '事件状态：OPEN/ACKNOWLEDGED/RESOLVED',
    notify_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '通知状态：PENDING/SENT/FAILED/SKIPPED',
    trigger_time DATETIME(3) NOT NULL COMMENT '触发时间',
    acknowledged_time DATETIME(3) NULL COMMENT '人工确认时间',
    acknowledged_by VARCHAR(64) NULL COMMENT '人工确认人',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_alert_event_code_deleted (event_code, deleted),
    KEY idx_channel_alert_event_rule (rule_id, trigger_time, deleted),
    KEY idx_channel_alert_event_channel_time (channel_id, trigger_time, deleted),
    KEY idx_channel_alert_event_status_time (event_status, trigger_time, deleted),
    KEY idx_channel_alert_event_type_level (rule_type, alert_level, trigger_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道预警触发事件表';

SET @channel_alert_event_card_brand_length := (
    SELECT CHARACTER_MAXIMUM_LENGTH
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'channel_alert_event'
      AND column_name = 'card_brand'
);
SET @channel_alert_event_card_brand_sql := IF(
    @channel_alert_event_card_brand_length IS NOT NULL AND @channel_alert_event_card_brand_length < 255,
    'ALTER TABLE channel_alert_event MODIFY COLUMN card_brand VARCHAR(255) NOT NULL DEFAULT ''ALL'' COMMENT ''卡品牌范围，ALL表示全部；多选时使用逗号分隔保存为一个预警范围''',
    'SELECT 1'
);
PREPARE channel_alert_event_card_brand_stmt FROM @channel_alert_event_card_brand_sql;
EXECUTE channel_alert_event_card_brand_stmt;
DEALLOCATE PREPARE channel_alert_event_card_brand_stmt;

CREATE TABLE IF NOT EXISTS channel_alert_notify_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    event_id BIGINT NOT NULL COMMENT '预警事件ID',
    event_code VARCHAR(64) NOT NULL COMMENT '预警事件编码',
    rule_id BIGINT NOT NULL COMMENT '预警规则ID',
    rule_code VARCHAR(64) NOT NULL COMMENT '预警规则编码',
    notify_type VARCHAR(32) NOT NULL DEFAULT 'EMAIL' COMMENT '通知方式，当前仅支持 EMAIL',
    notify_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '通知状态：PENDING/SENT/FAILED/SKIPPED',
    email_recipients VARCHAR(1000) NULL COMMENT '邮件收件人快照',
    email_cc VARCHAR(1000) NULL COMMENT '邮件抄送人快照',
    email_template_code VARCHAR(80) NULL COMMENT '邮件模板编码快照',
    email_scene_code VARCHAR(64) NULL COMMENT '邮件场景编码快照',
    send_start_time DATETIME(3) NULL COMMENT '发送开始时间',
    send_end_time DATETIME(3) NULL COMMENT '发送结束时间',
    fail_reason VARCHAR(1000) NULL COMMENT '通知失败原因',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    KEY idx_channel_alert_notify_event (event_id, deleted),
    KEY idx_channel_alert_notify_rule (rule_id, deleted),
    KEY idx_channel_alert_notify_status_time (notify_status, create_time, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道预警通知执行日志表';

INSERT INTO sys_dict_type (dict_name, dict_type, biz_domain, system_builtin, editable, status, deleted)
VALUES
    ('渠道预警规则类型', 'channel_alert_rule_type', 'channel', 1, 1, 1, 0),
    ('渠道预警级别', 'channel_alert_level', 'channel', 1, 1, 1, 0),
    ('渠道预警事件状态', 'channel_alert_event_status', 'channel', 1, 1, 1, 0),
    ('渠道预警通知状态', 'channel_alert_notify_status', 'channel', 1, 1, 1, 0),
    ('邮件场景', 'email_scene_code', 'email', 1, 1, 1, 0)
ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name), biz_domain=VALUES(biz_domain), system_builtin=VALUES(system_builtin), editable=VALUES(editable), status=VALUES(status), deleted=0;

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, is_default, status, deleted)
VALUES
    ('channel_alert_rule_type', '连续失败', 'CONTINUOUS_FAILURE', 'zh-CN', 1, 'danger', 1, 1, 0),
    ('channel_alert_rule_type', '成功率异常', 'SUCCESS_RATE_LOW', 'zh-CN', 2, 'warning', 0, 1, 0),
    ('channel_alert_rule_type', '技术异常比例', 'TECH_ERROR_RATE_HIGH', 'zh-CN', 3, 'danger', 0, 1, 0),
    ('channel_alert_rule_type', '响应延迟', 'LATENCY_HIGH', 'zh-CN', 4, 'warning', 0, 1, 0),
    ('channel_alert_level', 'L1 预警', 'L1_WARNING', 'zh-CN', 1, 'warning', 1, 1, 0),
    ('channel_alert_level', 'L2 降级', 'L2_DEGRADED', 'zh-CN', 2, 'danger', 0, 1, 0),
    ('channel_alert_level', 'L3 熔断', 'L3_CIRCUIT_BREAK', 'zh-CN', 3, 'danger', 0, 1, 0),
    ('channel_alert_event_status', '待处理', 'OPEN', 'zh-CN', 1, 'warning', 1, 1, 0),
    ('channel_alert_event_status', '已确认', 'ACKNOWLEDGED', 'zh-CN', 2, 'success', 0, 1, 0),
    ('channel_alert_event_status', '已恢复', 'RESOLVED', 'zh-CN', 3, 'success', 0, 1, 0),
    ('channel_alert_notify_status', '待发送', 'PENDING', 'zh-CN', 1, 'warning', 1, 1, 0),
    ('channel_alert_notify_status', '已发送', 'SENT', 'zh-CN', 2, 'success', 0, 1, 0),
    ('channel_alert_notify_status', '发送失败', 'FAILED', 'zh-CN', 3, 'danger', 0, 1, 0),
    ('channel_alert_notify_status', '已跳过', 'SKIPPED', 'zh-CN', 4, 'info', 0, 1, 0),
    ('channel_alert_rule_type', 'Continuous Failure', 'CONTINUOUS_FAILURE', 'en-US', 1, 'danger', 1, 1, 0),
    ('channel_alert_rule_type', 'Low Success Rate', 'SUCCESS_RATE_LOW', 'en-US', 2, 'warning', 0, 1, 0),
    ('channel_alert_rule_type', 'High Tech Error Rate', 'TECH_ERROR_RATE_HIGH', 'en-US', 3, 'danger', 0, 1, 0),
    ('channel_alert_rule_type', 'High Latency', 'LATENCY_HIGH', 'en-US', 4, 'warning', 0, 1, 0),
    ('channel_alert_level', 'L1 Warning', 'L1_WARNING', 'en-US', 1, 'warning', 1, 1, 0),
    ('channel_alert_level', 'L2 Degraded', 'L2_DEGRADED', 'en-US', 2, 'danger', 0, 1, 0),
    ('channel_alert_level', 'L3 Circuit Break', 'L3_CIRCUIT_BREAK', 'en-US', 3, 'danger', 0, 1, 0),
    ('channel_alert_event_status', 'Open', 'OPEN', 'en-US', 1, 'warning', 1, 1, 0),
    ('channel_alert_event_status', 'Acknowledged', 'ACKNOWLEDGED', 'en-US', 2, 'success', 0, 1, 0),
    ('channel_alert_event_status', 'Resolved', 'RESOLVED', 'en-US', 3, 'success', 0, 1, 0),
    ('channel_alert_notify_status', 'Pending', 'PENDING', 'en-US', 1, 'warning', 1, 1, 0),
    ('channel_alert_notify_status', 'Sent', 'SENT', 'en-US', 2, 'success', 0, 1, 0),
    ('channel_alert_notify_status', 'Failed', 'FAILED', 'en-US', 3, 'danger', 0, 1, 0),
    ('channel_alert_notify_status', 'Skipped', 'SKIPPED', 'en-US', 4, 'info', 0, 1, 0),
    ('email_scene_code', '渠道预警', 'CHANNEL_ALERT', 'zh-CN', 7, 'danger', 0, 1, 0),
    ('email_scene_code', 'Channel Alert', 'CHANNEL_ALERT', 'en-US', 7, 'danger', 0, 1, 0)
ON DUPLICATE KEY UPDATE dict_label=VALUES(dict_label), dict_sort=VALUES(dict_sort), list_class=VALUES(list_class), is_default=VALUES(is_default), status=VALUES(status), deleted=0;

INSERT INTO msg_email_template (
    template_code, template_name, app_code, scene_code, locale, subject_template, content_type,
    content_template, variable_schema, sensitive_variable_names, status, system_builtin, version_no,
    remark, create_by, update_by, deleted
)
SELECT item.template_code, item.template_name, item.app_code, item.scene_code, item.locale, item.subject_template, item.content_type,
       item.content_template, item.variable_schema, item.sensitive_variable_names, 1, 1, 1,
       item.remark, 'system', 'system', 0
FROM (
    SELECT 'CHANNEL_ALERT_DEFAULT' template_code, '渠道预警通知模板' template_name, 'ADMIN' app_code, 'CHANNEL_ALERT' scene_code, 'zh-CN' locale,
           '【Vexra Admin】渠道预警：${ruleName}' subject_template, 'HTML' content_type,
           '<p>渠道预警已触发，请及时处理。</p><p>渠道：${channelName}（${channelCode}）</p><p>业务类型：${businessType}</p><p>支付方式：${paymentMethod}</p><p>卡品牌：${cardBrand}</p><p>规则类型：${ruleType}</p><p>预警级别：${alertLevel}</p><p>触发时间：${triggerTime}</p><p>触发值：${triggerValue}</p><p>规则说明：${ruleDescription}</p>' content_template,
           '{"ruleName":"渠道连续失败预警","channelName":"示例渠道","channelCode":"DEMO","businessType":"ACQUIRING","paymentMethod":"BANK_CARD","cardBrand":"VISA","ruleType":"CONTINUOUS_FAILURE","alertLevel":"L1_WARNING","triggerTime":"2026-07-17 10:00:00","triggerValue":"3","ruleDescription":"连续失败超过阈值"}' variable_schema,
           '[]' sensitive_variable_names, '系统内置模板：渠道预警通知' remark
    UNION ALL SELECT 'CHANNEL_ALERT_DEFAULT', 'Channel Alert Notification', 'ADMIN', 'CHANNEL_ALERT', 'en-US',
           '[Vexra Admin] Channel Alert: ${ruleName}', 'HTML',
           '<p>A channel alert has been triggered. Please review it promptly.</p><p>Channel: ${channelName} (${channelCode})</p><p>Business Type: ${businessType}</p><p>Payment Method: ${paymentMethod}</p><p>Card Brand: ${cardBrand}</p><p>Rule Type: ${ruleType}</p><p>Alert Level: ${alertLevel}</p><p>Trigger Time: ${triggerTime}</p><p>Trigger Value: ${triggerValue}</p><p>Description: ${ruleDescription}</p>',
           '{"ruleName":"Channel continuous failure alert","channelName":"Demo Channel","channelCode":"DEMO","businessType":"ACQUIRING","paymentMethod":"BANK_CARD","cardBrand":"VISA","ruleType":"CONTINUOUS_FAILURE","alertLevel":"L1_WARNING","triggerTime":"2026-07-17 10:00:00","triggerValue":"3","ruleDescription":"Continuous failures exceeded threshold"}',
           '[]', 'System builtin template: channel alert notification'
) item
WHERE NOT EXISTS (
    SELECT 1 FROM msg_email_template exists_template
    WHERE exists_template.template_code = item.template_code
      AND exists_template.locale = item.locale
      AND exists_template.deleted = 0
);

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, parent.id, item.menu_code, item.menu_name, 'MENU', item.route_path, item.component_path, item.permission_code, item.icon, 1, item.sort_no, 1, 0
FROM sys_menu parent
JOIN (
    SELECT 'admin_channel_alert_rule_v1' menu_code, '渠道预警规则' menu_name, '/channel/alert-rule' route_path, 'channel/alert-rule' component_path, 'channel:alert-rule:list' permission_code, 'Bell' icon, 46 sort_no
    UNION ALL SELECT 'admin_channel_alert_event_v1', '渠道预警事件', '/channel/alert-event', 'channel/alert-event', 'channel:alert-event:list', 'WarningFilled', 47
) item ON 1 = 1
WHERE parent.app_id = 1
  AND parent.menu_code = 'admin_channel_catalog_v1'
  AND parent.deleted = 0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), route_path=VALUES(route_path), component_path=VALUES(component_path), permission_code=VALUES(permission_code), icon=VALUES(icon), visible=VALUES(visible), sort_no=VALUES(sort_no), status=VALUES(status), deleted=0;

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, parent.id, item.menu_code, item.menu_name, 'BUTTON', NULL, NULL, item.permission_code, NULL, 0, item.sort_no, 1, 0
FROM sys_menu parent
JOIN (
    SELECT 'admin_channel_alert_rule_v1' parent_code, 'admin_channel_alert_rule_detail_v1' menu_code, '渠道预警规则详情' menu_name, 'channel:alert-rule:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'admin_channel_alert_rule_v1', 'admin_channel_alert_rule_add_v1', '渠道预警规则新增', 'channel:alert-rule:add', 2
    UNION ALL SELECT 'admin_channel_alert_rule_v1', 'admin_channel_alert_rule_edit_v1', '渠道预警规则修改', 'channel:alert-rule:edit', 3
    UNION ALL SELECT 'admin_channel_alert_rule_v1', 'admin_channel_alert_rule_remove_v1', '渠道预警规则删除', 'channel:alert-rule:remove', 4
    UNION ALL SELECT 'admin_channel_alert_rule_v1', 'admin_channel_alert_rule_status_v1', '渠道预警规则状态', 'channel:alert-rule:status', 5
    UNION ALL SELECT 'admin_channel_alert_event_v1', 'admin_channel_alert_event_detail_v1', '渠道预警事件详情', 'channel:alert-event:detail', 1
    UNION ALL SELECT 'admin_channel_alert_event_v1', 'admin_channel_alert_event_acknowledge_v1', '渠道预警事件确认', 'channel:alert-event:acknowledge', 2
    UNION ALL SELECT 'admin_channel_alert_event_v1', 'admin_channel_alert_event_remove_v1', '渠道预警事件删除', 'channel:alert-event:remove', 3
    UNION ALL SELECT 'admin_channel_alert_event_v1', 'admin_channel_alert_notify_log_v1', '渠道预警通知日志', 'channel:alert-notify-log:list', 4
) item ON item.parent_code = parent.menu_code
WHERE parent.app_id = 1
  AND parent.deleted = 0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), permission_code=VALUES(permission_code), visible=VALUES(visible), sort_no=VALUES(sort_no), status=VALUES(status), deleted=0;

INSERT INTO sys_permission (app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
SELECT 1, menu.id, item.permission_code, item.permission_name, item.permission_type, item.resource_method, item.resource_path, 1, 0
FROM sys_menu menu
JOIN (
    SELECT 'admin_channel_alert_rule_v1' menu_code, 'channel:alert-rule:list' permission_code, '渠道预警规则查询' permission_name, 'MENU' permission_type, 'POST' resource_method, '/admin/channel/alert-rules/search' resource_path
    UNION ALL SELECT 'admin_channel_alert_rule_detail_v1', 'channel:alert-rule:detail', '渠道预警规则详情', 'BUTTON', 'GET', '/admin/channel/alert-rules/*'
    UNION ALL SELECT 'admin_channel_alert_rule_add_v1', 'channel:alert-rule:add', '渠道预警规则新增', 'BUTTON', 'POST', '/admin/channel/alert-rules'
    UNION ALL SELECT 'admin_channel_alert_rule_edit_v1', 'channel:alert-rule:edit', '渠道预警规则修改', 'BUTTON', 'PUT', '/admin/channel/alert-rules/*'
    UNION ALL SELECT 'admin_channel_alert_rule_remove_v1', 'channel:alert-rule:remove', '渠道预警规则删除', 'BUTTON', 'DELETE', '/admin/channel/alert-rules/*'
    UNION ALL SELECT 'admin_channel_alert_rule_status_v1', 'channel:alert-rule:status', '渠道预警规则状态', 'BUTTON', 'PUT', '/admin/channel/alert-rules/*/status'
    UNION ALL SELECT 'admin_channel_alert_event_v1', 'channel:alert-event:list', '渠道预警事件查询', 'MENU', 'POST', '/admin/channel/alert-events/search'
    UNION ALL SELECT 'admin_channel_alert_event_detail_v1', 'channel:alert-event:detail', '渠道预警事件详情', 'BUTTON', 'GET', '/admin/channel/alert-events/*'
    UNION ALL SELECT 'admin_channel_alert_event_acknowledge_v1', 'channel:alert-event:acknowledge', '渠道预警事件确认', 'BUTTON', 'PUT', '/admin/channel/alert-events/*/acknowledge'
    UNION ALL SELECT 'admin_channel_alert_event_remove_v1', 'channel:alert-event:remove', '渠道预警事件删除', 'BUTTON', 'DELETE', '/admin/channel/alert-events/*'
    UNION ALL SELECT 'admin_channel_alert_notify_log_v1', 'channel:alert-notify-log:list', '渠道预警通知日志查询', 'BUTTON', 'POST', '/admin/channel/alert-notify-logs/search'
) item ON item.menu_code = menu.menu_code
WHERE menu.app_id = 1
  AND menu.deleted = 0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id), permission_name=VALUES(permission_name), permission_type=VALUES(permission_type), resource_method=VALUES(resource_method), resource_path=VALUES(resource_path), status=VALUES(status), deleted=0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 1, role.id, menu.id, 0
FROM sys_role role
JOIN sys_menu menu ON menu.app_id = role.app_id
WHERE role.app_id = 1
  AND role.role_code IN ('ADMIN_OPERATOR', 'ADMIN')
  AND role.deleted = 0
  AND menu.menu_code IN (
      'admin_channel_alert_rule_v1',
      'admin_channel_alert_event_v1',
      'admin_channel_alert_rule_detail_v1',
      'admin_channel_alert_rule_add_v1',
      'admin_channel_alert_rule_edit_v1',
      'admin_channel_alert_rule_remove_v1',
      'admin_channel_alert_rule_status_v1',
      'admin_channel_alert_event_detail_v1',
      'admin_channel_alert_event_acknowledge_v1',
      'admin_channel_alert_event_remove_v1',
      'admin_channel_alert_notify_log_v1'
  )
  AND menu.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 1, role.id, permission.id, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id
WHERE role.app_id = 1
  AND role.role_code IN ('ADMIN_OPERATOR', 'ADMIN')
  AND role.deleted = 0
  AND permission.permission_code IN (
      'channel:alert-rule:list',
      'channel:alert-rule:detail',
      'channel:alert-rule:add',
      'channel:alert-rule:edit',
      'channel:alert-rule:remove',
      'channel:alert-rule:status',
      'channel:alert-event:list',
      'channel:alert-event:detail',
      'channel:alert-event:acknowledge',
      'channel:alert-event:remove',
      'channel:alert-notify-log:list'
  )
  AND permission.deleted = 0;
