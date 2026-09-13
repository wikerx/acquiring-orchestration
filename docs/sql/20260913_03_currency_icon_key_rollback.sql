-- Roll back managed currency presentation keys.
-- Review application compatibility before running because new code reads this column.

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS rollback_currency_icon_key;
DELIMITER $$
CREATE PROCEDURE rollback_currency_icon_key()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'base_iso_currency'
          AND column_name = 'icon_key'
    ) THEN
        ALTER TABLE base_iso_currency DROP COLUMN icon_key;
    END IF;
END$$
DELIMITER ;

CALL rollback_currency_icon_key();
DROP PROCEDURE rollback_currency_icon_key;
