CREATE TABLE IF NOT EXISTS base_merchant_info (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    merchant_id VARCHAR(32) NOT NULL COMMENT '支付框架颁发的商户号',
    merchant_name VARCHAR(128) NOT NULL COMMENT '商户主体名称',
    billing_descriptor VARCHAR(64) NULL COMMENT '账单描述，交易账单或渠道侧展示的商户识别名称',
    merchant_short_name VARCHAR(64) NULL COMMENT '商户简称',
    merchant_status TINYINT NOT NULL DEFAULT 1 COMMENT '商户状态：1正常，2冻结，3关闭',
    default_locale VARCHAR(20) NOT NULL DEFAULT 'zh-CN' COMMENT '商户系统和邮件默认语言',
    merchant_category_code VARCHAR(4) NOT NULL COMMENT '商户类别码MCC',
    country_code CHAR(3) NOT NULL COMMENT '商户所在国家三字码',
    region_code VARCHAR(16) NULL COMMENT '商户所在州、省或区域代码',
    city VARCHAR(64) NULL COMMENT '商户所在城市',
    address_line VARCHAR(256) NULL COMMENT '商户开户地址或经营地址',
    postal_code VARCHAR(32) NULL COMMENT '商户经营地址邮编',
    contact_name VARCHAR(64) NULL COMMENT '商户联系人姓名',
    contact_email VARCHAR(128) NULL COMMENT '商户联系人邮箱',
    contact_phone VARCHAR(32) NULL COMMENT '商户联系人电话',
    settlement_currency CHAR(3) NOT NULL COMMENT '默认结算币种',
    timezone VARCHAR(64) NOT NULL COMMENT '商户业务时区',
    risk_level TINYINT NOT NULL DEFAULT 2 COMMENT '商户风险等级：1低，2中，3高',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识：0正常，1删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_base_merchant_info_mid (merchant_id),
    KEY idx_base_merchant_status (merchant_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础商户信息表';

SET @add_merchant_billing_descriptor_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE base_merchant_info ADD COLUMN billing_descriptor VARCHAR(64) NULL COMMENT ''账单描述，交易账单或渠道侧展示的商户识别名称'' AFTER merchant_name',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'base_merchant_info'
      AND column_name = 'billing_descriptor'
);
PREPARE add_merchant_billing_descriptor_stmt FROM @add_merchant_billing_descriptor_sql;
EXECUTE add_merchant_billing_descriptor_stmt;
DEALLOCATE PREPARE add_merchant_billing_descriptor_stmt;

SET @add_merchant_default_locale_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE base_merchant_info ADD COLUMN default_locale VARCHAR(20) NOT NULL DEFAULT ''zh-CN'' COMMENT ''商户系统和邮件默认语言'' AFTER merchant_status',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'base_merchant_info'
      AND column_name = 'default_locale'
);
PREPARE add_merchant_default_locale_stmt FROM @add_merchant_default_locale_sql;
EXECUTE add_merchant_default_locale_stmt;
DEALLOCATE PREPARE add_merchant_default_locale_stmt;

SET @add_merchant_postal_code_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE base_merchant_info ADD COLUMN postal_code VARCHAR(32) NULL COMMENT ''商户经营地址邮编'' AFTER address_line',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'base_merchant_info'
      AND column_name = 'postal_code'
);
PREPARE add_merchant_postal_code_stmt FROM @add_merchant_postal_code_sql;
EXECUTE add_merchant_postal_code_stmt;
DEALLOCATE PREPARE add_merchant_postal_code_stmt;

SET @add_merchant_contact_name_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE base_merchant_info ADD COLUMN contact_name VARCHAR(64) NULL COMMENT ''商户联系人姓名'' AFTER postal_code',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'base_merchant_info'
      AND column_name = 'contact_name'
);
PREPARE add_merchant_contact_name_stmt FROM @add_merchant_contact_name_sql;
EXECUTE add_merchant_contact_name_stmt;
DEALLOCATE PREPARE add_merchant_contact_name_stmt;

SET @convert_merchant_status_sql = (
    SELECT IF(
        COUNT(*) > 0,
        'UPDATE base_merchant_info SET merchant_status = CASE UPPER(merchant_status) WHEN ''ACTIVE'' THEN ''1'' WHEN ''FROZEN'' THEN ''2'' WHEN ''CLOSED'' THEN ''3'' ELSE ''1'' END WHERE merchant_status IS NOT NULL',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'base_merchant_info'
      AND column_name = 'merchant_status'
      AND data_type IN ('varchar', 'char')
);
PREPARE convert_merchant_status_stmt FROM @convert_merchant_status_sql;
EXECUTE convert_merchant_status_stmt;
DEALLOCATE PREPARE convert_merchant_status_stmt;

ALTER TABLE base_merchant_info
    MODIFY COLUMN merchant_status TINYINT NOT NULL DEFAULT 1 COMMENT '商户状态：1正常，2冻结，3关闭';

SET @convert_merchant_risk_level_sql = (
    SELECT IF(
        COUNT(*) > 0,
        'UPDATE base_merchant_info SET risk_level = CASE UPPER(risk_level) WHEN ''LOW'' THEN ''1'' WHEN ''NORMAL'' THEN ''2'' WHEN ''MEDIUM'' THEN ''2'' WHEN ''HIGH'' THEN ''3'' ELSE ''2'' END WHERE risk_level IS NOT NULL',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'base_merchant_info'
      AND column_name = 'risk_level'
      AND data_type IN ('varchar', 'char')
);
PREPARE convert_merchant_risk_level_stmt FROM @convert_merchant_risk_level_sql;
EXECUTE convert_merchant_risk_level_stmt;
DEALLOCATE PREPARE convert_merchant_risk_level_stmt;

ALTER TABLE base_merchant_info
    MODIFY COLUMN risk_level TINYINT NOT NULL DEFAULT 2 COMMENT '商户风险等级：1低，2中，3高';

CREATE TABLE IF NOT EXISTS merchant_ip_whitelist (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id VARCHAR(32) NOT NULL COMMENT '商户号，对应 base_merchant_info.merchant_id',
    ip_type VARCHAR(8) NOT NULL COMMENT 'IP类型：IPv4/IPv6',
    ip_value VARCHAR(45) NOT NULL COMMENT '规范化后的精确 IP 地址',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '交易状态：1允许交易，0禁止交易',
    approval_status TINYINT NOT NULL DEFAULT 1 COMMENT '审核状态：0待审核，1审核通过，2审核拒绝',
    approval_remark VARCHAR(500) NULL COMMENT '审批说明，审核拒绝时必填',
    submit_source VARCHAR(16) NOT NULL DEFAULT 'ADMIN' COMMENT '提交来源：ADMIN、MERCHANT',
    review_by VARCHAR(64) NULL COMMENT '审核人账号或姓名',
    review_time DATETIME(3) NULL COMMENT '审核时间',
    remark VARCHAR(512) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，删除时写入主键ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_ip_whitelist_merchant_ip_deleted (merchant_id, ip_value, deleted),
    KEY idx_merchant_ip_whitelist_lookup (
        merchant_id, ip_value, approval_status, status, deleted
    ),
    KEY idx_merchant_ip_whitelist_merchant_time (merchant_id, gmt_modified, id),
    KEY idx_merchant_ip_whitelist_approval (
        approval_status, submit_source, gmt_create, id
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户 OpenAPI IP 白名单';

CREATE TABLE IF NOT EXISTS base_merchant_jwt_key (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    merchant_id VARCHAR(32) NOT NULL COMMENT '支付框架颁发的商户号',
    key_version VARCHAR(32) NOT NULL COMMENT '商户JWT密钥版本号',
    merchant_key VARCHAR(256) NOT NULL COMMENT '商户JWT HS256签名密钥，测试环境明文，生产必须密文或KMS',
    algorithm VARCHAR(32) NOT NULL COMMENT 'JWT签名算法',
    expires_seconds BIGINT NOT NULL COMMENT 'JWT最大有效期，单位秒',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用标识：1启用，0停用',
    effective_time DATETIME(3) NOT NULL COMMENT '密钥生效时间',
    expire_time DATETIME(3) NULL COMMENT '密钥失效时间',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识：0正常，1删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_base_merchant_jwt_key_mid_ver (merchant_id, key_version),
    KEY idx_base_merchant_jwt_key_lookup (merchant_id, algorithm, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础商户JWT签名密钥表';

CREATE TABLE IF NOT EXISTS base_platform_payload_key (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    merchant_id VARCHAR(32) NOT NULL COMMENT '支付框架颁发的商户号，每个商户独立一套平台请求体RSA密钥',
    public_key_x509_base64 TEXT NOT NULL COMMENT '平台X.509 DER Base64公钥，下发给商户',
    private_key_pkcs8_base64 TEXT NOT NULL COMMENT '平台PKCS#8 DER Base64私钥，测试环境明文，生产必须KMS或加密存储',
    algorithm VARCHAR(64) NOT NULL COMMENT '请求体加密算法',
    key_size INT NOT NULL COMMENT 'RSA密钥位数',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用标识：1启用，0停用',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识：0正常，1删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_base_platform_payload_key_mid (merchant_id),
    KEY idx_base_platform_payload_key_status (enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础平台请求体RSA密钥表';

SET @add_platform_payload_merchant_id_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE base_platform_payload_key ADD COLUMN merchant_id VARCHAR(32) NULL COMMENT ''支付框架颁发的商户号，每个商户独立一套平台请求体RSA密钥'' AFTER id',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'base_platform_payload_key'
      AND column_name = 'merchant_id'
);
PREPARE add_platform_payload_merchant_id_stmt FROM @add_platform_payload_merchant_id_sql;
EXECUTE add_platform_payload_merchant_id_stmt;
DEALLOCATE PREPARE add_platform_payload_merchant_id_stmt;

CREATE TABLE IF NOT EXISTS base_merchant_response_key (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    merchant_id VARCHAR(32) NOT NULL COMMENT '支付框架颁发的商户号',
    public_key_x509_base64 TEXT NOT NULL COMMENT '商户X.509 DER Base64响应公钥，平台只保存公钥',
    algorithm VARCHAR(64) NOT NULL COMMENT '响应data加密算法',
    key_size INT NOT NULL COMMENT 'RSA密钥位数',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用标识：1启用，0停用',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识：0正常，1删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_base_merchant_response_key_mid (merchant_id),
    KEY idx_base_merchant_response_key_lookup (merchant_id, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础商户响应加密公钥表';

SET @drop_merchant_info_platform_payload_key_id_sql = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE base_merchant_info DROP COLUMN platform_payload_key_id',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'base_merchant_info'
      AND column_name = 'platform_payload_key_id'
);
PREPARE drop_merchant_info_platform_payload_key_id_stmt FROM @drop_merchant_info_platform_payload_key_id_sql;
EXECUTE drop_merchant_info_platform_payload_key_id_stmt;
DEALLOCATE PREPARE drop_merchant_info_platform_payload_key_id_stmt;

SET @drop_merchant_info_response_key_id_sql = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE base_merchant_info DROP COLUMN response_key_id',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'base_merchant_info'
      AND column_name = 'response_key_id'
);
PREPARE drop_merchant_info_response_key_id_stmt FROM @drop_merchant_info_response_key_id_sql;
EXECUTE drop_merchant_info_response_key_id_stmt;
DEALLOCATE PREPARE drop_merchant_info_response_key_id_stmt;

SET @drop_platform_payload_key_id_sql = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE base_platform_payload_key DROP COLUMN platform_key_id',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'base_platform_payload_key'
      AND column_name = 'platform_key_id'
);
PREPARE drop_platform_payload_key_id_stmt FROM @drop_platform_payload_key_id_sql;
EXECUTE drop_platform_payload_key_id_stmt;
DEALLOCATE PREPARE drop_platform_payload_key_id_stmt;

SET @drop_merchant_response_key_id_sql = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE base_merchant_response_key DROP COLUMN response_key_id',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'base_merchant_response_key'
      AND column_name = 'response_key_id'
);
PREPARE drop_merchant_response_key_id_stmt FROM @drop_merchant_response_key_id_sql;
EXECUTE drop_merchant_response_key_id_stmt;
DEALLOCATE PREPARE drop_merchant_response_key_id_stmt;
