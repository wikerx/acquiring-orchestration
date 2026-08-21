-- 管理端账户扣减：独立申请、三段审批、复核扣减和不可变余额流水。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_fund_deduction (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    deduction_no VARCHAR(64) NOT NULL COMMENT '账户扣减申请号',
    account_id BIGINT NOT NULL COMMENT '资金账户ID',
    merchant_id VARCHAR(64) NOT NULL COMMENT '商户号',
    currency CHAR(3) NOT NULL COMMENT '账户结算币种',
    amount DECIMAL(24,8) NOT NULL COMMENT '扣减金额，必须大于0且不超过100000000',
    deduction_category VARCHAR(32) NOT NULL COMMENT '扣减类型：ACCOUNT_CORRECTION、EXTRA_FEE、PENALTY、OTHER',
    deduction_status VARCHAR(24) NOT NULL COMMENT '状态：PENDING_AUDIT、PENDING_RECHECK、POSTED、REJECTED',
    reason VARCHAR(500) NOT NULL COMMENT '商户可见的完整扣减说明',
    submit_by_id BIGINT NULL COMMENT '提交人账号ID',
    submit_by_name VARCHAR(128) NOT NULL COMMENT '提交人名称快照',
    submit_login_account VARCHAR(128) NULL COMMENT '提交人登录账号快照，用于admin自审边界审计',
    submit_time DATETIME(3) NOT NULL COMMENT '提交时间',
    audit_by_id BIGINT NULL COMMENT '审核人账号ID',
    audit_by_name VARCHAR(128) NULL COMMENT '审核人名称快照',
    audit_comment VARCHAR(500) NULL COMMENT '审核意见',
    audit_time DATETIME(3) NULL COMMENT '审核时间',
    recheck_by_id BIGINT NULL COMMENT '复核人账号ID',
    recheck_by_name VARCHAR(128) NULL COMMENT '复核人名称快照',
    recheck_comment VARCHAR(500) NULL COMMENT '复核意见',
    recheck_time DATETIME(3) NULL COMMENT '复核时间',
    reject_by_id BIGINT NULL COMMENT '驳回人账号ID',
    reject_by_name VARCHAR(128) NULL COMMENT '驳回人名称快照',
    reject_comment VARCHAR(500) NULL COMMENT '驳回原因',
    reject_time DATETIME(3) NULL COMMENT '驳回时间',
    request_id VARCHAR(64) NOT NULL COMMENT '客户端唯一请求号',
    ledger_no VARCHAR(64) NULL COMMENT '最终扣减余额流水号',
    posted_time DATETIME(3) NULL COMMENT '最终入账时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fund_deduction_no (deduction_no),
    UNIQUE KEY uk_fund_deduction_request (request_id),
    UNIQUE KEY uk_fund_deduction_ledger (ledger_no),
    KEY idx_fund_deduction_list (deleted, create_time, id),
    KEY idx_fund_deduction_status_time (deduction_status, deleted, create_time, id),
    KEY idx_fund_deduction_category_time (deduction_category, deleted, create_time, id),
    KEY idx_fund_deduction_merchant_time (merchant_id, deleted, create_time, id),
    KEY idx_fund_deduction_account_time (account_id, deleted, create_time, id),
    CONSTRAINT chk_fund_deduction_amount CHECK (amount > 0 AND amount <= 100000000),
    CONSTRAINT chk_fund_deduction_category CHECK (
        deduction_category IN ('ACCOUNT_CORRECTION', 'EXTRA_FEE', 'PENALTY', 'OTHER')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户资金账户扣减申请和审批表';

START TRANSACTION;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, 'admin_fund_deduction_v1', '账户扣减', 'MENU',
       '/fund/deduction', 'fund/deduction', 'fund:deduction:list', 'RemoveFilled',
       1, 1, 0, 44, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_manage'
                    AND parent.deleted = 0
WHERE app.app_code = 'ADMIN' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = app.id
        AND existing.menu_code = 'admin_fund_deduction_v1'
        AND existing.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_manage'
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = '账户扣减',
    menu.menu_type = 'MENU',
    menu.route_path = '/fund/deduction',
    menu.component_path = 'fund/deduction',
    menu.permission_code = 'fund:deduction:list',
    menu.icon = 'RemoveFilled',
    menu.visible = 1,
    menu.keep_alive = 1,
    menu.external_link = 0,
    menu.sort_no = 44,
    menu.status = 1
WHERE menu.menu_code = 'admin_fund_deduction_v1' AND menu.deleted = 0;

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
SET menu.sort_no = 45
WHERE menu.menu_code = 'admin_fund_ledger_all_v1' AND menu.deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, item.menu_code, item.menu_name, 'BUTTON', NULL, NULL,
       item.permission_code, NULL, 0, 0, 0, item.sort_no, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'admin_fund_deduction_v1'
                    AND parent.deleted = 0
JOIN (
    SELECT 'admin_fund_deduction_detail_v1' menu_code, '账户扣减详情' menu_name, 'fund:deduction:detail' permission_code, 101 sort_no
    UNION ALL SELECT 'admin_fund_deduction_add_v1', '提交扣减申请', 'fund:deduction:add', 102
    UNION ALL SELECT 'admin_fund_deduction_audit_v1', '审核扣减申请', 'fund:deduction:audit', 103
    UNION ALL SELECT 'admin_fund_deduction_recheck_v1', '复核扣减入账', 'fund:deduction:recheck', 104
    UNION ALL SELECT 'admin_fund_deduction_reject_v1', '驳回扣减申请', 'fund:deduction:reject', 105
    UNION ALL SELECT 'admin_fund_deduction_export_v1', '扣减申请导出', 'fund:deduction:export', 106
) item
WHERE app.app_code = 'ADMIN' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = app.id
        AND existing.menu_code = item.menu_code
        AND existing.deleted = 0
  );

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT app.id, menu.id, item.permission_code, item.permission_name, item.permission_type,
       item.resource_method, item.resource_path, item.description, 1, 0
FROM sys_app app
JOIN (
    SELECT 'admin_fund_deduction_v1' menu_code, 'fund:deduction:list' permission_code, '账户扣减查询' permission_name, 'MENU' permission_type, 'POST' resource_method, '/admin/fund-accounts/deductions/search' resource_path, '分页查询账户扣减申请' description
    UNION ALL SELECT 'admin_fund_deduction_detail_v1', 'fund:deduction:detail', '账户扣减详情', 'BUTTON', 'GET', '/admin/fund-accounts/deductions/*', '查询账户扣减完整审批快照'
    UNION ALL SELECT 'admin_fund_deduction_add_v1', 'fund:deduction:add', '提交扣减申请', 'BUTTON', 'POST', '/admin/fund-accounts/deductions', '提交待审核账户扣减申请'
    UNION ALL SELECT 'admin_fund_deduction_audit_v1', 'fund:deduction:audit', '审核扣减申请', 'BUTTON', 'POST', '/admin/fund-accounts/deductions/*/audit', '审核账户扣减申请'
    UNION ALL SELECT 'admin_fund_deduction_recheck_v1', 'fund:deduction:recheck', '复核扣减入账', 'BUTTON', 'POST', '/admin/fund-accounts/deductions/*/recheck', '复核通过并原子扣减可用余额'
    UNION ALL SELECT 'admin_fund_deduction_reject_v1', 'fund:deduction:reject', '驳回扣减申请', 'BUTTON', 'POST', '/admin/fund-accounts/deductions/*/reject', '驳回账户扣减申请'
    UNION ALL SELECT 'admin_fund_deduction_export_v1', 'fund:deduction:export', '扣减申请导出', 'BUTTON', 'POST', '/admin/fund-accounts/deductions/export', '按筛选条件导出全部账户扣减申请'
) item
JOIN sys_menu menu ON menu.app_id = app.id
                  AND menu.menu_code = item.menu_code
                  AND menu.deleted = 0
WHERE app.app_code = 'ADMIN' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing
      WHERE existing.app_id = app.id
        AND existing.permission_code = item.permission_code
        AND existing.deleted = 0
  );

INSERT INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT role.app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = role.app_id
                  AND menu.menu_code LIKE 'admin_fund_deduction_%'
                  AND menu.deleted = 0
WHERE role.role_code IN ('ADMIN_OPERATOR', 'SUPER_ADMIN') AND role.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu existing
      WHERE existing.app_id = role.app_id
        AND existing.role_id = role.id
        AND existing.menu_id = menu.id
        AND existing.deleted = 0
  );

INSERT INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT role.app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = role.app_id
                              AND permission.permission_code LIKE 'fund:deduction:%'
                              AND permission.deleted = 0
WHERE role.role_code IN ('ADMIN_OPERATOR', 'SUPER_ADMIN') AND role.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission existing
      WHERE existing.app_id = role.app_id
        AND existing.role_id = role.id
        AND existing.permission_id = permission.id
        AND existing.deleted = 0
  );

INSERT INTO sys_dict_type (
    dict_name, dict_type, biz_domain, system_builtin, editable, status, deleted
)
SELECT '账户扣减类型', 'fund_deduction_category', 'fund', 1, 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type existing
    WHERE existing.dict_type = 'fund_deduction_category' AND existing.deleted = 0
);

INSERT INTO sys_dict_data (
    dict_type, dict_label, dict_value, locale, dict_sort, list_class,
    is_default, status, deleted
)
SELECT item.dict_type, item.dict_label, item.dict_value, item.locale, item.dict_sort,
       item.list_class, item.is_default, 1, 0
FROM (
    SELECT 'fund_deduction_category' dict_type, '账务更正' dict_label, 'ACCOUNT_CORRECTION' dict_value, 'zh-CN' locale, 1 dict_sort, 'primary' list_class, 1 is_default
    UNION ALL SELECT 'fund_deduction_category', '额外费用', 'EXTRA_FEE', 'zh-CN', 2, 'warning', 0
    UNION ALL SELECT 'fund_deduction_category', '罚金', 'PENALTY', 'zh-CN', 3, 'danger', 0
    UNION ALL SELECT 'fund_deduction_category', '其他', 'OTHER', 'zh-CN', 4, 'info', 0
    UNION ALL SELECT 'fund_deduction_category', 'Account correction', 'ACCOUNT_CORRECTION', 'en-US', 1, 'primary', 1
    UNION ALL SELECT 'fund_deduction_category', 'Extra fee', 'EXTRA_FEE', 'en-US', 2, 'warning', 0
    UNION ALL SELECT 'fund_deduction_category', 'Penalty', 'PENALTY', 'en-US', 3, 'danger', 0
    UNION ALL SELECT 'fund_deduction_category', 'Other', 'OTHER', 'en-US', 4, 'info', 0
    UNION ALL SELECT 'fund_ledger_business_type', '账户扣减', 'BALANCE_DEDUCTION', 'zh-CN', 7, 'danger', 0
    UNION ALL SELECT 'fund_ledger_business_type', 'Balance deduction', 'BALANCE_DEDUCTION', 'en-US', 7, 'danger', 0
) item
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data existing
    WHERE existing.dict_type = item.dict_type
      AND existing.dict_value = item.dict_value
      AND existing.locale = item.locale
      AND existing.deleted = 0
);

COMMIT;
