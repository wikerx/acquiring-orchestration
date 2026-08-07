# Redis 监控与告警处置手册

## 1. 适用范围

本手册覆盖 `REDIS-GOVERNANCE-001` 的应用侧 Redis 指标、Redis 服务端指标、缓存失效
Outbox 和风控 shadow 切换门禁。规则文件为
`docs/deployment/prometheus/redis-alert-rules.yml`。

应用侧指标由 Spring Boot Actuator 和 Micrometer 提供；内存、碎片率、Key 数量、淘汰、
慢日志等服务端指标必须由 `redis_exporter` 或云 Redis 等价监控提供。应用指标不能替代
服务端监控。

## 2. 采集前置条件

1. 仅允许 Prometheus 从受控内网采集 `/actuator/prometheus`，不得直接暴露到公网。
2. Prometheus 抓取目标必须带稳定的 `job`、`instance` 标签；应用自动增加
   `application` 和 `environment` 标签。`redis_exporter` 的 job 必须使用
   `redis-exporter` 前缀，使 exporter 自身采集失败与 `redis_up=0` 的 Redis 实例不可达
   能分别告警。
3. `redis_exporter` 使用只读、最小权限账号；凭据进入密钥管理系统，不写入仓库。
4. UAT 和生产必须分别加载告警规则，禁止跨环境合并告警状态。
5. 首次上线先观察至少一个完整业务周期，再由服务 Owner 审核阈值。

## 3. 应用指标

| 指标 | 维度 | 用途 |
| --- | --- | --- |
| `acquiring.redis.operation.duration` | `feature`、`operation`、`outcome` | Redis 业务操作次数、结果和 P95/P99 |
| `acquiring.redis.fallback` | `feature`、`reason` | 缓存回源、MQ 去重降级等次数 |
| `acquiring.redis.lua.failure` | `script`、`failure` | Lua 连接、超时、返回值和执行失败 |
| `cache.gets` | `cache`、`result` | Spring Cache hit/miss |
| `lettuce.command.completion` | Lettuce 固定维度 | Redis 命令端到端延迟 |
| `acquiring.redis.cache.invalidation.outbox.*` | `outbox`、`outcome` | 安全缓存失效积压、批次饱和和失败 |

指标禁止包含 Redis Key、Value、merchantId、storeId、订单号、messageId、traceId、
requestId、异常正文或其他无界业务值。代码中的敏感标签过滤器和枚举标签测试属于发布
门禁，不得删除。

## 4. 告警处置

### 4.1 Redis 不可达、超时或业务降级

1. 核对 `redis_up`、Lettuce P99、连接数和服务实例错误率，确认是单节点、单实例还是集群问题。
2. 风控、全局 ID、锁和缓存失效门禁保持既定 fail-closed 策略，不得临时改为静默放行。
3. 普通查询缓存回源时同时观察数据库连接池、慢 SQL 和 QPS，必要时启用入口限流。
4. MQ 去重发生 `fallback` 时确认数据库唯一约束仍生效，并核对重复消费记录。
5. 故障恢复后检查告警归零、Outbox 消化和 shadow 差异，再决定是否恢复灰度。

### 4.2 缓存失效 Outbox 积压

1. 核对 `merchant_security` 或 `risk_rule` 的 due batch、饱和次数和失败事件计数。
2. 检查数据库查询、Redis 删除或 generation Lua 的失败日志；日志中不得输出门禁 token。
3. 不得跳过 Outbox 直接删除门禁，也不得使用 `KEYS`、`FLUSHDB` 或批量删除恢复。
4. 修复依赖后让中继任务按原事件重试，确认 due batch 回到 0。
5. 门禁仍存在时，安全配置读路径继续回源 MASTER；禁止为了降低数据库压力绕过门禁。

### 4.3 风控 shadow 差异或不可用

1. 任一 `mismatched` 或 `unavailable` 告警立即冻结 `SHADOW -> CLUSTER_SAFE` 和
   `LEGACY -> SLIDING_WINDOW` 切换。
2. 使用脱敏摘要定位对应规则和周期，核对数据库事实、旧路径和新路径，不从指标标签反查业务标识。
3. 修复后重新开始完整观察周期；不能把故障前后两个不连续窗口拼成验收周期。

### 4.4 内存、淘汰、热 Key 和大 Key

1. 淘汰发生后先确认是否影响全局 ID、锁、幂等和安全门禁等非普通缓存 Key。
2. 热 Key、大 Key 只能在 UAT、只读副本或获批生产窗口执行采样；优先使用云监控、
   Redis Insight、`redis-cli --hotkeys` 或 `redis-cli --bigkeys`。
3. 扫描必须限速并记录目标实例、开始结束时间和影响；禁止使用 `KEYS`。
4. 根据 Cache Catalog 核对 TTL、最大成员数和 Owner，不能仅靠扩大内存掩盖无界结构。

## 5. 生产准入证据

生产准入至少保留以下证据：

1. Prometheus targets 和规则加载成功截图或 API 输出。
2. 应用指标、Lettuce 指标与 `redis_exporter` 指标的样例查询结果。
3. 一次受控测试告警的触发、通知、确认和恢复时间。
4. 完整 shadow 周期内比较分母、差异数和不可用数。
5. Outbox 积压注入及恢复结果。
6. failover、replica lag、重启、淘汰和数据库回源过载演练记录。
7. 热 Key、大 Key 和长稳压测结果及容量余量。

没有真实环境证据的项目必须标记“未执行”，不得使用本地 Docker 结果替代。
