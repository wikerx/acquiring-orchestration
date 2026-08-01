-- 渠道交易查询勾兑任务。查询只确认渠道状态，不会按重试次数把未知资金结果自动判为失败。
INSERT INTO sys_job_task (
    job_code, job_name, job_group, handler_code, cron_expression, scheduler_mode, trigger_mode, execute_mode,
    route_strategy, misfire_strategy, timeout_seconds, retry_count, retry_interval_seconds, allow_concurrent,
    params, status, description, next_trigger_time, version, deleted, create_by, update_by
) VALUES (
    'CHANNEL_TRANSACTION_MATCH', '渠道交易查询勾兑任务', 'transaction', 'channelTransactionMatch',
    '0 */1 * * * ?', 'DISTRIBUTED', 'CRON', 'SYNC', 'LOCAL', 'FIRE_ONCE',
    300, 1, 60, 0, JSON_OBJECT('lookbackQuarters', 4, 'limit', 100), 'ENABLED',
    '每分钟扫描当前及前三个季度的到期非终态交易；单笔查询使用分阶段退避，渠道结果未知时保持处理中。',
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
