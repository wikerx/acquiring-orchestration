-- REQ-001 V1.5 支付方式待办、可靠通知与 Job 租约基础结构。
-- 先部署兼容版本。实际任务初始化及启用须在成功接通 Admin 签名服务与投递链后另行审批。

ALTER TABLE admin_operational_work_item
    ADD COLUMN source_basis_json JSON NULL COMMENT '支付方式待办事实的来源版本、转移依据与去重世代',
    DROP CHECK chk_admin_operational_work_item_state,
    ADD CONSTRAINT chk_admin_operational_work_item_state CHECK (((`source_type` in (_utf8mb4'REFUND_APPROVAL',_utf8mb4'SETTLEMENT_REVIEW',_utf8mb4'EXCHANGE_RATE_ALERT',_utf8mb4'MERCHANT_NOTIFICATION_FAILURE',_utf8mb4'CHANNEL_MATCH_ABNORMAL',_utf8mb4'CLEARING_BACKLOG',_utf8mb4'SETTLEMENT_BACKLOG',_utf8mb4'HIGH_RISK_EVENT',_utf8mb4'PAYMENT_METHOD_APPROVAL',_utf8mb4'PAYMENT_METHOD_CONFIGURATION')) and (`work_status` in (_utf8mb4'OPEN',_utf8mb4'PROCESSING',_utf8mb4'CLOSED')) and (`priority` in (_utf8mb4'CRITICAL',_utf8mb4'HIGH',_utf8mb4'NORMAL',_utf8mb4'LOW')) and (`version` >= 0) and (((`owner_account_id` is null) and (`owner_account_name` is null)) or ((`owner_account_id` is not null) and (`owner_account_name` is not null))) and (((`work_status` = _utf8mb4'CLOSED') and (`closed_at` is not null) and (`close_reason` is not null)) or ((`work_status` <> _utf8mb4'CLOSED') and (`closed_at` is null) and (`close_reason` is null)))));

ALTER TABLE msg_email_send_record
    ADD COLUMN business_notification_key BINARY(32) NULL COMMENT '稳定业务通知的幂等键，旧通知为空',
    ADD COLUMN business_notification_payload_hash BINARY(32) NULL COMMENT '通知受众与白名单内容的规范摘要',
    ADD UNIQUE KEY uk_email_send_record_biz_notif (business_notification_key);

ALTER TABLE sys_job_task
    ADD COLUMN checkpoint_json JSON NULL COMMENT '来源独立的有界扫描检查点',
    ADD COLUMN checkpoint_version BIGINT NOT NULL DEFAULT 0 COMMENT '任务检查点的乐观锁版本',
    ADD COLUMN lease_epoch BIGINT NOT NULL DEFAULT 0 COMMENT '每次有效任务抢占的单调世代';

ALTER TABLE sys_job_run_log
    ADD COLUMN lease_epoch BIGINT NULL COMMENT '本次运行对应的任务租约世代，旧记录为空';

ALTER TABLE merchant_channel_mid_binding
    ADD KEY idx_mcmb_effective_time (effective_time, id),
    ADD KEY idx_mcmb_expire_time (expire_time, id);

ALTER TABLE channel_mid_config
    ADD KEY idx_cmc_effective_time (effective_time, id),
    ADD KEY idx_cmc_expire_time (expire_time, id);
