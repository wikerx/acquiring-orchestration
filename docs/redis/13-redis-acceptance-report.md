# Redis 专项治理验收报告

## 1. 验收结论

`REDIS-GOVERNANCE-001` 的阶段 0～8 代码治理、阶段 9 本地 Redis/Cluster 验证和阶段 10
生产就绪补强已完成历史回归。在此基础上，商户资料、OpenAPI IP 策略、平台公开配置、
ISO 字典和风控规则已按永久 Redis 读模型方案补强；数据库继续作为事实源，管理端变更通过
pending、事务 Outbox、精确失效和持续重试保证收敛。

当前结论为：**代码级有条件通过，本轮补强已完成完整回归；仍不批准生产 Redis 模式切换
或生产发布准入**。

剩余阻断项是缺少真实 MySQL/Redis 拓扑、8 个外部 live 用例、完整 shadow 周期、告警通知
闭环、故障切换和业务容量证据。

## 2. 交付物

| 范围 | 文件 |
| --- | --- |
| 基线与扫描 | `01-redis-version-baseline.md` ～ `04-redis-risk-assessment.md` |
| 目标与目录 | `05-redis-target-design.md` ～ `08-redis-change-plan.md` |
| 实施与测试 | `09-redis-change-report.md`、`10-redis-test-report.md` |
| 性能与故障 | `11-redis-performance-report.md`、`12-redis-failure-drill-report.md` |
| 最终验收 | `13-redis-acceptance-report.md` |

## 3. 验收矩阵

| 验收项 | 状态 | 证据或缺口 |
| --- | --- | --- |
| 环境 Key 隔离 | 通过 | `acquiring:{environment}` 启动门禁 5 个测试通过 |
| 精简 Key 规范 | 有条件通过 | 新 Key 不含默认 service/version；两处风控代际兼容 Key 暂保留 |
| Spring Cache 准入 | 代码通过 | `merchant:info`、`merchant:openapi`、`config:public` 为永久读模型；交易详情和风控时间线缓存已退役 |
| TTL/null/jitter | 代码通过 | 三个永久 Cache 固定零 TTL；30 秒 miss marker 和其他临时状态保留正 TTL 与容量边界 |
| Serializer | 通过 | 新写受精确类型门禁，历史只读兼容；真实业务 DTO 双向测试通过 |
| 安全缓存失效 | 代码通过 | 商户、OpenAPI IP 和平台公开配置均使用 Outbox/pending/Master 回源；真实积压告警待验 |
| 商户密钥版本一致性 | 代码通过 | 同商户号删除重建、重新配置和轮换均失效 `merchant:keyMeta`，并清除当前实例旧敏感材料；密钥正文不进入 Redis |
| 风控规则缓存 | 代码通过 | 精确名单 Hash 及范围/限额/3DS/频率有界快照已实现；公共 BIN 国家 generation 点查缓存已完成 dev 性能验收；UAT `SHADOW` 差异和容量待验 |
| 累计限额 | 代码通过 | 生命周期、补偿、对账和 Cluster Lua 通过；最大周期 shadow 待验 |
| 频率滑动窗口 | 代码通过 | 单 ZSet、容量、重复、`WRONGTYPE` 和 Cluster 通过；生产切换待 shadow |
| MQ 幂等 | 通过 | 三态、双桶、容量和连接拒绝通过；数据库唯一约束继续兜底 |
| `service-data` 异步职责 | 代码通过 | 操作日志、风控/安全审计消费和商户通知共 24 个模块测试通过；数据库唯一键与状态 CAS 最终兜底 |
| 支付锁 | 代码通过 | 持有者与租约通过；多实例崩溃和真实 failover 待验 |
| 全局 ID | 代码通过 | Key/恢复门禁、100000 并发唯一性和连接拒绝通过；HA 恢复待验 |
| 基础性能 | 本地通过 | 四条代码路径有 P95/P99，另有有界 SET/GET；无生产流量、网络和数据库证据 |
| Redis 监控 | 代码/规则通过 | Actuator、业务指标、Prometheus 规则和 runbook 已实现；UAT 规则加载、告警通知与恢复待验 |
| Java 注释规范 | 通过 | 本轮复验 1434 个 Java 文件，剩余文件和缺口均为 0 |
| 本轮受影响模块编译 | 通过 | JDK 17；13 个 Reactor 模块全部 `SUCCESS` |
| 本轮永久读模型定向测试 | 通过 | 18 个测试类、104 个测试，失败 0、错误 0、跳过 0 |
| 历史阶段 10 完整回归 | 通过 | 当时 23 个 Reactor 模块全部成功；744 个测试失败 0、错误 0、跳过 27 |
| 本轮完整全项目回归 | 通过 | JDK 17；24 个 Reactor 模块全部成功；814 个测试失败 0、错误 0、跳过 27 |

## 4. Key 规范结论

新增和本轮整改 Key 继续使用：

```text
acquiring:{environment}:{domain}:{business}[:{businessKey}]
```

示例：

```text
acquiring:dev:merchant:info:{merchantId}
acquiring:dev:merchant:openapi:{merchantId}
acquiring:dev:config:public:{configKey}
acquiring:dev:iso:currency
acquiring:dev:iso:country
acquiring:dev:risk:black:card:{merchantId}
acquiring:dev:risk:limit:{merchantId}:{currency}
acquiring:dev:risk:frequency-window:{scopeDigest}
acquiring:dev:global-id:state
```

没有加入默认 `{service}` 或 `v{version}`。仅以下两个存量代际路径暂时保留旧 service/version
结构：

1. `RedisCacheGenerationStore.versionedKey(...)`
2. `DefaultRiskListRuntimeRepository.currentRuleCacheKey(...)`

它们共同承担风控规则 generation 发布和滚动版本一致性。直接改名会造成新旧实例读取不同
代际；当前保留属于兼容边界，不是新 Key 规范示例。

## 5. Generation Key 迁移门禁

目标精简 Key 为：

```text
acquiring:{environment}:cache:generation:risk-runtime-rule:current
acquiring:{environment}:cache:generation:risk-runtime-rule:publication
acquiring:{environment}:risk:runtime-rule:{generation}:{segments...}
```

迁移模式固定为：

```text
LEGACY_ONLY
-> DUAL_LEGACY_FIRST
-> DUAL_COMPACT_FIRST
-> COMPACT_WRITE_LEGACY_READ
-> COMPACT_ONLY
```

只有全部实例均能识别新旧发布门禁、任一门禁或 generation 不一致都回源主库后，才允许
开始双写。双写观察必须至少覆盖一个最大缓存 TTL 和完整滚动发布窗口；停止旧写后还要覆盖
一个最大缓存 TTL，且精简读取错误、存量回退、generation 差异和双写失败均为 0。

迁移回滚以数据库已发布规则版本为事实，不能用单侧 Redis generation 覆盖另一侧。派生
规则缓存和发布门禁按 TTL 自然过期；持久 current generation Key 不自然过期且继续保留，
最终退役需要单独审批和精确备份，不允许使用 `KEYS`、`FLUSHDB` 或前缀批量删除。完整契约
见 `05-redis-target-design.md` 第 10.1 节。

## 6. 本地验证汇总

| 验证 | 结果 |
| --- | --- |
| 单机 Redis 6 集成 | 12 个测试，失败 0、错误 0 |
| Redis 连接拒绝 | 2 个测试，失败 0、错误 0 |
| 六节点 Redis 6 Cluster | 5 个测试，失败 0、错误 0，无 `CROSSSLOT` |
| 全局 ID | 连续 10000 和 20 线程共 100000 个编号均无重复 |
| 阶段 10 Redis/风控指标 | 42 个测试，失败 0、错误 0、跳过 0；包含 Prometheus 导出名和敏感标签绕过门禁 |
| 阶段 10 Admin Outbox | 12 个测试，失败 0、错误 0、跳过 0；包含 Prometheus 导出名 |
| 阶段 10 风控仓储与 shadow | 30 个测试，失败 0、错误 0、跳过 0 |
| 阶段 10 隔离演练 | LFU 淘汰 1784 个 Key、热/大 Key 检出、无持久化重启数据丢失、Cluster 副本提升后可读 |
| Java 注释 | `checked_java_files=1434`、`remaining_files=0`、`remaining_hits=0` |
| 本轮受影响模块编译 | JDK 17，13 个 Reactor 模块全部 `SUCCESS`，`BUILD SUCCESS` |
| 本轮永久读模型定向测试 | 104 个测试，失败 0、错误 0、跳过 0 |
| 本轮完整全项目回归 | 814 个测试，失败 0、错误 0、跳过 27；实际执行 787 个，24 个 Reactor 模块全部 `SUCCESS` |
| 本轮全项目打包 | 24 个 Reactor 模块全部 `SUCCESS`；`service-data` 等所有服务 JAR 均成功重打包 |
| 日志规范 | 1434 个 Java 文件，敏感日志、必需事件和 trace 规则均 0 命中；Data 与 OpenAPI Logback 仅默认应用名不同 |
| Diff 格式 | `git diff --check` 通过 |
| YAML/监控规则 | Nacos 与 Prometheus YAML 解析通过；Registry 导出名通过，`promtool` 不可用，UAT 规则加载待验 |
| 阶段 9 历史完整回归 | 737 个测试，失败 0、错误 0、跳过 27；实际执行 710 个 |
| 阶段 10 历史完整回归 | 744 个测试，失败 0、错误 0、跳过 27；实际执行 717 个，23 个 Reactor 模块全部 `SUCCESS` |

性能数字和故障细节分别见 `11-redis-performance-report.md` 与
`12-redis-failure-drill-report.md`。

## 7. 生产准入阻断项

在下列证据完成并经 Owner 审核前，不得切换生产 `CLUSTER_SAFE`、
`SLIDING_WINDOW` 或执行全局 ID 恢复：

1. 在 UAT 等价拓扑完成真实 MySQL、Redis Cluster/Sentinel、RocketMQ 和多实例回归。
2. 累计限额覆盖最大业务周期的 shadow 差异为 0，频率窗口差异由业务确认。
3. 完成 failover、replica lag、超时、淘汰、重启、Outbox 积压和数据库回源过载演练。
4. 在 UAT 加载并采集 Redis 连接、命令延迟、hit/miss、回源、Key 基数、内存、锁、Lua
   和 fallback 指标，完成受控告警触发、通知、确认和恢复闭环。
5. 使用脱敏生产量级数据完成目标并发、热 Key、大 Key和长稳压测。
6. 完成名单普通摘要向 HMAC 的受控双摘要迁移；在原始值不可恢复的历史数据完成重导入、
   回填和零差异证据前，不得直接切换算法。

阶段 9 回归中的 27 个跳过项有 19 个已由隔离 Redis/Cluster/连接拒绝脚本验证；剩余 8 个
依赖 MPGS Sandbox 或真实 MySQL。仓库已提供显式确认的外部测试入口，但尚未执行，应纳入
UAT 外部联调证据，不得解释为已通过。

## 8. 回滚与数据安全

1. 生产模式默认保持 `LEGACY`；切换失败只回滚本阶段配置，不删除 Redis 数据。
2. 历史 ISO Key 已停止代码读写，所有实例升级后只允许核对环境并精确清理；风控
   generation 兼容 Key 按迁移门禁停止写入或等待有限期派生 Key 自然过期。
3. 不使用 `KEYS`、`FLUSHDB` 或批量删除作为回滚手段。
4. 数据库继续作为交易、资金、累计限额生命周期、操作日志和风险审计的事实源。
5. 全局 ID 状态禁止通过 Admin 页面删除；恢复必须核对历史最大编号并完成双人审批。

## 9. 本轮最终复验

2026-08-01 使用 Temurin 17 完成以下最终验证：

| 验证 | 结果 |
| --- | --- |
| 商户密钥重建与本地敏感缓存 | 4 个测试类、11 个测试全部通过 |
| `service-data` | 24 个测试全部通过；消费重试、重复消费、Redis 降级、数据库最终幂等、通知 CAS 和中断恢复均有覆盖 |
| 受影响模块 | 20 个 Reactor 模块全部 `SUCCESS` |
| `mvn -Pdev clean test` | 24 个 Reactor 模块、814 个测试，失败 0、错误 0、跳过 27 |
| `mvn -Pdev package` | 24 个 Reactor 模块全部 `SUCCESS`，所有服务完成打包 |
| 静态门禁 | Java 注释、敏感日志、必需事件、trace、XML 和 Diff 格式检查全部通过 |

本轮代码级验收通过，但生产准入状态仍保持“有条件通过”。真实 Redis/MySQL/RocketMQ、
多实例和 MPGS Sandbox 证据必须在受控 UAT 环境完成，不能由本地自动化结果替代。

## 10. Redis 6.2.23 Cluster 接入验收补充

2026-08-01 在 `feat_scott_redisMQupdate` 分支完成最终复验。开发环境的 Redis Cluster 配置
和代码改造已完成：Spring Data Redis/Lettuce 与 Redisson 共用 7001-7006 六节点拓扑，固定
使用 DB 0；Redisson 使用 `useClusterServers()` 和 MASTER 读取；支付业务锁统一通过
`DistributedLockService`，解锁校验当前线程持有者，并同时支持有界固定租约和 Watchdog。

| 验收项 | 最新结果 |
| --- | --- |
| 本地 Redis | Redis 6.2.23，3 Master + 3 Replica，6 个节点，16384 槽完整，失败槽 0 |
| Nacos | dev Namespace 实时回读 14 个 DataId；`redis-dev.yaml` 为 6 节点 Cluster 配置并含中文参数和环境变量注释 |
| 配置残留 | 无单机 host/port、7007、非 0 DB、`RedisStandaloneConfiguration` 或 `useSingleServer()` 生产残留 |
| 分布式锁 | Redisson Cluster 统一实现；跨客户端互斥、可重入、固定租约和 Watchdog 真实 Cluster 测试通过 |
| Lua | 只保留原子业务语义；多 Key 使用同一 Hash Tag，真实 Cluster 测试无 `CROSSSLOT` |
| 全项目回归 | 24 个 Reactor 模块全部成功；801 个测试，失败 0、错误 0、跳过 27 |
| Cluster 专项 | stage9 15 个真实 Cluster 用例和 2 个连接拒绝用例通过；风控 2 个 Cluster 用例通过 |
| 服务启动 | `service-payment`、`service-checkout`、`service-job`、`service-payout` 启动成功且无 Redis 异常 |

因此，本地 dev 范围的 `REDIS-CLUSTER-001` 配置接入、统一锁治理和代码兼容性改造结论为
通过。该结论不代表 UAT 或生产就绪：各环境必须使用独立 Namespace、Key 前缀、Secret 和
实际节点拓扑，并继续完成 ACL/TLS、真实 RocketMQ、多实例、容量、replica lag 和受控
failover 验收。共享开发集群故障切换本轮未执行，因为该破坏性演练尚未获得单独确认。
