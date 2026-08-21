-- Hosted Checkout 24 小时支付提交截止与四状态迁移。
-- 可重复执行；不新增表和业务字段。执行前应确认 service-payment 已升级到对应版本。

SET @schema_name = DATABASE();

UPDATE `payment_checkout_session`
SET `checkout_status` = CASE
        WHEN `checkout_status` IN ('PAYABLE') THEN 'PENDING'
        WHEN `checkout_status` IN ('PAYING', 'AUTHENTICATING', 'PROCESSING') THEN 'PROCESSING'
        WHEN `checkout_status` IN ('SUCCEEDED', 'SUCCESS') THEN 'SUCCESS'
        ELSE 'FAILED'
    END,
    `update_time` = CURRENT_TIMESTAMP(3)
WHERE `checkout_status` NOT IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED');

ALTER TABLE `payment_checkout_session`
    MODIFY COLUMN `checkout_status` varchar(32) NOT NULL
        COMMENT '支付状态：PENDING待处理、PROCESSING处理中、SUCCESS成功、FAILED失败。';

ALTER TABLE `payment_checkout_token`
    MODIFY COLUMN `expire_time` datetime(3) NULL
        COMMENT '令牌可选失效时间；NULL 表示未撤销前允许持续查询结果，不代表允许继续支付。';

UPDATE `payment_checkout_token`
SET `expire_time` = NULL,
    `update_time` = CURRENT_TIMESTAMP(3)
WHERE `token_status` = 'ACTIVE'
  AND `deleted` = 0
  AND `expire_time` IS NOT NULL;

SET @expire_index_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'payment_checkout_session'
      AND index_name = 'idx_checkout_expire_scan'
);
SET @drop_expire_index_sql = IF(
    @expire_index_exists > 0,
    'ALTER TABLE `payment_checkout_session` DROP INDEX `idx_checkout_expire_scan`',
    'SELECT 1'
);
PREPARE expire_index_stmt FROM @drop_expire_index_sql;
EXECUTE expire_index_stmt;
DEALLOCATE PREPARE expire_index_stmt;

CREATE INDEX `idx_checkout_expire_scan`
    ON `payment_checkout_session`
       (`checkout_status`, `process_stage`, `last_submit_time`, `deleted`, `expire_time`, `id`);

INSERT INTO `sys_job_task` (
    `job_code`, `job_name`, `job_group`, `handler_code`, `cron_expression`, `scheduler_mode`, `trigger_mode`,
    `execute_mode`, `route_strategy`, `misfire_strategy`, `timeout_seconds`, `retry_count`,
    `retry_interval_seconds`, `allow_concurrent`, `params`, `status`, `description`, `next_trigger_time`,
    `version`, `deleted`, `create_by`, `update_by`
) VALUES (
    'PAY_TIMEOUT_CLOSE', '支付超时关单任务', 'payment', 'paymentTimeoutClose', '0 */1 * * * ?',
    'DISTRIBUTED', 'CRON', 'SYNC', 'LOCAL', 'FIRE_ONCE', 300, 0, 60, 0,
    JSON_OBJECT('limit', 200), 'ENABLED',
    '每分钟关闭超过付款截止时间且从未提交支付的收银台订单；处理中交易不受影响。',
    DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 1 MINUTE), 0, 0, 'system', 'system'
)
ON DUPLICATE KEY UPDATE
    `job_name` = VALUES(`job_name`),
    `job_group` = VALUES(`job_group`),
    `handler_code` = VALUES(`handler_code`),
    `cron_expression` = VALUES(`cron_expression`),
    `scheduler_mode` = VALUES(`scheduler_mode`),
    `trigger_mode` = VALUES(`trigger_mode`),
    `execute_mode` = VALUES(`execute_mode`),
    `route_strategy` = VALUES(`route_strategy`),
    `misfire_strategy` = VALUES(`misfire_strategy`),
    `timeout_seconds` = VALUES(`timeout_seconds`),
    `retry_count` = VALUES(`retry_count`),
    `retry_interval_seconds` = VALUES(`retry_interval_seconds`),
    `allow_concurrent` = VALUES(`allow_concurrent`),
    `params` = VALUES(`params`),
    `description` = VALUES(`description`),
    `status` = 'ENABLED',
    `next_trigger_time` = COALESCE(`next_trigger_time`, DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 1 MINUTE)),
    `deleted` = 0,
    `update_by` = 'system';

-- 正式任务启用后停用历史 CRON 演示任务，避免同一处理器被重复调度。
UPDATE `sys_job_task`
SET `status` = 'DISABLED',
    `next_trigger_time` = NULL,
    `version` = `version` + 1,
    `update_by` = 'system',
    `update_time` = CURRENT_TIMESTAMP(3)
WHERE `job_code` IN ('JOB_DEMO_CRON_CLOSE_1M', 'JOB_DEMO_CRON_CLOSE_5M')
  AND `handler_code` = 'paymentTimeoutClose'
  AND `trigger_mode` = 'CRON'
  AND `deleted` = 0;
