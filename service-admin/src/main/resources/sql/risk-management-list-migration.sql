SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_risk_list_tables $$
CREATE PROCEDURE migrate_risk_list_tables()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE current_table VARCHAR(128);
    DECLARE table_cursor CURSOR FOR
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
	          AND (
	              table_name LIKE 'risk_aml\\_%'
	              OR table_name LIKE 'risk_black\\_%'
	              OR table_name LIKE 'risk_white\\_%'
	          )
	          AND table_name NOT IN ('risk_black_region', 'risk_black_card_no');
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN table_cursor;
    read_loop: LOOP
        FETCH table_cursor INTO current_table;
        IF done = 1 THEN
            LEAVE read_loop;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = current_table AND column_name = 'match_value_cipher') THEN
            SET @sql = CONCAT('ALTER TABLE ', current_table, ' ADD COLUMN match_value_cipher VARCHAR(1024) NULL COMMENT ''匹配值密文，仅编辑授权时解密回显'' AFTER match_value_hash');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = current_table AND column_name = 'match_value_start_number') THEN
            SET @sql = CONCAT('ALTER TABLE ', current_table, ' ADD COLUMN match_value_start_number DECIMAL(39,0) NULL COMMENT ''区间起始数值，BIN和IP交易检索使用'' AFTER match_value_end');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = current_table AND column_name = 'match_value_end_number') THEN
            SET @sql = CONCAT('ALTER TABLE ', current_table, ' ADD COLUMN match_value_end_number DECIMAL(39,0) NULL COMMENT ''区间结束数值，BIN和IP交易检索使用'' AFTER match_value_start_number');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = current_table AND column_name = 'validity_type') THEN
            SET @sql = CONCAT('ALTER TABLE ', current_table, ' ADD COLUMN validity_type VARCHAR(32) NOT NULL DEFAULT ''SUPER_LONG'' COMMENT ''有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期'' AFTER expire_time');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = current_table AND column_name = 'validity_days') THEN
            SET @sql = CONCAT('ALTER TABLE ', current_table, ' ADD COLUMN validity_days INT NULL COMMENT ''有效天数，长期和限定有效期使用'' AFTER validity_type');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = current_table AND column_name = 'source_type') THEN
            SET @sql = CONCAT('ALTER TABLE ', current_table, ' ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT ''MANUAL'' COMMENT ''来源类型：MANUAL手工、IMPORT导入、SYSTEM系统'' AFTER validity_days');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = current_table AND index_name = CONCAT('idx_', current_table, '_uniq_lookup')) THEN
            SET @sql = CONCAT('CREATE INDEX idx_', current_table, '_uniq_lookup ON ', current_table, ' (merchant_scope, merchant_id, match_value_hash, deleted)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = current_table AND index_name = CONCAT('idx_', current_table, '_range_num')) THEN
            SET @sql = CONCAT('CREATE INDEX idx_', current_table, '_range_num ON ', current_table, ' (match_value_start_number, match_value_end_number, status, deleted)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END LOOP;
    CLOSE table_cursor;
END $$

DROP PROCEDURE IF EXISTS migrate_risk_black_region $$
CREATE PROCEDURE migrate_risk_black_region()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'risk_black_region') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'risk_black_region' AND column_name = 'validity_type') THEN
            ALTER TABLE risk_black_region ADD COLUMN validity_type VARCHAR(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期' AFTER expire_time;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'risk_black_region' AND column_name = 'validity_days') THEN
            ALTER TABLE risk_black_region ADD COLUMN validity_days INT NULL COMMENT '有效天数，长期和限定有效期使用' AFTER validity_type;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'risk_black_region' AND column_name = 'source_type') THEN
            ALTER TABLE risk_black_region ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统' AFTER validity_days;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'risk_black_region' AND index_name = 'uk_black_region_scope_area') THEN
            CREATE UNIQUE INDEX uk_black_region_scope_area ON risk_black_region (merchant_scope, merchant_id, region_match_level, country_alpha3, state_province_name, city_name, deleted);
        END IF;
    END IF;
END $$

DROP PROCEDURE IF EXISTS migrate_risk_black_card_no $$
CREATE PROCEDURE migrate_risk_black_card_no()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND column_name = 'match_value_cipher') THEN
            ALTER TABLE risk_black_card_no ADD COLUMN match_value_cipher VARCHAR(1024) NULL COMMENT '卡号密文，仅编辑授权时解密回显' AFTER match_value_hash;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND column_name = 'validity_type') THEN
            ALTER TABLE risk_black_card_no ADD COLUMN validity_type VARCHAR(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期' AFTER expire_time;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND column_name = 'validity_days') THEN
            ALTER TABLE risk_black_card_no ADD COLUMN validity_days INT NULL COMMENT '有效天数，长期和限定有效期使用' AFTER validity_type;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND column_name = 'source_type') THEN
            ALTER TABLE risk_black_card_no ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统' AFTER validity_days;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND index_name = 'idx_risk_aml_card_range_num') THEN
            DROP INDEX idx_risk_aml_card_range_num ON risk_black_card_no;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND index_name = 'idx_risk_black_card_no_range_num') THEN
            DROP INDEX idx_risk_black_card_no_range_num ON risk_black_card_no;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND index_name = 'idx_risk_black_card_no_uniq_lookup') THEN
            DROP INDEX idx_risk_black_card_no_uniq_lookup ON risk_black_card_no;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND index_name = 'idx_risk_aml_card_hash') THEN
            DROP INDEX idx_risk_aml_card_hash ON risk_black_card_no;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND index_name = 'idx_risk_aml_card_merchant') THEN
            DROP INDEX idx_risk_aml_card_merchant ON risk_black_card_no;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND index_name = 'idx_risk_aml_card_uniq_lookup') THEN
            DROP INDEX idx_risk_aml_card_uniq_lookup ON risk_black_card_no;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND index_name = 'idx_risk_aml_card_time') THEN
            DROP INDEX idx_risk_aml_card_time ON risk_black_card_no;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND column_name = 'match_value_start') THEN
            ALTER TABLE risk_black_card_no DROP COLUMN match_value_start;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND column_name = 'match_value_end') THEN
            ALTER TABLE risk_black_card_no DROP COLUMN match_value_end;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND column_name = 'match_value_start_number') THEN
            ALTER TABLE risk_black_card_no DROP COLUMN match_value_start_number;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND column_name = 'match_value_end_number') THEN
            ALTER TABLE risk_black_card_no DROP COLUMN match_value_end_number;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND column_name = 'country_alpha2') THEN
            ALTER TABLE risk_black_card_no DROP COLUMN country_alpha2;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND column_name = 'country_alpha3') THEN
            ALTER TABLE risk_black_card_no DROP COLUMN country_alpha3;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND column_name = 'country_numeric') THEN
            ALTER TABLE risk_black_card_no DROP COLUMN country_numeric;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND index_name = 'idx_black_card_no_trade_lookup') THEN
            CREATE INDEX idx_black_card_no_trade_lookup ON risk_black_card_no (match_value_hash, merchant_scope, merchant_id, status, deleted, effective_time, expire_time);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND index_name = 'idx_black_card_no_duplicate') THEN
            CREATE INDEX idx_black_card_no_duplicate ON risk_black_card_no (merchant_scope, merchant_id, match_value_hash, deleted);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND index_name = 'idx_black_card_no_merchant_time') THEN
            CREATE INDEX idx_black_card_no_merchant_time ON risk_black_card_no (merchant_scope, merchant_id, update_time, id);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'risk_black_card_no' AND index_name = 'idx_black_card_no_time') THEN
            CREATE INDEX idx_black_card_no_time ON risk_black_card_no (update_time, id);
        END IF;
    END IF;
END $$

DELIMITER ;

CALL migrate_risk_list_tables();
CALL migrate_risk_black_region();
CALL migrate_risk_black_card_no();
DROP PROCEDURE IF EXISTS migrate_risk_list_tables;
DROP PROCEDURE IF EXISTS migrate_risk_black_region;
DROP PROCEDURE IF EXISTS migrate_risk_black_card_no;
