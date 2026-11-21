-- 结算 Phase A 数据结构草案，版本：1.0.0-SNAPSHOT。
-- 只供 DBA 评审和受控环境执行；Codex 未执行本文件，不构成生产变更授权。
-- 前置条件：20260825_04_transaction_clearing_topology_draft.sql 已创建 settlement_candidate，
-- 且清分后仅有经审核从 shadow_mode=1 切换为 0、补齐 settlement_profile_id 的候选可被认领。
-- 本阶段不修改 merchant_fund_account、merchant_fund_ledger、merchant_reserve_item 或任何余额数据。

-- 执行前必须确认以下查询返回 settlement_candidate_count=1；否则停止，不得跳过清分交接表。
SELECT COUNT(*) AS settlement_candidate_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'settlement_candidate';

-- ============================================================================
-- 1. 商户结算档案、数据库日序列和结算批次；批次号格式 SByyyyMMdd-NNNNNNNN
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
    processing_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO_POST' COMMENT '结算处理模式：AUTO_POST自动入账，AUTO_REVIEW自动选取后审批，MANUAL人工选择',
    profile_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '结算档案状态：ACTIVE活动，RETIRED已退役，SUSPENDED已暂停',
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
        AND processing_mode IN ('AUTO_POST', 'AUTO_REVIEW', 'MANUAL')
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
        COMMENT '结算批次类型：常规、保证金释放、冲正或调整',
    original_batch_no VARCHAR(19) NULL COMMENT '冲正或调整引用的原批次号',
    cutoff_begin_time DATETIME(3) NOT NULL COMMENT '候选窗口起始时间，闭区间',
    cutoff_end_time DATETIME(3) NOT NULL COMMENT '候选窗口结束时间，开区间',
    batch_status VARCHAR(24) NOT NULL DEFAULT 'CREATED'
        COMMENT '结算批次状态：已创建、认领中、已认领、已锁汇、计算中、已计算、入账中、已入账、可重试失败、人工复核、已取消、冲正中或已冲正',
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

-- ============================================================================
-- 2. 候选依赖、批次认领审计、不可变汇率矩阵和只读结果结构
-- ============================================================================

CREATE TABLE settlement_candidate_dependency (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    candidate_id BIGINT NOT NULL COMMENT '当前退款、冲正或后续动作候选ID',
    depends_on_candidate_id BIGINT NOT NULL COMMENT '必须先入账或同批先处理的源候选ID',
    dependency_type VARCHAR(32) NOT NULL
        COMMENT '依赖完成条件：来源财务入账、保证金留存入账或来源费用结果入账',
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
        COMMENT '候选认领状态：已认领、已释放、已入账或人工复核',
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
    rate_type VARCHAR(24) NOT NULL DEFAULT 'SETTLEMENT' COMMENT '结算汇率用途：常规结算或保证金释放',
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
        COMMENT '结算来源明细类型：交易清分、保证金清分、批次限额或批次净额',
    source_detail_no VARCHAR(64) NULL COMMENT '来源清分明细号；批次限额调整可为空',
    reversal_of_result_item_id BIGINT NULL COMMENT '冲正时引用的原结算结果项ID',
    source_transaction_id VARCHAR(64) NULL COMMENT '来源动作交易号',
    source_transaction_date_time DATETIME(3) NULL COMMENT '来源季度路由时间',
    fee_group_no VARCHAR(64) NULL COMMENT '费用组号；非费用为空',
    result_item_type VARCHAR(32) NOT NULL
        COMMENT '结算结果明细类型：本金、费用分量、费用组最终值、保证金留存、保证金退回、保证金释放、调整、冲正或净结算',
    result_role VARCHAR(24) NOT NULL
        COMMENT '结算结果角色：追踪说明、财务分量或资金入账',
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
    applied_limit VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '限额命中类型：NONE未命中，MINIMUM最低限额，MAXIMUM最高限额',
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

-- 批次号必须在一个 transaction 主库本地事务中分配并插入批次：
-- 1. INSERT IGNORE 当日 sequence；2. SELECT ... FOR UPDATE；3. 递增；4. 插入 batch。
-- create_request_key 命中唯一键时必须返回并校验原批次身份；允许序号空洞，禁止 Redis/JVM 单独发号。
-- 候选认领必须使用 READY + batch_no IS NULL + shadow_mode=0 + version 的数据库 CAS，
-- 并在同一事务插入 settlement_batch_candidate；重复消费不能重复增加 candidate_count。
-- settlement_batch_rate、settlement_result_item、settlement_result_summary 在 Phase A 只提供 INSERT/SELECT 结构，
-- 进入 RATE_LOCKED 后禁止原地修改；任何修正必须通过后续 REVERSAL 或 ADJUSTMENT 批次表达。
