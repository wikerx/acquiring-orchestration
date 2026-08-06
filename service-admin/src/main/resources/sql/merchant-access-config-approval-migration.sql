-- 商户来源网址与 IP 白名单审批字段迁移草案。
-- 执行前需备份两张目标表并确认不存在未完成的同表 DDL；本脚本未在任何环境自动执行。

SET NAMES utf8mb4;

ALTER TABLE merchant_ip_whitelist
    ADD COLUMN approval_status TINYINT NOT NULL DEFAULT 1 COMMENT '审核状态：0待审核，1审核通过，2审核拒绝' AFTER status,
    ADD COLUMN approval_remark VARCHAR(500) NULL COMMENT '审批说明，审核拒绝时必填' AFTER approval_status,
    ADD COLUMN submit_source VARCHAR(16) NOT NULL DEFAULT 'ADMIN' COMMENT '提交来源：ADMIN、MERCHANT' AFTER approval_remark,
    ADD COLUMN review_by VARCHAR(64) NULL COMMENT '审核人账号或姓名' AFTER submit_source,
    ADD COLUMN review_time DATETIME(3) NULL COMMENT '审核时间' AFTER review_by;

UPDATE merchant_ip_whitelist
SET approval_status = 1,
    submit_source = 'ADMIN'
WHERE approval_status IS NULL OR submit_source IS NULL OR submit_source = '';

ALTER TABLE merchant_ip_whitelist
    DROP INDEX idx_merchant_ip_whitelist_lookup,
    ADD INDEX idx_merchant_ip_whitelist_lookup (merchant_id, ip_value, approval_status, status, deleted),
    ADD INDEX idx_merchant_ip_whitelist_approval (approval_status, submit_source, gmt_create, id);

ALTER TABLE risk_rule_source_url
    ADD COLUMN approval_status TINYINT NOT NULL DEFAULT 1 COMMENT '审核状态：0待审核，1审核通过，2审核拒绝' AFTER status,
    ADD COLUMN approval_remark VARCHAR(500) NULL COMMENT '审批说明，审核拒绝时必填' AFTER approval_status,
    ADD COLUMN submit_source VARCHAR(16) NOT NULL DEFAULT 'ADMIN' COMMENT '提交来源：ADMIN、MERCHANT' AFTER approval_remark,
    ADD COLUMN review_by VARCHAR(64) NULL COMMENT '审核人账号或姓名' AFTER submit_source,
    ADD COLUMN review_time DATETIME(3) NULL COMMENT '审核时间' AFTER review_by;

UPDATE risk_rule_source_url
SET approval_status = 1,
    submit_source = 'ADMIN'
WHERE approval_status IS NULL OR submit_source IS NULL OR submit_source = '';

ALTER TABLE risk_rule_source_url
    DROP INDEX idx_rule_source_url_trade_lookup,
    ADD INDEX idx_rule_source_url_trade_lookup (merchant_id, source_host, approval_status, status, deleted, effective_time, expire_time),
    ADD INDEX idx_rule_source_url_approval (approval_status, submit_source, create_time, id);

-- 已部署环境补充管理端 IP 白名单审批按钮、接口权限和默认后台角色授权。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, sort_no, status, deleted
)
SELECT 1, parent.id, 'merchant_ip_whitelist_approve_v1', '商户IP白名单审批', 'BUTTON', NULL, NULL,
       'merchant:ip-whitelist:approve', NULL, 0, 9, 1, 0
FROM sys_menu parent
WHERE parent.app_id = 1
  AND parent.menu_code = 'merchant_ip_whitelist_manage_v1'
  AND parent.deleted = 0
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_name = VALUES(menu_name),
    permission_code = VALUES(permission_code),
    visible = 0,
    sort_no = VALUES(sort_no),
    status = 1,
    deleted = 0;

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, status, deleted
)
SELECT 1, menu.id, 'merchant:ip-whitelist:approve', '商户IP白名单审批', 'BUTTON',
       'PUT', '/admin/merchant/ip-whitelist/*/approval', 1, 0
FROM sys_menu menu
WHERE menu.app_id = 1
  AND menu.menu_code = 'merchant_ip_whitelist_approve_v1'
  AND menu.deleted = 0
ON DUPLICATE KEY UPDATE
    menu_id = VALUES(menu_id),
    permission_name = VALUES(permission_name),
    permission_type = VALUES(permission_type),
    resource_method = VALUES(resource_method),
    resource_path = VALUES(resource_path),
    status = 1,
    deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 1, role.id, menu.id, 0
FROM sys_role role
JOIN sys_menu menu ON menu.app_id = role.app_id
WHERE role.app_id = 1
  AND role.role_code IN ('ADMIN_OPERATOR', 'ADMIN')
  AND role.deleted = 0
  AND menu.menu_code = 'merchant_ip_whitelist_approve_v1'
  AND menu.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 1, role.id, permission.id, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id
WHERE role.app_id = 1
  AND role.role_code IN ('ADMIN_OPERATOR', 'ADMIN')
  AND role.deleted = 0
  AND permission.permission_code = 'merchant:ip-whitelist:approve'
  AND permission.deleted = 0;

INSERT INTO sys_dict_data (
    dict_type, dict_label, dict_value, locale, dict_sort, list_class, is_default, status, deleted
)
VALUES
    ('email_scene_code', '商户访问配置审批', 'MERCHANT_ACCESS_CONFIG_APPROVAL', 'zh-CN', 11, 'warning', 0, 1, 0),
    ('email_scene_code', 'Merchant Access Configuration Approval', 'MERCHANT_ACCESS_CONFIG_APPROVAL', 'en-US', 11, 'warning', 0, 1, 0)
ON DUPLICATE KEY UPDATE
    dict_label = VALUES(dict_label),
    dict_sort = VALUES(dict_sort),
    list_class = VALUES(list_class),
    status = 1,
    deleted = 0;

INSERT INTO msg_email_template (
    template_code, template_name, app_code, scene_code, locale, subject_template, content_type,
    content_template, variable_schema, sensitive_variable_names, status, system_builtin, version_no,
    remark, create_by, update_by, deleted
)
SELECT item.template_code, item.template_name, 'MERCHANT', 'MERCHANT_ACCESS_CONFIG_APPROVAL', item.locale,
       item.subject_template, 'HTML', item.content_template,
       '{"systemName":"Vexra Merchant","merchantName":"Example Merchant","merchantId":"M10000001","configValue":"https://shop.example.com","transactionStatusText":"Allowed","reviewTime":"2026-08-06 10:00:00","rejectReason":"The submitted value could not be verified."}',
       '[]', 1, 1, 1, '系统内置模板：商户访问配置审批结果通知', 'system', 'system', 0
FROM (
    SELECT 'MERCHANT_SOURCE_URL_APPROVED' template_code, '商户来源网址审核通过通知' template_name, 'zh-CN' locale,
           '【${systemName}】商户来源网址审核通过' subject_template,
           '<div style="font-family:Arial,sans-serif;line-height:1.7"><h2>商户来源网址审核通过</h2><p>您好，${merchantName}：</p><p>您提交的来源网址 <strong>${configValue}</strong> 已审核通过。</p><p>当前交易状态：<strong>${transactionStatusText}</strong></p><p>审核时间：${reviewTime}</p></div>' content_template
    UNION ALL SELECT 'MERCHANT_SOURCE_URL_REJECTED', '商户来源网址审核拒绝通知', 'zh-CN',
           '【${systemName}】商户来源网址审核未通过',
           '<div style="font-family:Arial,sans-serif;line-height:1.7"><h2>商户来源网址审核未通过</h2><p>您好，${merchantName}：</p><p>您提交的来源网址 <strong>${configValue}</strong> 未通过审核，当前禁止交易。</p><p>拒绝原因：<strong>${rejectReason}</strong></p><p>审核时间：${reviewTime}</p></div>'
    UNION ALL SELECT 'MERCHANT_IP_WHITELIST_APPROVED', '商户 IP 白名单审核通过通知', 'zh-CN',
           '【${systemName}】商户 IP 白名单审核通过',
           '<div style="font-family:Arial,sans-serif;line-height:1.7"><h2>商户 IP 白名单审核通过</h2><p>您好，${merchantName}：</p><p>您提交的 IP <strong>${configValue}</strong> 已审核通过。</p><p>当前交易状态：<strong>${transactionStatusText}</strong></p><p>审核时间：${reviewTime}</p></div>'
    UNION ALL SELECT 'MERCHANT_IP_WHITELIST_REJECTED', '商户 IP 白名单审核拒绝通知', 'zh-CN',
           '【${systemName}】商户 IP 白名单审核未通过',
           '<div style="font-family:Arial,sans-serif;line-height:1.7"><h2>商户 IP 白名单审核未通过</h2><p>您好，${merchantName}：</p><p>您提交的 IP <strong>${configValue}</strong> 未通过审核，当前禁止交易。</p><p>拒绝原因：<strong>${rejectReason}</strong></p><p>审核时间：${reviewTime}</p></div>'
    UNION ALL SELECT 'MERCHANT_SOURCE_URL_APPROVED', 'Merchant Source URL Approved', 'en-US',
           '[${systemName}] Source URL approved',
           '<div style="font-family:Arial,sans-serif;line-height:1.7"><h2>Source URL approved</h2><p>Hello ${merchantName},</p><p>Your source URL <strong>${configValue}</strong> has been approved.</p><p>Current transaction status: <strong>${transactionStatusText}</strong></p><p>Reviewed at: ${reviewTime}</p></div>'
    UNION ALL SELECT 'MERCHANT_SOURCE_URL_REJECTED', 'Merchant Source URL Rejected', 'en-US',
           '[${systemName}] Source URL rejected',
           '<div style="font-family:Arial,sans-serif;line-height:1.7"><h2>Source URL rejected</h2><p>Hello ${merchantName},</p><p>Your source URL <strong>${configValue}</strong> was rejected and remains prohibited.</p><p>Reason: <strong>${rejectReason}</strong></p><p>Reviewed at: ${reviewTime}</p></div>'
    UNION ALL SELECT 'MERCHANT_IP_WHITELIST_APPROVED', 'Merchant IP Whitelist Approved', 'en-US',
           '[${systemName}] IP whitelist entry approved',
           '<div style="font-family:Arial,sans-serif;line-height:1.7"><h2>IP whitelist entry approved</h2><p>Hello ${merchantName},</p><p>Your IP <strong>${configValue}</strong> has been approved.</p><p>Current transaction status: <strong>${transactionStatusText}</strong></p><p>Reviewed at: ${reviewTime}</p></div>'
    UNION ALL SELECT 'MERCHANT_IP_WHITELIST_REJECTED', 'Merchant IP Whitelist Rejected', 'en-US',
           '[${systemName}] IP whitelist entry rejected',
           '<div style="font-family:Arial,sans-serif;line-height:1.7"><h2>IP whitelist entry rejected</h2><p>Hello ${merchantName},</p><p>Your IP <strong>${configValue}</strong> was rejected and remains prohibited.</p><p>Reason: <strong>${rejectReason}</strong></p><p>Reviewed at: ${reviewTime}</p></div>'
) item
ON DUPLICATE KEY UPDATE
    template_name = VALUES(template_name),
    subject_template = VALUES(subject_template),
    content_template = VALUES(content_template),
    variable_schema = VALUES(variable_schema),
    status = 1,
    system_builtin = 1,
    update_by = 'system',
    deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, sort_no, status, deleted
)
SELECT 2, 0, 'merchant_access_config_catalog_v1', '访问配置', 'CATALOG', '/access-config', NULL,
       NULL, 'Lock', 1, 81, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE app_id = 2 AND menu_code = 'merchant_access_config_catalog_v1' AND deleted = 0
);

SET @merchant_access_catalog_id = (
    SELECT MIN(id) FROM sys_menu WHERE app_id = 2 AND menu_code = 'merchant_access_config_catalog_v1' AND deleted = 0
);

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, sort_no, status, deleted
)
SELECT 2, @merchant_access_catalog_id, item.menu_code, item.menu_name, 'MENU', item.route_path,
       item.component_path, 'merchant:access-config:view', item.icon, 1, item.sort_no, 1, 0
FROM (
    SELECT 'merchant_source_url_v1' menu_code, '商户来源网址' menu_name,
           '/access-config/source-url' route_path, 'access-config/source-url' component_path, 'Link' icon, 82 sort_no
    UNION ALL SELECT 'merchant_ip_whitelist_v1', 'IP白名单',
           '/access-config/ip-whitelist', 'access-config/ip-whitelist', 'Connection', 83
) item
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing
    WHERE existing.app_id = 2 AND existing.menu_code = item.menu_code AND existing.deleted = 0
);

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, status, deleted
)
SELECT 2, MIN(menu.id), item.permission_code, item.permission_name, item.permission_type,
       item.resource_method, '/merchant/access-config/*', 1, 0
FROM sys_menu menu
JOIN (
    SELECT 'merchant:access-config:view' permission_code, '商户访问配置查询' permission_name,
           'MENU' permission_type, 'GET' resource_method
    UNION ALL SELECT 'merchant:access-config:submit', '商户访问配置提交', 'BUTTON', 'POST'
) item ON 1 = 1
WHERE menu.app_id = 2 AND menu.menu_code = 'merchant_source_url_v1' AND menu.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing
      WHERE existing.app_id = 2 AND existing.permission_code = item.permission_code AND existing.deleted = 0
  )
GROUP BY item.permission_code, item.permission_name, item.permission_type, item.resource_method;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 2, role.id, menu.id, 0
FROM sys_role role
JOIN sys_menu menu ON menu.app_id = role.app_id AND menu.deleted = 0
WHERE role.app_id = 2 AND role.deleted = 0
  AND role.role_code LIKE 'MERCHANT\_%'
  AND menu.menu_code IN ('merchant_access_config_catalog_v1', 'merchant_source_url_v1', 'merchant_ip_whitelist_v1');

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 2, role.id, permission.id, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id AND permission.deleted = 0
WHERE role.app_id = 2 AND role.deleted = 0
  AND role.role_code LIKE 'MERCHANT\_%'
  AND permission.permission_code = 'merchant:access-config:view';

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 2, role.id, permission.id, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id AND permission.deleted = 0
WHERE role.app_id = 2 AND role.deleted = 0
  AND (role.role_code LIKE 'MERCHANT_ADMIN\_%' OR role.role_code LIKE 'MERCHANT_OPERATOR\_%')
  AND permission.permission_code = 'merchant:access-config:submit';
