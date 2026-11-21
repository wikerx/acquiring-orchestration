-- 支付链接允许 500 字说明，核心交易动作完整保存；仅修改长度，不修改交易、金额、状态或索引。
-- 在目标业务 schema 中执行；DDL 非事务原子，多表部分完成时可幂等重跑。先升级 schema，再启用修复后的 Payment。
-- 保留原字符集、排序规则和列注释；逻辑原型表和所有已存在季度物理表一起处理，未来建表继承原型。
-- 回退优先回退应用并保留宽列；仅所有历史说明均不超过 128 字时才执行对应回滚脚本。
DROP PROCEDURE IF EXISTS migrate_checkout_description_20261118;
DELIMITER $$
CREATE PROCEDURE migrate_checkout_description_20261118()
BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_schema VARCHAR(64);
    DECLARE v_table VARCHAR(64);
    DECLARE v_width INT;
    DECLARE v_charset VARCHAR(64);
    DECLARE v_collation VARCHAR(64);
    DECLARE v_comment TEXT;
    DECLARE tables_to_change CURSOR FOR
        SELECT TABLE_NAME, CHARACTER_MAXIMUM_LENGTH, CHARACTER_SET_NAME, COLLATION_NAME, COLUMN_COMMENT
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'description'
          AND TABLE_NAME REGEXP '^transaction_operation(_[0-9]{6})?$'
        ORDER BY TABLE_NAME;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;
    SET v_schema = DATABASE();
    IF v_schema IS NULL OR NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = v_schema
          AND TABLE_NAME = 'transaction_operation' AND COLUMN_NAME = 'description'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Select the target business schema; operation prototype missing';
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = v_schema AND COLUMN_NAME = 'description'
          AND TABLE_NAME REGEXP '^transaction_operation(_[0-9]{6})?$'
          AND (DATA_TYPE <> 'varchar' OR CHARACTER_MAXIMUM_LENGTH NOT IN (128, 512)
               OR IS_NULLABLE <> 'YES' OR COLUMN_DEFAULT IS NOT NULL)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unexpected operation description definition; review before DDL';
    END IF;

    OPEN tables_to_change;
    alter_loop: LOOP
        FETCH tables_to_change INTO v_table, v_width, v_charset, v_collation, v_comment;
        IF v_done THEN LEAVE alter_loop; END IF;
        IF v_width <> 512 THEN
            IF v_comment IS NULL OR v_comment NOT REGEXP '[一-龥]'
                OR v_comment REGEXP '[ÃÂâãäåæçèé�]' THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '操作描述字段必须先补齐无乱码的中文注释';
            END IF;
            SET @checkout_description_sql = CONCAT('ALTER TABLE `', v_schema, '`.`', v_table,
                '` MODIFY COLUMN `description` VARCHAR(512) CHARACTER SET ', v_charset,
                ' COLLATE ', v_collation, ' NULL DEFAULT NULL COMMENT ', QUOTE(v_comment),
                ', ALGORITHM=INPLACE, LOCK=NONE');
            PREPARE checkout_description_stmt FROM @checkout_description_sql;
            EXECUTE checkout_description_stmt;
            DEALLOCATE PREPARE checkout_description_stmt;
        END IF;
    END LOOP;
    CLOSE tables_to_change;
END$$
DELIMITER ;
CALL migrate_checkout_description_20261118();
DROP PROCEDURE migrate_checkout_description_20261118;
SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, CHARACTER_MAXIMUM_LENGTH AS description_limit
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'description'
  AND TABLE_NAME REGEXP '^transaction_operation(_[0-9]{6})?$'
ORDER BY TABLE_NAME;
