-- 在 Payment、OpenAPI 和 Admin 使用新代码前执行。
CREATE TABLE transaction_callback_delivery (
    delivery_id char(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'OpenAPI 为单次 HTTP 投递生成的编号',
    inbox_id varchar(64) NOT NULL COMMENT '渠道回调收件箱业务编号',
    trace_id varchar(64) NOT NULL COMMENT '本次投递的链路号',
    received_time datetime(3) NOT NULL COMMENT 'OpenAPI 接收本次投递的时间，使用交易业务时区',
    signature_valid tinyint(1) NOT NULL COMMENT '渠道验签结果',
    accept_result varchar(16) NOT NULL COMMENT '本次投递首次受理或事件去重结果',
    request_body_sha256 char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本次投递原始报文的 SHA-256 摘要',
    http_status smallint unsigned DEFAULT NULL COMMENT 'OpenAPI 实际生成的 HTTP 状态码',
    response_body varchar(64) DEFAULT NULL COMMENT '准许留存的固定应答正文',
    duration_millis bigint unsigned DEFAULT NULL COMMENT 'OpenAPI 接收到生成应答的毫秒数',
    PRIMARY KEY (delivery_id),
    KEY idx_callback_delivery_inbox_received (inbox_id, received_time, delivery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='已验证渠道回调的逐次 HTTP 投递审计';

ALTER TABLE transaction_callback_inbox
    ADD COLUMN callback_id varchar(64) DEFAULT NULL COMMENT '业务处理归属的渠道回调编号',
    ADD KEY idx_callback_inbox_callback (callback_id);
