-- 商户来源网址限定按 merchant_id + source_host 收敛迁移草案。
-- 执行前请先备份 risk_rule_source_url，并确认管理端代码已发布到不再读写 merchant_scope、rule_name、match_mode、match_value。
-- 本脚本不应直接在生产执行；请先运行重复检查 SQL，处理同商户同 host 的历史重复数据。

SET @table_name = 'risk_rule_source_url';

ALTER TABLE risk_rule_source_url
    ADD COLUMN source_url VARCHAR(512) NULL COMMENT '商户录入来源网址，必须以http://或https://开头' AFTER merchant_id,
    ADD COLUMN source_host VARCHAR(255) NULL COMMENT '来源网址Host，交易链路按商户号和Host匹配' AFTER source_url;

UPDATE risk_rule_source_url
SET source_url = COALESCE(source_url, match_value),
    source_host = LOWER(
        CASE
            WHEN match_value LIKE 'http://%' OR match_value LIKE 'https://%'
                THEN SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(match_value, '://', -1), '/', 1), ':', 1)
            ELSE match_value
        END
    )
WHERE deleted = 0;

-- 重复数据检查：如果有结果，必须先人工合并或删除重复项，否则唯一索引会创建失败。
SELECT merchant_id, source_host, COUNT(1) duplicate_count
FROM risk_rule_source_url
WHERE deleted = 0
GROUP BY merchant_id, source_host
HAVING COUNT(1) > 1;

-- 数据质量检查：如果有结果，必须先补齐商户号、URL 或 host。
SELECT id, merchant_id, source_url, source_host
FROM risk_rule_source_url
WHERE deleted = 0
  AND (merchant_id IS NULL OR merchant_id = '' OR source_url IS NULL OR source_url = '' OR source_host IS NULL OR source_host = '');

ALTER TABLE risk_rule_source_url
    MODIFY merchant_id VARCHAR(32) NOT NULL COMMENT '商户号，来源网址限定按商户直接生效',
    MODIFY source_url VARCHAR(512) NOT NULL COMMENT '商户录入来源网址，必须以http://或https://开头',
    MODIFY source_host VARCHAR(255) NOT NULL COMMENT '来源网址Host，交易链路按商户号和Host匹配';

CREATE UNIQUE INDEX uk_rule_source_url_merchant_host_deleted
    ON risk_rule_source_url (merchant_id, source_host, deleted);

CREATE INDEX idx_rule_source_url_trade_lookup
    ON risk_rule_source_url (merchant_id, source_host, status, deleted, effective_time, expire_time);

CREATE INDEX idx_rule_source_url_merchant_time
    ON risk_rule_source_url (merchant_id, update_time, id);

-- 当前来源网址限定已按 merchant_id + source_host 独立生效，以下旧规则模板字段不再读取。
ALTER TABLE risk_rule_source_url
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
