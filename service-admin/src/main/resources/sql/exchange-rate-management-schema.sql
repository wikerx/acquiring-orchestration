-- 汇率管理模块表结构与基础初始化脚本。
-- 本脚本为草案/初始化脚本，不由应用自动执行；执行前需人工确认数据库环境和现有菜单 ID。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS exchange_rate_source (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    source_code VARCHAR(64) NOT NULL COMMENT '汇率源编码，如 BOC/XE/MANUAL',
    source_name VARCHAR(128) NOT NULL COMMENT '汇率源名称',
    source_type VARCHAR(32) NOT NULL COMMENT '汇率源类型：WEB/API/MANUAL/IMPORT',
    request_url VARCHAR(512) NULL COMMENT '汇率源请求地址',
    default_source TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认来源：0否，1是',
    priority INT NOT NULL DEFAULT 100 COMMENT '优先级，数值越小优先级越高',
    timeout_seconds INT NOT NULL DEFAULT 10 COMMENT '拉取超时时间，单位秒',
    source_status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    last_fetch_time DATETIME(3) NULL COMMENT '最近一次拉取完成时间',
    last_fetch_status VARCHAR(32) NULL COMMENT '最近一次拉取状态：SUCCESS/FAILED/PARTIAL_SUCCESS',
    remark VARCHAR(512) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exchange_rate_source_code_deleted (source_code, deleted),
    KEY idx_exchange_rate_source_status (source_status, deleted),
    KEY idx_exchange_rate_source_priority (priority, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率源配置表';

CREATE TABLE IF NOT EXISTS exchange_raw_rate (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    source_code VARCHAR(64) NOT NULL COMMENT '汇率源编码',
    base_currency CHAR(3) NOT NULL COMMENT '原始币种 ISO 4217 编码',
    quote_currency CHAR(3) NOT NULL COMMENT '目标币种 ISO 4217 编码',
    cash_buy_rate DECIMAL(24, 12) NULL COMMENT '现钞买入价，统一为1原始币种兑换目标币种',
    cash_sell_rate DECIMAL(24, 12) NULL COMMENT '现钞卖出价，统一为1原始币种兑换目标币种',
    spot_buy_rate DECIMAL(24, 12) NULL COMMENT '现汇买入价，统一为1原始币种兑换目标币种',
    spot_sell_rate DECIMAL(24, 12) NULL COMMENT '现汇卖出价，统一为1原始币种兑换目标币种',
    middle_rate DECIMAL(24, 12) NULL COMMENT '中间折算价，统一为1原始币种兑换目标币种',
    publish_time DATETIME(3) NOT NULL COMMENT '汇率源发布时间',
    fetch_time DATETIME(3) NOT NULL COMMENT '系统拉取或录入时间',
    effective_time DATETIME(3) NOT NULL COMMENT '原始汇率生效时间',
    create_method VARCHAR(32) NOT NULL COMMENT '创建方式：AUTO/MANUAL/IMPORT',
    batch_no VARCHAR(64) NULL COMMENT '拉取或导入批次号',
    rate_status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/VOIDED',
    void_reason VARCHAR(512) NULL COMMENT '作废原因',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exchange_raw_rate_source_pair_publish (source_code, base_currency, quote_currency, publish_time, deleted),
    KEY idx_exchange_raw_rate_pair_status (base_currency, quote_currency, rate_status, deleted),
    KEY idx_exchange_raw_rate_source_time (source_code, publish_time, deleted),
    KEY idx_exchange_raw_rate_batch (batch_no, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='原始汇率记录表';

CREATE TABLE IF NOT EXISTS exchange_rate_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rate_type VARCHAR(32) NOT NULL COMMENT '汇率类型：TRANSACTION_RATE/SETTLEMENT_RATE',
    source_code VARCHAR(64) NOT NULL COMMENT '汇率源编码，ALL表示兜底',
    base_currency VARCHAR(3) NOT NULL COMMENT '原始币种，ALL表示兜底',
    quote_currency VARCHAR(3) NOT NULL COMMENT '目标币种，ALL表示兜底',
    rate_field VARCHAR(32) NOT NULL COMMENT '取值字段：SPOT_BUY_RATE/CASH_BUY_RATE/SPOT_SELL_RATE/CASH_SELL_RATE/MIDDLE_RATE',
    adjust_direction VARCHAR(16) NOT NULL COMMENT '调整方向：UP/DOWN/NONE',
    adjust_method VARCHAR(16) NOT NULL COMMENT '调整方式：BP/PERCENT',
    adjust_value DECIMAL(24, 12) NOT NULL DEFAULT 0 COMMENT '调整值，BP按基点，PERCENT按百分比',
    decimal_scale INT NOT NULL DEFAULT 8 COMMENT '最终汇率小数位',
    rounding_mode VARCHAR(32) NOT NULL DEFAULT 'ROUND_HALF_UP' COMMENT '舍入方式',
    priority INT NOT NULL DEFAULT 100 COMMENT '优先级，数值越小优先级越高',
    effective_start_time DATETIME(3) NULL COMMENT '规则生效开始时间',
    effective_end_time DATETIME(3) NULL COMMENT '规则生效结束时间',
    rule_status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    remark VARCHAR(512) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    KEY idx_exchange_rate_rule_scope (rate_type, source_code, base_currency, quote_currency, rule_status, deleted),
    KEY idx_exchange_rate_rule_time (effective_start_time, effective_end_time, deleted),
    KEY idx_exchange_rate_rule_priority (priority, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率规则配置表';

CREATE TABLE IF NOT EXISTS exchange_business_rate (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rate_type VARCHAR(32) NOT NULL COMMENT '汇率类型：TRANSACTION_RATE/SETTLEMENT_RATE',
    source_code VARCHAR(64) NOT NULL COMMENT '汇率源编码',
    base_currency CHAR(3) NOT NULL COMMENT '原始币种',
    quote_currency CHAR(3) NOT NULL COMMENT '目标币种',
    raw_rate_id BIGINT NULL COMMENT '原始汇率ID，手工录入业务汇率可为空',
    rule_id BIGINT NULL COMMENT '汇率规则ID，手工录入业务汇率可为空',
    original_rate DECIMAL(24, 12) NOT NULL COMMENT '规则选取的原始报价',
    final_rate DECIMAL(24, 12) NOT NULL COMMENT '最终业务汇率',
    adjust_description VARCHAR(512) NULL COMMENT '调整说明',
    effective_time DATETIME(3) NOT NULL COMMENT '业务汇率生效时间',
    expire_time DATETIME(3) NULL COMMENT '业务汇率失效时间',
    generate_method VARCHAR(32) NOT NULL COMMENT '生成方式：AUTO/MANUAL',
    rate_status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED/EXPIRED',
    remark VARCHAR(512) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    KEY idx_exchange_business_rate_lookup (rate_type, base_currency, quote_currency, deleted),
    KEY idx_exchange_business_rate_current (rate_type, base_currency, quote_currency, rate_status, effective_time, expire_time, deleted),
    KEY idx_exchange_business_rate_raw (raw_rate_id, deleted),
    KEY idx_exchange_business_rate_rule (rule_id, deleted),
    KEY idx_exchange_business_rate_volatility (rate_type, base_currency, quote_currency, effective_time, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务汇率表';

CREATE TABLE IF NOT EXISTS exchange_rate_alert_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rule_code VARCHAR(64) NOT NULL COMMENT '稳定规则编码',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    rate_type VARCHAR(32) NOT NULL COMMENT '汇率类型：TRANSACTION_RATE/SETTLEMENT_RATE',
    base_currency VARCHAR(3) NOT NULL DEFAULT 'ALL' COMMENT '原始币种，ALL表示所有币种',
    quote_currency VARCHAR(3) NOT NULL DEFAULT 'ALL' COMMENT '目标币种，ALL表示所有币种',
    window_minutes INT NOT NULL COMMENT '滚动窗口分钟数',
    trigger_threshold DECIMAL(9, 4) NOT NULL COMMENT '触发阈值百分比',
    recovery_threshold DECIMAL(9, 4) NOT NULL COMMENT '恢复阈值百分比',
    recovery_consecutive_count INT NOT NULL DEFAULT 2 COMMENT '连续低于恢复阈值的评估次数',
    minimum_sample_count INT NOT NULL DEFAULT 2 COMMENT '最小样本数',
    alert_level VARCHAR(32) NOT NULL DEFAULT 'WARNING' COMMENT '预警等级：WARNING/ERROR/CRITICAL',
    system_mandatory TINYINT NOT NULL DEFAULT 0 COMMENT '是否系统强制规则：0否，1是',
    rule_status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    remark VARCHAR(512) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exchange_rate_alert_rule_code_deleted (rule_code, deleted),
    KEY idx_exchange_rate_alert_rule_scope (rate_type, base_currency, quote_currency, rule_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率波动预警规则表';

CREATE TABLE IF NOT EXISTS exchange_rate_alert_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    event_no VARCHAR(64) NOT NULL COMMENT '预警事件编号',
    active_event_key VARCHAR(255) NULL COMMENT '活动事件唯一键，终态后置空',
    rule_id BIGINT NOT NULL COMMENT '规则ID',
    rule_code VARCHAR(64) NOT NULL COMMENT '触发时规则编码快照',
    rule_name VARCHAR(128) NOT NULL COMMENT '触发时规则名称快照',
    rate_type VARCHAR(32) NOT NULL COMMENT '汇率类型',
    base_currency CHAR(3) NOT NULL COMMENT '原始币种',
    quote_currency CHAR(3) NOT NULL COMMENT '目标币种',
    alert_level VARCHAR(32) NOT NULL COMMENT '预警等级快照',
    window_minutes INT NOT NULL COMMENT '窗口分钟数快照',
    trigger_threshold DECIMAL(9, 4) NOT NULL COMMENT '触发阈值百分比快照',
    recovery_threshold DECIMAL(9, 4) NOT NULL COMMENT '恢复阈值百分比快照',
    window_start_time DATETIME(3) NOT NULL COMMENT '最近评估窗口开始时间',
    window_end_time DATETIME(3) NOT NULL COMMENT '最近评估窗口结束时间',
    sample_count INT NOT NULL COMMENT '最近评估样本数',
    minimum_rate DECIMAL(24, 12) NOT NULL COMMENT '窗口最低汇率',
    minimum_rate_source VARCHAR(64) NULL COMMENT '最低汇率来源证据',
    minimum_business_rate_id BIGINT NULL COMMENT '最低汇率业务汇率ID',
    maximum_rate DECIMAL(24, 12) NOT NULL COMMENT '窗口最高汇率',
    maximum_rate_source VARCHAR(64) NULL COMMENT '最高汇率来源证据',
    maximum_business_rate_id BIGINT NULL COMMENT '最高汇率业务汇率ID',
    latest_rate DECIMAL(24, 12) NOT NULL COMMENT '窗口最新汇率',
    latest_rate_source VARCHAR(64) NULL COMMENT '最新汇率来源证据',
    latest_business_rate_id BIGINT NULL COMMENT '最新汇率业务汇率ID',
    volatility_rate DECIMAL(12, 6) NOT NULL COMMENT '最近窗口波动率百分比',
    maximum_volatility_rate DECIMAL(12, 6) NOT NULL COMMENT '事件持续期间最大波动率百分比',
    recovery_streak INT NOT NULL DEFAULT 0 COMMENT '连续低于恢复阈值次数',
    event_status VARCHAR(32) NOT NULL COMMENT 'OPEN/ACKNOWLEDGED/RESOLVED/CLOSED',
    first_trigger_time DATETIME(3) NOT NULL COMMENT '首次触发时间',
    last_detected_time DATETIME(3) NOT NULL COMMENT '最近发现时间',
    resolved_time DATETIME(3) NULL COMMENT '自动恢复时间',
    acknowledged_by VARCHAR(64) NULL COMMENT '确认人',
    acknowledged_time DATETIME(3) NULL COMMENT '确认时间',
    acknowledge_remark VARCHAR(512) NULL COMMENT '确认备注',
    closed_by VARCHAR(64) NULL COMMENT '关闭人',
    closed_time DATETIME(3) NULL COMMENT '关闭时间',
    close_remark VARCHAR(512) NULL COMMENT '关闭备注',
    version INT NOT NULL DEFAULT 0 COMMENT 'CAS版本',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exchange_rate_alert_event_no (event_no),
    UNIQUE KEY uk_exchange_rate_alert_active_key (active_event_key),
    KEY idx_exchange_rate_alert_event_status_time (event_status, first_trigger_time, id),
    KEY idx_exchange_rate_alert_event_scope_time (rate_type, base_currency, quote_currency, first_trigger_time, id),
    KEY idx_exchange_rate_alert_event_rule_time (rule_id, first_trigger_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率波动预警事件表';

CREATE TABLE IF NOT EXISTS exchange_rate_alert_evaluation (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    request_key VARCHAR(128) NOT NULL COMMENT '调度请求幂等键',
    window_end_time DATETIME(3) NOT NULL COMMENT '本轮评估业务截止时间',
    evaluation_status VARCHAR(32) NOT NULL COMMENT 'PROCESSING/SUCCESS/FAILED',
    evaluated_rule_count INT NOT NULL DEFAULT 0 COMMENT '评估规则数',
    evaluated_pair_count INT NOT NULL DEFAULT 0 COMMENT '评估币种对数',
    triggered_event_count INT NOT NULL DEFAULT 0 COMMENT '新触发事件数',
    updated_event_count INT NOT NULL DEFAULT 0 COMMENT '更新活动事件数',
    resolved_event_count INT NOT NULL DEFAULT 0 COMMENT '自动恢复事件数',
    insufficient_data_count INT NOT NULL DEFAULT 0 COMMENT '样本不足维度数',
    error_message VARCHAR(512) NULL COMMENT '失败摘要，不保存敏感信息',
    start_time DATETIME(3) NOT NULL COMMENT '开始时间',
    end_time DATETIME(3) NULL COMMENT '结束时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exchange_rate_alert_evaluation_request (request_key),
    KEY idx_exchange_rate_alert_evaluation_time (window_end_time, evaluation_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率波动预警评估幂等记录表';

CREATE TABLE IF NOT EXISTS exchange_rate_usage_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    usage_event_id VARCHAR(96) NOT NULL COMMENT '汇率使用事件全局幂等号',
    producer_service VARCHAR(64) NOT NULL COMMENT '产生实际使用事实的服务名',
    rate_type VARCHAR(32) NOT NULL COMMENT '汇率类型：TRANSACTION_RATE/SETTLEMENT_RATE',
    usage_scene VARCHAR(64) NOT NULL COMMENT '汇率使用场景',
    business_type VARCHAR(64) NOT NULL COMMENT '业务类型，如 PAYMENT/SETTLEMENT/REFUND',
    business_no VARCHAR(128) NOT NULL COMMENT '业务标识，如交易号、订单号、结算批次号',
    base_currency CHAR(3) NOT NULL COMMENT '原始币种',
    quote_currency CHAR(3) NOT NULL COMMENT '目标币种',
    used_rate DECIMAL(24, 12) NOT NULL COMMENT '实际使用汇率',
    business_rate_id BIGINT NULL COMMENT '业务汇率ID',
    raw_rate_id BIGINT NULL COMMENT '原始汇率ID',
    rule_id BIGINT NULL COMMENT '汇率规则ID',
    rate_source VARCHAR(64) NULL COMMENT '实际采用的汇率来源编码',
    source_quote_direction VARCHAR(16) NULL COMMENT '原始报价方向：DIRECT/INVERSE',
    rate_effective_time DATETIME(3) NULL COMMENT '实际采用汇率的生效时间',
    calculation_description VARCHAR(512) NULL COMMENT '计算说明',
    applied_time DATETIME(3) NOT NULL COMMENT '业务实际使用时间',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exchange_snapshot_usage_event (usage_event_id),
    KEY idx_exchange_snapshot_business (business_type, business_no, deleted),
    KEY idx_exchange_snapshot_scope (rate_type, usage_scene, base_currency, quote_currency, deleted),
    KEY idx_exchange_snapshot_applied (applied_time, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率使用快照表';

CREATE TABLE IF NOT EXISTS exchange_rate_fetch_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    batch_no VARCHAR(64) NOT NULL COMMENT '拉取批次号',
    source_code VARCHAR(64) NOT NULL COMMENT '汇率源编码',
    fetch_start_time DATETIME(3) NOT NULL COMMENT '拉取开始时间',
    fetch_end_time DATETIME(3) NULL COMMENT '拉取结束时间',
    fetch_status VARCHAR(32) NOT NULL COMMENT '拉取状态：SUCCESS/FAILED/PARTIAL_SUCCESS',
    request_url VARCHAR(512) NULL COMMENT '请求地址',
    total_count INT NOT NULL DEFAULT 0 COMMENT '解析总条数',
    success_count INT NOT NULL DEFAULT 0 COMMENT '成功入库条数',
    duplicate_count INT NOT NULL DEFAULT 0 COMMENT '重复跳过条数',
    skip_count INT NOT NULL DEFAULT 0 COMMENT '跳过条数',
    error_message TEXT NULL COMMENT '错误信息',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exchange_fetch_batch (batch_no),
    KEY idx_exchange_fetch_source (source_code),
    KEY idx_exchange_fetch_status (fetch_status),
    KEY idx_exchange_fetch_time (fetch_start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率拉取日志表';

-- 修复已存在表在错误客户端字符集下创建时产生的表注释/字段注释乱码。
ALTER TABLE exchange_rate_source COMMENT = '汇率源配置表',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN source_code VARCHAR(64) NOT NULL COMMENT '汇率源编码，如 BOC/XE/MANUAL',
    MODIFY COLUMN source_name VARCHAR(128) NOT NULL COMMENT '汇率源名称',
    MODIFY COLUMN source_type VARCHAR(32) NOT NULL COMMENT '汇率源类型：WEB/API/MANUAL/IMPORT',
    MODIFY COLUMN request_url VARCHAR(512) NULL COMMENT '汇率源请求地址',
    MODIFY COLUMN default_source TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认来源：0否，1是',
    MODIFY COLUMN priority INT NOT NULL DEFAULT 100 COMMENT '优先级，数值越小优先级越高',
    MODIFY COLUMN timeout_seconds INT NOT NULL DEFAULT 10 COMMENT '拉取超时时间，单位秒',
    MODIFY COLUMN source_status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    MODIFY COLUMN last_fetch_time DATETIME(3) NULL COMMENT '最近一次拉取完成时间',
    MODIFY COLUMN last_fetch_status VARCHAR(32) NULL COMMENT '最近一次拉取状态：SUCCESS/FAILED/PARTIAL_SUCCESS',
    MODIFY COLUMN remark VARCHAR(512) NULL COMMENT '备注',
    MODIFY COLUMN create_by VARCHAR(64) NULL COMMENT '创建人',
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN update_by VARCHAR(64) NULL COMMENT '更新人',
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID';

ALTER TABLE exchange_raw_rate COMMENT = '原始汇率记录表',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN source_code VARCHAR(64) NOT NULL COMMENT '汇率源编码',
    MODIFY COLUMN base_currency CHAR(3) NOT NULL COMMENT '原始币种 ISO 4217 编码',
    MODIFY COLUMN quote_currency CHAR(3) NOT NULL COMMENT '目标币种 ISO 4217 编码',
    MODIFY COLUMN cash_buy_rate DECIMAL(24, 12) NULL COMMENT '现钞买入价，统一为1原始币种兑换目标币种',
    MODIFY COLUMN cash_sell_rate DECIMAL(24, 12) NULL COMMENT '现钞卖出价，统一为1原始币种兑换目标币种',
    MODIFY COLUMN spot_buy_rate DECIMAL(24, 12) NULL COMMENT '现汇买入价，统一为1原始币种兑换目标币种',
    MODIFY COLUMN spot_sell_rate DECIMAL(24, 12) NULL COMMENT '现汇卖出价，统一为1原始币种兑换目标币种',
    MODIFY COLUMN middle_rate DECIMAL(24, 12) NULL COMMENT '中间折算价，统一为1原始币种兑换目标币种',
    MODIFY COLUMN publish_time DATETIME(3) NOT NULL COMMENT '汇率源发布时间',
    MODIFY COLUMN fetch_time DATETIME(3) NOT NULL COMMENT '系统拉取或录入时间',
    MODIFY COLUMN effective_time DATETIME(3) NOT NULL COMMENT '原始汇率生效时间',
    MODIFY COLUMN create_method VARCHAR(32) NOT NULL COMMENT '创建方式：AUTO/MANUAL/IMPORT',
    MODIFY COLUMN batch_no VARCHAR(64) NULL COMMENT '拉取或导入批次号',
    MODIFY COLUMN rate_status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/VOIDED',
    MODIFY COLUMN void_reason VARCHAR(512) NULL COMMENT '作废原因',
    MODIFY COLUMN create_by VARCHAR(64) NULL COMMENT '创建人',
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN update_by VARCHAR(64) NULL COMMENT '更新人',
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID';

ALTER TABLE exchange_rate_rule COMMENT = '汇率规则配置表',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN rate_type VARCHAR(32) NOT NULL COMMENT '汇率类型：TRANSACTION_RATE/SETTLEMENT_RATE',
    MODIFY COLUMN source_code VARCHAR(64) NOT NULL COMMENT '汇率源编码，ALL表示兜底',
    MODIFY COLUMN base_currency VARCHAR(3) NOT NULL COMMENT '原始币种，ALL表示兜底',
    MODIFY COLUMN quote_currency VARCHAR(3) NOT NULL COMMENT '目标币种，ALL表示兜底',
    MODIFY COLUMN rate_field VARCHAR(32) NOT NULL COMMENT '取值字段：SPOT_BUY_RATE/CASH_BUY_RATE/SPOT_SELL_RATE/CASH_SELL_RATE/MIDDLE_RATE',
    MODIFY COLUMN adjust_direction VARCHAR(16) NOT NULL COMMENT '调整方向：UP/DOWN/NONE',
    MODIFY COLUMN adjust_method VARCHAR(16) NOT NULL COMMENT '调整方式：BP/PERCENT',
    MODIFY COLUMN adjust_value DECIMAL(24, 12) NOT NULL DEFAULT 0 COMMENT '调整值，BP按基点，PERCENT按百分比',
    MODIFY COLUMN decimal_scale INT NOT NULL DEFAULT 8 COMMENT '最终汇率小数位',
    MODIFY COLUMN rounding_mode VARCHAR(32) NOT NULL DEFAULT 'ROUND_HALF_UP' COMMENT '舍入方式',
    MODIFY COLUMN priority INT NOT NULL DEFAULT 100 COMMENT '优先级，数值越小优先级越高',
    MODIFY COLUMN effective_start_time DATETIME(3) NULL COMMENT '规则生效开始时间',
    MODIFY COLUMN effective_end_time DATETIME(3) NULL COMMENT '规则生效结束时间',
    MODIFY COLUMN rule_status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    MODIFY COLUMN remark VARCHAR(512) NULL COMMENT '备注',
    MODIFY COLUMN create_by VARCHAR(64) NULL COMMENT '创建人',
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN update_by VARCHAR(64) NULL COMMENT '更新人',
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID';

ALTER TABLE exchange_business_rate COMMENT = '业务汇率表',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN rate_type VARCHAR(32) NOT NULL COMMENT '汇率类型：TRANSACTION_RATE/SETTLEMENT_RATE',
    MODIFY COLUMN source_code VARCHAR(64) NOT NULL COMMENT '汇率源编码',
    MODIFY COLUMN base_currency CHAR(3) NOT NULL COMMENT '原始币种',
    MODIFY COLUMN quote_currency CHAR(3) NOT NULL COMMENT '目标币种',
    MODIFY COLUMN raw_rate_id BIGINT NULL COMMENT '原始汇率ID，手工录入业务汇率可为空',
    MODIFY COLUMN rule_id BIGINT NULL COMMENT '汇率规则ID，手工录入业务汇率可为空',
    MODIFY COLUMN original_rate DECIMAL(24, 12) NOT NULL COMMENT '规则选取的原始报价',
    MODIFY COLUMN final_rate DECIMAL(24, 12) NOT NULL COMMENT '最终业务汇率',
    MODIFY COLUMN adjust_description VARCHAR(512) NULL COMMENT '调整说明',
    MODIFY COLUMN effective_time DATETIME(3) NOT NULL COMMENT '业务汇率生效时间',
    MODIFY COLUMN expire_time DATETIME(3) NULL COMMENT '业务汇率失效时间',
    MODIFY COLUMN generate_method VARCHAR(32) NOT NULL COMMENT '生成方式：AUTO/MANUAL',
    MODIFY COLUMN rate_status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED/EXPIRED',
    MODIFY COLUMN remark VARCHAR(512) NULL COMMENT '备注',
    MODIFY COLUMN create_by VARCHAR(64) NULL COMMENT '创建人',
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN update_by VARCHAR(64) NULL COMMENT '更新人',
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID';

ALTER TABLE exchange_rate_usage_snapshot COMMENT = '汇率使用快照表',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN rate_type VARCHAR(32) NOT NULL COMMENT '汇率类型：TRANSACTION_RATE/SETTLEMENT_RATE',
    MODIFY COLUMN usage_scene VARCHAR(64) NOT NULL COMMENT '汇率使用场景',
    MODIFY COLUMN business_type VARCHAR(64) NOT NULL COMMENT '业务类型，如 PAYMENT/SETTLEMENT/REFUND',
    MODIFY COLUMN business_no VARCHAR(128) NOT NULL COMMENT '业务标识，如交易号、订单号、结算批次号',
    MODIFY COLUMN base_currency CHAR(3) NOT NULL COMMENT '原始币种',
    MODIFY COLUMN quote_currency CHAR(3) NOT NULL COMMENT '目标币种',
    MODIFY COLUMN used_rate DECIMAL(24, 12) NOT NULL COMMENT '实际使用汇率',
    MODIFY COLUMN business_rate_id BIGINT NULL COMMENT '业务汇率ID',
    MODIFY COLUMN raw_rate_id BIGINT NULL COMMENT '原始汇率ID',
    MODIFY COLUMN rule_id BIGINT NULL COMMENT '汇率规则ID',
    MODIFY COLUMN calculation_description VARCHAR(512) NULL COMMENT '计算说明',
    MODIFY COLUMN applied_time DATETIME(3) NOT NULL COMMENT '业务实际使用时间',
    MODIFY COLUMN create_by VARCHAR(64) NULL COMMENT '创建人',
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID';

ALTER TABLE exchange_rate_fetch_log COMMENT = '汇率拉取日志表',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN batch_no VARCHAR(64) NOT NULL COMMENT '拉取批次号',
    MODIFY COLUMN source_code VARCHAR(64) NOT NULL COMMENT '汇率源编码',
    MODIFY COLUMN fetch_start_time DATETIME(3) NOT NULL COMMENT '拉取开始时间',
    MODIFY COLUMN fetch_end_time DATETIME(3) NULL COMMENT '拉取结束时间',
    MODIFY COLUMN fetch_status VARCHAR(32) NOT NULL COMMENT '拉取状态：SUCCESS/FAILED/PARTIAL_SUCCESS',
    MODIFY COLUMN request_url VARCHAR(512) NULL COMMENT '请求地址',
    MODIFY COLUMN total_count INT NOT NULL DEFAULT 0 COMMENT '解析总条数',
    MODIFY COLUMN success_count INT NOT NULL DEFAULT 0 COMMENT '成功入库条数',
    MODIFY COLUMN duplicate_count INT NOT NULL DEFAULT 0 COMMENT '重复跳过条数',
    MODIFY COLUMN skip_count INT NOT NULL DEFAULT 0 COMMENT '跳过条数',
    MODIFY COLUMN error_message TEXT NULL COMMENT '错误信息',
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';

-- 已存在环境的索引收敛：业务汇率高频读取按汇率类型、原始币种、目标币种和未删除查询。
SET @exchange_business_rate_drop_scope := (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE exchange_business_rate DROP INDEX idx_exchange_business_rate_scope', 'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'exchange_business_rate'
      AND index_name = 'idx_exchange_business_rate_scope'
);
PREPARE stmt FROM @exchange_business_rate_drop_scope;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exchange_business_rate_drop_time := (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE exchange_business_rate DROP INDEX idx_exchange_business_rate_time', 'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'exchange_business_rate'
      AND index_name = 'idx_exchange_business_rate_time'
);
PREPARE stmt FROM @exchange_business_rate_drop_time;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exchange_business_rate_add_lookup := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE exchange_business_rate ADD INDEX idx_exchange_business_rate_lookup (rate_type, base_currency, quote_currency, deleted)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'exchange_business_rate'
      AND index_name = 'idx_exchange_business_rate_lookup'
);
PREPARE stmt FROM @exchange_business_rate_add_lookup;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exchange_business_rate_add_current := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE exchange_business_rate ADD INDEX idx_exchange_business_rate_current (rate_type, base_currency, quote_currency, rate_status, effective_time, expire_time, deleted)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'exchange_business_rate'
      AND index_name = 'idx_exchange_business_rate_current'
);
PREPARE stmt FROM @exchange_business_rate_add_current;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO exchange_rate_source (
    source_code, source_name, source_type, request_url, default_source, priority, timeout_seconds, source_status, remark, create_by, update_by, deleted
) VALUES (
    'BOC', '中国银行', 'WEB', 'https://www.boc.cn/sourcedb/whpj/', 1, 1, 10, 1, '自动拉取中国银行外汇牌价', 'system', 'system', 0
)
ON DUPLICATE KEY UPDATE
    source_name = VALUES(source_name),
    source_type = VALUES(source_type),
    request_url = VALUES(request_url),
    default_source = VALUES(default_source),
    priority = VALUES(priority),
    timeout_seconds = VALUES(timeout_seconds),
    source_status = VALUES(source_status),
    remark = VALUES(remark),
    update_by = VALUES(update_by);

INSERT INTO exchange_rate_source (
    source_code, source_name, source_type, request_url, default_source, priority, timeout_seconds, source_status, remark, create_by, update_by, deleted
) VALUES (
    'NBP', '波兰国家银行', 'API', 'https://api.nbp.pl/api/exchangerates/tables/C?format=json', 0, 20, 10, 1,
    '自动拉取 NBP Table C 外币兑 PLN 买卖报价', 'system', 'system', 0
)
ON DUPLICATE KEY UPDATE
    source_name = VALUES(source_name),
    source_type = VALUES(source_type),
    request_url = VALUES(request_url),
    priority = VALUES(priority),
    timeout_seconds = VALUES(timeout_seconds),
    remark = VALUES(remark),
    update_by = VALUES(update_by);

-- 外部汇率源币种名称映射已下沉到汇率源 Provider，不再保留独立业务表。
DROP TABLE IF EXISTS exchange_source_currency_mapping;

-- 统一任务调度编码：job_code 使用大写下划线，handler_code 使用 lowerCamelCase。
UPDATE sys_job_task
SET job_code = 'SHARDING_TABLE_PRE_CREATE',
    handler_code = 'shardingTablePreCreate',
    update_by = 'system'
WHERE BINARY job_code = 'sharding_table_pre_create'
  AND deleted = 0;

UPDATE sys_job_task
SET handler_code = 'shardingTablePreCreate',
    update_by = 'system'
WHERE job_code = 'SHARDING_TABLE_PRE_CREATE'
  AND handler_code = 'sharding.table.pre-create'
  AND deleted = 0;

INSERT IGNORE INTO sys_job_task (
    job_code, job_name, job_group, handler_code, cron_expression, scheduler_mode, trigger_mode, execute_mode,
    route_strategy, misfire_strategy, timeout_seconds, retry_count, retry_interval_seconds, allow_concurrent,
    params, status, description, version, deleted, create_by, update_by
) VALUES (
    'BOC_EXCHANGE_RATE_FETCH', '中国银行汇率拉取任务', 'exchange', 'bocExchangeRateFetchJob', '0 */30 * * * ?',
    'DISTRIBUTED', 'CRON', 'SYNC', 'LOCAL', 'FIRE_ONCE', 60, 3, 60, 0,
    '{"sourceCode":"BOC","dryRun":false}', 'DISABLED', '每30分钟拉取中国银行外汇牌价；首次建议手动 dryRun 后再启用。', 0, 0, 'system', 'system'
);

UPDATE sys_job_task
SET job_code = 'BOC_EXCHANGE_RATE_FETCH',
    update_by = 'system'
WHERE BINARY job_code = 'boc_exchange_rate_fetch'
  AND deleted = 0;

UPDATE sys_job_task
SET job_name = '中国银行汇率拉取任务',
    description = '每30分钟拉取中国银行外汇牌价；首次建议手动 dryRun 后再启用。',
    update_by = 'system'
WHERE job_code = 'BOC_EXCHANGE_RATE_FETCH'
  AND deleted = 0;

INSERT IGNORE INTO sys_job_task (
    job_code, job_name, job_group, handler_code, cron_expression, scheduler_mode, trigger_mode, execute_mode,
    route_strategy, misfire_strategy, timeout_seconds, retry_count, retry_interval_seconds, allow_concurrent,
    params, status, description, version, deleted, create_by, update_by
) VALUES (
    'NBP_EXCHANGE_RATE_FETCH', '波兰国家银行汇率拉取任务', 'exchange', 'nbpExchangeRateFetchJob', '0 30 16 ? * MON-FRI',
    'DISTRIBUTED', 'CRON', 'SYNC', 'LOCAL', 'FIRE_ONCE', 60, 3, 60, 0,
    '{"sourceCode":"NBP","dryRun":false}', 'ENABLED',
    '每个工作日16:30拉取 NBP Table C 外币兑 PLN 买卖报价；业务汇率由管理端汇率规则决定。',
    0, 0, 'system', 'system'
);

-- 菜单和权限初始化使用确定性菜单编码，不写死菜单 ID。
INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, sort_no, status, deleted)
SELECT 1, 0, 'exchange', '汇率管理', 'CATALOG', '/exchange', NULL, NULL, 'Money', 6, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE app_id = 1 AND menu_code = 'exchange' AND deleted = 0);

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, sort_no, status, deleted)
SELECT 1, parent.id, child.menu_code, child.menu_name, 'MENU', child.route_path, child.component_path, child.permission_code, child.icon, child.sort_no, 1, 0
FROM sys_menu parent
JOIN (
    SELECT 'exchange_source' menu_code, '汇率源管理' menu_name, '/exchange/source' route_path, 'exchange/source' component_path, 'exchange:source:list' permission_code, 'Connection' icon, 1 sort_no
    UNION ALL SELECT 'exchange_raw_rate', '原始汇率记录', '/exchange/raw-rate', 'exchange/raw-rate', 'exchange:raw-rate:list', 'Tickets', 3
    UNION ALL SELECT 'exchange_rule', '汇率规则配置', '/exchange/rule', 'exchange/rule', 'exchange:rule:list', 'Setting', 4
    UNION ALL SELECT 'exchange_business_rate', '业务汇率管理', '/exchange/business-rate', 'exchange/business-rate', 'exchange:business-rate:list', 'TrendCharts', 5
    UNION ALL SELECT 'exchange_usage_snapshot', '汇率使用快照', '/exchange/usage-snapshot', 'exchange/usage-snapshot', 'exchange:usage-snapshot:list', 'DocumentChecked', 6
) child ON parent.app_id = 1 AND parent.menu_code = 'exchange' AND parent.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu exists_menu WHERE exists_menu.app_id = 1 AND exists_menu.menu_code = child.menu_code AND exists_menu.deleted = 0);

INSERT INTO sys_permission (app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
SELECT 1, menu.id, perm.permission_code, perm.permission_name, 'API', perm.resource_method, perm.resource_path, 1, 0
FROM (
    SELECT 'exchange_source' menu_code, 'exchange:source:list' permission_code, '汇率源查询' permission_name, 'POST' resource_method, '/admin/exchange/sources/search' resource_path
    UNION ALL SELECT 'exchange_source', 'exchange:source:detail', '汇率源详情', 'GET', '/admin/exchange/sources/*'
    UNION ALL SELECT 'exchange_source', 'exchange:source:add', '汇率源新增', 'POST', '/admin/exchange/sources'
    UNION ALL SELECT 'exchange_source', 'exchange:source:edit', '汇率源修改', 'PUT', '/admin/exchange/sources/*'
    UNION ALL SELECT 'exchange_source', 'exchange:source:status', '汇率源状态', 'PUT', '/admin/exchange/sources/*/status'
    UNION ALL SELECT 'exchange_source', 'exchange:source:remove', '汇率源删除', 'DELETE', '/admin/exchange/sources/*'
    UNION ALL SELECT 'exchange_source', 'exchange:source:export', '汇率源导出', 'POST', '/admin/exchange/sources/export'
    UNION ALL SELECT 'exchange_raw_rate', 'exchange:raw-rate:list', '原始汇率查询', 'POST', '/admin/exchange/raw-rates/search'
    UNION ALL SELECT 'exchange_raw_rate', 'exchange:raw-rate:detail', '原始汇率详情', 'GET', '/admin/exchange/raw-rates/*'
    UNION ALL SELECT 'exchange_raw_rate', 'exchange:raw-rate:add', '原始汇率新增', 'POST', '/admin/exchange/raw-rates'
    UNION ALL SELECT 'exchange_raw_rate', 'exchange:raw-rate:void', '原始汇率作废', 'PUT', '/admin/exchange/raw-rates/*/void'
    UNION ALL SELECT 'exchange_raw_rate', 'exchange:raw-rate:export', '原始汇率导出', 'POST', '/admin/exchange/raw-rates/export'
    UNION ALL SELECT 'exchange_rule', 'exchange:rule:list', '汇率规则查询', 'POST', '/admin/exchange/rules/search'
    UNION ALL SELECT 'exchange_rule', 'exchange:rule:detail', '汇率规则详情', 'GET', '/admin/exchange/rules/*'
    UNION ALL SELECT 'exchange_rule', 'exchange:rule:add', '汇率规则新增', 'POST', '/admin/exchange/rules'
    UNION ALL SELECT 'exchange_rule', 'exchange:rule:edit', '汇率规则修改', 'PUT', '/admin/exchange/rules/*'
    UNION ALL SELECT 'exchange_rule', 'exchange:rule:status', '汇率规则状态', 'PUT', '/admin/exchange/rules/*/status'
    UNION ALL SELECT 'exchange_rule', 'exchange:rule:export', '汇率规则导出', 'POST', '/admin/exchange/rules/export'
    UNION ALL SELECT 'exchange_business_rate', 'exchange:business-rate:list', '业务汇率查询', 'POST', '/admin/exchange/business-rates/search'
    UNION ALL SELECT 'exchange_business_rate', 'exchange:business-rate:detail', '业务汇率详情', 'GET', '/admin/exchange/business-rates/*'
    UNION ALL SELECT 'exchange_business_rate', 'exchange:business-rate:add', '业务汇率新增', 'POST', '/admin/exchange/business-rates'
    UNION ALL SELECT 'exchange_business_rate', 'exchange:business-rate:batch', '业务汇率批量录入', 'POST', '/admin/exchange/business-rates/batch'
    UNION ALL SELECT 'exchange_business_rate', 'exchange:business-rate:status', '业务汇率状态', 'PUT', '/admin/exchange/business-rates/*/status'
    UNION ALL SELECT 'exchange_business_rate', 'exchange:business-rate:export', '业务汇率导出', 'POST', '/admin/exchange/business-rates/export'
    UNION ALL SELECT 'exchange_usage_snapshot', 'exchange:usage-snapshot:list', '汇率快照查询', 'POST', '/admin/exchange/usage-snapshots/search'
    UNION ALL SELECT 'exchange_usage_snapshot', 'exchange:usage-snapshot:detail', '汇率快照详情', 'GET', '/admin/exchange/usage-snapshots/*'
    UNION ALL SELECT 'exchange_usage_snapshot', 'exchange:usage-snapshot:export', '汇率快照导出', 'POST', '/admin/exchange/usage-snapshots/export'
) perm
JOIN sys_menu menu ON menu.app_id = 1 AND menu.menu_code = perm.menu_code AND menu.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission exists_perm
    WHERE exists_perm.app_id = 1 AND exists_perm.permission_code = perm.permission_code AND exists_perm.deleted = 0
);

-- 修复重复执行或客户端字符集不一致导致的菜单/权限名称，并补齐菜单管理页可见的按钮权限节点。
UPDATE sys_menu menu
JOIN (
    SELECT 'exchange' menu_code, '汇率管理' menu_name, 'CATALOG' menu_type, '/exchange' route_path, NULL component_path, NULL permission_code, 'Money' icon, 1 visible, 6 sort_no
    UNION ALL SELECT 'exchange_source', '汇率源管理', 'MENU', '/exchange/source', 'exchange/source', 'exchange:source:list', 'Connection', 1, 1
    UNION ALL SELECT 'exchange_raw_rate', '原始汇率记录', 'MENU', '/exchange/raw-rate', 'exchange/raw-rate', 'exchange:raw-rate:list', 'Tickets', 1, 3
    UNION ALL SELECT 'exchange_rule', '汇率规则配置', 'MENU', '/exchange/rule', 'exchange/rule', 'exchange:rule:list', 'Setting', 1, 4
    UNION ALL SELECT 'exchange_business_rate', '业务汇率管理', 'MENU', '/exchange/business-rate', 'exchange/business-rate', 'exchange:business-rate:list', 'TrendCharts', 1, 5
    UNION ALL SELECT 'exchange_usage_snapshot', '汇率使用快照', 'MENU', '/exchange/usage-snapshot', 'exchange/usage-snapshot', 'exchange:usage-snapshot:list', 'DocumentChecked', 1, 6
) patch ON patch.menu_code = menu.menu_code
SET menu.menu_name = patch.menu_name,
    menu.menu_type = patch.menu_type,
    menu.route_path = patch.route_path,
    menu.component_path = patch.component_path,
    menu.permission_code = patch.permission_code,
    menu.icon = patch.icon,
    menu.visible = patch.visible,
    menu.sort_no = patch.sort_no,
    menu.status = 1
WHERE menu.app_id = 1
  AND menu.deleted = 0;

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, parent.id, button.menu_code, button.menu_name, 'BUTTON', NULL, NULL, button.permission_code, NULL, 0, button.sort_no, 1, 0
FROM sys_menu parent
JOIN (
    SELECT 'exchange_source' parent_code, 'exchange_source_detail' menu_code, '汇率源详情' menu_name, 'exchange:source:detail' permission_code, 101 sort_no
    UNION ALL SELECT 'exchange_source', 'exchange_source_add', '汇率源新增', 'exchange:source:add', 102
    UNION ALL SELECT 'exchange_source', 'exchange_source_edit', '汇率源修改', 'exchange:source:edit', 103
    UNION ALL SELECT 'exchange_source', 'exchange_source_status', '汇率源状态', 'exchange:source:status', 104
    UNION ALL SELECT 'exchange_source', 'exchange_source_remove', '汇率源删除', 'exchange:source:remove', 105
    UNION ALL SELECT 'exchange_raw_rate', 'exchange_raw_rate_detail', '原始汇率详情', 'exchange:raw-rate:detail', 101
    UNION ALL SELECT 'exchange_raw_rate', 'exchange_raw_rate_add', '原始汇率新增', 'exchange:raw-rate:add', 102
    UNION ALL SELECT 'exchange_raw_rate', 'exchange_raw_rate_void', '原始汇率作废', 'exchange:raw-rate:void', 103
    UNION ALL SELECT 'exchange_rule', 'exchange_rule_detail', '汇率规则详情', 'exchange:rule:detail', 101
    UNION ALL SELECT 'exchange_rule', 'exchange_rule_add', '汇率规则新增', 'exchange:rule:add', 102
    UNION ALL SELECT 'exchange_rule', 'exchange_rule_edit', '汇率规则修改', 'exchange:rule:edit', 103
    UNION ALL SELECT 'exchange_rule', 'exchange_rule_status', '汇率规则状态', 'exchange:rule:status', 104
    UNION ALL SELECT 'exchange_business_rate', 'exchange_business_rate_detail', '业务汇率详情', 'exchange:business-rate:detail', 101
    UNION ALL SELECT 'exchange_business_rate', 'exchange_business_rate_add', '业务汇率新增', 'exchange:business-rate:add', 102
    UNION ALL SELECT 'exchange_business_rate', 'exchange_business_rate_batch', '业务汇率批量录入', 'exchange:business-rate:batch', 103
    UNION ALL SELECT 'exchange_business_rate', 'exchange_business_rate_status', '业务汇率状态', 'exchange:business-rate:status', 104
    UNION ALL SELECT 'exchange_usage_snapshot', 'exchange_usage_snapshot_detail', '汇率快照详情', 'exchange:usage-snapshot:detail', 101
) button ON button.parent_code = parent.menu_code
WHERE parent.app_id = 1
  AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu exists_menu
      WHERE exists_menu.app_id = 1
        AND exists_menu.menu_code = button.menu_code
        AND exists_menu.deleted = 0
  );

UPDATE sys_menu menu
JOIN (
    SELECT 'exchange_source_detail' menu_code, '汇率源详情' menu_name, 'exchange:source:detail' permission_code, 101 sort_no
    UNION ALL SELECT 'exchange_source_add', '汇率源新增', 'exchange:source:add', 102
    UNION ALL SELECT 'exchange_source_edit', '汇率源修改', 'exchange:source:edit', 103
    UNION ALL SELECT 'exchange_source_status', '汇率源状态', 'exchange:source:status', 104
    UNION ALL SELECT 'exchange_source_remove', '汇率源删除', 'exchange:source:remove', 105
    UNION ALL SELECT 'exchange_source_export', '汇率源导出', 'exchange:source:export', 106
    UNION ALL SELECT 'exchange_raw_rate_detail', '原始汇率详情', 'exchange:raw-rate:detail', 101
    UNION ALL SELECT 'exchange_raw_rate_add', '原始汇率新增', 'exchange:raw-rate:add', 102
    UNION ALL SELECT 'exchange_raw_rate_void', '原始汇率作废', 'exchange:raw-rate:void', 103
    UNION ALL SELECT 'exchange_raw_rate_export', '原始汇率导出', 'exchange:raw-rate:export', 104
    UNION ALL SELECT 'exchange_rule_detail', '汇率规则详情', 'exchange:rule:detail', 101
    UNION ALL SELECT 'exchange_rule_add', '汇率规则新增', 'exchange:rule:add', 102
    UNION ALL SELECT 'exchange_rule_edit', '汇率规则修改', 'exchange:rule:edit', 103
    UNION ALL SELECT 'exchange_rule_status', '汇率规则状态', 'exchange:rule:status', 104
    UNION ALL SELECT 'exchange_rule_export', '汇率规则导出', 'exchange:rule:export', 105
    UNION ALL SELECT 'exchange_business_rate_detail', '业务汇率详情', 'exchange:business-rate:detail', 101
    UNION ALL SELECT 'exchange_business_rate_add', '业务汇率新增', 'exchange:business-rate:add', 102
    UNION ALL SELECT 'exchange_business_rate_batch', '业务汇率批量录入', 'exchange:business-rate:batch', 103
    UNION ALL SELECT 'exchange_business_rate_status', '业务汇率状态', 'exchange:business-rate:status', 104
    UNION ALL SELECT 'exchange_business_rate_export', '业务汇率导出', 'exchange:business-rate:export', 105
    UNION ALL SELECT 'exchange_usage_snapshot_detail', '汇率快照详情', 'exchange:usage-snapshot:detail', 101
    UNION ALL SELECT 'exchange_usage_snapshot_export', '汇率快照导出', 'exchange:usage-snapshot:export', 102
) patch ON patch.menu_code = menu.menu_code
SET menu.menu_name = patch.menu_name,
    menu.menu_type = 'BUTTON',
    menu.route_path = NULL,
    menu.component_path = NULL,
    menu.permission_code = patch.permission_code,
    menu.icon = NULL,
    menu.visible = 0,
    menu.sort_no = patch.sort_no,
    menu.status = 1
WHERE menu.app_id = 1
  AND menu.deleted = 0;

-- 汇率源币种映射是外部汇率源解析适配表，不作为后台菜单暴露，避免与基础数据币种管理重复。
UPDATE sys_role_menu role_menu
JOIN sys_menu menu ON menu.id = role_menu.menu_id
SET role_menu.deleted = role_menu.id
WHERE menu.app_id = 1
  AND menu.menu_code LIKE 'exchange_currency_mapping%'
  AND role_menu.deleted = 0;

UPDATE sys_role_permission role_permission
JOIN sys_permission permission ON permission.id = role_permission.permission_id
SET role_permission.deleted = role_permission.id
WHERE permission.app_id = 1
  AND permission.permission_code LIKE 'exchange:currency-mapping:%'
  AND role_permission.deleted = 0;

UPDATE sys_permission
SET deleted = id,
    status = 0
WHERE app_id = 1
  AND permission_code LIKE 'exchange:currency-mapping:%'
  AND deleted = 0;

UPDATE sys_menu
SET deleted = id,
    status = 0,
    visible = 0
WHERE app_id = 1
    AND menu_code LIKE 'exchange_currency_mapping%'
    AND deleted = 0;

UPDATE sys_role_menu role_menu
JOIN sys_menu menu ON menu.id = role_menu.menu_id
SET role_menu.deleted = role_menu.id
WHERE menu.app_id = 1
  AND menu.menu_code = 'exchange_business_rate_generate'
  AND role_menu.deleted = 0;

UPDATE sys_role_permission role_permission
JOIN sys_permission permission ON permission.id = role_permission.permission_id
SET role_permission.deleted = role_permission.id
WHERE permission.app_id = 1
  AND permission.permission_code = 'exchange:business-rate:generate'
  AND role_permission.deleted = 0;

UPDATE sys_permission
SET deleted = id,
    status = 0
WHERE app_id = 1
  AND permission_code = 'exchange:business-rate:generate'
  AND deleted = 0;

UPDATE sys_menu
SET deleted = id,
    status = 0,
    visible = 0
WHERE app_id = 1
  AND menu_code = 'exchange_business_rate_generate'
  AND deleted = 0;

UPDATE sys_permission permission
JOIN (
    SELECT 'exchange:source:list' permission_code, '汇率源查询' permission_name, 'MENU' permission_type, 'POST' resource_method, '/admin/exchange/sources/search' resource_path, 'exchange_source' menu_code
    UNION ALL SELECT 'exchange:source:detail', '汇率源详情', 'BUTTON', 'GET', '/admin/exchange/sources/*', 'exchange_source_detail'
    UNION ALL SELECT 'exchange:source:add', '汇率源新增', 'BUTTON', 'POST', '/admin/exchange/sources', 'exchange_source_add'
    UNION ALL SELECT 'exchange:source:edit', '汇率源修改', 'BUTTON', 'PUT', '/admin/exchange/sources/*', 'exchange_source_edit'
    UNION ALL SELECT 'exchange:source:status', '汇率源状态', 'BUTTON', 'PUT', '/admin/exchange/sources/*/status', 'exchange_source_status'
    UNION ALL SELECT 'exchange:source:remove', '汇率源删除', 'BUTTON', 'DELETE', '/admin/exchange/sources/*', 'exchange_source_remove'
    UNION ALL SELECT 'exchange:source:export', '汇率源导出', 'BUTTON', 'POST', '/admin/exchange/sources/export', 'exchange_source_export'
    UNION ALL SELECT 'exchange:raw-rate:list', '原始汇率查询', 'MENU', 'POST', '/admin/exchange/raw-rates/search', 'exchange_raw_rate'
    UNION ALL SELECT 'exchange:raw-rate:detail', '原始汇率详情', 'BUTTON', 'GET', '/admin/exchange/raw-rates/*', 'exchange_raw_rate_detail'
    UNION ALL SELECT 'exchange:raw-rate:add', '原始汇率新增', 'BUTTON', 'POST', '/admin/exchange/raw-rates', 'exchange_raw_rate_add'
    UNION ALL SELECT 'exchange:raw-rate:void', '原始汇率作废', 'BUTTON', 'PUT', '/admin/exchange/raw-rates/*/void', 'exchange_raw_rate_void'
    UNION ALL SELECT 'exchange:raw-rate:export', '原始汇率导出', 'BUTTON', 'POST', '/admin/exchange/raw-rates/export', 'exchange_raw_rate_export'
    UNION ALL SELECT 'exchange:rule:list', '汇率规则查询', 'MENU', 'POST', '/admin/exchange/rules/search', 'exchange_rule'
    UNION ALL SELECT 'exchange:rule:detail', '汇率规则详情', 'BUTTON', 'GET', '/admin/exchange/rules/*', 'exchange_rule_detail'
    UNION ALL SELECT 'exchange:rule:add', '汇率规则新增', 'BUTTON', 'POST', '/admin/exchange/rules', 'exchange_rule_add'
    UNION ALL SELECT 'exchange:rule:edit', '汇率规则修改', 'BUTTON', 'PUT', '/admin/exchange/rules/*', 'exchange_rule_edit'
    UNION ALL SELECT 'exchange:rule:status', '汇率规则状态', 'BUTTON', 'PUT', '/admin/exchange/rules/*/status', 'exchange_rule_status'
    UNION ALL SELECT 'exchange:rule:export', '汇率规则导出', 'BUTTON', 'POST', '/admin/exchange/rules/export', 'exchange_rule_export'
    UNION ALL SELECT 'exchange:business-rate:list', '业务汇率查询', 'MENU', 'POST', '/admin/exchange/business-rates/search', 'exchange_business_rate'
    UNION ALL SELECT 'exchange:business-rate:detail', '业务汇率详情', 'BUTTON', 'GET', '/admin/exchange/business-rates/*', 'exchange_business_rate_detail'
    UNION ALL SELECT 'exchange:business-rate:add', '业务汇率新增', 'BUTTON', 'POST', '/admin/exchange/business-rates', 'exchange_business_rate_add'
    UNION ALL SELECT 'exchange:business-rate:batch', '业务汇率批量录入', 'BUTTON', 'POST', '/admin/exchange/business-rates/batch', 'exchange_business_rate_batch'
    UNION ALL SELECT 'exchange:business-rate:status', '业务汇率状态', 'BUTTON', 'PUT', '/admin/exchange/business-rates/*/status', 'exchange_business_rate_status'
    UNION ALL SELECT 'exchange:business-rate:export', '业务汇率导出', 'BUTTON', 'POST', '/admin/exchange/business-rates/export', 'exchange_business_rate_export'
    UNION ALL SELECT 'exchange:usage-snapshot:list', '汇率快照查询', 'MENU', 'POST', '/admin/exchange/usage-snapshots/search', 'exchange_usage_snapshot'
    UNION ALL SELECT 'exchange:usage-snapshot:detail', '汇率快照详情', 'BUTTON', 'GET', '/admin/exchange/usage-snapshots/*', 'exchange_usage_snapshot_detail'
    UNION ALL SELECT 'exchange:usage-snapshot:export', '汇率快照导出', 'BUTTON', 'POST', '/admin/exchange/usage-snapshots/export', 'exchange_usage_snapshot_export'
) patch ON patch.permission_code = permission.permission_code
JOIN sys_menu menu ON menu.app_id = permission.app_id AND menu.menu_code = patch.menu_code AND menu.deleted = 0
SET permission.menu_id = menu.id,
    permission.permission_name = patch.permission_name,
    permission.permission_type = patch.permission_type,
    permission.resource_method = patch.resource_method,
    permission.resource_path = patch.resource_path,
    permission.status = 1
WHERE permission.app_id = 1
  AND permission.deleted = 0;

-- 汇率波动强制基线、调度任务以及细粒度菜单权限。
INSERT INTO exchange_rate_alert_rule (
    rule_code, rule_name, rate_type, base_currency, quote_currency, window_minutes,
    trigger_threshold, recovery_threshold, recovery_consecutive_count, minimum_sample_count,
    alert_level, system_mandatory, rule_status, remark, create_by, update_by, deleted
) VALUES
    ('SYSTEM_TRANSACTION_72H_3PCT', '交易汇率72小时强制波动预警', 'TRANSACTION_RATE', 'ALL', 'ALL', 4320,
     3.0000, 2.0000, 2, 2, 'WARNING', 1, 1, '系统强制基线：波动率大于3%触发，连续两次低于2%恢复。', 'system', 'system', 0),
    ('SYSTEM_SETTLEMENT_72H_3PCT', '结算汇率72小时强制波动预警', 'SETTLEMENT_RATE', 'ALL', 'ALL', 4320,
     3.0000, 2.0000, 2, 2, 'WARNING', 1, 1, '系统强制基线：波动率大于3%触发，连续两次低于2%恢复。', 'system', 'system', 0)
ON DUPLICATE KEY UPDATE
    rule_name = VALUES(rule_name),
    rate_type = VALUES(rate_type),
    base_currency = 'ALL',
    quote_currency = 'ALL',
    window_minutes = 4320,
    trigger_threshold = 3.0000,
    recovery_threshold = 2.0000,
    recovery_consecutive_count = 2,
    minimum_sample_count = 2,
    alert_level = 'WARNING',
    system_mandatory = 1,
    rule_status = 1,
    remark = VALUES(remark),
    deleted = 0,
    update_by = 'system';

INSERT INTO sys_job_task (
    job_code, job_name, job_group, handler_code, cron_expression, scheduler_mode, trigger_mode,
    execute_mode, route_strategy, misfire_strategy, timeout_seconds, retry_count,
    retry_interval_seconds, allow_concurrent, params, status, description, next_trigger_time,
    version, deleted, create_by, create_time, update_by, update_time
) SELECT
    'EXCHANGE_RATE_VOLATILITY_MONITOR', '汇率波动预警评估任务', 'exchange',
    'exchangeRateVolatilityMonitorJob', '0 */5 * * * ?', 'DISTRIBUTED', 'CRON',
    'SYNC', 'LOCAL', 'FIRE_ONCE', 120, 2, 30, 0, '{}', 'ENABLED',
    '每5分钟调用 service-admin 幂等评估交易汇率和结算汇率波动；系统任务禁止删除或更换处理器。',
    DATE_ADD(NOW(3), INTERVAL 5 MINUTE), 0, 0, 'system', NOW(3), 'system', NOW(3)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job_task
    WHERE job_code = 'EXCHANGE_RATE_VOLATILITY_MONITOR' AND deleted = 0
);

-- 系统任务身份不可变，保留运维人员已维护的 Cron、超时、重试和启停状态。
UPDATE sys_job_task
SET job_name = '汇率波动预警评估任务',
    job_group = 'exchange',
    handler_code = 'exchangeRateVolatilityMonitorJob',
    scheduler_mode = 'DISTRIBUTED',
    execute_mode = 'SYNC',
    allow_concurrent = 0,
    description = '每5分钟调用 service-admin 幂等评估交易汇率和结算汇率波动；系统任务禁止删除或更换处理器。',
    update_by = 'system-migration',
    update_time = NOW(3)
WHERE job_code = 'EXCHANGE_RATE_VOLATILITY_MONITOR'
  AND deleted = 0
  AND (
      job_name <> '汇率波动预警评估任务'
      OR job_group <> 'exchange'
      OR handler_code <> 'exchangeRateVolatilityMonitorJob'
      OR scheduler_mode <> 'DISTRIBUTED'
      OR execute_mode <> 'SYNC'
      OR allow_concurrent <> 0
      OR description <> '每5分钟调用 service-admin 幂等评估交易汇率和结算汇率波动；系统任务禁止删除或更换处理器。'
  );

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT 1, parent.id, child.menu_code, child.menu_name, 'MENU', child.route_path,
       child.component_path, child.permission_code, child.icon, 1, 0, 0, child.sort_no, 1, 0
FROM sys_menu parent
JOIN (
    SELECT 'exchange_rate_volatility' menu_code, '汇率波动' menu_name,
           '/exchange/volatility' route_path, 'exchange/volatility' component_path,
           'exchange:volatility:list' permission_code, 'DataLine' icon, 7 sort_no
    UNION ALL SELECT 'exchange_rate_alert_event', '汇率预警事件',
           '/exchange/alert-event', 'exchange/alert-event',
           'exchange:alert-event:list', 'Warning' icon, 8
    UNION ALL SELECT 'exchange_rate_alert_rule', '汇率预警规则',
           '/exchange/alert-rule', 'exchange/alert-rule',
           'exchange:alert-rule:list', 'Bell' icon, 9
) child ON parent.app_id = 1 AND parent.menu_code = 'exchange' AND parent.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu current_menu
    WHERE current_menu.app_id = 1 AND current_menu.menu_code = child.menu_code AND current_menu.deleted = 0
);

UPDATE sys_menu
SET menu_name = CASE menu_code
    WHEN 'exchange_rate_volatility' THEN '汇率波动'
    WHEN 'exchange_rate_alert_event' THEN '汇率预警事件'
    WHEN 'exchange_rate_alert_rule' THEN '汇率预警规则'
END
WHERE app_id = 1
  AND deleted = 0
  AND menu_code IN ('exchange_rate_volatility', 'exchange_rate_alert_event', 'exchange_rate_alert_rule');

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT 1, parent.id, button.menu_code, button.menu_name, 'BUTTON', NULL, NULL,
       button.permission_code, NULL, 0, 0, 0, button.sort_no, 1, 0
FROM sys_menu parent
JOIN (
    SELECT 'exchange_rate_volatility' parent_code, 'exchange_rate_volatility_query' menu_code, '汇率波动详情' menu_name, 'exchange:volatility:query' permission_code, 101 sort_no
    UNION ALL SELECT 'exchange_rate_volatility', 'exchange_rate_volatility_export', '汇率波动导出', 'exchange:volatility:export', 102
    UNION ALL SELECT 'exchange_rate_alert_event', 'exchange_rate_alert_event_query', '汇率预警事件详情', 'exchange:alert-event:query', 101
    UNION ALL SELECT 'exchange_rate_alert_event', 'exchange_rate_alert_event_ack', '汇率预警事件确认', 'exchange:alert-event:ack', 102
    UNION ALL SELECT 'exchange_rate_alert_event', 'exchange_rate_alert_event_close', '汇率预警事件关闭', 'exchange:alert-event:close', 103
    UNION ALL SELECT 'exchange_rate_alert_event', 'exchange_rate_alert_event_export', '汇率预警事件导出', 'exchange:alert-event:export', 104
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange_rate_alert_rule_query', '汇率预警规则详情', 'exchange:alert-rule:query', 101
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange_rate_alert_rule_add', '汇率预警规则新增', 'exchange:alert-rule:add', 102
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange_rate_alert_rule_edit', '汇率预警规则修改', 'exchange:alert-rule:edit', 103
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange_rate_alert_rule_remove', '汇率预警规则删除', 'exchange:alert-rule:remove', 104
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange_rate_alert_rule_start', '汇率预警规则启用', 'exchange:alert-rule:start', 105
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange_rate_alert_rule_stop', '汇率预警规则停用', 'exchange:alert-rule:stop', 106
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange_rate_alert_rule_import', '汇率预警规则导入', 'exchange:alert-rule:import', 107
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange_rate_alert_rule_export', '汇率预警规则导出', 'exchange:alert-rule:export', 108
) button ON button.parent_code = parent.menu_code
WHERE parent.app_id = 1 AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu current_menu
      WHERE current_menu.app_id = 1 AND current_menu.menu_code = button.menu_code AND current_menu.deleted = 0
  );

UPDATE sys_menu
SET menu_name = CASE menu_code
    WHEN 'exchange_rate_volatility_query' THEN '汇率波动详情'
    WHEN 'exchange_rate_volatility_export' THEN '汇率波动导出'
    WHEN 'exchange_rate_alert_event_query' THEN '汇率预警事件详情'
    WHEN 'exchange_rate_alert_event_ack' THEN '汇率预警事件确认'
    WHEN 'exchange_rate_alert_event_close' THEN '汇率预警事件关闭'
    WHEN 'exchange_rate_alert_event_export' THEN '汇率预警事件导出'
    WHEN 'exchange_rate_alert_rule_query' THEN '汇率预警规则详情'
    WHEN 'exchange_rate_alert_rule_add' THEN '汇率预警规则新增'
    WHEN 'exchange_rate_alert_rule_edit' THEN '汇率预警规则修改'
    WHEN 'exchange_rate_alert_rule_remove' THEN '汇率预警规则删除'
    WHEN 'exchange_rate_alert_rule_start' THEN '汇率预警规则启用'
    WHEN 'exchange_rate_alert_rule_stop' THEN '汇率预警规则停用'
    WHEN 'exchange_rate_alert_rule_import' THEN '汇率预警规则导入'
    WHEN 'exchange_rate_alert_rule_export' THEN '汇率预警规则导出'
END
WHERE app_id = 1
  AND deleted = 0
  AND menu_code IN (
      'exchange_rate_volatility_query', 'exchange_rate_volatility_export',
      'exchange_rate_alert_event_query', 'exchange_rate_alert_event_ack',
      'exchange_rate_alert_event_close', 'exchange_rate_alert_event_export',
      'exchange_rate_alert_rule_query', 'exchange_rate_alert_rule_add',
      'exchange_rate_alert_rule_edit', 'exchange_rate_alert_rule_remove',
      'exchange_rate_alert_rule_start', 'exchange_rate_alert_rule_stop',
      'exchange_rate_alert_rule_import', 'exchange_rate_alert_rule_export'
  );

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT 1, menu.id, permission.permission_code, permission.permission_name,
       permission.permission_type, permission.resource_method, permission.resource_path,
       permission.permission_name, 1, 0
FROM (
    SELECT 'exchange_rate_volatility' menu_code, 'exchange:volatility:list' permission_code, '汇率波动查询' permission_name, 'MENU' permission_type, 'POST' resource_method, '/admin/exchange/volatility/overview' resource_path
    UNION ALL SELECT 'exchange_rate_volatility_query', 'exchange:volatility:query', '汇率波动详情', 'BUTTON', 'POST', '/admin/exchange/volatility/trend'
    UNION ALL SELECT 'exchange_rate_volatility_export', 'exchange:volatility:export', '汇率波动导出', 'BUTTON', 'POST', '/admin/exchange/volatility/export'
    UNION ALL SELECT 'exchange_rate_alert_event', 'exchange:alert-event:list', '汇率预警事件查询', 'MENU', 'POST', '/admin/exchange/alert-events/search'
    UNION ALL SELECT 'exchange_rate_alert_event_query', 'exchange:alert-event:query', '汇率预警事件详情', 'BUTTON', 'GET', '/admin/exchange/alert-events/*'
    UNION ALL SELECT 'exchange_rate_alert_event_ack', 'exchange:alert-event:ack', '汇率预警事件确认', 'BUTTON', 'PUT', '/admin/exchange/alert-events/*/acknowledge'
    UNION ALL SELECT 'exchange_rate_alert_event_close', 'exchange:alert-event:close', '汇率预警事件关闭', 'BUTTON', 'PUT', '/admin/exchange/alert-events/*/close'
    UNION ALL SELECT 'exchange_rate_alert_event_export', 'exchange:alert-event:export', '汇率预警事件导出', 'BUTTON', 'POST', '/admin/exchange/alert-events/export'
    UNION ALL SELECT 'exchange_rate_alert_rule', 'exchange:alert-rule:list', '汇率预警规则查询', 'MENU', 'POST', '/admin/exchange/alert-rules/search'
    UNION ALL SELECT 'exchange_rate_alert_rule_query', 'exchange:alert-rule:query', '汇率预警规则详情', 'BUTTON', 'GET', '/admin/exchange/alert-rules/*'
    UNION ALL SELECT 'exchange_rate_alert_rule_add', 'exchange:alert-rule:add', '汇率预警规则新增', 'BUTTON', 'POST', '/admin/exchange/alert-rules'
    UNION ALL SELECT 'exchange_rate_alert_rule_edit', 'exchange:alert-rule:edit', '汇率预警规则修改', 'BUTTON', 'PUT', '/admin/exchange/alert-rules/*'
    UNION ALL SELECT 'exchange_rate_alert_rule_remove', 'exchange:alert-rule:remove', '汇率预警规则删除', 'BUTTON', 'DELETE', '/admin/exchange/alert-rules/*'
    UNION ALL SELECT 'exchange_rate_alert_rule_start', 'exchange:alert-rule:start', '汇率预警规则启用', 'BUTTON', 'PUT', '/admin/exchange/alert-rules/*/enable'
    UNION ALL SELECT 'exchange_rate_alert_rule_stop', 'exchange:alert-rule:stop', '汇率预警规则停用', 'BUTTON', 'PUT', '/admin/exchange/alert-rules/*/disable'
    UNION ALL SELECT 'exchange_rate_alert_rule_import', 'exchange:alert-rule:import', '汇率预警规则导入', 'BUTTON', 'POST', '/admin/exchange/alert-rules/import'
    UNION ALL SELECT 'exchange_rate_alert_rule_export', 'exchange:alert-rule:export', '汇率预警规则导出', 'BUTTON', 'POST', '/admin/exchange/alert-rules/export'
) permission
JOIN sys_menu menu ON menu.app_id = 1 AND menu.menu_code = permission.menu_code AND menu.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission current_permission
    WHERE current_permission.app_id = 1
      AND current_permission.permission_code = permission.permission_code
      AND current_permission.deleted = 0
);

UPDATE sys_permission current_permission
JOIN (
    SELECT 'exchange:volatility:list' permission_code, '汇率波动查询' permission_name
    UNION ALL SELECT 'exchange:volatility:query', '汇率波动详情'
    UNION ALL SELECT 'exchange:volatility:export', '汇率波动导出'
    UNION ALL SELECT 'exchange:alert-event:list', '汇率预警事件查询'
    UNION ALL SELECT 'exchange:alert-event:query', '汇率预警事件详情'
    UNION ALL SELECT 'exchange:alert-event:ack', '汇率预警事件确认'
    UNION ALL SELECT 'exchange:alert-event:close', '汇率预警事件关闭'
    UNION ALL SELECT 'exchange:alert-event:export', '汇率预警事件导出'
    UNION ALL SELECT 'exchange:alert-rule:list', '汇率预警规则查询'
    UNION ALL SELECT 'exchange:alert-rule:query', '汇率预警规则详情'
    UNION ALL SELECT 'exchange:alert-rule:add', '汇率预警规则新增'
    UNION ALL SELECT 'exchange:alert-rule:edit', '汇率预警规则修改'
    UNION ALL SELECT 'exchange:alert-rule:remove', '汇率预警规则删除'
    UNION ALL SELECT 'exchange:alert-rule:start', '汇率预警规则启用'
    UNION ALL SELECT 'exchange:alert-rule:stop', '汇率预警规则停用'
    UNION ALL SELECT 'exchange:alert-rule:import', '汇率预警规则导入'
    UNION ALL SELECT 'exchange:alert-rule:export', '汇率预警规则导出'
) expected ON expected.permission_code = current_permission.permission_code
SET current_permission.permission_name = expected.permission_name,
    current_permission.description = expected.permission_name
WHERE current_permission.app_id = 1
  AND current_permission.deleted = 0;

-- 本地管理后台默认角色授权；生产环境如采用精细化角色，应按权限中心页面人工分配。
INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 1, role.id, menu.id, 0
FROM sys_role role
JOIN sys_menu menu ON menu.app_id = role.app_id AND menu.menu_code LIKE 'exchange%' AND menu.deleted = 0
WHERE role.app_id = 1
  AND role.role_code = 'ADMIN_OPERATOR'
  AND role.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 1, role.id, permission.id, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id AND permission.permission_code LIKE 'exchange:%' AND permission.deleted = 0
WHERE role.app_id = 1
  AND role.role_code = 'ADMIN_OPERATOR'
  AND role.deleted = 0;
