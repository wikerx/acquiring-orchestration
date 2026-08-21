-- 商户通知原子尝试提交迁移回滚。
-- 回滚会删除人工回调事件审计字段，仅允许在确认无 MANUAL 数据后执行。

SET NAMES utf8mb4;

ALTER TABLE `transaction_event_outbox_202604` DROP INDEX `idx_event_status_update`;
ALTER TABLE `transaction_event_outbox_202603` DROP INDEX `idx_event_status_update`;
ALTER TABLE `transaction_event_outbox` DROP INDEX `idx_event_status_update`;

ALTER TABLE `transaction_merchant_notification_log_202604`
    DROP INDEX `uk_callback_event`,
    DROP COLUMN `delivery_mode`,
    DROP COLUMN `callback_event_id`;

ALTER TABLE `transaction_merchant_notification_log_202603`
    DROP INDEX `uk_callback_event`,
    DROP COLUMN `delivery_mode`,
    DROP COLUMN `callback_event_id`;

ALTER TABLE `transaction_merchant_notification_log`
    DROP INDEX `uk_callback_event`,
    DROP COLUMN `delivery_mode`,
    DROP COLUMN `callback_event_id`;

ALTER TABLE `transaction_merchant_notification_202604`
    DROP INDEX `idx_processing_event`,
    DROP COLUMN `processing_event_id`,
    DROP COLUMN `processing_mode`;

ALTER TABLE `transaction_merchant_notification_202603`
    DROP INDEX `idx_processing_event`,
    DROP COLUMN `processing_event_id`,
    DROP COLUMN `processing_mode`;

ALTER TABLE `transaction_merchant_notification`
    DROP INDEX `idx_processing_event`,
    DROP COLUMN `processing_event_id`,
    DROP COLUMN `processing_mode`;
