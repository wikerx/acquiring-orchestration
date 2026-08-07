# Redis 技术版本与扫描基线

## 1. 扫描结论

本报告对应 `REDIS-GOVERNANCE-001` 阶段 0，扫描对象为当前工作树，而不是仅扫描 `HEAD`。

> 本文件记录阶段 0 的扫描时状态，不代表治理后的当前实现。2026-08-01 已完成 dev Redis
> 6.2.23 Cluster（3 Master + 3 Replica、DB 0）接入，Redisson 3.50.0 已作为统一业务锁入口；
> 当前 Key、数据结构和生命周期以 `07-redis-cache-catalog.md` 为准。

| 项目 | 结论 | 证据 |
| --- | --- | --- |
| 扫描日期 | 2026-07-29（Asia/Shanghai） | 本轮扫描时间 |
| 仓库 | `acquiring-orchestration` | 当前仓库根目录 |
| 分支 | `feature_scott_payment` | `git rev-parse --abbrev-ref HEAD` |
| HEAD | `691becd9d443160d92deb4407763393248f10ceb` | `git rev-parse HEAD` |
| 工作树 | Dirty，包含 Redis、Cache、风控、支付等未提交修改 | `git status --short` |
| 扫描口径 | 当前工作树全部后端源码、配置、SQL、测试与文档 | 包含已跟踪修改和未跟踪文件 |

本轮没有回退、覆盖或整理现有未提交修改。后续审阅本报告时，应以本文件记录的提交和 dirty 状态作为可复现边界。

## 2. 依赖版本

| 组件 | 当前版本 | 确认方式 |
| --- | --- | --- |
| Spring Boot | `3.5.14` | 根 `pom.xml`；Maven `help:evaluate` |
| Spring Framework | `6.2.18` | Maven 依赖树中的 `spring-context`、`spring-tx`、`spring-core` |
| Spring Data Redis | `3.5.11` | Maven 依赖树 |
| Lettuce | `6.6.0.RELEASE` | Maven 依赖树 |
| Jedis | 未引入 | Maven 依赖树和全仓扫描 |
| Redisson | 未引入 | Maven 依赖树和全仓扫描 |
| Redis Server | 无法从仓库确认 | 没有镜像、Helm、Compose 或服务端版本锁定证据 |

复核命令：

```bash
mvn -pl component-library/component-redis dependency:tree \
  -Dincludes=org.springframework.data:spring-data-redis,io.lettuce:lettuce-core,redis.clients:jedis,org.redisson:redisson

mvn -pl component-library/component-redis dependency:tree \
  -Dincludes=org.springframework:spring-core,org.springframework:spring-context,org.springframework:spring-tx
```

## 3. Redis 部署模式

| 环境 | 仓库内可确认状态 | 结论 |
| --- | --- | --- |
| dev | `docs/deployment/nacos/redis-dev.yaml` 配置 `127.0.0.1:6379` 单机、DB 0 | 已确认是单机开发配置 |
| test | 仅看到服务从外部 Nacos 导入 `redis-test.yaml` | 未验证 |
| uat | 仅看到服务从外部 Nacos 导入 `redis-uat.yaml` | 未验证 |
| prod | 仅看到服务从外部 Nacos 导入 `redis-prod.yaml` | 未验证 |
| 部署规范 | `docs/deployment/nacos/README.md` 声明 Redis 按 Cluster 配置 | 目标/规范声明，不等于实际环境证据 |

当前存在“dev 单机实现证据”和“生产按 Cluster 的文档声明”，但没有 test/uat/prod 的真实 Nacos 内容、Redis `INFO`、`CLUSTER INFO` 或部署清单。本报告因此按“必须兼容 Cluster”评估多 Key Lua，同时将生产部署状态标记为待环境核验。

## 4. 已使用能力

| 能力 | 状态 | 主要位置 |
| --- | --- | --- |
| Spring Cache | 已使用 | `PaymentRedisCacheAutoConfiguration` 及 10 个缓存注解 |
| `@EnableCaching` | 已配置 | `PaymentRedisCacheAutoConfiguration` |
| `RedisCacheManager` | 已配置 | `PaymentRedisCacheAutoConfiguration.redisCacheManager` |
| `RedisTemplate<String, Object>` | 已使用 | `RedisTemplateConfig` 和通用数据结构封装 |
| `StringRedisTemplate` | 已使用 | 防重放、幂等、锁、ID、ISO、风控、Admin 监控 |
| Lua / `DefaultRedisScript` | 已使用 | 6 个脚本 |
| Redis Hash | 已使用 | 全局 ID 原子状态 |
| Redis ZSet | 已使用 | MQ 消费幂等桶 |
| Redis Set | 仅有未被业务调用的去重能力 | `RedisDeduplicationServiceImpl` |
| Redis List | 仅有未被业务调用的通用封装 | `RedisListServiceImpl` |
| Reactive Redis | 未发现 | 全仓扫描 |
| Redis Stream | 未发现 | 全仓扫描 |
| Redis Pub/Sub | 未发现 | 全仓扫描 |
| Bitmap | 未发现 | 全仓扫描 |
| HyperLogLog | 未发现 | 全仓扫描 |
| Bloom Filter | 未发现 | 全仓扫描 |
| Redisson | 未发现 | 依赖树和全仓扫描 |

## 5. CacheManager 基线

| 配置项 | 当前状态 | 评价 |
| --- | --- | --- |
| Key Serializer | `StringRedisSerializer.UTF_8` | 符合基线 |
| Value Serializer | `GenericJackson2JsonRedisSerializer` | 使用 Default Typing，存在安全问题 |
| 默认 TTL | 10 分钟 | 已配置 |
| 分缓存 TTL | 3、5、10、30 分钟 | 已配置 |
| Cache Key Prefix | 默认 `acquiring:local:cache:`；dev 为 `acquiring:dev:cache:` | 有环境前缀，缺服务和版本维度 |
| Null Value Cache | 全局关闭 | 不会缓存 null；个别不存在查询仍可能穿透 |
| Transaction Aware | 已开启 | 缓存 put/evict 在活动事务完成后执行 |
| TTL Jitter | 未配置 | 存在同批过期风险 |
| 单 Key 并发加载 | 未配置 | 没有 `sync=true`、逻辑过期或回源限流 |
| Cache Error Handler | GET/PUT/EVICT/CLEAR 全部记录后吞异常 | 普通查询可回源；安全缓存 EVICT 失败会保留旧值 |

## 6. 连接与可观测性基线

dev 配置可确认：

| 项目 | 当前值 |
| --- | --- |
| Redis 操作超时 | 2 秒 |
| Lettuce `max-active` | 32 |
| Lettuce `max-idle` | 16 |
| Lettuce `min-idle` | 4 |
| Lettuce `max-wait` | 2 秒 |
| Redis 重试 | 未发现统一 Redis 命令重试策略 |
| Redis 健康检查 | 未发现可验证的 Actuator 依赖和专项健康策略 |
| Redis 指标 | 未发现缓存命中率、锁等待、Lua 失败、超时等业务指标 |
| 管理查询 | Admin 自建 `INFO`、Key、Value、Delete 接口 |

`docs/deployment/nacos/common-dev.yaml` 配置了 `health,info,prometheus` 暴露范围，但各 `pom.xml` 未发现直接引入 `spring-boot-starter-actuator` 或 Prometheus registry，不能据此认定指标已实际可用。

## 7. 基线门禁

进入阶段 2 前必须补齐以下外部证据：

1. test/uat/prod 的 Redis Server 版本、拓扑和 Cluster 状态。
2. test/uat/prod 的真实 Nacos `redis-{env}.yaml`，尤其是 Key 前缀和全局 ID `state-key`。
3. Redis ACL、TLS、备份、持久化、淘汰策略和最大内存策略。
4. Actuator、Micrometer、Prometheus 的真实依赖和端点验证。
5. Cluster 环境执行全部多 Key Lua 的集成测试。

在这些证据补齐前，不得把“文档声明为 Cluster”视为“代码已经通过 Cluster 验证”。
