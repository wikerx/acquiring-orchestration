# 操作日志与异步导出可靠性监控手册

## 1. 适用范围

本手册覆盖全系统通用/交易 Outbox、管理操作日志的可靠发布、RocketMQ 消费和数据库最终幂等，以及 Admin/Merchant 异步导出任务的积压、失败、下载和对象清理。告警规则位于 `docs/deployment/prometheus/reliability-alert-rules.yml`。

规则文件是部署制品，不代表当前环境已经加载。UAT/生产准入必须另外保留 Prometheus 规则加载、告警触发和恢复证据。

## 2. 采集与标签约束

1. `/actuator/prometheus` 只能由受控内网采集，不得暴露公网。
2. Prometheus target 必须提供稳定的 `job`、`instance`；应用公共标签应提供 `application`、`environment`。告警规则始终保留 `job`、`instance`，即使某个环境暂未注入应用公共标签，也不能丢失具体采集实例。
3. 操作日志指标只允许 `system`、`event`、`outcome`；导出指标只允许 `status`、`event`、`outcome`。
4. 禁止把任务号、消息号、请求号、商户号、账号、操作人、文件名、异常正文或对象键放入标签。
5. 计时器直方图是否启用由统一 Micrometer 配置治理；未启用直方图时仍可使用 count/sum 观察吞吐和平均耗时。
6. 若 `/actuator/prometheus` 样例缺少 `application` 或 `environment`，应修复环境配置；在修复前，告警仍必须依靠 Prometheus 采集端注入的 `job`、`instance` 精确定位，禁止把多个实例无区分地聚合为一条告警。

## 3. 指标目录

| 指标 | 说明 |
|---|---|
| `acquiring.mq.outbox.independent.persistence` | 审计 Outbox 独立入库成功、失败、执行器拒绝 |
| `acquiring.operation.log.event.total` | 操作日志排队、消费、重复、畸形、失败和 Redis 降级 |
| `acquiring.operation.log.consume.duration` | 单条操作日志消费耗时 |
| `acquiring.mq.outbox.pending.count` | 通用/交易 Outbox 各状态积压 |
| `acquiring.mq.outbox.oldest.age.seconds` | 最早待投递消息等待时间 |
| `acquiring.mq.outbox.closed.count` | 重试耗尽关闭数量 |
| `acquiring.mq.outbox.snapshot.refresh` | 通用/交易 Outbox 数据库状态快照刷新成功或失败 |
| `acquiring.data.export.task.status.count` | 导出任务各状态数量；由调度轮次读取主库快照 |
| `acquiring.data.export.task.oldest.unfinished.age.seconds` | 最早 PENDING/PROCESSING 任务等待时间 |
| `acquiring.data.export.event.total` | 创建、重复、领取、延后、恢复、执行、下载、清理事件 |
| `acquiring.data.export.execution.duration` | 单个导出任务生成和状态提交耗时 |

导出任务 Gauge 会在 `service-data` 就绪后立即装载一次数据库快照，之后默认每 30 秒刷新；
即使没有调度任务请求，也必须持续反映历史失败和未完成任务。刷新间隔可通过
`acquiring.data-export.metrics-refresh-delay-ms` 调整，禁止设置为会对主库形成持续压力的过小值。

## 4. 操作日志告警处置

### 4.1 独立 Outbox 入库失败

1. 确认告警应用和时间窗口，检查 `INDEPENDENT_MQ_OUTBOX_PERSIST_FAILED` 固定事件日志；不得检索或复制消息载荷到工单。
2. 检查主库连接池、`sys_mq_outbox` 写权限、唯一键冲突和事务管理器状态。
3. 原业务调用可能已经成功，禁止通过重放业务请求补审计。
4. 若冻结事件没有进入 Outbox，必须按已批准的审计补偿流程处理；当前实现不会以覆盖业务结果为代价强制失败。

### 4.2 消费失败、畸形消息或来源不匹配

1. 检查消息声明 `systemCode` 与实际 Admin/Merchant Topic 是否一致。
2. 检查 Redis 辅助幂等和 `sys_oper_log.idempotent_key` 唯一约束；数据库事实优先于 Redis 占用。
3. Consumer Group 当前由 Broker 最多重试 16 次，耗尽后进入原生死信队列。
4. 重消费前先查询数据库是否已有相同幂等键；已有事实时不得直接插入或修改唯一键。
5. 修复生产者契约或依赖故障后再受控重消费，并确认 `consumed` 增长、`failed/invalid` 归零。

### 4.3 Outbox CLOSED

1. 按 `outbox_type` 区分通用 Outbox 与交易 Outbox，不得混用补偿脚本。
2. 核对 Topic、Broker、数据库记录状态、最近错误类型和重试次数。
3. 修复根因后使用原事件执行批准的补偿，禁止新造业务事件绕过幂等和状态机。

### 4.4 非资金审计受控补偿

`service-admin` 和 `service-merchant` 分别提供一次性启动入口 `AuditOutboxCompensationRunner`，
默认关闭且默认只读预演。每个入口只能恢复本服务产生、且缺少 `sys_oper_log` 最终事实的操作日志：

- Admin 入口固定允许 `service-admin`、`acquiring_admin_operation_log_topic`、`ADMIN`；
- Merchant 入口固定允许 `service-merchant`、`acquiring_merchant_operation_log_topic`、`MERCHANT`。

两个入口均不支持登录审计、风险审计、通知、交易、退款、资金、清分或结算消息。
禁止通过修改 Topic、生产服务、消息来源或 SQL 绕过白名单，也禁止由一个服务代投递另一个服务拥有的 Outbox。

每次执行必须遵守以下门槛：

1. 先确认原记录为 `CLOSED`、重试次数已耗尽、JSON 与消息声明合法，并且最终 `sys_oper_log` 幂等事实不存在。
2. 单批硬上限为 5；候选按原创建时间排序，逐条执行，首个校验异常、CAS 冲突或投递未确认立即停止。
3. 状态恢复必须调用 Outbox 的版本 CAS，投递必须使用原 `event_id`、原 Topic 和原载荷；不得复制载荷创建新事件。
4. 执行态除显式开关外，还必须提供二次确认令牌和不含敏感信息的批准原因。未经变更批准只能使用 dry-run。
5. 投递后必须等待消费者落库，并核对每个幂等键恰好一条最终事实；未知结果不得再次投递。
6. 日志和验收材料只记录事件号、计数、状态和停止原因，不复制审计载荷。

本机 Admin 预演示例（禁止把示例当作生产授权）：

```bash
java -jar service-admin/target/service-admin.jar \
  --server.port=18001 \
  --spring.cloud.nacos.discovery.enabled=false \
  --acquiring.mq.outbox.relay.enabled=false \
  --acquiring.audit-outbox-compensation.enabled=true \
  --acquiring.audit-outbox-compensation.dry-run=true \
  --acquiring.audit-outbox-compensation.batch-size=5
```

Merchant 预演使用 `service-merchant/target/service-merchant.jar` 和独立临时端口，其他安全门槛相同。
真实执行必须在对应生产服务的一次性实例内完成，确保 Relay 的 `producer_service` 所有权校验不被绕过。

真实执行还需在已批准的变更窗口内设置 `dry-run=false`、审批令牌和批准原因；运行结束后应立即关闭临时实例。

## 5. 异步导出告警处置

### 5.1 未完成任务超时

1. 检查 `DATA_EXPORT_TASK_DISPATCH` 是否按计划运行，内部调用是否成功。
2. 检查 `PENDING` 的 `next_retry_time` 和 `PROCESSING` 的租约是否持续推进。
3. 检查专用执行器是否频繁 `deferred`，以及来源分页接口、数据库、对象存储是否可用。
4. 不得直接把 PENDING/PROCESSING 改为 SUCCESS，也不得手工填写对象键。

### 5.2 最终失败任务

1. 从下载中心按原创建账号查看脱敏后的失败码、失败摘要、重试次数和版本。
2. 修复来源或对象存储故障后，由原创建人使用预期版本执行人工重试。
3. 版本条件冲突说明任务状态已经变化，应刷新详情，不得重复点击或直接改库。

### 5.3 下载或清理失败

1. 下载必须先写 `REQUESTED` 审计，完成后条件更新为 `SUCCESS/FAILED`。
2. 核对私有 Bucket 权限、对象存在性、保留期和 SHA-256；不得把对象存储永久 URL 返回浏览器。
3. 到期清理必须先删除对象再提交 EXPIRED。对象删除失败时保留任务事实，修复后由后续调度重试。

## 6. 验收证据

1. 应用单元/集成测试覆盖重复消费、来源不匹配、旧超长幂等键、Redis 降级、数据库失败和指标标签收敛。
2. Prometheus targets、规则加载结果和指标样例查询。
3. 独立 Outbox 失败、操作日志消费失败、导出积压和最终失败的受控告警触发/恢复记录。
4. RocketMQ 重试与死信处置演练；必须证明重消费不会产生重复 `sys_oper_log`。
5. 导出租约恢复、执行器背压、自动/人工重试、下载审计和到期清理结果。

未在真实 UAT/生产执行的项目必须明确标记“未执行”，不能用本地测试代替生产准入证据。

## 7. 本机 Prometheus/Alertmanager 演练环境

本机部署文件位于 `docs/deployment/prometheus/local/`，只绑定 `127.0.0.1:9090` 和
`127.0.0.1:9093`，通过 `host.docker.internal` 采集本机所有使用可靠 Outbox 的服务。启动、规则校验、通知
触发与恢复步骤以该目录的 `README.md` 为准。

该环境用于证明 target、规则求值、Alertmanager 路由和 Webhook 通知链路可用；它不等于
UAT/生产监控部署。若正式 CLOSED 告警持续 firing，应保留真实积压数量并按受控批次处置，
不得为了让告警恢复而直接改状态、删除数据或扩大补偿范围。
