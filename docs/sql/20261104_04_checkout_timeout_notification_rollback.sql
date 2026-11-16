-- 收银台超时通知契约治理回滚边界与应急检查。
-- 本次数据修复只为现有通知协议补齐 processStage 和 failReasonMessage；旧版消费者按既有
-- ignoreUnknown 约定兼容这两个字段。删除字段会重新制造不完整回调，因此历史数据采用前向修复，
-- 不执行破坏性逆向 UPDATE。
--
-- 应急处置：
-- 1. 停止 service-payment、service-data 和商户通知重试任务；
-- 2. 如新代码异常，可回滚应用版本，已修复 JSON 保持不变；
-- 3. 运行本脚本确认影响范围，并针对明确异常的 notify_id 编写经审核的定点前向修复；
-- 4. 禁止批量删除 SUCCESS/CLOSED 历史通知中的字段或重开已完成任务。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS inspect_checkout_timeout_notification_rollback_scope;
DELIMITER $$
CREATE PROCEDURE inspect_checkout_timeout_notification_rollback_scope()
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

        SET @rollback_scope_sql = CONCAT(
            'SELECT ', QUOTE(v_table_name), ' AS table_name, notify_status, COUNT(*) AS contract_row_count ',
            'FROM `', v_table_name, '` WHERE deleted = 0 ',
            'AND event_type = ''CHECKOUT_PAYMENT_TIMEOUT'' ',
            'AND JSON_VALID(payload_json) ',
            'AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.processStage'')) = ''FINISHED'' ',
            'AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, ''$.transactionInfo.failReasonMessage'')) ',
            '= ''Payment was not completed within the allowed time'' ',
            'GROUP BY notify_status ORDER BY notify_status'
        );
        PREPARE rollback_scope_statement FROM @rollback_scope_sql;
        EXECUTE rollback_scope_statement;
        DEALLOCATE PREPARE rollback_scope_statement;
    END LOOP;
    CLOSE notification_tables;
END$$
DELIMITER ;

SELECT 'FORWARD_FIX_ONLY' AS rollback_strategy,
       'Keep additive contract fields; never reopen or rewrite delivered terminal notifications' AS rollback_boundary;
CALL inspect_checkout_timeout_notification_rollback_scope();
DROP PROCEDURE inspect_checkout_timeout_notification_rollback_scope;
