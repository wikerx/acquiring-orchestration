-- 汇率波动与预警迁移后检查。所有 missing_*、invalid_*、duplicate_* 应为 0。

SELECT required_table.table_name AS missing_table
FROM (
    SELECT 'exchange_rate_alert_rule' table_name
    UNION ALL SELECT 'exchange_rate_alert_event'
    UNION ALL SELECT 'exchange_rate_alert_evaluation'
) required_table
LEFT JOIN information_schema.tables actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = required_table.table_name
WHERE actual.table_name IS NULL;

SELECT COUNT(*) AS missing_volatility_index
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'exchange_business_rate'
  AND index_name = 'idx_exchange_business_rate_volatility'
HAVING COUNT(*) = 0;

SELECT COUNT(*) AS invalid_system_rule_count
FROM exchange_rate_alert_rule
WHERE rule_code IN ('SYSTEM_TRANSACTION_72H_3PCT', 'SYSTEM_SETTLEMENT_72H_3PCT')
  AND (
      deleted <> 0 OR system_mandatory <> 1 OR rule_status <> 1
      OR base_currency <> 'ALL' OR quote_currency <> 'ALL'
      OR window_minutes <> 4320 OR trigger_threshold <> 3.0000
      OR recovery_threshold <> 2.0000 OR recovery_consecutive_count <> 2
      OR minimum_sample_count < 2 OR alert_level <> 'WARNING'
      OR (rule_code = 'SYSTEM_TRANSACTION_72H_3PCT'
          AND (rule_name <> '交易汇率72小时强制波动预警'
               OR remark <> '系统强制基线：波动率大于3%触发，连续两次低于2%恢复。'))
      OR (rule_code = 'SYSTEM_SETTLEMENT_72H_3PCT'
          AND (rule_name <> '结算汇率72小时强制波动预警'
               OR remark <> '系统强制基线：波动率大于3%触发，连续两次低于2%恢复。'))
  );

SELECT 2 - COUNT(*) AS missing_system_rule_count
FROM exchange_rate_alert_rule
WHERE rule_code IN ('SYSTEM_TRANSACTION_72H_3PCT', 'SYSTEM_SETTLEMENT_72H_3PCT')
  AND deleted = 0;

SELECT COUNT(*) AS invalid_system_job_count
FROM sys_job_task
WHERE job_code = 'EXCHANGE_RATE_VOLATILITY_MONITOR'
  AND deleted = 0
  AND (
      job_name <> '汇率波动预警评估任务'
      OR job_group <> 'exchange'
      OR handler_code <> 'exchangeRateVolatilityMonitorJob'
      OR scheduler_mode <> 'DISTRIBUTED'
      OR execute_mode <> 'SYNC'
      OR allow_concurrent <> 0
      OR description <> '每5分钟调用 service-admin 幂等评估交易汇率和结算汇率波动；系统任务禁止删除或更换处理器。'
  );

SELECT 1 - COUNT(*) AS missing_system_job_count
FROM sys_job_task
WHERE job_code = 'EXCHANGE_RATE_VOLATILITY_MONITOR'
  AND deleted = 0;

SELECT required_permission.permission_code AS missing_permission
FROM (
    SELECT 'exchange:volatility:list' permission_code
    UNION ALL SELECT 'exchange:volatility:query'
    UNION ALL SELECT 'exchange:volatility:export'
    UNION ALL SELECT 'exchange:alert-event:list'
    UNION ALL SELECT 'exchange:alert-event:query'
    UNION ALL SELECT 'exchange:alert-event:ack'
    UNION ALL SELECT 'exchange:alert-event:close'
    UNION ALL SELECT 'exchange:alert-event:export'
    UNION ALL SELECT 'exchange:alert-rule:list'
    UNION ALL SELECT 'exchange:alert-rule:query'
    UNION ALL SELECT 'exchange:alert-rule:add'
    UNION ALL SELECT 'exchange:alert-rule:edit'
    UNION ALL SELECT 'exchange:alert-rule:remove'
    UNION ALL SELECT 'exchange:alert-rule:start'
    UNION ALL SELECT 'exchange:alert-rule:stop'
    UNION ALL SELECT 'exchange:alert-rule:import'
    UNION ALL SELECT 'exchange:alert-rule:export'
) required_permission
LEFT JOIN sys_permission permission
  ON permission.app_id = 1
 AND permission.permission_code = required_permission.permission_code
 AND permission.deleted = 0
WHERE permission.id IS NULL;

SELECT COUNT(*) AS invalid_menu_name_count
FROM sys_menu menu
JOIN (
    SELECT 'exchange_rate_volatility' menu_code, '汇率波动' menu_name
    UNION ALL SELECT 'exchange_rate_alert_event', '汇率预警事件'
    UNION ALL SELECT 'exchange_rate_alert_rule', '汇率预警规则'
    UNION ALL SELECT 'exchange_rate_volatility_query', '汇率波动详情'
    UNION ALL SELECT 'exchange_rate_volatility_export', '汇率波动导出'
    UNION ALL SELECT 'exchange_rate_alert_event_query', '汇率预警事件详情'
    UNION ALL SELECT 'exchange_rate_alert_event_ack', '汇率预警事件确认'
    UNION ALL SELECT 'exchange_rate_alert_event_close', '汇率预警事件关闭'
    UNION ALL SELECT 'exchange_rate_alert_event_export', '汇率预警事件导出'
    UNION ALL SELECT 'exchange_rate_alert_rule_query', '汇率预警规则详情'
    UNION ALL SELECT 'exchange_rate_alert_rule_add', '汇率预警规则新增'
    UNION ALL SELECT 'exchange_rate_alert_rule_edit', '汇率预警规则修改'
    UNION ALL SELECT 'exchange_rate_alert_rule_remove', '汇率预警规则删除'
    UNION ALL SELECT 'exchange_rate_alert_rule_start', '汇率预警规则启用'
    UNION ALL SELECT 'exchange_rate_alert_rule_stop', '汇率预警规则停用'
    UNION ALL SELECT 'exchange_rate_alert_rule_import', '汇率预警规则导入'
    UNION ALL SELECT 'exchange_rate_alert_rule_export', '汇率预警规则导出'
) expected ON expected.menu_code = menu.menu_code
WHERE menu.app_id = 1
  AND menu.deleted = 0
  AND menu.menu_name <> expected.menu_name;

SELECT COUNT(*) AS invalid_permission_name_count
FROM sys_permission permission
JOIN (
    SELECT 'exchange:volatility:list' permission_code, '汇率波动查询' permission_name
    UNION ALL SELECT 'exchange:volatility:query', '汇率波动详情'
    UNION ALL SELECT 'exchange:volatility:export', '汇率波动导出'
    UNION ALL SELECT 'exchange:alert-event:list', '汇率预警事件查询'
    UNION ALL SELECT 'exchange:alert-event:query', '汇率预警事件详情'
    UNION ALL SELECT 'exchange:alert-event:ack', '汇率预警事件确认'
    UNION ALL SELECT 'exchange:alert-event:close', '汇率预警事件关闭'
    UNION ALL SELECT 'exchange:alert-event:export', '汇率预警事件导出'
    UNION ALL SELECT 'exchange:alert-rule:list', '汇率预警规则查询'
    UNION ALL SELECT 'exchange:alert-rule:query', '汇率预警规则详情'
    UNION ALL SELECT 'exchange:alert-rule:add', '汇率预警规则新增'
    UNION ALL SELECT 'exchange:alert-rule:edit', '汇率预警规则修改'
    UNION ALL SELECT 'exchange:alert-rule:remove', '汇率预警规则删除'
    UNION ALL SELECT 'exchange:alert-rule:start', '汇率预警规则启用'
    UNION ALL SELECT 'exchange:alert-rule:stop', '汇率预警规则停用'
    UNION ALL SELECT 'exchange:alert-rule:import', '汇率预警规则导入'
    UNION ALL SELECT 'exchange:alert-rule:export', '汇率预警规则导出'
) expected ON expected.permission_code = permission.permission_code
WHERE permission.app_id = 1
  AND permission.deleted = 0
  AND (permission.permission_name <> expected.permission_name
       OR permission.description <> expected.permission_name);

SELECT active_event_key, COUNT(*) AS duplicate_active_event_count
FROM exchange_rate_alert_event
WHERE active_event_key IS NOT NULL
GROUP BY active_event_key
HAVING COUNT(*) > 1;

SELECT COUNT(*) AS invalid_rate_value_count
FROM exchange_rate_alert_event
WHERE deleted = 0
  AND (
      minimum_rate <= 0 OR maximum_rate <= 0 OR latest_rate <= 0
      OR maximum_rate < minimum_rate OR volatility_rate < 0 OR maximum_volatility_rate < 0
  );
