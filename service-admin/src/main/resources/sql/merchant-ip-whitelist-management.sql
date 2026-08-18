CREATE TABLE IF NOT EXISTS merchant_openapi_access_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id VARCHAR(32) NOT NULL COMMENT '商户号，对应 base_merchant_info.merchant_id',
    ip_whitelist_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用 OpenAPI 请求 IP 白名单校验：1启用，0关闭',
    remark VARCHAR(512) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，删除时写入主键ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_openapi_access_config_merchant_deleted (merchant_id, deleted),
    KEY idx_merchant_openapi_access_config_time (gmt_modified, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户 OpenAPI 入站访问配置';

CREATE TABLE IF NOT EXISTS merchant_ip_whitelist (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id VARCHAR(32) NOT NULL COMMENT '商户号，对应 base_merchant_info.merchant_id',
    ip_type VARCHAR(8) NOT NULL COMMENT 'IP类型：IPv4/IPv6',
    ip_value VARCHAR(45) NOT NULL COMMENT '规范化后的精确 IP 地址',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '交易状态：1允许交易，0禁止交易',
    approval_status TINYINT NOT NULL DEFAULT 1 COMMENT '审核状态：0待审核，1审核通过，2审核拒绝',
    approval_remark VARCHAR(500) NULL COMMENT '审批说明，审核拒绝时必填',
    submit_source VARCHAR(16) NOT NULL DEFAULT 'ADMIN' COMMENT '提交来源：ADMIN、MERCHANT',
    review_by VARCHAR(64) NULL COMMENT '审核人账号或姓名',
    review_time DATETIME(3) NULL COMMENT '审核时间',
    remark VARCHAR(512) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，删除时写入主键ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_ip_whitelist_merchant_ip_deleted (merchant_id, ip_value, deleted),
    KEY idx_merchant_ip_whitelist_lookup (merchant_id, ip_value, approval_status, status, deleted),
    KEY idx_merchant_ip_whitelist_merchant_time (merchant_id, gmt_modified, id),
    KEY idx_merchant_ip_whitelist_approval (approval_status, submit_source, gmt_create, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户 OpenAPI IP 白名单';

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, parent.id, 'merchant_ip_whitelist_manage_v1', '商户IP白名单管理', 'MENU', '/merchant/ip-whitelist', 'merchant/ip-whitelist', 'merchant:ip-whitelist:list', 'Lock', 1, 42, 1, 0
FROM sys_menu parent
WHERE parent.app_id = 1
  AND parent.menu_code = 'merchant_manage'
  AND parent.deleted = 0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type), route_path=VALUES(route_path), component_path=VALUES(component_path), permission_code=VALUES(permission_code), icon=VALUES(icon), visible=VALUES(visible), sort_no=VALUES(sort_no), status=VALUES(status), deleted=0;

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, parent.id, item.menu_code, item.menu_name, 'BUTTON', NULL, NULL, item.permission_code, NULL, 0, item.sort_no, 1, 0
FROM sys_menu parent
JOIN (
    SELECT 'merchant_ip_whitelist_list_v1' menu_code, '商户IP白名单查询' menu_name, 'merchant:ip-whitelist:list' permission_code, 1 sort_no
    UNION ALL SELECT 'merchant_ip_whitelist_detail_v1', '商户IP白名单详情', 'merchant:ip-whitelist:detail', 2
    UNION ALL SELECT 'merchant_ip_whitelist_add_v1', '商户IP白名单新增', 'merchant:ip-whitelist:add', 3
    UNION ALL SELECT 'merchant_ip_whitelist_edit_v1', '商户IP白名单编辑', 'merchant:ip-whitelist:edit', 4
    UNION ALL SELECT 'merchant_ip_whitelist_remove_v1', '商户IP白名单删除', 'merchant:ip-whitelist:remove', 5
    UNION ALL SELECT 'merchant_ip_whitelist_status_v1', '商户IP白名单启停', 'merchant:ip-whitelist:status', 6
    UNION ALL SELECT 'merchant_ip_whitelist_config_v1', '商户IP白名单开关', 'merchant:ip-whitelist:config', 7
    UNION ALL SELECT 'merchant_ip_whitelist_export_v1', '商户IP白名单导出', 'merchant:ip-whitelist:export', 8
    UNION ALL SELECT 'merchant_ip_whitelist_approve_v1', '商户IP白名单审批', 'merchant:ip-whitelist:approve', 9
) item ON 1 = 1
WHERE parent.app_id = 1
  AND parent.menu_code = 'merchant_ip_whitelist_manage_v1'
  AND parent.deleted = 0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type), route_path=VALUES(route_path), component_path=VALUES(component_path), permission_code=VALUES(permission_code), visible=VALUES(visible), sort_no=VALUES(sort_no), status=VALUES(status), deleted=0;

INSERT INTO sys_permission (app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
SELECT 1, menu.id, item.permission_code, item.permission_name, 'BUTTON', item.resource_method, item.resource_path, 1, 0
FROM sys_menu menu
JOIN (
    SELECT 'merchant_ip_whitelist_list_v1' menu_code, 'merchant:ip-whitelist:list' permission_code, '商户IP白名单查询' permission_name, 'POST' resource_method, '/admin/merchant/ip-whitelist/search' resource_path
    UNION ALL SELECT 'merchant_ip_whitelist_detail_v1', 'merchant:ip-whitelist:detail', '商户IP白名单详情', 'GET', '/admin/merchant/ip-whitelist/*'
    UNION ALL SELECT 'merchant_ip_whitelist_add_v1', 'merchant:ip-whitelist:add', '商户IP白名单新增', 'POST', '/admin/merchant/ip-whitelist'
    UNION ALL SELECT 'merchant_ip_whitelist_edit_v1', 'merchant:ip-whitelist:edit', '商户IP白名单编辑', 'PUT', '/admin/merchant/ip-whitelist/*'
    UNION ALL SELECT 'merchant_ip_whitelist_remove_v1', 'merchant:ip-whitelist:remove', '商户IP白名单删除', 'DELETE', '/admin/merchant/ip-whitelist/*'
    UNION ALL SELECT 'merchant_ip_whitelist_status_v1', 'merchant:ip-whitelist:status', '商户IP白名单启停', 'PUT', '/admin/merchant/ip-whitelist/*/status'
    UNION ALL SELECT 'merchant_ip_whitelist_config_v1', 'merchant:ip-whitelist:config', '商户IP白名单开关', 'PUT', '/admin/merchant/ip-whitelist/config'
    UNION ALL SELECT 'merchant_ip_whitelist_export_v1', 'merchant:ip-whitelist:export', '商户IP白名单导出', 'POST', '/admin/merchant/ip-whitelist/export'
    UNION ALL SELECT 'merchant_ip_whitelist_approve_v1', 'merchant:ip-whitelist:approve', '商户IP白名单审批', 'PUT', '/admin/merchant/ip-whitelist/*/approval'
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
      'merchant_ip_whitelist_manage_v1',
      'merchant_ip_whitelist_list_v1',
      'merchant_ip_whitelist_detail_v1',
      'merchant_ip_whitelist_add_v1',
      'merchant_ip_whitelist_edit_v1',
      'merchant_ip_whitelist_remove_v1',
      'merchant_ip_whitelist_status_v1',
      'merchant_ip_whitelist_config_v1',
      'merchant_ip_whitelist_export_v1',
      'merchant_ip_whitelist_approve_v1'
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
      'merchant:ip-whitelist:list',
      'merchant:ip-whitelist:detail',
      'merchant:ip-whitelist:add',
      'merchant:ip-whitelist:edit',
      'merchant:ip-whitelist:remove',
      'merchant:ip-whitelist:status',
      'merchant:ip-whitelist:config',
      'merchant:ip-whitelist:export',
      'merchant:ip-whitelist:approve'
  )
  AND permission.deleted = 0;
