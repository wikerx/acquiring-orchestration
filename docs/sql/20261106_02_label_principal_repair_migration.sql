-- 未结算历史标签本金修复审批事实表。
--
-- 执行约束：先执行 20261106_01_label_principal_clearing_precheck.sql 并完成变更审批；
--           本脚本只创建修复通道，不取消结算批次、不修改既有清分或结算资金事实。
-- 时间口径：普通 DATETIME(3) 保存平台业务时区（Asia/Shanghai）的本地时间。

CREATE TABLE IF NOT EXISTS clearing_label_principal_repair (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    repair_no VARCHAR(64) NOT NULL COMMENT '稳定修复单号',
    request_key VARCHAR(128) NOT NULL COMMENT '调用方请求幂等键',
    transaction_id VARCHAR(64) NOT NULL COMMENT '待修复动作交易号',
    transaction_date_time DATETIME(3) NOT NULL COMMENT '动作季度精确路由时间',
    finance_state_id VARCHAR(64) NOT NULL COMMENT '动作财务状态业务号',
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号',
    expected_finance_state_version INT NOT NULL COMMENT '提交时冻结的财务状态CAS版本',
    expected_clearing_revision INT NOT NULL COMMENT '提交时冻结的原清分修订',
    candidate_id BIGINT NOT NULL COMMENT '提交时冻结的未认领结算候选主键',
    expected_candidate_version BIGINT NOT NULL COMMENT '提交时冻结的候选CAS版本',
    principal_detail_no VARCHAR(64) NOT NULL COMMENT '原ACTIVE本金明细业务号',
    principal_amount_before DECIMAL(24,8) NOT NULL COMMENT '修复前本金金额',
    principal_currency_before CHAR(3) NOT NULL COMMENT '修复前本金币种',
    principal_currency_exponent_before TINYINT NOT NULL COMMENT '修复前本金币种ISO小数位',
    label_amount DECIMAL(24,8) NOT NULL COMMENT '冻结的商户标签本金金额',
    label_currency CHAR(3) NOT NULL COMMENT '冻结的商户标签币种',
    label_currency_exponent TINYINT NOT NULL COMMENT '冻结的标签币种ISO小数位',
    source_snapshot_hash CHAR(64) NOT NULL COMMENT '原修订全部ACTIVE明细规范快照SHA-256',
    reserve_fact_count TINYINT NOT NULL COMMENT '原修订ACTIVE保证金事实数量，只允许0或1',
    reserve_snapshot_hash CHAR(64) NOT NULL COMMENT '原修订ACTIVE保证金事实规范快照SHA-256',
    reserve_state_count TINYINT NOT NULL COMMENT '冻结的保证金状态数量，必须与保证金事实数量一致',
    reserve_state_snapshot_hash CHAR(64) NOT NULL COMMENT '保证金状态完整规范快照SHA-256',
    dependency_count TINYINT NOT NULL COMMENT '旧候选依赖数量，只允许0或1',
    dependency_snapshot_hash CHAR(64) NOT NULL COMMENT '依赖关系及依赖候选状态规范快照SHA-256',
    reason VARCHAR(400) NOT NULL COMMENT '修复申请原因，不保存敏感支付数据',
    submit_operator VARCHAR(128) NOT NULL COMMENT 'service-admin注入的可信Maker身份',
    review_operator VARCHAR(128) NULL COMMENT 'service-admin注入的可信Checker身份',
    review_comment VARCHAR(400) NULL COMMENT '复核意见',
    repair_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_REVIEW'
        COMMENT 'PENDING_REVIEW、EXECUTED、REJECTED',
    new_clearing_revision INT NULL COMMENT '批准后生成的新清分修订',
    new_candidate_no VARCHAR(64) NULL COMMENT '批准后生成的新结算候选业务号',
    new_candidate_id BIGINT NULL COMMENT '批准后生成的新结算候选主键，仅用于依赖迁移审计',
    review_time DATETIME(3) NULL COMMENT '复核时间',
    executed_time DATETIME(3) NULL COMMENT '新修订、候选与Outbox同事务完成时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '审批状态CAS版本',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_label_principal_repair_no (repair_no),
    UNIQUE KEY uk_label_principal_repair_request (request_key),
    KEY idx_label_principal_repair_transaction
        (transaction_id, transaction_date_time, expected_clearing_revision, id),
    KEY idx_label_principal_repair_finance
        (finance_state_id, expected_clearing_revision, repair_status, id),
    KEY idx_label_principal_repair_status
        (repair_status, create_time, id),
    CONSTRAINT chk_label_principal_repair_value CHECK (
        expected_finance_state_version >= 0
        AND expected_clearing_revision >= 1
        AND candidate_id > 0
        AND expected_candidate_version >= 0
        AND principal_amount_before >= 0
        AND label_amount >= 0
        AND principal_currency_exponent_before BETWEEN 0 AND 8
        AND label_currency_exponent BETWEEN 0 AND 8
        AND reserve_fact_count BETWEEN 0 AND 1
        AND reserve_state_count BETWEEN 0 AND 1
        AND reserve_state_count = reserve_fact_count
        AND dependency_count BETWEEN 0 AND 1
        AND version >= 0
        AND REGEXP_LIKE(source_snapshot_hash, '^[0-9a-f]{64}$')
        AND REGEXP_LIKE(reserve_snapshot_hash, '^[0-9a-f]{64}$')
        AND REGEXP_LIKE(reserve_state_snapshot_hash, '^[0-9a-f]{64}$')
        AND REGEXP_LIKE(dependency_snapshot_hash, '^[0-9a-f]{64}$')
        AND (
            principal_amount_before <> label_amount
            OR principal_currency_before <> label_currency
            OR principal_currency_exponent_before <> label_currency_exponent
        )
    ),
    CONSTRAINT chk_label_principal_repair_state CHECK (
        repair_status IN ('PENDING_REVIEW', 'EXECUTED', 'REJECTED')
        AND (
            (repair_status = 'PENDING_REVIEW'
             AND review_operator IS NULL AND review_comment IS NULL AND review_time IS NULL
             AND new_clearing_revision IS NULL AND new_candidate_no IS NULL AND new_candidate_id IS NULL
             AND executed_time IS NULL)
            OR (repair_status = 'REJECTED'
                AND review_operator IS NOT NULL AND review_comment IS NOT NULL
                AND review_time IS NOT NULL AND review_operator <> submit_operator
                AND new_clearing_revision IS NULL AND new_candidate_no IS NULL AND new_candidate_id IS NULL
                AND executed_time IS NULL)
            OR (repair_status = 'EXECUTED'
                AND review_operator IS NOT NULL AND review_comment IS NOT NULL
                AND review_time IS NOT NULL AND review_operator <> submit_operator
                AND new_clearing_revision = expected_clearing_revision + 1
                AND new_candidate_no IS NOT NULL AND new_candidate_id IS NOT NULL
                AND executed_time IS NOT NULL)
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='未结算历史标签本金新修订修复双人复核事实；不重算费用、保证金或汇率';

CREATE TABLE IF NOT EXISTS clearing_label_principal_repair_dependency_audit (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    repair_no VARCHAR(64) NOT NULL COMMENT '关联的历史标签本金修复单号',
    dependency_id BIGINT NOT NULL COMMENT '被迁移的既有候选依赖主键',
    dependent_candidate_id BIGINT NOT NULL COMMENT '受依赖约束的保证金释放候选主键',
    old_depends_on_candidate_id BIGINT NOT NULL COMMENT '迁移前被依赖的旧清分候选主键',
    new_depends_on_candidate_id BIGINT NOT NULL COMMENT '迁移后被依赖的新清分候选主键',
    dependency_type VARCHAR(32) NOT NULL COMMENT '当前只允许RESERVE_HOLD_POSTED',
    dependent_candidate_version BIGINT NOT NULL COMMENT '迁移时冻结的保证金释放候选CAS版本',
    migrated_time DATETIME(3) NOT NULL COMMENT '依赖迁移完成时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '审计创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_label_principal_dependency_repair (repair_no, dependency_id),
    KEY idx_label_principal_dependency_old (old_depends_on_candidate_id, dependency_id),
    KEY idx_label_principal_dependency_new (new_depends_on_candidate_id, dependency_id),
    CONSTRAINT chk_label_principal_dependency_audit CHECK (
        dependency_id > 0
        AND dependent_candidate_id > 0
        AND old_depends_on_candidate_id > 0
        AND new_depends_on_candidate_id > 0
        AND old_depends_on_candidate_id <> new_depends_on_candidate_id
        AND dependent_candidate_id <> old_depends_on_candidate_id
        AND dependent_candidate_id <> new_depends_on_candidate_id
        AND dependency_type = 'RESERVE_HOLD_POSTED'
        AND dependent_candidate_version >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='历史标签本金修复候选依赖迁移不可变审计';

CREATE TABLE IF NOT EXISTS clearing_label_principal_repair_reserve_state_audit (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    repair_no VARCHAR(64) NOT NULL COMMENT '关联的历史标签本金修复单号',
    reserve_state_row_id BIGINT NOT NULL COMMENT '被迁移的保证金状态物理主键',
    reserve_state_id VARCHAR(64) NOT NULL COMMENT '保证金状态业务号',
    old_hold_detail_no VARCHAR(64) NOT NULL COMMENT '迁移前引用的旧HOLD明细号',
    new_hold_detail_no VARCHAR(64) NOT NULL COMMENT '迁移后引用的新HOLD明细号',
    old_state_version BIGINT NOT NULL COMMENT '迁移前冻结的保证金状态CAS版本',
    new_state_version BIGINT NOT NULL COMMENT '迁移后的保证金状态CAS版本',
    state_snapshot_hash CHAR(64) NOT NULL COMMENT '迁移前完整保证金状态SHA-256快照',
    migrated_time DATETIME(3) NOT NULL COMMENT '状态引用迁移完成时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '审计创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_label_principal_reserve_state_repair (repair_no, reserve_state_row_id),
    KEY idx_label_principal_reserve_state_old (old_hold_detail_no, reserve_state_row_id),
    KEY idx_label_principal_reserve_state_new (new_hold_detail_no, reserve_state_row_id),
    CONSTRAINT chk_label_principal_reserve_state_audit CHECK (
        reserve_state_row_id > 0
        AND old_hold_detail_no <> new_hold_detail_no
        AND old_state_version >= 0
        AND new_state_version = old_state_version + 1
        AND REGEXP_LIKE(state_snapshot_hash, '^[0-9a-f]{64}$')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='历史标签本金修复保证金状态HOLD引用迁移不可变审计';
