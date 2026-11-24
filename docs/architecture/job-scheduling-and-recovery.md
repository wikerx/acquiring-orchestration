# 任务调度与补偿运行说明

## 服务分工

| 服务 | 管理或运行的内容 | 代码入口 |
|---|---|---|
| Admin | 后台任务定义、启停、手动触发、执行记录和节点查看。另拥有运营待办、审批及通知业务。 | `MonitorJobController`、`MonitorJobLogController`、`MonitorJobNodeController` |
| Job | 保存任务定义和执行结果、计算下次触发时间、选取处理器、抢占租约、续期、超时及重试。 | `JobTaskInternalController`、`JobTriggerScanner`、`JobDispatchService`、`JobTimeoutScanner` |
| 业务归属服务 | 执行业务命令，管理状态转换、幂等和已提交事实。跨服务请求走现有内部签名。 | 各处理器的内部客户端和归属服务的内部控制器 |

管理请求从 Admin 到 Job 的 `/internal/job/tasks`、`/internal/job/logs`、`/internal/job/nodes`。Job 执行时按任务调用业务归属服务。支付方式任务的部分执行请求会到 Admin，以便复用运营待办和通知规则。

## 任务定义和运行记录

- `sys_job_task` 保存 `job_code`、`handler_code`、Cron、并发、超时、失败重试、租约和下次执行时间。环境实际任务以该表为准，代码中的注册处理器可以尚未配置对应任务。
- `JobHandlerRegistry` 按处理器编码注册实现。Cron 触发扫描只处理启用且到期的定义。手动触发同步处理器也交给执行线程池，后台先拿到 `runId`，再查看最终结果。
- `sys_job_run_log` 保存运行编号、触发方式、节点、状态、时间及结果摘要。使用 `runId` 和 `traceId` 对应 Job 及业务服务日志。失败摘要保存在 `error_message`，成功摘要保存在 `result_message`。
- `sys_job_executor_node` 保存节点心跳。按当前环境核对 `job.scheduler` 的开关、周期和批量参数。默认任务扫描 5 秒、心跳 10 秒、离线阈值 30 秒。
- 支付方式边界和通知恢复使用栅栏租约。每次有效抢占增加 `lease_epoch`。写进度和释放锁需要当前节点和当前世代，保存边界游标还要匹配 `checkpoint_version`。任务超时扫描将仍在运行的记录收口为 `TIMEOUT`。

## 已注册处理器

| 处理器编码 | 用途 | 主要执行位置 |
|---|---|---|
| `shardingTablePreCreate` | 提前建立交易物理分表并记录建表结果 | Job |
| `paymentTimeoutClose` | 触发到期支付单关闭 | Payment |
| `dataExportTaskDispatch` | 触发数据导出任务派发 | Data |
| `merchantNotificationRetry` | 扫描和补偿商户交易回调通知 | Data |
| `clearingCompensation` | 补扫需要清分的交易 | Clearing |
| `channelTransactionMatch` | 触发渠道交易勾兑 | Payment |
| `bocExchangeRateFetchJob` | 抓取中国银行汇率 | Job |
| `nbpExchangeRateFetchJob` | 抓取波兰央行汇率 | Job |
| `exchangeRateVolatilityMonitorJob` | 评估汇率波动并生成告警 | Admin |
| `operationalWorkItemSync` | 同步统一运营待办 | Admin |
| `merchantPaymentCapabilityBoundary` | 补查授权、渠道、MID、费率和字典边界 | Admin |
| `merchantPaymentCapabilityDeliveryRecovery` | 补查并恢复支付方式事件和邮件投递意图 | Admin |
| `merchantPaymentRiskReservationRecovery` | 复核过期或待核实的累计限额预占 | Risk |

## 支付方式任务、Outbox 和预占

1. **边界复核。** Job 为每个来源保存独立游标和固定扫描上界。收到已确认的页后保存进度，超时或请求失败的来源保留原游标。变更类来源保留短时间重叠扫描，业务事件通过来源版本幂等处理。摘要包含 `processed`、`completed`、`failed`、`deferred` 和 `budgetExhausted`。正常分页的来源出现在 `deferred`，下轮接着处理。失败来源或整轮预算用尽时记录失败。
2. **通知恢复。** 业务提交时保存 Outbox 投递意图。Admin 与 Merchant 的常规中继继续扫描自己产生的事件，Admin 的常规邮件恢复继续覆盖支付方式邮件。专用 Job 通过 Admin 补扫支付方式范围，可与常规中继发现同一记录。Outbox 按状态和版本抢占，邮件按状态和到期条件重新入队，同一条记录只有满足原条件的一方能取得处理权。Job 未启用时仍有常规兜底。Admin 停机期间保留 Admin 待投递数据，恢复后由可用入口继续。Broker 回执丢失时仍可能重投，沿用原事件号由消费端幂等收敛。邮件重投沿用原邮件记录及通知业务键。
3. **任务预算。** 支付方式边界复核、通知恢复取 `timeout_seconds` 算整轮截止时间。边界任务将剩余时间分配给未处理来源。连接和读取超时都受本次请求剩余预算约束，Admin 到达截止时间后停止开始新记录。已经开始的业务事务按完整状态提交或回滚，重跑使用原幂等键。通知恢复摘要包含 `requeued`、`selected`、`relayed`、`failed`、`deferred`。接口无可确认的响应时记录 `result=unknown`，下一轮以数据库状态判断。
4. **预占复核。** Risk 使用权威交易事实判断确认、取消或保留。交易查询未知、交易仍处理中或准备归属尚不能证明已结束时，保留预占并延后复核。对受保护的支付准备，Risk 仅在需要证明原准备已安全结束时调用 Payment，范围绑定原准备键、世代和交易时间。Risk 当前也有服务内定时器，启用集中 Job 前应核对执行入口及环境切换安排。

三项任务按原业务状态、时间窗口和处理条件覆盖全部商户。常用 dev 使用 200046 做人工验收；真实商户由正常调度处理。Risk 的 `risk.evaluation.reservation-reconcile-enabled` 为 `false` 时，累计限额预占由 Job 调度；为 `true` 时，由 Risk 内置定时器全量复核，Risk 会拒绝 Job 发起的复核请求。

切换时先暂停三个任务并确认在途批次已结束，核对内部签名和 Risk 配置，重启相关服务后启用三个任务。首次从限定商户的环境切换到全系统时，记录切换时间并为边界来源设置全系统游标，保留少量时间重叠。回滚时先暂停三个任务并等待在途批次结束，将 Risk 的上述开关恢复为 `true`，重启并确认内置定时器恢复。保留签名凭据、事件号、检查点版本、租约世代和运行日志。

三条支付方式相关任务的种子见 [权限及 Job 种子 SQL](../sql/20261120_08_merchant_payment_permissions_and_jobs_seed.sql)。边界、通知、预占分别预设每 5、10、15 秒触发，默认禁用，整轮 30 秒。以上是脚本默认值，环境实际状态从管理台或 `sys_job_task` 查询。

执行相关 SQL 时，MySQL 客户端使用 `--default-character-set=utf8mb4`，脚本显式设置 `SET NAMES utf8mb4`。写入后从数据库回读任务名称、说明和通知模板。已有乱码可使用 [中文数据修复 SQL](../sql/20261121_11_req001_job_and_notification_text_repair.sql)，脚本按种子文本的精确乱码字节匹配。

## 排查顺序

1. 在管理后台检查任务的处理器、状态、下次触发时间、节点心跳和最后一轮 runId。
2. 对照运行状态与结果摘要。边界失败优先检查失败来源的游标。通知失败结合 Outbox 的 `event_id`、`event_status`、`retry_count`、`next_retry_time` 和邮件的 `email_no`、`send_status`。查询可使用能容忍延迟的读库，接管、重试和业务判断读写按服务的主库路由。
3. 查看 Job 到归属服务的精确调用路径和服务健康。通知恢复确认积压是否到期、是否被另一入口占用、是否已终止重试。业务拥有者恢复后先确认原标识和当前状态，再决定是否重触发。
4. 预占按原交易号和准备世代对账，检查 `reservation_status` 和复核时间。交易结果未知时保留原金额与状态，等待能够证明的支付事实再推进。

新增处理器或调整任务归属时，同步更新本清单和涉及的部署、验收记录。
