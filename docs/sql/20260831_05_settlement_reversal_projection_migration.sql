-- Settlement reversal Maker-Checker and three-layer transaction projection migration.
-- Prerequisites:
--   20260825_03_transaction_clearing_compatibility_draft.sql
--   20260826_08_settlement_posting_migration.sql
--   20260831_03_settlement_review_maker_checker_migration.sql

SET NAMES utf8mb4;

CREATE TABLE settlement_reversal_daily_sequence (
    business_date DATE NOT NULL,
    current_sequence INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (business_date),
    CONSTRAINT chk_settlement_reversal_sequence CHECK (
        current_sequence BETWEEN 0 AND 99999999 AND version >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Settlement reversal database daily sequence';

CREATE TABLE settlement_reversal_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reversal_order_no VARCHAR(20) NOT NULL,
    create_request_key VARCHAR(128) NOT NULL,
    original_batch_no VARCHAR(19) NOT NULL,
    reversal_batch_no VARCHAR(19) NULL,
    merchant_id VARCHAR(64) NOT NULL,
    settlement_account_id BIGINT NOT NULL,
    target_currency CHAR(3) NOT NULL,
    target_currency_exponent TINYINT NOT NULL,
    original_batch_version BIGINT NOT NULL,
    original_net_result_item_id BIGINT NOT NULL,
    original_fund_ledger_id BIGINT NOT NULL,
    net_direction VARCHAR(8) NOT NULL,
    net_amount DECIMAL(24,8) NOT NULL,
    source_fingerprint CHAR(64) NOT NULL,
    reversal_status VARCHAR(24) NOT NULL,
    submitted_by_account_id BIGINT NOT NULL,
    submitted_by_account_name VARCHAR(128) NOT NULL,
    submitted_role_snapshot VARCHAR(1000) NOT NULL,
    submit_client_ip VARCHAR(64) NOT NULL,
    submit_user_agent VARCHAR(500) NOT NULL,
    submit_reason VARCHAR(400) NOT NULL,
    submitted_time DATETIME(3) NOT NULL,
    decided_by_account_id BIGINT NULL,
    decided_by_account_name VARCHAR(128) NULL,
    decided_role_snapshot VARCHAR(1000) NULL,
    decision_client_ip VARCHAR(64) NULL,
    decision_user_agent VARCHAR(500) NULL,
    decision_action VARCHAR(16) NULL,
    decision_request_key VARCHAR(128) NULL,
    decision_comment VARCHAR(400) NULL,
    decision_time DATETIME(3) NULL,
    active_original_batch_no VARCHAR(19) GENERATED ALWAYS AS (
        CASE
            WHEN reversal_status IN ('PENDING_APPROVAL', 'APPROVED') THEN original_batch_no
            ELSE NULL
        END
    ) STORED,
    version BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_reversal_no (reversal_order_no),
    UNIQUE KEY uk_settlement_reversal_create_request (create_request_key),
    UNIQUE KEY uk_settlement_reversal_decision_request (decision_request_key),
    UNIQUE KEY uk_settlement_reversal_batch (reversal_batch_no),
    UNIQUE KEY uk_settlement_reversal_active_original (active_original_batch_no),
    KEY idx_settlement_reversal_original_status (original_batch_no, reversal_status, id),
    KEY idx_settlement_reversal_merchant_status (merchant_id, reversal_status, create_time, id),
    KEY idx_settlement_reversal_status_time (reversal_status, submitted_time, id),
    CONSTRAINT chk_settlement_reversal_value CHECK (
        target_currency_exponent BETWEEN 0 AND 8
        AND original_batch_version >= 0
        AND net_direction IN ('CREDIT', 'DEBIT')
        AND net_amount >= 0
        AND version >= 0
    ),
    CONSTRAINT chk_settlement_reversal_state CHECK (
        reversal_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')
        AND ((reversal_status = 'PENDING_APPROVAL'
              AND reversal_batch_no IS NULL
              AND decided_by_account_id IS NULL AND decided_by_account_name IS NULL
              AND decided_role_snapshot IS NULL AND decision_client_ip IS NULL
              AND decision_user_agent IS NULL AND decision_action IS NULL
              AND decision_request_key IS NULL AND decision_comment IS NULL
              AND decision_time IS NULL)
             OR (reversal_status = 'APPROVED'
                 AND reversal_batch_no IS NOT NULL
                 AND decided_by_account_id IS NOT NULL AND decided_by_account_name IS NOT NULL
                 AND decided_role_snapshot IS NOT NULL AND decision_client_ip IS NOT NULL
                 AND decision_user_agent IS NOT NULL AND decision_action = 'APPROVE'
                 AND decision_request_key IS NOT NULL AND decision_comment IS NOT NULL
                 AND decision_time IS NOT NULL
                 AND submitted_by_account_id <> decided_by_account_id)
             OR (reversal_status = 'REJECTED'
                 AND reversal_batch_no IS NULL
                 AND decided_by_account_id IS NOT NULL AND decided_by_account_name IS NOT NULL
                 AND decided_role_snapshot IS NOT NULL AND decision_client_ip IS NOT NULL
                 AND decision_user_agent IS NOT NULL AND decision_action = 'REJECT'
                 AND decision_request_key IS NOT NULL AND decision_comment IS NOT NULL
                 AND decision_time IS NOT NULL
                 AND submitted_by_account_id <> decided_by_account_id))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Settlement reversal Maker-Checker order and frozen posting identity';

-- The action projection is the authoritative settlement snapshot for each real transaction action.
ALTER TABLE transaction_operation
    MODIFY COLUMN settlement_amount DECIMAL(24,8) NULL COMMENT '动作最终结算金额；仅结算投影写入',
    ADD COLUMN settlement_rate DECIMAL(24,12) NULL
        COMMENT '1单位动作交易币种兑换的结算币种数量；仅结算投影写入' AFTER settlement_amount,
    ADD COLUMN settlement_date DATE NULL COMMENT '动作最终结算业务日期' AFTER settlement_rate,
    ADD COLUMN settlement_batch_no VARCHAR(19) NULL
        COMMENT '动作最近一次结算或冲正批次号' AFTER settlement_date;

ALTER TABLE transaction_operation_202603
    MODIFY COLUMN settlement_amount DECIMAL(24,8) NULL COMMENT '动作最终结算金额；仅结算投影写入',
    ADD COLUMN settlement_rate DECIMAL(24,12) NULL
        COMMENT '1单位动作交易币种兑换的结算币种数量；仅结算投影写入' AFTER settlement_amount,
    ADD COLUMN settlement_date DATE NULL COMMENT '动作最终结算业务日期' AFTER settlement_rate,
    ADD COLUMN settlement_batch_no VARCHAR(19) NULL
        COMMENT '动作最近一次结算或冲正批次号' AFTER settlement_date;

ALTER TABLE transaction_operation_202604
    MODIFY COLUMN settlement_amount DECIMAL(24,8) NULL COMMENT '动作最终结算金额；仅结算投影写入',
    ADD COLUMN settlement_rate DECIMAL(24,12) NULL
        COMMENT '1单位动作交易币种兑换的结算币种数量；仅结算投影写入' AFTER settlement_amount,
    ADD COLUMN settlement_date DATE NULL COMMENT '动作最终结算业务日期' AFTER settlement_rate,
    ADD COLUMN settlement_batch_no VARCHAR(19) NULL
        COMMENT '动作最近一次结算或冲正批次号' AFTER settlement_date;

-- The lifecycle order keeps the latest real action snapshot using transaction time plus transaction ID.
ALTER TABLE transaction_order
    MODIFY COLUMN settlement_amount DECIMAL(24,8) NULL COMMENT '最近真实动作最终结算金额',
    ADD COLUMN settlement_rate DECIMAL(24,12) NULL
        COMMENT '最近真实动作中1单位交易币种兑换的结算币种数量' AFTER settlement_amount,
    ADD COLUMN settlement_date DATE NULL COMMENT '最近真实动作结算业务日期' AFTER settlement_rate,
    MODIFY COLUMN settlement_batch_no VARCHAR(19) NULL COMMENT '最近一次结算或冲正批次号',
    ADD COLUMN settlement_transaction_id VARCHAR(64) NULL
        COMMENT '当前结算快照来源的真实动作交易号' AFTER settlement_batch_no,
    ADD COLUMN settlement_transaction_date_time DATETIME(3) NULL
        COMMENT '当前结算快照来源的真实动作分片时间' AFTER settlement_transaction_id;

ALTER TABLE transaction_order_202603
    MODIFY COLUMN settlement_amount DECIMAL(24,8) NULL COMMENT '最近真实动作最终结算金额',
    ADD COLUMN settlement_rate DECIMAL(24,12) NULL
        COMMENT '最近真实动作中1单位交易币种兑换的结算币种数量' AFTER settlement_amount,
    ADD COLUMN settlement_date DATE NULL COMMENT '最近真实动作结算业务日期' AFTER settlement_rate,
    MODIFY COLUMN settlement_batch_no VARCHAR(19) NULL COMMENT '最近一次结算或冲正批次号',
    ADD COLUMN settlement_transaction_id VARCHAR(64) NULL
        COMMENT '当前结算快照来源的真实动作交易号' AFTER settlement_batch_no,
    ADD COLUMN settlement_transaction_date_time DATETIME(3) NULL
        COMMENT '当前结算快照来源的真实动作分片时间' AFTER settlement_transaction_id;

ALTER TABLE transaction_order_202604
    MODIFY COLUMN settlement_amount DECIMAL(24,8) NULL COMMENT '最近真实动作最终结算金额',
    ADD COLUMN settlement_rate DECIMAL(24,12) NULL
        COMMENT '最近真实动作中1单位交易币种兑换的结算币种数量' AFTER settlement_amount,
    ADD COLUMN settlement_date DATE NULL COMMENT '最近真实动作结算业务日期' AFTER settlement_rate,
    MODIFY COLUMN settlement_batch_no VARCHAR(19) NULL COMMENT '最近一次结算或冲正批次号',
    ADD COLUMN settlement_transaction_id VARCHAR(64) NULL
        COMMENT '当前结算快照来源的真实动作交易号' AFTER settlement_batch_no,
    ADD COLUMN settlement_transaction_date_time DATETIME(3) NULL
        COMMENT '当前结算快照来源的真实动作分片时间' AFTER settlement_transaction_id;

-- Normalize the finance-state projection precision and batch number width to the same contract.
ALTER TABLE transaction_finance_state
    MODIFY COLUMN settlement_rate DECIMAL(24,12) NULL COMMENT '锁定结算直汇率；清分不得写入',
    MODIFY COLUMN settlement_amount DECIMAL(24,8) NULL COMMENT '动作最终结算金额；清分不得写入',
    MODIFY COLUMN settlement_batch_no VARCHAR(19) NULL COMMENT '最近一次结算或冲正批次号';

ALTER TABLE transaction_finance_state_202603
    MODIFY COLUMN settlement_rate DECIMAL(24,12) NULL COMMENT '锁定结算直汇率；清分不得写入',
    MODIFY COLUMN settlement_amount DECIMAL(24,8) NULL COMMENT '动作最终结算金额；清分不得写入',
    MODIFY COLUMN settlement_batch_no VARCHAR(19) NULL COMMENT '最近一次结算或冲正批次号';

ALTER TABLE transaction_finance_state_202604
    MODIFY COLUMN settlement_rate DECIMAL(24,12) NULL COMMENT '锁定结算直汇率；清分不得写入',
    MODIFY COLUMN settlement_amount DECIMAL(24,8) NULL COMMENT '动作最终结算金额；清分不得写入',
    MODIFY COLUMN settlement_batch_no VARCHAR(19) NULL COMMENT '最近一次结算或冲正批次号';

-- A reversal task changes only settlement status and batch identity in transaction projections.
ALTER TABLE settlement_projection_task
    MODIFY COLUMN settlement_amount DECIMAL(24,8) NOT NULL
        COMMENT '原动作有符号结算金额；SETTLE和REVERSE均保持原结算事实',
    MODIFY COLUMN settlement_date DATE NOT NULL
        COMMENT '原动作结算业务日期；REVERSE不得替换为冲正日期';
