-- 为交易动作模板表及全部已发布季度分表补齐商户交易描述。
USE `payment_acquiring`;

DELIMITER $$
DROP PROCEDURE IF EXISTS `add_transaction_operation_description`$$
CREATE PROCEDURE `add_transaction_operation_description`(IN p_table_name varchar(64))
BEGIN
  IF NOT EXISTS (
      SELECT 1
        FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = p_table_name
         AND COLUMN_NAME = 'description'
  ) THEN
    SET @operation_description_ddl = CONCAT(
      'ALTER TABLE `', p_table_name, '` ',
      'ADD COLUMN `description` varchar(128) DEFAULT NULL ',
      'COMMENT ''商户上送的交易描述快照，用于响应、查询和通知回显。'' ',
      'AFTER `request_source`'
    );
    PREPARE operation_description_stmt FROM @operation_description_ddl;
    EXECUTE operation_description_stmt;
    DEALLOCATE PREPARE operation_description_stmt;
  END IF;
END$$
DELIMITER ;

CALL `add_transaction_operation_description`('transaction_operation');
CALL `add_transaction_operation_description`('transaction_operation_202603');
CALL `add_transaction_operation_description`('transaction_operation_202604');

DROP PROCEDURE `add_transaction_operation_description`;
