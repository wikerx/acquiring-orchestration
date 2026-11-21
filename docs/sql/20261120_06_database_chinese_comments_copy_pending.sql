-- payment_acquiring 中文数据库注释治理，仅注释变更。
-- 前置：已备份完整结构并在 MySQL 8.4.9 独立空库演练；逐项确认类型、默认值、生成表达式、索引、约束与备份一致。
-- 注意 DDL 隐式提交，不能用事务回滚。必须使用本次独立备份恢复注释，不回写业务记录。
USE `payment_acquiring`;
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=3;
-- 2026-11-20 本地已获用户明确批准并执行：21 张表逐表复制修复，完整备份已校验，全库后检零异常。
-- 历史文件名保留审批链接；任何重跑或其他环境执行，仍须先做结构预检、业务备份并确认暂停写入窗口。
-- 四个历史检查约束在同一条原子语句中按原名、原表达式重声明，以保留原字符集；不产生无约束的中间状态。
-- 不关闭约束、不删除数据；本文件禁止由启动流程自动执行。

ALTER TABLE `admin_operational_work_item`
    MODIFY COLUMN `work_status` varchar(16) NOT NULL DEFAULT 'OPEN' COMMENT '待办状态：OPEN待处理，PROCESSING处理中，CLOSED已关闭',
    MODIFY COLUMN `priority` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT '优先级：CRITICAL紧急，HIGH高，NORMAL普通，LOW低',
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `clearing_label_principal_repair`
    MODIFY COLUMN `repair_status` varchar(24) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '审批执行状态：PENDING_REVIEW待审批，EXECUTED已执行，REJECTED已拒绝',
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `clearing_reserve_adjustment`
    MODIFY COLUMN `adjustment_status` varchar(24) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '审批执行状态：PENDING_REVIEW待审批，EXECUTED已执行，REJECTED已拒绝',
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `clearing_tier_period_replay`
    MODIFY COLUMN `replay_status` varchar(24) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '重放状态：待审批、准备中、执行中、已完成、人工复核或已拒绝',
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `clearing_tier_period_replay_item`
    MODIFY COLUMN `item_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '明细处理状态：PENDING待处理，FAILED失败，COMPLETED已完成',
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `merchant_settlement_profile`
    MODIFY COLUMN `processing_mode` varchar(16) NOT NULL DEFAULT 'AUTO_POST' COMMENT '结算处理模式：AUTO_POST自动入账，AUTO_REVIEW自动选取后审批，MANUAL人工选择',
    MODIFY COLUMN `profile_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '结算档案状态：ACTIVE活动，RETIRED已退役，SUSPENDED已暂停',
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `settlement_batch`
    MODIFY COLUMN `batch_type` varchar(24) NOT NULL DEFAULT 'REGULAR' COMMENT '结算批次类型：常规、保证金释放、冲正或调整',
    MODIFY COLUMN `review_order_no` varchar(19) DEFAULT NULL COMMENT '结算预审单号',
    MODIFY COLUMN `create_mode` varchar(24) NOT NULL DEFAULT 'AUTO' COMMENT '结算创建模式，区分自动入账、自动预审和人工选择',
    MODIFY COLUMN `batch_status` varchar(24) NOT NULL DEFAULT 'CREATED' COMMENT '结算批次状态：已创建、认领中、已认领、已锁汇、计算中、已计算、入账中、已入账、可重试失败、人工复核、已取消、冲正中或已冲正',
    MODIFY COLUMN `projectable_candidate_count` int NOT NULL DEFAULT '0' COMMENT '可生成交易结算投影的清分候选数量',
    MODIFY COLUMN `result_fingerprint` char(64) DEFAULT NULL COMMENT '冻结财务计算结果的摘要指纹',
    MODIFY COLUMN `maker_account_id` bigint DEFAULT NULL COMMENT '申请人账号主键',
    MODIFY COLUMN `maker_account_name` varchar(128) DEFAULT NULL COMMENT '申请人账号名称快照',
    MODIFY COLUMN `maker_role_snapshot` varchar(1000) DEFAULT NULL COMMENT '申请人提交时的角色快照',
    MODIFY COLUMN `maker_client_ip` varchar(64) DEFAULT NULL COMMENT '申请客户端地址，仅用于受控审计',
    MODIFY COLUMN `maker_user_agent` varchar(500) DEFAULT NULL COMMENT '申请客户端用户代理信息',
    MODIFY COLUMN `maker_reason` varchar(400) DEFAULT NULL COMMENT '申请原因',
    MODIFY COLUMN `maker_time` datetime(3) DEFAULT NULL COMMENT '申请提交时间',
    MODIFY COLUMN `checker_account_id` bigint DEFAULT NULL COMMENT '复核人账号主键',
    MODIFY COLUMN `checker_account_name` varchar(128) DEFAULT NULL COMMENT '复核人账号名称快照',
    MODIFY COLUMN `checker_role_snapshot` varchar(1000) DEFAULT NULL COMMENT '复核人操作时的角色快照',
    MODIFY COLUMN `checker_client_ip` varchar(64) DEFAULT NULL COMMENT '复核客户端地址，仅用于受控审计',
    MODIFY COLUMN `checker_user_agent` varchar(500) DEFAULT NULL COMMENT '复核客户端用户代理信息',
    MODIFY COLUMN `checker_comment` varchar(400) DEFAULT NULL COMMENT '复核意见',
    MODIFY COLUMN `checker_time` datetime(3) DEFAULT NULL COMMENT '复核决定时间',
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `settlement_batch_candidate`
    MODIFY COLUMN `relation_status` varchar(24) NOT NULL DEFAULT 'CLAIMED' COMMENT '候选认领状态：已认领、已释放、已入账或人工复核',
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `settlement_candidate`
    MODIFY COLUMN `source_type` varchar(24) NOT NULL COMMENT '结算候选来源：清分修订、保证金释放或调整',
    MODIFY COLUMN `candidate_status` varchar(24) NOT NULL DEFAULT 'READY' COMMENT '候选状态：就绪、重放冻结、已替代、已认领、已入账、人工复核或已取消',
    MODIFY COLUMN `review_order_no` varchar(19) DEFAULT NULL COMMENT '结算预审单号',
    MODIFY COLUMN `review_locked_time` datetime(3) DEFAULT NULL COMMENT '候选被预审单独占锁定的时间',
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `settlement_event_outbox`
    MODIFY COLUMN `event_status` varchar(16) NOT NULL DEFAULT 'INIT' COMMENT '事件投递状态：初始、处理中、失败或已发送',
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `settlement_manual_review_task`
    DROP CHECK `chk_manual_review_task_value`,
    COMMENT = '冻结筛选条件并分段生成结算预审单的服务端任务',
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `task_no` varchar(40) NOT NULL COMMENT '持久化任务编号',
    MODIFY COLUMN `request_key` varchar(128) NOT NULL COMMENT '操作请求持久化幂等键',
    MODIFY COLUMN `submit_request_key` varchar(128) DEFAULT NULL COMMENT '提交结算预审单的请求幂等键',
    MODIFY COLUMN `review_order_no` varchar(19) NOT NULL COMMENT '结算预审单号',
    MODIFY COLUMN `review_type` varchar(24) NOT NULL COMMENT '结算预审类型，区分常规结算、保证金释放和调整',
    MODIFY COLUMN `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
    MODIFY COLUMN `settlement_profile_id` bigint NOT NULL COMMENT '冻结的商户结算档案主键',
    MODIFY COLUMN `settlement_account_id` bigint NOT NULL COMMENT '目标商户结算资金账户主键',
    MODIFY COLUMN `target_currency` char(3) NOT NULL COMMENT '目标结算币种代码',
    MODIFY COLUMN `target_currency_exponent` tinyint NOT NULL COMMENT '目标结算币种小数位',
    MODIFY COLUMN `payment_type` varchar(64) DEFAULT NULL COMMENT '支付业务类型',
    MODIFY COLUMN `payment_method` varchar(64) DEFAULT NULL COMMENT '收单支付方式',
    MODIFY COLUMN `business_date` date NOT NULL COMMENT '结算业务日期',
    MODIFY COLUMN `business_time_zone` varchar(64) NOT NULL COMMENT '解释结算业务时间的时区标识',
    MODIFY COLUMN `cutoff_begin_time` datetime(3) NOT NULL COMMENT '候选时间窗口的闭区间起点',
    MODIFY COLUMN `cutoff_end_time` datetime(3) NOT NULL COMMENT '候选时间窗口的开区间终点',
    MODIFY COLUMN `snapshot_max_candidate_id` bigint NOT NULL COMMENT '冻结查询时可见的候选最大主键',
    MODIFY COLUMN `expected_candidate_count` int NOT NULL COMMENT '预期纳入处理的候选数量',
    MODIFY COLUMN `processed_candidate_count` int NOT NULL DEFAULT '0' COMMENT '已经处理的候选数量',
    MODIFY COLUMN `locked_candidate_count` int NOT NULL DEFAULT '0' COMMENT '已经成功独占锁定的候选数量',
    MODIFY COLUMN `last_candidate_id` bigint NOT NULL DEFAULT '0' COMMENT '分段内最后一个候选主键',
    MODIFY COLUMN `initial_delay_unit` char(1) DEFAULT NULL COMMENT '首笔结算延迟的日期计算单位',
    MODIFY COLUMN `initial_delay_days` int DEFAULT NULL COMMENT '首笔结算延迟天数',
    MODIFY COLUMN `regular_delay_days` int DEFAULT NULL COMMENT '常规结算延迟天数',
    MODIFY COLUMN `settlement_frequency` varchar(16) DEFAULT NULL COMMENT '商户结算频率',
    MODIFY COLUMN `frequency_day` int DEFAULT NULL COMMENT '结算频率对应的周内日或月内日',
    MODIFY COLUMN `preview_json` json NOT NULL COMMENT '提交时冻结的预览结果快照',
    MODIFY COLUMN `task_status` varchar(24) NOT NULL COMMENT '任务处理状态',
    MODIFY COLUMN `submitted_by_account_id` bigint NOT NULL COMMENT '提交人账号主键',
    MODIFY COLUMN `submitted_by_account_name` varchar(128) NOT NULL COMMENT '提交人账号名称快照',
    MODIFY COLUMN `submitted_role_snapshot` varchar(1000) NOT NULL COMMENT '提交时的操作人角色快照',
    MODIFY COLUMN `submit_client_ip` varchar(64) NOT NULL COMMENT '提交客户端地址，仅用于受控审计',
    MODIFY COLUMN `submit_user_agent` varchar(500) NOT NULL COMMENT '提交客户端用户代理信息',
    MODIFY COLUMN `submit_reason` varchar(400) NOT NULL COMMENT '提交原因',
    MODIFY COLUMN `operation_time` datetime(3) NOT NULL COMMENT '业务操作发生时间',
    MODIFY COLUMN `processing_owner` varchar(128) DEFAULT NULL COMMENT '持有任务执行租约的工作节点标识',
    MODIFY COLUMN `processing_deadline` datetime(3) DEFAULT NULL COMMENT '任务执行租约的到期时间',
    MODIFY COLUMN `retry_count` int NOT NULL DEFAULT '0' COMMENT '已执行的重试次数',
    MODIFY COLUMN `next_retry_time` datetime(3) NOT NULL COMMENT '下次允许重试的时间',
    MODIFY COLUMN `last_failure_code` varchar(64) DEFAULT NULL COMMENT '最近一次失败原因码',
    MODIFY COLUMN `last_failure_message` varchar(500) DEFAULT NULL COMMENT '最近一次失败的受控说明',
    MODIFY COLUMN `started_time` datetime(3) DEFAULT NULL COMMENT '任务开始执行时间',
    MODIFY COLUMN `completed_time` datetime(3) DEFAULT NULL COMMENT '任务执行完成时间',
    MODIFY COLUMN `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，防止并发覆盖',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `active_profile_id` bigint GENERATED ALWAYS AS ((case when (`task_status` in (_utf8mb4'PREVIEWED',_utf8mb4'QUEUED',_utf8mb4'PROCESSING',_utf8mb4'FINALIZING',_utf8mb4'CANCELLING')) then `settlement_profile_id` else NULL end)) STORED COMMENT '未终态任务的结算档案唯一占位，终态时为空',
    ADD CONSTRAINT `chk_manual_review_task_value` CHECK (((`review_type` in (_latin1'REGULAR',_latin1'RESERVE_RELEASE')) and (`target_currency_exponent` between 0 and 8) and (`cutoff_end_time` > `cutoff_begin_time`) and (`snapshot_max_candidate_id` >= 0) and (`expected_candidate_count` >= 0) and (`processed_candidate_count` between 0 and `expected_candidate_count`) and (`locked_candidate_count` between 0 and `expected_candidate_count`) and (`last_candidate_id` between 0 and `snapshot_max_candidate_id`) and (`retry_count` >= 0) and (`version` >= 0) and (((`review_type` = _latin1'REGULAR') and (`initial_delay_unit` in (_latin1'T',_latin1'D')) and (`initial_delay_days` >= 1) and (`regular_delay_days` >= 1) and (`settlement_frequency` is not null)) or ((`review_type` = _latin1'RESERVE_RELEASE') and (`initial_delay_unit` is null) and (`initial_delay_days` is null) and (`regular_delay_days` is null) and (`settlement_frequency` is null) and (`frequency_day` is null))))),
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `settlement_projection_task`
    MODIFY COLUMN `projection_action` varchar(16) NOT NULL DEFAULT 'SETTLE' COMMENT '交易投影动作：结算或冲正',
    MODIFY COLUMN `task_status` varchar(16) NOT NULL DEFAULT 'INIT' COMMENT '投影任务状态：初始、处理中、失败或已完成',
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `settlement_reversal_order`
    COMMENT = '结算冲正申请及双人复核主单，保留冻结的入账身份',
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `reversal_order_no` varchar(20) NOT NULL COMMENT '结算冲正申请单号',
    MODIFY COLUMN `create_request_key` varchar(128) NOT NULL COMMENT '创建请求持久化幂等键',
    MODIFY COLUMN `original_batch_no` varchar(19) NOT NULL COMMENT '被冲正的原结算批次号',
    MODIFY COLUMN `reversal_batch_no` varchar(19) DEFAULT NULL COMMENT '实际生成的冲正结算批次号',
    MODIFY COLUMN `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
    MODIFY COLUMN `settlement_account_id` bigint NOT NULL COMMENT '目标商户结算资金账户主键',
    MODIFY COLUMN `target_currency` char(3) NOT NULL COMMENT '目标结算币种代码',
    MODIFY COLUMN `target_currency_exponent` tinyint NOT NULL COMMENT '目标结算币种小数位',
    MODIFY COLUMN `original_batch_version` bigint NOT NULL COMMENT '提交时冻结的原批次版本',
    MODIFY COLUMN `original_net_result_item_id` bigint NOT NULL COMMENT '原净结算结果明细主键',
    MODIFY COLUMN `original_fund_ledger_id` bigint NOT NULL COMMENT '原结算资金流水主键',
    MODIFY COLUMN `net_direction` varchar(8) NOT NULL COMMENT '净结算收付方向',
    MODIFY COLUMN `net_amount` decimal(24,8) NOT NULL COMMENT '冻结净结算金额，以目标币种主单位计价',
    MODIFY COLUMN `source_fingerprint` char(64) NOT NULL COMMENT '冻结来源事实集合的摘要指纹',
    MODIFY COLUMN `reversal_status` varchar(24) NOT NULL COMMENT '结算冲正申请状态',
    MODIFY COLUMN `submitted_by_account_id` bigint NOT NULL COMMENT '提交人账号主键',
    MODIFY COLUMN `submitted_by_account_name` varchar(128) NOT NULL COMMENT '提交人账号名称快照',
    MODIFY COLUMN `submitted_role_snapshot` varchar(1000) NOT NULL COMMENT '提交时的操作人角色快照',
    MODIFY COLUMN `submit_client_ip` varchar(64) NOT NULL COMMENT '提交客户端地址，仅用于受控审计',
    MODIFY COLUMN `submit_user_agent` varchar(500) NOT NULL COMMENT '提交客户端用户代理信息',
    MODIFY COLUMN `submit_reason` varchar(400) NOT NULL COMMENT '提交原因',
    MODIFY COLUMN `submitted_time` datetime(3) NOT NULL COMMENT '申请提交时间',
    MODIFY COLUMN `decided_by_account_id` bigint DEFAULT NULL COMMENT '审批决策人账号主键',
    MODIFY COLUMN `decided_by_account_name` varchar(128) DEFAULT NULL COMMENT '审批决策人账号名称快照',
    MODIFY COLUMN `decided_role_snapshot` varchar(1000) DEFAULT NULL COMMENT '审批决策时的角色快照',
    MODIFY COLUMN `decision_client_ip` varchar(64) DEFAULT NULL COMMENT '审批客户端地址，仅用于受控审计',
    MODIFY COLUMN `decision_user_agent` varchar(500) DEFAULT NULL COMMENT '审批客户端用户代理信息',
    MODIFY COLUMN `decision_action` varchar(16) DEFAULT NULL COMMENT '审批决定类型',
    MODIFY COLUMN `decision_request_key` varchar(128) DEFAULT NULL COMMENT '审批决定持久化幂等键',
    MODIFY COLUMN `decision_comment` varchar(400) DEFAULT NULL COMMENT '审批决定说明',
    MODIFY COLUMN `decision_time` datetime(3) DEFAULT NULL COMMENT '审批决定发生时间',
    MODIFY COLUMN `active_original_batch_no` varchar(19) GENERATED ALWAYS AS ((case when (`reversal_status` in (_utf8mb4'PENDING_APPROVAL',_utf8mb4'APPROVED')) then `original_batch_no` else NULL end)) STORED COMMENT '活动冲正申请的原批次唯一占位，终态时为空',
    MODIFY COLUMN `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，防止并发覆盖',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `settlement_review_candidate`
    COMMENT = '结算预审候选的不可变选择与锁定快照',
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `review_candidate_no` varchar(34) NOT NULL COMMENT '预审候选关系编号',
    MODIFY COLUMN `review_order_no` varchar(19) NOT NULL COMMENT '结算预审单号',
    MODIFY COLUMN `candidate_id` bigint NOT NULL COMMENT '结算候选物理主键',
    MODIFY COLUMN `candidate_no` varchar(64) NOT NULL COMMENT '结算候选稳定业务编号',
    MODIFY COLUMN `source_type` varchar(24) NOT NULL COMMENT '候选来源业务类型',
    MODIFY COLUMN `source_business_id` varchar(64) NOT NULL COMMENT '候选来源业务记录主键',
    MODIFY COLUMN `source_revision` int NOT NULL COMMENT '来源清分事实修订号',
    MODIFY COLUMN `source_transaction_id` varchar(64) NOT NULL COMMENT '来源系统交易号',
    MODIFY COLUMN `source_transaction_date_time` datetime(3) NOT NULL COMMENT '来源交易的精确路由时间',
    MODIFY COLUMN `locked_candidate_version` bigint NOT NULL COMMENT '独占锁定时的候选版本号',
    MODIFY COLUMN `clearing_fingerprint` char(64) NOT NULL COMMENT '锁定的清分事实摘要指纹',
    MODIFY COLUMN `relation_status` varchar(16) NOT NULL COMMENT '候选认领关系状态',
    MODIFY COLUMN `locked_time` datetime(3) NOT NULL COMMENT '候选独占锁定时间',
    MODIFY COLUMN `consumed_time` datetime(3) DEFAULT NULL COMMENT '预审候选被结算批次消费的时间',
    MODIFY COLUMN `released_time` datetime(3) DEFAULT NULL COMMENT '预审候选锁定关系释放时间',
    MODIFY COLUMN `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，防止并发覆盖',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `settlement_review_decision_task`
    DROP CHECK `chk_settlement_review_decision_task`,
    COMMENT = '可恢复的结算预审双人复核分段决策任务',
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `task_no` varchar(40) NOT NULL COMMENT '持久化任务编号',
    MODIFY COLUMN `request_key` varchar(128) NOT NULL COMMENT '操作请求持久化幂等键',
    MODIFY COLUMN `review_order_no` varchar(19) NOT NULL COMMENT '结算预审单号',
    MODIFY COLUMN `expected_review_version` bigint NOT NULL COMMENT '审批命令携带的预审单预期版本',
    MODIFY COLUMN `decision_action` varchar(16) NOT NULL COMMENT '审批决定类型',
    MODIFY COLUMN `decision_comment` varchar(400) NOT NULL COMMENT '审批决定说明',
    MODIFY COLUMN `operator_account_id` bigint NOT NULL COMMENT '操作人账号主键',
    MODIFY COLUMN `operator_account_name` varchar(128) NOT NULL COMMENT '操作人账号名称快照',
    MODIFY COLUMN `operator_role_snapshot` varchar(1000) NOT NULL COMMENT '操作人执行时的角色快照',
    MODIFY COLUMN `client_ip` varchar(64) NOT NULL COMMENT '客户端地址，仅用于受控审计',
    MODIFY COLUMN `user_agent` varchar(500) NOT NULL COMMENT '客户端用户代理信息',
    MODIFY COLUMN `operation_time` datetime(3) NOT NULL COMMENT '业务操作发生时间',
    MODIFY COLUMN `total_segment_count` int NOT NULL COMMENT '待处理物理分段总数量',
    MODIFY COLUMN `processed_segment_count` int NOT NULL DEFAULT '0' COMMENT '已完成物理分段数量',
    MODIFY COLUMN `result_batch_count` int NOT NULL DEFAULT '0' COMMENT '已生成结算批次数量',
    MODIFY COLUMN `first_settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '最先生成的结算批次号',
    MODIFY COLUMN `task_status` varchar(24) NOT NULL COMMENT '任务处理状态',
    MODIFY COLUMN `processing_owner` varchar(128) DEFAULT NULL COMMENT '持有任务执行租约的工作节点标识',
    MODIFY COLUMN `processing_deadline` datetime(3) DEFAULT NULL COMMENT '任务执行租约的到期时间',
    MODIFY COLUMN `retry_count` int NOT NULL DEFAULT '0' COMMENT '已执行的重试次数',
    MODIFY COLUMN `next_retry_time` datetime(3) NOT NULL COMMENT '下次允许重试的时间',
    MODIFY COLUMN `last_failure_code` varchar(64) DEFAULT NULL COMMENT '最近一次失败原因码',
    MODIFY COLUMN `last_failure_message` varchar(500) DEFAULT NULL COMMENT '最近一次失败的受控说明',
    MODIFY COLUMN `started_time` datetime(3) DEFAULT NULL COMMENT '任务开始执行时间',
    MODIFY COLUMN `completed_time` datetime(3) DEFAULT NULL COMMENT '任务执行完成时间',
    MODIFY COLUMN `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，防止并发覆盖',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `active_review_order_no` varchar(19) GENERATED ALWAYS AS ((case when (`task_status` in (_latin1'QUEUED',_latin1'PROCESSING',_latin1'FAILED')) then `review_order_no` else NULL end)) STORED COMMENT '未终态决策任务的预审单唯一占位，终态时为空',
    ADD CONSTRAINT `chk_settlement_review_decision_task` CHECK (((`expected_review_version` >= 0) and (`decision_action` in (_latin1'APPROVE',_latin1'REJECT',_latin1'CANCEL')) and (`total_segment_count` >= 1) and (`processed_segment_count` between 0 and `total_segment_count`) and (`result_batch_count` between 0 and `processed_segment_count`) and (`retry_count` >= 0) and (`version` >= 0) and (`task_status` in (_latin1'QUEUED',_latin1'PROCESSING',_latin1'COMPLETED',_latin1'FAILED')) and ((`decision_action` = _latin1'APPROVE') or ((`result_batch_count` = 0) and (`first_settlement_batch_no` is null))))),
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `settlement_review_order`
    DROP CHECK `chk_settlement_review_state`,
    COMMENT = '结算预审主单及双人复核的不可变审计事实',
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `review_order_no` varchar(19) NOT NULL COMMENT '结算预审单号',
    MODIFY COLUMN `create_request_key` varchar(128) NOT NULL COMMENT '创建请求持久化幂等键',
    MODIFY COLUMN `selection_fingerprint` char(64) NOT NULL COMMENT '候选编号及预期版本集合的摘要指纹',
    MODIFY COLUMN `review_type` varchar(24) NOT NULL COMMENT '结算预审类型，区分常规结算、保证金释放和调整',
    MODIFY COLUMN `create_mode` varchar(24) NOT NULL DEFAULT 'MANUAL' COMMENT '结算创建模式，区分自动入账、自动预审和人工选择',
    MODIFY COLUMN `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
    MODIFY COLUMN `settlement_profile_id` bigint NOT NULL COMMENT '冻结的商户结算档案主键',
    MODIFY COLUMN `settlement_account_id` bigint NOT NULL COMMENT '目标商户结算资金账户主键',
    MODIFY COLUMN `target_currency` char(3) NOT NULL COMMENT '目标结算币种代码',
    MODIFY COLUMN `target_currency_exponent` tinyint NOT NULL COMMENT '目标结算币种小数位',
    MODIFY COLUMN `business_date` date NOT NULL COMMENT '结算业务日期',
    MODIFY COLUMN `business_time_zone` varchar(64) NOT NULL COMMENT '解释结算业务时间的时区标识',
    MODIFY COLUMN `cutoff_begin_time` datetime(3) NOT NULL COMMENT '候选时间窗口的闭区间起点',
    MODIFY COLUMN `cutoff_end_time` datetime(3) NOT NULL COMMENT '候选时间窗口的开区间终点',
    MODIFY COLUMN `candidate_count` int NOT NULL COMMENT '冻结的候选总数量',
    MODIFY COLUMN `projectable_candidate_count` int NOT NULL DEFAULT '0' COMMENT '可生成交易结算投影的清分候选数量',
    MODIFY COLUMN `source_fingerprint` char(64) NOT NULL COMMENT '冻结来源事实集合的摘要指纹',
    MODIFY COLUMN `rate_fingerprint` char(64) NOT NULL COMMENT '冻结统一汇率矩阵的摘要指纹',
    MODIFY COLUMN `result_fingerprint` char(64) NOT NULL COMMENT '冻结财务计算结果的摘要指纹',
    MODIFY COLUMN `net_direction` varchar(8) NOT NULL COMMENT '净结算收付方向',
    MODIFY COLUMN `net_amount` decimal(24,8) NOT NULL COMMENT '冻结净结算金额，以目标币种主单位计价',
    MODIFY COLUMN `review_status` varchar(24) NOT NULL COMMENT '结算预审单状态',
    MODIFY COLUMN `created_by_account_id` bigint NOT NULL COMMENT '创建人账号主键',
    MODIFY COLUMN `created_by_account_name` varchar(128) NOT NULL COMMENT '创建人账号名称快照',
    MODIFY COLUMN `submitted_by_account_id` bigint NOT NULL COMMENT '提交人账号主键',
    MODIFY COLUMN `submitted_by_account_name` varchar(128) NOT NULL COMMENT '提交人账号名称快照',
    MODIFY COLUMN `submitted_role_snapshot` varchar(1000) NOT NULL COMMENT '提交时的操作人角色快照',
    MODIFY COLUMN `submit_client_ip` varchar(64) NOT NULL COMMENT '提交客户端地址，仅用于受控审计',
    MODIFY COLUMN `submit_user_agent` varchar(500) NOT NULL COMMENT '提交客户端用户代理信息',
    MODIFY COLUMN `submit_reason` varchar(400) NOT NULL COMMENT '提交原因',
    MODIFY COLUMN `submitted_time` datetime(3) NOT NULL COMMENT '申请提交时间',
    MODIFY COLUMN `decided_by_account_id` bigint DEFAULT NULL COMMENT '审批决策人账号主键',
    MODIFY COLUMN `decided_by_account_name` varchar(128) DEFAULT NULL COMMENT '审批决策人账号名称快照',
    MODIFY COLUMN `decided_role_snapshot` varchar(1000) DEFAULT NULL COMMENT '审批决策时的角色快照',
    MODIFY COLUMN `decision_client_ip` varchar(64) DEFAULT NULL COMMENT '审批客户端地址，仅用于受控审计',
    MODIFY COLUMN `decision_user_agent` varchar(500) DEFAULT NULL COMMENT '审批客户端用户代理信息',
    MODIFY COLUMN `decision_action` varchar(16) DEFAULT NULL COMMENT '审批决定类型',
    MODIFY COLUMN `decision_request_key` varchar(128) DEFAULT NULL COMMENT '审批决定持久化幂等键',
    MODIFY COLUMN `review_comment` varchar(400) DEFAULT NULL COMMENT '结算预审意见',
    MODIFY COLUMN `decision_time` datetime(3) DEFAULT NULL COMMENT '审批决定发生时间',
    MODIFY COLUMN `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '关联结算批次号',
    MODIFY COLUMN `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，防止并发覆盖',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    ADD CONSTRAINT `chk_settlement_review_state` CHECK (((`review_type` in (_latin1'REGULAR',_latin1'RESERVE_RELEASE',_latin1'ADJUSTMENT')) and (`create_mode` in (_latin1'MANUAL',_latin1'MANUAL_ASYNC',_latin1'AUTO_REVIEW')) and (`net_direction` in (_latin1'CREDIT',_latin1'DEBIT')) and (`review_status` in (_latin1'PENDING_APPROVAL',_latin1'APPROVED',_latin1'REJECTED',_latin1'CANCELLED',_latin1'EXPIRED')) and (((`review_status` = _latin1'PENDING_APPROVAL') and (`decided_by_account_id` is null) and (`decision_action` is null) and (`decision_request_key` is null) and (`decision_time` is null) and (`review_comment` is null) and (`settlement_batch_no` is null)) or ((`review_status` = _latin1'APPROVED') and (`decided_by_account_id` is not null) and (`decided_by_account_name` is not null) and (`decided_role_snapshot` is not null) and (`decision_client_ip` is not null) and (`decision_user_agent` is not null) and (`decision_action` = _latin1'APPROVE') and (`decision_request_key` is not null) and (`decision_time` is not null) and (`review_comment` is not null) and (`settlement_batch_no` is not null) and (`submitted_by_account_id` <> `decided_by_account_id`)) or ((`review_status` = _latin1'REJECTED') and (`decided_by_account_id` is not null) and (`decided_by_account_name` is not null) and (`decided_role_snapshot` is not null) and (`decision_client_ip` is not null) and (`decision_user_agent` is not null) and (`decision_action` = _latin1'REJECT') and (`decision_request_key` is not null) and (`decision_time` is not null) and (`review_comment` is not null) and (`submitted_by_account_id` <> `decided_by_account_id`) and (`settlement_batch_no` is null)) or ((`review_status` = _latin1'CANCELLED') and (`decided_by_account_id` is not null) and (`decided_by_account_name` is not null) and (`decided_role_snapshot` is not null) and (`decision_client_ip` is not null) and (`decision_user_agent` is not null) and (`decision_action` = _latin1'CANCEL') and (`decision_request_key` is not null) and (`decision_time` is not null) and (`review_comment` is not null) and (`submitted_by_account_id` = `decided_by_account_id`) and (`settlement_batch_no` is null)) or ((`review_status` = _latin1'EXPIRED') and (`decided_by_account_id` is not null) and (`decided_by_account_name` is not null) and (`decided_role_snapshot` is not null) and (`decision_client_ip` is not null) and (`decision_user_agent` is not null) and (`decision_action` = _latin1'EXPIRE') and (`decision_request_key` is not null) and (`decision_time` is not null) and (`review_comment` is not null) and (`settlement_batch_no` is null))))),
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `settlement_review_segment`
    DROP CHECK `chk_settlement_review_segment`,
    COMMENT = '一个逻辑结算预审单对应的物理处理分段',
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `segment_no` varchar(64) NOT NULL COMMENT '预审物理分段编号',
    MODIFY COLUMN `review_order_no` varchar(19) NOT NULL COMMENT '结算预审单号',
    MODIFY COLUMN `sequence_no` int NOT NULL COMMENT '同一预审单内的分段处理顺序',
    MODIFY COLUMN `first_candidate_id` bigint NOT NULL COMMENT '分段内首个候选主键',
    MODIFY COLUMN `last_candidate_id` bigint NOT NULL COMMENT '分段内最后一个候选主键',
    MODIFY COLUMN `candidate_count` int NOT NULL COMMENT '冻结的候选总数量',
    MODIFY COLUMN `projectable_candidate_count` int NOT NULL COMMENT '可生成交易结算投影的清分候选数量',
    MODIFY COLUMN `source_fingerprint` char(64) NOT NULL COMMENT '冻结来源事实集合的摘要指纹',
    MODIFY COLUMN `result_fingerprint` char(64) NOT NULL COMMENT '冻结财务计算结果的摘要指纹',
    MODIFY COLUMN `net_signed_amount` decimal(24,8) NOT NULL COMMENT '分段有符号净结算金额，以目标币种主单位计价',
    MODIFY COLUMN `settlement_batch_no` varchar(19) DEFAULT NULL COMMENT '关联结算批次号',
    MODIFY COLUMN `segment_status` varchar(16) NOT NULL DEFAULT 'LOCKED' COMMENT '预审物理分段处理状态',
    MODIFY COLUMN `processed_time` datetime(3) DEFAULT NULL COMMENT '分段处理完成时间',
    MODIFY COLUMN `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，防止并发覆盖',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    ADD CONSTRAINT `chk_settlement_review_segment` CHECK (((`sequence_no` >= 1) and (`first_candidate_id` > 0) and (`last_candidate_id` >= `first_candidate_id`) and (`candidate_count` >= 1) and (`projectable_candidate_count` between 0 and `candidate_count`) and (`segment_status` in (_latin1'LOCKED',_latin1'CONSUMED',_latin1'RELEASED')) and (`version` >= 0))),
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `transaction_event_outbox`
    MODIFY COLUMN `topic` varchar(128) NOT NULL COMMENT '消息队列主题',
    MODIFY COLUMN `tag` varchar(128) DEFAULT NULL COMMENT '消息队列标签',
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `transaction_event_outbox_202603`
    MODIFY COLUMN `topic` varchar(128) NOT NULL COMMENT '消息队列主题',
    MODIFY COLUMN `tag` varchar(128) DEFAULT NULL COMMENT '消息队列标签',
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `transaction_event_outbox_202604`
    MODIFY COLUMN `topic` varchar(128) NOT NULL COMMENT '消息队列主题',
    MODIFY COLUMN `tag` varchar(128) DEFAULT NULL COMMENT '消息队列标签',
    ALGORITHM=COPY, LOCK=SHARED;

ALTER TABLE `transaction_event_outbox_202701`
    MODIFY COLUMN `topic` varchar(128) NOT NULL COMMENT '消息队列主题',
    MODIFY COLUMN `tag` varchar(128) DEFAULT NULL COMMENT '消息队列标签',
    ALGORITHM=COPY, LOCK=SHARED;
