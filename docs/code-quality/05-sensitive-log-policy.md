# 敏感日志策略

## 1. 禁止项

| 类型 | 示例 |
|---|---|
| 卡数据 | 完整 PAN、CVV、安全码、认证令牌 |
| OpenAPI 凭据 | JWT、Authorization 头、merchantKey、API Key |
| 密钥 | RSA 私钥、AES 密钥、渠道密码 |
| 原始报文 | 未脱敏请求体、响应体、渠道回调原文 |
| 异常 | 可能含敏感报文的完整异常消息或堆栈 |

## 2. 允许项

- 交易号、操作号、商户号、商户订单号。
- 金额和币种。
- 平台状态、渠道统一状态、渠道响应码。
- 脱敏卡号、密文长度、密文指纹、响应摘要。
- HTTP 状态码、耗时、重试次数、处理结果。

## 3. 验收命令

生产日志、审计日志和渠道交互日志统一使用 `SensitiveDataMaskUtils.maskJsonSafely` 作为脱敏入口。
脱敏过程出现运行时异常时返回固定占位符 `***MASK_FAILED***`，禁止回退输出原始请求体、响应体或渠道报文。

新增单测：

- `SensitiveDataMaskUtilsTest.shouldMaskSensitiveJsonFields`
- `SensitiveDataMaskUtilsTest.shouldMaskJsonSafelyWithoutLeakingOriginalTextWhenMaskingFails`

阶段 4 验收命令：

```bash
mvn -pl component-library/component-core,component-library/component-http,component-library/component-mq,component-library/component-web -am test -DskipTests=false
python3 scripts/verify-logging-rules.py --root .
```

验收结果：

```text
checked_java_files=1129
sensitive_log_findings=0
missing_required_events=0
missing_trace_rules=0
```
