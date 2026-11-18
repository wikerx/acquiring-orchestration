-- 支付链接邀请发送请求与模板；不修改任何已有自建模板。

SET NAMES utf8mb4;

-- 复用邮件管理的场景字典；两端邀请、模板及发送记录使用同一业务值，中英文独立展示。
INSERT INTO sys_dict_data
(dict_type,dict_label,dict_value,locale,dict_sort,is_default,status,remark,created_by,updated_by,deleted)
SELECT 'email_scene_code','支付链接付款邀请','PAYMENT_LINK','zh-CN',80,0,1,'收单支付链接邀请场景','system','system',0
WHERE EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='email_scene_code' AND deleted=0)
  AND NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='email_scene_code' AND dict_value='PAYMENT_LINK' AND locale='zh-CN' AND deleted=0);

INSERT INTO sys_dict_data
(dict_type,dict_label,dict_value,locale,dict_sort,is_default,status,remark,created_by,updated_by,deleted)
SELECT 'email_scene_code','Payment link invitation','PAYMENT_LINK','en-US',80,0,1,'收单支付链接邀请场景','system','system',0
WHERE EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='email_scene_code' AND deleted=0)
  AND NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='email_scene_code' AND dict_value='PAYMENT_LINK' AND locale='en-US' AND deleted=0);

CREATE TABLE IF NOT EXISTS admin_payment_link_delivery_request (
 id BIGINT NOT NULL AUTO_INCREMENT,
 request_id VARCHAR(80) NOT NULL,
 merchant_id VARCHAR(64) NOT NULL,
 link_id VARCHAR(32) NOT NULL,
 fingerprint CHAR(64) NOT NULL,
 recipient_hash CHAR(64) NOT NULL,
 recipient_masked VARCHAR(260) NOT NULL,
 locale VARCHAR(12) NOT NULL,
 biz_no CHAR(64) NOT NULL,
 email_record_id BIGINT NULL,
 created_by VARCHAR(128) NOT NULL,
 create_time DATETIME(3) NOT NULL,
 PRIMARY KEY (id),
 UNIQUE KEY uk_admin_link_send_request (merchant_id,request_id),
 UNIQUE KEY uk_admin_link_send_business (biz_no),
 KEY idx_admin_link_send_page (merchant_id,link_id,create_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付邀请幂等请求，不保存客户邮箱或链接令牌原文';

CREATE TABLE IF NOT EXISTS merchant_payment_link_delivery_request (
 id BIGINT NOT NULL AUTO_INCREMENT,
 request_id VARCHAR(80) NOT NULL,
 merchant_id VARCHAR(64) NOT NULL,
 link_id VARCHAR(32) NOT NULL,
 fingerprint CHAR(64) NOT NULL,
 recipient_hash CHAR(64) NOT NULL,
 recipient_masked VARCHAR(260) NOT NULL,
 locale VARCHAR(12) NOT NULL,
 biz_no CHAR(64) NOT NULL,
 email_record_id BIGINT NULL,
 created_by VARCHAR(128) NOT NULL,
 create_time DATETIME(3) NOT NULL,
 PRIMARY KEY (id),
 UNIQUE KEY uk_merchant_link_send_request (merchant_id,request_id),
 UNIQUE KEY uk_merchant_link_send_business (biz_no),
 KEY idx_merchant_link_send_page (merchant_id,link_id,create_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付邀请幂等请求，不保存客户邮箱或链接令牌原文';

INSERT INTO msg_email_template
(template_code,template_name,app_code,scene_code,locale,subject_template,content_type,content_template,variable_schema,sensitive_variable_names,status,system_builtin,version_no,remark,create_by,update_by,deleted)
SELECT 'PAYMENT_LINK_INVITATION', '支付链接付款邀请', 'COMMON', 'PAYMENT_LINK', 'zh-CN', '${merchantName} · 付款邀请', 'HTML', '<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>付款邀请</title></head>
<body style="margin:0;padding:0;background:#f3f7ff;color:#0f172a;font-family:Arial,Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;">
<table role="presentation" style="width:100%;border-collapse:collapse;background:#f3f7ff;"><tr><td style="padding:28px 12px;">
<table role="presentation" align="center" style="width:100%;max-width:620px;border-collapse:collapse;background:#fff;border:1px solid #dbeafe;">
<tr><td style="padding:24px 28px;background:#2563eb;color:#fff;"><p style="margin:0;font-size:14px;letter-spacing:1px;">VEXRA · PAY BY LINK</p><h1 style="margin:10px 0 0;font-size:25px;line-height:1.3;">付款邀请</h1></td></tr>
<tr><td style="padding:28px 28px 14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:bold;word-break:break-word;">${merchantName}</p><p style="margin:0;color:#64748b;line-height:1.6;">请通过安全收银台完成以下订单的付款。</p></td></tr>
<tr><td style="padding:12px 28px;"><table role="presentation" style="width:100%;border-collapse:collapse;background:#eff6ff;"><tr><td style="padding:20px;"><p style="margin:0 0 8px;color:#475569;word-break:break-word;">${linkName}</p><p style="margin:0;color:#1d4ed8;font-size:32px;font-weight:bold;line-height:1.25;">${currency} ${amount}</p></td></tr></table></td></tr>
<tr><td style="padding:8px 28px;"><p style="margin:0;font-size:14px;color:#64748b;">商户订单号</p><p style="margin:5px 0 14px;word-break:break-all;font-size:15px;">${orderNo}</p><p style="margin:0;color:#475569;line-height:1.6;word-break:break-word;">${description}</p></td></tr>
<tr><td style="padding:12px 28px 20px;"><p style="margin:0;font-size:13px;color:#64748b;">有效截止时间</p><p style="margin:6px 0 0;font-size:14px;">${expireTime}</p></td></tr>
<tr><td style="padding:0 28px 24px;"><table role="presentation" style="width:100%;border-collapse:collapse;"><tr><td align="center" style="background:#2563eb;padding:15px 16px;"><a href="${paymentUrl}" style="display:block;color:#fff;text-decoration:none;font-size:17px;font-weight:bold;line-height:1.5;">前往支付</a></td></tr></table></td></tr>
<tr><td style="padding:0 28px 24px;"><p style="margin:0 0 8px;font-size:12px;color:#64748b;line-height:1.5;">如果按钮无法打开，请复制以下链接到浏览器：</p><p style="margin:0;font-size:12px;word-break:break-all;line-height:1.6;"><a href="${paymentUrl}" style="color:#2563eb;">${paymentUrl}</a></p></td></tr>
<tr><td style="padding:20px 28px;border-top:1px solid #e2e8f0;background:#f8fafc;"><p style="margin:0;color:#64748b;font-size:12px;line-height:1.7;">此邮件为付款邀请，不表示已经扣款。请核对商户与金额；已付款时请勿重复支付。</p><p style="margin:12px 0 0;color:#94a3b8;font-size:11px;">由 Vexra 提供托管支付服务</p></td></tr>
</table></td></tr></table></body></html>', '{"merchantName":"示例商户","linkName":"订单付款邀请","currency":"USD","amount":"99.00","orderNo":"DEMO-PBL-001","description":"仅用于邮件样式预览，不能用于付款","expireTime":"2030-01-01 23:59:59 (UTC+08:00)","paymentUrl":"https://pay.example.invalid/l/demo"}', '["paymentUrl"]', 1,1,1,'固定金额单次成功收单邀请，URL 为敏感变量','system','system',0
WHERE NOT EXISTS (SELECT 1 FROM msg_email_template WHERE template_code='PAYMENT_LINK_INVITATION' AND locale='zh-CN' AND deleted=0);

INSERT INTO msg_email_template
(template_code,template_name,app_code,scene_code,locale,subject_template,content_type,content_template,variable_schema,sensitive_variable_names,status,system_builtin,version_no,remark,create_by,update_by,deleted)
SELECT 'PAYMENT_LINK_INVITATION', 'Payment link invitation', 'COMMON', 'PAYMENT_LINK', 'en-US', '${merchantName} · Payment invitation', 'HTML', '<!doctype html>
<html lang="en-US"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Payment invitation</title></head>
<body style="margin:0;padding:0;background:#f3f7ff;color:#0f172a;font-family:Arial,Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif;">
<table role="presentation" style="width:100%;border-collapse:collapse;background:#f3f7ff;"><tr><td style="padding:28px 12px;">
<table role="presentation" align="center" style="width:100%;max-width:620px;border-collapse:collapse;background:#fff;border:1px solid #dbeafe;">
<tr><td style="padding:24px 28px;background:#2563eb;color:#fff;"><p style="margin:0;font-size:14px;letter-spacing:1px;">VEXRA · PAY BY LINK</p><h1 style="margin:10px 0 0;font-size:25px;line-height:1.3;">Payment invitation</h1></td></tr>
<tr><td style="padding:28px 28px 14px;"><p style="margin:0 0 8px;font-size:18px;font-weight:bold;word-break:break-word;">${merchantName}</p><p style="margin:0;color:#64748b;line-height:1.6;">Complete your order through our secure checkout.</p></td></tr>
<tr><td style="padding:12px 28px;"><table role="presentation" style="width:100%;border-collapse:collapse;background:#eff6ff;"><tr><td style="padding:20px;"><p style="margin:0 0 8px;color:#475569;word-break:break-word;">${linkName}</p><p style="margin:0;color:#1d4ed8;font-size:32px;font-weight:bold;line-height:1.25;">${currency} ${amount}</p></td></tr></table></td></tr>
<tr><td style="padding:8px 28px;"><p style="margin:0;font-size:14px;color:#64748b;">Order reference</p><p style="margin:5px 0 14px;word-break:break-all;font-size:15px;">${orderNo}</p><p style="margin:0;color:#475569;line-height:1.6;word-break:break-word;">${description}</p></td></tr>
<tr><td style="padding:12px 28px 20px;"><p style="margin:0;font-size:13px;color:#64748b;">Pay before</p><p style="margin:6px 0 0;font-size:14px;">${expireTime}</p></td></tr>
<tr><td style="padding:0 28px 24px;"><table role="presentation" style="width:100%;border-collapse:collapse;"><tr><td align="center" style="background:#2563eb;padding:15px 16px;"><a href="${paymentUrl}" style="display:block;color:#fff;text-decoration:none;font-size:17px;font-weight:bold;line-height:1.5;">Pay now</a></td></tr></table></td></tr>
<tr><td style="padding:0 28px 24px;"><p style="margin:0 0 8px;font-size:12px;color:#64748b;line-height:1.5;">If the button does not work, copy this link into your browser:</p><p style="margin:0;font-size:12px;word-break:break-all;line-height:1.6;"><a href="${paymentUrl}" style="color:#2563eb;">${paymentUrl}</a></p></td></tr>
<tr><td style="padding:20px 28px;border-top:1px solid #e2e8f0;background:#f8fafc;"><p style="margin:0;color:#64748b;font-size:12px;line-height:1.7;">This invitation does not mean a charge has been made. Check the merchant and amount. Do not pay again if your payment is pending or complete.</p><p style="margin:12px 0 0;color:#94a3b8;font-size:11px;">Hosted payment by Vexra</p></td></tr>
</table></td></tr></table></body></html>', '{"merchantName":"Demo Merchant","linkName":"Order payment invitation","currency":"USD","amount":"99.00","orderNo":"DEMO-PBL-001","description":"Preview only; this is not a payable link.","expireTime":"2030-01-01 23:59:59 (UTC+08:00)","paymentUrl":"https://pay.example.invalid/l/demo"}', '["paymentUrl"]', 1,1,1,'固定金额单次成功收单邀请，URL 为敏感变量','system','system',0
WHERE NOT EXISTS (SELECT 1 FROM msg_email_template WHERE template_code='PAYMENT_LINK_INVITATION' AND locale='en-US' AND deleted=0);

-- 仅修复本功能 v1 内置模板曾写入的数组变量示例；不修改正文或自定义模板。
UPDATE msg_email_template SET variable_schema='{"merchantName":"示例商户","linkName":"订单付款邀请","currency":"USD","amount":"99.00","orderNo":"DEMO-PBL-001","description":"仅用于邮件样式预览，不能用于付款","expireTime":"2030-01-01 23:59:59 (UTC+08:00)","paymentUrl":"https://pay.example.invalid/l/demo"}'
WHERE template_code='PAYMENT_LINK_INVITATION' AND locale='zh-CN'
  AND system_builtin=1 AND version_no=1 AND deleted=0 AND JSON_TYPE(variable_schema)='ARRAY';

UPDATE msg_email_template SET variable_schema='{"merchantName":"Demo Merchant","linkName":"Order payment invitation","currency":"USD","amount":"99.00","orderNo":"DEMO-PBL-001","description":"Preview only; this is not a payable link.","expireTime":"2030-01-01 23:59:59 (UTC+08:00)","paymentUrl":"https://pay.example.invalid/l/demo"}'
WHERE template_code='PAYMENT_LINK_INVITATION' AND locale='en-US'
  AND system_builtin=1 AND version_no=1 AND deleted=0 AND JSON_TYPE(variable_schema)='ARRAY';
