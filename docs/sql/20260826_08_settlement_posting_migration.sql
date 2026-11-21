-- 结算资金提交、保证金资金化、交易投影和可靠 MQ Outbox 正式迁移。
-- 前置：20260825_03、20260825_04、20260826_01 已按顺序完成且 07 前检全部为 0。

SET NAMES utf8mb4;

-- 一条原批只能有一条冲正链；NULL 原批不会互相冲突。
ALTER TABLE settlement_batch
    ADD UNIQUE KEY uk_settlement_single_reversal (original_batch_no, batch_type);

-- MySQL 唯一键允许多个 NULL，使用生成槽保证每批恰好最多一条 LEDGER_POSTING。
ALTER TABLE settlement_result_item
    ADD COLUMN ledger_posting_slot TINYINT
        GENERATED ALWAYS AS (CASE WHEN result_role = 'LEDGER_POSTING' THEN 1 ELSE NULL END) STORED
        COMMENT '批次级最终入账唯一槽；非入账结果为空',
    ADD UNIQUE KEY uk_settlement_one_ledger_posting
        (settlement_batch_no, ledger_posting_slot);

-- 保证金聚合继续使用原字段 currency，其业务语义固定为原交易标签/保证金币种。
ALTER TABLE merchant_reserve_item
    DROP CHECK chk_reserve_amount,
    ADD COLUMN returned_amount DECIMAL(24,8) NOT NULL DEFAULT 0
        COMMENT '退款等动作已返还的原标签币种保证金' AFTER retained_amount,
    ADD COLUMN reversed_amount DECIMAL(24,8) NOT NULL DEFAULT 0
        COMMENT '冲正撤销的原标签币种保证金' AFTER released_amount,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0
        COMMENT '保证金聚合并发CAS版本' AFTER release_batch_no,
    ADD CONSTRAINT chk_reserve_amount CHECK (
        retained_amount > 0
        AND returned_amount >= 0
        AND released_amount >= 0
        AND reversed_amount >= 0
        AND returned_amount + released_amount + reversed_amount <= retained_amount
    );

CREATE TABLE merchant_reserve_action (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    reserve_action_no VARCHAR(64) NOT NULL COMMENT '保证金动作全局幂等号',
    reserve_item_id BIGINT NOT NULL COMMENT '保证金聚合ID',
    reserve_no VARCHAR(64) NOT NULL COMMENT '保证金聚合业务号快照',
    settlement_batch_no VARCHAR(19) NOT NULL COMMENT '应用本动作的结算批次号',
    candidate_id BIGINT NOT NULL COMMENT '来源结算候选ID',
    source_reserve_detail_no VARCHAR(64) NOT NULL COMMENT '来源保证金清分明细或被冲正动作号',
    action_type VARCHAR(32) NOT NULL
        COMMENT '保证金动作类型：留存、退回、释放以及对应的冲正动作',
    currency CHAR(3) NOT NULL COMMENT '原交易标签/保证金币种；禁止换汇',
    amount DECIMAL(24,8) NOT NULL COMMENT '原保证金币种正数金额',
    action_time DATETIME(3) NOT NULL COMMENT '动作应用时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reserve_action_no (reserve_action_no),
    UNIQUE KEY uk_reserve_action_source_batch (settlement_batch_no, source_reserve_detail_no),
    KEY idx_reserve_action_item (reserve_item_id, id),
    KEY idx_reserve_action_batch (settlement_batch_no, candidate_id, id),
    CONSTRAINT chk_reserve_action_value CHECK (
        amount > 0
        AND action_type IN (
            'HOLD', 'RETURN', 'RELEASE',
            'REVERSAL_HOLD', 'REVERSAL_RETURN', 'REVERSAL_RELEASE'
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='保证金聚合不可变动作和最终幂等事实；保证金始终使用原标签币种';

CREATE TABLE settlement_projection_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    task_no VARCHAR(64) NOT NULL COMMENT '投影任务全局幂等号',
    settlement_batch_no VARCHAR(19) NOT NULL COMMENT '结算或冲正批次号',
    projection_action VARCHAR(16) NOT NULL DEFAULT 'SETTLE' COMMENT '交易投影动作：结算或冲正',
    original_batch_no VARCHAR(19) NULL COMMENT 'REVERSE 引用的原结算批次号',
    candidate_id BIGINT NOT NULL COMMENT '来源结算候选ID',
    transaction_id VARCHAR(64) NOT NULL COMMENT '动作级交易号',
    transaction_date_time DATETIME(3) NOT NULL COMMENT '动作季度精确路由时间',
    clearing_revision INT NOT NULL COMMENT '被结算清分修订号',
    operation_id VARCHAR(64) NOT NULL COMMENT '交易生命周期操作号和FIFO分组键',
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号',
    settlement_currency CHAR(3) NOT NULL COMMENT '批次目标结算币种',
    settlement_amount DECIMAL(24,8) NOT NULL COMMENT '候选有符号结算净额；冲正为原值相反数',
    settlement_date DATE NOT NULL COMMENT '结算或冲正业务日期',
    task_status VARCHAR(16) NOT NULL DEFAULT 'INIT' COMMENT '投影任务状态：初始、处理中、失败或已完成',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '投影重试次数',
    next_retry_time DATETIME(3) NULL COMMENT '下次允许处理时间',
    last_failure_code VARCHAR(64) NULL COMMENT '最近稳定失败码',
    completed_time DATETIME(3) NULL COMMENT '投影和Outbox完成时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '任务状态CAS版本',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_projection_task_no (task_no),
    UNIQUE KEY uk_settlement_projection_batch_candidate (settlement_batch_no, candidate_id),
    KEY idx_settlement_projection_due (task_status, next_retry_time, id),
    KEY idx_settlement_projection_transaction (transaction_id, transaction_date_time, id),
    CONSTRAINT chk_settlement_projection_value CHECK (
        clearing_revision >= 1 AND retry_count >= 0
        AND projection_action IN ('SETTLE', 'REVERSE')
        AND task_status IN ('INIT', 'PROCESSING', 'FAILED', 'COMPLETED')
        AND ((projection_action = 'SETTLE' AND original_batch_no IS NULL)
             OR (projection_action = 'REVERSE' AND original_batch_no IS NOT NULL))
        AND ((task_status = 'COMPLETED' AND completed_time IS NOT NULL AND next_retry_time IS NULL)
             OR task_status <> 'COMPLETED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='结算后异步更新交易财务和动作查询投影的可靠任务';

CREATE TABLE settlement_event_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    event_no VARCHAR(96) NOT NULL COMMENT '结算事件全局幂等号',
    settlement_batch_no VARCHAR(19) NOT NULL COMMENT '来源结算或冲正批次号',
    candidate_id BIGINT NOT NULL COMMENT '来源候选ID',
    topic VARCHAR(128) NOT NULL COMMENT '冻结RocketMQ Topic',
    tag VARCHAR(64) NOT NULL COMMENT '冻结RocketMQ Tag',
    message_key VARCHAR(128) NOT NULL COMMENT 'MQ消息幂等键',
    message_group VARCHAR(64) NOT NULL COMMENT 'operationId FIFO分组键',
    payload_json JSON NOT NULL COMMENT '不含卡和账单敏感数据的冻结消息JSON',
    event_status VARCHAR(16) NOT NULL DEFAULT 'INIT' COMMENT '事件投递状态：初始、处理中、失败或已发送',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '发送失败次数',
    next_retry_time DATETIME(3) NULL COMMENT '下次发送时间',
    processing_owner VARCHAR(128) NULL COMMENT '发送租约所有者',
    processing_deadline DATETIME(3) NULL COMMENT '发送租约截止时间',
    sent_time DATETIME(3) NULL COMMENT '最近成功发送时间',
    last_failure_code VARCHAR(64) NULL COMMENT '最近发送失败类型',
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Outbox状态CAS版本',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_event_no (event_no),
    UNIQUE KEY uk_settlement_event_candidate (settlement_batch_no, candidate_id, tag),
    KEY idx_settlement_event_due (event_status, next_retry_time, id),
    KEY idx_settlement_event_lease (event_status, processing_deadline, id),
    CONSTRAINT chk_settlement_event_value CHECK (
        retry_count >= 0
        AND event_status IN ('INIT', 'PROCESSING', 'FAILED', 'SENT')
        AND ((event_status = 'PROCESSING'
              AND processing_owner IS NOT NULL AND processing_deadline IS NOT NULL)
             OR event_status <> 'PROCESSING')
        AND ((event_status = 'SENT' AND sent_time IS NOT NULL) OR event_status <> 'SENT')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='结算完成和冲正完成交易级FIFO消息可靠Outbox';

-- 当前环境每个商户只有一个正常USD账户；初始化档案后服务启动即可自动处理新候选。
INSERT INTO merchant_settlement_profile
(settlement_profile_no, merchant_id, settlement_account_id, target_currency,
 target_currency_exponent, business_time_zone, daily_cutoff_time, processing_mode, profile_status,
 active_slot, effective_date, expire_date, version, create_time, update_time)
SELECT CONCAT('MSP-', account.merchant_id, '-', account.settlement_currency),
       account.merchant_id, account.id, account.settlement_currency,
       CASE
           WHEN account.settlement_currency IN ('JPY', 'KRW') THEN 0
           WHEN account.settlement_currency IN ('BHD', 'JOD', 'KWD', 'OMR', 'TND') THEN 3
           ELSE 2
       END,
       'Asia/Shanghai', '00:00:00', 'AUTO_POST', 'ACTIVE', 1, CURRENT_DATE(), NULL, 0,
       CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM merchant_fund_account account
WHERE account.deleted = 0
  AND account.account_status = 'NORMAL'
  AND NOT EXISTS (
      SELECT 1
      FROM merchant_settlement_profile profile
      WHERE profile.merchant_id = account.merchant_id
        AND profile.profile_status = 'ACTIVE'
        AND profile.active_slot = 1
  );
