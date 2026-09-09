-- Post-deployment checks for 20260908_01_manual_reserve_settlement_review_migration.sql.

SELECT column_name, is_nullable, column_type
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'settlement_manual_review_task'
  AND column_name IN ('review_type', 'initial_delay_unit', 'initial_delay_days',
                      'regular_delay_days', 'settlement_frequency', 'frequency_day')
ORDER BY ordinal_position;

SELECT constraint_name, check_clause
FROM information_schema.check_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name = 'chk_manual_review_task_value';

SELECT COUNT(*) AS invalid_reserve_manual_tasks
FROM settlement_manual_review_task
WHERE review_type = 'RESERVE_RELEASE'
  AND (initial_delay_unit IS NOT NULL
       OR initial_delay_days IS NOT NULL
       OR regular_delay_days IS NOT NULL
       OR settlement_frequency IS NOT NULL
       OR frequency_day IS NOT NULL);

SELECT COUNT(*) AS invalid_manual_task_review_types
FROM settlement_manual_review_task
WHERE review_type NOT IN ('REGULAR', 'RESERVE_RELEASE');
