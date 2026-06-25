# 全系统统一唯一标识生成规范

本文档记录全系统统一唯一标识生成组件的使用边界和现有替换点扫描清单。

## 1. 编号格式

统一编号固定为 22 位纯数字：

```text
yyMMddHHmmssSSS + SSSSSS + C
```

含义：

| 片段 | 长度 | 说明 |
|---|---:|---|
| `yyMMddHHmmssSSS` | 15 | 毫秒时间片 |
| `SSSSSS` | 6 | 当前毫秒内递增序列 |
| `C` | 1 | Luhn Mod 10 校验位 |

示例：

```text
2606241530181230000458
```

## 2. 使用边界

生产、UAT、测试环境的分布式服务必须使用 `RedisGlobalIdGenerator`。

本地开发、单元测试、离线工具可使用 `LocalGlobalIdGenerator`，但该实现只保证单 JVM 内唯一，禁止用于生产支付资金链路。

禁止在资金链路继续新增以下编号方式：

```text
UUID.randomUUID()
new Random()
System.currentTimeMillis() 手工拼接
数据库自增 ID 作为业务编号
带业务字母前缀的支付订单号
```

Redis 不可用、Lua 脚本执行失败、Redis TIME 获取失败、序列溢出超过重试次数、生成结果格式异常时，必须抛出 `ServiceException`，不允许降级到本地生成。

## 3. 推荐注入方式

```java
private final GlobalIdGenerator globalIdGenerator;

public PaymentApplicationService(GlobalIdGenerator globalIdGenerator) {
    this.globalIdGenerator = globalIdGenerator;
}

public void createTransaction() {
    String transactionId = globalIdGenerator.nextId();
}
```

## 4. 配置示例

```yaml
payment:
  global-id:
    enabled: true
    mode: redis
    timezone: Asia/Shanghai
    sequence-length: 6
    max-sequence: 999999
    seq-key-prefix: "biz:{global_id}:seq:"
    last-millis-key: "biz:{global_id}:last_millis"
    seq-key-expire-seconds: 172800
    max-retry-times: 3
    retry-sleep-millis: 1
```

`prod` 和 `uat` profile 禁止配置 `payment.global-id.mode=local`。

## 5. 编号生成替换点扫描清单

| 模块 | 文件 | 当前生成方式 | 建议替换为 | 是否建议本次替换 |
|---|---|---|---|---|
| `component-core` | `component-library/component-core/src/main/java/com/scott/payment/component/core/util/identity/PaymentOrderNoGenerator.java` | `Clock` + `AtomicInteger` + 业务前缀，单 JVM 风格 | 后续废弃或改为包装 `GlobalIdGenerator` | 否，需评估所有调用方字段格式兼容 |
| `component-redis` | `component-library/component-redis/src/main/java/com/scott/payment/component/redis/identity/impl/RedisOrderNoGeneratorImpl.java` | Redis `INCR` + JVM 本机时间 + 业务前缀 | `GlobalIdGenerator.nextId()` | 否，需确认旧订单号前缀是否被页面、日志、渠道映射依赖 |
| `component-redis` | `component-library/component-redis/src/main/java/com/scott/payment/component/redis/identity/impl/RedisIdentityServiceImpl.java` | 间接调用 `RedisOrderNoGenerator` | `GlobalIdGenerator.nextId()` | 否，需先明确 `nextIdentityId` 与 STAN 的职责边界 |
| `component-redis` | `component-library/component-redis/src/main/java/com/scott/payment/component/redis/dedup/impl/RedisDeduplicationServiceImpl.java` | `PaymentOrderNoGenerator.nextOrderNo("ARN")` + Redis Set 去重 | 新增 ARN 专用规则或使用 `GlobalIdGenerator` 后映射 ARN | 否，ARN 可能有渠道或卡组织格式约束 |
| `service-payment` | `service-payment/src/main/java/com/scott/payment/payment/service/impl/PaymentTransactionServiceImpl.java` | `PaymentOrderNoGenerator.nextOrderNo("PA")` | 注入 `GlobalIdGenerator.nextId()` | 否，当前支付核心仍是模拟实现，不在本次大范围替换 |
| `service-payout` | `service-payout/src/main/java/com/scott/payment/payout/service/impl/PayoutTransactionServiceImpl.java` | `PaymentOrderNoGenerator.nextOrderNo("PO")` | 注入 `GlobalIdGenerator.nextId()` | 否，当前代付核心仍是模拟实现，不在本次大范围替换 |
| `service-openapi` | `service-openapi/src/main/java/com/scott/payment/openapi/service/impl/PaymentServiceImpl.java` | 远程关闭时本地模拟 `PaymentOrderNoGenerator.nextOrderNo("PA")` | 本地模拟分支如保留，应注入 `GlobalIdGenerator` | 否，需同步评估 `remoteEnabled=false` 的环境边界 |
| `service-openapi` | `service-openapi/src/main/java/com/scott/payment/openapi/service/impl/PayoutServiceImpl.java` | 远程关闭时本地模拟 `PaymentOrderNoGenerator.nextOrderNo("PO")` | 本地模拟分支如保留，应注入 `GlobalIdGenerator` | 否，需同步评估 `remoteEnabled=false` 的环境边界 |
| `service-job` | `service-job/src/main/java/com/scott/payment/job/application/ShardingTablePreCreateApplicationService.java` | `UUID.randomUUID()` 生成手工任务 `runId` | 可继续使用 UUID，或按任务流水治理时切换 `GlobalIdGenerator` | 否，非资金业务编号 |
| `service-job` | `service-job/src/main/java/com/scott/payment/job/executor/JobDispatchService.java` | `UUID.randomUUID().toString().replace("-", "")` 生成任务 `runId` | 可继续使用 UUID，或按任务流水治理时切换 `GlobalIdGenerator` | 否，非资金业务编号 |
| `service-job` | `service-job/src/main/java/com/scott/payment/job/service/impl/ShardingTablePreCreateServiceImpl.java` | `"manual-" + System.currentTimeMillis()` 生成 `batchNo` | 后续可使用 `GlobalIdGenerator` 或任务批次专用规则 | 否，非资金链路，且当前包含可读前缀 |
| `service-job` | `service-job/src/main/java/com/scott/payment/job/support/TraceIdSupport.java` | UUID 生成 traceId | 保持 traceId 规则或另行制定链路追踪规范 | 否，traceId 不等同业务编号 |
| `component-mq` | `component-library/component-mq/src/main/java/com/scott/payment/component/mq/producer/impl/RocketMqProducer.java` | UUID 兜底生成 MQ `messageId` | 资金事件后续应优先使用业务编号或事件表 ID | 否，需结合 MQ 幂等与事件表治理 |
| `component-mq` | `component-library/component-mq/src/main/java/com/scott/payment/component/mq/publisher/OperationLogMqPublisher.java` | UUID 生成操作日志消息 ID 和兜底幂等片段 | 操作日志可保留 UUID；资金事件不应复用该逻辑 | 否，非资金业务编号 |
| `component-mq` | `component-library/component-mq/src/main/java/com/scott/payment/component/mq/admin/RocketMqAdminFacade.java` | `System.currentTimeMillis()` 拼接 RocketMQ AdminGroup | 保持内部客户端临时标识或改为专用内部命名 | 否，非业务编号 |
| `component-security` | `component-library/component-security/src/main/java/com/scott/payment/component/security/jwt/MerchantJwtVerifier.java` | `System.currentTimeMillis() / 1000L` 校验 JWT 时间 | 保持当前时间校验，不属于编号生成 | 否，不适用 |
| `service-openapi` | `service-openapi/src/main/java/com/scott/payment/openapi/support/OpenApiJwtReplayProtectionService.java` | `System.currentTimeMillis() / 1000L` 计算防重放窗口 | 保持当前时间窗口逻辑，不属于编号生成 | 否，不适用 |

## 6. 后续替换顺序建议

1. 先在正式交易主单、退款单、请款单、撤销单、冲正单、渠道请求单、商户通知单落地时使用 `GlobalIdGenerator`。
2. 再评估旧 `PaymentOrderNoGenerator` 调用方是否依赖业务前缀、长度或排序展示。
3. 最后处理 MQ、Job、traceId 等非资金业务编号，避免把业务编号和技术追踪标识混为一类。
