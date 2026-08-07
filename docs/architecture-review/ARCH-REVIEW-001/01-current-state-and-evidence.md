# ARCH-REVIEW-001 当前状态与代码证据

> 状态说明（2026-08-02）：本文保留 2026-07-28 迁移前的审计证据，不代表当前实现。
> 第一版 ShardingSphere 架构以 `07-test-and-acceptance-matrix.md` 第 5.9 节及 `docs/architecture/shardingsphere-sql-compatibility-matrix.md` 为准。

## 1. 审计基线

| 项目 | 当前值 |
|---|---|
| 审计日期 | 2026-07-28 |
| 工作目录 | `/Users/scott/Documents/code/ideaCodex/acquiring/acquiring-orchestration` |
| 当前分支 | `feature_scott_payment` |
| HEAD | `920e36e1026c0f7ee28653be0081a5507ffdc644` |
| 工作区 | 非干净，审计前已存在 `.gitignore` 修改和 `scripts/*.py` 未跟踪文件 |
| 本轮允许动作 | 只读扫描、代码证据分析、新增本目录审计文档 |
| 本轮禁止动作 | 修改业务 Java、Mapper/XML、DDL、YAML/properties/Nacos；启动服务；执行迁移；发送交易；Git reset/stash/commit/push |

## 2. 扫描范围

| 范围 | 数量 |
|---|---:|
| `rg --files` 全项目文件 | 1411 |
| Java 文件 | 1202 |
| XML/SQL/YAML/properties/Markdown 文件 | 193 |

重点覆盖模块：

| 模块 | 覆盖内容 |
|---|---|
| `service-openapi` | OpenAPI 控制器、安全注解、验签、防重放、商户 IP 白名单、请求来源字段 |
| `service-payment` | 支付创建、授权、请款、退款、撤销、增量授权、查询、回调、通知、MQ Outbox、交易事实持久化 |
| `service-risk` | 内部风控评估接口、应用服务、默认风控决策实现 |
| `service-admin` | AML、黑名单、白名单、规则风控管理端能力、风控记录查询 |
| `service-merchant` | 商户端跨分表交易查询 |
| `component-library/component-db` | 季度分表配置、解析、物理表名、范围路由、DDL 维护、ID 自增规则 |
| `docs/sql`、`docs/deployment/nacos` | DDL、分表配置、风控管理表、Nacos 示例配置 |

## 3. 交易季度分表现状

当前实现是自研应用层季度分表，不是 ShardingSphere 运行时路由。路由由 `component-db` 中的分表组件生成物理表名，再由业务 Service 把物理表名传入 Mapper 的 `${physicalTableName}` SQL。

| 证据点 | 文件与位置 | 类/方法 | 上游 | 下游 | 当前实际结果 |
|---|---|---|---|---|---|
| 分表配置前缀、分表字段、时区、ID 规则 | `component-library/component-db/src/main/java/com/scott/payment/component/db/sharding/PaymentQuarterShardingProperties.java:12-37, 111-137` | `PaymentQuarterShardingProperties` | Nacos/YAML 配置绑定 | `ShardingTableRangeResolver`、DDL 维护 | `global-payment.sharding`；字段 `transaction_date_time`；时区 `Asia/Shanghai`；ID 模式 `mysql-auto-increment-prefix`，`yyyyQQ` + 12 位序号 |
| 物理表范围配置 | `docs/deployment/nacos/sharding-dev.yaml` | Nacos 示例 | 应用启动配置 | 分表规则 | 交易逻辑表协议支持上限为 `2099 Q4`，Admin 仅展开滚动规划窗口，格式 `%s_%d%02d` |
| 单表路由强制分表时间 | `component-library/component-db/src/main/java/com/scott/payment/component/db/sharding/ShardingDataTemplate.java:143-170` | `executeSingle`、`validateSingleContext` | 业务 Service | `ShardingTableRangeResolver.physicalTable` | insert/update/queryOne 必须有 `shardingTime`，写操作必须主库 |
| 范围路由只返回表名 | `component-library/component-db/src/main/java/com/scott/payment/component/db/sharding/ShardingTableRangeResolver.java:80-99` | `physicalTablesInRange` | 查询 Service | Mapper/JdbcTemplate | 内部裁剪 begin/end，但只返回物理表名，调用方仍使用原始查询时间 |
| 物理表名安全校验 | `component-library/component-db/src/main/java/com/scott/payment/component/db/sharding/ShardingPhysicalTableNameResolver.java:21-43, 84-92` | `physicalTableName`、`requireSafeIdentifier` | 分表路由 | Mapper SQL `${}` | 生成 `logical_table_yyyyQQ` 并校验标识符正则 |
| 交易号解析分表时间 | `component-library/component-db/src/main/java/com/scott/payment/component/db/sharding/TransactionShardingKeyParser.java:49-62, 75-87` | `parseTransactionDateTime`、`parseOperationDateTime` | 交易定位、查询 | 单表路由 | 从 `yyyyMMddHHmmssSSS` 前缀解析时间；失败返回 `null` |
| 自增起始值计算 | `component-library/component-db/src/main/java/com/scott/payment/component/db/sharding/ShardingAutoIncrementValueCalculator.java:38-60` | `calculate` | DDL 维护 | 物理表 AUTO_INCREMENT | `yyyyQQ * 10^sequenceWidth + sequence`，检查 BIGINT 上限 |
| 分表 Mapper 暴露默认 BaseMapper | `service-payment/src/main/java/com/scott/payment/payment/mapper/TransactionOrderMapper.java:23`、`TransactionOperationMapper.java:23`、`TransactionFlowEventMapper.java:20` | 多个 Mapper | 业务 Service 或框架注入方 | MyBatis Plus 默认方法 | Mapper 同时有物理表方法和继承的 `insert/selectById/updateById` 默认方法 |
| 实体绑定逻辑表 | `service-payment/src/main/java/com/scott/payment/payment/entity/TransactionOrderDO.java:21-23`、`TransactionOperationDO.java:22-24`、`TransactionFlowEventDO.java:21-22` | `@TableName` | BaseMapper 默认方法 | 逻辑/模板表 | 默认方法会访问 `transaction_order`、`transaction_operation`、`transaction_flow_event` 逻辑表 |
| Mapper 动态物理表 | `service-payment/src/main/java/com/scott/payment/payment/mapper/TransactionOrderMapper.java:32-78`、`TransactionFlowEventMapper.java:29-49` | `insertPhysical` | `DefaultTransactionRecordService` | 物理分表 | SQL 通过 `${physicalTableName}` 插入，安全依赖上游传入已路由表名 |

## 4. 交易调用链与分表访问

### 4.1 首次交易

| 步骤 | 证据 | 当前行为 |
|---|---|---|
| OpenAPI 入口 | `service-openapi/src/main/java/com/scott/payment/openapi/api/rest/payment/v1/*Controller.java` 使用 `/api/rest/payment/{version}` 和 `@VerificationAndProcessing` | 对外支付、授权、预授权、查询等接口进入 OpenAPI 安全链路 |
| 请求来源填充 | `service-openapi/src/main/java/com/scott/payment/openapi/service/impl/PaymentServiceImpl.java:57-79, 539, 796` | `payerIp` 来自 `X-Forwarded-For`/`X-Real-IP`/remoteAddr，`sourceUrl` 来自 `Origin` 或 `Referer` |
| 首次交易准备 | `service-payment/src/main/java/com/scott/payment/payment/service/impl/DefaultPaymentTransactionPreparationService.java:224-330` | 创建幂等记录、生成 `operationId/transactionId`、执行风控、路由、准备渠道请求、记录事实和 Outbox |
| 风控调用位置 | `DefaultPaymentTransactionPreparationService.java:287-311` | `paymentRiskInvokeService.checkPreRoute(commandDTO)` 在渠道路由前执行 |
| 事实入库 | `service-payment/src/main/java/com/scott/payment/payment/service/impl/DefaultTransactionRecordService.java:436-482` | 用同一个 `now` 构造主单、动作单、状态历史、渠道审计、支付方式信息、流程事件、API 日志、商户通知任务 |
| 分表写入 | `DefaultTransactionRecordService.java:452-458` | 按 `commandDTO.transactionDateTime` 解析 `transaction_order`、`transaction_operation`、`transaction_status_history` 物理表并插入 |

### 4.2 后续交易

| 交易类型 | 证据 | 当前行为 |
|---|---|---|
| Capture | `DefaultCaptureTransactionPreparationService.java:158-188, 290-332, 744` | 构造器没有 `PaymentRiskInvokeService`；通过原交易号定位 source order，路由、准备渠道请求、记录 follow-up |
| Refund | `DefaultRefundTransactionPreparationService.java:278-320, 748` | 无风控调用；依赖 `findSourceOperationByTransactionId`/`findSourceOrderByTransactionId` 定位原交易 |
| Void | `DefaultVoidTransactionPreparationService.java:277-318, 678` | 无风控调用；记录 follow-up 事实 |
| Incremental Authorization | `DefaultIncrementalAuthorizationTransactionPreparationService.java:223-330, 697` | 无风控调用；仅做状态机、金额和冲突校验后路由 |
| 后续事件记录 | `DefaultTransactionRecordService.java:887-933` | 所有 follow-up 调用 `recordFlowEvents(..., PaymentRiskDecisionEnum.PASS, now)` |

## 5. 交易定位与范围扫描现状

| 证据点 | 文件与位置 | 方法 | 当前实际结果 |
|---|---|---|---|
| 源交易定位依赖 ID 时间 | `DefaultTransactionRecordService.java:607-628` | `findSourceOperationByTransactionId` | 先从 `sourceTransactionId` 解析时间，失败直接 `ORDER_NOT_FOUND`，没有永久定位兜底 |
| 源主单定位依赖 operationId 时间 | `DefaultTransactionRecordService.java:584-603` | `findSourceOrderByTransactionId` | 先查 source operation，再解析 `operationId` 或 operation 行时间定位主单 |
| 商户订单查询全历史扫描 | `DefaultTransactionRecordService.java:629-647` | `findOperationsByMerchantOrder` | 有 transactionId 可单表；解析失败时 `resolvePhysicalTables(transaction_operation, null, now)` |
| 首次交易冲突扫描 | `DefaultTransactionRecordService.java:661-669` | `findInitialOperationsByMerchantOrder` | 从配置起始季度扫描到当前季度 |
| 渠道交易定位 | `DefaultTransactionRecordService.java:794-810` | `findOperationByChannelTransaction` | 解析 `channelOrderNo` 时间，失败报 `ORDER_NOT_FOUND`；成功后从解析时间扫到当前 |
| Payment 查询跨表 | `DefaultTransactionQueryService.java:409, 459, 2060-2061` | 多个 `page*` 方法 | 循环物理表计数和查询 |
| Admin 查询跨表 | `service-admin/src/main/java/com/scott/payment/admin/service/impl/JdbcAdminTransactionQueryService.java:444-698, 1502` | `JdbcAdminTransactionQueryService` | 直接 `.formatted(table, ...)` 拼 SQL |
| Merchant 查询跨表 | `service-merchant/src/main/java/com/scott/payment/merchant/service/impl/JdbcMerchantTransactionQueryService.java:275-420, 1108` | `JdbcMerchantTransactionQueryService` | 直接 `.formatted(table, ...)` 拼 SQL |

结论：当前没有永久分片定位表，幂等记录不能承担长期定位职责；交易号格式变化、历史导入、外部迁移或幂等过期后，定位和后续交易存在失败或全表扫描风险。

## 6. 内风控现状

### 6.1 运行时调用链

| 证据点 | 文件与位置 | 类/方法 | 上游 | 下游 | 当前实际结果 |
|---|---|---|---|---|---|
| 风控接口抽象 | `service-payment/src/main/java/com/scott/payment/payment/service/PaymentRiskInvokeService.java:15-23` | `checkPreRoute` | 首次交易准备 | Noop 或远程实现 | 只定义路由前风控 |
| 首次交易真实调用 | `DefaultPaymentTransactionPreparationService.java:287-311` | `prepareInitialTransaction` | OpenAPI/内部支付创建 | `PaymentRiskInvokeService` | 首次支付/授权/预授权会在路由前调用 |
| 后续交易未注入风控 | `DefaultCaptureTransactionPreparationService.java:158-188`、`DefaultIncrementalAuthorizationTransactionPreparationService.java:223-236` | 构造器 | 后续交易准备 | 无 | Capture/Refund/Void/Incremental Auth 没有 `PaymentRiskInvokeService` 依赖 |
| Noop 默认启用 | `service-payment/src/main/java/com/scott/payment/payment/service/impl/NoopPaymentRiskInvokeService.java:18-33` | `checkPreRoute` | Spring 条件装配 | 返回 `PaymentRiskDecisionDTO.skip()` | `payment.risk-client.remote-enabled=false` 或缺失时风控跳过 |
| 基础配置关闭远程 | `service-payment/src/main/resources/application.yml:15-20` | `payment.risk-client.remote-enabled` | 应用配置 | Noop 条件 | 默认 `false` |
| dev Nacos 未覆盖 | `docs/deployment/nacos/service-payment-dev.yaml:1-18` | service-payment dev 配置 | Nacos 示例 | Spring 配置 | 未出现 `payment.risk-client.remote-enabled=true` |
| prod/uat 文件只导入 Nacos | `service-payment/src/main/resources/application-prod.yml:1-44`、`application-uat.yml:1-44` | profile 配置 | Nacos | 运行配置 | 本仓库无法确认生产 Nacos 是否开启远程风控 |
| 远程风控调用 | `RiskPaymentRiskInvokeService.java:51-86, 120-139` | `checkPreRoute`、`buildRequest` | 首次交易准备 | `RiskInternalClient.evaluatePayment` | 传递商户、金额、来源、IP、BIN、邮箱、3DS 等有限字段 |
| 风控 REST 端点 | `RiskInternalRestClient.java:40-68` | `evaluatePayment` | `RiskPaymentRiskInvokeService` | `http://service-risk/internal/risk/evaluate/payment` | 开启远程时调用 service-risk |
| service-risk 内部接口 | `service-risk/src/main/java/com/scott/payment/risk/api/internal/RiskInternalController.java:24-50` | `evaluatePayment` | service-payment | `RiskEvaluationApplicationService` | 内部 POST 接口返回风控结果 |
| service-risk 领域实现 | `service-risk/src/main/java/com/scott/payment/risk/service/impl/DefaultRiskEvaluationService.java:69-125` | `evaluatePayment` | 应用服务 | 内置规则 | 只用硬编码关键字、IP、金额、3DS 判断，未读管理端表 |

### 6.2 管理端能力与运行时消费

| 风控功能 | 管理端配置 | 数据表 | 运行时读取 | 交易链路调用 | 最终决策生效 | 结论 |
|---|---|---|---|---|---|---|
| AML 11 类 | `RiskFunctionDefinition.java:26-96`、`AdminRiskAmlController.java` | `risk_aml_*` | 未发现 service-risk 查询 | 未接入 | 否 | 主要是管理端 CRUD |
| 黑名单 18 类 | `RiskFunctionDefinition.java:104-223`、`AdminRiskBlackController.java` | `risk_black_*` | 未发现 service-risk 查询 | 未接入 | 否 | 主要是管理端 CRUD |
| 白名单 12 类 | `RiskFunctionDefinition.java` 白名单段 | `risk_white_*` | 未发现 service-risk 查询 | 未接入 | 否 | 主要是管理端 CRUD |
| 规则 4 类 | `RiskFunctionDefinition.java` 规则段、`AdminRiskRuleController.java` | `risk_rule_*` | 未发现 service-risk 查询 | 仅硬编码金额/3DS | 部分伪实现 | 管理端规则未真实消费 |
| 风控记录/命中 | `RiskManagementMapper.java:1293-1535`、`risk-management-schema.sql:50-84` | `risk_evaluation_record`、`risk_evaluation_hit_detail` | service-risk 未写入 | 支付侧只保存 `riskRecordNo` | 否 | 管理端可查表存在，但运行时无落库证据 |

### 6.3 风控请求字段

当前传入字段来自 `RiskPaymentRiskInvokeService.buildRequest` 和 `RiskPaymentEvaluateClientRequestDTO`：

| 已传字段 | 证据 |
|---|---|
| `merchantId`、`merchantOrderNo`、`transactionId`、`transactionType`、`paymentMethod`、`requestId` | `RiskPaymentRiskInvokeService.java:121-127` |
| `amount`、`currency`、`transactionDateTime`、`requestFingerprint` | `RiskPaymentRiskInvokeService.java:128-131` |
| `sourceUrl`、`payerIp`、`userAgent` | `RiskPaymentRiskInvokeService.java:132-134` |
| `subMerchantId`、`merchantCategory`、`subMerchantCountryCode` | `RiskPaymentRiskInvokeService.java:152-160` |
| `billingCountry`、`billingEmail` | `RiskPaymentRiskInvokeService.java:172-179` |
| `cardBrand`、`cardBin`、`cardLast4` | `RiskPaymentRiskInvokeService.java:191-207` |
| `threeDsEci`、`threeDsVersion`、`threeDsTransactionId` | `RiskPaymentRiskInvokeService.java:219-227` |

关键缺口：完整卡 HMAC 指纹、手机号、持卡人姓名、账单地址/邮编、收货地址/邮编/国家、发卡行国家、交易国家、设备指纹、Customer ID、法人、企业名、邮箱用户名/域名标准化字段、Origin 和 Referer 独立字段、风控配置版本。

### 6.4 敏感值处理

| 证据点 | 文件与位置 | 当前行为 |
|---|---|---|
| 管理端名单标准化 | `service-admin/src/main/java/com/scott/payment/admin/support/risk/RiskListValueNormalizer.java:80-87, 709` | 使用 `RiskSensitiveValueCrypto` 保存密文，匹配摘要使用普通 SHA-256 |
| 交易风险请求 | `RiskPaymentRiskInvokeService.java:191-207` | 从完整卡号提取 BIN/后四，不传完整 PAN，也未生成卡 HMAC 指纹 |
| 远程响应摘要 | `RiskInternalRestClient.java:229-236` | 日志摘要使用 SHA-256 |

结论：管理端名单和运行时匹配缺少统一 HMAC-SHA256 标准；普通 SHA-256 对低熵手机号、邮箱、卡 BIN、IP 等字段不适合作为最终匹配安全模型。

## 7. OpenAPI 安全和 IP/来源边界

| 证据点 | 文件与位置 | 当前行为 |
|---|---|---|
| 安全注解 | `service-openapi/src/main/java/com/scott/payment/openapi/annotation/VerificationAndProcessing.java:17-66` | 方法级注解要求 header、解密 DTO、Bean Validation |
| 支付控制器契约 | `service-openapi/src/main/java/com/scott/payment/openapi/api/rest/payment/v1/*Controller.java` | `/api/rest/payment/{version}` 下支付、授权、预授权、增量授权、请款、退款、撤销、查询使用注解 |
| 商户 OpenAPI IP 白名单 | `service-openapi/src/main/java/com/scott/payment/openapi/security/MerchantIpWhitelistAccessService.java:28-31, 90-116` | 只读取 `X-Gateway-Client-Ip`，按商户配置精确匹配白名单 |
| 付款人 IP 来源 | `PaymentServiceImpl.java:57-79, 796` | 付款人 IP 用 `X-Forwarded-For`/`X-Real-IP`/remoteAddr，服务于风控字段 |
| 安全拦截记录 | `service-openapi/src/main/java/com/scott/payment/openapi/security/SecurityInterceptEventRecorder.java` | 记录 OpenAPI 安全阻断事件，但不是 `transaction_flow_event` |

结论：商户 OpenAPI IP 白名单与付款人 IP AML/黑白名单/频率控制是两个不同概念。当前商户接口安全链路有实现，但未进入交易流程事件时间轴。

## 8. 交易流程事件现状

### 8.1 数据模型

| 证据点 | 文件与位置 | 当前字段/行为 |
|---|---|---|
| 实体绑定 | `service-payment/src/main/java/com/scott/payment/payment/entity/TransactionFlowEventDO.java:21-22` | `@TableName("transaction_flow_event")` |
| 字段范围 | `TransactionFlowEventDO.java:45-215`、`docs/sql/transaction-core-schema.sql:562-590` | 有 `flowEventId`、`transactionId`、`operationId`、`eventType`、`eventStage`、`eventStatus`、`eventName`、`eventContent`、`previousStatus`、`currentStatus`、`operatorType`、`referenceType`、`errorCode`、`eventTime`、`transactionDateTime` |
| 缺失字段 | 同上 | 缺少 `resultCode`、`resultMessage`、`traceId`、`requestId`、`durationMillis`、`attemptNo`、`eventKey`、`detailJson` |
| 唯一约束 | `docs/sql/transaction-core-schema.sql:585-589` | 只有 `uk_flow_event_id`，没有业务幂等键 |
| Mapper 动态表 | `TransactionFlowEventMapper.java:29-83` | 插入和查询均使用 `${physicalTableName}` |

### 8.2 写入点

| 模块 | 文件与方法 | 事件类型 | 写入时间来源 | 是否真实节点 | 是否批量补写 | 是否有业务幂等 |
|---|---|---|---|---|---|---|
| `service-payment` | `DefaultTransactionRecordService.recordInitialTransaction` | `API_ACCEPTED`、`RISK_CHECKED`、`ROUTE_SELECTED`、`CHANNEL_CALLED`、`STATUS_RECORDED` | 单个 `LocalDateTime now` | 否 | 是 | 否 |
| `service-payment` | `DefaultTransactionRecordService.recordFollowUpTransaction` | 同上 | 单个 `LocalDateTime now` | 否 | 是 | 否 |
| `service-payment` | `DefaultTransactionRecordService.insertCallbackStateAndFlow` | `CHANNEL_CALLBACK_PROCESSED` | 回调处理处传入 `now` | 部分 | 否 | 否 |

### 8.3 关键证据

| 结论 | 证据 |
|---|---|
| 首次交易结束后批量补事件 | `DefaultTransactionRecordService.java:443-482`：先用一个 `now` 写订单/动作/历史，再调用 `recordFlowEvents(..., now)` |
| 后续交易固定记录风控 PASS | `DefaultTransactionRecordService.java:887-933`：`recordFollowUpTransaction` 最后调用 `recordFlowEvents(..., PaymentRiskDecisionEnum.PASS, now)` |
| 事件状态多用最终状态 | `DefaultTransactionRecordService.java:3097-3126`：`API_ACCEPTED`、`RISK_CHECKED`、`CHANNEL_CALLED`、`STATUS_RECORDED` 多处使用 `resultDTO.getStatus()` 作为当前状态 |
| 单个批量写入方法 | `DefaultTransactionRecordService.java:3090-3127` | `recordFlowEvents` 一次写多个事件 |
| 单事件插入只接受 `now` | `DefaultTransactionRecordService.java:3319-3356` | `insertFlowEvent` 设置 `eventTime(now)` 和 `createTime(now)` |
| 回调仅记录处理完成 | `DefaultTransactionRecordService.java:2320-2357` | 只有 `CHANNEL_CALLBACK_PROCESSED`，没有收到、验签、解析、应用、忽略等拆分事件 |
| 商户通知有专表和日志，但不写流程事件 | `DefaultTransactionMerchantNotificationService.java:261-347, 402-431` | 记录通知任务状态和尝试日志，没有 `TransactionFlowEventMapper` |
| MQ Outbox 有专表和投递日志，但不写流程事件 | `DefaultTransactionEventOutboxService.java:61-90`、`DefaultTransactionEventOutboxRelayService.java:83-148` | 创建、投递、失败重试未进入 `transaction_flow_event` |

## 9. 当前核心结论

1. 分表：当前是自研应用层季度分表，主路径能按 `transaction_date_time` 路由写入，但 Mapper 继承 `BaseMapper` 和 `${physicalTableName}` 暴露使统一入口可被绕过；缺少永久分片定位表导致交易号解析失败后无法稳定定位历史交易。
2. 风控：首次交易存在路由前风控调用，但 Noop 默认开启且 `service-risk` 仍是硬编码骨架；管理端 AML/黑名单/白名单/规则配置没有被运行时读取；后续交易未接入风控。
3. 事件：当前流程事件不是按真实节点写入，而是交易事实记录阶段批量补写；后续交易会错误显示风控 PASS；回调、MQ、商户通知只在专表或日志记录，没有形成统一交易时间轴。
4. 统一风险：分表时间、风控记录、流程事件、幂等定位没有形成同一套不可变审计坐标，历史交易、跨季度后续交易、生产合规审计会受影响。
