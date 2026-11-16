-- 渠道 Logo 兼容升级。先核对当前库，再执行；不包含凭证，不自动连接任何数据库。
-- 前置检查：确认 channel_info 与 sys_user 已存在，sys_user.avatar_url 长度至少 500。
SELECT DATABASE() AS current_database;
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND ((TABLE_NAME = 'channel_info' AND COLUMN_NAME = 'logo_object_key')
 OR (TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'avatar_url'));
SET @channel_logo_ddl = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'channel_info' AND COLUMN_NAME = 'logo_object_key') = 0,
 'ALTER TABLE channel_info ADD COLUMN logo_object_key VARCHAR(500) NULL COMMENT ''通过安全扫描的私有渠道Logo对象引用''',
 'SELECT ''logo_object_key already exists'' AS migration_result');
PREPARE channel_logo_statement FROM @channel_logo_ddl;
EXECUTE channel_logo_statement;
DEALLOCATE PREPARE channel_logo_statement;
-- 后置检查：字段应存在、可空；旧数据不需回填，未设置 Logo 时显示占位。
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_info' AND COLUMN_NAME = 'logo_object_key';
-- 回退：先回退应用并禁用上传入口，保留新增可空列和私有图片，不自动 DROP 或删除正文。
