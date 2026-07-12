-- 来源网址 AML 按 source_host 收敛迁移草案。
-- 执行前请先备份 risk_aml_source_url，并人工处理同一 host 下的重复记录。

ALTER TABLE risk_aml_source_url
    ADD COLUMN source_host VARCHAR(255) NULL COMMENT '来源网址Host，交易和商户进件按全局Host匹配' AFTER match_value_cipher;

UPDATE risk_aml_source_url
SET source_host = LOWER(
        SUBSTRING_INDEX(
                SUBSTRING_INDEX(
                        REGEXP_REPLACE(match_value_masked, '^https?://', ''),
                        '/',
                        1
                ),
                ':',
                1
        )
    )
WHERE deleted = 0
  AND (source_host IS NULL OR source_host = '')
  AND match_value_masked REGEXP '^https?://';

SELECT source_host, COUNT(1) duplicate_count
FROM risk_aml_source_url
WHERE deleted = 0
  AND source_host IS NOT NULL
  AND source_host <> ''
GROUP BY source_host
HAVING COUNT(1) > 1;

SELECT id, match_value_masked, source_host
FROM risk_aml_source_url
WHERE deleted = 0
  AND (source_host IS NULL OR source_host = '');

CREATE UNIQUE INDEX uk_aml_source_url_host_deleted
    ON risk_aml_source_url (source_host, deleted);

CREATE INDEX idx_aml_source_url_host_lookup
    ON risk_aml_source_url (source_host, status, deleted, effective_time, expire_time);
