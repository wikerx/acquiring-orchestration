-- 邮箱值类名单字段和索引收敛。
-- 适用：AML邮箱/域名、邮箱用户名黑名单、邮箱域名黑名单、邮箱地址白名单、邮箱域名白名单。

DROP PROCEDURE IF EXISTS migrate_risk_email_value_table;

DELIMITER $$

CREATE PROCEDURE migrate_risk_email_value_table(
    IN p_table_name VARCHAR(64),
    IN p_index_prefix VARCHAR(64),
    IN p_masked_comment VARCHAR(255),
    IN p_cipher_required TINYINT,
    IN p_cipher_comment VARCHAR(255),
    IN p_risk_level_default VARCHAR(32),
    IN p_decision_action_default VARCHAR(32)
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
        SET @message = CONCAT(p_table_name, ' has duplicate active email value records; clean duplicates before creating unique index');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @message;
    END IF;

    SET @old_indexes = CONCAT(
        'idx_risk_aml_card_hash,idx_risk_aml_card_merchant,idx_risk_aml_card_uniq_lookup,idx_risk_aml_card_range_num,idx_risk_aml_card_time,',
        'idx_', p_table_name, '_range_num,idx_', p_table_name, '_uniq_lookup,',
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

    SET @drop_columns = 'match_value_start,match_value_end,match_value_start_number,match_value_end_number,card_brand,country_alpha2,country_alpha3,country_numeric';

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
        ' MODIFY match_value_masked VARCHAR(255) NOT NULL COMMENT ''', p_masked_comment, ''',',
        ' MODIFY match_value_hash VARCHAR(128) NOT NULL COMMENT ''邮箱值归一化哈希，用于交易检索和重复校验'',',
        ' MODIFY match_value_cipher VARCHAR(1024) ', @cipher_null_sql, ' COMMENT ''', p_cipher_comment, ''',',
        ' MODIFY risk_level VARCHAR(32) NOT NULL DEFAULT ''', p_risk_level_default, ''' COMMENT ''风险等级'',',
        ' MODIFY decision_action VARCHAR(32) NOT NULL DEFAULT ''', p_decision_action_default, ''' COMMENT ''命中动作：REJECT、REVIEW、PASS'''
    );
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = p_table_name AND INDEX_NAME = CONCAT('uk_', p_index_prefix, '_scope_hash_deleted')
    ) THEN
        SET @sql = CONCAT('CREATE UNIQUE INDEX uk_', p_index_prefix, '_scope_hash_deleted ON ', p_table_name, ' (merchant_scope, merchant_id, match_value_hash, deleted)');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = p_table_name AND INDEX_NAME = CONCAT('idx_', p_index_prefix, '_trade_lookup')
    ) THEN
        SET @sql = CONCAT('CREATE INDEX idx_', p_index_prefix, '_trade_lookup ON ', p_table_name, ' (match_value_hash, merchant_scope, merchant_id, status, deleted, effective_time, expire_time)');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = p_table_name AND INDEX_NAME = CONCAT('idx_', p_index_prefix, '_merchant_time')
    ) THEN
        SET @sql = CONCAT('CREATE INDEX idx_', p_index_prefix, '_merchant_time ON ', p_table_name, ' (merchant_scope, merchant_id, update_time, id)');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = p_table_name AND INDEX_NAME = CONCAT('idx_', p_index_prefix, '_time')
    ) THEN
        SET @sql = CONCAT('CREATE INDEX idx_', p_index_prefix, '_time ON ', p_table_name, ' (update_time, id)');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL migrate_risk_email_value_table('risk_aml_email', 'aml_email', '邮箱地址脱敏值或邮箱域名展示值', 0, '完整邮箱地址密文，域名类记录为空', 'CRITICAL', 'REJECT');
CALL migrate_risk_email_value_table('risk_black_email_username', 'black_email_username', '邮箱用户名脱敏展示值，禁止保存完整明文', 1, '邮箱用户名密文，仅编辑授权时解密回显', 'HIGH', 'REJECT');
CALL migrate_risk_email_value_table('risk_black_email_domain', 'black_email_domain', '邮箱域名展示值', 0, '邮箱域名不属于敏感明文，默认不加密存储', 'HIGH', 'REJECT');
CALL migrate_risk_email_value_table('risk_white_email', 'white_email', '邮箱地址脱敏展示值，禁止保存完整邮箱明文', 1, '邮箱地址密文，仅编辑授权时解密回显', 'LOW', 'PASS');
CALL migrate_risk_email_value_table('risk_white_email_domain', 'white_email_domain', '邮箱域名展示值', 0, '邮箱域名不属于敏感明文，默认不加密存储', 'LOW', 'PASS');

DROP PROCEDURE IF EXISTS migrate_risk_email_value_table;
