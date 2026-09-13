-- 修复剩余责任已归零但仍停留在 HELD 的保证金聚合状态。
-- 仅处理同一结算批次中存在不可变 RELEASE 动作的记录。
-- 执行前必须先审阅 20260912_02_reserve_status_repair_postcheck.sql，并在目标环境完成备份。

SET NAMES utf8mb4;

START TRANSACTION;

UPDATE merchant_reserve_item reserve_item
SET reserve_item.reserve_status = 'RELEASED',
    reserve_item.version = reserve_item.version + 1,
    reserve_item.update_time = CURRENT_TIMESTAMP(3)
WHERE reserve_item.reserve_status = 'HELD'
  AND reserve_item.release_batch_no IS NOT NULL
  AND reserve_item.released_amount > 0
  AND reserve_item.retained_amount + reserve_item.debit_adjustment_amount
      - reserve_item.returned_amount - reserve_item.released_amount
      - reserve_item.credit_adjustment_amount - reserve_item.reversed_amount = 0
  AND EXISTS (
      SELECT 1
      FROM merchant_reserve_action reserve_action
      WHERE reserve_action.reserve_item_id = reserve_item.id
        AND reserve_action.settlement_batch_no = reserve_item.release_batch_no
        AND reserve_action.action_type = 'RELEASE'
  );

COMMIT;
