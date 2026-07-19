CREATE TABLE IF NOT EXISTS security_intercept_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_no VARCHAR(64) NOT NULL COMMENT '安全事件号',
    event_time DATETIME(3) NOT NULL COMMENT '事件发生时间',
    source_layer VARCHAR(32) NOT NULL COMMENT '来源层级：OPENAPI/CHANNEL/GATEWAY',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    risk_level VARCHAR(16) NOT NULL COMMENT '风险等级：LOW/MEDIUM/HIGH/CRITICAL',
    action VARCHAR(32) NOT NULL COMMENT '处置动作：BLOCK/REVIEW/LOG',
    merchant_id VARCHAR(32) NULL COMMENT '商户号',
    client_ip VARCHAR(45) NULL COMMENT '客户端IP',
    request_method VARCHAR(16) NULL COMMENT '请求方法',
    request_path VARCHAR(512) NULL COMMENT '请求路径',
    trace_id VARCHAR(64) NULL COMMENT '链路追踪ID',
    request_id VARCHAR(64) NULL COMMENT '请求ID',
    user_agent VARCHAR(512) NULL COMMENT '脱敏或截断后的User-Agent',
    reason_code VARCHAR(64) NULL COMMENT '拦截原因码',
    reason_message VARCHAR(512) NULL COMMENT '脱敏后的拦截原因说明',
    service_name VARCHAR(64) NULL COMMENT '记录事件的服务名',
    hit_rule_code VARCHAR(64) NULL COMMENT '命中的安全规则编码',
    header_summary VARCHAR(1024) NULL COMMENT '脱敏后的请求头摘要，禁止保存Authorization/Cookie/密钥/完整密文',
    process_status TINYINT NOT NULL DEFAULT 0 COMMENT '处理状态：0未处理，1已处理，2忽略',
    process_remark VARCHAR(512) NULL COMMENT '处理备注',
    processed_by VARCHAR(64) NULL COMMENT '处理人',
    processed_time DATETIME(3) NULL COMMENT '处理时间',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_security_intercept_event_no (event_no),
    KEY idx_security_intercept_event_time (event_time, id),
    KEY idx_security_intercept_event_merchant_time (merchant_id, event_time),
    KEY idx_security_intercept_event_ip_time (client_ip, event_time),
    KEY idx_security_intercept_event_type_time (event_type, event_time),
    KEY idx_security_intercept_event_risk_time (risk_level, event_time),
    KEY idx_security_intercept_event_trace (trace_id),
    KEY idx_security_intercept_event_process_time (process_status, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全拦截事件';

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, parent.id, 'security_intercept_event_v1', '安全拦截事件', 'MENU', '/monitor/security-intercept-event', 'security/intercept-event', 'security:intercept-event:list', 'WarnTriangleFilled', 1, 90, 1, 0
FROM sys_menu parent
WHERE parent.app_id = 1
  AND parent.menu_code = 'system_monitor'
  AND parent.deleted = 0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type), route_path=VALUES(route_path), component_path=VALUES(component_path), permission_code=VALUES(permission_code), icon=VALUES(icon), visible=VALUES(visible), sort_no=VALUES(sort_no), status=VALUES(status), deleted=0;

UPDATE sys_menu
SET visible = 0,
    status = 0,
    deleted = 1
WHERE app_id = 1
  AND menu_code = 'security_center_v1'
  AND deleted = 0;

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, parent.id, item.menu_code, item.menu_name, 'BUTTON', NULL, NULL, item.permission_code, NULL, 0, item.sort_no, 1, 0
FROM sys_menu parent
JOIN (
    SELECT 'security_intercept_event_list_v1' menu_code, '安全拦截事件查询' menu_name, 'security:intercept-event:list' permission_code, 1 sort_no
    UNION ALL SELECT 'security_intercept_event_detail_v1', '安全拦截事件详情', 'security:intercept-event:detail', 2
    UNION ALL SELECT 'security_intercept_event_mark_v1', '安全拦截事件处理标记', 'security:intercept-event:mark', 3
    UNION ALL SELECT 'security_intercept_event_export_v1', '安全拦截事件导出', 'security:intercept-event:export', 4
) item ON 1 = 1
WHERE parent.app_id = 1
  AND parent.menu_code = 'security_intercept_event_v1'
  AND parent.deleted = 0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type), route_path=VALUES(route_path), component_path=VALUES(component_path), permission_code=VALUES(permission_code), visible=VALUES(visible), sort_no=VALUES(sort_no), status=VALUES(status), deleted=0;

INSERT INTO sys_permission (app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
SELECT 1, menu.id, item.permission_code, item.permission_name, 'BUTTON', item.resource_method, item.resource_path, 1, 0
FROM sys_menu menu
JOIN (
    SELECT 'security_intercept_event_list_v1' menu_code, 'security:intercept-event:list' permission_code, '安全拦截事件查询' permission_name, 'POST' resource_method, '/admin/security/intercept-events/search' resource_path
    UNION ALL SELECT 'security_intercept_event_detail_v1', 'security:intercept-event:detail', '安全拦截事件详情', 'GET', '/admin/security/intercept-events/*'
    UNION ALL SELECT 'security_intercept_event_mark_v1', 'security:intercept-event:mark', '安全拦截事件处理标记', 'PUT', '/admin/security/intercept-events/*/mark'
    UNION ALL SELECT 'security_intercept_event_export_v1', 'security:intercept-event:export', '安全拦截事件导出', 'POST', '/admin/security/intercept-events/export'
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
      'system_monitor',
      'security_intercept_event_v1',
      'security_intercept_event_list_v1',
      'security_intercept_event_detail_v1',
      'security_intercept_event_mark_v1',
      'security_intercept_event_export_v1'
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
      'security:intercept-event:list',
      'security:intercept-event:detail',
      'security:intercept-event:mark',
      'security:intercept-event:export'
  )
  AND permission.deleted = 0;
