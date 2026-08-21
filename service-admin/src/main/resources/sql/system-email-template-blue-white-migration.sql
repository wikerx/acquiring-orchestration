-- 系统内置费用、资金和商户访问配置邮件蓝白主题迁移。
-- 仅更新下列明确模板且 system_builtin=1，不修改用户自建或复制模板。

SET NAMES utf8mb4;
START TRANSACTION;

CREATE TABLE IF NOT EXISTS msg_email_template_blue_white_backup_20260820 LIKE msg_email_template;

INSERT IGNORE INTO msg_email_template_blue_white_backup_20260820
SELECT template.*
FROM msg_email_template template
WHERE template.system_builtin = 1
  AND template.deleted = 0
  AND template.template_code IN (
      'FEE_CONFIG_PENDING_REVIEW', 'FEE_CONFIG_REJECTED', 'FEE_RULE_MISSING',
      'SETTLEMENT_RATE_MISSING', 'NEGATIVE_BALANCE_INTERNAL', 'NEGATIVE_BALANCE_MERCHANT',
      'BALANCE_RESTORED', 'HOLIDAY_CALENDAR_MISSING', 'FUND_RECHARGE_POSTED',
      'FUND_RECHARGE_REJECTED', 'MERCHANT_SOURCE_URL_APPROVED',
      'MERCHANT_SOURCE_URL_REJECTED', 'MERCHANT_IP_WHITELIST_APPROVED',
      'MERCHANT_IP_WHITELIST_REJECTED'
  );

UPDATE msg_email_template template
SET template.content_template = CONCAT(
        '<div data-template-theme="vexra-blue-white-v1" style="margin:0;padding:32px 16px;background:#F3F7FF;font-family:Arial,sans-serif;color:#0F172A;">',
        '<div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;">',
        '<div style="padding:24px 28px;background:#2563EB;color:#FFFFFF;">',
        '<div style="font-size:13px;color:#DBEAFE;">',
        CASE WHEN template.app_code = 'MERCHANT' THEN 'Vexra Merchant' ELSE 'Vexra Admin' END,
        '</div><div style="margin-top:6px;font-size:22px;font-weight:700;">',
        template.template_name,
        '</div></div>',
        '<div style="padding:28px;line-height:1.7;font-size:14px;">',
        '<div style="display:inline-block;margin:0 0 18px;padding:6px 10px;border-radius:4px;font-size:12px;font-weight:700;',
        CASE
            WHEN template.template_code IN (
                'BALANCE_RESTORED', 'FUND_RECHARGE_POSTED',
                'MERCHANT_SOURCE_URL_APPROVED', 'MERCHANT_IP_WHITELIST_APPROVED'
            ) THEN 'background:#ECFDF5;color:#047857;border:1px solid #A7F3D0;'
            WHEN template.template_code IN (
                'FEE_CONFIG_REJECTED', 'FEE_RULE_MISSING', 'SETTLEMENT_RATE_MISSING',
                'NEGATIVE_BALANCE_INTERNAL', 'NEGATIVE_BALANCE_MERCHANT',
                'HOLIDAY_CALENDAR_MISSING', 'FUND_RECHARGE_REJECTED',
                'MERCHANT_SOURCE_URL_REJECTED', 'MERCHANT_IP_WHITELIST_REJECTED'
            ) THEN 'background:#FEF2F2;color:#B91C1C;border:1px solid #FECACA;'
            ELSE 'background:#FFF7ED;color:#C2410C;border:1px solid #FED7AA;'
        END,
        '">',
        CASE
            WHEN template.locale = 'zh-CN' AND template.template_code IN (
                'BALANCE_RESTORED', 'FUND_RECHARGE_POSTED',
                'MERCHANT_SOURCE_URL_APPROVED', 'MERCHANT_IP_WHITELIST_APPROVED'
            ) THEN '处理成功'
            WHEN template.locale = 'zh-CN' AND template.template_code = 'FEE_CONFIG_PENDING_REVIEW'
                THEN '等待审核'
            WHEN template.locale = 'zh-CN' THEN '需要关注'
            WHEN template.template_code IN (
                'BALANCE_RESTORED', 'FUND_RECHARGE_POSTED',
                'MERCHANT_SOURCE_URL_APPROVED', 'MERCHANT_IP_WHITELIST_APPROVED'
            ) THEN 'Completed'
            WHEN template.template_code = 'FEE_CONFIG_PENDING_REVIEW' THEN 'Pending review'
            ELSE 'Action required'
        END,
        '</div>',
        '<div style="padding:18px;background:#F8FAFC;border:1px solid #E2E8F0;border-radius:6px;word-break:break-word;">',
        template.content_template,
        '</div></div>',
        '<div style="padding:16px 28px;background:#F3F7FF;border-top:1px solid #DBEAFE;color:#64748B;font-size:12px;">',
        CASE WHEN template.locale = 'zh-CN'
             THEN '此邮件由系统自动发送，请勿直接回复。'
             ELSE 'This is an automated message. Please do not reply.' END,
        '</div></div></div>'
    ),
    template.version_no = template.version_no + 1,
    template.update_by = 'system',
    template.update_time = CURRENT_TIMESTAMP(3)
WHERE template.system_builtin = 1
  AND template.deleted = 0
  AND template.locale IN ('zh-CN', 'en-US')
  AND template.template_code IN (
      'FEE_CONFIG_PENDING_REVIEW', 'FEE_CONFIG_REJECTED', 'FEE_RULE_MISSING',
      'SETTLEMENT_RATE_MISSING', 'NEGATIVE_BALANCE_INTERNAL', 'NEGATIVE_BALANCE_MERCHANT',
      'BALANCE_RESTORED', 'HOLIDAY_CALENDAR_MISSING', 'FUND_RECHARGE_POSTED',
      'FUND_RECHARGE_REJECTED', 'MERCHANT_SOURCE_URL_APPROVED',
      'MERCHANT_SOURCE_URL_REJECTED', 'MERCHANT_IP_WHITELIST_APPROVED',
      'MERCHANT_IP_WHITELIST_REJECTED'
  )
  AND template.content_template NOT LIKE '%data-template-theme="vexra-blue-white-v1"%';

COMMIT;
