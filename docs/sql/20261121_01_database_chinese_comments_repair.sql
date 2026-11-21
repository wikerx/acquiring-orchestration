-- payment_acquiring 中文注释纠错；已于 2026-11-21 执行完成，作为迁移审计留档。
-- 共修复 18 张表的 79 处注释；14 表在线原地修复，另 4 表经用户单独批准后复制修复。
-- 完整备份已在独立库恢复验证；非注释元数据逐项一致，复制前后业务行数一致。
-- 本脚本已执行，禁止重复执行；后续变更应重新预检和按当次快照生成脚本。
SET NAMES utf8mb4;
USE payment_acquiring;
SET SESSION lock_wait_timeout=3;

ALTER TABLE `admin_operational_work_item`
  MODIFY COLUMN `source_basis_json` json DEFAULT NULL COMMENT '支付方式待办事实的来源版本、转移依据与去重世代',
  ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `base_merchant_info`
  MODIFY COLUMN `payment_authorization_revision` bigint NOT NULL DEFAULT '0' COMMENT '当前商户支付方式授权及准入治理修订号',
  MODIFY COLUMN `payment_admission_mode` varchar(24) DEFAULT NULL COMMENT '支付方式准入模式，由显式历史核验或切换命令维护',
  MODIFY COLUMN `payment_baseline_batch_ref` varchar(64) DEFAULT NULL COMMENT '既有支付方式兼容范围的冻结批次引用',
  MODIFY COLUMN `payment_admission_cutover_time` datetime(3) DEFAULT NULL COMMENT '商户最近一次切换支付方式准入模式的时间',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `merchant_payment_application_item`
  DROP CHECK `chk_mpai_operation`,
  DROP CHECK `chk_mpai_active`,
  COMMENT = '商户支付方式申请明细',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '支付方式申请明细主键',
  MODIFY COLUMN `application_no` varchar(40) NOT NULL COMMENT '同批申请的全局业务编号',
  MODIFY COLUMN `merchant_id` varchar(32) NOT NULL COMMENT '所属商户编号',
  MODIFY COLUMN `item_key` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范支付方式键',
  MODIFY COLUMN `operation` varchar(16) NOT NULL COMMENT '首次申请或终止后的重新申请类型',
  MODIFY COLUMN `status` varchar(32) NOT NULL COMMENT '当前申请明细状态',
  MODIFY COLUMN `round_no` int NOT NULL DEFAULT '1' COMMENT '补充后重新审核的轮次',
  MODIFY COLUMN `source` varchar(24) NOT NULL COMMENT '商户自助申请或平台代申请来源',
  MODIFY COLUMN `create_request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建批次的原始幂等请求键',
  MODIFY COLUMN `initial_submitter_app_code` varchar(32) NOT NULL COMMENT '首次提交者所属应用编码',
  MODIFY COLUMN `initial_submitter_account_id` bigint NOT NULL COMMENT '首次提交者可信登录账号主键',
  MODIFY COLUMN `initial_submitter_user_id` bigint NOT NULL COMMENT '首次提交者可信自然人主体主键',
  MODIFY COLUMN `current_submitter_app_code` varchar(32) NOT NULL COMMENT '当前审核轮次提交者所属应用编码',
  MODIFY COLUMN `current_submitter_account_id` bigint NOT NULL COMMENT '当前审核轮次提交者可信登录账号主键',
  MODIFY COLUMN `current_submitter_user_id` bigint NOT NULL COMMENT '当前审核轮次提交者可信自然人主体主键',
  MODIFY COLUMN `reason` varchar(1000) DEFAULT NULL COMMENT '申请或补充的说明',
  MODIFY COLUMN `expected_grant_version` bigint NOT NULL DEFAULT '0' COMMENT '申请时的授权版本，零表示当时无授权',
  MODIFY COLUMN `active_slot` tinyint DEFAULT NULL COMMENT '待审核或退回补充为一，终态为空',
  MODIFY COLUMN `version` bigint NOT NULL DEFAULT '1' COMMENT '当前申请明细的乐观锁版本',
  MODIFY COLUMN `submitted_at` datetime(3) NOT NULL COMMENT '当前审核轮次的提交时刻',
  MODIFY COLUMN `closed_at` datetime(3) DEFAULT NULL COMMENT '申请进入终态的时刻',
  MODIFY COLUMN `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '申请明细创建时刻',
  MODIFY COLUMN `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '申请明细最后更新时刻',
  ADD CONSTRAINT `chk_mpai_active` CHECK ((((`status` in (_latin1'PENDING_REVIEW',_latin1'RETURNED')) and (`active_slot` is not null) and (`active_slot` = 1) and (`closed_at` is null)) or ((`status` in (_latin1'APPROVED',_latin1'REJECTED',_latin1'WITHDRAWN')) and (`active_slot` is null) and (`closed_at` is not null)))),
  ADD CONSTRAINT `chk_mpai_operation` CHECK ((((`operation` = _latin1'APPLY') and (`expected_grant_version` = 0)) or ((`operation` = _latin1'REAPPLY') and (`expected_grant_version` > 0)))),
  ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `merchant_payment_authorization`
  DROP CHECK `chk_mpa_state`,
  DROP CHECK `chk_mpa_source`,
  COMMENT = '商户当前支付方式授权',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商户支付方式授权主键',
  MODIFY COLUMN `merchant_id` varchar(32) NOT NULL COMMENT '被授权商户编号',
  MODIFY COLUMN `item_key` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范支付方式键',
  MODIFY COLUMN `authorization_status` varchar(16) NOT NULL COMMENT '当前授权持久化状态',
  MODIFY COLUMN `version` bigint NOT NULL DEFAULT '1' COMMENT '当前授权的乐观锁版本',
  MODIFY COLUMN `source` varchar(24) NOT NULL COMMENT '申请批准或历史核验的授权来源',
  MODIFY COLUMN `source_ref` varchar(64) NOT NULL COMMENT '申请明细主键或历史基线批次引用',
  MODIFY COLUMN `approved_app_code` varchar(32) DEFAULT NULL COMMENT '真实申请批准者所属应用编码',
  MODIFY COLUMN `approved_account_id` bigint DEFAULT NULL COMMENT '真实申请批准者的可信登录账号主键',
  MODIFY COLUMN `approved_user_id` bigint DEFAULT NULL COMMENT '真实申请批准者的可信自然人主体主键',
  MODIFY COLUMN `effective_time` datetime(3) NOT NULL COMMENT '批准后的授权生效时间',
  MODIFY COLUMN `expire_time` datetime(3) DEFAULT NULL COMMENT '授权失效边界，为空表示长期有效',
  MODIFY COLUMN `suspended_time` datetime(3) DEFAULT NULL COMMENT '本次暂停的时间',
  MODIFY COLUMN `terminated_time` datetime(3) DEFAULT NULL COMMENT '本次终止的时间',
  MODIFY COLUMN `last_review_record_id` bigint NOT NULL COMMENT '上一次授权变更或核验的不可变审核记录主键',
  MODIFY COLUMN `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权记录创建时刻',
  MODIFY COLUMN `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '授权记录最后更新时刻',
  ADD CONSTRAINT `chk_mpa_source` CHECK ((((`source` = _latin1'APPLICATION') and (`approved_app_code` is not null) and (`approved_account_id` is not null) and (`approved_user_id` is not null)) or ((`source` = _latin1'HISTORICAL_BASELINE') and (`approved_app_code` is null) and (`approved_account_id` is null) and (`approved_user_id` is null)))),
  ADD CONSTRAINT `chk_mpa_state` CHECK ((`authorization_status` in (_latin1'ACTIVE',_latin1'SUSPENDED',_latin1'TERMINATED'))),
  ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `merchant_review_record`
  MODIFY COLUMN `target_type` varchar(24) DEFAULT NULL COMMENT '支付方式申请批次、明细或准入治理的审核目标类型',
  MODIFY COLUMN `target_key` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '审核目标的规范业务键',
  MODIFY COLUMN `application_item_id` bigint DEFAULT NULL COMMENT '关联的支付方式申请明细主键',
  MODIFY COLUMN `round_no` int DEFAULT NULL COMMENT '本次审核对应的申请轮次',
  MODIFY COLUMN `operator_app_code` varchar(32) DEFAULT NULL COMMENT '实际操作者所属应用编码',
  MODIFY COLUMN `operator_user_id` bigint DEFAULT NULL COMMENT '实际操作者可信自然人主体主键',
  MODIFY COLUMN `command_request_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '成功业务命令的幂等请求键',
  MODIFY COLUMN `payload_hash` binary(32) DEFAULT NULL COMMENT '按稳定语义规范化的业务命令指纹',
  MODIFY COLUMN `result_json` json DEFAULT NULL COMMENT '不可变的成功命令回执',
  MODIFY COLUMN `fact_snapshot_json` json DEFAULT NULL COMMENT '经白名单限制的审核前后事实和来源引用',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `msg_email_send_record`
  MODIFY COLUMN `business_notification_key` binary(32) DEFAULT NULL COMMENT '稳定业务通知的幂等键，旧通知为空',
  MODIFY COLUMN `business_notification_payload_hash` binary(32) DEFAULT NULL COMMENT '通知受众与白名单内容的规范摘要',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `payment_link`
  MODIFY COLUMN `allowed_methods_json` json DEFAULT NULL COMMENT '带版本的链接方式范围，空值表示沿用历史银行卡品牌范围',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `risk_merchant_limit_reservation`
  MODIFY COLUMN `payment_preparation_key` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'Payment 内部准备动作的稳定摘要',
  MODIFY COLUMN `payment_preparation_generation` bigint DEFAULT NULL COMMENT '当前预占独占的 Payment 准备代次',
  MODIFY COLUMN `payment_transaction_date_time` datetime(3) DEFAULT NULL COMMENT 'Payment 原动作时间，用于定位交易分片',
  ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `sys_job_run_log`
  MODIFY COLUMN `lease_epoch` bigint DEFAULT NULL COMMENT '本次运行对应的任务租约世代，旧记录为空',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `sys_job_task`
  MODIFY COLUMN `checkpoint_json` json DEFAULT NULL COMMENT '来源独立的有界扫描检查点',
  MODIFY COLUMN `checkpoint_version` bigint NOT NULL DEFAULT '0' COMMENT '任务检查点的乐观锁版本',
  MODIFY COLUMN `lease_epoch` bigint NOT NULL DEFAULT '0' COMMENT '每次有效任务抢占的单调世代',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_merchant_api_interaction_log`
  MODIFY COLUMN `transaction_id` varchar(64) DEFAULT NULL COMMENT '真实系统订单标识，建单前拒绝时为空',
  MODIFY COLUMN `admission_context_json` json DEFAULT NULL COMMENT '建单前后的规范方式、稳定尝试及受理证据摘要',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_merchant_api_interaction_log_202603`
  MODIFY COLUMN `transaction_id` varchar(64) DEFAULT NULL COMMENT '真实系统订单标识，建单前拒绝时为空',
  MODIFY COLUMN `admission_context_json` json DEFAULT NULL COMMENT '建单前后的规范方式、稳定尝试及受理证据摘要',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_merchant_api_interaction_log_202604`
  MODIFY COLUMN `transaction_id` varchar(64) DEFAULT NULL COMMENT '真实系统订单标识，建单前拒绝时为空',
  MODIFY COLUMN `admission_context_json` json DEFAULT NULL COMMENT '建单前后的规范方式、稳定尝试及受理证据摘要',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_merchant_api_interaction_log_202701`
  MODIFY COLUMN `transaction_id` varchar(64) DEFAULT NULL COMMENT '真实系统订单标识，建单前拒绝时为空',
  MODIFY COLUMN `admission_context_json` json DEFAULT NULL COMMENT '建单前后的规范方式、稳定尝试及受理证据摘要',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_merchant_snapshot`
  MODIFY COLUMN `payment_admission_snapshot_json` json DEFAULT NULL COMMENT '首次受理时的规范方式、授权费率及执行引用的白名单快照',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_merchant_snapshot_202603`
  MODIFY COLUMN `payment_admission_snapshot_json` json DEFAULT NULL COMMENT '首次受理时的规范方式、授权费率及执行引用的白名单快照',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_merchant_snapshot_202604`
  MODIFY COLUMN `payment_admission_snapshot_json` json DEFAULT NULL COMMENT '首次受理时的规范方式、授权费率及执行引用的白名单快照',
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_merchant_snapshot_202701`
  MODIFY COLUMN `payment_admission_snapshot_json` json DEFAULT NULL COMMENT '首次受理时的规范方式、授权费率及执行引用的白名单快照',
  ALGORITHM=INPLACE, LOCK=NONE;
