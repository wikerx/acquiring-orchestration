-- 费用、资金账户结构收敛和查询索引优化。
-- 生产环境执行前应确认主从延迟，并在低峰期评估 ALTER TABLE 元数据锁影响。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `validate_fee_account_optimization`;
DELIMITER $$
CREATE PROCEDURE `validate_fee_account_optimization`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fee_plan_version' AND column_name = 'regular_delay_unit'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM fee_plan_version
            WHERE regular_delay_unit IS NULL OR regular_delay_unit <> initial_delay_unit
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fee_plan_version contains inconsistent settlement delay units';
        END IF;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'merchant_fund_ledger' AND column_name = 'balance_type'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM merchant_fund_ledger
            WHERE balance_type IS NULL OR balance_type <> 'AVAILABLE'
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'merchant_fund_ledger contains unsupported balance types';
        END IF;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'merchant_fund_account' AND column_name = 'reverse_restricted'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM merchant_fund_account
            WHERE reverse_restricted IS NULL OR reverse_restricted <> (available_balance < 0)
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'merchant_fund_account contains inconsistent reverse restriction flags';
        END IF;
    END IF;
    IF EXISTS (
        SELECT merchant_id, source_business_no
        FROM merchant_reserve_item
        GROUP BY merchant_id, source_business_no
        HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'merchant_reserve_item contains duplicate merchant source businesses';
    END IF;
    IF EXISTS (
        SELECT 1 FROM merchant_fund_recharge WHERE amount < 100 OR amount > 100000000
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'merchant_fund_recharge contains an out-of-range amount';
    END IF;
    IF EXISTS (
        SELECT 1 FROM merchant_fund_ledger
        WHERE amount <= 0 OR direction NOT IN ('CREDIT', 'DEBIT')
           OR (direction = 'CREDIT' AND balance_after <> balance_before + amount)
           OR (direction = 'DEBIT' AND balance_after <> balance_before - amount)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'merchant_fund_ledger contains inconsistent balance arithmetic';
    END IF;
    IF EXISTS (
        SELECT 1 FROM fee_rule
        WHERE percentage_rate < 0 OR fixed_amount_usd < 0
           OR minimum_amount_usd < 0 OR maximum_amount_usd < 0
           OR (minimum_amount_usd IS NOT NULL AND maximum_amount_usd IS NOT NULL
               AND maximum_amount_usd < minimum_amount_usd)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fee_rule contains invalid amount limits';
    END IF;
    IF EXISTS (
        SELECT 1 FROM fee_rule_tier
        WHERE lower_bound < 0 OR (upper_bound IS NOT NULL AND upper_bound <= lower_bound)
           OR percentage_rate < 0 OR fixed_amount_usd < 0
           OR minimum_amount_usd < 0 OR maximum_amount_usd < 0
           OR (minimum_amount_usd IS NOT NULL AND maximum_amount_usd IS NOT NULL
               AND maximum_amount_usd < minimum_amount_usd)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fee_rule_tier contains invalid ranges or amount limits';
    END IF;
    IF EXISTS (
        SELECT 1 FROM fee_plan_version
        WHERE reserve_rate < 0 OR reserve_rate > 100
           OR reserve_delay_unit NOT IN ('T', 'D') OR reserve_delay_days < 1
           OR initial_delay_unit NOT IN ('T', 'D') OR initial_delay_days < 1 OR regular_delay_days < 1
           OR settlement_frequency NOT IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY')
           OR (settlement_frequency = 'DAILY' AND frequency_day IS NOT NULL)
           OR (settlement_frequency IN ('WEEKLY', 'BIWEEKLY') AND (frequency_day IS NULL OR frequency_day NOT BETWEEN 1 AND 7))
           OR (settlement_frequency = 'MONTHLY' AND (frequency_day IS NULL OR frequency_day NOT BETWEEN 1 AND 28))
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fee_plan_version contains invalid settlement settings';
    END IF;
    IF EXISTS (
        SELECT 1 FROM merchant_reserve_item
        WHERE retained_amount <= 0 OR released_amount < 0 OR released_amount > retained_amount
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'merchant_reserve_item contains invalid retained or released amounts';
    END IF;
END$$
DELIMITER ;

CALL `validate_fee_account_optimization`();
DROP PROCEDURE `validate_fee_account_optimization`;

DROP PROCEDURE IF EXISTS `ensure_fee_account_column`;
DELIMITER $$
CREATE PROCEDURE `ensure_fee_account_column`(
    IN target_table VARCHAR(64),
    IN target_column VARCHAR(64),
    IN add_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = target_table AND column_name = target_column
    ) THEN
        SET @column_sql = CONCAT('ALTER TABLE `', target_table, '` ADD COLUMN ', add_definition);
        PREPARE column_statement FROM @column_sql;
        EXECUTE column_statement;
        DEALLOCATE PREPARE column_statement;
    END IF;
END$$
DELIMITER ;

CALL `ensure_fee_account_column`(
    'fee_simulation_record',
    'risk_service_type',
    '`risk_service_type` VARCHAR(16) NOT NULL DEFAULT ''NONE'' COMMENT ''风控类型：INTERNAL、EXTERNAL、THREE_DS；非风控为NONE'' AFTER `payment_method`'
);
DROP PROCEDURE `ensure_fee_account_column`;

UPDATE fee_simulation_record simulation
JOIN fee_rule rule ON rule.id = simulation.matched_rule_id
SET simulation.risk_service_type = CASE
    WHEN simulation.fee_category = 'RISK_FEE' THEN rule.risk_service_type
    ELSE 'NONE'
END
WHERE simulation.risk_service_type = 'NONE';

UPDATE merchant_fund_account
SET account_status = 'NORMAL', update_time = CURRENT_TIMESTAMP(3)
WHERE account_status = 'NEGATIVE_BALANCE';

DROP PROCEDURE IF EXISTS `drop_fee_account_column`;
DELIMITER $$
CREATE PROCEDURE `drop_fee_account_column`(IN target_table VARCHAR(64), IN target_column VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = target_table AND column_name = target_column
    ) THEN
        SET @drop_column_sql = CONCAT('ALTER TABLE `', target_table, '` DROP COLUMN `', target_column, '`');
        PREPARE drop_column_statement FROM @drop_column_sql;
        EXECUTE drop_column_statement;
        DEALLOCATE PREPARE drop_column_statement;
    END IF;
END$$
DELIMITER ;

CALL `drop_fee_account_column`('fee_plan_version', 'regular_delay_unit');
CALL `drop_fee_account_column`('merchant_fund_ledger', 'balance_type');
CALL `drop_fee_account_column`('merchant_fund_account', 'reverse_restricted');
DROP PROCEDURE `drop_fee_account_column`;

ALTER TABLE merchant_fund_account
    MODIFY COLUMN account_status VARCHAR(24) NOT NULL DEFAULT 'NORMAL'
        COMMENT '人工状态：NORMAL、FROZEN、CLOSED；负余额限制由可用余额实时派生';
ALTER TABLE merchant_fund_ledger COMMENT = '商户可用余额不可变流水表';

DROP PROCEDURE IF EXISTS `ensure_fee_account_index`;
DELIMITER $$
CREATE PROCEDURE `ensure_fee_account_index`(
    IN target_table VARCHAR(64),
    IN target_index VARCHAR(64),
    IN target_unique TINYINT,
    IN target_columns TEXT
)
BEGIN
    DECLARE current_columns TEXT DEFAULT NULL;
    DECLARE current_non_unique INT DEFAULT NULL;

    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ','), MIN(non_unique)
      INTO current_columns, current_non_unique
      FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = target_table AND index_name = target_index;

    IF current_columns IS NOT NULL
       AND (current_columns <> REPLACE(target_columns, '`', '')
            OR current_non_unique <> IF(target_unique = 1, 0, 1)) THEN
        SET @drop_index_sql = CONCAT('ALTER TABLE `', target_table, '` DROP INDEX `', target_index, '`');
        PREPARE drop_index_statement FROM @drop_index_sql;
        EXECUTE drop_index_statement;
        DEALLOCATE PREPARE drop_index_statement;
        SET current_columns = NULL;
    END IF;

    IF current_columns IS NULL THEN
        SET @add_index_sql = CONCAT(
            'ALTER TABLE `', target_table, '` ADD ',
            IF(target_unique = 1, 'UNIQUE ', ''),
            'INDEX `', target_index, '` (', target_columns, ')');
        PREPARE add_index_statement FROM @add_index_sql;
        EXECUTE add_index_statement;
        DEALLOCATE PREPARE add_index_statement;
    END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS `drop_fee_account_index`;
DELIMITER $$
CREATE PROCEDURE `drop_fee_account_index`(IN target_table VARCHAR(64), IN target_index VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = target_table AND index_name = target_index
    ) THEN
        SET @drop_named_index_sql = CONCAT('ALTER TABLE `', target_table, '` DROP INDEX `', target_index, '`');
        PREPARE drop_named_index_statement FROM @drop_named_index_sql;
        EXECUTE drop_named_index_statement;
        DEALLOCATE PREPARE drop_named_index_statement;
    END IF;
END$$
DELIMITER ;

CALL `drop_fee_account_index`('fee_plan', 'idx_fee_plan_source_template');
CALL `ensure_fee_account_index`('fee_plan', 'idx_fee_plan_type_list', 0, '`plan_type`,`deleted`,`update_time`,`id`');
CALL `ensure_fee_account_index`('fee_plan', 'idx_fee_plan_type_status', 0, '`plan_type`,`status`,`deleted`,`update_time`,`id`');

CALL `ensure_fee_account_index`('fee_plan_version', 'idx_fee_version_review', 0, '`version_status`,`deleted`,`submit_time`,`id`');
CALL `ensure_fee_account_index`('fee_plan_version', 'idx_fee_version_history', 0, '`plan_id`,`deleted`,`version_no`,`id`');
CALL `ensure_fee_account_index`('fee_plan_version', 'idx_fee_version_effective', 0, '`plan_id`,`deleted`,`effective_time`,`id`');
CALL `ensure_fee_account_index`('fee_rule', 'idx_fee_rule_version', 0, '`plan_version_id`,`deleted`,`sort_no`,`id`');
CALL `ensure_fee_account_index`('fee_rule_tier', 'idx_fee_tier_rule', 0, '`fee_rule_id`,`deleted`,`sort_no`,`id`');

CALL `ensure_fee_account_index`('fee_simulation_record', 'idx_fee_simulation_create_time', 0, '`create_time`,`id`');
CALL `ensure_fee_account_index`('fee_simulation_record', 'idx_fee_simulation_plan_time', 0, '`plan_version_id`,`create_time`,`id`');
CALL `ensure_fee_account_index`('fee_simulation_record', 'idx_fee_simulation_merchant_time', 0, '`merchant_id`,`create_time`,`id`');
CALL `ensure_fee_account_index`('fee_simulation_record', 'idx_fee_simulation_transaction_time', 0, '`transaction_type`,`create_time`,`id`');

CALL `ensure_fee_account_index`('merchant_fund_account', 'idx_fund_account_list', 0, '`deleted`,`update_time`,`id`');
CALL `ensure_fee_account_index`('merchant_fund_account', 'idx_fund_account_status', 0, '`account_status`,`deleted`,`update_time`,`id`');

CALL `ensure_fee_account_index`('merchant_fund_ledger', 'idx_fund_ledger_account_time', 0, '`account_id`,`merchant_id`,`posted_time`,`id`');
CALL `ensure_fee_account_index`('merchant_fund_ledger', 'idx_fund_ledger_merchant_time', 0, '`merchant_id`,`posted_time`,`id`');
CALL `ensure_fee_account_index`('merchant_fund_ledger', 'idx_fund_ledger_business_time', 0, '`business_type`,`posted_time`,`id`');

CALL `ensure_fee_account_index`('merchant_fund_recharge', 'idx_fund_recharge_list', 0, '`deleted`,`create_time`,`id`');
CALL `ensure_fee_account_index`('merchant_fund_recharge', 'idx_fund_recharge_status_time', 0, '`recharge_status`,`deleted`,`create_time`,`id`');
CALL `ensure_fee_account_index`('merchant_fund_recharge', 'idx_fund_recharge_merchant_time', 0, '`merchant_id`,`deleted`,`create_time`,`id`');
CALL `ensure_fee_account_index`('merchant_fund_recharge', 'idx_fund_recharge_account_time', 0, '`account_id`,`deleted`,`create_time`,`id`');

CALL `drop_fee_account_index`('merchant_reserve_item', 'idx_reserve_merchant_status');
CALL `ensure_fee_account_index`('merchant_reserve_item', 'uk_reserve_merchant_source_business', 1, '`merchant_id`,`source_business_no`');
CALL `ensure_fee_account_index`('merchant_reserve_item', 'idx_reserve_account_status_release', 0, '`account_id`,`merchant_id`,`reserve_status`,`expected_release_date`,`id`');
CALL `ensure_fee_account_index`('merchant_reserve_item', 'idx_reserve_status_release', 0, '`reserve_status`,`expected_release_date`,`account_id`,`id`');

CALL `ensure_fee_account_index`('settlement_holiday_calendar', 'idx_settlement_calendar_year_date', 0, '`calendar_year_id`,`deleted`,`calendar_date`');

DROP PROCEDURE IF EXISTS `optimize_pending_balance_indexes`;
DELIMITER $$
CREATE PROCEDURE `optimize_pending_balance_indexes`()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE target_table VARCHAR(64);
    DECLARE target_column_count INT DEFAULT 0;
    DECLARE pending_error VARCHAR(128);
    DECLARE table_cursor CURSOR FOR
        SELECT table_name
          FROM information_schema.tables
         WHERE table_schema = DATABASE()
           AND table_name REGEXP '^transaction_operation(_[0-9]{6})?$';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN table_cursor;
    table_loop: LOOP
        FETCH table_cursor INTO target_table;
        IF done = 1 THEN
            LEAVE table_loop;
        END IF;
        SELECT COUNT(DISTINCT column_name)
          INTO target_column_count
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = target_table
           AND column_name IN ('merchant_id', 'transaction_status', 'settlement_status', 'deleted',
                               'transaction_type', 'transaction_date_time', 'label_currency');
        IF target_column_count <> 7 THEN
            SET pending_error = CONCAT(target_table, ' is missing pending balance query columns');
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = pending_error;
        END IF;
        CALL `ensure_fee_account_index`(
            target_table,
            'idx_pending_fund_balance',
            0,
            '`merchant_id`,`transaction_status`,`settlement_status`,`deleted`,`transaction_type`,`transaction_date_time`,`label_currency`'
        );
    END LOOP;
    CLOSE table_cursor;
END$$
DELIMITER ;

CALL `optimize_pending_balance_indexes`();
DROP PROCEDURE `optimize_pending_balance_indexes`;
DROP PROCEDURE `drop_fee_account_index`;
DROP PROCEDURE `ensure_fee_account_index`;

DROP PROCEDURE IF EXISTS `ensure_fee_account_check`;
DELIMITER $$
CREATE PROCEDURE `ensure_fee_account_check`(
    IN target_table VARCHAR(64),
    IN target_constraint VARCHAR(64),
    IN check_expression TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE() AND table_name = target_table
          AND constraint_name = target_constraint AND constraint_type = 'CHECK'
    ) THEN
        SET @check_sql = CONCAT(
            'ALTER TABLE `', target_table, '` ADD CONSTRAINT `', target_constraint,
            '` CHECK (', check_expression, ')');
        PREPARE check_statement FROM @check_sql;
        EXECUTE check_statement;
        DEALLOCATE PREPARE check_statement;
    END IF;
END$$
DELIMITER ;

CALL `ensure_fee_account_check`('fee_plan_version', 'chk_fee_version_reserve',
    '`reserve_rate` BETWEEN 0 AND 100 AND `reserve_delay_unit` IN (''T'',''D'') AND `reserve_delay_days` >= 1');
CALL `ensure_fee_account_check`('fee_plan_version', 'chk_fee_version_settlement_cycle',
    '`initial_delay_unit` IN (''T'',''D'') AND `initial_delay_days` >= 1 AND `regular_delay_days` >= 1');
CALL `ensure_fee_account_check`('fee_plan_version', 'chk_fee_version_frequency',
    '(`settlement_frequency` = ''DAILY'' AND `frequency_day` IS NULL) OR (`settlement_frequency` IN (''WEEKLY'',''BIWEEKLY'') AND `frequency_day` BETWEEN 1 AND 7) OR (`settlement_frequency` = ''MONTHLY'' AND `frequency_day` BETWEEN 1 AND 28)');
CALL `ensure_fee_account_check`('fee_rule', 'chk_fee_rule_amount',
    '`percentage_rate` >= 0 AND `fixed_amount_usd` >= 0 AND (`minimum_amount_usd` IS NULL OR `minimum_amount_usd` >= 0) AND (`maximum_amount_usd` IS NULL OR `maximum_amount_usd` >= 0) AND (`minimum_amount_usd` IS NULL OR `maximum_amount_usd` IS NULL OR `maximum_amount_usd` >= `minimum_amount_usd`)');
CALL `ensure_fee_account_check`('fee_rule_tier', 'chk_fee_tier_range',
    '`lower_bound` >= 0 AND (`upper_bound` IS NULL OR `upper_bound` > `lower_bound`)');
CALL `ensure_fee_account_check`('fee_rule_tier', 'chk_fee_tier_amount',
    '`percentage_rate` >= 0 AND `fixed_amount_usd` >= 0 AND (`minimum_amount_usd` IS NULL OR `minimum_amount_usd` >= 0) AND (`maximum_amount_usd` IS NULL OR `maximum_amount_usd` >= 0) AND (`minimum_amount_usd` IS NULL OR `maximum_amount_usd` IS NULL OR `maximum_amount_usd` >= `minimum_amount_usd`)');
CALL `ensure_fee_account_check`('fee_simulation_record', 'chk_fee_simulation_risk_type',
    '`risk_service_type` IN (''NONE'',''INTERNAL'',''EXTERNAL'',''THREE_DS'')');
CALL `ensure_fee_account_check`('merchant_fund_account', 'chk_fund_account_status',
    '`account_status` IN (''NORMAL'',''FROZEN'',''CLOSED'')');
CALL `ensure_fee_account_check`('merchant_fund_ledger', 'chk_fund_ledger_balance',
    '`amount` > 0 AND `direction` IN (''CREDIT'',''DEBIT'') AND ((`direction` = ''CREDIT'' AND `balance_after` = `balance_before` + `amount`) OR (`direction` = ''DEBIT'' AND `balance_after` = `balance_before` - `amount`))');
CALL `ensure_fee_account_check`('merchant_fund_recharge', 'chk_fund_recharge_amount',
    '`amount` BETWEEN 100 AND 100000000');
CALL `ensure_fee_account_check`('merchant_reserve_item', 'chk_reserve_amount',
    '`retained_amount` > 0 AND `released_amount` >= 0 AND `released_amount` <= `retained_amount`');

DROP PROCEDURE `ensure_fee_account_check`;
