-- 收单交易清分影子验收只读草案，当前覆盖 2026 年第 3 季度物理表。
-- 状态：仅供影子灰度核对；禁止在本文件增加 DML、DDL、锁表、临时表或数据修复语句。
-- 执行：只允许在受控只读账号和只读副本执行。先核对 EXPLAIN、复制延迟和时间窗口，再逐段运行。
-- 币种：任何金额结果必须保留 currency 维度，禁止把 USD、EUR、JPY 等不同币种直接相加。
-- 扩展：进入新季度前复制本文件并评审替换所有 _202603 后缀和半开时间范围，不使用动态表名。

SET @begin_time = '2026-07-01 00:00:00.000';
SET @end_time = '2026-10-01 00:00:00.000';
SET @published_detail_end_time = '2027-01-01 00:00:00.000';
SET @merchant_id = NULL;
SET @stale_before = DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 15 MINUTE);

-- 1. 影子窗口状态分布。用于记录扫描基数，不作为通过/失败的单独结论。
SELECT clearing_status,
       COUNT(*) AS action_count,
       MIN(COALESCE(clearing_request_time, create_time)) AS oldest_request_time,
       MAX(update_time) AS latest_update_time
FROM transaction_finance_state_202603
WHERE transaction_date_time >= @begin_time
  AND transaction_date_time < @end_time
  AND deleted = 0
  AND (@merchant_id IS NULL OR merchant_id = @merchant_id)
GROUP BY clearing_status
ORDER BY clearing_status;

-- 2. 超过 15 分钟的待处理、失败和人工复核积压。结果必须逐条有可解释的重试或处置状态。
SELECT transaction_id, operation_id, merchant_id, transaction_type,
       clearing_status, clearing_retry_count, next_retry_time,
       last_failure_code, processing_deadline, transaction_date_time,
       TIMESTAMPDIFF(SECOND, COALESCE(clearing_request_time, create_time), UTC_TIMESTAMP(3)) AS pending_seconds
FROM transaction_finance_state_202603
WHERE transaction_date_time >= @begin_time
  AND transaction_date_time < @end_time
  AND deleted = 0
  AND clearing_status IN ('PENDING', 'PROCESSING', 'WAITING_SOURCE', 'FAILED', 'MANUAL_REVIEW')
  AND COALESCE(clearing_request_time, create_time) < @stale_before
  AND (@merchant_id IS NULL OR merchant_id = @merchant_id)
ORDER BY COALESCE(clearing_request_time, create_time), id
LIMIT 200;

-- 3. CLEARED 汇总必须存在当前修订 ACTIVE 交易清分明细。结果必须为空。
SELECT f.transaction_id, f.finance_state_id, f.clearing_revision,
       f.transaction_type, f.label_currency, f.transaction_date_time
FROM transaction_finance_state_202603 f
LEFT JOIN transaction_clearing_detail_202603 d
  ON d.finance_state_id = f.finance_state_id
 AND d.transaction_id = f.transaction_id
 AND d.clearing_revision = f.clearing_revision
 AND d.record_status = 'ACTIVE'
 AND d.transaction_date_time = f.transaction_date_time
WHERE f.transaction_date_time >= @begin_time
  AND f.transaction_date_time < @end_time
  AND f.deleted = 0
  AND f.clearing_status = 'CLEARED'
  AND (@merchant_id IS NULL OR f.merchant_id = @merchant_id)
GROUP BY f.transaction_id, f.finance_state_id, f.clearing_revision,
         f.transaction_type, f.label_currency, f.transaction_date_time
HAVING COUNT(d.id) = 0
LIMIT 200;

-- 4. 标签币种本金、平台费用和费用返还汇总必须与当前 ACTIVE 明细一致。结果必须为空。
SELECT f.transaction_id, f.finance_state_id, f.clearing_revision, f.label_currency,
       f.gross_label_amount AS summary_principal,
       COALESCE(x.principal_amount, 0) AS detail_principal,
       f.platform_fee_amount AS summary_platform_fee,
       COALESCE(x.platform_fee_amount, 0) AS detail_platform_fee,
       f.fee_reversal_amount AS summary_fee_reversal,
       COALESCE(x.fee_reversal_amount, 0) AS detail_fee_reversal
FROM transaction_finance_state_202603 f
LEFT JOIN (
    SELECT finance_state_id, clearing_revision, label_currency,
           SUM(CASE
                   WHEN item_type = 'PRINCIPAL' AND direction = 'CREDIT' THEN amount
                   WHEN item_type = 'PRINCIPAL' AND direction = 'DEBIT' THEN -amount
                   ELSE 0
               END) AS principal_amount,
           SUM(CASE
                   WHEN item_type = 'PLATFORM_FEE' AND currency = label_currency THEN amount
                   ELSE 0
               END) AS platform_fee_amount,
           SUM(CASE
                   WHEN item_type = 'FEE_REVERSAL' AND currency = label_currency THEN amount
                   ELSE 0
               END) AS fee_reversal_amount
    FROM transaction_clearing_detail_202603
    WHERE transaction_date_time >= @begin_time
      AND transaction_date_time < @end_time
      AND record_status = 'ACTIVE'
    GROUP BY finance_state_id, clearing_revision, label_currency
) x
  ON x.finance_state_id = f.finance_state_id
 AND x.clearing_revision = f.clearing_revision
 AND x.label_currency = f.label_currency
WHERE f.transaction_date_time >= @begin_time
  AND f.transaction_date_time < @end_time
  AND f.deleted = 0
  AND f.clearing_status = 'CLEARED'
  AND (@merchant_id IS NULL OR f.merchant_id = @merchant_id)
  AND (
      COALESCE(f.gross_label_amount, 0) <> COALESCE(x.principal_amount, 0)
      OR COALESCE(f.platform_fee_amount, 0) <> COALESCE(x.platform_fee_amount, 0)
      OR COALESCE(f.fee_reversal_amount, 0) <> COALESCE(x.fee_reversal_amount, 0)
  )
ORDER BY f.transaction_date_time, f.id
LIMIT 200;

-- 5. 结算输入预览：按支付方式、交易类型、组件币种、项目和方向分别统计，禁止跨币种净额化。
SELECT COALESCE(p.payment_method, 'UNKNOWN') AS payment_type,
       COALESCE(p.payment_brand, 'UNKNOWN') AS payment_method,
       d.transaction_type, d.item_type, d.fee_category, d.risk_service_type,
       d.direction, d.currency,
       COUNT(DISTINCT d.transaction_id) AS transaction_count,
       COUNT(*) AS detail_count,
       SUM(d.amount) AS amount
FROM transaction_clearing_detail_202603 d
LEFT JOIN transaction_payment_method_info_202603 p
 ON p.transaction_id = d.transaction_id
 AND p.transaction_date_time = d.transaction_date_time
WHERE d.transaction_date_time >= @begin_time
  AND d.transaction_date_time < @end_time
  AND d.record_status = 'ACTIVE'
  AND (@merchant_id IS NULL OR d.merchant_id = @merchant_id)
GROUP BY COALESCE(p.payment_method, 'UNKNOWN'), COALESCE(p.payment_brand, 'UNKNOWN'),
         d.transaction_type, d.item_type, d.fee_category, d.risk_service_type,
         d.direction, d.currency
ORDER BY d.currency, payment_type, payment_method, d.transaction_type,
         d.item_type, d.fee_category, d.direction;

-- 6. 保证金状态守恒：扣留 = 已返还 + 已释放 + 剩余。结果必须为空。
SELECT reserve_state_id, original_transaction_id, merchant_id, reserve_currency,
       retained_amount, returned_amount, released_amount, remaining_amount,
       reserve_status, transaction_date_time
FROM transaction_reserve_clearing_state_202603
WHERE transaction_date_time >= @begin_time
  AND transaction_date_time < @end_time
  AND (@merchant_id IS NULL OR merchant_id = @merchant_id)
  AND (
      retained_amount < 0 OR returned_amount < 0 OR released_amount < 0 OR remaining_amount < 0
      OR retained_amount <> returned_amount + released_amount + remaining_amount
      OR returned_amount + released_amount > retained_amount
  )
ORDER BY transaction_date_time, id
LIMIT 200;

-- 7. 保证金当前状态必须等于独立 ACTIVE 保证金明细累计。结果必须为空。
-- 退款 RETURN 明细跟随当前退款动作分片，所以必须联合全部已发布动作季度；新季度发布时继续扩展静态 UNION ALL。
SELECT s.reserve_state_id, s.original_transaction_id, s.original_hold_detail_no,
       s.reserve_currency,
       s.retained_amount AS state_retained_amount,
       COALESCE(r.detail_retained_amount, 0) AS detail_retained_amount,
       s.returned_amount AS state_returned_amount,
       COALESCE(r.detail_returned_amount, 0) AS detail_returned_amount,
       s.released_amount AS state_released_amount,
       COALESCE(r.detail_released_amount, 0) AS detail_released_amount,
       s.remaining_amount
FROM transaction_reserve_clearing_state_202603 s
LEFT JOIN (
    SELECT COALESCE(source_reserve_detail_no, reserve_clearing_detail_no) AS hold_detail_no,
           reserve_currency,
           SUM(retained_amount) AS detail_retained_amount,
           SUM(returned_amount) AS detail_returned_amount,
           SUM(released_amount) AS detail_released_amount
    FROM (
        SELECT reserve_clearing_detail_no, source_reserve_detail_no, reserve_currency,
               retained_amount, returned_amount, released_amount
        FROM transaction_reserve_clearing_detail_202603
        WHERE transaction_date_time >= @begin_time
          AND transaction_date_time < @end_time
          AND record_status = 'ACTIVE'
        UNION ALL
        SELECT reserve_clearing_detail_no, source_reserve_detail_no, reserve_currency,
               retained_amount, returned_amount, released_amount
        FROM transaction_reserve_clearing_detail_202604
        WHERE transaction_date_time >= @end_time
          AND transaction_date_time < @published_detail_end_time
          AND record_status = 'ACTIVE'
    ) published_reserve_detail
    GROUP BY COALESCE(source_reserve_detail_no, reserve_clearing_detail_no), reserve_currency
) r
  ON r.hold_detail_no = s.original_hold_detail_no
 AND r.reserve_currency = s.reserve_currency
WHERE s.transaction_date_time >= @begin_time
  AND s.transaction_date_time < @end_time
  AND (@merchant_id IS NULL OR s.merchant_id = @merchant_id)
  AND (
      s.retained_amount <> COALESCE(r.detail_retained_amount, 0)
      OR s.returned_amount <> COALESCE(r.detail_returned_amount, 0)
      OR s.released_amount <> COALESCE(r.detail_released_amount, 0)
      OR s.remaining_amount < 0
  )
ORDER BY s.transaction_date_time, s.id
LIMIT 200;

-- 8. 保证金容量：只按标签币种分别统计剩余负债，不生成跨币种合计。
SELECT reserve_currency,
       COUNT(*) AS open_reserve_count,
       SUM(retained_amount) AS retained_amount,
       SUM(returned_amount) AS returned_amount,
       SUM(released_amount) AS released_amount,
       SUM(remaining_amount) AS remaining_amount
FROM transaction_reserve_clearing_state_202603
WHERE transaction_date_time >= @begin_time
  AND transaction_date_time < @end_time
  AND (@merchant_id IS NULL OR merchant_id = @merchant_id)
GROUP BY reserve_currency
ORDER BY reserve_currency;

-- 9. 同一清分修订不得存在重复 ACTIVE 交易项目。结果必须为空。
SELECT transaction_id, finance_state_id, clearing_revision, item_code,
       COUNT(*) AS active_duplicate_count
FROM transaction_clearing_detail_202603
WHERE transaction_date_time >= @begin_time
  AND transaction_date_time < @end_time
  AND record_status = 'ACTIVE'
  AND (@merchant_id IS NULL OR merchant_id = @merchant_id)
GROUP BY transaction_id, finance_state_id, clearing_revision, item_code
HAVING COUNT(*) > 1
LIMIT 200;

-- 10. 同一清分修订不得存在重复 ACTIVE 保证金项目。结果必须为空。
SELECT transaction_id, finance_state_id, clearing_revision, item_code,
       COUNT(*) AS active_duplicate_count
FROM transaction_reserve_clearing_detail_202603
WHERE transaction_date_time >= @begin_time
  AND transaction_date_time < @end_time
  AND record_status = 'ACTIVE'
  AND (@merchant_id IS NULL OR merchant_id = @merchant_id)
GROUP BY transaction_id, finance_state_id, clearing_revision, item_code
HAVING COUNT(*) > 1
LIMIT 200;

-- 11. 每个动作最多只能有一个 ACTIVE 修订，且必须等于 finance state 当前修订。结果必须为空。
SELECT f.transaction_id, f.finance_state_id, f.clearing_revision AS state_revision,
       MIN(d.clearing_revision) AS min_active_revision,
       MAX(d.clearing_revision) AS max_active_revision,
       COUNT(DISTINCT d.clearing_revision) AS active_revision_count
FROM transaction_finance_state_202603 f
JOIN transaction_clearing_detail_202603 d
  ON d.finance_state_id = f.finance_state_id
 AND d.transaction_id = f.transaction_id
 AND d.transaction_date_time = f.transaction_date_time
 AND d.record_status = 'ACTIVE'
WHERE f.transaction_date_time >= @begin_time
  AND f.transaction_date_time < @end_time
  AND f.deleted = 0
  AND (@merchant_id IS NULL OR f.merchant_id = @merchant_id)
GROUP BY f.transaction_id, f.finance_state_id, f.clearing_revision
HAVING COUNT(DISTINCT d.clearing_revision) <> 1
    OR MIN(d.clearing_revision) <> f.clearing_revision
    OR MAX(d.clearing_revision) <> f.clearing_revision
LIMIT 200;

-- 12. 清分采用的费用版本身份和 hash 必须等于交易动作冻结快照。结果必须为空。
SELECT f.transaction_id, f.finance_state_id,
       f.fee_plan_id AS clearing_fee_plan_id,
       s.fee_plan_id AS snapshot_fee_plan_id,
       f.fee_plan_version_id AS clearing_version_id,
       s.fee_plan_version_id AS snapshot_version_id,
       f.fee_plan_version_no AS clearing_version_no,
       s.fee_plan_version_no AS snapshot_version_no,
       f.fee_snapshot_hash AS clearing_snapshot_hash,
       s.fee_snapshot_hash AS action_snapshot_hash,
       f.transaction_date_time
FROM transaction_finance_state_202603 f
LEFT JOIN transaction_merchant_snapshot_202603 s
  ON s.transaction_id = f.transaction_id
 AND s.transaction_date_time = f.transaction_date_time
WHERE f.transaction_date_time >= @begin_time
  AND f.transaction_date_time < @end_time
  AND f.deleted = 0
  AND f.clearing_status = 'CLEARED'
  AND (@merchant_id IS NULL OR f.merchant_id = @merchant_id)
  AND (
      s.id IS NULL
      OR NOT (f.fee_plan_id <=> s.fee_plan_id)
      OR NOT (f.fee_plan_version_id <=> s.fee_plan_version_id)
      OR NOT (f.fee_plan_version_no <=> s.fee_plan_version_no)
      OR NOT (f.fee_snapshot_hash <=> s.fee_snapshot_hash)
  )
ORDER BY f.transaction_date_time, f.id
LIMIT 200;

-- 13. 费用币种语义：百分比必须按标签币种，固定费和上下限必须使用 USD。结果必须为空。
SELECT clearing_detail_no, transaction_id, fee_category, component_type,
       label_currency, basis_currency, currency,
       percentage_rate, fixed_amount_usd, minimum_amount_usd, maximum_amount_usd,
       limit_evaluation_status, transaction_date_time
FROM transaction_clearing_detail_202603
WHERE transaction_date_time >= @begin_time
  AND transaction_date_time < @end_time
  AND record_status = 'ACTIVE'
  AND item_type IN ('PLATFORM_FEE', 'FEE_REVERSAL')
  AND (@merchant_id IS NULL OR merchant_id = @merchant_id)
  AND (
      (component_type = 'PERCENTAGE'
       AND (basis_currency <> label_currency OR currency <> label_currency))
      OR (component_type IN ('FIXED', 'LIMIT_ADJUSTMENT')
          AND (basis_currency <> 'USD' OR currency <> 'USD'))
      OR (component_type = 'FIXED' AND fixed_amount_usd IS NULL)
      OR (applied_limit = 'MINIMUM' AND minimum_amount_usd IS NULL)
      OR (applied_limit = 'MAXIMUM' AND maximum_amount_usd IS NULL)
  )
ORDER BY transaction_date_time, id
LIMIT 200;

-- 14. 清分阶段不得写入结算汇率或最终结算金额。影子窗口结果必须为空。
SELECT transaction_id, finance_state_id, clearing_revision, settlement_status,
       settlement_rate, settlement_amount, settlement_fee_amount,
       net_settlement_amount, fee_evaluation_status, transaction_date_time
FROM transaction_finance_state_202603
WHERE transaction_date_time >= @begin_time
  AND transaction_date_time < @end_time
  AND deleted = 0
  AND clearing_status IN ('CLEARED', 'NOT_REQUIRED')
  AND (@merchant_id IS NULL OR merchant_id = @merchant_id)
  AND (
      settlement_rate IS NOT NULL
      OR settlement_amount IS NOT NULL
      OR settlement_fee_amount IS NOT NULL
      OR (fee_evaluation_status = 'PENDING_SETTLEMENT_RATE' AND net_settlement_amount IS NOT NULL)
  )
ORDER BY transaction_date_time, id
LIMIT 200;

-- 15. 已 POSTED 的结算修订不得再被清分重算覆盖。结算前已完成的合法重算不应误报；结果必须为空。
SELECT f.transaction_id, f.finance_state_id, f.clearing_revision,
       f.settlement_status,
       current_candidate.candidate_no AS current_posted_candidate_no,
       current_candidate.source_revision AS posted_revision,
       current_candidate.posted_time,
       COUNT(prior_posted.id) AS prior_posted_revision_count,
       f.transaction_date_time
FROM transaction_finance_state_202603 f
LEFT JOIN settlement_candidate current_candidate
  ON current_candidate.source_type = 'CLEARING_REVISION'
 AND current_candidate.source_business_id = f.finance_state_id
 AND current_candidate.source_revision = f.clearing_revision
 AND current_candidate.candidate_status = 'POSTED'
LEFT JOIN settlement_candidate prior_posted
  ON prior_posted.source_type = 'CLEARING_REVISION'
 AND prior_posted.source_business_id = f.finance_state_id
 AND prior_posted.source_revision < f.clearing_revision
 AND prior_posted.candidate_status = 'POSTED'
WHERE f.transaction_date_time >= @begin_time
  AND f.transaction_date_time < @end_time
  AND f.deleted = 0
  AND f.settlement_status <> 'NOT_SETTLED'
  AND (@merchant_id IS NULL OR f.merchant_id = @merchant_id)
GROUP BY f.transaction_id, f.finance_state_id, f.clearing_revision,
         f.settlement_status, current_candidate.candidate_no,
         current_candidate.source_revision, current_candidate.posted_time,
         f.clearing_complete_time, f.transaction_date_time
HAVING current_candidate.candidate_no IS NULL
    OR current_candidate.posted_time IS NULL
    OR f.clearing_complete_time > current_candidate.posted_time
    OR COUNT(prior_posted.id) > 0
LIMIT 200;

-- 16. 交易清分明细不得混入保证金项目或保证金币种字段语义。结果必须为空。
SELECT clearing_detail_no, transaction_id, item_type, item_code,
       fee_category, component_type, currency, transaction_date_time
FROM transaction_clearing_detail_202603
WHERE transaction_date_time >= @begin_time
  AND transaction_date_time < @end_time
  AND record_status = 'ACTIVE'
  AND (@merchant_id IS NULL OR merchant_id = @merchant_id)
  AND (
      item_code LIKE 'RESERVE:%'
      OR item_name LIKE '%保证金%'
      OR fee_category = 'RESERVE'
  )
ORDER BY transaction_date_time, id
LIMIT 200;

-- 17. 保证金明细只允许标签币种 HOLD/RETURN/RELEASE/ADJUSTMENT，不得携带汇率。结果必须为空。
SELECT r.reserve_clearing_detail_no, r.transaction_id, r.original_transaction_id,
       r.reserve_action_type, r.reserve_currency, f.label_currency,
       r.retained_amount, r.returned_amount, r.released_amount,
       r.remaining_amount, r.transaction_date_time
FROM transaction_reserve_clearing_detail_202603 r
LEFT JOIN transaction_finance_state_202603 f
  ON f.transaction_id = r.transaction_id
 AND f.transaction_date_time = r.transaction_date_time
 AND f.deleted = 0
WHERE r.transaction_date_time >= @begin_time
  AND r.transaction_date_time < @end_time
  AND r.record_status = 'ACTIVE'
  AND (@merchant_id IS NULL OR r.merchant_id = @merchant_id)
  AND (
      f.id IS NULL
      OR r.reserve_currency <> f.label_currency
      OR r.reserve_action_type NOT IN ('HOLD', 'RETURN', 'RELEASE', 'ADJUSTMENT')
      OR r.reserve_rate < 0 OR r.reserve_rate > 100
  )
ORDER BY r.transaction_date_time, r.id
LIMIT 200;

-- 18. 清分失败码和人工复核分布。每个非零分组必须有负责人和处置结论。
SELECT clearing_status, last_failure_code,
       COUNT(*) AS action_count,
       MIN(COALESCE(clearing_request_time, create_time)) AS oldest_request_time,
       MAX(clearing_retry_count) AS max_retry_count
FROM transaction_finance_state_202603
WHERE transaction_date_time >= @begin_time
  AND transaction_date_time < @end_time
  AND deleted = 0
  AND clearing_status IN ('WAITING_SOURCE', 'FAILED', 'MANUAL_REVIEW')
  AND (@merchant_id IS NULL OR merchant_id = @merchant_id)
GROUP BY clearing_status, last_failure_code
ORDER BY clearing_status, action_count DESC, last_failure_code;

-- 19. 抽样审计：明细保留费用配置公式、固定 USD 金额、上下限和原币种计算结果。
SELECT d.transaction_id, d.operation_id, d.merchant_id, d.transaction_type,
       d.clearing_revision, d.line_no, d.item_type, d.fee_category,
       d.risk_service_type, d.component_type, d.label_currency, d.label_amount,
       d.percentage_rate, d.fixed_amount_usd, d.minimum_amount_usd, d.maximum_amount_usd,
       d.applied_limit, d.limit_evaluation_status,
       d.basis_currency, d.basis_amount, d.currency, d.amount,
       d.fee_plan_version_id, d.fee_rule_id, d.fee_rule_tier_id,
       d.fee_snapshot_hash, d.formula_snapshot, d.transaction_date_time
FROM transaction_clearing_detail_202603 d
WHERE d.transaction_date_time >= @begin_time
  AND d.transaction_date_time < @end_time
  AND d.record_status = 'ACTIVE'
  AND (@merchant_id IS NULL OR d.merchant_id = @merchant_id)
ORDER BY d.transaction_date_time DESC, d.transaction_id, d.line_no
LIMIT 200;
