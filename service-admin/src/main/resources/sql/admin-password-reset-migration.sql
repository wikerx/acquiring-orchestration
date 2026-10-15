-- 管理后台安全找回密码迁移。
-- 生产执行前必须确认现有账号邮箱可信来源；本脚本不会批量把历史邮箱标记为已验证。

SET NAMES utf8mb4;

SET @email_verified_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_account'
      AND column_name = 'email_verified_at'
);
SET @email_verified_sql = IF(
    @email_verified_column_exists = 0,
    'ALTER TABLE sys_account ADD COLUMN email_verified_at DATETIME(3) NULL COMMENT ''登录邮箱可信确认时间；为空时不得用于匿名找回密码'' AFTER email',
    'SELECT 1'
);
PREPARE email_verified_statement FROM @email_verified_sql;
EXECUTE email_verified_statement;
DEALLOCATE PREPARE email_verified_statement;

-- 修复早期迁移因客户端字符集错误写入的字段注释；仅在注释不一致时执行 DDL。
SET @email_verified_comment = (
    SELECT column_comment
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_account'
      AND column_name = 'email_verified_at'
);
SET @email_verified_comment_sql = IF(
    @email_verified_comment <> '登录邮箱可信确认时间；为空时不得用于匿名找回密码',
    'ALTER TABLE sys_account MODIFY COLUMN email_verified_at DATETIME(3) NULL COMMENT ''登录邮箱可信确认时间；为空时不得用于匿名找回密码''',
    'SELECT 1'
);
PREPARE email_verified_comment_statement FROM @email_verified_comment_sql;
EXECUTE email_verified_comment_statement;
DEALLOCATE PREPARE email_verified_comment_statement;

CREATE TABLE IF NOT EXISTS sys_password_reset_request (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    request_no VARCHAR(64) NOT NULL COMMENT '高熵公开请求编号，不包含账号信息',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    account_id BIGINT NULL COMMENT '合格账号ID；虚拟请求为空',
    account_fingerprint CHAR(64) NOT NULL COMMENT '规范化登录账号SHA-256指纹，仅用于限流',
    email_code_hash VARCHAR(255) NOT NULL COMMENT '邮件验证码PBKDF2哈希',
    email_code_salt VARCHAR(100) NOT NULL COMMENT '邮件验证码随机盐',
    email_expire_at DATETIME(3) NOT NULL COMMENT '邮件验证码到期时间',
    email_verify_count INT NOT NULL DEFAULT 0 COMMENT '邮件验证码累计失败次数',
    email_verified_at DATETIME(3) NULL COMMENT '邮件验证码验证成功时间',
    mfa_verified_at DATETIME(3) NULL COMMENT 'MFA验证成功时间',
    stage_token_hash CHAR(64) NULL COMMENT '邮箱验证后阶段令牌SHA-256哈希',
    stage_token_expire_at DATETIME(3) NULL COMMENT '阶段令牌到期时间',
    reset_token_hash CHAR(64) NULL COMMENT '最终重置令牌SHA-256哈希',
    reset_token_expire_at DATETIME(3) NULL COMMENT '最终重置令牌到期时间',
    state VARCHAR(30) NOT NULL COMMENT 'PENDING_EMAIL、PENDING_MFA、READY、COMPLETED、EXPIRED、LOCKED、BLOCKED',
    request_ip VARCHAR(64) NOT NULL COMMENT '请求IP，用于限流和安全审计',
    user_agent VARCHAR(500) NULL COMMENT '截断后的User-Agent',
    locale VARCHAR(20) NOT NULL DEFAULT 'zh-CN' COMMENT '邮件语言',
    completed_at DATETIME(3) NULL COMMENT '密码重置完成时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_password_reset_request_no (request_no),
    KEY idx_sys_password_reset_account_time (app_id, account_fingerprint, created_at),
    KEY idx_sys_password_reset_ip_time (app_id, request_ip, created_at),
    KEY idx_sys_password_reset_account_state (app_id, account_id, state, created_at),
    KEY idx_sys_password_reset_expire_state (state, email_expire_at, reset_token_expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理后台找回密码安全请求表';

-- ===================== 管理后台安全找回密码邮件 =====================
INSERT INTO msg_email_template (
    template_code, template_name, app_code, scene_code, locale, subject_template, content_type,
    content_template, variable_schema, sensitive_variable_names, status, system_builtin, version_no,
    remark, create_by, update_by, deleted
)
SELECT item.template_code, item.template_name, 'ADMIN', item.scene_code, item.locale,
       item.subject_template, 'HTML', item.content_template, item.variable_schema,
       item.sensitive_variable_names, 1, 1, 1, item.remark, 'system', 'system', 0
FROM (
    SELECT 'ADMIN_PASSWORD_RESET_VERIFY' template_code, '管理系统找回密码验证码' template_name,
           'PASSWORD_RESET_VERIFY' scene_code, 'zh-CN' locale,
           '【${systemName}】找回密码验证码' subject_template,
           '<div data-template-theme="vexra-blue-white-v1" style="margin:0;padding:32px 16px;background:#F3F7FF;font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;color:#0F172A;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#2563EB;color:#FFFFFF;"><div style="font-size:13px;color:#DBEAFE;">Vexra Admin</div><div style="margin-top:6px;font-size:22px;font-weight:700;">找回管理系统密码</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:700;">您好，${userName}</p><p style="margin:0 0 18px;color:#64748B;">请使用以下验证码继续 ${systemName} 的身份验证。</p><div style="margin:20px 0;padding:22px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:8px;text-align:center;"><div style="font-size:12px;color:#64748B;">一次性验证码</div><div style="margin-top:8px;font-size:32px;letter-spacing:4px;font-weight:700;color:#0F172A;">${verifyCode}</div><div style="margin-top:8px;font-size:12px;color:#64748B;">${expireMinutes} 分钟内有效</div></div><div style="padding:14px 16px;background:#F8FAFC;border:1px solid #E2E8F0;border-radius:6px;color:#475569;">申请时间：${requestTime}</div><div style="margin-top:18px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #F59E0B;color:#9A3412;">${securityNotice}</div></div><div style="padding:16px 28px;background:#F3F7FF;border-top:1px solid #DBEAFE;color:#64748B;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿回复、转发或向任何人透露验证码。</div></div></div>' content_template,
           '{"systemName":"Vexra Admin","userName":"admin","verifyCode":"aB3xY7","expireMinutes":30,"verificationUrl":"https://admin.example.com/forgot-password?requestId=example","requestTime":"2026-10-14 10:00:00","securityNotice":"如非本人操作，请忽略本邮件并联系其他管理员。"}' variable_schema,
           '["verifyCode"]' sensitive_variable_names, '系统内置模板：管理后台找回密码邮箱验证码' remark
    UNION ALL SELECT 'ADMIN_PASSWORD_RESET_VERIFY', 'Admin password recovery code',
           'PASSWORD_RESET_VERIFY', 'en-US',
           '[${systemName}] Password recovery code',
           '<div data-template-theme="vexra-blue-white-v1" style="margin:0;padding:32px 16px;background:#F3F7FF;font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;color:#0F172A;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#2563EB;color:#FFFFFF;"><div style="font-size:13px;color:#DBEAFE;">Vexra Admin</div><div style="margin-top:6px;font-size:22px;font-weight:700;">Recover your admin password</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:700;">Hello ${userName},</p><p style="margin:0 0 18px;color:#64748B;">Use the code below to continue identity verification for ${systemName}.</p><div style="margin:20px 0;padding:22px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:8px;text-align:center;"><div style="font-size:12px;color:#64748B;">ONE-TIME CODE</div><div style="margin-top:8px;font-size:32px;letter-spacing:4px;font-weight:700;color:#0F172A;">${verifyCode}</div><div style="margin-top:8px;font-size:12px;color:#64748B;">Valid for ${expireMinutes} minutes</div></div><div style="padding:14px 16px;background:#F8FAFC;border:1px solid #E2E8F0;border-radius:6px;color:#475569;">Requested at: ${requestTime}</div><div style="margin-top:18px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #F59E0B;color:#9A3412;">${securityNotice}</div></div><div style="padding:16px 28px;background:#F3F7FF;border-top:1px solid #DBEAFE;color:#64748B;font-size:12px;">This message was sent automatically by ${systemName}. Never share this code.</div></div></div>',
           '{"systemName":"Vexra Admin","userName":"admin","verifyCode":"aB3xY7","expireMinutes":30,"verificationUrl":"https://admin.example.com/forgot-password?requestId=example","requestTime":"2026-10-14 10:00:00","securityNotice":"If you did not request this, ignore this email and contact another administrator."}',
           '["verifyCode"]', 'Built-in template: admin password recovery verification'
    UNION ALL SELECT 'ADMIN_PASSWORD_RESET_SUCCESS', '管理系统密码重置成功通知',
           'PASSWORD_RESET_SUCCESS', 'zh-CN',
           '【${systemName}】密码已重置',
           '<div data-template-theme="vexra-blue-white-v1" style="margin:0;padding:32px 16px;background:#F3F7FF;font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;color:#0F172A;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#2563EB;color:#FFFFFF;"><div style="font-size:13px;color:#DBEAFE;">Vexra Admin</div><div style="margin-top:6px;font-size:22px;font-weight:700;">密码重置成功</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:700;">您好，${userName}</p><p style="margin:0 0 18px;color:#64748B;">您的 ${systemName} 登录密码已更新。</p><div style="padding:18px;background:#ECFDF5;border:1px solid #A7F3D0;border-radius:8px;color:#065F46;"><div style="font-weight:700;">安全变更已完成</div><div style="margin-top:6px;">操作时间：${operationTime}</div></div><div style="margin-top:18px;padding:12px 14px;background:#FEF2F2;border-left:4px solid #DC2626;color:#991B1B;">${securityNotice}</div></div><div style="padding:16px 28px;background:#F3F7FF;border-top:1px solid #DBEAFE;color:#64748B;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿直接回复。</div></div></div>',
           '{"systemName":"Vexra Admin","userName":"admin","operationTime":"2026-10-14 10:05:00","securityNotice":"所有旧会话均已退出。如非本人操作，请立即联系其他管理员。"}',
           '[]', '系统内置模板：管理后台密码重置成功通知'
    UNION ALL SELECT 'ADMIN_PASSWORD_RESET_SUCCESS', 'Admin password reset confirmation',
           'PASSWORD_RESET_SUCCESS', 'en-US',
           '[${systemName}] Password reset completed',
           '<div data-template-theme="vexra-blue-white-v1" style="margin:0;padding:32px 16px;background:#F3F7FF;font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;color:#0F172A;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#2563EB;color:#FFFFFF;"><div style="font-size:13px;color:#DBEAFE;">Vexra Admin</div><div style="margin-top:6px;font-size:22px;font-weight:700;">Password reset completed</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:700;">Hello ${userName},</p><p style="margin:0 0 18px;color:#64748B;">Your ${systemName} sign-in password has been updated.</p><div style="padding:18px;background:#ECFDF5;border:1px solid #A7F3D0;border-radius:8px;color:#065F46;"><div style="font-weight:700;">Security change completed</div><div style="margin-top:6px;">Completed at: ${operationTime}</div></div><div style="margin-top:18px;padding:12px 14px;background:#FEF2F2;border-left:4px solid #DC2626;color:#991B1B;">${securityNotice}</div></div><div style="padding:16px 28px;background:#F3F7FF;border-top:1px solid #DBEAFE;color:#64748B;font-size:12px;">This message was sent automatically by ${systemName}. Please do not reply.</div></div></div>',
           '{"systemName":"Vexra Admin","userName":"admin","operationTime":"2026-10-14 10:05:00","securityNotice":"All previous sessions were signed out. Contact another administrator immediately if this was not you."}',
           '[]', 'Built-in template: admin password reset confirmation'
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
    deleted = 0;

UPDATE msg_email_template
SET content_template = REPLACE(
        content_template,
        '</div><div style="padding:14px 16px;background:#F8FAFC',
        CONCAT('</div><div style="margin:18px 0;text-align:center;"><a href="${verificationUrl}" style="display:inline-block;padding:11px 22px;border-radius:6px;background:#2563EB;color:#FFFFFF;text-decoration:none;font-weight:700;">',
               IF(locale = 'en-US', 'Continue secure password reset', '继续安全重置密码'),
               '</a></div><div style="padding:14px 16px;background:#F8FAFC')
    )
WHERE template_code = 'ADMIN_PASSWORD_RESET_VERIFY'
  AND deleted = 0
  AND content_template NOT LIKE '%${verificationUrl}%';
