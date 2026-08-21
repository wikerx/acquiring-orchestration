-- 回滚分片启用前遗留商户通知迁移。
-- 只处理小于季度号段起点的历史 ID；正常季度号段记录不会被复制或删除。

SET NAMES utf8mb4;

START TRANSACTION;

INSERT INTO `transaction_merchant_notification`
(
  `id`, `notify_id`, `transaction_id`, `operation_id`, `merchant_id`, `merchant_order_no`,
  `notify_type`, `event_type`, `notify_status`, `notify_config_version`, `callback_url`, `payload_json`,
  `target_url_hash`, `target_url_masked`, `payload_json_masked`, `sign_type`, `last_attempt_no`,
  `max_retry_count`, `next_retry_time`, `success_time`, `fail_reason`, `processing_mode`,
  `processing_event_id`, `transaction_date_time`, `transaction_utc_time`, `transaction_time_zone`,
  `version`, `deleted`, `create_time`, `update_time`
)
SELECT
  `source`.`id`, `source`.`notify_id`, `source`.`transaction_id`, `source`.`operation_id`,
  `source`.`merchant_id`, `source`.`merchant_order_no`, `source`.`notify_type`, `source`.`event_type`,
  `source`.`notify_status`, `source`.`notify_config_version`, `source`.`callback_url`, `source`.`payload_json`,
  `source`.`target_url_hash`, `source`.`target_url_masked`, `source`.`payload_json_masked`,
  `source`.`sign_type`, `source`.`last_attempt_no`, `source`.`max_retry_count`, `source`.`next_retry_time`,
  `source`.`success_time`, `source`.`fail_reason`, `source`.`processing_mode`, `source`.`processing_event_id`,
  `source`.`transaction_date_time`, `source`.`transaction_utc_time`, `source`.`transaction_time_zone`,
  `source`.`version`, `source`.`deleted`, `source`.`create_time`, `source`.`update_time`
FROM `transaction_merchant_notification_202603` AS `source`
WHERE `source`.`id` < 202600000000000000;

INSERT INTO `transaction_merchant_notification`
(
  `id`, `notify_id`, `transaction_id`, `operation_id`, `merchant_id`, `merchant_order_no`,
  `notify_type`, `event_type`, `notify_status`, `notify_config_version`, `callback_url`, `payload_json`,
  `target_url_hash`, `target_url_masked`, `payload_json_masked`, `sign_type`, `last_attempt_no`,
  `max_retry_count`, `next_retry_time`, `success_time`, `fail_reason`, `processing_mode`,
  `processing_event_id`, `transaction_date_time`, `transaction_utc_time`, `transaction_time_zone`,
  `version`, `deleted`, `create_time`, `update_time`
)
SELECT
  `source`.`id`, `source`.`notify_id`, `source`.`transaction_id`, `source`.`operation_id`,
  `source`.`merchant_id`, `source`.`merchant_order_no`, `source`.`notify_type`, `source`.`event_type`,
  `source`.`notify_status`, `source`.`notify_config_version`, `source`.`callback_url`, `source`.`payload_json`,
  `source`.`target_url_hash`, `source`.`target_url_masked`, `source`.`payload_json_masked`,
  `source`.`sign_type`, `source`.`last_attempt_no`, `source`.`max_retry_count`, `source`.`next_retry_time`,
  `source`.`success_time`, `source`.`fail_reason`, `source`.`processing_mode`, `source`.`processing_event_id`,
  `source`.`transaction_date_time`, `source`.`transaction_utc_time`, `source`.`transaction_time_zone`,
  `source`.`version`, `source`.`deleted`, `source`.`create_time`, `source`.`update_time`
FROM `transaction_merchant_notification_202604` AS `source`
WHERE `source`.`id` < 202600000000000000;

INSERT INTO `transaction_merchant_notification_log`
(
  `id`, `notify_log_id`, `notify_id`, `callback_event_id`, `delivery_mode`, `transaction_id`,
  `operation_id`, `merchant_id`, `attempt_no`, `target_url_hash`, `http_status`,
  `request_header_json_masked`, `request_body_json_masked`, `response_body_json_masked`,
  `success`, `error_message`, `notify_time`, `duration_millis`, `transaction_date_time`,
  `transaction_utc_time`, `transaction_time_zone`, `create_time`
)
SELECT
  `source`.`id`, `source`.`notify_log_id`, `source`.`notify_id`, `source`.`callback_event_id`,
  `source`.`delivery_mode`, `source`.`transaction_id`, `source`.`operation_id`, `source`.`merchant_id`,
  `source`.`attempt_no`, `source`.`target_url_hash`, `source`.`http_status`,
  `source`.`request_header_json_masked`, `source`.`request_body_json_masked`,
  `source`.`response_body_json_masked`, `source`.`success`, `source`.`error_message`,
  `source`.`notify_time`, `source`.`duration_millis`, `source`.`transaction_date_time`,
  `source`.`transaction_utc_time`, `source`.`transaction_time_zone`, `source`.`create_time`
FROM `transaction_merchant_notification_log_202603` AS `source`
JOIN `transaction_merchant_notification_202603` AS `notification`
  ON `notification`.`notify_id` = `source`.`notify_id`
WHERE `notification`.`id` < 202600000000000000;

INSERT INTO `transaction_merchant_notification_log`
(
  `id`, `notify_log_id`, `notify_id`, `callback_event_id`, `delivery_mode`, `transaction_id`,
  `operation_id`, `merchant_id`, `attempt_no`, `target_url_hash`, `http_status`,
  `request_header_json_masked`, `request_body_json_masked`, `response_body_json_masked`,
  `success`, `error_message`, `notify_time`, `duration_millis`, `transaction_date_time`,
  `transaction_utc_time`, `transaction_time_zone`, `create_time`
)
SELECT
  `source`.`id`, `source`.`notify_log_id`, `source`.`notify_id`, `source`.`callback_event_id`,
  `source`.`delivery_mode`, `source`.`transaction_id`, `source`.`operation_id`, `source`.`merchant_id`,
  `source`.`attempt_no`, `source`.`target_url_hash`, `source`.`http_status`,
  `source`.`request_header_json_masked`, `source`.`request_body_json_masked`,
  `source`.`response_body_json_masked`, `source`.`success`, `source`.`error_message`,
  `source`.`notify_time`, `source`.`duration_millis`, `source`.`transaction_date_time`,
  `source`.`transaction_utc_time`, `source`.`transaction_time_zone`, `source`.`create_time`
FROM `transaction_merchant_notification_log_202604` AS `source`
JOIN `transaction_merchant_notification_202604` AS `notification`
  ON `notification`.`notify_id` = `source`.`notify_id`
WHERE `notification`.`id` < 202600000000000000;

DELETE FROM `transaction_merchant_notification_log_202603`
WHERE EXISTS (
  SELECT 1
  FROM `transaction_merchant_notification_202603` AS `notification`
  WHERE `notification`.`notify_id` = `transaction_merchant_notification_log_202603`.`notify_id`
    AND `notification`.`id` < 202600000000000000
);

DELETE FROM `transaction_merchant_notification_log_202604`
WHERE EXISTS (
  SELECT 1
  FROM `transaction_merchant_notification_202604` AS `notification`
  WHERE `notification`.`notify_id` = `transaction_merchant_notification_log_202604`.`notify_id`
    AND `notification`.`id` < 202600000000000000
);

DELETE FROM `transaction_merchant_notification_202603`
WHERE `id` < 202600000000000000;

DELETE FROM `transaction_merchant_notification_202604`
WHERE `id` < 202600000000000000;

COMMIT;
