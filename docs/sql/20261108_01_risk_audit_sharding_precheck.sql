-- 风控审计季度分表迁移前检。
-- 执行顺序：01 前检 -> 停止 service-data 风控审计消费者写入 -> 02 迁移 -> 03 后检。
-- 本脚本只读，不修改任何业务事实。

SELECT 'SOURCE_TABLES_EXIST' AS check_name,
       COUNT(*) = 2 AS passed,
       COUNT(*) AS actual_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('risk_evaluation_record', 'risk_evaluation_hit_detail');

SELECT 'RISK_RECORD_NO_NOT_BLANK' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS invalid_count
FROM risk_evaluation_record
WHERE risk_record_no IS NULL OR TRIM(risk_record_no) = '';

SELECT 'RISK_RECORD_NO_UNIQUE' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS duplicate_group_count
FROM (
    SELECT risk_record_no
    FROM risk_evaluation_record
    GROUP BY risk_record_no
    HAVING COUNT(*) > 1
) duplicated;

SELECT 'SUPPORTED_QUARTER_RANGE' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS unsupported_count
FROM risk_evaluation_record
WHERE evaluation_time < '2026-07-01 00:00:00.000'
   OR evaluation_time >= '2027-01-01 00:00:00.000';

SELECT 'DETAIL_HAS_PARENT_RECORD' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS orphan_count
FROM risk_evaluation_hit_detail detail
LEFT JOIN risk_evaluation_record evaluation
       ON evaluation.risk_record_no = detail.risk_record_no
WHERE evaluation.id IS NULL;

SELECT 'Q3_SOURCE_ID_RANGE' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS overflow_count
FROM risk_evaluation_record
WHERE evaluation_time >= '2026-07-01 00:00:00.000'
  AND evaluation_time < '2026-10-01 00:00:00.000'
  AND id >= 202603000000000001;

SELECT 'Q4_SOURCE_ID_RANGE' AS check_name,
       COUNT(*) = 0 AS passed,
       COUNT(*) AS overflow_count
FROM risk_evaluation_record
WHERE evaluation_time >= '2026-10-01 00:00:00.000'
  AND evaluation_time < '2027-01-01 00:00:00.000'
  AND id >= 202604000000000001;

SELECT CASE
           WHEN evaluation_time >= '2026-07-01 00:00:00.000'
                AND evaluation_time < '2026-10-01 00:00:00.000' THEN '202603'
           WHEN evaluation_time >= '2026-10-01 00:00:00.000'
                AND evaluation_time < '2027-01-01 00:00:00.000' THEN '202604'
           ELSE 'UNSUPPORTED'
       END AS quarter_suffix,
       COUNT(*) AS record_count,
       MIN(evaluation_time) AS min_evaluation_time,
       MAX(evaluation_time) AS max_evaluation_time
FROM risk_evaluation_record
GROUP BY quarter_suffix
ORDER BY quarter_suffix;

SELECT CASE
           WHEN evaluation.evaluation_time >= '2026-07-01 00:00:00.000'
                AND evaluation.evaluation_time < '2026-10-01 00:00:00.000' THEN '202603'
           WHEN evaluation.evaluation_time >= '2026-10-01 00:00:00.000'
                AND evaluation.evaluation_time < '2027-01-01 00:00:00.000' THEN '202604'
           ELSE 'UNSUPPORTED'
       END AS quarter_suffix,
       COUNT(*) AS detail_count
FROM risk_evaluation_hit_detail detail
JOIN risk_evaluation_record evaluation
  ON evaluation.risk_record_no = detail.risk_record_no
GROUP BY quarter_suffix
ORDER BY quarter_suffix;
