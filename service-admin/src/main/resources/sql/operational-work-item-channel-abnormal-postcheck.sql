-- 统一运营待办渠道勾兑异常接入发布后检查。

-- 两条约束必须分别包含 CHANNEL_MATCH_ABNORMAL 和 SOURCE_SYNC。
SELECT constraint_name, check_clause
FROM information_schema.check_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name IN (
      'chk_admin_operational_work_item_state',
      'chk_admin_operational_work_item_history_action'
  )
ORDER BY constraint_name;

-- 同一渠道异常案件只能存在一条统一待办；应返回 0 行。
SELECT source_key, COUNT(*) AS duplicate_count
FROM admin_operational_work_item
WHERE source_type = 'CHANNEL_MATCH_ABNORMAL'
GROUP BY source_key
HAVING COUNT(*) > 1;

-- 来源托管案件不得出现只有负责人 ID 或只有负责人名称的半状态；应返回 0。
SELECT COUNT(*) AS invalid_owner_projection_count
FROM admin_operational_work_item
WHERE source_type = 'CHANNEL_MATCH_ABNORMAL'
  AND ((owner_account_id IS NULL AND owner_account_name IS NOT NULL)
       OR (owner_account_id IS NOT NULL AND owner_account_name IS NULL));

SELECT job_code, handler_code, description, status, deleted
FROM sys_job_task
WHERE job_code = 'OPERATIONAL_WORK_ITEM_SYNC';
