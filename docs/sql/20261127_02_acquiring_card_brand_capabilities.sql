-- 先执行 20261127_01_acquiring_channel_method_scope_migration.sql。
-- 在停止渠道配置写入及收单流量后执行。保留旧能力及其明细，便于上线前比对和回退。
-- 回退时应恢复迁移前的数据库快照。启用独立配置后，旧版无法表达拆分后的差异。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS migrate_acquiring_card_brand_capabilities;
DELIMITER //
CREATE PROCEDURE migrate_acquiring_card_brand_capabilities()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'channel_payment_capability' AND column_name = 'card_brand'
    ) THEN
        ALTER TABLE channel_payment_capability
            ADD COLUMN card_brand VARCHAR(64) NOT NULL DEFAULT 'NONE'
                COMMENT '收单银行卡能力的卡品牌，其他能力使用NONE' AFTER payment_method,
            DROP INDEX uk_channel_capability_scope,
            ADD UNIQUE KEY uk_channel_capability_scope (channel_id, business_type, payment_method, card_brand, deleted);
    END IF;

    START TRANSACTION;

    IF EXISTS (
        SELECT 1
        FROM channel_payment_capability legacy
        WHERE legacy.business_type = 'ACQUIRING' AND legacy.payment_method = 'BANK_CARD'
          AND legacy.card_brand = 'NONE' AND legacy.deleted = 0
          AND NOT EXISTS (
              SELECT 1 FROM channel_capability_card_brand brand
              WHERE brand.capability_id = legacy.id AND brand.deleted = 0
          )
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'An acquiring card capability has no active brand records';
    END IF;

    INSERT INTO channel_payment_capability (
        channel_id, channel_code, business_type, payment_method, card_brand, transaction_type,
        default_transaction_currency, allowed_country_scope, support_3ds, support_incremental_authorization,
        capability_status, sort_order, remark, create_by, create_time, update_by, update_time, deleted
    )
    SELECT legacy.channel_id, legacy.channel_code, legacy.business_type, legacy.payment_method,
           brand.card_brand, legacy.transaction_type, legacy.default_transaction_currency, legacy.allowed_country_scope,
           legacy.support_3ds, legacy.support_incremental_authorization,
           IF(legacy.capability_status = 1 AND brand.brand_status = 1, 1, 0),
           legacy.sort_order, legacy.remark, legacy.create_by, legacy.create_time, legacy.update_by, legacy.update_time, 0
    FROM channel_payment_capability legacy
    JOIN channel_capability_card_brand brand ON brand.capability_id = legacy.id AND brand.deleted = 0
    WHERE legacy.business_type = 'ACQUIRING' AND legacy.payment_method = 'BANK_CARD'
      AND legacy.card_brand = 'NONE' AND legacy.deleted = 0
    ORDER BY legacy.sort_order, legacy.id, brand.sort_order, brand.id;

    INSERT INTO channel_capability_currency (
        capability_id, channel_id, channel_code, currency_code, currency_status,
        create_by, create_time, update_by, update_time, deleted
    )
    SELECT child.id, currency.channel_id, currency.channel_code, currency.currency_code,
           currency.currency_status, currency.create_by, currency.create_time, currency.update_by, currency.update_time, 0
    FROM channel_payment_capability legacy
    JOIN channel_payment_capability child ON child.channel_id = legacy.channel_id
         AND child.business_type = legacy.business_type AND child.payment_method = legacy.payment_method
         AND child.card_brand <> 'NONE' AND child.deleted = 0
    JOIN channel_capability_currency currency ON currency.capability_id = legacy.id AND currency.deleted = 0
    WHERE legacy.business_type = 'ACQUIRING' AND legacy.payment_method = 'BANK_CARD'
      AND legacy.card_brand = 'NONE' AND legacy.deleted = 0;

    INSERT INTO channel_capability_card_brand (
        capability_id, channel_id, channel_code, card_brand, brand_status, sort_order,
        create_by, create_time, update_by, update_time, deleted
    )
    SELECT child.id, brand.channel_id, brand.channel_code, brand.card_brand, 1, brand.sort_order,
           brand.create_by, brand.create_time, brand.update_by, brand.update_time, 0
    FROM channel_payment_capability legacy
    JOIN channel_capability_card_brand brand ON brand.capability_id = legacy.id AND brand.deleted = 0
    JOIN channel_payment_capability child ON child.channel_id = legacy.channel_id
         AND child.business_type = legacy.business_type AND child.payment_method = legacy.payment_method
         AND child.card_brand = brand.card_brand AND child.deleted = 0
    WHERE legacy.business_type = 'ACQUIRING' AND legacy.payment_method = 'BANK_CARD'
      AND legacy.card_brand = 'NONE' AND legacy.deleted = 0;

    UPDATE channel_payment_capability
    SET deleted = id, update_time = update_time
    WHERE business_type = 'ACQUIRING' AND payment_method = 'BANK_CARD' AND card_brand = 'NONE' AND deleted = 0;

    UPDATE channel_mid_config SET mcc = '5311', update_time = update_time
    WHERE mcc IS NULL OR TRIM(mcc) = '';

    COMMIT;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'channel_mid_config'
          AND column_name = 'mcc' AND is_nullable = 'YES'
    ) THEN
        ALTER TABLE channel_mid_config
            MODIFY COLUMN mcc VARCHAR(16) NOT NULL COMMENT '渠道商户号对应的商户类别码';
    END IF;
END//
DELIMITER ;

CALL migrate_acquiring_card_brand_capabilities();
DROP PROCEDURE migrate_acquiring_card_brand_capabilities;
