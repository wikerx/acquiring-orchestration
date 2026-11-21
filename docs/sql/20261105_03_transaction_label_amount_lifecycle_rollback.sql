-- 交易金额双口径生命周期迁移回滚。
-- 仅允许在迁移后尚未恢复 service-payment、回调、勾兑和补偿写入时执行。
-- 回滚会恢复历史 VOID 被迁移改写的既有字段，并删除本次新增的标签生命周期列。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `precheck_transaction_label_amount_lifecycle_rollback_20261105`;
DELIMITER $$
CREATE PROCEDURE `precheck_transaction_label_amount_lifecycle_rollback_20261105`()
BEGIN
    DECLARE actual_column_count INT DEFAULT 0;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'transaction_label_amount_void_backup_20261105'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'label amount lifecycle rollback requires the 20261105 VOID backup table';
    END IF;

    SELECT COUNT(*) INTO actual_column_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND (
          (TABLE_NAME IN ('transaction_order', 'transaction_order_202603', 'transaction_order_202604')
           AND COLUMN_NAME IN (
               'label_currency_exponent', 'label_authorized_amount', 'label_authorized_cancel_amount',
               'label_captured_amount', 'label_refunded_amount', 'label_chargeback_amount',
               'label_available_capture_amount', 'label_available_refund_amount'
           ))
          OR (TABLE_NAME IN ('transaction_operation', 'transaction_operation_202603', 'transaction_operation_202604')
              AND COLUMN_NAME = 'label_currency_exponent')
          OR (TABLE_NAME IN (
                  'transaction_amount_change_log', 'transaction_amount_change_log_202603',
                  'transaction_amount_change_log_202604'
              )
              AND COLUMN_NAME IN (
                  'label_amount_currency', 'label_change_amount', 'label_authorized_before',
                  'label_authorized_after', 'label_captured_before', 'label_captured_after',
                  'label_refunded_before', 'label_refunded_after', 'label_available_capture_before',
                  'label_available_capture_after', 'label_available_refund_before',
                  'label_available_refund_after'
              ))
      );
    IF actual_column_count <> 63 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'label amount lifecycle rollback found an incomplete migration; inspect schema before rollback';
    END IF;
END$$
DELIMITER ;

CALL `precheck_transaction_label_amount_lifecycle_rollback_20261105`();
DROP PROCEDURE `precheck_transaction_label_amount_lifecycle_rollback_20261105`;

UPDATE transaction_operation_202603 o
JOIN transaction_label_amount_void_backup_20261105 b
  ON b.shard_code = '202603' AND b.operation_row_id = o.id
SET o.label_currency = b.label_currency,
    o.label_amount = b.label_amount,
    o.transaction_rate = b.transaction_rate,
    o.dcc_enabled = b.dcc_enabled,
    o.edc_enabled = b.edc_enabled;

UPDATE transaction_operation_202604 o
JOIN transaction_label_amount_void_backup_20261105 b
  ON b.shard_code = '202604' AND b.operation_row_id = o.id
SET o.label_currency = b.label_currency,
    o.label_amount = b.label_amount,
    o.transaction_rate = b.transaction_rate,
    o.dcc_enabled = b.dcc_enabled,
    o.edc_enabled = b.edc_enabled;

ALTER TABLE transaction_amount_change_log
    DROP COLUMN label_amount_currency,
    DROP COLUMN label_change_amount,
    DROP COLUMN label_authorized_before,
    DROP COLUMN label_authorized_after,
    DROP COLUMN label_captured_before,
    DROP COLUMN label_captured_after,
    DROP COLUMN label_refunded_before,
    DROP COLUMN label_refunded_after,
    DROP COLUMN label_available_capture_before,
    DROP COLUMN label_available_capture_after,
    DROP COLUMN label_available_refund_before,
    DROP COLUMN label_available_refund_after;

ALTER TABLE transaction_amount_change_log_202603
    DROP COLUMN label_amount_currency,
    DROP COLUMN label_change_amount,
    DROP COLUMN label_authorized_before,
    DROP COLUMN label_authorized_after,
    DROP COLUMN label_captured_before,
    DROP COLUMN label_captured_after,
    DROP COLUMN label_refunded_before,
    DROP COLUMN label_refunded_after,
    DROP COLUMN label_available_capture_before,
    DROP COLUMN label_available_capture_after,
    DROP COLUMN label_available_refund_before,
    DROP COLUMN label_available_refund_after;

ALTER TABLE transaction_amount_change_log_202604
    DROP COLUMN label_amount_currency,
    DROP COLUMN label_change_amount,
    DROP COLUMN label_authorized_before,
    DROP COLUMN label_authorized_after,
    DROP COLUMN label_captured_before,
    DROP COLUMN label_captured_after,
    DROP COLUMN label_refunded_before,
    DROP COLUMN label_refunded_after,
    DROP COLUMN label_available_capture_before,
    DROP COLUMN label_available_capture_after,
    DROP COLUMN label_available_refund_before,
    DROP COLUMN label_available_refund_after;

ALTER TABLE transaction_operation DROP COLUMN label_currency_exponent;
ALTER TABLE transaction_operation_202603 DROP COLUMN label_currency_exponent;
ALTER TABLE transaction_operation_202604 DROP COLUMN label_currency_exponent;

ALTER TABLE transaction_order
    DROP COLUMN label_currency_exponent,
    DROP COLUMN label_authorized_amount,
    DROP COLUMN label_authorized_cancel_amount,
    DROP COLUMN label_captured_amount,
    DROP COLUMN label_refunded_amount,
    DROP COLUMN label_chargeback_amount,
    DROP COLUMN label_available_capture_amount,
    DROP COLUMN label_available_refund_amount;

ALTER TABLE transaction_order_202603
    DROP COLUMN label_currency_exponent,
    DROP COLUMN label_authorized_amount,
    DROP COLUMN label_authorized_cancel_amount,
    DROP COLUMN label_captured_amount,
    DROP COLUMN label_refunded_amount,
    DROP COLUMN label_chargeback_amount,
    DROP COLUMN label_available_capture_amount,
    DROP COLUMN label_available_refund_amount;

ALTER TABLE transaction_order_202604
    DROP COLUMN label_currency_exponent,
    DROP COLUMN label_authorized_amount,
    DROP COLUMN label_authorized_cancel_amount,
    DROP COLUMN label_captured_amount,
    DROP COLUMN label_refunded_amount,
    DROP COLUMN label_chargeback_amount,
    DROP COLUMN label_available_capture_amount,
    DROP COLUMN label_available_refund_amount;

DROP TABLE transaction_label_amount_void_backup_20261105;

SELECT 'ROLLED_BACK' AS rollback_result;
