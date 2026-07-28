# 链路追踪传播说明

## 1. 传播路径

| 链路点 | 处理方式 |
|---|---|
| Gateway | `GatewayTraceIdFilter` 校验或生成 `X-Trace-Id`，写入响应头和 MDC |
| Servlet 服务 | `TraceIdFilter` 绑定 `TraceContext` 和 MDC，请求结束清理 |
| RestTemplate | `TraceIdRestTemplateInterceptor` 自动追加 `X-Trace-Id` |
| 手工 RestTemplate | `TraceIdRestTemplateCustomizer` 统一追加拦截器，admin、merchant、openapi、payment、job 内部客户端统一接入 |
| Hutool HTTP | `HttpClientUtils` 在 headers 为空或缺失 `X-Trace-Id` 时自动补齐 traceId |
| MQ 消息 | `BaseMqMessage.traceId` 承载 traceId，生产者发送前补齐 messageId、createdAt、traceId |
| MQ 消费 | payment/admin/merchant 消费者消费前绑定 traceId，finally 清理 |
| Job | `TraceIdSupport` 统一委托 `TraceContext` |

## 2. 验收点

- traceId 缺失时生成 32 位十六进制值。
- 外部 traceId 只允许字母、数字、`-`、`_`，长度不超过 64。
- RestTemplate 出站调用带 `X-Trace-Id`。
- `HttpClientUtils` 出站调用带 `X-Trace-Id`，已有调用方显式传入时不覆盖。
- MQ 生产端即使 RocketMQTemplate 未就绪，也先补齐消息元数据，便于本地降级和审计排查。
- 请求和 MQ 消费结束必须清理 MDC，避免线程复用串号。

## 3. 单测覆盖

新增测试：

- `TraceContextTest`
- `TraceIdRestTemplateInterceptorTest`
- `HttpClientUtilsTest`
- `RocketMqProducerTest`

## 4. 阶段 2/3 验收命令

```bash
mvn -pl component-library/component-core,component-library/component-http,component-library/component-mq,component-library/component-web -am test -DskipTests=false
python3 scripts/verify-logging-rules.py --root .
```

验收结果：

```text
component-http HttpClientUtilsTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
component-mq RocketMqProducerTest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
missing_trace_rules=0
```
