-- Extend the existing resumable manual settlement task to reserve release reviews.
-- Reserve maturity is defined by immutable reserve detail fields, not transaction T/D+N fields.

ALTER TABLE settlement_manual_review_task
    DROP CHECK chk_manual_review_task_value,
    MODIFY COLUMN initial_delay_unit CHAR(1) NULL,
    MODIFY COLUMN initial_delay_days INT NULL,
    MODIFY COLUMN regular_delay_days INT NULL,
    MODIFY COLUMN settlement_frequency VARCHAR(16) NULL,
    ADD CONSTRAINT chk_manual_review_task_value CHECK (
        review_type IN ('REGULAR', 'RESERVE_RELEASE')
        AND target_currency_exponent BETWEEN 0 AND 8
        AND cutoff_end_time > cutoff_begin_time
        AND snapshot_max_candidate_id >= 0
        AND expected_candidate_count >= 0
        AND processed_candidate_count BETWEEN 0 AND expected_candidate_count
        AND locked_candidate_count BETWEEN 0 AND expected_candidate_count
        AND last_candidate_id BETWEEN 0 AND snapshot_max_candidate_id
        AND retry_count >= 0 AND version >= 0
        AND ((review_type = 'REGULAR'
              AND initial_delay_unit IN ('T', 'D')
              AND initial_delay_days >= 1
              AND regular_delay_days >= 1
              AND settlement_frequency IS NOT NULL)
             OR (review_type = 'RESERVE_RELEASE'
                 AND initial_delay_unit IS NULL
                 AND initial_delay_days IS NULL
                 AND regular_delay_days IS NULL
                 AND settlement_frequency IS NULL
                 AND frequency_day IS NULL))
    );
