-- 商户通知补偿任务。首次 MQ 投递失败后，按 transaction_date_time 路由当前季度分表继续重试。
INSERT INTO sys_job_task (
    job_code, job_name, job_group, handler_code, cron_expression, scheduler_mode, trigger_mode, execute_mode,
    route_strategy, misfire_strategy, timeout_seconds, retry_count, retry_interval_seconds, allow_concurrent,
    params, status, description, next_trigger_time, version, deleted, create_by, update_by
) VALUES (
    'MERCHANT_NOTIFICATION_RETRY', '商户通知补偿重试任务', 'transaction', 'merchantNotificationRetry',
    '0 */1 * * * ?', 'DISTRIBUTED', 'CRON', 'SYNC', 'LOCAL', 'FIRE_ONCE',
    300, 1, 60, 0, JSON_OBJECT('limit', 5), 'ENABLED',
    '每分钟扫描当前季度已到期的 INIT/FAILED 商户通知；通过分片时间、状态和版本号 CAS 抢占，禁止并发执行。',
    DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE), 0, 0, 'system', 'system'
)
ON DUPLICATE KEY UPDATE
    job_name = VALUES(job_name),
    job_group = VALUES(job_group),
    handler_code = VALUES(handler_code),
    cron_expression = VALUES(cron_expression),
    scheduler_mode = VALUES(scheduler_mode),
    trigger_mode = VALUES(trigger_mode),
    execute_mode = VALUES(execute_mode),
    route_strategy = VALUES(route_strategy),
    misfire_strategy = VALUES(misfire_strategy),
    timeout_seconds = VALUES(timeout_seconds),
    retry_count = VALUES(retry_count),
    retry_interval_seconds = VALUES(retry_interval_seconds),
    allow_concurrent = VALUES(allow_concurrent),
    params = VALUES(params),
    description = VALUES(description),
    next_trigger_time = CASE
        WHEN status = 'ENABLED' AND next_trigger_time IS NULL
            THEN DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE)
        ELSE next_trigger_time
    END,
    update_by = 'system';
