-- Manual transaction settlement review generation for large candidate sets.
-- Review membership is frozen in bounded transactions; one logical review order may contain many segments.

CREATE TABLE settlement_manual_review_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键' ,
    task_no VARCHAR(40) NOT NULL COMMENT '持久化任务编号' ,
    request_key VARCHAR(128) NOT NULL COMMENT '操作请求持久化幂等键' ,
    submit_request_key VARCHAR(128) NULL COMMENT '提交结算预审单的请求幂等键' ,
    review_order_no VARCHAR(19) NOT NULL COMMENT '结算预审单号' ,
    review_type VARCHAR(24) NOT NULL COMMENT '结算预审类型，区分常规结算、保证金释放和调整' ,
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号' ,
    settlement_profile_id BIGINT NOT NULL COMMENT '冻结的商户结算档案主键' ,
    settlement_account_id BIGINT NOT NULL COMMENT '目标商户结算资金账户主键' ,
    target_currency CHAR(3) NOT NULL COMMENT '目标结算币种代码' ,
    target_currency_exponent TINYINT NOT NULL COMMENT '目标结算币种小数位' ,
    payment_type VARCHAR(64) NULL COMMENT '支付业务类型' ,
    payment_method VARCHAR(64) NULL COMMENT '收单支付方式' ,
    business_date DATE NOT NULL COMMENT '结算业务日期' ,
    business_time_zone VARCHAR(64) NOT NULL COMMENT '解释结算业务时间的时区标识' ,
    cutoff_begin_time DATETIME(3) NOT NULL COMMENT '候选时间窗口的闭区间起点' ,
    cutoff_end_time DATETIME(3) NOT NULL COMMENT '候选时间窗口的开区间终点' ,
    snapshot_max_candidate_id BIGINT NOT NULL COMMENT '冻结查询时可见的候选最大主键' ,
    expected_candidate_count INT NOT NULL COMMENT '预期纳入处理的候选数量' ,
    processed_candidate_count INT NOT NULL DEFAULT 0 COMMENT '已经处理的候选数量' ,
    locked_candidate_count INT NOT NULL DEFAULT 0 COMMENT '已经成功独占锁定的候选数量' ,
    last_candidate_id BIGINT NOT NULL DEFAULT 0 COMMENT '分段内最后一个候选主键' ,
    initial_delay_unit CHAR(1) NOT NULL COMMENT '首笔结算延迟的日期计算单位' ,
    initial_delay_days INT NOT NULL COMMENT '首笔结算延迟天数' ,
    regular_delay_days INT NOT NULL COMMENT '常规结算延迟天数' ,
    settlement_frequency VARCHAR(16) NOT NULL COMMENT '商户结算频率' ,
    frequency_day INT NULL COMMENT '结算频率对应的周内日或月内日' ,
    preview_json JSON NOT NULL COMMENT '提交时冻结的预览结果快照' ,
    task_status VARCHAR(24) NOT NULL COMMENT '任务处理状态' ,
    submitted_by_account_id BIGINT NOT NULL COMMENT '提交人账号主键' ,
    submitted_by_account_name VARCHAR(128) NOT NULL COMMENT '提交人账号名称快照' ,
    submitted_role_snapshot VARCHAR(1000) NOT NULL COMMENT '提交时的操作人角色快照' ,
    submit_client_ip VARCHAR(64) NOT NULL COMMENT '提交客户端地址，仅用于受控审计' ,
    submit_user_agent VARCHAR(500) NOT NULL COMMENT '提交客户端用户代理信息' ,
    submit_reason VARCHAR(400) NOT NULL COMMENT '提交原因' ,
    operation_time DATETIME(3) NOT NULL COMMENT '业务操作发生时间' ,
    processing_owner VARCHAR(128) NULL COMMENT '持有任务执行租约的工作节点标识' ,
    processing_deadline DATETIME(3) NULL COMMENT '任务执行租约的到期时间' ,
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已执行的重试次数' ,
    next_retry_time DATETIME(3) NOT NULL COMMENT '下次允许重试的时间' ,
    last_failure_code VARCHAR(64) NULL COMMENT '最近一次失败原因码' ,
    last_failure_message VARCHAR(500) NULL COMMENT '最近一次失败的受控说明' ,
    started_time DATETIME(3) NULL COMMENT '任务开始执行时间' ,
    completed_time DATETIME(3) NULL COMMENT '任务执行完成时间' ,
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，防止并发覆盖' ,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' ,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' ,
    active_profile_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN task_status IN ('PREVIEWED', 'QUEUED', 'PROCESSING', 'FINALIZING', 'CANCELLING')
             THEN settlement_profile_id ELSE NULL END
    ) STORED COMMENT '未终态任务的结算档案唯一占位，终态时为空' ,
    PRIMARY KEY (id),
    UNIQUE KEY uk_manual_review_task_no (task_no),
    UNIQUE KEY uk_manual_review_task_request (request_key),
    UNIQUE KEY uk_manual_review_task_submit_request (submit_request_key),
    UNIQUE KEY uk_manual_review_task_review (review_order_no),
    UNIQUE KEY uk_manual_review_task_active_profile (active_profile_id),
    KEY idx_manual_review_task_due (task_status, next_retry_time, id),
    KEY idx_manual_review_task_merchant (merchant_id, task_status, create_time, id),
    CONSTRAINT chk_manual_review_task_value CHECK (
        review_type = 'REGULAR'
        AND target_currency_exponent BETWEEN 0 AND 8
        AND cutoff_end_time > cutoff_begin_time
        AND snapshot_max_candidate_id >= 0
        AND expected_candidate_count >= 0
        AND processed_candidate_count BETWEEN 0 AND expected_candidate_count
        AND locked_candidate_count BETWEEN 0 AND expected_candidate_count
        AND last_candidate_id BETWEEN 0 AND snapshot_max_candidate_id
        AND initial_delay_unit IN ('T', 'D')
        AND initial_delay_days >= 1 AND regular_delay_days >= 1
        AND retry_count >= 0 AND version >= 0
    ),
    CONSTRAINT chk_manual_review_task_state CHECK (
        task_status IN ('PREVIEWED', 'QUEUED', 'PROCESSING', 'FINALIZING',
                        'COMPLETED', 'FAILED', 'CANCELLING', 'CANCELLED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='冻结筛选条件并分段生成结算预审单的服务端任务';

CREATE TABLE settlement_review_segment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键' ,
    segment_no VARCHAR(64) NOT NULL COMMENT '预审物理分段编号' ,
    review_order_no VARCHAR(19) NOT NULL COMMENT '结算预审单号' ,
    sequence_no INT NOT NULL COMMENT '同一预审单内的分段处理顺序' ,
    first_candidate_id BIGINT NOT NULL COMMENT '分段内首个候选主键' ,
    last_candidate_id BIGINT NOT NULL COMMENT '分段内最后一个候选主键' ,
    candidate_count INT NOT NULL COMMENT '冻结的候选总数量' ,
    projectable_candidate_count INT NOT NULL COMMENT '可生成交易结算投影的清分候选数量' ,
    source_fingerprint CHAR(64) NOT NULL COMMENT '冻结来源事实集合的摘要指纹' ,
    result_fingerprint CHAR(64) NOT NULL COMMENT '冻结财务计算结果的摘要指纹' ,
    net_signed_amount DECIMAL(24,8) NOT NULL COMMENT '分段有符号净结算金额，以目标币种主单位计价' ,
    settlement_batch_no VARCHAR(19) NULL COMMENT '关联结算批次号' ,
    segment_status VARCHAR(16) NOT NULL DEFAULT 'LOCKED' COMMENT '预审物理分段处理状态' ,
    processed_time DATETIME(3) NULL COMMENT '分段处理完成时间' ,
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，防止并发覆盖' ,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' ,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' ,
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_review_segment_no (segment_no),
    UNIQUE KEY uk_settlement_review_segment_sequence (review_order_no, sequence_no),
    UNIQUE KEY uk_settlement_review_segment_range
        (review_order_no, first_candidate_id, last_candidate_id),
    UNIQUE KEY uk_settlement_review_segment_batch (settlement_batch_no),
    KEY idx_settlement_review_segment_status (review_order_no, segment_status, sequence_no, id),
    CONSTRAINT chk_settlement_review_segment CHECK (
        sequence_no >= 1
        AND first_candidate_id > 0 AND last_candidate_id >= first_candidate_id
        AND candidate_count >= 1
        AND projectable_candidate_count BETWEEN 0 AND candidate_count
        AND segment_status IN ('LOCKED', 'CONSUMED', 'RELEASED')
        AND version >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='一个逻辑结算预审单对应的物理处理分段';

CREATE TABLE settlement_review_decision_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键' ,
    task_no VARCHAR(40) NOT NULL COMMENT '持久化任务编号' ,
    request_key VARCHAR(128) NOT NULL COMMENT '操作请求持久化幂等键' ,
    review_order_no VARCHAR(19) NOT NULL COMMENT '结算预审单号' ,
    expected_review_version BIGINT NOT NULL COMMENT '审批命令携带的预审单预期版本' ,
    decision_action VARCHAR(16) NOT NULL COMMENT '审批决定类型' ,
    decision_comment VARCHAR(400) NOT NULL COMMENT '审批决定说明' ,
    operator_account_id BIGINT NOT NULL COMMENT '操作人账号主键' ,
    operator_account_name VARCHAR(128) NOT NULL COMMENT '操作人账号名称快照' ,
    operator_role_snapshot VARCHAR(1000) NOT NULL COMMENT '操作人执行时的角色快照' ,
    client_ip VARCHAR(64) NOT NULL COMMENT '客户端地址，仅用于受控审计' ,
    user_agent VARCHAR(500) NOT NULL COMMENT '客户端用户代理信息' ,
    operation_time DATETIME(3) NOT NULL COMMENT '业务操作发生时间' ,
    total_segment_count INT NOT NULL COMMENT '待处理物理分段总数量' ,
    processed_segment_count INT NOT NULL DEFAULT 0 COMMENT '已完成物理分段数量' ,
    result_batch_count INT NOT NULL DEFAULT 0 COMMENT '已生成结算批次数量' ,
    first_settlement_batch_no VARCHAR(19) NULL COMMENT '最先生成的结算批次号' ,
    task_status VARCHAR(24) NOT NULL COMMENT '任务处理状态' ,
    processing_owner VARCHAR(128) NULL COMMENT '持有任务执行租约的工作节点标识' ,
    processing_deadline DATETIME(3) NULL COMMENT '任务执行租约的到期时间' ,
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已执行的重试次数' ,
    next_retry_time DATETIME(3) NOT NULL COMMENT '下次允许重试的时间' ,
    last_failure_code VARCHAR(64) NULL COMMENT '最近一次失败原因码' ,
    last_failure_message VARCHAR(500) NULL COMMENT '最近一次失败的受控说明' ,
    started_time DATETIME(3) NULL COMMENT '任务开始执行时间' ,
    completed_time DATETIME(3) NULL COMMENT '任务执行完成时间' ,
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，防止并发覆盖' ,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' ,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' ,
    active_review_order_no VARCHAR(19) GENERATED ALWAYS AS (
        CASE WHEN task_status IN ('QUEUED', 'PROCESSING', 'FAILED')
             THEN review_order_no ELSE NULL END
    ) STORED COMMENT '未终态决策任务的预审单唯一占位，终态时为空' ,
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_review_decision_task_no (task_no),
    UNIQUE KEY uk_settlement_review_decision_request (request_key),
    UNIQUE KEY uk_settlement_review_decision_active (active_review_order_no),
    KEY idx_settlement_review_decision_due (task_status, next_retry_time, id),
    KEY idx_settlement_review_decision_order (review_order_no, create_time, id),
    CONSTRAINT chk_settlement_review_decision_task CHECK (
        expected_review_version >= 0
        AND decision_action IN ('APPROVE', 'REJECT', 'CANCEL')
        AND total_segment_count >= 1
        AND processed_segment_count BETWEEN 0 AND total_segment_count
        AND result_batch_count BETWEEN 0 AND processed_segment_count
        AND retry_count >= 0 AND version >= 0
        AND task_status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')
        AND ((decision_action = 'APPROVE')
             OR (result_batch_count = 0 AND first_settlement_batch_no IS NULL))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='可恢复的结算预审双人复核分段决策任务';

ALTER TABLE settlement_review_order
    DROP CHECK chk_settlement_review_value,
    DROP CHECK chk_settlement_review_state;

ALTER TABLE settlement_review_order
    ADD CONSTRAINT chk_settlement_review_value CHECK (
        target_currency_exponent BETWEEN 0 AND 8
        AND cutoff_end_time > cutoff_begin_time
        AND candidate_count >= 1
        AND projectable_candidate_count BETWEEN 0 AND candidate_count
        AND net_amount >= 0
        AND version >= 0
    ),
    ADD CONSTRAINT chk_settlement_review_state CHECK (
        review_type IN ('REGULAR', 'RESERVE_RELEASE', 'ADJUSTMENT')
        AND create_mode IN ('MANUAL', 'MANUAL_ASYNC', 'AUTO_REVIEW')
        AND net_direction IN ('CREDIT', 'DEBIT')
        AND review_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED')
        AND ((review_status = 'PENDING_APPROVAL'
              AND decided_by_account_id IS NULL AND decision_action IS NULL
              AND decision_request_key IS NULL AND decision_time IS NULL
              AND review_comment IS NULL
              AND settlement_batch_no IS NULL)
             OR (review_status = 'APPROVED'
                 AND decided_by_account_id IS NOT NULL AND decided_by_account_name IS NOT NULL
                 AND decided_role_snapshot IS NOT NULL AND decision_client_ip IS NOT NULL
                 AND decision_user_agent IS NOT NULL AND decision_action = 'APPROVE'
                 AND decision_request_key IS NOT NULL AND decision_time IS NOT NULL
                 AND review_comment IS NOT NULL
                 AND settlement_batch_no IS NOT NULL
                 AND submitted_by_account_id <> decided_by_account_id)
             OR (review_status = 'REJECTED'
                 AND decided_by_account_id IS NOT NULL AND decided_by_account_name IS NOT NULL
                 AND decided_role_snapshot IS NOT NULL AND decision_client_ip IS NOT NULL
                 AND decision_user_agent IS NOT NULL AND decision_action = 'REJECT'
                 AND decision_request_key IS NOT NULL AND decision_time IS NOT NULL
                 AND review_comment IS NOT NULL
                 AND submitted_by_account_id <> decided_by_account_id
                 AND settlement_batch_no IS NULL)
             OR (review_status = 'CANCELLED'
                 AND decided_by_account_id IS NOT NULL AND decided_by_account_name IS NOT NULL
                 AND decided_role_snapshot IS NOT NULL AND decision_client_ip IS NOT NULL
                 AND decision_user_agent IS NOT NULL AND decision_action = 'CANCEL'
                 AND decision_request_key IS NOT NULL AND decision_time IS NOT NULL
                 AND review_comment IS NOT NULL
                 AND submitted_by_account_id = decided_by_account_id
                 AND settlement_batch_no IS NULL)
             OR (review_status = 'EXPIRED'
                 AND decided_by_account_id IS NOT NULL AND decided_by_account_name IS NOT NULL
                 AND decided_role_snapshot IS NOT NULL AND decision_client_ip IS NOT NULL
                 AND decision_user_agent IS NOT NULL AND decision_action = 'EXPIRE'
                 AND decision_request_key IS NOT NULL AND decision_time IS NOT NULL
                 AND review_comment IS NOT NULL
                 AND settlement_batch_no IS NULL))
    );

ALTER TABLE settlement_candidate
    ADD KEY idx_settlement_candidate_manual_review
        (merchant_id, settlement_profile_id, target_currency, candidate_status,
         shadow_mode, settlement_eligible_date, create_time, id);

ALTER TABLE settlement_batch
    DROP INDEX uk_settlement_batch_review,
    ADD KEY idx_settlement_batch_review (review_order_no, batch_status, id);

ALTER TABLE settlement_batch_rate
    DROP INDEX uk_settlement_batch_review_rate,
    ADD UNIQUE KEY uk_settlement_batch_review_rate
        (settlement_batch_no, review_rate_id);
