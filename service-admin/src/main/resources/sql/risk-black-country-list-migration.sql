-- 账单、收货、发卡行国家地区黑名单字段和索引收敛。
-- 国家地区名单交易匹配主字段统一为三位大写 Alpha-3 编码。

SET @risk_black_country_tables = 'risk_black_billing_country,risk_black_shipping_country,risk_black_issuer_country';

DROP PROCEDURE IF EXISTS migrate_risk_black_country_list;
DELIMITER $$
CREATE PROCEDURE migrate_risk_black_country_list()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE current_table VARCHAR(128);
    DECLARE duplicate_count BIGINT DEFAULT 0;
    DECLARE table_cursor CURSOR FOR
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND FIND_IN_SET(table_name, @risk_black_country_tables);
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN table_cursor;
    table_loop: LOOP
        FETCH table_cursor INTO current_table;
        IF done = 1 THEN
            LEAVE table_loop;
        END IF;

        SET @sql = CONCAT(
            'UPDATE ', current_table, ' r ',
            'LEFT JOIN base_iso_country c ON c.alpha2_code = r.country_alpha2 AND c.status = 1 AND c.deleted = 0 ',
            'SET r.merchant_id = COALESCE(r.merchant_id, ''''), ',
            'r.country_alpha3 = UPPER(COALESCE(NULLIF(r.country_alpha3, ''''), c.alpha3_code, r.match_value_masked)), ',
            'r.match_value_masked = UPPER(COALESCE(NULLIF(r.country_alpha3, ''''), c.alpha3_code, r.match_value_masked)), ',
            'r.match_value_hash = SHA2(UPPER(COALESCE(NULLIF(r.country_alpha3, ''''), c.alpha3_code, r.match_value_masked)), 256) ',
            'WHERE r.deleted = 0'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET @sql = CONCAT(
            'SELECT COUNT(1) INTO @duplicate_count FROM (',
            'SELECT merchant_scope, COALESCE(merchant_id, '''') merchant_id, country_alpha3, deleted ',
            'FROM ', current_table, ' WHERE deleted = 0 ',
            'GROUP BY merchant_scope, COALESCE(merchant_id, ''''), country_alpha3, deleted HAVING COUNT(1) > 1',
            ') duplicate_rows'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET duplicate_count = @duplicate_count;
        IF duplicate_count > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'risk black country list has duplicate active records; clean duplicates before creating unique index';
        END IF;

        SET @old_indexes = 'idx_risk_aml_card_hash,idx_risk_aml_card_merchant,idx_risk_aml_card_time,idx_risk_black_billing_country_uniq_lookup,idx_risk_black_billing_country_range_num,idx_risk_black_shipping_country_uniq_lookup,idx_risk_black_shipping_country_range_num,idx_risk_black_issuer_country_uniq_lookup,idx_risk_black_issuer_country_range_num,uk_black_billing_country_scope_country_deleted,uk_black_shipping_country_scope_country_deleted,uk_black_issuer_country_scope_country_deleted,idx_black_billing_country_trade_lookup,idx_black_shipping_country_trade_lookup,idx_black_issuer_country_trade_lookup,idx_black_billing_country_merchant_time,idx_black_shipping_country_merchant_time,idx_black_issuer_country_merchant_time,idx_black_billing_country_time,idx_black_shipping_country_time,idx_black_issuer_country_time';
        SET @sql = (
            SELECT GROUP_CONCAT(CONCAT('DROP INDEX ', INDEX_NAME) SEPARATOR ', ')
            FROM (
                SELECT DISTINCT INDEX_NAME
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = current_table
                  AND FIND_IN_SET(INDEX_NAME, @old_indexes) > 0
            ) matched_indexes
        );
        SET @sql = IF(@sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE ', current_table, ' ', @sql));
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET @drop_columns = 'match_value_start,match_value_end,match_value_start_number,match_value_end_number,card_brand,country_numeric';
        SET @sql = (
            SELECT GROUP_CONCAT(CONCAT('DROP COLUMN ', COLUMN_NAME) SEPARATOR ', ')
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = current_table
              AND FIND_IN_SET(COLUMN_NAME, @drop_columns) > 0
        );
        SET @sql = IF(@sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE ', current_table, ' ', @sql));
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET @prefix = REPLACE(REPLACE(current_table, 'risk_', ''), '_', '_');
        SET @uk_name = CONCAT('uk_', REPLACE(current_table, 'risk_', ''), '_scope_country_deleted');
        SET @lookup_name = CONCAT('idx_', REPLACE(current_table, 'risk_', ''), '_trade_lookup');
        SET @merchant_time_name = CONCAT('idx_', REPLACE(current_table, 'risk_', ''), '_merchant_time');
        SET @time_name = CONCAT('idx_', REPLACE(current_table, 'risk_', ''), '_time');

        SET @sql = CONCAT('ALTER TABLE ', current_table,
            ' MODIFY merchant_id VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''商户号，仅商户范围生效时必填；全局范围为空字符串'',',
            ' MODIFY match_value_masked VARCHAR(3) NOT NULL COMMENT ''国家或地区 Alpha-3 编码展示值'',',
            ' MODIFY match_value_hash VARCHAR(128) NOT NULL COMMENT ''国家或地区 Alpha-3 编码哈希，用于交易检索和重复校验'',',
            ' MODIFY match_value_cipher VARCHAR(1024) NULL COMMENT ''预留密文字段，国家或地区默认不加密存储'',',
            ' MODIFY country_alpha2 VARCHAR(2) NULL COMMENT ''国家或地区 Alpha-2 编码，仅用于管理端回显'',',
            ' MODIFY country_alpha3 VARCHAR(3) NOT NULL COMMENT ''国家或地区 Alpha-3 编码，交易匹配主字段'',',
            ' MODIFY risk_level VARCHAR(32) NOT NULL DEFAULT ''HIGH'' COMMENT ''风险等级'''
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET @sql = CONCAT('CREATE UNIQUE INDEX ', @uk_name, ' ON ', current_table, ' (merchant_scope, merchant_id, country_alpha3, deleted)');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET @sql = CONCAT('CREATE INDEX ', @lookup_name, ' ON ', current_table, ' (country_alpha3, merchant_scope, merchant_id, status, deleted, effective_time, expire_time)');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET @sql = CONCAT('CREATE INDEX ', @merchant_time_name, ' ON ', current_table, ' (merchant_scope, merchant_id, update_time, id)');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET @sql = CONCAT('CREATE INDEX ', @time_name, ' ON ', current_table, ' (update_time, id)');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE table_cursor;
END$$
DELIMITER ;

CALL migrate_risk_black_country_list();
DROP PROCEDURE IF EXISTS migrate_risk_black_country_list;
