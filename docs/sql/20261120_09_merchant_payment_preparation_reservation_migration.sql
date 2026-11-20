-- REQ-001 D01，仅增加可空关联。先核对目标表和备份，扩表成功后再部署读取新增列的 Risk。
-- 旧记录全部为 NULL，继续采用原对账方式。启用准备门禁前应验证 Risk 到 Payment 的签名回读。
ALTER TABLE risk_merchant_limit_reservation
    ADD COLUMN payment_preparation_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Payment 内部准备动作的稳定摘要',
    ADD COLUMN payment_preparation_generation BIGINT NULL COMMENT '当前预占独占的 Payment 准备代次',
    ADD COLUMN payment_transaction_date_time DATETIME(3) NULL COMMENT 'Payment 原动作时间，用于定位交易分片',
    ADD CONSTRAINT chk_risk_limit_reservation_preparation CHECK (
        (payment_preparation_key IS NULL AND payment_preparation_generation IS NULL AND payment_transaction_date_time IS NULL)
        OR (payment_preparation_key IS NOT NULL AND payment_preparation_generation IS NOT NULL
            AND payment_preparation_generation > 0 AND payment_transaction_date_time IS NOT NULL)
    );

-- 回退保留列与准备归属事实。确认受保护在途事实全部处理前，不应回退到仅按交易查空释放额度的版本。
