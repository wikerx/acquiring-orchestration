# 阶段 5 链路日志基础设施验收报告

## 1. 建设范围

| 链路点 | 本次处理 |
|---|---|
| Gateway | `GatewayTraceIdFilter` 校验外部 `X-Trace-Id`，非法或缺失时生成新 traceId；写入下游请求头、响应头、TraceContext 和 MDC；记录请求开始、路由完成、响应结束或异常、耗时 |
| Servlet 服务 | `TraceIdFilter` 读取并校验 `X-Trace-Id`，绑定 TraceContext/MDC，响应头回传，finally 清理 ThreadLocal 和 MDC |
| RestTemplate | `TraceIdRestTemplateInterceptor` 统一追加 `X-Trace-Id`；现有内部客户端配置通过 `TraceIdRestTemplateCustomizer` 或 `additionalInterceptors` 接入 |
| MQ 生产 | `BaseMqMessage` 承载 `traceId` 和 `retryCount`；`RocketMqProducer` 投递前补齐 messageId、createdAt、traceId、retryCount，并写入 RocketMQ 消息头 |
| MQ 消费 | payment/admin/merchant 消费者从消息体 traceId 绑定 MDC，日志输出 retryCount，finally 清理 TraceContext |
| 异步任务 | `TraceContextTaskDecorator` 在 job 执行线程池和延迟调度线程池传播 MDC/TraceContext，执行后恢复或清理线程上下文 |
| 定时任务 | 每次首次调度生成独立 traceId；失败重试沿用原 traceId；任务日志记录 jobId、handler、runId、retryIndex、shardIndex、shardTotal |
| 渠道调用 | MPGS 渠道日志沿用当前 MDC traceId；HTTP 请求只发送 MPGS 必需的 Authorization、Accept、Content-Type，不向第三方发送内部 `X-Trace-Id` |

## 2. spanId 处理结论

项目当前没有完整 span 生命周期、span 传播和父子关系实现。本轮只建设 traceId，因此不伪造 spanId。

处理方式：删除各服务 `logback-spring.xml` 中无意义的 `spanId=%X{spanId:-}` 占位，仅保留 `traceId=%X{traceId:-}`。

## 3. 代码级验收点

| 要求 | 验收依据 |
|---|---|
| 接收合法 traceId | `TraceContext.isValidTraceId` 允许字母、数字、`-`、`_`，最大 64 位 |
| 覆盖非法 traceId | Gateway 和 Servlet 均通过 `TraceContext.resolveOrCreate` 生成新值 |
| 下游透传 | Gateway mutate request header；RestTemplate interceptor 自动 set header |
| 响应回传 | Gateway 和 Servlet 均设置 `X-Trace-Id` 响应头 |
| 线程清理 | Servlet filter、Gateway finally、MQ consumer finally、TaskDecorator finally 均清理 |
| MQ 重试标记 | 消息体和 RocketMQ header 均携带 retryCount；消费者日志输出 retryCount |
| Job 重试 trace 保持 | `scheduleRetry` 调用 dispatch 时传入原 context traceId |
| Job 分片日志 | 当前未实现真实分片调度，默认 shardIndex=0、shardTotal=1；后续分片扩展应沿用同一 traceId |

## 4. 本轮新增/更新测试

| 测试 | 覆盖点 |
|---|---|
| `RocketMqProducerTest` | traceId/retryCount/messageId 写入消息体和 RocketMQ header，RocketMQTemplate 不可用时仍补齐元数据 |
| `TraceContextTaskDecoratorTest` | 任务线程恢复提交线程 traceId，异常后仍清理并还原原线程上下文 |

## 5. 验收命令

```bash
rg -n "spanId" service-*/src/main/resources/log-config/logback-spring.xml
mvn -pl component-library/component-mq,service-job -am test -DskipTests=false
python3 scripts/verify-logging-rules.py --root .
python3 scripts/verify-java-comments.py --root .
git diff --check
mvn -DskipTests clean compile
```

## 6. 本轮实际验收结果

验收时间：2026-07-26 16:12 Asia/Shanghai

| 验收项 | 结果 |
|---|---|
| 日志规则脚本 | `checked_java_files=1131`，`sensitive_log_findings=0`，`missing_required_events=0`，`missing_trace_rules=0` |
| Java 注释脚本 | `checked_java_files=1131`，`remaining_files=0`，`remaining_hits=0` |
| spanId 占位检查 | 服务 `logback-spring.xml` 无命中 |
| Diff 空白检查 | `git diff --check` 通过 |
| 阶段 5 定向测试 | `mvn -pl component-library/component-mq,service-gateway,service-job,service-payment,service-admin,service-merchant,channel-library/payment-channel-library -am test -DskipTests=false` 通过 |
| Job 线程传播复验 | `mvn -pl service-job -am test -DskipTests=false` 通过，service-job 测试 13 个，失败 0，错误 0 |
| 全量编译 | `mvn -DskipTests clean compile` 通过，23 个模块均 SUCCESS |
| 全量测试 | `mvn test` 通过，23 个模块均 SUCCESS |
