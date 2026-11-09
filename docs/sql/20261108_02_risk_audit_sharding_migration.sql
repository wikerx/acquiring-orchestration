-- 风控审计主记录/执行明细季度分表与定位表迁移。
-- 执行约束：先完成 20261108_01 前检；执行期间停止 service-data 风控审计消费者写入。
-- 本脚本保留原逻辑单表作为模板和回退数据源，不删除、不重命名历史事实。

DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_risk_audit_quarter_sharding$$
CREATE PROCEDURE migrate_risk_audit_quarter_sharding()
BEGIN
    DECLARE unsupported_count BIGINT DEFAULT 0;
    DECLARE orphan_count BIGINT DEFAULT 0;
    DECLARE locator_conflict_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO unsupported_count
    FROM risk_evaluation_record
    WHERE evaluation_time < '2026-07-01 00:00:00.000'
       OR evaluation_time >= '2027-01-01 00:00:00.000';
    IF unsupported_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'risk audit migration contains records outside 202603/202604';
    END IF;

    SELECT COUNT(*) INTO orphan_count
    FROM risk_evaluation_hit_detail detail
    LEFT JOIN risk_evaluation_record evaluation
           ON evaluation.risk_record_no = detail.risk_record_no
    WHERE evaluation.id IS NULL;
    IF orphan_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'risk audit migration contains orphan hit details';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'risk_evaluation_hit_detail'
          AND column_name = 'evaluation_time'
    ) THEN
        ALTER TABLE risk_evaluation_hit_detail
            ADD COLUMN evaluation_time DATETIME(3) NULL COMMENT '所属风控评估时间，也是季度分片路由键'
            AFTER risk_record_no;
    END IF;

    UPDATE risk_evaluation_hit_detail detail
    JOIN risk_evaluation_record evaluation
      ON evaluation.risk_record_no = detail.risk_record_no
    SET detail.evaluation_time = evaluation.evaluation_time
    WHERE detail.evaluation_time IS NULL;

    IF EXISTS (
        SELECT 1 FROM risk_evaluation_hit_detail WHERE evaluation_time IS NULL LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'risk audit detail evaluation_time backfill is incomplete';
    END IF;

    ALTER TABLE risk_evaluation_hit_detail
        MODIFY COLUMN evaluation_time DATETIME(3) NOT NULL COMMENT '所属风控评估时间，也是季度分片路由键';

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'risk_evaluation_hit_detail'
          AND index_name = 'idx_risk_hit_time_record'
    ) THEN
        ALTER TABLE risk_evaluation_hit_detail
            ADD KEY idx_risk_hit_time_record (evaluation_time, risk_record_no, id);
    END IF;

    CREATE TABLE IF NOT EXISTS risk_evaluation_locator (
        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
        risk_record_no VARCHAR(64) NOT NULL COMMENT '全局唯一风控记录号',
        evaluation_time DATETIME(3) NOT NULL COMMENT '原始评估时间，用于精确定位季度分片',
        create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
        PRIMARY KEY (id),
        UNIQUE KEY uk_risk_evaluation_locator_record (risk_record_no),
        KEY idx_risk_evaluation_locator_time (evaluation_time, risk_record_no)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
      COMMENT='风控记录号季度定位与跨季度幂等表';

    SELECT COUNT(*) INTO locator_conflict_count
    FROM risk_evaluation_locator locator
    JOIN risk_evaluation_record evaluation
      ON evaluation.risk_record_no = locator.risk_record_no
    WHERE locator.evaluation_time <> evaluation.evaluation_time;
    IF locator_conflict_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'risk audit locator contains conflicting evaluation_time';
    END IF;

    INSERT INTO risk_evaluation_locator (risk_record_no, evaluation_time)
    SELECT evaluation.risk_record_no, evaluation.evaluation_time
    FROM risk_evaluation_record evaluation
    LEFT JOIN risk_evaluation_locator locator
      ON locator.risk_record_no = evaluation.risk_record_no
    WHERE locator.id IS NULL;

    CREATE TABLE IF NOT EXISTS risk_evaluation_record_202603 LIKE risk_evaluation_record;
    CREATE TABLE IF NOT EXISTS risk_evaluation_hit_detail_202603 LIKE risk_evaluation_hit_detail;
    CREATE TABLE IF NOT EXISTS risk_evaluation_record_202604 LIKE risk_evaluation_record;
    CREATE TABLE IF NOT EXISTS risk_evaluation_hit_detail_202604 LIKE risk_evaluation_hit_detail;

    INSERT INTO risk_evaluation_record_202603 (
        id, risk_record_no, merchant_id, merchant_name, merchant_order_no, payment_order_no,
        transaction_amount, transaction_currency, risk_level, decision_result,
        decision_reason_code, decision_reason, hit_count, evaluation_time, create_time
    )
    SELECT id, risk_record_no, merchant_id, merchant_name, merchant_order_no, payment_order_no,
           transaction_amount, transaction_currency, risk_level, decision_result,
           decision_reason_code, decision_reason, hit_count, evaluation_time, create_time
    FROM risk_evaluation_record source
    WHERE source.evaluation_time >= '2026-07-01 00:00:00.000'
      AND source.evaluation_time < '2026-10-01 00:00:00.000'
      AND NOT EXISTS (
          SELECT 1 FROM risk_evaluation_record_202603 target WHERE target.id = source.id
      );

    INSERT INTO risk_evaluation_record_202604 (
        id, risk_record_no, merchant_id, merchant_name, merchant_order_no, payment_order_no,
        transaction_amount, transaction_currency, risk_level, decision_result,
        decision_reason_code, decision_reason, hit_count, evaluation_time, create_time
    )
    SELECT id, risk_record_no, merchant_id, merchant_name, merchant_order_no, payment_order_no,
           transaction_amount, transaction_currency, risk_level, decision_result,
           decision_reason_code, decision_reason, hit_count, evaluation_time, create_time
    FROM risk_evaluation_record source
    WHERE source.evaluation_time >= '2026-10-01 00:00:00.000'
      AND source.evaluation_time < '2027-01-01 00:00:00.000'
      AND NOT EXISTS (
          SELECT 1 FROM risk_evaluation_record_202604 target WHERE target.id = source.id
      );

    INSERT INTO risk_evaluation_hit_detail_202603 (
        id, risk_record_no, evaluation_time, module_type, function_code, function_name, rule_id,
        hit_element, hit_value_masked, risk_level, decision_result, reason_code, decision_reason,
        decision_time, time_window_seconds, threshold_count, elements_json, current_count,
        stage_code, stage_name, stage_order, match_result, decision_effect
    )
    SELECT id, risk_record_no, evaluation_time, module_type, function_code, function_name, rule_id,
           hit_element, hit_value_masked, risk_level, decision_result, reason_code, decision_reason,
           decision_time, time_window_seconds, threshold_count, elements_json, current_count,
           stage_code, stage_name, stage_order, match_result, decision_effect
    FROM risk_evaluation_hit_detail source
    WHERE source.evaluation_time >= '2026-07-01 00:00:00.000'
      AND source.evaluation_time < '2026-10-01 00:00:00.000'
      AND NOT EXISTS (
          SELECT 1 FROM risk_evaluation_hit_detail_202603 target WHERE target.id = source.id
      );

    INSERT INTO risk_evaluation_hit_detail_202604 (
        id, risk_record_no, evaluation_time, module_type, function_code, function_name, rule_id,
        hit_element, hit_value_masked, risk_level, decision_result, reason_code, decision_reason,
        decision_time, time_window_seconds, threshold_count, elements_json, current_count,
        stage_code, stage_name, stage_order, match_result, decision_effect
    )
    SELECT id, risk_record_no, evaluation_time, module_type, function_code, function_name, rule_id,
           hit_element, hit_value_masked, risk_level, decision_result, reason_code, decision_reason,
           decision_time, time_window_seconds, threshold_count, elements_json, current_count,
           stage_code, stage_name, stage_order, match_result, decision_effect
    FROM risk_evaluation_hit_detail source
    WHERE source.evaluation_time >= '2026-10-01 00:00:00.000'
      AND source.evaluation_time < '2027-01-01 00:00:00.000'
      AND NOT EXISTS (
          SELECT 1 FROM risk_evaluation_hit_detail_202604 target WHERE target.id = source.id
      );

    ALTER TABLE risk_evaluation_record_202603 AUTO_INCREMENT = 202603000000000001;
    ALTER TABLE risk_evaluation_hit_detail_202603 AUTO_INCREMENT = 202603000000000001;
    ALTER TABLE risk_evaluation_record_202604 AUTO_INCREMENT = 202604000000000001;
    ALTER TABLE risk_evaluation_hit_detail_202604 AUTO_INCREMENT = 202604000000000001;
END$$

CALL migrate_risk_audit_quarter_sharding()$$
DROP PROCEDURE IF EXISTS migrate_risk_audit_quarter_sharding$$

DELIMITER ;
