-- Read-only postcheck for channel capability default transaction currency.
-- Every count result must be zero.

SET NAMES utf8mb4;

SELECT 1 - COUNT(*) AS missing_default_transaction_currency_column_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'channel_payment_capability'
  AND column_name = 'default_transaction_currency'
  AND data_type = 'char'
  AND character_maximum_length = 3
  AND is_nullable = 'NO';

SELECT COUNT(*) AS invalid_default_transaction_currency_count
FROM channel_payment_capability capability
WHERE capability.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM channel_capability_currency currency_row
      WHERE currency_row.capability_id = capability.id
        AND currency_row.currency_code = capability.default_transaction_currency
        AND currency_row.currency_status = 1
        AND currency_row.deleted = 0
  );

SELECT COUNT(*) AS invalid_default_transaction_currency_format_count
FROM channel_payment_capability
WHERE CHAR_LENGTH(default_transaction_currency) <> 3
   OR BINARY default_transaction_currency <> BINARY UPPER(default_transaction_currency);

SELECT COUNT(*) AS mpgs_usd_default_mismatch_count
FROM channel_payment_capability capability
WHERE capability.deleted = 0
  AND capability.channel_code = 'MPGS'
  AND capability.default_transaction_currency <> 'USD'
  AND EXISTS (
      SELECT 1
      FROM channel_capability_currency currency_row
      WHERE currency_row.capability_id = capability.id
        AND currency_row.currency_code = 'USD'
        AND currency_row.currency_status = 1
        AND currency_row.deleted = 0
  );

SELECT channel_code, business_type, payment_method,
       default_transaction_currency, COUNT(*) AS capability_count
FROM channel_payment_capability
WHERE deleted = 0
GROUP BY channel_code, business_type, payment_method, default_transaction_currency
ORDER BY channel_code, business_type, payment_method, default_transaction_currency;
