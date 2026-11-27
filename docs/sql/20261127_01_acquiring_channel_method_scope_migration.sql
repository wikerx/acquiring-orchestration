-- Add method-level country scope and enable method-level default currency only
-- where the existing acquiring MID and every selected enabled capability agree.
-- Run before deploying readers. Old readers can continue to use the original
-- MID columns. PAYOUT rows and the legacy MID identity and scope are preserved.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS migrate_acquiring_channel_method_scope;
DELIMITER $$
CREATE PROCEDURE migrate_acquiring_channel_method_scope()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'channel_payment_capability'
          AND column_name = 'allowed_country_scope'
    ) THEN
        ALTER TABLE channel_payment_capability ADD COLUMN allowed_country_scope VARCHAR(512) NULL
            COMMENT '收单允许交易国家，ALL或逗号分隔' AFTER default_transaction_currency;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'channel_mid_config'
          AND column_name = 'inherit_transaction_currency'
    ) THEN
        ALTER TABLE channel_mid_config ADD COLUMN inherit_transaction_currency TINYINT NOT NULL DEFAULT 0
            COMMENT '收单默认交易币种是否按支付能力读取' AFTER default_transaction_currency;
    END IF;

    -- Preserve business timestamps. This is a change of representation.
    UPDATE channel_payment_capability
    SET allowed_country_scope = 'ALL', update_time = update_time
    WHERE business_type = 'ACQUIRING' AND deleted = 0 AND allowed_country_scope IS NULL;

    UPDATE channel_mid_config mid
    SET mid.inherit_transaction_currency = 1, mid.update_time = mid.update_time
    WHERE mid.business_type = 'ACQUIRING' AND mid.deleted = 0 AND mid.inherit_transaction_currency = 0
      AND mid.default_transaction_currency = 'USD'
      AND EXISTS (
          SELECT 1 FROM channel_payment_capability cap
          WHERE cap.channel_id = mid.channel_id AND cap.business_type = 'ACQUIRING'
            AND cap.deleted = 0 AND cap.capability_status = 1
            AND (mid.payment_method_scope = 'ALL' OR FIND_IN_SET(cap.payment_method, mid.payment_method_scope) > 0)
      )
      AND NOT EXISTS (
          SELECT 1 FROM channel_payment_capability cap
          WHERE cap.channel_id = mid.channel_id AND cap.business_type = 'ACQUIRING'
            AND cap.deleted = 0
            AND (mid.payment_method_scope = 'ALL' OR FIND_IN_SET(cap.payment_method, mid.payment_method_scope) > 0)
            AND cap.default_transaction_currency <> mid.default_transaction_currency
      );
END$$
DELIMITER ;

CALL migrate_acquiring_channel_method_scope();
DROP PROCEDURE migrate_acquiring_channel_method_scope;
