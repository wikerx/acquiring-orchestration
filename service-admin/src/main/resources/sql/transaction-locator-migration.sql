-- 交易定位固定表迁移。
-- transaction_locator 不参与季度分表，用于将每笔交易动作定位到真实分片时间。

CREATE TABLE IF NOT EXISTS `transaction_locator` (
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
