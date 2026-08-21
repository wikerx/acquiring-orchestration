-- Hosted Checkout 24 小时支付提交截止迁移回滚。
-- FAILED 只能按现有快照和重试条件近似恢复旧状态，无法还原历史上已被归一化的取消或阻断状态。

SET @schema_name = DATABASE();

UPDATE `payment_checkout_session`
SET `checkout_status` = CASE
        WHEN `checkout_status` = 'PENDING' THEN 'PAYABLE'
        WHEN `checkout_status` = 'PROCESSING' THEN 'PROCESSING'
        WHEN `checkout_status` = 'SUCCESS' THEN 'SUCCEEDED'
        WHEN `result_snapshot` LIKE '%PAYMENT_TIMEOUT%' THEN 'EXPIRED'
        WHEN `retry_allowed` = 1 AND `expire_time` > CURRENT_TIMESTAMP(3) THEN 'PAYABLE_FAILED_RETRYABLE'
        ELSE 'FAILED_FINAL'
    END,
    `update_time` = CURRENT_TIMESTAMP(3)
WHERE `checkout_status` IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED');

ALTER TABLE `payment_checkout_session`
    MODIFY COLUMN `checkout_status` varchar(32) NOT NULL
        COMMENT '收银台会话状态：PAYABLE、PAYING、AUTHENTICATING、PROCESSING、PAYABLE_FAILED_RETRYABLE、SUCCEEDED、FAILED_FINAL、EXPIRED、CANCELLED、BLOCKED。';

UPDATE `payment_checkout_token` token
LEFT JOIN `payment_checkout_session` session
    ON session.`checkout_session_id` = token.`checkout_session_id`
SET token.`expire_time` = DATE_ADD(
        COALESCE(session.`expire_time`, token.`create_time`, CURRENT_TIMESTAMP(3)),
        INTERVAL 30 DAY
    ),
    token.`update_time` = CURRENT_TIMESTAMP(3)
WHERE token.`expire_time` IS NULL;

ALTER TABLE `payment_checkout_token`
    MODIFY COLUMN `expire_time` datetime(3) NOT NULL
        COMMENT '令牌过期时间。';

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
    ON `payment_checkout_session` (`checkout_status`, `expire_time`, `deleted`);

DELETE FROM `sys_job_task`
WHERE `job_code` = 'PAY_TIMEOUT_CLOSE'
  AND `handler_code` = 'paymentTimeoutClose';

-- 历史 CRON 演示任务保持停用，回滚不得重新引入重复关单调度。
