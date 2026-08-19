-- 在途余额改为 transaction_operation 实时聚合后的数据库迁移。
-- 旧表仅在确认空表时删除；交易操作模板表与当前物理分片补充聚合索引。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `drop_empty_legacy_pending_fund_table`;
DELIMITER $$
CREATE PROCEDURE `drop_empty_legacy_pending_fund_table`()
BEGIN
    DECLARE legacy_table_exists INT DEFAULT 0;
    DECLARE legacy_row_count BIGINT DEFAULT 0;

    SELECT COUNT(1)
      INTO legacy_table_exists
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'merchant_pending_fund_item';

    IF legacy_table_exists > 0 THEN
        SELECT COUNT(1) INTO legacy_row_count FROM merchant_pending_fund_item;
        IF legacy_row_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'merchant_pending_fund_item is not empty; reconcile before dropping';
        ELSE
            DROP TABLE merchant_pending_fund_item;
        END IF;
    END IF;
END$$
DELIMITER ;

CALL `drop_empty_legacy_pending_fund_table`();
DROP PROCEDURE `drop_empty_legacy_pending_fund_table`;

DROP PROCEDURE IF EXISTS `add_pending_fund_balance_index`;
DELIMITER $$
CREATE PROCEDURE `add_pending_fund_balance_index`(IN target_table VARCHAR(64))
BEGIN
    DECLARE target_table_exists INT DEFAULT 0;
    DECLARE target_index_exists INT DEFAULT 0;

    SELECT COUNT(1)
      INTO target_table_exists
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = target_table;

    SELECT COUNT(1)
      INTO target_index_exists
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = target_table
       AND index_name = 'idx_pending_fund_balance';

    IF target_table NOT REGEXP '^transaction_operation(_[0-9]{6})?$' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'invalid transaction operation table name';
    ELSEIF target_table_exists > 0 AND target_index_exists = 0 THEN
        SET @pending_balance_index_sql = CONCAT(
            'ALTER TABLE `', target_table, '` ADD INDEX `idx_pending_fund_balance` ',
            '(`merchant_id`,`transaction_status`,`settlement_status`,`transaction_type`,`transaction_date_time`)');
        PREPARE pending_balance_index_statement FROM @pending_balance_index_sql;
        EXECUTE pending_balance_index_statement;
        DEALLOCATE PREPARE pending_balance_index_statement;
    END IF;
END$$
DELIMITER ;

CALL `add_pending_fund_balance_index`('transaction_operation');
CALL `add_pending_fund_balance_index`('transaction_operation_202603');
CALL `add_pending_fund_balance_index`('transaction_operation_202604');
DROP PROCEDURE `add_pending_fund_balance_index`;
