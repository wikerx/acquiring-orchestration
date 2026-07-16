# 收单交易核心表设计

本文说明 `service-payment` 后续正式落库的交易表族设计。所有交易相关表统一使用 `transaction_` 前缀；正式 SQL 草案见 `docs/sql/transaction-core-schema.sql`。

## 设计原则

1. `transaction_type` 和 `transaction_status` 对齐系统字典管理，不在数据库中散落新状态。
2. `transaction_status` 只表达交易结果状态：`SUCCESS`、`FAILED`、`PENDING`、`PROCESSING`；风控、路由、渠道请求、等待回调等过程节点使用 `process_stage`。
3. 每一笔交易动作使用独立 `transaction_id`；同一笔原始交易生命周期使用内部 `operation_id` 关联，`operation_id` 不返回商户。
4. 大体量事实表按季度分表；交易查询、更新、状态推进必须传入路由时间或明确时间范围，第一阶段不建设全局索引表。
5. 金额统一使用主币种单位 `DECIMAL(20,6)`；汇率使用 `DECIMAL(24,12)`；接口响应金额按 ISO 4217 精度转换为最小币种单位。
6. 表示具体时间点的字段统一使用 `DATETIME(3)`；业务日期如 `settlement_date`、`reconciliation_date` 使用 `DATE`。
7. 渠道交互原文、回调原文和商户通知报文只能保存密文、摘要或脱敏摘要，不保存完整卡号、CVV、JWT、私钥、API Key。
8. 交易表保留 `time_zone` 字段；分表路由仍按系统统一季度规则和对应时间字段计算，不按订单号或商户号猜测路由。
9. `transaction_idempotency` 只负责资金类重复请求兜底，不承担跨分表查询索引职责。

## 表族关系

| 表名 | 是否分表 | 分表字段 | 职责 |
|---|---:|---|---|
| `transaction_idempotency` | 否 | 无 | 创建、请款、退款、回调、MQ 消费等幂等兜底 |
| `transaction_order` | 是 | `transaction_date_time` | 同一原始交易生命周期主单，使用内部 `operation_id` 聚合 |
| `transaction_operation` | 是 | `transaction_date_time` | 每个交易动作单，使用独立 `transaction_id` |
| `transaction_additional_info` | 是 | `transaction_date_time` | 付款人、卡 BIN、3DS、风控摘要等附属信息 |
| `transaction_merchant_snapshot` | 是 | `transaction_date_time` | 交易发生时商户、子商户、费率配置快照 |
| `transaction_channel_request` | 是 | `transaction_date_time` | 渠道请求核心字段 |
| `transaction_channel_interaction_log` | 是 | `transaction_date_time` | 渠道请求/响应交互日志 |
| `transaction_channel_callback_log` | 是 | `transaction_date_time` | 渠道回调原文日志，未匹配交易先按接收时间落库 |
| `transaction_channel_callback` | 是 | `transaction_date_time` | 渠道回调业务处理单 |
| `transaction_status_history` | 是 | `transaction_date_time` | 交易状态流转历史 |
| `transaction_finance_state` | 是 | `transaction_date_time` | 交易侧结算、对账、账务入账状态 |
| `transaction_merchant_notification` | 是 | `transaction_date_time` | 商户异步通知任务 |
| `transaction_merchant_notification_log` | 是 | `transaction_date_time` | 商户异步通知尝试日志 |
| `transaction_event_outbox` | 是 | `transaction_date_time` | 本地事务事件表，事务提交后异步发 MQ |

## 生命周期建模

`transaction_order` 是原始交易生命周期主单。比如一笔授权交易后续发生增量授权、请款、退款、拒付，这些动作都使用同一个内部 `operation_id`。

`transaction_operation` 是动作单。每个交易动作拥有独立 `transaction_id` 和 `transaction_type`，例如：

- `AUTHORIZATION`
- `INCREMENTAL_AUTHORIZATION`
- `CAPTURE`
- `REFUND`
- `VOID`
- `REVERSAL`
- `CHARGEBACK`
- `REPRESENTMENT`
- `RETRIEVAL_REQUEST`

后续动作通过 `source_transaction_id` 关联源平台交易 ID，例如请款关联授权交易、退款关联请款或支付交易。

交易时间模型分为两层：

- `transaction_order.transaction_date_time` 使用原始授权或一步支付受理时间，主单长期按根交易时间定位。
- `transaction_operation.transaction_date_time` 使用每一次交易动作自己的受理时间，请款、退款、撤销跨季度时写入本次动作所在季度分表。
- `operation_id` 自身带根交易时间片，可用于从任意动作单回溯主单分表；后台交易详情按 `operation_id` 跨分表聚合同一生命周期下的动作、状态历史、渠道日志、回调和商户通知。

## 状态与幂等

交易状态更新必须使用 CAS 条件：

```sql
UPDATE transaction_operation_xxxxxx
SET transaction_status = ?, process_stage = ?, version = version + 1
WHERE transaction_id = ?
  AND transaction_status IN (...)
  AND version = ?;
```

终态 `SUCCESS`、`FAILED` 不允许被普通回调覆盖。重复回调只能写入 `transaction_channel_callback_log` 和幂等命中结果，不得重复推进金额或状态。

建议幂等维度：

| 场景 | 幂等范围 | 幂等键 |
|---|---|---|
| 创建支付/授权 | `TRANSACTION_OPERATION` | `merchantId + orderInfo.orderId + transactionType` |
| 请款 | `TRANSACTION_OPERATION` | `merchantId + orderInfo.orderId + transactionType` |
| 退款 | `TRANSACTION_OPERATION` | `merchantId + orderInfo.orderId + transactionType` |
| 撤销/冲正 | `TRANSACTION_OPERATION` | `merchantId + orderInfo.orderId + transactionType` |
| 渠道回调 | `CHANNEL_CALLBACK` | `channelCode + callbackEventId`，无事件ID时使用 `channelCode + bodySha256` |
| MQ 消费 | `MQ_CONSUME` | `consumerGroup + messageKey` |
| 商户通知 | `MERCHANT_NOTIFY` | `notifyNo + attemptNo` |

## 金额与结算

交易创建时写入：

- `label_currency` / `label_amount`：商户上送或页面展示的原始币种金额
- `transaction_currency` / `transaction_amount`：系统上送渠道和状态机使用的交易币种金额
- `currency_exponent`：交易币种默认小数位，用于 OpenAPI 响应最小单位换算
- `dcc_enabled` / `edc_enabled` / `transaction_rate`：DCC、EDC 和标签币种到交易币种的汇率快照

动作成功后按类型更新累计金额：

- 授权成功更新 `authorized_amount`
- 请款成功更新 `captured_amount`
- 退款成功更新 `refunded_amount`
- 拒付成功更新 `chargeback_amount`

结算和对账状态先落在 `transaction_finance_state`，后续正式清分结算模块再扩展 `settlement_`、`reconciliation_` 表族。授权类交易不能直接计入应结金额，只有请款或一步支付成功后才进入可结算范围。

## 渠道请求与回调

主动请求链路：

1. 创建 `transaction_channel_request`
2. 写 `transaction_channel_interaction_log` 请求日志
3. 收到同步响应后更新 `transaction_channel_request`
4. 写 `transaction_channel_interaction_log` 响应日志
5. 按渠道同步状态推进 `transaction_operation`

渠道回调链路：

1. `service-openapi` 先做渠道维度签名/IP 边界校验，签名文本包含 method、path、timestamp、nonce、channelCode 和原始 body 的 SHA-256 摘要，再把回调原文和安全结果转发到 `service-payment`。
2. `service-payment` 先写 `transaction_channel_callback_log`，保存回调原文脱敏摘要、来源 IP、验签结果。
3. `service-payment` 通过 `PaymentChannelCallbackExecutor` 调用渠道回调 SPI，渠道差异留在 `channel-library`。
4. MPGS 回调处理器按 `order.id` 解析平台原始 `transaction_id`，按 `transaction.id` 解析平台生成并落库的 `channel_transaction_id`，并复用 `result + response.acquirerCode=00` 的成功判断。
5. 匹配成功后创建 `transaction_channel_callback`，用 `channelCode + channelOrderNo + channelTransactionId + callbackType` 做回调业务幂等。
6. 使用状态机 CAS 更新 `transaction_operation`、`transaction_order` 和后续财务状态；终态重复回调只记录幂等命中和忽略结果，不重复推进金额。
7. 写 `transaction_status_history`、`transaction_flow_event`，终态后激活 `transaction_merchant_notification` 通知任务。

## 分表注意事项

季度分表表内唯一索引只能保证单个物理表唯一，无法保证跨季度唯一。因此第一阶段采用“强制路由时间 + 非分表幂等表”的方案：

- 创建、请款、退款、撤销、回调、MQ 消费等资金类入口必须先写 `transaction_idempotency`，用非分表唯一键兜底重复处理。
- 根据 `transaction_id`、`operation_id`、`merchant_order_no` 查询或更新交易事实表时，调用方必须传入 `transaction_date_time`，或者传入受控的起止时间范围。
- 平台 `transaction_id` 带 UTC+8 时间片，时间片必须与对应动作单的 `transaction_date_time` 一致。首次交易使用受理业务时间生成 `transaction_id`；后续请款、退款、撤销等动作使用本次动作受理时间生成新的 `transaction_id`，确保每个动作 ID 可解析到实际所在物理表。
- 后续动作先用 `sourceTransactionId` 定位原动作分表，再通过动作单的 `operation_id` 读取生命周期主单；主单更新按 `operation_id` 的根时间片定位，详情聚合按 `operation_id` 在受控时间范围内路由多个物理表。
- MPGS 渠道标识映射固定为：MPGS `orderId` = 原始授权或一步支付的平台 `transaction_id`，MPGS `transactionId` = 平台生成并落库的 `channel_transaction_id`；部分渠道没有渠道交易 ID 时 `channel_transaction_id` 可以为空。
- 后台查询允许按时间范围路由多个季度物理表，但必须限制最大范围，避免无界扫表。
- `transaction_channel_callback_log` 允许未匹配交易先按 `callback_received_time` 落库，后续匹配成功再写 `transaction_channel_callback`。
- 物理表预创建应复用现有 `service-job` 分表预创建能力，正式启用前再把 `docs/deployment/nacos/transaction-sharding-dev-draft.yaml` 合入环境配置。
- 如果后续 MPGS 回调、对账或后台检索大量出现“只有渠道流水号、没有交易时间”的场景，再评估新增轻量 `transaction_global_index` 或渠道索引表作为二阶段优化。

## 本地消息表说明

`transaction_event_outbox` 是交易侧本地消息表，解决“本地事务提交成功后可靠投递 RocketMQ”的问题。业务写交易状态、幂等结果和 outbox 事件必须在同一个本地事务内完成，事务提交后由 relay 服务扫描 `INIT` 或可重试的 `FAILED` 事件并投递 MQ。

当前已具备 outbox 写入、按 `transaction_date_time` 分表扫描、成功/失败 CAS 更新和 `payment-event` Topic 声明。后续还需要接入正式定时任务调度、补齐 MQ 消费数据库幂等，并把商户回调配置通过 MQ 同步到通知任务配置快照。
