-- Admin 清分与结算标准分页查询索引迁移。
-- 依据 2026-08-27 本地开发库 EXPLAIN：清分列表为 ALL + Using filesort，结算列表为 Using filesort。
-- 本脚本仅添加二级查询索引，不修改金额、币种、费用、保证金、状态或幂等字段语义。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS add_admin_query_index_if_absent;

DELIMITER $$
CREATE PROCEDURE add_admin_query_index_if_absent(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_columns VARCHAR(512)
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @admin_query_index_sql = CONCAT(
            'ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
            '` ADD KEY `', REPLACE(p_index_name, '`', '``'),
            '` (', p_columns, '), ALGORITHM=INPLACE, LOCK=NONE'
        );
        PREPARE admin_query_index_statement FROM @admin_query_index_sql;
        EXECUTE admin_query_index_statement;
        DEALLOCATE PREPARE admin_query_index_statement;
    END IF;
END$$
DELIMITER ;

-- 模板表保证后续季度 CREATE TABLE LIKE 自动继承；当前活动季度物理表同步补齐。
CALL add_admin_query_index_if_absent(
    'transaction_finance_state', 'idx_finance_admin_time',
    '`deleted`, `transaction_date_time`, `id`');
CALL add_admin_query_index_if_absent(
    'transaction_finance_state', 'idx_finance_admin_merchant_time',
    '`merchant_id`, `deleted`, `transaction_date_time`, `id`');
CALL add_admin_query_index_if_absent(
    'transaction_finance_state', 'idx_finance_admin_status_time',
    '`clearing_status`, `deleted`, `transaction_date_time`, `id`');

CALL add_admin_query_index_if_absent(
    'transaction_finance_state_202603', 'idx_finance_admin_time',
    '`deleted`, `transaction_date_time`, `id`');
CALL add_admin_query_index_if_absent(
    'transaction_finance_state_202603', 'idx_finance_admin_merchant_time',
    '`merchant_id`, `deleted`, `transaction_date_time`, `id`');
CALL add_admin_query_index_if_absent(
    'transaction_finance_state_202603', 'idx_finance_admin_status_time',
    '`clearing_status`, `deleted`, `transaction_date_time`, `id`');

CALL add_admin_query_index_if_absent(
    'transaction_finance_state_202604', 'idx_finance_admin_time',
    '`deleted`, `transaction_date_time`, `id`');
CALL add_admin_query_index_if_absent(
    'transaction_finance_state_202604', 'idx_finance_admin_merchant_time',
    '`merchant_id`, `deleted`, `transaction_date_time`, `id`');
CALL add_admin_query_index_if_absent(
    'transaction_finance_state_202604', 'idx_finance_admin_status_time',
    '`clearing_status`, `deleted`, `transaction_date_time`, `id`');

CALL add_admin_query_index_if_absent(
    'settlement_batch', 'idx_settlement_admin_date',
    '`business_date`, `id`');
CALL add_admin_query_index_if_absent(
    'settlement_batch', 'idx_settlement_admin_merchant_date',
    '`merchant_id`, `business_date`, `id`');
CALL add_admin_query_index_if_absent(
    'settlement_batch', 'idx_settlement_admin_status_date',
    '`batch_status`, `business_date`, `id`');

DROP PROCEDURE IF EXISTS add_admin_query_index_if_absent;

-- 发布后核验：每条结果的 columns_in_order 必须与脚本定义一致。
SELECT table_name,
       index_name,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_order
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND index_name IN (
      'idx_finance_admin_time',
      'idx_finance_admin_merchant_time',
      'idx_finance_admin_status_time',
      'idx_settlement_admin_date',
      'idx_settlement_admin_merchant_date',
      'idx_settlement_admin_status_date'
  )
GROUP BY table_name, index_name
ORDER BY table_name, index_name;
