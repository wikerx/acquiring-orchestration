-- Channel capability default transaction currency migration.
-- MPGS capabilities that allow USD are backfilled to USD by confirmed operating intent.
-- Other active capabilities retain the previous alphabetical-first fallback behavior.

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS migrate_channel_capability_default_currency;
DELIMITER $$
CREATE PROCEDURE migrate_channel_capability_default_currency()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'channel_payment_capability'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'channel_payment_capability does not exist';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'channel_capability_currency'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'channel_capability_currency does not exist';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'channel_payment_capability'
          AND column_name = 'default_transaction_currency'
    ) THEN
        ALTER TABLE channel_payment_capability
            ADD COLUMN default_transaction_currency CHAR(3) NULL
                COMMENT 'Default transaction currency; must belong to capability currencies'
                AFTER transaction_type;
    END IF;

    UPDATE channel_payment_capability capability
    SET capability.default_transaction_currency = CASE
        WHEN capability.channel_code = 'MPGS'
             AND EXISTS (
                 SELECT 1
                 FROM channel_capability_currency currency_row
                 WHERE currency_row.capability_id = capability.id
                   AND currency_row.currency_code = 'USD'
                   AND currency_row.currency_status = 1
                   AND currency_row.deleted = 0
             ) THEN 'USD'
        ELSE (
            SELECT MIN(currency_row.currency_code)
            FROM channel_capability_currency currency_row
            WHERE currency_row.capability_id = capability.id
              AND currency_row.currency_status = 1
              AND currency_row.deleted = 0
        )
    END
    WHERE capability.deleted = 0
      AND (
          capability.default_transaction_currency IS NULL
          OR capability.default_transaction_currency = ''
          OR NOT EXISTS (
              SELECT 1
              FROM channel_capability_currency allowed_currency
              WHERE allowed_currency.capability_id = capability.id
                AND allowed_currency.currency_code = capability.default_transaction_currency
                AND allowed_currency.currency_status = 1
                AND allowed_currency.deleted = 0
          )
      );

    UPDATE channel_payment_capability capability
    SET capability.default_transaction_currency = COALESCE(
        (
            SELECT MIN(currency_row.currency_code)
            FROM channel_capability_currency currency_row
            WHERE currency_row.capability_id = capability.id
        ),
        'USD'
    )
    WHERE capability.default_transaction_currency IS NULL
       OR capability.default_transaction_currency = '';

    IF EXISTS (
        SELECT 1
        FROM channel_payment_capability capability
        WHERE capability.deleted = 0
          AND NOT EXISTS (
              SELECT 1
              FROM channel_capability_currency currency_row
              WHERE currency_row.capability_id = capability.id
                AND currency_row.currency_code = capability.default_transaction_currency
                AND currency_row.currency_status = 1
                AND currency_row.deleted = 0
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'active capability default currency is outside allowed currencies';
    END IF;

    ALTER TABLE channel_payment_capability
        MODIFY COLUMN default_transaction_currency CHAR(3) NOT NULL
            COMMENT 'Default transaction currency; must belong to capability currencies';

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'channel_payment_capability'
          AND constraint_name = 'chk_channel_capability_default_transaction_currency'
          AND constraint_type = 'CHECK'
    ) THEN
        ALTER TABLE channel_payment_capability
            ADD CONSTRAINT chk_channel_capability_default_transaction_currency CHECK (
                CHAR_LENGTH(default_transaction_currency) = 3
                AND BINARY default_transaction_currency = BINARY UPPER(default_transaction_currency)
            );
    END IF;
END$$
DELIMITER ;

CALL migrate_channel_capability_default_currency();
DROP PROCEDURE migrate_channel_capability_default_currency;
