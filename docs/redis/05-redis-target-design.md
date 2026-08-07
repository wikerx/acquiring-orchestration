# Redis 目标设计

## 1. 设计目标与非目标

目标是把 Redis 定位为可丢失、可重建、可观测、可降级的加速和并发协调层，
而不是交易或资金事实库。

必须满足：

1. 数据库、配置中心或外部权威系统始终是缓存事实源。
2. 支付状态、余额、累计退款/授权/结算金额和渠道最终结果不进入通用查询缓存。
3. 安全、风控、锁、幂等、普通查询缓存分别定义故障策略，不能共用“吞异常”。
4. 所有 Key、TTL、序列化、容量、失效、重建、Owner 和指标先登记后使用。
5. 多 Key Lua 必须经过真实 Redis Cluster 测试；只有确有原子需求时使用 Hash Tag。
6. 不为技术展示引入 Redisson、Stream、Bloom、Bitmap 或 HyperLogLog。

本设计不批准升级依赖、批量迁移历史 Key 或变更 Redis 部署拓扑。

## 2. 目标分层

```text
业务查询 / 风控 / 支付 / MQ 消费
            |
            v
业务化窄接口（Cache、Lock、RateLimit、Idempotency）
            |
            v
Key Registry + TTL Policy + Serializer + Failure Policy
            |
            v
Spring Cache / StringRedisTemplate / 受控 Lua
            |
            v
Lettuce + Redis（单机开发，生产必须按实测拓扑验收）
            |
            +--> Metrics / Audit / Alert / Rebuild
```

基础设施职责：

| 组件职责 | 目标约束 |
| --- | --- |
| Cache Registry | 集中登记 Cache Name、DTO、TTL、jitter、null TTL、Owner |
| Redis Key Builder | 固定系统/环境/领域/业务；校验字符、长度和 Hash Tag |
| Serializer Registry | Key 一律 String；Value 使用明确 DTO JSON，禁止 broad Default Typing |
| Lifecycle Policy | 按数据职责显式登记永久或临时生命周期；永久业务读模型必须具备可靠失效，临时状态必须设置正 TTL |
| Atomic Operation | Lua 脚本独立资源化、版本化、输入校验、Cluster 测试 |
| Failure Policy | 按用途选择回源、Fail Closed、人工审核、拒绝、重试 |
| Observability | 指标、结构化日志、慢操作、容量、热/大 Key 和失效失败告警 |

## 3. Namespace 与 Key 规范

### 3.1 通用格式

```text
acquiring:{environment}:{domain}:{business}[:{businessKey}]
```

示例：

```text
acquiring:dev:merchant:info:{merchantId}
acquiring:dev:iso:currency
acquiring:dev:iso:country
acquiring:prod:security:jwt-replay:{merchantId}:{jtiDigest}
acquiring:prod:payment:operation-lock:{idempotencyDigest}
```

`service` 不进入物理 Key；`v{version}` 也不是默认层级。只有数据结构或序列化协议发生
不兼容迁移、且无法通过独立新业务名表达时，才允许在该 Key 家族的迁移方案中增加版本片段。
示例中的花括号仅表达变量，不等同于 Redis Cluster Hash Tag。实际生成器必须区分“模板
占位符”和物理 Key 中的 `{...}`。

Key Builder 必须拒绝：

* 空白、控制字符、内嵌未转义冒号和超长片段。
* 调用方自行提供 `{}`；Hash Tag 只能由专用 co-slot API 生成。
* 完整 PAN、CVV、密码、Secret、私钥、完整 Token 和未脱敏证件号。
* 对象 `toString()`、无界 JSON 或未经规范化的用户输入。

### 3.2 业务域与跨服务共享

Key 以业务数据 Owner 的领域命名，不以调用服务命名。多个服务访问同一份数据时使用同一
业务 Key 语义；不同服务的权限隔离通过 Redis ACL、账号和可访问前缀实现。仅以下经架构
评审后可共享：

| 类型 | 共享条件 | Owner |
| --- | --- | --- |
| 商户运行时安全资料 | 多个服务确需相同 DTO 和失效语义 | component-db / merchant |
| 全局 ID 状态 | 全系统必须共用且高可用能力经验证 | component-redis |
| 缓存失效事件 | 仅传 Key 身份/版本，不传敏感 Value | 数据 Owner |

支付锁、风控计数、MQ 去重仍归属各自业务域，不允许 Admin 通过通用接口跨业务域读取或
删除。

### 3.3 Cluster 同槽规范

累计限额保留两个 String Key 时，目标模板必须由专用构造器产生共同 Hash Tag：

```text
acquiring:{environment}:risk:merchant-limit:{scopeDigest}:total
acquiring:{environment}:risk:merchant-limit:{scopeDigest}:reservation:{transactionDigest}
```

Hash Tag 必须使用长度受控、非敏感的稳定摘要；不能直接把任意商户输入放入 `{}`。
频率限制目标改为单个 ZSet Key 后不再需要第二个 transaction Key。

现有长格式 Key 不在本工作包内批量改名。每个旧 Key 家族必须独立评估双读、回填、回滚
和自然过期策略；新增 Key 和本次新增失效门禁直接使用精简格式。

## 4. Spring Cache 设计

### 4.1 可保留范围

| Cache | 目标决定 | 目标 TTL | 失效 |
| --- | --- | --- | --- |
| `merchant:info` | Admin、Merchant Portal、OpenAPI 和支付服务共用的完整商户资料 DTO；包含主体资料、经营地址、联系人、状态、MCC、国家、结算币种、时区、风险等级和审计时间，不包含任何密钥材料 | 永久，无 TTL；数据库明确不存在时使用独立 30 秒 miss marker；项目未上线，不保留历史 Value 双读或结构版本兼容逻辑 | Admin 新增、修改、启停、删除，以及 Merchant Portal 资料修改，均在主库事务内登记同一 pending + Outbox；提交后精确删除正缓存和 marker |
| `merchant:openapi` | 保留启用状态和允许 IP 集合 | 永久，无 TTL；不缓存异常 | IP 策略新增、修改、启停、删除在事务内登记 pending + Outbox，提交后精确删除 |
| `merchant:keyMeta` | 当前 OpenAPI 密钥的 ID、版本、算法、位数、更新时间和组合 revision；不含密钥、公私钥正文 | 永久，无 TTL；空元数据不缓存 | Admin 或 Merchant Portal 初始化、轮换、启停密钥前登记 pending + Outbox，提交后精确删除；OpenAPI 用 revision 驱动 JVM 内短时密钥材料切换 |
| `merchant:route` | 商户绑定、渠道/MID 状态、支付能力、币种范围和超时的非敏感路由快照 | 永久，无 TTL；无路由允许缓存有效空集合 | Admin 修改商户、绑定、渠道、MID 或能力配置前登记 pending + Outbox，提交后精确删除；渠道密码、证书、令牌和 metadata 明文只从 MASTER 读取并短时保留在 JVM |
| `config:public` | 仅允许白名单内四个非敏感公开 URL | 永久，无 TTL；空值不缓存 | 配置保存、修改、启停、删除在事务内登记 pending + Outbox，提交后精确删除 |
| `transaction:detail` | 已于阶段 4-A 移除 | 不适用 | 数据库实时查询 |
| `risk:evaluation:detail` | 默认移除；只有明确非实时快照才保留 | 如保留，30~60 秒 + jitter | 风控审计消费提交后失效 |
| `risk:runtime-rule` | 删除 Spring Cache 死声明 | 不适用 | 风控使用直连 Redis 永久快照和 generation 发布 |

永久只表示 Redis 不设置过期时间，不改变数据库事实源地位。永久缓存能否准入，取决于
管理端变更是否具备事务 Outbox、pending 读保护、精确失效、持续重试和告警闭环。

### 4.2 一致性

永久业务读模型采用带可靠失效保护的 Cache Aside：

```text
读：检查 pending -> 缓存 -> 再检查 pending -> 未命中或 pending 时查 MASTER -> 按条件写缓存
写：Admin 或 Merchant Portal 在主库事务内获取 pending -> 同事务写业务数据和共享 Outbox -> 提交后精确删除 -> 释放 pending
失败：Outbox 保持 INIT/FAILED -> 每 5 秒持续重试 -> 删除和释放完成后标记 SENT
```

`merchant:info` 的数据库事实源固定为 `base_merchant_info`。商户门户只能从认证上下文取得
`merchantId`，不得通过请求参数查询或修改其他商户。联系人和详细街道地址作为完整资料的一部分
进入该受 ACL 保护的永久缓存，但禁止写入日志、操作日志正文或无关接口；OpenAPI 和支付链路只
映射各自需要的字段。JWT Secret、RSA 私钥、AES Key 和其他可直接使用的密钥材料禁止进入
`merchant:info` 或任何 Redis Key。OpenAPI 用 `merchant:keyMeta` 的非敏感 revision 驱动 JVM
内短时密钥缓存，真实密钥固定读取 MASTER，避免轮换后的复制延迟。

安全收紧操作不能仅依赖最终 TTL：

* 禁用商户、删除允许 IP、新增黑名单、启用拒绝规则必须有可靠失效结果。
* 可选实现为事务 Outbox + 失效消费者，或提交后同步失效 + 持久重试任务。
* 重试达到阈值仍失败时，发布操作必须 Fail Closed 或进入人工处理，具体由 Owner 确认。

### 4.3 穿透、击穿与雪崩

* 只有“数据库明确不存在”才能写独立短空值缓存。
* 数据库异常、超时、反序列化失败不得转换成空值。
* jitter 只用于已登记的有限期缓存，建议 `baseTtl * [0.9, 1.1]`；永久缓存不挂载 TTL
  函数，避免配置误把可靠失效问题掩盖为等待过期。
* 热点 Key 采用单 Key 加载、短时旧值、预热或回源舱壁；不能把
  `@Cacheable(sync=true)` 当作跨实例分布式锁。
* Redis 全故障时按服务设置回源并发、超时和降级预算，避免数据库雪崩。

## 5. 直连 Redis 目标

| 场景 | 目标结构 | 原子性 | 事实源 / 故障策略 |
| --- | --- | --- | --- |
| JWT replay | String `SET NX EX` | 单命令 | required=true 时 Bean 缺失或 Redis 失败均 Fail Closed |
| 支付锁 | String token + compare-delete Lua；是否引入成熟锁库另行评审 | 获取/释放原子；需要临界区预算或续期 | 数据库唯一约束仍兜底；锁失败拒绝/处理中 |
| 全局 ID | Hash + Redis TIME + Lua | 单 Key Lua | Redis 强依赖；部署 HA、备份和禁止通用删除 |
| MQ 去重 | 分桶 ZSet 或受控 ZSet | Lua 清理 + `ZADD NX` | DB 唯一约束；Redis 故障可放行但必须告警 |
| 风控查询 | 精确名单使用 Hash；范围、来源、限额、3DS 和频率规则使用有界 JSON 快照；公共 BIN 发卡国家使用 generation 隔离的按 BIN 短 TTL Cache-Aside | 快照使用单 Key 替换或单 Key Lua；BIN 查询按摘要 Key 独立读写 | DB 事实源；规则快照永久无 TTL 并由 Outbox 切 generation；BIN 命中默认缓存 300 秒、未命中默认缓存 60 秒，Redis、generation 或缓存内容不可用时执行数据库区间点查 |
| 累计限额 | 同槽 aggregate/reservation String + Lua | 双 Key 同槽 Lua | 主库/一致事实源；reserve-confirm-cancel + 对账 |
| 频率限制 | 单 Key ZSet 滑动窗口 | Lua trim/add/count/expire | 风控策略决定 Fail Closed/REVIEW；限制 member 数 |
| ISO 字典 | 固定 String JSON 数组 | 单 Key | ISO DB；`acquiring:{environment}:iso:{country|currency}` 永久无 TTL，管理变更精确删除，故障有界回源 |

## 6. 序列化与兼容

1. Spring Cache 和 `RedisTemplate` 共用受控 Serializer Registry。
2. 普通缓存 Value 使用明确 DTO JSON，不写数据库 Entity。
3. 不使用 JDK 序列化，不启用 `DefaultTyping.NON_FINAL`。
4. 金额使用字符串或整数最小单位，DTO 中使用 `BigDecimal`，禁止浮点数。
5. 时间格式、枚举未知值、字段新增/删除必须有兼容测试。
6. 不兼容变更使用独立新 Key 双读/回填迁移，不原地反序列化旧 Value；版本片段只在确有
   兼容性需要时出现。
7. 安全标识采用 SHA-256 等稳定摘要；日志仅记录 namespace、摘要长度和错误类型。

阶段 4-A 只完成 Serializer v2 差距审计，没有修改现有 Value wire format。当前集中工厂已
使用 `BasicPolymorphicTypeValidator` 限制包范围并能拒绝 `java.io.File`，但仍启用
`DefaultTyping.NON_FINAL`，因此尚未达到本节“明确 DTO/无类型元数据”的最终目标。

## 7. 故障策略矩阵

| 用途 | Redis GET/WRITE 失败 | Bean 缺失 | 删除/失效失败 | 禁止行为 |
| --- | --- | --- | --- | --- |
| 普通查询缓存 | 有界回源 DB | 回源 DB | 告警并持久重试 | 无限制回源 |
| 商户启停/IP 安全策略 | 查询可回源主库 | 启动门禁或回源主库 | Fail Closed / 阻断发布 / 可靠重试 | 吞异常等 TTL |
| 黑名单/风控限制 | 降级主库、REVIEW 或阻断，按规则登记 | 关键能力启动失败 | 阻断规则发布或切新版本失败 | 默认直接 PASS |
| JWT replay | required=true Fail Closed | required=true 启动失败 | 不适用 | required=true 静默放行 |
| 支付锁 | 返回处理中/拒绝，不进临界区 | 若锁是必需能力则启动失败 | token 安全释放失败告警，等待 TTL | 捕获后继续执行 |
| 支付幂等 | 依赖 DB 唯一约束/幂等记录 | DB 保护继续 | 不适用 | Redis 作为唯一依据 |
| MQ 辅助去重 | 允许进入 DB 唯一约束，打点告警 | 同左 | 失败消息释放失败靠 TTL | 无 DB 兜底时放行 |
| 全局 ID | 中断编号生成并告警 | 启动失败 | 不适用 | 本地无约束降级 |

## 8. 管理与安全

Admin Redis 监控目标只提供：

* 经过脱敏和白名单过滤的连接/容量指标。
* 指定 cache namespace 的 SCAN，不允许默认 `*`。
* 元数据查看，不返回安全、锁、幂等、全局 ID、风控计数 Value。
* 只对普通查询缓存提供精确 Key 失效；禁止通用任意删除。
* 危险操作需要独立权限、二次确认、原因、审批和不可抵赖审计。

Redis 账号按服务和用途最小权限隔离；生产环境核验 TLS、ACL、持久化、备份、
`maxmemory-policy`、慢日志和命令禁用策略。

## 9. 可观测性

最低指标：

| 类别 | 指标 |
| --- | --- |
| 连接 | 可用性、连接池 active/idle/wait、连接/命令超时、重连 |
| 缓存 | 各 Cache Name hit/miss/load/evict failure、回源次数和耗时 |
| Keyspace | namespace cardinality、bytes、TTL 分布、hot key、big key |
| Lua | script 名称、成功/失败/CROSSSLOT/超时、执行耗时 |
| 锁 | 获取成功/失败、等待、持有时长、TTL 到期前完成率、释放失败 |
| 风控 | 规则缓存版本、失效延迟、频率触发、累计 reserve/confirm/cancel/对账差异 |
| MQ 去重 | namespace ZSet 大小、清理数量、重复命中、DB 唯一冲突 |
| Redis 服务 | memory、fragmentation、evicted/expired、latency、slowlog、replication lag |

告警必须能关联环境、服务、namespace 和 operation，不记录完整 Value 或敏感 Key。

## 10. 迁移原则

* 每个 Key 家族独立迁移，禁止一次性全量改名。
* 需要双轨兼容的 Key 采用旧 Key 读 -> 新 Key 回填 -> 新 Key 主读 -> 停止旧 Key 写 ->
  自然过期的顺序；已明确退役且无可靠环境归属的 ISO 历史 Key 不再由代码读写，只由运维
  确认环境后精确清理。
* 持久全局 ID 状态不能自然重建，必须独立备份、停机/双写方案和唯一性验证。
* 风控计数迁移必须保留周期基线和 reservation 状态，不允许直接清空。
* 所有迁移先 dev/test，再真实 Cluster 的 uat，最后灰度生产；每步有指标和回滚开关。

### 10.1 风控 generation 兼容 Key 迁移

以下两个调用点继续保留现状，当前工作包只定义迁移契约，不直接改名：

1. `RedisCacheGenerationStore.versionedKey(...)`
2. `DefaultRiskListRuntimeRepository.currentRuleCacheKey(...)`

存量与目标 Key 家族如下。`{segments...}` 只表示由代码生成的受控、脱敏业务片段，不允许
把任意用户输入原样拼入 Key。

| 用途 | 存量 Key | 精简目标 Key |
| --- | --- | --- |
| 当前 generation | `acquiring:{environment}:component-redis:cache:generation:v1:risk-runtime-rule:current` | `acquiring:{environment}:cache:generation:risk-runtime-rule:current` |
| 发布门禁 | `acquiring:{environment}:component-redis:cache:generation:v1:risk-runtime-rule:publication` | `acquiring:{environment}:cache:generation:risk-runtime-rule:publication` |
| generation 规则缓存 | `acquiring:{environment}:service-risk:risk:runtime-rule:v1:{generation}:{segments...}` | `acquiring:{environment}:risk:runtime-rule:{generation}:{segments...}` |

迁移必须由显式模式开关控制，顺序固定为：

| 模式 | 写入策略 | 读取策略 | 进入下一步的门禁 |
| --- | --- | --- | --- |
| `LEGACY_ONLY` | 只写存量 Key | 只读存量 Key | 完成 Key 基数、TTL、实例版本和发布窗口基线 |
| `DUAL_LEGACY_FIRST` | generation、发布门禁和规则缓存双写 | 存量优先，精简回退；并比较两侧状态 | 所有实例均识别双门禁；覆盖至少一个最大缓存 TTL 和完整滚动发布窗口，generation 差异、双写失败均为 0 |
| `DUAL_COMPACT_FIRST` | 继续双写 | 精简优先，存量回退；记录回退和不一致 | 再覆盖至少一个最大缓存 TTL，精简读取错误、存量回退和 shadow 差异均为 0 |
| `COMPACT_WRITE_LEGACY_READ` | 停止存量新写，只写精简 Key | 精简优先，存量只作回退 | 已过发布回滚窗口，且连续一个最大缓存 TTL 无存量回退 |
| `COMPACT_ONLY` | 只写精简 Key | 只读精简 Key | 规则缓存自然过期完成，Owner 审核证据并关闭迁移开关 |

generation 当前值和发布门禁分属两个 Key 家族，迁移时不得假设跨槽双写原子。进入
`DUAL_LEGACY_FIRST` 前，必须先滚动替换为能同时识别两侧发布门禁和 generation 差异的
版本；任一门禁处于发布中、任一侧写失败或两侧 generation 不一致时，规则读路径必须绕过
Redis 并回源主库。恢复时以数据库已发布规则版本为事实，不根据“最后写入的 Redis 值”
猜测正确 generation。

回滚要求：

1. 停止存量写之前，可立即切回 `LEGACY_ONLY`，精简 Key 保留供审计，不批量删除。
2. 停止存量写之后，只能先恢复双写并从数据库已发布版本校准两侧 generation，再恢复
   存量优先读取；禁止把单侧 Redis generation 直接覆盖到另一侧。
3. generation 规则缓存和发布门禁按既有正 TTL 自然过期；持久的 current generation
   Key 不会自然过期，迁移完成后仍保留。其最终退役必须另行审批、精确备份并验证无旧实例、
   无存量回退和无回滚需求，不能使用 `KEYS`、`FLUSHDB` 或前缀批量删除。
4. 指标只使用模式、操作、结果和固定失败类型等有界标签，不记录 merchantId、规则标识、
   generation、完整 Redis Key 或发布 token。
