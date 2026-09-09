-- 清分漏单滚动补偿任务。
-- 默认每五分钟扫描最近十五分钟；重叠窗口和数据库幂等键共同防止边界漏单及重复恢复。
INSERT INTO sys_job_task (
    job_code, job_name, job_group, handler_code, cron_expression, scheduler_mode, trigger_mode, execute_mode,
    route_strategy, misfire_strategy, timeout_seconds, retry_count, retry_interval_seconds, allow_concurrent,
    params, status, description, next_trigger_time, version, deleted, create_by, update_by
) VALUES (
    'CLEARING_COMPENSATION', '清分漏单补偿任务', 'transaction', 'clearingCompensation',
    '0 */5 * * * ?', 'DISTRIBUTED', 'CRON', 'SYNC', 'LOCAL', 'FIRE_ONCE',
    300, 0, 60, 0,
    JSON_OBJECT('mode', 'SHADOW_WRITE', 'limit', 200, 'maxPages', 20), 'ENABLED',
    '每五分钟滚动扫描最近十五分钟的漏清分、超时处理和到期失败交易，并通过清分服务幂等安排恢复。',
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
