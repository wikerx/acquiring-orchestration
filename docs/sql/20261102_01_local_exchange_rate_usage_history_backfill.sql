-- 本机开发库汇率使用历史事实回填。
-- 仅允许在 payment_acquiring 执行；首次执行会备份原快照表，脚本可幂等重跑。
-- 本脚本只修正/补充汇率审计事实，不修改交易、清分、结算结果、金额或状态。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS backfill_local_exchange_rate_usage_history;
DELIMITER $$
CREATE PROCEDURE backfill_local_exchange_rate_usage_history()
BEGIN
    DECLARE v_repaired_type_count BIGINT DEFAULT 0;
    DECLARE v_inserted_transaction_count BIGINT DEFAULT 0;
    DECLARE v_inserted_settlement_count BIGINT DEFAULT 0;
    DECLARE v_transaction_fact_count BIGINT DEFAULT 0;
    DECLARE v_settlement_fact_count BIGINT DEFAULT 0;
    DECLARE v_invalid_count BIGINT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF DATABASE() <> 'payment_acquiring' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'local exchange-rate backfill is restricted to payment_acquiring';
    END IF;

    IF 4 <> (
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'transaction_order_202603', 'transaction_order_202604',
              'transaction_operation_202603', 'transaction_operation_202604'
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'required local transaction history shards are missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'exchange_rate_usage_snapshot'
          AND column_name = 'producer_service'
          AND is_nullable = 'NO'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'run exchange-rate usage pipeline migration before local history backfill';
    END IF;

    -- 备份表只在第一次运行时写入，避免重跑把回填结果混入执行前基线。
    CREATE TABLE IF NOT EXISTS exchange_rate_usage_snapshot_backup_20261102
        LIKE exchange_rate_usage_snapshot;
    IF NOT EXISTS (
        SELECT 1 FROM exchange_rate_usage_snapshot_backup_20261102 LIMIT 1
    ) THEN
        INSERT INTO exchange_rate_usage_snapshot_backup_20261102
        SELECT * FROM exchange_rate_usage_snapshot;
    END IF;

    DROP TEMPORARY TABLE IF EXISTS tmp_exchange_rate_source_order;
    CREATE TEMPORARY TABLE tmp_exchange_rate_source_order (
        root_transaction_id VARCHAR(64) NOT NULL,
        rate_source VARCHAR(64) NOT NULL,
        rate_time DATETIME(3) NOT NULL,
        PRIMARY KEY (root_transaction_id)
    ) ENGINE=InnoDB;

    INSERT INTO tmp_exchange_rate_source_order (root_transaction_id, rate_source, rate_time)
    SELECT history.root_transaction_id, history.rate_source, history.rate_time
    FROM (
        SELECT root_transaction_id, rate_source, rate_time
        FROM transaction_order_202603
        WHERE deleted = 0
        UNION ALL
        SELECT root_transaction_id, rate_source, rate_time
        FROM transaction_order_202604
        WHERE deleted = 0
    ) history
    WHERE history.rate_source IS NOT NULL
      AND history.rate_time IS NOT NULL;

    DROP TEMPORARY TABLE IF EXISTS tmp_exchange_rate_transaction_fact;
    CREATE TEMPORARY TABLE tmp_exchange_rate_transaction_fact (
        business_no VARCHAR(128) NOT NULL,
        business_type VARCHAR(64) NOT NULL,
        base_currency CHAR(3) NOT NULL,
        quote_currency CHAR(3) NOT NULL,
        used_rate DECIMAL(24, 12) NOT NULL,
        business_rate_id BIGINT NOT NULL,
        raw_rate_id BIGINT NULL,
        rule_id BIGINT NULL,
        rate_source VARCHAR(64) NOT NULL,
        rate_effective_time DATETIME(3) NOT NULL,
        calculation_description VARCHAR(512) NOT NULL,
        applied_time DATETIME(3) NOT NULL,
        PRIMARY KEY (business_no)
    ) ENGINE=InnoDB;

    -- 首次支付、授权和预授权使用主单冻结的真实跨币种交易汇率。
    INSERT INTO tmp_exchange_rate_transaction_fact (
        business_no, business_type, base_currency, quote_currency, used_rate,
        business_rate_id, raw_rate_id, rule_id, rate_source, rate_effective_time,
        calculation_description, applied_time
    )
    SELECT orders.root_transaction_id,
           orders.transaction_type,
           orders.label_currency,
           orders.transaction_currency,
           orders.transaction_rate,
           rate.id,
           rate.raw_rate_id,
           rate.rule_id,
           rate.source_code,
           rate.effective_time,
           CONCAT('transactionAmount = labelAmount * transactionRate; targetCurrencyExponent=',
                  orders.currency_exponent),
           orders.create_time
    FROM (
        SELECT root_transaction_id, transaction_type, label_currency, transaction_currency,
               transaction_rate, rate_source, rate_time, currency_exponent,
               dcc_enabled, edc_enabled, create_time
        FROM transaction_order_202603
        WHERE deleted = 0
        UNION ALL
        SELECT root_transaction_id, transaction_type, label_currency, transaction_currency,
               transaction_rate, rate_source, rate_time, currency_exponent,
               dcc_enabled, edc_enabled, create_time
        FROM transaction_order_202604
        WHERE deleted = 0
    ) orders
    JOIN exchange_business_rate rate
      ON rate.deleted = 0
     AND rate.rate_type = 'TRANSACTION_RATE'
     AND rate.base_currency = orders.label_currency
     AND rate.quote_currency = orders.transaction_currency
     AND rate.final_rate = orders.transaction_rate
     AND rate.source_code = orders.rate_source
     AND rate.effective_time = orders.rate_time
    WHERE orders.transaction_type IN ('PAYMENT', 'AUTHORIZATION', 'PRE_AUTHORIZATION')
      AND orders.label_currency <> orders.transaction_currency
      AND orders.transaction_rate > 0
      AND (orders.dcc_enabled = 1 OR orders.edc_enabled = 1);

    -- 请款、预授权完成和增量授权只接受能精确追溯到首笔冻结版本的汇率。
    INSERT INTO tmp_exchange_rate_transaction_fact (
        business_no, business_type, base_currency, quote_currency, used_rate,
        business_rate_id, raw_rate_id, rule_id, rate_source, rate_effective_time,
        calculation_description, applied_time
    )
    SELECT operations.transaction_id,
           operations.transaction_type,
           operations.label_currency,
           operations.transaction_currency,
           operations.transaction_rate,
           rate.id,
           rate.raw_rate_id,
           rate.rule_id,
           rate.source_code,
           rate.effective_time,
           CONCAT('transactionAmount = labelAmount * frozenTransactionRate; sourceTransactionId=',
                  operations.source_transaction_id,
                  '; targetCurrencyExponent=', operations.currency_exponent),
           operations.create_time
    FROM (
        SELECT transaction_id, source_transaction_id, transaction_type,
               label_currency, transaction_currency, transaction_rate,
               currency_exponent, dcc_enabled, edc_enabled, create_time
        FROM transaction_operation_202603
        WHERE deleted = 0
        UNION ALL
        SELECT transaction_id, source_transaction_id, transaction_type,
               label_currency, transaction_currency, transaction_rate,
               currency_exponent, dcc_enabled, edc_enabled, create_time
        FROM transaction_operation_202604
        WHERE deleted = 0
    ) operations
    JOIN tmp_exchange_rate_source_order source_order
      ON source_order.root_transaction_id = operations.source_transaction_id
    JOIN exchange_business_rate rate
      ON rate.deleted = 0
     AND rate.rate_type = 'TRANSACTION_RATE'
     AND rate.base_currency = operations.label_currency
     AND rate.quote_currency = operations.transaction_currency
     AND rate.final_rate = operations.transaction_rate
     AND rate.source_code = source_order.rate_source
     AND rate.effective_time = source_order.rate_time
    WHERE operations.transaction_type IN (
              'CAPTURE', 'PRE_AUTH_COMPLETION', 'INCREMENTAL_AUTHORIZATION', 'REFUND'
          )
      AND operations.label_currency <> operations.transaction_currency
      AND operations.transaction_rate > 0
      AND (operations.dcc_enabled = 1 OR operations.edc_enabled = 1);

    SET v_transaction_fact_count = (
        SELECT COUNT(*) FROM tmp_exchange_rate_transaction_fact
    );

    START TRANSACTION;

    -- 修复旧生产者曾把授权和预授权统一写成 PAYMENT 的历史类型。
    UPDATE exchange_rate_usage_snapshot snapshot
    JOIN tmp_exchange_rate_transaction_fact fact
      ON snapshot.rate_type = 'TRANSACTION_RATE'
     AND snapshot.business_no = fact.business_no
     AND snapshot.deleted = 0
    SET snapshot.business_type = fact.business_type,
        snapshot.producer_service = 'service-payment'
    WHERE snapshot.business_type <> fact.business_type
       OR snapshot.producer_service <> 'service-payment';
    SET v_repaired_type_count = ROW_COUNT();

    INSERT IGNORE INTO exchange_rate_usage_snapshot (
        usage_event_id, producer_service, rate_type, usage_scene,
        business_type, business_no, base_currency, quote_currency, used_rate,
        business_rate_id, raw_rate_id, rule_id, rate_source,
        source_quote_direction, rate_effective_time, calculation_description,
        applied_time, create_by, create_time, deleted
    )
    SELECT CONCAT('FXTR:', fact.business_no),
           'service-payment',
           'TRANSACTION_RATE',
           'TRANSACTION',
           fact.business_type,
           fact.business_no,
           fact.base_currency,
           fact.quote_currency,
           fact.used_rate,
           fact.business_rate_id,
           fact.raw_rate_id,
           fact.rule_id,
           fact.rate_source,
           'DIRECT',
           fact.rate_effective_time,
           fact.calculation_description,
           fact.applied_time,
           'history-backfill',
           CURRENT_TIMESTAMP(3),
           0
    FROM tmp_exchange_rate_transaction_fact fact
    WHERE NOT EXISTS (
        SELECT 1
        FROM exchange_rate_usage_snapshot snapshot
        WHERE snapshot.rate_type = 'TRANSACTION_RATE'
          AND snapshot.business_no = fact.business_no
          AND snapshot.deleted = 0
    );
    SET v_inserted_transaction_count = ROW_COUNT();

    -- 结算仅回填已入账批次中被结果明细实际引用的跨币种锁定汇率。
    INSERT IGNORE INTO exchange_rate_usage_snapshot (
        usage_event_id, producer_service, rate_type, usage_scene,
        business_type, business_no, base_currency, quote_currency, used_rate,
        business_rate_id, raw_rate_id, rule_id, rate_source,
        source_quote_direction, rate_effective_time, calculation_description,
        applied_time, create_by, create_time, deleted
    )
    SELECT CONCAT('FXST:', rate.settlement_batch_no, ':', rate.id),
           'service-settlement',
           'SETTLEMENT_RATE',
           'SETTLEMENT',
           'SETTLEMENT',
           rate.settlement_batch_no,
           rate.source_currency,
           rate.target_currency,
           rate.direct_rate,
           business_rate.id,
           business_rate.raw_rate_id,
           business_rate.rule_id,
           rate.rate_source,
           rate.source_quote_direction,
           rate.effective_time,
           CONCAT('settlementAmount = sourceAmount * lockedDirectRate; settlementBatchRateId=',
                  rate.id),
           COALESCE(batch.calculated_time, batch.posted_time, rate.locked_time),
           'history-backfill',
           CURRENT_TIMESTAMP(3),
           0
    FROM settlement_batch_rate rate
    JOIN settlement_batch batch
      ON batch.settlement_batch_no = rate.settlement_batch_no
     AND batch.batch_status = 'POSTED'
    JOIN exchange_business_rate business_rate
      ON business_rate.id = CAST(rate.quote_id AS UNSIGNED)
     AND business_rate.deleted = 0
     AND business_rate.rate_type = 'SETTLEMENT_RATE'
    WHERE rate.source_currency <> rate.target_currency
      AND rate.quote_id REGEXP '^[1-9][0-9]*$'
      AND EXISTS (
          SELECT 1
          FROM settlement_result_item result_item
          WHERE result_item.settlement_batch_rate_id = rate.id
      )
      AND NOT EXISTS (
          SELECT 1
          FROM exchange_rate_usage_snapshot snapshot
          WHERE snapshot.rate_type = 'SETTLEMENT_RATE'
            AND snapshot.usage_event_id = CONCAT(
                'FXST:', rate.settlement_batch_no, ':', rate.id
            )
            AND snapshot.deleted = 0
      );
    SET v_inserted_settlement_count = ROW_COUNT();

    SET v_settlement_fact_count = (
        SELECT COUNT(*)
        FROM settlement_batch_rate rate
        JOIN settlement_batch batch
          ON batch.settlement_batch_no = rate.settlement_batch_no
         AND batch.batch_status = 'POSTED'
        JOIN exchange_business_rate business_rate
          ON business_rate.id = CAST(rate.quote_id AS UNSIGNED)
         AND business_rate.deleted = 0
         AND business_rate.rate_type = 'SETTLEMENT_RATE'
        WHERE rate.source_currency <> rate.target_currency
          AND rate.quote_id REGEXP '^[1-9][0-9]*$'
          AND EXISTS (
              SELECT 1
              FROM settlement_result_item result_item
              WHERE result_item.settlement_batch_rate_id = rate.id
          )
    );

    SELECT COUNT(*) INTO v_invalid_count
    FROM tmp_exchange_rate_transaction_fact fact
    LEFT JOIN exchange_rate_usage_snapshot snapshot
      ON snapshot.rate_type = 'TRANSACTION_RATE'
     AND snapshot.business_no = fact.business_no
     AND snapshot.deleted = 0
    WHERE snapshot.id IS NULL
       OR snapshot.producer_service <> 'service-payment'
       OR snapshot.usage_scene <> 'TRANSACTION'
       OR snapshot.business_type <> fact.business_type
       OR snapshot.base_currency <> fact.base_currency
       OR snapshot.quote_currency <> fact.quote_currency
       OR snapshot.used_rate <> fact.used_rate
       OR snapshot.business_rate_id <> fact.business_rate_id
       OR NOT (snapshot.raw_rate_id <=> fact.raw_rate_id)
       OR NOT (snapshot.rule_id <=> fact.rule_id)
       OR snapshot.rate_source <> fact.rate_source
       OR snapshot.source_quote_direction <> 'DIRECT'
       OR snapshot.rate_effective_time <> fact.rate_effective_time;
    IF v_invalid_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'transaction exchange-rate history backfill verification failed';
    END IF;

    SELECT COUNT(*) INTO v_invalid_count
    FROM settlement_batch_rate rate
    JOIN settlement_batch batch
      ON batch.settlement_batch_no = rate.settlement_batch_no
     AND batch.batch_status = 'POSTED'
    JOIN exchange_business_rate business_rate
      ON business_rate.id = CAST(rate.quote_id AS UNSIGNED)
     AND business_rate.deleted = 0
     AND business_rate.rate_type = 'SETTLEMENT_RATE'
    LEFT JOIN exchange_rate_usage_snapshot snapshot
      ON snapshot.usage_event_id = CONCAT('FXST:', rate.settlement_batch_no, ':', rate.id)
     AND snapshot.deleted = 0
    WHERE rate.source_currency <> rate.target_currency
      AND rate.quote_id REGEXP '^[1-9][0-9]*$'
      AND EXISTS (
          SELECT 1
          FROM settlement_result_item result_item
          WHERE result_item.settlement_batch_rate_id = rate.id
      )
      AND (
          snapshot.id IS NULL
          OR snapshot.producer_service <> 'service-settlement'
          OR snapshot.rate_type <> 'SETTLEMENT_RATE'
          OR snapshot.usage_scene <> 'SETTLEMENT'
          OR snapshot.business_type <> 'SETTLEMENT'
          OR snapshot.business_rate_id <> business_rate.id
          OR snapshot.used_rate <> rate.direct_rate
      );
    IF v_invalid_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'settlement exchange-rate history backfill verification failed';
    END IF;

    COMMIT;

    SELECT v_transaction_fact_count AS transaction_fact_count,
           v_repaired_type_count AS repaired_existing_type_count,
           v_inserted_transaction_count AS inserted_transaction_snapshot_count,
           v_settlement_fact_count AS settlement_fact_count,
           v_inserted_settlement_count AS inserted_settlement_snapshot_count;
END$$
DELIMITER ;

CALL backfill_local_exchange_rate_usage_history();
DROP PROCEDURE backfill_local_exchange_rate_usage_history;

SELECT rate_type, business_type, COUNT(*) AS snapshot_count
FROM exchange_rate_usage_snapshot
WHERE deleted = 0
GROUP BY rate_type, business_type
ORDER BY rate_type, business_type;
