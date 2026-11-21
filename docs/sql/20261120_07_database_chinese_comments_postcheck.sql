-- 必须连接实际目标库；不是只检查迁移文件。以下异常明细查询均应返回零行。
SET NAMES utf8mb4;

SELECT TABLE_SCHEMA AS database_name, COUNT(*) AS table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'payment_acquiring' AND TABLE_TYPE = 'BASE TABLE'
GROUP BY TABLE_SCHEMA;

SELECT TABLE_SCHEMA AS database_name, COUNT(*) AS column_count
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'payment_acquiring'
GROUP BY TABLE_SCHEMA;

SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'payment_acquiring' AND TABLE_TYPE = 'BASE TABLE'
  AND (TRIM(TABLE_COMMENT) = '' OR TABLE_COMMENT NOT REGEXP '[一-龥]'
       OR TABLE_COMMENT REGEXP '[ÃÂâãäåæçèé�]|锟斤拷|烫烫烫'
       OR TABLE_NAME NOT REGEXP '^[a-z_][a-z0-9_]*$');

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'payment_acquiring'
  AND (TRIM(COLUMN_COMMENT) = '' OR COLUMN_COMMENT NOT REGEXP '[一-龥]'
       OR COLUMN_COMMENT REGEXP '[ÃÂâãäåæçèé�]|锟斤拷|烫烫烫'
       OR COLUMN_NAME NOT REGEXP '^[a-z_][a-z0-9_]*$');

-- 完整乱码和占位注释规则使用同一 Python 门禁；连接失败、目标库为空不能通过：
-- python3 scripts/audit-database-comments.py --container MySQL8 --schema payment_acquiring
-- 实施证据：全库 364 表、8628 字段；19 个表注释、601 个字段注释修复，2026-11-20 后检零异常。
-- 非注释定义、索引和检查约束逐项与修复前一致；21 张复制表执行前后行数相同。
