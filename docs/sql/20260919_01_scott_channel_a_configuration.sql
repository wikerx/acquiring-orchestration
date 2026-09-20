-- ScottChannelA 渠道、MID 元数据模板、支付能力和支付方式配置。
--
-- 凭据约束：本脚本不写入具体 MID、API Key 或 Callback Secret。渠道 MID 由 MID 管理页面录入；
-- apiKey、callbackSecret 仅作为敏感元数据字段定义，实际值由管理端保存并在展示时掩码。

SET NAMES utf8mb4;
START TRANSACTION;

INSERT IGNORE INTO sys_dict_type (id, dict_name, dict_type, biz_domain, system_builtin, editable, status, deleted)
VALUES (42, '代付交易类型', 'payout_transaction_type', 'channel', 1, 1, 1, 0);

-- 只补充字典中尚不存在的编码；已有 BANK_CARD、PAYPAL、CASH_APP_PAY、APPLE_PAY、GOOGLE_PAY、ACH_DEBIT 直接复用。
INSERT INTO sys_dict_data (
    dict_type, dict_label, dict_value, locale, dict_sort, list_class,
    extra_json, is_default, status, deleted
)
SELECT 'acquiring_payment_method', '统一支付接口', 'UPI', 'zh-CN', 8, 'primary', NULL, 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'acquiring_payment_method' AND dict_value = 'UPI' AND locale = 'zh-CN' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'acquiring_payment_method', 'Bitcoin', 'BTC_ON_CHAIN', 'zh-CN', 9, 'primary', '{"logoKey":"bitcoin","logoKeys":["bitcoin"]}', 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'acquiring_payment_method' AND dict_value = 'BTC_ON_CHAIN' AND locale = 'zh-CN' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'acquiring_payment_method', 'PYUSD', 'PYUSD', 'zh-CN', 10, 'primary', NULL, 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'acquiring_payment_method' AND dict_value = 'PYUSD' AND locale = 'zh-CN' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'payout_payment_method', '银行卡', 'BANK_CARD', 'zh-CN', 4, 'primary', '{"logoKey":"bankCard","logoKeys":["visa","mastercard","jcb","maestro"]}', 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'payout_payment_method' AND dict_value = 'BANK_CARD' AND locale = 'zh-CN' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'payout_payment_method', 'Google Pay', 'GOOGLE_PAY', 'zh-CN', 5, 'primary', '{"logoKey":"googlePay","logoKeys":["googlePay"]}', 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'payout_payment_method' AND dict_value = 'GOOGLE_PAY' AND locale = 'zh-CN' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'payout_payment_method', 'ACH Debit', 'ACH_DEBIT', 'zh-CN', 6, 'primary', '{"logoKey":"achDebit","logoKeys":["achDebit"]}', 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'payout_payment_method' AND dict_value = 'ACH_DEBIT' AND locale = 'zh-CN' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'payout_payment_method', '统一支付接口', 'UPI', 'zh-CN', 7, 'primary', NULL, 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'payout_payment_method' AND dict_value = 'UPI' AND locale = 'zh-CN' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'payout_payment_method', 'Venmo', 'VENMO', 'zh-CN', 8, 'primary', NULL, 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'payout_payment_method' AND dict_value = 'VENMO' AND locale = 'zh-CN' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'payout_payment_method', 'Bitcoin', 'BTC_ON_CHAIN', 'zh-CN', 9, 'primary', '{"logoKey":"bitcoin","logoKeys":["bitcoin"]}', 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'payout_payment_method' AND dict_value = 'BTC_ON_CHAIN' AND locale = 'zh-CN' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'payout_payment_method', 'PYUSD', 'PYUSD', 'zh-CN', 10, 'primary', NULL, 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'payout_payment_method' AND dict_value = 'PYUSD' AND locale = 'zh-CN' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'acquiring_payment_method', 'UPI', 'UPI', 'en-US', 8, 'primary', NULL, 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'acquiring_payment_method' AND dict_value = 'UPI' AND locale = 'en-US' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'acquiring_payment_method', 'Bitcoin', 'BTC_ON_CHAIN', 'en-US', 9, 'primary', '{"logoKey":"bitcoin","logoKeys":["bitcoin"]}', 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'acquiring_payment_method' AND dict_value = 'BTC_ON_CHAIN' AND locale = 'en-US' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'acquiring_payment_method', 'PYUSD', 'PYUSD', 'en-US', 10, 'primary', NULL, 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'acquiring_payment_method' AND dict_value = 'PYUSD' AND locale = 'en-US' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'payout_payment_method', 'Bank Card', 'BANK_CARD', 'en-US', 4, 'primary', '{"logoKey":"bankCard","logoKeys":["visa","mastercard","jcb","maestro"]}', 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'payout_payment_method' AND dict_value = 'BANK_CARD' AND locale = 'en-US' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'payout_payment_method', 'Google Pay', 'GOOGLE_PAY', 'en-US', 5, 'primary', '{"logoKey":"googlePay","logoKeys":["googlePay"]}', 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'payout_payment_method' AND dict_value = 'GOOGLE_PAY' AND locale = 'en-US' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'payout_payment_method', 'ACH Debit', 'ACH_DEBIT', 'en-US', 6, 'primary', '{"logoKey":"achDebit","logoKeys":["achDebit"]}', 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'payout_payment_method' AND dict_value = 'ACH_DEBIT' AND locale = 'en-US' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'payout_payment_method', 'UPI', 'UPI', 'en-US', 7, 'primary', NULL, 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'payout_payment_method' AND dict_value = 'UPI' AND locale = 'en-US' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'payout_payment_method', 'Venmo', 'VENMO', 'en-US', 8, 'primary', NULL, 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'payout_payment_method' AND dict_value = 'VENMO' AND locale = 'en-US' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'payout_payment_method', 'Bitcoin', 'BTC_ON_CHAIN', 'en-US', 9, 'primary', '{"logoKey":"bitcoin","logoKeys":["bitcoin"]}', 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'payout_payment_method' AND dict_value = 'BTC_ON_CHAIN' AND locale = 'en-US' AND deleted = 0
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'payout_payment_method', 'PYUSD', 'PYUSD', 'en-US', 10, 'primary', NULL, 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'payout_payment_method' AND dict_value = 'PYUSD' AND locale = 'en-US' AND deleted = 0
);

INSERT IGNORE INTO sys_dict_data (
    dict_type, dict_label, dict_value, locale, dict_sort, list_class,
    extra_json, is_default, status, deleted
) VALUES
('payout_transaction_type', '代付', 'PAYOUT', 'zh-CN', 1, 'primary', NULL, 1, 1, 0),
('payout_transaction_type', '取消', 'CANCEL', 'zh-CN', 2, 'warning', NULL, 0, 1, 0),
('payout_transaction_type', 'Payout', 'PAYOUT', 'en-US', 1, 'primary', NULL, 1, 1, 0),
('payout_transaction_type', 'Cancel', 'CANCEL', 'en-US', 2, 'warning', NULL, 0, 1, 0);

-- 统一修正新增支付方式的本地化文案；重复执行时也会覆盖历史字符集错误产生的乱码。
UPDATE sys_dict_data
SET dict_label = CASE
    WHEN dict_type = 'acquiring_payment_method' AND dict_value = 'UPI' AND locale = 'zh-CN' THEN '统一支付接口'
    WHEN dict_type = 'acquiring_payment_method' AND dict_value = 'BTC_ON_CHAIN' AND locale = 'zh-CN' THEN 'Bitcoin'
    WHEN dict_type = 'acquiring_payment_method' AND dict_value = 'PYUSD' AND locale = 'zh-CN' THEN 'PYUSD'
    WHEN dict_type = 'payout_payment_method' AND dict_value = 'BANK_CARD' AND locale = 'zh-CN' THEN '银行卡'
    WHEN dict_type = 'payout_payment_method' AND dict_value = 'GOOGLE_PAY' AND locale = 'zh-CN' THEN 'Google Pay'
    WHEN dict_type = 'payout_payment_method' AND dict_value = 'ACH_DEBIT' AND locale = 'zh-CN' THEN 'ACH Debit'
    WHEN dict_type = 'payout_payment_method' AND dict_value = 'UPI' AND locale = 'zh-CN' THEN '统一支付接口'
    WHEN dict_type = 'payout_payment_method' AND dict_value = 'VENMO' AND locale = 'zh-CN' THEN 'Venmo'
    WHEN dict_type = 'payout_payment_method' AND dict_value = 'BTC_ON_CHAIN' AND locale = 'zh-CN' THEN 'Bitcoin'
    WHEN dict_type = 'payout_payment_method' AND dict_value = 'PYUSD' AND locale = 'zh-CN' THEN 'PYUSD'
    ELSE dict_label
END
WHERE deleted = 0
  AND (
      (dict_type = 'acquiring_payment_method' AND dict_value IN ('UPI', 'BTC_ON_CHAIN', 'PYUSD') AND locale = 'zh-CN')
      OR (dict_type = 'payout_payment_method' AND dict_value IN ('BANK_CARD', 'GOOGLE_PAY', 'ACH_DEBIT', 'UPI', 'VENMO', 'BTC_ON_CHAIN', 'PYUSD') AND locale = 'zh-CN')
  );

UPDATE sys_dict_data
SET extra_json = '{"logoKey":"bitcoin","logoKeys":["bitcoin"]}'
WHERE dict_type IN ('acquiring_payment_method', 'payout_payment_method')
  AND dict_value = 'BTC_ON_CHAIN'
  AND deleted = 0;

INSERT INTO channel_info (
    channel_code, channel_cn_name, channel_en_name, channel_status,
    support_acquiring, support_payout, support_3ds, default_request_url,
    default_interaction_mode, connect_timeout_seconds, read_timeout_seconds,
    sort_order, remark, create_by, update_by, deleted
)
SELECT
    'SCOTT_CHANNEL_A', 'ScottChannelA', 'ScottChannelA', 1,
    1, 1, 0, 'https://scottadmin.zpaymall.com',
    'API_KEY', 15, 15, 900, 'ScottChannelA direct payin and payout provider', 'migration', 'migration', 0
WHERE NOT EXISTS (
    SELECT 1 FROM channel_info WHERE channel_code = 'SCOTT_CHANNEL_A' AND deleted = 0
);

SET @scott_channel_a_id = (
    SELECT id FROM channel_info
    WHERE channel_code = 'SCOTT_CHANNEL_A' AND deleted = 0
    ORDER BY id DESC LIMIT 1
);

-- 渠道请求地址统一使用 channel_info.default_request_url；历史版本若已创建 apiBaseUrl 或 merchantId
-- 模板，软删除这些重复字段。deleted 使用记录 id，保持项目软删除约定。
UPDATE channel_metadata_schema
SET deleted = id,
    field_status = 0,
    update_by = 'migration'
WHERE channel_id = @scott_channel_a_id
  AND channel_code = 'SCOTT_CHANNEL_A'
  AND field_key IN ('apiBaseUrl', 'merchantId')
  AND deleted = 0;

-- 对已有的 ScottChannelA 元数据进行幂等规范化，避免历史配置仍以明文文本类型保存。
UPDATE channel_metadata_schema
SET field_type = 'TEXT',
    sensitive_flag = 0,
    required_flag = 1,
    validation_regex = '^v[0-9]+$',
    placeholder = 'v1',
    default_value = COALESCE(default_value, 'v1'),
    sort_order = 1,
    field_status = 1,
    update_by = 'migration'
WHERE channel_id = @scott_channel_a_id
  AND channel_code = 'SCOTT_CHANNEL_A'
  AND field_key = 'version'
  AND deleted = 0;

UPDATE channel_metadata_schema
SET field_type = 'PASSWORD',
    sensitive_flag = 1,
    required_flag = 1,
    placeholder = '请输入 MID API Key / Enter MID API Key',
    sort_order = 2,
    field_status = 1,
    update_by = 'migration'
WHERE channel_id = @scott_channel_a_id
  AND channel_code = 'SCOTT_CHANNEL_A'
  AND field_key = 'apiKey'
  AND deleted = 0;

UPDATE channel_metadata_schema
SET field_type = 'PASSWORD',
    sensitive_flag = 1,
    required_flag = 1,
    placeholder = '请输入 MID Callback Secret / Enter MID Callback Secret',
    sort_order = 3,
    field_status = 1,
    update_by = 'migration'
WHERE channel_id = @scott_channel_a_id
  AND channel_code = 'SCOTT_CHANNEL_A'
  AND field_key = 'callbackSecret'
  AND deleted = 0;

INSERT INTO channel_metadata_schema (
    channel_id, channel_code, field_key, field_label, field_type,
    required_flag, sensitive_flag, validation_regex, placeholder,
    default_value, sort_order, field_status, create_by, update_by, deleted
)
SELECT @scott_channel_a_id, 'SCOTT_CHANNEL_A', 'version', 'API 版本 / API Version', 'TEXT',
       1, 0, '^v[0-9]+$', 'v1', 'v1', 1, 1, 'migration', 'migration', 0
WHERE NOT EXISTS (
    SELECT 1 FROM channel_metadata_schema
    WHERE channel_id = @scott_channel_a_id AND field_key = 'version' AND deleted = 0
);

INSERT INTO channel_metadata_schema (
    channel_id, channel_code, field_key, field_label, field_type,
    required_flag, sensitive_flag, validation_regex, placeholder,
    default_value, sort_order, field_status, create_by, update_by, deleted
)
SELECT @scott_channel_a_id, 'SCOTT_CHANNEL_A', 'apiKey', 'API Key', 'PASSWORD',
       1, 1, NULL, '请输入 MID API Key / Enter MID API Key', NULL, 2, 1, 'migration', 'migration', 0
WHERE NOT EXISTS (
    SELECT 1 FROM channel_metadata_schema
    WHERE channel_id = @scott_channel_a_id AND field_key = 'apiKey' AND deleted = 0
);

INSERT INTO channel_metadata_schema (
    channel_id, channel_code, field_key, field_label, field_type,
    required_flag, sensitive_flag, validation_regex, placeholder,
    default_value, sort_order, field_status, create_by, update_by, deleted
)
SELECT @scott_channel_a_id, 'SCOTT_CHANNEL_A', 'callbackSecret', '回调签名密钥 / Callback Secret', 'PASSWORD',
       1, 1, NULL, '请输入 MID Callback Secret / Enter MID Callback Secret', NULL, 3, 1, 'migration', 'migration', 0
WHERE NOT EXISTS (
    SELECT 1 FROM channel_metadata_schema
    WHERE channel_id = @scott_channel_a_id AND field_key = 'callbackSecret' AND deleted = 0
);

-- 收单：平台已存在的支付方式复用原编码；新增的 BTC_ON_CHAIN、PYUSD、UPI 已在基础字典中补齐。
INSERT INTO channel_payment_capability (
    channel_id, channel_code, business_type, payment_method, transaction_type,
    default_transaction_currency, support_3ds, support_incremental_authorization,
    capability_status, sort_order, remark, create_by, update_by, deleted
)
SELECT @scott_channel_a_id, 'SCOTT_CHANNEL_A', 'ACQUIRING', methods.payment_method,
       'PAYMENT,REFUND,QUERY', 'USD', 0, 0, 1, methods.sort_order,
       'ScottChannelA direct payin; checkout uses the platform hosted flow', 'migration', 'migration', 0
FROM (
    SELECT 'BANK_CARD' payment_method, 1 sort_order UNION ALL
    SELECT 'PAYPAL', 2 UNION ALL
    SELECT 'APPLE_PAY', 3 UNION ALL
    SELECT 'GOOGLE_PAY', 4 UNION ALL
    SELECT 'CASH_APP_PAY', 5 UNION ALL
    SELECT 'ACH_DEBIT', 6 UNION ALL
    SELECT 'UPI', 7 UNION ALL
    SELECT 'BTC_ON_CHAIN', 8 UNION ALL
    SELECT 'PYUSD', 9
) methods
WHERE NOT EXISTS (
    SELECT 1 FROM channel_payment_capability existing
    WHERE existing.channel_id = @scott_channel_a_id
      AND existing.business_type = 'ACQUIRING'
      AND existing.payment_method = methods.payment_method
      AND existing.deleted = 0
);

-- 代付：覆盖当前 MID 已开启的 GOOGLE_PAY 以及渠道文档列出的 UPI、CASHAPP、PAY_PAL、ACH、VENMO、CARD、Bitcoin、PYUSD；动作类型为代付和取消。
INSERT INTO channel_payment_capability (
    channel_id, channel_code, business_type, payment_method, transaction_type,
    default_transaction_currency, support_3ds, support_incremental_authorization,
    capability_status, sort_order, remark, create_by, update_by, deleted
)
SELECT @scott_channel_a_id, 'SCOTT_CHANNEL_A', 'PAYOUT', methods.payment_method,
       'PAYOUT,CANCEL', 'USD', 0, 0, 1, methods.sort_order,
       'ScottChannelA submit/query/cancel payout provider', 'migration', 'migration', 0
FROM (
    SELECT 'UPI' payment_method, 1 sort_order UNION ALL
    SELECT 'CASH_APP_PAY', 2 UNION ALL
    SELECT 'PAYPAL', 3 UNION ALL
    SELECT 'ACH_DEBIT', 4 UNION ALL
    SELECT 'VENMO', 5 UNION ALL
    SELECT 'BANK_CARD', 6 UNION ALL
    SELECT 'BTC_ON_CHAIN', 7 UNION ALL
    SELECT 'PYUSD', 8 UNION ALL
    SELECT 'GOOGLE_PAY', 9
) methods
WHERE NOT EXISTS (
    SELECT 1 FROM channel_payment_capability existing
    WHERE existing.channel_id = @scott_channel_a_id
      AND existing.business_type = 'PAYOUT'
      AND existing.payment_method = methods.payment_method
      AND existing.deleted = 0
);

UPDATE channel_payment_capability
SET transaction_type = 'PAYOUT,CANCEL',
    update_by = 'migration'
WHERE channel_id = @scott_channel_a_id
  AND channel_code = 'SCOTT_CHANNEL_A'
  AND business_type = 'PAYOUT'
  AND transaction_type IN ('', 'NONE');

-- 当前路由模型要求每项能力至少有一个明确币种；MID 后续可按实际渠道支持范围追加币种。
INSERT INTO channel_capability_currency (
    capability_id, channel_id, channel_code, currency_code,
    currency_status, create_by, update_by, deleted
)
SELECT capability.id, @scott_channel_a_id, 'SCOTT_CHANNEL_A', 'USD', 1, 'migration', 'migration', 0
FROM channel_payment_capability capability
WHERE capability.channel_id = @scott_channel_a_id
  AND capability.channel_code = 'SCOTT_CHANNEL_A'
  AND capability.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM channel_capability_currency currency
      WHERE currency.capability_id = capability.id
        AND currency.currency_code = 'USD'
        AND currency.deleted = 0
  );

-- ScottChannelA 的直连 API 接受 ISO 币种代码；EUR 必须作为直连能力币种保存，
-- 否则路由会回退到 USD 并要求平台执行 EUR->USD 换汇。
INSERT INTO channel_capability_currency (
    capability_id, channel_id, channel_code, currency_code,
    currency_status, create_by, update_by, deleted
)
SELECT capability.id, @scott_channel_a_id, 'SCOTT_CHANNEL_A', 'EUR', 1, 'migration', 'migration', 0
FROM channel_payment_capability capability
WHERE capability.channel_id = @scott_channel_a_id
  AND capability.channel_code = 'SCOTT_CHANNEL_A'
  AND capability.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM channel_capability_currency currency
      WHERE currency.capability_id = capability.id
        AND currency.currency_code = 'EUR'
        AND currency.deleted = 0
  );

-- 银行卡能力必须显式限定卡品牌，避免路由把未配置品牌的卡请求送到渠道。
INSERT INTO channel_capability_card_brand (
    capability_id, channel_id, channel_code, card_brand,
    brand_status, sort_order, create_by, update_by, deleted
)
SELECT capability.id, @scott_channel_a_id, 'SCOTT_CHANNEL_A', brands.card_brand,
       1, brands.sort_order, 'migration', 'migration', 0
FROM channel_payment_capability capability
JOIN (
    SELECT 'VISA' card_brand, 1 sort_order UNION ALL
    SELECT 'MASTERCARD', 2 UNION ALL
    SELECT 'JCB', 3 UNION ALL
    SELECT 'MAESTRO', 4 UNION ALL
    SELECT 'AMEX', 5 UNION ALL
    SELECT 'DINERS_CLUB', 6 UNION ALL
    SELECT 'DISCOVER', 7 UNION ALL
    SELECT 'UNIONPAY', 8
) brands ON capability.payment_method = 'BANK_CARD'
WHERE capability.channel_id = @scott_channel_a_id
  AND capability.channel_code = 'SCOTT_CHANNEL_A'
  AND capability.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM channel_capability_card_brand existing
      WHERE existing.capability_id = capability.id
        AND existing.card_brand = brands.card_brand
        AND existing.deleted = 0
  );

COMMIT;
