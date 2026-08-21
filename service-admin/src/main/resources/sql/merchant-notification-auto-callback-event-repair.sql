-- 自动商户回调曾误将协议事件号写入人工重发幂等列，导致第二次自动尝试触发唯一键冲突。
-- 协议事件号仍保留在 request_header_json_masked，本脚本只修正 callback_event_id 的领域语义。
SET NAMES utf8mb4;

START TRANSACTION;

UPDATE `transaction_merchant_notification_log`
SET `callback_event_id` = NULL
WHERE `delivery_mode` = 'AUTO'
  AND `callback_event_id` IS NOT NULL;

UPDATE `transaction_merchant_notification_log_202603`
SET `callback_event_id` = NULL
WHERE `delivery_mode` = 'AUTO'
  AND `callback_event_id` IS NOT NULL;

UPDATE `transaction_merchant_notification_log_202604`
SET `callback_event_id` = NULL
WHERE `delivery_mode` = 'AUTO'
  AND `callback_event_id` IS NOT NULL;

COMMIT;
