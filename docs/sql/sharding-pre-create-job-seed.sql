-- 分表物理表预创建任务种子 SQL。
-- 本脚本只新增任务定义，不直接触发任务执行；真实 DDL 由任务运行时根据 dryRun 参数决定。

INSERT INTO sys_job_task (
    job_code,
    job_name,
    job_group,
    handler_code,
    cron_expression,
    scheduler_mode,
    trigger_mode,
    execute_mode,
    route_strategy,
    misfire_strategy,
    timeout_seconds,
    retry_count,
    retry_interval_seconds,
    allow_concurrent,
    params,
    status,
    description,
    next_trigger_time,
    deleted,
    create_by,
    create_time,
    update_by,
    update_time
)
SELECT 'sharding_table_pre_create',
       '分表物理表预创建任务',
       'sharding',
       'sharding.table.pre-create',
       '0 10 2 * * ?',
       'DISTRIBUTED',
       'CRON',
       'SYNC',
       'LOCAL',
       'IGNORE',
       600,
       0,
       60,
       0,
       '{"dryRun":false,"includeCurrentQuarter":true,"includeNextQuarter":true,"logicalTables":[],"compareSchemaIfExists":true}',
       'DISABLED',
       '按季度预创建当前季度和下一季度测试分表物理表；首次验证建议手动 dryRun 后再启用。',
       NULL,
       0,
       'system',
       CURRENT_TIMESTAMP(3),
       'system',
       CURRENT_TIMESTAMP(3)
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_job_task
    WHERE job_code = 'sharding_table_pre_create'
      AND deleted = 0
);
