-- 交易动作可信接入 IP 审计字段迁移草案（MySQL 8.4，可重复执行）。
-- 仅供评审，未由 Codex 执行。执行前必须完成备份、DDL 窗口、分表清单和回滚方案评审。
-- 本脚本只修改 transaction_operation 逻辑表及数据库中已存在的 YYYYMM 月分表，不写入业务数据。

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `payment_acquiring`;

DROP PROCEDURE IF EXISTS `migrate_20261017_03_transaction_ingress_ip`;
DELIMITER $$
CREATE PROCEDURE `migrate_20261017_03_transaction_ingress_ip`()
BEGIN
  DECLARE finished integer DEFAULT 0;
  DECLARE operation_table varchar(64);
  DECLARE operation_tables CURSOR FOR
    SELECT `table_name`
    FROM `information_schema`.`tables`
    WHERE `table_schema` = DATABASE()
      AND (`table_name` = 'transaction_operation'
           OR `table_name` REGEXP '^transaction_operation_[0-9]{6}$')
    ORDER BY `table_name`;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;

  OPEN operation_tables;
  migrate_tables: LOOP
    FETCH operation_tables INTO operation_table;
    IF finished = 1 THEN
      LEAVE migrate_tables;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE()
          AND `table_name` = operation_table
          AND `column_name` = 'ingress_client_ip'
    ) THEN
      SET @add_ingress_ip_sql = CONCAT(
          'ALTER TABLE `', REPLACE(operation_table, '`', '``'),
          '` ADD COLUMN `ingress_client_ip` varchar(45) DEFAULT NULL ',
          'COMMENT ''网关认证并规范化的接入客户端IP，仅用于受控安全审计。'' AFTER `request_source`');
      PREPARE add_ingress_ip_statement FROM @add_ingress_ip_sql;
      EXECUTE add_ingress_ip_statement;
      DEALLOCATE PREPARE add_ingress_ip_statement;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE()
          AND `table_name` = operation_table
          AND `column_name` = 'ingress_client_ip_hash'
    ) THEN
      SET @add_ingress_ip_hash_sql = CONCAT(
          'ALTER TABLE `', REPLACE(operation_table, '`', '``'),
          '` ADD COLUMN `ingress_client_ip_hash` char(64) DEFAULT NULL ',
          'COMMENT ''接入客户端IP的SHA-256摘要，用于关联检索。'' AFTER `ingress_client_ip`');
      PREPARE add_ingress_ip_hash_statement FROM @add_ingress_ip_hash_sql;
      EXECUTE add_ingress_ip_hash_statement;
      DEALLOCATE PREPARE add_ingress_ip_hash_statement;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM `information_schema`.`statistics`
        WHERE `table_schema` = DATABASE()
          AND `table_name` = operation_table
          AND `index_name` = 'idx_transaction_operation_ingress_ip_hash'
    ) THEN
      SET @add_ingress_ip_index_sql = CONCAT(
          'ALTER TABLE `', REPLACE(operation_table, '`', '``'),
          '` ADD INDEX `idx_transaction_operation_ingress_ip_hash` ',
          '(`ingress_client_ip_hash`, `transaction_date_time`)');
      PREPARE add_ingress_ip_index_statement FROM @add_ingress_ip_index_sql;
      EXECUTE add_ingress_ip_index_statement;
      DEALLOCATE PREPARE add_ingress_ip_index_statement;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE()
          AND `table_name` = operation_table
          AND ((`column_name` = 'ingress_client_ip'
                AND `column_comment` <> '网关认证并规范化的接入客户端IP，仅用于受控安全审计。')
            OR (`column_name` = 'ingress_client_ip_hash'
                AND `column_comment` <> '接入客户端IP的SHA-256摘要，用于关联检索。'))
    ) THEN
      SET @repair_ingress_ip_comments_sql = CONCAT(
          'ALTER TABLE `', REPLACE(operation_table, '`', '``'), '` ALGORITHM=INSTANT, ',
          'MODIFY COLUMN `ingress_client_ip` varchar(45) DEFAULT NULL ',
          'COMMENT ''网关认证并规范化的接入客户端IP，仅用于受控安全审计。'', ',
          'MODIFY COLUMN `ingress_client_ip_hash` char(64) DEFAULT NULL ',
          'COMMENT ''接入客户端IP的SHA-256摘要，用于关联检索。''');
      PREPARE repair_ingress_ip_comments_statement FROM @repair_ingress_ip_comments_sql;
      EXECUTE repair_ingress_ip_comments_statement;
      DEALLOCATE PREPARE repair_ingress_ip_comments_statement;
    END IF;
  END LOOP;
  CLOSE operation_tables;
END$$
DELIMITER ;

CALL `migrate_20261017_03_transaction_ingress_ip`();
DROP PROCEDURE IF EXISTS `migrate_20261017_03_transaction_ingress_ip`;

-- 后检：结果必须为空；任何一行都表示逻辑表或现有月分表尚未完成迁移。
SELECT `t`.`table_name`
FROM `information_schema`.`tables` `t`
WHERE `t`.`table_schema` = DATABASE()
  AND (`t`.`table_name` = 'transaction_operation'
       OR `t`.`table_name` REGEXP '^transaction_operation_[0-9]{6}$')
  AND (
       NOT EXISTS (
           SELECT 1 FROM `information_schema`.`columns` `c`
           WHERE `c`.`table_schema` = DATABASE()
             AND `c`.`table_name` = `t`.`table_name`
             AND `c`.`column_name` = 'ingress_client_ip'
       )
       OR NOT EXISTS (
           SELECT 1 FROM `information_schema`.`columns` `c`
           WHERE `c`.`table_schema` = DATABASE()
             AND `c`.`table_name` = `t`.`table_name`
             AND `c`.`column_name` = 'ingress_client_ip_hash'
       )
       OR NOT EXISTS (
           SELECT 1 FROM `information_schema`.`statistics` `s`
           WHERE `s`.`table_schema` = DATABASE()
             AND `s`.`table_name` = `t`.`table_name`
             AND `s`.`index_name` = 'idx_transaction_operation_ingress_ip_hash'
       )
       OR EXISTS (
           SELECT 1 FROM `information_schema`.`columns` `c`
           WHERE `c`.`table_schema` = DATABASE()
             AND `c`.`table_name` = `t`.`table_name`
             AND ((`c`.`column_name` = 'ingress_client_ip'
                   AND `c`.`column_comment` <> '网关认证并规范化的接入客户端IP，仅用于受控安全审计。')
               OR (`c`.`column_name` = 'ingress_client_ip_hash'
                   AND `c`.`column_comment` <> '接入客户端IP的SHA-256摘要，用于关联检索。'))
       )
      );
