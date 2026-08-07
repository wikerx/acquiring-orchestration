# Redis 数据结构映射

## 1. 选型结论

当前项目不需要“把 String 改成更多类型”作为目标。数据结构只由访问模式、原子边界、
容量和生命周期决定。

| 数据结构 | 当前活跃 | 目标决定 |
| --- | --- | --- |
| String | 23 个家族 | 继续承载单对象、短标识、锁和整数计数；减少交易实时状态和高基数查询 |
| Hash | 1 个家族 | 保留全局 ID 状态；不把不同 TTL 的商户配置强塞进 Hash |
| Set | 0 | 暂不新增；IP 区间/卡 BIN 区间不适合简单 Set |
| ZSet | 1 个家族 | 保留 MQ 去重；新增频率滑动窗口候选 |
| List | 0 | 不新增，不替代 RocketMQ |
| Stream | 0 | 不新增，现有 RocketMQ 已承担可靠事件 |
| Bitmap | 0 | 无明确大规模布尔状态需求，不新增 |
| HyperLogLog | 0 | 交易/财务指标要求精确，不新增 |
| Bloom Filter | 0 | 当前规模和依赖证据不足，不引入 |

## 2. 当前到目标映射

| 业务场景 | 当前结构 | 访问模式 | 目标结构 | 决策 |
| --- | --- | --- | --- | --- |
| 商户运行时资料 | Spring Cache String JSON | merchantId 单对象读 | Spring Cache String 明确 DTO | 保留，补可靠失效和短 null TTL |
| OpenAPI IP 策略 | Spring Cache String JSON | merchantId -> IP 集合 | Spring Cache String 最小 DTO | 不改 Hash/Set；先解决失效、安全查看和容量 |
| 平台配置 | Spring Cache String | configKey 单值 | Spring Cache String | 保留白名单内低频配置 |
| 交易详情 | Spring Cache String 大聚合 | transactionId 实时查询 | 无 Redis | 移除实时交易状态缓存 |
| 风控时间线 | Spring Cache String 列表 | transactionId 追加后查询 | 默认无 Redis；或短快照 String | 异步追加失效成本高，默认取消 |
| ISO 字典 | 两个固定 String JSON 列表 | 全量读 | Spring Cache String 或版本化 String | 二选一，禁止重复双缓存 |
| JWT replay | String | `SET NX EX` | String | 当前类型正确；required 统一 Fail Closed |
| 支付锁 | String | `SET NX EX` + compare-delete | String | 当前类型正确；统一 Key 与租约策略 |
| 全局 ID | Hash | HGET/HSET 两字段 + Lua | Hash | 生命周期一致且需字段原子更新，保留 |
| MQ 幂等 | ZSet | 时间分数、NX、清理 | 分桶/受控 ZSet | 适合时间窗口去重，补容量 |
| 风控列表/规则查询 | String JSON | 单 Key 查询结果 | 版本化 String JSON 规则 DTO | 保留，规则发布时精确失效/切版本 |
| 累计限额 aggregate | String integer | `INCRBY`、周期 TTL | 同槽 String integer | 已有同槽迁移路径；金额保持 6 位整数单位，仍缺持久状态机 |
| 累计限额 reservation | String integer | transaction NX marker | 同槽 String marker | 已有共同摘要 Hash Tag；生命周期仍需 confirm/cancel 补偿 |
| 固定窗口频率 | 两个 String | counter + transaction marker | 单 Key ZSet | 阶段 4-B 先完成两个 String 同槽；阶段 7 再迁移滑动窗口 |
| 通用业务去重 | 未活跃 Set | SADD/EXPIRE | 暂不启用 | 先证明需求；启用前修原子 TTL |
| 订单号/STAN | 未活跃 String counter | INCR/DECR + TTL | 单 Key Lua String counter | 若确认使用，先原子化边界和 TTL |

## 3. String 使用边界

保留：

* 明确 DTO 的单对象查询缓存。
* `SET NX EX` 的短期防重放和锁。
* 以整数最小单位存储的累计金额计数。
* 低基数、正 TTL、可重建的短标识。

禁止：

* 交易实时状态、支付成功、渠道最终结果、余额和结算结果。
* `INCR` 后另行 `EXPIRE` 的新实现。
* 无 Owner、无 TTL、无最大基数的业务 Key。
* 使用 `double/float` 保存或计算金额。

## 4. Hash 使用边界

全局 ID 的 `last_millis` 和 `sequence` 生命周期一致、总字段数固定、需要同 Key Lua，
因此 Hash 合理。该 Key 无 TTL 是受控的持久状态例外，必须：

* 独立 namespace 和 ACL。
* 禁止 Admin 通用删除或查看。
* 备份并验证恢复后唯一性。
* 非 dev 环境强制显式 state Key。

不建议把商户全部配置放进一个大 Hash：不同字段敏感性、更新频率和 TTL 不一致，且会
扩大跨服务耦合。累计限额也不默认迁移为单个大 Hash，避免每周期高交易量形成大量
reservation field。

## 5. Set 使用边界

当前没有活跃 Set 家族。以下场景暂不迁移：

* IP 白名单同时涉及启用状态、IP/CIDR 和策略 DTO，不是纯成员集合。
* 风控黑白名单包含范围、优先级、动作、有效期和商户范围，Set 不能表达完整规则。
* 卡 BIN 和 IP 区间不能用简单 Set 做范围匹配。

将来只有纯精确成员关系、数据库可重建、成员已摘要化、容量受控时才登记 Set。

## 6. ZSet 设计

### 6.1 MQ 去重

保留 ZSet，因为需要按时间清理和 `ZADD NX`。目标约束：

* 按 namespace 和时间桶控制大 Key。
* Lua 原子清理、写入和 TTL。
* DB 唯一约束继续作为最终幂等。
* 监控 member 数、bytes、清理量和重复命中。

### 6.2 频率滑动窗口

目标单 Key：

```text
acquiring:{environment}:risk:frequency:{ruleScopeDigest}
```

目标 Lua 原子步骤：

```text
ZREMRANGEBYSCORE key -inf windowStart
ZADD key NX nowMillis transactionDigest
ZCARD key
EXPIRE key windowSeconds + buffer
返回 count 与是否新增
```

单个 transaction digest 作为 member，重复评估不重复计数。必须设置：

* 最大允许窗口、阈值和 member 数。
* 对时钟来源的统一约束。
* 达到容量上限时的 Fail Closed/REVIEW 策略。
* Cluster、并发、边界时间和重复 transaction 测试。

## 7. 不引入的数据结构

| 结构 | 不引入原因 | 重新评估条件 |
| --- | --- | --- |
| List | 项目已有 RocketMQ；可靠事件需要 ACK、重试和持久消费语义 | 仅固定长度非可靠最近记录 |
| Stream | 没有替代 RocketMQ 的收益证据，会增加双消息体系 | 有独立小规模事件流、消费者组和运维 Owner |
| Bitmap | 没有大规模连续 ID 布尔状态 | 出现明确日活/布尔打点且无需明细 |
| HyperLogLog | 支付与财务指标要求精确 | 仅非财务近似 UV 且误差可接受 |
| Bloom | 无 RedisBloom/Redisson 依赖，穿透规模未证明 | 空值缓存和限流不足，规模、误判、重建方案完整 |

## 8. Lua 清单与目标

| 脚本 | 当前 Key 数 | 当前状态 | 目标 |
| --- | ---: | --- | --- |
| 锁释放 | 1 | compare-and-delete 正确 | 保留，资源化并加指标 |
| 全局 ID | 1 Hash | 单 Key，Cluster 可执行 | 保留，恢复和 HA 验收 |
| MQ 去重 | 1 ZSet | 原子清理 + NX + TTL | 保留，分桶/容量 |
| 累计限额 reserve | 2 String | 同槽路径已实现；默认 `LEGACY`，`SHADOW` 可双写 | 完整周期观察后切换，继续补状态机 |
| 累计限额 rollback | 2 String | 同槽路径已实现并通过真实 Cluster 测试 | 与 reserve 同一迁移和补偿门禁 |
| 频率计数 | 2 String | 同槽路径已实现并通过真实 Cluster 测试；仍为固定窗口 | 完成切换后再评估单 ZSet 滑动窗口 |

任何 Lua 变更都必须验证空 Key、重复请求、超时重试、返回值解析、脚本缓存丢失、
Cluster failover 和业务回滚，不能只做单机 happy path 单元测试。

## 9. 阶段 4-B 同槽迁移结构

新物理 Key 使用精简命名，不包含服务名和默认版本号。下列 `scopeDigest` 是组件根据稳定
业务范围生成的 SHA-256 Redis Hash Tag，原始商户号和交易号不进入 Hash Tag：

```text
acquiring:{environment}:risk:merchant-limit:{scopeDigest}:total
acquiring:{environment}:risk:merchant-limit:{scopeDigest}:reservation:{transactionDigest}
acquiring:{environment}:risk:frequency:{scopeDigest}:count
acquiring:{environment}:risk:frequency:{scopeDigest}:transaction:{transactionDigest}
```

| 模式 | 读写行为 | 决策来源 | 用途 |
| --- | --- | --- | --- |
| `LEGACY` | 只写历史 Key | 历史 Key | 默认兼容和紧急回退 |
| `SHADOW` | 历史与同槽 Key 双写 | 历史 Key | 完整周期观察差异；同槽写失败不改变业务判断 |
| `CLUSTER_SAFE` | 只写同槽 Key | 同槽 Key | 观察完成后的目标模式；必须显式确认切换 |

当前真实 Redis 6 Cluster 已验证同一次 Lua 的两个 Key 同槽且没有 `CROSSSLOT`。尚未覆盖
Cluster failover、生产拓扑和完整业务周期 SHADOW 数据，因此生产仍保持 `LEGACY`。
