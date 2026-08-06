-- Merchant freeze, locale and bilingual email template migration.
SET NAMES utf8mb4;

SET @add_merchant_default_locale = IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'base_merchant_info'
          AND COLUMN_NAME = 'default_locale'
    ),
    'SELECT 1',
    'ALTER TABLE base_merchant_info ADD COLUMN default_locale VARCHAR(20) NOT NULL DEFAULT ''zh-CN'' COMMENT ''商户系统和邮件默认语言'' AFTER merchant_status'
);
PREPARE add_merchant_default_locale_stmt FROM @add_merchant_default_locale;
EXECUTE add_merchant_default_locale_stmt;
DEALLOCATE PREPARE add_merchant_default_locale_stmt;

UPDATE base_merchant_info
SET default_locale = 'zh-CN'
WHERE default_locale IS NULL OR default_locale NOT IN ('zh-CN', 'en-US');

SET @merchant_status_permission_id = (
    SELECT MIN(id) FROM sys_permission
    WHERE app_id = 1 AND permission_code = 'merchant:info:changeStatus' AND deleted = 0
);
SET @legacy_merchant_disable_permission_id = (
    SELECT MIN(id) FROM sys_permission
    WHERE app_id = 1 AND (id = 304 OR permission_code = 'merchant:info:disable') AND deleted = 0
);

UPDATE sys_permission
SET permission_code = 'merchant:info:changeStatus',
    permission_name = '商户冻结/解冻',
    resource_method = 'PUT',
    resource_path = '/admin/merchants/**/status',
    updated_at = CURRENT_TIMESTAMP(3)
WHERE id = COALESCE(@merchant_status_permission_id, @legacy_merchant_disable_permission_id);

SET @merchant_status_permission_id = (
    SELECT MIN(id) FROM sys_permission
    WHERE app_id = 1 AND permission_code = 'merchant:info:changeStatus' AND deleted = 0
);

INSERT IGNORE INTO sys_role_permission (
    app_id, role_id, permission_id, created_at, created_by, deleted
)
SELECT relation.app_id, relation.role_id, @merchant_status_permission_id,
       relation.created_at, relation.created_by, 0
FROM sys_role_permission relation
WHERE relation.permission_id = @legacy_merchant_disable_permission_id
  AND relation.deleted = 0
  AND @legacy_merchant_disable_permission_id <> @merchant_status_permission_id;

DELETE FROM sys_role_permission
WHERE permission_id = @legacy_merchant_disable_permission_id
  AND @legacy_merchant_disable_permission_id <> @merchant_status_permission_id;

DELETE FROM sys_permission
WHERE id = @legacy_merchant_disable_permission_id
  AND @legacy_merchant_disable_permission_id <> @merchant_status_permission_id;

INSERT INTO sys_dict_data (
    dict_type, dict_label, dict_value, locale, dict_sort, list_class, is_default, status, deleted
)
VALUES
    ('email_scene_code', '商户状态变更', 'MERCHANT_STATUS_CHANGED', 'zh-CN', 10, 'warning', 0, 1, 0),
    ('email_scene_code', 'Merchant Status Changed', 'MERCHANT_STATUS_CHANGED', 'en-US', 10, 'warning', 0, 1, 0)
ON DUPLICATE KEY UPDATE
    dict_label = VALUES(dict_label),
    dict_sort = VALUES(dict_sort),
    list_class = VALUES(list_class),
    status = VALUES(status),
    deleted = 0;

INSERT INTO msg_email_template (
    template_code, template_name, app_code, scene_code, locale, subject_template, content_type,
    content_template, variable_schema, sensitive_variable_names, status, system_builtin, version_no,
    remark, create_by, update_by, deleted
)
SELECT item.template_code, item.template_name, 'MERCHANT', 'MERCHANT_STATUS_CHANGED', item.locale,
       item.subject_template, 'HTML', item.content_template,
       '{"systemName":"Vexra Merchant","merchantName":"Example Merchant","merchantId":"M10000001","operatorName":"System Administrator","operationTime":"2026-08-05 10:00:00"}',
       '[]', 1, 1, 1, '系统内置模板：商户冻结与解冻通知', 'system', 'system', 0
FROM (
    SELECT 'MERCHANT_FROZEN' template_code, '商户冻结通知' template_name, 'zh-CN' locale,
           '【${systemName}】您的商户已被冻结' subject_template,
           '<div style="margin:0;padding:32px 16px;background:#F3F7FF;font-family:Arial,Microsoft YaHei,sans-serif;color:#0F172A;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#2563EB;color:#FFFFFF;"><div style="font-size:13px;color:#DBEAFE;">Vexra Merchant</div><div style="margin-top:6px;font-size:22px;font-weight:700;">商户冻结通知</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 16px;">您好，${merchantName}：</p><p style="margin:0 0 16px;color:#64748B;">您的商户已被冻结，商户旗下用户将无法登录商户系统。</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">商户号：<strong>${merchantId}</strong></p><p style="margin:0 0 8px;">操作人：${operatorName}</p><p style="margin:0;">操作时间：${operationTime}</p></div><div style="margin-top:16px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #F59E0B;color:#9A3412;">如需恢复使用，请联系您的专属管家。</div></div><div style="padding:16px 28px;background:#F3F7FF;border-top:1px solid #DBEAFE;color:#64748B;font-size:12px;">此邮件由系统自动发送，请勿直接回复。</div></div></div>' content_template
    UNION ALL SELECT 'MERCHANT_UNFROZEN', '商户解冻通知', 'zh-CN',
           '【${systemName}】您的商户已解除冻结',
           '<div style="margin:0;padding:32px 16px;background:#F3F7FF;font-family:Arial,Microsoft YaHei,sans-serif;color:#0F172A;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#2563EB;color:#FFFFFF;"><div style="font-size:13px;color:#DBEAFE;">Vexra Merchant</div><div style="margin-top:6px;font-size:22px;font-weight:700;">商户解冻通知</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 16px;">您好，${merchantName}：</p><p style="margin:0 0 16px;color:#64748B;">您的商户已解除冻结，商户旗下用户现在可以正常登录商户系统。</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">商户号：<strong>${merchantId}</strong></p><p style="margin:0 0 8px;">操作人：${operatorName}</p><p style="margin:0;">操作时间：${operationTime}</p></div></div><div style="padding:16px 28px;background:#F3F7FF;border-top:1px solid #DBEAFE;color:#64748B;font-size:12px;">此邮件由系统自动发送，请勿直接回复。</div></div></div>'
    UNION ALL SELECT 'MERCHANT_FROZEN', 'Merchant Frozen Notice', 'en-US',
           '[${systemName}] Your merchant account has been frozen',
           '<div style="margin:0;padding:32px 16px;background:#F3F7FF;font-family:Arial,sans-serif;color:#0F172A;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#2563EB;color:#FFFFFF;"><div style="font-size:13px;color:#DBEAFE;">Vexra Merchant</div><div style="margin-top:6px;font-size:22px;font-weight:700;">Merchant frozen</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 16px;">Hello ${merchantName},</p><p style="margin:0 0 16px;color:#64748B;">Your merchant account has been frozen. All users under this merchant can no longer sign in.</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">Merchant ID: <strong>${merchantId}</strong></p><p style="margin:0 0 8px;">Operator: ${operatorName}</p><p style="margin:0;">Operation time: ${operationTime}</p></div><div style="margin-top:16px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #F59E0B;color:#9A3412;">Please contact your dedicated account manager for assistance.</div></div><div style="padding:16px 28px;background:#F3F7FF;border-top:1px solid #DBEAFE;color:#64748B;font-size:12px;">This is an automated message. Please do not reply.</div></div></div>'
    UNION ALL SELECT 'MERCHANT_UNFROZEN', 'Merchant Unfrozen Notice', 'en-US',
           '[${systemName}] Your merchant account has been unfrozen',
           '<div style="margin:0;padding:32px 16px;background:#F3F7FF;font-family:Arial,sans-serif;color:#0F172A;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#2563EB;color:#FFFFFF;"><div style="font-size:13px;color:#DBEAFE;">Vexra Merchant</div><div style="margin-top:6px;font-size:22px;font-weight:700;">Merchant unfrozen</div></div><div style="padding:28px;line-height:1.7;font-size:14px;"><p style="margin:0 0 16px;">Hello ${merchantName},</p><p style="margin:0 0 16px;color:#64748B;">Your merchant account has been unfrozen. Users under this merchant can now sign in normally.</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">Merchant ID: <strong>${merchantId}</strong></p><p style="margin:0 0 8px;">Operator: ${operatorName}</p><p style="margin:0;">Operation time: ${operationTime}</p></div></div><div style="padding:16px 28px;background:#F3F7FF;border-top:1px solid #DBEAFE;color:#64748B;font-size:12px;">This is an automated message. Please do not reply.</div></div></div>'
) item
ON DUPLICATE KEY UPDATE
    template_name = VALUES(template_name),
    subject_template = VALUES(subject_template),
    content_template = VALUES(content_template),
    variable_schema = VALUES(variable_schema),
    status = 1,
    system_builtin = 1,
    version_no = GREATEST(msg_email_template.version_no, 1),
    update_by = 'system',
    deleted = 0;

-- Build an English blue/white counterpart from each retained Chinese system template.
INSERT INTO msg_email_template (
    template_code, template_name, app_code, scene_code, locale, subject_template, content_type,
    content_template, variable_schema, sensitive_variable_names, status, system_builtin, version_no,
    remark, create_by, update_by, deleted
)
SELECT source.template_code,
       CASE source.template_code
           WHEN 'ADMIN_ACCOUNT_CREATED' THEN 'Admin Account Created'
           WHEN 'MERCHANT_ACCOUNT_CREATED' THEN 'Merchant Account Created'
           WHEN 'ADMIN_PASSWORD_CHANGED_BY_ADMIN' THEN 'Admin Password Changed'
           WHEN 'MERCHANT_PASSWORD_CHANGED_BY_ADMIN' THEN 'Merchant Password Changed'
           WHEN 'API_KEY_CREATED' THEN 'API Key Created'
           WHEN 'API_KEY_RESET' THEN 'API Key Rotated'
           WHEN 'API_KEY_ENABLED' THEN 'API Key Enabled'
           WHEN 'API_KEY_DISABLED' THEN 'API Key Disabled'
           WHEN 'ADMIN_MFA_BIND_NOTICE' THEN 'Admin MFA Binding Required'
           WHEN 'ADMIN_MFA_ENABLED_NOTICE' THEN 'Admin MFA Enabled'
           WHEN 'ADMIN_MFA_RESET_NOTICE' THEN 'Admin MFA Reset'
           WHEN 'ADMIN_MFA_DISABLED_NOTICE' THEN 'Admin MFA Disabled'
           WHEN 'ADMIN_MFA_EXEMPT_NOTICE' THEN 'Admin MFA Exemption'
           WHEN 'MERCHANT_MFA_BIND_NOTICE' THEN 'Merchant MFA Binding Required'
           WHEN 'MERCHANT_MFA_ENABLED_NOTICE' THEN 'Merchant MFA Enabled'
           WHEN 'MERCHANT_MFA_RESET_NOTICE' THEN 'Merchant MFA Reset'
           WHEN 'MERCHANT_MFA_DISABLED_NOTICE' THEN 'Merchant MFA Disabled'
           WHEN 'MERCHANT_MFA_EXEMPT_NOTICE' THEN 'Merchant MFA Exemption'
           WHEN 'CHANNEL_ALERT_DEFAULT' THEN 'Default Channel Alert'
       END,
       source.app_code, source.scene_code, 'en-US',
       CASE source.template_code
           WHEN 'ADMIN_ACCOUNT_CREATED' THEN '[${systemName}] Your admin account is ready'
           WHEN 'MERCHANT_ACCOUNT_CREATED' THEN '[${systemName}] Your merchant account is ready'
           WHEN 'ADMIN_PASSWORD_CHANGED_BY_ADMIN' THEN '[${systemName}] Your admin password was changed'
           WHEN 'MERCHANT_PASSWORD_CHANGED_BY_ADMIN' THEN '[${systemName}] Your merchant password was changed'
           WHEN 'API_KEY_CREATED' THEN '[${systemName}] API key created'
           WHEN 'API_KEY_RESET' THEN '[${systemName}] API key rotated'
           WHEN 'API_KEY_ENABLED' THEN '[${systemName}] API key enabled'
           WHEN 'API_KEY_DISABLED' THEN '[${systemName}] API key disabled'
           WHEN 'CHANNEL_ALERT_DEFAULT' THEN '[Vexra Admin] Channel alert: ${ruleName}'
           ELSE '[${systemName}] Security notification'
       END,
       'HTML',
       CONCAT(
           '<div style="margin:0;padding:32px 16px;background:#F3F7FF;font-family:Arial,sans-serif;color:#0F172A;"><div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;"><div style="padding:24px 28px;background:#2563EB;color:#FFFFFF;"><div style="font-size:13px;color:#DBEAFE;">',
           CASE WHEN source.app_code = 'MERCHANT' THEN 'Vexra Merchant' ELSE 'Vexra Admin' END,
           '</div><div style="margin-top:6px;font-size:22px;font-weight:700;">',
           CASE
               WHEN source.template_code IN ('ADMIN_ACCOUNT_CREATED', 'MERCHANT_ACCOUNT_CREATED') THEN 'Account created'
               WHEN source.template_code IN ('ADMIN_PASSWORD_CHANGED_BY_ADMIN', 'MERCHANT_PASSWORD_CHANGED_BY_ADMIN') THEN 'Password changed'
               WHEN source.template_code = 'API_KEY_CREATED' THEN 'API key created'
               WHEN source.template_code = 'API_KEY_RESET' THEN 'API key rotated'
               WHEN source.template_code = 'API_KEY_ENABLED' THEN 'API key enabled'
               WHEN source.template_code = 'API_KEY_DISABLED' THEN 'API key disabled'
               WHEN source.template_code = 'CHANNEL_ALERT_DEFAULT' THEN 'Channel alert notification'
               ELSE 'Security notification'
           END,
           '</div></div><div style="padding:28px;line-height:1.7;font-size:14px;">',
           CASE
               WHEN source.template_code = 'ADMIN_ACCOUNT_CREATED' THEN '<p>Hello ${userName},</p><p>Your ${systemName} account is ready.</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p>Login: <strong>${loginAccount}</strong></p><p>Initial password: <strong>${initialPassword}</strong></p><p>Login URL: <a style="color:#2563EB;" href="${loginUrl}">${loginUrl}</a></p></div><p>${verifyCodeGuide}</p><p>${mfaGuide}</p>'
               WHEN source.template_code = 'MERCHANT_ACCOUNT_CREATED' THEN '<p>Hello ${userName},</p><p>Your ${systemName} account is ready.</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p>Merchant: <strong>${merchantName}</strong> (${merchantId})</p><p>Login: <strong>${loginAccount}</strong></p><p>Initial password: <strong>${initialPassword}</strong></p><p>Login URL: <a style="color:#2563EB;" href="${loginUrl}">${loginUrl}</a></p></div><p>${verifyCodeGuide}</p><p>${mfaGuide}</p>'
               WHEN source.template_code = 'ADMIN_PASSWORD_CHANGED_BY_ADMIN' THEN '<p>Hello ${userName},</p><p>Your admin password was changed by ${operatorName}.</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p>Login: <strong>${loginAccount}</strong></p><p>Temporary password: <strong>${temporaryPassword}</strong></p><p>Operation time: ${operationTime}</p><p>Login URL: <a style="color:#2563EB;" href="${loginUrl}">${loginUrl}</a></p></div>'
               WHEN source.template_code = 'MERCHANT_PASSWORD_CHANGED_BY_ADMIN' THEN '<p>Hello ${userName},</p><p>Your merchant password was changed by ${operatorName}.</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p>Merchant: <strong>${merchantName}</strong> (${merchantId})</p><p>Login: <strong>${loginAccount}</strong></p><p>Temporary password: <strong>${temporaryPassword}</strong></p><p>Operation time: ${operationTime}</p><p>Login URL: <a style="color:#2563EB;" href="${loginUrl}">${loginUrl}</a></p></div>'
               WHEN source.template_code IN ('API_KEY_CREATED', 'API_KEY_RESET', 'API_KEY_ENABLED', 'API_KEY_DISABLED') THEN '<p>Hello ${merchantName},</p><p>Your API key status has changed.</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p>Merchant ID: <strong>${merchantNo}</strong></p><p>Key: ${keyName}</p><p>Fingerprint ending: <strong>${keyLast4}</strong></p><p>Operator: ${operatorName}</p><p>Operation time: ${operationTime}</p></div>'
               WHEN source.template_code = 'CHANNEL_ALERT_DEFAULT' THEN '<p>A channel alert has been triggered.</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p>Rule: <strong>${ruleName}</strong></p><p>Channel: ${channelName} (${channelCode})</p><p>Business / payment / card brand: ${businessType} / ${paymentMethod} / ${cardBrand}</p><p>Rule type / level: ${ruleType} / ${alertLevel}</p><p>Triggered at: ${triggerTime}</p><p>Value: <strong>${triggerValue}</strong></p><p>Description: ${ruleDescription}</p></div>'
               WHEN source.template_code LIKE '%MFA_EXEMPT%' THEN '<p>Hello ${loginAccount},</p><p>An MFA exemption was configured for your account.</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p>Reason: ${reason}</p><p>Valid until: ${exemptUntil}</p></div>'
               WHEN source.template_code LIKE '%MFA_DISABLED%' THEN '<p>Hello ${loginAccount},</p><p>MFA was disabled for your account.</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p>Reason: ${reason}</p></div>'
               ELSE '<p>Hello ${loginAccount},</p><p>Your MFA security settings have changed.</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p>Reason: ${reason}</p><p>Security entry: <a style="color:#2563EB;" href="${bindUrl}">${bindUrl}</a></p></div>'
           END,
           '</div><div style="padding:16px 28px;background:#F3F7FF;border-top:1px solid #DBEAFE;color:#64748B;font-size:12px;">This is an automated message. Please do not reply or forward sensitive information.</div></div></div>'
       ),
       source.variable_schema, source.sensitive_variable_names, source.status, 1,
       GREATEST(source.version_no, 2), CONCAT(source.remark, ' / English'), 'system', 'system', 0
FROM msg_email_template source
WHERE source.locale = 'zh-CN'
  AND source.deleted = 0
  AND source.template_code IN (
      'ADMIN_ACCOUNT_CREATED', 'MERCHANT_ACCOUNT_CREATED',
      'ADMIN_PASSWORD_CHANGED_BY_ADMIN', 'MERCHANT_PASSWORD_CHANGED_BY_ADMIN',
      'API_KEY_CREATED', 'API_KEY_RESET', 'API_KEY_ENABLED', 'API_KEY_DISABLED',
      'ADMIN_MFA_BIND_NOTICE', 'ADMIN_MFA_ENABLED_NOTICE', 'ADMIN_MFA_RESET_NOTICE',
      'ADMIN_MFA_DISABLED_NOTICE', 'ADMIN_MFA_EXEMPT_NOTICE',
      'MERCHANT_MFA_BIND_NOTICE', 'MERCHANT_MFA_ENABLED_NOTICE', 'MERCHANT_MFA_RESET_NOTICE',
      'MERCHANT_MFA_DISABLED_NOTICE', 'MERCHANT_MFA_EXEMPT_NOTICE',
      'CHANNEL_ALERT_DEFAULT'
  )
ON DUPLICATE KEY UPDATE
    template_name = VALUES(template_name),
    subject_template = VALUES(subject_template),
    content_template = VALUES(content_template),
    variable_schema = VALUES(variable_schema),
    sensitive_variable_names = VALUES(sensitive_variable_names),
    status = VALUES(status),
    system_builtin = 1,
    version_no = GREATEST(msg_email_template.version_no, VALUES(version_no)),
    update_by = 'system',
    deleted = 0;

-- Keep user-created templates, but physically remove every retired built-in definition.
DELETE FROM msg_email_template
WHERE system_builtin = 1
  AND template_code NOT IN (
      'ADMIN_ACCOUNT_CREATED', 'MERCHANT_ACCOUNT_CREATED',
      'ADMIN_PASSWORD_CHANGED_BY_ADMIN', 'MERCHANT_PASSWORD_CHANGED_BY_ADMIN',
      'API_KEY_CREATED', 'API_KEY_RESET', 'API_KEY_ENABLED', 'API_KEY_DISABLED',
      'ADMIN_MFA_BIND_NOTICE', 'ADMIN_MFA_ENABLED_NOTICE', 'ADMIN_MFA_RESET_NOTICE',
      'ADMIN_MFA_DISABLED_NOTICE', 'ADMIN_MFA_EXEMPT_NOTICE',
      'MERCHANT_MFA_BIND_NOTICE', 'MERCHANT_MFA_ENABLED_NOTICE', 'MERCHANT_MFA_RESET_NOTICE',
      'MERCHANT_MFA_DISABLED_NOTICE', 'MERCHANT_MFA_EXEMPT_NOTICE',
      'CHANNEL_ALERT_DEFAULT', 'MERCHANT_FROZEN', 'MERCHANT_UNFROZEN'
  );
