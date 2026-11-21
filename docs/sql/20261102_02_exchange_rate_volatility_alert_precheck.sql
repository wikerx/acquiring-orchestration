-- 汇率波动与预警上线前检查。
-- 所有结果都应由发布人员留档；任何 missing_* 或 duplicate_* 非零时必须停止迁移。

SELECT 1 - COUNT(*) AS missing_exchange_business_rate
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'exchange_business_rate';

SELECT COUNT(*) AS missing_admin_rbac_tables
FROM (
    SELECT 'sys_menu' table_name
    UNION ALL SELECT 'sys_permission'
    UNION ALL SELECT 'sys_role_menu'
    UNION ALL SELECT 'sys_role_permission'
    UNION ALL SELECT 'sys_job_task'
) required_table
LEFT JOIN information_schema.tables actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = required_table.table_name
WHERE actual.table_name IS NULL;

SELECT job_code, COUNT(*) AS duplicate_count
FROM sys_job_task
WHERE job_code = 'EXCHANGE_RATE_VOLATILITY_MONITOR'
  AND deleted = 0
GROUP BY job_code
HAVING COUNT(*) > 1;

SELECT permission_code, COUNT(*) AS duplicate_count
FROM sys_permission
WHERE permission_code LIKE 'exchange:volatility:%'
   OR permission_code LIKE 'exchange:alert-event:%'
   OR permission_code LIKE 'exchange:alert-rule:%'
GROUP BY app_id, permission_code, deleted
HAVING COUNT(*) > 1;

SELECT COUNT(*) AS invalid_business_rate_rows
FROM exchange_business_rate
WHERE deleted = 0
  AND (
      rate_type NOT IN ('TRANSACTION_RATE', 'SETTLEMENT_RATE')
      OR base_currency NOT REGEXP '^[A-Z]{3}$'
      OR quote_currency NOT REGEXP '^[A-Z]{3}$'
      OR final_rate <= 0
      OR effective_time IS NULL
  );

SELECT rate_type, base_currency, quote_currency, COUNT(*) AS sample_count,
       MIN(effective_time) AS first_effective_time,
       MAX(effective_time) AS last_effective_time
FROM exchange_business_rate
WHERE deleted = 0
  AND rate_status IN ('ENABLED', 'EXPIRED')
GROUP BY rate_type, base_currency, quote_currency
ORDER BY rate_type, base_currency, quote_currency;
