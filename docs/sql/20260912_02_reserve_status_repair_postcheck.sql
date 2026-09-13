-- 全额释放保证金状态修复的只读后检脚本，不包含任何数据变更语句。

SET NAMES utf8mb4;

SELECT COUNT(1) AS fully_released_still_held_count
FROM merchant_reserve_item reserve_item
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

SELECT COUNT(1) AS released_with_nonzero_responsibility_count
FROM merchant_reserve_item reserve_item
WHERE reserve_item.reserve_status = 'RELEASED'
  AND reserve_item.retained_amount + reserve_item.debit_adjustment_amount
      - reserve_item.returned_amount - reserve_item.released_amount
      - reserve_item.credit_adjustment_amount - reserve_item.reversed_amount <> 0;
