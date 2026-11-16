-- 统一待办真实数据库分页与处理历史分页：迁移后检查。
SET NAMES utf8mb4;

SELECT source_projection_version, COUNT(*) AS row_count
FROM admin_operational_work_item
GROUP BY source_projection_version
ORDER BY source_projection_version;

SELECT source_type, COUNT(*) AS missing_merchant_projection_count
FROM admin_operational_work_item
WHERE source_type <> 'EXCHANGE_RATE_ALERT'
  AND (source_projection_version <> 1 OR merchant_id IS NULL OR merchant_id = '')
GROUP BY source_type
ORDER BY source_type;

SELECT source_type, source_key, COUNT(*) AS duplicate_count
FROM admin_operational_work_item
GROUP BY source_type, source_key
HAVING COUNT(*) > 1;

SELECT table_name, index_name,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND (
      (table_name = 'admin_operational_work_item'
       AND index_name IN (
           'idx_admin_operational_work_item_projection_queue',
           'idx_admin_operational_work_item_projection_merchant'))
      OR (table_name = 'admin_operational_work_item_history'
          AND index_name = 'idx_admin_operational_work_item_history_page')
  )
GROUP BY table_name, index_name
ORDER BY table_name, index_name;

DROP PROCEDURE IF EXISTS admin_owi_assert_pagination_migration;
DELIMITER $$
CREATE PROCEDURE admin_owi_assert_pagination_migration()
BEGIN
    DECLARE invalid_projection_count BIGINT DEFAULT 0;
    DECLARE missing_merchant_count BIGINT DEFAULT 0;
    DECLARE duplicate_source_count BIGINT DEFAULT 0;
    DECLARE missing_index_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO invalid_projection_count
    FROM admin_operational_work_item
    WHERE source_projection_version <> 1;

    SELECT COUNT(*) INTO missing_merchant_count
    FROM admin_operational_work_item
    WHERE source_type <> 'EXCHANGE_RATE_ALERT'
      AND (merchant_id IS NULL OR merchant_id = '');

    SELECT COUNT(*) INTO duplicate_source_count
    FROM (
        SELECT source_type, source_key
        FROM admin_operational_work_item
        GROUP BY source_type, source_key
        HAVING COUNT(*) > 1
    ) duplicate_source;

    SELECT 3 - COUNT(DISTINCT CONCAT(table_name, ':', index_name)) INTO missing_index_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND (
          (table_name = 'admin_operational_work_item'
           AND index_name IN (
               'idx_admin_operational_work_item_projection_queue',
               'idx_admin_operational_work_item_projection_merchant'))
          OR (table_name = 'admin_operational_work_item_history'
              AND index_name = 'idx_admin_operational_work_item_history_page')
      );

    IF invalid_projection_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'operational work item projection backfill is incomplete';
    END IF;
    IF missing_merchant_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'merchant-owned work item is missing merchant projection';
    END IF;
    IF duplicate_source_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'duplicate operational work item source identity found';
    END IF;
    IF missing_index_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'operational work item pagination index is missing';
    END IF;
END$$
DELIMITER ;

CALL admin_owi_assert_pagination_migration();
DROP PROCEDURE admin_owi_assert_pagination_migration;

SELECT 'PASS' AS migration_result;
