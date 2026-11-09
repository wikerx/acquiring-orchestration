-- 已入账历史标签本金差额调整通道。
--
-- 执行前置：
--   1. 先执行 20261106_01_label_principal_clearing_precheck.sql 并人工确认已入账异常范围；
--   2. 禁止在迁移中创建调整单或修改任何历史清分、结算、账务、费用、保证金和汇率事实；
--   3. 业务调整必须通过 service-admin -> service-settlement 的 Maker-Checker 命令执行。

SET NAMES utf8mb4;

CREATE TABLE settlement_principal_correction_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    correction_no VARCHAR(35) NOT NULL COMMENT '调整单号，SPC加32位小写十六进制摘要',
    submit_request_key VARCHAR(128) NOT NULL COMMENT 'Maker提交全局幂等键',
    original_batch_no VARCHAR(19) NOT NULL COMMENT '原POSTED REGULAR结算批次号',
    merchant_id VARCHAR(64) NOT NULL COMMENT '原批次平台商户号',
    settlement_account_id BIGINT NOT NULL COMMENT '原批次结算资金账户ID',
    target_currency CHAR(3) NOT NULL COMMENT '原批次目标结算币种',
    target_currency_exponent TINYINT NOT NULL COMMENT '目标币种ISO exponent',
    original_batch_version BIGINT NOT NULL COMMENT '申请时冻结的原批次版本',
    original_fund_ledger_id BIGINT NOT NULL COMMENT '原批次不可变资金流水ID',
    item_count INT NOT NULL COMMENT '冻结的异常本金结果项数量',
    correction_direction VARCHAR(8) NOT NULL COMMENT '商户余额差额方向，CREDIT或DEBIT',
    correction_amount DECIMAL(24,8) NOT NULL COMMENT '目标币种非负差额合计',
    source_fingerprint CHAR(64) NOT NULL COMMENT '原批次、流水、逐笔来源和计算结果SHA-256',
    correction_status VARCHAR(24) NOT NULL COMMENT 'PENDING_REVIEW、EXECUTED或REJECTED',
    submitted_by_account_id BIGINT NOT NULL COMMENT 'Maker管理账号ID',
    submitted_by_account_name VARCHAR(128) NOT NULL COMMENT 'Maker账号名称快照',
    submitted_role_snapshot VARCHAR(1000) NOT NULL COMMENT 'Maker角色权限快照',
    submit_client_ip VARCHAR(64) NOT NULL COMMENT 'Maker客户端IP',
    submit_user_agent VARCHAR(500) NOT NULL COMMENT 'Maker客户端User-Agent',
    submit_reason VARCHAR(400) NOT NULL COMMENT '调整原因',
    submitted_time DATETIME(3) NOT NULL COMMENT 'Maker提交时间',
    decided_by_account_id BIGINT NULL COMMENT 'Checker管理账号ID',
    decided_by_account_name VARCHAR(128) NULL COMMENT 'Checker账号名称快照',
    decided_role_snapshot VARCHAR(1000) NULL COMMENT 'Checker角色权限快照',
    decision_client_ip VARCHAR(64) NULL COMMENT 'Checker客户端IP',
    decision_user_agent VARCHAR(500) NULL COMMENT 'Checker客户端User-Agent',
    decision_action VARCHAR(16) NULL COMMENT 'APPROVE或REJECT',
    decision_request_key VARCHAR(128) NULL COMMENT 'Checker决策全局幂等键',
    decision_comment VARCHAR(400) NULL COMMENT 'Checker复核意见',
    decision_time DATETIME(3) NULL COMMENT 'Checker决策时间',
    correction_ledger_id BIGINT NULL COMMENT '非零差额对应的唯一新增资金流水ID',
    projection_completed_count INT NOT NULL DEFAULT 0 COMMENT '已完成三层交易投影修正数量',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '调整单状态CAS版本',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_principal_correction_no (correction_no),
    UNIQUE KEY uk_settlement_principal_correction_submit (submit_request_key),
    UNIQUE KEY uk_settlement_principal_correction_batch (original_batch_no),
    UNIQUE KEY uk_settlement_principal_correction_decision (decision_request_key),
    UNIQUE KEY uk_settlement_principal_correction_ledger (correction_ledger_id),
    KEY idx_settlement_principal_correction_merchant
        (merchant_id, correction_status, submitted_time, id),
    KEY idx_settlement_principal_correction_status
        (correction_status, submitted_time, id),
    CONSTRAINT chk_settlement_principal_correction_value CHECK (
        REGEXP_LIKE(target_currency, '^[A-Z]{3}$', 'c')
        AND target_currency_exponent BETWEEN 0 AND 8
        AND original_batch_version >= 0
        AND original_fund_ledger_id > 0
        AND item_count > 0
        AND correction_direction IN ('CREDIT', 'DEBIT')
        AND correction_amount >= 0
        AND projection_completed_count BETWEEN 0 AND item_count
        AND version >= 0
        AND (correction_amount > 0 OR correction_direction = 'CREDIT')
    ),
    CONSTRAINT chk_settlement_principal_correction_state CHECK (
        correction_status IN ('PENDING_REVIEW', 'EXECUTED', 'REJECTED')
        AND ((correction_status = 'PENDING_REVIEW'
              AND decided_by_account_id IS NULL AND decided_by_account_name IS NULL
              AND decided_role_snapshot IS NULL AND decision_client_ip IS NULL
              AND decision_user_agent IS NULL AND decision_action IS NULL
              AND decision_request_key IS NULL AND decision_comment IS NULL
              AND decision_time IS NULL AND correction_ledger_id IS NULL)
             OR (correction_status = 'EXECUTED'
                 AND decided_by_account_id IS NOT NULL AND decided_by_account_name IS NOT NULL
                 AND decided_role_snapshot IS NOT NULL AND decision_client_ip IS NOT NULL
                 AND decision_user_agent IS NOT NULL AND decision_action = 'APPROVE'
                 AND decision_request_key IS NOT NULL AND decision_comment IS NOT NULL
                 AND decision_time IS NOT NULL
                 AND submitted_by_account_id <> decided_by_account_id
                 AND ((correction_amount = 0 AND correction_ledger_id IS NULL)
                      OR (correction_amount > 0 AND correction_ledger_id IS NOT NULL)))
             OR (correction_status = 'REJECTED'
                 AND decided_by_account_id IS NOT NULL AND decided_by_account_name IS NOT NULL
                 AND decided_role_snapshot IS NOT NULL AND decision_client_ip IS NOT NULL
                 AND decision_user_agent IS NOT NULL AND decision_action = 'REJECT'
                 AND decision_request_key IS NOT NULL AND decision_comment IS NOT NULL
                 AND decision_time IS NOT NULL AND correction_ledger_id IS NULL
                 AND submitted_by_account_id <> decided_by_account_id))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='已入账历史标签本金差额调整Maker-Checker主单；原资金事实保持不可变';

CREATE TABLE settlement_principal_correction_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    correction_item_no VARCHAR(36) NOT NULL COMMENT '逐笔调整号，SPCI加32位小写十六进制摘要',
    correction_no VARCHAR(35) NOT NULL COMMENT '调整主单号',
    original_result_item_id BIGINT NOT NULL COMMENT '原不可变PRINCIPAL结算结果项ID',
    original_projection_task_id BIGINT NOT NULL COMMENT '原已完成结算投影任务ID',
    original_projection_task_no VARCHAR(64) NOT NULL COMMENT '原结算投影任务号',
    candidate_id BIGINT NOT NULL COMMENT '原POSTED真实交易候选ID',
    clearing_detail_no VARCHAR(64) NOT NULL COMMENT '原ACTIVE本金清分明细号',
    transaction_id VARCHAR(64) NOT NULL COMMENT '平台动作交易号',
    transaction_date_time DATETIME(3) NOT NULL COMMENT '交易季度精确分片时间',
    clearing_revision INT NOT NULL COMMENT '原清分修订号',
    operation_id VARCHAR(64) NOT NULL COMMENT '平台交易生命周期号',
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号',
    direction VARCHAR(8) NOT NULL COMMENT '原本金商户视角CREDIT或DEBIT',
    original_source_amount DECIMAL(24,8) NOT NULL COMMENT '原结算结果引用的错误本金金额',
    original_source_currency CHAR(3) NOT NULL COMMENT '原结算结果引用的错误本金币种',
    original_source_currency_exponent TINYINT NOT NULL COMMENT '原错误本金币种exponent',
    label_amount DECIMAL(24,8) NOT NULL COMMENT '商户标签本金金额',
    label_currency CHAR(3) NOT NULL COMMENT '商户标签本金币种',
    label_currency_exponent TINYINT NOT NULL COMMENT '标签本金币种exponent',
    original_locked_rate_id BIGINT NOT NULL COMMENT '原历史结果实际引用的批次锁定汇率ID',
    original_locked_direct_rate DECIMAL(24,12) NOT NULL COMMENT '原历史结果实际引用的direct rate',
    label_locked_rate_id BIGINT NOT NULL COMMENT '标签本金重算使用的批次锁定汇率ID',
    label_locked_direct_rate DECIMAL(24,12) NOT NULL COMMENT '标签本金重算使用的direct rate',
    target_currency CHAR(3) NOT NULL COMMENT '原批次目标结算币种',
    target_currency_exponent TINYINT NOT NULL COMMENT '目标币种exponent',
    rounding_mode VARCHAR(16) NOT NULL COMMENT '原结算结果冻结舍入模式',
    original_target_amount DECIMAL(24,8) NOT NULL COMMENT '原本金目标币种结果',
    expected_target_amount DECIMAL(24,8) NOT NULL COMMENT '标签本金按原汇率重算后的目标金额',
    signed_delta_amount DECIMAL(24,8) NOT NULL COMMENT '商户余额视角有符号逐笔差额',
    original_projection_amount DECIMAL(24,8) NOT NULL COMMENT '原动作完整结算投影金额',
    corrected_projection_amount DECIMAL(24,8) NOT NULL COMMENT '加上本金差额后的动作结算投影金额',
    settlement_date DATE NOT NULL COMMENT '原结算业务日期',
    projection_status VARCHAR(24) NOT NULL COMMENT 'WAITING_REVIEW、INIT、PROCESSING、COMPLETED、FAILED或CANCELLED',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '投影修正失败次数',
    next_retry_time DATETIME(3) NULL COMMENT '下次允许投影修正时间',
    last_failure_code VARCHAR(64) NULL COMMENT '最近稳定失败码',
    completed_time DATETIME(3) NULL COMMENT '三层交易投影完成时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '投影任务状态CAS版本',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_principal_correction_item_no (correction_item_no),
    UNIQUE KEY uk_settlement_principal_correction_result (original_result_item_id),
    UNIQUE KEY uk_settlement_principal_correction_order_candidate (correction_no, candidate_id),
    KEY idx_settlement_principal_correction_item_order (correction_no, id),
    KEY idx_settlement_principal_correction_item_due
        (projection_status, next_retry_time, id),
    KEY idx_settlement_principal_correction_item_transaction
        (transaction_id, transaction_date_time, clearing_revision, id),
    CONSTRAINT chk_settlement_principal_correction_item_value CHECK (
        original_result_item_id > 0 AND original_projection_task_id > 0 AND candidate_id > 0
        AND clearing_revision > 0
        AND direction IN ('CREDIT', 'DEBIT')
        AND original_source_amount >= 0 AND label_amount >= 0
        AND REGEXP_LIKE(original_source_currency, '^[A-Z]{3}$', 'c')
        AND REGEXP_LIKE(label_currency, '^[A-Z]{3}$', 'c')
        AND REGEXP_LIKE(target_currency, '^[A-Z]{3}$', 'c')
        AND original_source_currency_exponent BETWEEN 0 AND 8
        AND label_currency_exponent BETWEEN 0 AND 8
        AND original_locked_rate_id > 0 AND original_locked_direct_rate > 0
        AND label_locked_rate_id > 0 AND label_locked_direct_rate > 0
        AND target_currency_exponent BETWEEN 0 AND 8
        AND rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'DOWN')
        AND original_target_amount >= 0 AND expected_target_amount >= 0
        AND retry_count >= 0 AND version >= 0
    ),
    CONSTRAINT chk_settlement_principal_correction_item_state CHECK (
        projection_status IN (
            'WAITING_REVIEW', 'INIT', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'
        )
        AND ((projection_status = 'WAITING_REVIEW'
              AND next_retry_time IS NULL AND completed_time IS NULL)
             OR (projection_status IN ('INIT', 'PROCESSING', 'FAILED')
                 AND next_retry_time IS NOT NULL AND completed_time IS NULL)
             OR (projection_status = 'COMPLETED'
                 AND next_retry_time IS NULL AND completed_time IS NOT NULL)
             OR (projection_status = 'CANCELLED'
                 AND next_retry_time IS NULL AND completed_time IS NULL))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='历史标签本金逐笔冻结事实及可靠三层交易投影修正任务';
