-- Hosted Checkout 明文快照迁移第一阶段。
-- 先增加新列，历史 payer 密文必须在第二阶段前通过应用同版本 SensitiveFieldCipher 完成回填。
USE `payment_acquiring`;

ALTER TABLE `payment_checkout_session`
  ADD COLUMN `sub_merchant_info_json` json DEFAULT NULL COMMENT '子商户完整明文 JSON 快照。' AFTER `merchant_notify_url_ciphertext`,
  ADD COLUMN `payer_info_json` json DEFAULT NULL COMMENT '付款人预填信息明文 JSON 快照。' AFTER `sub_merchant_info_json`,
  ADD COLUMN `billing_info_json` json DEFAULT NULL COMMENT '持卡人账单预填信息明文 JSON 快照。' AFTER `payer_info_json`,
  ADD COLUMN `shipping_info_json` json DEFAULT NULL COMMENT '收货信息结构化明文 JSON。' AFTER `billing_info_json`,
  ADD COLUMN `redirect_url_hash` char(64) DEFAULT NULL COMMENT '结果页返回地址 SHA-256。' AFTER `shipping_info_json`,
  ADD COLUMN `redirect_url_ciphertext` text DEFAULT NULL COMMENT '结果页返回地址 AES-GCM 密文。' AFTER `redirect_url_hash`,
  ADD COLUMN `redirect_url_encryption_key_version` varchar(64) DEFAULT NULL COMMENT '结果页返回地址密钥版本。' AFTER `redirect_url_ciphertext`,
  ADD COLUMN `payer_email` varchar(64) DEFAULT NULL COMMENT '付款人邮箱明文，禁止普通日志输出。' AFTER `payer_country`;
