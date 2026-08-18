-- 为交易生命周期主单增加商户网站原文，用于来源网址限定后的创建和查询响应回显。
-- 执行前必须备份 transaction_order 模板表及全部季度物理分表；DDL 自动提交。
-- 脚本只处理严格匹配 transaction_order 或 transaction_order_YYYYMM 的现有表；
-- 已有字段会修复历史乱码注释，缺失字段会按标准定义补齐，可重复执行。

DROP PROCEDURE IF EXISTS migrate_transaction_order_merchant_website;

DELIMITER //
CREATE PROCEDURE migrate_transaction_order_merchant_website()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE current_table VARCHAR(64);
    DECLARE merchant_website_exists INT DEFAULT 0;
    DECLARE table_cursor CURSOR FOR
        SELECT table_name
        FROM information_schema.tables table_info
        WHERE table_info.table_schema = DATABASE()
          AND table_info.table_type = 'BASE TABLE'
          AND table_info.table_name REGEXP '^transaction_order(_[0-9]{6})?$'
        ORDER BY table_name;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN table_cursor;
    migration_loop: LOOP
        FETCH table_cursor INTO current_table;
        IF done = 1 THEN
            LEAVE migration_loop;
        END IF;

        SELECT COUNT(*)
        INTO merchant_website_exists
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = current_table
          AND column_name = 'merchant_website';

        IF merchant_website_exists = 0 THEN
            SET @merchant_website_ddl = CONCAT(
                'ALTER TABLE `', current_table,
                '` ADD COLUMN `merchant_website` VARCHAR(512) NULL ',
                'COMMENT ''首次支付、授权或预授权请求中的商户网站原始URL，用于来源网址限定和查询回显'' ',
                'AFTER `internal_risk_record_no`'
            );
        ELSE
            SET @merchant_website_ddl = CONCAT(
                'ALTER TABLE `', current_table,
                '` MODIFY COLUMN `merchant_website` VARCHAR(512) NULL ',
                'COMMENT ''首次支付、授权或预授权请求中的商户网站原始URL，用于来源网址限定和查询回显'''
            );
        END IF;
        PREPARE merchant_website_stmt FROM @merchant_website_ddl;
        EXECUTE merchant_website_stmt;
        DEALLOCATE PREPARE merchant_website_stmt;
    END LOOP;
    CLOSE table_cursor;
END//
DELIMITER ;

CALL migrate_transaction_order_merchant_website();
DROP PROCEDURE IF EXISTS migrate_transaction_order_merchant_website;
