-- 统一运营待办结算异常积压接入发布后检查。

SET NAMES utf8mb4;

-- 待办约束必须包含 SETTLEMENT_BACKLOG。
SELECT constraint_name, check_clause
FROM information_schema.check_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name = 'chk_admin_operational_work_item_state';

-- 同一结算批次只能存在一条统一待办；应返回 0 行。
SELECT source_key, COUNT(*) AS duplicate_count
FROM admin_operational_work_item
WHERE source_type = 'SETTLEMENT_BACKLOG'
GROUP BY source_key
HAVING COUNT(*) > 1;

-- 结算状态机托管负责人和终态；统一待办不得持有负责人或自身处理中状态；应返回 0。
SELECT COUNT(*) AS invalid_settlement_projection_count
FROM admin_operational_work_item
WHERE source_type = 'SETTLEMENT_BACKLOG'
  AND (owner_account_id IS NOT NULL
       OR owner_account_name IS NOT NULL
       OR work_status = 'PROCESSING');

-- 活动待办必须仍对应 MANUAL_REVIEW 批次；应返回 0。
SELECT COUNT(*) AS stale_active_settlement_count
FROM admin_operational_work_item item
LEFT JOIN settlement_batch batch ON batch.settlement_batch_no = item.source_key
WHERE item.source_type = 'SETTLEMENT_BACKLOG'
  AND item.work_status <> 'CLOSED'
  AND (batch.id IS NULL OR batch.batch_status <> 'MANUAL_REVIEW');

SELECT job_code, handler_code, description, status, deleted
FROM sys_job_task
WHERE job_code = 'OPERATIONAL_WORK_ITEM_SYNC';
