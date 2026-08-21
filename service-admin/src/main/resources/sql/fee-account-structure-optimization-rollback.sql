-- fee-account-structure-optimization.sql 的结构回滚脚本。
-- 回滚会删除 fee_simulation_record.risk_service_type 快照，执行前必须确认该字段数据不再需要。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `rollback_drop_check`;
DELIMITER $$
CREATE PROCEDURE `rollback_drop_check`(IN target_table VARCHAR(64), IN target_constraint VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE() AND table_name = target_table
          AND constraint_name = target_constraint AND constraint_type = 'CHECK'
    ) THEN
        SET @drop_check_sql = CONCAT('ALTER TABLE `', target_table, '` DROP CHECK `', target_constraint, '`');
        PREPARE drop_check_statement FROM @drop_check_sql;
        EXECUTE drop_check_statement;
        DEALLOCATE PREPARE drop_check_statement;
    END IF;
END$$
DELIMITER ;

CALL `rollback_drop_check`('fee_plan_version', 'chk_fee_version_reserve');
CALL `rollback_drop_check`('fee_plan_version', 'chk_fee_version_settlement_cycle');
CALL `rollback_drop_check`('fee_plan_version', 'chk_fee_version_frequency');
CALL `rollback_drop_check`('fee_rule', 'chk_fee_rule_amount');
CALL `rollback_drop_check`('fee_rule_tier', 'chk_fee_tier_range');
CALL `rollback_drop_check`('fee_rule_tier', 'chk_fee_tier_amount');
CALL `rollback_drop_check`('fee_simulation_record', 'chk_fee_simulation_risk_type');
CALL `rollback_drop_check`('merchant_fund_account', 'chk_fund_account_status');
CALL `rollback_drop_check`('merchant_fund_ledger', 'chk_fund_ledger_balance');
CALL `rollback_drop_check`('merchant_fund_recharge', 'chk_fund_recharge_amount');
CALL `rollback_drop_check`('merchant_reserve_item', 'chk_reserve_amount');
DROP PROCEDURE `rollback_drop_check`;

DROP PROCEDURE IF EXISTS `rollback_replace_index`;
DELIMITER $$
CREATE PROCEDURE `rollback_replace_index`(
    IN target_table VARCHAR(64),
    IN target_index VARCHAR(64),
    IN target_unique TINYINT,
    IN target_columns TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = target_table AND index_name = target_index
    ) THEN
        SET @rollback_drop_index_sql = CONCAT('ALTER TABLE `', target_table, '` DROP INDEX `', target_index, '`');
        PREPARE rollback_drop_index_statement FROM @rollback_drop_index_sql;
        EXECUTE rollback_drop_index_statement;
        DEALLOCATE PREPARE rollback_drop_index_statement;
    END IF;
    IF target_columns IS NOT NULL THEN
        SET @rollback_add_index_sql = CONCAT(
            'ALTER TABLE `', target_table, '` ADD ', IF(target_unique = 1, 'UNIQUE ', ''),
            'INDEX `', target_index, '` (', target_columns, ')');
        PREPARE rollback_add_index_statement FROM @rollback_add_index_sql;
        EXECUTE rollback_add_index_statement;
        DEALLOCATE PREPARE rollback_add_index_statement;
    END IF;
END$$
DELIMITER ;

CALL `rollback_replace_index`('fee_plan', 'idx_fee_plan_type_list', 0, NULL);
CALL `rollback_replace_index`('fee_plan', 'idx_fee_plan_type_status', 0, '`plan_type`,`status`,`deleted`');
CALL `rollback_replace_index`('fee_plan', 'idx_fee_plan_source_template', 0, '`source_template_id`,`source_template_version_no`');
CALL `rollback_replace_index`('fee_plan_version', 'idx_fee_version_review', 0, '`version_status`,`submit_time`,`deleted`');
CALL `rollback_replace_index`('fee_plan_version', 'idx_fee_version_history', 0, NULL);
CALL `rollback_replace_index`('fee_plan_version', 'idx_fee_version_effective', 0, '`plan_id`,`effective_time`,`deleted`');
CALL `rollback_replace_index`('fee_rule', 'idx_fee_rule_version', 0, '`plan_version_id`,`sort_no`,`deleted`');
CALL `rollback_replace_index`('fee_rule_tier', 'idx_fee_tier_rule', 0, '`fee_rule_id`,`sort_no`,`deleted`');
CALL `rollback_replace_index`('fee_simulation_record', 'idx_fee_simulation_create_time', 0, NULL);
CALL `rollback_replace_index`('fee_simulation_record', 'idx_fee_simulation_plan_time', 0, '`plan_version_id`,`create_time`');
CALL `rollback_replace_index`('fee_simulation_record', 'idx_fee_simulation_merchant_time', 0, '`merchant_id`,`create_time`');
CALL `rollback_replace_index`('fee_simulation_record', 'idx_fee_simulation_transaction_time', 0, NULL);
CALL `rollback_replace_index`('merchant_fund_account', 'idx_fund_account_list', 0, NULL);
CALL `rollback_replace_index`('merchant_fund_account', 'idx_fund_account_status', 0, '`account_status`,`deleted`');
CALL `rollback_replace_index`('merchant_fund_ledger', 'idx_fund_ledger_account_time', 0, NULL);
CALL `rollback_replace_index`('merchant_fund_ledger', 'idx_fund_ledger_merchant_time', 0, '`merchant_id`,`posted_time`');
CALL `rollback_replace_index`('merchant_fund_ledger', 'idx_fund_ledger_business_time', 0, '`business_type`,`posted_time`');
CALL `rollback_replace_index`('merchant_fund_recharge', 'idx_fund_recharge_list', 0, NULL);
CALL `rollback_replace_index`('merchant_fund_recharge', 'idx_fund_recharge_status_time', 0, '`recharge_status`,`create_time`');
CALL `rollback_replace_index`('merchant_fund_recharge', 'idx_fund_recharge_merchant_time', 0, '`merchant_id`,`create_time`');
CALL `rollback_replace_index`('merchant_fund_recharge', 'idx_fund_recharge_account_time', 0, '`account_id`,`create_time`');
CALL `rollback_replace_index`('merchant_reserve_item', 'uk_reserve_merchant_source_business', 0, NULL);
CALL `rollback_replace_index`('merchant_reserve_item', 'idx_reserve_account_status_release', 0, NULL);
CALL `rollback_replace_index`('merchant_reserve_item', 'idx_reserve_status_release', 0, NULL);
CALL `rollback_replace_index`('merchant_reserve_item', 'idx_reserve_merchant_status', 0, '`merchant_id`,`reserve_status`,`expected_release_date`');
CALL `rollback_replace_index`('settlement_holiday_calendar', 'idx_settlement_calendar_year_date', 0, '`calendar_year_id`,`calendar_date`,`deleted`');

DROP PROCEDURE IF EXISTS `rollback_pending_balance_indexes`;
DELIMITER $$
CREATE PROCEDURE `rollback_pending_balance_indexes`()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE target_table VARCHAR(64);
    DECLARE table_cursor CURSOR FOR
        SELECT table_name FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name REGEXP '^transaction_operation(_[0-9]{6})?$';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
    OPEN table_cursor;
    table_loop: LOOP
        FETCH table_cursor INTO target_table;
        IF done = 1 THEN LEAVE table_loop; END IF;
        CALL `rollback_replace_index`(
            target_table, 'idx_pending_fund_balance', 0,
            '`merchant_id`,`transaction_status`,`settlement_status`,`transaction_type`,`transaction_date_time`'
        );
    END LOOP;
    CLOSE table_cursor;
END$$
DELIMITER ;
CALL `rollback_pending_balance_indexes`();
DROP PROCEDURE `rollback_pending_balance_indexes`;
DROP PROCEDURE `rollback_replace_index`;

DROP PROCEDURE IF EXISTS `rollback_add_column`;
DELIMITER $$
CREATE PROCEDURE `rollback_add_column`(IN target_table VARCHAR(64), IN target_column VARCHAR(64), IN add_definition TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = target_table AND column_name = target_column
    ) THEN
        SET @rollback_add_column_sql = CONCAT('ALTER TABLE `', target_table, '` ADD COLUMN ', add_definition);
        PREPARE rollback_add_column_statement FROM @rollback_add_column_sql;
        EXECUTE rollback_add_column_statement;
        DEALLOCATE PREPARE rollback_add_column_statement;
    END IF;
END$$
DELIMITER ;

CALL `rollback_add_column`('fee_plan_version', 'regular_delay_unit',
    '`regular_delay_unit` CHAR(1) NOT NULL DEFAULT ''T'' COMMENT ''兼容历史版本；与initial_delay_unit一致'' AFTER `initial_delay_days`');
UPDATE fee_plan_version SET regular_delay_unit = initial_delay_unit;

CALL `rollback_add_column`('merchant_fund_ledger', 'balance_type',
    '`balance_type` VARCHAR(16) NOT NULL DEFAULT ''AVAILABLE'' COMMENT ''余额类型：AVAILABLE'' AFTER `merchant_id`');

CALL `rollback_add_column`('merchant_fund_account', 'reverse_restricted',
    '`reverse_restricted` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否暂停产生资金流出的主动逆向交易'' AFTER `account_status`');
UPDATE merchant_fund_account SET reverse_restricted = CASE WHEN available_balance < 0 THEN 1 ELSE 0 END;

DROP PROCEDURE `rollback_add_column`;

DROP PROCEDURE IF EXISTS `rollback_drop_column`;
DELIMITER $$
CREATE PROCEDURE `rollback_drop_column`(IN target_table VARCHAR(64), IN target_column VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = target_table AND column_name = target_column
    ) THEN
        SET @rollback_drop_column_sql = CONCAT('ALTER TABLE `', target_table, '` DROP COLUMN `', target_column, '`');
        PREPARE rollback_drop_column_statement FROM @rollback_drop_column_sql;
        EXECUTE rollback_drop_column_statement;
        DEALLOCATE PREPARE rollback_drop_column_statement;
    END IF;
END$$
DELIMITER ;
CALL `rollback_drop_column`('fee_simulation_record', 'risk_service_type');
DROP PROCEDURE `rollback_drop_column`;

ALTER TABLE merchant_fund_account
    MODIFY COLUMN account_status VARCHAR(24) NOT NULL DEFAULT 'NORMAL'
        COMMENT '人工状态：NORMAL、FROZEN、CLOSED；负余额限制由reverse_restricted表达';
ALTER TABLE merchant_fund_ledger COMMENT = '商户余额和保证金不可变流水表';
