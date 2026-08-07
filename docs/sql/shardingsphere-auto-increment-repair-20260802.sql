-- ShardingSphere 季度号段修复草案
-- 版本：2026.08.02-001
-- 状态：dev 已受控执行；其他环境和重复执行仍须独立审批
--
-- 目标：修复以下两个既有物理表没有应用 yyyyQQ + 12 位序列起点的问题。
-- transaction_merchant_api_interaction_log_202603 -> 202603000000000001
-- transaction_merchant_api_interaction_log_202604 -> 202604000000000001
--
-- 本文件不修改数据、不连接数据库，也不更新 Nacos。ALTER 语句保持注释状态。

-- -----------------------------------------------------------------------------
-- 1. 执行前只读复核
-- -----------------------------------------------------------------------------

SELECT DATABASE() AS current_schema;

-- MySQL 8.4 默认会缓存 information_schema 表统计；本会话强制读取实时 AUTO_INCREMENT。
SET SESSION information_schema_stats_expiry = 0;

SELECT TABLE_NAME, TABLE_ROWS, AUTO_INCREMENT, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
    'transaction_merchant_api_interaction_log_202603',
    'transaction_merchant_api_interaction_log_202604'
  )
ORDER BY TABLE_NAME;

SELECT 'transaction_merchant_api_interaction_log_202603' AS table_name,
       COUNT(*) AS row_count,
       MIN(id) AS min_id,
       MAX(id) AS max_id
FROM transaction_merchant_api_interaction_log_202603
UNION ALL
SELECT 'transaction_merchant_api_interaction_log_202604',
       COUNT(*), MIN(id), MAX(id)
FROM transaction_merchant_api_interaction_log_202604;

-- 必须确认 MAX(id) 为空或小于对应目标起点；否则停止并单独设计数据修复。
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_KEY, EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
    'transaction_merchant_api_interaction_log_202603',
    'transaction_merchant_api_interaction_log_202604'
  )
  AND COLUMN_NAME IN ('id', 'transaction_date_time')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

SHOW CREATE TABLE transaction_merchant_api_interaction_log_202603;
SHOW CREATE TABLE transaction_merchant_api_interaction_log_202604;

SELECT logical_table, physical_table, auto_increment_start,
       auto_increment_current, auto_increment_max, schema_check_status
FROM sys_sharding_physical_table
WHERE physical_table IN (
  'transaction_merchant_api_interaction_log_202603',
  'transaction_merchant_api_interaction_log_202604'
)
ORDER BY physical_table;

-- -----------------------------------------------------------------------------
-- 2. 人工审批后的 ALTER，默认不可执行
-- -----------------------------------------------------------------------------

-- 前置人工门禁：
-- [ ] 两张表都存在且 id 为 BIGINT AUTO_INCREMENT PRIMARY KEY。
-- [ ] transaction_date_time 为 DATETIME(3)，表字符集为 utf8mb4。
-- [ ] MAX(id) 小于目标起点，且不存在依赖旧小号段的外部系统。
-- [ ] 已记录当前 AUTO_INCREMENT、MAX(id) 和 SHOW CREATE TABLE。
-- [ ] 已安排短暂写入隔离窗口，避免检查与 ALTER 之间发生并发插入。
-- [ ] DBA、业务负责人和发布负责人已批准独立变更单。

-- ALTER TABLE transaction_merchant_api_interaction_log_202603
--   AUTO_INCREMENT = 202603000000000001;

-- ALTER TABLE transaction_merchant_api_interaction_log_202604
--   AUTO_INCREMENT = 202604000000000001;

-- 不直接 UPDATE sys_sharding_physical_table。ALTER 后由 SHARDING_TABLE_PRE_CREATE
-- 的 schema/号段检查刷新治理记录，再生成候选 rule-version/checksum。

-- -----------------------------------------------------------------------------
-- 3. 执行后只读核验
-- -----------------------------------------------------------------------------

SELECT TABLE_NAME, AUTO_INCREMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
    'transaction_merchant_api_interaction_log_202603',
    'transaction_merchant_api_interaction_log_202604'
  )
ORDER BY TABLE_NAME;

-- 预期：Job Dry Run/检查结果中 schema、DATETIME(3)、字符集和号段均通过，
-- 两张表对应季度才允许继续留在 actualDataNodes 候选中。
SELECT logical_table, physical_table, auto_increment_start,
       auto_increment_current, auto_increment_max, schema_check_status,
       last_check_time, error_message
FROM sys_sharding_physical_table
WHERE physical_table IN (
  'transaction_merchant_api_interaction_log_202603',
  'transaction_merchant_api_interaction_log_202604'
)
ORDER BY physical_table;

-- 回滚说明：AUTO_INCREMENT 变更不改写已有行。若尚未生成新 ID 且必须恢复，
-- DBA 可依据审批附件中的原始值生成独立 ALTER 草案；若已生成新季度号段 ID，
-- 禁止降低 AUTO_INCREMENT，也不得改写已提交交易事实。

-- 2026-08-02 dev 执行记录：
-- 1. 两张表的结构、字符集、DATETIME(3)、MAX(id)、治理记录和元数据锁前置门禁全部通过。
-- 2. 两条 ALTER 均执行成功，既有行 MAX(id) 未变化。
-- 3. MySQL 8.4 information_schema 默认统计缓存曾返回旧值；实时 SHOW CREATE TABLE 与
--    information_schema_stats_expiry=0 查询均确认目标号段生效。
-- 4. 修复检查器实时元数据读取后，真实 Job Dry Run 46/46 通过，未执行建表或其他 DDL。
