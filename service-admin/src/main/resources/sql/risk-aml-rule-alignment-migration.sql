-- AML 功能补齐与内风控规则菜单对齐迁移。
-- 执行前请确认已备份当前库；脚本会下线已废弃的商户交易国家限定菜单、权限和旧规则表。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS risk_aml_ip (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    merchant_scope VARCHAR(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
    merchant_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
    match_value_masked VARCHAR(128) NOT NULL COMMENT 'IP地址或区间展示值',
    match_value_hash VARCHAR(128) NOT NULL COMMENT 'IP区间归一化哈希，用于重复校验',
    match_value_cipher VARCHAR(1024) NULL COMMENT 'IP地址默认不保存敏感密文',
    match_value_start VARCHAR(128) NOT NULL COMMENT '起始IP',
    match_value_end VARCHAR(128) NOT NULL COMMENT '截止IP',
    match_value_start_number DECIMAL(39,0) NOT NULL COMMENT '起始IP数值，交易检索使用',
    match_value_end_number DECIMAL(39,0) NOT NULL COMMENT '截止IP数值，交易检索使用',
    ip_version VARCHAR(8) NOT NULL COMMENT 'IP版本：IPV4、IPV6',
    risk_level VARCHAR(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
    decision_action VARCHAR(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
    effective_time DATETIME(3) NULL COMMENT '生效时间',
    expire_time DATETIME(3) NULL COMMENT '失效时间',
    validity_type VARCHAR(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
    validity_days INT NULL COMMENT '有效天数，长期和限定有效期使用',
    source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IP地址区间AML名单表';

CREATE TABLE IF NOT EXISTS risk_aml_legal_person (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    merchant_scope VARCHAR(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
    merchant_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
    match_value_masked VARCHAR(255) NOT NULL COMMENT '法人名称脱敏展示值',
    match_value_hash VARCHAR(128) NOT NULL COMMENT '法人名称归一化哈希，用于交易检索和重复校验',
    match_value_cipher VARCHAR(1024) NULL COMMENT '法人名称密文，仅编辑授权时解密回显',
    risk_level VARCHAR(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
    decision_action VARCHAR(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
    effective_time DATETIME(3) NULL COMMENT '生效时间',
    expire_time DATETIME(3) NULL COMMENT '失效时间',
    validity_type VARCHAR(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
    validity_days INT NULL COMMENT '有效天数，长期和限定有效期使用',
    source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AML法人名单表';

CREATE TABLE IF NOT EXISTS risk_aml_enterprise (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    merchant_scope VARCHAR(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
    merchant_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
    match_value_masked VARCHAR(255) NOT NULL COMMENT '企业名称脱敏展示值',
    match_value_hash VARCHAR(128) NOT NULL COMMENT '企业名称归一化哈希，用于交易检索和重复校验',
    match_value_cipher VARCHAR(1024) NULL COMMENT '企业名称密文，仅编辑授权时解密回显',
    risk_level VARCHAR(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
    decision_action VARCHAR(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
    effective_time DATETIME(3) NULL COMMENT '生效时间',
    expire_time DATETIME(3) NULL COMMENT '失效时间',
    validity_type VARCHAR(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
    validity_days INT NULL COMMENT '有效天数，长期和限定有效期使用',
    source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AML企业名单表';

CREATE TABLE IF NOT EXISTS risk_aml_merchant_billing_address (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    merchant_scope VARCHAR(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
    merchant_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
    match_value_masked VARCHAR(255) NOT NULL COMMENT '商户账单地址明文展示值',
    match_value_hash VARCHAR(128) NOT NULL COMMENT '商户账单地址归一化哈希，用于交易检索和重复校验',
    match_value_cipher VARCHAR(1024) NULL COMMENT '商户账单地址默认不加密存储',
    risk_level VARCHAR(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
    decision_action VARCHAR(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
    effective_time DATETIME(3) NULL COMMENT '生效时间',
    expire_time DATETIME(3) NULL COMMENT '失效时间',
    validity_type VARCHAR(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
    validity_days INT NULL COMMENT '有效天数，长期和限定有效期使用',
    source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AML商户账单地址名单表';

DROP PROCEDURE IF EXISTS migrate_risk_aml_alignment;
DROP PROCEDURE IF EXISTS create_index_if_missing;

DELIMITER $$

CREATE PROCEDURE create_index_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_create_sql TEXT
)
BEGIN
    SELECT COUNT(1) INTO @index_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND INDEX_NAME = p_index_name;

    IF @index_exists = 0 THEN
        SET @sql = p_create_sql;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE migrate_risk_aml_alignment()
BEGIN
    SET @schema_name = DATABASE();

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'risk_aml_ip' AND COLUMN_NAME = 'ip_version'
    ) THEN
        ALTER TABLE risk_aml_ip ADD COLUMN ip_version VARCHAR(8) NULL COMMENT 'IP版本：IPV4、IPV6' AFTER match_value_end_number;
    END IF;

    UPDATE risk_aml_ip
    SET merchant_id = ''
    WHERE merchant_id IS NULL;

    UPDATE risk_aml_ip
    SET ip_version = CASE
        WHEN match_value_start LIKE '%:%' THEN 'IPV6'
        ELSE 'IPV4'
    END
    WHERE ip_version IS NULL OR ip_version = '';

    SET @drop_columns = 'card_brand,country_alpha2,country_alpha3,country_numeric';
    SET @drop_column_sql = (
        SELECT GROUP_CONCAT(CONCAT('DROP COLUMN ', COLUMN_NAME) SEPARATOR ', ')
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'risk_aml_ip'
          AND FIND_IN_SET(COLUMN_NAME, @drop_columns) > 0
    );
    SET @sql = IF(@drop_column_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE risk_aml_ip ', @drop_column_sql));
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @old_indexes = 'idx_risk_aml_card_hash,idx_risk_aml_card_merchant,idx_risk_aml_card_uniq_lookup,idx_risk_aml_card_range_num,idx_risk_aml_card_time,uk_aml_ip_scope_hash_deleted,idx_aml_ip_trade_lookup,idx_aml_ip_merchant_time,idx_aml_ip_time';
    SET @drop_index_sql = (
        SELECT GROUP_CONCAT(CONCAT('DROP INDEX ', INDEX_NAME) SEPARATOR ', ')
        FROM (
            SELECT DISTINCT INDEX_NAME
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'risk_aml_ip'
              AND FIND_IN_SET(INDEX_NAME, @old_indexes) > 0
        ) matched_indexes
    );
    SET @sql = IF(@drop_index_sql IS NULL, 'SELECT 1', CONCAT('ALTER TABLE risk_aml_ip ', @drop_index_sql));
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    ALTER TABLE risk_aml_ip
        MODIFY merchant_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
        MODIFY match_value_masked VARCHAR(128) NOT NULL COMMENT 'IP地址或区间展示值',
        MODIFY match_value_hash VARCHAR(128) NOT NULL COMMENT 'IP区间归一化哈希，用于重复校验',
        MODIFY match_value_cipher VARCHAR(1024) NULL COMMENT 'IP地址默认不保存敏感密文',
        MODIFY match_value_start VARCHAR(128) NOT NULL COMMENT '起始IP',
        MODIFY match_value_end VARCHAR(128) NOT NULL COMMENT '截止IP',
        MODIFY match_value_start_number DECIMAL(39,0) NOT NULL COMMENT '起始IP数值，交易检索使用',
        MODIFY match_value_end_number DECIMAL(39,0) NOT NULL COMMENT '截止IP数值，交易检索使用',
        MODIFY ip_version VARCHAR(8) NOT NULL COMMENT 'IP版本：IPV4、IPV6',
        MODIFY risk_level VARCHAR(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
        MODIFY decision_action VARCHAR(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS';

    CALL create_index_if_missing('risk_aml_ip', 'uk_aml_ip_scope_hash_deleted', 'CREATE UNIQUE INDEX uk_aml_ip_scope_hash_deleted ON risk_aml_ip (merchant_scope, merchant_id, match_value_hash, deleted)');
    CALL create_index_if_missing('risk_aml_ip', 'idx_aml_ip_trade_lookup', 'CREATE INDEX idx_aml_ip_trade_lookup ON risk_aml_ip (ip_version, match_value_start_number, match_value_end_number, merchant_scope, merchant_id, status, deleted, effective_time, expire_time)');
    CALL create_index_if_missing('risk_aml_ip', 'idx_aml_ip_merchant_time', 'CREATE INDEX idx_aml_ip_merchant_time ON risk_aml_ip (merchant_scope, merchant_id, update_time, id)');
    CALL create_index_if_missing('risk_aml_ip', 'idx_aml_ip_time', 'CREATE INDEX idx_aml_ip_time ON risk_aml_ip (update_time, id)');

    CALL create_index_if_missing('risk_aml_legal_person', 'uk_aml_legal_person_scope_hash_deleted', 'CREATE UNIQUE INDEX uk_aml_legal_person_scope_hash_deleted ON risk_aml_legal_person (merchant_scope, merchant_id, match_value_hash, deleted)');
    CALL create_index_if_missing('risk_aml_legal_person', 'idx_aml_legal_person_trade_lookup', 'CREATE INDEX idx_aml_legal_person_trade_lookup ON risk_aml_legal_person (match_value_hash, merchant_scope, merchant_id, status, deleted, effective_time, expire_time)');
    CALL create_index_if_missing('risk_aml_legal_person', 'idx_aml_legal_person_merchant_time', 'CREATE INDEX idx_aml_legal_person_merchant_time ON risk_aml_legal_person (merchant_scope, merchant_id, update_time, id)');
    CALL create_index_if_missing('risk_aml_legal_person', 'idx_aml_legal_person_time', 'CREATE INDEX idx_aml_legal_person_time ON risk_aml_legal_person (update_time, id)');

    CALL create_index_if_missing('risk_aml_enterprise', 'uk_aml_enterprise_scope_hash_deleted', 'CREATE UNIQUE INDEX uk_aml_enterprise_scope_hash_deleted ON risk_aml_enterprise (merchant_scope, merchant_id, match_value_hash, deleted)');
    CALL create_index_if_missing('risk_aml_enterprise', 'idx_aml_enterprise_trade_lookup', 'CREATE INDEX idx_aml_enterprise_trade_lookup ON risk_aml_enterprise (match_value_hash, merchant_scope, merchant_id, status, deleted, effective_time, expire_time)');
    CALL create_index_if_missing('risk_aml_enterprise', 'idx_aml_enterprise_merchant_time', 'CREATE INDEX idx_aml_enterprise_merchant_time ON risk_aml_enterprise (merchant_scope, merchant_id, update_time, id)');
    CALL create_index_if_missing('risk_aml_enterprise', 'idx_aml_enterprise_time', 'CREATE INDEX idx_aml_enterprise_time ON risk_aml_enterprise (update_time, id)');

    CALL create_index_if_missing('risk_aml_merchant_billing_address', 'uk_aml_merchant_billing_address_scope_hash_deleted', 'CREATE UNIQUE INDEX uk_aml_merchant_billing_address_scope_hash_deleted ON risk_aml_merchant_billing_address (merchant_scope, merchant_id, match_value_hash, deleted)');
    CALL create_index_if_missing('risk_aml_merchant_billing_address', 'idx_aml_merchant_billing_address_trade_lookup', 'CREATE INDEX idx_aml_merchant_billing_address_trade_lookup ON risk_aml_merchant_billing_address (match_value_hash, merchant_scope, merchant_id, status, deleted, effective_time, expire_time)');
    CALL create_index_if_missing('risk_aml_merchant_billing_address', 'idx_aml_merchant_billing_address_merchant_time', 'CREATE INDEX idx_aml_merchant_billing_address_merchant_time ON risk_aml_merchant_billing_address (merchant_scope, merchant_id, update_time, id)');
    CALL create_index_if_missing('risk_aml_merchant_billing_address', 'idx_aml_merchant_billing_address_time', 'CREATE INDEX idx_aml_merchant_billing_address_time ON risk_aml_merchant_billing_address (update_time, id)');
END$$

DELIMITER ;

CALL migrate_risk_aml_alignment();

DROP PROCEDURE IF EXISTS migrate_risk_aml_alignment;
DROP PROCEDURE IF EXISTS create_index_if_missing;

-- AML 强制拦截全局化整改：
-- AML 名单属于全局合规强制拦截，不按商户号生效。当前通用名单 Mapper 仍依赖 merchant_scope、merchant_id 技术列，
-- 因此本段只做数据收敛和全局查询索引优化，不直接删除列；物理删列需拆分 AML 专用 Mapper 后单独评审执行。
DROP PROCEDURE IF EXISTS migrate_risk_aml_global_scope;
DROP PROCEDURE IF EXISTS create_index_if_missing;

-- 商户交易国家限定规则已废弃；白名单交易国家使用 risk_white_trade_country，不受本清理影响。
DROP TABLE IF EXISTS risk_rule_trade_country;

-- 发卡行国家限定规则已废弃；发卡行国家拦截使用 risk_black_issuer_country 名单能力。
DROP TABLE IF EXISTS risk_rule_issuer_country;

-- 卡BIN交易规则已废弃；卡 BIN 拦截使用 AML、黑名单、白名单和基础卡 BIN 能力。
DROP TABLE IF EXISTS risk_rule_card_bin;

DELIMITER $$

CREATE PROCEDURE create_index_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_create_sql TEXT
)
BEGIN
    SELECT COUNT(1) INTO @index_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND INDEX_NAME = p_index_name;

    IF @index_exists = 0 THEN
        SET @sql = p_create_sql;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE migrate_risk_aml_global_scope()
BEGIN
    UPDATE risk_aml_card SET merchant_scope = 'GLOBAL', merchant_id = '' WHERE merchant_scope <> 'GLOBAL' OR merchant_id <> '';
    UPDATE risk_aml_card_bin SET merchant_scope = 'GLOBAL', merchant_id = '' WHERE merchant_scope <> 'GLOBAL' OR merchant_id <> '';
    UPDATE risk_aml_ip SET merchant_scope = 'GLOBAL', merchant_id = '' WHERE merchant_scope <> 'GLOBAL' OR merchant_id <> '';
    UPDATE risk_aml_country SET merchant_scope = 'GLOBAL', merchant_id = '' WHERE merchant_scope <> 'GLOBAL' OR merchant_id <> '';
    UPDATE risk_aml_email SET merchant_scope = 'GLOBAL', merchant_id = '' WHERE merchant_scope <> 'GLOBAL' OR merchant_id <> '';
    UPDATE risk_aml_phone SET merchant_scope = 'GLOBAL', merchant_id = '' WHERE merchant_scope <> 'GLOBAL' OR merchant_id <> '';
    UPDATE risk_aml_cardholder_name SET merchant_scope = 'GLOBAL', merchant_id = '' WHERE merchant_scope <> 'GLOBAL' OR merchant_id <> '';
    UPDATE risk_aml_legal_person SET merchant_scope = 'GLOBAL', merchant_id = '' WHERE merchant_scope <> 'GLOBAL' OR merchant_id <> '';
    UPDATE risk_aml_enterprise SET merchant_scope = 'GLOBAL', merchant_id = '' WHERE merchant_scope <> 'GLOBAL' OR merchant_id <> '';
    UPDATE risk_aml_merchant_billing_address SET merchant_scope = 'GLOBAL', merchant_id = '' WHERE merchant_scope <> 'GLOBAL' OR merchant_id <> '';
    UPDATE risk_aml_source_url SET merchant_scope = 'GLOBAL', merchant_id = '' WHERE merchant_scope <> 'GLOBAL' OR merchant_id <> '';

    CALL create_index_if_missing('risk_aml_card', 'idx_aml_card_global_lookup', 'CREATE INDEX idx_aml_card_global_lookup ON risk_aml_card (merchant_scope, match_value_hash, status, deleted, effective_time, expire_time)');
    CALL create_index_if_missing('risk_aml_card_bin', 'idx_aml_card_bin_global_lookup', 'CREATE INDEX idx_aml_card_bin_global_lookup ON risk_aml_card_bin (merchant_scope, match_value_start_number, match_value_end_number, status, deleted, effective_time, expire_time)');
    CALL create_index_if_missing('risk_aml_ip', 'idx_aml_ip_global_lookup', 'CREATE INDEX idx_aml_ip_global_lookup ON risk_aml_ip (merchant_scope, ip_version, match_value_start_number, match_value_end_number, status, deleted, effective_time, expire_time)');
    CALL create_index_if_missing('risk_aml_country', 'idx_aml_country_global_lookup', 'CREATE INDEX idx_aml_country_global_lookup ON risk_aml_country (merchant_scope, country_alpha3, status, deleted, effective_time, expire_time)');
    CALL create_index_if_missing('risk_aml_email', 'idx_aml_email_global_lookup', 'CREATE INDEX idx_aml_email_global_lookup ON risk_aml_email (merchant_scope, match_value_hash, status, deleted, effective_time, expire_time)');
    CALL create_index_if_missing('risk_aml_phone', 'idx_aml_phone_global_lookup', 'CREATE INDEX idx_aml_phone_global_lookup ON risk_aml_phone (merchant_scope, match_value_hash, status, deleted, effective_time, expire_time)');
    CALL create_index_if_missing('risk_aml_cardholder_name', 'idx_aml_cardholder_name_global_lookup', 'CREATE INDEX idx_aml_cardholder_name_global_lookup ON risk_aml_cardholder_name (merchant_scope, match_value_hash, status, deleted, effective_time, expire_time)');
    CALL create_index_if_missing('risk_aml_legal_person', 'idx_aml_legal_person_global_lookup', 'CREATE INDEX idx_aml_legal_person_global_lookup ON risk_aml_legal_person (merchant_scope, match_value_hash, status, deleted, effective_time, expire_time)');
    CALL create_index_if_missing('risk_aml_enterprise', 'idx_aml_enterprise_global_lookup', 'CREATE INDEX idx_aml_enterprise_global_lookup ON risk_aml_enterprise (merchant_scope, match_value_hash, status, deleted, effective_time, expire_time)');
    CALL create_index_if_missing('risk_aml_merchant_billing_address', 'idx_aml_merchant_billing_address_global_lookup', 'CREATE INDEX idx_aml_merchant_billing_address_global_lookup ON risk_aml_merchant_billing_address (merchant_scope, match_value_hash, status, deleted, effective_time, expire_time)');
    CALL create_index_if_missing('risk_aml_source_url', 'idx_aml_source_url_global_lookup', 'CREATE INDEX idx_aml_source_url_global_lookup ON risk_aml_source_url (merchant_scope, match_value_hash, status, deleted, effective_time, expire_time)');
END$$

DELIMITER ;

CALL migrate_risk_aml_global_scope();

DROP PROCEDURE IF EXISTS migrate_risk_aml_global_scope;
DROP PROCEDURE IF EXISTS create_index_if_missing;
