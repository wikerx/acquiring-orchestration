-- Merchant settlement profile processing mode migration.
-- Existing profiles remain AUTO_POST to preserve the deployed automatic-posting behavior.

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS migrate_settlement_processing_mode;
DELIMITER $$
CREATE PROCEDURE migrate_settlement_processing_mode()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'merchant_settlement_profile'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'merchant_settlement_profile does not exist';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'merchant_settlement_profile'
          AND column_name = 'processing_mode'
    ) THEN
        ALTER TABLE merchant_settlement_profile
            ADD COLUMN processing_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO_POST'
                COMMENT 'AUTO_POST automatic posting, AUTO_REVIEW automatic selection with approval, MANUAL admin selection'
                AFTER daily_cutoff_time;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM merchant_settlement_profile
        WHERE processing_mode IS NULL
           OR processing_mode NOT IN ('AUTO_POST', 'AUTO_REVIEW', 'MANUAL')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'merchant_settlement_profile contains an invalid processing_mode';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'merchant_settlement_profile'
          AND constraint_name = 'chk_settlement_profile_processing_mode'
          AND constraint_type = 'CHECK'
    ) THEN
        ALTER TABLE merchant_settlement_profile
            ADD CONSTRAINT chk_settlement_profile_processing_mode CHECK (
                processing_mode IN ('AUTO_POST', 'AUTO_REVIEW', 'MANUAL')
            );
    END IF;
END$$
DELIMITER ;

CALL migrate_settlement_processing_mode();
DROP PROCEDURE migrate_settlement_processing_mode;
