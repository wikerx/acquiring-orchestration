-- AML、黑名单、白名单手机号名单表结构清理。
-- 手机号名单按手机号哈希精确匹配，不需要国家、区间或卡品牌字段。

SET @risk_phone_tables = 'risk_aml_phone,risk_black_phone,risk_white_phone';

DROP PROCEDURE IF EXISTS migrate_risk_phone_list_cleanup;
DELIMITER $$
CREATE PROCEDURE migrate_risk_phone_list_cleanup()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE current_table VARCHAR(128);
    DECLARE table_cursor CURSOR FOR
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND FIND_IN_SET(table_name, @risk_phone_tables);
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN table_cursor;
    table_loop: LOOP
        FETCH table_cursor INTO current_table;
        IF done = 1 THEN
            LEAVE table_loop;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = current_table AND index_name = CONCAT('idx_', current_table, '_range_num')) THEN
            SET @sql = CONCAT('DROP INDEX idx_', current_table, '_range_num ON ', current_table);
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = current_table AND column_name = 'match_value_start') THEN
            SET @sql = CONCAT('ALTER TABLE ', current_table, ' DROP COLUMN match_value_start');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = current_table AND column_name = 'match_value_end') THEN
            SET @sql = CONCAT('ALTER TABLE ', current_table, ' DROP COLUMN match_value_end');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = current_table AND column_name = 'match_value_start_number') THEN
            SET @sql = CONCAT('ALTER TABLE ', current_table, ' DROP COLUMN match_value_start_number');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = current_table AND column_name = 'match_value_end_number') THEN
            SET @sql = CONCAT('ALTER TABLE ', current_table, ' DROP COLUMN match_value_end_number');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = current_table AND column_name = 'card_brand') THEN
            SET @sql = CONCAT('ALTER TABLE ', current_table, ' DROP COLUMN card_brand');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = current_table AND column_name = 'country_alpha2') THEN
            SET @sql = CONCAT('ALTER TABLE ', current_table, ' DROP COLUMN country_alpha2');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = current_table AND column_name = 'country_alpha3') THEN
            SET @sql = CONCAT('ALTER TABLE ', current_table, ' DROP COLUMN country_alpha3');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = current_table AND column_name = 'country_numeric') THEN
            SET @sql = CONCAT('ALTER TABLE ', current_table, ' DROP COLUMN country_numeric');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = current_table AND index_name = CONCAT('idx_', current_table, '_phone_trade_lookup')) THEN
            SET @sql = CONCAT('CREATE INDEX idx_', current_table, '_phone_trade_lookup ON ', current_table, ' (match_value_hash, merchant_scope, merchant_id, status, deleted, effective_time, expire_time)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END LOOP;
    CLOSE table_cursor;
END$$
DELIMITER ;

CALL migrate_risk_phone_list_cleanup();
DROP PROCEDURE IF EXISTS migrate_risk_phone_list_cleanup;
