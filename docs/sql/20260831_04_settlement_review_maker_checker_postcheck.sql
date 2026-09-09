-- Settlement review Maker-Checker post-deployment checks. Every result must be zero.

SELECT COUNT(*) AS invalid_replay_hold_ownership
FROM settlement_candidate
WHERE candidate_status = 'REPLAY_HOLD'
  AND (settlement_batch_no IS NOT NULL OR review_order_no IS NOT NULL);

SELECT COUNT(*) AS invalid_review_candidate_ownership
FROM settlement_candidate candidate
LEFT JOIN settlement_review_candidate relation
       ON relation.review_order_no = candidate.review_order_no
      AND relation.candidate_id = candidate.id
      AND relation.relation_status = 'LOCKED'
WHERE candidate.candidate_status = 'REVIEW_LOCKED'
  AND (candidate.review_order_no IS NULL OR candidate.settlement_batch_no IS NOT NULL
       OR relation.id IS NULL);

SELECT COUNT(*) AS invalid_pending_review_snapshot
FROM settlement_review_order review_order
WHERE review_order.review_status = 'PENDING_APPROVAL'
  AND (review_order.candidate_count <> (
          SELECT COUNT(*) FROM settlement_review_candidate relation
          WHERE relation.review_order_no = review_order.review_order_no
            AND relation.relation_status = 'LOCKED'
      )
       OR review_order.source_fingerprint IS NULL
       OR review_order.rate_fingerprint IS NULL
       OR review_order.result_fingerprint IS NULL);

SELECT COUNT(*) AS invalid_approved_review_batch
FROM settlement_review_order review_order
LEFT JOIN settlement_batch batch
       ON batch.review_order_no = review_order.review_order_no
      AND batch.settlement_batch_no = review_order.settlement_batch_no
WHERE review_order.review_status = 'APPROVED'
  AND (batch.id IS NULL OR batch.create_mode <> 'MANUAL_REVIEW'
       OR batch.result_fingerprint <> review_order.result_fingerprint
       OR batch.maker_account_id = batch.checker_account_id
       OR batch.candidate_count <> review_order.candidate_count
       OR batch.projectable_candidate_count <> review_order.projectable_candidate_count);

SELECT COUNT(*) AS invalid_review_rate_count
FROM settlement_batch batch
WHERE batch.create_mode = 'MANUAL_REVIEW'
  AND ((
      SELECT COUNT(*) FROM settlement_review_rate review_rate
      WHERE review_rate.review_order_no = batch.review_order_no
  ) = 0 OR (
      SELECT COUNT(*) FROM settlement_review_rate review_rate
      WHERE review_rate.review_order_no = batch.review_order_no
  ) <> (
      SELECT COUNT(*) FROM settlement_batch_rate batch_rate
      WHERE batch_rate.settlement_batch_no = batch.settlement_batch_no
        AND batch_rate.review_rate_id IS NOT NULL
  ));

SELECT COUNT(*) AS invalid_review_rate_inheritance
FROM settlement_batch batch
JOIN settlement_batch_rate batch_rate
  ON batch_rate.settlement_batch_no = batch.settlement_batch_no
LEFT JOIN settlement_review_rate review_rate
  ON review_rate.id = batch_rate.review_rate_id
 AND review_rate.review_order_no = batch.review_order_no
WHERE batch.create_mode = 'MANUAL_REVIEW'
  AND (review_rate.id IS NULL
       OR review_rate.source_currency <> batch_rate.source_currency
       OR review_rate.target_currency <> batch_rate.target_currency
       OR review_rate.direct_rate <> batch_rate.direct_rate
       OR review_rate.source_currency_exponent <> batch_rate.source_currency_exponent
       OR review_rate.target_currency_exponent <> batch_rate.target_currency_exponent);

SELECT COUNT(*) AS invalid_manual_fund_ledger_audit
FROM settlement_batch batch
JOIN merchant_fund_ledger ledger
  ON ledger.settlement_batch_no = batch.settlement_batch_no
WHERE batch.create_mode = 'MANUAL_REVIEW'
  AND (ledger.operation_mode <> 'MANUAL'
       OR ledger.operator_id <> batch.maker_account_id
       OR ledger.reviewer_id <> batch.checker_account_id
       OR ledger.operation_reason <> batch.maker_reason
       OR ledger.review_comment <> batch.checker_comment
       OR ledger.submit_time <> batch.maker_time
       OR ledger.review_time <> batch.checker_time);

SELECT COUNT(*) AS invalid_settlement_projection_count
FROM settlement_batch batch
WHERE batch.projectable_candidate_count <> (
    SELECT COUNT(*)
    FROM settlement_batch_candidate relation
    WHERE relation.settlement_batch_no = batch.settlement_batch_no
      AND relation.source_type = 'CLEARING_REVISION'
);

SELECT COUNT(*) AS invalid_review_projection_count
FROM settlement_batch batch
WHERE batch.create_mode = 'MANUAL_REVIEW'
  AND batch.projectable_candidate_count <> (
      SELECT COUNT(*)
      FROM settlement_batch_candidate relation
      WHERE relation.settlement_batch_no = batch.settlement_batch_no
        AND relation.source_type = 'CLEARING_REVISION'
  );
