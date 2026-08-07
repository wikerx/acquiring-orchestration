-- ShardingSphere 测试分表退役草案
-- 版本：2026.08.02-001
-- 状态：仅供 DBA 评审，禁止直接执行破坏性语句
--
-- 安全边界：
-- 1. 本脚本中的只读复核查询可以独立执行。
-- 2. DROP TABLE 和 DELETE 全部保持注释状态；仅在数据库负责人重新核验、
--    变更单批准且 Nacos 已移除四条 test_* 规则后，由 DBA 手工解注释。
-- 3. dev 的空表结论不得复用于 test、uat 或 prod。
-- 4. DDL 会隐式提交，不能依赖 ROLLBACK 恢复；执行前必须保存 SHOW CREATE TABLE。

-- -----------------------------------------------------------------------------
-- 1. 执行前只读复核
-- -----------------------------------------------------------------------------

SELECT DATABASE() AS current_schema;

SELECT TABLE_NAME, TABLE_TYPE, ENGINE, TABLE_ROWS, AUTO_INCREMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
    'test_transaction',
    'test_transaction_202603',
    'test_transaction_202604',
    'test_transaction_info',
    'test_transaction_info_202603',
    'test_transaction_info_202604',
    'test_transaction_merge_info',
    'test_transaction_merge_info_202603',
    'test_transaction_merge_info_202604',
    'test_transaction_status_info',
    'test_transaction_status_info_202603',
    'test_transaction_status_info_202604'
  )
ORDER BY TABLE_NAME;

-- 预期返回 12 行，且全部为 BASE TABLE。
SELECT COUNT(*) AS matched_test_table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_TYPE = 'BASE TABLE'
  AND TABLE_NAME IN (
    'test_transaction',
    'test_transaction_202603',
    'test_transaction_202604',
    'test_transaction_info',
    'test_transaction_info_202603',
    'test_transaction_info_202604',
    'test_transaction_merge_info',
    'test_transaction_merge_info_202603',
    'test_transaction_merge_info_202604',
    'test_transaction_status_info',
    'test_transaction_status_info_202603',
    'test_transaction_status_info_202604'
  );

-- 每个结果都必须为 0。任一表非空时立即停止，不得执行后续清理。
SELECT 'test_transaction' AS table_name, COUNT(*) AS row_count FROM test_transaction
UNION ALL SELECT 'test_transaction_202603', COUNT(*) FROM test_transaction_202603
UNION ALL SELECT 'test_transaction_202604', COUNT(*) FROM test_transaction_202604
UNION ALL SELECT 'test_transaction_info', COUNT(*) FROM test_transaction_info
UNION ALL SELECT 'test_transaction_info_202603', COUNT(*) FROM test_transaction_info_202603
UNION ALL SELECT 'test_transaction_info_202604', COUNT(*) FROM test_transaction_info_202604
UNION ALL SELECT 'test_transaction_merge_info', COUNT(*) FROM test_transaction_merge_info
UNION ALL SELECT 'test_transaction_merge_info_202603', COUNT(*) FROM test_transaction_merge_info_202603
UNION ALL SELECT 'test_transaction_merge_info_202604', COUNT(*) FROM test_transaction_merge_info_202604
UNION ALL SELECT 'test_transaction_status_info', COUNT(*) FROM test_transaction_status_info
UNION ALL SELECT 'test_transaction_status_info_202603', COUNT(*) FROM test_transaction_status_info_202603
UNION ALL SELECT 'test_transaction_status_info_202604', COUNT(*) FROM test_transaction_status_info_202604;

-- 预期 12 条治理记录；保存查询结果作为审批附件。
SELECT *
FROM sys_sharding_physical_table
WHERE logical_table IN (
  'test_transaction',
  'test_transaction_info',
  'test_transaction_merge_info',
  'test_transaction_status_info'
)
ORDER BY logical_table, year, quarter;

-- 外键、视图、存储过程、触发器或事件存在任何引用时立即停止。
SELECT CONSTRAINT_SCHEMA, TABLE_NAME, CONSTRAINT_NAME,
       REFERENCED_TABLE_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE REFERENCED_TABLE_SCHEMA = DATABASE()
  AND REFERENCED_TABLE_NAME REGEXP '^test_transaction(_info|_merge_info|_status_info)?(_[0-9]{6})?$';

SELECT TABLE_NAME AS view_name
FROM information_schema.VIEWS
WHERE TABLE_SCHEMA = DATABASE()
  AND VIEW_DEFINITION REGEXP 'test_transaction(_info|_merge_info|_status_info)?(_[0-9]{6})?';

SELECT ROUTINE_NAME, ROUTINE_TYPE
FROM information_schema.ROUTINES
WHERE ROUTINE_SCHEMA = DATABASE()
  AND ROUTINE_DEFINITION REGEXP 'test_transaction(_info|_merge_info|_status_info)?(_[0-9]{6})?';

SELECT TRIGGER_NAME, EVENT_OBJECT_TABLE
FROM information_schema.TRIGGERS
WHERE TRIGGER_SCHEMA = DATABASE()
  AND ACTION_STATEMENT REGEXP 'test_transaction(_info|_merge_info|_status_info)?(_[0-9]{6})?';

SELECT EVENT_NAME
FROM information_schema.EVENTS
WHERE EVENT_SCHEMA = DATABASE()
  AND EVENT_DEFINITION REGEXP 'test_transaction(_info|_merge_info|_status_info)?(_[0-9]{6})?';

-- 执行前将以下输出保存到审批附件。
SHOW CREATE TABLE test_transaction;
SHOW CREATE TABLE test_transaction_202603;
SHOW CREATE TABLE test_transaction_202604;
SHOW CREATE TABLE test_transaction_info;
SHOW CREATE TABLE test_transaction_info_202603;
SHOW CREATE TABLE test_transaction_info_202604;
SHOW CREATE TABLE test_transaction_merge_info;
SHOW CREATE TABLE test_transaction_merge_info_202603;
SHOW CREATE TABLE test_transaction_merge_info_202604;
SHOW CREATE TABLE test_transaction_status_info;
SHOW CREATE TABLE test_transaction_status_info_202603;
SHOW CREATE TABLE test_transaction_status_info_202604;

-- -----------------------------------------------------------------------------
-- 2. 人工审批后的破坏性步骤，默认不可执行
-- -----------------------------------------------------------------------------

-- 前置人工门禁：
-- [ ] 全仓正式代码不再引用四个 test_* 逻辑表。
-- [ ] 目标环境 12 张表逐表 COUNT(*) = 0。
-- [ ] 外键、视图、存储过程、触发器、事件和报表引用均为 0。
-- [ ] 新 ShardingSphere 规则精确包含 23 张正式表且不含 test_*。
-- [ ] 五个直连服务已加载同一 rule-version/checksum。
-- [ ] SHOW CREATE TABLE 和 12 条治理记录已归档。
-- [ ] DBA、业务负责人和发布负责人已批准同一变更单。

-- 先删除物理表，再删除模板表，最后删除治理登记。
-- DROP TABLE test_transaction_202604;
-- DROP TABLE test_transaction_202603;
-- DROP TABLE test_transaction;
-- DROP TABLE test_transaction_info_202604;
-- DROP TABLE test_transaction_info_202603;
-- DROP TABLE test_transaction_info;
-- DROP TABLE test_transaction_merge_info_202604;
-- DROP TABLE test_transaction_merge_info_202603;
-- DROP TABLE test_transaction_merge_info;
-- DROP TABLE test_transaction_status_info_202604;
-- DROP TABLE test_transaction_status_info_202603;
-- DROP TABLE test_transaction_status_info;

-- DELETE FROM sys_sharding_physical_table
-- WHERE logical_table IN (
--   'test_transaction',
--   'test_transaction_info',
--   'test_transaction_merge_info',
--   'test_transaction_status_info'
-- );

-- -----------------------------------------------------------------------------
-- 3. 执行后只读核验
-- -----------------------------------------------------------------------------

SELECT COUNT(*) AS remaining_test_table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME REGEXP '^test_transaction(_info|_merge_info|_status_info)?(_[0-9]{6})?$';

SELECT COUNT(*) AS remaining_test_governance_count
FROM sys_sharding_physical_table
WHERE logical_table IN (
  'test_transaction',
  'test_transaction_info',
  'test_transaction_merge_info',
  'test_transaction_status_info'
);

-- 正式治理规则必须仍为 23 个逻辑表；物理登记数量按已建季度决定。
SELECT COUNT(DISTINCT logical_table) AS formal_logical_table_count
FROM sys_sharding_physical_table
WHERE logical_table NOT LIKE 'test\_%';

-- 回滚说明：DROP 无法事务回滚。仅在误删且尚未产生依赖时，使用审批附件中的
-- SHOW CREATE TABLE 重建空表，再由 Job 治理刷新登记；不得把 test_* 重新加入 Nacos 正式规则。
