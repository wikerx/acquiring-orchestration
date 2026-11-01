-- 汇率实际使用事件链路增量迁移。
-- 前置：20261101_01 检查全部为 0；执行后运行 20261101_03。
-- 发布顺序：先迁移数据库和 RocketMQ 资源，再发布 service-data，最后发布生产者服务。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS migrate_exchange_rate_usage_pipeline;
DELIMITER $$
CREATE PROCEDURE migrate_exchange_rate_usage_pipeline()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'exchange_rate_usage_snapshot'
          AND column_name = 'usage_event_id'
    ) THEN
        ALTER TABLE exchange_rate_usage_snapshot
            ADD COLUMN usage_event_id VARCHAR(96) NULL
                COMMENT '汇率使用事件全局幂等号' AFTER id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'exchange_rate_usage_snapshot'
          AND column_name = 'producer_service'
    ) THEN
        ALTER TABLE exchange_rate_usage_snapshot
            ADD COLUMN producer_service VARCHAR(64) NULL
                COMMENT '产生实际使用事实的服务名' AFTER usage_event_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'exchange_rate_usage_snapshot'
          AND column_name = 'rate_source'
    ) THEN
        ALTER TABLE exchange_rate_usage_snapshot
            ADD COLUMN rate_source VARCHAR(64) NULL
                COMMENT '实际采用的汇率来源编码' AFTER rule_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'exchange_rate_usage_snapshot'
          AND column_name = 'source_quote_direction'
    ) THEN
        ALTER TABLE exchange_rate_usage_snapshot
            ADD COLUMN source_quote_direction VARCHAR(16) NULL
                COMMENT '原始报价方向：DIRECT/INVERSE' AFTER rate_source;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'exchange_rate_usage_snapshot'
          AND column_name = 'rate_effective_time'
    ) THEN
        ALTER TABLE exchange_rate_usage_snapshot
            ADD COLUMN rate_effective_time DATETIME(3) NULL
                COMMENT '实际采用汇率的生效时间' AFTER source_quote_direction;
    END IF;

    -- 存量人工/历史快照无法恢复原事件号，以主键生成只用于幂等迁移的保留身份。
    UPDATE exchange_rate_usage_snapshot
    SET usage_event_id = CONCAT('LEGACY:', LPAD(CAST(id AS CHAR), 20, '0'))
    WHERE usage_event_id IS NULL OR usage_event_id = '';

    -- 迁移前仅有交易汇率快照；按汇率类型补齐生产服务，禁止新消费者写入无所有者事实。
    UPDATE exchange_rate_usage_snapshot
    SET producer_service = CASE rate_type
        WHEN 'TRANSACTION_RATE' THEN 'service-payment'
        WHEN 'SETTLEMENT_RATE' THEN 'service-settlement'
        ELSE producer_service
    END
    WHERE producer_service IS NULL OR producer_service = '';

    ALTER TABLE exchange_rate_usage_snapshot
        MODIFY COLUMN usage_event_id VARCHAR(96) NOT NULL
            COMMENT '汇率使用事件全局幂等号';

    ALTER TABLE exchange_rate_usage_snapshot
        MODIFY COLUMN producer_service VARCHAR(64) NOT NULL
            COMMENT '产生实际使用事实的服务名';

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'exchange_rate_usage_snapshot'
          AND index_name = 'uk_exchange_snapshot_usage_event'
    ) THEN
        ALTER TABLE exchange_rate_usage_snapshot
            ADD UNIQUE KEY uk_exchange_snapshot_usage_event (usage_event_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'settlement_event_outbox'
          AND column_name = 'rate_usage_id'
    ) THEN
        ALTER TABLE settlement_event_outbox
            ADD COLUMN rate_usage_id BIGINT NULL
                COMMENT '来源结算批次汇率行ID' AFTER candidate_id;
    END IF;

    -- 交易投影事件继续使用 candidate_id；批次汇率事件使用 rate_usage_id。
    ALTER TABLE settlement_event_outbox
        MODIFY COLUMN candidate_id BIGINT NULL COMMENT '来源真实交易候选主键';

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'settlement_event_outbox'
          AND index_name = 'uk_settlement_event_rate_usage'
    ) THEN
        ALTER TABLE settlement_event_outbox
            ADD UNIQUE KEY uk_settlement_event_rate_usage
                (settlement_batch_no, rate_usage_id, tag);
    END IF;
END$$
DELIMITER ;

CALL migrate_exchange_rate_usage_pipeline();
DROP PROCEDURE migrate_exchange_rate_usage_pipeline;
