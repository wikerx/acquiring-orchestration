-- 管理系统账号激活与邮箱所有权验证迁移。
-- 回滚建议：先停用新入口并确认无待处理请求，再删除本脚本新增权限和模板；账号字段及审计请求表应保留归档，不建议直接删除。

SET NAMES utf8mb4;

-- 管理账号改为邮箱激活与首次设密后，停用并移除旧的明文初始密码模板。
DELETE FROM msg_email_template
WHERE template_code = 'ADMIN_ACCOUNT_CREATED'
  AND system_builtin = 1;

SET @activation_status_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_account' AND column_name = 'activation_status'
);
SET @activation_status_sql = IF(
    @activation_status_column_exists = 0,
    'ALTER TABLE sys_account ADD COLUMN activation_status VARCHAR(30) NOT NULL DEFAULT ''ACTIVE'' COMMENT ''账号激活状态：PENDING_ACTIVATION、ACTIVE'' AFTER email_verified_at',
    'SELECT 1'
);
PREPARE activation_status_statement FROM @activation_status_sql;
EXECUTE activation_status_statement;
DEALLOCATE PREPARE activation_status_statement;

SET @activated_at_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_account' AND column_name = 'activated_at'
);
SET @activated_at_sql = IF(
    @activated_at_column_exists = 0,
    'ALTER TABLE sys_account ADD COLUMN activated_at DATETIME(3) NULL COMMENT ''账号所有人完成邮箱验证并设置首次密码的时间'' AFTER activation_status',
    'SELECT 1'
);
PREPARE activated_at_statement FROM @activated_at_sql;
EXECUTE activated_at_statement;
DEALLOCATE PREPARE activated_at_statement;

-- 历史账号保持可登录；不批量伪造邮箱已验证时间。
UPDATE sys_account
SET activation_status = 'ACTIVE'
WHERE activation_status IS NULL OR activation_status = '';

CREATE TABLE IF NOT EXISTS sys_email_verification_request (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    request_no VARCHAR(64) NOT NULL COMMENT '高熵公开请求编号，不包含账号或邮箱信息',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    account_id BIGINT NOT NULL COMMENT '待验证账号ID',
    purpose VARCHAR(30) NOT NULL COMMENT '用途：ACCOUNT_ACTIVATION、EMAIL_VERIFICATION、EMAIL_CHANGE',
    email_fingerprint CHAR(64) NOT NULL COMMENT '规范化目标邮箱SHA-256指纹',
    code_hash VARCHAR(255) NOT NULL COMMENT '邮件验证码PBKDF2哈希',
    code_salt VARCHAR(100) NOT NULL COMMENT '邮件验证码随机盐',
    expire_at DATETIME(3) NOT NULL COMMENT '邮件验证码到期时间',
    verify_count INT NOT NULL DEFAULT 0 COMMENT '邮件验证码累计失败次数',
    verified_at DATETIME(3) NULL COMMENT '验证码验证成功时间',
    action_token_hash CHAR(64) NULL COMMENT '首次设密一次性动作令牌SHA-256哈希',
    action_token_expire_at DATETIME(3) NULL COMMENT '首次设密动作令牌到期时间',
    state VARCHAR(30) NOT NULL COMMENT 'PENDING、VERIFIED、COMPLETED、EXPIRED、LOCKED、SUPERSEDED、BLOCKED',
    request_ip VARCHAR(64) NOT NULL COMMENT '发起IP，用于限流和安全审计',
    user_agent VARCHAR(500) NULL COMMENT '截断后的User-Agent',
    locale VARCHAR(20) NOT NULL DEFAULT 'zh-CN' COMMENT '邮件语言',
    completed_at DATETIME(3) NULL COMMENT '验证或激活完成时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_email_verification_request_no (request_no),
    KEY idx_sys_email_verification_account_state (app_id, account_id, state, created_at),
    KEY idx_sys_email_verification_account_time (app_id, account_id, created_at),
    KEY idx_sys_email_verification_ip_time (app_id, request_ip, created_at),
    KEY idx_sys_email_verification_expire_state (state, expire_at, action_token_expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理账号激活与邮箱所有权验证请求表';

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, created_at, updated_at, deleted
)
SELECT app.id, menu.id, 'system:user:emailVerify', '用户邮箱验证邮件发送', 'BUTTON',
       'POST', '/admin/system/users/email-verification/send',
       '允许发送或重发账号激活及邮箱变更验证邮件', 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM sys_app app
JOIN sys_menu menu ON menu.app_id = app.id
    AND menu.menu_code IN ('admin_system_user_v3', 'system_user')
    AND menu.deleted = 0
WHERE app.app_code = 'ADMIN' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission permission
      WHERE permission.app_id = app.id
        AND permission.permission_code = 'system:user:emailVerify'
        AND permission.deleted = 0
  )
ORDER BY CASE WHEN menu.menu_code = 'admin_system_user_v3' THEN 0 ELSE 1 END
LIMIT 1;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, created_at, deleted)
SELECT permission.app_id, role.id, permission.id, CURRENT_TIMESTAMP(3), 0
FROM sys_permission permission
JOIN sys_role role ON role.app_id = permission.app_id
    AND role.role_code IN ('SUPER_ADMIN', 'ADMIN_OPERATOR', 'ADMIN')
    AND role.deleted = 0
WHERE permission.permission_code = 'system:user:emailVerify'
  AND permission.deleted = 0;

INSERT INTO sys_dict_data (
    dict_type, dict_label, dict_value, locale, dict_sort, list_class,
    is_default, status, created_by, updated_by, created_at, updated_at, deleted
) VALUES
    ('email_scene_code', '账号激活', 'ACCOUNT_ACTIVATION', 'zh-CN', 12, 'success', 0, 1, 'system', 'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    ('email_scene_code', '邮箱验证', 'EMAIL_VERIFICATION', 'zh-CN', 13, 'primary', 0, 1, 'system', 'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    ('email_scene_code', 'Account Activation', 'ACCOUNT_ACTIVATION', 'en-US', 12, 'success', 0, 1, 'system', 'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    ('email_scene_code', 'Email Verification', 'EMAIL_VERIFICATION', 'en-US', 13, 'primary', 0, 1, 'system', 'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
ON DUPLICATE KEY UPDATE
    dict_label = VALUES(dict_label),
    dict_sort = VALUES(dict_sort),
    list_class = VALUES(list_class),
    status = 1,
    updated_by = 'system',
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO msg_email_template (
    template_code, template_name, app_code, scene_code, locale, subject_template, content_type,
    content_template, variable_schema, sensitive_variable_names, status, system_builtin, version_no,
    remark, create_by, update_by, create_time, update_time, deleted
)
SELECT item.template_code, item.template_name, 'ADMIN', item.scene_code, item.locale,
       item.subject_template, 'HTML', item.content_template, item.variable_schema,
       item.sensitive_variable_names, 1, 1, 1, item.remark,
       'system', 'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM (
    SELECT 'ADMIN_ACCOUNT_ACTIVATION' template_code, '管理系统账号激活' template_name,
           'ACCOUNT_ACTIVATION' scene_code, 'zh-CN' locale,
           '【${systemName}】请完成账号激活' subject_template,
           '<div data-template-theme="vexra-blue-white-v1" style="margin:0;padding:32px 16px;background:#F3F7FF;font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;color:#0F172A;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#2563EB;color:#FFFFFF;"><div style="font-size:13px;color:#DBEAFE;">Vexra Admin</div><div style="margin-top:6px;font-size:22px;font-weight:700;">激活您的管理账号</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:700;">您好，${userName}</p><p style="margin:0 0 18px;color:#64748B;">管理员已为您创建 ${systemName} 账号 <strong>${loginAccount}</strong>。请验证邮箱并设置首次登录密码。</p><div style="margin:20px 0;padding:22px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:8px;text-align:center;"><div style="font-size:12px;color:#64748B;">一次性验证码</div><div style="margin-top:8px;font-size:32px;letter-spacing:4px;font-weight:700;color:#0F172A;">${verificationCode}</div><div style="margin-top:8px;font-size:12px;color:#64748B;">${expireMinutes} 分钟内有效</div></div><div style="text-align:center;"><a href="${verificationUrl}" style="display:inline-block;padding:11px 22px;border-radius:6px;background:#2563EB;color:#FFFFFF;text-decoration:none;font-weight:700;">验证邮箱并设置密码</a></div><div style="margin-top:18px;padding:14px 16px;background:#F8FAFC;border:1px solid #E2E8F0;border-radius:6px;color:#475569;">创建时间：${requestTime}</div><div style="margin-top:18px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #F59E0B;color:#9A3412;">${securityNotice}</div></div><div style="padding:16px 28px;background:#F3F7FF;border-top:1px solid #DBEAFE;color:#64748B;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿回复、转发或向任何人透露验证码。</div></div></div>' content_template,
           '{"systemName":"Vexra Admin","userName":"张三","loginAccount":"zhangsan","verificationCode":"aB3xY7","expireMinutes":30,"verificationUrl":"https://admin.example.com/account-verification?requestId=example","requestTime":"2026-10-15 10:00:00","securityNotice":"如非本人或授权管理员发起，请忽略本邮件并联系其他管理员。"}' variable_schema,
           '["verificationCode"]' sensitive_variable_names,
           '系统内置模板：管理账号邮箱验证与首次设密' remark
    UNION ALL SELECT 'ADMIN_ACCOUNT_ACTIVATION', 'Admin account activation',
           'ACCOUNT_ACTIVATION', 'en-US',
           '[${systemName}] Activate your account',
           '<div data-template-theme="vexra-blue-white-v1" style="margin:0;padding:32px 16px;background:#F3F7FF;font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;color:#0F172A;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#2563EB;color:#FFFFFF;"><div style="font-size:13px;color:#DBEAFE;">Vexra Admin</div><div style="margin-top:6px;font-size:22px;font-weight:700;">Activate your admin account</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:700;">Hello ${userName},</p><p style="margin:0 0 18px;color:#64748B;">An administrator created the ${systemName} account <strong>${loginAccount}</strong> for you. Verify your email and set your first password.</p><div style="margin:20px 0;padding:22px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:8px;text-align:center;"><div style="font-size:12px;color:#64748B;">ONE-TIME CODE</div><div style="margin-top:8px;font-size:32px;letter-spacing:4px;font-weight:700;color:#0F172A;">${verificationCode}</div><div style="margin-top:8px;font-size:12px;color:#64748B;">Valid for ${expireMinutes} minutes</div></div><div style="text-align:center;"><a href="${verificationUrl}" style="display:inline-block;padding:11px 22px;border-radius:6px;background:#2563EB;color:#FFFFFF;text-decoration:none;font-weight:700;">Verify email and set password</a></div><div style="margin-top:18px;padding:14px 16px;background:#F8FAFC;border:1px solid #E2E8F0;border-radius:6px;color:#475569;">Created at: ${requestTime}</div><div style="margin-top:18px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #F59E0B;color:#9A3412;">${securityNotice}</div></div><div style="padding:16px 28px;background:#F3F7FF;border-top:1px solid #DBEAFE;color:#64748B;font-size:12px;">This message was sent automatically by ${systemName}. Never reply, forward, or share the code.</div></div></div>',
           '{"systemName":"Vexra Admin","userName":"Alex","loginAccount":"alex","verificationCode":"aB3xY7","expireMinutes":30,"verificationUrl":"https://admin.example.com/account-verification?requestId=example","requestTime":"2026-10-15 10:00:00","securityNotice":"If you did not request this change, ignore this email and contact another administrator."}',
           '["verificationCode"]', 'Built-in template: admin account activation and first password setup'
    UNION ALL SELECT 'ADMIN_EMAIL_VERIFICATION', '管理系统邮箱验证',
           'EMAIL_VERIFICATION', 'zh-CN',
           '【${systemName}】请验证新的登录邮箱',
           '<div data-template-theme="vexra-blue-white-v1" style="margin:0;padding:32px 16px;background:#F3F7FF;font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;color:#0F172A;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#2563EB;color:#FFFFFF;"><div style="font-size:13px;color:#DBEAFE;">Vexra Admin</div><div style="margin-top:6px;font-size:22px;font-weight:700;">验证登录邮箱</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:700;">您好，${userName}</p><p style="margin:0 0 18px;color:#64748B;">请验证账号 <strong>${loginAccount}</strong> 当前绑定的登录邮箱。</p><div style="margin:20px 0;padding:22px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:8px;text-align:center;"><div style="font-size:12px;color:#64748B;">一次性验证码</div><div style="margin-top:8px;font-size:32px;letter-spacing:4px;font-weight:700;color:#0F172A;">${verificationCode}</div><div style="margin-top:8px;font-size:12px;color:#64748B;">${expireMinutes} 分钟内有效</div></div><div style="text-align:center;"><a href="${verificationUrl}" style="display:inline-block;padding:11px 22px;border-radius:6px;background:#2563EB;color:#FFFFFF;text-decoration:none;font-weight:700;">验证邮箱</a></div><div style="margin-top:18px;padding:14px 16px;background:#F8FAFC;border:1px solid #E2E8F0;border-radius:6px;color:#475569;">申请时间：${requestTime}</div><div style="margin-top:18px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #F59E0B;color:#9A3412;">${securityNotice}</div></div><div style="padding:16px 28px;background:#F3F7FF;border-top:1px solid #DBEAFE;color:#64748B;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿回复、转发或向任何人透露验证码。</div></div></div>',
           '{"systemName":"Vexra Admin","userName":"张三","loginAccount":"zhangsan","verificationCode":"aB3xY7","expireMinutes":30,"verificationUrl":"https://admin.example.com/account-verification?requestId=example","requestTime":"2026-10-15 10:00:00","securityNotice":"如非本人或授权管理员发起，请忽略本邮件并联系其他管理员。"}',
           '["verificationCode"]', '系统内置模板：管理账号邮箱所有权验证'
    UNION ALL SELECT 'ADMIN_EMAIL_VERIFICATION', 'Admin email verification',
           'EMAIL_VERIFICATION', 'en-US',
           '[${systemName}] Verify your sign-in email',
           '<div data-template-theme="vexra-blue-white-v1" style="margin:0;padding:32px 16px;background:#F3F7FF;font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;color:#0F172A;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#2563EB;color:#FFFFFF;"><div style="font-size:13px;color:#DBEAFE;">Vexra Admin</div><div style="margin-top:6px;font-size:22px;font-weight:700;">Verify your sign-in email</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:700;">Hello ${userName},</p><p style="margin:0 0 18px;color:#64748B;">Verify the email address currently bound to account <strong>${loginAccount}</strong>.</p><div style="margin:20px 0;padding:22px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:8px;text-align:center;"><div style="font-size:12px;color:#64748B;">ONE-TIME CODE</div><div style="margin-top:8px;font-size:32px;letter-spacing:4px;font-weight:700;color:#0F172A;">${verificationCode}</div><div style="margin-top:8px;font-size:12px;color:#64748B;">Valid for ${expireMinutes} minutes</div></div><div style="text-align:center;"><a href="${verificationUrl}" style="display:inline-block;padding:11px 22px;border-radius:6px;background:#2563EB;color:#FFFFFF;text-decoration:none;font-weight:700;">Verify email</a></div><div style="margin-top:18px;padding:14px 16px;background:#F8FAFC;border:1px solid #E2E8F0;border-radius:6px;color:#475569;">Requested at: ${requestTime}</div><div style="margin-top:18px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #F59E0B;color:#9A3412;">${securityNotice}</div></div><div style="padding:16px 28px;background:#F3F7FF;border-top:1px solid #DBEAFE;color:#64748B;font-size:12px;">This message was sent automatically by ${systemName}. Never reply, forward, or share the code.</div></div></div>',
           '{"systemName":"Vexra Admin","userName":"Alex","loginAccount":"alex","verificationCode":"aB3xY7","expireMinutes":30,"verificationUrl":"https://admin.example.com/account-verification?requestId=example","requestTime":"2026-10-15 10:00:00","securityNotice":"If you did not request this change, ignore this email and contact another administrator."}',
           '["verificationCode"]', 'Built-in template: admin sign-in email ownership verification'
) item
ON DUPLICATE KEY UPDATE
    template_name = VALUES(template_name),
    app_code = VALUES(app_code),
    scene_code = VALUES(scene_code),
    subject_template = VALUES(subject_template),
    content_type = VALUES(content_type),
    content_template = VALUES(content_template),
    variable_schema = VALUES(variable_schema),
    sensitive_variable_names = VALUES(sensitive_variable_names),
    status = 1,
    system_builtin = 1,
    version_no = GREATEST(version_no, 1),
    remark = VALUES(remark),
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP(3),
    deleted = 0;
