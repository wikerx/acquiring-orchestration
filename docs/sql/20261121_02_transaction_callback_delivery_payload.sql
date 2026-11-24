-- 在 Payment、OpenAPI 和 Admin 使用逐次脱敏正文前执行。历史投递保留空值。
ALTER TABLE transaction_callback_delivery
    ADD COLUMN request_body_masked json DEFAULT NULL COMMENT '本次投递已验签且按渠道原字段名提取的脱敏请求正文';
