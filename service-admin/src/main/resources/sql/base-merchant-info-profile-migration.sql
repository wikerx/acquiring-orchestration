-- 商户基础资料扩展：账单描述、邮编、联系人姓名。
-- 执行前请先确认 merchant_name 是否存在中文历史数据；新代码保存时会限制 merchant_name 与 billing_descriptor 为英文可打印字符。

SET @add_billing_descriptor_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE base_merchant_info ADD COLUMN billing_descriptor VARCHAR(64) NULL COMMENT ''账单描述，交易账单或渠道侧展示的商户识别名称'' AFTER merchant_name',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'base_merchant_info'
      AND column_name = 'billing_descriptor'
);
PREPARE add_billing_descriptor_stmt FROM @add_billing_descriptor_sql;
EXECUTE add_billing_descriptor_stmt;
DEALLOCATE PREPARE add_billing_descriptor_stmt;

SET @add_postal_code_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE base_merchant_info ADD COLUMN postal_code VARCHAR(32) NULL COMMENT ''商户经营地址邮编'' AFTER address_line',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'base_merchant_info'
      AND column_name = 'postal_code'
);
PREPARE add_postal_code_stmt FROM @add_postal_code_sql;
EXECUTE add_postal_code_stmt;
DEALLOCATE PREPARE add_postal_code_stmt;

SET @add_contact_name_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE base_merchant_info ADD COLUMN contact_name VARCHAR(64) NULL COMMENT ''商户联系人姓名'' AFTER postal_code',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'base_merchant_info'
      AND column_name = 'contact_name'
);
PREPARE add_contact_name_stmt FROM @add_contact_name_sql;
EXECUTE add_contact_name_stmt;
DEALLOCATE PREPARE add_contact_name_stmt;

UPDATE base_merchant_info
SET billing_descriptor = merchant_name
WHERE billing_descriptor IS NULL
  AND merchant_name REGEXP '^[ -~]+$';
