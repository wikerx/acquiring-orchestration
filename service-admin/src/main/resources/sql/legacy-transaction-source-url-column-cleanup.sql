-- 清理商户来源网址限定与交易主单中已停用的兼容字段。
-- 执行前必须确认旧字段无非空数据，并备份目标表；DDL 会自动提交，不得与其他迁移混合执行。
-- 交易主单必须同时修改模板表和当前全部物理分表，避免 ShardingSphere 元数据不一致。

-- 来源网址限定已经按 merchant_id + source_host 生效，以下通用规则模板字段不再读写。
ALTER TABLE risk_rule_source_url
    DROP INDEX idx_risk_rule_scope,
    DROP INDEX idx_risk_rule_currency,
    DROP COLUMN merchant_scope,
    DROP COLUMN rule_name,
    DROP COLUMN match_mode,
    DROP COLUMN match_value,
    DROP COLUMN limit_type,
    DROP COLUMN amount_min,
    DROP COLUMN amount_max,
    DROP COLUMN currency,
    DROP COLUMN time_window_seconds,
    DROP COLUMN threshold_count,
    DROP COLUMN elements_json;

-- 交易生命周期已统一使用 operation_id、root_transaction_id 和 latest_transaction_id。
ALTER TABLE transaction_order
    DROP INDEX uk_transaction_id,
    DROP INDEX uk_root_operation_id,
    DROP COLUMN transaction_id,
    DROP COLUMN root_operation_id,
    DROP COLUMN latest_operation_id;

ALTER TABLE transaction_order_202603
    DROP INDEX uk_transaction_id,
    DROP INDEX uk_root_operation_id,
    DROP COLUMN transaction_id,
    DROP COLUMN root_operation_id,
    DROP COLUMN latest_operation_id;

ALTER TABLE transaction_order_202604
    DROP INDEX uk_transaction_id,
    DROP INDEX uk_root_operation_id,
    DROP COLUMN transaction_id,
    DROP COLUMN root_operation_id,
    DROP COLUMN latest_operation_id;
