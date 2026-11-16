-- 收银台超时商户通知契约治理后置检查。
-- 所有分片的 remaining_active_nonconforming_count 必须为 0；
-- SUCCESS/CLOSED 仅作为历史事实展示，不属于本次改写范围。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS postcheck_checkout_timeout_notification_contract;
DELIMITER $$
CREATE PROCEDURE postcheck_checkout_timeout_notification_contract()
BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_table_name VARCHAR(64);
    DECLARE notification_tables CURSOR FOR
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_type = 'BASE TABLE'
          AND table_name REGEXP '^transaction_merchant_notification(_[0-9]{6})?$'
        ORDER BY table_name;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    OPEN notification_tables;
    table_loop: LOOP
        FETCH notification_tables INTO v_table_name;
        IF v_done = 1 THEN
            LEAVE table_loop;
        END IF;

        SET @postcheck_sql = CONCAT(
            'SELECT ', QUOTE(v_table_name), ' AS table_name, ',
            'COALESCE(SUM(CASE WHEN deleted = 0 AND notify_status IN (''INIT'', ''FAILED'') ',
            'AND JSON_VALID(payload_json) ',
            'AND JSON_TYPE(JSON_EXTRACT(payload_json, ''$.transactionInfo'')) = ''OBJECT'' ',
            'AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.code'')) = ''PAYMENT_TIMEOUT'' ',
            'AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.transactionStatus'')) = ''FAILED'' ',
            'AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.failReasonCode'')) = ''PAYMENT_TIMEOUT'' ',
            'AND (COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.processStage'')), '''') <> ''FINISHED'' ',
            'OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.failReasonMessage'')), '''') ',
            '<> ''Payment was not completed within the allowed time'') THEN 1 ELSE 0 END), 0) ',
            'AS remaining_active_nonconforming_count, ',
            'COALESCE(SUM(CASE WHEN deleted = 0 AND notify_status IN (''INIT'', ''FAILED'') ',
            'AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.processStage'')) = ''FINISHED'' ',
            'AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.failReasonMessage'')) ',
            '= ''Payment was not completed within the allowed time'' THEN 1 ELSE 0 END), 0) ',
            'AS active_conforming_count, ',
            'COALESCE(SUM(CASE WHEN deleted = 0 AND notify_status = ''PROCESSING'' THEN 1 ELSE 0 END), 0) AS processing_count, ',
            'COALESCE(SUM(CASE WHEN deleted = 0 AND notify_status IN (''SUCCESS'', ''CLOSED'') THEN 1 ELSE 0 END), 0) ',
            'AS preserved_terminal_history_count ',
            'FROM `', v_table_name, '` WHERE event_type = ''CHECKOUT_PAYMENT_TIMEOUT'''
        );
        PREPARE postcheck_statement FROM @postcheck_sql;
        EXECUTE postcheck_statement;
        DEALLOCATE PREPARE postcheck_statement;
    END LOOP;
    CLOSE notification_tables;
END$$
DELIMITER ;

CALL postcheck_checkout_timeout_notification_contract();
DROP PROCEDURE postcheck_checkout_timeout_notification_contract;
