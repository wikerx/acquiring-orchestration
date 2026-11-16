-- 未结算历史标签本金修复通道结构回滚。
-- 仅允许从未产生审批事实的环境删除空表；一旦存在记录，历史审计事实必须保留并走前向修复。

DELIMITER $$

DROP PROCEDURE IF EXISTS rollback_empty_label_principal_repair_tables$$
CREATE PROCEDURE rollback_empty_label_principal_repair_tables()
BEGIN
    DECLARE repair_table_exists_count INT DEFAULT 0;
    DECLARE dependency_audit_table_exists_count INT DEFAULT 0;
    DECLARE reserve_state_audit_table_exists_count INT DEFAULT 0;
    DECLARE repair_row_count BIGINT DEFAULT 0;
    DECLARE dependency_audit_row_count BIGINT DEFAULT 0;
    DECLARE reserve_state_audit_row_count BIGINT DEFAULT 0;

    SELECT COUNT(*)
      INTO repair_table_exists_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'clearing_label_principal_repair';

    SELECT COUNT(*)
      INTO dependency_audit_table_exists_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'clearing_label_principal_repair_dependency_audit';

    SELECT COUNT(*)
      INTO reserve_state_audit_table_exists_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'clearing_label_principal_repair_reserve_state_audit';

    IF repair_table_exists_count = 1 THEN
        SELECT COUNT(*) INTO repair_row_count
        FROM clearing_label_principal_repair;
    END IF;

    IF dependency_audit_table_exists_count = 1 THEN
        SELECT COUNT(*) INTO dependency_audit_row_count
        FROM clearing_label_principal_repair_dependency_audit;
    END IF;

    IF reserve_state_audit_table_exists_count = 1 THEN
        SELECT COUNT(*) INTO reserve_state_audit_row_count
        FROM clearing_label_principal_repair_reserve_state_audit;
    END IF;

    IF repair_row_count > 0 OR dependency_audit_row_count > 0
        OR reserve_state_audit_row_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'label principal repair tables contain audit facts; use a reviewed forward-fix';
    END IF;

    IF dependency_audit_table_exists_count = 1 THEN
        DROP TABLE clearing_label_principal_repair_dependency_audit;
    END IF;

    IF reserve_state_audit_table_exists_count = 1 THEN
        DROP TABLE clearing_label_principal_repair_reserve_state_audit;
    END IF;

    IF repair_table_exists_count = 1 THEN
        DROP TABLE clearing_label_principal_repair;
    END IF;
END$$

CALL rollback_empty_label_principal_repair_tables()$$
DROP PROCEDURE rollback_empty_label_principal_repair_tables$$

DELIMITER ;
