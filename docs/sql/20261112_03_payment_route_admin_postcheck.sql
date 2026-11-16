-- 支付路由配置中心迁移后检查。仅在 20261112_02 后、后续类型和品牌维度迁移前执行。
-- 后续迁移会替换索引并将 BANK_CARD 拆分为品牌维度，不得在全链路迁移完成后重复以本脚本判断状态。
-- 所有 missing_*、invalid_*、duplicate_* 应为0或无结果。

SELECT required_table.table_name AS missing_table
FROM (
    SELECT 'payment_route_rule' table_name
    UNION ALL SELECT 'payment_route_method'
    UNION ALL SELECT 'payment_route_transaction_type'
    UNION ALL SELECT 'payment_route_strategy'
    UNION ALL SELECT 'payment_route_candidate'
    UNION ALL SELECT 'payment_route_change_log'
) required_table
LEFT JOIN information_schema.tables actual
  ON actual.table_schema = DATABASE() AND actual.table_name = required_table.table_name
WHERE actual.table_name IS NULL;

SELECT required_column.table_name, required_column.column_name AS missing_column
FROM (
    SELECT 'payment_route_rule' table_name, 'rule_code' column_name
    UNION ALL SELECT 'payment_route_rule', 'rule_priority'
    UNION ALL SELECT 'payment_route_rule', 'unavailable_policy'
    UNION ALL SELECT 'payment_route_rule', 'rule_status'
    UNION ALL SELECT 'payment_route_rule', 'version'
    UNION ALL SELECT 'payment_route_strategy', 'strategy_type'
    UNION ALL SELECT 'payment_route_strategy', 'strategy_priority'
    UNION ALL SELECT 'payment_route_strategy', 'history_lookback_months'
    UNION ALL SELECT 'payment_route_method', 'payment_method'
    UNION ALL SELECT 'payment_route_transaction_type', 'transaction_type'
    UNION ALL SELECT 'payment_route_candidate', 'payment_method'
    UNION ALL SELECT 'payment_route_candidate', 'mid_config_id'
    UNION ALL SELECT 'payment_route_candidate', 'candidate_priority'
    UNION ALL SELECT 'payment_route_candidate', 'candidate_weight'
    UNION ALL SELECT 'payment_route_change_log', 'before_snapshot_json'
    UNION ALL SELECT 'payment_route_change_log', 'after_snapshot_json'
    UNION ALL SELECT 'payment_route_change_log', 'operation_reason'
) required_column
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = required_column.table_name
 AND actual.column_name = required_column.column_name
WHERE actual.column_name IS NULL;

SELECT required_index.table_name, required_index.index_name AS missing_index
FROM (
    SELECT 'payment_route_rule' table_name, 'uk_payment_route_rule_code_deleted' index_name
    UNION ALL SELECT 'payment_route_rule', 'idx_payment_route_rule_status_priority'
    UNION ALL SELECT 'payment_route_method', 'uk_payment_route_method_rule_method_deleted'
    UNION ALL SELECT 'payment_route_transaction_type', 'uk_payment_route_transaction_rule_type_deleted'
    UNION ALL SELECT 'payment_route_transaction_type', 'idx_payment_route_transaction_type_rule'
    UNION ALL SELECT 'payment_route_strategy', 'uk_payment_route_strategy_rule_type_deleted'
    UNION ALL SELECT 'payment_route_candidate', 'uk_payment_route_candidate_mid_deleted'
    UNION ALL SELECT 'payment_route_candidate', 'uk_payment_route_candidate_priority_deleted'
    UNION ALL SELECT 'payment_route_candidate', 'idx_payment_route_candidate_method'
    UNION ALL SELECT 'payment_route_change_log', 'idx_payment_route_change_rule_time'
) required_index
LEFT JOIN information_schema.statistics actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = required_index.table_name
 AND actual.index_name = required_index.index_name
WHERE actual.index_name IS NULL;

SELECT 1 - COUNT(*) AS missing_route_menu_count
FROM sys_menu
WHERE app_id = 1 AND menu_code = 'admin_payment_route_v1' AND deleted = 0
  AND parent_id = (SELECT id FROM sys_menu WHERE app_id = 1 AND menu_code = 'admin_channel_catalog_v1' AND deleted = 0 LIMIT 1)
  AND route_path = '/channel/route' AND component_path = 'channel/route';

SELECT required_permission.permission_code AS missing_permission
FROM (
    SELECT 'channel:route:list' permission_code
    UNION ALL SELECT 'channel:route:detail'
    UNION ALL SELECT 'channel:route:add'
    UNION ALL SELECT 'channel:route:edit'
    UNION ALL SELECT 'channel:route:copy'
    UNION ALL SELECT 'channel:route:publish'
    UNION ALL SELECT 'channel:route:disable'
    UNION ALL SELECT 'channel:route:remove'
    UNION ALL SELECT 'channel:route:validate'
    UNION ALL SELECT 'channel:route:history'
) required_permission
LEFT JOIN sys_permission permission
  ON permission.app_id = 1
 AND permission.permission_code = required_permission.permission_code
 AND permission.deleted = 0
WHERE permission.id IS NULL;

SELECT expected.permission_code AS invalid_permission_resource
FROM (
    SELECT 'channel:route:list' permission_code, 'POST' resource_method, '/admin/channel/routes/search' resource_path
    UNION ALL SELECT 'channel:route:detail', 'GET', '/admin/channel/routes/*'
    UNION ALL SELECT 'channel:route:add', 'POST', '/admin/channel/routes'
    UNION ALL SELECT 'channel:route:edit', 'PUT', '/admin/channel/routes/*'
    UNION ALL SELECT 'channel:route:copy', 'POST', '/admin/channel/routes/*/copy'
    UNION ALL SELECT 'channel:route:publish', 'POST', '/admin/channel/routes/*/publish'
    UNION ALL SELECT 'channel:route:disable', 'POST', '/admin/channel/routes/*/disable'
    UNION ALL SELECT 'channel:route:remove', 'DELETE', '/admin/channel/routes/*'
    UNION ALL SELECT 'channel:route:validate', 'GET', '/admin/channel/routes/*/validation'
    UNION ALL SELECT 'channel:route:history', 'POST', '/admin/channel/routes/*/change-logs/search'
) expected
JOIN sys_permission permission
  ON permission.app_id = 1
 AND permission.permission_code = expected.permission_code
 AND permission.deleted = 0
WHERE permission.resource_method <> expected.resource_method
   OR permission.resource_path <> expected.resource_path;

SELECT rule_code, COUNT(*) AS duplicate_rule_code_count
FROM payment_route_rule
WHERE deleted = 0
GROUP BY rule_code
HAVING COUNT(*) > 1;

SELECT rule_id, payment_method, candidate_priority, COUNT(*) AS duplicate_candidate_priority_count
FROM payment_route_candidate
WHERE deleted = 0
GROUP BY rule_id, payment_method, candidate_priority
HAVING COUNT(*) > 1;

SELECT rule_id, payment_method, mid_config_id, COUNT(*) AS duplicate_candidate_mid_count
FROM payment_route_candidate
WHERE deleted = 0
GROUP BY rule_id, payment_method, mid_config_id
HAVING COUNT(*) > 1;

SELECT COUNT(*) AS invalid_enabled_rule_count
FROM payment_route_rule rule_record
WHERE rule_record.deleted = 0
  AND rule_record.rule_status = 'ENABLED'
  AND (
      NOT EXISTS (
          SELECT 1 FROM payment_route_strategy strategy
          WHERE strategy.rule_id = rule_record.id AND strategy.strategy_type = rule_record.rule_type
            AND strategy.strategy_status = 1 AND strategy.deleted = 0
      )
      OR NOT EXISTS (
          SELECT 1 FROM payment_route_method method_record
          WHERE method_record.rule_id = rule_record.id AND method_record.deleted = 0
      )
      OR EXISTS (
          SELECT 1 FROM payment_route_candidate candidate
          WHERE candidate.rule_id = rule_record.id AND candidate.deleted = 0
            AND candidate.payment_method = 'ALL'
      )
      OR (rule_record.rule_type <> 'HISTORY_AFFINITY' AND NOT EXISTS (
          SELECT 1 FROM payment_route_candidate candidate
          WHERE candidate.rule_id = rule_record.id AND candidate.candidate_status = 1 AND candidate.deleted = 0
      ))
      OR (rule_record.rule_type = 'HISTORY_AFFINITY' AND EXISTS (
          SELECT 1 FROM payment_route_candidate candidate
          WHERE candidate.rule_id = rule_record.id AND candidate.deleted = 0
      ))
      OR (rule_record.rule_type <> 'HISTORY_AFFINITY' AND EXISTS (
          SELECT 1
          FROM payment_route_method configured
          WHERE configured.rule_id = rule_record.id AND configured.deleted = 0
            AND NOT EXISTS (
                SELECT 1 FROM payment_route_candidate enabled_candidate
                WHERE enabled_candidate.rule_id = configured.rule_id
                  AND enabled_candidate.payment_method = configured.payment_method
                  AND enabled_candidate.candidate_status = 1
                  AND enabled_candidate.deleted = 0
            )
      ))
      OR (rule_record.rule_type = 'FIXED' AND EXISTS (
          SELECT configured.payment_method
          FROM payment_route_method configured
          LEFT JOIN payment_route_candidate candidate
            ON candidate.rule_id = configured.rule_id
           AND candidate.payment_method = configured.payment_method
           AND candidate.candidate_status = 1
           AND candidate.deleted = 0
          WHERE configured.rule_id = rule_record.id AND configured.deleted = 0
          GROUP BY configured.payment_method
          HAVING COUNT(candidate.id) <> 1
      ))
      OR (rule_record.rule_type = 'WEIGHT' AND EXISTS (
          SELECT candidate.payment_method
          FROM payment_route_candidate candidate
          WHERE candidate.rule_id = rule_record.id
            AND candidate.candidate_status = 1 AND candidate.deleted = 0
          GROUP BY candidate.payment_method
          HAVING SUM(candidate.candidate_weight) <> 100.00 OR SUM(candidate.candidate_weight) IS NULL
      ))
  );

SELECT role.role_code, required.permission_code AS missing_admin_role_permission
FROM sys_role role
JOIN (
    SELECT 'channel:route:list' permission_code
    UNION ALL SELECT 'channel:route:detail'
    UNION ALL SELECT 'channel:route:add'
    UNION ALL SELECT 'channel:route:edit'
    UNION ALL SELECT 'channel:route:copy'
    UNION ALL SELECT 'channel:route:publish'
    UNION ALL SELECT 'channel:route:disable'
    UNION ALL SELECT 'channel:route:remove'
    UNION ALL SELECT 'channel:route:validate'
    UNION ALL SELECT 'channel:route:history'
) required ON 1 = 1
JOIN sys_permission permission
  ON permission.app_id = role.app_id AND permission.permission_code = required.permission_code AND permission.deleted = 0
LEFT JOIN sys_role_permission grant_record
  ON grant_record.app_id = role.app_id AND grant_record.role_id = role.id
 AND grant_record.permission_id = permission.id AND grant_record.deleted = 0
WHERE role.app_id = 1 AND (role.id = 1 OR role.role_code IN ('ADMIN', 'ADMIN_OPERATOR'))
  AND role.deleted = 0 AND grant_record.id IS NULL;
