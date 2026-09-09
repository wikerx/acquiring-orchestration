-- 收单交易清分第三阶段迁移包 3/4：28 张季度逻辑表及清分固定表拓扑草案。
-- 状态：仅供评审，禁止未经 DBA 审批直接在 UAT/生产执行。
-- 前置：03 兼容字段已完成并核验；先建模板，再建所有已发布季度物理表，最后执行 05 只读核验。
-- 边界：只创建阶梯累计事实表、三张清分季度表、清分人工工作流固定表和影子结算候选表；真实结算、余额和保证金资金化不在本文件范围内。

SET NAMES utf8mb4;

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
-- 13. 清分修订级影子结算候选固定表；现有 transaction_rw.* Single Rule 自动发现
-- ============================================================================

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

-- 仅失败演练环境可在 DBA 书面批准后反向清理：先重放项，再重放控制和保证金调整，
-- 再清理候选、季度保证金状态/明细、季度交易清分明细，最后清理阶梯累计；禁止自动生成 DROP 语句。
