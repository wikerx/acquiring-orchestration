-- 2027-Q1 分表结构修复和风控双表预建最终后检。
-- 执行时点：完成 20261108_06，并通过 service-job 执行一次 SHARDING_TABLE_PRE_CREATE 后。
-- 所有检查必须输出 passed=1，才能把 202701 加入 Nacos 正式节点。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `postcheck_future_quarter_schema_repair_20261108`;
DELIMITER $$
CREATE PROCEDURE `postcheck_future_quarter_schema_repair_20261108`()
BEGIN
    DECLARE required_table_count INT DEFAULT 0;
    DECLARE target_row_count BIGINT DEFAULT 0;
    DECLARE column_mismatch_count INT DEFAULT 0;
    DECLARE index_mismatch_count INT DEFAULT 0;
    DECLARE table_option_mismatch_count INT DEFAULT 0;
    DECLARE invalid_auto_increment_count INT DEFAULT 0;
    DECLARE latest_create_log_count INT DEFAULT 0;
    DECLARE invalid_create_log_count INT DEFAULT 0;
    DECLARE sharding_job_count INT DEFAULT 0;
    DECLARE invalid_job_status_count INT DEFAULT 0;

    SELECT COUNT(*) INTO required_table_count
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN (
          'transaction_order_202701',
          'transaction_operation_202701',
          'transaction_amount_change_log_202701',
          'risk_evaluation_record_202701',
          'risk_evaluation_hit_detail_202701'
      );
    IF required_table_count <> 5 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter postcheck requires all five 202701 tables';
    END IF;

    SELECT (SELECT COUNT(*) FROM transaction_order_202701)
         + (SELECT COUNT(*) FROM transaction_operation_202701)
         + (SELECT COUNT(*) FROM transaction_amount_change_log_202701)
         + (SELECT COUNT(*) FROM risk_evaluation_record_202701)
         + (SELECT COUNT(*) FROM risk_evaluation_hit_detail_202701)
    INTO target_row_count;
    IF target_row_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter postcheck found unexpected 202701 business data';
    END IF;

    SELECT COUNT(*) INTO column_mismatch_count
    FROM (
        SELECT pair_rule.pair_name,
               column_meta.COLUMN_NAME,
               MAX(CASE WHEN column_meta.TABLE_NAME = pair_rule.template_table
                        THEN CONCAT_WS('|', column_meta.ORDINAL_POSITION, column_meta.COLUMN_TYPE,
                             column_meta.IS_NULLABLE, COALESCE(column_meta.COLUMN_DEFAULT, '<NULL>'),
                             column_meta.EXTRA, COALESCE(column_meta.CHARACTER_SET_NAME, '-'),
                             COALESCE(column_meta.COLLATION_NAME, '-'), column_meta.COLUMN_COMMENT)
                   END) AS template_definition,
               MAX(CASE WHEN column_meta.TABLE_NAME = pair_rule.physical_table
                        THEN CONCAT_WS('|', column_meta.ORDINAL_POSITION, column_meta.COLUMN_TYPE,
                             column_meta.IS_NULLABLE, COALESCE(column_meta.COLUMN_DEFAULT, '<NULL>'),
                             column_meta.EXTRA, COALESCE(column_meta.CHARACTER_SET_NAME, '-'),
                             COALESCE(column_meta.COLLATION_NAME, '-'), column_meta.COLUMN_COMMENT)
                   END) AS physical_definition
        FROM (
            SELECT 'transaction_order' AS pair_name,
                   'transaction_order' AS template_table,
                   'transaction_order_202701' AS physical_table
            UNION ALL
            SELECT 'transaction_operation', 'transaction_operation', 'transaction_operation_202701'
            UNION ALL
            SELECT 'transaction_amount_change_log', 'transaction_amount_change_log',
                   'transaction_amount_change_log_202701'
            UNION ALL
            SELECT 'risk_evaluation_record', 'risk_evaluation_record', 'risk_evaluation_record_202701'
            UNION ALL
            SELECT 'risk_evaluation_hit_detail', 'risk_evaluation_hit_detail',
                   'risk_evaluation_hit_detail_202701'
        ) pair_rule
        JOIN information_schema.COLUMNS column_meta
          ON column_meta.TABLE_SCHEMA = DATABASE()
         AND column_meta.TABLE_NAME IN (pair_rule.template_table, pair_rule.physical_table)
        GROUP BY pair_rule.pair_name, column_meta.COLUMN_NAME
        HAVING NOT (template_definition <=> physical_definition)
    ) mismatch;
    IF column_mismatch_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter postcheck found column definition mismatch';
    END IF;

    SELECT COUNT(*) INTO index_mismatch_count
    FROM (
        SELECT pair_rule.pair_name,
               index_meta.INDEX_NAME,
               MAX(CASE WHEN index_meta.TABLE_NAME = pair_rule.template_table
                        THEN index_meta.index_definition END) AS template_definition,
               MAX(CASE WHEN index_meta.TABLE_NAME = pair_rule.physical_table
                        THEN index_meta.index_definition END) AS physical_definition
        FROM (
            SELECT 'transaction_order' AS pair_name,
                   'transaction_order' AS template_table,
                   'transaction_order_202701' AS physical_table
            UNION ALL
            SELECT 'transaction_operation', 'transaction_operation', 'transaction_operation_202701'
            UNION ALL
            SELECT 'transaction_amount_change_log', 'transaction_amount_change_log',
                   'transaction_amount_change_log_202701'
            UNION ALL
            SELECT 'risk_evaluation_record', 'risk_evaluation_record', 'risk_evaluation_record_202701'
            UNION ALL
            SELECT 'risk_evaluation_hit_detail', 'risk_evaluation_hit_detail',
                   'risk_evaluation_hit_detail_202701'
        ) pair_rule
        JOIN (
            SELECT TABLE_NAME,
                   INDEX_NAME,
                   CONCAT(NON_UNIQUE, '|', INDEX_TYPE, '|',
                          GROUP_CONCAT(CONCAT(SEQ_IN_INDEX, ':', COLUMN_NAME,
                              IF(SUB_PART IS NULL, '', CONCAT('(', SUB_PART, ')')))
                              ORDER BY SEQ_IN_INDEX SEPARATOR ',')) AS index_definition
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME IN (
                  'transaction_order', 'transaction_order_202701',
                  'transaction_operation', 'transaction_operation_202701',
                  'transaction_amount_change_log', 'transaction_amount_change_log_202701',
                  'risk_evaluation_record', 'risk_evaluation_record_202701',
                  'risk_evaluation_hit_detail', 'risk_evaluation_hit_detail_202701'
              )
            GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE, INDEX_TYPE
        ) index_meta
          ON index_meta.TABLE_NAME IN (pair_rule.template_table, pair_rule.physical_table)
        GROUP BY pair_rule.pair_name, index_meta.INDEX_NAME
        HAVING NOT (template_definition <=> physical_definition)
    ) mismatch;
    IF index_mismatch_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter postcheck found index definition mismatch';
    END IF;

    SELECT COUNT(*) INTO table_option_mismatch_count
    FROM (
        SELECT pair_rule.pair_name,
               COUNT(DISTINCT CONCAT_WS('|', table_meta.ENGINE, table_meta.ROW_FORMAT,
                                        table_meta.TABLE_COLLATION)) AS definition_count
        FROM (
            SELECT 'transaction_order' AS pair_name,
                   'transaction_order' AS template_table,
                   'transaction_order_202701' AS physical_table
            UNION ALL
            SELECT 'transaction_operation', 'transaction_operation', 'transaction_operation_202701'
            UNION ALL
            SELECT 'transaction_amount_change_log', 'transaction_amount_change_log',
                   'transaction_amount_change_log_202701'
            UNION ALL
            SELECT 'risk_evaluation_record', 'risk_evaluation_record', 'risk_evaluation_record_202701'
            UNION ALL
            SELECT 'risk_evaluation_hit_detail', 'risk_evaluation_hit_detail',
                   'risk_evaluation_hit_detail_202701'
        ) pair_rule
        JOIN information_schema.TABLES table_meta
          ON table_meta.TABLE_SCHEMA = DATABASE()
         AND table_meta.TABLE_NAME IN (pair_rule.template_table, pair_rule.physical_table)
        GROUP BY pair_rule.pair_name
        HAVING COUNT(*) <> 2 OR definition_count <> 1
    ) mismatch;
    IF table_option_mismatch_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter postcheck found table option mismatch';
    END IF;

    SELECT COUNT(*) INTO invalid_auto_increment_count
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN (
          'transaction_order_202701',
          'transaction_operation_202701',
          'transaction_amount_change_log_202701',
          'risk_evaluation_record_202701',
          'risk_evaluation_hit_detail_202701'
      )
      AND (AUTO_INCREMENT IS NULL
           OR AUTO_INCREMENT < 202701000000000001
           OR AUTO_INCREMENT > 202701999999999999);
    IF invalid_auto_increment_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter postcheck found invalid 202701 auto increment range';
    END IF;

    SELECT COUNT(*) INTO latest_create_log_count
    FROM sys_sharding_table_create_log
    WHERE dry_run = 0;
    IF latest_create_log_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter postcheck requires a sharding create log';
    END IF;

    SELECT COUNT(*) INTO invalid_create_log_count
    FROM (
        SELECT dry_run, planned_count, failed_count, schema_mismatch_count, run_status
        FROM sys_sharding_table_create_log
        WHERE dry_run = 0
        ORDER BY id DESC
        LIMIT 1
    ) latest_log
    WHERE planned_count <> 60
       OR failed_count <> 0
       OR schema_mismatch_count <> 0
       OR run_status <> 'SUCCESS';
    IF invalid_create_log_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter postcheck found unsuccessful latest sharding create log';
    END IF;

    SELECT COUNT(*) INTO sharding_job_count
    FROM sys_job_task
    WHERE job_code = 'SHARDING_TABLE_PRE_CREATE';
    IF sharding_job_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter postcheck requires exactly one sharding job';
    END IF;

    SELECT COUNT(*) INTO invalid_job_status_count
    FROM sys_job_task
    WHERE job_code = 'SHARDING_TABLE_PRE_CREATE'
      AND (status <> 'ENABLED' OR last_run_status <> 'SUCCESS');
    IF invalid_job_status_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'future quarter postcheck found unsuccessful sharding job status';
    END IF;
END$$
DELIMITER ;

CALL `postcheck_future_quarter_schema_repair_20261108`();
DROP PROCEDURE `postcheck_future_quarter_schema_repair_20261108`;

SELECT 'FUTURE_QUARTER_REQUIRED_TABLES' AS check_name,
       COUNT(*) = 5 AS passed,
       COUNT(*) AS actual_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
      'transaction_order_202701',
      'transaction_operation_202701',
      'transaction_amount_change_log_202701',
      'risk_evaluation_record_202701',
      'risk_evaluation_hit_detail_202701'
  );

SELECT 'FUTURE_QUARTER_CREATE_LOG' AS check_name,
       run_status = 'SUCCESS' AND failed_count = 0 AND schema_mismatch_count = 0 AS passed,
       planned_count, created_count, skipped_count, failed_count, schema_mismatch_count, run_status
FROM sys_sharding_table_create_log
WHERE dry_run = 0
ORDER BY id DESC
LIMIT 1;

SELECT 'FUTURE_QUARTER_JOB_STATUS' AS check_name,
       status = 'ENABLED' AND last_run_status = 'SUCCESS' AS passed,
       status, last_run_status, last_trigger_time
FROM sys_job_task
WHERE job_code = 'SHARDING_TABLE_PRE_CREATE';

SELECT TABLE_NAME, AUTO_INCREMENT, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
      'transaction_order_202701',
      'transaction_operation_202701',
      'transaction_amount_change_log_202701',
      'risk_evaluation_record_202701',
      'risk_evaluation_hit_detail_202701'
  )
ORDER BY TABLE_NAME;
