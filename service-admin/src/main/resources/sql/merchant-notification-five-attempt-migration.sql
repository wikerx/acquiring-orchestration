-- 商户交易结果回调五次自动投递计划迁移。
-- 首次任务就绪后等待 5 秒；后续失败后依次等待 1、5、10、30 分钟。
-- 可重复执行；不新增表和字段，不改写 SUCCESS、CLOSED 历史审计快照。

SET NAMES utf8mb4;

ALTER TABLE `transaction_merchant_notification`
    MODIFY COLUMN `max_retry_count` int NOT NULL DEFAULT 5
        COMMENT '自动通知最大投递次数，当前协议固定为5次。';

ALTER TABLE `transaction_merchant_notification_202603`
    MODIFY COLUMN `max_retry_count` int NOT NULL DEFAULT 5
        COMMENT '自动通知最大投递次数，当前协议固定为5次。';

ALTER TABLE `transaction_merchant_notification_202604`
    MODIFY COLUMN `max_retry_count` int NOT NULL DEFAULT 5
        COMMENT '自动通知最大投递次数，当前协议固定为5次。';

UPDATE `transaction_merchant_notification_202603`
SET `max_retry_count` = 5,
    `update_time` = CURRENT_TIMESTAMP(3)
WHERE `deleted` = 0
  AND `notify_status` IN ('INIT', 'FAILED')
  AND `last_attempt_no` < 5
  AND `max_retry_count` <> 5;

UPDATE `transaction_merchant_notification_202604`
SET `max_retry_count` = 5,
    `update_time` = CURRENT_TIMESTAMP(3)
WHERE `deleted` = 0
  AND `notify_status` IN ('INIT', 'FAILED')
  AND `last_attempt_no` < 5
  AND `max_retry_count` <> 5;

UPDATE `transaction_merchant_notification_202603`
SET `notify_status` = 'CLOSED',
    `max_retry_count` = 5,
    `next_retry_time` = NULL,
    `fail_reason` = COALESCE(NULLIF(`fail_reason`, ''), 'automatic notification attempts exhausted'),
    `version` = `version` + 1,
    `update_time` = CURRENT_TIMESTAMP(3)
WHERE `deleted` = 0
  AND `notify_status` IN ('INIT', 'FAILED')
  AND `last_attempt_no` >= 5;

UPDATE `transaction_merchant_notification_202604`
SET `notify_status` = 'CLOSED',
    `max_retry_count` = 5,
    `next_retry_time` = NULL,
    `fail_reason` = COALESCE(NULLIF(`fail_reason`, ''), 'automatic notification attempts exhausted'),
    `version` = `version` + 1,
    `update_time` = CURRENT_TIMESTAMP(3)
WHERE `deleted` = 0
  AND `notify_status` IN ('INIT', 'FAILED')
  AND `last_attempt_no` >= 5;
