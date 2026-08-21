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
    DECLARE target_index_columns TEXT DEFAULT NULL;

    SELECT COUNT(1)
      INTO target_table_exists
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = target_table;

    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
      INTO target_index_columns
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = target_table
       AND index_name = 'idx_pending_fund_balance';

    IF target_table NOT REGEXP '^transaction_operation(_[0-9]{6})?$' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'invalid transaction operation table name';
    ELSEIF target_table_exists > 0 THEN
        IF target_index_columns IS NOT NULL
           AND target_index_columns <> 'merchant_id,transaction_status,settlement_status,deleted,transaction_type,transaction_date_time,label_currency' THEN
            SET @pending_balance_drop_index_sql = CONCAT(
                'ALTER TABLE `', target_table, '` DROP INDEX `idx_pending_fund_balance`');
            PREPARE pending_balance_drop_index_statement FROM @pending_balance_drop_index_sql;
            EXECUTE pending_balance_drop_index_statement;
            DEALLOCATE PREPARE pending_balance_drop_index_statement;
            SET target_index_columns = NULL;
        END IF;
        IF target_index_columns IS NULL THEN
        SET @pending_balance_index_sql = CONCAT(
            'ALTER TABLE `', target_table, '` ADD INDEX `idx_pending_fund_balance` ',
            '(`merchant_id`,`transaction_status`,`settlement_status`,`deleted`,',
            '`transaction_type`,`transaction_date_time`,`label_currency`)');
        PREPARE pending_balance_index_statement FROM @pending_balance_index_sql;
        EXECUTE pending_balance_index_statement;
        DEALLOCATE PREPARE pending_balance_index_statement;
        END IF;
    END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS `add_all_pending_fund_balance_indexes`;
DELIMITER $$
CREATE PROCEDURE `add_all_pending_fund_balance_indexes`()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE target_table VARCHAR(64);
    DECLARE table_cursor CURSOR FOR
        SELECT table_name
          FROM information_schema.tables
         WHERE table_schema = DATABASE()
           AND table_name REGEXP '^transaction_operation(_[0-9]{6})?$';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN table_cursor;
    table_loop: LOOP
        FETCH table_cursor INTO target_table;
        IF done = 1 THEN LEAVE table_loop; END IF;
        CALL `add_pending_fund_balance_index`(target_table);
    END LOOP;
    CLOSE table_cursor;
END$$
DELIMITER ;

CALL `add_all_pending_fund_balance_indexes`();
DROP PROCEDURE `add_all_pending_fund_balance_indexes`;
DROP PROCEDURE `add_pending_fund_balance_index`;
