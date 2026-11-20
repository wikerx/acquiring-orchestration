-- REQ-001 V1.5 支付方式申请、授权和审核事实的扩展迁移。
-- 运行前确认目标库实际结构、旧审核读取范围和两张新表不存在。本文件由发布负责人按窗口执行。
-- MySQL DDL 会单独提交。中断后按表核对已执行步骤，只继续未执行的步骤。
-- 兼容回退保留结构与业务记录，先停用新写入。已经生效的授权限制仍由兼容版本保护。

CREATE TABLE merchant_payment_application_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '支付方式申请明细主键',
    application_no VARCHAR(40) NOT NULL COMMENT '同批申请的全局业务编号',
    merchant_id VARCHAR(32) NOT NULL COMMENT '所属商户编号',
    item_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范支付方式键',
    operation VARCHAR(16) NOT NULL COMMENT '首次申请或终止后的重新申请类型',
    status VARCHAR(32) NOT NULL COMMENT '当前申请明细状态',
    round_no INT NOT NULL DEFAULT 1 COMMENT '补充后重新审核的轮次',
    source VARCHAR(24) NOT NULL COMMENT '商户自助申请或平台代申请来源',
    create_request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建批次的原始幂等请求键',
    initial_submitter_app_code VARCHAR(32) NOT NULL COMMENT '首次提交者所属应用编码',
    initial_submitter_account_id BIGINT NOT NULL COMMENT '首次提交者可信登录账号主键',
    initial_submitter_user_id BIGINT NOT NULL COMMENT '首次提交者可信自然人主体主键',
    current_submitter_app_code VARCHAR(32) NOT NULL COMMENT '当前审核轮次提交者所属应用编码',
    current_submitter_account_id BIGINT NOT NULL COMMENT '当前审核轮次提交者可信登录账号主键',
    current_submitter_user_id BIGINT NOT NULL COMMENT '当前审核轮次提交者可信自然人主体主键',
    reason VARCHAR(1000) NULL COMMENT '申请或补充的说明',
    expected_grant_version BIGINT NOT NULL DEFAULT 0 COMMENT '申请时的授权版本，零表示当时无授权',
    active_slot TINYINT NULL COMMENT '待审核或退回补充为一，终态为空',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '当前申请明细的乐观锁版本',
    submitted_at DATETIME(3) NOT NULL COMMENT '当前审核轮次的提交时刻',
    closed_at DATETIME(3) NULL COMMENT '申请进入终态的时刻',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '申请明细创建时刻',
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '申请明细最后更新时刻',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mpai_active (merchant_id, item_key, active_slot),
    UNIQUE KEY uk_mpai_create_request (merchant_id, create_request_id, item_key),
    KEY idx_mpai_application (merchant_id, application_no, id),
    KEY idx_mpai_application_global (application_no, merchant_id, id),
    KEY idx_mpai_merchant_created (merchant_id, gmt_create, id),
    KEY idx_mpai_review_queue (status, submitted_at, id),
    CONSTRAINT chk_mpai_active CHECK (
        (status IN ('PENDING_REVIEW', 'RETURNED') AND active_slot IS NOT NULL AND active_slot = 1 AND closed_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED', 'WITHDRAWN') AND active_slot IS NULL AND closed_at IS NOT NULL)
    ),
    CONSTRAINT chk_mpai_operation CHECK (
        (operation = 'APPLY' AND expected_grant_version = 0)
        OR (operation = 'REAPPLY' AND expected_grant_version > 0)
    ),
    CONSTRAINT chk_mpai_version CHECK (round_no > 0 AND version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户支付方式申请明细';

CREATE TABLE merchant_payment_authorization (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '商户支付方式授权主键',
    merchant_id VARCHAR(32) NOT NULL COMMENT '被授权商户编号',
    item_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范支付方式键',
    authorization_status VARCHAR(16) NOT NULL COMMENT '当前授权持久化状态',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '当前授权的乐观锁版本',
    source VARCHAR(24) NOT NULL COMMENT '申请批准或历史核验的授权来源',
    source_ref VARCHAR(64) NOT NULL COMMENT '申请明细主键或历史基线批次引用',
    approved_app_code VARCHAR(32) NULL COMMENT '真实申请批准者所属应用编码',
    approved_account_id BIGINT NULL COMMENT '真实申请批准者的可信登录账号主键',
    approved_user_id BIGINT NULL COMMENT '真实申请批准者的可信自然人主体主键',
    effective_time DATETIME(3) NOT NULL COMMENT '批准后的授权生效时间',
    expire_time DATETIME(3) NULL COMMENT '授权失效边界，为空表示长期有效',
    suspended_time DATETIME(3) NULL COMMENT '本次暂停的时间',
    terminated_time DATETIME(3) NULL COMMENT '本次终止的时间',
    last_review_record_id BIGINT NOT NULL COMMENT '上一次授权变更或核验的不可变审核记录主键',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权记录创建时刻',
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '授权记录最后更新时刻',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mpa_merchant_method (merchant_id, item_key),
    KEY idx_mpa_effective_time (effective_time, id),
    KEY idx_mpa_expire_time (expire_time, id),
    CONSTRAINT chk_mpa_state CHECK (authorization_status IN ('ACTIVE', 'SUSPENDED', 'TERMINATED')),
    CONSTRAINT chk_mpa_source CHECK (
        (source = 'APPLICATION' AND approved_app_code IS NOT NULL AND approved_account_id IS NOT NULL AND approved_user_id IS NOT NULL)
        OR (source = 'HISTORICAL_BASELINE' AND approved_app_code IS NULL AND approved_account_id IS NULL AND approved_user_id IS NULL)
    ),
    CONSTRAINT chk_mpa_version CHECK (version > 0),
    CONSTRAINT chk_mpa_time CHECK (expire_time IS NULL OR expire_time > effective_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户当前支付方式授权';

ALTER TABLE base_merchant_info
    ADD COLUMN payment_authorization_revision BIGINT NOT NULL DEFAULT 0 COMMENT '当前商户支付方式授权及准入治理修订号',
    ADD COLUMN payment_admission_mode VARCHAR(24) NULL COMMENT '支付方式准入模式，由显式历史核验或切换命令维护',
    ADD COLUMN payment_baseline_batch_ref VARCHAR(64) NULL COMMENT '既有支付方式兼容范围的冻结批次引用',
    ADD COLUMN payment_admission_cutover_time DATETIME(3) NULL COMMENT '商户最近一次切换支付方式准入模式的时间';

ALTER TABLE merchant_review_record
    ADD COLUMN target_type VARCHAR(24) NULL COMMENT '支付方式申请批次、明细或准入治理的审核目标类型',
    ADD COLUMN target_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '审核目标的规范业务键',
    ADD COLUMN application_item_id BIGINT NULL COMMENT '关联的支付方式申请明细主键',
    ADD COLUMN round_no INT NULL COMMENT '本次审核对应的申请轮次',
    ADD COLUMN operator_app_code VARCHAR(32) NULL COMMENT '实际操作者所属应用编码',
    ADD COLUMN operator_user_id BIGINT NULL COMMENT '实际操作者可信自然人主体主键',
    ADD COLUMN command_request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '成功业务命令的幂等请求键',
    ADD COLUMN payload_hash BINARY(32) NULL COMMENT '按稳定语义规范化的业务命令指纹',
    ADD COLUMN result_json JSON NULL COMMENT '不可变的成功命令回执',
    ADD COLUMN fact_snapshot_json JSON NULL COMMENT '经白名单限制的审核前后事实和来源引用',
    ADD UNIQUE KEY uk_mrr_scope_merchant_cmd (review_scope, merchant_id, command_request_id),
    ADD KEY idx_mrr_method_item (review_scope, merchant_id, application_item_id, gmt_create, id);
