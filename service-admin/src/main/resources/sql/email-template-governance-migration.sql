-- 邮件模板治理升级脚本。
-- 目标：补齐商户角色归属字段，统一内置模板主题，新增密码变更通知，并物理删除无触发模板。
-- 本脚本只删除 msg_email_template 中指定模板定义，不删除 msg_email_send_record 历史发送记录。

SET NAMES utf8mb4;

INSERT INTO sys_dict_data (
    dict_type, dict_label, dict_value, locale, dict_sort, list_class, is_default, status, deleted
)
VALUES
    ('email_scene_code', '密码变更通知', 'PASSWORD_CHANGED', 'zh-CN', 9, 'warning', 0, 1, 0),
    ('email_scene_code', 'Password Changed', 'PASSWORD_CHANGED', 'en-US', 9, 'warning', 0, 1, 0)
ON DUPLICATE KEY UPDATE
    dict_label = VALUES(dict_label),
    dict_sort = VALUES(dict_sort),
    list_class = VALUES(list_class),
    status = VALUES(status),
    deleted = 0;

SET @add_role_merchant_column = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sys_role'
          AND COLUMN_NAME = 'merchant_id'
    ),
    'SELECT 1',
    'ALTER TABLE sys_role ADD COLUMN merchant_id VARCHAR(32) NULL COMMENT ''商户号，商户系统角色必须绑定当前商户'' AFTER role_name'
);
PREPARE add_role_merchant_column_stmt FROM @add_role_merchant_column;
EXECUTE add_role_merchant_column_stmt;
DEALLOCATE PREPARE add_role_merchant_column_stmt;

SET @add_role_merchant_index = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sys_role'
          AND INDEX_NAME = 'idx_sys_role_merchant'
    ),
    'SELECT 1',
    'ALTER TABLE sys_role ADD INDEX idx_sys_role_merchant (merchant_id, status, deleted)'
);
PREPARE add_role_merchant_index_stmt FROM @add_role_merchant_index;
EXECUTE add_role_merchant_index_stmt;
DEALLOCATE PREPARE add_role_merchant_index_stmt;

UPDATE sys_role role_row
JOIN base_merchant_info merchant
  ON role_row.role_code IN (
      CONCAT('MERCHANT_ADMIN_', merchant.merchant_id),
      CONCAT('MERCHANT_OPERATOR_', merchant.merchant_id),
      CONCAT('MERCHANT_VIEWER_', merchant.merchant_id)
  )
SET role_row.merchant_id = merchant.merchant_id,
    role_row.updated_at = CURRENT_TIMESTAMP(3)
WHERE role_row.app_id = 2
  AND role_row.merchant_id IS NULL
  AND role_row.deleted = 0
  AND merchant.deleted = 0;

INSERT INTO msg_email_template (
    template_code, template_name, app_code, scene_code, locale, subject_template, content_type,
    content_template, variable_schema, sensitive_variable_names, status, system_builtin, version_no,
    remark, create_by, update_by, deleted
)
SELECT item.template_code, item.template_name, item.app_code, 'PASSWORD_CHANGED', 'zh-CN',
       item.subject_template, 'HTML', item.content_template, item.variable_schema,
       '["temporaryPassword"]', 1, 1, 2, item.remark, 'system', 'system', 0
FROM (
    SELECT 'ADMIN_PASSWORD_CHANGED_BY_ADMIN' template_code,
           '管理系统密码变更通知' template_name,
           'ADMIN' app_code,
           '【${systemName}】密码已由管理员修改' subject_template,
           '<p>您好，${userName}：</p><p>您的管理系统登录密码已由 ${operatorName} 修改。</p><p>登录账号：${loginAccount}</p><p>临时密码：${temporaryPassword}</p><p>操作时间：${operationTime}</p><p>登录地址：${loginUrl}</p>' content_template,
           '{"systemName":"Vexra Admin","userName":"张三","loginAccount":"admin@example.com","temporaryPassword":"******","operatorName":"系统管理员","operationTime":"2026-07-29 10:00:00","loginUrl":"https://admin.example.com/login"}' variable_schema,
           '系统内置模板：管理员修改管理系统账号密码通知' remark
    UNION ALL SELECT 'MERCHANT_PASSWORD_CHANGED_BY_ADMIN',
           '商户系统密码变更通知',
           'MERCHANT',
           '【${systemName}】密码已由管理员修改',
           '<p>您好，${userName}：</p><p>您的商户系统登录密码已由 ${operatorName} 修改。</p><p>商户：${merchantName}（${merchantId}）</p><p>登录账号：${loginAccount}</p><p>临时密码：${temporaryPassword}</p><p>操作时间：${operationTime}</p><p>登录地址：${loginUrl}</p>',
           '{"systemName":"Vexra Merchant","userName":"张三","merchantId":"M10000001","merchantName":"示例商户","loginAccount":"merchant@example.com","temporaryPassword":"******","operatorName":"商户管理员","operationTime":"2026-07-29 10:00:00","loginUrl":"https://merchant.example.com/login"}',
           '系统内置模板：商户管理员修改员工密码通知'
) item
WHERE NOT EXISTS (
    SELECT 1
    FROM msg_email_template existing
    WHERE existing.template_code = item.template_code
      AND existing.locale = 'zh-CN'
      AND existing.deleted = 0
);

UPDATE msg_email_template
SET content_template = CONCAT(
        '<div style="margin:0;padding:32px 16px;background:#F3F7FF;font-family:Arial,''Microsoft YaHei'',sans-serif;color:#0F172A;">',
        '<div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;">',
        '<div style="padding:24px 28px;background:#2563EB;color:#FFFFFF;">',
        '<div style="font-size:13px;color:#DBEAFE;">',
        CASE
            WHEN template_code = 'CHANNEL_ALERT_DEFAULT' THEN 'Vexra Admin Operations'
            WHEN app_code = 'MERCHANT' THEN 'Vexra Merchant'
            ELSE 'Vexra Admin'
        END,
        '</div><div style="margin-top:6px;font-size:22px;font-weight:700;">',
        CASE CONCAT(template_code, ':', locale)
            WHEN 'ADMIN_ACCOUNT_CREATED:zh-CN' THEN '管理系统账号已开通'
            WHEN 'MERCHANT_ACCOUNT_CREATED:zh-CN' THEN '商户系统账号已开通'
            WHEN 'ADMIN_PASSWORD_CHANGED_BY_ADMIN:zh-CN' THEN '管理系统密码已修改'
            WHEN 'MERCHANT_PASSWORD_CHANGED_BY_ADMIN:zh-CN' THEN '商户系统密码已修改'
            WHEN 'API_KEY_CREATED:zh-CN' THEN 'API 密钥已创建'
            WHEN 'API_KEY_RESET:zh-CN' THEN 'API 密钥已轮换'
            WHEN 'API_KEY_ENABLED:zh-CN' THEN 'API 密钥已启用'
            WHEN 'API_KEY_DISABLED:zh-CN' THEN 'API 密钥已停用'
            WHEN 'ADMIN_MFA_BIND_NOTICE:zh-CN' THEN '请绑定多因素认证（MFA）'
            WHEN 'ADMIN_MFA_ENABLED_NOTICE:zh-CN' THEN '多因素认证（MFA）已开启'
            WHEN 'ADMIN_MFA_RESET_NOTICE:zh-CN' THEN '多因素认证（MFA）已重置'
            WHEN 'ADMIN_MFA_DISABLED_NOTICE:zh-CN' THEN '多因素认证（MFA）已停用'
            WHEN 'ADMIN_MFA_EXEMPT_NOTICE:zh-CN' THEN '多因素认证（MFA）豁免已配置'
            WHEN 'MERCHANT_MFA_BIND_NOTICE:zh-CN' THEN '请绑定多因素认证（MFA）'
            WHEN 'MERCHANT_MFA_ENABLED_NOTICE:zh-CN' THEN '多因素认证（MFA）已开启'
            WHEN 'MERCHANT_MFA_RESET_NOTICE:zh-CN' THEN '多因素认证（MFA）已重置'
            WHEN 'MERCHANT_MFA_DISABLED_NOTICE:zh-CN' THEN '多因素认证（MFA）已停用'
            WHEN 'MERCHANT_MFA_EXEMPT_NOTICE:zh-CN' THEN '多因素认证（MFA）豁免已配置'
            WHEN 'CHANNEL_ALERT_DEFAULT:zh-CN' THEN '渠道预警通知'
            WHEN 'CHANNEL_ALERT_DEFAULT:en-US' THEN 'Channel alert notification'
        END,
        '</div></div><div style="padding:28px;line-height:1.7;font-size:14px;">',
        CASE CONCAT(template_code, ':', locale)
            WHEN 'ADMIN_ACCOUNT_CREATED:zh-CN' THEN '<p style="margin:0 0 14px;font-size:20px;font-weight:700;">您好，${userName}</p><p style="margin:0 0 16px;color:#64748B;">您的 ${systemName} 账号已创建，请使用以下信息完成首次登录。</p><div style="margin:18px 0;padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0 0 8px;">初始密码：<strong>${initialPassword}</strong></p><p style="margin:0;">登录地址：<a href="${loginUrl}" style="color:#2563EB;word-break:break-all;">${loginUrl}</a></p></div><p style="margin:0 0 8px;">${verifyCodeGuide}</p><p style="margin:0 0 16px;">${mfaGuide}</p><div style="padding:12px 14px;background:#FFF7ED;border-left:4px solid #F59E0B;color:#9A3412;">初始密码属于敏感信息，请勿转发本邮件；首次登录后请立即修改密码。</div>'
            WHEN 'MERCHANT_ACCOUNT_CREATED:zh-CN' THEN '<p style="margin:0 0 14px;font-size:20px;font-weight:700;">您好，${userName}</p><p style="margin:0 0 16px;color:#64748B;">您的 ${systemName} 账号已创建，请使用以下信息完成首次登录。</p><div style="margin:18px 0;padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">商户号：<strong>${merchantId}</strong></p><p style="margin:0 0 8px;">商户名称：<strong>${merchantName}</strong></p><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0 0 8px;">初始密码：<strong>${initialPassword}</strong></p><p style="margin:0;">登录地址：<a href="${loginUrl}" style="color:#2563EB;word-break:break-all;">${loginUrl}</a></p></div><p style="margin:0 0 8px;">${verifyCodeGuide}</p><p style="margin:0 0 16px;">${mfaGuide}</p><div style="padding:12px 14px;background:#FFF7ED;border-left:4px solid #F59E0B;color:#9A3412;">初始密码属于敏感信息，请勿转发本邮件；首次登录后请立即修改密码。</div>'
            WHEN 'ADMIN_PASSWORD_CHANGED_BY_ADMIN:zh-CN' THEN '<p style="margin:0 0 14px;font-size:20px;font-weight:700;">您好，${userName}</p><p style="margin:0 0 16px;color:#64748B;">您的管理系统密码已由 ${operatorName} 修改。</p><div style="margin:18px 0;padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0 0 8px;">临时密码：<strong>${temporaryPassword}</strong></p><p style="margin:0 0 8px;">操作时间：${operationTime}</p><p style="margin:0;">登录地址：<a href="${loginUrl}" style="color:#2563EB;word-break:break-all;">${loginUrl}</a></p></div><div style="padding:12px 14px;background:#FEF2F2;border-left:4px solid #DC2626;color:#991B1B;">请登录后立即修改密码。如非本人授权，请立即联系系统管理员。</div>'
            WHEN 'MERCHANT_PASSWORD_CHANGED_BY_ADMIN:zh-CN' THEN '<p style="margin:0 0 14px;font-size:20px;font-weight:700;">您好，${userName}</p><p style="margin:0 0 16px;color:#64748B;">您的商户系统密码已由 ${operatorName} 修改。</p><div style="margin:18px 0;padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">商户：<strong>${merchantName}</strong>（${merchantId}）</p><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0 0 8px;">临时密码：<strong>${temporaryPassword}</strong></p><p style="margin:0 0 8px;">操作时间：${operationTime}</p><p style="margin:0;">登录地址：<a href="${loginUrl}" style="color:#2563EB;word-break:break-all;">${loginUrl}</a></p></div><div style="padding:12px 14px;background:#FEF2F2;border-left:4px solid #DC2626;color:#991B1B;">请登录后立即修改密码。如非本人授权，请立即联系商户管理员。</div>'
            WHEN 'API_KEY_CREATED:zh-CN' THEN '<p style="margin:0 0 16px;">您好，${merchantName}：</p><p style="margin:0 0 16px;color:#64748B;">您的商户 API 密钥已创建。</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">商户号：<strong>${merchantNo}</strong></p><p style="margin:0 0 8px;">密钥类型：${keyName}</p><p style="margin:0 0 8px;">指纹尾号：<strong>${keyLast4}</strong></p><p style="margin:0 0 8px;">操作人：${operatorName}</p><p style="margin:0;">操作时间：${operationTime}</p></div><p style="margin:16px 0 0;color:#64748B;">邮件不会展示完整 API Key、私钥或密钥材料。</p>'
            WHEN 'API_KEY_RESET:zh-CN' THEN '<p style="margin:0 0 16px;">您好，${merchantName}：</p><p style="margin:0 0 16px;color:#64748B;">您的商户 API 密钥已轮换，旧密钥材料已失效。</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">商户号：<strong>${merchantNo}</strong></p><p style="margin:0 0 8px;">密钥类型：${keyName}</p><p style="margin:0 0 8px;">新指纹尾号：<strong>${keyLast4}</strong></p><p style="margin:0 0 8px;">操作人：${operatorName}</p><p style="margin:0;">操作时间：${operationTime}</p></div><div style="margin-top:16px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #F59E0B;color:#9A3412;">请及时更新对接配置。如非授权操作，请立即联系平台支持。</div>'
            WHEN 'API_KEY_ENABLED:zh-CN' THEN '<p style="margin:0 0 16px;">您好，${merchantName}：</p><p style="margin:0 0 16px;color:#64748B;">您的商户 API 密钥已启用。</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">商户号：<strong>${merchantNo}</strong></p><p style="margin:0 0 8px;">密钥类型：${keyName}</p><p style="margin:0 0 8px;">指纹尾号：<strong>${keyLast4}</strong></p><p style="margin:0 0 8px;">操作人：${operatorName}</p><p style="margin:0;">操作时间：${operationTime}</p></div>'
            WHEN 'API_KEY_DISABLED:zh-CN' THEN '<p style="margin:0 0 16px;">您好，${merchantName}：</p><p style="margin:0 0 16px;color:#64748B;">您的商户 API 密钥已停用。</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">商户号：<strong>${merchantNo}</strong></p><p style="margin:0 0 8px;">密钥类型：${keyName}</p><p style="margin:0 0 8px;">指纹尾号：<strong>${keyLast4}</strong></p><p style="margin:0 0 8px;">操作人：${operatorName}</p><p style="margin:0;">操作时间：${operationTime}</p></div><div style="margin-top:16px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #F59E0B;color:#9A3412;">如非授权操作，请立即联系平台支持。</div>'
            WHEN 'ADMIN_MFA_BIND_NOTICE:zh-CN' THEN '<p style="margin:0 0 16px;">您好，${loginAccount}：</p><p style="margin:0 0 16px;color:#64748B;">您的管理系统账号需要绑定多因素认证。</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0 0 8px;">操作原因：${reason}</p><p style="margin:0;">登录地址：<a href="${bindUrl}" style="color:#2563EB;word-break:break-all;">${bindUrl}</a></p></div><p style="margin:16px 0 0;color:#64748B;">邮件不会包含 MFA 密钥、二维码或验证码。</p>'
            WHEN 'ADMIN_MFA_ENABLED_NOTICE:zh-CN' THEN '<p style="margin:0 0 16px;">您好，${loginAccount}：</p><p style="margin:0 0 16px;color:#64748B;">管理员已为您的管理系统账号开启多因素认证。</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0 0 8px;">操作原因：${reason}</p><p style="margin:0;">绑定入口：<a href="${bindUrl}" style="color:#2563EB;word-break:break-all;">${bindUrl}</a></p></div>'
            WHEN 'ADMIN_MFA_RESET_NOTICE:zh-CN' THEN '<p style="margin:0 0 16px;">您好，${loginAccount}：</p><p style="margin:0 0 16px;color:#64748B;">您的原 MFA 密钥已失效，下次登录时必须重新绑定。</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0 0 8px;">重置原因：${reason}</p><p style="margin:0;">重新绑定：<a href="${bindUrl}" style="color:#2563EB;word-break:break-all;">${bindUrl}</a></p></div>'
            WHEN 'ADMIN_MFA_DISABLED_NOTICE:zh-CN' THEN '<p style="margin:0 0 16px;">您好，${loginAccount}：</p><p style="margin:0 0 16px;color:#64748B;">管理员已停用您账号的多因素认证要求。</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0;">操作原因：${reason}</p></div><div style="margin-top:16px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #F59E0B;color:#9A3412;">如果您不清楚该变更来源，请联系系统管理员。</div>'
            WHEN 'ADMIN_MFA_EXEMPT_NOTICE:zh-CN' THEN '<p style="margin:0 0 16px;">您好，${loginAccount}：</p><p style="margin:0 0 16px;color:#64748B;">管理员已为您的账号配置多因素认证豁免。</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0 0 8px;">豁免有效期：${exemptUntil}</p><p style="margin:0;">豁免原因：${reason}</p></div><div style="margin-top:16px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #F59E0B;color:#9A3412;">豁免账号仍应使用强密码，并限制共享和转借。</div>'
            WHEN 'MERCHANT_MFA_BIND_NOTICE:zh-CN' THEN '<p style="margin:0 0 16px;">您好，${loginAccount}：</p><p style="margin:0 0 16px;color:#64748B;">您的商户系统账号需要绑定多因素认证。</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">商户：<strong>${merchantName}</strong>（${merchantId}）</p><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0 0 8px;">操作原因：${reason}</p><p style="margin:0;">登录地址：<a href="${bindUrl}" style="color:#2563EB;word-break:break-all;">${bindUrl}</a></p></div><p style="margin:16px 0 0;color:#64748B;">邮件不会包含 MFA 密钥、二维码或验证码。</p>'
            WHEN 'MERCHANT_MFA_ENABLED_NOTICE:zh-CN' THEN '<p style="margin:0 0 16px;">您好，${loginAccount}：</p><p style="margin:0 0 16px;color:#64748B;">商户管理员已为您的账号开启多因素认证。</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">商户：<strong>${merchantName}</strong>（${merchantId}）</p><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0 0 8px;">操作原因：${reason}</p><p style="margin:0;">绑定入口：<a href="${bindUrl}" style="color:#2563EB;word-break:break-all;">${bindUrl}</a></p></div>'
            WHEN 'MERCHANT_MFA_RESET_NOTICE:zh-CN' THEN '<p style="margin:0 0 16px;">您好，${loginAccount}：</p><p style="margin:0 0 16px;color:#64748B;">您的原 MFA 密钥已失效，下次登录商户系统时必须重新绑定。</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">商户：<strong>${merchantName}</strong>（${merchantId}）</p><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0 0 8px;">重置原因：${reason}</p><p style="margin:0;">重新绑定：<a href="${bindUrl}" style="color:#2563EB;word-break:break-all;">${bindUrl}</a></p></div>'
            WHEN 'MERCHANT_MFA_DISABLED_NOTICE:zh-CN' THEN '<p style="margin:0 0 16px;">您好，${loginAccount}：</p><p style="margin:0 0 16px;color:#64748B;">商户管理员已停用您账号的多因素认证要求。</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">商户：<strong>${merchantName}</strong>（${merchantId}）</p><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0;">操作原因：${reason}</p></div><div style="margin-top:16px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #F59E0B;color:#9A3412;">如果您不清楚该变更来源，请联系商户管理员。</div>'
            WHEN 'MERCHANT_MFA_EXEMPT_NOTICE:zh-CN' THEN '<p style="margin:0 0 16px;">您好，${loginAccount}：</p><p style="margin:0 0 16px;color:#64748B;">商户管理员已为您的账号配置多因素认证豁免。</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">商户：<strong>${merchantName}</strong>（${merchantId}）</p><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0 0 8px;">豁免有效期：${exemptUntil}</p><p style="margin:0;">豁免原因：${reason}</p></div><div style="margin-top:16px;padding:12px 14px;background:#FFF7ED;border-left:4px solid #F59E0B;color:#9A3412;">豁免账号仍应使用强密码，并限制共享和转借。</div>'
            WHEN 'CHANNEL_ALERT_DEFAULT:zh-CN' THEN '<p style="margin:0 0 16px;color:#64748B;">渠道预警已触发，请及时处理。</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">规则：<strong>${ruleName}</strong></p><p style="margin:0 0 8px;">渠道：${channelName}（${channelCode}）</p><p style="margin:0 0 8px;">业务类型 / 支付方式 / 卡品牌：${businessType} / ${paymentMethod} / ${cardBrand}</p><p style="margin:0 0 8px;">规则类型 / 预警级别：${ruleType} / ${alertLevel}</p><p style="margin:0 0 8px;">触发时间：${triggerTime}</p><p style="margin:0 0 8px;">触发值：<strong>${triggerValue}</strong></p><p style="margin:0;">规则说明：${ruleDescription}</p></div>'
            WHEN 'CHANNEL_ALERT_DEFAULT:en-US' THEN '<p style="margin:0 0 16px;color:#64748B;">A channel alert has been triggered. Please review it promptly.</p><div style="padding:18px;background:#F3F7FF;border:1px solid #DBEAFE;border-radius:6px;"><p style="margin:0 0 8px;">Rule: <strong>${ruleName}</strong></p><p style="margin:0 0 8px;">Channel: ${channelName} (${channelCode})</p><p style="margin:0 0 8px;">Business / payment / card brand: ${businessType} / ${paymentMethod} / ${cardBrand}</p><p style="margin:0 0 8px;">Rule type / alert level: ${ruleType} / ${alertLevel}</p><p style="margin:0 0 8px;">Triggered at: ${triggerTime}</p><p style="margin:0 0 8px;">Trigger value: <strong>${triggerValue}</strong></p><p style="margin:0;">Description: ${ruleDescription}</p></div>'
        END,
        '</div><div style="padding:16px 28px;background:#F3F7FF;border-top:1px solid #DBEAFE;color:#64748B;font-size:12px;">此邮件由系统自动发送，请勿直接回复或转发敏感信息。</div></div></div>'
    ),
    version_no = GREATEST(version_no, 2),
    update_by = 'system'
WHERE template_code IN (
    'ADMIN_ACCOUNT_CREATED', 'MERCHANT_ACCOUNT_CREATED',
    'ADMIN_PASSWORD_CHANGED_BY_ADMIN', 'MERCHANT_PASSWORD_CHANGED_BY_ADMIN',
    'API_KEY_CREATED', 'API_KEY_RESET', 'API_KEY_ENABLED', 'API_KEY_DISABLED',
    'ADMIN_MFA_BIND_NOTICE', 'ADMIN_MFA_ENABLED_NOTICE', 'ADMIN_MFA_RESET_NOTICE',
    'ADMIN_MFA_DISABLED_NOTICE', 'ADMIN_MFA_EXEMPT_NOTICE',
    'MERCHANT_MFA_BIND_NOTICE', 'MERCHANT_MFA_ENABLED_NOTICE', 'MERCHANT_MFA_RESET_NOTICE',
    'MERCHANT_MFA_DISABLED_NOTICE', 'MERCHANT_MFA_EXEMPT_NOTICE',
    'CHANNEL_ALERT_DEFAULT'
)
  AND locale IN ('zh-CN', 'en-US')
  AND (locale = 'zh-CN' OR template_code = 'CHANNEL_ALERT_DEFAULT')
  AND deleted = 0;

UPDATE msg_email_template
SET sensitive_variable_names = CASE
        WHEN template_code IN ('ADMIN_ACCOUNT_CREATED', 'MERCHANT_ACCOUNT_CREATED') THEN '["initialPassword"]'
        WHEN template_code IN ('ADMIN_PASSWORD_CHANGED_BY_ADMIN', 'MERCHANT_PASSWORD_CHANGED_BY_ADMIN') THEN '["temporaryPassword"]'
        ELSE sensitive_variable_names
    END,
    update_by = 'system'
WHERE template_code IN (
    'ADMIN_ACCOUNT_CREATED', 'MERCHANT_ACCOUNT_CREATED',
    'ADMIN_PASSWORD_CHANGED_BY_ADMIN', 'MERCHANT_PASSWORD_CHANGED_BY_ADMIN'
)
  AND deleted = 0;

UPDATE msg_email_template
SET variable_schema = '{"systemName":"Vexra Merchant","merchantName":"示例商户","merchantNo":"M10000001","keyName":"JWT 签名密钥","keyLast4":"A1B2","operatorName":"系统管理员","operationTime":"2026-07-29 10:00:00"}',
    sensitive_variable_names = '[]',
    update_by = 'system'
WHERE template_code IN ('API_KEY_CREATED', 'API_KEY_RESET', 'API_KEY_ENABLED', 'API_KEY_DISABLED')
  AND deleted = 0;

DELETE FROM msg_email_template
WHERE template_code IN (
    'ADMIN_LOGIN_OTP',
    'MERCHANT_LOGIN_OTP',
    'ADMIN_PASSWORD_RESET',
    'MERCHANT_PASSWORD_RESET',
    'MERCHANT_ONBOARDING_APPROVED',
    'MERCHANT_ONBOARDING_REJECTED',
    'MERCHANT_MFA_EXEMPT_NOTICE_COPY_1785241605402'
);
