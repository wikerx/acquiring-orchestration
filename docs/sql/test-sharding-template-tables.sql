-- 测试分表模板表 SQL。
-- 本文件只包含测试模板表，不接入真实支付、代付、退款、渠道回调、对账或清结算主链路。
-- 物理分表应通过 CREATE TABLE target LIKE template 复制结构，并在创建后设置 AUTO_INCREMENT 起始值。

CREATE TABLE IF NOT EXISTS `test_transaction` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，分表物理表按yyyyQQ+12位自增序号生成',
  `transaction_no` VARCHAR(64) NOT NULL COMMENT '交易单号',
  `merchant_id` VARCHAR(64) NOT NULL COMMENT '商户ID',
  `store_id` VARCHAR(64) DEFAULT NULL COMMENT '店铺ID',
  `transaction_type` VARCHAR(32) NOT NULL COMMENT '交易类型',
  `transaction_status` VARCHAR(32) NOT NULL COMMENT '交易状态',
  `transaction_currency` CHAR(3) NOT NULL COMMENT '交易币种',
  `transaction_amount` DECIMAL(20, 4) NOT NULL COMMENT '交易金额',
  `payment_method` VARCHAR(32) DEFAULT NULL COMMENT '支付方式',
  `channel_code` VARCHAR(64) DEFAULT NULL COMMENT '渠道编码',
  `transaction_date_time` DATETIME(3) NOT NULL COMMENT '交易时间，分表字段，按Asia/Shanghai季度路由',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0=否，1=是',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transaction_no` (`transaction_no`),
  KEY `idx_merchant_time` (`merchant_id`, `transaction_date_time`),
  KEY `idx_store_time` (`store_id`, `transaction_date_time`),
  KEY `idx_status_time` (`transaction_status`, `transaction_date_time`),
  KEY `idx_channel_time` (`channel_code`, `transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试交易主表模板';

CREATE TABLE IF NOT EXISTS `test_transaction_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，分表物理表按yyyyQQ+12位自增序号生成',
  `transaction_no` VARCHAR(64) NOT NULL COMMENT '交易单号',
  `merchant_id` VARCHAR(64) NOT NULL COMMENT '商户ID',
  `payer_name` VARCHAR(128) DEFAULT NULL COMMENT '付款人姓名',
  `payer_email_masked` VARCHAR(128) DEFAULT NULL COMMENT '付款人邮箱，脱敏后保存',
  `payer_ip` VARCHAR(64) DEFAULT NULL COMMENT '付款人IP',
  `card_bin` VARCHAR(11) DEFAULT NULL COMMENT '卡BIN',
  `card_last4` VARCHAR(4) DEFAULT NULL COMMENT '卡号后四位',
  `card_scheme` VARCHAR(32) DEFAULT NULL COMMENT '卡组织',
  `issuer_country` VARCHAR(3) DEFAULT NULL COMMENT '发卡国家',
  `transaction_date_time` DATETIME(3) NOT NULL COMMENT '交易时间，分表字段，按Asia/Shanghai季度路由',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0=否，1=是',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transaction_no` (`transaction_no`),
  KEY `idx_merchant_time` (`merchant_id`, `transaction_date_time`),
  KEY `idx_card_bin_time` (`card_bin`, `transaction_date_time`),
  KEY `idx_payer_ip_time` (`payer_ip`, `transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试交易信息表模板';

CREATE TABLE IF NOT EXISTS `test_transaction_merge_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，分表物理表按yyyyQQ+12位自增序号生成',
  `transaction_no` VARCHAR(64) NOT NULL COMMENT '交易单号',
  `merchant_id` VARCHAR(64) NOT NULL COMMENT '商户ID',
  `business_order_no` VARCHAR(64) DEFAULT NULL COMMENT '商户业务订单号',
  `channel_order_no` VARCHAR(128) DEFAULT NULL COMMENT '渠道订单号',
  `gateway_request_id` VARCHAR(128) DEFAULT NULL COMMENT '网关请求ID',
  `risk_result` VARCHAR(32) DEFAULT NULL COMMENT '风控结果',
  `three_ds_result` VARCHAR(32) DEFAULT NULL COMMENT '3DS结果',
  `metadata_json` TEXT DEFAULT NULL COMMENT '附属信息JSON，测试字段',
  `transaction_date_time` DATETIME(3) NOT NULL COMMENT '交易时间，分表字段，按Asia/Shanghai季度路由',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0=否，1=是',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transaction_no` (`transaction_no`),
  KEY `idx_business_order_no` (`business_order_no`),
  KEY `idx_channel_order_no` (`channel_order_no`),
  KEY `idx_merchant_time` (`merchant_id`, `transaction_date_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试交易附属信息表模板';

CREATE TABLE IF NOT EXISTS `test_transaction_status_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，分表物理表按yyyyQQ+12位自增序号生成',
  `transaction_no` VARCHAR(64) NOT NULL COMMENT '交易单号',
  `merchant_id` VARCHAR(64) NOT NULL COMMENT '商户ID',
  `from_status` VARCHAR(32) DEFAULT NULL COMMENT '变更前状态',
  `to_status` VARCHAR(32) NOT NULL COMMENT '变更后状态',
  `status_reason` VARCHAR(255) DEFAULT NULL COMMENT '状态原因',
  `event_type` VARCHAR(64) DEFAULT NULL COMMENT '事件类型',
  `event_time` DATETIME(3) NOT NULL COMMENT '事件时间',
  `transaction_date_time` DATETIME(3) NOT NULL COMMENT '交易时间，分表字段，按Asia/Shanghai季度路由',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0=否，1=是',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_transaction_no` (`transaction_no`),
  KEY `idx_merchant_time` (`merchant_id`, `transaction_date_time`),
  KEY `idx_to_status_time` (`to_status`, `transaction_date_time`),
  KEY `idx_event_time` (`event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试交易状态信息表模板';
