-- 渠道回调全局 Inbox 迁移草案。
-- 仅供评审，未由 Codex 执行。应用发布前必须先完成备份、DDL 窗口和回滚评审。

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `payment_acquiring`;

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
  `retry_count` int unsigned NOT NULL DEFAULT 0 COMMENT '已记录的处理失败次数。',
  `max_retry_count` int unsigned NOT NULL DEFAULT 12 COMMENT '最大处理失败次数。',
  `next_retry_time` datetime(3) DEFAULT NULL COMMENT '下一次允许抢占的时间。',
  `last_error` varchar(256) DEFAULT NULL COMMENT '最近失败异常类型，不保存异常消息和敏感报文。',
  `received_time` datetime(3) NOT NULL COMMENT 'OpenAPI 入口收到回调的时间。',
  `processed_time` datetime(3) DEFAULT NULL COMMENT '业务回调处理完成时间。',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '多实例抢占和结果回写 CAS 版本。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间及 PROCESSING 租约起点。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_callback_inbox_no` (`inbox_id`),
  UNIQUE KEY `uk_callback_inbox_idempotency` (`channel_code`, `idempotency_key`),
  KEY `idx_callback_inbox_due` (`inbox_status`, `next_retry_time`, `id`),
  KEY `idx_callback_inbox_stale` (`inbox_status`, `update_time`, `id`),
  KEY `idx_callback_inbox_received` (`channel_code`, `received_time`, `id`),
  CONSTRAINT `chk_callback_inbox_status` CHECK (
    `inbox_status` IN ('RECEIVED', 'PROCESSING', 'RETRY', 'PROCESSED', 'CLOSED')
  ),
  CONSTRAINT `chk_callback_inbox_retry` CHECK (
    `retry_count` <= `max_retry_count` AND `max_retry_count` > 0
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='渠道回调可靠受理 Inbox；先落库提交再返回渠道 ACK，Worker 至少一次消费。';

-- 后检：表、唯一约束和扫描索引必须全部存在。
SHOW CREATE TABLE `transaction_callback_inbox`;

SELECT `index_name`, GROUP_CONCAT(`column_name` ORDER BY `seq_in_index`) AS `columns`
FROM `information_schema`.`statistics`
WHERE `table_schema` = DATABASE()
  AND `table_name` = 'transaction_callback_inbox'
GROUP BY `index_name`
ORDER BY `index_name`;
