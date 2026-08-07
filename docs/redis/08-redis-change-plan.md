# Redis 分阶段整改计划

## 1. 执行边界

本文件是阶段 1 的建议计划，不是实施记录。本轮停在文档评审，禁止修改生产代码、
配置、依赖、SQL、Redis 部署、支付链路或风控链路。

整改顺序固定为：

```text
基础设施
-> P0 风险
-> P1 风险
-> 低风险查询缓存
-> 高并发计数
-> 频率限制
-> 锁与幂等
-> 监控、压测和故障演练
```

不得因为某个低风险缓存容易修改而绕过 P0/P1 设计门禁。

## 2. 阶段总览

| 阶段 | 目标 | 主要交付 | 进入门禁 | 完成门禁 |
| --- | --- | --- | --- | --- |
| 0 | 技术基线 | `01` | 当前仓库可扫描 | 依赖、客户端、拓扑证据边界明确 |
| 1 | 全量扫描与目标设计 | `02`~`08` | 用户批准只扫描 | 33 项问题、Catalog、目标和计划完成并暂停 |
| 2 | 统一基础设施 | Key/TTL/Serializer/Failure/Metric 设计与实现 | 阶段 1 书面审核 | 兼容测试通过，不改业务语义 |
| 3 | P0 治理 | 安全失效、风控失效、主库基线、防重放门禁 | P0 设计/回滚/Owner 确认 | 四个 P0 独立验收 |
| 4 | P1 治理（代码完成） | Cluster、补偿、Admin、锁、交易缓存、配置门禁 | P0 完成 | 代码与自动化通过；生产灰度仍受阶段 9 门禁 |
| 5 | 低风险查询缓存（代码完成） | 商户/配置/ISO/时间线收敛 | P0/P1 完成 | 本地验证通过；真实命中收益与数据库回源容量待生产前验收 |
| 6 | 高并发累计计数（代码完成） | 累计限额 v2 | 主库事实和状态机已落地 | 状态机、补偿和 Cluster 脚本通过；完整周期 shadow 待环境观察 |
| 7 | 频率限制（代码完成） | ZSet 滑动窗口 v2 | 规则语义和容量确认 | 边界、重复、容量和真实 Cluster Lua 通过；切换前仍需 shadow 观察 |
| 8 | 锁与幂等（代码完成） | 锁租约、MQ 去重、未用生成器处置 | 支付/渠道副作用分析完成 | 本地真实 Redis 与数据库约束复核通过；生产 HA/多实例待验收 |
| 9 | 监控与验收（本地完成） | 基础性能、故障演练、完整回归、最终报告 | 前述代码阶段完成 | `09`~`13` 和完整自动化回归已完成；生产准入因外部证据缺口暂不批准 |
| 10 | 生产就绪补强（本地完成） | 业务指标、Prometheus 规则、外部/UAT 入口、隔离故障演练和 generation 迁移契约 | 阶段 9 本地结论明确 | 定向验证、隔离演练和本批完整回归通过；UAT/生产证据仍阻断发布 |

## 3. 阶段 2：统一基础设施

建议任务：

1. 建立 Cache/Key Registry，编码对应 `07` Catalog。
2. 建立强校验 Key Builder 和专用 Cluster co-slot Builder。
3. 统一明确 DTO 的 JSON Serializer，设计 v1/v2 兼容迁移，移除 broad Default Typing。
4. 建立可配置 TTL、null TTL、jitter 和最大容量策略。
5. 区分普通缓存、安全缓存、风控、锁、幂等和全局 ID 的 Failure Policy。
6. Lua 独立资源化、命名、版本化并统一执行指标。
7. 提供提交后精确失效、持久重试和审计基础能力。

验收：

* 现有 Key 读写行为保持兼容，默认不开启业务迁移。
* 非 dev 前缀缺失、非法片段和未经授权 Hash Tag 启动/构建失败。
* Serializer 兼容样本覆盖旧 Value；不兼容时走独立新 Key，不原地破坏。
* jitter 分布、TTL 边界、null/异常区分和指标单元测试通过。

回滚：所有新能力有配置开关，旧 Key 和旧读路径在观察期内保留；禁止批量删除。

## 4. 阶段 3：P0 治理

### 4.1 RG-P0-01 安全缓存可靠失效

* 把普通 GET 降级与安全 EVICT 失败策略拆开。
* 商户启停、密钥状态、IP 白名单写事务提交后产生可靠失效记录。
* 定义同步失败、异步重试耗尽时的 Fail Closed/阻断发布策略。
* 验证 DB 回滚不失效、DB 提交且 Redis 失败可最终收敛。

回滚：保留 DB 主库直接校验开关，不回滚到“吞 EVICT 等 TTL”。

### 4.2 RG-P0-02 风控规则版本与失效

* 盘点 Admin 所有 create/update/delete/status/import/release 操作影响的 Key。
* 优先选择规则版本切换；无法版本化的查询建立精确索引失效。
* 新增黑名单、删除白名单、启用拒绝规则作为安全收紧用例。

回滚：可切换为风控主库直接查询或 REVIEW，不能回滚为默认 PASS。

### 4.3 RG-P0-03 累计限额主库基线

* 明确可计入交易状态、时间边界、币种和排除当前 transaction 的 SQL 语义。
* 使用主库或可证明一致的事实快照，采集副本 lag 只作观测。
* 建立周期对账和 Redis 重建工具，金额全程整数最小单位。

回滚：累计规则进入 REVIEW/人工或按业务批准的 Fail Closed，不使用旧从库基线。

### 4.4 RG-P0-04 防重放装配门禁

* required=true 且 Redis Bean 缺失时启动失败。
* Redis 连接失败和 Bean 缺失使用同一策略。
* 增加生产 profile 上下文、连接拒绝和重复 jti 测试。

回滚：只有非生产、显式 `required=false` 才允许仅 JWT 校验。

## 5. 阶段 4：P1 治理

| 工作包 | 对应问题 | 实施重点 | 验收 |
| --- | --- | --- | --- |
| Cluster Lua 同槽治理 | P1-01、P1-02 | 精简同槽 Key；`LEGACY/SHADOW/CLUSTER_SAFE` 迁移；频率先保守兼容再迁移单 ZSet | 真实 Cluster 无 CROSSSLOT，完整周期差异为 0，failover/重试幂等 |
| 累计状态机 | P1-03 | reserve-confirm-cancel、Outbox/MQ、超时补偿、对账 | 支付成功/失败/超时/重复回调均收敛 |
| Admin 监控收敛 | P1-04、P1-05 | SCAN、namespace 白名单、禁止敏感 Value 和任意 delete | 大 Keyspace 不阻塞；RBAC/审计/保护 Key 测试 |
| Serializer v2 | P1-06 | 类型白名单/明确 DTO，双读迁移 | 旧数据兼容、污染 Value 拒绝、无任意类型实例化 |
| 支付锁 | P1-07 | 阶段 4-D 已完成精简脱敏 Key 和准备事务解锁；保留 DB 最终幂等 | 定向及完整自动化回归已通过；多实例、锁过期、持有者崩溃、Redis failover 和容量待验收 |
| 交易详情缓存移除 | P1-09 | 去掉实时状态缓存及相关 evict | 查询正确性和数据库容量压测 |
| 环境门禁 | P1-10 | prod/test/uat 显式前缀、拓扑、ACL、Server 版本 | 部署检查失败即阻断 |

P1-08 的频率滑动窗口在阶段 7 完成；阶段 4 先保证 Cluster 下不会静默失效。

阶段 4-A 已完成 `P1-04`、`P1-05`、`P1-09` 的代码治理，并完成 `P1-10` 的
test/uat/prod 前缀代码门禁。`P1-06` 本批只完成差距审计，不切换历史 Value wire format；
真实拓扑、ACL、Server 版本和交易详情数据库容量压测仍属于后续验收。

阶段 4-B 已完成 `P1-01`、`P1-02` 的同槽代码路径、迁移模式、切换门禁和真实 Redis 6
Cluster `CROSSSLOT` 测试。生产默认仍为 `LEGACY`，必须先让 `SHADOW` 覆盖一个完整最大
业务周期，确认差异和新路径异常均在预算内，才能设置切换确认并启用 `CLUSTER_SAFE`。

阶段 4-C 已完成 `P1-03` 的持久化 reserve-confirm-cancel、支付终态 Outbox、失败补偿、
超时扫描和数据库对账代码，并通过定向测试及全项目自动化回归。迁移 SQL 尚未执行，
真实 MySQL、Redis Cluster failover、MQ 积压/重复投递、多实例对账竞争和容量验收仍未
完成。`P1-08` 仍未完成；生产继续保持 `counter-mode=LEGACY` 和
`baseline-mode=LEGACY`。

阶段 4-D 已完成 `P1-07` 的代码治理。operation 与 merchant-order-flow 两类锁使用
`acquiring:{environment}:payment:lock:{purpose}:{sha256}`，不包含 service 或默认版本段，
也不暴露商户号、订单号或幂等键。Redis 锁只保护独立本地准备事务，准备服务返回后立即
释放，再发起渠道调用；数据库幂等唯一约束和交易状态机继续承担最终正确性。定向自动化
验证和 23 模块完整全项目回归均已完成；真实多实例、Redis failover、进程崩溃和容量验收
仍属于阶段 9。

阶段 4-E 已完成 `P1-06` 的代码实现和定向回归。Serializer v2 新写路径只接受明确登记
的业务 DTO、容器和标量，使用 `OBJECT_AND_NON_CONCRETE`；历史 `NON_FINAL` 仅保留为
精确白名单只读兼容路径。真实 DTO、平台配置 String 和风控时间轴均已完成双向兼容验证，
因此保留现有 Key namespace，不加入 service 或默认版本段，也不删除历史 Value。完整
全项目回归已通过第二道确认门禁并完成：23 个 Reactor 模块全部 `SUCCESS`，671 个测试
失败 0、错误 0、跳过 15。

## 6. 阶段 5：低风险查询缓存

优先顺序：

1. `platform:config` 仅保留非敏感配置白名单。
2. ISO 字典迁移到统一精简 namespace，增加变更失效和 jitter。
3. `merchant:runtime-profile` 增加独立短 null TTL 和回源舱壁。
4. `risk:evaluation:detail` 默认取消；如数据证明收益明显，再以非实时快照登记。
5. 删除未使用 `risk:runtime-rule` 声明和无收益的重复手工缓存能力。

每个缓存必须用数据证明：

* 读写比、DB 查询成本、命中率、P95/P99 Value bytes。
* 允许陈旧时间和所有写入失效点。
* Redis 故障下数据库能承受的最大回源并发。

阶段 5 代码治理已完成：平台配置收敛为四个公开 URL 白名单键，ISO 使用精简环境 Key
并保留历史回退与双写，商户运行资料增加三态 miss marker 和公平 64 许可主库回源舱壁，
风控时间线缓存及未使用的风险 Spring Cache 声明已删除。相关 11 个 Reactor 模块执行
266 个测试，失败 0、错误 0，外部 Redis 集成测试跳过 5 个。真实命中率、Value bytes、
数据库容量和迁移观察仍属于阶段 9 验收，不能用模块测试代替。

## 7. 阶段 6：高并发累计计数

实施次序：

1. 持久化 reserve-confirm-cancel 业务状态先落地。
2. 精简同槽 aggregate/reservation Key 和 Lua。
3. 主库基线、水位、周期边界、跨时区和金额精度测试。
4. `LEGACY/SHADOW` 双计算比对，不影响真实决策。
5. 小商户灰度 `CLUSTER_SAFE` 决策，持续对账。
6. 扩大灰度后停止 legacy 新 reserve，旧 Key 自然过期。

必测：

* 1/100/1000 并发同商户、同交易重试、不同币种和规则。
* 日/周/月边界、闰日、时区、周期 TTL。
* 支付成功、拒绝、渠道超时、回调晚到、人工终态。
* Redis 超时、脚本重试、Cluster failover、DB 主从延迟。
* 对账差异必须为 0；任何差异自动阻断扩大灰度。

阶段 6 代码治理已完成：累计限额采用持久化
`PREPARING/RESERVED/CONFIRMED/CANCELLED` 生命周期，支付终态 Outbox 推进确认或取消，
超时任务按数据库事实对账；同槽 aggregate/reservation Lua 已在真实 Redis 6 Cluster
验证无 `CROSSSLOT`。生产仍保持 `LEGACY`，未完成覆盖最大周期的 `SHADOW` 差异观察、
真实 MySQL 并发和 failover 前不得切换 `CLUSTER_SAFE`。

## 8. 阶段 7：频率限制

* 把固定窗口规则语义迁移为 ZSet 滑动窗口。
* 单 transaction digest 作为 member，Lua 原子 trim/add/count/expire。
* 配置最大窗口、最大阈值、最大 member、攻击流量和超限 Failure Policy。
* 当前同槽固定窗口与未来滑动窗口 shadow 对比命中差异，业务确认边界语义后再切决策。

回滚：保留模式开关；若新窗口不可用，按规则进入 REVIEW/阻断或数据库降级，不静默 PASS。

阶段 7 代码治理已完成：新 Key 为
`acquiring:{environment}:risk:frequency-window:{scopeDigest}`，使用单 ZSet 和生产 Lua
原子完成 trim、交易摘要去重、计数、容量保护与过期。阶段 9 真实 Cluster 已覆盖重复交易、
容量上限和 `WRONGTYPE`，异常均进入 `ERROR/REVIEW`。生产默认仍为 `LEGACY`，只有完整
`SHADOW` 观察和业务确认后才允许切换 `SLIDING_WINDOW`。

## 9. 阶段 8：锁与幂等

| 对象 | 计划 |
| --- | --- |
| 支付锁 | 准备事务返回后立即释放；阶段 9 真实 Redis 已验证 token 持有者释放和 1 秒租约恢复；生产多实例崩溃、failover 仍待验证 |
| 数据库幂等 | 已确认 `sys_oper_log.idempotent_key`、`risk_evaluation_record.risk_record_no` 及支付幂等唯一约束继续承担最终保护 |
| MQ ZSet | 已改为 Redis TIME 双时间桶、SHA-256 member、默认每桶 100000、最大 TTL 30 天；超限或连接故障返回 `FALLBACK` |
| 全局 ID | 已使用 `acquiring:{environment}:global-id:state` 单 Hash；启动校验环境前缀，恢复需确认和时间下限；Redis 故障禁止本地降级 |
| 通用包装器 | String 因存量风控调用默认启用；Hash/List/Set/ZSet 默认不注册，专用滑动窗口和 MQ 继续使用有界 Lua |

## 10. 阶段 9：测试、压测和故障验收

### 10.1 自动化测试

* 单元：Key、Cache Name、TTL/jitter、null、Serializer、Lua、锁、ZSet。
* 集成：Testcontainers 单机 Redis；另建真实 Redis Cluster 测试。
* 事务：提交、回滚、失效失败、重试、Outbox 重放。
* 多实例：同 Key 并发加载、锁竞争、脚本重试和重复 MQ 消息。
* 安全：非法 Key、污染 Value、越权查看/删除、required Bean 缺失。

### 10.2 压测

记录基线和整改后结果：

* Cache hit/miss 与数据库 QPS、P95/P99。
* Redis command/Lua P95/P99、连接池等待、CPU/内存。
* 热 Key、大 Key、同时过期、规则发布失效延迟。
* 累计限额和频率在目标并发下的正确性，不只看吞吐。

### 10.3 故障演练

覆盖 Redis 重启、连接拒绝、超时、Cluster failover、replica lag、内存淘汰、脚本错误、
缓存删除失败、失效消费者积压和数据库回源过载。

阶段 9 已完成本机临时 Redis 6 和六节点 Redis 6 Cluster 的基础验收，覆盖连接拒绝、
Lua `WRONGTYPE`、MQ/ZSet 容量、全局 ID 禁止降级、锁持有者释放和租约超时，并记录
四条生产代码路径的吞吐与 P95/P99。未具备真实 MySQL 数据集、生产网络、Sentinel/Cluster
运维权限和业务流量，因此重启、真实 failover、replica lag、内存淘汰、回源过载及
失效积压只列为生产前阻断项，不从本地结果推断通过。

输出后续要求文件：

```text
docs/redis/09-redis-change-report.md
docs/redis/10-redis-test-report.md
docs/redis/11-redis-performance-report.md
docs/redis/12-redis-failure-drill-report.md
docs/redis/13-redis-acceptance-report.md
```

## 11. 阶段 10：生产就绪补强

阶段 10 的本地范围包括：

1. 使用 Micrometer 固定枚举记录 Spring Cache、miss marker、锁、幂等、MQ 去重、全局 ID、
   generation、风控 Lua、shadow 和缓存失效 Outbox，不允许业务标识进入指标标签。
2. 暴露受控内网 `/actuator/prometheus`，增加应用侧和 `redis_exporter` 服务端告警规则及
   处置手册；服务端内存、淘汰、碎片率、慢日志等指标不能由应用指标替代。
3. 提供 MPGS Sandbox、真实 MySQL 和 UAT Redis 的显式确认脚本；配置缺失或环境不是
   `uat` 时必须拒绝执行，不自动连接任何共享环境。
4. 在一次性 `redis:6` 容器中验证 LFU 淘汰、热/大 Key 检测、无持久化重启、有界负载和
   六节点 Cluster 主节点停止后的副本提升。所有结果只属于本地功能演练。
5. 为存量 generation Key 定义 `LEGACY_ONLY -> DUAL_LEGACY_FIRST ->
   DUAL_COMPACT_FIRST -> COMPACT_WRITE_LEGACY_READ -> COMPACT_ONLY` 迁移门禁。本阶段
   不修改 `RedisCacheGenerationStore.versionedKey(...)` 或
   `DefaultRiskListRuntimeRepository.currentRuleCacheKey(...)`。

generation 双写不具备跨槽原子性。只有全部实例都能识别两侧发布门禁、任一侧异常均回源
主库后，才允许进入双写；停止旧写前必须覆盖最大缓存 TTL 和完整滚动发布窗口。规则缓存
按 TTL 自然过期，持久 current generation Key 保留，不以批量删除完成迁移。详细契约见
`05-redis-target-design.md` 第 10.1 节。

阶段 10 的本地完成门禁不等同于生产准入。Prometheus 规则加载、受控告警触发与恢复、
完整 shadow 周期、真实 Outbox 积压、replica lag、Sentinel/生产 Cluster、数据库回源
过载、多实例故障和生产量级长稳仍必须在 UAT 或等价环境补证。

## 12. 影响范围与回滚总则

| 变更类型 | 主要影响 | 回滚要求 |
| --- | --- | --- |
| Key 结构 | 所有读写方、历史 Key、内存 | 双读/双写或回填；旧 Key 自然过期 |
| Serializer | 所有缓存 Value | 双向兼容通过时保留原 namespace；回滚旧 Serializer，不删除历史 Value |
| 安全/风控失效 | Admin 写、OpenAPI/风控读、MQ | 回滚到主库直查/REVIEW，不回滚到旧缓存放行 |
| 累计计数 | 风控、支付、渠道、对账 | 持久状态可重放；shadow 比对；灰度开关 |
| 频率窗口 | 风控命中语义 | v1/v2 对比；规则级开关 |
| 锁 | 支付并发和渠道副作用 | 数据库幂等先验证；按操作灰度 |
| Admin 监控 | 运营排障能力 | 保留只读脱敏指标，不恢复任意 Key 操作 |

任何阶段失败只回滚本阶段，不删除用户数据，不清空全 Redis，不改变已完成的安全门禁。

## 13. 当前完成点

阶段 0 和阶段 1 的历史审核门禁已经由用户逐阶段确认，阶段 2～8 代码治理、阶段 9 本地
Redis/Cluster 验收、阶段 10 生产就绪补强及其完整全项目回归均已执行。

阶段 10 完整回归使用 Temurin 17 执行 `mvn -Pdev clean test`，23 个 Reactor 模块全部
`SUCCESS`，`BUILD SUCCESS`，耗时 53.666 秒。156 份 Surefire XML 共记录 744 个测试，
失败 0、错误 0、跳过 27，实际执行 717 个。首次执行在 `component-redis` 暴露 2 个自动
装配失败；将可观测性依赖改为可选并在未装配指标时使用 noop 后，7 个定向测试和第二次
完整回归均通过。

27 个跳过项中，19 个 Redis、Cluster 和连接拒绝用例已有阶段 9 隔离脚本通过证据；剩余
8 个依赖 MPGS Sandbox 或真实 MySQL 的 live 用例仍未执行。当前停止点还包括缺少
UAT/生产等价拓扑、完整 shadow 周期、告警通知闭环、真实故障切换和生产量级容量证据。
这些外部准入项完成前，不把本地结果表述为生产准入，也不切换生产 `CLUSTER_SAFE`、
`SLIDING_WINDOW` 或全局 ID 恢复配置。
