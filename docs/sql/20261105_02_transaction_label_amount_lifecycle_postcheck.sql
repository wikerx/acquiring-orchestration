-- 交易金额双口径生命周期迁移后置校验。
-- 任一检查失败都会抛出 SQLSTATE 45000；只有脚本完整执行并输出 PASS 才可恢复业务写入。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `postcheck_transaction_label_amount_lifecycle_20261105`;
DELIMITER $$
CREATE PROCEDURE `postcheck_transaction_label_amount_lifecycle_20261105`()
BEGIN
    DECLARE actual_column_count INT DEFAULT 0;
    DECLARE invalid_row_count BIGINT DEFAULT 0;

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
            SET MESSAGE_TEXT = 'label amount lifecycle postcheck failed: expected 63 new columns';
    END IF;

    SELECT COUNT(*) INTO invalid_row_count
    FROM (
        SELECT 'order' AS table_family, COLUMN_NAME,
               COUNT(*) AS copy_count,
               COUNT(DISTINCT CONCAT_WS('|', COLUMN_TYPE, IS_NULLABLE, COALESCE(COLUMN_DEFAULT, '<NULL>')))
                   AS definition_count
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME IN ('transaction_order', 'transaction_order_202603', 'transaction_order_202604')
          AND COLUMN_NAME IN (
              'label_currency_exponent', 'label_authorized_amount', 'label_authorized_cancel_amount',
              'label_captured_amount', 'label_refunded_amount', 'label_chargeback_amount',
              'label_available_capture_amount', 'label_available_refund_amount'
          )
        GROUP BY COLUMN_NAME
        UNION ALL
        SELECT 'operation', COLUMN_NAME, COUNT(*),
               COUNT(DISTINCT CONCAT_WS('|', COLUMN_TYPE, IS_NULLABLE, COALESCE(COLUMN_DEFAULT, '<NULL>')))
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME IN ('transaction_operation', 'transaction_operation_202603', 'transaction_operation_202604')
          AND COLUMN_NAME = 'label_currency_exponent'
        GROUP BY COLUMN_NAME
        UNION ALL
        SELECT 'amount_log', COLUMN_NAME, COUNT(*),
               COUNT(DISTINCT CONCAT_WS('|', COLUMN_TYPE, IS_NULLABLE, COALESCE(COLUMN_DEFAULT, '<NULL>')))
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME IN (
              'transaction_amount_change_log', 'transaction_amount_change_log_202603',
              'transaction_amount_change_log_202604'
          )
          AND COLUMN_NAME IN (
              'label_amount_currency', 'label_change_amount', 'label_authorized_before',
              'label_authorized_after', 'label_captured_before', 'label_captured_after',
              'label_refunded_before', 'label_refunded_after', 'label_available_capture_before',
              'label_available_capture_after', 'label_available_refund_before',
              'label_available_refund_after'
          )
        GROUP BY COLUMN_NAME
    ) definitions
    WHERE copy_count <> 3 OR definition_count <> 1;
    IF invalid_row_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'label amount lifecycle postcheck failed: template and shard definitions differ';
    END IF;

    SELECT COUNT(*) INTO invalid_row_count
    FROM (
        SELECT o.id
        FROM transaction_order_202603 o
        JOIN base_iso_currency c ON c.alpha3_code = o.label_currency AND c.deleted = 0
        WHERE o.label_currency_exponent <> c.fraction_digits
        UNION ALL
        SELECT o.id
        FROM transaction_order_202604 o
        JOIN base_iso_currency c ON c.alpha3_code = o.label_currency AND c.deleted = 0
        WHERE o.label_currency_exponent <> c.fraction_digits
        UNION ALL
        SELECT o.id
        FROM transaction_operation_202603 o
        JOIN base_iso_currency c ON c.alpha3_code = o.label_currency AND c.deleted = 0
        WHERE o.label_currency_exponent <> c.fraction_digits
        UNION ALL
        SELECT o.id
        FROM transaction_operation_202604 o
        JOIN base_iso_currency c ON c.alpha3_code = o.label_currency AND c.deleted = 0
        WHERE o.label_currency_exponent <> c.fraction_digits
    ) exponent_mismatch;
    IF invalid_row_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'label amount lifecycle postcheck failed: label exponent differs from ISO dictionary';
    END IF;

    SELECT COUNT(*) INTO invalid_row_count
    FROM (
        SELECT id FROM transaction_order_202603
        WHERE label_authorized_amount < 0 OR label_authorized_cancel_amount < 0
           OR label_captured_amount < 0 OR label_refunded_amount < 0 OR label_chargeback_amount < 0
           OR label_available_capture_amount < 0 OR label_available_refund_amount < 0
           OR label_available_capture_amount <>
              GREATEST(label_authorized_amount - label_authorized_cancel_amount - label_captured_amount, 0)
           OR label_available_refund_amount <>
              GREATEST(label_captured_amount - label_refunded_amount - label_chargeback_amount, 0)
        UNION ALL
        SELECT id FROM transaction_order_202604
        WHERE label_authorized_amount < 0 OR label_authorized_cancel_amount < 0
           OR label_captured_amount < 0 OR label_refunded_amount < 0 OR label_chargeback_amount < 0
           OR label_available_capture_amount < 0 OR label_available_refund_amount < 0
           OR label_available_capture_amount <>
              GREATEST(label_authorized_amount - label_authorized_cancel_amount - label_captured_amount, 0)
           OR label_available_refund_amount <>
              GREATEST(label_captured_amount - label_refunded_amount - label_chargeback_amount, 0)
    ) invalid_balance;
    IF invalid_row_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'label amount lifecycle postcheck failed: negative or inconsistent label balance';
    END IF;

    WITH lifecycle AS (
        SELECT operation_id,
               SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type IN
                       ('AUTHORIZATION', 'PRE_AUTHORIZATION', 'PAYMENT', 'INCREMENTAL_AUTHORIZATION')
                   THEN label_amount ELSE 0 END) AS authorized_amount,
               SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type IN
                       ('PAYMENT', 'CAPTURE', 'PRE_AUTH_COMPLETION')
                   THEN label_amount ELSE 0 END) AS captured_amount,
               SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'REFUND'
                   THEN label_amount ELSE 0 END) AS refunded_amount,
               SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'CHARGEBACK'
                   THEN label_amount ELSE 0 END) AS chargeback_amount,
               MAX(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'VOID' THEN 1 ELSE 0 END)
                   AS successful_void
        FROM transaction_operation_202603
        WHERE deleted = 0
        GROUP BY operation_id
    )
    SELECT COUNT(*) INTO invalid_row_count
    FROM transaction_order_202603 o
    LEFT JOIN lifecycle l ON l.operation_id = o.operation_id
    WHERE o.deleted = 0
      AND (o.label_authorized_amount <> COALESCE(l.authorized_amount, 0)
        OR o.label_authorized_cancel_amount <> CASE WHEN COALESCE(l.successful_void, 0) = 1
               THEN GREATEST(COALESCE(l.authorized_amount, 0) - COALESCE(l.captured_amount, 0), 0) ELSE 0 END
        OR o.label_captured_amount <> COALESCE(l.captured_amount, 0)
        OR o.label_refunded_amount <> COALESCE(l.refunded_amount, 0)
        OR o.label_chargeback_amount <> COALESCE(l.chargeback_amount, 0));
    IF invalid_row_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'label amount lifecycle postcheck failed: 202603 order totals differ from successful operations';
    END IF;

    WITH lifecycle AS (
        SELECT operation_id,
               SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type IN
                       ('AUTHORIZATION', 'PRE_AUTHORIZATION', 'PAYMENT', 'INCREMENTAL_AUTHORIZATION')
                   THEN label_amount ELSE 0 END) AS authorized_amount,
               SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type IN
                       ('PAYMENT', 'CAPTURE', 'PRE_AUTH_COMPLETION')
                   THEN label_amount ELSE 0 END) AS captured_amount,
               SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'REFUND'
                   THEN label_amount ELSE 0 END) AS refunded_amount,
               SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'CHARGEBACK'
                   THEN label_amount ELSE 0 END) AS chargeback_amount,
               MAX(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'VOID' THEN 1 ELSE 0 END)
                   AS successful_void
        FROM transaction_operation_202604
        WHERE deleted = 0
        GROUP BY operation_id
    )
    SELECT COUNT(*) INTO invalid_row_count
    FROM transaction_order_202604 o
    LEFT JOIN lifecycle l ON l.operation_id = o.operation_id
    WHERE o.deleted = 0
      AND (o.label_authorized_amount <> COALESCE(l.authorized_amount, 0)
        OR o.label_authorized_cancel_amount <> CASE WHEN COALESCE(l.successful_void, 0) = 1
               THEN GREATEST(COALESCE(l.authorized_amount, 0) - COALESCE(l.captured_amount, 0), 0) ELSE 0 END
        OR o.label_captured_amount <> COALESCE(l.captured_amount, 0)
        OR o.label_refunded_amount <> COALESCE(l.refunded_amount, 0)
        OR o.label_chargeback_amount <> COALESCE(l.chargeback_amount, 0));
    IF invalid_row_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'label amount lifecycle postcheck failed: 202604 order totals differ from successful operations';
    END IF;

    SELECT COUNT(*) INTO invalid_row_count
    FROM (
        SELECT id FROM transaction_amount_change_log_202603
        WHERE label_amount_currency IS NULL OR label_change_amount IS NULL
           OR label_authorized_before IS NULL OR label_authorized_after IS NULL
           OR label_captured_before IS NULL OR label_captured_after IS NULL
           OR label_refunded_before IS NULL OR label_refunded_after IS NULL
           OR label_available_capture_before IS NULL OR label_available_capture_after IS NULL
           OR label_available_refund_before IS NULL OR label_available_refund_after IS NULL
        UNION ALL
        SELECT id FROM transaction_amount_change_log_202604
        WHERE label_amount_currency IS NULL OR label_change_amount IS NULL
           OR label_authorized_before IS NULL OR label_authorized_after IS NULL
           OR label_captured_before IS NULL OR label_captured_after IS NULL
           OR label_refunded_before IS NULL OR label_refunded_after IS NULL
           OR label_available_capture_before IS NULL OR label_available_capture_after IS NULL
           OR label_available_refund_before IS NULL OR label_available_refund_after IS NULL
    ) incomplete_log;
    IF invalid_row_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'label amount lifecycle postcheck failed: amount log has incomplete label facts';
    END IF;

    SELECT COUNT(*) INTO invalid_row_count
    FROM (
        SELECT op.id
        FROM transaction_operation_202603 op
        JOIN transaction_order_202603 o ON o.operation_id = op.operation_id
        WHERE op.transaction_type = 'VOID'
          AND op.label_currency <> op.transaction_currency
          AND (op.label_currency <> o.label_currency
            OR NOT (op.transaction_rate <=> o.transaction_rate)
            OR op.dcc_enabled <> o.dcc_enabled
            OR op.edc_enabled <> o.edc_enabled)
        UNION ALL
        SELECT op.id
        FROM transaction_operation_202604 op
        JOIN transaction_order_202604 o ON o.operation_id = op.operation_id
        WHERE op.transaction_type = 'VOID'
          AND op.label_currency <> op.transaction_currency
          AND (op.label_currency <> o.label_currency
            OR NOT (op.transaction_rate <=> o.transaction_rate)
            OR op.dcc_enabled <> o.dcc_enabled
            OR op.edc_enabled <> o.edc_enabled)
    ) invalid_void;
    IF invalid_row_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'label amount lifecycle postcheck failed: cross-currency VOID did not preserve frozen rate facts';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'transaction_label_amount_void_backup_20261105'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'label amount lifecycle postcheck failed: rollback backup is missing';
    END IF;
END$$
DELIMITER ;

CALL `postcheck_transaction_label_amount_lifecycle_20261105`();
DROP PROCEDURE `postcheck_transaction_label_amount_lifecycle_20261105`;

SELECT 'PASS' AS migration_result,
       (SELECT COUNT(*) FROM transaction_order_202603)
         + (SELECT COUNT(*) FROM transaction_order_202604) AS checked_order_count,
       (SELECT COUNT(*) FROM transaction_operation_202603)
         + (SELECT COUNT(*) FROM transaction_operation_202604) AS checked_operation_count,
       (SELECT COUNT(*) FROM transaction_amount_change_log_202603)
         + (SELECT COUNT(*) FROM transaction_amount_change_log_202604) AS checked_amount_log_count,
       (SELECT COUNT(*) FROM transaction_label_amount_void_backup_20261105) AS backed_up_void_count;
