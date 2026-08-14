-- callbackUrl / redirectUrl 明文存储迁移。
-- 历史通知快照中仍有明文 URL/载荷时自动迁移；无法恢复 URL 的旧通知任务关闭且保留审计行。
-- 历史收银台和订单 URL 不强制回填，迁移后删除旧密文、密钥版本和哈希列。
USE `payment_acquiring`;

DELIMITER $$
DROP PROCEDURE IF EXISTS `callback_url_exec_ddl_if_missing`$$
CREATE PROCEDURE `callback_url_exec_ddl_if_missing`(
  IN p_table_name varchar(64),
  IN p_column_name varchar(64),
  IN p_ddl text
)
BEGIN
  IF NOT EXISTS (
      SELECT 1
        FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = p_table_name
         AND COLUMN_NAME = p_column_name
  ) THEN
    SET @callback_url_ddl = p_ddl;
    PREPARE callback_url_stmt FROM @callback_url_ddl;
    EXECUTE callback_url_stmt;
    DEALLOCATE PREPARE callback_url_stmt;
  END IF;
END$$

DROP PROCEDURE IF EXISTS `callback_url_exec_ddl_if_exists`$$
CREATE PROCEDURE `callback_url_exec_ddl_if_exists`(
  IN p_table_name varchar(64),
  IN p_column_name varchar(64),
  IN p_ddl text
)
BEGIN
  IF EXISTS (
      SELECT 1
        FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = p_table_name
         AND COLUMN_NAME = p_column_name
  ) THEN
    SET @callback_url_ddl = p_ddl;
    PREPARE callback_url_stmt FROM @callback_url_ddl;
    EXECUTE callback_url_stmt;
    DEALLOCATE PREPARE callback_url_stmt;
  END IF;
END$$

DROP PROCEDURE IF EXISTS `callback_url_backfill_notification_snapshot`$$
CREATE PROCEDURE `callback_url_backfill_notification_snapshot`(IN p_table_name varchar(64))
BEGIN
  IF EXISTS (
      SELECT 1
        FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = p_table_name
         AND COLUMN_NAME = 'notify_config_snapshot_json'
  ) THEN
    SET @callback_url_dml = CONCAT(
      'UPDATE `', p_table_name, '` ',
      'SET callback_url = COALESCE(callback_url, JSON_UNQUOTE(JSON_EXTRACT(notify_config_snapshot_json, ''$.callbackUrl''))), ',
      'payload_json = COALESCE(payload_json, JSON_UNQUOTE(JSON_EXTRACT(notify_config_snapshot_json, ''$.payloadJson''))) ',
      'WHERE notify_config_snapshot_json IS NOT NULL'
    );
    PREPARE callback_url_stmt FROM @callback_url_dml;
    EXECUTE callback_url_stmt;
    DEALLOCATE PREPARE callback_url_stmt;
  END IF;
END$$

DELIMITER ;

CALL `callback_url_exec_ddl_if_missing`(
  'payment_checkout_session', 'merchant_notify_url',
  'ALTER TABLE `payment_checkout_session` ADD COLUMN `merchant_notify_url` varchar(512) DEFAULT NULL COMMENT ''商户异步通知地址明文；禁止完整写入日志。'' AFTER `merchant_notify_url_ciphertext`'
);
CALL `callback_url_exec_ddl_if_missing`(
  'payment_checkout_session', 'redirect_url',
  'ALTER TABLE `payment_checkout_session` ADD COLUMN `redirect_url` varchar(512) DEFAULT NULL COMMENT ''交易完成后 Form POST 的商户结果页地址明文。'' AFTER `shipping_info_json`'
);

CALL `callback_url_exec_ddl_if_missing`(
  'transaction_order', 'callback_url',
  'ALTER TABLE `transaction_order` ADD COLUMN `callback_url` varchar(512) DEFAULT NULL COMMENT ''商户异步通知地址明文；禁止完整写入日志。'' AFTER `merchant_website`'
);
CALL `callback_url_exec_ddl_if_missing`(
  'transaction_order', 'redirect_url',
  'ALTER TABLE `transaction_order` ADD COLUMN `redirect_url` varchar(512) DEFAULT NULL COMMENT ''Hosted Checkout 结果页 Form POST 地址明文。'' AFTER `callback_url`'
);
CALL `callback_url_exec_ddl_if_missing`(
  'transaction_order', 'language',
  'ALTER TABLE `transaction_order` ADD COLUMN `language` varchar(20) DEFAULT NULL COMMENT ''Hosted Checkout 页面语言。'' AFTER `redirect_url`'
);

CALL `callback_url_exec_ddl_if_missing`(
  'transaction_order_202603', 'callback_url',
  'ALTER TABLE `transaction_order_202603` ADD COLUMN `callback_url` varchar(512) DEFAULT NULL COMMENT ''商户异步通知地址明文；禁止完整写入日志。'' AFTER `merchant_website`'
);
CALL `callback_url_exec_ddl_if_missing`(
  'transaction_order_202603', 'redirect_url',
  'ALTER TABLE `transaction_order_202603` ADD COLUMN `redirect_url` varchar(512) DEFAULT NULL COMMENT ''Hosted Checkout 结果页 Form POST 地址明文。'' AFTER `callback_url`'
);
CALL `callback_url_exec_ddl_if_missing`(
  'transaction_order_202603', 'language',
  'ALTER TABLE `transaction_order_202603` ADD COLUMN `language` varchar(20) DEFAULT NULL COMMENT ''Hosted Checkout 页面语言。'' AFTER `redirect_url`'
);

CALL `callback_url_exec_ddl_if_missing`(
  'transaction_order_202604', 'callback_url',
  'ALTER TABLE `transaction_order_202604` ADD COLUMN `callback_url` varchar(512) DEFAULT NULL COMMENT ''商户异步通知地址明文；禁止完整写入日志。'' AFTER `merchant_website`'
);
CALL `callback_url_exec_ddl_if_missing`(
  'transaction_order_202604', 'redirect_url',
  'ALTER TABLE `transaction_order_202604` ADD COLUMN `redirect_url` varchar(512) DEFAULT NULL COMMENT ''Hosted Checkout 结果页 Form POST 地址明文。'' AFTER `callback_url`'
);
CALL `callback_url_exec_ddl_if_missing`(
  'transaction_order_202604', 'language',
  'ALTER TABLE `transaction_order_202604` ADD COLUMN `language` varchar(20) DEFAULT NULL COMMENT ''Hosted Checkout 页面语言。'' AFTER `redirect_url`'
);

CALL `callback_url_exec_ddl_if_missing`(
  'transaction_merchant_notification', 'callback_url',
  'ALTER TABLE `transaction_merchant_notification` ADD COLUMN `callback_url` varchar(512) DEFAULT NULL COMMENT ''商户回调地址明文；禁止完整写入日志。'' AFTER `notify_config_version`'
);
CALL `callback_url_exec_ddl_if_missing`(
  'transaction_merchant_notification', 'payload_json',
  'ALTER TABLE `transaction_merchant_notification` ADD COLUMN `payload_json` mediumtext COMMENT ''商户通知业务载荷明文 JSON。'' AFTER `callback_url`'
);
CALL `callback_url_exec_ddl_if_missing`(
  'transaction_merchant_notification_202603', 'callback_url',
  'ALTER TABLE `transaction_merchant_notification_202603` ADD COLUMN `callback_url` varchar(512) DEFAULT NULL COMMENT ''商户回调地址明文；禁止完整写入日志。'' AFTER `notify_config_version`'
);
CALL `callback_url_exec_ddl_if_missing`(
  'transaction_merchant_notification_202603', 'payload_json',
  'ALTER TABLE `transaction_merchant_notification_202603` ADD COLUMN `payload_json` mediumtext COMMENT ''商户通知业务载荷明文 JSON。'' AFTER `callback_url`'
);
CALL `callback_url_exec_ddl_if_missing`(
  'transaction_merchant_notification_202604', 'callback_url',
  'ALTER TABLE `transaction_merchant_notification_202604` ADD COLUMN `callback_url` varchar(512) DEFAULT NULL COMMENT ''商户回调地址明文；禁止完整写入日志。'' AFTER `notify_config_version`'
);
CALL `callback_url_exec_ddl_if_missing`(
  'transaction_merchant_notification_202604', 'payload_json',
  'ALTER TABLE `transaction_merchant_notification_202604` ADD COLUMN `payload_json` mediumtext COMMENT ''商户通知业务载荷明文 JSON。'' AFTER `callback_url`'
);

CALL `callback_url_backfill_notification_snapshot`('transaction_merchant_notification');
CALL `callback_url_backfill_notification_snapshot`('transaction_merchant_notification_202603');
CALL `callback_url_backfill_notification_snapshot`('transaction_merchant_notification_202604');

UPDATE `transaction_order_202603` orders
JOIN `transaction_merchant_notification_202603` notifications
  ON notifications.transaction_id = orders.root_transaction_id
 AND notifications.deleted = 0
SET orders.callback_url = COALESCE(orders.callback_url, notifications.callback_url)
WHERE orders.callback_url IS NULL;

UPDATE `transaction_order_202604` orders
JOIN `transaction_merchant_notification_202604` notifications
  ON notifications.transaction_id = orders.root_transaction_id
 AND notifications.deleted = 0
SET orders.callback_url = COALESCE(orders.callback_url, notifications.callback_url)
WHERE orders.callback_url IS NULL;

UPDATE `transaction_order_202603` orders
JOIN `payment_checkout_session` sessions
  ON sessions.operation_id = orders.operation_id
 AND sessions.deleted = 0
SET orders.redirect_url = COALESCE(orders.redirect_url, sessions.redirect_url),
    orders.language = COALESCE(orders.language, sessions.locale)
WHERE orders.redirect_url IS NULL OR orders.language IS NULL;

UPDATE `transaction_order_202604` orders
JOIN `payment_checkout_session` sessions
  ON sessions.operation_id = orders.operation_id
 AND sessions.deleted = 0
SET orders.redirect_url = COALESCE(orders.redirect_url, sessions.redirect_url),
    orders.language = COALESCE(orders.language, sessions.locale)
WHERE orders.redirect_url IS NULL OR orders.language IS NULL;

-- 无法从旧快照恢复 URL 的历史任务不得继续重试，避免迁移后误投递。
UPDATE `transaction_merchant_notification`
   SET `fail_reason` = CASE
         WHEN (`callback_url` IS NULL OR `callback_url` = '')
          AND `notify_status` IN ('INIT', 'PROCESSING', 'FAILED') THEN 'LEGACY_CALLBACK_URL_DISCARDED'
         ELSE `fail_reason`
       END,
       `notify_status` = CASE
         WHEN (`callback_url` IS NULL OR `callback_url` = '')
          AND `notify_status` IN ('INIT', 'PROCESSING', 'FAILED') THEN 'CLOSED'
         ELSE `notify_status`
       END,
       `next_retry_time` = CASE
         WHEN (`callback_url` IS NULL OR `callback_url` = '') THEN NULL
         ELSE `next_retry_time`
       END,
       `callback_url` = COALESCE(`callback_url`, ''),
       `payload_json` = COALESCE(`payload_json`, '{}');
UPDATE `transaction_merchant_notification_202603`
   SET `fail_reason` = CASE
         WHEN (`callback_url` IS NULL OR `callback_url` = '')
          AND `notify_status` IN ('INIT', 'PROCESSING', 'FAILED') THEN 'LEGACY_CALLBACK_URL_DISCARDED'
         ELSE `fail_reason`
       END,
       `notify_status` = CASE
         WHEN (`callback_url` IS NULL OR `callback_url` = '')
          AND `notify_status` IN ('INIT', 'PROCESSING', 'FAILED') THEN 'CLOSED'
         ELSE `notify_status`
       END,
       `next_retry_time` = CASE
         WHEN (`callback_url` IS NULL OR `callback_url` = '') THEN NULL
         ELSE `next_retry_time`
       END,
       `callback_url` = COALESCE(`callback_url`, ''),
       `payload_json` = COALESCE(`payload_json`, '{}');
UPDATE `transaction_merchant_notification_202604`
   SET `fail_reason` = CASE
         WHEN (`callback_url` IS NULL OR `callback_url` = '')
          AND `notify_status` IN ('INIT', 'PROCESSING', 'FAILED') THEN 'LEGACY_CALLBACK_URL_DISCARDED'
         ELSE `fail_reason`
       END,
       `notify_status` = CASE
         WHEN (`callback_url` IS NULL OR `callback_url` = '')
          AND `notify_status` IN ('INIT', 'PROCESSING', 'FAILED') THEN 'CLOSED'
         ELSE `notify_status`
       END,
       `next_retry_time` = CASE
         WHEN (`callback_url` IS NULL OR `callback_url` = '') THEN NULL
         ELSE `next_retry_time`
       END,
       `callback_url` = COALESCE(`callback_url`, ''),
       `payload_json` = COALESCE(`payload_json`, '{}');

CALL `callback_url_exec_ddl_if_exists`('payment_checkout_session', 'merchant_notify_url_hash', 'ALTER TABLE `payment_checkout_session` DROP COLUMN `merchant_notify_url_hash`');
CALL `callback_url_exec_ddl_if_exists`('payment_checkout_session', 'merchant_notify_url_ciphertext', 'ALTER TABLE `payment_checkout_session` DROP COLUMN `merchant_notify_url_ciphertext`');
CALL `callback_url_exec_ddl_if_exists`('payment_checkout_session', 'redirect_url_hash', 'ALTER TABLE `payment_checkout_session` DROP COLUMN `redirect_url_hash`');
CALL `callback_url_exec_ddl_if_exists`('payment_checkout_session', 'redirect_url_ciphertext', 'ALTER TABLE `payment_checkout_session` DROP COLUMN `redirect_url_ciphertext`');
CALL `callback_url_exec_ddl_if_exists`('payment_checkout_session', 'redirect_url_encryption_key_version', 'ALTER TABLE `payment_checkout_session` DROP COLUMN `redirect_url_encryption_key_version`');

CALL `callback_url_exec_ddl_if_exists`('transaction_order', 'callback_url_ciphertext', 'ALTER TABLE `transaction_order` DROP COLUMN `callback_url_ciphertext`');
CALL `callback_url_exec_ddl_if_exists`('transaction_order', 'callback_url_encryption_key_version', 'ALTER TABLE `transaction_order` DROP COLUMN `callback_url_encryption_key_version`');
CALL `callback_url_exec_ddl_if_exists`('transaction_order', 'callback_url_hash', 'ALTER TABLE `transaction_order` DROP COLUMN `callback_url_hash`');
CALL `callback_url_exec_ddl_if_exists`('transaction_order', 'redirect_url_ciphertext', 'ALTER TABLE `transaction_order` DROP COLUMN `redirect_url_ciphertext`');
CALL `callback_url_exec_ddl_if_exists`('transaction_order', 'redirect_url_encryption_key_version', 'ALTER TABLE `transaction_order` DROP COLUMN `redirect_url_encryption_key_version`');
CALL `callback_url_exec_ddl_if_exists`('transaction_order', 'redirect_url_hash', 'ALTER TABLE `transaction_order` DROP COLUMN `redirect_url_hash`');

CALL `callback_url_exec_ddl_if_exists`('transaction_order_202603', 'callback_url_ciphertext', 'ALTER TABLE `transaction_order_202603` DROP COLUMN `callback_url_ciphertext`');
CALL `callback_url_exec_ddl_if_exists`('transaction_order_202603', 'callback_url_encryption_key_version', 'ALTER TABLE `transaction_order_202603` DROP COLUMN `callback_url_encryption_key_version`');
CALL `callback_url_exec_ddl_if_exists`('transaction_order_202603', 'callback_url_hash', 'ALTER TABLE `transaction_order_202603` DROP COLUMN `callback_url_hash`');
CALL `callback_url_exec_ddl_if_exists`('transaction_order_202603', 'redirect_url_ciphertext', 'ALTER TABLE `transaction_order_202603` DROP COLUMN `redirect_url_ciphertext`');
CALL `callback_url_exec_ddl_if_exists`('transaction_order_202603', 'redirect_url_encryption_key_version', 'ALTER TABLE `transaction_order_202603` DROP COLUMN `redirect_url_encryption_key_version`');
CALL `callback_url_exec_ddl_if_exists`('transaction_order_202603', 'redirect_url_hash', 'ALTER TABLE `transaction_order_202603` DROP COLUMN `redirect_url_hash`');

CALL `callback_url_exec_ddl_if_exists`('transaction_order_202604', 'callback_url_ciphertext', 'ALTER TABLE `transaction_order_202604` DROP COLUMN `callback_url_ciphertext`');
CALL `callback_url_exec_ddl_if_exists`('transaction_order_202604', 'callback_url_encryption_key_version', 'ALTER TABLE `transaction_order_202604` DROP COLUMN `callback_url_encryption_key_version`');
CALL `callback_url_exec_ddl_if_exists`('transaction_order_202604', 'callback_url_hash', 'ALTER TABLE `transaction_order_202604` DROP COLUMN `callback_url_hash`');
CALL `callback_url_exec_ddl_if_exists`('transaction_order_202604', 'redirect_url_ciphertext', 'ALTER TABLE `transaction_order_202604` DROP COLUMN `redirect_url_ciphertext`');
CALL `callback_url_exec_ddl_if_exists`('transaction_order_202604', 'redirect_url_encryption_key_version', 'ALTER TABLE `transaction_order_202604` DROP COLUMN `redirect_url_encryption_key_version`');
CALL `callback_url_exec_ddl_if_exists`('transaction_order_202604', 'redirect_url_hash', 'ALTER TABLE `transaction_order_202604` DROP COLUMN `redirect_url_hash`');

CALL `callback_url_exec_ddl_if_exists`('transaction_merchant_notification', 'notify_config_snapshot_json', 'ALTER TABLE `transaction_merchant_notification` DROP COLUMN `notify_config_snapshot_json`');
CALL `callback_url_exec_ddl_if_exists`('transaction_merchant_notification_202603', 'notify_config_snapshot_json', 'ALTER TABLE `transaction_merchant_notification_202603` DROP COLUMN `notify_config_snapshot_json`');
CALL `callback_url_exec_ddl_if_exists`('transaction_merchant_notification_202604', 'notify_config_snapshot_json', 'ALTER TABLE `transaction_merchant_notification_202604` DROP COLUMN `notify_config_snapshot_json`');

ALTER TABLE `transaction_merchant_notification`
  MODIFY COLUMN `callback_url` varchar(512) NOT NULL COMMENT '商户回调地址明文；禁止完整写入日志。',
  MODIFY COLUMN `payload_json` mediumtext NOT NULL COMMENT '商户通知业务载荷明文 JSON。';
ALTER TABLE `transaction_merchant_notification_202603`
  MODIFY COLUMN `callback_url` varchar(512) NOT NULL COMMENT '商户回调地址明文；禁止完整写入日志。',
  MODIFY COLUMN `payload_json` mediumtext NOT NULL COMMENT '商户通知业务载荷明文 JSON。';
ALTER TABLE `transaction_merchant_notification_202604`
  MODIFY COLUMN `callback_url` varchar(512) NOT NULL COMMENT '商户回调地址明文；禁止完整写入日志。',
  MODIFY COLUMN `payload_json` mediumtext NOT NULL COMMENT '商户通知业务载荷明文 JSON。';

DROP PROCEDURE `callback_url_backfill_notification_snapshot`;
DROP PROCEDURE `callback_url_exec_ddl_if_exists`;
DROP PROCEDURE `callback_url_exec_ddl_if_missing`;
