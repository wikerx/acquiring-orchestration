-- 本地 payment_acquiring 已执行的中文注释修复，共 1 处表注释和 24 处列注释。
-- 临时表演练及执行后核对均确认非注释定义一致。再次执行前应重新核对目标库的列定义。
SET NAMES utf8mb4;
USE payment_acquiring;
SET SESSION lock_wait_timeout=3;

ALTER TABLE `transaction_callback_delivery`
  MODIFY COLUMN `delivery_id` char(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'OpenAPI 为单次 HTTP 投递生成的编号',
  MODIFY COLUMN `inbox_id` varchar(64) NOT NULL COMMENT '渠道回调收件箱业务编号',
  MODIFY COLUMN `trace_id` varchar(64) NOT NULL COMMENT '本次投递的链路号',
  MODIFY COLUMN `received_time` datetime(3) NOT NULL COMMENT 'OpenAPI 接收本次投递的时间，使用交易业务时区',
  MODIFY COLUMN `signature_valid` tinyint(1) NOT NULL COMMENT '渠道验签结果',
  MODIFY COLUMN `accept_result` varchar(16) NOT NULL COMMENT '本次投递首次受理或事件去重结果',
  MODIFY COLUMN `request_body_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本次投递原始报文的 SHA-256 摘要',
  MODIFY COLUMN `http_status` smallint unsigned DEFAULT NULL COMMENT 'OpenAPI 实际生成的 HTTP 状态码',
  MODIFY COLUMN `response_body` varchar(64) DEFAULT NULL COMMENT '准许留存的固定应答正文',
  MODIFY COLUMN `duration_millis` bigint unsigned DEFAULT NULL COMMENT 'OpenAPI 接收到生成应答的毫秒数',
  MODIFY COLUMN `request_body_masked` json DEFAULT NULL COMMENT '本次投递已验签且按渠道原字段名提取的脱敏请求正文',
  COMMENT = '已验证渠道回调的逐次 HTTP 投递审计',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_callback_inbox`
  MODIFY COLUMN `callback_id` varchar(64) DEFAULT NULL COMMENT '业务处理归属的渠道回调编号',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_channel_request`
  MODIFY COLUMN `channel_merchant_reference` varchar(128) DEFAULT NULL COMMENT 'Scott 实际发出的商户侧动作参考号',
  MODIFY COLUMN `channel_notify_url` varchar(512) DEFAULT NULL COMMENT 'Scott 首次外呼前冻结的本笔回调地址',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_order`
  MODIFY COLUMN `channel_authorization_id` varchar(128) DEFAULT NULL COMMENT 'Scott 原授权生命周期编号',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_channel_request_202603`
  MODIFY COLUMN `channel_merchant_reference` varchar(128) DEFAULT NULL COMMENT 'Scott 实际发出的商户侧动作参考号',
  MODIFY COLUMN `channel_notify_url` varchar(512) DEFAULT NULL COMMENT 'Scott 首次外呼前冻结的本笔回调地址',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_order_202603`
  MODIFY COLUMN `channel_authorization_id` varchar(128) DEFAULT NULL COMMENT 'Scott 原授权生命周期编号',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_channel_request_202604`
  MODIFY COLUMN `channel_merchant_reference` varchar(128) DEFAULT NULL COMMENT 'Scott 实际发出的商户侧动作参考号',
  MODIFY COLUMN `channel_notify_url` varchar(512) DEFAULT NULL COMMENT 'Scott 首次外呼前冻结的本笔回调地址',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_order_202604`
  MODIFY COLUMN `channel_authorization_id` varchar(128) DEFAULT NULL COMMENT 'Scott 原授权生命周期编号',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_channel_request_202701`
  MODIFY COLUMN `channel_merchant_reference` varchar(128) DEFAULT NULL COMMENT 'Scott 实际发出的商户侧动作参考号',
  MODIFY COLUMN `channel_notify_url` varchar(512) DEFAULT NULL COMMENT 'Scott 首次外呼前冻结的本笔回调地址',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_order_202701`
  MODIFY COLUMN `channel_authorization_id` varchar(128) DEFAULT NULL COMMENT 'Scott 原授权生命周期编号',
  ALGORITHM=INPLACE, LOCK=NONE;
