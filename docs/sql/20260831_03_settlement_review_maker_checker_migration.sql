-- Settlement review Maker-Checker schema migration.
-- Prerequisite: 20260831_01_settlement_p0_consistency_migration.sql has completed.

SET NAMES utf8mb4;

-- Must return zero before migration. Candidate audit relations are the authority for projection counts.
SELECT COUNT(*) AS invalid_existing_batch_projection_count
FROM settlement_batch batch
LEFT JOIN (
    SELECT relation.settlement_batch_no,
           COUNT(*) AS relation_count,
           SUM(relation.source_type = 'CLEARING_REVISION') AS projectable_candidate_count
    FROM settlement_batch_candidate relation
    GROUP BY relation.settlement_batch_no
) projection ON projection.settlement_batch_no = batch.settlement_batch_no
WHERE COALESCE(projection.relation_count, 0) <> batch.candidate_count
   OR COALESCE(projection.projectable_candidate_count, 0) > batch.candidate_count;

CREATE TABLE settlement_review_daily_sequence (
    business_date DATE NOT NULL COMMENT '结算业务日期' ,
    current_sequence INT NOT NULL DEFAULT 0 COMMENT '业务日期内已分配的最大流水序号' ,
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，防止并发覆盖' ,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' ,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' ,
    PRIMARY KEY (business_date),
    CONSTRAINT chk_settlement_review_sequence CHECK (
        current_sequence BETWEEN 0 AND 99999999 AND version >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='结算预审单号的数据库业务日流水序列表';

CREATE TABLE settlement_review_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键' ,
    review_order_no VARCHAR(19) NOT NULL COMMENT '结算预审单号' ,
    create_request_key VARCHAR(128) NOT NULL COMMENT '创建请求持久化幂等键' ,
    selection_fingerprint CHAR(64) NOT NULL COMMENT '候选编号及预期版本集合的摘要指纹' ,
    review_type VARCHAR(24) NOT NULL COMMENT '结算预审类型，区分常规结算、保证金释放和调整' ,
    create_mode VARCHAR(24) NOT NULL DEFAULT 'MANUAL' COMMENT '结算创建模式，区分自动入账、自动预审和人工选择' ,
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号' ,
    settlement_profile_id BIGINT NOT NULL COMMENT '冻结的商户结算档案主键' ,
    settlement_account_id BIGINT NOT NULL COMMENT '目标商户结算资金账户主键' ,
    target_currency CHAR(3) NOT NULL COMMENT '目标结算币种代码' ,
    target_currency_exponent TINYINT NOT NULL COMMENT '目标结算币种小数位' ,
    business_date DATE NOT NULL COMMENT '结算业务日期' ,
    business_time_zone VARCHAR(64) NOT NULL COMMENT '解释结算业务时间的时区标识' ,
    cutoff_begin_time DATETIME(3) NOT NULL COMMENT '候选时间窗口的闭区间起点' ,
    cutoff_end_time DATETIME(3) NOT NULL COMMENT '候选时间窗口的开区间终点' ,
    candidate_count INT NOT NULL COMMENT '冻结的候选总数量' ,
    projectable_candidate_count INT NOT NULL DEFAULT 0 COMMENT '可生成交易结算投影的清分候选数量' ,
    source_fingerprint CHAR(64) NOT NULL COMMENT '冻结来源事实集合的摘要指纹' ,
    rate_fingerprint CHAR(64) NOT NULL COMMENT '冻结统一汇率矩阵的摘要指纹' ,
    result_fingerprint CHAR(64) NOT NULL COMMENT '冻结财务计算结果的摘要指纹' ,
    net_direction VARCHAR(8) NOT NULL COMMENT '净结算收付方向' ,
    net_amount DECIMAL(24,8) NOT NULL COMMENT '冻结净结算金额，以目标币种主单位计价' ,
    review_status VARCHAR(24) NOT NULL COMMENT '结算预审单状态' ,
    created_by_account_id BIGINT NOT NULL COMMENT '创建人账号主键' ,
    created_by_account_name VARCHAR(128) NOT NULL COMMENT '创建人账号名称快照' ,
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
    review_comment VARCHAR(400) NULL COMMENT '结算预审意见' ,
    decision_time DATETIME(3) NULL COMMENT '审批决定发生时间' ,
    settlement_batch_no VARCHAR(19) NULL COMMENT '关联结算批次号' ,
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，防止并发覆盖' ,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' ,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' ,
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_review_no (review_order_no),
    UNIQUE KEY uk_settlement_review_create_request (create_request_key),
    UNIQUE KEY uk_settlement_review_decision_request (decision_request_key),
    UNIQUE KEY uk_settlement_review_batch (settlement_batch_no),
    KEY idx_settlement_review_merchant_status (merchant_id, review_status, create_time, id),
    KEY idx_settlement_review_status_time (review_status, submitted_time, id),
    CONSTRAINT chk_settlement_review_value CHECK (
        target_currency_exponent BETWEEN 0 AND 8
        AND cutoff_end_time > cutoff_begin_time
        AND candidate_count BETWEEN 1 AND 1000
        AND projectable_candidate_count BETWEEN 0 AND candidate_count
        AND net_amount >= 0
        AND version >= 0
    ),
    CONSTRAINT chk_settlement_review_state CHECK (
        review_type IN ('REGULAR', 'RESERVE_RELEASE', 'ADJUSTMENT')
        AND create_mode IN ('MANUAL', 'AUTO_REVIEW')
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
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='结算预审主单及双人复核的不可变审计事实';

CREATE TABLE settlement_review_candidate (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键' ,
    review_candidate_no VARCHAR(34) NOT NULL COMMENT '预审候选关系编号' ,
    review_order_no VARCHAR(19) NOT NULL COMMENT '结算预审单号' ,
    candidate_id BIGINT NOT NULL COMMENT '结算候选物理主键' ,
    candidate_no VARCHAR(64) NOT NULL COMMENT '结算候选稳定业务编号' ,
    source_type VARCHAR(24) NOT NULL COMMENT '候选来源业务类型' ,
    source_business_id VARCHAR(64) NOT NULL COMMENT '候选来源业务记录主键' ,
    source_revision INT NOT NULL COMMENT '来源清分事实修订号' ,
    source_transaction_id VARCHAR(64) NOT NULL COMMENT '来源系统交易号' ,
    source_transaction_date_time DATETIME(3) NOT NULL COMMENT '来源交易的精确路由时间' ,
    locked_candidate_version BIGINT NOT NULL COMMENT '独占锁定时的候选版本号' ,
    clearing_fingerprint CHAR(64) NOT NULL COMMENT '锁定的清分事实摘要指纹' ,
    relation_status VARCHAR(16) NOT NULL COMMENT '候选认领关系状态' ,
    locked_time DATETIME(3) NOT NULL COMMENT '候选独占锁定时间' ,
    consumed_time DATETIME(3) NULL COMMENT '预审候选被结算批次消费的时间' ,
    released_time DATETIME(3) NULL COMMENT '预审候选锁定关系释放时间' ,
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，防止并发覆盖' ,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' ,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' ,
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_review_candidate_no (review_candidate_no),
    UNIQUE KEY uk_settlement_review_candidate (review_order_no, candidate_id),
    KEY idx_settlement_review_candidate_id (candidate_id, relation_status, id),
    CONSTRAINT chk_settlement_review_candidate_state CHECK (
        source_type IN ('CLEARING_REVISION', 'RESERVE_RELEASE', 'ADJUSTMENT')
        AND source_revision >= 1 AND locked_candidate_version >= 0 AND version >= 0
        AND relation_status IN ('LOCKED', 'CONSUMED', 'RELEASED')
        AND ((relation_status = 'LOCKED' AND consumed_time IS NULL AND released_time IS NULL)
             OR (relation_status = 'CONSUMED' AND consumed_time IS NOT NULL AND released_time IS NULL)
             OR (relation_status = 'RELEASED' AND consumed_time IS NULL AND released_time IS NOT NULL))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='结算预审候选的不可变选择与锁定快照';

CREATE TABLE settlement_review_rate (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键' ,
    review_order_no VARCHAR(19) NOT NULL COMMENT '结算预审单号' ,
    source_currency CHAR(3) NOT NULL COMMENT '来源金额币种代码' ,
    target_currency CHAR(3) NOT NULL COMMENT '目标结算币种代码' ,
    direct_rate DECIMAL(24,12) NOT NULL COMMENT '来源币种兑换目标币种的直接汇率' ,
    source_currency_exponent TINYINT NOT NULL COMMENT '来源币种小数位' ,
    target_currency_exponent TINYINT NOT NULL COMMENT '目标结算币种小数位' ,
    rate_source VARCHAR(64) NOT NULL COMMENT '汇率取值来源' ,
    quote_id VARCHAR(128) NULL COMMENT '冻结采用的汇率报价标识' ,
    source_quote_direction VARCHAR(16) NOT NULL COMMENT '原始报价方向' ,
    effective_time DATETIME(3) NOT NULL COMMENT '汇率生效时间' ,
    locked_time DATETIME(3) NOT NULL COMMENT '候选独占锁定时间' ,
    locked_by VARCHAR(128) NOT NULL COMMENT '锁定汇率的操作人标识' ,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' ,
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_review_rate_pair (review_order_no, source_currency, target_currency),
    CONSTRAINT chk_settlement_review_rate CHECK (
        direct_rate > 0
        AND source_currency_exponent BETWEEN 0 AND 8
        AND target_currency_exponent BETWEEN 0 AND 8
        AND source_quote_direction IN ('DIRECT', 'INVERSE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='结算预审冻结的统一汇率矩阵';

CREATE TABLE settlement_review_summary (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键' ,
    review_order_no VARCHAR(19) NOT NULL COMMENT '结算预审单号' ,
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号' ,
    payment_type VARCHAR(32) NOT NULL COMMENT '支付业务类型' ,
    payment_method VARCHAR(32) NOT NULL COMMENT '收单支付方式' ,
    transaction_type VARCHAR(32) NOT NULL COMMENT '收单交易类型' ,
    result_item_type VARCHAR(32) NOT NULL COMMENT '结算结果明细类型' ,
    fee_category VARCHAR(32) NOT NULL COMMENT '费用分类编码' ,
    direction VARCHAR(8) NOT NULL COMMENT '金额收付方向' ,
    source_currency CHAR(3) NOT NULL COMMENT '来源金额币种代码' ,
    target_currency CHAR(3) NOT NULL COMMENT '目标结算币种代码' ,
    transaction_count BIGINT NOT NULL COMMENT '纳入汇总的交易笔数' ,
    source_amount DECIMAL(24,8) NOT NULL COMMENT '按来源币种计价的汇总金额' ,
    target_amount DECIMAL(24,8) NOT NULL COMMENT '按目标币种计价的汇总金额' ,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' ,
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_review_summary (
        review_order_no, payment_type, payment_method, transaction_type,
        result_item_type, fee_category, direction, source_currency, target_currency
    ),
    CONSTRAINT chk_settlement_review_summary CHECK (
        direction IN ('CREDIT', 'DEBIT')
        AND transaction_count > 0 AND source_amount >= 0 AND target_amount >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='结算预审冻结的财务汇总结果';

-- MySQL 8 DROP CHECK requires the deployed symbol to exist; tolerate partial baselines safely.
DROP PROCEDURE IF EXISTS drop_settlement_review_check;
DELIMITER $$
CREATE PROCEDURE drop_settlement_review_check(
    IN target_table VARCHAR(64),
    IN target_constraint VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = target_table
          AND constraint_name = target_constraint
          AND constraint_type = 'CHECK'
    ) THEN
        SET @drop_review_check_sql = CONCAT(
            'ALTER TABLE `', target_table, '` DROP CHECK `', target_constraint, '`'
        );
        PREPARE drop_review_check_stmt FROM @drop_review_check_sql;
        EXECUTE drop_review_check_stmt;
        DEALLOCATE PREPARE drop_review_check_stmt;
    END IF;
END$$
DELIMITER ;

CALL drop_settlement_review_check('settlement_candidate', 'chk_settlement_candidate_state');
DROP PROCEDURE drop_settlement_review_check;

ALTER TABLE settlement_candidate
    ADD COLUMN review_order_no VARCHAR(19) NULL  COMMENT '结算预审单号' AFTER settlement_batch_no,
    ADD COLUMN review_locked_time DATETIME(3) NULL  COMMENT '候选被预审单独占锁定的时间' AFTER review_order_no,
    ADD KEY idx_settlement_candidate_review (review_order_no, candidate_status, id),
    ADD CONSTRAINT chk_settlement_candidate_state CHECK (
        source_type IN ('CLEARING_REVISION', 'RESERVE_RELEASE', 'ADJUSTMENT')
        AND candidate_status IN (
            'READY', 'REPLAY_HOLD', 'REVIEW_LOCKED', 'SUPERSEDED',
            'CLAIMED', 'POSTED', 'MANUAL_REVIEW', 'CANCELLED'
        )
        AND ((candidate_status IN ('READY', 'REPLAY_HOLD', 'SUPERSEDED')
              AND settlement_batch_no IS NULL AND review_order_no IS NULL)
             OR (candidate_status = 'REVIEW_LOCKED'
                 AND settlement_batch_no IS NULL AND review_order_no IS NOT NULL
                 AND review_locked_time IS NOT NULL)
             OR (candidate_status IN ('CLAIMED', 'POSTED', 'MANUAL_REVIEW', 'CANCELLED')
                 AND settlement_batch_no IS NOT NULL))
    );

ALTER TABLE settlement_batch
    ADD COLUMN review_order_no VARCHAR(19) NULL  COMMENT '结算预审单号' AFTER original_batch_no,
    ADD COLUMN create_mode VARCHAR(24) NOT NULL DEFAULT 'AUTO'  COMMENT '结算创建模式，区分自动入账、自动预审和人工选择' AFTER review_order_no,
    ADD COLUMN projectable_candidate_count INT NOT NULL DEFAULT 0  COMMENT '可生成交易结算投影的清分候选数量' AFTER candidate_count,
    ADD COLUMN result_fingerprint CHAR(64) NULL  COMMENT '冻结财务计算结果的摘要指纹' AFTER projectable_candidate_count,
    ADD COLUMN maker_account_id BIGINT NULL  COMMENT '申请人账号主键' AFTER result_fingerprint,
    ADD COLUMN maker_account_name VARCHAR(128) NULL  COMMENT '申请人账号名称快照' AFTER maker_account_id,
    ADD COLUMN maker_role_snapshot VARCHAR(1000) NULL  COMMENT '申请人提交时的角色快照' AFTER maker_account_name,
    ADD COLUMN maker_client_ip VARCHAR(64) NULL  COMMENT '申请客户端地址，仅用于受控审计' AFTER maker_role_snapshot,
    ADD COLUMN maker_user_agent VARCHAR(500) NULL  COMMENT '申请客户端用户代理信息' AFTER maker_client_ip,
    ADD COLUMN maker_reason VARCHAR(400) NULL  COMMENT '申请原因' AFTER maker_user_agent,
    ADD COLUMN maker_time DATETIME(3) NULL  COMMENT '申请提交时间' AFTER maker_reason,
    ADD COLUMN checker_account_id BIGINT NULL  COMMENT '复核人账号主键' AFTER maker_time,
    ADD COLUMN checker_account_name VARCHAR(128) NULL  COMMENT '复核人账号名称快照' AFTER checker_account_id,
    ADD COLUMN checker_role_snapshot VARCHAR(1000) NULL  COMMENT '复核人操作时的角色快照' AFTER checker_account_name,
    ADD COLUMN checker_client_ip VARCHAR(64) NULL  COMMENT '复核客户端地址，仅用于受控审计' AFTER checker_role_snapshot,
    ADD COLUMN checker_user_agent VARCHAR(500) NULL  COMMENT '复核客户端用户代理信息' AFTER checker_client_ip,
    ADD COLUMN checker_comment VARCHAR(400) NULL  COMMENT '复核意见' AFTER checker_user_agent,
    ADD COLUMN checker_time DATETIME(3) NULL  COMMENT '复核决定时间' AFTER checker_comment,
    ADD UNIQUE KEY uk_settlement_batch_review (review_order_no),
    ADD CONSTRAINT chk_settlement_batch_review_audit CHECK (
        create_mode IN ('AUTO', 'MANUAL_REVIEW')
        AND projectable_candidate_count BETWEEN 0 AND candidate_count
        AND ((create_mode = 'AUTO')
             OR (review_order_no IS NOT NULL AND result_fingerprint IS NOT NULL
                 AND maker_account_id IS NOT NULL AND maker_account_name IS NOT NULL
                 AND maker_role_snapshot IS NOT NULL AND maker_client_ip IS NOT NULL
                 AND maker_user_agent IS NOT NULL
                 AND maker_reason IS NOT NULL AND maker_time IS NOT NULL
                 AND checker_account_id IS NOT NULL AND checker_account_name IS NOT NULL
                 AND checker_role_snapshot IS NOT NULL AND checker_client_ip IS NOT NULL
                 AND checker_user_agent IS NOT NULL
                 AND checker_comment IS NOT NULL AND checker_time IS NOT NULL
                 AND maker_account_id <> checker_account_id))
    );

-- Backfill existing AUTO batches before application code starts maintaining both counters in one CAS.
UPDATE settlement_batch batch
LEFT JOIN (
    SELECT relation.settlement_batch_no,
           SUM(relation.source_type = 'CLEARING_REVISION') AS projectable_candidate_count
    FROM settlement_batch_candidate relation
    GROUP BY relation.settlement_batch_no
) projection ON projection.settlement_batch_no = batch.settlement_batch_no
SET batch.projectable_candidate_count = COALESCE(projection.projectable_candidate_count, 0);

ALTER TABLE settlement_batch_rate
    ADD COLUMN review_rate_id BIGINT NULL  COMMENT '采用的预审统一汇率记录主键' AFTER settlement_batch_no,
    ADD UNIQUE KEY uk_settlement_batch_review_rate (review_rate_id);
