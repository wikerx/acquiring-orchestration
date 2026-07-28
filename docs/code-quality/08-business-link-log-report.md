# 阶段 6 完整业务链路日志验收报告

## 1. 建设范围

| 模块 | 本次补充 |
|---|---|
| service-gateway | 网关请求开始、路由完成、响应结束/异常日志显式输出 traceId、routeId、targetService、method、path、queryKeys、clientIp、userAgent 摘要、HTTP status、durationMs |
| service-openapi | 请求进入、JWT/IP/防重放校验结果、jti 摘要、请求解密摘要、调用 service-payment 开始/结束、响应 data 加密、HTTP 状态、平台业务码和总耗时 |
| service-payment | 交易接收、交易号和 operationId 生成、幂等命中/冲突/完成、本地落库 affectedRows、风控调用、路由结果、渠道调用、状态映射、金额累计变化、商户响应构造、MQ outbox、商户通知创建 |
| service-risk | 风控请求收到、商户/订单/交易类型、金额币种、规则数量、命中规则 ID/类型、风控结论、拒绝原因和耗时 |
| channel-library | MPGS 请求/响应结构化日志，复用 `maskMpgsJson` 和 `toMaskedJsonLogObject` 输出脱敏 request/response summary，不发送内部敏感请求头到第三方渠道 |
| 回调链路 | 回调收到、渠道、事件类型、请求号、签名和 IP 校验、幂等命中/未命中、重复回调、状态前后值、DB affectedRows、是否触发商户通知、耗时 |
| 商户通知 | notifyId、transactionId、callbackUrl 脱敏、attemptCount、HTTP status、商户响应摘要、success/failure、nextRetryTime、durationMs |
| service-admin/service-merchant | 基于 `OperationLogAspect` 统一输出写操作和查询摘要日志；写操作覆盖新增、编辑、删除、审核、启停、密钥和配置类变更；查询只记录条件摘要、状态和耗时，不输出完整结果 |
| service-job | 调度器保留 jobId、handler、runId、分片和耗时；具体 handler 补充调度参数、扫描区间、扫描数、成功数、失败数、跳过/待处理数和失败原因统计 |

## 2. 敏感日志边界

| 数据 | 处理方式 |
|---|---|
| JWT / Authorization | OpenAPI 只输出 `jtiDigest`，内部 RestClient 不打印签名头或 Authorization |
| AES/RSA/商户密钥 | 不输出密钥、私钥、完整密文；响应加密只记录明文长度和密文长度 |
| 卡号/CVV | payment 与 channel 日志只允许脱敏摘要；MPGS 复用既有脱敏方法处理 PAN、认证 token 和 apiPassword |
| Query / Body | Gateway 只记录 query 参数名；OpenAPI 只记录脱敏后的业务参数摘要；禁止打印完整 body |
| 商户通知 URL | 使用已落库的 `targetUrlMasked` 输出，不打印完整 URL |

## 3. 阶段 6 关键事件

| 链路 | 关键事件 |
|---|---|
| Gateway | `GATEWAY_REQUEST_START`、`GATEWAY_ROUTE_COMPLETE`、`GATEWAY_REQUEST_END`、`GATEWAY_REQUEST_ERROR` |
| OpenAPI | `OPENAPI_REQUEST_ENTER`、`OPENAPI_SECURITY_CHECK_END`、`OPENAPI_REQUEST_DECRYPT_END`、`OPENAPI_PAYMENT_CALL_START`、`OPENAPI_PAYMENT_CALL_END`、`OPENAPI_RESPONSE_ENCRYPT_END`、`OPENAPI_REQUEST_END` |
| Payment | `PAYMENT_TRANSACTION_START`、`PAYMENT_IDENTIFIERS_GENERATED`、`PAYMENT_IDEMPOTENCY_HIT`、`PAYMENT_IDEMPOTENCY_CONFLICT`、`PAYMENT_IDEMPOTENCY_COMPLETE`、`PAYMENT_ROUTE_DECISION`、`PAYMENT_STATUS_MAPPED`、`PAYMENT_LOCAL_PREPARE_COMMIT`、`PAYMENT_AMOUNT_CHANGED`、`PAYMENT_MERCHANT_RESPONSE_BUILT` |
| Channel | `PAYMENT_CHANNEL_REQUEST_START`、`PAYMENT_CHANNEL_REQUEST_END`、`PAYMENT_CHANNEL_REQUEST_FAILED`、`CHANNEL_REQUEST_START`、`CHANNEL_RESPONSE_END`、`CHANNEL_REQUEST_FAILED` |
| Callback | `PAYMENT_CHANNEL_CALLBACK_START`、`PAYMENT_CHANNEL_CALLBACK_IDEMPOTENCY_MISS`、`PAYMENT_CHANNEL_CALLBACK_DUPLICATE`、`PAYMENT_CHANNEL_CALLBACK_PROCESS_UPDATE`、`PAYMENT_CALLBACK_DB_UPDATE`、`PAYMENT_CHANNEL_CALLBACK_END` |
| Notify | `PAYMENT_MERCHANT_NOTIFY_CREATED`、`PAYMENT_MERCHANT_NOTIFY_ACTIVATED`、`PAYMENT_MERCHANT_NOTIFY_ATTEMPT_START`、`PAYMENT_MERCHANT_NOTIFY_ATTEMPT_END` |
| Admin/Merchant | `ADMIN_WRITE_OPERATION`、`ADMIN_QUERY_ACCESS` |
| Job | `JOB_EXECUTE_START`、`JOB_EXECUTE_END`、`JOB_HANDLER_SCAN_START`、`JOB_HANDLER_SCAN_END` |

## 4. 已知边界

- 阶段 6 只建设 traceId 维度的业务链路日志；阶段 5 已确认项目未建设完整 span 生命周期，因此不伪造 spanId。
- `PaymentTimeoutCloseJob` 当前仍是占位任务，本次日志明确记录 `scannedCount=0`、`skipCount=1`，不伪造扫描结果。
- 渠道库当前只对 MPGS 补充结构化日志；其它渠道如接入真实 HTTP 客户端，应复用同等字段和脱敏策略。

## 5. 验收命令

```bash
mvn -pl service-gateway,service-openapi,service-payment,service-risk,channel-library/payment-channel-library,service-job,service-admin,service-merchant -am -DskipTests compile
python3 scripts/verify-logging-rules.py --root .
python3 scripts/verify-java-comments.py --root .
git diff --check
mvn test
```

## 6. 本轮验收结果

验收时间：2026-07-26 16:30 Asia/Shanghai。

| 验收项 | 命令 | 结果 |
|---|---|---|
| Java 注释治理扫描 | `python3 scripts/verify-java-comments.py --root .` | 通过；`checked_java_files=1131`，`remaining_files=0`，`remaining_hits=0` |
| 日志规则和 trace 规则扫描 | `python3 scripts/verify-logging-rules.py --root .` | 通过；`checked_java_files=1131`，`sensitive_log_findings=0`，`missing_required_events=0`，`missing_trace_rules=0` |
| 空白和补丁格式检查 | `git diff --check` | 通过；无输出 |
| 生产日志敏感字段 grep | `rg -n "log\\.(info|warn|error|debug)\\([^\\n]*(Authorization|authorization|securityCode|cvv|cvc|privateKey|aesKey|apiPassword|password|cardNo|requestBody|plainDataJson|encryptedData)" service-* component-library channel-library -g '*.java' -g '!**/src/test/**' -g '!**/target/**'` | 通过；无生产代码命中 |
| 受影响模块回归 | `mvn -pl service-gateway,service-openapi,service-payment,service-risk,channel-library/payment-channel-library,service-job,service-admin,service-merchant -am test -DskipTests=false` | 通过；20 个 Reactor 模块 `BUILD SUCCESS` |
| 全项目回归 | `mvn test` | 通过；23 个 Reactor 模块 `BUILD SUCCESS` |

## 7. 卡信息存储结论

交易链路不保存完整 PAN 或 CVV。交易支付工具摘要表 `transaction_payment_method_info` 保存的是 `card_bin`、`card_last4`、`card_number_masked`、`expiry_month`、`expiry_year`、`token_id` 和 `payment_account_hash` 等排查、展示和风控字段；`securityCode`/CVV 只允许在 OpenAPI 到渠道调用的内存链路中短暂使用。

风控黑白名单配置存在 `risk_black_card_no`、`risk_white_card_no` 等表，代码会将卡号标准化后保存脱敏展示值、SHA-256 哈希和加密密文，用于名单命中和授权编辑回显；这属于风控名单配置存储，不是交易订单保存完整卡号。
