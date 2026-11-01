-- 汇率实际使用事件链路紧急回滚。
-- 必须先停止 service-payment、service-settlement 和 service-data；存在新事件时拒绝回滚并优先前向修复。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS rollback_exchange_rate_usage_pipeline;
DELIMITER $$
CREATE PROCEDURE rollback_exchange_rate_usage_pipeline()
BEGIN
    IF EXISTS (
        SELECT 1 FROM exchange_rate_usage_snapshot
        WHERE usage_event_id NOT LIKE 'LEGACY:%'
    ) OR EXISTS (
        SELECT 1 FROM settlement_event_outbox
        WHERE rate_usage_id IS NOT NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'new exchange-rate usage facts exist; use forward repair instead of destructive rollback';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'settlement_event_outbox'
          AND index_name = 'uk_settlement_event_rate_usage'
    ) THEN
        ALTER TABLE settlement_event_outbox
            DROP INDEX uk_settlement_event_rate_usage;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'settlement_event_outbox'
          AND column_name = 'rate_usage_id'
    ) THEN
        ALTER TABLE settlement_event_outbox DROP COLUMN rate_usage_id;
    END IF;

    ALTER TABLE settlement_event_outbox
        MODIFY COLUMN candidate_id BIGINT NOT NULL COMMENT '来源真实交易候选主键';

    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'exchange_rate_usage_snapshot'
          AND index_name = 'uk_exchange_snapshot_usage_event'
    ) THEN
        ALTER TABLE exchange_rate_usage_snapshot
            DROP INDEX uk_exchange_snapshot_usage_event;
    END IF;

    ALTER TABLE exchange_rate_usage_snapshot
        MODIFY COLUMN producer_service VARCHAR(64) NULL
            COMMENT '产生实际使用事实的服务名';

    ALTER TABLE exchange_rate_usage_snapshot
        DROP COLUMN rate_effective_time,
        DROP COLUMN source_quote_direction,
        DROP COLUMN rate_source,
        DROP COLUMN producer_service,
        DROP COLUMN usage_event_id;
END$$
DELIMITER ;

CALL rollback_exchange_rate_usage_pipeline();
DROP PROCEDURE rollback_exchange_rate_usage_pipeline;
