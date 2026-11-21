-- Settlement reversal Maker-Checker and three-layer transaction projection migration.
-- Prerequisites:
--   20260825_03_transaction_clearing_compatibility_draft.sql
--   20260826_08_settlement_posting_migration.sql
--   20260831_03_settlement_review_maker_checker_migration.sql

SET NAMES utf8mb4;

CREATE TABLE settlement_reversal_daily_sequence (
    business_date DATE NOT NULL COMMENT '结算业务日期' ,
    current_sequence INT NOT NULL DEFAULT 0 COMMENT '业务日期内已分配的最大流水序号' ,
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，防止并发覆盖' ,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' ,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' ,
    PRIMARY KEY (business_date),
    CONSTRAINT chk_settlement_reversal_sequence CHECK (
        current_sequence BETWEEN 0 AND 99999999 AND version >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='结算冲正单号的数据库业务日流水序列表';

CREATE TABLE settlement_reversal_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键' ,
    reversal_order_no VARCHAR(20) NOT NULL COMMENT '结算冲正申请单号' ,
    create_request_key VARCHAR(128) NOT NULL COMMENT '创建请求持久化幂等键' ,
    original_batch_no VARCHAR(19) NOT NULL COMMENT '被冲正的原结算批次号' ,
    reversal_batch_no VARCHAR(19) NULL COMMENT '实际生成的冲正结算批次号' ,
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号' ,
    settlement_account_id BIGINT NOT NULL COMMENT '目标商户结算资金账户主键' ,
    target_currency CHAR(3) NOT NULL COMMENT '目标结算币种代码' ,
    target_currency_exponent TINYINT NOT NULL COMMENT '目标结算币种小数位' ,
    original_batch_version BIGINT NOT NULL COMMENT '提交时冻结的原批次版本' ,
    original_net_result_item_id BIGINT NOT NULL COMMENT '原净结算结果明细主键' ,
    original_fund_ledger_id BIGINT NOT NULL COMMENT '原结算资金流水主键' ,
    net_direction VARCHAR(8) NOT NULL COMMENT '净结算收付方向' ,
    net_amount DECIMAL(24,8) NOT NULL COMMENT '冻结净结算金额，以目标币种主单位计价' ,
    source_fingerprint CHAR(64) NOT NULL COMMENT '冻结来源事实集合的摘要指纹' ,
    reversal_status VARCHAR(24) NOT NULL COMMENT '结算冲正申请状态' ,
    submitted_by_account_id BIGINT NOT NULL COMMENT '提交人账号主键' ,
    submitted_by_account_name VARCHAR(128) NOT NULL COMMENT '提交人账号名称快照' ,
    submitted_role_snapshot VARCHAR(1000) NOT NULL COMMENT '提交时的操作人角色快照' ,
    submit_client_ip VARCHAR(64) NOT NULL COMMENT '提交客户端地址，仅用于受控审计' ,
    submit_user_agent VARCHAR(500) NOT NULL COMMENT '提交客户端用户代理信息' ,
    submit_reason VARCHAR(400) NOT NULL COMMENT '提交原因' ,
    submitted_time DATETIME(3) NOT NULL COMMENT '申请提交时间' ,
    decided_by_account_id BIGINT NULL COMMENT '审批决策人账号主键' ,
    decided_by_account_name VARCHAR(128) NULL COMMENT '审批决策人账号名称快照' ,
    decided_role_snapshot VARCHAR(1000) NULL COMMENT '审批决策时的角色快照' ,
    decision_client_ip VARCHAR(64) NULL COMMENT '审批客户端地址，仅用于受控审计' ,
    decision_user_agent VARCHAR(500) NULL COMMENT '审批客户端用户代理信息' ,
    decision_action VARCHAR(16) NULL COMMENT '审批决定类型' ,
    decision_request_key VARCHAR(128) NULL COMMENT '审批决定持久化幂等键' ,
    decision_comment VARCHAR(400) NULL COMMENT '审批决定说明' ,
    decision_time DATETIME(3) NULL COMMENT '审批决定发生时间' ,
    active_original_batch_no VARCHAR(19) GENERATED ALWAYS AS (
        CASE
            WHEN reversal_status IN ('PENDING_APPROVAL', 'APPROVED') THEN original_batch_no
            ELSE NULL
        END
    ) STORED COMMENT '活动冲正申请的原批次唯一占位，终态时为空' ,
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，防止并发覆盖' ,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' ,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' ,
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
  COMMENT='结算冲正申请及双人复核主单，保留冻结的入账身份';

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
