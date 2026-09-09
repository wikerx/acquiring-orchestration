-- Post-deployment checks for 20260905_01_manual_settlement_async_review_migration.sql.

SELECT table_name, constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name IN ('settlement_manual_review_task', 'settlement_review_segment',
                     'settlement_review_decision_task', 'settlement_review_order')
ORDER BY table_name, constraint_name;

SELECT table_name, index_name, non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS indexed_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('settlement_manual_review_task', 'settlement_review_segment',
                     'settlement_review_decision_task', 'settlement_candidate', 'settlement_batch')
  AND index_name IN ('uk_manual_review_task_active_profile',
                     'idx_manual_review_task_due',
                     'idx_settlement_review_segment_status',
                     'uk_settlement_review_decision_active',
                     'idx_settlement_review_decision_due',
                     'idx_settlement_review_decision_order',
                     'idx_settlement_candidate_manual_review',
                     'idx_settlement_batch_review')
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;

SELECT COUNT(*) AS invalid_pending_review_rows
FROM settlement_review_order
WHERE review_status = 'PENDING_APPROVAL'
  AND (decided_by_account_id IS NOT NULL OR decision_action IS NOT NULL
       OR decision_request_key IS NOT NULL OR decision_time IS NOT NULL
       OR review_comment IS NOT NULL OR settlement_batch_no IS NOT NULL);

SELECT COUNT(*) AS duplicate_active_profile_tasks
FROM (
    SELECT settlement_profile_id
    FROM settlement_manual_review_task
    WHERE task_status IN ('PREVIEWED', 'QUEUED', 'PROCESSING', 'FINALIZING', 'CANCELLING')
    GROUP BY settlement_profile_id
    HAVING COUNT(*) > 1
) duplicated;

SELECT COUNT(*) AS duplicate_active_review_decision_tasks
FROM (
    SELECT review_order_no
    FROM settlement_review_decision_task
    WHERE task_status IN ('QUEUED', 'PROCESSING', 'FAILED')
    GROUP BY review_order_no
    HAVING COUNT(*) > 1
) duplicated;

SELECT COUNT(*) AS invalid_review_decision_progress_rows
FROM settlement_review_decision_task
WHERE total_segment_count < 1
   OR processed_segment_count < 0
   OR processed_segment_count > total_segment_count
   OR result_batch_count < 0
   OR result_batch_count > processed_segment_count
   OR (decision_action <> 'APPROVE'
       AND (result_batch_count <> 0 OR first_settlement_batch_no IS NOT NULL));

SELECT COUNT(*) AS invalid_segment_batch_links
FROM settlement_review_segment segment
LEFT JOIN settlement_batch batch
       ON batch.settlement_batch_no = segment.settlement_batch_no
WHERE segment.settlement_batch_no IS NOT NULL
  AND (batch.id IS NULL OR batch.review_order_no <> segment.review_order_no);

SELECT COUNT(*) AS invalid_completed_decision_tasks
FROM settlement_review_decision_task task
LEFT JOIN settlement_review_order review
       ON review.review_order_no = task.review_order_no
WHERE task.task_status = 'COMPLETED'
  AND (task.processed_segment_count <> task.total_segment_count
       OR review.id IS NULL
       OR review.review_status NOT IN ('APPROVED', 'REJECTED', 'CANCELLED'));
