# Redis 缓存功能目录

## 1. 使用规则

本目录是当前代码的 Redis 准入清单。业务代码新增 Key 前必须先补充本文件，并明确事实源、
数据结构、容量、生命周期、变更行为、故障策略和 Owner。

统一 Key 格式：

```text
acquiring:{environment}:{domain}:{dataset}[:{businessKey}]
```

新 Key 不默认包含 `{service}` 或 `v{version}`。花括号在本文中表示变量；只有累计限额等
确需多 Key 同槽 Lua 的场景，物理 Key 才包含由组件生成的 Redis Cluster Hash Tag。

生命周期含义：

* **永久**：不设置 Redis TTL，但数据库仍是事实源；管理端真实变更必须可靠失效或切换代际。
* **临时**：必须设置 TTL；到期用于清理恢复性状态，不替代数据库幂等、Outbox 或状态机。
* **兼容**：仅供迁移模式使用，不是新接入目标。
* **退役**：生产代码已停止读写；只允许按迁移说明处理历史 Key。

表中“数据示例”是脱敏后的逻辑内容，不承诺与 Redis Serializer 的实际字节完全一致。

## 2. 永久业务读模型

| 缓存名称 | Redis 类型 | Key / 键值对 | 脱敏数据示例 | 过期策略 | 新增、修改、删除行为 | 故障、容量与说明 |
| --- | --- | --- | --- | --- | --- | --- |
| 商户完整资料 | String，Spring Cache JSON | `acquiring:{env}:merchant:info:{merchantId}`；Key 为商户号，Value 为跨 Admin、Merchant Portal、OpenAPI 与支付服务共用的完整资料 DTO | `{"merchantId":"200045","merchantName":"Acquiring Merchant","billingDescriptor":"ACQUIRING","merchantStatus":1,"countryCode":"USA","addressLine":"***","contactEmail":"o***@example.com","settlementCurrency":"USD","timezone":"Asia/Shanghai"}` | 永久，无 TTL；不存在使用独立 30 秒 miss marker；项目未上线，不保留历史 Value 双读或结构版本兼容分支 | Admin 新增、修改、启停、删除和 Merchant Portal 资料修改均先获取 pending；同一主库事务写共享 Outbox；提交后精确删除正缓存和 miss marker，失败每 5 秒重试；写事务同时安排事务提交后的新 Value 写入 | `base_merchant_info` 是唯一事实源；pending 或门禁查询异常时绕过缓存查 MASTER；Value 包含联系人和经营地址等受保护资料，Redis 必须限制 ACL，且日志、审计正文和无关接口不得传播；JWT Secret、RSA 私钥、AES Key 不进入 Value；单实例数据库回源使用 64 许可舱壁 |
| 商户 OpenAPI 密钥元数据 | String，Spring Cache JSON | `acquiring:{env}:merchant:keyMeta:{merchantId}`；Value 为当前三类密钥的非敏感版本快照 | `{"merchantId":"200045","jwtKeyId":41,"jwtKeyVersion":"k-20260801","jwtAlgorithm":"HS256","platformKeyId":51,"responseKeyId":61,"revision":"sha256:..."}` | 永久，无 TTL；元数据不存在时不缓存 | Admin 或 Merchant Portal 初始化、轮换、启停密钥前创建 pending + Outbox；提交后精确删除；OpenAPI 下一次读取从 MASTER 重建元数据 | Redis 仅保存 ID、版本、算法、位数、时间和组合 revision；JWT Secret、公私钥正文永不进入 Redis；OpenAPI 实际密钥只从 MASTER 加载到有 TTL、有容量上限的 JVM 本地缓存 |
| 商户收单路由 | String，Spring Cache JSON | `acquiring:{env}:merchant:route:{merchantId}`；Value 为绑定、渠道、MID、能力和币种范围的聚合快照 | `{"merchantId":"200045","bindingCount":1,"routeOptions":[{"bindingId":11,"midConfigId":21,"channelMid":"***0045","channelCode":"MPGS","capabilityPaymentMethod":"CARD","supportedCurrencies":["USD"]}]}` | 永久，无 TTL；有效空路由允许保存 | Admin 修改商户、绑定、渠道、MID 或支付能力配置前创建 pending + Outbox；提交后精确删除；支付下一次选路从 MASTER 批量重建 | 数据库是事实源；渠道 MID 属受保护标识，日志必须掩码；渠道密码、API Key、证书、私钥、完整令牌和 metadata 明文不进入 Redis，实际敏感元数据仅在 JVM 内短时缓存 |
| 商户 OpenAPI IP 策略 | String，Spring Cache JSON | `acquiring:{env}:merchant:openapi:{merchantId}`；Key 为商户号，Value 为启用状态和允许 IP 集合 | `{"whitelistEnabled":true,"allowedIps":["203.0.113.10"]}` | 永久，无 TTL；不缓存异常 | 白名单新增、修改、启停、删除和策略开关变更均先创建 pending + Outbox；提交后精确删除 | 主库是事实源；pending/Redis 状态未知时查 MASTER；数据库读取失败不能使用旧允许策略静默放行 |
| 平台公开配置 | String，Spring Cache JSON | `acquiring:{env}:config:public:{configKey}`；仅允许四个 `platform.*.base-url`/`frontend-base-url` | `"https://gateway.example.com"` | 永久，无 TTL；空值不缓存 | Admin 保存或删除白名单配置时，在同一数据库事务登记 pending + Outbox；提交后精确删除 | 仅允许 `PlatformConfigCachePolicy` 中四个公开 URL；Secret、密钥和未登记配置不进入该缓存；pending 时从 MASTER 读取 |
| 国家字典 | String JSON 数组 | `acquiring:{env}:iso:country`；Key 固定，Value 为全部启用国家地区 | `[{"alpha2Code":"US","alpha3Code":"USA","currencyAlpha3Code":"USD"}]` | 永久，无 TTL；空结果不缓存 | 国家或区域币种管理变更后精确删除；下次查询从数据库重建 | Redis 异常或数据库空/失败时，本次请求可使用内置 ISO 数据，但禁止把兜底写回永久缓存 |
| 币种字典 | String JSON 数组 | `acquiring:{env}:iso:currency`；Key 固定，Value 为全部启用币种 | `[{"alpha3Code":"USD","numericCode":"840","fractionDigits":2}]` | 永久，无 TTL；空结果不缓存 | 币种或区域币种管理变更后精确删除；下次查询从数据库重建 | 同国家字典；金额仍使用 `BigDecimal`/最小单位，禁止浮点数 |
| 风控精确名单快照 | Hash | `acquiring:{env}:risk:{white\|black\|aml}:{function}:{merchantId\|GLOBAL}`；`@meta` 保存 generation/loaded/count，`{matchValueHash}` 字段保存最小规则 JSON | `@meta -> {"generation":"g-...","loaded":true,"count":2}`；`9f86... -> {"ruleId":17,"decisionAction":"REJECT"}` | 永久，无 TTL | Admin 对白名单、黑名单、AML 元素或规则执行新增、修改、启停、删除、导入、发布时，通过规则 Outbox 切换 generation；读路径发现代际不一致后从 MASTER 全量重建并由单 Key Lua 原子替换 Hash | 数据库是事实源；只存摘要，不存 PAN、邮箱、手机号等明文；单快照最多 5000 行、序列化字符最多 5 MiB，越界不写缓存并回退数据库点查 |
| 风控范围/地域名单快照 | String JSON 信封 | Key 同上一行；Value 为 `generation + loaded + count + rows`，用于风险名单自身配置的 IP/BIN 区间、国家、地区和来源主机 | `{"generation":"g-...","loaded":true,"count":1,"rows":[{"countryAlpha3":"USA","decisionAction":"REJECT"}]}` | 永久，无 TTL | 与精确名单共用 generation 发布和重建规则；有效空集合使用 `loaded=true,count=0` 表达 | 区间数据不使用 Set/Bloom；不包含公共 `base_card_bin_range` 发卡国家映射；Redis/代际不可用或容量越界时查 MASTER，风控不能默认 PASS |
| 商户来源网址规则 | String JSON 信封 | `acquiring:{env}:risk:source:{merchantId}`；Value 为全部启用 host 规则 | `{"generation":"g-...","loaded":true,"count":1,"rows":[{"sourceHost":"shop.example.com"}]}` | 永久，无 TTL | Admin 新增、修改、启停、删除或发布来源规则后切换 generation；按商户重建 | 只保存规范化 host，不保存协议、路径、查询参数或凭据；未命中允许列表按业务规则拒绝 |
| 商户限额规则 | String JSON 信封 | `acquiring:{env}:risk:limit:{merchantId}:{currency}`；Value 为单笔及累计限额规则集合 | `{"generation":"g-...","loaded":true,"count":2,"rows":[{"limitType":"SINGLE_MAX","amountMax":"1000.00","currency":"USD"}]}` | 永久，无 TTL | Admin 规则变更后切换 generation；首次交易按商户和币种重建 | 这里只缓存规则，不缓存商户余额或交易累计事实；金额序列化禁止 `double/float` |
| 3DS 规则 | String JSON 信封 | `acquiring:{env}:risk:3ds:{merchantId}`；Value 为商户全部启用 3DS 规则 | `{"generation":"g-...","loaded":true,"count":1,"rows":[{"paymentMethod":"CARD","triggerAction":"FORCE_3DS"}]}` | 永久，无 TTL | Admin 规则新增、修改、启停、删除或发布后切换 generation；按商户重建 | 数据库是事实源；按优先级、金额、币种、卡品牌和风险条件在内存匹配 |
| 频率规则配置 | String JSON 信封 | `acquiring:{env}:risk:frequency:{merchantId}`；Value 为可直接执行的规则集合 | `{"generation":"g-...","loaded":true,"count":1,"matches":[{"windowSeconds":300,"threshold":5}]}` | 永久，无 TTL | Admin 频率规则变更后切换 generation；按商户重建 | 这是规则配置，不是频率计数；真实窗口状态使用有 TTL 的 ZSet |

## 3. 不进入 Redis 的持久事实

| 数据 | 最终存储与写入方式 | 变更与恢复规则 | 说明 |
| --- | --- | --- | --- |
| 支付、退款、撤销等交易主单、操作单、状态历史和金额 | `service-payment` 在业务事务中同步写 MySQL | 数据库唯一约束、状态条件和终态保护；失败回滚业务事务 | Redis 不能作为资金或状态事实源，MQ 也不能延后核心事实落库 |
| 渠道请求、响应和回调 inbox | `service-payment` 同步写 MySQL 分表 | 以平台/渠道业务键幂等，保留恢复和审计所需原始事实 | 需要恢复的渠道交互不能只发 MQ；日志展示必须脱敏 |
| 商户通知任务和每次投递日志 | `service-payment` 同步创建/激活任务，`service-data` 以 CAS 更新并写尝试日志 | MySQL 状态、版本和固定 `notifyId` 兜底；`service-job` 触发到期补偿 | Redis 不保存回调 URL、通知正文或待消费消息；回调 URL 查询参数可能含令牌，只能在 MySQL 快照中保存 |
| 交易 Outbox | `service-payment` 与交易事实同事务写 MySQL | relay 发送后 CAS 更新，失败持续重试 | 不把 Outbox 镜像到 Redis |
| 操作日志、风控评估审计、OpenAPI 安全拦截审计 | 生产服务发送脱敏最小 MQ 消息，`service-data` 消费并写 MySQL | 数据库唯一键是最终幂等；Redis 只可使用短 TTL 消费去重标记 | Authorization、Cookie、请求体、密钥、卡数据和未脱敏个人信息禁止进入 MQ、Redis 和日志 |
| 风险累计限额预占 | 业务事务写 `merchant_limit_reservation`，Redis 只做有 TTL 的原子运行状态 | 数据库状态机、补偿任务和对账最终收敛 | Redis 预占不是最终资金或风险事实 |

## 4. 永久控制状态

| 名称 | Redis 类型 | Key / 数据 | 过期策略 | 变更与故障策略 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 全局 ID 状态 | Hash | `acquiring:{env}:global-id:state`；字段保存最后毫秒和序列 | 永久，无 TTL | 仅 Redis TIME + 单 Key Lua 修改；Redis 或状态恢复配置异常时停止发号 | Redis 是该分布式序列状态的强依赖；必须独立备份、ACL、HA 和误删告警 |
| 风控当前 generation | String | 当前兼容 Key：`acquiring:{env}:component-redis:cache:generation:v1:risk-runtime-rule:current` | 永久，无 TTL | 只能由规则 Outbox 发布；丢失或不可读时风控回源 MASTER | 这是存量兼容例外，尚未完成短 Key 双轨迁移，禁止手工覆盖 |

## 5. 临时门禁、负缓存与防重放

| 名称 | Redis 类型 | Key / 值 | TTL | 创建与移除策略 | 故障策略 |
| --- | --- | --- | --- | --- | --- |
| 商户资料失效门禁 | String token | `acquiring:{env}:merchant:info:pending:{merchantId}` | 默认 2 小时 | Admin 或 Merchant Portal 主库写事务内 `SET NX`；事务回滚或共享 Outbox 精确删除成功后按 token Lua 释放 | 门禁存在或状态未知时读 MASTER；TTL 只清理异常遗留，不替代 Outbox；OpenAPI、Admin 和 Merchant 读取遵循同一结论 |
| 密钥元数据失效门禁 | String token | `acquiring:{env}:merchant:keyMeta:pending:{merchantId}` | 默认 2 小时 | 密钥初始化、轮换、启停或商户删除前在主库事务内创建；Outbox 精确删除元数据后按 token 释放 | 门禁存在或状态未知时从 MASTER 读取元数据，禁止使用旧 revision 静默放行 |
| 商户路由失效门禁 | String token | `acquiring:{env}:merchant:route:pending:{merchantId}` | 默认 2 小时 | 商户、渠道、MID、绑定或能力配置变更前创建；Outbox 删除路由快照后释放 | 门禁存在或状态未知时从 MASTER 重建，不能继续使用旧路由或旧超时配置 |
| OpenAPI 策略失效门禁 | String token | `acquiring:{env}:merchant:openapi:pending:{merchantId}` | 默认 2 小时 | 同上 | 同上；不能因门禁故障使用旧允许策略 |
| 平台配置失效门禁 | String token | `acquiring:{env}:config:public:pending:{configKey}` | 默认 2 小时 | 仅四个公开配置写事务可创建；Outbox 完成后 token Lua 释放 | Merchant 读取端状态未知时查 MASTER；Admin 缓存监控必须隐藏并拒绝删除该 Key |
| 商户不存在 marker | String marker | `acquiring:{env}:merchant:runtime-profile-miss:{merchantId}` | 默认 30 秒 + 抖动 | 只有 MASTER 明确不存在才写；新增/更新商户时与正缓存一起可靠删除 | Redis 异常返回 `UNAVAILABLE` 并查数据库，不把异常固化为不存在 |
| BIN 发卡国家点查缓存 | String JSON 信封 | `acquiring:{env}:risk:runtime-rule:{generation}:card-bin:issuer-country:{binDigest}`；Key 只包含 BIN 摘要 | `{"found":true,"match":{"hitValueMasked":"USA"}}`；未命中为 `{"found":false}` | 命中默认 300 秒；未命中默认 60 秒 | 首次查询执行 `base_card_bin_range` 区间点查并缓存结构化结果；规则 generation 变化后自动隔离旧结果 | 不保存原始 BIN，不构建公共 BIN 全量快照；Redis、generation 或反序列化不可用时查询数据库，数据库异常不得缓存为未命中 |
| OpenAPI JWT 防重放 | String | `acquiring:{env}:security:openapi:jwt-replay:{merchantId}:{jtiDigest}` | JWT 剩余有效期 + 60 秒 | 首次合法请求 `SET NX EX`，自动过期 | `required=true` 时 Redis 缺失或异常 Fail Closed；不能只靠该 Key 代替业务幂等 |
| 风控规则发布门禁 | String token | 当前兼容 Key：`acquiring:{env}:component-redis:cache:generation:v1:risk-runtime-rule:publication` | 首次发布 30 分钟，恢复发布 30 秒 | Admin 发布开始时获取，事务回滚释放；提交后 Outbox 切 generation 并释放 | 门禁存在时 Risk 不读旧缓存，直接查询 MASTER |

## 6. 临时锁、计数、预占和去重

| 名称 | Redis 类型 | Key / 值示例 | TTL / 容量 | 更新与移除策略 | 最终事实与故障策略 |
| --- | --- | --- | --- | --- | --- |
| 支付操作锁 | String token | `acquiring:{env}:payment:lock:operation:{idempotencyDigest}` | 30 秒 | `SET NX EX` 获取，渠道 I/O 前按 token Lua 释放 | 数据库唯一约束/幂等记录兜底；锁失败查询既有结果或返回繁忙，不能继续临界区 |
| 商户订单流锁 | String token | `acquiring:{env}:payment:lock:merchant-order-flow:{orderDigest}` | 30 秒 | 同支付操作锁 | 同上 |
| MQ 辅助去重 | 双 ZSet | `acquiring:{env}:mq:dedup:{slotDigest}:{namespace}:{bucket}`；member 为消息摘要，score 为 Redis 时间 | 调用方 TTL，最大 30 天；默认每桶 100000 member | Lua 跨当前/前一桶查重、清理、`ZADD NX` 和设置 TTL；业务失败精确移除 | 数据库唯一约束是最终幂等；Redis/容量故障返回 `FALLBACK` 并继续数据库 |
| 累计限额总额 | String 定标整数 | 同槽目标：`acquiring:{env}:risk:merchant-limit:{scopeDigest}:total` | 周期结束 + 1 小时 | Lua 原子 reserve/rollback；从 MASTER 交易事实初始化 | 数据库预占状态机和对账最终收敛；Redis 异常进入 REVIEW，不静默 PASS |
| 累计限额预占 | String 定标整数 | `acquiring:{env}:risk:merchant-limit:{scopeDigest}:reservation:{transactionDigest}` | 与对应 aggregate 同生命周期 | `PREPARING -> RESERVED -> CONFIRMED/CANCELLED`；Lua NX 预占和回滚 | 数据库 `merchant_limit_reservation` 是补偿事实；MQ/扫描任务处理终态 |
| 频率滑动窗口 | ZSet | `acquiring:{env}:risk:frequency-window:{scopeDigest}`；member 为交易摘要，score 为 Redis TIME | 规则窗口；默认最大 2000 member | Lua 原子 trim、`ZADD NX`、`ZCARD`、`PEXPIRE` | Redis/脚本/容量异常返回 ERROR/REVIEW；生产切换前必须完成 SHADOW |

## 7. 兼容与退役 Key

| Key 家族 | 状态 | 当前代码行为 | 清理或迁移要求 |
| --- | --- | --- | --- |
| `payment:iso:country:all` | 退役 | 不再读取、写入或删除 | 所有实例升级后，在正确 Redis 数据库逐个确认并精确 `UNLINK`/`DEL`；禁止 `KEYS`、`FLUSHDB` 和跨环境误删 |
| `payment:iso:currency:all` | 退役 | 不再读取、写入或删除 | 同上 |
| `transaction:detail` | 退役 | 交易详情直查数据库 | 旧 Key 原 TTL 自然过期，不恢复缓存 |
| `risk:evaluation:detail` | 退役 | 风控审计时间线直查数据库 | 旧 Key 原 TTL 自然过期 |
| `acquiring:{env}:service-risk:risk:runtime-rule:v1:*` | 兼容 | `LEGACY` 使用请求级 300/60 秒缓存；`SHADOW` 比较旧路径与永久快照；`SNAPSHOT` 不再使用它做决策 | Java 默认仍为 `LEGACY`；UAT/生产必须先完成 SHADOW 命中和容量证据再切 `SNAPSHOT`，不能直接批量删除 |
| 风控累计限额/固定窗口历史 Key | 兼容 | 由 `LEGACY`/`SHADOW` 模式继续使用 | 覆盖完整最大周期并核对差异后，才允许切换 Cluster-safe/ZSet 路径并等待旧 TTL 到期 |

### 7.1 ISO 精确清理步骤

1. 确认所有运行实例已包含“只使用 `acquiring:{env}:iso:*`”的新代码。
2. 使用 `SCAN MATCH payment:iso:country:all COUNT 100` 和
   `SCAN MATCH payment:iso:currency:all COUNT 100` 只做存在性核对。
3. 核对当前连接的 Redis 集群、逻辑 DB 和环境归属。历史 Key 没有环境前缀，多个环境共享
   Redis 时必须先确认其真实消费者。
4. 记录备份/回滚窗口后，只对两个完整 Key 执行 `UNLINK`；Redis 版本或运维规范不允许时
   才使用精确 `DEL`。
5. 观察应用回源、错误率和新 ISO Key 重建结果；不得使用模式批量删除。

## 8. 敏感哈希遗留风险

名单表中的 `match_value_hash` 已避免把 PAN、邮箱、手机号等明文写入 Redis，但现有历史值
不能仅通过配置切换为带平台密钥的 HMAC。系统没有原始明文时，无法从普通 SHA-256 值反推
HMAC；直接切换会造成全部历史规则失配，属于 P0 风控风险。

后续迁移必须独立审批并满足：

1. 从受控原始来源重新导入，或在管理端下一次编辑时生成新 HMAC；禁止反推或导出敏感明文。
2. 数据库增加独立算法标识和新摘要列，不原地覆盖旧 `match_value_hash`。
3. 运行期双摘要读取并记录低基数差异，覆盖白名单、黑名单和 AML 全部功能。
4. Redis 快照元数据记录摘要算法，避免同一 Hash 混用两种 field 语义。
5. 全量回填、差异为零、回滚窗口结束后再停止旧摘要读取。

在上述迁移完成前，文档和代码不得宣称名单摘要已使用 HMAC。

## 9. 准入门禁

新增或修改任何 Key 家族时，Owner 必须提供：

1. 读取量、更新量、预计 Key 基数、P95/P99 Value 大小和热点模型。
2. 数据库事实源、允许陈旧时间、TTL/null TTL/jitter 和容量上限。
3. 数据库提交后的精确失效、generation 切换或自然到期路径。
4. Redis 超时、不可用、反序列化失败、门禁状态未知和 Key 丢失时的业务结果。
5. 重建、迁移、回滚、指标、告警、ACL 和下线清理方案。
6. 敏感数据评审；完整 PAN、CVV、密钥、私钥、完整 Token 和未脱敏个人数据禁止进入 Redis。
