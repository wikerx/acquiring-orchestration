-- 2027-Q1 未来季度交易分表结构修复前检。
-- 目标：确认三张待修复物理表仍为空、模板定义完整且两张风控 Q1 表尚未创建。
-- 任一检查失败都会抛出 SQLSTATE 45000，禁止继续执行迁移。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `precheck_future_quarter_schema_repair_20261108`;
DELIMITER $$
CREATE PROCEDURE `precheck_future_quarter_schema_repair_20261108`()
BEGIN
    DECLARE actual_table_count INT DEFAULT 0;
    DECLARE existing_target_column_count INT DEFAULT 0;
    DECLARE expected_template_column_count INT DEFAULT 0;
    DECLARE target_row_count BIGINT DEFAULT 0;
    DECLARE invalid_table_option_count INT DEFAULT 0;
    DECLARE existing_risk_q1_table_count INT DEFAULT 0;

    SELECT COUNT(*) INTO actual_table_count
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN (
          'transaction_order_202701',
          'transaction_operation_202701',
          'transaction_amount_change_log_202701'
      );
    IF actual_table_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter repair requires all three 202701 transaction tables';
    END IF;

    SELECT (SELECT COUNT(*) FROM transaction_order_202701)
         + (SELECT COUNT(*) FROM transaction_operation_202701)
         + (SELECT COUNT(*) FROM transaction_amount_change_log_202701)
    INTO target_row_count;
    IF target_row_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter repair requires empty 202701 transaction tables';
    END IF;

    SELECT COUNT(*) INTO existing_target_column_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND (
          (TABLE_NAME = 'transaction_order_202701'
           AND COLUMN_NAME IN (
               'label_currency_exponent', 'label_authorized_amount',
               'label_authorized_cancel_amount', 'label_captured_amount',
               'label_refunded_amount', 'label_chargeback_amount',
               'label_available_capture_amount', 'label_available_refund_amount'
           ))
          OR (TABLE_NAME = 'transaction_operation_202701'
              AND COLUMN_NAME = 'label_currency_exponent')
          OR (TABLE_NAME = 'transaction_amount_change_log_202701'
              AND COLUMN_NAME IN (
                  'label_amount_currency', 'label_change_amount',
                  'label_authorized_before', 'label_authorized_after',
                  'label_captured_before', 'label_captured_after',
                  'label_refunded_before', 'label_refunded_after',
                  'label_available_capture_before', 'label_available_capture_after',
                  'label_available_refund_before', 'label_available_refund_after'
              ))
      );
    IF existing_target_column_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter repair found a partial or repeated migration';
    END IF;

    SELECT COUNT(*) INTO expected_template_column_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND (
          (TABLE_NAME = 'transaction_order'
           AND COLUMN_NAME IN (
               'label_currency_exponent', 'label_authorized_amount',
               'label_authorized_cancel_amount', 'label_captured_amount',
               'label_refunded_amount', 'label_chargeback_amount',
               'label_available_capture_amount', 'label_available_refund_amount'
           ))
          OR (TABLE_NAME = 'transaction_operation'
              AND COLUMN_NAME = 'label_currency_exponent')
          OR (TABLE_NAME = 'transaction_amount_change_log'
              AND COLUMN_NAME IN (
                  'label_amount_currency', 'label_change_amount',
                  'label_authorized_before', 'label_authorized_after',
                  'label_captured_before', 'label_captured_after',
                  'label_refunded_before', 'label_refunded_after',
                  'label_available_capture_before', 'label_available_capture_after',
                  'label_available_refund_before', 'label_available_refund_after'
              ))
      );
    IF expected_template_column_count <> 21 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter repair template columns are incomplete';
    END IF;

    SELECT COUNT(*) INTO invalid_table_option_count
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN (
          'transaction_order_202701',
          'transaction_operation_202701',
          'transaction_amount_change_log_202701'
      )
      AND (ENGINE <> 'InnoDB' OR TABLE_COLLATION <> 'utf8mb4_0900_ai_ci');
    IF invalid_table_option_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter repair found unsupported engine or collation';
    END IF;

    SELECT COUNT(*) INTO existing_risk_q1_table_count
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN (
          'risk_evaluation_record_202701',
          'risk_evaluation_hit_detail_202701'
      );
    IF existing_risk_q1_table_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter repair found existing risk Q1 tables; inspect partial execution first';
    END IF;
END$$
DELIMITER ;

CALL `precheck_future_quarter_schema_repair_20261108`();
DROP PROCEDURE `precheck_future_quarter_schema_repair_20261108`;

SELECT 'FUTURE_QUARTER_SCHEMA_REPAIR_PRECHECK' AS check_name, 1 AS passed;
