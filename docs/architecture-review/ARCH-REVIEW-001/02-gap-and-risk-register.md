# ARCH-REVIEW-001 差距与风险登记表

> 状态说明（2026-08-02）：本文是迁移前风险快照，行号、分支和旧自研分表结论只用于追溯。
> 已关闭项和当前剩余门禁以 `07-test-and-acceptance-matrix.md` 第 5.9 节起的验收记录为准。

## 1. 风险数量

| 风险级别 | 数量 | 定义 |
|---|---:|---|
| P0 | 7 | 可能导致错误交易、绕过风控、错误数据、错误更新、资金或合规风险 |
| P1 | 12 | 影响稳定性、性能、审计、扩展性 |
| P2 | 5 | 代码规范、维护性和长期治理问题 |

## 2. 风险登记

| 编号 | 专题 | 问题 | 代码证据 | 影响 | 风险级别 | 触发场景 | 建议方向 |
|---|---|---|---|---|---|---|---|
| R-001 | 风控 | 后续交易未调用内风控 | `DefaultCaptureTransactionPreparationService`、`DefaultRefundTransactionPreparationService`、`DefaultVoidTransactionPreparationService`、`DefaultIncrementalAuthorizationTransactionPreparationService` 构造器未注入 `PaymentRiskInvokeService`；`DefaultPaymentTransactionPreparationService.java:287-311` 仅首次交易调用 | Capture/Refund/Void/Incremental Auth 可绕过 AML、黑名单、频率、限额、3DS 规则 | P0 | 原授权通过后执行高风险请款、退款、撤销、增量授权 | 为后续交易设计 `RiskEvaluationScenario`，在状态机/金额校验后、渠道路由前执行 scoped risk |
| R-002 | 风控/事件 | 未执行风控的后续交易被记录为 PASS | `DefaultTransactionRecordService.java:887-933` 调用 `recordFlowEvents(..., PaymentRiskDecisionEnum.PASS, now)` | 审计时间轴显示虚假风控通过，掩盖真实绕过 | P0 | 管理端查看后续交易详情或合规审计追溯 | 事件必须按真实风控决策写入；未执行时只能记录 `RISK_SKIPPED`，并说明策略原因 |
| R-003 | 风控 | Noop 风控默认启用，配置缺失即跳过 | `NoopPaymentRiskInvokeService.java:18-33` 使用 `matchIfMissing=true`；`application.yml:15-20` 默认 `remote-enabled=false`；`service-payment-dev.yaml` 未覆盖 | 环境配置缺失或灰度误配会使真实交易跳过风控 | P0 | UAT/生产 Nacos 未配置 `payment.risk-client.remote-enabled=true` | 生产默认 fail-closed：缺失配置禁止启动或拒绝交易；Noop 限制为 local/test profile |
| R-004 | 风控 | `service-risk` 未消费管理端 AML/黑白名单/规则表 | `DefaultRiskEvaluationService.java:69-125` 只用硬编码 source keyword、IP、金额、3DS；`RiskFunctionDefinition.java` 和 `risk-management-schema.sql` 存在大量管理配置 | 管理端配置看似完整但不影响交易，形成重大合规落差 | P0 | 管理员新增 AML/黑名单后真实交易仍可通过 | 建立运行时 Repository/Cache，读取启用、有效期、商户范围、配置版本，写入评估记录和命中明细 |
| R-005 | 风控 | 风控请求字段不足，无法执行多数已建规则 | `RiskPaymentRiskInvokeService.java:120-227` 只传 BIN/后四、邮箱、billingCountry、IP、sourceUrl、3DS 等 | 卡号指纹、手机号、姓名、地址、收货、发卡国、设备、Customer ID、法人/企业等规则无法命中 | P0 | 管理端启用对应规则，但交易请求没有匹配输入 | 定义 `RiskPaymentContext`，OpenAPI/payment/checkout 统一采集、标准化、HMAC、脱敏并传递所需字段 |
| R-006 | 分表 | 缺少永久分片定位表，长期依赖交易号解析时间 | `TransactionShardingKeyParser.java:49-87` 解析失败返回 null；`DefaultTransactionRecordService.java:607-628` 解析失败 `ORDER_NOT_FOUND` | 导入历史、格式变更、外部迁移、旧交易补单无法定位原表；后续交易和回调可能失败 | P0 | `sourceTransactionId` 或 `channelOrderNo` 不含标准时间前缀 | 新增永久 `transaction_shard_locator`，交易创建同步写，历史回填，单笔查询/后续交易先查 locator |
| R-007 | 分表/Mapper | 分表 Mapper 继承 `BaseMapper` 并绑定逻辑表，存在模板表误写风险 | `TransactionOrderMapper.java:23`、`TransactionOperationMapper.java:23`、`TransactionFlowEventMapper.java:20` 继承 `BaseMapper`；实体 `@TableName("transaction_*")` | 任意默认 `insert/selectById/updateById` 调用会绕过分表路由访问逻辑/模板表 | P0 | 后续开发新增默认 Mapper 调用、测试工具或框架自动注入调用 | 建立 sharded repository 边界；禁止核心分表 Mapper 暴露 BaseMapper 默认方法；增加 ArchUnit/扫描测试 |
| R-008 | 分表查询 | 范围裁剪只返回表名，不返回有效 begin/end | `ShardingTableRangeResolver.java:80-99` 计算 `safeBegin/safeEnd` 后只返回 `tables`；查询 Service 继续传原始时间 | SQL 条件与表路由裁剪不一致，边界查询和性能预算不可控 | P1 | begin 早于首表、end 晚于当前时间、跨未来时间 | 返回 `ShardingResolvedRange{tables,effectiveBegin,effectiveEnd}`，调用方只使用裁剪后时间 |
| R-009 | 分表查询 | 存在全历史分表扫描兜底 | `DefaultTransactionRecordService.java:643,667` 使用 `resolvePhysicalTables(..., null, now)` | 历史季度增多后查询和幂等冲突校验性能下降，且表缺失时更难定位 | P1 | 没有可解析 transactionId，只按商户订单号或创建冲突查询 | 用 locator、幂等唯一索引、最大时间跨度替代全历史扫描 |
| R-010 | 分表查询 | Payment/Admin/Merchant 重复实现跨表 count/page/summary | `DefaultTransactionQueryService.java:409-2061`、`JdbcAdminTransactionQueryService.java:444-1502`、`JdbcMerchantTransactionQueryService.java:275-1108` | 查询逻辑和性能策略分散，边界修复容易漏模块 | P1 | 修复分页或裁剪策略时只改一个模块 | 抽象 `ShardedTransactionQueryRepository`，统一跨表分页、排序、count、summary |
| R-011 | Mapper | `${physicalTableName}` 动态表名依赖调用方纪律 | `TransactionOrderMapper.java:32-78`、`TransactionFlowEventMapper.java:29-83`、多交易 Mapper | 虽有路由器校验，但 Mapper 方法本身不能证明参数来源安全 | P1 | 新增调用方直接传外部表名或未校验字符串 | 物理表名封装成不可从请求构造的 value object；Mapper 放 infrastructure 包并限制访问 |
| R-012 | 事件 | 事件使用同一个 `now` 批量补写，不是真实节点时间 | `DefaultTransactionRecordService.java:443-482, 3090-3127, 3319-3356` | API、风控、路由、渠道、入库节点时间相同，无法还原真实耗时和顺序 | P1 | 交易超时、渠道慢、风控慢、路由异常排障 | 引入 `TransactionFlowEventRecorder`，在真实节点开始/结束处记录 |
| R-013 | 事件 | 事件模型缺少链路和幂等字段 | `transaction-core-schema.sql:562-590`、`TransactionFlowEventDO.java` | 无法按 traceId/requestId 查询，不能记录耗时、重试、稳定业务幂等键和详情 JSON | P1 | 重复回调、MQ 重试、通知重试、跨服务排障 | 增加 `result_code/result_message/trace_id/request_id/duration_millis/attempt_no/event_key/detail_json` |
| R-014 | 事件/回调 | 回调只记录 `CHANNEL_CALLBACK_PROCESSED` | `DefaultTransactionRecordService.java:2320-2357` | 收到、验签、解析、状态应用、终态忽略无法区分 | P1 | 渠道重复回调、验签失败、字段解析失败、终态保护 | 分拆 `CHANNEL_CALLBACK_RECEIVED/VERIFIED/PARSED/APPLIED/IGNORED` |
| R-015 | 事件/MQ/通知 | MQ Outbox 和商户通知未进入统一流程事件 | `DefaultTransactionEventOutboxService.java:61-90`、`DefaultTransactionEventOutboxRelayService.java:83-148`、`DefaultTransactionMerchantNotificationService.java:261-431` | 后台时间线看不到异步消息创建、发送、失败、通知尝试 | P1 | 商户称未收到通知、MQ 重试、事件积压 | 专表保存明细，`transaction_flow_event` 保存摘要节点并用 referenceId 关联 |
| R-016 | 事件 | 流程事件缺少业务幂等唯一键 | `transaction-core-schema.sql:585-589` 仅 `uk_flow_event_id` | 重试、回调、补偿可能重复插入同一语义事件 | P1 | MQ 重放、回调重复、补偿任务重跑 | 设计 `event_key = operationId + stage + type + referenceId + attemptNo` 并唯一约束 |
| R-017 | 风控记录 | 风控记录和命中明细表未由运行时写入 | `RiskManagementMapper.java:1293-1535` 仅管理查询；`DefaultRiskEvaluationService.java:223-230` 只生成 `riskRecordNo` | 无法追溯风控命中、配置版本、规则快照 | P1 | 合规审计、交易争议、规则误杀复盘 | service-risk 写 `risk_evaluation_record` 和 `risk_evaluation_hit_detail`，支付主单/事件关联 `riskRecordNo` |
| R-018 | 敏感数据 | 名单匹配摘要使用普通 SHA-256 而非 HMAC | `RiskListValueNormalizer.java:709` 使用 `MessageDigest.getInstance("SHA-256")` | 低熵字段可能被离线枚举，且密钥轮换不可控 | P1 | 邮箱、手机号、IP、卡号摘要泄露或备份外流 | 使用 HMAC-SHA256 + keyVersion；历史双读迁移，逐步停用 SHA-256 |
| R-019 | 风控异常策略 | 远程风控异常/超时的业务状态策略未形成产品门禁 | `RiskInternalRestClient` 捕获 `RestClientException` 抛 `ServiceException(BAD_GATEWAY)`；Open Decisions 需确认 | 是否失败、处理中、人工复核缺少一致规则 | P1 | service-risk 不可用、超时、返回未知决策 | 明确 fail-closed、fail-pending 或 REVIEW 策略，并写入事件和风控记录 |
| R-020 | 查询性能 | 缺少最大查询跨度和跨季度性能预算 | `DefaultTransactionQueryService`、`JdbcAdminTransactionQueryService`、`JdbcMerchantTransactionQueryService` 多处按表循环 | 查询时间越长越慢，count/page/summary 成本不可控 | P1 | 管理端查询多年历史或无时间条件 | 配置最大跨度；普通查询强制时间，后台导出走异步任务 |
| R-021 | 枚举治理 | 流程事件类型/阶段/状态大量字符串散落 | `DefaultTransactionRecordService.java:3097-3126, 2340-2343` | 拼写不一致、前端字典和后端事件难统一 | P2 | 新增事件类型、前端展示时间线 | 建立 `TransactionFlowEventTypeEnum/StageEnum/StatusEnum` |
| R-022 | 架构决策 | 自研分表和 ShardingSphere 缺少正式取舍记录 | 当前代码存在 `PaymentOrderShardingAlgorithm` 但主路径使用应用层路由 | 后续团队可能重复引入路由方案，造成双系统 | P2 | 大规模重构或新服务查询接入 | 短期保留自研并收口；中长期单独 PoC ShardingSphere |
| R-023 | 配置治理 | 生产 Nacos 风控开关未知 | 仓库只见 `application-prod.yml` 导入 Nacos，未见 prod DataId 内容 | 无法从代码仓库证明生产风控已开启 | P2 | 上线前审核或安全审计 | 将生产关键开关纳入上线门禁和配置快照审计 |
| R-024 | 测试治理 | 缺少架构守护测试覆盖分表/风控/事件边界 | 已有单元测试覆盖部分交易流程，但未见统一 ArchUnit 或扫描型门禁 | 后续回归可能再次引入 BaseMapper、Noop、伪事件 | P2 | 新增交易类型或 Mapper | 增加架构测试：禁止 sharded Mapper 默认方法、禁止 Noop 进入非 local、校验事件真实时间 |

## 3. 专题差距矩阵

| 目标要求 | 当前实现 | 差距 | 关联风险 |
|---|---|---|---|
| 所有分表写入必须传 `transactionDateTime` | 主路径写入满足；BaseMapper 默认方法不受约束 | 统一入口可绕过 | R-007、R-011 |
| 单笔查询无时间时先查永久定位 | 解析交易号/operationId 时间 | 缺永久定位表 | R-006 |
| 禁止缺少时间扫描历史季度 | 存在 `null -> now` 扫描 | 性能和准确性风险 | R-009、R-020 |
| 管理端风控配置进入交易运行时 | 管理端 CRUD 存在，service-risk 不读取 | 配置不生效 | R-004 |
| 首次和后续交易都要按场景风控 | 只有首次调用 | 后续绕过 | R-001 |
| 风控事件必须反映真实结果 | 后续固定 PASS | 审计错误 | R-002 |
| 事件按真实节点和时间写入 | 事实记录后批量补写 | 时间/顺序不真实 | R-012 |
| 回调、MQ、通知进入统一时间轴 | 只在日志/专表记录 | 审计链断裂 | R-014、R-015 |
