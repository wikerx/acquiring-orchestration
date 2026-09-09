/*
 Navicat Premium Dump SQL

 Source Server         : 本机Docker：MySQL
 Source Server Type    : MySQL
 Source Server Version : 80409 (8.4.9)
 Source Host           : localhost:3306
 Source Schema         : payment_acquiring

 Target Server Type    : MySQL
 Target Server Version : 80409 (8.4.9)
 File Encoding         : 65001

 Date: 09/08/2026 18:20:31
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for base_card_bin_import_batch
-- ----------------------------
DROP TABLE IF EXISTS `base_card_bin_import_batch`;
CREATE TABLE `base_card_bin_import_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_no` varchar(64) NOT NULL COMMENT '批次号',
  `import_type` varchar(32) NOT NULL DEFAULT 'DB_INIT' COMMENT '导入类型：DB_INIT、EXCEL、CSV、API',
  `data_source` varchar(64) NOT NULL DEFAULT 'LEGACY_DB' COMMENT '数据来源',
  `file_name` varchar(255) DEFAULT NULL COMMENT '文件名称，数据库初始化导入可为空',
  `total_count` int NOT NULL DEFAULT '0' COMMENT '总条数',
  `success_count` int NOT NULL DEFAULT '0' COMMENT '成功条数',
  `failed_count` int NOT NULL DEFAULT '0' COMMENT '失败条数',
  `conflict_count` int NOT NULL DEFAULT '0' COMMENT '冲突条数',
  `duplicate_count` int NOT NULL DEFAULT '0' COMMENT '重复条数',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0处理中，1成功，2部分成功，3失败',
  `error_message` varchar(1024) DEFAULT NULL COMMENT '错误信息',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_bin_batch_no` (`batch_no`),
  KEY `idx_card_bin_batch_source` (`data_source`),
  KEY `idx_card_bin_batch_status` (`status`),
  KEY `idx_card_bin_batch_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础卡BIN导入批次表';

-- ----------------------------
-- Table structure for base_card_bin_range
-- ----------------------------
DROP TABLE IF EXISTS `base_card_bin_range`;
CREATE TABLE `base_card_bin_range` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `legacy_pk_id` bigint DEFAULT NULL COMMENT '旧表card_bin_type_info.pk_id，用于初始化数据追溯',
  `card_bin_start` bigint unsigned NOT NULL COMMENT '卡BIN开始值，统一按11位数字存储，不足位右侧补0',
  `card_bin_end` bigint unsigned NOT NULL COMMENT '卡BIN结束值，统一按11位数字存储，不足位右侧补9',
  `bin_length` tinyint unsigned NOT NULL DEFAULT '11' COMMENT 'BIN精度长度：6、7、8、9、10、11',
  `card_brand` varchar(64) NOT NULL DEFAULT 'UNKNOWN' COMMENT '卡品牌：复用系统已有卡品牌字典',
  `card_sub_brand` varchar(128) DEFAULT NULL COMMENT '卡子品牌/产品名称',
  `card_type` varchar(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT '卡类型：CREDIT、DEBIT、PREPAID、CHARGE、COMMERCIAL、UNKNOWN',
  `card_level` varchar(64) DEFAULT NULL COMMENT '卡等级',
  `issuer_country_name` varchar(128) DEFAULT NULL COMMENT '发卡行国家全称',
  `issuer_country_alpha2` char(2) DEFAULT NULL COMMENT '发卡行国家ISO Alpha-2',
  `issuer_country_alpha3` char(3) DEFAULT NULL COMMENT '发卡行国家ISO Alpha-3',
  `issuer_country_numeric` char(3) DEFAULT NULL COMMENT '发卡行国家ISO Numeric',
  `issuer_bank` varchar(256) DEFAULT NULL COMMENT '隶属发卡行',
  `issuer_web_url` varchar(512) DEFAULT NULL COMMENT '发卡行网页访问URL',
  `issuer_telephone` varchar(64) DEFAULT NULL COMMENT '发卡行联系电话',
  `data_source` varchar(64) NOT NULL DEFAULT 'MANUAL' COMMENT '数据来源',
  `source_batch_no` varchar(64) DEFAULT NULL COMMENT '来源批次号',
  `source_priority` tinyint NOT NULL DEFAULT '50' COMMENT '来源优先级，数值越大优先级越高',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0禁用，1启用，2待确认，3已过期',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_bin_legacy_deleted` (`data_source`,`legacy_pk_id`,`deleted`),
  KEY `idx_card_bin_range_status` (`deleted`,`status`,`card_bin_start`,`card_bin_end`),
  KEY `idx_card_bin_start` (`card_bin_start`),
  KEY `idx_card_bin_end` (`card_bin_end`),
  KEY `idx_card_bin_brand_country` (`card_brand`,`issuer_country_alpha2`,`deleted`),
  KEY `idx_card_bin_type` (`card_type`,`deleted`),
  KEY `idx_card_bin_country` (`issuer_country_alpha2`,`deleted`),
  KEY `idx_card_bin_bank` (`issuer_bank`),
  KEY `idx_card_bin_source_batch` (`source_batch_no`,`deleted`),
  KEY `idx_card_bin_update_time` (`update_time`)
) ENGINE=InnoDB AUTO_INCREMENT=1016461 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础卡BIN区间表';

-- ----------------------------
-- Table structure for base_iso_country
-- ----------------------------
DROP TABLE IF EXISTS `base_iso_country`;
CREATE TABLE `base_iso_country` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `continent_code` char(2) NOT NULL DEFAULT '' COMMENT '七大洲代码：AS/EU/AF/NA/SA/OC/AN',
  `continent_name` varchar(32) NOT NULL DEFAULT '' COMMENT '七大洲中文名称',
  `english_name` varchar(160) NOT NULL COMMENT '国家/地区英文全称',
  `short_english_name` varchar(128) NOT NULL COMMENT '国家/地区英文简称',
  `chinese_name` varchar(128) NOT NULL COMMENT '国家/地区中文名称',
  `alpha2_code` char(2) NOT NULL COMMENT 'ISO 3166-1 alpha-2 两位字母代码',
  `alpha3_code` char(3) NOT NULL COMMENT 'ISO 3166-1 alpha-3 三位字母代码',
  `numeric_code` char(3) NOT NULL COMMENT 'ISO 3166-1 numeric 三位数字代码',
  `flag_emoji` varchar(16) NOT NULL DEFAULT '' COMMENT '国家/地区国旗图标',
  `primary_language_code` varchar(16) NOT NULL DEFAULT '' COMMENT '主要语言代码，非 ISO 3166 强制字段',
  `primary_language_english` varchar(64) NOT NULL DEFAULT '' COMMENT '主要语言英文名称',
  `primary_language_chinese` varchar(64) NOT NULL DEFAULT '' COMMENT '主要语言中文名称',
  `currency_alpha3_code` char(3) NOT NULL DEFAULT '' COMMENT '默认币种 ISO 4217 三位字母代码',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_base_iso_country_alpha2` (`alpha2_code`),
  UNIQUE KEY `uk_base_iso_country_alpha3` (`alpha3_code`),
  UNIQUE KEY `uk_base_iso_country_numeric` (`numeric_code`),
  KEY `idx_base_iso_country_continent` (`continent_code`),
  KEY `idx_base_iso_country_currency` (`currency_alpha3_code`)
) ENGINE=InnoDB AUTO_INCREMENT=250 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ISO 3166 国家地区基础字典';

-- ----------------------------
-- Table structure for base_iso_currency
-- ----------------------------
DROP TABLE IF EXISTS `base_iso_currency`;
CREATE TABLE `base_iso_currency` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `alpha3_code` char(3) NOT NULL COMMENT 'ISO 4217 三位字母币种代码',
  `numeric_code` char(3) NOT NULL COMMENT 'ISO 4217 三位数字币种代码',
  `english_name` varchar(128) NOT NULL COMMENT '币种英文名称',
  `chinese_name` varchar(128) NOT NULL COMMENT '币种中文名称',
  `currency_symbol` varchar(16) NOT NULL DEFAULT '' COMMENT '币种符号/图标',
  `fraction_digits` tinyint NOT NULL COMMENT '默认辅币位，-1 表示无定义',
  `minor_unit_multiplier` bigint NOT NULL DEFAULT '0' COMMENT '最小单位换算倍数',
  `minimum_amount` decimal(18,6) NOT NULL DEFAULT '0.000000' COMMENT '最小金额单位',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_base_iso_currency_alpha3` (`alpha3_code`),
  KEY `idx_base_iso_currency_numeric` (`numeric_code`)
) ENGINE=InnoDB AUTO_INCREMENT=234 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ISO 4217 币种基础字典';

-- ----------------------------
-- Table structure for base_mcc_code
-- ----------------------------
DROP TABLE IF EXISTS `base_mcc_code`;
CREATE TABLE `base_mcc_code` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `mcc_code` char(4) NOT NULL COMMENT 'MCC编码，固定4位行业编码',
  `name_cn` varchar(160) DEFAULT NULL COMMENT 'MCC中文名称',
  `name_en` varchar(200) DEFAULT NULL COMMENT 'MCC英文名称',
  `mcc_name_cn` varchar(256) NOT NULL COMMENT 'MCC中文标题',
  `mcc_name_en` varchar(256) NOT NULL COMMENT 'MCC英文标题',
  `level1_id` bigint NOT NULL COMMENT '一级分类ID，关联base_mcc_level1.id',
  `level2_id` bigint NOT NULL COMMENT '二级分类ID，关联base_mcc_level2.id',
  `mcc_type` varchar(32) NOT NULL COMMENT 'MCC类型',
  `risk_level` varchar(32) NOT NULL COMMENT '默认风险等级：LOW低风险，MEDIUM中风险，HIGH高风险，PROHIBITED禁入/系统用途，UNSPECIFIED未定义',
  `delivery_applicability` varchar(32) DEFAULT NULL COMMENT '妥投适用性：PHYSICAL实物，NON_PHYSICAL非实物，BOTH均可',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1启用，0禁用',
  `source` varchar(64) DEFAULT NULL COMMENT '数据来源',
  `version_no` varchar(32) DEFAULT NULL COMMENT '版本号',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `sort_no` int NOT NULL DEFAULT '100' COMMENT '排序号',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_base_mcc_code` (`mcc_code`),
  KEY `idx_base_mcc_name_cn` (`mcc_name_cn`),
  KEY `idx_base_mcc_name_en` (`mcc_name_en`),
  KEY `idx_base_mcc_type` (`mcc_type`),
  KEY `idx_base_mcc_risk` (`risk_level`),
  KEY `idx_base_mcc_delivery` (`delivery_applicability`),
  KEY `idx_base_mcc_status` (`status`),
  KEY `idx_base_mcc_effective` (`effective_time`,`expire_time`),
  KEY `idx_base_mcc_code_l1_l2` (`level1_id`,`level2_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1003000904 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCC基础明细表';

-- ----------------------------
-- Table structure for base_mcc_level1
-- ----------------------------
DROP TABLE IF EXISTS `base_mcc_level1`;
CREATE TABLE `base_mcc_level1` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `level1_code` varchar(32) NOT NULL COMMENT '一级分类编码',
  `name_cn` varchar(128) DEFAULT NULL COMMENT '中文名称',
  `name_en` varchar(128) DEFAULT NULL COMMENT '英文名称',
  `level1_name_cn` varchar(128) NOT NULL COMMENT '一级分类中文名称',
  `level1_name_en` varchar(128) NOT NULL COMMENT '一级分类英文名称',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1启用，0禁用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_base_mcc_l1_code` (`level1_code`),
  KEY `idx_base_mcc_l1_status_sort` (`status`,`sort_no`)
) ENGINE=InnoDB AUTO_INCREMENT=1000000023 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCC一级分类表';

-- ----------------------------
-- Table structure for base_mcc_level2
-- ----------------------------
DROP TABLE IF EXISTS `base_mcc_level2`;
CREATE TABLE `base_mcc_level2` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `level1_id` bigint NOT NULL COMMENT '一级分类ID，关联base_mcc_level1.id',
  `level2_code` varchar(32) NOT NULL COMMENT '二级分类编码',
  `name_cn` varchar(128) DEFAULT NULL COMMENT '中文名称',
  `name_en` varchar(128) DEFAULT NULL COMMENT '英文名称',
  `level2_name_cn` varchar(128) NOT NULL COMMENT '二级分类中文名称',
  `level2_name_en` varchar(128) NOT NULL COMMENT '二级分类英文名称',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1启用，0禁用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_base_mcc_l2_code` (`level2_code`),
  KEY `idx_base_mcc_l2_l1` (`level1_id`),
  KEY `idx_base_mcc_l2_status_sort` (`status`,`sort_no`)
) ENGINE=InnoDB AUTO_INCREMENT=1001000208 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCC二级分类表';

-- ----------------------------
-- Table structure for base_mcc_risk_policy
-- ----------------------------
DROP TABLE IF EXISTS `base_mcc_risk_policy`;
CREATE TABLE `base_mcc_risk_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `mcc_code` char(4) NOT NULL COMMENT 'MCC编码，关联base_mcc_code.mcc_code',
  `card_scheme` varchar(32) NOT NULL DEFAULT '' COMMENT '真实卡品牌编码，不允许ALL',
  `channel_scope` varchar(16) NOT NULL DEFAULT 'ALL' COMMENT '渠道适用范围：ALL全部，SPECIFIC指定',
  `channel_code` varchar(64) NOT NULL DEFAULT '' COMMENT '渠道编码，范围为SPECIFIC时必填，否则为空字符串',
  `country_scope` varchar(16) NOT NULL DEFAULT 'ALL' COMMENT '国家地区适用范围：ALL全部，SPECIFIC指定',
  `country_code` varchar(8) NOT NULL DEFAULT '' COMMENT 'ISO国家地区编码，范围为SPECIFIC时必填，否则为空字符串',
  `risk_level` varchar(32) NOT NULL COMMENT '风险等级：LOW、MEDIUM、HIGH、PROHIBITED、UNSPECIFIED',
  `allow_onboarding` tinyint NOT NULL DEFAULT '1' COMMENT '是否允许准入：1允许，0不允许',
  `allow_acquiring` tinyint NOT NULL DEFAULT '1' COMMENT '是否允许收单：1允许，0不允许',
  `require_enhanced_review` tinyint NOT NULL DEFAULT '0' COMMENT '是否需要加强审核：1需要，0不需要',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `policy_status` tinyint NOT NULL DEFAULT '1' COMMENT '策略状态：1启用，0禁用',
  `priority` int NOT NULL DEFAULT '100' COMMENT '优先级，数值越小优先级越高',
  `effective_time` datetime(3) NOT NULL DEFAULT '2026-01-01 00:00:00.000' COMMENT '策略生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '策略失效时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_base_mcc_risk_scope_deleted` (`mcc_code`,`card_scheme`,`channel_scope`,`channel_code`,`country_scope`,`country_code`,`deleted`),
  KEY `idx_mcc_risk_code` (`mcc_code`),
  KEY `idx_mcc_risk_scope` (`card_scheme`,`channel_code`,`country_code`),
  KEY `idx_mcc_risk_level` (`risk_level`),
  KEY `idx_mcc_risk_allow` (`allow_onboarding`,`allow_acquiring`),
  KEY `idx_mcc_risk_status` (`policy_status`),
  KEY `idx_mcc_risk_priority` (`priority`)
) ENGINE=InnoDB AUTO_INCREMENT=1004017287 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCC风险策略表';

-- ----------------------------
-- Table structure for base_merchant_info
-- ----------------------------
DROP TABLE IF EXISTS `base_merchant_info`;
CREATE TABLE `base_merchant_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(32) NOT NULL COMMENT '支付框架颁发的商户号',
  `merchant_name` varchar(128) NOT NULL COMMENT '商户主体名称',
  `billing_descriptor` varchar(64) DEFAULT NULL COMMENT '账单描述，交易账单或渠道侧展示的商户识别名称',
  `merchant_short_name` varchar(64) DEFAULT NULL COMMENT '商户简称',
  `merchant_status` tinyint NOT NULL DEFAULT '1' COMMENT '商户状态：1正常，2冻结，3关闭',
  `default_locale` varchar(20) NOT NULL DEFAULT 'zh-CN' COMMENT '商户系统和邮件默认语言',
  `merchant_category_code` varchar(4) NOT NULL COMMENT '商户类别码MCC',
  `mcc_code` char(4) DEFAULT NULL COMMENT '商户MCC编码，关联base_mcc_code.mcc_code',
  `country_code` char(3) NOT NULL COMMENT '商户所在国家三字码',
  `region_code` varchar(16) DEFAULT NULL COMMENT '商户所在州、省或区域代码',
  `city` varchar(64) DEFAULT NULL COMMENT '商户所在城市',
  `address_line` varchar(256) DEFAULT NULL COMMENT '商户开户地址或经营地址',
  `postal_code` varchar(32) DEFAULT NULL COMMENT '商户经营地址邮编',
  `contact_name` varchar(64) DEFAULT NULL COMMENT '商户联系人姓名',
  `contact_email` varchar(128) DEFAULT NULL COMMENT '商户联系人邮箱',
  `contact_phone` varchar(32) DEFAULT NULL COMMENT '商户联系人电话',
  `settlement_currency` char(3) NOT NULL COMMENT '默认结算币种',
  `timezone` varchar(64) NOT NULL COMMENT '商户业务时区',
  `risk_level` tinyint NOT NULL DEFAULT '2' COMMENT '商户风险等级：1低，2中，3高',
  `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0正常，1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_base_merchant_info_mid` (`merchant_id`),
  KEY `idx_base_merchant_status` (`merchant_status`,`deleted`),
  KEY `idx_base_merchant_mcc_code` (`mcc_code`)
) ENGINE=InnoDB AUTO_INCREMENT=2086018524461572099 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础商户信息表';

-- ----------------------------
-- Table structure for base_merchant_jwt_key
-- ----------------------------
DROP TABLE IF EXISTS `base_merchant_jwt_key`;
CREATE TABLE `base_merchant_jwt_key` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(32) NOT NULL COMMENT '支付框架颁发的商户号',
  `key_version` varchar(32) NOT NULL COMMENT '商户JWT密钥版本号',
  `merchant_key` varchar(256) NOT NULL COMMENT '商户JWT HS256签名密钥，测试环境明文，生产必须密文或KMS',
  `algorithm` varchar(32) NOT NULL COMMENT 'JWT签名算法',
  `expires_seconds` bigint NOT NULL COMMENT 'JWT最大有效期，单位秒',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用标识：1启用，0停用',
  `effective_time` datetime(3) NOT NULL COMMENT '密钥生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '密钥失效时间',
  `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0正常，1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_base_merchant_jwt_key_mid_ver` (`merchant_id`,`key_version`),
  KEY `idx_base_merchant_jwt_key_lookup` (`merchant_id`,`algorithm`,`enabled`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2086018524474155010 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础商户JWT签名密钥表';

-- ----------------------------
-- Table structure for base_merchant_response_key
-- ----------------------------
DROP TABLE IF EXISTS `base_merchant_response_key`;
CREATE TABLE `base_merchant_response_key` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(32) NOT NULL COMMENT '支付框架颁发的商户号',
  `public_key_x509_base64` text NOT NULL COMMENT '商户X.509 DER Base64响应公钥，平台只保存公钥',
  `private_key_pkcs8_base64` text COMMENT '商户响应解密私钥PKCS8 Base64，高权限可见',
  `algorithm` varchar(64) NOT NULL COMMENT '响应data加密算法',
  `key_size` int NOT NULL COMMENT 'RSA密钥位数',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用标识：1启用，0停用',
  `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0正常，1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_base_merchant_response_key_mid` (`merchant_id`),
  KEY `idx_base_merchant_response_key_lookup` (`merchant_id`,`enabled`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2086018524499320835 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础商户响应加密公钥表';

-- ----------------------------
-- Table structure for base_platform_payload_key
-- ----------------------------
DROP TABLE IF EXISTS `base_platform_payload_key`;
CREATE TABLE `base_platform_payload_key` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(32) NOT NULL COMMENT '支付框架颁发的商户号，每个商户独立一套平台请求体RSA密钥',
  `public_key_x509_base64` text NOT NULL COMMENT '平台X.509 DER Base64公钥，下发给商户',
  `private_key_pkcs8_base64` text NOT NULL COMMENT '平台PKCS#8 DER Base64私钥，测试环境明文，生产必须KMS或加密存储',
  `algorithm` varchar(64) NOT NULL COMMENT '请求体加密算法',
  `key_size` int NOT NULL COMMENT 'RSA密钥位数',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用标识：1启用，0停用',
  `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0正常，1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_base_platform_payload_key_mid` (`merchant_id`),
  KEY `idx_base_platform_payload_key_status` (`enabled`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2086018524486737923 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础平台请求体RSA密钥表';

-- ----------------------------
-- Table structure for channel_alert_event
-- ----------------------------
DROP TABLE IF EXISTS `channel_alert_event`;
CREATE TABLE `channel_alert_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_code` varchar(64) NOT NULL COMMENT '预警事件编码',
  `rule_id` bigint NOT NULL COMMENT '触发规则ID',
  `rule_code` varchar(64) NOT NULL COMMENT '触发规则编码',
  `rule_name` varchar(128) NOT NULL COMMENT '触发时规则名称快照',
  `channel_id` bigint NOT NULL COMMENT '渠道ID',
  `channel_code` varchar(64) NOT NULL COMMENT '渠道编码',
  `business_type` varchar(32) NOT NULL COMMENT '业务类型',
  `payment_method` varchar(64) NOT NULL DEFAULT 'ALL' COMMENT '支付方式',
  `card_brand` varchar(255) NOT NULL DEFAULT 'ALL' COMMENT '卡品牌范围，ALL表示全部；多选时使用逗号分隔保存为一个预警范围',
  `rule_type` varchar(64) NOT NULL COMMENT '规则类型',
  `alert_level` varchar(32) NOT NULL COMMENT '预警级别',
  `window_minutes` int NOT NULL COMMENT '统计窗口分钟数',
  `window_start_time` datetime(3) NOT NULL COMMENT '统计窗口开始时间',
  `window_end_time` datetime(3) NOT NULL COMMENT '统计窗口结束时间',
  `sample_count` int NOT NULL DEFAULT '0' COMMENT '窗口样本数',
  `failure_count` int NOT NULL DEFAULT '0' COMMENT '窗口失败笔数',
  `success_count` int NOT NULL DEFAULT '0' COMMENT '窗口成功笔数',
  `success_rate` decimal(10,4) DEFAULT NULL COMMENT '窗口成功率百分比',
  `error_rate` decimal(10,4) DEFAULT NULL COMMENT '窗口异常率百分比',
  `max_continuous_failure_count` int DEFAULT NULL COMMENT '最大连续失败笔数',
  `average_latency_millis` int DEFAULT NULL COMMENT '平均渠道响应耗时，单位毫秒',
  `trigger_value_count` int DEFAULT NULL COMMENT '触发值笔数',
  `trigger_value_rate` decimal(10,4) DEFAULT NULL COMMENT '触发值比例',
  `trigger_value_millis` int DEFAULT NULL COMMENT '触发值耗时，单位毫秒',
  `threshold_snapshot` json DEFAULT NULL COMMENT '触发时阈值快照',
  `event_status` varchar(32) NOT NULL DEFAULT 'OPEN' COMMENT '事件状态：OPEN/ACKNOWLEDGED/RESOLVED',
  `notify_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '通知状态：PENDING/SENT/FAILED/SKIPPED',
  `trigger_time` datetime(3) NOT NULL COMMENT '触发时间',
  `acknowledged_time` datetime(3) DEFAULT NULL COMMENT '人工确认时间',
  `acknowledged_by` varchar(64) DEFAULT NULL COMMENT '人工确认人',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_alert_event_code_deleted` (`event_code`,`deleted`),
  KEY `idx_channel_alert_event_rule` (`rule_id`,`trigger_time`,`deleted`),
  KEY `idx_channel_alert_event_channel_time` (`channel_id`,`trigger_time`,`deleted`),
  KEY `idx_channel_alert_event_status_time` (`event_status`,`trigger_time`,`deleted`),
  KEY `idx_channel_alert_event_type_level` (`rule_type`,`alert_level`,`trigger_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道预警触发事件表';

-- ----------------------------
-- Table structure for channel_alert_notify_log
-- ----------------------------
DROP TABLE IF EXISTS `channel_alert_notify_log`;
CREATE TABLE `channel_alert_notify_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_id` bigint NOT NULL COMMENT '预警事件ID',
  `event_code` varchar(64) NOT NULL COMMENT '预警事件编码',
  `rule_id` bigint NOT NULL COMMENT '预警规则ID',
  `rule_code` varchar(64) NOT NULL COMMENT '预警规则编码',
  `notify_type` varchar(32) NOT NULL DEFAULT 'EMAIL' COMMENT '通知方式，当前仅支持 EMAIL',
  `notify_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '通知状态：PENDING/SENT/FAILED/SKIPPED',
  `email_recipients` varchar(1000) DEFAULT NULL COMMENT '邮件收件人快照',
  `email_cc` varchar(1000) DEFAULT NULL COMMENT '邮件抄送人快照',
  `email_template_code` varchar(80) DEFAULT NULL COMMENT '邮件模板编码快照',
  `email_scene_code` varchar(64) DEFAULT NULL COMMENT '邮件场景编码快照',
  `send_start_time` datetime(3) DEFAULT NULL COMMENT '发送开始时间',
  `send_end_time` datetime(3) DEFAULT NULL COMMENT '发送结束时间',
  `fail_reason` varchar(1000) DEFAULT NULL COMMENT '通知失败原因',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_channel_alert_notify_event` (`event_id`,`deleted`),
  KEY `idx_channel_alert_notify_rule` (`rule_id`,`deleted`),
  KEY `idx_channel_alert_notify_status_time` (`notify_status`,`create_time`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道预警通知执行日志表';

-- ----------------------------
-- Table structure for channel_alert_rule
-- ----------------------------
DROP TABLE IF EXISTS `channel_alert_rule`;
CREATE TABLE `channel_alert_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_code` varchar(64) NOT NULL COMMENT '规则编码，用于事件和通知日志关联',
  `rule_group_code` varchar(64) NOT NULL COMMENT '规则分组编码，同一次批量配置共用',
  `rule_name` varchar(128) NOT NULL COMMENT '规则名称',
  `channel_id` bigint NOT NULL COMMENT '渠道ID，关联 channel_info.id',
  `channel_code` varchar(64) NOT NULL COMMENT '渠道编码',
  `business_type` varchar(32) NOT NULL COMMENT '业务类型：ACQUIRING/PAYOUT',
  `payment_method` varchar(64) NOT NULL DEFAULT 'ALL' COMMENT '支付方式，ALL表示全部',
  `card_brand` varchar(255) NOT NULL DEFAULT 'ALL' COMMENT '卡品牌范围，ALL表示全部；多选时使用逗号分隔保存为一个预警范围',
  `rule_type` varchar(64) NOT NULL COMMENT '规则类型：CONTINUOUS_FAILURE/SUCCESS_RATE_LOW/TECH_ERROR_RATE_HIGH/LATENCY_HIGH',
  `window_minutes` int NOT NULL COMMENT '统计时间窗口，单位分钟',
  `threshold_count` int DEFAULT NULL COMMENT '笔数阈值，用于连续失败类规则',
  `threshold_rate` decimal(10,4) DEFAULT NULL COMMENT '比例阈值，按百分比保存',
  `threshold_millis` int DEFAULT NULL COMMENT '延迟阈值，单位毫秒',
  `minimum_sample_count` int NOT NULL DEFAULT '1' COMMENT '最小样本数，避免小样本误触发',
  `alert_level` varchar(32) NOT NULL COMMENT '预警级别：L1_WARNING/L2_DEGRADED/L3_CIRCUIT_BREAK',
  `rule_description` varchar(1000) DEFAULT NULL COMMENT '规则说明，进入事件快照和邮件变量',
  `auto_degrade` tinyint NOT NULL DEFAULT '0' COMMENT '是否自动降级：0否，1是；当前仅保存配置',
  `auto_circuit_break` tinyint NOT NULL DEFAULT '0' COMMENT '是否自动熔断：0否，1是；当前仅保存配置',
  `rule_status` tinyint NOT NULL DEFAULT '1' COMMENT '规则状态：0停用，1启用',
  `notify_type` varchar(32) NOT NULL DEFAULT 'EMAIL' COMMENT '通知方式，当前仅支持 EMAIL',
  `email_recipients` varchar(1000) NOT NULL COMMENT '邮件收件人，多个邮箱逗号分隔',
  `email_cc` varchar(1000) DEFAULT NULL COMMENT '邮件抄送人，多个邮箱逗号分隔',
  `email_template_code` varchar(80) DEFAULT NULL COMMENT '邮件模板编码',
  `email_scene_code` varchar(64) NOT NULL DEFAULT 'CHANNEL_ALERT' COMMENT '邮件场景编码',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_alert_rule_code_deleted` (`rule_code`,`deleted`),
  UNIQUE KEY `uk_channel_alert_rule_scope_deleted` (`channel_id`,`business_type`,`payment_method`,`card_brand`,`rule_type`,`deleted`),
  KEY `idx_channel_alert_rule_channel` (`channel_id`,`deleted`),
  KEY `idx_channel_alert_rule_code` (`channel_code`,`deleted`),
  KEY `idx_channel_alert_rule_status` (`rule_status`,`deleted`),
  KEY `idx_channel_alert_rule_type_level` (`rule_type`,`alert_level`,`deleted`),
  KEY `idx_channel_alert_rule_group` (`rule_group_code`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道预警规则配置表';

-- ----------------------------
-- Table structure for channel_capability_card_brand
-- ----------------------------
DROP TABLE IF EXISTS `channel_capability_card_brand`;
CREATE TABLE `channel_capability_card_brand` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `capability_id` bigint NOT NULL COMMENT '渠道支付能力ID',
  `channel_id` bigint NOT NULL COMMENT '渠道ID',
  `channel_code` varchar(64) NOT NULL COMMENT '渠道编码',
  `card_brand` varchar(64) NOT NULL COMMENT '卡品牌，如 VISA/MASTERCARD',
  `brand_status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_capability_card_brand_deleted` (`capability_id`,`card_brand`,`deleted`),
  KEY `idx_capability_card_brand_channel` (`channel_id`,`deleted`),
  KEY `idx_capability_card_brand_brand` (`card_brand`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=69 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道支付能力卡品牌表';

-- ----------------------------
-- Table structure for channel_capability_currency
-- ----------------------------
DROP TABLE IF EXISTS `channel_capability_currency`;
CREATE TABLE `channel_capability_currency` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `capability_id` bigint NOT NULL COMMENT '渠道支付能力ID',
  `channel_id` bigint NOT NULL COMMENT '渠道ID',
  `channel_code` varchar(64) NOT NULL COMMENT '渠道编码',
  `currency_code` varchar(3) NOT NULL COMMENT '币种代码',
  `currency_status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_capability_currency_deleted` (`capability_id`,`currency_code`,`deleted`),
  KEY `idx_capability_currency_channel` (`channel_id`,`deleted`),
  KEY `idx_capability_currency_code` (`currency_code`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=85 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道支付能力币种表';

-- ----------------------------
-- Table structure for channel_info
-- ----------------------------
DROP TABLE IF EXISTS `channel_info`;
CREATE TABLE `channel_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `channel_code` varchar(64) NOT NULL COMMENT '渠道编码，全局唯一',
  `channel_cn_name` varchar(128) NOT NULL COMMENT '渠道中文名称',
  `channel_en_name` varchar(128) NOT NULL COMMENT '渠道英文名称',
  `channel_status` tinyint NOT NULL DEFAULT '1' COMMENT '渠道状态：0停用，1启用',
  `support_acquiring` tinyint NOT NULL DEFAULT '0' COMMENT '是否支持收单：0否，1是',
  `support_payout` tinyint NOT NULL DEFAULT '0' COMMENT '是否支持代付：0否，1是',
  `support_3ds` tinyint NOT NULL DEFAULT '0' COMMENT '是否支持3DS：0否，1是',
  `default_request_url` varchar(512) DEFAULT NULL COMMENT '默认渠道请求地址',
  `default_interaction_mode` varchar(32) DEFAULT NULL COMMENT '默认交互方式',
  `connect_timeout_seconds` int NOT NULL DEFAULT '10' COMMENT '连接超时时间，单位秒',
  `read_timeout_seconds` int NOT NULL DEFAULT '30' COMMENT '读取超时时间，单位秒',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_info_code_deleted` (`channel_code`,`deleted`),
  KEY `idx_channel_info_status` (`channel_status`,`deleted`),
  KEY `idx_channel_info_capability` (`support_acquiring`,`support_payout`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道信息表';

-- ----------------------------
-- Table structure for channel_limit_rule
-- ----------------------------
DROP TABLE IF EXISTS `channel_limit_rule`;
CREATE TABLE `channel_limit_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `channel_id` bigint NOT NULL COMMENT '渠道ID',
  `channel_code` varchar(64) NOT NULL COMMENT '渠道编码',
  `business_type` varchar(32) NOT NULL COMMENT '业务类型：ACQUIRING/PAYOUT',
  `payment_method` varchar(64) NOT NULL DEFAULT 'ALL' COMMENT '支付方式，ALL表示渠道级限额',
  `card_brand` varchar(64) NOT NULL DEFAULT 'ALL' COMMENT '卡品牌，ALL表示不限卡品牌',
  `limit_type` varchar(32) NOT NULL COMMENT '限额类型：SINGLE_MIN/SINGLE_MAX/DAILY/WEEKLY/MONTHLY',
  `limit_currency` varchar(3) NOT NULL DEFAULT 'USD' COMMENT '限额币种，当前固定USD',
  `limit_amount` decimal(20,6) NOT NULL COMMENT '限额金额',
  `rule_status` tinyint NOT NULL DEFAULT '1' COMMENT '规则状态：0停用，1启用',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_limit_scope_deleted` (`channel_id`,`business_type`,`payment_method`,`card_brand`,`limit_type`,`deleted`),
  KEY `idx_channel_limit_channel` (`channel_id`,`deleted`),
  KEY `idx_channel_limit_code` (`channel_code`,`deleted`),
  KEY `idx_channel_limit_status` (`rule_status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道限额规则表';

-- ----------------------------
-- Table structure for channel_metadata_schema
-- ----------------------------
DROP TABLE IF EXISTS `channel_metadata_schema`;
CREATE TABLE `channel_metadata_schema` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `channel_id` bigint NOT NULL COMMENT '渠道ID',
  `channel_code` varchar(64) NOT NULL COMMENT '渠道编码',
  `field_key` varchar(64) NOT NULL COMMENT '元数据字段key，如merchantId、username、privateKey',
  `field_label` varchar(128) NOT NULL COMMENT '元数据字段展示名称',
  `field_type` varchar(32) NOT NULL DEFAULT 'TEXT' COMMENT '字段类型：TEXT/PASSWORD/URL/NUMBER/JSON/TEXTAREA/PRIVATE_KEY/PUBLIC_KEY/CERTIFICATE/SELECT',
  `required_flag` tinyint NOT NULL DEFAULT '1' COMMENT '是否必填：0否，1是',
  `sensitive_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否敏感：0否，1是',
  `validation_regex` varchar(512) DEFAULT NULL COMMENT '格式校验正则表达式',
  `placeholder` varchar(255) DEFAULT NULL COMMENT '输入占位说明',
  `default_value` varchar(512) DEFAULT NULL COMMENT '默认值，敏感字段不允许配置默认值',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `field_status` tinyint NOT NULL DEFAULT '1' COMMENT '字段状态：0停用，1启用',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_metadata_key_deleted` (`channel_id`,`field_key`,`deleted`),
  KEY `idx_channel_metadata_channel_sort` (`channel_id`,`field_status`,`deleted`,`sort_order`),
  KEY `idx_channel_metadata_code` (`channel_code`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道MID参数模板表';

-- ----------------------------
-- Table structure for channel_mid_config
-- ----------------------------
DROP TABLE IF EXISTS `channel_mid_config`;
CREATE TABLE `channel_mid_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `channel_id` bigint NOT NULL COMMENT '渠道ID',
  `channel_code` varchar(64) NOT NULL COMMENT '渠道编码',
  `channel_mid` varchar(128) NOT NULL COMMENT '渠道侧真实MID或商户号',
  `mid_name` varchar(128) NOT NULL COMMENT 'MID展示名称',
  `terminal_id` varchar(128) DEFAULT NULL COMMENT '渠道终端号',
  `business_type` varchar(32) NOT NULL COMMENT '业务类型：ACQUIRING/PAYOUT',
  `payment_method_scope` varchar(512) NOT NULL COMMENT '支持支付方式，ALL或逗号分隔',
  `card_brand_scope` varchar(512) NOT NULL DEFAULT 'NONE' COMMENT '银行卡品牌范围，非银行卡为NONE，银行卡为ALL或逗号分隔',
  `transaction_type_scope` varchar(512) NOT NULL COMMENT '支持交易类型，ALL或逗号分隔',
  `currency_scope` varchar(512) NOT NULL COMMENT '支持交易币种，ALL或逗号分隔',
  `allowed_country_scope` varchar(512) NOT NULL COMMENT '允许交易国家，ALL或逗号分隔',
  `default_settlement_currency` char(3) NOT NULL COMMENT '默认结算币种',
  `settlement_cycle` varchar(32) NOT NULL COMMENT '结算周期：T0/T1/T2',
  `settlement_cutoff_time` time DEFAULT NULL COMMENT '结算日切时间',
  `settlement_time_zone` varchar(64) NOT NULL COMMENT '结算时区',
  `mcc` varchar(16) DEFAULT NULL COMMENT 'MID MCC',
  `statement_descriptor` varchar(128) DEFAULT NULL COMMENT '账单描述',
  `metadata_value_json` json DEFAULT NULL COMMENT '按渠道元数据模板录入的MID元数据',
  `mid_status` tinyint NOT NULL DEFAULT '1' COMMENT 'MID状态：0停用，1启用',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间，空表示永不过期',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_mid_deleted` (`channel_id`,`channel_mid`,`deleted`),
  KEY `idx_channel_mid_code_status` (`channel_code`,`mid_status`,`deleted`),
  KEY `idx_channel_mid_business` (`business_type`,`mid_status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道MID配置表';

-- ----------------------------
-- Table structure for channel_payment_capability
-- ----------------------------
DROP TABLE IF EXISTS `channel_payment_capability`;
CREATE TABLE `channel_payment_capability` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `channel_id` bigint NOT NULL COMMENT '渠道ID',
  `channel_code` varchar(64) NOT NULL COMMENT '渠道编码',
  `business_type` varchar(32) NOT NULL COMMENT '业务类型：ACQUIRING/PAYOUT',
  `payment_method` varchar(64) NOT NULL COMMENT '支付方式',
  `transaction_type` varchar(512) NOT NULL DEFAULT 'NONE' COMMENT '交易类型列表，多个以英文逗号分隔，代付为NONE',
  `default_transaction_currency` char(3) NOT NULL COMMENT '默认交易币种，必须属于能力允许币种',
  `support_3ds` tinyint NOT NULL DEFAULT '0' COMMENT '是否支持3DS：0否，1是',
  `support_incremental_authorization` tinyint NOT NULL DEFAULT '0' COMMENT '是否支持增量授权：0否，1是',
  `capability_status` tinyint NOT NULL DEFAULT '1' COMMENT '能力状态：0停用，1启用',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_capability_scope` (`channel_id`,`business_type`,`payment_method`,`deleted`),
  KEY `idx_channel_capability_channel` (`channel_id`,`deleted`),
  KEY `idx_channel_capability_code` (`channel_code`,`deleted`),
  KEY `idx_channel_capability_method` (`business_type`,`payment_method`,`deleted`),
  KEY `idx_channel_capability_status` (`capability_status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道支付能力表';

-- ----------------------------
-- Table structure for exchange_business_rate
-- ----------------------------
DROP TABLE IF EXISTS `exchange_business_rate`;
CREATE TABLE `exchange_business_rate` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rate_type` varchar(32) NOT NULL COMMENT '汇率类型：TRANSACTION_RATE/SETTLEMENT_RATE',
  `source_code` varchar(64) NOT NULL COMMENT '汇率源编码',
  `base_currency` char(3) NOT NULL COMMENT '原始币种',
  `quote_currency` char(3) NOT NULL COMMENT '目标币种',
  `raw_rate_id` bigint DEFAULT NULL COMMENT '原始汇率ID，手工录入业务汇率可为空',
  `rule_id` bigint DEFAULT NULL COMMENT '汇率规则ID，手工录入业务汇率可为空',
  `original_rate` decimal(24,12) NOT NULL COMMENT '规则选取的原始报价',
  `final_rate` decimal(24,12) NOT NULL COMMENT '最终业务汇率',
  `adjust_description` varchar(512) DEFAULT NULL COMMENT '调整说明',
  `effective_time` datetime(3) NOT NULL COMMENT '业务汇率生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '业务汇率失效时间',
  `generate_method` varchar(32) NOT NULL COMMENT '生成方式：AUTO/MANUAL',
  `rate_status` varchar(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED/EXPIRED',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_exchange_business_rate_raw` (`raw_rate_id`,`deleted`),
  KEY `idx_exchange_business_rate_rule` (`rule_id`,`deleted`),
  KEY `idx_exchange_business_rate_lookup` (`rate_type`,`base_currency`,`quote_currency`,`deleted`),
  KEY `idx_exchange_business_rate_current` (`rate_type`,`base_currency`,`quote_currency`,`rate_status`,`effective_time`,`expire_time`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=4904 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务汇率表';

-- ----------------------------
-- Table structure for exchange_rate_fetch_log
-- ----------------------------
DROP TABLE IF EXISTS `exchange_rate_fetch_log`;
CREATE TABLE `exchange_rate_fetch_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_no` varchar(64) NOT NULL COMMENT '拉取批次号',
  `source_code` varchar(64) NOT NULL COMMENT '汇率源编码',
  `fetch_start_time` datetime(3) NOT NULL COMMENT '拉取开始时间',
  `fetch_end_time` datetime(3) DEFAULT NULL COMMENT '拉取结束时间',
  `fetch_status` varchar(32) NOT NULL COMMENT '拉取状态：SUCCESS/FAILED/PARTIAL_SUCCESS',
  `request_url` varchar(512) DEFAULT NULL COMMENT '请求地址',
  `total_count` int NOT NULL DEFAULT '0' COMMENT '解析总条数',
  `success_count` int NOT NULL DEFAULT '0' COMMENT '成功入库条数',
  `duplicate_count` int NOT NULL DEFAULT '0' COMMENT '重复跳过条数',
  `skip_count` int NOT NULL DEFAULT '0' COMMENT '跳过条数',
  `error_message` text COMMENT '错误信息',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_fetch_batch` (`batch_no`),
  KEY `idx_exchange_fetch_source` (`source_code`),
  KEY `idx_exchange_fetch_status` (`fetch_status`),
  KEY `idx_exchange_fetch_time` (`fetch_start_time`)
) ENGINE=InnoDB AUTO_INCREMENT=232 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率拉取日志表';

-- ----------------------------
-- Table structure for exchange_rate_rule
-- ----------------------------
DROP TABLE IF EXISTS `exchange_rate_rule`;
CREATE TABLE `exchange_rate_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rate_type` varchar(32) NOT NULL COMMENT '汇率类型：TRANSACTION_RATE/SETTLEMENT_RATE',
  `source_code` varchar(64) NOT NULL COMMENT '汇率源编码，ALL表示兜底',
  `base_currency` varchar(3) NOT NULL COMMENT '原始币种，ALL表示兜底',
  `quote_currency` varchar(3) NOT NULL COMMENT '目标币种，ALL表示兜底',
  `rate_field` varchar(32) NOT NULL COMMENT '取值字段：SPOT_BUY_RATE/CASH_BUY_RATE/SPOT_SELL_RATE/CASH_SELL_RATE/MIDDLE_RATE',
  `adjust_direction` varchar(16) NOT NULL COMMENT '调整方向：UP/DOWN/NONE',
  `adjust_method` varchar(16) NOT NULL COMMENT '调整方式：BP/PERCENT',
  `adjust_value` decimal(24,12) NOT NULL DEFAULT '0.000000000000' COMMENT '调整值，BP按基点，PERCENT按百分比',
  `decimal_scale` int NOT NULL DEFAULT '8' COMMENT '最终汇率小数位',
  `rounding_mode` varchar(32) NOT NULL DEFAULT 'ROUND_HALF_UP' COMMENT '舍入方式',
  `priority` int NOT NULL DEFAULT '100' COMMENT '优先级，数值越小优先级越高',
  `effective_start_time` datetime(3) DEFAULT NULL COMMENT '规则生效开始时间',
  `effective_end_time` datetime(3) DEFAULT NULL COMMENT '规则生效结束时间',
  `rule_status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_exchange_rate_rule_scope` (`rate_type`,`source_code`,`base_currency`,`quote_currency`,`rule_status`,`deleted`),
  KEY `idx_exchange_rate_rule_time` (`effective_start_time`,`effective_end_time`,`deleted`),
  KEY `idx_exchange_rate_rule_priority` (`priority`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率规则配置表';

-- ----------------------------
-- Table structure for exchange_rate_source
-- ----------------------------
DROP TABLE IF EXISTS `exchange_rate_source`;
CREATE TABLE `exchange_rate_source` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `source_code` varchar(64) NOT NULL COMMENT '汇率源编码，如 BOC/XE/MANUAL',
  `source_name` varchar(128) NOT NULL COMMENT '汇率源名称',
  `source_type` varchar(32) NOT NULL COMMENT '汇率源类型：WEB/API/MANUAL/IMPORT',
  `request_url` varchar(512) DEFAULT NULL COMMENT '汇率源请求地址',
  `default_source` tinyint NOT NULL DEFAULT '0' COMMENT '是否默认来源：0否，1是',
  `priority` int NOT NULL DEFAULT '100' COMMENT '优先级，数值越小优先级越高',
  `timeout_seconds` int NOT NULL DEFAULT '10' COMMENT '拉取超时时间，单位秒',
  `source_status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `last_fetch_time` datetime(3) DEFAULT NULL COMMENT '最近一次拉取完成时间',
  `last_fetch_status` varchar(32) DEFAULT NULL COMMENT '最近一次拉取状态：SUCCESS/FAILED/PARTIAL_SUCCESS',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_rate_source_code_deleted` (`source_code`,`deleted`),
  KEY `idx_exchange_rate_source_status` (`source_status`,`deleted`),
  KEY `idx_exchange_rate_source_priority` (`priority`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率源配置表';

-- ----------------------------
-- Table structure for exchange_rate_usage_snapshot
-- ----------------------------
DROP TABLE IF EXISTS `exchange_rate_usage_snapshot`;
CREATE TABLE `exchange_rate_usage_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rate_type` varchar(32) NOT NULL COMMENT '汇率类型：TRANSACTION_RATE/SETTLEMENT_RATE',
  `usage_scene` varchar(64) NOT NULL COMMENT '汇率使用场景',
  `business_type` varchar(64) NOT NULL COMMENT '业务类型，如 PAYMENT/SETTLEMENT/REFUND',
  `business_no` varchar(128) NOT NULL COMMENT '业务标识，如交易号、订单号、结算批次号',
  `base_currency` char(3) NOT NULL COMMENT '原始币种',
  `quote_currency` char(3) NOT NULL COMMENT '目标币种',
  `used_rate` decimal(24,12) NOT NULL COMMENT '实际使用汇率',
  `business_rate_id` bigint DEFAULT NULL COMMENT '业务汇率ID',
  `raw_rate_id` bigint DEFAULT NULL COMMENT '原始汇率ID',
  `rule_id` bigint DEFAULT NULL COMMENT '汇率规则ID',
  `calculation_description` varchar(512) DEFAULT NULL COMMENT '计算说明',
  `applied_time` datetime(3) NOT NULL COMMENT '业务实际使用时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_exchange_snapshot_business` (`business_type`,`business_no`,`deleted`),
  KEY `idx_exchange_snapshot_scope` (`rate_type`,`usage_scene`,`base_currency`,`quote_currency`,`deleted`),
  KEY `idx_exchange_snapshot_applied` (`applied_time`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率使用快照表';

-- ----------------------------
-- Table structure for exchange_raw_rate
-- ----------------------------
DROP TABLE IF EXISTS `exchange_raw_rate`;
CREATE TABLE `exchange_raw_rate` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `source_code` varchar(64) NOT NULL COMMENT '汇率源编码',
  `base_currency` char(3) NOT NULL COMMENT '原始币种 ISO 4217 编码',
  `quote_currency` char(3) NOT NULL COMMENT '目标币种 ISO 4217 编码',
  `cash_buy_rate` decimal(24,12) DEFAULT NULL COMMENT '现钞买入价，统一为1原始币种兑换目标币种',
  `cash_sell_rate` decimal(24,12) DEFAULT NULL COMMENT '现钞卖出价，统一为1原始币种兑换目标币种',
  `spot_buy_rate` decimal(24,12) DEFAULT NULL COMMENT '现汇买入价，统一为1原始币种兑换目标币种',
  `spot_sell_rate` decimal(24,12) DEFAULT NULL COMMENT '现汇卖出价，统一为1原始币种兑换目标币种',
  `middle_rate` decimal(24,12) DEFAULT NULL COMMENT '中间折算价，统一为1原始币种兑换目标币种',
  `publish_time` datetime(3) NOT NULL COMMENT '汇率源发布时间',
  `fetch_time` datetime(3) NOT NULL COMMENT '系统拉取或录入时间',
  `effective_time` datetime(3) NOT NULL COMMENT '原始汇率生效时间',
  `create_method` varchar(32) NOT NULL COMMENT '创建方式：AUTO/MANUAL/IMPORT',
  `batch_no` varchar(64) DEFAULT NULL COMMENT '拉取或导入批次号',
  `rate_status` varchar(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/VOIDED',
  `void_reason` varchar(512) DEFAULT NULL COMMENT '作废原因',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_raw_rate_source_pair_publish` (`source_code`,`base_currency`,`quote_currency`,`publish_time`,`deleted`),
  KEY `idx_exchange_raw_rate_pair_status` (`base_currency`,`quote_currency`,`rate_status`,`deleted`),
  KEY `idx_exchange_raw_rate_source_time` (`source_code`,`publish_time`,`deleted`),
  KEY `idx_exchange_raw_rate_batch` (`batch_no`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2513 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='原始汇率记录表';

-- ----------------------------
-- Table structure for ip_library_split_model
-- ----------------------------
DROP TABLE IF EXISTS `ip_library_split_model`;
CREATE TABLE `ip_library_split_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `ip_type` varchar(8) NOT NULL COMMENT 'IP 类型：IPV4、IPV6',
  `shard_no` tinyint NOT NULL COMMENT '分片编号：1-8',
  `table_name` varchar(64) NOT NULL COMMENT 'IP 库物理分表名称',
  `range_start` decimal(39,0) NOT NULL COMMENT '分片起始 IP 数值',
  `range_end` decimal(39,0) NOT NULL COMMENT '分片截止 IP 数值',
  `data_version` varchar(32) NOT NULL COMMENT '当前生效数据版本',
  `active_flag` tinyint NOT NULL DEFAULT '1' COMMENT '是否生效：1是，0否',
  `row_count` bigint NOT NULL DEFAULT '0' COMMENT '当前分片数据量',
  `load_status` varchar(32) NOT NULL DEFAULT 'READY' COMMENT '分片数据状态：READY、LOADING、FAILED',
  `start_time` datetime(3) DEFAULT NULL COMMENT '开始处理时间',
  `end_time` datetime(3) DEFAULT NULL COMMENT '处理完毕时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ip_shard_version` (`ip_type`,`shard_no`,`data_version`),
  KEY `idx_ip_route` (`ip_type`,`active_flag`,`range_start`,`range_end`),
  KEY `idx_ip_table_active` (`table_name`,`active_flag`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IP库分片路由配置表';

-- ----------------------------
-- Table structure for ip_library_v4_data_01
-- ----------------------------
DROP TABLE IF EXISTS `ip_library_v4_data_01`;
CREATE TABLE `ip_library_v4_data_01` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `ip_type` varchar(8) NOT NULL DEFAULT 'IPV4' COMMENT 'IP 类型：IPV4、IPV6',
  `ip_number_start` bigint unsigned NOT NULL COMMENT 'IP Number 开始值',
  `ip_number_end` bigint unsigned NOT NULL COMMENT 'IP Number 截止值',
  `country_alpha2` varchar(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
  `country_numeric` varchar(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
  `country_name` varchar(128) NOT NULL COMMENT '国家英文全称',
  `state_province` varchar(128) DEFAULT NULL COMMENT '归属州/省',
  `city` varchar(128) DEFAULT NULL COMMENT '归属城市',
  `data_version` varchar(32) NOT NULL COMMENT '数据版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_ip_start` (`ip_number_start`),
  KEY `idx_ip_range` (`ip_number_start`,`ip_number_end`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ip_lookup` (`data_version`,`deleted`,`ip_number_start`,`ip_number_end`)
) ENGINE=InnoDB AUTO_INCREMENT=746465 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV4 库分表 01';

-- ----------------------------
-- Table structure for ip_library_v4_data_02
-- ----------------------------
DROP TABLE IF EXISTS `ip_library_v4_data_02`;
CREATE TABLE `ip_library_v4_data_02` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `ip_type` varchar(8) NOT NULL DEFAULT 'IPV4' COMMENT 'IP 类型：IPV4、IPV6',
  `ip_number_start` bigint unsigned NOT NULL COMMENT 'IP Number 开始值',
  `ip_number_end` bigint unsigned NOT NULL COMMENT 'IP Number 截止值',
  `country_alpha2` varchar(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
  `country_numeric` varchar(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
  `country_name` varchar(128) NOT NULL COMMENT '国家英文全称',
  `state_province` varchar(128) DEFAULT NULL COMMENT '归属州/省',
  `city` varchar(128) DEFAULT NULL COMMENT '归属城市',
  `data_version` varchar(32) NOT NULL COMMENT '数据版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_ip_start` (`ip_number_start`),
  KEY `idx_ip_range` (`ip_number_start`,`ip_number_end`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ip_lookup` (`data_version`,`deleted`,`ip_number_start`,`ip_number_end`)
) ENGINE=InnoDB AUTO_INCREMENT=1052080 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV4 库分表 02';

-- ----------------------------
-- Table structure for ip_library_v4_data_03
-- ----------------------------
DROP TABLE IF EXISTS `ip_library_v4_data_03`;
CREATE TABLE `ip_library_v4_data_03` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `ip_type` varchar(8) NOT NULL DEFAULT 'IPV4' COMMENT 'IP 类型：IPV4、IPV6',
  `ip_number_start` bigint unsigned NOT NULL COMMENT 'IP Number 开始值',
  `ip_number_end` bigint unsigned NOT NULL COMMENT 'IP Number 截止值',
  `country_alpha2` varchar(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
  `country_numeric` varchar(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
  `country_name` varchar(128) NOT NULL COMMENT '国家英文全称',
  `state_province` varchar(128) DEFAULT NULL COMMENT '归属州/省',
  `city` varchar(128) DEFAULT NULL COMMENT '归属城市',
  `data_version` varchar(32) NOT NULL COMMENT '数据版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_ip_start` (`ip_number_start`),
  KEY `idx_ip_range` (`ip_number_start`,`ip_number_end`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ip_lookup` (`data_version`,`deleted`,`ip_number_start`,`ip_number_end`)
) ENGINE=InnoDB AUTO_INCREMENT=3160586 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV4 库分表 03';

-- ----------------------------
-- Table structure for ip_library_v4_data_04
-- ----------------------------
DROP TABLE IF EXISTS `ip_library_v4_data_04`;
CREATE TABLE `ip_library_v4_data_04` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `ip_type` varchar(8) NOT NULL DEFAULT 'IPV4' COMMENT 'IP 类型：IPV4、IPV6',
  `ip_number_start` bigint unsigned NOT NULL COMMENT 'IP Number 开始值',
  `ip_number_end` bigint unsigned NOT NULL COMMENT 'IP Number 截止值',
  `country_alpha2` varchar(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
  `country_numeric` varchar(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
  `country_name` varchar(128) NOT NULL COMMENT '国家英文全称',
  `state_province` varchar(128) DEFAULT NULL COMMENT '归属州/省',
  `city` varchar(128) DEFAULT NULL COMMENT '归属城市',
  `data_version` varchar(32) NOT NULL COMMENT '数据版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_ip_start` (`ip_number_start`),
  KEY `idx_ip_range` (`ip_number_start`,`ip_number_end`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ip_lookup` (`data_version`,`deleted`,`ip_number_start`,`ip_number_end`)
) ENGINE=InnoDB AUTO_INCREMENT=1128928 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV4 库分表 04';

-- ----------------------------
-- Table structure for ip_library_v4_data_05
-- ----------------------------
DROP TABLE IF EXISTS `ip_library_v4_data_05`;
CREATE TABLE `ip_library_v4_data_05` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `ip_type` varchar(8) NOT NULL DEFAULT 'IPV4' COMMENT 'IP 类型：IPV4、IPV6',
  `ip_number_start` bigint unsigned NOT NULL COMMENT 'IP Number 开始值',
  `ip_number_end` bigint unsigned NOT NULL COMMENT 'IP Number 截止值',
  `country_alpha2` varchar(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
  `country_numeric` varchar(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
  `country_name` varchar(128) NOT NULL COMMENT '国家英文全称',
  `state_province` varchar(128) DEFAULT NULL COMMENT '归属州/省',
  `city` varchar(128) DEFAULT NULL COMMENT '归属城市',
  `data_version` varchar(32) NOT NULL COMMENT '数据版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_ip_start` (`ip_number_start`),
  KEY `idx_ip_range` (`ip_number_start`,`ip_number_end`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ip_lookup` (`data_version`,`deleted`,`ip_number_start`,`ip_number_end`)
) ENGINE=InnoDB AUTO_INCREMENT=307753 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV4 库分表 05';

-- ----------------------------
-- Table structure for ip_library_v4_data_06
-- ----------------------------
DROP TABLE IF EXISTS `ip_library_v4_data_06`;
CREATE TABLE `ip_library_v4_data_06` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `ip_type` varchar(8) NOT NULL DEFAULT 'IPV4' COMMENT 'IP 类型：IPV4、IPV6',
  `ip_number_start` bigint unsigned NOT NULL COMMENT 'IP Number 开始值',
  `ip_number_end` bigint unsigned NOT NULL COMMENT 'IP Number 截止值',
  `country_alpha2` varchar(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
  `country_numeric` varchar(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
  `country_name` varchar(128) NOT NULL COMMENT '国家英文全称',
  `state_province` varchar(128) DEFAULT NULL COMMENT '归属州/省',
  `city` varchar(128) DEFAULT NULL COMMENT '归属城市',
  `data_version` varchar(32) NOT NULL COMMENT '数据版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_ip_start` (`ip_number_start`),
  KEY `idx_ip_range` (`ip_number_start`,`ip_number_end`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ip_lookup` (`data_version`,`deleted`,`ip_number_start`,`ip_number_end`)
) ENGINE=InnoDB AUTO_INCREMENT=1005569 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV4 库分表 06';

-- ----------------------------
-- Table structure for ip_library_v4_data_07
-- ----------------------------
DROP TABLE IF EXISTS `ip_library_v4_data_07`;
CREATE TABLE `ip_library_v4_data_07` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `ip_type` varchar(8) NOT NULL DEFAULT 'IPV4' COMMENT 'IP 类型：IPV4、IPV6',
  `ip_number_start` bigint unsigned NOT NULL COMMENT 'IP Number 开始值',
  `ip_number_end` bigint unsigned NOT NULL COMMENT 'IP Number 截止值',
  `country_alpha2` varchar(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
  `country_numeric` varchar(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
  `country_name` varchar(128) NOT NULL COMMENT '国家英文全称',
  `state_province` varchar(128) DEFAULT NULL COMMENT '归属州/省',
  `city` varchar(128) DEFAULT NULL COMMENT '归属城市',
  `data_version` varchar(32) NOT NULL COMMENT '数据版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_ip_start` (`ip_number_start`),
  KEY `idx_ip_range` (`ip_number_start`,`ip_number_end`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ip_lookup` (`data_version`,`deleted`,`ip_number_start`,`ip_number_end`)
) ENGINE=InnoDB AUTO_INCREMENT=1687314 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV4 库分表 07';

-- ----------------------------
-- Table structure for ip_library_v4_data_08
-- ----------------------------
DROP TABLE IF EXISTS `ip_library_v4_data_08`;
CREATE TABLE `ip_library_v4_data_08` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `ip_type` varchar(8) NOT NULL DEFAULT 'IPV4' COMMENT 'IP 类型：IPV4、IPV6',
  `ip_number_start` bigint unsigned NOT NULL COMMENT 'IP Number 开始值',
  `ip_number_end` bigint unsigned NOT NULL COMMENT 'IP Number 截止值',
  `country_alpha2` varchar(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
  `country_numeric` varchar(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
  `country_name` varchar(128) NOT NULL COMMENT '国家英文全称',
  `state_province` varchar(128) DEFAULT NULL COMMENT '归属州/省',
  `city` varchar(128) DEFAULT NULL COMMENT '归属城市',
  `data_version` varchar(32) NOT NULL COMMENT '数据版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_ip_start` (`ip_number_start`),
  KEY `idx_ip_range` (`ip_number_start`,`ip_number_end`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ip_lookup` (`data_version`,`deleted`,`ip_number_start`,`ip_number_end`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV4 库分表 08';

-- ----------------------------
-- Table structure for ip_library_v6_data_01
-- ----------------------------
DROP TABLE IF EXISTS `ip_library_v6_data_01`;
CREATE TABLE `ip_library_v6_data_01` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `ip_type` varchar(8) NOT NULL DEFAULT 'IPV6' COMMENT 'IP 类型：IPV4、IPV6',
  `ip_number_start` decimal(39,0) NOT NULL COMMENT 'IP Number 开始值',
  `ip_number_end` decimal(39,0) NOT NULL COMMENT 'IP Number 截止值',
  `country_alpha2` varchar(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
  `country_numeric` varchar(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
  `country_name` varchar(128) NOT NULL COMMENT '国家英文全称',
  `state_province` varchar(128) DEFAULT NULL COMMENT '归属州/省',
  `city` varchar(128) DEFAULT NULL COMMENT '归属城市',
  `data_version` varchar(32) NOT NULL COMMENT '数据版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_ip_start` (`ip_number_start`),
  KEY `idx_ip_range` (`ip_number_start`,`ip_number_end`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ip_lookup` (`data_version`,`deleted`,`ip_number_start`,`ip_number_end`)
) ENGINE=InnoDB AUTO_INCREMENT=9119907 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV6 库分表 01';

-- ----------------------------
-- Table structure for ip_library_v6_data_02
-- ----------------------------
DROP TABLE IF EXISTS `ip_library_v6_data_02`;
CREATE TABLE `ip_library_v6_data_02` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `ip_type` varchar(8) NOT NULL DEFAULT 'IPV6' COMMENT 'IP 类型：IPV4、IPV6',
  `ip_number_start` decimal(39,0) NOT NULL COMMENT 'IP Number 开始值',
  `ip_number_end` decimal(39,0) NOT NULL COMMENT 'IP Number 截止值',
  `country_alpha2` varchar(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
  `country_numeric` varchar(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
  `country_name` varchar(128) NOT NULL COMMENT '国家英文全称',
  `state_province` varchar(128) DEFAULT NULL COMMENT '归属州/省',
  `city` varchar(128) DEFAULT NULL COMMENT '归属城市',
  `data_version` varchar(32) NOT NULL COMMENT '数据版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_ip_start` (`ip_number_start`),
  KEY `idx_ip_range` (`ip_number_start`,`ip_number_end`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ip_lookup` (`data_version`,`deleted`,`ip_number_start`,`ip_number_end`)
) ENGINE=InnoDB AUTO_INCREMENT=489674 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV6 库分表 02';

-- ----------------------------
-- Table structure for ip_library_v6_data_03
-- ----------------------------
DROP TABLE IF EXISTS `ip_library_v6_data_03`;
CREATE TABLE `ip_library_v6_data_03` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `ip_type` varchar(8) NOT NULL DEFAULT 'IPV6' COMMENT 'IP 类型：IPV4、IPV6',
  `ip_number_start` decimal(39,0) NOT NULL COMMENT 'IP Number 开始值',
  `ip_number_end` decimal(39,0) NOT NULL COMMENT 'IP Number 截止值',
  `country_alpha2` varchar(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
  `country_numeric` varchar(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
  `country_name` varchar(128) NOT NULL COMMENT '国家英文全称',
  `state_province` varchar(128) DEFAULT NULL COMMENT '归属州/省',
  `city` varchar(128) DEFAULT NULL COMMENT '归属城市',
  `data_version` varchar(32) NOT NULL COMMENT '数据版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_ip_start` (`ip_number_start`),
  KEY `idx_ip_range` (`ip_number_start`,`ip_number_end`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ip_lookup` (`data_version`,`deleted`,`ip_number_start`,`ip_number_end`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV6 库分表 03';

-- ----------------------------
-- Table structure for ip_library_v6_data_04
-- ----------------------------
DROP TABLE IF EXISTS `ip_library_v6_data_04`;
CREATE TABLE `ip_library_v6_data_04` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `ip_type` varchar(8) NOT NULL DEFAULT 'IPV6' COMMENT 'IP 类型：IPV4、IPV6',
  `ip_number_start` decimal(39,0) NOT NULL COMMENT 'IP Number 开始值',
  `ip_number_end` decimal(39,0) NOT NULL COMMENT 'IP Number 截止值',
  `country_alpha2` varchar(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
  `country_numeric` varchar(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
  `country_name` varchar(128) NOT NULL COMMENT '国家英文全称',
  `state_province` varchar(128) DEFAULT NULL COMMENT '归属州/省',
  `city` varchar(128) DEFAULT NULL COMMENT '归属城市',
  `data_version` varchar(32) NOT NULL COMMENT '数据版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_ip_start` (`ip_number_start`),
  KEY `idx_ip_range` (`ip_number_start`,`ip_number_end`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ip_lookup` (`data_version`,`deleted`,`ip_number_start`,`ip_number_end`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV6 库分表 04';

-- ----------------------------
-- Table structure for ip_library_v6_data_05
-- ----------------------------
DROP TABLE IF EXISTS `ip_library_v6_data_05`;
CREATE TABLE `ip_library_v6_data_05` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `ip_type` varchar(8) NOT NULL DEFAULT 'IPV6' COMMENT 'IP 类型：IPV4、IPV6',
  `ip_number_start` decimal(39,0) NOT NULL COMMENT 'IP Number 开始值',
  `ip_number_end` decimal(39,0) NOT NULL COMMENT 'IP Number 截止值',
  `country_alpha2` varchar(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
  `country_numeric` varchar(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
  `country_name` varchar(128) NOT NULL COMMENT '国家英文全称',
  `state_province` varchar(128) DEFAULT NULL COMMENT '归属州/省',
  `city` varchar(128) DEFAULT NULL COMMENT '归属城市',
  `data_version` varchar(32) NOT NULL COMMENT '数据版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_ip_start` (`ip_number_start`),
  KEY `idx_ip_range` (`ip_number_start`,`ip_number_end`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ip_lookup` (`data_version`,`deleted`,`ip_number_start`,`ip_number_end`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV6 库分表 05';

-- ----------------------------
-- Table structure for ip_library_v6_data_06
-- ----------------------------
DROP TABLE IF EXISTS `ip_library_v6_data_06`;
CREATE TABLE `ip_library_v6_data_06` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `ip_type` varchar(8) NOT NULL DEFAULT 'IPV6' COMMENT 'IP 类型：IPV4、IPV6',
  `ip_number_start` decimal(39,0) NOT NULL COMMENT 'IP Number 开始值',
  `ip_number_end` decimal(39,0) NOT NULL COMMENT 'IP Number 截止值',
  `country_alpha2` varchar(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
  `country_numeric` varchar(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
  `country_name` varchar(128) NOT NULL COMMENT '国家英文全称',
  `state_province` varchar(128) DEFAULT NULL COMMENT '归属州/省',
  `city` varchar(128) DEFAULT NULL COMMENT '归属城市',
  `data_version` varchar(32) NOT NULL COMMENT '数据版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_ip_start` (`ip_number_start`),
  KEY `idx_ip_range` (`ip_number_start`,`ip_number_end`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ip_lookup` (`data_version`,`deleted`,`ip_number_start`,`ip_number_end`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV6 库分表 06';

-- ----------------------------
-- Table structure for ip_library_v6_data_07
-- ----------------------------
DROP TABLE IF EXISTS `ip_library_v6_data_07`;
CREATE TABLE `ip_library_v6_data_07` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `ip_type` varchar(8) NOT NULL DEFAULT 'IPV6' COMMENT 'IP 类型：IPV4、IPV6',
  `ip_number_start` decimal(39,0) NOT NULL COMMENT 'IP Number 开始值',
  `ip_number_end` decimal(39,0) NOT NULL COMMENT 'IP Number 截止值',
  `country_alpha2` varchar(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
  `country_numeric` varchar(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
  `country_name` varchar(128) NOT NULL COMMENT '国家英文全称',
  `state_province` varchar(128) DEFAULT NULL COMMENT '归属州/省',
  `city` varchar(128) DEFAULT NULL COMMENT '归属城市',
  `data_version` varchar(32) NOT NULL COMMENT '数据版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_ip_start` (`ip_number_start`),
  KEY `idx_ip_range` (`ip_number_start`,`ip_number_end`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ip_lookup` (`data_version`,`deleted`,`ip_number_start`,`ip_number_end`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV6 库分表 07';

-- ----------------------------
-- Table structure for ip_library_v6_data_08
-- ----------------------------
DROP TABLE IF EXISTS `ip_library_v6_data_08`;
CREATE TABLE `ip_library_v6_data_08` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `ip_type` varchar(8) NOT NULL DEFAULT 'IPV6' COMMENT 'IP 类型：IPV4、IPV6',
  `ip_number_start` decimal(39,0) NOT NULL COMMENT 'IP Number 开始值',
  `ip_number_end` decimal(39,0) NOT NULL COMMENT 'IP Number 截止值',
  `country_alpha2` varchar(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
  `country_numeric` varchar(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
  `country_name` varchar(128) NOT NULL COMMENT '国家英文全称',
  `state_province` varchar(128) DEFAULT NULL COMMENT '归属州/省',
  `city` varchar(128) DEFAULT NULL COMMENT '归属城市',
  `data_version` varchar(32) NOT NULL COMMENT '数据版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_ip_start` (`ip_number_start`),
  KEY `idx_ip_range` (`ip_number_start`,`ip_number_end`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_ip_lookup` (`data_version`,`deleted`,`ip_number_start`,`ip_number_end`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV6 库分表 08';

-- ----------------------------
-- Table structure for merchant_channel_mid_binding
-- ----------------------------
DROP TABLE IF EXISTS `merchant_channel_mid_binding`;
CREATE TABLE `merchant_channel_mid_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `channel_id` bigint NOT NULL COMMENT '渠道ID',
  `channel_code` varchar(64) NOT NULL COMMENT '渠道编码',
  `mid_config_id` bigint NOT NULL COMMENT '渠道MID配置ID',
  `channel_mid` varchar(128) NOT NULL COMMENT '渠道侧真实MID或商户号',
  `binding_status` tinyint NOT NULL DEFAULT '1' COMMENT '绑定状态：0停用，1启用',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间，空表示永不过期',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_mid_deleted` (`merchant_id`,`mid_config_id`,`deleted`),
  KEY `idx_merchant_channel_status` (`merchant_id`,`channel_id`,`binding_status`,`deleted`),
  KEY `idx_binding_mid` (`mid_config_id`,`binding_status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户渠道MID绑定表';

-- ----------------------------
-- Table structure for merchant_ip_whitelist
-- ----------------------------
DROP TABLE IF EXISTS `merchant_ip_whitelist`;
CREATE TABLE `merchant_ip_whitelist` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `merchant_id` varchar(32) NOT NULL COMMENT '商户号，对应 base_merchant_info.merchant_id',
  `ip_type` varchar(8) NOT NULL COMMENT 'IP类型：IPv4/IPv6',
  `ip_value` varchar(45) NOT NULL COMMENT '规范化后的精确 IP 地址',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `approval_status` tinyint NOT NULL DEFAULT '1' COMMENT '审核状态：0待审核，1审核通过，2审核拒绝',
  `approval_remark` varchar(500) DEFAULT NULL COMMENT '审批说明，审核拒绝时必填',
  `submit_source` varchar(16) NOT NULL DEFAULT 'ADMIN' COMMENT '提交来源：ADMIN、MERCHANT',
  `review_by` varchar(64) DEFAULT NULL COMMENT '审核人账号或姓名',
  `review_time` datetime(3) DEFAULT NULL COMMENT '审核时间',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，删除时写入主键ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_ip_whitelist_merchant_ip_deleted` (`merchant_id`,`ip_value`,`deleted`),
  KEY `idx_merchant_ip_whitelist_merchant_time` (`merchant_id`,`gmt_modified`,`id`),
  KEY `idx_merchant_ip_whitelist_lookup` (`merchant_id`,`ip_value`,`approval_status`,`status`,`deleted`),
  KEY `idx_merchant_ip_whitelist_approval` (`approval_status`,`submit_source`,`gmt_create`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户 OpenAPI IP 白名单';

-- ----------------------------
-- Table structure for merchant_openapi_access_config
-- ----------------------------
DROP TABLE IF EXISTS `merchant_openapi_access_config`;
CREATE TABLE `merchant_openapi_access_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `merchant_id` varchar(32) NOT NULL COMMENT '商户号，对应 base_merchant_info.merchant_id',
  `ip_whitelist_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用 OpenAPI 请求 IP 白名单校验：1启用，0关闭',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，删除时写入主键ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_openapi_access_config_merchant_deleted` (`merchant_id`,`deleted`),
  KEY `idx_merchant_openapi_access_config_time` (`gmt_modified`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户 OpenAPI 入站访问配置';

-- ----------------------------
-- Table structure for merchant_security_cache_invalidation_outbox
-- ----------------------------
DROP TABLE IF EXISTS `merchant_security_cache_invalidation_outbox`;
CREATE TABLE `merchant_security_cache_invalidation_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_id` varchar(64) NOT NULL COMMENT '失效事件唯一编号',
  `cache_name` varchar(64) NOT NULL COMMENT '已登记 Spring Cache 名称',
  `business_key` varchar(128) NOT NULL COMMENT '待失效业务缓存 Key：商户号或平台公开配置键',
  `gate_token` varchar(128) NOT NULL COMMENT 'Redis 失效门禁持有者 token',
  `event_status` varchar(16) NOT NULL DEFAULT 'INIT' COMMENT '状态：INIT、FAILED、SENT',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '已重试次数',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下次重试时间',
  `published_time` datetime(3) DEFAULT NULL COMMENT '失效成功时间',
  `failure_reason` varchar(512) DEFAULT NULL COMMENT '最近一次失败原因摘要',
  `version` int NOT NULL DEFAULT '0' COMMENT 'CAS 版本号',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_security_cache_event` (`event_id`),
  KEY `idx_merchant_security_cache_due` (`event_status`,`next_retry_time`,`create_time`,`id`),
  KEY `idx_merchant_security_cache_target` (`cache_name`,`business_key`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2665 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受管永久缓存可靠失效事件表（兼容保留历史表名）';

-- ----------------------------
-- Table structure for msg_email_account
-- ----------------------------
DROP TABLE IF EXISTS `msg_email_account`;
CREATE TABLE `msg_email_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `account_code` varchar(64) NOT NULL COMMENT '账户编码',
  `account_name` varchar(100) NOT NULL COMMENT '账户名称',
  `app_code` varchar(32) NOT NULL COMMENT '所属系统：ADMIN管理系统，MERCHANT商户系统',
  `scope_type` varchar(32) NOT NULL COMMENT '配置范围：SYSTEM系统默认，MERCHANT指定商户',
  `merchant_id` varchar(64) DEFAULT NULL COMMENT '商户ID',
  `merchant_no` varchar(64) DEFAULT NULL COMMENT '商户号',
  `merchant_name` varchar(200) DEFAULT NULL COMMENT '商户名称',
  `scene_code` varchar(64) NOT NULL DEFAULT 'COMMON' COMMENT '适用场景',
  `provider_type` varchar(32) NOT NULL DEFAULT 'SMTP' COMMENT '邮件服务商类型',
  `from_name` varchar(100) NOT NULL COMMENT '发件人名称',
  `from_email` varchar(255) NOT NULL COMMENT '发件邮箱',
  `reply_to_email` varchar(255) DEFAULT NULL COMMENT '回复邮箱',
  `smtp_host` varchar(255) NOT NULL COMMENT 'SMTP服务器地址',
  `smtp_port` int NOT NULL COMMENT 'SMTP端口',
  `encryption_type` varchar(32) NOT NULL DEFAULT 'SSL' COMMENT '加密方式：SSL/TLS/STARTTLS/NONE',
  `smtp_auth_required` tinyint NOT NULL DEFAULT '1' COMMENT '是否需要SMTP认证：0否，1是',
  `smtp_username` varchar(255) NOT NULL COMMENT 'SMTP账号',
  `smtp_password_cipher` text COMMENT 'SMTP密码密文',
  `password_updated_time` datetime(3) DEFAULT NULL COMMENT '密码更新时间',
  `connect_timeout_ms` int NOT NULL DEFAULT '10000' COMMENT '连接超时时间，单位毫秒',
  `read_timeout_ms` int NOT NULL DEFAULT '30000' COMMENT '读取超时时间，单位毫秒',
  `default_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否默认账户：0否，1是',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `verify_status` tinyint NOT NULL DEFAULT '0' COMMENT '验证状态：0未验证，1验证成功，2验证失败',
  `last_test_time` datetime(3) DEFAULT NULL COMMENT '最近测试时间',
  `last_error_message` varchar(1000) DEFAULT NULL COMMENT '最近失败原因',
  `minute_limit` int NOT NULL DEFAULT '60' COMMENT '单分钟最大发送数',
  `daily_limit` int NOT NULL DEFAULT '10000' COMMENT '单日最大发送数',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_account_code_deleted` (`account_code`,`deleted`),
  KEY `idx_email_account_route` (`app_code`,`scope_type`,`merchant_id`,`scene_code`,`default_flag`,`status`,`deleted`),
  KEY `idx_email_account_merchant` (`merchant_id`,`merchant_no`,`deleted`),
  KEY `idx_email_account_from_email` (`from_email`),
  KEY `idx_email_account_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件发件账户配置表';

-- ----------------------------
-- Table structure for msg_email_send_record
-- ----------------------------
DROP TABLE IF EXISTS `msg_email_send_record`;
CREATE TABLE `msg_email_send_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `email_no` varchar(64) NOT NULL COMMENT '邮件流水号',
  `app_code` varchar(32) NOT NULL COMMENT '所属系统',
  `merchant_id` varchar(64) DEFAULT NULL COMMENT '商户ID',
  `merchant_no` varchar(64) DEFAULT NULL COMMENT '商户号',
  `merchant_name` varchar(200) DEFAULT NULL COMMENT '商户名称',
  `scene_code` varchar(64) NOT NULL COMMENT '邮件场景',
  `template_code` varchar(100) DEFAULT NULL COMMENT '模板编码',
  `template_name` varchar(150) DEFAULT NULL COMMENT '模板名称',
  `locale` varchar(20) NOT NULL DEFAULT 'zh-CN' COMMENT '语言',
  `account_id` bigint DEFAULT NULL COMMENT '发件账户ID',
  `account_code` varchar(64) DEFAULT NULL COMMENT '发件账户编码',
  `provider_type` varchar(32) DEFAULT NULL COMMENT '邮件服务商类型',
  `from_name` varchar(100) DEFAULT NULL COMMENT '发件人名称',
  `from_email` varchar(255) DEFAULT NULL COMMENT '发件邮箱',
  `reply_to_email` varchar(255) DEFAULT NULL COMMENT '回复邮箱',
  `to_emails` text NOT NULL COMMENT '收件人邮箱JSON数组',
  `cc_emails` text COMMENT '抄送邮箱JSON数组',
  `bcc_emails` text COMMENT '密送邮箱JSON数组',
  `subject` varchar(500) NOT NULL COMMENT '邮件标题',
  `content_snapshot` longtext COMMENT '邮件正文快照，敏感内容需脱敏',
  `delivery_content_cipher` longtext COMMENT 'encrypted delivery body, cleared after success',
  `content_type` varchar(16) DEFAULT NULL COMMENT 'HTML or TEXT',
  `variables_snapshot` json DEFAULT NULL COMMENT '模板变量快照，敏感变量需脱敏',
  `biz_type` varchar(64) DEFAULT NULL COMMENT '业务类型',
  `biz_no` varchar(100) DEFAULT NULL COMMENT '业务单号',
  `send_status` tinyint NOT NULL DEFAULT '0' COMMENT '发送状态：0待发送，1发送中，2发送成功，3发送失败，4重试中，5已取消',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '已重试次数',
  `max_retry_count` int NOT NULL DEFAULT '0' COMMENT '最大重试次数',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下次重试时间',
  `send_start_time` datetime(3) DEFAULT NULL COMMENT '发送开始时间',
  `send_end_time` datetime(3) DEFAULT NULL COMMENT '发送结束时间',
  `send_success_time` datetime(3) DEFAULT NULL COMMENT '发送成功时间',
  `cost_ms` bigint DEFAULT NULL COMMENT '发送耗时，单位毫秒',
  `error_code` varchar(100) DEFAULT NULL COMMENT '错误编码',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '错误信息',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人名称',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_send_record_no` (`email_no`),
  KEY `idx_email_record_app_scene` (`app_code`,`scene_code`,`send_status`,`deleted`),
  KEY `idx_email_record_merchant` (`merchant_id`,`merchant_no`,`deleted`),
  KEY `idx_email_record_template` (`template_code`),
  KEY `idx_email_record_biz` (`biz_type`,`biz_no`),
  KEY `idx_email_record_create_time` (`create_time`),
  KEY `idx_email_record_send_time` (`send_success_time`),
  KEY `idx_email_record_retry` (`app_code`,`send_status`,`next_retry_time`,`deleted`),
  KEY `idx_email_record_recovery` (`app_code`,`send_status`,`send_start_time`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件发送记录表';

-- ----------------------------
-- Table structure for msg_email_template
-- ----------------------------
DROP TABLE IF EXISTS `msg_email_template`;
CREATE TABLE `msg_email_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_code` varchar(100) NOT NULL COMMENT '模板编码',
  `template_name` varchar(150) NOT NULL COMMENT '模板名称',
  `app_code` varchar(32) NOT NULL COMMENT '所属系统：ADMIN管理系统，MERCHANT商户系统，COMMON通用',
  `scene_code` varchar(64) NOT NULL COMMENT '模板场景',
  `locale` varchar(20) NOT NULL DEFAULT 'zh-CN' COMMENT '语言',
  `subject_template` varchar(500) NOT NULL COMMENT '邮件标题模板',
  `content_type` varchar(20) NOT NULL DEFAULT 'HTML' COMMENT '内容类型：HTML/TEXT',
  `content_template` longtext NOT NULL COMMENT '邮件正文模板',
  `variable_schema` json DEFAULT NULL COMMENT '模板变量定义',
  `sensitive_variable_names` json DEFAULT NULL COMMENT '敏感变量名称',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `system_builtin` tinyint NOT NULL DEFAULT '0' COMMENT '是否系统内置：0否，1是',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '版本号',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_template_code_locale_deleted` (`template_code`,`locale`,`deleted`),
  KEY `idx_email_template_app_scene` (`app_code`,`scene_code`,`locale`,`status`,`deleted`),
  KEY `idx_email_template_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=81 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件模板表';

-- ----------------------------
-- Table structure for payment_checkout_attempt
-- ----------------------------
DROP TABLE IF EXISTS `payment_checkout_attempt`;
CREATE TABLE `payment_checkout_attempt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID，使用系统统一主键规则。',
  `checkout_attempt_id` varchar(64) NOT NULL COMMENT '收银台支付尝试ID。',
  `checkout_session_id` varchar(64) NOT NULL COMMENT 'Hosted Checkout 会话ID。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户原始订单号。',
  `attempt_no` int NOT NULL COMMENT '会话内尝试序号，从 1 开始递增。',
  `attempt_request_id` varchar(128) NOT NULL COMMENT '前端提交支付时生成的幂等请求ID，同一会话内唯一。',
  `request_fingerprint` varchar(128) NOT NULL COMMENT '支付提交明文业务参数摘要，必须排除 PAN、CVV 原文。',
  `attempt_status` varchar(32) NOT NULL COMMENT '尝试状态：INIT、CARD_SUBMITTED、THREE_DS_INITIATED、THREE_DS_REQUIRED、THREE_DS_RETURNED、THREE_DS_PASSED、THREE_DS_FAILED、CHANNEL_SUBMITTED、SUCCEEDED、FAILED、PROCESSING、ABANDONED。',
  `process_stage` varchar(64) NOT NULL COMMENT '内部处理阶段，如 CARD_VALIDATE、INITIATE_3DS、AUTHENTICATE_PAYER、SUBMIT_CHANNEL、WAITING_CALLBACK。',
  `payment_method` varchar(32) NOT NULL COMMENT '支付方式，V1 固定 BANK_CARD。',
  `payment_brand` varchar(32) DEFAULT NULL COMMENT '卡品牌，如 VISA、MASTERCARD、AMEX、JCB。',
  `label_currency` char(3) NOT NULL COMMENT '本次尝试页面展示币种快照。',
  `label_amount` decimal(20,6) NOT NULL COMMENT '本次尝试页面展示金额快照。',
  `channel_request_currency` char(3) DEFAULT NULL COMMENT '上送渠道币种。',
  `channel_request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道金额。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '本次尝试关联的交易生命周期ID。',
  `transaction_id` varchar(64) DEFAULT NULL COMMENT '本次尝试关联的平台交易ID。',
  `transaction_date_time` datetime(3) DEFAULT NULL COMMENT '本次尝试关联交易业务时间，用于路由 transaction_* 分表。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码，V1 为 MPGS。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道侧主订单号。',
  `channel_transaction_id` varchar(128) DEFAULT NULL COMMENT '渠道侧交易ID。',
  `channel_request_id` varchar(64) DEFAULT NULL COMMENT '最近一次渠道请求ID，关联 transaction_channel_request.request_id。',
  `channel_status` varchar(64) DEFAULT NULL COMMENT '渠道原始状态摘要。',
  `channel_response_code` varchar(64) DEFAULT NULL COMMENT '渠道响应码摘要。',
  `channel_response_message` varchar(512) DEFAULT NULL COMMENT '渠道响应描述摘要，禁止保存敏感原文。',
  `card_bin` varchar(12) DEFAULT NULL COMMENT '卡 BIN，可按合规要求保留前 6 或前 8。',
  `card_last4` varchar(4) DEFAULT NULL COMMENT '卡号后四位。',
  `card_number_masked` varchar(32) DEFAULT NULL COMMENT '脱敏卡号，如 512345******0008。',
  `cardholder_name_masked` varchar(128) DEFAULT NULL COMMENT '持卡人姓名脱敏展示值。',
  `payment_account_hash` char(64) DEFAULT NULL COMMENT '支付账户哈希，用于风控和排查，不可反推 PAN。',
  `three_ds_required` tinyint NOT NULL DEFAULT '0' COMMENT '本次尝试是否需要 3DS，0否，1是。',
  `three_ds_status` varchar(32) DEFAULT NULL COMMENT '3DS 状态：NOT_REQUIRED、INITIATED、CHALLENGE_REQUIRED、AUTHENTICATED、FAILED、UNAVAILABLE。',
  `three_ds_version` varchar(32) DEFAULT NULL COMMENT '3DS 协议版本，如 2.2.0。',
  `three_ds_transaction_id` varchar(128) DEFAULT NULL COMMENT '3DS 交易ID。',
  `three_ds_server_transaction_id` varchar(128) DEFAULT NULL COMMENT '3DS Server 交易ID。',
  `acs_transaction_id` varchar(128) DEFAULT NULL COMMENT 'ACS 交易ID。',
  `ds_transaction_id` varchar(128) DEFAULT NULL COMMENT 'Directory Server 交易ID。',
  `eci` varchar(8) DEFAULT NULL COMMENT '电子商务交易指示值 ECI。',
  `liability_shift` tinyint DEFAULT NULL COMMENT '是否责任转移，0否，1是。',
  `three_ds_return_token_hash` char(64) DEFAULT NULL COMMENT '3DS 浏览器回跳一次性 token 摘要，不保存明文。',
  `authentication_redirect_url_hash` char(64) DEFAULT NULL COMMENT '3DS 认证跳转或回跳地址哈希，用于排查。',
  `browser_info_json` json DEFAULT NULL COMMENT '3DS 和风控所需浏览器摘要，禁止保存完整 User-Agent 原文以外的敏感字段。',
  `device_info_json` json DEFAULT NULL COMMENT '设备摘要信息，禁止保存设备长期明文标识。',
  `failure_reason_code` varchar(64) DEFAULT NULL COMMENT '失败原因码。',
  `failure_reason_message` varchar(512) DEFAULT NULL COMMENT '后台可见失败原因描述。',
  `payer_visible_message` varchar(512) DEFAULT NULL COMMENT '付款人可见的模糊失败原因。',
  `submit_time` datetime(3) DEFAULT NULL COMMENT '付款人提交支付时间。',
  `authentication_start_time` datetime(3) DEFAULT NULL COMMENT '3DS 认证开始时间。',
  `authentication_complete_time` datetime(3) DEFAULT NULL COMMENT '3DS 认证完成时间。',
  `channel_submit_time` datetime(3) DEFAULT NULL COMMENT '上送渠道扣款时间。',
  `complete_time` datetime(3) DEFAULT NULL COMMENT '尝试完成时间。',
  `result_snapshot` json DEFAULT NULL COMMENT '本次尝试返回前端的结果快照，禁止保存 PAN、CVV、JWT、CAVV 明文。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，用于状态机 CAS 更新。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识，资金和审计相关记录原则上不做业务删除。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_checkout_attempt_id` (`checkout_attempt_id`),
  UNIQUE KEY `uk_session_attempt_no` (`checkout_session_id`,`attempt_no`),
  UNIQUE KEY `uk_session_attempt_request` (`checkout_session_id`,`attempt_request_id`),
  UNIQUE KEY `uk_three_ds_return_token_hash` (`three_ds_return_token_hash`),
  KEY `idx_session_status` (`checkout_session_id`,`attempt_status`),
  KEY `idx_merchant_order_time` (`merchant_id`,`merchant_order_no`,`create_time`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_channel_transaction` (`channel_code`,`channel_transaction_id`,`transaction_date_time`),
  KEY `idx_three_ds_transaction` (`three_ds_transaction_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hosted Checkout 支付尝试表，记录卡支付提交、MPGS 请求、3DS 编排和尝试结果，不保存卡敏感原文。';

-- ----------------------------
-- Table structure for payment_checkout_event
-- ----------------------------
DROP TABLE IF EXISTS `payment_checkout_event`;
CREATE TABLE `payment_checkout_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID，使用系统统一主键规则。',
  `checkout_event_id` varchar(64) NOT NULL COMMENT '收银台事件ID。',
  `checkout_session_id` varchar(64) NOT NULL COMMENT 'Hosted Checkout 会话ID。',
  `checkout_attempt_id` varchar(64) DEFAULT NULL COMMENT '收银台支付尝试ID，无尝试的页面事件可为空。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型，如 SESSION_CREATED、SESSION_OPENED、PAYMENT_SUBMITTED、THREE_DS_INITIATED、THREE_DS_RETURNED、STATUS_POLLED、RESULT_RENDERED。',
  `event_stage` varchar(64) DEFAULT NULL COMMENT '事件所处阶段。',
  `event_result` varchar(32) NOT NULL COMMENT '事件结果：SUCCESS、FAILED、IGNORED。',
  `checkout_status_before` varchar(32) DEFAULT NULL COMMENT '事件发生前会话状态。',
  `checkout_status_after` varchar(32) DEFAULT NULL COMMENT '事件发生后会话状态。',
  `attempt_status_before` varchar(32) DEFAULT NULL COMMENT '事件发生前尝试状态。',
  `attempt_status_after` varchar(32) DEFAULT NULL COMMENT '事件发生后尝试状态。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '关联交易生命周期ID。',
  `transaction_id` varchar(64) DEFAULT NULL COMMENT '关联平台交易ID。',
  `transaction_date_time` datetime(3) DEFAULT NULL COMMENT '关联交易业务时间，用于路由 transaction_* 分表。',
  `trace_id` varchar(128) DEFAULT NULL COMMENT '链路追踪ID。',
  `request_id` varchar(128) DEFAULT NULL COMMENT '前端或内部请求ID。',
  `client_ip_hash` char(64) DEFAULT NULL COMMENT '客户端 IP 哈希。',
  `user_agent_hash` char(64) DEFAULT NULL COMMENT 'User-Agent 哈希。',
  `origin_hash` char(64) DEFAULT NULL COMMENT 'Origin Header 哈希。',
  `referer_hash` char(64) DEFAULT NULL COMMENT 'Referer Header 哈希。',
  `event_payload_json` json DEFAULT NULL COMMENT '事件脱敏摘要，禁止保存 PAN、CVV、JWT、密钥和完整 token。',
  `event_time` datetime(3) NOT NULL COMMENT '事件发生时间。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_checkout_event_id` (`checkout_event_id`),
  KEY `idx_session_time` (`checkout_session_id`,`event_time`),
  KEY `idx_attempt_time` (`checkout_attempt_id`,`event_time`),
  KEY `idx_event_type_time` (`event_type`,`event_time`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=409 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hosted Checkout 业务和页面事件日志，支撑审计、排查和用户体验分析。';

-- ----------------------------
-- Table structure for payment_checkout_security_event
-- ----------------------------
DROP TABLE IF EXISTS `payment_checkout_security_event`;
CREATE TABLE `payment_checkout_security_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID，使用系统统一主键规则。',
  `security_event_id` varchar(64) NOT NULL COMMENT '收银台安全事件ID。',
  `checkout_session_id` varchar(64) DEFAULT NULL COMMENT 'Hosted Checkout 会话ID；非法 token 未解析成功时可为空。',
  `checkout_attempt_id` varchar(64) DEFAULT NULL COMMENT '收银台支付尝试ID；未进入支付尝试时可为空。',
  `merchant_id` varchar(64) DEFAULT NULL COMMENT '平台商户号；非法 token 未解析成功时可为空。',
  `token_hash` char(64) DEFAULT NULL COMMENT '请求中 opaqueToken 的 HMAC-SHA256 摘要，无法计算时为空。',
  `security_event_type` varchar(64) NOT NULL COMMENT '安全事件类型：TOKEN_INVALID、TOKEN_EXPIRED、TOKEN_REVOKED、RATE_LIMITED、ORIGIN_DENIED、CSRF_INVALID、METHOD_NOT_ALLOWED、STATUS_NOT_PAYABLE、CARD_INPUT_REJECTED。',
  `security_decision` varchar(32) NOT NULL COMMENT '安全决策：ALLOW、BLOCK、CHALLENGE、LOG_ONLY。',
  `block_reason_code` varchar(64) DEFAULT NULL COMMENT '拦截原因码，前端只展示模糊文案。',
  `http_status` int DEFAULT NULL COMMENT '对外 HTTP 状态码。',
  `request_method` varchar(16) DEFAULT NULL COMMENT 'HTTP Method。',
  `request_path_hash` char(64) DEFAULT NULL COMMENT '请求路径哈希，禁止保存完整 token 路径。',
  `client_ip_hash` char(64) DEFAULT NULL COMMENT '客户端 IP 哈希。',
  `client_ip_country` varchar(3) DEFAULT NULL COMMENT 'IP 解析国家/地区。',
  `user_agent_hash` char(64) DEFAULT NULL COMMENT 'User-Agent 哈希。',
  `device_id_hash` char(64) DEFAULT NULL COMMENT '设备ID哈希。',
  `origin_hash` char(64) DEFAULT NULL COMMENT 'Origin Header 哈希。',
  `referer_hash` char(64) DEFAULT NULL COMMENT 'Referer Header 哈希。',
  `risk_score` decimal(10,4) DEFAULT NULL COMMENT '安全风险评分。',
  `evidence_json` json DEFAULT NULL COMMENT '安全证据脱敏摘要，禁止保存 PAN、CVV、JWT、密钥和完整 token。',
  `trace_id` varchar(128) DEFAULT NULL COMMENT '链路追踪ID。',
  `event_time` datetime(3) NOT NULL COMMENT '安全事件发生时间。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_security_event_id` (`security_event_id`),
  KEY `idx_session_time` (`checkout_session_id`,`event_time`),
  KEY `idx_attempt_time` (`checkout_attempt_id`,`event_time`),
  KEY `idx_token_time` (`token_hash`,`event_time`),
  KEY `idx_event_type_time` (`security_event_type`,`event_time`),
  KEY `idx_decision_time` (`security_decision`,`event_time`),
  KEY `idx_ip_time` (`client_ip_hash`,`event_time`)
) ENGINE=InnoDB AUTO_INCREMENT=103 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hosted Checkout 安全事件日志，用于非法请求拦截、风控审计和异常访问排查。';

-- ----------------------------
-- Table structure for payment_checkout_session
-- ----------------------------
DROP TABLE IF EXISTS `payment_checkout_session`;
CREATE TABLE `payment_checkout_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID，使用系统统一主键规则。',
  `checkout_session_id` varchar(64) NOT NULL COMMENT 'Hosted Checkout 会话ID，可返回商户和前端，不等同于交易ID。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户原始订单号，用于商户侧展示和查询。',
  `merchant_request_id` varchar(128) NOT NULL COMMENT '商户本次创建收银台请求唯一标识，建议来自 orderInfo.orderId，用于创建收银台幂等。',
  `request_fingerprint` varchar(128) NOT NULL COMMENT '创建收银台明文业务参数摘要，用于识别同幂等键但请求内容不一致。',
  `payment_action` varchar(32) NOT NULL DEFAULT 'PAYMENT' COMMENT '支付动作，V1 固定 PAYMENT，后续可扩展 AUTHORIZATION。',
  `integration_type` varchar(32) NOT NULL DEFAULT 'HOSTED_CHECKOUT' COMMENT '接入类型，固定 HOSTED_CHECKOUT。',
  `checkout_status` varchar(32) NOT NULL COMMENT '支付状态：PENDING待处理、PROCESSING处理中、SUCCESS成功、FAILED失败。',
  `process_stage` varchar(64) NOT NULL COMMENT '内部处理阶段，如 SESSION_CREATED、WAITING_PAYER、CARD_SUBMITTED、WAITING_3DS、WAITING_CHANNEL、RESULT_RENDERED。',
  `last_status_time` datetime(3) NOT NULL COMMENT '最近一次收银台状态更新时间。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '支付提交后关联的交易生命周期ID；付款人未提交前为空。',
  `root_transaction_id` varchar(64) DEFAULT NULL COMMENT '支付提交后关联的首笔平台开户交易ID；付款人未提交前为空。',
  `latest_transaction_id` varchar(64) DEFAULT NULL COMMENT '最近一次支付尝试关联的平台交易ID；付款人未提交前为空。',
  `transaction_date_time` datetime(3) DEFAULT NULL COMMENT '最近一次关联交易业务时间，用于路由 transaction_* 分表；付款人未提交前为空。',
  `label_currency` char(3) NOT NULL COMMENT '页面展示和商户请求币种，ISO 4217 三位代码。',
  `label_amount` decimal(20,6) NOT NULL COMMENT '页面展示和商户请求金额，主币种单位。',
  `currency_exponent` tinyint NOT NULL COMMENT '币种小数位精度，如 USD=2、JPY=0。',
  `order_subject` varchar(256) DEFAULT NULL COMMENT '订单标题，用于收银台展示。',
  `order_description` varchar(512) DEFAULT NULL COMMENT '订单描述，用于收银台展示。',
  `order_items_json` json DEFAULT NULL COMMENT '订单商品摘要，仅保存展示字段，不保存敏感扩展参数。',
  `allowed_payment_methods_json` json NOT NULL COMMENT '平台根据商户能力配置生成的支付方式和品牌快照，商户创建收银台时不得指定。',
  `selected_payment_method` varchar(32) DEFAULT NULL COMMENT '付款人已选择支付方式，如 BANK_CARD。',
  `selected_payment_brand` varchar(32) DEFAULT NULL COMMENT '付款人卡品牌或支付品牌，如 VISA、MASTERCARD。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '本次会话最终路由渠道，V1 为 MPGS；提交前可为空。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '本次会话最终路由渠道MID配置ID；提交前可为空。',
  `merchant_display_name` varchar(256) DEFAULT NULL COMMENT '收银台展示的商户名称快照。',
  `merchant_logo_url` varchar(1024) DEFAULT NULL COMMENT '收银台展示的商户Logo地址，必须来自平台配置或已校验白名单。',
  `merchant_notify_url` varchar(512) DEFAULT NULL COMMENT '商户异步通知地址明文；禁止完整写入日志。',
  `sub_merchant_info_json` json DEFAULT NULL COMMENT '子商户完整明文 JSON 快照。',
  `payer_info_json` json DEFAULT NULL COMMENT '付款人预填信息明文 JSON 快照。',
  `billing_info_json` json DEFAULT NULL COMMENT '持卡人账单预填信息明文 JSON 快照。',
  `shipping_info_json` json DEFAULT NULL COMMENT '收货信息结构化明文 JSON；生成交易后拆分写入 transaction_shipping_info 明文列。',
  `redirect_url` varchar(512) DEFAULT NULL COMMENT '交易完成后 Form POST 的商户结果页地址明文。',
  `locale` varchar(16) NOT NULL DEFAULT 'en-US' COMMENT '收银台默认语言，如 en-US、zh-CN。',
  `payer_country` varchar(3) DEFAULT NULL COMMENT '商户传入或付款人选择的国家/地区，ISO 3166-1 alpha-3。',
  `payer_email` varchar(64) DEFAULT NULL COMMENT '付款人邮箱明文，用于收银台预填和风险校验，禁止普通日志输出。',
  `payer_email_hash` char(64) DEFAULT NULL COMMENT '付款人邮箱哈希，用于风控和排查。',
  `retry_allowed` tinyint NOT NULL DEFAULT '1' COMMENT '支付失败后是否允许重新支付，0否，1是。',
  `max_attempt_count` int NOT NULL DEFAULT '3' COMMENT '最大支付尝试次数。',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '已创建支付尝试次数。',
  `success_attempt_id` varchar(64) DEFAULT NULL COMMENT '成功支付尝试ID，终态成功后写入。',
  `last_attempt_id` varchar(64) DEFAULT NULL COMMENT '最近一次支付尝试ID。',
  `checkout_domain` varchar(256) NOT NULL COMMENT '生成收银台 URL 使用的域名。',
  `expire_time` datetime(3) NOT NULL COMMENT '收银台会话过期时间。',
  `paid_time` datetime(3) DEFAULT NULL COMMENT '支付成功时间。',
  `cancel_time` datetime(3) DEFAULT NULL COMMENT '取消时间。',
  `blocked_time` datetime(3) DEFAULT NULL COMMENT '安全拦截时间。',
  `block_reason_code` varchar(64) DEFAULT NULL COMMENT '安全拦截原因码，前端只展示模糊文案。',
  `last_open_time` datetime(3) DEFAULT NULL COMMENT '最近一次打开收银台时间。',
  `last_submit_time` datetime(3) DEFAULT NULL COMMENT '最近一次提交支付时间。',
  `next_channel_match_time` datetime(3) DEFAULT NULL COMMENT '处理中状态下一次渠道查单补偿时间。',
  `result_snapshot` json DEFAULT NULL COMMENT '创建收银台幂等响应快照，不保存 opaqueToken 明文；重复请求可重新签发 token 后组装 URL。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，用于状态机 CAS 更新。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识，资金和审计相关记录原则上不做业务删除。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_checkout_session_id` (`checkout_session_id`),
  UNIQUE KEY `uk_merchant_request` (`merchant_id`,`merchant_request_id`),
  KEY `idx_merchant_order_time` (`merchant_id`,`merchant_order_no`,`create_time`),
  KEY `idx_status_expire` (`checkout_status`,`expire_time`),
  KEY `idx_latest_transaction_time` (`latest_transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_channel_match_next` (`checkout_status`,`next_channel_match_time`),
  KEY `idx_checkout_expire_scan` (`checkout_status`,`process_stage`,`last_submit_time`,`deleted`,`expire_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hosted Checkout 会话主表，保存收银台订单意向、展示快照、状态和交易关联，不保存卡敏感信息。';

-- ----------------------------
-- Table structure for payment_checkout_token
-- ----------------------------
DROP TABLE IF EXISTS `payment_checkout_token`;
CREATE TABLE `payment_checkout_token` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID，使用系统统一主键规则。',
  `checkout_token_id` varchar(64) NOT NULL COMMENT '收银台 URL 令牌记录ID，不返回付款人。',
  `checkout_session_id` varchar(64) NOT NULL COMMENT 'Hosted Checkout 会话ID。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号，冗余用于审计和清理。',
  `token_hash` char(64) NOT NULL COMMENT 'opaqueToken 的 HMAC-SHA256 摘要，使用平台 pepper，不保存 token 明文。',
  `token_hash_alg` varchar(32) NOT NULL DEFAULT 'HMAC_SHA256' COMMENT '令牌摘要算法。',
  `token_key_version` varchar(32) NOT NULL COMMENT '令牌摘要 pepper 或密钥版本，用于后续轮换。',
  `token_status` varchar(32) NOT NULL COMMENT '令牌状态：ACTIVE、REVOKED、EXPIRED。',
  `issue_reason` varchar(32) NOT NULL COMMENT '签发原因：CREATE、IDEMPOTENT_REISSUE、ROTATE、RISK_REISSUE。',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '令牌可选失效时间；NULL 表示未撤销前允许持续查询结果，不代表允许继续支付。',
  `first_used_time` datetime(3) DEFAULT NULL COMMENT '首次被付款人打开或查询时间。',
  `last_used_time` datetime(3) DEFAULT NULL COMMENT '最近一次使用时间。',
  `use_count` int NOT NULL DEFAULT '0' COMMENT '累计使用次数，用于异常访问识别。',
  `last_client_ip_hash` char(64) DEFAULT NULL COMMENT '最近一次使用来源 IP 哈希。',
  `last_user_agent_hash` char(64) DEFAULT NULL COMMENT '最近一次使用 User-Agent 哈希。',
  `revoked_time` datetime(3) DEFAULT NULL COMMENT '令牌吊销时间。',
  `revoke_reason_code` varchar(64) DEFAULT NULL COMMENT '令牌吊销原因码。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识，审计记录原则上不做业务删除。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_checkout_token_id` (`checkout_token_id`),
  UNIQUE KEY `uk_token_hash` (`token_hash`),
  KEY `idx_session_status` (`checkout_session_id`,`token_status`),
  KEY `idx_merchant_create` (`merchant_id`,`create_time`),
  KEY `idx_status_expire` (`token_status`,`expire_time`)
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hosted Checkout URL 令牌表，支持不保存 token 明文的幂等重试、令牌轮换和吊销。';

-- ----------------------------
-- Table structure for risk_aml_card
-- ----------------------------
DROP TABLE IF EXISTS `risk_aml_card`;
CREATE TABLE `risk_aml_card` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，仅商户范围生效时必填',
  `match_value_masked` varchar(255) NOT NULL COMMENT '匹配值脱敏展示',
  `match_value_hash` varchar(128) DEFAULT NULL COMMENT '匹配值哈希，卡号等敏感信息禁止保存明文',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '匹配值密文，仅编辑授权时解密回显',
  `match_value_start` varchar(128) DEFAULT NULL COMMENT '区间起始值，用于BIN或IP等区间类场景',
  `match_value_end` varchar(128) DEFAULT NULL COMMENT '区间结束值，用于BIN或IP等区间类场景',
  `match_value_start_number` decimal(39,0) DEFAULT NULL COMMENT '区间起始数值，BIN和IP交易检索使用',
  `match_value_end_number` decimal(39,0) DEFAULT NULL COMMENT '区间结束数值，BIN和IP交易检索使用',
  `card_brand` varchar(64) DEFAULT NULL COMMENT '卡品牌，复用 card_brand 字典',
  `country_alpha2` varchar(2) DEFAULT NULL COMMENT '国家或地区 Alpha-2 编码',
  `country_alpha3` varchar(3) DEFAULT NULL COMMENT '国家或地区 Alpha-3 编码',
  `country_numeric` varchar(3) DEFAULT NULL COMMENT '国家或地区数字编码',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_risk_aml_card_hash` (`match_value_hash`,`status`,`deleted`),
  KEY `idx_risk_aml_card_merchant` (`merchant_scope`,`merchant_id`,`status`,`deleted`),
  KEY `idx_risk_aml_card_time` (`create_time`),
  KEY `idx_risk_aml_card_uniq_lookup` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_risk_aml_card_range_num` (`match_value_start_number`,`match_value_end_number`,`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡号卡指纹AML名单表';

-- ----------------------------
-- Table structure for risk_aml_card_bin
-- ----------------------------
DROP TABLE IF EXISTS `risk_aml_card_bin`;
CREATE TABLE `risk_aml_card_bin` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，仅商户范围生效时必填',
  `match_value_masked` varchar(255) NOT NULL COMMENT '匹配值脱敏展示',
  `match_value_hash` varchar(128) DEFAULT NULL COMMENT '匹配值哈希，卡号等敏感信息禁止保存明文',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '匹配值密文，仅编辑授权时解密回显',
  `match_value_start` varchar(11) DEFAULT NULL COMMENT '卡BIN起始值，统一右补0至11位',
  `match_value_end` varchar(11) DEFAULT NULL COMMENT '卡BIN截止值，统一右补9至11位',
  `match_value_start_number` decimal(39,0) DEFAULT NULL COMMENT '区间起始数值，BIN和IP交易检索使用',
  `match_value_end_number` decimal(39,0) DEFAULT NULL COMMENT '区间结束数值，BIN和IP交易检索使用',
  `card_brand` varchar(64) DEFAULT NULL COMMENT '卡品牌，复用 card_brand 字典',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_risk_aml_card_hash` (`match_value_hash`,`status`,`deleted`),
  KEY `idx_risk_aml_card_merchant` (`merchant_scope`,`merchant_id`,`status`,`deleted`),
  KEY `idx_risk_aml_card_time` (`create_time`),
  KEY `idx_risk_aml_card_bin_uniq_lookup` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_risk_aml_card_bin_range_num` (`match_value_start_number`,`match_value_end_number`,`status`,`deleted`),
  KEY `idx_risk_aml_card_bin_bin_lookup` (`status`,`deleted`,`merchant_scope`,`merchant_id`,`match_value_start_number`,`match_value_end_number`,`effective_time`,`expire_time`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡BIN区间AML名单表';

-- ----------------------------
-- Table structure for risk_aml_cardholder_name
-- ----------------------------
DROP TABLE IF EXISTS `risk_aml_cardholder_name`;
CREATE TABLE `risk_aml_cardholder_name` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，仅商户范围生效时必填',
  `match_value_masked` varchar(255) NOT NULL COMMENT '匹配值脱敏展示',
  `match_value_hash` varchar(128) DEFAULT NULL COMMENT '匹配值哈希，卡号等敏感信息禁止保存明文',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '匹配值密文，仅编辑授权时解密回显',
  `match_value_start` varchar(128) DEFAULT NULL COMMENT '区间起始值，用于BIN或IP等区间类场景',
  `match_value_end` varchar(128) DEFAULT NULL COMMENT '区间结束值，用于BIN或IP等区间类场景',
  `match_value_start_number` decimal(39,0) DEFAULT NULL COMMENT '区间起始数值，BIN和IP交易检索使用',
  `match_value_end_number` decimal(39,0) DEFAULT NULL COMMENT '区间结束数值，BIN和IP交易检索使用',
  `card_brand` varchar(64) DEFAULT NULL COMMENT '卡品牌，复用 card_brand 字典',
  `country_alpha2` varchar(2) DEFAULT NULL COMMENT '国家或地区 Alpha-2 编码',
  `country_alpha3` varchar(3) DEFAULT NULL COMMENT '国家或地区 Alpha-3 编码',
  `country_numeric` varchar(3) DEFAULT NULL COMMENT '国家或地区数字编码',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_risk_aml_card_hash` (`match_value_hash`,`status`,`deleted`),
  KEY `idx_risk_aml_card_merchant` (`merchant_scope`,`merchant_id`,`status`,`deleted`),
  KEY `idx_risk_aml_card_time` (`create_time`),
  KEY `idx_risk_aml_cardholder_name_uniq_lookup` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_risk_aml_cardholder_name_range_num` (`match_value_start_number`,`match_value_end_number`,`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='持卡人姓名AML名单表';

-- ----------------------------
-- Table structure for risk_aml_country
-- ----------------------------
DROP TABLE IF EXISTS `risk_aml_country`;
CREATE TABLE `risk_aml_country` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，仅商户范围生效时必填',
  `match_value_masked` varchar(255) NOT NULL COMMENT '匹配值脱敏展示',
  `match_value_hash` varchar(128) DEFAULT NULL COMMENT '匹配值哈希，卡号等敏感信息禁止保存明文',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '匹配值密文，仅编辑授权时解密回显',
  `match_value_start` varchar(128) DEFAULT NULL COMMENT '区间起始值，用于BIN或IP等区间类场景',
  `match_value_end` varchar(128) DEFAULT NULL COMMENT '区间结束值，用于BIN或IP等区间类场景',
  `match_value_start_number` decimal(39,0) DEFAULT NULL COMMENT '区间起始数值，BIN和IP交易检索使用',
  `match_value_end_number` decimal(39,0) DEFAULT NULL COMMENT '区间结束数值，BIN和IP交易检索使用',
  `card_brand` varchar(64) DEFAULT NULL COMMENT '卡品牌，复用 card_brand 字典',
  `country_alpha2` varchar(2) DEFAULT NULL COMMENT '国家或地区 Alpha-2 编码',
  `country_alpha3` varchar(3) DEFAULT NULL COMMENT '国家或地区 Alpha-3 编码',
  `country_numeric` varchar(3) DEFAULT NULL COMMENT '国家或地区数字编码',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_risk_aml_card_hash` (`match_value_hash`,`status`,`deleted`),
  KEY `idx_risk_aml_card_merchant` (`merchant_scope`,`merchant_id`,`status`,`deleted`),
  KEY `idx_risk_aml_card_time` (`create_time`),
  KEY `idx_risk_aml_country_uniq_lookup` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_risk_aml_country_range_num` (`match_value_start_number`,`match_value_end_number`,`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='国家地区AML名单表';

-- ----------------------------
-- Table structure for risk_aml_email
-- ----------------------------
DROP TABLE IF EXISTS `risk_aml_email`;
CREATE TABLE `risk_aml_email` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '邮箱地址脱敏值或邮箱域名展示值',
  `match_value_hash` varchar(128) NOT NULL COMMENT '邮箱值归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '完整邮箱地址密文，域名类记录为空',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aml_email_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_aml_email_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_aml_email_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_aml_email_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮箱域名AML名单表';

-- ----------------------------
-- Table structure for risk_aml_enterprise
-- ----------------------------
DROP TABLE IF EXISTS `risk_aml_enterprise`;
CREATE TABLE `risk_aml_enterprise` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '企业名称脱敏展示值',
  `match_value_hash` varchar(128) NOT NULL COMMENT '企业名称归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '企业名称密文，仅编辑授权时解密回显',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aml_enterprise_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_aml_enterprise_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_aml_enterprise_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_aml_enterprise_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AML企业名单表';

-- ----------------------------
-- Table structure for risk_aml_ip
-- ----------------------------
DROP TABLE IF EXISTS `risk_aml_ip`;
CREATE TABLE `risk_aml_ip` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(128) NOT NULL COMMENT 'IP地址或区间展示值',
  `match_value_hash` varchar(128) NOT NULL COMMENT 'IP区间归一化哈希，用于重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT 'IP地址默认不保存敏感密文',
  `match_value_start` varchar(128) NOT NULL COMMENT '起始IP',
  `match_value_end` varchar(128) NOT NULL COMMENT '截止IP',
  `match_value_start_number` decimal(39,0) NOT NULL COMMENT '起始IP数值，交易检索使用',
  `match_value_end_number` decimal(39,0) NOT NULL COMMENT '截止IP数值，交易检索使用',
  `ip_version` varchar(8) NOT NULL COMMENT 'IP版本：IPV4、IPV6',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aml_ip_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_risk_aml_ip_uniq_lookup` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_risk_aml_ip_range_num` (`match_value_start_number`,`match_value_end_number`,`status`,`deleted`),
  KEY `idx_aml_ip_trade_lookup` (`ip_version`,`match_value_start_number`,`match_value_end_number`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_aml_ip_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_aml_ip_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IP地址区间AML名单表';

-- ----------------------------
-- Table structure for risk_aml_legal_person
-- ----------------------------
DROP TABLE IF EXISTS `risk_aml_legal_person`;
CREATE TABLE `risk_aml_legal_person` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '法人名称脱敏展示值',
  `match_value_hash` varchar(128) NOT NULL COMMENT '法人名称归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '法人名称密文，仅编辑授权时解密回显',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aml_legal_person_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_aml_legal_person_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_aml_legal_person_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_aml_legal_person_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AML法人名单表';

-- ----------------------------
-- Table structure for risk_aml_merchant_billing_address
-- ----------------------------
DROP TABLE IF EXISTS `risk_aml_merchant_billing_address`;
CREATE TABLE `risk_aml_merchant_billing_address` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '商户账单地址明文展示值',
  `match_value_hash` varchar(128) NOT NULL COMMENT '商户账单地址归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '商户账单地址默认不加密存储',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aml_merchant_billing_address_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_aml_merchant_billing_address_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_aml_merchant_billing_address_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_aml_merchant_billing_address_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AML商户账单地址名单表';

-- ----------------------------
-- Table structure for risk_aml_phone
-- ----------------------------
DROP TABLE IF EXISTS `risk_aml_phone`;
CREATE TABLE `risk_aml_phone` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，仅商户范围生效时必填',
  `match_value_masked` varchar(255) NOT NULL COMMENT '匹配值脱敏展示',
  `match_value_hash` varchar(128) DEFAULT NULL COMMENT '匹配值哈希，卡号等敏感信息禁止保存明文',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '匹配值密文，仅编辑授权时解密回显',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_risk_aml_card_hash` (`match_value_hash`,`status`,`deleted`),
  KEY `idx_risk_aml_card_merchant` (`merchant_scope`,`merchant_id`,`status`,`deleted`),
  KEY `idx_risk_aml_card_time` (`create_time`),
  KEY `idx_risk_aml_phone_uniq_lookup` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_risk_aml_phone_phone_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='手机号AML名单表';

-- ----------------------------
-- Table structure for risk_aml_source_url
-- ----------------------------
DROP TABLE IF EXISTS `risk_aml_source_url`;
CREATE TABLE `risk_aml_source_url` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，仅商户范围生效时必填',
  `match_value_masked` varchar(255) NOT NULL COMMENT '匹配值脱敏展示',
  `match_value_hash` varchar(128) DEFAULT NULL COMMENT '匹配值哈希，卡号等敏感信息禁止保存明文',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '匹配值密文，仅编辑授权时解密回显',
  `source_host` varchar(255) DEFAULT NULL COMMENT '来源网址Host，交易和商户进件按全局Host匹配',
  `match_value_start` varchar(128) DEFAULT NULL COMMENT '区间起始值，用于BIN或IP等区间类场景',
  `match_value_end` varchar(128) DEFAULT NULL COMMENT '区间结束值，用于BIN或IP等区间类场景',
  `match_value_start_number` decimal(39,0) DEFAULT NULL COMMENT '区间起始数值，BIN和IP交易检索使用',
  `match_value_end_number` decimal(39,0) DEFAULT NULL COMMENT '区间结束数值，BIN和IP交易检索使用',
  `card_brand` varchar(64) DEFAULT NULL COMMENT '卡品牌，复用 card_brand 字典',
  `country_alpha2` varchar(2) DEFAULT NULL COMMENT '国家或地区 Alpha-2 编码',
  `country_alpha3` varchar(3) DEFAULT NULL COMMENT '国家或地区 Alpha-3 编码',
  `country_numeric` varchar(3) DEFAULT NULL COMMENT '国家或地区数字编码',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aml_source_url_host_deleted` (`source_host`,`deleted`),
  KEY `idx_risk_aml_card_hash` (`match_value_hash`,`status`,`deleted`),
  KEY `idx_risk_aml_card_merchant` (`merchant_scope`,`merchant_id`,`status`,`deleted`),
  KEY `idx_risk_aml_card_time` (`create_time`),
  KEY `idx_risk_aml_source_url_uniq_lookup` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_risk_aml_source_url_range_num` (`match_value_start_number`,`match_value_end_number`,`status`,`deleted`),
  KEY `idx_aml_source_url_host_lookup` (`source_host`,`status`,`deleted`,`effective_time`,`expire_time`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='来源网址AML名单表';

-- ----------------------------
-- Table structure for risk_black_billing_address
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_billing_address`;
CREATE TABLE `risk_black_billing_address` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '账单地址明文展示值',
  `match_value_hash` varchar(128) NOT NULL COMMENT '账单地址归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '账单地址明文展示，默认不加密存储',
  `risk_level` varchar(32) NOT NULL DEFAULT 'HIGH' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_billing_address_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_black_billing_address_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_black_billing_address_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_black_billing_address_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账单地址黑名单表';

-- ----------------------------
-- Table structure for risk_black_billing_country
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_billing_country`;
CREATE TABLE `risk_black_billing_country` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(3) NOT NULL COMMENT '国家或地区 Alpha-3 编码展示值',
  `match_value_hash` varchar(128) NOT NULL COMMENT '国家或地区 Alpha-3 编码哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '预留密文字段，国家或地区默认不加密存储',
  `country_alpha2` varchar(2) DEFAULT NULL COMMENT '国家或地区 Alpha-2 编码，仅用于管理端回显',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家或地区 Alpha-3 编码，交易匹配主字段',
  `risk_level` varchar(32) NOT NULL DEFAULT 'HIGH' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_billing_country_scope_country_deleted` (`merchant_scope`,`merchant_id`,`country_alpha3`,`deleted`),
  KEY `idx_black_billing_country_trade_lookup` (`country_alpha3`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_black_billing_country_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_black_billing_country_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=58 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账单国家地区黑名单表';

-- ----------------------------
-- Table structure for risk_black_billing_zip
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_billing_zip`;
CREATE TABLE `risk_black_billing_zip` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(32) NOT NULL COMMENT '账单邮编展示值，按大写和单空格规范化',
  `match_value_hash` varchar(128) NOT NULL COMMENT '账单邮编检索哈希，按去除空格和短横线后的值生成',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '预留密文字段，账单邮编默认不加密存储',
  `risk_level` varchar(32) NOT NULL DEFAULT 'HIGH' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_billing_zip_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_black_billing_zip_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_black_billing_zip_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_black_billing_zip_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账单邮编黑名单表';

-- ----------------------------
-- Table structure for risk_black_card_bin
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_card_bin`;
CREATE TABLE `risk_black_card_bin` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '匹配值脱敏展示',
  `match_value_hash` varchar(128) NOT NULL COMMENT 'BIN区间归一化哈希，用于重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '匹配值密文，仅编辑授权时解密回显',
  `match_value_start` varchar(11) DEFAULT NULL COMMENT '卡BIN起始值，统一右补0至11位',
  `match_value_end` varchar(11) DEFAULT NULL COMMENT '卡BIN截止值，统一右补9至11位',
  `match_value_start_number` decimal(39,0) DEFAULT NULL COMMENT '区间起始数值，BIN和IP交易检索使用',
  `match_value_end_number` decimal(39,0) DEFAULT NULL COMMENT '区间结束数值，BIN和IP交易检索使用',
  `card_brand` varchar(64) DEFAULT NULL COMMENT '卡品牌，复用 card_brand 字典',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_card_bin_scope_range_deleted` (`merchant_scope`,`merchant_id`,`match_value_start_number`,`match_value_end_number`,`deleted`),
  KEY `idx_risk_aml_card_hash` (`match_value_hash`,`status`,`deleted`),
  KEY `idx_risk_aml_card_merchant` (`merchant_scope`,`merchant_id`,`status`,`deleted`),
  KEY `idx_risk_aml_card_time` (`create_time`),
  KEY `idx_risk_black_card_bin_range_num` (`match_value_start_number`,`match_value_end_number`,`status`,`deleted`),
  KEY `idx_risk_black_card_bin_bin_lookup` (`status`,`deleted`,`merchant_scope`,`merchant_id`,`match_value_start_number`,`match_value_end_number`,`effective_time`,`expire_time`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡BIN区间黑名单表';

-- ----------------------------
-- Table structure for risk_black_card_fingerprint
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_card_fingerprint`;
CREATE TABLE `risk_black_card_fingerprint` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '匹配值脱敏展示',
  `match_value_hash` varchar(128) NOT NULL COMMENT '卡指纹归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '匹配值密文，仅编辑授权时解密回显',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_card_fingerprint_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_risk_aml_card_hash` (`match_value_hash`,`status`,`deleted`),
  KEY `idx_risk_aml_card_merchant` (`merchant_scope`,`merchant_id`,`status`,`deleted`),
  KEY `idx_risk_aml_card_time` (`create_time`),
  KEY `idx_black_card_fingerprint_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_black_card_fingerprint_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_black_card_fingerprint_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡指纹黑名单表';

-- ----------------------------
-- Table structure for risk_black_card_no
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_card_no`;
CREATE TABLE `risk_black_card_no` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '匹配值脱敏展示',
  `match_value_hash` varchar(128) NOT NULL COMMENT '卡号归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '匹配值密文，仅编辑授权时解密回显',
  `card_brand` varchar(64) DEFAULT NULL COMMENT '卡品牌，复用 card_brand 字典',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_card_no_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_black_card_no_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_black_card_no_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_black_card_no_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡号黑名单表';

-- ----------------------------
-- Table structure for risk_black_cardholder_name
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_cardholder_name`;
CREATE TABLE `risk_black_cardholder_name` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '匹配值脱敏展示',
  `match_value_hash` varchar(128) NOT NULL COMMENT '持卡人姓名归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '匹配值密文，仅编辑授权时解密回显',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_cardholder_name_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_risk_aml_card_hash` (`match_value_hash`,`status`,`deleted`),
  KEY `idx_risk_aml_card_merchant` (`merchant_scope`,`merchant_id`,`status`,`deleted`),
  KEY `idx_risk_aml_card_time` (`create_time`),
  KEY `idx_black_cardholder_name_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_black_cardholder_name_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_black_cardholder_name_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='持卡人姓名黑名单表';

-- ----------------------------
-- Table structure for risk_black_device_fingerprint
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_device_fingerprint`;
CREATE TABLE `risk_black_device_fingerprint` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '匹配值脱敏展示',
  `match_value_hash` varchar(128) NOT NULL COMMENT '设备指纹归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '匹配值密文，仅编辑授权时解密回显',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_device_fingerprint_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_risk_aml_card_hash` (`match_value_hash`,`status`,`deleted`),
  KEY `idx_risk_aml_card_merchant` (`merchant_scope`,`merchant_id`,`status`,`deleted`),
  KEY `idx_risk_aml_card_time` (`create_time`),
  KEY `idx_black_device_fingerprint_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_black_device_fingerprint_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_black_device_fingerprint_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备指纹黑名单表';

-- ----------------------------
-- Table structure for risk_black_email
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_email`;
CREATE TABLE `risk_black_email` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '邮箱地址脱敏展示值，禁止保存完整邮箱明文',
  `match_value_hash` varchar(128) NOT NULL COMMENT '邮箱地址归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) NOT NULL COMMENT '邮箱地址密文，仅编辑授权时解密回显',
  `risk_level` varchar(32) NOT NULL DEFAULT 'HIGH' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_email_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_black_email_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_black_email_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_black_email_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮箱地址黑名单表';

-- ----------------------------
-- Table structure for risk_black_email_domain
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_email_domain`;
CREATE TABLE `risk_black_email_domain` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '邮箱域名展示值',
  `match_value_hash` varchar(128) NOT NULL COMMENT '邮箱值归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '邮箱域名不属于敏感明文，默认不加密存储',
  `risk_level` varchar(32) NOT NULL DEFAULT 'HIGH' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_email_domain_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_black_email_domain_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_black_email_domain_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_black_email_domain_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮箱域名黑名单表';

-- ----------------------------
-- Table structure for risk_black_email_username
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_email_username`;
CREATE TABLE `risk_black_email_username` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '邮箱用户名脱敏展示值，禁止保存完整明文',
  `match_value_hash` varchar(128) NOT NULL COMMENT '邮箱值归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) NOT NULL COMMENT '邮箱用户名密文，仅编辑授权时解密回显',
  `risk_level` varchar(32) NOT NULL DEFAULT 'HIGH' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_email_username_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_black_email_username_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_black_email_username_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_black_email_username_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮箱用户名黑名单表';

-- ----------------------------
-- Table structure for risk_black_ip
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_ip`;
CREATE TABLE `risk_black_ip` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，仅商户范围生效时必填',
  `match_value_masked` varchar(255) NOT NULL COMMENT '匹配值脱敏展示',
  `match_value_hash` varchar(128) DEFAULT NULL COMMENT '匹配值哈希，卡号等敏感信息禁止保存明文',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '匹配值密文，仅编辑授权时解密回显',
  `match_value_start` varchar(128) DEFAULT NULL COMMENT '区间起始值，用于BIN或IP等区间类场景',
  `match_value_end` varchar(128) DEFAULT NULL COMMENT '区间结束值，用于BIN或IP等区间类场景',
  `match_value_start_number` decimal(39,0) DEFAULT NULL COMMENT '区间起始数值，BIN和IP交易检索使用',
  `match_value_end_number` decimal(39,0) DEFAULT NULL COMMENT '区间结束数值，BIN和IP交易检索使用',
  `ip_version` varchar(8) DEFAULT NULL COMMENT 'IP版本：IPV4、IPV6',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_ip_scope_range_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_black_ip_trade_lookup` (`ip_version`,`match_value_start_number`,`match_value_end_number`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_black_ip_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_black_ip_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IP地址区间黑名单表';

-- ----------------------------
-- Table structure for risk_black_issuer_country
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_issuer_country`;
CREATE TABLE `risk_black_issuer_country` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(3) NOT NULL COMMENT '国家或地区 Alpha-3 编码展示值',
  `match_value_hash` varchar(128) NOT NULL COMMENT '国家或地区 Alpha-3 编码哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '预留密文字段，国家或地区默认不加密存储',
  `country_alpha2` varchar(2) DEFAULT NULL COMMENT '国家或地区 Alpha-2 编码，仅用于管理端回显',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家或地区 Alpha-3 编码，交易匹配主字段',
  `risk_level` varchar(32) NOT NULL DEFAULT 'HIGH' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_issuer_country_scope_country_deleted` (`merchant_scope`,`merchant_id`,`country_alpha3`,`deleted`),
  KEY `idx_black_issuer_country_trade_lookup` (`country_alpha3`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_black_issuer_country_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_black_issuer_country_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发卡行国家地区黑名单表';

-- ----------------------------
-- Table structure for risk_black_phone
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_phone`;
CREATE TABLE `risk_black_phone` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，仅商户范围生效时必填',
  `match_value_masked` varchar(255) NOT NULL COMMENT '匹配值脱敏展示',
  `match_value_hash` varchar(128) DEFAULT NULL COMMENT '匹配值哈希，卡号等敏感信息禁止保存明文',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '匹配值密文，仅编辑授权时解密回显',
  `risk_level` varchar(32) NOT NULL DEFAULT 'CRITICAL' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_phone_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_black_phone_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_black_phone_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_black_phone_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='电话号码黑名单表';

-- ----------------------------
-- Table structure for risk_black_region
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_region`;
CREATE TABLE `risk_black_region` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `region_match_level` varchar(32) NOT NULL COMMENT '区域匹配级别：COUNTRY国家、STATE州省、CITY城市',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家或地区 Alpha-3 编码',
  `state_province_name` varchar(128) NOT NULL DEFAULT '' COMMENT '州省名称',
  `city_name` varchar(128) NOT NULL DEFAULT '' COMMENT '城市名称',
  `risk_level` varchar(32) NOT NULL DEFAULT 'HIGH' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_region_scope_area` (`merchant_scope`,`merchant_id`,`region_match_level`,`country_alpha3`,`state_province_name`,`city_name`,`deleted`),
  KEY `idx_black_region_level` (`region_match_level`,`status`,`deleted`),
  KEY `idx_black_region_trade_lookup` (`merchant_scope`,`merchant_id`,`region_match_level`,`country_alpha3`,`state_province_name`,`city_name`,`status`,`deleted`,`effective_time`,`expire_time`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='高风险区域黑名单表';

-- ----------------------------
-- Table structure for risk_black_shipping_address
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_shipping_address`;
CREATE TABLE `risk_black_shipping_address` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '收货地址明文展示值',
  `match_value_hash` varchar(128) NOT NULL COMMENT '收货地址归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '收货地址明文展示，默认不加密存储',
  `risk_level` varchar(32) NOT NULL DEFAULT 'HIGH' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_shipping_address_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_black_shipping_address_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_black_shipping_address_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_black_shipping_address_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货地址黑名单表';

-- ----------------------------
-- Table structure for risk_black_shipping_country
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_shipping_country`;
CREATE TABLE `risk_black_shipping_country` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(3) NOT NULL COMMENT '国家或地区 Alpha-3 编码展示值',
  `match_value_hash` varchar(128) NOT NULL COMMENT '国家或地区 Alpha-3 编码哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '预留密文字段，国家或地区默认不加密存储',
  `country_alpha2` varchar(2) DEFAULT NULL COMMENT '国家或地区 Alpha-2 编码，仅用于管理端回显',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家或地区 Alpha-3 编码，交易匹配主字段',
  `risk_level` varchar(32) NOT NULL DEFAULT 'HIGH' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_shipping_country_scope_country_deleted` (`merchant_scope`,`merchant_id`,`country_alpha3`,`deleted`),
  KEY `idx_black_shipping_country_trade_lookup` (`country_alpha3`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_black_shipping_country_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_black_shipping_country_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货国家地区黑名单表';

-- ----------------------------
-- Table structure for risk_black_shipping_zip
-- ----------------------------
DROP TABLE IF EXISTS `risk_black_shipping_zip`;
CREATE TABLE `risk_black_shipping_zip` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(32) NOT NULL COMMENT '收货邮编展示值，按大写和单空格规范化',
  `match_value_hash` varchar(128) NOT NULL COMMENT '收货邮编检索哈希，按去除空格和短横线后的值生成',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '预留密文字段，收货邮编默认不加密存储',
  `risk_level` varchar(32) NOT NULL DEFAULT 'HIGH' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REJECT' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_black_shipping_zip_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_black_shipping_zip_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_black_shipping_zip_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_black_shipping_zip_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货邮编黑名单表';

-- ----------------------------
-- Table structure for risk_cache_invalidation_outbox
-- ----------------------------
DROP TABLE IF EXISTS `risk_cache_invalidation_outbox`;
CREATE TABLE `risk_cache_invalidation_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_id` varchar(64) NOT NULL COMMENT '失效事件唯一编号',
  `namespace` varchar(64) NOT NULL COMMENT '缓存代际命名空间',
  `publication_token` varchar(128) NOT NULL COMMENT 'Redis 发布门禁持有者 token',
  `generation` varchar(128) NOT NULL COMMENT '待切换的新缓存代际',
  `event_status` varchar(16) NOT NULL DEFAULT 'INIT' COMMENT '状态：INIT、FAILED、SENT',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '已重试次数',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下次重试时间',
  `published_time` datetime(3) DEFAULT NULL COMMENT '发布成功时间',
  `failure_reason` varchar(512) DEFAULT NULL COMMENT '最近一次失败原因摘要',
  `version` int NOT NULL DEFAULT '0' COMMENT 'CAS 版本号',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_risk_cache_invalidation_event` (`event_id`),
  KEY `idx_risk_cache_invalidation_due` (`event_status`,`next_retry_time`,`create_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控规则缓存失效事件表';

-- ----------------------------
-- Table structure for risk_config_change_log
-- ----------------------------
DROP TABLE IF EXISTS `risk_config_change_log`;
CREATE TABLE `risk_config_change_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `module_type` varchar(32) NOT NULL COMMENT '模块类型：AML、BLACK、WHITE、RULE、TRADE_BLACK',
  `function_code` varchar(64) NOT NULL COMMENT '功能编码，对应管理端风险功能定义',
  `business_id` bigint DEFAULT NULL COMMENT '业务记录ID',
  `operation_type` varchar(32) NOT NULL COMMENT '操作类型：CREATE、UPDATE、DELETE、STATUS、IMPORT、BATCH',
  `before_snapshot` json DEFAULT NULL COMMENT '变更前快照，敏感字段必须脱敏',
  `after_snapshot` json DEFAULT NULL COMMENT '变更后快照，敏感字段必须脱敏',
  `operator` varchar(64) DEFAULT NULL COMMENT '操作人',
  `operation_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_risk_change_function_time` (`module_type`,`function_code`,`operation_time`),
  KEY `idx_risk_change_business` (`module_type`,`function_code`,`business_id`)
) ENGINE=InnoDB AUTO_INCREMENT=229 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控配置变更日志表';

-- ----------------------------
-- Table structure for risk_evaluation_hit_detail
-- ----------------------------
DROP TABLE IF EXISTS `risk_evaluation_hit_detail`;
CREATE TABLE `risk_evaluation_hit_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `risk_record_no` varchar(64) NOT NULL COMMENT '风控记录号',
  `module_type` varchar(32) NOT NULL COMMENT '命中模块类型：AML、BLACK、WHITE、RULE',
  `function_code` varchar(64) NOT NULL COMMENT '命中功能编码',
  `function_name` varchar(128) DEFAULT NULL COMMENT '命中功能名称',
  `rule_id` bigint DEFAULT NULL COMMENT '命中名单或规则ID',
  `hit_element` varchar(64) DEFAULT NULL COMMENT '命中元素类型',
  `hit_value_masked` varchar(255) DEFAULT NULL COMMENT '命中值脱敏展示',
  `risk_level` varchar(32) DEFAULT NULL COMMENT '风险等级',
  `decision_result` varchar(32) NOT NULL COMMENT '本项决策结果',
  `decision_reason` varchar(500) DEFAULT NULL COMMENT '本项决策说明',
  `decision_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '本项决策时间',
  `time_window_seconds` int DEFAULT NULL COMMENT '频率规则统计窗口秒数',
  `threshold_count` int DEFAULT NULL COMMENT '频率规则阈值次数',
  `elements_json` json DEFAULT NULL COMMENT '规则统计元素配置快照',
  `current_count` bigint DEFAULT NULL COMMENT '频率规则当前计数',
  `stage_code` varchar(64) DEFAULT NULL COMMENT '风控执行阶段编码',
  `stage_name` varchar(128) DEFAULT NULL COMMENT '风控执行阶段名称',
  `stage_order` int DEFAULT NULL COMMENT '风控执行阶段顺序',
  `match_result` varchar(32) DEFAULT NULL COMMENT '匹配结果：HIT、MISS、PASS、SKIPPED',
  `decision_effect` varchar(32) DEFAULT NULL COMMENT '当前明细对最终决策的影响：ALLOW、BLOCK、REVIEW、CHALLENGE、NONE',
  PRIMARY KEY (`id`),
  KEY `idx_risk_hit_record` (`risk_record_no`),
  KEY `idx_risk_hit_function_time` (`module_type`,`function_code`,`decision_time`),
  KEY `idx_risk_hit_record_stage` (`risk_record_no`,`stage_order`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=15823 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控评估命中明细表';

-- ----------------------------
-- Table structure for risk_evaluation_record
-- ----------------------------
DROP TABLE IF EXISTS `risk_evaluation_record`;
CREATE TABLE `risk_evaluation_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `risk_record_no` varchar(64) NOT NULL COMMENT '风控记录号',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号',
  `merchant_name` varchar(128) DEFAULT NULL COMMENT '商户名称',
  `merchant_order_no` varchar(64) DEFAULT NULL COMMENT '商户订单号',
  `payment_order_no` varchar(64) DEFAULT NULL COMMENT '平台支付订单号',
  `transaction_amount` decimal(18,6) DEFAULT NULL COMMENT '交易金额',
  `transaction_currency` varchar(3) DEFAULT NULL COMMENT '交易币种，ISO 4217 Alpha-3',
  `risk_level` varchar(32) DEFAULT NULL COMMENT '风险等级：LOW、MEDIUM、HIGH、CRITICAL',
  `decision_result` varchar(32) NOT NULL DEFAULT 'REVIEW' COMMENT '决策结果：PASS、REJECT、REVIEW',
  `decision_reason` varchar(500) DEFAULT NULL COMMENT '决策说明',
  `hit_count` int NOT NULL DEFAULT '0' COMMENT '命中规则数量',
  `evaluation_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '风控决策时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_risk_record_no` (`risk_record_no`),
  KEY `idx_risk_eval_order` (`merchant_order_no`,`payment_order_no`),
  KEY `idx_risk_eval_payment_time` (`payment_order_no`,`evaluation_time`,`risk_record_no`),
  KEY `idx_risk_eval_merchant_time` (`merchant_id`,`evaluation_time`,`id`),
  KEY `idx_risk_eval_result_time` (`decision_result`,`evaluation_time`,`id`),
  KEY `idx_risk_eval_level_time` (`risk_level`,`evaluation_time`,`id`),
  KEY `idx_risk_eval_time_id` (`evaluation_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=671 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控评估记录表';

-- ----------------------------
-- Table structure for risk_import_batch
-- ----------------------------
DROP TABLE IF EXISTS `risk_import_batch`;
CREATE TABLE `risk_import_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `module_type` varchar(32) NOT NULL COMMENT '模块类型：AML、BLACK、WHITE、RULE',
  `function_code` varchar(64) NOT NULL COMMENT '功能编码',
  `batch_no` varchar(64) NOT NULL COMMENT '导入批次号',
  `file_name` varchar(255) DEFAULT NULL COMMENT '导入文件名',
  `total_count` int NOT NULL DEFAULT '0' COMMENT '总条数',
  `success_count` int NOT NULL DEFAULT '0' COMMENT '成功条数',
  `failed_count` int NOT NULL DEFAULT '0' COMMENT '失败条数',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0处理中，1成功，2部分成功，3失败',
  `operator` varchar(64) DEFAULT NULL COMMENT '操作人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_risk_import_batch_no` (`batch_no`),
  KEY `idx_risk_import_function_time` (`module_type`,`function_code`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控名单导入批次表';

-- ----------------------------
-- Table structure for risk_import_error
-- ----------------------------
DROP TABLE IF EXISTS `risk_import_error`;
CREATE TABLE `risk_import_error` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_no` varchar(64) NOT NULL COMMENT '导入批次号',
  `row_no` int NOT NULL COMMENT '文件行号',
  `raw_content` text COMMENT '原始行内容，敏感字段必须脱敏',
  `error_message` varchar(1000) NOT NULL COMMENT '错误说明',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_risk_import_error_batch` (`batch_no`,`row_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控名单导入错误表';

-- ----------------------------
-- Table structure for risk_merchant_limit_reservation
-- ----------------------------
DROP TABLE IF EXISTS `risk_merchant_limit_reservation`;
CREATE TABLE `risk_merchant_limit_reservation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `transaction_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '支付平台交易号',
  `risk_record_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '首次创建预占的风控评估流水号',
  `merchant_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商户号',
  `rule_id` bigint NOT NULL COMMENT '商户累计限额规则 ID',
  `limit_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'DAILY/WEEKLY/MONTHLY',
  `currency` char(3) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ISO 4217 币种',
  `period_bucket` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '周期桶，例如 20260730',
  `period_begin_time` datetime(3) NOT NULL COMMENT '周期开始时间',
  `period_end_time` datetime(3) NOT NULL COMMENT '周期结束时间',
  `amount_units` bigint NOT NULL COMMENT '六位小数整数单位的预占金额',
  `counter_mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '预占时 LEGACY/SHADOW/CLUSTER_SAFE',
  `reservation_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PREPARING/RESERVED/CONFIRMED/CANCELLED',
  `cancel_reason` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '取消原因摘要',
  `expires_at` datetime(3) NOT NULL COMMENT 'Redis 周期投影预期过期时间',
  `reserved_time` datetime(3) DEFAULT NULL COMMENT '进入 RESERVED 时间',
  `confirmed_time` datetime(3) DEFAULT NULL COMMENT '进入 CONFIRMED 时间',
  `cancelled_time` datetime(3) DEFAULT NULL COMMENT '进入 CANCELLED 时间',
  `version` int NOT NULL DEFAULT '0' COMMENT 'CAS 乐观锁版本',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_risk_limit_reservation_business` (`transaction_id`,`rule_id`,`limit_type`,`period_bucket`),
  KEY `idx_risk_limit_reservation_status` (`reservation_status`,`update_time`,`id`),
  KEY `idx_risk_limit_reservation_record` (`risk_record_no`,`id`),
  KEY `idx_risk_limit_reservation_baseline` (`merchant_id`,`rule_id`,`currency`,`period_bucket`,`reservation_status`,`deleted`),
  CONSTRAINT `chk_risk_limit_reservation_amount` CHECK ((`amount_units` > 0)),
  CONSTRAINT `chk_risk_limit_reservation_counter_mode` CHECK ((`counter_mode` in (_utf8mb4'LEGACY',_utf8mb4'SHADOW',_utf8mb4'CLUSTER_SAFE'))),
  CONSTRAINT `chk_risk_limit_reservation_status` CHECK ((`reservation_status` in (_utf8mb4'PREPARING',_utf8mb4'RESERVED',_utf8mb4'CONFIRMED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB AUTO_INCREMENT=787 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商户累计限额 Redis 预占生命周期';

-- ----------------------------
-- Table structure for risk_rule_3ds
-- ----------------------------
DROP TABLE IF EXISTS `risk_rule_3ds`;
CREATE TABLE `risk_rule_3ds` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_group_no` varchar(64) NOT NULL COMMENT '规则组编号',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号；全局范围为空字符串',
  `merchant_name` varchar(128) DEFAULT NULL COMMENT '商户名称，管理端展示辅助快照',
  `rule_name` varchar(128) NOT NULL COMMENT '规则名称',
  `rule_type` varchar(32) NOT NULL DEFAULT 'RISK_STRATEGY' COMMENT '规则类型：RISK_STRATEGY风险策略、EXEMPTION_STRATEGY豁免策略、CHANNEL_POLICY渠道策略',
  `channel_code` varchar(64) NOT NULL DEFAULT 'ALL' COMMENT '收单渠道编码，ALL表示全部渠道',
  `payment_method` varchar(64) NOT NULL DEFAULT 'ALL' COMMENT '支付方式，ALL表示全部支付方式',
  `card_brand` varchar(64) NOT NULL DEFAULT 'ALL' COMMENT '卡品牌，ALL表示全部卡品牌',
  `amount_match_type` varchar(32) NOT NULL DEFAULT 'ALL' COMMENT '金额匹配类型：ALL、GE、LE、BETWEEN',
  `amount_min` decimal(18,2) DEFAULT NULL COMMENT '最小交易金额，固定USD且保留2位小数',
  `amount_max` decimal(18,2) DEFAULT NULL COMMENT '最大交易金额，固定USD且保留2位小数',
  `currency` varchar(3) NOT NULL DEFAULT 'USD' COMMENT '交易币种，当前固定USD',
  `risk_condition` varchar(32) NOT NULL DEFAULT 'ANY' COMMENT '风险条件：ANY、LOW_AND_ABOVE、MEDIUM_AND_ABOVE、HIGH_AND_ABOVE、CRITICAL_ONLY',
  `trigger_action` varchar(32) NOT NULL DEFAULT 'FORCE_3DS' COMMENT '触发动作：FORCE_3DS、SKIP_3DS、FOLLOW_DEFAULT',
  `priority` int NOT NULL DEFAULT '100' COMMENT '优先级，数字越小越优先',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_3ds_dimension_deleted` (`merchant_scope`,`merchant_id`,`channel_code`,`payment_method`,`card_brand`,`amount_match_type`,`amount_min`,`amount_max`,`currency`,`risk_condition`,`trigger_action`,`deleted`),
  KEY `idx_risk_rule_scope` (`merchant_scope`,`merchant_id`,`status`,`deleted`),
  KEY `idx_risk_rule_time` (`create_time`),
  KEY `idx_risk_rule_currency` (`currency`,`status`,`deleted`),
  KEY `idx_rule_3ds_trade_lookup` (`deleted`,`status`,`merchant_scope`,`merchant_id`,`channel_code`,`payment_method`,`card_brand`,`currency`,`priority`,`effective_time`,`expire_time`),
  KEY `idx_rule_3ds_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_rule_3ds_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='3DS规则管理表';

-- ----------------------------
-- Table structure for risk_rule_card_bin
-- ----------------------------
DROP TABLE IF EXISTS `risk_rule_card_bin`;
CREATE TABLE `risk_rule_card_bin` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，仅商户范围生效时必填',
  `rule_name` varchar(128) NOT NULL COMMENT '规则名称',
  `match_mode` varchar(32) DEFAULT NULL COMMENT '匹配方式：EXACT、DOMAIN、CONTAINS、REGEX',
  `match_value` varchar(512) DEFAULT NULL COMMENT '匹配值，URL类规则不得写入卡号等敏感数据',
  `limit_type` varchar(64) DEFAULT NULL COMMENT '限额类型，复用 channel_limit_type 或风险字典',
  `amount_min` decimal(18,6) DEFAULT NULL COMMENT '最小金额',
  `amount_max` decimal(18,6) DEFAULT NULL COMMENT '最大金额',
  `currency` varchar(3) DEFAULT NULL COMMENT '币种，ISO 4217 Alpha-3',
  `time_window_seconds` int DEFAULT NULL COMMENT '时间窗口秒数',
  `threshold_count` int DEFAULT NULL COMMENT '阈值次数',
  `elements_json` json DEFAULT NULL COMMENT '限定元素或组合元素JSON',
  `risk_level` varchar(32) NOT NULL DEFAULT 'MEDIUM' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REVIEW' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_risk_rule_scope` (`merchant_scope`,`merchant_id`,`status`,`deleted`),
  KEY `idx_risk_rule_time` (`create_time`),
  KEY `idx_risk_rule_currency` (`currency`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡BIN交易规则表';

-- ----------------------------
-- Table structure for risk_rule_frequency
-- ----------------------------
DROP TABLE IF EXISTS `risk_rule_frequency`;
CREATE TABLE `risk_rule_frequency` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，仅商户范围生效时必填',
  `rule_name` varchar(128) NOT NULL COMMENT '规则名称',
  `match_mode` varchar(32) DEFAULT NULL COMMENT '匹配方式：EXACT、DOMAIN、CONTAINS、REGEX',
  `match_value` varchar(512) DEFAULT NULL COMMENT '匹配值，URL类规则不得写入卡号等敏感数据',
  `limit_type` varchar(64) DEFAULT NULL COMMENT '限额类型，复用 channel_limit_type 或风险字典',
  `amount_min` decimal(18,6) DEFAULT NULL COMMENT '最小金额',
  `amount_max` decimal(18,6) DEFAULT NULL COMMENT '最大金额',
  `currency` varchar(3) DEFAULT NULL COMMENT '币种，ISO 4217 Alpha-3',
  `time_window_seconds` int DEFAULT NULL COMMENT '时间窗口秒数',
  `threshold_count` int DEFAULT NULL COMMENT '阈值次数',
  `elements_json` json DEFAULT NULL COMMENT '限定元素或组合元素JSON',
  `risk_level` varchar(32) NOT NULL DEFAULT 'MEDIUM' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REVIEW' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_risk_rule_scope` (`merchant_scope`,`merchant_id`,`status`,`deleted`),
  KEY `idx_risk_rule_time` (`create_time`),
  KEY `idx_risk_rule_currency` (`currency`,`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易频率限定规则表';

-- ----------------------------
-- Table structure for risk_rule_issuer_country
-- ----------------------------
DROP TABLE IF EXISTS `risk_rule_issuer_country`;
CREATE TABLE `risk_rule_issuer_country` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，仅商户范围生效时必填',
  `rule_name` varchar(128) NOT NULL COMMENT '规则名称',
  `match_mode` varchar(32) DEFAULT NULL COMMENT '匹配方式：EXACT、DOMAIN、CONTAINS、REGEX',
  `match_value` varchar(512) DEFAULT NULL COMMENT '匹配值，URL类规则不得写入卡号等敏感数据',
  `limit_type` varchar(64) DEFAULT NULL COMMENT '限额类型，复用 channel_limit_type 或风险字典',
  `amount_min` decimal(18,6) DEFAULT NULL COMMENT '最小金额',
  `amount_max` decimal(18,6) DEFAULT NULL COMMENT '最大金额',
  `currency` varchar(3) DEFAULT NULL COMMENT '币种，ISO 4217 Alpha-3',
  `time_window_seconds` int DEFAULT NULL COMMENT '时间窗口秒数',
  `threshold_count` int DEFAULT NULL COMMENT '阈值次数',
  `elements_json` json DEFAULT NULL COMMENT '限定元素或组合元素JSON',
  `risk_level` varchar(32) NOT NULL DEFAULT 'MEDIUM' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REVIEW' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_risk_rule_scope` (`merchant_scope`,`merchant_id`,`status`,`deleted`),
  KEY `idx_risk_rule_time` (`create_time`),
  KEY `idx_risk_rule_currency` (`currency`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发卡行国家限定规则表';

-- ----------------------------
-- Table structure for risk_rule_merchant_limit
-- ----------------------------
DROP TABLE IF EXISTS `risk_rule_merchant_limit`;
CREATE TABLE `risk_rule_merchant_limit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，仅商户范围生效时必填',
  `rule_name` varchar(128) NOT NULL COMMENT '规则名称',
  `match_mode` varchar(32) DEFAULT NULL COMMENT '匹配方式：EXACT、DOMAIN、CONTAINS、REGEX',
  `match_value` varchar(512) DEFAULT NULL COMMENT '匹配值，URL类规则不得写入卡号等敏感数据',
  `limit_type` varchar(64) DEFAULT NULL COMMENT '限额类型，复用 channel_limit_type 或风险字典',
  `amount_min` decimal(18,6) DEFAULT NULL COMMENT '最小金额',
  `amount_max` decimal(18,6) DEFAULT NULL COMMENT '最大金额',
  `currency` varchar(3) DEFAULT NULL COMMENT '币种，ISO 4217 Alpha-3',
  `time_window_seconds` int DEFAULT NULL COMMENT '时间窗口秒数',
  `threshold_count` int DEFAULT NULL COMMENT '阈值次数',
  `elements_json` json DEFAULT NULL COMMENT '限定元素或组合元素JSON',
  `risk_level` varchar(32) NOT NULL DEFAULT 'MEDIUM' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REVIEW' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_risk_rule_scope` (`merchant_scope`,`merchant_id`,`status`,`deleted`),
  KEY `idx_risk_rule_time` (`create_time`),
  KEY `idx_risk_rule_currency` (`currency`,`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户交易限额规则表';

-- ----------------------------
-- Table structure for risk_rule_source_url
-- ----------------------------
DROP TABLE IF EXISTS `risk_rule_source_url`;
CREATE TABLE `risk_rule_source_url` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(32) NOT NULL COMMENT '商户号，来源网址限定按商户直接生效',
  `source_url` varchar(512) NOT NULL COMMENT '商户录入来源网址，必须以http://或https://开头',
  `source_host` varchar(255) NOT NULL COMMENT '来源网址Host，交易链路按商户号和Host匹配',
  `risk_level` varchar(32) NOT NULL DEFAULT 'MEDIUM' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REVIEW' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `approval_status` tinyint NOT NULL DEFAULT '1' COMMENT '审核状态：0待审核，1审核通过，2审核拒绝',
  `approval_remark` varchar(500) DEFAULT NULL COMMENT '审批说明，审核拒绝时必填',
  `submit_source` varchar(16) NOT NULL DEFAULT 'ADMIN' COMMENT '提交来源：ADMIN、MERCHANT',
  `review_by` varchar(64) DEFAULT NULL COMMENT '审核人账号或姓名',
  `review_time` datetime(3) DEFAULT NULL COMMENT '审核时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_source_url_merchant_host_deleted` (`merchant_id`,`source_host`,`deleted`),
  KEY `idx_risk_rule_time` (`create_time`),
  KEY `idx_rule_source_url_merchant_time` (`merchant_id`,`update_time`,`id`),
  KEY `idx_rule_source_url_trade_lookup` (`merchant_id`,`source_host`,`approval_status`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_rule_source_url_approval` (`approval_status`,`submit_source`,`create_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户来源网址限定规则表';

-- ----------------------------
-- Table structure for risk_rule_trade_country
-- ----------------------------
DROP TABLE IF EXISTS `risk_rule_trade_country`;
CREATE TABLE `risk_rule_trade_country` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，仅商户范围生效时必填',
  `rule_name` varchar(128) NOT NULL COMMENT '规则名称',
  `match_mode` varchar(32) DEFAULT NULL COMMENT '匹配方式：EXACT、DOMAIN、CONTAINS、REGEX',
  `match_value` varchar(512) DEFAULT NULL COMMENT '匹配值，URL类规则不得写入卡号等敏感数据',
  `limit_type` varchar(64) DEFAULT NULL COMMENT '限额类型，复用 channel_limit_type 或风险字典',
  `amount_min` decimal(18,6) DEFAULT NULL COMMENT '最小金额',
  `amount_max` decimal(18,6) DEFAULT NULL COMMENT '最大金额',
  `currency` varchar(3) DEFAULT NULL COMMENT '币种，ISO 4217 Alpha-3',
  `time_window_seconds` int DEFAULT NULL COMMENT '时间窗口秒数',
  `threshold_count` int DEFAULT NULL COMMENT '阈值次数',
  `elements_json` json DEFAULT NULL COMMENT '限定元素或组合元素JSON',
  `risk_level` varchar(32) NOT NULL DEFAULT 'MEDIUM' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'REVIEW' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_risk_rule_scope` (`merchant_scope`,`merchant_id`,`status`,`deleted`),
  KEY `idx_risk_rule_time` (`create_time`),
  KEY `idx_risk_rule_currency` (`currency`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户交易国家限定规则表';

-- ----------------------------
-- Table structure for risk_trade_black_record
-- ----------------------------
DROP TABLE IF EXISTS `risk_trade_black_record`;
CREATE TABLE `risk_trade_black_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号',
  `merchant_name` varchar(128) DEFAULT NULL COMMENT '商户名称',
  `merchant_order_no` varchar(64) DEFAULT NULL COMMENT '商户订单号',
  `payment_order_no` varchar(64) DEFAULT NULL COMMENT '平台支付订单号',
  `black_target_type` varchar(64) NOT NULL COMMENT '加黑对象类型：CARD、CARD_FINGERPRINT、EMAIL、PHONE、IP、DEVICE、CUSTOMER',
  `black_target_value_masked` varchar(255) NOT NULL COMMENT '加黑对象脱敏展示值',
  `black_target_hash` varchar(128) DEFAULT NULL COMMENT '加黑对象哈希值，禁止保存完整敏感明文',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL、BATCH、SYSTEM',
  `action_type` varchar(32) NOT NULL DEFAULT 'ADD' COMMENT '动作类型：ADD、RELEASE',
  `action_reason` varchar(500) DEFAULT NULL COMMENT '操作原因',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0已解除，1已加黑',
  `operator` varchar(64) DEFAULT NULL COMMENT '操作人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_trade_black_order` (`merchant_order_no`,`payment_order_no`,`deleted`),
  KEY `idx_trade_black_target` (`black_target_type`,`black_target_hash`,`status`,`deleted`),
  KEY `idx_trade_black_merchant_time` (`merchant_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统交易加黑记录表';

-- ----------------------------
-- Table structure for risk_white_card_bin
-- ----------------------------
DROP TABLE IF EXISTS `risk_white_card_bin`;
CREATE TABLE `risk_white_card_bin` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '卡BIN区间展示值，保存为补齐后的起止值',
  `match_value_hash` varchar(128) NOT NULL COMMENT 'BIN区间归一化哈希，用于重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT 'BIN区间默认不保存敏感密文',
  `match_value_start` varchar(11) DEFAULT NULL COMMENT '起始BIN，录入不足11位时右补0',
  `match_value_end` varchar(11) DEFAULT NULL COMMENT '截止BIN，录入不足11位时右补9',
  `match_value_start_number` decimal(39,0) DEFAULT NULL COMMENT '起始BIN数值，交易卡号区间检索使用',
  `match_value_end_number` decimal(39,0) DEFAULT NULL COMMENT '截止BIN数值，交易卡号区间检索使用',
  `card_brand` varchar(64) DEFAULT NULL COMMENT '卡品牌，后端根据起始BIN自动识别',
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'PASS' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_white_card_bin_scope_range_deleted` (`merchant_scope`,`merchant_id`,`match_value_start_number`,`match_value_end_number`,`deleted`),
  KEY `idx_white_card_bin_bin_lookup` (`status`,`deleted`,`merchant_scope`,`merchant_id`,`match_value_start_number`,`match_value_end_number`,`effective_time`,`expire_time`),
  KEY `idx_white_card_bin_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_white_card_bin_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡BIN区间白名单表';

-- ----------------------------
-- Table structure for risk_white_card_fingerprint
-- ----------------------------
DROP TABLE IF EXISTS `risk_white_card_fingerprint`;
CREATE TABLE `risk_white_card_fingerprint` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '卡指纹脱敏展示值，禁止保存完整明文',
  `match_value_hash` varchar(128) NOT NULL COMMENT '匹配值归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) NOT NULL COMMENT '卡指纹密文，仅编辑授权时解密回显',
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'PASS' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_white_card_fingerprint_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_white_card_fingerprint_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_white_card_fingerprint_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_white_card_fingerprint_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡指纹白名单表';

-- ----------------------------
-- Table structure for risk_white_card_no
-- ----------------------------
DROP TABLE IF EXISTS `risk_white_card_no`;
CREATE TABLE `risk_white_card_no` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '卡号脱敏展示值，禁止保存完整卡号明文',
  `match_value_hash` varchar(128) NOT NULL COMMENT '匹配值归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) NOT NULL COMMENT '卡号密文，仅编辑授权时解密回显',
  `card_brand` varchar(64) DEFAULT NULL COMMENT '卡品牌，后端根据卡号自动识别',
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'PASS' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_white_card_no_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_white_card_no_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_white_card_no_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_white_card_no_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡号白名单表';

-- ----------------------------
-- Table structure for risk_white_customer_id
-- ----------------------------
DROP TABLE IF EXISTS `risk_white_customer_id`;
CREATE TABLE `risk_white_customer_id` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT 'Customer ID 脱敏展示值',
  `match_value_hash` varchar(128) NOT NULL COMMENT '匹配值归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) NOT NULL COMMENT 'Customer ID 密文，仅编辑授权时解密回显',
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'PASS' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_white_customer_id_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_white_customer_id_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_white_customer_id_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_white_customer_id_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Customer ID白名单表';

-- ----------------------------
-- Table structure for risk_white_device_fingerprint
-- ----------------------------
DROP TABLE IF EXISTS `risk_white_device_fingerprint`;
CREATE TABLE `risk_white_device_fingerprint` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '设备指纹脱敏展示值，禁止保存完整明文',
  `match_value_hash` varchar(128) NOT NULL COMMENT '匹配值归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) NOT NULL COMMENT '设备指纹密文，仅编辑授权时解密回显',
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'PASS' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_white_device_fingerprint_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_white_device_fingerprint_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_white_device_fingerprint_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_white_device_fingerprint_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备指纹白名单表';

-- ----------------------------
-- Table structure for risk_white_email
-- ----------------------------
DROP TABLE IF EXISTS `risk_white_email`;
CREATE TABLE `risk_white_email` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '邮箱地址脱敏展示值，禁止保存完整邮箱明文',
  `match_value_hash` varchar(128) NOT NULL COMMENT '匹配值归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) NOT NULL COMMENT '邮箱地址密文，仅编辑授权时解密回显',
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'PASS' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_white_email_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_white_email_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_white_email_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_white_email_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮箱地址白名单表';

-- ----------------------------
-- Table structure for risk_white_email_domain
-- ----------------------------
DROP TABLE IF EXISTS `risk_white_email_domain`;
CREATE TABLE `risk_white_email_domain` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '邮箱域名展示值',
  `match_value_hash` varchar(128) NOT NULL COMMENT '匹配值归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '邮箱域名不属于敏感明文，默认不加密存储',
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'PASS' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_white_email_domain_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_white_email_domain_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_white_email_domain_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_white_email_domain_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮箱域名白名单表';

-- ----------------------------
-- Table structure for risk_white_ip
-- ----------------------------
DROP TABLE IF EXISTS `risk_white_ip`;
CREATE TABLE `risk_white_ip` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(128) NOT NULL COMMENT 'IP地址展示值，白名单仅支持单IP',
  `match_value_hash` varchar(128) NOT NULL COMMENT 'IP地址归一化哈希，用于重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT 'IP地址默认不保存敏感密文',
  `match_value_start` varchar(128) NOT NULL COMMENT 'IP地址值',
  `match_value_end` varchar(128) NOT NULL COMMENT 'IP地址值，白名单与起始值一致',
  `match_value_start_number` decimal(39,0) NOT NULL COMMENT 'IP数值，交易检索使用',
  `match_value_end_number` decimal(39,0) NOT NULL COMMENT 'IP数值，白名单与起始数值一致',
  `ip_version` varchar(8) NOT NULL COMMENT 'IP版本：IPV4、IPV6',
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'PASS' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_white_ip_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_white_ip_trade_lookup` (`ip_version`,`match_value_start_number`,`match_value_end_number`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_white_ip_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_white_ip_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IP地址白名单表';

-- ----------------------------
-- Table structure for risk_white_issuer_country
-- ----------------------------
DROP TABLE IF EXISTS `risk_white_issuer_country`;
CREATE TABLE `risk_white_issuer_country` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(3) NOT NULL COMMENT '国家或地区 Alpha-3 编码展示值',
  `match_value_hash` varchar(128) NOT NULL COMMENT '国家或地区 Alpha-3 编码哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '预留密文字段，国家或地区默认不加密存储',
  `country_alpha2` varchar(2) DEFAULT NULL COMMENT '国家或地区 Alpha-2 编码，仅用于管理端回显',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家或地区 Alpha-3 编码，交易匹配主字段',
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'PASS' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_white_issuer_country_scope_country_deleted` (`merchant_scope`,`merchant_id`,`country_alpha3`,`deleted`),
  KEY `idx_white_issuer_country_trade_lookup` (`country_alpha3`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_white_issuer_country_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_white_issuer_country_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发卡行国家地区白名单表';

-- ----------------------------
-- Table structure for risk_white_merchant
-- ----------------------------
DROP TABLE IF EXISTS `risk_white_merchant`;
CREATE TABLE `risk_white_merchant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'MERCHANT' COMMENT '固定为MERCHANT，商户白名单仅对商户自身生效',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '白名单商户号，与商户号展示值保持一致',
  `match_value_masked` varchar(255) NOT NULL COMMENT '商户号展示值',
  `match_value_hash` varchar(128) NOT NULL COMMENT '匹配值归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '商户白名单不保存敏感密文',
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'PASS' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_white_merchant_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_white_merchant_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_white_merchant_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_white_merchant_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户白名单表';

-- ----------------------------
-- Table structure for risk_white_phone
-- ----------------------------
DROP TABLE IF EXISTS `risk_white_phone`;
CREATE TABLE `risk_white_phone` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(255) NOT NULL COMMENT '手机号展示值',
  `match_value_hash` varchar(128) NOT NULL COMMENT '匹配值归一化哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) NOT NULL COMMENT '手机号密文，仅编辑授权时解密回显',
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'PASS' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_white_phone_scope_hash_deleted` (`merchant_scope`,`merchant_id`,`match_value_hash`,`deleted`),
  KEY `idx_white_phone_trade_lookup` (`match_value_hash`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_white_phone_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_white_phone_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='手机号白名单表';

-- ----------------------------
-- Table structure for risk_white_trade_country
-- ----------------------------
DROP TABLE IF EXISTS `risk_white_trade_country`;
CREATE TABLE `risk_white_trade_country` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_scope` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '生效范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '商户号，仅商户范围生效时必填；全局范围为空字符串',
  `match_value_masked` varchar(3) NOT NULL COMMENT '国家或地区 Alpha-3 编码展示值',
  `match_value_hash` varchar(128) NOT NULL COMMENT '国家或地区 Alpha-3 编码哈希，用于交易检索和重复校验',
  `match_value_cipher` varchar(1024) DEFAULT NULL COMMENT '预留密文字段，国家或地区默认不加密存储',
  `country_alpha2` varchar(2) DEFAULT NULL COMMENT '国家或地区 Alpha-2 编码，仅用于管理端回显',
  `country_alpha3` varchar(3) NOT NULL COMMENT '国家或地区 Alpha-3 编码，交易匹配主字段',
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
  `decision_action` varchar(32) NOT NULL DEFAULT 'PASS' COMMENT '命中动作：REJECT、REVIEW、PASS',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `validity_type` varchar(32) NOT NULL DEFAULT 'SUPER_LONG' COMMENT '有效期类型：SUPER_LONG超长期、LONG长期、LIMITED限定有效期',
  `validity_days` int DEFAULT NULL COMMENT '有效天数，长期和限定有效期使用',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL手工、IMPORT导入、SYSTEM系统',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_white_trade_country_scope_country_deleted` (`merchant_scope`,`merchant_id`,`country_alpha3`,`deleted`),
  KEY `idx_white_trade_country_trade_lookup` (`country_alpha3`,`merchant_scope`,`merchant_id`,`status`,`deleted`,`effective_time`,`expire_time`),
  KEY `idx_white_trade_country_merchant_time` (`merchant_scope`,`merchant_id`,`update_time`,`id`),
  KEY `idx_white_trade_country_time` (`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易国家地区白名单表';

-- ----------------------------
-- Table structure for security_intercept_event
-- ----------------------------
DROP TABLE IF EXISTS `security_intercept_event`;
CREATE TABLE `security_intercept_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_no` varchar(64) NOT NULL COMMENT '安全事件号',
  `event_time` datetime(3) NOT NULL COMMENT '事件发生时间',
  `source_layer` varchar(32) NOT NULL COMMENT '来源层级：OPENAPI/CHANNEL/GATEWAY',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型',
  `risk_level` varchar(16) NOT NULL COMMENT '风险等级：LOW/MEDIUM/HIGH/CRITICAL',
  `action` varchar(32) NOT NULL COMMENT '处置动作：BLOCK/REVIEW/LOG',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号',
  `client_ip` varchar(45) DEFAULT NULL COMMENT '客户端IP',
  `request_method` varchar(16) DEFAULT NULL COMMENT '请求方法',
  `request_path` varchar(512) DEFAULT NULL COMMENT '请求路径',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路追踪ID',
  `request_id` varchar(64) DEFAULT NULL COMMENT '请求ID',
  `user_agent` varchar(512) DEFAULT NULL COMMENT '脱敏或截断后的User-Agent',
  `reason_code` varchar(64) DEFAULT NULL COMMENT '拦截原因码',
  `reason_message` varchar(512) DEFAULT NULL COMMENT '脱敏后的拦截原因说明',
  `service_name` varchar(64) DEFAULT NULL COMMENT '记录事件的服务名',
  `hit_rule_code` varchar(64) DEFAULT NULL COMMENT '命中的安全规则编码',
  `header_summary` varchar(1024) DEFAULT NULL COMMENT '脱敏后的请求头摘要，禁止保存Authorization/Cookie/密钥/完整密文',
  `process_status` tinyint NOT NULL DEFAULT '0' COMMENT '处理状态：0未处理，1已处理，2忽略',
  `process_remark` varchar(512) DEFAULT NULL COMMENT '处理备注',
  `processed_by` varchar(64) DEFAULT NULL COMMENT '处理人',
  `processed_time` datetime(3) DEFAULT NULL COMMENT '处理时间',
  `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_security_intercept_event_no` (`event_no`),
  KEY `idx_security_intercept_event_time` (`event_time`,`id`),
  KEY `idx_security_intercept_event_merchant_time` (`merchant_id`,`event_time`),
  KEY `idx_security_intercept_event_ip_time` (`client_ip`,`event_time`),
  KEY `idx_security_intercept_event_type_time` (`event_type`,`event_time`),
  KEY `idx_security_intercept_event_risk_time` (`risk_level`,`event_time`),
  KEY `idx_security_intercept_event_trace` (`trace_id`),
  KEY `idx_security_intercept_event_process_time` (`process_status`,`event_time`)
) ENGINE=InnoDB AUTO_INCREMENT=969 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全拦截事件';

-- ----------------------------
-- Table structure for sys_account
-- ----------------------------
DROP TABLE IF EXISTS `sys_account`;
CREATE TABLE `sys_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `user_id` bigint NOT NULL COMMENT '用户主体ID',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，商户系统账号必须绑定已有 base_merchant_info',
  `login_account` varchar(100) NOT NULL COMMENT '登录账号',
  `password_hash` varchar(255) NOT NULL COMMENT '密码哈希值',
  `password_salt` varchar(100) NOT NULL COMMENT '密码盐值',
  `password_algo` varchar(50) NOT NULL COMMENT '密码算法，如 PBKDF2WithHmacSHA256',
  `mobile` varchar(30) DEFAULT NULL COMMENT '该系统登录手机号',
  `email` varchar(150) NOT NULL COMMENT '登录邮箱',
  `mfa_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否开启MFA：0否，1是',
  `mfa_type` varchar(30) DEFAULT NULL COMMENT 'MFA类型：SMS短信，EMAIL邮箱，TOTP身份验证器',
  `totp_secret` varchar(255) DEFAULT NULL COMMENT 'TOTP密钥，生产环境建议加密存储',
  `password_expired` tinyint NOT NULL DEFAULT '0' COMMENT '密码是否过期：0否，1是',
  `password_updated_at` datetime(3) DEFAULT NULL COMMENT '密码更新时间',
  `last_login_at` datetime(3) DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(64) DEFAULT NULL COMMENT '最后登录IP',
  `failed_login_count` int NOT NULL DEFAULT '0' COMMENT '连续登录失败次数',
  `locked` tinyint NOT NULL DEFAULT '0' COMMENT '是否锁定：0否，1是',
  `locked_at` datetime(3) DEFAULT NULL COMMENT '锁定时间',
  `locked_reason` varchar(255) DEFAULT NULL COMMENT '锁定原因',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_account_app_login_deleted` (`app_id`,`login_account`,`deleted`),
  UNIQUE KEY `uk_sys_account_app_user_deleted` (`app_id`,`user_id`,`deleted`),
  KEY `idx_sys_account_merchant` (`merchant_id`,`status`,`deleted`),
  KEY `idx_sys_account_status_locked` (`status`,`locked`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统登录账号表';

-- ----------------------------
-- Table structure for sys_account_mfa
-- ----------------------------
DROP TABLE IF EXISTS `sys_account_mfa`;
CREATE TABLE `sys_account_mfa` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `account_id` bigint NOT NULL COMMENT '登录账号ID',
  `user_id` bigint NOT NULL COMMENT '用户主体ID',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，管理后台账号为空',
  `mfa_policy` varchar(30) NOT NULL COMMENT 'MFA策略：OPTIONAL未强制，REQUIRED强制，EXEMPT豁免',
  `mfa_status` varchar(30) NOT NULL COMMENT 'MFA状态：NOT_ENABLED、PENDING_BIND、ENABLED、RESET_REQUIRED、EXEMPT、LOCKED、DISABLED',
  `mfa_type` varchar(30) NOT NULL DEFAULT 'TOTP' COMMENT 'MFA类型，本期固定TOTP',
  `secret_cipher` varchar(512) DEFAULT NULL COMMENT '已绑定TOTP密钥密文',
  `pending_secret_cipher` varchar(512) DEFAULT NULL COMMENT '待绑定TOTP密钥密文',
  `issuer` varchar(100) DEFAULT NULL COMMENT '验证器发行方',
  `account_label` varchar(150) DEFAULT NULL COMMENT '验证器账号标签',
  `bind_time` datetime(3) DEFAULT NULL COMMENT '完成绑定时间',
  `last_verify_time` datetime(3) DEFAULT NULL COMMENT '最近验证成功时间',
  `last_success_time_step` bigint DEFAULT NULL COMMENT '最近验证成功TOTP时间步，用于防重放',
  `failed_verify_count` int NOT NULL DEFAULT '0' COMMENT '连续验证失败次数',
  `locked_until` datetime(3) DEFAULT NULL COMMENT '临时锁定截止时间',
  `reset_time` datetime(3) DEFAULT NULL COMMENT '最近重置时间',
  `exempt_reason` varchar(500) DEFAULT NULL COMMENT '豁免原因',
  `exempt_until` datetime(3) DEFAULT NULL COMMENT '豁免截止时间，空表示长期豁免',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_account_mfa_account_deleted` (`app_id`,`account_id`,`deleted`),
  KEY `idx_sys_account_mfa_status` (`app_id`,`mfa_policy`,`mfa_status`,`deleted`),
  KEY `idx_sys_account_mfa_user` (`app_id`,`user_id`,`deleted`),
  KEY `idx_sys_account_mfa_merchant` (`merchant_id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统账号MFA配置表';

-- ----------------------------
-- Table structure for sys_account_mfa_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_account_mfa_log`;
CREATE TABLE `sys_account_mfa_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `account_id` bigint DEFAULT NULL COMMENT '登录账号ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户主体ID',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，管理后台账号为空',
  `action_type` varchar(50) NOT NULL COMMENT '操作类型',
  `result` varchar(20) NOT NULL COMMENT '操作结果：SUCCESS、FAILED',
  `reason` varchar(500) DEFAULT NULL COMMENT '操作原因或失败原因',
  `before_policy` varchar(30) DEFAULT NULL COMMENT '变更前策略',
  `before_status` varchar(30) DEFAULT NULL COMMENT '变更前状态',
  `after_policy` varchar(30) DEFAULT NULL COMMENT '变更后策略',
  `after_status` varchar(30) DEFAULT NULL COMMENT '变更后状态',
  `operator_account_id` bigint DEFAULT NULL COMMENT '操作人账号ID',
  `operator_login_account` varchar(100) DEFAULT NULL COMMENT '操作人登录账号',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(512) DEFAULT NULL COMMENT 'User-Agent',
  `event_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_sys_account_mfa_log_account_time` (`app_id`,`account_id`,`event_time`),
  KEY `idx_sys_account_mfa_log_action_time` (`app_id`,`action_type`,`result`,`event_time`),
  KEY `idx_sys_account_mfa_log_operator_time` (`operator_account_id`,`event_time`)
) ENGINE=InnoDB AUTO_INCREMENT=60 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统账号MFA安全操作日志表';

-- ----------------------------
-- Table structure for sys_account_mfa_token
-- ----------------------------
DROP TABLE IF EXISTS `sys_account_mfa_token`;
CREATE TABLE `sys_account_mfa_token` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `account_id` bigint NOT NULL COMMENT '登录账号ID',
  `token_type` varchar(30) NOT NULL COMMENT '票据类型：LOGIN_MFA',
  `token_hash` varchar(128) NOT NULL COMMENT '票据SHA-256哈希',
  `challenge_type` varchar(40) NOT NULL COMMENT '挑战类型：BIND_REQUIRED、VERIFY_REQUIRED、RESET_BIND_REQUIRED',
  `expire_at` datetime(3) NOT NULL COMMENT '过期时间',
  `used` tinyint NOT NULL DEFAULT '0' COMMENT '是否已使用：0否，1是',
  `used_at` datetime(3) DEFAULT NULL COMMENT '使用时间',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(512) DEFAULT NULL COMMENT 'User-Agent',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_account_mfa_token_hash_deleted` (`token_hash`,`deleted`),
  KEY `idx_sys_account_mfa_token_account` (`app_id`,`account_id`,`token_type`,`used`,`expire_at`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统账号MFA短期票据表';

-- ----------------------------
-- Table structure for sys_account_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_account_role`;
CREATE TABLE `sys_account_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `account_id` bigint NOT NULL COMMENT '账号ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_account_role_deleted` (`app_id`,`account_id`,`role_id`,`deleted`),
  KEY `idx_sys_account_role_account` (`app_id`,`account_id`,`deleted`),
  KEY `idx_sys_account_role_role` (`app_id`,`role_id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=60 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号角色关联表';

-- ----------------------------
-- Table structure for sys_app
-- ----------------------------
DROP TABLE IF EXISTS `sys_app`;
CREATE TABLE `sys_app` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_code` varchar(50) NOT NULL COMMENT '系统编码：ADMIN管理后台，MERCHANT商户系统',
  `app_name` varchar(100) NOT NULL COMMENT '系统名称',
  `app_type` varchar(30) NOT NULL COMMENT '系统类型：ADMIN管理端，MERCHANT商户端',
  `domain_url` varchar(255) DEFAULT NULL COMMENT '系统访问域名或地址',
  `description` varchar(500) DEFAULT NULL COMMENT '系统说明',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_app_code_deleted` (`app_code`,`deleted`),
  KEY `idx_sys_app_type_status` (`app_type`,`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统应用表';

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_name` varchar(100) NOT NULL COMMENT '参数名称',
  `config_key` varchar(150) NOT NULL COMMENT '参数键名，全局唯一，如 sys.user.init_password',
  `config_value` text COMMENT '参数键值，支持普通文本或JSON字符串',
  `value_type` tinyint NOT NULL DEFAULT '1' COMMENT '值类型：1字符串，2数字，3布尔，4JSON',
  `config_group` varchar(64) DEFAULT NULL COMMENT '配置分组，如 system、merchant、risk、settlement',
  `system_builtin` tinyint NOT NULL DEFAULT '0' COMMENT '是否系统内置：0否，1是',
  `visible` tinyint NOT NULL DEFAULT '1' COMMENT '是否前端可见：0否，1是',
  `encrypted` tinyint NOT NULL DEFAULT '0' COMMENT '是否加密存储：0否，1是；密钥类配置不建议放本表',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注说明',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_config_key_deleted` (`config_key`,`deleted`),
  KEY `idx_sys_config_group_status` (`config_group`,`status`,`deleted`),
  KEY `idx_sys_config_name` (`config_name`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统参数配置表';

-- ----------------------------
-- Table structure for sys_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父部门ID，0为根节点',
  `dept_name` varchar(100) NOT NULL COMMENT '部门名称',
  `sort_no` int NOT NULL DEFAULT '100' COMMENT '显示排序',
  `leader` varchar(50) DEFAULT NULL COMMENT '负责人',
  `phone` varchar(30) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(150) DEFAULT NULL COMMENT '邮箱',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_sys_dept_app_parent` (`app_id`,`parent_id`,`status`,`deleted`),
  KEY `idx_sys_dept_deleted` (`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门管理表';

-- ----------------------------
-- Table structure for sys_dict_data
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_data`;
CREATE TABLE `sys_dict_data` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dict_type` varchar(100) NOT NULL COMMENT '字典类型编码，对应 sys_dict_type.dict_type',
  `dict_label` varchar(100) NOT NULL COMMENT '字典标签，前端展示值',
  `dict_value` varchar(100) NOT NULL COMMENT '字典键值，业务实际值',
  `parent_value` varchar(100) DEFAULT NULL COMMENT '父级字典值，用于层级字典',
  `locale` varchar(16) NOT NULL DEFAULT 'zh-CN' COMMENT '语言区域，如 zh-CN、en-US',
  `dict_sort` int NOT NULL DEFAULT '0' COMMENT '排序，值越小越靠前',
  `list_class` varchar(100) DEFAULT NULL COMMENT '展示样式：default、primary、success、warning、danger',
  `extra_json` text COMMENT '扩展属性JSON，如图标、颜色、渠道映射值',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '是否默认：0否，1是',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_value_deleted` (`dict_type`,`dict_value`,`locale`,`deleted`),
  KEY `idx_sys_dict_type_status_sort` (`dict_type`,`status`,`dict_sort`,`deleted`),
  KEY `idx_sys_dict_parent` (`dict_type`,`parent_value`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=15263 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典数据表';

-- ----------------------------
-- Table structure for sys_dict_type
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dict_name` varchar(100) NOT NULL COMMENT '字典名称，如商户状态、风险等级',
  `dict_type` varchar(100) NOT NULL COMMENT '字典类型编码，如 merchant_status',
  `biz_domain` varchar(64) DEFAULT NULL COMMENT '业务域：system、merchant、payment、risk、settlement',
  `system_builtin` tinyint NOT NULL DEFAULT '0' COMMENT '是否系统内置：0否，1是',
  `editable` tinyint NOT NULL DEFAULT '1' COMMENT '是否允许编辑：0否，1是',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_type_deleted` (`dict_type`,`deleted`),
  KEY `idx_sys_dict_type_domain_status` (`biz_domain`,`status`,`deleted`),
  KEY `idx_sys_dict_type_name` (`dict_name`)
) ENGINE=InnoDB AUTO_INCREMENT=81 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型表';

-- ----------------------------
-- Table structure for sys_job_executor_node
-- ----------------------------
DROP TABLE IF EXISTS `sys_job_executor_node`;
CREATE TABLE `sys_job_executor_node` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `node_id` varchar(100) NOT NULL COMMENT '节点ID',
  `app_name` varchar(100) NOT NULL COMMENT '服务名称',
  `host` varchar(100) NOT NULL COMMENT '节点Host',
  `port` int DEFAULT NULL COMMENT '节点端口',
  `instance_id` varchar(150) DEFAULT NULL COMMENT '实例ID',
  `status` varchar(30) NOT NULL DEFAULT 'ONLINE' COMMENT '节点状态：ONLINE/OFFLINE/UNKNOWN',
  `last_heartbeat_time` datetime NOT NULL COMMENT '最后心跳时间',
  `current_running_count` int NOT NULL DEFAULT '0' COMMENT '当前运行任务数',
  `max_concurrent_count` int NOT NULL DEFAULT '10' COMMENT '最大并发数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_node_id` (`node_id`),
  KEY `idx_status_heartbeat` (`status`,`last_heartbeat_time`)
) ENGINE=InnoDB AUTO_INCREMENT=135670 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统任务执行节点表';

-- ----------------------------
-- Table structure for sys_job_run_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_job_run_log`;
CREATE TABLE `sys_job_run_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `run_id` varchar(100) NOT NULL COMMENT '执行批次号',
  `job_id` bigint NOT NULL COMMENT '任务ID',
  `job_code` varchar(100) NOT NULL COMMENT '任务编码',
  `job_name` varchar(100) NOT NULL COMMENT '任务名称',
  `handler_code` varchar(100) NOT NULL COMMENT '任务处理器编码',
  `trigger_type` varchar(30) NOT NULL COMMENT '触发方式：SCHEDULE/MANUAL/RETRY',
  `scheduler_mode` varchar(30) NOT NULL COMMENT '调度模式：STANDALONE/DISTRIBUTED',
  `execute_mode` varchar(30) NOT NULL DEFAULT 'SYNC' COMMENT '执行模式：SYNC/ASYNC',
  `executor_node` varchar(100) DEFAULT NULL COMMENT '执行节点',
  `run_status` varchar(30) NOT NULL COMMENT '执行状态：WAITING/RUNNING/SUCCESS/FAILED/TIMEOUT/CANCELLED',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration_ms` bigint DEFAULT NULL COMMENT '执行耗时，毫秒',
  `retry_index` int NOT NULL DEFAULT '0' COMMENT '当前重试序号',
  `max_retry_count` int NOT NULL DEFAULT '0' COMMENT '最大重试次数',
  `timeout_seconds` int NOT NULL DEFAULT '300' COMMENT '超时时间快照，单位秒',
  `params_snapshot` json DEFAULT NULL COMMENT '脱敏后的参数快照',
  `result_message` varchar(1000) DEFAULT NULL COMMENT '执行结果摘要',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '失败原因摘要',
  `trace_id` varchar(100) DEFAULT NULL COMMENT '链路追踪ID',
  `operator_id` varchar(64) DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人名称',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_id` (`run_id`),
  KEY `idx_job_code_create_time` (`job_code`,`create_time`),
  KEY `idx_run_status_create_time` (`run_status`,`create_time`),
  KEY `idx_executor_node_create_time` (`executor_node`,`create_time`),
  KEY `idx_job_id_create_time` (`job_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=8938 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统任务执行日志表';

-- ----------------------------
-- Table structure for sys_job_task
-- ----------------------------
DROP TABLE IF EXISTS `sys_job_task`;
CREATE TABLE `sys_job_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_code` varchar(100) NOT NULL COMMENT '任务编码',
  `job_name` varchar(100) NOT NULL COMMENT '任务名称',
  `job_group` varchar(50) NOT NULL DEFAULT 'system' COMMENT '任务分组：system/payment/settlement/notification',
  `handler_code` varchar(100) NOT NULL COMMENT '任务处理器编码',
  `cron_expression` varchar(100) DEFAULT NULL COMMENT 'Cron表达式',
  `scheduler_mode` varchar(30) NOT NULL DEFAULT 'STANDALONE' COMMENT '调度模式：STANDALONE/DISTRIBUTED',
  `trigger_mode` varchar(30) NOT NULL DEFAULT 'CRON' COMMENT '触发模式：CRON/MANUAL',
  `execute_mode` varchar(30) NOT NULL DEFAULT 'SYNC' COMMENT '执行模式：SYNC/ASYNC',
  `route_strategy` varchar(30) NOT NULL DEFAULT 'LOCAL' COMMENT '路由策略：LOCAL/FIRST',
  `misfire_strategy` varchar(30) NOT NULL DEFAULT 'IGNORE' COMMENT '错过调度策略：IGNORE/FIRE_ONCE',
  `timeout_seconds` int NOT NULL DEFAULT '300' COMMENT '超时时间，单位秒',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '失败重试次数',
  `retry_interval_seconds` int NOT NULL DEFAULT '60' COMMENT '失败重试间隔，单位秒',
  `allow_concurrent` tinyint NOT NULL DEFAULT '0' COMMENT '是否允许同一任务并发执行：0=否，1=是',
  `params` json DEFAULT NULL COMMENT '默认任务参数JSON',
  `status` varchar(30) NOT NULL DEFAULT 'DISABLED' COMMENT '任务状态：ENABLED/DISABLED',
  `description` varchar(500) DEFAULT NULL COMMENT '任务描述',
  `next_trigger_time` datetime DEFAULT NULL COMMENT '下次触发时间',
  `last_trigger_time` datetime DEFAULT NULL COMMENT '上次触发时间',
  `last_run_status` varchar(30) DEFAULT NULL COMMENT '上次执行状态',
  `lock_owner` varchar(100) DEFAULT NULL COMMENT '锁持有节点',
  `lock_until` datetime DEFAULT NULL COMMENT '锁过期时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除：0=否，1=是',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_code` (`job_code`),
  KEY `idx_status_next_trigger_time` (`status`,`next_trigger_time`),
  KEY `idx_handler_code` (`handler_code`),
  KEY `idx_job_group` (`job_group`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统任务定义表';

-- ----------------------------
-- Table structure for sys_login_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_login_log`;
CREATE TABLE `sys_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `account_id` bigint DEFAULT NULL COMMENT '账号ID，登录失败且账号不存在时为空',
  `user_id` bigint DEFAULT NULL COMMENT '用户主体ID',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号',
  `login_account` varchar(100) DEFAULT NULL COMMENT '登录账号',
  `login_ip` varchar(64) DEFAULT NULL COMMENT '登录IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `login_status` tinyint NOT NULL DEFAULT '1' COMMENT '登录状态：0失败，1成功',
  `fail_reason` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `login_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '登录时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_sys_login_app_account_time` (`app_id`,`account_id`,`login_at`),
  KEY `idx_sys_login_merchant_time` (`merchant_id`,`login_at`),
  KEY `idx_sys_login_status_time` (`login_status`,`login_at`)
) ENGINE=InnoDB AUTO_INCREMENT=819 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表';

-- ----------------------------
-- Table structure for sys_login_session
-- ----------------------------
DROP TABLE IF EXISTS `sys_login_session`;
CREATE TABLE `sys_login_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `account_id` bigint NOT NULL COMMENT '账号ID',
  `user_id` bigint NOT NULL COMMENT '用户主体ID',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号',
  `token_hash` varchar(128) NOT NULL COMMENT '登录token哈希，禁止保存token明文',
  `login_ip` varchar(64) DEFAULT NULL COMMENT '登录IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `expire_at` datetime(3) NOT NULL COMMENT '过期时间',
  `logout` tinyint NOT NULL DEFAULT '0' COMMENT '是否退出：0否，1是',
  `logout_at` datetime(3) DEFAULT NULL COMMENT '退出时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_login_session_token` (`token_hash`),
  KEY `idx_sys_login_session_account` (`app_id`,`account_id`,`logout`,`expire_at`),
  KEY `idx_sys_login_session_merchant` (`merchant_id`,`logout`,`expire_at`)
) ENGINE=InnoDB AUTO_INCREMENT=812 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录会话表';

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父级菜单ID，0为顶级',
  `menu_code` varchar(100) NOT NULL COMMENT '菜单编码',
  `menu_name` varchar(100) NOT NULL COMMENT '菜单名称',
  `menu_type` varchar(30) NOT NULL COMMENT '菜单类型：CATALOG目录，MENU菜单，BUTTON按钮，LINK外链',
  `route_path` varchar(255) DEFAULT NULL COMMENT '前端路由路径',
  `component_path` varchar(255) DEFAULT NULL COMMENT '前端组件路径',
  `permission_code` varchar(150) DEFAULT NULL COMMENT '权限标识，前端按钮鉴权使用',
  `icon` varchar(100) DEFAULT NULL COMMENT '菜单图标',
  `redirect` varchar(255) DEFAULT NULL COMMENT '重定向地址',
  `visible` tinyint NOT NULL DEFAULT '1' COMMENT '是否显示：0隐藏，1显示',
  `keep_alive` tinyint NOT NULL DEFAULT '0' COMMENT '是否缓存页面：0否，1是',
  `external_link` tinyint NOT NULL DEFAULT '0' COMMENT '是否外链：0否，1是',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_menu_app_code_deleted` (`app_id`,`menu_code`,`deleted`),
  KEY `idx_sys_menu_app_parent` (`app_id`,`parent_id`,`status`,`deleted`),
  KEY `idx_sys_menu_permission` (`permission_code`)
) ENGINE=InnoDB AUTO_INCREMENT=1493 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单表';

-- ----------------------------
-- Table structure for sys_merchant_account_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_merchant_account_dept`;
CREATE TABLE `sys_merchant_account_dept` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(64) NOT NULL COMMENT '商户号',
  `account_id` bigint NOT NULL COMMENT '商户账号ID',
  `dept_id` bigint NOT NULL COMMENT '部门ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_dept` (`account_id`,`dept_id`),
  KEY `idx_merchant_id` (`merchant_id`),
  KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户账号部门关联表';

-- ----------------------------
-- Table structure for sys_merchant_account_post
-- ----------------------------
DROP TABLE IF EXISTS `sys_merchant_account_post`;
CREATE TABLE `sys_merchant_account_post` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(64) NOT NULL COMMENT '商户号',
  `account_id` bigint NOT NULL COMMENT '商户账号ID',
  `post_id` bigint NOT NULL COMMENT '岗位ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_post` (`account_id`,`post_id`),
  KEY `idx_merchant_id` (`merchant_id`),
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户账号岗位关联表';

-- ----------------------------
-- Table structure for sys_merchant_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_merchant_dept`;
CREATE TABLE `sys_merchant_dept` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(64) NOT NULL COMMENT '商户号',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父级部门ID，0=顶级部门',
  `dept_code` varchar(80) NOT NULL COMMENT '部门编码',
  `dept_name` varchar(100) NOT NULL COMMENT '部门名称',
  `leader_account_id` bigint DEFAULT NULL COMMENT '负责人账号ID',
  `phone` varchar(30) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(150) DEFAULT NULL COMMENT '邮箱',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=启用，0=禁用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0=未删除，非0=删除时间戳或删除批次ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_dept_code_deleted` (`merchant_id`,`dept_code`,`deleted`),
  KEY `idx_merchant_parent` (`merchant_id`,`parent_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户部门表';

-- ----------------------------
-- Table structure for sys_merchant_menu_grant
-- ----------------------------
DROP TABLE IF EXISTS `sys_merchant_menu_grant`;
CREATE TABLE `sys_merchant_menu_grant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(64) NOT NULL COMMENT '商户号',
  `app_id` bigint NOT NULL COMMENT '系统应用ID，固定为商户系统',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  `grant_source` varchar(30) NOT NULL DEFAULT 'ADMIN' COMMENT '授权来源：ADMIN=平台授权，SYSTEM=系统初始化',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=启用，0=禁用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0=未删除，非0=删除时间戳或删除批次ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_menu_deleted` (`merchant_id`,`menu_id`,`deleted`),
  KEY `idx_merchant_id` (`merchant_id`),
  KEY `idx_app_id` (`app_id`),
  KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1545 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户菜单授权表';

-- ----------------------------
-- Table structure for sys_merchant_permission_grant
-- ----------------------------
DROP TABLE IF EXISTS `sys_merchant_permission_grant`;
CREATE TABLE `sys_merchant_permission_grant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(64) NOT NULL COMMENT '商户号',
  `app_id` bigint NOT NULL COMMENT '系统应用ID，固定为商户系统',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `grant_source` varchar(30) NOT NULL DEFAULT 'ADMIN' COMMENT '授权来源：ADMIN=平台授权，SYSTEM=系统初始化',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=启用，0=禁用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0=未删除，非0=删除时间戳或删除批次ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_permission_deleted` (`merchant_id`,`permission_id`,`deleted`),
  KEY `idx_merchant_id` (`merchant_id`),
  KEY `idx_app_id` (`app_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1764 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户权限授权表';

-- ----------------------------
-- Table structure for sys_merchant_post
-- ----------------------------
DROP TABLE IF EXISTS `sys_merchant_post`;
CREATE TABLE `sys_merchant_post` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(64) NOT NULL COMMENT '商户号',
  `post_code` varchar(80) NOT NULL COMMENT '岗位编码',
  `post_name` varchar(100) NOT NULL COMMENT '岗位名称',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=启用，0=禁用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0=未删除，非0=删除时间戳或删除批次ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_post_code_deleted` (`merchant_id`,`post_code`,`deleted`),
  KEY `idx_merchant_id` (`merchant_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户岗位表';

-- ----------------------------
-- Table structure for sys_merchant_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_merchant_user`;
CREATE TABLE `sys_merchant_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_info_id` bigint NOT NULL COMMENT '商户主表ID，对应 base_merchant_info.id',
  `merchant_id` varchar(32) NOT NULL COMMENT '支付框架商户号，对应 base_merchant_info.merchant_id',
  `user_id` bigint DEFAULT NULL COMMENT '用户主体ID，对应 sys_user.id',
  `account_id` bigint DEFAULT NULL COMMENT '登录账号ID，对应 sys_account.id',
  `login_account` varchar(100) DEFAULT NULL COMMENT '商户端登录账号',
  `real_name` varchar(100) DEFAULT NULL COMMENT '用户姓名',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_merchant_user_account_deleted` (`account_id`,`deleted`),
  KEY `idx_sys_merchant_user_mid` (`merchant_info_id`,`status`,`deleted`),
  KEY `idx_sys_merchant_user_merchant` (`merchant_id`,`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户端登录用户表';

-- ----------------------------
-- Table structure for sys_merchant_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_merchant_user_role`;
CREATE TABLE `sys_merchant_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `merchant_info_id` bigint NOT NULL COMMENT '商户主表ID，对应 base_merchant_info.id',
  `merchant_user_id` bigint NOT NULL COMMENT '商户端用户ID，对应 sys_merchant_user.id',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_merchant_user_role_deleted` (`app_id`,`merchant_user_id`,`role_id`,`deleted`),
  KEY `idx_sys_merchant_user_role_user` (`app_id`,`merchant_user_id`,`deleted`),
  KEY `idx_sys_merchant_user_role_merchant` (`merchant_info_id`,`role_id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户端用户角色关联表';

-- ----------------------------
-- Table structure for sys_mq_consume_record
-- ----------------------------
DROP TABLE IF EXISTS `sys_mq_consume_record`;
CREATE TABLE `sys_mq_consume_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `consumer_group` varchar(128) NOT NULL COMMENT '消费者组',
  `message_id` varchar(64) NOT NULL COMMENT '消息唯一编号',
  `topic` varchar(128) NOT NULL COMMENT '消息Topic',
  `consumed_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '业务落库完成时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_mq_consume_group_message` (`consumer_group`,`message_id`),
  KEY `idx_sys_mq_consume_time` (`consumed_time`)
) ENGINE=InnoDB AUTO_INCREMENT=141 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MQ消费数据库幂等记录表';

-- ----------------------------
-- Table structure for sys_mq_outbox
-- ----------------------------
DROP TABLE IF EXISTS `sys_mq_outbox`;
CREATE TABLE `sys_mq_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_id` varchar(64) NOT NULL COMMENT '消息唯一编号',
  `topic` varchar(128) NOT NULL COMMENT 'RocketMQ Topic',
  `tag` varchar(128) DEFAULT NULL COMMENT 'RocketMQ Tag',
  `producer_service` varchar(64) NOT NULL COMMENT '生产服务编码',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路追踪号',
  `payload_json` mediumtext NOT NULL COMMENT '已脱敏消息JSON快照',
  `event_status` varchar(16) NOT NULL DEFAULT 'INIT' COMMENT 'INIT、PROCESSING、RETRY_WAIT、SENT、CLOSED',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '已失败重试次数',
  `max_retry_count` int NOT NULL DEFAULT '8' COMMENT '最大失败重试次数',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下次允许重试时间',
  `processing_started_time` datetime(3) DEFAULT NULL COMMENT '本次投递抢占时间',
  `sent_time` datetime(3) DEFAULT NULL COMMENT '投递成功时间',
  `failure_reason` varchar(512) DEFAULT NULL COMMENT '最近失败原因摘要',
  `version` int NOT NULL DEFAULT '0' COMMENT 'CAS版本号',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_mq_outbox_event` (`event_id`),
  KEY `idx_sys_mq_outbox_due` (`event_status`,`next_retry_time`,`create_time`,`id`),
  KEY `idx_sys_mq_outbox_processing` (`event_status`,`processing_started_time`)
) ENGINE=InnoDB AUTO_INCREMENT=5930 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='非交易可靠MQ本地消息表';

-- ----------------------------
-- Table structure for sys_notice
-- ----------------------------
DROP TABLE IF EXISTS `sys_notice`;
CREATE TABLE `sys_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notice_title` varchar(200) NOT NULL COMMENT '通知标题',
  `notice_type` varchar(10) NOT NULL DEFAULT '1' COMMENT '类型：1通知，2公告',
  `notice_content` text COMMENT '内容',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知公告表';

-- ----------------------------
-- Table structure for sys_oper_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_oper_log`;
CREATE TABLE `sys_oper_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路追踪ID',
  `request_id` varchar(64) DEFAULT NULL COMMENT '请求ID',
  `message_id` varchar(64) DEFAULT NULL COMMENT 'MQ消息唯一标识',
  `idempotent_key` varchar(255) DEFAULT NULL COMMENT '消费幂等键',
  `system_code` varchar(32) DEFAULT NULL COMMENT '系统编码，区分 ADMIN 和 MERCHANT',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，后台操作涉及商户时记录',
  `module_name` varchar(100) DEFAULT NULL COMMENT '模块名称，如商户管理、费率管理、系统配置',
  `operation_name` varchar(100) DEFAULT NULL COMMENT '操作名称',
  `business_type` tinyint DEFAULT NULL COMMENT '业务类型：1新增，2修改，3删除，4查询，5导出，6审核，7冻结，8解冻',
  `method_name` varchar(255) DEFAULT NULL COMMENT '后端方法名称',
  `request_method` varchar(20) DEFAULT NULL COMMENT '请求方式：GET、POST、PUT、DELETE',
  `operator_type` tinyint NOT NULL DEFAULT '1' COMMENT '操作人类别：1后台用户，2商户用户，3系统任务',
  `operator_id` varchar(64) DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人名称',
  `oper_url` varchar(500) DEFAULT NULL COMMENT '请求URL',
  `oper_ip` varchar(45) DEFAULT NULL COMMENT '操作IP，支持IPv4/IPv6',
  `oper_location` varchar(255) DEFAULT NULL COMMENT '操作地点',
  `store_id` varchar(64) DEFAULT NULL COMMENT '店铺号',
  `user_agent` varchar(512) DEFAULT NULL COMMENT '浏览器User-Agent',
  `request_param` text COMMENT '脱敏后的请求参数，禁止记录密钥、卡号、CVV、JWT明文',
  `response_result` text COMMENT '脱敏后的响应结果',
  `cost_time` bigint DEFAULT NULL COMMENT '执行时长，单位毫秒',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '操作状态：0失败，1成功',
  `error_code` varchar(32) DEFAULT NULL COMMENT '错误码',
  `error_msg` varchar(1000) DEFAULT NULL COMMENT '错误信息，禁止写入堆栈明文',
  `operated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_oper_idempotent_key` (`idempotent_key`),
  KEY `idx_sys_oper_trace_id` (`trace_id`),
  KEY `idx_sys_oper_request_id` (`request_id`),
  KEY `idx_sys_oper_merchant_time` (`merchant_id`,`operated_at`),
  KEY `idx_sys_oper_operator_time` (`operator_id`,`operated_at`),
  KEY `idx_sys_oper_time_status` (`operated_at`,`status`),
  KEY `idx_sys_oper_business_type` (`business_type`),
  KEY `idx_sys_oper_message_id` (`message_id`)
) ENGINE=InnoDB AUTO_INCREMENT=53665 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统后台操作日志表';

-- ----------------------------
-- Table structure for sys_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `menu_id` bigint DEFAULT NULL COMMENT '归属菜单ID，可为空',
  `permission_code` varchar(150) NOT NULL COMMENT '权限编码，如 merchant:user:create',
  `permission_name` varchar(100) NOT NULL COMMENT '权限名称',
  `permission_type` varchar(30) NOT NULL COMMENT '权限类型：MENU菜单，BUTTON按钮，API接口，DATA数据',
  `resource_method` varchar(20) DEFAULT NULL COMMENT '接口请求方法',
  `resource_path` varchar(255) DEFAULT NULL COMMENT '接口资源路径',
  `description` varchar(500) DEFAULT NULL COMMENT '权限说明',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_permission_app_code_deleted` (`app_id`,`permission_code`,`deleted`),
  KEY `idx_sys_permission_app_menu` (`app_id`,`menu_id`,`status`,`deleted`),
  KEY `idx_sys_permission_resource` (`resource_method`,`resource_path`)
) ENGINE=InnoDB AUTO_INCREMENT=2322 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限资源表';

-- ----------------------------
-- Table structure for sys_post
-- ----------------------------
DROP TABLE IF EXISTS `sys_post`;
CREATE TABLE `sys_post` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `post_code` varchar(80) NOT NULL COMMENT '岗位编码',
  `post_name` varchar(100) NOT NULL COMMENT '岗位名称',
  `sort_no` int NOT NULL DEFAULT '100' COMMENT '显示排序',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_post_app_code_deleted` (`app_id`,`post_code`,`deleted`),
  KEY `idx_sys_post_deleted` (`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位管理表';

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `role_code` varchar(80) NOT NULL COMMENT '角色编码',
  `role_name` varchar(100) NOT NULL COMMENT '角色名称',
  `merchant_id` varchar(64) DEFAULT NULL COMMENT '商户号，商户系统角色必填，平台角色为空',
  `role_type` varchar(30) NOT NULL DEFAULT 'CUSTOM' COMMENT '角色类型：SYSTEM系统内置，CUSTOM自定义',
  `data_scope` varchar(30) NOT NULL DEFAULT 'SELF' COMMENT '数据范围：ALL全部，SELF本人，CUSTOM自定义',
  `description` varchar(500) DEFAULT NULL COMMENT '角色说明',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_app_code_deleted` (`app_id`,`role_code`,`deleted`),
  KEY `idx_sys_role_app_status` (`app_id`,`status`,`deleted`),
  KEY `idx_sys_role_merchant` (`merchant_id`,`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

-- ----------------------------
-- Table structure for sys_role_data_scope
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_data_scope`;
CREATE TABLE `sys_role_data_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `scope_type` varchar(30) NOT NULL COMMENT '数据范围类型：ORG机构，MERCHANT商户，STORE门店，CHANNEL渠道，CUSTOM自定义',
  `scope_value` varchar(100) NOT NULL COMMENT '数据范围值',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_sys_role_scope_role` (`app_id`,`role_id`,`scope_type`,`deleted`),
  KEY `idx_sys_role_scope_value` (`scope_type`,`scope_value`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=76 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色数据权限范围表';

-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_menu_deleted` (`app_id`,`role_id`,`menu_id`,`deleted`),
  KEY `idx_sys_role_menu_role` (`app_id`,`role_id`,`deleted`),
  KEY `idx_sys_role_menu_menu` (`app_id`,`menu_id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=8170 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色菜单关联表';

-- ----------------------------
-- Table structure for sys_role_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_permission_deleted` (`app_id`,`role_id`,`permission_id`,`deleted`),
  KEY `idx_sys_role_permission_role` (`app_id`,`role_id`,`deleted`),
  KEY `idx_sys_role_permission_permission` (`app_id`,`permission_id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=4599 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';

-- ----------------------------
-- Table structure for sys_sharding_physical_table
-- ----------------------------
DROP TABLE IF EXISTS `sys_sharding_physical_table`;
CREATE TABLE `sys_sharding_physical_table` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `logical_table` varchar(64) NOT NULL COMMENT '逻辑表名',
  `template_table` varchar(64) NOT NULL COMMENT '模板表名',
  `physical_table` varchar(128) NOT NULL COMMENT '物理表名',
  `sharding_column` varchar(64) NOT NULL COMMENT '分表字段',
  `strategy` varchar(32) NOT NULL COMMENT '分表策略',
  `year` int NOT NULL COMMENT '年份',
  `quarter` tinyint NOT NULL COMMENT '季度：1-4',
  `quarter_suffix` varchar(6) NOT NULL COMMENT '季度后缀，例如202602',
  `data_source` varchar(64) NOT NULL COMMENT '数据源',
  `table_status` varchar(32) NOT NULL COMMENT '表状态：EXISTS/CREATED/MISSING/FAILED',
  `auto_created` tinyint NOT NULL DEFAULT '0' COMMENT '是否自动创建：0=否，1=是',
  `auto_increment_start` bigint DEFAULT NULL COMMENT 'AUTO_INCREMENT起始值',
  `auto_increment_current` bigint DEFAULT NULL COMMENT 'AUTO_INCREMENT当前值',
  `auto_increment_max` bigint DEFAULT NULL COMMENT '当前季度最大安全值',
  `schema_check_status` varchar(32) DEFAULT NULL COMMENT '结构校验状态：MATCHED/MISMATCHED/SKIPPED/FAILED',
  `last_check_time` datetime DEFAULT NULL COMMENT '最后检查时间',
  `created_time` datetime DEFAULT NULL COMMENT '物理表创建时间',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '失败原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_physical_table` (`physical_table`),
  KEY `idx_logic_year_quarter` (`logical_table`,`year`,`quarter`),
  KEY `idx_table_status` (`table_status`),
  KEY `idx_last_check_time` (`last_check_time`)
) ENGINE=InnoDB AUTO_INCREMENT=63 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分表物理表登记表';

-- ----------------------------
-- Table structure for sys_sharding_table_create_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_sharding_table_create_log`;
CREATE TABLE `sys_sharding_table_create_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_no` varchar(64) NOT NULL COMMENT '任务批次号',
  `trigger_type` varchar(32) NOT NULL COMMENT '触发方式：SCHEDULE/MANUAL',
  `dry_run` tinyint NOT NULL DEFAULT '0' COMMENT '是否仅预演：0=否，1=是',
  `target_quarters` varchar(255) NOT NULL COMMENT '目标季度，例如2026-Q2,2026-Q3',
  `planned_count` int NOT NULL DEFAULT '0' COMMENT '计划处理数量',
  `created_count` int NOT NULL DEFAULT '0' COMMENT '创建数量',
  `skipped_count` int NOT NULL DEFAULT '0' COMMENT '跳过数量',
  `failed_count` int NOT NULL DEFAULT '0' COMMENT '失败数量',
  `schema_mismatch_count` int NOT NULL DEFAULT '0' COMMENT '结构不一致数量',
  `run_status` varchar(32) NOT NULL COMMENT '执行状态：SUCCESS/PARTIAL_FAILED/FAILED',
  `result_summary` text COMMENT '执行摘要JSON',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '失败原因',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时毫秒',
  `operator_id` varchar(64) DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人名称',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_run_status` (`run_status`)
) ENGINE=InnoDB AUTO_INCREMENT=56 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分表建表任务日志表';

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_type` varchar(30) NOT NULL COMMENT '用户类型：PLATFORM平台用户，MERCHANT商户用户',
  `real_name` varchar(100) DEFAULT NULL COMMENT '真实姓名',
  `dept_id` bigint DEFAULT NULL COMMENT '所属部门ID',
  `nickname` varchar(100) DEFAULT NULL COMMENT '昵称',
  `mobile` varchar(30) DEFAULT NULL COMMENT '主体手机号',
  `email` varchar(150) NOT NULL COMMENT '邮箱',
  `avatar_url` varchar(500) DEFAULT NULL COMMENT '头像地址',
  `country_code` varchar(10) DEFAULT NULL COMMENT '国家地区编码',
  `language` varchar(20) DEFAULT NULL COMMENT '用户语言，如 zh-CN、en-US',
  `timezone` varchar(50) DEFAULT NULL COMMENT '用户时区',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `updated_by` bigint DEFAULT NULL COMMENT '修改人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_sys_user_type_status` (`user_type`,`status`,`deleted`),
  KEY `idx_sys_user_mobile` (`mobile`),
  KEY `idx_sys_user_email` (`email`),
  KEY `idx_sys_user_dept_id` (`dept_id`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户主体表';

-- ----------------------------
-- Table structure for sys_user_post
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_post`;
CREATE TABLE `sys_user_post` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户主体ID',
  `post_id` bigint NOT NULL COMMENT '岗位ID',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_post` (`user_id`,`post_id`),
  KEY `idx_sys_user_post_post_id` (`post_id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户与岗位关联表';

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `user_id` bigint NOT NULL COMMENT '用户主体ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_role_deleted` (`app_id`,`user_id`,`role_id`,`deleted`),
  KEY `idx_sys_user_role_user` (`app_id`,`user_id`,`deleted`),
  KEY `idx_sys_user_role_role` (`app_id`,`role_id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户主体角色关联表';

-- ----------------------------
-- Table structure for sys_verify_code
-- ----------------------------
DROP TABLE IF EXISTS `sys_verify_code`;
CREATE TABLE `sys_verify_code` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `scene` varchar(50) NOT NULL COMMENT '验证码场景：LOGIN登录，REGISTER注册，RESET_PASSWORD重置密码',
  `receiver_type` varchar(20) NOT NULL COMMENT '接收方式：SMS短信，EMAIL邮箱，TOTP身份验证器',
  `receiver` varchar(150) NOT NULL COMMENT '接收人手机号或邮箱',
  `code_hash` varchar(255) NOT NULL COMMENT '验证码哈希值，不保存明文验证码',
  `code_salt` varchar(100) DEFAULT NULL COMMENT '验证码盐值',
  `expire_at` datetime(3) NOT NULL COMMENT '过期时间',
  `used` tinyint NOT NULL DEFAULT '0' COMMENT '是否已使用：0否，1是',
  `used_at` datetime(3) DEFAULT NULL COMMENT '使用时间',
  `verify_count` int NOT NULL DEFAULT '0' COMMENT '验证次数',
  `send_ip` varchar(64) DEFAULT NULL COMMENT '发送请求IP',
  `send_channel` varchar(50) DEFAULT NULL COMMENT '发送渠道',
  `send_status` tinyint NOT NULL DEFAULT '1' COMMENT '发送状态：0失败，1成功',
  `send_fail_reason` varchar(500) DEFAULT NULL COMMENT '发送失败原因',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_sys_verify_app_scene_receiver` (`app_id`,`scene`,`receiver`),
  KEY `idx_sys_verify_expire_used` (`expire_at`,`used`)
) ENGINE=InnoDB AUTO_INCREMENT=1244 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态验证码表';

-- ----------------------------
-- Table structure for transaction_abnormal_event
-- ----------------------------
DROP TABLE IF EXISTS `transaction_abnormal_event`;
CREATE TABLE `transaction_abnormal_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `abnormal_event_id` varchar(64) NOT NULL COMMENT '异常事件ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `abnormal_type` varchar(64) NOT NULL COMMENT '异常类型，如 STATE_CONFLICT、AMOUNT_MISMATCH、CALLBACK_UNHANDLED、CHANNEL_RESULT_MISMATCH。',
  `abnormal_level` varchar(32) NOT NULL COMMENT 'LOW/MEDIUM/HIGH/CRITICAL',
  `event_status` varchar(32) NOT NULL COMMENT '异常状态：OPEN、PROCESSING、RESOLVED、IGNORED。',
  `source_record_type` varchar(128) DEFAULT NULL COMMENT '异常来源记录类型',
  `source_record_id` varchar(64) DEFAULT NULL COMMENT '异常来源记录号',
  `abnormal_description` varchar(1024) DEFAULT NULL COMMENT '异常描述',
  `raw_reference_json` json DEFAULT NULL COMMENT '异常排查引用摘要。',
  `first_seen_time` datetime(3) NOT NULL COMMENT '首次发现时间。',
  `resolved_time` datetime(3) DEFAULT NULL COMMENT '解决时间。',
  `resolved_by` varchar(128) DEFAULT NULL COMMENT '处理人或处理任务。',
  `resolution` varchar(1024) DEFAULT NULL COMMENT '处理结果说明。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  `deduplication_key` varchar(128) DEFAULT NULL COMMENT '异常去重键',
  `merchant_id` varchar(64) DEFAULT NULL COMMENT '商户号',
  `merchant_order_no` varchar(128) DEFAULT NULL COMMENT '商户订单号快照',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '源交易号',
  `source_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '源动作分片时间',
  `root_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '主单分片时间',
  `transaction_type` varchar(32) DEFAULT NULL COMMENT '交易类型',
  `platform_status` varchar(32) DEFAULT NULL COMMENT '平台状态快照',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道订单号',
  `channel_transaction_id` varchar(128) DEFAULT NULL COMMENT '渠道交易号',
  `channel_status` varchar(64) DEFAULT NULL COMMENT '渠道状态摘要',
  `channel_match_result` varchar(64) DEFAULT NULL COMMENT '勾兑结果摘要',
  `detect_source` varchar(32) DEFAULT NULL COMMENT '发现来源',
  `platform_currency` char(3) DEFAULT NULL COMMENT '平台币种',
  `platform_amount` decimal(20,6) DEFAULT NULL COMMENT '平台金额',
  `channel_currency` char(3) DEFAULT NULL COMMENT '渠道币种',
  `channel_amount` decimal(20,6) DEFAULT NULL COMMENT '渠道金额',
  `amount_difference` decimal(20,6) DEFAULT NULL COMMENT '同币种渠道减平台差额',
  `currency_exponent` tinyint DEFAULT NULL COMMENT '币种精度',
  `last_seen_time` datetime(3) DEFAULT NULL COMMENT '最近发现时间',
  `occurrence_count` int NOT NULL DEFAULT '1' COMMENT '发生次数',
  `assigned_to_id` varchar(128) DEFAULT NULL COMMENT '处理账号',
  `assigned_to_name` varchar(128) DEFAULT NULL COMMENT '处理人快照',
  `assigned_time` datetime(3) DEFAULT NULL COMMENT '分派时间',
  `resolution_type` varchar(64) DEFAULT NULL COMMENT '处置类型',
  `resolution_reference_id` varchar(64) DEFAULT NULL COMMENT '处置引用',
  `merchant_notify_required` tinyint NOT NULL DEFAULT '0' COMMENT '是否需要通知商户',
  `version` int NOT NULL DEFAULT '0' COMMENT 'CAS 版本',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_abnormal_event_id` (`abnormal_event_id`),
  UNIQUE KEY `uk_abnormal_deduplication` (`deduplication_key`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_status_time` (`event_status`,`first_seen_time`),
  KEY `idx_type_time` (`abnormal_type`,`first_seen_time`),
  KEY `idx_abnormal_status_time` (`event_status`,`first_seen_time`,`id`),
  KEY `idx_abnormal_merchant_status_time` (`merchant_id`,`event_status`,`first_seen_time`,`id`),
  KEY `idx_abnormal_channel_type_time` (`channel_code`,`abnormal_type`,`transaction_date_time`,`id`),
  KEY `idx_abnormal_transaction_time` (`transaction_id`,`transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存已能定位交易时间的交易异常事件，如状态推进失败、金额校验失败、回调无法处理等。';

-- ----------------------------
-- Table structure for transaction_abnormal_event_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_abnormal_event_202603`;
CREATE TABLE `transaction_abnormal_event_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `abnormal_event_id` varchar(64) NOT NULL COMMENT '异常事件ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `abnormal_type` varchar(64) NOT NULL COMMENT '异常类型，如 STATE_CONFLICT、AMOUNT_MISMATCH、CALLBACK_UNHANDLED、CHANNEL_RESULT_MISMATCH。',
  `abnormal_level` varchar(32) NOT NULL COMMENT 'LOW/MEDIUM/HIGH/CRITICAL',
  `event_status` varchar(32) NOT NULL COMMENT '异常状态：OPEN、PROCESSING、RESOLVED、IGNORED。',
  `source_record_type` varchar(128) DEFAULT NULL COMMENT '异常来源记录类型',
  `source_record_id` varchar(64) DEFAULT NULL COMMENT '异常来源记录号',
  `abnormal_description` varchar(1024) DEFAULT NULL COMMENT '异常描述',
  `raw_reference_json` json DEFAULT NULL COMMENT '异常排查引用摘要。',
  `first_seen_time` datetime(3) NOT NULL COMMENT '首次发现时间。',
  `resolved_time` datetime(3) DEFAULT NULL COMMENT '解决时间。',
  `resolved_by` varchar(128) DEFAULT NULL COMMENT '处理人或处理任务。',
  `resolution` varchar(1024) DEFAULT NULL COMMENT '处理结果说明。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  `deduplication_key` varchar(128) DEFAULT NULL COMMENT '异常去重键',
  `merchant_id` varchar(64) DEFAULT NULL COMMENT '商户号',
  `merchant_order_no` varchar(128) DEFAULT NULL COMMENT '商户订单号快照',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '源交易号',
  `source_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '源动作分片时间',
  `root_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '主单分片时间',
  `transaction_type` varchar(32) DEFAULT NULL COMMENT '交易类型',
  `platform_status` varchar(32) DEFAULT NULL COMMENT '平台状态快照',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道订单号',
  `channel_transaction_id` varchar(128) DEFAULT NULL COMMENT '渠道交易号',
  `channel_status` varchar(64) DEFAULT NULL COMMENT '渠道状态摘要',
  `channel_match_result` varchar(64) DEFAULT NULL COMMENT '勾兑结果摘要',
  `detect_source` varchar(32) DEFAULT NULL COMMENT '发现来源',
  `platform_currency` char(3) DEFAULT NULL COMMENT '平台币种',
  `platform_amount` decimal(20,6) DEFAULT NULL COMMENT '平台金额',
  `channel_currency` char(3) DEFAULT NULL COMMENT '渠道币种',
  `channel_amount` decimal(20,6) DEFAULT NULL COMMENT '渠道金额',
  `amount_difference` decimal(20,6) DEFAULT NULL COMMENT '同币种渠道减平台差额',
  `currency_exponent` tinyint DEFAULT NULL COMMENT '币种精度',
  `last_seen_time` datetime(3) DEFAULT NULL COMMENT '最近发现时间',
  `occurrence_count` int NOT NULL DEFAULT '1' COMMENT '发生次数',
  `assigned_to_id` varchar(128) DEFAULT NULL COMMENT '处理账号',
  `assigned_to_name` varchar(128) DEFAULT NULL COMMENT '处理人快照',
  `assigned_time` datetime(3) DEFAULT NULL COMMENT '分派时间',
  `resolution_type` varchar(64) DEFAULT NULL COMMENT '处置类型',
  `resolution_reference_id` varchar(64) DEFAULT NULL COMMENT '处置引用',
  `merchant_notify_required` tinyint NOT NULL DEFAULT '0' COMMENT '是否需要通知商户',
  `version` int NOT NULL DEFAULT '0' COMMENT 'CAS 版本',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_abnormal_event_id` (`abnormal_event_id`),
  UNIQUE KEY `uk_abnormal_deduplication` (`deduplication_key`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_status_time` (`event_status`,`first_seen_time`),
  KEY `idx_type_time` (`abnormal_type`,`first_seen_time`),
  KEY `idx_abnormal_status_time` (`event_status`,`first_seen_time`,`id`),
  KEY `idx_abnormal_merchant_status_time` (`merchant_id`,`event_status`,`first_seen_time`,`id`),
  KEY `idx_abnormal_channel_type_time` (`channel_code`,`abnormal_type`,`transaction_date_time`,`id`),
  KEY `idx_abnormal_transaction_time` (`transaction_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存已能定位交易时间的交易异常事件，如状态推进失败、金额校验失败、回调无法处理等。';

-- ----------------------------
-- Table structure for transaction_abnormal_event_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_abnormal_event_202604`;
CREATE TABLE `transaction_abnormal_event_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `abnormal_event_id` varchar(64) NOT NULL COMMENT '异常事件ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `abnormal_type` varchar(64) NOT NULL COMMENT '异常类型，如 STATE_CONFLICT、AMOUNT_MISMATCH、CALLBACK_UNHANDLED、CHANNEL_RESULT_MISMATCH。',
  `abnormal_level` varchar(32) NOT NULL COMMENT 'LOW/MEDIUM/HIGH/CRITICAL',
  `event_status` varchar(32) NOT NULL COMMENT '异常状态：OPEN、PROCESSING、RESOLVED、IGNORED。',
  `source_record_type` varchar(128) DEFAULT NULL COMMENT '异常来源记录类型',
  `source_record_id` varchar(64) DEFAULT NULL COMMENT '异常来源记录号',
  `abnormal_description` varchar(1024) DEFAULT NULL COMMENT '异常描述',
  `raw_reference_json` json DEFAULT NULL COMMENT '异常排查引用摘要。',
  `first_seen_time` datetime(3) NOT NULL COMMENT '首次发现时间。',
  `resolved_time` datetime(3) DEFAULT NULL COMMENT '解决时间。',
  `resolved_by` varchar(128) DEFAULT NULL COMMENT '处理人或处理任务。',
  `resolution` varchar(1024) DEFAULT NULL COMMENT '处理结果说明。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  `deduplication_key` varchar(128) DEFAULT NULL COMMENT '异常去重键',
  `merchant_id` varchar(64) DEFAULT NULL COMMENT '商户号',
  `merchant_order_no` varchar(128) DEFAULT NULL COMMENT '商户订单号快照',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '源交易号',
  `source_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '源动作分片时间',
  `root_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '主单分片时间',
  `transaction_type` varchar(32) DEFAULT NULL COMMENT '交易类型',
  `platform_status` varchar(32) DEFAULT NULL COMMENT '平台状态快照',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道订单号',
  `channel_transaction_id` varchar(128) DEFAULT NULL COMMENT '渠道交易号',
  `channel_status` varchar(64) DEFAULT NULL COMMENT '渠道状态摘要',
  `channel_match_result` varchar(64) DEFAULT NULL COMMENT '勾兑结果摘要',
  `detect_source` varchar(32) DEFAULT NULL COMMENT '发现来源',
  `platform_currency` char(3) DEFAULT NULL COMMENT '平台币种',
  `platform_amount` decimal(20,6) DEFAULT NULL COMMENT '平台金额',
  `channel_currency` char(3) DEFAULT NULL COMMENT '渠道币种',
  `channel_amount` decimal(20,6) DEFAULT NULL COMMENT '渠道金额',
  `amount_difference` decimal(20,6) DEFAULT NULL COMMENT '同币种渠道减平台差额',
  `currency_exponent` tinyint DEFAULT NULL COMMENT '币种精度',
  `last_seen_time` datetime(3) DEFAULT NULL COMMENT '最近发现时间',
  `occurrence_count` int NOT NULL DEFAULT '1' COMMENT '发生次数',
  `assigned_to_id` varchar(128) DEFAULT NULL COMMENT '处理账号',
  `assigned_to_name` varchar(128) DEFAULT NULL COMMENT '处理人快照',
  `assigned_time` datetime(3) DEFAULT NULL COMMENT '分派时间',
  `resolution_type` varchar(64) DEFAULT NULL COMMENT '处置类型',
  `resolution_reference_id` varchar(64) DEFAULT NULL COMMENT '处置引用',
  `merchant_notify_required` tinyint NOT NULL DEFAULT '0' COMMENT '是否需要通知商户',
  `version` int NOT NULL DEFAULT '0' COMMENT 'CAS 版本',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_abnormal_event_id` (`abnormal_event_id`),
  UNIQUE KEY `uk_abnormal_deduplication` (`deduplication_key`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_status_time` (`event_status`,`first_seen_time`),
  KEY `idx_type_time` (`abnormal_type`,`first_seen_time`),
  KEY `idx_abnormal_status_time` (`event_status`,`first_seen_time`,`id`),
  KEY `idx_abnormal_merchant_status_time` (`merchant_id`,`event_status`,`first_seen_time`,`id`),
  KEY `idx_abnormal_channel_type_time` (`channel_code`,`abnormal_type`,`transaction_date_time`,`id`),
  KEY `idx_abnormal_transaction_time` (`transaction_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存已能定位交易时间的交易异常事件，如状态推进失败、金额校验失败、回调无法处理等。';

-- ----------------------------
-- Table structure for transaction_additional_info
-- ----------------------------
DROP TABLE IF EXISTS `transaction_additional_info`;
CREATE TABLE `transaction_additional_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `additional_id` varchar(64) NOT NULL COMMENT '附属信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `recurring_type` varchar(32) DEFAULT NULL COMMENT '循环付款类型。',
  `installment_count` int DEFAULT NULL COMMENT '分期期数。',
  `auth_incremental_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否增量授权。',
  `auth_valid_until` datetime(3) DEFAULT NULL COMMENT '授权有效截止时间。',
  `checkout_session_id` varchar(128) DEFAULT NULL COMMENT 'Hosted Checkout 会话ID。',
  `payment_link_id` varchar(128) DEFAULT NULL COMMENT '支付链接ID。',
  `qr_code_id` varchar(128) DEFAULT NULL COMMENT '二维码支付ID。',
  `browser_info_json` json DEFAULT NULL COMMENT '浏览器摘要信息。',
  `order_extra_json` json DEFAULT NULL COMMENT '商户订单扩展信息。',
  `channel_extra_json` json DEFAULT NULL COMMENT '渠道扩展摘要。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_additional_id` (`additional_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_checkout_session` (`checkout_session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存循环付款、分期、Hosted Checkout、二维码、支付链接等扩展属性，避免主表字段膨胀。';

-- ----------------------------
-- Table structure for transaction_additional_info_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_additional_info_202603`;
CREATE TABLE `transaction_additional_info_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `additional_id` varchar(64) NOT NULL COMMENT '附属信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `recurring_type` varchar(32) DEFAULT NULL COMMENT '循环付款类型。',
  `installment_count` int DEFAULT NULL COMMENT '分期期数。',
  `auth_incremental_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否增量授权。',
  `auth_valid_until` datetime(3) DEFAULT NULL COMMENT '授权有效截止时间。',
  `checkout_session_id` varchar(128) DEFAULT NULL COMMENT 'Hosted Checkout 会话ID。',
  `payment_link_id` varchar(128) DEFAULT NULL COMMENT '支付链接ID。',
  `qr_code_id` varchar(128) DEFAULT NULL COMMENT '二维码支付ID。',
  `browser_info_json` json DEFAULT NULL COMMENT '浏览器摘要信息。',
  `order_extra_json` json DEFAULT NULL COMMENT '商户订单扩展信息。',
  `channel_extra_json` json DEFAULT NULL COMMENT '渠道扩展摘要。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_additional_id` (`additional_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_checkout_session` (`checkout_session_id`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存循环付款、分期、Hosted Checkout、二维码、支付链接等扩展属性，避免主表字段膨胀。';

-- ----------------------------
-- Table structure for transaction_additional_info_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_additional_info_202604`;
CREATE TABLE `transaction_additional_info_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `additional_id` varchar(64) NOT NULL COMMENT '附属信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `recurring_type` varchar(32) DEFAULT NULL COMMENT '循环付款类型。',
  `installment_count` int DEFAULT NULL COMMENT '分期期数。',
  `auth_incremental_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否增量授权。',
  `auth_valid_until` datetime(3) DEFAULT NULL COMMENT '授权有效截止时间。',
  `checkout_session_id` varchar(128) DEFAULT NULL COMMENT 'Hosted Checkout 会话ID。',
  `payment_link_id` varchar(128) DEFAULT NULL COMMENT '支付链接ID。',
  `qr_code_id` varchar(128) DEFAULT NULL COMMENT '二维码支付ID。',
  `browser_info_json` json DEFAULT NULL COMMENT '浏览器摘要信息。',
  `order_extra_json` json DEFAULT NULL COMMENT '商户订单扩展信息。',
  `channel_extra_json` json DEFAULT NULL COMMENT '渠道扩展摘要。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_additional_id` (`additional_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_checkout_session` (`checkout_session_id`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存循环付款、分期、Hosted Checkout、二维码、支付链接等扩展属性，避免主表字段膨胀。';

-- ----------------------------
-- Table structure for transaction_amount_change_log
-- ----------------------------
DROP TABLE IF EXISTS `transaction_amount_change_log`;
CREATE TABLE `transaction_amount_change_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `amount_change_id` varchar(64) NOT NULL COMMENT '金额变动ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) NOT NULL COMMENT '触发金额变动的交易动作ID。',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '被影响或被引用的源平台交易ID。',
  `source_operation_id` varchar(64) DEFAULT NULL COMMENT '被影响或被引用的源动作ID。',
  `change_type` varchar(64) NOT NULL COMMENT '变动类型：AUTH_INCREASE、CAPTURE、REFUND、VOID、CHARGEBACK。',
  `amount_currency` char(3) NOT NULL COMMENT '金额币种。',
  `change_amount` decimal(20,6) NOT NULL COMMENT '本次变动金额，可正可负。',
  `authorized_before` decimal(20,6) NOT NULL COMMENT '变动前累计授权金额。',
  `authorized_after` decimal(20,6) NOT NULL COMMENT '变动后累计授权金额。',
  `captured_before` decimal(20,6) NOT NULL COMMENT '变动前累计请款金额。',
  `captured_after` decimal(20,6) NOT NULL COMMENT '变动后累计请款金额。',
  `refunded_before` decimal(20,6) NOT NULL COMMENT '变动前累计退款金额。',
  `refunded_after` decimal(20,6) NOT NULL COMMENT '变动后累计退款金额。',
  `available_capture_before` decimal(20,6) NOT NULL COMMENT '变动前可请款金额。',
  `available_capture_after` decimal(20,6) NOT NULL COMMENT '变动后可请款金额。',
  `available_refund_before` decimal(20,6) NOT NULL COMMENT '变动前可退款金额。',
  `available_refund_after` decimal(20,6) NOT NULL COMMENT '变动后可退款金额。',
  `change_reason` varchar(512) DEFAULT NULL COMMENT '金额变动原因。',
  `change_time` datetime(3) NOT NULL COMMENT '金额变动时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_amount_change_id` (`amount_change_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`,`change_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_source_transaction` (`source_transaction_id`,`transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录授权、请款、退款、拒付等动作对订单累计金额的影响。';

-- ----------------------------
-- Table structure for transaction_amount_change_log_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_amount_change_log_202603`;
CREATE TABLE `transaction_amount_change_log_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `amount_change_id` varchar(64) NOT NULL COMMENT '金额变动ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) NOT NULL COMMENT '触发金额变动的交易动作ID。',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '被影响或被引用的源平台交易ID。',
  `source_operation_id` varchar(64) DEFAULT NULL COMMENT '被影响或被引用的源动作ID。',
  `change_type` varchar(64) NOT NULL COMMENT '变动类型：AUTH_INCREASE、CAPTURE、REFUND、VOID、CHARGEBACK。',
  `amount_currency` char(3) NOT NULL COMMENT '金额币种。',
  `change_amount` decimal(20,6) NOT NULL COMMENT '本次变动金额，可正可负。',
  `authorized_before` decimal(20,6) NOT NULL COMMENT '变动前累计授权金额。',
  `authorized_after` decimal(20,6) NOT NULL COMMENT '变动后累计授权金额。',
  `captured_before` decimal(20,6) NOT NULL COMMENT '变动前累计请款金额。',
  `captured_after` decimal(20,6) NOT NULL COMMENT '变动后累计请款金额。',
  `refunded_before` decimal(20,6) NOT NULL COMMENT '变动前累计退款金额。',
  `refunded_after` decimal(20,6) NOT NULL COMMENT '变动后累计退款金额。',
  `available_capture_before` decimal(20,6) NOT NULL COMMENT '变动前可请款金额。',
  `available_capture_after` decimal(20,6) NOT NULL COMMENT '变动后可请款金额。',
  `available_refund_before` decimal(20,6) NOT NULL COMMENT '变动前可退款金额。',
  `available_refund_after` decimal(20,6) NOT NULL COMMENT '变动后可退款金额。',
  `change_reason` varchar(512) DEFAULT NULL COMMENT '金额变动原因。',
  `change_time` datetime(3) NOT NULL COMMENT '金额变动时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_amount_change_id` (`amount_change_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`,`change_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_source_transaction` (`source_transaction_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000231 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录授权、请款、退款、拒付等动作对订单累计金额的影响。';

-- ----------------------------
-- Table structure for transaction_amount_change_log_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_amount_change_log_202604`;
CREATE TABLE `transaction_amount_change_log_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `amount_change_id` varchar(64) NOT NULL COMMENT '金额变动ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) NOT NULL COMMENT '触发金额变动的交易动作ID。',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '被影响或被引用的源平台交易ID。',
  `source_operation_id` varchar(64) DEFAULT NULL COMMENT '被影响或被引用的源动作ID。',
  `change_type` varchar(64) NOT NULL COMMENT '变动类型：AUTH_INCREASE、CAPTURE、REFUND、VOID、CHARGEBACK。',
  `amount_currency` char(3) NOT NULL COMMENT '金额币种。',
  `change_amount` decimal(20,6) NOT NULL COMMENT '本次变动金额，可正可负。',
  `authorized_before` decimal(20,6) NOT NULL COMMENT '变动前累计授权金额。',
  `authorized_after` decimal(20,6) NOT NULL COMMENT '变动后累计授权金额。',
  `captured_before` decimal(20,6) NOT NULL COMMENT '变动前累计请款金额。',
  `captured_after` decimal(20,6) NOT NULL COMMENT '变动后累计请款金额。',
  `refunded_before` decimal(20,6) NOT NULL COMMENT '变动前累计退款金额。',
  `refunded_after` decimal(20,6) NOT NULL COMMENT '变动后累计退款金额。',
  `available_capture_before` decimal(20,6) NOT NULL COMMENT '变动前可请款金额。',
  `available_capture_after` decimal(20,6) NOT NULL COMMENT '变动后可请款金额。',
  `available_refund_before` decimal(20,6) NOT NULL COMMENT '变动前可退款金额。',
  `available_refund_after` decimal(20,6) NOT NULL COMMENT '变动后可退款金额。',
  `change_reason` varchar(512) DEFAULT NULL COMMENT '金额变动原因。',
  `change_time` datetime(3) NOT NULL COMMENT '金额变动时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_amount_change_id` (`amount_change_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`,`change_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_source_transaction` (`source_transaction_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录授权、请款、退款、拒付等动作对订单累计金额的影响。';

-- ----------------------------
-- Table structure for transaction_authentication_info
-- ----------------------------
DROP TABLE IF EXISTS `transaction_authentication_info`;
CREATE TABLE `transaction_authentication_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `authentication_info_id` varchar(64) NOT NULL COMMENT '认证信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `authentication_type` varchar(32) NOT NULL COMMENT '认证类型，如 3DS、SCA、NETWORK_TOKEN_AUTH。',
  `authentication_status` varchar(64) DEFAULT NULL COMMENT '平台归一后的认证状态，如 NOT_REQUIRED、AUTHENTICATED、FAILED、ATTEMPTED。',
  `authentication_source` varchar(64) DEFAULT NULL COMMENT '认证结果来源，如 CHANNEL、CARD_SCHEME、PLATFORM。',
  `three_ds_version` varchar(32) DEFAULT NULL COMMENT '3DS版本，如 1.0.2、2.2.0。',
  `three_ds_transaction_id` varchar(128) DEFAULT NULL COMMENT '3DS交易ID。',
  `three_ds_server_transaction_id` varchar(128) DEFAULT NULL COMMENT '3DS Server交易ID。',
  `acs_transaction_id` varchar(128) DEFAULT NULL COMMENT 'ACS交易ID。',
  `ds_transaction_id` varchar(128) DEFAULT NULL COMMENT 'Directory Server交易ID。',
  `eci` varchar(8) DEFAULT NULL COMMENT '电子商务交易指示值 ECI。',
  `cavv` varchar(256) DEFAULT NULL COMMENT 'CAVV/AAV认证值，按合规要求可脱敏或加密保存。',
  `xid` varchar(256) DEFAULT NULL COMMENT '3DS 1.x XID。',
  `liability_shift` tinyint DEFAULT NULL COMMENT '是否责任转移，0否，1是。',
  `challenge_required` tinyint DEFAULT NULL COMMENT '是否需要挑战认证，0否，1是。',
  `challenge_status` varchar(32) DEFAULT NULL COMMENT '挑战认证状态，如 REQUIRED、COMPLETED、FAILED、CANCELLED。',
  `authentication_redirect_url_hash` char(64) DEFAULT NULL COMMENT '认证跳转地址哈希，用于排查。',
  `authentication_result_code` varchar(64) DEFAULT NULL COMMENT '认证结果码。',
  `authentication_result_message` varchar(512) DEFAULT NULL COMMENT '认证结果描述。',
  `authentication_time` datetime(3) DEFAULT NULL COMMENT '认证完成或结果确认时间。',
  `authentication_extra_json` json DEFAULT NULL COMMENT '认证扩展摘要，保存渠道/卡组织非高频字段。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_authentication_info_id` (`authentication_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_three_ds_transaction` (`three_ds_transaction_id`,`transaction_date_time`),
  KEY `idx_auth_status_time` (`authentication_status`,`authentication_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存3DS、SCA、渠道/卡组织认证结果，支撑交易详情页认证信息展示和后续责任转移判断。';

-- ----------------------------
-- Table structure for transaction_authentication_info_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_authentication_info_202603`;
CREATE TABLE `transaction_authentication_info_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `authentication_info_id` varchar(64) NOT NULL COMMENT '认证信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `authentication_type` varchar(32) NOT NULL COMMENT '认证类型，如 3DS、SCA、NETWORK_TOKEN_AUTH。',
  `authentication_status` varchar(64) DEFAULT NULL COMMENT '平台归一后的认证状态，如 NOT_REQUIRED、AUTHENTICATED、FAILED、ATTEMPTED。',
  `authentication_source` varchar(64) DEFAULT NULL COMMENT '认证结果来源，如 CHANNEL、CARD_SCHEME、PLATFORM。',
  `three_ds_version` varchar(32) DEFAULT NULL COMMENT '3DS版本，如 1.0.2、2.2.0。',
  `three_ds_transaction_id` varchar(128) DEFAULT NULL COMMENT '3DS交易ID。',
  `three_ds_server_transaction_id` varchar(128) DEFAULT NULL COMMENT '3DS Server交易ID。',
  `acs_transaction_id` varchar(128) DEFAULT NULL COMMENT 'ACS交易ID。',
  `ds_transaction_id` varchar(128) DEFAULT NULL COMMENT 'Directory Server交易ID。',
  `eci` varchar(8) DEFAULT NULL COMMENT '电子商务交易指示值 ECI。',
  `cavv` varchar(256) DEFAULT NULL COMMENT 'CAVV/AAV认证值，按合规要求可脱敏或加密保存。',
  `xid` varchar(256) DEFAULT NULL COMMENT '3DS 1.x XID。',
  `liability_shift` tinyint DEFAULT NULL COMMENT '是否责任转移，0否，1是。',
  `challenge_required` tinyint DEFAULT NULL COMMENT '是否需要挑战认证，0否，1是。',
  `challenge_status` varchar(32) DEFAULT NULL COMMENT '挑战认证状态，如 REQUIRED、COMPLETED、FAILED、CANCELLED。',
  `authentication_redirect_url_hash` char(64) DEFAULT NULL COMMENT '认证跳转地址哈希，用于排查。',
  `authentication_result_code` varchar(64) DEFAULT NULL COMMENT '认证结果码。',
  `authentication_result_message` varchar(512) DEFAULT NULL COMMENT '认证结果描述。',
  `authentication_time` datetime(3) DEFAULT NULL COMMENT '认证完成或结果确认时间。',
  `authentication_extra_json` json DEFAULT NULL COMMENT '认证扩展摘要，保存渠道/卡组织非高频字段。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_authentication_info_id` (`authentication_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_three_ds_transaction` (`three_ds_transaction_id`,`transaction_date_time`),
  KEY `idx_auth_status_time` (`authentication_status`,`authentication_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存3DS、SCA、渠道/卡组织认证结果，支撑交易详情页认证信息展示和后续责任转移判断。';

-- ----------------------------
-- Table structure for transaction_authentication_info_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_authentication_info_202604`;
CREATE TABLE `transaction_authentication_info_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `authentication_info_id` varchar(64) NOT NULL COMMENT '认证信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `authentication_type` varchar(32) NOT NULL COMMENT '认证类型，如 3DS、SCA、NETWORK_TOKEN_AUTH。',
  `authentication_status` varchar(64) DEFAULT NULL COMMENT '平台归一后的认证状态，如 NOT_REQUIRED、AUTHENTICATED、FAILED、ATTEMPTED。',
  `authentication_source` varchar(64) DEFAULT NULL COMMENT '认证结果来源，如 CHANNEL、CARD_SCHEME、PLATFORM。',
  `three_ds_version` varchar(32) DEFAULT NULL COMMENT '3DS版本，如 1.0.2、2.2.0。',
  `three_ds_transaction_id` varchar(128) DEFAULT NULL COMMENT '3DS交易ID。',
  `three_ds_server_transaction_id` varchar(128) DEFAULT NULL COMMENT '3DS Server交易ID。',
  `acs_transaction_id` varchar(128) DEFAULT NULL COMMENT 'ACS交易ID。',
  `ds_transaction_id` varchar(128) DEFAULT NULL COMMENT 'Directory Server交易ID。',
  `eci` varchar(8) DEFAULT NULL COMMENT '电子商务交易指示值 ECI。',
  `cavv` varchar(256) DEFAULT NULL COMMENT 'CAVV/AAV认证值，按合规要求可脱敏或加密保存。',
  `xid` varchar(256) DEFAULT NULL COMMENT '3DS 1.x XID。',
  `liability_shift` tinyint DEFAULT NULL COMMENT '是否责任转移，0否，1是。',
  `challenge_required` tinyint DEFAULT NULL COMMENT '是否需要挑战认证，0否，1是。',
  `challenge_status` varchar(32) DEFAULT NULL COMMENT '挑战认证状态，如 REQUIRED、COMPLETED、FAILED、CANCELLED。',
  `authentication_redirect_url_hash` char(64) DEFAULT NULL COMMENT '认证跳转地址哈希，用于排查。',
  `authentication_result_code` varchar(64) DEFAULT NULL COMMENT '认证结果码。',
  `authentication_result_message` varchar(512) DEFAULT NULL COMMENT '认证结果描述。',
  `authentication_time` datetime(3) DEFAULT NULL COMMENT '认证完成或结果确认时间。',
  `authentication_extra_json` json DEFAULT NULL COMMENT '认证扩展摘要，保存渠道/卡组织非高频字段。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_authentication_info_id` (`authentication_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_three_ds_transaction` (`three_ds_transaction_id`,`transaction_date_time`),
  KEY `idx_auth_status_time` (`authentication_status`,`authentication_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存3DS、SCA、渠道/卡组织认证结果，支撑交易详情页认证信息展示和后续责任转移判断。';

-- ----------------------------
-- Table structure for transaction_billing_info
-- ----------------------------
DROP TABLE IF EXISTS `transaction_billing_info`;
CREATE TABLE `transaction_billing_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `billing_info_id` varchar(64) NOT NULL COMMENT '账单信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '持卡人名，按商户请求快照明文保存。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '持卡人姓，按商户请求快照明文保存。',
  `email` varchar(64) DEFAULT NULL COMMENT '持卡人邮箱，按商户请求快照明文保存，禁止普通日志输出。',
  `phone` varchar(32) DEFAULT NULL COMMENT '持卡人电话，按商户请求快照明文保存，禁止普通日志输出。',
  `billing_country` varchar(3) DEFAULT NULL COMMENT '账单国家/地区。',
  `billing_state` varchar(64) DEFAULT NULL COMMENT '账单州/省。',
  `billing_city` varchar(64) DEFAULT NULL COMMENT '账单城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '持卡人账单街道，按商户请求快照明文保存。',
  `billing_postal_code` varchar(32) DEFAULT NULL COMMENT '账单邮编。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_info_id` (`billing_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_country_time` (`billing_country`,`transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存交易维度账单地址和邮编等信息，供卡组织、渠道、风控、争议场景使用。';

-- ----------------------------
-- Table structure for transaction_billing_info_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_billing_info_202603`;
CREATE TABLE `transaction_billing_info_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `billing_info_id` varchar(64) NOT NULL COMMENT '账单信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '持卡人名，按商户请求快照明文保存。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '持卡人姓，按商户请求快照明文保存。',
  `email` varchar(64) DEFAULT NULL COMMENT '持卡人邮箱，按商户请求快照明文保存，禁止普通日志输出。',
  `phone` varchar(32) DEFAULT NULL COMMENT '持卡人电话，按商户请求快照明文保存，禁止普通日志输出。',
  `billing_country` varchar(3) DEFAULT NULL COMMENT '账单国家/地区。',
  `billing_state` varchar(64) DEFAULT NULL COMMENT '账单州/省。',
  `billing_city` varchar(64) DEFAULT NULL COMMENT '账单城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '持卡人账单街道，按商户请求快照明文保存。',
  `billing_postal_code` varchar(32) DEFAULT NULL COMMENT '账单邮编。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_info_id` (`billing_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_country_time` (`billing_country`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存交易维度账单地址和邮编等信息，供卡组织、渠道、风控、争议场景使用。';

-- ----------------------------
-- Table structure for transaction_billing_info_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_billing_info_202604`;
CREATE TABLE `transaction_billing_info_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `billing_info_id` varchar(64) NOT NULL COMMENT '账单信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '持卡人名，按商户请求快照明文保存。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '持卡人姓，按商户请求快照明文保存。',
  `email` varchar(64) DEFAULT NULL COMMENT '持卡人邮箱，按商户请求快照明文保存，禁止普通日志输出。',
  `phone` varchar(32) DEFAULT NULL COMMENT '持卡人电话，按商户请求快照明文保存，禁止普通日志输出。',
  `billing_country` varchar(3) DEFAULT NULL COMMENT '账单国家/地区。',
  `billing_state` varchar(64) DEFAULT NULL COMMENT '账单州/省。',
  `billing_city` varchar(64) DEFAULT NULL COMMENT '账单城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '持卡人账单街道，按商户请求快照明文保存。',
  `billing_postal_code` varchar(32) DEFAULT NULL COMMENT '账单邮编。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_info_id` (`billing_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_country_time` (`billing_country`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存交易维度账单地址和邮编等信息，供卡组织、渠道、风控、争议场景使用。';

-- ----------------------------
-- Table structure for transaction_shipping_info
-- ----------------------------
DROP TABLE IF EXISTS `transaction_shipping_info`;
CREATE TABLE `transaction_shipping_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `shipping_info_id` varchar(64) NOT NULL COMMENT '收货信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '收货人名，按商户请求快照明文保存。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '收货人姓，按商户请求快照明文保存。',
  `email` varchar(64) DEFAULT NULL COMMENT '收货人邮箱，按商户请求快照明文保存，禁止普通日志输出。',
  `phone` varchar(32) DEFAULT NULL COMMENT '收货人电话，按商户请求快照明文保存，禁止普通日志输出。',
  `country` varchar(3) DEFAULT NULL COMMENT '收货国家/地区，ISO 3166-1 alpha-3。',
  `state` varchar(64) DEFAULT NULL COMMENT '收货州、省或地区。',
  `city` varchar(64) DEFAULT NULL COMMENT '收货城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '收货街道，按商户请求快照明文保存。',
  `postal` varchar(32) DEFAULT NULL COMMENT '收货邮编。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipping_info_id` (`shipping_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_country_time` (`country`,`transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户上送的收货人身份、联系方式和收货地址明文快照，供风控、渠道透传和商户查询回显使用。';

-- ----------------------------
-- Table structure for transaction_shipping_info_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_shipping_info_202603`;
CREATE TABLE `transaction_shipping_info_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `shipping_info_id` varchar(64) NOT NULL COMMENT '收货信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '收货人名，按商户请求快照明文保存。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '收货人姓，按商户请求快照明文保存。',
  `email` varchar(64) DEFAULT NULL COMMENT '收货人邮箱，按商户请求快照明文保存，禁止普通日志输出。',
  `phone` varchar(32) DEFAULT NULL COMMENT '收货人电话，按商户请求快照明文保存，禁止普通日志输出。',
  `country` varchar(3) DEFAULT NULL COMMENT '收货国家/地区，ISO 3166-1 alpha-3。',
  `state` varchar(64) DEFAULT NULL COMMENT '收货州、省或地区。',
  `city` varchar(64) DEFAULT NULL COMMENT '收货城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '收货街道，按商户请求快照明文保存。',
  `postal` varchar(32) DEFAULT NULL COMMENT '收货邮编。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipping_info_id` (`shipping_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_country_time` (`country`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户上送的收货人身份、联系方式和收货地址明文快照，供风控、渠道透传和商户查询回显使用。';

-- ----------------------------
-- Table structure for transaction_shipping_info_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_shipping_info_202604`;
CREATE TABLE `transaction_shipping_info_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `shipping_info_id` varchar(64) NOT NULL COMMENT '收货信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '收货人名，按商户请求快照明文保存。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '收货人姓，按商户请求快照明文保存。',
  `email` varchar(64) DEFAULT NULL COMMENT '收货人邮箱，按商户请求快照明文保存，禁止普通日志输出。',
  `phone` varchar(32) DEFAULT NULL COMMENT '收货人电话，按商户请求快照明文保存，禁止普通日志输出。',
  `country` varchar(3) DEFAULT NULL COMMENT '收货国家/地区，ISO 3166-1 alpha-3。',
  `state` varchar(64) DEFAULT NULL COMMENT '收货州、省或地区。',
  `city` varchar(64) DEFAULT NULL COMMENT '收货城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '收货街道，按商户请求快照明文保存。',
  `postal` varchar(32) DEFAULT NULL COMMENT '收货邮编。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipping_info_id` (`shipping_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_country_time` (`country`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户上送的收货人身份、联系方式和收货地址明文快照，供风控、渠道透传和商户查询回显使用。';

-- ----------------------------
-- Table structure for transaction_card_vault
-- ----------------------------
DROP TABLE IF EXISTS `transaction_card_vault`;
CREATE TABLE `transaction_card_vault` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '分片内自增主键',
  `vault_record_id` varchar(64) NOT NULL COMMENT '卡资料库记录号',
  `message_id` varchar(64) NOT NULL COMMENT 'MQ 消息号',
  `merchant_id` varchar(64) NOT NULL COMMENT '商户号',
  `checkout_attempt_id` varchar(64) NOT NULL COMMENT '收银台支付尝试号',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易号',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易时间及季度分片键',
  `card_brand` varchar(32) NOT NULL COMMENT '卡品牌',
  `card_bin` varchar(8) NOT NULL COMMENT '卡号前六位 BIN',
  `card_last4` char(4) NOT NULL COMMENT '卡号后四位',
  `pan_hmac` char(64) NOT NULL COMMENT '带 secret pepper 的 PAN HMAC-SHA256',
  `pan_hmac_key_version` varchar(32) NOT NULL COMMENT 'PAN HMAC 密钥版本',
  `pan_ciphertext` varchar(128) NOT NULL COMMENT 'PAN AES-256-GCM 密文',
  `pan_iv` varchar(32) NOT NULL COMMENT 'PAN AES-GCM IV',
  `pan_auth_tag` varchar(32) NOT NULL COMMENT 'PAN AES-GCM 认证标签',
  `expiration_ciphertext` varchar(64) NOT NULL COMMENT '有效期 AES-256-GCM 密文',
  `expiration_iv` varchar(32) NOT NULL COMMENT '有效期 AES-GCM IV',
  `expiration_auth_tag` varchar(32) NOT NULL COMMENT '有效期 AES-GCM 认证标签',
  `cardholder_name_ciphertext` varchar(512) DEFAULT NULL COMMENT '持卡人姓名 AES-256-GCM 密文',
  `cardholder_name_iv` varchar(32) DEFAULT NULL COMMENT '持卡人姓名 AES-GCM IV',
  `cardholder_name_auth_tag` varchar(32) DEFAULT NULL COMMENT '持卡人姓名 AES-GCM 认证标签',
  `wrapped_dek_ciphertext` varchar(128) NOT NULL COMMENT 'KEK 包裹后的随机 DEK 密文',
  `wrapped_dek_iv` varchar(32) NOT NULL COMMENT '包裹 DEK 使用的 AES-GCM IV',
  `wrapped_dek_auth_tag` varchar(32) NOT NULL COMMENT '包裹 DEK 使用的 AES-GCM 认证标签',
  `kek_version` varchar(32) NOT NULL COMMENT 'KEK 版本',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_vault_record` (`vault_record_id`,`transaction_date_time`),
  UNIQUE KEY `uk_card_vault_message` (`message_id`,`transaction_date_time`),
  UNIQUE KEY `uk_card_vault_transaction` (`merchant_id`,`transaction_id`,`transaction_date_time`),
  KEY `idx_card_vault_pan_hmac` (`merchant_id`,`pan_hmac`,`transaction_date_time`),
  KEY `idx_card_vault_attempt` (`checkout_attempt_id`,`transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收银台卡资料加密分表模板';

-- ----------------------------
-- Table structure for transaction_card_vault_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_card_vault_202603`;
CREATE TABLE `transaction_card_vault_202603` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '分片内自增主键',
  `vault_record_id` varchar(64) NOT NULL COMMENT '卡资料库记录号',
  `message_id` varchar(64) NOT NULL COMMENT 'MQ 消息号',
  `merchant_id` varchar(64) NOT NULL COMMENT '商户号',
  `checkout_attempt_id` varchar(64) NOT NULL COMMENT '收银台支付尝试号',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易号',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易时间及季度分片键',
  `card_brand` varchar(32) NOT NULL COMMENT '卡品牌',
  `card_bin` varchar(8) NOT NULL COMMENT '卡号前六位 BIN',
  `card_last4` char(4) NOT NULL COMMENT '卡号后四位',
  `pan_hmac` char(64) NOT NULL COMMENT '带 secret pepper 的 PAN HMAC-SHA256',
  `pan_hmac_key_version` varchar(32) NOT NULL COMMENT 'PAN HMAC 密钥版本',
  `pan_ciphertext` varchar(128) NOT NULL COMMENT 'PAN AES-256-GCM 密文',
  `pan_iv` varchar(32) NOT NULL COMMENT 'PAN AES-GCM IV',
  `pan_auth_tag` varchar(32) NOT NULL COMMENT 'PAN AES-GCM 认证标签',
  `expiration_ciphertext` varchar(64) NOT NULL COMMENT '有效期 AES-256-GCM 密文',
  `expiration_iv` varchar(32) NOT NULL COMMENT '有效期 AES-GCM IV',
  `expiration_auth_tag` varchar(32) NOT NULL COMMENT '有效期 AES-GCM 认证标签',
  `cardholder_name_ciphertext` varchar(512) DEFAULT NULL COMMENT '持卡人姓名 AES-256-GCM 密文',
  `cardholder_name_iv` varchar(32) DEFAULT NULL COMMENT '持卡人姓名 AES-GCM IV',
  `cardholder_name_auth_tag` varchar(32) DEFAULT NULL COMMENT '持卡人姓名 AES-GCM 认证标签',
  `wrapped_dek_ciphertext` varchar(128) NOT NULL COMMENT 'KEK 包裹后的随机 DEK 密文',
  `wrapped_dek_iv` varchar(32) NOT NULL COMMENT '包裹 DEK 使用的 AES-GCM IV',
  `wrapped_dek_auth_tag` varchar(32) NOT NULL COMMENT '包裹 DEK 使用的 AES-GCM 认证标签',
  `kek_version` varchar(32) NOT NULL COMMENT 'KEK 版本',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_vault_record` (`vault_record_id`,`transaction_date_time`),
  UNIQUE KEY `uk_card_vault_message` (`message_id`,`transaction_date_time`),
  UNIQUE KEY `uk_card_vault_transaction` (`merchant_id`,`transaction_id`,`transaction_date_time`),
  KEY `idx_card_vault_pan_hmac` (`merchant_id`,`pan_hmac`,`transaction_date_time`),
  KEY `idx_card_vault_attempt` (`checkout_attempt_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收银台卡资料加密分表模板';

-- ----------------------------
-- Table structure for transaction_card_vault_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_card_vault_202604`;
CREATE TABLE `transaction_card_vault_202604` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '分片内自增主键',
  `vault_record_id` varchar(64) NOT NULL COMMENT '卡资料库记录号',
  `message_id` varchar(64) NOT NULL COMMENT 'MQ 消息号',
  `merchant_id` varchar(64) NOT NULL COMMENT '商户号',
  `checkout_attempt_id` varchar(64) NOT NULL COMMENT '收银台支付尝试号',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易号',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易时间及季度分片键',
  `card_brand` varchar(32) NOT NULL COMMENT '卡品牌',
  `card_bin` varchar(8) NOT NULL COMMENT '卡号前六位 BIN',
  `card_last4` char(4) NOT NULL COMMENT '卡号后四位',
  `pan_hmac` char(64) NOT NULL COMMENT '带 secret pepper 的 PAN HMAC-SHA256',
  `pan_hmac_key_version` varchar(32) NOT NULL COMMENT 'PAN HMAC 密钥版本',
  `pan_ciphertext` varchar(128) NOT NULL COMMENT 'PAN AES-256-GCM 密文',
  `pan_iv` varchar(32) NOT NULL COMMENT 'PAN AES-GCM IV',
  `pan_auth_tag` varchar(32) NOT NULL COMMENT 'PAN AES-GCM 认证标签',
  `expiration_ciphertext` varchar(64) NOT NULL COMMENT '有效期 AES-256-GCM 密文',
  `expiration_iv` varchar(32) NOT NULL COMMENT '有效期 AES-GCM IV',
  `expiration_auth_tag` varchar(32) NOT NULL COMMENT '有效期 AES-GCM 认证标签',
  `cardholder_name_ciphertext` varchar(512) DEFAULT NULL COMMENT '持卡人姓名 AES-256-GCM 密文',
  `cardholder_name_iv` varchar(32) DEFAULT NULL COMMENT '持卡人姓名 AES-GCM IV',
  `cardholder_name_auth_tag` varchar(32) DEFAULT NULL COMMENT '持卡人姓名 AES-GCM 认证标签',
  `wrapped_dek_ciphertext` varchar(128) NOT NULL COMMENT 'KEK 包裹后的随机 DEK 密文',
  `wrapped_dek_iv` varchar(32) NOT NULL COMMENT '包裹 DEK 使用的 AES-GCM IV',
  `wrapped_dek_auth_tag` varchar(32) NOT NULL COMMENT '包裹 DEK 使用的 AES-GCM 认证标签',
  `kek_version` varchar(32) NOT NULL COMMENT 'KEK 版本',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_vault_record` (`vault_record_id`,`transaction_date_time`),
  UNIQUE KEY `uk_card_vault_message` (`message_id`,`transaction_date_time`),
  UNIQUE KEY `uk_card_vault_transaction` (`merchant_id`,`transaction_id`,`transaction_date_time`),
  KEY `idx_card_vault_pan_hmac` (`merchant_id`,`pan_hmac`,`transaction_date_time`),
  KEY `idx_card_vault_attempt` (`checkout_attempt_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收银台卡资料加密分表模板';

-- ----------------------------
-- Table structure for transaction_channel_callback
-- ----------------------------
DROP TABLE IF EXISTS `transaction_channel_callback`;
CREATE TABLE `transaction_channel_callback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `callback_id` varchar(64) NOT NULL COMMENT '渠道回调业务ID。',
  `callback_log_id` varchar(64) NOT NULL COMMENT '渠道回调日志ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `channel_code` varchar(32) NOT NULL COMMENT '渠道编码。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道订单号。',
  `channel_transaction_id` varchar(128) DEFAULT NULL COMMENT '渠道交易ID。',
  `callback_type` varchar(64) NOT NULL COMMENT '回调类型。',
  `channel_event_type` varchar(64) DEFAULT NULL COMMENT '渠道原始事件类型。',
  `callback_status` varchar(32) NOT NULL COMMENT '回调处理状态：RECEIVED、PROCESSED、IGNORED、FAILED。',
  `idempotency_key` varchar(256) NOT NULL COMMENT '渠道回调幂等键。',
  `signature_valid` tinyint NOT NULL DEFAULT '0' COMMENT '签名校验是否通过。',
  `ip_allowed` tinyint NOT NULL DEFAULT '0' COMMENT 'IP白名单是否通过。',
  `parsed_transaction_status` varchar(32) DEFAULT NULL COMMENT '从渠道回调解析出的平台目标状态。',
  `previous_transaction_status` varchar(32) DEFAULT NULL COMMENT '状态推进前平台状态。',
  `target_transaction_status` varchar(32) DEFAULT NULL COMMENT '状态推进目标状态。',
  `process_result` varchar(64) DEFAULT NULL COMMENT '业务处理结果，如 STATUS_CHANGED、DUPLICATE、TERMINAL_IGNORED。',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '处理失败原因。',
  `callback_received_time` datetime(3) NOT NULL COMMENT '渠道回调接收时间。',
  `processed_time` datetime(3) DEFAULT NULL COMMENT '回调处理完成时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '原交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '原交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '原交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_callback_id` (`callback_id`),
  UNIQUE KEY `uk_callback_idempotency` (`channel_code`,`idempotency_key`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_callback_status_time` (`callback_status`,`callback_received_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存渠道回调解析后的业务处理结果、幂等和状态推进结果。';

-- ----------------------------
-- Table structure for transaction_channel_callback_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_channel_callback_202603`;
CREATE TABLE `transaction_channel_callback_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `callback_id` varchar(64) NOT NULL COMMENT '渠道回调业务ID。',
  `callback_log_id` varchar(64) NOT NULL COMMENT '渠道回调日志ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `channel_code` varchar(32) NOT NULL COMMENT '渠道编码。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道订单号。',
  `channel_transaction_id` varchar(128) DEFAULT NULL COMMENT '渠道交易ID。',
  `callback_type` varchar(64) NOT NULL COMMENT '回调类型。',
  `channel_event_type` varchar(64) DEFAULT NULL COMMENT '渠道原始事件类型。',
  `callback_status` varchar(32) NOT NULL COMMENT '回调处理状态：RECEIVED、PROCESSED、IGNORED、FAILED。',
  `idempotency_key` varchar(256) NOT NULL COMMENT '渠道回调幂等键。',
  `signature_valid` tinyint NOT NULL DEFAULT '0' COMMENT '签名校验是否通过。',
  `ip_allowed` tinyint NOT NULL DEFAULT '0' COMMENT 'IP白名单是否通过。',
  `parsed_transaction_status` varchar(32) DEFAULT NULL COMMENT '从渠道回调解析出的平台目标状态。',
  `previous_transaction_status` varchar(32) DEFAULT NULL COMMENT '状态推进前平台状态。',
  `target_transaction_status` varchar(32) DEFAULT NULL COMMENT '状态推进目标状态。',
  `process_result` varchar(64) DEFAULT NULL COMMENT '业务处理结果，如 STATUS_CHANGED、DUPLICATE、TERMINAL_IGNORED。',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '处理失败原因。',
  `callback_received_time` datetime(3) NOT NULL COMMENT '渠道回调接收时间。',
  `processed_time` datetime(3) DEFAULT NULL COMMENT '回调处理完成时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '原交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '原交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '原交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_callback_id` (`callback_id`),
  UNIQUE KEY `uk_callback_idempotency` (`channel_code`,`idempotency_key`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_callback_status_time` (`callback_status`,`callback_received_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存渠道回调解析后的业务处理结果、幂等和状态推进结果。';

-- ----------------------------
-- Table structure for transaction_channel_callback_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_channel_callback_202604`;
CREATE TABLE `transaction_channel_callback_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `callback_id` varchar(64) NOT NULL COMMENT '渠道回调业务ID。',
  `callback_log_id` varchar(64) NOT NULL COMMENT '渠道回调日志ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `channel_code` varchar(32) NOT NULL COMMENT '渠道编码。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道订单号。',
  `channel_transaction_id` varchar(128) DEFAULT NULL COMMENT '渠道交易ID。',
  `callback_type` varchar(64) NOT NULL COMMENT '回调类型。',
  `channel_event_type` varchar(64) DEFAULT NULL COMMENT '渠道原始事件类型。',
  `callback_status` varchar(32) NOT NULL COMMENT '回调处理状态：RECEIVED、PROCESSED、IGNORED、FAILED。',
  `idempotency_key` varchar(256) NOT NULL COMMENT '渠道回调幂等键。',
  `signature_valid` tinyint NOT NULL DEFAULT '0' COMMENT '签名校验是否通过。',
  `ip_allowed` tinyint NOT NULL DEFAULT '0' COMMENT 'IP白名单是否通过。',
  `parsed_transaction_status` varchar(32) DEFAULT NULL COMMENT '从渠道回调解析出的平台目标状态。',
  `previous_transaction_status` varchar(32) DEFAULT NULL COMMENT '状态推进前平台状态。',
  `target_transaction_status` varchar(32) DEFAULT NULL COMMENT '状态推进目标状态。',
  `process_result` varchar(64) DEFAULT NULL COMMENT '业务处理结果，如 STATUS_CHANGED、DUPLICATE、TERMINAL_IGNORED。',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '处理失败原因。',
  `callback_received_time` datetime(3) NOT NULL COMMENT '渠道回调接收时间。',
  `processed_time` datetime(3) DEFAULT NULL COMMENT '回调处理完成时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '原交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '原交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '原交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_callback_id` (`callback_id`),
  UNIQUE KEY `uk_callback_idempotency` (`channel_code`,`idempotency_key`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_callback_status_time` (`callback_status`,`callback_received_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存渠道回调解析后的业务处理结果、幂等和状态推进结果。';

-- ----------------------------
-- Table structure for transaction_channel_callback_log
-- ----------------------------
DROP TABLE IF EXISTS `transaction_channel_callback_log`;
CREATE TABLE `transaction_channel_callback_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `callback_log_id` varchar(64) NOT NULL COMMENT '渠道回调日志ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID，可由回调解析或业务处理后回填。',
  `channel_code` varchar(32) NOT NULL COMMENT '渠道编码。',
  `callback_type` varchar(64) NOT NULL COMMENT '回调类型。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道订单号。',
  `channel_transaction_id` varchar(128) DEFAULT NULL COMMENT '渠道交易ID。',
  `request_uri` varchar(512) DEFAULT NULL COMMENT '回调请求URI。',
  `http_method` varchar(16) DEFAULT NULL COMMENT 'HTTP方法。',
  `source_ip` varchar(64) DEFAULT NULL COMMENT '渠道回调来源IP。',
  `request_header_json_masked` json DEFAULT NULL COMMENT '脱敏请求头 JSON。',
  `request_body_json_masked` mediumtext COMMENT '脱敏回调原文 JSON 字符串。',
  `signature_valid` tinyint NOT NULL DEFAULT '0' COMMENT '签名校验是否通过。',
  `ip_allowed` tinyint NOT NULL DEFAULT '0' COMMENT 'IP白名单是否通过。',
  `platform_response_code` varchar(64) DEFAULT NULL COMMENT '平台返回给渠道的响应码。',
  `platform_response_body` varchar(1024) DEFAULT NULL COMMENT '平台返回给渠道的响应摘要。',
  `callback_received_time` datetime(3) NOT NULL COMMENT '渠道回调接收时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '原交易业务时间，所有交易分表统一字段。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '原交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '原交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_callback_log_id` (`callback_log_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_channel_order` (`channel_code`,`channel_order_no`,`transaction_date_time`),
  KEY `idx_received_time` (`callback_received_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存渠道回调原文、验签、IP校验和平台响应日志。';

-- ----------------------------
-- Table structure for transaction_channel_callback_log_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_channel_callback_log_202603`;
CREATE TABLE `transaction_channel_callback_log_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `callback_log_id` varchar(64) NOT NULL COMMENT '渠道回调日志ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID，可由回调解析或业务处理后回填。',
  `channel_code` varchar(32) NOT NULL COMMENT '渠道编码。',
  `callback_type` varchar(64) NOT NULL COMMENT '回调类型。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道订单号。',
  `channel_transaction_id` varchar(128) DEFAULT NULL COMMENT '渠道交易ID。',
  `request_uri` varchar(512) DEFAULT NULL COMMENT '回调请求URI。',
  `http_method` varchar(16) DEFAULT NULL COMMENT 'HTTP方法。',
  `source_ip` varchar(64) DEFAULT NULL COMMENT '渠道回调来源IP。',
  `request_header_json_masked` json DEFAULT NULL COMMENT '脱敏请求头 JSON。',
  `request_body_json_masked` mediumtext COMMENT '脱敏回调原文 JSON 字符串。',
  `signature_valid` tinyint NOT NULL DEFAULT '0' COMMENT '签名校验是否通过。',
  `ip_allowed` tinyint NOT NULL DEFAULT '0' COMMENT 'IP白名单是否通过。',
  `platform_response_code` varchar(64) DEFAULT NULL COMMENT '平台返回给渠道的响应码。',
  `platform_response_body` varchar(1024) DEFAULT NULL COMMENT '平台返回给渠道的响应摘要。',
  `callback_received_time` datetime(3) NOT NULL COMMENT '渠道回调接收时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '原交易业务时间，所有交易分表统一字段。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '原交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '原交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_callback_log_id` (`callback_log_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_channel_order` (`channel_code`,`channel_order_no`,`transaction_date_time`),
  KEY `idx_received_time` (`callback_received_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存渠道回调原文、验签、IP校验和平台响应日志。';

-- ----------------------------
-- Table structure for transaction_channel_callback_log_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_channel_callback_log_202604`;
CREATE TABLE `transaction_channel_callback_log_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `callback_log_id` varchar(64) NOT NULL COMMENT '渠道回调日志ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID，可由回调解析或业务处理后回填。',
  `channel_code` varchar(32) NOT NULL COMMENT '渠道编码。',
  `callback_type` varchar(64) NOT NULL COMMENT '回调类型。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道订单号。',
  `channel_transaction_id` varchar(128) DEFAULT NULL COMMENT '渠道交易ID。',
  `request_uri` varchar(512) DEFAULT NULL COMMENT '回调请求URI。',
  `http_method` varchar(16) DEFAULT NULL COMMENT 'HTTP方法。',
  `source_ip` varchar(64) DEFAULT NULL COMMENT '渠道回调来源IP。',
  `request_header_json_masked` json DEFAULT NULL COMMENT '脱敏请求头 JSON。',
  `request_body_json_masked` mediumtext COMMENT '脱敏回调原文 JSON 字符串。',
  `signature_valid` tinyint NOT NULL DEFAULT '0' COMMENT '签名校验是否通过。',
  `ip_allowed` tinyint NOT NULL DEFAULT '0' COMMENT 'IP白名单是否通过。',
  `platform_response_code` varchar(64) DEFAULT NULL COMMENT '平台返回给渠道的响应码。',
  `platform_response_body` varchar(1024) DEFAULT NULL COMMENT '平台返回给渠道的响应摘要。',
  `callback_received_time` datetime(3) NOT NULL COMMENT '渠道回调接收时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '原交易业务时间，所有交易分表统一字段。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '原交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '原交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_callback_log_id` (`callback_log_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_channel_order` (`channel_code`,`channel_order_no`,`transaction_date_time`),
  KEY `idx_received_time` (`callback_received_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存渠道回调原文、验签、IP校验和平台响应日志。';

-- ----------------------------
-- Table structure for transaction_channel_interaction_log
-- ----------------------------
DROP TABLE IF EXISTS `transaction_channel_interaction_log`;
CREATE TABLE `transaction_channel_interaction_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `interaction_log_id` varchar(64) NOT NULL COMMENT '渠道交互日志ID。',
  `request_id` varchar(64) NOT NULL COMMENT '平台渠道请求ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) NOT NULL COMMENT '交易动作ID。',
  `channel_code` varchar(32) NOT NULL COMMENT '渠道编码。',
  `interaction_type` varchar(32) NOT NULL COMMENT '交互类型：REQUEST、RESPONSE、EXCEPTION。',
  `http_method` varchar(16) DEFAULT NULL COMMENT 'HTTP方法。',
  `request_url_masked` varchar(512) DEFAULT NULL COMMENT '脱敏请求URL。',
  `http_status` int DEFAULT NULL COMMENT 'HTTP状态码。',
  `request_header_json_masked` json DEFAULT NULL COMMENT '脱敏请求头 JSON。',
  `request_body_json_masked` mediumtext COMMENT '脱敏请求体 JSON 字符串，便于复制到 JSON 工具查看。',
  `response_header_json_masked` json DEFAULT NULL COMMENT '脱敏响应头 JSON。',
  `response_body_json_masked` mediumtext COMMENT '脱敏响应体 JSON 字符串，便于复制到 JSON 工具查看。',
  `exception_type` varchar(128) DEFAULT NULL COMMENT '异常类型。',
  `exception_message` varchar(1024) DEFAULT NULL COMMENT '异常摘要。',
  `duration_millis` int DEFAULT NULL COMMENT '耗时毫秒。',
  `trace_id` varchar(128) DEFAULT NULL COMMENT '链路追踪ID。',
  `interaction_time` datetime(3) NOT NULL COMMENT '交互日志产生时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_interaction_log_id` (`interaction_log_id`),
  KEY `idx_request_time` (`request_id`,`transaction_date_time`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_channel_time` (`channel_code`,`interaction_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`,`interaction_time`),
  KEY `idx_page_time` (`transaction_date_time`,`interaction_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存渠道请求、响应、异常的可审计日志；请求/响应对象直接转 JSON 后脱敏入库。';

-- ----------------------------
-- Table structure for transaction_channel_interaction_log_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_channel_interaction_log_202603`;
CREATE TABLE `transaction_channel_interaction_log_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `interaction_log_id` varchar(64) NOT NULL COMMENT '渠道交互日志ID。',
  `request_id` varchar(64) NOT NULL COMMENT '平台渠道请求ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) NOT NULL COMMENT '交易动作ID。',
  `channel_code` varchar(32) NOT NULL COMMENT '渠道编码。',
  `interaction_type` varchar(32) NOT NULL COMMENT '交互类型：REQUEST、RESPONSE、EXCEPTION。',
  `http_method` varchar(16) DEFAULT NULL COMMENT 'HTTP方法。',
  `request_url_masked` varchar(512) DEFAULT NULL COMMENT '脱敏请求URL。',
  `http_status` int DEFAULT NULL COMMENT 'HTTP状态码。',
  `request_header_json_masked` json DEFAULT NULL COMMENT '脱敏请求头 JSON。',
  `request_body_json_masked` mediumtext COMMENT '脱敏请求体 JSON 字符串，便于复制到 JSON 工具查看。',
  `response_header_json_masked` json DEFAULT NULL COMMENT '脱敏响应头 JSON。',
  `response_body_json_masked` mediumtext COMMENT '脱敏响应体 JSON 字符串，便于复制到 JSON 工具查看。',
  `exception_type` varchar(128) DEFAULT NULL COMMENT '异常类型。',
  `exception_message` varchar(1024) DEFAULT NULL COMMENT '异常摘要。',
  `duration_millis` int DEFAULT NULL COMMENT '耗时毫秒。',
  `trace_id` varchar(128) DEFAULT NULL COMMENT '链路追踪ID。',
  `interaction_time` datetime(3) NOT NULL COMMENT '交互日志产生时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_interaction_log_id` (`interaction_log_id`),
  KEY `idx_request_time` (`request_id`,`transaction_date_time`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_channel_time` (`channel_code`,`interaction_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`,`interaction_time`),
  KEY `idx_page_time` (`transaction_date_time`,`interaction_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000001084 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存渠道请求、响应、异常的可审计日志；请求/响应对象直接转 JSON 后脱敏入库。';

-- ----------------------------
-- Table structure for transaction_channel_interaction_log_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_channel_interaction_log_202604`;
CREATE TABLE `transaction_channel_interaction_log_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `interaction_log_id` varchar(64) NOT NULL COMMENT '渠道交互日志ID。',
  `request_id` varchar(64) NOT NULL COMMENT '平台渠道请求ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) NOT NULL COMMENT '交易动作ID。',
  `channel_code` varchar(32) NOT NULL COMMENT '渠道编码。',
  `interaction_type` varchar(32) NOT NULL COMMENT '交互类型：REQUEST、RESPONSE、EXCEPTION。',
  `http_method` varchar(16) DEFAULT NULL COMMENT 'HTTP方法。',
  `request_url_masked` varchar(512) DEFAULT NULL COMMENT '脱敏请求URL。',
  `http_status` int DEFAULT NULL COMMENT 'HTTP状态码。',
  `request_header_json_masked` json DEFAULT NULL COMMENT '脱敏请求头 JSON。',
  `request_body_json_masked` mediumtext COMMENT '脱敏请求体 JSON 字符串，便于复制到 JSON 工具查看。',
  `response_header_json_masked` json DEFAULT NULL COMMENT '脱敏响应头 JSON。',
  `response_body_json_masked` mediumtext COMMENT '脱敏响应体 JSON 字符串，便于复制到 JSON 工具查看。',
  `exception_type` varchar(128) DEFAULT NULL COMMENT '异常类型。',
  `exception_message` varchar(1024) DEFAULT NULL COMMENT '异常摘要。',
  `duration_millis` int DEFAULT NULL COMMENT '耗时毫秒。',
  `trace_id` varchar(128) DEFAULT NULL COMMENT '链路追踪ID。',
  `interaction_time` datetime(3) NOT NULL COMMENT '交互日志产生时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_interaction_log_id` (`interaction_log_id`),
  KEY `idx_request_time` (`request_id`,`transaction_date_time`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_channel_time` (`channel_code`,`interaction_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`,`interaction_time`),
  KEY `idx_page_time` (`transaction_date_time`,`interaction_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存渠道请求、响应、异常的可审计日志；请求/响应对象直接转 JSON 后脱敏入库。';

-- ----------------------------
-- Table structure for transaction_channel_request
-- ----------------------------
DROP TABLE IF EXISTS `transaction_channel_request`;
CREATE TABLE `transaction_channel_request` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `request_id` varchar(64) NOT NULL COMMENT '平台渠道请求ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) NOT NULL COMMENT '交易动作ID。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID；路由失败或仅记录异常链路时可为空。',
  `channel_code` varchar(32) NOT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `transaction_type` varchar(32) NOT NULL COMMENT '交易类型，对齐字典 transaction_type。',
  `request_scene` varchar(64) NOT NULL COMMENT '渠道请求场景，如 AUTHORIZATION、CAPTURE、REFUND、VOID、RETRIEVE、QUERY_CONFIRM。',
  `channel_match_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否为渠道结果勾兑/查询确认请求，0否，1是。',
  `request_status` varchar(32) NOT NULL COMMENT '请求状态，如 INIT、SUCCESS、FAILED、TIMEOUT。',
  `http_method` varchar(16) DEFAULT NULL COMMENT 'HTTP方法。',
  `request_url_masked` varchar(512) DEFAULT NULL COMMENT '脱敏后的请求URL。',
  `request_currency` char(3) DEFAULT NULL COMMENT '上送渠道币种。',
  `request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道金额。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道订单号。',
  `channel_transaction_id` varchar(128) DEFAULT NULL COMMENT '渠道交易ID。',
  `gateway_result` varchar(64) DEFAULT NULL COMMENT '渠道网关外层结果，如 MPGS result。',
  `gateway_code` varchar(64) DEFAULT NULL COMMENT '渠道网关码，如 MPGS response.gatewayCode。',
  `acquirer_code` varchar(64) DEFAULT NULL COMMENT '收单响应码，如 MPGS response.acquirerCode；授权/支付成功重点判断字段。',
  `acquirer_message` varchar(512) DEFAULT NULL COMMENT '收单响应描述。',
  `channel_status` varchar(64) DEFAULT NULL COMMENT '渠道原始交易状态。',
  `platform_success` tinyint NOT NULL DEFAULT '0' COMMENT '平台按渠道规则判断是否成功。',
  `platform_result_code` varchar(64) DEFAULT NULL COMMENT '平台统一结果码。',
  `platform_fail_reason` varchar(512) DEFAULT NULL COMMENT '后台可见失败原因。',
  `request_start_time` datetime(3) NOT NULL COMMENT '渠道请求发起时间。',
  `response_time` datetime(3) DEFAULT NULL COMMENT '渠道响应时间。',
  `duration_millis` int DEFAULT NULL COMMENT '渠道请求耗时，单位毫秒。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_id` (`request_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_channel_order` (`channel_code`,`channel_order_no`,`transaction_date_time`),
  KEY `idx_request_status_time` (`request_status`,`request_start_time`),
  KEY `idx_channel_transaction_identity` (`channel_code`,`channel_order_no`,`channel_transaction_id`,`transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一次渠道请求的核心字段、同步响应摘要和渠道结果判断。';

-- ----------------------------
-- Table structure for transaction_channel_request_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_channel_request_202603`;
CREATE TABLE `transaction_channel_request_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `request_id` varchar(64) NOT NULL COMMENT '平台渠道请求ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) NOT NULL COMMENT '交易动作ID。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID；路由失败或仅记录异常链路时可为空。',
  `channel_code` varchar(32) NOT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `transaction_type` varchar(32) NOT NULL COMMENT '交易类型，对齐字典 transaction_type。',
  `request_scene` varchar(64) NOT NULL COMMENT '渠道请求场景，如 AUTHORIZATION、CAPTURE、REFUND、VOID、RETRIEVE、QUERY_CONFIRM。',
  `channel_match_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否为渠道结果勾兑/查询确认请求，0否，1是。',
  `request_status` varchar(32) NOT NULL COMMENT '请求状态，如 INIT、SUCCESS、FAILED、TIMEOUT。',
  `http_method` varchar(16) DEFAULT NULL COMMENT 'HTTP方法。',
  `request_url_masked` varchar(512) DEFAULT NULL COMMENT '脱敏后的请求URL。',
  `request_currency` char(3) DEFAULT NULL COMMENT '上送渠道币种。',
  `request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道金额。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道订单号。',
  `channel_transaction_id` varchar(128) DEFAULT NULL COMMENT '渠道交易ID。',
  `gateway_result` varchar(64) DEFAULT NULL COMMENT '渠道网关外层结果，如 MPGS result。',
  `gateway_code` varchar(64) DEFAULT NULL COMMENT '渠道网关码，如 MPGS response.gatewayCode。',
  `acquirer_code` varchar(64) DEFAULT NULL COMMENT '收单响应码，如 MPGS response.acquirerCode；授权/支付成功重点判断字段。',
  `acquirer_message` varchar(512) DEFAULT NULL COMMENT '收单响应描述。',
  `channel_status` varchar(64) DEFAULT NULL COMMENT '渠道原始交易状态。',
  `platform_success` tinyint NOT NULL DEFAULT '0' COMMENT '平台按渠道规则判断是否成功。',
  `platform_result_code` varchar(64) DEFAULT NULL COMMENT '平台统一结果码。',
  `platform_fail_reason` varchar(512) DEFAULT NULL COMMENT '后台可见失败原因。',
  `request_start_time` datetime(3) NOT NULL COMMENT '渠道请求发起时间。',
  `response_time` datetime(3) DEFAULT NULL COMMENT '渠道响应时间。',
  `duration_millis` int DEFAULT NULL COMMENT '渠道请求耗时，单位毫秒。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_id` (`request_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_channel_order` (`channel_code`,`channel_order_no`,`transaction_date_time`),
  KEY `idx_request_status_time` (`request_status`,`request_start_time`),
  KEY `idx_channel_transaction_identity` (`channel_code`,`channel_order_no`,`channel_transaction_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000980 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一次渠道请求的核心字段、同步响应摘要和渠道结果判断。';

-- ----------------------------
-- Table structure for transaction_channel_request_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_channel_request_202604`;
CREATE TABLE `transaction_channel_request_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `request_id` varchar(64) NOT NULL COMMENT '平台渠道请求ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) NOT NULL COMMENT '交易动作ID。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID；路由失败或仅记录异常链路时可为空。',
  `channel_code` varchar(32) NOT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `transaction_type` varchar(32) NOT NULL COMMENT '交易类型，对齐字典 transaction_type。',
  `request_scene` varchar(64) NOT NULL COMMENT '渠道请求场景，如 AUTHORIZATION、CAPTURE、REFUND、VOID、RETRIEVE、QUERY_CONFIRM。',
  `channel_match_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否为渠道结果勾兑/查询确认请求，0否，1是。',
  `request_status` varchar(32) NOT NULL COMMENT '请求状态，如 INIT、SUCCESS、FAILED、TIMEOUT。',
  `http_method` varchar(16) DEFAULT NULL COMMENT 'HTTP方法。',
  `request_url_masked` varchar(512) DEFAULT NULL COMMENT '脱敏后的请求URL。',
  `request_currency` char(3) DEFAULT NULL COMMENT '上送渠道币种。',
  `request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道金额。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道订单号。',
  `channel_transaction_id` varchar(128) DEFAULT NULL COMMENT '渠道交易ID。',
  `gateway_result` varchar(64) DEFAULT NULL COMMENT '渠道网关外层结果，如 MPGS result。',
  `gateway_code` varchar(64) DEFAULT NULL COMMENT '渠道网关码，如 MPGS response.gatewayCode。',
  `acquirer_code` varchar(64) DEFAULT NULL COMMENT '收单响应码，如 MPGS response.acquirerCode；授权/支付成功重点判断字段。',
  `acquirer_message` varchar(512) DEFAULT NULL COMMENT '收单响应描述。',
  `channel_status` varchar(64) DEFAULT NULL COMMENT '渠道原始交易状态。',
  `platform_success` tinyint NOT NULL DEFAULT '0' COMMENT '平台按渠道规则判断是否成功。',
  `platform_result_code` varchar(64) DEFAULT NULL COMMENT '平台统一结果码。',
  `platform_fail_reason` varchar(512) DEFAULT NULL COMMENT '后台可见失败原因。',
  `request_start_time` datetime(3) NOT NULL COMMENT '渠道请求发起时间。',
  `response_time` datetime(3) DEFAULT NULL COMMENT '渠道响应时间。',
  `duration_millis` int DEFAULT NULL COMMENT '渠道请求耗时，单位毫秒。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_id` (`request_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_channel_order` (`channel_code`,`channel_order_no`,`transaction_date_time`),
  KEY `idx_request_status_time` (`request_status`,`request_start_time`),
  KEY `idx_channel_transaction_identity` (`channel_code`,`channel_order_no`,`channel_transaction_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一次渠道请求的核心字段、同步响应摘要和渠道结果判断。';

-- ----------------------------
-- Table structure for transaction_currency_conversion
-- ----------------------------
DROP TABLE IF EXISTS `transaction_currency_conversion`;
CREATE TABLE `transaction_currency_conversion` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `conversion_id` varchar(64) NOT NULL COMMENT '币种转换ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `conversion_scene` varchar(32) NOT NULL COMMENT '换汇场景：DCC、EDC、SETTLEMENT、REFUND、RECONCILIATION。',
  `source_currency` char(3) NOT NULL COMMENT '源币种。',
  `source_amount` decimal(20,6) NOT NULL COMMENT '源金额。',
  `target_currency` char(3) NOT NULL COMMENT '目标币种。',
  `target_amount` decimal(20,6) NOT NULL COMMENT '目标金额。',
  `exchange_rate` decimal(24,12) NOT NULL COMMENT '换汇汇率。',
  `rate_source` varchar(64) DEFAULT NULL COMMENT '汇率来源。',
  `rate_quote_id` varchar(128) DEFAULT NULL COMMENT '汇率报价ID。',
  `rate_time` datetime(3) NOT NULL COMMENT '汇率报价或生效时间。',
  `rounding_mode` varchar(32) NOT NULL COMMENT '舍入规则，如 HALF_UP、HALF_EVEN。',
  `source_currency_exponent` tinyint NOT NULL COMMENT '源币种小数位。',
  `target_currency_exponent` tinyint NOT NULL COMMENT '目标币种小数位。',
  `dcc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否DCC换汇记录。',
  `edc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否EDC换汇记录。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversion_id` (`conversion_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_scene_time` (`conversion_scene`,`rate_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存DCC、渠道币种转换EDC、结算换汇、退款换汇等汇率报价、换算金额和舍入规则快照。';

-- ----------------------------
-- Table structure for transaction_currency_conversion_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_currency_conversion_202603`;
CREATE TABLE `transaction_currency_conversion_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `conversion_id` varchar(64) NOT NULL COMMENT '币种转换ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `conversion_scene` varchar(32) NOT NULL COMMENT '换汇场景：DCC、EDC、SETTLEMENT、REFUND、RECONCILIATION。',
  `source_currency` char(3) NOT NULL COMMENT '源币种。',
  `source_amount` decimal(20,6) NOT NULL COMMENT '源金额。',
  `target_currency` char(3) NOT NULL COMMENT '目标币种。',
  `target_amount` decimal(20,6) NOT NULL COMMENT '目标金额。',
  `exchange_rate` decimal(24,12) NOT NULL COMMENT '换汇汇率。',
  `rate_source` varchar(64) DEFAULT NULL COMMENT '汇率来源。',
  `rate_quote_id` varchar(128) DEFAULT NULL COMMENT '汇率报价ID。',
  `rate_time` datetime(3) NOT NULL COMMENT '汇率报价或生效时间。',
  `rounding_mode` varchar(32) NOT NULL COMMENT '舍入规则，如 HALF_UP、HALF_EVEN。',
  `source_currency_exponent` tinyint NOT NULL COMMENT '源币种小数位。',
  `target_currency_exponent` tinyint NOT NULL COMMENT '目标币种小数位。',
  `dcc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否DCC换汇记录。',
  `edc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否EDC换汇记录。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversion_id` (`conversion_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_scene_time` (`conversion_scene`,`rate_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存DCC、渠道币种转换EDC、结算换汇、退款换汇等汇率报价、换算金额和舍入规则快照。';

-- ----------------------------
-- Table structure for transaction_currency_conversion_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_currency_conversion_202604`;
CREATE TABLE `transaction_currency_conversion_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `conversion_id` varchar(64) NOT NULL COMMENT '币种转换ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `conversion_scene` varchar(32) NOT NULL COMMENT '换汇场景：DCC、EDC、SETTLEMENT、REFUND、RECONCILIATION。',
  `source_currency` char(3) NOT NULL COMMENT '源币种。',
  `source_amount` decimal(20,6) NOT NULL COMMENT '源金额。',
  `target_currency` char(3) NOT NULL COMMENT '目标币种。',
  `target_amount` decimal(20,6) NOT NULL COMMENT '目标金额。',
  `exchange_rate` decimal(24,12) NOT NULL COMMENT '换汇汇率。',
  `rate_source` varchar(64) DEFAULT NULL COMMENT '汇率来源。',
  `rate_quote_id` varchar(128) DEFAULT NULL COMMENT '汇率报价ID。',
  `rate_time` datetime(3) NOT NULL COMMENT '汇率报价或生效时间。',
  `rounding_mode` varchar(32) NOT NULL COMMENT '舍入规则，如 HALF_UP、HALF_EVEN。',
  `source_currency_exponent` tinyint NOT NULL COMMENT '源币种小数位。',
  `target_currency_exponent` tinyint NOT NULL COMMENT '目标币种小数位。',
  `dcc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否DCC换汇记录。',
  `edc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否EDC换汇记录。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversion_id` (`conversion_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_scene_time` (`conversion_scene`,`rate_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存DCC、渠道币种转换EDC、结算换汇、退款换汇等汇率报价、换算金额和舍入规则快照。';

-- ----------------------------
-- Table structure for transaction_event_outbox
-- ----------------------------
DROP TABLE IF EXISTS `transaction_event_outbox`;
CREATE TABLE `transaction_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `event_no` varchar(64) NOT NULL COMMENT '本地事务事件号。',
  `aggregate_type` varchar(64) NOT NULL COMMENT '聚合类型，如 PAYMENT_TRANSACTION、TRANSACTION_CALLBACK。',
  `aggregate_no` varchar(64) NOT NULL COMMENT '聚合标识，建议使用 transaction_id 或 callback_id。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `merchant_id` varchar(64) DEFAULT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) DEFAULT NULL COMMENT '商户订单号，用于商户侧查询和补偿排查。',
  `transaction_type` varchar(32) DEFAULT NULL COMMENT '交易类型，对齐字典 transaction_type。',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型，如 PAYMENT_CREATED、STATUS_CHANGED、CALLBACK_PROCESSED。',
  `event_status` varchar(32) NOT NULL COMMENT '事件发布状态：INIT、PROCESSING、SENT、FAILED、CLOSED。',
  `topic` varchar(128) NOT NULL COMMENT 'RocketMQ Topic。',
  `tag` varchar(128) DEFAULT NULL COMMENT 'RocketMQ Tag。',
  `message_key` varchar(128) NOT NULL COMMENT 'MQ消息Key，下游消费幂等使用。',
  `message_group` varchar(128) DEFAULT NULL COMMENT '顺序消息分组键，如 transaction_id。',
  `payload_json` json NOT NULL COMMENT '事件载荷。',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '发布重试次数。',
  `max_retry_count` int NOT NULL DEFAULT '10' COMMENT '最大重试次数。',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下一次发布重试时间。',
  `sent_time` datetime(3) DEFAULT NULL COMMENT '投递成功时间。',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '最近一次投递失败原因。',
  `event_time` datetime(3) NOT NULL COMMENT '事件产生时间，只做业务排序和重试扫描条件。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间，最终分表字段。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_no` (`event_no`),
  UNIQUE KEY `uk_message_key` (`message_key`),
  KEY `idx_event_status_retry` (`event_status`,`next_retry_time`),
  KEY `idx_event_status_update` (`event_status`,`update_time`),
  KEY `idx_aggregate` (`aggregate_type`,`aggregate_no`),
  KEY `idx_transaction_event` (`transaction_id`,`transaction_date_time`,`event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易本地事务内写入事件，事务提交后可靠投递 RocketMQ。';

-- ----------------------------
-- Table structure for transaction_event_outbox_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_event_outbox_202603`;
CREATE TABLE `transaction_event_outbox_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `event_no` varchar(64) NOT NULL COMMENT '本地事务事件号。',
  `aggregate_type` varchar(64) NOT NULL COMMENT '聚合类型，如 PAYMENT_TRANSACTION、TRANSACTION_CALLBACK。',
  `aggregate_no` varchar(64) NOT NULL COMMENT '聚合标识，建议使用 transaction_id 或 callback_id。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `merchant_id` varchar(64) DEFAULT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) DEFAULT NULL COMMENT '商户订单号，用于商户侧查询和补偿排查。',
  `transaction_type` varchar(32) DEFAULT NULL COMMENT '交易类型，对齐字典 transaction_type。',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型，如 PAYMENT_CREATED、STATUS_CHANGED、CALLBACK_PROCESSED。',
  `event_status` varchar(32) NOT NULL COMMENT '事件发布状态：INIT、PROCESSING、SENT、FAILED、CLOSED。',
  `topic` varchar(128) NOT NULL COMMENT 'RocketMQ Topic。',
  `tag` varchar(128) DEFAULT NULL COMMENT 'RocketMQ Tag。',
  `message_key` varchar(128) NOT NULL COMMENT 'MQ消息Key，下游消费幂等使用。',
  `message_group` varchar(128) DEFAULT NULL COMMENT '顺序消息分组键，如 transaction_id。',
  `payload_json` json NOT NULL COMMENT '事件载荷。',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '发布重试次数。',
  `max_retry_count` int NOT NULL DEFAULT '10' COMMENT '最大重试次数。',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下一次发布重试时间。',
  `sent_time` datetime(3) DEFAULT NULL COMMENT '投递成功时间。',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '最近一次投递失败原因。',
  `event_time` datetime(3) NOT NULL COMMENT '事件产生时间，只做业务排序和重试扫描条件。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间，最终分表字段。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_no` (`event_no`),
  UNIQUE KEY `uk_message_key` (`message_key`),
  KEY `idx_event_status_retry` (`event_status`,`next_retry_time`),
  KEY `idx_event_status_update` (`event_status`,`update_time`),
  KEY `idx_aggregate` (`aggregate_type`,`aggregate_no`),
  KEY `idx_transaction_event` (`transaction_id`,`transaction_date_time`,`event_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000001796 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易本地事务内写入事件，事务提交后可靠投递 RocketMQ。';

-- ----------------------------
-- Table structure for transaction_event_outbox_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_event_outbox_202604`;
CREATE TABLE `transaction_event_outbox_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `event_no` varchar(64) NOT NULL COMMENT '本地事务事件号。',
  `aggregate_type` varchar(64) NOT NULL COMMENT '聚合类型，如 PAYMENT_TRANSACTION、TRANSACTION_CALLBACK。',
  `aggregate_no` varchar(64) NOT NULL COMMENT '聚合标识，建议使用 transaction_id 或 callback_id。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `merchant_id` varchar(64) DEFAULT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) DEFAULT NULL COMMENT '商户订单号，用于商户侧查询和补偿排查。',
  `transaction_type` varchar(32) DEFAULT NULL COMMENT '交易类型，对齐字典 transaction_type。',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型，如 PAYMENT_CREATED、STATUS_CHANGED、CALLBACK_PROCESSED。',
  `event_status` varchar(32) NOT NULL COMMENT '事件发布状态：INIT、PROCESSING、SENT、FAILED、CLOSED。',
  `topic` varchar(128) NOT NULL COMMENT 'RocketMQ Topic。',
  `tag` varchar(128) DEFAULT NULL COMMENT 'RocketMQ Tag。',
  `message_key` varchar(128) NOT NULL COMMENT 'MQ消息Key，下游消费幂等使用。',
  `message_group` varchar(128) DEFAULT NULL COMMENT '顺序消息分组键，如 transaction_id。',
  `payload_json` json NOT NULL COMMENT '事件载荷。',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '发布重试次数。',
  `max_retry_count` int NOT NULL DEFAULT '10' COMMENT '最大重试次数。',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下一次发布重试时间。',
  `sent_time` datetime(3) DEFAULT NULL COMMENT '投递成功时间。',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '最近一次投递失败原因。',
  `event_time` datetime(3) NOT NULL COMMENT '事件产生时间，只做业务排序和重试扫描条件。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间，最终分表字段。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_no` (`event_no`),
  UNIQUE KEY `uk_message_key` (`message_key`),
  KEY `idx_event_status_retry` (`event_status`,`next_retry_time`),
  KEY `idx_event_status_update` (`event_status`,`update_time`),
  KEY `idx_aggregate` (`aggregate_type`,`aggregate_no`),
  KEY `idx_transaction_event` (`transaction_id`,`transaction_date_time`,`event_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易本地事务内写入事件，事务提交后可靠投递 RocketMQ。';

-- ----------------------------
-- Table structure for transaction_finance_state
-- ----------------------------
DROP TABLE IF EXISTS `transaction_finance_state`;
CREATE TABLE `transaction_finance_state` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `finance_state_id` varchar(64) NOT NULL COMMENT '财务状态ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) NOT NULL COMMENT '交易动作ID。',
  `transaction_type` varchar(32) NOT NULL COMMENT '交易类型。',
  `settlement_status` varchar(32) NOT NULL DEFAULT 'NOT_SETTLED' COMMENT '结算状态。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '结算币种。',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '锁定结算直汇率；1单位交易币种兑换的结算币种数量。',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '动作最终有符号结算金额。',
  `settlement_fee_amount` decimal(20,6) DEFAULT NULL COMMENT '从毛结算金额中扣除的结算费用合计。',
  `fee_items_json` json DEFAULT NULL COMMENT '结算费用类目、金额、币种和换汇率明细。',
  `settlement_date` date DEFAULT NULL COMMENT '结算业务日期。',
  `settlement_cycle` varchar(16) DEFAULT NULL COMMENT '结算周期，如 T+0、T+1、T+2。',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '最近一次结算或冲正批次号。',
  `reconciliation_status` varchar(32) NOT NULL DEFAULT 'NOT_RECONCILED' COMMENT '对账状态。',
  `reconciliation_date` date DEFAULT NULL COMMENT '对账业务日期。',
  `reconciliation_batch_no` varchar(64) DEFAULT NULL COMMENT '对账批次号。',
  `accounting_status` varchar(32) NOT NULL DEFAULT 'NOT_ACCOUNTED' COMMENT '入账状态。',
  `accounting_time` datetime(3) DEFAULT NULL COMMENT '入账时间。',
  `channel_fee_currency` char(3) DEFAULT NULL COMMENT '渠道手续费币种。',
  `channel_fee_amount` decimal(20,6) DEFAULT NULL COMMENT '渠道手续费金额。',
  `platform_fee_currency` char(3) DEFAULT NULL COMMENT '平台手续费币种。',
  `platform_fee_amount` decimal(20,6) DEFAULT NULL COMMENT '平台手续费金额。',
  `merchant_receivable_currency` char(3) DEFAULT NULL COMMENT '商户应收币种。',
  `merchant_receivable_amount` decimal(20,6) DEFAULT NULL COMMENT '商户应收金额。',
  `reserve_currency` char(3) DEFAULT NULL COMMENT '保证金币种。',
  `reserve_amount` decimal(20,6) DEFAULT NULL COMMENT '保证金金额。',
  `net_settlement_currency` char(3) DEFAULT NULL COMMENT '净结算币种。',
  `net_settlement_amount` decimal(20,6) DEFAULT NULL COMMENT '净结算金额。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_state_id` (`finance_state_id`),
  UNIQUE KEY `uk_operation_finance` (`operation_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_settlement_status_time` (`settlement_status`,`transaction_date_time`),
  KEY `idx_reconciliation_status_time` (`reconciliation_status`,`transaction_date_time`),
  KEY `idx_accounting_status_time` (`accounting_status`,`transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存交易维度结算、对账、入账当前状态和批次关联。';

-- ----------------------------
-- Table structure for transaction_finance_state_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_finance_state_202603`;
CREATE TABLE `transaction_finance_state_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `finance_state_id` varchar(64) NOT NULL COMMENT '财务状态ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) NOT NULL COMMENT '交易动作ID。',
  `transaction_type` varchar(32) NOT NULL COMMENT '交易类型。',
  `settlement_status` varchar(32) NOT NULL DEFAULT 'NOT_SETTLED' COMMENT '结算状态。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '结算币种。',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '锁定结算直汇率；1单位交易币种兑换的结算币种数量。',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '动作最终有符号结算金额。',
  `settlement_fee_amount` decimal(20,6) DEFAULT NULL COMMENT '从毛结算金额中扣除的结算费用合计。',
  `fee_items_json` json DEFAULT NULL COMMENT '结算费用类目、金额、币种和换汇率明细。',
  `settlement_date` date DEFAULT NULL COMMENT '结算业务日期。',
  `settlement_cycle` varchar(16) DEFAULT NULL COMMENT '结算周期，如 T+0、T+1、T+2。',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '最近一次结算或冲正批次号。',
  `reconciliation_status` varchar(32) NOT NULL DEFAULT 'NOT_RECONCILED' COMMENT '对账状态。',
  `reconciliation_date` date DEFAULT NULL COMMENT '对账业务日期。',
  `reconciliation_batch_no` varchar(64) DEFAULT NULL COMMENT '对账批次号。',
  `accounting_status` varchar(32) NOT NULL DEFAULT 'NOT_ACCOUNTED' COMMENT '入账状态。',
  `accounting_time` datetime(3) DEFAULT NULL COMMENT '入账时间。',
  `channel_fee_currency` char(3) DEFAULT NULL COMMENT '渠道手续费币种。',
  `channel_fee_amount` decimal(20,6) DEFAULT NULL COMMENT '渠道手续费金额。',
  `platform_fee_currency` char(3) DEFAULT NULL COMMENT '平台手续费币种。',
  `platform_fee_amount` decimal(20,6) DEFAULT NULL COMMENT '平台手续费金额。',
  `merchant_receivable_currency` char(3) DEFAULT NULL COMMENT '商户应收币种。',
  `merchant_receivable_amount` decimal(20,6) DEFAULT NULL COMMENT '商户应收金额。',
  `reserve_currency` char(3) DEFAULT NULL COMMENT '保证金币种。',
  `reserve_amount` decimal(20,6) DEFAULT NULL COMMENT '保证金金额。',
  `net_settlement_currency` char(3) DEFAULT NULL COMMENT '净结算币种。',
  `net_settlement_amount` decimal(20,6) DEFAULT NULL COMMENT '净结算金额。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_state_id` (`finance_state_id`),
  UNIQUE KEY `uk_operation_finance` (`operation_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_settlement_status_time` (`settlement_status`,`transaction_date_time`),
  KEY `idx_reconciliation_status_time` (`reconciliation_status`,`transaction_date_time`),
  KEY `idx_accounting_status_time` (`accounting_status`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存交易维度结算、对账、入账当前状态和批次关联。';

-- ----------------------------
-- Table structure for transaction_finance_state_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_finance_state_202604`;
CREATE TABLE `transaction_finance_state_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `finance_state_id` varchar(64) NOT NULL COMMENT '财务状态ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) NOT NULL COMMENT '交易动作ID。',
  `transaction_type` varchar(32) NOT NULL COMMENT '交易类型。',
  `settlement_status` varchar(32) NOT NULL DEFAULT 'NOT_SETTLED' COMMENT '结算状态。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '结算币种。',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '锁定结算直汇率；1单位交易币种兑换的结算币种数量。',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '动作最终有符号结算金额。',
  `settlement_fee_amount` decimal(20,6) DEFAULT NULL COMMENT '从毛结算金额中扣除的结算费用合计。',
  `fee_items_json` json DEFAULT NULL COMMENT '结算费用类目、金额、币种和换汇率明细。',
  `settlement_date` date DEFAULT NULL COMMENT '结算业务日期。',
  `settlement_cycle` varchar(16) DEFAULT NULL COMMENT '结算周期，如 T+0、T+1、T+2。',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '最近一次结算或冲正批次号。',
  `reconciliation_status` varchar(32) NOT NULL DEFAULT 'NOT_RECONCILED' COMMENT '对账状态。',
  `reconciliation_date` date DEFAULT NULL COMMENT '对账业务日期。',
  `reconciliation_batch_no` varchar(64) DEFAULT NULL COMMENT '对账批次号。',
  `accounting_status` varchar(32) NOT NULL DEFAULT 'NOT_ACCOUNTED' COMMENT '入账状态。',
  `accounting_time` datetime(3) DEFAULT NULL COMMENT '入账时间。',
  `channel_fee_currency` char(3) DEFAULT NULL COMMENT '渠道手续费币种。',
  `channel_fee_amount` decimal(20,6) DEFAULT NULL COMMENT '渠道手续费金额。',
  `platform_fee_currency` char(3) DEFAULT NULL COMMENT '平台手续费币种。',
  `platform_fee_amount` decimal(20,6) DEFAULT NULL COMMENT '平台手续费金额。',
  `merchant_receivable_currency` char(3) DEFAULT NULL COMMENT '商户应收币种。',
  `merchant_receivable_amount` decimal(20,6) DEFAULT NULL COMMENT '商户应收金额。',
  `reserve_currency` char(3) DEFAULT NULL COMMENT '保证金币种。',
  `reserve_amount` decimal(20,6) DEFAULT NULL COMMENT '保证金金额。',
  `net_settlement_currency` char(3) DEFAULT NULL COMMENT '净结算币种。',
  `net_settlement_amount` decimal(20,6) DEFAULT NULL COMMENT '净结算金额。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_state_id` (`finance_state_id`),
  UNIQUE KEY `uk_operation_finance` (`operation_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_settlement_status_time` (`settlement_status`,`transaction_date_time`),
  KEY `idx_reconciliation_status_time` (`reconciliation_status`,`transaction_date_time`),
  KEY `idx_accounting_status_time` (`accounting_status`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存交易维度结算、对账、入账当前状态和批次关联。';

-- ----------------------------
-- Table structure for transaction_flow_event
-- ----------------------------
DROP TABLE IF EXISTS `transaction_flow_event`;
CREATE TABLE `transaction_flow_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `flow_event_id` varchar(64) NOT NULL COMMENT '流程事件ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型，如 API_ACCEPTED、PARAM_VALIDATED、MERCHANT_CHECKED、CHANNEL_CALLED、CHANNEL_MATCHED。',
  `event_stage` varchar(64) NOT NULL COMMENT '事件阶段，如 API、VALIDATION、MERCHANT、CONFIG、RISK、ROUTE、CHANNEL、CALLBACK、MQ。',
  `event_status` varchar(32) NOT NULL COMMENT '事件状态，如 SUCCESS、FAILED、SKIPPED。',
  `event_name` varchar(128) NOT NULL COMMENT '页面展示的事件名称。',
  `event_content` varchar(1024) DEFAULT NULL COMMENT '事件摘要说明。',
  `previous_status` varchar(32) DEFAULT NULL COMMENT '事件发生前交易状态。',
  `current_status` varchar(32) DEFAULT NULL COMMENT '事件发生后交易状态。',
  `operator_type` varchar(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '操作者类型：SYSTEM、MERCHANT、CHANNEL、JOB、ADMIN。',
  `operator_id` varchar(128) DEFAULT NULL COMMENT '操作者标识。',
  `reference_type` varchar(64) DEFAULT NULL COMMENT '关联对象类型，如 REQUEST、CALLBACK、OUTBOX。',
  `reference_id` varchar(64) DEFAULT NULL COMMENT '关联对象ID。',
  `error_code` varchar(64) DEFAULT NULL COMMENT '错误码。',
  `error_message` varchar(512) DEFAULT NULL COMMENT '错误摘要。',
  `event_time` datetime(3) NOT NULL COMMENT '事件发生时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_event_id` (`flow_event_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`,`event_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`,`event_time`),
  KEY `idx_event_type_time` (`event_type`,`event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录交易每一个流程事件，用于后台时间线展示和问题追踪。';

-- ----------------------------
-- Table structure for transaction_flow_event_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_flow_event_202603`;
CREATE TABLE `transaction_flow_event_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `flow_event_id` varchar(64) NOT NULL COMMENT '流程事件ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型，如 API_ACCEPTED、PARAM_VALIDATED、MERCHANT_CHECKED、CHANNEL_CALLED、CHANNEL_MATCHED。',
  `event_stage` varchar(64) NOT NULL COMMENT '事件阶段，如 API、VALIDATION、MERCHANT、CONFIG、RISK、ROUTE、CHANNEL、CALLBACK、MQ。',
  `event_status` varchar(32) NOT NULL COMMENT '事件状态，如 SUCCESS、FAILED、SKIPPED。',
  `event_name` varchar(128) NOT NULL COMMENT '页面展示的事件名称。',
  `event_content` varchar(1024) DEFAULT NULL COMMENT '事件摘要说明。',
  `previous_status` varchar(32) DEFAULT NULL COMMENT '事件发生前交易状态。',
  `current_status` varchar(32) DEFAULT NULL COMMENT '事件发生后交易状态。',
  `operator_type` varchar(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '操作者类型：SYSTEM、MERCHANT、CHANNEL、JOB、ADMIN。',
  `operator_id` varchar(128) DEFAULT NULL COMMENT '操作者标识。',
  `reference_type` varchar(64) DEFAULT NULL COMMENT '关联对象类型，如 REQUEST、CALLBACK、OUTBOX。',
  `reference_id` varchar(64) DEFAULT NULL COMMENT '关联对象ID。',
  `error_code` varchar(64) DEFAULT NULL COMMENT '错误码。',
  `error_message` varchar(512) DEFAULT NULL COMMENT '错误摘要。',
  `event_time` datetime(3) NOT NULL COMMENT '事件发生时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_event_id` (`flow_event_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`,`event_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`,`event_time`),
  KEY `idx_event_type_time` (`event_type`,`event_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000006138 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录交易每一个流程事件，用于后台时间线展示和问题追踪。';

-- ----------------------------
-- Table structure for transaction_flow_event_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_flow_event_202604`;
CREATE TABLE `transaction_flow_event_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `flow_event_id` varchar(64) NOT NULL COMMENT '流程事件ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型，如 API_ACCEPTED、PARAM_VALIDATED、MERCHANT_CHECKED、CHANNEL_CALLED、CHANNEL_MATCHED。',
  `event_stage` varchar(64) NOT NULL COMMENT '事件阶段，如 API、VALIDATION、MERCHANT、CONFIG、RISK、ROUTE、CHANNEL、CALLBACK、MQ。',
  `event_status` varchar(32) NOT NULL COMMENT '事件状态，如 SUCCESS、FAILED、SKIPPED。',
  `event_name` varchar(128) NOT NULL COMMENT '页面展示的事件名称。',
  `event_content` varchar(1024) DEFAULT NULL COMMENT '事件摘要说明。',
  `previous_status` varchar(32) DEFAULT NULL COMMENT '事件发生前交易状态。',
  `current_status` varchar(32) DEFAULT NULL COMMENT '事件发生后交易状态。',
  `operator_type` varchar(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '操作者类型：SYSTEM、MERCHANT、CHANNEL、JOB、ADMIN。',
  `operator_id` varchar(128) DEFAULT NULL COMMENT '操作者标识。',
  `reference_type` varchar(64) DEFAULT NULL COMMENT '关联对象类型，如 REQUEST、CALLBACK、OUTBOX。',
  `reference_id` varchar(64) DEFAULT NULL COMMENT '关联对象ID。',
  `error_code` varchar(64) DEFAULT NULL COMMENT '错误码。',
  `error_message` varchar(512) DEFAULT NULL COMMENT '错误摘要。',
  `event_time` datetime(3) NOT NULL COMMENT '事件发生时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_event_id` (`flow_event_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`,`event_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`,`event_time`),
  KEY `idx_event_type_time` (`event_type`,`event_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录交易每一个流程事件，用于后台时间线展示和问题追踪。';

-- ----------------------------
-- Table structure for transaction_idempotency
-- ----------------------------
DROP TABLE IF EXISTS `transaction_idempotency`;
CREATE TABLE `transaction_idempotency` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID，使用系统统一主键规则。',
  `idempotency_scope` varchar(64) NOT NULL COMMENT '幂等范围，如 PAYMENT_CREATE、AUTHORIZATION、CAPTURE、REFUND、CHANNEL_CALLBACK、MQ_CONSUME。',
  `idempotency_key` varchar(256) NOT NULL COMMENT '幂等键，同一范围内唯一；建议由 merchant_id、商户订单/动作号、交易类型等稳定字段组成。',
  `merchant_id` varchar(64) DEFAULT NULL COMMENT '平台商户号；渠道回调或系统任务无法识别商户时可为空。',
  `merchant_order_no` varchar(128) DEFAULT NULL COMMENT '商户订单号，用于商户侧创建交易幂等和排查。',
  `merchant_order_id` varchar(128) DEFAULT NULL COMMENT '商户本次API请求唯一标识，来自 orderInfo.orderId，用于资金类幂等。',
  `transaction_type` varchar(32) DEFAULT NULL COMMENT '交易类型，对齐字典 transaction_type。',
  `transaction_id` varchar(64) DEFAULT NULL COMMENT '平台交易生命周期唯一标识；创建前可为空，成功创建后回填。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作唯一标识；授权、请款、退款等动作创建后回填。',
  `transaction_status` varchar(32) NOT NULL COMMENT '幂等处理对应交易状态，对齐字典 transaction_status。',
  `request_amount` decimal(20,6) DEFAULT NULL COMMENT '请求金额，主币种单位，用于幂等冲突排查。',
  `request_currency` char(3) DEFAULT NULL COMMENT '请求币种，ISO 4217 三位代码。',
  `transaction_amount` decimal(20,6) DEFAULT NULL COMMENT '系统交易金额，主币种单位。',
  `transaction_currency` char(3) DEFAULT NULL COMMENT '系统交易币种，ISO 4217 三位代码。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间；幂等表不分表，但保留交易路由和审计语义。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间，用于跨时区统一排序和审计。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区，如 Asia/Shanghai。',
  `transaction_timezone_offset` varchar(6) DEFAULT NULL COMMENT '交易发生时区偏移，如 +08:00，仅用于审计展示。',
  `request_fingerprint` varchar(128) DEFAULT NULL COMMENT '请求体安全摘要，用于识别同幂等键但请求内容不一致。',
  `result_snapshot` json DEFAULT NULL COMMENT '首次处理结果快照，用于重复请求直接返回。',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '幂等记录过期时间；资金类通常为空表示长期保留。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识，0未删除，1已删除。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_key` (`idempotency_scope`,`idempotency_key`),
  KEY `idx_transaction_id` (`transaction_id`),
  KEY `idx_operation_id` (`operation_id`),
  KEY `idx_merchant_order_type` (`merchant_id`,`merchant_order_no`,`transaction_type`),
  KEY `idx_status_time` (`transaction_status`,`transaction_date_time`),
  KEY `idx_merchant_order_id` (`merchant_id`,`merchant_order_id`,`transaction_type`)
) ENGINE=InnoDB AUTO_INCREMENT=1359 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资金类请求、渠道回调、MQ消费等全局幂等兜底；沿用现有表，不再新建第二套幂等表。';

-- ----------------------------
-- Table structure for transaction_merchant_api_interaction_log
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_api_interaction_log`;
CREATE TABLE `transaction_merchant_api_interaction_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `api_log_id` varchar(64) NOT NULL COMMENT '商户API交互日志ID。',
  `request_id` varchar(128) DEFAULT NULL COMMENT '商户本次API请求唯一标识，通常等于 orderInfo.orderId。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台当前交易唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '平台内部生命周期关联标识。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户订单号。',
  `merchant_order_id` varchar(128) DEFAULT NULL COMMENT '商户请求ID。',
  `api_operation` varchar(32) NOT NULL COMMENT '商户API交易动作，如 PAYMENT、AUTHORIZATION、CAPTURE、REFUND、VOID。',
  `request_path` varchar(512) DEFAULT NULL COMMENT '商户请求路径。',
  `request_time` datetime(3) NOT NULL COMMENT '商户请求进入支付核心时间。',
  `request_result` varchar(32) NOT NULL COMMENT '请求处理结果，如 SUCCESS、FAILED、PROCESSING、PENDING。',
  `request_cipher_digest` varchar(128) DEFAULT NULL COMMENT '商户请求密文安全摘要，禁止保存完整密文。',
  `request_cipher_masked` varchar(128) DEFAULT NULL COMMENT '商户请求密文掩码，只保留首尾短片段用于人工核对。',
  `request_plain_json_masked` mediumtext COMMENT '商户请求脱敏明文 JSON；卡号、CVV、JWT、密钥等敏感字段必须脱敏。',
  `response_time` datetime(3) DEFAULT NULL COMMENT '平台响应生成时间。',
  `response_result` varchar(32) DEFAULT NULL COMMENT '平台响应结果。',
  `merchant_response_code` varchar(32) DEFAULT NULL COMMENT '商户侧可见响应码。',
  `merchant_response_message` varchar(256) DEFAULT NULL COMMENT '商户侧可见响应描述。',
  `response_plain_json_masked` mediumtext COMMENT '系统返回商户的脱敏明文 JSON。',
  `response_cipher_digest` varchar(128) DEFAULT NULL COMMENT '系统响应商户密文摘要；响应加密切面回填前可为空。',
  `response_cipher_masked` varchar(128) DEFAULT NULL COMMENT '系统响应商户密文掩码；响应加密切面回填前可为空。',
  `duration_millis` int DEFAULT NULL COMMENT 'OpenAPI到支付核心处理耗时，单位毫秒。',
  `trace_id` varchar(128) DEFAULT NULL COMMENT '链路追踪ID。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_log_id` (`api_log_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`,`request_time`),
  KEY `idx_merchant_order_time` (`merchant_id`,`merchant_order_no`,`transaction_date_time`),
  KEY `idx_request_id_time` (`request_id`,`transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户OpenAPI请求和平台响应的脱敏审计日志。';

-- ----------------------------
-- Table structure for transaction_merchant_api_interaction_log_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_api_interaction_log_202603`;
CREATE TABLE `transaction_merchant_api_interaction_log_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `api_log_id` varchar(64) NOT NULL COMMENT '商户API交互日志ID。',
  `request_id` varchar(128) DEFAULT NULL COMMENT '商户本次API请求唯一标识，通常等于 orderInfo.orderId。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台当前交易唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '平台内部生命周期关联标识。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户订单号。',
  `merchant_order_id` varchar(128) DEFAULT NULL COMMENT '商户请求ID。',
  `api_operation` varchar(32) NOT NULL COMMENT '商户API交易动作，如 PAYMENT、AUTHORIZATION、CAPTURE、REFUND、VOID。',
  `request_path` varchar(512) DEFAULT NULL COMMENT '商户请求路径。',
  `request_time` datetime(3) NOT NULL COMMENT '商户请求进入支付核心时间。',
  `request_result` varchar(32) NOT NULL COMMENT '请求处理结果，如 SUCCESS、FAILED、PROCESSING、PENDING。',
  `request_cipher_digest` varchar(128) DEFAULT NULL COMMENT '商户请求密文安全摘要，禁止保存完整密文。',
  `request_cipher_masked` varchar(128) DEFAULT NULL COMMENT '商户请求密文掩码，只保留首尾短片段用于人工核对。',
  `request_plain_json_masked` mediumtext COMMENT '商户请求脱敏明文 JSON；卡号、CVV、JWT、密钥等敏感字段必须脱敏。',
  `response_time` datetime(3) DEFAULT NULL COMMENT '平台响应生成时间。',
  `response_result` varchar(32) DEFAULT NULL COMMENT '平台响应结果。',
  `merchant_response_code` varchar(32) DEFAULT NULL COMMENT '商户侧可见响应码。',
  `merchant_response_message` varchar(256) DEFAULT NULL COMMENT '商户侧可见响应描述。',
  `response_plain_json_masked` mediumtext COMMENT '系统返回商户的脱敏明文 JSON。',
  `response_cipher_digest` varchar(128) DEFAULT NULL COMMENT '系统响应商户密文摘要；响应加密切面回填前可为空。',
  `response_cipher_masked` varchar(128) DEFAULT NULL COMMENT '系统响应商户密文掩码；响应加密切面回填前可为空。',
  `duration_millis` int DEFAULT NULL COMMENT 'OpenAPI到支付核心处理耗时，单位毫秒。',
  `trace_id` varchar(128) DEFAULT NULL COMMENT '链路追踪ID。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_log_id` (`api_log_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`,`request_time`),
  KEY `idx_merchant_order_time` (`merchant_id`,`merchant_order_no`,`transaction_date_time`),
  KEY `idx_request_id_time` (`request_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000444 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户OpenAPI请求和平台响应的脱敏审计日志。';

-- ----------------------------
-- Table structure for transaction_merchant_api_interaction_log_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_api_interaction_log_202604`;
CREATE TABLE `transaction_merchant_api_interaction_log_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `api_log_id` varchar(64) NOT NULL COMMENT '商户API交互日志ID。',
  `request_id` varchar(128) DEFAULT NULL COMMENT '商户本次API请求唯一标识，通常等于 orderInfo.orderId。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台当前交易唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '平台内部生命周期关联标识。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户订单号。',
  `merchant_order_id` varchar(128) DEFAULT NULL COMMENT '商户请求ID。',
  `api_operation` varchar(32) NOT NULL COMMENT '商户API交易动作，如 PAYMENT、AUTHORIZATION、CAPTURE、REFUND、VOID。',
  `request_path` varchar(512) DEFAULT NULL COMMENT '商户请求路径。',
  `request_time` datetime(3) NOT NULL COMMENT '商户请求进入支付核心时间。',
  `request_result` varchar(32) NOT NULL COMMENT '请求处理结果，如 SUCCESS、FAILED、PROCESSING、PENDING。',
  `request_cipher_digest` varchar(128) DEFAULT NULL COMMENT '商户请求密文安全摘要，禁止保存完整密文。',
  `request_cipher_masked` varchar(128) DEFAULT NULL COMMENT '商户请求密文掩码，只保留首尾短片段用于人工核对。',
  `request_plain_json_masked` mediumtext COMMENT '商户请求脱敏明文 JSON；卡号、CVV、JWT、密钥等敏感字段必须脱敏。',
  `response_time` datetime(3) DEFAULT NULL COMMENT '平台响应生成时间。',
  `response_result` varchar(32) DEFAULT NULL COMMENT '平台响应结果。',
  `merchant_response_code` varchar(32) DEFAULT NULL COMMENT '商户侧可见响应码。',
  `merchant_response_message` varchar(256) DEFAULT NULL COMMENT '商户侧可见响应描述。',
  `response_plain_json_masked` mediumtext COMMENT '系统返回商户的脱敏明文 JSON。',
  `response_cipher_digest` varchar(128) DEFAULT NULL COMMENT '系统响应商户密文摘要；响应加密切面回填前可为空。',
  `response_cipher_masked` varchar(128) DEFAULT NULL COMMENT '系统响应商户密文掩码；响应加密切面回填前可为空。',
  `duration_millis` int DEFAULT NULL COMMENT 'OpenAPI到支付核心处理耗时，单位毫秒。',
  `trace_id` varchar(128) DEFAULT NULL COMMENT '链路追踪ID。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_log_id` (`api_log_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`,`request_time`),
  KEY `idx_merchant_order_time` (`merchant_id`,`merchant_order_no`,`transaction_date_time`),
  KEY `idx_request_id_time` (`request_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户OpenAPI请求和平台响应的脱敏审计日志。';

-- ----------------------------
-- Table structure for transaction_merchant_notification
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_notification`;
CREATE TABLE `transaction_merchant_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `notify_id` varchar(64) NOT NULL COMMENT '商户通知任务ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户订单号。',
  `notify_type` varchar(64) NOT NULL COMMENT '通知类型，如 PAYMENT_RESULT、REFUND_RESULT、CHARGEBACK。',
  `event_type` varchar(64) NOT NULL COMMENT '触发通知的交易事件类型。',
  `notify_status` varchar(32) NOT NULL COMMENT '通知状态：INIT、PROCESSING、SUCCESS、FAILED、CLOSED。',
  `notify_config_version` varchar(64) DEFAULT NULL COMMENT '商户通知配置版本，来自MQ同步配置。',
  `callback_url` varchar(512) NOT NULL COMMENT '商户回调地址明文；只允许用于通知投递，禁止完整写入日志。',
  `payload_json` mediumtext NOT NULL COMMENT '商户通知业务载荷明文 JSON；发送前使用商户响应公钥加密。',
  `target_url_hash` char(64) NOT NULL COMMENT '通知URL哈希。',
  `target_url_masked` varchar(512) DEFAULT NULL COMMENT '脱敏通知URL，用于后台排查。',
  `payload_json_masked` mediumtext COMMENT '脱敏通知载荷 JSON。',
  `sign_type` varchar(32) DEFAULT NULL COMMENT '商户通知签名方式。',
  `last_attempt_no` int NOT NULL DEFAULT '0' COMMENT '最近一次通知尝试次数。',
  `max_retry_count` int NOT NULL DEFAULT '5' COMMENT '自动通知最大投递次数，当前协议固定为5次。',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下一次重试时间。',
  `success_time` datetime(3) DEFAULT NULL COMMENT '通知成功时间。',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '最近失败原因。',
  `processing_mode` varchar(16) DEFAULT NULL COMMENT '当前执行模式：AUTO 自动投递、MANUAL 人工立即重发。',
  `processing_event_id` varchar(128) DEFAULT NULL COMMENT '当前人工 MQ 事件号；自动投递为空。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notify_id` (`notify_id`),
  UNIQUE KEY `uk_notify_idempotency` (`merchant_id`,`transaction_id`,`notify_type`,`event_type`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_notify_status_retry` (`notify_status`,`next_retry_time`),
  KEY `idx_processing_event` (`processing_event_id`,`transaction_date_time`),
  KEY `idx_merchant_time` (`merchant_id`,`transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存给商户异步通知的任务当前状态、重试计划和成功记录。';

-- ----------------------------
-- Table structure for transaction_merchant_notification_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_notification_202603`;
CREATE TABLE `transaction_merchant_notification_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `notify_id` varchar(64) NOT NULL COMMENT '商户通知任务ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户订单号。',
  `notify_type` varchar(64) NOT NULL COMMENT '通知类型，如 PAYMENT_RESULT、REFUND_RESULT、CHARGEBACK。',
  `event_type` varchar(64) NOT NULL COMMENT '触发通知的交易事件类型。',
  `notify_status` varchar(32) NOT NULL COMMENT '通知状态：INIT、PROCESSING、SUCCESS、FAILED、CLOSED。',
  `notify_config_version` varchar(64) DEFAULT NULL COMMENT '商户通知配置版本，来自MQ同步配置。',
  `callback_url` varchar(512) NOT NULL COMMENT '商户回调地址明文；只允许用于通知投递，禁止完整写入日志。',
  `payload_json` mediumtext NOT NULL COMMENT '商户通知业务载荷明文 JSON；发送前使用商户响应公钥加密。',
  `target_url_hash` char(64) NOT NULL COMMENT '通知URL哈希。',
  `target_url_masked` varchar(512) DEFAULT NULL COMMENT '脱敏通知URL，用于后台排查。',
  `payload_json_masked` mediumtext COMMENT '脱敏通知载荷 JSON。',
  `sign_type` varchar(32) DEFAULT NULL COMMENT '商户通知签名方式。',
  `last_attempt_no` int NOT NULL DEFAULT '0' COMMENT '最近一次通知尝试次数。',
  `max_retry_count` int NOT NULL DEFAULT '5' COMMENT '自动通知最大投递次数，当前协议固定为5次。',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下一次重试时间。',
  `success_time` datetime(3) DEFAULT NULL COMMENT '通知成功时间。',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '最近失败原因。',
  `processing_mode` varchar(16) DEFAULT NULL COMMENT '当前执行模式：AUTO 自动投递、MANUAL 人工立即重发。',
  `processing_event_id` varchar(128) DEFAULT NULL COMMENT '当前人工 MQ 事件号；自动投递为空。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notify_id` (`notify_id`),
  UNIQUE KEY `uk_notify_idempotency` (`merchant_id`,`transaction_id`,`notify_type`,`event_type`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_notify_status_retry` (`notify_status`,`next_retry_time`),
  KEY `idx_processing_event` (`processing_event_id`,`transaction_date_time`),
  KEY `idx_merchant_time` (`merchant_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000001079 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存给商户异步通知的任务当前状态、重试计划和成功记录。';

-- ----------------------------
-- Table structure for transaction_merchant_notification_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_notification_202604`;
CREATE TABLE `transaction_merchant_notification_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `notify_id` varchar(64) NOT NULL COMMENT '商户通知任务ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户订单号。',
  `notify_type` varchar(64) NOT NULL COMMENT '通知类型，如 PAYMENT_RESULT、REFUND_RESULT、CHARGEBACK。',
  `event_type` varchar(64) NOT NULL COMMENT '触发通知的交易事件类型。',
  `notify_status` varchar(32) NOT NULL COMMENT '通知状态：INIT、PROCESSING、SUCCESS、FAILED、CLOSED。',
  `notify_config_version` varchar(64) DEFAULT NULL COMMENT '商户通知配置版本，来自MQ同步配置。',
  `callback_url` varchar(512) NOT NULL COMMENT '商户回调地址明文；只允许用于通知投递，禁止完整写入日志。',
  `payload_json` mediumtext NOT NULL COMMENT '商户通知业务载荷明文 JSON；发送前使用商户响应公钥加密。',
  `target_url_hash` char(64) NOT NULL COMMENT '通知URL哈希。',
  `target_url_masked` varchar(512) DEFAULT NULL COMMENT '脱敏通知URL，用于后台排查。',
  `payload_json_masked` mediumtext COMMENT '脱敏通知载荷 JSON。',
  `sign_type` varchar(32) DEFAULT NULL COMMENT '商户通知签名方式。',
  `last_attempt_no` int NOT NULL DEFAULT '0' COMMENT '最近一次通知尝试次数。',
  `max_retry_count` int NOT NULL DEFAULT '5' COMMENT '自动通知最大投递次数，当前协议固定为5次。',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下一次重试时间。',
  `success_time` datetime(3) DEFAULT NULL COMMENT '通知成功时间。',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '最近失败原因。',
  `processing_mode` varchar(16) DEFAULT NULL COMMENT '当前执行模式：AUTO 自动投递、MANUAL 人工立即重发。',
  `processing_event_id` varchar(128) DEFAULT NULL COMMENT '当前人工 MQ 事件号；自动投递为空。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notify_id` (`notify_id`),
  UNIQUE KEY `uk_notify_idempotency` (`merchant_id`,`transaction_id`,`notify_type`,`event_type`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_notify_status_retry` (`notify_status`,`next_retry_time`),
  KEY `idx_processing_event` (`processing_event_id`,`transaction_date_time`),
  KEY `idx_merchant_time` (`merchant_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存给商户异步通知的任务当前状态、重试计划和成功记录。';

-- ----------------------------
-- Table structure for transaction_merchant_notification_log
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_notification_log`;
CREATE TABLE `transaction_merchant_notification_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `notify_log_id` varchar(64) NOT NULL COMMENT '商户通知日志ID。',
  `notify_id` varchar(64) NOT NULL COMMENT '商户通知任务ID。',
  `callback_event_id` varchar(128) DEFAULT NULL COMMENT '人工重发 MQ 稳定事件号；自动投递为空。',
  `delivery_mode` varchar(16) NOT NULL DEFAULT 'AUTO' COMMENT '投递模式：AUTO 自动计划、MANUAL 人工立即重发。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `attempt_no` int NOT NULL COMMENT '通知尝试次数。',
  `target_url_hash` char(64) NOT NULL COMMENT '商户通知URL哈希。',
  `http_status` int DEFAULT NULL COMMENT 'HTTP状态码。',
  `request_header_json_masked` json DEFAULT NULL COMMENT '脱敏请求头 JSON。',
  `request_body_json_masked` mediumtext COMMENT '脱敏请求体 JSON。',
  `response_body_json_masked` mediumtext COMMENT '脱敏商户响应体 JSON。',
  `success` tinyint NOT NULL DEFAULT '0' COMMENT '通知是否成功。',
  `error_message` varchar(1024) DEFAULT NULL COMMENT '错误摘要。',
  `notify_time` datetime(3) NOT NULL COMMENT '通知发起时间。',
  `duration_millis` int DEFAULT NULL COMMENT '通知耗时毫秒。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notify_log_id` (`notify_log_id`),
  UNIQUE KEY `uk_notify_attempt` (`notify_id`,`attempt_no`),
  UNIQUE KEY `uk_callback_event` (`callback_event_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_notify_time` (`notify_id`,`notify_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存每一次商户通知请求/响应日志。';

-- ----------------------------
-- Table structure for transaction_merchant_notification_log_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_notification_log_202603`;
CREATE TABLE `transaction_merchant_notification_log_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `notify_log_id` varchar(64) NOT NULL COMMENT '商户通知日志ID。',
  `notify_id` varchar(64) NOT NULL COMMENT '商户通知任务ID。',
  `callback_event_id` varchar(128) DEFAULT NULL COMMENT '人工重发 MQ 稳定事件号；自动投递为空。',
  `delivery_mode` varchar(16) NOT NULL DEFAULT 'AUTO' COMMENT '投递模式：AUTO 自动计划、MANUAL 人工立即重发。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `attempt_no` int NOT NULL COMMENT '通知尝试次数。',
  `target_url_hash` char(64) NOT NULL COMMENT '商户通知URL哈希。',
  `http_status` int DEFAULT NULL COMMENT 'HTTP状态码。',
  `request_header_json_masked` json DEFAULT NULL COMMENT '脱敏请求头 JSON。',
  `request_body_json_masked` mediumtext COMMENT '脱敏请求体 JSON。',
  `response_body_json_masked` mediumtext COMMENT '脱敏商户响应体 JSON。',
  `success` tinyint NOT NULL DEFAULT '0' COMMENT '通知是否成功。',
  `error_message` varchar(1024) DEFAULT NULL COMMENT '错误摘要。',
  `notify_time` datetime(3) NOT NULL COMMENT '通知发起时间。',
  `duration_millis` int DEFAULT NULL COMMENT '通知耗时毫秒。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notify_log_id` (`notify_log_id`),
  UNIQUE KEY `uk_notify_attempt` (`notify_id`,`attempt_no`),
  UNIQUE KEY `uk_callback_event` (`callback_event_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_notify_time` (`notify_id`,`notify_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000965 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存每一次商户通知请求/响应日志。';

-- ----------------------------
-- Table structure for transaction_merchant_notification_log_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_notification_log_202604`;
CREATE TABLE `transaction_merchant_notification_log_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `notify_log_id` varchar(64) NOT NULL COMMENT '商户通知日志ID。',
  `notify_id` varchar(64) NOT NULL COMMENT '商户通知任务ID。',
  `callback_event_id` varchar(128) DEFAULT NULL COMMENT '人工重发 MQ 稳定事件号；自动投递为空。',
  `delivery_mode` varchar(16) NOT NULL DEFAULT 'AUTO' COMMENT '投递模式：AUTO 自动计划、MANUAL 人工立即重发。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `attempt_no` int NOT NULL COMMENT '通知尝试次数。',
  `target_url_hash` char(64) NOT NULL COMMENT '商户通知URL哈希。',
  `http_status` int DEFAULT NULL COMMENT 'HTTP状态码。',
  `request_header_json_masked` json DEFAULT NULL COMMENT '脱敏请求头 JSON。',
  `request_body_json_masked` mediumtext COMMENT '脱敏请求体 JSON。',
  `response_body_json_masked` mediumtext COMMENT '脱敏商户响应体 JSON。',
  `success` tinyint NOT NULL DEFAULT '0' COMMENT '通知是否成功。',
  `error_message` varchar(1024) DEFAULT NULL COMMENT '错误摘要。',
  `notify_time` datetime(3) NOT NULL COMMENT '通知发起时间。',
  `duration_millis` int DEFAULT NULL COMMENT '通知耗时毫秒。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notify_log_id` (`notify_log_id`),
  UNIQUE KEY `uk_notify_attempt` (`notify_id`,`attempt_no`),
  UNIQUE KEY `uk_callback_event` (`callback_event_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_notify_time` (`notify_id`,`notify_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存每一次商户通知请求/响应日志。';

-- ----------------------------
-- Table structure for transaction_locator
-- ----------------------------
DROP TABLE IF EXISTS `transaction_locator`;
CREATE TABLE `transaction_locator` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '固定表自增主键。',
  `transaction_id` varchar(64) NOT NULL COMMENT '每一笔交易动作的平台交易 ID。',
  `operation_id` varchar(64) NOT NULL COMMENT '同一交易生命周期共享的内部关联标识。',
  `root_transaction_id` varchar(64) NOT NULL COMMENT '生命周期首笔交易的平台交易 ID。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户原始订单号。',
  `transaction_type` varchar(32) NOT NULL COMMENT '当前动作交易类型。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '当前动作所在交易分表的业务时间。',
  `root_transaction_date_time` datetime(3) NOT NULL COMMENT '生命周期根主单所在交易分表的业务时间。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transaction_id` (`transaction_id`),
  KEY `idx_merchant_transaction` (`merchant_id`,`transaction_id`),
  KEY `idx_merchant_order_root` (`merchant_id`,`merchant_order_no`,`root_transaction_id`),
  KEY `idx_operation_id` (`operation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='非分表交易定位索引；将商户交易标识映射到动作和生命周期根交易的真实季度分片时间。';

-- ----------------------------
-- Table structure for transaction_merchant_snapshot
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_snapshot`;
CREATE TABLE `transaction_merchant_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `snapshot_id` varchar(64) NOT NULL COMMENT '交易商户快照ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID；订单级快照可为空。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `sub_merchant_info_json` json DEFAULT NULL COMMENT '商户上送的完整子商户信息明文 JSON；未上送子商户时为空。',
  `merchant_name` varchar(256) DEFAULT NULL COMMENT '交易发生时商户名称快照。',
  `merchant_country` varchar(3) DEFAULT NULL COMMENT '商户国家/地区，ISO 3166-1 alpha-3。',
  `merchant_category_code` varchar(16) DEFAULT NULL COMMENT 'MCC。',
  `merchant_status` varchar(32) DEFAULT NULL COMMENT '交易发生时商户状态。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `channel_merchant_id` varchar(128) DEFAULT NULL COMMENT '渠道真实MID，来自MID元数据。',
  `terminal_id` varchar(128) DEFAULT NULL COMMENT '终端号。',
  `channel_mid_metadata_json` json DEFAULT NULL COMMENT '交易使用的MID核心元数据快照。',
  `settlement_config_snapshot_json` json DEFAULT NULL COMMENT '结算配置快照。',
  `fee_config_snapshot_json` json DEFAULT NULL COMMENT '费率配置快照。',
  `internal_risk_config_snapshot_json` json DEFAULT NULL COMMENT '内风控配置快照；外风控平台、RequestID、触发时间、审核记录本版不结构化。',
  `route_config_snapshot_json` json DEFAULT NULL COMMENT '路由决策配置快照。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_snapshot_id` (`snapshot_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_merchant_time` (`merchant_id`,`transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存交易发生时的商户、渠道、MID、费率、结算、内风控配置快照，避免后续配置变更影响历史交易解释。';

-- ----------------------------
-- Table structure for transaction_merchant_snapshot_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_snapshot_202603`;
CREATE TABLE `transaction_merchant_snapshot_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `snapshot_id` varchar(64) NOT NULL COMMENT '交易商户快照ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID；订单级快照可为空。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `sub_merchant_info_json` json DEFAULT NULL COMMENT '商户上送的完整子商户信息明文 JSON；未上送子商户时为空。',
  `merchant_name` varchar(256) DEFAULT NULL COMMENT '交易发生时商户名称快照。',
  `merchant_country` varchar(3) DEFAULT NULL COMMENT '商户国家/地区，ISO 3166-1 alpha-3。',
  `merchant_category_code` varchar(16) DEFAULT NULL COMMENT 'MCC。',
  `merchant_status` varchar(32) DEFAULT NULL COMMENT '交易发生时商户状态。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `channel_merchant_id` varchar(128) DEFAULT NULL COMMENT '渠道真实MID，来自MID元数据。',
  `terminal_id` varchar(128) DEFAULT NULL COMMENT '终端号。',
  `channel_mid_metadata_json` json DEFAULT NULL COMMENT '交易使用的MID核心元数据快照。',
  `settlement_config_snapshot_json` json DEFAULT NULL COMMENT '结算配置快照。',
  `fee_config_snapshot_json` json DEFAULT NULL COMMENT '费率配置快照。',
  `internal_risk_config_snapshot_json` json DEFAULT NULL COMMENT '内风控配置快照；外风控平台、RequestID、触发时间、审核记录本版不结构化。',
  `route_config_snapshot_json` json DEFAULT NULL COMMENT '路由决策配置快照。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_snapshot_id` (`snapshot_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_merchant_time` (`merchant_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存交易发生时的商户、渠道、MID、费率、结算、内风控配置快照，避免后续配置变更影响历史交易解释。';

-- ----------------------------
-- Table structure for transaction_merchant_snapshot_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_snapshot_202604`;
CREATE TABLE `transaction_merchant_snapshot_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `snapshot_id` varchar(64) NOT NULL COMMENT '交易商户快照ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID；订单级快照可为空。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `sub_merchant_info_json` json DEFAULT NULL COMMENT '商户上送的完整子商户信息明文 JSON；未上送子商户时为空。',
  `merchant_name` varchar(256) DEFAULT NULL COMMENT '交易发生时商户名称快照。',
  `merchant_country` varchar(3) DEFAULT NULL COMMENT '商户国家/地区，ISO 3166-1 alpha-3。',
  `merchant_category_code` varchar(16) DEFAULT NULL COMMENT 'MCC。',
  `merchant_status` varchar(32) DEFAULT NULL COMMENT '交易发生时商户状态。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `channel_merchant_id` varchar(128) DEFAULT NULL COMMENT '渠道真实MID，来自MID元数据。',
  `terminal_id` varchar(128) DEFAULT NULL COMMENT '终端号。',
  `channel_mid_metadata_json` json DEFAULT NULL COMMENT '交易使用的MID核心元数据快照。',
  `settlement_config_snapshot_json` json DEFAULT NULL COMMENT '结算配置快照。',
  `fee_config_snapshot_json` json DEFAULT NULL COMMENT '费率配置快照。',
  `internal_risk_config_snapshot_json` json DEFAULT NULL COMMENT '内风控配置快照；外风控平台、RequestID、触发时间、审核记录本版不结构化。',
  `route_config_snapshot_json` json DEFAULT NULL COMMENT '路由决策配置快照。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_snapshot_id` (`snapshot_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_merchant_time` (`merchant_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存交易发生时的商户、渠道、MID、费率、结算、内风控配置快照，避免后续配置变更影响历史交易解释。';

-- ----------------------------
-- Table structure for transaction_operation
-- ----------------------------
DROP TABLE IF EXISTS `transaction_operation`;
CREATE TABLE `transaction_operation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `operation_id` varchar(64) NOT NULL COMMENT '平台交易动作唯一标识。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '源交易ID；退款、拒付等关联原交易时使用。',
  `source_operation_id` varchar(64) DEFAULT NULL COMMENT '源动作ID；请款关联授权、退款关联请款或原授权。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户订单号。',
  `merchant_order_id` varchar(128) DEFAULT NULL COMMENT '商户本次API请求唯一标识，旧动作单兼容字段；新持久化模型不再写入。',
  `merchant_operation_no` varchar(128) NOT NULL COMMENT '商户动作单号，如 captureNo、refundNo；首笔可等于 merchant_order_no。',
  `request_source` varchar(32) NOT NULL DEFAULT 'LEGACY_UNKNOWN' COMMENT '动作请求来源',
  `refund_scope` varchar(32) DEFAULT NULL COMMENT 'FULL/PARTIAL/VOID',
  `request_reason` varchar(512) DEFAULT NULL COMMENT '退款或撤销原因',
  `description` varchar(128) DEFAULT NULL COMMENT '商户上送的交易描述快照，用于响应、查询和通知回显。',
  `applicant_type` varchar(32) DEFAULT NULL COMMENT '申请主体类型',
  `applicant_id` varchar(128) DEFAULT NULL COMMENT '申请主体稳定标识',
  `applicant_name` varchar(128) DEFAULT NULL COMMENT '申请人名称快照',
  `execution_mode` varchar(32) DEFAULT NULL COMMENT 'CHANNEL',
  `operation_sequence` int NOT NULL COMMENT '生命周期内动作序号，从1递增。',
  `transaction_type` varchar(32) NOT NULL COMMENT '交易类型，对齐字典 transaction_type。',
  `transaction_status` varchar(32) NOT NULL COMMENT '动作当前交易状态，对齐字典 transaction_status。',
  `process_stage` varchar(64) NOT NULL COMMENT '内部处理阶段。',
  `pending_reason_code` varchar(64) DEFAULT NULL COMMENT '挂起原因码。',
  `fail_reason_code` varchar(64) DEFAULT NULL COMMENT '失败原因码。',
  `fail_reason_message` varchar(512) DEFAULT NULL COMMENT '后台可见失败原因描述。',
  `label_currency` char(3) NOT NULL COMMENT '商户上送/标签币种。',
  `label_amount` decimal(20,6) NOT NULL COMMENT '商户上送/标签金额。',
  `transaction_currency` char(3) NOT NULL COMMENT '系统交易币种。',
  `transaction_amount` decimal(20,6) NOT NULL COMMENT '系统交易金额。',
  `approved_currency` char(3) DEFAULT NULL COMMENT '渠道批准或最终成功币种。',
  `approved_amount` decimal(20,6) DEFAULT NULL COMMENT '渠道批准或最终成功金额。',
  `channel_request_currency` char(3) DEFAULT NULL COMMENT '上送渠道币种。',
  `channel_request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道金额。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '动作最终结算币种。',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '动作最终有符号结算金额。',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '1单位动作交易币种兑换的结算币种数量。',
  `settlement_date` date DEFAULT NULL COMMENT '动作最终结算业务日期。',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '动作最近一次结算或冲正批次号。',
  `currency_exponent` tinyint NOT NULL COMMENT '交易币种小数位精度。',
  `dcc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '当前动作是否启用 DCC。',
  `edc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '当前动作是否启用 EDC。',
  `transaction_rate` decimal(24,12) DEFAULT NULL COMMENT '标签金额转交易金额使用的汇率。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `channel_terminal_id` varchar(128) DEFAULT NULL COMMENT '渠道终端号或子MID。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道订单号。',
  `channel_transaction_id` varchar(128) DEFAULT NULL COMMENT '渠道交易ID。',
  `channel_status` varchar(64) DEFAULT NULL COMMENT '渠道原始交易状态。',
  `channel_response_code` varchar(64) DEFAULT NULL COMMENT '渠道响应码，如 MPGS response.acquirerCode。',
  `channel_response_message` varchar(512) DEFAULT NULL COMMENT '渠道响应摘要。',
  `auth_code` varchar(64) DEFAULT NULL COMMENT '授权码。',
  `rrn` varchar(64) DEFAULT NULL COMMENT '检索参考号或渠道参考号。',
  `acquirer_reference_no` varchar(128) DEFAULT NULL COMMENT '收单机构参考号，用于对账和争议。',
  `settlement_status` varchar(32) NOT NULL DEFAULT 'NOT_SETTLED' COMMENT '当前动作结算状态。',
  `reconciliation_status` varchar(32) NOT NULL DEFAULT 'NOT_RECONCILED' COMMENT '当前动作对账状态。',
  `accounting_status` varchar(32) NOT NULL DEFAULT 'NOT_ACCOUNTED' COMMENT '当前动作入账状态。',
  `channel_match_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '当前动作的渠道结果勾兑/查询确认状态。',
  `channel_match_result` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认结果。',
  `channel_match_count` int NOT NULL DEFAULT '0' COMMENT '当前动作渠道查询确认次数。',
  `last_channel_match_request_id` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认请求ID。',
  `last_channel_match_time` datetime(3) DEFAULT NULL COMMENT '最近一次渠道查询确认时间。',
  `next_channel_match_time` datetime(3) DEFAULT NULL COMMENT '下一次渠道查询确认计划时间。',
  `channel_match_fail_reason` varchar(512) DEFAULT NULL COMMENT '最近一次渠道查询确认失败原因。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间，所有交易分表统一字段。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `operation_time` datetime(3) NOT NULL COMMENT '动作受理时间。',
  `complete_time` datetime(3) DEFAULT NULL COMMENT '动作完成时间。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transaction_id` (`transaction_id`),
  UNIQUE KEY `uk_merchant_operation` (`merchant_id`,`source_transaction_id`,`transaction_type`,`merchant_operation_no`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_source_operation` (`source_operation_id`,`transaction_date_time`),
  KEY `idx_merchant_operation` (`merchant_id`,`merchant_operation_no`,`transaction_type`),
  KEY `idx_status_time` (`transaction_status`,`transaction_date_time`),
  KEY `idx_channel_match_next` (`channel_match_status`,`next_channel_match_time`),
  KEY `idx_channel_order` (`channel_code`,`channel_order_no`,`transaction_date_time`),
  KEY `idx_channel_transaction` (`channel_code`,`channel_transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_source_transaction` (`source_transaction_id`,`transaction_date_time`),
  KEY `idx_merchant_order_id` (`merchant_id`,`merchant_order_id`,`transaction_type`),
  KEY `idx_refund_type_time` (`transaction_type`,`transaction_date_time`,`id`),
  KEY `idx_refund_merchant_time` (`merchant_id`,`transaction_type`,`transaction_date_time`,`id`),
  KEY `idx_pending_fund_balance` (`merchant_id`,`transaction_status`,`settlement_status`,`transaction_type`,`transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易动作单；授权、增量授权、请款、退款、Void、拒付等每一个动作一条记录。';

-- ----------------------------
-- Table structure for transaction_operation_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_operation_202603`;
CREATE TABLE `transaction_operation_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `operation_id` varchar(64) NOT NULL COMMENT '平台交易动作唯一标识。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '源交易ID；退款、拒付等关联原交易时使用。',
  `source_operation_id` varchar(64) DEFAULT NULL COMMENT '源动作ID；请款关联授权、退款关联请款或原授权。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户订单号。',
  `merchant_order_id` varchar(128) DEFAULT NULL COMMENT '商户本次API请求唯一标识，旧动作单兼容字段；新持久化模型不再写入。',
  `merchant_operation_no` varchar(128) NOT NULL COMMENT '商户动作单号，如 captureNo、refundNo；首笔可等于 merchant_order_no。',
  `request_source` varchar(32) NOT NULL DEFAULT 'LEGACY_UNKNOWN' COMMENT '动作请求来源',
  `refund_scope` varchar(32) DEFAULT NULL COMMENT 'FULL/PARTIAL/VOID',
  `request_reason` varchar(512) DEFAULT NULL COMMENT '退款或撤销原因',
  `description` varchar(128) DEFAULT NULL COMMENT '商户上送的交易描述快照，用于响应、查询和通知回显。',
  `applicant_type` varchar(32) DEFAULT NULL COMMENT '申请主体类型',
  `applicant_id` varchar(128) DEFAULT NULL COMMENT '申请主体稳定标识',
  `applicant_name` varchar(128) DEFAULT NULL COMMENT '申请人名称快照',
  `execution_mode` varchar(32) DEFAULT NULL COMMENT 'CHANNEL',
  `operation_sequence` int NOT NULL COMMENT '生命周期内动作序号，从1递增。',
  `transaction_type` varchar(32) NOT NULL COMMENT '交易类型，对齐字典 transaction_type。',
  `transaction_status` varchar(32) NOT NULL COMMENT '动作当前交易状态，对齐字典 transaction_status。',
  `process_stage` varchar(64) NOT NULL COMMENT '内部处理阶段。',
  `pending_reason_code` varchar(64) DEFAULT NULL COMMENT '挂起原因码。',
  `fail_reason_code` varchar(64) DEFAULT NULL COMMENT '失败原因码。',
  `fail_reason_message` varchar(512) DEFAULT NULL COMMENT '后台可见失败原因描述。',
  `label_currency` char(3) NOT NULL COMMENT '商户上送/标签币种。',
  `label_amount` decimal(20,6) NOT NULL COMMENT '商户上送/标签金额。',
  `transaction_currency` char(3) NOT NULL COMMENT '系统交易币种。',
  `transaction_amount` decimal(20,6) NOT NULL COMMENT '系统交易金额。',
  `approved_currency` char(3) DEFAULT NULL COMMENT '渠道批准或最终成功币种。',
  `approved_amount` decimal(20,6) DEFAULT NULL COMMENT '渠道批准或最终成功金额。',
  `channel_request_currency` char(3) DEFAULT NULL COMMENT '上送渠道币种。',
  `channel_request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道金额。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '动作最终结算币种。',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '动作最终有符号结算金额。',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '1单位动作交易币种兑换的结算币种数量。',
  `settlement_date` date DEFAULT NULL COMMENT '动作最终结算业务日期。',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '动作最近一次结算或冲正批次号。',
  `currency_exponent` tinyint NOT NULL COMMENT '交易币种小数位精度。',
  `dcc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '当前动作是否启用 DCC。',
  `edc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '当前动作是否启用 EDC。',
  `transaction_rate` decimal(24,12) DEFAULT NULL COMMENT '标签金额转交易金额使用的汇率。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `channel_terminal_id` varchar(128) DEFAULT NULL COMMENT '渠道终端号或子MID。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道订单号。',
  `channel_transaction_id` varchar(128) DEFAULT NULL COMMENT '渠道交易ID。',
  `channel_status` varchar(64) DEFAULT NULL COMMENT '渠道原始交易状态。',
  `channel_response_code` varchar(64) DEFAULT NULL COMMENT '渠道响应码，如 MPGS response.acquirerCode。',
  `channel_response_message` varchar(512) DEFAULT NULL COMMENT '渠道响应摘要。',
  `auth_code` varchar(64) DEFAULT NULL COMMENT '授权码。',
  `rrn` varchar(64) DEFAULT NULL COMMENT '检索参考号或渠道参考号。',
  `acquirer_reference_no` varchar(128) DEFAULT NULL COMMENT '收单机构参考号，用于对账和争议。',
  `settlement_status` varchar(32) NOT NULL DEFAULT 'NOT_SETTLED' COMMENT '当前动作结算状态。',
  `reconciliation_status` varchar(32) NOT NULL DEFAULT 'NOT_RECONCILED' COMMENT '当前动作对账状态。',
  `accounting_status` varchar(32) NOT NULL DEFAULT 'NOT_ACCOUNTED' COMMENT '当前动作入账状态。',
  `channel_match_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '当前动作的渠道结果勾兑/查询确认状态。',
  `channel_match_result` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认结果。',
  `channel_match_count` int NOT NULL DEFAULT '0' COMMENT '当前动作渠道查询确认次数。',
  `last_channel_match_request_id` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认请求ID。',
  `last_channel_match_time` datetime(3) DEFAULT NULL COMMENT '最近一次渠道查询确认时间。',
  `next_channel_match_time` datetime(3) DEFAULT NULL COMMENT '下一次渠道查询确认计划时间。',
  `channel_match_fail_reason` varchar(512) DEFAULT NULL COMMENT '最近一次渠道查询确认失败原因。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间，所有交易分表统一字段。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `operation_time` datetime(3) NOT NULL COMMENT '动作受理时间。',
  `complete_time` datetime(3) DEFAULT NULL COMMENT '动作完成时间。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transaction_id` (`transaction_id`),
  UNIQUE KEY `uk_merchant_operation` (`merchant_id`,`source_transaction_id`,`transaction_type`,`merchant_operation_no`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_source_operation` (`source_operation_id`,`transaction_date_time`),
  KEY `idx_merchant_operation` (`merchant_id`,`merchant_operation_no`,`transaction_type`),
  KEY `idx_status_time` (`transaction_status`,`transaction_date_time`),
  KEY `idx_channel_match_next` (`channel_match_status`,`next_channel_match_time`),
  KEY `idx_channel_order` (`channel_code`,`channel_order_no`,`transaction_date_time`),
  KEY `idx_channel_transaction` (`channel_code`,`channel_transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_source_transaction` (`source_transaction_id`,`transaction_date_time`),
  KEY `idx_merchant_order_id` (`merchant_id`,`merchant_order_id`,`transaction_type`),
  KEY `idx_refund_type_time` (`transaction_type`,`transaction_date_time`,`id`),
  KEY `idx_refund_merchant_time` (`merchant_id`,`transaction_type`,`transaction_date_time`,`id`),
  KEY `idx_pending_fund_balance` (`merchant_id`,`transaction_status`,`settlement_status`,`transaction_type`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000001230 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易动作单；授权、增量授权、请款、退款、Void、拒付等每一个动作一条记录。';

-- ----------------------------
-- Table structure for transaction_operation_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_operation_202604`;
CREATE TABLE `transaction_operation_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `operation_id` varchar(64) NOT NULL COMMENT '平台交易动作唯一标识。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '源交易ID；退款、拒付等关联原交易时使用。',
  `source_operation_id` varchar(64) DEFAULT NULL COMMENT '源动作ID；请款关联授权、退款关联请款或原授权。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户订单号。',
  `merchant_order_id` varchar(128) DEFAULT NULL COMMENT '商户本次API请求唯一标识，旧动作单兼容字段；新持久化模型不再写入。',
  `merchant_operation_no` varchar(128) NOT NULL COMMENT '商户动作单号，如 captureNo、refundNo；首笔可等于 merchant_order_no。',
  `request_source` varchar(32) NOT NULL DEFAULT 'LEGACY_UNKNOWN' COMMENT '动作请求来源',
  `refund_scope` varchar(32) DEFAULT NULL COMMENT 'FULL/PARTIAL/VOID',
  `request_reason` varchar(512) DEFAULT NULL COMMENT '退款或撤销原因',
  `description` varchar(128) DEFAULT NULL COMMENT '商户上送的交易描述快照，用于响应、查询和通知回显。',
  `applicant_type` varchar(32) DEFAULT NULL COMMENT '申请主体类型',
  `applicant_id` varchar(128) DEFAULT NULL COMMENT '申请主体稳定标识',
  `applicant_name` varchar(128) DEFAULT NULL COMMENT '申请人名称快照',
  `execution_mode` varchar(32) DEFAULT NULL COMMENT 'CHANNEL',
  `operation_sequence` int NOT NULL COMMENT '生命周期内动作序号，从1递增。',
  `transaction_type` varchar(32) NOT NULL COMMENT '交易类型，对齐字典 transaction_type。',
  `transaction_status` varchar(32) NOT NULL COMMENT '动作当前交易状态，对齐字典 transaction_status。',
  `process_stage` varchar(64) NOT NULL COMMENT '内部处理阶段。',
  `pending_reason_code` varchar(64) DEFAULT NULL COMMENT '挂起原因码。',
  `fail_reason_code` varchar(64) DEFAULT NULL COMMENT '失败原因码。',
  `fail_reason_message` varchar(512) DEFAULT NULL COMMENT '后台可见失败原因描述。',
  `label_currency` char(3) NOT NULL COMMENT '商户上送/标签币种。',
  `label_amount` decimal(20,6) NOT NULL COMMENT '商户上送/标签金额。',
  `transaction_currency` char(3) NOT NULL COMMENT '系统交易币种。',
  `transaction_amount` decimal(20,6) NOT NULL COMMENT '系统交易金额。',
  `approved_currency` char(3) DEFAULT NULL COMMENT '渠道批准或最终成功币种。',
  `approved_amount` decimal(20,6) DEFAULT NULL COMMENT '渠道批准或最终成功金额。',
  `channel_request_currency` char(3) DEFAULT NULL COMMENT '上送渠道币种。',
  `channel_request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道金额。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '动作最终结算币种。',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '动作最终有符号结算金额。',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '1单位动作交易币种兑换的结算币种数量。',
  `settlement_date` date DEFAULT NULL COMMENT '动作最终结算业务日期。',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '动作最近一次结算或冲正批次号。',
  `currency_exponent` tinyint NOT NULL COMMENT '交易币种小数位精度。',
  `dcc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '当前动作是否启用 DCC。',
  `edc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '当前动作是否启用 EDC。',
  `transaction_rate` decimal(24,12) DEFAULT NULL COMMENT '标签金额转交易金额使用的汇率。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `channel_terminal_id` varchar(128) DEFAULT NULL COMMENT '渠道终端号或子MID。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道订单号。',
  `channel_transaction_id` varchar(128) DEFAULT NULL COMMENT '渠道交易ID。',
  `channel_status` varchar(64) DEFAULT NULL COMMENT '渠道原始交易状态。',
  `channel_response_code` varchar(64) DEFAULT NULL COMMENT '渠道响应码，如 MPGS response.acquirerCode。',
  `channel_response_message` varchar(512) DEFAULT NULL COMMENT '渠道响应摘要。',
  `auth_code` varchar(64) DEFAULT NULL COMMENT '授权码。',
  `rrn` varchar(64) DEFAULT NULL COMMENT '检索参考号或渠道参考号。',
  `acquirer_reference_no` varchar(128) DEFAULT NULL COMMENT '收单机构参考号，用于对账和争议。',
  `settlement_status` varchar(32) NOT NULL DEFAULT 'NOT_SETTLED' COMMENT '当前动作结算状态。',
  `reconciliation_status` varchar(32) NOT NULL DEFAULT 'NOT_RECONCILED' COMMENT '当前动作对账状态。',
  `accounting_status` varchar(32) NOT NULL DEFAULT 'NOT_ACCOUNTED' COMMENT '当前动作入账状态。',
  `channel_match_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '当前动作的渠道结果勾兑/查询确认状态。',
  `channel_match_result` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认结果。',
  `channel_match_count` int NOT NULL DEFAULT '0' COMMENT '当前动作渠道查询确认次数。',
  `last_channel_match_request_id` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认请求ID。',
  `last_channel_match_time` datetime(3) DEFAULT NULL COMMENT '最近一次渠道查询确认时间。',
  `next_channel_match_time` datetime(3) DEFAULT NULL COMMENT '下一次渠道查询确认计划时间。',
  `channel_match_fail_reason` varchar(512) DEFAULT NULL COMMENT '最近一次渠道查询确认失败原因。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间，所有交易分表统一字段。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `operation_time` datetime(3) NOT NULL COMMENT '动作受理时间。',
  `complete_time` datetime(3) DEFAULT NULL COMMENT '动作完成时间。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transaction_id` (`transaction_id`),
  UNIQUE KEY `uk_merchant_operation` (`merchant_id`,`source_transaction_id`,`transaction_type`,`merchant_operation_no`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_source_operation` (`source_operation_id`,`transaction_date_time`),
  KEY `idx_merchant_operation` (`merchant_id`,`merchant_operation_no`,`transaction_type`),
  KEY `idx_status_time` (`transaction_status`,`transaction_date_time`),
  KEY `idx_channel_match_next` (`channel_match_status`,`next_channel_match_time`),
  KEY `idx_channel_order` (`channel_code`,`channel_order_no`,`transaction_date_time`),
  KEY `idx_channel_transaction` (`channel_code`,`channel_transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_source_transaction` (`source_transaction_id`,`transaction_date_time`),
  KEY `idx_merchant_order_id` (`merchant_id`,`merchant_order_id`,`transaction_type`),
  KEY `idx_refund_type_time` (`transaction_type`,`transaction_date_time`,`id`),
  KEY `idx_refund_merchant_time` (`merchant_id`,`transaction_type`,`transaction_date_time`,`id`),
  KEY `idx_pending_fund_balance` (`merchant_id`,`transaction_status`,`settlement_status`,`transaction_type`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易动作单；授权、增量授权、请款、退款、Void、拒付等每一个动作一条记录。';

-- ----------------------------
-- Table structure for transaction_order
-- ----------------------------
DROP TABLE IF EXISTS `transaction_order`;
CREATE TABLE `transaction_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID，季度物理表可按系统分表规则设置自增起始值。',
  `operation_id` varchar(64) NOT NULL COMMENT '平台内部生命周期关联标识，同一原始交易生命周期内保持不变，不返回商户。',
  `root_transaction_id` varchar(64) NOT NULL COMMENT '生命周期内首个平台开户交易ID。',
  `latest_transaction_id` varchar(64) NOT NULL COMMENT '最近一次平台开户交易ID。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户原始订单号，用于商户查询和创建交易幂等。',
  `merchant_order_id` varchar(128) NOT NULL COMMENT '商户本次API请求唯一标识，来自 orderInfo.orderId，用于幂等和排查。',
  `merchant_transaction_id` varchar(128) DEFAULT NULL COMMENT '商户侧交易ID，来自 OpenAPI 请求。',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '源平台交易ID；用于复制、补单、争议等扩展关联。',
  `payment_method` varchar(32) NOT NULL COMMENT '支付方式，如 CARD、APPLE_PAY、GOOGLE_PAY。',
  `payment_brand` varchar(32) DEFAULT NULL COMMENT '卡品牌或支付品牌，如 VISA、MASTERCARD。',
  `transaction_type` varchar(32) NOT NULL COMMENT '首个交易类型，对齐字典 transaction_type。',
  `transaction_status` varchar(32) NOT NULL COMMENT '生命周期当前交易状态，对齐字典 transaction_status。',
  `process_stage` varchar(64) NOT NULL COMMENT '内部处理阶段，如 ACCEPTED、WAITING_3DS、WAITING_CALLBACK。',
  `pending_reason_code` varchar(64) DEFAULT NULL COMMENT '挂起原因码，仅 PENDING 时使用。',
  `fail_reason_code` varchar(64) DEFAULT NULL COMMENT '失败原因码，用于后台展示真实原因。',
  `fail_reason_message` varchar(512) DEFAULT NULL COMMENT '后台可见失败原因描述。',
  `merchant_visible_message` varchar(512) DEFAULT NULL COMMENT '商户可见的模糊失败原因。',
  `payer_visible_message` varchar(512) DEFAULT NULL COMMENT '付款人可见的模糊失败原因。',
  `label_currency` char(3) NOT NULL COMMENT '标签币种，即商户上送/页面展示的原始交易币种。',
  `label_amount` decimal(20,6) NOT NULL COMMENT '标签金额，即商户上送/页面展示的原始交易金额。',
  `transaction_currency` char(3) NOT NULL COMMENT '系统交易币种，经过 DCC/EDC 后用于交易核心处理。',
  `transaction_amount` decimal(20,6) NOT NULL COMMENT '系统交易金额，经过 DCC/EDC 后用于交易核心处理。',
  `channel_request_currency` char(3) DEFAULT NULL COMMENT '上送渠道的币种。',
  `channel_request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道的金额。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '最近真实动作最终结算币种。',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '最近真实动作最终有符号结算金额。',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '最近真实动作中1单位交易币种兑换的结算币种数量。',
  `settlement_date` date DEFAULT NULL COMMENT '最近真实动作结算业务日期。',
  `currency_exponent` tinyint NOT NULL COMMENT '交易币种小数位精度，如 USD=2、JPY=0。',
  `dcc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用 DCC，0否，1是。',
  `edc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用 EDC，0否，1是；渠道不支持商户币种时使用。',
  `transaction_rate` decimal(24,12) DEFAULT NULL COMMENT '标签金额转交易金额使用的汇率。',
  `rate_source` varchar(64) DEFAULT NULL COMMENT '汇率来源，如 PLATFORM、CHANNEL、MERCHANT。',
  `rate_time` datetime(3) DEFAULT NULL COMMENT '汇率生效或报价时间。',
  `authorized_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计授权成功金额，交易币种单位。',
  `authorized_cancel_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计授权取消、预授权取消或未请款金额释放成功金额，交易币种单位。',
  `captured_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计请款成功金额，交易币种单位。',
  `refunded_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计退款成功金额，交易币种单位。',
  `chargeback_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计拒付金额，交易币种单位。',
  `available_capture_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '当前可请款金额，交易币种单位。',
  `available_refund_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '当前可退款金额，交易币种单位。',
  `settlement_status` varchar(32) NOT NULL DEFAULT 'NOT_SETTLED' COMMENT '结算状态，建议进入字典 settlement_status。',
  `reconciliation_status` varchar(32) NOT NULL DEFAULT 'NOT_RECONCILED' COMMENT '对账状态，建议进入字典 reconciliation_status。',
  `accounting_status` varchar(32) NOT NULL DEFAULT 'NOT_ACCOUNTED' COMMENT '入账状态，建议进入字典 accounting_status。',
  `channel_match_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '渠道结果勾兑/查询确认状态；定时任务按该字段调用渠道查询API同步一致。',
  `channel_match_result` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认结果，如 SAME、STATUS_CHANGED、AMOUNT_CHANGED、CHANNEL_NOT_FOUND。',
  `channel_match_count` int NOT NULL DEFAULT '0' COMMENT '渠道结果勾兑查询次数。',
  `last_channel_match_request_id` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认请求ID，关联 transaction_channel_request.request_id。',
  `last_channel_match_time` datetime(3) DEFAULT NULL COMMENT '最近一次渠道查询确认时间。',
  `next_channel_match_time` datetime(3) DEFAULT NULL COMMENT '下一次渠道查询确认计划时间。',
  `channel_match_fail_reason` varchar(512) DEFAULT NULL COMMENT '最近一次渠道查询确认失败原因。',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '最近一次结算或冲正批次号。',
  `settlement_transaction_id` varchar(64) DEFAULT NULL COMMENT '当前结算快照来源的真实动作交易号。',
  `settlement_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '当前结算快照来源的真实动作分片时间。',
  `reconciliation_batch_no` varchar(64) DEFAULT NULL COMMENT '最近一次对账批次号。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID，关联 channel_info.id。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码，如 MPGS。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '交易使用的渠道MID配置ID。',
  `channel_merchant_id` varchar(128) DEFAULT NULL COMMENT '渠道真实商户号或渠道MID，来自MID元数据快照。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道侧主订单号。',
  `internal_risk_decision` varchar(32) DEFAULT NULL COMMENT '最近一次内风控决策，如 PASS、REJECT、REVIEW、REQUIRE_3DS；外风控结构化字段本版不设计。',
  `internal_risk_record_no` varchar(64) DEFAULT NULL COMMENT '最近一次内风控评估流水号；不承载外风控平台RequestID。',
  `merchant_website` varchar(512) DEFAULT NULL COMMENT '首次支付、授权或预授权请求中的商户网站原始URL，用于来源网址限定和查询回显。',
  `callback_url` varchar(512) DEFAULT NULL COMMENT '商户异步通知地址明文；未上送时为空，禁止完整写入日志。',
  `redirect_url` varchar(512) DEFAULT NULL COMMENT 'Hosted Checkout 结果页 Form POST 地址明文；Direct API 或未上送时为空。',
  `language` varchar(20) DEFAULT NULL COMMENT 'Hosted Checkout 页面语言，如 zh-CN、en-US。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间，所有交易分表统一字段。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `transaction_timezone_offset` varchar(6) DEFAULT NULL COMMENT '交易发生时区偏移，如 +08:00。',
  `last_status_time` datetime(3) NOT NULL COMMENT '最近一次交易状态更新时间。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，用于状态机 CAS 更新。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识，0未删除，1已删除。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_operation_id` (`operation_id`),
  UNIQUE KEY `uk_root_transaction_id` (`root_transaction_id`),
  KEY `idx_merchant_order_time` (`merchant_id`,`merchant_order_no`,`transaction_date_time`),
  KEY `idx_status_time` (`transaction_status`,`transaction_date_time`),
  KEY `idx_channel_match_next` (`channel_match_status`,`next_channel_match_time`),
  KEY `idx_settlement_time` (`settlement_status`,`transaction_date_time`),
  KEY `idx_reconciliation_time` (`reconciliation_status`,`transaction_date_time`),
  KEY `idx_channel_order` (`channel_code`,`channel_order_no`,`transaction_date_time`),
  KEY `idx_latest_transaction_id` (`latest_transaction_id`),
  KEY `idx_merchant_order_id` (`merchant_id`,`merchant_order_id`,`transaction_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易生命周期主单；同一笔原始交易的授权、增量授权、请款、退款、拒付等动作共用同一个 transaction_id。';

-- ----------------------------
-- Table structure for transaction_order_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_order_202603`;
CREATE TABLE `transaction_order_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID，季度物理表可按系统分表规则设置自增起始值。',
  `operation_id` varchar(64) NOT NULL COMMENT '平台内部生命周期关联标识，同一原始交易生命周期内保持不变，不返回商户。',
  `root_transaction_id` varchar(64) NOT NULL COMMENT '生命周期内首个平台开户交易ID。',
  `latest_transaction_id` varchar(64) NOT NULL COMMENT '最近一次平台开户交易ID。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户原始订单号，用于商户查询和创建交易幂等。',
  `merchant_order_id` varchar(128) NOT NULL COMMENT '商户本次API请求唯一标识，来自 orderInfo.orderId，用于幂等和排查。',
  `merchant_transaction_id` varchar(128) DEFAULT NULL COMMENT '商户侧交易ID，来自 OpenAPI 请求。',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '源平台交易ID；用于复制、补单、争议等扩展关联。',
  `payment_method` varchar(32) NOT NULL COMMENT '支付方式，如 CARD、APPLE_PAY、GOOGLE_PAY。',
  `payment_brand` varchar(32) DEFAULT NULL COMMENT '卡品牌或支付品牌，如 VISA、MASTERCARD。',
  `transaction_type` varchar(32) NOT NULL COMMENT '首个交易类型，对齐字典 transaction_type。',
  `transaction_status` varchar(32) NOT NULL COMMENT '生命周期当前交易状态，对齐字典 transaction_status。',
  `process_stage` varchar(64) NOT NULL COMMENT '内部处理阶段，如 ACCEPTED、WAITING_3DS、WAITING_CALLBACK。',
  `pending_reason_code` varchar(64) DEFAULT NULL COMMENT '挂起原因码，仅 PENDING 时使用。',
  `fail_reason_code` varchar(64) DEFAULT NULL COMMENT '失败原因码，用于后台展示真实原因。',
  `fail_reason_message` varchar(512) DEFAULT NULL COMMENT '后台可见失败原因描述。',
  `merchant_visible_message` varchar(512) DEFAULT NULL COMMENT '商户可见的模糊失败原因。',
  `payer_visible_message` varchar(512) DEFAULT NULL COMMENT '付款人可见的模糊失败原因。',
  `label_currency` char(3) NOT NULL COMMENT '标签币种，即商户上送/页面展示的原始交易币种。',
  `label_amount` decimal(20,6) NOT NULL COMMENT '标签金额，即商户上送/页面展示的原始交易金额。',
  `transaction_currency` char(3) NOT NULL COMMENT '系统交易币种，经过 DCC/EDC 后用于交易核心处理。',
  `transaction_amount` decimal(20,6) NOT NULL COMMENT '系统交易金额，经过 DCC/EDC 后用于交易核心处理。',
  `channel_request_currency` char(3) DEFAULT NULL COMMENT '上送渠道的币种。',
  `channel_request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道的金额。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '最近真实动作最终结算币种。',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '最近真实动作最终有符号结算金额。',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '最近真实动作中1单位交易币种兑换的结算币种数量。',
  `settlement_date` date DEFAULT NULL COMMENT '最近真实动作结算业务日期。',
  `currency_exponent` tinyint NOT NULL COMMENT '交易币种小数位精度，如 USD=2、JPY=0。',
  `dcc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用 DCC，0否，1是。',
  `edc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用 EDC，0否，1是；渠道不支持商户币种时使用。',
  `transaction_rate` decimal(24,12) DEFAULT NULL COMMENT '标签金额转交易金额使用的汇率。',
  `rate_source` varchar(64) DEFAULT NULL COMMENT '汇率来源，如 PLATFORM、CHANNEL、MERCHANT。',
  `rate_time` datetime(3) DEFAULT NULL COMMENT '汇率生效或报价时间。',
  `authorized_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计授权成功金额，交易币种单位。',
  `authorized_cancel_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计授权取消、预授权取消或未请款金额释放成功金额，交易币种单位。',
  `captured_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计请款成功金额，交易币种单位。',
  `refunded_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计退款成功金额，交易币种单位。',
  `chargeback_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计拒付金额，交易币种单位。',
  `available_capture_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '当前可请款金额，交易币种单位。',
  `available_refund_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '当前可退款金额，交易币种单位。',
  `settlement_status` varchar(32) NOT NULL DEFAULT 'NOT_SETTLED' COMMENT '结算状态，建议进入字典 settlement_status。',
  `reconciliation_status` varchar(32) NOT NULL DEFAULT 'NOT_RECONCILED' COMMENT '对账状态，建议进入字典 reconciliation_status。',
  `accounting_status` varchar(32) NOT NULL DEFAULT 'NOT_ACCOUNTED' COMMENT '入账状态，建议进入字典 accounting_status。',
  `channel_match_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '渠道结果勾兑/查询确认状态；定时任务按该字段调用渠道查询API同步一致。',
  `channel_match_result` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认结果，如 SAME、STATUS_CHANGED、AMOUNT_CHANGED、CHANNEL_NOT_FOUND。',
  `channel_match_count` int NOT NULL DEFAULT '0' COMMENT '渠道结果勾兑查询次数。',
  `last_channel_match_request_id` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认请求ID，关联 transaction_channel_request.request_id。',
  `last_channel_match_time` datetime(3) DEFAULT NULL COMMENT '最近一次渠道查询确认时间。',
  `next_channel_match_time` datetime(3) DEFAULT NULL COMMENT '下一次渠道查询确认计划时间。',
  `channel_match_fail_reason` varchar(512) DEFAULT NULL COMMENT '最近一次渠道查询确认失败原因。',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '最近一次结算或冲正批次号。',
  `settlement_transaction_id` varchar(64) DEFAULT NULL COMMENT '当前结算快照来源的真实动作交易号。',
  `settlement_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '当前结算快照来源的真实动作分片时间。',
  `reconciliation_batch_no` varchar(64) DEFAULT NULL COMMENT '最近一次对账批次号。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID，关联 channel_info.id。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码，如 MPGS。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '交易使用的渠道MID配置ID。',
  `channel_merchant_id` varchar(128) DEFAULT NULL COMMENT '渠道真实商户号或渠道MID，来自MID元数据快照。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道侧主订单号。',
  `internal_risk_decision` varchar(32) DEFAULT NULL COMMENT '最近一次内风控决策，如 PASS、REJECT、REVIEW、REQUIRE_3DS；外风控结构化字段本版不设计。',
  `internal_risk_record_no` varchar(64) DEFAULT NULL COMMENT '最近一次内风控评估流水号；不承载外风控平台RequestID。',
  `merchant_website` varchar(512) DEFAULT NULL COMMENT '首次支付、授权或预授权请求中的商户网站原始URL，用于来源网址限定和查询回显。',
  `callback_url` varchar(512) DEFAULT NULL COMMENT '商户异步通知地址明文；未上送时为空，禁止完整写入日志。',
  `redirect_url` varchar(512) DEFAULT NULL COMMENT 'Hosted Checkout 结果页 Form POST 地址明文；Direct API 或未上送时为空。',
  `language` varchar(20) DEFAULT NULL COMMENT 'Hosted Checkout 页面语言，如 zh-CN、en-US。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间，所有交易分表统一字段。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `transaction_timezone_offset` varchar(6) DEFAULT NULL COMMENT '交易发生时区偏移，如 +08:00。',
  `last_status_time` datetime(3) NOT NULL COMMENT '最近一次交易状态更新时间。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，用于状态机 CAS 更新。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识，0未删除，1已删除。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_operation_id` (`operation_id`),
  UNIQUE KEY `uk_root_transaction_id` (`root_transaction_id`),
  KEY `idx_merchant_order_time` (`merchant_id`,`merchant_order_no`,`transaction_date_time`),
  KEY `idx_status_time` (`transaction_status`,`transaction_date_time`),
  KEY `idx_channel_match_next` (`channel_match_status`,`next_channel_match_time`),
  KEY `idx_settlement_time` (`settlement_status`,`transaction_date_time`),
  KEY `idx_reconciliation_time` (`reconciliation_status`,`transaction_date_time`),
  KEY `idx_channel_order` (`channel_code`,`channel_order_no`,`transaction_date_time`),
  KEY `idx_latest_transaction_id` (`latest_transaction_id`),
  KEY `idx_merchant_order_id` (`merchant_id`,`merchant_order_id`,`transaction_type`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000950 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易生命周期主单；同一笔原始交易的授权、增量授权、请款、退款、拒付等动作共用同一个 transaction_id。';

-- ----------------------------
-- Table structure for transaction_order_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_order_202604`;
CREATE TABLE `transaction_order_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID，季度物理表可按系统分表规则设置自增起始值。',
  `operation_id` varchar(64) NOT NULL COMMENT '平台内部生命周期关联标识，同一原始交易生命周期内保持不变，不返回商户。',
  `root_transaction_id` varchar(64) NOT NULL COMMENT '生命周期内首个平台开户交易ID。',
  `latest_transaction_id` varchar(64) NOT NULL COMMENT '最近一次平台开户交易ID。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户原始订单号，用于商户查询和创建交易幂等。',
  `merchant_order_id` varchar(128) NOT NULL COMMENT '商户本次API请求唯一标识，来自 orderInfo.orderId，用于幂等和排查。',
  `merchant_transaction_id` varchar(128) DEFAULT NULL COMMENT '商户侧交易ID，来自 OpenAPI 请求。',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '源平台交易ID；用于复制、补单、争议等扩展关联。',
  `payment_method` varchar(32) NOT NULL COMMENT '支付方式，如 CARD、APPLE_PAY、GOOGLE_PAY。',
  `payment_brand` varchar(32) DEFAULT NULL COMMENT '卡品牌或支付品牌，如 VISA、MASTERCARD。',
  `transaction_type` varchar(32) NOT NULL COMMENT '首个交易类型，对齐字典 transaction_type。',
  `transaction_status` varchar(32) NOT NULL COMMENT '生命周期当前交易状态，对齐字典 transaction_status。',
  `process_stage` varchar(64) NOT NULL COMMENT '内部处理阶段，如 ACCEPTED、WAITING_3DS、WAITING_CALLBACK。',
  `pending_reason_code` varchar(64) DEFAULT NULL COMMENT '挂起原因码，仅 PENDING 时使用。',
  `fail_reason_code` varchar(64) DEFAULT NULL COMMENT '失败原因码，用于后台展示真实原因。',
  `fail_reason_message` varchar(512) DEFAULT NULL COMMENT '后台可见失败原因描述。',
  `merchant_visible_message` varchar(512) DEFAULT NULL COMMENT '商户可见的模糊失败原因。',
  `payer_visible_message` varchar(512) DEFAULT NULL COMMENT '付款人可见的模糊失败原因。',
  `label_currency` char(3) NOT NULL COMMENT '标签币种，即商户上送/页面展示的原始交易币种。',
  `label_amount` decimal(20,6) NOT NULL COMMENT '标签金额，即商户上送/页面展示的原始交易金额。',
  `transaction_currency` char(3) NOT NULL COMMENT '系统交易币种，经过 DCC/EDC 后用于交易核心处理。',
  `transaction_amount` decimal(20,6) NOT NULL COMMENT '系统交易金额，经过 DCC/EDC 后用于交易核心处理。',
  `channel_request_currency` char(3) DEFAULT NULL COMMENT '上送渠道的币种。',
  `channel_request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道的金额。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '最近真实动作最终结算币种。',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '最近真实动作最终有符号结算金额。',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '最近真实动作中1单位交易币种兑换的结算币种数量。',
  `settlement_date` date DEFAULT NULL COMMENT '最近真实动作结算业务日期。',
  `currency_exponent` tinyint NOT NULL COMMENT '交易币种小数位精度，如 USD=2、JPY=0。',
  `dcc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用 DCC，0否，1是。',
  `edc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用 EDC，0否，1是；渠道不支持商户币种时使用。',
  `transaction_rate` decimal(24,12) DEFAULT NULL COMMENT '标签金额转交易金额使用的汇率。',
  `rate_source` varchar(64) DEFAULT NULL COMMENT '汇率来源，如 PLATFORM、CHANNEL、MERCHANT。',
  `rate_time` datetime(3) DEFAULT NULL COMMENT '汇率生效或报价时间。',
  `authorized_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计授权成功金额，交易币种单位。',
  `authorized_cancel_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计授权取消、预授权取消或未请款金额释放成功金额，交易币种单位。',
  `captured_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计请款成功金额，交易币种单位。',
  `refunded_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计退款成功金额，交易币种单位。',
  `chargeback_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计拒付金额，交易币种单位。',
  `available_capture_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '当前可请款金额，交易币种单位。',
  `available_refund_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '当前可退款金额，交易币种单位。',
  `settlement_status` varchar(32) NOT NULL DEFAULT 'NOT_SETTLED' COMMENT '结算状态，建议进入字典 settlement_status。',
  `reconciliation_status` varchar(32) NOT NULL DEFAULT 'NOT_RECONCILED' COMMENT '对账状态，建议进入字典 reconciliation_status。',
  `accounting_status` varchar(32) NOT NULL DEFAULT 'NOT_ACCOUNTED' COMMENT '入账状态，建议进入字典 accounting_status。',
  `channel_match_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '渠道结果勾兑/查询确认状态；定时任务按该字段调用渠道查询API同步一致。',
  `channel_match_result` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认结果，如 SAME、STATUS_CHANGED、AMOUNT_CHANGED、CHANNEL_NOT_FOUND。',
  `channel_match_count` int NOT NULL DEFAULT '0' COMMENT '渠道结果勾兑查询次数。',
  `last_channel_match_request_id` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认请求ID，关联 transaction_channel_request.request_id。',
  `last_channel_match_time` datetime(3) DEFAULT NULL COMMENT '最近一次渠道查询确认时间。',
  `next_channel_match_time` datetime(3) DEFAULT NULL COMMENT '下一次渠道查询确认计划时间。',
  `channel_match_fail_reason` varchar(512) DEFAULT NULL COMMENT '最近一次渠道查询确认失败原因。',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '最近一次结算或冲正批次号。',
  `settlement_transaction_id` varchar(64) DEFAULT NULL COMMENT '当前结算快照来源的真实动作交易号。',
  `settlement_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '当前结算快照来源的真实动作分片时间。',
  `reconciliation_batch_no` varchar(64) DEFAULT NULL COMMENT '最近一次对账批次号。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID，关联 channel_info.id。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码，如 MPGS。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '交易使用的渠道MID配置ID。',
  `channel_merchant_id` varchar(128) DEFAULT NULL COMMENT '渠道真实商户号或渠道MID，来自MID元数据快照。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道侧主订单号。',
  `internal_risk_decision` varchar(32) DEFAULT NULL COMMENT '最近一次内风控决策，如 PASS、REJECT、REVIEW、REQUIRE_3DS；外风控结构化字段本版不设计。',
  `internal_risk_record_no` varchar(64) DEFAULT NULL COMMENT '最近一次内风控评估流水号；不承载外风控平台RequestID。',
  `merchant_website` varchar(512) DEFAULT NULL COMMENT '首次支付、授权或预授权请求中的商户网站原始URL，用于来源网址限定和查询回显。',
  `callback_url` varchar(512) DEFAULT NULL COMMENT '商户异步通知地址明文；未上送时为空，禁止完整写入日志。',
  `redirect_url` varchar(512) DEFAULT NULL COMMENT 'Hosted Checkout 结果页 Form POST 地址明文；Direct API 或未上送时为空。',
  `language` varchar(20) DEFAULT NULL COMMENT 'Hosted Checkout 页面语言，如 zh-CN、en-US。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间，所有交易分表统一字段。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `transaction_timezone_offset` varchar(6) DEFAULT NULL COMMENT '交易发生时区偏移，如 +08:00。',
  `last_status_time` datetime(3) NOT NULL COMMENT '最近一次交易状态更新时间。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，用于状态机 CAS 更新。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识，0未删除，1已删除。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_operation_id` (`operation_id`),
  UNIQUE KEY `uk_root_transaction_id` (`root_transaction_id`),
  KEY `idx_merchant_order_time` (`merchant_id`,`merchant_order_no`,`transaction_date_time`),
  KEY `idx_status_time` (`transaction_status`,`transaction_date_time`),
  KEY `idx_channel_match_next` (`channel_match_status`,`next_channel_match_time`),
  KEY `idx_settlement_time` (`settlement_status`,`transaction_date_time`),
  KEY `idx_reconciliation_time` (`reconciliation_status`,`transaction_date_time`),
  KEY `idx_channel_order` (`channel_code`,`channel_order_no`,`transaction_date_time`),
  KEY `idx_latest_transaction_id` (`latest_transaction_id`),
  KEY `idx_merchant_order_id` (`merchant_id`,`merchant_order_id`,`transaction_type`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易生命周期主单；同一笔原始交易的授权、增量授权、请款、退款、拒付等动作共用同一个 transaction_id。';

-- ----------------------------
-- Table structure for transaction_payer_info
-- ----------------------------
DROP TABLE IF EXISTS `transaction_payer_info`;
CREATE TABLE `transaction_payer_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `payer_info_id` varchar(64) NOT NULL COMMENT '付款人信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `payer_id` varchar(64) DEFAULT NULL COMMENT '商户侧付款人ID或客户ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '付款人名，按商户请求快照明文保存。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '付款人姓，按商户请求快照明文保存。',
  `phone` varchar(32) DEFAULT NULL COMMENT '付款人电话明文，禁止普通日志输出。',
  `email` varchar(64) DEFAULT NULL COMMENT '付款人邮箱明文，禁止普通日志输出。',
  `country` varchar(3) DEFAULT NULL COMMENT '付款人国家/地区，ISO 3166-1 alpha-3。',
  `state` varchar(64) DEFAULT NULL COMMENT '付款人州、省或地区。',
  `city` varchar(64) DEFAULT NULL COMMENT '付款人城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '付款人街道地址明文。',
  `postal` varchar(32) DEFAULT NULL COMMENT '付款人邮编。',
  `ip_address` varchar(64) NOT NULL COMMENT '付款人客户端 IP 明文，参与 AML、黑白名单和区域风险校验。',
  `session_id` varchar(128) DEFAULT NULL COMMENT '付款会话 ID 明文。',
  `browser_info_json` json DEFAULT NULL COMMENT '商户上送的浏览器信息明文 JSON。',
  `user_agent` varchar(512) DEFAULT NULL COMMENT '付款人 User-Agent 明文，禁止普通日志输出。',
  `payer_email_hash` char(64) DEFAULT NULL COMMENT '付款人邮箱哈希。',
  `payer_phone_hash` char(64) DEFAULT NULL COMMENT '付款人手机号哈希。',
  `ip_address_hash` char(64) DEFAULT NULL COMMENT '付款人 IP 地址 SHA-256 摘要，用于名单索引和审计关联。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payer_info_id` (`payer_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_country_time` (`country`,`transaction_date_time`),
  KEY `idx_email_hash` (`payer_email_hash`,`transaction_date_time`),
  KEY `idx_phone_hash` (`payer_phone_hash`,`transaction_date_time`),
  KEY `idx_ip_hash` (`ip_address_hash`,`transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户上送的付款人身份、联系方式、IP 和浏览器信息明文快照，供风控、审计和商户查询回显使用。';

-- ----------------------------
-- Table structure for transaction_payer_info_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_payer_info_202603`;
CREATE TABLE `transaction_payer_info_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `payer_info_id` varchar(64) NOT NULL COMMENT '付款人信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `payer_id` varchar(64) DEFAULT NULL COMMENT '商户侧付款人ID或客户ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '付款人名，按商户请求快照明文保存。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '付款人姓，按商户请求快照明文保存。',
  `phone` varchar(32) DEFAULT NULL COMMENT '付款人电话明文，禁止普通日志输出。',
  `email` varchar(64) DEFAULT NULL COMMENT '付款人邮箱明文，禁止普通日志输出。',
  `country` varchar(3) DEFAULT NULL COMMENT '付款人国家/地区，ISO 3166-1 alpha-3。',
  `state` varchar(64) DEFAULT NULL COMMENT '付款人州、省或地区。',
  `city` varchar(64) DEFAULT NULL COMMENT '付款人城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '付款人街道地址明文。',
  `postal` varchar(32) DEFAULT NULL COMMENT '付款人邮编。',
  `ip_address` varchar(64) NOT NULL COMMENT '付款人客户端 IP 明文，参与 AML、黑白名单和区域风险校验。',
  `session_id` varchar(128) DEFAULT NULL COMMENT '付款会话 ID 明文。',
  `browser_info_json` json DEFAULT NULL COMMENT '商户上送的浏览器信息明文 JSON。',
  `user_agent` varchar(512) DEFAULT NULL COMMENT '付款人 User-Agent 明文，禁止普通日志输出。',
  `payer_email_hash` char(64) DEFAULT NULL COMMENT '付款人邮箱哈希。',
  `payer_phone_hash` char(64) DEFAULT NULL COMMENT '付款人手机号哈希。',
  `ip_address_hash` char(64) DEFAULT NULL COMMENT '付款人 IP 地址 SHA-256 摘要，用于名单索引和审计关联。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payer_info_id` (`payer_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_country_time` (`country`,`transaction_date_time`),
  KEY `idx_email_hash` (`payer_email_hash`,`transaction_date_time`),
  KEY `idx_phone_hash` (`payer_phone_hash`,`transaction_date_time`),
  KEY `idx_ip_hash` (`ip_address_hash`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户上送的付款人身份、联系方式、IP 和浏览器信息明文快照，供风控、审计和商户查询回显使用。';

-- ----------------------------
-- Table structure for transaction_payer_info_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_payer_info_202604`;
CREATE TABLE `transaction_payer_info_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `payer_info_id` varchar(64) NOT NULL COMMENT '付款人信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `payer_id` varchar(64) DEFAULT NULL COMMENT '商户侧付款人ID或客户ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '付款人名，按商户请求快照明文保存。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '付款人姓，按商户请求快照明文保存。',
  `phone` varchar(32) DEFAULT NULL COMMENT '付款人电话明文，禁止普通日志输出。',
  `email` varchar(64) DEFAULT NULL COMMENT '付款人邮箱明文，禁止普通日志输出。',
  `country` varchar(3) DEFAULT NULL COMMENT '付款人国家/地区，ISO 3166-1 alpha-3。',
  `state` varchar(64) DEFAULT NULL COMMENT '付款人州、省或地区。',
  `city` varchar(64) DEFAULT NULL COMMENT '付款人城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '付款人街道地址明文。',
  `postal` varchar(32) DEFAULT NULL COMMENT '付款人邮编。',
  `ip_address` varchar(64) NOT NULL COMMENT '付款人客户端 IP 明文，参与 AML、黑白名单和区域风险校验。',
  `session_id` varchar(128) DEFAULT NULL COMMENT '付款会话 ID 明文。',
  `browser_info_json` json DEFAULT NULL COMMENT '商户上送的浏览器信息明文 JSON。',
  `user_agent` varchar(512) DEFAULT NULL COMMENT '付款人 User-Agent 明文，禁止普通日志输出。',
  `payer_email_hash` char(64) DEFAULT NULL COMMENT '付款人邮箱哈希。',
  `payer_phone_hash` char(64) DEFAULT NULL COMMENT '付款人手机号哈希。',
  `ip_address_hash` char(64) DEFAULT NULL COMMENT '付款人 IP 地址 SHA-256 摘要，用于名单索引和审计关联。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payer_info_id` (`payer_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_country_time` (`country`,`transaction_date_time`),
  KEY `idx_email_hash` (`payer_email_hash`,`transaction_date_time`),
  KEY `idx_phone_hash` (`payer_phone_hash`,`transaction_date_time`),
  KEY `idx_ip_hash` (`ip_address_hash`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户上送的付款人身份、联系方式、IP 和浏览器信息明文快照，供风控、审计和商户查询回显使用。';

-- ----------------------------
-- Table structure for transaction_payment_method_info
-- ----------------------------
DROP TABLE IF EXISTS `transaction_payment_method_info`;
CREATE TABLE `transaction_payment_method_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `payment_info_id` varchar(64) NOT NULL COMMENT '支付工具信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `payment_method` varchar(32) NOT NULL COMMENT '支付方式，如 CARD、APPLE_PAY。',
  `payment_brand` varchar(32) DEFAULT NULL COMMENT '卡品牌或钱包品牌，如 VISA、MASTERCARD。',
  `card_bin` varchar(12) DEFAULT NULL COMMENT '卡BIN，可按合规要求保留前6或前8。',
  `card_last4` varchar(4) DEFAULT NULL COMMENT '卡号后四位。',
  `card_number_masked` varchar(32) DEFAULT NULL COMMENT '脱敏卡号，如 512345******0008。',
  `cardholder_name_masked` varchar(128) DEFAULT NULL COMMENT '持卡人姓名脱敏展示值。',
  `expiry_month` varchar(2) DEFAULT NULL COMMENT '卡有效期月份。',
  `expiry_year` varchar(4) DEFAULT NULL COMMENT '卡有效期年份。',
  `token_id` varchar(128) DEFAULT NULL COMMENT '平台或渠道支付令牌ID。',
  `wallet_type` varchar(32) DEFAULT NULL COMMENT '钱包类型。',
  `payment_account_hash` char(64) DEFAULT NULL COMMENT '支付账户哈希，用于风控和排查。',
  `issuer_country` varchar(3) DEFAULT NULL COMMENT '发卡国家/地区。',
  `funding_method` varchar(32) DEFAULT NULL COMMENT '资金类型，如 CREDIT、DEBIT。',
  `three_ds_indicator` varchar(32) DEFAULT NULL COMMENT '3DS标识或策略结果。',
  `csc_result` varchar(32) DEFAULT NULL COMMENT 'CVV/CSC校验结果；不保存CVV原文。',
  `avs_result` varchar(32) DEFAULT NULL COMMENT 'AVS校验结果。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_info_id` (`payment_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_account_hash` (`payment_account_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存卡、钱包、令牌、渠道绑卡标识等支付工具摘要。';

-- ----------------------------
-- Table structure for transaction_payment_method_info_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_payment_method_info_202603`;
CREATE TABLE `transaction_payment_method_info_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `payment_info_id` varchar(64) NOT NULL COMMENT '支付工具信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `payment_method` varchar(32) NOT NULL COMMENT '支付方式，如 CARD、APPLE_PAY。',
  `payment_brand` varchar(32) DEFAULT NULL COMMENT '卡品牌或钱包品牌，如 VISA、MASTERCARD。',
  `card_bin` varchar(12) DEFAULT NULL COMMENT '卡BIN，可按合规要求保留前6或前8。',
  `card_last4` varchar(4) DEFAULT NULL COMMENT '卡号后四位。',
  `card_number_masked` varchar(32) DEFAULT NULL COMMENT '脱敏卡号，如 512345******0008。',
  `cardholder_name_masked` varchar(128) DEFAULT NULL COMMENT '持卡人姓名脱敏展示值。',
  `expiry_month` varchar(2) DEFAULT NULL COMMENT '卡有效期月份。',
  `expiry_year` varchar(4) DEFAULT NULL COMMENT '卡有效期年份。',
  `token_id` varchar(128) DEFAULT NULL COMMENT '平台或渠道支付令牌ID。',
  `wallet_type` varchar(32) DEFAULT NULL COMMENT '钱包类型。',
  `payment_account_hash` char(64) DEFAULT NULL COMMENT '支付账户哈希，用于风控和排查。',
  `issuer_country` varchar(3) DEFAULT NULL COMMENT '发卡国家/地区。',
  `funding_method` varchar(32) DEFAULT NULL COMMENT '资金类型，如 CREDIT、DEBIT。',
  `three_ds_indicator` varchar(32) DEFAULT NULL COMMENT '3DS标识或策略结果。',
  `csc_result` varchar(32) DEFAULT NULL COMMENT 'CVV/CSC校验结果；不保存CVV原文。',
  `avs_result` varchar(32) DEFAULT NULL COMMENT 'AVS校验结果。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_info_id` (`payment_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_account_hash` (`payment_account_hash`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000001212 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存卡、钱包、令牌、渠道绑卡标识等支付工具摘要。';

-- ----------------------------
-- Table structure for transaction_payment_method_info_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_payment_method_info_202604`;
CREATE TABLE `transaction_payment_method_info_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `payment_info_id` varchar(64) NOT NULL COMMENT '支付工具信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `payment_method` varchar(32) NOT NULL COMMENT '支付方式，如 CARD、APPLE_PAY。',
  `payment_brand` varchar(32) DEFAULT NULL COMMENT '卡品牌或钱包品牌，如 VISA、MASTERCARD。',
  `card_bin` varchar(12) DEFAULT NULL COMMENT '卡BIN，可按合规要求保留前6或前8。',
  `card_last4` varchar(4) DEFAULT NULL COMMENT '卡号后四位。',
  `card_number_masked` varchar(32) DEFAULT NULL COMMENT '脱敏卡号，如 512345******0008。',
  `cardholder_name_masked` varchar(128) DEFAULT NULL COMMENT '持卡人姓名脱敏展示值。',
  `expiry_month` varchar(2) DEFAULT NULL COMMENT '卡有效期月份。',
  `expiry_year` varchar(4) DEFAULT NULL COMMENT '卡有效期年份。',
  `token_id` varchar(128) DEFAULT NULL COMMENT '平台或渠道支付令牌ID。',
  `wallet_type` varchar(32) DEFAULT NULL COMMENT '钱包类型。',
  `payment_account_hash` char(64) DEFAULT NULL COMMENT '支付账户哈希，用于风控和排查。',
  `issuer_country` varchar(3) DEFAULT NULL COMMENT '发卡国家/地区。',
  `funding_method` varchar(32) DEFAULT NULL COMMENT '资金类型，如 CREDIT、DEBIT。',
  `three_ds_indicator` varchar(32) DEFAULT NULL COMMENT '3DS标识或策略结果。',
  `csc_result` varchar(32) DEFAULT NULL COMMENT 'CVV/CSC校验结果；不保存CVV原文。',
  `avs_result` varchar(32) DEFAULT NULL COMMENT 'AVS校验结果。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_info_id` (`payment_info_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_account_hash` (`payment_account_hash`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存卡、钱包、令牌、渠道绑卡标识等支付工具摘要。';

-- ----------------------------
-- Table structure for transaction_product_item
-- ----------------------------
DROP TABLE IF EXISTS `transaction_product_item`;
CREATE TABLE `transaction_product_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `product_item_id` varchar(64) NOT NULL COMMENT '商品明细ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID；订单级商品明细可为空。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户订单号。',
  `item_sequence` int NOT NULL COMMENT '商品行序号，从1递增。',
  `product_code` varchar(128) DEFAULT NULL COMMENT '商户侧商品编码或SKU。',
  `product_name` varchar(256) DEFAULT NULL COMMENT '交易发生时商品名称快照。',
  `product_category` varchar(128) DEFAULT NULL COMMENT '商品分类。',
  `quantity` decimal(20,6) NOT NULL DEFAULT '1.000000' COMMENT '购买数量，支持小数数量场景。',
  `unit_price_currency` char(3) DEFAULT NULL COMMENT '商品单价币种。',
  `unit_price_amount` decimal(20,6) DEFAULT NULL COMMENT '商品单价金额。',
  `item_currency` char(3) NOT NULL COMMENT '商品行金额币种。',
  `item_amount` decimal(20,6) NOT NULL COMMENT '商品行金额，等于数量、单价、折扣、税费等计算后的行金额。',
  `tax_currency` char(3) DEFAULT NULL COMMENT '商品行税费币种。',
  `tax_amount` decimal(20,6) DEFAULT NULL COMMENT '商品行税费金额。',
  `discount_currency` char(3) DEFAULT NULL COMMENT '商品行折扣币种。',
  `discount_amount` decimal(20,6) DEFAULT NULL COMMENT '商品行折扣金额。',
  `description` varchar(512) DEFAULT NULL COMMENT '商品描述摘要。',
  `product_extra_json` json DEFAULT NULL COMMENT '商品扩展摘要。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_item_id` (`product_item_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_product_code_time` (`product_code`,`transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户上送的商品/订单项目明细，支撑交易详情页商品列表、风控摘要、争议和人工排查。';

-- ----------------------------
-- Table structure for transaction_product_item_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_product_item_202603`;
CREATE TABLE `transaction_product_item_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `product_item_id` varchar(64) NOT NULL COMMENT '商品明细ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID；订单级商品明细可为空。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户订单号。',
  `item_sequence` int NOT NULL COMMENT '商品行序号，从1递增。',
  `product_code` varchar(128) DEFAULT NULL COMMENT '商户侧商品编码或SKU。',
  `product_name` varchar(256) DEFAULT NULL COMMENT '交易发生时商品名称快照。',
  `product_category` varchar(128) DEFAULT NULL COMMENT '商品分类。',
  `quantity` decimal(20,6) NOT NULL DEFAULT '1.000000' COMMENT '购买数量，支持小数数量场景。',
  `unit_price_currency` char(3) DEFAULT NULL COMMENT '商品单价币种。',
  `unit_price_amount` decimal(20,6) DEFAULT NULL COMMENT '商品单价金额。',
  `item_currency` char(3) NOT NULL COMMENT '商品行金额币种。',
  `item_amount` decimal(20,6) NOT NULL COMMENT '商品行金额，等于数量、单价、折扣、税费等计算后的行金额。',
  `tax_currency` char(3) DEFAULT NULL COMMENT '商品行税费币种。',
  `tax_amount` decimal(20,6) DEFAULT NULL COMMENT '商品行税费金额。',
  `discount_currency` char(3) DEFAULT NULL COMMENT '商品行折扣币种。',
  `discount_amount` decimal(20,6) DEFAULT NULL COMMENT '商品行折扣金额。',
  `description` varchar(512) DEFAULT NULL COMMENT '商品描述摘要。',
  `product_extra_json` json DEFAULT NULL COMMENT '商品扩展摘要。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_item_id` (`product_item_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_product_code_time` (`product_code`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户上送的商品/订单项目明细，支撑交易详情页商品列表、风控摘要、争议和人工排查。';

-- ----------------------------
-- Table structure for transaction_product_item_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_product_item_202604`;
CREATE TABLE `transaction_product_item_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `product_item_id` varchar(64) NOT NULL COMMENT '商品明细ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID；订单级商品明细可为空。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `merchant_order_no` varchar(128) NOT NULL COMMENT '商户订单号。',
  `item_sequence` int NOT NULL COMMENT '商品行序号，从1递增。',
  `product_code` varchar(128) DEFAULT NULL COMMENT '商户侧商品编码或SKU。',
  `product_name` varchar(256) DEFAULT NULL COMMENT '交易发生时商品名称快照。',
  `product_category` varchar(128) DEFAULT NULL COMMENT '商品分类。',
  `quantity` decimal(20,6) NOT NULL DEFAULT '1.000000' COMMENT '购买数量，支持小数数量场景。',
  `unit_price_currency` char(3) DEFAULT NULL COMMENT '商品单价币种。',
  `unit_price_amount` decimal(20,6) DEFAULT NULL COMMENT '商品单价金额。',
  `item_currency` char(3) NOT NULL COMMENT '商品行金额币种。',
  `item_amount` decimal(20,6) NOT NULL COMMENT '商品行金额，等于数量、单价、折扣、税费等计算后的行金额。',
  `tax_currency` char(3) DEFAULT NULL COMMENT '商品行税费币种。',
  `tax_amount` decimal(20,6) DEFAULT NULL COMMENT '商品行税费金额。',
  `discount_currency` char(3) DEFAULT NULL COMMENT '商品行折扣币种。',
  `discount_amount` decimal(20,6) DEFAULT NULL COMMENT '商品行折扣金额。',
  `description` varchar(512) DEFAULT NULL COMMENT '商品描述摘要。',
  `product_extra_json` json DEFAULT NULL COMMENT '商品扩展摘要。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_item_id` (`product_item_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_product_code_time` (`product_code`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户上送的商品/订单项目明细，支撑交易详情页商品列表、风控摘要、争议和人工排查。';

-- ----------------------------
-- Table structure for transaction_refund_approval
-- ----------------------------
DROP TABLE IF EXISTS `transaction_refund_approval`;
CREATE TABLE `transaction_refund_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `approval_id` varchar(64) NOT NULL COMMENT '退款审批单号',
  `refund_transaction_id` varchar(64) NOT NULL COMMENT '退款动作交易号',
  `refund_transaction_date_time` datetime(3) NOT NULL COMMENT '退款动作分片时间',
  `source_transaction_id` varchar(64) NOT NULL COMMENT '源交易号',
  `source_transaction_date_time` datetime(3) NOT NULL COMMENT '源动作分片时间',
  `root_transaction_date_time` datetime(3) NOT NULL COMMENT '生命周期主单分片时间',
  `merchant_id` varchar(64) NOT NULL COMMENT '商户号',
  `approval_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/EXPIRED',
  `approval_policy_code` varchar(64) NOT NULL COMMENT '审批策略编码',
  `approval_policy_snapshot` json NOT NULL COMMENT '非敏感审批策略快照',
  `current_approval_level` tinyint NOT NULL DEFAULT '1' COMMENT '当前审批层级',
  `total_approval_levels` tinyint NOT NULL DEFAULT '1' COMMENT '总审批层级',
  `applicant_type` varchar(32) NOT NULL COMMENT '申请主体类型',
  `applicant_id` varchar(128) NOT NULL COMMENT '申请主体稳定标识',
  `applicant_name` varchar(128) DEFAULT NULL COMMENT '申请人显示名快照',
  `approval_operator_id` varchar(128) DEFAULT NULL COMMENT '最终审批账号',
  `approval_operator_name` varchar(128) DEFAULT NULL COMMENT '审批人显示名快照',
  `approval_time` datetime(3) DEFAULT NULL COMMENT '审批完成时间',
  `approval_reason` varchar(512) DEFAULT NULL COMMENT '审批意见',
  `expire_time` datetime(3) NOT NULL COMMENT '审批过期时间',
  `decision_request_id` varchar(64) DEFAULT NULL COMMENT '审批命令幂等号',
  `execution_event_id` varchar(64) DEFAULT NULL COMMENT '退款执行事件号',
  `version` int NOT NULL DEFAULT '0' COMMENT 'CAS 版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_approval_id` (`approval_id`),
  UNIQUE KEY `uk_refund_transaction` (`refund_transaction_id`),
  UNIQUE KEY `uk_refund_decision_request` (`decision_request_id`),
  KEY `idx_refund_approval_queue` (`approval_status`,`create_time`,`id`),
  KEY `idx_refund_approval_expire` (`approval_status`,`expire_time`,`id`),
  KEY `idx_refund_approval_merchant` (`merchant_id`,`approval_status`,`create_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款审批工作队列';

-- ----------------------------
-- Table structure for transaction_status_history
-- ----------------------------
DROP TABLE IF EXISTS `transaction_status_history`;
CREATE TABLE `transaction_status_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `status_history_id` varchar(64) NOT NULL COMMENT '状态历史ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `status_object` varchar(32) NOT NULL COMMENT '状态对象：ORDER、OPERATION、FINANCE、NOTIFICATION。',
  `from_status` varchar(32) DEFAULT NULL COMMENT '流转前状态。',
  `to_status` varchar(32) NOT NULL COMMENT '流转后状态。',
  `trigger_type` varchar(64) NOT NULL COMMENT '触发类型：API、CHANNEL_RESPONSE、CHANNEL_CALLBACK、CHANNEL_QUERY_CONFIRM、JOB、ADMIN、RECONCILIATION。',
  `trigger_id` varchar(64) DEFAULT NULL COMMENT '触发对象ID，如 request_id、callback_id、job_log_id。',
  `transition_result` varchar(32) NOT NULL COMMENT '流转结果：SUCCESS、REJECTED、IGNORED。',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '流转失败或忽略原因。',
  `version_before` int DEFAULT NULL COMMENT '流转前版本号。',
  `version_after` int DEFAULT NULL COMMENT '流转后版本号。',
  `status_time` datetime(3) NOT NULL COMMENT '状态流转时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_status_history_id` (`status_history_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`,`status_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`,`status_time`),
  KEY `idx_status_object_time` (`status_object`,`to_status`,`status_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录订单、动作、财务状态机每一次流转，支持审计和问题追踪。';

-- ----------------------------
-- Table structure for transaction_status_history_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_status_history_202603`;
CREATE TABLE `transaction_status_history_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `status_history_id` varchar(64) NOT NULL COMMENT '状态历史ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `status_object` varchar(32) NOT NULL COMMENT '状态对象：ORDER、OPERATION、FINANCE、NOTIFICATION。',
  `from_status` varchar(32) DEFAULT NULL COMMENT '流转前状态。',
  `to_status` varchar(32) NOT NULL COMMENT '流转后状态。',
  `trigger_type` varchar(64) NOT NULL COMMENT '触发类型：API、CHANNEL_RESPONSE、CHANNEL_CALLBACK、CHANNEL_QUERY_CONFIRM、JOB、ADMIN、RECONCILIATION。',
  `trigger_id` varchar(64) DEFAULT NULL COMMENT '触发对象ID，如 request_id、callback_id、job_log_id。',
  `transition_result` varchar(32) NOT NULL COMMENT '流转结果：SUCCESS、REJECTED、IGNORED。',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '流转失败或忽略原因。',
  `version_before` int DEFAULT NULL COMMENT '流转前版本号。',
  `version_after` int DEFAULT NULL COMMENT '流转后版本号。',
  `status_time` datetime(3) NOT NULL COMMENT '状态流转时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_status_history_id` (`status_history_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`,`status_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`,`status_time`),
  KEY `idx_status_object_time` (`status_object`,`to_status`,`status_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000003458 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录订单、动作、财务状态机每一次流转，支持审计和问题追踪。';

-- ----------------------------
-- Table structure for transaction_status_history_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_status_history_202604`;
CREATE TABLE `transaction_status_history_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `status_history_id` varchar(64) NOT NULL COMMENT '状态历史ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `status_object` varchar(32) NOT NULL COMMENT '状态对象：ORDER、OPERATION、FINANCE、NOTIFICATION。',
  `from_status` varchar(32) DEFAULT NULL COMMENT '流转前状态。',
  `to_status` varchar(32) NOT NULL COMMENT '流转后状态。',
  `trigger_type` varchar(64) NOT NULL COMMENT '触发类型：API、CHANNEL_RESPONSE、CHANNEL_CALLBACK、CHANNEL_QUERY_CONFIRM、JOB、ADMIN、RECONCILIATION。',
  `trigger_id` varchar(64) DEFAULT NULL COMMENT '触发对象ID，如 request_id、callback_id、job_log_id。',
  `transition_result` varchar(32) NOT NULL COMMENT '流转结果：SUCCESS、REJECTED、IGNORED。',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '流转失败或忽略原因。',
  `version_before` int DEFAULT NULL COMMENT '流转前版本号。',
  `version_after` int DEFAULT NULL COMMENT '流转后版本号。',
  `status_time` datetime(3) NOT NULL COMMENT '状态流转时间。',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_status_history_id` (`status_history_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`,`status_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`,`status_time`),
  KEY `idx_status_object_time` (`status_object`,`to_status`,`status_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录订单、动作、财务状态机每一次流转，支持审计和问题追踪。';

SET FOREIGN_KEY_CHECKS = 1;
