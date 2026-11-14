-- 系统交易加黑补充迁移回滚脚本，仅回滚本补充迁移引入的字段、索引和注释。
-- 回滚会丢失批次关联和操作账号归属，请确认数据已归档。

ALTER TABLE risk_trade_black_batch
    DROP INDEX uk_trade_black_request_account,
    ADD UNIQUE KEY uk_trade_black_request_deleted (request_id, deleted),
    DROP COLUMN operator_account_id,
    DROP COLUMN request_fingerprint;

ALTER TABLE risk_trade_black_record
    DROP INDEX idx_trade_black_batch_candidate,
    DROP INDEX idx_trade_black_update_time,
    DROP COLUMN candidate_key,
    DROP COLUMN batch_no,
    MODIFY COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL'
        COMMENT '来源类型：MANUAL、BATCH、SYSTEM',
    MODIFY COLUMN black_target_type VARCHAR(64) NOT NULL
        COMMENT '加黑对象类型：CARD、CARD_FINGERPRINT、EMAIL、PHONE、IP、DEVICE、CUSTOMER';
