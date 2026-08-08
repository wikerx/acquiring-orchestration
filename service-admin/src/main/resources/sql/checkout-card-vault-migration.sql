-- 收银台卡资料库季度分表模板。
-- 本表不存储 CVV；PAN 查询标识使用 secret pepper 的 HMAC-SHA256，DEK 只以 KEK 包裹密文保存。
-- 执行顺序：创建模板表 -> 预建并校验所有已发布季度物理表 -> 发布 24 表分片规则 -> 开启生产和消费开关。

CREATE TABLE IF NOT EXISTS `transaction_card_vault` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分片内自增主键',
    `vault_record_id` VARCHAR(64) NOT NULL COMMENT '卡资料库记录号',
    `message_id` VARCHAR(64) NOT NULL COMMENT 'MQ 消息号',
    `merchant_id` VARCHAR(64) NOT NULL COMMENT '商户号',
    `checkout_attempt_id` VARCHAR(64) NOT NULL COMMENT '收银台支付尝试号',
    `transaction_id` VARCHAR(64) NOT NULL COMMENT '平台交易号',
    `transaction_date_time` DATETIME(3) NOT NULL COMMENT '交易时间及季度分片键',
    `card_brand` VARCHAR(32) NOT NULL COMMENT '卡品牌',
    `card_bin` VARCHAR(8) NOT NULL COMMENT '卡号前六位 BIN',
    `card_last4` CHAR(4) NOT NULL COMMENT '卡号后四位',
    `pan_hmac` CHAR(64) NOT NULL COMMENT '带 secret pepper 的 PAN HMAC-SHA256',
    `pan_hmac_key_version` VARCHAR(32) NOT NULL COMMENT 'PAN HMAC 密钥版本',
    `pan_ciphertext` VARCHAR(128) NOT NULL COMMENT 'PAN AES-256-GCM 密文',
    `pan_iv` VARCHAR(32) NOT NULL COMMENT 'PAN AES-GCM IV',
    `pan_auth_tag` VARCHAR(32) NOT NULL COMMENT 'PAN AES-GCM 认证标签',
    `expiration_ciphertext` VARCHAR(64) NOT NULL COMMENT '有效期 AES-256-GCM 密文',
    `expiration_iv` VARCHAR(32) NOT NULL COMMENT '有效期 AES-GCM IV',
    `expiration_auth_tag` VARCHAR(32) NOT NULL COMMENT '有效期 AES-GCM 认证标签',
    `cardholder_name_ciphertext` VARCHAR(512) NULL COMMENT '持卡人姓名 AES-256-GCM 密文',
    `cardholder_name_iv` VARCHAR(32) NULL COMMENT '持卡人姓名 AES-GCM IV',
    `cardholder_name_auth_tag` VARCHAR(32) NULL COMMENT '持卡人姓名 AES-GCM 认证标签',
    `wrapped_dek_ciphertext` VARCHAR(128) NOT NULL COMMENT 'KEK 包裹后的随机 DEK 密文',
    `wrapped_dek_iv` VARCHAR(32) NOT NULL COMMENT '包裹 DEK 使用的 AES-GCM IV',
    `wrapped_dek_auth_tag` VARCHAR(32) NOT NULL COMMENT '包裹 DEK 使用的 AES-GCM 认证标签',
    `kek_version` VARCHAR(32) NOT NULL COMMENT 'KEK 版本',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_card_vault_record` (`vault_record_id`, `transaction_date_time`),
    UNIQUE KEY `uk_card_vault_message` (`message_id`, `transaction_date_time`),
    UNIQUE KEY `uk_card_vault_transaction` (`merchant_id`, `transaction_id`, `transaction_date_time`),
    KEY `idx_card_vault_pan_hmac` (`merchant_id`, `pan_hmac`, `transaction_date_time`),
    KEY `idx_card_vault_attempt` (`checkout_attempt_id`, `transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收银台卡资料加密分表模板';

-- 当前已发布分片节点；后续季度由分片治理任务基于模板表预建。
CREATE TABLE IF NOT EXISTS `transaction_card_vault_202603` LIKE `transaction_card_vault`;
ALTER TABLE `transaction_card_vault_202603` AUTO_INCREMENT = 202603000000000001;

CREATE TABLE IF NOT EXISTS `transaction_card_vault_202604` LIKE `transaction_card_vault`;
ALTER TABLE `transaction_card_vault_202604` AUTO_INCREMENT = 202604000000000001;
