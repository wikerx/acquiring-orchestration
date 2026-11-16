-- 风控审计季度分表迁移后检。
-- 所有 passed 必须为 1，且源表与对应季度目标表数量必须完全一致，才能发布 enabled=true。

SELECT 'REQUIRED_TABLES_EXIST' AS check_name,
       COUNT(*) = 7 AS passed,
       COUNT(*) AS actual_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'risk_evaluation_record',
      'risk_evaluation_hit_detail',
      'risk_evaluation_locator',
      'risk_evaluation_record_202603',
      'risk_evaluation_hit_detail_202603',
      'risk_evaluation_record_202604',
      'risk_evaluation_hit_detail_202604'
  );

SELECT 'DETAIL_ROUTING_COLUMN' AS check_name,
       COUNT(*) = 3 AS passed,
       COUNT(*) AS actual_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN (
      'risk_evaluation_hit_detail',
      'risk_evaluation_hit_detail_202603',
      'risk_evaluation_hit_detail_202604'
  )
  AND column_name = 'evaluation_time'
  AND is_nullable = 'NO'
  AND data_type = 'datetime'
  AND datetime_precision = 3;

SELECT 'LOCATOR_UNIQUE_KEY' AS check_name,
       COUNT(*) = 1 AS passed,
       COUNT(*) AS actual_count
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'risk_evaluation_locator'
  AND index_name = 'uk_risk_evaluation_locator_record'
  AND non_unique = 0
  AND seq_in_index = 1
  AND column_name = 'risk_record_no';

SELECT 'DETAIL_ROUTING_INDEX' AS check_name,
       COUNT(*) = 3 AS passed,
       COUNT(*) AS actual_count
FROM (
    SELECT table_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'risk_evaluation_hit_detail',
          'risk_evaluation_hit_detail_202603',
          'risk_evaluation_hit_detail_202604'
      )
      AND index_name = 'idx_risk_hit_time_record'
    GROUP BY table_name, index_name
    HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)
               = 'evaluation_time,risk_record_no,id'
) valid_routing_index;

SELECT 'LOCATOR_COVERS_SOURCE' AS check_name,
       (SELECT COUNT(*) FROM risk_evaluation_locator)
           = (SELECT COUNT(*) FROM risk_evaluation_record) AS passed,
       (SELECT COUNT(*) FROM risk_evaluation_record) AS source_count,
       (SELECT COUNT(*) FROM risk_evaluation_locator) AS locator_count;

SELECT 'LOCATOR_TIME_MATCHES_SOURCE' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS mismatch_count
FROM risk_evaluation_locator locator
JOIN risk_evaluation_record evaluation
  ON evaluation.risk_record_no = locator.risk_record_no
WHERE locator.evaluation_time <> evaluation.evaluation_time;

SELECT 'Q3_RECORD_COUNT_MATCH' AS check_name,
       (SELECT COUNT(*)
        FROM risk_evaluation_record
        WHERE evaluation_time >= '2026-07-01 00:00:00.000'
          AND evaluation_time < '2026-10-01 00:00:00.000')
           = (SELECT COUNT(*) FROM risk_evaluation_record_202603) AS passed,
       (SELECT COUNT(*)
        FROM risk_evaluation_record
        WHERE evaluation_time >= '2026-07-01 00:00:00.000'
          AND evaluation_time < '2026-10-01 00:00:00.000') AS source_count,
       (SELECT COUNT(*) FROM risk_evaluation_record_202603) AS target_count;

SELECT 'Q4_RECORD_COUNT_MATCH' AS check_name,
       (SELECT COUNT(*)
        FROM risk_evaluation_record
        WHERE evaluation_time >= '2026-10-01 00:00:00.000'
          AND evaluation_time < '2027-01-01 00:00:00.000')
           = (SELECT COUNT(*) FROM risk_evaluation_record_202604) AS passed,
       (SELECT COUNT(*)
        FROM risk_evaluation_record
        WHERE evaluation_time >= '2026-10-01 00:00:00.000'
          AND evaluation_time < '2027-01-01 00:00:00.000') AS source_count,
       (SELECT COUNT(*) FROM risk_evaluation_record_202604) AS target_count;

SELECT 'Q3_DETAIL_COUNT_MATCH' AS check_name,
       (SELECT COUNT(*)
        FROM risk_evaluation_hit_detail
        WHERE evaluation_time >= '2026-07-01 00:00:00.000'
          AND evaluation_time < '2026-10-01 00:00:00.000')
           = (SELECT COUNT(*) FROM risk_evaluation_hit_detail_202603) AS passed,
       (SELECT COUNT(*)
        FROM risk_evaluation_hit_detail
        WHERE evaluation_time >= '2026-07-01 00:00:00.000'
          AND evaluation_time < '2026-10-01 00:00:00.000') AS source_count,
       (SELECT COUNT(*) FROM risk_evaluation_hit_detail_202603) AS target_count;

SELECT 'Q4_DETAIL_COUNT_MATCH' AS check_name,
       (SELECT COUNT(*)
        FROM risk_evaluation_hit_detail
        WHERE evaluation_time >= '2026-10-01 00:00:00.000'
          AND evaluation_time < '2027-01-01 00:00:00.000')
           = (SELECT COUNT(*) FROM risk_evaluation_hit_detail_202604) AS passed,
       (SELECT COUNT(*)
        FROM risk_evaluation_hit_detail
        WHERE evaluation_time >= '2026-10-01 00:00:00.000'
          AND evaluation_time < '2027-01-01 00:00:00.000') AS source_count,
       (SELECT COUNT(*) FROM risk_evaluation_hit_detail_202604) AS target_count;

SELECT 'PHYSICAL_DETAIL_PARENT_MATCH' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS orphan_count
FROM (
    SELECT detail.risk_record_no, detail.evaluation_time
    FROM risk_evaluation_hit_detail_202603 detail
    LEFT JOIN risk_evaluation_record_202603 evaluation
      ON evaluation.risk_record_no = detail.risk_record_no
     AND evaluation.evaluation_time = detail.evaluation_time
    WHERE evaluation.id IS NULL
    UNION ALL
    SELECT detail.risk_record_no, detail.evaluation_time
    FROM risk_evaluation_hit_detail_202604 detail
    LEFT JOIN risk_evaluation_record_202604 evaluation
      ON evaluation.risk_record_no = detail.risk_record_no
     AND evaluation.evaluation_time = detail.evaluation_time
    WHERE evaluation.id IS NULL
) orphan_detail;

SELECT 'PHYSICAL_AUTO_INCREMENT_RANGE' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS invalid_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'risk_evaluation_record_202603',
      'risk_evaluation_hit_detail_202603',
      'risk_evaluation_record_202604',
      'risk_evaluation_hit_detail_202604'
  )
  AND (
      (table_name LIKE '%_202603'
          AND (auto_increment < 202603000000000001 OR auto_increment > 202603999999999999))
      OR
      (table_name LIKE '%_202604'
          AND (auto_increment < 202604000000000001 OR auto_increment > 202604999999999999))
  );

SELECT table_name, auto_increment
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'risk_evaluation_record_202603',
      'risk_evaluation_hit_detail_202603',
      'risk_evaluation_record_202604',
      'risk_evaluation_hit_detail_202604'
  )
ORDER BY table_name;
