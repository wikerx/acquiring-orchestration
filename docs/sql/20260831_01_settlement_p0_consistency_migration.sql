-- 结算 P0 一致性增量迁移：保证金调整资金化、调整冲正和批次引用约束。
-- 前置：20260826_08_settlement_posting_migration.sql 已完成；执行后运行 20260831_02 后检。

SET NAMES utf8mb4;

-- 兼容不同基线中可能存在的同名 CHECK，统一由本迁移重建目标约束。
DROP PROCEDURE IF EXISTS drop_settlement_p0_check;
DELIMITER $$
CREATE PROCEDURE drop_settlement_p0_check(
    IN target_table VARCHAR(64),
    IN target_constraint VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = target_table
          AND constraint_name = target_constraint
          AND constraint_type = 'CHECK'
    ) THEN
        SET @drop_check_sql = CONCAT(
            'ALTER TABLE `', target_table, '` DROP CHECK `', target_constraint, '`'
        );
        PREPARE drop_check_stmt FROM @drop_check_sql;
        EXECUTE drop_check_stmt;
        DEALLOCATE PREPARE drop_check_stmt;
    END IF;
END$$
DELIMITER ;

CALL drop_settlement_p0_check('merchant_reserve_item', 'chk_reserve_amount');
CALL drop_settlement_p0_check('merchant_reserve_item', 'chk_reserve_status');
CALL drop_settlement_p0_check('merchant_reserve_action', 'chk_reserve_action_value');
CALL drop_settlement_p0_check('settlement_batch', 'chk_settlement_batch_enum');
DROP PROCEDURE drop_settlement_p0_check;

-- 调整累计始终使用保证金原标签币种；剩余责任由非负约束和守恒上限共同保护。
ALTER TABLE merchant_reserve_item
    ADD COLUMN debit_adjustment_amount DECIMAL(24,8) NOT NULL DEFAULT 0
        COMMENT '借方调整累计；增加原标签币种保证金责任' AFTER retained_amount,
    ADD COLUMN credit_adjustment_amount DECIMAL(24,8) NOT NULL DEFAULT 0
        COMMENT '贷方调整累计；减少原标签币种保证金责任' AFTER debit_adjustment_amount,
    ADD CONSTRAINT chk_reserve_amount CHECK (
        retained_amount > 0
        AND debit_adjustment_amount >= 0
        AND credit_adjustment_amount >= 0
        AND returned_amount >= 0
        AND released_amount >= 0
        AND reversed_amount >= 0
        AND returned_amount + released_amount + credit_adjustment_amount + reversed_amount
            <= retained_amount + debit_adjustment_amount
    ),
    ADD CONSTRAINT chk_reserve_status CHECK (
        reserve_status IN (
            'HELD', 'PARTIALLY_RETURNED', 'RETURNED', 'RELEASABLE', 'RELEASED',
            'FROZEN', 'DEDUCTED', 'REVERSED', 'ADJUSTED'
        )
    );

-- 先以可空列完成历史动作方向和冲正引用回填，再收口为不可空及数据库唯一约束。
ALTER TABLE merchant_reserve_action
    ADD COLUMN direction VARCHAR(8) NULL COMMENT 'DEBIT 增加责任、CREDIT 减少责任' AFTER action_type,
    ADD COLUMN reversal_of_action_id BIGINT NULL COMMENT '冲正动作引用的原保证金动作ID'
        AFTER source_reserve_detail_no;

UPDATE merchant_reserve_action
SET direction = CASE
    WHEN action_type IN ('HOLD', 'REVERSAL_RETURN', 'REVERSAL_RELEASE') THEN 'DEBIT'
    WHEN action_type IN ('RETURN', 'RELEASE', 'REVERSAL_HOLD') THEN 'CREDIT'
    ELSE direction
END
WHERE direction IS NULL;

UPDATE merchant_reserve_action reversal_action
JOIN merchant_reserve_action original_action
  ON original_action.reserve_action_no = reversal_action.source_reserve_detail_no
SET reversal_action.reversal_of_action_id = original_action.id
WHERE reversal_action.action_type IN ('REVERSAL_HOLD', 'REVERSAL_RETURN', 'REVERSAL_RELEASE')
  AND reversal_action.reversal_of_action_id IS NULL;

-- 本查询必须返回 0；否则先修复无法关联原动作的历史冲正，再继续执行后续 ALTER。
SELECT COUNT(*) AS unresolved_reserve_action_count
FROM merchant_reserve_action
WHERE direction IS NULL
   OR (action_type IN (
          'REVERSAL_HOLD', 'REVERSAL_RETURN', 'REVERSAL_RELEASE', 'REVERSAL_ADJUSTMENT'
       ) AND reversal_of_action_id IS NULL);

ALTER TABLE merchant_reserve_action
    MODIFY COLUMN direction VARCHAR(8) NOT NULL COMMENT 'DEBIT 增加责任、CREDIT 减少责任',
    ADD UNIQUE KEY uk_reserve_action_reversal_ref (reversal_of_action_id),
    ADD CONSTRAINT chk_reserve_action_value CHECK (
        amount > 0
        AND direction IN ('DEBIT', 'CREDIT')
        AND action_type IN (
            'HOLD', 'RETURN', 'RELEASE', 'ADJUSTMENT',
            'REVERSAL_HOLD', 'REVERSAL_RETURN', 'REVERSAL_RELEASE', 'REVERSAL_ADJUSTMENT'
        )
        AND ((action_type IN ('HOLD', 'RETURN', 'RELEASE', 'ADJUSTMENT')
              AND reversal_of_action_id IS NULL)
             OR (action_type IN (
                    'REVERSAL_HOLD', 'REVERSAL_RETURN', 'REVERSAL_RELEASE', 'REVERSAL_ADJUSTMENT'
                 ) AND reversal_of_action_id IS NOT NULL))
        AND ((action_type = 'HOLD' AND direction = 'DEBIT')
             OR (action_type IN ('RETURN', 'RELEASE') AND direction = 'CREDIT')
             OR (action_type = 'REVERSAL_HOLD' AND direction = 'CREDIT')
             OR (action_type IN ('REVERSAL_RETURN', 'REVERSAL_RELEASE') AND direction = 'DEBIT')
             OR action_type IN ('ADJUSTMENT', 'REVERSAL_ADJUSTMENT'))
    );

-- ADJUSTMENT 直接引用原 HOLD 清分明细，不伪造原结算批次引用；只有 REVERSAL 必须引用原批次。
ALTER TABLE settlement_batch
    ADD CONSTRAINT chk_settlement_batch_enum CHECK (
        batch_type IN ('REGULAR', 'RESERVE_RELEASE', 'REVERSAL', 'ADJUSTMENT')
        AND batch_status IN (
            'CREATED', 'CLAIMING', 'CLAIMED', 'RATE_LOCKED', 'CALCULATING', 'CALCULATED',
            'POSTING', 'POSTED', 'FAILED_RETRYABLE', 'MANUAL_REVIEW', 'CANCELLED',
            'REVERSING', 'REVERSED'
        )
        AND ((batch_type = 'REVERSAL' AND original_batch_no IS NOT NULL)
             OR (batch_type IN ('REGULAR', 'RESERVE_RELEASE', 'ADJUSTMENT')
                 AND original_batch_no IS NULL))
    );
