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

 Date: 19/11/2026 11:51:03
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for admin_operational_work_item
-- ----------------------------
DROP TABLE IF EXISTS `admin_operational_work_item`;
CREATE TABLE `admin_operational_work_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `work_item_no` varchar(32) NOT NULL COMMENT '统一待办编号',
  `source_type` varchar(48) NOT NULL COMMENT '来源类型',
  `source_key` varchar(128) NOT NULL COMMENT '来源稳定业务键',
  `work_status` varchar(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/PROCESSING/CLOSED',
  `priority` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'CRITICAL/HIGH/NORMAL/LOW',
  `owner_account_id` bigint DEFAULT NULL COMMENT '当前负责人管理账号ID',
  `owner_account_name` varchar(128) DEFAULT NULL COMMENT '当前负责人名称快照',
  `sla_due_at` datetime(3) NOT NULL COMMENT '当前待办SLA到期时间',
  `first_detected_at` datetime(3) NOT NULL COMMENT '首次发现来源活动状态时间',
  `last_synced_at` datetime(3) NOT NULL COMMENT '最近来源同步时间',
  `closed_at` datetime(3) DEFAULT NULL COMMENT '来源终态自动关闭时间',
  `close_reason` varchar(256) DEFAULT NULL COMMENT '自动关闭原因',
  `latest_remark` varchar(1000) DEFAULT NULL COMMENT '最近人工处理备注',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '人工命令和状态同步CAS版本',
  `source_projection_version` int NOT NULL DEFAULT '0' COMMENT '来源检索投影版本',
  `merchant_id` varchar(64) DEFAULT NULL COMMENT '来源商户号检索投影',
  `merchant_name` varchar(128) DEFAULT NULL COMMENT '来源商户名称检索投影',
  `business_no` varchar(128) DEFAULT NULL COMMENT '来源主业务单号检索投影',
  `related_business_no` varchar(128) DEFAULT NULL COMMENT '来源关联业务单号检索投影',
  `transaction_id` varchar(64) DEFAULT NULL COMMENT '来源系统订单号检索投影',
  `transaction_date_time` datetime(3) DEFAULT NULL COMMENT '来源交易时间检索投影',
  `source_occurred_at` datetime(3) DEFAULT NULL COMMENT '来源事件时间检索投影',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `source_basis_json` json DEFAULT NULL COMMENT '支付方式待办事实的来源版本、转移依据与去重世代',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_operational_work_item_no` (`work_item_no`),
  UNIQUE KEY `uk_admin_operational_work_item_source` (`source_type`,`source_key`),
  KEY `idx_admin_operational_work_item_queue` (`work_status`,`priority`,`sla_due_at`,`id`),
  KEY `idx_admin_operational_work_item_owner` (`owner_account_id`,`work_status`,`sla_due_at`,`id`),
  KEY `idx_admin_operational_work_item_detected` (`first_detected_at`,`id`),
  KEY `idx_admin_operational_work_item_projection_queue` (`source_projection_version`,`work_status`,`priority`,`sla_due_at`,`first_detected_at`,`id`),
  KEY `idx_admin_operational_work_item_projection_merchant` (`source_projection_version`,`merchant_id`,`work_status`,`first_detected_at`,`id`),
  CONSTRAINT `chk_admin_operational_work_item_state` CHECK (((`source_type` in (_utf8mb4'REFUND_APPROVAL',_utf8mb4'SETTLEMENT_REVIEW',_utf8mb4'EXCHANGE_RATE_ALERT',_utf8mb4'MERCHANT_NOTIFICATION_FAILURE',_utf8mb4'CHANNEL_MATCH_ABNORMAL',_utf8mb4'CLEARING_BACKLOG',_utf8mb4'SETTLEMENT_BACKLOG',_utf8mb4'HIGH_RISK_EVENT',_utf8mb4'PAYMENT_METHOD_APPROVAL',_utf8mb4'PAYMENT_METHOD_CONFIGURATION')) and (`work_status` in (_utf8mb4'OPEN',_utf8mb4'PROCESSING',_utf8mb4'CLOSED')) and (`priority` in (_utf8mb4'CRITICAL',_utf8mb4'HIGH',_utf8mb4'NORMAL',_utf8mb4'LOW')) and (`version` >= 0) and (((`owner_account_id` is null) and (`owner_account_name` is null)) or ((`owner_account_id` is not null) and (`owner_account_name` is not null))) and (((`work_status` = _utf8mb4'CLOSED') and (`closed_at` is not null) and (`close_reason` is not null)) or ((`work_status` <> _utf8mb4'CLOSED') and (`closed_at` is null) and (`close_reason` is null)))))
) ENGINE=InnoDB AUTO_INCREMENT=5951 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一运营待办元数据';

-- ----------------------------
-- Table structure for admin_operational_work_item_history
-- ----------------------------
DROP TABLE IF EXISTS `admin_operational_work_item_history`;
CREATE TABLE `admin_operational_work_item_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `work_item_id` bigint NOT NULL COMMENT '待办主键',
  `work_item_no` varchar(32) NOT NULL COMMENT '待办编号快照',
  `request_id` varchar(128) NOT NULL COMMENT '人工命令或同步动作幂等键',
  `action_type` varchar(24) NOT NULL COMMENT 'CREATE/REOPEN/CLAIM/ASSIGN/TRANSFER/REMARK/AUTO_CLOSE',
  `before_status` varchar(16) DEFAULT NULL COMMENT '操作前待办状态',
  `after_status` varchar(16) NOT NULL COMMENT '操作后待办状态',
  `before_owner_account_id` bigint DEFAULT NULL COMMENT '操作前负责人账号ID',
  `before_owner_account_name` varchar(128) DEFAULT NULL COMMENT '操作前负责人名称',
  `after_owner_account_id` bigint DEFAULT NULL COMMENT '操作后负责人账号ID',
  `after_owner_account_name` varchar(128) DEFAULT NULL COMMENT '操作后负责人名称',
  `operator_account_id` bigint DEFAULT NULL COMMENT '人工操作账号ID，系统动作为空',
  `operator_account_name` varchar(128) NOT NULL COMMENT '操作人或SYSTEM',
  `remark` varchar(1000) DEFAULT NULL COMMENT '处理说明',
  `operated_at` datetime(3) NOT NULL COMMENT '操作时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_operational_work_item_history_request` (`request_id`),
  KEY `idx_admin_operational_work_item_history_item` (`work_item_id`,`operated_at`,`id`),
  KEY `idx_admin_operational_work_item_history_no` (`work_item_no`,`operated_at`,`id`),
  KEY `idx_admin_operational_work_item_history_page` (`work_item_id`,`operated_at` DESC,`id` DESC),
  CONSTRAINT `chk_admin_operational_work_item_history_action` CHECK (((`action_type` in (_utf8mb4'CREATE',_utf8mb4'REOPEN',_utf8mb4'CLAIM',_utf8mb4'ASSIGN',_utf8mb4'TRANSFER',_utf8mb4'COMPLETE',_utf8mb4'REMARK',_utf8mb4'SOURCE_SYNC',_utf8mb4'AUTO_CLOSE')) and (`after_status` in (_utf8mb4'OPEN',_utf8mb4'PROCESSING',_utf8mb4'CLOSED')) and ((`before_status` is null) or (`before_status` in (_utf8mb4'OPEN',_utf8mb4'PROCESSING',_utf8mb4'CLOSED'))) and (((`before_owner_account_id` is null) and (`before_owner_account_name` is null)) or ((`before_owner_account_id` is not null) and (`before_owner_account_name` is not null))) and (((`after_owner_account_id` is null) and (`after_owner_account_name` is null)) or ((`after_owner_account_id` is not null) and (`after_owner_account_name` is not null)))))
) ENGINE=InnoDB AUTO_INCREMENT=5969 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一运营待办处理轨迹';

-- ----------------------------
-- Table structure for admin_payment_link_delivery_request
-- ----------------------------
DROP TABLE IF EXISTS `admin_payment_link_delivery_request`;
CREATE TABLE `admin_payment_link_delivery_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` varchar(80) NOT NULL,
  `merchant_id` varchar(64) NOT NULL,
  `link_id` varchar(32) NOT NULL,
  `fingerprint` char(64) NOT NULL,
  `recipient_hash` char(64) NOT NULL,
  `recipient_masked` varchar(260) NOT NULL,
  `locale` varchar(12) NOT NULL,
  `biz_no` char(64) NOT NULL,
  `email_record_id` bigint DEFAULT NULL,
  `created_by` varchar(128) NOT NULL,
  `create_time` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_link_send_request` (`merchant_id`,`request_id`),
  UNIQUE KEY `uk_admin_link_send_business` (`biz_no`),
  KEY `idx_admin_link_send_page` (`merchant_id`,`link_id`,`create_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付邀请幂等请求，不保存客户邮箱或链接令牌原文';

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
) ENGINE=InnoDB AUTO_INCREMENT=251 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ISO 3166 国家地区基础字典';

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
  `icon_key` varchar(64) DEFAULT NULL COMMENT 'Controlled currency presentation icon key',
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
-- Table structure for base_merchant_id_sequence
-- ----------------------------
DROP TABLE IF EXISTS `base_merchant_id_sequence`;
CREATE TABLE `base_merchant_id_sequence` (
  `business_year` char(2) NOT NULL COMMENT '商户号业务年份，格式yy',
  `current_sequence` smallint unsigned NOT NULL DEFAULT '0' COMMENT '当前年份已分配最大流水，范围0至9999',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '序列CAS版本',
  `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`business_year`),
  CONSTRAINT `chk_base_merchant_id_sequence` CHECK ((regexp_like(`business_year`,_utf8mb4'^[0-9]{2}$') and (`current_sequence` between 0 and 9999)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户号年度序列表';

-- ----------------------------
-- Table structure for base_merchant_info
-- ----------------------------
DROP TABLE IF EXISTS `base_merchant_info`;
CREATE TABLE `base_merchant_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(32) NOT NULL COMMENT '支付框架颁发的商户号',
  `application_no` varchar(40) DEFAULT NULL COMMENT '开户申请编号',
  `onboarding_source` varchar(24) NOT NULL DEFAULT 'ADMIN' COMMENT '开户来源：ADMIN、SELF_REGISTER、IMPORT',
  `onboarding_status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '开户状态',
  `review_status` varchar(24) NOT NULL DEFAULT 'NOT_SUBMITTED' COMMENT '审核状态',
  `activation_status` varchar(24) NOT NULL DEFAULT 'NOT_READY' COMMENT '激活状态',
  `merchant_name` varchar(128) NOT NULL COMMENT '商户主体名称',
  `billing_descriptor` varchar(64) DEFAULT NULL COMMENT '账单描述，交易账单或渠道侧展示的商户识别名称',
  `merchant_short_name` varchar(64) DEFAULT NULL COMMENT '商户简称',
  `merchant_type` varchar(32) DEFAULT NULL COMMENT '商户主体类型',
  `merchant_status` tinyint NOT NULL DEFAULT '1' COMMENT '商户状态：1正常，2冻结，3关闭',
  `default_locale` varchar(20) NOT NULL DEFAULT 'zh-CN' COMMENT '商户系统和邮件默认语言',
  `merchant_category_code` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '商户类别码MCC',
  `mcc_code` char(4) DEFAULT NULL COMMENT '商户MCC编码，关联base_mcc_code.mcc_code',
  `country_code` char(3) NOT NULL COMMENT '商户所在国家三字码',
  `operating_country` char(3) DEFAULT NULL COMMENT '实际经营国家三字码',
  `business_type` varchar(64) DEFAULT NULL COMMENT '业务类型',
  `industry_category` varchar(64) DEFAULT NULL COMMENT '行业分类',
  `merchant_description` varchar(1000) DEFAULT NULL COMMENT '商户主营业务描述',
  `registration_number` varchar(128) DEFAULT NULL COMMENT '公司注册号',
  `legal_entity_type` varchar(64) DEFAULT NULL COMMENT '企业注册类型',
  `incorporation_date` date DEFAULT NULL COMMENT '成立日期',
  `incorporation_country` char(3) DEFAULT NULL COMMENT '注册证书签发国家三字码',
  `registered_state` varchar(64) DEFAULT NULL COMMENT '注册地址州省',
  `registered_city` varchar(64) DEFAULT NULL COMMENT '注册地址城市',
  `registered_postcode` varchar(32) DEFAULT NULL COMMENT '注册地址邮编',
  `registered_address` varchar(512) DEFAULT NULL COMMENT '注册地址',
  `operating_same_as_registered` tinyint DEFAULT NULL COMMENT '经营地址是否同注册地址',
  `tax_id` varchar(128) DEFAULT NULL COMMENT '税号、EIN或TIN',
  `company_size` varchar(32) DEFAULT NULL COMMENT '公司规模区间',
  `employee_count` int DEFAULT NULL COMMENT '员工人数',
  `region_code` varchar(16) DEFAULT NULL COMMENT '商户所在州、省或区域代码',
  `city` varchar(64) DEFAULT NULL COMMENT '商户所在城市',
  `address_line` varchar(256) DEFAULT NULL COMMENT '商户开户地址或经营地址',
  `postal_code` varchar(32) DEFAULT NULL COMMENT '商户经营地址邮编',
  `contact_name` varchar(64) DEFAULT NULL COMMENT '商户联系人姓名',
  `contact_title` varchar(64) DEFAULT NULL COMMENT '主要联系人职位',
  `phone_country_code` varchar(8) DEFAULT NULL COMMENT '联系人电话国家区号',
  `contact_email` varchar(128) DEFAULT NULL COMMENT '商户联系人邮箱',
  `contact_phone` varchar(32) DEFAULT NULL COMMENT '商户联系人电话',
  `alternate_email` varchar(128) DEFAULT NULL COMMENT '备用联系邮箱',
  `finance_contact_name` varchar(64) DEFAULT NULL COMMENT '财务联系人姓名',
  `finance_contact_email` varchar(128) DEFAULT NULL COMMENT '财务联系人邮箱',
  `technical_contact_name` varchar(64) DEFAULT NULL COMMENT '技术联系人姓名',
  `technical_contact_email` varchar(128) DEFAULT NULL COMMENT '技术联系人邮箱',
  `business_model` varchar(32) DEFAULT NULL COMMENT '业务模式',
  `sales_channels` varchar(256) DEFAULT NULL COMMENT '销售渠道，多值使用逗号分隔',
  `products_services` varchar(1000) DEFAULT NULL COMMENT '主营产品或服务',
  `target_markets` varchar(512) DEFAULT NULL COMMENT '目标市场国家，多值使用逗号分隔',
  `customer_type` varchar(32) DEFAULT NULL COMMENT '客户类型',
  `transaction_currencies` varchar(256) DEFAULT NULL COMMENT '预计交易币种，多值使用逗号分隔',
  `expected_monthly_volume` decimal(20,2) DEFAULT NULL COMMENT '预计月交易金额',
  `expected_volume_currency` char(3) DEFAULT NULL COMMENT '预计交易金额币种',
  `average_ticket` decimal(20,2) DEFAULT NULL COMMENT '平均单笔金额',
  `max_ticket` decimal(20,2) DEFAULT NULL COMMENT '最大单笔金额预估',
  `expected_monthly_count` int DEFAULT NULL COMMENT '预计月交易笔数',
  `expected_refund_rate` decimal(8,4) DEFAULT NULL COMMENT '预计退款率百分比',
  `expected_chargeback_rate` decimal(8,4) DEFAULT NULL COMMENT '预计拒付率百分比',
  `recurring_payment_flag` tinyint DEFAULT NULL COMMENT '是否订阅或循环扣款',
  `presale_flag` tinyint DEFAULT NULL COMMENT '是否预售',
  `fulfillment_days` int DEFAULT NULL COMMENT '发货或服务交付天数',
  `digital_goods_flag` tinyint DEFAULT NULL COMMENT '是否涉及数字商品',
  `restricted_business_flag` tinyint DEFAULT NULL COMMENT '是否涉及受限行业',
  `expected_go_live_date` date DEFAULT NULL COMMENT '预计上线日期',
  `website_url` varchar(512) DEFAULT NULL COMMENT '官方网站',
  `app_store_url` varchar(512) DEFAULT NULL COMMENT 'App Store地址',
  `google_play_url` varchar(512) DEFAULT NULL COMMENT 'Google Play地址',
  `other_sales_url` varchar(512) DEFAULT NULL COMMENT '其他销售页面',
  `website_languages` varchar(256) DEFAULT NULL COMMENT '网站语言，多值使用逗号分隔',
  `website_live_flag` tinyint DEFAULT NULL COMMENT '网站是否已上线',
  `privacy_policy_url` varchar(512) DEFAULT NULL COMMENT '隐私政策地址',
  `refund_policy_url` varchar(512) DEFAULT NULL COMMENT '退款政策地址',
  `terms_url` varchar(512) DEFAULT NULL COMMENT '服务条款地址',
  `shipping_policy_url` varchar(512) DEFAULT NULL COMMENT '配送政策地址',
  `settlement_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '默认结算币种',
  `timezone` varchar(64) NOT NULL COMMENT '商户业务时区',
  `risk_level` tinyint NOT NULL DEFAULT '2' COMMENT '商户风险等级：1低，2中，3高',
  `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0正常，1删除',
  `payment_authorization_revision` bigint NOT NULL DEFAULT '0' COMMENT '当前商户支付方式授权及准入治理修订号',
  `payment_admission_mode` varchar(24) DEFAULT NULL COMMENT '支付方式准入模式，由显式历史核验或切换命令维护',
  `payment_baseline_batch_ref` varchar(64) DEFAULT NULL COMMENT '既有支付方式兼容范围的冻结批次引用',
  `payment_admission_cutover_time` datetime(3) DEFAULT NULL COMMENT '商户最近一次切换支付方式准入模式的时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_base_merchant_info_mid` (`merchant_id`),
  UNIQUE KEY `uk_base_merchant_application_no` (`application_no`),
  KEY `idx_base_merchant_status` (`merchant_status`,`deleted`),
  KEY `idx_base_merchant_mcc_code` (`mcc_code`),
  KEY `idx_base_merchant_onboarding` (`onboarding_status`,`review_status`,`activation_status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2111294273912877058 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础商户信息表';

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
) ENGINE=InnoDB AUTO_INCREMENT=2111294273933848578 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础商户JWT签名密钥表';

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
) ENGINE=InnoDB AUTO_INCREMENT=2111294273959014403 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础商户响应加密公钥表';

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
) ENGINE=InnoDB AUTO_INCREMENT=2111294273946431490 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础平台请求体RSA密钥表';

-- ----------------------------
-- Table structure for biz_document
-- ----------------------------
DROP TABLE IF EXISTS `biz_document`;
CREATE TABLE `biz_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `biz_type` varchar(32) NOT NULL COMMENT '业务类型',
  `biz_id` varchar(64) NOT NULL COMMENT '业务标识，商户资料使用平台商户号',
  `request_no` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '资料变更申请编号；正式资料为空',
  `document_type` varchar(64) NOT NULL COMMENT '资料类型',
  `original_filename` varchar(255) NOT NULL COMMENT '原始文件名',
  `content_type` varchar(128) NOT NULL COMMENT 'MIME类型',
  `file_size` bigint NOT NULL COMMENT '文件字节数',
  `sha256` char(64) NOT NULL COMMENT '文件SHA-256摘要',
  `storage_provider` varchar(24) NOT NULL COMMENT '存储供应商',
  `bucket_name` varchar(128) NOT NULL COMMENT '对象存储Bucket',
  `object_key` varchar(512) NOT NULL COMMENT '对象键',
  `document_status` varchar(24) NOT NULL DEFAULT 'UPLOADED' COMMENT '资料状态',
  `uploaded_by` varchar(64) DEFAULT NULL COMMENT '上传操作人',
  `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '删除标识',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_document_object` (`storage_provider`,`bucket_name`,`object_key`),
  KEY `idx_biz_document_owner` (`biz_type`,`biz_id`,`document_type`,`deleted`,`gmt_create`),
  KEY `idx_biz_document_request` (`biz_type`,`biz_id`,`request_no`,`deleted`,`gmt_create`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通用业务资料对象元数据';

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
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道预警规则配置表';

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
) ENGINE=InnoDB AUTO_INCREMENT=150 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道支付能力卡品牌表';

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
) ENGINE=InnoDB AUTO_INCREMENT=197 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道支付能力币种表';

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
  `logo_object_key` varchar(500) DEFAULT NULL COMMENT '通过安全扫描的私有渠道Logo对象引用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_info_code_deleted` (`channel_code`,`deleted`),
  KEY `idx_channel_info_status` (`channel_status`,`deleted`),
  KEY `idx_channel_info_capability` (`support_acquiring`,`support_payout`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道信息表';

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
) ENGINE=InnoDB AUTO_INCREMENT=102 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道MID参数模板表';

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
  `default_transaction_currency` char(3) NOT NULL COMMENT 'Default transaction currency for future initial transactions',
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
  KEY `idx_channel_mid_business` (`business_type`,`mid_status`,`deleted`),
  KEY `idx_cmc_effective_time` (`effective_time`,`id`),
  KEY `idx_cmc_expire_time` (`expire_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道MID配置表';

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
  `default_transaction_currency` char(3) NOT NULL COMMENT 'Default transaction currency; must belong to capability currencies',
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
  KEY `idx_channel_capability_status` (`capability_status`,`deleted`),
  CONSTRAINT `chk_channel_capability_default_transaction_currency` CHECK (((char_length(`default_transaction_currency`) = 3) and (cast(`default_transaction_currency` as char charset binary) = cast(upper(`default_transaction_currency`) as char charset binary))))
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道支付能力表';

-- ----------------------------
-- Table structure for clearing_label_principal_repair
-- ----------------------------
DROP TABLE IF EXISTS `clearing_label_principal_repair`;
CREATE TABLE `clearing_label_principal_repair` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `repair_no` varchar(64) NOT NULL COMMENT '稳定修复单号',
  `request_key` varchar(128) NOT NULL COMMENT '调用方请求幂等键',
  `transaction_id` varchar(64) NOT NULL COMMENT '待修复动作交易号',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '动作季度精确路由时间',
  `finance_state_id` varchar(64) NOT NULL COMMENT '动作财务状态业务号',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `expected_finance_state_version` int NOT NULL COMMENT '提交时冻结的财务状态CAS版本',
  `expected_clearing_revision` int NOT NULL COMMENT '提交时冻结的原清分修订',
  `candidate_id` bigint NOT NULL COMMENT '提交时冻结的未认领结算候选主键',
  `expected_candidate_version` bigint NOT NULL COMMENT '提交时冻结的候选CAS版本',
  `principal_detail_no` varchar(64) NOT NULL COMMENT '原ACTIVE本金明细业务号',
  `principal_amount_before` decimal(24,8) NOT NULL COMMENT '修复前本金金额',
  `principal_currency_before` char(3) NOT NULL COMMENT '修复前本金币种',
  `principal_currency_exponent_before` tinyint NOT NULL COMMENT '修复前本金币种ISO小数位',
  `label_amount` decimal(24,8) NOT NULL COMMENT '冻结的商户标签本金金额',
  `label_currency` char(3) NOT NULL COMMENT '冻结的商户标签币种',
  `label_currency_exponent` tinyint NOT NULL COMMENT '冻结的标签币种ISO小数位',
  `source_snapshot_hash` char(64) NOT NULL COMMENT '原修订全部ACTIVE明细规范快照SHA-256',
  `reserve_fact_count` tinyint NOT NULL COMMENT '原修订ACTIVE保证金事实数量，只允许0或1',
  `reserve_snapshot_hash` char(64) NOT NULL COMMENT '原修订ACTIVE保证金事实规范快照SHA-256',
  `reserve_state_count` tinyint NOT NULL COMMENT '冻结的保证金状态数量，必须与保证金事实数量一致',
  `reserve_state_snapshot_hash` char(64) NOT NULL COMMENT '保证金状态完整规范快照SHA-256',
  `dependency_count` tinyint NOT NULL COMMENT '旧候选依赖数量，只允许0或1',
  `dependency_snapshot_hash` char(64) NOT NULL COMMENT '依赖关系及依赖候选状态规范快照SHA-256',
  `reason` varchar(400) NOT NULL COMMENT '修复申请原因，不保存敏感支付数据',
  `submit_operator` varchar(128) NOT NULL COMMENT 'service-admin注入的可信Maker身份',
  `review_operator` varchar(128) DEFAULT NULL COMMENT 'service-admin注入的可信Checker身份',
  `review_comment` varchar(400) DEFAULT NULL COMMENT '复核意见',
  `repair_status` varchar(24) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT 'PENDING_REVIEW、EXECUTED、REJECTED',
  `new_clearing_revision` int DEFAULT NULL COMMENT '批准后生成的新清分修订',
  `new_candidate_no` varchar(64) DEFAULT NULL COMMENT '批准后生成的新结算候选业务号',
  `new_candidate_id` bigint DEFAULT NULL COMMENT '批准后生成的新结算候选主键，仅用于依赖迁移审计',
  `review_time` datetime(3) DEFAULT NULL COMMENT '复核时间',
  `executed_time` datetime(3) DEFAULT NULL COMMENT '新修订、候选与Outbox同事务完成时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '审批状态CAS版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_label_principal_repair_no` (`repair_no`),
  UNIQUE KEY `uk_label_principal_repair_request` (`request_key`),
  KEY `idx_label_principal_repair_transaction` (`transaction_id`,`transaction_date_time`,`expected_clearing_revision`,`id`),
  KEY `idx_label_principal_repair_finance` (`finance_state_id`,`expected_clearing_revision`,`repair_status`,`id`),
  KEY `idx_label_principal_repair_status` (`repair_status`,`create_time`,`id`),
  CONSTRAINT `chk_label_principal_repair_state` CHECK (((`repair_status` in (_utf8mb4'PENDING_REVIEW',_utf8mb4'EXECUTED',_utf8mb4'REJECTED')) and (((`repair_status` = _utf8mb4'PENDING_REVIEW') and (`review_operator` is null) and (`review_comment` is null) and (`review_time` is null) and (`new_clearing_revision` is null) and (`new_candidate_no` is null) and (`new_candidate_id` is null) and (`executed_time` is null)) or ((`repair_status` = _utf8mb4'REJECTED') and (`review_operator` is not null) and (`review_comment` is not null) and (`review_time` is not null) and (`review_operator` <> `submit_operator`) and (`new_clearing_revision` is null) and (`new_candidate_no` is null) and (`new_candidate_id` is null) and (`executed_time` is null)) or ((`repair_status` = _utf8mb4'EXECUTED') and (`review_operator` is not null) and (`review_comment` is not null) and (`review_time` is not null) and (`review_operator` <> `submit_operator`) and (`new_clearing_revision` = (`expected_clearing_revision` + 1)) and (`new_candidate_no` is not null) and (`new_candidate_id` is not null) and (`executed_time` is not null))))),
  CONSTRAINT `chk_label_principal_repair_value` CHECK (((`expected_finance_state_version` >= 0) and (`expected_clearing_revision` >= 1) and (`candidate_id` > 0) and (`expected_candidate_version` >= 0) and (`principal_amount_before` >= 0) and (`label_amount` >= 0) and (`principal_currency_exponent_before` between 0 and 8) and (`label_currency_exponent` between 0 and 8) and (`reserve_fact_count` between 0 and 1) and (`reserve_state_count` between 0 and 1) and (`reserve_state_count` = `reserve_fact_count`) and (`dependency_count` between 0 and 1) and (`version` >= 0) and regexp_like(`source_snapshot_hash`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`reserve_snapshot_hash`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`reserve_state_snapshot_hash`,_utf8mb4'^[0-9a-f]{64}$') and regexp_like(`dependency_snapshot_hash`,_utf8mb4'^[0-9a-f]{64}$') and ((`principal_amount_before` <> `label_amount`) or (`principal_currency_before` <> `label_currency`) or (`principal_currency_exponent_before` <> `label_currency_exponent`))))
) ENGINE=InnoDB AUTO_INCREMENT=622 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='未结算历史标签本金新修订修复双人复核事实；不重算费用、保证金或汇率';

-- ----------------------------
-- Table structure for clearing_label_principal_repair_dependency_audit
-- ----------------------------
DROP TABLE IF EXISTS `clearing_label_principal_repair_dependency_audit`;
CREATE TABLE `clearing_label_principal_repair_dependency_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `repair_no` varchar(64) NOT NULL COMMENT '关联的历史标签本金修复单号',
  `dependency_id` bigint NOT NULL COMMENT '被迁移的既有候选依赖主键',
  `dependent_candidate_id` bigint NOT NULL COMMENT '受依赖约束的保证金释放候选主键',
  `old_depends_on_candidate_id` bigint NOT NULL COMMENT '迁移前被依赖的旧清分候选主键',
  `new_depends_on_candidate_id` bigint NOT NULL COMMENT '迁移后被依赖的新清分候选主键',
  `dependency_type` varchar(32) NOT NULL COMMENT '当前只允许RESERVE_HOLD_POSTED',
  `dependent_candidate_version` bigint NOT NULL COMMENT '迁移时冻结的保证金释放候选CAS版本',
  `migrated_time` datetime(3) NOT NULL COMMENT '依赖迁移完成时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '审计创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_label_principal_dependency_repair` (`repair_no`,`dependency_id`),
  KEY `idx_label_principal_dependency_old` (`old_depends_on_candidate_id`,`dependency_id`),
  KEY `idx_label_principal_dependency_new` (`new_depends_on_candidate_id`,`dependency_id`),
  CONSTRAINT `chk_label_principal_dependency_audit` CHECK (((`dependency_id` > 0) and (`dependent_candidate_id` > 0) and (`old_depends_on_candidate_id` > 0) and (`new_depends_on_candidate_id` > 0) and (`old_depends_on_candidate_id` <> `new_depends_on_candidate_id`) and (`dependent_candidate_id` <> `old_depends_on_candidate_id`) and (`dependent_candidate_id` <> `new_depends_on_candidate_id`) and (`dependency_type` = _utf8mb4'RESERVE_HOLD_POSTED') and (`dependent_candidate_version` >= 0)))
) ENGINE=InnoDB AUTO_INCREMENT=621 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='历史标签本金修复候选依赖迁移不可变审计';

-- ----------------------------
-- Table structure for clearing_label_principal_repair_reserve_state_audit
-- ----------------------------
DROP TABLE IF EXISTS `clearing_label_principal_repair_reserve_state_audit`;
CREATE TABLE `clearing_label_principal_repair_reserve_state_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `repair_no` varchar(64) NOT NULL COMMENT '关联的历史标签本金修复单号',
  `reserve_state_row_id` bigint NOT NULL COMMENT '被迁移的保证金状态物理主键',
  `reserve_state_id` varchar(64) NOT NULL COMMENT '保证金状态业务号',
  `old_hold_detail_no` varchar(64) NOT NULL COMMENT '迁移前引用的旧HOLD明细号',
  `new_hold_detail_no` varchar(64) NOT NULL COMMENT '迁移后引用的新HOLD明细号',
  `old_state_version` bigint NOT NULL COMMENT '迁移前冻结的保证金状态CAS版本',
  `new_state_version` bigint NOT NULL COMMENT '迁移后的保证金状态CAS版本',
  `state_snapshot_hash` char(64) NOT NULL COMMENT '迁移前完整保证金状态SHA-256快照',
  `migrated_time` datetime(3) NOT NULL COMMENT '状态引用迁移完成时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '审计创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_label_principal_reserve_state_repair` (`repair_no`,`reserve_state_row_id`),
  KEY `idx_label_principal_reserve_state_old` (`old_hold_detail_no`,`reserve_state_row_id`),
  KEY `idx_label_principal_reserve_state_new` (`new_hold_detail_no`,`reserve_state_row_id`),
  CONSTRAINT `chk_label_principal_reserve_state_audit` CHECK (((`reserve_state_row_id` > 0) and (`old_hold_detail_no` <> `new_hold_detail_no`) and (`old_state_version` >= 0) and (`new_state_version` = (`old_state_version` + 1)) and regexp_like(`state_snapshot_hash`,_utf8mb4'^[0-9a-f]{64}$')))
) ENGINE=InnoDB AUTO_INCREMENT=622 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='历史标签本金修复保证金状态HOLD引用迁移不可变审计';

-- ----------------------------
-- Table structure for clearing_reserve_adjustment
-- ----------------------------
DROP TABLE IF EXISTS `clearing_reserve_adjustment`;
CREATE TABLE `clearing_reserve_adjustment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `adjustment_no` varchar(64) NOT NULL COMMENT '稳定保证金调整业务号',
  `request_key` varchar(128) NOT NULL COMMENT '提交请求幂等键',
  `reserve_state_id` varchar(64) NOT NULL COMMENT '目标保证金状态业务号',
  `original_transaction_id` varchar(64) NOT NULL COMMENT '原支付动作交易号',
  `original_transaction_date_time` datetime(3) NOT NULL COMMENT '原支付季度精确路由时间',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `reserve_currency` char(3) NOT NULL COMMENT '保证金标签币种；调整全程禁止换汇',
  `reserve_currency_exponent` tinyint NOT NULL COMMENT '保证金币种ISO小数位',
  `direction` varchar(8) NOT NULL COMMENT 'DEBIT增加负债、CREDIT减少负债',
  `adjustment_amount` decimal(24,8) NOT NULL COMMENT '标签币种绝对调整金额',
  `requested_release_date` date DEFAULT NULL COMMENT 'DEBIT调整后的预计释放业务日期；CREDIT为空',
  `expected_reserve_state_version` bigint NOT NULL COMMENT '提交时冻结的保证金状态CAS版本',
  `reason` varchar(400) NOT NULL COMMENT '调整原因，不保存敏感原文',
  `submit_operator` varchar(128) NOT NULL COMMENT '可信Admin登录上下文提交人',
  `review_operator` varchar(128) DEFAULT NULL COMMENT '可信Admin登录上下文复核人，必须不同于提交人',
  `review_comment` varchar(400) DEFAULT NULL COMMENT '复核意见',
  `adjustment_status` varchar(24) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT 'PENDING_REVIEW、EXECUTED、REJECTED',
  `execution_transaction_id` varchar(64) DEFAULT NULL COMMENT '批准后形成的独立保证金调整动作号',
  `source_revision` int DEFAULT NULL COMMENT '批准后形成的保证金来源修订',
  `review_time` datetime(3) DEFAULT NULL COMMENT '复核时间',
  `executed_time` datetime(3) DEFAULT NULL COMMENT '调整事实、状态和候选同事务完成时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '审批状态CAS版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reserve_adjustment_no` (`adjustment_no`),
  UNIQUE KEY `uk_reserve_adjustment_request` (`request_key`),
  UNIQUE KEY `uk_reserve_adjustment_execution` (`execution_transaction_id`),
  KEY `idx_reserve_adjustment_state` (`reserve_state_id`,`original_transaction_date_time`,`adjustment_status`,`id`),
  KEY `idx_reserve_adjustment_review` (`adjustment_status`,`create_time`,`id`),
  CONSTRAINT `chk_reserve_adjustment_state` CHECK (((`adjustment_status` in (_utf8mb4'PENDING_REVIEW',_utf8mb4'EXECUTED',_utf8mb4'REJECTED')) and (((`adjustment_status` = _utf8mb4'PENDING_REVIEW') and (`review_operator` is null) and (`review_comment` is null) and (`review_time` is null) and (`execution_transaction_id` is null) and (`source_revision` is null) and (`executed_time` is null)) or ((`adjustment_status` = _utf8mb4'REJECTED') and (`review_operator` is not null) and (`review_comment` is not null) and (`review_time` is not null) and (`review_operator` <> `submit_operator`) and (`execution_transaction_id` is null) and (`source_revision` is null) and (`executed_time` is null)) or ((`adjustment_status` = _utf8mb4'EXECUTED') and (`review_operator` is not null) and (`review_comment` is not null) and (`review_time` is not null) and (`review_operator` <> `submit_operator`) and (`execution_transaction_id` is not null) and (`source_revision` >= 1) and (`executed_time` is not null))))),
  CONSTRAINT `chk_reserve_adjustment_value` CHECK (((`reserve_currency_exponent` between 0 and 8) and (`adjustment_amount` > 0) and (`expected_reserve_state_version` >= 0) and (`version` >= 0) and (((`direction` = _utf8mb4'DEBIT') and (`requested_release_date` is not null)) or ((`direction` = _utf8mb4'CREDIT') and (`requested_release_date` is null)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保证金标签币种差额调整双人复核事实；不保存汇率、不直接写余额';

-- ----------------------------
-- Table structure for clearing_tier_period_replay
-- ----------------------------
DROP TABLE IF EXISTS `clearing_tier_period_replay`;
CREATE TABLE `clearing_tier_period_replay` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `replay_no` varchar(64) NOT NULL COMMENT '稳定阶梯期间重放业务号',
  `request_key` varchar(128) NOT NULL COMMENT '提交请求幂等键',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `fee_plan_id` bigint NOT NULL COMMENT '商户费用方案ID',
  `fee_plan_version_id` bigint NOT NULL COMMENT '不可变费用版本ID',
  `trigger_fee_rule_id` bigint NOT NULL COMMENT '触发申请的阶梯规则ID',
  `period_key` char(6) NOT NULL COMMENT '重放月份yyyyMM',
  `period_start` datetime(3) NOT NULL COMMENT '平台业务时区月份起点，闭区间',
  `period_end` datetime(3) NOT NULL COMMENT '平台业务时区下月起点，开区间',
  `reason` varchar(400) NOT NULL COMMENT '重放原因',
  `submit_operator` varchar(128) NOT NULL COMMENT '可信Admin登录上下文提交人',
  `review_operator` varchar(128) DEFAULT NULL COMMENT '可信Admin登录上下文复核人，必须不同于提交人',
  `review_comment` varchar(400) DEFAULT NULL COMMENT '复核意见',
  `replay_status` varchar(24) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT 'PENDING_REVIEW、PREPARING、RUNNING、COMPLETED、MANUAL_REVIEW、REJECTED',
  `item_count` int NOT NULL DEFAULT '0' COMMENT '复核后冻结的稳定动作项数量',
  `completed_count` int NOT NULL DEFAULT '0' COMMENT '已按稳定顺序完成的动作项数量',
  `last_clearing_complete_time` datetime(3) DEFAULT NULL COMMENT '最后完成项的原清分完成时间游标',
  `last_transaction_id` varchar(64) DEFAULT NULL COMMENT '最后完成项动作号游标',
  `last_error_code` varchar(64) DEFAULT NULL COMMENT '有限错误码',
  `last_error_message` varchar(400) DEFAULT NULL COMMENT '脱敏错误摘要',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '重放控制状态CAS版本',
  `review_time` datetime(3) DEFAULT NULL COMMENT '复核时间',
  `completed_time` datetime(3) DEFAULT NULL COMMENT '全部动作项完成时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tier_replay_no` (`replay_no`),
  UNIQUE KEY `uk_tier_replay_request` (`request_key`),
  KEY `idx_tier_replay_scope` (`merchant_id`,`fee_plan_version_id`,`period_key`,`replay_status`,`id`),
  KEY `idx_tier_replay_runnable` (`replay_status`,`update_time`,`id`),
  CONSTRAINT `chk_tier_replay_state` CHECK (((`replay_status` in (_utf8mb4'PENDING_REVIEW',_utf8mb4'PREPARING',_utf8mb4'RUNNING',_utf8mb4'COMPLETED',_utf8mb4'MANUAL_REVIEW',_utf8mb4'REJECTED')) and (((`replay_status` = _utf8mb4'PENDING_REVIEW') and (`review_operator` is null) and (`review_comment` is null) and (`review_time` is null) and (`item_count` = 0) and (`completed_count` = 0) and (`completed_time` is null)) or ((`replay_status` = _utf8mb4'REJECTED') and (`review_operator` is not null) and (`review_comment` is not null) and (`review_time` is not null) and (`review_operator` <> `submit_operator`) and (`item_count` = 0) and (`completed_count` = 0) and (`completed_time` is null)) or ((`replay_status` in (_utf8mb4'PREPARING',_utf8mb4'RUNNING',_utf8mb4'MANUAL_REVIEW')) and (`review_operator` is not null) and (`review_comment` is not null) and (`review_time` is not null) and (`review_operator` <> `submit_operator`) and (`completed_time` is null)) or ((`replay_status` = _utf8mb4'COMPLETED') and (`review_operator` is not null) and (`review_comment` is not null) and (`review_time` is not null) and (`review_operator` <> `submit_operator`) and (`item_count` > 0) and (`completed_count` = `item_count`) and (`completed_time` is not null))))),
  CONSTRAINT `chk_tier_replay_value` CHECK (((`fee_plan_id` > 0) and (`fee_plan_version_id` > 0) and (`trigger_fee_rule_id` > 0) and regexp_like(`period_key`,_utf8mb4'^[0-9]{4}(0[1-9]|1[0-2])$') and (`period_end` > `period_start`) and (`item_count` >= 0) and (`completed_count` >= 0) and (`completed_count` <= `item_count`) and (`version` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变商户费用版本的月度阶梯重放控制；数据库累计为权威，Redis仅镜像';

-- ----------------------------
-- Table structure for clearing_tier_period_replay_item
-- ----------------------------
DROP TABLE IF EXISTS `clearing_tier_period_replay_item`;
CREATE TABLE `clearing_tier_period_replay_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `replay_no` varchar(64) NOT NULL COMMENT '所属阶梯期间重放业务号',
  `sequence_no` int NOT NULL COMMENT '按原清分完成时间和动作号生成的稳定序号',
  `finance_state_id` varchar(64) NOT NULL COMMENT '原清分汇总业务号',
  `transaction_id` varchar(64) NOT NULL COMMENT '原动作交易号',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '原动作季度精确路由时间',
  `expected_clearing_revision` int NOT NULL COMMENT '冻结时原清分修订',
  `expected_finance_state_version` int NOT NULL COMMENT '冻结时finance state CAS版本',
  `clearing_complete_time` datetime(3) NOT NULL COMMENT '原清分完成时间，用于稳定顺序',
  `item_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING、FAILED、COMPLETED',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '失败尝试次数，最大8次后转人工复核',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT 'FAILED项的下次重试时间',
  `last_error_code` varchar(64) DEFAULT NULL COMMENT '有限错误码',
  `last_error_message` varchar(400) DEFAULT NULL COMMENT '脱敏错误摘要',
  `processed_revision` int DEFAULT NULL COMMENT '成功替换后的新清分修订',
  `processed_time` datetime(3) DEFAULT NULL COMMENT '单项短事务完成时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '重放项状态CAS版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tier_replay_item_sequence` (`replay_no`,`sequence_no`),
  UNIQUE KEY `uk_tier_replay_item_finance` (`replay_no`,`finance_state_id`,`expected_clearing_revision`),
  KEY `idx_tier_replay_item_runnable` (`replay_no`,`item_status`,`next_retry_time`,`sequence_no`,`id`),
  KEY `idx_tier_replay_item_transaction` (`transaction_id`,`transaction_date_time`,`expected_clearing_revision`,`id`),
  CONSTRAINT `chk_tier_replay_item_state` CHECK (((`item_status` in (_utf8mb4'PENDING',_utf8mb4'FAILED',_utf8mb4'COMPLETED')) and (((`item_status` = _utf8mb4'PENDING') and (`attempt_count` = 0) and (`next_retry_time` is null) and (`last_error_code` is null) and (`last_error_message` is null) and (`processed_revision` is null) and (`processed_time` is null)) or ((`item_status` = _utf8mb4'FAILED') and (`attempt_count` >= 1) and (`next_retry_time` is not null) and (`last_error_code` is not null) and (`last_error_message` is not null) and (`processed_revision` is null) and (`processed_time` is null)) or ((`item_status` = _utf8mb4'COMPLETED') and (`next_retry_time` is null) and (`last_error_code` is null) and (`last_error_message` is null) and (`processed_revision` > `expected_clearing_revision`) and (`processed_time` is not null))))),
  CONSTRAINT `chk_tier_replay_item_value` CHECK (((`sequence_no` >= 1) and (`expected_clearing_revision` >= 1) and (`expected_finance_state_version` >= 0) and (`attempt_count` between 0 and 8) and (`version` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阶梯期间重放稳定动作项；每项独立短事务并按sequence_no严格推进';

-- ----------------------------
-- Table structure for data_export_download_audit
-- ----------------------------
DROP TABLE IF EXISTS `data_export_download_audit`;
CREATE TABLE `data_export_download_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `audit_no` varchar(64) NOT NULL COMMENT '下载审计号',
  `task_no` varchar(64) NOT NULL COMMENT '导出任务号',
  `operator_app_code` varchar(32) NOT NULL DEFAULT 'ADMIN' COMMENT '下载操作人所属应用ADMIN/MERCHANT',
  `operator_account_id` bigint NOT NULL COMMENT '下载操作人管理账号ID',
  `merchant_id` varchar(64) DEFAULT NULL COMMENT '商户下载所属商户号，管理下载为空',
  `operator_account_name` varchar(128) DEFAULT NULL COMMENT '下载操作人名称快照',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '浏览器连接来源IP',
  `result_status` varchar(16) NOT NULL COMMENT 'REQUESTED/SUCCESS/FAILED',
  `failure_reason` varchar(500) DEFAULT NULL COMMENT '脱敏失败摘要',
  `file_sha256` char(64) DEFAULT NULL COMMENT '下载文件SHA-256摘要',
  `requested_at` datetime(3) NOT NULL COMMENT '开始下载时间',
  `downloaded_at` datetime(3) DEFAULT NULL COMMENT '下载完成或失败时间',
  `update_time` datetime(3) NOT NULL COMMENT '状态更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_data_export_download_audit_no` (`audit_no`),
  KEY `idx_data_export_download_task_time` (`task_no`,`requested_at`,`id`),
  KEY `idx_data_export_download_operator_time` (`operator_account_id`,`requested_at`,`id`),
  KEY `idx_data_export_download_owner_time` (`operator_app_code`,`operator_account_id`,`merchant_id`,`requested_at`,`id`),
  CONSTRAINT `chk_data_export_download_status` CHECK ((`result_status` in (_utf8mb4'REQUESTED',_utf8mb4'SUCCESS',_utf8mb4'FAILED')))
) ENGINE=InnoDB AUTO_INCREMENT=74 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导出文件下载审计';

-- ----------------------------
-- Table structure for data_export_task
-- ----------------------------
DROP TABLE IF EXISTS `data_export_task`;
CREATE TABLE `data_export_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `task_no` varchar(64) NOT NULL COMMENT '导出任务号',
  `request_id` varchar(128) NOT NULL COMMENT '创建账号范围内的提交幂等键',
  `task_type` varchar(64) NOT NULL COMMENT '任务类型',
  `task_name` varchar(128) NOT NULL COMMENT '用户可见任务名称',
  `source_service` varchar(64) NOT NULL COMMENT '创建任务的来源服务',
  `creator_app_code` varchar(32) NOT NULL DEFAULT 'ADMIN' COMMENT '创建账号所属应用ADMIN/MERCHANT',
  `creator_account_id` bigint NOT NULL COMMENT '创建人管理账号ID',
  `merchant_id` varchar(64) DEFAULT NULL COMMENT '商户任务所属商户号，管理任务为空',
  `creator_account_name` varchar(128) NOT NULL COMMENT '创建人名称快照',
  `locale` varchar(16) NOT NULL COMMENT '创建时语言标签',
  `timezone` varchar(64) NOT NULL COMMENT '创建时时区',
  `query_snapshot` json NOT NULL COMMENT '来源查询条件冻结快照',
  `data_scope_snapshot` json NOT NULL COMMENT '服务端商户数据范围冻结快照',
  `status` varchar(16) NOT NULL COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED/EXPIRED',
  `progress_percent` int NOT NULL DEFAULT '0' COMMENT '任务进度0至100',
  `processed_rows` bigint NOT NULL DEFAULT '0' COMMENT '已写入数据行数',
  `total_rows` bigint NOT NULL DEFAULT '0' COMMENT '权威查询总行数',
  `file_name` varchar(255) DEFAULT NULL COMMENT '成功文件名',
  `content_type` varchar(128) DEFAULT NULL COMMENT '文件媒体类型',
  `object_key` varchar(512) DEFAULT NULL COMMENT '私有Bucket对象键，不对浏览器返回',
  `file_size_bytes` bigint DEFAULT NULL COMMENT '文件字节数',
  `file_sha256` char(64) DEFAULT NULL COMMENT '文件SHA-256摘要',
  `expires_at` datetime(3) DEFAULT NULL COMMENT '文件到期时间',
  `failure_code` varchar(128) DEFAULT NULL COMMENT '最近失败类型',
  `failure_reason` varchar(500) DEFAULT NULL COMMENT '脱敏失败摘要',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '当前自动重试次数',
  `max_retry_count` int NOT NULL COMMENT '任务创建时冻结的最大重试次数',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下次允许领取时间',
  `lease_owner` varchar(192) DEFAULT NULL COMMENT '当前执行租约持有者',
  `lease_until` datetime(3) DEFAULT NULL COMMENT '当前执行租约到期时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '状态迁移CAS版本',
  `started_at` datetime(3) DEFAULT NULL COMMENT '首次开始时间',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '成功或最终失败时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_data_export_task_no` (`task_no`),
  UNIQUE KEY `uk_data_export_task_request` (`creator_app_code`,`creator_account_id`,`request_id`),
  KEY `idx_data_export_task_dispatch` (`status`,`next_retry_time`,`lease_until`,`id`),
  KEY `idx_data_export_task_creator_time` (`creator_account_id`,`create_time`,`id`),
  KEY `idx_data_export_task_expiry` (`status`,`expires_at`,`id`),
  KEY `idx_data_export_task_owner_time` (`creator_app_code`,`creator_account_id`,`merchant_id`,`create_time`,`id`),
  CONSTRAINT `chk_data_export_task_status` CHECK (((`status` in (_utf8mb4'PENDING',_utf8mb4'PROCESSING',_utf8mb4'SUCCESS',_utf8mb4'FAILED',_utf8mb4'EXPIRED')) and (`progress_percent` between 0 and 100) and (`processed_rows` >= 0) and (`total_rows` >= 0) and (`retry_count` >= 0) and (`max_retry_count` > 0) and (`version` >= 0)))
) ENGINE=InnoDB AUTO_INCREMENT=83 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='异步导出任务';

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
  KEY `idx_exchange_business_rate_current` (`rate_type`,`base_currency`,`quote_currency`,`rate_status`,`effective_time`,`expire_time`,`deleted`),
  KEY `idx_exchange_business_rate_volatility` (`rate_type`,`base_currency`,`quote_currency`,`effective_time`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=46250 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务汇率表';

-- ----------------------------
-- Table structure for exchange_rate_alert_evaluation
-- ----------------------------
DROP TABLE IF EXISTS `exchange_rate_alert_evaluation`;
CREATE TABLE `exchange_rate_alert_evaluation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®ID',
  `request_key` varchar(128) NOT NULL COMMENT 'è°ƒåº¦è¯·æ±‚å¹‚ç­‰é”®',
  `window_end_time` datetime(3) NOT NULL COMMENT 'æœ¬è½®è¯„ä¼°ä¸šåŠ¡æˆªæ­¢æ—¶é—´',
  `evaluation_status` varchar(32) NOT NULL COMMENT 'PROCESSING/SUCCESS/FAILED',
  `evaluated_rule_count` int NOT NULL DEFAULT '0' COMMENT 'è¯„ä¼°è§„åˆ™æ•°',
  `evaluated_pair_count` int NOT NULL DEFAULT '0' COMMENT 'è¯„ä¼°å¸ç§å¯¹æ•°',
  `triggered_event_count` int NOT NULL DEFAULT '0' COMMENT 'æ–°è§¦å‘äº‹ä»¶æ•°',
  `updated_event_count` int NOT NULL DEFAULT '0' COMMENT 'æ›´æ–°æ´»åŠ¨äº‹ä»¶æ•°',
  `resolved_event_count` int NOT NULL DEFAULT '0' COMMENT 'è‡ªåŠ¨æ¢å¤äº‹ä»¶æ•°',
  `insufficient_data_count` int NOT NULL DEFAULT '0' COMMENT 'æ ·æœ¬ä¸è¶³ç»´åº¦æ•°',
  `error_message` varchar(512) DEFAULT NULL COMMENT 'å¤±è´¥æ‘˜è¦ï¼Œä¸ä¿å­˜æ•æ„Ÿä¿¡æ¯',
  `start_time` datetime(3) NOT NULL COMMENT 'å¼€å§‹æ—¶é—´',
  `end_time` datetime(3) DEFAULT NULL COMMENT 'ç»“æŸæ—¶é—´',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'åˆ›å»ºæ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_rate_alert_evaluation_request` (`request_key`),
  KEY `idx_exchange_rate_alert_evaluation_time` (`window_end_time`,`evaluation_status`)
) ENGINE=InnoDB AUTO_INCREMENT=469 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ±‡çŽ‡æ³¢åŠ¨é¢„è­¦è¯„ä¼°å¹‚ç­‰è®°å½•è¡¨';

-- ----------------------------
-- Table structure for exchange_rate_alert_event
-- ----------------------------
DROP TABLE IF EXISTS `exchange_rate_alert_event`;
CREATE TABLE `exchange_rate_alert_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®ID',
  `event_no` varchar(64) NOT NULL COMMENT 'é¢„è­¦äº‹ä»¶ç¼–å·',
  `active_event_key` varchar(255) DEFAULT NULL COMMENT 'æ´»åŠ¨äº‹ä»¶å”¯ä¸€é”®ï¼Œç»ˆæ€åŽç½®ç©º',
  `rule_id` bigint NOT NULL COMMENT 'è§„åˆ™ID',
  `rule_code` varchar(64) NOT NULL COMMENT 'è§¦å‘æ—¶è§„åˆ™ç¼–ç å¿«ç…§',
  `rule_name` varchar(128) NOT NULL COMMENT 'è§¦å‘æ—¶è§„åˆ™åç§°å¿«ç…§',
  `rate_type` varchar(32) NOT NULL COMMENT 'æ±‡çŽ‡ç±»åž‹',
  `base_currency` char(3) NOT NULL COMMENT 'åŽŸå§‹å¸ç§',
  `quote_currency` char(3) NOT NULL COMMENT 'ç›®æ ‡å¸ç§',
  `alert_level` varchar(32) NOT NULL COMMENT 'é¢„è­¦ç­‰çº§å¿«ç…§',
  `window_minutes` int NOT NULL COMMENT 'çª—å£åˆ†é’Ÿæ•°å¿«ç…§',
  `trigger_threshold` decimal(9,4) NOT NULL COMMENT 'è§¦å‘é˜ˆå€¼ç™¾åˆ†æ¯”å¿«ç…§',
  `recovery_threshold` decimal(9,4) NOT NULL COMMENT 'æ¢å¤é˜ˆå€¼ç™¾åˆ†æ¯”å¿«ç…§',
  `window_start_time` datetime(3) NOT NULL COMMENT 'æœ€è¿‘è¯„ä¼°çª—å£å¼€å§‹æ—¶é—´',
  `window_end_time` datetime(3) NOT NULL COMMENT 'æœ€è¿‘è¯„ä¼°çª—å£ç»“æŸæ—¶é—´',
  `sample_count` int NOT NULL COMMENT 'æœ€è¿‘è¯„ä¼°æ ·æœ¬æ•°',
  `minimum_rate` decimal(24,12) NOT NULL COMMENT 'çª—å£æœ€ä½Žæ±‡çŽ‡',
  `minimum_rate_source` varchar(64) DEFAULT NULL COMMENT 'æœ€ä½Žæ±‡çŽ‡æ¥æºè¯æ®',
  `minimum_business_rate_id` bigint DEFAULT NULL COMMENT 'æœ€ä½Žæ±‡çŽ‡ä¸šåŠ¡æ±‡çŽ‡ID',
  `maximum_rate` decimal(24,12) NOT NULL COMMENT 'çª—å£æœ€é«˜æ±‡çŽ‡',
  `maximum_rate_source` varchar(64) DEFAULT NULL COMMENT 'æœ€é«˜æ±‡çŽ‡æ¥æºè¯æ®',
  `maximum_business_rate_id` bigint DEFAULT NULL COMMENT 'æœ€é«˜æ±‡çŽ‡ä¸šåŠ¡æ±‡çŽ‡ID',
  `latest_rate` decimal(24,12) NOT NULL COMMENT 'çª—å£æœ€æ–°æ±‡çŽ‡',
  `latest_rate_source` varchar(64) DEFAULT NULL COMMENT 'æœ€æ–°æ±‡çŽ‡æ¥æºè¯æ®',
  `latest_business_rate_id` bigint DEFAULT NULL COMMENT 'æœ€æ–°æ±‡çŽ‡ä¸šåŠ¡æ±‡çŽ‡ID',
  `volatility_rate` decimal(12,6) NOT NULL COMMENT 'æœ€è¿‘çª—å£æ³¢åŠ¨çŽ‡ç™¾åˆ†æ¯”',
  `maximum_volatility_rate` decimal(12,6) NOT NULL COMMENT 'äº‹ä»¶æŒç»­æœŸé—´æœ€å¤§æ³¢åŠ¨çŽ‡ç™¾åˆ†æ¯”',
  `recovery_streak` int NOT NULL DEFAULT '0' COMMENT 'è¿žç»­ä½ŽäºŽæ¢å¤é˜ˆå€¼æ¬¡æ•°',
  `event_status` varchar(32) NOT NULL COMMENT 'OPEN/ACKNOWLEDGED/RESOLVED/CLOSED',
  `first_trigger_time` datetime(3) NOT NULL COMMENT 'é¦–æ¬¡è§¦å‘æ—¶é—´',
  `last_detected_time` datetime(3) NOT NULL COMMENT 'æœ€è¿‘å‘çŽ°æ—¶é—´',
  `resolved_time` datetime(3) DEFAULT NULL COMMENT 'è‡ªåŠ¨æ¢å¤æ—¶é—´',
  `acknowledged_by` varchar(64) DEFAULT NULL COMMENT 'ç¡®è®¤äºº',
  `acknowledged_time` datetime(3) DEFAULT NULL COMMENT 'ç¡®è®¤æ—¶é—´',
  `acknowledge_remark` varchar(512) DEFAULT NULL COMMENT 'ç¡®è®¤å¤‡æ³¨',
  `closed_by` varchar(64) DEFAULT NULL COMMENT 'å…³é—­äºº',
  `closed_time` datetime(3) DEFAULT NULL COMMENT 'å…³é—­æ—¶é—´',
  `close_remark` varchar(512) DEFAULT NULL COMMENT 'å…³é—­å¤‡æ³¨',
  `version` int NOT NULL DEFAULT '0' COMMENT 'CASç‰ˆæœ¬',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'æ›´æ–°æ—¶é—´',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT 'åˆ é™¤æ ‡è¯†ï¼š0æœªåˆ é™¤ï¼Œå¤§äºŽ0ä¸ºåˆ é™¤è®°å½•ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_rate_alert_event_no` (`event_no`),
  UNIQUE KEY `uk_exchange_rate_alert_active_key` (`active_event_key`),
  KEY `idx_exchange_rate_alert_event_status_time` (`event_status`,`first_trigger_time`,`id`),
  KEY `idx_exchange_rate_alert_event_scope_time` (`rate_type`,`base_currency`,`quote_currency`,`first_trigger_time`,`id`),
  KEY `idx_exchange_rate_alert_event_rule_time` (`rule_id`,`first_trigger_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ±‡çŽ‡æ³¢åŠ¨é¢„è­¦äº‹ä»¶è¡¨';

-- ----------------------------
-- Table structure for exchange_rate_alert_rule
-- ----------------------------
DROP TABLE IF EXISTS `exchange_rate_alert_rule`;
CREATE TABLE `exchange_rate_alert_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®ID',
  `rule_code` varchar(64) NOT NULL COMMENT 'ç¨³å®šè§„åˆ™ç¼–ç ',
  `rule_name` varchar(128) NOT NULL COMMENT 'è§„åˆ™åç§°',
  `rate_type` varchar(32) NOT NULL COMMENT 'æ±‡çŽ‡ç±»åž‹ï¼šTRANSACTION_RATE/SETTLEMENT_RATE',
  `base_currency` varchar(3) NOT NULL DEFAULT 'ALL' COMMENT 'åŽŸå§‹å¸ç§ï¼ŒALLè¡¨ç¤ºæ‰€æœ‰å¸ç§',
  `quote_currency` varchar(3) NOT NULL DEFAULT 'ALL' COMMENT 'ç›®æ ‡å¸ç§ï¼ŒALLè¡¨ç¤ºæ‰€æœ‰å¸ç§',
  `window_minutes` int NOT NULL COMMENT 'æ»šåŠ¨çª—å£åˆ†é’Ÿæ•°',
  `trigger_threshold` decimal(9,4) NOT NULL COMMENT 'è§¦å‘é˜ˆå€¼ç™¾åˆ†æ¯”',
  `recovery_threshold` decimal(9,4) NOT NULL COMMENT 'æ¢å¤é˜ˆå€¼ç™¾åˆ†æ¯”',
  `recovery_consecutive_count` int NOT NULL DEFAULT '2' COMMENT 'è¿žç»­ä½ŽäºŽæ¢å¤é˜ˆå€¼çš„è¯„ä¼°æ¬¡æ•°',
  `minimum_sample_count` int NOT NULL DEFAULT '2' COMMENT 'æœ€å°æ ·æœ¬æ•°',
  `alert_level` varchar(32) NOT NULL DEFAULT 'WARNING' COMMENT 'é¢„è­¦ç­‰çº§ï¼šWARNING/ERROR/CRITICAL',
  `system_mandatory` tinyint NOT NULL DEFAULT '0' COMMENT 'æ˜¯å¦ç³»ç»Ÿå¼ºåˆ¶è§„åˆ™ï¼š0å¦ï¼Œ1æ˜¯',
  `rule_status` tinyint NOT NULL DEFAULT '1' COMMENT 'çŠ¶æ€ï¼š0åœç”¨ï¼Œ1å¯ç”¨',
  `remark` varchar(512) DEFAULT NULL COMMENT 'å¤‡æ³¨',
  `create_by` varchar(64) DEFAULT NULL COMMENT 'åˆ›å»ºäºº',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_by` varchar(64) DEFAULT NULL COMMENT 'æ›´æ–°äºº',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'æ›´æ–°æ—¶é—´',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT 'åˆ é™¤æ ‡è¯†ï¼š0æœªåˆ é™¤ï¼Œå¤§äºŽ0ä¸ºåˆ é™¤è®°å½•ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_rate_alert_rule_code_deleted` (`rule_code`,`deleted`),
  KEY `idx_exchange_rate_alert_rule_scope` (`rate_type`,`base_currency`,`quote_currency`,`rule_status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ±‡çŽ‡æ³¢åŠ¨é¢„è­¦è§„åˆ™è¡¨';

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
) ENGINE=InnoDB AUTO_INCREMENT=2186 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率拉取日志表';

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
  `usage_event_id` varchar(96) NOT NULL COMMENT '汇率使用事件全局幂等号',
  `producer_service` varchar(64) NOT NULL COMMENT '产生实际使用事实的服务名',
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
  `rate_source` varchar(64) DEFAULT NULL COMMENT '实际采用的汇率来源编码',
  `source_quote_direction` varchar(16) DEFAULT NULL COMMENT '原始报价方向：DIRECT/INVERSE',
  `rate_effective_time` datetime(3) DEFAULT NULL COMMENT '实际采用汇率的生效时间',
  `calculation_description` varchar(512) DEFAULT NULL COMMENT '计算说明',
  `applied_time` datetime(3) NOT NULL COMMENT '业务实际使用时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_snapshot_usage_event` (`usage_event_id`),
  KEY `idx_exchange_snapshot_business` (`business_type`,`business_no`,`deleted`),
  KEY `idx_exchange_snapshot_scope` (`rate_type`,`usage_scene`,`base_currency`,`quote_currency`,`deleted`),
  KEY `idx_exchange_snapshot_applied` (`applied_time`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=18058 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率使用快照表';

-- ----------------------------
-- Table structure for exchange_rate_usage_snapshot_backup_20261102
-- ----------------------------
DROP TABLE IF EXISTS `exchange_rate_usage_snapshot_backup_20261102`;
CREATE TABLE `exchange_rate_usage_snapshot_backup_20261102` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `usage_event_id` varchar(96) NOT NULL COMMENT '汇率使用事件全局幂等号',
  `producer_service` varchar(64) NOT NULL COMMENT '产生实际使用事实的服务名',
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
  `rate_source` varchar(64) DEFAULT NULL COMMENT '实际采用的汇率来源编码',
  `source_quote_direction` varchar(16) DEFAULT NULL COMMENT '原始报价方向：DIRECT/INVERSE',
  `rate_effective_time` datetime(3) DEFAULT NULL COMMENT '实际采用汇率的生效时间',
  `calculation_description` varchar(512) DEFAULT NULL COMMENT '计算说明',
  `applied_time` datetime(3) NOT NULL COMMENT '业务实际使用时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_snapshot_usage_event` (`usage_event_id`),
  KEY `idx_exchange_snapshot_business` (`business_type`,`business_no`,`deleted`),
  KEY `idx_exchange_snapshot_scope` (`rate_type`,`usage_scene`,`base_currency`,`quote_currency`,`deleted`),
  KEY `idx_exchange_snapshot_applied` (`applied_time`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=314 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率使用快照表';

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
) ENGINE=InnoDB AUTO_INCREMENT=23240 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='原始汇率记录表';

-- ----------------------------
-- Table structure for fee_plan
-- ----------------------------
DROP TABLE IF EXISTS `fee_plan`;
CREATE TABLE `fee_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_code` varchar(64) NOT NULL COMMENT '费用方案编码',
  `plan_name` varchar(128) NOT NULL COMMENT '费用方案名称',
  `plan_type` varchar(16) NOT NULL COMMENT '方案类型：TEMPLATE、MERCHANT',
  `merchant_id` varchar(64) DEFAULT NULL COMMENT '商户号；商户方案必填，模板为空',
  `source_template_id` bigint DEFAULT NULL COMMENT '来源模板ID',
  `source_template_version_no` int DEFAULT NULL COMMENT '复制时的来源模板版本号',
  `origin_type` varchar(32) NOT NULL DEFAULT 'INDEPENDENT' COMMENT '来源类型：TEMPLATE、TEMPLATE_CUSTOMIZED、INDEPENDENT',
  `current_version_id` bigint DEFAULT NULL COMMENT '当前生效版本ID',
  `current_version_no` int DEFAULT NULL COMMENT '当前生效版本号',
  `status` varchar(16) NOT NULL DEFAULT 'DISABLED' COMMENT '状态：ENABLED、DISABLED、ARCHIVED',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) NOT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) NOT NULL COMMENT '修改人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识；本业务只归档，不物理删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fee_plan_code_deleted` (`plan_code`,`deleted`),
  UNIQUE KEY `uk_fee_plan_merchant_deleted` (`plan_type`,`merchant_id`,`deleted`),
  KEY `idx_fee_plan_type_list` (`plan_type`,`deleted`,`update_time`,`id`),
  KEY `idx_fee_plan_type_status` (`plan_type`,`status`,`deleted`,`update_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='费用模板和商户费用方案主表';

-- ----------------------------
-- Table structure for fee_plan_version
-- ----------------------------
DROP TABLE IF EXISTS `fee_plan_version`;
CREATE TABLE `fee_plan_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_id` bigint NOT NULL COMMENT '费用方案ID',
  `version_no` int NOT NULL COMMENT '版本号，提交后即占用且不复用',
  `version_status` varchar(24) NOT NULL COMMENT '版本状态：DRAFT、PENDING_REVIEW、ACTIVE、REJECTED、SUPERSEDED',
  `change_type` varchar(32) NOT NULL COMMENT '变更类型：CREATED、UPDATED、TEMPLATE_ASSIGNED、CUSTOMIZED',
  `source_template_id` bigint DEFAULT NULL COMMENT '该版本复制或调整所基于的模板ID',
  `source_template_version_no` int DEFAULT NULL COMMENT '该版本复制或调整所基于的模板版本号',
  `origin_type` varchar(32) NOT NULL DEFAULT 'INDEPENDENT' COMMENT '该版本来源：TEMPLATE、TEMPLATE_CUSTOMIZED、INDEPENDENT',
  `reserve_rate` decimal(12,8) NOT NULL DEFAULT '0.00000000' COMMENT '滚动保证金比例，例如10表示10%',
  `reserve_delay_unit` char(1) NOT NULL DEFAULT 'D' COMMENT '滚动保证金留存周期单位：T工作日、D自然日',
  `reserve_delay_days` int NOT NULL DEFAULT '180' COMMENT '滚动保证金D+N留存自然日天数',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '商户待生效结算币种快照；模板版本为空',
  `initial_delay_unit` char(1) NOT NULL DEFAULT 'T' COMMENT '首次结算周期单位：T工作日、D自然日',
  `initial_delay_days` int NOT NULL DEFAULT '1' COMMENT '首次结算周期天数，最小1',
  `regular_delay_days` int NOT NULL DEFAULT '1' COMMENT '常规结算周期天数，最小1',
  `settlement_frequency` varchar(16) NOT NULL DEFAULT 'DAILY' COMMENT '结算频率：DAILY、WEEKLY、BIWEEKLY、MONTHLY',
  `frequency_day` int DEFAULT NULL COMMENT '周结为1至7，月结为1至28；日结为空',
  `change_reason` varchar(500) NOT NULL COMMENT '变更原因',
  `submit_by_id` bigint DEFAULT NULL COMMENT '提交人账号ID',
  `submit_by_name` varchar(128) NOT NULL COMMENT '提交人名称快照',
  `submit_time` datetime(3) NOT NULL COMMENT '提交时间',
  `review_by_id` bigint DEFAULT NULL COMMENT '审核人账号ID',
  `review_by_name` varchar(128) DEFAULT NULL COMMENT '审核人名称快照',
  `review_comment` varchar(500) DEFAULT NULL COMMENT '审核意见',
  `review_time` datetime(3) DEFAULT NULL COMMENT '审核时间',
  `effective_time` datetime(3) DEFAULT NULL COMMENT '审核通过后的实际生效时间',
  `superseded_time` datetime(3) DEFAULT NULL COMMENT '被新版本替代时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fee_plan_version_deleted` (`plan_id`,`version_no`,`deleted`),
  KEY `idx_fee_version_review` (`version_status`,`deleted`,`submit_time`,`id`),
  KEY `idx_fee_version_history` (`plan_id`,`deleted`,`version_no`,`id`),
  KEY `idx_fee_version_effective` (`plan_id`,`deleted`,`effective_time`,`id`),
  CONSTRAINT `chk_fee_version_frequency` CHECK ((((`settlement_frequency` = _utf8mb4'DAILY') and (`frequency_day` is null)) or ((`settlement_frequency` in (_utf8mb4'WEEKLY',_utf8mb4'BIWEEKLY')) and (`frequency_day` between 1 and 7)) or ((`settlement_frequency` = _utf8mb4'MONTHLY') and (`frequency_day` between 1 and 28)))),
  CONSTRAINT `chk_fee_version_reserve` CHECK (((`reserve_rate` between 0 and 100) and (`reserve_delay_unit` in (_utf8mb4'T',_utf8mb4'D')) and (`reserve_delay_days` >= 1))),
  CONSTRAINT `chk_fee_version_settlement_cycle` CHECK (((`initial_delay_unit` in (_utf8mb4'T',_utf8mb4'D')) and (`initial_delay_days` >= 1) and (`regular_delay_days` >= 1)))
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='费用方案版本表；草稿可编辑，提交审核后不可变';

-- ----------------------------
-- Table structure for fee_rule
-- ----------------------------
DROP TABLE IF EXISTS `fee_rule`;
CREATE TABLE `fee_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_version_id` bigint NOT NULL COMMENT '费用方案版本ID',
  `rule_group_code` varchar(64) DEFAULT NULL COMMENT '逻辑规则分组编码；同一次多选展开共享，历史数据可空',
  `fee_category` varchar(32) NOT NULL DEFAULT 'TRANSACTION_FEE' COMMENT '费用分类',
  `rule_name` varchar(128) NOT NULL COMMENT '规则名称',
  `transaction_type` varchar(64) NOT NULL COMMENT '交易动作，例如PAYMENT、CAPTURE、REFUND',
  `payment_type` varchar(64) NOT NULL COMMENT '支付类型，复用acquiring_payment_method字典',
  `payment_method` varchar(64) NOT NULL DEFAULT 'ALL' COMMENT '具体支付方式或品牌；ALL表示全部',
  `risk_service_type` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '风控类型：INTERNAL、EXTERNAL、THREE_DS；非风控为NONE',
  `charge_trigger` varchar(32) NOT NULL DEFAULT 'NOT_APPLICABLE' COMMENT '收费触发：NO_CHARGE、SUCCESS、SUCCESS_OR_FAILURE、ON_CALL',
  `fee_mode` varchar(16) NOT NULL DEFAULT 'STANDARD' COMMENT '计费模式：STANDARD、TIER',
  `percentage_rate` decimal(12,8) NOT NULL DEFAULT '0.00000000' COMMENT '百分比费率，例如2.3表示2.3%',
  `fixed_amount_usd` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '固定费用，币种固定USD',
  `minimum_amount_usd` decimal(24,8) DEFAULT NULL COMMENT '最低费用，币种固定USD',
  `maximum_amount_usd` decimal(24,8) DEFAULT NULL COMMENT '最高费用，币种固定USD',
  `tier_metric` varchar(16) DEFAULT NULL COMMENT '阶梯指标：COUNT、AMOUNT',
  `tier_period` varchar(16) DEFAULT NULL COMMENT '阶梯累计周期，本期固定MONTH',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fee_rule_dimension_deleted` (`plan_version_id`,`fee_category`,`risk_service_type`,`transaction_type`,`payment_type`,`payment_method`,`deleted`),
  KEY `idx_fee_rule_version` (`plan_version_id`,`deleted`,`sort_no`,`id`),
  CONSTRAINT `chk_fee_rule_amount` CHECK (((`percentage_rate` >= 0) and (`fixed_amount_usd` >= 0) and ((`minimum_amount_usd` is null) or (`minimum_amount_usd` >= 0)) and ((`maximum_amount_usd` is null) or (`maximum_amount_usd` >= 0)) and ((`minimum_amount_usd` is null) or (`maximum_amount_usd` is null) or (`maximum_amount_usd` >= `minimum_amount_usd`))))
) ENGINE=InnoDB AUTO_INCREMENT=986 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='费用方案版本规则表';

-- ----------------------------
-- Table structure for fee_rule_tier
-- ----------------------------
DROP TABLE IF EXISTS `fee_rule_tier`;
CREATE TABLE `fee_rule_tier` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `fee_rule_id` bigint NOT NULL COMMENT '费用规则ID',
  `lower_bound` decimal(24,8) NOT NULL COMMENT '档位下界，包含',
  `upper_bound` decimal(24,8) DEFAULT NULL COMMENT '档位上界，不包含；最后一档为空',
  `percentage_rate` decimal(12,8) NOT NULL DEFAULT '0.00000000' COMMENT '档位百分比费率',
  `fixed_amount_usd` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '档位固定费用USD',
  `minimum_amount_usd` decimal(24,8) DEFAULT NULL COMMENT '档位最低费用USD',
  `maximum_amount_usd` decimal(24,8) DEFAULT NULL COMMENT '档位最高费用USD',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fee_tier_lower_deleted` (`fee_rule_id`,`lower_bound`,`deleted`),
  KEY `idx_fee_tier_rule` (`fee_rule_id`,`deleted`,`sort_no`,`id`),
  CONSTRAINT `chk_fee_tier_amount` CHECK (((`percentage_rate` >= 0) and (`fixed_amount_usd` >= 0) and ((`minimum_amount_usd` is null) or (`minimum_amount_usd` >= 0)) and ((`maximum_amount_usd` is null) or (`maximum_amount_usd` >= 0)) and ((`minimum_amount_usd` is null) or (`maximum_amount_usd` is null) or (`maximum_amount_usd` >= `minimum_amount_usd`)))),
  CONSTRAINT `chk_fee_tier_range` CHECK (((`lower_bound` >= 0) and ((`upper_bound` is null) or (`upper_bound` > `lower_bound`))))
) ENGINE=InnoDB AUTO_INCREMENT=406 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='月累计阶梯费率档位表';

-- ----------------------------
-- Table structure for fee_simulation_record
-- ----------------------------
DROP TABLE IF EXISTS `fee_simulation_record`;
CREATE TABLE `fee_simulation_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `simulation_no` varchar(64) NOT NULL COMMENT '试算编号',
  `plan_version_id` bigint NOT NULL COMMENT '试算使用的方案版本ID',
  `merchant_id` varchar(64) NOT NULL COMMENT '商户号',
  `fee_category` varchar(32) NOT NULL DEFAULT 'TRANSACTION_FEE' COMMENT '试算费用分类',
  `transaction_type` varchar(64) NOT NULL COMMENT '交易动作',
  `payment_type` varchar(64) NOT NULL COMMENT '支付类型',
  `payment_method` varchar(64) NOT NULL COMMENT '支付方式',
  `risk_service_type` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '风控类型：INTERNAL、EXTERNAL、THREE_DS；非风控为NONE',
  `label_amount` decimal(24,8) NOT NULL COMMENT '标签金额',
  `label_currency` char(3) NOT NULL COMMENT '标签币种',
  `label_to_usd_rate` decimal(24,12) NOT NULL COMMENT '系统解析的标签币种到USD正向结算汇率',
  `label_amount_usd` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '标签金额按试算汇率归一后的USD快照',
  `settlement_rate_id` bigint DEFAULT NULL COMMENT '系统业务汇率记录ID；USD恒等汇率为空',
  `settlement_rate_source` varchar(64) NOT NULL COMMENT '汇率来源编码；USD恒等汇率为SYSTEM_IDENTITY',
  `rate_effective_time` datetime(3) NOT NULL COMMENT '选用汇率的生效时间',
  `rate_valuation_time` datetime(3) NOT NULL COMMENT '本次试算汇率估值时间',
  `monthly_count_before` bigint NOT NULL DEFAULT '0' COMMENT '本次交易前的月累计笔数',
  `monthly_amount_usd_before` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '本次交易前的月累计金额USD',
  `matched_rule_id` bigint NOT NULL COMMENT '匹配规则ID',
  `matched_tier_id` bigint DEFAULT NULL COMMENT '匹配阶梯ID',
  `percentage_fee_label` decimal(24,8) NOT NULL COMMENT '标签币种百分比费用',
  `raw_fee_usd` decimal(24,8) NOT NULL COMMENT '应用上下限前的USD费用',
  `final_fee_usd` decimal(24,8) NOT NULL COMMENT '应用上下限后的USD费用',
  `reserve_rate` decimal(12,8) NOT NULL DEFAULT '0.00000000' COMMENT '试算使用的滚动保证金比例快照',
  `reserve_amount_usd` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '预计滚动保证金USD',
  `estimated_net_settlement_usd` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '预计净结算金额USD',
  `formula_snapshot` varchar(1000) NOT NULL COMMENT '试算公式快照',
  `net_settlement_formula_snapshot` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '净结算计算公式快照',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人账号ID',
  `operator_name` varchar(128) NOT NULL COMMENT '操作人名称快照',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '试算时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fee_simulation_no` (`simulation_no`),
  KEY `idx_fee_simulation_rate` (`settlement_rate_id`,`rate_valuation_time`),
  KEY `idx_fee_simulation_create_time` (`create_time`,`id`),
  KEY `idx_fee_simulation_plan_time` (`plan_version_id`,`create_time`,`id`),
  KEY `idx_fee_simulation_merchant_time` (`merchant_id`,`create_time`,`id`),
  KEY `idx_fee_simulation_transaction_time` (`transaction_type`,`create_time`,`id`),
  CONSTRAINT `chk_fee_simulation_risk_type` CHECK ((`risk_service_type` in (_utf8mb4'NONE',_utf8mb4'INTERNAL',_utf8mb4'EXTERNAL',_utf8mb4'THREE_DS')))
) ENGINE=InnoDB AUTO_INCREMENT=110 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='费用试算记录表';

-- ----------------------------
-- Table structure for fee_simulation_record_detail
-- ----------------------------
DROP TABLE IF EXISTS `fee_simulation_record_detail`;
CREATE TABLE `fee_simulation_record_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `simulation_record_id` bigint NOT NULL COMMENT '费用试算记录ID',
  `line_no` int NOT NULL COMMENT '同一试算内的稳定明细顺序',
  `item_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '明细类型：FEE、RESERVE',
  `fee_category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '费用分类；保证金使用RESERVE',
  `risk_service_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NONE' COMMENT '风控类型；非风控使用NONE',
  `calculation_status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '计算状态：CALCULATED、NOT_APPLICABLE、NOT_CONFIGURED',
  `included_in_fee_total` tinyint NOT NULL DEFAULT '0' COMMENT '是否计入费用合计：0否、1是',
  `charge_trigger` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NOT_APPLICABLE' COMMENT '收费触发快照',
  `rule_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '命中规则名称快照',
  `fee_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '命中计费模式快照',
  `matched_rule_id` bigint DEFAULT NULL COMMENT '命中费用规则ID',
  `matched_tier_id` bigint DEFAULT NULL COMMENT '命中费用档位ID',
  `percentage_fee_label` decimal(24,8) DEFAULT NULL COMMENT '标签币种百分比费用',
  `percentage_fee_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '百分比费用标签币种',
  `raw_fee_usd` decimal(24,8) DEFAULT NULL COMMENT '应用上下限前USD费用',
  `final_fee_usd` decimal(24,8) DEFAULT NULL COMMENT '最终USD金额；未计算时为空',
  `applied_limit` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NONE' COMMENT '实际应用的上下限',
  `formula_snapshot` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '单项计算公式快照',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fee_simulation_detail_line` (`simulation_record_id`,`line_no`),
  KEY `idx_fee_simulation_detail_rule` (`matched_rule_id`,`matched_tier_id`),
  CONSTRAINT `chk_fee_simulation_detail_risk` CHECK ((`risk_service_type` in (_utf8mb4'NONE',_utf8mb4'INTERNAL',_utf8mb4'EXTERNAL',_utf8mb4'THREE_DS'))),
  CONSTRAINT `chk_fee_simulation_detail_status` CHECK ((`calculation_status` in (_utf8mb4'CALCULATED',_utf8mb4'NOT_APPLICABLE',_utf8mb4'NOT_CONFIGURED'))),
  CONSTRAINT `chk_fee_simulation_detail_total` CHECK ((`included_in_fee_total` in (0,1))),
  CONSTRAINT `chk_fee_simulation_detail_type` CHECK ((`item_type` in (_utf8mb4'FEE',_utf8mb4'RESERVE')))
) ENGINE=InnoDB AUTO_INCREMENT=401 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='费用试算逐项审计快照表';

-- ----------------------------
-- Table structure for fee_tier_accumulator
-- ----------------------------
DROP TABLE IF EXISTS `fee_tier_accumulator`;
CREATE TABLE `fee_tier_accumulator` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `fee_plan_version_id` bigint NOT NULL COMMENT '不可变费用版本ID',
  `fee_rule_id` bigint NOT NULL COMMENT '阶梯费用规则ID',
  `period_key` char(6) NOT NULL COMMENT '累计月份，格式yyyyMM，按平台业务时区确定',
  `accumulated_count` bigint NOT NULL DEFAULT '0' COMMENT '已成功清分动作累计笔数',
  `accumulated_amount_usd` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '按现有费用配置口径归一到USD的已成功清分计费基数累计金额',
  `last_transaction_id` varchar(64) DEFAULT NULL COMMENT '最近计入累计的动作级交易号',
  `last_clearing_revision` int DEFAULT NULL COMMENT '最近计入累计的动作清分修订号',
  `last_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '最近计入累计的动作业务时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '并发CAS版本号',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fee_tier_accumulator` (`merchant_id`,`fee_plan_version_id`,`fee_rule_id`,`period_key`),
  KEY `idx_fee_tier_period` (`period_key`,`merchant_id`,`id`),
  CONSTRAINT `chk_fee_tier_accumulator_value` CHECK (((`accumulated_count` >= 0) and (`accumulated_amount_usd` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='按商户生效费用版本维护COUNT或USD归一AMOUNT的月累计阶梯事实；Redis仅保存镜像';

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
  KEY `idx_binding_mid` (`mid_config_id`,`binding_status`,`deleted`),
  KEY `idx_mcmb_effective_time` (`effective_time`,`id`),
  KEY `idx_mcmb_expire_time` (`expire_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户渠道MID绑定表';

-- ----------------------------
-- Table structure for merchant_fund_account
-- ----------------------------
DROP TABLE IF EXISTS `merchant_fund_account`;
CREATE TABLE `merchant_fund_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `account_no` varchar(64) NOT NULL COMMENT '资金账户号',
  `merchant_id` varchar(64) NOT NULL COMMENT '商户号',
  `settlement_currency` char(3) NOT NULL COMMENT '结算币种；当前每个商户只允许一个',
  `available_balance` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '账户余额，允许为负数',
  `account_status` varchar(24) NOT NULL DEFAULT 'NORMAL' COMMENT '人工状态：NORMAL、FROZEN、CLOSED；负余额限制由可用余额实时派生',
  `account_version` bigint NOT NULL DEFAULT '0' COMMENT '账户并发版本号',
  `create_by` varchar(64) NOT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) NOT NULL COMMENT '修改人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fund_account_no_deleted` (`account_no`,`deleted`),
  UNIQUE KEY `uk_fund_account_merchant_currency_deleted` (`merchant_id`,`settlement_currency`,`deleted`),
  KEY `idx_fund_account_list` (`deleted`,`update_time`,`id`),
  KEY `idx_fund_account_status` (`account_status`,`deleted`,`update_time`,`id`),
  CONSTRAINT `chk_fund_account_status` CHECK ((`account_status` in (_utf8mb4'NORMAL',_utf8mb4'FROZEN',_utf8mb4'CLOSED')))
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户资金账户表';

-- ----------------------------
-- Table structure for merchant_fund_deduction
-- ----------------------------
DROP TABLE IF EXISTS `merchant_fund_deduction`;
CREATE TABLE `merchant_fund_deduction` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `deduction_no` varchar(64) NOT NULL COMMENT '账户扣减申请号',
  `account_id` bigint NOT NULL COMMENT '资金账户ID',
  `merchant_id` varchar(64) NOT NULL COMMENT '商户号',
  `currency` char(3) NOT NULL COMMENT '账户结算币种',
  `amount` decimal(24,8) NOT NULL COMMENT '扣减金额，必须大于0且不超过100000000',
  `deduction_category` varchar(32) NOT NULL COMMENT '扣减类型：ACCOUNT_CORRECTION、EXTRA_FEE、PENALTY、OTHER',
  `deduction_status` varchar(24) NOT NULL COMMENT '状态：PENDING_AUDIT、PENDING_RECHECK、POSTED、REJECTED',
  `reason` varchar(500) NOT NULL COMMENT '商户可见的完整扣减说明',
  `submit_by_id` bigint DEFAULT NULL COMMENT '提交人账号ID',
  `submit_by_name` varchar(128) NOT NULL COMMENT '提交人名称快照',
  `submit_login_account` varchar(128) DEFAULT NULL COMMENT '提交人登录账号快照，用于admin自审边界审计',
  `submit_time` datetime(3) NOT NULL COMMENT '提交时间',
  `audit_by_id` bigint DEFAULT NULL COMMENT '审核人账号ID',
  `audit_by_name` varchar(128) DEFAULT NULL COMMENT '审核人名称快照',
  `audit_comment` varchar(500) DEFAULT NULL COMMENT '审核意见',
  `audit_time` datetime(3) DEFAULT NULL COMMENT '审核时间',
  `recheck_by_id` bigint DEFAULT NULL COMMENT '复核人账号ID',
  `recheck_by_name` varchar(128) DEFAULT NULL COMMENT '复核人名称快照',
  `recheck_comment` varchar(500) DEFAULT NULL COMMENT '复核意见',
  `recheck_time` datetime(3) DEFAULT NULL COMMENT '复核时间',
  `reject_by_id` bigint DEFAULT NULL COMMENT '驳回人账号ID',
  `reject_by_name` varchar(128) DEFAULT NULL COMMENT '驳回人名称快照',
  `reject_comment` varchar(500) DEFAULT NULL COMMENT '驳回原因',
  `reject_time` datetime(3) DEFAULT NULL COMMENT '驳回时间',
  `request_id` varchar(64) NOT NULL COMMENT '客户端唯一请求号',
  `ledger_no` varchar(64) DEFAULT NULL COMMENT '最终扣减余额流水号',
  `posted_time` datetime(3) DEFAULT NULL COMMENT '最终入账时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fund_deduction_no` (`deduction_no`),
  UNIQUE KEY `uk_fund_deduction_request` (`request_id`),
  UNIQUE KEY `uk_fund_deduction_ledger` (`ledger_no`),
  KEY `idx_fund_deduction_list` (`deleted`,`create_time`,`id`),
  KEY `idx_fund_deduction_status_time` (`deduction_status`,`deleted`,`create_time`,`id`),
  KEY `idx_fund_deduction_category_time` (`deduction_category`,`deleted`,`create_time`,`id`),
  KEY `idx_fund_deduction_merchant_time` (`merchant_id`,`deleted`,`create_time`,`id`),
  KEY `idx_fund_deduction_account_time` (`account_id`,`deleted`,`create_time`,`id`),
  CONSTRAINT `chk_fund_deduction_amount` CHECK (((`amount` > 0) and (`amount` <= 100000000))),
  CONSTRAINT `chk_fund_deduction_category` CHECK ((`deduction_category` in (_utf8mb4'ACCOUNT_CORRECTION',_utf8mb4'EXTRA_FEE',_utf8mb4'PENALTY',_utf8mb4'OTHER')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户资金账户扣减申请和审批表';

-- ----------------------------
-- Table structure for merchant_fund_ledger
-- ----------------------------
DROP TABLE IF EXISTS `merchant_fund_ledger`;
CREATE TABLE `merchant_fund_ledger` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `ledger_no` varchar(64) NOT NULL COMMENT '余额流水号',
  `ledger_group_no` varchar(64) DEFAULT NULL COMMENT '同一业务产生的关联流水组号',
  `account_id` bigint NOT NULL COMMENT '资金账户ID',
  `merchant_id` varchar(64) NOT NULL COMMENT '商户号',
  `business_type` varchar(64) NOT NULL COMMENT '业务类型',
  `summary` varchar(500) NOT NULL COMMENT '变动摘要',
  `business_no` varchar(128) NOT NULL COMMENT '关联业务单号',
  `transaction_id` varchar(64) DEFAULT NULL COMMENT '关联交易号',
  `settlement_batch_no` varchar(64) DEFAULT NULL COMMENT '关联结算批次号',
  `fee_detail_no` varchar(64) DEFAULT NULL COMMENT '关联费用明细号',
  `currency` char(3) NOT NULL COMMENT '币种',
  `direction` varchar(8) NOT NULL COMMENT '借贷方向：CREDIT增加、DEBIT减少',
  `amount` decimal(24,8) NOT NULL COMMENT '发生金额，始终为正数',
  `balance_before` decimal(24,8) NOT NULL COMMENT '操作前金额',
  `balance_after` decimal(24,8) NOT NULL COMMENT '操作后金额',
  `account_sequence` bigint NOT NULL COMMENT '账户内严格递增流水序号',
  `fee_version_id` bigint DEFAULT NULL COMMENT '费用版本ID',
  `rate_snapshot_id` bigint DEFAULT NULL COMMENT '汇率快照ID',
  `operation_mode` varchar(16) NOT NULL COMMENT '操作模式：AUTO、MANUAL',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人账号ID',
  `operator_name` varchar(128) NOT NULL COMMENT '操作人名称快照',
  `reviewer_id` bigint DEFAULT NULL COMMENT '审核人账号ID',
  `reviewer_name` varchar(128) DEFAULT NULL COMMENT '审核人名称快照',
  `operation_reason` varchar(500) DEFAULT NULL COMMENT '操作原因',
  `review_comment` varchar(500) DEFAULT NULL COMMENT '审核意见',
  `business_time` datetime(3) NOT NULL COMMENT '业务发生时间',
  `submit_time` datetime(3) DEFAULT NULL COMMENT '人工提交时间',
  `review_time` datetime(3) DEFAULT NULL COMMENT '审核时间',
  `posted_time` datetime(3) NOT NULL COMMENT '入账时间',
  `request_id` varchar(64) DEFAULT NULL COMMENT '请求号',
  `idempotency_key` varchar(128) NOT NULL COMMENT '资金幂等键',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路追踪号',
  `reversal_of_ledger_id` bigint DEFAULT NULL COMMENT '被冲正流水ID',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fund_ledger_no` (`ledger_no`),
  UNIQUE KEY `uk_fund_ledger_idempotency` (`idempotency_key`),
  UNIQUE KEY `uk_fund_ledger_account_sequence` (`account_id`,`account_sequence`),
  KEY `idx_fund_ledger_business` (`business_type`,`business_no`),
  KEY `idx_fund_ledger_batch` (`settlement_batch_no`),
  KEY `idx_fund_ledger_reversal` (`reversal_of_ledger_id`),
  KEY `idx_fund_ledger_posted_time` (`posted_time`,`id`),
  KEY `idx_fund_ledger_account_time` (`account_id`,`merchant_id`,`posted_time`,`id`),
  KEY `idx_fund_ledger_merchant_time` (`merchant_id`,`posted_time`,`id`),
  KEY `idx_fund_ledger_business_time` (`business_type`,`posted_time`,`id`),
  CONSTRAINT `chk_fund_ledger_balance` CHECK (((`amount` > 0) and (`direction` in (_utf8mb4'CREDIT',_utf8mb4'DEBIT')) and (((`direction` = _utf8mb4'CREDIT') and (`balance_after` = (`balance_before` + `amount`))) or ((`direction` = _utf8mb4'DEBIT') and (`balance_after` = (`balance_before` - `amount`))))))
) ENGINE=InnoDB AUTO_INCREMENT=112 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户可用余额不可变流水表';

-- ----------------------------
-- Table structure for merchant_fund_recharge
-- ----------------------------
DROP TABLE IF EXISTS `merchant_fund_recharge`;
CREATE TABLE `merchant_fund_recharge` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `recharge_no` varchar(64) NOT NULL COMMENT '充值申请号',
  `account_id` bigint NOT NULL COMMENT '资金账户ID',
  `merchant_id` varchar(64) NOT NULL COMMENT '商户号',
  `currency` char(3) NOT NULL COMMENT '账户结算币种',
  `amount` decimal(24,8) NOT NULL COMMENT '充值金额，范围100至100000000',
  `recharge_status` varchar(24) NOT NULL COMMENT '状态：PENDING_AUDIT、PENDING_RECHECK、POSTED、REJECTED',
  `remark` varchar(500) NOT NULL COMMENT '充值原因或凭证摘要',
  `submit_by_id` bigint DEFAULT NULL COMMENT '提交人账号ID',
  `submit_by_name` varchar(128) NOT NULL COMMENT '提交人名称快照',
  `submit_login_account` varchar(128) DEFAULT NULL COMMENT '提交人登录账号快照，用于admin自审边界审计',
  `submit_time` datetime(3) NOT NULL COMMENT '提交时间',
  `audit_by_id` bigint DEFAULT NULL COMMENT '审核人账号ID',
  `audit_by_name` varchar(128) DEFAULT NULL COMMENT '审核人名称快照',
  `audit_comment` varchar(500) DEFAULT NULL COMMENT '审核意见',
  `audit_time` datetime(3) DEFAULT NULL COMMENT '审核时间',
  `recheck_by_id` bigint DEFAULT NULL COMMENT '复核人账号ID',
  `recheck_by_name` varchar(128) DEFAULT NULL COMMENT '复核人名称快照',
  `recheck_comment` varchar(500) DEFAULT NULL COMMENT '复核意见',
  `recheck_time` datetime(3) DEFAULT NULL COMMENT '复核时间',
  `reject_by_id` bigint DEFAULT NULL COMMENT '驳回人账号ID',
  `reject_by_name` varchar(128) DEFAULT NULL COMMENT '驳回人名称快照',
  `reject_comment` varchar(500) DEFAULT NULL COMMENT '驳回原因',
  `reject_time` datetime(3) DEFAULT NULL COMMENT '驳回时间',
  `request_id` varchar(64) NOT NULL COMMENT '客户端唯一请求号',
  `ledger_no` varchar(64) DEFAULT NULL COMMENT '最终入账余额流水号',
  `posted_time` datetime(3) DEFAULT NULL COMMENT '最终入账时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fund_recharge_no` (`recharge_no`),
  UNIQUE KEY `uk_fund_recharge_request` (`request_id`),
  UNIQUE KEY `uk_fund_recharge_ledger` (`ledger_no`),
  KEY `idx_fund_recharge_list` (`deleted`,`create_time`,`id`),
  KEY `idx_fund_recharge_status_time` (`recharge_status`,`deleted`,`create_time`,`id`),
  KEY `idx_fund_recharge_merchant_time` (`merchant_id`,`deleted`,`create_time`,`id`),
  KEY `idx_fund_recharge_account_time` (`account_id`,`deleted`,`create_time`,`id`),
  CONSTRAINT `chk_fund_recharge_amount` CHECK ((`amount` between 100 and 100000000))
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户资金账户充值申请和审批表';

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
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户 OpenAPI IP 白名单';

-- ----------------------------
-- Table structure for merchant_payment_link_delivery_request
-- ----------------------------
DROP TABLE IF EXISTS `merchant_payment_link_delivery_request`;
CREATE TABLE `merchant_payment_link_delivery_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` varchar(80) NOT NULL,
  `merchant_id` varchar(64) NOT NULL,
  `link_id` varchar(32) NOT NULL,
  `fingerprint` char(64) NOT NULL,
  `recipient_hash` char(64) NOT NULL,
  `recipient_masked` varchar(260) NOT NULL,
  `locale` varchar(12) NOT NULL,
  `biz_no` char(64) NOT NULL,
  `email_record_id` bigint DEFAULT NULL,
  `created_by` varchar(128) NOT NULL,
  `create_time` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_link_send_request` (`merchant_id`,`request_id`),
  UNIQUE KEY `uk_merchant_link_send_business` (`biz_no`),
  KEY `idx_merchant_link_send_page` (`merchant_id`,`link_id`,`create_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付邀请幂等请求，不保存客户邮箱或链接令牌原文';

-- ----------------------------
-- Table structure for merchant_profile_change_request
-- ----------------------------
DROP TABLE IF EXISTS `merchant_profile_change_request`;
CREATE TABLE `merchant_profile_change_request` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_no` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资料变更申请编号',
  `merchant_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '平台商户号',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'DRAFT、PENDING_REVIEW、SUPPLEMENT_REQUIRED、APPROVED、REJECTED或WITHDRAWN',
  `active_flag` tinyint DEFAULT NULL COMMENT '活动申请为1，终态为NULL，用于单商户活动申请唯一约束',
  `current_snapshot_cipher` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建申请时正式资料AES-GCM加密快照',
  `proposed_snapshot_cipher` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '拟变更资料AES-GCM加密快照',
  `changed_fields_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '变更字段编码JSON数组',
  `submit_comment` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '商户提交说明',
  `review_comment` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '审核意见或补件说明',
  `submitted_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '提交商户账号ID',
  `reviewed_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '审核管理账号ID',
  `submitted_at` datetime(3) DEFAULT NULL COMMENT '提交审核时间',
  `reviewed_at` datetime(3) DEFAULT NULL COMMENT '审核处理时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '删除标识',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_profile_change_request_no` (`request_no`),
  UNIQUE KEY `uk_merchant_profile_change_active` (`merchant_id`,`active_flag`),
  KEY `idx_merchant_profile_change_review` (`status`,`submitted_at`,`id`),
  KEY `idx_merchant_profile_change_history` (`merchant_id`,`gmt_create`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户资料变更申请';

-- ----------------------------
-- Table structure for merchant_related_person
-- ----------------------------
DROP TABLE IF EXISTS `merchant_related_person`;
CREATE TABLE `merchant_related_person` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(32) NOT NULL COMMENT '平台商户号',
  `full_name` varchar(128) NOT NULL COMMENT '人员姓名',
  `person_roles` varchar(256) NOT NULL COMMENT '人员角色，多值使用逗号分隔',
  `nationality` char(3) DEFAULT NULL COMMENT '国籍国家三字码',
  `date_of_birth` date DEFAULT NULL COMMENT '出生日期',
  `residence_country` char(3) DEFAULT NULL COMMENT '居住国家三字码',
  `residential_address` varchar(512) DEFAULT NULL COMMENT '居住地址',
  `id_type` varchar(32) DEFAULT NULL COMMENT '证件类型',
  `id_number_cipher` varchar(1024) DEFAULT NULL COMMENT '证件号AES-GCM密文',
  `id_number_masked` varchar(64) DEFAULT NULL COMMENT '证件号脱敏值',
  `id_expiry_date` date DEFAULT NULL COMMENT '证件有效期',
  `ownership_percentage` decimal(7,4) DEFAULT NULL COMMENT '持股比例百分比',
  `controller_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否实际控制人',
  `pep_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否政治公众人物',
  `email` varchar(128) DEFAULT NULL COMMENT '联系邮箱',
  `phone` varchar(32) DEFAULT NULL COMMENT '联系电话',
  `display_order` int NOT NULL DEFAULT '0' COMMENT '页面展示顺序',
  `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '删除标识',
  PRIMARY KEY (`id`),
  KEY `idx_merchant_related_person_mid` (`merchant_id`,`deleted`,`display_order`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户法定代表人、董事、UBO及授权人';

-- ----------------------------
-- Table structure for merchant_reserve_action
-- ----------------------------
DROP TABLE IF EXISTS `merchant_reserve_action`;
CREATE TABLE `merchant_reserve_action` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `reserve_action_no` varchar(64) NOT NULL COMMENT '保证金动作全局幂等号',
  `reserve_item_id` bigint NOT NULL COMMENT '保证金聚合ID',
  `reserve_no` varchar(64) NOT NULL COMMENT '保证金聚合业务号快照',
  `settlement_batch_no` varchar(19) NOT NULL COMMENT '应用本动作的结算批次号',
  `candidate_id` bigint NOT NULL COMMENT '来源结算候选ID',
  `source_reserve_detail_no` varchar(64) NOT NULL COMMENT '来源保证金清分明细或被冲正动作号',
  `reversal_of_action_id` bigint DEFAULT NULL COMMENT '冲正动作引用的原保证金动作ID',
  `action_type` varchar(32) NOT NULL COMMENT 'HOLD、RETURN、RELEASE、REVERSAL_HOLD、REVERSAL_RETURN、REVERSAL_RELEASE',
  `direction` varchar(8) NOT NULL COMMENT 'DEBIT 增加责任、CREDIT 减少责任',
  `currency` char(3) NOT NULL COMMENT '原交易标签/保证金币种；禁止换汇',
  `amount` decimal(24,8) NOT NULL COMMENT '原保证金币种正数金额',
  `action_time` datetime(3) NOT NULL COMMENT '动作应用时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reserve_action_no` (`reserve_action_no`),
  UNIQUE KEY `uk_reserve_action_source_batch` (`settlement_batch_no`,`source_reserve_detail_no`),
  UNIQUE KEY `uk_reserve_action_reversal_ref` (`reversal_of_action_id`),
  KEY `idx_reserve_action_item` (`reserve_item_id`,`id`),
  KEY `idx_reserve_action_batch` (`settlement_batch_no`,`candidate_id`,`id`),
  CONSTRAINT `chk_reserve_action_value` CHECK (((`amount` > 0) and (`direction` in (_utf8mb4'DEBIT',_utf8mb4'CREDIT')) and (`action_type` in (_utf8mb4'HOLD',_utf8mb4'RETURN',_utf8mb4'RELEASE',_utf8mb4'ADJUSTMENT',_utf8mb4'REVERSAL_HOLD',_utf8mb4'REVERSAL_RETURN',_utf8mb4'REVERSAL_RELEASE',_utf8mb4'REVERSAL_ADJUSTMENT')) and (((`action_type` in (_utf8mb4'HOLD',_utf8mb4'RETURN',_utf8mb4'RELEASE',_utf8mb4'ADJUSTMENT')) and (`reversal_of_action_id` is null)) or ((`action_type` in (_utf8mb4'REVERSAL_HOLD',_utf8mb4'REVERSAL_RETURN',_utf8mb4'REVERSAL_RELEASE',_utf8mb4'REVERSAL_ADJUSTMENT')) and (`reversal_of_action_id` is not null))) and (((`action_type` = _utf8mb4'HOLD') and (`direction` = _utf8mb4'DEBIT')) or ((`action_type` in (_utf8mb4'RETURN',_utf8mb4'RELEASE')) and (`direction` = _utf8mb4'CREDIT')) or ((`action_type` = _utf8mb4'REVERSAL_HOLD') and (`direction` = _utf8mb4'CREDIT')) or ((`action_type` in (_utf8mb4'REVERSAL_RETURN',_utf8mb4'REVERSAL_RELEASE')) and (`direction` = _utf8mb4'DEBIT')) or (`action_type` in (_utf8mb4'ADJUSTMENT',_utf8mb4'REVERSAL_ADJUSTMENT')))))
) ENGINE=InnoDB AUTO_INCREMENT=60944 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保证金聚合不可变动作和最终幂等事实；保证金始终使用原标签币种';

-- ----------------------------
-- Table structure for merchant_reserve_item
-- ----------------------------
DROP TABLE IF EXISTS `merchant_reserve_item`;
CREATE TABLE `merchant_reserve_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `reserve_no` varchar(64) NOT NULL COMMENT '保证金明细号',
  `account_id` bigint NOT NULL COMMENT '资金账户ID',
  `merchant_id` varchar(64) NOT NULL COMMENT '商户号',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '来源交易号',
  `source_business_no` varchar(128) NOT NULL COMMENT '来源业务单号',
  `currency` char(3) NOT NULL COMMENT '保证金币种，等于结算币种',
  `retained_amount` decimal(24,8) NOT NULL COMMENT '留存金额',
  `debit_adjustment_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '借方调整累计；增加原标签币种保证金责任',
  `credit_adjustment_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '贷方调整累计；减少原标签币种保证金责任',
  `returned_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '退款等动作已返还的原标签币种保证金',
  `released_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '累计释放金额',
  `reversed_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '冲正撤销的原标签币种保证金',
  `reserve_status` varchar(24) NOT NULL DEFAULT 'HELD' COMMENT '状态：HELD、RELEASABLE、RELEASED、FROZEN、DEDUCTED',
  `expected_release_date` date DEFAULT NULL COMMENT '预计释放日期；第一阶段允许为空',
  `release_batch_no` varchar(64) DEFAULT NULL COMMENT '释放批次号',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '保证金聚合并发CAS版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reserve_no` (`reserve_no`),
  UNIQUE KEY `uk_reserve_merchant_source_business` (`merchant_id`,`source_business_no`),
  KEY `idx_reserve_source` (`source_transaction_id`,`source_business_no`),
  KEY `idx_reserve_account_status_release` (`account_id`,`merchant_id`,`reserve_status`,`expected_release_date`,`id`),
  KEY `idx_reserve_status_release` (`reserve_status`,`expected_release_date`,`account_id`,`id`),
  CONSTRAINT `chk_reserve_amount` CHECK (((`retained_amount` > 0) and (`debit_adjustment_amount` >= 0) and (`credit_adjustment_amount` >= 0) and (`returned_amount` >= 0) and (`released_amount` >= 0) and (`reversed_amount` >= 0) and ((((`returned_amount` + `released_amount`) + `credit_adjustment_amount`) + `reversed_amount`) <= (`retained_amount` + `debit_adjustment_amount`)))),
  CONSTRAINT `chk_reserve_status` CHECK ((`reserve_status` in (_utf8mb4'HELD',_utf8mb4'PARTIALLY_RETURNED',_utf8mb4'RETURNED',_utf8mb4'RELEASABLE',_utf8mb4'RELEASED',_utf8mb4'FROZEN',_utf8mb4'DEDUCTED',_utf8mb4'REVERSED',_utf8mb4'ADJUSTED')))
) ENGINE=InnoDB AUTO_INCREMENT=55365 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户保证金留存和释放明细表';

-- ----------------------------
-- Table structure for merchant_review_record
-- ----------------------------
DROP TABLE IF EXISTS `merchant_review_record`;
CREATE TABLE `merchant_review_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(32) NOT NULL COMMENT '平台商户号',
  `review_scope` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ONBOARDING' COMMENT 'ONBOARDING或PROFILE_CHANGE',
  `request_no` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '资料变更申请编号',
  `review_action` varchar(32) NOT NULL COMMENT '提交、通过、补件或驳回动作',
  `from_status` varchar(32) NOT NULL COMMENT '动作前审核状态',
  `to_status` varchar(32) NOT NULL COMMENT '动作后审核状态',
  `review_comment` varchar(1000) DEFAULT NULL COMMENT '审核意见或补件说明',
  `operator_id` varchar(64) DEFAULT NULL COMMENT '审核操作人账号ID',
  `operator_name` varchar(128) DEFAULT NULL COMMENT '审核操作人名称',
  `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `target_type` varchar(24) DEFAULT NULL COMMENT '支付方式申请批次、明细或准入治理的审核目标类型',
  `target_key` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '审核目标的规范业务键',
  `application_item_id` bigint DEFAULT NULL COMMENT '关联的支付方式申请明细主键',
  `round_no` int DEFAULT NULL COMMENT '本次审核对应的申请轮次',
  `operator_app_code` varchar(32) DEFAULT NULL COMMENT '实际操作者所属应用编码',
  `operator_user_id` bigint DEFAULT NULL COMMENT '实际操作者可信自然人主体主键',
  `command_request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '成功业务命令的幂等请求键',
  `payload_hash` binary(32) DEFAULT NULL COMMENT '按稳定语义规范化的业务命令指纹',
  `result_json` json DEFAULT NULL COMMENT '不可变的成功命令回执',
  `fact_snapshot_json` json DEFAULT NULL COMMENT '经白名单限制的审核前后事实和来源引用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mrr_scope_merchant_cmd` (`review_scope`,`merchant_id`,`command_request_id`),
  KEY `idx_merchant_review_record_mid` (`merchant_id`,`gmt_create`,`id`),
  KEY `idx_merchant_review_request` (`review_scope`,`request_no`,`gmt_create`,`id`),
  KEY `idx_mrr_method_item` (`review_scope`,`merchant_id`,`application_item_id`,`gmt_create`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户开户审核不可变记录';

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
) ENGINE=InnoDB AUTO_INCREMENT=8921 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受管永久缓存可靠失效事件表（兼容保留历史表名）';

-- ----------------------------
-- Table structure for merchant_settlement_profile
-- ----------------------------
DROP TABLE IF EXISTS `merchant_settlement_profile`;
CREATE TABLE `merchant_settlement_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `settlement_profile_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '稳定结算档案号',
  `merchant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '平台商户号',
  `settlement_account_id` bigint NOT NULL COMMENT '目标商户资金账户ID',
  `target_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标结算币种',
  `target_currency_exponent` tinyint NOT NULL COMMENT '目标币种ISO小数位',
  `business_time_zone` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '日切使用的IANA业务时区',
  `beneficiary_name` varchar(128) DEFAULT NULL COMMENT '结算收款人名称',
  `bank_name` varchar(128) DEFAULT NULL COMMENT '结算银行名称',
  `bank_country` char(3) DEFAULT NULL COMMENT '结算银行国家三字码',
  `bank_account_cipher` varchar(1024) DEFAULT NULL COMMENT '结算账号或IBAN密文',
  `swift_bic` varchar(32) DEFAULT NULL COMMENT 'SWIFT或BIC',
  `daily_cutoff_time` time NOT NULL COMMENT '商户本地每日结算日切时间',
  `processing_mode` varchar(16) NOT NULL DEFAULT 'AUTO_POST' COMMENT 'AUTO_POST automatic posting, AUTO_REVIEW automatic selection with approval, MANUAL admin selection',
  `profile_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVEã€RETIREDã€SUSPENDED',
  `active_slot` tinyint DEFAULT NULL COMMENT '活动档案固定为1，非活动档案为空，用于唯一约束',
  `effective_date` date NOT NULL COMMENT '档案生效业务日期',
  `expire_date` date DEFAULT NULL COMMENT '档案失效业务日期，长期有效为空',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '档案并发和审计版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_profile_no` (`settlement_profile_no`),
  UNIQUE KEY `uk_settlement_profile_active` (`merchant_id`,`active_slot`),
  KEY `idx_settlement_profile_account` (`settlement_account_id`,`merchant_id`,`id`),
  CONSTRAINT `chk_settlement_profile_processing_mode` CHECK ((`processing_mode` in (_utf8mb4'AUTO_POST',_utf8mb4'AUTO_REVIEW',_utf8mb4'MANUAL'))),
  CONSTRAINT `chk_settlement_profile_value` CHECK (((`target_currency_exponent` between 0 and 8) and (`profile_status` in (_utf8mb4'ACTIVE',_utf8mb4'RETIRED',_utf8mb4'SUSPENDED')) and (((`profile_status` = _utf8mb4'ACTIVE') and (`active_slot` = 1)) or ((`profile_status` <> _utf8mb4'ACTIVE') and (`active_slot` is null))) and ((`expire_date` is null) or (`expire_date` >= `effective_date`))))
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户结算日历、目标币种和资金账户的权威档案；每商户最多一条活动档案';

-- ----------------------------
-- Table structure for merchant_settlement_profile_command_audit
-- ----------------------------
DROP TABLE IF EXISTS `merchant_settlement_profile_command_audit`;
CREATE TABLE `merchant_settlement_profile_command_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_key` varchar(64) NOT NULL COMMENT 'service-admin生成的全局请求幂等键',
  `request_fingerprint` char(64) NOT NULL COMMENT '规范化命令SHA-256指纹，用于拒绝同键异参',
  `command_action` varchar(24) NOT NULL COMMENT 'CREATE或CREATE_VERSION',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `source_profile_no` varchar(64) DEFAULT NULL COMMENT '版本切换前档案号；初始创建为空',
  `result_profile_no` varchar(64) NOT NULL COMMENT '命令生成的活动档案号',
  `expected_version` bigint DEFAULT NULL COMMENT '页面读取的源档案版本；初始创建为空',
  `operator_account_id` bigint NOT NULL COMMENT '可信管理账号ID',
  `operator_account_name` varchar(128) NOT NULL COMMENT '管理账号名称快照',
  `operator_role_snapshot` varchar(1000) NOT NULL COMMENT '操作时角色权限快照',
  `client_ip` varchar(64) NOT NULL COMMENT '操作客户端IP',
  `user_agent` varchar(500) NOT NULL COMMENT '操作客户端User-Agent',
  `reason` varchar(400) NOT NULL COMMENT '人工配置或变更原因',
  `operation_time` datetime(3) NOT NULL COMMENT '管理端记录的实际操作时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '结算领域提交时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_profile_command_request` (`request_key`),
  UNIQUE KEY `uk_settlement_profile_command_result` (`result_profile_no`),
  KEY `idx_settlement_profile_command_merchant` (`merchant_id`,`create_time`,`id`),
  KEY `idx_settlement_profile_command_source` (`source_profile_no`,`create_time`,`id`),
  CONSTRAINT `chk_settlement_profile_command_action` CHECK ((`command_action` in (_utf8mb4'CREATE',_utf8mb4'CREATE_VERSION'))),
  CONSTRAINT `chk_settlement_profile_command_version` CHECK ((((`command_action` = _utf8mb4'CREATE') and (`source_profile_no` is null) and (`expected_version` is null)) or ((`command_action` = _utf8mb4'CREATE_VERSION') and (`source_profile_no` is not null) and (`expected_version` is not null) and (`expected_version` >= 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算档案创建和版本切换不可变审计；request_key提供持久化幂等';

-- ----------------------------
-- Table structure for monitor_alert_handle_history
-- ----------------------------
DROP TABLE IF EXISTS `monitor_alert_handle_history`;
CREATE TABLE `monitor_alert_handle_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `source_type` varchar(32) NOT NULL COMMENT '告警来源类型：CHANNEL/SECURITY',
  `source_id` varchar(64) NOT NULL COMMENT '来源告警唯一标识',
  `action` varchar(32) NOT NULL COMMENT '处置动作：TAKEOVER/MARK_PROCESSING/CLOSE',
  `from_status` varchar(24) DEFAULT NULL COMMENT '变更前状态',
  `to_status` varchar(24) NOT NULL COMMENT '变更后状态',
  `operator_account_id` varchar(64) DEFAULT NULL COMMENT '操作人账号ID',
  `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人名称',
  `remark` varchar(512) DEFAULT NULL COMMENT '处置备注，不得记录敏感请求内容',
  `operated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_monitor_alert_history_source_time` (`source_type`,`source_id`,`operated_at`),
  KEY `idx_monitor_alert_history_operator_time` (`operator_account_id`,`operated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='监控告警人工处置历史表';

-- ----------------------------
-- Table structure for monitor_alert_handle_state
-- ----------------------------
DROP TABLE IF EXISTS `monitor_alert_handle_state`;
CREATE TABLE `monitor_alert_handle_state` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `source_type` varchar(32) NOT NULL COMMENT '告警来源类型：CHANNEL/SECURITY',
  `source_id` varchar(64) NOT NULL COMMENT '来源告警唯一标识',
  `status` varchar(24) NOT NULL DEFAULT 'OPEN' COMMENT '处置状态：OPEN/PROCESSING/RECOVERED/CLOSED',
  `owner_account_id` varchar(64) DEFAULT NULL COMMENT '负责人账号ID',
  `owner_name` varchar(100) DEFAULT NULL COMMENT '负责人名称',
  `handle_remark` varchar(512) DEFAULT NULL COMMENT '最新处置备注，不得记录敏感请求内容',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_monitor_alert_handle_source` (`source_type`,`source_id`),
  KEY `idx_monitor_alert_handle_status_time` (`status`,`update_time`),
  KEY `idx_monitor_alert_handle_owner_time` (`owner_account_id`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='监控告警人工处置状态表';

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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件发件账户配置表';

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
  `business_notification_key` binary(32) DEFAULT NULL COMMENT '稳定业务通知的幂等键，旧通知为空',
  `business_notification_payload_hash` binary(32) DEFAULT NULL COMMENT '通知受众与白名单内容的规范摘要',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_send_record_no` (`email_no`),
  UNIQUE KEY `uk_email_send_record_biz_notif` (`business_notification_key`),
  KEY `idx_email_record_app_scene` (`app_code`,`scene_code`,`send_status`,`deleted`),
  KEY `idx_email_record_merchant` (`merchant_id`,`merchant_no`,`deleted`),
  KEY `idx_email_record_template` (`template_code`),
  KEY `idx_email_record_biz` (`biz_type`,`biz_no`),
  KEY `idx_email_record_create_time` (`create_time`),
  KEY `idx_email_record_send_time` (`send_success_time`),
  KEY `idx_email_record_retry` (`app_code`,`send_status`,`next_retry_time`,`deleted`),
  KEY `idx_email_record_recovery` (`app_code`,`send_status`,`send_start_time`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=59 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件发送记录表';

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
) ENGINE=InnoDB AUTO_INCREMENT=142 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件模板表';

-- ----------------------------
-- Table structure for msg_email_template_blue_white_backup_20260820
-- ----------------------------
DROP TABLE IF EXISTS `msg_email_template_blue_white_backup_20260820`;
CREATE TABLE `msg_email_template_blue_white_backup_20260820` (
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
) ENGINE=InnoDB AUTO_INCREMENT=102 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件模板表';

-- ----------------------------
-- Table structure for msg_email_template_visual_backup_20261014
-- ----------------------------
DROP TABLE IF EXISTS `msg_email_template_visual_backup_20261014`;
CREATE TABLE `msg_email_template_visual_backup_20261014` (
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
) ENGINE=InnoDB AUTO_INCREMENT=117 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件模板表';

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
) ENGINE=InnoDB AUTO_INCREMENT=69 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hosted Checkout 支付尝试表，记录卡支付提交、MPGS 请求、3DS 编排和尝试结果，不保存卡敏感原文。';

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
) ENGINE=InnoDB AUTO_INCREMENT=2284 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hosted Checkout 业务和页面事件日志，支撑审计、排查和用户体验分析。';

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
) ENGINE=InnoDB AUTO_INCREMENT=111 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hosted Checkout 安全事件日志，用于非法请求拦截、风控审计和异常访问排查。';

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
  `allowed_payment_methods_json` json NOT NULL COMMENT '商户本次允许的支付方式和品牌快照，V1 只允许 BANK_CARD + MPGS。',
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
  `shipping_info_json` json DEFAULT NULL COMMENT '收货信息结构化明文 JSON。',
  `redirect_url` varchar(512) DEFAULT NULL COMMENT '交易完成后 Form POST 的商户结果页地址明文。',
  `locale` varchar(16) NOT NULL DEFAULT 'en-US' COMMENT '收银台默认语言，如 en-US、zh-CN。',
  `payer_country` varchar(3) DEFAULT NULL COMMENT '商户传入或付款人选择的国家/地区，ISO 3166-1 alpha-3。',
  `payer_email` varchar(64) DEFAULT NULL COMMENT '付款人邮箱明文，禁止普通日志输出。',
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
) ENGINE=InnoDB AUTO_INCREMENT=421 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hosted Checkout 会话主表，保存收银台订单意向、展示快照、状态和交易关联，不保存卡敏感信息。';

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
) ENGINE=InnoDB AUTO_INCREMENT=423 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hosted Checkout URL 令牌表，支持不保存 token 明文的幂等重试、令牌轮换和吊销。';

-- ----------------------------
-- Table structure for payment_link
-- ----------------------------
DROP TABLE IF EXISTS `payment_link`;
CREATE TABLE `payment_link` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `link_id` varchar(32) NOT NULL,
  `merchant_id` varchar(64) NOT NULL,
  `merchant_order_no` varchar(64) NOT NULL,
  `link_name` varchar(120) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `currency` char(3) NOT NULL,
  `amount` decimal(24,8) NOT NULL,
  `currency_exponent` int NOT NULL,
  `locale` varchar(12) NOT NULL,
  `allowed_brands_json` text NOT NULL,
  `merchant_display_name` varchar(256) NOT NULL,
  `notify_url` varchar(512) DEFAULT NULL,
  `redirect_url` varchar(512) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `expire_time` datetime(3) NOT NULL,
  `create_request_id` varchar(80) NOT NULL,
  `request_fingerprint` char(64) NOT NULL,
  `created_app_code` varchar(16) NOT NULL,
  `created_by` varchar(128) NOT NULL,
  `disabled_by` varchar(128) DEFAULT NULL,
  `disable_request_id` varchar(80) DEFAULT NULL,
  `disable_reason` varchar(500) DEFAULT NULL,
  `disabled_time` datetime(3) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL,
  `update_time` datetime(3) NOT NULL,
  `allowed_methods_json` json DEFAULT NULL COMMENT '带版本的链接方式范围，空值表示沿用历史银行卡品牌范围',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_link_id` (`link_id`),
  UNIQUE KEY `uk_payment_link_merchant_order` (`merchant_id`,`merchant_order_no`),
  UNIQUE KEY `uk_payment_link_create_request` (`merchant_id`,`create_request_id`),
  KEY `idx_payment_link_merchant_created` (`merchant_id`,`create_time`,`id`),
  KEY `idx_payment_link_created` (`create_time`,`id`),
  KEY `idx_payment_link_order_created` (`merchant_order_no`,`create_time`,`id`),
  KEY `idx_payment_link_status_expire` (`status`,`expire_time`,`id`),
  CONSTRAINT `ck_payment_link_positive_amount` CHECK ((`amount` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固定金额、单次成功的收单支付链接';

-- ----------------------------
-- Table structure for payment_link_access
-- ----------------------------
DROP TABLE IF EXISTS `payment_link_access`;
CREATE TABLE `payment_link_access` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `link_id` varchar(32) NOT NULL,
  `key_version` varchar(32) NOT NULL,
  `token_hash` char(64) NOT NULL,
  `create_time` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_link_access_link` (`link_id`),
  UNIQUE KEY `uk_payment_link_access_hash` (`token_hash`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='链接访问摘要，不保存令牌原文';

-- ----------------------------
-- Table structure for payment_link_checkout_binding
-- ----------------------------
DROP TABLE IF EXISTS `payment_link_checkout_binding`;
CREATE TABLE `payment_link_checkout_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `link_id` varchar(32) NOT NULL,
  `collection_id` varchar(64) NOT NULL,
  `checkout_session_id` varchar(64) NOT NULL,
  `create_time` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_link_binding_link` (`link_id`),
  UNIQUE KEY `uk_payment_link_binding_collection` (`collection_id`),
  UNIQUE KEY `uk_payment_link_binding_session` (`checkout_session_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='链接会话唯一绑定，失败重试不新建独立会话';

-- ----------------------------
-- Table structure for payment_link_collection
-- ----------------------------
DROP TABLE IF EXISTS `payment_link_collection`;
CREATE TABLE `payment_link_collection` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `collection_id` varchar(64) NOT NULL,
  `link_id` varchar(32) NOT NULL,
  `merchant_id` varchar(64) NOT NULL,
  `merchant_order_no` varchar(64) NOT NULL,
  `submission_deadline` datetime(3) NOT NULL,
  `create_time` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_link_collection_id` (`collection_id`),
  UNIQUE KEY `uk_payment_link_collection_link` (`link_id`),
  UNIQUE KEY `uk_payment_link_collection_order` (`merchant_id`,`merchant_order_no`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='链接到现有核心订单的唯一收款关联';

-- ----------------------------
-- Table structure for payment_link_event
-- ----------------------------
DROP TABLE IF EXISTS `payment_link_event`;
CREATE TABLE `payment_link_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` varchar(64) NOT NULL,
  `link_id` varchar(32) NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `actor_app_code` varchar(16) NOT NULL,
  `actor_id` varchar(128) NOT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_link_event_id` (`event_id`),
  KEY `idx_payment_link_event_link` (`link_id`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='链接状态事务内脱敏审计';

-- ----------------------------
-- Table structure for payment_route_candidate
-- ----------------------------
DROP TABLE IF EXISTS `payment_route_candidate`;
CREATE TABLE `payment_route_candidate` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_id` bigint NOT NULL COMMENT '支付路由规则ID',
  `payment_method` varchar(64) NOT NULL COMMENT '候选所属收单支付方式',
  `card_brand` varchar(64) NOT NULL DEFAULT 'NONE' COMMENT 'Card brand; NONE for non-card payment methods',
  `channel_id` bigint NOT NULL COMMENT '渠道ID，关联channel_info.id',
  `mid_config_id` bigint NOT NULL COMMENT '渠道MID配置ID，关联channel_mid_config.id',
  `candidate_priority` int NOT NULL COMMENT '第三层候选MID优先级，数值越小优先级越高',
  `candidate_weight` decimal(5,2) DEFAULT NULL COMMENT 'WEIGHTç±»åž‹å€™é€‰æƒé‡ï¼ŒæŒ‰æ”¯ä»˜æ–¹å¼åˆè®¡100.00',
  `candidate_status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_route_candidate_mid_deleted` (`rule_id`,`payment_method`,`card_brand`,`mid_config_id`,`deleted`),
  UNIQUE KEY `uk_payment_route_candidate_priority_deleted` (`rule_id`,`payment_method`,`card_brand`,`candidate_priority`,`deleted`),
  KEY `idx_payment_route_candidate_channel` (`channel_id`,`candidate_status`,`deleted`),
  KEY `idx_payment_route_candidate_mid` (`mid_config_id`,`candidate_status`,`deleted`),
  KEY `idx_payment_route_candidate_scope` (`payment_method`,`card_brand`,`candidate_status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付路由候选MID配置表';

-- ----------------------------
-- Table structure for payment_route_change_log
-- ----------------------------
DROP TABLE IF EXISTS `payment_route_change_log`;
CREATE TABLE `payment_route_change_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_id` bigint NOT NULL COMMENT '支付路由规则ID',
  `rule_code` varchar(64) NOT NULL COMMENT '规则编码审计冗余',
  `action_type` varchar(32) NOT NULL COMMENT '操作类型：CREATE、UPDATE、COPY、PUBLISH、DISABLE、DELETE',
  `before_version` int DEFAULT NULL COMMENT '变更前版本',
  `after_version` int DEFAULT NULL COMMENT '变更后版本',
  `before_snapshot_json` json DEFAULT NULL COMMENT '变更前非敏感配置快照',
  `after_snapshot_json` json DEFAULT NULL COMMENT '变更后非敏感配置快照',
  `operator_account_id` bigint DEFAULT NULL COMMENT '可信登录账号ID',
  `operator_name` varchar(64) NOT NULL COMMENT '可信操作人名称',
  `operation_reason` varchar(500) NOT NULL COMMENT '操作原因',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_payment_route_change_rule_time` (`rule_id`,`create_time`,`id`),
  KEY `idx_payment_route_change_operator_time` (`operator_account_id`,`create_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付路由配置不可变变更日志表';

-- ----------------------------
-- Table structure for payment_route_method
-- ----------------------------
DROP TABLE IF EXISTS `payment_route_method`;
CREATE TABLE `payment_route_method` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®ID',
  `rule_id` bigint NOT NULL COMMENT 'æ”¯ä»˜è·¯ç”±è§„åˆ™ID',
  `payment_method` varchar(64) NOT NULL COMMENT 'è§„åˆ™é€‚ç”¨çš„æ”¶å•æ”¯ä»˜æ–¹å¼',
  `card_brand` varchar(64) NOT NULL DEFAULT 'NONE' COMMENT 'Card brand; NONE for non-card payment methods',
  `create_by` varchar(64) DEFAULT NULL COMMENT 'åˆ›å»ºäºº',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_by` varchar(64) DEFAULT NULL COMMENT 'æ›´æ–°äºº',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'æ›´æ–°æ—¶é—´',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT 'åˆ é™¤æ ‡è¯†ï¼š0æœªåˆ é™¤ï¼Œå¤§äºŽ0ä¸ºåˆ é™¤è®°å½•ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_route_method_rule_scope_deleted` (`rule_id`,`payment_method`,`card_brand`,`deleted`),
  KEY `idx_payment_route_method_scope_rule` (`payment_method`,`card_brand`,`rule_id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ”¯ä»˜è·¯ç”±è§„åˆ™é€‚ç”¨æ”¯ä»˜æ–¹å¼è¡¨';

-- ----------------------------
-- Table structure for payment_route_rule
-- ----------------------------
DROP TABLE IF EXISTS `payment_route_rule`;
CREATE TABLE `payment_route_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_code` varchar(64) NOT NULL COMMENT '稳定路由规则编码',
  `rule_name` varchar(128) NOT NULL COMMENT '路由规则名称',
  `rule_type` varchar(32) NOT NULL COMMENT '规则类型：FIXED固定路由、STANDARD普通路由',
  `scope_type` varchar(32) NOT NULL COMMENT '作用范围：GLOBAL全局、MERCHANT商户',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号，MERCHANT范围时必填',
  `business_type` varchar(32) NOT NULL DEFAULT 'ACQUIRING' COMMENT '业务类型；阶段1A仅支持ACQUIRING',
  `payment_method` varchar(64) DEFAULT NULL COMMENT '支付方式，空表示不限',
  `transaction_type` varchar(64) DEFAULT NULL COMMENT '交易类型，空表示不限',
  `card_brand` varchar(64) DEFAULT NULL COMMENT '卡品牌，空表示不限',
  `label_currency` char(3) DEFAULT NULL COMMENT '商户标签币种，空表示不限',
  `issuer_country_scope` varchar(1024) NOT NULL DEFAULT 'ALL' COMMENT '发卡国家范围，ALL或ISO Alpha-2逗号分隔',
  `billing_country_scope` varchar(1024) NOT NULL DEFAULT 'ALL' COMMENT '账单国家范围，ALL或ISO Alpha-2逗号分隔',
  `amount_min` decimal(24,8) DEFAULT NULL COMMENT '标签金额下限，包含边界',
  `amount_max` decimal(24,8) DEFAULT NULL COMMENT '标签金额上限，包含边界',
  `rule_priority` int NOT NULL COMMENT '第一层规则优先级，数值越小优先级越高',
  `unavailable_policy` varchar(32) NOT NULL COMMENT '候选不可用策略：FALLBACK回退、REJECT拒绝',
  `rule_status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '生命周期：DRAFT、ENABLED、DISABLED',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) DEFAULT NULL COMMENT '运营备注',
  `published_by` varchar(64) DEFAULT NULL COMMENT '最近发布人',
  `published_time` datetime(3) DEFAULT NULL COMMENT '最近发布时间',
  `disabled_by` varchar(64) DEFAULT NULL COMMENT '最近停用人',
  `disabled_time` datetime(3) DEFAULT NULL COMMENT '最近停用时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_route_rule_code_deleted` (`rule_code`,`deleted`),
  KEY `idx_payment_route_rule_status_priority` (`rule_status`,`business_type`,`rule_priority`,`deleted`),
  KEY `idx_payment_route_rule_merchant` (`scope_type`,`merchant_id`,`rule_status`,`deleted`),
  KEY `idx_payment_route_rule_dimension` (`payment_method`,`transaction_type`,`card_brand`,`label_currency`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付路由规则配置表；阶段1A不接入支付链路';

-- ----------------------------
-- Table structure for payment_route_rule_merchant
-- ----------------------------
DROP TABLE IF EXISTS `payment_route_rule_merchant`;
CREATE TABLE `payment_route_rule_merchant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®ID',
  `rule_id` bigint NOT NULL COMMENT 'æ”¯ä»˜è·¯ç”±è§„åˆ™ID',
  `merchant_id` varchar(32) NOT NULL COMMENT 'å¹³å°å•†æˆ·å·',
  `create_by` varchar(64) DEFAULT NULL COMMENT 'åˆ›å»ºäºº',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_by` varchar(64) DEFAULT NULL COMMENT 'æ›´æ–°äºº',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'æ›´æ–°æ—¶é—´',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT 'åˆ é™¤æ ‡è¯†ï¼š0æœªåˆ é™¤ï¼Œå¤§äºŽ0ä¸ºåˆ é™¤è®°å½•ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_route_rule_merchant_deleted` (`rule_id`,`merchant_id`,`deleted`),
  KEY `idx_payment_route_rule_merchant_lookup` (`merchant_id`,`rule_id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ”¯ä»˜è·¯ç”±è§„åˆ™æŒ‡å®šå•†æˆ·å…³è”è¡¨';

-- ----------------------------
-- Table structure for payment_route_strategy
-- ----------------------------
DROP TABLE IF EXISTS `payment_route_strategy`;
CREATE TABLE `payment_route_strategy` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_id` bigint NOT NULL COMMENT '支付路由规则ID',
  `strategy_type` varchar(32) NOT NULL COMMENT '策略类型；阶段1A仅支持PRIORITY',
  `strategy_priority` int NOT NULL COMMENT '第二层策略优先级，数值越小优先级越高',
  `history_lookback_months` int DEFAULT NULL COMMENT 'åŽ†å²æˆåŠŸMIDè·¯ç”±å›žçœ‹æœˆæ•°ï¼Œå…¶ä»–ç±»åž‹ä¸ºç©º',
  `strategy_status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_route_strategy_rule_type_deleted` (`rule_id`,`strategy_type`,`deleted`),
  KEY `idx_payment_route_strategy_rule_status` (`rule_id`,`strategy_status`,`strategy_priority`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付路由策略配置表';

-- ----------------------------
-- Table structure for payment_route_transaction_type
-- ----------------------------
DROP TABLE IF EXISTS `payment_route_transaction_type`;
CREATE TABLE `payment_route_transaction_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®ID',
  `rule_id` bigint NOT NULL COMMENT 'æ”¯ä»˜è·¯ç”±è§„åˆ™ID',
  `transaction_type` varchar(64) NOT NULL COMMENT 'è§„åˆ™é€‚ç”¨çš„æ”¶å•äº¤æ˜“ç±»åž‹',
  `create_by` varchar(64) DEFAULT NULL COMMENT 'åˆ›å»ºäºº',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_by` varchar(64) DEFAULT NULL COMMENT 'æ›´æ–°äºº',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'æ›´æ–°æ—¶é—´',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT 'åˆ é™¤æ ‡è¯†ï¼š0æœªåˆ é™¤ï¼Œå¤§äºŽ0ä¸ºåˆ é™¤è®°å½•ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_route_transaction_rule_type_deleted` (`rule_id`,`transaction_type`,`deleted`),
  KEY `idx_payment_route_transaction_type_rule` (`transaction_type`,`rule_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ”¯ä»˜è·¯ç”±è§„åˆ™é€‚ç”¨äº¤æ˜“ç±»åž‹è¡¨ï¼›æ— æœ‰æ•ˆè®°å½•è¡¨ç¤ºå…¨éƒ¨äº¤æ˜“ç±»åž‹';

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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡号卡指纹AML名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡BIN区间AML名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='持卡人姓名AML名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='国家地区AML名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮箱域名AML名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AML企业名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IP地址区间AML名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AML法人名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AML商户账单地址名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='手机号AML名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='来源网址AML名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账单地址黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=62 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账单国家地区黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账单邮编黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡BIN区间黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡指纹黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡号黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='持卡人姓名黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备指纹黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮箱地址黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮箱域名黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮箱用户名黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IP地址区间黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发卡行国家地区黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='电话号码黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='高风险区域黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货地址黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货国家地区黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货邮编黑名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=159 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控规则缓存失效事件表';

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
) ENGINE=InnoDB AUTO_INCREMENT=362 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控配置变更日志表';

-- ----------------------------
-- Table structure for risk_evaluation_hit_detail
-- ----------------------------
DROP TABLE IF EXISTS `risk_evaluation_hit_detail`;
CREATE TABLE `risk_evaluation_hit_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `risk_record_no` varchar(64) NOT NULL COMMENT '风控记录号',
  `evaluation_time` datetime(3) NOT NULL COMMENT '所属风控评估时间，也是季度分片路由键',
  `module_type` varchar(32) NOT NULL COMMENT '命中模块类型：AML、BLACK、WHITE、RULE',
  `function_code` varchar(64) NOT NULL COMMENT '命中功能编码',
  `function_name` varchar(128) DEFAULT NULL COMMENT '命中功能名称',
  `rule_id` bigint DEFAULT NULL COMMENT '命中名单或规则ID',
  `hit_element` varchar(64) DEFAULT NULL COMMENT '命中元素类型',
  `hit_value_masked` varchar(255) DEFAULT NULL COMMENT '命中值脱敏展示',
  `risk_level` varchar(32) DEFAULT NULL COMMENT '风险等级',
  `decision_result` varchar(32) NOT NULL COMMENT '本项决策结果',
  `reason_code` varchar(64) DEFAULT NULL COMMENT '明细稳定原因码：NOT_ENABLED、NOT_CONFIGURED、NO_MATCH 等。',
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
  KEY `idx_risk_hit_record_stage` (`risk_record_no`,`stage_order`,`id`),
  KEY `idx_risk_hit_time_record` (`evaluation_time`,`risk_record_no`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=979733 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控评估命中明细表';

-- ----------------------------
-- Table structure for risk_evaluation_hit_detail_202603
-- ----------------------------
DROP TABLE IF EXISTS `risk_evaluation_hit_detail_202603`;
CREATE TABLE `risk_evaluation_hit_detail_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `risk_record_no` varchar(64) NOT NULL COMMENT '风控记录号',
  `evaluation_time` datetime(3) NOT NULL COMMENT '所属风控评估时间，也是季度分片路由键',
  `module_type` varchar(32) NOT NULL COMMENT '命中模块类型：AML、BLACK、WHITE、RULE',
  `function_code` varchar(64) NOT NULL COMMENT '命中功能编码',
  `function_name` varchar(128) DEFAULT NULL COMMENT '命中功能名称',
  `rule_id` bigint DEFAULT NULL COMMENT '命中名单或规则ID',
  `hit_element` varchar(64) DEFAULT NULL COMMENT '命中元素类型',
  `hit_value_masked` varchar(255) DEFAULT NULL COMMENT '命中值脱敏展示',
  `risk_level` varchar(32) DEFAULT NULL COMMENT '风险等级',
  `decision_result` varchar(32) NOT NULL COMMENT '本项决策结果',
  `reason_code` varchar(64) DEFAULT NULL COMMENT '明细稳定原因码：NOT_ENABLED、NOT_CONFIGURED、NO_MATCH 等。',
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
  KEY `idx_risk_hit_record_stage` (`risk_record_no`,`stage_order`,`id`),
  KEY `idx_risk_hit_time_record` (`evaluation_time`,`risk_record_no`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控评估命中明细表';

-- ----------------------------
-- Table structure for risk_evaluation_hit_detail_202604
-- ----------------------------
DROP TABLE IF EXISTS `risk_evaluation_hit_detail_202604`;
CREATE TABLE `risk_evaluation_hit_detail_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `risk_record_no` varchar(64) NOT NULL COMMENT '风控记录号',
  `evaluation_time` datetime(3) NOT NULL COMMENT '所属风控评估时间，也是季度分片路由键',
  `module_type` varchar(32) NOT NULL COMMENT '命中模块类型：AML、BLACK、WHITE、RULE',
  `function_code` varchar(64) NOT NULL COMMENT '命中功能编码',
  `function_name` varchar(128) DEFAULT NULL COMMENT '命中功能名称',
  `rule_id` bigint DEFAULT NULL COMMENT '命中名单或规则ID',
  `hit_element` varchar(64) DEFAULT NULL COMMENT '命中元素类型',
  `hit_value_masked` varchar(255) DEFAULT NULL COMMENT '命中值脱敏展示',
  `risk_level` varchar(32) DEFAULT NULL COMMENT '风险等级',
  `decision_result` varchar(32) NOT NULL COMMENT '本项决策结果',
  `reason_code` varchar(64) DEFAULT NULL COMMENT '明细稳定原因码：NOT_ENABLED、NOT_CONFIGURED、NO_MATCH 等。',
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
  KEY `idx_risk_hit_record_stage` (`risk_record_no`,`stage_order`,`id`),
  KEY `idx_risk_hit_time_record` (`evaluation_time`,`risk_record_no`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000001456760 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控评估命中明细表';

-- ----------------------------
-- Table structure for risk_evaluation_hit_detail_202701
-- ----------------------------
DROP TABLE IF EXISTS `risk_evaluation_hit_detail_202701`;
CREATE TABLE `risk_evaluation_hit_detail_202701` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `risk_record_no` varchar(64) NOT NULL COMMENT '风控记录号',
  `evaluation_time` datetime(3) NOT NULL COMMENT '所属风控评估时间，也是季度分片路由键',
  `module_type` varchar(32) NOT NULL COMMENT '命中模块类型：AML、BLACK、WHITE、RULE',
  `function_code` varchar(64) NOT NULL COMMENT '命中功能编码',
  `function_name` varchar(128) DEFAULT NULL COMMENT '命中功能名称',
  `rule_id` bigint DEFAULT NULL COMMENT '命中名单或规则ID',
  `hit_element` varchar(64) DEFAULT NULL COMMENT '命中元素类型',
  `hit_value_masked` varchar(255) DEFAULT NULL COMMENT '命中值脱敏展示',
  `risk_level` varchar(32) DEFAULT NULL COMMENT '风险等级',
  `decision_result` varchar(32) NOT NULL COMMENT '本项决策结果',
  `reason_code` varchar(64) DEFAULT NULL COMMENT '明细稳定原因码：NOT_ENABLED、NOT_CONFIGURED、NO_MATCH 等。',
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
  KEY `idx_risk_hit_record_stage` (`risk_record_no`,`stage_order`,`id`),
  KEY `idx_risk_hit_time_record` (`evaluation_time`,`risk_record_no`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控评估命中明细表';

-- ----------------------------
-- Table structure for risk_evaluation_locator
-- ----------------------------
DROP TABLE IF EXISTS `risk_evaluation_locator`;
CREATE TABLE `risk_evaluation_locator` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `risk_record_no` varchar(64) NOT NULL COMMENT '全局唯一风控记录号',
  `evaluation_time` datetime(3) NOT NULL COMMENT '原始评估时间，用于精确定位季度分片',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_risk_evaluation_locator_record` (`risk_record_no`),
  KEY `idx_risk_evaluation_locator_time` (`evaluation_time`,`risk_record_no`)
) ENGINE=InnoDB AUTO_INCREMENT=93650 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控记录号季度定位与跨季度幂等表';

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
  `decision_reason_code` varchar(64) DEFAULT NULL COMMENT '最终决策稳定原因码。',
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
) ENGINE=InnoDB AUTO_INCREMENT=35216 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控评估记录表';

-- ----------------------------
-- Table structure for risk_evaluation_record_202603
-- ----------------------------
DROP TABLE IF EXISTS `risk_evaluation_record_202603`;
CREATE TABLE `risk_evaluation_record_202603` (
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
  `decision_reason_code` varchar(64) DEFAULT NULL COMMENT '最终决策稳定原因码。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控评估记录表';

-- ----------------------------
-- Table structure for risk_evaluation_record_202604
-- ----------------------------
DROP TABLE IF EXISTS `risk_evaluation_record_202604`;
CREATE TABLE `risk_evaluation_record_202604` (
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
  `decision_reason_code` varchar(64) DEFAULT NULL COMMENT '最终决策稳定原因码。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202604000000028115 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控评估记录表';

-- ----------------------------
-- Table structure for risk_evaluation_record_202701
-- ----------------------------
DROP TABLE IF EXISTS `risk_evaluation_record_202701`;
CREATE TABLE `risk_evaluation_record_202701` (
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
  `decision_reason_code` varchar(64) DEFAULT NULL COMMENT '最终决策稳定原因码。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控评估记录表';

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控名单导入批次表';

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
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控名单导入错误表';

-- ----------------------------
-- Table structure for risk_merchant_function_config
-- ----------------------------
DROP TABLE IF EXISTS `risk_merchant_function_config`;
CREATE TABLE `risk_merchant_function_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键。',
  `merchant_id` varchar(32) NOT NULL COMMENT '商户号。',
  `module_type` varchar(32) NOT NULL COMMENT '风控模块类型：WHITE、BLACK、RULE。',
  `function_code` varchar(64) NOT NULL COMMENT '稳定风控功能编码。',
  `enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '商户是否开通该可选功能：0 否，1 是。',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '软删除标记：0 正常，1 删除。',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人。',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '最后修改人。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_risk_merchant_function` (`merchant_id`,`module_type`,`function_code`,`deleted`),
  KEY `idx_risk_merchant_function_enabled` (`merchant_id`,`enabled`,`deleted`,`module_type`,`function_code`),
  CONSTRAINT `chk_risk_merchant_function_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_risk_merchant_function_enabled` CHECK ((`enabled` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户可选风控功能开通状态；平台全局 AML 和监管规则不受该表控制。';

-- ----------------------------
-- Table structure for risk_merchant_limit_reservation
-- ----------------------------
DROP TABLE IF EXISTS `risk_merchant_limit_reservation`;
CREATE TABLE `risk_merchant_limit_reservation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `transaction_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '支付平台交易号',
  `risk_record_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '首次创建预占的风控评估流水号',
  `merchant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商户号',
  `rule_id` bigint NOT NULL COMMENT '商户累计限额规则 ID',
  `limit_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'DAILY/WEEKLY/MONTHLY',
  `currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ISO 4217 币种',
  `period_bucket` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '周期桶，例如 20260730',
  `period_begin_time` datetime(3) NOT NULL COMMENT '周期开始时间',
  `period_end_time` datetime(3) NOT NULL COMMENT '周期结束时间',
  `amount_units` bigint NOT NULL COMMENT '六位小数整数单位的预占金额',
  `counter_mode` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '预占时 LEGACY/SHADOW/CLUSTER_SAFE',
  `reservation_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PREPARING/RESERVED/CONFIRMED/CANCELLED',
  `cancel_reason` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '取消原因摘要',
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
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='3DS规则管理表';

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
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易频率限定规则表';

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
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户交易限额规则表';

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
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户来源网址限定规则表';

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
-- Table structure for risk_trade_black_batch
-- ----------------------------
DROP TABLE IF EXISTS `risk_trade_black_batch`;
CREATE TABLE `risk_trade_black_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_no` varchar(64) NOT NULL COMMENT '操作批次号',
  `request_id` varchar(64) NOT NULL COMMENT '客户端请求幂等号',
  `request_fingerprint` char(64) DEFAULT NULL COMMENT '规范化请求摘要；历史批次允许为空，新批次必须由应用写入',
  `input_mode` varchar(16) NOT NULL COMMENT '录入方式：SINGLE、PASTE、FILE',
  `scope_type` varchar(16) NOT NULL COMMENT '目标作用域：GLOBAL、MERCHANT',
  `action_type` varchar(16) NOT NULL COMMENT '操作：ADD、RELEASE',
  `function_count` int NOT NULL DEFAULT '0' COMMENT '选择的黑名单功能数',
  `order_count` int NOT NULL DEFAULT '0' COMMENT '输入订单数',
  `status` varchar(24) NOT NULL COMMENT 'PROCESSING、SUCCESS、PARTIAL_SUCCESS、FAILED',
  `total_count` int NOT NULL DEFAULT '0' COMMENT '候选项总数',
  `success_count` int NOT NULL DEFAULT '0' COMMENT '实际变更成功数',
  `skipped_count` int NOT NULL DEFAULT '0' COMMENT '幂等跳过数',
  `failure_count` int NOT NULL DEFAULT '0' COMMENT '失败数',
  `operator_account_id` bigint DEFAULT NULL COMMENT '操作账号ID；历史批次允许为空，新批次必须由应用写入',
  `operator` varchar(64) DEFAULT NULL COMMENT '操作人',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '完成时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_black_batch_no` (`batch_no`),
  UNIQUE KEY `uk_trade_black_request_account` (`operator_account_id`,`request_id`),
  KEY `idx_trade_black_batch_time` (`create_time`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统交易加黑操作批次';

-- ----------------------------
-- Table structure for risk_trade_black_batch_item
-- ----------------------------
DROP TABLE IF EXISTS `risk_trade_black_batch_item`;
CREATE TABLE `risk_trade_black_batch_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_no` varchar(64) NOT NULL COMMENT '操作批次号',
  `candidate_key` varchar(64) NOT NULL COMMENT '服务端候选项稳定键',
  `row_no` int DEFAULT NULL COMMENT '来源文件或输入行号',
  `input_order_no` varchar(128) DEFAULT NULL COMMENT '输入订单号',
  `transaction_id` varchar(64) DEFAULT NULL COMMENT '解析到的系统交易号',
  `source_merchant_id` varchar(32) DEFAULT NULL COMMENT '交易所属商户',
  `merchant_order_no` varchar(64) DEFAULT NULL COMMENT '交易商户订单号',
  `function_code` varchar(64) NOT NULL COMMENT '黑名单功能编码',
  `function_name` varchar(128) NOT NULL COMMENT '黑名单功能名称',
  `source_field` varchar(64) DEFAULT NULL COMMENT '交易事实来源字段',
  `target_scope` varchar(16) NOT NULL COMMENT '目标作用域',
  `target_merchant_id` varchar(32) NOT NULL DEFAULT '' COMMENT '目标商户号',
  `match_value_masked` varchar(255) DEFAULT NULL COMMENT '脱敏展示值',
  `result_code` varchar(32) NOT NULL COMMENT 'ADDED、RELEASED、ALREADY_EXISTS、ALREADY_MISSING、FAILED',
  `result_message` varchar(500) DEFAULT NULL COMMENT '结果说明',
  `operator` varchar(64) DEFAULT NULL COMMENT '操作人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_black_batch_candidate` (`batch_no`,`candidate_key`,`deleted`),
  KEY `idx_trade_black_item_transaction` (`transaction_id`,`create_time`),
  KEY `idx_trade_black_item_result` (`batch_no`,`result_code`)
) ENGINE=InnoDB AUTO_INCREMENT=60 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统交易加黑候选项执行明细';

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
  `black_target_type` varchar(64) NOT NULL COMMENT '黑名单功能编码：cardNo、cardFingerprint、cardBin、cardholderName、issuerCountry、deviceFingerprint、phone、email、emailUsername、emailDomain、ip、billingAddress、billingZip、billingCountry、shippingAddress、shippingZip、shippingCountry、region',
  `black_target_value_masked` varchar(255) NOT NULL COMMENT '加黑对象脱敏展示值',
  `black_target_hash` varchar(128) DEFAULT NULL COMMENT '加黑对象哈希值，禁止保存完整敏感明文',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '录入来源/方式：MANUAL、BATCH、SYSTEM、SINGLE、PASTE、FILE',
  `action_type` varchar(32) NOT NULL DEFAULT 'ADD' COMMENT '动作类型：ADD、RELEASE',
  `action_reason` varchar(500) DEFAULT NULL COMMENT '操作原因',
  `batch_no` varchar(64) DEFAULT NULL COMMENT '系统交易加黑操作批次号，历史或手工记录允许为空',
  `candidate_key` varchar(64) DEFAULT NULL COMMENT '服务端候选项稳定键，禁止保存敏感明文',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0已解除，1已加黑',
  `operator` varchar(64) DEFAULT NULL COMMENT '操作人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
  PRIMARY KEY (`id`),
  KEY `idx_trade_black_order` (`merchant_order_no`,`payment_order_no`,`deleted`),
  KEY `idx_trade_black_target` (`black_target_type`,`black_target_hash`,`status`,`deleted`),
  KEY `idx_trade_black_merchant_time` (`merchant_id`,`create_time`),
  KEY `idx_trade_black_update_time` (`update_time`,`id`),
  KEY `idx_trade_black_batch_candidate` (`batch_no`,`candidate_key`)
) ENGINE=InnoDB AUTO_INCREMENT=51 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统交易加黑记录表';

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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡BIN区间白名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡指纹白名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡号白名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Customer ID白名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备指纹白名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮箱地址白名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮箱域名白名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IP地址白名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发卡行国家地区白名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户白名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='手机号白名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易国家地区白名单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=5722 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全拦截事件';

-- ----------------------------
-- Table structure for settlement_batch
-- ----------------------------
DROP TABLE IF EXISTS `settlement_batch`;
CREATE TABLE `settlement_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `settlement_batch_no` varchar(19) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '批次号，格式SByyyyMMdd-NNNNNNNN',
  `create_request_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调度或人工创建请求幂等键',
  `business_date` date NOT NULL COMMENT '结算业务日期，禁止从批次号解析',
  `business_time_zone` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '结算日历业务时区',
  `daily_sequence` int NOT NULL COMMENT '当日数据库序号，1至99999999',
  `merchant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '平台商户号',
  `settlement_profile_id` bigint NOT NULL COMMENT '冻结的商户结算配置ID',
  `settlement_account_id` bigint NOT NULL COMMENT '目标结算资金账户ID',
  `target_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '本批目标结算币种',
  `target_currency_exponent` tinyint NOT NULL COMMENT '目标币种ISO小数位',
  `batch_type` varchar(24) NOT NULL DEFAULT 'REGULAR' COMMENT 'REGULARã€RESERVE_RELEASEã€REVERSALã€ADJUSTMENT',
  `original_batch_no` varchar(19) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '冲正或调整引用的原批次号',
  `review_order_no` varchar(19) DEFAULT NULL,
  `create_mode` varchar(24) NOT NULL DEFAULT 'AUTO',
  `cutoff_begin_time` datetime(3) NOT NULL COMMENT '候选窗口起始时间，闭区间',
  `cutoff_end_time` datetime(3) NOT NULL COMMENT '候选窗口结束时间，开区间',
  `batch_status` varchar(24) NOT NULL DEFAULT 'CREATED' COMMENT 'CREATEDã€CLAIMINGã€CLAIMEDã€RATE_LOCKEDã€CALCULATINGã€CALCULATEDã€POSTINGã€POSTEDã€FAILED_RETRYABLEã€MANUAL_REVIEWã€CANCELLEDã€REVERSINGã€REVERSED',
  `candidate_count` int NOT NULL DEFAULT '0' COMMENT '本批已认领候选数',
  `projectable_candidate_count` int NOT NULL DEFAULT '0',
  `result_fingerprint` char(64) DEFAULT NULL,
  `maker_account_id` bigint DEFAULT NULL,
  `maker_account_name` varchar(128) DEFAULT NULL,
  `maker_role_snapshot` varchar(1000) DEFAULT NULL,
  `maker_client_ip` varchar(64) DEFAULT NULL,
  `maker_user_agent` varchar(500) DEFAULT NULL,
  `maker_reason` varchar(400) DEFAULT NULL,
  `maker_time` datetime(3) DEFAULT NULL,
  `checker_account_id` bigint DEFAULT NULL,
  `checker_account_name` varchar(128) DEFAULT NULL,
  `checker_role_snapshot` varchar(1000) DEFAULT NULL,
  `checker_client_ip` varchar(64) DEFAULT NULL,
  `checker_user_agent` varchar(500) DEFAULT NULL,
  `checker_comment` varchar(400) DEFAULT NULL,
  `checker_time` datetime(3) DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '批次阶段重试次数',
  `processing_owner` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '当前处理租约所有者',
  `processing_deadline` datetime(3) DEFAULT NULL COMMENT '处理租约截止时间',
  `last_failure_stage` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最近失败阶段',
  `last_failure_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最近失败码',
  `last_failure_message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最近失败摘要，不保存敏感正文',
  `rate_locked_time` datetime(3) DEFAULT NULL COMMENT '汇率矩阵完整锁定时间',
  `calculated_time` datetime(3) DEFAULT NULL COMMENT '结果和汇总计算完成时间，不代表余额入账',
  `posted_time` datetime(3) DEFAULT NULL COMMENT '资金和批次结果提交时间',
  `cancelled_time` datetime(3) DEFAULT NULL COMMENT '入账前取消时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '批次状态CAS版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_batch_no` (`settlement_batch_no`),
  UNIQUE KEY `uk_settlement_create_request` (`create_request_key`),
  UNIQUE KEY `uk_settlement_business_sequence` (`business_date`,`daily_sequence`),
  UNIQUE KEY `uk_settlement_single_reversal` (`original_batch_no`,`batch_type`),
  KEY `idx_settlement_merchant_date` (`merchant_id`,`business_date`,`batch_status`,`id`),
  KEY `idx_settlement_status_lease` (`batch_status`,`processing_deadline`,`id`),
  KEY `idx_settlement_original_batch` (`original_batch_no`,`batch_type`,`id`),
  KEY `idx_settlement_admin_date` (`business_date`,`id`),
  KEY `idx_settlement_admin_merchant_date` (`merchant_id`,`business_date`,`id`),
  KEY `idx_settlement_admin_status_date` (`batch_status`,`business_date`,`id`),
  KEY `idx_settlement_batch_review` (`review_order_no`,`batch_status`,`id`),
  CONSTRAINT `chk_settlement_batch_enum` CHECK (((`batch_type` in (_utf8mb4'REGULAR',_utf8mb4'RESERVE_RELEASE',_utf8mb4'REVERSAL',_utf8mb4'ADJUSTMENT')) and (`batch_status` in (_utf8mb4'CREATED',_utf8mb4'CLAIMING',_utf8mb4'CLAIMED',_utf8mb4'RATE_LOCKED',_utf8mb4'CALCULATING',_utf8mb4'CALCULATED',_utf8mb4'POSTING',_utf8mb4'POSTED',_utf8mb4'FAILED_RETRYABLE',_utf8mb4'MANUAL_REVIEW',_utf8mb4'CANCELLED',_utf8mb4'REVERSING',_utf8mb4'REVERSED')) and (((`batch_type` = _utf8mb4'REVERSAL') and (`original_batch_no` is not null)) or ((`batch_type` in (_utf8mb4'REGULAR',_utf8mb4'RESERVE_RELEASE',_utf8mb4'ADJUSTMENT')) and (`original_batch_no` is null))))),
  CONSTRAINT `chk_settlement_batch_review_audit` CHECK (((`create_mode` in (_utf8mb4'AUTO',_utf8mb4'MANUAL_REVIEW')) and (`projectable_candidate_count` between 0 and `candidate_count`) and ((`create_mode` = _utf8mb4'AUTO') or ((`review_order_no` is not null) and (`result_fingerprint` is not null) and (`maker_account_id` is not null) and (`maker_account_name` is not null) and (`maker_role_snapshot` is not null) and (`maker_client_ip` is not null) and (`maker_user_agent` is not null) and (`maker_reason` is not null) and (`maker_time` is not null) and (`checker_account_id` is not null) and (`checker_account_name` is not null) and (`checker_role_snapshot` is not null) and (`checker_client_ip` is not null) and (`checker_user_agent` is not null) and (`checker_comment` is not null) and (`checker_time` is not null) and (`maker_account_id` <> `checker_account_id`))))),
  CONSTRAINT `chk_settlement_batch_value` CHECK (((`daily_sequence` between 1 and 99999999) and (`target_currency_exponent` between 0 and 8) and (`cutoff_end_time` > `cutoff_begin_time`) and (`candidate_count` >= 0) and (`retry_count` >= 0)))
) ENGINE=InnoDB AUTO_INCREMENT=173 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户单目标币种结算批次；批次号和创建请求双重幂等';

-- ----------------------------
-- Table structure for settlement_batch_cancellation_audit
-- ----------------------------
DROP TABLE IF EXISTS `settlement_batch_cancellation_audit`;
CREATE TABLE `settlement_batch_cancellation_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `settlement_batch_no` varchar(19) NOT NULL,
  `request_key` varchar(64) NOT NULL,
  `expected_version` bigint NOT NULL,
  `merchant_id` varchar(64) NOT NULL,
  `batch_status_before` varchar(24) NOT NULL,
  `released_candidate_count` int NOT NULL,
  `operator_account_id` bigint NOT NULL,
  `operator_account_name` varchar(128) NOT NULL,
  `operator_role_snapshot` varchar(1000) NOT NULL,
  `client_ip` varchar(64) NOT NULL,
  `user_agent` varchar(500) NOT NULL,
  `reason` varchar(400) NOT NULL,
  `operation_time` datetime(3) NOT NULL,
  `cancelled_time` datetime(3) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_batch_cancellation_batch` (`settlement_batch_no`),
  UNIQUE KEY `uk_settlement_batch_cancellation_request` (`request_key`),
  KEY `idx_settlement_batch_cancellation_merchant_time` (`merchant_id`,`cancelled_time`,`id`),
  CONSTRAINT `chk_settlement_batch_cancellation_value` CHECK (((`expected_version` >= 0) and (`released_candidate_count` >= 0) and ((`operator_account_id` > 0) or ((`operator_account_id` = 0) and (`operator_account_name` = _utf8mb4'service-settlement') and (`operator_role_snapshot` = _utf8mb4'SYSTEM'))) and (`batch_status_before` in (_utf8mb4'CREATED',_utf8mb4'CLAIMING',_utf8mb4'CLAIMED',_utf8mb4'RATE_LOCKED',_utf8mb4'CALCULATING',_utf8mb4'CALCULATED',_utf8mb4'FAILED_RETRYABLE',_utf8mb4'MANUAL_REVIEW'))))
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Immutable settlement batch pre-post cancellation audit';

-- ----------------------------
-- Table structure for settlement_batch_candidate
-- ----------------------------
DROP TABLE IF EXISTS `settlement_batch_candidate`;
CREATE TABLE `settlement_batch_candidate` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_candidate_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '批次候选关系业务号',
  `settlement_batch_no` varchar(19) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '结算批次号',
  `candidate_id` bigint NOT NULL COMMENT '结算候选ID',
  `source_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '候选来源类型快照',
  `source_business_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '候选来源业务ID快照',
  `source_revision` int NOT NULL COMMENT '候选来源修订号快照',
  `relation_status` varchar(24) NOT NULL DEFAULT 'CLAIMED' COMMENT 'CLAIMEDã€RELEASEDã€POSTEDã€MANUAL_REVIEW',
  `claimed_time` datetime(3) NOT NULL COMMENT '本批认领时间',
  `released_time` datetime(3) DEFAULT NULL COMMENT '入账前取消并释放候选的时间',
  `posted_time` datetime(3) DEFAULT NULL COMMENT '候选随批次入账时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '关系状态CAS版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_batch_candidate_no` (`batch_candidate_no`),
  UNIQUE KEY `uk_settlement_batch_candidate_pair` (`settlement_batch_no`,`candidate_id`),
  KEY `idx_settlement_batch_candidate_id` (`candidate_id`,`relation_status`,`id`),
  KEY `idx_settlement_batch_candidate_source` (`source_type`,`source_business_id`,`source_revision`,`id`),
  CONSTRAINT `chk_settlement_batch_candidate_value` CHECK (((`source_revision` >= 1) and (`relation_status` in (_utf8mb4'CLAIMED',_utf8mb4'RELEASED',_utf8mb4'POSTED',_utf8mb4'MANUAL_REVIEW')) and ((`relation_status` <> _utf8mb4'RELEASED') or (`released_time` is not null)) and ((`relation_status` <> _utf8mb4'POSTED') or (`posted_time` is not null))))
) ENGINE=InnoDB AUTO_INCREMENT=132556 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='批次认领候选的不可删除审计关系；取消后保留RELEASED历史，候选可被新批次重新认领';

-- ----------------------------
-- Table structure for settlement_batch_daily_sequence
-- ----------------------------
DROP TABLE IF EXISTS `settlement_batch_daily_sequence`;
CREATE TABLE `settlement_batch_daily_sequence` (
  `business_date` date NOT NULL COMMENT '独立业务日期；业务逻辑禁止解析批次号取得',
  `current_sequence` int NOT NULL DEFAULT '0' COMMENT '当日已分配最大序号，范围0至99999999',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '审计版本；分配仍必须SELECT FOR UPDATE',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`business_date`),
  CONSTRAINT `chk_settlement_daily_sequence_value` CHECK (((`current_sequence` >= 0) and (`current_sequence` <= 99999999)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算批次号数据库日序列；允许空洞，禁止回收和Redis/JVM单独发号';

-- ----------------------------
-- Table structure for settlement_batch_rate
-- ----------------------------
DROP TABLE IF EXISTS `settlement_batch_rate`;
CREATE TABLE `settlement_batch_rate` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `settlement_batch_no` varchar(19) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属结算批次号',
  `review_rate_id` bigint DEFAULT NULL,
  `source_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '源币种',
  `target_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标结算币种',
  `rate_type` varchar(24) NOT NULL DEFAULT 'SETTLEMENT' COMMENT 'SETTLEMENTã€RESERVE_RELEASE',
  `direct_rate` decimal(24,12) NOT NULL COMMENT '1单位源币种兑换的目标币种数量',
  `source_currency_exponent` tinyint NOT NULL COMMENT '源币种ISO小数位',
  `target_currency_exponent` tinyint NOT NULL COMMENT '目标币种ISO小数位',
  `rate_source` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '汇率来源；同币种为SYSTEM_IDENTITY',
  `quote_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '外部报价或内部业务汇率记录ID',
  `source_quote_direction` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DIRECT' COMMENT '原报价DIRECT或INVERSE',
  `effective_time` datetime(3) NOT NULL COMMENT '汇率生效时间',
  `locked_time` datetime(3) NOT NULL COMMENT '批次锁定时间',
  `locked_by` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '锁定服务实例或操作人',
  `rate_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'LOCKED' COMMENT 'LOCKED；插入后不可更新',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_batch_currency_rate` (`settlement_batch_no`,`source_currency`,`target_currency`,`rate_type`),
  UNIQUE KEY `uk_settlement_batch_review_rate` (`settlement_batch_no`,`review_rate_id`),
  KEY `idx_settlement_rate_quote` (`rate_source`,`quote_id`,`effective_time`,`id`),
  CONSTRAINT `chk_settlement_batch_rate_value` CHECK (((`direct_rate` > 0) and (`source_currency_exponent` between 0 and 8) and (`target_currency_exponent` between 0 and 8) and (`source_quote_direction` in (_utf8mb4'DIRECT',_utf8mb4'INVERSE')) and (`rate_type` in (_utf8mb4'SETTLEMENT',_utf8mb4'RESERVE_RELEASE')) and (`rate_status` = _utf8mb4'LOCKED') and ((`source_currency` <> `target_currency`) or ((`direct_rate` = 1) and (`rate_source` = _utf8mb4'SYSTEM_IDENTITY')))))
) ENGINE=InnoDB AUTO_INCREMENT=528 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算批次不可变汇率矩阵；同一批次同一币种对和rate type仅一条';

-- ----------------------------
-- Table structure for settlement_batch_recovery_audit
-- ----------------------------
DROP TABLE IF EXISTS `settlement_batch_recovery_audit`;
CREATE TABLE `settlement_batch_recovery_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `settlement_batch_no` varchar(19) NOT NULL,
  `request_key` varchar(64) NOT NULL,
  `expected_version` bigint NOT NULL,
  `merchant_id` varchar(64) NOT NULL,
  `recovery_action` varchar(32) NOT NULL,
  `batch_status_before` varchar(24) NOT NULL,
  `failure_stage_before` varchar(32) NOT NULL,
  `failure_code_before` varchar(64) NOT NULL,
  `retry_count_before` int NOT NULL,
  `restored_candidate_count` int NOT NULL,
  `operator_account_id` bigint NOT NULL,
  `operator_account_name` varchar(128) NOT NULL,
  `operator_role_snapshot` varchar(1000) NOT NULL,
  `client_ip` varchar(64) NOT NULL,
  `user_agent` varchar(500) NOT NULL,
  `reason` varchar(400) NOT NULL,
  `operation_time` datetime(3) NOT NULL,
  `recovered_time` datetime(3) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_batch_recovery_request` (`request_key`),
  KEY `idx_settlement_batch_recovery_batch_time` (`settlement_batch_no`,`recovered_time`,`id`),
  KEY `idx_settlement_batch_recovery_merchant_time` (`merchant_id`,`recovered_time`,`id`),
  CONSTRAINT `chk_settlement_batch_recovery_value` CHECK (((`expected_version` >= 0) and (`retry_count_before` >= 0) and (`restored_candidate_count` > 0) and (`operator_account_id` > 0) and (`recovery_action` = _utf8mb4'RETRY_RATE_LOCKING') and (`batch_status_before` = _utf8mb4'MANUAL_REVIEW') and (`failure_stage_before` = _utf8mb4'RATE_LOCKING') and (`failure_code_before` = _utf8mb4'SETTLEMENT_RETRY_EXHAUSTED')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Immutable audit for manually recovering exhausted settlement rate locking';

-- ----------------------------
-- Table structure for settlement_calendar_year
-- ----------------------------
DROP TABLE IF EXISTS `settlement_calendar_year`;
CREATE TABLE `settlement_calendar_year` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `calendar_year` int NOT NULL COMMENT '自然年',
  `region_code` varchar(32) NOT NULL DEFAULT 'CN_MAINLAND' COMMENT '地区编码，中国大陆全局日历',
  `time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '系统日历时区',
  `year_status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '年度状态：DRAFT、ACTIVE',
  `total_days` int NOT NULL COMMENT '年度自然日总数',
  `confirmed_by` varchar(128) DEFAULT NULL COMMENT '确认人名称快照',
  `confirmed_time` datetime(3) DEFAULT NULL COMMENT '确认时间',
  `create_by` varchar(128) NOT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(128) NOT NULL COMMENT '修改人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_calendar_year` (`calendar_year`,`region_code`,`deleted`),
  KEY `idx_settlement_calendar_status` (`year_status`,`calendar_year`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全局中国大陆结算日历年度表';

-- ----------------------------
-- Table structure for settlement_candidate
-- ----------------------------
DROP TABLE IF EXISTS `settlement_candidate`;
CREATE TABLE `settlement_candidate` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `candidate_no` varchar(64) NOT NULL COMMENT '候选业务号',
  `source_type` varchar(24) NOT NULL COMMENT 'CLEARING_REVISION、RESERVE_RELEASE、ADJUSTMENT',
  `source_business_id` varchar(64) NOT NULL COMMENT 'finance_state_id、reserve_state_id或调整业务号',
  `source_revision` int NOT NULL COMMENT '清分修订号或来源版本，从1递增',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '来源动作交易号',
  `source_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '来源季度精确路由时间',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `settlement_profile_id` bigint DEFAULT NULL COMMENT '冻结的结算配置ID；影子候选在结算模块上线前为空',
  `target_currency` char(3) NOT NULL COMMENT '目标结算币种',
  `target_currency_exponent` tinyint NOT NULL COMMENT '目标结算币种ISO小数位',
  `settlement_eligible_date` date NOT NULL COMMENT '最早可认领业务日期',
  `candidate_status` varchar(24) NOT NULL DEFAULT 'READY' COMMENT 'READY、REPLAY_HOLD、SUPERSEDED、CLAIMED、POSTED、MANUAL_REVIEW、CANCELLED',
  `shadow_mode` tinyint NOT NULL DEFAULT '1' COMMENT '1=影子候选，任何结算扫描必须排除；0=可进入真实结算',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '当前独占本候选的批次号',
  `review_order_no` varchar(19) DEFAULT NULL,
  `review_locked_time` datetime(3) DEFAULT NULL,
  `claimed_time` datetime(3) DEFAULT NULL COMMENT '最近成功认领时间',
  `posted_time` datetime(3) DEFAULT NULL COMMENT '资金入账完成时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '认领和释放CAS版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_candidate_no` (`candidate_no`),
  UNIQUE KEY `uk_settlement_candidate_source` (`source_type`,`source_business_id`,`source_revision`),
  KEY `idx_settlement_candidate_pick` (`merchant_id`,`target_currency`,`shadow_mode`,`candidate_status`,`settlement_eligible_date`,`id`),
  KEY `idx_settlement_candidate_batch` (`settlement_batch_no`,`candidate_status`,`id`),
  KEY `idx_settlement_candidate_source_transaction` (`source_transaction_id`,`source_transaction_date_time`,`source_revision`,`id`),
  KEY `idx_settlement_candidate_review` (`review_order_no`,`candidate_status`,`id`),
  KEY `idx_settlement_candidate_manual_review` (`merchant_id`,`settlement_profile_id`,`target_currency`,`candidate_status`,`shadow_mode`,`settlement_eligible_date`,`create_time`,`id`),
  CONSTRAINT `chk_settlement_candidate_state` CHECK (((`source_type` in (_utf8mb4'CLEARING_REVISION',_utf8mb4'RESERVE_RELEASE',_utf8mb4'ADJUSTMENT')) and (`candidate_status` in (_utf8mb4'READY',_utf8mb4'REPLAY_HOLD',_utf8mb4'REVIEW_LOCKED',_utf8mb4'SUPERSEDED',_utf8mb4'CLAIMED',_utf8mb4'POSTED',_utf8mb4'MANUAL_REVIEW',_utf8mb4'CANCELLED')) and (((`candidate_status` in (_utf8mb4'READY',_utf8mb4'REPLAY_HOLD',_utf8mb4'SUPERSEDED')) and (`settlement_batch_no` is null) and (`review_order_no` is null)) or ((`candidate_status` = _utf8mb4'REVIEW_LOCKED') and (`settlement_batch_no` is null) and (`review_order_no` is not null) and (`review_locked_time` is not null)) or ((`candidate_status` in (_utf8mb4'CLAIMED',_utf8mb4'POSTED',_utf8mb4'MANUAL_REVIEW',_utf8mb4'CANCELLED')) and (`settlement_batch_no` is not null))))),
  CONSTRAINT `chk_settlement_candidate_value` CHECK (((`source_revision` >= 1) and (`target_currency_exponent` between 0 and 8) and (`shadow_mode` in (0,1)) and (((`shadow_mode` = 1) and (`settlement_profile_id` is null)) or ((`shadow_mode` = 0) and (`settlement_profile_id` is not null)))))
) ENGINE=InnoDB AUTO_INCREMENT=216009 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='清分修订级结算候选；来源唯一加状态/version CAS防止跨批重复归属';

-- ----------------------------
-- Table structure for settlement_candidate_dependency
-- ----------------------------
DROP TABLE IF EXISTS `settlement_candidate_dependency`;
CREATE TABLE `settlement_candidate_dependency` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `candidate_id` bigint NOT NULL COMMENT '当前退款、冲正或后续动作候选ID',
  `depends_on_candidate_id` bigint NOT NULL COMMENT '必须先入账或同批先处理的源候选ID',
  `dependency_type` varchar(32) NOT NULL COMMENT 'SOURCE_FINANCIAL_POSTEDã€RESERVE_HOLD_POSTEDã€SOURCE_FEE_RESULT_POSTED',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_candidate_dependency` (`candidate_id`,`depends_on_candidate_id`,`dependency_type`),
  KEY `idx_settlement_dependency_source` (`depends_on_candidate_id`,`candidate_id`,`id`),
  CONSTRAINT `chk_settlement_candidate_dependency` CHECK (((`candidate_id` <> `depends_on_candidate_id`) and (`dependency_type` in (_utf8mb4'SOURCE_FINANCIAL_POSTED',_utf8mb4'RESERVE_HOLD_POSTED',_utf8mb4'SOURCE_FEE_RESULT_POSTED'))))
) ENGINE=InnoDB AUTO_INCREMENT=44482 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算候选不可变依赖图；依赖须已POSTED或与当前候选同批按拓扑顺序处理';

-- ----------------------------
-- Table structure for settlement_event_outbox
-- ----------------------------
DROP TABLE IF EXISTS `settlement_event_outbox`;
CREATE TABLE `settlement_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_no` varchar(96) NOT NULL COMMENT '结算事件全局幂等号',
  `settlement_batch_no` varchar(19) NOT NULL COMMENT '来源结算或冲正批次号',
  `candidate_id` bigint DEFAULT NULL COMMENT '来源真实交易候选主键',
  `rate_usage_id` bigint DEFAULT NULL COMMENT '来源结算批次汇率行ID',
  `topic` varchar(128) NOT NULL COMMENT '冻结RocketMQ Topic',
  `tag` varchar(64) NOT NULL COMMENT '冻结RocketMQ Tag',
  `message_key` varchar(128) NOT NULL COMMENT 'MQ消息幂等键',
  `message_group` varchar(64) NOT NULL COMMENT 'operationId FIFO分组键',
  `payload_json` json NOT NULL COMMENT '不含卡和账单敏感数据的冻结消息JSON',
  `event_status` varchar(16) NOT NULL DEFAULT 'INIT' COMMENT 'INIT、PROCESSING、FAILED、SENT',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '发送失败次数',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下次发送时间',
  `processing_owner` varchar(128) DEFAULT NULL COMMENT '发送租约所有者',
  `processing_deadline` datetime(3) DEFAULT NULL COMMENT '发送租约截止时间',
  `sent_time` datetime(3) DEFAULT NULL COMMENT '最近成功发送时间',
  `last_failure_code` varchar(64) DEFAULT NULL COMMENT '最近发送失败类型',
  `version` bigint NOT NULL DEFAULT '0' COMMENT 'Outbox状态CAS版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_event_no` (`event_no`),
  UNIQUE KEY `uk_settlement_event_candidate` (`settlement_batch_no`,`candidate_id`,`tag`),
  UNIQUE KEY `uk_settlement_event_rate_usage` (`settlement_batch_no`,`rate_usage_id`,`tag`),
  KEY `idx_settlement_event_due` (`event_status`,`next_retry_time`,`id`),
  KEY `idx_settlement_event_lease` (`event_status`,`processing_deadline`,`id`),
  CONSTRAINT `chk_settlement_event_value` CHECK (((`retry_count` >= 0) and (`event_status` in (_utf8mb4'INIT',_utf8mb4'PROCESSING',_utf8mb4'FAILED',_utf8mb4'SENT')) and (((`event_status` = _utf8mb4'PROCESSING') and (`processing_owner` is not null) and (`processing_deadline` is not null)) or (`event_status` <> _utf8mb4'PROCESSING')) and (((`event_status` = _utf8mb4'SENT') and (`sent_time` is not null)) or (`event_status` <> _utf8mb4'SENT'))))
) ENGINE=InnoDB AUTO_INCREMENT=72431 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算完成和冲正完成交易级FIFO消息可靠Outbox';

-- ----------------------------
-- Table structure for settlement_holiday_calendar
-- ----------------------------
DROP TABLE IF EXISTS `settlement_holiday_calendar`;
CREATE TABLE `settlement_holiday_calendar` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `calendar_year_id` bigint NOT NULL COMMENT '日历年度ID',
  `calendar_date` date NOT NULL COMMENT '自然日期',
  `day_of_week` tinyint NOT NULL COMMENT '星期一至星期日对应1至7',
  `day_type` varchar(16) NOT NULL COMMENT '日期类型：WORKDAY、HOLIDAY',
  `holiday_name` varchar(128) DEFAULT NULL COMMENT '法定节假日或休息日名称',
  `statutory_holiday` tinyint NOT NULL DEFAULT '0' COMMENT '是否法定节假日',
  `adjusted_workday` tinyint NOT NULL DEFAULT '0' COMMENT '是否调休工作日',
  `data_source` varchar(32) NOT NULL COMMENT '来源：SYSTEM_DEFAULT、MANUAL、IMPORT',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(128) NOT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(128) NOT NULL COMMENT '修改人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_calendar_date` (`calendar_date`,`deleted`),
  KEY `idx_settlement_calendar_type_date` (`day_type`,`calendar_date`,`deleted`),
  KEY `idx_settlement_calendar_year_date` (`calendar_year_id`,`deleted`,`calendar_date`)
) ENGINE=InnoDB AUTO_INCREMENT=731 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全局中国大陆结算节假日日历明细表';

-- ----------------------------
-- Table structure for settlement_manual_review_task
-- ----------------------------
DROP TABLE IF EXISTS `settlement_manual_review_task`;
CREATE TABLE `settlement_manual_review_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_no` varchar(40) NOT NULL,
  `request_key` varchar(128) NOT NULL,
  `submit_request_key` varchar(128) DEFAULT NULL,
  `review_order_no` varchar(19) NOT NULL,
  `review_type` varchar(24) NOT NULL,
  `merchant_id` varchar(64) NOT NULL,
  `settlement_profile_id` bigint NOT NULL,
  `settlement_account_id` bigint NOT NULL,
  `target_currency` char(3) NOT NULL,
  `target_currency_exponent` tinyint NOT NULL,
  `payment_type` varchar(64) DEFAULT NULL,
  `payment_method` varchar(64) DEFAULT NULL,
  `business_date` date NOT NULL,
  `business_time_zone` varchar(64) NOT NULL,
  `cutoff_begin_time` datetime(3) NOT NULL,
  `cutoff_end_time` datetime(3) NOT NULL,
  `snapshot_max_candidate_id` bigint NOT NULL,
  `expected_candidate_count` int NOT NULL,
  `processed_candidate_count` int NOT NULL DEFAULT '0',
  `locked_candidate_count` int NOT NULL DEFAULT '0',
  `last_candidate_id` bigint NOT NULL DEFAULT '0',
  `initial_delay_unit` char(1) DEFAULT NULL,
  `initial_delay_days` int DEFAULT NULL,
  `regular_delay_days` int DEFAULT NULL,
  `settlement_frequency` varchar(16) DEFAULT NULL,
  `frequency_day` int DEFAULT NULL,
  `preview_json` json NOT NULL,
  `task_status` varchar(24) NOT NULL,
  `submitted_by_account_id` bigint NOT NULL,
  `submitted_by_account_name` varchar(128) NOT NULL,
  `submitted_role_snapshot` varchar(1000) NOT NULL,
  `submit_client_ip` varchar(64) NOT NULL,
  `submit_user_agent` varchar(500) NOT NULL,
  `submit_reason` varchar(400) NOT NULL,
  `operation_time` datetime(3) NOT NULL,
  `processing_owner` varchar(128) DEFAULT NULL,
  `processing_deadline` datetime(3) DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_time` datetime(3) NOT NULL,
  `last_failure_code` varchar(64) DEFAULT NULL,
  `last_failure_message` varchar(500) DEFAULT NULL,
  `started_time` datetime(3) DEFAULT NULL,
  `completed_time` datetime(3) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `active_profile_id` bigint GENERATED ALWAYS AS ((case when (`task_status` in (_utf8mb4'PREVIEWED',_utf8mb4'QUEUED',_utf8mb4'PROCESSING',_utf8mb4'FINALIZING',_utf8mb4'CANCELLING')) then `settlement_profile_id` else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_manual_review_task_no` (`task_no`),
  UNIQUE KEY `uk_manual_review_task_request` (`request_key`),
  UNIQUE KEY `uk_manual_review_task_review` (`review_order_no`),
  UNIQUE KEY `uk_manual_review_task_submit_request` (`submit_request_key`),
  UNIQUE KEY `uk_manual_review_task_active_profile` (`active_profile_id`),
  KEY `idx_manual_review_task_due` (`task_status`,`next_retry_time`,`id`),
  KEY `idx_manual_review_task_merchant` (`merchant_id`,`task_status`,`create_time`,`id`),
  CONSTRAINT `chk_manual_review_task_state` CHECK ((`task_status` in (_utf8mb4'PREVIEWED',_utf8mb4'QUEUED',_utf8mb4'PROCESSING',_utf8mb4'FINALIZING',_utf8mb4'COMPLETED',_utf8mb4'FAILED',_utf8mb4'CANCELLING',_utf8mb4'CANCELLED'))),
  CONSTRAINT `chk_manual_review_task_value` CHECK (((`review_type` in (_latin1'REGULAR',_latin1'RESERVE_RELEASE')) and (`target_currency_exponent` between 0 and 8) and (`cutoff_end_time` > `cutoff_begin_time`) and (`snapshot_max_candidate_id` >= 0) and (`expected_candidate_count` >= 0) and (`processed_candidate_count` between 0 and `expected_candidate_count`) and (`locked_candidate_count` between 0 and `expected_candidate_count`) and (`last_candidate_id` between 0 and `snapshot_max_candidate_id`) and (`retry_count` >= 0) and (`version` >= 0) and (((`review_type` = _latin1'REGULAR') and (`initial_delay_unit` in (_latin1'T',_latin1'D')) and (`initial_delay_days` >= 1) and (`regular_delay_days` >= 1) and (`settlement_frequency` is not null)) or ((`review_type` = _latin1'RESERVE_RELEASE') and (`initial_delay_unit` is null) and (`initial_delay_days` is null) and (`regular_delay_days` is null) and (`settlement_frequency` is null) and (`frequency_day` is null)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Server-side frozen manual settlement review generation task';

-- ----------------------------
-- Table structure for settlement_principal_correction_item
-- ----------------------------
DROP TABLE IF EXISTS `settlement_principal_correction_item`;
CREATE TABLE `settlement_principal_correction_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `correction_item_no` varchar(36) NOT NULL COMMENT '逐笔调整号，SPCI加32位小写十六进制摘要',
  `correction_no` varchar(35) NOT NULL COMMENT '调整主单号',
  `original_result_item_id` bigint NOT NULL COMMENT '原不可变PRINCIPAL结算结果项ID',
  `original_projection_task_id` bigint NOT NULL COMMENT '原已完成结算投影任务ID',
  `original_projection_task_no` varchar(64) NOT NULL COMMENT '原结算投影任务号',
  `candidate_id` bigint NOT NULL COMMENT '原POSTED真实交易候选ID',
  `clearing_detail_no` varchar(64) NOT NULL COMMENT '原ACTIVE本金清分明细号',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台动作交易号',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易季度精确分片时间',
  `clearing_revision` int NOT NULL COMMENT '原清分修订号',
  `operation_id` varchar(64) NOT NULL COMMENT '平台交易生命周期号',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `direction` varchar(8) NOT NULL COMMENT '原本金商户视角CREDIT或DEBIT',
  `original_source_amount` decimal(24,8) NOT NULL COMMENT '原结算结果引用的错误本金金额',
  `original_source_currency` char(3) NOT NULL COMMENT '原结算结果引用的错误本金币种',
  `original_source_currency_exponent` tinyint NOT NULL COMMENT '原错误本金币种exponent',
  `label_amount` decimal(24,8) NOT NULL COMMENT '商户标签本金金额',
  `label_currency` char(3) NOT NULL COMMENT '商户标签本金币种',
  `label_currency_exponent` tinyint NOT NULL COMMENT '标签本金币种exponent',
  `original_locked_rate_id` bigint NOT NULL COMMENT '原历史结果实际引用的批次锁定汇率ID',
  `original_locked_direct_rate` decimal(24,12) NOT NULL COMMENT '原历史结果实际引用的direct rate',
  `label_locked_rate_id` bigint NOT NULL COMMENT '标签本金重算使用的批次锁定汇率ID',
  `label_locked_direct_rate` decimal(24,12) NOT NULL COMMENT '标签本金重算使用的direct rate',
  `target_currency` char(3) NOT NULL COMMENT '原批次目标结算币种',
  `target_currency_exponent` tinyint NOT NULL COMMENT '目标币种exponent',
  `rounding_mode` varchar(16) NOT NULL COMMENT '原结算结果冻结舍入模式',
  `original_target_amount` decimal(24,8) NOT NULL COMMENT '原本金目标币种结果',
  `expected_target_amount` decimal(24,8) NOT NULL COMMENT '标签本金按原汇率重算后的目标金额',
  `signed_delta_amount` decimal(24,8) NOT NULL COMMENT '商户余额视角有符号逐笔差额',
  `original_projection_amount` decimal(24,8) NOT NULL COMMENT '原动作完整结算投影金额',
  `corrected_projection_amount` decimal(24,8) NOT NULL COMMENT '加上本金差额后的动作结算投影金额',
  `settlement_date` date NOT NULL COMMENT '原结算业务日期',
  `projection_status` varchar(24) NOT NULL COMMENT 'WAITING_REVIEW、INIT、PROCESSING、COMPLETED、FAILED或CANCELLED',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '投影修正失败次数',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下次允许投影修正时间',
  `last_failure_code` varchar(64) DEFAULT NULL COMMENT '最近稳定失败码',
  `completed_time` datetime(3) DEFAULT NULL COMMENT '三层交易投影完成时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '投影任务状态CAS版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_principal_correction_item_no` (`correction_item_no`),
  UNIQUE KEY `uk_settlement_principal_correction_result` (`original_result_item_id`),
  UNIQUE KEY `uk_settlement_principal_correction_order_candidate` (`correction_no`,`candidate_id`),
  KEY `idx_settlement_principal_correction_item_order` (`correction_no`,`id`),
  KEY `idx_settlement_principal_correction_item_due` (`projection_status`,`next_retry_time`,`id`),
  KEY `idx_settlement_principal_correction_item_transaction` (`transaction_id`,`transaction_date_time`,`clearing_revision`,`id`),
  CONSTRAINT `chk_settlement_principal_correction_item_state` CHECK (((`projection_status` in (_utf8mb4'WAITING_REVIEW',_utf8mb4'INIT',_utf8mb4'PROCESSING',_utf8mb4'COMPLETED',_utf8mb4'FAILED',_utf8mb4'CANCELLED')) and (((`projection_status` = _utf8mb4'WAITING_REVIEW') and (`next_retry_time` is null) and (`completed_time` is null)) or ((`projection_status` in (_utf8mb4'INIT',_utf8mb4'PROCESSING',_utf8mb4'FAILED')) and (`next_retry_time` is not null) and (`completed_time` is null)) or ((`projection_status` = _utf8mb4'COMPLETED') and (`next_retry_time` is null) and (`completed_time` is not null)) or ((`projection_status` = _utf8mb4'CANCELLED') and (`next_retry_time` is null) and (`completed_time` is null))))),
  CONSTRAINT `chk_settlement_principal_correction_item_value` CHECK (((`original_result_item_id` > 0) and (`original_projection_task_id` > 0) and (`candidate_id` > 0) and (`clearing_revision` > 0) and (`direction` in (_utf8mb4'CREDIT',_utf8mb4'DEBIT')) and (`original_source_amount` >= 0) and (`label_amount` >= 0) and regexp_like(`original_source_currency`,_utf8mb4'^[A-Z]{3}$',_utf8mb4'c') and regexp_like(`label_currency`,_utf8mb4'^[A-Z]{3}$',_utf8mb4'c') and regexp_like(`target_currency`,_utf8mb4'^[A-Z]{3}$',_utf8mb4'c') and (`original_source_currency_exponent` between 0 and 8) and (`label_currency_exponent` between 0 and 8) and (`original_locked_rate_id` > 0) and (`original_locked_direct_rate` > 0) and (`label_locked_rate_id` > 0) and (`label_locked_direct_rate` > 0) and (`target_currency_exponent` between 0 and 8) and (`rounding_mode` in (_utf8mb4'HALF_UP',_utf8mb4'HALF_EVEN',_utf8mb4'DOWN')) and (`original_target_amount` >= 0) and (`expected_target_amount` >= 0) and (`retry_count` >= 0) and (`version` >= 0)))
) ENGINE=InnoDB AUTO_INCREMENT=3320 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='历史标签本金逐笔冻结事实及可靠三层交易投影修正任务';

-- ----------------------------
-- Table structure for settlement_principal_correction_order
-- ----------------------------
DROP TABLE IF EXISTS `settlement_principal_correction_order`;
CREATE TABLE `settlement_principal_correction_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `correction_no` varchar(35) NOT NULL COMMENT '调整单号，SPC加32位小写十六进制摘要',
  `submit_request_key` varchar(128) NOT NULL COMMENT 'Maker提交全局幂等键',
  `original_batch_no` varchar(19) NOT NULL COMMENT '原POSTED REGULAR结算批次号',
  `merchant_id` varchar(64) NOT NULL COMMENT '原批次平台商户号',
  `settlement_account_id` bigint NOT NULL COMMENT '原批次结算资金账户ID',
  `target_currency` char(3) NOT NULL COMMENT '原批次目标结算币种',
  `target_currency_exponent` tinyint NOT NULL COMMENT '目标币种ISO exponent',
  `original_batch_version` bigint NOT NULL COMMENT '申请时冻结的原批次版本',
  `original_fund_ledger_id` bigint NOT NULL COMMENT '原批次不可变资金流水ID',
  `item_count` int NOT NULL COMMENT '冻结的异常本金结果项数量',
  `correction_direction` varchar(8) NOT NULL COMMENT '商户余额差额方向，CREDIT或DEBIT',
  `correction_amount` decimal(24,8) NOT NULL COMMENT '目标币种非负差额合计',
  `source_fingerprint` char(64) NOT NULL COMMENT '原批次、流水、逐笔来源和计算结果SHA-256',
  `correction_status` varchar(24) NOT NULL COMMENT 'PENDING_REVIEW、EXECUTED或REJECTED',
  `submitted_by_account_id` bigint NOT NULL COMMENT 'Maker管理账号ID',
  `submitted_by_account_name` varchar(128) NOT NULL COMMENT 'Maker账号名称快照',
  `submitted_role_snapshot` varchar(1000) NOT NULL COMMENT 'Maker角色权限快照',
  `submit_client_ip` varchar(64) NOT NULL COMMENT 'Maker客户端IP',
  `submit_user_agent` varchar(500) NOT NULL COMMENT 'Maker客户端User-Agent',
  `submit_reason` varchar(400) NOT NULL COMMENT '调整原因',
  `submitted_time` datetime(3) NOT NULL COMMENT 'Maker提交时间',
  `decided_by_account_id` bigint DEFAULT NULL COMMENT 'Checker管理账号ID',
  `decided_by_account_name` varchar(128) DEFAULT NULL COMMENT 'Checker账号名称快照',
  `decided_role_snapshot` varchar(1000) DEFAULT NULL COMMENT 'Checker角色权限快照',
  `decision_client_ip` varchar(64) DEFAULT NULL COMMENT 'Checker客户端IP',
  `decision_user_agent` varchar(500) DEFAULT NULL COMMENT 'Checker客户端User-Agent',
  `decision_action` varchar(16) DEFAULT NULL COMMENT 'APPROVE或REJECT',
  `decision_request_key` varchar(128) DEFAULT NULL COMMENT 'Checker决策全局幂等键',
  `decision_comment` varchar(400) DEFAULT NULL COMMENT 'Checker复核意见',
  `decision_time` datetime(3) DEFAULT NULL COMMENT 'Checker决策时间',
  `correction_ledger_id` bigint DEFAULT NULL COMMENT '非零差额对应的唯一新增资金流水ID',
  `projection_completed_count` int NOT NULL DEFAULT '0' COMMENT '已完成三层交易投影修正数量',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '调整单状态CAS版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_principal_correction_no` (`correction_no`),
  UNIQUE KEY `uk_settlement_principal_correction_submit` (`submit_request_key`),
  UNIQUE KEY `uk_settlement_principal_correction_batch` (`original_batch_no`),
  UNIQUE KEY `uk_settlement_principal_correction_decision` (`decision_request_key`),
  UNIQUE KEY `uk_settlement_principal_correction_ledger` (`correction_ledger_id`),
  KEY `idx_settlement_principal_correction_merchant` (`merchant_id`,`correction_status`,`submitted_time`,`id`),
  KEY `idx_settlement_principal_correction_status` (`correction_status`,`submitted_time`,`id`),
  CONSTRAINT `chk_settlement_principal_correction_state` CHECK (((`correction_status` in (_utf8mb4'PENDING_REVIEW',_utf8mb4'EXECUTED',_utf8mb4'REJECTED')) and (((`correction_status` = _utf8mb4'PENDING_REVIEW') and (`decided_by_account_id` is null) and (`decided_by_account_name` is null) and (`decided_role_snapshot` is null) and (`decision_client_ip` is null) and (`decision_user_agent` is null) and (`decision_action` is null) and (`decision_request_key` is null) and (`decision_comment` is null) and (`decision_time` is null) and (`correction_ledger_id` is null)) or ((`correction_status` = _utf8mb4'EXECUTED') and (`decided_by_account_id` is not null) and (`decided_by_account_name` is not null) and (`decided_role_snapshot` is not null) and (`decision_client_ip` is not null) and (`decision_user_agent` is not null) and (`decision_action` = _utf8mb4'APPROVE') and (`decision_request_key` is not null) and (`decision_comment` is not null) and (`decision_time` is not null) and (`submitted_by_account_id` <> `decided_by_account_id`) and (((`correction_amount` = 0) and (`correction_ledger_id` is null)) or ((`correction_amount` > 0) and (`correction_ledger_id` is not null)))) or ((`correction_status` = _utf8mb4'REJECTED') and (`decided_by_account_id` is not null) and (`decided_by_account_name` is not null) and (`decided_role_snapshot` is not null) and (`decision_client_ip` is not null) and (`decision_user_agent` is not null) and (`decision_action` = _utf8mb4'REJECT') and (`decision_request_key` is not null) and (`decision_comment` is not null) and (`decision_time` is not null) and (`correction_ledger_id` is null) and (`submitted_by_account_id` <> `decided_by_account_id`))))),
  CONSTRAINT `chk_settlement_principal_correction_value` CHECK ((regexp_like(`target_currency`,_utf8mb4'^[A-Z]{3}$',_utf8mb4'c') and (`target_currency_exponent` between 0 and 8) and (`original_batch_version` >= 0) and (`original_fund_ledger_id` > 0) and (`item_count` > 0) and (`correction_direction` in (_utf8mb4'CREDIT',_utf8mb4'DEBIT')) and (`correction_amount` >= 0) and (`projection_completed_count` between 0 and `item_count`) and (`version` >= 0) and ((`correction_amount` > 0) or (`correction_direction` = _utf8mb4'CREDIT'))))
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='已入账历史标签本金差额调整Maker-Checker主单；原资金事实保持不可变';

-- ----------------------------
-- Table structure for settlement_projection_task
-- ----------------------------
DROP TABLE IF EXISTS `settlement_projection_task`;
CREATE TABLE `settlement_projection_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_no` varchar(64) NOT NULL COMMENT '投影任务全局幂等号',
  `settlement_batch_no` varchar(19) NOT NULL COMMENT '结算或冲正批次号',
  `projection_action` varchar(16) NOT NULL DEFAULT 'SETTLE' COMMENT 'SETTLE、REVERSE',
  `original_batch_no` varchar(19) DEFAULT NULL COMMENT 'REVERSE 引用的原结算批次号',
  `candidate_id` bigint NOT NULL COMMENT '来源结算候选ID',
  `transaction_id` varchar(64) NOT NULL COMMENT '动作级交易号',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '动作季度精确路由时间',
  `clearing_revision` int NOT NULL COMMENT '被结算清分修订号',
  `operation_id` varchar(64) NOT NULL COMMENT '交易生命周期操作号和FIFO分组键',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `settlement_currency` char(3) NOT NULL COMMENT '批次目标结算币种',
  `settlement_amount` decimal(24,8) NOT NULL COMMENT '原动作有符号结算金额；SETTLE和REVERSE均保持原结算事实',
  `settlement_date` date NOT NULL COMMENT '原动作结算业务日期；REVERSE不得替换为冲正日期',
  `task_status` varchar(16) NOT NULL DEFAULT 'INIT' COMMENT 'INIT、PROCESSING、FAILED、COMPLETED',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '投影重试次数',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下次允许处理时间',
  `last_failure_code` varchar(64) DEFAULT NULL COMMENT '最近稳定失败码',
  `completed_time` datetime(3) DEFAULT NULL COMMENT '投影和Outbox完成时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '任务状态CAS版本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_projection_task_no` (`task_no`),
  UNIQUE KEY `uk_settlement_projection_batch_candidate` (`settlement_batch_no`,`candidate_id`),
  KEY `idx_settlement_projection_due` (`task_status`,`next_retry_time`,`id`),
  KEY `idx_settlement_projection_transaction` (`transaction_id`,`transaction_date_time`,`id`),
  CONSTRAINT `chk_settlement_projection_value` CHECK (((`clearing_revision` >= 1) and (`retry_count` >= 0) and (`projection_action` in (_utf8mb4'SETTLE',_utf8mb4'REVERSE')) and (`task_status` in (_utf8mb4'INIT',_utf8mb4'PROCESSING',_utf8mb4'FAILED',_utf8mb4'COMPLETED')) and (((`projection_action` = _utf8mb4'SETTLE') and (`original_batch_no` is null)) or ((`projection_action` = _utf8mb4'REVERSE') and (`original_batch_no` is not null))) and (((`task_status` = _utf8mb4'COMPLETED') and (`completed_time` is not null) and (`next_retry_time` is null)) or (`task_status` <> _utf8mb4'COMPLETED'))))
) ENGINE=InnoDB AUTO_INCREMENT=72074 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算后异步更新交易财务和动作查询投影的可靠任务';

-- ----------------------------
-- Table structure for settlement_result_item
-- ----------------------------
DROP TABLE IF EXISTS `settlement_result_item`;
CREATE TABLE `settlement_result_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `settlement_result_item_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '结算结果明细号',
  `settlement_batch_no` varchar(19) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属结算批次号',
  `candidate_id` bigint DEFAULT NULL COMMENT '来源结算候选ID；批次级NET_SETTLEMENT为空',
  `result_line_no` int NOT NULL COMMENT '候选或批次内稳定结果行号，从1递增',
  `merchant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '平台商户号',
  `settlement_account_id` bigint NOT NULL COMMENT '目标资金账户ID',
  `source_detail_type` varchar(24) NOT NULL COMMENT 'TRANSACTION_CLEARINGã€RESERVE_CLEARINGã€BATCH_LIMITã€BATCH_NET',
  `source_detail_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '来源清分明细号；批次限额调整可为空',
  `reversal_of_result_item_id` bigint DEFAULT NULL COMMENT '冲正时引用的原结算结果项ID',
  `source_transaction_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '来源动作交易号',
  `source_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '来源季度路由时间',
  `fee_group_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '费用组号；非费用为空',
  `result_item_type` varchar(32) NOT NULL COMMENT 'PRINCIPALã€FEE_COMPONENTã€FEE_GROUP_FINALã€RESERVE_HOLDã€RESERVE_RETURNã€RESERVE_RELEASEã€ADJUSTMENTã€REVERSALã€NET_SETTLEMENT',
  `result_role` varchar(24) NOT NULL COMMENT 'TRACEã€FINANCIAL_COMPONENTã€LEDGER_POSTING',
  `payment_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '支付类型快照',
  `payment_method` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '支付方式快照',
  `transaction_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '交易动作类型',
  `fee_category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '费用类别；非费用为空',
  `direction` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商户视角CREDIT或DEBIT',
  `source_amount` decimal(24,8) NOT NULL COMMENT '来源原币种非负金额',
  `source_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源原币种',
  `source_currency_exponent` tinyint NOT NULL COMMENT '来源币种ISO小数位',
  `settlement_batch_rate_id` bigint NOT NULL COMMENT '本行使用的批次汇率ID；同币种也引用恒等行',
  `unrounded_target_amount` decimal(48,20) NOT NULL COMMENT '原金额乘12位汇率后的目标币种未舍入中间值',
  `target_amount` decimal(24,8) NOT NULL COMMENT '按目标币种舍入后的非负金额',
  `target_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标结算币种',
  `target_currency_exponent` tinyint NOT NULL COMMENT '目标币种ISO小数位',
  `applied_limit` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONEã€MINIMUMã€MAXIMUM',
  `minimum_target_amount` decimal(48,20) DEFAULT NULL COMMENT '按批次汇率转换的最低费目标币种未舍入值',
  `maximum_target_amount` decimal(48,20) DEFAULT NULL COMMENT '按批次汇率转换的最高费目标币种未舍入值',
  `rounding_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标金额舍入模式',
  `formula_snapshot` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '批次换汇及费用限额公式快照',
  `ledger_idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '对应资金流水幂等键；非最终入账行可为空',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `ledger_posting_slot` tinyint GENERATED ALWAYS AS ((case when (`result_role` = _utf8mb4'LEDGER_POSTING') then 1 else NULL end)) STORED COMMENT '批次级最终入账唯一槽；非入账结果为空',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_result_item_no` (`settlement_result_item_no`),
  UNIQUE KEY `uk_settlement_result_candidate_line` (`settlement_batch_no`,`candidate_id`,`result_line_no`),
  UNIQUE KEY `uk_settlement_result_ledger_idempotency` (`ledger_idempotency_key`),
  UNIQUE KEY `uk_settlement_one_ledger_posting` (`settlement_batch_no`,`ledger_posting_slot`),
  KEY `idx_settlement_result_source` (`source_detail_type`,`source_detail_no`,`id`),
  KEY `idx_settlement_result_reversal` (`reversal_of_result_item_id`,`id`),
  KEY `idx_settlement_result_group` (`settlement_batch_no`,`fee_group_no`,`result_item_type`,`id`),
  KEY `idx_settlement_result_rate` (`settlement_batch_rate_id`,`id`),
  CONSTRAINT `chk_settlement_result_item_value` CHECK (((`result_line_no` >= 1) and (`source_amount` >= 0) and (`unrounded_target_amount` >= 0) and (`target_amount` >= 0) and (`source_currency_exponent` between 0 and 8) and (`target_currency_exponent` between 0 and 8) and (`direction` in (_utf8mb4'CREDIT',_utf8mb4'DEBIT')) and (`applied_limit` in (_utf8mb4'NONE',_utf8mb4'MINIMUM',_utf8mb4'MAXIMUM')) and (`rounding_mode` in (_utf8mb4'HALF_UP',_utf8mb4'HALF_EVEN',_utf8mb4'DOWN')) and (`source_detail_type` in (_utf8mb4'TRANSACTION_CLEARING',_utf8mb4'RESERVE_CLEARING',_utf8mb4'BATCH_LIMIT',_utf8mb4'BATCH_NET')) and (`result_item_type` in (_utf8mb4'PRINCIPAL',_utf8mb4'FEE_COMPONENT',_utf8mb4'FEE_GROUP_FINAL',_utf8mb4'RESERVE_HOLD',_utf8mb4'RESERVE_RETURN',_utf8mb4'RESERVE_RELEASE',_utf8mb4'ADJUSTMENT',_utf8mb4'REVERSAL',_utf8mb4'NET_SETTLEMENT')) and (`result_role` in (_utf8mb4'TRACE',_utf8mb4'FINANCIAL_COMPONENT',_utf8mb4'LEDGER_POSTING')) and (((`result_role` = _utf8mb4'LEDGER_POSTING') and (`result_item_type` = _utf8mb4'NET_SETTLEMENT') and (`candidate_id` is null) and (`ledger_idempotency_key` is not null)) or ((`result_role` <> _utf8mb4'LEDGER_POSTING') and (`result_item_type` <> _utf8mb4'NET_SETTLEMENT') and (`candidate_id` is not null) and (`ledger_idempotency_key` is null)))))
) ENGINE=InnoDB AUTO_INCREMENT=1037335 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算不可变结果明细；记录原币种、统一批次汇率、限额命中和目标币种结果';

-- ----------------------------
-- Table structure for settlement_result_summary
-- ----------------------------
DROP TABLE IF EXISTS `settlement_result_summary`;
CREATE TABLE `settlement_result_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `settlement_batch_no` varchar(19) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属结算批次号',
  `merchant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '平台商户号',
  `payment_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NONE' COMMENT '支付类型汇总维度',
  `payment_method` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NONE' COMMENT '支付方式汇总维度',
  `transaction_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NONE' COMMENT '交易类型汇总维度',
  `result_item_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '本金、费用或保证金结果类型',
  `fee_category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NONE' COMMENT '费用类别汇总维度',
  `direction` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商户视角CREDIT或DEBIT',
  `source_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源原币种',
  `target_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标结算币种',
  `transaction_count` bigint NOT NULL COMMENT '去重候选或交易动作笔数',
  `source_amount` decimal(24,8) NOT NULL COMMENT '同原币种金额合计',
  `target_amount` decimal(24,8) NOT NULL COMMENT '目标结算币种金额合计',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_result_summary_dim` (`settlement_batch_no`,`merchant_id`,`payment_type`,`payment_method`,`transaction_type`,`result_item_type`,`fee_category`,`direction`,`source_currency`,`target_currency`),
  CONSTRAINT `chk_settlement_result_summary_value` CHECK (((`transaction_count` >= 0) and (`source_amount` >= 0) and (`target_amount` >= 0) and (`direction` in (_utf8mb4'CREDIT',_utf8mb4'DEBIT'))))
) ENGINE=InnoDB AUTO_INCREMENT=14968 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='仅从FINANCIAL_COMPONENT结果行按支付类型方式、交易类型、费用类别和原币种形成的批次统计';

-- ----------------------------
-- Table structure for settlement_reversal_daily_sequence
-- ----------------------------
DROP TABLE IF EXISTS `settlement_reversal_daily_sequence`;
CREATE TABLE `settlement_reversal_daily_sequence` (
  `business_date` date NOT NULL,
  `current_sequence` int NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`business_date`),
  CONSTRAINT `chk_settlement_reversal_sequence` CHECK (((`current_sequence` between 0 and 99999999) and (`version` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Settlement reversal database daily sequence';

-- ----------------------------
-- Table structure for settlement_reversal_order
-- ----------------------------
DROP TABLE IF EXISTS `settlement_reversal_order`;
CREATE TABLE `settlement_reversal_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reversal_order_no` varchar(20) NOT NULL,
  `create_request_key` varchar(128) NOT NULL,
  `original_batch_no` varchar(19) NOT NULL,
  `reversal_batch_no` varchar(19) DEFAULT NULL,
  `merchant_id` varchar(64) NOT NULL,
  `settlement_account_id` bigint NOT NULL,
  `target_currency` char(3) NOT NULL,
  `target_currency_exponent` tinyint NOT NULL,
  `original_batch_version` bigint NOT NULL,
  `original_net_result_item_id` bigint NOT NULL,
  `original_fund_ledger_id` bigint NOT NULL,
  `net_direction` varchar(8) NOT NULL,
  `net_amount` decimal(24,8) NOT NULL,
  `source_fingerprint` char(64) NOT NULL,
  `reversal_status` varchar(24) NOT NULL,
  `submitted_by_account_id` bigint NOT NULL,
  `submitted_by_account_name` varchar(128) NOT NULL,
  `submitted_role_snapshot` varchar(1000) NOT NULL,
  `submit_client_ip` varchar(64) NOT NULL,
  `submit_user_agent` varchar(500) NOT NULL,
  `submit_reason` varchar(400) NOT NULL,
  `submitted_time` datetime(3) NOT NULL,
  `decided_by_account_id` bigint DEFAULT NULL,
  `decided_by_account_name` varchar(128) DEFAULT NULL,
  `decided_role_snapshot` varchar(1000) DEFAULT NULL,
  `decision_client_ip` varchar(64) DEFAULT NULL,
  `decision_user_agent` varchar(500) DEFAULT NULL,
  `decision_action` varchar(16) DEFAULT NULL,
  `decision_request_key` varchar(128) DEFAULT NULL,
  `decision_comment` varchar(400) DEFAULT NULL,
  `decision_time` datetime(3) DEFAULT NULL,
  `active_original_batch_no` varchar(19) GENERATED ALWAYS AS ((case when (`reversal_status` in (_utf8mb4'PENDING_APPROVAL',_utf8mb4'APPROVED')) then `original_batch_no` else NULL end)) STORED,
  `version` bigint NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_reversal_no` (`reversal_order_no`),
  UNIQUE KEY `uk_settlement_reversal_create_request` (`create_request_key`),
  UNIQUE KEY `uk_settlement_reversal_decision_request` (`decision_request_key`),
  UNIQUE KEY `uk_settlement_reversal_batch` (`reversal_batch_no`),
  UNIQUE KEY `uk_settlement_reversal_active_original` (`active_original_batch_no`),
  KEY `idx_settlement_reversal_original_status` (`original_batch_no`,`reversal_status`,`id`),
  KEY `idx_settlement_reversal_merchant_status` (`merchant_id`,`reversal_status`,`create_time`,`id`),
  KEY `idx_settlement_reversal_status_time` (`reversal_status`,`submitted_time`,`id`),
  CONSTRAINT `chk_settlement_reversal_state` CHECK (((`reversal_status` in (_utf8mb4'PENDING_APPROVAL',_utf8mb4'APPROVED',_utf8mb4'REJECTED')) and (((`reversal_status` = _utf8mb4'PENDING_APPROVAL') and (`reversal_batch_no` is null) and (`decided_by_account_id` is null) and (`decided_by_account_name` is null) and (`decided_role_snapshot` is null) and (`decision_client_ip` is null) and (`decision_user_agent` is null) and (`decision_action` is null) and (`decision_request_key` is null) and (`decision_comment` is null) and (`decision_time` is null)) or ((`reversal_status` = _utf8mb4'APPROVED') and (`reversal_batch_no` is not null) and (`decided_by_account_id` is not null) and (`decided_by_account_name` is not null) and (`decided_role_snapshot` is not null) and (`decision_client_ip` is not null) and (`decision_user_agent` is not null) and (`decision_action` = _utf8mb4'APPROVE') and (`decision_request_key` is not null) and (`decision_comment` is not null) and (`decision_time` is not null) and (`submitted_by_account_id` <> `decided_by_account_id`)) or ((`reversal_status` = _utf8mb4'REJECTED') and (`reversal_batch_no` is null) and (`decided_by_account_id` is not null) and (`decided_by_account_name` is not null) and (`decided_role_snapshot` is not null) and (`decision_client_ip` is not null) and (`decision_user_agent` is not null) and (`decision_action` = _utf8mb4'REJECT') and (`decision_request_key` is not null) and (`decision_comment` is not null) and (`decision_time` is not null) and (`submitted_by_account_id` <> `decided_by_account_id`))))),
  CONSTRAINT `chk_settlement_reversal_value` CHECK (((`target_currency_exponent` between 0 and 8) and (`original_batch_version` >= 0) and (`net_direction` in (_utf8mb4'CREDIT',_utf8mb4'DEBIT')) and (`net_amount` >= 0) and (`version` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Settlement reversal Maker-Checker order and frozen posting identity';

-- ----------------------------
-- Table structure for settlement_review_candidate
-- ----------------------------
DROP TABLE IF EXISTS `settlement_review_candidate`;
CREATE TABLE `settlement_review_candidate` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `review_candidate_no` varchar(34) NOT NULL,
  `review_order_no` varchar(19) NOT NULL,
  `candidate_id` bigint NOT NULL,
  `candidate_no` varchar(64) NOT NULL,
  `source_type` varchar(24) NOT NULL,
  `source_business_id` varchar(64) NOT NULL,
  `source_revision` int NOT NULL,
  `source_transaction_id` varchar(64) NOT NULL,
  `source_transaction_date_time` datetime(3) NOT NULL,
  `locked_candidate_version` bigint NOT NULL,
  `clearing_fingerprint` char(64) NOT NULL,
  `relation_status` varchar(16) NOT NULL,
  `locked_time` datetime(3) NOT NULL,
  `consumed_time` datetime(3) DEFAULT NULL,
  `released_time` datetime(3) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_review_candidate_no` (`review_candidate_no`),
  UNIQUE KEY `uk_settlement_review_candidate` (`review_order_no`,`candidate_id`),
  KEY `idx_settlement_review_candidate_id` (`candidate_id`,`relation_status`,`id`),
  CONSTRAINT `chk_settlement_review_candidate_state` CHECK (((`source_type` in (_utf8mb4'CLEARING_REVISION',_utf8mb4'RESERVE_RELEASE',_utf8mb4'ADJUSTMENT')) and (`source_revision` >= 1) and (`locked_candidate_version` >= 0) and (`version` >= 0) and (`relation_status` in (_utf8mb4'LOCKED',_utf8mb4'CONSUMED',_utf8mb4'RELEASED')) and (((`relation_status` = _utf8mb4'LOCKED') and (`consumed_time` is null) and (`released_time` is null)) or ((`relation_status` = _utf8mb4'CONSUMED') and (`consumed_time` is not null) and (`released_time` is null)) or ((`relation_status` = _utf8mb4'RELEASED') and (`consumed_time` is null) and (`released_time` is not null)))))
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Immutable settlement review candidate snapshot';

-- ----------------------------
-- Table structure for settlement_review_daily_sequence
-- ----------------------------
DROP TABLE IF EXISTS `settlement_review_daily_sequence`;
CREATE TABLE `settlement_review_daily_sequence` (
  `business_date` date NOT NULL,
  `current_sequence` int NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`business_date`),
  CONSTRAINT `chk_settlement_review_sequence` CHECK (((`current_sequence` between 0 and 99999999) and (`version` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Settlement review database daily sequence';

-- ----------------------------
-- Table structure for settlement_review_decision_recovery_audit
-- ----------------------------
DROP TABLE IF EXISTS `settlement_review_decision_recovery_audit`;
CREATE TABLE `settlement_review_decision_recovery_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_no` varchar(40) NOT NULL,
  `review_order_no` varchar(19) NOT NULL,
  `request_key` varchar(64) NOT NULL,
  `expected_version` bigint NOT NULL,
  `task_status_before` varchar(24) NOT NULL,
  `processed_segment_count_before` int NOT NULL,
  `result_batch_count_before` int NOT NULL,
  `retry_count_before` int NOT NULL,
  `failure_code_before` varchar(64) NOT NULL,
  `failure_message_before` varchar(500) DEFAULT NULL,
  `started_time_before` datetime(3) DEFAULT NULL,
  `completed_time_before` datetime(3) DEFAULT NULL,
  `operator_account_id` bigint NOT NULL,
  `operator_account_name` varchar(128) NOT NULL,
  `operator_role_snapshot` varchar(1000) NOT NULL,
  `client_ip` varchar(64) NOT NULL,
  `user_agent` varchar(500) NOT NULL,
  `reason` varchar(400) NOT NULL,
  `operation_time` datetime(3) NOT NULL,
  `recovered_time` datetime(3) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_review_decision_recovery_request` (`request_key`),
  KEY `idx_settlement_review_decision_recovery_task_time` (`task_no`,`recovered_time`,`id`),
  KEY `idx_settlement_review_decision_recovery_order_time` (`review_order_no`,`recovered_time`,`id`),
  CONSTRAINT `chk_settlement_review_decision_recovery_value` CHECK (((`expected_version` >= 0) and (`processed_segment_count_before` >= 0) and (`result_batch_count_before` between 0 and `processed_segment_count_before`) and (`retry_count_before` >= 0) and (`task_status_before` = _utf8mb4'FAILED') and (`operator_account_id` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Immutable audit for manually resuming failed segmented settlement review decisions';

-- ----------------------------
-- Table structure for settlement_review_decision_task
-- ----------------------------
DROP TABLE IF EXISTS `settlement_review_decision_task`;
CREATE TABLE `settlement_review_decision_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_no` varchar(40) NOT NULL,
  `request_key` varchar(128) NOT NULL,
  `review_order_no` varchar(19) NOT NULL,
  `expected_review_version` bigint NOT NULL,
  `decision_action` varchar(16) NOT NULL,
  `decision_comment` varchar(400) NOT NULL,
  `operator_account_id` bigint NOT NULL,
  `operator_account_name` varchar(128) NOT NULL,
  `operator_role_snapshot` varchar(1000) NOT NULL,
  `client_ip` varchar(64) NOT NULL,
  `user_agent` varchar(500) NOT NULL,
  `operation_time` datetime(3) NOT NULL,
  `total_segment_count` int NOT NULL,
  `processed_segment_count` int NOT NULL DEFAULT '0',
  `result_batch_count` int NOT NULL DEFAULT '0',
  `first_settlement_batch_no` varchar(19) DEFAULT NULL,
  `task_status` varchar(24) NOT NULL,
  `processing_owner` varchar(128) DEFAULT NULL,
  `processing_deadline` datetime(3) DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_time` datetime(3) NOT NULL,
  `last_failure_code` varchar(64) DEFAULT NULL,
  `last_failure_message` varchar(500) DEFAULT NULL,
  `started_time` datetime(3) DEFAULT NULL,
  `completed_time` datetime(3) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `active_review_order_no` varchar(19) GENERATED ALWAYS AS ((case when (`task_status` in (_utf8mb4'QUEUED',_utf8mb4'PROCESSING',_utf8mb4'FAILED')) then `review_order_no` else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_review_decision_task_no` (`task_no`),
  UNIQUE KEY `uk_settlement_review_decision_request` (`request_key`),
  UNIQUE KEY `uk_settlement_review_decision_active` (`active_review_order_no`),
  KEY `idx_settlement_review_decision_due` (`task_status`,`next_retry_time`,`id`),
  KEY `idx_settlement_review_decision_order` (`review_order_no`,`create_time`,`id`),
  CONSTRAINT `chk_settlement_review_decision_task` CHECK (((`expected_review_version` >= 0) and (`decision_action` in (_latin1'APPROVE',_latin1'REJECT',_latin1'CANCEL')) and (`total_segment_count` >= 1) and (`processed_segment_count` between 0 and `total_segment_count`) and (`result_batch_count` between 0 and `processed_segment_count`) and (`retry_count` >= 0) and (`version` >= 0) and (`task_status` in (_latin1'QUEUED',_latin1'PROCESSING',_latin1'COMPLETED',_latin1'FAILED')) and ((`decision_action` = _latin1'APPROVE') or ((`result_batch_count` = 0) and (`first_settlement_batch_no` is null)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Resumable segmented Maker-Checker decision task for large settlement reviews';

-- ----------------------------
-- Table structure for settlement_review_order
-- ----------------------------
DROP TABLE IF EXISTS `settlement_review_order`;
CREATE TABLE `settlement_review_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `review_order_no` varchar(19) NOT NULL,
  `create_request_key` varchar(128) NOT NULL,
  `selection_fingerprint` char(64) NOT NULL,
  `review_type` varchar(24) NOT NULL,
  `create_mode` varchar(24) NOT NULL DEFAULT 'MANUAL',
  `merchant_id` varchar(64) NOT NULL,
  `settlement_profile_id` bigint NOT NULL,
  `settlement_account_id` bigint NOT NULL,
  `target_currency` char(3) NOT NULL,
  `target_currency_exponent` tinyint NOT NULL,
  `business_date` date NOT NULL,
  `business_time_zone` varchar(64) NOT NULL,
  `cutoff_begin_time` datetime(3) NOT NULL,
  `cutoff_end_time` datetime(3) NOT NULL,
  `candidate_count` int NOT NULL,
  `projectable_candidate_count` int NOT NULL DEFAULT '0',
  `source_fingerprint` char(64) NOT NULL,
  `rate_fingerprint` char(64) NOT NULL,
  `result_fingerprint` char(64) NOT NULL,
  `net_direction` varchar(8) NOT NULL,
  `net_amount` decimal(24,8) NOT NULL,
  `review_status` varchar(24) NOT NULL,
  `created_by_account_id` bigint NOT NULL,
  `created_by_account_name` varchar(128) NOT NULL,
  `submitted_by_account_id` bigint NOT NULL,
  `submitted_by_account_name` varchar(128) NOT NULL,
  `submitted_role_snapshot` varchar(1000) NOT NULL,
  `submit_client_ip` varchar(64) NOT NULL,
  `submit_user_agent` varchar(500) NOT NULL,
  `submit_reason` varchar(400) NOT NULL,
  `submitted_time` datetime(3) NOT NULL,
  `decided_by_account_id` bigint DEFAULT NULL,
  `decided_by_account_name` varchar(128) DEFAULT NULL,
  `decided_role_snapshot` varchar(1000) DEFAULT NULL,
  `decision_client_ip` varchar(64) DEFAULT NULL,
  `decision_user_agent` varchar(500) DEFAULT NULL,
  `decision_action` varchar(16) DEFAULT NULL,
  `decision_request_key` varchar(128) DEFAULT NULL,
  `review_comment` varchar(400) DEFAULT NULL,
  `decision_time` datetime(3) DEFAULT NULL,
  `settlement_batch_no` varchar(19) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_review_no` (`review_order_no`),
  UNIQUE KEY `uk_settlement_review_create_request` (`create_request_key`),
  UNIQUE KEY `uk_settlement_review_decision_request` (`decision_request_key`),
  UNIQUE KEY `uk_settlement_review_batch` (`settlement_batch_no`),
  KEY `idx_settlement_review_merchant_status` (`merchant_id`,`review_status`,`create_time`,`id`),
  KEY `idx_settlement_review_status_time` (`review_status`,`submitted_time`,`id`),
  CONSTRAINT `chk_settlement_review_state` CHECK (((`review_type` in (_latin1'REGULAR',_latin1'RESERVE_RELEASE',_latin1'ADJUSTMENT')) and (`create_mode` in (_latin1'MANUAL',_latin1'MANUAL_ASYNC',_latin1'AUTO_REVIEW')) and (`net_direction` in (_latin1'CREDIT',_latin1'DEBIT')) and (`review_status` in (_latin1'PENDING_APPROVAL',_latin1'APPROVED',_latin1'REJECTED',_latin1'CANCELLED',_latin1'EXPIRED')) and (((`review_status` = _latin1'PENDING_APPROVAL') and (`decided_by_account_id` is null) and (`decision_action` is null) and (`decision_request_key` is null) and (`decision_time` is null) and (`review_comment` is null) and (`settlement_batch_no` is null)) or ((`review_status` = _latin1'APPROVED') and (`decided_by_account_id` is not null) and (`decided_by_account_name` is not null) and (`decided_role_snapshot` is not null) and (`decision_client_ip` is not null) and (`decision_user_agent` is not null) and (`decision_action` = _latin1'APPROVE') and (`decision_request_key` is not null) and (`decision_time` is not null) and (`review_comment` is not null) and (`settlement_batch_no` is not null) and (`submitted_by_account_id` <> `decided_by_account_id`)) or ((`review_status` = _latin1'REJECTED') and (`decided_by_account_id` is not null) and (`decided_by_account_name` is not null) and (`decided_role_snapshot` is not null) and (`decision_client_ip` is not null) and (`decision_user_agent` is not null) and (`decision_action` = _latin1'REJECT') and (`decision_request_key` is not null) and (`decision_time` is not null) and (`review_comment` is not null) and (`submitted_by_account_id` <> `decided_by_account_id`) and (`settlement_batch_no` is null)) or ((`review_status` = _latin1'CANCELLED') and (`decided_by_account_id` is not null) and (`decided_by_account_name` is not null) and (`decided_role_snapshot` is not null) and (`decision_client_ip` is not null) and (`decision_user_agent` is not null) and (`decision_action` = _latin1'CANCEL') and (`decision_request_key` is not null) and (`decision_time` is not null) and (`review_comment` is not null) and (`submitted_by_account_id` = `decided_by_account_id`) and (`settlement_batch_no` is null)) or ((`review_status` = _latin1'EXPIRED') and (`decided_by_account_id` is not null) and (`decided_by_account_name` is not null) and (`decided_role_snapshot` is not null) and (`decision_client_ip` is not null) and (`decision_user_agent` is not null) and (`decision_action` = _latin1'EXPIRE') and (`decision_request_key` is not null) and (`decision_time` is not null) and (`review_comment` is not null) and (`settlement_batch_no` is null))))),
  CONSTRAINT `chk_settlement_review_value` CHECK (((`target_currency_exponent` between 0 and 8) and (`cutoff_end_time` > `cutoff_begin_time`) and (`candidate_count` >= 1) and (`projectable_candidate_count` between 0 and `candidate_count`) and (`net_amount` >= 0) and (`version` >= 0)))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Immutable settlement review order and Maker-Checker audit';

-- ----------------------------
-- Table structure for settlement_review_rate
-- ----------------------------
DROP TABLE IF EXISTS `settlement_review_rate`;
CREATE TABLE `settlement_review_rate` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `review_order_no` varchar(19) NOT NULL,
  `source_currency` char(3) NOT NULL,
  `target_currency` char(3) NOT NULL,
  `direct_rate` decimal(24,12) NOT NULL,
  `source_currency_exponent` tinyint NOT NULL,
  `target_currency_exponent` tinyint NOT NULL,
  `rate_source` varchar(64) NOT NULL,
  `quote_id` varchar(128) DEFAULT NULL,
  `source_quote_direction` varchar(16) NOT NULL,
  `effective_time` datetime(3) NOT NULL,
  `locked_time` datetime(3) NOT NULL,
  `locked_by` varchar(128) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_review_rate_pair` (`review_order_no`,`source_currency`,`target_currency`),
  CONSTRAINT `chk_settlement_review_rate` CHECK (((`direct_rate` > 0) and (`source_currency_exponent` between 0 and 8) and (`target_currency_exponent` between 0 and 8) and (`source_quote_direction` in (_utf8mb4'DIRECT',_utf8mb4'INVERSE'))))
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Immutable settlement review unified rate matrix';

-- ----------------------------
-- Table structure for settlement_review_segment
-- ----------------------------
DROP TABLE IF EXISTS `settlement_review_segment`;
CREATE TABLE `settlement_review_segment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `segment_no` varchar(64) NOT NULL,
  `review_order_no` varchar(19) NOT NULL,
  `sequence_no` int NOT NULL,
  `first_candidate_id` bigint NOT NULL,
  `last_candidate_id` bigint NOT NULL,
  `candidate_count` int NOT NULL,
  `projectable_candidate_count` int NOT NULL,
  `source_fingerprint` char(64) NOT NULL,
  `result_fingerprint` char(64) NOT NULL,
  `net_signed_amount` decimal(24,8) NOT NULL,
  `settlement_batch_no` varchar(19) DEFAULT NULL,
  `segment_status` varchar(16) NOT NULL DEFAULT 'LOCKED',
  `processed_time` datetime(3) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_review_segment_no` (`segment_no`),
  UNIQUE KEY `uk_settlement_review_segment_sequence` (`review_order_no`,`sequence_no`),
  UNIQUE KEY `uk_settlement_review_segment_range` (`review_order_no`,`first_candidate_id`,`last_candidate_id`),
  UNIQUE KEY `uk_settlement_review_segment_batch` (`settlement_batch_no`),
  KEY `idx_settlement_review_segment_status` (`review_order_no`,`segment_status`,`sequence_no`,`id`),
  CONSTRAINT `chk_settlement_review_segment` CHECK (((`sequence_no` >= 1) and (`first_candidate_id` > 0) and (`last_candidate_id` >= `first_candidate_id`) and (`candidate_count` >= 1) and (`projectable_candidate_count` between 0 and `candidate_count`) and (`segment_status` in (_latin1'LOCKED',_latin1'CONSUMED',_latin1'RELEASED')) and (`version` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Physical processing segments behind one logical settlement review order';

-- ----------------------------
-- Table structure for settlement_review_summary
-- ----------------------------
DROP TABLE IF EXISTS `settlement_review_summary`;
CREATE TABLE `settlement_review_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `review_order_no` varchar(19) NOT NULL,
  `merchant_id` varchar(64) NOT NULL,
  `payment_type` varchar(32) NOT NULL,
  `payment_method` varchar(32) NOT NULL,
  `transaction_type` varchar(32) NOT NULL,
  `result_item_type` varchar(32) NOT NULL,
  `fee_category` varchar(32) NOT NULL,
  `direction` varchar(8) NOT NULL,
  `source_currency` char(3) NOT NULL,
  `target_currency` char(3) NOT NULL,
  `transaction_count` bigint NOT NULL,
  `source_amount` decimal(24,8) NOT NULL,
  `target_amount` decimal(24,8) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_review_summary` (`review_order_no`,`payment_type`,`payment_method`,`transaction_type`,`result_item_type`,`fee_category`,`direction`,`source_currency`,`target_currency`),
  CONSTRAINT `chk_settlement_review_summary` CHECK (((`direction` in (_utf8mb4'CREDIT',_utf8mb4'DEBIT')) and (`transaction_count` > 0) and (`source_amount` >= 0) and (`target_amount` >= 0)))
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Immutable settlement review financial summary';

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
  `pending_email` varchar(150) DEFAULT NULL COMMENT '等待所有权验证的新邮箱；验证完成前不得替换正式邮箱',
  `email_verified_at` datetime(3) DEFAULT NULL COMMENT '登录邮箱可信确认时间；为空时不得用于匿名找回密码',
  `activation_status` varchar(30) NOT NULL DEFAULT 'ACTIVE' COMMENT '账号激活状态：PENDING_ACTIVATION、ACTIVE',
  `activated_at` datetime(3) DEFAULT NULL COMMENT '账号所有人完成邮箱验证并设置首次密码的时间',
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
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统登录账号表';

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
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统账号MFA配置表';

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
) ENGINE=InnoDB AUTO_INCREMENT=81 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统账号MFA安全操作日志表';

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
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统账号MFA短期票据表';

-- ----------------------------
-- Table structure for sys_account_password_history
-- ----------------------------
DROP TABLE IF EXISTS `sys_account_password_history`;
CREATE TABLE `sys_account_password_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `account_id` bigint NOT NULL COMMENT '登录账号ID',
  `password_hash` varchar(255) NOT NULL COMMENT '历史密码PBKDF2哈希',
  `password_salt` varchar(100) NOT NULL COMMENT '历史密码随机盐',
  `password_algo` varchar(50) NOT NULL COMMENT '历史密码哈希算法',
  `changed_at` datetime(3) NOT NULL COMMENT '该密码被替换或迁移播种的时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_account_password_history_recent` (`app_id`,`account_id`,`changed_at` DESC,`id` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号历史密码哈希表';

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
) ENGINE=InnoDB AUTO_INCREMENT=68 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号角色关联表';

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
) ENGINE=InnoDB AUTO_INCREMENT=15352 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典数据表';

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
) ENGINE=InnoDB AUTO_INCREMENT=85 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型表';

-- ----------------------------
-- Table structure for sys_email_verification_request
-- ----------------------------
DROP TABLE IF EXISTS `sys_email_verification_request`;
CREATE TABLE `sys_email_verification_request` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_no` varchar(64) NOT NULL COMMENT '高熵公开请求编号，不包含账号或邮箱信息',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号；管理系统请求可为空',
  `account_id` bigint NOT NULL COMMENT '待验证账号ID',
  `purpose` varchar(30) NOT NULL COMMENT '用途：ACCOUNT_ACTIVATION、EMAIL_VERIFICATION、EMAIL_CHANGE',
  `email_fingerprint` char(64) NOT NULL COMMENT '规范化目标邮箱SHA-256指纹',
  `code_hash` varchar(255) NOT NULL COMMENT '邮件验证码PBKDF2哈希',
  `code_salt` varchar(100) NOT NULL COMMENT '邮件验证码随机盐',
  `expire_at` datetime(3) NOT NULL COMMENT '邮件验证码到期时间',
  `verify_count` int NOT NULL DEFAULT '0' COMMENT '邮件验证码累计失败次数',
  `verified_at` datetime(3) DEFAULT NULL COMMENT '验证码验证成功时间',
  `action_token_hash` char(64) DEFAULT NULL COMMENT '首次设密一次性动作令牌SHA-256哈希',
  `action_token_expire_at` datetime(3) DEFAULT NULL COMMENT '首次设密动作令牌到期时间',
  `state` varchar(30) NOT NULL COMMENT 'PENDING、VERIFIED、COMPLETED、EXPIRED、LOCKED、SUPERSEDED、BLOCKED',
  `request_ip` varchar(64) NOT NULL COMMENT '发起IP，用于限流和安全审计',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '截断后的User-Agent',
  `locale` varchar(20) NOT NULL DEFAULT 'zh-CN' COMMENT '邮件语言',
  `completed_at` datetime(3) DEFAULT NULL COMMENT '验证或激活完成时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_email_verification_request_no` (`request_no`),
  KEY `idx_sys_email_verification_account_state` (`app_id`,`account_id`,`state`,`created_at`),
  KEY `idx_sys_email_verification_account_time` (`app_id`,`account_id`,`created_at`),
  KEY `idx_sys_email_verification_ip_time` (`app_id`,`request_ip`,`created_at`),
  KEY `idx_sys_email_verification_expire_state` (`state`,`expire_at`,`action_token_expire_at`),
  KEY `idx_sys_email_verification_merchant_account` (`app_id`,`merchant_id`,`account_id`,`state`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统账号激活与邮箱所有权验证请求表';

-- ----------------------------
-- Table structure for sys_file_template
-- ----------------------------
DROP TABLE IF EXISTS `sys_file_template`;
CREATE TABLE `sys_file_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_code` varchar(64) NOT NULL COMMENT '稳定模板编码，业务代码只引用该编码',
  `owner_system` varchar(16) NOT NULL COMMENT 'ADMIN、MERCHANT或SHARED',
  `module_code` varchar(64) NOT NULL COMMENT '所属业务模块编码',
  `name_zh` varchar(128) NOT NULL COMMENT '中文下载文件名，不含扩展名',
  `name_en` varchar(128) NOT NULL COMMENT '英文下载文件名，不含扩展名',
  `file_extension` varchar(16) NOT NULL COMMENT '系统自动拼接的扩展名',
  `admin_permission_code` varchar(128) DEFAULT NULL COMMENT '管理系统消费该模板时的业务权限',
  `merchant_permission_code` varchar(128) DEFAULT NULL COMMENT '商户系统消费该模板时的业务权限',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0停用，1启用',
  `current_version_id` bigint DEFAULT NULL COMMENT '当前发布版本ID',
  `last_operation_request_id` varchar(64) DEFAULT NULL COMMENT '最近发布或回滚幂等请求ID',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '定义与发布指针CAS版本',
  `create_by` varchar(64) NOT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(64) NOT NULL COMMENT '最后操作人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识；系统模板只允许停用，不开放删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_file_template_code_deleted` (`template_code`,`deleted`),
  KEY `idx_sys_file_template_scope` (`owner_system`,`module_code`,`status`,`deleted`),
  KEY `idx_sys_file_template_current` (`current_version_id`),
  CONSTRAINT `chk_sys_file_template_extension` CHECK ((`file_extension` in (_utf8mb4'xlsx',_utf8mb4'csv',_utf8mb4'pdf',_utf8mb4'docx',_utf8mb4'zip'))),
  CONSTRAINT `chk_sys_file_template_owner` CHECK ((`owner_system` in (_utf8mb4'ADMIN',_utf8mb4'MERCHANT',_utf8mb4'SHARED'))),
  CONSTRAINT `chk_sys_file_template_permission_scope` CHECK ((((`owner_system` = _utf8mb4'ADMIN') and (`admin_permission_code` is not null) and (`merchant_permission_code` is null)) or ((`owner_system` = _utf8mb4'MERCHANT') and (`admin_permission_code` is null) and (`merchant_permission_code` is not null)) or ((`owner_system` = _utf8mb4'SHARED') and (`admin_permission_code` is not null) and (`merchant_permission_code` is not null)))),
  CONSTRAINT `chk_sys_file_template_status` CHECK ((`status` in (0,1))),
  CONSTRAINT `chk_sys_file_template_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统文件模板定义';

-- ----------------------------
-- Table structure for sys_file_template_asset
-- ----------------------------
DROP TABLE IF EXISTS `sys_file_template_asset`;
CREATE TABLE `sys_file_template_asset` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_version_id` bigint NOT NULL COMMENT '模板版本ID',
  `locale` varchar(16) NOT NULL COMMENT 'zh-CN或en-US',
  `original_filename` varchar(255) NOT NULL COMMENT '上传时安全文件名，仅审计展示',
  `content_type` varchar(128) NOT NULL COMMENT '服务端识别的MIME',
  `file_size` bigint NOT NULL COMMENT '文件字节数',
  `sha256` char(64) NOT NULL COMMENT '正文SHA-256',
  `storage_provider` varchar(32) NOT NULL COMMENT 'MINIO、AWS_S3或其它S3兼容供应商',
  `bucket_name` varchar(128) NOT NULL COMMENT '模板专用私有Bucket，仅服务端使用',
  `object_key` varchar(512) NOT NULL COMMENT '不可预测对象键，仅服务端使用',
  `create_by` varchar(64) NOT NULL COMMENT '上传人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '上传时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_file_template_asset_locale` (`template_version_id`,`locale`),
  UNIQUE KEY `uk_sys_file_template_asset_object` (`object_key`),
  KEY `idx_sys_file_template_asset_sha` (`sha256`),
  CONSTRAINT `chk_sys_file_template_asset_locale` CHECK ((`locale` in (_utf8mb4'zh-CN',_utf8mb4'en-US'))),
  CONSTRAINT `chk_sys_file_template_asset_sha` CHECK ((char_length(`sha256`) = 64)),
  CONSTRAINT `chk_sys_file_template_asset_size` CHECK ((`file_size` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=93 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统文件模板本地化资产';

-- ----------------------------
-- Table structure for sys_file_template_version
-- ----------------------------
DROP TABLE IF EXISTS `sys_file_template_version`;
CREATE TABLE `sys_file_template_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_id` bigint NOT NULL COMMENT '模板定义ID',
  `version_no` int NOT NULL COMMENT '单模板递增版本号',
  `schema_version` varchar(32) DEFAULT NULL COMMENT '导入字段协议版本',
  `request_id` varchar(64) NOT NULL COMMENT '上传幂等请求ID',
  `release_note` varchar(500) DEFAULT NULL COMMENT '版本说明',
  `publish_status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT、PUBLISHED或ARCHIVED',
  `published_by` varchar(64) DEFAULT NULL COMMENT '发布或回滚操作人',
  `publish_reason` varchar(500) DEFAULT NULL COMMENT '发布或回滚原因',
  `published_time` datetime(3) DEFAULT NULL COMMENT '最近发布时间',
  `create_by` varchar(64) NOT NULL COMMENT '上传人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '上传时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_file_template_version_no` (`template_id`,`version_no`),
  UNIQUE KEY `uk_sys_file_template_version_request` (`template_id`,`request_id`),
  KEY `idx_sys_file_template_version_status` (`template_id`,`publish_status`,`version_no`),
  CONSTRAINT `chk_sys_file_template_publish_status` CHECK ((`publish_status` in (_utf8mb4'DRAFT',_utf8mb4'PUBLISHED',_utf8mb4'ARCHIVED'))),
  CONSTRAINT `chk_sys_file_template_version_no` CHECK ((`version_no` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统文件模板不可变版本';

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
) ENGINE=InnoDB AUTO_INCREMENT=382084 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统任务执行节点表';

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
  `lease_epoch` bigint DEFAULT NULL COMMENT '本次运行对应的任务租约世代，旧记录为空',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_id` (`run_id`),
  KEY `idx_job_code_create_time` (`job_code`,`create_time`),
  KEY `idx_run_status_create_time` (`run_status`,`create_time`),
  KEY `idx_executor_node_create_time` (`executor_node`,`create_time`),
  KEY `idx_job_id_create_time` (`job_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=150152 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统任务执行日志表';

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
  `checkpoint_json` json DEFAULT NULL COMMENT '来源独立的有界扫描检查点',
  `checkpoint_version` bigint NOT NULL DEFAULT '0' COMMENT '任务检查点的乐观锁版本',
  `lease_epoch` bigint NOT NULL DEFAULT '0' COMMENT '每次有效任务抢占的单调世代',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_code` (`job_code`),
  KEY `idx_status_next_trigger_time` (`status`,`next_trigger_time`),
  KEY `idx_handler_code` (`handler_code`),
  KEY `idx_job_group` (`job_group`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统任务定义表';

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
) ENGINE=InnoDB AUTO_INCREMENT=1902 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表';

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
) ENGINE=InnoDB AUTO_INCREMENT=1916 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录会话表';

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
) ENGINE=InnoDB AUTO_INCREMENT=1810 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单表';

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
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户账号部门关联表';

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
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户账号岗位关联表';

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
) ENGINE=InnoDB AUTO_INCREMENT=2102 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户菜单授权表';

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
) ENGINE=InnoDB AUTO_INCREMENT=2346 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户权限授权表';

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
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户端登录用户表';

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
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户端用户角色关联表';

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
) ENGINE=InnoDB AUTO_INCREMENT=1224 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MQ消费数据库幂等记录表';

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
  KEY `idx_sys_mq_outbox_due` (`producer_service`,`event_status`,`next_retry_time`,`create_time`,`id`),
  KEY `idx_sys_mq_outbox_processing` (`producer_service`,`event_status`,`processing_started_time`)
) ENGINE=InnoDB AUTO_INCREMENT=158695 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='非交易可靠MQ本地消息表';

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
) ENGINE=InnoDB AUTO_INCREMENT=98873 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统后台操作日志表';

-- ----------------------------
-- Table structure for sys_password_reset_request
-- ----------------------------
DROP TABLE IF EXISTS `sys_password_reset_request`;
CREATE TABLE `sys_password_reset_request` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '高熵公开请求编号，不包含账号信息',
  `app_id` bigint NOT NULL COMMENT '系统应用ID',
  `merchant_id` varchar(32) DEFAULT NULL COMMENT '商户号；管理系统请求可为空',
  `account_id` bigint DEFAULT NULL COMMENT '合格账号ID；虚拟请求为空',
  `account_fingerprint` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规范化登录账号SHA-256指纹，仅用于限流',
  `email_code_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '邮件验证码PBKDF2哈希',
  `email_code_salt` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '邮件验证码随机盐',
  `email_expire_at` datetime(3) NOT NULL COMMENT '邮件验证码到期时间',
  `email_verify_count` int NOT NULL DEFAULT '0' COMMENT '邮件验证码累计失败次数',
  `email_verified_at` datetime(3) DEFAULT NULL COMMENT '邮件验证码验证成功时间',
  `mfa_verified_at` datetime(3) DEFAULT NULL COMMENT 'MFA验证成功时间',
  `stage_token_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '邮箱验证后阶段令牌SHA-256哈希',
  `stage_token_expire_at` datetime(3) DEFAULT NULL COMMENT '阶段令牌到期时间',
  `reset_token_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最终重置令牌SHA-256哈希',
  `reset_token_expire_at` datetime(3) DEFAULT NULL COMMENT '最终重置令牌到期时间',
  `state` varchar(30) NOT NULL COMMENT 'PENDING_EMAILã€PENDING_MFAã€READYã€COMPLETEDã€EXPIREDã€LOCKEDã€BLOCKED',
  `request_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '请求IP，用于限流和安全审计',
  `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '截断后的User-Agent',
  `locale` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'zh-CN' COMMENT '邮件语言',
  `completed_at` datetime(3) DEFAULT NULL COMMENT '密码重置完成时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_password_reset_request_no` (`request_no`),
  KEY `idx_sys_password_reset_account_time` (`app_id`,`account_fingerprint`,`created_at`),
  KEY `idx_sys_password_reset_ip_time` (`app_id`,`request_ip`,`created_at`),
  KEY `idx_sys_password_reset_account_state` (`app_id`,`account_id`,`state`,`created_at`),
  KEY `idx_sys_password_reset_expire_state` (`state`,`email_expire_at`,`reset_token_expire_at`),
  KEY `idx_sys_password_reset_merchant_fingerprint` (`app_id`,`merchant_id`,`account_fingerprint`,`created_at`),
  KEY `idx_sys_password_reset_merchant_account` (`app_id`,`merchant_id`,`account_id`,`state`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统账号找回密码安全请求表';

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
) ENGINE=InnoDB AUTO_INCREMENT=2626 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限资源表';

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
) ENGINE=InnoDB AUTO_INCREMENT=44 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

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
) ENGINE=InnoDB AUTO_INCREMENT=9633 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色菜单关联表';

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
) ENGINE=InnoDB AUTO_INCREMENT=6253 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';

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
) ENGINE=InnoDB AUTO_INCREMENT=103 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分表物理表登记表';

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
) ENGINE=InnoDB AUTO_INCREMENT=128 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分表建表任务日志表';

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
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户主体表';

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
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户与岗位关联表';

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
) ENGINE=InnoDB AUTO_INCREMENT=3664 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态验证码表';

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
) ENGINE=InnoDB AUTO_INCREMENT=202603000000004196 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存已能定位交易时间的交易异常事件，如状态推进失败、金额校验失败、回调无法处理等。';

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
) ENGINE=InnoDB AUTO_INCREMENT=202604000000001398 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存已能定位交易时间的交易异常事件，如状态推进失败、金额校验失败、回调无法处理等。';

-- ----------------------------
-- Table structure for transaction_abnormal_event_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_abnormal_event_202701`;
CREATE TABLE `transaction_abnormal_event_202701` (
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存已能定位交易时间的交易异常事件，如状态推进失败、金额校验失败、回调无法处理等。';

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
-- Table structure for transaction_additional_info_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_additional_info_202701`;
CREATE TABLE `transaction_additional_info_202701` (
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存循环付款、分期、Hosted Checkout、二维码、支付链接等扩展属性，避免主表字段膨胀。';

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
  `label_amount_currency` char(3) NOT NULL COMMENT '本次变动的标签币种。',
  `label_change_amount` decimal(20,6) NOT NULL COMMENT '本次变动的标签金额。',
  `label_authorized_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签授权金额。',
  `label_authorized_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签授权金额。',
  `label_captured_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签请款金额。',
  `label_captured_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签请款金额。',
  `label_refunded_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签退款金额。',
  `label_refunded_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签退款金额。',
  `label_available_capture_before` decimal(20,6) NOT NULL COMMENT '变动前标签可请款金额。',
  `label_available_capture_after` decimal(20,6) NOT NULL COMMENT '变动后标签可请款金额。',
  `label_available_refund_before` decimal(20,6) NOT NULL COMMENT '变动前标签可退款金额。',
  `label_available_refund_after` decimal(20,6) NOT NULL COMMENT '变动后标签可退款金额。',
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
  `label_amount_currency` char(3) NOT NULL COMMENT '本次变动的标签币种。',
  `label_change_amount` decimal(20,6) NOT NULL COMMENT '本次变动的标签金额。',
  `label_authorized_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签授权金额。',
  `label_authorized_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签授权金额。',
  `label_captured_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签请款金额。',
  `label_captured_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签请款金额。',
  `label_refunded_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签退款金额。',
  `label_refunded_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签退款金额。',
  `label_available_capture_before` decimal(20,6) NOT NULL COMMENT '变动前标签可请款金额。',
  `label_available_capture_after` decimal(20,6) NOT NULL COMMENT '变动后标签可请款金额。',
  `label_available_refund_before` decimal(20,6) NOT NULL COMMENT '变动前标签可退款金额。',
  `label_available_refund_after` decimal(20,6) NOT NULL COMMENT '变动后标签可退款金额。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000315 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录授权、请款、退款、拒付等动作对订单累计金额的影响。';

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
  `label_amount_currency` char(3) NOT NULL COMMENT '本次变动的标签币种。',
  `label_change_amount` decimal(20,6) NOT NULL COMMENT '本次变动的标签金额。',
  `label_authorized_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签授权金额。',
  `label_authorized_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签授权金额。',
  `label_captured_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签请款金额。',
  `label_captured_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签请款金额。',
  `label_refunded_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签退款金额。',
  `label_refunded_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签退款金额。',
  `label_available_capture_before` decimal(20,6) NOT NULL COMMENT '变动前标签可请款金额。',
  `label_available_capture_after` decimal(20,6) NOT NULL COMMENT '变动后标签可请款金额。',
  `label_available_refund_before` decimal(20,6) NOT NULL COMMENT '变动前标签可退款金额。',
  `label_available_refund_after` decimal(20,6) NOT NULL COMMENT '变动后标签可退款金额。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202604000000011765 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录授权、请款、退款、拒付等动作对订单累计金额的影响。';

-- ----------------------------
-- Table structure for transaction_amount_change_log_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_amount_change_log_202701`;
CREATE TABLE `transaction_amount_change_log_202701` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `amount_change_id` varchar(64) NOT NULL COMMENT '金额变动ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) NOT NULL COMMENT '触发金额变动的交易动作ID。',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '被影响或被引用的源平台交易ID。',
  `source_operation_id` varchar(64) DEFAULT NULL COMMENT '被影响或被引用的源动作ID。',
  `change_type` varchar(64) NOT NULL COMMENT '变动类型：AUTH_INCREASE、CAPTURE、REFUND、VOID、CHARGEBACK。',
  `amount_currency` char(3) NOT NULL COMMENT '金额币种。',
  `change_amount` decimal(20,6) NOT NULL COMMENT '本次变动金额，可正可负。',
  `label_amount_currency` char(3) NOT NULL COMMENT '本次变动的标签币种。',
  `label_change_amount` decimal(20,6) NOT NULL COMMENT '本次变动的标签金额。',
  `label_authorized_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签授权金额。',
  `label_authorized_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签授权金额。',
  `label_captured_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签请款金额。',
  `label_captured_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签请款金额。',
  `label_refunded_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签退款金额。',
  `label_refunded_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签退款金额。',
  `label_available_capture_before` decimal(20,6) NOT NULL COMMENT '变动前标签可请款金额。',
  `label_available_capture_after` decimal(20,6) NOT NULL COMMENT '变动后标签可请款金额。',
  `label_available_refund_before` decimal(20,6) NOT NULL COMMENT '变动前标签可退款金额。',
  `label_available_refund_after` decimal(20,6) NOT NULL COMMENT '变动后标签可退款金额。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录授权、请款、退款、拒付等动作对订单累计金额的影响。';

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
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000018 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存3DS、SCA、渠道/卡组织认证结果，支撑交易详情页认证信息展示和后续责任转移判断。';

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
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000004 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存3DS、SCA、渠道/卡组织认证结果，支撑交易详情页认证信息展示和后续责任转移判断。';

-- ----------------------------
-- Table structure for transaction_authentication_info_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_authentication_info_202701`;
CREATE TABLE `transaction_authentication_info_202701` (
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存3DS、SCA、渠道/卡组织认证结果，支撑交易详情页认证信息展示和后续责任转移判断。';

-- ----------------------------
-- Table structure for transaction_billing_info
-- ----------------------------
DROP TABLE IF EXISTS `transaction_billing_info`;
CREATE TABLE `transaction_billing_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `billing_info_id` varchar(64) NOT NULL COMMENT '账单信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '持卡人名。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '持卡人姓。',
  `email` varchar(64) DEFAULT NULL COMMENT '持卡人邮箱明文。',
  `phone` varchar(32) DEFAULT NULL COMMENT '持卡人电话明文。',
  `billing_country` varchar(3) DEFAULT NULL COMMENT '账单国家/地区。',
  `billing_state` varchar(64) DEFAULT NULL COMMENT '账单州/省。',
  `billing_city` varchar(64) DEFAULT NULL COMMENT '账单城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '持卡人账单街道。',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='持卡人账单信息明文交易快照。';

-- ----------------------------
-- Table structure for transaction_billing_info_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_billing_info_202603`;
CREATE TABLE `transaction_billing_info_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `billing_info_id` varchar(64) NOT NULL COMMENT '账单信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '持卡人名。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '持卡人姓。',
  `email` varchar(64) DEFAULT NULL COMMENT '持卡人邮箱明文。',
  `phone` varchar(32) DEFAULT NULL COMMENT '持卡人电话明文。',
  `billing_country` varchar(3) DEFAULT NULL COMMENT '账单国家/地区。',
  `billing_state` varchar(64) DEFAULT NULL COMMENT '账单州/省。',
  `billing_city` varchar(64) DEFAULT NULL COMMENT '账单城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '持卡人账单街道。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202603000000014031 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='持卡人账单信息明文交易快照。';

-- ----------------------------
-- Table structure for transaction_billing_info_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_billing_info_202604`;
CREATE TABLE `transaction_billing_info_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `billing_info_id` varchar(64) NOT NULL COMMENT '账单信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '持卡人名。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '持卡人姓。',
  `email` varchar(64) DEFAULT NULL COMMENT '持卡人邮箱明文。',
  `phone` varchar(32) DEFAULT NULL COMMENT '持卡人电话明文。',
  `billing_country` varchar(3) DEFAULT NULL COMMENT '账单国家/地区。',
  `billing_state` varchar(64) DEFAULT NULL COMMENT '账单州/省。',
  `billing_city` varchar(64) DEFAULT NULL COMMENT '账单城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '持卡人账单街道。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202604000000048705 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='持卡人账单信息明文交易快照。';

-- ----------------------------
-- Table structure for transaction_billing_info_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_billing_info_202701`;
CREATE TABLE `transaction_billing_info_202701` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `billing_info_id` varchar(64) NOT NULL COMMENT '账单信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '持卡人名。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '持卡人姓。',
  `email` varchar(64) DEFAULT NULL COMMENT '持卡人邮箱明文。',
  `phone` varchar(32) DEFAULT NULL COMMENT '持卡人电话明文。',
  `billing_country` varchar(3) DEFAULT NULL COMMENT '账单国家/地区。',
  `billing_state` varchar(64) DEFAULT NULL COMMENT '账单州/省。',
  `billing_city` varchar(64) DEFAULT NULL COMMENT '账单城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '持卡人账单街道。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='持卡人账单信息明文交易快照。';

-- ----------------------------
-- Table structure for transaction_callback_inbox
-- ----------------------------
DROP TABLE IF EXISTS `transaction_callback_inbox`;
CREATE TABLE `transaction_callback_inbox` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键。',
  `inbox_id` varchar(64) NOT NULL COMMENT 'Inbox 全局业务号。',
  `trace_id` varchar(64) NOT NULL COMMENT '首次接收回调的链路号。',
  `channel_code` varchar(32) NOT NULL COMMENT '标准渠道编码。',
  `credential_hint` varchar(128) DEFAULT NULL COMMENT '验签使用的渠道凭据标识，如 MID；禁止保存密钥。',
  `callback_event_id` varchar(256) NOT NULL COMMENT '渠道稳定事件号。',
  `idempotency_key` char(64) NOT NULL COMMENT '渠道、凭据标识和事件号生成的 SHA-256。',
  `payload_json` json NOT NULL COMMENT '已验签标准事件；不包含原始请求头和可逆报文正文。',
  `request_header_json_masked` json DEFAULT NULL COMMENT '数量和长度受控的脱敏请求头摘要。',
  `request_body_length` int unsigned NOT NULL COMMENT '原始回调报文字节数。',
  `request_body_sha256` char(64) NOT NULL COMMENT '原始回调报文 SHA-256。',
  `inbox_status` varchar(16) NOT NULL COMMENT 'RECEIVED、PROCESSING、RETRY、PROCESSED、CLOSED。',
  `retry_count` int unsigned NOT NULL DEFAULT '0' COMMENT '已记录的处理失败次数。',
  `max_retry_count` int unsigned NOT NULL DEFAULT '12' COMMENT '最大处理失败次数。',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下一次允许抢占的时间。',
  `last_error` varchar(256) DEFAULT NULL COMMENT '最近失败异常类型，不保存异常消息和敏感报文。',
  `received_time` datetime(3) NOT NULL COMMENT 'OpenAPI 入口收到回调的时间。',
  `processed_time` datetime(3) DEFAULT NULL COMMENT '业务回调处理完成时间。',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '多实例抢占和结果回写 CAS 版本。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间及 PROCESSING 租约起点。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_callback_inbox_no` (`inbox_id`),
  UNIQUE KEY `uk_callback_inbox_idempotency` (`channel_code`,`idempotency_key`),
  KEY `idx_callback_inbox_due` (`inbox_status`,`next_retry_time`,`id`),
  KEY `idx_callback_inbox_stale` (`inbox_status`,`update_time`,`id`),
  KEY `idx_callback_inbox_received` (`channel_code`,`received_time`,`id`),
  CONSTRAINT `chk_callback_inbox_retry` CHECK (((`retry_count` <= `max_retry_count`) and (`max_retry_count` > 0))),
  CONSTRAINT `chk_callback_inbox_status` CHECK ((`inbox_status` in (_utf8mb4'RECEIVED',_utf8mb4'PROCESSING',_utf8mb4'RETRY',_utf8mb4'PROCESSED',_utf8mb4'CLOSED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道回调可靠受理 Inbox；先落库提交再返回渠道 ACK，Worker 至少一次消费。';

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
-- Table structure for transaction_card_vault_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_card_vault_202701`;
CREATE TABLE `transaction_card_vault_202701` (
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收银台卡资料加密分表模板';

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
-- Table structure for transaction_channel_callback_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_channel_callback_202701`;
CREATE TABLE `transaction_channel_callback_202701` (
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存渠道回调解析后的业务处理结果、幂等和状态推进结果。';

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
-- Table structure for transaction_channel_callback_log_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_channel_callback_log_202701`;
CREATE TABLE `transaction_channel_callback_log_202701` (
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存渠道回调原文、验签、IP校验和平台响应日志。';

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
) ENGINE=InnoDB AUTO_INCREMENT=202603000000015180 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存渠道请求、响应、异常的可审计日志；请求/响应对象直接转 JSON 后脱敏入库。';

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
) ENGINE=InnoDB AUTO_INCREMENT=202604000000060838 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存渠道请求、响应、异常的可审计日志；请求/响应对象直接转 JSON 后脱敏入库。';

-- ----------------------------
-- Table structure for transaction_channel_interaction_log_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_channel_interaction_log_202701`;
CREATE TABLE `transaction_channel_interaction_log_202701` (
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存渠道请求、响应、异常的可审计日志；请求/响应对象直接转 JSON 后脱敏入库。';

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
) ENGINE=InnoDB AUTO_INCREMENT=202603000000015076 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一次渠道请求的核心字段、同步响应摘要和渠道结果判断。';

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
) ENGINE=InnoDB AUTO_INCREMENT=202604000000060838 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一次渠道请求的核心字段、同步响应摘要和渠道结果判断。';

-- ----------------------------
-- Table structure for transaction_channel_request_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_channel_request_202701`;
CREATE TABLE `transaction_channel_request_202701` (
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一次渠道请求的核心字段、同步响应摘要和渠道结果判断。';

-- ----------------------------
-- Table structure for transaction_clearing_detail
-- ----------------------------
DROP TABLE IF EXISTS `transaction_clearing_detail`;
CREATE TABLE `transaction_clearing_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID',
  `clearing_detail_no` varchar(64) NOT NULL COMMENT '清分明细号',
  `finance_state_id` varchar(64) NOT NULL COMMENT '所属清分汇总ID',
  `transaction_id` varchar(64) NOT NULL COMMENT '动作级平台交易号',
  `operation_id` varchar(64) NOT NULL COMMENT '交易生命周期关联号',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '退款、冲正、拒付等来源动作交易号',
  `source_clearing_detail_no` varchar(64) DEFAULT NULL COMMENT '冲正或返还所引用的原清分明细号',
  `source_settlement_result_item_no` varchar(64) DEFAULT NULL COMMENT '跨币种原费用已结算后返费所引用的实际收费结果明细号',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `payment_type` varchar(32) NOT NULL COMMENT '清分时冻结的支付类型，用于结算分组',
  `payment_method` varchar(32) NOT NULL COMMENT '清分时冻结的支付方式，用于结算分组',
  `transaction_type` varchar(32) NOT NULL COMMENT '交易动作类型',
  `clearing_revision` int NOT NULL COMMENT '所属清分修订号，从1递增',
  `line_no` int NOT NULL COMMENT '同一修订内稳定明细序号，从1递增',
  `item_type` varchar(32) NOT NULL COMMENT 'PRINCIPAL、PLATFORM_FEE、FEE_REVERSAL、ADJUSTMENT；禁止保存保证金',
  `fee_category` varchar(32) DEFAULT NULL COMMENT '交易费、内外风控费、3DS费、退款费、换汇费、拒付费等；本金可为空',
  `risk_service_type` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '风控费用细分：INTERNAL、EXTERNAL、THREE_DS；非风控费用为NONE',
  `item_code` varchar(64) NOT NULL COMMENT '修订内稳定业务项编码，例如PRINCIPAL或FEE:TRANSACTION_FEE:ruleId',
  `item_name` varchar(128) NOT NULL COMMENT '费用或本金项目名称快照',
  `direction` varchar(8) NOT NULL COMMENT '商户视角方向：CREDIT增加应结、DEBIT减少应结',
  `label_currency` char(3) NOT NULL COMMENT '当前动作标签币种；百分比基数币种',
  `label_amount` decimal(24,8) NOT NULL COMMENT '当前动作标签金额，始终非负',
  `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种ISO小数位',
  `fee_group_no` varchar(64) DEFAULT NULL COMMENT '同一规则一次收费的稳定组号；本金为空',
  `component_no` smallint NOT NULL COMMENT '费用组内稳定组件序号；本金固定1',
  `component_type` varchar(24) NOT NULL COMMENT 'PRINCIPAL、PERCENTAGE、FIXED、LIMIT_ADJUSTMENT、REVERSAL、ADJUSTMENT',
  `basis_currency` char(3) NOT NULL COMMENT '百分比基数或本金币种；固定费固定为USD',
  `basis_amount` decimal(24,8) NOT NULL COMMENT '百分比基数、本金或固定费配置金额，始终非负',
  `basis_currency_exponent` tinyint NOT NULL COMMENT '基础币种ISO小数位',
  `amount` decimal(24,8) NOT NULL COMMENT '当前原子组件按自身币种舍入后的权威金额，始终非负',
  `currency` char(3) NOT NULL COMMENT '当前原子组件原币种；清分阶段不转换',
  `currency_exponent` tinyint NOT NULL COMMENT '组件币种ISO小数位',
  `fee_plan_id` bigint DEFAULT NULL COMMENT '费用方案ID；本金可为空',
  `fee_plan_version_id` bigint DEFAULT NULL COMMENT '不可变费用版本ID；本金可为空',
  `fee_plan_version_no` int DEFAULT NULL COMMENT '费用版本号快照',
  `fee_rule_id` bigint DEFAULT NULL COMMENT '命中的费用规则ID',
  `fee_rule_tier_id` bigint DEFAULT NULL COMMENT '命中的阶梯ID',
  `charge_trigger` varchar(32) DEFAULT NULL COMMENT '收费触发条件快照',
  `fee_mode` varchar(16) DEFAULT NULL COMMENT 'STANDARD或TIER',
  `tier_period_key` char(6) DEFAULT NULL COMMENT '阶梯累计月份yyyyMM；非阶梯费用为空',
  `tier_metric` varchar(16) DEFAULT NULL COMMENT 'COUNT或AMOUNT；非阶梯费用为空',
  `tier_count_before` bigint DEFAULT NULL COMMENT '应用本动作前累计笔数',
  `tier_count_delta` bigint DEFAULT NULL COMMENT '本动作对该规则贡献笔数，当前为1',
  `tier_count_after` bigint DEFAULT NULL COMMENT '应用本动作后累计笔数',
  `tier_amount_usd_before` decimal(24,8) DEFAULT NULL COMMENT '应用本动作前按现有配置口径归一的USD累计基数',
  `tier_amount_usd_delta` decimal(24,8) DEFAULT NULL COMMENT '本动作按相同口径归一的USD计费基数',
  `tier_amount_usd_after` decimal(24,8) DEFAULT NULL COMMENT '应用本动作后的USD累计基数',
  `percentage_rate` decimal(12,8) DEFAULT NULL COMMENT '百分比费率快照，例如2.3表示2.3%',
  `fixed_amount_usd` decimal(24,8) DEFAULT NULL COMMENT '商户规则固定费USD快照',
  `minimum_amount_usd` decimal(24,8) DEFAULT NULL COMMENT '商户规则最低费USD快照',
  `maximum_amount_usd` decimal(24,8) DEFAULT NULL COMMENT '商户规则最高费USD快照',
  `limit_evaluation_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT 'NOT_REQUIRED、FINAL_AT_CLEARING、PENDING_SETTLEMENT_RATE',
  `applied_limit` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE、MINIMUM、MAXIMUM',
  `rounding_mode` varchar(16) NOT NULL COMMENT '最终明细舍入模式',
  `formula_snapshot` varchar(1000) NOT NULL COMMENT '可审计计算公式快照',
  `rule_snapshot_json` json DEFAULT NULL COMMENT '命中规则的规范化非敏感快照',
  `fee_snapshot_hash` char(64) DEFAULT NULL COMMENT '交易动作费用快照SHA-256',
  `settlement_eligible_date` date NOT NULL COMMENT '最早可进入结算批次的业务日期',
  `record_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、SUPERSEDED；冲正不修改原明细状态',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '动作交易业务时间，季度分片键',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '动作交易业务时间对应UTC时间',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '动作业务时区',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_clearing_detail_no` (`clearing_detail_no`),
  UNIQUE KEY `uk_clearing_revision_line` (`finance_state_id`,`clearing_revision`,`line_no`),
  UNIQUE KEY `uk_clearing_transaction_item` (`transaction_id`,`clearing_revision`,`item_code`),
  KEY `idx_clearing_transaction_time` (`transaction_id`,`transaction_date_time`,`clearing_revision`,`id`),
  KEY `idx_clearing_operation_time` (`operation_id`,`transaction_date_time`,`id`),
  KEY `idx_clearing_source` (`source_transaction_id`,`source_clearing_detail_no`,`transaction_date_time`),
  KEY `idx_clearing_source_settlement` (`source_settlement_result_item_no`,`transaction_date_time`,`id`),
  KEY `idx_clearing_rule` (`fee_plan_version_id`,`fee_rule_id`,`fee_rule_tier_id`),
  KEY `idx_clearing_settlement_eligible` (`merchant_id`,`record_status`,`settlement_eligible_date`,`currency`,`id`),
  KEY `idx_clearing_fee_group` (`fee_group_no`,`clearing_revision`,`component_no`,`id`),
  CONSTRAINT `chk_clearing_amount_tpl` CHECK (((`label_amount` >= 0) and (`basis_amount` >= 0) and (`amount` >= 0) and (`component_no` >= 1) and ((`percentage_rate` is null) or (`percentage_rate` >= 0)) and ((`fixed_amount_usd` is null) or (`fixed_amount_usd` >= 0)) and ((`minimum_amount_usd` is null) or (`minimum_amount_usd` >= 0)) and ((`maximum_amount_usd` is null) or (`maximum_amount_usd` >= 0)) and ((`minimum_amount_usd` is null) or (`maximum_amount_usd` is null) or (`minimum_amount_usd` <= `maximum_amount_usd`)))),
  CONSTRAINT `chk_clearing_direction_tpl` CHECK ((`direction` in (_utf8mb4'CREDIT',_utf8mb4'DEBIT'))),
  CONSTRAINT `chk_clearing_enum_tpl` CHECK (((`item_type` in (_utf8mb4'PRINCIPAL',_utf8mb4'PLATFORM_FEE',_utf8mb4'FEE_REVERSAL',_utf8mb4'ADJUSTMENT')) and (`component_type` in (_utf8mb4'PRINCIPAL',_utf8mb4'PERCENTAGE',_utf8mb4'FIXED',_utf8mb4'LIMIT_ADJUSTMENT',_utf8mb4'REVERSAL',_utf8mb4'ADJUSTMENT')) and (`applied_limit` in (_utf8mb4'NONE',_utf8mb4'MINIMUM',_utf8mb4'MAXIMUM')) and (`limit_evaluation_status` in (_utf8mb4'NOT_REQUIRED',_utf8mb4'FINAL_AT_CLEARING',_utf8mb4'PENDING_SETTLEMENT_RATE')) and (`rounding_mode` in (_utf8mb4'HALF_UP',_utf8mb4'HALF_EVEN',_utf8mb4'DOWN')) and (`risk_service_type` in (_utf8mb4'NONE',_utf8mb4'INTERNAL',_utf8mb4'EXTERNAL',_utf8mb4'THREE_DS')))),
  CONSTRAINT `chk_clearing_exponent_tpl` CHECK (((`label_currency_exponent` between 0 and 8) and (`basis_currency_exponent` between 0 and 8) and (`currency_exponent` between 0 and 8))),
  CONSTRAINT `chk_clearing_fee_currency_tpl` CHECK ((((`component_type` <> _utf8mb4'PRINCIPAL') or ((`fee_group_no` is null) and (`currency` = `basis_currency`))) and ((`component_type` = _utf8mb4'PRINCIPAL') or (`fee_group_no` is not null)) and ((`component_type` <> _utf8mb4'PERCENTAGE') or ((`basis_currency` = `label_currency`) and (`currency` = `label_currency`))) and ((`component_type` <> _utf8mb4'FIXED') or ((`currency` = _utf8mb4'USD') and (`basis_currency` = _utf8mb4'USD'))) and ((`component_type` <> _utf8mb4'LIMIT_ADJUSTMENT') or ((`currency` = _utf8mb4'USD') and (`basis_currency` = _utf8mb4'USD'))))),
  CONSTRAINT `chk_clearing_record_status_tpl` CHECK ((`record_status` in (_utf8mb4'ACTIVE',_utf8mb4'SUPERSEDED'))),
  CONSTRAINT `chk_clearing_revision_line_tpl` CHECK (((`clearing_revision` >= 1) and (`line_no` >= 1))),
  CONSTRAINT `chk_clearing_risk_type_tpl` CHECK ((((coalesce(`fee_category`,_utf8mb4'') = _utf8mb4'RISK_FEE') and (`risk_service_type` in (_utf8mb4'INTERNAL',_utf8mb4'EXTERNAL',_utf8mb4'THREE_DS'))) or ((coalesce(`fee_category`,_utf8mb4'') <> _utf8mb4'RISK_FEE') and (`risk_service_type` = _utf8mb4'NONE')))),
  CONSTRAINT `chk_clearing_tier_snapshot_tpl` CHECK (((`fee_mode` is null) or ((`fee_mode` = _utf8mb4'STANDARD') and (`tier_period_key` is null) and (`tier_metric` is null) and (`tier_count_before` is null) and (`tier_count_delta` is null) and (`tier_count_after` is null) and (`tier_amount_usd_before` is null) and (`tier_amount_usd_delta` is null) and (`tier_amount_usd_after` is null)) or ((`fee_mode` = _utf8mb4'TIER') and (`tier_metric` in (_utf8mb4'COUNT',_utf8mb4'AMOUNT')) and (`tier_period_key` is not null) and (`tier_count_before` >= 0) and (`tier_count_delta` = 1) and (`tier_count_after` = (`tier_count_before` + `tier_count_delta`)) and (`tier_amount_usd_before` >= 0) and (`tier_amount_usd_delta` >= 0) and (`tier_amount_usd_after` = (`tier_amount_usd_before` + `tier_amount_usd_delta`)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动作级不可变交易清分明细；只保存本金和费用，跟随当前动作季度分片';

-- ----------------------------
-- Table structure for transaction_clearing_detail_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_clearing_detail_202603`;
CREATE TABLE `transaction_clearing_detail_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID',
  `clearing_detail_no` varchar(64) NOT NULL COMMENT '清分明细号',
  `finance_state_id` varchar(64) NOT NULL COMMENT '所属清分汇总ID',
  `transaction_id` varchar(64) NOT NULL COMMENT '动作级平台交易号',
  `operation_id` varchar(64) NOT NULL COMMENT '交易生命周期关联号',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '退款、冲正、拒付等来源动作交易号',
  `source_clearing_detail_no` varchar(64) DEFAULT NULL COMMENT '冲正或返还所引用的原清分明细号',
  `source_settlement_result_item_no` varchar(64) DEFAULT NULL COMMENT '跨币种原费用已结算后返费所引用的实际收费结果明细号',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `payment_type` varchar(32) NOT NULL COMMENT '清分时冻结的支付类型，用于结算分组',
  `payment_method` varchar(32) NOT NULL COMMENT '清分时冻结的支付方式，用于结算分组',
  `transaction_type` varchar(32) NOT NULL COMMENT '交易动作类型',
  `clearing_revision` int NOT NULL COMMENT '所属清分修订号，从1递增',
  `line_no` int NOT NULL COMMENT '同一修订内稳定明细序号，从1递增',
  `item_type` varchar(32) NOT NULL COMMENT 'PRINCIPAL、PLATFORM_FEE、FEE_REVERSAL、ADJUSTMENT；禁止保存保证金',
  `fee_category` varchar(32) DEFAULT NULL COMMENT '交易费、内外风控费、3DS费、退款费、换汇费、拒付费等；本金可为空',
  `risk_service_type` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '风控费用细分：INTERNAL、EXTERNAL、THREE_DS；非风控费用为NONE',
  `item_code` varchar(64) NOT NULL COMMENT '修订内稳定业务项编码，例如PRINCIPAL或FEE:TRANSACTION_FEE:ruleId',
  `item_name` varchar(128) NOT NULL COMMENT '费用或本金项目名称快照',
  `direction` varchar(8) NOT NULL COMMENT '商户视角方向：CREDIT增加应结、DEBIT减少应结',
  `label_currency` char(3) NOT NULL COMMENT '当前动作标签币种；百分比基数币种',
  `label_amount` decimal(24,8) NOT NULL COMMENT '当前动作标签金额，始终非负',
  `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种ISO小数位',
  `fee_group_no` varchar(64) DEFAULT NULL COMMENT '同一规则一次收费的稳定组号；本金为空',
  `component_no` smallint NOT NULL COMMENT '费用组内稳定组件序号；本金固定1',
  `component_type` varchar(24) NOT NULL COMMENT 'PRINCIPAL、PERCENTAGE、FIXED、LIMIT_ADJUSTMENT、REVERSAL、ADJUSTMENT',
  `basis_currency` char(3) NOT NULL COMMENT '百分比基数或本金币种；固定费固定为USD',
  `basis_amount` decimal(24,8) NOT NULL COMMENT '百分比基数、本金或固定费配置金额，始终非负',
  `basis_currency_exponent` tinyint NOT NULL COMMENT '基础币种ISO小数位',
  `amount` decimal(24,8) NOT NULL COMMENT '当前原子组件按自身币种舍入后的权威金额，始终非负',
  `currency` char(3) NOT NULL COMMENT '当前原子组件原币种；清分阶段不转换',
  `currency_exponent` tinyint NOT NULL COMMENT '组件币种ISO小数位',
  `fee_plan_id` bigint DEFAULT NULL COMMENT '费用方案ID；本金可为空',
  `fee_plan_version_id` bigint DEFAULT NULL COMMENT '不可变费用版本ID；本金可为空',
  `fee_plan_version_no` int DEFAULT NULL COMMENT '费用版本号快照',
  `fee_rule_id` bigint DEFAULT NULL COMMENT '命中的费用规则ID',
  `fee_rule_tier_id` bigint DEFAULT NULL COMMENT '命中的阶梯ID',
  `charge_trigger` varchar(32) DEFAULT NULL COMMENT '收费触发条件快照',
  `fee_mode` varchar(16) DEFAULT NULL COMMENT 'STANDARD或TIER',
  `tier_period_key` char(6) DEFAULT NULL COMMENT '阶梯累计月份yyyyMM；非阶梯费用为空',
  `tier_metric` varchar(16) DEFAULT NULL COMMENT 'COUNT或AMOUNT；非阶梯费用为空',
  `tier_count_before` bigint DEFAULT NULL COMMENT '应用本动作前累计笔数',
  `tier_count_delta` bigint DEFAULT NULL COMMENT '本动作对该规则贡献笔数，当前为1',
  `tier_count_after` bigint DEFAULT NULL COMMENT '应用本动作后累计笔数',
  `tier_amount_usd_before` decimal(24,8) DEFAULT NULL COMMENT '应用本动作前按现有配置口径归一的USD累计基数',
  `tier_amount_usd_delta` decimal(24,8) DEFAULT NULL COMMENT '本动作按相同口径归一的USD计费基数',
  `tier_amount_usd_after` decimal(24,8) DEFAULT NULL COMMENT '应用本动作后的USD累计基数',
  `percentage_rate` decimal(12,8) DEFAULT NULL COMMENT '百分比费率快照，例如2.3表示2.3%',
  `fixed_amount_usd` decimal(24,8) DEFAULT NULL COMMENT '商户规则固定费USD快照',
  `minimum_amount_usd` decimal(24,8) DEFAULT NULL COMMENT '商户规则最低费USD快照',
  `maximum_amount_usd` decimal(24,8) DEFAULT NULL COMMENT '商户规则最高费USD快照',
  `limit_evaluation_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT 'NOT_REQUIRED、FINAL_AT_CLEARING、PENDING_SETTLEMENT_RATE',
  `applied_limit` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE、MINIMUM、MAXIMUM',
  `rounding_mode` varchar(16) NOT NULL COMMENT '最终明细舍入模式',
  `formula_snapshot` varchar(1000) NOT NULL COMMENT '可审计计算公式快照',
  `rule_snapshot_json` json DEFAULT NULL COMMENT '命中规则的规范化非敏感快照',
  `fee_snapshot_hash` char(64) DEFAULT NULL COMMENT '交易动作费用快照SHA-256',
  `settlement_eligible_date` date NOT NULL COMMENT '最早可进入结算批次的业务日期',
  `record_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、SUPERSEDED；冲正不修改原明细状态',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '动作交易业务时间，季度分片键',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '动作交易业务时间对应UTC时间',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '动作业务时区',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_clearing_detail_no` (`clearing_detail_no`),
  UNIQUE KEY `uk_clearing_revision_line` (`finance_state_id`,`clearing_revision`,`line_no`),
  UNIQUE KEY `uk_clearing_transaction_item` (`transaction_id`,`clearing_revision`,`item_code`),
  KEY `idx_clearing_transaction_time` (`transaction_id`,`transaction_date_time`,`clearing_revision`,`id`),
  KEY `idx_clearing_operation_time` (`operation_id`,`transaction_date_time`,`id`),
  KEY `idx_clearing_source` (`source_transaction_id`,`source_clearing_detail_no`,`transaction_date_time`),
  KEY `idx_clearing_source_settlement` (`source_settlement_result_item_no`,`transaction_date_time`,`id`),
  KEY `idx_clearing_rule` (`fee_plan_version_id`,`fee_rule_id`,`fee_rule_tier_id`),
  KEY `idx_clearing_settlement_eligible` (`merchant_id`,`record_status`,`settlement_eligible_date`,`currency`,`id`),
  KEY `idx_clearing_fee_group` (`fee_group_no`,`clearing_revision`,`component_no`,`id`),
  CONSTRAINT `chk_clearing_amount_202603` CHECK (((`label_amount` >= 0) and (`basis_amount` >= 0) and (`amount` >= 0) and (`component_no` >= 1) and ((`percentage_rate` is null) or (`percentage_rate` >= 0)) and ((`fixed_amount_usd` is null) or (`fixed_amount_usd` >= 0)) and ((`minimum_amount_usd` is null) or (`minimum_amount_usd` >= 0)) and ((`maximum_amount_usd` is null) or (`maximum_amount_usd` >= 0)) and ((`minimum_amount_usd` is null) or (`maximum_amount_usd` is null) or (`minimum_amount_usd` <= `maximum_amount_usd`)))),
  CONSTRAINT `chk_clearing_direction_202603` CHECK ((`direction` in (_utf8mb4'CREDIT',_utf8mb4'DEBIT'))),
  CONSTRAINT `chk_clearing_enum_202603` CHECK (((`item_type` in (_utf8mb4'PRINCIPAL',_utf8mb4'PLATFORM_FEE',_utf8mb4'FEE_REVERSAL',_utf8mb4'ADJUSTMENT')) and (`component_type` in (_utf8mb4'PRINCIPAL',_utf8mb4'PERCENTAGE',_utf8mb4'FIXED',_utf8mb4'LIMIT_ADJUSTMENT',_utf8mb4'REVERSAL',_utf8mb4'ADJUSTMENT')) and (`applied_limit` in (_utf8mb4'NONE',_utf8mb4'MINIMUM',_utf8mb4'MAXIMUM')) and (`limit_evaluation_status` in (_utf8mb4'NOT_REQUIRED',_utf8mb4'FINAL_AT_CLEARING',_utf8mb4'PENDING_SETTLEMENT_RATE')) and (`rounding_mode` in (_utf8mb4'HALF_UP',_utf8mb4'HALF_EVEN',_utf8mb4'DOWN')) and (`risk_service_type` in (_utf8mb4'NONE',_utf8mb4'INTERNAL',_utf8mb4'EXTERNAL',_utf8mb4'THREE_DS')))),
  CONSTRAINT `chk_clearing_exponent_202603` CHECK (((`label_currency_exponent` between 0 and 8) and (`basis_currency_exponent` between 0 and 8) and (`currency_exponent` between 0 and 8))),
  CONSTRAINT `chk_clearing_fee_currency_202603` CHECK ((((`component_type` <> _utf8mb4'PRINCIPAL') or ((`fee_group_no` is null) and (`currency` = `basis_currency`))) and ((`component_type` = _utf8mb4'PRINCIPAL') or (`fee_group_no` is not null)) and ((`component_type` <> _utf8mb4'PERCENTAGE') or ((`basis_currency` = `label_currency`) and (`currency` = `label_currency`))) and ((`component_type` <> _utf8mb4'FIXED') or ((`currency` = _utf8mb4'USD') and (`basis_currency` = _utf8mb4'USD'))) and ((`component_type` <> _utf8mb4'LIMIT_ADJUSTMENT') or ((`currency` = _utf8mb4'USD') and (`basis_currency` = _utf8mb4'USD'))))),
  CONSTRAINT `chk_clearing_record_status_202603` CHECK ((`record_status` in (_utf8mb4'ACTIVE',_utf8mb4'SUPERSEDED'))),
  CONSTRAINT `chk_clearing_revision_line_202603` CHECK (((`clearing_revision` >= 1) and (`line_no` >= 1))),
  CONSTRAINT `chk_clearing_risk_type_202603` CHECK ((((coalesce(`fee_category`,_utf8mb4'') = _utf8mb4'RISK_FEE') and (`risk_service_type` in (_utf8mb4'INTERNAL',_utf8mb4'EXTERNAL',_utf8mb4'THREE_DS'))) or ((coalesce(`fee_category`,_utf8mb4'') <> _utf8mb4'RISK_FEE') and (`risk_service_type` = _utf8mb4'NONE')))),
  CONSTRAINT `chk_clearing_tier_snapshot_202603` CHECK (((`fee_mode` is null) or ((`fee_mode` = _utf8mb4'STANDARD') and (`tier_period_key` is null) and (`tier_metric` is null) and (`tier_count_before` is null) and (`tier_count_delta` is null) and (`tier_count_after` is null) and (`tier_amount_usd_before` is null) and (`tier_amount_usd_delta` is null) and (`tier_amount_usd_after` is null)) or ((`fee_mode` = _utf8mb4'TIER') and (`tier_metric` in (_utf8mb4'COUNT',_utf8mb4'AMOUNT')) and (`tier_period_key` is not null) and (`tier_count_before` >= 0) and (`tier_count_delta` = 1) and (`tier_count_after` = (`tier_count_before` + `tier_count_delta`)) and (`tier_amount_usd_before` >= 0) and (`tier_amount_usd_delta` >= 0) and (`tier_amount_usd_after` = (`tier_amount_usd_before` + `tier_amount_usd_delta`)))))
) ENGINE=InnoDB AUTO_INCREMENT=202603000000074463 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='2026年第3季度动作级交易清分明细';

-- ----------------------------
-- Table structure for transaction_clearing_detail_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_clearing_detail_202604`;
CREATE TABLE `transaction_clearing_detail_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID',
  `clearing_detail_no` varchar(64) NOT NULL COMMENT '清分明细号',
  `finance_state_id` varchar(64) NOT NULL COMMENT '所属清分汇总ID',
  `transaction_id` varchar(64) NOT NULL COMMENT '动作级平台交易号',
  `operation_id` varchar(64) NOT NULL COMMENT '交易生命周期关联号',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '退款、冲正、拒付等来源动作交易号',
  `source_clearing_detail_no` varchar(64) DEFAULT NULL COMMENT '冲正或返还所引用的原清分明细号',
  `source_settlement_result_item_no` varchar(64) DEFAULT NULL COMMENT '跨币种原费用已结算后返费所引用的实际收费结果明细号',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `payment_type` varchar(32) NOT NULL COMMENT '清分时冻结的支付类型，用于结算分组',
  `payment_method` varchar(32) NOT NULL COMMENT '清分时冻结的支付方式，用于结算分组',
  `transaction_type` varchar(32) NOT NULL COMMENT '交易动作类型',
  `clearing_revision` int NOT NULL COMMENT '所属清分修订号，从1递增',
  `line_no` int NOT NULL COMMENT '同一修订内稳定明细序号，从1递增',
  `item_type` varchar(32) NOT NULL COMMENT 'PRINCIPAL、PLATFORM_FEE、FEE_REVERSAL、ADJUSTMENT；禁止保存保证金',
  `fee_category` varchar(32) DEFAULT NULL COMMENT '交易费、内外风控费、3DS费、退款费、换汇费、拒付费等；本金可为空',
  `risk_service_type` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '风控费用细分：INTERNAL、EXTERNAL、THREE_DS；非风控费用为NONE',
  `item_code` varchar(64) NOT NULL COMMENT '修订内稳定业务项编码，例如PRINCIPAL或FEE:TRANSACTION_FEE:ruleId',
  `item_name` varchar(128) NOT NULL COMMENT '费用或本金项目名称快照',
  `direction` varchar(8) NOT NULL COMMENT '商户视角方向：CREDIT增加应结、DEBIT减少应结',
  `label_currency` char(3) NOT NULL COMMENT '当前动作标签币种；百分比基数币种',
  `label_amount` decimal(24,8) NOT NULL COMMENT '当前动作标签金额，始终非负',
  `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种ISO小数位',
  `fee_group_no` varchar(64) DEFAULT NULL COMMENT '同一规则一次收费的稳定组号；本金为空',
  `component_no` smallint NOT NULL COMMENT '费用组内稳定组件序号；本金固定1',
  `component_type` varchar(24) NOT NULL COMMENT 'PRINCIPAL、PERCENTAGE、FIXED、LIMIT_ADJUSTMENT、REVERSAL、ADJUSTMENT',
  `basis_currency` char(3) NOT NULL COMMENT '百分比基数或本金币种；固定费固定为USD',
  `basis_amount` decimal(24,8) NOT NULL COMMENT '百分比基数、本金或固定费配置金额，始终非负',
  `basis_currency_exponent` tinyint NOT NULL COMMENT '基础币种ISO小数位',
  `amount` decimal(24,8) NOT NULL COMMENT '当前原子组件按自身币种舍入后的权威金额，始终非负',
  `currency` char(3) NOT NULL COMMENT '当前原子组件原币种；清分阶段不转换',
  `currency_exponent` tinyint NOT NULL COMMENT '组件币种ISO小数位',
  `fee_plan_id` bigint DEFAULT NULL COMMENT '费用方案ID；本金可为空',
  `fee_plan_version_id` bigint DEFAULT NULL COMMENT '不可变费用版本ID；本金可为空',
  `fee_plan_version_no` int DEFAULT NULL COMMENT '费用版本号快照',
  `fee_rule_id` bigint DEFAULT NULL COMMENT '命中的费用规则ID',
  `fee_rule_tier_id` bigint DEFAULT NULL COMMENT '命中的阶梯ID',
  `charge_trigger` varchar(32) DEFAULT NULL COMMENT '收费触发条件快照',
  `fee_mode` varchar(16) DEFAULT NULL COMMENT 'STANDARD或TIER',
  `tier_period_key` char(6) DEFAULT NULL COMMENT '阶梯累计月份yyyyMM；非阶梯费用为空',
  `tier_metric` varchar(16) DEFAULT NULL COMMENT 'COUNT或AMOUNT；非阶梯费用为空',
  `tier_count_before` bigint DEFAULT NULL COMMENT '应用本动作前累计笔数',
  `tier_count_delta` bigint DEFAULT NULL COMMENT '本动作对该规则贡献笔数，当前为1',
  `tier_count_after` bigint DEFAULT NULL COMMENT '应用本动作后累计笔数',
  `tier_amount_usd_before` decimal(24,8) DEFAULT NULL COMMENT '应用本动作前按现有配置口径归一的USD累计基数',
  `tier_amount_usd_delta` decimal(24,8) DEFAULT NULL COMMENT '本动作按相同口径归一的USD计费基数',
  `tier_amount_usd_after` decimal(24,8) DEFAULT NULL COMMENT '应用本动作后的USD累计基数',
  `percentage_rate` decimal(12,8) DEFAULT NULL COMMENT '百分比费率快照，例如2.3表示2.3%',
  `fixed_amount_usd` decimal(24,8) DEFAULT NULL COMMENT '商户规则固定费USD快照',
  `minimum_amount_usd` decimal(24,8) DEFAULT NULL COMMENT '商户规则最低费USD快照',
  `maximum_amount_usd` decimal(24,8) DEFAULT NULL COMMENT '商户规则最高费USD快照',
  `limit_evaluation_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT 'NOT_REQUIRED、FINAL_AT_CLEARING、PENDING_SETTLEMENT_RATE',
  `applied_limit` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE、MINIMUM、MAXIMUM',
  `rounding_mode` varchar(16) NOT NULL COMMENT '最终明细舍入模式',
  `formula_snapshot` varchar(1000) NOT NULL COMMENT '可审计计算公式快照',
  `rule_snapshot_json` json DEFAULT NULL COMMENT '命中规则的规范化非敏感快照',
  `fee_snapshot_hash` char(64) DEFAULT NULL COMMENT '交易动作费用快照SHA-256',
  `settlement_eligible_date` date NOT NULL COMMENT '最早可进入结算批次的业务日期',
  `record_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、SUPERSEDED；冲正不修改原明细状态',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '动作交易业务时间，季度分片键',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '动作交易业务时间对应UTC时间',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '动作业务时区',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_clearing_detail_no` (`clearing_detail_no`),
  UNIQUE KEY `uk_clearing_revision_line` (`finance_state_id`,`clearing_revision`,`line_no`),
  UNIQUE KEY `uk_clearing_transaction_item` (`transaction_id`,`clearing_revision`,`item_code`),
  KEY `idx_clearing_transaction_time` (`transaction_id`,`transaction_date_time`,`clearing_revision`,`id`),
  KEY `idx_clearing_operation_time` (`operation_id`,`transaction_date_time`,`id`),
  KEY `idx_clearing_source` (`source_transaction_id`,`source_clearing_detail_no`,`transaction_date_time`),
  KEY `idx_clearing_source_settlement` (`source_settlement_result_item_no`,`transaction_date_time`,`id`),
  KEY `idx_clearing_rule` (`fee_plan_version_id`,`fee_rule_id`,`fee_rule_tier_id`),
  KEY `idx_clearing_settlement_eligible` (`merchant_id`,`record_status`,`settlement_eligible_date`,`currency`,`id`),
  KEY `idx_clearing_fee_group` (`fee_group_no`,`clearing_revision`,`component_no`,`id`),
  CONSTRAINT `chk_clearing_amount_202604` CHECK (((`label_amount` >= 0) and (`basis_amount` >= 0) and (`amount` >= 0) and (`component_no` >= 1) and ((`percentage_rate` is null) or (`percentage_rate` >= 0)) and ((`fixed_amount_usd` is null) or (`fixed_amount_usd` >= 0)) and ((`minimum_amount_usd` is null) or (`minimum_amount_usd` >= 0)) and ((`maximum_amount_usd` is null) or (`maximum_amount_usd` >= 0)) and ((`minimum_amount_usd` is null) or (`maximum_amount_usd` is null) or (`minimum_amount_usd` <= `maximum_amount_usd`)))),
  CONSTRAINT `chk_clearing_direction_202604` CHECK ((`direction` in (_utf8mb4'CREDIT',_utf8mb4'DEBIT'))),
  CONSTRAINT `chk_clearing_enum_202604` CHECK (((`item_type` in (_utf8mb4'PRINCIPAL',_utf8mb4'PLATFORM_FEE',_utf8mb4'FEE_REVERSAL',_utf8mb4'ADJUSTMENT')) and (`component_type` in (_utf8mb4'PRINCIPAL',_utf8mb4'PERCENTAGE',_utf8mb4'FIXED',_utf8mb4'LIMIT_ADJUSTMENT',_utf8mb4'REVERSAL',_utf8mb4'ADJUSTMENT')) and (`applied_limit` in (_utf8mb4'NONE',_utf8mb4'MINIMUM',_utf8mb4'MAXIMUM')) and (`limit_evaluation_status` in (_utf8mb4'NOT_REQUIRED',_utf8mb4'FINAL_AT_CLEARING',_utf8mb4'PENDING_SETTLEMENT_RATE')) and (`rounding_mode` in (_utf8mb4'HALF_UP',_utf8mb4'HALF_EVEN',_utf8mb4'DOWN')) and (`risk_service_type` in (_utf8mb4'NONE',_utf8mb4'INTERNAL',_utf8mb4'EXTERNAL',_utf8mb4'THREE_DS')))),
  CONSTRAINT `chk_clearing_exponent_202604` CHECK (((`label_currency_exponent` between 0 and 8) and (`basis_currency_exponent` between 0 and 8) and (`currency_exponent` between 0 and 8))),
  CONSTRAINT `chk_clearing_fee_currency_202604` CHECK ((((`component_type` <> _utf8mb4'PRINCIPAL') or ((`fee_group_no` is null) and (`currency` = `basis_currency`))) and ((`component_type` = _utf8mb4'PRINCIPAL') or (`fee_group_no` is not null)) and ((`component_type` <> _utf8mb4'PERCENTAGE') or ((`basis_currency` = `label_currency`) and (`currency` = `label_currency`))) and ((`component_type` <> _utf8mb4'FIXED') or ((`currency` = _utf8mb4'USD') and (`basis_currency` = _utf8mb4'USD'))) and ((`component_type` <> _utf8mb4'LIMIT_ADJUSTMENT') or ((`currency` = _utf8mb4'USD') and (`basis_currency` = _utf8mb4'USD'))))),
  CONSTRAINT `chk_clearing_record_status_202604` CHECK ((`record_status` in (_utf8mb4'ACTIVE',_utf8mb4'SUPERSEDED'))),
  CONSTRAINT `chk_clearing_revision_line_202604` CHECK (((`clearing_revision` >= 1) and (`line_no` >= 1))),
  CONSTRAINT `chk_clearing_risk_type_202604` CHECK ((((coalesce(`fee_category`,_utf8mb4'') = _utf8mb4'RISK_FEE') and (`risk_service_type` in (_utf8mb4'INTERNAL',_utf8mb4'EXTERNAL',_utf8mb4'THREE_DS'))) or ((coalesce(`fee_category`,_utf8mb4'') <> _utf8mb4'RISK_FEE') and (`risk_service_type` = _utf8mb4'NONE')))),
  CONSTRAINT `chk_clearing_tier_snapshot_202604` CHECK (((`fee_mode` is null) or ((`fee_mode` = _utf8mb4'STANDARD') and (`tier_period_key` is null) and (`tier_metric` is null) and (`tier_count_before` is null) and (`tier_count_delta` is null) and (`tier_count_after` is null) and (`tier_amount_usd_before` is null) and (`tier_amount_usd_delta` is null) and (`tier_amount_usd_after` is null)) or ((`fee_mode` = _utf8mb4'TIER') and (`tier_metric` in (_utf8mb4'COUNT',_utf8mb4'AMOUNT')) and (`tier_period_key` is not null) and (`tier_count_before` >= 0) and (`tier_count_delta` = 1) and (`tier_count_after` = (`tier_count_before` + `tier_count_delta`)) and (`tier_amount_usd_before` >= 0) and (`tier_amount_usd_delta` >= 0) and (`tier_amount_usd_after` = (`tier_amount_usd_before` + `tier_amount_usd_delta`)))))
) ENGINE=InnoDB AUTO_INCREMENT=202604000000255284 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='2026年第4季度动作级交易清分明细';

-- ----------------------------
-- Table structure for transaction_clearing_detail_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_clearing_detail_202701`;
CREATE TABLE `transaction_clearing_detail_202701` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID',
  `clearing_detail_no` varchar(64) NOT NULL COMMENT '清分明细号',
  `finance_state_id` varchar(64) NOT NULL COMMENT '所属清分汇总ID',
  `transaction_id` varchar(64) NOT NULL COMMENT '动作级平台交易号',
  `operation_id` varchar(64) NOT NULL COMMENT '交易生命周期关联号',
  `source_transaction_id` varchar(64) DEFAULT NULL COMMENT '退款、冲正、拒付等来源动作交易号',
  `source_clearing_detail_no` varchar(64) DEFAULT NULL COMMENT '冲正或返还所引用的原清分明细号',
  `source_settlement_result_item_no` varchar(64) DEFAULT NULL COMMENT '跨币种原费用已结算后返费所引用的实际收费结果明细号',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `payment_type` varchar(32) NOT NULL COMMENT '清分时冻结的支付类型，用于结算分组',
  `payment_method` varchar(32) NOT NULL COMMENT '清分时冻结的支付方式，用于结算分组',
  `transaction_type` varchar(32) NOT NULL COMMENT '交易动作类型',
  `clearing_revision` int NOT NULL COMMENT '所属清分修订号，从1递增',
  `line_no` int NOT NULL COMMENT '同一修订内稳定明细序号，从1递增',
  `item_type` varchar(32) NOT NULL COMMENT 'PRINCIPAL、PLATFORM_FEE、FEE_REVERSAL、ADJUSTMENT；禁止保存保证金',
  `fee_category` varchar(32) DEFAULT NULL COMMENT '交易费、内外风控费、3DS费、退款费、换汇费、拒付费等；本金可为空',
  `risk_service_type` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '风控费用细分：INTERNAL、EXTERNAL、THREE_DS；非风控费用为NONE',
  `item_code` varchar(64) NOT NULL COMMENT '修订内稳定业务项编码，例如PRINCIPAL或FEE:TRANSACTION_FEE:ruleId',
  `item_name` varchar(128) NOT NULL COMMENT '费用或本金项目名称快照',
  `direction` varchar(8) NOT NULL COMMENT '商户视角方向：CREDIT增加应结、DEBIT减少应结',
  `label_currency` char(3) NOT NULL COMMENT '当前动作标签币种；百分比基数币种',
  `label_amount` decimal(24,8) NOT NULL COMMENT '当前动作标签金额，始终非负',
  `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种ISO小数位',
  `fee_group_no` varchar(64) DEFAULT NULL COMMENT '同一规则一次收费的稳定组号；本金为空',
  `component_no` smallint NOT NULL COMMENT '费用组内稳定组件序号；本金固定1',
  `component_type` varchar(24) NOT NULL COMMENT 'PRINCIPAL、PERCENTAGE、FIXED、LIMIT_ADJUSTMENT、REVERSAL、ADJUSTMENT',
  `basis_currency` char(3) NOT NULL COMMENT '百分比基数或本金币种；固定费固定为USD',
  `basis_amount` decimal(24,8) NOT NULL COMMENT '百分比基数、本金或固定费配置金额，始终非负',
  `basis_currency_exponent` tinyint NOT NULL COMMENT '基础币种ISO小数位',
  `amount` decimal(24,8) NOT NULL COMMENT '当前原子组件按自身币种舍入后的权威金额，始终非负',
  `currency` char(3) NOT NULL COMMENT '当前原子组件原币种；清分阶段不转换',
  `currency_exponent` tinyint NOT NULL COMMENT '组件币种ISO小数位',
  `fee_plan_id` bigint DEFAULT NULL COMMENT '费用方案ID；本金可为空',
  `fee_plan_version_id` bigint DEFAULT NULL COMMENT '不可变费用版本ID；本金可为空',
  `fee_plan_version_no` int DEFAULT NULL COMMENT '费用版本号快照',
  `fee_rule_id` bigint DEFAULT NULL COMMENT '命中的费用规则ID',
  `fee_rule_tier_id` bigint DEFAULT NULL COMMENT '命中的阶梯ID',
  `charge_trigger` varchar(32) DEFAULT NULL COMMENT '收费触发条件快照',
  `fee_mode` varchar(16) DEFAULT NULL COMMENT 'STANDARD或TIER',
  `tier_period_key` char(6) DEFAULT NULL COMMENT '阶梯累计月份yyyyMM；非阶梯费用为空',
  `tier_metric` varchar(16) DEFAULT NULL COMMENT 'COUNT或AMOUNT；非阶梯费用为空',
  `tier_count_before` bigint DEFAULT NULL COMMENT '应用本动作前累计笔数',
  `tier_count_delta` bigint DEFAULT NULL COMMENT '本动作对该规则贡献笔数，当前为1',
  `tier_count_after` bigint DEFAULT NULL COMMENT '应用本动作后累计笔数',
  `tier_amount_usd_before` decimal(24,8) DEFAULT NULL COMMENT '应用本动作前按现有配置口径归一的USD累计基数',
  `tier_amount_usd_delta` decimal(24,8) DEFAULT NULL COMMENT '本动作按相同口径归一的USD计费基数',
  `tier_amount_usd_after` decimal(24,8) DEFAULT NULL COMMENT '应用本动作后的USD累计基数',
  `percentage_rate` decimal(12,8) DEFAULT NULL COMMENT '百分比费率快照，例如2.3表示2.3%',
  `fixed_amount_usd` decimal(24,8) DEFAULT NULL COMMENT '商户规则固定费USD快照',
  `minimum_amount_usd` decimal(24,8) DEFAULT NULL COMMENT '商户规则最低费USD快照',
  `maximum_amount_usd` decimal(24,8) DEFAULT NULL COMMENT '商户规则最高费USD快照',
  `limit_evaluation_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT 'NOT_REQUIRED、FINAL_AT_CLEARING、PENDING_SETTLEMENT_RATE',
  `applied_limit` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE、MINIMUM、MAXIMUM',
  `rounding_mode` varchar(16) NOT NULL COMMENT '最终明细舍入模式',
  `formula_snapshot` varchar(1000) NOT NULL COMMENT '可审计计算公式快照',
  `rule_snapshot_json` json DEFAULT NULL COMMENT '命中规则的规范化非敏感快照',
  `fee_snapshot_hash` char(64) DEFAULT NULL COMMENT '交易动作费用快照SHA-256',
  `settlement_eligible_date` date NOT NULL COMMENT '最早可进入结算批次的业务日期',
  `record_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、SUPERSEDED；冲正不修改原明细状态',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '动作交易业务时间，季度分片键',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '动作交易业务时间对应UTC时间',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '动作业务时区',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_clearing_detail_no` (`clearing_detail_no`),
  UNIQUE KEY `uk_clearing_revision_line` (`finance_state_id`,`clearing_revision`,`line_no`),
  UNIQUE KEY `uk_clearing_transaction_item` (`transaction_id`,`clearing_revision`,`item_code`),
  KEY `idx_clearing_transaction_time` (`transaction_id`,`transaction_date_time`,`clearing_revision`,`id`),
  KEY `idx_clearing_operation_time` (`operation_id`,`transaction_date_time`,`id`),
  KEY `idx_clearing_source` (`source_transaction_id`,`source_clearing_detail_no`,`transaction_date_time`),
  KEY `idx_clearing_source_settlement` (`source_settlement_result_item_no`,`transaction_date_time`,`id`),
  KEY `idx_clearing_rule` (`fee_plan_version_id`,`fee_rule_id`,`fee_rule_tier_id`),
  KEY `idx_clearing_settlement_eligible` (`merchant_id`,`record_status`,`settlement_eligible_date`,`currency`,`id`),
  KEY `idx_clearing_fee_group` (`fee_group_no`,`clearing_revision`,`component_no`,`id`),
  CONSTRAINT `transaction_clearing_detail_202701_chk_1` CHECK (((`label_amount` >= 0) and (`basis_amount` >= 0) and (`amount` >= 0) and (`component_no` >= 1) and ((`percentage_rate` is null) or (`percentage_rate` >= 0)) and ((`fixed_amount_usd` is null) or (`fixed_amount_usd` >= 0)) and ((`minimum_amount_usd` is null) or (`minimum_amount_usd` >= 0)) and ((`maximum_amount_usd` is null) or (`maximum_amount_usd` >= 0)) and ((`minimum_amount_usd` is null) or (`maximum_amount_usd` is null) or (`minimum_amount_usd` <= `maximum_amount_usd`)))),
  CONSTRAINT `transaction_clearing_detail_202701_chk_2` CHECK ((`direction` in (_utf8mb4'CREDIT',_utf8mb4'DEBIT'))),
  CONSTRAINT `transaction_clearing_detail_202701_chk_3` CHECK (((`item_type` in (_utf8mb4'PRINCIPAL',_utf8mb4'PLATFORM_FEE',_utf8mb4'FEE_REVERSAL',_utf8mb4'ADJUSTMENT')) and (`component_type` in (_utf8mb4'PRINCIPAL',_utf8mb4'PERCENTAGE',_utf8mb4'FIXED',_utf8mb4'LIMIT_ADJUSTMENT',_utf8mb4'REVERSAL',_utf8mb4'ADJUSTMENT')) and (`applied_limit` in (_utf8mb4'NONE',_utf8mb4'MINIMUM',_utf8mb4'MAXIMUM')) and (`limit_evaluation_status` in (_utf8mb4'NOT_REQUIRED',_utf8mb4'FINAL_AT_CLEARING',_utf8mb4'PENDING_SETTLEMENT_RATE')) and (`rounding_mode` in (_utf8mb4'HALF_UP',_utf8mb4'HALF_EVEN',_utf8mb4'DOWN')) and (`risk_service_type` in (_utf8mb4'NONE',_utf8mb4'INTERNAL',_utf8mb4'EXTERNAL',_utf8mb4'THREE_DS')))),
  CONSTRAINT `transaction_clearing_detail_202701_chk_4` CHECK (((`label_currency_exponent` between 0 and 8) and (`basis_currency_exponent` between 0 and 8) and (`currency_exponent` between 0 and 8))),
  CONSTRAINT `transaction_clearing_detail_202701_chk_5` CHECK ((((`component_type` <> _utf8mb4'PRINCIPAL') or ((`fee_group_no` is null) and (`currency` = `basis_currency`))) and ((`component_type` = _utf8mb4'PRINCIPAL') or (`fee_group_no` is not null)) and ((`component_type` <> _utf8mb4'PERCENTAGE') or ((`basis_currency` = `label_currency`) and (`currency` = `label_currency`))) and ((`component_type` <> _utf8mb4'FIXED') or ((`currency` = _utf8mb4'USD') and (`basis_currency` = _utf8mb4'USD'))) and ((`component_type` <> _utf8mb4'LIMIT_ADJUSTMENT') or ((`currency` = _utf8mb4'USD') and (`basis_currency` = _utf8mb4'USD'))))),
  CONSTRAINT `transaction_clearing_detail_202701_chk_6` CHECK ((`record_status` in (_utf8mb4'ACTIVE',_utf8mb4'SUPERSEDED'))),
  CONSTRAINT `transaction_clearing_detail_202701_chk_7` CHECK (((`clearing_revision` >= 1) and (`line_no` >= 1))),
  CONSTRAINT `transaction_clearing_detail_202701_chk_8` CHECK ((((coalesce(`fee_category`,_utf8mb4'') = _utf8mb4'RISK_FEE') and (`risk_service_type` in (_utf8mb4'INTERNAL',_utf8mb4'EXTERNAL',_utf8mb4'THREE_DS'))) or ((coalesce(`fee_category`,_utf8mb4'') <> _utf8mb4'RISK_FEE') and (`risk_service_type` = _utf8mb4'NONE')))),
  CONSTRAINT `transaction_clearing_detail_202701_chk_9` CHECK (((`fee_mode` is null) or ((`fee_mode` = _utf8mb4'STANDARD') and (`tier_period_key` is null) and (`tier_metric` is null) and (`tier_count_before` is null) and (`tier_count_delta` is null) and (`tier_count_after` is null) and (`tier_amount_usd_before` is null) and (`tier_amount_usd_delta` is null) and (`tier_amount_usd_after` is null)) or ((`fee_mode` = _utf8mb4'TIER') and (`tier_metric` in (_utf8mb4'COUNT',_utf8mb4'AMOUNT')) and (`tier_period_key` is not null) and (`tier_count_before` >= 0) and (`tier_count_delta` = 1) and (`tier_count_after` = (`tier_count_before` + `tier_count_delta`)) and (`tier_amount_usd_before` >= 0) and (`tier_amount_usd_delta` >= 0) and (`tier_amount_usd_after` = (`tier_amount_usd_before` + `tier_amount_usd_delta`)))))
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动作级不可变交易清分明细；只保存本金和费用，跟随当前动作季度分片';

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
-- Table structure for transaction_currency_conversion_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_currency_conversion_202701`;
CREATE TABLE `transaction_currency_conversion_202701` (
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存DCC、渠道币种转换EDC、结算换汇、退款换汇等汇率报价、换算金额和舍入规则快照。';

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
  `event_status` varchar(32) NOT NULL COMMENT '事件发布状态：INIT、SENT、FAILED、CLOSED。',
  `topic` varchar(128) NOT NULL COMMENT 'RocketMQ Topic。',
  `tag` varchar(128) DEFAULT NULL COMMENT 'RocketMQ Tag。',
  `message_key` varchar(128) NOT NULL COMMENT 'MQ消息Key，下游消费幂等使用。',
  `message_group` varchar(128) DEFAULT NULL COMMENT '顺序消息分组键，如 transaction_id。',
  `delivery_mode` varchar(16) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO、NORMAL、ORDERLY、SCHEDULED；AUTO兼容历史message_group逻辑',
  `deliver_at` datetime(3) DEFAULT NULL COMMENT 'SCHEDULED消息的Broker最早投递UTC时间；其它模式为空',
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
  KEY `idx_aggregate` (`aggregate_type`,`aggregate_no`),
  KEY `idx_transaction_event` (`transaction_id`,`transaction_date_time`,`event_time`),
  KEY `idx_event_status_update` (`event_status`,`update_time`),
  KEY `idx_delivery_mode_time` (`delivery_mode`,`deliver_at`,`transaction_date_time`,`id`),
  CONSTRAINT `chk_event_delivery_mode_tpl` CHECK (((`delivery_mode` in (_utf8mb4'AUTO',_utf8mb4'NORMAL',_utf8mb4'ORDERLY',_utf8mb4'SCHEDULED')) and ((`delivery_mode` <> _utf8mb4'ORDERLY') or (`message_group` is not null)) and ((`delivery_mode` <> _utf8mb4'SCHEDULED') or (`deliver_at` is not null)) and ((`delivery_mode` = _utf8mb4'SCHEDULED') or (`deliver_at` is null))))
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
  `event_status` varchar(32) NOT NULL COMMENT '事件发布状态：INIT、SENT、FAILED、CLOSED。',
  `topic` varchar(128) NOT NULL COMMENT 'RocketMQ Topic。',
  `tag` varchar(128) DEFAULT NULL COMMENT 'RocketMQ Tag。',
  `message_key` varchar(128) NOT NULL COMMENT 'MQ消息Key，下游消费幂等使用。',
  `message_group` varchar(128) DEFAULT NULL COMMENT '顺序消息分组键，如 transaction_id。',
  `delivery_mode` varchar(16) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO、NORMAL、ORDERLY、SCHEDULED；AUTO兼容历史message_group逻辑',
  `deliver_at` datetime(3) DEFAULT NULL COMMENT 'SCHEDULED消息的Broker最早投递UTC时间；其它模式为空',
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
  KEY `idx_aggregate` (`aggregate_type`,`aggregate_no`),
  KEY `idx_transaction_event` (`transaction_id`,`transaction_date_time`,`event_time`),
  KEY `idx_event_status_update` (`event_status`,`update_time`),
  KEY `idx_delivery_mode_time` (`delivery_mode`,`deliver_at`,`transaction_date_time`,`id`),
  CONSTRAINT `chk_event_delivery_mode_202603` CHECK (((`delivery_mode` in (_utf8mb4'AUTO',_utf8mb4'NORMAL',_utf8mb4'ORDERLY',_utf8mb4'SCHEDULED')) and ((`delivery_mode` <> _utf8mb4'ORDERLY') or (`message_group` is not null)) and ((`delivery_mode` <> _utf8mb4'SCHEDULED') or (`deliver_at` is not null)) and ((`delivery_mode` = _utf8mb4'SCHEDULED') or (`deliver_at` is null))))
) ENGINE=InnoDB AUTO_INCREMENT=202603000000071462 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易本地事务内写入事件，事务提交后可靠投递 RocketMQ。';

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
  `event_status` varchar(32) NOT NULL COMMENT '事件发布状态：INIT、SENT、FAILED、CLOSED。',
  `topic` varchar(128) NOT NULL COMMENT 'RocketMQ Topic。',
  `tag` varchar(128) DEFAULT NULL COMMENT 'RocketMQ Tag。',
  `message_key` varchar(128) NOT NULL COMMENT 'MQ消息Key，下游消费幂等使用。',
  `message_group` varchar(128) DEFAULT NULL COMMENT '顺序消息分组键，如 transaction_id。',
  `delivery_mode` varchar(16) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO、NORMAL、ORDERLY、SCHEDULED；AUTO兼容历史message_group逻辑',
  `deliver_at` datetime(3) DEFAULT NULL COMMENT 'SCHEDULED消息的Broker最早投递UTC时间；其它模式为空',
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
  KEY `idx_aggregate` (`aggregate_type`,`aggregate_no`),
  KEY `idx_transaction_event` (`transaction_id`,`transaction_date_time`,`event_time`),
  KEY `idx_event_status_update` (`event_status`,`update_time`),
  KEY `idx_delivery_mode_time` (`delivery_mode`,`deliver_at`,`transaction_date_time`,`id`),
  CONSTRAINT `chk_event_delivery_mode_202604` CHECK (((`delivery_mode` in (_utf8mb4'AUTO',_utf8mb4'NORMAL',_utf8mb4'ORDERLY',_utf8mb4'SCHEDULED')) and ((`delivery_mode` <> _utf8mb4'ORDERLY') or (`message_group` is not null)) and ((`delivery_mode` <> _utf8mb4'SCHEDULED') or (`deliver_at` is not null)) and ((`delivery_mode` = _utf8mb4'SCHEDULED') or (`deliver_at` is null))))
) ENGINE=InnoDB AUTO_INCREMENT=202604000000268148 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易本地事务内写入事件，事务提交后可靠投递 RocketMQ。';

-- ----------------------------
-- Table structure for transaction_event_outbox_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_event_outbox_202701`;
CREATE TABLE `transaction_event_outbox_202701` (
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
  `event_status` varchar(32) NOT NULL COMMENT '事件发布状态：INIT、SENT、FAILED、CLOSED。',
  `topic` varchar(128) NOT NULL COMMENT 'RocketMQ Topic。',
  `tag` varchar(128) DEFAULT NULL COMMENT 'RocketMQ Tag。',
  `message_key` varchar(128) NOT NULL COMMENT 'MQ消息Key，下游消费幂等使用。',
  `message_group` varchar(128) DEFAULT NULL COMMENT '顺序消息分组键，如 transaction_id。',
  `delivery_mode` varchar(16) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO、NORMAL、ORDERLY、SCHEDULED；AUTO兼容历史message_group逻辑',
  `deliver_at` datetime(3) DEFAULT NULL COMMENT 'SCHEDULED消息的Broker最早投递UTC时间；其它模式为空',
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
  KEY `idx_aggregate` (`aggregate_type`,`aggregate_no`),
  KEY `idx_transaction_event` (`transaction_id`,`transaction_date_time`,`event_time`),
  KEY `idx_event_status_update` (`event_status`,`update_time`),
  KEY `idx_delivery_mode_time` (`delivery_mode`,`deliver_at`,`transaction_date_time`,`id`),
  CONSTRAINT `transaction_event_outbox_202701_chk_1` CHECK (((`delivery_mode` in (_utf8mb4'AUTO',_utf8mb4'NORMAL',_utf8mb4'ORDERLY',_utf8mb4'SCHEDULED')) and ((`delivery_mode` <> _utf8mb4'ORDERLY') or (`message_group` is not null)) and ((`delivery_mode` <> _utf8mb4'SCHEDULED') or (`deliver_at` is not null)) and ((`delivery_mode` = _utf8mb4'SCHEDULED') or (`deliver_at` is null))))
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易本地事务内写入事件，事务提交后可靠投递 RocketMQ。';

-- ----------------------------
-- Table structure for transaction_finance_state
-- ----------------------------
DROP TABLE IF EXISTS `transaction_finance_state`;
CREATE TABLE `transaction_finance_state` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `finance_state_id` varchar(64) NOT NULL COMMENT '财务状态ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) NOT NULL COMMENT '交易动作ID。',
  `merchant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '平台商户号；存量回填完成后改为NOT NULL',
  `source_transaction_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '退款、冲正、拒付等来源动作交易号',
  `label_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '动作标签币种；生产清分费用和保证金的权威币种',
  `transaction_type` varchar(32) NOT NULL COMMENT '交易类型。',
  `clearing_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NOT_CLEARED' COMMENT '清分状态：NOT_CLEARED、PENDING、PROCESSING、WAITING_SOURCE、FAILED、MANUAL_REVIEW、CLEARED、NOT_REQUIRED',
  `clearing_revision` int NOT NULL DEFAULT '0' COMMENT '当前有效清分修订号，从1递增',
  `clearing_trigger_event_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '首次触发清分的终态事件号',
  `clearing_request_time` datetime(3) DEFAULT NULL COMMENT '首次接收清分请求时间',
  `clearing_start_time` datetime(3) DEFAULT NULL COMMENT '最近一次开始计算时间',
  `clearing_complete_time` datetime(3) DEFAULT NULL COMMENT '当前修订清分完成时间',
  `processing_owner` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'PROCESSING处理实例和线程租约标识',
  `processing_deadline` datetime(3) DEFAULT NULL COMMENT 'PROCESSING租约到期时间',
  `clearing_retry_count` int NOT NULL DEFAULT '0' COMMENT '清分业务重试次数',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下一次允许清分重试时间',
  `last_failure_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最近一次清分失败码',
  `last_failure_message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最近一次清分失败摘要，不保存请求或配置正文',
  `fee_plan_id` bigint DEFAULT NULL COMMENT '清分采用的费用方案ID',
  `fee_plan_version_id` bigint DEFAULT NULL COMMENT '清分采用的不可变费用版本ID',
  `fee_plan_version_no` int DEFAULT NULL COMMENT '清分采用的费用版本号',
  `fee_snapshot_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '实际采用的规范化费用快照SHA-256',
  `gross_label_amount` decimal(24,8) DEFAULT NULL COMMENT '标签币种下有符号毛本金',
  `fee_component_currency_count` smallint NOT NULL DEFAULT '0' COMMENT '当前修订费用组件币种数',
  `fee_evaluation_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'FINAL_AT_CLEARING或PENDING_SETTLEMENT_RATE',
  `settlement_status` varchar(32) NOT NULL DEFAULT 'NOT_SETTLED' COMMENT '结算状态。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '结算币种。',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '锁定结算直汇率；清分不得写入',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '动作最终结算金额；清分不得写入',
  `settlement_fee_amount` decimal(24,8) DEFAULT NULL COMMENT '历史兼容列；清分不得写入',
  `settlement_date` date DEFAULT NULL COMMENT '结算业务日期。',
  `settlement_cycle` varchar(16) DEFAULT NULL COMMENT '结算周期，如 T+0、T+1、T+2。',
  `settlement_eligible_date` date DEFAULT NULL COMMENT '满足结算周期后的最早可结算业务日期',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '最近一次结算或冲正批次号',
  `reconciliation_status` varchar(32) NOT NULL DEFAULT 'NOT_RECONCILED' COMMENT '对账状态。',
  `reconciliation_date` date DEFAULT NULL COMMENT '对账业务日期。',
  `reconciliation_batch_no` varchar(64) DEFAULT NULL COMMENT '对账批次号。',
  `accounting_status` varchar(32) NOT NULL DEFAULT 'NOT_ACCOUNTED' COMMENT '入账状态。',
  `accounting_time` datetime(3) DEFAULT NULL COMMENT '入账时间。',
  `channel_fee_currency` char(3) DEFAULT NULL COMMENT '渠道手续费币种。',
  `channel_fee_amount` decimal(24,8) DEFAULT NULL COMMENT '已确认渠道成本；清分一期为空',
  `platform_fee_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '平台费用币种；生产清分必须等于label_currency',
  `platform_fee_amount` decimal(24,8) DEFAULT NULL COMMENT '仅标签币种费用组件合计；异币种组件不计入',
  `fee_reversal_amount` decimal(24,8) DEFAULT NULL COMMENT '当前清分修订返还或冲回的标签币种平台费用正数合计',
  `merchant_receivable_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '商户应收币种；生产清分必须等于label_currency',
  `merchant_receivable_amount` decimal(24,8) DEFAULT NULL COMMENT '仅全部费用已在标签币种求值时可用，否则为空',
  `reserve_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '保证金币种；生产清分必须等于label_currency',
  `reserve_amount` decimal(24,8) DEFAULT NULL COMMENT '当前动作标签币种保证金扣留合计',
  `reserve_reversal_amount` decimal(24,8) DEFAULT NULL COMMENT '当前动作标签币种保证金返还或释放正数合计',
  `expected_reserve_release_date` date DEFAULT NULL COMMENT '预计保证金释放业务日期',
  `net_settlement_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '动作清分净额币种；生产清分必须等于label_currency',
  `net_settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '历史查询投影；存在异币种费用时为空',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_state_id` (`finance_state_id`),
  UNIQUE KEY `uk_transaction_finance` (`transaction_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_settlement_status_time` (`settlement_status`,`transaction_date_time`),
  KEY `idx_reconciliation_status_time` (`reconciliation_status`,`transaction_date_time`),
  KEY `idx_accounting_status_time` (`accounting_status`,`transaction_date_time`),
  KEY `idx_operation_finance_time` (`operation_id`,`transaction_date_time`,`id`),
  KEY `idx_clearing_retry` (`clearing_status`,`next_retry_time`,`transaction_date_time`,`id`),
  KEY `idx_clearing_lease` (`clearing_status`,`processing_deadline`,`transaction_date_time`,`id`),
  KEY `idx_merchant_settlement_eligible` (`merchant_id`,`settlement_currency`,`clearing_status`,`settlement_status`,`settlement_eligible_date`,`id`),
  KEY `idx_finance_admin_time` (`deleted`,`transaction_date_time`,`id`),
  KEY `idx_finance_admin_merchant_time` (`merchant_id`,`deleted`,`transaction_date_time`,`id`),
  KEY `idx_finance_admin_status_time` (`clearing_status`,`deleted`,`transaction_date_time`,`id`),
  CONSTRAINT `chk_finance_clearing_revision_tpl` CHECK ((`clearing_revision` >= 0)),
  CONSTRAINT `chk_finance_clearing_status_tpl` CHECK ((`clearing_status` in (_utf8mb4'NOT_CLEARED',_utf8mb4'PENDING',_utf8mb4'PROCESSING',_utf8mb4'WAITING_SOURCE',_utf8mb4'FAILED',_utf8mb4'MANUAL_REVIEW',_utf8mb4'CLEARED',_utf8mb4'NOT_REQUIRED'))),
  CONSTRAINT `chk_finance_fee_evaluation_tpl` CHECK (((`fee_component_currency_count` >= 0) and ((`fee_evaluation_status` is null) or (`fee_evaluation_status` in (_utf8mb4'FINAL_AT_CLEARING',_utf8mb4'PENDING_SETTLEMENT_RATE'))))),
  CONSTRAINT `chk_finance_label_currency_tpl` CHECK (((`label_currency` is null) or (((`platform_fee_currency` is null) or (`platform_fee_currency` = `label_currency`)) and ((`merchant_receivable_currency` is null) or (`merchant_receivable_currency` = `label_currency`)) and ((`reserve_currency` is null) or (`reserve_currency` = `label_currency`)) and ((`net_settlement_currency` is null) or (`net_settlement_currency` = `label_currency`))))),
  CONSTRAINT `chk_finance_nonnegative_summary_tpl` CHECK ((((`platform_fee_amount` is null) or (`platform_fee_amount` >= 0)) and ((`fee_reversal_amount` is null) or (`fee_reversal_amount` >= 0)) and ((`reserve_amount` is null) or (`reserve_amount` >= 0)) and ((`reserve_reversal_amount` is null) or (`reserve_reversal_amount` >= 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动作级清分汇总及结算、对账、账务当前状态；transaction_id唯一，operation_id用于生命周期聚合';

-- ----------------------------
-- Table structure for transaction_finance_state_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_finance_state_202603`;
CREATE TABLE `transaction_finance_state_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `finance_state_id` varchar(64) NOT NULL COMMENT '财务状态ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) NOT NULL COMMENT '交易动作ID。',
  `merchant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '平台商户号；存量回填完成后改为NOT NULL',
  `source_transaction_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '退款、冲正、拒付等来源动作交易号',
  `label_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '动作标签币种；生产清分费用和保证金的权威币种',
  `transaction_type` varchar(32) NOT NULL COMMENT '交易类型。',
  `clearing_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NOT_CLEARED' COMMENT '清分状态：NOT_CLEARED、PENDING、PROCESSING、WAITING_SOURCE、FAILED、MANUAL_REVIEW、CLEARED、NOT_REQUIRED',
  `clearing_revision` int NOT NULL DEFAULT '0' COMMENT '当前有效清分修订号，从1递增',
  `clearing_trigger_event_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '首次触发清分的终态事件号',
  `clearing_request_time` datetime(3) DEFAULT NULL COMMENT '首次接收清分请求时间',
  `clearing_start_time` datetime(3) DEFAULT NULL COMMENT '最近一次开始计算时间',
  `clearing_complete_time` datetime(3) DEFAULT NULL COMMENT '当前修订清分完成时间',
  `processing_owner` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'PROCESSING处理实例和线程租约标识',
  `processing_deadline` datetime(3) DEFAULT NULL COMMENT 'PROCESSING租约到期时间',
  `clearing_retry_count` int NOT NULL DEFAULT '0' COMMENT '清分业务重试次数',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下一次允许清分重试时间',
  `last_failure_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最近一次清分失败码',
  `last_failure_message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最近一次清分失败摘要，不保存请求或配置正文',
  `fee_plan_id` bigint DEFAULT NULL COMMENT '清分采用的费用方案ID',
  `fee_plan_version_id` bigint DEFAULT NULL COMMENT '清分采用的不可变费用版本ID',
  `fee_plan_version_no` int DEFAULT NULL COMMENT '清分采用的费用版本号',
  `fee_snapshot_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '实际采用的规范化费用快照SHA-256',
  `gross_label_amount` decimal(24,8) DEFAULT NULL COMMENT '标签币种下有符号毛本金',
  `fee_component_currency_count` smallint NOT NULL DEFAULT '0' COMMENT '当前修订费用组件币种数',
  `fee_evaluation_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'FINAL_AT_CLEARING或PENDING_SETTLEMENT_RATE',
  `settlement_status` varchar(32) NOT NULL DEFAULT 'NOT_SETTLED' COMMENT '结算状态。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '结算币种。',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '锁定结算直汇率；清分不得写入',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '动作最终结算金额；清分不得写入',
  `settlement_fee_amount` decimal(24,8) DEFAULT NULL COMMENT '历史兼容列；清分不得写入',
  `settlement_date` date DEFAULT NULL COMMENT '结算业务日期。',
  `settlement_cycle` varchar(16) DEFAULT NULL COMMENT '结算周期，如 T+0、T+1、T+2。',
  `settlement_eligible_date` date DEFAULT NULL COMMENT '满足结算周期后的最早可结算业务日期',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '最近一次结算或冲正批次号',
  `reconciliation_status` varchar(32) NOT NULL DEFAULT 'NOT_RECONCILED' COMMENT '对账状态。',
  `reconciliation_date` date DEFAULT NULL COMMENT '对账业务日期。',
  `reconciliation_batch_no` varchar(64) DEFAULT NULL COMMENT '对账批次号。',
  `accounting_status` varchar(32) NOT NULL DEFAULT 'NOT_ACCOUNTED' COMMENT '入账状态。',
  `accounting_time` datetime(3) DEFAULT NULL COMMENT '入账时间。',
  `channel_fee_currency` char(3) DEFAULT NULL COMMENT '渠道手续费币种。',
  `channel_fee_amount` decimal(24,8) DEFAULT NULL COMMENT '已确认渠道成本；清分一期为空',
  `platform_fee_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '平台费用币种；生产清分必须等于label_currency',
  `platform_fee_amount` decimal(24,8) DEFAULT NULL COMMENT '仅标签币种费用组件合计；异币种组件不计入',
  `fee_reversal_amount` decimal(24,8) DEFAULT NULL COMMENT '当前清分修订返还或冲回的标签币种平台费用正数合计',
  `merchant_receivable_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '商户应收币种；生产清分必须等于label_currency',
  `merchant_receivable_amount` decimal(24,8) DEFAULT NULL COMMENT '仅全部费用已在标签币种求值时可用，否则为空',
  `reserve_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '保证金币种；生产清分必须等于label_currency',
  `reserve_amount` decimal(24,8) DEFAULT NULL COMMENT '当前动作标签币种保证金扣留合计',
  `reserve_reversal_amount` decimal(24,8) DEFAULT NULL COMMENT '当前动作标签币种保证金返还或释放正数合计',
  `expected_reserve_release_date` date DEFAULT NULL COMMENT '预计保证金释放业务日期',
  `net_settlement_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '动作清分净额币种；生产清分必须等于label_currency',
  `net_settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '历史查询投影；存在异币种费用时为空',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_state_id` (`finance_state_id`),
  UNIQUE KEY `uk_transaction_finance` (`transaction_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_settlement_status_time` (`settlement_status`,`transaction_date_time`),
  KEY `idx_reconciliation_status_time` (`reconciliation_status`,`transaction_date_time`),
  KEY `idx_accounting_status_time` (`accounting_status`,`transaction_date_time`),
  KEY `idx_operation_finance_time` (`operation_id`,`transaction_date_time`,`id`),
  KEY `idx_clearing_retry` (`clearing_status`,`next_retry_time`,`transaction_date_time`,`id`),
  KEY `idx_clearing_lease` (`clearing_status`,`processing_deadline`,`transaction_date_time`,`id`),
  KEY `idx_merchant_settlement_eligible` (`merchant_id`,`settlement_currency`,`clearing_status`,`settlement_status`,`settlement_eligible_date`,`id`),
  KEY `idx_finance_admin_time` (`deleted`,`transaction_date_time`,`id`),
  KEY `idx_finance_admin_merchant_time` (`merchant_id`,`deleted`,`transaction_date_time`,`id`),
  KEY `idx_finance_admin_status_time` (`clearing_status`,`deleted`,`transaction_date_time`,`id`),
  CONSTRAINT `chk_finance_clearing_revision_202603` CHECK ((`clearing_revision` >= 0)),
  CONSTRAINT `chk_finance_clearing_status_202603` CHECK ((`clearing_status` in (_utf8mb4'NOT_CLEARED',_utf8mb4'PENDING',_utf8mb4'PROCESSING',_utf8mb4'WAITING_SOURCE',_utf8mb4'FAILED',_utf8mb4'MANUAL_REVIEW',_utf8mb4'CLEARED',_utf8mb4'NOT_REQUIRED'))),
  CONSTRAINT `chk_finance_fee_evaluation_202603` CHECK (((`fee_component_currency_count` >= 0) and ((`fee_evaluation_status` is null) or (`fee_evaluation_status` in (_utf8mb4'FINAL_AT_CLEARING',_utf8mb4'PENDING_SETTLEMENT_RATE'))))),
  CONSTRAINT `chk_finance_label_currency_202603` CHECK (((`label_currency` is null) or (((`platform_fee_currency` is null) or (`platform_fee_currency` = `label_currency`)) and ((`merchant_receivable_currency` is null) or (`merchant_receivable_currency` = `label_currency`)) and ((`reserve_currency` is null) or (`reserve_currency` = `label_currency`)) and ((`net_settlement_currency` is null) or (`net_settlement_currency` = `label_currency`))))),
  CONSTRAINT `chk_finance_nonnegative_summary_202603` CHECK ((((`platform_fee_amount` is null) or (`platform_fee_amount` >= 0)) and ((`fee_reversal_amount` is null) or (`fee_reversal_amount` >= 0)) and ((`reserve_amount` is null) or (`reserve_amount` >= 0)) and ((`reserve_reversal_amount` is null) or (`reserve_reversal_amount` >= 0))))
) ENGINE=InnoDB AUTO_INCREMENT=202603000000014196 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动作级清分汇总及结算、对账、账务当前状态；transaction_id唯一，operation_id用于生命周期聚合';

-- ----------------------------
-- Table structure for transaction_finance_state_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_finance_state_202604`;
CREATE TABLE `transaction_finance_state_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `finance_state_id` varchar(64) NOT NULL COMMENT '财务状态ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) NOT NULL COMMENT '交易动作ID。',
  `merchant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '平台商户号；存量回填完成后改为NOT NULL',
  `source_transaction_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '退款、冲正、拒付等来源动作交易号',
  `label_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '动作标签币种；生产清分费用和保证金的权威币种',
  `transaction_type` varchar(32) NOT NULL COMMENT '交易类型。',
  `clearing_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NOT_CLEARED' COMMENT '清分状态：NOT_CLEARED、PENDING、PROCESSING、WAITING_SOURCE、FAILED、MANUAL_REVIEW、CLEARED、NOT_REQUIRED',
  `clearing_revision` int NOT NULL DEFAULT '0' COMMENT '当前有效清分修订号，从1递增',
  `clearing_trigger_event_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '首次触发清分的终态事件号',
  `clearing_request_time` datetime(3) DEFAULT NULL COMMENT '首次接收清分请求时间',
  `clearing_start_time` datetime(3) DEFAULT NULL COMMENT '最近一次开始计算时间',
  `clearing_complete_time` datetime(3) DEFAULT NULL COMMENT '当前修订清分完成时间',
  `processing_owner` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'PROCESSING处理实例和线程租约标识',
  `processing_deadline` datetime(3) DEFAULT NULL COMMENT 'PROCESSING租约到期时间',
  `clearing_retry_count` int NOT NULL DEFAULT '0' COMMENT '清分业务重试次数',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下一次允许清分重试时间',
  `last_failure_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最近一次清分失败码',
  `last_failure_message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最近一次清分失败摘要，不保存请求或配置正文',
  `fee_plan_id` bigint DEFAULT NULL COMMENT '清分采用的费用方案ID',
  `fee_plan_version_id` bigint DEFAULT NULL COMMENT '清分采用的不可变费用版本ID',
  `fee_plan_version_no` int DEFAULT NULL COMMENT '清分采用的费用版本号',
  `fee_snapshot_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '实际采用的规范化费用快照SHA-256',
  `gross_label_amount` decimal(24,8) DEFAULT NULL COMMENT '标签币种下有符号毛本金',
  `fee_component_currency_count` smallint NOT NULL DEFAULT '0' COMMENT '当前修订费用组件币种数',
  `fee_evaluation_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'FINAL_AT_CLEARING或PENDING_SETTLEMENT_RATE',
  `settlement_status` varchar(32) NOT NULL DEFAULT 'NOT_SETTLED' COMMENT '结算状态。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '结算币种。',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '锁定结算直汇率；清分不得写入',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '动作最终结算金额；清分不得写入',
  `settlement_fee_amount` decimal(24,8) DEFAULT NULL COMMENT '历史兼容列；清分不得写入',
  `settlement_date` date DEFAULT NULL COMMENT '结算业务日期。',
  `settlement_cycle` varchar(16) DEFAULT NULL COMMENT '结算周期，如 T+0、T+1、T+2。',
  `settlement_eligible_date` date DEFAULT NULL COMMENT '满足结算周期后的最早可结算业务日期',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '最近一次结算或冲正批次号',
  `reconciliation_status` varchar(32) NOT NULL DEFAULT 'NOT_RECONCILED' COMMENT '对账状态。',
  `reconciliation_date` date DEFAULT NULL COMMENT '对账业务日期。',
  `reconciliation_batch_no` varchar(64) DEFAULT NULL COMMENT '对账批次号。',
  `accounting_status` varchar(32) NOT NULL DEFAULT 'NOT_ACCOUNTED' COMMENT '入账状态。',
  `accounting_time` datetime(3) DEFAULT NULL COMMENT '入账时间。',
  `channel_fee_currency` char(3) DEFAULT NULL COMMENT '渠道手续费币种。',
  `channel_fee_amount` decimal(24,8) DEFAULT NULL COMMENT '已确认渠道成本；清分一期为空',
  `platform_fee_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '平台费用币种；生产清分必须等于label_currency',
  `platform_fee_amount` decimal(24,8) DEFAULT NULL COMMENT '仅标签币种费用组件合计；异币种组件不计入',
  `fee_reversal_amount` decimal(24,8) DEFAULT NULL COMMENT '当前清分修订返还或冲回的标签币种平台费用正数合计',
  `merchant_receivable_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '商户应收币种；生产清分必须等于label_currency',
  `merchant_receivable_amount` decimal(24,8) DEFAULT NULL COMMENT '仅全部费用已在标签币种求值时可用，否则为空',
  `reserve_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '保证金币种；生产清分必须等于label_currency',
  `reserve_amount` decimal(24,8) DEFAULT NULL COMMENT '当前动作标签币种保证金扣留合计',
  `reserve_reversal_amount` decimal(24,8) DEFAULT NULL COMMENT '当前动作标签币种保证金返还或释放正数合计',
  `expected_reserve_release_date` date DEFAULT NULL COMMENT '预计保证金释放业务日期',
  `net_settlement_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '动作清分净额币种；生产清分必须等于label_currency',
  `net_settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '历史查询投影；存在异币种费用时为空',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_state_id` (`finance_state_id`),
  UNIQUE KEY `uk_transaction_finance` (`transaction_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_settlement_status_time` (`settlement_status`,`transaction_date_time`),
  KEY `idx_reconciliation_status_time` (`reconciliation_status`,`transaction_date_time`),
  KEY `idx_accounting_status_time` (`accounting_status`,`transaction_date_time`),
  KEY `idx_operation_finance_time` (`operation_id`,`transaction_date_time`,`id`),
  KEY `idx_clearing_retry` (`clearing_status`,`next_retry_time`,`transaction_date_time`,`id`),
  KEY `idx_clearing_lease` (`clearing_status`,`processing_deadline`,`transaction_date_time`,`id`),
  KEY `idx_merchant_settlement_eligible` (`merchant_id`,`settlement_currency`,`clearing_status`,`settlement_status`,`settlement_eligible_date`,`id`),
  KEY `idx_finance_admin_time` (`deleted`,`transaction_date_time`,`id`),
  KEY `idx_finance_admin_merchant_time` (`merchant_id`,`deleted`,`transaction_date_time`,`id`),
  KEY `idx_finance_admin_status_time` (`clearing_status`,`deleted`,`transaction_date_time`,`id`),
  CONSTRAINT `chk_finance_clearing_revision_202604` CHECK ((`clearing_revision` >= 0)),
  CONSTRAINT `chk_finance_clearing_status_202604` CHECK ((`clearing_status` in (_utf8mb4'NOT_CLEARED',_utf8mb4'PENDING',_utf8mb4'PROCESSING',_utf8mb4'WAITING_SOURCE',_utf8mb4'FAILED',_utf8mb4'MANUAL_REVIEW',_utf8mb4'CLEARED',_utf8mb4'NOT_REQUIRED'))),
  CONSTRAINT `chk_finance_fee_evaluation_202604` CHECK (((`fee_component_currency_count` >= 0) and ((`fee_evaluation_status` is null) or (`fee_evaluation_status` in (_utf8mb4'FINAL_AT_CLEARING',_utf8mb4'PENDING_SETTLEMENT_RATE'))))),
  CONSTRAINT `chk_finance_label_currency_202604` CHECK (((`label_currency` is null) or (((`platform_fee_currency` is null) or (`platform_fee_currency` = `label_currency`)) and ((`merchant_receivable_currency` is null) or (`merchant_receivable_currency` = `label_currency`)) and ((`reserve_currency` is null) or (`reserve_currency` = `label_currency`)) and ((`net_settlement_currency` is null) or (`net_settlement_currency` = `label_currency`))))),
  CONSTRAINT `chk_finance_nonnegative_summary_202604` CHECK ((((`platform_fee_amount` is null) or (`platform_fee_amount` >= 0)) and ((`fee_reversal_amount` is null) or (`fee_reversal_amount` >= 0)) and ((`reserve_amount` is null) or (`reserve_amount` >= 0)) and ((`reserve_reversal_amount` is null) or (`reserve_reversal_amount` >= 0))))
) ENGINE=InnoDB AUTO_INCREMENT=202604000000061075 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动作级清分汇总及结算、对账、账务当前状态；transaction_id唯一，operation_id用于生命周期聚合';

-- ----------------------------
-- Table structure for transaction_finance_state_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_finance_state_202701`;
CREATE TABLE `transaction_finance_state_202701` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `finance_state_id` varchar(64) NOT NULL COMMENT '财务状态ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) NOT NULL COMMENT '交易动作ID。',
  `merchant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '平台商户号；存量回填完成后改为NOT NULL',
  `source_transaction_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '退款、冲正、拒付等来源动作交易号',
  `label_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '动作标签币种；生产清分费用和保证金的权威币种',
  `transaction_type` varchar(32) NOT NULL COMMENT '交易类型。',
  `clearing_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NOT_CLEARED' COMMENT '清分状态：NOT_CLEARED、PENDING、PROCESSING、WAITING_SOURCE、FAILED、MANUAL_REVIEW、CLEARED、NOT_REQUIRED',
  `clearing_revision` int NOT NULL DEFAULT '0' COMMENT '当前有效清分修订号，从1递增',
  `clearing_trigger_event_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '首次触发清分的终态事件号',
  `clearing_request_time` datetime(3) DEFAULT NULL COMMENT '首次接收清分请求时间',
  `clearing_start_time` datetime(3) DEFAULT NULL COMMENT '最近一次开始计算时间',
  `clearing_complete_time` datetime(3) DEFAULT NULL COMMENT '当前修订清分完成时间',
  `processing_owner` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'PROCESSING处理实例和线程租约标识',
  `processing_deadline` datetime(3) DEFAULT NULL COMMENT 'PROCESSING租约到期时间',
  `clearing_retry_count` int NOT NULL DEFAULT '0' COMMENT '清分业务重试次数',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下一次允许清分重试时间',
  `last_failure_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最近一次清分失败码',
  `last_failure_message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '最近一次清分失败摘要，不保存请求或配置正文',
  `fee_plan_id` bigint DEFAULT NULL COMMENT '清分采用的费用方案ID',
  `fee_plan_version_id` bigint DEFAULT NULL COMMENT '清分采用的不可变费用版本ID',
  `fee_plan_version_no` int DEFAULT NULL COMMENT '清分采用的费用版本号',
  `fee_snapshot_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '实际采用的规范化费用快照SHA-256',
  `gross_label_amount` decimal(24,8) DEFAULT NULL COMMENT '标签币种下有符号毛本金',
  `fee_component_currency_count` smallint NOT NULL DEFAULT '0' COMMENT '当前修订费用组件币种数',
  `fee_evaluation_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'FINAL_AT_CLEARING或PENDING_SETTLEMENT_RATE',
  `settlement_status` varchar(32) NOT NULL DEFAULT 'NOT_SETTLED' COMMENT '结算状态。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '结算币种。',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '锁定结算直汇率；清分不得写入',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '动作最终结算金额；清分不得写入',
  `settlement_fee_amount` decimal(24,8) DEFAULT NULL COMMENT '历史兼容列；清分不得写入',
  `settlement_date` date DEFAULT NULL COMMENT '结算业务日期。',
  `settlement_cycle` varchar(16) DEFAULT NULL COMMENT '结算周期，如 T+0、T+1、T+2。',
  `settlement_eligible_date` date DEFAULT NULL COMMENT '满足结算周期后的最早可结算业务日期',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '最近一次结算或冲正批次号',
  `reconciliation_status` varchar(32) NOT NULL DEFAULT 'NOT_RECONCILED' COMMENT '对账状态。',
  `reconciliation_date` date DEFAULT NULL COMMENT '对账业务日期。',
  `reconciliation_batch_no` varchar(64) DEFAULT NULL COMMENT '对账批次号。',
  `accounting_status` varchar(32) NOT NULL DEFAULT 'NOT_ACCOUNTED' COMMENT '入账状态。',
  `accounting_time` datetime(3) DEFAULT NULL COMMENT '入账时间。',
  `channel_fee_currency` char(3) DEFAULT NULL COMMENT '渠道手续费币种。',
  `channel_fee_amount` decimal(24,8) DEFAULT NULL COMMENT '已确认渠道成本；清分一期为空',
  `platform_fee_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '平台费用币种；生产清分必须等于label_currency',
  `platform_fee_amount` decimal(24,8) DEFAULT NULL COMMENT '仅标签币种费用组件合计；异币种组件不计入',
  `fee_reversal_amount` decimal(24,8) DEFAULT NULL COMMENT '当前清分修订返还或冲回的标签币种平台费用正数合计',
  `merchant_receivable_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '商户应收币种；生产清分必须等于label_currency',
  `merchant_receivable_amount` decimal(24,8) DEFAULT NULL COMMENT '仅全部费用已在标签币种求值时可用，否则为空',
  `reserve_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '保证金币种；生产清分必须等于label_currency',
  `reserve_amount` decimal(24,8) DEFAULT NULL COMMENT '当前动作标签币种保证金扣留合计',
  `reserve_reversal_amount` decimal(24,8) DEFAULT NULL COMMENT '当前动作标签币种保证金返还或释放正数合计',
  `expected_reserve_release_date` date DEFAULT NULL COMMENT '预计保证金释放业务日期',
  `net_settlement_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '动作清分净额币种；生产清分必须等于label_currency',
  `net_settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '历史查询投影；存在异币种费用时为空',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号。',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_state_id` (`finance_state_id`),
  UNIQUE KEY `uk_transaction_finance` (`transaction_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_settlement_status_time` (`settlement_status`,`transaction_date_time`),
  KEY `idx_reconciliation_status_time` (`reconciliation_status`,`transaction_date_time`),
  KEY `idx_accounting_status_time` (`accounting_status`,`transaction_date_time`),
  KEY `idx_operation_finance_time` (`operation_id`,`transaction_date_time`,`id`),
  KEY `idx_clearing_retry` (`clearing_status`,`next_retry_time`,`transaction_date_time`,`id`),
  KEY `idx_clearing_lease` (`clearing_status`,`processing_deadline`,`transaction_date_time`,`id`),
  KEY `idx_merchant_settlement_eligible` (`merchant_id`,`settlement_currency`,`clearing_status`,`settlement_status`,`settlement_eligible_date`,`id`),
  KEY `idx_finance_admin_time` (`deleted`,`transaction_date_time`,`id`),
  KEY `idx_finance_admin_merchant_time` (`merchant_id`,`deleted`,`transaction_date_time`,`id`),
  KEY `idx_finance_admin_status_time` (`clearing_status`,`deleted`,`transaction_date_time`,`id`),
  CONSTRAINT `transaction_finance_state_202701_chk_1` CHECK ((`clearing_revision` >= 0)),
  CONSTRAINT `transaction_finance_state_202701_chk_2` CHECK ((`clearing_status` in (_utf8mb4'NOT_CLEARED',_utf8mb4'PENDING',_utf8mb4'PROCESSING',_utf8mb4'WAITING_SOURCE',_utf8mb4'FAILED',_utf8mb4'MANUAL_REVIEW',_utf8mb4'CLEARED',_utf8mb4'NOT_REQUIRED'))),
  CONSTRAINT `transaction_finance_state_202701_chk_3` CHECK (((`fee_component_currency_count` >= 0) and ((`fee_evaluation_status` is null) or (`fee_evaluation_status` in (_utf8mb4'FINAL_AT_CLEARING',_utf8mb4'PENDING_SETTLEMENT_RATE'))))),
  CONSTRAINT `transaction_finance_state_202701_chk_4` CHECK (((`label_currency` is null) or (((`platform_fee_currency` is null) or (`platform_fee_currency` = `label_currency`)) and ((`merchant_receivable_currency` is null) or (`merchant_receivable_currency` = `label_currency`)) and ((`reserve_currency` is null) or (`reserve_currency` = `label_currency`)) and ((`net_settlement_currency` is null) or (`net_settlement_currency` = `label_currency`))))),
  CONSTRAINT `transaction_finance_state_202701_chk_5` CHECK ((((`platform_fee_amount` is null) or (`platform_fee_amount` >= 0)) and ((`fee_reversal_amount` is null) or (`fee_reversal_amount` >= 0)) and ((`reserve_amount` is null) or (`reserve_amount` >= 0)) and ((`reserve_reversal_amount` is null) or (`reserve_reversal_amount` >= 0))))
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动作级清分汇总及结算、对账、账务当前状态；transaction_id唯一，operation_id用于生命周期聚合';

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
  `processor_service` varchar(64) DEFAULT NULL COMMENT '处理事件的服务名。',
  `processor_instance_id` varchar(128) DEFAULT NULL COMMENT '部署平台实例或Pod标识快照。',
  `processor_host_name` varchar(128) DEFAULT NULL COMMENT '处理节点主机名快照。',
  `processor_private_ip` varchar(45) DEFAULT NULL COMMENT '部署平台显式注入的节点内网IP快照。',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '跨服务调用链Trace ID。',
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
  `processor_service` varchar(64) DEFAULT NULL COMMENT '处理事件的服务名。',
  `processor_instance_id` varchar(128) DEFAULT NULL COMMENT '部署平台实例或Pod标识快照。',
  `processor_host_name` varchar(128) DEFAULT NULL COMMENT '处理节点主机名快照。',
  `processor_private_ip` varchar(45) DEFAULT NULL COMMENT '部署平台显式注入的节点内网IP快照。',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '跨服务调用链Trace ID。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202603000000091140 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录交易每一个流程事件，用于后台时间线展示和问题追踪。';

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
  `processor_service` varchar(64) DEFAULT NULL COMMENT '处理事件的服务名。',
  `processor_instance_id` varchar(128) DEFAULT NULL COMMENT '部署平台实例或Pod标识快照。',
  `processor_host_name` varchar(128) DEFAULT NULL COMMENT '处理节点主机名快照。',
  `processor_private_ip` varchar(45) DEFAULT NULL COMMENT '部署平台显式注入的节点内网IP快照。',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '跨服务调用链Trace ID。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202604000000365972 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录交易每一个流程事件，用于后台时间线展示和问题追踪。';

-- ----------------------------
-- Table structure for transaction_flow_event_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_flow_event_202701`;
CREATE TABLE `transaction_flow_event_202701` (
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
  `processor_service` varchar(64) DEFAULT NULL COMMENT '处理事件的服务名。',
  `processor_instance_id` varchar(128) DEFAULT NULL COMMENT '部署平台实例或Pod标识快照。',
  `processor_host_name` varchar(128) DEFAULT NULL COMMENT '处理节点主机名快照。',
  `processor_private_ip` varchar(45) DEFAULT NULL COMMENT '部署平台显式注入的节点内网IP快照。',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '跨服务调用链Trace ID。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录交易每一个流程事件，用于后台时间线展示和问题追踪。';

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
) ENGINE=InnoDB AUTO_INCREMENT=213863 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资金类请求、渠道回调、MQ消费等全局幂等兜底；沿用现有表，不再新建第二套幂等表。';

-- ----------------------------
-- Table structure for transaction_label_amount_void_backup_20261105
-- ----------------------------
DROP TABLE IF EXISTS `transaction_label_amount_void_backup_20261105`;
CREATE TABLE `transaction_label_amount_void_backup_20261105` (
  `shard_code` char(6) NOT NULL COMMENT '原交易分片：202603、202604。',
  `operation_row_id` bigint NOT NULL COMMENT 'transaction_operation 分片表物理主键。',
  `label_currency` char(3) NOT NULL COMMENT '迁移前标签币种。',
  `label_amount` decimal(20,6) NOT NULL COMMENT '迁移前标签金额。',
  `transaction_rate` decimal(24,12) DEFAULT NULL COMMENT '迁移前交易汇率。',
  `dcc_enabled` tinyint NOT NULL COMMENT '迁移前 DCC 标识。',
  `edc_enabled` tinyint NOT NULL COMMENT '迁移前 EDC 标识。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '备份时间。',
  PRIMARY KEY (`shard_code`,`operation_row_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='20261105 标签金额双口径迁移的历史 VOID 回滚备份。';

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
) ENGINE=InnoDB AUTO_INCREMENT=75312 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='非分表交易定位索引；将商户交易标识映射到动作和生命周期根交易的真实季度分片时间。';

-- ----------------------------
-- Table structure for transaction_merchant_api_interaction_log
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_api_interaction_log`;
CREATE TABLE `transaction_merchant_api_interaction_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `api_log_id` varchar(64) NOT NULL COMMENT '商户API交互日志ID。',
  `request_id` varchar(128) DEFAULT NULL COMMENT '商户本次API请求唯一标识，通常等于 orderInfo.orderId。',
  `transaction_id` varchar(64) DEFAULT NULL COMMENT '真实系统订单标识，建单前拒绝时为空',
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
  `admission_context_json` json DEFAULT NULL COMMENT '建单前后的规范方式、稳定尝试及受理证据摘要',
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
  `transaction_id` varchar(64) DEFAULT NULL COMMENT '真实系统订单标识，建单前拒绝时为空',
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
  `admission_context_json` json DEFAULT NULL COMMENT '建单前后的规范方式、稳定尝试及受理证据摘要',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_log_id` (`api_log_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`,`request_time`),
  KEY `idx_merchant_order_time` (`merchant_id`,`merchant_order_no`,`transaction_date_time`),
  KEY `idx_request_id_time` (`request_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000014566 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户OpenAPI请求和平台响应的脱敏审计日志。';

-- ----------------------------
-- Table structure for transaction_merchant_api_interaction_log_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_api_interaction_log_202604`;
CREATE TABLE `transaction_merchant_api_interaction_log_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `api_log_id` varchar(64) NOT NULL COMMENT '商户API交互日志ID。',
  `request_id` varchar(128) DEFAULT NULL COMMENT '商户本次API请求唯一标识，通常等于 orderInfo.orderId。',
  `transaction_id` varchar(64) DEFAULT NULL COMMENT '真实系统订单标识，建单前拒绝时为空',
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
  `admission_context_json` json DEFAULT NULL COMMENT '建单前后的规范方式、稳定尝试及受理证据摘要',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_log_id` (`api_log_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`,`request_time`),
  KEY `idx_merchant_order_time` (`merchant_id`,`merchant_order_no`,`transaction_date_time`),
  KEY `idx_request_id_time` (`request_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000061056 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户OpenAPI请求和平台响应的脱敏审计日志。';

-- ----------------------------
-- Table structure for transaction_merchant_api_interaction_log_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_api_interaction_log_202701`;
CREATE TABLE `transaction_merchant_api_interaction_log_202701` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `api_log_id` varchar(64) NOT NULL COMMENT '商户API交互日志ID。',
  `request_id` varchar(128) DEFAULT NULL COMMENT '商户本次API请求唯一标识，通常等于 orderInfo.orderId。',
  `transaction_id` varchar(64) DEFAULT NULL COMMENT '真实系统订单标识，建单前拒绝时为空',
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
  `admission_context_json` json DEFAULT NULL COMMENT '建单前后的规范方式、稳定尝试及受理证据摘要',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_log_id` (`api_log_id`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`,`request_time`),
  KEY `idx_merchant_order_time` (`merchant_id`,`merchant_order_no`,`transaction_date_time`),
  KEY `idx_request_id_time` (`request_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户OpenAPI请求和平台响应的脱敏审计日志。';

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
  `callback_url` varchar(512) NOT NULL COMMENT '商户回调地址明文；禁止完整写入日志。',
  `payload_json` mediumtext NOT NULL COMMENT '商户通知业务载荷明文 JSON。',
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
  KEY `idx_merchant_time` (`merchant_id`,`transaction_date_time`),
  KEY `idx_processing_event` (`processing_event_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存给商户异步通知的任务当前状态、重试计划和成功记录。';

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
  `callback_url` varchar(512) NOT NULL COMMENT '商户回调地址明文；禁止完整写入日志。',
  `payload_json` mediumtext NOT NULL COMMENT '商户通知业务载荷明文 JSON。',
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
  KEY `idx_merchant_time` (`merchant_id`,`transaction_date_time`),
  KEY `idx_processing_event` (`processing_event_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000015279 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存给商户异步通知的任务当前状态、重试计划和成功记录。';

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
  `callback_url` varchar(512) NOT NULL COMMENT '商户回调地址明文；禁止完整写入日志。',
  `payload_json` mediumtext NOT NULL COMMENT '商户通知业务载荷明文 JSON。',
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
  KEY `idx_merchant_time` (`merchant_id`,`transaction_date_time`),
  KEY `idx_processing_event` (`processing_event_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000061376 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存给商户异步通知的任务当前状态、重试计划和成功记录。';

-- ----------------------------
-- Table structure for transaction_merchant_notification_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_notification_202701`;
CREATE TABLE `transaction_merchant_notification_202701` (
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
  `callback_url` varchar(512) NOT NULL COMMENT '商户回调地址明文；禁止完整写入日志。',
  `payload_json` mediumtext NOT NULL COMMENT '商户通知业务载荷明文 JSON。',
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
  KEY `idx_merchant_time` (`merchant_id`,`transaction_date_time`),
  KEY `idx_processing_event` (`processing_event_id`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存给商户异步通知的任务当前状态、重试计划和成功记录。';

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
) ENGINE=InnoDB AUTO_INCREMENT=202603000000023850 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存每一次商户通知请求/响应日志。';

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
) ENGINE=InnoDB AUTO_INCREMENT=202604000000069101 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存每一次商户通知请求/响应日志。';

-- ----------------------------
-- Table structure for transaction_merchant_notification_log_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_notification_log_202701`;
CREATE TABLE `transaction_merchant_notification_log_202701` (
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存每一次商户通知请求/响应日志。';

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
  `sub_merchant_info_json` json DEFAULT NULL COMMENT '商户上送的完整子商户信息明文 JSON；未上送时为空。',
  `merchant_name` varchar(256) DEFAULT NULL COMMENT '交易发生时商户名称快照。',
  `merchant_country` varchar(3) DEFAULT NULL COMMENT '商户国家/地区。',
  `merchant_category_code` varchar(16) DEFAULT NULL COMMENT 'MCC。',
  `merchant_status` varchar(32) DEFAULT NULL COMMENT '交易发生时商户状态。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `channel_merchant_id` varchar(128) DEFAULT NULL COMMENT '渠道真实MID。',
  `terminal_id` varchar(128) DEFAULT NULL COMMENT '终端号。',
  `channel_mid_metadata_json` json DEFAULT NULL COMMENT '交易使用的MID核心元数据快照。',
  `settlement_config_snapshot_json` json DEFAULT NULL COMMENT '结算配置快照。',
  `fee_config_snapshot_json` json DEFAULT NULL COMMENT '费率配置快照。',
  `internal_risk_config_snapshot_json` json DEFAULT NULL COMMENT '内风控配置快照。',
  `route_config_snapshot_json` json DEFAULT NULL COMMENT '路由决策配置快照。',
  `fee_plan_id` bigint DEFAULT NULL COMMENT '动作受理时费用方案ID',
  `fee_plan_version_id` bigint DEFAULT NULL COMMENT '动作受理时冻结的费用方案版本ID',
  `fee_plan_version_no` int DEFAULT NULL COMMENT '动作受理时冻结的费用版本号',
  `fee_snapshot_hash` char(64) DEFAULT NULL COMMENT '规范化费用快照SHA-256',
  `fee_snapshot_time` datetime(3) DEFAULT NULL COMMENT '费用配置冻结时间',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  `payment_admission_snapshot_json` json DEFAULT NULL COMMENT '首次受理时的规范方式、授权费率及执行引用的白名单快照',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_snapshot_id` (`snapshot_id`),
  UNIQUE KEY `uk_merchant_snapshot_transaction` (`transaction_id`,`transaction_date_time`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_merchant_time` (`merchant_id`,`transaction_date_time`),
  KEY `idx_merchant_fee_version_time` (`merchant_id`,`fee_plan_version_id`,`transaction_date_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易商户、渠道、费率、结算和风控配置快照。';

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
  `sub_merchant_info_json` json DEFAULT NULL COMMENT '商户上送的完整子商户信息明文 JSON；未上送时为空。',
  `merchant_name` varchar(256) DEFAULT NULL COMMENT '交易发生时商户名称快照。',
  `merchant_country` varchar(3) DEFAULT NULL COMMENT '商户国家/地区。',
  `merchant_category_code` varchar(16) DEFAULT NULL COMMENT 'MCC。',
  `merchant_status` varchar(32) DEFAULT NULL COMMENT '交易发生时商户状态。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `channel_merchant_id` varchar(128) DEFAULT NULL COMMENT '渠道真实MID。',
  `terminal_id` varchar(128) DEFAULT NULL COMMENT '终端号。',
  `channel_mid_metadata_json` json DEFAULT NULL COMMENT '交易使用的MID核心元数据快照。',
  `settlement_config_snapshot_json` json DEFAULT NULL COMMENT '结算配置快照。',
  `fee_config_snapshot_json` json DEFAULT NULL COMMENT '费率配置快照。',
  `internal_risk_config_snapshot_json` json DEFAULT NULL COMMENT '内风控配置快照。',
  `route_config_snapshot_json` json DEFAULT NULL COMMENT '路由决策配置快照。',
  `fee_plan_id` bigint DEFAULT NULL COMMENT '动作受理时费用方案ID',
  `fee_plan_version_id` bigint DEFAULT NULL COMMENT '动作受理时冻结的费用方案版本ID',
  `fee_plan_version_no` int DEFAULT NULL COMMENT '动作受理时冻结的费用版本号',
  `fee_snapshot_hash` char(64) DEFAULT NULL COMMENT '规范化费用快照SHA-256',
  `fee_snapshot_time` datetime(3) DEFAULT NULL COMMENT '费用配置冻结时间',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  `payment_admission_snapshot_json` json DEFAULT NULL COMMENT '首次受理时的规范方式、授权费率及执行引用的白名单快照',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_snapshot_id` (`snapshot_id`),
  UNIQUE KEY `uk_merchant_snapshot_transaction` (`transaction_id`,`transaction_date_time`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_merchant_time` (`merchant_id`,`transaction_date_time`),
  KEY `idx_merchant_fee_version_time` (`merchant_id`,`fee_plan_version_id`,`transaction_date_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000014089 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易商户、渠道、费率、结算和风控配置快照。';

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
  `sub_merchant_info_json` json DEFAULT NULL COMMENT '商户上送的完整子商户信息明文 JSON；未上送时为空。',
  `merchant_name` varchar(256) DEFAULT NULL COMMENT '交易发生时商户名称快照。',
  `merchant_country` varchar(3) DEFAULT NULL COMMENT '商户国家/地区。',
  `merchant_category_code` varchar(16) DEFAULT NULL COMMENT 'MCC。',
  `merchant_status` varchar(32) DEFAULT NULL COMMENT '交易发生时商户状态。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `channel_merchant_id` varchar(128) DEFAULT NULL COMMENT '渠道真实MID。',
  `terminal_id` varchar(128) DEFAULT NULL COMMENT '终端号。',
  `channel_mid_metadata_json` json DEFAULT NULL COMMENT '交易使用的MID核心元数据快照。',
  `settlement_config_snapshot_json` json DEFAULT NULL COMMENT '结算配置快照。',
  `fee_config_snapshot_json` json DEFAULT NULL COMMENT '费率配置快照。',
  `internal_risk_config_snapshot_json` json DEFAULT NULL COMMENT '内风控配置快照。',
  `route_config_snapshot_json` json DEFAULT NULL COMMENT '路由决策配置快照。',
  `fee_plan_id` bigint DEFAULT NULL COMMENT '动作受理时费用方案ID',
  `fee_plan_version_id` bigint DEFAULT NULL COMMENT '动作受理时冻结的费用方案版本ID',
  `fee_plan_version_no` int DEFAULT NULL COMMENT '动作受理时冻结的费用版本号',
  `fee_snapshot_hash` char(64) DEFAULT NULL COMMENT '规范化费用快照SHA-256',
  `fee_snapshot_time` datetime(3) DEFAULT NULL COMMENT '费用配置冻结时间',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  `payment_admission_snapshot_json` json DEFAULT NULL COMMENT '首次受理时的规范方式、授权费率及执行引用的白名单快照',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_snapshot_id` (`snapshot_id`),
  UNIQUE KEY `uk_merchant_snapshot_transaction` (`transaction_id`,`transaction_date_time`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_merchant_time` (`merchant_id`,`transaction_date_time`),
  KEY `idx_merchant_fee_version_time` (`merchant_id`,`fee_plan_version_id`,`transaction_date_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000061076 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易商户、渠道、费率、结算和风控配置快照。';

-- ----------------------------
-- Table structure for transaction_merchant_snapshot_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_merchant_snapshot_202701`;
CREATE TABLE `transaction_merchant_snapshot_202701` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `snapshot_id` varchar(64) NOT NULL COMMENT '交易商户快照ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID；订单级快照可为空。',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号。',
  `sub_merchant_info_json` json DEFAULT NULL COMMENT '商户上送的完整子商户信息明文 JSON；未上送时为空。',
  `merchant_name` varchar(256) DEFAULT NULL COMMENT '交易发生时商户名称快照。',
  `merchant_country` varchar(3) DEFAULT NULL COMMENT '商户国家/地区。',
  `merchant_category_code` varchar(16) DEFAULT NULL COMMENT 'MCC。',
  `merchant_status` varchar(32) DEFAULT NULL COMMENT '交易发生时商户状态。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `channel_merchant_id` varchar(128) DEFAULT NULL COMMENT '渠道真实MID。',
  `terminal_id` varchar(128) DEFAULT NULL COMMENT '终端号。',
  `channel_mid_metadata_json` json DEFAULT NULL COMMENT '交易使用的MID核心元数据快照。',
  `settlement_config_snapshot_json` json DEFAULT NULL COMMENT '结算配置快照。',
  `fee_config_snapshot_json` json DEFAULT NULL COMMENT '费率配置快照。',
  `internal_risk_config_snapshot_json` json DEFAULT NULL COMMENT '内风控配置快照。',
  `route_config_snapshot_json` json DEFAULT NULL COMMENT '路由决策配置快照。',
  `fee_plan_id` bigint DEFAULT NULL COMMENT '动作受理时费用方案ID',
  `fee_plan_version_id` bigint DEFAULT NULL COMMENT '动作受理时冻结的费用方案版本ID',
  `fee_plan_version_no` int DEFAULT NULL COMMENT '动作受理时冻结的费用版本号',
  `fee_snapshot_hash` char(64) DEFAULT NULL COMMENT '规范化费用快照SHA-256',
  `fee_snapshot_time` datetime(3) DEFAULT NULL COMMENT '费用配置冻结时间',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '交易业务时间。',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '交易业务时间对应 UTC 时间。',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '交易业务时间所属 IANA 时区。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  `payment_admission_snapshot_json` json DEFAULT NULL COMMENT '首次受理时的规范方式、授权费率及执行引用的白名单快照',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_snapshot_id` (`snapshot_id`),
  UNIQUE KEY `uk_merchant_snapshot_transaction` (`transaction_id`,`transaction_date_time`),
  KEY `idx_transaction_time` (`transaction_id`,`transaction_date_time`),
  KEY `idx_operation_time` (`operation_id`,`transaction_date_time`),
  KEY `idx_merchant_time` (`merchant_id`,`transaction_date_time`),
  KEY `idx_merchant_fee_version_time` (`merchant_id`,`fee_plan_version_id`,`transaction_date_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易商户、渠道、费率、结算和风控配置快照。';

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
  `ingress_client_ip` varchar(45) DEFAULT NULL COMMENT '网关认证并规范化的接入客户端IP，仅用于受控安全审计。',
  `ingress_client_ip_hash` char(64) DEFAULT NULL COMMENT '接入客户端IP的SHA-256摘要，用于关联检索。',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '商户上送的交易描述快照，用于响应、查询和通知回显。',
  `refund_scope` varchar(32) DEFAULT NULL COMMENT 'FULL/PARTIAL/VOID',
  `request_reason` varchar(512) DEFAULT NULL COMMENT '退款或撤销原因',
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
  `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种 ISO 4217 小数位精度。',
  `transaction_currency` char(3) NOT NULL COMMENT '系统交易币种。',
  `transaction_amount` decimal(20,6) NOT NULL COMMENT '系统交易金额。',
  `approved_currency` char(3) DEFAULT NULL COMMENT '渠道批准或最终成功币种。',
  `approved_amount` decimal(20,6) DEFAULT NULL COMMENT '渠道批准或最终成功金额。',
  `channel_request_currency` char(3) DEFAULT NULL COMMENT '上送渠道币种。',
  `channel_request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道金额。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '动作预计或最终结算币种。',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '动作最终结算金额；仅结算投影写入',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '1单位动作交易币种兑换的结算币种数量；仅结算投影写入',
  `settlement_date` date DEFAULT NULL COMMENT '动作最终结算业务日期',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '动作最近一次结算或冲正批次号',
  `currency_exponent` tinyint NOT NULL COMMENT '交易币种小数位精度。',
  `dcc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '当前动作是否启用 DCC。',
  `edc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '当前动作是否启用 EDC。',
  `transaction_rate` decimal(24,12) DEFAULT NULL COMMENT '标签金额转交易金额使用的汇率。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `channel_terminal_id` varchar(128) DEFAULT NULL COMMENT '渠道终端号或子MID。',
  `channel_route_snapshot_json` json DEFAULT NULL COMMENT '交易创建时冻结的原始渠道路由快照。',
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
  `clearing_status` varchar(32) NOT NULL DEFAULT 'NOT_CLEARED' COMMENT '动作清分查询投影：NOT_CLEARED、PENDING、CLEARED、FAILED、NOT_REQUIRED',
  `clearing_complete_time` datetime(3) DEFAULT NULL COMMENT '最近一次有效清分完成时间',
  `clearing_failure_code` varchar(64) DEFAULT NULL COMMENT '最近一次清分失败码；成功或无需清分时为空',
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
  KEY `idx_pending_fund_balance` (`merchant_id`,`transaction_status`,`settlement_status`,`deleted`,`transaction_type`,`transaction_date_time`,`label_currency`),
  KEY `idx_clearing_status_time` (`clearing_status`,`transaction_date_time`,`id`),
  KEY `idx_transaction_operation_ingress_ip_hash` (`ingress_client_ip_hash`,`transaction_date_time`)
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
  `ingress_client_ip` varchar(45) DEFAULT NULL COMMENT '网关认证并规范化的接入客户端IP，仅用于受控安全审计。',
  `ingress_client_ip_hash` char(64) DEFAULT NULL COMMENT '接入客户端IP的SHA-256摘要，用于关联检索。',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '商户上送的交易描述快照，用于响应、查询和通知回显。',
  `refund_scope` varchar(32) DEFAULT NULL COMMENT 'FULL/PARTIAL/VOID',
  `request_reason` varchar(512) DEFAULT NULL COMMENT '退款或撤销原因',
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
  `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种 ISO 4217 小数位精度。',
  `transaction_currency` char(3) NOT NULL COMMENT '系统交易币种。',
  `transaction_amount` decimal(20,6) NOT NULL COMMENT '系统交易金额。',
  `approved_currency` char(3) DEFAULT NULL COMMENT '渠道批准或最终成功币种。',
  `approved_amount` decimal(20,6) DEFAULT NULL COMMENT '渠道批准或最终成功金额。',
  `channel_request_currency` char(3) DEFAULT NULL COMMENT '上送渠道币种。',
  `channel_request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道金额。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '动作预计或最终结算币种。',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '动作最终结算金额；仅结算投影写入',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '1单位动作交易币种兑换的结算币种数量；仅结算投影写入',
  `settlement_date` date DEFAULT NULL COMMENT '动作最终结算业务日期',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '动作最近一次结算或冲正批次号',
  `currency_exponent` tinyint NOT NULL COMMENT '交易币种小数位精度。',
  `dcc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '当前动作是否启用 DCC。',
  `edc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '当前动作是否启用 EDC。',
  `transaction_rate` decimal(24,12) DEFAULT NULL COMMENT '标签金额转交易金额使用的汇率。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `channel_terminal_id` varchar(128) DEFAULT NULL COMMENT '渠道终端号或子MID。',
  `channel_route_snapshot_json` json DEFAULT NULL COMMENT '交易创建时冻结的原始渠道路由快照。',
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
  `clearing_status` varchar(32) NOT NULL DEFAULT 'NOT_CLEARED' COMMENT '动作清分查询投影：NOT_CLEARED、PENDING、CLEARED、FAILED、NOT_REQUIRED',
  `clearing_complete_time` datetime(3) DEFAULT NULL COMMENT '最近一次有效清分完成时间',
  `clearing_failure_code` varchar(64) DEFAULT NULL COMMENT '最近一次清分失败码；成功或无需清分时为空',
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
  KEY `idx_pending_fund_balance` (`merchant_id`,`transaction_status`,`settlement_status`,`deleted`,`transaction_type`,`transaction_date_time`,`label_currency`),
  KEY `idx_clearing_status_time` (`clearing_status`,`transaction_date_time`,`id`),
  KEY `idx_transaction_operation_ingress_ip_hash` (`ingress_client_ip_hash`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000015496 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易动作单；授权、增量授权、请款、退款、Void、拒付等每一个动作一条记录。';

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
  `ingress_client_ip` varchar(45) DEFAULT NULL COMMENT '网关认证并规范化的接入客户端IP，仅用于受控安全审计。',
  `ingress_client_ip_hash` char(64) DEFAULT NULL COMMENT '接入客户端IP的SHA-256摘要，用于关联检索。',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '商户上送的交易描述快照，用于响应、查询和通知回显。',
  `refund_scope` varchar(32) DEFAULT NULL COMMENT 'FULL/PARTIAL/VOID',
  `request_reason` varchar(512) DEFAULT NULL COMMENT '退款或撤销原因',
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
  `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种 ISO 4217 小数位精度。',
  `transaction_currency` char(3) NOT NULL COMMENT '系统交易币种。',
  `transaction_amount` decimal(20,6) NOT NULL COMMENT '系统交易金额。',
  `approved_currency` char(3) DEFAULT NULL COMMENT '渠道批准或最终成功币种。',
  `approved_amount` decimal(20,6) DEFAULT NULL COMMENT '渠道批准或最终成功金额。',
  `channel_request_currency` char(3) DEFAULT NULL COMMENT '上送渠道币种。',
  `channel_request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道金额。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '动作预计或最终结算币种。',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '动作最终结算金额；仅结算投影写入',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '1单位动作交易币种兑换的结算币种数量；仅结算投影写入',
  `settlement_date` date DEFAULT NULL COMMENT '动作最终结算业务日期',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '动作最近一次结算或冲正批次号',
  `currency_exponent` tinyint NOT NULL COMMENT '交易币种小数位精度。',
  `dcc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '当前动作是否启用 DCC。',
  `edc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '当前动作是否启用 EDC。',
  `transaction_rate` decimal(24,12) DEFAULT NULL COMMENT '标签金额转交易金额使用的汇率。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `channel_terminal_id` varchar(128) DEFAULT NULL COMMENT '渠道终端号或子MID。',
  `channel_route_snapshot_json` json DEFAULT NULL COMMENT '交易创建时冻结的原始渠道路由快照。',
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
  `clearing_status` varchar(32) NOT NULL DEFAULT 'NOT_CLEARED' COMMENT '动作清分查询投影：NOT_CLEARED、PENDING、CLEARED、FAILED、NOT_REQUIRED',
  `clearing_complete_time` datetime(3) DEFAULT NULL COMMENT '最近一次有效清分完成时间',
  `clearing_failure_code` varchar(64) DEFAULT NULL COMMENT '最近一次清分失败码；成功或无需清分时为空',
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
  KEY `idx_pending_fund_balance` (`merchant_id`,`transaction_status`,`settlement_status`,`deleted`,`transaction_type`,`transaction_date_time`,`label_currency`),
  KEY `idx_clearing_status_time` (`clearing_status`,`transaction_date_time`,`id`),
  KEY `idx_transaction_operation_ingress_ip_hash` (`ingress_client_ip_hash`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000061125 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易动作单；授权、增量授权、请款、退款、Void、拒付等每一个动作一条记录。';

-- ----------------------------
-- Table structure for transaction_operation_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_operation_202701`;
CREATE TABLE `transaction_operation_202701` (
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
  `ingress_client_ip` varchar(45) DEFAULT NULL COMMENT '网关认证并规范化的接入客户端IP，仅用于受控安全审计。',
  `ingress_client_ip_hash` char(64) DEFAULT NULL COMMENT '接入客户端IP的SHA-256摘要，用于关联检索。',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '商户上送的交易描述快照，用于响应、查询和通知回显。',
  `refund_scope` varchar(32) DEFAULT NULL COMMENT 'FULL/PARTIAL/VOID',
  `request_reason` varchar(512) DEFAULT NULL COMMENT '退款或撤销原因',
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
  `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种 ISO 4217 小数位精度。',
  `transaction_currency` char(3) NOT NULL COMMENT '系统交易币种。',
  `transaction_amount` decimal(20,6) NOT NULL COMMENT '系统交易金额。',
  `approved_currency` char(3) DEFAULT NULL COMMENT '渠道批准或最终成功币种。',
  `approved_amount` decimal(20,6) DEFAULT NULL COMMENT '渠道批准或最终成功金额。',
  `channel_request_currency` char(3) DEFAULT NULL COMMENT '上送渠道币种。',
  `channel_request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道金额。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '动作预计或最终结算币种。',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '动作最终结算金额；仅结算投影写入',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '1单位动作交易币种兑换的结算币种数量；仅结算投影写入',
  `settlement_date` date DEFAULT NULL COMMENT '动作最终结算业务日期',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '动作最近一次结算或冲正批次号',
  `currency_exponent` tinyint NOT NULL COMMENT '交易币种小数位精度。',
  `dcc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '当前动作是否启用 DCC。',
  `edc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '当前动作是否启用 EDC。',
  `transaction_rate` decimal(24,12) DEFAULT NULL COMMENT '标签金额转交易金额使用的汇率。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '渠道MID配置ID。',
  `channel_terminal_id` varchar(128) DEFAULT NULL COMMENT '渠道终端号或子MID。',
  `channel_route_snapshot_json` json DEFAULT NULL COMMENT '交易创建时冻结的原始渠道路由快照。',
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
  `clearing_status` varchar(32) NOT NULL DEFAULT 'NOT_CLEARED' COMMENT '动作清分查询投影：NOT_CLEARED、PENDING、CLEARED、FAILED、NOT_REQUIRED',
  `clearing_complete_time` datetime(3) DEFAULT NULL COMMENT '最近一次有效清分完成时间',
  `clearing_failure_code` varchar(64) DEFAULT NULL COMMENT '最近一次清分失败码；成功或无需清分时为空',
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
  KEY `idx_pending_fund_balance` (`merchant_id`,`transaction_status`,`settlement_status`,`deleted`,`transaction_type`,`transaction_date_time`,`label_currency`),
  KEY `idx_clearing_status_time` (`clearing_status`,`transaction_date_time`,`id`),
  KEY `idx_transaction_operation_ingress_ip_hash` (`ingress_client_ip_hash`,`transaction_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易动作单；授权、增量授权、请款、退款、Void、拒付等每一个动作一条记录。';

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
  `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种 ISO 4217 小数位精度。',
  `transaction_currency` char(3) NOT NULL COMMENT '系统交易币种，经过 DCC/EDC 后用于交易核心处理。',
  `transaction_amount` decimal(20,6) NOT NULL COMMENT '系统交易金额，经过 DCC/EDC 后用于交易核心处理。',
  `channel_request_currency` char(3) DEFAULT NULL COMMENT '上送渠道的币种。',
  `channel_request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道的金额。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '预计或最终结算币种。',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '最近真实动作最终结算金额',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '最近真实动作中1单位交易币种兑换的结算币种数量',
  `settlement_date` date DEFAULT NULL COMMENT '最近真实动作结算业务日期',
  `currency_exponent` tinyint NOT NULL COMMENT '交易币种小数位精度，如 USD=2、JPY=0。',
  `dcc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用 DCC，0否，1是。',
  `edc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用 EDC，0否，1是；渠道不支持商户币种时使用。',
  `transaction_rate` decimal(24,12) DEFAULT NULL COMMENT '标签金额转交易金额使用的汇率。',
  `rate_source` varchar(64) DEFAULT NULL COMMENT '汇率来源，如 PLATFORM、CHANNEL、MERCHANT。',
  `rate_time` datetime(3) DEFAULT NULL COMMENT '汇率生效或报价时间。',
  `label_authorized_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计授权成功金额，标签币种单位。',
  `label_authorized_cancel_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计授权取消金额，标签币种单位。',
  `label_captured_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计请款成功金额，标签币种单位。',
  `label_refunded_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计退款成功金额，标签币种单位。',
  `label_chargeback_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计拒付金额，标签币种单位。',
  `label_available_capture_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '当前可请款金额，标签币种单位。',
  `label_available_refund_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '当前可退款金额，标签币种单位。',
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
  `clearing_status` varchar(32) NOT NULL DEFAULT 'NOT_CLEARED' COMMENT '生命周期清分聚合投影：NOT_CLEARED、PENDING、PARTIALLY_CLEARED、CLEARED、FAILED、NOT_REQUIRED',
  `clearing_complete_time` datetime(3) DEFAULT NULL COMMENT '生命周期最近一次清分聚合完成时间',
  `channel_match_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '渠道结果勾兑/查询确认状态；定时任务按该字段调用渠道查询API同步一致。',
  `channel_match_result` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认结果，如 SAME、STATUS_CHANGED、AMOUNT_CHANGED、CHANNEL_NOT_FOUND。',
  `channel_match_count` int NOT NULL DEFAULT '0' COMMENT '渠道结果勾兑查询次数。',
  `last_channel_match_request_id` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认请求ID，关联 transaction_channel_request.request_id。',
  `last_channel_match_time` datetime(3) DEFAULT NULL COMMENT '最近一次渠道查询确认时间。',
  `next_channel_match_time` datetime(3) DEFAULT NULL COMMENT '下一次渠道查询确认计划时间。',
  `channel_match_fail_reason` varchar(512) DEFAULT NULL COMMENT '最近一次渠道查询确认失败原因。',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '最近一次结算或冲正批次号',
  `settlement_transaction_id` varchar(64) DEFAULT NULL COMMENT '当前结算快照来源的真实动作交易号',
  `settlement_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '当前结算快照来源的真实动作分片时间',
  `reconciliation_batch_no` varchar(64) DEFAULT NULL COMMENT '最近一次对账批次号。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID，关联 channel_info.id。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码，如 MPGS。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '交易使用的渠道MID配置ID。',
  `channel_merchant_id` varchar(128) DEFAULT NULL COMMENT '渠道真实商户号或渠道MID，来自MID元数据快照。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道侧主订单号。',
  `internal_risk_decision` varchar(32) DEFAULT NULL COMMENT '最近一次内风控决策，如 PASS、REJECT、REVIEW、REQUIRE_3DS；外风控结构化字段本版不设计。',
  `internal_risk_record_no` varchar(64) DEFAULT NULL COMMENT '最近一次内风控评估流水号；不承载外风控平台RequestID。',
  `merchant_website` varchar(512) DEFAULT NULL COMMENT '首次支付、授权或预授权请求中的商户网站原始URL，用于来源网址限定和查询回显',
  `callback_url` varchar(512) DEFAULT NULL COMMENT '商户异步通知地址明文；禁止完整写入日志。',
  `redirect_url` varchar(512) DEFAULT NULL COMMENT 'Hosted Checkout 结果页 Form POST 地址明文。',
  `language` varchar(20) DEFAULT NULL COMMENT 'Hosted Checkout 页面语言。',
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
  KEY `idx_merchant_order_id` (`merchant_id`,`merchant_order_id`,`transaction_type`),
  KEY `idx_clearing_time` (`clearing_status`,`transaction_date_time`,`id`)
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
  `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种 ISO 4217 小数位精度。',
  `transaction_currency` char(3) NOT NULL COMMENT '系统交易币种，经过 DCC/EDC 后用于交易核心处理。',
  `transaction_amount` decimal(20,6) NOT NULL COMMENT '系统交易金额，经过 DCC/EDC 后用于交易核心处理。',
  `channel_request_currency` char(3) DEFAULT NULL COMMENT '上送渠道的币种。',
  `channel_request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道的金额。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '预计或最终结算币种。',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '最近真实动作最终结算金额',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '最近真实动作中1单位交易币种兑换的结算币种数量',
  `settlement_date` date DEFAULT NULL COMMENT '最近真实动作结算业务日期',
  `currency_exponent` tinyint NOT NULL COMMENT '交易币种小数位精度，如 USD=2、JPY=0。',
  `dcc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用 DCC，0否，1是。',
  `edc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用 EDC，0否，1是；渠道不支持商户币种时使用。',
  `transaction_rate` decimal(24,12) DEFAULT NULL COMMENT '标签金额转交易金额使用的汇率。',
  `rate_source` varchar(64) DEFAULT NULL COMMENT '汇率来源，如 PLATFORM、CHANNEL、MERCHANT。',
  `rate_time` datetime(3) DEFAULT NULL COMMENT '汇率生效或报价时间。',
  `label_authorized_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计授权成功金额，标签币种单位。',
  `label_authorized_cancel_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计授权取消金额，标签币种单位。',
  `label_captured_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计请款成功金额，标签币种单位。',
  `label_refunded_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计退款成功金额，标签币种单位。',
  `label_chargeback_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计拒付金额，标签币种单位。',
  `label_available_capture_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '当前可请款金额，标签币种单位。',
  `label_available_refund_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '当前可退款金额，标签币种单位。',
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
  `clearing_status` varchar(32) NOT NULL DEFAULT 'NOT_CLEARED' COMMENT '生命周期清分聚合投影：NOT_CLEARED、PENDING、PARTIALLY_CLEARED、CLEARED、FAILED、NOT_REQUIRED',
  `clearing_complete_time` datetime(3) DEFAULT NULL COMMENT '生命周期最近一次清分聚合完成时间',
  `channel_match_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '渠道结果勾兑/查询确认状态；定时任务按该字段调用渠道查询API同步一致。',
  `channel_match_result` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认结果，如 SAME、STATUS_CHANGED、AMOUNT_CHANGED、CHANNEL_NOT_FOUND。',
  `channel_match_count` int NOT NULL DEFAULT '0' COMMENT '渠道结果勾兑查询次数。',
  `last_channel_match_request_id` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认请求ID，关联 transaction_channel_request.request_id。',
  `last_channel_match_time` datetime(3) DEFAULT NULL COMMENT '最近一次渠道查询确认时间。',
  `next_channel_match_time` datetime(3) DEFAULT NULL COMMENT '下一次渠道查询确认计划时间。',
  `channel_match_fail_reason` varchar(512) DEFAULT NULL COMMENT '最近一次渠道查询确认失败原因。',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '最近一次结算或冲正批次号',
  `settlement_transaction_id` varchar(64) DEFAULT NULL COMMENT '当前结算快照来源的真实动作交易号',
  `settlement_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '当前结算快照来源的真实动作分片时间',
  `reconciliation_batch_no` varchar(64) DEFAULT NULL COMMENT '最近一次对账批次号。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID，关联 channel_info.id。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码，如 MPGS。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '交易使用的渠道MID配置ID。',
  `channel_merchant_id` varchar(128) DEFAULT NULL COMMENT '渠道真实商户号或渠道MID，来自MID元数据快照。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道侧主订单号。',
  `internal_risk_decision` varchar(32) DEFAULT NULL COMMENT '最近一次内风控决策，如 PASS、REJECT、REVIEW、REQUIRE_3DS；外风控结构化字段本版不设计。',
  `internal_risk_record_no` varchar(64) DEFAULT NULL COMMENT '最近一次内风控评估流水号；不承载外风控平台RequestID。',
  `merchant_website` varchar(512) DEFAULT NULL COMMENT '首次支付、授权或预授权请求中的商户网站原始URL，用于来源网址限定和查询回显',
  `callback_url` varchar(512) DEFAULT NULL COMMENT '商户异步通知地址明文；禁止完整写入日志。',
  `redirect_url` varchar(512) DEFAULT NULL COMMENT 'Hosted Checkout 结果页 Form POST 地址明文。',
  `language` varchar(20) DEFAULT NULL COMMENT 'Hosted Checkout 页面语言。',
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
  KEY `idx_merchant_order_id` (`merchant_id`,`merchant_order_id`,`transaction_type`),
  KEY `idx_clearing_time` (`clearing_status`,`transaction_date_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=202603000000015116 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易生命周期主单；同一笔原始交易的授权、增量授权、请款、退款、拒付等动作共用同一个 transaction_id。';

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
  `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种 ISO 4217 小数位精度。',
  `transaction_currency` char(3) NOT NULL COMMENT '系统交易币种，经过 DCC/EDC 后用于交易核心处理。',
  `transaction_amount` decimal(20,6) NOT NULL COMMENT '系统交易金额，经过 DCC/EDC 后用于交易核心处理。',
  `channel_request_currency` char(3) DEFAULT NULL COMMENT '上送渠道的币种。',
  `channel_request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道的金额。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '预计或最终结算币种。',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '最近真实动作最终结算金额',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '最近真实动作中1单位交易币种兑换的结算币种数量',
  `settlement_date` date DEFAULT NULL COMMENT '最近真实动作结算业务日期',
  `currency_exponent` tinyint NOT NULL COMMENT '交易币种小数位精度，如 USD=2、JPY=0。',
  `dcc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用 DCC，0否，1是。',
  `edc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用 EDC，0否，1是；渠道不支持商户币种时使用。',
  `transaction_rate` decimal(24,12) DEFAULT NULL COMMENT '标签金额转交易金额使用的汇率。',
  `rate_source` varchar(64) DEFAULT NULL COMMENT '汇率来源，如 PLATFORM、CHANNEL、MERCHANT。',
  `rate_time` datetime(3) DEFAULT NULL COMMENT '汇率生效或报价时间。',
  `label_authorized_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计授权成功金额，标签币种单位。',
  `label_authorized_cancel_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计授权取消金额，标签币种单位。',
  `label_captured_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计请款成功金额，标签币种单位。',
  `label_refunded_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计退款成功金额，标签币种单位。',
  `label_chargeback_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计拒付金额，标签币种单位。',
  `label_available_capture_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '当前可请款金额，标签币种单位。',
  `label_available_refund_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '当前可退款金额，标签币种单位。',
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
  `clearing_status` varchar(32) NOT NULL DEFAULT 'NOT_CLEARED' COMMENT '生命周期清分聚合投影：NOT_CLEARED、PENDING、PARTIALLY_CLEARED、CLEARED、FAILED、NOT_REQUIRED',
  `clearing_complete_time` datetime(3) DEFAULT NULL COMMENT '生命周期最近一次清分聚合完成时间',
  `channel_match_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '渠道结果勾兑/查询确认状态；定时任务按该字段调用渠道查询API同步一致。',
  `channel_match_result` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认结果，如 SAME、STATUS_CHANGED、AMOUNT_CHANGED、CHANNEL_NOT_FOUND。',
  `channel_match_count` int NOT NULL DEFAULT '0' COMMENT '渠道结果勾兑查询次数。',
  `last_channel_match_request_id` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认请求ID，关联 transaction_channel_request.request_id。',
  `last_channel_match_time` datetime(3) DEFAULT NULL COMMENT '最近一次渠道查询确认时间。',
  `next_channel_match_time` datetime(3) DEFAULT NULL COMMENT '下一次渠道查询确认计划时间。',
  `channel_match_fail_reason` varchar(512) DEFAULT NULL COMMENT '最近一次渠道查询确认失败原因。',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '最近一次结算或冲正批次号',
  `settlement_transaction_id` varchar(64) DEFAULT NULL COMMENT '当前结算快照来源的真实动作交易号',
  `settlement_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '当前结算快照来源的真实动作分片时间',
  `reconciliation_batch_no` varchar(64) DEFAULT NULL COMMENT '最近一次对账批次号。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID，关联 channel_info.id。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码，如 MPGS。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '交易使用的渠道MID配置ID。',
  `channel_merchant_id` varchar(128) DEFAULT NULL COMMENT '渠道真实商户号或渠道MID，来自MID元数据快照。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道侧主订单号。',
  `internal_risk_decision` varchar(32) DEFAULT NULL COMMENT '最近一次内风控决策，如 PASS、REJECT、REVIEW、REQUIRE_3DS；外风控结构化字段本版不设计。',
  `internal_risk_record_no` varchar(64) DEFAULT NULL COMMENT '最近一次内风控评估流水号；不承载外风控平台RequestID。',
  `merchant_website` varchar(512) DEFAULT NULL COMMENT '首次支付、授权或预授权请求中的商户网站原始URL，用于来源网址限定和查询回显',
  `callback_url` varchar(512) DEFAULT NULL COMMENT '商户异步通知地址明文；禁止完整写入日志。',
  `redirect_url` varchar(512) DEFAULT NULL COMMENT 'Hosted Checkout 结果页 Form POST 地址明文。',
  `language` varchar(20) DEFAULT NULL COMMENT 'Hosted Checkout 页面语言。',
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
  KEY `idx_merchant_order_id` (`merchant_id`,`merchant_order_id`,`transaction_type`),
  KEY `idx_clearing_time` (`clearing_status`,`transaction_date_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=202604000000048755 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易生命周期主单；同一笔原始交易的授权、增量授权、请款、退款、拒付等动作共用同一个 transaction_id。';

-- ----------------------------
-- Table structure for transaction_order_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_order_202701`;
CREATE TABLE `transaction_order_202701` (
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
  `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种 ISO 4217 小数位精度。',
  `transaction_currency` char(3) NOT NULL COMMENT '系统交易币种，经过 DCC/EDC 后用于交易核心处理。',
  `transaction_amount` decimal(20,6) NOT NULL COMMENT '系统交易金额，经过 DCC/EDC 后用于交易核心处理。',
  `channel_request_currency` char(3) DEFAULT NULL COMMENT '上送渠道的币种。',
  `channel_request_amount` decimal(20,6) DEFAULT NULL COMMENT '上送渠道的金额。',
  `settlement_currency` char(3) DEFAULT NULL COMMENT '预计或最终结算币种。',
  `settlement_amount` decimal(24,8) DEFAULT NULL COMMENT '最近真实动作最终结算金额',
  `settlement_rate` decimal(24,12) DEFAULT NULL COMMENT '最近真实动作中1单位交易币种兑换的结算币种数量',
  `settlement_date` date DEFAULT NULL COMMENT '最近真实动作结算业务日期',
  `currency_exponent` tinyint NOT NULL COMMENT '交易币种小数位精度，如 USD=2、JPY=0。',
  `dcc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用 DCC，0否，1是。',
  `edc_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用 EDC，0否，1是；渠道不支持商户币种时使用。',
  `transaction_rate` decimal(24,12) DEFAULT NULL COMMENT '标签金额转交易金额使用的汇率。',
  `rate_source` varchar(64) DEFAULT NULL COMMENT '汇率来源，如 PLATFORM、CHANNEL、MERCHANT。',
  `rate_time` datetime(3) DEFAULT NULL COMMENT '汇率生效或报价时间。',
  `label_authorized_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计授权成功金额，标签币种单位。',
  `label_authorized_cancel_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计授权取消金额，标签币种单位。',
  `label_captured_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计请款成功金额，标签币种单位。',
  `label_refunded_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计退款成功金额，标签币种单位。',
  `label_chargeback_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '累计拒付金额，标签币种单位。',
  `label_available_capture_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '当前可请款金额，标签币种单位。',
  `label_available_refund_amount` decimal(20,6) NOT NULL DEFAULT '0.000000' COMMENT '当前可退款金额，标签币种单位。',
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
  `clearing_status` varchar(32) NOT NULL DEFAULT 'NOT_CLEARED' COMMENT '生命周期清分聚合投影：NOT_CLEARED、PENDING、PARTIALLY_CLEARED、CLEARED、FAILED、NOT_REQUIRED',
  `clearing_complete_time` datetime(3) DEFAULT NULL COMMENT '生命周期最近一次清分聚合完成时间',
  `channel_match_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '渠道结果勾兑/查询确认状态；定时任务按该字段调用渠道查询API同步一致。',
  `channel_match_result` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认结果，如 SAME、STATUS_CHANGED、AMOUNT_CHANGED、CHANNEL_NOT_FOUND。',
  `channel_match_count` int NOT NULL DEFAULT '0' COMMENT '渠道结果勾兑查询次数。',
  `last_channel_match_request_id` varchar(64) DEFAULT NULL COMMENT '最近一次渠道查询确认请求ID，关联 transaction_channel_request.request_id。',
  `last_channel_match_time` datetime(3) DEFAULT NULL COMMENT '最近一次渠道查询确认时间。',
  `next_channel_match_time` datetime(3) DEFAULT NULL COMMENT '下一次渠道查询确认计划时间。',
  `channel_match_fail_reason` varchar(512) DEFAULT NULL COMMENT '最近一次渠道查询确认失败原因。',
  `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '最近一次结算或冲正批次号',
  `settlement_transaction_id` varchar(64) DEFAULT NULL COMMENT '当前结算快照来源的真实动作交易号',
  `settlement_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '当前结算快照来源的真实动作分片时间',
  `reconciliation_batch_no` varchar(64) DEFAULT NULL COMMENT '最近一次对账批次号。',
  `channel_id` bigint DEFAULT NULL COMMENT '渠道信息ID，关联 channel_info.id。',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码，如 MPGS。',
  `channel_mid_config_id` bigint DEFAULT NULL COMMENT '交易使用的渠道MID配置ID。',
  `channel_merchant_id` varchar(128) DEFAULT NULL COMMENT '渠道真实商户号或渠道MID，来自MID元数据快照。',
  `channel_order_no` varchar(128) DEFAULT NULL COMMENT '渠道侧主订单号。',
  `internal_risk_decision` varchar(32) DEFAULT NULL COMMENT '最近一次内风控决策，如 PASS、REJECT、REVIEW、REQUIRE_3DS；外风控结构化字段本版不设计。',
  `internal_risk_record_no` varchar(64) DEFAULT NULL COMMENT '最近一次内风控评估流水号；不承载外风控平台RequestID。',
  `merchant_website` varchar(512) DEFAULT NULL COMMENT '首次支付、授权或预授权请求中的商户网站原始URL，用于来源网址限定和查询回显',
  `callback_url` varchar(512) DEFAULT NULL COMMENT '商户异步通知地址明文；禁止完整写入日志。',
  `redirect_url` varchar(512) DEFAULT NULL COMMENT 'Hosted Checkout 结果页 Form POST 地址明文。',
  `language` varchar(20) DEFAULT NULL COMMENT 'Hosted Checkout 页面语言。',
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
  KEY `idx_merchant_order_id` (`merchant_id`,`merchant_order_id`,`transaction_type`),
  KEY `idx_clearing_time` (`clearing_status`,`transaction_date_time`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易生命周期主单；同一笔原始交易的授权、增量授权、请款、退款、拒付等动作共用同一个 transaction_id。';

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
  `first_name` varchar(32) DEFAULT NULL COMMENT '付款人名。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '付款人姓。',
  `phone` varchar(32) DEFAULT NULL COMMENT '付款人电话明文。',
  `email` varchar(64) DEFAULT NULL COMMENT '付款人邮箱明文。',
  `country` varchar(3) DEFAULT NULL COMMENT '付款人国家/地区。',
  `state` varchar(64) DEFAULT NULL COMMENT '付款人州、省或地区。',
  `city` varchar(64) DEFAULT NULL COMMENT '付款人城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '付款人街道地址。',
  `postal` varchar(32) DEFAULT NULL COMMENT '付款人邮编。',
  `ip_address` varchar(64) NOT NULL COMMENT '付款人客户端 IP 明文。',
  `session_id` varchar(128) DEFAULT NULL COMMENT '付款会话 ID 明文。',
  `browser_info_json` json DEFAULT NULL COMMENT '浏览器信息明文 JSON。',
  `user_agent` varchar(512) DEFAULT NULL COMMENT '付款人 User-Agent 明文。',
  `payer_email_hash` char(64) DEFAULT NULL COMMENT '付款人邮箱哈希。',
  `payer_phone_hash` char(64) DEFAULT NULL COMMENT '付款人手机号哈希。',
  `ip_address_hash` char(64) DEFAULT NULL COMMENT '付款人 IP 地址哈希。',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款人明文交易快照，供风控、审计和商户查询回显使用。';

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
  `first_name` varchar(32) DEFAULT NULL COMMENT '付款人名。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '付款人姓。',
  `phone` varchar(32) DEFAULT NULL COMMENT '付款人电话明文。',
  `email` varchar(64) DEFAULT NULL COMMENT '付款人邮箱明文。',
  `country` varchar(3) DEFAULT NULL COMMENT '付款人国家/地区。',
  `state` varchar(64) DEFAULT NULL COMMENT '付款人州、省或地区。',
  `city` varchar(64) DEFAULT NULL COMMENT '付款人城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '付款人街道地址。',
  `postal` varchar(32) DEFAULT NULL COMMENT '付款人邮编。',
  `ip_address` varchar(64) NOT NULL COMMENT '付款人客户端 IP 明文。',
  `session_id` varchar(128) DEFAULT NULL COMMENT '付款会话 ID 明文。',
  `browser_info_json` json DEFAULT NULL COMMENT '浏览器信息明文 JSON。',
  `user_agent` varchar(512) DEFAULT NULL COMMENT '付款人 User-Agent 明文。',
  `payer_email_hash` char(64) DEFAULT NULL COMMENT '付款人邮箱哈希。',
  `payer_phone_hash` char(64) DEFAULT NULL COMMENT '付款人手机号哈希。',
  `ip_address_hash` char(64) DEFAULT NULL COMMENT '付款人 IP 地址哈希。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202603000000014031 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款人明文交易快照，供风控、审计和商户查询回显使用。';

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
  `first_name` varchar(32) DEFAULT NULL COMMENT '付款人名。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '付款人姓。',
  `phone` varchar(32) DEFAULT NULL COMMENT '付款人电话明文。',
  `email` varchar(64) DEFAULT NULL COMMENT '付款人邮箱明文。',
  `country` varchar(3) DEFAULT NULL COMMENT '付款人国家/地区。',
  `state` varchar(64) DEFAULT NULL COMMENT '付款人州、省或地区。',
  `city` varchar(64) DEFAULT NULL COMMENT '付款人城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '付款人街道地址。',
  `postal` varchar(32) DEFAULT NULL COMMENT '付款人邮编。',
  `ip_address` varchar(64) NOT NULL COMMENT '付款人客户端 IP 明文。',
  `session_id` varchar(128) DEFAULT NULL COMMENT '付款会话 ID 明文。',
  `browser_info_json` json DEFAULT NULL COMMENT '浏览器信息明文 JSON。',
  `user_agent` varchar(512) DEFAULT NULL COMMENT '付款人 User-Agent 明文。',
  `payer_email_hash` char(64) DEFAULT NULL COMMENT '付款人邮箱哈希。',
  `payer_phone_hash` char(64) DEFAULT NULL COMMENT '付款人手机号哈希。',
  `ip_address_hash` char(64) DEFAULT NULL COMMENT '付款人 IP 地址哈希。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202604000000048703 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款人明文交易快照，供风控、审计和商户查询回显使用。';

-- ----------------------------
-- Table structure for transaction_payer_info_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_payer_info_202701`;
CREATE TABLE `transaction_payer_info_202701` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `payer_info_id` varchar(64) NOT NULL COMMENT '付款人信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `payer_id` varchar(64) DEFAULT NULL COMMENT '商户侧付款人ID或客户ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '付款人名。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '付款人姓。',
  `phone` varchar(32) DEFAULT NULL COMMENT '付款人电话明文。',
  `email` varchar(64) DEFAULT NULL COMMENT '付款人邮箱明文。',
  `country` varchar(3) DEFAULT NULL COMMENT '付款人国家/地区。',
  `state` varchar(64) DEFAULT NULL COMMENT '付款人州、省或地区。',
  `city` varchar(64) DEFAULT NULL COMMENT '付款人城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '付款人街道地址。',
  `postal` varchar(32) DEFAULT NULL COMMENT '付款人邮编。',
  `ip_address` varchar(64) NOT NULL COMMENT '付款人客户端 IP 明文。',
  `session_id` varchar(128) DEFAULT NULL COMMENT '付款会话 ID 明文。',
  `browser_info_json` json DEFAULT NULL COMMENT '浏览器信息明文 JSON。',
  `user_agent` varchar(512) DEFAULT NULL COMMENT '付款人 User-Agent 明文。',
  `payer_email_hash` char(64) DEFAULT NULL COMMENT '付款人邮箱哈希。',
  `payer_phone_hash` char(64) DEFAULT NULL COMMENT '付款人手机号哈希。',
  `ip_address_hash` char(64) DEFAULT NULL COMMENT '付款人 IP 地址哈希。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款人明文交易快照，供风控、审计和商户查询回显使用。';

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
) ENGINE=InnoDB AUTO_INCREMENT=202603000000015408 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存卡、钱包、令牌、渠道绑卡标识等支付工具摘要。';

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
) ENGINE=InnoDB AUTO_INCREMENT=202604000000061074 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存卡、钱包、令牌、渠道绑卡标识等支付工具摘要。';

-- ----------------------------
-- Table structure for transaction_payment_method_info_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_payment_method_info_202701`;
CREATE TABLE `transaction_payment_method_info_202701` (
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存卡、钱包、令牌、渠道绑卡标识等支付工具摘要。';

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
) ENGINE=InnoDB AUTO_INCREMENT=202603000000014011 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户上送的商品/订单项目明细，支撑交易详情页商品列表、风控摘要、争议和人工排查。';

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
) ENGINE=InnoDB AUTO_INCREMENT=202604000000048703 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户上送的商品/订单项目明细，支撑交易详情页商品列表、风控摘要、争议和人工排查。';

-- ----------------------------
-- Table structure for transaction_product_item_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_product_item_202701`;
CREATE TABLE `transaction_product_item_202701` (
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存商户上送的商品/订单项目明细，支撑交易详情页商品列表、风控摘要、争议和人工排查。';

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
-- Table structure for transaction_reserve_clearing_detail
-- ----------------------------
DROP TABLE IF EXISTS `transaction_reserve_clearing_detail`;
CREATE TABLE `transaction_reserve_clearing_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID',
  `reserve_clearing_detail_no` varchar(64) NOT NULL COMMENT '保证金清分明细号',
  `finance_state_id` varchar(64) NOT NULL COMMENT '所属当前动作清分汇总ID',
  `transaction_id` varchar(64) NOT NULL COMMENT '产生本明细的动作级平台交易号',
  `operation_id` varchar(64) NOT NULL COMMENT '交易生命周期关联号',
  `original_transaction_id` varchar(64) NOT NULL COMMENT '原支付或原请款动作交易号',
  `original_transaction_date_time` datetime(3) NOT NULL COMMENT '原支付动作业务时间，用于精确路由保证金状态',
  `source_reserve_detail_no` varchar(64) DEFAULT NULL COMMENT 'RETURN、RELEASE引用的原HOLD保证金明细号',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `payment_type` varchar(32) NOT NULL COMMENT '保证金事实形成时冻结的支付类型',
  `payment_method` varchar(32) NOT NULL COMMENT '保证金事实形成时冻结的支付方式',
  `transaction_type` varchar(32) NOT NULL COMMENT '产生本保证金事实的交易动作类型',
  `clearing_revision` int NOT NULL COMMENT '所属当前动作清分修订号，从1递增',
  `line_no` int NOT NULL COMMENT '同一修订内保证金稳定明细序号，从1递增',
  `reserve_action_type` varchar(16) NOT NULL COMMENT 'HOLD、RETURN、RELEASE、ADJUSTMENT',
  `item_code` varchar(128) NOT NULL COMMENT '稳定业务项编码，例如RESERVE:HOLD或RESERVE:RETURN:原HOLD明细号',
  `item_name` varchar(128) NOT NULL COMMENT '保证金项目名称快照',
  `direction` varchar(8) NOT NULL COMMENT '商户视角：HOLD为DEBIT，RETURN或RELEASE为CREDIT',
  `reserve_currency` char(3) NOT NULL COMMENT '保证金负债币种，固定等于原支付标签币种',
  `reserve_currency_exponent` tinyint NOT NULL COMMENT '保证金币种ISO小数位',
  `basis_amount` decimal(24,8) NOT NULL COMMENT '支付或本次退款保证金币种基数，始终非负',
  `reserve_rate` decimal(12,8) NOT NULL COMMENT '原支付清分时固化的保证金比例，例如10表示10%',
  `retained_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '本明细HOLD扣留金额，其它动作固定0',
  `returned_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '本明细RETURN返还金额，其它动作固定0',
  `released_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '本明细RELEASE释放金额，其它动作固定0',
  `adjustment_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '本明细ADJUSTMENT绝对金额，其它动作固定0',
  `remaining_amount` decimal(24,8) NOT NULL COMMENT '本明细入账后原HOLD剩余保证金负债金额',
  `fee_plan_id` bigint NOT NULL COMMENT '原支付费用方案ID快照',
  `fee_plan_version_id` bigint NOT NULL COMMENT '原支付不可变费用版本ID快照',
  `fee_plan_version_no` int NOT NULL COMMENT '原支付费用版本号快照',
  `reserve_snapshot_hash` char(64) NOT NULL COMMENT '原支付保证金配置规范化快照SHA-256',
  `reserve_basis` varchar(32) NOT NULL COMMENT '保证金基数快照，当前固定LABEL_AMOUNT',
  `reserve_delay_unit` char(1) DEFAULT NULL COMMENT '原支付保证金留存周期单位T或D',
  `reserve_delay_days` int DEFAULT NULL COMMENT '原支付保证金留存天数',
  `rounding_mode` varchar(16) NOT NULL COMMENT '原支付保证金舍入规则快照',
  `formula_snapshot` varchar(1000) NOT NULL COMMENT '标签币种保证金扣留、返还或释放计算公式快照',
  `expected_reserve_release_date` date DEFAULT NULL COMMENT '预计保证金释放业务日期',
  `record_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、SUPERSEDED',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '产生本明细的当前动作业务时间，季度分片键',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '当前动作业务时间对应UTC时间',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '当前动作业务时区',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reserve_clearing_detail_no` (`reserve_clearing_detail_no`),
  UNIQUE KEY `uk_reserve_revision_line` (`finance_state_id`,`clearing_revision`,`line_no`),
  UNIQUE KEY `uk_reserve_transaction_item` (`transaction_id`,`clearing_revision`,`item_code`),
  KEY `idx_reserve_transaction_time` (`transaction_id`,`transaction_date_time`,`clearing_revision`,`id`),
  KEY `idx_reserve_original` (`original_transaction_id`,`original_transaction_date_time`,`source_reserve_detail_no`,`transaction_date_time`,`id`),
  KEY `idx_reserve_fee_version` (`fee_plan_version_id`,`merchant_id`,`transaction_date_time`,`id`),
  KEY `idx_reserve_settlement_eligible` (`merchant_id`,`reserve_currency`,`record_status`,`expected_reserve_release_date`,`id`),
  CONSTRAINT `chk_reserve_detail_amount_tpl` CHECK (((`basis_amount` >= 0) and (`retained_amount` >= 0) and (`returned_amount` >= 0) and (`released_amount` >= 0) and (`adjustment_amount` >= 0) and (`remaining_amount` >= 0) and (`reserve_rate` >= 0) and (`reserve_rate` <= 100) and (`reserve_currency_exponent` between 0 and 8))),
  CONSTRAINT `chk_reserve_detail_enum_tpl` CHECK (((`reserve_action_type` in (_utf8mb4'HOLD',_utf8mb4'RETURN',_utf8mb4'RELEASE',_utf8mb4'ADJUSTMENT')) and (`direction` in (_utf8mb4'CREDIT',_utf8mb4'DEBIT')) and (`reserve_basis` = _utf8mb4'LABEL_AMOUNT') and (`rounding_mode` in (_utf8mb4'HALF_UP',_utf8mb4'HALF_EVEN',_utf8mb4'DOWN')) and (`record_status` in (_utf8mb4'ACTIVE',_utf8mb4'SUPERSEDED')))),
  CONSTRAINT `chk_reserve_detail_revision_tpl` CHECK (((`clearing_revision` >= 1) and (`line_no` >= 1))),
  CONSTRAINT `chk_reserve_detail_source_tpl` CHECK ((((`reserve_action_type` = _utf8mb4'HOLD') and (`direction` = _utf8mb4'DEBIT') and (`original_transaction_id` = `transaction_id`) and (`source_reserve_detail_no` is null) and (`retained_amount` > 0) and (`returned_amount` = 0) and (`released_amount` = 0) and (`adjustment_amount` = 0)) or ((`reserve_action_type` in (_utf8mb4'RETURN',_utf8mb4'RELEASE')) and (`direction` = _utf8mb4'CREDIT') and (`source_reserve_detail_no` is not null) and (`retained_amount` = 0) and (`adjustment_amount` = 0) and (((`reserve_action_type` = _utf8mb4'RETURN') and (`returned_amount` > 0) and (`released_amount` = 0)) or ((`reserve_action_type` = _utf8mb4'RELEASE') and (`released_amount` > 0) and (`returned_amount` = 0)))) or ((`reserve_action_type` = _utf8mb4'ADJUSTMENT') and (`adjustment_amount` > 0) and (`retained_amount` = 0) and (`returned_amount` = 0) and (`released_amount` = 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动作级不可变保证金清分明细；与交易清分明细完全分离，跟随当前动作季度分片';

-- ----------------------------
-- Table structure for transaction_reserve_clearing_detail_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_reserve_clearing_detail_202603`;
CREATE TABLE `transaction_reserve_clearing_detail_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID',
  `reserve_clearing_detail_no` varchar(64) NOT NULL COMMENT '保证金清分明细号',
  `finance_state_id` varchar(64) NOT NULL COMMENT '所属当前动作清分汇总ID',
  `transaction_id` varchar(64) NOT NULL COMMENT '产生本明细的动作级平台交易号',
  `operation_id` varchar(64) NOT NULL COMMENT '交易生命周期关联号',
  `original_transaction_id` varchar(64) NOT NULL COMMENT '原支付或原请款动作交易号',
  `original_transaction_date_time` datetime(3) NOT NULL COMMENT '原支付动作业务时间，用于精确路由保证金状态',
  `source_reserve_detail_no` varchar(64) DEFAULT NULL COMMENT 'RETURN、RELEASE引用的原HOLD保证金明细号',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `payment_type` varchar(32) NOT NULL COMMENT '保证金事实形成时冻结的支付类型',
  `payment_method` varchar(32) NOT NULL COMMENT '保证金事实形成时冻结的支付方式',
  `transaction_type` varchar(32) NOT NULL COMMENT '产生本保证金事实的交易动作类型',
  `clearing_revision` int NOT NULL COMMENT '所属当前动作清分修订号，从1递增',
  `line_no` int NOT NULL COMMENT '同一修订内保证金稳定明细序号，从1递增',
  `reserve_action_type` varchar(16) NOT NULL COMMENT 'HOLD、RETURN、RELEASE、ADJUSTMENT',
  `item_code` varchar(128) NOT NULL COMMENT '稳定业务项编码，例如RESERVE:HOLD或RESERVE:RETURN:原HOLD明细号',
  `item_name` varchar(128) NOT NULL COMMENT '保证金项目名称快照',
  `direction` varchar(8) NOT NULL COMMENT '商户视角：HOLD为DEBIT，RETURN或RELEASE为CREDIT',
  `reserve_currency` char(3) NOT NULL COMMENT '保证金负债币种，固定等于原支付标签币种',
  `reserve_currency_exponent` tinyint NOT NULL COMMENT '保证金币种ISO小数位',
  `basis_amount` decimal(24,8) NOT NULL COMMENT '支付或本次退款保证金币种基数，始终非负',
  `reserve_rate` decimal(12,8) NOT NULL COMMENT '原支付清分时固化的保证金比例，例如10表示10%',
  `retained_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '本明细HOLD扣留金额，其它动作固定0',
  `returned_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '本明细RETURN返还金额，其它动作固定0',
  `released_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '本明细RELEASE释放金额，其它动作固定0',
  `adjustment_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '本明细ADJUSTMENT绝对金额，其它动作固定0',
  `remaining_amount` decimal(24,8) NOT NULL COMMENT '本明细入账后原HOLD剩余保证金负债金额',
  `fee_plan_id` bigint NOT NULL COMMENT '原支付费用方案ID快照',
  `fee_plan_version_id` bigint NOT NULL COMMENT '原支付不可变费用版本ID快照',
  `fee_plan_version_no` int NOT NULL COMMENT '原支付费用版本号快照',
  `reserve_snapshot_hash` char(64) NOT NULL COMMENT '原支付保证金配置规范化快照SHA-256',
  `reserve_basis` varchar(32) NOT NULL COMMENT '保证金基数快照，当前固定LABEL_AMOUNT',
  `reserve_delay_unit` char(1) DEFAULT NULL COMMENT '原支付保证金留存周期单位T或D',
  `reserve_delay_days` int DEFAULT NULL COMMENT '原支付保证金留存天数',
  `rounding_mode` varchar(16) NOT NULL COMMENT '原支付保证金舍入规则快照',
  `formula_snapshot` varchar(1000) NOT NULL COMMENT '标签币种保证金扣留、返还或释放计算公式快照',
  `expected_reserve_release_date` date DEFAULT NULL COMMENT '预计保证金释放业务日期',
  `record_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、SUPERSEDED',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '产生本明细的当前动作业务时间，季度分片键',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '当前动作业务时间对应UTC时间',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '当前动作业务时区',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reserve_clearing_detail_no` (`reserve_clearing_detail_no`),
  UNIQUE KEY `uk_reserve_revision_line` (`finance_state_id`,`clearing_revision`,`line_no`),
  UNIQUE KEY `uk_reserve_transaction_item` (`transaction_id`,`clearing_revision`,`item_code`),
  KEY `idx_reserve_transaction_time` (`transaction_id`,`transaction_date_time`,`clearing_revision`,`id`),
  KEY `idx_reserve_original` (`original_transaction_id`,`original_transaction_date_time`,`source_reserve_detail_no`,`transaction_date_time`,`id`),
  KEY `idx_reserve_fee_version` (`fee_plan_version_id`,`merchant_id`,`transaction_date_time`,`id`),
  KEY `idx_reserve_settlement_eligible` (`merchant_id`,`reserve_currency`,`record_status`,`expected_reserve_release_date`,`id`),
  CONSTRAINT `chk_reserve_detail_amount_202603` CHECK (((`basis_amount` >= 0) and (`retained_amount` >= 0) and (`returned_amount` >= 0) and (`released_amount` >= 0) and (`adjustment_amount` >= 0) and (`remaining_amount` >= 0) and (`reserve_rate` >= 0) and (`reserve_rate` <= 100) and (`reserve_currency_exponent` between 0 and 8))),
  CONSTRAINT `chk_reserve_detail_enum_202603` CHECK (((`reserve_action_type` in (_utf8mb4'HOLD',_utf8mb4'RETURN',_utf8mb4'RELEASE',_utf8mb4'ADJUSTMENT')) and (`direction` in (_utf8mb4'CREDIT',_utf8mb4'DEBIT')) and (`reserve_basis` = _utf8mb4'LABEL_AMOUNT') and (`rounding_mode` in (_utf8mb4'HALF_UP',_utf8mb4'HALF_EVEN',_utf8mb4'DOWN')) and (`record_status` in (_utf8mb4'ACTIVE',_utf8mb4'SUPERSEDED')))),
  CONSTRAINT `chk_reserve_detail_revision_202603` CHECK (((`clearing_revision` >= 1) and (`line_no` >= 1))),
  CONSTRAINT `chk_reserve_detail_source_202603` CHECK ((((`reserve_action_type` = _utf8mb4'HOLD') and (`direction` = _utf8mb4'DEBIT') and (`original_transaction_id` = `transaction_id`) and (`source_reserve_detail_no` is null) and (`retained_amount` > 0) and (`returned_amount` = 0) and (`released_amount` = 0) and (`adjustment_amount` = 0)) or ((`reserve_action_type` in (_utf8mb4'RETURN',_utf8mb4'RELEASE')) and (`direction` = _utf8mb4'CREDIT') and (`source_reserve_detail_no` is not null) and (`retained_amount` = 0) and (`adjustment_amount` = 0) and (((`reserve_action_type` = _utf8mb4'RETURN') and (`returned_amount` > 0) and (`released_amount` = 0)) or ((`reserve_action_type` = _utf8mb4'RELEASE') and (`released_amount` > 0) and (`returned_amount` = 0)))) or ((`reserve_action_type` = _utf8mb4'ADJUSTMENT') and (`adjustment_amount` > 0) and (`retained_amount` = 0) and (`returned_amount` = 0) and (`released_amount` = 0))))
) ENGINE=InnoDB AUTO_INCREMENT=202603200000114986 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='2026年第3季度动作级保证金清分明细';

-- ----------------------------
-- Table structure for transaction_reserve_clearing_detail_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_reserve_clearing_detail_202604`;
CREATE TABLE `transaction_reserve_clearing_detail_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID',
  `reserve_clearing_detail_no` varchar(64) NOT NULL COMMENT '保证金清分明细号',
  `finance_state_id` varchar(64) NOT NULL COMMENT '所属当前动作清分汇总ID',
  `transaction_id` varchar(64) NOT NULL COMMENT '产生本明细的动作级平台交易号',
  `operation_id` varchar(64) NOT NULL COMMENT '交易生命周期关联号',
  `original_transaction_id` varchar(64) NOT NULL COMMENT '原支付或原请款动作交易号',
  `original_transaction_date_time` datetime(3) NOT NULL COMMENT '原支付动作业务时间，用于精确路由保证金状态',
  `source_reserve_detail_no` varchar(64) DEFAULT NULL COMMENT 'RETURN、RELEASE引用的原HOLD保证金明细号',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `payment_type` varchar(32) NOT NULL COMMENT '保证金事实形成时冻结的支付类型',
  `payment_method` varchar(32) NOT NULL COMMENT '保证金事实形成时冻结的支付方式',
  `transaction_type` varchar(32) NOT NULL COMMENT '产生本保证金事实的交易动作类型',
  `clearing_revision` int NOT NULL COMMENT '所属当前动作清分修订号，从1递增',
  `line_no` int NOT NULL COMMENT '同一修订内保证金稳定明细序号，从1递增',
  `reserve_action_type` varchar(16) NOT NULL COMMENT 'HOLD、RETURN、RELEASE、ADJUSTMENT',
  `item_code` varchar(128) NOT NULL COMMENT '稳定业务项编码，例如RESERVE:HOLD或RESERVE:RETURN:原HOLD明细号',
  `item_name` varchar(128) NOT NULL COMMENT '保证金项目名称快照',
  `direction` varchar(8) NOT NULL COMMENT '商户视角：HOLD为DEBIT，RETURN或RELEASE为CREDIT',
  `reserve_currency` char(3) NOT NULL COMMENT '保证金负债币种，固定等于原支付标签币种',
  `reserve_currency_exponent` tinyint NOT NULL COMMENT '保证金币种ISO小数位',
  `basis_amount` decimal(24,8) NOT NULL COMMENT '支付或本次退款保证金币种基数，始终非负',
  `reserve_rate` decimal(12,8) NOT NULL COMMENT '原支付清分时固化的保证金比例，例如10表示10%',
  `retained_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '本明细HOLD扣留金额，其它动作固定0',
  `returned_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '本明细RETURN返还金额，其它动作固定0',
  `released_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '本明细RELEASE释放金额，其它动作固定0',
  `adjustment_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '本明细ADJUSTMENT绝对金额，其它动作固定0',
  `remaining_amount` decimal(24,8) NOT NULL COMMENT '本明细入账后原HOLD剩余保证金负债金额',
  `fee_plan_id` bigint NOT NULL COMMENT '原支付费用方案ID快照',
  `fee_plan_version_id` bigint NOT NULL COMMENT '原支付不可变费用版本ID快照',
  `fee_plan_version_no` int NOT NULL COMMENT '原支付费用版本号快照',
  `reserve_snapshot_hash` char(64) NOT NULL COMMENT '原支付保证金配置规范化快照SHA-256',
  `reserve_basis` varchar(32) NOT NULL COMMENT '保证金基数快照，当前固定LABEL_AMOUNT',
  `reserve_delay_unit` char(1) DEFAULT NULL COMMENT '原支付保证金留存周期单位T或D',
  `reserve_delay_days` int DEFAULT NULL COMMENT '原支付保证金留存天数',
  `rounding_mode` varchar(16) NOT NULL COMMENT '原支付保证金舍入规则快照',
  `formula_snapshot` varchar(1000) NOT NULL COMMENT '标签币种保证金扣留、返还或释放计算公式快照',
  `expected_reserve_release_date` date DEFAULT NULL COMMENT '预计保证金释放业务日期',
  `record_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、SUPERSEDED',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '产生本明细的当前动作业务时间，季度分片键',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '当前动作业务时间对应UTC时间',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '当前动作业务时区',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reserve_clearing_detail_no` (`reserve_clearing_detail_no`),
  UNIQUE KEY `uk_reserve_revision_line` (`finance_state_id`,`clearing_revision`,`line_no`),
  UNIQUE KEY `uk_reserve_transaction_item` (`transaction_id`,`clearing_revision`,`item_code`),
  KEY `idx_reserve_transaction_time` (`transaction_id`,`transaction_date_time`,`clearing_revision`,`id`),
  KEY `idx_reserve_original` (`original_transaction_id`,`original_transaction_date_time`,`source_reserve_detail_no`,`transaction_date_time`,`id`),
  KEY `idx_reserve_fee_version` (`fee_plan_version_id`,`merchant_id`,`transaction_date_time`,`id`),
  KEY `idx_reserve_settlement_eligible` (`merchant_id`,`reserve_currency`,`record_status`,`expected_reserve_release_date`,`id`),
  CONSTRAINT `chk_reserve_detail_amount_202604` CHECK (((`basis_amount` >= 0) and (`retained_amount` >= 0) and (`returned_amount` >= 0) and (`released_amount` >= 0) and (`adjustment_amount` >= 0) and (`remaining_amount` >= 0) and (`reserve_rate` >= 0) and (`reserve_rate` <= 100) and (`reserve_currency_exponent` between 0 and 8))),
  CONSTRAINT `chk_reserve_detail_enum_202604` CHECK (((`reserve_action_type` in (_utf8mb4'HOLD',_utf8mb4'RETURN',_utf8mb4'RELEASE',_utf8mb4'ADJUSTMENT')) and (`direction` in (_utf8mb4'CREDIT',_utf8mb4'DEBIT')) and (`reserve_basis` = _utf8mb4'LABEL_AMOUNT') and (`rounding_mode` in (_utf8mb4'HALF_UP',_utf8mb4'HALF_EVEN',_utf8mb4'DOWN')) and (`record_status` in (_utf8mb4'ACTIVE',_utf8mb4'SUPERSEDED')))),
  CONSTRAINT `chk_reserve_detail_revision_202604` CHECK (((`clearing_revision` >= 1) and (`line_no` >= 1))),
  CONSTRAINT `chk_reserve_detail_source_202604` CHECK ((((`reserve_action_type` = _utf8mb4'HOLD') and (`direction` = _utf8mb4'DEBIT') and (`original_transaction_id` = `transaction_id`) and (`source_reserve_detail_no` is null) and (`retained_amount` > 0) and (`returned_amount` = 0) and (`released_amount` = 0) and (`adjustment_amount` = 0)) or ((`reserve_action_type` in (_utf8mb4'RETURN',_utf8mb4'RELEASE')) and (`direction` = _utf8mb4'CREDIT') and (`source_reserve_detail_no` is not null) and (`retained_amount` = 0) and (`adjustment_amount` = 0) and (((`reserve_action_type` = _utf8mb4'RETURN') and (`returned_amount` > 0) and (`released_amount` = 0)) or ((`reserve_action_type` = _utf8mb4'RELEASE') and (`released_amount` > 0) and (`returned_amount` = 0)))) or ((`reserve_action_type` = _utf8mb4'ADJUSTMENT') and (`adjustment_amount` > 0) and (`retained_amount` = 0) and (`returned_amount` = 0) and (`released_amount` = 0))))
) ENGINE=InnoDB AUTO_INCREMENT=202604200000088798 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='2026年第4季度动作级保证金清分明细';

-- ----------------------------
-- Table structure for transaction_reserve_clearing_detail_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_reserve_clearing_detail_202701`;
CREATE TABLE `transaction_reserve_clearing_detail_202701` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID',
  `reserve_clearing_detail_no` varchar(64) NOT NULL COMMENT '保证金清分明细号',
  `finance_state_id` varchar(64) NOT NULL COMMENT '所属当前动作清分汇总ID',
  `transaction_id` varchar(64) NOT NULL COMMENT '产生本明细的动作级平台交易号',
  `operation_id` varchar(64) NOT NULL COMMENT '交易生命周期关联号',
  `original_transaction_id` varchar(64) NOT NULL COMMENT '原支付或原请款动作交易号',
  `original_transaction_date_time` datetime(3) NOT NULL COMMENT '原支付动作业务时间，用于精确路由保证金状态',
  `source_reserve_detail_no` varchar(64) DEFAULT NULL COMMENT 'RETURN、RELEASE引用的原HOLD保证金明细号',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `payment_type` varchar(32) NOT NULL COMMENT '保证金事实形成时冻结的支付类型',
  `payment_method` varchar(32) NOT NULL COMMENT '保证金事实形成时冻结的支付方式',
  `transaction_type` varchar(32) NOT NULL COMMENT '产生本保证金事实的交易动作类型',
  `clearing_revision` int NOT NULL COMMENT '所属当前动作清分修订号，从1递增',
  `line_no` int NOT NULL COMMENT '同一修订内保证金稳定明细序号，从1递增',
  `reserve_action_type` varchar(16) NOT NULL COMMENT 'HOLD、RETURN、RELEASE、ADJUSTMENT',
  `item_code` varchar(128) NOT NULL COMMENT '稳定业务项编码，例如RESERVE:HOLD或RESERVE:RETURN:原HOLD明细号',
  `item_name` varchar(128) NOT NULL COMMENT '保证金项目名称快照',
  `direction` varchar(8) NOT NULL COMMENT '商户视角：HOLD为DEBIT，RETURN或RELEASE为CREDIT',
  `reserve_currency` char(3) NOT NULL COMMENT '保证金负债币种，固定等于原支付标签币种',
  `reserve_currency_exponent` tinyint NOT NULL COMMENT '保证金币种ISO小数位',
  `basis_amount` decimal(24,8) NOT NULL COMMENT '支付或本次退款保证金币种基数，始终非负',
  `reserve_rate` decimal(12,8) NOT NULL COMMENT '原支付清分时固化的保证金比例，例如10表示10%',
  `retained_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '本明细HOLD扣留金额，其它动作固定0',
  `returned_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '本明细RETURN返还金额，其它动作固定0',
  `released_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '本明细RELEASE释放金额，其它动作固定0',
  `adjustment_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '本明细ADJUSTMENT绝对金额，其它动作固定0',
  `remaining_amount` decimal(24,8) NOT NULL COMMENT '本明细入账后原HOLD剩余保证金负债金额',
  `fee_plan_id` bigint NOT NULL COMMENT '原支付费用方案ID快照',
  `fee_plan_version_id` bigint NOT NULL COMMENT '原支付不可变费用版本ID快照',
  `fee_plan_version_no` int NOT NULL COMMENT '原支付费用版本号快照',
  `reserve_snapshot_hash` char(64) NOT NULL COMMENT '原支付保证金配置规范化快照SHA-256',
  `reserve_basis` varchar(32) NOT NULL COMMENT '保证金基数快照，当前固定LABEL_AMOUNT',
  `reserve_delay_unit` char(1) DEFAULT NULL COMMENT '原支付保证金留存周期单位T或D',
  `reserve_delay_days` int DEFAULT NULL COMMENT '原支付保证金留存天数',
  `rounding_mode` varchar(16) NOT NULL COMMENT '原支付保证金舍入规则快照',
  `formula_snapshot` varchar(1000) NOT NULL COMMENT '标签币种保证金扣留、返还或释放计算公式快照',
  `expected_reserve_release_date` date DEFAULT NULL COMMENT '预计保证金释放业务日期',
  `record_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、SUPERSEDED',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '产生本明细的当前动作业务时间，季度分片键',
  `transaction_utc_time` datetime(3) NOT NULL COMMENT '当前动作业务时间对应UTC时间',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '当前动作业务时区',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reserve_clearing_detail_no` (`reserve_clearing_detail_no`),
  UNIQUE KEY `uk_reserve_revision_line` (`finance_state_id`,`clearing_revision`,`line_no`),
  UNIQUE KEY `uk_reserve_transaction_item` (`transaction_id`,`clearing_revision`,`item_code`),
  KEY `idx_reserve_transaction_time` (`transaction_id`,`transaction_date_time`,`clearing_revision`,`id`),
  KEY `idx_reserve_original` (`original_transaction_id`,`original_transaction_date_time`,`source_reserve_detail_no`,`transaction_date_time`,`id`),
  KEY `idx_reserve_fee_version` (`fee_plan_version_id`,`merchant_id`,`transaction_date_time`,`id`),
  KEY `idx_reserve_settlement_eligible` (`merchant_id`,`reserve_currency`,`record_status`,`expected_reserve_release_date`,`id`),
  CONSTRAINT `transaction_reserve_clearing_detail_202701_chk_1` CHECK (((`basis_amount` >= 0) and (`retained_amount` >= 0) and (`returned_amount` >= 0) and (`released_amount` >= 0) and (`adjustment_amount` >= 0) and (`remaining_amount` >= 0) and (`reserve_rate` >= 0) and (`reserve_rate` <= 100) and (`reserve_currency_exponent` between 0 and 8))),
  CONSTRAINT `transaction_reserve_clearing_detail_202701_chk_2` CHECK (((`reserve_action_type` in (_utf8mb4'HOLD',_utf8mb4'RETURN',_utf8mb4'RELEASE',_utf8mb4'ADJUSTMENT')) and (`direction` in (_utf8mb4'CREDIT',_utf8mb4'DEBIT')) and (`reserve_basis` = _utf8mb4'LABEL_AMOUNT') and (`rounding_mode` in (_utf8mb4'HALF_UP',_utf8mb4'HALF_EVEN',_utf8mb4'DOWN')) and (`record_status` in (_utf8mb4'ACTIVE',_utf8mb4'SUPERSEDED')))),
  CONSTRAINT `transaction_reserve_clearing_detail_202701_chk_3` CHECK (((`clearing_revision` >= 1) and (`line_no` >= 1))),
  CONSTRAINT `transaction_reserve_clearing_detail_202701_chk_4` CHECK ((((`reserve_action_type` = _utf8mb4'HOLD') and (`direction` = _utf8mb4'DEBIT') and (`original_transaction_id` = `transaction_id`) and (`source_reserve_detail_no` is null) and (`retained_amount` > 0) and (`returned_amount` = 0) and (`released_amount` = 0) and (`adjustment_amount` = 0)) or ((`reserve_action_type` in (_utf8mb4'RETURN',_utf8mb4'RELEASE')) and (`direction` = _utf8mb4'CREDIT') and (`source_reserve_detail_no` is not null) and (`retained_amount` = 0) and (`adjustment_amount` = 0) and (((`reserve_action_type` = _utf8mb4'RETURN') and (`returned_amount` > 0) and (`released_amount` = 0)) or ((`reserve_action_type` = _utf8mb4'RELEASE') and (`released_amount` > 0) and (`returned_amount` = 0)))) or ((`reserve_action_type` = _utf8mb4'ADJUSTMENT') and (`adjustment_amount` > 0) and (`retained_amount` = 0) and (`returned_amount` = 0) and (`released_amount` = 0))))
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动作级不可变保证金清分明细；与交易清分明细完全分离，跟随当前动作季度分片';

-- ----------------------------
-- Table structure for transaction_reserve_clearing_state
-- ----------------------------
DROP TABLE IF EXISTS `transaction_reserve_clearing_state`;
CREATE TABLE `transaction_reserve_clearing_state` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID',
  `reserve_state_id` varchar(64) NOT NULL COMMENT '保证金清分状态ID',
  `original_transaction_id` varchar(64) NOT NULL COMMENT '原支付或原请款动作交易号',
  `operation_id` varchar(64) NOT NULL COMMENT '交易生命周期关联号',
  `original_finance_state_id` varchar(64) NOT NULL COMMENT '原支付动作清分汇总ID',
  `original_hold_detail_no` varchar(64) NOT NULL COMMENT '原HOLD保证金清分明细号',
  `original_fee_plan_version_id` bigint NOT NULL COMMENT '原支付不可变费用版本ID',
  `original_reserve_snapshot_hash` char(64) NOT NULL COMMENT '原支付保证金配置快照SHA-256',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `reserve_currency` char(3) NOT NULL COMMENT '保证金负债币种，固定等于原支付标签币种',
  `reserve_currency_exponent` tinyint NOT NULL COMMENT '保证金币种ISO小数位',
  `original_basis_amount` decimal(24,8) NOT NULL COMMENT '原支付保证金计提基数',
  `original_reserve_rate` decimal(12,8) NOT NULL COMMENT '原支付清分时固化的保证金比例',
  `original_rounding_mode` varchar(16) NOT NULL COMMENT '原支付保证金舍入规则',
  `retained_amount` decimal(24,8) NOT NULL COMMENT '原支付保证金扣留金额',
  `returned_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '累计退款返还金额',
  `released_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '累计到期释放金额',
  `debit_adjustment_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '经双人复核增加的累计保证金负债',
  `credit_adjustment_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '经双人复核减少的累计保证金负债',
  `remaining_amount` decimal(24,8) NOT NULL COMMENT '剩余保证金负债金额',
  `expected_reserve_release_date` date DEFAULT NULL COMMENT '预计保证金释放业务日期',
  `reserve_status` varchar(24) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN、FULLY_RETURNED、FULLY_RELEASED、ADJUSTED',
  `last_return_transaction_id` varchar(64) DEFAULT NULL COMMENT '最近成功返还保证金的退款动作交易号',
  `last_return_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '最近成功返还动作时间',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '原支付动作业务时间，统一季度分片键',
  `original_transaction_utc_time` datetime(3) NOT NULL COMMENT '原支付动作业务时间对应UTC时间',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '原支付动作业务时区',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '并发退款行锁后的CAS版本号',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reserve_state_id` (`reserve_state_id`),
  UNIQUE KEY `uk_reserve_state_original_transaction` (`original_transaction_id`),
  UNIQUE KEY `uk_reserve_state_hold_detail` (`original_hold_detail_no`),
  KEY `idx_reserve_state_operation` (`operation_id`,`transaction_date_time`,`id`),
  KEY `idx_reserve_state_merchant_release` (`merchant_id`,`reserve_currency`,`reserve_status`,`expected_reserve_release_date`,`id`),
  KEY `idx_reserve_state_metrics` (`reserve_currency`,`transaction_date_time`,`remaining_amount`),
  CONSTRAINT `chk_reserve_state_amount_tpl` CHECK (((`original_basis_amount` >= 0) and (`original_reserve_rate` >= 0) and (`original_reserve_rate` <= 100) and (`retained_amount` >= 0) and (`returned_amount` >= 0) and (`released_amount` >= 0) and (`debit_adjustment_amount` >= 0) and (`credit_adjustment_amount` >= 0) and (`remaining_amount` >= 0) and ((`retained_amount` + `debit_adjustment_amount`) = (((`returned_amount` + `released_amount`) + `credit_adjustment_amount`) + `remaining_amount`)))),
  CONSTRAINT `chk_reserve_state_enum_tpl` CHECK (((`reserve_currency_exponent` between 0 and 8) and (`original_rounding_mode` in (_utf8mb4'HALF_UP',_utf8mb4'HALF_EVEN',_utf8mb4'DOWN')) and (`reserve_status` in (_utf8mb4'OPEN',_utf8mb4'FULLY_RETURNED',_utf8mb4'FULLY_RELEASED',_utf8mb4'ADJUSTED'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='原支付保证金当前汇总状态；用于并发退款上限控制，不替代不可变保证金明细';

-- ----------------------------
-- Table structure for transaction_reserve_clearing_state_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_reserve_clearing_state_202603`;
CREATE TABLE `transaction_reserve_clearing_state_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID',
  `reserve_state_id` varchar(64) NOT NULL COMMENT '保证金清分状态ID',
  `original_transaction_id` varchar(64) NOT NULL COMMENT '原支付或原请款动作交易号',
  `operation_id` varchar(64) NOT NULL COMMENT '交易生命周期关联号',
  `original_finance_state_id` varchar(64) NOT NULL COMMENT '原支付动作清分汇总ID',
  `original_hold_detail_no` varchar(64) NOT NULL COMMENT '原HOLD保证金清分明细号',
  `original_fee_plan_version_id` bigint NOT NULL COMMENT '原支付不可变费用版本ID',
  `original_reserve_snapshot_hash` char(64) NOT NULL COMMENT '原支付保证金配置快照SHA-256',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `reserve_currency` char(3) NOT NULL COMMENT '保证金负债币种，固定等于原支付标签币种',
  `reserve_currency_exponent` tinyint NOT NULL COMMENT '保证金币种ISO小数位',
  `original_basis_amount` decimal(24,8) NOT NULL COMMENT '原支付保证金计提基数',
  `original_reserve_rate` decimal(12,8) NOT NULL COMMENT '原支付清分时固化的保证金比例',
  `original_rounding_mode` varchar(16) NOT NULL COMMENT '原支付保证金舍入规则',
  `retained_amount` decimal(24,8) NOT NULL COMMENT '原支付保证金扣留金额',
  `returned_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '累计退款返还金额',
  `released_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '累计到期释放金额',
  `debit_adjustment_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '经双人复核增加的累计保证金负债',
  `credit_adjustment_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '经双人复核减少的累计保证金负债',
  `remaining_amount` decimal(24,8) NOT NULL COMMENT '剩余保证金负债金额',
  `expected_reserve_release_date` date DEFAULT NULL COMMENT '预计保证金释放业务日期',
  `reserve_status` varchar(24) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN、FULLY_RETURNED、FULLY_RELEASED、ADJUSTED',
  `last_return_transaction_id` varchar(64) DEFAULT NULL COMMENT '最近成功返还保证金的退款动作交易号',
  `last_return_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '最近成功返还动作时间',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '原支付动作业务时间，统一季度分片键',
  `original_transaction_utc_time` datetime(3) NOT NULL COMMENT '原支付动作业务时间对应UTC时间',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '原支付动作业务时区',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '并发退款行锁后的CAS版本号',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reserve_state_id` (`reserve_state_id`),
  UNIQUE KEY `uk_reserve_state_original_transaction` (`original_transaction_id`),
  UNIQUE KEY `uk_reserve_state_hold_detail` (`original_hold_detail_no`),
  KEY `idx_reserve_state_operation` (`operation_id`,`transaction_date_time`,`id`),
  KEY `idx_reserve_state_merchant_release` (`merchant_id`,`reserve_currency`,`reserve_status`,`expected_reserve_release_date`,`id`),
  KEY `idx_reserve_state_metrics` (`reserve_currency`,`transaction_date_time`,`remaining_amount`),
  CONSTRAINT `chk_reserve_state_amount_202603` CHECK (((`original_basis_amount` >= 0) and (`original_reserve_rate` >= 0) and (`original_reserve_rate` <= 100) and (`retained_amount` >= 0) and (`returned_amount` >= 0) and (`released_amount` >= 0) and (`debit_adjustment_amount` >= 0) and (`credit_adjustment_amount` >= 0) and (`remaining_amount` >= 0) and ((`retained_amount` + `debit_adjustment_amount`) = (((`returned_amount` + `released_amount`) + `credit_adjustment_amount`) + `remaining_amount`)))),
  CONSTRAINT `chk_reserve_state_enum_202603` CHECK (((`reserve_currency_exponent` between 0 and 8) and (`original_rounding_mode` in (_utf8mb4'HALF_UP',_utf8mb4'HALF_EVEN',_utf8mb4'DOWN')) and (`reserve_status` in (_utf8mb4'OPEN',_utf8mb4'FULLY_RETURNED',_utf8mb4'FULLY_RELEASED',_utf8mb4'ADJUSTED'))))
) ENGINE=InnoDB AUTO_INCREMENT=202603400000013640 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='2026年第3季度原支付保证金清分状态';

-- ----------------------------
-- Table structure for transaction_reserve_clearing_state_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_reserve_clearing_state_202604`;
CREATE TABLE `transaction_reserve_clearing_state_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID',
  `reserve_state_id` varchar(64) NOT NULL COMMENT '保证金清分状态ID',
  `original_transaction_id` varchar(64) NOT NULL COMMENT '原支付或原请款动作交易号',
  `operation_id` varchar(64) NOT NULL COMMENT '交易生命周期关联号',
  `original_finance_state_id` varchar(64) NOT NULL COMMENT '原支付动作清分汇总ID',
  `original_hold_detail_no` varchar(64) NOT NULL COMMENT '原HOLD保证金清分明细号',
  `original_fee_plan_version_id` bigint NOT NULL COMMENT '原支付不可变费用版本ID',
  `original_reserve_snapshot_hash` char(64) NOT NULL COMMENT '原支付保证金配置快照SHA-256',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `reserve_currency` char(3) NOT NULL COMMENT '保证金负债币种，固定等于原支付标签币种',
  `reserve_currency_exponent` tinyint NOT NULL COMMENT '保证金币种ISO小数位',
  `original_basis_amount` decimal(24,8) NOT NULL COMMENT '原支付保证金计提基数',
  `original_reserve_rate` decimal(12,8) NOT NULL COMMENT '原支付清分时固化的保证金比例',
  `original_rounding_mode` varchar(16) NOT NULL COMMENT '原支付保证金舍入规则',
  `retained_amount` decimal(24,8) NOT NULL COMMENT '原支付保证金扣留金额',
  `returned_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '累计退款返还金额',
  `released_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '累计到期释放金额',
  `debit_adjustment_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '经双人复核增加的累计保证金负债',
  `credit_adjustment_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '经双人复核减少的累计保证金负债',
  `remaining_amount` decimal(24,8) NOT NULL COMMENT '剩余保证金负债金额',
  `expected_reserve_release_date` date DEFAULT NULL COMMENT '预计保证金释放业务日期',
  `reserve_status` varchar(24) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN、FULLY_RETURNED、FULLY_RELEASED、ADJUSTED',
  `last_return_transaction_id` varchar(64) DEFAULT NULL COMMENT '最近成功返还保证金的退款动作交易号',
  `last_return_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '最近成功返还动作时间',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '原支付动作业务时间，统一季度分片键',
  `original_transaction_utc_time` datetime(3) NOT NULL COMMENT '原支付动作业务时间对应UTC时间',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '原支付动作业务时区',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '并发退款行锁后的CAS版本号',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reserve_state_id` (`reserve_state_id`),
  UNIQUE KEY `uk_reserve_state_original_transaction` (`original_transaction_id`),
  UNIQUE KEY `uk_reserve_state_hold_detail` (`original_hold_detail_no`),
  KEY `idx_reserve_state_operation` (`operation_id`,`transaction_date_time`,`id`),
  KEY `idx_reserve_state_merchant_release` (`merchant_id`,`reserve_currency`,`reserve_status`,`expected_reserve_release_date`,`id`),
  KEY `idx_reserve_state_metrics` (`reserve_currency`,`transaction_date_time`,`remaining_amount`),
  CONSTRAINT `chk_reserve_state_amount_202604` CHECK (((`original_basis_amount` >= 0) and (`original_reserve_rate` >= 0) and (`original_reserve_rate` <= 100) and (`retained_amount` >= 0) and (`returned_amount` >= 0) and (`released_amount` >= 0) and (`debit_adjustment_amount` >= 0) and (`credit_adjustment_amount` >= 0) and (`remaining_amount` >= 0) and ((`retained_amount` + `debit_adjustment_amount`) = (((`returned_amount` + `released_amount`) + `credit_adjustment_amount`) + `remaining_amount`)))),
  CONSTRAINT `chk_reserve_state_enum_202604` CHECK (((`reserve_currency_exponent` between 0 and 8) and (`original_rounding_mode` in (_utf8mb4'HALF_UP',_utf8mb4'HALF_EVEN',_utf8mb4'DOWN')) and (`reserve_status` in (_utf8mb4'OPEN',_utf8mb4'FULLY_RETURNED',_utf8mb4'FULLY_RELEASED',_utf8mb4'ADJUSTED'))))
) ENGINE=InnoDB AUTO_INCREMENT=202604400000041805 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='2026年第4季度原支付保证金清分状态';

-- ----------------------------
-- Table structure for transaction_reserve_clearing_state_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_reserve_clearing_state_202701`;
CREATE TABLE `transaction_reserve_clearing_state_202701` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID',
  `reserve_state_id` varchar(64) NOT NULL COMMENT '保证金清分状态ID',
  `original_transaction_id` varchar(64) NOT NULL COMMENT '原支付或原请款动作交易号',
  `operation_id` varchar(64) NOT NULL COMMENT '交易生命周期关联号',
  `original_finance_state_id` varchar(64) NOT NULL COMMENT '原支付动作清分汇总ID',
  `original_hold_detail_no` varchar(64) NOT NULL COMMENT '原HOLD保证金清分明细号',
  `original_fee_plan_version_id` bigint NOT NULL COMMENT '原支付不可变费用版本ID',
  `original_reserve_snapshot_hash` char(64) NOT NULL COMMENT '原支付保证金配置快照SHA-256',
  `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
  `reserve_currency` char(3) NOT NULL COMMENT '保证金负债币种，固定等于原支付标签币种',
  `reserve_currency_exponent` tinyint NOT NULL COMMENT '保证金币种ISO小数位',
  `original_basis_amount` decimal(24,8) NOT NULL COMMENT '原支付保证金计提基数',
  `original_reserve_rate` decimal(12,8) NOT NULL COMMENT '原支付清分时固化的保证金比例',
  `original_rounding_mode` varchar(16) NOT NULL COMMENT '原支付保证金舍入规则',
  `retained_amount` decimal(24,8) NOT NULL COMMENT '原支付保证金扣留金额',
  `returned_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '累计退款返还金额',
  `released_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '累计到期释放金额',
  `debit_adjustment_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '经双人复核增加的累计保证金负债',
  `credit_adjustment_amount` decimal(24,8) NOT NULL DEFAULT '0.00000000' COMMENT '经双人复核减少的累计保证金负债',
  `remaining_amount` decimal(24,8) NOT NULL COMMENT '剩余保证金负债金额',
  `expected_reserve_release_date` date DEFAULT NULL COMMENT '预计保证金释放业务日期',
  `reserve_status` varchar(24) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN、FULLY_RETURNED、FULLY_RELEASED、ADJUSTED',
  `last_return_transaction_id` varchar(64) DEFAULT NULL COMMENT '最近成功返还保证金的退款动作交易号',
  `last_return_transaction_date_time` datetime(3) DEFAULT NULL COMMENT '最近成功返还动作时间',
  `transaction_date_time` datetime(3) NOT NULL COMMENT '原支付动作业务时间，统一季度分片键',
  `original_transaction_utc_time` datetime(3) NOT NULL COMMENT '原支付动作业务时间对应UTC时间',
  `transaction_time_zone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '原支付动作业务时区',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '并发退款行锁后的CAS版本号',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reserve_state_id` (`reserve_state_id`),
  UNIQUE KEY `uk_reserve_state_original_transaction` (`original_transaction_id`),
  UNIQUE KEY `uk_reserve_state_hold_detail` (`original_hold_detail_no`),
  KEY `idx_reserve_state_operation` (`operation_id`,`transaction_date_time`,`id`),
  KEY `idx_reserve_state_merchant_release` (`merchant_id`,`reserve_currency`,`reserve_status`,`expected_reserve_release_date`,`id`),
  KEY `idx_reserve_state_metrics` (`reserve_currency`,`transaction_date_time`,`remaining_amount`),
  CONSTRAINT `transaction_reserve_clearing_state_202701_chk_1` CHECK (((`original_basis_amount` >= 0) and (`original_reserve_rate` >= 0) and (`original_reserve_rate` <= 100) and (`retained_amount` >= 0) and (`returned_amount` >= 0) and (`released_amount` >= 0) and (`debit_adjustment_amount` >= 0) and (`credit_adjustment_amount` >= 0) and (`remaining_amount` >= 0) and ((`retained_amount` + `debit_adjustment_amount`) = (((`returned_amount` + `released_amount`) + `credit_adjustment_amount`) + `remaining_amount`)))),
  CONSTRAINT `transaction_reserve_clearing_state_202701_chk_2` CHECK (((`reserve_currency_exponent` between 0 and 8) and (`original_rounding_mode` in (_utf8mb4'HALF_UP',_utf8mb4'HALF_EVEN',_utf8mb4'DOWN')) and (`reserve_status` in (_utf8mb4'OPEN',_utf8mb4'FULLY_RETURNED',_utf8mb4'FULLY_RELEASED',_utf8mb4'ADJUSTED'))))
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='原支付保证金当前汇总状态；用于并发退款上限控制，不替代不可变保证金明细';

-- ----------------------------
-- Table structure for transaction_shipping_info
-- ----------------------------
DROP TABLE IF EXISTS `transaction_shipping_info`;
CREATE TABLE `transaction_shipping_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `shipping_info_id` varchar(64) NOT NULL COMMENT '收货信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '收货人名。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '收货人姓。',
  `email` varchar(64) DEFAULT NULL COMMENT '收货人邮箱明文。',
  `phone` varchar(32) DEFAULT NULL COMMENT '收货人电话明文。',
  `country` varchar(3) DEFAULT NULL COMMENT '收货国家/地区。',
  `state` varchar(64) DEFAULT NULL COMMENT '收货州、省或地区。',
  `city` varchar(64) DEFAULT NULL COMMENT '收货城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '收货街道。',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货人身份、联系方式和地址明文交易快照。';

-- ----------------------------
-- Table structure for transaction_shipping_info_202603
-- ----------------------------
DROP TABLE IF EXISTS `transaction_shipping_info_202603`;
CREATE TABLE `transaction_shipping_info_202603` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `shipping_info_id` varchar(64) NOT NULL COMMENT '收货信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '收货人名。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '收货人姓。',
  `email` varchar(64) DEFAULT NULL COMMENT '收货人邮箱明文。',
  `phone` varchar(32) DEFAULT NULL COMMENT '收货人电话明文。',
  `country` varchar(3) DEFAULT NULL COMMENT '收货国家/地区。',
  `state` varchar(64) DEFAULT NULL COMMENT '收货州、省或地区。',
  `city` varchar(64) DEFAULT NULL COMMENT '收货城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '收货街道。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货人身份、联系方式和地址明文交易快照。';

-- ----------------------------
-- Table structure for transaction_shipping_info_202604
-- ----------------------------
DROP TABLE IF EXISTS `transaction_shipping_info_202604`;
CREATE TABLE `transaction_shipping_info_202604` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `shipping_info_id` varchar(64) NOT NULL COMMENT '收货信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '收货人名。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '收货人姓。',
  `email` varchar(64) DEFAULT NULL COMMENT '收货人邮箱明文。',
  `phone` varchar(32) DEFAULT NULL COMMENT '收货人电话明文。',
  `country` varchar(3) DEFAULT NULL COMMENT '收货国家/地区。',
  `state` varchar(64) DEFAULT NULL COMMENT '收货州、省或地区。',
  `city` varchar(64) DEFAULT NULL COMMENT '收货城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '收货街道。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202604000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货人身份、联系方式和地址明文交易快照。';

-- ----------------------------
-- Table structure for transaction_shipping_info_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_shipping_info_202701`;
CREATE TABLE `transaction_shipping_info_202701` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID。',
  `shipping_info_id` varchar(64) NOT NULL COMMENT '收货信息ID。',
  `transaction_id` varchar(64) NOT NULL COMMENT '平台交易生命周期唯一标识。',
  `operation_id` varchar(64) DEFAULT NULL COMMENT '交易动作ID。',
  `first_name` varchar(32) DEFAULT NULL COMMENT '收货人名。',
  `last_name` varchar(32) DEFAULT NULL COMMENT '收货人姓。',
  `email` varchar(64) DEFAULT NULL COMMENT '收货人邮箱明文。',
  `phone` varchar(32) DEFAULT NULL COMMENT '收货人电话明文。',
  `country` varchar(3) DEFAULT NULL COMMENT '收货国家/地区。',
  `state` varchar(64) DEFAULT NULL COMMENT '收货州、省或地区。',
  `city` varchar(64) DEFAULT NULL COMMENT '收货城市。',
  `street` varchar(128) DEFAULT NULL COMMENT '收货街道。',
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货人身份、联系方式和地址明文交易快照。';

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
) ENGINE=InnoDB AUTO_INCREMENT=202603000000060112 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录订单、动作、财务状态机每一次流转，支持审计和问题追踪。';

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
) ENGINE=InnoDB AUTO_INCREMENT=202604000000244243 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录订单、动作、财务状态机每一次流转，支持审计和问题追踪。';

-- ----------------------------
-- Table structure for transaction_status_history_202701
-- ----------------------------
DROP TABLE IF EXISTS `transaction_status_history_202701`;
CREATE TABLE `transaction_status_history_202701` (
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
) ENGINE=InnoDB AUTO_INCREMENT=202701000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录订单、动作、财务状态机每一次流转，支持审计和问题追踪。';

-- ----------------------------
-- Triggers structure for table sys_file_template
-- ----------------------------
DROP TRIGGER IF EXISTS `trg_sys_file_template_stable_identity`;
delimiter ;;
CREATE TRIGGER `payment_acquiring`.`trg_sys_file_template_stable_identity` BEFORE UPDATE ON `sys_file_template` FOR EACH ROW BEGIN
    IF NOT (OLD.template_code <=> NEW.template_code)
       OR NOT (OLD.file_extension <=> NEW.file_extension) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'template code and file extension are immutable';
    END IF;
END
;;
delimiter ;

-- ----------------------------
-- Triggers structure for table sys_file_template_asset
-- ----------------------------
DROP TRIGGER IF EXISTS `trg_sys_file_template_asset_immutable_update`;
delimiter ;;
CREATE TRIGGER `payment_acquiring`.`trg_sys_file_template_asset_immutable_update` BEFORE UPDATE ON `sys_file_template_asset` FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'template asset is immutable';
END
;;
delimiter ;

-- ----------------------------
-- Triggers structure for table sys_file_template_asset
-- ----------------------------
DROP TRIGGER IF EXISTS `trg_sys_file_template_asset_immutable_delete`;
delimiter ;;
CREATE TRIGGER `payment_acquiring`.`trg_sys_file_template_asset_immutable_delete` BEFORE DELETE ON `sys_file_template_asset` FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'template asset cannot be deleted';
END
;;
delimiter ;

-- ----------------------------
-- Triggers structure for table sys_file_template_version
-- ----------------------------
DROP TRIGGER IF EXISTS `trg_sys_file_template_version_immutable`;
delimiter ;;
CREATE TRIGGER `payment_acquiring`.`trg_sys_file_template_version_immutable` BEFORE UPDATE ON `sys_file_template_version` FOR EACH ROW BEGIN
    IF NOT (OLD.template_id <=> NEW.template_id)
       OR NOT (OLD.version_no <=> NEW.version_no)
       OR NOT (OLD.schema_version <=> NEW.schema_version)
       OR NOT (OLD.request_id <=> NEW.request_id)
       OR NOT (OLD.release_note <=> NEW.release_note)
       OR NOT (OLD.create_by <=> NEW.create_by)
       OR NOT (OLD.create_time <=> NEW.create_time) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'template version content is immutable';
    END IF;
END
;;
delimiter ;

SET FOREIGN_KEY_CHECKS = 1;

-- REQ-001 支付方式治理结构
DROP TABLE IF EXISTS `merchant_payment_application_item`;
CREATE TABLE `merchant_payment_application_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '支付方式申请明细主键',
  `application_no` varchar(40) NOT NULL COMMENT '同批申请的全局业务编号',
  `merchant_id` varchar(32) NOT NULL COMMENT '所属商户编号',
  `item_key` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范支付方式键',
  `operation` varchar(16) NOT NULL COMMENT '首次申请或终止后的重新申请类型',
  `status` varchar(32) NOT NULL COMMENT '当前申请明细状态',
  `round_no` int NOT NULL DEFAULT '1' COMMENT '补充后重新审核的轮次',
  `source` varchar(24) NOT NULL COMMENT '商户自助申请或平台代申请来源',
  `create_request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建批次的原始幂等请求键',
  `initial_submitter_app_code` varchar(32) NOT NULL COMMENT '首次提交者所属应用编码',
  `initial_submitter_account_id` bigint NOT NULL COMMENT '首次提交者可信登录账号主键',
  `initial_submitter_user_id` bigint NOT NULL COMMENT '首次提交者可信自然人主体主键',
  `current_submitter_app_code` varchar(32) NOT NULL COMMENT '当前审核轮次提交者所属应用编码',
  `current_submitter_account_id` bigint NOT NULL COMMENT '当前审核轮次提交者可信登录账号主键',
  `current_submitter_user_id` bigint NOT NULL COMMENT '当前审核轮次提交者可信自然人主体主键',
  `reason` varchar(1000) DEFAULT NULL COMMENT '申请或补充的说明',
  `expected_grant_version` bigint NOT NULL DEFAULT '0' COMMENT '申请时的授权版本，零表示当时无授权',
  `active_slot` tinyint DEFAULT NULL COMMENT '待审核或退回补充为一，终态为空',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '当前申请明细的乐观锁版本',
  `submitted_at` datetime(3) NOT NULL COMMENT '当前审核轮次的提交时刻',
  `closed_at` datetime(3) DEFAULT NULL COMMENT '申请进入终态的时刻',
  `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '申请明细创建时刻',
  `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '申请明细最后更新时刻',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mpai_create_request` (`merchant_id`,`create_request_id`,`item_key`),
  UNIQUE KEY `uk_mpai_active` (`merchant_id`,`item_key`,`active_slot`),
  KEY `idx_mpai_application` (`merchant_id`,`application_no`,`id`),
  KEY `idx_mpai_merchant_created` (`merchant_id`,`gmt_create`,`id`),
  KEY `idx_mpai_review_queue` (`status`,`submitted_at`,`id`),
  KEY `idx_mpai_application_global` (`application_no`,`merchant_id`,`id`),
  CONSTRAINT `chk_mpai_active` CHECK ((((`status` in (_utf8mb4'PENDING_REVIEW',_utf8mb4'RETURNED')) and (`active_slot` is not null) and (`active_slot` = 1) and (`closed_at` is null)) or ((`status` in (_utf8mb4'APPROVED',_utf8mb4'REJECTED',_utf8mb4'WITHDRAWN')) and (`active_slot` is null) and (`closed_at` is not null)))),
  CONSTRAINT `chk_mpai_operation` CHECK ((((`operation` = _utf8mb4'APPLY') and (`expected_grant_version` = 0)) or ((`operation` = _utf8mb4'REAPPLY') and (`expected_grant_version` > 0)))),
  CONSTRAINT `chk_mpai_version` CHECK (((`round_no` > 0) and (`version` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户支付方式申请明细';

-- REQ-001 支付方式治理结构
DROP TABLE IF EXISTS `merchant_payment_authorization`;
CREATE TABLE `merchant_payment_authorization` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商户支付方式授权主键',
  `merchant_id` varchar(32) NOT NULL COMMENT '被授权商户编号',
  `item_key` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范支付方式键',
  `authorization_status` varchar(16) NOT NULL COMMENT '当前授权持久化状态',
  `version` bigint NOT NULL DEFAULT '1' COMMENT '当前授权的乐观锁版本',
  `source` varchar(24) NOT NULL COMMENT '申请批准或历史核验的授权来源',
  `source_ref` varchar(64) NOT NULL COMMENT '申请明细主键或历史基线批次引用',
  `approved_app_code` varchar(32) DEFAULT NULL COMMENT '真实申请批准者所属应用编码',
  `approved_account_id` bigint DEFAULT NULL COMMENT '真实申请批准者的可信登录账号主键',
  `approved_user_id` bigint DEFAULT NULL COMMENT '真实申请批准者的可信自然人主体主键',
  `effective_time` datetime(3) NOT NULL COMMENT '批准后的授权生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '授权失效边界，为空表示长期有效',
  `suspended_time` datetime(3) DEFAULT NULL COMMENT '本次暂停的时间',
  `terminated_time` datetime(3) DEFAULT NULL COMMENT '本次终止的时间',
  `last_review_record_id` bigint NOT NULL COMMENT '上一次授权变更或核验的不可变审核记录主键',
  `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权记录创建时刻',
  `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '授权记录最后更新时刻',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mpa_merchant_method` (`merchant_id`,`item_key`),
  KEY `idx_mpa_effective_time` (`effective_time`,`id`),
  KEY `idx_mpa_expire_time` (`expire_time`,`id`),
  CONSTRAINT `chk_mpa_source` CHECK ((((`source` = _latin1'APPLICATION') and (`approved_app_code` is not null) and (`approved_account_id` is not null) and (`approved_user_id` is not null)) or ((`source` = _latin1'HISTORICAL_BASELINE') and (`approved_app_code` is null) and (`approved_account_id` is null) and (`approved_user_id` is null)))),
  CONSTRAINT `chk_mpa_state` CHECK ((`authorization_status` in (_latin1'ACTIVE',_latin1'SUSPENDED',_latin1'TERMINATED'))),
  CONSTRAINT `chk_mpa_time` CHECK (((`expire_time` is null) or (`expire_time` > `effective_time`))),
  CONSTRAINT `chk_mpa_version` CHECK ((`version` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户当前支付方式授权';
