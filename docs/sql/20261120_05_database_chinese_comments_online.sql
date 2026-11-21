-- payment_acquiring 中文数据库注释治理，仅注释变更。
-- 前置：已备份完整结构并在 MySQL 8.4.9 独立空库演练；逐项确认类型、默认值、生成表达式、索引、约束与备份一致。
-- 注意 DDL 隐式提交，不能用事务回滚。必须使用本次独立备份恢复注释，不回写业务记录。
USE `payment_acquiring`;
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=3;
-- 已演练支持在线修改；禁止自动退化为复制表。

ALTER TABLE `admin_operational_work_item_history`
    MODIFY COLUMN `action_type` varchar(24) NOT NULL COMMENT '待办动作：创建、重新打开、认领、分配、转交、备注及自动关闭',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `admin_payment_link_delivery_request`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `request_id` varchar(80) NOT NULL COMMENT '客户端请求幂等编号',
    MODIFY COLUMN `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
    MODIFY COLUMN `link_id` varchar(32) NOT NULL COMMENT '支付链接编号，兼容历史编号格式',
    MODIFY COLUMN `fingerprint` char(64) NOT NULL COMMENT '发送请求内容指纹，用于校验同一幂等请求的参数一致性',
    MODIFY COLUMN `recipient_hash` char(64) NOT NULL COMMENT '收件邮箱的不可逆摘要，不保存邮箱原文',
    MODIFY COLUMN `recipient_masked` varchar(260) NOT NULL COMMENT '脱敏收件邮箱，用于受控页面展示',
    MODIFY COLUMN `locale` varchar(12) NOT NULL COMMENT '消息或页面语言标识',
    MODIFY COLUMN `biz_no` char(64) NOT NULL COMMENT '邮件发送业务幂等编号',
    MODIFY COLUMN `email_record_id` bigint DEFAULT NULL COMMENT '关联邮件发送记录主键',
    MODIFY COLUMN `created_by` varchar(128) NOT NULL COMMENT '创建操作人标识',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL COMMENT '创建时间',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `base_iso_currency`
    MODIFY COLUMN `icon_key` varchar(64) DEFAULT NULL COMMENT '受控币种展示图标键',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `channel_mid_config`
    MODIFY COLUMN `default_transaction_currency` char(3) NOT NULL COMMENT '后续新发起交易默认使用的交易币种',
    MODIFY COLUMN `mcc` varchar(16) DEFAULT NULL COMMENT '渠道商户号对应的商户类别码',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `channel_payment_capability`
    MODIFY COLUMN `default_transaction_currency` char(3) NOT NULL COMMENT '默认交易币种，必须属于该支付能力允许的币种集合',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `data_export_download_audit`
    MODIFY COLUMN `result_status` varchar(16) NOT NULL COMMENT '下载审计结果：REQUESTED已请求，SUCCESS成功，FAILED失败',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `data_export_task`
    MODIFY COLUMN `status` varchar(16) NOT NULL COMMENT '导出任务状态：待处理、处理中、成功、失败或已过期',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `exchange_rate_alert_evaluation`
    COMMENT = '汇率波动预警评估幂等记录表',
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN `request_key` varchar(128) NOT NULL COMMENT '调度请求幂等键',
    MODIFY COLUMN `window_end_time` datetime(3) NOT NULL COMMENT '本轮评估业务截止时间',
    MODIFY COLUMN `evaluation_status` varchar(32) NOT NULL COMMENT '评估执行状态：PROCESSING处理中，SUCCESS成功，FAILED失败',
    MODIFY COLUMN `evaluated_rule_count` int NOT NULL DEFAULT '0' COMMENT '评估规则数',
    MODIFY COLUMN `evaluated_pair_count` int NOT NULL DEFAULT '0' COMMENT '评估币种对数',
    MODIFY COLUMN `triggered_event_count` int NOT NULL DEFAULT '0' COMMENT '新触发事件数',
    MODIFY COLUMN `updated_event_count` int NOT NULL DEFAULT '0' COMMENT '更新活动事件数',
    MODIFY COLUMN `resolved_event_count` int NOT NULL DEFAULT '0' COMMENT '自动恢复事件数',
    MODIFY COLUMN `insufficient_data_count` int NOT NULL DEFAULT '0' COMMENT '样本不足维度数',
    MODIFY COLUMN `error_message` varchar(512) DEFAULT NULL COMMENT '失败摘要，不保存敏感信息',
    MODIFY COLUMN `start_time` datetime(3) NOT NULL COMMENT '开始时间',
    MODIFY COLUMN `end_time` datetime(3) DEFAULT NULL COMMENT '结束时间',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `exchange_rate_alert_event`
    COMMENT = '汇率波动预警事件表',
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN `event_no` varchar(64) NOT NULL COMMENT '预警事件编号',
    MODIFY COLUMN `active_event_key` varchar(255) DEFAULT NULL COMMENT '活动事件唯一键，终态后置空',
    MODIFY COLUMN `rule_id` bigint NOT NULL COMMENT '规则ID',
    MODIFY COLUMN `rule_code` varchar(64) NOT NULL COMMENT '触发时规则编码快照',
    MODIFY COLUMN `rule_name` varchar(128) NOT NULL COMMENT '触发时规则名称快照',
    MODIFY COLUMN `rate_type` varchar(32) NOT NULL COMMENT '汇率类型',
    MODIFY COLUMN `base_currency` char(3) NOT NULL COMMENT '原始币种',
    MODIFY COLUMN `quote_currency` char(3) NOT NULL COMMENT '目标币种',
    MODIFY COLUMN `alert_level` varchar(32) NOT NULL COMMENT '预警等级快照',
    MODIFY COLUMN `window_minutes` int NOT NULL COMMENT '窗口分钟数快照',
    MODIFY COLUMN `trigger_threshold` decimal(9,4) NOT NULL COMMENT '触发阈值百分比快照',
    MODIFY COLUMN `recovery_threshold` decimal(9,4) NOT NULL COMMENT '恢复阈值百分比快照',
    MODIFY COLUMN `window_start_time` datetime(3) NOT NULL COMMENT '最近评估窗口开始时间',
    MODIFY COLUMN `window_end_time` datetime(3) NOT NULL COMMENT '最近评估窗口结束时间',
    MODIFY COLUMN `sample_count` int NOT NULL COMMENT '最近评估样本数',
    MODIFY COLUMN `minimum_rate` decimal(24,12) NOT NULL COMMENT '窗口最低汇率',
    MODIFY COLUMN `minimum_rate_source` varchar(64) DEFAULT NULL COMMENT '最低汇率来源证据',
    MODIFY COLUMN `minimum_business_rate_id` bigint DEFAULT NULL COMMENT '最低汇率业务汇率ID',
    MODIFY COLUMN `maximum_rate` decimal(24,12) NOT NULL COMMENT '窗口最高汇率',
    MODIFY COLUMN `maximum_rate_source` varchar(64) DEFAULT NULL COMMENT '最高汇率来源证据',
    MODIFY COLUMN `maximum_business_rate_id` bigint DEFAULT NULL COMMENT '最高汇率业务汇率ID',
    MODIFY COLUMN `latest_rate` decimal(24,12) NOT NULL COMMENT '窗口最新汇率',
    MODIFY COLUMN `latest_rate_source` varchar(64) DEFAULT NULL COMMENT '最新汇率来源证据',
    MODIFY COLUMN `latest_business_rate_id` bigint DEFAULT NULL COMMENT '最新汇率业务汇率ID',
    MODIFY COLUMN `volatility_rate` decimal(12,6) NOT NULL COMMENT '最近窗口波动率百分比',
    MODIFY COLUMN `maximum_volatility_rate` decimal(12,6) NOT NULL COMMENT '事件持续期间最大波动率百分比',
    MODIFY COLUMN `recovery_streak` int NOT NULL DEFAULT '0' COMMENT '连续低于恢复阈值次数',
    MODIFY COLUMN `event_status` varchar(32) NOT NULL COMMENT '预警事件状态：待确认、已确认、已恢复或已关闭',
    MODIFY COLUMN `first_trigger_time` datetime(3) NOT NULL COMMENT '首次触发时间',
    MODIFY COLUMN `last_detected_time` datetime(3) NOT NULL COMMENT '最近发现时间',
    MODIFY COLUMN `resolved_time` datetime(3) DEFAULT NULL COMMENT '自动恢复时间',
    MODIFY COLUMN `acknowledged_by` varchar(64) DEFAULT NULL COMMENT '确认人',
    MODIFY COLUMN `acknowledged_time` datetime(3) DEFAULT NULL COMMENT '确认时间',
    MODIFY COLUMN `acknowledge_remark` varchar(512) DEFAULT NULL COMMENT '确认备注',
    MODIFY COLUMN `closed_by` varchar(64) DEFAULT NULL COMMENT '关闭人',
    MODIFY COLUMN `closed_time` datetime(3) DEFAULT NULL COMMENT '关闭时间',
    MODIFY COLUMN `close_remark` varchar(512) DEFAULT NULL COMMENT '关闭备注',
    MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT 'CAS版本',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `exchange_rate_alert_rule`
    COMMENT = '汇率波动预警规则表',
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN `rule_code` varchar(64) NOT NULL COMMENT '稳定规则编码',
    MODIFY COLUMN `rule_name` varchar(128) NOT NULL COMMENT '规则名称',
    MODIFY COLUMN `rate_type` varchar(32) NOT NULL COMMENT '汇率类型：TRANSACTION_RATE/SETTLEMENT_RATE',
    MODIFY COLUMN `base_currency` varchar(3) NOT NULL DEFAULT 'ALL' COMMENT '原始币种，ALL表示所有币种',
    MODIFY COLUMN `quote_currency` varchar(3) NOT NULL DEFAULT 'ALL' COMMENT '目标币种，ALL表示所有币种',
    MODIFY COLUMN `window_minutes` int NOT NULL COMMENT '滚动窗口分钟数',
    MODIFY COLUMN `trigger_threshold` decimal(9,4) NOT NULL COMMENT '触发阈值百分比',
    MODIFY COLUMN `recovery_threshold` decimal(9,4) NOT NULL COMMENT '恢复阈值百分比',
    MODIFY COLUMN `recovery_consecutive_count` int NOT NULL DEFAULT '2' COMMENT '连续低于恢复阈值的评估次数',
    MODIFY COLUMN `minimum_sample_count` int NOT NULL DEFAULT '2' COMMENT '最小样本数',
    MODIFY COLUMN `alert_level` varchar(32) NOT NULL DEFAULT 'WARNING' COMMENT '预警等级：WARNING/ERROR/CRITICAL',
    MODIFY COLUMN `system_mandatory` tinyint NOT NULL DEFAULT '0' COMMENT '是否系统强制规则：0否，1是',
    MODIFY COLUMN `rule_status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用，1启用',
    MODIFY COLUMN `remark` varchar(512) DEFAULT NULL COMMENT '备注',
    MODIFY COLUMN `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `merchant_payment_link_delivery_request`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `request_id` varchar(80) NOT NULL COMMENT '客户端请求幂等编号',
    MODIFY COLUMN `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
    MODIFY COLUMN `link_id` varchar(32) NOT NULL COMMENT '支付链接编号，兼容历史编号格式',
    MODIFY COLUMN `fingerprint` char(64) NOT NULL COMMENT '发送请求内容指纹，用于校验同一幂等请求的参数一致性',
    MODIFY COLUMN `recipient_hash` char(64) NOT NULL COMMENT '收件邮箱的不可逆摘要，不保存邮箱原文',
    MODIFY COLUMN `recipient_masked` varchar(260) NOT NULL COMMENT '脱敏收件邮箱，用于受控页面展示',
    MODIFY COLUMN `locale` varchar(12) NOT NULL COMMENT '消息或页面语言标识',
    MODIFY COLUMN `biz_no` char(64) NOT NULL COMMENT '邮件发送业务幂等编号',
    MODIFY COLUMN `email_record_id` bigint DEFAULT NULL COMMENT '关联邮件发送记录主键',
    MODIFY COLUMN `created_by` varchar(128) NOT NULL COMMENT '创建操作人标识',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL COMMENT '创建时间',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `merchant_reserve_action`
    MODIFY COLUMN `action_type` varchar(32) NOT NULL COMMENT '保证金动作类型：留存、退回、释放以及对应的冲正动作',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `msg_email_send_record`
    MODIFY COLUMN `delivery_content_cipher` longtext COMMENT '加密的邮件投递正文，发送成功后清除',
    MODIFY COLUMN `content_type` varchar(16) DEFAULT NULL COMMENT '邮件正文类型：HTML富文本或TEXT纯文本',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `payment_checkout_security_event`
    MODIFY COLUMN `request_method` varchar(16) DEFAULT NULL COMMENT '请求采用的超文本传输方法',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `payment_link`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `link_id` varchar(32) NOT NULL COMMENT '支付链接编号，兼容历史编号格式',
    MODIFY COLUMN `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
    MODIFY COLUMN `merchant_order_no` varchar(64) NOT NULL COMMENT '商户侧订单号，在商户范围内唯一',
    MODIFY COLUMN `link_name` varchar(120) NOT NULL COMMENT '支付链接名称，未填写时保存空串并按商户订单号展示',
    MODIFY COLUMN `description` varchar(500) DEFAULT NULL COMMENT '业务说明',
    MODIFY COLUMN `currency` char(3) NOT NULL COMMENT '金额对应的三位字母币种代码',
    MODIFY COLUMN `amount` decimal(24,8) NOT NULL COMMENT '支付链接固定金额，以币种主单位计价',
    MODIFY COLUMN `currency_exponent` int NOT NULL COMMENT '币种小数位，用于金额精度校验与展示',
    MODIFY COLUMN `locale` varchar(12) NOT NULL COMMENT '消息或页面语言标识',
    MODIFY COLUMN `allowed_brands_json` text NOT NULL COMMENT '允许的支付卡品牌列表，使用序列化数组保存',
    MODIFY COLUMN `merchant_display_name` varchar(256) NOT NULL COMMENT '支付页展示的商户名称快照',
    MODIFY COLUMN `notify_url` varchar(512) DEFAULT NULL COMMENT '商户交易结果通知地址',
    MODIFY COLUMN `redirect_url` varchar(512) DEFAULT NULL COMMENT '支付完成后的页面跳转地址',
    MODIFY COLUMN `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '支付链接状态：ACTIVE有效，DISABLED停用，EXPIRED过期，COMPLETED已完成',
    MODIFY COLUMN `expire_time` datetime(3) NOT NULL COMMENT '支付链接有效期截止时间',
    MODIFY COLUMN `create_request_id` varchar(80) NOT NULL COMMENT '创建支付链接的请求幂等编号',
    MODIFY COLUMN `request_fingerprint` char(64) NOT NULL COMMENT '请求参数指纹，用于防止同一幂等编号对应不同请求',
    MODIFY COLUMN `created_app_code` varchar(16) NOT NULL COMMENT '创建来源应用编码',
    MODIFY COLUMN `created_by` varchar(128) NOT NULL COMMENT '创建操作人标识',
    MODIFY COLUMN `disabled_by` varchar(128) DEFAULT NULL COMMENT '停用操作人标识',
    MODIFY COLUMN `disable_request_id` varchar(80) DEFAULT NULL COMMENT '停用请求幂等编号',
    MODIFY COLUMN `disable_reason` varchar(500) DEFAULT NULL COMMENT '停用原因',
    MODIFY COLUMN `disabled_time` datetime(3) DEFAULT NULL COMMENT '停用成功时间',
    MODIFY COLUMN `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，防止并发覆盖',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL COMMENT '创建时间',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL COMMENT '更新时间',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `payment_link_access`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `link_id` varchar(32) NOT NULL COMMENT '支付链接编号，兼容历史编号格式',
    MODIFY COLUMN `key_version` varchar(32) NOT NULL COMMENT '访问令牌签名密钥版本号，不保存密钥原文',
    MODIFY COLUMN `token_hash` char(64) NOT NULL COMMENT '访问令牌摘要，不保存令牌原文',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL COMMENT '创建时间',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `payment_link_checkout_binding`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `link_id` varchar(32) NOT NULL COMMENT '支付链接编号，兼容历史编号格式',
    MODIFY COLUMN `collection_id` varchar(64) NOT NULL COMMENT '支付链接唯一收款关联编号',
    MODIFY COLUMN `checkout_session_id` varchar(64) NOT NULL COMMENT '关联收银台会话编号',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL COMMENT '创建时间',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `payment_link_collection`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `collection_id` varchar(64) NOT NULL COMMENT '支付链接唯一收款关联编号',
    MODIFY COLUMN `link_id` varchar(32) NOT NULL COMMENT '支付链接编号，兼容历史编号格式',
    MODIFY COLUMN `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
    MODIFY COLUMN `merchant_order_no` varchar(64) NOT NULL COMMENT '商户侧订单号，在商户范围内唯一',
    MODIFY COLUMN `submission_deadline` datetime(3) NOT NULL COMMENT '本次收款允许提交支付的截止时间',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL COMMENT '创建时间',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `payment_link_event`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `event_id` varchar(64) NOT NULL COMMENT '支付链接审计事件编号',
    MODIFY COLUMN `link_id` varchar(32) NOT NULL COMMENT '支付链接编号，兼容历史编号格式',
    MODIFY COLUMN `event_type` varchar(32) NOT NULL COMMENT '支付链接审计事件类型',
    MODIFY COLUMN `actor_app_code` varchar(16) NOT NULL COMMENT '操作来源应用编码',
    MODIFY COLUMN `actor_id` varchar(128) NOT NULL COMMENT '操作人标识',
    MODIFY COLUMN `reason` varchar(500) DEFAULT NULL COMMENT '操作原因或业务说明',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL COMMENT '创建时间',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `payment_route_candidate`
    MODIFY COLUMN `card_brand` varchar(64) NOT NULL DEFAULT 'NONE' COMMENT '支付卡品牌，非卡支付方式使用NONE',
    MODIFY COLUMN `candidate_weight` decimal(5,2) DEFAULT NULL COMMENT 'WEIGHT类型候选权重，按支付方式合计100.00',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `payment_route_method`
    COMMENT = '支付路由规则适用支付方式表',
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN `rule_id` bigint NOT NULL COMMENT '支付路由规则ID',
    MODIFY COLUMN `payment_method` varchar(64) NOT NULL COMMENT '规则适用的收单支付方式',
    MODIFY COLUMN `card_brand` varchar(64) NOT NULL DEFAULT 'NONE' COMMENT '支付卡品牌，非卡支付方式使用NONE',
    MODIFY COLUMN `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `payment_route_rule_merchant`
    COMMENT = '支付路由规则指定商户关联表',
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN `rule_id` bigint NOT NULL COMMENT '支付路由规则ID',
    MODIFY COLUMN `merchant_id` varchar(32) NOT NULL COMMENT '平台商户号',
    MODIFY COLUMN `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `payment_route_strategy`
    MODIFY COLUMN `history_lookback_months` int DEFAULT NULL COMMENT '历史成功MID路由回看月数，其他类型为空',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `payment_route_transaction_type`
    COMMENT = '支付路由规则适用交易类型表；无有效记录表示全部交易类型',
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    MODIFY COLUMN `rule_id` bigint NOT NULL COMMENT '支付路由规则ID',
    MODIFY COLUMN `transaction_type` varchar(64) NOT NULL COMMENT '规则适用的收单交易类型',
    MODIFY COLUMN `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `deleted` bigint NOT NULL DEFAULT '0' COMMENT '删除标识：0未删除，大于0为删除记录ID',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `risk_merchant_limit_reservation`
    MODIFY COLUMN `limit_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '风控限额周期：DAILY每日，WEEKLY每周，MONTHLY每月',
    MODIFY COLUMN `reservation_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '限额预占状态：准备中、已预占、已确认或已取消',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `risk_trade_black_batch`
    MODIFY COLUMN `status` varchar(24) NOT NULL COMMENT '批量任务状态：处理中、全部成功、部分成功或失败',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `risk_trade_black_batch_item`
    MODIFY COLUMN `result_code` varchar(32) NOT NULL COMMENT '名单变更结果：已添加、已释放、已存在、原本不存在或失败',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `settlement_batch_cancellation_audit`
    COMMENT = '结算批次入账前取消操作的不可变审计记录',
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `settlement_batch_no` varchar(19) NOT NULL COMMENT '关联结算批次号',
    MODIFY COLUMN `request_key` varchar(64) NOT NULL COMMENT '操作请求持久化幂等键',
    MODIFY COLUMN `expected_version` bigint NOT NULL COMMENT '请求携带的预期版本号',
    MODIFY COLUMN `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
    MODIFY COLUMN `batch_status_before` varchar(24) NOT NULL COMMENT '操作前结算批次状态快照',
    MODIFY COLUMN `released_candidate_count` int NOT NULL COMMENT '取消批次时释放认领的候选数量',
    MODIFY COLUMN `operator_account_id` bigint NOT NULL COMMENT '操作人账号主键',
    MODIFY COLUMN `operator_account_name` varchar(128) NOT NULL COMMENT '操作人账号名称快照',
    MODIFY COLUMN `operator_role_snapshot` varchar(1000) NOT NULL COMMENT '操作人执行时的角色快照',
    MODIFY COLUMN `client_ip` varchar(64) NOT NULL COMMENT '客户端地址，仅用于受控审计',
    MODIFY COLUMN `user_agent` varchar(500) NOT NULL COMMENT '客户端用户代理信息',
    MODIFY COLUMN `reason` varchar(400) NOT NULL COMMENT '操作原因或业务说明',
    MODIFY COLUMN `operation_time` datetime(3) NOT NULL COMMENT '业务操作发生时间',
    MODIFY COLUMN `cancelled_time` datetime(3) NOT NULL COMMENT '结算批次取消成功时间',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `settlement_batch_rate`
    MODIFY COLUMN `review_rate_id` bigint DEFAULT NULL COMMENT '采用的预审统一汇率记录主键',
    MODIFY COLUMN `rate_type` varchar(24) NOT NULL DEFAULT 'SETTLEMENT' COMMENT '结算汇率用途：常规结算或保证金释放',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `settlement_batch_recovery_audit`
    COMMENT = '结算批次汇率锁定重试耗尽后的人工恢复审计记录',
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `settlement_batch_no` varchar(19) NOT NULL COMMENT '关联结算批次号',
    MODIFY COLUMN `request_key` varchar(64) NOT NULL COMMENT '操作请求持久化幂等键',
    MODIFY COLUMN `expected_version` bigint NOT NULL COMMENT '请求携带的预期版本号',
    MODIFY COLUMN `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
    MODIFY COLUMN `recovery_action` varchar(32) NOT NULL COMMENT '人工恢复动作类型',
    MODIFY COLUMN `batch_status_before` varchar(24) NOT NULL COMMENT '操作前结算批次状态快照',
    MODIFY COLUMN `failure_stage_before` varchar(32) NOT NULL COMMENT '恢复前失败阶段快照',
    MODIFY COLUMN `failure_code_before` varchar(64) NOT NULL COMMENT '恢复前失败原因码快照',
    MODIFY COLUMN `retry_count_before` int NOT NULL COMMENT '恢复前已重试次数',
    MODIFY COLUMN `restored_candidate_count` int NOT NULL COMMENT '恢复时重新关联的候选数量',
    MODIFY COLUMN `operator_account_id` bigint NOT NULL COMMENT '操作人账号主键',
    MODIFY COLUMN `operator_account_name` varchar(128) NOT NULL COMMENT '操作人账号名称快照',
    MODIFY COLUMN `operator_role_snapshot` varchar(1000) NOT NULL COMMENT '操作人执行时的角色快照',
    MODIFY COLUMN `client_ip` varchar(64) NOT NULL COMMENT '客户端地址，仅用于受控审计',
    MODIFY COLUMN `user_agent` varchar(500) NOT NULL COMMENT '客户端用户代理信息',
    MODIFY COLUMN `reason` varchar(400) NOT NULL COMMENT '操作原因或业务说明',
    MODIFY COLUMN `operation_time` datetime(3) NOT NULL COMMENT '业务操作发生时间',
    MODIFY COLUMN `recovered_time` datetime(3) NOT NULL COMMENT '人工恢复完成时间',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `settlement_candidate_dependency`
    MODIFY COLUMN `dependency_type` varchar(32) NOT NULL COMMENT '依赖完成条件：来源财务入账、保证金留存入账或来源费用结果入账',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `settlement_result_item`
    MODIFY COLUMN `source_detail_type` varchar(24) NOT NULL COMMENT '结算来源明细类型：交易清分、保证金清分、批次限额或批次净额',
    MODIFY COLUMN `result_item_type` varchar(32) NOT NULL COMMENT '结算结果明细类型：本金、费用分量、费用组最终值、保证金留存、保证金退回、保证金释放、调整、冲正或净结算',
    MODIFY COLUMN `result_role` varchar(24) NOT NULL COMMENT '结算结果角色：追踪说明、财务分量或资金入账',
    MODIFY COLUMN `applied_limit` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '限额命中类型：NONE未命中，MINIMUM最低限额，MAXIMUM最高限额',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `settlement_reversal_daily_sequence`
    COMMENT = '结算冲正单号的数据库业务日流水序列表',
    MODIFY COLUMN `business_date` date NOT NULL COMMENT '结算业务日期',
    MODIFY COLUMN `current_sequence` int NOT NULL DEFAULT '0' COMMENT '业务日期内已分配的最大流水序号',
    MODIFY COLUMN `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，防止并发覆盖',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `settlement_review_daily_sequence`
    COMMENT = '结算预审单号的数据库业务日流水序列表',
    MODIFY COLUMN `business_date` date NOT NULL COMMENT '结算业务日期',
    MODIFY COLUMN `current_sequence` int NOT NULL DEFAULT '0' COMMENT '业务日期内已分配的最大流水序号',
    MODIFY COLUMN `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，防止并发覆盖',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `settlement_review_decision_recovery_audit`
    COMMENT = '结算预审分段决策任务失败后人工恢复的不可变审计记录',
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `task_no` varchar(40) NOT NULL COMMENT '持久化任务编号',
    MODIFY COLUMN `review_order_no` varchar(19) NOT NULL COMMENT '结算预审单号',
    MODIFY COLUMN `request_key` varchar(64) NOT NULL COMMENT '操作请求持久化幂等键',
    MODIFY COLUMN `expected_version` bigint NOT NULL COMMENT '请求携带的预期版本号',
    MODIFY COLUMN `task_status_before` varchar(24) NOT NULL COMMENT '恢复前任务状态快照',
    MODIFY COLUMN `processed_segment_count_before` int NOT NULL COMMENT '恢复前已处理分段数量',
    MODIFY COLUMN `result_batch_count_before` int NOT NULL COMMENT '恢复前已生成结算批次数量',
    MODIFY COLUMN `retry_count_before` int NOT NULL COMMENT '恢复前已重试次数',
    MODIFY COLUMN `failure_code_before` varchar(64) NOT NULL COMMENT '恢复前失败原因码快照',
    MODIFY COLUMN `failure_message_before` varchar(500) DEFAULT NULL COMMENT '恢复前失败说明快照',
    MODIFY COLUMN `started_time_before` datetime(3) DEFAULT NULL COMMENT '恢复前任务开始时间快照',
    MODIFY COLUMN `completed_time_before` datetime(3) DEFAULT NULL COMMENT '恢复前任务完成时间快照',
    MODIFY COLUMN `operator_account_id` bigint NOT NULL COMMENT '操作人账号主键',
    MODIFY COLUMN `operator_account_name` varchar(128) NOT NULL COMMENT '操作人账号名称快照',
    MODIFY COLUMN `operator_role_snapshot` varchar(1000) NOT NULL COMMENT '操作人执行时的角色快照',
    MODIFY COLUMN `client_ip` varchar(64) NOT NULL COMMENT '客户端地址，仅用于受控审计',
    MODIFY COLUMN `user_agent` varchar(500) NOT NULL COMMENT '客户端用户代理信息',
    MODIFY COLUMN `reason` varchar(400) NOT NULL COMMENT '操作原因或业务说明',
    MODIFY COLUMN `operation_time` datetime(3) NOT NULL COMMENT '业务操作发生时间',
    MODIFY COLUMN `recovered_time` datetime(3) NOT NULL COMMENT '人工恢复完成时间',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `settlement_review_rate`
    COMMENT = '结算预审冻结的统一汇率矩阵',
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `review_order_no` varchar(19) NOT NULL COMMENT '结算预审单号',
    MODIFY COLUMN `source_currency` char(3) NOT NULL COMMENT '来源金额币种代码',
    MODIFY COLUMN `target_currency` char(3) NOT NULL COMMENT '目标结算币种代码',
    MODIFY COLUMN `direct_rate` decimal(24,12) NOT NULL COMMENT '来源币种兑换目标币种的直接汇率',
    MODIFY COLUMN `source_currency_exponent` tinyint NOT NULL COMMENT '来源币种小数位',
    MODIFY COLUMN `target_currency_exponent` tinyint NOT NULL COMMENT '目标结算币种小数位',
    MODIFY COLUMN `rate_source` varchar(64) NOT NULL COMMENT '汇率取值来源',
    MODIFY COLUMN `quote_id` varchar(128) DEFAULT NULL COMMENT '冻结采用的汇率报价标识',
    MODIFY COLUMN `source_quote_direction` varchar(16) NOT NULL COMMENT '原始报价方向',
    MODIFY COLUMN `effective_time` datetime(3) NOT NULL COMMENT '汇率生效时间',
    MODIFY COLUMN `locked_time` datetime(3) NOT NULL COMMENT '候选独占锁定时间',
    MODIFY COLUMN `locked_by` varchar(128) NOT NULL COMMENT '锁定汇率的操作人标识',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `settlement_review_summary`
    COMMENT = '结算预审冻结的财务汇总结果',
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `review_order_no` varchar(19) NOT NULL COMMENT '结算预审单号',
    MODIFY COLUMN `merchant_id` varchar(64) NOT NULL COMMENT '平台商户号',
    MODIFY COLUMN `payment_type` varchar(32) NOT NULL COMMENT '支付业务类型',
    MODIFY COLUMN `payment_method` varchar(32) NOT NULL COMMENT '收单支付方式',
    MODIFY COLUMN `transaction_type` varchar(32) NOT NULL COMMENT '收单交易类型',
    MODIFY COLUMN `result_item_type` varchar(32) NOT NULL COMMENT '结算结果明细类型',
    MODIFY COLUMN `fee_category` varchar(32) NOT NULL COMMENT '费用分类编码',
    MODIFY COLUMN `direction` varchar(8) NOT NULL COMMENT '金额收付方向',
    MODIFY COLUMN `source_currency` char(3) NOT NULL COMMENT '来源金额币种代码',
    MODIFY COLUMN `target_currency` char(3) NOT NULL COMMENT '目标结算币种代码',
    MODIFY COLUMN `transaction_count` bigint NOT NULL COMMENT '纳入汇总的交易笔数',
    MODIFY COLUMN `source_amount` decimal(24,8) NOT NULL COMMENT '按来源币种计价的汇总金额',
    MODIFY COLUMN `target_amount` decimal(24,8) NOT NULL COMMENT '按目标币种计价的汇总金额',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `sys_account_mfa_log`
    MODIFY COLUMN `user_agent` varchar(512) DEFAULT NULL COMMENT '客户端用户代理信息',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `sys_account_mfa_token`
    MODIFY COLUMN `user_agent` varchar(512) DEFAULT NULL COMMENT '客户端用户代理信息',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `sys_email_verification_request`
    MODIFY COLUMN `state` varchar(30) NOT NULL COMMENT '邮箱验证请求状态：待验证、已验证、已完成、已过期、已锁定、已替代或已阻断',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `sys_login_log`
    MODIFY COLUMN `user_agent` varchar(500) DEFAULT NULL COMMENT '客户端用户代理信息',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `sys_login_session`
    MODIFY COLUMN `user_agent` varchar(500) DEFAULT NULL COMMENT '客户端用户代理信息',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `sys_mq_outbox`
    MODIFY COLUMN `topic` varchar(128) NOT NULL COMMENT '消息队列主题',
    MODIFY COLUMN `tag` varchar(128) DEFAULT NULL COMMENT '消息队列标签',
    MODIFY COLUMN `event_status` varchar(16) NOT NULL DEFAULT 'INIT' COMMENT '消息发件箱状态：初始、处理中、等待重试、已发送或已关闭',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `sys_notice`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
    MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `deleted` bigint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识，零表示未删除',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `sys_password_reset_request`
    MODIFY COLUMN `state` varchar(30) NOT NULL COMMENT '密码重置请求状态：待邮箱验证、待多因素验证、就绪、已完成、已过期、已锁定或已阻断',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_abnormal_event`
    MODIFY COLUMN `abnormal_level` varchar(32) NOT NULL COMMENT '异常等级：低、中、高或严重',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_abnormal_event_202603`
    MODIFY COLUMN `abnormal_level` varchar(32) NOT NULL COMMENT '异常等级：低、中、高或严重',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_abnormal_event_202604`
    MODIFY COLUMN `abnormal_level` varchar(32) NOT NULL COMMENT '异常等级：低、中、高或严重',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_abnormal_event_202701`
    MODIFY COLUMN `abnormal_level` varchar(32) NOT NULL COMMENT '异常等级：低、中、高或严重',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_authentication_info`
    MODIFY COLUMN `xid` varchar(256) DEFAULT NULL COMMENT '第一版三域安全验证协议的外部交易标识',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_authentication_info_202603`
    MODIFY COLUMN `xid` varchar(256) DEFAULT NULL COMMENT '第一版三域安全验证协议的外部交易标识',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_authentication_info_202604`
    MODIFY COLUMN `xid` varchar(256) DEFAULT NULL COMMENT '第一版三域安全验证协议的外部交易标识',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_authentication_info_202701`
    MODIFY COLUMN `xid` varchar(256) DEFAULT NULL COMMENT '第一版三域安全验证协议的外部交易标识',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_callback_inbox`
    MODIFY COLUMN `inbox_status` varchar(16) NOT NULL COMMENT '回调收件箱状态：已接收、处理中、待重试、已处理或已关闭',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_card_vault`
    MODIFY COLUMN `pan_iv` varchar(32) NOT NULL COMMENT '卡号加密使用的初始化向量',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_card_vault_202603`
    MODIFY COLUMN `pan_iv` varchar(32) NOT NULL COMMENT '卡号加密使用的初始化向量',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_card_vault_202604`
    MODIFY COLUMN `pan_iv` varchar(32) NOT NULL COMMENT '卡号加密使用的初始化向量',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_card_vault_202701`
    MODIFY COLUMN `pan_iv` varchar(32) NOT NULL COMMENT '卡号加密使用的初始化向量',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_clearing_detail`
    MODIFY COLUMN `component_type` varchar(24) NOT NULL COMMENT '清分分量类型：本金、比例费用、固定费用、限额调整、冲正或调整',
    MODIFY COLUMN `limit_evaluation_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '限额评估状态：无需评估、清分阶段已确定或等待结算汇率',
    MODIFY COLUMN `applied_limit` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '限额命中类型：NONE未命中，MINIMUM最低限额，MAXIMUM最高限额',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_clearing_detail_202603`
    MODIFY COLUMN `component_type` varchar(24) NOT NULL COMMENT '清分分量类型：本金、比例费用、固定费用、限额调整、冲正或调整',
    MODIFY COLUMN `limit_evaluation_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '限额评估状态：无需评估、清分阶段已确定或等待结算汇率',
    MODIFY COLUMN `applied_limit` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '限额命中类型：NONE未命中，MINIMUM最低限额，MAXIMUM最高限额',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_clearing_detail_202604`
    MODIFY COLUMN `component_type` varchar(24) NOT NULL COMMENT '清分分量类型：本金、比例费用、固定费用、限额调整、冲正或调整',
    MODIFY COLUMN `limit_evaluation_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '限额评估状态：无需评估、清分阶段已确定或等待结算汇率',
    MODIFY COLUMN `applied_limit` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '限额命中类型：NONE未命中，MINIMUM最低限额，MAXIMUM最高限额',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_clearing_detail_202701`
    MODIFY COLUMN `component_type` varchar(24) NOT NULL COMMENT '清分分量类型：本金、比例费用、固定费用、限额调整、冲正或调整',
    MODIFY COLUMN `limit_evaluation_status` varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '限额评估状态：无需评估、清分阶段已确定或等待结算汇率',
    MODIFY COLUMN `applied_limit` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '限额命中类型：NONE未命中，MINIMUM最低限额，MAXIMUM最高限额',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_merchant_snapshot`
    MODIFY COLUMN `merchant_category_code` varchar(16) DEFAULT NULL COMMENT '商户类别码',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_merchant_snapshot_202603`
    MODIFY COLUMN `merchant_category_code` varchar(16) DEFAULT NULL COMMENT '商户类别码',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_merchant_snapshot_202604`
    MODIFY COLUMN `merchant_category_code` varchar(16) DEFAULT NULL COMMENT '商户类别码',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_merchant_snapshot_202701`
    MODIFY COLUMN `merchant_category_code` varchar(16) DEFAULT NULL COMMENT '商户类别码',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_operation`
    MODIFY COLUMN `refund_scope` varchar(32) DEFAULT NULL COMMENT '退款范围：FULL全额，PARTIAL部分，VOID撤销',
    MODIFY COLUMN `execution_mode` varchar(32) DEFAULT NULL COMMENT '资金执行方式：渠道执行',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_operation_202603`
    MODIFY COLUMN `refund_scope` varchar(32) DEFAULT NULL COMMENT '退款范围：FULL全额，PARTIAL部分，VOID撤销',
    MODIFY COLUMN `execution_mode` varchar(32) DEFAULT NULL COMMENT '资金执行方式：渠道执行',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_operation_202604`
    MODIFY COLUMN `refund_scope` varchar(32) DEFAULT NULL COMMENT '退款范围：FULL全额，PARTIAL部分，VOID撤销',
    MODIFY COLUMN `execution_mode` varchar(32) DEFAULT NULL COMMENT '资金执行方式：渠道执行',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_operation_202701`
    MODIFY COLUMN `refund_scope` varchar(32) DEFAULT NULL COMMENT '退款范围：FULL全额，PARTIAL部分，VOID撤销',
    MODIFY COLUMN `execution_mode` varchar(32) DEFAULT NULL COMMENT '资金执行方式：渠道执行',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_refund_approval`
    MODIFY COLUMN `approval_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '退款审批状态：待审批、已通过、已拒绝或已过期',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_reserve_clearing_detail`
    MODIFY COLUMN `reserve_action_type` varchar(16) NOT NULL COMMENT '保证金清分动作：留存、退回、释放或调整',
    MODIFY COLUMN `record_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '清分事实状态：ACTIVE当前有效，SUPERSEDED已被替代',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_reserve_clearing_detail_202603`
    MODIFY COLUMN `reserve_action_type` varchar(16) NOT NULL COMMENT '保证金清分动作：留存、退回、释放或调整',
    MODIFY COLUMN `record_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '清分事实状态：ACTIVE当前有效，SUPERSEDED已被替代',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_reserve_clearing_detail_202604`
    MODIFY COLUMN `reserve_action_type` varchar(16) NOT NULL COMMENT '保证金清分动作：留存、退回、释放或调整',
    MODIFY COLUMN `record_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '清分事实状态：ACTIVE当前有效，SUPERSEDED已被替代',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_reserve_clearing_detail_202701`
    MODIFY COLUMN `reserve_action_type` varchar(16) NOT NULL COMMENT '保证金清分动作：留存、退回、释放或调整',
    MODIFY COLUMN `record_status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '清分事实状态：ACTIVE当前有效，SUPERSEDED已被替代',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_reserve_clearing_state`
    MODIFY COLUMN `reserve_status` varchar(24) NOT NULL DEFAULT 'OPEN' COMMENT '保证金状态：未完结、已全部退回、已全部释放或已调整',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_reserve_clearing_state_202603`
    MODIFY COLUMN `reserve_status` varchar(24) NOT NULL DEFAULT 'OPEN' COMMENT '保证金状态：未完结、已全部退回、已全部释放或已调整',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_reserve_clearing_state_202604`
    MODIFY COLUMN `reserve_status` varchar(24) NOT NULL DEFAULT 'OPEN' COMMENT '保证金状态：未完结、已全部退回、已全部释放或已调整',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `transaction_reserve_clearing_state_202701`
    MODIFY COLUMN `reserve_status` varchar(24) NOT NULL DEFAULT 'OPEN' COMMENT '保证金状态：未完结、已全部退回、已全部释放或已调整',
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `settlement_manual_review_task` COMMENT='冻结筛选条件并分段生成结算预审单的服务端任务', ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `settlement_reversal_order` COMMENT='结算冲正申请及双人复核主单，保留冻结的入账身份', ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `settlement_review_candidate` COMMENT='结算预审候选的不可变选择与锁定快照', ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `settlement_review_decision_task` COMMENT='可恢复的结算预审双人复核分段决策任务', ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `settlement_review_order` COMMENT='结算预审主单及双人复核的不可变审计事实', ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `settlement_review_segment` COMMENT='一个逻辑结算预审单对应的物理处理分段', ALGORITHM=INPLACE, LOCK=NONE;
