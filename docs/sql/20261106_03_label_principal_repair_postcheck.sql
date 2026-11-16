-- 未结算历史标签本金修复通道结构与数据一致性后检。
-- 每个检查结果都必须返回 0；本脚本只读。

-- 1. 目标表缺失数。
SELECT COUNT(*) AS missing_table_count
FROM (
    SELECT expected.table_name
    FROM (
        SELECT 'clearing_label_principal_repair' AS table_name
        UNION ALL SELECT 'clearing_label_principal_repair_dependency_audit'
        UNION ALL SELECT 'clearing_label_principal_repair_reserve_state_audit'
    ) expected
    LEFT JOIN information_schema.tables actual
      ON actual.table_schema = DATABASE()
     AND actual.table_name = expected.table_name
     AND actual.engine = 'InnoDB'
    WHERE actual.table_name IS NULL
) missing_table;

-- 2. 关键列类型、精度或空值约束不匹配数。
SELECT COUNT(*) AS invalid_column_count
FROM (
    SELECT 'clearing_label_principal_repair' AS table_name,
           'principal_amount_before' AS column_name, 'decimal' AS data_type,
           24 AS numeric_precision, 8 AS numeric_scale, NULL AS datetime_precision,
           NULL AS character_maximum_length, 'NO' AS is_nullable
    UNION ALL SELECT 'clearing_label_principal_repair', 'label_amount', 'decimal', 24, 8, NULL, NULL, 'NO'
    UNION ALL SELECT 'clearing_label_principal_repair', 'reserve_fact_count', 'tinyint', NULL, NULL, NULL, NULL, 'NO'
    UNION ALL SELECT 'clearing_label_principal_repair', 'reserve_snapshot_hash', 'char', NULL, NULL, NULL, 64, 'NO'
    UNION ALL SELECT 'clearing_label_principal_repair', 'reserve_state_count', 'tinyint', NULL, NULL, NULL, NULL, 'NO'
    UNION ALL SELECT 'clearing_label_principal_repair', 'reserve_state_snapshot_hash', 'char', NULL, NULL, NULL, 64, 'NO'
    UNION ALL SELECT 'clearing_label_principal_repair', 'dependency_count', 'tinyint', NULL, NULL, NULL, NULL, 'NO'
    UNION ALL SELECT 'clearing_label_principal_repair', 'dependency_snapshot_hash', 'char', NULL, NULL, NULL, 64, 'NO'
    UNION ALL SELECT 'clearing_label_principal_repair', 'new_candidate_id', 'bigint', NULL, NULL, NULL, NULL, 'YES'
    UNION ALL SELECT 'clearing_label_principal_repair', 'transaction_date_time', 'datetime', NULL, NULL, 3, NULL, 'NO'
    UNION ALL SELECT 'clearing_label_principal_repair', 'review_time', 'datetime', NULL, NULL, 3, NULL, 'YES'
    UNION ALL SELECT 'clearing_label_principal_repair', 'executed_time', 'datetime', NULL, NULL, 3, NULL, 'YES'
    UNION ALL SELECT 'clearing_label_principal_repair', 'create_time', 'datetime', NULL, NULL, 3, NULL, 'NO'
    UNION ALL SELECT 'clearing_label_principal_repair', 'update_time', 'datetime', NULL, NULL, 3, NULL, 'NO'
    UNION ALL SELECT 'clearing_label_principal_repair_dependency_audit', 'migrated_time', 'datetime', NULL, NULL, 3, NULL, 'NO'
    UNION ALL SELECT 'clearing_label_principal_repair_dependency_audit', 'create_time', 'datetime', NULL, NULL, 3, NULL, 'NO'
    UNION ALL SELECT 'clearing_label_principal_repair_reserve_state_audit', 'state_snapshot_hash', 'char', NULL, NULL, NULL, 64, 'NO'
    UNION ALL SELECT 'clearing_label_principal_repair_reserve_state_audit', 'migrated_time', 'datetime', NULL, NULL, 3, NULL, 'NO'
    UNION ALL SELECT 'clearing_label_principal_repair_reserve_state_audit', 'create_time', 'datetime', NULL, NULL, 3, NULL, 'NO'
) expected
LEFT JOIN information_schema.columns actual
       ON actual.table_schema = DATABASE()
      AND actual.table_name = expected.table_name
      AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL
   OR actual.data_type <> expected.data_type
   OR (expected.numeric_precision IS NOT NULL
       AND actual.numeric_precision <> expected.numeric_precision)
   OR (expected.numeric_scale IS NOT NULL
       AND actual.numeric_scale <> expected.numeric_scale)
   OR (expected.datetime_precision IS NOT NULL
       AND actual.datetime_precision <> expected.datetime_precision)
   OR (expected.character_maximum_length IS NOT NULL
       AND actual.character_maximum_length <> expected.character_maximum_length)
   OR actual.is_nullable <> expected.is_nullable;

-- 3. 必需唯一键、查询索引或CHECK约束缺失数。
SELECT COUNT(*) AS missing_contract_count
FROM (
    SELECT 'clearing_label_principal_repair' AS table_name,
           'INDEX' AS contract_type, 'uk_label_principal_repair_no' AS contract_name
    UNION ALL SELECT 'clearing_label_principal_repair', 'INDEX', 'uk_label_principal_repair_request'
    UNION ALL SELECT 'clearing_label_principal_repair', 'INDEX', 'idx_label_principal_repair_transaction'
    UNION ALL SELECT 'clearing_label_principal_repair', 'INDEX', 'idx_label_principal_repair_finance'
    UNION ALL SELECT 'clearing_label_principal_repair', 'INDEX', 'idx_label_principal_repair_status'
    UNION ALL SELECT 'clearing_label_principal_repair', 'CHECK', 'chk_label_principal_repair_value'
    UNION ALL SELECT 'clearing_label_principal_repair', 'CHECK', 'chk_label_principal_repair_state'
    UNION ALL SELECT 'clearing_label_principal_repair_dependency_audit', 'INDEX', 'uk_label_principal_dependency_repair'
    UNION ALL SELECT 'clearing_label_principal_repair_dependency_audit', 'INDEX', 'idx_label_principal_dependency_old'
    UNION ALL SELECT 'clearing_label_principal_repair_dependency_audit', 'INDEX', 'idx_label_principal_dependency_new'
    UNION ALL SELECT 'clearing_label_principal_repair_dependency_audit', 'CHECK', 'chk_label_principal_dependency_audit'
    UNION ALL SELECT 'clearing_label_principal_repair_reserve_state_audit', 'INDEX', 'uk_label_principal_reserve_state_repair'
    UNION ALL SELECT 'clearing_label_principal_repair_reserve_state_audit', 'INDEX', 'idx_label_principal_reserve_state_old'
    UNION ALL SELECT 'clearing_label_principal_repair_reserve_state_audit', 'INDEX', 'idx_label_principal_reserve_state_new'
    UNION ALL SELECT 'clearing_label_principal_repair_reserve_state_audit', 'CHECK', 'chk_label_principal_reserve_state_audit'
) expected
LEFT JOIN (
    SELECT table_name, 'INDEX' AS contract_type, index_name AS contract_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name IN ('clearing_label_principal_repair',
                         'clearing_label_principal_repair_dependency_audit',
                         'clearing_label_principal_repair_reserve_state_audit')
    UNION ALL
    SELECT table_name, 'CHECK', constraint_name
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name IN ('clearing_label_principal_repair',
                         'clearing_label_principal_repair_dependency_audit',
                         'clearing_label_principal_repair_reserve_state_audit')
      AND constraint_type = 'CHECK'
) actual
       ON actual.table_name = expected.table_name
      AND actual.contract_type = expected.contract_type
      AND actual.contract_name = expected.contract_name
WHERE actual.contract_name IS NULL;

-- 4. 已存在审批事实的值域或状态组合异常数。
SELECT COUNT(*) AS invalid_repair_row_count
FROM clearing_label_principal_repair
WHERE expected_finance_state_version < 0
   OR expected_clearing_revision < 1
   OR candidate_id <= 0
   OR expected_candidate_version < 0
   OR principal_amount_before < 0
   OR label_amount < 0
   OR principal_currency_exponent_before NOT BETWEEN 0 AND 8
   OR label_currency_exponent NOT BETWEEN 0 AND 8
   OR reserve_fact_count NOT BETWEEN 0 AND 1
   OR reserve_state_count NOT BETWEEN 0 AND 1
   OR reserve_state_count <> reserve_fact_count
   OR dependency_count NOT BETWEEN 0 AND 1
   OR version < 0
   OR source_snapshot_hash NOT REGEXP '^[0-9a-f]{64}$'
   OR reserve_snapshot_hash NOT REGEXP '^[0-9a-f]{64}$'
   OR reserve_state_snapshot_hash NOT REGEXP '^[0-9a-f]{64}$'
   OR dependency_snapshot_hash NOT REGEXP '^[0-9a-f]{64}$'
   OR NOT (
       principal_amount_before <> label_amount
       OR principal_currency_before <> label_currency
       OR principal_currency_exponent_before <> label_currency_exponent
   )
   OR repair_status NOT IN ('PENDING_REVIEW', 'EXECUTED', 'REJECTED')
   OR (repair_status = 'PENDING_REVIEW' AND NOT (
       review_operator IS NULL AND review_comment IS NULL AND review_time IS NULL
       AND new_clearing_revision IS NULL AND new_candidate_no IS NULL
       AND new_candidate_id IS NULL AND executed_time IS NULL
   ))
   OR (repair_status = 'REJECTED' AND NOT (
       review_operator IS NOT NULL AND review_comment IS NOT NULL AND review_time IS NOT NULL
       AND review_operator <> submit_operator AND new_clearing_revision IS NULL
       AND new_candidate_no IS NULL AND new_candidate_id IS NULL AND executed_time IS NULL
   ))
   OR (repair_status = 'EXECUTED' AND NOT (
       review_operator IS NOT NULL AND review_comment IS NOT NULL AND review_time IS NOT NULL
       AND review_operator <> submit_operator
       AND new_clearing_revision = expected_clearing_revision + 1
       AND new_candidate_no IS NOT NULL AND new_candidate_id IS NOT NULL
       AND executed_time IS NOT NULL
   ));

-- 5. 依赖迁移审计必须与已执行修复及当前依赖指向完全一致。
SELECT COUNT(*) AS invalid_dependency_audit_row_count
FROM clearing_label_principal_repair_dependency_audit audit
LEFT JOIN clearing_label_principal_repair repair
       ON repair.repair_no = audit.repair_no
LEFT JOIN settlement_candidate_dependency dependency
       ON dependency.id = audit.dependency_id
WHERE repair.id IS NULL
   OR repair.repair_status <> 'EXECUTED'
   OR repair.dependency_count <> 1
   OR repair.candidate_id <> audit.old_depends_on_candidate_id
   OR repair.new_candidate_id <> audit.new_depends_on_candidate_id
   OR audit.dependency_type <> 'RESERVE_HOLD_POSTED'
   OR audit.old_depends_on_candidate_id = audit.new_depends_on_candidate_id
   OR dependency.id IS NULL
   OR dependency.candidate_id <> audit.dependent_candidate_id
   OR dependency.depends_on_candidate_id <> audit.new_depends_on_candidate_id
   OR dependency.dependency_type <> audit.dependency_type;

-- 6. 已执行申请的冻结依赖数量必须与迁移审计数量一致。
SELECT COUNT(*) AS missing_or_unexpected_dependency_audit_count
FROM clearing_label_principal_repair repair
LEFT JOIN (
    SELECT repair_no, COUNT(*) AS audit_count
    FROM clearing_label_principal_repair_dependency_audit
    GROUP BY repair_no
) audit ON audit.repair_no = repair.repair_no
WHERE repair.repair_status = 'EXECUTED'
  AND COALESCE(audit.audit_count, 0) <> repair.dependency_count;

-- 7. 保证金状态迁移审计必须与当前状态引用、新旧 HOLD 状态和修复修订完全一致。
WITH reserve_state AS (
    SELECT state.* FROM transaction_reserve_clearing_state state
    UNION ALL SELECT state.* FROM transaction_reserve_clearing_state_202603 state
    UNION ALL SELECT state.* FROM transaction_reserve_clearing_state_202604 state
    UNION ALL SELECT state.* FROM transaction_reserve_clearing_state_202701 state
), reserve_detail AS (
    SELECT detail.* FROM transaction_reserve_clearing_detail detail
    UNION ALL SELECT detail.* FROM transaction_reserve_clearing_detail_202603 detail
    UNION ALL SELECT detail.* FROM transaction_reserve_clearing_detail_202604 detail
    UNION ALL SELECT detail.* FROM transaction_reserve_clearing_detail_202701 detail
)
SELECT COUNT(*) AS invalid_reserve_state_audit_row_count
FROM clearing_label_principal_repair_reserve_state_audit audit
LEFT JOIN clearing_label_principal_repair repair
       ON repair.repair_no = audit.repair_no
LEFT JOIN reserve_state state
       ON state.id = audit.reserve_state_row_id
      AND state.reserve_state_id = audit.reserve_state_id
LEFT JOIN reserve_detail old_hold
       ON old_hold.reserve_clearing_detail_no = audit.old_hold_detail_no
LEFT JOIN reserve_detail new_hold
       ON new_hold.reserve_clearing_detail_no = audit.new_hold_detail_no
WHERE repair.id IS NULL
   OR repair.repair_status <> 'EXECUTED'
   OR repair.reserve_state_count <> 1
   OR audit.state_snapshot_hash <> repair.reserve_state_snapshot_hash
   OR audit.new_state_version <> audit.old_state_version + 1
   OR state.id IS NULL
   OR state.original_hold_detail_no <> audit.new_hold_detail_no
   OR state.version <> audit.new_state_version
   OR old_hold.id IS NULL
   OR old_hold.record_status <> 'SUPERSEDED'
   OR old_hold.clearing_revision <> repair.expected_clearing_revision
   OR new_hold.id IS NULL
   OR new_hold.record_status <> 'ACTIVE'
   OR new_hold.reserve_action_type <> 'HOLD'
   OR new_hold.clearing_revision <> repair.new_clearing_revision;

-- 8. 已执行申请的冻结保证金状态数量必须与迁移审计数量一致。
SELECT COUNT(*) AS missing_or_unexpected_reserve_state_audit_count
FROM clearing_label_principal_repair repair
LEFT JOIN (
    SELECT repair_no, COUNT(*) AS audit_count
    FROM clearing_label_principal_repair_reserve_state_audit
    GROUP BY repair_no
) audit ON audit.repair_no = repair.repair_no
WHERE repair.repair_status = 'EXECUTED'
  AND COALESCE(audit.audit_count, 0) <> repair.reserve_state_count;
