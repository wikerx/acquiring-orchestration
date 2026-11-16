-- 收银台超时商户通知契约治理前置检查。
-- 运行窗口：停止 service-payment、service-data 和商户通知重试任务后执行。
-- candidate_count 是本次可自动修复范围；processing_count 必须为 0 后才能执行迁移。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS precheck_checkout_timeout_notification_contract;
DELIMITER $$
CREATE PROCEDURE precheck_checkout_timeout_notification_contract()
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

        SET @precheck_sql = CONCAT(
            'SELECT ', QUOTE(v_table_name), ' AS table_name, ',
            'COUNT(*) AS timeout_notification_count, ',
            'COALESCE(SUM(CASE WHEN deleted = 0 AND notify_status IN (''INIT'', ''FAILED'') ',
            'AND JSON_VALID(payload_json) ',
            'AND JSON_TYPE(JSON_EXTRACT(payload_json, ''$.transactionInfo'')) = ''OBJECT'' ',
            'AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.code'')) = ''PAYMENT_TIMEOUT'' ',
            'AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.transactionStatus'')) = ''FAILED'' ',
            'AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.failReasonCode'')) = ''PAYMENT_TIMEOUT'' ',
            'AND (COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.processStage'')), '''') <> ''FINISHED'' ',
            'OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.failReasonMessage'')), '''') ',
            '<> ''Payment was not completed within the allowed time'') THEN 1 ELSE 0 END), 0) AS candidate_count, ',
            'COALESCE(SUM(CASE WHEN deleted = 0 AND notify_status = ''PROCESSING'' THEN 1 ELSE 0 END), 0) AS processing_count, ',
            'COALESCE(SUM(CASE WHEN deleted = 0 AND notify_status = ''SUCCESS'' ',
            'AND (NOT JSON_VALID(payload_json) ',
            'OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.processStage'')), '''') <> ''FINISHED'' ',
            'OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.failReasonMessage'')), '''') ',
            '<> ''Payment was not completed within the allowed time'') THEN 1 ELSE 0 END), 0) AS delivered_legacy_count, ',
            'COALESCE(SUM(CASE WHEN deleted = 0 AND NOT JSON_VALID(payload_json) THEN 1 ELSE 0 END), 0) AS invalid_payload_count ',
            'FROM `', v_table_name, '` WHERE event_type = ''CHECKOUT_PAYMENT_TIMEOUT'''
        );
        PREPARE precheck_statement FROM @precheck_sql;
        EXECUTE precheck_statement;
        DEALLOCATE PREPARE precheck_statement;
    END LOOP;
    CLOSE notification_tables;
END$$
DELIMITER ;

CALL precheck_checkout_timeout_notification_contract();
DROP PROCEDURE precheck_checkout_timeout_notification_contract;
