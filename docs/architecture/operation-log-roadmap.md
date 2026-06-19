# 操作日志后续规划

## 1. 背景说明

当前系统已经具备管理类系统操作日志 AOP 采集能力，支付 OpenAPI 不采集操作日志。

后续在 RocketMQ 完成后，需要将管理类系统的操作日志从临时同步处理方式升级为异步、解耦、可重试的日志链路。

## 2. 待办事项

### 2.1 使用 RocketMQ 异步上报操作日志

后续操作日志采集后不直接同步调用远程服务，应通过 RocketMQ 异步上报。

目标：

- 管理类接口响应不依赖日志写入耗时。
- 日志消息具备重试能力。
- 消费端需要支持幂等，避免 RocketMQ 重投导致重复日志。
- 日志消息中禁止包含完整卡号、CVV、JWT、私钥、密钥等敏感信息。

### 2.2 admin 与 merchant 分别本地记录日志

`service-admin` 和 `service-merchant` 都属于管理类系统，应各自在自己的服务内完成日志落库。

目标：

- 两个服务可以共用同一套操作日志表结构。
- `service-admin` 记录后台管理系统操作日志。
- `service-merchant` 记录商户管理系统操作日志。
- 不再采用 `service-merchant` 采集后转发到 `service-admin` 统一保存的方式。
- 公共 AOP、注解、DTO、脱敏规则继续放在 `component-web` 复用。

## 3. 后续改造方向

1. 在 `component-mq` 中定义操作日志消息发送工具。
2. 在 `service-admin` 和 `service-merchant` 中分别实现本地日志消费者。
3. 移除 `service-merchant` 通过 HTTP 调用 `service-admin` 的临时记录器。
4. 为日志消息增加幂等键，例如 `requestId + methodName + operationTime`。
5. 补充 MQ 发送失败、重复消费、消费异常的测试用例。
