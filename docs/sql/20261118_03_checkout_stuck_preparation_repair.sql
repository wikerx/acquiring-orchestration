-- 单次事故补偿，仅针对 2026-11-18 16:36:48 的指定尝试；须人工确认原请求已结束且渠道从未提交。
-- 先执行 20261118_02_checkout_operation_description.sql，再执行本脚本；不创建/重发交易，不标记收款成功。
-- 事务内锁定会话和尝试，原状态/版本、链接归属、未提交渠道、所有核心/渠道表零记录全部满足才修复。
-- 追加稳定事件号的修复审计；重跑幂等。补偿后不自动回退为处理中，不覆盖后续付款结果。
DROP PROCEDURE IF EXISTS repair_checkout_preparation_20261118;
DELIMITER $$
CREATE PROCEDURE repair_checkout_preparation_20261118()
recovery_block: BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_table VARCHAR(64);
    DECLARE v_locked_id BIGINT;
    DECLARE v_count BIGINT DEFAULT 0;
    DECLARE v_schema VARCHAR(64);
    DECLARE v_now DATETIME(3);
    DECLARE core_tables CURSOR FOR
        SELECT TABLE_NAME FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME REGEXP '^(transaction_order|transaction_operation|transaction_locator|transaction_channel_request|transaction_channel_interaction_log)(_[0-9]{6})?$';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
    SET v_schema = DATABASE();
    IF v_schema IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Select the reviewed business schema'; END IF;
    IF EXISTS (SELECT 1 FROM payment_checkout_event WHERE checkout_event_id='CE_RECOVERY_20261118_1636480320000012') THEN
        SELECT 'ALREADY_APPLIED' AS recovery_result;
        LEAVE recovery_block;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_schema
        AND TABLE_NAME='transaction_operation' AND COLUMN_NAME='description' AND CHARACTER_MAXIMUM_LENGTH>=500) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Description migration must be applied before allowing a retry';
    END IF;
    START TRANSACTION;
    SELECT COUNT(*) INTO v_count FROM payment_checkout_session
      WHERE checkout_session_id='2611181604349630000012' AND merchant_id='200045'
        AND merchant_order_no='2611181602590880000010' AND last_attempt_id='2611181636480320000012'
        AND checkout_status='PROCESSING' AND process_stage='CARD_SUBMITTED' AND version=1
        AND success_attempt_id IS NULL AND deleted=0;
    IF v_count<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Session state differs from the reviewed incident'; END IF;
    SELECT id INTO v_locked_id FROM payment_checkout_session
      WHERE checkout_session_id='2611181604349630000012' FOR UPDATE;
    SELECT COUNT(*) INTO v_count FROM payment_checkout_attempt
      WHERE checkout_attempt_id='2611181636480320000012' AND checkout_session_id='2611181604349630000012'
        AND merchant_id='200045' AND transaction_id='2611181636480370000011'
        AND attempt_status='CARD_SUBMITTED' AND process_stage='CARD_SUBMITTED' AND version=0
        AND channel_submit_time IS NULL AND channel_request_id IS NULL AND complete_time IS NULL
        AND submit_time < NOW(3)-INTERVAL 10 MINUTE AND deleted=0;
    IF v_count<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Attempt changed, recently submitted, or channel submission exists'; END IF;
    SELECT id INTO v_locked_id FROM payment_checkout_attempt
      WHERE checkout_attempt_id='2611181636480320000012' FOR UPDATE;
    IF NOT EXISTS (SELECT 1 FROM payment_link_checkout_binding
        WHERE link_id='L2611181602590930000010' AND checkout_session_id='2611181604349630000012') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'The incident link and checkout session do not match';
    END IF;
    OPEN core_tables;
    check_core: LOOP
        FETCH core_tables INTO v_table;
        IF v_done THEN LEAVE check_core; END IF;
        SET @checkout_recovery_count=NULL;
        SET @checkout_recovery_sql=CONCAT('SELECT COUNT(*) INTO @checkout_recovery_count FROM `',v_schema,'`.`',v_table,'` WHERE ',
            IF(v_table REGEXP '^transaction_order(_[0-9]{6})?$',
               'root_transaction_id=''2611181636480370000011'' OR latest_transaction_id=''2611181636480370000011''',
               'transaction_id=''2611181636480370000011'''));
        PREPARE checkout_recovery_stmt FROM @checkout_recovery_sql;
        EXECUTE checkout_recovery_stmt;
        DEALLOCATE PREPARE checkout_recovery_stmt;
        IF @checkout_recovery_count<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Core transaction or channel evidence exists; manual reconciliation required'; END IF;
    END LOOP;
    CLOSE core_tables;
    SET v_now=NOW(3);
    UPDATE payment_checkout_attempt SET attempt_status='FAILED',process_stage='RESULT_RENDERED',
        failure_reason_code='ROUTE_FAILED',failure_reason_message='Core preparation rolled back before channel submission: description exceeded legacy column limit',
        payer_visible_message='Payment could not be prepared. Please try again later.',
        result_snapshot='{"pageState":"FAILED_RETRYABLE"}',complete_time=v_now,update_time=v_now,version=version+1
      WHERE checkout_attempt_id='2611181636480320000012' AND merchant_id='200045'
        AND attempt_status='CARD_SUBMITTED' AND version=0 AND channel_submit_time IS NULL AND deleted=0;
    IF ROW_COUNT()<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Attempt CAS failed; no recovery applied'; END IF;
    UPDATE payment_checkout_session SET checkout_status='FAILED',process_stage='RESULT_RENDERED',
        last_status_time=v_now,update_time=v_now,version=version+1
      WHERE checkout_session_id='2611181604349630000012' AND merchant_id='200045'
        AND checkout_status='PROCESSING' AND version=1 AND success_attempt_id IS NULL AND deleted=0;
    IF ROW_COUNT()<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Session CAS failed; no recovery applied'; END IF;
    INSERT INTO payment_checkout_event(checkout_event_id,checkout_session_id,checkout_attempt_id,merchant_id,
        event_type,event_stage,event_result,checkout_status_before,checkout_status_after,
        attempt_status_before,attempt_status_after,transaction_id,request_id,event_payload_json,event_time,create_time)
      VALUES('CE_RECOVERY_20261118_1636480320000012','2611181604349630000012','2611181636480320000012','200045',
        'PRE_CHANNEL_FAILED','RESULT_RENDERED','FAILED','PROCESSING','FAILED','CARD_SUBMITTED','FAILED',
        '2611181636480370000011','RECOVERY_20261118_1636480320000012',
        JSON_OBJECT('recoveryReason','description_overflow_before_channel','databaseOperator',CURRENT_USER(),'coreRecordsVerifiedAbsent',TRUE),v_now,v_now);
    COMMIT;
    SELECT 'APPLIED_NO_CHANNEL_SUBMISSION' AS recovery_result;
END$$
DELIMITER ;
CALL repair_checkout_preparation_20261118();
DROP PROCEDURE repair_checkout_preparation_20261118;
