# ARCH-REVIEW-001 预计影响文件清单

> 状态说明（2026-08-02）：本文只保留迁移前的预计清单，不代表当前待办；locator 和旧自研路由文件不得据此新增。
> 实际迁移范围及当前代码状态以 SQL 兼容矩阵和测试验收矩阵第 5.9 节为准。

本文件仅列出后续整改预计需要修改或新增的文件，不代表本轮已经修改。当前轮次未修改业务 Java、Mapper/XML、数据库 DDL 或配置。

## 1. 分表访问边界

| 阶段 | 模块 | 文件路径 | 类/表/配置 | 修改原因 | 修改类型 |
|---|---|---|---|---|---|
| Phase 1 | `component-library/component-db` | `component-library/component-db/src/main/java/com/scott/payment/component/db/sharding/ShardingTableAccess.java` | 新增 `ShardingTableAccess` | 返回物理表、裁剪后时间范围、表状态 | 新增 Java |
| Phase 1 | `component-library/component-db` | `component-library/component-db/src/main/java/com/scott/payment/component/db/sharding/ShardingResolvedRange.java` | 新增 range 结果对象 | 解决只返回表名的问题 | 新增 Java |
| Phase 1 | `component-library/component-db` | `component-library/component-db/src/main/java/com/scott/payment/component/db/sharding/ShardingTableRangeResolver.java` | `physicalTablesInRange` | 返回 `effectiveBegin/effectiveEnd` | 修改 Java |
| Phase 1 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/repository/TransactionOrderRepository.java` | 交易主单 Repository | 收口 `transaction_order` 分表访问 | 新增 Java |
| Phase 1 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/repository/TransactionOperationRepository.java` | 动作单 Repository | 收口 `transaction_operation` 分表访问 | 新增 Java |
| Phase 1 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/repository/TransactionFlowEventRepository.java` | 流程事件 Repository | 收口 `transaction_flow_event` 分表访问 | 新增 Java |
| Phase 1 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/mapper/TransactionOrderMapper.java` | 物理表 Mapper | 限制默认 `BaseMapper` 暴露，保留受控物理 SQL | 修改 Java |
| Phase 1 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/mapper/TransactionOperationMapper.java` | 物理表 Mapper | 同上 | 修改 Java |
| Phase 1 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/mapper/TransactionFlowEventMapper.java` | 物理表 Mapper | 同上 | 修改 Java |
| Phase 1 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/service/impl/DefaultTransactionRecordService.java` | 交易事实记录 | 改为调用 Repository，不直接传物理表 | 修改 Java |
| Phase 1 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/service/impl/DefaultTransactionQueryService.java` | 支付后台查询 | 统一跨表查询和裁剪时间 | 修改 Java |
| Phase 1 | `service-admin` | `service-admin/src/main/java/com/scott/payment/admin/service/impl/JdbcAdminTransactionQueryService.java` | 管理端交易查询 | 移除散落 `.formatted(table)` | 修改 Java |
| Phase 1 | `service-merchant` | `service-merchant/src/main/java/com/scott/payment/merchant/service/impl/JdbcMerchantTransactionQueryService.java` | 商户端交易查询 | 统一跨表分页逻辑 | 修改 Java |

## 2. 永久分片定位

| 阶段 | 模块 | 文件路径 | 类/表/配置 | 修改原因 | 修改类型 |
|---|---|---|---|---|---|
| Phase 2 | `docs/sql` | `docs/sql/transaction-shard-locator-schema.sql` | `transaction_shard_locator` | 新增永久分片定位表 DDL 草案 | 新增 SQL |
| Phase 2 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/entity/TransactionShardLocatorDO.java` | Locator 实体 | 保存交易到季度表定位 | 新增 Java |
| Phase 2 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/mapper/TransactionShardLocatorMapper.java` | Locator Mapper | 精确查询和双写 | 新增 Java |
| Phase 2 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/repository/TransactionShardLocatorRepository.java` | Locator Repository | 统一 locator 读写 | 新增 Java |
| Phase 2 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/service/impl/DefaultTransactionRecordService.java` | 记录交易事实 | 首次/后续/回调写 locator 和读取 locator | 修改 Java |
| Phase 2 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/service/impl/DefaultCaptureTransactionPreparationService.java` | 后续交易准备 | 使用 locator 定位原交易 | 修改 Java |
| Phase 2 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/service/impl/DefaultRefundTransactionPreparationService.java` | 后续交易准备 | 使用 locator 定位原交易 | 修改 Java |
| Phase 2 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/service/impl/DefaultVoidTransactionPreparationService.java` | 后续交易准备 | 使用 locator 定位原交易 | 修改 Java |
| Phase 2 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/service/impl/DefaultIncrementalAuthorizationTransactionPreparationService.java` | 后续交易准备 | 使用 locator 定位原交易 | 修改 Java |

## 3. 风控运行时接入

| 阶段 | 模块 | 文件路径 | 类/表/配置 | 修改原因 | 修改类型 |
|---|---|---|---|---|---|
| Phase 3 | `component-library/component-common` | `component-library/component-common/src/main/java/com/scott/payment/component/common/security/HmacValueService.java` | HMAC 标准化服务 | 替代普通 SHA-256 作为匹配摘要 | 新增 Java |
| Phase 3 | `service-openapi` | `service-openapi/src/main/java/com/scott/payment/openapi/service/impl/PaymentServiceImpl.java` | 请求来源采集 | 透传 Origin、Referer、payerIp、设备等字段 | 修改 Java |
| Phase 3 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/api/internal/dto/PaymentCreateCommandDTO.java` | 交易命令 DTO | 补齐风控字段承载 | 修改 Java |
| Phase 3 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/client/risk/dto/RiskPaymentEvaluateClientRequestDTO.java` | 风控请求 DTO | 补齐 HMAC、地址、手机号、设备、发卡国等字段 | 修改 Java |
| Phase 3 | `service-risk` | `service-risk/src/main/java/com/scott/payment/risk/api/internal/dto/RiskPaymentEvaluateRequestDTO.java` | 风控内部 DTO | 与 payment 请求字段对齐 | 修改 Java |
| Phase 4 | `service-risk` | `service-risk/src/main/java/com/scott/payment/risk/service/impl/DefaultRiskEvaluationService.java` | 风控引擎 | 从硬编码规则改为读取配置和写记录 | 修改 Java |
| Phase 4 | `service-risk` | `service-risk/src/main/java/com/scott/payment/risk/repository/RiskRuntimeConfigRepository.java` | 风控运行时配置 | 查询 AML/黑白名单/规则 | 新增 Java |
| Phase 4 | `service-risk` | `service-risk/src/main/java/com/scott/payment/risk/repository/RiskEvaluationRecordRepository.java` | 风控记录 | 写评估记录和命中明细 | 新增 Java |
| Phase 4 | `service-risk` | `service-risk/src/main/java/com/scott/payment/risk/mapper/RiskRuntimeMapper.java` | 风控 Mapper | 运行时受控查询管理端表 | 新增 Java |
| Phase 4 | `service-admin` | `service-admin/src/main/java/com/scott/payment/admin/support/risk/RiskListValueNormalizer.java` | 名单标准化 | 双写 HMAC/keyVersion | 修改 Java |
| Phase 5 | `service-risk` | `service-risk/src/main/java/com/scott/payment/risk/service/RiskFrequencyCounterService.java` | Redis 频率 | 交易频率规则 | 新增 Java |
| Phase 5 | `component-library/component-redis` | `component-library/component-redis/src/main/java/com/scott/payment/component/redis/*` | Redis 原子计数 | 频率窗口和限流计数 | 可能修改 Java |

## 4. 流程事件体系

| 阶段 | 模块 | 文件路径 | 类/表/配置 | 修改原因 | 修改类型 |
|---|---|---|---|---|---|
| Phase 6 | `docs/sql` | `docs/sql/transaction-flow-event-upgrade.sql` | `transaction_flow_event` | 新增事件字段、索引、唯一键 DDL 草案 | 新增 SQL |
| Phase 6 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/domain/event/TransactionFlowEventTypeEnum.java` | 事件类型枚举 | 替代散落字符串 | 新增 Java |
| Phase 6 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/domain/event/TransactionFlowEventStageEnum.java` | 事件阶段枚举 | 替代散落字符串 | 新增 Java |
| Phase 6 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/service/TransactionFlowEventRecorder.java` | 事件记录器 | 真实节点写事件 | 新增 Java |
| Phase 6 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/service/impl/DefaultTransactionFlowEventRecorder.java` | 事件记录器实现 | 统一 event key、trace、request、duration | 新增 Java |
| Phase 6 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/entity/TransactionFlowEventDO.java` | 事件实体 | 补字段 | 修改 Java |
| Phase 6 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/mapper/TransactionFlowEventMapper.java` | 事件 Mapper | 支持新字段和 event key 去重 | 修改 Java |
| Phase 6 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/service/impl/DefaultTransactionRecordService.java` | 交易事实记录 | 去除批量伪事件，改为真实节点调用 | 修改 Java |
| Phase 6 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/service/impl/DefaultPaymentTransactionPreparationService.java` | 首次交易准备 | 风控/路由/渠道节点事件 | 修改 Java |
| Phase 6 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/service/impl/Default*TransactionPreparationService.java` | 后续交易准备 | 后续交易风险/路由/渠道节点事件 | 修改 Java |
| Phase 7 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/service/impl/DefaultTransactionCallbackService.java` | 回调处理 | 回调收到、验签、解析、应用事件 | 修改 Java |
| Phase 7 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/service/impl/DefaultTransactionEventOutboxService.java` | Outbox | MQ 创建事件 | 修改 Java |
| Phase 7 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/service/impl/DefaultTransactionEventOutboxRelayService.java` | Outbox relay | MQ 发送/失败事件 | 修改 Java |
| Phase 7 | `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/service/impl/DefaultTransactionMerchantNotificationService.java` | 商户通知 | 通知创建、发送、失败事件 | 修改 Java |

## 5. 测试与验收

| 阶段 | 模块 | 文件路径 | 类/表/配置 | 修改原因 | 修改类型 |
|---|---|---|---|---|---|
| Phase 0 | `service-payment` | `service-payment/src/test/java/com/scott/payment/payment/architecture/ShardedMapperArchitectureTests.java` | 架构测试 | 禁止核心分表 Mapper 默认访问 | 新增测试 |
| Phase 0 | `service-payment` | `service-payment/src/test/java/com/scott/payment/payment/architecture/RiskClientConfigurationTests.java` | 配置测试 | 非 local 禁止 Noop 风控 | 新增测试 |
| Phase 1 | `service-payment` | `service-payment/src/test/java/com/scott/payment/payment/repository/*RepositoryTests.java` | Repository 测试 | 分表路由和裁剪验证 | 新增测试 |
| Phase 2 | `service-payment` | `service-payment/src/test/java/com/scott/payment/payment/repository/TransactionShardLocatorRepositoryTests.java` | Locator 测试 | 唯一约束、查询优先级、回退策略 | 新增测试 |
| Phase 4 | `service-risk` | `service-risk/src/test/java/com/scott/payment/risk/service/RiskRuntimeEvaluationServiceTests.java` | 风控运行时测试 | AML/黑白名单/规则命中 | 新增测试 |
| Phase 5 | `service-risk` | `service-risk/src/test/java/com/scott/payment/risk/service/RiskFrequencyCounterServiceTests.java` | Redis/频率测试 | 频率窗口和并发 | 新增测试 |
| Phase 6 | `service-payment` | `service-payment/src/test/java/com/scott/payment/payment/service/impl/TransactionFlowEventRecorderTests.java` | 事件测试 | 真实时间、event key、trace/request | 新增测试 |
| Phase 7 | `service-payment` | `service-payment/src/test/java/com/scott/payment/payment/service/impl/TransactionCallbackFlowEventTests.java` | 回调事件测试 | 重复回调和终态保护 | 新增测试 |

## 6. 配置清单

| 阶段 | 模块 | 文件路径 | 类/表/配置 | 修改原因 | 修改类型 |
|---|---|---|---|---|---|
| Phase 0 | Nacos | `service-payment-{env}.yaml` | `payment.risk-client.remote-enabled` | 确认生产/UAT 是否开启远程风控 | 配置审计 |
| Phase 1 | Nacos | `service-payment-{env}.yaml` | `payment.transaction.query.max-range-days` | 控制普通跨分表查询跨度 | 新增配置 |
| Phase 3 | Nacos/Secret | `risk-secret-{env}` | HMAC key/keyVersion | 敏感字段匹配 | 新增配置/密钥 |
| Phase 4 | Nacos | `service-risk-{env}.yaml` | 风控规则缓存 TTL、灰度 enforce 开关 | 管理端配置运行时生效 | 新增配置 |
| Phase 6 | Nacos | `service-payment-{env}.yaml` | flow event 新旧双写/开关 | 事件灰度和回滚 | 新增配置 |
