-- 渠道 MID 支付方式/卡品牌字段迁移脚本。
-- 用于已有数据库补齐 card_brand_scope，支持重复执行。

SET @schema_name = DATABASE();

SET @table_exists = (
    SELECT COUNT(1)
    FROM information_schema.tables
    WHERE table_schema = @schema_name
      AND table_name = 'channel_mid_config'
);

SET @column_exists = (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'channel_mid_config'
      AND column_name = 'card_brand_scope'
);

SET @ddl = IF(@table_exists > 0 AND @column_exists = 0,
    'ALTER TABLE channel_mid_config ADD COLUMN card_brand_scope VARCHAR(512) NOT NULL DEFAULT ''NONE'' COMMENT ''银行卡品牌范围，非银行卡为NONE，银行卡为ALL或逗号分隔'' AFTER payment_method_scope',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @cleanup_sql = IF(@table_exists > 0,
    'UPDATE channel_mid_config SET card_brand_scope = ''NONE'' WHERE (card_brand_scope IS NULL OR card_brand_scope = '''') AND deleted = 0',
    'SELECT 1');
PREPARE stmt FROM @cleanup_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
