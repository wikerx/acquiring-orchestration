# Redis 故障演练报告

## 1. 演练结论

阶段 9 已在临时 Redis 6 和六节点 Redis 6 Cluster 中完成连接拒绝、Lua 类型错误、容量
上限、锁持有者和租约超时演练。所有已执行场景均符合既定 Failure Policy：

* MQ 去重失效时继续数据库最终幂等；
* 风控频率异常时进入 `ERROR/REVIEW`，不静默放行；
* 全局 ID 失效时明确失败，禁止 JVM 本地发号；
* Redis 锁只允许持有者释放，持有者消失后依赖有限 TTL 恢复。

阶段 10 又在完全隔离的容器内补充 LFU 淘汰、无持久化重启和六节点 Cluster 主节点停止。
这些结果验证本地演练脚本和预期失败边界，不等同于生产 Cluster/Sentinel failover，也不
构成灾备验收。

## 2. 演练环境与保护措施

| 项目 | 说明 |
| --- | --- |
| Redis | 一次性 `redis:6` 容器；Cluster 为 6 节点、3 主 3 从 |
| 数据 | 随机测试前缀、SHA-256 摘要和虚构交易号 |
| 清理 | 只删除精确测试 Key；容器退出时按唯一名称删除 |
| 禁止操作 | 未执行 `KEYS`、`FLUSHDB`、SQL、共享 Redis/Nacos 修改 |
| 连接拒绝 | 先删除测试容器，再连接同一已释放本机端口 |

## 3. 已执行场景

| 场景 | 注入方式 | 预期策略 | 实际结果 | 结论 |
| --- | --- | --- | --- | --- |
| Redis 连接拒绝：MQ | 容器停止后执行生产双桶获取 | 返回 `FALLBACK`，交给数据库唯一约束 | `FALLBACK` | 通过 |
| Redis 连接拒绝：全局 ID | 容器停止后调用 `nextId()` | 抛出业务异常，不本地降级 | `Redis TIME` 失败，无编号 | 通过 |
| Lua `WRONGTYPE` | 把频率 ZSet Key 预置为 String | 风控 `ERROR/REVIEW` | `ERROR/REVIEW`，污染值未覆盖 | 通过 |
| 频率容量超限 | 最大 member 设为 2，写入第三个摘要 | `ERROR/REVIEW`，不继续增长 | ZSet 保持 2 个成员 | 通过 |
| MQ 容量超限 | 每桶上限设为 2，写入第三个摘要 | `FALLBACK`，已有重复仍可识别 | 符合预期 | 通过 |
| 非持有者解锁 | `holder-B` 释放 `holder-A` 的锁 | compare-delete 不删除 | 锁值仍为 `holder-A` | 通过 |
| 锁租约超时 | 1 秒 TTL 且持有者不释放 | TTL 后竞争者恢复获取 | 3 秒窗口内获取成功 | 通过 |
| Cluster 同槽 | 累计限额多 Key 与单 ZSet 生产 Lua | 无 `CROSSSLOT` | 5 个 Cluster 用例通过 | 通过 |

## 4. 阶段 10 隔离演练

执行入口：

```bash
./scripts/run-redis-stage10-drills.sh
```

最新证据目录：`target/redis-readiness/stage10/20260731T062255Z/`。

| 场景 | 注入方式 | 实际结果 | 结论边界 |
| --- | --- | --- | --- |
| LFU 内存淘汰 | 单机容器设置 8 MiB、`allkeys-lfu` 后持续写入 | `evicted_keys=1784` | 淘汰注入与观测通过；未验证业务强依赖 Key 的生产保护 |
| 大 Key | 写入 1 MiB 测试 String 后运行 `--bigkeys` | 精确定位测试 Key | 仅证明隔离 Keyspace 的检测入口可用 |
| 热 Key | 对单个测试 Key 重复读取后运行 `--hotkeys` | counter 41，排名第一 | 仅证明 LFU 热 Key 采样链路可用 |
| 无持久化重启 | `appendonly no`、`save ""`，重启一次性容器 | 重启后测试 Key 不存在 | 明确无持久化时数据会丢失，不代表恢复通过 |
| 六节点 Cluster failover | 停止目标 slot 的 master 进程 | 副本提升后原测试 Key 可读取 | 本地单容器六端口模型通过；生产拓扑仍待验 |
| 有界持续负载 | SET/GET 各 100000 请求 | 命令完成并保留 `INFO` | 只作本地脚本稳定性证据 |

脚本使用唯一容器名称和 `acquiring:it-stage10` 测试 Key，结束时删除容器；摘要明确记录
`shared_or_external_redis_accessed=false`，未连接共享 Redis、Nacos 或 MySQL。

## 5. 业务失败边界

### 5.1 MQ 去重

`ACQUIRED` 只表示 Redis 辅助层取得处理资格。只有该状态且业务持久化失败时才释放 Redis；
`DUPLICATE` 直接跳过；`FALLBACK` 必须继续到数据库唯一约束。Admin、Merchant、Risk
消费者均已按该三态处理。

### 5.2 风控

滑动窗口脚本返回空、负容量码、连接异常或 Lua 异常时，规则明细为 `ERROR` 且决策动作
为 `REVIEW`。这可以阻止 Redis 异常被转换为 PASS，但最终 REVIEW 的运营处置仍需部署
环境确认。

### 5.3 全局 ID

Redis `TIME`、状态 Hash 或 Lua 任一失败都会抛出业务异常。恢复状态必须同时配置
`restore-acknowledged=true` 和正数 `restore-floor-epoch-millis`；正常环境禁止启用。

### 5.4 支付锁

Redis 锁只覆盖本地准备事务，渠道 I/O 前已经释放；数据库幂等记录、唯一约束、行锁和
状态机仍承担最终副作用保护。解锁异常记录告警并依赖 30 秒 TTL，不能反向撤销已经提交
的准备事务。

## 6. 未执行场景

受当前环境限制，以下生产级场景仍是准入阻断项：

1. UAT/生产等价 Sentinel 或托管 Cluster 故障切换、客户端拓扑刷新和跨实例业务恢复。
2. replica lag、网络抖动、命令超时、跨可用区分区和 DNS 故障。
3. 真实容量下的 `maxmemory` 淘汰影响、磁盘/AOF/RDB 故障和持久状态恢复；本地无持久化
   数据丢失结果不能替代。
4. 安全缓存删除失败、规则发布 Outbox 长时间积压、重试耗尽以及告警通知和恢复闭环。
5. 数据库回源过载、连接池耗尽以及 Redis 与数据库同时故障。
6. 支付实例在持锁期间进程退出、准备事务超过租约和大规模多实例锁竞争。

这些场景需要 UAT 运维权限、真实拓扑和受控演练窗口。未完成前不得批准生产模式切换。
