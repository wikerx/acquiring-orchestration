-- 白名单名单表对齐黑名单交易检索结构。
-- 执行前请确认已备份当前库；脚本会在发现未删除重复数据时主动中断，避免唯一索引创建失败后数据语义不清。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS migrate_risk_white_value_table;
DROP PROCEDURE IF EXISTS migrate_risk_white_merchant_table;
DROP PROCEDURE IF EXISTS migrate_risk_white_card_bin_table;
DROP PROCEDURE IF EXISTS migrate_risk_white_ip_table;
DROP PROCEDURE IF EXISTS migrate_risk_white_country_table;

DELIMITER $$

CREATE PROCEDURE migrate_risk_white_value_table(
    IN p_table_name VARCHAR(64),
    IN p_index_prefix VARCHAR(64),
    IN p_masked_type VARCHAR(255),
    IN p_cipher_comment VARCHAR(255),
    IN p_cipher_required TINYINT,
    IN p_has_card_brand TINYINT
)
BEGIN
    SET @schema_name = DATABASE();

    SET @sql = CONCAT('UPDATE ', p_table_name, ' SET merchant_id = '''' WHERE merchant_id IS NULL');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @sql = CONCAT(
        'SELECT COUNT(1) INTO @duplicate_count FROM (',
        'SELECT merchant_scope, COALESCE(merchant_id, '''') merchant_id, match_value_hash, deleted ',
        'FROM ', p_table_name, ' WHERE deleted = 0 ',
        'GROUP BY merchant_scope, COALESCE(merchant_id, ''''), match_value_hash, deleted HAVING COUNT(1) > 1',
        ') duplicate_rows'
    );
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    IF @duplicate_count > 0 THEN
        SET @message = CONCAT(p_table_name, ' has duplicate active whitelist records; clean duplicates before creating unique index');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @message;
    END IF;

    SET @old_indexes = CONCAT(
        'idx_risk_aml_card_hash,idx_risk_aml_card_merchant,idx_risk_aml_card_uniq_lookup,idx_risk_aml_card_range_num,idx_risk_aml_card_time,',
        'idx_', p_table_name, '_uniq_lookup,idx_', p_table_name, '_range_num,',
        'idx_', p_table_name, '_phone_trade_lookup,',
        'uk_', p_index_prefix, '_scope_hash_deleted,idx_', p_index_prefix, '_trade_lookup,idx_', p_index_prefix, '_merchant_time,idx_', p_index_prefix, '_time'
    );
    SET @drop_index_sql = (
        SELECT GROUP_CONCAT(CONCAT('DROP INDEX ', INDEX_NAME) SEPARATOR ', ')
        FROM (
            SELECT DISTINCT INDEX_NAME
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = p_table_name
              AND FIND_IN_SET(INDEX_NAME, @old_indexes) > 0
        ) matched_indexes
    );
    SET @sql = IF(@drop_index_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE ', p_table_name, ' ', @drop_index_sql));
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @drop_columns = IF(p_has_card_brand = 1,
        'match_value_start,match_value_end,match_value_start_number,match_value_end_number,country_alpha2,country_alpha3,country_numeric',
        'match_value_start,match_value_end,match_value_start_number,match_value_end_number,card_brand,country_alpha2,country_alpha3,country_numeric'
    );
    SET @drop_column_sql = (
        SELECT GROUP_CONCAT(CONCAT('DROP COLUMN ', COLUMN_NAME) SEPARATOR ', ')
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = p_table_name
          AND FIND_IN_SET(COLUMN_NAME, @drop_columns) > 0
    );
    SET @sql = IF(@drop_column_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE ', p_table_name, ' ', @drop_column_sql));
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @cipher_null_sql = IF(p_cipher_required = 1, 'NOT NULL', 'NULL');
    SET @sql = CONCAT(
        'ALTER TABLE ', p_table_name,
        ' MODIFY merchant_id VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''商户号，仅商户范围生效时必填；全局范围为空字符串'',',
        ' MODIFY match_value_masked VARCHAR(255) NOT NULL COMMENT ''', p_masked_type, ''',',
        ' MODIFY match_value_hash VARCHAR(128) NOT NULL COMMENT ''匹配值归一化哈希，用于交易检索和重复校验'',',
        ' MODIFY match_value_cipher VARCHAR(1024) ', @cipher_null_sql, ' COMMENT ''', p_cipher_comment, ''',',
        IF(p_has_card_brand = 1, ' MODIFY card_brand VARCHAR(64) NULL COMMENT ''卡品牌，后端根据卡号自动识别'',', ''),
        ' MODIFY risk_level VARCHAR(32) NOT NULL DEFAULT ''LOW'' COMMENT ''风险等级'',',
        ' MODIFY decision_action VARCHAR(32) NOT NULL DEFAULT ''PASS'' COMMENT ''命中动作：REJECT、REVIEW、PASS'''
    );
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @sql = CONCAT('CREATE UNIQUE INDEX uk_', p_index_prefix, '_scope_hash_deleted ON ', p_table_name, ' (merchant_scope, merchant_id, match_value_hash, deleted)');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @sql = CONCAT('CREATE INDEX idx_', p_index_prefix, '_trade_lookup ON ', p_table_name, ' (match_value_hash, merchant_scope, merchant_id, status, deleted, effective_time, expire_time)');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @sql = CONCAT('CREATE INDEX idx_', p_index_prefix, '_merchant_time ON ', p_table_name, ' (merchant_scope, merchant_id, update_time, id)');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @sql = CONCAT('CREATE INDEX idx_', p_index_prefix, '_time ON ', p_table_name, ' (update_time, id)');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END$$

CREATE PROCEDURE migrate_risk_white_merchant_table()
BEGIN
    SELECT COUNT(1) INTO @duplicate_count
    FROM (
        SELECT match_value_masked, match_value_hash, deleted
        FROM risk_white_merchant
        WHERE deleted = 0
        GROUP BY match_value_masked, match_value_hash, deleted
        HAVING COUNT(1) > 1
    ) duplicate_rows;

    IF @duplicate_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'risk_white_merchant has duplicate active merchant records; clean duplicates before normalizing merchant whitelist scope';
    END IF;

    UPDATE risk_white_merchant
    SET merchant_scope = 'MERCHANT',
        merchant_id = match_value_masked
    WHERE deleted = 0;

    ALTER TABLE risk_white_merchant
        MODIFY merchant_scope VARCHAR(32) NOT NULL DEFAULT 'MERCHANT' COMMENT '固定为MERCHANT，商户白名单仅对商户自身生效',
        MODIFY merchant_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '白名单商户号，与商户号展示值保持一致';
END$$

CREATE PROCEDURE migrate_risk_white_card_bin_table()
BEGIN
    SET @schema_name = DATABASE();
    SET @table_name = 'risk_white_card_bin';

    UPDATE risk_white_card_bin
    SET merchant_id = ''
    WHERE merchant_id IS NULL;

    SELECT COUNT(1) INTO @duplicate_count
    FROM (
        SELECT merchant_scope, COALESCE(merchant_id, '') merchant_id, match_value_start_number, match_value_end_number, deleted
        FROM risk_white_card_bin
        WHERE deleted = 0
        GROUP BY merchant_scope, COALESCE(merchant_id, ''), match_value_start_number, match_value_end_number, deleted
        HAVING COUNT(1) > 1
    ) duplicate_rows;

    IF @duplicate_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'risk_white_card_bin has duplicate active ranges; clean duplicates before creating unique index';
    END IF;

    SET @old_indexes = 'idx_risk_aml_card_hash,idx_risk_aml_card_merchant,idx_risk_aml_card_uniq_lookup,idx_risk_aml_card_range_num,idx_risk_aml_card_time,idx_risk_white_card_bin_uniq_lookup,idx_risk_white_card_bin_range_num,idx_risk_white_card_bin_bin_lookup,uk_white_card_bin_scope_range_deleted,idx_white_card_bin_bin_lookup,idx_white_card_bin_merchant_time,idx_white_card_bin_time';
    SET @drop_index_sql = (
        SELECT GROUP_CONCAT(CONCAT('DROP INDEX ', INDEX_NAME) SEPARATOR ', ')
        FROM (
            SELECT DISTINCT INDEX_NAME
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = @table_name
              AND FIND_IN_SET(INDEX_NAME, @old_indexes) > 0
        ) matched_indexes
    );
    SET @sql = IF(@drop_index_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE risk_white_card_bin ', @drop_index_sql));
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @drop_columns = 'country_alpha2,country_alpha3,country_numeric';
    SET @drop_column_sql = (
        SELECT GROUP_CONCAT(CONCAT('DROP COLUMN ', COLUMN_NAME) SEPARATOR ', ')
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = @table_name
          AND FIND_IN_SET(COLUMN_NAME, @drop_columns) > 0
    );
    SET @sql = IF(@drop_column_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE risk_white_card_bin ', @drop_column_sql));
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    ALTER TABLE risk_white_card_bin
        MODIFY merchant_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
        MODIFY match_value_masked VARCHAR(255) NOT NULL COMMENT '卡BIN区间展示值，保存为补齐后的起止值',
        MODIFY match_value_hash VARCHAR(128) NOT NULL COMMENT 'BIN区间归一化哈希，用于重复校验',
        MODIFY match_value_cipher VARCHAR(1024) NULL COMMENT 'BIN区间默认不保存敏感密文',
        MODIFY match_value_start VARCHAR(11) NULL COMMENT '起始BIN，录入不足11位时右补0',
        MODIFY match_value_end VARCHAR(11) NULL COMMENT '截止BIN，录入不足11位时右补9',
        MODIFY match_value_start_number DECIMAL(39,0) NULL COMMENT '起始BIN数值，交易卡号区间检索使用',
        MODIFY match_value_end_number DECIMAL(39,0) NULL COMMENT '截止BIN数值，交易卡号区间检索使用',
        MODIFY card_brand VARCHAR(64) NULL COMMENT '卡品牌，后端根据起始BIN自动识别',
        MODIFY risk_level VARCHAR(32) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
        MODIFY decision_action VARCHAR(32) NOT NULL DEFAULT 'PASS' COMMENT '命中动作：REJECT、REVIEW、PASS';

    CREATE UNIQUE INDEX uk_white_card_bin_scope_range_deleted ON risk_white_card_bin (merchant_scope, merchant_id, match_value_start_number, match_value_end_number, deleted);
    CREATE INDEX idx_white_card_bin_bin_lookup ON risk_white_card_bin (status, deleted, merchant_scope, merchant_id, match_value_start_number, match_value_end_number, effective_time, expire_time);
    CREATE INDEX idx_white_card_bin_merchant_time ON risk_white_card_bin (merchant_scope, merchant_id, update_time, id);
    CREATE INDEX idx_white_card_bin_time ON risk_white_card_bin (update_time, id);
END$$

CREATE PROCEDURE migrate_risk_white_ip_table()
BEGIN
    SET @schema_name = DATABASE();
    SET @table_name = 'risk_white_ip';

    UPDATE risk_white_ip
    SET merchant_id = ''
    WHERE merchant_id IS NULL;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = @table_name AND COLUMN_NAME = 'ip_version'
    ) THEN
        ALTER TABLE risk_white_ip ADD COLUMN ip_version VARCHAR(8) NULL COMMENT 'IP版本：IPV4、IPV6' AFTER match_value_end_number;
    END IF;

    SELECT COUNT(1) INTO @duplicate_count
    FROM (
        SELECT merchant_scope, COALESCE(merchant_id, '') merchant_id, match_value_hash, deleted
        FROM risk_white_ip
        WHERE deleted = 0
        GROUP BY merchant_scope, COALESCE(merchant_id, ''), match_value_hash, deleted
        HAVING COUNT(1) > 1
    ) duplicate_rows;

    IF @duplicate_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'risk_white_ip has duplicate active IP records; clean duplicates before creating unique index';
    END IF;

    SET @old_indexes = 'idx_risk_aml_card_hash,idx_risk_aml_card_merchant,idx_risk_aml_card_uniq_lookup,idx_risk_aml_card_range_num,idx_risk_aml_card_time,idx_risk_white_ip_uniq_lookup,idx_risk_white_ip_range_num,uk_white_ip_scope_hash_deleted,idx_white_ip_trade_lookup,idx_white_ip_merchant_time,idx_white_ip_time';
    SET @drop_index_sql = (
        SELECT GROUP_CONCAT(CONCAT('DROP INDEX ', INDEX_NAME) SEPARATOR ', ')
        FROM (
            SELECT DISTINCT INDEX_NAME
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = @table_name
              AND FIND_IN_SET(INDEX_NAME, @old_indexes) > 0
        ) matched_indexes
    );
    SET @sql = IF(@drop_index_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE risk_white_ip ', @drop_index_sql));
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @drop_columns = 'card_brand,country_alpha2,country_alpha3,country_numeric';
    SET @drop_column_sql = (
        SELECT GROUP_CONCAT(CONCAT('DROP COLUMN ', COLUMN_NAME) SEPARATOR ', ')
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = @table_name
          AND FIND_IN_SET(COLUMN_NAME, @drop_columns) > 0
    );
    SET @sql = IF(@drop_column_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE risk_white_ip ', @drop_column_sql));
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    ALTER TABLE risk_white_ip
        MODIFY merchant_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
        MODIFY match_value_masked VARCHAR(128) NOT NULL COMMENT 'IP地址展示值，白名单仅支持单IP',
        MODIFY match_value_hash VARCHAR(128) NOT NULL COMMENT 'IP地址归一化哈希，用于重复校验',
        MODIFY match_value_cipher VARCHAR(1024) NULL COMMENT 'IP地址默认不保存敏感密文',
        MODIFY match_value_start VARCHAR(128) NOT NULL COMMENT 'IP地址值',
        MODIFY match_value_end VARCHAR(128) NOT NULL COMMENT 'IP地址值，白名单与起始值一致',
        MODIFY match_value_start_number DECIMAL(39,0) NOT NULL COMMENT 'IP数值，交易检索使用',
        MODIFY match_value_end_number DECIMAL(39,0) NOT NULL COMMENT 'IP数值，白名单与起始数值一致',
        MODIFY ip_version VARCHAR(8) NOT NULL COMMENT 'IP版本：IPV4、IPV6',
        MODIFY risk_level VARCHAR(32) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
        MODIFY decision_action VARCHAR(32) NOT NULL DEFAULT 'PASS' COMMENT '命中动作：REJECT、REVIEW、PASS';

    CREATE UNIQUE INDEX uk_white_ip_scope_hash_deleted ON risk_white_ip (merchant_scope, merchant_id, match_value_hash, deleted);
    CREATE INDEX idx_white_ip_trade_lookup ON risk_white_ip (ip_version, match_value_start_number, match_value_end_number, merchant_scope, merchant_id, status, deleted, effective_time, expire_time);
    CREATE INDEX idx_white_ip_merchant_time ON risk_white_ip (merchant_scope, merchant_id, update_time, id);
    CREATE INDEX idx_white_ip_time ON risk_white_ip (update_time, id);
END$$

CREATE PROCEDURE migrate_risk_white_country_table(
    IN p_table_name VARCHAR(64),
    IN p_index_prefix VARCHAR(64)
)
BEGIN
    SET @schema_name = DATABASE();

    SET @sql = CONCAT(
        'UPDATE ', p_table_name, ' r ',
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
        'FROM ', p_table_name, ' WHERE deleted = 0 ',
        'GROUP BY merchant_scope, COALESCE(merchant_id, ''''), country_alpha3, deleted HAVING COUNT(1) > 1',
        ') duplicate_rows'
    );
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    IF @duplicate_count > 0 THEN
        SET @message = CONCAT(p_table_name, ' has duplicate active country records; clean duplicates before creating unique index');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @message;
    END IF;

    SET @old_indexes = CONCAT(
        'idx_risk_aml_card_hash,idx_risk_aml_card_merchant,idx_risk_aml_card_uniq_lookup,idx_risk_aml_card_range_num,idx_risk_aml_card_time,',
        'idx_', p_table_name, '_uniq_lookup,idx_', p_table_name, '_range_num,',
        'uk_', p_index_prefix, '_scope_country_deleted,idx_', p_index_prefix, '_trade_lookup,idx_', p_index_prefix, '_merchant_time,idx_', p_index_prefix, '_time'
    );
    SET @drop_index_sql = (
        SELECT GROUP_CONCAT(CONCAT('DROP INDEX ', INDEX_NAME) SEPARATOR ', ')
        FROM (
            SELECT DISTINCT INDEX_NAME
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = p_table_name
              AND FIND_IN_SET(INDEX_NAME, @old_indexes) > 0
        ) matched_indexes
    );
    SET @sql = IF(@drop_index_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE ', p_table_name, ' ', @drop_index_sql));
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @drop_columns = 'match_value_start,match_value_end,match_value_start_number,match_value_end_number,card_brand,country_numeric';
    SET @drop_column_sql = (
        SELECT GROUP_CONCAT(CONCAT('DROP COLUMN ', COLUMN_NAME) SEPARATOR ', ')
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = p_table_name
          AND FIND_IN_SET(COLUMN_NAME, @drop_columns) > 0
    );
    SET @sql = IF(@drop_column_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE ', p_table_name, ' ', @drop_column_sql));
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @sql = CONCAT(
        'ALTER TABLE ', p_table_name,
        ' MODIFY merchant_id VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''商户号，仅商户范围生效时必填；全局范围为空字符串'',',
        ' MODIFY match_value_masked VARCHAR(3) NOT NULL COMMENT ''国家或地区 Alpha-3 编码展示值'',',
        ' MODIFY match_value_hash VARCHAR(128) NOT NULL COMMENT ''国家或地区 Alpha-3 编码哈希，用于交易检索和重复校验'',',
        ' MODIFY match_value_cipher VARCHAR(1024) NULL COMMENT ''预留密文字段，国家或地区默认不加密存储'',',
        ' MODIFY country_alpha2 VARCHAR(2) NULL COMMENT ''国家或地区 Alpha-2 编码，仅用于管理端回显'',',
        ' MODIFY country_alpha3 VARCHAR(3) NOT NULL COMMENT ''国家或地区 Alpha-3 编码，交易匹配主字段'',',
        ' MODIFY risk_level VARCHAR(32) NOT NULL DEFAULT ''LOW'' COMMENT ''风险等级'',',
        ' MODIFY decision_action VARCHAR(32) NOT NULL DEFAULT ''PASS'' COMMENT ''命中动作：REJECT、REVIEW、PASS'''
    );
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @sql = CONCAT('CREATE UNIQUE INDEX uk_', p_index_prefix, '_scope_country_deleted ON ', p_table_name, ' (merchant_scope, merchant_id, country_alpha3, deleted)');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @sql = CONCAT('CREATE INDEX idx_', p_index_prefix, '_trade_lookup ON ', p_table_name, ' (country_alpha3, merchant_scope, merchant_id, status, deleted, effective_time, expire_time)');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @sql = CONCAT('CREATE INDEX idx_', p_index_prefix, '_merchant_time ON ', p_table_name, ' (merchant_scope, merchant_id, update_time, id)');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @sql = CONCAT('CREATE INDEX idx_', p_index_prefix, '_time ON ', p_table_name, ' (update_time, id)');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END$$

DELIMITER ;

CALL migrate_risk_white_value_table('risk_white_merchant', 'white_merchant', '商户号展示值', '商户白名单不保存敏感密文', 0, 0);
CALL migrate_risk_white_merchant_table();
CALL migrate_risk_white_value_table('risk_white_card_no', 'white_card_no', '卡号脱敏展示值，禁止保存完整卡号明文', '卡号密文，仅编辑授权时解密回显', 1, 1);
CALL migrate_risk_white_value_table('risk_white_card_fingerprint', 'white_card_fingerprint', '卡指纹脱敏展示值，禁止保存完整明文', '卡指纹密文，仅编辑授权时解密回显', 1, 0);
CALL migrate_risk_white_card_bin_table();
CALL migrate_risk_white_ip_table();
CALL migrate_risk_white_country_table('risk_white_trade_country', 'white_trade_country');
CALL migrate_risk_white_country_table('risk_white_issuer_country', 'white_issuer_country');
CALL migrate_risk_white_value_table('risk_white_email', 'white_email', '邮箱地址脱敏展示值，禁止保存完整邮箱明文', '邮箱地址密文，仅编辑授权时解密回显', 1, 0);
CALL migrate_risk_white_value_table('risk_white_email_domain', 'white_email_domain', '邮箱域名展示值', '邮箱域名不属于敏感明文，默认不加密存储', 0, 0);
CALL migrate_risk_white_value_table('risk_white_phone', 'white_phone', '手机号展示值', '手机号密文，仅编辑授权时解密回显', 1, 0);
CALL migrate_risk_white_value_table('risk_white_customer_id', 'white_customer_id', 'Customer ID 脱敏展示值', 'Customer ID 密文，仅编辑授权时解密回显', 1, 0);
CALL migrate_risk_white_value_table('risk_white_device_fingerprint', 'white_device_fingerprint', '设备指纹脱敏展示值，禁止保存完整明文', '设备指纹密文，仅编辑授权时解密回显', 1, 0);

DROP PROCEDURE IF EXISTS migrate_risk_white_value_table;
DROP PROCEDURE IF EXISTS migrate_risk_white_merchant_table;
DROP PROCEDURE IF EXISTS migrate_risk_white_card_bin_table;
DROP PROCEDURE IF EXISTS migrate_risk_white_ip_table;
DROP PROCEDURE IF EXISTS migrate_risk_white_country_table;
