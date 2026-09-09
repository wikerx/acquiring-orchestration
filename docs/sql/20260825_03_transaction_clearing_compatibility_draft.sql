-- 收单交易清分第三阶段迁移包 2/4：兼容字段扩展草案。
-- 状态：仅供评审，禁止未经 DBA 审批直接在 UAT/生产执行。
-- 前置：02 前检全部通过；逐表评估 MDL、执行算法、临时空间和主从延迟，禁止交易高峰直接接受 COPY。
-- 发布顺序：数据库兼容扩展先于引用新列的应用；本文件不创建清分消费者、不修改余额、不执行历史清分。

SET NAMES utf8mb4;

-- ============================================================================
-- 3. Transaction Outbox 投递模式；兼容普通、顺序和 RocketMQ 5.x 定时消息
-- ============================================================================

ALTER TABLE transaction_event_outbox
    ADD COLUMN delivery_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO'
        COMMENT 'AUTO、NORMAL、ORDERLY、SCHEDULED；AUTO兼容历史message_group逻辑' AFTER message_group,
    ADD COLUMN deliver_at DATETIME(3) NULL
        COMMENT 'SCHEDULED消息的Broker最早投递UTC时间；其它模式为空' AFTER delivery_mode,
    ADD KEY idx_delivery_mode_time (delivery_mode, deliver_at, transaction_date_time, id),
    ADD CONSTRAINT chk_event_delivery_mode_tpl CHECK (
        delivery_mode IN ('AUTO', 'NORMAL', 'ORDERLY', 'SCHEDULED')
        AND (delivery_mode <> 'ORDERLY' OR message_group IS NOT NULL)
        AND (delivery_mode <> 'SCHEDULED' OR deliver_at IS NOT NULL)
        AND (delivery_mode = 'SCHEDULED' OR deliver_at IS NULL)
    );

ALTER TABLE transaction_event_outbox_202603
    ADD COLUMN delivery_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO'
        COMMENT 'AUTO、NORMAL、ORDERLY、SCHEDULED；AUTO兼容历史message_group逻辑' AFTER message_group,
    ADD COLUMN deliver_at DATETIME(3) NULL
        COMMENT 'SCHEDULED消息的Broker最早投递UTC时间；其它模式为空' AFTER delivery_mode,
    ADD KEY idx_delivery_mode_time (delivery_mode, deliver_at, transaction_date_time, id),
    ADD CONSTRAINT chk_event_delivery_mode_202603 CHECK (
        delivery_mode IN ('AUTO', 'NORMAL', 'ORDERLY', 'SCHEDULED')
        AND (delivery_mode <> 'ORDERLY' OR message_group IS NOT NULL)
        AND (delivery_mode <> 'SCHEDULED' OR deliver_at IS NOT NULL)
        AND (delivery_mode = 'SCHEDULED' OR deliver_at IS NULL)
    );

ALTER TABLE transaction_event_outbox_202604
    ADD COLUMN delivery_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO'
        COMMENT 'AUTO、NORMAL、ORDERLY、SCHEDULED；AUTO兼容历史message_group逻辑' AFTER message_group,
    ADD COLUMN deliver_at DATETIME(3) NULL
        COMMENT 'SCHEDULED消息的Broker最早投递UTC时间；其它模式为空' AFTER delivery_mode,
    ADD KEY idx_delivery_mode_time (delivery_mode, deliver_at, transaction_date_time, id),
    ADD CONSTRAINT chk_event_delivery_mode_202604 CHECK (
        delivery_mode IN ('AUTO', 'NORMAL', 'ORDERLY', 'SCHEDULED')
        AND (delivery_mode <> 'ORDERLY' OR message_group IS NOT NULL)
        AND (delivery_mode <> 'SCHEDULED' OR deliver_at IS NOT NULL)
        AND (delivery_mode = 'SCHEDULED' OR deliver_at IS NULL)
    );

-- ============================================================================
-- 4. 交易动作清分查询投影
-- ============================================================================

ALTER TABLE transaction_operation
    ADD COLUMN clearing_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CLEARED'
        COMMENT '动作清分查询投影：NOT_CLEARED、PENDING、CLEARED、FAILED、NOT_REQUIRED' AFTER accounting_status,
    ADD COLUMN clearing_complete_time DATETIME(3) NULL
        COMMENT '最近一次有效清分完成时间' AFTER clearing_status,
    ADD COLUMN clearing_failure_code VARCHAR(64) NULL
        COMMENT '最近一次清分失败码；成功或无需清分时为空' AFTER clearing_complete_time,
    ADD KEY idx_clearing_status_time (clearing_status, transaction_date_time, id);

ALTER TABLE transaction_operation_202603
    ADD COLUMN clearing_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CLEARED'
        COMMENT '动作清分查询投影：NOT_CLEARED、PENDING、CLEARED、FAILED、NOT_REQUIRED' AFTER accounting_status,
    ADD COLUMN clearing_complete_time DATETIME(3) NULL
        COMMENT '最近一次有效清分完成时间' AFTER clearing_status,
    ADD COLUMN clearing_failure_code VARCHAR(64) NULL
        COMMENT '最近一次清分失败码；成功或无需清分时为空' AFTER clearing_complete_time,
    ADD KEY idx_clearing_status_time (clearing_status, transaction_date_time, id);

ALTER TABLE transaction_operation_202604
    ADD COLUMN clearing_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CLEARED'
        COMMENT '动作清分查询投影：NOT_CLEARED、PENDING、CLEARED、FAILED、NOT_REQUIRED' AFTER accounting_status,
    ADD COLUMN clearing_complete_time DATETIME(3) NULL
        COMMENT '最近一次有效清分完成时间' AFTER clearing_status,
    ADD COLUMN clearing_failure_code VARCHAR(64) NULL
        COMMENT '最近一次清分失败码；成功或无需清分时为空' AFTER clearing_complete_time,
    ADD KEY idx_clearing_status_time (clearing_status, transaction_date_time, id);

-- ============================================================================
-- 5. 生命周期主单清分聚合投影
-- ============================================================================

ALTER TABLE transaction_order
    ADD COLUMN clearing_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CLEARED'
        COMMENT '生命周期清分聚合投影：NOT_CLEARED、PENDING、PARTIALLY_CLEARED、CLEARED、FAILED、NOT_REQUIRED'
        AFTER accounting_status,
    ADD COLUMN clearing_complete_time DATETIME(3) NULL
        COMMENT '生命周期最近一次清分聚合完成时间' AFTER clearing_status,
    ADD KEY idx_clearing_time (clearing_status, transaction_date_time, id);

ALTER TABLE transaction_order_202603
    ADD COLUMN clearing_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CLEARED'
        COMMENT '生命周期清分聚合投影：NOT_CLEARED、PENDING、PARTIALLY_CLEARED、CLEARED、FAILED、NOT_REQUIRED'
        AFTER accounting_status,
    ADD COLUMN clearing_complete_time DATETIME(3) NULL
        COMMENT '生命周期最近一次清分聚合完成时间' AFTER clearing_status,
    ADD KEY idx_clearing_time (clearing_status, transaction_date_time, id);

ALTER TABLE transaction_order_202604
    ADD COLUMN clearing_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CLEARED'
        COMMENT '生命周期清分聚合投影：NOT_CLEARED、PENDING、PARTIALLY_CLEARED、CLEARED、FAILED、NOT_REQUIRED'
        AFTER accounting_status,
    ADD COLUMN clearing_complete_time DATETIME(3) NULL
        COMMENT '生命周期最近一次清分聚合完成时间' AFTER clearing_status,
    ADD KEY idx_clearing_time (clearing_status, transaction_date_time, id);

-- ============================================================================
-- 6. 动作费用配置快照结构化索引
-- ============================================================================

ALTER TABLE transaction_merchant_snapshot
    ADD COLUMN fee_plan_id BIGINT NULL COMMENT '动作受理时费用方案ID' AFTER route_config_snapshot_json,
    ADD COLUMN fee_plan_version_id BIGINT NULL COMMENT '动作受理时冻结的费用方案版本ID' AFTER fee_plan_id,
    ADD COLUMN fee_plan_version_no INT NULL COMMENT '动作受理时冻结的费用版本号' AFTER fee_plan_version_id,
    ADD COLUMN fee_snapshot_hash CHAR(64) NULL COMMENT '规范化费用快照SHA-256' AFTER fee_plan_version_no,
    ADD COLUMN fee_snapshot_time DATETIME(3) NULL COMMENT '费用配置冻结时间' AFTER fee_snapshot_hash,
    ADD UNIQUE KEY uk_merchant_snapshot_transaction (transaction_id, transaction_date_time),
    ADD KEY idx_merchant_fee_version_time (merchant_id, fee_plan_version_id, transaction_date_time, id);

ALTER TABLE transaction_merchant_snapshot_202603
    ADD COLUMN fee_plan_id BIGINT NULL COMMENT '动作受理时费用方案ID' AFTER route_config_snapshot_json,
    ADD COLUMN fee_plan_version_id BIGINT NULL COMMENT '动作受理时冻结的费用方案版本ID' AFTER fee_plan_id,
    ADD COLUMN fee_plan_version_no INT NULL COMMENT '动作受理时冻结的费用版本号' AFTER fee_plan_version_id,
    ADD COLUMN fee_snapshot_hash CHAR(64) NULL COMMENT '规范化费用快照SHA-256' AFTER fee_plan_version_no,
    ADD COLUMN fee_snapshot_time DATETIME(3) NULL COMMENT '费用配置冻结时间' AFTER fee_snapshot_hash,
    ADD UNIQUE KEY uk_merchant_snapshot_transaction (transaction_id, transaction_date_time),
    ADD KEY idx_merchant_fee_version_time (merchant_id, fee_plan_version_id, transaction_date_time, id);

ALTER TABLE transaction_merchant_snapshot_202604
    ADD COLUMN fee_plan_id BIGINT NULL COMMENT '动作受理时费用方案ID' AFTER route_config_snapshot_json,
    ADD COLUMN fee_plan_version_id BIGINT NULL COMMENT '动作受理时冻结的费用方案版本ID' AFTER fee_plan_id,
    ADD COLUMN fee_plan_version_no INT NULL COMMENT '动作受理时冻结的费用版本号' AFTER fee_plan_version_id,
    ADD COLUMN fee_snapshot_hash CHAR(64) NULL COMMENT '规范化费用快照SHA-256' AFTER fee_plan_version_no,
    ADD COLUMN fee_snapshot_time DATETIME(3) NULL COMMENT '费用配置冻结时间' AFTER fee_snapshot_hash,
    ADD UNIQUE KEY uk_merchant_snapshot_transaction (transaction_id, transaction_date_time),
    ADD KEY idx_merchant_fee_version_time (merchant_id, fee_plan_version_id, transaction_date_time, id);

-- ============================================================================
-- 7. 清分汇总和财务状态表增强
-- ============================================================================

ALTER TABLE transaction_finance_state
    DROP INDEX uk_operation_finance,
    ADD COLUMN merchant_id VARCHAR(64) NULL COMMENT '平台商户号；存量回填完成后改为NOT NULL' AFTER operation_id,
    ADD COLUMN source_transaction_id VARCHAR(64) NULL COMMENT '退款、冲正、拒付等来源动作交易号' AFTER merchant_id,
    ADD COLUMN label_currency CHAR(3) NULL COMMENT '动作标签币种；生产清分费用和保证金的权威币种' AFTER source_transaction_id,
    ADD COLUMN clearing_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CLEARED'
        COMMENT '清分状态：NOT_CLEARED、PENDING、PROCESSING、WAITING_SOURCE、FAILED、MANUAL_REVIEW、CLEARED、NOT_REQUIRED'
        AFTER transaction_type,
    ADD COLUMN clearing_revision INT NOT NULL DEFAULT 0 COMMENT '当前有效清分修订号，从1递增' AFTER clearing_status,
    ADD COLUMN clearing_trigger_event_no VARCHAR(64) NULL COMMENT '首次触发清分的终态事件号' AFTER clearing_revision,
    ADD COLUMN clearing_request_time DATETIME(3) NULL COMMENT '首次接收清分请求时间' AFTER clearing_trigger_event_no,
    ADD COLUMN clearing_start_time DATETIME(3) NULL COMMENT '最近一次开始计算时间' AFTER clearing_request_time,
    ADD COLUMN clearing_complete_time DATETIME(3) NULL COMMENT '当前修订清分完成时间' AFTER clearing_start_time,
    ADD COLUMN processing_owner VARCHAR(128) NULL COMMENT 'PROCESSING处理实例和线程租约标识' AFTER clearing_complete_time,
    ADD COLUMN processing_deadline DATETIME(3) NULL COMMENT 'PROCESSING租约到期时间' AFTER processing_owner,
    ADD COLUMN clearing_retry_count INT NOT NULL DEFAULT 0 COMMENT '清分业务重试次数' AFTER processing_deadline,
    ADD COLUMN next_retry_time DATETIME(3) NULL COMMENT '下一次允许清分重试时间' AFTER clearing_retry_count,
    ADD COLUMN last_failure_code VARCHAR(64) NULL COMMENT '最近一次清分失败码' AFTER next_retry_time,
    ADD COLUMN last_failure_message VARCHAR(512) NULL COMMENT '最近一次清分失败摘要，不保存请求或配置正文' AFTER last_failure_code,
    ADD COLUMN fee_plan_id BIGINT NULL COMMENT '清分采用的费用方案ID' AFTER last_failure_message,
    ADD COLUMN fee_plan_version_id BIGINT NULL COMMENT '清分采用的不可变费用版本ID' AFTER fee_plan_id,
    ADD COLUMN fee_plan_version_no INT NULL COMMENT '清分采用的费用版本号' AFTER fee_plan_version_id,
    ADD COLUMN fee_snapshot_hash CHAR(64) NULL COMMENT '实际采用的规范化费用快照SHA-256' AFTER fee_plan_version_no,
    ADD COLUMN gross_label_amount DECIMAL(24,8) NULL COMMENT '标签币种下有符号毛本金' AFTER fee_snapshot_hash,
    ADD COLUMN fee_component_currency_count SMALLINT NOT NULL DEFAULT 0 COMMENT '当前修订费用组件币种数' AFTER gross_label_amount,
    ADD COLUMN fee_evaluation_status VARCHAR(32) NULL
        COMMENT 'FINAL_AT_CLEARING或PENDING_SETTLEMENT_RATE' AFTER fee_component_currency_count,
    ADD COLUMN settlement_eligible_date DATE NULL COMMENT '满足结算周期后的最早可结算业务日期' AFTER settlement_cycle,
    ADD COLUMN expected_reserve_release_date DATE NULL COMMENT '预计保证金释放业务日期' AFTER reserve_amount,
    ADD COLUMN settlement_rate DECIMAL(24,12) NULL COMMENT '历史兼容列；清分不得写入' AFTER settlement_currency,
    MODIFY COLUMN settlement_amount DECIMAL(24,8) NULL COMMENT '历史兼容列；清分不得写入',
    ADD COLUMN settlement_fee_amount DECIMAL(24,8) NULL COMMENT '历史兼容列；清分不得写入' AFTER settlement_amount,
    MODIFY COLUMN channel_fee_amount DECIMAL(24,8) NULL COMMENT '已确认渠道成本；清分一期为空',
    MODIFY COLUMN platform_fee_currency CHAR(3) NULL COMMENT '平台费用币种；生产清分必须等于label_currency',
    MODIFY COLUMN platform_fee_amount DECIMAL(24,8) NULL COMMENT '仅标签币种费用组件合计；异币种组件不计入',
    ADD COLUMN fee_reversal_amount DECIMAL(24,8) NULL
        COMMENT '当前清分修订返还或冲回的标签币种平台费用正数合计' AFTER platform_fee_amount,
    MODIFY COLUMN merchant_receivable_currency CHAR(3) NULL COMMENT '商户应收币种；生产清分必须等于label_currency',
    MODIFY COLUMN merchant_receivable_amount DECIMAL(24,8) NULL COMMENT '仅全部费用已在标签币种求值时可用，否则为空',
    MODIFY COLUMN reserve_currency CHAR(3) NULL COMMENT '保证金币种；生产清分必须等于label_currency',
    MODIFY COLUMN reserve_amount DECIMAL(24,8) NULL COMMENT '当前动作标签币种保证金扣留合计',
    ADD COLUMN reserve_reversal_amount DECIMAL(24,8) NULL
        COMMENT '当前动作标签币种保证金返还或释放正数合计' AFTER reserve_amount,
    MODIFY COLUMN net_settlement_currency CHAR(3) NULL COMMENT '动作清分净额币种；生产清分必须等于label_currency',
    MODIFY COLUMN net_settlement_amount DECIMAL(24,8) NULL COMMENT '历史查询投影；存在异币种费用时为空',
    ADD UNIQUE KEY uk_transaction_finance (transaction_id),
    ADD KEY idx_operation_finance_time (operation_id, transaction_date_time, id),
    ADD KEY idx_clearing_retry (clearing_status, next_retry_time, transaction_date_time, id),
    ADD KEY idx_clearing_lease (clearing_status, processing_deadline, transaction_date_time, id),
    ADD KEY idx_merchant_settlement_eligible
        (merchant_id, settlement_currency, clearing_status, settlement_status, settlement_eligible_date, id),
    ADD CONSTRAINT chk_finance_clearing_revision_tpl CHECK (clearing_revision >= 0),
    ADD CONSTRAINT chk_finance_clearing_status_tpl CHECK (
        clearing_status IN (
            'NOT_CLEARED', 'PENDING', 'PROCESSING', 'WAITING_SOURCE',
            'FAILED', 'MANUAL_REVIEW', 'CLEARED', 'NOT_REQUIRED'
        )
    ),
    ADD CONSTRAINT chk_finance_fee_evaluation_tpl CHECK (
        fee_component_currency_count >= 0
        AND (fee_evaluation_status IS NULL OR fee_evaluation_status IN (
            'FINAL_AT_CLEARING', 'PENDING_SETTLEMENT_RATE'
        ))
    ),
    ADD CONSTRAINT chk_finance_nonnegative_summary_tpl CHECK (
        (platform_fee_amount IS NULL OR platform_fee_amount >= 0)
        AND (fee_reversal_amount IS NULL OR fee_reversal_amount >= 0)
        AND (reserve_amount IS NULL OR reserve_amount >= 0)
        AND (reserve_reversal_amount IS NULL OR reserve_reversal_amount >= 0)
    ),
    ADD CONSTRAINT chk_finance_label_currency_tpl CHECK (
        label_currency IS NULL
        OR ((platform_fee_currency IS NULL OR platform_fee_currency = label_currency)
            AND (merchant_receivable_currency IS NULL OR merchant_receivable_currency = label_currency)
            AND (reserve_currency IS NULL OR reserve_currency = label_currency)
            AND (net_settlement_currency IS NULL OR net_settlement_currency = label_currency))
    ),
    COMMENT = '动作级清分汇总及结算、对账、账务当前状态；transaction_id唯一，operation_id用于生命周期聚合';

ALTER TABLE transaction_finance_state_202603
    DROP INDEX uk_operation_finance,
    ADD COLUMN merchant_id VARCHAR(64) NULL COMMENT '平台商户号；存量回填完成后改为NOT NULL' AFTER operation_id,
    ADD COLUMN source_transaction_id VARCHAR(64) NULL COMMENT '退款、冲正、拒付等来源动作交易号' AFTER merchant_id,
    ADD COLUMN label_currency CHAR(3) NULL COMMENT '动作标签币种；生产清分费用和保证金的权威币种' AFTER source_transaction_id,
    ADD COLUMN clearing_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CLEARED'
        COMMENT '清分状态：NOT_CLEARED、PENDING、PROCESSING、WAITING_SOURCE、FAILED、MANUAL_REVIEW、CLEARED、NOT_REQUIRED'
        AFTER transaction_type,
    ADD COLUMN clearing_revision INT NOT NULL DEFAULT 0 COMMENT '当前有效清分修订号，从1递增' AFTER clearing_status,
    ADD COLUMN clearing_trigger_event_no VARCHAR(64) NULL COMMENT '首次触发清分的终态事件号' AFTER clearing_revision,
    ADD COLUMN clearing_request_time DATETIME(3) NULL COMMENT '首次接收清分请求时间' AFTER clearing_trigger_event_no,
    ADD COLUMN clearing_start_time DATETIME(3) NULL COMMENT '最近一次开始计算时间' AFTER clearing_request_time,
    ADD COLUMN clearing_complete_time DATETIME(3) NULL COMMENT '当前修订清分完成时间' AFTER clearing_start_time,
    ADD COLUMN processing_owner VARCHAR(128) NULL COMMENT 'PROCESSING处理实例和线程租约标识' AFTER clearing_complete_time,
    ADD COLUMN processing_deadline DATETIME(3) NULL COMMENT 'PROCESSING租约到期时间' AFTER processing_owner,
    ADD COLUMN clearing_retry_count INT NOT NULL DEFAULT 0 COMMENT '清分业务重试次数' AFTER processing_deadline,
    ADD COLUMN next_retry_time DATETIME(3) NULL COMMENT '下一次允许清分重试时间' AFTER clearing_retry_count,
    ADD COLUMN last_failure_code VARCHAR(64) NULL COMMENT '最近一次清分失败码' AFTER next_retry_time,
    ADD COLUMN last_failure_message VARCHAR(512) NULL COMMENT '最近一次清分失败摘要，不保存请求或配置正文' AFTER last_failure_code,
    ADD COLUMN fee_plan_id BIGINT NULL COMMENT '清分采用的费用方案ID' AFTER last_failure_message,
    ADD COLUMN fee_plan_version_id BIGINT NULL COMMENT '清分采用的不可变费用版本ID' AFTER fee_plan_id,
    ADD COLUMN fee_plan_version_no INT NULL COMMENT '清分采用的费用版本号' AFTER fee_plan_version_id,
    ADD COLUMN fee_snapshot_hash CHAR(64) NULL COMMENT '实际采用的规范化费用快照SHA-256' AFTER fee_plan_version_no,
    ADD COLUMN gross_label_amount DECIMAL(24,8) NULL COMMENT '标签币种下有符号毛本金' AFTER fee_snapshot_hash,
    ADD COLUMN fee_component_currency_count SMALLINT NOT NULL DEFAULT 0 COMMENT '当前修订费用组件币种数' AFTER gross_label_amount,
    ADD COLUMN fee_evaluation_status VARCHAR(32) NULL
        COMMENT 'FINAL_AT_CLEARING或PENDING_SETTLEMENT_RATE' AFTER fee_component_currency_count,
    ADD COLUMN settlement_eligible_date DATE NULL COMMENT '满足结算周期后的最早可结算业务日期' AFTER settlement_cycle,
    ADD COLUMN expected_reserve_release_date DATE NULL COMMENT '预计保证金释放业务日期' AFTER reserve_amount,
    ADD COLUMN settlement_rate DECIMAL(24,12) NULL COMMENT '历史兼容列；清分不得写入' AFTER settlement_currency,
    MODIFY COLUMN settlement_amount DECIMAL(24,8) NULL COMMENT '历史兼容列；清分不得写入',
    ADD COLUMN settlement_fee_amount DECIMAL(24,8) NULL COMMENT '历史兼容列；清分不得写入' AFTER settlement_amount,
    MODIFY COLUMN channel_fee_amount DECIMAL(24,8) NULL COMMENT '已确认渠道成本；清分一期为空',
    MODIFY COLUMN platform_fee_currency CHAR(3) NULL COMMENT '平台费用币种；生产清分必须等于label_currency',
    MODIFY COLUMN platform_fee_amount DECIMAL(24,8) NULL COMMENT '仅标签币种费用组件合计；异币种组件不计入',
    ADD COLUMN fee_reversal_amount DECIMAL(24,8) NULL
        COMMENT '当前清分修订返还或冲回的标签币种平台费用正数合计' AFTER platform_fee_amount,
    MODIFY COLUMN merchant_receivable_currency CHAR(3) NULL COMMENT '商户应收币种；生产清分必须等于label_currency',
    MODIFY COLUMN merchant_receivable_amount DECIMAL(24,8) NULL COMMENT '仅全部费用已在标签币种求值时可用，否则为空',
    MODIFY COLUMN reserve_currency CHAR(3) NULL COMMENT '保证金币种；生产清分必须等于label_currency',
    MODIFY COLUMN reserve_amount DECIMAL(24,8) NULL COMMENT '当前动作标签币种保证金扣留合计',
    ADD COLUMN reserve_reversal_amount DECIMAL(24,8) NULL
        COMMENT '当前动作标签币种保证金返还或释放正数合计' AFTER reserve_amount,
    MODIFY COLUMN net_settlement_currency CHAR(3) NULL COMMENT '动作清分净额币种；生产清分必须等于label_currency',
    MODIFY COLUMN net_settlement_amount DECIMAL(24,8) NULL COMMENT '历史查询投影；存在异币种费用时为空',
    ADD UNIQUE KEY uk_transaction_finance (transaction_id),
    ADD KEY idx_operation_finance_time (operation_id, transaction_date_time, id),
    ADD KEY idx_clearing_retry (clearing_status, next_retry_time, transaction_date_time, id),
    ADD KEY idx_clearing_lease (clearing_status, processing_deadline, transaction_date_time, id),
    ADD KEY idx_merchant_settlement_eligible
        (merchant_id, settlement_currency, clearing_status, settlement_status, settlement_eligible_date, id),
    ADD CONSTRAINT chk_finance_clearing_revision_202603 CHECK (clearing_revision >= 0),
    ADD CONSTRAINT chk_finance_clearing_status_202603 CHECK (
        clearing_status IN (
            'NOT_CLEARED', 'PENDING', 'PROCESSING', 'WAITING_SOURCE',
            'FAILED', 'MANUAL_REVIEW', 'CLEARED', 'NOT_REQUIRED'
        )
    ),
    ADD CONSTRAINT chk_finance_fee_evaluation_202603 CHECK (
        fee_component_currency_count >= 0
        AND (fee_evaluation_status IS NULL OR fee_evaluation_status IN (
            'FINAL_AT_CLEARING', 'PENDING_SETTLEMENT_RATE'
        ))
    ),
    ADD CONSTRAINT chk_finance_nonnegative_summary_202603 CHECK (
        (platform_fee_amount IS NULL OR platform_fee_amount >= 0)
        AND (fee_reversal_amount IS NULL OR fee_reversal_amount >= 0)
        AND (reserve_amount IS NULL OR reserve_amount >= 0)
        AND (reserve_reversal_amount IS NULL OR reserve_reversal_amount >= 0)
    ),
    ADD CONSTRAINT chk_finance_label_currency_202603 CHECK (
        label_currency IS NULL
        OR ((platform_fee_currency IS NULL OR platform_fee_currency = label_currency)
            AND (merchant_receivable_currency IS NULL OR merchant_receivable_currency = label_currency)
            AND (reserve_currency IS NULL OR reserve_currency = label_currency)
            AND (net_settlement_currency IS NULL OR net_settlement_currency = label_currency))
    ),
    COMMENT = '动作级清分汇总及结算、对账、账务当前状态；transaction_id唯一，operation_id用于生命周期聚合';

ALTER TABLE transaction_finance_state_202604
    DROP INDEX uk_operation_finance,
    ADD COLUMN merchant_id VARCHAR(64) NULL COMMENT '平台商户号；存量回填完成后改为NOT NULL' AFTER operation_id,
    ADD COLUMN source_transaction_id VARCHAR(64) NULL COMMENT '退款、冲正、拒付等来源动作交易号' AFTER merchant_id,
    ADD COLUMN label_currency CHAR(3) NULL COMMENT '动作标签币种；生产清分费用和保证金的权威币种' AFTER source_transaction_id,
    ADD COLUMN clearing_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CLEARED'
        COMMENT '清分状态：NOT_CLEARED、PENDING、PROCESSING、WAITING_SOURCE、FAILED、MANUAL_REVIEW、CLEARED、NOT_REQUIRED'
        AFTER transaction_type,
    ADD COLUMN clearing_revision INT NOT NULL DEFAULT 0 COMMENT '当前有效清分修订号，从1递增' AFTER clearing_status,
    ADD COLUMN clearing_trigger_event_no VARCHAR(64) NULL COMMENT '首次触发清分的终态事件号' AFTER clearing_revision,
    ADD COLUMN clearing_request_time DATETIME(3) NULL COMMENT '首次接收清分请求时间' AFTER clearing_trigger_event_no,
    ADD COLUMN clearing_start_time DATETIME(3) NULL COMMENT '最近一次开始计算时间' AFTER clearing_request_time,
    ADD COLUMN clearing_complete_time DATETIME(3) NULL COMMENT '当前修订清分完成时间' AFTER clearing_start_time,
    ADD COLUMN processing_owner VARCHAR(128) NULL COMMENT 'PROCESSING处理实例和线程租约标识' AFTER clearing_complete_time,
    ADD COLUMN processing_deadline DATETIME(3) NULL COMMENT 'PROCESSING租约到期时间' AFTER processing_owner,
    ADD COLUMN clearing_retry_count INT NOT NULL DEFAULT 0 COMMENT '清分业务重试次数' AFTER processing_deadline,
    ADD COLUMN next_retry_time DATETIME(3) NULL COMMENT '下一次允许清分重试时间' AFTER clearing_retry_count,
    ADD COLUMN last_failure_code VARCHAR(64) NULL COMMENT '最近一次清分失败码' AFTER next_retry_time,
    ADD COLUMN last_failure_message VARCHAR(512) NULL COMMENT '最近一次清分失败摘要，不保存请求或配置正文' AFTER last_failure_code,
    ADD COLUMN fee_plan_id BIGINT NULL COMMENT '清分采用的费用方案ID' AFTER last_failure_message,
    ADD COLUMN fee_plan_version_id BIGINT NULL COMMENT '清分采用的不可变费用版本ID' AFTER fee_plan_id,
    ADD COLUMN fee_plan_version_no INT NULL COMMENT '清分采用的费用版本号' AFTER fee_plan_version_id,
    ADD COLUMN fee_snapshot_hash CHAR(64) NULL COMMENT '实际采用的规范化费用快照SHA-256' AFTER fee_plan_version_no,
    ADD COLUMN gross_label_amount DECIMAL(24,8) NULL COMMENT '标签币种下有符号毛本金' AFTER fee_snapshot_hash,
    ADD COLUMN fee_component_currency_count SMALLINT NOT NULL DEFAULT 0 COMMENT '当前修订费用组件币种数' AFTER gross_label_amount,
    ADD COLUMN fee_evaluation_status VARCHAR(32) NULL
        COMMENT 'FINAL_AT_CLEARING或PENDING_SETTLEMENT_RATE' AFTER fee_component_currency_count,
    ADD COLUMN settlement_eligible_date DATE NULL COMMENT '满足结算周期后的最早可结算业务日期' AFTER settlement_cycle,
    ADD COLUMN expected_reserve_release_date DATE NULL COMMENT '预计保证金释放业务日期' AFTER reserve_amount,
    ADD COLUMN settlement_rate DECIMAL(24,12) NULL COMMENT '历史兼容列；清分不得写入' AFTER settlement_currency,
    MODIFY COLUMN settlement_amount DECIMAL(24,8) NULL COMMENT '历史兼容列；清分不得写入',
    ADD COLUMN settlement_fee_amount DECIMAL(24,8) NULL COMMENT '历史兼容列；清分不得写入' AFTER settlement_amount,
    MODIFY COLUMN channel_fee_amount DECIMAL(24,8) NULL COMMENT '已确认渠道成本；清分一期为空',
    MODIFY COLUMN platform_fee_currency CHAR(3) NULL COMMENT '平台费用币种；生产清分必须等于label_currency',
    MODIFY COLUMN platform_fee_amount DECIMAL(24,8) NULL COMMENT '仅标签币种费用组件合计；异币种组件不计入',
    ADD COLUMN fee_reversal_amount DECIMAL(24,8) NULL
        COMMENT '当前清分修订返还或冲回的标签币种平台费用正数合计' AFTER platform_fee_amount,
    MODIFY COLUMN merchant_receivable_currency CHAR(3) NULL COMMENT '商户应收币种；生产清分必须等于label_currency',
    MODIFY COLUMN merchant_receivable_amount DECIMAL(24,8) NULL COMMENT '仅全部费用已在标签币种求值时可用，否则为空',
    MODIFY COLUMN reserve_currency CHAR(3) NULL COMMENT '保证金币种；生产清分必须等于label_currency',
    MODIFY COLUMN reserve_amount DECIMAL(24,8) NULL COMMENT '当前动作标签币种保证金扣留合计',
    ADD COLUMN reserve_reversal_amount DECIMAL(24,8) NULL
        COMMENT '当前动作标签币种保证金返还或释放正数合计' AFTER reserve_amount,
    MODIFY COLUMN net_settlement_currency CHAR(3) NULL COMMENT '动作清分净额币种；生产清分必须等于label_currency',
    MODIFY COLUMN net_settlement_amount DECIMAL(24,8) NULL COMMENT '历史查询投影；存在异币种费用时为空',
    ADD UNIQUE KEY uk_transaction_finance (transaction_id),
    ADD KEY idx_operation_finance_time (operation_id, transaction_date_time, id),
    ADD KEY idx_clearing_retry (clearing_status, next_retry_time, transaction_date_time, id),
    ADD KEY idx_clearing_lease (clearing_status, processing_deadline, transaction_date_time, id),
    ADD KEY idx_merchant_settlement_eligible
        (merchant_id, settlement_currency, clearing_status, settlement_status, settlement_eligible_date, id),
    ADD CONSTRAINT chk_finance_clearing_revision_202604 CHECK (clearing_revision >= 0),
    ADD CONSTRAINT chk_finance_clearing_status_202604 CHECK (
        clearing_status IN (
            'NOT_CLEARED', 'PENDING', 'PROCESSING', 'WAITING_SOURCE',
            'FAILED', 'MANUAL_REVIEW', 'CLEARED', 'NOT_REQUIRED'
        )
    ),
    ADD CONSTRAINT chk_finance_fee_evaluation_202604 CHECK (
        fee_component_currency_count >= 0
        AND (fee_evaluation_status IS NULL OR fee_evaluation_status IN (
            'FINAL_AT_CLEARING', 'PENDING_SETTLEMENT_RATE'
        ))
    ),
    ADD CONSTRAINT chk_finance_nonnegative_summary_202604 CHECK (
        (platform_fee_amount IS NULL OR platform_fee_amount >= 0)
        AND (fee_reversal_amount IS NULL OR fee_reversal_amount >= 0)
        AND (reserve_amount IS NULL OR reserve_amount >= 0)
        AND (reserve_reversal_amount IS NULL OR reserve_reversal_amount >= 0)
    ),
    ADD CONSTRAINT chk_finance_label_currency_202604 CHECK (
        label_currency IS NULL
        OR ((platform_fee_currency IS NULL OR platform_fee_currency = label_currency)
            AND (merchant_receivable_currency IS NULL OR merchant_receivable_currency = label_currency)
            AND (reserve_currency IS NULL OR reserve_currency = label_currency)
            AND (net_settlement_currency IS NULL OR net_settlement_currency = label_currency))
    ),
    COMMENT = '动作级清分汇总及结算、对账、账务当前状态；transaction_id唯一，operation_id用于生命周期聚合';
