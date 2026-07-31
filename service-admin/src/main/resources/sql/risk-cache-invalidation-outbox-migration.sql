CREATE TABLE IF NOT EXISTS risk_cache_invalidation_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    event_id VARCHAR(64) NOT NULL COMMENT '失效事件唯一编号',
    namespace VARCHAR(64) NOT NULL COMMENT '缓存代际命名空间',
    publication_token VARCHAR(128) NOT NULL COMMENT 'Redis 发布门禁持有者 token',
    generation VARCHAR(128) NOT NULL COMMENT '待切换的新缓存代际',
    event_status VARCHAR(16) NOT NULL DEFAULT 'INIT' COMMENT '状态：INIT、FAILED、SENT',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_time DATETIME(3) NULL COMMENT '下次重试时间',
    published_time DATETIME(3) NULL COMMENT '发布成功时间',
    failure_reason VARCHAR(512) NULL COMMENT '最近一次失败原因摘要',
    version INT NOT NULL DEFAULT 0 COMMENT 'CAS 版本号',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_cache_invalidation_event (event_id),
    KEY idx_risk_cache_invalidation_due (event_status, next_retry_time, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控规则缓存失效事件表';
