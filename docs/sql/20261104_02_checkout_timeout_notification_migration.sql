-- 收银台超时商户通知契约幂等修复。
-- 前置：先运行 20261104_01；停止 service-payment、service-data 和商户通知重试任务；
--       所有分片的 processing_count 必须为 0。
-- 范围：只修复仍可投递的 INIT/FAILED 超时通知；不改写 PROCESSING、SUCCESS、CLOSED 或其他事件。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS migrate_checkout_timeout_notification_contract;
DELIMITER $$
CREATE PROCEDURE migrate_checkout_timeout_notification_contract()
BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_table_name VARCHAR(64);
    DECLARE v_repaired_count BIGINT DEFAULT 0;
    DECLARE notification_tables CURSOR FOR
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_type = 'BASE TABLE'
          AND table_name REGEXP '^transaction_merchant_notification(_[0-9]{6})?$'
        ORDER BY table_name;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    OPEN notification_tables;
    table_loop: LOOP
        FETCH notification_tables INTO v_table_name;
        IF v_done = 1 THEN
            LEAVE table_loop;
        END IF;

        SET @processing_count = 0;
        SET @processing_sql = CONCAT(
            'SELECT COUNT(*) INTO @processing_count FROM `', v_table_name, '` ',
            'WHERE deleted = 0 AND event_type = ''CHECKOUT_PAYMENT_TIMEOUT'' ',
            'AND notify_status = ''PROCESSING'''
        );
        PREPARE processing_statement FROM @processing_sql;
        EXECUTE processing_statement;
        DEALLOCATE PREPARE processing_statement;
        IF @processing_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'processing checkout-timeout notification exists; stop delivery and retry migration';
        END IF;

        SET @migration_sql = CONCAT(
            'UPDATE `', v_table_name, '` SET ',
            'payload_json = JSON_SET(payload_json, ',
            '''$.transactionInfo.processStage'', ''FINISHED'', ',
            '''$.transactionInfo.failReasonMessage'', ''Payment was not completed within the allowed time''), ',
            'payload_json_masked = CASE ',
            'WHEN payload_json_masked IS NOT NULL AND JSON_VALID(payload_json_masked) ',
            'AND JSON_TYPE(JSON_EXTRACT(payload_json_masked, ''$.transactionInfo'')) = ''OBJECT'' ',
            'THEN JSON_SET(payload_json_masked, ',
            '''$.transactionInfo.processStage'', ''FINISHED'', ',
            '''$.transactionInfo.failReasonMessage'', ''Payment was not completed within the allowed time'') ',
            'ELSE payload_json_masked END, ',
            'version = version + 1, update_time = CURRENT_TIMESTAMP(3) ',
            'WHERE deleted = 0 AND event_type = ''CHECKOUT_PAYMENT_TIMEOUT'' ',
            'AND notify_status IN (''INIT'', ''FAILED'') ',
            'AND JSON_VALID(payload_json) ',
            'AND JSON_TYPE(JSON_EXTRACT(payload_json, ''$.transactionInfo'')) = ''OBJECT'' ',
            'AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.code'')) = ''PAYMENT_TIMEOUT'' ',
            'AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.transactionStatus'')) = ''FAILED'' ',
            'AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.failReasonCode'')) = ''PAYMENT_TIMEOUT'' ',
            'AND (COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.processStage'')), '''') <> ''FINISHED'' ',
            'OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.failReasonMessage'')), '''') ',
            '<> ''Payment was not completed within the allowed time'')'
        );
        PREPARE migration_statement FROM @migration_sql;
        EXECUTE migration_statement;
        SET v_repaired_count = v_repaired_count + ROW_COUNT();
        DEALLOCATE PREPARE migration_statement;
    END LOOP;
    CLOSE notification_tables;
    COMMIT;

    SELECT v_repaired_count AS repaired_notification_count;
END$$
DELIMITER ;

CALL migrate_checkout_timeout_notification_contract();
DROP PROCEDURE migrate_checkout_timeout_notification_contract;
