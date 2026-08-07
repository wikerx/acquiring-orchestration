# Redis 问题清单

> 本文件以阶段 1 问题识别和分阶段关闭记录为主；早期 Cache Name 与风险描述属于扫描时
> 证据。当前 Redis 功能目录以 `07-redis-cache-catalog.md` 为准。

## 1. 统计与判定标准

| 等级 | 数量 | 判定 |
| --- | ---: | --- |
| P0 | 4 | 可能造成支付、越权、安全事故或明确风控绕过 |
| P1 | 10 | 可能造成交易异常、风控失效或数据错误 |
| P2 | 13 | 性能、稳定性、缓存一致性问题 |
| P3 | 6 | 规范、可维护性和代码质量问题 |
| 合计 | 33 | 当前工作树静态审计结果 |

P0/P1 是进入任何低风险缓存扩展前的门禁。生产拓扑、Redis Server 版本和外部
Nacos 内容不可见的问题，不因缺少证据而降级为“没有风险”。

阶段 4-A 状态更新（2026-07-30）：

| ID | 当前状态 | 说明 |
| --- | --- | --- |
| `RG-P1-04` | 已完成代码治理 | Admin Key 列表改为 `SCAN COUNT 100`，单次最多保留 1000 个 Key，pageSize 最大 100 |
| `RG-P1-05` | 已完成代码治理 | 只允许 `config:public` 中四个登记数据 Key；隐藏 pending 和未知同前缀 Key；不读取 Value；删除记录 Key SHA-256 摘要且请求参数不进操作日志 |
| `RG-P1-06` | 已完成差距审计，风险未关闭 | 已有 `BasicPolymorphicTypeValidator` 和恶意类型拒绝测试，但仍使用 `DefaultTyping.NON_FINAL`；本批不切换 wire format |
| `RG-P1-09` | 已完成代码治理 | 已删除实时交易详情 Cache Name、TTL、读注解、失效服务及全部写侧 evict |
| `RG-P1-10` | 代码门禁已完成，部署验收未完成 | test/uat/prod 前缀错配会阻断启动；真实拓扑、ACL、Server 版本仍需环境验收 |

阶段 4-B 状态更新（2026-07-30）：

| ID | 当前状态 | 说明 |
| --- | --- | --- |
| `RG-P1-01` | 同槽代码路径与真实 Cluster 测试完成，生产切换未完成 | 累计限额新增摘要 Hash Tag 同槽 Key、`LEGACY/SHADOW/CLUSTER_SAFE` 迁移模式和切换门禁；真实 Redis 6 Cluster 无 `CROSSSLOT` |
| `RG-P1-02` | 同槽代码路径与真实 Cluster 测试完成，生产切换未完成 | 频率计数与交易幂等标记使用相同摘要 Hash Tag；真实 Redis 6 Cluster 验证重复交易只计一次 |

阶段 4-C 状态更新（2026-07-30）：

| ID | 当前状态 | 说明 |
| --- | --- | --- |
| `RG-P1-03` | 代码实现和完整自动化回归完成，生产验收未完成 | 已实现持久化 reserve-confirm-cancel、支付终态 Outbox、失败补偿、超时扫描和对账；迁移 SQL 未执行，真实 MySQL、Redis 故障和 MQ 积压验收仍待完成 |

默认模式仍为 `LEGACY`。生产必须先使用 `SHADOW` 覆盖一个完整最大业务周期并核对差异，
再显式设置切换确认；当前状态不能表述为已完成生产迁移。

阶段 5 状态更新（2026-07-30）：

| ID | 当前状态 | 说明 |
| --- | --- | --- |
| `RG-P2-01` | 已完成代码治理 | 取消无可靠失效链路的风控时间线 Spring Cache，查询恢复数据库事实源 |
| `RG-P2-02` | 已完成兼容迁移代码 | ISO 新 Key 使用 `acquiring:{environment}:iso:{country\|currency}`；新 Key 优先、历史 Key 回退、双写，Admin 变更同时删除两代 Key |
| `RG-P2-03` | 已完成本阶段统一策略 | Spring Cache、ISO 和商户 miss marker 使用统一有界 TTL 抖动；未登记 Redis 能力不得自行实现随机 TTL |
| `RG-P2-04` | 商户资料回源保护已完成 | 单实例使用公平 64 许可舱壁；饱和返回可重试 `F503`，不把过载解释为商户不存在 |
| `RG-P2-13` | 已完成代码治理 | 只有主库明确无记录才写 30 秒、10% 抖动的独立 miss marker；Redis 异常保持 `UNAVAILABLE`，不生成负缓存 |
| `RG-P3-02` | 已完成 | 删除未使用的 `risk:runtime-rule` Spring Cache 声明和 TTL 配置 |

## 2. P0 问题

| ID | 问题与证据 | 后果 | 建议 | 影响范围 |
| --- | --- | --- | --- | --- |
| RG-P0-01 | `PaymentRedisCacheAutoConfiguration.paymentCacheErrorHandler` 吞掉 EVICT/CLEAR 异常；`DefaultMerchantRuntimeProfileCacheService.evictRuntimeProfile/evictOpenApiAccessPolicy` 用于商户启停和 IP 白名单变更。现有自动配置测试还把“不抛异常”固定为预期 | 数据库已禁用商户但旧运行时资料仍可用，或已删除 IP 仍被旧策略接受，直到 10/30 分钟 TTL 到期 | 普通 GET 可降级回源；安全缓存 EVICT 必须进入可靠提交后失效、重试/告警，无法确认删除时按业务 Fail Closed | component-redis、component-db、service-admin、service-openapi |
| RG-P0-02 | `DefaultRiskListRuntimeRepository` 为黑白名单、来源 URL、IP 白名单、限额、频率和 3DS 规则建立 `risk:runtime:*` 缓存；`AdminRiskManagementApplicationService` 的新增、修改、删除、导入、启停、发布未调用任何对应失效能力 | 新增黑名单、启用限制规则或删除白名单后，旧 miss/pass 可继续 60~300 秒，形成确定的风控时窗绕过 | 建立规则变更事件与精确索引失效；安全收紧操作先保证失效可达，失败时阻断发布或切版本 | service-risk、service-admin、MQ/Outbox |
| RG-P0-03 | `DefaultRiskListRuntimeRepository` 类级 `@DS(SLAVE)`，`seedPeriodAmount` 还显式用 `DataSourceName.SLAVE` 聚合日/周/月已通过交易金额 | 副本延迟会把累计金额基线低估；低估值写入 Redis 后可持续到周期结束，导致商户累计限额被突破 | 基线从主库或可证明一致的账务事实源读取；定义水位校验、对账和重建，禁止从普通读副本初始化强风控计数 | service-risk、分库分表、数据库读写路由 |
| RG-P0-04 | `OpenApiJwtReplayProtectionService.checkAndMark` 在 `StringRedisTemplate == null` 时直接 return；未检查 `openapi.security.replay.required=true`。连接异常路径反而会按 required 拒绝 | 生产误装配或条件配置导致 Bean 缺失时，防重放静默失效，即使配置要求强制保护 | required=true 且 Bean 缺失时启动失败或请求 Fail Closed；增加上下文装配测试和启动健康门禁 | service-openapi、component-redis、部署配置 |

## 3. P1 问题

| ID | 问题与证据 | 后果 | 建议 | 影响范围 |
| --- | --- | --- | --- | --- |
| RG-P1-01 | 历史累计限额 reserve/rollback Key 不同槽；阶段 4-B 已新增摘要 Hash Tag 同槽路径和迁移门禁，但默认仍为 `LEGACY` | 旧路径在 Cluster 下仍会 `CROSSSLOT`；新路径已通过真实 Cluster 脚本测试，但生产未切换 | 先以 `SHADOW` 覆盖完整最大周期并核对差异，再确认切换；继续补 failover、补偿和生产拓扑验收 | service-risk、Key 规范、部署 |
| RG-P1-02 | 历史频率 counter 与 transaction Key 不同槽；阶段 4-B 已新增同槽路径和迁移门禁，但默认仍为 `LEGACY` | 旧路径在 Cluster 下仍不可用；新路径已验证无 `CROSSSLOT`，固定窗口边界风险仍在 | 完成 SHADOW 观察和切换；阶段 7 再评估单 ZSet 滑动窗口 | service-risk |
| RG-P1-03 | 阶段 4-C 已实现持久化 reserve-confirm-cancel、支付终态 Outbox、失败补偿、超时扫描和数据库对账；生产迁移与故障验收未完成 | 自动化路径已收敛支付成功、失败和重复事件；真实环境中断、Redis failover、MQ 积压及容量风险仍需验证 | 执行迁移审批和真实 MySQL/Redis/MQ 验收；生产继续保持 `LEGACY`，完成 SHADOW 观察前不得切换 | service-risk、service-payment、渠道链路 |
| RG-P1-04 | `AdminMonitorCacheApplicationService.scanKeys` 调用 `StringRedisTemplate.keys(pattern)`，之后才 `limit(1000)` | 大 Keyspace 下阻塞 Redis 单线程；分页是内存分页而非增量扫描 | 使用带 count 的 SCAN 游标、命名空间白名单、服务端硬上限和超时 | service-admin |
| RG-P1-05 | Admin monitor 可读取任意 String/List/Set/ZSet/Hash 值并删除任意 Key；权限只有 query/clear，没有命名空间和操作分级 | 有权限账号可读取安全策略、删除全局 ID、JWT replay、锁和风控计数，扩大越权与误操作半径 | 仅开放批准的 cache namespace；禁止查看敏感值；危险删除双重授权、审计和保护列表 | service-admin、RBAC、安全审计 |
| RG-P1-06 | `RedisTemplateConfig` 和 `PaymentRedisCacheAutoConfiguration` 都使用 `activateDefaultTyping(...NON_FINAL...)` | Redis 内容被污染或权限被突破时扩大不受控多态反序列化攻击面；DTO 演进脆弱 | 使用明确 DTO/类型白名单或无类型元数据 JSON；统一单一序列化配置并做兼容测试 | component-redis、所有缓存消费者 |
| RG-P1-07 | 初始问题为支付锁使用裸 `transaction:*` 且覆盖渠道 I/O；阶段 4-D 已改为 `acquiring:{environment}:payment:lock:{purpose}:{digest}`，并在独立准备事务返回后、渠道调用前释放 | 代码级跨环境冲突和渠道长调用超出租约问题已消除；真实多实例竞争、Redis failover、进程崩溃和容量表现仍未验收 | 保持 30 秒只作为准备阶段租约，数据库 `uk_scope_key` 和状态机继续最终兜底；完成真实环境故障与容量验收后关闭 | service-payment、component-redis |
| RG-P1-08 | 交易频率使用固定窗口 String 计数 | 窗口边界可在极短时间内通过接近 2 倍阈值请求，弱化风险策略 | 评估 ZSet 滑动窗口 Lua；限制 member 数、Key 容量并保留精确交易摘要 | service-risk |
| RG-P1-09 | `DefaultTransactionQueryService.detail` 缓存交易实时状态及多表聚合 3 分钟 | 用户/运营看到旧交易状态，且对象大、失效触点多；违反交易实时状态不缓存原则 | 移除该缓存；如确需报表快照，使用明确非实时 DTO、版本和短 TTL | service-payment、service-admin/merchant 查询 |
| RG-P1-10 | `RedisGlobalIdProperties.stateKey` 默认含 `acquiring:dev`；外部 Nacos import 可选；test/uat/prod 前缀与真实拓扑不可验证 | 配置缺失时非 dev 环境可能共享/污染 dev 命名空间；脚本兼容性和隔离无法证明 | 生产环境强制显式配置并启动校验；部署门禁核对前缀、拓扑、ACL 与 Server 版本 | component-redis、所有服务、Nacos |

## 4. P2 问题

| ID | 问题与证据 | 后果 | 建议 |
| --- | --- | --- | --- |
| RG-P2-01 | 阶段 5 已取消无可靠失效链路的 `risk:evaluation:detail` Spring Cache | 陈旧缓存风险已从代码路径移除；数据库查询容量仍需阶段 9 验收 | 保持数据库事实源查询，未经新的收益、失效和容量证据不得恢复 |
| RG-P2-02 | 阶段 5 已增加环境隔离 ISO Key、历史回退、双写、精确双代删除和 10% TTL 抖动 | 代码兼容路径已完成；历史 Key 在观察期内保留，真实环境迁移尚未验收 | 观察新旧读取和删除指标后停止历史写入，旧 Key 自然过期 |
| RG-P2-03 | 阶段 5 已为 Spring Cache、ISO 和商户 miss marker 接入统一有界 TTL jitter | 本阶段治理对象的同时过期风险已降低；通用未登记能力仍不得直接用于新业务 | 新增 Catalog 条目必须复用统一 TTL 策略并补边界测试 |
| RG-P2-04 | 商户运行资料已增加公平 64 许可数据库回源舱壁，饱和返回可重试 `F503` | 已保护当前高价值缓存；其他缓存仍需逐项提供回源预算 | 阶段 9 验证真实连接池、热点和 Redis 故障容量，未登记缓存不得扩展 |
| RG-P2-05 | String/Hash/List/Set/ZSet 通用 API 接受任意 caller Key，支持无 TTL 写入和完整集合读取 | 新调用容易产生永久 Key、大 Key、跨命名空间删除 | 业务化窄接口、强制 Key Builder、TTL 和容量策略 |
| RG-P2-06 | Hash/Set/ZSet 的“写入并设置 TTL”实际是两个命令 | 进程崩溃或第二命令失败时留下永久 Key | Lua/事务或带原子过期语义的受控实现 |
| RG-P2-07 | `RedisDeduplicationServiceImpl.checkAndAdd` 是 `SADD` 后 `EXPIRE` | 能力一旦启用会出现永久 Set | 合并为 Lua；未启用前先修复或删除能力 |
| RG-P2-08 | 文件号、订单号和 STAN 计数存在 `INCR` 后 `EXPIRE`；溢出/下溢是非原子 reset | 失去 TTL 或并发生成重复序号 | 单 Key Lua 原子初始化、计数、边界处理；先确认真实业务需求 |
| RG-P2-09 | MQ 去重每个 namespace 一个 ZSet，仅按调用清理历史成员，无最大成员数、内存预算或指标 | 消费停止清理、突发流量或超长 TTL 可形成大 Key | 容量上限、分桶、定期清理、cardinality/bytes 指标 |
| RG-P2-10 | 商户限额和 3DS 查询 Key 包含 amount digest | 金额离散度高时 Key 数接近请求数，缓存收益低且耗内存 | 缓存规则集合后在内存选择，或按区间/版本设计低基数 Key |
| RG-P2-11 | OpenAPI IP 访问策略缓存含 IP 明文集合 | Admin 任意值读取能力会暴露访问策略 | 最小 DTO、限制值查看、必要时摘要化，独立安全 namespace |
| RG-P2-12 | 未验证 Actuator/Micrometer 依赖，无命中率、回源、锁、Lua、慢命令和大 Key 指标 | 故障发现和容量治理依赖日志或人工 | 建立指标、告警、Dashboard 和容量基线 |
| RG-P2-13 | 阶段 5 已使用独立三态 miss marker，仅主库明确不存在时写入 30 秒、10% 抖动 marker | Redis 不可用不会伪装成不存在；代码级穿透治理已完成 | 阶段 9 验证攻击流量、marker 基数和数据库回源上限 |

## 5. P3 问题

| ID | 问题与证据 | 建议 |
| --- | --- | --- |
| RG-P3-01 | 两处重复构建 Jackson Redis Serializer | 收敛成单一 Bean 和版本化兼容策略 |
| RG-P3-02 | 阶段 5 已删除未使用的 `PaymentCacheNames.RISK_RUNTIME_RULE` 声明和 TTL 配置 | 后续如确有 Spring Cache 需求，必须重新登记 Catalog 并完成失效设计 |
| RG-P3-03 | 通用 Redis wrapper 大量自动注册但基本无业务调用 | 统计真实消费者；删除重复/危险能力或改为非自动装配 |
| RG-P3-04 | Key 命名混用 `payment:*`、裸 `transaction:*`、`acquiring:{env}:*`，系统和环境隔离不统一 | 按 `acquiring:{environment}:{domain}:{business}[:{businessKey}]` 渐进迁移，保留双读/切换 |
| RG-P3-05 | `PaymentRedisProperties.normalizeSegment` 只去首尾冒号，不拒绝内嵌冒号、`{}`、空白、控制字符或过长片段 | Key Builder 做字符集、长度、Hash Tag 使用场景校验 |
| RG-P3-06 | 测试缺少 Cluster、多实例、缓存删除失败恢复、TTL 抖动、雪崩、热点、大 Key 和 Redis 故障演练 | 建立单元、Testcontainers、Cluster 与故障测试矩阵 |

## 6. 错误使用 Top 20

Top 20 按事故可能性、影响半径和可恢复性排序；编号引用上文，不另建统计口径。

| 排名 | ID / 文件 / 方法 | 当前实现与风险 | 推荐方案 | 修改影响 |
| ---: | --- | --- | --- | --- |
| 1 | RG-P0-02；`DefaultRiskListRuntimeRepository` 全部 cache 方法；Admin 风控写方法 | 风控缓存无失效，安全收紧存在 60~300 秒绕过窗口 | 规则版本 + 精确失效事件 + 发布门禁 | 风控、Admin、MQ/Outbox |
| 2 | RG-P0-03；同类 `seedPeriodAmount` | SLAVE 延迟低估累计金额并固化到 Redis | 主库/一致事实源、水位与对账 | 风控、分片数据库 |
| 3 | RG-P0-01；`PaymentRedisCacheAutoConfiguration.paymentCacheErrorHandler` | 安全缓存 EVICT 失败被吞，旧授权继续生效 | 安全缓存可靠提交后失效、重试和 Fail Closed | Redis 组件、商户、OpenAPI |
| 4 | RG-P0-04；`OpenApiJwtReplayProtectionService.checkAndMark` | Bean 缺失无视 required，重放保护静默关闭 | 启动失败或请求 Fail Closed | OpenAPI、部署 |
| 5 | RG-P1-01；累计限额 reserve/rollback Lua | 同槽路径和 Cluster 测试已完成，生产仍默认走旧 Key | 完整周期 SHADOW、差异门禁、failover 后切换 | 风控 Key 迁移 |
| 6 | RG-P1-03；累计限额 reservation 生命周期 | 阶段 4-C 已完成代码治理；生产迁移、故障演练和容量验收待完成 | 审批迁移后验证真实 MySQL/Redis/MQ 与多实例对账 | 风控、支付、渠道 |
| 7 | RG-P1-02；频率 Lua | 同槽路径已通过 Cluster 测试，生产仍默认走旧 Key | 完整观察后切换；后续单 ZSet 滑动窗口 | 风控 |
| 8 | RG-P1-05；`AdminMonitorCacheApplicationService.value/delete` | 任意读取和删除安全/业务 Key | namespace 白名单、保护列表、双重授权 | Admin、RBAC |
| 9 | RG-P1-04；同类 `scanKeys` | 使用 KEYS 阻塞 Redis | SCAN 游标和硬限额 | Admin |
| 10 | RG-P1-06；两个 serializer 配置 | 广泛 Default Typing | 明确 DTO/白名单序列化 | 全缓存兼容 |
| 11 | RG-P1-07；`PaymentTransactionServiceImpl` 准备锁 | 阶段 4-D 已完成精简脱敏 Key 和渠道调用前解锁；真实多实例、failover、崩溃和容量未验收 | 执行真实环境竞争、租约超时、进程退出和 Redis 故障演练 | 支付核心 |
| 12 | RG-P1-09；`DefaultTransactionQueryService.detail` | 缓存实时交易状态和大聚合 | 移除或显式非实时快照 | 支付查询 |
| 13 | RG-P1-08；`evaluateFrequencyRules` | 固定窗口边界突发 | ZSet 滑动窗口 Lua | 风控 |
| 14 | RG-P1-10；全局 ID 与外部配置 | dev 默认值、生产配置和拓扑未验证 | 非 dev 强制显式配置和环境验收 | 所有服务、Nacos |
| 15 | RG-P2-02；`IsoDictionaryServiceImpl` | 固定无前缀 Key 与 12h TTL | 统一精简 namespace + 失效 | component-db |
| 16 | RG-P2-04；全部查询缓存 | 无热点加载保护与回源限流 | 每 Cache Name 回源预算 | Redis、数据库 |
| 17 | RG-P2-03；全部 TTL | 无抖动 | 统一有界 jitter | Redis 基础设施 |
| 18 | RG-P2-09；MQ ZSet 去重 | 无容量预算和指标 | 分桶/上限/监控 | Admin、Merchant、Risk MQ |
| 19 | RG-P2-10；限额/3DS cache key | amount digest 高基数 | 缓存规则集合、内存选择 | 风控 |
| 20 | RG-P2-01；`JdbcAdminRiskTimelineQueryService.findRiskEvents` | 异步追加后不失效 | 消费提交后精确失效或取消缓存 | Risk MQ、Admin |

## 7. 修复边界

本文件同时保留初始问题证据和后续治理状态。任何 P0/P1 项只有在代码、迁移、真实环境、
回滚和监控门禁都完成后才能关闭；完成代码或单次测试不等同于生产验收。
