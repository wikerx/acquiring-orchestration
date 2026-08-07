# Redis 变更报告

## 1. 当前治理进度

截至 2026-07-31，`REDIS-GOVERNANCE-001` 已完成阶段 0、阶段 1、阶段 2 和阶段 3。
阶段 4-A 已完成代码实现和完整自动化回归；阶段 4-B 已完成同槽代码路径、真实 Redis
Cluster `CROSSSLOT` 测试、相关模块回归和全项目完整回归，但生产迁移与故障验收仍待执行。
阶段 4-C 已完成 `RG-P1-03` 代码实现、基础定向验证和完整全项目自动化回归。阶段 4-D
已完成 `RG-P1-07` 支付锁代码治理、定向自动化验证和完整全项目回归。阶段 4-E 已完成
`RG-P1-06` 代码实现、定向回归和完整全项目回归。阶段 5 已完成低风险查询缓存代码治理
和相关模块回归。阶段 6 已完成累计限额生命周期，阶段 7 已完成 ZSet 滑动窗口，阶段 8
已完成 MQ 三态去重、通用包装器和全局 ID 治理。阶段 9 已完成本机临时 Redis 6、
六节点 Redis 6 Cluster、基础性能、故障演练和完整全项目回归；生产准入仍受真实拓扑、
shadow、监控和容量证据约束。

| 工作项 | 状态 | 结论 |
| --- | --- | --- |
| `RG-P0-01` 安全缓存可靠失效 | 已完成实现和完整自动化回归 | 商户运行资料和 OpenAPI 访问策略使用失效门禁、事务 Outbox、精确删除和持续重试 |
| `RG-P0-02` 风控规则版本与可靠失效 | 已完成实现和完整自动化回归 | 使用规则统一代际、事务 Outbox、发布门禁和持续重试 |
| `RG-P0-03` 累计限额主库基线 | 已完成 | 风控运行时仓储统一从 MASTER 获取规则和累计基线 |
| `RG-P0-04` 防重放装配门禁 | 已完成 | required 模式不再允许 Redis Bean 缺失时静默放行 |
| `RG-P1-04/05` Admin Redis 监控收口 | 已完成实现和完整自动化回归 | 使用受限 SCAN，只开放平台配置缓存 Key 元数据和单 Key 删除 |
| `RG-P1-09` 实时交易详情缓存移除 | 已完成实现和完整自动化回归 | 交易详情恢复数据库实时查询，删除 Cache Name、TTL 和全部 evict |
| `RG-P1-10` 环境前缀门禁 | 已完成代码门禁和完整自动化回归 | test/uat/prod 前缀错配阻断启动；真实环境验收待完成 |
| `RG-P1-06` Serializer v2 | 已完成实现和完整自动化回归 | 精确类型登记、受控新写、历史只读兼容；双向兼容通过后保留原 Key |
| `RG-P1-01/02` Cluster Lua 同槽治理 | 代码路径和真实 Cluster 测试完成，生产切换未完成 | 默认 `LEGACY`；`SHADOW` 双写观察；`CLUSTER_SAFE` 受显式确认门禁保护 |
| `RG-P1-03` 累计限额预占生命周期 | 已完成实现和完整自动化回归 | 持久化 reserve-confirm-cancel、支付终态 Outbox、补偿和超时对账 |
| `RG-P1-07` 支付锁治理 | 已完成实现和完整自动化回归 | 精简环境隔离 Key、动态值 SHA-256 摘要、锁只覆盖本地准备事务；真实环境验收待完成 |
| 阶段 5 低风险查询缓存 | 已完成代码治理和模块回归，后续永久缓存方案已补强 | 平台配置白名单、ISO 单一永久 Key、商户 miss marker/回源舱壁、无收益缓存退役 |
| 阶段 6 累计限额生命周期 | 已完成代码治理和本地验证 | 持久状态机、终态 Outbox、补偿、对账和同槽 Lua；生产默认保持 `LEGACY` |
| 阶段 7 频率滑动窗口 | 已完成代码治理和真实 Cluster 验证 | 单 ZSet、容量和异常 `REVIEW`；生产切换等待完整 shadow |
| 阶段 8 锁与幂等 | 已完成代码治理和真实 Redis 验证 | MQ 双桶三态、包装器门禁、全局 ID 单 Hash 与恢复门禁 |
| 阶段 9 本地验收 | 已完成基础验证和完整自动化回归 | 19 个真实 Redis/Cluster/故障用例通过；完整回归 737 个测试失败 0、错误 0；生产级故障和容量仍未验 |

阶段 4-A 修改后执行 `mvn -Pdev clean test`，23 个 Reactor 模块全部成功；627 个测试
无失败、无错误，13 个依赖外部环境的 live/integration 测试跳过。

阶段 4-B 执行 31 个定向测试、2 个真实 Redis 6 Cluster 测试和 216 个相关模块测试，
均无失败或错误；随后执行全项目 `clean test`，640 个测试无失败或错误，15 个需外部环境
的 live/integration 测试跳过。

阶段 4-C 在 JDK 17 下执行风险侧 84 个扩大定向测试和支付侧 33 个扩大定向测试，均无
失败或错误；随后执行全项目 `clean test`，657 个测试无失败或错误，15 个需外部环境的
live/integration 测试跳过。

阶段 4-D 先以新增行为测试复现裸 Key/长锁的 2 个预期失败，并以追加测试复现解锁异常
阻断渠道调用的 1 个预期错误，再完成实现；支付锁与并发幂等定向回归执行 91 个测试，
失败 0、错误 0、跳过 0。随后执行全项目 `clean test`，660 个测试无失败或错误，15 个
需外部环境的 live/integration 测试跳过。

阶段 4-E 以历史 Value、污染类型和未登记写入样本完成 Serializer v2 TDD；Serializer
边界测试 11 个、真实业务 DTO 兼容测试 3 个均通过。相关 14 个 Reactor 模块定向回归
全部成功；随后执行完整 `mvn -Pdev clean test`，23 个 Reactor 模块全部成功，671 个
测试失败 0、错误 0、跳过 15。

## 2. `RG-P0-01` 处理结论

商户禁用、基础资料变化和 IP 白名单收紧不能等待 Spring Cache TTL，也不能在 Redis 删除
失败时继续把旧策略用于鉴权。本次引入商户维度 token 门禁，并把失效意图与业务写事务
绑定：

```text
Admin 写事务开始
-> 获取 merchantId 对应的 Redis 失效门禁
-> 同一事务写业务数据和 Outbox
-> 数据库提交
-> 提交后精确删除 Spring Cache 物理 Key
-> token compare-delete 释放门禁
-> Outbox 标记 SENT
```

删除、释放或标记失败时，Outbox 保留 `INIT`/`FAILED`，调度器每 5 秒持续重试。安全缓存
读取在调用独立 `@Cacheable` reader 前后检查门禁；pending 或门禁查询异常时丢弃旧缓存，
直接查询 MASTER。OpenAPI 访问策略的主库读取异常继续拒绝请求，不使用旧策略放行。

## 3. `RG-P0-01` 主要变更

| 模块 | 变更 | 目的 |
| --- | --- | --- |
| `component-core` | 新增 `CacheInvalidationGuard` 和 token lease 契约 | 在共享缓存读写模块间传递窄化的门禁语义 |
| `component-redis` | 新增 Redis token 门禁、立即精确删除和安全 Cache EVICT/CLEAR 失败抛出 | 防止事务感知删除延迟和安全失效异常被吞 |
| `component-db` | 商户运行资料拆成门禁 facade 与独立缓存 reader，读取显式走 MASTER | pending、门禁异常或命中竞态时不返回旧商户状态 |
| `service-openapi` | OpenAPI 访问策略拆成门禁 facade 与独立缓存 reader；JWT merchantKey 改走 MASTER | IP 收紧或商户禁用后不受从库延迟和旧策略影响 |
| `service-admin` | 新增商户安全缓存 Outbox、提交后 relay、5 秒调度和 8 个写入口接入 | 把数据库提交和可靠缓存失效关联起来 |
| `docs/redis` | Key 规范改为精简格式并登记两个门禁 Key | 新增 Key 不再默认携带 service 和 version 层级 |

## 4. `RG-P0-01` Key 与数据库变更

新增 Key 使用：

```text
acquiring:{environment}:{domain}:{business}[:{businessKey}]
```

| 用途 | Key Pattern | 类型 | 生命周期 |
| --- | --- | --- | --- |
| 商户运行资料失效门禁 | `acquiring:{environment}:merchant:info:pending:{merchantId}` | String token | 默认 2 小时 |
| OpenAPI 访问策略失效门禁 | `acquiring:{environment}:merchant:openapi:pending:{merchantId}` | String token | 默认 2 小时 |
| 平台公开配置失效门禁 | `acquiring:{environment}:config:public:pending:{configKey}` | String token | 默认 2 小时 |

门禁 TTL 只用于清理进程退出等异常遗留租约，不是永久缓存的过期替代。Outbox 的持续重试
才是最终收敛机制；门禁删除成功前，读取端必须绕过永久缓存并查询 MASTER。

新增表 `merchant_security_cache_invalidation_outbox`，`event_id` 唯一，`version` 用于 CAS，
到期扫描索引覆盖状态、下次重试时间、创建时间和主键。迁移脚本：

```text
service-admin/src/main/resources/sql/merchant-security-cache-invalidation-outbox-migration.sql
```

部署必须先执行迁移，再发布 Admin 和读取服务。

## 5. `RG-P0-01` 故障策略

| 场景 | 当前行为 |
| --- | --- |
| Admin 无法获取门禁 | 当前业务写事务不执行，返回失败 |
| 数据库事务回滚 | Outbox 同事务回滚并释放自身门禁 |
| 提交后精确删除失败 | 不释放门禁，Outbox 5 秒后持续重试 |
| 门禁 pending | 不调用旧缓存，直接查询 MASTER |
| 门禁状态查询异常 | 按状态未知处理，绕过旧缓存并查询 MASTER |
| 缓存命中期间新建门禁 | facade 二次检查后丢弃已读旧值，查询 MASTER |
| Spring Cache GET/PUT 异常 | 普通读穿回源；数据库仍是事实源 |
| 安全 Cache EVICT/CLEAR 异常 | 抛出并进入持久重试，不再静默吞异常 |
| OpenAPI 策略主库异常 | 请求失败，不使用旧禁用策略绕过 IP 白名单 |

## 6. `RG-P0-02` 处理结论

原实现的风控规则缓存没有 Admin 写入失效链路。新增黑名单、删除白名单、启用拒绝规则后，
旧 miss/pass 最长可继续 60~300 秒。本次将全部规则查询缓存迁移到统一 generation，
Admin 写事务提交后原子切换 generation，旧代际 Key 不批量删除，只按原 TTL 自然过期。

```text
Admin 数据库事务开始
-> 获取 Redis 发布门禁
-> 同一事务写入 Outbox
-> 数据库提交
-> 原子切换 generation 并释放门禁
-> Risk 后续查询只访问新 generation
```

数据库回滚时不切换 generation，只由门禁持有者释放门禁。进程退出、Redis 短时不可用或
提交后发布失败时，Outbox 保留 `INIT`/`FAILED` 事件，由定时中继持续重试。

## 7. `RG-P0-02` 主要变更

| 模块 | 变更 | 目的 |
| --- | --- | --- |
| `component-redis` | 新增 `RedisCacheGenerationStore`、代际状态/发布凭证和 read/begin/commit Lua | 原子协调当前代际和发布门禁，支持提交、回滚释放及同代际幂等重试 |
| `service-risk` | 规则缓存 Key 增加 generation，规则查询统一走 MASTER | 发布中或 generation 查询异常时绕过 Redis，避免读取旧规则 |
| `service-admin` | 新增 `risk_cache_invalidation_outbox`、协调器、中继和 5 秒重试调度 | 把失效意图与业务写事务绑定，提交后发布，失败后持久重试 |
| `service-admin` | 接入 13 个风控配置写入口和 5 个商户 IP 白名单写入口 | 覆盖 create、update、delete、status、import、release 等规则变化 |
| `docs/redis` | 更新 Cache Catalog、变更报告和测试报告 | 登记新增 Key、失败策略、测试证据和剩余门禁 |

## 8. `RG-P0-02` Key 与数据结构

| 用途 | Key Pattern | 类型 | 生命周期 |
| --- | --- | --- | --- |
| 当前规则代际 | `acquiring:{env}:component-redis:cache:generation:v1:risk-runtime-rule:current` | String | 无 TTL |
| 规则发布门禁 | `acquiring:{env}:component-redis:cache:generation:v1:risk-runtime-rule:publication` | String token | 首次发布 30 分钟，恢复发布 30 秒 |
| 风控规则缓存 | `acquiring:{env}:service-risk:risk:runtime-rule:v1:{generation}:...` | String JSON/boolean | hit 默认 300 秒，miss 默认 60 秒 |

频率计数、累计额度、累计预占状态等运行时状态 Key 未迁入 generation，避免规则发布误清
正在使用的业务状态。

## 9. `RG-P0-02` 一致性与故障策略

| 场景 | 当前行为 |
| --- | --- |
| Admin 无法取得发布门禁 | 抛出异常并回滚当前数据库写事务 |
| Admin 数据库事务回滚 | 释放自身门禁，不切换 generation，Outbox 随事务回滚 |
| 数据库提交后 Redis 发布失败 | Outbox 标记 `FAILED`，5 秒后持续重试，不设耗尽次数 |
| 原门禁已过期 | 获取 30 秒恢复门禁，以数据库 CAS 更新发布凭证后重试 |
| 恢复凭证写数据库失败 | 主动释放刚取得的门禁，避免无事件持有者长期阻塞 |
| Risk 发现发布门禁 | 不读取、不写入规则缓存，直接查询 MASTER |
| generation 或普通规则缓存访问异常 | 直接查询 MASTER；风控规则不因 Redis 异常默认放行 |
| 重复发布同一 generation | Lua 返回幂等成功，Outbox 最终标记 `SENT` |

## 10. `RG-P0-02` 数据库变更

新增 `risk_cache_invalidation_outbox`。`event_id` 使用唯一索引保证事件唯一性，
`version` 用于 CAS，`(event_status, next_retry_time, create_time, id)` 支持有界到期扫描。
所有时间点字段使用 `DATETIME(3)`。

迁移脚本：

```text
service-admin/src/main/resources/sql/risk-cache-invalidation-outbox-migration.sql
```

部署顺序必须先执行迁移，再发布包含新协调器的 Admin 应用；缺表时规则写事务会失败并回滚。

## 11. 回滚与剩余风险

1. 规则缓存可通过停用 Redis 能力退化为 MASTER 直查，不能回滚到无失效的旧缓存放行。
2. 旧 `risk:runtime:*` 规则缓存不批量删除，只自然过期。
3. 当前 generation Lua 使用两个物理 Key。非 dev Redis 拓扑尚未核实，真实 Redis Cluster
   的同槽迁移和 failover 验证属于阶段 4；完成前不得据此报告批准 Cluster 生产上线。
4. 当前没有真实 Redis、真实 MySQL、进程崩溃和多实例并发集成证据，详见测试报告。

## 12. 阶段 4-A 主要变更

| 模块 | 变更 | 目的 |
| --- | --- | --- |
| `service-admin` | `AdminMonitorCacheApplicationService` 使用 `SCAN COUNT 100`，最多扫描 1000 个 Key，pageSize 最大 100 | 删除生产路径中的阻塞式 `KEYS` |
| `service-admin` | 仅允许 `{cachePrefix}config:public:` 中四个登记数据 Key；隐藏 pending；Value 固定不可读；删除记录 Key SHA-256 摘要 | 防止读取或删除控制门禁、全局 ID、防重放、锁、风控和幂等状态 |
| `service-admin` | 删除接口增加 `OperationLog`，关闭请求和响应记录 | 保留操作审计，同时避免原始 Key 进入审计参数 |
| `component-redis` | 新增 test/uat/prod 环境前缀启动门禁 | 阻止受保护环境复用其他环境命名空间 |
| `service-payment` | 删除 `transaction:detail` 的 Cache Name、TTL、`@Cacheable`、失效服务和全部写侧 evict | 交易实时状态和大聚合不再进入查询缓存 |

受保护环境必须满足：

```text
payment.redis.key-prefix = acquiring:{environment}
payment.cache.redis.key-prefix = acquiring:{environment}
payment.global-id.state-key 以 acquiring:{environment}: 开头
```

新增 Key 继续采用已确认的精简规则：

```text
acquiring:{environment}:{domain}:{business}[:{businessKey}]
```

不默认加入 `service` 或 `v{version}`；历史 Key 不在本批批量改名。

## 13. Serializer v2 差距审计

`PaymentRedisSerializerFactory` 已集中使用 `BasicPolymorphicTypeValidator`，只允许
`com.scott.payment.`、`java.util.`、`java.time.` 和 `java.math.`，现有测试可拒绝
`java.io.File`。但工厂仍调用 `activateDefaultTyping(...NON_FINAL...)`，Value 仍携带类型
元数据，尚未达到明确缓存 DTO 或无类型元数据 JSON 的最终目标。

本批不切换 wire format，原因是直接替换会使历史 Value 无法兼容反序列化。后续必须先
准备独立新业务 Key、旧值样本兼容测试和双读/自然过期方案，再单独批准迁移。

## 14. 阶段 4-A 剩余风险

1. test/uat/prod 的真实 Redis 拓扑、ACL、TLS、Server 版本和 Nacos 最终值仍未核验。
2. Admin SCAN 已有代码级上限，但尚未在真实大 Keyspace 上验证延迟和连接超时。
3. 交易详情改为实时数据库查询后尚未完成容量压测和慢查询基线。
4. Serializer v2 风险仍开放，不能把本次审计表述为序列化治理完成。
5. 完整回归跳过了 13 个依赖真实 Redis、MySQL 或 MPGS Sandbox 的测试，尚不能替代真实
   环境、集群故障和外部渠道验收。

## 15. 阶段 4-A 完整自动化回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -Pdev clean test
```

Reactor 结果为 23 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 54.883 秒。Surefire
的 131 份 XML 共记录 627 个测试，失败 0、错误 0、跳过 13。模块明细、跳过原因和未覆盖
的真实环境验证见 `10-redis-test-report.md`。

## 16. 阶段 4-B 主要变更

| 模块 | 变更 | 目的 |
| --- | --- | --- |
| `component-redis` | 新增 `coLocatedBusinessKey`，Hash Tag 统一使用组件生成的 SHA-256 摘要 | 生成同槽 Key，并拒绝调用方注入 `{}`、空白或超长片段 |
| `service-risk` | 新增 `LEGACY/SHADOW/CLUSTER_SAFE`；累计限额和频率计数接入同槽路径 | 保持默认行为，通过双写观察后再切换 |
| `service-risk` | `CLUSTER_SAFE` 必须同时配置 `counter-cutover-confirmed=true` | 防止未观察、未核对就误切生产 |
| `service-risk` | 三段 Lua 移入 `META-INF/payment/redis/scripts/v1` | 集中管理脚本资源，避免业务类内嵌长脚本 |
| `scripts` | 新增 6 节点 Redis 6 Cluster 集成脚本 | 真实验证多 Key Lua 无 `CROSSSLOT` |
| `docs/deployment/nacos` | 登记迁移模式与切换确认配置，默认 `LEGACY/false` | 明确部署行为和生产切换门禁 |

回滚异常日志只记录 aggregate Key 的 SHA-256 摘要，不再输出完整物理 Key。

## 17. 阶段 4-B Key 与迁移模式

```text
acquiring:{environment}:risk:merchant-limit:{scopeDigest}:total
acquiring:{environment}:risk:merchant-limit:{scopeDigest}:reservation:{transactionDigest}
acquiring:{environment}:risk:frequency:{scopeDigest}:count
acquiring:{environment}:risk:frequency:{scopeDigest}:transaction:{transactionDigest}
```

新 Key 沿用已确认的精简规则，不加入服务名或默认 `v{version}`。`scopeDigest` 是 Redis
Hash Tag，组件根据稳定业务范围生成；交易标识只以 SHA-256 摘要进入 Key。

| 模式 | 行为 | 生产用途 |
| --- | --- | --- |
| `LEGACY` | 只使用历史 Key | 当前默认和紧急回退 |
| `SHADOW` | 历史 Key 决策，同时写同槽 Key；新路径失败不改变 legacy 判断 | 完整最大周期差异观察 |
| `CLUSTER_SAFE` | 只使用同槽 Key | 观察完成、显式确认后的目标模式 |

## 18. 阶段 4-B 验证结论

| 验证 | 结果 | 证据 |
| --- | --- | --- |
| 定向单元测试 | 31 个通过 | Key 命名、模式门禁、legacy/shadow/cluster-safe、SHADOW 故障降级 |
| 真实 Redis 6 Cluster | 2 个通过 | 累计预留幂等/回滚、频率幂等/递增，无 `CROSSSLOT` |
| 相关模块回归 | 216 个，失败 0、错误 0、跳过 8 | 8 个 Reactor 模块全部 `SUCCESS` |
| 全项目完整回归 | 640 个，失败 0、错误 0、跳过 15 | 23 个 Reactor 模块全部 `SUCCESS` |

## 19. 阶段 4-B 剩余风险

1. 生产仍为 `LEGACY`，同槽路径没有完成完整最大周期 SHADOW 观察和差异验收。
2. `RG-P1-03` 的 reserve-confirm-cancel、支付失败补偿、超时扫描和对账仍未实现。
3. Redis Cluster failover、脚本超时重试、真实生产拓扑、ACL、TLS 和 Server 版本未验收。
4. 固定窗口频率的边界突发问题仍在，单 ZSet 滑动窗口属于后续阶段。
5. 全项目回归仍跳过 15 个依赖真实 Redis、MySQL 或 MPGS Sandbox 的测试；其中阶段 4-B
   的 2 个真实 Cluster 用例已通过独立脚本验证，其余外部环境验收仍未完成。

## 20. 阶段 4-B 完整自动化回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -Pdev clean test
```

Reactor 结果为 23 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 52.499 秒。本次
`clean` 后生成 133 份 Surefire XML，共记录 640 个测试，失败 0、错误 0、跳过 15。
模块明细和跳过原因见 `10-redis-test-report.md`。

## 21. 阶段 4-C 主要变更

| 模块 | 变更 | 目的 |
| --- | --- | --- |
| `service-risk` | 新增 `PREPARING -> RESERVED -> CONFIRMED/CANCELLED` 持久化生命周期、唯一业务键和版本 CAS | Redis 预占不再是唯一事实，支持支付失败补偿和中断恢复 |
| `service-risk` | `PREPARING` 使用独立事务先提交，再执行 Redis Lua；预占时持久化 `LEGACY/SHADOW/CLUSTER_SAFE` | Redis 变更前保留恢复意图，并保证跨模式撤销使用原投影 |
| `service-risk` | 取消编排在事务内按 `transactionId` 加行锁，锁内检查终态、回滚 Redis、CAS 写 `CANCELLED` | 防止并发成功确认后又错误扣减 Redis |
| `service-risk` | 消费支付创建、回调和状态变更事件，并扫描超时 `PREPARING/RESERVED` | MQ 重复、进程中断或事件延迟时仍可按支付事实恢复 |
| `service-payment` | 风控结果携带稳定 `transactionId`、单次 `riskRecordNo` 和 `merchantLimitReserved` | 支付链路能准确识别需要补偿的预占 |
| `service-payment` | 本地失败补偿调用风控撤销；终态 CAS 成功后才在同事务写状态变更 Outbox | 避免支付未落终态就确认预占，重复 CAS 不重复产生成功事件 |
| `service-payment` | 启用交易 Outbox 定时中继，并补充 dev Nacos 配置 | 持续投递已提交终态事件，支持风险生命周期最终收敛 |
| `service-admin` | 新增 `risk_merchant_limit_reservation` 迁移草案 | 提供生命周期事实表、唯一约束、状态扫描索引和基线索引 |

## 22. 阶段 4-C 一致性与并发规则

```text
提交 PREPARING
-> Redis 原子预占
-> CAS 标记 RESERVED
-> 支付 SUCCESS: CAS 标记 CONFIRMED
-> 支付 FAILED: 行锁检查 -> Redis 幂等回滚 -> CAS 标记 CANCELLED
```

1. `(transaction_id, rule_id, limit_type, period_bucket)` 唯一约束兜底重复风控评估。
2. `transactionId` 在支付受理前生成并贯穿风控、支付事实、Outbox 和补偿调用。
3. 同一评估只生成一个 `riskRecordNo`，多条规则预占共享该评估流水。
4. 支付终态 Outbox 只在交易状态 CAS 真正推进后写入；重复回调或主动查询不会重复写同一
   次状态推进事件。
5. 取消先取得生命周期行锁，再判断 `CONFIRMED/CANCELLED`，避免成功确认与失败补偿并发
   时先回滚 Redis、后发现数据库终态冲突。
6. MQ 入口 `applyPaymentStatus` 保持事务边界，锁内 `cancelLocked` 使用 `MANDATORY`，
   防止无事务执行 `SELECT ... FOR UPDATE`。
7. Redis Lua 返回 `null` 按失败处理；返回 `0` 仍表示 marker 已不存在或累计归零的幂等
   成功结果。

## 23. 阶段 4-C Key、数据库与切换门禁

新生命周期继续使用阶段 4-B 的精简 Key，不增加服务名和默认版本段：

```text
acquiring:{environment}:risk:runtime:merchant-limit:{limitType}:{ruleId}:{merchantId}:{currency}:{periodBucket}
acquiring:{environment}:risk:merchant-limit:{scopeDigest}:total
acquiring:{environment}:risk:merchant-limit:{scopeDigest}:reservation:{transactionDigest}
```

交易号只以 SHA-256 摘要进入 Redis Key；服务间 DTO 和数据库生命周期记录不传递物理
Redis Key，而是使用稳定业务元数据在风险服务内重建投影。

数据库迁移草案：

```text
service-admin/src/main/resources/sql/risk-merchant-limit-reservation-migration.sql
```

该脚本未执行。部署必须先完成数据库备份、容量评估和审批，再执行迁移并发布应用。

| 配置 | 默认值 | 切换要求 |
| --- | --- | --- |
| `risk.evaluation.counter-mode` | `LEGACY` | `SHADOW` 完整覆盖最大周期后，显式确认才能切 `CLUSTER_SAFE` |
| `risk.evaluation.baseline-mode` | `LEGACY` | `SHADOW` 核对生命周期与 payment 基线后，显式确认才能切 `LIFECYCLE` |
| `reservation-event-consumer-enabled` | `true` | 发布前确认 RocketMQ Topic、Tag 和消费组 |
| `reservation-reconcile-enabled` | `true` | 发布前确认 payment 只读数据源、批量和扫描周期 |

## 24. 阶段 4-C 当前结论与剩余风险

1. 代码实现、JDK 17 编译、阶段 4-C 定向测试和完整 `mvn -Pdev clean test` 已通过。
2. 迁移 SQL 仅生成草案，未连接或修改任何数据库。
3. 数据库与 Redis 无法组成单一原子事务，异常中断依赖生命周期记录和对账任务最终收敛；
   仍需真实 MySQL、真实 Redis、进程崩溃和 Redis 超时故障演练。
4. 生产仍应保持 `counter-mode=LEGACY`、`baseline-mode=LEGACY`；未完成完整最大周期
   SHADOW 差异观察前不得切换。
5. 尚未完成 Redis Cluster failover、MQ 积压/重复投递、多实例对账抢占和容量压测。

## 25. 阶段 4-C 完整自动化回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -Pdev clean test
```

Reactor 结果为 23 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 54.557 秒。本次
`clean` 后生成 141 份 Surefire XML，共记录 657 个测试，失败 0、错误 0、跳过 15。
模块明细和跳过原因见 `10-redis-test-report.md`。

## 26. 阶段 4-D 主要变更

| 模块 | 变更 | 目的 |
| --- | --- | --- |
| `service-payment` | 注入 `PaymentRedisProperties`，通过 `businessKey` 构造两类支付锁 Key | 复用统一 `acquiring:{environment}` 环境前缀，不引入 service 或默认版本段 |
| `service-payment` | operation 和 merchant-order-flow 动态身份统一使用 SHA-256 摘要 | 物理 Key 不暴露商户号、订单号、交易号或幂等键 |
| `service-payment` | Authorization/Payment、Capture、Refund、Void、Incremental Authorization 在准备服务返回后立即解锁 | Redis 租约不再覆盖外部渠道调用，避免渠道耗时超过 30 秒后锁自然失效 |
| `service-payment` | 准备锁释放异常记录告警并继续渠道调用，锁由 30 秒 TTL 自然恢复 | 避免本地准备已经提交后因 Redis compare-delete 故障永久停在渠道调用前 |
| `service-payment` | 无调用方的旧私有后续交易路径移除 Redis 长锁包装 | 防止旧路径未来误启用时重新把锁带入渠道 I/O |
| `service-payment` 测试 | 新增锁 Key、TTL、敏感值和渠道调用时锁状态断言 | 固化精简命名与准备阶段锁边界 |

没有新增依赖、Redisson、watchdog、续期线程或 SQL；没有执行数据库变更。

## 27. 阶段 4-D Key 与并发边界

```text
acquiring:{environment}:payment:lock:operation:{idempotencyDigest}
acquiring:{environment}:payment:lock:merchant-order-flow:{orderDigest}
```

1. `{idempotencyDigest}` 与 `{orderDigest}` 均为 64 位小写 SHA-256，不写入原始商户号、
   商户订单号、平台交易号或幂等键。
2. 30 秒 TTL 仅作为本地准备事务租约，不覆盖渠道 HTTP 调用和渠道结果事务。
3. 准备服务是独立 Spring 事务 Bean；生产调用中方法返回时本地事务已经提交或回滚，
   随后用现有 token compare-delete Lua 安全解锁。
4. 解锁异常只记录脱敏物理 Key、异常和 traceId，继续渠道调用；遗留锁最多保留到 30 秒
   TTL，不把已提交准备记录永久停在渠道调用前。
5. Redis 锁获取失败时仍先查询数据库幂等结果；无结果才返回繁忙。
6. 锁释放后，`transaction_idempotency.uk_scope_key (idempotency_scope, idempotency_key)`
   唯一约束、交易事实行锁和状态机继续防止重复渠道副作用及非法状态推进。

## 28. 阶段 4-D 定向验证

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -pl service-payment -am \
  -Dtest=PaymentTransactionServiceImplTests,PaymentTransactionConsistencyBaselineTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果为 12 个 Reactor 模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 1.949 秒；共执行
91 个测试，失败 0、错误 0、跳过 0。并发基线继续证明同商户同订单重复并发请求只创建
一笔支付并只调用一次渠道。

## 29. 阶段 4-D 当前结论与剩余风险

1. 裸 `transaction:operation:*`、`transaction:merchant-order-flow:*` 已退出支付锁实现。
2. 代码与单 JVM 自动化测试已证明渠道调用发生时准备锁不再处于持有状态。
3. 阶段 4-D 修改后的 23 模块全项目自动化回归无失败或错误。
4. 尚未完成真实 Redis 多实例竞争、准备事务超过 30 秒、持有者进程退出、Redis failover、
   网络分区和高并发容量测试。
5. Redis 仍只是削峰和减少冲突的辅助保护，不能替代数据库幂等唯一约束、行锁和状态机。

## 30. 阶段 4-D 完整全项目回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -Pdev clean test
```

Reactor 结果为 23 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 54.586 秒。本次
`clean` 后生成 141 份 Surefire XML，共记录 660 个测试，失败 0、错误 0、跳过 15。
其中 `service-payment` 执行 167 个测试，失败 0、错误 0、跳过 0。15 个跳过项均由真实
Redis、Redis Cluster、MySQL 或 MPGS Sandbox 条件开关未启用导致，明细见
`10-redis-test-report.md` 第 24 节。

本结果证明当前代码在 dev 自动化回归范围内未发现行为回归，但不替代真实 Redis 多实例
竞争、锁持有者崩溃、网络分区、Cluster failover、吞吐与容量验收，也不构成生产发布批准。

## 31. 阶段 4-E 主要变更

| 模块 | 变更 | 目的 |
| --- | --- | --- |
| `component-redis` | Serializer 工厂返回统一 `RedisSerializer<Object>`，读写均经过登记门禁 | 防止调用方绕过受控接口执行额外的类型化反序列化 |
| `component-redis` | 新写使用 `OBJECT_AND_NON_CONCRETE`，写前递归检查根值、容器成员和 DTO 字段 | 停止新 Value 依赖 broad `NON_FINAL`，拒绝未登记类型和循环对象图 |
| `component-redis` | 历史 `NON_FINAL` Serializer 只作为精确白名单 reader | 兼容存量 Value，不允许它继续承担新写 |
| `component-redis` | 移除 `com.scott.payment.*`、`java.util.*`、`java.time.*`、`java.math.*` | 只登记实际缓存 DTO、容器、时间和金额类型 |
| `service-openapi` 测试 | 使用两个真实商户缓存 DTO 验证双向兼容和嵌套类型拒绝 | 防止仅用测试替身证明兼容 |

明确登记的业务类型为 `MerchantRuntimeProfile` 和 `MerchantOpenApiAccessPolicy`；容器为
`ArrayList`、`LinkedHashMap`、`LinkedHashSet`；业务值包含 `String`、`Boolean`、
`Integer`、`Long`、`BigDecimal`、`LocalDateTime`。新增类型必须先登记并补兼容测试，
通用 List/Hash/Set/ZSet 包装器未删除，但写入未登记对象会明确失败。

## 32. 阶段 4-E 兼容与 Key 结论

| Value 形态 | 历史写 / v2 读 | v2 写 / 历史读 |
| --- | --- | --- |
| `MerchantRuntimeProfile` | 通过 | 通过 |
| `MerchantOpenApiAccessPolicy` + `LinkedHashSet<String>` | 通过 | 通过 |
| `List<Map<String,Object>>` + `Long/Integer/LocalDateTime` | 通过 | 通过 |
| 平台配置 `String` | 通过 | 通过 |

双向兼容成立，因此本阶段不创建 v2 namespace，不改名或删除现有 Redis Key，也不加入
service/default version 段。Key 继续采用：

```text
acquiring:{environment}:{domain}:{business}[:{businessKey}]
```

回滚时可以恢复上一版 Serializer；v2 新写 Value 仍可由上一版读取，不需要批量清理 Redis。

## 33. 阶段 4-E 当前结论与剩余风险

1. 代码实现、JDK 17 编译、TDD 用例、相关模块定向回归和完整全项目回归已通过。
2. 未执行数据库或 Redis 写操作，未删除历史 Key，未修改生产配置、依赖版本或缓存 TTL。
3. 尚未使用真实 Redis 样本库验证长期存量 Value；当前证据来自历史 Serializer 生成的
   代表性 wire format 和真实业务 DTO。
4. 新增缓存 DTO、容器实现、时间或数值类型时必须显式登记；否则写入会失败，这是安全
   边界而非自动兼容行为。
5. 完整回归中的 15 个跳过项仍需真实 Redis、Redis Cluster、MySQL 或 MPGS Sandbox
   环境，当前结果不构成生产发布批准。

## 34. 阶段 4-E 完整全项目回归

执行命令：

```bash
JAVA_HOME=/Users/scott/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home \
mvn -Pdev clean test
```

Reactor 结果为 23 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 51.339 秒。本次
`clean` 后生成 142 份 Surefire XML，共记录 671 个测试，失败 0、错误 0、跳过 15。
其中 `component-redis` 执行 66 个测试，`service-openapi` 执行 75 个测试；Serializer
边界和真实业务 DTO 兼容用例均包含在本次全量结果中。15 个跳过项均由真实 Redis、
Redis Cluster、MySQL 或 MPGS Sandbox 条件开关未启用导致，模块与原因明细见
`10-redis-test-report.md` 第 29 节。

本结果证明阶段 4-E 在 dev 自动化回归范围内未发现行为回归，但不替代真实环境的长期
存量 Value 读取、Redis 故障与容量验收，也不构成生产发布批准。

## 35. 阶段 5 主要变更

| 模块 | 变更 | 目的 |
| --- | --- | --- |
| `component-core` | 新增平台配置白名单、统一 TTL 抖动和跨组件 Key 解析契约 | 集中缓存准入、过期和精简 Key 语义 |
| `component-redis` | 提供三态 miss marker 存储及精简业务 Key 解析实现 | 区分明确无记录、无 marker 和 Redis 不可用 |
| `component-db` | 商户资料使用永久正缓存、30 秒 miss marker 和 64 许可回源舱壁；ISO 只读写环境隔离的单一永久 Key | 治理穿透、故障回源和跨环境字典 Key |
| `service-admin` | 平台公开配置使用永久缓存和可靠 Outbox 失效；国家、币种和区域币种变更只删除当前 ISO Key | 防止敏感配置进入缓存并保证字典变更收敛 |
| `service-risk`、`service-admin` | 删除未使用风险 Spring Cache 声明和无可靠失效的风控时间线缓存 | 避免死配置和陈旧风控审计数据 |

## 36. 阶段 5 Key 与失败策略

新增 Key 继续遵循已确认的精简格式，不加入 service 或默认版本段：

```text
acquiring:{environment}:{domain}:{business}[:{businessKey}]
```

| 用途 | Key | 失败策略 |
| --- | --- | --- |
| ISO 国家 | `acquiring:{environment}:iso:country` | 永久无 TTL；未命中或 Redis 不可用时回源数据库，空/异常时可使用内置 ISO 数据但不回写兜底值 |
| ISO 币种 | `acquiring:{environment}:iso:currency` | 同国家；管理变更时只精确删除当前环境 Key |
| 商户不存在 marker | `acquiring:{environment}:merchant:runtime-profile-miss:{merchantId}` | Redis 不可用返回 `UNAVAILABLE`，不写负缓存；回源饱和返回可重试 `F503` |

生产代码不再读取、写入或删除历史 `payment:iso:country:all` 和
`payment:iso:currency:all`。所有实例升级后，由运维在确认 Redis 集群、逻辑 DB 和环境归属
后逐个精确 `UNLINK`/`DEL`；禁止使用 `KEYS`、`FLUSHDB` 或模式批量删除。只有主库明确返回
商户无记录才写 miss marker；数据库异常和 Redis 读取异常都不得转换为“不存在”。

## 37. 阶段 5 验证结论

在 JDK 17 下执行 `component-core`、`component-db`、`component-redis` 和
`service-admin` 及其依赖模块回归，11 个 Reactor 模块全部成功，共 266 个测试，失败 0、
错误 0、外部 Redis 集成测试跳过 5 个。测试覆盖平台配置准入、TTL 抖动、三态 marker、
只对确认空结果写 marker、Redis 故障语义、回源舱壁、ISO 新旧读写和 Admin 双代失效。

## 38. 阶段 5 剩余风险

1. 未连接生产或共享测试 Redis，尚未观察 ISO 新旧 Key 命中比例和历史写停止条件。
2. 未在真实数据库连接池上验证 64 许可是否匹配各服务实例容量。
3. 未取得平台配置命中率、P95/P99 Value bytes 和数据库回源 QPS。
4. 5 个真实 Redis 集成用例仍因外部环境开关未启用而跳过。
5. 本阶段没有执行 SQL、删除历史 Key、修改生产 Redis/Nacos 或切换生产模式。

## 39. 阶段 6 高并发累计计数

阶段 6 把累计限额从仅有 Redis reservation 投影收敛为数据库生命周期与 Redis 计数投影
协作：

1. 数据库记录使用 `PREPARING/RESERVED/CONFIRMED/CANCELLED` 状态机和 CAS/行锁保护。
2. 同一 `transactionId` 的重复 reserve、confirm、cancel 保持幂等，终态不能被晚到事件覆盖。
3. 支付终态 Outbox 只有在支付状态真正推进时写入，风险消费者在事务内确认或取消。
4. 超时对账按支付数据库事实恢复 `PREPARING`、确认成功或回滚失败交易。
5. `LEGACY/SHADOW/CLUSTER_SAFE` 与基线模式均有显式切换门禁，生产默认保持历史决策。

同槽 Key 继续使用精简规范：

```text
acquiring:{environment}:risk:merchant-limit:{scopeDigest}:total
acquiring:{environment}:risk:merchant-limit:{scopeDigest}:reservation:{transactionDigest}
```

交易号只以 SHA-256 摘要进入 Redis Key。数据库迁移文件仍只是草案，本轮未执行 SQL。

## 40. 阶段 7 频率滑动窗口

频率限制新增单 ZSet 生产路径：

```text
acquiring:{environment}:risk:frequency-window:{scopeDigest}
```

Lua 原子执行窗口外成员清理、交易摘要 `ZADD NX`、`ZCARD`、容量保护和 `PEXPIRE`。
规则配置受最大窗口、最大阈值和最大成员数约束；重复交易不会重复计数；容量、连接或脚本
异常返回 `ERROR/REVIEW`，不静默 PASS。`SHADOW` 继续使用 legacy 决策，只记录两种窗口
差异；切换到 `SLIDING_WINDOW` 必须显式确认。

阶段 9 使用临时六节点 Redis 6 Cluster 重新执行生产 Lua，重复、容量和 `WRONGTYPE`
路径均通过，未发生 `CROSSSLOT`。

## 41. 阶段 8 锁、MQ 幂等与全局 ID

| 对象 | 修改结果 |
| --- | --- |
| MQ 去重 | 引入 `ACQUIRED/DUPLICATE/FALLBACK` 三态；Redis TIME 双桶同槽查重；消息键只保存 SHA-256 摘要；默认每桶 100000、TTL 最大 30 天 |
| MQ 消费者 | Admin、Merchant、Risk 对 `DUPLICATE` 跳过，对 `FALLBACK` 继续数据库唯一约束；只有 `ACQUIRED` 且业务失败时释放 Redis |
| 通用包装器 | String 因存量风控调用默认启用；Hash/List/Set/ZSet 默认不注册，避免未登记能力被直接使用 |
| 全局 ID | Key 改为 `acquiring:{environment}:global-id:state`；移除无必要 Hash Tag；状态 Key 与环境前缀精确匹配 |
| 全局 ID 恢复 | 新增 `restore-acknowledged` 和 `restore-floor-epoch-millis`，Lua 只在双重门禁通过后推进恢复时间下限 |

数据库最终幂等已确认包括 `sys_oper_log.idempotent_key`、
`risk_evaluation_record.risk_record_no` 以及支付幂等表唯一约束。Redis 故障不会取消这些
持久保护；全局 ID 则选择明确失败，禁止切换到 JVM 本地序列。

## 42. 阶段 9 本地验收变更

阶段 9 只增加显式开关的真实 Redis 集成测试和测试编排脚本，没有改变生产业务语义：

| 文件 | 用途 |
| --- | --- |
| `RedisLockIntegrationTests` | 持有者 token、租约到期和锁基础性能 |
| `RedisConnectionFailureIntegrationTests` | 连接拒绝下 MQ 与全局 ID 失败策略 |
| `RedisIdempotentIntegrationTests` | 增加 MQ 双桶 P95/P99 采样 |
| `RedisGlobalIdIntegrationTests` | 增加单线程发号 P95/P99 采样 |
| `DefaultRiskListRuntimeRepositoryClusterIntegrationTests` | 增加滑动窗口容量、真实 Lua 错误和 P95/P99 |
| `run-redis-stage9-tests.sh` | 一次性 Redis 6 容器、精确测试开关及容器停止后的连接拒绝演练 |

所有测试使用随机隔离前缀或精确可计算 Key，清理只删除测试 Key；未执行 `KEYS`、
`FLUSHDB`、SQL、共享 Redis 写入或生产 Nacos 修改。

## 43. 阶段 9 完整全项目回归

用户确认方案 A 后执行：

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 17)" mvn -Pdev clean test
```

Reactor 结果为 23 个模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 53.790 秒。本次
`clean` 后生成 154 份 Surefire XML，共记录 737 个测试，失败 0、错误 0、跳过 27，
实际执行 710 个。

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

其余 7 个聚合或骨架模块没有独立测试用例。27 个跳过项按证据状态分为：

| 测试组 | 跳过数 | 原因与补充证据 |
| --- | ---: | --- |
| `RedisConnectionFailureIntegrationTests` | 2 | 完整回归未开启故障开关；阶段 9 容器停止后已单独执行通过 |
| `RedisGlobalIdIntegrationTests` | 5 | 完整回归未开启真实 Redis 开关；阶段 9 单机 Redis 6 已单独执行通过 |
| `RedisIdempotentIntegrationTests` | 4 | 完整回归未开启真实 Redis 开关；阶段 9 单机 Redis 6 已单独执行通过 |
| `RedisLockIntegrationTests` | 3 | 完整回归未开启真实 Redis 开关；阶段 9 单机 Redis 6 已单独执行通过 |
| `DefaultRiskListRuntimeRepositoryClusterIntegrationTests` | 5 | 完整回归未开启 Cluster 开关；阶段 9 六节点 Redis 6 Cluster 已单独执行通过 |
| `MpgsApiClientLiveFlowTests` | 2 | 未设置 `MPGS_LIVE_TEST_ENABLED`，未连接 MPGS Sandbox |
| `MerchantOpenApiMpgsLiveFlowTests` | 5 | 未设置 OpenAPI live/risk-block 开关，未连接 MPGS Sandbox |
| `RiskRuntimeMapperMySqlLiveTests` | 1 | 未设置 `risk.mysql.live.enabled`，未连接真实 MySQL |

因此，27 个跳过项中 19 个已有阶段 9 隔离环境的独立通过证据；剩余 8 个外部 live
用例未执行。完整自动化回归结论不能替代 UAT/生产拓扑、MPGS Sandbox、真实 MySQL、
完整 shadow 周期、监控告警和容量准入证据。

## 44. 永久业务读模型补强

2026-07-31 根据业务复核，将可由数据库重建、但必须在交易高频读路径长期驻留的资料统一
调整为永久 Redis 读模型。Spring Cache 物理前缀不再插入 `cache` 层级，三个 Cache Name
固定为：

```text
acquiring:{environment}:merchant:info:{merchantId}
acquiring:{environment}:merchant:openapi:{merchantId}
acquiring:{environment}:config:public:{configKey}
```

三者均不设置 TTL。永久只表示 Redis 不主动过期，数据库仍是事实源；Admin 真实变更先创建
pending 门禁并在同一数据库事务写 Outbox，提交后精确删除缓存并按 token 释放门禁，失败
事件每 5 秒持续重试。读取端在缓存读取前后检查 pending，命中或 Redis 状态未知时丢弃缓存
结果并查询 MASTER，避免主从延迟把旧值重新写回。

平台公开配置复用现有 `merchant_security_cache_invalidation_outbox` 表以兼容已部署数据，
新事件号使用 `managed-cache-` 前缀。Admin 缓存监控只展示四个
`PlatformConfigCachePolicy` 登记的数据 Key，隐藏并拒绝查看/删除
`config:public:pending:*` 和同前缀未知 Key。

ISO 当前只使用以下两个永久 Key：

```text
acquiring:{environment}:iso:country
acquiring:{environment}:iso:currency
```

旧 `payment:iso:*:all` 仅在负向测试中用于证明生产代码不会读取或删除。升级全部实例后，
历史实物 Key 由运维核对环境并精确清理。

风控规则新增 `LEGACY`、`SHADOW`、`SNAPSHOT` 读取模式。`SNAPSHOT` 使用永久短 Key：

```text
acquiring:{environment}:risk:{white|black|aml}:{function}:{merchantId|GLOBAL}
acquiring:{environment}:risk:source:{merchantId}
acquiring:{environment}:risk:limit:{merchantId}:{currency}
acquiring:{environment}:risk:3ds:{merchantId}
acquiring:{environment}:risk:frequency:{merchantId}
```

精确名单使用带 `@meta` 的 Hash；风险名单自身的范围、来源、限额、3DS 和频率规则使用有界
JSON 快照。单快照最多 5000 行、序列化字符最多 5 MiB；Redis、generation 或容量异常时回源
MASTER，不能默认 PASS。公共 BIN 发卡国家数据不参与通用全量快照，改为以下 generation 隔离的
按 BIN Cache-Aside Key：

```text
acquiring:{environment}:risk:runtime-rule:{generation}:card-bin:issuer-country:{binDigest}
```

该 Key 只保存结构化命中或未命中结果，不保存原始 BIN；命中默认 TTL 为 300 秒，未命中默认
TTL 为 60 秒。Redis、generation 或缓存内容不可用时执行数据库区间点查，数据库异常不得固化为
未命中。兼容 generation Key 和 30 秒
`merchant:runtime-profile-miss:{merchantId}` marker 暂不改名，前者受滚动迁移约束，后者是
有 TTL 的恢复性负缓存，不属于永久业务 Key。

名单 `match_value_hash` 目前不能直接切换为 HMAC。历史普通摘要无法反推原始值；必须增加
算法标识和新摘要列，通过受控重导入或编辑再生成、双摘要读取和 `SHADOW` 零差异完成迁移。
在此之前不得宣称 HMAC 已落地，也不得直接切换导致历史 AML、黑名单或白名单失配。

本轮在 JDK 17 下完成受影响模块编译和 18 个测试类的定向验证。编译覆盖 13 个 Reactor
模块并全部 `SUCCESS`；定向测试共 104 个，失败 0、错误 0、跳过 0。完整命令和分模块结果
见 `10-redis-test-report.md` 第 43 节。本结果是永久读模型补强的基础验证，不替代修改后的
全项目完整回归或 UAT 等价拓扑验收。

## 45. 永久业务读模型补强完整回归

用户通过完整回归门禁后，使用 Temurin 17 执行 `mvn -Pdev clean test`。23 个 Reactor
模块全部 `SUCCESS`，`BUILD SUCCESS`，总耗时 53.921 秒。全新生成 159 份 Surefire
测试套件，共 760 个测试，失败 0、错误 0、跳过 27，实际执行 733 个。

27 个跳过项中，19 个是需要显式启动隔离 Redis、连接拒绝或六节点 Cluster 的测试，已有
阶段 9 独立证据；剩余 8 个依赖 MPGS Sandbox 或真实 MySQL。本轮完整回归证明当前本地
自动化范围内未发现行为回归，但不替代 UAT 等价拓扑、完整 `SHADOW` 周期、真实告警闭环、
生产量级容量或 HMAC 双摘要迁移验收。详细命令和跳过项见
`10-redis-test-report.md` 第 44 节。

## 46. 商户事实源与跨系统缓存一致性补强

2026-08-01 将管理系统、商户门户和 OpenAPI 的商户资料读取统一到
`base_merchant_info` 与同一永久 Key：

```text
acquiring:{environment}:merchant:info:{merchantId}
```

本批保留 `MerchantRuntimeProfile` Java 类型名，并补齐主体展示、经营地址、联系人和审计时间等
商户主表字段。项目尚未上线，本次不保留历史 Value 双读、结构修订号或迁移兼容分支；部署前按
环境精确清理旧开发测试 Key，不扫描 Keyspace、不新增 service 或版本段。

Admin 商户新增、编辑、启停、密钥初始化和轮换，以及 Merchant Portal 资料修改和密钥轮换，
统一使用 `ManagedCacheInvalidationCoordinator`：在主库业务事务中获取 pending、写入既有
`merchant_security_cache_invalidation_outbox`，提交后由共享 relay 立即精确删除，失败保持
`INIT/FAILED` 并由 Admin 5 秒补偿任务重试。Admin 普通列表/详情/密钥概览显式路由 SLAVE，
全部事实和密钥变更显式路由 MASTER；Merchant Portal 修改与轮换同样固定 MASTER。

Merchant Portal 新增 `GET/PUT /merchant/info`。商户号只来自认证上下文；商户可维护账单描述、
简称、区域、城市、详细地址、邮编、联系人和时区，不能修改商户号、主体名称、状态、MCC、
国家、结算币种或风险等级。完整资料使用同一共享缓存；联系人和详细地址属于受保护字段，禁止
进入操作日志正文、普通业务日志或无关接口，Redis 访问必须受环境隔离和 ACL 保护。

OpenAPI 的启用商户校验改为复用共享画像。JWT 验签密钥、平台请求体公私钥、商户响应公钥、
商户客户端材料和服务端诊断材料均显式读取 MASTER，避免密钥轮换后的复制延迟。JWT Secret、
RSA 私钥、AES Key 和其他可直接使用的密钥明文没有加入 Redis；`merchant:keyMeta` 只保存
密钥 ID、版本、算法、更新时间和组合 revision，真实密钥仅从 MASTER 加载到 JVM 短时缓存。

Merchant Portal 新增真实商户资料页和 API 模块，沿用后端动态菜单
`merchant/info/index`，按 `merchant:info:edit` 控制保存入口；保存后重新查询后端，验证提交后
共享画像已经刷新。前端不把联系人或密钥材料写入 localStorage、sessionStorage 或控制台。

本节只记录实现结果。当前新鲜定向测试和前端验证见 `10-redis-test-report.md` 第 45 节；
修改后的全项目完整回归仍受第二次确认门禁约束，尚不能引用第 45 节以前的历史全量结果代替。

## 47. 商户永久缓存与 service-data 异步职责补强

2026-08-01 在商户完整资料缓存基础上新增并接通两个永久读模型：

```text
acquiring:{environment}:merchant:keyMeta:{merchantId}
acquiring:{environment}:merchant:route:{merchantId}
```

`merchant:keyMeta` 只保存当前密钥记录 ID、版本、算法、位数、更新时间和组合 revision。
OpenAPI 每次读取该 revision，只有 JVM 内同 revision 的短时条目可复用；版本变化、TTL 到期或
容量淘汰后从 MASTER 重新加载真实材料。JWT Secret、RSA 私钥、公钥正文、AES Key 和渠道凭据
均不进入 Redis。

`merchant:route` 聚合商户绑定、渠道/MID 状态、支付能力、币种范围、优先级和超时配置，支付
选路先读取永久快照。渠道密码、API Key、证书、私钥、完整令牌和 metadata 明文仍固定从
MASTER 加载到 JVM 短时缓存。Admin 修改商户、渠道、MID、绑定或能力配置时，先登记 pending
与事务 Outbox，提交后精确删除对应商户路由快照。

商户资料新增、编辑、启停和 Merchant Portal 自助更新已统一为以下顺序：

```text
创建 pending 并写失效 Outbox
-> 在 MASTER 修改 base_merchant_info
-> 提交事务
-> 可靠删除旧永久缓存和 miss marker
-> 事务感知 CacheManager 写入当前事务读到的新完整资料
```

该顺序保证提交后的新缓存写入失败时，旧永久 Value 仍会被 Outbox 删除；pending 存在或状态
不可确认时，Admin、Merchant Portal、OpenAPI 和支付服务都绕过缓存读取 MASTER。项目尚未上线，
不保留历史 Value 双读、结构版本号或旧 Cache Name 兼容代码。

新增 `service-data` 后，异步职责固定为：

| 数据类型 | 生产端 | 消费/执行端 | 最终事实与幂等 |
| --- | --- | --- | --- |
| Admin、Merchant Portal 操作日志 | 对应管理服务发送脱敏 MQ | `service-data` | `sys_oper_log.idempotent_key` 唯一约束 |
| 风控评估审计 | `service-risk` 发送最小审计消息 | `service-data` | `risk_evaluation_record.risk_record_no` 唯一约束 |
| OpenAPI 安全拦截审计 | `service-openapi` 发送脱敏安全事件 | `service-data` | `security_intercept_event.event_no` 唯一约束 |
| 商户通知 HTTP 投递 | `service-payment` 事务 Outbox 发布终态事件 | `service-data` | 通知任务状态、版本 CAS 和固定 `notifyId` |
| 到期通知补偿 | `service-job` 定时扫描并发送 HMAC 内部请求 | `service-data` | 同一通知任务状态与版本 CAS |

安全拦截消息不携带 Authorization、Cookie、请求体、密钥、公私钥、卡数据或可直接使用的 Token；
MQ 发送失败只记录脱敏告警，不改变原安全拦截决定。消费失败时只有本次确实获得的短 TTL Redis
去重声明可以释放，RocketMQ 随后重试；数据库唯一键始终是最终幂等依据。

商户通知任务、尝试日志、回调 URL 快照、交易主单、状态、金额、渠道恢复事实、风险预占和
交易 Outbox 继续同步写 MySQL。Redis 不保存 MQ 原文、回调 URL 或待投递通知，不能作为数据库
或 RocketMQ 的“双保险”备份。

`service-data` 日志配置以 `service-openapi/src/main/resources/log-config/logback-spring.xml`
为基准，控制台/文件 Appender、滚动策略、异步队列、时区和 traceId 格式保持一致，仅默认
`applicationName` 为 `service-data`。

本节只记录代码与配置变更，不提前声明本轮完整回归结果；新鲜验证证据在
`10-redis-test-report.md` 后续章节单独记录。
