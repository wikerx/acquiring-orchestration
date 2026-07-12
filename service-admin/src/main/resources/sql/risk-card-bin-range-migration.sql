-- 卡BIN/区间名单表结构优化迁移草案。
-- 执行前请先确认当前环境已完成数据备份，并确认管理端代码已发布到不再读写 country_alpha2 / country_alpha3 / country_numeric。

SET @risk_card_bin_tables = 'risk_aml_card_bin,risk_black_card_bin,risk_white_card_bin';

DROP PROCEDURE IF EXISTS migrate_risk_card_bin_range;
DELIMITER $$
CREATE PROCEDURE migrate_risk_card_bin_range()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE current_table VARCHAR(128);
    DECLARE table_cursor CURSOR FOR
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND FIND_IN_SET(table_name, @risk_card_bin_tables);
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN table_cursor;
    table_loop: LOOP
        FETCH table_cursor INTO current_table;
        IF done = 1 THEN
            LEAVE table_loop;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = current_table AND column_name = 'match_value_start') THEN
            SET @sql = CONCAT('ALTER TABLE ', current_table, ' MODIFY COLUMN match_value_start VARCHAR(11) NULL COMMENT ''卡BIN起始值，统一右补0至11位''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = current_table AND column_name = 'match_value_end') THEN
            SET @sql = CONCAT('ALTER TABLE ', current_table, ' MODIFY COLUMN match_value_end VARCHAR(11) NULL COMMENT ''卡BIN截止值，统一右补9至11位''');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = current_table AND index_name = CONCAT('idx_', current_table, '_bin_lookup')) THEN
            SET @sql = CONCAT('CREATE INDEX idx_', current_table, '_bin_lookup ON ', current_table, ' (status, deleted, merchant_scope, merchant_id, match_value_start_number, match_value_end_number, effective_time, expire_time)');
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
    END LOOP;
    CLOSE table_cursor;
END$$
DELIMITER ;

-- CALL migrate_risk_card_bin_range();
-- DROP PROCEDURE IF EXISTS migrate_risk_card_bin_range;
