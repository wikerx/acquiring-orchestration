# 操作日志演进规划

## 1. 背景说明

当前系统已经具备管理类系统操作日志 AOP 采集能力，支付 OpenAPI 不采集操作日志。

当前已完成第一阶段落地：管理类系统操作日志已从临时同步处理方式升级为 RocketMQ 异步、解耦、可重试链路。

当前链路如下：

```text
管理类接口请求
    ↓
component-web 操作日志 AOP 采集
    ↓
敏感信息脱敏 / 文本截断
    ↓
component-mq 发布 OperationLogMessage
    ↓
service-admin / service-merchant 各自消费所属 Topic
    ↓
各自在本地 sys_oper_log 表落库
```

同时补充声明式 RocketMQ 资源初始化能力：

```text
Nacos / YAML 声明 Topic、Consumer Group
    ↓
component-mq 启动时读取 acquiring.mq.initializer
    ↓
RocketMQ 官方 DefaultMQAdminExt 检查资源
    ↓
资源缺失时按配置创建，失败时按 fail-fast 决定是否阻断启动
```

## 2. 已完成事项

### 2.1 使用 RocketMQ 异步上报操作日志

当前实现要点：

- 管理类接口响应不依赖日志写入耗时。
- 日志消息具备重试能力。
- 消费端需要支持幂等，避免 RocketMQ 重投导致重复日志。
- 日志消息中禁止包含完整卡号、CVV、JWT、私钥、密钥等敏感信息。

### 2.2 admin 与 merchant 分别本地记录日志

`service-admin` 和 `service-merchant` 都属于管理类系统，应各自在自己的服务内完成日志落库。

当前实现要点：

- 两个服务可以共用同一套操作日志表结构。
- `service-admin` 记录后台管理系统操作日志。
- `service-merchant` 记录商户管理系统操作日志。
- 已移除 `service-merchant` 采集后通过 HTTP 转发到 `service-admin` 统一保存的临时方案。
- 公共 AOP、注解、DTO、脱敏规则继续放在 `component-web` 复用。

### 2.3 使用官方 Admin API 做资源初始化

当前实现要点：

- 资源初始化仅封装业务适配层，不自建通用 MQ 框架。
- 发送端继续使用 `RocketMQTemplate`。
- 消费端继续使用 `@RocketMQMessageListener`。
- Topic / Consumer Group 检查与创建使用 RocketMQ 官方 `DefaultMQAdminExt`。
- 初始化器通过 `acquiring.mq.initializer.*` 配置声明资源，不影响未启用的服务。

## 3. 后续改造方向

1. 补充操作日志 MQ 发送失败、重复消费、消费异常的自动化测试。
2. 评估是否需要对操作日志消费失败增加死信主题或人工补偿脚本。
3. 视商户后台需求决定是否补充商户侧操作日志查询页面与 RBAC 权限。
4. 如果后续引入更多管理类服务，继续沿用 `OperationLogPublisher` SPI + 独立 Topic 或严格 Tag 隔离方式接入。
