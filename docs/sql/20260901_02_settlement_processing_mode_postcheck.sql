-- Read-only postcheck for merchant settlement profile processing mode.
-- Every result except processing_mode_distribution must be zero.

SET NAMES utf8mb4;

SELECT 1 - COUNT(*) AS missing_or_invalid_processing_mode_column_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'merchant_settlement_profile'
  AND column_name = 'processing_mode'
  AND data_type = 'varchar'
  AND character_maximum_length = 16
  AND is_nullable = 'NO'
  AND column_default = 'AUTO_POST';

SELECT 1 - COUNT(*) AS missing_processing_mode_check_count
FROM information_schema.table_constraints constraint_row
INNER JOIN information_schema.check_constraints check_row
        ON check_row.constraint_schema = constraint_row.constraint_schema
       AND check_row.constraint_name = constraint_row.constraint_name
WHERE constraint_row.constraint_schema = DATABASE()
  AND constraint_row.table_name = 'merchant_settlement_profile'
  AND constraint_row.constraint_name = 'chk_settlement_profile_processing_mode'
  AND constraint_row.constraint_type = 'CHECK'
  AND check_row.check_clause LIKE '%AUTO_POST%'
  AND check_row.check_clause LIKE '%AUTO_REVIEW%'
  AND check_row.check_clause LIKE '%MANUAL%';

SELECT COUNT(*) AS invalid_processing_mode_value_count
FROM merchant_settlement_profile
WHERE processing_mode IS NULL
   OR processing_mode NOT IN ('AUTO_POST', 'AUTO_REVIEW', 'MANUAL');

SELECT processing_mode, COUNT(*) AS profile_count
FROM merchant_settlement_profile
GROUP BY processing_mode
ORDER BY processing_mode;
