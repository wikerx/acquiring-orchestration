-- 系统交易加黑补充迁移。
-- 仅适用于已经执行旧版工作流脚本、且尚无 request_fingerprint/operator_account_id 的存量环境。
-- 新环境直接执行当前 risk-management-schema.sql 或 risk-trade-black-workflow-migration.sql，不要再执行本脚本。
-- 执行前请先确认 risk_trade_black_batch.request_id 不存在重复有效记录，并完成数据库备份。

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE risk_trade_black_record
    MODIFY COLUMN black_target_type VARCHAR(64) NOT NULL
        COMMENT '黑名单功能编码：cardNo、cardFingerprint、cardBin、cardholderName、issuerCountry、deviceFingerprint、phone、email、emailUsername、emailDomain、ip、billingAddress、billingZip、billingCountry、shippingAddress、shippingZip、shippingCountry、region',
    MODIFY COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL'
        COMMENT '录入来源/方式：MANUAL、BATCH、SYSTEM、SINGLE、PASTE、FILE',
    ADD COLUMN batch_no VARCHAR(64) NULL
        COMMENT '系统交易加黑操作批次号，历史或手工记录允许为空' AFTER action_reason,
    ADD COLUMN candidate_key VARCHAR(64) NULL
        COMMENT '服务端候选项稳定键，禁止保存敏感明文' AFTER batch_no,
    ADD KEY idx_trade_black_update_time (update_time, id),
    ADD KEY idx_trade_black_batch_candidate (batch_no, candidate_key);

ALTER TABLE risk_trade_black_batch
    ADD COLUMN request_fingerprint CHAR(64) NULL
        COMMENT '规范化请求摘要；历史批次允许为空，新批次必须由应用写入' AFTER request_id,
    ADD COLUMN operator_account_id BIGINT NULL
        COMMENT '操作账号ID；历史批次允许为空，新批次必须由应用写入' AFTER failure_count,
    DROP INDEX uk_trade_black_request_deleted,
    ADD UNIQUE KEY uk_trade_black_request_account (operator_account_id, request_id);
