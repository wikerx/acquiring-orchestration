-- 统一运营待办高风险事件接入发布后检查。

SET NAMES utf8mb4;

-- 待办约束必须包含 HIGH_RISK_EVENT。
SELECT constraint_name, check_clause
FROM information_schema.check_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name = 'chk_admin_operational_work_item_state';

-- 同一风控记录只能存在一条统一待办；应返回 0 行。
SELECT source_key, COUNT(*) AS duplicate_count
FROM admin_operational_work_item
WHERE source_type = 'HIGH_RISK_EVENT'
GROUP BY source_key
HAVING COUNT(*) > 1;

-- 活动待办必须仍对应拒绝决策或 HIGH/CRITICAL 风险事件；应返回 0。
SELECT COUNT(*) AS invalid_high_risk_projection_count
FROM admin_operational_work_item item
LEFT JOIN risk_evaluation_record evaluation ON evaluation.risk_record_no = item.source_key
WHERE item.source_type = 'HIGH_RISK_EVENT'
  AND item.work_status <> 'CLOSED'
  AND (evaluation.id IS NULL
       OR (evaluation.decision_result <> 'REJECT'
           AND evaluation.risk_level NOT IN ('HIGH', 'CRITICAL'))
       OR (evaluation.risk_level = 'CRITICAL' AND item.priority <> 'CRITICAL')
       OR (evaluation.risk_level <> 'CRITICAL' AND item.priority <> 'HIGH'));

-- 高风险事件由统一待办闭环，不得被误标记为来源终态自动关闭；应返回 0。
SELECT COUNT(*) AS invalid_high_risk_close_reason_count
FROM admin_operational_work_item
WHERE source_type = 'HIGH_RISK_EVENT'
  AND work_status = 'CLOSED'
  AND close_reason <> 'MANUAL_COMPLETE';

SELECT job_code, handler_code, description, status, deleted
FROM sys_job_task
WHERE job_code = 'OPERATIONAL_WORK_ITEM_SYNC';
