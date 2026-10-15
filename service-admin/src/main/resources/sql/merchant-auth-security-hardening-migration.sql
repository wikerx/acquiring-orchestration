-- 商户系统账号激活、邮箱验证与安全找回密码迁移。
-- 回滚建议：先回退依赖 merchant_id 的应用版本；请求表与邮件模板属于安全审计数据，建议保留。

SET NAMES utf8mb4;

SET @password_reset_merchant_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_password_reset_request'
      AND column_name = 'merchant_id'
);
SET @password_reset_merchant_column_sql = IF(
    @password_reset_merchant_column_exists = 0,
    'ALTER TABLE sys_password_reset_request ADD COLUMN merchant_id VARCHAR(32) NULL COMMENT ''商户号；管理系统请求可为空'' AFTER app_id',
    'SELECT 1'
);
PREPARE password_reset_merchant_column_statement FROM @password_reset_merchant_column_sql;
EXECUTE password_reset_merchant_column_statement;
DEALLOCATE PREPARE password_reset_merchant_column_statement;

SET @email_verification_merchant_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_email_verification_request'
      AND column_name = 'merchant_id'
);
SET @email_verification_merchant_column_sql = IF(
    @email_verification_merchant_column_exists = 0,
    'ALTER TABLE sys_email_verification_request ADD COLUMN merchant_id VARCHAR(32) NULL COMMENT ''商户号；管理系统请求可为空'' AFTER app_id',
    'SELECT 1'
);
PREPARE email_verification_merchant_column_statement FROM @email_verification_merchant_column_sql;
EXECUTE email_verification_merchant_column_statement;
DEALLOCATE PREPARE email_verification_merchant_column_statement;

SET @password_reset_merchant_fingerprint_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_password_reset_request'
      AND index_name = 'idx_sys_password_reset_merchant_fingerprint'
);
SET @password_reset_merchant_fingerprint_index_sql = IF(
    @password_reset_merchant_fingerprint_index_exists = 0,
    'ALTER TABLE sys_password_reset_request ADD INDEX idx_sys_password_reset_merchant_fingerprint (app_id, merchant_id, account_fingerprint, created_at)',
    'SELECT 1'
);
PREPARE password_reset_merchant_fingerprint_index_statement FROM @password_reset_merchant_fingerprint_index_sql;
EXECUTE password_reset_merchant_fingerprint_index_statement;
DEALLOCATE PREPARE password_reset_merchant_fingerprint_index_statement;

SET @password_reset_merchant_account_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_password_reset_request'
      AND index_name = 'idx_sys_password_reset_merchant_account'
);
SET @password_reset_merchant_account_index_sql = IF(
    @password_reset_merchant_account_index_exists = 0,
    'ALTER TABLE sys_password_reset_request ADD INDEX idx_sys_password_reset_merchant_account (app_id, merchant_id, account_id, state, created_at)',
    'SELECT 1'
);
PREPARE password_reset_merchant_account_index_statement FROM @password_reset_merchant_account_index_sql;
EXECUTE password_reset_merchant_account_index_statement;
DEALLOCATE PREPARE password_reset_merchant_account_index_statement;

SET @email_verification_merchant_account_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_email_verification_request'
      AND index_name = 'idx_sys_email_verification_merchant_account'
);
SET @email_verification_merchant_account_index_sql = IF(
    @email_verification_merchant_account_index_exists = 0,
    'ALTER TABLE sys_email_verification_request ADD INDEX idx_sys_email_verification_merchant_account (app_id, merchant_id, account_id, state, created_at)',
    'SELECT 1'
);
PREPARE email_verification_merchant_account_index_statement FROM @email_verification_merchant_account_index_sql;
EXECUTE email_verification_merchant_account_index_statement;
DEALLOCATE PREPARE email_verification_merchant_account_index_statement;

ALTER TABLE sys_password_reset_request COMMENT = '系统账号找回密码安全请求表';
ALTER TABLE sys_email_verification_request COMMENT = '系统账号激活与邮箱所有权验证请求表';

-- 邮箱验证重发使用独立权限，避免拥有普通编辑权限的角色自动获得安全邮件操作能力。
INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, status, description, created_at, updated_at, deleted
)
SELECT app.id, menu.id, 'merchant:system:account:emailVerify', '员工邮箱验证邮件发送', 'BUTTON',
       'POST', '/merchant/system/accounts/*/email-verification/send', 1,
       '重发员工账号激活或待验证邮箱确认邮件', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM sys_app app
JOIN sys_menu menu ON menu.app_id = app.id
    AND menu.menu_code = 'merchant_system_account_v1'
    AND menu.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission permission
      WHERE permission.app_id = app.id
        AND permission.permission_code = 'merchant:system:account:emailVerify'
        AND permission.deleted = 0
  );

-- 已拥有员工管理权限的商户管理员角色同步获得该操作；后续仍可在角色授权页单独收回。
INSERT INTO sys_role_permission (app_id, role_id, permission_id, created_at, deleted)
SELECT permission.app_id, role.id, permission.id, CURRENT_TIMESTAMP(3), 0
FROM sys_permission permission
JOIN sys_role role ON role.app_id = permission.app_id
    AND role.role_code = 'MERCHANT_ADMIN'
    AND role.deleted = 0
WHERE permission.permission_code = 'merchant:system:account:emailVerify'
  AND permission.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission relation
      WHERE relation.app_id = permission.app_id
        AND relation.role_id = role.id
        AND relation.permission_id = permission.id
        AND relation.deleted = 0
  );

INSERT INTO sys_dict_data (
    dict_type, dict_label, dict_value, locale, dict_sort, list_class,
    is_default, status, created_by, updated_by, created_at, updated_at, deleted
) VALUES
    ('email_scene_code', '账号激活', 'ACCOUNT_ACTIVATION', 'zh-CN', 12, 'success', 0, 1, 'system', 'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    ('email_scene_code', '邮箱验证', 'EMAIL_VERIFICATION', 'zh-CN', 13, 'primary', 0, 1, 'system', 'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    ('email_scene_code', '密码重置验证', 'PASSWORD_RESET_VERIFY', 'zh-CN', 14, 'warning', 0, 1, 'system', 'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    ('email_scene_code', '密码重置成功', 'PASSWORD_RESET_SUCCESS', 'zh-CN', 15, 'success', 0, 1, 'system', 'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    ('email_scene_code', 'Account Activation', 'ACCOUNT_ACTIVATION', 'en-US', 12, 'success', 0, 1, 'system', 'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    ('email_scene_code', 'Email Verification', 'EMAIL_VERIFICATION', 'en-US', 13, 'primary', 0, 1, 'system', 'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    ('email_scene_code', 'Password Reset Verification', 'PASSWORD_RESET_VERIFY', 'en-US', 14, 'warning', 0, 1, 'system', 'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    ('email_scene_code', 'Password Reset Completed', 'PASSWORD_RESET_SUCCESS', 'en-US', 15, 'success', 0, 1, 'system', 'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
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
SELECT item.template_code, item.template_name, 'MERCHANT', item.scene_code, item.locale,
       item.subject_template, 'HTML', item.content_template, item.variable_schema,
       item.sensitive_variable_names, 1, 1, 1, item.remark,
       'system', 'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM (
    SELECT 'MERCHANT_ACCOUNT_ACTIVATION' template_code, '商户账号激活' template_name,
           'ACCOUNT_ACTIVATION' scene_code, 'zh-CN' locale,
           '【${systemName}】请完成账号激活' subject_template,
           '<div data-template-theme="vexra-merchant-blue-green-v1" style="margin:0;padding:32px 16px;background:#EFF6F6;font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;color:#123047;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #CFE3E2;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#0B4F6C;color:#FFFFFF;"><div style="font-size:13px;color:#BFE3E1;">Vexra Merchant · ${merchantId}</div><div style="margin-top:6px;font-size:22px;font-weight:700;">激活您的商户账号</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:700;">您好，${userName}</p><p style="margin:0 0 18px;color:#526A79;">账号 <strong>${loginAccount}</strong> 已创建。请验证邮箱并设置首次登录密码。</p><div style="margin:20px 0;padding:22px;background:#F0F7F6;border:1px solid #B9D9D5;border-radius:8px;text-align:center;"><div style="font-size:12px;color:#526A79;">一次性验证码</div><div style="margin-top:8px;font-size:32px;letter-spacing:4px;font-weight:700;color:#123047;">${verificationCode}</div><div style="margin-top:8px;font-size:12px;color:#526A79;">${expireMinutes} 分钟内有效</div></div><div style="text-align:center;"><a href="${verificationUrl}" style="display:inline-block;padding:11px 22px;border-radius:6px;background:#0F766E;color:#FFFFFF;text-decoration:none;font-weight:700;">验证邮箱并设置密码</a></div><div style="margin-top:18px;padding:14px 16px;background:#F7FAFC;border:1px solid #D8E2E8;border-radius:6px;color:#526A79;">创建时间：${requestTime}</div><div style="margin-top:18px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #D97706;color:#92400E;">${securityNotice}</div></div><div style="padding:16px 28px;background:#EFF6F6;border-top:1px solid #CFE3E2;color:#526A79;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿回复、转发或向任何人透露验证码。</div></div></div>' content_template,
           '{"systemName":"Vexra Merchant","userName":"张三","loginAccount":"operator","merchantId":"M10000001","verificationCode":"aB3xY7","expireMinutes":30,"verificationUrl":"https://merchant.example.com/account-verification?requestId=example","requestTime":"2026-10-15 10:00:00","securityNotice":"如非本人或商户管理员发起，请忽略本邮件并联系商户管理员。"}' variable_schema,
           '["verificationCode"]' sensitive_variable_names, '系统内置模板：商户账号邮箱验证与首次设密' remark
    UNION ALL SELECT 'MERCHANT_ACCOUNT_ACTIVATION', 'Merchant account activation',
           'ACCOUNT_ACTIVATION', 'en-US', '[${systemName}] Activate your merchant account',
           '<div data-template-theme="vexra-merchant-blue-green-v1" style="margin:0;padding:32px 16px;background:#EFF6F6;font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;color:#123047;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #CFE3E2;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#0B4F6C;color:#FFFFFF;"><div style="font-size:13px;color:#BFE3E1;">Vexra Merchant · ${merchantId}</div><div style="margin-top:6px;font-size:22px;font-weight:700;">Activate your merchant account</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:700;">Hello ${userName},</p><p style="margin:0 0 18px;color:#526A79;">Account <strong>${loginAccount}</strong> is ready. Verify your email and set your first password.</p><div style="margin:20px 0;padding:22px;background:#F0F7F6;border:1px solid #B9D9D5;border-radius:8px;text-align:center;"><div style="font-size:12px;color:#526A79;">ONE-TIME CODE</div><div style="margin-top:8px;font-size:32px;letter-spacing:4px;font-weight:700;color:#123047;">${verificationCode}</div><div style="margin-top:8px;font-size:12px;color:#526A79;">Valid for ${expireMinutes} minutes</div></div><div style="text-align:center;"><a href="${verificationUrl}" style="display:inline-block;padding:11px 22px;border-radius:6px;background:#0F766E;color:#FFFFFF;text-decoration:none;font-weight:700;">Verify email and set password</a></div><div style="margin-top:18px;padding:14px 16px;background:#F7FAFC;border:1px solid #D8E2E8;border-radius:6px;color:#526A79;">Created at: ${requestTime}</div><div style="margin-top:18px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #D97706;color:#92400E;">${securityNotice}</div></div><div style="padding:16px 28px;background:#EFF6F6;border-top:1px solid #CFE3E2;color:#526A79;font-size:12px;">This message was sent automatically by ${systemName}. Never reply, forward, or share the code.</div></div></div>',
           '{"systemName":"Vexra Merchant","userName":"Alex","loginAccount":"operator","merchantId":"M10000001","verificationCode":"aB3xY7","expireMinutes":30,"verificationUrl":"https://merchant.example.com/account-verification?requestId=example","requestTime":"2026-10-15 10:00:00","securityNotice":"If you did not request this account, ignore this email and contact your merchant administrator."}',
           '["verificationCode"]', 'Built-in template: merchant account activation and first password setup'
    UNION ALL SELECT 'MERCHANT_EMAIL_VERIFICATION', '商户邮箱验证',
           'EMAIL_VERIFICATION', 'zh-CN', '【${systemName}】请验证新的登录邮箱',
           '<div data-template-theme="vexra-merchant-blue-green-v1" style="margin:0;padding:32px 16px;background:#EFF6F6;font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;color:#123047;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #CFE3E2;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#0B4F6C;color:#FFFFFF;"><div style="font-size:13px;color:#BFE3E1;">Vexra Merchant · ${merchantId}</div><div style="margin-top:6px;font-size:22px;font-weight:700;">验证登录邮箱</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:700;">您好，${userName}</p><p style="margin:0 0 18px;color:#526A79;">请验证账号 <strong>${loginAccount}</strong> 的新邮箱。</p><div style="margin:20px 0;padding:22px;background:#F0F7F6;border:1px solid #B9D9D5;border-radius:8px;text-align:center;"><div style="font-size:12px;color:#526A79;">一次性验证码</div><div style="margin-top:8px;font-size:32px;letter-spacing:4px;font-weight:700;color:#123047;">${verificationCode}</div><div style="margin-top:8px;font-size:12px;color:#526A79;">${expireMinutes} 分钟内有效</div></div><div style="text-align:center;"><a href="${verificationUrl}" style="display:inline-block;padding:11px 22px;border-radius:6px;background:#0F766E;color:#FFFFFF;text-decoration:none;font-weight:700;">验证邮箱</a></div><div style="margin-top:18px;padding:14px 16px;background:#F7FAFC;border:1px solid #D8E2E8;border-radius:6px;color:#526A79;">申请时间：${requestTime}</div><div style="margin-top:18px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #D97706;color:#92400E;">${securityNotice}</div></div><div style="padding:16px 28px;background:#EFF6F6;border-top:1px solid #CFE3E2;color:#526A79;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿回复、转发或向任何人透露验证码。</div></div></div>',
           '{"systemName":"Vexra Merchant","userName":"张三","loginAccount":"operator","merchantId":"M10000001","verificationCode":"aB3xY7","expireMinutes":30,"verificationUrl":"https://merchant.example.com/account-verification?requestId=example","requestTime":"2026-10-15 10:00:00","securityNotice":"如非本人或商户管理员发起，请忽略本邮件并联系商户管理员。"}',
           '["verificationCode"]', '系统内置模板：商户登录邮箱所有权验证'
    UNION ALL SELECT 'MERCHANT_EMAIL_VERIFICATION', 'Merchant email verification',
           'EMAIL_VERIFICATION', 'en-US', '[${systemName}] Verify your sign-in email',
           '<div data-template-theme="vexra-merchant-blue-green-v1" style="margin:0;padding:32px 16px;background:#EFF6F6;font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;color:#123047;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #CFE3E2;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#0B4F6C;color:#FFFFFF;"><div style="font-size:13px;color:#BFE3E1;">Vexra Merchant · ${merchantId}</div><div style="margin-top:6px;font-size:22px;font-weight:700;">Verify your sign-in email</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:700;">Hello ${userName},</p><p style="margin:0 0 18px;color:#526A79;">Verify the new email address for account <strong>${loginAccount}</strong>.</p><div style="margin:20px 0;padding:22px;background:#F0F7F6;border:1px solid #B9D9D5;border-radius:8px;text-align:center;"><div style="font-size:12px;color:#526A79;">ONE-TIME CODE</div><div style="margin-top:8px;font-size:32px;letter-spacing:4px;font-weight:700;color:#123047;">${verificationCode}</div><div style="margin-top:8px;font-size:12px;color:#526A79;">Valid for ${expireMinutes} minutes</div></div><div style="text-align:center;"><a href="${verificationUrl}" style="display:inline-block;padding:11px 22px;border-radius:6px;background:#0F766E;color:#FFFFFF;text-decoration:none;font-weight:700;">Verify email</a></div><div style="margin-top:18px;padding:14px 16px;background:#F7FAFC;border:1px solid #D8E2E8;border-radius:6px;color:#526A79;">Requested at: ${requestTime}</div><div style="margin-top:18px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #D97706;color:#92400E;">${securityNotice}</div></div><div style="padding:16px 28px;background:#EFF6F6;border-top:1px solid #CFE3E2;color:#526A79;font-size:12px;">This message was sent automatically by ${systemName}. Never reply, forward, or share the code.</div></div></div>',
           '{"systemName":"Vexra Merchant","userName":"Alex","loginAccount":"operator","merchantId":"M10000001","verificationCode":"aB3xY7","expireMinutes":30,"verificationUrl":"https://merchant.example.com/account-verification?requestId=example","requestTime":"2026-10-15 10:00:00","securityNotice":"If you did not request this change, ignore this email and contact your merchant administrator."}',
           '["verificationCode"]', 'Built-in template: merchant sign-in email ownership verification'
    UNION ALL SELECT 'MERCHANT_PASSWORD_RESET_VERIFY', '商户系统找回密码验证码',
           'PASSWORD_RESET_VERIFY', 'zh-CN', '【${systemName}】找回密码验证码',
           '<div data-template-theme="vexra-merchant-blue-green-v1" style="margin:0;padding:32px 16px;background:#EFF6F6;font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;color:#123047;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #CFE3E2;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#0B4F6C;color:#FFFFFF;"><div style="font-size:13px;color:#BFE3E1;">Vexra Merchant · ${merchantId}</div><div style="margin-top:6px;font-size:22px;font-weight:700;">安全重置密码</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:700;">您好，${userName}</p><p style="margin:0 0 18px;color:#526A79;">请使用以下验证码继续 ${systemName} 的身份验证。</p><div style="margin:20px 0;padding:22px;background:#F0F7F6;border:1px solid #B9D9D5;border-radius:8px;text-align:center;"><div style="font-size:12px;color:#526A79;">一次性验证码</div><div style="margin-top:8px;font-size:32px;letter-spacing:4px;font-weight:700;color:#123047;">${verifyCode}</div><div style="margin-top:8px;font-size:12px;color:#526A79;">${expireMinutes} 分钟内有效</div></div><div style="text-align:center;"><a href="${verificationUrl}" style="display:inline-block;padding:11px 22px;border-radius:6px;background:#0F766E;color:#FFFFFF;text-decoration:none;font-weight:700;">继续安全重置密码</a></div><div style="margin-top:18px;padding:14px 16px;background:#F7FAFC;border:1px solid #D8E2E8;border-radius:6px;color:#526A79;">申请时间：${requestTime}</div><div style="margin-top:18px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #D97706;color:#92400E;">${securityNotice}</div></div><div style="padding:16px 28px;background:#EFF6F6;border-top:1px solid #CFE3E2;color:#526A79;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿回复、转发或向任何人透露验证码。</div></div></div>',
           '{"systemName":"Vexra Merchant","userName":"operator","merchantId":"M10000001","verifyCode":"aB3xY7","expireMinutes":30,"verificationUrl":"https://merchant.example.com/forgot-password?requestId=example","requestTime":"2026-10-15 10:00:00","securityNotice":"如非本人操作，请忽略本邮件并联系商户管理员。"}',
           '["verifyCode"]', '系统内置模板：商户找回密码邮箱验证码'
    UNION ALL SELECT 'MERCHANT_PASSWORD_RESET_VERIFY', 'Merchant password recovery code',
           'PASSWORD_RESET_VERIFY', 'en-US', '[${systemName}] Password recovery code',
           '<div data-template-theme="vexra-merchant-blue-green-v1" style="margin:0;padding:32px 16px;background:#EFF6F6;font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;color:#123047;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #CFE3E2;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#0B4F6C;color:#FFFFFF;"><div style="font-size:13px;color:#BFE3E1;">Vexra Merchant · ${merchantId}</div><div style="margin-top:6px;font-size:22px;font-weight:700;">Secure password reset</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:700;">Hello ${userName},</p><p style="margin:0 0 18px;color:#526A79;">Use the code below to continue identity verification for ${systemName}.</p><div style="margin:20px 0;padding:22px;background:#F0F7F6;border:1px solid #B9D9D5;border-radius:8px;text-align:center;"><div style="font-size:12px;color:#526A79;">ONE-TIME CODE</div><div style="margin-top:8px;font-size:32px;letter-spacing:4px;font-weight:700;color:#123047;">${verifyCode}</div><div style="margin-top:8px;font-size:12px;color:#526A79;">Valid for ${expireMinutes} minutes</div></div><div style="text-align:center;"><a href="${verificationUrl}" style="display:inline-block;padding:11px 22px;border-radius:6px;background:#0F766E;color:#FFFFFF;text-decoration:none;font-weight:700;">Continue secure password reset</a></div><div style="margin-top:18px;padding:14px 16px;background:#F7FAFC;border:1px solid #D8E2E8;border-radius:6px;color:#526A79;">Requested at: ${requestTime}</div><div style="margin-top:18px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #D97706;color:#92400E;">${securityNotice}</div></div><div style="padding:16px 28px;background:#EFF6F6;border-top:1px solid #CFE3E2;color:#526A79;font-size:12px;">This message was sent automatically by ${systemName}. Never reply, forward, or share the code.</div></div></div>',
           '{"systemName":"Vexra Merchant","userName":"operator","merchantId":"M10000001","verifyCode":"aB3xY7","expireMinutes":30,"verificationUrl":"https://merchant.example.com/forgot-password?requestId=example","requestTime":"2026-10-15 10:00:00","securityNotice":"If you did not request this, ignore this email and contact your merchant administrator."}',
           '["verifyCode"]', 'Built-in template: merchant password recovery verification'
    UNION ALL SELECT 'MERCHANT_PASSWORD_RESET_SUCCESS', '商户系统密码重置成功通知',
           'PASSWORD_RESET_SUCCESS', 'zh-CN', '【${systemName}】密码已重置',
           '<div data-template-theme="vexra-merchant-blue-green-v1" style="margin:0;padding:32px 16px;background:#EFF6F6;font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;color:#123047;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #CFE3E2;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#0B4F6C;color:#FFFFFF;"><div style="font-size:13px;color:#BFE3E1;">Vexra Merchant · ${merchantId}</div><div style="margin-top:6px;font-size:22px;font-weight:700;">密码重置成功</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:700;">您好，${userName}</p><p style="margin:0 0 18px;color:#526A79;">您的 ${systemName} 登录密码已更新。</p><div style="padding:18px;background:#ECFDF5;border:1px solid #A7F3D0;border-radius:8px;color:#065F46;"><div style="font-weight:700;">安全变更已完成</div><div style="margin-top:6px;">操作时间：${operationTime}</div></div><div style="margin-top:18px;padding:12px 14px;background:#FEF2F2;border-left:4px solid #DC2626;color:#991B1B;">${securityNotice}</div></div><div style="padding:16px 28px;background:#EFF6F6;border-top:1px solid #CFE3E2;color:#526A79;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿直接回复。</div></div></div>',
           '{"systemName":"Vexra Merchant","userName":"operator","merchantId":"M10000001","operationTime":"2026-10-15 10:05:00","securityNotice":"所有旧会话均已退出。如非本人操作，请立即联系商户管理员。"}',
           '[]', '系统内置模板：商户密码重置成功通知'
    UNION ALL SELECT 'MERCHANT_PASSWORD_RESET_SUCCESS', 'Merchant password reset confirmation',
           'PASSWORD_RESET_SUCCESS', 'en-US', '[${systemName}] Password reset completed',
           '<div data-template-theme="vexra-merchant-blue-green-v1" style="margin:0;padding:32px 16px;background:#EFF6F6;font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;color:#123047;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #CFE3E2;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#0B4F6C;color:#FFFFFF;"><div style="font-size:13px;color:#BFE3E1;">Vexra Merchant · ${merchantId}</div><div style="margin-top:6px;font-size:22px;font-weight:700;">Password reset completed</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:700;">Hello ${userName},</p><p style="margin:0 0 18px;color:#526A79;">Your ${systemName} sign-in password has been updated.</p><div style="padding:18px;background:#ECFDF5;border:1px solid #A7F3D0;border-radius:8px;color:#065F46;"><div style="font-weight:700;">Security change completed</div><div style="margin-top:6px;">Completed at: ${operationTime}</div></div><div style="margin-top:18px;padding:12px 14px;background:#FEF2F2;border-left:4px solid #DC2626;color:#991B1B;">${securityNotice}</div></div><div style="padding:16px 28px;background:#EFF6F6;border-top:1px solid #CFE3E2;color:#526A79;font-size:12px;">This message was sent automatically by ${systemName}. Please do not reply.</div></div></div>',
           '{"systemName":"Vexra Merchant","userName":"operator","merchantId":"M10000001","operationTime":"2026-10-15 10:05:00","securityNotice":"All previous sessions were signed out. Contact your merchant administrator immediately if this was not you."}',
           '[]', 'Built-in template: merchant password reset confirmation'
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

-- 历史模板包含明文初始密码或临时密码，仅停用以保留既有发送记录的可追溯性。
UPDATE msg_email_template
SET status = 0,
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP(3)
WHERE template_code IN ('MERCHANT_ACCOUNT_CREATED', 'MERCHANT_PASSWORD_CHANGED_BY_ADMIN')
  AND deleted = 0;
