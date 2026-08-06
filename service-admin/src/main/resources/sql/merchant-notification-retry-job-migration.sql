-- 商户通知低频对账任务。默认覆盖全部已发布季度并将到期任务重新可靠入 MQ。
INSERT INTO sys_job_task (
    job_code, job_name, job_group, handler_code, cron_expression, scheduler_mode, trigger_mode, execute_mode,
    route_strategy, misfire_strategy, timeout_seconds, retry_count, retry_interval_seconds, allow_concurrent,
    params, status, description, next_trigger_time, version, deleted, create_by, update_by
) VALUES (
    'MERCHANT_NOTIFICATION_RETRY', '商户通知补偿重试任务', 'transaction', 'merchantNotificationRetry',
    '0 */5 * * * ?', 'DISTRIBUTED', 'CRON', 'SYNC', 'LOCAL', 'FIRE_ONCE',
    300, 1, 60, 0, JSON_OBJECT('limit', 5, 'mode', 'MQ'), 'ENABLED',
    '每五分钟对账全部已发布季度的到期商户通知并重新可靠入 MQ；数据库版本 CAS 防止重复回调。',
    DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 5 MINUTE), 0, 0, 'system', 'system'
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
            THEN DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 5 MINUTE)
        ELSE next_trigger_time
    END,
    update_by = 'system';
