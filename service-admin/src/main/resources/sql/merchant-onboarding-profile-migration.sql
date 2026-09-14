-- 管理端商户开户资料扩展。
-- 设计约束：保留 merchant_status=1/2/3 作为运行状态；开户、审核、激活使用独立状态字段。
-- 本脚本不删除历史字段，不创建物理外键，重复执行不会重复增加字段或索引。

DROP PROCEDURE IF EXISTS ensure_merchant_profile_column;
DROP PROCEDURE IF EXISTS ensure_merchant_profile_index;

DELIMITER $$

CREATE PROCEDURE ensure_merchant_profile_column(
    IN target_table VARCHAR(64),
    IN target_column VARCHAR(64),
    IN column_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = target_table
          AND column_name = target_column
    ) THEN
        SET @column_sql = CONCAT('ALTER TABLE `', target_table, '` ADD COLUMN `',
                                 target_column, '` ', column_definition);
        PREPARE column_stmt FROM @column_sql;
        EXECUTE column_stmt;
        DEALLOCATE PREPARE column_stmt;
    END IF;
END$$

CREATE PROCEDURE ensure_merchant_profile_index(
    IN target_table VARCHAR(64),
    IN target_index VARCHAR(64),
    IN index_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = target_table
          AND index_name = target_index
    ) THEN
        SET @index_sql = CONCAT('ALTER TABLE `', target_table, '` ADD ', index_definition);
        PREPARE index_stmt FROM @index_sql;
        EXECUTE index_stmt;
        DEALLOCATE PREPARE index_stmt;
    END IF;
END$$

DELIMITER ;

CALL ensure_merchant_profile_column('base_merchant_info', 'application_no',
    'VARCHAR(40) NULL COMMENT ''开户申请编号'' AFTER `merchant_id`');
CALL ensure_merchant_profile_column('base_merchant_info', 'onboarding_source',
    'VARCHAR(24) NOT NULL DEFAULT ''ADMIN'' COMMENT ''开户来源：ADMIN、SELF_REGISTER、IMPORT'' AFTER `application_no`');
CALL ensure_merchant_profile_column('base_merchant_info', 'onboarding_status',
    'VARCHAR(32) NOT NULL DEFAULT ''DRAFT'' COMMENT ''开户状态'' AFTER `onboarding_source`');
CALL ensure_merchant_profile_column('base_merchant_info', 'review_status',
    'VARCHAR(24) NOT NULL DEFAULT ''NOT_SUBMITTED'' COMMENT ''审核状态'' AFTER `onboarding_status`');
CALL ensure_merchant_profile_column('base_merchant_info', 'activation_status',
    'VARCHAR(24) NOT NULL DEFAULT ''NOT_READY'' COMMENT ''激活状态'' AFTER `review_status`');
CALL ensure_merchant_profile_column('base_merchant_info', 'merchant_type',
    'VARCHAR(32) NULL COMMENT ''商户主体类型'' AFTER `merchant_short_name`');
CALL ensure_merchant_profile_column('base_merchant_info', 'operating_country',
    'CHAR(3) NULL COMMENT ''实际经营国家三字码'' AFTER `country_code`');
CALL ensure_merchant_profile_column('base_merchant_info', 'business_type',
    'VARCHAR(64) NULL COMMENT ''业务类型'' AFTER `operating_country`');
CALL ensure_merchant_profile_column('base_merchant_info', 'industry_category',
    'VARCHAR(64) NULL COMMENT ''行业分类'' AFTER `business_type`');
CALL ensure_merchant_profile_column('base_merchant_info', 'merchant_description',
    'VARCHAR(1000) NULL COMMENT ''商户主营业务描述'' AFTER `industry_category`');
CALL ensure_merchant_profile_column('base_merchant_info', 'registration_number',
    'VARCHAR(128) NULL COMMENT ''公司注册号'' AFTER `merchant_description`');
CALL ensure_merchant_profile_column('base_merchant_info', 'legal_entity_type',
    'VARCHAR(64) NULL COMMENT ''企业注册类型'' AFTER `registration_number`');
CALL ensure_merchant_profile_column('base_merchant_info', 'incorporation_date',
    'DATE NULL COMMENT ''成立日期'' AFTER `legal_entity_type`');
CALL ensure_merchant_profile_column('base_merchant_info', 'incorporation_country',
    'CHAR(3) NULL COMMENT ''注册证书签发国家三字码'' AFTER `incorporation_date`');
CALL ensure_merchant_profile_column('base_merchant_info', 'registered_state',
    'VARCHAR(64) NULL COMMENT ''注册地址州省'' AFTER `incorporation_country`');
CALL ensure_merchant_profile_column('base_merchant_info', 'registered_city',
    'VARCHAR(64) NULL COMMENT ''注册地址城市'' AFTER `registered_state`');
CALL ensure_merchant_profile_column('base_merchant_info', 'registered_postcode',
    'VARCHAR(32) NULL COMMENT ''注册地址邮编'' AFTER `registered_city`');
CALL ensure_merchant_profile_column('base_merchant_info', 'registered_address',
    'VARCHAR(512) NULL COMMENT ''注册地址'' AFTER `registered_postcode`');
CALL ensure_merchant_profile_column('base_merchant_info', 'operating_same_as_registered',
    'TINYINT NULL COMMENT ''经营地址是否同注册地址'' AFTER `registered_address`');
CALL ensure_merchant_profile_column('base_merchant_info', 'tax_id',
    'VARCHAR(128) NULL COMMENT ''税号、EIN或TIN'' AFTER `operating_same_as_registered`');
CALL ensure_merchant_profile_column('base_merchant_info', 'company_size',
    'VARCHAR(32) NULL COMMENT ''公司规模区间'' AFTER `tax_id`');
CALL ensure_merchant_profile_column('base_merchant_info', 'employee_count',
    'INT NULL COMMENT ''员工人数'' AFTER `company_size`');
CALL ensure_merchant_profile_column('base_merchant_info', 'contact_title',
    'VARCHAR(64) NULL COMMENT ''主要联系人职位'' AFTER `contact_name`');
CALL ensure_merchant_profile_column('base_merchant_info', 'phone_country_code',
    'VARCHAR(8) NULL COMMENT ''联系人电话国家区号'' AFTER `contact_title`');
CALL ensure_merchant_profile_column('base_merchant_info', 'alternate_email',
    'VARCHAR(128) NULL COMMENT ''备用联系邮箱'' AFTER `contact_phone`');
CALL ensure_merchant_profile_column('base_merchant_info', 'finance_contact_name',
    'VARCHAR(64) NULL COMMENT ''财务联系人姓名'' AFTER `alternate_email`');
CALL ensure_merchant_profile_column('base_merchant_info', 'finance_contact_email',
    'VARCHAR(128) NULL COMMENT ''财务联系人邮箱'' AFTER `finance_contact_name`');
CALL ensure_merchant_profile_column('base_merchant_info', 'technical_contact_name',
    'VARCHAR(64) NULL COMMENT ''技术联系人姓名'' AFTER `finance_contact_email`');
CALL ensure_merchant_profile_column('base_merchant_info', 'technical_contact_email',
    'VARCHAR(128) NULL COMMENT ''技术联系人邮箱'' AFTER `technical_contact_name`');
CALL ensure_merchant_profile_column('base_merchant_info', 'business_model',
    'VARCHAR(32) NULL COMMENT ''业务模式'' AFTER `technical_contact_email`');
CALL ensure_merchant_profile_column('base_merchant_info', 'sales_channels',
    'VARCHAR(256) NULL COMMENT ''销售渠道，多值使用逗号分隔'' AFTER `business_model`');
CALL ensure_merchant_profile_column('base_merchant_info', 'products_services',
    'VARCHAR(1000) NULL COMMENT ''主营产品或服务'' AFTER `sales_channels`');
CALL ensure_merchant_profile_column('base_merchant_info', 'target_markets',
    'VARCHAR(512) NULL COMMENT ''目标市场国家，多值使用逗号分隔'' AFTER `products_services`');
CALL ensure_merchant_profile_column('base_merchant_info', 'customer_type',
    'VARCHAR(32) NULL COMMENT ''客户类型'' AFTER `target_markets`');
CALL ensure_merchant_profile_column('base_merchant_info', 'transaction_currencies',
    'VARCHAR(256) NULL COMMENT ''预计交易币种，多值使用逗号分隔'' AFTER `customer_type`');
CALL ensure_merchant_profile_column('base_merchant_info', 'expected_monthly_volume',
    'DECIMAL(20,2) NULL COMMENT ''预计月交易金额'' AFTER `transaction_currencies`');
CALL ensure_merchant_profile_column('base_merchant_info', 'expected_volume_currency',
    'CHAR(3) NULL COMMENT ''预计交易金额币种'' AFTER `expected_monthly_volume`');
CALL ensure_merchant_profile_column('base_merchant_info', 'average_ticket',
    'DECIMAL(20,2) NULL COMMENT ''平均单笔金额'' AFTER `expected_volume_currency`');
CALL ensure_merchant_profile_column('base_merchant_info', 'max_ticket',
    'DECIMAL(20,2) NULL COMMENT ''最大单笔金额预估'' AFTER `average_ticket`');
CALL ensure_merchant_profile_column('base_merchant_info', 'expected_monthly_count',
    'INT NULL COMMENT ''预计月交易笔数'' AFTER `max_ticket`');
CALL ensure_merchant_profile_column('base_merchant_info', 'expected_refund_rate',
    'DECIMAL(8,4) NULL COMMENT ''预计退款率百分比'' AFTER `expected_monthly_count`');
CALL ensure_merchant_profile_column('base_merchant_info', 'expected_chargeback_rate',
    'DECIMAL(8,4) NULL COMMENT ''预计拒付率百分比'' AFTER `expected_refund_rate`');
CALL ensure_merchant_profile_column('base_merchant_info', 'recurring_payment_flag',
    'TINYINT NULL COMMENT ''是否订阅或循环扣款'' AFTER `expected_chargeback_rate`');
CALL ensure_merchant_profile_column('base_merchant_info', 'presale_flag',
    'TINYINT NULL COMMENT ''是否预售'' AFTER `recurring_payment_flag`');
CALL ensure_merchant_profile_column('base_merchant_info', 'fulfillment_days',
    'INT NULL COMMENT ''发货或服务交付天数'' AFTER `presale_flag`');
CALL ensure_merchant_profile_column('base_merchant_info', 'digital_goods_flag',
    'TINYINT NULL COMMENT ''是否涉及数字商品'' AFTER `fulfillment_days`');
CALL ensure_merchant_profile_column('base_merchant_info', 'restricted_business_flag',
    'TINYINT NULL COMMENT ''是否涉及受限行业'' AFTER `digital_goods_flag`');
CALL ensure_merchant_profile_column('base_merchant_info', 'expected_go_live_date',
    'DATE NULL COMMENT ''预计上线日期'' AFTER `restricted_business_flag`');
CALL ensure_merchant_profile_column('base_merchant_info', 'website_url',
    'VARCHAR(512) NULL COMMENT ''官方网站'' AFTER `expected_go_live_date`');
CALL ensure_merchant_profile_column('base_merchant_info', 'app_store_url',
    'VARCHAR(512) NULL COMMENT ''App Store地址'' AFTER `website_url`');
CALL ensure_merchant_profile_column('base_merchant_info', 'google_play_url',
    'VARCHAR(512) NULL COMMENT ''Google Play地址'' AFTER `app_store_url`');
CALL ensure_merchant_profile_column('base_merchant_info', 'other_sales_url',
    'VARCHAR(512) NULL COMMENT ''其他销售页面'' AFTER `google_play_url`');
CALL ensure_merchant_profile_column('base_merchant_info', 'website_languages',
    'VARCHAR(256) NULL COMMENT ''网站语言，多值使用逗号分隔'' AFTER `other_sales_url`');
CALL ensure_merchant_profile_column('base_merchant_info', 'website_live_flag',
    'TINYINT NULL COMMENT ''网站是否已上线'' AFTER `website_languages`');
CALL ensure_merchant_profile_column('base_merchant_info', 'privacy_policy_url',
    'VARCHAR(512) NULL COMMENT ''隐私政策地址'' AFTER `website_live_flag`');
CALL ensure_merchant_profile_column('base_merchant_info', 'refund_policy_url',
    'VARCHAR(512) NULL COMMENT ''退款政策地址'' AFTER `privacy_policy_url`');
CALL ensure_merchant_profile_column('base_merchant_info', 'terms_url',
    'VARCHAR(512) NULL COMMENT ''服务条款地址'' AFTER `refund_policy_url`');
CALL ensure_merchant_profile_column('base_merchant_info', 'shipping_policy_url',
    'VARCHAR(512) NULL COMMENT ''配送政策地址'' AFTER `terms_url`');

-- 草稿阶段尚未完成内部配置，MCC、结算币种和必答布尔项必须允许为空。
-- 显式 MODIFY 兼容这些字段已经由旧脚本创建或原表定义为 NOT NULL 的环境。
ALTER TABLE base_merchant_info
    MODIFY COLUMN merchant_category_code VARCHAR(4) NULL COMMENT '商户类别码MCC',
    MODIFY COLUMN settlement_currency CHAR(3) NULL COMMENT '默认结算币种',
    MODIFY COLUMN operating_same_as_registered TINYINT NULL COMMENT '经营地址是否同注册地址',
    MODIFY COLUMN recurring_payment_flag TINYINT NULL COMMENT '是否订阅或循环扣款',
    MODIFY COLUMN presale_flag TINYINT NULL COMMENT '是否预售',
    MODIFY COLUMN digital_goods_flag TINYINT NULL COMMENT '是否涉及数字商品',
    MODIFY COLUMN restricted_business_flag TINYINT NULL COMMENT '是否涉及受限行业',
    MODIFY COLUMN website_live_flag TINYINT NULL COMMENT '网站是否已上线';

CALL ensure_merchant_profile_column('merchant_settlement_profile', 'beneficiary_name',
    'VARCHAR(128) NULL COMMENT ''结算收款人名称'' AFTER `business_time_zone`');
CALL ensure_merchant_profile_column('merchant_settlement_profile', 'bank_name',
    'VARCHAR(128) NULL COMMENT ''结算银行名称'' AFTER `beneficiary_name`');
CALL ensure_merchant_profile_column('merchant_settlement_profile', 'bank_country',
    'CHAR(3) NULL COMMENT ''结算银行国家三字码'' AFTER `bank_name`');
CALL ensure_merchant_profile_column('merchant_settlement_profile', 'bank_account_cipher',
    'VARCHAR(1024) NULL COMMENT ''结算账号或IBAN密文'' AFTER `bank_country`');
CALL ensure_merchant_profile_column('merchant_settlement_profile', 'swift_bic',
    'VARCHAR(32) NULL COMMENT ''SWIFT或BIC'' AFTER `bank_account_cipher`');

UPDATE base_merchant_info
SET application_no = CONCAT('LEGACY-', merchant_id),
    onboarding_source = 'IMPORT',
    onboarding_status = CASE WHEN merchant_status = 3 THEN 'CLOSED' ELSE 'ACTIVE' END,
    review_status = 'PASSED',
    activation_status = CASE WHEN merchant_status = 3 THEN 'INACTIVE' ELSE 'ACTIVE' END
WHERE deleted = 0
  AND application_no IS NULL;

CALL ensure_merchant_profile_index('base_merchant_info', 'uk_base_merchant_application_no',
    'UNIQUE KEY `uk_base_merchant_application_no` (`application_no`)');
CALL ensure_merchant_profile_index('base_merchant_info', 'idx_base_merchant_onboarding',
    'KEY `idx_base_merchant_onboarding` (`onboarding_status`, `review_status`, `activation_status`, `deleted`)');

CREATE TABLE IF NOT EXISTS merchant_related_person (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    merchant_id VARCHAR(32) NOT NULL COMMENT '平台商户号',
    full_name VARCHAR(128) NOT NULL COMMENT '人员姓名',
    person_roles VARCHAR(256) NOT NULL COMMENT '人员角色，多值使用逗号分隔',
    nationality CHAR(3) NULL COMMENT '国籍国家三字码',
    date_of_birth DATE NULL COMMENT '出生日期',
    residence_country CHAR(3) NULL COMMENT '居住国家三字码',
    residential_address VARCHAR(512) NULL COMMENT '居住地址',
    id_type VARCHAR(32) NULL COMMENT '证件类型',
    id_number_cipher VARCHAR(1024) NULL COMMENT '证件号AES-GCM密文',
    id_number_masked VARCHAR(64) NULL COMMENT '证件号脱敏值',
    id_expiry_date DATE NULL COMMENT '证件有效期',
    ownership_percentage DECIMAL(7,4) NULL COMMENT '持股比例百分比',
    controller_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否实际控制人',
    pep_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否政治公众人物',
    email VARCHAR(128) NULL COMMENT '联系邮箱',
    phone VARCHAR(32) NULL COMMENT '联系电话',
    display_order INT NOT NULL DEFAULT 0 COMMENT '页面展示顺序',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识',
    PRIMARY KEY (id),
    KEY idx_merchant_related_person_mid (merchant_id, deleted, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户法定代表人、董事、UBO及授权人';

CREATE TABLE IF NOT EXISTS biz_document (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    biz_type VARCHAR(32) NOT NULL COMMENT '业务类型',
    biz_id VARCHAR(64) NOT NULL COMMENT '业务标识，商户资料使用平台商户号',
    document_type VARCHAR(64) NOT NULL COMMENT '资料类型',
    original_filename VARCHAR(255) NOT NULL COMMENT '原始文件名',
    content_type VARCHAR(128) NOT NULL COMMENT 'MIME类型',
    file_size BIGINT NOT NULL COMMENT '文件字节数',
    sha256 CHAR(64) NOT NULL COMMENT '文件SHA-256摘要',
    storage_provider VARCHAR(24) NOT NULL COMMENT '存储供应商',
    bucket_name VARCHAR(128) NOT NULL COMMENT '对象存储Bucket',
    object_key VARCHAR(512) NOT NULL COMMENT '对象键',
    document_status VARCHAR(24) NOT NULL DEFAULT 'UPLOADED' COMMENT '资料状态',
    uploaded_by VARCHAR(64) NULL COMMENT '上传操作人',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_document_object (storage_provider, bucket_name, object_key),
    KEY idx_biz_document_owner (biz_type, biz_id, document_type, deleted, gmt_create)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通用业务资料对象元数据';

CREATE TABLE IF NOT EXISTS merchant_review_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    merchant_id VARCHAR(32) NOT NULL COMMENT '平台商户号',
    review_action VARCHAR(32) NOT NULL COMMENT '提交、通过、补件或驳回动作',
    from_status VARCHAR(32) NOT NULL COMMENT '动作前审核状态',
    to_status VARCHAR(32) NOT NULL COMMENT '动作后审核状态',
    review_comment VARCHAR(1000) NULL COMMENT '审核意见或补件说明',
    operator_id VARCHAR(64) NULL COMMENT '审核操作人账号ID',
    operator_name VARCHAR(128) NULL COMMENT '审核操作人名称',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_merchant_review_record_mid (merchant_id, gmt_create, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户开户审核不可变记录';

DROP PROCEDURE IF EXISTS ensure_merchant_profile_column;
DROP PROCEDURE IF EXISTS ensure_merchant_profile_index;
