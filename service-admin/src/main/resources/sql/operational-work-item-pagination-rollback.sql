-- 统一待办真实数据库分页与处理历史分页：仅回滚本批检索投影和索引。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS admin_owi_drop_index;
DELIMITER $$
CREATE PROCEDURE admin_owi_drop_index(IN p_table_name VARCHAR(64), IN p_index_name VARCHAR(128))
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` DROP INDEX `', p_index_name, '`');
        PREPARE statement FROM @ddl;
        EXECUTE statement;
        DEALLOCATE PREPARE statement;
    END IF;
END$$
DELIMITER ;

CALL admin_owi_drop_index('admin_operational_work_item_history',
                          'idx_admin_operational_work_item_history_page');
CALL admin_owi_drop_index('admin_operational_work_item',
                          'idx_admin_operational_work_item_projection_merchant');
CALL admin_owi_drop_index('admin_operational_work_item',
                          'idx_admin_operational_work_item_projection_queue');
DROP PROCEDURE admin_owi_drop_index;

DROP PROCEDURE IF EXISTS admin_owi_drop_column;
DELIMITER $$
CREATE PROCEDURE admin_owi_drop_column(IN p_column_name VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'admin_operational_work_item'
          AND column_name = p_column_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE admin_operational_work_item DROP COLUMN `', p_column_name, '`');
        PREPARE statement FROM @ddl;
        EXECUTE statement;
        DEALLOCATE PREPARE statement;
    END IF;
END$$
DELIMITER ;

CALL admin_owi_drop_column('source_occurred_at');
CALL admin_owi_drop_column('transaction_date_time');
CALL admin_owi_drop_column('transaction_id');
CALL admin_owi_drop_column('related_business_no');
CALL admin_owi_drop_column('business_no');
CALL admin_owi_drop_column('merchant_name');
CALL admin_owi_drop_column('merchant_id');
CALL admin_owi_drop_column('source_projection_version');
DROP PROCEDURE admin_owi_drop_column;
