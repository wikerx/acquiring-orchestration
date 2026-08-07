# Redis 测试报告

## 1. 测试环境

| 项目 | 结果 |
| --- | --- |
| 执行时间 | 2026-07-30 |
| 操作系统 | macOS 26.4.1 aarch64 |
| Java | Eclipse Adoptium 17.0.19 |
| Maven | 3.9.16 |
| 项目基线 | Java 17 |

## 2. `RG-P0-01` 定向测试

| 测试范围 | 用例数 | 失败 | 错误 | 跳过 | 主要覆盖 |
| --- | ---: | ---: | ---: | ---: | --- |
| `component-redis` 安全缓存基础设施 | 20 | 0 | 0 | 0 | 精简 Key、门禁 token、立即精确删除、Cache 失败策略和 TTL 门禁 |
| Admin 商户安全缓存失效 | 14 | 0 | 0 | 0 | 提交/回滚、Outbox、入口覆盖、删除-释放-标记顺序、失败重试和调度 |
| 商户运行资料安全读取 | 6 | 0 | 0 | 0 | 正常命中、pending、门禁异常、命中竞态、主动失效和 MASTER 路由 |
| OpenAPI 访问策略安全读取 | 7 | 0 | 0 | 0 | 正常命中、pending、门禁异常、DB 异常拒绝、命中竞态和 MASTER 路由 |
| 合计 | 47 | 0 | 0 | 0 | `RG-P0-01` 代码级定向验证 |

定向测试明确验证 relay 的顺序为“精确删除 -> token 门禁释放 -> Outbox 标记 `SENT`”。

## 3. `RG-P0-01` 模块级回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
PATH=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin \
mvn -Pdev -pl component-library/component-db,component-library/component-redis,service-admin,service-openapi -am test
```

Reactor 结果：13 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 28.983 秒。

| 模块 | 用例数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| `component-core` | 33 | 0 | 0 | 0 |
| `component-excel` | 2 | 0 | 0 | 0 |
| `component-web` | 11 | 0 | 0 | 0 |
| `component-db` | 39 | 0 | 0 | 0 |
| `component-redis` | 52 | 0 | 0 | 5 |
| `component-security` | 5 | 0 | 0 | 0 |
| `component-http` | 2 | 0 | 0 | 0 |
| `component-mq` | 2 | 0 | 0 | 0 |
| `component-job` | 0 | 0 | 0 | 0 |
| `service-admin` | 84 | 0 | 0 | 0 |
| `service-openapi` | 72 | 0 | 0 | 5 |
| 合计 | 302 | 0 | 0 | 10 |

聚合模块不产生独立测试用例。

## 4. `RG-P0-02` 定向测试

| 测试范围 | 用例数 | 失败 | 错误 | 跳过 | 主要覆盖 |
| --- | ---: | ---: | ---: | ---: | --- |
| `RedisCacheGenerationStoreTests` | 5 | 0 | 0 | 0 | 当前代际、pending、单发布者、提交幂等、持有者回滚释放 |
| Admin 风控失效测试 | 12 | 0 | 0 | 0 | 提交/回滚、事务内去重、Outbox DDL、入口覆盖、CAS 恢复、失败重试、调度批量边界 |
| `DefaultRiskListRuntimeRepositoryTests` | 16 | 0 | 0 | 0 | generation 隔离、pending/异常直读主库、Boolean/List/Optional 缓存和状态 Key 边界 |
| 合计 | 33 | 0 | 0 | 0 | `RG-P0-02` 代码级定向验证 |

Admin 的 12 个用例包含 11 个 `application.risk.cache` 用例和 1 个
`AdminRiskManagementApplicationServiceTests` 用例。

## 5. `RG-P0-02` 模块级回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
PATH=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin \
mvn -pl service-admin,service-risk -am test
```

Reactor 结果：12 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 23.270 秒。

| 模块 | 用例数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| `component-core` | 33 | 0 | 0 | 0 |
| `component-excel` | 2 | 0 | 0 | 0 |
| `component-web` | 11 | 0 | 0 | 0 |
| `component-db` | 35 | 0 | 0 | 0 |
| `component-redis` | 45 | 0 | 0 | 5 |
| `component-security` | 5 | 0 | 0 | 0 |
| `component-mq` | 2 | 0 | 0 | 0 |
| `component-job` | 0 | 0 | 0 | 0 |
| `service-admin` | 73 | 0 | 0 | 0 |
| `service-risk` | 61 | 0 | 0 | 1 |
| 合计 | 267 | 0 | 0 | 6 |

`acquiring-orchestration` 和 `component-library` 是聚合模块，不产生独立测试用例。

## 6. `RG-P0-01` 全项目回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
PATH=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin \
mvn -Pdev clean test
```

Reactor 结果：23 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 56.179 秒。

| 模块 | 用例数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| `payment-channel-library` | 63 | 0 | 0 | 2 |
| `component-core` | 33 | 0 | 0 | 0 |
| `component-db` | 39 | 0 | 0 | 0 |
| `component-excel` | 2 | 0 | 0 | 0 |
| `component-http` | 2 | 0 | 0 | 0 |
| `component-mq` | 2 | 0 | 0 | 0 |
| `component-redis` | 52 | 0 | 0 | 5 |
| `component-security` | 5 | 0 | 0 | 0 |
| `component-web` | 11 | 0 | 0 | 0 |
| `service-admin` | 84 | 0 | 0 | 0 |
| `service-checkout` | 2 | 0 | 0 | 0 |
| `service-job` | 15 | 0 | 0 | 0 |
| `service-merchant` | 13 | 0 | 0 | 0 |
| `service-openapi` | 72 | 0 | 0 | 5 |
| `service-payment` | 160 | 0 | 0 | 0 |
| `service-risk` | 61 | 0 | 0 | 1 |
| 合计 | 616 | 0 | 0 | 13 |

其余聚合模块或骨架模块没有独立测试用例。

## 7. 跳过项

| 测试 | 跳过数量 | 原因 |
| --- | ---: | --- |
| `MpgsApiClientLiveFlowTests` | 2 | 未设置 `MPGS_LIVE_TEST_ENABLED`，未连接 MPGS Sandbox |
| `RedisGlobalIdIntegrationTests` | 5 | 未设置 `global-id.redis.integration.enabled`，未连接真实 Redis |
| `MerchantOpenApiMpgsLiveFlowTests` | 5 | 未设置 OpenAPI live 测试开关，未连接 MPGS Sandbox |
| `RiskRuntimeMapperMySqlLiveTests` | 1 | 未设置 `risk.mysql.live.enabled`，未连接真实 MySQL |

这些跳过项不影响单元测试结论，但意味着本轮没有获得真实 Redis Lua、真实 MySQL Outbox、
网络超时和服务进程退出后的集成证据。

## 8. 静态检查

| 检查 | 结果 |
| --- | --- |
| `git diff --check` | 通过，无空白错误 |
| 安全缓存读取 MASTER 路由契约 | 通过 |
| pending、门禁异常和命中竞态绕过旧缓存 | 通过 |
| relay 删除、释放、标记顺序 | 通过 |
| 门禁 Key 精简命名与 TTL 配置 | 通过 |
| Outbox 主 schema 与独立迁移契约 | 通过 |
| 13 个风控写入口和 5 个商户 IP 白名单写入口契约 | 通过 |
| JDK 基线 | 通过，Maven 实际使用 Java 17.0.19 |

## 9. 未覆盖的集成验证

1. 真实 Redis 单机 Lua 集成：需要可控 Redis 环境和显式测试开关。
2. 真实 Redis Cluster、CROSSSLOT、failover：列入阶段 4。
3. 真实 MySQL 事务、Outbox 锁竞争和多实例调度并发。
4. 进程在事务提交前后退出、Redis 重启/超时、Outbox 积压与恢复演练。
5. 性能、容量和故障演练：后续分别记录到 `11`、`12`。

当前证据支持 `RG-P0-01` 的代码级、相关模块和全项目自动化回归，可以关闭阶段 3；真实
Redis、真实 MySQL、集群故障和性能证据仍需在后续阶段补齐，因此本报告不构成生产环境
验收。

## 10. 阶段 4-A 定向验证

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -Pdev -pl component-library/component-redis,service-admin,service-payment -am \
  -Dtest=PaymentRedisEnvironmentGuardAutoConfigurationTests,PaymentRedisCacheAutoConfigurationTests,PaymentRedisSerializerFactoryTests,AdminMonitorCacheApplicationServiceTests,DefaultTransactionRecordServiceTests,TransactionDetailCacheRemovalContractTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Reactor 结果：15 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 17.099 秒。

| 模块 / 测试 | 用例数 | 失败 | 错误 | 跳过 | 主要覆盖 |
| --- | ---: | ---: | ---: | ---: | --- |
| `component-redis` | 20 | 0 | 0 | 0 | 环境前缀门禁、Cache Registry、TTL、受控 Serializer |
| `service-admin` | 5 | 0 | 0 | 0 | SCAN、无 KEYS、无 Value 读取、命名空间保护、删除和 pageSize 上限 |
| `service-payment` | 15 | 0 | 0 | 0 | 交易事实写入原行为、详情无 `@Cacheable`、Registry 无 `transaction:detail` |
| 合计 | 40 | 0 | 0 | 0 | 阶段 4-A 代码级定向验证 |

静态检查确认生产源码和部署配置中已无 `TRANSACTION_DETAIL`、
`TransactionDetailCacheService`、`transactionDetailCacheService` 或交易详情 evict；
`transaction:detail` 只保留在契约测试和治理文档中。Admin 生产代码已无
`StringRedisTemplate.keys(...)` 和任何 Value `GET/range/members/entries` 读取。

阶段 3 的 616 用例全项目结果是修改前历史证据，不能替代阶段 4-A 修改后的完整回归。
阶段 4-A 修改后的完整回归结果见第 12 节。

## 11. 阶段 4-A 模块级回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -Pdev -pl component-library/component-redis,service-admin,service-payment -am test
```

Reactor 结果：15 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 19.461 秒。

| 模块 | 用例数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| `component-core` | 33 | 0 | 0 | 0 |
| `component-excel` | 2 | 0 | 0 | 0 |
| `component-web` | 11 | 0 | 0 | 0 |
| `component-db` | 39 | 0 | 0 | 0 |
| `component-redis` | 57 | 0 | 0 | 5 |
| `component-security` | 5 | 0 | 0 | 0 |
| `component-http` | 2 | 0 | 0 | 0 |
| `component-mq` | 2 | 0 | 0 | 0 |
| `payment-channel-library` | 63 | 0 | 0 | 2 |
| `service-admin` | 89 | 0 | 0 | 0 |
| `service-payment` | 161 | 0 | 0 | 0 |
| 合计 | 464 | 0 | 0 | 7 |

`component-job` 和 4 个聚合模块没有独立测试用例。7 个跳过项均为需要外部 Redis 或
MPGS Sandbox 的既有 live/integration 测试，不影响本批单元与模块级结论。

## 12. 阶段 4-A 完整全项目回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -Pdev clean test
```

Reactor 结果：23 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 54.883 秒。

| 模块 | 用例数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| `payment-channel-library` | 63 | 0 | 0 | 2 |
| `component-core` | 33 | 0 | 0 | 0 |
| `component-db` | 39 | 0 | 0 | 0 |
| `component-excel` | 2 | 0 | 0 | 0 |
| `component-http` | 2 | 0 | 0 | 0 |
| `component-mq` | 2 | 0 | 0 | 0 |
| `component-redis` | 57 | 0 | 0 | 5 |
| `component-security` | 5 | 0 | 0 | 0 |
| `component-web` | 11 | 0 | 0 | 0 |
| `service-admin` | 89 | 0 | 0 | 0 |
| `service-checkout` | 2 | 0 | 0 | 0 |
| `service-job` | 15 | 0 | 0 | 0 |
| `service-merchant` | 13 | 0 | 0 | 0 |
| `service-openapi` | 72 | 0 | 0 | 5 |
| `service-payment` | 161 | 0 | 0 | 0 |
| `service-risk` | 61 | 0 | 0 | 1 |
| 合计 | 627 | 0 | 0 | 13 |

其余 7 个聚合或骨架模块没有独立测试用例。13 个跳过项与第 7 节一致，分别依赖真实
Redis、MySQL 或 MPGS Sandbox；本次没有启用对应 live/integration 开关。

阶段 4-A 的代码级定向测试、相关模块回归和完整全项目自动化回归均无失败或错误。该结论
不覆盖真实 Redis Cluster failover、大 Keyspace SCAN、真实 MySQL 并发、MPGS Sandbox
或交易详情移除缓存后的容量压测。

## 13. 阶段 4-B 定向与真实 Cluster 测试

定向命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -Pdev -pl component-library/component-redis,service-risk -am \
  -Dtest=PaymentRedisPropertiesTests,RiskEvaluationConfigTests,DefaultRiskListRuntimeRepositoryTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

| 测试 | 用例数 | 失败 | 错误 | 跳过 | 主要覆盖 |
| --- | ---: | ---: | ---: | ---: | --- |
| `PaymentRedisPropertiesTests` | 5 | 0 | 0 | 0 | 精简 Key、同槽摘要 Hash Tag、非法片段和长度约束 |
| `DefaultRiskListRuntimeRepositoryTests` | 22 | 0 | 0 | 0 | 三种模式、累计与频率同槽 Key、SHADOW 决策和故障降级 |
| `RiskEvaluationConfigTests` | 4 | 0 | 0 | 0 | 默认 `LEGACY`、SHADOW、未确认切换阻断和确认后放行 |
| 合计 | 31 | 0 | 0 | 0 | 阶段 4-B 代码级验证 |

真实 Cluster 命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
scripts/run-risk-redis-cluster-tests.sh
```

脚本使用 `redis:6` 启动 6 节点 Cluster，默认映射 `18000-18005`，测试结束后自动清理。
`DefaultRiskListRuntimeRepositoryClusterIntegrationTests` 共 2 个用例，失败 0、错误 0、
跳过 0；累计预留重复交易只计一次、回滚后可重新预留，频率相同交易只计一次且不同交易
递增。两个 Lua Key 使用相同 Hash Tag，真实执行未出现 `CROSSSLOT`。

## 14. 阶段 4-B 模块级回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -Pdev -pl component-library/component-redis,service-risk -am test
```

Reactor 结果：8 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 11.185 秒。

| 模块 | 用例数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| `component-core` | 33 | 0 | 0 | 0 |
| `component-web` | 11 | 0 | 0 | 0 |
| `component-db` | 39 | 0 | 0 | 0 |
| `component-redis` | 58 | 0 | 0 | 5 |
| `component-mq` | 2 | 0 | 0 | 0 |
| `service-risk` | 73 | 0 | 0 | 3 |
| 合计 | 216 | 0 | 0 | 8 |

8 个跳过项需要外部 Redis 或 MySQL 开关，其中阶段 4-B 的真实 Cluster 用例已通过独立脚本
显式启用并验证。当前结果不覆盖 Cluster failover、生产拓扑、完整周期 SHADOW 观察或
阶段 4-B 修改后的全项目回归；全项目回归结果见第 16 节。

## 15. 阶段 4-B 静态检查

| 检查 | 结果 |
| --- | --- |
| `git diff --check` | 通过，无已跟踪文件空白错误 |
| 阶段 4-B 相关文件尾随空白扫描 | 通过，包含未跟踪的新文件 |
| `bash -n scripts/run-risk-redis-cluster-tests.sh` | 通过 |
| `service-risk-dev.yaml` YAML 解析 | 通过 |
| Lua 资源加载扫描 | 通过，仓储通过 `ClassPathResource` 加载三段脚本，无内联脚本文本 |
| 临时 Cluster 容器清理 | 通过，无 `acquiring-risk-redis-cluster-*` 容器残留 |
| Key 与日志敏感边界 | 通过，Hash Tag 和 transaction 使用 SHA-256；回滚异常只记录 Key 摘要 |

## 16. 阶段 4-B 完整全项目回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -Pdev clean test
```

Reactor 结果：23 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 52.499 秒。本次
`clean` 后生成 133 份 Surefire XML。

| 模块 | 用例数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| `payment-channel-library` | 63 | 0 | 0 | 2 |
| `component-core` | 33 | 0 | 0 | 0 |
| `component-db` | 39 | 0 | 0 | 0 |
| `component-excel` | 2 | 0 | 0 | 0 |
| `component-http` | 2 | 0 | 0 | 0 |
| `component-mq` | 2 | 0 | 0 | 0 |
| `component-redis` | 58 | 0 | 0 | 5 |
| `component-security` | 5 | 0 | 0 | 0 |
| `component-web` | 11 | 0 | 0 | 0 |
| `service-admin` | 89 | 0 | 0 | 0 |
| `service-checkout` | 2 | 0 | 0 | 0 |
| `service-job` | 15 | 0 | 0 | 0 |
| `service-merchant` | 13 | 0 | 0 | 0 |
| `service-openapi` | 72 | 0 | 0 | 5 |
| `service-payment` | 161 | 0 | 0 | 0 |
| `service-risk` | 73 | 0 | 0 | 3 |
| 合计 | 640 | 0 | 0 | 15 |

15 个跳过项：

| 测试 | 跳过数量 | 原因 |
| --- | ---: | --- |
| `MpgsApiClientLiveFlowTests` | 2 | 未连接 MPGS Sandbox |
| `RedisGlobalIdIntegrationTests` | 5 | 未启用真实 Redis 集成开关 |
| `MerchantOpenApiMpgsLiveFlowTests` | 5 | 未启用 OpenAPI live 测试开关 |
| `RiskRuntimeMapperMySqlLiveTests` | 1 | 未启用真实 MySQL 测试开关 |
| `DefaultRiskListRuntimeRepositoryClusterIntegrationTests` | 2 | 全项目命令未启用 Cluster 开关；已由阶段 4-B 独立脚本验证通过 |

阶段 4-B 的定向测试、真实 Cluster 测试、相关模块回归和全项目自动化回归均无失败或错误。
该结论仍不覆盖 Redis Cluster failover、完整最大周期 SHADOW 观察、生产拓扑与 ACL/TLS、
真实 MySQL 并发或 MPGS Sandbox。

## 17. 阶段 4-C 定向验证

风险侧扩大定向命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -pl service-risk -am \
  -Dtest=RiskEvaluationApplicationServiceTests,RiskEvaluationConfigTests,RiskListFunctionCoverageTests,MerchantLimitReservationPaymentEventConsumerTests,RiskEvaluationAuditConsumerTests,DefaultRiskAuditRecordWriterTests,DefaultRiskListRuntimeRepositoryTests,RiskRuntimeValueNormalizerTests,DefaultMerchantLimitReservationCounterServiceTests,DefaultMerchantLimitReservationLifecycleCoordinatorTests,DefaultMerchantLimitReservationReconciliationServiceTests,DefaultMerchantLimitReservationStateServiceTests,DefaultRiskEvaluationServiceTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

支付侧扩大定向命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -pl service-payment -am \
  -Dtest=TransactionEventOutboxRelaySchedulerTests,DefaultPaymentChannelResultTransactionServiceTests,DefaultTransactionChannelMatchServiceTests,DefaultTransactionEventOutboxRelayServiceTests,DefaultTransactionRecordServiceTests,PaymentRiskReservationCompensationTests,RiskPaymentRiskInvokeServiceTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

| 范围 | 用例数 | 失败 | 错误 | 跳过 | 主要覆盖 |
| --- | ---: | ---: | ---: | ---: | --- |
| `service-risk` 扩大定向 | 84 | 0 | 0 | 0 | 预占、状态 CAS、事务门禁、三模式投影、支付事件、基线门禁、超时对账和评估回滚 |
| `service-payment` 扩大定向 | 33 | 0 | 0 | 0 | 风控补偿、终态 CAS、单次 Outbox、主动查询、Outbox 中继与调度 |

## 18. 阶段 4-C 静态检查

| 检查 | 结果 |
| --- | --- |
| `git diff --check` | 通过，无已跟踪文件空白错误 |
| 阶段 4-C 源码、测试、SQL 和 Nacos 尾随空白扫描 | 通过 |
| `service-risk-dev.yaml`、`service-payment-dev.yaml`、`rocketmq-dev.yaml` 解析 | 通过 |
| 风险服务 dev/test/uat/prod/sample YAML 解析 | 通过 |
| `service-payment-dev.yaml` 节点检查 | 通过，Outbox 与 merchant notification 共用唯一 `payment.transaction` 节点 |
| SQL 草案边界 | 通过，仅创建生命周期表；未执行 SQL；时间点字段均为 `DATETIME(3)` |
| Key 命名边界 | 通过，新增风险 Key 不含服务段或默认版本段，交易号使用 SHA-256 摘要 |
| 服务 DTO 边界 | 通过，支付与风险接口只传业务标识和状态，不传 Redis 物理 Key |

`service-payment/src/main/resources/application.yml` 包含 Maven 资源占位符
`@spring.profiles.active@`，通用 Ruby YAML 解析器不能直接解析；该文件由 Maven 过滤后使用，
不属于本次 Nacos 节点合并验证范围。

## 19. 阶段 4-C 未覆盖项

1. `RiskRuntimeMapperMySqlLiveTests` 未启用，迁移脚本也未连接或执行到真实 MySQL。
2. 本轮未启动真实 Redis Cluster，阶段 4-B 的同槽证据不能替代本生命周期的故障演练。
3. 未覆盖进程在 Redis 回滚和数据库 CAS 之间退出、Redis 超时、RocketMQ 积压/重复、
   多实例对账竞争和 Cluster failover。
4. 未执行完整最大周期 SHADOW 差异观察、容量压测或生产配置验收。

## 20. 阶段 4-C 完整全项目回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -Pdev clean test
```

Reactor 结果：23 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 54.557 秒。本次
`clean` 后生成 141 份 Surefire XML。

| 模块 | 用例数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| `payment-channel-library` | 63 | 0 | 0 | 2 |
| `component-core` | 33 | 0 | 0 | 0 |
| `component-db` | 39 | 0 | 0 | 0 |
| `component-excel` | 2 | 0 | 0 | 0 |
| `component-http` | 2 | 0 | 0 | 0 |
| `component-mq` | 2 | 0 | 0 | 0 |
| `component-redis` | 58 | 0 | 0 | 5 |
| `component-security` | 5 | 0 | 0 | 0 |
| `component-web` | 11 | 0 | 0 | 0 |
| `service-admin` | 89 | 0 | 0 | 0 |
| `service-checkout` | 2 | 0 | 0 | 0 |
| `service-job` | 15 | 0 | 0 | 0 |
| `service-merchant` | 13 | 0 | 0 | 0 |
| `service-openapi` | 72 | 0 | 0 | 5 |
| `service-payment` | 164 | 0 | 0 | 0 |
| `service-risk` | 87 | 0 | 0 | 3 |
| 合计 | 657 | 0 | 0 | 15 |

其余 7 个聚合或骨架模块没有独立测试用例。15 个跳过项：

| 测试 | 跳过数量 | 原因 |
| --- | ---: | --- |
| `MpgsApiClientLiveFlowTests` | 2 | 未连接 MPGS Sandbox |
| `RedisGlobalIdIntegrationTests` | 5 | 未启用真实 Redis 集成开关 |
| `MerchantOpenApiMpgsLiveFlowTests` | 5 | 未启用 OpenAPI live 测试开关 |
| `RiskRuntimeMapperMySqlLiveTests` | 1 | 未启用真实 MySQL 测试开关 |
| `DefaultRiskListRuntimeRepositoryClusterIntegrationTests` | 2 | 未启用本轮 Cluster 集成开关；阶段 4-B 已独立验证同槽路径 |

阶段 4-C 的定向测试和全项目自动化回归均无失败或错误。该结论不构成生产切换批准，
仍需完成第 19 节列出的真实环境、故障和容量验收。

## 21. 阶段 4-D TDD 验证

先新增以下行为断言，再修改生产代码：

1. 首次交易必须生成 operation 与 merchant-order-flow 两类环境隔离 Key。
2. Capture 必须生成同一规范的 operation Key。
3. 所有动态身份必须为 64 位小写 SHA-256，不包含原始商户号、订单号或幂等键。
4. Key 不包含 `service-payment` 或默认 `v1` 段，TTL 保持 30 秒。
5. 首次交易和 Capture 进入渠道调用时，Redis 准备锁必须已经释放。

第一轮 RED 执行 `PaymentTransactionServiceImplTests` 共 38 个测试，新增 2 个测试按预期
失败：捕获到旧 `transaction:operation:*`、`transaction:merchant-order-flow:*` 裸 Key。
第一轮实现后 38 个全部通过。差异复核再增加解锁异常测试，RED 阶段按预期出现 1 个错误：
Redis compare-delete 异常阻断了渠道调用；加入告警降级后 39 个测试全部通过。

## 22. 阶段 4-D 定向回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -pl service-payment -am \
  -Dtest=PaymentTransactionServiceImplTests,PaymentTransactionConsistencyBaselineTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

| 范围 | 用例数 | 失败 | 错误 | 跳过 | 主要覆盖 |
| --- | ---: | ---: | ---: | ---: | --- |
| `PaymentTransactionServiceImplTests` | 39 | 0 | 0 | 0 | Key 结构、SHA-256 边界、30 秒 TTL、准备后解锁、解锁异常降级、锁忙、五类交易基本行为 |
| `PaymentTransactionConsistencyBaselineTests` | 52 | 0 | 0 | 0 | DB 幂等、唯一约束语义、并发重复订单单次渠道调用、状态机和准备/结果事务边界 |
| 合计 | 91 | 0 | 0 | 0 | `RG-P1-07` 代码级定向验证 |

Reactor 结果：12 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 1.949 秒。

## 23. 阶段 4-D 静态检查与未覆盖项

| 检查 | 结果 |
| --- | --- |
| `git diff --check` | 通过 |
| 阶段 4-D 源码、测试和 Redis 文档尾随空白扫描 | 通过 |
| 阶段 4-D Java 注释治理 | 本阶段 3 个 Java 文件无命中；全仓脚本仍有 71 个文件、468 个既有存量命中 |
| 全仓日志安全规则 | 通过，0 敏感日志命中、0 缺失必需事件、0 缺失 trace 规则 |
| 旧裸支付锁 Key 扫描 | 生产源码中无 `transaction:operation:` 或 `transaction:merchant-order-flow:` |
| Key 命名边界 | 使用 `acquiring:{environment}:payment:lock:{purpose}:{digest}`，无 service/default version 段 |
| 敏感值边界 | operation 与 merchant-order-flow 动态身份均使用 SHA-256 摘要 |
| 锁作用域 | 五条活跃交易路径均在准备服务返回后解锁，再调用渠道 |
| 解锁故障策略 | compare-delete 异常记录告警并继续渠道调用，遗留锁依赖 30 秒 TTL |
| SQL / 依赖边界 | 未新增或执行 SQL；未新增 Redisson、watchdog 或续期依赖 |

本阶段未覆盖真实 Redis 多实例竞争、准备事务超过 30 秒、进程崩溃、网络分区、
Redis Cluster failover、锁吞吐和容量压测。这些缺口不能由本轮 dev 自动化回归替代。

## 24. 阶段 4-D 完整全项目回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -Pdev clean test
```

Reactor 结果：23 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 54.586 秒。本次
`clean` 后生成 141 份 Surefire XML。

| 模块 | 用例数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| `payment-channel-library` | 63 | 0 | 0 | 2 |
| `component-core` | 33 | 0 | 0 | 0 |
| `component-db` | 39 | 0 | 0 | 0 |
| `component-excel` | 2 | 0 | 0 | 0 |
| `component-http` | 2 | 0 | 0 | 0 |
| `component-mq` | 2 | 0 | 0 | 0 |
| `component-redis` | 58 | 0 | 0 | 5 |
| `component-security` | 5 | 0 | 0 | 0 |
| `component-web` | 11 | 0 | 0 | 0 |
| `service-admin` | 89 | 0 | 0 | 0 |
| `service-checkout` | 2 | 0 | 0 | 0 |
| `service-job` | 15 | 0 | 0 | 0 |
| `service-merchant` | 13 | 0 | 0 | 0 |
| `service-openapi` | 72 | 0 | 0 | 5 |
| `service-payment` | 167 | 0 | 0 | 0 |
| `service-risk` | 87 | 0 | 0 | 3 |
| 合计 | 660 | 0 | 0 | 15 |

其余 7 个聚合或骨架模块没有独立测试用例。15 个跳过项：

| 测试 | 跳过数量 | 原因 |
| --- | ---: | --- |
| `MpgsApiClientLiveFlowTests` | 2 | 未设置 `MPGS_LIVE_TEST_ENABLED`，未连接 MPGS Sandbox |
| `RedisGlobalIdIntegrationTests` | 5 | 未设置 `global-id.redis.integration.enabled`，未启用真实 Redis 集成测试 |
| `MerchantOpenApiMpgsLiveFlowTests` | 5 | 未设置 OpenAPI live/risk-block 开关，未连接 MPGS Sandbox |
| `RiskRuntimeMapperMySqlLiveTests` | 1 | 未设置 `risk.mysql.live.enabled`，未启用真实 MySQL 测试 |
| `DefaultRiskListRuntimeRepositoryClusterIntegrationTests` | 2 | 未设置 `risk.redis.cluster.integration.enabled`；阶段 4-B 已独立验证同槽路径 |

阶段 4-D 的 TDD、定向回归和完整全项目自动化回归均无失败或错误。该结论不覆盖真实
Redis 多实例竞争、准备事务超过 30 秒、持有者进程退出、网络分区、Cluster failover、
锁吞吐和容量压测，也不构成生产发布批准。

## 25. 阶段 4-E TDD 与兼容验证

| 测试点 | 结果 | 说明 |
| --- | --- | --- |
| 历史风控时间轴读取 | 通过 | RED 阶段暴露 `Long` 未登记，增加精确类型后转为 GREEN |
| 未登记项目类型读取 | 通过 | RED 阶段证明项目包前缀过宽，改为两个真实 DTO 精确登记 |
| 未登记 JDK 容器读取 | 通过 | RED 阶段证明 `java.util.*` 过宽，`PriorityQueue` 现被拒绝 |
| 未登记根对象写入 | 通过 | RED 阶段旧实现未抛异常，受控写入门禁加入后转为 GREEN |
| 未登记嵌套对象写入 | 通过 | `LinkedHashMap` 内的 `MonthDay` 被拒绝 |
| 未登记时间/金额类型读取 | 通过 | `MonthDay`、`BigInteger` 均被拒绝 |
| 风控时间轴双向兼容 | 通过 | 历史写/v2读、v2写/历史读均保持内容和类型 |
| 平台配置 String 双向兼容 | 通过 | 根标量无需新 namespace |
| 真实商户 DTO 双向兼容 | 通过 | 两个业务 DTO 均完成两代 Serializer 双向验证 |
| 真实 DTO 嵌套类型门禁 | 通过 | `MerchantOpenApiAccessPolicy` 使用 `TreeSet` 时拒绝写入 |

最新 Serializer 工厂测试为 11 个，真实业务 DTO 兼容测试为 3 个，失败 0、错误 0、
跳过 0。完成前复跑 Serializer、Spring Cache 接线和真实 DTO 三组测试，共 26 个，
失败 0、错误 0、跳过 0，10 个 Reactor 模块全部 `SUCCESS`。

## 26. 阶段 4-E 相关模块回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -pl component-library/component-redis,component-library/component-db,service-admin,service-merchant,service-openapi \
  -am test
```

Reactor 覆盖 14 个模块，全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 27.061 秒。

| 模块 | 用例数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| `component-core` | 33 | 0 | 0 | 0 |
| `component-excel` | 2 | 0 | 0 | 0 |
| `component-web` | 11 | 0 | 0 | 0 |
| `component-db` | 39 | 0 | 0 | 0 |
| `component-redis` | 66 | 0 | 0 | 5 |
| `component-security` | 5 | 0 | 0 | 0 |
| `component-http` | 2 | 0 | 0 | 0 |
| `component-mq` | 2 | 0 | 0 | 0 |
| `component-job` | 0 | 0 | 0 | 0 |
| `service-admin` | 89 | 0 | 0 | 0 |
| `service-merchant` | 13 | 0 | 0 | 0 |
| `service-openapi` | 75 | 0 | 0 | 5 |
| 合计 | 337 | 0 | 0 | 10 |

10 个跳过项为 `RedisGlobalIdIntegrationTests` 5 个和
`MerchantOpenApiMpgsLiveFlowTests` 5 个，均由真实 Redis 或 MPGS Sandbox 条件开关未
启用导致。

## 27. 阶段 4-E 未覆盖项

1. 未连接真实 Redis 读取生产或测试环境的长期存量 Value。
2. 未验证 Redis Cluster failover、网络超时或序列化失败后的业务降级容量。
3. 未新增、删除或批量迁移 Redis Key；现有双向兼容证据支持继续沿用原 namespace。

## 28. 阶段 4-E 静态检查

| 检查 | 结果 |
| --- | --- |
| `git diff --check` | 通过 |
| 本阶段源码、测试和 Redis 文档尾随空白 | 通过 |
| broad 类型白名单扫描 | 通过；生产工厂无项目包或 `java.util/time/math` 包级 allowlist |
| Default Typing 边界 | 新写只使用 `OBJECT_AND_NON_CONCRETE`；`NON_FINAL` 仅存在于历史只读 reader |
| Java 注释治理 | 本阶段新增 Java 文件无命中；全仓仍有 71 个文件、468 个既有存量命中 |
| Key / 配置边界 | 未修改 Key 结构、生产模式、TTL、依赖或数据库脚本 |

## 29. 阶段 4-E 完整全项目回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -Pdev clean test
```

Reactor 结果：23 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 51.339 秒。本次
`clean` 后生成 142 份 Surefire XML。

| 模块 | 用例数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| `payment-channel-library` | 63 | 0 | 0 | 2 |
| `component-core` | 33 | 0 | 0 | 0 |
| `component-db` | 39 | 0 | 0 | 0 |
| `component-excel` | 2 | 0 | 0 | 0 |
| `component-http` | 2 | 0 | 0 | 0 |
| `component-mq` | 2 | 0 | 0 | 0 |
| `component-redis` | 66 | 0 | 0 | 5 |
| `component-security` | 5 | 0 | 0 | 0 |
| `component-web` | 11 | 0 | 0 | 0 |
| `service-admin` | 89 | 0 | 0 | 0 |
| `service-checkout` | 2 | 0 | 0 | 0 |
| `service-job` | 15 | 0 | 0 | 0 |
| `service-merchant` | 13 | 0 | 0 | 0 |
| `service-openapi` | 75 | 0 | 0 | 5 |
| `service-payment` | 167 | 0 | 0 | 0 |
| `service-risk` | 87 | 0 | 0 | 3 |
| 合计 | 671 | 0 | 0 | 15 |

其余 7 个聚合或骨架模块没有独立测试用例。15 个跳过项：

| 测试 | 跳过数量 | 原因 |
| --- | ---: | --- |
| `MpgsApiClientLiveFlowTests` | 2 | 未设置 `MPGS_LIVE_TEST_ENABLED`，未连接 MPGS Sandbox |
| `RedisGlobalIdIntegrationTests` | 5 | 未设置 `global-id.redis.integration.enabled`，未启用真实 Redis 集成测试 |
| `MerchantOpenApiMpgsLiveFlowTests` | 5 | 未设置 OpenAPI live/risk-block 开关，未连接 MPGS Sandbox |
| `RiskRuntimeMapperMySqlLiveTests` | 1 | 未设置 `risk.mysql.live.enabled`，未启用真实 MySQL 测试 |
| `DefaultRiskListRuntimeRepositoryClusterIntegrationTests` | 2 | 未设置 `risk.redis.cluster.integration.enabled`；阶段 4-B 已独立验证同槽路径 |

阶段 4-E 的 TDD、定向回归和完整全项目自动化回归均无失败或错误。该结论不覆盖真实
Redis 长期存量 Value、Cluster failover、网络超时、序列化故障降级、吞吐和容量压测，
也不构成生产发布批准。

## 30. 阶段 5 相关模块回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -pl component-library/component-core,component-library/component-db,component-library/component-redis,service-admin -am test
```

Reactor 覆盖 11 个模块，全部 `SUCCESS`，共执行 266 个测试，失败 0、错误 0、跳过 5，
总耗时 22.485 秒。5 个跳过项均为未启用外部 Redis 集成环境的
`RedisGlobalIdIntegrationTests`，不影响本阶段纯单元和模块契约结论。

## 31. 阶段 5 覆盖范围

| 测试点 | 结果 | 说明 |
| --- | --- | --- |
| 平台配置准入 | 通过 | 只允许四个公开 URL 配置键，未登记及敏感键绕过缓存 |
| TTL 抖动 | 通过 | 0% 和 10% 边界、非法 TTL/比例拒绝、Spring Cache TTL 接线 |
| 商户 miss marker | 通过 | `PRESENT/ABSENT/UNAVAILABLE` 三态，只有主库明确无记录才写 |
| 数据库回源舱壁 | 通过 | 公平许可上限生效，饱和抛出可重试 `F503`，不伪装成不存在 |
| 商户可靠失效 | 通过 | Outbox 删除正缓存时同步删除 miss marker |
| ISO 迁移 | 通过 | 新 Key 优先、历史回退、双写、10% 抖动和两代精确删除 |
| 无收益缓存退役 | 通过 | 风控时间线和未使用风险 Cache Name/TTL 不再存在生产读写 |

## 32. 阶段 5 静态检查与未覆盖项

| 检查 | 结果 |
| --- | --- |
| `git diff --check` | 通过 |
| 日志治理脚本 | 1,353 个 Java 文件，0 个命中 |
| Java 注释治理 | 阶段 5 新增核心类无命中；全仓存量缺口继续在阶段 6～8 随修改治理 |
| Key 格式 | 新增 Key 均为 `acquiring:{environment}:{domain}:{business}[:{businessKey}]` |

未覆盖真实 Redis 拒绝连接、共享环境新旧 Key 迁移比例、真实数据库连接池饱和和生产容量；
这些项目必须在阶段 9 报告中标记为实测或受阻，不得由当前模块测试推断为通过。

## 33. 阶段 6 与阶段 7 代码验证

累计限额生命周期、频率模式门禁和生产 Lua 的自动化覆盖包括：

| 测试组 | 当前用例数 | 结果 | 覆盖重点 |
| --- | ---: | --- | --- |
| `RiskEvaluationConfigTests` | 10 | 通过 | 默认模式、显式切换确认、阈值和成员上限 |
| `DefaultRiskListRuntimeRepositoryTests` | 28 | 通过 | 主库基线、同槽 Key、shadow、单 ZSet、重复与容量 |
| 累计限额状态/协调/对账/计数测试 | 12 | 通过 | 状态机终态、晚到事件、Redis 回滚、超时对账 |
| 支付终态 MQ 消费测试 | 1 | 通过 | 成功确认、失败取消、处理中保留 |

这些测试证明代码分支和状态转换，不替代真实 MySQL 行锁竞争、完整最大周期 shadow 或
生产 MQ 重放。

## 34. 阶段 8 定向验证

阶段 8 的基础验证结果：

| 范围 | 用例数 | 失败 | 错误 | 结论 |
| --- | ---: | ---: | ---: | --- |
| MQ 三态与三个消费者 | 29 | 0 | 0 | `DUPLICATE/FALLBACK/ACQUIRED` 分支符合数据库最终幂等边界 |
| 通用包装器注册门禁 | 9 | 0 | 0 | String 默认启用，其余集合包装器默认关闭 |
| 全局 ID 单元与装配 | 18 | 0 | 0 | Key、环境门禁、恢复下限和故障禁止降级通过 |
| Redis 环境前缀启动门禁 | 5 | 0 | 0 | `acquiring:{environment}` 精确隔离通过 |

真实 Redis 6 已验证 MQ 3 个用例、全局 ID 5 个用例；连续 10000 个编号和 20 线程共
100000 个编号均无重复。临时六节点 Redis 6 Cluster 的累计限额和频率生产脚本无
`CROSSSLOT`。

## 35. 阶段 9 真实 Redis 与 Cluster

执行：

```bash
./scripts/run-redis-stage9-tests.sh
./scripts/run-risk-redis-cluster-tests.sh
```

| 环境 | 用例数 | 失败 | 错误 | 覆盖 |
| --- | ---: | ---: | ---: | --- |
| 临时单机 Redis 6 | 12 | 0 | 0 | MQ 双桶、锁、全局 ID、容量、跨桶、唯一性和性能 |
| 容器停止后的连接拒绝 | 2 | 0 | 0 | MQ `FALLBACK`、全局 ID 明确失败 |
| 六节点 Redis 6 Cluster | 5 | 0 | 0 | 累计限额、滑动窗口重复、容量、`WRONGTYPE` 和性能 |
| 合计 | 19 | 0 | 0 | 所有容器均由脚本精确删除 |

Cluster 用例显式设置 `RiskFrequencyMode.SLIDING_WINDOW` 和切换确认标识，频率用例确实
执行单 ZSet 生产 Lua，不是历史固定窗口路径。

## 36. 阶段 9 静态检查

| 检查 | 结果 |
| --- | --- |
| `python3 scripts/verify-logging-rules.py --root .` | 通过；1363 个 Java 文件，敏感日志、必需事件和 trace 规则均 0 命中 |
| 本轮新增/修改测试的注释规范 | 通过；新增测试文件未出现在注释扫描命中清单 |
| 全仓 Java 注释扫描 | 原结论已失效；强化声明边界识别后基线为 `checked_java_files=1363`、`remaining_files=211`、`remaining_hits=1736`，当前治理中 |
| `git diff --check` | 通过 |

早期校验器修复了 16 个 MyBatis 多行方法签名误报，但当时仍会把跨越上一成员的 Javadoc
错误关联到当前成员，也没有完整跳过多行注解体，因此 `remaining_files=0` 属于假阴性。
强化方法边界和注解体识别后，可信基线扩大为 211 个文件、1736 个缺口；该范围包含生产
代码和测试桩，必须逐项补充真实职责、字段敏感性、金额时间单位、幂等终态和 Redis 故障
策略后才能重新判定通过。

## 37. 阶段 9 未覆盖项

当前环境不能提供以下生产级证据：

1. 真实 MySQL 数据集下缓存 hit/miss、数据库 QPS、连接池饱和和回源舱壁容量。
2. 生产或 UAT Redis Cluster/Sentinel 的主从切换、replica lag、网络延迟和 ACL。
3. `maxmemory` 淘汰、批量同时过期、热 Key、大 Key和长期内存碎片。
4. 安全缓存删除失败、Outbox 长时间积压和规则发布失效延迟的真实告警链路。
5. 多实例进程崩溃、支付准备事务超过 30 秒和渠道 I/O 故障组合。

上述项目在 `13-redis-acceptance-report.md` 中作为生产准入阻断项，不由本机测试推断通过。

## 38. 阶段 9 完整全项目回归

执行命令：

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 17)" mvn -Pdev clean test
```

Reactor 结果：23 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 53.790 秒。本次
`clean` 后生成 154 份 Surefire XML。

| 模块 | 用例数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| `payment-channel-library` | 63 | 0 | 0 | 2 |
| `component-core` | 36 | 0 | 0 | 0 |
| `component-db` | 46 | 0 | 0 | 0 |
| `component-excel` | 2 | 0 | 0 | 0 |
| `component-http` | 2 | 0 | 0 | 0 |
| `component-mq` | 2 | 0 | 0 | 0 |
| `component-redis` | 93 | 0 | 0 | 14 |
| `component-security` | 5 | 0 | 0 | 0 |
| `component-web` | 11 | 0 | 0 | 0 |
| `service-admin` | 97 | 0 | 0 | 0 |
| `service-checkout` | 2 | 0 | 0 | 0 |
| `service-job` | 15 | 0 | 0 | 0 |
| `service-merchant` | 17 | 0 | 0 | 0 |
| `service-openapi` | 75 | 0 | 0 | 5 |
| `service-payment` | 167 | 0 | 0 | 0 |
| `service-risk` | 104 | 0 | 0 | 6 |
| 合计 | 737 | 0 | 0 | 27 |

737 个测试中实际执行 710 个，失败 0、错误 0。其余 7 个 Reactor 聚合或骨架模块没有
独立测试用例。

### 38.1 跳过项与外部条件

| 测试组 | 跳过数 | 原因 | 当前证据 |
| --- | ---: | --- | --- |
| `RedisConnectionFailureIntegrationTests` | 2 | 未设置 `redis.failure.integration.enabled` | 阶段 9 停止容器后单独执行通过 |
| `RedisGlobalIdIntegrationTests` | 5 | 未设置 `global-id.redis.integration.enabled` | 阶段 9 单机 Redis 6 单独执行通过 |
| `RedisIdempotentIntegrationTests` | 4 | 未设置 `idempotent.redis.integration.enabled` | 阶段 9 单机 Redis 6 单独执行通过 |
| `RedisLockIntegrationTests` | 3 | 未设置 `lock.redis.integration.enabled` | 阶段 9 单机 Redis 6 单独执行通过 |
| `DefaultRiskListRuntimeRepositoryClusterIntegrationTests` | 5 | 未设置 `risk.redis.cluster.integration.enabled` | 阶段 9 六节点 Redis 6 Cluster 单独执行通过 |
| `MpgsApiClientLiveFlowTests` | 2 | 未设置 `MPGS_LIVE_TEST_ENABLED` | 未连接 MPGS Sandbox |
| `MerchantOpenApiMpgsLiveFlowTests` | 5 | 未设置 OpenAPI live/risk-block 开关 | 未连接 MPGS Sandbox |
| `RiskRuntimeMapperMySqlLiveTests` | 1 | 未设置 `risk.mysql.live.enabled` | 未连接真实 MySQL |
| 合计 | 27 |  | 19 个已有隔离环境通过证据，8 个外部 live 用例未执行 |

完整回归证明当前代码在 JDK 17 和 dev profile 下可编译，所有默认启用的自动化用例通过。
该结果与阶段 9 的 19 个隔离环境用例共同构成本地验收证据，但不覆盖 UAT/生产等价拓扑、
真实 failover、长期 shadow、监控告警闭环或生产量级容量。

## 39. Java 注释专项清理复验（进行中）

本轮先强化 `verify-java-comments.py` 对多行方法签名、完整注解体和相邻成员 Javadoc
归属的识别，再逐项清理真实注释缺口。Maven 默认运行时为 Java 26，不符合项目基线；
以下已执行命令均显式使用 Temurin 17.0.19。

定向命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
PATH=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin \
mvn -Pdev -pl service-risk -am \
  -Dtest=DefaultRiskEvaluationServiceTests,DefaultRiskListRuntimeRepositoryTests,RiskRuntimeValueNormalizerTests,DefaultMerchantLimitReservationStateServiceTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

模块命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
PATH=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin \
mvn -Pdev -pl service-risk -am test
```

| 验证 | 结果 |
| --- | --- |
| Java 注释门禁 | 治理中；1363 个文件，211 个文件、1736 个缺口 |
| 日志门禁 | 1363 个文件，敏感日志、必需事件和 trace 规则均 0 命中 |
| 风控定向测试 | 4 个测试类、70 个用例，失败 0、错误 0、跳过 0 |
| `service-risk` 模块测试 | 104 个用例，失败 0、错误 0、跳过 6 |
| Reactor | 8 个模块全部 `SUCCESS` |
| Diff 格式 | `git diff --check` 通过 |

定向和模块测试发生在首批注释清理之后，但强化校验器随后暴露了更大存量范围，因此不能
据此宣称全仓注释门禁通过。第 38 节完整全项目回归是本轮注释清理前的阶段 9 证据；全量
缺口归零并完成基础验证后，再按执行门禁单独确认是否重复执行完整全项目回归。

## 40. Java 注释专项清理完成与最终回归

第 39 节记录的是治理过程中的可信中间基线，现已由本节最终证据取代。生产代码、测试桩、
匿名实现和测试 Bean 的缺口均已按真实职责补充；测试替身注释明确区分参数捕获、固定返回、
CAS/锁/幂等模拟和禁止意外调用，不以接口名称复述替代业务说明。

最终执行：

```bash
python3 scripts/verify-java-comments.py
python3 scripts/verify-logging-rules.py --root .
git diff --check
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
PATH="$JAVA_HOME/bin:$PATH" \
mvn -Pdev clean test
```

| 验证 | 最终结果 |
| --- | --- |
| Java 注释门禁 | `checked_java_files=1363`、`remaining_files=0`、`remaining_hits=0` |
| 日志门禁 | 1363 个 Java 文件，敏感日志、必需事件和 trace 规则均 0 命中 |
| Diff 格式 | `git diff --check` 通过 |
| 完整 Reactor | 23 个模块全部 `SUCCESS`，`BUILD SUCCESS`，耗时 46.681 秒 |
| Surefire | 154 份测试套件，737 个用例，失败 0、错误 0、跳过 27 |

737 个测试中实际执行 710 个。27 个跳过项与第 38.1 节相同：19 个 Redis/Cluster/连接
拒绝场景已有阶段 9 隔离环境通过证据，剩余 8 个依赖 MPGS Sandbox 或真实 MySQL，仍须
作为 UAT 外部联调证据，不得解释为已执行通过。

## 41. 阶段 10 生产就绪定向验证

阶段 10 在既有 Redis 治理基础上增加业务指标、Prometheus 告警规则、缓存失效 Outbox
指标、外部/UAT 测试入口和隔离故障演练。指标测试只允许固定枚举标签，明确拒绝
merchantId、业务 Key、订单号、交易号、traceId 和异常正文等无界或敏感标签。

| 验证组 | 测试数 | 失败 | 错误 | 跳过 | 覆盖 |
| --- | ---: | ---: | ---: | ---: | --- |
| `component-redis` 指标与已插桩基础设施 | 40 | 0 | 0 | 0 | Cache、失效门禁、generation、全局 ID、幂等、锁、Prometheus 导出名和标签边界 |
| 风控 shadow 指标 | 2 | 0 | 0 | 0 | match、mismatch、unavailable 和固定维度 |
| Admin 缓存失效 Outbox | 12 | 0 | 0 | 0 | merchant/risk relay 批次、失败、饱和、Prometheus 导出名和指标 |
| 风控仓储与 shadow | 30 | 0 | 0 | 0 | 规则缓存、频率/累计 Lua、故障降级和 shadow 指标 |

监控配置已通过 Ruby YAML 安全解析，`RedisBusinessMetricsTests` 和
`CacheInvalidationOutboxMetricsTests` 使用真实 Prometheus Registry 断言告警所依赖的
snake_case 导出名。当前开发机没有 `promtool`，因此尚未取得官方 PromQL parser 结果；
规则已逐条人工复核，仍必须在 UAT 通过 Prometheus 规则加载 API 取得最终语法和告警状态
证据。

本地隔离演练入口：

```bash
./scripts/run-redis-stage10-drills.sh
```

最新证据目录：`target/redis-readiness/stage10/20260731T062255Z/`。

| 场景 | 结果 | 证据边界 |
| --- | --- | --- |
| LFU 淘汰 | `evicted_keys=1784` | 一次性单机 Redis 6；只证明演练注入生效 |
| 大 Key 检测 | 定位到 1 MiB 测试 String | `--bigkeys` 仅扫描隔离容器 |
| 热 Key 检测 | 测试 Key LFU counter 为 41，排名第一 | `--hotkeys` 仅扫描隔离容器 |
| 无持久化重启 | 重启后测试 Key 不存在 | 证明无持久化配置的数据丢失边界 |
| 有界 SET/GET 负载 | 每类 100000 请求完成 | 本地 Docker 数据，不作为容量结论 |
| 六节点 Cluster 主节点停止 | 副本提升后测试 Key 仍可读取 | 本地单容器六端口模型，不等同生产拓扑 |
| 外部访问保护 | `shared_or_external_redis_accessed=false` | 未访问共享 Redis、Nacos 或 MySQL |

`run-redis-external-live-tests.sh` 和 `run-redis-uat-readiness-tests.sh` 只提供受控入口。没有
显式确认、凭据或 UAT 环境标识时必须拒绝执行；本报告没有把 2 个渠道 MPGS、5 个 OpenAPI
MPGS 或 1 个真实 MySQL live 用例写为通过。

## 42. 阶段 10 完整全项目回归

用户通过第二道门禁后，使用 Temurin 17 执行：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
PATH="$JAVA_HOME/bin:$PATH" \
mvn -Pdev clean test
```

首次执行到 `component-redis` 时共运行 97 个测试，其中
`RedisGlobalIdAutoConfigurationTests` 有 2 个失败。原因是全局 ID 自动配置把
`RedisBusinessMetrics` 当作必需 Bean；隔离 `ApplicationContextRunner` 未启用可观测性
自动配置时，Redis 全局 ID 生成器因此无法装配。生产逻辑本身没有降级为本地发号，但这个
不必要的装配耦合会阻断只加载全局 ID 自动配置的上下文。

整改后，全局 ID 自动配置通过 `ObjectProvider<RedisBusinessMetrics>` 获取可选指标实例，
指标自动配置缺失时使用 `RedisBusinessMetrics.noop()`。指标存在时仍记录原有业务指标，
不存在时只关闭观测副作用，不改变 Redis `TIME`、状态 Lua、环境 Key 门禁或失败策略。

| 验证 | 结果 |
| --- | --- |
| 全局 ID 自动配置定向复验 | 7 个测试，失败 0、错误 0、跳过 0 |
| 完整 Reactor | 23 个模块全部 `SUCCESS`，`BUILD SUCCESS`，耗时 53.666 秒 |
| Surefire | 156 份测试套件，744 个用例，失败 0、错误 0、跳过 27 |

744 个测试中实际执行 717 个。27 个跳过项的边界与第 38.1 节一致：19 个 Redis、Cluster
和连接拒绝场景已有隔离环境通过证据；剩余 8 个依赖 MPGS Sandbox 或真实 MySQL，仍须在
受控 UAT/外部联调中执行，不能由本地全量回归推断通过。

## 43. 永久业务读模型补强定向验证

本节只记录 2026-07-31 永久缓存、ISO 单 Key、平台配置可靠失效、Admin pending 隔离和
风控永久快照补强后的新鲜验证证据，不改写前述历史回归结果。

本机默认 `mvn -v` 使用 JDK 26，不符合仓库基线；以下命令均显式使用：

```text
/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home
```

受影响模块编译：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -pl component-library/component-core,component-library/component-db,\
component-library/component-redis,service-admin,service-merchant,service-risk \
-am -DskipTests compile
```

结果为 13 个 Reactor 模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 7.818 秒。编译警告为
仓库既有 deprecated API、unchecked 和 Lombok `equals/hashCode` 提示，本轮未新增编译错误。

定向测试：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -pl component-library/component-redis,component-library/component-db,\
service-admin,service-merchant,service-risk -am \
-Dtest=PaymentRedisCacheAutoConfigurationTests,\
PaymentRedisEnvironmentGuardAutoConfigurationTests,PaymentRedisPropertiesTests,\
RedisCacheInvalidationGuardTests,IsoDictionaryServiceImplTests,\
AdminIsoDictionaryCacheInvalidationTests,AdminConfigServiceImplTests,\
MerchantConfigServiceImplTests,MerchantSecurityCacheInvalidationCoordinatorTests,\
MerchantSecurityCacheInvalidationRelayServiceTests,\
MerchantSecurityCacheInvalidationRetrySchedulerTests,\
MerchantSecurityCacheInvalidationPersistenceContractTests,\
MerchantSecurityCacheInvalidationEntryPointContractTests,\
AdminMonitorCacheApplicationServiceTests,RiskRuleCacheInvalidationCoordinatorTests,\
RiskEvaluationConfigTests,DefaultRiskRuleSnapshotRepositoryTests,\
DefaultRiskListRuntimeRepositoryTests \
-Dsurefire.failIfNoSpecifiedTests=false test
```

| 模块 | 测试数 | 失败 | 错误 | 跳过 | 主要覆盖 |
| --- | ---: | ---: | ---: | ---: | --- |
| `component-db` | 6 | 0 | 0 | 0 | ISO 永久短 Key、无历史回退、无 TTL、异常兜底不回写 |
| `component-redis` | 27 | 0 | 0 | 0 | 永久 Cache 注册、短前缀、环境门禁、pending Key、配置覆盖拒绝 |
| `service-admin` | 26 | 0 | 0 | 0 | ISO 失效、平台配置事务、Outbox 顺序/重试、Admin pending 隔离、风控 generation |
| `service-merchant` | 4 | 0 | 0 | 0 | 平台配置 pending 前后双检查、Redis 异常回源 MASTER、白名单 |
| `service-risk` | 41 | 0 | 0 | 0 | 永久 Hash/JSON 快照、generation 重建、容量/故障回退、迁移模式门禁 |
| 合计 | 104 | 0 | 0 | 0 | 18 个测试类 |

测试日志格式调整后曾使用不带 `-am` 的单模块命令复跑
`PaymentRedisPropertiesTests`，命令在 `testCompile` 阶段以退出码 1 结束，原因是当前工作区的
`component-core` 新类型尚未安装到本地 Maven 仓库；测试本身未开始执行。改用以下包含上游
Reactor 的命令后，5 个测试失败 0、错误 0、跳过 0，`BUILD SUCCESS`：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -pl component-library/component-redis -am \
-Dtest=PaymentRedisPropertiesTests -Dsurefire.failIfNoSpecifiedTests=false test
```

静态检查：

| 检查 | 结果 |
| --- | --- |
| `python3 scripts/verify-java-comments.py --root .` | `checked_java_files=1375`、`remaining_files=0`、`remaining_hits=0` |
| `git diff --check` | 通过 |
| 旧物理 Key 扫描 | 生产代码无旧 Spring Cache Name 和旧 ISO 访问；兼容 generation、30 秒 miss marker、ISO 负向测试按目录说明保留 |

本节记录时尚未执行修改后的全项目完整回归；用户随后通过回归门禁，结果见第 44 节。

## 44. 永久业务读模型补强完整回归

用户确认执行完整回归后，使用 Temurin 17 运行：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
PATH="$JAVA_HOME/bin:$PATH" \
mvn -Pdev clean test
```

| 验证 | 结果 |
| --- | --- |
| Reactor | 23 个模块全部 `SUCCESS`，`BUILD SUCCESS` |
| 总耗时 | 53.921 秒 |
| Surefire | 159 份测试套件，760 个测试 |
| 测试结果 | 失败 0、错误 0、跳过 27，实际执行 733 个 |
| Redis 治理重点模块 | `component-redis` 98、`service-admin` 105、`service-merchant` 21、`service-risk` 107 个测试，均无失败或错误 |

本次 `clean` 后的跳过项为：

| 测试组 | 跳过数 | 原因与证据边界 |
| --- | ---: | --- |
| `RedisConnectionFailureIntegrationTests` | 2 | 完整回归未开启连接拒绝开关；阶段 9 隔离演练已有独立证据 |
| `RedisGlobalIdIntegrationTests` | 5 | 完整回归未开启真实 Redis 开关；阶段 9 单机 Redis 6 已独立执行 |
| `RedisIdempotentIntegrationTests` | 4 | 同上 |
| `RedisLockIntegrationTests` | 3 | 同上 |
| `DefaultRiskListRuntimeRepositoryClusterIntegrationTests` | 5 | 未开启六节点 Cluster 开关；阶段 9 隔离 Cluster 已独立执行 |
| `MpgsApiClientLiveFlowTests` | 2 | 未连接 MPGS Sandbox |
| `MerchantOpenApiMpgsLiveFlowTests` | 5 | 未连接 MPGS Sandbox，未开启 OpenAPI live/risk-block 开关 |
| `RiskRuntimeMapperMySqlLiveTests` | 1 | 未连接真实 MySQL |
| 合计 | 27 | 前 19 个有历史隔离环境证据；后 8 个仍需受控 UAT/外部联调 |

完整回归没有访问共享 Redis、生产 Nacos、真实 MySQL、RocketMQ 或 MPGS Sandbox。结果证明
本地自动化范围内未发现行为回归，但不构成生产 Redis 模式切换或生产发布批准。

## 45. 商户统一事实源与共享缓存整改定向验证

本节记录 2026-08-01 商户资料一致性整改后的定向证据。Admin、Merchant Portal 和 OpenAPI
统一以 `base_merchant_info` 为事实源，共享永久缓存物理 Key：
`acquiring:{environment}:merchant:info:{merchantId}`。Redis Value 保存商户主表的完整资料，
其中联系人和详细地址按受保护字段管理，禁止进入日志和无关接口；JWT Secret、RSA 私钥、
AES Key 等可直接使用的密钥材料不进入该缓存。

后端定向测试使用 Temurin 17 执行：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
PATH="$JAVA_HOME/bin:$PATH" \
mvn -pl component-library/component-db,component-library/component-redis,\
service-admin,service-merchant,service-openapi -am \
-Dtest=DefaultMerchantRuntimeProfileCacheServiceTests,\
PaymentRedisCacheAutoConfigurationTests,ImmediateCacheEvictionServiceTests,\
MerchantSecurityCacheInvalidationCoordinatorTests,\
MerchantSecurityCacheInvalidationEntryPointContractTests,\
MerchantSecurityCacheInvalidationRelayServiceTests,\
MerchantSecurityCacheInvalidationRetrySchedulerTests,\
AdminMerchantInfoServiceDataSourceContractTests,AdminMerchantInfoServiceImplTest,\
MerchantOpenApiKeyApplicationServiceTests,MerchantProfileServiceImplTests,\
MerchantSecurityServiceDataSourceContractTests,MerchantSecurityServiceSharedCacheTests \
-Dsurefire.failIfNoSpecifiedTests=false test
```

| 模块 | 测试数 | 失败 | 错误 | 跳过 | 主要覆盖 |
| --- | ---: | ---: | ---: | ---: | --- |
| `component-db` | 11 | 0 | 0 | 0 | 永久商户资料缓存、结构修订刷新、负缓存、MASTER 回源和并发保护 |
| `component-redis` | 18 | 0 | 0 | 0 | 永久 Cache 注册、配置保护、立即精确失效和 miss marker 协同删除 |
| `service-admin` | 15 | 0 | 0 | 0 | 商户写操作数据源、事务 Outbox、提交后失效、门禁释放和失败重试 |
| `service-merchant` | 4 | 0 | 0 | 0 | 当前商户资料查询/更新、允许字段边界、OpenAPI 密钥读写数据源和共享失效 |
| `service-openapi` | 3 | 0 | 0 | 0 | 共享商户缓存、状态校验和安全密钥 MASTER 读取 |
| 合计 | 51 | 0 | 0 | 0 | 13 个测试类，14 个 Reactor 模块全部 `SUCCESS` |

Merchant Portal 前端验证：

```bash
npm --workspace @acquiring/merchant-portal run typecheck
npm --workspace @acquiring/merchant-portal run build
```

两条命令均以退出码 0 完成。生产构建转换 1843 个模块并成功生成产物；Rollup 第三方
PURE 注释、静态/动态重复导入和大分包提示为既有告警，本轮没有新增类型或构建错误。

最终静态门禁：

| 检查 | 结果 |
| --- | --- |
| `python3 scripts/verify-java-comments.py --root .` | `checked_java_files=1389`、`remaining_files=0`、`remaining_hits=0` |
| 后端 `git diff --check` | 通过 |
| 前端 `git diff --check` | 通过 |

浏览器目标流程为：`/merchant/info` 加载当前商户资料 -> 修改账单描述并保存 -> 后端更新后
重新查询 -> 页面显示新值和新更新时间。Browser 插件访问 localhost 时返回
`ERR_BLOCKED_BY_CLIENT`，因此按前端测试回退策略使用隔离 Playwright CLI 会话，并通过
本地模拟登录态和 API 响应验证页面交互；该结果不等同于真实后端联调或真实数据库验收。

| 浏览器检查 | 结果 |
| --- | --- |
| 页面身份 | URL 为 `/merchant/info`，标题为“商户信息管理 - Vexra Merchant” |
| 首屏与错误遮罩 | 页面主体正常渲染，未出现 Vite/Vue 错误遮罩 |
| 保存交互 | 成功提示为“商户资料已更新”，保存后按钮恢复禁用 |
| 请求顺序 | `PUT /merchant/info` 返回 200，随后 `GET /merchant/info` 返回 200 |
| 提交后刷新 | 账单描述显示最新值，更新时间立即显示 `2026-08-01 13:05:00` |
| 响应式布局 | 1440、768、390 像素视口分别显示 3、2、1 列；页面与表格均无横向溢出 |
| 控制台 | 最终页面错误 0、警告 0 |

移动端首次检查发现只读描述表的最小内容宽度会撑大单列 Grid，造成字段裁切。页面已增加
Grid 收缩约束，并按视口同步只读描述列数；修复后 390 像素视口的页面、`body` 和表格
`scrollWidth` 均未超过各自可视宽度。

本节只完成受影响范围的定向回归。按照执行门禁，修改后的全项目完整回归须在用户再次确认
后单独执行；在此之前不得将本节结果解释为全仓回归通过。

## 46. 商户密钥重建、service-data 日志与最终全仓回归

本节记录 2026-08-01 商户删除后使用相同商户号重新开户或重新生成密钥的缓存一致性修复，
以及 `service-data` 日志配置和全仓构建的最终复验结果。

问题根因是永久 `merchant:keyMeta` 仍可能保存旧数据库记录 ID 和 revision，OpenAPI 进程内
敏感材料缓存因而继续命中旧版本。修复后，开户、重新配置和 JWT 密钥轮换均在写库前登记
`merchant:info`、`merchant:keyMeta` 的 pending 与事务 Outbox，提交后刷新非敏感快照，并
清除当前 OpenAPI 实例内该商户的短时敏感材料。JWT Secret、RSA 私钥、公钥正文和 AES Key
仍不写入 Redis。

| 验证 | 结果 |
| --- | --- |
| 密钥与本地缓存定向测试 | 4 个测试类、11 个测试，失败 0、错误 0、跳过 0 |
| 同商户号重建回归 | 先复现旧 Secret 被使用，再验证重建后使用最新 Secret；最终测试通过 |
| 已删除 SQL 契约清理 | 删除 7 个只引用 `delete SQL` 提交已移除文档的失效断言；保留 6 个 Mapper/CAS/Nacos 有效契约并全部通过 |
| 受影响模块回归 | 20 个 Reactor 模块全部 `SUCCESS`，`BUILD SUCCESS`，耗时 40.100 秒 |
| 全仓冷编译与测试 | 24 个 Reactor 模块全部 `SUCCESS`；173 个测试套件、814 个测试，失败 0、错误 0、跳过 27，实际执行 787 个 |
| 全仓打包 | 24 个 Reactor 模块全部 `SUCCESS`，所有服务完成 JAR 打包，耗时 55.626 秒 |
| Java 注释门禁 | `checked_java_files=1434`、`remaining_files=0`、`remaining_hits=0` |
| 日志门禁 | 1434 个 Java 文件，敏感日志、必需事件和 trace 规则均 0 命中 |
| Logback XML | OpenAPI 与 Data 两份 XML 均通过 `xmllint`；归一化应用名后逐行无差异 |
| Diff 格式 | `git diff --check` 通过 |

`service-data/src/main/resources/log-config/logback-spring.xml` 与
`service-openapi/src/main/resources/log-config/logback-spring.xml` 保持相同日志格式、上海时区、
`traceId`、控制台/文件 Appender、滚动归档、异步队列和 Root 级别。唯一必要差异是默认
`applicationName` 分别为 `service-data` 和 `global-payment`。

27 个跳过项仍是需要显式开关或外部环境的 Redis、Cluster、MPGS Sandbox 和真实 MySQL
测试。本轮没有据此扩大生产准入结论；UAT 等价拓扑、多实例、真实 MQ 和渠道联调仍按验收
报告中的生产阻断项执行。

## 47. Redis 6.2.23 Cluster 接入最终复验

本节记录 `feat_scott_redisMQupdate` 分支完成 Lettuce Cluster、Redisson Cluster、统一分布式
锁和配置注释治理后的最终证据。历史章节中的测试数字保留为对应代码时点的证据，不用于替代
本节最新结果。

全项目回归使用 JDK 17 执行：

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 17)" mvn -Pdev clean test
```

| 验证 | 结果 |
| --- | --- |
| Reactor | 24 个模块全部 `SUCCESS`，`BUILD SUCCESS` |
| Surefire | 175 份测试套件、801 个测试 |
| 测试结果 | 失败 0、错误 0、跳过 27，实际执行 774 个 |
| 执行时间 | 2026-08-01 23:53:35（Asia/Shanghai）完成 |

一次性 Redis 6.2.23 六节点 Cluster 回归通过 15 个真实 Cluster 用例，覆盖 Redisson 跨客户端
互斥、可重入、固定租约、Watchdog、MQ 双桶 Lua、100000 个并发全局 ID、缓存 generation
同槽 Lua 和 token 持有者释放；另有 2 个连接拒绝降级用例通过。风控独立 Cluster 回归的
2 个用例也全部通过，覆盖累计限额 reserve/rollback 和频率计数多 Key Lua。所有测试均为
失败 0、错误 0，未出现 `CROSSSLOT`。

本地服务启动复验中，`service-payment`、`service-checkout`、`service-job` 和
`service-payout` 均到达 `Started ...Application`，日志未出现 Redis 连接、认证、重定向、
`CROSSSLOT` 或 Bean 初始化异常。`service-job` 在收到 `SIGTERM` 后 15 秒内未退出并被强制
停止，这是独立的停机行为问题，不是 Redis 启动失败。

验证结束后，一次性 stage9/risk 容器均已删除，`18000-18005` 和 `17999` 无测试监听，
共享开发集群中也没有 `acquiring:it-*` 或 `acquiring:cluster-it*` 测试 Key。现有
`17001-17006` 是 7001-7006 节点的 Cluster Bus 端口，不是测试残留。

本轮没有停止共享开发集群 Master，也没有执行共享集群故障切换。该操作需要单独确认、明确
目标 Master/Replica 和恢复步骤。UAT/生产等价拓扑、ACL/TLS、真实 RocketMQ、多实例、
replica lag 和故障切换仍属于环境验收，不得由本地结果推断通过。
