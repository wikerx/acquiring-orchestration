CREATE TABLE IF NOT EXISTS base_merchant_info (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    merchant_id VARCHAR(32) NOT NULL COMMENT '支付框架颁发的商户号',
    merchant_name VARCHAR(128) NOT NULL COMMENT '商户主体名称',
    merchant_short_name VARCHAR(64) NULL COMMENT '商户简称',
    merchant_status VARCHAR(16) NOT NULL COMMENT '商户状态：ACTIVE/FROZEN/CLOSED',
    merchant_category_code VARCHAR(4) NOT NULL COMMENT '商户类别码MCC',
    country_code CHAR(3) NOT NULL COMMENT '商户所在国家三字码',
    region_code VARCHAR(16) NULL COMMENT '商户所在州、省或区域代码',
    city VARCHAR(64) NULL COMMENT '商户所在城市',
    address_line VARCHAR(256) NULL COMMENT '商户开户地址或经营地址',
    contact_email VARCHAR(128) NULL COMMENT '商户联系人邮箱',
    contact_phone VARCHAR(32) NULL COMMENT '商户联系人电话',
    settlement_currency CHAR(3) NOT NULL COMMENT '默认结算币种',
    timezone VARCHAR(64) NOT NULL COMMENT '商户业务时区',
    risk_level VARCHAR(16) NOT NULL COMMENT '商户风险等级',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识：0正常，1删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_base_merchant_info_mid (merchant_id),
    KEY idx_base_merchant_status (merchant_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础商户信息表';

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
