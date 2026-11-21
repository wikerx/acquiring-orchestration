-- 2027-Q1 分表结构修复受控回滚。
-- 仅限 202701 尚未承载业务数据时使用；本脚本不会自动执行。
-- 执行前必须先从 Nacos 正式节点移除 202701，停止所有交易写服务并完成配置回滚重启。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `precheck_future_quarter_schema_rollback_20261108`;
DELIMITER $$
CREATE PROCEDURE `precheck_future_quarter_schema_rollback_20261108`()
BEGIN
    DECLARE required_table_count INT DEFAULT 0;
    DECLARE target_row_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO required_table_count
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN (
          'transaction_order_202701',
          'transaction_operation_202701',
          'transaction_amount_change_log_202701',
          'risk_evaluation_record_202701',
          'risk_evaluation_hit_detail_202701'
      );
    IF required_table_count <> 5 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter rollback requires the complete repaired table set';
    END IF;

    SELECT (SELECT COUNT(*) FROM transaction_order_202701)
         + (SELECT COUNT(*) FROM transaction_operation_202701)
         + (SELECT COUNT(*) FROM transaction_amount_change_log_202701)
         + (SELECT COUNT(*) FROM risk_evaluation_record_202701)
         + (SELECT COUNT(*) FROM risk_evaluation_hit_detail_202701)
    INTO target_row_count;
    IF target_row_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter rollback refuses non-empty 202701 tables';
    END IF;
END$$
DELIMITER ;

CALL `precheck_future_quarter_schema_rollback_20261108`();
DROP PROCEDURE `precheck_future_quarter_schema_rollback_20261108`;

DROP TABLE `risk_evaluation_hit_detail_202701`;
DROP TABLE `risk_evaluation_record_202701`;

ALTER TABLE `transaction_amount_change_log_202701`
    DROP COLUMN `label_available_refund_after`,
    DROP COLUMN `label_available_refund_before`,
    DROP COLUMN `label_available_capture_after`,
    DROP COLUMN `label_available_capture_before`,
    DROP COLUMN `label_refunded_after`,
    DROP COLUMN `label_refunded_before`,
    DROP COLUMN `label_captured_after`,
    DROP COLUMN `label_captured_before`,
    DROP COLUMN `label_authorized_after`,
    DROP COLUMN `label_authorized_before`,
    DROP COLUMN `label_change_amount`,
    DROP COLUMN `label_amount_currency`;

ALTER TABLE `transaction_operation_202701`
    DROP COLUMN `label_currency_exponent`;

ALTER TABLE `transaction_order_202701`
    DROP COLUMN `label_available_refund_amount`,
    DROP COLUMN `label_available_capture_amount`,
    DROP COLUMN `label_chargeback_amount`,
    DROP COLUMN `label_refunded_amount`,
    DROP COLUMN `label_captured_amount`,
    DROP COLUMN `label_authorized_cancel_amount`,
    DROP COLUMN `label_authorized_amount`,
    DROP COLUMN `label_currency_exponent`;

SELECT 'FUTURE_QUARTER_SCHEMA_REPAIR_ROLLBACK' AS check_name, 1 AS executed;
