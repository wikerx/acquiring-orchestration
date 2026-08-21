-- 商户交易结果回调五次自动投递计划回滚。
-- 只恢复表默认值和仍在执行的任务上限；迁移时已关闭的耗尽任务不自动重新打开。

SET NAMES utf8mb4;

ALTER TABLE `transaction_merchant_notification`
    MODIFY COLUMN `max_retry_count` int NOT NULL DEFAULT 10
        COMMENT '最大重试次数。';

ALTER TABLE `transaction_merchant_notification_202603`
    MODIFY COLUMN `max_retry_count` int NOT NULL DEFAULT 10
        COMMENT '最大重试次数。';

ALTER TABLE `transaction_merchant_notification_202604`
    MODIFY COLUMN `max_retry_count` int NOT NULL DEFAULT 10
        COMMENT '最大重试次数。';

UPDATE `transaction_merchant_notification_202603`
SET `max_retry_count` = 10,
    `update_time` = CURRENT_TIMESTAMP(3)
WHERE `deleted` = 0
  AND `notify_status` IN ('INIT', 'FAILED')
  AND `max_retry_count` = 5;

UPDATE `transaction_merchant_notification_202604`
SET `max_retry_count` = 10,
    `update_time` = CURRENT_TIMESTAMP(3)
WHERE `deleted` = 0
  AND `notify_status` IN ('INIT', 'FAILED')
  AND `max_retry_count` = 5;
