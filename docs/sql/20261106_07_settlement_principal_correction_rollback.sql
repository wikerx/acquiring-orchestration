-- 已入账历史标签本金差额调整通道结构回滚。
-- 仅允许删除从未产生调整事实的空表；一旦有记录，必须停止入口并使用受审的正向补偿。

DELIMITER $$

DROP PROCEDURE IF EXISTS rollback_empty_settlement_principal_correction_tables$$
CREATE PROCEDURE rollback_empty_settlement_principal_correction_tables()
BEGIN
    DECLARE order_table_exists_count INT DEFAULT 0;
    DECLARE item_table_exists_count INT DEFAULT 0;
    DECLARE order_row_count BIGINT DEFAULT 0;
    DECLARE item_row_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO order_table_exists_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'settlement_principal_correction_order';

    SELECT COUNT(*) INTO item_table_exists_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'settlement_principal_correction_item';

    IF order_table_exists_count = 1 THEN
        SELECT COUNT(*) INTO order_row_count
        FROM settlement_principal_correction_order;
    END IF;

    IF item_table_exists_count = 1 THEN
        SELECT COUNT(*) INTO item_row_count
        FROM settlement_principal_correction_item;
    END IF;

    IF order_row_count > 0 OR item_row_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'principal correction tables contain financial audit facts; use a reviewed forward compensation';
    END IF;

    IF item_table_exists_count = 1 THEN
        DROP TABLE settlement_principal_correction_item;
    END IF;

    IF order_table_exists_count = 1 THEN
        DROP TABLE settlement_principal_correction_order;
    END IF;
END$$

CALL rollback_empty_settlement_principal_correction_tables()$$
DROP PROCEDURE rollback_empty_settlement_principal_correction_tables$$

DELIMITER ;
