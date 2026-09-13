-- Add managed currency presentation keys used by Admin and Merchant interfaces.
-- This script stores only controlled keys, never external image URLs or HTML.

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS migrate_currency_icon_key;
DELIMITER $$
CREATE PROCEDURE migrate_currency_icon_key()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'base_iso_currency'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'base_iso_currency does not exist';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'base_iso_country'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'base_iso_country does not exist';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'base_iso_currency'
          AND column_name = 'icon_key'
    ) THEN
        ALTER TABLE base_iso_currency
            ADD COLUMN icon_key VARCHAR(64) NULL
                COMMENT 'Controlled currency presentation icon key'
                AFTER currency_symbol;
    END IF;

    UPDATE base_iso_currency currency_row
    LEFT JOIN (
        SELECT currency_alpha3_code, MIN(alpha2_code) AS alpha2_code
        FROM base_iso_country
        WHERE currency_alpha3_code <> ''
          AND status = 1
          AND deleted = 0
        GROUP BY currency_alpha3_code
        HAVING COUNT(*) = 1
    ) country_row ON country_row.currency_alpha3_code = currency_row.alpha3_code
    SET currency_row.icon_key = CASE
        WHEN currency_row.alpha3_code = 'USD' THEN 'flag:US'
        WHEN currency_row.alpha3_code = 'CNY' THEN 'flag:CN'
        WHEN currency_row.alpha3_code = 'EUR' THEN 'flag:EU'
        WHEN currency_row.alpha3_code = 'HKD' THEN 'flag:HK'
        WHEN currency_row.alpha3_code = 'GBP' THEN 'flag:GB'
        WHEN currency_row.alpha3_code = 'JPY' THEN 'flag:JP'
        WHEN currency_row.alpha3_code IN ('XAU', 'XAG', 'XPT', 'XPD', 'XAF', 'XOF', 'XCD', 'XXX')
            THEN CONCAT('currency:', currency_row.alpha3_code)
        WHEN country_row.alpha2_code IS NOT NULL THEN CONCAT('flag:', country_row.alpha2_code)
        ELSE NULL
    END
    WHERE currency_row.deleted = 0
      AND (currency_row.icon_key IS NULL OR currency_row.icon_key = '');
END$$
DELIMITER ;

CALL migrate_currency_icon_key();
DROP PROCEDURE migrate_currency_icon_key;
