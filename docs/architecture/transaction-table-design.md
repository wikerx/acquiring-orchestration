# 收单交易核心表设计

本文说明 `service-payment` 后续正式落库的交易表族设计。所有交易相关表统一使用 `transaction_` 前缀；正式 SQL 草案见 `docs/sql/transaction-core-schema.sql`。

## 设计原则

1. `transaction_type` 和 `transaction_status` 对齐系统字典管理，不在数据库中散落新状态。
2. `transaction_status` 只表达交易结果状态：`SUCCESS`、`FAILED`、`PENDING`、`PROCESSING`；风控、路由、渠道请求、等待回调等过程节点使用 `process_stage`。
3. 同一笔原始交易生命周期使用 `transaction_order_no` 关联；授权、增量授权、请款、退款、拒付、二次请款等每个动作使用独立 `transaction_no`。
4. 大体量事实表按季度分表；交易查询、更新、状态推进必须传入路由时间或明确时间范围，第一阶段不建设全局索引表。
5. 金额同时保留主币种金额和最小单位金额；汇率使用 `DECIMAL(20,10)`；不使用 `double` 或 `float`。
6. 表示具体时间点的字段统一使用 `DATETIME(3)`；业务日期如 `settlement_date`、`reconciliation_date` 使用 `DATE`。
7. 渠道交互原文、回调原文和商户通知报文只能保存密文、摘要或脱敏摘要，不保存完整卡号、CVV、JWT、私钥、API Key。
8. 交易表保留 `time_zone` 字段；分表路由仍按系统统一季度规则和对应时间字段计算，不按订单号或商户号猜测路由。
9. `transaction_idempotency` 只负责资金类重复请求兜底，不承担跨分表查询索引职责。

## 表族关系

| 表名 | 是否分表 | 分表字段 | 职责 |
|---|---:|---|---|
| `transaction_idempotency` | 否 | 无 | 创建、请款、退款、回调、MQ 消费等幂等兜底 |
| `transaction_order` | 是 | `transaction_date_time` | 同一原始交易生命周期主单 |
| `transaction_operation` | 是 | `transaction_date_time` | 每个交易动作单 |
| `transaction_additional_info` | 是 | `transaction_date_time` | 付款人、卡 BIN、3DS、风控摘要等附属信息 |
| `transaction_merchant_snapshot` | 是 | `transaction_date_time` | 交易发生时商户、子商户、费率配置快照 |
| `transaction_channel_request` | 是 | `transaction_date_time` | 渠道请求核心字段 |
| `transaction_channel_interaction_log` | 是 | `transaction_date_time` | 渠道请求/响应交互日志 |
| `transaction_callback_log` | 是 | `callback_received_time` | 渠道回调原文日志，允许未匹配交易先落库 |
| `transaction_callback` | 是 | `transaction_date_time` | 渠道回调业务处理单 |
| `transaction_status_history` | 是 | `transaction_date_time` | 交易状态流转历史 |
| `transaction_finance_state` | 是 | `transaction_date_time` | 交易侧结算、对账、账务入账状态 |
| `transaction_merchant_notification` | 是 | `transaction_date_time` | 商户异步通知任务 |
| `transaction_merchant_notification_log` | 是 | `transaction_date_time` | 商户异步通知尝试日志 |
| `transaction_event_outbox` | 是 | `event_time` | 本地事务事件表，事务提交后异步发 MQ |

## 生命周期建模

`transaction_order` 是原始交易生命周期主单。比如一笔授权交易后续发生增量授权、请款、退款、拒付，这些动作都使用同一个 `transaction_order_no`。

`transaction_operation` 是动作单。每个交易动作拥有独立 `transaction_no` 和 `transaction_type`，例如：

- `AUTHORIZATION`
- `INCREMENTAL_AUTHORIZATION`
- `CAPTURE`
- `REFUND`
- `VOID`
- `REVERSAL`
- `CHARGEBACK`
- `REPRESENTMENT`
- `RETRIEVAL_REQUEST`

后续动作通过 `source_transaction_no` 关联源动作，例如退款关联请款，请款关联授权。

## 状态与幂等

交易状态更新必须使用 CAS 条件：

```sql
UPDATE transaction_operation_xxxxxx
SET transaction_status = ?, process_stage = ?, version = version + 1
WHERE transaction_no = ?
  AND transaction_status IN (...)
  AND version = ?;
```

终态 `SUCCESS`、`FAILED` 不允许被普通回调覆盖。重复回调只能写入 `transaction_callback_log` 和幂等命中结果，不得重复推进金额或状态。

建议幂等维度：

| 场景 | 幂等范围 | 幂等键 |
|---|---|---|
| 创建支付/授权 | `PAYMENT_CREATE` | `merchantId + merchantOrderNo + transactionType` |
| 请款 | `CAPTURE` | `merchantId + merchantCaptureNo + sourceTransactionNo` |
| 退款 | `REFUND` | `merchantId + merchantRefundNo + sourceTransactionNo` |
| 撤销/冲正 | `VOID` / `REVERSAL` | `merchantId + merchantOperationNo + sourceTransactionNo` |
| 渠道回调 | `CHANNEL_CALLBACK` | `channelCode + callbackEventId`，无事件ID时使用 `channelCode + bodySha256` |
| MQ 消费 | `MQ_CONSUME` | `consumerGroup + messageKey` |
| 商户通知 | `MERCHANT_NOTIFY` | `notifyNo + attemptNo` |

## 金额与结算

交易创建时写入：

- `order_amount` / `order_amount_minor`
- `transaction_currency`
- `currency_exponent`

动作成功后按类型更新累计金额：

- 授权成功更新 `authorized_amount_minor`
- 请款成功更新 `captured_amount_minor`
- 退款成功更新 `refunded_amount_minor`
- 拒付成功更新 `chargeback_amount_minor`

结算和对账状态先落在 `transaction_finance_state`，后续正式清分结算模块再扩展 `settlement_`、`reconciliation_` 表族。授权类交易不能直接计入应结金额，只有请款或一步支付成功后才进入可结算范围。

## 渠道请求与回调

主动请求链路：

1. 创建 `transaction_channel_request`
2. 写 `transaction_channel_interaction_log` 请求日志
3. 收到同步响应后更新 `transaction_channel_request`
4. 写 `transaction_channel_interaction_log` 响应日志
5. 按渠道同步状态推进 `transaction_operation`

渠道回调链路：

1. 先写 `transaction_callback_log`，保存回调原文密文、摘要、来源 IP、验签结果。
2. 根据回调中的平台交易标识、渠道订单号、渠道交易 ID 和回调接收时间确定查询时间范围；无法确定原交易时间时，必须限制扫描季度范围并记录待人工匹配状态。
3. 匹配成功后创建 `transaction_callback`。
4. 命中 `transaction_idempotency` 则返回重复处理结果，不推进状态。
5. 使用状态机 CAS 更新 `transaction_operation`、`transaction_order` 和 `transaction_finance_state`。
6. 写 `transaction_status_history` 和 `transaction_event_outbox`。

## 渠道回调 SPI 待补

目前 `payment-channel-library` 的 `PaymentChannelClient` 已覆盖主动请求能力：`payment`、`authorize`、`capture`、`refund`、`voidPayment`、`reversal`、`query`。但还缺少统一渠道回调 SPI，这一点需要在对接 MPGS 前补齐。

建议后续新增：

- `ChannelCallbackRequest`：承载渠道编码、请求头、请求体密文或原文、来源 IP、请求 URI。
- `ChannelCallbackVerifyResult`：返回验签、IP 白名单、时间窗校验结果。
- `ChannelCallbackParseResult`：返回渠道订单号、渠道交易 ID、渠道状态、金额、币种、事件 ID、原始错误码。
- `PaymentChannelCallbackHandler`：统一定义 `verify`、`parse`、`mapStatus`。
- `PaymentChannelCallbackExecutor`：按 `channelCode` 路由到具体渠道回调处理器。

MPGS 对接时不能把回调解析逻辑写进 `service-openapi` 或 `service-payment` Controller；渠道差异应留在 `channel-library`，平台状态推进留在 `service-payment`。

## 分表注意事项

季度分表表内唯一索引只能保证单个物理表唯一，无法保证跨季度唯一。因此第一阶段采用“强制路由时间 + 非分表幂等表”的方案：

- 创建、请款、退款、撤销、回调、MQ 消费等资金类入口必须先写 `transaction_idempotency`，用非分表唯一键兜底重复处理。
- 根据 `transaction_no`、`transaction_order_no`、`merchant_order_no` 查询或更新交易事实表时，调用方必须传入 `transaction_date_time`，或者传入受控的起止时间范围。
- 平台内部单号带 UTC+8 时间片，可用于排查和缩小时间范围，但正式路由仍以请求上下文中的路由时间字段为准。
- 后台查询允许按时间范围路由多个季度物理表，但必须限制最大范围，避免无界扫表。
- `transaction_callback_log` 允许未匹配交易先按 `callback_received_time` 落库，后续匹配成功再写 `transaction_callback`。
- 物理表预创建应复用现有 `service-job` 分表预创建能力，正式启用前再把 `docs/deployment/nacos/transaction-sharding-dev-draft.yaml` 合入环境配置。
- 如果后续 MPGS 回调、对账或后台检索大量出现“只有渠道流水号、没有交易时间”的场景，再评估新增轻量 `transaction_global_index` 或渠道索引表作为二阶段优化。

## 本地消息表说明

`transaction_event_outbox` 是交易侧本地消息表，解决“本地事务提交成功后可靠投递 RocketMQ”的问题。业务写交易状态、幂等结果和 outbox 事件必须在同一个本地事务内完成，事务提交后由 relay 任务扫描 `INIT` 或可重试的 `FAILED` 事件并投递 MQ。

第一阶段只定义表结构和写入骨架；正式可用还需要补齐：

- outbox relay 定时任务或后台任务；
- 投递成功后按 `event_status + version` CAS 更新为 `SENT`；
- 投递失败后增加 `retry_count`、`next_retry_time` 和 `fail_reason`；
- 消费者侧必须按 `consumerGroup + messageKey` 做数据库幂等，不能假设 MQ exactly-once。
