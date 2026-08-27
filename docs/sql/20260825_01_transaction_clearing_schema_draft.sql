-- 收单交易清分一期数据库迁移草案。
-- 状态：仅供评审，禁止直接在 UAT/生产执行。
-- 前置：必须先备份、核对活动季度、评估 ALTER 锁表时间，并完成 28 表 ShardingSphere 规则和 checksum 演练。
-- 边界：本草案包含结算目标表结构供评审，但不得据此执行结算、修改商户余额或写资金流水。
-- 费用口径：按商户生效配置计算；百分比使用标签金额和标签币种，固定费和上下限固定为 USD，清分不读取结算汇率。
-- 执行：每个 ALTER 必须先在同结构同数据量影子表验证执行算法、峰值临时空间、主从延迟和 MDL 等待。
--       不在本草案强写 ALGORITHM/LOCK；若目标 MySQL 对某一步不支持 LOCK=NONE，应使用在线变更工具或维护窗口，
--       禁止在交易高峰直接接受 COPY/长时间阻塞。模板、202603、202604 必须逐表执行并逐表验收。
-- 拆分：第三阶段实际评审按 20260825_02 至 20260825_05 四个独立草案执行；本文件保留完整设计和后续结算结构，不作为一键脚本。

SET NAMES utf8mb4;

-- ============================================================================
-- 1. 执行前只读核对
-- ============================================================================

-- finance state 应按动作级 transaction_id 唯一；唯一索引覆盖软删除行，以下查询不能过滤 deleted。
-- 三条查询必须均返回 0 行。
SELECT transaction_id, COUNT(*) AS duplicate_count
FROM transaction_finance_state
GROUP BY transaction_id
HAVING COUNT(*) > 1;

SELECT transaction_id, COUNT(*) AS duplicate_count
FROM transaction_finance_state_202603
GROUP BY transaction_id
HAVING COUNT(*) > 1;

SELECT transaction_id, COUNT(*) AS duplicate_count
FROM transaction_finance_state_202604
GROUP BY transaction_id
HAVING COUNT(*) > 1;

-- 动作费用快照按 transaction_id + transaction_date_time 唯一；以下三条查询必须均返回 0 行，
-- 否则不能创建动作快照幂等唯一索引，也不能部署自动清分服务。
SELECT transaction_id, transaction_date_time, COUNT(*) AS duplicate_count
FROM transaction_merchant_snapshot
GROUP BY transaction_id, transaction_date_time
HAVING COUNT(*) > 1;

SELECT transaction_id, transaction_date_time, COUNT(*) AS duplicate_count
FROM transaction_merchant_snapshot_202603
GROUP BY transaction_id, transaction_date_time
HAVING COUNT(*) > 1;

SELECT transaction_id, transaction_date_time, COUNT(*) AS duplicate_count
FROM transaction_merchant_snapshot_202604
GROUP BY transaction_id, transaction_date_time
HAVING COUNT(*) > 1;

-- 当前 operation_id 是生命周期关联号，允许同一生命周期包含多个动作。
-- 若 finance state 已有数据，先核对同一 operation_id 的记录是否属于不同 transaction_id。
SELECT operation_id, COUNT(DISTINCT transaction_id) AS action_count
FROM transaction_finance_state_202603
WHERE deleted = 0
GROUP BY operation_id
HAVING COUNT(DISTINCT transaction_id) > 1;

-- 新表名必须均不存在；若返回记录，停止迁移并先确认是否为失败残留或其它版本。
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'fee_tier_accumulator',
      'transaction_clearing_detail',
      'transaction_clearing_detail_202603',
      'transaction_clearing_detail_202604',
      'transaction_reserve_clearing_detail',
      'transaction_reserve_clearing_detail_202603',
      'transaction_reserve_clearing_detail_202604',
      'transaction_reserve_clearing_state',
      'transaction_reserve_clearing_state_202603',
      'transaction_reserve_clearing_state_202604',
      'clearing_reserve_adjustment',
      'clearing_tier_period_replay',
      'clearing_tier_period_replay_item',
      'settlement_batch_daily_sequence',
      'settlement_candidate',
      'settlement_candidate_dependency',
      'settlement_batch_candidate',
      'settlement_batch',
      'settlement_batch_rate',
      'settlement_result_item',
      'settlement_result_summary'
  );

SELECT operation_id, COUNT(DISTINCT transaction_id) AS action_count
FROM transaction_finance_state_202604
WHERE deleted = 0
GROUP BY operation_id
HAVING COUNT(DISTINCT transaction_id) > 1;

-- 现有平台费和保证金汇总按绝对值保存；以下计数必须均为0。
SELECT 'template' AS shard_name, COUNT(*) AS negative_summary_count
FROM transaction_finance_state
WHERE platform_fee_amount < 0 OR reserve_amount < 0
UNION ALL
SELECT '202603', COUNT(*)
FROM transaction_finance_state_202603
WHERE platform_fee_amount < 0 OR reserve_amount < 0
UNION ALL
SELECT '202604', COUNT(*)
FROM transaction_finance_state_202604
WHERE platform_fee_amount < 0 OR reserve_amount < 0;

-- ============================================================================
-- 2. 商户生效费用配置只读核对；禁止改变现有模板和商户配置口径
-- ============================================================================

-- 清分只允许使用商户方案当前已生效且不可变的版本。以下查询命中时必须停止发布并修复配置，
-- 不能回退平台默认费率，也不能临时读取模板当前版本替代商户已冻结版本。
SELECT fp.id AS fee_plan_id,
       fp.merchant_id,
       fp.current_version_id,
       fp.current_version_no,
       fpv.version_status,
       fpv.effective_time,
       fpv.settlement_currency
FROM fee_plan fp
LEFT JOIN fee_plan_version fpv
       ON fpv.id = fp.current_version_id
      AND fpv.plan_id = fp.id
      AND fpv.deleted = 0
WHERE fp.plan_type = 'MERCHANT'
  AND fp.deleted = 0
  AND (
      fp.merchant_id IS NULL
      OR fp.merchant_id = ''
      OR fp.status <> 'ENABLED'
      OR fp.current_version_id IS NULL
      OR fp.current_version_no IS NULL
      OR fpv.id IS NULL
      OR fpv.version_status <> 'ACTIVE'
      OR fpv.version_no <> fp.current_version_no
      OR fpv.effective_time IS NULL
      OR fpv.settlement_currency IS NULL
  );

-- 现有费用配置的币种合同保持不变：百分比按动作标签币种计算，固定费、最低费和最高费为 USD。
-- 本草案不向 fee_plan_version、fee_rule、fee_rule_tier 或 Admin 试算表增加任何币种配置字段。
SELECT 'fee_rule' AS source_table,
       fr.id AS source_id,
       fr.plan_version_id,
       fr.fixed_amount_usd,
       fr.minimum_amount_usd,
       fr.maximum_amount_usd
FROM fee_rule fr
WHERE fr.deleted = 0
  AND (
      fr.fixed_amount_usd IS NULL
      OR fr.fixed_amount_usd < 0
      OR (fr.minimum_amount_usd IS NOT NULL AND fr.minimum_amount_usd < 0)
      OR (fr.maximum_amount_usd IS NOT NULL AND fr.maximum_amount_usd < 0)
      OR (fr.minimum_amount_usd IS NOT NULL
          AND fr.maximum_amount_usd IS NOT NULL
          AND fr.minimum_amount_usd > fr.maximum_amount_usd)
  )
UNION ALL
SELECT 'fee_rule_tier',
       frt.id,
       fr.plan_version_id,
       frt.fixed_amount_usd,
       frt.minimum_amount_usd,
       frt.maximum_amount_usd
FROM fee_rule_tier frt
JOIN fee_rule fr
  ON fr.id = frt.fee_rule_id
 AND fr.deleted = 0
WHERE frt.deleted = 0
  AND (
      frt.fixed_amount_usd IS NULL
      OR frt.fixed_amount_usd < 0
      OR (frt.minimum_amount_usd IS NOT NULL AND frt.minimum_amount_usd < 0)
      OR (frt.maximum_amount_usd IS NOT NULL AND frt.maximum_amount_usd < 0)
      OR (frt.minimum_amount_usd IS NOT NULL
          AND frt.maximum_amount_usd IS NOT NULL
          AND frt.minimum_amount_usd > frt.maximum_amount_usd)
  );

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

-- ============================================================================
-- 8. 月累计阶梯费率事实表；现有 transaction_rw.* Single Rule 自动发现
-- ============================================================================

CREATE TABLE fee_tier_accumulator (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号',
    fee_plan_version_id BIGINT NOT NULL COMMENT '不可变费用版本ID',
    fee_rule_id BIGINT NOT NULL COMMENT '阶梯费用规则ID',
    period_key CHAR(6) NOT NULL COMMENT '累计月份，格式yyyyMM，按平台业务时区确定',
    accumulated_count BIGINT NOT NULL DEFAULT 0 COMMENT '已成功清分动作累计笔数',
    accumulated_amount_usd DECIMAL(24,8) NOT NULL DEFAULT 0
        COMMENT '按现有费用配置口径归一到USD的已成功清分计费基数累计金额',
    last_transaction_id VARCHAR(64) NULL COMMENT '最近计入累计的动作级交易号',
    last_clearing_revision INT NULL COMMENT '最近计入累计的动作清分修订号',
    last_transaction_date_time DATETIME(3) NULL COMMENT '最近计入累计的动作业务时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '并发CAS版本号',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fee_tier_accumulator
        (merchant_id, fee_plan_version_id, fee_rule_id, period_key),
    KEY idx_fee_tier_period (period_key, merchant_id, id),
    CONSTRAINT chk_fee_tier_accumulator_value CHECK (
        accumulated_count >= 0
        AND accumulated_amount_usd >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='按商户生效费用版本维护COUNT或USD归一AMOUNT的月累计阶梯事实；Redis仅保存镜像';

-- ============================================================================
-- 9. 第26张交易逻辑表：交易清分明细模板和活动季度物理表
-- ============================================================================

CREATE TABLE transaction_clearing_detail (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID',
    clearing_detail_no VARCHAR(64) NOT NULL COMMENT '清分明细号',
    finance_state_id VARCHAR(64) NOT NULL COMMENT '所属清分汇总ID',
    transaction_id VARCHAR(64) NOT NULL COMMENT '动作级平台交易号',
    operation_id VARCHAR(64) NOT NULL COMMENT '交易生命周期关联号',
    source_transaction_id VARCHAR(64) NULL COMMENT '退款、冲正、拒付等来源动作交易号',
    source_clearing_detail_no VARCHAR(64) NULL COMMENT '冲正或返还所引用的原清分明细号',
    source_settlement_result_item_no VARCHAR(64) NULL
        COMMENT '跨币种原费用已结算后返费所引用的实际收费结果明细号',
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号',
    payment_type VARCHAR(32) NOT NULL COMMENT '清分时冻结的支付类型，用于结算分组',
    payment_method VARCHAR(32) NOT NULL COMMENT '清分时冻结的支付方式，用于结算分组',
    transaction_type VARCHAR(32) NOT NULL COMMENT '交易动作类型',
    clearing_revision INT NOT NULL COMMENT '所属清分修订号，从1递增',
    line_no INT NOT NULL COMMENT '同一修订内稳定明细序号，从1递增',
    item_type VARCHAR(32) NOT NULL
        COMMENT 'PRINCIPAL、PLATFORM_FEE、FEE_REVERSAL、ADJUSTMENT；禁止保存保证金',
    fee_category VARCHAR(32) NULL COMMENT '交易费、内外风控费、3DS费、退款费、换汇费、拒付费等；本金可为空',
    risk_service_type VARCHAR(16) NOT NULL DEFAULT 'NONE'
        COMMENT '风控费用细分：INTERNAL、EXTERNAL、THREE_DS；非风控费用为NONE',
    item_code VARCHAR(64) NOT NULL COMMENT '修订内稳定业务项编码，例如PRINCIPAL或FEE:TRANSACTION_FEE:ruleId',
    item_name VARCHAR(128) NOT NULL COMMENT '费用或本金项目名称快照',
    direction VARCHAR(8) NOT NULL COMMENT '商户视角方向：CREDIT增加应结、DEBIT减少应结',
    label_currency CHAR(3) NOT NULL COMMENT '当前动作标签币种；百分比基数币种',
    label_amount DECIMAL(24,8) NOT NULL COMMENT '当前动作标签金额，始终非负',
    label_currency_exponent TINYINT NOT NULL COMMENT '标签币种ISO小数位',
    fee_group_no VARCHAR(64) NULL COMMENT '同一规则一次收费的稳定组号；本金为空',
    component_no SMALLINT NOT NULL COMMENT '费用组内稳定组件序号；本金固定1',
    component_type VARCHAR(24) NOT NULL
        COMMENT 'PRINCIPAL、PERCENTAGE、FIXED、LIMIT_ADJUSTMENT、REVERSAL、ADJUSTMENT',
    basis_currency CHAR(3) NOT NULL COMMENT '百分比基数或本金币种；固定费固定为USD',
    basis_amount DECIMAL(24,8) NOT NULL COMMENT '百分比基数、本金或固定费配置金额，始终非负',
    basis_currency_exponent TINYINT NOT NULL COMMENT '基础币种ISO小数位',
    amount DECIMAL(24,8) NOT NULL COMMENT '当前原子组件按自身币种舍入后的权威金额，始终非负',
    currency CHAR(3) NOT NULL COMMENT '当前原子组件原币种；清分阶段不转换',
    currency_exponent TINYINT NOT NULL COMMENT '组件币种ISO小数位',
    fee_plan_id BIGINT NULL COMMENT '费用方案ID；本金可为空',
    fee_plan_version_id BIGINT NULL COMMENT '不可变费用版本ID；本金可为空',
    fee_plan_version_no INT NULL COMMENT '费用版本号快照',
    fee_rule_id BIGINT NULL COMMENT '命中的费用规则ID',
    fee_rule_tier_id BIGINT NULL COMMENT '命中的阶梯ID',
    charge_trigger VARCHAR(32) NULL COMMENT '收费触发条件快照',
    fee_mode VARCHAR(16) NULL COMMENT 'STANDARD或TIER',
    tier_period_key CHAR(6) NULL COMMENT '阶梯累计月份yyyyMM；非阶梯费用为空',
    tier_metric VARCHAR(16) NULL COMMENT 'COUNT或AMOUNT；非阶梯费用为空',
    tier_count_before BIGINT NULL COMMENT '应用本动作前累计笔数',
    tier_count_delta BIGINT NULL COMMENT '本动作对该规则贡献笔数，当前为1',
    tier_count_after BIGINT NULL COMMENT '应用本动作后累计笔数',
    tier_amount_usd_before DECIMAL(24,8) NULL COMMENT '应用本动作前按现有配置口径归一的USD累计基数',
    tier_amount_usd_delta DECIMAL(24,8) NULL COMMENT '本动作按相同口径归一的USD计费基数',
    tier_amount_usd_after DECIMAL(24,8) NULL COMMENT '应用本动作后的USD累计基数',
    percentage_rate DECIMAL(12,8) NULL COMMENT '百分比费率快照，例如2.3表示2.3%',
    fixed_amount_usd DECIMAL(24,8) NULL COMMENT '商户规则固定费USD快照',
    minimum_amount_usd DECIMAL(24,8) NULL COMMENT '商户规则最低费USD快照',
    maximum_amount_usd DECIMAL(24,8) NULL COMMENT '商户规则最高费USD快照',
    limit_evaluation_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED'
        COMMENT 'NOT_REQUIRED、FINAL_AT_CLEARING、PENDING_SETTLEMENT_RATE',
    applied_limit VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE、MINIMUM、MAXIMUM',
    rounding_mode VARCHAR(16) NOT NULL COMMENT '最终明细舍入模式',
    formula_snapshot VARCHAR(1000) NOT NULL COMMENT '可审计计算公式快照',
    rule_snapshot_json JSON NULL COMMENT '命中规则的规范化非敏感快照',
    fee_snapshot_hash CHAR(64) NULL COMMENT '交易动作费用快照SHA-256',
    settlement_eligible_date DATE NOT NULL COMMENT '最早可进入结算批次的业务日期',
    record_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、SUPERSEDED；冲正不修改原明细状态',
    transaction_date_time DATETIME(3) NOT NULL COMMENT '动作交易业务时间，季度分片键',
    transaction_utc_time DATETIME(3) NOT NULL COMMENT '动作交易业务时间对应UTC时间',
    transaction_time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '动作业务时区',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_clearing_detail_no (clearing_detail_no),
    UNIQUE KEY uk_clearing_revision_line (finance_state_id, clearing_revision, line_no),
    UNIQUE KEY uk_clearing_transaction_item (transaction_id, clearing_revision, item_code),
    KEY idx_clearing_transaction_time (transaction_id, transaction_date_time, clearing_revision, id),
    KEY idx_clearing_operation_time (operation_id, transaction_date_time, id),
    KEY idx_clearing_source (source_transaction_id, source_clearing_detail_no, transaction_date_time),
    KEY idx_clearing_source_settlement (source_settlement_result_item_no, transaction_date_time, id),
    KEY idx_clearing_rule (fee_plan_version_id, fee_rule_id, fee_rule_tier_id),
    KEY idx_clearing_settlement_eligible
        (merchant_id, record_status, settlement_eligible_date, currency, id),
    KEY idx_clearing_fee_group (fee_group_no, clearing_revision, component_no, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='动作级不可变交易清分明细；只保存本金和费用，跟随当前动作季度分片';

CREATE TABLE transaction_clearing_detail_202603 LIKE transaction_clearing_detail;
ALTER TABLE transaction_clearing_detail_202603
    AUTO_INCREMENT = 202603000000000001,
    COMMENT = '2026年第3季度动作级交易清分明细';

CREATE TABLE transaction_clearing_detail_202604 LIKE transaction_clearing_detail;
ALTER TABLE transaction_clearing_detail_202604
    AUTO_INCREMENT = 202604000000000001,
    COMMENT = '2026年第4季度动作级交易清分明细';

-- CREATE TABLE LIKE 在不同 MySQL 小版本对命名 CHECK 的复制行为存在差异，故先克隆无 CHECK 结构，
-- 再给模板和每张物理表增加 schema 内唯一名称的相同约束。
ALTER TABLE transaction_clearing_detail
    ADD CONSTRAINT chk_clearing_revision_line_tpl CHECK (clearing_revision >= 1 AND line_no >= 1),
    ADD CONSTRAINT chk_clearing_direction_tpl CHECK (direction IN ('CREDIT', 'DEBIT')),
    ADD CONSTRAINT chk_clearing_amount_tpl CHECK (
        label_amount >= 0 AND basis_amount >= 0 AND amount >= 0 AND component_no >= 1
        AND (percentage_rate IS NULL OR percentage_rate >= 0)
        AND (fixed_amount_usd IS NULL OR fixed_amount_usd >= 0)
        AND (minimum_amount_usd IS NULL OR minimum_amount_usd >= 0)
        AND (maximum_amount_usd IS NULL OR maximum_amount_usd >= 0)
        AND (minimum_amount_usd IS NULL OR maximum_amount_usd IS NULL
             OR minimum_amount_usd <= maximum_amount_usd)
    ),
    ADD CONSTRAINT chk_clearing_exponent_tpl CHECK (
        label_currency_exponent BETWEEN 0 AND 8
        AND basis_currency_exponent BETWEEN 0 AND 8
        AND currency_exponent BETWEEN 0 AND 8
    ),
    ADD CONSTRAINT chk_clearing_record_status_tpl CHECK (
        record_status IN ('ACTIVE', 'SUPERSEDED')
    ),
    ADD CONSTRAINT chk_clearing_enum_tpl CHECK (
        item_type IN (
            'PRINCIPAL', 'PLATFORM_FEE', 'FEE_REVERSAL', 'ADJUSTMENT'
        )
        AND component_type IN (
            'PRINCIPAL', 'PERCENTAGE', 'FIXED', 'LIMIT_ADJUSTMENT', 'REVERSAL', 'ADJUSTMENT'
        )
        AND applied_limit IN ('NONE', 'MINIMUM', 'MAXIMUM')
        AND limit_evaluation_status IN ('NOT_REQUIRED', 'FINAL_AT_CLEARING', 'PENDING_SETTLEMENT_RATE')
        AND rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'DOWN')
        AND risk_service_type IN ('NONE', 'INTERNAL', 'EXTERNAL', 'THREE_DS')
    ),
    ADD CONSTRAINT chk_clearing_tier_snapshot_tpl CHECK (
        fee_mode IS NULL
        OR (
            fee_mode = 'STANDARD'
            AND tier_period_key IS NULL AND tier_metric IS NULL
            AND tier_count_before IS NULL AND tier_count_delta IS NULL AND tier_count_after IS NULL
            AND tier_amount_usd_before IS NULL AND tier_amount_usd_delta IS NULL
            AND tier_amount_usd_after IS NULL
        )
        OR (
            fee_mode = 'TIER'
            AND tier_metric IN ('COUNT', 'AMOUNT')
            AND tier_period_key IS NOT NULL
            AND tier_count_before >= 0 AND tier_count_delta = 1
            AND tier_count_after = tier_count_before + tier_count_delta
            AND tier_amount_usd_before >= 0 AND tier_amount_usd_delta >= 0
            AND tier_amount_usd_after = tier_amount_usd_before + tier_amount_usd_delta
        )
    ),
    ADD CONSTRAINT chk_clearing_fee_currency_tpl CHECK (
        (component_type <> 'PRINCIPAL' OR (fee_group_no IS NULL AND currency = basis_currency))
        AND (component_type = 'PRINCIPAL' OR fee_group_no IS NOT NULL)
        AND (component_type <> 'PERCENTAGE'
             OR (basis_currency = label_currency AND currency = label_currency))
        AND (component_type <> 'FIXED'
             OR (currency = 'USD' AND basis_currency = 'USD'))
        AND (component_type <> 'LIMIT_ADJUSTMENT'
             OR (currency = 'USD' AND basis_currency = 'USD'))
    ),
    ADD CONSTRAINT chk_clearing_risk_type_tpl CHECK (
        (COALESCE(fee_category, '') = 'RISK_FEE'
         AND risk_service_type IN ('INTERNAL', 'EXTERNAL', 'THREE_DS'))
        OR (COALESCE(fee_category, '') <> 'RISK_FEE' AND risk_service_type = 'NONE')
    );

ALTER TABLE transaction_clearing_detail_202603
    ADD CONSTRAINT chk_clearing_revision_line_202603 CHECK (clearing_revision >= 1 AND line_no >= 1),
    ADD CONSTRAINT chk_clearing_direction_202603 CHECK (direction IN ('CREDIT', 'DEBIT')),
    ADD CONSTRAINT chk_clearing_amount_202603 CHECK (
        label_amount >= 0 AND basis_amount >= 0 AND amount >= 0 AND component_no >= 1
        AND (percentage_rate IS NULL OR percentage_rate >= 0)
        AND (fixed_amount_usd IS NULL OR fixed_amount_usd >= 0)
        AND (minimum_amount_usd IS NULL OR minimum_amount_usd >= 0)
        AND (maximum_amount_usd IS NULL OR maximum_amount_usd >= 0)
        AND (minimum_amount_usd IS NULL OR maximum_amount_usd IS NULL
             OR minimum_amount_usd <= maximum_amount_usd)
    ),
    ADD CONSTRAINT chk_clearing_exponent_202603 CHECK (
        label_currency_exponent BETWEEN 0 AND 8
        AND basis_currency_exponent BETWEEN 0 AND 8
        AND currency_exponent BETWEEN 0 AND 8
    ),
    ADD CONSTRAINT chk_clearing_record_status_202603 CHECK (
        record_status IN ('ACTIVE', 'SUPERSEDED')
    ),
    ADD CONSTRAINT chk_clearing_enum_202603 CHECK (
        item_type IN (
            'PRINCIPAL', 'PLATFORM_FEE', 'FEE_REVERSAL', 'ADJUSTMENT'
        )
        AND component_type IN (
            'PRINCIPAL', 'PERCENTAGE', 'FIXED', 'LIMIT_ADJUSTMENT', 'REVERSAL', 'ADJUSTMENT'
        )
        AND applied_limit IN ('NONE', 'MINIMUM', 'MAXIMUM')
        AND limit_evaluation_status IN ('NOT_REQUIRED', 'FINAL_AT_CLEARING', 'PENDING_SETTLEMENT_RATE')
        AND rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'DOWN')
        AND risk_service_type IN ('NONE', 'INTERNAL', 'EXTERNAL', 'THREE_DS')
    ),
    ADD CONSTRAINT chk_clearing_tier_snapshot_202603 CHECK (
        fee_mode IS NULL
        OR (
            fee_mode = 'STANDARD'
            AND tier_period_key IS NULL AND tier_metric IS NULL
            AND tier_count_before IS NULL AND tier_count_delta IS NULL AND tier_count_after IS NULL
            AND tier_amount_usd_before IS NULL AND tier_amount_usd_delta IS NULL
            AND tier_amount_usd_after IS NULL
        )
        OR (
            fee_mode = 'TIER'
            AND tier_metric IN ('COUNT', 'AMOUNT')
            AND tier_period_key IS NOT NULL
            AND tier_count_before >= 0 AND tier_count_delta = 1
            AND tier_count_after = tier_count_before + tier_count_delta
            AND tier_amount_usd_before >= 0 AND tier_amount_usd_delta >= 0
            AND tier_amount_usd_after = tier_amount_usd_before + tier_amount_usd_delta
        )
    ),
    ADD CONSTRAINT chk_clearing_fee_currency_202603 CHECK (
        (component_type <> 'PRINCIPAL' OR (fee_group_no IS NULL AND currency = basis_currency))
        AND (component_type = 'PRINCIPAL' OR fee_group_no IS NOT NULL)
        AND (component_type <> 'PERCENTAGE'
             OR (basis_currency = label_currency AND currency = label_currency))
        AND (component_type <> 'FIXED'
             OR (currency = 'USD' AND basis_currency = 'USD'))
        AND (component_type <> 'LIMIT_ADJUSTMENT'
             OR (currency = 'USD' AND basis_currency = 'USD'))
    ),
    ADD CONSTRAINT chk_clearing_risk_type_202603 CHECK (
        (COALESCE(fee_category, '') = 'RISK_FEE'
         AND risk_service_type IN ('INTERNAL', 'EXTERNAL', 'THREE_DS'))
        OR (COALESCE(fee_category, '') <> 'RISK_FEE' AND risk_service_type = 'NONE')
    );

ALTER TABLE transaction_clearing_detail_202604
    ADD CONSTRAINT chk_clearing_revision_line_202604 CHECK (clearing_revision >= 1 AND line_no >= 1),
    ADD CONSTRAINT chk_clearing_direction_202604 CHECK (direction IN ('CREDIT', 'DEBIT')),
    ADD CONSTRAINT chk_clearing_amount_202604 CHECK (
        label_amount >= 0 AND basis_amount >= 0 AND amount >= 0 AND component_no >= 1
        AND (percentage_rate IS NULL OR percentage_rate >= 0)
        AND (fixed_amount_usd IS NULL OR fixed_amount_usd >= 0)
        AND (minimum_amount_usd IS NULL OR minimum_amount_usd >= 0)
        AND (maximum_amount_usd IS NULL OR maximum_amount_usd >= 0)
        AND (minimum_amount_usd IS NULL OR maximum_amount_usd IS NULL
             OR minimum_amount_usd <= maximum_amount_usd)
    ),
    ADD CONSTRAINT chk_clearing_exponent_202604 CHECK (
        label_currency_exponent BETWEEN 0 AND 8
        AND basis_currency_exponent BETWEEN 0 AND 8
        AND currency_exponent BETWEEN 0 AND 8
    ),
    ADD CONSTRAINT chk_clearing_record_status_202604 CHECK (
        record_status IN ('ACTIVE', 'SUPERSEDED')
    ),
    ADD CONSTRAINT chk_clearing_enum_202604 CHECK (
        item_type IN (
            'PRINCIPAL', 'PLATFORM_FEE', 'FEE_REVERSAL', 'ADJUSTMENT'
        )
        AND component_type IN (
            'PRINCIPAL', 'PERCENTAGE', 'FIXED', 'LIMIT_ADJUSTMENT', 'REVERSAL', 'ADJUSTMENT'
        )
        AND applied_limit IN ('NONE', 'MINIMUM', 'MAXIMUM')
        AND limit_evaluation_status IN ('NOT_REQUIRED', 'FINAL_AT_CLEARING', 'PENDING_SETTLEMENT_RATE')
        AND rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'DOWN')
        AND risk_service_type IN ('NONE', 'INTERNAL', 'EXTERNAL', 'THREE_DS')
    ),
    ADD CONSTRAINT chk_clearing_tier_snapshot_202604 CHECK (
        fee_mode IS NULL
        OR (
            fee_mode = 'STANDARD'
            AND tier_period_key IS NULL AND tier_metric IS NULL
            AND tier_count_before IS NULL AND tier_count_delta IS NULL AND tier_count_after IS NULL
            AND tier_amount_usd_before IS NULL AND tier_amount_usd_delta IS NULL
            AND tier_amount_usd_after IS NULL
        )
        OR (
            fee_mode = 'TIER'
            AND tier_metric IN ('COUNT', 'AMOUNT')
            AND tier_period_key IS NOT NULL
            AND tier_count_before >= 0 AND tier_count_delta = 1
            AND tier_count_after = tier_count_before + tier_count_delta
            AND tier_amount_usd_before >= 0 AND tier_amount_usd_delta >= 0
            AND tier_amount_usd_after = tier_amount_usd_before + tier_amount_usd_delta
        )
    ),
    ADD CONSTRAINT chk_clearing_fee_currency_202604 CHECK (
        (component_type <> 'PRINCIPAL' OR (fee_group_no IS NULL AND currency = basis_currency))
        AND (component_type = 'PRINCIPAL' OR fee_group_no IS NOT NULL)
        AND (component_type <> 'PERCENTAGE'
             OR (basis_currency = label_currency AND currency = label_currency))
        AND (component_type <> 'FIXED'
             OR (currency = 'USD' AND basis_currency = 'USD'))
        AND (component_type <> 'LIMIT_ADJUSTMENT'
             OR (currency = 'USD' AND basis_currency = 'USD'))
    ),
    ADD CONSTRAINT chk_clearing_risk_type_202604 CHECK (
        (COALESCE(fee_category, '') = 'RISK_FEE'
         AND risk_service_type IN ('INTERNAL', 'EXTERNAL', 'THREE_DS'))
        OR (COALESCE(fee_category, '') <> 'RISK_FEE' AND risk_service_type = 'NONE')
    );

-- ============================================================================
-- 10. 第27张交易逻辑表：保证金清分明细模板和活动季度物理表
-- ============================================================================

CREATE TABLE transaction_reserve_clearing_detail (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID',
    reserve_clearing_detail_no VARCHAR(64) NOT NULL COMMENT '保证金清分明细号',
    finance_state_id VARCHAR(64) NOT NULL COMMENT '所属当前动作清分汇总ID',
    transaction_id VARCHAR(64) NOT NULL COMMENT '产生本明细的动作级平台交易号',
    operation_id VARCHAR(64) NOT NULL COMMENT '交易生命周期关联号',
    original_transaction_id VARCHAR(64) NOT NULL COMMENT '原支付或原请款动作交易号',
    original_transaction_date_time DATETIME(3) NOT NULL COMMENT '原支付动作业务时间，用于精确路由保证金状态',
    source_reserve_detail_no VARCHAR(64) NULL COMMENT 'RETURN、RELEASE引用的原HOLD保证金明细号',
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号',
    payment_type VARCHAR(32) NOT NULL COMMENT '保证金事实形成时冻结的支付类型',
    payment_method VARCHAR(32) NOT NULL COMMENT '保证金事实形成时冻结的支付方式',
    transaction_type VARCHAR(32) NOT NULL COMMENT '产生本保证金事实的交易动作类型',
    clearing_revision INT NOT NULL COMMENT '所属当前动作清分修订号，从1递增',
    line_no INT NOT NULL COMMENT '同一修订内保证金稳定明细序号，从1递增',
    reserve_action_type VARCHAR(16) NOT NULL COMMENT 'HOLD、RETURN、RELEASE、ADJUSTMENT',
    item_code VARCHAR(128) NOT NULL COMMENT '稳定业务项编码，例如RESERVE:HOLD或RESERVE:RETURN:原HOLD明细号',
    item_name VARCHAR(128) NOT NULL COMMENT '保证金项目名称快照',
    direction VARCHAR(8) NOT NULL COMMENT '商户视角：HOLD为DEBIT，RETURN或RELEASE为CREDIT',
    reserve_currency CHAR(3) NOT NULL COMMENT '保证金负债币种，固定等于原支付标签币种',
    reserve_currency_exponent TINYINT NOT NULL COMMENT '保证金币种ISO小数位',
    basis_amount DECIMAL(24,8) NOT NULL COMMENT '支付或本次退款保证金币种基数，始终非负',
    reserve_rate DECIMAL(12,8) NOT NULL COMMENT '原支付清分时固化的保证金比例，例如10表示10%',
    retained_amount DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '本明细HOLD扣留金额，其它动作固定0',
    returned_amount DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '本明细RETURN返还金额，其它动作固定0',
    released_amount DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '本明细RELEASE释放金额，其它动作固定0',
    adjustment_amount DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '本明细ADJUSTMENT绝对金额，其它动作固定0',
    remaining_amount DECIMAL(24,8) NOT NULL COMMENT '本明细入账后原HOLD剩余保证金负债金额',
    fee_plan_id BIGINT NOT NULL COMMENT '原支付费用方案ID快照',
    fee_plan_version_id BIGINT NOT NULL COMMENT '原支付不可变费用版本ID快照',
    fee_plan_version_no INT NOT NULL COMMENT '原支付费用版本号快照',
    reserve_snapshot_hash CHAR(64) NOT NULL COMMENT '原支付保证金配置规范化快照SHA-256',
    reserve_basis VARCHAR(32) NOT NULL COMMENT '保证金基数快照，当前固定LABEL_AMOUNT',
    reserve_delay_unit CHAR(1) NULL COMMENT '原支付保证金留存周期单位T或D',
    reserve_delay_days INT NULL COMMENT '原支付保证金留存天数',
    rounding_mode VARCHAR(16) NOT NULL COMMENT '原支付保证金舍入规则快照',
    formula_snapshot VARCHAR(1000) NOT NULL COMMENT '标签币种保证金扣留、返还或释放计算公式快照',
    expected_reserve_release_date DATE NULL COMMENT '预计保证金释放业务日期',
    record_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、SUPERSEDED',
    transaction_date_time DATETIME(3) NOT NULL COMMENT '产生本明细的当前动作业务时间，季度分片键',
    transaction_utc_time DATETIME(3) NOT NULL COMMENT '当前动作业务时间对应UTC时间',
    transaction_time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '当前动作业务时区',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reserve_clearing_detail_no (reserve_clearing_detail_no),
    UNIQUE KEY uk_reserve_revision_line (finance_state_id, clearing_revision, line_no),
    UNIQUE KEY uk_reserve_transaction_item (transaction_id, clearing_revision, item_code),
    KEY idx_reserve_transaction_time (transaction_id, transaction_date_time, clearing_revision, id),
    KEY idx_reserve_original
        (original_transaction_id, original_transaction_date_time, source_reserve_detail_no, transaction_date_time, id),
    KEY idx_reserve_fee_version (fee_plan_version_id, merchant_id, transaction_date_time, id),
    KEY idx_reserve_settlement_eligible
        (merchant_id, reserve_currency, record_status, expected_reserve_release_date, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='动作级不可变保证金清分明细；与交易清分明细完全分离，跟随当前动作季度分片';

CREATE TABLE transaction_reserve_clearing_detail_202603 LIKE transaction_reserve_clearing_detail;
ALTER TABLE transaction_reserve_clearing_detail_202603
    AUTO_INCREMENT = 202603200000000001,
    COMMENT = '2026年第3季度动作级保证金清分明细';

CREATE TABLE transaction_reserve_clearing_detail_202604 LIKE transaction_reserve_clearing_detail;
ALTER TABLE transaction_reserve_clearing_detail_202604
    AUTO_INCREMENT = 202604200000000001,
    COMMENT = '2026年第4季度动作级保证金清分明细';

ALTER TABLE transaction_reserve_clearing_detail
    ADD CONSTRAINT chk_reserve_detail_revision_tpl CHECK (clearing_revision >= 1 AND line_no >= 1),
    ADD CONSTRAINT chk_reserve_detail_amount_tpl CHECK (
        basis_amount >= 0 AND retained_amount >= 0 AND returned_amount >= 0
        AND released_amount >= 0 AND adjustment_amount >= 0 AND remaining_amount >= 0
        AND reserve_rate >= 0 AND reserve_rate <= 100
        AND reserve_currency_exponent BETWEEN 0 AND 8
    ),
    ADD CONSTRAINT chk_reserve_detail_enum_tpl CHECK (
        reserve_action_type IN ('HOLD', 'RETURN', 'RELEASE', 'ADJUSTMENT')
        AND direction IN ('CREDIT', 'DEBIT')
        AND reserve_basis = 'LABEL_AMOUNT'
        AND rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'DOWN')
        AND record_status IN ('ACTIVE', 'SUPERSEDED')
    ),
    ADD CONSTRAINT chk_reserve_detail_source_tpl CHECK (
        (reserve_action_type = 'HOLD'
         AND direction = 'DEBIT'
         AND original_transaction_id = transaction_id AND source_reserve_detail_no IS NULL
         AND retained_amount > 0 AND returned_amount = 0 AND released_amount = 0 AND adjustment_amount = 0)
        OR (reserve_action_type IN ('RETURN', 'RELEASE')
            AND direction = 'CREDIT'
            AND source_reserve_detail_no IS NOT NULL
            AND retained_amount = 0 AND adjustment_amount = 0
            AND ((reserve_action_type = 'RETURN' AND returned_amount > 0 AND released_amount = 0)
                 OR (reserve_action_type = 'RELEASE' AND released_amount > 0 AND returned_amount = 0)))
        OR (reserve_action_type = 'ADJUSTMENT' AND adjustment_amount > 0
            AND retained_amount = 0 AND returned_amount = 0 AND released_amount = 0)
    );

ALTER TABLE transaction_reserve_clearing_detail_202603
    ADD CONSTRAINT chk_reserve_detail_revision_202603 CHECK (clearing_revision >= 1 AND line_no >= 1),
    ADD CONSTRAINT chk_reserve_detail_amount_202603 CHECK (
        basis_amount >= 0 AND retained_amount >= 0 AND returned_amount >= 0
        AND released_amount >= 0 AND adjustment_amount >= 0 AND remaining_amount >= 0
        AND reserve_rate >= 0 AND reserve_rate <= 100 AND reserve_currency_exponent BETWEEN 0 AND 8
    ),
    ADD CONSTRAINT chk_reserve_detail_enum_202603 CHECK (
        reserve_action_type IN ('HOLD', 'RETURN', 'RELEASE', 'ADJUSTMENT')
        AND direction IN ('CREDIT', 'DEBIT') AND reserve_basis = 'LABEL_AMOUNT'
        AND rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'DOWN')
        AND record_status IN ('ACTIVE', 'SUPERSEDED')
    ),
    ADD CONSTRAINT chk_reserve_detail_source_202603 CHECK (
        (reserve_action_type = 'HOLD' AND direction = 'DEBIT'
         AND original_transaction_id = transaction_id AND source_reserve_detail_no IS NULL
         AND retained_amount > 0 AND returned_amount = 0 AND released_amount = 0 AND adjustment_amount = 0)
        OR (reserve_action_type IN ('RETURN', 'RELEASE')
            AND direction = 'CREDIT' AND source_reserve_detail_no IS NOT NULL
            AND retained_amount = 0 AND adjustment_amount = 0
            AND ((reserve_action_type = 'RETURN' AND returned_amount > 0 AND released_amount = 0)
                 OR (reserve_action_type = 'RELEASE' AND released_amount > 0 AND returned_amount = 0)))
        OR (reserve_action_type = 'ADJUSTMENT' AND adjustment_amount > 0
            AND retained_amount = 0 AND returned_amount = 0 AND released_amount = 0)
    );

ALTER TABLE transaction_reserve_clearing_detail_202604
    ADD CONSTRAINT chk_reserve_detail_revision_202604 CHECK (clearing_revision >= 1 AND line_no >= 1),
    ADD CONSTRAINT chk_reserve_detail_amount_202604 CHECK (
        basis_amount >= 0 AND retained_amount >= 0 AND returned_amount >= 0
        AND released_amount >= 0 AND adjustment_amount >= 0 AND remaining_amount >= 0
        AND reserve_rate >= 0 AND reserve_rate <= 100 AND reserve_currency_exponent BETWEEN 0 AND 8
    ),
    ADD CONSTRAINT chk_reserve_detail_enum_202604 CHECK (
        reserve_action_type IN ('HOLD', 'RETURN', 'RELEASE', 'ADJUSTMENT')
        AND direction IN ('CREDIT', 'DEBIT') AND reserve_basis = 'LABEL_AMOUNT'
        AND rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'DOWN')
        AND record_status IN ('ACTIVE', 'SUPERSEDED')
    ),
    ADD CONSTRAINT chk_reserve_detail_source_202604 CHECK (
        (reserve_action_type = 'HOLD' AND direction = 'DEBIT'
         AND original_transaction_id = transaction_id AND source_reserve_detail_no IS NULL
         AND retained_amount > 0 AND returned_amount = 0 AND released_amount = 0 AND adjustment_amount = 0)
        OR (reserve_action_type IN ('RETURN', 'RELEASE')
            AND direction = 'CREDIT' AND source_reserve_detail_no IS NOT NULL
            AND retained_amount = 0 AND adjustment_amount = 0
            AND ((reserve_action_type = 'RETURN' AND returned_amount > 0 AND released_amount = 0)
                 OR (reserve_action_type = 'RELEASE' AND released_amount > 0 AND returned_amount = 0)))
        OR (reserve_action_type = 'ADJUSTMENT' AND adjustment_amount > 0
            AND retained_amount = 0 AND returned_amount = 0 AND released_amount = 0)
    );

-- ============================================================================
-- 11. 第28张交易逻辑表：原支付保证金清分状态模板和活动季度物理表
-- ============================================================================

CREATE TABLE transaction_reserve_clearing_state (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理表主键ID',
    reserve_state_id VARCHAR(64) NOT NULL COMMENT '保证金清分状态ID',
    original_transaction_id VARCHAR(64) NOT NULL COMMENT '原支付或原请款动作交易号',
    operation_id VARCHAR(64) NOT NULL COMMENT '交易生命周期关联号',
    original_finance_state_id VARCHAR(64) NOT NULL COMMENT '原支付动作清分汇总ID',
    original_hold_detail_no VARCHAR(64) NOT NULL COMMENT '原HOLD保证金清分明细号',
    original_fee_plan_version_id BIGINT NOT NULL COMMENT '原支付不可变费用版本ID',
    original_reserve_snapshot_hash CHAR(64) NOT NULL COMMENT '原支付保证金配置快照SHA-256',
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号',
    reserve_currency CHAR(3) NOT NULL COMMENT '保证金负债币种，固定等于原支付标签币种',
    reserve_currency_exponent TINYINT NOT NULL COMMENT '保证金币种ISO小数位',
    original_basis_amount DECIMAL(24,8) NOT NULL COMMENT '原支付保证金计提基数',
    original_reserve_rate DECIMAL(12,8) NOT NULL COMMENT '原支付清分时固化的保证金比例',
    original_rounding_mode VARCHAR(16) NOT NULL COMMENT '原支付保证金舍入规则',
    retained_amount DECIMAL(24,8) NOT NULL COMMENT '原支付保证金扣留金额',
    returned_amount DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '累计退款返还金额',
    released_amount DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '累计到期释放金额',
    debit_adjustment_amount DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '经双人复核增加的累计保证金负债',
    credit_adjustment_amount DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '经双人复核减少的累计保证金负债',
    remaining_amount DECIMAL(24,8) NOT NULL COMMENT '剩余保证金负债金额',
    expected_reserve_release_date DATE NULL COMMENT '预计保证金释放业务日期',
    reserve_status VARCHAR(24) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN、FULLY_RETURNED、FULLY_RELEASED、ADJUSTED',
    last_return_transaction_id VARCHAR(64) NULL COMMENT '最近成功返还保证金的退款动作交易号',
    last_return_transaction_date_time DATETIME(3) NULL COMMENT '最近成功返还动作时间',
    transaction_date_time DATETIME(3) NOT NULL COMMENT '原支付动作业务时间，统一季度分片键',
    original_transaction_utc_time DATETIME(3) NOT NULL COMMENT '原支付动作业务时间对应UTC时间',
    transaction_time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '原支付动作业务时区',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '并发退款行锁后的CAS版本号',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reserve_state_id (reserve_state_id),
    UNIQUE KEY uk_reserve_state_original_transaction (original_transaction_id),
    UNIQUE KEY uk_reserve_state_hold_detail (original_hold_detail_no),
    KEY idx_reserve_state_operation (operation_id, transaction_date_time, id),
    KEY idx_reserve_state_merchant_release
        (merchant_id, reserve_currency, reserve_status, expected_reserve_release_date, id),
    KEY idx_reserve_state_metrics
        (reserve_currency, transaction_date_time, remaining_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='原支付保证金当前汇总状态；用于并发退款上限控制，不替代不可变保证金明细';

CREATE TABLE transaction_reserve_clearing_state_202603 LIKE transaction_reserve_clearing_state;
ALTER TABLE transaction_reserve_clearing_state_202603
    AUTO_INCREMENT = 202603400000000001,
    COMMENT = '2026年第3季度原支付保证金清分状态';

CREATE TABLE transaction_reserve_clearing_state_202604 LIKE transaction_reserve_clearing_state;
ALTER TABLE transaction_reserve_clearing_state_202604
    AUTO_INCREMENT = 202604400000000001,
    COMMENT = '2026年第4季度原支付保证金清分状态';

ALTER TABLE transaction_reserve_clearing_state
    ADD CONSTRAINT chk_reserve_state_amount_tpl CHECK (
        original_basis_amount >= 0 AND original_reserve_rate >= 0 AND original_reserve_rate <= 100
        AND retained_amount >= 0 AND returned_amount >= 0 AND released_amount >= 0
        AND debit_adjustment_amount >= 0 AND credit_adjustment_amount >= 0 AND remaining_amount >= 0
        AND retained_amount + debit_adjustment_amount
            = returned_amount + released_amount + credit_adjustment_amount + remaining_amount
    ),
    ADD CONSTRAINT chk_reserve_state_enum_tpl CHECK (
        reserve_currency_exponent BETWEEN 0 AND 8
        AND original_rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'DOWN')
        AND reserve_status IN ('OPEN', 'FULLY_RETURNED', 'FULLY_RELEASED', 'ADJUSTED')
    );

ALTER TABLE transaction_reserve_clearing_state_202603
    ADD CONSTRAINT chk_reserve_state_amount_202603 CHECK (
        original_basis_amount >= 0 AND original_reserve_rate >= 0 AND original_reserve_rate <= 100
        AND retained_amount >= 0 AND returned_amount >= 0 AND released_amount >= 0
        AND debit_adjustment_amount >= 0 AND credit_adjustment_amount >= 0 AND remaining_amount >= 0
        AND retained_amount + debit_adjustment_amount
            = returned_amount + released_amount + credit_adjustment_amount + remaining_amount
    ),
    ADD CONSTRAINT chk_reserve_state_enum_202603 CHECK (
        reserve_currency_exponent BETWEEN 0 AND 8
        AND original_rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'DOWN')
        AND reserve_status IN ('OPEN', 'FULLY_RETURNED', 'FULLY_RELEASED', 'ADJUSTED')
    );

ALTER TABLE transaction_reserve_clearing_state_202604
    ADD CONSTRAINT chk_reserve_state_amount_202604 CHECK (
        original_basis_amount >= 0 AND original_reserve_rate >= 0 AND original_reserve_rate <= 100
        AND retained_amount >= 0 AND returned_amount >= 0 AND released_amount >= 0
        AND debit_adjustment_amount >= 0 AND credit_adjustment_amount >= 0 AND remaining_amount >= 0
        AND retained_amount + debit_adjustment_amount
            = returned_amount + released_amount + credit_adjustment_amount + remaining_amount
    ),
    ADD CONSTRAINT chk_reserve_state_enum_202604 CHECK (
        reserve_currency_exponent BETWEEN 0 AND 8
        AND original_rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'DOWN')
        AND reserve_status IN ('OPEN', 'FULLY_RETURNED', 'FULLY_RELEASED', 'ADJUSTED')
    );

-- ============================================================================
-- 12. 清分人工复核与阶梯期间重放固定表；现有 transaction_rw.* Single Rule 自动发现
-- ============================================================================

CREATE TABLE clearing_reserve_adjustment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    adjustment_no VARCHAR(64) NOT NULL COMMENT '稳定保证金调整业务号',
    request_key VARCHAR(128) NOT NULL COMMENT '提交请求幂等键',
    reserve_state_id VARCHAR(64) NOT NULL COMMENT '目标保证金状态业务号',
    original_transaction_id VARCHAR(64) NOT NULL COMMENT '原支付动作交易号',
    original_transaction_date_time DATETIME(3) NOT NULL COMMENT '原支付季度精确路由时间',
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号',
    reserve_currency CHAR(3) NOT NULL COMMENT '保证金标签币种；调整全程禁止换汇',
    reserve_currency_exponent TINYINT NOT NULL COMMENT '保证金币种ISO小数位',
    direction VARCHAR(8) NOT NULL COMMENT 'DEBIT增加负债、CREDIT减少负债',
    adjustment_amount DECIMAL(24,8) NOT NULL COMMENT '标签币种绝对调整金额',
    requested_release_date DATE NULL COMMENT 'DEBIT调整后的预计释放业务日期；CREDIT为空',
    expected_reserve_state_version BIGINT NOT NULL COMMENT '提交时冻结的保证金状态CAS版本',
    reason VARCHAR(400) NOT NULL COMMENT '调整原因，不保存敏感原文',
    submit_operator VARCHAR(128) NOT NULL COMMENT '可信Admin登录上下文提交人',
    review_operator VARCHAR(128) NULL COMMENT '可信Admin登录上下文复核人，必须不同于提交人',
    review_comment VARCHAR(400) NULL COMMENT '复核意见',
    adjustment_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_REVIEW'
        COMMENT 'PENDING_REVIEW、EXECUTED、REJECTED',
    execution_transaction_id VARCHAR(64) NULL COMMENT '批准后形成的独立保证金调整动作号',
    source_revision INT NULL COMMENT '批准后形成的保证金来源修订',
    review_time DATETIME(3) NULL COMMENT '复核时间',
    executed_time DATETIME(3) NULL COMMENT '调整事实、状态和候选同事务完成时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '审批状态CAS版本',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reserve_adjustment_no (adjustment_no),
    UNIQUE KEY uk_reserve_adjustment_request (request_key),
    UNIQUE KEY uk_reserve_adjustment_execution (execution_transaction_id),
    KEY idx_reserve_adjustment_state
        (reserve_state_id, original_transaction_date_time, adjustment_status, id),
    KEY idx_reserve_adjustment_review (adjustment_status, create_time, id),
    CONSTRAINT chk_reserve_adjustment_value CHECK (
        reserve_currency_exponent BETWEEN 0 AND 8
        AND adjustment_amount > 0
        AND expected_reserve_state_version >= 0
        AND version >= 0
        AND ((direction = 'DEBIT' AND requested_release_date IS NOT NULL)
             OR (direction = 'CREDIT' AND requested_release_date IS NULL))
    ),
    CONSTRAINT chk_reserve_adjustment_state CHECK (
        adjustment_status IN ('PENDING_REVIEW', 'EXECUTED', 'REJECTED')
        AND (
            (adjustment_status = 'PENDING_REVIEW'
             AND review_operator IS NULL AND review_comment IS NULL AND review_time IS NULL
             AND execution_transaction_id IS NULL AND source_revision IS NULL AND executed_time IS NULL)
            OR (adjustment_status = 'REJECTED'
                AND review_operator IS NOT NULL AND review_comment IS NOT NULL AND review_time IS NOT NULL
                AND review_operator <> submit_operator
                AND execution_transaction_id IS NULL AND source_revision IS NULL AND executed_time IS NULL)
            OR (adjustment_status = 'EXECUTED'
                AND review_operator IS NOT NULL AND review_comment IS NOT NULL AND review_time IS NOT NULL
                AND review_operator <> submit_operator
                AND execution_transaction_id IS NOT NULL AND source_revision >= 1
                AND executed_time IS NOT NULL)
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='保证金标签币种差额调整双人复核事实；不保存汇率、不直接写余额';

CREATE TABLE clearing_tier_period_replay (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    replay_no VARCHAR(64) NOT NULL COMMENT '稳定阶梯期间重放业务号',
    request_key VARCHAR(128) NOT NULL COMMENT '提交请求幂等键',
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号',
    fee_plan_id BIGINT NOT NULL COMMENT '商户费用方案ID',
    fee_plan_version_id BIGINT NOT NULL COMMENT '不可变费用版本ID',
    trigger_fee_rule_id BIGINT NOT NULL COMMENT '触发申请的阶梯规则ID',
    period_key CHAR(6) NOT NULL COMMENT '重放月份yyyyMM',
    period_start DATETIME(3) NOT NULL COMMENT '平台业务时区月份起点，闭区间',
    period_end DATETIME(3) NOT NULL COMMENT '平台业务时区下月起点，开区间',
    reason VARCHAR(400) NOT NULL COMMENT '重放原因',
    submit_operator VARCHAR(128) NOT NULL COMMENT '可信Admin登录上下文提交人',
    review_operator VARCHAR(128) NULL COMMENT '可信Admin登录上下文复核人，必须不同于提交人',
    review_comment VARCHAR(400) NULL COMMENT '复核意见',
    replay_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_REVIEW'
        COMMENT 'PENDING_REVIEW、PREPARING、RUNNING、COMPLETED、MANUAL_REVIEW、REJECTED',
    item_count INT NOT NULL DEFAULT 0 COMMENT '复核后冻结的稳定动作项数量',
    completed_count INT NOT NULL DEFAULT 0 COMMENT '已按稳定顺序完成的动作项数量',
    last_clearing_complete_time DATETIME(3) NULL COMMENT '最后完成项的原清分完成时间游标',
    last_transaction_id VARCHAR(64) NULL COMMENT '最后完成项动作号游标',
    last_error_code VARCHAR(64) NULL COMMENT '有限错误码',
    last_error_message VARCHAR(400) NULL COMMENT '脱敏错误摘要',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '重放控制状态CAS版本',
    review_time DATETIME(3) NULL COMMENT '复核时间',
    completed_time DATETIME(3) NULL COMMENT '全部动作项完成时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tier_replay_no (replay_no),
    UNIQUE KEY uk_tier_replay_request (request_key),
    KEY idx_tier_replay_scope
        (merchant_id, fee_plan_version_id, period_key, replay_status, id),
    KEY idx_tier_replay_runnable (replay_status, update_time, id),
    CONSTRAINT chk_tier_replay_value CHECK (
        fee_plan_id > 0 AND fee_plan_version_id > 0 AND trigger_fee_rule_id > 0
        AND REGEXP_LIKE(period_key, '^[0-9]{4}(0[1-9]|1[0-2])$')
        AND period_end > period_start
        AND item_count >= 0 AND completed_count >= 0 AND completed_count <= item_count
        AND version >= 0
    ),
    CONSTRAINT chk_tier_replay_state CHECK (
        replay_status IN (
            'PENDING_REVIEW', 'PREPARING', 'RUNNING', 'COMPLETED', 'MANUAL_REVIEW', 'REJECTED'
        )
        AND (
            (replay_status = 'PENDING_REVIEW'
             AND review_operator IS NULL AND review_comment IS NULL AND review_time IS NULL
             AND item_count = 0 AND completed_count = 0 AND completed_time IS NULL)
            OR (replay_status = 'REJECTED'
                AND review_operator IS NOT NULL AND review_comment IS NOT NULL AND review_time IS NOT NULL
                AND review_operator <> submit_operator
                AND item_count = 0 AND completed_count = 0 AND completed_time IS NULL)
            OR (replay_status IN ('PREPARING', 'RUNNING', 'MANUAL_REVIEW')
                AND review_operator IS NOT NULL AND review_comment IS NOT NULL AND review_time IS NOT NULL
                AND review_operator <> submit_operator AND completed_time IS NULL)
            OR (replay_status = 'COMPLETED'
                AND review_operator IS NOT NULL AND review_comment IS NOT NULL AND review_time IS NOT NULL
                AND review_operator <> submit_operator
                AND item_count > 0 AND completed_count = item_count AND completed_time IS NOT NULL)
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='不可变商户费用版本的月度阶梯重放控制；数据库累计为权威，Redis仅镜像';

CREATE TABLE clearing_tier_period_replay_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    replay_no VARCHAR(64) NOT NULL COMMENT '所属阶梯期间重放业务号',
    sequence_no INT NOT NULL COMMENT '按原清分完成时间和动作号生成的稳定序号',
    finance_state_id VARCHAR(64) NOT NULL COMMENT '原清分汇总业务号',
    transaction_id VARCHAR(64) NOT NULL COMMENT '原动作交易号',
    transaction_date_time DATETIME(3) NOT NULL COMMENT '原动作季度精确路由时间',
    expected_clearing_revision INT NOT NULL COMMENT '冻结时原清分修订',
    expected_finance_state_version INT NOT NULL COMMENT '冻结时finance state CAS版本',
    clearing_complete_time DATETIME(3) NOT NULL COMMENT '原清分完成时间，用于稳定顺序',
    item_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING、FAILED、COMPLETED',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '失败尝试次数，最大8次后转人工复核',
    next_retry_time DATETIME(3) NULL COMMENT 'FAILED项的下次重试时间',
    last_error_code VARCHAR(64) NULL COMMENT '有限错误码',
    last_error_message VARCHAR(400) NULL COMMENT '脱敏错误摘要',
    processed_revision INT NULL COMMENT '成功替换后的新清分修订',
    processed_time DATETIME(3) NULL COMMENT '单项短事务完成时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '重放项状态CAS版本',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tier_replay_item_sequence (replay_no, sequence_no),
    UNIQUE KEY uk_tier_replay_item_finance
        (replay_no, finance_state_id, expected_clearing_revision),
    KEY idx_tier_replay_item_runnable
        (replay_no, item_status, next_retry_time, sequence_no, id),
    KEY idx_tier_replay_item_transaction
        (transaction_id, transaction_date_time, expected_clearing_revision, id),
    CONSTRAINT chk_tier_replay_item_value CHECK (
        sequence_no >= 1 AND expected_clearing_revision >= 1
        AND expected_finance_state_version >= 0
        AND attempt_count BETWEEN 0 AND 8
        AND version >= 0
    ),
    CONSTRAINT chk_tier_replay_item_state CHECK (
        item_status IN ('PENDING', 'FAILED', 'COMPLETED')
        AND (
            (item_status = 'PENDING'
             AND attempt_count = 0 AND next_retry_time IS NULL
             AND last_error_code IS NULL AND last_error_message IS NULL
             AND processed_revision IS NULL AND processed_time IS NULL)
            OR (item_status = 'FAILED'
                AND attempt_count >= 1 AND next_retry_time IS NOT NULL
                AND last_error_code IS NOT NULL AND last_error_message IS NOT NULL
                AND processed_revision IS NULL AND processed_time IS NULL)
            OR (item_status = 'COMPLETED'
                AND next_retry_time IS NULL
                AND last_error_code IS NULL AND last_error_message IS NULL
                AND processed_revision > expected_clearing_revision AND processed_time IS NOT NULL)
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='阶梯期间重放稳定动作项；每项独立短事务并按sequence_no严格推进';


-- ============================================================================
-- 13. 结算目标固定表评审结构；一期只建结构或保留，不启用资金处理
-- ============================================================================

CREATE TABLE merchant_settlement_profile (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    settlement_profile_no VARCHAR(64) NOT NULL COMMENT '稳定结算档案号',
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号',
    settlement_account_id BIGINT NOT NULL COMMENT '目标商户资金账户ID',
    target_currency CHAR(3) NOT NULL COMMENT '目标结算币种',
    target_currency_exponent TINYINT NOT NULL COMMENT '目标币种ISO小数位',
    business_time_zone VARCHAR(64) NOT NULL COMMENT '日切使用的IANA业务时区',
    daily_cutoff_time TIME NOT NULL COMMENT '商户本地每日结算日切时间',
    profile_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、RETIRED、SUSPENDED',
    active_slot TINYINT NULL COMMENT '活动档案固定为1，非活动档案为空，用于唯一约束',
    effective_date DATE NOT NULL COMMENT '档案生效业务日期',
    expire_date DATE NULL COMMENT '档案失效业务日期，长期有效为空',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '档案并发和审计版本',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_profile_no (settlement_profile_no),
    UNIQUE KEY uk_settlement_profile_active (merchant_id, active_slot),
    KEY idx_settlement_profile_account (settlement_account_id, merchant_id, id),
    CONSTRAINT chk_settlement_profile_value CHECK (
        target_currency_exponent BETWEEN 0 AND 8
        AND profile_status IN ('ACTIVE', 'RETIRED', 'SUSPENDED')
        AND ((profile_status = 'ACTIVE' AND active_slot = 1)
             OR (profile_status <> 'ACTIVE' AND active_slot IS NULL))
        AND (expire_date IS NULL OR expire_date >= effective_date)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='商户结算日历、目标币种和资金账户的权威档案；每商户最多一条活动档案';

CREATE TABLE settlement_batch_daily_sequence (
    business_date DATE NOT NULL COMMENT '独立业务日期；业务逻辑禁止解析批次号取得',
    current_sequence INT NOT NULL DEFAULT 0 COMMENT '当日已分配最大序号，范围0至99999999',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '审计版本；分配仍必须SELECT FOR UPDATE',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (business_date),
    CONSTRAINT chk_settlement_daily_sequence_value CHECK (
        current_sequence >= 0 AND current_sequence <= 99999999
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='结算批次号数据库日序列；允许空洞，禁止回收和Redis/JVM单独发号';

CREATE TABLE settlement_batch (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    settlement_batch_no VARCHAR(19) NOT NULL COMMENT '批次号，格式SByyyyMMdd-NNNNNNNN',
    create_request_key VARCHAR(128) NOT NULL COMMENT '调度或人工创建请求幂等键',
    business_date DATE NOT NULL COMMENT '结算业务日期，禁止从批次号解析',
    business_time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '结算日历业务时区',
    daily_sequence INT NOT NULL COMMENT '当日数据库序号，1至99999999',
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号',
    settlement_profile_id BIGINT NOT NULL COMMENT '冻结的商户结算配置ID',
    settlement_account_id BIGINT NOT NULL COMMENT '目标结算资金账户ID',
    target_currency CHAR(3) NOT NULL COMMENT '本批目标结算币种',
    target_currency_exponent TINYINT NOT NULL COMMENT '目标币种ISO小数位',
    batch_type VARCHAR(24) NOT NULL DEFAULT 'REGULAR'
        COMMENT 'REGULAR、RESERVE_RELEASE、REVERSAL、ADJUSTMENT',
    original_batch_no VARCHAR(19) NULL COMMENT '冲正或调整引用的原批次号',
    cutoff_begin_time DATETIME(3) NOT NULL COMMENT '候选窗口起始时间，闭区间',
    cutoff_end_time DATETIME(3) NOT NULL COMMENT '候选窗口结束时间，开区间',
    batch_status VARCHAR(24) NOT NULL DEFAULT 'CREATED'
        COMMENT 'CREATED、CLAIMING、CLAIMED、RATE_LOCKED、CALCULATING、CALCULATED、POSTING、POSTED、FAILED_RETRYABLE、MANUAL_REVIEW、CANCELLED、REVERSING、REVERSED',
    candidate_count INT NOT NULL DEFAULT 0 COMMENT '本批已认领候选数',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '批次阶段重试次数',
    processing_owner VARCHAR(128) NULL COMMENT '当前处理租约所有者',
    processing_deadline DATETIME(3) NULL COMMENT '处理租约截止时间',
    last_failure_stage VARCHAR(32) NULL COMMENT '最近失败阶段',
    last_failure_code VARCHAR(64) NULL COMMENT '最近失败码',
    last_failure_message VARCHAR(512) NULL COMMENT '最近失败摘要，不保存敏感正文',
    rate_locked_time DATETIME(3) NULL COMMENT '汇率矩阵完整锁定时间',
    calculated_time DATETIME(3) NULL COMMENT '结果和汇总计算完成时间，不代表余额入账',
    posted_time DATETIME(3) NULL COMMENT '资金和批次结果提交时间',
    cancelled_time DATETIME(3) NULL COMMENT '入账前取消时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '批次状态CAS版本',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_batch_no (settlement_batch_no),
    UNIQUE KEY uk_settlement_create_request (create_request_key),
    UNIQUE KEY uk_settlement_business_sequence (business_date, daily_sequence),
    KEY idx_settlement_merchant_date (merchant_id, business_date, batch_status, id),
    KEY idx_settlement_status_lease (batch_status, processing_deadline, id),
    KEY idx_settlement_original_batch (original_batch_no, batch_type, id),
    CONSTRAINT chk_settlement_batch_value CHECK (
        daily_sequence BETWEEN 1 AND 99999999
        AND target_currency_exponent BETWEEN 0 AND 8
        AND cutoff_end_time > cutoff_begin_time
        AND candidate_count >= 0 AND retry_count >= 0
    ),
    CONSTRAINT chk_settlement_batch_enum CHECK (
        batch_type IN ('REGULAR', 'RESERVE_RELEASE', 'REVERSAL', 'ADJUSTMENT')
        AND batch_status IN (
            'CREATED', 'CLAIMING', 'CLAIMED', 'RATE_LOCKED', 'CALCULATING', 'CALCULATED',
            'POSTING', 'POSTED',
            'FAILED_RETRYABLE', 'MANUAL_REVIEW', 'CANCELLED', 'REVERSING', 'REVERSED'
        )
        AND ((batch_type IN ('REVERSAL', 'ADJUSTMENT') AND original_batch_no IS NOT NULL)
             OR (batch_type IN ('REGULAR', 'RESERVE_RELEASE') AND original_batch_no IS NULL))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='商户单目标币种结算批次；批次号和创建请求双重幂等';

CREATE TABLE settlement_candidate (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    candidate_no VARCHAR(64) NOT NULL COMMENT '候选业务号',
    source_type VARCHAR(24) NOT NULL COMMENT 'CLEARING_REVISION、RESERVE_RELEASE、ADJUSTMENT',
    source_business_id VARCHAR(64) NOT NULL COMMENT 'finance_state_id、reserve_state_id或调整业务号',
    source_revision INT NOT NULL COMMENT '清分修订号或来源版本，从1递增',
    source_transaction_id VARCHAR(64) NULL COMMENT '来源动作交易号',
    source_transaction_date_time DATETIME(3) NULL COMMENT '来源季度精确路由时间',
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号',
    settlement_profile_id BIGINT NULL COMMENT '冻结的结算配置ID；影子候选在结算模块上线前为空',
    target_currency CHAR(3) NOT NULL COMMENT '目标结算币种',
    target_currency_exponent TINYINT NOT NULL COMMENT '目标结算币种ISO小数位',
    settlement_eligible_date DATE NOT NULL COMMENT '最早可认领业务日期',
    candidate_status VARCHAR(24) NOT NULL DEFAULT 'READY'
        COMMENT 'READY、REPLAY_HOLD、SUPERSEDED、CLAIMED、POSTED、MANUAL_REVIEW、CANCELLED',
    shadow_mode TINYINT NOT NULL DEFAULT 1 COMMENT '1=影子候选，任何结算扫描必须排除；0=可进入真实结算',
    settlement_batch_no VARCHAR(19) NULL COMMENT '当前独占本候选的批次号',
    claimed_time DATETIME(3) NULL COMMENT '最近成功认领时间',
    posted_time DATETIME(3) NULL COMMENT '资金入账完成时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '认领和释放CAS版本',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_candidate_no (candidate_no),
    UNIQUE KEY uk_settlement_candidate_source (source_type, source_business_id, source_revision),
    KEY idx_settlement_candidate_pick
        (merchant_id, target_currency, shadow_mode, candidate_status, settlement_eligible_date, id),
    KEY idx_settlement_candidate_batch (settlement_batch_no, candidate_status, id),
    KEY idx_settlement_candidate_source_transaction
        (source_transaction_id, source_transaction_date_time, source_revision, id),
    CONSTRAINT chk_settlement_candidate_value CHECK (
        source_revision >= 1 AND target_currency_exponent BETWEEN 0 AND 8
        AND shadow_mode IN (0, 1)
        AND ((shadow_mode = 1 AND settlement_profile_id IS NULL)
             OR (shadow_mode = 0 AND settlement_profile_id IS NOT NULL))
    ),
    CONSTRAINT chk_settlement_candidate_state CHECK (
        source_type IN ('CLEARING_REVISION', 'RESERVE_RELEASE', 'ADJUSTMENT')
        AND candidate_status IN (
            'READY', 'REPLAY_HOLD', 'SUPERSEDED', 'CLAIMED', 'POSTED', 'MANUAL_REVIEW', 'CANCELLED'
        )
        AND ((candidate_status IN ('READY', 'REPLAY_HOLD', 'SUPERSEDED') AND settlement_batch_no IS NULL)
             OR (candidate_status NOT IN ('READY', 'REPLAY_HOLD', 'SUPERSEDED')
                 AND settlement_batch_no IS NOT NULL))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='清分修订级结算候选；来源唯一加状态/version CAS防止跨批重复归属';

CREATE TABLE settlement_candidate_dependency (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    candidate_id BIGINT NOT NULL COMMENT '当前退款、冲正或后续动作候选ID',
    depends_on_candidate_id BIGINT NOT NULL COMMENT '必须先入账或同批先处理的源候选ID',
    dependency_type VARCHAR(32) NOT NULL
        COMMENT 'SOURCE_FINANCIAL_POSTED、RESERVE_HOLD_POSTED、SOURCE_FEE_RESULT_POSTED',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_candidate_dependency
        (candidate_id, depends_on_candidate_id, dependency_type),
    KEY idx_settlement_dependency_source (depends_on_candidate_id, candidate_id, id),
    CONSTRAINT chk_settlement_candidate_dependency CHECK (
        candidate_id <> depends_on_candidate_id
        AND dependency_type IN (
            'SOURCE_FINANCIAL_POSTED', 'RESERVE_HOLD_POSTED', 'SOURCE_FEE_RESULT_POSTED'
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='结算候选不可变依赖图；依赖须已POSTED或与当前候选同批按拓扑顺序处理';

CREATE TABLE settlement_batch_candidate (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    batch_candidate_no VARCHAR(64) NOT NULL COMMENT '批次候选关系业务号',
    settlement_batch_no VARCHAR(19) NOT NULL COMMENT '结算批次号',
    candidate_id BIGINT NOT NULL COMMENT '结算候选ID',
    source_type VARCHAR(24) NOT NULL COMMENT '候选来源类型快照',
    source_business_id VARCHAR(64) NOT NULL COMMENT '候选来源业务ID快照',
    source_revision INT NOT NULL COMMENT '候选来源修订号快照',
    relation_status VARCHAR(24) NOT NULL DEFAULT 'CLAIMED'
        COMMENT 'CLAIMED、RELEASED、POSTED、MANUAL_REVIEW',
    claimed_time DATETIME(3) NOT NULL COMMENT '本批认领时间',
    released_time DATETIME(3) NULL COMMENT '入账前取消并释放候选的时间',
    posted_time DATETIME(3) NULL COMMENT '候选随批次入账时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '关系状态CAS版本',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_batch_candidate_no (batch_candidate_no),
    UNIQUE KEY uk_settlement_batch_candidate_pair (settlement_batch_no, candidate_id),
    KEY idx_settlement_batch_candidate_id (candidate_id, relation_status, id),
    KEY idx_settlement_batch_candidate_source (source_type, source_business_id, source_revision, id),
    CONSTRAINT chk_settlement_batch_candidate_value CHECK (
        source_revision >= 1
        AND relation_status IN ('CLAIMED', 'RELEASED', 'POSTED', 'MANUAL_REVIEW')
        AND (relation_status <> 'RELEASED' OR released_time IS NOT NULL)
        AND (relation_status <> 'POSTED' OR posted_time IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='批次认领候选的不可删除审计关系；取消后保留RELEASED历史，候选可被新批次重新认领';

CREATE TABLE settlement_batch_rate (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    settlement_batch_no VARCHAR(19) NOT NULL COMMENT '所属结算批次号',
    source_currency CHAR(3) NOT NULL COMMENT '源币种',
    target_currency CHAR(3) NOT NULL COMMENT '目标结算币种',
    rate_type VARCHAR(24) NOT NULL DEFAULT 'SETTLEMENT' COMMENT 'SETTLEMENT、RESERVE_RELEASE',
    direct_rate DECIMAL(24,12) NOT NULL COMMENT '1单位源币种兑换的目标币种数量',
    source_currency_exponent TINYINT NOT NULL COMMENT '源币种ISO小数位',
    target_currency_exponent TINYINT NOT NULL COMMENT '目标币种ISO小数位',
    rate_source VARCHAR(64) NOT NULL COMMENT '汇率来源；同币种为SYSTEM_IDENTITY',
    quote_id VARCHAR(128) NULL COMMENT '外部报价或内部业务汇率记录ID',
    source_quote_direction VARCHAR(8) NOT NULL DEFAULT 'DIRECT' COMMENT '原报价DIRECT或INVERSE',
    effective_time DATETIME(3) NOT NULL COMMENT '汇率生效时间',
    locked_time DATETIME(3) NOT NULL COMMENT '批次锁定时间',
    locked_by VARCHAR(128) NOT NULL COMMENT '锁定服务实例或操作人',
    rate_status VARCHAR(16) NOT NULL DEFAULT 'LOCKED' COMMENT 'LOCKED；插入后不可更新',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_batch_currency_rate
        (settlement_batch_no, source_currency, target_currency, rate_type),
    KEY idx_settlement_rate_quote (rate_source, quote_id, effective_time, id),
    CONSTRAINT chk_settlement_batch_rate_value CHECK (
        direct_rate > 0
        AND source_currency_exponent BETWEEN 0 AND 8
        AND target_currency_exponent BETWEEN 0 AND 8
        AND source_quote_direction IN ('DIRECT', 'INVERSE')
        AND rate_type IN ('SETTLEMENT', 'RESERVE_RELEASE')
        AND rate_status = 'LOCKED'
        AND ((source_currency <> target_currency)
             OR (direct_rate = 1 AND rate_source = 'SYSTEM_IDENTITY'))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='结算批次不可变汇率矩阵；同一批次同一币种对和rate type仅一条';

CREATE TABLE settlement_result_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    settlement_result_item_no VARCHAR(64) NOT NULL COMMENT '结算结果明细号',
    settlement_batch_no VARCHAR(19) NOT NULL COMMENT '所属结算批次号',
    candidate_id BIGINT NULL COMMENT '来源结算候选ID；批次级NET_SETTLEMENT为空',
    result_line_no INT NOT NULL COMMENT '候选或批次内稳定结果行号，从1递增',
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号',
    settlement_account_id BIGINT NOT NULL COMMENT '目标资金账户ID',
    source_detail_type VARCHAR(24) NOT NULL
        COMMENT 'TRANSACTION_CLEARING、RESERVE_CLEARING、BATCH_LIMIT、BATCH_NET',
    source_detail_no VARCHAR(64) NULL COMMENT '来源清分明细号；批次限额调整可为空',
    reversal_of_result_item_id BIGINT NULL COMMENT '冲正时引用的原结算结果项ID',
    source_transaction_id VARCHAR(64) NULL COMMENT '来源动作交易号',
    source_transaction_date_time DATETIME(3) NULL COMMENT '来源季度路由时间',
    fee_group_no VARCHAR(64) NULL COMMENT '费用组号；非费用为空',
    result_item_type VARCHAR(32) NOT NULL
        COMMENT 'PRINCIPAL、FEE_COMPONENT、FEE_GROUP_FINAL、RESERVE_HOLD、RESERVE_RETURN、RESERVE_RELEASE、ADJUSTMENT、REVERSAL、NET_SETTLEMENT',
    result_role VARCHAR(24) NOT NULL
        COMMENT 'TRACE、FINANCIAL_COMPONENT、LEDGER_POSTING',
    payment_type VARCHAR(32) NULL COMMENT '支付类型快照',
    payment_method VARCHAR(32) NULL COMMENT '支付方式快照',
    transaction_type VARCHAR(32) NULL COMMENT '交易动作类型',
    fee_category VARCHAR(32) NULL COMMENT '费用类别；非费用为空',
    direction VARCHAR(8) NOT NULL COMMENT '商户视角CREDIT或DEBIT',
    source_amount DECIMAL(24,8) NOT NULL COMMENT '来源原币种非负金额',
    source_currency CHAR(3) NOT NULL COMMENT '来源原币种',
    source_currency_exponent TINYINT NOT NULL COMMENT '来源币种ISO小数位',
    settlement_batch_rate_id BIGINT NOT NULL COMMENT '本行使用的批次汇率ID；同币种也引用恒等行',
    unrounded_target_amount DECIMAL(48,20) NOT NULL COMMENT '原金额乘12位汇率后的目标币种未舍入中间值',
    target_amount DECIMAL(24,8) NOT NULL COMMENT '按目标币种舍入后的非负金额',
    target_currency CHAR(3) NOT NULL COMMENT '目标结算币种',
    target_currency_exponent TINYINT NOT NULL COMMENT '目标币种ISO小数位',
    applied_limit VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE、MINIMUM、MAXIMUM',
    minimum_target_amount DECIMAL(48,20) NULL COMMENT '按批次汇率转换的最低费目标币种未舍入值',
    maximum_target_amount DECIMAL(48,20) NULL COMMENT '按批次汇率转换的最高费目标币种未舍入值',
    rounding_mode VARCHAR(16) NOT NULL COMMENT '目标金额舍入模式',
    formula_snapshot VARCHAR(1000) NOT NULL COMMENT '批次换汇及费用限额公式快照',
    ledger_idempotency_key VARCHAR(128) NULL COMMENT '对应资金流水幂等键；非最终入账行可为空',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_result_item_no (settlement_result_item_no),
    UNIQUE KEY uk_settlement_result_candidate_line (settlement_batch_no, candidate_id, result_line_no),
    UNIQUE KEY uk_settlement_result_ledger_idempotency (ledger_idempotency_key),
    KEY idx_settlement_result_source (source_detail_type, source_detail_no, id),
    KEY idx_settlement_result_reversal (reversal_of_result_item_id, id),
    KEY idx_settlement_result_group (settlement_batch_no, fee_group_no, result_item_type, id),
    KEY idx_settlement_result_rate (settlement_batch_rate_id, id),
    CONSTRAINT chk_settlement_result_item_value CHECK (
        result_line_no >= 1 AND source_amount >= 0 AND unrounded_target_amount >= 0 AND target_amount >= 0
        AND source_currency_exponent BETWEEN 0 AND 8 AND target_currency_exponent BETWEEN 0 AND 8
        AND direction IN ('CREDIT', 'DEBIT')
        AND applied_limit IN ('NONE', 'MINIMUM', 'MAXIMUM')
        AND rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'DOWN')
        AND source_detail_type IN ('TRANSACTION_CLEARING', 'RESERVE_CLEARING', 'BATCH_LIMIT', 'BATCH_NET')
        AND result_item_type IN (
            'PRINCIPAL', 'FEE_COMPONENT', 'FEE_GROUP_FINAL', 'RESERVE_HOLD',
            'RESERVE_RETURN', 'RESERVE_RELEASE', 'ADJUSTMENT', 'REVERSAL', 'NET_SETTLEMENT'
        )
        AND result_role IN ('TRACE', 'FINANCIAL_COMPONENT', 'LEDGER_POSTING')
        AND ((result_role = 'LEDGER_POSTING' AND result_item_type = 'NET_SETTLEMENT'
              AND candidate_id IS NULL AND ledger_idempotency_key IS NOT NULL)
             OR (result_role <> 'LEDGER_POSTING' AND result_item_type <> 'NET_SETTLEMENT'
                 AND candidate_id IS NOT NULL AND ledger_idempotency_key IS NULL))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='结算不可变结果明细；记录原币种、统一批次汇率、限额命中和目标币种结果';

CREATE TABLE settlement_result_summary (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    settlement_batch_no VARCHAR(19) NOT NULL COMMENT '所属结算批次号',
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号',
    payment_type VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '支付类型汇总维度',
    payment_method VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '支付方式汇总维度',
    transaction_type VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '交易类型汇总维度',
    result_item_type VARCHAR(32) NOT NULL COMMENT '本金、费用或保证金结果类型',
    fee_category VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '费用类别汇总维度',
    direction VARCHAR(8) NOT NULL COMMENT '商户视角CREDIT或DEBIT',
    source_currency CHAR(3) NOT NULL COMMENT '来源原币种',
    target_currency CHAR(3) NOT NULL COMMENT '目标结算币种',
    transaction_count BIGINT NOT NULL COMMENT '去重候选或交易动作笔数',
    source_amount DECIMAL(24,8) NOT NULL COMMENT '同原币种金额合计',
    target_amount DECIMAL(24,8) NOT NULL COMMENT '目标结算币种金额合计',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_result_summary_dim (
        settlement_batch_no, merchant_id, payment_type, payment_method, transaction_type,
        result_item_type, fee_category, direction, source_currency, target_currency
    ),
    CONSTRAINT chk_settlement_result_summary_value CHECK (
        transaction_count >= 0 AND source_amount >= 0 AND target_amount >= 0
        AND direction IN ('CREDIT', 'DEBIT')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='仅从FINANCIAL_COMPONENT结果行按支付类型方式、交易类型、费用类别和原币种形成的批次统计';

-- 现有保证金基础表兼容改造：保证金负债币种不再要求等于结算出款币种。
ALTER TABLE merchant_reserve_item
    DROP CONSTRAINT chk_reserve_amount,
    MODIFY COLUMN account_id BIGINT NOT NULL
        COMMENT '所属商户结算资金账户ID；仅表示归属，账户币种可以不同于reserve_currency',
    CHANGE COLUMN currency reserve_currency CHAR(3) NOT NULL
        COMMENT '保证金负债币种，等于原支付标签币种',
    MODIFY COLUMN reserve_status VARCHAR(24) NOT NULL DEFAULT 'HELD'
        COMMENT 'HELD、PARTIALLY_RETURNED、RETURNED、RELEASABLE、RELEASED、FROZEN、DEDUCTED',
    ADD COLUMN returned_amount DECIMAL(24,8) NOT NULL DEFAULT 0
        COMMENT '累计退款返还保证金金额' AFTER retained_amount,
    ADD COLUMN remaining_amount DECIMAL(24,8) NULL
        COMMENT '剩余保证金负债金额；回填后收口NOT NULL' AFTER released_amount,
    ADD COLUMN source_reserve_clearing_detail_no VARCHAR(64) NULL
        COMMENT '来源HOLD保证金清分明细号；存量回填后收口' AFTER source_business_no,
    ADD COLUMN last_settlement_batch_no VARCHAR(19) NULL
        COMMENT '最近一次扣留、返还或释放结算批次号' AFTER release_batch_no,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '返还、释放和冻结CAS版本' AFTER last_settlement_batch_no,
    ADD UNIQUE KEY uk_reserve_source_clearing_detail (merchant_id, source_reserve_clearing_detail_no),
    ADD KEY idx_reserve_last_settlement_batch (last_settlement_batch_no, id);

UPDATE merchant_reserve_item
SET remaining_amount = retained_amount - released_amount
WHERE remaining_amount IS NULL;

-- 以下核对必须返回0行后才能收口约束。
SELECT id, reserve_no, retained_amount, returned_amount, released_amount, remaining_amount
FROM merchant_reserve_item
WHERE remaining_amount IS NULL OR remaining_amount < 0
   OR retained_amount <> returned_amount + released_amount + remaining_amount;

ALTER TABLE merchant_reserve_item
    MODIFY COLUMN remaining_amount DECIMAL(24,8) NOT NULL COMMENT '剩余保证金负债金额',
    ADD CONSTRAINT chk_reserve_amount_consistency CHECK (
        retained_amount > 0 AND returned_amount >= 0 AND released_amount >= 0 AND remaining_amount >= 0
        AND retained_amount = returned_amount + released_amount + remaining_amount
    ),
    ADD CONSTRAINT chk_reserve_status CHECK (
        reserve_status IN (
            'HELD', 'PARTIALLY_RETURNED', 'RETURNED', 'RELEASABLE', 'RELEASED', 'FROZEN', 'DEDUCTED'
        )
    );

-- 批次号分配必须由应用在一个主库事务中执行以下算法，不能把示例直接改成定时脚本：
-- 1. INSERT IGNORE INTO settlement_batch_daily_sequence(business_date, current_sequence) VALUES (?, 0)
-- 2. SELECT current_sequence FROM settlement_batch_daily_sequence WHERE business_date = ? FOR UPDATE
-- 3. UPDATE ... SET current_sequence = current_sequence + 1, version = version + 1
-- 4. 格式化 SByyyyMMdd-NNNNNNNN 并 INSERT settlement_batch；同事务提交。
-- 重复 create_request_key 命中唯一键时读取并返回原批次；序号空洞允许，任何已分配序号不得复用。

-- 候选认领固定CAS条件：
-- UPDATE settlement_candidate
-- SET candidate_status='CLAIMED', settlement_batch_no=?, claimed_time=NOW(3), version=version+1
-- WHERE id=? AND candidate_status='READY' AND settlement_batch_no IS NULL AND version=?;
-- CAS成功后在同一事务INSERT settlement_batch_candidate；唯一(batch_no,candidate_id)保证同批重试幂等。
-- 取消只允许尚未写资金流水的批次，并以 batch_no + CLAIMED + version CAS恢复候选为READY。
-- 同一事务把 settlement_batch_candidate.relation_status 更新为RELEASED，保留历史批次认领证据。
-- POSTED后禁止释放候选或改汇率，只能创建引用 original_batch_no 的REVERSAL/ADJUSTMENT批次。
-- settlement_batch_rate和settlement_result_item只提供INSERT/SELECT Mapper，不提供UPDATE/DELETE；
-- 批次进入RATE_LOCKED后，数据库审计发现任何汇率行变更必须P0告警并阻断POSTING。
-- 资金POSTING必须通过DataSourceName.TRANSACTION在primary master执行一个本地事务：先锁merchant_fund_account，
-- 再生成account_sequence、插入唯一ledger、更新reserve投影并CAS batch/candidate；禁止切换到@DS(MASTER) Service。
-- merchant_fund_ledger净额流水通过settlement_batch_no关联全部汇率；多币种批次的rate_snapshot_id保持NULL，
-- 禁止从汇率矩阵任意挑一行填入该单值字段。

-- ============================================================================
-- 14. 存量回填和 merchant_id、label_currency NOT NULL 第二阶段
-- ============================================================================

-- 1. 不在本迁移脚本中把所有历史交易直接标记为PENDING或CLEARED。
-- 2. 使用独立应用任务按 finance_state.id 游标分批回填，不执行无界 UPDATE JOIN：
--    a. 每批从 transaction_finance_state_yyyy0Q 只读取得最多 500 个 merchant_id 或 label_currency 为空的 id；
--    b. 同季度按 transaction_id + transaction_date_time 批量读取 transaction_operation；
--    c. 校验一一对应后回填 merchant_id 和 label_currency，按主键批量 UPDATE，单批提交并记录证据；
--    d. 找不到动作或商户不一致的记录写异常清单，不用任意 merchant_id 填充。
-- 3. 历史终态交易通过 service-clearing dry-run 确认费率版本后再生成清分修订。
-- 4. 无法唯一确定历史费用版本的动作进入MANUAL_REVIEW。
-- 5. 历史处理中禁止使用当前活动费率替代交易时费率。

-- Phase B 门禁：以下计数必须全部为0。
SELECT 'template' AS shard_name, COUNT(*) AS missing_identity_count
FROM transaction_finance_state
WHERE merchant_id IS NULL OR label_currency IS NULL;

SELECT '202603' AS shard_name, COUNT(*) AS missing_identity_count
FROM transaction_finance_state_202603
WHERE merchant_id IS NULL OR label_currency IS NULL;

SELECT '202604' AS shard_name, COUNT(*) AS missing_identity_count
FROM transaction_finance_state_202604
WHERE merchant_id IS NULL OR label_currency IS NULL;

-- 标签币种回填后，以下币种不一致计数必须全部为0；存在历史异币种值时进入人工迁移清单，禁止强行覆盖。
SELECT 'template' AS shard_name, COUNT(*) AS currency_scope_mismatch_count
FROM transaction_finance_state
WHERE label_currency IS NOT NULL
  AND ((platform_fee_currency IS NOT NULL AND platform_fee_currency <> label_currency)
    OR (merchant_receivable_currency IS NOT NULL AND merchant_receivable_currency <> label_currency)
    OR (reserve_currency IS NOT NULL AND reserve_currency <> label_currency)
    OR (net_settlement_currency IS NOT NULL AND net_settlement_currency <> label_currency))
UNION ALL
SELECT '202603', COUNT(*)
FROM transaction_finance_state_202603
WHERE label_currency IS NOT NULL
  AND ((platform_fee_currency IS NOT NULL AND platform_fee_currency <> label_currency)
    OR (merchant_receivable_currency IS NOT NULL AND merchant_receivable_currency <> label_currency)
    OR (reserve_currency IS NOT NULL AND reserve_currency <> label_currency)
    OR (net_settlement_currency IS NOT NULL AND net_settlement_currency <> label_currency))
UNION ALL
SELECT '202604', COUNT(*)
FROM transaction_finance_state_202604
WHERE label_currency IS NOT NULL
  AND ((platform_fee_currency IS NOT NULL AND platform_fee_currency <> label_currency)
    OR (merchant_receivable_currency IS NOT NULL AND merchant_receivable_currency <> label_currency)
    OR (reserve_currency IS NOT NULL AND reserve_currency <> label_currency)
    OR (net_settlement_currency IS NOT NULL AND net_settlement_currency <> label_currency));

-- Phase B 只在上述门禁通过后的独立变更窗口执行；再次评估 MDL 和主从延迟。
ALTER TABLE transaction_finance_state
    MODIFY COLUMN merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号',
    MODIFY COLUMN label_currency CHAR(3) NOT NULL COMMENT '动作标签币种；百分比基数和保证金负债币种';

ALTER TABLE transaction_finance_state_202603
    MODIFY COLUMN merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号',
    MODIFY COLUMN label_currency CHAR(3) NOT NULL COMMENT '动作标签币种；百分比基数和保证金负债币种';

ALTER TABLE transaction_finance_state_202604
    MODIFY COLUMN merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号',
    MODIFY COLUMN label_currency CHAR(3) NOT NULL COMMENT '动作标签币种；百分比基数和保证金负债币种';

-- ============================================================================
-- 15. 执行后核对；发布28表规则和结算固定表前必须全部通过
-- ============================================================================

SELECT table_name, table_rows, auto_increment, table_collation
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'transaction_clearing_detail',
      'transaction_clearing_detail_202603',
      'transaction_clearing_detail_202604',
      'transaction_reserve_clearing_detail',
      'transaction_reserve_clearing_detail_202603',
      'transaction_reserve_clearing_detail_202604',
      'transaction_reserve_clearing_state',
      'transaction_reserve_clearing_state_202603',
      'transaction_reserve_clearing_state_202604',
      'fee_tier_accumulator',
      'clearing_reserve_adjustment',
      'clearing_tier_period_replay',
      'clearing_tier_period_replay_item',
      'settlement_batch_daily_sequence',
      'settlement_candidate',
      'settlement_candidate_dependency',
      'settlement_batch_candidate',
      'settlement_batch',
      'settlement_batch_rate',
      'settlement_result_item',
      'settlement_result_summary',
      'merchant_reserve_item'
  )
ORDER BY table_name;

SELECT table_name, column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN (
      'transaction_clearing_detail',
      'transaction_clearing_detail_202603',
      'transaction_clearing_detail_202604',
      'transaction_reserve_clearing_detail',
      'transaction_reserve_clearing_detail_202603',
      'transaction_reserve_clearing_detail_202604',
      'transaction_reserve_clearing_state',
      'transaction_reserve_clearing_state_202603',
      'transaction_reserve_clearing_state_202604',
      'clearing_reserve_adjustment',
      'clearing_tier_period_replay',
      'clearing_tier_period_replay_item',
      'settlement_batch_daily_sequence',
      'settlement_candidate',
      'settlement_candidate_dependency',
      'settlement_batch_candidate',
      'settlement_batch',
      'settlement_batch_rate',
      'settlement_result_item',
      'settlement_result_summary',
      'merchant_reserve_item'
  )
ORDER BY ordinal_position, table_name;

-- 每组模板及两张活动季度表的 column_count、index_count、check_count 必须分别一致。
SELECT target.table_name,
       (SELECT COUNT(*) FROM information_schema.columns c
        WHERE c.table_schema = DATABASE() AND c.table_name = target.table_name) AS column_count,
       (SELECT COUNT(DISTINCT s.index_name) FROM information_schema.statistics s
        WHERE s.table_schema = DATABASE() AND s.table_name = target.table_name) AS index_count,
       (SELECT COUNT(*) FROM information_schema.table_constraints tc
        WHERE tc.table_schema = DATABASE() AND tc.table_name = target.table_name
          AND tc.constraint_type = 'CHECK') AS check_count
FROM (
    SELECT 'transaction_clearing_detail' AS table_name
    UNION ALL SELECT 'transaction_clearing_detail_202603'
    UNION ALL SELECT 'transaction_clearing_detail_202604'
    UNION ALL SELECT 'transaction_reserve_clearing_detail'
    UNION ALL SELECT 'transaction_reserve_clearing_detail_202603'
    UNION ALL SELECT 'transaction_reserve_clearing_detail_202604'
    UNION ALL SELECT 'transaction_reserve_clearing_state'
    UNION ALL SELECT 'transaction_reserve_clearing_state_202603'
    UNION ALL SELECT 'transaction_reserve_clearing_state_202604'
) target;

-- CHECK 名称必须在当前 schema 内唯一，且状态均为 ENFORCED。
SELECT tc.table_name, tc.constraint_name, tc.enforced
FROM information_schema.table_constraints tc
WHERE tc.table_schema = DATABASE()
  AND (
      tc.table_name LIKE 'transaction_clearing_detail%'
      OR tc.table_name LIKE 'transaction_reserve_clearing_detail%'
      OR tc.table_name LIKE 'transaction_reserve_clearing_state%'
      OR tc.table_name LIKE 'transaction_finance_state%'
      OR tc.table_name LIKE 'transaction_event_outbox%'
      OR tc.table_name LIKE 'settlement_%'
      OR tc.table_name = 'merchant_reserve_item'
  )
  AND tc.constraint_type = 'CHECK'
ORDER BY tc.table_name, tc.constraint_name;

-- 25 -> 28 拓扑发布顺序：
-- 1. 在 service-clearing 尚未部署时，先向其它直连服务发布同时接受完整25/28表的兼容代码。
-- 2. 按 20260825_02 至 20260825_05 拆分草案逐阶段审批并完成只读校验；确认下一待建季度任务包含三张清分表。
-- 3. 生成28表Nacos候选配置和新checksum，在单实例Dry Run校验 actualDataNodes。
-- 4. 分批重启全部 direct-access-services，确认每个实例都加载相同28表规则。
-- 5. 验证 transaction 数据源可访问 fee_tier_accumulator、transaction_idempotency 和结算固定表Single Rule。
-- 6. Broker、监控和HMAC门禁完成后再启动service-clearing；启动即自动处理全部合法终态事件。

-- 安全停止：暂停两个清分消费者组并下线全部service-clearing实例，保留新增字段、表和已经形成的清分事实。
-- 禁止自动 DROP 清分明细、finance state 字段或费用版本字段。
-- 仅无业务数据的失败演练环境可经 DBA 书面批准反向清理：重放项 -> 重放控制/保证金调整 -> 候选
-- -> 季度保证金状态/明细 -> 季度交易清分明细 -> 阶梯累计；任何已形成资金事实的环境禁止按此顺序删除。
