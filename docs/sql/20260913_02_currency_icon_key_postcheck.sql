-- Read-only postcheck for managed currency presentation keys.
-- Every *_count result must be zero.

SET NAMES utf8mb4;

SELECT 1 - COUNT(*) AS missing_currency_icon_key_column_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'base_iso_currency'
  AND column_name = 'icon_key'
  AND data_type = 'varchar'
  AND character_maximum_length = 64
  AND is_nullable = 'YES';

SELECT COUNT(*) AS invalid_currency_icon_key_count
FROM base_iso_currency
WHERE icon_key IS NOT NULL
  AND icon_key <> ''
  AND icon_key NOT REGEXP '^flag:[A-Z]{2}$|^currency:[A-Z0-9]{3,12}$';

SELECT COUNT(*) AS mismatched_currency_avatar_count
FROM base_iso_currency
WHERE icon_key LIKE 'currency:%'
  AND SUBSTRING(icon_key, 10) <> alpha3_code;

SELECT alpha3_code, chinese_name, english_name, currency_symbol, icon_key, status
FROM base_iso_currency
WHERE deleted = 0
ORDER BY alpha3_code;
