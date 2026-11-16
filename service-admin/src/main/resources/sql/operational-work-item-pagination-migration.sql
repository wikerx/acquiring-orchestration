-- 统一待办真实数据库分页与处理历史分页。
-- 仅复制搜索和商户数据范围所需的最小来源标识；金额、币种、状态和失败原因继续实时读取来源表。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS admin_owi_add_column;
DELIMITER $$
CREATE PROCEDURE admin_owi_add_column(IN p_column_name VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'admin_operational_work_item'
          AND column_name = p_column_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE admin_operational_work_item ADD COLUMN ', p_definition);
        PREPARE statement FROM @ddl;
        EXECUTE statement;
        DEALLOCATE PREPARE statement;
    END IF;
END$$
DELIMITER ;

CALL admin_owi_add_column('source_projection_version',
    'source_projection_version INT NOT NULL DEFAULT 0 COMMENT ''来源检索投影版本'' AFTER version');
CALL admin_owi_add_column('merchant_id',
    'merchant_id VARCHAR(64) NULL COMMENT ''来源商户号检索投影'' AFTER source_projection_version');
CALL admin_owi_add_column('merchant_name',
    'merchant_name VARCHAR(128) NULL COMMENT ''来源商户名称检索投影'' AFTER merchant_id');
CALL admin_owi_add_column('business_no',
    'business_no VARCHAR(128) NULL COMMENT ''来源主业务单号检索投影'' AFTER merchant_name');
CALL admin_owi_add_column('related_business_no',
    'related_business_no VARCHAR(128) NULL COMMENT ''来源关联业务单号检索投影'' AFTER business_no');
CALL admin_owi_add_column('transaction_id',
    'transaction_id VARCHAR(64) NULL COMMENT ''来源系统订单号检索投影'' AFTER related_business_no');
CALL admin_owi_add_column('transaction_date_time',
    'transaction_date_time DATETIME(3) NULL COMMENT ''来源交易时间检索投影'' AFTER transaction_id');
CALL admin_owi_add_column('source_occurred_at',
    'source_occurred_at DATETIME(3) NULL COMMENT ''来源事件时间检索投影'' AFTER transaction_date_time');
DROP PROCEDURE admin_owi_add_column;

DROP TEMPORARY TABLE IF EXISTS admin_owi_projection_source;
CREATE TEMPORARY TABLE admin_owi_projection_source (
    source_type VARCHAR(48) NOT NULL,
    logical_table VARCHAR(64) NOT NULL,
    key_expression VARCHAR(255) NOT NULL,
    merchant_expression VARCHAR(255) NOT NULL,
    merchant_name_expression VARCHAR(255) NOT NULL,
    merchant_join_expression VARCHAR(255) NOT NULL,
    business_expression VARCHAR(255) NOT NULL,
    related_business_expression VARCHAR(255) NOT NULL,
    transaction_expression VARCHAR(255) NOT NULL,
    transaction_time_expression VARCHAR(255) NOT NULL,
    occurred_expression VARCHAR(255) NOT NULL,
    PRIMARY KEY (source_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO admin_owi_projection_source VALUES
    ('REFUND_APPROVAL', 'transaction_refund_approval', 'src.approval_id',
     'src.merchant_id', 'merchant.merchant_name',
     'merchant.merchant_id = src.merchant_id AND merchant.deleted = 0',
     'src.refund_transaction_id', 'src.source_transaction_id',
     'src.refund_transaction_id', 'src.refund_transaction_date_time', 'src.create_time'),
    ('SETTLEMENT_REVIEW', 'settlement_review_order', 'src.review_order_no',
     'src.merchant_id', 'merchant.merchant_name',
     'merchant.merchant_id = src.merchant_id AND merchant.deleted = 0',
     'src.review_order_no', 'src.settlement_batch_no',
     'NULL', 'NULL', 'src.submitted_time'),
    ('EXCHANGE_RATE_ALERT', 'exchange_rate_alert_event', 'src.event_no',
     'NULL', 'NULL', '1 = 0',
     'src.event_no', 'src.rule_code', 'NULL', 'NULL', 'src.first_trigger_time'),
    ('MERCHANT_NOTIFICATION_FAILURE', 'transaction_merchant_notification', 'src.notify_id',
     'src.merchant_id', 'merchant.merchant_name',
     'merchant.merchant_id = src.merchant_id AND merchant.deleted = 0',
     'src.notify_id', 'src.merchant_order_no',
     'src.transaction_id', 'src.transaction_date_time', 'src.create_time'),
    ('CHANNEL_MATCH_ABNORMAL', 'transaction_abnormal_event', 'src.abnormal_event_id',
     'src.merchant_id', 'merchant.merchant_name',
     'merchant.merchant_id = src.merchant_id AND merchant.deleted = 0',
     'src.abnormal_event_id', 'src.merchant_order_no',
     'src.transaction_id', 'src.transaction_date_time', 'src.first_seen_time'),
    ('CLEARING_BACKLOG', 'transaction_finance_state', 'src.finance_state_id',
     'src.merchant_id', 'merchant.merchant_name',
     'merchant.merchant_id = src.merchant_id AND merchant.deleted = 0',
     'src.transaction_id', 'src.operation_id',
     'src.transaction_id', 'src.transaction_date_time', 'src.update_time'),
    ('SETTLEMENT_BACKLOG', 'settlement_batch', 'src.settlement_batch_no',
     'src.merchant_id', 'merchant.merchant_name',
     'merchant.merchant_id = src.merchant_id AND merchant.deleted = 0',
     'src.settlement_batch_no', 'COALESCE(src.review_order_no, src.original_batch_no)',
     'NULL', 'NULL', 'src.update_time'),
    ('HIGH_RISK_EVENT', 'risk_evaluation_record', 'src.risk_record_no',
     'src.merchant_id', 'COALESCE(NULLIF(src.merchant_name, ''''), merchant.merchant_name)',
     'merchant.merchant_id = src.merchant_id AND merchant.deleted = 0',
     'src.risk_record_no', 'COALESCE(NULLIF(src.payment_order_no, ''''), src.merchant_order_no)',
     'src.payment_order_no', 'NULL', 'src.evaluation_time');

DROP PROCEDURE IF EXISTS admin_owi_backfill_projection;
DELIMITER $$
CREATE PROCEDURE admin_owi_backfill_projection()
BEGIN
    DECLARE finished INT DEFAULT 0;
    DECLARE v_source_type VARCHAR(48);
    DECLARE v_table_name VARCHAR(64);
    DECLARE v_key_expression VARCHAR(255);
    DECLARE v_merchant_expression VARCHAR(255);
    DECLARE v_merchant_name_expression VARCHAR(255);
    DECLARE v_merchant_join_expression VARCHAR(255);
    DECLARE v_business_expression VARCHAR(255);
    DECLARE v_related_business_expression VARCHAR(255);
    DECLARE v_transaction_expression VARCHAR(255);
    DECLARE v_transaction_time_expression VARCHAR(255);
    DECLARE v_occurred_expression VARCHAR(255);
    DECLARE source_cursor CURSOR FOR
        SELECT mapping.source_type,
               source_table.table_name,
               mapping.key_expression,
               mapping.merchant_expression,
               mapping.merchant_name_expression,
               mapping.merchant_join_expression,
               mapping.business_expression,
               mapping.related_business_expression,
               mapping.transaction_expression,
               mapping.transaction_time_expression,
               mapping.occurred_expression
        FROM admin_owi_projection_source mapping
        JOIN information_schema.tables source_table
          ON source_table.table_schema = DATABASE()
         AND source_table.table_name REGEXP CONCAT(
             '^', mapping.logical_table, '(_[0-9]{6})?$')
        ORDER BY mapping.source_type, source_table.table_name;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;

    OPEN source_cursor;
    projection_loop: LOOP
        FETCH source_cursor INTO v_source_type, v_table_name, v_key_expression,
            v_merchant_expression, v_merchant_name_expression, v_merchant_join_expression,
            v_business_expression, v_related_business_expression, v_transaction_expression,
            v_transaction_time_expression, v_occurred_expression;
        IF finished = 1 THEN
            LEAVE projection_loop;
        END IF;

        SET @backfill_sql = CONCAT(
            'UPDATE admin_operational_work_item item ',
            'JOIN `', REPLACE(v_table_name, '`', '``'), '` src ',
            'ON item.source_type = ', QUOTE(v_source_type),
            ' AND item.source_key = ', v_key_expression, ' ',
            'LEFT JOIN base_merchant_info merchant ON ', v_merchant_join_expression, ' ',
            'SET item.merchant_id = ', v_merchant_expression, ', ',
            'item.merchant_name = ', v_merchant_name_expression, ', ',
            'item.business_no = ', v_business_expression, ', ',
            'item.related_business_no = ', v_related_business_expression, ', ',
            'item.transaction_id = ', v_transaction_expression, ', ',
            'item.transaction_date_time = ', v_transaction_time_expression, ', ',
            'item.source_occurred_at = ', v_occurred_expression, ', ',
            'item.source_projection_version = 1 ',
            'WHERE item.source_projection_version <> 1'
        );
        PREPARE statement FROM @backfill_sql;
        EXECUTE statement;
        DEALLOCATE PREPARE statement;
    END LOOP;
    CLOSE source_cursor;
END$$
DELIMITER ;

CALL admin_owi_backfill_projection();
DROP PROCEDURE admin_owi_backfill_projection;
DROP TEMPORARY TABLE admin_owi_projection_source;

DROP PROCEDURE IF EXISTS admin_owi_add_index;
DELIMITER $$
CREATE PROCEDURE admin_owi_add_index(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(128),
    IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD INDEX `',
                          p_index_name, '` ', p_definition);
        PREPARE statement FROM @ddl;
        EXECUTE statement;
        DEALLOCATE PREPARE statement;
    END IF;
END$$
DELIMITER ;

CALL admin_owi_add_index(
    'admin_operational_work_item',
    'idx_admin_operational_work_item_projection_queue',
    '(source_projection_version, work_status, priority, sla_due_at, first_detected_at, id)');
CALL admin_owi_add_index(
    'admin_operational_work_item',
    'idx_admin_operational_work_item_projection_merchant',
    '(source_projection_version, merchant_id, work_status, first_detected_at, id)');
CALL admin_owi_add_index(
    'admin_operational_work_item_history',
    'idx_admin_operational_work_item_history_page',
    '(work_item_id, operated_at DESC, id DESC)');
DROP PROCEDURE admin_owi_add_index;
