CREATE TABLE IF NOT EXISTS merchant_security_cache_invalidation_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    event_id VARCHAR(64) NOT NULL COMMENT '失效事件唯一编号',
    cache_name VARCHAR(64) NOT NULL COMMENT '已登记 Spring Cache 名称',
    business_key VARCHAR(128) NOT NULL COMMENT '待失效业务缓存 Key：商户号或平台公开配置键',
    gate_token VARCHAR(128) NOT NULL COMMENT 'Redis 失效门禁持有者 token',
    event_status VARCHAR(16) NOT NULL DEFAULT 'INIT' COMMENT '状态：INIT、FAILED、SENT',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_time DATETIME(3) NULL COMMENT '下次重试时间',
    published_time DATETIME(3) NULL COMMENT '失效成功时间',
    failure_reason VARCHAR(512) NULL COMMENT '最近一次失败原因摘要',
    version INT NOT NULL DEFAULT 0 COMMENT 'CAS 版本号',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_security_cache_event (event_id),
    KEY idx_merchant_security_cache_due (event_status, next_retry_time, create_time, id),
    KEY idx_merchant_security_cache_target (cache_name, business_key, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受管永久缓存可靠失效事件表（兼容保留历史表名）';
