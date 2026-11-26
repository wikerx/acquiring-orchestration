-- 待确认的建表评审稿，由人工审核后进入正式迁移及基线。当前无启动脚本引用。
-- 仅新增配置表；在目标库执行前先核实九个表名均未占用、备份现有库元数据及确认 MySQL 8 版本。
SET NAMES utf8mb4;

CREATE TABLE dispute_alert_platform (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '预警平台主键',
    code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定的预警平台编码',
    name VARCHAR(80) NOT NULL COMMENT '运营后台展示的平台名称',
    logo_object_key VARCHAR(500) NULL COMMENT '通过安全扫描的预警平台私有 Logo 对象引用',
    type VARCHAR(24) NOT NULL COMMENT '接入方类型，区分聚合服务商、收单支付机构和官方直连',
    enabled TINYINT NOT NULL COMMENT '平台配置是否启用，1启用，0停用',
    evidence VARCHAR(250) NULL COMMENT '平台产品能力的资料来源或登记编号',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '平台及产品配置修改使用的并发版本',
    create_by BIGINT NOT NULL COMMENT '创建配置的管理端账号ID',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '平台配置登记时间',
    update_by BIGINT NOT NULL COMMENT '最后修改配置的管理端账号ID',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '平台配置最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dispute_alert_platform_code (code),
    KEY idx_dispute_alert_platform_type (type, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拒付预警平台及接入方配置';

CREATE TABLE dispute_alert_platform_product (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台产品配置主键',
    platform_id BIGINT NOT NULL COMMENT '提供该产品的预警平台主键',
    product VARCHAR(24) NOT NULL COMMENT '预警产品编码，区分 Ethoca、RDR、CDRN 和 Amex Inquiry',
    enabled TINYINT NOT NULL COMMENT '平台产品配置是否启用，1启用，0停用',
    coverage VARCHAR(16) NOT NULL COMMENT '卡品牌覆盖的人工确认状态',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dispute_alert_platform_product (platform_id, product),
    UNIQUE KEY uk_dispute_alert_platform_product_owner (id, platform_id),
    KEY idx_dispute_alert_platform_product_scope (product, enabled, id),
    CONSTRAINT fk_dispute_alert_product_platform FOREIGN KEY (platform_id) REFERENCES dispute_alert_platform (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预警平台提供的产品配置';

CREATE TABLE dispute_alert_platform_product_brand (
    platform_product_id BIGINT NOT NULL COMMENT '卡品牌所属的平台产品主键',
    card_brand VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '平台产品已登记的标准卡品牌编码',
    PRIMARY KEY (platform_product_id, card_brand),
    CONSTRAINT fk_dispute_alert_brand_product FOREIGN KEY (platform_product_id) REFERENCES dispute_alert_platform_product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预警平台产品的卡品牌覆盖';

CREATE TABLE dispute_alert_account (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '预警接入账号主键',
    platform_id BIGINT NOT NULL COMMENT '分配该账号的预警平台主键',
    environment VARCHAR(16) NOT NULL COMMENT '账号对应的测试或生产环境',
    merchant_number VARCHAR(80) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '预警平台分配的上游账号标识或商户号',
    status VARCHAR(16) NOT NULL COMMENT '账号配置状态，区分草稿、启用和停用',
    credential_version BIGINT NULL COMMENT '账号当前使用的凭据版本号，未登记时为空',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '账号及产品配置修改使用的并发版本',
    create_by BIGINT NOT NULL COMMENT '创建账号配置的管理端账号ID',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '账号配置登记时间',
    update_by BIGINT NOT NULL COMMENT '最后修改账号配置的管理端账号ID',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '账号配置最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dispute_alert_account_identity (platform_id, environment, merchant_number),
    UNIQUE KEY uk_dispute_alert_account_owner (id, platform_id),
    KEY idx_dispute_alert_account_environment (environment, status, id),
    CONSTRAINT fk_dispute_alert_account_platform FOREIGN KEY (platform_id) REFERENCES dispute_alert_platform (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拒付预警平台的接入账号';

CREATE TABLE dispute_alert_account_product (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '接入账号产品配置主键',
    platform_id BIGINT NOT NULL COMMENT '同时归属账号和平台产品的预警平台主键',
    account_id BIGINT NOT NULL COMMENT '开通该产品的预警账号主键',
    platform_product_id BIGINT NOT NULL COMMENT '该账号关联的平台产品主键',
    status VARCHAR(16) NOT NULL COMMENT '账号产品独立配置状态，区分草稿、启用和停用',
    transport VARCHAR(16) NOT NULL COMMENT '产品交付方式，区分推送、拉取和投递',
    host VARCHAR(120) NULL COMMENT '上游文件交付主机和端口登记值',
    directory VARCHAR(120) NULL COMMENT '上游文件交付目录登记值',
    schedule VARCHAR(120) NULL COMMENT '上游文件交付周期及其时区登记值',
    source_authentication VARCHAR(80) NULL COMMENT '来源认证方式描述，不保存认证凭据材料',
    outcome_url VARCHAR(250) NULL COMMENT '上游业务回执地址登记值',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dispute_alert_account_product (account_id, platform_product_id),
    KEY idx_dispute_alert_account_product_option (platform_product_id, status, account_id),
    CONSTRAINT fk_dispute_alert_account_product_account FOREIGN KEY (account_id, platform_id) REFERENCES dispute_alert_account (id, platform_id),
    CONSTRAINT fk_dispute_alert_account_product_capability FOREIGN KEY (platform_product_id, platform_id) REFERENCES dispute_alert_platform_product (id, platform_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预警账号开通产品及交付配置';

CREATE TABLE dispute_alert_credential_version (
    account_id BIGINT NOT NULL COMMENT '共享凭据所属的预警接入账号主键',
    version BIGINT NOT NULL COMMENT '该账号下单调递增的凭据版本号',
    public_secret_ref CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '密钥托管服务公钥材料的不透明引用',
    private_secret_ref CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '密钥托管服务私钥材料的不透明引用',
    public_key_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '登记公钥文本的指纹，用于核对版本',
    create_by BIGINT NOT NULL COMMENT '登记该版本的管理端账号ID',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '凭据版本登记时间',
    PRIMARY KEY (account_id, version),
    CONSTRAINT fk_dispute_alert_credential_account FOREIGN KEY (account_id) REFERENCES dispute_alert_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预警账号共用凭据的版本和托管引用';

CREATE TABLE dispute_alert_mid_binding (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '渠道收单MID的预警产品绑定主键',
    mid_config_id BIGINT NOT NULL COMMENT '关联现有 channel_mid_config 主键',
    account_product_id BIGINT NOT NULL COMMENT '绑定所使用的具体预警账号产品主键',
    external_merchant_id VARCHAR(80) COLLATE utf8mb4_0900_bin NOT NULL DEFAULT '' COMMENT '上游子商户范围，空字符串表示使用账号默认范围',
    upstream_merchant_id VARCHAR(80) NULL COMMENT '上游预警产品侧登记的商户标识',
    enrollment VARCHAR(16) NOT NULL COMMENT '上游开通状态，区分待确认、已确认和已撤销',
    enrollment_evidence VARCHAR(180) NULL COMMENT '开通确认单号或其他可追溯的登记依据',
    status VARCHAR(16) NOT NULL COMMENT '绑定的本地配置状态，区分草稿、启用和停用',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '绑定及所属标识修改使用的并发版本',
    create_by BIGINT NOT NULL COMMENT '登记绑定的管理端账号ID',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定登记时间',
    update_by BIGINT NOT NULL COMMENT '最后修改绑定的管理端账号ID',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '绑定最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dispute_alert_mid_binding (mid_config_id, account_product_id, external_merchant_id),
    KEY idx_dispute_alert_mid_binding_source (account_product_id, status, mid_config_id),
    KEY idx_dispute_alert_mid_binding_status (status, id),
    CONSTRAINT fk_dispute_alert_binding_source FOREIGN KEY (account_product_id) REFERENCES dispute_alert_account_product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道收单MID与预警账号产品的开通绑定';

CREATE TABLE dispute_alert_mid_identifier (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '预警绑定收单行标识主键',
    binding_id BIGINT NOT NULL COMMENT '收单行标识所属的预警绑定主键',
    type VARCHAR(16) NOT NULL COMMENT '收单行标识类型，区分真实收单商户号和收单BIN加CAID',
    acquirer VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '上游开通资料中的收单行标识',
    acquirer_mid VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL DEFAULT '' COMMENT '真实收单行MID，其他标识类型为空字符串',
    acquirer_bin VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '' COMMENT '收单机构BIN，使用字符串保留前导零',
    caid VARCHAR(80) COLLATE utf8mb4_0900_bin NOT NULL DEFAULT '' COMMENT '上游分配的卡受理方标识，使用字符串保留前导零',
    enabled TINYINT NOT NULL COMMENT '该条收单行标识是否启用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dispute_alert_mid_identifier (binding_id, type, acquirer, acquirer_mid, acquirer_bin, caid),
    CONSTRAINT fk_dispute_alert_identifier_binding FOREIGN KEY (binding_id) REFERENCES dispute_alert_mid_binding (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预警绑定的可多条收单行标识';

CREATE TABLE dispute_alert_mid_descriptor (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '预警绑定账单描述主键',
    binding_id BIGINT NOT NULL COMMENT '账单描述所属的预警绑定主键',
    descriptor VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '运营登记的完整账单描述原文',
    enabled TINYINT NOT NULL COMMENT '该条账单描述是否启用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dispute_alert_mid_descriptor (binding_id, descriptor),
    CONSTRAINT fk_dispute_alert_descriptor_binding FOREIGN KEY (binding_id) REFERENCES dispute_alert_mid_binding (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预警绑定的可多条账单描述';

-- 后检评审：比对九张表的字段、索引、外键及全库中文注释；检查现有渠道MID表定义不变。
-- 回退评审：若应用已开启配置入口，先回退应用并保留表和托管凭据，后续凭审计决定数据留存。
