-- 费用配置与商户资金账户第一阶段基础结构。
-- 本脚本仅提供配置、审批、试算、账户及只读明细能力，不接入真实交易计费和资金流转。
-- 在途余额直接从 transaction_operation 成功未结算资金动作实时聚合，不创建独立在途明细表。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS fee_plan (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    plan_code VARCHAR(64) NOT NULL COMMENT '费用方案编码',
    plan_name VARCHAR(128) NOT NULL COMMENT '费用方案名称',
    plan_type VARCHAR(16) NOT NULL COMMENT '方案类型：TEMPLATE、MERCHANT',
    merchant_id VARCHAR(64) NULL COMMENT '商户号；商户方案必填，模板为空',
    source_template_id BIGINT NULL COMMENT '来源模板ID',
    source_template_version_no INT NULL COMMENT '复制时的来源模板版本号',
    origin_type VARCHAR(32) NOT NULL DEFAULT 'INDEPENDENT' COMMENT '来源类型：TEMPLATE、TEMPLATE_CUSTOMIZED、INDEPENDENT',
    current_version_id BIGINT NULL COMMENT '当前生效版本ID',
    current_version_no INT NULL COMMENT '当前生效版本号',
    status VARCHAR(16) NOT NULL DEFAULT 'DISABLED' COMMENT '状态：ENABLED、DISABLED、ARCHIVED',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NOT NULL COMMENT '修改人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识；本业务只归档，不物理删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fee_plan_code_deleted (plan_code, deleted),
    UNIQUE KEY uk_fee_plan_merchant_deleted (plan_type, merchant_id, deleted),
    KEY idx_fee_plan_type_list (plan_type, deleted, update_time, id),
    KEY idx_fee_plan_type_status (plan_type, status, deleted, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='费用模板和商户费用方案主表';

CREATE TABLE IF NOT EXISTS fee_plan_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    plan_id BIGINT NOT NULL COMMENT '费用方案ID',
    version_no INT NOT NULL COMMENT '版本号，提交后即占用且不复用',
    version_status VARCHAR(24) NOT NULL COMMENT '版本状态：DRAFT、PENDING_REVIEW、ACTIVE、REJECTED、SUPERSEDED',
    change_type VARCHAR(32) NOT NULL COMMENT '变更类型：CREATED、UPDATED、TEMPLATE_ASSIGNED、CUSTOMIZED',
    source_template_id BIGINT NULL COMMENT '该版本复制或调整所基于的模板ID',
    source_template_version_no INT NULL COMMENT '该版本复制或调整所基于的模板版本号',
    origin_type VARCHAR(32) NOT NULL DEFAULT 'INDEPENDENT' COMMENT '该版本来源：TEMPLATE、TEMPLATE_CUSTOMIZED、INDEPENDENT',
    reserve_rate DECIMAL(12,8) NOT NULL DEFAULT 0 COMMENT '滚动保证金比例，例如10表示10%',
    reserve_delay_unit CHAR(1) NOT NULL DEFAULT 'D' COMMENT '滚动保证金留存周期单位：T工作日、D自然日',
    reserve_delay_days INT NOT NULL DEFAULT 180 COMMENT '滚动保证金T/D+N留存天数，最小1',
    settlement_currency CHAR(3) NULL COMMENT '商户待生效结算币种快照；模板版本为空',
    initial_delay_unit CHAR(1) NOT NULL DEFAULT 'T' COMMENT '首次和常规结算周期共用单位：T工作日、D自然日',
    initial_delay_days INT NOT NULL DEFAULT 1 COMMENT '首次结算周期天数，最小1',
    regular_delay_days INT NOT NULL DEFAULT 1 COMMENT '常规结算周期天数，最小1',
    settlement_frequency VARCHAR(16) NOT NULL DEFAULT 'DAILY' COMMENT '结算频率：DAILY、WEEKLY、BIWEEKLY、MONTHLY',
    frequency_day INT NULL COMMENT '周结为1至7，月结为1至28；日结为空',
    change_reason VARCHAR(500) NOT NULL COMMENT '变更原因',
    submit_by_id BIGINT NULL COMMENT '提交人账号ID',
    submit_by_name VARCHAR(128) NOT NULL COMMENT '提交人名称快照',
    submit_time DATETIME(3) NOT NULL COMMENT '提交时间',
    review_by_id BIGINT NULL COMMENT '审核人账号ID',
    review_by_name VARCHAR(128) NULL COMMENT '审核人名称快照',
    review_comment VARCHAR(500) NULL COMMENT '审核意见',
    review_time DATETIME(3) NULL COMMENT '审核时间',
    effective_time DATETIME(3) NULL COMMENT '审核通过后的实际生效时间',
    superseded_time DATETIME(3) NULL COMMENT '被新版本替代时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fee_plan_version_deleted (plan_id, version_no, deleted),
    KEY idx_fee_version_review (version_status, deleted, submit_time, id),
    KEY idx_fee_version_history (plan_id, deleted, version_no, id),
    KEY idx_fee_version_effective (plan_id, deleted, effective_time, id),
    CONSTRAINT chk_fee_version_reserve CHECK (reserve_rate BETWEEN 0 AND 100 AND reserve_delay_unit IN ('T', 'D') AND reserve_delay_days >= 1),
    CONSTRAINT chk_fee_version_settlement_cycle CHECK (initial_delay_unit IN ('T', 'D') AND initial_delay_days >= 1 AND regular_delay_days >= 1),
    CONSTRAINT chk_fee_version_frequency CHECK (
        (settlement_frequency = 'DAILY' AND frequency_day IS NULL)
        OR (settlement_frequency IN ('WEEKLY', 'BIWEEKLY') AND frequency_day BETWEEN 1 AND 7)
        OR (settlement_frequency = 'MONTHLY' AND frequency_day BETWEEN 1 AND 28)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='费用方案版本表；草稿可编辑，提交审核后不可变';

CREATE TABLE IF NOT EXISTS fee_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    plan_version_id BIGINT NOT NULL COMMENT '费用方案版本ID',
    rule_group_code VARCHAR(64) NULL COMMENT '逻辑规则分组编码；同一次多选展开共享，历史数据可空',
    fee_category VARCHAR(32) NOT NULL DEFAULT 'TRANSACTION_FEE' COMMENT '费用分类：交易、退款、风控、拒付、结算处理、结算换汇',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    transaction_type VARCHAR(64) NOT NULL COMMENT '交易动作，例如PAYMENT、CAPTURE、REFUND',
    payment_type VARCHAR(64) NOT NULL COMMENT '支付类型，复用acquiring_payment_method字典',
    payment_method VARCHAR(64) NOT NULL DEFAULT 'ALL' COMMENT '具体支付方式或品牌；ALL表示全部',
    risk_service_type VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '风控类型：INTERNAL、EXTERNAL、THREE_DS；非风控为NONE',
    charge_trigger VARCHAR(32) NOT NULL DEFAULT 'NOT_APPLICABLE' COMMENT '收费触发：NO_CHARGE、SUCCESS、SUCCESS_OR_FAILURE、ON_CALL',
    fee_mode VARCHAR(16) NOT NULL DEFAULT 'STANDARD' COMMENT '计费模式：STANDARD、TIER',
    percentage_rate DECIMAL(12,8) NOT NULL DEFAULT 0 COMMENT '百分比费率，例如2.3表示2.3%',
    fixed_amount_usd DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '固定费用，币种固定USD',
    minimum_amount_usd DECIMAL(24,8) NULL COMMENT '最低费用，币种固定USD',
    maximum_amount_usd DECIMAL(24,8) NULL COMMENT '最高费用，币种固定USD',
    tier_metric VARCHAR(16) NULL COMMENT '阶梯指标：COUNT、AMOUNT',
    tier_period VARCHAR(16) NULL COMMENT '阶梯累计周期，本期固定MONTH',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fee_rule_dimension_deleted (plan_version_id, fee_category, risk_service_type, transaction_type, payment_type, payment_method, deleted),
    KEY idx_fee_rule_version (plan_version_id, deleted, sort_no, id),
    CONSTRAINT chk_fee_rule_amount CHECK (
        percentage_rate >= 0 AND fixed_amount_usd >= 0
        AND (minimum_amount_usd IS NULL OR minimum_amount_usd >= 0)
        AND (maximum_amount_usd IS NULL OR maximum_amount_usd >= 0)
        AND (minimum_amount_usd IS NULL OR maximum_amount_usd IS NULL OR maximum_amount_usd >= minimum_amount_usd)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='费用方案版本规则表';

CREATE TABLE IF NOT EXISTS fee_rule_tier (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    fee_rule_id BIGINT NOT NULL COMMENT '费用规则ID',
    lower_bound DECIMAL(24,8) NOT NULL COMMENT '档位下界，包含',
    upper_bound DECIMAL(24,8) NULL COMMENT '档位上界，不包含；最后一档为空',
    percentage_rate DECIMAL(12,8) NOT NULL DEFAULT 0 COMMENT '档位百分比费率',
    fixed_amount_usd DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '档位固定费用USD',
    minimum_amount_usd DECIMAL(24,8) NULL COMMENT '档位最低费用USD',
    maximum_amount_usd DECIMAL(24,8) NULL COMMENT '档位最高费用USD',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fee_tier_lower_deleted (fee_rule_id, lower_bound, deleted),
    KEY idx_fee_tier_rule (fee_rule_id, deleted, sort_no, id),
    CONSTRAINT chk_fee_tier_range CHECK (lower_bound >= 0 AND (upper_bound IS NULL OR upper_bound > lower_bound)),
    CONSTRAINT chk_fee_tier_amount CHECK (
        percentage_rate >= 0 AND fixed_amount_usd >= 0
        AND (minimum_amount_usd IS NULL OR minimum_amount_usd >= 0)
        AND (maximum_amount_usd IS NULL OR maximum_amount_usd >= 0)
        AND (minimum_amount_usd IS NULL OR maximum_amount_usd IS NULL OR maximum_amount_usd >= minimum_amount_usd)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='月累计阶梯费率档位表';

CREATE TABLE IF NOT EXISTS fee_simulation_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    simulation_no VARCHAR(64) NOT NULL COMMENT '试算编号',
    plan_version_id BIGINT NOT NULL COMMENT '试算使用的方案版本ID',
    merchant_id VARCHAR(64) NOT NULL COMMENT '商户号',
    fee_category VARCHAR(32) NOT NULL DEFAULT 'TRANSACTION_FEE' COMMENT '本次试算费用分类',
    transaction_type VARCHAR(64) NOT NULL COMMENT '交易动作',
    payment_type VARCHAR(64) NOT NULL COMMENT '支付类型',
    payment_method VARCHAR(64) NOT NULL COMMENT '支付方式',
    risk_service_type VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '风控类型：INTERNAL、EXTERNAL、THREE_DS；非风控为NONE',
    label_amount DECIMAL(24,8) NOT NULL COMMENT '标签金额',
    label_currency CHAR(3) NOT NULL COMMENT '标签币种',
    label_to_usd_rate DECIMAL(24,12) NOT NULL COMMENT '系统解析的标签币种到USD正向结算汇率',
    label_amount_usd DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '标签金额按试算汇率归一后的USD快照',
    settlement_rate_id BIGINT NULL COMMENT '系统业务汇率记录ID；USD恒等汇率为空',
    settlement_rate_source VARCHAR(64) NOT NULL COMMENT '汇率来源编码；USD恒等汇率为SYSTEM_IDENTITY',
    rate_effective_time DATETIME(3) NOT NULL COMMENT '选用汇率的生效时间',
    rate_valuation_time DATETIME(3) NOT NULL COMMENT '本次试算汇率估值时间',
    monthly_count_before BIGINT NOT NULL DEFAULT 0 COMMENT '本次交易前的月累计笔数',
    monthly_amount_usd_before DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '本次交易前的月累计金额USD',
    matched_rule_id BIGINT NOT NULL COMMENT '匹配规则ID',
    matched_tier_id BIGINT NULL COMMENT '匹配阶梯ID',
    percentage_fee_label DECIMAL(24,8) NOT NULL COMMENT '标签币种百分比费用',
    raw_fee_usd DECIMAL(24,8) NOT NULL COMMENT '应用上下限前的USD费用',
    final_fee_usd DECIMAL(24,8) NOT NULL COMMENT '应用上下限后的USD费用',
    reserve_rate DECIMAL(12,8) NOT NULL DEFAULT 0 COMMENT '试算使用的滚动保证金比例快照',
    reserve_amount_usd DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '本次交易预计滚动保证金USD',
    estimated_net_settlement_usd DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '预计净结算金额USD',
    formula_snapshot VARCHAR(1000) NOT NULL COMMENT '试算公式快照',
    net_settlement_formula_snapshot VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '净结算计算公式快照',
    operator_id BIGINT NULL COMMENT '操作人账号ID',
    operator_name VARCHAR(128) NOT NULL COMMENT '操作人名称快照',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '试算时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fee_simulation_no (simulation_no),
    KEY idx_fee_simulation_create_time (create_time, id),
    KEY idx_fee_simulation_plan_time (plan_version_id, create_time, id),
    KEY idx_fee_simulation_merchant_time (merchant_id, create_time, id),
    KEY idx_fee_simulation_transaction_time (transaction_type, create_time, id),
    KEY idx_fee_simulation_rate (settlement_rate_id, rate_valuation_time),
    CONSTRAINT chk_fee_simulation_risk_type CHECK (risk_service_type IN ('NONE', 'INTERNAL', 'EXTERNAL', 'THREE_DS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='费用试算记录表';

CREATE TABLE IF NOT EXISTS fee_simulation_record_detail (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    simulation_record_id BIGINT NOT NULL COMMENT '费用试算记录ID',
    line_no INT NOT NULL COMMENT '同一试算内的稳定明细顺序',
    item_type VARCHAR(16) NOT NULL COMMENT '明细类型：FEE、RESERVE',
    fee_category VARCHAR(32) NOT NULL COMMENT '费用分类；保证金使用RESERVE',
    risk_service_type VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '风控类型；非风控使用NONE',
    calculation_status VARCHAR(24) NOT NULL COMMENT '计算状态：CALCULATED、NOT_APPLICABLE、NOT_CONFIGURED',
    included_in_fee_total TINYINT NOT NULL DEFAULT 0 COMMENT '是否计入费用合计：0否、1是',
    charge_trigger VARCHAR(32) NOT NULL DEFAULT 'NOT_APPLICABLE' COMMENT '收费触发快照',
    rule_name VARCHAR(128) NULL COMMENT '命中规则名称快照',
    fee_mode VARCHAR(16) NULL COMMENT '命中计费模式快照',
    matched_rule_id BIGINT NULL COMMENT '命中费用规则ID',
    matched_tier_id BIGINT NULL COMMENT '命中费用档位ID',
    percentage_fee_label DECIMAL(24,8) NULL COMMENT '标签币种百分比费用',
    percentage_fee_currency CHAR(3) NULL COMMENT '百分比费用标签币种',
    raw_fee_usd DECIMAL(24,8) NULL COMMENT '应用上下限前USD费用',
    final_fee_usd DECIMAL(24,8) NULL COMMENT '最终USD金额；未计算时为空',
    applied_limit VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '实际应用的上下限',
    formula_snapshot VARCHAR(1000) NULL COMMENT '单项计算公式快照',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fee_simulation_detail_line (simulation_record_id, line_no),
    KEY idx_fee_simulation_detail_rule (matched_rule_id, matched_tier_id),
    CONSTRAINT chk_fee_simulation_detail_type CHECK (item_type IN ('FEE', 'RESERVE')),
    CONSTRAINT chk_fee_simulation_detail_status CHECK (calculation_status IN ('CALCULATED', 'NOT_APPLICABLE', 'NOT_CONFIGURED')),
    CONSTRAINT chk_fee_simulation_detail_total CHECK (included_in_fee_total IN (0, 1)),
    CONSTRAINT chk_fee_simulation_detail_risk CHECK (risk_service_type IN ('NONE', 'INTERNAL', 'EXTERNAL', 'THREE_DS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='费用试算逐项审计快照表';

CREATE TABLE IF NOT EXISTS merchant_fund_account (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    account_no VARCHAR(64) NOT NULL COMMENT '资金账户号',
    merchant_id VARCHAR(64) NOT NULL COMMENT '商户号',
    settlement_currency CHAR(3) NOT NULL COMMENT '结算币种；当前每个商户只允许一个',
    available_balance DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '账户余额，允许为负数',
    account_status VARCHAR(24) NOT NULL DEFAULT 'NORMAL' COMMENT '人工状态：NORMAL、FROZEN、CLOSED；负余额限制由可用余额实时派生',
    account_version BIGINT NOT NULL DEFAULT 0 COMMENT '账户并发版本号',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NOT NULL COMMENT '修改人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fund_account_no_deleted (account_no, deleted),
    UNIQUE KEY uk_fund_account_merchant_currency_deleted (merchant_id, settlement_currency, deleted),
    KEY idx_fund_account_list (deleted, update_time, id),
    KEY idx_fund_account_status (account_status, deleted, update_time, id),
    CONSTRAINT chk_fund_account_status CHECK (account_status IN ('NORMAL', 'FROZEN', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户资金账户表';

CREATE TABLE IF NOT EXISTS merchant_fund_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    ledger_no VARCHAR(64) NOT NULL COMMENT '余额流水号',
    ledger_group_no VARCHAR(64) NULL COMMENT '同一业务产生的关联流水组号',
    account_id BIGINT NOT NULL COMMENT '资金账户ID',
    merchant_id VARCHAR(64) NOT NULL COMMENT '商户号',
    business_type VARCHAR(64) NOT NULL COMMENT '业务类型',
    summary VARCHAR(500) NOT NULL COMMENT '变动摘要',
    business_no VARCHAR(128) NOT NULL COMMENT '关联业务单号',
    transaction_id VARCHAR(64) NULL COMMENT '关联交易号',
    settlement_batch_no VARCHAR(64) NULL COMMENT '关联结算批次号',
    fee_detail_no VARCHAR(64) NULL COMMENT '关联费用明细号',
    currency CHAR(3) NOT NULL COMMENT '币种',
    direction VARCHAR(8) NOT NULL COMMENT '借贷方向：CREDIT增加、DEBIT减少',
    amount DECIMAL(24,8) NOT NULL COMMENT '发生金额，始终为正数',
    balance_before DECIMAL(24,8) NOT NULL COMMENT '操作前金额',
    balance_after DECIMAL(24,8) NOT NULL COMMENT '操作后金额',
    account_sequence BIGINT NOT NULL COMMENT '账户内严格递增流水序号',
    fee_version_id BIGINT NULL COMMENT '费用版本ID',
    rate_snapshot_id BIGINT NULL COMMENT '汇率快照ID',
    operation_mode VARCHAR(16) NOT NULL COMMENT '操作模式：AUTO、MANUAL',
    operator_id BIGINT NULL COMMENT '操作人账号ID',
    operator_name VARCHAR(128) NOT NULL COMMENT '操作人名称快照',
    reviewer_id BIGINT NULL COMMENT '审核人账号ID',
    reviewer_name VARCHAR(128) NULL COMMENT '审核人名称快照',
    operation_reason VARCHAR(500) NULL COMMENT '操作原因',
    review_comment VARCHAR(500) NULL COMMENT '审核意见',
    business_time DATETIME(3) NOT NULL COMMENT '业务发生时间',
    submit_time DATETIME(3) NULL COMMENT '人工提交时间',
    review_time DATETIME(3) NULL COMMENT '审核时间',
    posted_time DATETIME(3) NOT NULL COMMENT '入账时间',
    request_id VARCHAR(64) NULL COMMENT '请求号',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '资金幂等键',
    trace_id VARCHAR(64) NULL COMMENT '链路追踪号',
    reversal_of_ledger_id BIGINT NULL COMMENT '被冲正流水ID',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fund_ledger_no (ledger_no),
    UNIQUE KEY uk_fund_ledger_idempotency (idempotency_key),
    UNIQUE KEY uk_fund_ledger_account_sequence (account_id, account_sequence),
    KEY idx_fund_ledger_account_time (account_id, merchant_id, posted_time, id),
    KEY idx_fund_ledger_merchant_time (merchant_id, posted_time, id),
    KEY idx_fund_ledger_posted_time (posted_time, id),
    KEY idx_fund_ledger_business_time (business_type, posted_time, id),
    KEY idx_fund_ledger_business (business_type, business_no),
    KEY idx_fund_ledger_batch (settlement_batch_no),
    KEY idx_fund_ledger_reversal (reversal_of_ledger_id),
    CONSTRAINT chk_fund_ledger_balance CHECK (
        amount > 0 AND direction IN ('CREDIT', 'DEBIT')
        AND ((direction = 'CREDIT' AND balance_after = balance_before + amount)
            OR (direction = 'DEBIT' AND balance_after = balance_before - amount))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户可用余额不可变流水表';

CREATE TABLE IF NOT EXISTS merchant_reserve_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    reserve_no VARCHAR(64) NOT NULL COMMENT '保证金明细号',
    account_id BIGINT NOT NULL COMMENT '资金账户ID',
    merchant_id VARCHAR(64) NOT NULL COMMENT '商户号',
    source_transaction_id VARCHAR(64) NULL COMMENT '来源交易号',
    source_business_no VARCHAR(128) NOT NULL COMMENT '来源业务单号',
    currency CHAR(3) NOT NULL COMMENT '保证金币种，等于结算币种',
    retained_amount DECIMAL(24,8) NOT NULL COMMENT '留存金额',
    released_amount DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '累计释放金额',
    reserve_status VARCHAR(24) NOT NULL DEFAULT 'HELD' COMMENT '状态：HELD、RELEASABLE、RELEASED、FROZEN、DEDUCTED',
    expected_release_date DATE NULL COMMENT '预计释放日期；第一阶段允许为空',
    release_batch_no VARCHAR(64) NULL COMMENT '释放批次号',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reserve_no (reserve_no),
    UNIQUE KEY uk_reserve_merchant_source_business (merchant_id, source_business_no),
    KEY idx_reserve_account_status_release (account_id, merchant_id, reserve_status, expected_release_date, id),
    KEY idx_reserve_status_release (reserve_status, expected_release_date, account_id, id),
    KEY idx_reserve_source (source_transaction_id, source_business_no),
    CONSTRAINT chk_reserve_amount CHECK (retained_amount > 0 AND released_amount >= 0 AND released_amount <= retained_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户保证金留存和释放明细表';

CREATE TABLE IF NOT EXISTS merchant_fund_recharge (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    recharge_no VARCHAR(64) NOT NULL COMMENT '充值申请号',
    account_id BIGINT NOT NULL COMMENT '资金账户ID',
    merchant_id VARCHAR(64) NOT NULL COMMENT '商户号',
    currency CHAR(3) NOT NULL COMMENT '账户结算币种',
    amount DECIMAL(24,8) NOT NULL COMMENT '充值金额，范围100至100000000',
    recharge_status VARCHAR(24) NOT NULL COMMENT '状态：PENDING_AUDIT、PENDING_RECHECK、POSTED、REJECTED',
    remark VARCHAR(500) NOT NULL COMMENT '充值原因或凭证摘要',
    submit_by_id BIGINT NULL COMMENT '提交人账号ID',
    submit_by_name VARCHAR(128) NOT NULL COMMENT '提交人名称快照',
    submit_login_account VARCHAR(128) NULL COMMENT '提交人登录账号快照，用于admin自审边界审计',
    submit_time DATETIME(3) NOT NULL COMMENT '提交时间',
    audit_by_id BIGINT NULL COMMENT '审核人账号ID',
    audit_by_name VARCHAR(128) NULL COMMENT '审核人名称快照',
    audit_comment VARCHAR(500) NULL COMMENT '审核意见',
    audit_time DATETIME(3) NULL COMMENT '审核时间',
    recheck_by_id BIGINT NULL COMMENT '复核人账号ID',
    recheck_by_name VARCHAR(128) NULL COMMENT '复核人名称快照',
    recheck_comment VARCHAR(500) NULL COMMENT '复核意见',
    recheck_time DATETIME(3) NULL COMMENT '复核时间',
    reject_by_id BIGINT NULL COMMENT '驳回人账号ID',
    reject_by_name VARCHAR(128) NULL COMMENT '驳回人名称快照',
    reject_comment VARCHAR(500) NULL COMMENT '驳回原因',
    reject_time DATETIME(3) NULL COMMENT '驳回时间',
    request_id VARCHAR(64) NOT NULL COMMENT '客户端唯一请求号',
    ledger_no VARCHAR(64) NULL COMMENT '最终入账余额流水号',
    posted_time DATETIME(3) NULL COMMENT '最终入账时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fund_recharge_no (recharge_no),
    UNIQUE KEY uk_fund_recharge_request (request_id),
    UNIQUE KEY uk_fund_recharge_ledger (ledger_no),
    KEY idx_fund_recharge_list (deleted, create_time, id),
    KEY idx_fund_recharge_status_time (recharge_status, deleted, create_time, id),
    KEY idx_fund_recharge_merchant_time (merchant_id, deleted, create_time, id),
    KEY idx_fund_recharge_account_time (account_id, deleted, create_time, id),
    CONSTRAINT chk_fund_recharge_amount CHECK (amount BETWEEN 100 AND 100000000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户资金账户充值申请和审批表';

CREATE TABLE IF NOT EXISTS merchant_fund_deduction (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    deduction_no VARCHAR(64) NOT NULL COMMENT '账户扣减申请号',
    account_id BIGINT NOT NULL COMMENT '资金账户ID',
    merchant_id VARCHAR(64) NOT NULL COMMENT '商户号',
    currency CHAR(3) NOT NULL COMMENT '账户结算币种',
    amount DECIMAL(24,8) NOT NULL COMMENT '扣减金额，必须大于0且不超过100000000',
    deduction_category VARCHAR(32) NOT NULL COMMENT '扣减类型：ACCOUNT_CORRECTION、EXTRA_FEE、PENALTY、OTHER',
    deduction_status VARCHAR(24) NOT NULL COMMENT '状态：PENDING_AUDIT、PENDING_RECHECK、POSTED、REJECTED',
    reason VARCHAR(500) NOT NULL COMMENT '商户可见的完整扣减说明',
    submit_by_id BIGINT NULL COMMENT '提交人账号ID',
    submit_by_name VARCHAR(128) NOT NULL COMMENT '提交人名称快照',
    submit_login_account VARCHAR(128) NULL COMMENT '提交人登录账号快照，用于admin自审边界审计',
    submit_time DATETIME(3) NOT NULL COMMENT '提交时间',
    audit_by_id BIGINT NULL COMMENT '审核人账号ID',
    audit_by_name VARCHAR(128) NULL COMMENT '审核人名称快照',
    audit_comment VARCHAR(500) NULL COMMENT '审核意见',
    audit_time DATETIME(3) NULL COMMENT '审核时间',
    recheck_by_id BIGINT NULL COMMENT '复核人账号ID',
    recheck_by_name VARCHAR(128) NULL COMMENT '复核人名称快照',
    recheck_comment VARCHAR(500) NULL COMMENT '复核意见',
    recheck_time DATETIME(3) NULL COMMENT '复核时间',
    reject_by_id BIGINT NULL COMMENT '驳回人账号ID',
    reject_by_name VARCHAR(128) NULL COMMENT '驳回人名称快照',
    reject_comment VARCHAR(500) NULL COMMENT '驳回原因',
    reject_time DATETIME(3) NULL COMMENT '驳回时间',
    request_id VARCHAR(64) NOT NULL COMMENT '客户端唯一请求号',
    ledger_no VARCHAR(64) NULL COMMENT '最终扣减余额流水号',
    posted_time DATETIME(3) NULL COMMENT '最终入账时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fund_deduction_no (deduction_no),
    UNIQUE KEY uk_fund_deduction_request (request_id),
    UNIQUE KEY uk_fund_deduction_ledger (ledger_no),
    KEY idx_fund_deduction_list (deleted, create_time, id),
    KEY idx_fund_deduction_status_time (deduction_status, deleted, create_time, id),
    KEY idx_fund_deduction_category_time (deduction_category, deleted, create_time, id),
    KEY idx_fund_deduction_merchant_time (merchant_id, deleted, create_time, id),
    KEY idx_fund_deduction_account_time (account_id, deleted, create_time, id),
    CONSTRAINT chk_fund_deduction_amount CHECK (amount > 0 AND amount <= 100000000),
    CONSTRAINT chk_fund_deduction_category CHECK (
        deduction_category IN ('ACCOUNT_CORRECTION', 'EXTRA_FEE', 'PENALTY', 'OTHER')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户资金账户扣减申请和审批表';

CREATE TABLE IF NOT EXISTS settlement_calendar_year (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    calendar_year INT NOT NULL COMMENT '自然年',
    region_code VARCHAR(32) NOT NULL DEFAULT 'CN_MAINLAND' COMMENT '地区编码，中国大陆全局日历',
    time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '系统日历时区',
    year_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '年度状态：DRAFT、ACTIVE',
    total_days INT NOT NULL COMMENT '年度自然日总数',
    confirmed_by VARCHAR(128) NULL COMMENT '确认人名称快照',
    confirmed_time DATETIME(3) NULL COMMENT '确认时间',
    create_by VARCHAR(128) NOT NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(128) NOT NULL COMMENT '修改人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_calendar_year (calendar_year, region_code, deleted),
    KEY idx_settlement_calendar_status (year_status, calendar_year, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全局中国大陆结算日历年度表';

CREATE TABLE IF NOT EXISTS settlement_holiday_calendar (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    calendar_year_id BIGINT NOT NULL COMMENT '日历年度ID',
    calendar_date DATE NOT NULL COMMENT '自然日期',
    day_of_week TINYINT NOT NULL COMMENT '星期一至星期日对应1至7',
    day_type VARCHAR(16) NOT NULL COMMENT '日期类型：WORKDAY、HOLIDAY',
    holiday_name VARCHAR(128) NULL COMMENT '法定节假日或休息日名称',
    statutory_holiday TINYINT NOT NULL DEFAULT 0 COMMENT '是否法定节假日',
    adjusted_workday TINYINT NOT NULL DEFAULT 0 COMMENT '是否调休工作日',
    data_source VARCHAR(32) NOT NULL COMMENT '来源：SYSTEM_DEFAULT、MANUAL、IMPORT',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_by VARCHAR(128) NOT NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(128) NOT NULL COMMENT '修改人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_calendar_date (calendar_date, deleted),
    KEY idx_settlement_calendar_year_date (calendar_year_id, deleted, calendar_date),
    KEY idx_settlement_calendar_type_date (day_type, calendar_date, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全局中国大陆结算节假日日历明细表';

-- 兼容尚未执行本脚本旧稿的本地环境：账户唯一约束按商户和结算币种预留多币种扩展位。
SET @drop_old_fund_account_uk := (
    SELECT IF(COUNT(*) > 0,
        'ALTER TABLE merchant_fund_account DROP INDEX uk_fund_account_merchant_deleted',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'merchant_fund_account'
      AND index_name = 'uk_fund_account_merchant_deleted'
);
PREPARE stmt FROM @drop_old_fund_account_uk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 账户人工状态只保留 NORMAL、FROZEN、CLOSED；负余额限制由可用余额实时派生。
ALTER TABLE merchant_fund_account
    MODIFY COLUMN account_status VARCHAR(24) NOT NULL DEFAULT 'NORMAL'
        COMMENT '人工状态：NORMAL、FROZEN、CLOSED；负余额限制由可用余额实时派生';

UPDATE merchant_fund_account
SET account_status = 'NORMAL',
    update_time = CURRENT_TIMESTAMP(3)
WHERE account_status = 'NEGATIVE_BALANCE';

-- 保证金余额由留存明细实时汇总，删除旧稿中的冗余账户余额列，避免双事实源。
SET @drop_fund_account_reserve_balance := (
    SELECT IF(COUNT(*) > 0,
        'ALTER TABLE merchant_fund_account DROP COLUMN reserve_balance',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'merchant_fund_account'
      AND column_name = 'reserve_balance'
);
PREPARE stmt FROM @drop_fund_account_reserve_balance;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fund_ledger_posted_time_idx := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE merchant_fund_ledger ADD INDEX idx_fund_ledger_posted_time (posted_time, id)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'merchant_fund_ledger'
      AND index_name = 'idx_fund_ledger_posted_time'
);
PREPARE stmt FROM @add_fund_ledger_posted_time_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fund_ledger_business_time_idx := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE merchant_fund_ledger ADD INDEX idx_fund_ledger_business_time (business_type, posted_time)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'merchant_fund_ledger'
      AND index_name = 'idx_fund_ledger_business_time'
);
PREPARE stmt FROM @add_fund_ledger_business_time_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fund_account_currency_uk := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE merchant_fund_account ADD UNIQUE KEY uk_fund_account_merchant_currency_deleted (merchant_id, settlement_currency, deleted)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'merchant_fund_account'
      AND index_name = 'uk_fund_account_merchant_currency_deleted'
);
PREPARE stmt FROM @add_fund_account_currency_uk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 兼容已执行第一阶段旧稿的环境：补齐模板草稿语义、滚动保证金、费用分类和试算结果字段。
SET @update_fee_version_status_comment := (
    SELECT IF(column_comment = '版本状态：DRAFT、PENDING_REVIEW、ACTIVE、REJECTED、SUPERSEDED',
        'SELECT 1',
        'ALTER TABLE fee_plan_version MODIFY COLUMN version_status VARCHAR(24) NOT NULL COMMENT ''版本状态：DRAFT、PENDING_REVIEW、ACTIVE、REJECTED、SUPERSEDED''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'fee_plan_version'
      AND column_name = 'version_status'
);
PREPARE stmt FROM @update_fee_version_status_comment;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @update_fee_version_table_comment := (
    SELECT IF(table_comment = '费用方案版本表；草稿可编辑，提交审核后不可变',
        'SELECT 1',
        'ALTER TABLE fee_plan_version COMMENT = ''费用方案版本表；草稿可编辑，提交审核后不可变''')
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'fee_plan_version'
);
PREPARE stmt FROM @update_fee_version_table_comment;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fee_version_reserve_rate := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_plan_version ADD COLUMN reserve_rate DECIMAL(12,8) NOT NULL DEFAULT 0 COMMENT ''滚动保证金比例，例如10表示10%'' AFTER origin_type',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fee_plan_version' AND column_name = 'reserve_rate'
);
PREPARE stmt FROM @add_fee_version_reserve_rate;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fee_version_reserve_days := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_plan_version ADD COLUMN reserve_delay_days INT NOT NULL DEFAULT 180 COMMENT ''滚动保证金D+N留存自然日天数'' AFTER reserve_rate',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fee_plan_version' AND column_name = 'reserve_delay_days'
);
PREPARE stmt FROM @add_fee_version_reserve_days;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fee_version_reserve_unit := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_plan_version ADD COLUMN reserve_delay_unit CHAR(1) NOT NULL DEFAULT ''D'' COMMENT ''滚动保证金留存周期单位：T工作日、D自然日'' AFTER reserve_rate',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fee_plan_version' AND column_name = 'reserve_delay_unit'
);
PREPARE stmt FROM @add_fee_version_reserve_unit;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fee_version_settlement_currency := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_plan_version ADD COLUMN settlement_currency CHAR(3) NULL COMMENT ''商户待生效结算币种快照；模板版本为空'' AFTER reserve_delay_days',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fee_plan_version' AND column_name = 'settlement_currency'
);
PREPARE stmt FROM @add_fee_version_settlement_currency;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 旧稿曾将版本结算币种定义为必填；模板版本不持有结算币种，需兼容调整为可空。
SET @allow_template_settlement_currency_null := (
    SELECT IF(COUNT(*) > 0,
        'ALTER TABLE fee_plan_version MODIFY COLUMN settlement_currency CHAR(3) NULL COMMENT ''商户待生效结算币种快照；模板版本为空''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'fee_plan_version'
      AND column_name = 'settlement_currency'
      AND is_nullable = 'NO'
);
PREPARE stmt FROM @allow_template_settlement_currency_null;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fee_rule_group_code := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_rule ADD COLUMN rule_group_code VARCHAR(64) NULL COMMENT ''逻辑规则分组编码；同一次多选展开共享，历史数据可空'' AFTER plan_version_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fee_rule' AND column_name = 'rule_group_code'
);
PREPARE stmt FROM @add_fee_rule_group_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fee_rule_category := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_rule ADD COLUMN fee_category VARCHAR(32) NOT NULL DEFAULT ''TRANSACTION_FEE'' COMMENT ''费用分类'' AFTER plan_version_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fee_rule' AND column_name = 'fee_category'
);
PREPARE stmt FROM @add_fee_rule_category;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fee_rule_risk_service_type := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_rule ADD COLUMN risk_service_type VARCHAR(16) NOT NULL DEFAULT ''NONE'' COMMENT ''风控类型：INTERNAL、EXTERNAL、THREE_DS；非风控为NONE'' AFTER payment_method',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fee_rule' AND column_name = 'risk_service_type'
);
PREPARE stmt FROM @add_fee_rule_risk_service_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fee_rule_charge_trigger := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_rule ADD COLUMN charge_trigger VARCHAR(32) NOT NULL DEFAULT ''NOT_APPLICABLE'' COMMENT ''收费触发：NO_CHARGE、SUCCESS、SUCCESS_OR_FAILURE、ON_CALL'' AFTER risk_service_type',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fee_rule' AND column_name = 'charge_trigger'
);
PREPARE stmt FROM @add_fee_rule_charge_trigger;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE fee_rule
SET fee_category = CASE
    WHEN transaction_type IN ('REFUND', 'CANCEL', 'VOID', 'AUTH_CANCEL') THEN 'REFUND_FEE'
    WHEN transaction_type IN ('CHARGEBACK', 'DISPUTE') THEN 'DISPUTE_FEE'
    WHEN transaction_type LIKE 'RISK%' THEN 'RISK_FEE'
    ELSE COALESCE(NULLIF(fee_category, ''), 'TRANSACTION_FEE')
END
WHERE deleted = 0;

-- 仅当唯一索引维度不一致时重建，避免脚本重复执行时无意义地锁表。
SET @ensure_fee_rule_dimension_uk := (
    SELECT CASE
        WHEN COALESCE(GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ','), '') =
             'plan_version_id,fee_category,risk_service_type,transaction_type,payment_type,payment_method,deleted'
            THEN 'SELECT 1'
        WHEN COUNT(*) = 0
            THEN 'ALTER TABLE fee_rule ADD UNIQUE KEY uk_fee_rule_dimension_deleted (plan_version_id, fee_category, risk_service_type, transaction_type, payment_type, payment_method, deleted)'
        ELSE 'ALTER TABLE fee_rule DROP INDEX uk_fee_rule_dimension_deleted, ADD UNIQUE KEY uk_fee_rule_dimension_deleted (plan_version_id, fee_category, risk_service_type, transaction_type, payment_type, payment_method, deleted)'
    END
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'fee_rule'
      AND index_name = 'uk_fee_rule_dimension_deleted'
);
PREPARE stmt FROM @ensure_fee_rule_dimension_uk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fee_simulation_category := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_simulation_record ADD COLUMN fee_category VARCHAR(32) NOT NULL DEFAULT ''TRANSACTION_FEE'' COMMENT ''试算费用分类'' AFTER merchant_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fee_simulation_record' AND column_name = 'fee_category'
);
PREPARE stmt FROM @add_fee_simulation_category;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fee_simulation_risk_service_type := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_simulation_record ADD COLUMN risk_service_type VARCHAR(16) NOT NULL DEFAULT ''NONE'' COMMENT ''风控类型：INTERNAL、EXTERNAL、THREE_DS；非风控为NONE'' AFTER payment_method',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fee_simulation_record' AND column_name = 'risk_service_type'
);
PREPARE stmt FROM @add_fee_simulation_risk_service_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fee_simulation_reserve := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_simulation_record ADD COLUMN reserve_amount_usd DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT ''预计滚动保证金USD'' AFTER final_fee_usd',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fee_simulation_record' AND column_name = 'reserve_amount_usd'
);
PREPARE stmt FROM @add_fee_simulation_reserve;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fee_simulation_label_amount_usd := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_simulation_record ADD COLUMN label_amount_usd DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT ''标签金额按试算汇率归一后的USD快照'' AFTER label_to_usd_rate',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fee_simulation_record' AND column_name = 'label_amount_usd'
);
PREPARE stmt FROM @add_fee_simulation_label_amount_usd;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fee_simulation_reserve_rate := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_simulation_record ADD COLUMN reserve_rate DECIMAL(12,8) NOT NULL DEFAULT 0 COMMENT ''试算使用的滚动保证金比例快照'' AFTER final_fee_usd',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fee_simulation_record' AND column_name = 'reserve_rate'
);
PREPARE stmt FROM @add_fee_simulation_reserve_rate;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fee_simulation_net := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_simulation_record ADD COLUMN estimated_net_settlement_usd DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT ''预计净结算金额USD'' AFTER reserve_amount_usd',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fee_simulation_record' AND column_name = 'estimated_net_settlement_usd'
);
PREPARE stmt FROM @add_fee_simulation_net;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fee_simulation_net_formula := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fee_simulation_record ADD COLUMN net_settlement_formula_snapshot VARCHAR(1000) NOT NULL DEFAULT '''' COMMENT ''净结算计算公式快照'' AFTER formula_snapshot',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fee_simulation_record' AND column_name = 'net_settlement_formula_snapshot'
);
PREPARE stmt FROM @add_fee_simulation_net_formula;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 费用试算必须绑定商户；旧环境若存在空商户历史记录，本次迁移应失败并由数据负责人先处理。
SET @enforce_fee_simulation_merchant := (
    SELECT IF(COUNT(*) > 0,
        'ALTER TABLE fee_simulation_record MODIFY COLUMN merchant_id VARCHAR(64) NOT NULL COMMENT ''商户号''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'fee_simulation_record'
      AND column_name = 'merchant_id'
      AND is_nullable = 'YES'
);
PREPARE stmt FROM @enforce_fee_simulation_merchant;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

START TRANSACTION;

-- 已有商户只回填当前结算币种的零余额账户，不创建登录账号、角色、MFA 或默认费率。
INSERT INTO merchant_fund_account (
    account_no, merchant_id, settlement_currency, available_balance,
    account_status, account_version, create_by, create_time,
    update_by, update_time, deleted
)
SELECT CONCAT('FA', merchant.merchant_id, UPPER(TRIM(merchant.settlement_currency))),
       merchant.merchant_id, UPPER(TRIM(merchant.settlement_currency)), 0,
       'NORMAL', 0, 'fee-account-migration', CURRENT_TIMESTAMP(3),
       'fee-account-migration', CURRENT_TIMESTAMP(3), 0
FROM base_merchant_info merchant
WHERE merchant.deleted = 0
  AND merchant.settlement_currency IS NOT NULL
  AND TRIM(merchant.settlement_currency) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM merchant_fund_account account
      WHERE account.merchant_id = merchant.merchant_id
        AND account.settlement_currency = UPPER(TRIM(merchant.settlement_currency))
        AND account.deleted = 0
  );

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, 'admin_fund_recharge_v1', '账户充值', 'MENU', '/fund/recharge', 'fund/recharge',
       'fund:recharge:list', 'CreditCard', 1, 1, 0, 43, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_manage'
                    AND parent.deleted = 0
WHERE app.app_code = 'ADMIN' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = app.id AND existing.menu_code = 'admin_fund_recharge_v1' AND existing.deleted = 0
  );

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, 'admin_fund_deduction_v1', '账户扣减', 'MENU', '/fund/deduction', 'fund/deduction',
       'fund:deduction:list', 'RemoveFilled', 1, 1, 0, 44, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_manage'
                    AND parent.deleted = 0
WHERE app.app_code = 'ADMIN' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = app.id AND existing.menu_code = 'admin_fund_deduction_v1' AND existing.deleted = 0
  );

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, 'system_holiday_calendar_v1', '节假日日历', 'MENU',
       '/system/holiday-calendar', 'system/holiday-calendar/index',
       'system:calendar:list', 'Calendar', 1, 1, 0, 20, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'system_manage'
                    AND parent.deleted = 0
WHERE app.app_code = 'ADMIN' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = app.id AND existing.menu_code = 'system_holiday_calendar_v1' AND existing.deleted = 0
  );

-- 兼容旧脚本已创建但组件路径、权限或父节点不完整的菜单记录。
UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id
                AND app.app_code = 'ADMIN'
                AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_manage'
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = '账户充值',
    menu.menu_type = 'MENU',
    menu.route_path = '/fund/recharge',
    menu.component_path = 'fund/recharge',
    menu.permission_code = 'fund:recharge:list',
    menu.icon = 'CreditCard',
    menu.visible = 1,
    menu.keep_alive = 1,
    menu.external_link = 0,
    menu.sort_no = 43,
    menu.status = 1
WHERE menu.menu_code = 'admin_fund_recharge_v1'
  AND menu.deleted = 0;

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id
                AND app.app_code = 'ADMIN'
                AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'system_manage'
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = '节假日日历',
    menu.menu_type = 'MENU',
    menu.route_path = '/system/holiday-calendar',
    menu.component_path = 'system/holiday-calendar',
    menu.permission_code = 'system:calendar:list',
    menu.icon = 'Calendar',
    menu.visible = 1,
    menu.keep_alive = 1,
    menu.external_link = 0,
    menu.sort_no = 20,
    menu.status = 1
WHERE menu.menu_code = 'system_holiday_calendar_v1'
  AND menu.deleted = 0;

-- 管理端菜单使用 app_code 解析应用，避免依赖不同环境的应用主键。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, 0, 'admin_fee_catalog_v1', '费用管理', 'CATALOG', '/fee', NULL,
       NULL, 'PriceTag', 1, 0, 0, 45, 1, 0
FROM sys_app app
WHERE app.app_code = 'ADMIN' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = app.id AND existing.menu_code = 'admin_fee_catalog_v1' AND existing.deleted = 0
  );

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, item.menu_code, item.menu_name, 'MENU', item.route_path, item.component_path,
       item.permission_code, item.icon, 1, 1, 0, item.sort_no, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'admin_fee_catalog_v1'
                    AND parent.deleted = 0
JOIN (
    SELECT 'admin_fee_template_v1' menu_code, '费用模板' menu_name, '/fee/template' route_path,
           'fee/template' component_path, 'fee:template:list' permission_code, 'Tickets' icon, 1 sort_no
    UNION ALL SELECT 'admin_fee_merchant_v1', '商户费率', '/fee/merchant', 'fee/merchant', 'fee:merchant:list', 'Shop', 2
    UNION ALL SELECT 'admin_fee_review_v1', '费率审核', '/fee/review', 'fee/review', 'fee:review:list', 'DocumentChecked', 3
    UNION ALL SELECT 'admin_fee_simulation_v1', '费用试算', '/fee/simulation', 'fee/simulation', 'fee:simulation:use', 'Operation', 4
) item
WHERE app.app_code = 'ADMIN' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = app.id AND existing.menu_code = item.menu_code AND existing.deleted = 0
  );

-- 在途和保证金保留为内部结算明细表，不再向管理端或商户端暴露独立明细入口。
UPDATE sys_menu
SET visible = 0, status = 0
WHERE menu_code IN (
    'admin_fund_pending_list_v1', 'admin_fund_reserve_list_v1',
    'merchant_fund_pending_list_v1', 'merchant_fund_reserve_list_v1'
) AND deleted = 0;

UPDATE sys_permission
SET status = 0
WHERE permission_code IN (
    'fund:pending:list', 'fund:reserve:list',
    'merchant:fund:pending:view', 'merchant:fund:reserve:view'
) AND deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, 'admin_fund_account_v1', '资金账户', 'MENU', '/fund/account', 'fund/account',
       'fund:account:list', 'Wallet', 1, 1, 0, 42, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_manage'
                    AND parent.deleted = 0
WHERE app.app_code = 'ADMIN' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = app.id AND existing.menu_code = 'admin_fund_account_v1' AND existing.deleted = 0
  );

-- 兼容已执行旧版脚本的环境：资金账户归入商户管理，不保留顶级孤立菜单。
UPDATE sys_menu account_menu
JOIN sys_app app ON app.id = account_menu.app_id
                AND app.app_code = 'ADMIN'
                AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_manage'
                    AND parent.deleted = 0
SET account_menu.parent_id = parent.id,
    account_menu.sort_no = 42
WHERE account_menu.menu_code = 'admin_fund_account_v1'
  AND account_menu.deleted = 0;

-- 全局余额明细作为商户管理下独立菜单，供财务按商户、账户和入账时间统一核对。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, 'admin_fund_ledger_all_v1', '余额明细', 'MENU',
       '/fund/ledger', 'fund/ledger', 'fund:ledger:all:list', 'NotebookTabs',
       1, 1, 0, 45, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_manage'
                    AND parent.deleted = 0
WHERE app.app_code = 'ADMIN' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = app.id
        AND existing.menu_code = 'admin_fund_ledger_all_v1'
        AND existing.deleted = 0
  );

UPDATE sys_menu ledger_menu
JOIN sys_app app ON app.id = ledger_menu.app_id
                AND app.app_code = 'ADMIN'
                AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_manage'
                    AND parent.deleted = 0
SET ledger_menu.parent_id = parent.id,
    ledger_menu.menu_name = '余额明细',
    ledger_menu.menu_type = 'MENU',
    ledger_menu.route_path = '/fund/ledger',
    ledger_menu.component_path = 'fund/ledger',
    ledger_menu.permission_code = 'fund:ledger:all:list',
    ledger_menu.icon = 'NotebookTabs',
    ledger_menu.visible = 1,
    ledger_menu.keep_alive = 1,
    ledger_menu.external_link = 0,
    ledger_menu.sort_no = 45,
    ledger_menu.status = 1
WHERE ledger_menu.menu_code = 'admin_fund_ledger_all_v1'
  AND ledger_menu.deleted = 0;

-- 管理端按钮菜单与接口权限一一对应，用于页面按钮和角色授权细分。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, item.menu_code, item.menu_name, 'BUTTON', NULL, NULL,
       item.permission_code, NULL, 0, 0, 0, item.sort_no, 1, 0
FROM sys_app app
JOIN (
    SELECT 'admin_fee_template_v1' parent_code, 'admin_fee_template_detail_v1' menu_code, '费用模板详情' menu_name, 'fee:template:detail' permission_code, 101 sort_no
    UNION ALL SELECT 'admin_fee_template_v1', 'admin_fee_template_add_v1', '新增费用模板', 'fee:template:add', 102
    UNION ALL SELECT 'admin_fee_template_v1', 'admin_fee_template_edit_v1', '编辑费用模板草稿', 'fee:template:edit', 103
    UNION ALL SELECT 'admin_fee_template_v1', 'admin_fee_template_submit_v1', '提交费用模板复核', 'fee:template:submit', 104
    UNION ALL SELECT 'admin_fee_template_v1', 'admin_fee_template_withdraw_v1', '撤回费用模板复核', 'fee:template:withdraw', 105
    UNION ALL SELECT 'admin_fee_template_v1', 'admin_fee_template_status_v1', '费用模板启停', 'fee:template:status', 106
    UNION ALL SELECT 'admin_fee_template_v1', 'admin_fee_template_archive_v1', '费用模板归档', 'fee:template:archive', 107
    UNION ALL SELECT 'admin_fee_template_v1', 'admin_fee_template_export_v1', '费用模板导出', 'fee:template:export', 108
    UNION ALL SELECT 'admin_fee_merchant_v1', 'admin_fee_merchant_detail_v1', '商户费率详情', 'fee:merchant:detail', 101
    UNION ALL SELECT 'admin_fee_merchant_v1', 'admin_fee_merchant_template_assign_v1', '商户选择费率模板', 'fee:merchant:template:assign', 102
    UNION ALL SELECT 'admin_fee_merchant_v1', 'admin_fee_merchant_configure_v1', '商户独立或调整费率', 'fee:merchant:configure', 103
    UNION ALL SELECT 'admin_fee_merchant_v1', 'admin_fee_merchant_export_v1', '商户费率导出', 'fee:merchant:export', 104
    UNION ALL SELECT 'admin_fee_review_v1', 'admin_fee_review_approve_v1', '费率审核通过', 'fee:review:approve', 101
    UNION ALL SELECT 'admin_fee_review_v1', 'admin_fee_review_reject_v1', '费率审核拒绝', 'fee:review:reject', 102
    UNION ALL SELECT 'admin_fee_review_v1', 'admin_fee_review_export_v1', '费率审核导出', 'fee:review:export', 103
    UNION ALL SELECT 'admin_fee_simulation_v1', 'admin_fee_simulation_record_list_v1', '试算记录查询', 'fee:simulation:record:list', 101
    UNION ALL SELECT 'admin_fee_simulation_v1', 'admin_fee_simulation_record_export_v1', '试算记录导出', 'fee:simulation:record:export', 102
    UNION ALL SELECT 'admin_fund_account_v1', 'admin_fund_account_detail_v1', '资金账户详情', 'fund:account:detail', 101
    UNION ALL SELECT 'admin_fund_account_v1', 'admin_fund_ledger_list_v1', '余额流水查询', 'fund:ledger:list', 102
    UNION ALL SELECT 'admin_fund_account_v1', 'admin_fund_ledger_export_v1', '余额明细导出', 'fund:ledger:export', 103
    UNION ALL SELECT 'admin_fund_account_v1', 'admin_fund_account_export_v1', '资金账户导出', 'fund:account:export', 104
    UNION ALL SELECT 'admin_fund_account_v1', 'admin_fund_account_freeze_v1', '冻结资金账户', 'fund:account:freeze', 105
    UNION ALL SELECT 'admin_fund_account_v1', 'admin_fund_account_unfreeze_v1', '解冻资金账户', 'fund:account:unfreeze', 106
    UNION ALL SELECT 'admin_fund_account_v1', 'admin_fund_account_close_v1', '关闭资金账户', 'fund:account:close', 107
    UNION ALL SELECT 'admin_fund_account_v1', 'admin_fund_account_reopen_v1', '恢复资金账户', 'fund:account:reopen', 108
    UNION ALL SELECT 'admin_fund_ledger_all_v1', 'admin_fund_ledger_all_export_v1', '全局余额明细导出', 'fund:ledger:all:export', 101
    UNION ALL SELECT 'admin_fund_recharge_v1', 'admin_fund_recharge_add_v1', '提交充值申请', 'fund:recharge:add', 101
    UNION ALL SELECT 'admin_fund_recharge_v1', 'admin_fund_recharge_audit_v1', '审核充值申请', 'fund:recharge:audit', 102
    UNION ALL SELECT 'admin_fund_recharge_v1', 'admin_fund_recharge_recheck_v1', '复核充值入账', 'fund:recharge:recheck', 103
    UNION ALL SELECT 'admin_fund_recharge_v1', 'admin_fund_recharge_reject_v1', '驳回充值申请', 'fund:recharge:reject', 104
    UNION ALL SELECT 'admin_fund_recharge_v1', 'admin_fund_recharge_export_v1', '充值申请导出', 'fund:recharge:export', 105
    UNION ALL SELECT 'admin_fund_deduction_v1', 'admin_fund_deduction_detail_v1', '账户扣减详情', 'fund:deduction:detail', 101
    UNION ALL SELECT 'admin_fund_deduction_v1', 'admin_fund_deduction_add_v1', '提交扣减申请', 'fund:deduction:add', 102
    UNION ALL SELECT 'admin_fund_deduction_v1', 'admin_fund_deduction_audit_v1', '审核扣减申请', 'fund:deduction:audit', 103
    UNION ALL SELECT 'admin_fund_deduction_v1', 'admin_fund_deduction_recheck_v1', '复核扣减入账', 'fund:deduction:recheck', 104
    UNION ALL SELECT 'admin_fund_deduction_v1', 'admin_fund_deduction_reject_v1', '驳回扣减申请', 'fund:deduction:reject', 105
    UNION ALL SELECT 'admin_fund_deduction_v1', 'admin_fund_deduction_export_v1', '扣减申请导出', 'fund:deduction:export', 106
    UNION ALL SELECT 'system_holiday_calendar_v1', 'system_holiday_calendar_initialize_v1', '初始化年度日历', 'system:calendar:initialize', 101
    UNION ALL SELECT 'system_holiday_calendar_v1', 'system_holiday_calendar_edit_v1', '维护节假日日历', 'system:calendar:edit', 102
    UNION ALL SELECT 'system_holiday_calendar_v1', 'system_holiday_calendar_confirm_v1', '确认年度日历', 'system:calendar:confirm', 103
    UNION ALL SELECT 'system_holiday_calendar_v1', 'system_holiday_calendar_export_v1', '导出年度日历', 'system:calendar:export', 104
) item
JOIN sys_menu parent ON parent.app_id = app.id AND parent.menu_code = item.parent_code AND parent.deleted = 0
WHERE app.app_code = 'ADMIN' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = app.id AND existing.menu_code = item.menu_code AND existing.deleted = 0
  );

-- 同步旧环境中已存在的模板按钮名称与排序。
UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
SET menu.menu_name = CASE menu.menu_code
        WHEN 'admin_fee_template_edit_v1' THEN '编辑费用模板草稿'
        WHEN 'admin_fee_template_submit_v1' THEN '提交费用模板复核'
        WHEN 'admin_fee_template_withdraw_v1' THEN '撤回费用模板复核'
        ELSE menu.menu_name
    END,
    menu.sort_no = CASE menu.menu_code
        WHEN 'admin_fee_template_detail_v1' THEN 101
        WHEN 'admin_fee_template_add_v1' THEN 102
        WHEN 'admin_fee_template_edit_v1' THEN 103
        WHEN 'admin_fee_template_submit_v1' THEN 104
        WHEN 'admin_fee_template_withdraw_v1' THEN 105
        WHEN 'admin_fee_template_status_v1' THEN 106
        WHEN 'admin_fee_template_archive_v1' THEN 107
        WHEN 'admin_fee_template_export_v1' THEN 108
        ELSE menu.sort_no
    END
WHERE menu.menu_code IN (
    'admin_fee_template_detail_v1', 'admin_fee_template_add_v1',
    'admin_fee_template_edit_v1', 'admin_fee_template_submit_v1',
    'admin_fee_template_withdraw_v1', 'admin_fee_template_status_v1',
    'admin_fee_template_archive_v1', 'admin_fee_template_export_v1'
)
  AND menu.deleted = 0;

-- 商户端财务目录只展示当前商户已生效费率和账户数据，不暴露平台模板库。
INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, 0, 'merchant_finance_catalog_v1', '财务管理', 'CATALOG', '/finance', NULL,
       NULL, 'WalletCards', 1, 0, 0, 60, 1, 0
FROM sys_app app
WHERE app.app_code = 'MERCHANT' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = app.id AND existing.menu_code = 'merchant_finance_catalog_v1' AND existing.deleted = 0
  );

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, item.menu_code, item.menu_name, 'MENU', item.route_path, item.component_path,
       item.permission_code, item.icon, 1, 1, 0, item.sort_no, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_finance_catalog_v1'
                    AND parent.deleted = 0
JOIN (
    SELECT 'merchant_current_fee_v1' menu_code, '当前费率' menu_name, '/finance/fee' route_path,
           'finance/fee' component_path, 'merchant:fee:view' permission_code, 'PriceTag' icon, 1 sort_no
    UNION ALL SELECT 'merchant_fund_account_v1', '资金账户', '/finance/account', 'finance/account',
                     'merchant:fund:account:view', 'Wallet', 2
) item
WHERE app.app_code = 'MERCHANT' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = app.id AND existing.menu_code = item.menu_code AND existing.deleted = 0
  );

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, item.menu_code, item.menu_name, 'BUTTON', NULL, NULL,
       item.permission_code, NULL, 0, 0, 0, item.sort_no, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_fund_account_v1'
                    AND parent.deleted = 0
JOIN (
    SELECT 'merchant_fund_ledger_list_v1' menu_code, '余额流水查询' menu_name,
           'merchant:fund:ledger:view' permission_code, 101 sort_no
    UNION ALL SELECT 'merchant_fund_ledger_export_v1', '余额明细导出', 'merchant:fund:ledger:export', 102
) item
WHERE app.app_code = 'MERCHANT' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = app.id AND existing.menu_code = item.menu_code AND existing.deleted = 0
  );

-- API 权限与 Controller 权限注解保持一致。
INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT app.id, menu.id, item.permission_code, item.permission_name, item.permission_type,
       item.resource_method, item.resource_path, item.description, 1, 0
FROM sys_app app
JOIN (
    SELECT 'admin_fee_template_v1' menu_code, 'fee:template:list' permission_code, '费用模板查询' permission_name, 'MENU' permission_type, 'POST' resource_method, '/admin/fees/templates/search' resource_path, '分页查询费用模板' description
    UNION ALL SELECT 'admin_fee_template_detail_v1', 'fee:template:detail', '费用模板详情', 'BUTTON', 'GET', '/admin/fees/templates/*', '查询费用模板版本历史'
    UNION ALL SELECT 'admin_fee_template_add_v1', 'fee:template:add', '费用模板新增', 'BUTTON', 'POST', '/admin/fees/templates', '新建费用模板并保存v1草稿'
    UNION ALL SELECT 'admin_fee_template_edit_v1', 'fee:template:edit', '费用模板草稿编辑', 'BUTTON', 'POST', '/admin/fees/templates/*/versions', '创建新版本草稿或编辑未提交草稿'
    UNION ALL SELECT 'admin_fee_template_submit_v1', 'fee:template:submit', '费用模板提交复核', 'BUTTON', 'PUT', '/admin/fees/versions/*/submit', '提交模板草稿并进入待复核状态'
    UNION ALL SELECT 'admin_fee_template_withdraw_v1', 'fee:template:withdraw', '费用模板撤回复核', 'BUTTON', 'PUT', '/admin/fees/versions/*/withdraw', '原提交人撤回待复核模板并恢复为草稿'
    UNION ALL SELECT 'admin_fee_template_status_v1', 'fee:template:status', '费用模板启停', 'BUTTON', 'PUT', '/admin/fees/templates/*/status', '启停模板后续选择能力'
    UNION ALL SELECT 'admin_fee_template_archive_v1', 'fee:template:archive', '费用模板归档', 'BUTTON', 'PUT', '/admin/fees/templates/*/archive', '归档模板并保留历史'
    UNION ALL SELECT 'admin_fee_template_export_v1', 'fee:template:export', '费用模板导出', 'BUTTON', 'POST', '/admin/fees/templates/export', '按当前筛选条件导出费用模板'
    UNION ALL SELECT 'admin_fee_merchant_v1', 'fee:merchant:list', '商户费率查询', 'MENU', 'POST', '/admin/fees/merchants/search', '分页查询商户费率状态'
    UNION ALL SELECT 'admin_fee_merchant_detail_v1', 'fee:merchant:detail', '商户费率详情', 'BUTTON', 'GET', '/admin/fees/merchants/*', '查询商户费率版本历史'
    UNION ALL SELECT 'admin_fee_merchant_template_assign_v1', 'fee:merchant:template:assign', '商户选择费率模板', 'BUTTON', 'POST', '/admin/fees/merchants/*/template-versions', '复制当前模板版本并提交审核'
    UNION ALL SELECT 'admin_fee_merchant_configure_v1', 'fee:merchant:configure', '商户独立或调整费率', 'BUTTON', 'POST', '/admin/fees/merchants/*/custom-versions', '提交独立配置或基于模板调整版本'
    UNION ALL SELECT 'admin_fee_merchant_export_v1', 'fee:merchant:export', '商户费率导出', 'BUTTON', 'POST', '/admin/fees/merchants/export', '按当前筛选条件导出商户费率'
    UNION ALL SELECT 'admin_fee_review_v1', 'fee:review:list', '费率审核查询', 'MENU', 'POST', '/admin/fees/reviews/search', '分页查询待审核费率版本'
    UNION ALL SELECT 'admin_fee_review_approve_v1', 'fee:review:approve', '费率审核通过', 'BUTTON', 'PUT', '/admin/fees/versions/*/approve', '审核通过并即时生效'
    UNION ALL SELECT 'admin_fee_review_reject_v1', 'fee:review:reject', '费率审核拒绝', 'BUTTON', 'PUT', '/admin/fees/versions/*/reject', '审核拒绝并保留意见'
    UNION ALL SELECT 'admin_fee_review_export_v1', 'fee:review:export', '费率审核导出', 'BUTTON', 'POST', '/admin/fees/reviews/export', '按当前筛选条件导出费率审核记录'
    UNION ALL SELECT 'admin_fee_simulation_v1', 'fee:simulation:use', '费用试算', 'MENU', 'POST', '/admin/fees/simulations', '使用系统当前有效结算汇率试算'
    UNION ALL SELECT 'admin_fee_simulation_record_list_v1', 'fee:simulation:record:list', '试算记录查询', 'BUTTON', 'POST', '/admin/fees/simulation-records/search', '分页查询费用试算记录'
    UNION ALL SELECT 'admin_fee_simulation_record_export_v1', 'fee:simulation:record:export', '试算记录导出', 'BUTTON', 'POST', '/admin/fees/simulation-records/export', '按当前筛选条件导出费用试算记录'
    UNION ALL SELECT 'admin_fund_account_v1', 'fund:account:list', '资金账户查询', 'MENU', 'POST', '/admin/fund-accounts/search', '分页查询资金账户'
    UNION ALL SELECT 'admin_fund_account_detail_v1', 'fund:account:detail', '资金账户详情', 'BUTTON', 'GET', '/admin/fund-accounts/*', '查询资金账户余额摘要'
    UNION ALL SELECT 'admin_fund_ledger_list_v1', 'fund:ledger:list', '余额流水查询', 'BUTTON', 'POST', '/admin/fund-accounts/*/ledgers/search', '查询不可变余额流水'
    UNION ALL SELECT 'admin_fund_ledger_export_v1', 'fund:ledger:export', '余额明细导出', 'BUTTON', 'POST', '/admin/fund-accounts/*/ledgers/export', '按筛选条件导出全部余额明细'
    UNION ALL SELECT 'admin_fund_account_export_v1', 'fund:account:export', '资金账户导出', 'BUTTON', 'POST', '/admin/fund-accounts/export', '按当前筛选条件导出资金账户'
    UNION ALL SELECT 'admin_fund_account_freeze_v1', 'fund:account:freeze', '冻结资金账户', 'BUTTON', 'PUT', '/admin/fund-accounts/*/freeze', '冻结账户并保留入账和结算能力'
    UNION ALL SELECT 'admin_fund_account_unfreeze_v1', 'fund:account:unfreeze', '解冻资金账户', 'BUTTON', 'PUT', '/admin/fund-accounts/*/unfreeze', '将冻结账户恢复为正常状态'
    UNION ALL SELECT 'admin_fund_account_close_v1', 'fund:account:close', '关闭资金账户', 'BUTTON', 'PUT', '/admin/fund-accounts/*/close', '关闭账户并禁止结算、提现和主动资金转出'
    UNION ALL SELECT 'admin_fund_account_reopen_v1', 'fund:account:reopen', '恢复资金账户', 'BUTTON', 'PUT', '/admin/fund-accounts/*/reopen', '将关闭账户恢复为正常状态'
    UNION ALL SELECT 'admin_fund_ledger_all_v1', 'fund:ledger:all:list', '全局余额明细查询', 'MENU', 'POST', '/admin/fund-accounts/ledgers/search', '查询所有商户和账户的不可变余额流水'
    UNION ALL SELECT 'admin_fund_ledger_all_export_v1', 'fund:ledger:all:export', '全局余额明细导出', 'BUTTON', 'POST', '/admin/fund-accounts/ledgers/export', '按筛选条件导出所有商户余额明细'
    UNION ALL SELECT 'admin_fund_recharge_v1', 'fund:recharge:list', '充值申请查询', 'MENU', 'POST', '/admin/fund-accounts/recharges/search', '分页查询充值申请'
    UNION ALL SELECT 'admin_fund_recharge_add_v1', 'fund:recharge:add', '提交充值申请', 'BUTTON', 'POST', '/admin/fund-accounts/recharges', '提交待审核充值申请'
    UNION ALL SELECT 'admin_fund_recharge_audit_v1', 'fund:recharge:audit', '审核充值申请', 'BUTTON', 'POST', '/admin/fund-accounts/recharges/*/audit', '审核充值申请'
    UNION ALL SELECT 'admin_fund_recharge_recheck_v1', 'fund:recharge:recheck', '复核充值入账', 'BUTTON', 'POST', '/admin/fund-accounts/recharges/*/recheck', '复核通过并原子入账'
    UNION ALL SELECT 'admin_fund_recharge_reject_v1', 'fund:recharge:reject', '驳回充值申请', 'BUTTON', 'POST', '/admin/fund-accounts/recharges/*/reject', '驳回充值申请'
    UNION ALL SELECT 'admin_fund_recharge_export_v1', 'fund:recharge:export', '充值申请导出', 'BUTTON', 'POST', '/admin/fund-accounts/recharges/export', '按筛选条件导出全部充值申请'
    UNION ALL SELECT 'admin_fund_deduction_v1', 'fund:deduction:list', '账户扣减查询', 'MENU', 'POST', '/admin/fund-accounts/deductions/search', '分页查询账户扣减申请'
    UNION ALL SELECT 'admin_fund_deduction_detail_v1', 'fund:deduction:detail', '账户扣减详情', 'BUTTON', 'GET', '/admin/fund-accounts/deductions/*', '查询账户扣减完整审批快照'
    UNION ALL SELECT 'admin_fund_deduction_add_v1', 'fund:deduction:add', '提交扣减申请', 'BUTTON', 'POST', '/admin/fund-accounts/deductions', '提交待审核账户扣减申请'
    UNION ALL SELECT 'admin_fund_deduction_audit_v1', 'fund:deduction:audit', '审核扣减申请', 'BUTTON', 'POST', '/admin/fund-accounts/deductions/*/audit', '审核账户扣减申请'
    UNION ALL SELECT 'admin_fund_deduction_recheck_v1', 'fund:deduction:recheck', '复核扣减入账', 'BUTTON', 'POST', '/admin/fund-accounts/deductions/*/recheck', '复核通过并原子扣减可用余额'
    UNION ALL SELECT 'admin_fund_deduction_reject_v1', 'fund:deduction:reject', '驳回扣减申请', 'BUTTON', 'POST', '/admin/fund-accounts/deductions/*/reject', '驳回账户扣减申请'
    UNION ALL SELECT 'admin_fund_deduction_export_v1', 'fund:deduction:export', '扣减申请导出', 'BUTTON', 'POST', '/admin/fund-accounts/deductions/export', '按筛选条件导出全部账户扣减申请'
    UNION ALL SELECT 'system_holiday_calendar_v1', 'system:calendar:list', '节假日日历查询', 'MENU', 'GET', '/admin/system/holiday-calendar', '查询中国大陆结算日历月视图'
    UNION ALL SELECT 'system_holiday_calendar_initialize_v1', 'system:calendar:initialize', '初始化年度日历', 'BUTTON', 'POST', '/admin/system/holiday-calendar/years', '初始化年度基础日历'
    UNION ALL SELECT 'system_holiday_calendar_edit_v1', 'system:calendar:edit', '维护节假日日历', 'BUTTON', 'PUT', '/admin/system/holiday-calendar/days', '批量维护或导入日期'
    UNION ALL SELECT 'system_holiday_calendar_confirm_v1', 'system:calendar:confirm', '确认年度日历', 'BUTTON', 'PUT', '/admin/system/holiday-calendar/years/confirm', '确认年度日历供T+N使用'
    UNION ALL SELECT 'system_holiday_calendar_export_v1', 'system:calendar:export', '导出年度日历', 'BUTTON', 'POST', '/admin/system/holiday-calendar/export', '导出年度完整日历'
) item
JOIN sys_menu menu ON menu.app_id = app.id AND menu.menu_code = item.menu_code AND menu.deleted = 0
WHERE app.app_code = 'ADMIN' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing
      WHERE existing.app_id = app.id AND existing.permission_code = item.permission_code AND existing.deleted = 0
  );

-- 同步旧环境中已存在的模板新增和编辑权限说明。
UPDATE sys_permission permission
JOIN sys_app app ON app.id = permission.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
SET permission.permission_name = CASE permission.permission_code
        WHEN 'fee:template:add' THEN '费用模板新增'
        WHEN 'fee:template:edit' THEN '费用模板草稿编辑'
        ELSE permission.permission_name
    END,
    permission.description = CASE permission.permission_code
        WHEN 'fee:template:add' THEN '新建费用模板并保存v1草稿'
        WHEN 'fee:template:edit' THEN '创建新版本草稿或编辑未提交草稿'
        ELSE permission.description
    END
WHERE permission.permission_code IN ('fee:template:add', 'fee:template:edit')
  AND permission.deleted = 0;

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT app.id, menu.id, item.permission_code, item.permission_name, item.permission_type,
       item.resource_method, item.resource_path, item.description, 1, 0
FROM sys_app app
JOIN (
    SELECT 'merchant_current_fee_v1' menu_code, 'merchant:fee:view' permission_code, '当前费率查询' permission_name, 'MENU' permission_type, 'GET' resource_method, '/merchant/fees/current' resource_path, '查询当前商户已生效费率' description
    UNION ALL SELECT 'merchant_fund_account_v1', 'merchant:fund:account:view', '资金账户查询', 'MENU', 'GET', '/merchant/fund-account', '查询当前商户资金账户摘要'
    UNION ALL SELECT 'merchant_fund_ledger_list_v1', 'merchant:fund:ledger:view', '余额流水查询', 'BUTTON', 'POST', '/merchant/fund-account/ledgers/search', '查询当前商户余额流水'
    UNION ALL SELECT 'merchant_fund_ledger_export_v1', 'merchant:fund:ledger:export', '余额明细导出', 'BUTTON', 'POST', '/merchant/fund-account/ledgers/export', '按筛选条件导出当前商户全部余额明细'
) item
JOIN sys_menu menu ON menu.app_id = app.id AND menu.menu_code = item.menu_code AND menu.deleted = 0
WHERE app.app_code = 'MERCHANT' AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing
      WHERE existing.app_id = app.id AND existing.permission_code = item.permission_code AND existing.deleted = 0
  );

-- 本地系统角色默认授权；后续仍可在权限中心按角色收敛。
INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT role.app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = role.app_id
                  AND (menu.menu_code LIKE 'admin_fee_%'
                       OR menu.menu_code LIKE 'admin_fund_%'
                       OR menu.menu_code LIKE 'system_holiday_calendar_%')
                  AND menu.deleted = 0
WHERE role.role_code IN ('ADMIN_OPERATOR', 'SUPER_ADMIN') AND role.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT role.app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = role.app_id
                              AND (permission.permission_code LIKE 'fee:%'
                                   OR permission.permission_code LIKE 'fund:%'
                                   OR permission.permission_code LIKE 'system:calendar:%')
                              AND permission.deleted = 0
WHERE role.role_code IN ('ADMIN_OPERATOR', 'SUPER_ADMIN') AND role.deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT role.app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = role.app_id
                  AND (menu.menu_code LIKE 'merchant_finance_%'
                       OR menu.menu_code LIKE 'merchant_fund_%')
                  AND menu.deleted = 0
WHERE role.role_type = 'SYSTEM'
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
  AND role.deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT role.app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = role.app_id
                  AND menu.menu_code IN ('merchant_current_fee_v1', 'merchant_fund_account_v1')
                  AND menu.deleted = 0
WHERE role.role_type = 'SYSTEM'
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
  AND role.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT role.app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = role.app_id
                              AND (permission.permission_code = 'merchant:fee:view'
                                   OR permission.permission_code LIKE 'merchant:fund:%')
                              AND permission.deleted = 0
WHERE role.role_type = 'SYSTEM'
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
  AND role.deleted = 0;

-- 已有商户补齐财务菜单和权限的商户授权交集。
INSERT IGNORE INTO sys_merchant_menu_grant (
    merchant_id, app_id, menu_id, grant_source, status, created_at, updated_at, deleted
)
SELECT merchant.merchant_id, menu.app_id, menu.id, 'SYSTEM', 1,
       CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM base_merchant_info merchant
JOIN sys_app app ON app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = app.id
                  AND (menu.menu_code LIKE 'merchant_finance_%'
                       OR menu.menu_code LIKE 'merchant_fund_%'
                       OR menu.menu_code = 'merchant_current_fee_v1')
                  AND menu.deleted = 0
WHERE merchant.deleted = 0;

INSERT IGNORE INTO sys_merchant_permission_grant (
    merchant_id, app_id, permission_id, grant_source, status, created_at, updated_at, deleted
)
SELECT merchant.merchant_id, permission.app_id, permission.id, 'SYSTEM', 1,
       CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM base_merchant_info merchant
JOIN sys_app app ON app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = app.id
                              AND (permission.permission_code = 'merchant:fee:view'
                                   OR permission.permission_code LIKE 'merchant:fund:%')
                              AND permission.deleted = 0
WHERE merchant.deleted = 0;

-- 余额流水查询字典；页面只展示字典标签，接口仍使用稳定业务编码。
INSERT INTO sys_dict_type (
    dict_name, dict_type, biz_domain, system_builtin, editable, status, deleted
)
SELECT item.dict_name, item.dict_type, 'fund', 1, 0, 1, 0
FROM (
    SELECT '余额流水业务类型' dict_name, 'fund_ledger_business_type' dict_type
    UNION ALL SELECT '余额流水方向', 'fund_direction'
    UNION ALL SELECT '账户扣减类型', 'fund_deduction_category'
) item
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type existing
    WHERE existing.dict_type = item.dict_type AND existing.deleted = 0
);

INSERT INTO sys_dict_data (
    dict_type, dict_label, dict_value, locale, dict_sort, list_class,
    is_default, status, deleted
)
SELECT item.dict_type, item.dict_label, item.dict_value, item.locale, item.dict_sort,
       item.list_class, item.is_default, 1, 0
FROM (
    SELECT 'fund_direction' dict_type, '收入' dict_label, 'CREDIT' dict_value,
           'zh-CN' locale, 1 dict_sort, 'success' list_class, 1 is_default
    UNION ALL SELECT 'fund_direction', '支出', 'DEBIT', 'zh-CN', 2, 'danger', 0
    UNION ALL SELECT 'fund_direction', 'Credit', 'CREDIT', 'en-US', 1, 'success', 1
    UNION ALL SELECT 'fund_direction', 'Debit', 'DEBIT', 'en-US', 2, 'danger', 0
    UNION ALL SELECT 'fund_ledger_business_type', '账户充值', 'RECHARGE', 'zh-CN', 1, 'success', 1
    UNION ALL SELECT 'fund_ledger_business_type', '交易结算', 'TRANSACTION_SETTLEMENT', 'zh-CN', 2, 'primary', 0
    UNION ALL SELECT 'fund_ledger_business_type', '保证金结算', 'RESERVE_SETTLEMENT', 'zh-CN', 3, 'warning', 0
    UNION ALL SELECT 'fund_ledger_business_type', '提现', 'WITHDRAWAL', 'zh-CN', 4, 'danger', 0
    UNION ALL SELECT 'fund_ledger_business_type', '人工调账', 'MANUAL_ADJUSTMENT', 'zh-CN', 5, 'warning', 0
    UNION ALL SELECT 'fund_ledger_business_type', '流水冲正', 'REVERSAL', 'zh-CN', 6, 'info', 0
    UNION ALL SELECT 'fund_ledger_business_type', '账户扣减', 'BALANCE_DEDUCTION', 'zh-CN', 7, 'danger', 0
    UNION ALL SELECT 'fund_ledger_business_type', 'Recharge', 'RECHARGE', 'en-US', 1, 'success', 1
    UNION ALL SELECT 'fund_ledger_business_type', 'Transaction settlement', 'TRANSACTION_SETTLEMENT', 'en-US', 2, 'primary', 0
    UNION ALL SELECT 'fund_ledger_business_type', 'Reserve settlement', 'RESERVE_SETTLEMENT', 'en-US', 3, 'warning', 0
    UNION ALL SELECT 'fund_ledger_business_type', 'Withdrawal', 'WITHDRAWAL', 'en-US', 4, 'danger', 0
    UNION ALL SELECT 'fund_ledger_business_type', 'Manual adjustment', 'MANUAL_ADJUSTMENT', 'en-US', 5, 'warning', 0
    UNION ALL SELECT 'fund_ledger_business_type', 'Ledger reversal', 'REVERSAL', 'en-US', 6, 'info', 0
    UNION ALL SELECT 'fund_ledger_business_type', 'Balance deduction', 'BALANCE_DEDUCTION', 'en-US', 7, 'danger', 0
    UNION ALL SELECT 'fund_deduction_category', '账务更正', 'ACCOUNT_CORRECTION', 'zh-CN', 1, 'primary', 1
    UNION ALL SELECT 'fund_deduction_category', '额外费用', 'EXTRA_FEE', 'zh-CN', 2, 'warning', 0
    UNION ALL SELECT 'fund_deduction_category', '罚金', 'PENALTY', 'zh-CN', 3, 'danger', 0
    UNION ALL SELECT 'fund_deduction_category', '其他', 'OTHER', 'zh-CN', 4, 'info', 0
    UNION ALL SELECT 'fund_deduction_category', 'Account correction', 'ACCOUNT_CORRECTION', 'en-US', 1, 'primary', 1
    UNION ALL SELECT 'fund_deduction_category', 'Extra fee', 'EXTRA_FEE', 'en-US', 2, 'warning', 0
    UNION ALL SELECT 'fund_deduction_category', 'Penalty', 'PENALTY', 'en-US', 3, 'danger', 0
    UNION ALL SELECT 'fund_deduction_category', 'Other', 'OTHER', 'en-US', 4, 'info', 0
) item
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data existing
    WHERE existing.dict_type = item.dict_type
      AND existing.dict_value = item.dict_value
      AND existing.locale = item.locale
      AND existing.deleted = 0
);

-- 系统内置邮件模板；按模板编码和语言幂等创建，不覆盖已有人工调整。
INSERT INTO msg_email_template (
    template_code, template_name, app_code, scene_code, locale, subject_template,
    content_type, content_template, variable_schema, sensitive_variable_names,
    status, system_builtin, version_no, remark, create_by, update_by, deleted
)
SELECT item.template_code, item.template_name, item.app_code, item.scene_code, item.locale, item.subject_template,
       'HTML', item.content_template, item.variable_schema, JSON_ARRAY(), 1, 1, 1,
       '费用和资金账户第一阶段系统内置模板', 'system', 'system', 0
FROM (
    SELECT 'FEE_CONFIG_PENDING_REVIEW' template_code, '费率配置待审核' template_name, 'ADMIN' app_code, 'FEE_REVIEW' scene_code, 'zh-CN' locale,
           '[${systemName}] 费率配置待审核：${planName}' subject_template,
           '<p>费率配置已提交审核。</p><p>方案：${planName}（${planCode}）</p><p>版本：v${versionNo}</p><p>提交人：${submitterName}</p><p>提交时间：${submitTime}</p><p>变更原因：${changeReason}</p>' content_template,
           '{"systemName":"Vexra Admin","planName":"标准费率","planCode":"FP0001","versionNo":"2","submitterName":"张三","submitTime":"2026-08-18 10:00:00","changeReason":"调整费率"}' variable_schema
    UNION ALL SELECT 'FEE_CONFIG_REJECTED', '费率配置审核拒绝', 'ADMIN', 'FEE_REVIEW', 'zh-CN',
           '[${systemName}] 费率配置审核未通过：${planName}',
           '<p>费率配置审核未通过。</p><p>方案：${planName}（${planCode}）</p><p>版本：v${versionNo}</p><p>审核人：${reviewerName}</p><p>审核时间：${reviewTime}</p><p>审核意见：${reviewComment}</p>',
           '{"systemName":"Vexra Admin","planName":"标准费率","planCode":"FP0001","versionNo":"2","reviewerName":"李四","reviewTime":"2026-08-18 11:00:00","reviewComment":"费率区间需要调整"}'
    UNION ALL SELECT 'FEE_RULE_MISSING', '费率规则缺失', 'ADMIN', 'FEE_EXCEPTION', 'zh-CN',
           '[${systemName}] 商户费率规则缺失：${merchantId}',
           '<p>商户交易未匹配到有效费率规则。</p><p>商户号：${merchantId}</p><p>交易类型：${transactionType}</p><p>支付类型：${paymentType}</p><p>支付方式：${paymentMethod}</p><p>发生时间：${eventTime}</p>',
           '{"systemName":"Vexra Admin","merchantId":"M10000001","transactionType":"PAYMENT","paymentType":"BANK_CARD","paymentMethod":"VISA","eventTime":"2026-08-18 12:00:00"}'
    UNION ALL SELECT 'SETTLEMENT_RATE_MISSING', '结算汇率缺失', 'ADMIN', 'SETTLEMENT_EXCEPTION', 'zh-CN',
           '[${systemName}] 结算批次因汇率缺失暂停：${batchNo}',
           '<p>结算批次缺少指定方向的直接汇率，已暂停处理。</p><p>批次号：${batchNo}</p><p>商户号：${merchantId}</p><p>币种方向：${baseCurrency} → ${quoteCurrency}</p><p>估值时间：${valuationTime}</p>',
           '{"systemName":"Vexra Admin","batchNo":"SET202608180001","merchantId":"M10000001","baseCurrency":"EUR","quoteCurrency":"USD","valuationTime":"2026-08-18 00:00:00"}'
    UNION ALL SELECT 'NEGATIVE_BALANCE_INTERNAL', '商户负余额内部通知', 'ADMIN', 'FUND_ACCOUNT', 'zh-CN',
           '[${systemName}] 商户余额为负：${merchantId}',
           '<p>商户资金账户余额已低于零。</p><p>商户号：${merchantId}</p><p>账户号：${accountNo}</p><p>当前余额：${currency} ${balance}</p><p>发生时间：${eventTime}</p>',
           '{"systemName":"Vexra Admin","merchantId":"M10000001","accountNo":"FA10000001","currency":"USD","balance":"-100.00","eventTime":"2026-08-18 12:00:00"}'
    UNION ALL SELECT 'NEGATIVE_BALANCE_MERCHANT', '商户负余额通知', 'MERCHANT', 'FUND_ACCOUNT', 'zh-CN',
           '[${systemName}] 您的账户余额已低于零',
           '<p>您好，${merchantName}：</p><p>您的资金账户余额为 ${currency} ${balance}，系统已暂停会产生资金流出的主动逆向交易。</p><p>请及时充值或联系平台处理。</p><p>发生时间：${eventTime}</p>',
           '{"systemName":"Vexra Merchant","merchantName":"示例商户","currency":"USD","balance":"-100.00","eventTime":"2026-08-18 12:00:00"}'
    UNION ALL SELECT 'BALANCE_RESTORED', '商户余额恢复通知', 'MERCHANT', 'FUND_ACCOUNT', 'zh-CN',
           '[${systemName}] 您的账户余额已恢复',
           '<p>您好，${merchantName}：</p><p>您的资金账户余额已恢复为 ${currency} ${balance}，负余额导致的逆向交易限制已解除。</p><p>恢复时间：${eventTime}</p>',
           '{"systemName":"Vexra Merchant","merchantName":"示例商户","currency":"USD","balance":"20.00","eventTime":"2026-08-18 12:00:00"}'
    UNION ALL SELECT 'HOLIDAY_CALENDAR_MISSING', '结算节假日日历缺失', 'ADMIN', 'SETTLEMENT_EXCEPTION', 'zh-CN',
           '[${systemName}] T+N结算因节假日日历缺失暂停：${calendarDate}',
           '<p>T+N结算需要的中国大陆节假日日历未确认或缺少日期，相关结算已暂停。</p><p>日期：${calendarDate}</p><p>商户号：${merchantId}</p><p>结算批次：${batchNo}</p><p>请在系统管理的节假日日历中维护并确认对应年度。</p>',
           '{"systemName":"Vexra Admin","calendarDate":"2026-10-01","merchantId":"M10000001","batchNo":"SET202610010001"}'
    UNION ALL SELECT 'FUND_RECHARGE_POSTED', '商户充值入账通知', 'MERCHANT', 'FUND_ACCOUNT', 'zh-CN',
           '[${systemName}] 充值已入账：${rechargeNo}',
           '<p>您好，${merchantName}：</p><p>充值申请已完成复核并计入可用余额。</p><p>充值单号：${rechargeNo}</p><p>入账金额：${currency} ${amount}</p><p>入账时间：${postedTime}</p>',
           '{"systemName":"Vexra Merchant","merchantName":"示例商户","rechargeNo":"RC10000001","currency":"USD","amount":"1000.00","postedTime":"2026-08-18 12:00:00"}'
    UNION ALL SELECT 'FUND_RECHARGE_REJECTED', '商户充值驳回通知', 'MERCHANT', 'FUND_ACCOUNT', 'zh-CN',
           '[${systemName}] 充值申请未通过：${rechargeNo}',
           '<p>您好，${merchantName}：</p><p>充值申请未通过审核或复核。</p><p>充值单号：${rechargeNo}</p><p>金额：${currency} ${amount}</p><p>原因：${rejectComment}</p><p>处理时间：${rejectTime}</p>',
           '{"systemName":"Vexra Merchant","merchantName":"示例商户","rechargeNo":"RC10000001","currency":"USD","amount":"1000.00","rejectComment":"凭证不完整","rejectTime":"2026-08-18 12:00:00"}'
    UNION ALL SELECT 'FEE_CONFIG_PENDING_REVIEW', 'Fee configuration pending review', 'ADMIN', 'FEE_REVIEW', 'en-US',
           '[${systemName}] Fee configuration pending review: ${planName}',
           '<p>A fee configuration has been submitted for review.</p><p>Plan: ${planName} (${planCode})</p><p>Version: v${versionNo}</p><p>Submitted by: ${submitterName}</p><p>Submitted at: ${submitTime}</p><p>Reason: ${changeReason}</p>',
           '{"systemName":"Vexra Admin","planName":"Standard fee plan","planCode":"FP0001","versionNo":"2","submitterName":"Alex","submitTime":"2026-08-18 10:00:00","changeReason":"Fee adjustment"}'
    UNION ALL SELECT 'FEE_CONFIG_REJECTED', 'Fee configuration rejected', 'ADMIN', 'FEE_REVIEW', 'en-US',
           '[${systemName}] Fee configuration rejected: ${planName}',
           '<p>The fee configuration was rejected.</p><p>Plan: ${planName} (${planCode})</p><p>Version: v${versionNo}</p><p>Reviewed by: ${reviewerName}</p><p>Reviewed at: ${reviewTime}</p><p>Review comment: ${reviewComment}</p>',
           '{"systemName":"Vexra Admin","planName":"Standard fee plan","planCode":"FP0001","versionNo":"2","reviewerName":"Taylor","reviewTime":"2026-08-18 11:00:00","reviewComment":"Adjust the fee range"}'
    UNION ALL SELECT 'FEE_RULE_MISSING', 'Fee rule missing', 'ADMIN', 'FEE_EXCEPTION', 'en-US',
           '[${systemName}] Merchant fee rule missing: ${merchantId}',
           '<p>No active fee rule matched the merchant transaction.</p><p>Merchant ID: ${merchantId}</p><p>Transaction type: ${transactionType}</p><p>Payment type: ${paymentType}</p><p>Payment method: ${paymentMethod}</p><p>Event time: ${eventTime}</p>',
           '{"systemName":"Vexra Admin","merchantId":"M10000001","transactionType":"PAYMENT","paymentType":"BANK_CARD","paymentMethod":"VISA","eventTime":"2026-08-18 12:00:00"}'
    UNION ALL SELECT 'SETTLEMENT_RATE_MISSING', 'Settlement rate missing', 'ADMIN', 'SETTLEMENT_EXCEPTION', 'en-US',
           '[${systemName}] Settlement batch paused because a rate is missing: ${batchNo}',
           '<p>The settlement batch is paused because the required direct currency rate is missing.</p><p>Batch: ${batchNo}</p><p>Merchant ID: ${merchantId}</p><p>Currency pair: ${baseCurrency} to ${quoteCurrency}</p><p>Valuation time: ${valuationTime}</p>',
           '{"systemName":"Vexra Admin","batchNo":"SET202608180001","merchantId":"M10000001","baseCurrency":"EUR","quoteCurrency":"USD","valuationTime":"2026-08-18 00:00:00"}'
    UNION ALL SELECT 'NEGATIVE_BALANCE_INTERNAL', 'Merchant negative balance alert', 'ADMIN', 'FUND_ACCOUNT', 'en-US',
           '[${systemName}] Merchant balance is negative: ${merchantId}',
           '<p>The merchant fund account balance is below zero.</p><p>Merchant ID: ${merchantId}</p><p>Account: ${accountNo}</p><p>Current balance: ${currency} ${balance}</p><p>Event time: ${eventTime}</p>',
           '{"systemName":"Vexra Admin","merchantId":"M10000001","accountNo":"FA10000001","currency":"USD","balance":"-100.00","eventTime":"2026-08-18 12:00:00"}'
    UNION ALL SELECT 'NEGATIVE_BALANCE_MERCHANT', 'Negative balance notice', 'MERCHANT', 'FUND_ACCOUNT', 'en-US',
           '[${systemName}] Your account balance is below zero',
           '<p>Hello ${merchantName},</p><p>Your fund account balance is ${currency} ${balance}. Active reverse transactions that would reduce the balance have been paused.</p><p>Please fund the account or contact the platform.</p><p>Event time: ${eventTime}</p>',
           '{"systemName":"Vexra Merchant","merchantName":"Example merchant","currency":"USD","balance":"-100.00","eventTime":"2026-08-18 12:00:00"}'
    UNION ALL SELECT 'BALANCE_RESTORED', 'Balance restored notice', 'MERCHANT', 'FUND_ACCOUNT', 'en-US',
           '[${systemName}] Your account balance has been restored',
           '<p>Hello ${merchantName},</p><p>Your fund account balance is now ${currency} ${balance}. The reverse transaction restriction caused by the negative balance has been removed.</p><p>Restored at: ${eventTime}</p>',
           '{"systemName":"Vexra Merchant","merchantName":"Example merchant","currency":"USD","balance":"20.00","eventTime":"2026-08-18 12:00:00"}'
    UNION ALL SELECT 'HOLIDAY_CALENDAR_MISSING', 'Settlement holiday calendar missing', 'ADMIN', 'SETTLEMENT_EXCEPTION', 'en-US',
           '[${systemName}] T+N settlement paused because calendar data is missing: ${calendarDate}',
           '<p>The confirmed China mainland settlement calendar is missing for the required date.</p><p>Date: ${calendarDate}</p><p>Merchant ID: ${merchantId}</p><p>Batch: ${batchNo}</p><p>Maintain and confirm the year in System Management.</p>',
           '{"systemName":"Vexra Admin","calendarDate":"2026-10-01","merchantId":"M10000001","batchNo":"SET202610010001"}'
    UNION ALL SELECT 'FUND_RECHARGE_POSTED', 'Merchant recharge posted', 'MERCHANT', 'FUND_ACCOUNT', 'en-US',
           '[${systemName}] Recharge posted: ${rechargeNo}',
           '<p>Hello ${merchantName},</p><p>Your recharge was rechecked and posted to the available balance.</p><p>Recharge: ${rechargeNo}</p><p>Amount: ${currency} ${amount}</p><p>Posted at: ${postedTime}</p>',
           '{"systemName":"Vexra Merchant","merchantName":"Example merchant","rechargeNo":"RC10000001","currency":"USD","amount":"1000.00","postedTime":"2026-08-18 12:00:00"}'
    UNION ALL SELECT 'FUND_RECHARGE_REJECTED', 'Merchant recharge rejected', 'MERCHANT', 'FUND_ACCOUNT', 'en-US',
           '[${systemName}] Recharge rejected: ${rechargeNo}',
           '<p>Hello ${merchantName},</p><p>Your recharge did not pass audit or recheck.</p><p>Recharge: ${rechargeNo}</p><p>Amount: ${currency} ${amount}</p><p>Reason: ${rejectComment}</p><p>Processed at: ${rejectTime}</p>',
           '{"systemName":"Vexra Merchant","merchantName":"Example merchant","rechargeNo":"RC10000001","currency":"USD","amount":"1000.00","rejectComment":"Evidence is incomplete","rejectTime":"2026-08-18 12:00:00"}'
) item
WHERE NOT EXISTS (
    SELECT 1 FROM msg_email_template existing
    WHERE existing.template_code = item.template_code AND existing.locale = item.locale AND existing.deleted = 0
);

-- 新环境初始化时直接使用与系统既有邮件一致的蓝白主题。
UPDATE msg_email_template template
SET template.content_template = CONCAT(
        '<div data-template-theme="vexra-blue-white-v1" style="margin:0;padding:32px 16px;background:#F3F7FF;font-family:Arial,sans-serif;color:#0F172A;">',
        '<div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #DBEAFE;border-radius:8px;overflow:hidden;">',
        '<div style="padding:24px 28px;background:#2563EB;color:#FFFFFF;"><div style="font-size:13px;color:#DBEAFE;">',
        CASE WHEN template.app_code = 'MERCHANT' THEN 'Vexra Merchant' ELSE 'Vexra Admin' END,
        '</div><div style="margin-top:6px;font-size:22px;font-weight:700;">', template.template_name,
        '</div></div><div style="padding:28px;line-height:1.7;font-size:14px;">',
        '<div style="padding:18px;background:#F8FAFC;border:1px solid #E2E8F0;border-radius:6px;word-break:break-word;">',
        template.content_template,
        '</div></div><div style="padding:16px 28px;background:#F3F7FF;border-top:1px solid #DBEAFE;color:#64748B;font-size:12px;">',
        CASE WHEN template.locale = 'zh-CN' THEN '此邮件由系统自动发送，请勿直接回复。'
             ELSE 'This is an automated message. Please do not reply.' END,
        '</div></div></div>'
    ),
    template.version_no = GREATEST(template.version_no, 2),
    template.update_by = 'system',
    template.update_time = CURRENT_TIMESTAMP(3)
WHERE template.system_builtin = 1
  AND template.deleted = 0
  AND template.template_code IN (
      'FEE_CONFIG_PENDING_REVIEW', 'FEE_CONFIG_REJECTED', 'FEE_RULE_MISSING',
      'SETTLEMENT_RATE_MISSING', 'NEGATIVE_BALANCE_INTERNAL', 'NEGATIVE_BALANCE_MERCHANT',
      'BALANCE_RESTORED', 'HOLIDAY_CALENDAR_MISSING', 'FUND_RECHARGE_POSTED',
      'FUND_RECHARGE_REJECTED'
  )
  AND template.content_template NOT LIKE '%data-template-theme="vexra-blue-white-v1"%';

COMMIT;
