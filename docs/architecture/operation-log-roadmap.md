# 操作日志可靠链路与演进边界

## 1. 当前真实架构

管理端和商户端的管理类接口通过 `component-web` 采集操作日志；支付 OpenAPI 不复用该管理操作日志 AOP。

```text
service-admin / service-merchant 管理接口
    ↓
component-web OperationLogAspect
    ↓  脱敏、字段截断、最小必要信息
component-mq OperationLogMqPublisher
    ↓  IndependentReliableMqPublisher
sys_mq_outbox（独立事务可靠入队）
    ↓  Relay 投递、失败退避、超限 CLOSED
Admin / Merchant 独立 RocketMQ Topic 与 Consumer Group
    ↓
service-data OperationLogConsumerService
    ↓  来源校验、Redis 辅助幂等、数据库唯一键最终幂等
sys_oper_log
    ↓
service-admin / service-merchant 按权限和数据范围只读查询
```

`service-admin` 和 `service-merchant` 不再分别消费并本地写入，也不通过 HTTP 互相转发。操作日志异步持久化由 `service-data` 统一拥有，Admin 与 Merchant 仍使用独立 Topic、Consumer Group 和 Redis 幂等命名空间隔离。

## 2. 已落地约束

### 2.1 可靠发布

- 操作日志先冻结为脱敏消息，再写入通用 `sys_mq_outbox`。
- 外层业务事务无论提交还是回滚，审计事件都在事务资源释放后使用独立事务入队。
- Outbox 通过 CAS 领取、失败退避、超时恢复和最大重试次数保证至少一次投递。
- 独立入库任务被拒绝或持久化失败时不得覆盖原业务结果，但必须记录固定事件日志和 Micrometer 指标。
- 新消息使用 `系统编码:消息号` 作为稳定、受字段长度约束的消费幂等键；请求号和方法名不再进入幂等键。

### 2.2 消费与最终幂等

- `service-data` 校验消息声明的 `systemCode` 必须与实际 Topic 来源一致，跨系统消息直接失败并交由 Broker 重试。
- Redis 仅作为短期辅助幂等；`sys_oper_log.idempotent_key` 唯一约束是最终事实。
- Redis 不可用时继续尝试数据库写入，Redis 残留占用没有数据库事实时也继续写库。
- 仅实际取得 Redis 占用的消费者可在写库失败时释放占用。
- 兼容旧生产者：超出 `VARCHAR(255)` 的旧幂等键按 SHA-256 稳定收敛，避免形成无法落库的永久重试消息。
- 畸形、空消息、来源不匹配和写库异常都必须抛出，不能被消费者静默确认。

### 2.3 重试、死信与处置边界

- 开发环境资源声明中的操作日志 Consumer Group 使用 Broker 重试，当前最大重试次数为 16。
- 重试耗尽后使用 RocketMQ Consumer Group 原生死信队列，不额外复制一套业务死信 Topic。
- 人工处置必须以 Broker 中的原始消息和数据库事实为准：先确认 `sys_oper_log` 是否已经存在，再决定重消费；禁止直接绕过唯一约束插入重复日志。
- `sys_mq_outbox` 进入 `CLOSED` 或独立 Outbox 入库出现失败指标时，需要先修复数据库或 Broker 故障，再执行受控补偿。

### 2.4 查询与权限现状

- Admin 已有通用操作日志查询能力。
- Merchant 已有商户 OpenAPI 密钥场景的受限操作日志查询，后端强制注入认证商户号和固定模块，不允许跨商户查询。
- 是否新增 Merchant 通用操作日志菜单仍属于产品与 RBAC 决策；在确定展示范围、字段脱敏和权限编码前不得自行扩展为全量查询。

## 3. 运行指标

指标只允许固定低基数标签，不得使用请求号、消息号、商户号、账号、操作人或异常正文。

| 指标 | 固定标签 | 说明 |
|---|---|---|
| `acquiring.mq.outbox.independent.persistence` | `outcome` | 独立审计 Outbox 的成功、失败和执行器拒绝 |
| `acquiring.operation.log.event.total` | `system`、`event` | 发布、消费、重复、畸形、失败、Redis 降级和残留占用 |
| `acquiring.operation.log.consume.duration` | `system`、`outcome` | 单条操作日志消费耗时 |
| `acquiring.mq.outbox.pending.count` | `outbox_type`、`status` | 通用和交易 Outbox 当前积压 |
| `acquiring.mq.outbox.oldest.age.seconds` | `outbox_type` | 最早待处理消息等待时间 |
| `acquiring.mq.outbox.closed.count` | `outbox_type`、`topic_domain` | 重试耗尽关闭数量 |

## 4. 自动化验证基线

必须持续覆盖：

1. 发布前脱敏、字段截断和幂等键长度。
2. 外层事务提交、回滚及执行器拒绝后的独立 Outbox 入库。
3. 正常消费、重复消费、Redis 降级、Redis 残留占用、数据库失败释放。
4. 空消息、畸形 JSON、来源不匹配和超长旧幂等键。
5. 指标标签收敛，确保任意外部输入不能制造高基数时间序列。

## 5. 后续演进条件

1. 新增管理类系统时，继续复用 `OperationLogPublisher` SPI，但必须配置独立来源枚举、Topic/Tag 隔离、Consumer Group 和查询数据范围。
2. 只有 Broker 原生死信无法满足保留期、审批或审计要求时，才设计业务死信表；该变更需要单独评审数据模型、权限和补偿状态机。
3. Merchant 通用操作日志页面必须先确认产品范围和 RBAC，不得仅因为表中已有数据就开放。
4. 操作日志只能记录管理操作证据，不能替代交易状态历史、资金流水、审批轨迹或结算记账事实。
