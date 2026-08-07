# Redis 扫描时使用清单

## 1. 扫描口径

本清单对应 `REDIS-GOVERNANCE-001` 阶段 1，基于
`feature_scott_payment@691becd9d443160d92deb4407763393248f10ceb`
的当前 dirty 工作树。扫描覆盖全部 Java 源码、配置、SQL、测试和文档。

> 表格保留阶段 1 的原始扫描证据，旧 Cache Name、TTL 和 ISO Key 不代表治理后的现状。
> 当前实现统一以 `07-redis-cache-catalog.md` 为准，实施差异见 `09-redis-change-report.md`。

统计规则：

* “活跃”表示存在生产业务调用链，不表示已在生产环境验证。
* “能力型”表示组件已注册或可被调用，但全仓未发现生产业务调用。
* 物理 Key 家族按相同数据类型、业务目的和生命周期合并近似变体；例如频率计数脚本的计数 Key 与交易去重 Key合并为一个状态家族。
* Spring Cache 的物理 Key 由 `keyPrefix + cacheName + ":" + annotationKey` 组成。
* test/uat/prod 的真实 Nacos 配置和 Redis 拓扑不可见；阶段 4-A 已增加代码级环境前缀启动门禁，但外部拓扑、ACL 和 Server 版本仍待部署验收。

## 2. Spring Cache 清单

项目声明 5 个 Cache Name，实际使用 4 个；共 4 个 `@Cacheable` 和 4 个
`@CacheEvict`。未发现 `@CachePut`、`@Caching`、`@CacheConfig` 或
`allEntries=true`。

| 服务 | 类 / 方法 | 注解 | Cache Name / Key | TTL | 数据源与一致性 | 故障策略 | 敏感性 | 问题 / 建议 | 风险 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| component-db | `DefaultMerchantRuntimeProfileCacheService.findRuntimeProfile` | `@Cacheable` | `merchant:runtime-profile` / `merchantId` | 30 分钟 | 主库商户资料；Admin 更新后精确删除 | GET/PUT 异常吞掉并回源 | 高：启停状态、密钥状态摘要 | 保留查询缓存；EVICT 必须可靠，补短空值 TTL | P0 |
| component-db | `DefaultMerchantRuntimeProfileCacheService.evictRuntimeProfile` | `@CacheEvict` | 同上 | 不适用 | Admin 商户新增、更新、启停后调用 | EVICT 异常仅记录 | 高 | 安全缓存删除失败仍保留旧值；提交后可靠失效 | P0 |
| service-openapi | `MerchantOpenApiAccessPolicyCacheService.findPolicy` | `@Cacheable` | `merchant:openapi-access` / `merchantId` | 10 分钟 | 商户和 IP 白名单表；Admin 更新后精确删除 | GET/PUT 异常吞掉并回源 | 高：允许 IP 明文集合 | 保留但缩小 DTO；EVICT 失败应 Fail Closed 或重试 | P0 |
| component-db | `DefaultMerchantRuntimeProfileCacheService.evictOpenApiAccessPolicy` | `@CacheEvict` | 同上 | 不适用 | IP 白名单新增、更新、删除、启停后调用 | EVICT 异常仅记录 | 高 | 删除失败可继续接受已移除 IP；建立可靠失效 | P0 |
| service-merchant | `MerchantConfigServiceImpl.enabledConfigValue` | `@Cacheable` | `platform:config` / `configKey` | 10 分钟 | 平台配置表 | GET/PUT 异常回源；Admin 变更后删除 | 中 | 保留；限制允许缓存的非敏感配置键 | P2 |
| service-admin | `AdminConfigServiceImpl.saveConfig` | `@CacheEvict` | 同上 / `configKey` | 不适用 | 数据库提交后事务感知删除 | EVICT 异常仅记录 | 中 | 普通配置可最终一致；补重试和告警 | P2 |
| service-admin | `AdminConfigServiceImpl.deleteConfig` | `@CacheEvict` | 同上 / `configKey` | 不适用 | 同上 | 同上 | 中 | 同上 | P2 |
| service-admin | `JdbcAdminRiskTimelineQueryService.findRiskEvents` | `@Cacheable` | `risk:evaluation:detail` / `transactionId` | 3 分钟 | 风控审计表 | GET/PUT 异常回源；无写入失效 | 高：风控命中详情 | MQ 异步追加后缓存可能陈旧；补事件失效或取消 | P2 |
| component-core | `PaymentCacheNames.RISK_RUNTIME_RULE` | 仅声明 | `risk:runtime-rule` | 5 分钟 | 无活跃注解调用 | 不适用 | 中 | 删除无效声明，或迁移直连缓存后正式登记 | P3 |

阶段 4-A 已删除 `transaction:detail` 的 Cache Name、TTL、`@Cacheable`、失效服务和全部写路径
evict。交易详情现直接查询数据库事实表；旧物理 Key 不再读写，按原 3 分钟 TTL 自然过期。

CacheManager 当前统一关闭 null 缓存、启用 `transactionAware()`，但没有 TTL
抖动、单 Key 加载保护或回源并发控制。`CacheErrorHandler` 对
GET/PUT/EVICT/CLEAR 全部吞异常。

## 3. 直接 Redis 业务使用清单

| 服务 | 类 / 方法 | 用途 | 数据结构 / Key 家族 | TTL | 数据源 / 一致性 | 故障策略 | 敏感性 | 主要结论 | 风险 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| component-db | `IsoDictionaryServiceImpl.findAllCountries`、`findAllCurrencies` | ISO 字典缓存 | String；`payment:iso:country:all`、`payment:iso:currency:all` | 固定 12 小时 | ISO 数据库；只依赖过期重建 | Redis 异常回源 | 低 | Key 无统一系统/环境前缀，无显式失效、抖动和击穿保护 | P2 |
| service-openapi | `OpenApiJwtReplayProtectionService.checkAndMark` | JWT 防重放 | String；`{prefix}:security:openapi:jwt-replay:{merchantId}:{jtiDigest}` | JWT 剩余时间 + 60 秒 | 请求首次写入，`SET NX EX` | 连接异常按 `replayRequired`；Bean 缺失直接放行 | 高：安全标识摘要 | Bean 缺失绕过 required 配置 | P0 |
| service-payment | `PaymentTransactionServiceImpl` 交易动作准备锁 | 交易动作本地准备互斥 | String 锁；`acquiring:{environment}:payment:lock:operation:{idempotencyDigest}` | 固定 30 秒 | 仅覆盖独立本地准备事务；DB 幂等表和 `uk_scope_key` 唯一约束最终兜底 | 获取失败返回既有幂等结果或繁忙；准备事务返回后用 Lua token 解锁；解锁异常告警并依赖 TTL | 高：只保存 SHA-256 摘要 | 阶段 4-D 已完成 Key 和锁作用域治理；真实多实例、超时和崩溃验收待完成 | P1 验收中 |
| service-payment | `PaymentTransactionServiceImpl` 商户订单流准备锁 | 同商户订单首次准备互斥 | String 锁；`acquiring:{environment}:payment:lock:merchant-order-flow:{orderDigest}` | 固定 30 秒 | 仅覆盖首次交易独立本地准备事务；数据库仍为事实源 | 同上 | 高：只保存 SHA-256 摘要 | 阶段 4-D 已消除裸 Key 和渠道 I/O 长锁；真实环境验收待完成 | P1 验收中 |
| component-redis | `RedisGlobalIdGenerator.nextId` | 全局 ID | Hash；默认 `acquiring:dev:global-id:{state}` | 无 TTL，持久状态 | Redis TIME + Lua HSET；Redis 强依赖 | 失败中断编号生成 | 高 | 数据结构合理；环境默认值和部署可用性需核验 | P1 |
| component-redis | `RedisIdempotentServiceImpl.acquire(namespace,...)` | MQ 消费去重 | ZSet；`{prefix}:mq:dedup:{namespace}` | 每次调用重置为业务 TTL | RocketMQ 消息摘要；数据库唯一约束兜底 | Redis 缺失时放行 | 中 | 三个命名空间共用模式；需容量预算和指标 | P2 |
| service-admin | `AdminOperationLogConsumer.consume/release` | Admin 操作日志去重 | ZSet；namespace `admin-operation-log` | 消费配置 TTL | MQ + DB 唯一约束 | Redis 缺失放行，DB 兜底 | 中 | 可保留辅助去重 | P2 |
| service-merchant | `MerchantOperationLogConsumer.consume/release` | Merchant 操作日志去重 | ZSet；namespace `merchant-operation-log` | 消费配置 TTL | MQ + DB 唯一约束 | 同上 | 中 | 可保留辅助去重 | P2 |
| service-risk | `RiskEvaluationAuditConsumer.consume/release` | 风控审计去重 | ZSet；namespace `risk-audit` | 消费配置 TTL | MQ + DB 唯一约束 | 同上 | 高 | Redis 不是唯一幂等来源；监控大 ZSet | P2 |
| service-admin | `AdminMonitorCacheApplicationService.info` | Redis INFO | Server command | 无 | Redis 运行态 | 异常转换为未连接响应 | 高 | 返回范围和生产权限需收敛 | P1 |
| service-admin | `AdminMonitorCacheApplicationService.keys` | Key 检索 | `KEYS pattern` | 无 | Redis 全 Key 空间 | 异常上抛 | 高 | 1000 条限制在 KEYS 完成后，生产阻塞风险 | P1 |
| service-admin | `AdminMonitorCacheApplicationService.value` | 任意 Key 值读取 | String/List/Set/ZSet/Hash 全量或前 101 条 | 无 | Redis 任意命名空间 | 异常上抛 | 极高 | 可读取安全和业务状态，无命名空间白名单 | P1 |
| service-admin | `AdminMonitorCacheApplicationService.delete` | 任意 Key 删除 | 任意类型 / 任意 Key | 无 | Redis 任意命名空间 | 返回删除结果 | 极高 | 可删除锁、ID、重放和风控计数；应分级隔离 | P1 |

## 4. 风控运行时 Redis 清单

以下使用点都位于
`service-risk/.../DefaultRiskListRuntimeRepository`。普通查询缓存命中 TTL 为
300 秒，未命中 TTL 为 60 秒；所有 Key 都以 `payment.redis.key-prefix`
开头。代码未提供与 Admin 风控增删改、导入、发布相连接的精确失效入口。

| 方法 | 当前用途 | Key 模板（省略顶级前缀） | TTL / 数据源 | 故障行为 | 问题 / 风险 |
| --- | --- | --- | --- | --- | --- |
| `findListMatch` | 黑白名单、AML 等匹配缓存 | `risk:runtime:match:{module}:{function}:{merchant}:{lookupDigest}` | 300/60 秒；风控表 | 缓存异常回源 DB | 新增黑名单可能在旧 miss 过期前不生效，P0 |
| `hasActiveListRule` | 是否有启用列表规则 | `risk:runtime:list:active:{function}:{merchant}` | 300/60 秒；风控表 | 回源 DB | 规则启停无失效，P0 |
| `findSourceUrlRule` / `findSourceUrlRestrictionMiss` | 来源域名允许/拒绝 | `risk:runtime:rule:source-url[:miss]:{merchant}:{digest}` | 300/60 秒 | 回源 DB | 限制规则新增后旧结果可继续放行，P0 |
| `findMerchantIpWhitelistHit` | 风控链路 IP 白名单命中 | `risk:runtime:merchant-ip-whitelist:hit:{merchant}:{ipDigest}` | 300/60 秒 | 回源 DB | 与 Spring Cache IP 策略是两套缓存，Admin 只清后者，P0 |
| `findMerchantIpWhitelistMiss` | 风控链路 IP 白名单未命中 | `risk:runtime:merchant-ip-whitelist:miss:{merchant}:{ipDigest}` | 300/60 秒 | 回源 DB | 同上，P0 |
| `findMerchantLimitRule` | 单笔商户限额规则 | `risk:runtime:rule:merchant-limit:{merchant}:{currency}:{amountDigest}` | 300/60 秒 | 回源 DB | 金额进入 Key 导致高基数；规则变更无失效 |
| `hasActiveMerchantLimitRule` | 是否有商户限额规则 | `risk:runtime:rule:merchant-limit:active:{merchant}:{currency}` | 300/60 秒 | 回源 DB | 规则启停无失效 |
| `activeCumulativeMerchantLimitRules` | 累计限额规则列表 | `risk:runtime:rule:merchant-limit:cumulative:active:{merchant}:{currency}` | 300/60 秒 | 回源 DB | 规则变更无失效 |
| `findIssuerCountryByCardBin` | 卡 BIN 发卡国家 | `risk:runtime:card-bin:issuer-country:{binDigest}` | 300/60 秒 | 回源 DB | 可保留；需要容量与失效方案 |
| `findThreeDsRule` | 3DS 规则选择 | `risk:runtime:rule:three-ds:{merchant}:{method}:{brand}:{currency}:{risk}:{amountDigest}` | 300/60 秒 | 回源 DB | 高基数，变更无失效 |
| `activeFrequencyRules` | 频率规则列表 | `risk:runtime:rule:frequency:active:{merchant}` | 300/60 秒 | 回源 DB | 变更无失效 |
| `reserveCumulativeMerchantLimits` | 日/周/月累计金额预占 | `risk:runtime:merchant-limit:{type}:{rule}:{merchant}:{currency}:{bucket}` | 到周期结束后 1 小时 | 从 SLAVE 聚合交易后 Lua 原子累加 | 双 Key 跨槽；从库延迟可低估；跨服务失败未确认/补偿，P0/P1 |
| 同上 / `rollbackCumulativeMerchantLimits` | 单交易预占幂等及回滚 | `{aggregateKey}:reservation:{transactionDigest}` | 同聚合 Key | Lua reserve/rollback | 与聚合 Key 无 Hash Tag；仅当前评估失败回滚 |
| `evaluateFrequencyRules` | 固定窗口频率计数 | `risk:runtime:frequency:{rule}:{merchant}:{elementDigest}` + `:transaction:{transactionDigest}` | 规则窗口 | Lua `INCR/EXPIRE/SET` | 双 Key 跨槽；固定窗口边界突发，P1 |

累计金额使用整数最小单位（内部 6 位小数）传入 Lua，未发现
`double/float` 或 Lua 浮点金额累加；金额精度实现本身不是本轮问题。

## 5. 能力型、当前未发现业务调用的组件

| 组件 | 能力 | 数据结构 | TTL 特征 | 结论 |
| --- | --- | --- | --- | --- |
| `RedisStringServiceImpl` | get/set/delete/increment 等通用 API | String | 允许无 TTL | API 过宽，调用者可传任意 Key；新增调用前必须登记 |
| `RedisHashServiceImpl` | Hash CRUD、全量 entries、increment | Hash | 写入后另行 expire | write + expire 非原子；允许大 Hash 全读 |
| `RedisSetServiceImpl` | Set CRUD、members、集合运算 | Set | 写入后另行 expire | 当前无业务家族；不应仅为使用 Set 强行迁移 |
| `RedisZSetServiceImpl` | ZSet CRUD、范围查询 | ZSet | 写入后另行 expire | 当前业务 ZSet 直接使用 `StringRedisTemplate` |
| `RedisListServiceImpl` | List push/range/trim | List | 写入后另行 expire | 不得替代 RocketMQ |
| `RedisDeduplicationServiceImpl.checkAndAdd` | ARN/文件去重 | Set | `SADD` 后 `EXPIRE` | 非原子；未发现业务调用 |
| `RedisDeduplicationServiceImpl.nextFileId` | 文件序号 | String | `INCR` 后 `EXPIRE` | 非原子；未发现业务调用 |
| `RedisOrderNoGeneratorImpl.nextOrderNo` | 订单号序列 | String | 首次 `INCR` 后 2 天 TTL | 非原子；未发现业务调用 |
| `RedisIdentityServiceImpl.nextDailyStan` | STAN 递增序列 | String | 首次 `INCR` 后次日 TTL | 溢出重置竞态；未发现业务调用 |
| `RedisIdentityServiceImpl.nextDailyDecrementStan` | STAN 递减序列 | String | `SET NX` 带 TTL | 下溢重置竞态；未发现业务调用 |
| `RedisIdempotentServiceImpl.acquire(key,...)` | 单 Key 辅助幂等 | String | `SET NX EX` | 无业务调用；Redis 缺失时放行 |
| `RedisCacheServiceImpl` | 手工 JSON String 缓存 | String | 调用者提供 TTL | 与 Spring Cache、String Service 重叠 |

## 6. 数据结构统计

按“活跃物理 Key 模式家族”计数：

| 类型 | 活跃家族数 | 说明 |
| --- | ---: | --- |
| String | 22 | 含 4 个活跃 Spring Cache、ISO、防重放、2 类锁及风控查询/计数家族；同一 Lua 中共生命周期的双 Key 合并计数 |
| Hash | 1 | 全局 ID 状态 |
| Set | 0 | 仅存在能力型去重和通用封装 |
| ZSet | 1 | MQ 去重家族，存在 3 个具体 namespace |
| List | 0 | 仅通用封装 |
| Stream | 0 | 未发现 |
| Bitmap | 0 | 未发现 |
| HyperLogLog | 0 | 未发现 |
| Bloom Filter | 0 | 未发现，也无依赖支持 |
| 分布式锁 | 2 | 两个业务 Key 家族，底层均为 String |
| Lua | 6 | 锁释放、全局 ID、MQ 去重、累计限额 reserve/rollback、频率计数 |
| Spring Cache | 5/4/8 | 5 个声明、4 个活跃 Cache Name、8 个注解 |

若按每个具体 namespace、hit/miss 变体和 Lua 双 Key 分开统计，物理模板数量会更高；
本报告所有后续统计统一采用上述家族口径。

## 7. 未发现的错误模式

本轮未发现以下活跃实现：

* Redis 保存余额、结算金额、退款额度或支付最终结果并作为唯一事实源。
* Redis List、Stream 或 Pub/Sub 替代 RocketMQ。
* Redisson、Reactive Redis、Bitmap、HyperLogLog 或 Bloom Filter。
* `allEntries=true` 全量清 Spring Cache。
* Spring Cache 注解的明确类内自调用。
* JDK 原生序列化。
* 缓存完整银行卡号、CVV、密码、私钥、API Secret 或完整 Token。

“未发现”仅代表静态扫描当前工作树，没有替代运行环境 ACL、慢日志、Keyspace
抽样和内存分析。
