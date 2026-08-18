-- 商户系统 Multi-Factor Authentication (MFA) 落地脚本
-- 适用：当前开发库。生产执行前请先确认 PAYMENT_MFA_SECRET / payment.mfa.secret 和管理系统通用邮件发件账户均已正确配置。
-- 邮件通知复用管理系统“邮件模板管理 / 发件账户配置”能力：
-- 1. 模板写入 msg_email_template，所属应用 MERCHANT，场景 MERCHANT_MFA；
-- 2. 商户服务发送时优先使用商户专属发件账户，其次使用管理系统 ADMIN/SYSTEM 默认发件账户；
-- 3. 邮件正文不包含 MFA 密钥、二维码或 MFA 验证码，用户仍需在登录页完成扫码/验证。

CREATE TABLE IF NOT EXISTS sys_account_mfa (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    account_id BIGINT NOT NULL COMMENT '登录账号ID',
    user_id BIGINT NOT NULL COMMENT '用户主体ID',
    merchant_id VARCHAR(32) NULL COMMENT '商户号，管理后台账号为空',
    mfa_policy VARCHAR(30) NOT NULL COMMENT 'MFA策略：OPTIONAL未强制，REQUIRED强制，EXEMPT豁免',
    mfa_status VARCHAR(30) NOT NULL COMMENT 'MFA状态：NOT_ENABLED、PENDING_BIND、ENABLED、RESET_REQUIRED、EXEMPT、LOCKED、DISABLED',
    mfa_type VARCHAR(30) NOT NULL DEFAULT 'TOTP' COMMENT 'MFA类型，本期固定TOTP',
    secret_cipher VARCHAR(512) NULL COMMENT '已绑定TOTP密钥密文',
    pending_secret_cipher VARCHAR(512) NULL COMMENT '待绑定TOTP密钥密文',
    issuer VARCHAR(100) NULL COMMENT '验证器发行方',
    account_label VARCHAR(150) NULL COMMENT '验证器账号标签',
    bind_time DATETIME(3) NULL COMMENT '完成绑定时间',
    last_verify_time DATETIME(3) NULL COMMENT '最近验证成功时间',
    last_success_time_step BIGINT NULL COMMENT '最近验证成功TOTP时间步，用于防重放',
    failed_verify_count INT NOT NULL DEFAULT 0 COMMENT '连续验证失败次数',
    locked_until DATETIME(3) NULL COMMENT '临时锁定截止时间',
    reset_time DATETIME(3) NULL COMMENT '最近重置时间',
    exempt_reason VARCHAR(500) NULL COMMENT '豁免原因',
    exempt_until DATETIME(3) NULL COMMENT '豁免截止时间，空表示长期豁免',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by BIGINT NULL COMMENT '修改人ID',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_account_mfa_account_deleted (app_id, account_id, deleted),
    KEY idx_sys_account_mfa_status (app_id, mfa_policy, mfa_status, deleted),
    KEY idx_sys_account_mfa_user (app_id, user_id, deleted),
    KEY idx_sys_account_mfa_merchant (merchant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统账号MFA配置表';

CREATE TABLE IF NOT EXISTS sys_account_mfa_token (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    account_id BIGINT NOT NULL COMMENT '登录账号ID',
    token_type VARCHAR(30) NOT NULL COMMENT '票据类型：LOGIN_MFA',
    token_hash VARCHAR(128) NOT NULL COMMENT '票据SHA-256哈希',
    challenge_type VARCHAR(40) NOT NULL COMMENT '挑战类型：BIND_REQUIRED、VERIFY_REQUIRED、RESET_BIND_REQUIRED',
    expire_at DATETIME(3) NOT NULL COMMENT '过期时间',
    used TINYINT NOT NULL DEFAULT 0 COMMENT '是否已使用：0否，1是',
    used_at DATETIME(3) NULL COMMENT '使用时间',
    client_ip VARCHAR(64) NULL COMMENT '客户端IP',
    user_agent VARCHAR(512) NULL COMMENT 'User-Agent',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_account_mfa_token_hash_deleted (token_hash, deleted),
    KEY idx_sys_account_mfa_token_account (app_id, account_id, token_type, used, expire_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统账号MFA短期票据表';

CREATE TABLE IF NOT EXISTS sys_account_mfa_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    account_id BIGINT NULL COMMENT '登录账号ID',
    user_id BIGINT NULL COMMENT '用户主体ID',
    merchant_id VARCHAR(32) NULL COMMENT '商户号，管理后台账号为空',
    action_type VARCHAR(50) NOT NULL COMMENT '操作类型',
    result VARCHAR(20) NOT NULL COMMENT '操作结果：SUCCESS、FAILED',
    reason VARCHAR(500) NULL COMMENT '操作原因或失败原因',
    before_policy VARCHAR(30) NULL COMMENT '变更前策略',
    before_status VARCHAR(30) NULL COMMENT '变更前状态',
    after_policy VARCHAR(30) NULL COMMENT '变更后策略',
    after_status VARCHAR(30) NULL COMMENT '变更后状态',
    operator_account_id BIGINT NULL COMMENT '操作人账号ID',
    operator_login_account VARCHAR(100) NULL COMMENT '操作人登录账号',
    client_ip VARCHAR(64) NULL COMMENT '客户端IP',
    user_agent VARCHAR(512) NULL COMMENT 'User-Agent',
    event_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sys_account_mfa_log_account_time (app_id, account_id, event_time),
    KEY idx_sys_account_mfa_log_action_time (app_id, action_type, result, event_time),
    KEY idx_sys_account_mfa_log_operator_time (operator_account_id, event_time),
    KEY idx_sys_account_mfa_log_merchant_time (merchant_id, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统账号MFA安全操作日志表';

-- 上线前已有 MERCHANT 用户默认不强制迁移；新增商户员工由后端默认 REQUIRED + PENDING_BIND。
INSERT INTO sys_account_mfa (
    app_id, account_id, user_id, merchant_id, mfa_policy, mfa_status, mfa_type,
    issuer, account_label, failed_verify_count, created_at, updated_at, deleted
)
SELECT account.app_id,
       account.id,
       account.user_id,
       account.merchant_id,
       'OPTIONAL',
       CASE WHEN account.status = 0 THEN 'DISABLED' ELSE 'NOT_ENABLED' END,
       'TOTP',
       'Acquiring Merchant',
       CONCAT(account.merchant_id, ':', account.login_account),
       0,
       CURRENT_TIMESTAMP(3),
       CURRENT_TIMESTAMP(3),
       0
FROM sys_account account
JOIN sys_app app ON app.id = account.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
WHERE account.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_account_mfa exists_mfa
      WHERE exists_mfa.app_id = account.app_id
        AND exists_mfa.account_id = account.id
        AND exists_mfa.deleted = 0
  );

INSERT INTO sys_permission (app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, description, status, deleted)
SELECT app.id,
       menu.id,
       item.permission_code,
       item.permission_name,
       'BUTTON',
       'POST',
       item.resource_path,
       item.description,
       1,
       0
FROM sys_app app
JOIN sys_menu menu ON menu.app_id = app.id AND menu.menu_code = 'merchant_system_account_v1' AND menu.deleted = 0
JOIN (
    SELECT 'merchant:system:account:mfa:require' permission_code, '强制启用员工 MFA' permission_name, '/merchant/system/accounts/*/mfa/require' resource_path, '将商户员工 MFA 策略调整为强制启用' description
    UNION ALL SELECT 'merchant:system:account:mfa:reset', '重置员工 MFA', '/merchant/system/accounts/*/mfa/reset', '废弃旧 MFA 密钥并要求员工重新绑定'
    UNION ALL SELECT 'merchant:system:account:mfa:exempt', '配置员工 MFA 豁免', '/merchant/system/accounts/*/mfa/exempt', '为商户员工配置 MFA 豁免'
    UNION ALL SELECT 'merchant:system:account:mfa:disable', '停用员工 MFA', '/merchant/system/accounts/*/mfa/disable', '将员工 MFA 恢复为未启用'
    UNION ALL SELECT 'merchant:system:account:mfa:unlock', '解锁员工 MFA', '/merchant/system/accounts/*/mfa/unlock', '解除员工 MFA 连续失败导致的临时锁定'
    UNION ALL SELECT 'merchant:system:account:mfa:resend', '重发 MFA 绑定邮件', '/merchant/system/accounts/*/mfa/resend-bind-mail', '重新发送商户员工 MFA 绑定引导邮件'
) item
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_permission exists_permission
      WHERE exists_permission.app_id = app.id
        AND exists_permission.permission_code = item.permission_code
        AND exists_permission.deleted = 0
  );

UPDATE sys_permission permission
JOIN sys_app app ON app.id = permission.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = app.id AND menu.menu_code = 'merchant_system_account_v1' AND menu.deleted = 0
JOIN (
    SELECT 'merchant:system:account:mfa:require' permission_code, '/merchant/system/accounts/*/mfa/require' resource_path
    UNION ALL SELECT 'merchant:system:account:mfa:reset', '/merchant/system/accounts/*/mfa/reset'
    UNION ALL SELECT 'merchant:system:account:mfa:exempt', '/merchant/system/accounts/*/mfa/exempt'
    UNION ALL SELECT 'merchant:system:account:mfa:disable', '/merchant/system/accounts/*/mfa/disable'
    UNION ALL SELECT 'merchant:system:account:mfa:unlock', '/merchant/system/accounts/*/mfa/unlock'
    UNION ALL SELECT 'merchant:system:account:mfa:resend', '/merchant/system/accounts/*/mfa/resend-bind-mail'
) item ON item.permission_code = permission.permission_code
SET permission.menu_id = menu.id,
    permission.permission_type = 'BUTTON',
    permission.resource_method = 'POST',
    permission.resource_path = item.resource_path,
    permission.updated_at = CURRENT_TIMESTAMP(3)
WHERE permission.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT role.app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = role.app_id AND permission.deleted = 0
WHERE role.deleted = 0
  AND (
      role.role_code = 'MERCHANT_ADMIN'
      OR role.role_code LIKE 'MERCHANT_ADMIN\_%'
  )
  AND permission.permission_code IN (
      'merchant:system:account:mfa:require',
      'merchant:system:account:mfa:reset',
      'merchant:system:account:mfa:exempt',
      'merchant:system:account:mfa:disable',
      'merchant:system:account:mfa:unlock',
      'merchant:system:account:mfa:resend'
  );

-- 商户系统权限会先取“角色权限”和“平台开放权限”的交集；新按钮必须同步开放给已有商户，否则前端不会展示 MFA 管理入口。
INSERT IGNORE INTO sys_merchant_permission_grant (
    merchant_id, app_id, permission_id, grant_source, status, created_at, updated_at, deleted
)
SELECT merchant.merchant_id,
       permission.app_id,
       permission.id,
       'SYSTEM',
       1,
       CURRENT_TIMESTAMP(3),
       CURRENT_TIMESTAMP(3),
       0
FROM base_merchant_info merchant
JOIN sys_app app ON app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = app.id AND permission.deleted = 0
WHERE merchant.deleted = 0
  AND permission.permission_code IN (
      'merchant:system:account:mfa:require',
      'merchant:system:account:mfa:reset',
      'merchant:system:account:mfa:exempt',
      'merchant:system:account:mfa:disable',
      'merchant:system:account:mfa:unlock',
      'merchant:system:account:mfa:resend'
  );

INSERT IGNORE INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, is_default, status, deleted)
VALUES
('email_scene_code', '商户 MFA 安全通知', 'MERCHANT_MFA', 'zh-CN', 8, 'danger', 0, 1, 0),
('email_scene_code', 'Merchant MFA Security', 'MERCHANT_MFA', 'en-US', 8, 'danger', 0, 1, 0);

INSERT INTO msg_email_template (
    template_code, template_name, app_code, scene_code, locale, subject_template, content_type,
    content_template, variable_schema, sensitive_variable_names, status, system_builtin, version_no,
    remark, create_by, update_by, deleted
)
SELECT item.template_code, item.template_name, 'MERCHANT', 'MERCHANT_MFA', 'zh-CN', item.subject_template, 'HTML',
       item.content_template, item.variable_schema, '[]', 1, 1, 1,
       item.remark, 'system', 'system', 0
FROM (
    SELECT 'MERCHANT_MFA_BIND_NOTICE' template_code,
           '商户系统 MFA 绑定通知' template_name,
           '【Vexra Merchant】请绑定多因素认证（MFA）' subject_template,
           '<div style="margin:0;padding:34px;background:#F3F7FF;font-family:Arial,''Microsoft YaHei'',sans-serif;color:#0F172A;"><div style="max-width:660px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:28px 32px;background:#2563EB;color:#FFFFFF;"><div style="font-size:12px;letter-spacing:0;text-transform:uppercase;color:#DBEAFE;">Vexra Merchant Security</div><div style="margin-top:8px;font-size:24px;font-weight:700;">多因素认证（MFA）绑定</div></div><div style="padding:30px 32px;line-height:1.8;font-size:14px;"><p style="margin:0 0 14px;">您好，${loginAccount}：</p><p style="margin:0 0 14px;">您的商户系统账号已开启多因素认证（MFA）。请打开商户系统登录页，完成商户号、账号、密码和图形验证码校验后，按页面提示扫码绑定。</p><div style="margin:22px 0;padding:18px 20px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:8px;"><p style="margin:0 0 8px;">商户：<strong>${merchantName}</strong>（${merchantId}）</p><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0 0 8px;">登录地址：<a href="${bindUrl}" style="color:#2563EB;text-decoration:none;">${bindUrl}</a></p><p style="margin:0;">操作原因：${reason}</p></div><p style="margin:0 0 10px;">请使用支持 TOTP 的验证器应用。邮件不会包含 MFA 密钥、二维码或 MFA 验证码。</p><p style="margin:0;color:#9a3412;">如非本人或授权商户管理员发起，请立即联系平台运营或商户管理员。</p></div><div style="padding:16px 32px;background:#F3F7FF;color:#64748B;font-size:12px;">Vexra Merchant 安全通知，请勿转发。</div></div></div>' content_template,
           '{"merchantName":"Blue Ocean Store","merchantId":"M10000001","loginAccount":"finance","bindUrl":"https://merchant.example.com/login","merchantSystemBaseUrl":"https://merchant.example.com","reason":"安全策略启用"}' variable_schema,
           '系统内置模板：商户系统 MFA 绑定通知' remark
    UNION ALL SELECT 'MERCHANT_MFA_ENABLED_NOTICE',
           '商户系统 MFA 启用通知',
           '【Vexra Merchant】多因素认证（MFA）已开启',
           '<div style="margin:0;padding:34px;background:#F3F7FF;font-family:Arial,''Microsoft YaHei'',sans-serif;color:#0F172A;"><div style="max-width:660px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:28px 32px;background:#2563EB;color:#FFFFFF;"><div style="font-size:12px;letter-spacing:0;text-transform:uppercase;color:#DBEAFE;">Vexra Merchant Security</div><div style="margin-top:8px;font-size:24px;font-weight:700;">多因素认证（MFA）已开启</div></div><div style="padding:30px 32px;line-height:1.8;font-size:14px;"><p style="margin:0 0 14px;">您好，${loginAccount}：</p><p style="margin:0 0 14px;">商户管理员已为您的账号开启多因素认证（MFA）。下次登录商户系统时，您需要先完成绑定再进入系统。</p><div style="margin:22px 0;padding:18px 20px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:8px;"><p style="margin:0 0 8px;">商户：<strong>${merchantName}</strong>（${merchantId}）</p><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0;">操作原因：${reason}</p></div><p style="margin:0;">请前往 <a href="${bindUrl}" style="color:#2563EB;text-decoration:none;">商户系统登录页</a> 完成绑定。</p></div><div style="padding:16px 32px;background:#F3F7FF;color:#64748B;font-size:12px;">Vexra Merchant 安全通知，请勿转发。</div></div></div>',
           '{"merchantName":"Blue Ocean Store","merchantId":"M10000001","loginAccount":"finance","bindUrl":"https://merchant.example.com/login","reason":"安全策略启用"}',
           '系统内置模板：商户系统 MFA 启用通知'
    UNION ALL SELECT 'MERCHANT_MFA_RESET_NOTICE',
           '商户系统 MFA 重置通知',
           '【Vexra Merchant】多因素认证（MFA）已重置',
           '<div style="margin:0;padding:34px;background:#F3F7FF;font-family:Arial,''Microsoft YaHei'',sans-serif;color:#0F172A;"><div style="max-width:660px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:28px 32px;background:#2563EB;color:#FFFFFF;"><div style="font-size:12px;letter-spacing:0;text-transform:uppercase;color:#DBEAFE;">Vexra Merchant Security</div><div style="margin-top:8px;font-size:24px;font-weight:700;">多因素认证（MFA）已重置</div></div><div style="padding:30px 32px;line-height:1.8;font-size:14px;"><p style="margin:0 0 14px;">您好，${loginAccount}：</p><p style="margin:0 0 14px;">您的原多因素认证（MFA）密钥已失效。下次登录商户系统时必须重新绑定新的验证器。</p><div style="margin:22px 0;padding:18px 20px;background:#FFF7ED;border:1px solid #DBEAFE;border-radius:8px;"><p style="margin:0 0 8px;">商户：<strong>${merchantName}</strong>（${merchantId}）</p><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0;">重置原因：${reason}</p></div><p style="margin:0;">请前往 <a href="${bindUrl}" style="color:#2563EB;text-decoration:none;">商户系统登录页</a> 重新绑定。</p></div><div style="padding:16px 32px;background:#F3F7FF;color:#64748B;font-size:12px;">Vexra Merchant 安全通知，请勿转发。</div></div></div>',
           '{"merchantName":"Blue Ocean Store","merchantId":"M10000001","loginAccount":"finance","bindUrl":"https://merchant.example.com/login","reason":"用户更换设备"}',
           '系统内置模板：商户系统 MFA 重置通知'
    UNION ALL SELECT 'MERCHANT_MFA_DISABLED_NOTICE',
           '商户系统 MFA 停用通知',
           '【Vexra Merchant】多因素认证（MFA）已停用',
           '<div style="margin:0;padding:34px;background:#F3F7FF;font-family:Arial,''Microsoft YaHei'',sans-serif;color:#0F172A;"><div style="max-width:660px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:28px 32px;background:#2563EB;color:#FFFFFF;"><div style="font-size:12px;letter-spacing:0;text-transform:uppercase;color:#DBEAFE;">Vexra Merchant Security</div><div style="margin-top:8px;font-size:24px;font-weight:700;">多因素认证（MFA）已停用</div></div><div style="padding:30px 32px;line-height:1.8;font-size:14px;"><p style="margin:0 0 14px;">您好，${loginAccount}：</p><p style="margin:0 0 14px;">商户管理员已停用您账号的多因素认证（MFA）要求。</p><div style="margin:22px 0;padding:18px 20px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:8px;"><p style="margin:0 0 8px;">商户：<strong>${merchantName}</strong>（${merchantId}）</p><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0;">操作原因：${reason}</p></div><p style="margin:0;color:#9a3412;">如果您不清楚该变更来源，请联系商户管理员确认。</p></div><div style="padding:16px 32px;background:#F3F7FF;color:#64748B;font-size:12px;">Vexra Merchant 安全通知，请勿转发。</div></div></div>',
           '{"merchantName":"Blue Ocean Store","merchantId":"M10000001","loginAccount":"finance","reason":"特殊账号调整"}',
           '系统内置模板：商户系统 MFA 停用通知'
    UNION ALL SELECT 'MERCHANT_MFA_EXEMPT_NOTICE',
           '商户系统 MFA 豁免通知',
           '【Vexra Merchant】多因素认证（MFA）豁免已配置',
           '<div style="margin:0;padding:34px;background:#F3F7FF;font-family:Arial,''Microsoft YaHei'',sans-serif;color:#0F172A;"><div style="max-width:660px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:28px 32px;background:#2563EB;color:#FFFFFF;"><div style="font-size:12px;letter-spacing:0;text-transform:uppercase;color:#DBEAFE;">Vexra Merchant Security</div><div style="margin-top:8px;font-size:24px;font-weight:700;">多因素认证（MFA）豁免</div></div><div style="padding:30px 32px;line-height:1.8;font-size:14px;"><p style="margin:0 0 14px;">您好，${loginAccount}：</p><p style="margin:0 0 14px;">商户管理员已为您的账号配置多因素认证（MFA）豁免。</p><div style="margin:22px 0;padding:18px 20px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:8px;"><p style="margin:0 0 8px;">商户：<strong>${merchantName}</strong>（${merchantId}）</p><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0 0 8px;">豁免有效期：${exemptUntil}</p><p style="margin:0;">豁免原因：${reason}</p></div><p style="margin:0;color:#9a3412;">豁免账号仍应使用强密码，并限制共享和转借。</p></div><div style="padding:16px 32px;background:#F3F7FF;color:#64748B;font-size:12px;">Vexra Merchant 安全通知，请勿转发。</div></div></div>',
           '{"merchantName":"Blue Ocean Store","merchantId":"M10000001","loginAccount":"finance","reason":"应急账号","exemptUntil":"长期有效"}',
           '系统内置模板：商户系统 MFA 豁免通知'
) item
WHERE NOT EXISTS (
    SELECT 1
    FROM msg_email_template exists_template
    WHERE exists_template.template_code = item.template_code
      AND exists_template.locale = 'zh-CN'
      AND exists_template.deleted = 0
);
