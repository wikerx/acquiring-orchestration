-- 商户通知原子尝试提交与人工 MQ 幂等迁移。
-- 执行顺序：先停 service-data 通知消费者，再执行本脚本，最后发布并重启服务。

SET NAMES utf8mb4;

ALTER TABLE `transaction_merchant_notification`
    ADD COLUMN `processing_mode` varchar(16) DEFAULT NULL
        COMMENT '当前执行模式：AUTO 自动投递、MANUAL 人工立即重发。' AFTER `fail_reason`,
    ADD COLUMN `processing_event_id` varchar(128) DEFAULT NULL
        COMMENT '当前人工 MQ 事件号；自动投递为空。' AFTER `processing_mode`,
    ADD KEY `idx_processing_event` (`processing_event_id`, `transaction_date_time`);

ALTER TABLE `transaction_merchant_notification_202603`
    ADD COLUMN `processing_mode` varchar(16) DEFAULT NULL
        COMMENT '当前执行模式：AUTO 自动投递、MANUAL 人工立即重发。' AFTER `fail_reason`,
    ADD COLUMN `processing_event_id` varchar(128) DEFAULT NULL
        COMMENT '当前人工 MQ 事件号；自动投递为空。' AFTER `processing_mode`,
    ADD KEY `idx_processing_event` (`processing_event_id`, `transaction_date_time`);

ALTER TABLE `transaction_merchant_notification_202604`
    ADD COLUMN `processing_mode` varchar(16) DEFAULT NULL
        COMMENT '当前执行模式：AUTO 自动投递、MANUAL 人工立即重发。' AFTER `fail_reason`,
    ADD COLUMN `processing_event_id` varchar(128) DEFAULT NULL
        COMMENT '当前人工 MQ 事件号；自动投递为空。' AFTER `processing_mode`,
    ADD KEY `idx_processing_event` (`processing_event_id`, `transaction_date_time`);

ALTER TABLE `transaction_merchant_notification_log`
    ADD COLUMN `callback_event_id` varchar(128) DEFAULT NULL
        COMMENT '人工重发 MQ 稳定事件号；自动投递为空。' AFTER `notify_id`,
    ADD COLUMN `delivery_mode` varchar(16) NOT NULL DEFAULT 'AUTO'
        COMMENT '投递模式：AUTO 自动计划、MANUAL 人工立即重发。' AFTER `callback_event_id`,
    ADD UNIQUE KEY `uk_callback_event` (`callback_event_id`);

ALTER TABLE `transaction_merchant_notification_log_202603`
    ADD COLUMN `callback_event_id` varchar(128) DEFAULT NULL
        COMMENT '人工重发 MQ 稳定事件号；自动投递为空。' AFTER `notify_id`,
    ADD COLUMN `delivery_mode` varchar(16) NOT NULL DEFAULT 'AUTO'
        COMMENT '投递模式：AUTO 自动计划、MANUAL 人工立即重发。' AFTER `callback_event_id`,
    ADD UNIQUE KEY `uk_callback_event` (`callback_event_id`);

ALTER TABLE `transaction_merchant_notification_log_202604`
    ADD COLUMN `callback_event_id` varchar(128) DEFAULT NULL
        COMMENT '人工重发 MQ 稳定事件号；自动投递为空。' AFTER `notify_id`,
    ADD COLUMN `delivery_mode` varchar(16) NOT NULL DEFAULT 'AUTO'
        COMMENT '投递模式：AUTO 自动计划、MANUAL 人工立即重发。' AFTER `callback_event_id`,
    ADD UNIQUE KEY `uk_callback_event` (`callback_event_id`);

ALTER TABLE `transaction_event_outbox`
    ADD KEY `idx_event_status_update` (`event_status`, `update_time`);

ALTER TABLE `transaction_event_outbox_202603`
    ADD KEY `idx_event_status_update` (`event_status`, `update_time`);

ALTER TABLE `transaction_event_outbox_202604`
    ADD KEY `idx_event_status_update` (`event_status`, `update_time`);
