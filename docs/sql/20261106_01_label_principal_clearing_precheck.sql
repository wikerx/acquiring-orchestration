-- 商户标签本金清分历史事实只读预检。
--
-- 目的：识别 ACTIVE 本金明细中币种、金额或 exponent 与当前动作标签口径不一致的数据，并按结算状态
--       区分后续治理方式。本脚本不会修改清分、候选、结算或账务数据。
-- 执行约束：仅允许在 DBA/研发确认的只读连接执行；第 1 节返回缺表时停止，不得跳过门禁继续执行。
-- 修复约束：未入账数据使用受审的新清分修订；POSTED 数据只能走冲正/调整，不得 UPDATE 历史资金事实。

-- =============================================================================
-- 1. 表存在性门禁：必须返回 0 行
-- =============================================================================

SELECT expected.table_name AS missing_table_name
FROM (
    SELECT 'transaction_clearing_detail' AS table_name
    UNION ALL SELECT 'transaction_clearing_detail_202603'
    UNION ALL SELECT 'transaction_clearing_detail_202604'
    UNION ALL SELECT 'settlement_candidate'
) expected
LEFT JOIN information_schema.tables actual
       ON actual.table_schema = DATABASE()
      AND actual.table_name = expected.table_name
WHERE actual.table_name IS NULL;

-- =============================================================================
-- 2. 异常分布汇总：所有计数为 0 才表示历史 ACTIVE 本金符合标签口径
-- =============================================================================

WITH principal_fact AS (
    SELECT 'template' AS shard_name, detail.*
    FROM transaction_clearing_detail detail
    WHERE detail.record_status = 'ACTIVE'
      AND detail.item_type = 'PRINCIPAL'
    UNION ALL
    SELECT '202603' AS shard_name, detail.*
    FROM transaction_clearing_detail_202603 detail
    WHERE detail.record_status = 'ACTIVE'
      AND detail.item_type = 'PRINCIPAL'
    UNION ALL
    SELECT '202604' AS shard_name, detail.*
    FROM transaction_clearing_detail_202604 detail
    WHERE detail.record_status = 'ACTIVE'
      AND detail.item_type = 'PRINCIPAL'
), mismatch AS (
    SELECT principal.*,
           candidate.candidate_status,
           CASE
               WHEN candidate.candidate_status = 'POSTED'
                   THEN 'POSTED_REVERSAL_OR_ADJUSTMENT'
               WHEN candidate.candidate_status IS NULL
                   THEN 'NO_CANDIDATE_REVIEW_AND_RECALCULATE'
               ELSE 'UNPOSTED_REVIEW_AND_NEW_CLEARING_REVISION'
           END AS remediation_class
    FROM principal_fact principal
    LEFT JOIN settlement_candidate candidate
           ON candidate.source_type = 'CLEARING_REVISION'
          AND candidate.source_business_id = principal.finance_state_id
          AND candidate.source_revision = principal.clearing_revision
    WHERE UPPER(principal.currency) <> UPPER(principal.label_currency)
       OR principal.amount <> principal.label_amount
       OR principal.currency_exponent <> principal.label_currency_exponent
)
SELECT shard_name,
       COALESCE(candidate_status, 'NO_CANDIDATE') AS candidate_status,
       remediation_class,
       COUNT(*) AS mismatch_count
FROM mismatch
GROUP BY shard_name, COALESCE(candidate_status, 'NO_CANDIDATE'), remediation_class
ORDER BY shard_name, candidate_status, remediation_class;

-- =============================================================================
-- 3. 未入账异常明细：评审候选/批次状态后创建新清分修订，禁止原地覆盖
-- =============================================================================

WITH principal_fact AS (
    SELECT 'template' AS shard_name, detail.*
    FROM transaction_clearing_detail detail
    WHERE detail.record_status = 'ACTIVE'
      AND detail.item_type = 'PRINCIPAL'
    UNION ALL
    SELECT '202603' AS shard_name, detail.*
    FROM transaction_clearing_detail_202603 detail
    WHERE detail.record_status = 'ACTIVE'
      AND detail.item_type = 'PRINCIPAL'
    UNION ALL
    SELECT '202604' AS shard_name, detail.*
    FROM transaction_clearing_detail_202604 detail
    WHERE detail.record_status = 'ACTIVE'
      AND detail.item_type = 'PRINCIPAL'
)
SELECT principal.shard_name,
       principal.clearing_detail_no,
       principal.finance_state_id,
       principal.transaction_id,
       principal.operation_id,
       principal.merchant_id,
       principal.clearing_revision,
       principal.currency AS principal_currency,
       principal.amount AS principal_amount,
       principal.currency_exponent AS principal_currency_exponent,
       principal.label_currency,
       principal.label_amount,
       principal.label_currency_exponent,
       COALESCE(candidate.candidate_status, 'NO_CANDIDATE') AS candidate_status,
       candidate.candidate_no,
       candidate.settlement_batch_no,
       'REVIEW CANDIDATE/BATCH, THEN CREATE A NEW CLEARING REVISION' AS remediation_action
FROM principal_fact principal
LEFT JOIN settlement_candidate candidate
       ON candidate.source_type = 'CLEARING_REVISION'
      AND candidate.source_business_id = principal.finance_state_id
      AND candidate.source_revision = principal.clearing_revision
WHERE (UPPER(principal.currency) <> UPPER(principal.label_currency)
    OR principal.amount <> principal.label_amount
    OR principal.currency_exponent <> principal.label_currency_exponent)
  AND (candidate.candidate_status IS NULL OR candidate.candidate_status <> 'POSTED')
ORDER BY principal.transaction_date_time, principal.transaction_id, principal.clearing_revision;

-- =============================================================================
-- 4. 已入账异常明细：只能走已审核的冲正/调整，禁止重算覆盖或直接更新
-- =============================================================================

WITH principal_fact AS (
    SELECT 'template' AS shard_name, detail.*
    FROM transaction_clearing_detail detail
    WHERE detail.record_status = 'ACTIVE'
      AND detail.item_type = 'PRINCIPAL'
    UNION ALL
    SELECT '202603' AS shard_name, detail.*
    FROM transaction_clearing_detail_202603 detail
    WHERE detail.record_status = 'ACTIVE'
      AND detail.item_type = 'PRINCIPAL'
    UNION ALL
    SELECT '202604' AS shard_name, detail.*
    FROM transaction_clearing_detail_202604 detail
    WHERE detail.record_status = 'ACTIVE'
      AND detail.item_type = 'PRINCIPAL'
)
SELECT principal.shard_name,
       principal.clearing_detail_no,
       principal.finance_state_id,
       principal.transaction_id,
       principal.operation_id,
       principal.merchant_id,
       principal.clearing_revision,
       principal.currency AS principal_currency,
       principal.amount AS principal_amount,
       principal.currency_exponent AS principal_currency_exponent,
       principal.label_currency,
       principal.label_amount,
       principal.label_currency_exponent,
       candidate.candidate_no,
       candidate.settlement_batch_no,
       candidate.posted_time,
       'CREATE AN AUDITED REVERSAL OR ADJUSTMENT; NEVER UPDATE THE POSTED FACT' AS remediation_action
FROM principal_fact principal
JOIN settlement_candidate candidate
  ON candidate.source_type = 'CLEARING_REVISION'
 AND candidate.source_business_id = principal.finance_state_id
 AND candidate.source_revision = principal.clearing_revision
 AND candidate.candidate_status = 'POSTED'
WHERE UPPER(principal.currency) <> UPPER(principal.label_currency)
   OR principal.amount <> principal.label_amount
   OR principal.currency_exponent <> principal.label_currency_exponent
ORDER BY principal.transaction_date_time, principal.transaction_id, principal.clearing_revision;
