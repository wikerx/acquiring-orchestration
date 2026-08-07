# Redis 风险评估

> 阶段 4-A 更新：`RG-P1-04`、`RG-P1-05`、`RG-P1-09` 已完成代码治理；
> `RG-P1-10` 已完成 test/uat/prod 前缀启动门禁但尚缺真实环境验收；`RG-P1-06`
> 仍因 `DefaultTyping.NON_FINAL` 保持开放。下文数量和场景保留阶段 1 的原始审计基线，
> 当前实施状态以 `03-redis-problem-list.md` 和 `09-redis-change-report.md` 为准。

## 1. 总体结论

当前 Redis 使用成熟度评定为 **L1.5（局部规范化、整体未治理）**：

* 已有统一前缀属性、Spring Cache TTL、事务感知、原子锁释放、MQ ZSet 去重和 Lua 金额整数化等正确基础。
* 但安全缓存失效、风控缓存失效、累计限额基线、Cluster 多 Key Lua 四个问题已经越过一般缓存质量边界。
* Key、序列化、TTL 抖动、故障策略、容量预算、监控与环境验收仍不完整。
* Redis 没有被发现作为余额、结算或支付最终结果的唯一事实源；数据库唯一约束仍为交易幂等兜底，这是当前风险未进一步放大的重要控制。

结论：**存在严重问题，存在明确风控绕过与安全授权陈旧风险；存在交易异常、性能和数据一致性问题。阶段 2 之前必须先评审 P0/P1 设计。**

## 2. 风险统计

| 等级 | 数量 | 主要主题 |
| --- | ---: | --- |
| P0 | 4 | 安全缓存删除失败、风控规则无失效、累计限额从库基线、防重放 Bean 缺失放行 |
| P1 | 10 | Cluster CROSSSLOT、累计额度补偿、Admin KEYS/任意值删除、反序列化、锁、固定窗口、交易状态缓存、环境默认值 |
| P2 | 13 | 时间线一致性、ISO Key、雪崩/击穿、通用 API、非原子 TTL、容量、高基数、敏感值、监控、穿透 |
| P3 | 6 | 重复配置、死声明、过宽封装、Key 规范、输入校验、测试缺口 |
| 合计 | 33 | 与问题清单 ID 一一对应 |

## 3. 分领域评估

| 领域 | 评级 | 已有控制 | 剩余风险 | 结论 |
| --- | --- | --- | --- | --- |
| 支付与资金 | 高 | 交易数据库和 `transaction_idempotency` 唯一约束仍是事实源；锁安全释放 | 交易状态缓存、锁 TTL、累计限额虚占与从库低估 | 未发现余额/结算 Redis 唯一存储，但 P0/P1 足以影响交易决策 |
| 风控 | 极高 | 查询异常多数回源，累计金额使用整数单位，异常返回 REVIEW | 管理变更无失效、SLAVE 基线、固定窗口、Cluster 脚本 | 存在可确认的临时绕过和错误拒绝窗口 |
| OpenAPI 安全 | 极高 | JWT jti 用摘要和 `SET NX EX`；连接异常可配置 Fail Closed | Bean 缺失直接放行；安全策略缓存 EVICT 被吞 | 必须在装配和失效两个层面收紧 |
| MQ 幂等 | 中 | Redis ZSet 原子 acquire；三类消费者均有 DB 唯一约束 | Redis 缺失放行、ZSet 容量无指标 | 当前不是资金 P0，但需容量和降级验收 |
| 缓存一致性 | 高 | CacheManager `transactionAware()`；部分写路径精确 evict | EVICT 吞异常、风控与时间线漏失效 | 事务提交顺序正确不等于最终失效可靠 |
| 性能与容量 | 高 | 有基础 TTL、Redis 连接池和 2 秒超时 | KEYS、无 jitter、无击穿保护、高基数、大 ZSet | 高并发和故障场景未验证 |
| 序列化与敏感信息 | 高 | Key 使用字符串、业务查询多为 JSON DTO/摘要 | broad Default Typing、IP 明文、Admin 任意查看 | Redis ACL 被突破或误授权时影响半径过大 |
| 运维与可观测性 | 高 | Admin INFO 页面和结构化失败日志 | 无已验证指标、ACL/拓扑/淘汰策略/备份证据 | 无法证明生产可观测和故障恢复能力 |

## 4. 关键故障场景

| 场景 | 当前行为 | 业务影响 | 目标行为 |
| --- | --- | --- | --- |
| Redis 短暂不可用，普通查询缓存 GET 失败 | 吞异常并回源数据库 | 流量同时打到 DB，可能雪崩 | 有界回源、超时、舱壁、命中/回源指标 |
| 商户被禁用时缓存 EVICT 失败 | DB 提交成功，旧缓存保留 | 已禁用商户可能继续通过运行时校验 | 可靠提交后失效；不能确认时安全阻断 |
| 新增黑名单/限制规则 | 无 `risk:runtime:*` 失效 | 旧 miss/pass 继续 60~300 秒 | 规则版本切换或精确失效原子发布 |
| OpenAPI required=true 但 Redis Bean 未装配 | 直接放行 | JWT 可重放 | 启动失败或请求 Fail Closed |
| Cluster 执行累计/频率 Lua | 多 Key 不同槽 | CROSSSLOT，风控不可用 | 同槽 Key + Cluster 集成测试 |
| 支付失败但累计额度已 reserve | 无跨服务补偿证据 | 额度虚占至周期结束 | confirm/cancel 状态机 + 对账 |
| Admin 按 `*` 查询 | Redis KEYS 全量执行 | 阻塞实例 | SCAN + namespace/游标/预算 |
| 大量 TTL 同时到期 | 无 jitter/加载保护 | Redis 与 DB 同时抖动 | 有界抖动、单 Key 加载、预热和回源限流 |

## 5. 支付与幂等专项判断

### 已确认的安全边界

* `transaction_idempotency` 存在唯一 `(idempotency_scope, idempotency_key)`，支付锁不是唯一幂等来源。
* Admin/Merchant 操作日志和风险审计消费者均有数据库唯一约束兜底。
* Redis 锁释放使用“value 相等才 DEL”的 Lua，没有直接无条件解锁。
* 累计金额进入 Lua 前以 6 位小数转换为整数单位，没有使用 `double/float` 或过早四舍五入。

### 仍需阻断的风险

* Redis 锁过期后数据库唯一约束只能防重复落同一幂等记录，不能自动证明所有渠道副作用都不会并发发生。
* 累计限额 Redis reservation 没有持久状态机，不能承担最终额度事实。
* MQ Redis 去重缺失时虽然 DB 唯一约束兜底，但必须验证异常是在副作用之前还是之后落库。
* 全局 ID Hash 无 TTL 是有意持久状态，不应被 Admin 通用删除接口触达。

## 6. 已确认事实与待核验项

| 类别 | 已确认 | 待核验 |
| --- | --- | --- |
| 依赖 | Boot 3.5.14、Data Redis 3.5.11、Lettuce 6.6.0；无 Jedis/Redisson | Redis Server 版本 |
| 拓扑 | dev 单机 127.0.0.1；部署文档要求 Cluster | test/uat/prod 实际单机/Sentinel/Cluster |
| 配置 | 代码默认前缀、TTL、2 秒超时和 dev pool | 外部 Nacos 真实前缀、ACL、TLS、淘汰策略 |
| 数据结构 | 当前源码中的 String/Hash/ZSet、6 个 Lua | 生产 Keyspace 是否还有仓库外历史 Key |
| 指标 | 有结构化错误日志和 Admin INFO | Actuator/Micrometer/Prometheus 是否真实启用 |
| 容量 | 无代码级 maximum size | 每类 Key 基数、value 大小、热点与内存占用 |
| 恢复 | DB 是多数缓存事实源 | Redis 备份、持久化、RTO/RPO、Cluster 故障转移 |

## 7. 阶段门禁

在进入代码整改前，至少完成以下评审：

1. P0 四项的业务 Owner、失败策略、回滚方案和验收用例。
2. test/uat/prod 真实 Redis 版本、拓扑、Nacos、ACL 和内存淘汰策略核验。
3. 累计限额 reserve-confirm-cancel 状态机与主库/事实源基线设计。
4. 安全缓存可靠失效与风控规则发布/失效设计。
5. 所有 6 个 Lua 的单机、Cluster、超时、失败重试与幂等测试方案。
6. Key 迁移兼容方案，禁止直接批量删旧 Key。

本轮到此只完成风险识别，不对运行中 Key、数据库、交易或风控流程做任何变更。
