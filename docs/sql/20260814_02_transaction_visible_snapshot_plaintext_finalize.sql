-- 交易附属信息明文化迁移第二阶段。
-- 执行前提：第一阶段已完成，历史 Hosted Checkout payer 密文已解密写入 payer_info_json。
USE `payment_acquiring`;

DELIMITER $$
DROP PROCEDURE IF EXISTS `assert_visible_snapshot_plaintext_ready`$$
CREATE PROCEDURE `assert_visible_snapshot_plaintext_ready`()
BEGIN
  IF EXISTS (
      SELECT 1
        FROM `payment_checkout_session`
       WHERE `payer_info_ciphertext` IS NOT NULL
         AND `payer_info_json` IS NULL
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'payer_info_json backfill is incomplete';
  END IF;

  IF EXISTS (
      SELECT 1
        FROM `payment_checkout_session`
       WHERE `billing_info_ciphertext` IS NOT NULL
         AND `billing_info_json` IS NULL
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'billing_info_json backfill is incomplete';
  END IF;

  IF (SELECT COUNT(*) FROM `transaction_merchant_snapshot`) <> 0
      OR (SELECT COUNT(*) FROM `transaction_merchant_snapshot_202603`) <> 0
      OR (SELECT COUNT(*) FROM `transaction_merchant_snapshot_202604`) <> 0
      OR (SELECT COUNT(*) FROM `transaction_payer_info`) <> 0
      OR (SELECT COUNT(*) FROM `transaction_payer_info_202603`) <> 0
      OR (SELECT COUNT(*) FROM `transaction_payer_info_202604`) <> 0
      OR (SELECT COUNT(*) FROM `transaction_billing_info`) <> 0
      OR (SELECT COUNT(*) FROM `transaction_billing_info_202603`) <> 0
      OR (SELECT COUNT(*) FROM `transaction_billing_info_202604`) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'transaction snapshot tables are not empty; use an online copy migration';
  END IF;
END$$
DELIMITER ;

CALL `assert_visible_snapshot_plaintext_ready`();
DROP PROCEDURE `assert_visible_snapshot_plaintext_ready`;

UPDATE `payment_checkout_session`
   SET `payer_email` = JSON_UNQUOTE(JSON_EXTRACT(`payer_info_json`, '$.email'))
 WHERE `payer_info_json` IS NOT NULL
   AND JSON_EXTRACT(`payer_info_json`, '$.email') IS NOT NULL;

ALTER TABLE `payment_checkout_session`
  DROP COLUMN `merchant_return_url`,
  DROP COLUMN `merchant_cancel_url`,
  DROP COLUMN `payer_info_ciphertext`,
  DROP COLUMN `billing_info_ciphertext`,
  DROP COLUMN `payer_email_masked`;

DROP TABLE `transaction_merchant_snapshot_202604`;
DROP TABLE `transaction_merchant_snapshot_202603`;
DROP TABLE `transaction_merchant_snapshot`;

CREATE TABLE `transaction_merchant_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `snapshot_id` varchar(64) NOT NULL COMMENT '交易商户快照ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID；订单级快照可为空。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `sub_merchant_info_json` json DEFAULT NULL COMMENT '商户上送的完整子商户信息明文 JSON；未上送时为空。',
  `merchant_name` varchar(256) DEFAULT NULL COMMENT '交易发生时商户名称快照。',
  `merchant_country` varchar(3) DEFAULT NULL COMMENT '商户国家/地区。',
  `merchant_category_code` varchar(16) DEFAULT NULL COMMENT 'MCC。',
  `merchant_status` varchar(32) DEFAULT NULL COMMENT '交易发生时商户状态。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `channel_merchant_id` varchar(128) DEFAULT NULL COMMENT '渠道真实MID。',
  `terminal_id` varchar(128) DEFAULT NULL COMMENT '终端号。',
  `channel_mid_metadata_json` json DEFAULT NULL COMMENT '交易使用的MID核心元数据快照。',
  `settlement_config_snapshot_json` json DEFAULT NULL COMMENT '结算配置快照。',
  `fee_config_snapshot_json` json DEFAULT NULL COMMENT '费率配置快照。',
  `internal_risk_config_snapshot_json` json DEFAULT NULL COMMENT '内风控配置快照。',
  `route_config_snapshot_json` json DEFAULT NULL COMMENT '路由决策配置快照。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_snapshot_id` (`snapshot_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_merchant_time` (`merchant_id`,`transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易商户、渠道、费率、结算和风控配置快照。';
CREATE TABLE `transaction_merchant_snapshot_202603` LIKE `transaction_merchant_snapshot`;
CREATE TABLE `transaction_merchant_snapshot_202604` LIKE `transaction_merchant_snapshot`;
ALTER TABLE `transaction_merchant_snapshot_202603` AUTO_INCREMENT = 202603000000000001;
ALTER TABLE `transaction_merchant_snapshot_202604` AUTO_INCREMENT = 202604000000000001;

DROP TABLE `transaction_payer_info_202604`;
DROP TABLE `transaction_payer_info_202603`;
DROP TABLE `transaction_payer_info`;

CREATE TABLE `transaction_payer_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `payer_info_id` varchar(64) NOT NULL COMMENT '付款人信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `payer_id` varchar(64) DEFAULT NULL COMMENT '商户侧付款人ID或客户ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '付款人名。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '付款人姓。',
  `phone` varchar(32) DEFAULT NULL COMMENT '付款人电话明文。',
  `email` varchar(64) DEFAULT NULL COMMENT '付款人邮箱明文。',
  `country` varchar(3) DEFAULT NULL COMMENT '付款人国家/地区。',
  `state` varchar(64) DEFAULT NULL COMMENT '付款人州、省或地区。',
  `city` varchar(64) DEFAULT NULL COMMENT '付款人城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '付款人街道地址。',
  `postal` varchar(32) DEFAULT NULL COMMENT '付款人邮编。',
  `ip_address` varchar(64) NOT NULL COMMENT '付款人客户端 IP 明文。',
  `session_id` varchar(128) DEFAULT NULL COMMENT '付款会话 ID 明文。',
  `browser_info_json` json DEFAULT NULL COMMENT '浏览器信息明文 JSON。',
  `user_agent` varchar(512) DEFAULT NULL COMMENT '付款人 User-Agent 明文。',
  `payer_email_hash` char(64) DEFAULT NULL COMMENT '付款人邮箱哈希。',
  `payer_phone_hash` char(64) DEFAULT NULL COMMENT '付款人手机号哈希。',
  `ip_address_hash` char(64) DEFAULT NULL COMMENT '付款人 IP 地址哈希。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payer_info_id` (`payer_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_country_time` (`country`,`transaction_date_time`),
  KEY `idx_email_hash` (`payer_email_hash`,`transaction_date_time`),
  KEY `idx_phone_hash` (`payer_phone_hash`,`transaction_date_time`),
  KEY `idx_ip_hash` (`ip_address_hash`,`transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款人明文交易快照，供风控、审计和商户查询回显使用。';
CREATE TABLE `transaction_payer_info_202603` LIKE `transaction_payer_info`;
CREATE TABLE `transaction_payer_info_202604` LIKE `transaction_payer_info`;
ALTER TABLE `transaction_payer_info_202603` AUTO_INCREMENT = 202603000000000001;
ALTER TABLE `transaction_payer_info_202604` AUTO_INCREMENT = 202604000000000001;

DROP TABLE `transaction_billing_info_202604`;
DROP TABLE `transaction_billing_info_202603`;
DROP TABLE `transaction_billing_info`;

CREATE TABLE `transaction_billing_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `billing_info_id` varchar(64) NOT NULL COMMENT '账单信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '持卡人名。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '持卡人姓。',
  `email` varchar(64) DEFAULT NULL COMMENT '持卡人邮箱明文。',
  `phone` varchar(32) DEFAULT NULL COMMENT '持卡人电话明文。',
  `billing_country` varchar(3) DEFAULT NULL COMMENT '账单国家/地区。',
  `billing_state` varchar(64) DEFAULT NULL COMMENT '账单州/省。',
  `billing_city` varchar(64) DEFAULT NULL COMMENT '账单城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '持卡人账单街道。',
  `billing_postal_code` varchar(32) DEFAULT NULL COMMENT '账单邮编。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_info_id` (`billing_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_country_time` (`billing_country`,`transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='持卡人账单信息明文交易快照。';
CREATE TABLE `transaction_billing_info_202603` LIKE `transaction_billing_info`;
CREATE TABLE `transaction_billing_info_202604` LIKE `transaction_billing_info`;
ALTER TABLE `transaction_billing_info_202603` AUTO_INCREMENT = 202603000000000001;
ALTER TABLE `transaction_billing_info_202604` AUTO_INCREMENT = 202604000000000001;

CREATE TABLE `transaction_shipping_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `shipping_info_id` varchar(64) NOT NULL COMMENT '收货信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '收货人名。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '收货人姓。',
  `email` varchar(64) DEFAULT NULL COMMENT '收货人邮箱明文。',
  `phone` varchar(32) DEFAULT NULL COMMENT '收货人电话明文。',
  `country` varchar(3) DEFAULT NULL COMMENT '收货国家/地区。',
  `state` varchar(64) DEFAULT NULL COMMENT '收货州、省或地区。',
  `city` varchar(64) DEFAULT NULL COMMENT '收货城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '收货街道。',
  `postal` varchar(32) DEFAULT NULL COMMENT '收货邮编。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipping_info_id` (`shipping_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_country_time` (`country`,`transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货人身份、联系方式和地址明文交易快照。';
CREATE TABLE `transaction_shipping_info_202603` LIKE `transaction_shipping_info`;
CREATE TABLE `transaction_shipping_info_202604` LIKE `transaction_shipping_info`;
ALTER TABLE `transaction_shipping_info_202603` AUTO_INCREMENT = 202603000000000001;
ALTER TABLE `transaction_shipping_info_202604` AUTO_INCREMENT = 202604000000000001;
