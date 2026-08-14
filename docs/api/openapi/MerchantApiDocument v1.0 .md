# 商户 OpenAPI 接入指南

| 项目 | 内容 |
| --- | --- |
| 文档版本 | `v1.0` |
| API 版本 | `v1` |
| 更新日期 | `2026-08-14` |
| 适用对象 | 商户服务端开发、测试、运维和安全人员 |

本文档说明商户服务端如何接入支付平台 OpenAPI，包括身份认证、请求加密、响应解密、幂等、终态回调和重试规则，以及当前 14 个正式开放接口和 1 个规划中接口的请求与响应契约。

除 4.7 节明确列出的 Sandbox 测试卡外，本文档中的商户号、密钥、卡号、账户号和交易号均为格式示例，不能用于真实交易。各环境的商户凭据、平台公钥和商户响应私钥以商户系统或开户邮件提供的材料为准。

## 1. 接入概览

### 1.1 接入步骤

1. 从平台获取环境地址和商户接入材料。
2. 在商户服务端生成 JWT，并通过 `Authorization: Bearer {JWT}` 发送。
3. 使用平台公钥对明文业务 JSON 执行混合加密，将密文放入请求体 `data`。
4. 调用对应的 `POST` 接口，先判断 HTTP 状态和外层响应 `code`。
5. 外层 `data` 非空时，使用商户响应私钥解密，再判断交易业务结果。

### 1.2 平台交付材料

| 材料 | 用途 | 保密要求 |
| --- | --- | --- |
| `Base URL` | 支付网关对外 API 请求地址 | 可公开给商户接入人员，不应由前端用户修改 |
| `merchantId` | 商户号                        | 可在商户服务端配置中保存 |
| `merchantKey` | 生成 JWT 的 HS256 签名 | 高敏感，只允许保存在商户服务端 |
| 平台请求加密公钥 | 加密每次请求的 AES 会话密钥 | 公钥材料，仍应校验来源和指纹 |
| 商户响应私钥 | 解密平台响应 `data` | 高敏感，只允许保存在商户服务端 |
| Sandbox 测试数据 | 联调支付、3DS 和退款场景 | 只能用于指定的非生产环境 |

生产密钥不得进入浏览器、移动端、代码仓库、构建产物、容器镜像应用层或日志。建议通过 KMS/HSM、只读 Secret Volume 或受控外置文件注入。

商户可以从商户系统自行获取密钥材料，也可以使用开户邮件中提供的密钥材料。首次获取后应立即转存到商户服务端的安全存储，核对所属环境和 `merchantId`，不得转发邮件、粘贴到即时通信工具或提交到代码仓库。

### 1.3 参数标识

| 标识 | 含义 |
| --- | --- |
| M | Mandatory，必填 |
| O | Optional，可选 |
| C | Conditional，满足指定条件时必填 |

请求参数表中的 `M/O/C` 分别表示必填、可选和条件必填。响应参数表中的 `M` 表示外层 `code=T200` 且返回该业务模型时必须返回，`O` 表示平台可选返回，`C` 表示满足表内条件时必须返回；协议、鉴权或参数校验失败且外层 `data` 为空时，不适用解密后业务字段的必返规则。

金额字段统一标记为 `BigDecimal（12,2）`，表示整数最多 12 位、平台最多处理两位有效小数；三位小数币种仅允许第三位为 `0`，具体规则见 4.3 节。汇率字段标记为 `BigDecimal（18,8）`，表示整数最多 18 位、小数最多 8 位。这里的括号是商户接口取值范围，不是 Java 类型声明，也不是数据库 `DECIMAL(p,s)` 的总精度定义。JSON 报文使用十进制 number，Java 商户映射为 `BigDecimal`，其他语言使用等价的十进制定点类型；代付最小单位金额仍使用整数。

每个接口的参数说明先列“主参数”，再按父对象列“子参数”。子参数表中的字段名均相对于当前标题所示父对象，例如 `subMerchantInfo` 子参数表中的 `intesCode` 对应 JSON 路径 `merchantInfo.subMerchantInfo.intesCode`；商户不需要在 JSON 中使用带点号的字段名。

### 1.4 接入边界

- 商户只能调用第 5 章标记为正式开放的 `/api/rest/**` 接口；规划中接口不可调用。
- 商户不得调用 `/internal/**`、`/channel/**`、通知重试入口或 Hosted Checkout 浏览器内部接口。
- OpenAPI 凭据只能由商户服务端持有，不能从浏览器或移动端直接调用 OpenAPI。
- API 路径存在不代表所有支付方式或币种默认开通，具体能力以商户配置和平台通知为准。

## 2. 通信规范

### 2.1 请求地址

当前 Sandbox 环境地址如下：

```text
Base URL: http://test-vexra.com/api/rest/
```

该 `Base URL` 已包含 `/api/rest/`。拼接请求地址时，应使用不带开头斜杠的相对路径：

```text
Relative Path: payment/v1/payment
Full URL: http://test-vexra.com/api/rest/payment/v1/payment
```

第 5 章及各接口章节继续使用以 `/api/rest/` 开头的规范 API Path，便于独立识别接口。不要把规范 API Path 直接追加到上述 Sandbox `Base URL`，否则会得到重复的 `/api/rest/api/rest/`。

Sandbox 地址使用 HTTP，仅限测试联调，不得用于真实持卡人数据或生产交易。UAT 和 Production 的 `Base URL` 由平台另行提供，其中 Production 必须使用 HTTPS；不要自行推导其他环境地址。

### 2.2 传输要求

| 项目 | 规则 |
| --- | --- |
| 协议 | Production 必须使用 HTTPS |
| 字符集 | UTF-8 |
| Content-Type | `application/json` |
| HTTP Method | 当前 14 个正式开放接口全部使用 `POST`；规划中的代付接口拟使用 `POST` |
| API 版本 | 当前为 `v1`，版本位于 URL 中 |
| 请求体 | 外层 JSON 只传加密后的 `data` |
| 成功响应 | 外层 `code/message` 明文，`data` 加密 |
| 失败响应 | 外层 `code/message` 明文，`data` 通常为空 |

### 2.3 公共请求头

| Header | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `Content-Type` | M | `application/json` | 固定值 |
| `Authorization` | M | `Bearer eyJ...` | `Bearer`、一个空格和 JWT |

HTTP Header 名称不区分大小写，本文统一使用标准写法 `Authorization`。

商户只需显式配置上述两个请求 Header。`Host` 属于 HTTP 传输层信息，由请求 URL、SDK 或 HTTP 客户端自动生成，不作为商户手工传入或平台业务校验的 Header。

商户不应发送或伪造 `X-Gateway-Client-Ip`。平台使用受信网关确认的商户服务器出口 IP 执行 OpenAPI 访问白名单和接口安全校验；该网络层 IP 不作为 `payerInfo.ipAddress`，也不替代付款人 IP 风控。商户应提前向平台提供服务器、NAT 网关或固定代理的公网出口 IP。

### 2.4 时间和时区

- JWT 的 `iat` 和 `exp` 使用 Unix epoch 秒。
- 商户服务器必须启用可靠的 NTP 时间同步。
- 响应中的交易时间使用 ISO-8601 offset datetime，例如 `2026-08-01T10:20:30.123+08:00`。
- 商户应按带偏移量时间解析，不要假定平台和商户部署在同一时区。

## 3. 身份认证与报文加密

### 3.1 安全方案

```text
HTTPS + JWT HS256 + RSA-OAEP-256 + AES-256-GCM + jti 防重放
```

JWT 用于身份认证和防篡改，不承载卡号、CVV、账户号或其他敏感业务数据。业务 JSON 必须通过混合加密后放入请求体。

### 3.2 完整调用流程

```mermaid
sequenceDiagram
    participant M as 商户服务端
    participant P as 支付平台 OpenAPI

    M->>M: 生成唯一 jti 和短时效 JWT
    M->>M: 生成随机 AES-256 key 和 12 字节 IV
    M->>M: 使用 AES-256-GCM 加密业务 JSON
    M->>M: 使用平台公钥通过 RSA-OAEP-256 加密 AES key
    M->>P: POST + Bearer JWT + data 密文
    P->>P: 验证商户、JWT、时间窗、jti 和来源
    P->>P: 解密 data 并校验业务参数
    P->>P: 执行业务处理
    P->>P: 使用商户响应公钥加密响应 data
    P-->>M: 返回明文 code/message 和加密 data
    M->>M: 使用商户响应私钥解密 data
```

### 3.3 JWT 生成规则

JWT Header 固定为：

```json
{
  "typ": "JWT",
  "alg": "HS256"
}
```

JWT Payload 示例：

```json
{
  "aud": ["gateway"],
  "iss": "merchant",
  "jti": "550e8400-e29b-41d4-a716-446655440000",
  "iat": 1785549600,
  "exp": 1785549720,
  "merchantId": "200045"
}
```

| 字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `aud` | array/String | M | 必须包含或等于 `gateway`，建议使用数组 `['gateway']` |
| `iss` | String | M | 固定为 `merchant` |
| `jti` | String | M | 每次 HTTP 尝试唯一，不能重复使用 |
| `iat` | integer | M | 当前 Unix epoch 秒；最多允许比平台时间快 60 秒 |
| `exp` | integer | M | 必须晚于 `iat`，且 `exp - iat <= 180` 秒 |
| `merchantId` | String | M | 平台分配的商户号 |

签名算法为标准 HMAC-SHA256：

```text
base64url(jwtHeader) + "." + base64url(jwtPayload)
```

使用 `merchantKey` 对上述字符串计算 HMAC-SHA256，再进行无填充 Base64Url 编码。最终 JWT 为：

```text
base64url(header).base64url(payload).base64url(signature)
```

#### 3.3.1 请求头参数示例

以下内容用于说明 Header 名称和值，不是请求体 JSON：

```http
Content-Type: application/json
Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOlsiZ2F0ZXdheSJdLCJpc3MiOiJtZXJjaGFudCIsImp0aSI6Imlzby1jb3VudHJpZXMtZGNjNTE4NjYtNWNmYy00ZWZlLTk5OWYtNjkwODlhMzU2NzcxIiwiaWF0IjoxNzg2NjA4ODAwLCJleHAiOjE3ODY2MDg5ODAsIm1lcmNoYW50SWQiOiIyMDAwNDUifQ.ItrwebmqS-w6192cS862TUsloyB-54AybQkPvg_1bqY
```

商户需要配置的请求片段如下；域名和 `Host` 信息由请求 URL 与 HTTP 客户端处理：

```http
POST /api/rest/payment/v1/payment HTTP/1.1
Content-Type: application/json
Authorization: Bearer {JWT}

{
  "data": "{five-part-encrypted-request}"
}
```

#### 3.3.2 Authorization 解析

平台先校验 `Authorization` 是否以 `Bearer ` 开头，再解析 JWT 的 Header、Payload 和签名。商户不得在 `Bearer` 前后增加引号，不得使用过期 JWT，也不得在多个 HTTP 尝试中复用同一个 `jti`。



### 3.4 防重放与业务幂等的区别

`jti` 和业务幂等键不是同一个概念：

| 标识 | 作用 | 重试时规则 |
| --- | --- | --- |
| JWT `jti` | 防止同一 HTTP 报文被重放 | 每次 HTTP 尝试都必须生成新值 |
| `orderInfo.orderNo` | 商户业务订单生命周期 | 同一个商户业务订单保持不变；明确失败后再次尝试支付仍使用原值 |
| `orderInfo.orderId` | 单个业务动作幂等 | 同一业务动作的网络重试保持不变；新动作或明确失败后的新支付尝试使用新值 |
| `merchantOrderNo` | 代付业务订单标识 | 同一代付业务保持不变，不得换号绕过结果不确定状态 |

同一个 `jti` 被再次使用会被拒绝。网络超时后不要只更换 `orderId`、`merchantOrderNo` 或源交易号重新发起资金动作，应先按第 10.3 节的重试决策处理。

### 3.5 请求体加密算法

明文业务 JSON 使用一次性 AES-256-GCM 会话密钥加密；AES 会话密钥再使用平台 RSA 公钥通过 RSA-OAEP-256 加密。

算法参数必须完全符合下表：

| 项目 | 固定规则 |
| --- | --- |
| AES 算法 | `AES/GCM/NoPadding` |
| AES key | 每次请求随机生成 32 字节 |
| IV | 每次请求随机生成 12 字节 |
| GCM Tag | 128 bit，即 16 字节 |
| RSA 算法 | `RSA/ECB/OAEPPadding` |
| OAEP Digest | SHA-256 |
| MGF | MGF1-SHA256 |
| RSA 公钥格式 | X.509 SubjectPublicKeyInfo，PEM 或 Base64 |
| Base64 | Base64Url，无 `=` 填充 |
| 明文编码 | UTF-8 |

特别注意：部分语言或加密库在选择 `OAEPWithSHA-256` 时仍默认使用 MGF1-SHA1。商户必须显式指定 MGF1-SHA256，否则平台无法解密。

### 3.6 五段式密文

请求和响应的 `data` 都使用以下五段式 compact 格式：

```text
base64url(protectedHeader).base64url(encryptedKey).base64url(iv).base64url(cipherText).base64url(tag)
```

受保护头固定表达以下 JSON：

```json
{
  "typ": "PAYMENT-PAYLOAD",
  "alg": "RSA-OAEP-256",
  "enc": "A256GCM"
}
```

| 分段 | 内容 |
| --- | --- |
| `protectedHeader` | 上述 JSON 的 UTF-8 字节经无填充 Base64Url 编码 |
| `encryptedKey` | 使用接收方 RSA 公钥加密的 32 字节 AES key |
| `iv` | 12 字节随机 IV |
| `cipherText` | AES-GCM 加密后的密文，不包含末尾 Tag |
| `tag` | 16 字节 GCM 认证标签 |

第一段 `protectedHeader` 的 Base64Url 文本本身必须作为 AES-GCM AAD，按 US-ASCII 字节传入。JSON 字段顺序可以不同，但加密和解密必须使用密文中完全相同的第一段作为 AAD。

### 3.7 请求加密步骤

1. 将接口明文参数序列化为 UTF-8 JSON，不要把外层 `data` 一起加密。
2. 生成 32 字节密码学安全随机 AES key。
3. 生成 12 字节密码学安全随机 IV。
4. 构建受保护头并生成第一段 Base64Url 文本。
5. 使用 AES-256-GCM 加密明文，第一段文本作为 AAD。
6. 将 AES-GCM 输出拆分为 `cipherText` 和末尾 16 字节 `tag`。
7. 使用平台公钥和 RSA-OAEP-256 加密 AES key。
8. 将五段内容分别进行无填充 Base64Url 编码并以 `.` 拼接。
9. 将最终字符串放入外层请求体 `data`。

外层请求体固定为：

```json
{
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

以上仅展示结构，花括号内容不是有效密文。

### 3.8 响应解密步骤

1. 解析 HTTP 响应 JSON，读取外层 `code`、`message` 和 `data`。
2. `data` 为空时不要执行解密，直接按外层错误码处理。
3. 将 `data` 按 `.` 拆为五段，并验证受保护头中的 `typ/alg/enc`。
4. 使用商户响应私钥和 RSA-OAEP-256 解密第二段，得到 AES key。
5. 合并 `cipherText` 和 `tag`，使用 AES-256-GCM 解密。
6. AES-GCM 的 AAD 必须使用密文原始第一段文本。
7. 将明文字节按 UTF-8 解析为对应接口的响应对象。

平台不会要求商户把响应私钥上传到 API 请求中。响应私钥一旦泄露，应立即停止使用并联系平台轮换商户响应密钥对。

### 3.9 完整 HTTP 请求和响应结构

```http
POST /api/rest/payment/v1/query HTTP/1.1
Content-Type: application/json
Authorization: Bearer {jwt}

{
  "data": "{five-part-compact-payload}"
}
```

成功返回：

```json
{
  "code": "T200",
  "message": "Success",
  "data": "{five-part-encrypted-response}"
}
```

参数或安全校验失败：

```json
{
  "code": "F402001",
  "message": "Invalid request parameter",
  "data": null
}
```

## 4. 公共业务规则

### 4.1 外层响应和交易结果

所有接口使用统一外层响应：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | String | OpenAPI 请求处理结果码 |
| `message` | String | OpenAPI 请求处理结果描述 |
| `data` | String/null | 成功时通常为加密密文；失败时通常为空 |

支付类接口需要进行三层判断：

1. 外层 `code` 表示 OpenAPI 是否完成鉴权、解密、校验和业务调用。`T200` 表示成功返回加密业务数据；协议、鉴权或参数错误使用外层 `F/Z` 错误码。
2. 解密后的 `transactionInfo.code` 表示当前业务动作的受理结果，例如成功、处理中或失败。
3. 解密后的 `transactionInfo.transactionStatus` 是平台持久化的交易状态，用于查询、回调和后续状态判断。

外层不得使用交易动作码代替 OpenAPI 处理结果。`T201/T202/T203/F207/F210` 等交易结果应放在解密后的 `transactionInfo.code` 中。外层 `code=T200` 不能单独证明支付、授权、请款或退款成功；商户必须解密 `data`，同时检查 `transactionInfo.code` 和 `transactionInfo.transactionStatus`。当二者暂时不一致时，以持久化的 `transactionStatus` 作为交易状态，以内层 `code` 解释本次 API 动作结果；结果不确定时继续查询，不能重复发起资金动作。

### 4.2 标识与幂等

| 字段 | 作用 | 商户要求 |
| --- | --- | --- |
| `merchantInfo.merchantId` | 商户身份 | 必须与 JWT `merchantId` 一致 |
| `orderInfo.orderNo` | 商户业务订单号 | 同一业务订单保持稳定 |
| `orderInfo.orderId` | 本次支付动作幂等标识 | 每个支付动作唯一；同一动作重试保持不变 |
| `transactionInfo.transactionId` | 平台当前交易 ID | 商户必须保存；查询时可用于精确过滤 |
| `transactionInfo.sourceTransactionId` | 后续动作关联的原平台交易 ID | 请款、退款、撤销等后续动作使用 |
| `merchantOrderNo` | 商户代付订单号 | 商户侧唯一并长期保存 |

同一个 `orderInfo.orderId` 只能表示同一个业务动作。使用原 `orderId` 和完全相同的业务内容重试时，平台返回已有结果；金额、币种、订单号、源交易号、商品快照、三类人员快照或其他核心业务内容不同则返回幂等冲突。商户不得通过更换订单号、请求号或源交易号绕过重复请求、状态校验或金额限制。

### 4.3 金额与币种

- 支付类 `orderInfo.amount` 使用主币种单位的十进制金额，例如 USD 10.25 传 `10.25`，金额必须大于 0。
- 平台最多处理两位有效小数。JPY 等零小数币种只接受正整数；USD、EUR 等二位小数币种最多接受两位小数。
- ISO 4217 标准为三位小数的币种只支持第三位为 `0` 的金额。商户传 `KWD 1.23` 时平台按 `1.230` 处理，传 `1.230` 也合法；`1.231` 必须拒绝，平台不得静默舍入或截断非零第三位。
- 代付 `amount` 使用最小币种单位整数，不能按固定两位小数换算。
- 对三位小数币种，代付最小单位 `amount` 必须是 `10` 的整数倍，例如 KWD 1.230 传 `1230`；`1231` 对应非零第三位，平台必须拒绝。
- 商户应调用币种查询接口读取 `defaultFractionDigits`、`minorUnitMultiplier` 和 `minimumAmount`。
- Java 使用 `BigDecimal` 或最小单位整数，其他语言使用十进制定点类型；禁止使用 `float/double` 计算资金金额。
- 币种使用 ISO 4217 三位大写字母代码。
- 国家或地区代码按具体字段使用 ISO 3166-1 alpha-2 或 alpha-3，不得混用。

当标签币种和交易币种不同时，平台按下式计算：

```text
transactionRate = 1 单位 labelCurrency 可兑换的 transactionCurrency 数量
transactionAmount = round(labelAmount * transactionRate, transactionCurrency 支持精度)
```

当交易币种和结算币种不同时，平台按下式计算：

```text
settlementRate = 1 单位 transactionCurrency 可兑换的 settlementCurrency 数量
grossSettlementAmount = transactionAmount * settlementRate
settlementAmount = round(grossSettlementAmount - settlementFeeAmount, settlementCurrency 支持精度)
```

汇率使用十进制定点数，传输精度最多 8 位小数；中间计算至少保留 12 位小数，只在生成对应币种的最终金额时使用 `HALF_UP` 舍入。实际计费方向、汇率来源和费用明细以响应 `billingInfo` 为准，商户不得用展示汇率反推记账金额。

代付最小单位换算示例：

| 币种 | 主币种金额 | `minorUnitMultiplier` | 代付 `amount` |
| --- | ---: | ---: | ---: |
| USD | 10.25 | 100 | 1025 |
| JPY | 100 | 1 | 100 |
| KWD | 1.230 | 1000 | 1230 |

换算结果必须是整数；如产生非零余数，应拒绝请求，不得静默四舍五入。

### 4.4 可选字段和兼容性

- 响应中的可选字段可能缺失或为 `null`，客户端必须兼容。
- 商户不得依赖 JSON 字段顺序。
- 未在本文声明的响应字段不得作为稳定契约使用。
- `cardInfo`、CVV、完整卡号、渠道原始响应和内部失败原因不会在响应中返回。
- 平台可能新增可选响应字段，商户 JSON 反序列化应忽略未知字段。

### 4.5 通知地址字段

首次支付、授权、预授权或 Hosted Checkout 创建请求中的 `transactionInfo.callbackUrl` 是可选通知地址。平台保存该地址作为交易生命周期属性；首次交易及其增量授权、预授权完成、请款、退款、撤销等后续动作进入终态后，均按第 7.11 节的 JWT、密文、ACK、自动重试和人工重发协议通知该地址。商户必须同时实现回调事件幂等和交易查询兜底，不能仅依赖浏览器跳转或单次通知结果确认最终交易状态。

平台不全局要求 `callbackUrl` 必须预登记。开发和测试环境允许使用可访问的 HTTP 或 HTTPS 地址；生产环境强烈建议使用 HTTPS，平台可基于商户安全配置启用域名白名单、禁止内网地址、回环地址和云元数据地址。无论环境如何，回调地址都不得包含用户名、密码或 URL Fragment。

### 4.6 HTTP 状态和客户端超时

- 商户必须同时处理非 2xx HTTP 状态、连接失败、读取超时和合法 JSON 业务响应。
- 不得假定所有错误都通过 HTTP 200 返回。
- 连接超时或读取超时不能证明平台未受理请求。
- 资金类请求结果不确定时先查询，不得立即生成新的业务幂等标识重复提交。
- 具体连接超时、读取超时、限流和 SLA 参数以平台环境接入材料为准。

### 4.7 Sandbox 测试卡

以下卡号由平台提供，仅用于 Sandbox 联调。

| 卡品牌 | 卡号 | 3DS 注册状态 | 建议测试场景 |
| --- | --- | --- | --- |
| `MASTERCARD` | `5123450000000008` | 已注册 | 3DS 已注册卡支付 |
| `MASTERCARD` | `5111111111111118` | 未注册 | 非 3DS 卡支付 |
| `VISA` | `4508750015741019` | 已注册 | 3DS 已注册卡支付 |
| `VISA` | `4012000033330026` | 未注册 | 非 3DS 卡支付 |
| `JCB` | `3528000000000007` | 已注册 | 3DS 已注册卡支付 |
| `DISCOVER` | `6011003179988686` | 已注册 | 3DS 已注册卡支付 |

只有商户已开通并且 Sandbox 渠道支持的卡组织才能成功受理；未开通的卡组织可能返回 `F410` 或 `F413`。测试卡也必须按第 3 章规则加密后放入请求 `data`，不得出现在日志、数据库、MQ、异常文本或监控标签中。

Sandbox 模拟器可通过有效期触发预期交易结果。本文 API 使用四位年份：

| `expirationMonth` | `expirationYear` | 预期模拟结果 |
| --- | --- | --- |
| `01` | `2039` | `APPROVED` |
| `05` | `2039` | `DECLINED` |
| `04` | `2027` | `EXPIRED_CARD` |
| `08` | `2028` | `TIMED_OUT` |

非 American Express 测试卡可使用以下 `securityCode` 触发 CVV 校验结果：`100` 表示 `MATCH`，`101` 表示 `NOT_PROCESSED`，`102` 表示 `NO_MATCH`。渠道模拟结果会映射为平台统一交易结果，商户应按外层 `code` 和解密后的 `transactionInfo.code` 处理，不应依赖渠道原始响应文本。

> **仅限测试：** 上述卡号和 CVV 只能用于 Sandbox 模拟器，不得用于 UAT、Production、真实持卡人交易或外部网站。测试数据可能调整，发生差异时以平台最新通知为准。

## 5. API 一览

| # | 分类 | 接口 | Method | Path |
| ---: | --- | --- | --- | --- |
| 1 | ISO 字典 | 查询国家地区 | POST | `/api/rest/iso/v1/countries/query` |
| 2 | ISO 字典 | 查询币种 | POST | `/api/rest/iso/v1/currencies/query` |
| 3 | 基础数据 | IP 数据检索 | POST | `/api/rest/ip/v1/query` |
| 4 | 基础数据 | 卡 BIN 数据检索 | POST | `/api/rest/card-bin/v1/query` |
| 5 | 收单支付 | 一步支付 | POST | `/api/rest/payment/v1/payment` |
| 6 | 收单支付 | 授权 | POST | `/api/rest/payment/v1/authorization` |
| 7 | 收单支付 | 预授权 | POST | `/api/rest/payment/v1/pre-authorization` |
| 8 | 收单支付 | 增量授权 | POST | `/api/rest/payment/v1/incremental-authorization` |
| 9 | 收单支付 | 预授权完成 | POST | `/api/rest/payment/v1/pre-auth-completion` |
| 10 | 收单支付 | 请款 | POST | `/api/rest/payment/v1/capture` |
| 11 | 收单支付 | 退款 | POST | `/api/rest/payment/v1/refund` |
| 12 | 收单支付 | 撤销 | POST | `/api/rest/payment/v1/void` |
| 13 | 收单支付 | 交易查询 | POST | `/api/rest/payment/v1/query` |
| 14 | Hosted Checkout | 创建收银台会话 | POST | `/api/rest/checkout/v1/session` |
| 15 | 代付（规划中） | 创建代付（规划中） | POST | `/api/rest/payout/v1/create` |

第 1 至 14 项为当前正式开放接口。第 15 项代付接口仅展示规划中的契约草案，当前环境不可调用，不作为商户联调或上线依据。

## 6. ISO 与基础数据接口

### 6.1 查询国家地区

查询平台支持的 ISO 3166 国家或地区。所有查询条件均可选；不传条件时返回全部可用记录。

#### 6.1.1 接口地址

```http
POST /api/rest/iso/v1/countries/query
```



#### 6.1.2 请求方式

> POST



#### 6.1.3 请求参数

| 字段名称            | 类型/最大长度 | 是否必须 | 备注                                  |
| ------------------- | ------------- | -------- | ------------------------------------- |
| alpha2              | String（2）   | O        | ISO 3166-1 alpha-2，两位大写字母      |
| alpha3              | String（3）   | O        | ISO 3166-1 alpha-3，三位大写字母      |
| numeric             | String（3）   | O        | ISO 3166-1 三位数字代码               |
| englishName         | String（128） | O        | 国家/地区英文名称                     |
| shortEnglishName    | String（128） | O        | 国家/地区英文简称                     |
| chineseName         | String（128） | O        | 国家/地区中文名称                     |
| continentCode       | String（2）   | O        | 七大洲代码，例如 `AS`、`EU`、`NA`     |
| primaryLanguageCode | String（6）   | O        | 国家/地区语言代码，例如 `en`、`zh-CN` |
| currencyAlpha3Code  | String（3）   | O        | ISO 4217 三位大写币种代码             |

##### 6.1.3.1 请求参数示例

```json
{
    "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.USBLkW-06rhClN8x3A3KqS1kLcZIxHD1efkh-ycZKrPxxg4nJuljUTVtRXxyUkCi8434Hodcz_q4yHQhyKKfVtbv6h1oeGZbW2kmSc-Yvw8_n7iPX-XwBXQNSsKgagAbeLVjRhQv9myOHlQZwZqoPsdim-AK8ItPFF4xaaZ7ReWmWTD3dwN3KBsj2sUmyEym0uLhXlG78fFufOv3KXnMmsgoBYyT2p0HDmElPYQLbz4nVaLCNo48gj8yA8Imq1HKmJwzphWMEnUY6WiQBUFTR-Km_WoIyb6tiVel-F2WR-I9Ia55aInYuXwevRh3QUlH7j1s0AXOa_tax1EGF-pHBA.Sio9oVqqSidSLVZt.joHSfUPgb_4XcoTDkF864w.dmaxuhlHvzOspFURKYtBIQ"
}
```

**明文请求参数示例**

```json
{
    "alpha3": "USA"
}
```



#### 6.1.4 响应参数

**加密响应外层参数**

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `code` | String（16） | M | OpenAPI 外层处理结果；成功返回业务密文时为 `T200` |
| `message` | String（128） | M | OpenAPI 外层处理结果描述 |
| `data` | String | M | 五段式加密业务响应；商户使用响应私钥解密 |

**解密后 `data` 参数**

解密后的 `data` 类型为 `Array<Object>`；无匹配记录时返回空数组 `[]`。

##### 数组元素子参数

| 名称                   | 类型/最大长度 | 是否必须 | 备注                    |
| --- | --- | --- | --- |
| alpha2 | String（2） | M | ISO 3166-1 两位字母代码 |
| alpha3 | String（3） | M | ISO 3166-1 三位字母代码 |
| numeric | String（3） | M | ISO 3166-1 三位数字代码，保留前导零 |
| englishName            | String（128） | O        | 国家/地区英文名称       |
| shortEnglishName       | String（128） | O        | 国家/地区英文简称       |
| chineseName            | String（128） | O        | 国家/地区中文名称       |
| continentCode | String（2） | O | 七大洲代码，例如 `AS`、`EU`、`NA` |
| continentName | String（128） | O | 洲中文名称 |
| flagEmoji | String（128） | O | 国家或地区图标 |
| primaryLanguageCode | String（6） | O | 主要语言代码，例如 `en`、`zh-CN` |
| primaryLanguageEnglish | String（16）  | O | 主要语言英文名称 |
| primaryLanguageChinese | String（16） | O | 主要语言中文名称 |
| currencyAlpha3Code | String（3） | O | 默认币种代码 |



##### 6.1.4.1 响应参数示例

```json
{
    "code": "T200",
    "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.fAeNZyFsBK3jrPEzvb9CNIpsusTELzKDP-QEV0RT-XH6mJkNoj9Uakgt4dt8UriIOPMyOM0SdIQYliw6UR0XTtx5uxl9ZNWkGnKzsmdTibwGgUG5lceY7YdNdz9eR1Vuuf7HrbtooM3rSN8NhwUrsCHxtv7b_u3I4_9xkvLpZ7cYQ2c4X-AJPJCj32wFn76pJ1TelfNqMt13Eo4-DV6eP-vKjFoPZTxB1LZIIbXybij91IJtALNVFdNW0DcGDK-L8MCaJI_F7mDKFOdlYLeYOJqz8WWFXttUtlTEMlaqEAxBAMfKnWpRkB1D9EO148IRbP_Arp1PWBGsoVZPeQdu8w.u1Lo5Ub3MayoHVvi.TQpPkXkgBu2fZr-wAJkcKD8Jn0wNG2CNPlFK0PCdLll1glnhufsHg_ve80uhtYUi2rnV88aHdTA70Q6ECNFwc_V2BzzrZCmkbfYfhVlOP_7MwgeQHusVBlMmp61ojOKUVljswTgJfOcUhUacgXdhrFXn7ayXYgKbF35B7TllbD3XeyhZpvCimFuo5Vab5xRJa9qmOELDDerqwsD0moTvPbMPkgBpE3748oRHKTG0-uS0OROH-0oKwVVWBpsHkRFavIJciVl7K89c-y-wbFEeZn7n_gIbIdz9dV3zftm6jd3xnQPPgP8cxHk6EvWvO8tnmrgdymoon_rRi6AH5amdEirFxzNFzmq7dTWRnKDVuvXZdaxzQvashZOwSIYGWI5xQpPPichZ-dz9aAArCygf2T6iEFHJL6l7muWjHcKlkK15uf5bEuyjzxYfdWuyHXULUUve1lmX.EsUBWjIp1MB-HJjNGk76ew",
    "message": "Success"
}
```



**明文响应参数示例**

```json
[
    {
        "alpha2": "US",
        "alpha3": "USA",
        "chineseName": "美国",
        "continentCode": "NA",
        "continentName": "北美洲",
        "currencyAlpha3Code": "USD",
        "englishName": "United States of America",
        "flagEmoji": "🇺🇸",
        "numeric": "840",
        "primaryLanguageChinese": "英语",
        "primaryLanguageCode": "en",
        "primaryLanguageEnglish": "English",
        "shortEnglishName": "United States"
    }
]
```



### 6.2 查询币种

查询平台支持的 ISO 4217 币种及其最小单位信息。所有查询条件均可选；不传条件时返回全部可用记录。

#### 6.2.1 接口地址

```http
POST /api/rest/iso/v1/currencies/query
```



#### 6.2.2 请求方式

> POST



#### 6.2.3 请求参数

| 字段名称       | 类型/最大长度 | 是否必须 | 备注                      |
| -------------- | ------------- | -------- | ------------------------- |
| alphabeticCode | String（3）   | O        | ISO 4217 三位大写字母代码 |
| numericCode    | String（3）   | O        | ISO 4217 三位数字代码     |
| englishName    | String（128） | O        | 英文名称，最长 128 个字符 |
| chineseName    | String（128） | O        | 中文名称，最长 128 个字符 |
| currencySymbol | String（16）  | O        | 币种符号，最长 16 个字符  |



##### 6.2.3.1 请求参数示例

```json
{
    "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.DMWPaB2lpWrDt2uPkut4a-4VoJcLqJn81eJScMtOh4NCgnLYvGkzh6lcrUN8sJnFavx1_gx1xM7MhszjUTKPaADYSRoABhsn2t8sMfcQXpDyyUcAy34fuSwOMYsBPZaTj8CYVLt2RMj5f5kqV9ju7eFozdJiRtaGt8JOGbTJafmq14CBhI-4TmmDzhDF_NWUEkPqcf1dwfplKbCKL5rP9LZxAAg0Ym54B6NyHpbcpvCockMO1aEP7dythiin5QzU4rRvWn2wbso9o1EAZmMS69Qhi0FFtVwN_EAFg6uQBd8XahFJP_rzaaxS54LWLEbNnYOeZtVfqhOmRsmsWMz07w.o1XnEx7hwPLAq0oR.PPkoNdxojQEoPiz3F6f--hESkFUGCBxy.b763Pksqf6VcmTqCo3rXBw"
}
```



**明文请求参数示例**

```json
{
    "alphabeticCode": "USD"
}
```



#### 6.2.4 响应参数

**加密响应外层参数**

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `code` | String（16） | M | OpenAPI 外层处理结果；成功返回业务密文时为 `T200` |
| `message` | String（128） | M | OpenAPI 外层处理结果描述 |
| `data` | String | M | 五段式加密业务响应；商户使用响应私钥解密 |

**解密后 `data` 参数**

解密后的 `data` 类型为 `Array<Object>`；无匹配记录时返回空数组 `[]`。

##### 数组元素子参数

| 字段名称              | 类型/最大长度      | 是否必须 | 备注                                |
| --------------------- | ------------------ | -------- | ----------------------------------- |
| alphabeticCode        | String（3）        | M        | ISO 4217 三位字母代码               |
| numericCode           | String（3）        | M        | ISO 4217 三位数字代码               |
| englishName           | String（128）      | O        | 币种英文名称                        |
| chineseName           | String（128）      | O        | 币种中文名称                        |
| defaultFractionDigits | Integer            | M        | 默认辅币位；小于 0 表示没有可靠定义 |
| minorUnitMultiplier   | Integer            | O        | 主币种单位转换为最小单位的倍数      |
| minimumAmount         | BigDecimal（16,8） | O        | 最小金额单位                        |
| currencySymbol        | String（16）       | O        | 币种符号                            |



##### 6.2.4.1 响应参数示例

```json
{
    "code": "T200",
    "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.KVetdH9MxBQpAxcJ-EB1EHDLwpWCtBwLMTlbV2epCKfQZbwGKqPy-c8JylXmVwTE9XX-GkkCnfPpc9G1k_cKC3J_PHSTykla0nOmJL8f9uSxGPSc6XEJmXroKrAlvHHb6XlBUAAVlrYopEJrZm9gse91iI-Vlsk5l3GDjeJn4DiISAR9nW9FOZpmRCRRbr3iRqpI16ZjKdwnTkQy0QPxIsRTy_AG3jYCldw8E_LvimIj-u6hDNvf8O53upvMdauVVNIS7bsP0y3n5ojdIZssG2oAx0HlsLa16xvmrDXJDyRXPvv5STMNNC1QtWuWoBrtCUHsMuh1bblpN9Nf_sNfkQ.hKO9K6xf4j6aQp2t.GMq5eDDBEZttUCbH1-NdOrjqGiEBAgUr1-_ee1uLwQlgTFm9avMbHzWfGXGCYXkMrK3is2Br4XXlZ8NCYEdkrszcauJQ2keTkue8PfCW1FggDN0nz8B6NMRK370p7ERFqwhe2hjSHpXbYCIgI8CPR5BXCk3-0WIm4GBc9YjGNAsHnP9rXpRVFOvPUSsiQNtw3l9Wjm4pEa6F7wMfFA0FvfOVOjaVPs32F0f-b1qjLkAM88IGINzoZRk5tSFRzVzjpg.l4JQa8RIIj6XQ816N9EkNw",
    "message": "Success"
}
```



**明文响应参数示例**

```json
[
    {
        "alphabeticCode": "USD",
        "chineseName": "美元",
        "currencySymbol": "$",
        "defaultFractionDigits": 2,
        "englishName": "US Dollar",
        "minimumAmount": 0.010000,
        "minorUnitMultiplier": 100,
        "numericCode": "840"
    }
]
```

币种精度和最小单位可能随平台字典更新。商户不得在代码中永久假定所有币种均为两位小数。



### 6.3 IP 数据检索

根据单个 IPv4 或 IPv6 地址查询平台当前 IP 库中的归属信息。只支持精确 IP 字面量，不支持域名、CIDR、IP 范围或批量查询。



#### 6.3.1 接口地址

```http
POST /api/rest/ip/v1/query
```



#### 6.3.2 请求方式

> POST



#### 6.3.3 请求参数

| 字段名称  | 类型/最大长度 | 是否必须 | 备注                                     |
| --------- | ------------- | -------- | ---------------------------------------- |
| ipAddress | String（64）  | M        | 标准 IPv4 或 IPv6 字面量，最长 64 个字符 |



##### 6.3.3.1 请求参数示例

```json
{
    "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.Hqxm281a5dk8ANTM52gHJrkbq9m-bn3L69Y9iFfiB_ZeZkWFAnVUnICEsqHSiGB-wlzowrgRLnbTxFuwphYaDM7xw2xzvvxhMuCS4uNYXc7yTGqWymKbnmg7rq2Nweo_tAWXaii20-oB4gdb7fVzXlhF-XN9PuG3qJVUT37vmYGqX1hA13HP01q1erGgVedWuVY3lB2NRx2uk0Pwjh4zMvFbJfV0_IEhvinWJP5No3cfd2mg03nDQhnj92zWhADnbgmG0UaYpYBOK2u9awVFz7CvNwXsI-bzabsohzPs9K8I9dFRa4B78szdczfue8mXOLga6-cxnKH3ywRR8VrRXw.7xczuPgRsdCzL_C7.jOf1Kwwh11VcbN3QLRsbaJXs-sc.99jgVWY-hRsb_KtFOv7_5w"
}
```



**明文请求参数示例**

```json
{
    "ipAddress": "2001:4860:4860::8888"
}
```



#### 6.3.4 响应参数

**加密响应外层参数**

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `code` | String（16） | M | OpenAPI 外层处理结果；成功返回业务密文时为 `T200` |
| `message` | String（128） | M | OpenAPI 外层处理结果描述 |
| `data` | String | M | 五段式加密业务响应；商户使用响应私钥解密 |

**解密后 `data` 参数**

| 字段名称 | 类型/最大长度 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| `matched` | Boolean | M | 是否命中当前有效 IP 归属区间 |
| `ipAddress` | String（64） | M | 平台规范化后的 IP 地址 |
| `ipType` | String（8） | C | `IPV4` 或 `IPV6` |
| `countryAlpha2` | String（2） | O | 国家或地区 ISO Alpha-2 编码，未命中时为空 |
| `countryAlpha3` | String（3） | O | 国家或地区 ISO Alpha-3 编码，未命中时为空 |
| `countryNumeric` | String（3） | O | 国家或地区 ISO Numeric 编码，未命中时为空 |
| `countryName` | String（64） | O | 国家或地区英文名称，未命中时为空 |
| `stateProvince` | String（128） | O | 州或省名称，未命中或数据源未提供时为空 |
| `city` | String（128） | O | 城市名称，未命中或数据源未提供时为空 |



##### 6.3.4.1 响应参数示例

```json
{
    "code": "T200",
    "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.WEbu3Ml-C38i0BwWvMfF_N9720-4eC_bKqSsOiMcBwJaBBZ9U9ZhhrF8ssw_A46AP8tXW18ae4ONBGx2J5kyY_EvGBXGyxkmXTHko8cyyPzOVGL2CYs3G2L4jBZPkokFjH_4sfQxj9I4qHNDIebIzB_V1S8h0Vm_-2Ylt0StmW0xZvUYgluwGlN62bbikM5j9M5jHY15RHP8HQZCB28Owm6P4joNNphUexcNh5IcPCzGQs3NbW5e_0OtHOTMT8Wm-ADQXtjFL44fiyrFkOohgG28C09PGmHXtZNcMBmE8KDT0nuctfXuavk9-MXC_37Yw6kinJsn5RS4cbrxN0qYxA.sU8DKMxOB_VYFt5d.bw70K78v6DFgDcjkOBBeMVWqUTMai2GW1YkdQrHbJ2kuPvX_LoeOh6yEYrHfumZSKPNYxojL8Uqa90AboEVMUiPk6Wkghpt9ruwm2-s-dku3Jkea7iWRPQKCF2IsshuCsMBecFPETHlxr6arcpXJ5UYabGLa08X9lRYfTaU_qYwXV_6HpgYxTkkRaIz-CTS-WEt2uWcJVQS5aHDWW_u4q_4ssA.XJSQ67DPuQAgielNKblAGQ",
    "message": "Success"
}
```



**明文响应参数示例**

```json
{
    "countryAlpha2": "US",
    "countryAlpha3": "USA",
    "countryName": "United States",
    "countryNumeric": "840",
    "ipAddress": "2001:4860:4860::8888",
    "ipType": "IPV6",
    "matched": true
}
```

格式正确但未命中时，接口仍返回 `T200`，解密后的 `data.matched=false`。未命中不是系统异常，商户不应立即高频重试。IP 归属数据属于参考信息，可能随数据版本更新或从库同步存在短暂延迟，不应单独作为付款、开户或风控放行依据。



### 6.4 卡 BIN 数据检索

根据 6 至 11 位纯数字卡 BIN 查询平台当前有效的卡品牌、卡类型和发卡机构归属信息。不得向本接口传入完整卡号、CVV 或其他持卡人数据。



#### 6.4.1 接口地址

```http
POST /api/rest/card-bin/v1/query
```



#### 6.4.2 请求方式

> POST



#### 6.4.3 请求参数

**明文请求参数**

| 字段名称 | 类型/最大长度 | 必填 | 备注（规则） |
| --- | --- | --- | --- |
| cardBin | String（11） | M | 6 至 11 位纯数字，不允许传入完整卡号 |



##### 6.4.3.1 请求参数示例

```json
{
    "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.Hqxm281a5dk8ANTM52gHJrkbq9m-bn3L69Y9iFfiB_ZeZkWFAnVUnICEsqHSiGB-wlzowrgRLnbTxFuwphYaDM7xw2xzvvxhMuCS4uNYXc7yTGqWymKbnmg7rq2Nweo_tAWXaii20-oB4gdb7fVzXlhF-XN9PuG3qJVUT37vmYGqX1hA13HP01q1erGgVedWuVY3lB2NRx2uk0Pwjh4zMvFbJfV0_IEhvinWJP5No3cfd2mg03nDQhnj92zWhADnbgmG0UaYpYBOK2u9awVFz7CvNwXsI-bzabsohzPs9K8I9dFRa4B78szdczfue8mXOLga6-cxnKH3ywRR8VrRXw.7xczuPgRsdCzL_C7.jOf1Kwwh11VcbN3QLRsbaJXs-sc.99jgVWY-hRsb_KtFOv7_5w"
}
```



**明文请求参数示例**

```json
{
  "cardBin": "411111"
}
```



#### 6.4.4 响应参数

**加密响应外层参数**

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `code` | String（16） | M | OpenAPI 外层处理结果；成功返回业务密文时为 `T200` |
| `message` | String（128） | M | OpenAPI 外层处理结果描述 |
| `data` | String | M | 五段式加密业务响应；商户使用响应私钥解密 |

**解密后 `data` 参数**

| 字段名称             | 类型/最大长度 | 是否必须 | 备注                                             |
| --- | --- | --- | --- |
| matched | Boolean | M | 是否命中当前有效卡 BIN 区间 |
| cardBin | String（11） | M | 商户提交的卡 BIN |
| binLength | Integer | C | 命中时返回匹配记录精度，范围为 6 至 11，且不会大于请求长度 |
| cardBrand | String（16） | C | 命中且数据源提供时返回卡品牌代码 |
| cardSubBrand | String（32） | C | 命中且数据源提供时返回卡子品牌或产品名称 |
| cardType | String（16） | C | 命中且数据源提供时返回卡类型代码 |
| cardLevel | String（16） | C | 命中且数据源提供时返回卡等级 |
| issuerCountryName | String（32） | C | 命中且数据源提供时返回发卡国家或地区名称 |
| issuerCountryAlpha2 | String（2） | C | 命中且数据源提供时返回 ISO Alpha-2 编码 |
| issuerCountryAlpha3 | String（3） | C | 命中且数据源提供时返回 ISO Alpha-3 编码 |
| issuerCountryNumeric | String（3） | C | 命中且数据源提供时返回 ISO Numeric 编码 |
| issuerBank | String（128） | C | 命中且数据源提供时返回发卡行名称 |



##### 6.4.4.1 响应参数示例

```json
{
    "code": "T200",
    "message": "Success",
    "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.YejEAwdfdN5VX-rAEjO0kgxUWwP-GEChttJSImFQrGy4PQ5U3Nl2W1DoRch0tcDIg2eFwJmQ1FuwFxstKKRdZdHyBO4a8FUFtsl2qVVmKPPdTNWiSS0D2wdWke0OlIB00LgmThhDGi1scLbe2Zmg-mw9sVa36XzeH0dXfWSF_1xOBScsK4pwPkyRuE0OjYFJyKQVch4D8MKzvzzzGhP5ZZ7iwqaZa3Wp-jGXTMoeRBVAYI1wThiX_hmOzB7gZEJwO_-vdItHtx1X8A3qe4WKVxx7PxEgXtr5uDheXQElJmxxn8snBFWbwE7P0wYHntFxz1tvnokWTq-9lqpHbHEB8Q.D0_FXePf1bms5Lyl.sl_PcLaZ5CH3QsS-yHvVUce-E-s.8AqWzukuGV32yFT_5pgH0g"
}
```



**明文响应参数示例**

```json
{
    "binLength": 6,
    "cardBin": "411111",
    "cardBrand": "VISA",
    "cardLevel": "GOLD",
    "cardSubBrand": "CLASSIC",
    "cardType": "CREDIT",
    "issuerBank": "Example Bank",
    "issuerCountryAlpha2": "US",
    "issuerCountryAlpha3": "USA",
    "issuerCountryName": "United States",
    "issuerCountryNumeric": "840",
    "matched": true
}
```

格式正确但未命中时，接口仍返回 `T200`，解密后的 `data.matched=false`。卡 BIN 数据属于参考信息，可能随数据源和从库同步更新，不能替代卡组织、发卡行或实际交易授权结果。



## 7. 收单业务接口

### 7.1 业务参数对象

收单业务对外接口共用一套对象，不同接口通过条件规则控制必填字段。本文定义商户接入应遵循的正式对外契约。

**顶层对象**

| 字段名称 | 类型 | 适用范围 | 备注 |
| --- | --- | --- | --- |
| merchantInfo | Object | 全部支付接口 | 商户及可选子商户信息 |
| orderInfo | Object | 全部支付接口 | 商户订单、动作幂等号、金额和币种 |
| goodsInfo | List<Object> | 首次支付、授权、预授权和 Hosted Checkout 可选 | 商品或服务明细快照；提供后响应、查询和回调条件返回 |
| billingCardHolderInfo | Object | 一步支付、授权、预授权必传；Hosted Checkout 可选 | 持卡人账单信息；用于风控、资料校验，并在渠道协议支持时透传渠道或收单行 |
| cardInfo | Object | 一步支付、授权、预授权 | 卡号、有效期和 CVV |
| threeDSInfo | Object | 商户已独立完成 3DS 时条件必填 | 3DS 认证输入；响应只返回安全子集 |
| transactionInfo | Object | 首次交易可选，后续动作和查询条件必填 | 平台交易关联、结果和持久化状态 |
| riskInfo | Object | 首次交易可选 | 预留对象；当前版本不参与风控决策，响应不返回 |
| payerInfo | Object | 首次交易和 Hosted Checkout 必传 | 付款人信息；仅 `ipAddress` 必填，其余子字段可选 |
| shippingInfo | Object | 首次交易可选 | 收货信息；提供后参与收货资料风控并作为快照返回 |
| billingInfo | Object | 仅响应 | 平台计算的交易、换汇、结算和费用信息 |

**字段来源与返回规则**

| 分类 | 对象/字段 | 返回规则 |
| --- | --- | --- |
| 请求回显 | `merchantInfo`、`orderInfo` 的请求字段、`goodsInfo`、`billingCardHolderInfo`、`payerInfo`、`shippingInfo`、`transactionInfo.description/callbackUrl/merchantWebsite` | 直接 API 按受理时的请求快照原样返回；Hosted Checkout 的 `billingCardHolderInfo` 按付款人最终提交值返回；查询返回同一交易生命周期保存的快照 |
| 平台生成 | `transactionInfo` 结果字段、`orderInfo` 累计金额、`billingInfo`、`threeDSInfo` 安全子集 | 由平台计算、持久化或从渠道标准化后返回 |
| 禁止返回 | `cardInfo`、`riskInfo`、CVV/CVC、完整 PAN、3DS 原始认证载荷和渠道原始响应 | 任何支付响应、交易查询和商户回调都不得返回 |

“原样返回”是指字段值按平台成功受理时保存的请求快照返回，不保证 JSON 字段顺序，也不包含平台未受理或已按安全规则拒绝保存的未知字段。

**人员资料与风控处理规则**

- `billingCardHolderInfo` 是持卡人账单资料，用于付款人身份与地址校验、AML、黑名单和白名单规则，并在渠道协议支持时透传渠道或收单行。一步支付、授权和预授权必须提供完整对象；Hosted Checkout 可由商户预填或由付款人在页面补充。
- `payerInfo` 是付款人资料。首次交易和 Hosted Checkout 必须提供该对象，其中只有 `ipAddress` 必填；其他子字段提供后参与付款人 AML、黑名单和白名单校验。业务上 `payerInfo` 通常与 `billingCardHolderInfo` 一致，但平台不要求两个对象的可选字段完全相同。
- `payerInfo.ipAddress` 必须是付款人实际使用的公网 IPv4 或 IPv6 地址，用于 IP 地址/区间 AML、IP 地址/区间黑名单、高风险区域黑名单、IP 地址白名单和交易国家/地区白名单。该字段不是商户服务器调用 OpenAPI 时的出口 IP；商户应从受信任的边缘网关或反向代理获取付款人 IP，不得直接采用付款人可伪造的任意转发 Header。
- `shippingInfo` 提供后用于收货人和收货地址的 AML、黑名单、白名单及资料一致性校验。
- `riskInfo` 是为后续版本保留的扩展对象，当前版本商户可省略，平台不使用其字段作出风控决策，也不会在响应、查询或回调中返回该对象。

| 对外对象/字段 | 内部处理用途 | 使用阶段 | 渠道处理 | 快照及返回规则 |
| --- | --- | --- | --- | --- |
| `billingCardHolderInfo` | 付款人身份、联系方式、账单地址校验及相关 AML、黑白名单规则 | 直接 API 受理时；Hosted Checkout 使用付款人最终提交值 | 渠道协议支持时透传渠道或收单行 | 保存交易快照；支付响应、查询和回调返回，Hosted Checkout 返回最终值 |
| `payerInfo` 可选子字段 | 付款人 AML、黑名单和白名单校验 | 首次交易受理及 Hosted Checkout 会话风控 | 不作为本版统一渠道透传契约 | 保存商户请求快照；响应、查询和回调原样返回 |
| `payerInfo.ipAddress` | IP 归属识别、IP 地址/区间 AML、IP 黑白名单、高风险区域及交易国家/地区白名单 | 首次交易受理；Hosted Checkout 还可结合浏览器连接 IP 校验 | 不作为本版统一渠道透传契约 | 必须保存并原样返回商户请求值 |
| `shippingInfo` | 收货人、收货地址 AML、黑名单、白名单及资料一致性校验 | 首次交易提供时 | 渠道需要且协议支持时使用 | 提供后保存快照；响应、查询和回调条件返回 |
| `riskInfo` | 当前版本不使用，预留后续扩展 | 无 | 不透传 | 不作为有效风控快照，不在任何商户响应中返回 |



#### 7.1.1 merchantInfo（商户信息）

| 字段名称        | 类型/最大长度 | 是否必须 | 备注       |
| --------------- | ------------- | -------- | ---------- |
| merchantId      | String（16）  | M        | 商户号     |
| subMerchantInfo | Object        | O        | 子商户信息 |



##### 7.1.1.1 merchantInfo.subMerchantInfo（子商户信息）

子商户支持两种模式：

- 已在平台注册的子商户：传 `subId` 即可引用已登记资料；如果同时传其他字段，这些字段必须与登记资料一致。
- 未注册或临时子商户：必须传完整经营主体资料，且 `subName`、`subCompanyName` 至少填写一个。

| 字段名称 | 类型/最大长度 | 已注册模式 | 临时模式 | 备注 |
| --- | --- | --- | --- | --- |
| subId | String（32） | M | M | 商户侧或平台约定的子商户 ID |
| subName | String（128） | O | C | 与 `subCompanyName` 至少填写一个 |
| subCompanyName | String（128） | O | C | 与 `subName` 至少填写一个 |
| subCountryCode | String（3） | O | M | ISO 3166-1 alpha-3 |
| subState | String（3） | O | O | 州/省/地区；适用国家使用标准代码 |
| subCity | String（64） | O | M | 城市 |
| subStreet | String（128） | O | M | 街道地址 |
| subEmail | String（64） | O | O | 联系人邮箱 |
| subPhone | String（32） | O | O | 联系电话 |
| subPostal | String（32） | O | O | 邮编 |
| subTaxId | String（32） | O | O | 税号，不得包含中文字符 |
| merchantCategory | String（4） | O | M | 四位 MCC |
| intesCode | String（4） | O | O | 特定卡组要求时传 3 至 4 位字母或数字 |
| chargeType | String（3） | O | O | 特定卡组要求时传 3 位字母或数字 |



#### 7.1.2 orderInfo（订单信息）

| 字段名称                    | 类型/最大长度       | 备注                                                         |
| --- | --- | --- |
| amount | BigDecimal（12,2） | 交易金额，主币种单位；精度规则见 4.3 和 6.2 |
| currency | String（3） | 交易币种                                                     |
| orderNo | String（64） | 商户订单号，同一订单号只能成功一笔                           |
| orderId | String（64） | 每个业务动作唯一；同一动作的网络重试必须复用 |
| totalAuthorizedAmount | BigDecimal（12,2） | 平台响应字段，累计授权成功总金额 |
| totalCapturedAmount | BigDecimal（12,2） | 平台响应字段，累计请款/预授权完成成功总金额 |
| totalRefundAmount | BigDecimal（12,2） | 平台响应字段，累计退款成功总金额 |
| totalAuthorizedCancelAmount | BigDecimal（12,2） | 平台响应字段，累计撤销或授权释放成功总金额 |
| totalRefuseAmount | BigDecimal（12,2） | 平台响应字段，累计拒付总金额 |



#### 7.1.3 billingCardHolderInfo（持卡人信息）

本对象是重要的风控和渠道字段。平台使用其身份、联系方式和账单地址执行付款人资料校验、AML、黑名单和白名单规则；字段校验通过且渠道协议支持时，平台将对应资料透传渠道或收单行。

| 字段名称  | 类型/最大长度 | 备注                                                         |
| --- | --- | --- |
| firstName | String（32） | 持卡人名（必须是英文） |
| lastName | String（32） | 持卡人姓（必须是英文） |
| phone | String（32） | 持卡人电话号码 |
| email | String（64） | 持卡人有效邮箱，最长 64 个字符 |
| country | String（3） | 持卡人所在国家，ISO 3166-1 alpha-3 |
| state | String（3） | 持卡人所在州/省/地区；其中USA、CAN两个国家必须遵循其规范：州省使用2位大写字母 |
| city | String（64） | 持卡人所在城市 |
| street | String（128） | 持卡人所在街道 |
| postal | String（32） | 持卡人邮编 |



#### 7.1.4 cardInfo（卡信息）

| 字段名称        | 类型/最大长度 | 备注                |
| --- | --- | --- |
| cardNo | String（19） | 11 至 19 位数字 PAN |
| expirationMonth | String（2） | `01` 至 `12` |
| expirationYear | String（4） | 四位年份 |
| securityCode | String（4） | 3 至 4 位 CVV/CVC |

完整 PAN 和 CVV 只能存在于商户受控的支付采集流程、内存中的明文对象和加密后的 `data` 中。严禁写入日志、数据库、MQ、异常文本或监控标签。



#### 7.1.5 threeDSInfo（3DS信息）

商户使用平台托管 3DS 时不传本对象。商户在平台外已完成 3DS 并希望平台使用认证结果发起交易时，四个请求字段必须同时提供。

| 字段名称 | 类型/最大长度 | 请求 | 响应 | 备注 |
| --- | --- | --- | --- | --- |
| eci | String（2） | C | O | 两位数字 ECI，例如 `05`、`06`、`07` 或卡组对应值 |
| cavv | String（28） | C | 不返回 | Base64 认证值；只用于提交授权 |
| dsTransactionId | String（36） | C | O | 3DS 2.x Directory Server Transaction ID，UUID 格式 |
| threeDsVersion | String（8） | C | O | 协议版本，例如 `2.1.0`、`2.2.0` |
| status | String（1） | O | O | 标准化认证状态：`Y/A/N/U/R/C` 等 |
| liabilityShifted | Boolean | 不传 | O | 平台根据卡组、ECI 和认证结果计算的责任转移标识 |

响应不会返回 `cavv`、原始 ARes/CRes/RReq、ACS HTML、完整认证报文或渠道私有字段。`status` 和 `liabilityShifted` 只描述认证结果，不能替代 `transactionStatus` 判断资金交易状态。



#### 7.1.6 riskInfo（风控信息）

| 字段名称           | 类型/最大长度 | 备注 |
| --- | --- | --- |
| customerId | String（64） | 商户体系内稳定客户 ID，1 至 64 个 ASCII 非空白字符 |
| deviceFingerprint | String（128） | 稳定设备指纹，1 至 128 个 ASCII 非空白字符 |
| shippingAddress | String（256） | 收货地址，1 至 256 个 ASCII 可打印字符 |
| shippingPostalCode | String（32） | 2 至 20 位字母、数字、空格或短横线 |
| shippingCountry | String（3） | ISO 3166-1 alpha-3 |

`customerId` 和 `deviceFingerprint` 不得使用姓名、邮箱、卡号或完整原始设备采集报文。

`riskInfo` 是为后续版本保留的扩展对象。当前版本商户可省略，平台不使用本对象作出风控决策；支付响应、交易查询和终态回调均不返回本对象。



#### 7.1.7 transactionInfo（交易信息）

| 字段名称            | 类型/最大长度 | 备注                                                         |
| --- | --- | --- |
| transactionId | String（64） | 交易ID；支付网关唯一标识订单信息                             |
| sourceTransactionId | String（64） | 源交易返回的 `transactionId`；支付系统返回给商户             |
| sourceTransactionDateTime | String（64） | 仅响应字段；平台根据 `sourceTransactionId` 自动检索并返回源交易时间，商户请求不应上送 |
| description | String（128） | 本次交易描述，最长 128 个字符 |
| callbackUrl | String（512） | HTTP/HTTPS 通知地址；环境规则见 4.5；未传时不按本字段通知 |
| merchantWebsite | String（512） | 商户发起交易的网站原始 HTTP/HTTPS URL，最长 512 个字符；必须包含合法主机名且不得包含用户信息 |
| redirectUrl | String（512） | Hosted Checkout 可选返回地址；未提供时停留平台结果页，提供时按第 8.4 节通过浏览器表单 POST 返回商户页面 |
| code | String（16）  | 交易状态码，详情请参考状态码表                               |
| message             | String（128） | 当前交易的响应描述                                           |
| transactionType     | String（32）  | 交易类型，详情请参考交易类型表                               |
| transactionStatus | String（16） | 平台持久化交易状态：`PROCESSING`、`PENDING`、`SUCCESS`、`FAILED` |
| transactionDateTime | String（64） | ISO-8601 offset datetime；示例：2026-08-13T09:57:45.314Z |
| paymentMethod | String（16） | 支付方式，例如 `BANK_CARD` |
| cardBrand | String（16） | 卡品牌，例如 `VISA`、`MASTERCARD`、`AMEX` 或 `JCB` |
| cardBin | String（14） | 脱敏卡摘要，例如前六位 + `****` + 后四位 |
| authCode | String（6） | 授权码 |
| arn | String（32） | ARN/收单参考号，渠道返回时存在 |
| language | String（8） | 浏览器语言，一般商户使用收银台时建议上送，收银台会根据此字段自行展示对应的国际化 |

`code/message/transactionType/transactionStatus/transactionDateTime/paymentMethod/cardBrand/cardBin/authCode/arn` 均为平台响应字段，商户请求不应上送。`merchantWebsite` 会作为交易生命周期属性保存并原样回显；商户启用来源网址限定后，该字段缺失或主机名不匹配可能被风控拒绝。`redirectUrl` 和 `language` 只用于 Hosted Checkout 创建请求，直接支付接口不使用这两个字段。

交易链根时间由平台内部维护，不作为商户 OpenAPI 字段；商户无需在请求中上传，也不会在支付响应、交易查询或终态回调中收到该字段。

交易状态规则：

| `transactionStatus` | 是否终态 | 商户处理 |
| --- | --- | --- |
| `PROCESSING` | 否 | 平台正在处理，保存交易标识并查询，不得重复提交资金动作 |
| `PENDING` | 否 | 等待 3DS、渠道异步结果或其他外部动作，查询或等待已开通的通知 |
| `SUCCESS` | 是 | 当前交易动作成功，可按交易类型推进商户订单 |
| `FAILED` | 是 | 当前交易动作失败；允许时使用新的业务动作标识发起新尝试 |

`SUCCESS` 和 `FAILED` 为不可逆终态，平台不得用迟到的渠道响应、重复回调或 MQ 重复消费将终态覆盖为其他状态。`transactionInfo.code` 用于说明本次 API 动作结果，不能替代持久化状态机。



#### 7.1.8 payerInfo（付款人信息）

| 字段名称 | 类型/最大长度 | 备注 |
| --- | --- | --- |
| payerId | String（64） | 商户侧付款人标识 |
| firstName | String（32） | 付款人名 |
| lastName | String（32） | 付款人姓 |
| phone | String（32） | 付款人电话号码 |
| email | String（64） | 付款人有效邮箱 |
| country | String（3） | ISO 3166-1 alpha-3 |
| state | String（64） | 州/省/地区 |
| city | String（64） | 城市 |
| street | String（128） | 街道地址 |
| postal | String（32） | 邮编 |
| ipAddress | String（64） | 付款人公网 IPv4 或 IPv6 地址；首次交易及 Hosted Checkout 必填 |
| sessionId | String（128） | 商户侧浏览器会话标识 |
| browserInfo | Object | 浏览器环境信息，不得包含卡数据或密钥 |
| userAgent | String（512） | 浏览器 User-Agent |

`payerInfo` 在首次交易和 Hosted Checkout 请求中必传，其中仅 `ipAddress` 必填，其他子字段可选。商户上送的身份、联系方式和地址字段参与付款人 AML、黑名单和白名单校验；`ipAddress` 参与 IP 地址/区间 AML、IP 地址/区间黑名单、高风险区域黑名单、IP 地址白名单和交易国家/地区白名单。

`payerInfo.ipAddress` 表示付款人公网 IP，不是商户服务器调用 OpenAPI 时的出口 IP。商户应从受信任的边缘网关或反向代理取得该值，不得直接采用付款人可以伪造的任意代理 Header。



> browserInfo 示例

```json
{
  "browser": {
    "name": "Chrome",
    "version": "128.0.0.0",
    "major": "128"
  },
  "engine": {
    "name": "Blink",
    "version": "128.0.0.0"
  },
  "os": {
    "name": "Windows",
    "version": "11"
  },
  "device": {
    "vendor": "Intel",
    "model": "PC",
    "type": "desktop"
  },
  "hardware": {
    "cpuCore": 8,
    "memoryGB": 16,
    "maxTouchPoints": 0
  },
  "network": {
    "online": true,
    "language": "zh-CN",
    "cookieEnabled": true
  },
  "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
}

```



#### 7.1.9 shippingInfo（收货人信息）

本对象提供后，平台使用收货人身份、联系方式和收货地址执行 AML、黑名单、白名单及资料一致性校验。

| 字段名称  | 类型/最大长度 | 备注                                                         |
| --------- | ------------- | ------------------------------------------------------------ |
| firstName | String（32）  | 收货人名（必须是英文）                                       |
| lastName  | String（32）  | 收货人姓（必须是英文）                                       |
| phone     | String（32）  | 收货人电话号码                                               |
| email     | String（64）  | 收货人有效邮箱，最长 64 个字符                               |
| country   | String（3）   | 收货人所在国家，ISO 3166-1 alpha-3                           |
| state     | String（3）   | 收货人所在州/省/地区；其中USA、CAN两个国家必须遵循其规范：州省使用2位大写字母 |
| city      | String（64）  | 收货人所在城市                                               |
| street    | String（128） | 收货人所在街道                                               |
| postal    | String（32）  | 收货人邮编                                                   |



#### 7.1.10 billingInfo（账单信息）

| 字段名称            | 类型/最大长度       | 备注                                                      |
| ------------------- | ------------------- | --------------------------------------------------------- |
| labelAmount         | BigDecimal（12,2） | 商户上送或页面展示金额                                    |
| labelCurrency       | String（3）         | 商户上送或页面展示币种                                    |
| transactionAmount   | BigDecimal（12,2） | 平台发送渠道/卡组/收单行的交易金额                        |
| transactionCurrency | String（3）         | 平台发送渠道/卡组/收单行的交易币种                        |
| transactionRate     | BigDecimal（18,8） | 标签金额转换为交易金额的汇率；未换汇时通常为 `1.00000000` |
| rateSource | String（32） | 汇率来源编码，发生换汇时返回 |
| rateTime | String（64） | 汇率报价或生效时间，ISO-8601 offset datetime |
| settlementRate | BigDecimal（18,8） | 1 单位交易币种兑换的结算币种数量 |
| settlementAmount    | BigDecimal（12,2） | 预计或最终结算金额，适用时存在                            |
| settlementCurrency  | String（3）         | 预计或最终结算币种，适用时存在                            |
| settlementFeeAmount | BigDecimal（12,2） | 从毛结算金额中扣除的结算费用合计，适用时存在 |
| feeItems            | List<Object>        | 费用类目；结算完成后展示                                  |

凡外层 `data` 返回收单业务对象，顶层 `billingInfo` 必须存在。尚未形成结算的授权、预授权、增量授权和撤销等动作，仍返回 `labelAmount`、`labelCurrency`、`transactionAmount`、`transactionCurrency` 和 `transactionRate`；`settlementRate`、`settlementAmount`、`settlementCurrency`、`settlementFeeAmount` 和 `feeItems` 仅在平台已完成对应计算时返回，不得为了补齐字段而伪造结算结果。

##### 7.1.10.1 feeItems 子参数

| 字段名称   | 类型/最大长度       | 备注               |
| ---------- | ------------------- | ------------------ |
| categories | String（32）        | 费用类目           |
| amount     | BigDecimal（12,2） | 费用金额           |
| currency   | String（3）         | 费用币种           |
| rate       | BigDecimal（18,8） | 费用转结算币种汇率 |


#### 7.1.11 goodsInfo（商品信息）

`goodsInfo` 是顶层可选 `List<Object>`。首次支付、授权、预授权或 Hosted Checkout 提供后，平台按订单快照保存，并在当前响应、后续动作响应、交易查询和终态回调中条件返回；后续动作请求不要求商户重复上传。

| 字段名称 | 类型/最大长度 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| name | String（128） | M | 商品或服务名称 |
| quantity | Integer | M | 商品数量，必须大于 0 |
| amount | BigDecimal（12,2） | M | 商品行总金额，不是商品单价；必须大于 0 |
| currency | String（3） | M | ISO 4217 三位大写币种代码，必须与 `orderInfo.currency` 一致 |

`goodsInfo[].amount` 只用于商品快照、收银台展示和风险判断，不参与重新计算支付金额。资金处理始终以 `orderInfo.amount` 为准；商户必须保证商品信息真实且与订单业务一致。





### 7.2 支付

支付接口用于发起一步支付。成功时通常在同一交易中完成授权和请款。

#### 7.2.1 接口定义

| 项目 | 值 | 必填 | 说明 |
| --- | --- | --- | --- |
| Method | `POST` | M | 固定为 POST |
| Path | `/api/rest/payment/v1/payment` | M | API v1 接口地址 |
| `Content-Type` | `application/json` | M | 商户显式配置 |
| `Authorization` | `Bearer {JWT}` | M | 每次 HTTP 尝试使用新的 JWT `jti` |

**重复请求与重试规则**

同一业务动作的网络重试必须保持明文业务内容和 `orderInfo.orderId` 不变，但每次重试必须重新生成请求密文和 JWT `jti`。

#### 7.2.2 请求参数

##### 主参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息对象 |
| `orderInfo` | Object | M | 订单信息对象 |
| `goodsInfo` | List<Object> | O | 可选商品或服务明细；提供后作为订单快照保存 |
| `billingCardHolderInfo` | Object | M | 持卡人账单资料 |
| `cardInfo` | Object | M | 卡信息，只能出现在加密请求中 |
| `threeDSInfo` | Object | C | 商户已在平台外完成 3DS 时必填 |
| `transactionInfo` | Object | O | 交易扩展信息 |
| `riskInfo` | Object | O | 后续版本预留对象；当前不参与风控，任何响应均不返回 |
| `payerInfo` | Object | M | 付款人信息；`ipAddress` 必填，其他子字段可选 |
| `shippingInfo` | Object | O | 收货人快照；提供后响应、查询和回调条件必返 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 必须与 JWT `merchantId` 一致 |
| `subMerchantInfo` | Object | O | 子商户信息；不涉及子商户时省略 |


##### subMerchantInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `subId` | String（32） | C | 提供子商户对象时必填 |
| `subName` | String（128） | C | 临时子商户与 `subCompanyName` 至少填写一个 |
| `subCompanyName` | String（128） | C | 临时子商户与 `subName` 至少填写一个 |
| `subCountryCode` | String（3） | C | 临时子商户必填，ISO 3166-1 alpha-3 |
| `subState` | String（3） | O | 州、省或地区代码 |
| `subCity` | String（64） | C | 临时子商户必填 |
| `subStreet` | String（128） | C | 临时子商户必填 |
| `subEmail` | String（64） | O | 子商户联系人邮箱 |
| `subPhone` | String（32） | O | 子商户联系人电话 |
| `subPostal` | String（32） | O | 子商户邮编 |
| `subTaxId` | String（32） | O | 子商户税号，不得包含中文字符 |
| `merchantCategory` | String（4） | C | 临时子商户必填，四位 MCC |
| `intesCode` | String（4） | O | 特定卡组要求时传 3 至 4 位字母或数字 |
| `chargeType` | String（3） | O | 特定卡组要求时传 3 位字母或数字 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 本次交易金额，必须大于 0，精度遵循币种规则 |
| `currency` | String（3） | M | ISO 4217 三位大写币种代码 |
| `orderNo` | String（64） | M | 商户业务订单号 |
| `orderId` | String（64） | M | 本次业务动作幂等标识 |


##### goodsInfo 元素子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | String（128） | M | 商品或服务名称 |
| `quantity` | Integer | M | 商品数量，必须大于 0 |
| `amount` | BigDecimal（12,2） | M | 商品行总金额，不是商品单价 |
| `currency` | String（3） | M | 必须与 `orderInfo.currency` 一致 |


##### billingCardHolderInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | M | 持卡人名，使用英文 |
| `lastName` | String（32） | M | 持卡人姓，使用英文 |
| `phone` | String（32） | M | 持卡人电话号码 |
| `email` | String（64） | M | 持卡人有效邮箱 |
| `country` | String（3） | M | ISO 3166-1 alpha-3 |
| `state` | String（3） | M | 州、省或地区；USA/CAN 使用两位代码 |
| `city` | String（64） | M | 城市 |
| `street` | String（128） | M | 街道地址 |
| `postal` | String（32） | M | 邮编 |


##### cardInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `cardNo` | String（19） | M | 11 至 19 位数字 PAN |
| `expirationMonth` | String（2） | M | 有效期月份，`01` 至 `12` |
| `expirationYear` | String（4） | M | 四位有效期年份 |
| `securityCode` | String（4） | M | 3 至 4 位 CVV/CVC |


##### threeDSInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `eci` | String（2） | C | 提供外部 3DS 结果时必填 |
| `cavv` | String（28） | C | 提供外部 3DS 结果时必填；响应不返回 |
| `dsTransactionId` | String（36） | C | 提供外部 3DS 2.x 结果时必填 |
| `threeDsVersion` | String（8） | C | 提供外部 3DS 结果时必填 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `description` | String（128） | O | 交易描述，受理后原样返回 |
| `callbackUrl` | String（512） | O | 终态通知地址，环境规则见 4.5 |
| `merchantWebsite` | String（512） | O | 商户发起交易的网站 URL |


##### riskInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `customerId` | String（64） | O | 预留客户标识；当前版本不使用 |
| `deviceFingerprint` | String（128） | O | 预留设备指纹；当前版本不使用 |
| `shippingAddress` | String（256） | O | 预留收货地址；当前版本不使用 |
| `shippingPostalCode` | String（32） | O | 预留收货邮编；当前版本不使用 |
| `shippingCountry` | String（3） | O | 预留收货国家；当前版本不使用 |


##### payerInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `payerId` | String（64） | O | 商户侧付款人标识 |
| `firstName` | String（32） | O | 付款人名 |
| `lastName` | String（32） | O | 付款人姓 |
| `phone` | String（32） | O | 付款人电话 |
| `email` | String（64） | O | 付款人邮箱 |
| `country` | String（3） | O | ISO 3166-1 alpha-3 |
| `state` | String（64） | O | 州、省或地区 |
| `city` | String（64） | O | 城市 |
| `street` | String（128） | O | 街道地址 |
| `postal` | String（32） | O | 邮编 |
| `ipAddress` | String（64） | M | 付款人公网 IPv4 或 IPv6 地址；用于 IP AML、黑白名单和国家/地区规则 |
| `sessionId` | String（128） | O | 商户侧浏览器会话标识 |
| `browserInfo` | Object | O | 浏览器环境信息，不得包含卡数据 |
| `userAgent` | String（512） | O | 浏览器 User-Agent |


##### shippingInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | O | 收货人名，使用英文 |
| `lastName` | String（32） | O | 收货人姓，使用英文 |
| `phone` | String（32） | O | 收货人电话 |
| `email` | String（64） | O | 收货人邮箱 |
| `country` | String（3） | O | ISO 3166-1 alpha-3 |
| `state` | String（3） | O | 州、省或地区；USA/CAN 使用两位代码 |
| `city` | String（64） | O | 城市 |
| `street` | String（128） | O | 街道地址 |
| `postal` | String（32） | O | 邮编 |

`billingCardHolderInfo`、`payerInfo` 中商户提供的资料参与付款人 AML、黑名单和白名单校验，`shippingInfo` 提供后参与收货资料的对应校验。`cardInfo`、CVV、完整 PAN、`riskInfo` 和 3DS 敏感值只能出现在加密请求中，不得进入日志、URL、MQ、异常文本或监控标签。

#### 7.2.3 请求密文示例

```json
{
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.2.4 请求明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 10.25,
    "currency": "USD",
    "orderNo": "M202608010001",
    "orderId": "PAY202608010001"
  },
  "goodsInfo": [
    {
      "name": "Wireless Headphones",
      "quantity": 1,
      "amount": 10.25,
      "currency": "USD"
    }
  ],
  "billingCardHolderInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "cardInfo": {
    "cardNo": "{sandbox-card-number}",
    "expirationMonth": "12",
    "expirationYear": "2039",
    "securityCode": "{sandbox-cvv}"
  },
  "threeDSInfo": {
    "eci": "05",
    "cavv": "{base64-authentication-value}",
    "dsTransactionId": "550e8400-e29b-41d4-a716-446655440000",
    "threeDsVersion": "2.2.0"
  },
  "transactionInfo": {
    "description": "Pay order M202608010001",
    "callbackUrl": "https://merchant.example/openapi/payment/callback",
    "merchantWebsite": "https://shop.merchant.example/checkout"
  },
  "payerInfo": {
    "payerId": "CUSTOMER-10002",
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001",
    "ipAddress": "203.0.113.10",
    "sessionId": "SESSION-10002",
    "browserInfo": {
      "browser": {
        "name": "Chrome",
        "version": "128.0.0.0"
      }
    },
    "userAgent": "Mozilla/5.0"
  },
  "shippingInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  }
}
```

#### 7.2.5 响应参数

**加密响应外层参数**

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `code` | String（16） | M | OpenAPI 外层处理结果；成功返回业务密文时为 `T200` |
| `message` | String（128） | M | OpenAPI 外层处理结果描述 |
| `data` | String | M | 五段式加密业务响应；商户使用响应私钥解密 |

**解密后 `data` 参数**

##### 主参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息 |
| `orderInfo` | Object | M | 本次交易及交易生命周期累计金额 |
| `goodsInfo` | List<Object> | C | 首次请求提供时完整原样返回 |
| `billingCardHolderInfo` | Object | M | 首次交易保存的持卡人账单快照 |
| `payerInfo` | Object | M | 首次交易必传的付款人快照 |
| `shippingInfo` | Object | C | 首次交易提供收货人快照时必须完整返回 |
| `threeDSInfo` | Object | C | 交易存在 3DS 安全结果时返回 |
| `transactionInfo` | Object | M | 本次业务动作结果 |
| `billingInfo` | Object | M | 平台计算的金额、换汇、结算及费用信息 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 商户号 |
| `subMerchantInfo` | Object | C | 原交易使用子商户时必须返回 |


##### subMerchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `subId` | String（32） | C | 子商户快照存在时返回 |
| `subName` | String（128） | C | 原请求存在时原样返回 |
| `subCompanyName` | String（128） | C | 原请求存在时原样返回 |
| `subCountryCode` | String（3） | C | 原请求存在时原样返回 |
| `subState` | String（3） | C | 原请求存在时原样返回 |
| `subCity` | String（64） | C | 原请求存在时原样返回 |
| `subStreet` | String（128） | C | 原请求存在时原样返回 |
| `subEmail` | String（64） | C | 原请求存在时原样返回 |
| `subPhone` | String（32） | C | 原请求存在时原样返回 |
| `subPostal` | String（32） | C | 原请求存在时原样返回 |
| `subTaxId` | String（32） | C | 原请求存在时原样返回 |
| `merchantCategory` | String（4） | C | 原请求存在时原样返回 |
| `intesCode` | String（4） | C | 原请求存在时原样返回 |
| `chargeType` | String（3） | C | 原请求存在时原样返回 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 本次支付金额 |
| `currency` | String（3） | M | 交易币种 |
| `orderNo` | String（64） | M | 商户业务订单号 |
| `orderId` | String（64） | M | 本次业务动作幂等标识 |
| `totalAuthorizedAmount` | BigDecimal（12,2） | M | 累计授权成功金额，未发生时返回 `0` |
| `totalCapturedAmount` | BigDecimal（12,2） | M | 累计请款或预授权完成成功金额，未发生时返回 `0` |
| `totalRefundAmount` | BigDecimal（12,2） | M | 累计退款成功金额，未发生时返回 `0` |
| `totalAuthorizedCancelAmount` | BigDecimal（12,2） | M | 累计撤销或授权释放成功金额，未发生时返回 `0` |
| `totalRefuseAmount` | BigDecimal（12,2） | M | 累计拒付金额，未发生时返回 `0` |


##### goodsInfo 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `name` | String（128） | C | 商品或服务名称 |
| `quantity` | Integer | C | 商品数量，必须大于 0 |
| `amount` | BigDecimal（12,2） | C | 商品行总金额，不是商品单价 |
| `currency` | String（3） | C | 必须与 `orderInfo.currency` 一致 |


##### billingCardHolderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | M | 持卡人名 |
| `lastName` | String（32） | M | 持卡人姓 |
| `phone` | String（32） | M | 持卡人电话 |
| `email` | String（64） | M | 持卡人邮箱 |
| `country` | String（3） | M | ISO 3166-1 alpha-3 |
| `state` | String（3） | M | 州、省或地区 |
| `city` | String（64） | M | 城市 |
| `street` | String（128） | M | 街道地址 |
| `postal` | String（32） | M | 邮编 |


##### payerInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `payerId` | String（64） | C | 原请求存在时原样返回 |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（64） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |
| `ipAddress` | String（64） | M | 首次交易必传的付款人公网 IP 原样返回 |
| `sessionId` | String（128） | C | 原请求存在时原样返回 |
| `browserInfo` | Object | C | 原请求存在时原样返回 |
| `userAgent` | String（512） | C | 原请求存在时原样返回 |


##### shippingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（3） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |


##### threeDSInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `eci` | String（2） | C | 安全 ECI；不返回 CAVV |
| `dsTransactionId` | String（36） | C | 3DS 2.x Directory Server Transaction ID |
| `threeDsVersion` | String（8） | C | 3DS 协议版本 |
| `status` | String（1） | C | 标准化认证状态 |
| `liabilityShifted` | Boolean | C | 责任转移标识 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `transactionId` | String（64） | M | 平台交易 ID |
| `sourceTransactionId` | String（64） | O | 后续动作关联的源交易 ID；首次交易不返回 |
| `sourceTransactionDateTime` | String（64） | O | 后续动作源交易时间；首次交易不返回 |
| `description` | String（128） | C | 原请求提供时原样返回 |
| `callbackUrl` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `merchantWebsite` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `redirectUrl` | String（512） | C | Hosted Checkout 创建请求提供时按交易生命周期原样返回 |
| `language` | String（8） | C | Hosted Checkout 请求提供时原样返回 |
| `code` | String（16） | M | 当前动作业务结果码 |
| `message` | String（128） | M | 当前动作业务结果描述 |
| `transactionType` | String（32） | M | 当前交易动作类型 |
| `transactionStatus` | String（16） | M | `PROCESSING/PENDING/SUCCESS/FAILED` |
| `transactionDateTime` | String（64） | M | ISO-8601 offset datetime |
| `paymentMethod` | String（16） | C | 平台已确定实际支付方式时返回 |
| `cardBrand` | String（16） | C | 实际支付方式为银行卡且可识别卡品牌时返回 |
| `cardBin` | String（14） | C | 实际支付方式为银行卡且已生成脱敏卡摘要时返回 |
| `authCode` | String（6） | C | 渠道返回授权码时返回 |
| `arn` | String（32） | C | 渠道返回 ARN 时返回 |


##### billingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `labelAmount` | BigDecimal（12,2） | M | 商户上送或页面展示金额 |
| `labelCurrency` | String（3） | M | 商户上送或页面展示币种 |
| `transactionAmount` | BigDecimal（12,2） | M | 发送至渠道的交易金额 |
| `transactionCurrency` | String（3） | M | 发送至渠道的交易币种 |
| `transactionRate` | BigDecimal（18,8） | M | 标签币种转换为交易币种的汇率；未换汇为 `1` |
| `rateSource` | String（32） | C | 发生换汇并有来源编码时返回 |
| `rateTime` | String（64） | C | 发生换汇并有报价时间时返回 |
| `settlementRate` | BigDecimal（18,8） | C | 已计算结算换汇时返回 |
| `settlementAmount` | BigDecimal（12,2） | C | 已形成预计或最终结算金额时返回 |
| `settlementCurrency` | String（3） | C | 返回结算金额时必须同时返回 |
| `settlementFeeAmount` | BigDecimal（12,2） | C | 存在结算费用时返回 |
| `feeItems` | Array<Object> | C | 存在费用明细时完整返回 |


##### feeItems 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `categories` | String（32） | C | 费用类别 |
| `amount` | BigDecimal（12,2） | C | 费用金额 |
| `currency` | String（3） | C | 费用币种 |
| `rate` | BigDecimal（18,8） | C | 费用转结算币种汇率 |

`cardInfo`、`riskInfo`、CAVV、完整 PAN、CVV、3DS 原始认证载荷和渠道原文永不返回。

#### 7.2.6 响应密文示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.2.7 响应明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 10.25,
    "currency": "USD",
    "orderNo": "M202608010001",
    "orderId": "PAY202608010001",
    "totalAuthorizedAmount": 10.25,
    "totalCapturedAmount": 10.25,
    "totalRefundAmount": 0,
    "totalAuthorizedCancelAmount": 0,
    "totalRefuseAmount": 0
  },
  "goodsInfo": [
    {
      "name": "Wireless Headphones",
      "quantity": 1,
      "amount": 10.25,
      "currency": "USD"
    }
  ],
  "billingCardHolderInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "payerInfo": {
    "payerId": "CUSTOMER-10002",
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001",
    "ipAddress": "203.0.113.10",
    "sessionId": "SESSION-10002",
    "browserInfo": {
      "browser": {
        "name": "Chrome",
        "version": "128.0.0.0"
      }
    },
    "userAgent": "Mozilla/5.0"
  },
  "shippingInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "threeDSInfo": {
    "eci": "05",
    "dsTransactionId": "550e8400-e29b-41d4-a716-446655440000",
    "threeDsVersion": "2.2.0",
    "status": "Y",
    "liabilityShifted": true
  },
  "transactionInfo": {
    "transactionId": "202608011020301230001",
    "code": "T200",
    "message": "Success",
    "transactionType": "PAYMENT",
    "transactionStatus": "SUCCESS",
    "transactionDateTime": "2026-08-01T10:20:30.123+08:00",
    "paymentMethod": "BANK_CARD",
    "cardBrand": "VISA",
    "cardBin": "411111****1111",
    "description": "Pay order M202608010001",
    "callbackUrl": "https://merchant.example/openapi/payment/callback",
    "merchantWebsite": "https://shop.merchant.example/checkout",
    "authCode": "654321",
    "arn": "12345678901234567890123"
  },
  "billingInfo": {
    "labelAmount": 10.25,
    "labelCurrency": "USD",
    "transactionAmount": 10.25,
    "transactionCurrency": "USD",
    "transactionRate": 1.0,
    "settlementRate": 1.0,
    "settlementAmount": 9.95,
    "settlementCurrency": "USD",
    "settlementFeeAmount": 0.3,
    "feeItems": [
      {
        "categories": "PROCESSING_FEE",
        "amount": 0.3,
        "currency": "USD",
        "rate": 1.0
      }
    ]
  }
}
```

#### 7.2.8 业务规则

- 相同 `orderNo` 的支付处于 `PROCESSING`、`PENDING` 或 `SUCCESS` 时，不得换新 `orderId` 重复扣款。
- 只有前一次支付明确进入 `FAILED` 后，才可使用同一 `orderNo` 和新的 `orderId` 发起新的支付尝试。
- 外层 `T200` 不代表支付成功；必须同时判断内层 `transactionInfo.code` 和 `transactionStatus`。


### 7.3 授权

授权接口占用持卡人额度但不完成最终扣款。授权成功后按业务需要发起请款或撤销。

#### 7.3.1 接口定义

| 项目 | 值 | 必填 | 说明 |
| --- | --- | --- | --- |
| Method | `POST` | M | 固定为 POST |
| Path | `/api/rest/payment/v1/authorization` | M | API v1 接口地址 |
| `Content-Type` | `application/json` | M | 商户显式配置 |
| `Authorization` | `Bearer {JWT}` | M | 每次 HTTP 尝试使用新的 JWT `jti` |

**重复请求与重试规则**

同一业务动作的网络重试必须保持明文业务内容和 `orderInfo.orderId` 不变，但每次重试必须重新生成请求密文和 JWT `jti`。

#### 7.3.2 请求参数

##### 主参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息对象 |
| `orderInfo` | Object | M | 订单信息对象 |
| `goodsInfo` | List<Object> | O | 可选商品或服务明细；提供后作为订单快照保存 |
| `billingCardHolderInfo` | Object | M | 持卡人账单资料 |
| `cardInfo` | Object | M | 卡信息，只能出现在加密请求中 |
| `threeDSInfo` | Object | C | 商户已在平台外完成 3DS 时必填 |
| `transactionInfo` | Object | O | 交易扩展信息 |
| `riskInfo` | Object | O | 后续版本预留对象；当前不参与风控，任何响应均不返回 |
| `payerInfo` | Object | M | 付款人信息；`ipAddress` 必填，其他子字段可选 |
| `shippingInfo` | Object | O | 收货人快照；提供后响应、查询和回调条件必返 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 必须与 JWT `merchantId` 一致 |
| `subMerchantInfo` | Object | O | 子商户信息；不涉及子商户时省略 |


##### subMerchantInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `subId` | String（32） | C | 提供子商户对象时必填 |
| `subName` | String（128） | C | 临时子商户与 `subCompanyName` 至少填写一个 |
| `subCompanyName` | String（128） | C | 临时子商户与 `subName` 至少填写一个 |
| `subCountryCode` | String（3） | C | 临时子商户必填，ISO 3166-1 alpha-3 |
| `subState` | String（3） | O | 州、省或地区代码 |
| `subCity` | String（64） | C | 临时子商户必填 |
| `subStreet` | String（128） | C | 临时子商户必填 |
| `subEmail` | String（64） | O | 子商户联系人邮箱 |
| `subPhone` | String（32） | O | 子商户联系人电话 |
| `subPostal` | String（32） | O | 子商户邮编 |
| `subTaxId` | String（32） | O | 子商户税号，不得包含中文字符 |
| `merchantCategory` | String（4） | C | 临时子商户必填，四位 MCC |
| `intesCode` | String（4） | O | 特定卡组要求时传 3 至 4 位字母或数字 |
| `chargeType` | String（3） | O | 特定卡组要求时传 3 位字母或数字 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 本次交易金额，必须大于 0，精度遵循币种规则 |
| `currency` | String（3） | M | ISO 4217 三位大写币种代码 |
| `orderNo` | String（64） | M | 商户业务订单号 |
| `orderId` | String（64） | M | 本次业务动作幂等标识 |


##### goodsInfo 元素子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | String（128） | M | 商品或服务名称 |
| `quantity` | Integer | M | 商品数量，必须大于 0 |
| `amount` | BigDecimal（12,2） | M | 商品行总金额，不是商品单价 |
| `currency` | String（3） | M | 必须与 `orderInfo.currency` 一致 |


##### billingCardHolderInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | M | 持卡人名，使用英文 |
| `lastName` | String（32） | M | 持卡人姓，使用英文 |
| `phone` | String（32） | M | 持卡人电话号码 |
| `email` | String（64） | M | 持卡人有效邮箱 |
| `country` | String（3） | M | ISO 3166-1 alpha-3 |
| `state` | String（3） | M | 州、省或地区；USA/CAN 使用两位代码 |
| `city` | String（64） | M | 城市 |
| `street` | String（128） | M | 街道地址 |
| `postal` | String（32） | M | 邮编 |


##### cardInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `cardNo` | String（19） | M | 11 至 19 位数字 PAN |
| `expirationMonth` | String（2） | M | 有效期月份，`01` 至 `12` |
| `expirationYear` | String（4） | M | 四位有效期年份 |
| `securityCode` | String（4） | M | 3 至 4 位 CVV/CVC |


##### threeDSInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `eci` | String（2） | C | 提供外部 3DS 结果时必填 |
| `cavv` | String（28） | C | 提供外部 3DS 结果时必填；响应不返回 |
| `dsTransactionId` | String（36） | C | 提供外部 3DS 2.x 结果时必填 |
| `threeDsVersion` | String（8） | C | 提供外部 3DS 结果时必填 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `description` | String（128） | O | 交易描述，受理后原样返回 |
| `callbackUrl` | String（512） | O | 终态通知地址，环境规则见 4.5 |
| `merchantWebsite` | String（512） | O | 商户发起交易的网站 URL |


##### riskInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `customerId` | String（64） | O | 预留客户标识；当前版本不使用 |
| `deviceFingerprint` | String（128） | O | 预留设备指纹；当前版本不使用 |
| `shippingAddress` | String（256） | O | 预留收货地址；当前版本不使用 |
| `shippingPostalCode` | String（32） | O | 预留收货邮编；当前版本不使用 |
| `shippingCountry` | String（3） | O | 预留收货国家；当前版本不使用 |


##### payerInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `payerId` | String（64） | O | 商户侧付款人标识 |
| `firstName` | String（32） | O | 付款人名 |
| `lastName` | String（32） | O | 付款人姓 |
| `phone` | String（32） | O | 付款人电话 |
| `email` | String（64） | O | 付款人邮箱 |
| `country` | String（3） | O | ISO 3166-1 alpha-3 |
| `state` | String（64） | O | 州、省或地区 |
| `city` | String（64） | O | 城市 |
| `street` | String（128） | O | 街道地址 |
| `postal` | String（32） | O | 邮编 |
| `ipAddress` | String（64） | M | 付款人公网 IPv4 或 IPv6 地址；用于 IP AML、黑白名单和国家/地区规则 |
| `sessionId` | String（128） | O | 商户侧浏览器会话标识 |
| `browserInfo` | Object | O | 浏览器环境信息，不得包含卡数据 |
| `userAgent` | String（512） | O | 浏览器 User-Agent |


##### shippingInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | O | 收货人名，使用英文 |
| `lastName` | String（32） | O | 收货人姓，使用英文 |
| `phone` | String（32） | O | 收货人电话 |
| `email` | String（64） | O | 收货人邮箱 |
| `country` | String（3） | O | ISO 3166-1 alpha-3 |
| `state` | String（3） | O | 州、省或地区；USA/CAN 使用两位代码 |
| `city` | String（64） | O | 城市 |
| `street` | String（128） | O | 街道地址 |
| `postal` | String（32） | O | 邮编 |

`billingCardHolderInfo`、`payerInfo` 中商户提供的资料参与付款人 AML、黑名单和白名单校验，`shippingInfo` 提供后参与收货资料的对应校验。`cardInfo`、CVV、完整 PAN、`riskInfo` 和 3DS 敏感值只能出现在加密请求中，不得进入日志、URL、MQ、异常文本或监控标签。

#### 7.3.3 请求密文示例

```json
{
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.3.4 请求明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 120.0,
    "currency": "USD",
    "orderNo": "M202608010002",
    "orderId": "AUTH202608010001"
  },
  "goodsInfo": [
    {
      "name": "Travel Booking",
      "quantity": 1,
      "amount": 120.0,
      "currency": "USD"
    }
  ],
  "billingCardHolderInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "cardInfo": {
    "cardNo": "{sandbox-card-number}",
    "expirationMonth": "12",
    "expirationYear": "2039",
    "securityCode": "{sandbox-cvv}"
  },
  "threeDSInfo": {
    "eci": "05",
    "cavv": "{base64-authentication-value}",
    "dsTransactionId": "550e8400-e29b-41d4-a716-446655440000",
    "threeDsVersion": "2.2.0"
  },
  "transactionInfo": {
    "description": "Authorize order M202608010002",
    "callbackUrl": "https://merchant.example/openapi/payment/callback",
    "merchantWebsite": "https://shop.merchant.example/checkout"
  },
  "payerInfo": {
    "payerId": "CUSTOMER-10002",
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001",
    "ipAddress": "203.0.113.10",
    "sessionId": "SESSION-10002",
    "browserInfo": {
      "browser": {
        "name": "Chrome",
        "version": "128.0.0.0"
      }
    },
    "userAgent": "Mozilla/5.0"
  },
  "shippingInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  }
}
```

#### 7.3.5 响应参数

**加密响应外层参数**

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `code` | String（16） | M | OpenAPI 外层处理结果；成功返回业务密文时为 `T200` |
| `message` | String（128） | M | OpenAPI 外层处理结果描述 |
| `data` | String | M | 五段式加密业务响应；商户使用响应私钥解密 |

**解密后 `data` 参数**

##### 主参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息 |
| `orderInfo` | Object | M | 本次交易及交易生命周期累计金额 |
| `goodsInfo` | List<Object> | C | 首次请求提供时完整原样返回 |
| `billingCardHolderInfo` | Object | M | 首次交易保存的持卡人账单快照 |
| `payerInfo` | Object | M | 首次交易必传的付款人快照 |
| `shippingInfo` | Object | C | 首次交易提供收货人快照时必须完整返回 |
| `threeDSInfo` | Object | C | 交易存在 3DS 安全结果时返回 |
| `transactionInfo` | Object | M | 本次业务动作结果 |
| `billingInfo` | Object | M | 平台计算的金额、换汇、结算及费用信息 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 商户号 |
| `subMerchantInfo` | Object | C | 原交易使用子商户时必须返回 |


##### subMerchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `subId` | String（32） | C | 子商户快照存在时返回 |
| `subName` | String（128） | C | 原请求存在时原样返回 |
| `subCompanyName` | String（128） | C | 原请求存在时原样返回 |
| `subCountryCode` | String（3） | C | 原请求存在时原样返回 |
| `subState` | String（3） | C | 原请求存在时原样返回 |
| `subCity` | String（64） | C | 原请求存在时原样返回 |
| `subStreet` | String（128） | C | 原请求存在时原样返回 |
| `subEmail` | String（64） | C | 原请求存在时原样返回 |
| `subPhone` | String（32） | C | 原请求存在时原样返回 |
| `subPostal` | String（32） | C | 原请求存在时原样返回 |
| `subTaxId` | String（32） | C | 原请求存在时原样返回 |
| `merchantCategory` | String（4） | C | 原请求存在时原样返回 |
| `intesCode` | String（4） | C | 原请求存在时原样返回 |
| `chargeType` | String（3） | C | 原请求存在时原样返回 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 本次授权金额 |
| `currency` | String（3） | M | 交易币种 |
| `orderNo` | String（64） | M | 商户业务订单号 |
| `orderId` | String（64） | M | 本次业务动作幂等标识 |
| `totalAuthorizedAmount` | BigDecimal（12,2） | M | 累计授权成功金额，未发生时返回 `0` |
| `totalCapturedAmount` | BigDecimal（12,2） | M | 累计请款或预授权完成成功金额，未发生时返回 `0` |
| `totalRefundAmount` | BigDecimal（12,2） | M | 累计退款成功金额，未发生时返回 `0` |
| `totalAuthorizedCancelAmount` | BigDecimal（12,2） | M | 累计撤销或授权释放成功金额，未发生时返回 `0` |
| `totalRefuseAmount` | BigDecimal（12,2） | M | 累计拒付金额，未发生时返回 `0` |


##### goodsInfo 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `name` | String（128） | C | 商品或服务名称 |
| `quantity` | Integer | C | 商品数量，必须大于 0 |
| `amount` | BigDecimal（12,2） | C | 商品行总金额，不是商品单价 |
| `currency` | String（3） | C | 必须与 `orderInfo.currency` 一致 |


##### billingCardHolderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | M | 持卡人名 |
| `lastName` | String（32） | M | 持卡人姓 |
| `phone` | String（32） | M | 持卡人电话 |
| `email` | String（64） | M | 持卡人邮箱 |
| `country` | String（3） | M | ISO 3166-1 alpha-3 |
| `state` | String（3） | M | 州、省或地区 |
| `city` | String（64） | M | 城市 |
| `street` | String（128） | M | 街道地址 |
| `postal` | String（32） | M | 邮编 |


##### payerInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `payerId` | String（64） | C | 原请求存在时原样返回 |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（64） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |
| `ipAddress` | String（64） | M | 首次交易必传的付款人公网 IP 原样返回 |
| `sessionId` | String（128） | C | 原请求存在时原样返回 |
| `browserInfo` | Object | C | 原请求存在时原样返回 |
| `userAgent` | String（512） | C | 原请求存在时原样返回 |


##### shippingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（3） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |


##### threeDSInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `eci` | String（2） | C | 安全 ECI；不返回 CAVV |
| `dsTransactionId` | String（36） | C | 3DS 2.x Directory Server Transaction ID |
| `threeDsVersion` | String（8） | C | 3DS 协议版本 |
| `status` | String（1） | C | 标准化认证状态 |
| `liabilityShifted` | Boolean | C | 责任转移标识 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `transactionId` | String（64） | M | 平台交易 ID |
| `sourceTransactionId` | String（64） | O | 后续动作关联的源交易 ID；首次交易不返回 |
| `sourceTransactionDateTime` | String（64） | O | 后续动作源交易时间；首次交易不返回 |
| `description` | String（128） | C | 原请求提供时原样返回 |
| `callbackUrl` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `merchantWebsite` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `redirectUrl` | String（512） | C | Hosted Checkout 创建请求提供时按交易生命周期原样返回 |
| `language` | String（8） | C | Hosted Checkout 请求提供时原样返回 |
| `code` | String（16） | M | 当前动作业务结果码 |
| `message` | String（128） | M | 当前动作业务结果描述 |
| `transactionType` | String（32） | M | 当前交易动作类型 |
| `transactionStatus` | String（16） | M | `PROCESSING/PENDING/SUCCESS/FAILED` |
| `transactionDateTime` | String（64） | M | ISO-8601 offset datetime |
| `paymentMethod` | String（16） | C | 平台已确定实际支付方式时返回 |
| `cardBrand` | String（16） | C | 实际支付方式为银行卡且可识别卡品牌时返回 |
| `cardBin` | String（14） | C | 实际支付方式为银行卡且已生成脱敏卡摘要时返回 |
| `authCode` | String（6） | C | 渠道返回授权码时返回 |
| `arn` | String（32） | C | 渠道返回 ARN 时返回 |


##### billingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `labelAmount` | BigDecimal（12,2） | M | 商户上送或页面展示金额 |
| `labelCurrency` | String（3） | M | 商户上送或页面展示币种 |
| `transactionAmount` | BigDecimal（12,2） | M | 发送至渠道的交易金额 |
| `transactionCurrency` | String（3） | M | 发送至渠道的交易币种 |
| `transactionRate` | BigDecimal（18,8） | M | 标签币种转换为交易币种的汇率；未换汇为 `1` |
| `rateSource` | String（32） | C | 发生换汇并有来源编码时返回 |
| `rateTime` | String（64） | C | 发生换汇并有报价时间时返回 |
| `settlementRate` | BigDecimal（18,8） | C | 已计算结算换汇时返回 |
| `settlementAmount` | BigDecimal（12,2） | C | 已形成预计或最终结算金额时返回 |
| `settlementCurrency` | String（3） | C | 返回结算金额时必须同时返回 |
| `settlementFeeAmount` | BigDecimal（12,2） | C | 存在结算费用时返回 |
| `feeItems` | Array<Object> | C | 存在费用明细时完整返回 |


##### feeItems 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `categories` | String（32） | C | 费用类别 |
| `amount` | BigDecimal（12,2） | C | 费用金额 |
| `currency` | String（3） | C | 费用币种 |
| `rate` | BigDecimal（18,8） | C | 费用转结算币种汇率 |

`cardInfo`、`riskInfo`、CAVV、完整 PAN、CVV、3DS 原始认证载荷和渠道原文永不返回。

#### 7.3.6 响应密文示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.3.7 响应明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 120.0,
    "currency": "USD",
    "orderNo": "M202608010002",
    "orderId": "AUTH202608010001",
    "totalAuthorizedAmount": 120.0,
    "totalCapturedAmount": 0,
    "totalRefundAmount": 0,
    "totalAuthorizedCancelAmount": 0,
    "totalRefuseAmount": 0
  },
  "goodsInfo": [
    {
      "name": "Travel Booking",
      "quantity": 1,
      "amount": 120.0,
      "currency": "USD"
    }
  ],
  "billingCardHolderInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "payerInfo": {
    "payerId": "CUSTOMER-10002",
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001",
    "ipAddress": "203.0.113.10",
    "sessionId": "SESSION-10002",
    "browserInfo": {
      "browser": {
        "name": "Chrome",
        "version": "128.0.0.0"
      }
    },
    "userAgent": "Mozilla/5.0"
  },
  "shippingInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "threeDSInfo": {
    "eci": "05",
    "dsTransactionId": "550e8400-e29b-41d4-a716-446655440000",
    "threeDsVersion": "2.2.0",
    "status": "Y",
    "liabilityShifted": true
  },
  "transactionInfo": {
    "transactionId": "202608011030001230002",
    "code": "T200",
    "message": "Success",
    "transactionType": "AUTHORIZATION",
    "transactionStatus": "SUCCESS",
    "transactionDateTime": "2026-08-01T10:30:00.123+08:00",
    "paymentMethod": "BANK_CARD",
    "cardBrand": "VISA",
    "cardBin": "411111****1111",
    "description": "Authorize order M202608010002",
    "callbackUrl": "https://merchant.example/openapi/payment/callback",
    "merchantWebsite": "https://shop.merchant.example/checkout",
    "authCode": "654321"
  },
  "billingInfo": {
    "labelAmount": 120.0,
    "labelCurrency": "USD",
    "transactionAmount": 120.0,
    "transactionCurrency": "USD",
    "transactionRate": 1.0
  }
}
```

#### 7.3.8 业务规则

- 授权成功只增加累计授权金额，不增加累计请款金额。
- 后续请款、增量授权或撤销必须使用本接口返回的 `transactionId` 和 `transactionDateTime`。
- 授权终态为 `SUCCESS` 后仍不是已结算扣款，商户不得按一步支付成功处理。


### 7.4 预授权

预授权用于暂时冻结额度，后续可执行预授权完成、请款、增量授权或撤销。

#### 7.4.1 接口定义

| 项目 | 值 | 必填 | 说明 |
| --- | --- | --- | --- |
| Method | `POST` | M | 固定为 POST |
| Path | `/api/rest/payment/v1/pre-authorization` | M | API v1 接口地址 |
| `Content-Type` | `application/json` | M | 商户显式配置 |
| `Authorization` | `Bearer {JWT}` | M | 每次 HTTP 尝试使用新的 JWT `jti` |

**重复请求与重试规则**

同一业务动作的网络重试必须保持明文业务内容和 `orderInfo.orderId` 不变，但每次重试必须重新生成请求密文和 JWT `jti`。

#### 7.4.2 请求参数

##### 主参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息对象 |
| `orderInfo` | Object | M | 订单信息对象 |
| `goodsInfo` | List<Object> | O | 可选商品或服务明细；提供后作为订单快照保存 |
| `billingCardHolderInfo` | Object | M | 持卡人账单资料 |
| `cardInfo` | Object | M | 卡信息，只能出现在加密请求中 |
| `threeDSInfo` | Object | C | 商户已在平台外完成 3DS 时必填 |
| `transactionInfo` | Object | O | 交易扩展信息 |
| `riskInfo` | Object | O | 后续版本预留对象；当前不参与风控，任何响应均不返回 |
| `payerInfo` | Object | M | 付款人信息；`ipAddress` 必填，其他子字段可选 |
| `shippingInfo` | Object | O | 收货人快照；提供后响应、查询和回调条件必返 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 必须与 JWT `merchantId` 一致 |
| `subMerchantInfo` | Object | O | 子商户信息；不涉及子商户时省略 |


##### subMerchantInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `subId` | String（32） | C | 提供子商户对象时必填 |
| `subName` | String（128） | C | 临时子商户与 `subCompanyName` 至少填写一个 |
| `subCompanyName` | String（128） | C | 临时子商户与 `subName` 至少填写一个 |
| `subCountryCode` | String（3） | C | 临时子商户必填，ISO 3166-1 alpha-3 |
| `subState` | String（3） | O | 州、省或地区代码 |
| `subCity` | String（64） | C | 临时子商户必填 |
| `subStreet` | String（128） | C | 临时子商户必填 |
| `subEmail` | String（64） | O | 子商户联系人邮箱 |
| `subPhone` | String（32） | O | 子商户联系人电话 |
| `subPostal` | String（32） | O | 子商户邮编 |
| `subTaxId` | String（32） | O | 子商户税号，不得包含中文字符 |
| `merchantCategory` | String（4） | C | 临时子商户必填，四位 MCC |
| `intesCode` | String（4） | O | 特定卡组要求时传 3 至 4 位字母或数字 |
| `chargeType` | String（3） | O | 特定卡组要求时传 3 位字母或数字 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 本次交易金额，必须大于 0，精度遵循币种规则 |
| `currency` | String（3） | M | ISO 4217 三位大写币种代码 |
| `orderNo` | String（64） | M | 商户业务订单号 |
| `orderId` | String（64） | M | 本次业务动作幂等标识 |


##### goodsInfo 元素子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | String（128） | M | 商品或服务名称 |
| `quantity` | Integer | M | 商品数量，必须大于 0 |
| `amount` | BigDecimal（12,2） | M | 商品行总金额，不是商品单价 |
| `currency` | String（3） | M | 必须与 `orderInfo.currency` 一致 |


##### billingCardHolderInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | M | 持卡人名，使用英文 |
| `lastName` | String（32） | M | 持卡人姓，使用英文 |
| `phone` | String（32） | M | 持卡人电话号码 |
| `email` | String（64） | M | 持卡人有效邮箱 |
| `country` | String（3） | M | ISO 3166-1 alpha-3 |
| `state` | String（3） | M | 州、省或地区；USA/CAN 使用两位代码 |
| `city` | String（64） | M | 城市 |
| `street` | String（128） | M | 街道地址 |
| `postal` | String（32） | M | 邮编 |


##### cardInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `cardNo` | String（19） | M | 11 至 19 位数字 PAN |
| `expirationMonth` | String（2） | M | 有效期月份，`01` 至 `12` |
| `expirationYear` | String（4） | M | 四位有效期年份 |
| `securityCode` | String（4） | M | 3 至 4 位 CVV/CVC |


##### threeDSInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `eci` | String（2） | C | 提供外部 3DS 结果时必填 |
| `cavv` | String（28） | C | 提供外部 3DS 结果时必填；响应不返回 |
| `dsTransactionId` | String（36） | C | 提供外部 3DS 2.x 结果时必填 |
| `threeDsVersion` | String（8） | C | 提供外部 3DS 结果时必填 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `description` | String（128） | O | 交易描述，受理后原样返回 |
| `callbackUrl` | String（512） | O | 终态通知地址，环境规则见 4.5 |
| `merchantWebsite` | String（512） | O | 商户发起交易的网站 URL |


##### riskInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `customerId` | String（64） | O | 预留客户标识；当前版本不使用 |
| `deviceFingerprint` | String（128） | O | 预留设备指纹；当前版本不使用 |
| `shippingAddress` | String（256） | O | 预留收货地址；当前版本不使用 |
| `shippingPostalCode` | String（32） | O | 预留收货邮编；当前版本不使用 |
| `shippingCountry` | String（3） | O | 预留收货国家；当前版本不使用 |


##### payerInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `payerId` | String（64） | O | 商户侧付款人标识 |
| `firstName` | String（32） | O | 付款人名 |
| `lastName` | String（32） | O | 付款人姓 |
| `phone` | String（32） | O | 付款人电话 |
| `email` | String（64） | O | 付款人邮箱 |
| `country` | String（3） | O | ISO 3166-1 alpha-3 |
| `state` | String（64） | O | 州、省或地区 |
| `city` | String（64） | O | 城市 |
| `street` | String（128） | O | 街道地址 |
| `postal` | String（32） | O | 邮编 |
| `ipAddress` | String（64） | M | 付款人公网 IPv4 或 IPv6 地址；用于 IP AML、黑白名单和国家/地区规则 |
| `sessionId` | String（128） | O | 商户侧浏览器会话标识 |
| `browserInfo` | Object | O | 浏览器环境信息，不得包含卡数据 |
| `userAgent` | String（512） | O | 浏览器 User-Agent |


##### shippingInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | O | 收货人名，使用英文 |
| `lastName` | String（32） | O | 收货人姓，使用英文 |
| `phone` | String（32） | O | 收货人电话 |
| `email` | String（64） | O | 收货人邮箱 |
| `country` | String（3） | O | ISO 3166-1 alpha-3 |
| `state` | String（3） | O | 州、省或地区；USA/CAN 使用两位代码 |
| `city` | String（64） | O | 城市 |
| `street` | String（128） | O | 街道地址 |
| `postal` | String（32） | O | 邮编 |

`billingCardHolderInfo`、`payerInfo` 中商户提供的资料参与付款人 AML、黑名单和白名单校验，`shippingInfo` 提供后参与收货资料的对应校验。`cardInfo`、CVV、完整 PAN、`riskInfo` 和 3DS 敏感值只能出现在加密请求中，不得进入日志、URL、MQ、异常文本或监控标签。

#### 7.4.3 请求密文示例

```json
{
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.4.4 请求明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 200.0,
    "currency": "USD",
    "orderNo": "M202608010003",
    "orderId": "PREAUTH202608010001"
  },
  "goodsInfo": [
    {
      "name": "Hotel Reservation",
      "quantity": 1,
      "amount": 200.0,
      "currency": "USD"
    }
  ],
  "billingCardHolderInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "cardInfo": {
    "cardNo": "{sandbox-card-number}",
    "expirationMonth": "12",
    "expirationYear": "2039",
    "securityCode": "{sandbox-cvv}"
  },
  "threeDSInfo": {
    "eci": "05",
    "cavv": "{base64-authentication-value}",
    "dsTransactionId": "550e8400-e29b-41d4-a716-446655440000",
    "threeDsVersion": "2.2.0"
  },
  "transactionInfo": {
    "description": "Pre-authorize order M202608010003",
    "callbackUrl": "https://merchant.example/openapi/payment/callback",
    "merchantWebsite": "https://shop.merchant.example/checkout"
  },
  "payerInfo": {
    "payerId": "CUSTOMER-10002",
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001",
    "ipAddress": "203.0.113.10",
    "sessionId": "SESSION-10002",
    "browserInfo": {
      "browser": {
        "name": "Chrome",
        "version": "128.0.0.0"
      }
    },
    "userAgent": "Mozilla/5.0"
  },
  "shippingInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  }
}
```

#### 7.4.5 响应参数

**加密响应外层参数**

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `code` | String（16） | M | OpenAPI 外层处理结果；成功返回业务密文时为 `T200` |
| `message` | String（128） | M | OpenAPI 外层处理结果描述 |
| `data` | String | M | 五段式加密业务响应；商户使用响应私钥解密 |

**解密后 `data` 参数**

##### 主参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息 |
| `orderInfo` | Object | M | 本次交易及交易生命周期累计金额 |
| `goodsInfo` | List<Object> | C | 首次请求提供时完整原样返回 |
| `billingCardHolderInfo` | Object | M | 首次交易保存的持卡人账单快照 |
| `payerInfo` | Object | M | 首次交易必传的付款人快照 |
| `shippingInfo` | Object | C | 首次交易提供收货人快照时必须完整返回 |
| `threeDSInfo` | Object | C | 交易存在 3DS 安全结果时返回 |
| `transactionInfo` | Object | M | 本次业务动作结果 |
| `billingInfo` | Object | M | 平台计算的金额、换汇、结算及费用信息 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 商户号 |
| `subMerchantInfo` | Object | C | 原交易使用子商户时必须返回 |


##### subMerchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `subId` | String（32） | C | 子商户快照存在时返回 |
| `subName` | String（128） | C | 原请求存在时原样返回 |
| `subCompanyName` | String（128） | C | 原请求存在时原样返回 |
| `subCountryCode` | String（3） | C | 原请求存在时原样返回 |
| `subState` | String（3） | C | 原请求存在时原样返回 |
| `subCity` | String（64） | C | 原请求存在时原样返回 |
| `subStreet` | String（128） | C | 原请求存在时原样返回 |
| `subEmail` | String（64） | C | 原请求存在时原样返回 |
| `subPhone` | String（32） | C | 原请求存在时原样返回 |
| `subPostal` | String（32） | C | 原请求存在时原样返回 |
| `subTaxId` | String（32） | C | 原请求存在时原样返回 |
| `merchantCategory` | String（4） | C | 原请求存在时原样返回 |
| `intesCode` | String（4） | C | 原请求存在时原样返回 |
| `chargeType` | String（3） | C | 原请求存在时原样返回 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 本次预授权金额 |
| `currency` | String（3） | M | 交易币种 |
| `orderNo` | String（64） | M | 商户业务订单号 |
| `orderId` | String（64） | M | 本次业务动作幂等标识 |
| `totalAuthorizedAmount` | BigDecimal（12,2） | M | 累计授权成功金额，未发生时返回 `0` |
| `totalCapturedAmount` | BigDecimal（12,2） | M | 累计请款或预授权完成成功金额，未发生时返回 `0` |
| `totalRefundAmount` | BigDecimal（12,2） | M | 累计退款成功金额，未发生时返回 `0` |
| `totalAuthorizedCancelAmount` | BigDecimal（12,2） | M | 累计撤销或授权释放成功金额，未发生时返回 `0` |
| `totalRefuseAmount` | BigDecimal（12,2） | M | 累计拒付金额，未发生时返回 `0` |


##### goodsInfo 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `name` | String（128） | C | 商品或服务名称 |
| `quantity` | Integer | C | 商品数量，必须大于 0 |
| `amount` | BigDecimal（12,2） | C | 商品行总金额，不是商品单价 |
| `currency` | String（3） | C | 必须与 `orderInfo.currency` 一致 |


##### billingCardHolderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | M | 持卡人名 |
| `lastName` | String（32） | M | 持卡人姓 |
| `phone` | String（32） | M | 持卡人电话 |
| `email` | String（64） | M | 持卡人邮箱 |
| `country` | String（3） | M | ISO 3166-1 alpha-3 |
| `state` | String（3） | M | 州、省或地区 |
| `city` | String（64） | M | 城市 |
| `street` | String（128） | M | 街道地址 |
| `postal` | String（32） | M | 邮编 |


##### payerInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `payerId` | String（64） | C | 原请求存在时原样返回 |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（64） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |
| `ipAddress` | String（64） | M | 首次交易必传的付款人公网 IP 原样返回 |
| `sessionId` | String（128） | C | 原请求存在时原样返回 |
| `browserInfo` | Object | C | 原请求存在时原样返回 |
| `userAgent` | String（512） | C | 原请求存在时原样返回 |


##### shippingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（3） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |


##### threeDSInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `eci` | String（2） | C | 安全 ECI；不返回 CAVV |
| `dsTransactionId` | String（36） | C | 3DS 2.x Directory Server Transaction ID |
| `threeDsVersion` | String（8） | C | 3DS 协议版本 |
| `status` | String（1） | C | 标准化认证状态 |
| `liabilityShifted` | Boolean | C | 责任转移标识 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `transactionId` | String（64） | M | 平台交易 ID |
| `sourceTransactionId` | String（64） | O | 后续动作关联的源交易 ID；首次交易不返回 |
| `sourceTransactionDateTime` | String（64） | O | 后续动作源交易时间；首次交易不返回 |
| `description` | String（128） | C | 原请求提供时原样返回 |
| `callbackUrl` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `merchantWebsite` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `redirectUrl` | String（512） | C | Hosted Checkout 创建请求提供时按交易生命周期原样返回 |
| `language` | String（8） | C | Hosted Checkout 请求提供时原样返回 |
| `code` | String（16） | M | 当前动作业务结果码 |
| `message` | String（128） | M | 当前动作业务结果描述 |
| `transactionType` | String（32） | M | 当前交易动作类型 |
| `transactionStatus` | String（16） | M | `PROCESSING/PENDING/SUCCESS/FAILED` |
| `transactionDateTime` | String（64） | M | ISO-8601 offset datetime |
| `paymentMethod` | String（16） | C | 平台已确定实际支付方式时返回 |
| `cardBrand` | String（16） | C | 实际支付方式为银行卡且可识别卡品牌时返回 |
| `cardBin` | String（14） | C | 实际支付方式为银行卡且已生成脱敏卡摘要时返回 |
| `authCode` | String（6） | C | 渠道返回授权码时返回 |
| `arn` | String（32） | C | 渠道返回 ARN 时返回 |


##### billingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `labelAmount` | BigDecimal（12,2） | M | 商户上送或页面展示金额 |
| `labelCurrency` | String（3） | M | 商户上送或页面展示币种 |
| `transactionAmount` | BigDecimal（12,2） | M | 发送至渠道的交易金额 |
| `transactionCurrency` | String（3） | M | 发送至渠道的交易币种 |
| `transactionRate` | BigDecimal（18,8） | M | 标签币种转换为交易币种的汇率；未换汇为 `1` |
| `rateSource` | String（32） | C | 发生换汇并有来源编码时返回 |
| `rateTime` | String（64） | C | 发生换汇并有报价时间时返回 |
| `settlementRate` | BigDecimal（18,8） | C | 已计算结算换汇时返回 |
| `settlementAmount` | BigDecimal（12,2） | C | 已形成预计或最终结算金额时返回 |
| `settlementCurrency` | String（3） | C | 返回结算金额时必须同时返回 |
| `settlementFeeAmount` | BigDecimal（12,2） | C | 存在结算费用时返回 |
| `feeItems` | Array<Object> | C | 存在费用明细时完整返回 |


##### feeItems 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `categories` | String（32） | C | 费用类别 |
| `amount` | BigDecimal（12,2） | C | 费用金额 |
| `currency` | String（3） | C | 费用币种 |
| `rate` | BigDecimal（18,8） | C | 费用转结算币种汇率 |

`cardInfo`、`riskInfo`、CAVV、完整 PAN、CVV、3DS 原始认证载荷和渠道原文永不返回。

#### 7.4.6 响应密文示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.4.7 响应明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 200.0,
    "currency": "USD",
    "orderNo": "M202608010003",
    "orderId": "PREAUTH202608010001",
    "totalAuthorizedAmount": 200.0,
    "totalCapturedAmount": 0,
    "totalRefundAmount": 0,
    "totalAuthorizedCancelAmount": 0,
    "totalRefuseAmount": 0
  },
  "goodsInfo": [
    {
      "name": "Hotel Reservation",
      "quantity": 1,
      "amount": 200.0,
      "currency": "USD"
    }
  ],
  "billingCardHolderInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "payerInfo": {
    "payerId": "CUSTOMER-10002",
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001",
    "ipAddress": "203.0.113.10",
    "sessionId": "SESSION-10002",
    "browserInfo": {
      "browser": {
        "name": "Chrome",
        "version": "128.0.0.0"
      }
    },
    "userAgent": "Mozilla/5.0"
  },
  "shippingInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "threeDSInfo": {
    "eci": "05",
    "dsTransactionId": "550e8400-e29b-41d4-a716-446655440000",
    "threeDsVersion": "2.2.0",
    "status": "Y",
    "liabilityShifted": true
  },
  "transactionInfo": {
    "transactionId": "202608011040001230003",
    "code": "T200",
    "message": "Success",
    "transactionType": "PRE_AUTHORIZATION",
    "transactionStatus": "SUCCESS",
    "transactionDateTime": "2026-08-01T10:40:00.123+08:00",
    "paymentMethod": "BANK_CARD",
    "cardBrand": "VISA",
    "cardBin": "411111****1111",
    "description": "Pre-authorize order M202608010003",
    "callbackUrl": "https://merchant.example/openapi/payment/callback",
    "merchantWebsite": "https://shop.merchant.example/checkout",
    "authCode": "654321"
  },
  "billingInfo": {
    "labelAmount": 200.0,
    "labelCurrency": "USD",
    "transactionAmount": 200.0,
    "transactionCurrency": "USD",
    "transactionRate": 1.0
  }
}
```

#### 7.4.8 业务规则

- 预授权成功只冻结额度，不代表已完成扣款。
- 后续动作必须引用平台返回的 `transactionId` 和 `transactionDateTime`。
- 是否允许增量授权、完成、请款或撤销由源交易状态和剩余金额共同决定。


### 7.5 增量授权

对成功的授权或预授权追加授权额度。

#### 7.5.1 接口定义

| 项目 | 值 | 必填 | 说明 |
| --- | --- | --- | --- |
| Method | `POST` | M | 固定为 POST |
| Path | `/api/rest/payment/v1/incremental-authorization` | M | API v1 接口地址 |
| `Content-Type` | `application/json` | M | 商户显式配置 |
| `Authorization` | `Bearer {JWT}` | M | 每次 HTTP 尝试使用新的 JWT `jti` |

**重复请求与重试规则**

同一业务动作的网络重试必须保持明文业务内容和 `orderInfo.orderId` 不变，但每次重试必须重新生成请求密文和 JWT `jti`。

#### 7.5.2 请求参数

##### 主参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息对象 |
| `orderInfo` | Object | M | 本次动作订单信息 |
| `transactionInfo` | Object | M | 源交易关联信息 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 必须与 JWT `merchantId` 一致 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 本次动作金额，必须大于 0 |
| `currency` | String（3） | M | 必须与源交易币种一致 |
| `orderNo` | String（64） | M | 必须与源交易商户订单号一致 |
| `orderId` | String（64） | M | 本次业务动作幂等标识 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sourceTransactionId` | String（64） | M | 源交易返回的 `transactionId` |
| `description` | String（128） | O | 本次动作描述，受理后原样返回 |

平台根据 `sourceTransactionId` 自动检索源交易及其交易时间；请求不接收 `sourceTransactionDateTime`。



#### 7.5.3 请求密文示例

```json
{
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.5.4 请求明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 20.0,
    "currency": "USD",
    "orderNo": "M202608010002",
    "orderId": "INCR202608010001"
  },
  "transactionInfo": {
    "sourceTransactionId": "202608011030001230002",
    "description": "Increase authorization for order M202608010002"
  }
}
```

#### 7.5.5 响应参数

**加密响应外层参数**

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `code` | String（16） | M | OpenAPI 外层处理结果；成功返回业务密文时为 `T200` |
| `message` | String（128） | M | OpenAPI 外层处理结果描述 |
| `data` | String | M | 五段式加密业务响应；商户使用响应私钥解密 |

**解密后 `data` 参数**

##### 主参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息 |
| `orderInfo` | Object | M | 本次交易及交易生命周期累计金额 |
| `goodsInfo` | List<Object> | C | 首次请求提供时完整原样返回 |
| `billingCardHolderInfo` | Object | M | 首次交易保存的持卡人账单快照 |
| `payerInfo` | Object | M | 首次交易必传的付款人快照 |
| `shippingInfo` | Object | C | 首次交易提供收货人快照时必须完整返回 |
| `threeDSInfo` | Object | C | 交易存在 3DS 安全结果时返回 |
| `transactionInfo` | Object | M | 本次业务动作结果 |
| `billingInfo` | Object | M | 平台计算的金额、换汇、结算及费用信息 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 商户号 |
| `subMerchantInfo` | Object | C | 原交易使用子商户时必须返回 |


##### subMerchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `subId` | String（32） | C | 子商户快照存在时返回 |
| `subName` | String（128） | C | 原请求存在时原样返回 |
| `subCompanyName` | String（128） | C | 原请求存在时原样返回 |
| `subCountryCode` | String（3） | C | 原请求存在时原样返回 |
| `subState` | String（3） | C | 原请求存在时原样返回 |
| `subCity` | String（64） | C | 原请求存在时原样返回 |
| `subStreet` | String（128） | C | 原请求存在时原样返回 |
| `subEmail` | String（64） | C | 原请求存在时原样返回 |
| `subPhone` | String（32） | C | 原请求存在时原样返回 |
| `subPostal` | String（32） | C | 原请求存在时原样返回 |
| `subTaxId` | String（32） | C | 原请求存在时原样返回 |
| `merchantCategory` | String（4） | C | 原请求存在时原样返回 |
| `intesCode` | String（4） | C | 原请求存在时原样返回 |
| `chargeType` | String（3） | C | 原请求存在时原样返回 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 本次追加授权金额 |
| `currency` | String（3） | M | 交易币种 |
| `orderNo` | String（64） | M | 商户业务订单号 |
| `orderId` | String（64） | M | 本次业务动作幂等标识 |
| `totalAuthorizedAmount` | BigDecimal（12,2） | M | 累计授权成功金额，未发生时返回 `0` |
| `totalCapturedAmount` | BigDecimal（12,2） | M | 累计请款或预授权完成成功金额，未发生时返回 `0` |
| `totalRefundAmount` | BigDecimal（12,2） | M | 累计退款成功金额，未发生时返回 `0` |
| `totalAuthorizedCancelAmount` | BigDecimal（12,2） | M | 累计撤销或授权释放成功金额，未发生时返回 `0` |
| `totalRefuseAmount` | BigDecimal（12,2） | M | 累计拒付金额，未发生时返回 `0` |


##### goodsInfo 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `name` | String（128） | C | 商品或服务名称 |
| `quantity` | Integer | C | 商品数量，必须大于 0 |
| `amount` | BigDecimal（12,2） | C | 商品行总金额，不是商品单价 |
| `currency` | String（3） | C | 必须与 `orderInfo.currency` 一致 |


##### billingCardHolderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | M | 持卡人名 |
| `lastName` | String（32） | M | 持卡人姓 |
| `phone` | String（32） | M | 持卡人电话 |
| `email` | String（64） | M | 持卡人邮箱 |
| `country` | String（3） | M | ISO 3166-1 alpha-3 |
| `state` | String（3） | M | 州、省或地区 |
| `city` | String（64） | M | 城市 |
| `street` | String（128） | M | 街道地址 |
| `postal` | String（32） | M | 邮编 |


##### payerInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `payerId` | String（64） | C | 原请求存在时原样返回 |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（64） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |
| `ipAddress` | String（64） | M | 首次交易必传的付款人公网 IP 原样返回 |
| `sessionId` | String（128） | C | 原请求存在时原样返回 |
| `browserInfo` | Object | C | 原请求存在时原样返回 |
| `userAgent` | String（512） | C | 原请求存在时原样返回 |


##### shippingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（3） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |


##### threeDSInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `eci` | String（2） | C | 安全 ECI；不返回 CAVV |
| `dsTransactionId` | String（36） | C | 3DS 2.x Directory Server Transaction ID |
| `threeDsVersion` | String（8） | C | 3DS 协议版本 |
| `status` | String（1） | C | 标准化认证状态 |
| `liabilityShifted` | Boolean | C | 责任转移标识 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `transactionId` | String（64） | M | 平台交易 ID |
| `sourceTransactionId` | String（64） | M | 本次后续动作关联的源交易 ID |
| `sourceTransactionDateTime` | String（64） | M | 平台根据 `sourceTransactionId` 自动检索的源交易时间 |
| `description` | String（128） | C | 原请求提供时原样返回 |
| `callbackUrl` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `merchantWebsite` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `redirectUrl` | String（512） | C | Hosted Checkout 创建请求提供时按交易生命周期原样返回 |
| `language` | String（8） | C | Hosted Checkout 请求提供时原样返回 |
| `code` | String（16） | M | 当前动作业务结果码 |
| `message` | String（128） | M | 当前动作业务结果描述 |
| `transactionType` | String（32） | M | 当前交易动作类型 |
| `transactionStatus` | String（16） | M | `PROCESSING/PENDING/SUCCESS/FAILED` |
| `transactionDateTime` | String（64） | M | ISO-8601 offset datetime |
| `paymentMethod` | String（16） | C | 平台已确定实际支付方式时返回 |
| `cardBrand` | String（16） | C | 实际支付方式为银行卡且可识别卡品牌时返回 |
| `cardBin` | String（14） | C | 实际支付方式为银行卡且已生成脱敏卡摘要时返回 |
| `authCode` | String（6） | C | 渠道返回授权码时返回 |
| `arn` | String（32） | C | 渠道返回 ARN 时返回 |


##### billingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `labelAmount` | BigDecimal（12,2） | M | 商户上送或页面展示金额 |
| `labelCurrency` | String（3） | M | 商户上送或页面展示币种 |
| `transactionAmount` | BigDecimal（12,2） | M | 发送至渠道的交易金额 |
| `transactionCurrency` | String（3） | M | 发送至渠道的交易币种 |
| `transactionRate` | BigDecimal（18,8） | M | 标签币种转换为交易币种的汇率；未换汇为 `1` |
| `rateSource` | String（32） | C | 发生换汇并有来源编码时返回 |
| `rateTime` | String（64） | C | 发生换汇并有报价时间时返回 |
| `settlementRate` | BigDecimal（18,8） | C | 已计算结算换汇时返回 |
| `settlementAmount` | BigDecimal（12,2） | C | 已形成预计或最终结算金额时返回 |
| `settlementCurrency` | String（3） | C | 返回结算金额时必须同时返回 |
| `settlementFeeAmount` | BigDecimal（12,2） | C | 存在结算费用时返回 |
| `feeItems` | Array<Object> | C | 存在费用明细时完整返回 |


##### feeItems 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `categories` | String（32） | C | 费用类别 |
| `amount` | BigDecimal（12,2） | C | 费用金额 |
| `currency` | String（3） | C | 费用币种 |
| `rate` | BigDecimal（18,8） | C | 费用转结算币种汇率 |

后续动作响应必须返回首次交易保存的商品和人员快照、当前动作完整交易信息、累计金额和平台计算的 `billingInfo`，不能只返回交易 ID 和状态。`cardInfo`、`riskInfo` 和 3DS 敏感值永不返回。

#### 7.5.6 响应密文示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.5.7 响应明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 20.0,
    "currency": "USD",
    "orderNo": "M202608010002",
    "orderId": "INCR202608010001",
    "totalAuthorizedAmount": 140.0,
    "totalCapturedAmount": 0,
    "totalRefundAmount": 0,
    "totalAuthorizedCancelAmount": 0,
    "totalRefuseAmount": 0
  },
  "goodsInfo": [
    {
      "name": "Travel Booking",
      "quantity": 1,
      "amount": 120.0,
      "currency": "USD"
    }
  ],
  "billingCardHolderInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "payerInfo": {
    "payerId": "CUSTOMER-10002",
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001",
    "ipAddress": "203.0.113.10",
    "sessionId": "SESSION-10002",
    "browserInfo": {
      "browser": {
        "name": "Chrome",
        "version": "128.0.0.0"
      }
    },
    "userAgent": "Mozilla/5.0"
  },
  "shippingInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "threeDSInfo": {
    "eci": "05",
    "dsTransactionId": "550e8400-e29b-41d4-a716-446655440000",
    "threeDsVersion": "2.2.0",
    "status": "Y",
    "liabilityShifted": true
  },
  "transactionInfo": {
    "transactionId": "202608011100001230004",
    "code": "T200",
    "message": "Success",
    "transactionType": "INCREMENTAL_AUTHORIZATION",
    "transactionStatus": "SUCCESS",
    "transactionDateTime": "2026-08-01T11:00:00.123+08:00",
    "paymentMethod": "BANK_CARD",
    "cardBrand": "VISA",
    "cardBin": "411111****1111",
    "description": "Increase authorization for order M202608010002",
    "callbackUrl": "https://merchant.example/openapi/payment/callback",
    "merchantWebsite": "https://shop.merchant.example/checkout",
    "sourceTransactionId": "202608011030001230002",
    "sourceTransactionDateTime": "2026-08-01T10:30:00.123+08:00",
    "authCode": "765432"
  },
  "billingInfo": {
    "labelAmount": 20.0,
    "labelCurrency": "USD",
    "transactionAmount": 20.0,
    "transactionCurrency": "USD",
    "transactionRate": 1.0
  }
}
```

#### 7.5.8 业务规则

- 源交易必须为当前商户成功且允许增量授权的授权或预授权。
- `orderInfo.amount` 是本次追加金额，不是追加后的累计授权金额。
- 金额不能超过平台或渠道允许的增量授权上限。


### 7.6 预授权完成

对一笔允许完成的预授权确认本次最终扣款金额。

#### 7.6.1 接口定义

| 项目 | 值 | 必填 | 说明 |
| --- | --- | --- | --- |
| Method | `POST` | M | 固定为 POST |
| Path | `/api/rest/payment/v1/pre-auth-completion` | M | API v1 接口地址 |
| `Content-Type` | `application/json` | M | 商户显式配置 |
| `Authorization` | `Bearer {JWT}` | M | 每次 HTTP 尝试使用新的 JWT `jti` |

**重复请求与重试规则**

同一业务动作的网络重试必须保持明文业务内容和 `orderInfo.orderId` 不变，但每次重试必须重新生成请求密文和 JWT `jti`。

#### 7.6.2 请求参数

##### 主参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息对象 |
| `orderInfo` | Object | M | 本次动作订单信息 |
| `transactionInfo` | Object | M | 源交易关联信息 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 必须与 JWT `merchantId` 一致 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 本次动作金额，必须大于 0 |
| `currency` | String（3） | M | 必须与源交易币种一致 |
| `orderNo` | String（64） | M | 必须与源交易商户订单号一致 |
| `orderId` | String（64） | M | 本次业务动作幂等标识 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sourceTransactionId` | String（64） | M | 源交易返回的 `transactionId` |
| `description` | String（128） | O | 本次动作描述，受理后原样返回 |

平台根据 `sourceTransactionId` 自动检索源交易及其交易时间；请求不接收 `sourceTransactionDateTime`。



#### 7.6.3 请求密文示例

```json
{
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.6.4 请求明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 180.0,
    "currency": "USD",
    "orderNo": "M202608010003",
    "orderId": "PAC202608010001"
  },
  "transactionInfo": {
    "sourceTransactionId": "202608011040001230003",
    "description": "Complete pre-authorization for order M202608010003"
  }
}
```

#### 7.6.5 响应参数

**加密响应外层参数**

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `code` | String（16） | M | OpenAPI 外层处理结果；成功返回业务密文时为 `T200` |
| `message` | String（128） | M | OpenAPI 外层处理结果描述 |
| `data` | String | M | 五段式加密业务响应；商户使用响应私钥解密 |

**解密后 `data` 参数**

##### 主参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息 |
| `orderInfo` | Object | M | 本次交易及交易生命周期累计金额 |
| `goodsInfo` | List<Object> | C | 首次请求提供时完整原样返回 |
| `billingCardHolderInfo` | Object | M | 首次交易保存的持卡人账单快照 |
| `payerInfo` | Object | M | 首次交易必传的付款人快照 |
| `shippingInfo` | Object | C | 首次交易提供收货人快照时必须完整返回 |
| `threeDSInfo` | Object | C | 交易存在 3DS 安全结果时返回 |
| `transactionInfo` | Object | M | 本次业务动作结果 |
| `billingInfo` | Object | M | 平台计算的金额、换汇、结算及费用信息 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 商户号 |
| `subMerchantInfo` | Object | C | 原交易使用子商户时必须返回 |


##### subMerchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `subId` | String（32） | C | 子商户快照存在时返回 |
| `subName` | String（128） | C | 原请求存在时原样返回 |
| `subCompanyName` | String（128） | C | 原请求存在时原样返回 |
| `subCountryCode` | String（3） | C | 原请求存在时原样返回 |
| `subState` | String（3） | C | 原请求存在时原样返回 |
| `subCity` | String（64） | C | 原请求存在时原样返回 |
| `subStreet` | String（128） | C | 原请求存在时原样返回 |
| `subEmail` | String（64） | C | 原请求存在时原样返回 |
| `subPhone` | String（32） | C | 原请求存在时原样返回 |
| `subPostal` | String（32） | C | 原请求存在时原样返回 |
| `subTaxId` | String（32） | C | 原请求存在时原样返回 |
| `merchantCategory` | String（4） | C | 原请求存在时原样返回 |
| `intesCode` | String（4） | C | 原请求存在时原样返回 |
| `chargeType` | String（3） | C | 原请求存在时原样返回 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 本次预授权完成金额 |
| `currency` | String（3） | M | 交易币种 |
| `orderNo` | String（64） | M | 商户业务订单号 |
| `orderId` | String（64） | M | 本次业务动作幂等标识 |
| `totalAuthorizedAmount` | BigDecimal（12,2） | M | 累计授权成功金额，未发生时返回 `0` |
| `totalCapturedAmount` | BigDecimal（12,2） | M | 累计请款或预授权完成成功金额，未发生时返回 `0` |
| `totalRefundAmount` | BigDecimal（12,2） | M | 累计退款成功金额，未发生时返回 `0` |
| `totalAuthorizedCancelAmount` | BigDecimal（12,2） | M | 累计撤销或授权释放成功金额，未发生时返回 `0` |
| `totalRefuseAmount` | BigDecimal（12,2） | M | 累计拒付金额，未发生时返回 `0` |


##### goodsInfo 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `name` | String（128） | C | 商品或服务名称 |
| `quantity` | Integer | C | 商品数量，必须大于 0 |
| `amount` | BigDecimal（12,2） | C | 商品行总金额，不是商品单价 |
| `currency` | String（3） | C | 必须与 `orderInfo.currency` 一致 |


##### billingCardHolderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | M | 持卡人名 |
| `lastName` | String（32） | M | 持卡人姓 |
| `phone` | String（32） | M | 持卡人电话 |
| `email` | String（64） | M | 持卡人邮箱 |
| `country` | String（3） | M | ISO 3166-1 alpha-3 |
| `state` | String（3） | M | 州、省或地区 |
| `city` | String（64） | M | 城市 |
| `street` | String（128） | M | 街道地址 |
| `postal` | String（32） | M | 邮编 |


##### payerInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `payerId` | String（64） | C | 原请求存在时原样返回 |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（64） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |
| `ipAddress` | String（64） | M | 首次交易必传的付款人公网 IP 原样返回 |
| `sessionId` | String（128） | C | 原请求存在时原样返回 |
| `browserInfo` | Object | C | 原请求存在时原样返回 |
| `userAgent` | String（512） | C | 原请求存在时原样返回 |


##### shippingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（3） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |


##### threeDSInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `eci` | String（2） | C | 安全 ECI；不返回 CAVV |
| `dsTransactionId` | String（36） | C | 3DS 2.x Directory Server Transaction ID |
| `threeDsVersion` | String（8） | C | 3DS 协议版本 |
| `status` | String（1） | C | 标准化认证状态 |
| `liabilityShifted` | Boolean | C | 责任转移标识 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `transactionId` | String（64） | M | 平台交易 ID |
| `sourceTransactionId` | String（64） | M | 本次后续动作关联的源交易 ID |
| `sourceTransactionDateTime` | String（64） | M | 平台根据 `sourceTransactionId` 自动检索的源交易时间 |
| `description` | String（128） | C | 原请求提供时原样返回 |
| `callbackUrl` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `merchantWebsite` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `redirectUrl` | String（512） | C | Hosted Checkout 创建请求提供时按交易生命周期原样返回 |
| `language` | String（8） | C | Hosted Checkout 请求提供时原样返回 |
| `code` | String（16） | M | 当前动作业务结果码 |
| `message` | String（128） | M | 当前动作业务结果描述 |
| `transactionType` | String（32） | M | 当前交易动作类型 |
| `transactionStatus` | String（16） | M | `PROCESSING/PENDING/SUCCESS/FAILED` |
| `transactionDateTime` | String（64） | M | ISO-8601 offset datetime |
| `paymentMethod` | String（16） | C | 平台已确定实际支付方式时返回 |
| `cardBrand` | String（16） | C | 实际支付方式为银行卡且可识别卡品牌时返回 |
| `cardBin` | String（14） | C | 实际支付方式为银行卡且已生成脱敏卡摘要时返回 |
| `authCode` | String（6） | C | 渠道返回授权码时返回 |
| `arn` | String（32） | C | 渠道返回 ARN 时返回 |


##### billingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `labelAmount` | BigDecimal（12,2） | M | 商户上送或页面展示金额 |
| `labelCurrency` | String（3） | M | 商户上送或页面展示币种 |
| `transactionAmount` | BigDecimal（12,2） | M | 发送至渠道的交易金额 |
| `transactionCurrency` | String（3） | M | 发送至渠道的交易币种 |
| `transactionRate` | BigDecimal（18,8） | M | 标签币种转换为交易币种的汇率；未换汇为 `1` |
| `rateSource` | String（32） | C | 发生换汇并有来源编码时返回 |
| `rateTime` | String（64） | C | 发生换汇并有报价时间时返回 |
| `settlementRate` | BigDecimal（18,8） | C | 已计算结算换汇时返回 |
| `settlementAmount` | BigDecimal（12,2） | C | 已形成预计或最终结算金额时返回 |
| `settlementCurrency` | String（3） | C | 返回结算金额时必须同时返回 |
| `settlementFeeAmount` | BigDecimal（12,2） | C | 存在结算费用时返回 |
| `feeItems` | Array<Object> | C | 存在费用明细时完整返回 |


##### feeItems 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `categories` | String（32） | C | 费用类别 |
| `amount` | BigDecimal（12,2） | C | 费用金额 |
| `currency` | String（3） | C | 费用币种 |
| `rate` | BigDecimal（18,8） | C | 费用转结算币种汇率 |

后续动作响应必须返回首次交易保存的商品和人员快照、当前动作完整交易信息、累计金额和平台计算的 `billingInfo`，不能只返回交易 ID 和状态。`cardInfo`、`riskInfo` 和 3DS 敏感值永不返回。

#### 7.6.6 响应密文示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.6.7 响应明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 180.0,
    "currency": "USD",
    "orderNo": "M202608010003",
    "orderId": "PAC202608010001",
    "totalAuthorizedAmount": 200.0,
    "totalCapturedAmount": 180.0,
    "totalRefundAmount": 0,
    "totalAuthorizedCancelAmount": 0,
    "totalRefuseAmount": 0
  },
  "goodsInfo": [
    {
      "name": "Hotel Reservation",
      "quantity": 1,
      "amount": 200.0,
      "currency": "USD"
    }
  ],
  "billingCardHolderInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "payerInfo": {
    "payerId": "CUSTOMER-10002",
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001",
    "ipAddress": "203.0.113.10",
    "sessionId": "SESSION-10002",
    "browserInfo": {
      "browser": {
        "name": "Chrome",
        "version": "128.0.0.0"
      }
    },
    "userAgent": "Mozilla/5.0"
  },
  "shippingInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "threeDSInfo": {
    "eci": "05",
    "dsTransactionId": "550e8400-e29b-41d4-a716-446655440000",
    "threeDsVersion": "2.2.0",
    "status": "Y",
    "liabilityShifted": true
  },
  "transactionInfo": {
    "transactionId": "202608011110001230005",
    "code": "T200",
    "message": "Success",
    "transactionType": "PRE_AUTH_COMPLETION",
    "transactionStatus": "SUCCESS",
    "transactionDateTime": "2026-08-01T11:10:00.123+08:00",
    "paymentMethod": "BANK_CARD",
    "cardBrand": "VISA",
    "cardBin": "411111****1111",
    "description": "Complete pre-authorization for order M202608010003",
    "callbackUrl": "https://merchant.example/openapi/payment/callback",
    "merchantWebsite": "https://shop.merchant.example/checkout",
    "sourceTransactionId": "202608011040001230003",
    "sourceTransactionDateTime": "2026-08-01T10:40:00.123+08:00"
  },
  "billingInfo": {
    "labelAmount": 180.0,
    "labelCurrency": "USD",
    "transactionAmount": 180.0,
    "transactionCurrency": "USD",
    "transactionRate": 1.0,
    "settlementRate": 1.0,
    "settlementAmount": 179.7,
    "settlementCurrency": "USD",
    "settlementFeeAmount": 0.3,
    "feeItems": [
      {
        "categories": "PROCESSING_FEE",
        "amount": 0.3,
        "currency": "USD",
        "rate": 1.0
      }
    ]
  }
}
```

#### 7.6.8 业务规则

- 源交易必须为成功且仍允许完成的预授权。
- 本次金额不能超过源交易剩余可完成金额。
- 币种必须与源交易一致。


### 7.7 请款

对成功且仍有可请款金额的授权生命周期发起资金捕获。

#### 7.7.1 接口定义

| 项目 | 值 | 必填 | 说明 |
| --- | --- | --- | --- |
| Method | `POST` | M | 固定为 POST |
| Path | `/api/rest/payment/v1/capture` | M | API v1 接口地址 |
| `Content-Type` | `application/json` | M | 商户显式配置 |
| `Authorization` | `Bearer {JWT}` | M | 每次 HTTP 尝试使用新的 JWT `jti` |

**重复请求与重试规则**

同一业务动作的网络重试必须保持明文业务内容和 `orderInfo.orderId` 不变，但每次重试必须重新生成请求密文和 JWT `jti`。

#### 7.7.2 请求参数

##### 主参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息对象 |
| `orderInfo` | Object | M | 本次动作订单信息 |
| `transactionInfo` | Object | M | 源交易关联信息 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 必须与 JWT `merchantId` 一致 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 本次动作金额，必须大于 0 |
| `currency` | String（3） | M | 必须与源交易币种一致 |
| `orderNo` | String（64） | M | 必须与源交易商户订单号一致 |
| `orderId` | String（64） | M | 本次业务动作幂等标识 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sourceTransactionId` | String（64） | M | 源交易返回的 `transactionId` |
| `description` | String（128） | O | 本次动作描述，受理后原样返回 |

平台根据 `sourceTransactionId` 自动检索源交易及其交易时间；请求不接收 `sourceTransactionDateTime`。



#### 7.7.3 请求密文示例

```json
{
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.7.4 请求明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 80.0,
    "currency": "USD",
    "orderNo": "M202608010002",
    "orderId": "CAPTURE202608010001"
  },
  "transactionInfo": {
    "sourceTransactionId": "202608011030001230002",
    "description": "Capture order M202608010002"
  }
}
```

#### 7.7.5 响应参数

**加密响应外层参数**

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `code` | String（16） | M | OpenAPI 外层处理结果；成功返回业务密文时为 `T200` |
| `message` | String（128） | M | OpenAPI 外层处理结果描述 |
| `data` | String | M | 五段式加密业务响应；商户使用响应私钥解密 |

**解密后 `data` 参数**

##### 主参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息 |
| `orderInfo` | Object | M | 本次交易及交易生命周期累计金额 |
| `goodsInfo` | List<Object> | C | 首次请求提供时完整原样返回 |
| `billingCardHolderInfo` | Object | M | 首次交易保存的持卡人账单快照 |
| `payerInfo` | Object | M | 首次交易必传的付款人快照 |
| `shippingInfo` | Object | C | 首次交易提供收货人快照时必须完整返回 |
| `threeDSInfo` | Object | C | 交易存在 3DS 安全结果时返回 |
| `transactionInfo` | Object | M | 本次业务动作结果 |
| `billingInfo` | Object | M | 平台计算的金额、换汇、结算及费用信息 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 商户号 |
| `subMerchantInfo` | Object | C | 原交易使用子商户时必须返回 |


##### subMerchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `subId` | String（32） | C | 子商户快照存在时返回 |
| `subName` | String（128） | C | 原请求存在时原样返回 |
| `subCompanyName` | String（128） | C | 原请求存在时原样返回 |
| `subCountryCode` | String（3） | C | 原请求存在时原样返回 |
| `subState` | String（3） | C | 原请求存在时原样返回 |
| `subCity` | String（64） | C | 原请求存在时原样返回 |
| `subStreet` | String（128） | C | 原请求存在时原样返回 |
| `subEmail` | String（64） | C | 原请求存在时原样返回 |
| `subPhone` | String（32） | C | 原请求存在时原样返回 |
| `subPostal` | String（32） | C | 原请求存在时原样返回 |
| `subTaxId` | String（32） | C | 原请求存在时原样返回 |
| `merchantCategory` | String（4） | C | 原请求存在时原样返回 |
| `intesCode` | String（4） | C | 原请求存在时原样返回 |
| `chargeType` | String（3） | C | 原请求存在时原样返回 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 本次请款金额 |
| `currency` | String（3） | M | 交易币种 |
| `orderNo` | String（64） | M | 商户业务订单号 |
| `orderId` | String（64） | M | 本次业务动作幂等标识 |
| `totalAuthorizedAmount` | BigDecimal（12,2） | M | 累计授权成功金额，未发生时返回 `0` |
| `totalCapturedAmount` | BigDecimal（12,2） | M | 累计请款或预授权完成成功金额，未发生时返回 `0` |
| `totalRefundAmount` | BigDecimal（12,2） | M | 累计退款成功金额，未发生时返回 `0` |
| `totalAuthorizedCancelAmount` | BigDecimal（12,2） | M | 累计撤销或授权释放成功金额，未发生时返回 `0` |
| `totalRefuseAmount` | BigDecimal（12,2） | M | 累计拒付金额，未发生时返回 `0` |


##### goodsInfo 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `name` | String（128） | C | 商品或服务名称 |
| `quantity` | Integer | C | 商品数量，必须大于 0 |
| `amount` | BigDecimal（12,2） | C | 商品行总金额，不是商品单价 |
| `currency` | String（3） | C | 必须与 `orderInfo.currency` 一致 |


##### billingCardHolderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | M | 持卡人名 |
| `lastName` | String（32） | M | 持卡人姓 |
| `phone` | String（32） | M | 持卡人电话 |
| `email` | String（64） | M | 持卡人邮箱 |
| `country` | String（3） | M | ISO 3166-1 alpha-3 |
| `state` | String（3） | M | 州、省或地区 |
| `city` | String（64） | M | 城市 |
| `street` | String（128） | M | 街道地址 |
| `postal` | String（32） | M | 邮编 |


##### payerInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `payerId` | String（64） | C | 原请求存在时原样返回 |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（64） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |
| `ipAddress` | String（64） | M | 首次交易必传的付款人公网 IP 原样返回 |
| `sessionId` | String（128） | C | 原请求存在时原样返回 |
| `browserInfo` | Object | C | 原请求存在时原样返回 |
| `userAgent` | String（512） | C | 原请求存在时原样返回 |


##### shippingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（3） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |


##### threeDSInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `eci` | String（2） | C | 安全 ECI；不返回 CAVV |
| `dsTransactionId` | String（36） | C | 3DS 2.x Directory Server Transaction ID |
| `threeDsVersion` | String（8） | C | 3DS 协议版本 |
| `status` | String（1） | C | 标准化认证状态 |
| `liabilityShifted` | Boolean | C | 责任转移标识 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `transactionId` | String（64） | M | 平台交易 ID |
| `sourceTransactionId` | String（64） | M | 本次后续动作关联的源交易 ID |
| `sourceTransactionDateTime` | String（64） | M | 平台根据 `sourceTransactionId` 自动检索的源交易时间 |
| `description` | String（128） | C | 原请求提供时原样返回 |
| `callbackUrl` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `merchantWebsite` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `redirectUrl` | String（512） | C | Hosted Checkout 创建请求提供时按交易生命周期原样返回 |
| `language` | String（8） | C | Hosted Checkout 请求提供时原样返回 |
| `code` | String（16） | M | 当前动作业务结果码 |
| `message` | String（128） | M | 当前动作业务结果描述 |
| `transactionType` | String（32） | M | 当前交易动作类型 |
| `transactionStatus` | String（16） | M | `PROCESSING/PENDING/SUCCESS/FAILED` |
| `transactionDateTime` | String（64） | M | ISO-8601 offset datetime |
| `paymentMethod` | String（16） | C | 平台已确定实际支付方式时返回 |
| `cardBrand` | String（16） | C | 实际支付方式为银行卡且可识别卡品牌时返回 |
| `cardBin` | String（14） | C | 实际支付方式为银行卡且已生成脱敏卡摘要时返回 |
| `authCode` | String（6） | C | 渠道返回授权码时返回 |
| `arn` | String（32） | C | 渠道返回 ARN 时返回 |


##### billingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `labelAmount` | BigDecimal（12,2） | M | 商户上送或页面展示金额 |
| `labelCurrency` | String（3） | M | 商户上送或页面展示币种 |
| `transactionAmount` | BigDecimal（12,2） | M | 发送至渠道的交易金额 |
| `transactionCurrency` | String（3） | M | 发送至渠道的交易币种 |
| `transactionRate` | BigDecimal（18,8） | M | 标签币种转换为交易币种的汇率；未换汇为 `1` |
| `rateSource` | String（32） | C | 发生换汇并有来源编码时返回 |
| `rateTime` | String（64） | C | 发生换汇并有报价时间时返回 |
| `settlementRate` | BigDecimal（18,8） | C | 已计算结算换汇时返回 |
| `settlementAmount` | BigDecimal（12,2） | C | 已形成预计或最终结算金额时返回 |
| `settlementCurrency` | String（3） | C | 返回结算金额时必须同时返回 |
| `settlementFeeAmount` | BigDecimal（12,2） | C | 存在结算费用时返回 |
| `feeItems` | Array<Object> | C | 存在费用明细时完整返回 |


##### feeItems 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `categories` | String（32） | C | 费用类别 |
| `amount` | BigDecimal（12,2） | C | 费用金额 |
| `currency` | String（3） | C | 费用币种 |
| `rate` | BigDecimal（18,8） | C | 费用转结算币种汇率 |

后续动作响应必须返回首次交易保存的商品和人员快照、当前动作完整交易信息、累计金额和平台计算的 `billingInfo`，不能只返回交易 ID 和状态。`cardInfo`、`riskInfo` 和 3DS 敏感值永不返回。

#### 7.7.6 响应密文示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.7.7 响应明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 80.0,
    "currency": "USD",
    "orderNo": "M202608010002",
    "orderId": "CAPTURE202608010001",
    "totalAuthorizedAmount": 120.0,
    "totalCapturedAmount": 80.0,
    "totalRefundAmount": 0,
    "totalAuthorizedCancelAmount": 0,
    "totalRefuseAmount": 0
  },
  "goodsInfo": [
    {
      "name": "Travel Booking",
      "quantity": 1,
      "amount": 120.0,
      "currency": "USD"
    }
  ],
  "billingCardHolderInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "payerInfo": {
    "payerId": "CUSTOMER-10002",
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001",
    "ipAddress": "203.0.113.10",
    "sessionId": "SESSION-10002",
    "browserInfo": {
      "browser": {
        "name": "Chrome",
        "version": "128.0.0.0"
      }
    },
    "userAgent": "Mozilla/5.0"
  },
  "shippingInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "threeDSInfo": {
    "eci": "05",
    "dsTransactionId": "550e8400-e29b-41d4-a716-446655440000",
    "threeDsVersion": "2.2.0",
    "status": "Y",
    "liabilityShifted": true
  },
  "transactionInfo": {
    "transactionId": "202608011120001230006",
    "code": "T200",
    "message": "Success",
    "transactionType": "CAPTURE",
    "transactionStatus": "SUCCESS",
    "transactionDateTime": "2026-08-01T11:20:00.123+08:00",
    "paymentMethod": "BANK_CARD",
    "cardBrand": "VISA",
    "cardBin": "411111****1111",
    "description": "Capture order M202608010002",
    "callbackUrl": "https://merchant.example/openapi/payment/callback",
    "merchantWebsite": "https://shop.merchant.example/checkout",
    "sourceTransactionId": "202608011030001230002",
    "sourceTransactionDateTime": "2026-08-01T10:30:00.123+08:00",
    "arn": "12345678901234567890123"
  },
  "billingInfo": {
    "labelAmount": 80.0,
    "labelCurrency": "USD",
    "transactionAmount": 80.0,
    "transactionCurrency": "USD",
    "transactionRate": 1.0,
    "settlementRate": 1.0,
    "settlementAmount": 79.7,
    "settlementCurrency": "USD",
    "settlementFeeAmount": 0.3,
    "feeItems": [
      {
        "categories": "PROCESSING_FEE",
        "amount": 0.3,
        "currency": "USD",
        "rate": 1.0
      }
    ]
  }
}
```

#### 7.7.8 业务规则

- 源交易必须成功且处于允许请款的状态。
- 本次请款金额不能超过剩余可请款金额。
- 多次部分请款的每个新动作必须使用新的 `orderId`；网络重试仍复用原 `orderId`。


### 7.8 退款

对存在可退金额的成功支付或请款发起全部或部分退款。

#### 7.8.1 接口定义

| 项目 | 值 | 必填 | 说明 |
| --- | --- | --- | --- |
| Method | `POST` | M | 固定为 POST |
| Path | `/api/rest/payment/v1/refund` | M | API v1 接口地址 |
| `Content-Type` | `application/json` | M | 商户显式配置 |
| `Authorization` | `Bearer {JWT}` | M | 每次 HTTP 尝试使用新的 JWT `jti` |

**重复请求与重试规则**

同一业务动作的网络重试必须保持明文业务内容和 `orderInfo.orderId` 不变，但每次重试必须重新生成请求密文和 JWT `jti`。

#### 7.8.2 请求参数

##### 主参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息对象 |
| `orderInfo` | Object | M | 本次动作订单信息 |
| `transactionInfo` | Object | M | 源交易关联信息 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 必须与 JWT `merchantId` 一致 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 本次动作金额，必须大于 0 |
| `currency` | String（3） | M | 必须与源交易币种一致 |
| `orderNo` | String（64） | M | 必须与源交易商户订单号一致 |
| `orderId` | String（64） | M | 本次业务动作幂等标识 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sourceTransactionId` | String（64） | M | 源交易返回的 `transactionId` |
| `description` | String（128） | O | 本次动作描述，受理后原样返回 |

平台根据 `sourceTransactionId` 自动检索源交易及其交易时间；请求不接收 `sourceTransactionDateTime`。



#### 7.8.3 请求密文示例

```json
{
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.8.4 请求明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 2.5,
    "currency": "USD",
    "orderNo": "M202608010001",
    "orderId": "REFUND202608010001"
  },
  "transactionInfo": {
    "sourceTransactionId": "202608011020301230001",
    "description": "Refund order M202608010001"
  }
}
```

#### 7.8.5 响应参数

**加密响应外层参数**

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `code` | String（16） | M | OpenAPI 外层处理结果；成功返回业务密文时为 `T200` |
| `message` | String（128） | M | OpenAPI 外层处理结果描述 |
| `data` | String | M | 五段式加密业务响应；商户使用响应私钥解密 |

**解密后 `data` 参数**

##### 主参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息 |
| `orderInfo` | Object | M | 本次交易及交易生命周期累计金额 |
| `goodsInfo` | List<Object> | C | 首次请求提供时完整原样返回 |
| `billingCardHolderInfo` | Object | M | 首次交易保存的持卡人账单快照 |
| `payerInfo` | Object | M | 首次交易必传的付款人快照 |
| `shippingInfo` | Object | C | 首次交易提供收货人快照时必须完整返回 |
| `threeDSInfo` | Object | C | 交易存在 3DS 安全结果时返回 |
| `transactionInfo` | Object | M | 本次业务动作结果 |
| `billingInfo` | Object | M | 平台计算的金额、换汇、结算及费用信息 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 商户号 |
| `subMerchantInfo` | Object | C | 原交易使用子商户时必须返回 |


##### subMerchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `subId` | String（32） | C | 子商户快照存在时返回 |
| `subName` | String（128） | C | 原请求存在时原样返回 |
| `subCompanyName` | String（128） | C | 原请求存在时原样返回 |
| `subCountryCode` | String（3） | C | 原请求存在时原样返回 |
| `subState` | String（3） | C | 原请求存在时原样返回 |
| `subCity` | String（64） | C | 原请求存在时原样返回 |
| `subStreet` | String（128） | C | 原请求存在时原样返回 |
| `subEmail` | String（64） | C | 原请求存在时原样返回 |
| `subPhone` | String（32） | C | 原请求存在时原样返回 |
| `subPostal` | String（32） | C | 原请求存在时原样返回 |
| `subTaxId` | String（32） | C | 原请求存在时原样返回 |
| `merchantCategory` | String（4） | C | 原请求存在时原样返回 |
| `intesCode` | String（4） | C | 原请求存在时原样返回 |
| `chargeType` | String（3） | C | 原请求存在时原样返回 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 本次退款金额 |
| `currency` | String（3） | M | 交易币种 |
| `orderNo` | String（64） | M | 商户业务订单号 |
| `orderId` | String（64） | M | 本次业务动作幂等标识 |
| `totalAuthorizedAmount` | BigDecimal（12,2） | M | 累计授权成功金额，未发生时返回 `0` |
| `totalCapturedAmount` | BigDecimal（12,2） | M | 累计请款或预授权完成成功金额，未发生时返回 `0` |
| `totalRefundAmount` | BigDecimal（12,2） | M | 累计退款成功金额，未发生时返回 `0` |
| `totalAuthorizedCancelAmount` | BigDecimal（12,2） | M | 累计撤销或授权释放成功金额，未发生时返回 `0` |
| `totalRefuseAmount` | BigDecimal（12,2） | M | 累计拒付金额，未发生时返回 `0` |


##### goodsInfo 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `name` | String（128） | C | 商品或服务名称 |
| `quantity` | Integer | C | 商品数量，必须大于 0 |
| `amount` | BigDecimal（12,2） | C | 商品行总金额，不是商品单价 |
| `currency` | String（3） | C | 必须与 `orderInfo.currency` 一致 |


##### billingCardHolderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | M | 持卡人名 |
| `lastName` | String（32） | M | 持卡人姓 |
| `phone` | String（32） | M | 持卡人电话 |
| `email` | String（64） | M | 持卡人邮箱 |
| `country` | String（3） | M | ISO 3166-1 alpha-3 |
| `state` | String（3） | M | 州、省或地区 |
| `city` | String（64） | M | 城市 |
| `street` | String（128） | M | 街道地址 |
| `postal` | String（32） | M | 邮编 |


##### payerInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `payerId` | String（64） | C | 原请求存在时原样返回 |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（64） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |
| `ipAddress` | String（64） | M | 首次交易必传的付款人公网 IP 原样返回 |
| `sessionId` | String（128） | C | 原请求存在时原样返回 |
| `browserInfo` | Object | C | 原请求存在时原样返回 |
| `userAgent` | String（512） | C | 原请求存在时原样返回 |


##### shippingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（3） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |


##### threeDSInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `eci` | String（2） | C | 安全 ECI；不返回 CAVV |
| `dsTransactionId` | String（36） | C | 3DS 2.x Directory Server Transaction ID |
| `threeDsVersion` | String（8） | C | 3DS 协议版本 |
| `status` | String（1） | C | 标准化认证状态 |
| `liabilityShifted` | Boolean | C | 责任转移标识 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `transactionId` | String（64） | M | 平台交易 ID |
| `sourceTransactionId` | String（64） | M | 本次后续动作关联的源交易 ID |
| `sourceTransactionDateTime` | String（64） | M | 平台根据 `sourceTransactionId` 自动检索的源交易时间 |
| `description` | String（128） | C | 原请求提供时原样返回 |
| `callbackUrl` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `merchantWebsite` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `redirectUrl` | String（512） | C | Hosted Checkout 创建请求提供时按交易生命周期原样返回 |
| `language` | String（8） | C | Hosted Checkout 请求提供时原样返回 |
| `code` | String（16） | M | 当前动作业务结果码 |
| `message` | String（128） | M | 当前动作业务结果描述 |
| `transactionType` | String（32） | M | 当前交易动作类型 |
| `transactionStatus` | String（16） | M | `PROCESSING/PENDING/SUCCESS/FAILED` |
| `transactionDateTime` | String（64） | M | ISO-8601 offset datetime |
| `paymentMethod` | String（16） | C | 平台已确定实际支付方式时返回 |
| `cardBrand` | String（16） | C | 实际支付方式为银行卡且可识别卡品牌时返回 |
| `cardBin` | String（14） | C | 实际支付方式为银行卡且已生成脱敏卡摘要时返回 |
| `authCode` | String（6） | C | 渠道返回授权码时返回 |
| `arn` | String（32） | C | 渠道返回 ARN 时返回 |


##### billingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `labelAmount` | BigDecimal（12,2） | M | 商户上送或页面展示金额 |
| `labelCurrency` | String（3） | M | 商户上送或页面展示币种 |
| `transactionAmount` | BigDecimal（12,2） | M | 发送至渠道的交易金额 |
| `transactionCurrency` | String（3） | M | 发送至渠道的交易币种 |
| `transactionRate` | BigDecimal（18,8） | M | 标签币种转换为交易币种的汇率；未换汇为 `1` |
| `rateSource` | String（32） | C | 发生换汇并有来源编码时返回 |
| `rateTime` | String（64） | C | 发生换汇并有报价时间时返回 |
| `settlementRate` | BigDecimal（18,8） | C | 已计算结算换汇时返回 |
| `settlementAmount` | BigDecimal（12,2） | C | 已形成预计或最终结算金额时返回 |
| `settlementCurrency` | String（3） | C | 返回结算金额时必须同时返回 |
| `settlementFeeAmount` | BigDecimal（12,2） | C | 存在结算费用时返回 |
| `feeItems` | Array<Object> | C | 存在费用明细时完整返回 |


##### feeItems 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `categories` | String（32） | C | 费用类别 |
| `amount` | BigDecimal（12,2） | C | 费用金额 |
| `currency` | String（3） | C | 费用币种 |
| `rate` | BigDecimal（18,8） | C | 费用转结算币种汇率 |

后续动作响应必须返回首次交易保存的商品和人员快照、当前动作完整交易信息、累计金额和平台计算的 `billingInfo`，不能只返回交易 ID 和状态。`cardInfo`、`riskInfo` 和 3DS 敏感值永不返回。

#### 7.8.6 响应密文示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.8.7 响应明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 2.5,
    "currency": "USD",
    "orderNo": "M202608010001",
    "orderId": "REFUND202608010001",
    "totalAuthorizedAmount": 10.25,
    "totalCapturedAmount": 10.25,
    "totalRefundAmount": 2.5,
    "totalAuthorizedCancelAmount": 0,
    "totalRefuseAmount": 0
  },
  "goodsInfo": [
    {
      "name": "Wireless Headphones",
      "quantity": 1,
      "amount": 10.25,
      "currency": "USD"
    }
  ],
  "billingCardHolderInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "payerInfo": {
    "payerId": "CUSTOMER-10002",
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001",
    "ipAddress": "203.0.113.10",
    "sessionId": "SESSION-10002",
    "browserInfo": {
      "browser": {
        "name": "Chrome",
        "version": "128.0.0.0"
      }
    },
    "userAgent": "Mozilla/5.0"
  },
  "shippingInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "threeDSInfo": {
    "eci": "05",
    "dsTransactionId": "550e8400-e29b-41d4-a716-446655440000",
    "threeDsVersion": "2.2.0",
    "status": "Y",
    "liabilityShifted": true
  },
  "transactionInfo": {
    "transactionId": "202608011130001230007",
    "code": "T200",
    "message": "Success",
    "transactionType": "REFUND",
    "transactionStatus": "SUCCESS",
    "transactionDateTime": "2026-08-01T11:30:00.123+08:00",
    "paymentMethod": "BANK_CARD",
    "cardBrand": "VISA",
    "cardBin": "411111****1111",
    "description": "Refund order M202608010001",
    "callbackUrl": "https://merchant.example/openapi/payment/callback",
    "merchantWebsite": "https://shop.merchant.example/checkout",
    "sourceTransactionId": "202608011020301230001",
    "sourceTransactionDateTime": "2026-08-01T10:20:30.123+08:00"
  },
  "billingInfo": {
    "labelAmount": 2.5,
    "labelCurrency": "USD",
    "transactionAmount": 2.5,
    "transactionCurrency": "USD",
    "transactionRate": 1.0
  }
}
```

#### 7.8.8 业务规则

- 退款支持全部退款和部分退款，`orderInfo.amount` 为本次退款金额。
- 本次退款金额不能超过源交易剩余可退金额，币种必须与源交易一致。
- 多次部分退款的每个新动作使用新的 `orderId`；结果不确定时先查询，不能换号重退。


### 7.9 撤销

撤销尚未完成不可逆资金处理且仍有可撤销金额的授权、预授权或支付动作。撤销只支持全额撤销当前全部可撤销金额。

#### 7.9.1 接口定义

| 项目 | 值 | 必填 | 说明 |
| --- | --- | --- | --- |
| Method | `POST` | M | 固定为 POST |
| Path | `/api/rest/payment/v1/void` | M | API v1 接口地址 |
| `Content-Type` | `application/json` | M | 商户显式配置 |
| `Authorization` | `Bearer {JWT}` | M | 每次 HTTP 尝试使用新的 JWT `jti` |

**重复请求与重试规则**

同一业务动作的网络重试必须保持明文业务内容和 `orderInfo.orderId` 不变，但每次重试必须重新生成请求密文和 JWT `jti`。

#### 7.9.2 请求参数

##### 主参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息对象 |
| `orderInfo` | Object | M | 本次动作订单信息 |
| `transactionInfo` | Object | M | 源交易关联信息 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 必须与 JWT `merchantId` 一致 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `orderNo` | String（64） | M | 必须与源交易商户订单号一致 |
| `orderId` | String（64） | M | 本次业务动作幂等标识 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sourceTransactionId` | String（64） | M | 源交易返回的 `transactionId` |
| `description` | String（128） | O | 本次动作描述，受理后原样返回 |

平台根据 `sourceTransactionId` 自动检索源交易及其交易时间；请求不接收 `sourceTransactionDateTime`。

撤销请求不接收 `orderInfo.amount` 和 `orderInfo.currency`。平台根据源交易状态计算并全额撤销当前全部可撤销金额。

#### 7.9.3 请求密文示例

```json
{
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.9.4 请求明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "orderNo": "M202608010004",
    "orderId": "VOID202608010001"
  },
  "transactionInfo": {
    "sourceTransactionId": "202608011130001230007",
    "description": "Void order M202608010004"
  }
}
```

#### 7.9.5 响应参数

**加密响应外层参数**

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `code` | String（16） | M | OpenAPI 外层处理结果；成功返回业务密文时为 `T200` |
| `message` | String（128） | M | OpenAPI 外层处理结果描述 |
| `data` | String | M | 五段式加密业务响应；商户使用响应私钥解密 |

**解密后 `data` 参数**

##### 主参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息 |
| `orderInfo` | Object | M | 本次交易及交易生命周期累计金额 |
| `goodsInfo` | List<Object> | C | 首次请求提供时完整原样返回 |
| `billingCardHolderInfo` | Object | M | 首次交易保存的持卡人账单快照 |
| `payerInfo` | Object | M | 首次交易必传的付款人快照 |
| `shippingInfo` | Object | C | 首次交易提供收货人快照时必须完整返回 |
| `threeDSInfo` | Object | C | 交易存在 3DS 安全结果时返回 |
| `transactionInfo` | Object | M | 本次业务动作结果 |
| `billingInfo` | Object | M | 平台计算的金额、换汇、结算及费用信息 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 商户号 |
| `subMerchantInfo` | Object | C | 原交易使用子商户时必须返回 |


##### subMerchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `subId` | String（32） | C | 子商户快照存在时返回 |
| `subName` | String（128） | C | 原请求存在时原样返回 |
| `subCompanyName` | String（128） | C | 原请求存在时原样返回 |
| `subCountryCode` | String（3） | C | 原请求存在时原样返回 |
| `subState` | String（3） | C | 原请求存在时原样返回 |
| `subCity` | String（64） | C | 原请求存在时原样返回 |
| `subStreet` | String（128） | C | 原请求存在时原样返回 |
| `subEmail` | String（64） | C | 原请求存在时原样返回 |
| `subPhone` | String（32） | C | 原请求存在时原样返回 |
| `subPostal` | String（32） | C | 原请求存在时原样返回 |
| `subTaxId` | String（32） | C | 原请求存在时原样返回 |
| `merchantCategory` | String（4） | C | 原请求存在时原样返回 |
| `intesCode` | String（4） | C | 原请求存在时原样返回 |
| `chargeType` | String（3） | C | 原请求存在时原样返回 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 平台计算的本次全部可撤销金额 |
| `currency` | String（3） | M | 交易币种 |
| `orderNo` | String（64） | M | 商户业务订单号 |
| `orderId` | String（64） | M | 本次业务动作幂等标识 |
| `totalAuthorizedAmount` | BigDecimal（12,2） | M | 累计授权成功金额，未发生时返回 `0` |
| `totalCapturedAmount` | BigDecimal（12,2） | M | 累计请款或预授权完成成功金额，未发生时返回 `0` |
| `totalRefundAmount` | BigDecimal（12,2） | M | 累计退款成功金额，未发生时返回 `0` |
| `totalAuthorizedCancelAmount` | BigDecimal（12,2） | M | 累计撤销或授权释放成功金额，未发生时返回 `0` |
| `totalRefuseAmount` | BigDecimal（12,2） | M | 累计拒付金额，未发生时返回 `0` |


##### goodsInfo 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `name` | String（128） | C | 商品或服务名称 |
| `quantity` | Integer | C | 商品数量，必须大于 0 |
| `amount` | BigDecimal（12,2） | C | 商品行总金额，不是商品单价 |
| `currency` | String（3） | C | 必须与 `orderInfo.currency` 一致 |


##### billingCardHolderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | M | 持卡人名 |
| `lastName` | String（32） | M | 持卡人姓 |
| `phone` | String（32） | M | 持卡人电话 |
| `email` | String（64） | M | 持卡人邮箱 |
| `country` | String（3） | M | ISO 3166-1 alpha-3 |
| `state` | String（3） | M | 州、省或地区 |
| `city` | String（64） | M | 城市 |
| `street` | String（128） | M | 街道地址 |
| `postal` | String（32） | M | 邮编 |


##### payerInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `payerId` | String（64） | C | 原请求存在时原样返回 |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（64） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |
| `ipAddress` | String（64） | M | 首次交易必传的付款人公网 IP 原样返回 |
| `sessionId` | String（128） | C | 原请求存在时原样返回 |
| `browserInfo` | Object | C | 原请求存在时原样返回 |
| `userAgent` | String（512） | C | 原请求存在时原样返回 |


##### shippingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（3） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |


##### threeDSInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `eci` | String（2） | C | 安全 ECI；不返回 CAVV |
| `dsTransactionId` | String（36） | C | 3DS 2.x Directory Server Transaction ID |
| `threeDsVersion` | String（8） | C | 3DS 协议版本 |
| `status` | String（1） | C | 标准化认证状态 |
| `liabilityShifted` | Boolean | C | 责任转移标识 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `transactionId` | String（64） | M | 平台交易 ID |
| `sourceTransactionId` | String（64） | M | 本次后续动作关联的源交易 ID |
| `sourceTransactionDateTime` | String（64） | M | 平台根据 `sourceTransactionId` 自动检索的源交易时间 |
| `description` | String（128） | C | 原请求提供时原样返回 |
| `callbackUrl` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `merchantWebsite` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `redirectUrl` | String（512） | C | Hosted Checkout 创建请求提供时按交易生命周期原样返回 |
| `language` | String（8） | C | Hosted Checkout 请求提供时原样返回 |
| `code` | String（16） | M | 当前动作业务结果码 |
| `message` | String（128） | M | 当前动作业务结果描述 |
| `transactionType` | String（32） | M | 当前交易动作类型 |
| `transactionStatus` | String（16） | M | `PROCESSING/PENDING/SUCCESS/FAILED` |
| `transactionDateTime` | String（64） | M | ISO-8601 offset datetime |
| `paymentMethod` | String（16） | C | 平台已确定实际支付方式时返回 |
| `cardBrand` | String（16） | C | 实际支付方式为银行卡且可识别卡品牌时返回 |
| `cardBin` | String（14） | C | 实际支付方式为银行卡且已生成脱敏卡摘要时返回 |
| `authCode` | String（6） | C | 渠道返回授权码时返回 |
| `arn` | String（32） | C | 渠道返回 ARN 时返回 |


##### billingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `labelAmount` | BigDecimal（12,2） | M | 商户上送或页面展示金额 |
| `labelCurrency` | String（3） | M | 商户上送或页面展示币种 |
| `transactionAmount` | BigDecimal（12,2） | M | 发送至渠道的交易金额 |
| `transactionCurrency` | String（3） | M | 发送至渠道的交易币种 |
| `transactionRate` | BigDecimal（18,8） | M | 标签币种转换为交易币种的汇率；未换汇为 `1` |
| `rateSource` | String（32） | C | 发生换汇并有来源编码时返回 |
| `rateTime` | String（64） | C | 发生换汇并有报价时间时返回 |
| `settlementRate` | BigDecimal（18,8） | C | 已计算结算换汇时返回 |
| `settlementAmount` | BigDecimal（12,2） | C | 已形成预计或最终结算金额时返回 |
| `settlementCurrency` | String（3） | C | 返回结算金额时必须同时返回 |
| `settlementFeeAmount` | BigDecimal（12,2） | C | 存在结算费用时返回 |
| `feeItems` | Array<Object> | C | 存在费用明细时完整返回 |


##### feeItems 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `categories` | String（32） | C | 费用类别 |
| `amount` | BigDecimal（12,2） | C | 费用金额 |
| `currency` | String（3） | C | 费用币种 |
| `rate` | BigDecimal（18,8） | C | 费用转结算币种汇率 |

后续动作响应必须返回首次交易保存的商品和人员快照、当前动作完整交易信息、累计金额和平台计算的 `billingInfo`，不能只返回交易 ID 和状态。`cardInfo`、`riskInfo` 和 3DS 敏感值永不返回。

#### 7.9.6 响应密文示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.9.7 响应明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 120.0,
    "currency": "USD",
    "orderNo": "M202608010004",
    "orderId": "VOID202608010001",
    "totalAuthorizedAmount": 120.0,
    "totalCapturedAmount": 0,
    "totalRefundAmount": 0,
    "totalAuthorizedCancelAmount": 120.0,
    "totalRefuseAmount": 0
  },
  "goodsInfo": [
    {
      "name": "Travel Booking",
      "quantity": 1,
      "amount": 120.0,
      "currency": "USD"
    }
  ],
  "billingCardHolderInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "payerInfo": {
    "payerId": "CUSTOMER-10002",
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001",
    "ipAddress": "203.0.113.10",
    "sessionId": "SESSION-10002",
    "browserInfo": {
      "browser": {
        "name": "Chrome",
        "version": "128.0.0.0"
      }
    },
    "userAgent": "Mozilla/5.0"
  },
  "shippingInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "threeDSInfo": {
    "eci": "05",
    "dsTransactionId": "550e8400-e29b-41d4-a716-446655440000",
    "threeDsVersion": "2.2.0",
    "status": "Y",
    "liabilityShifted": true
  },
  "transactionInfo": {
    "transactionId": "202608011140001230008",
    "code": "T200",
    "message": "Success",
    "transactionType": "VOID",
    "transactionStatus": "SUCCESS",
    "transactionDateTime": "2026-08-01T11:40:00.123+08:00",
    "paymentMethod": "BANK_CARD",
    "cardBrand": "VISA",
    "cardBin": "411111****1111",
    "description": "Void order M202608010004",
    "callbackUrl": "https://merchant.example/openapi/payment/callback",
    "merchantWebsite": "https://shop.merchant.example/checkout",
    "sourceTransactionId": "202608011130001230007",
    "sourceTransactionDateTime": "2026-08-01T11:30:00.123+08:00"
  },
  "billingInfo": {
    "labelAmount": 120.0,
    "labelCurrency": "USD",
    "transactionAmount": 120.0,
    "transactionCurrency": "USD",
    "transactionRate": 1.0
  }
}
```

#### 7.9.8 业务规则

- 请求禁止传入金额和币种；平台全额撤销当前全部可撤销金额，不支持商户指定部分撤销金额。
- 源交易必须属于当前商户、处于允许撤销的状态，且尚未发生成功请款、预授权完成或退款；`totalCapturedAmount` 和 `totalRefundAmount` 必须均为 `0`。
- 响应 `orderInfo.amount` 和 `currency` 由平台返回，表示本次实际全额撤销金额及币种。
- 全额撤销成功后，`totalAuthorizedAmount` 保留累计授权成功金额，`totalAuthorizedCancelAmount` 等于本次全部已撤销授权金额；示例中二者均为 `120.0`。


### 7.10 交易查询

按商户订单号查询同一订单下的全部交易动作；可选提供平台交易 ID 精确筛选。查询返回商品及人员快照、累计金额、完整交易动作字段、3DS 安全子集和平台账单信息。

#### 7.10.1 接口定义

| 项目 | 值 | 必填 | 说明 |
| --- | --- | --- | --- |
| Method | `POST` | M | 固定为 POST |
| Path | `/api/rest/payment/v1/query` | M | API v1 接口地址 |
| `Content-Type` | `application/json` | M | 商户显式配置 |
| `Authorization` | `Bearer {JWT}` | M | 每次 HTTP 尝试使用新的 JWT `jti` |

**重复请求与重试规则**

同一业务动作的网络重试必须保持明文业务内容和 `orderInfo.orderId` 不变，但每次重试必须重新生成请求密文和 JWT `jti`。

#### 7.10.2 请求参数

##### 主参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息对象 |
| `orderInfo` | Object | M | 查询请求信息 |
| `transactionInfo` | Object | O | 精确交易筛选条件；省略或传空对象时查询订单全部动作 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 必须与 JWT `merchantId` 一致 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `orderNo` | String（64） | M | 待查询的商户业务订单号 |
| `orderId` | String（64） | M | 本次查询请求标识；每次新查询使用新值 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `transactionId` | String（64） | O | 平台交易 ID；提供时只返回匹配动作 |

#### 7.10.3 请求密文示例

```json
{
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.10.4 请求明文示例

**查询订单全部动作**

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "orderNo": "M202608010002",
    "orderId": "QUERY202608010001"
  },
  "transactionInfo": {
  }
}
```

**精确查询单个动作**

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "orderNo": "M202608010002",
    "orderId": "QUERY202608010002"
  },
  "transactionInfo": {
    "transactionId": "202608011120001230006"
  }
}
```

#### 7.10.5 响应参数

**加密响应外层参数**

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `code` | String（16） | M | OpenAPI 外层处理结果；成功返回业务密文时为 `T200` |
| `message` | String（128） | M | OpenAPI 外层处理结果描述 |
| `data` | String | M | 五段式加密业务响应；商户使用响应私钥解密 |

**解密后 `data` 参数**

##### 主参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息 |
| `orderInfo` | Object | M | 本次交易及交易生命周期累计金额 |
| `goodsInfo` | List<Object> | C | 首次请求提供时完整原样返回 |
| `billingCardHolderInfo` | Object | M | 首次交易保存的持卡人账单快照 |
| `payerInfo` | Object | M | 首次交易必传的付款人快照 |
| `shippingInfo` | Object | C | 首次交易提供收货人快照时必须完整返回 |
| `threeDSInfo` | Object | C | 交易存在 3DS 安全结果时返回 |
| `transactionInfo` | Array<Object> | M | 符合条件的完整交易动作列表 |
| `billingInfo` | Object | M | 平台计算的金额、换汇、结算及费用信息 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 商户号 |
| `subMerchantInfo` | Object | C | 原交易使用子商户时必须返回 |


##### subMerchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `subId` | String（32） | C | 子商户快照存在时返回 |
| `subName` | String（128） | C | 原请求存在时原样返回 |
| `subCompanyName` | String（128） | C | 原请求存在时原样返回 |
| `subCountryCode` | String（3） | C | 原请求存在时原样返回 |
| `subState` | String（3） | C | 原请求存在时原样返回 |
| `subCity` | String（64） | C | 原请求存在时原样返回 |
| `subStreet` | String（128） | C | 原请求存在时原样返回 |
| `subEmail` | String（64） | C | 原请求存在时原样返回 |
| `subPhone` | String（32） | C | 原请求存在时原样返回 |
| `subPostal` | String（32） | C | 原请求存在时原样返回 |
| `subTaxId` | String（32） | C | 原请求存在时原样返回 |
| `merchantCategory` | String（4） | C | 原请求存在时原样返回 |
| `intesCode` | String（4） | C | 原请求存在时原样返回 |
| `chargeType` | String（3） | C | 原请求存在时原样返回 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 原始交易金额 |
| `currency` | String（3） | M | 交易币种 |
| `orderNo` | String（64） | M | 商户业务订单号 |
| `orderId` | String（64） | M | 本次查询请求标识 |
| `totalAuthorizedAmount` | BigDecimal（12,2） | M | 累计授权成功金额，未发生时返回 `0` |
| `totalCapturedAmount` | BigDecimal（12,2） | M | 累计请款或预授权完成成功金额，未发生时返回 `0` |
| `totalRefundAmount` | BigDecimal（12,2） | M | 累计退款成功金额，未发生时返回 `0` |
| `totalAuthorizedCancelAmount` | BigDecimal（12,2） | M | 累计撤销或授权释放成功金额，未发生时返回 `0` |
| `totalRefuseAmount` | BigDecimal（12,2） | M | 累计拒付金额，未发生时返回 `0` |


##### goodsInfo 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `name` | String（128） | C | 商品或服务名称 |
| `quantity` | Integer | C | 商品数量，必须大于 0 |
| `amount` | BigDecimal（12,2） | C | 商品行总金额，不是商品单价 |
| `currency` | String（3） | C | 必须与 `orderInfo.currency` 一致 |


##### billingCardHolderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | M | 持卡人名 |
| `lastName` | String（32） | M | 持卡人姓 |
| `phone` | String（32） | M | 持卡人电话 |
| `email` | String（64） | M | 持卡人邮箱 |
| `country` | String（3） | M | ISO 3166-1 alpha-3 |
| `state` | String（3） | M | 州、省或地区 |
| `city` | String（64） | M | 城市 |
| `street` | String（128） | M | 街道地址 |
| `postal` | String（32） | M | 邮编 |


##### payerInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `payerId` | String（64） | C | 原请求存在时原样返回 |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（64） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |
| `ipAddress` | String（64） | M | 首次交易必传的付款人公网 IP 原样返回 |
| `sessionId` | String（128） | C | 原请求存在时原样返回 |
| `browserInfo` | Object | C | 原请求存在时原样返回 |
| `userAgent` | String（512） | C | 原请求存在时原样返回 |


##### shippingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（3） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |


##### threeDSInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `eci` | String（2） | C | 安全 ECI；不返回 CAVV |
| `dsTransactionId` | String（36） | C | 3DS 2.x Directory Server Transaction ID |
| `threeDsVersion` | String（8） | C | 3DS 协议版本 |
| `status` | String（1） | C | 标准化认证状态 |
| `liabilityShifted` | Boolean | C | 责任转移标识 |


##### transactionInfo 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `transactionId` | String（64） | M | 平台交易 ID |
| `sourceTransactionId` | String（64） | C | 后续动作关联的源交易 ID；首次交易不返回 |
| `sourceTransactionDateTime` | String（64） | C | 后续动作源交易时间；首次交易不返回 |
| `description` | String（128） | C | 原请求提供时原样返回 |
| `callbackUrl` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `merchantWebsite` | String（512） | C | 首次交易提供时按交易生命周期返回 |
| `redirectUrl` | String（512） | C | Hosted Checkout 创建请求提供时按交易生命周期原样返回 |
| `language` | String（8） | C | Hosted Checkout 请求提供时原样返回 |
| `code` | String（16） | M | 当前动作业务结果码 |
| `message` | String（128） | M | 当前动作业务结果描述 |
| `transactionType` | String（32） | M | 当前交易动作类型 |
| `transactionStatus` | String（16） | M | `PROCESSING/PENDING/SUCCESS/FAILED` |
| `transactionDateTime` | String（64） | M | ISO-8601 offset datetime |
| `paymentMethod` | String（16） | C | 平台已确定实际支付方式时返回 |
| `cardBrand` | String（16） | C | 实际支付方式为银行卡且可识别卡品牌时返回 |
| `cardBin` | String（14） | C | 实际支付方式为银行卡且已生成脱敏卡摘要时返回 |
| `authCode` | String（6） | C | 渠道返回授权码时返回 |
| `arn` | String（32） | C | 渠道返回 ARN 时返回 |


##### billingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `labelAmount` | BigDecimal（12,2） | M | 商户上送或页面展示金额 |
| `labelCurrency` | String（3） | M | 商户上送或页面展示币种 |
| `transactionAmount` | BigDecimal（12,2） | M | 发送至渠道的交易金额 |
| `transactionCurrency` | String（3） | M | 发送至渠道的交易币种 |
| `transactionRate` | BigDecimal（18,8） | M | 标签币种转换为交易币种的汇率；未换汇为 `1` |
| `rateSource` | String（32） | C | 发生换汇并有来源编码时返回 |
| `rateTime` | String（64） | C | 发生换汇并有报价时间时返回 |
| `settlementRate` | BigDecimal（18,8） | C | 已计算结算换汇时返回 |
| `settlementAmount` | BigDecimal（12,2） | C | 已形成预计或最终结算金额时返回 |
| `settlementCurrency` | String（3） | C | 返回结算金额时必须同时返回 |
| `settlementFeeAmount` | BigDecimal（12,2） | C | 存在结算费用时返回 |
| `feeItems` | Array<Object> | C | 存在费用明细时完整返回 |


##### feeItems 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `categories` | String（32） | C | 费用类别 |
| `amount` | BigDecimal（12,2） | C | 费用金额 |
| `currency` | String（3） | C | 费用币种 |
| `rate` | BigDecimal（18,8） | C | 费用转结算币种汇率 |

查询响应不返回 `cardInfo`、`riskInfo`、CAVV、完整 PAN、CVV、3DS 原始载荷或渠道原文。`payerInfo` 是首次交易必传快照，必须完整返回；`goodsInfo`、`billingCardHolderInfo` 和 `shippingInfo` 按各自条件返回保存的完整快照，不能只返回摘要字段。Hosted Checkout 完成付款后，`billingCardHolderInfo` 返回付款人最终提交的资料。

#### 7.10.6 响应密文示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 7.10.7 响应明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 120.0,
    "currency": "USD",
    "orderNo": "M202608010002",
    "orderId": "QUERY202608010001",
    "totalAuthorizedAmount": 120.0,
    "totalCapturedAmount": 80.0,
    "totalRefundAmount": 0,
    "totalAuthorizedCancelAmount": 0,
    "totalRefuseAmount": 0
  },
  "goodsInfo": [
    {
      "name": "Travel Booking",
      "quantity": 1,
      "amount": 120.0,
      "currency": "USD"
    }
  ],
  "billingCardHolderInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "payerInfo": {
    "payerId": "CUSTOMER-10002",
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001",
    "ipAddress": "203.0.113.10",
    "sessionId": "SESSION-10002",
    "browserInfo": {
      "browser": {
        "name": "Chrome",
        "version": "128.0.0.0"
      }
    },
    "userAgent": "Mozilla/5.0"
  },
  "shippingInfo": {
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+12025550124",
    "email": "john.smith@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "threeDSInfo": {
    "eci": "05",
    "dsTransactionId": "550e8400-e29b-41d4-a716-446655440000",
    "threeDsVersion": "2.2.0",
    "status": "Y",
    "liabilityShifted": true
  },
  "transactionInfo": [
    {
      "transactionId": "202608011030001230002",
      "code": "T200",
      "message": "Success",
      "transactionType": "AUTHORIZATION",
      "transactionStatus": "SUCCESS",
      "transactionDateTime": "2026-08-01T10:30:00.123+08:00",
      "paymentMethod": "BANK_CARD",
      "cardBrand": "VISA",
      "cardBin": "411111****1111",
      "description": "Authorize order M202608010002",
      "callbackUrl": "https://merchant.example/openapi/payment/callback",
      "merchantWebsite": "https://shop.merchant.example/checkout",
      "authCode": "654321"
    },
    {
      "transactionId": "202608011100001230004",
      "code": "T200",
      "message": "Success",
      "transactionType": "INCREMENTAL_AUTHORIZATION",
      "transactionStatus": "SUCCESS",
      "transactionDateTime": "2026-08-01T11:00:00.123+08:00",
      "paymentMethod": "BANK_CARD",
      "cardBrand": "VISA",
      "cardBin": "411111****1111",
      "description": "Increase authorization amount",
      "callbackUrl": "https://merchant.example/openapi/payment/callback",
      "merchantWebsite": "https://shop.merchant.example/checkout",
      "sourceTransactionId": "202608011030001230002",
      "sourceTransactionDateTime": "2026-08-01T10:30:00.123+08:00",
      "authCode": "765432"
    },
    {
      "transactionId": "202608011120001230006",
      "code": "T200",
      "message": "Success",
      "transactionType": "CAPTURE",
      "transactionStatus": "SUCCESS",
      "transactionDateTime": "2026-08-01T11:20:00.123+08:00",
      "paymentMethod": "BANK_CARD",
      "cardBrand": "VISA",
      "cardBin": "411111****1111",
      "description": "Capture authorized amount",
      "callbackUrl": "https://merchant.example/openapi/payment/callback",
      "merchantWebsite": "https://shop.merchant.example/checkout",
      "sourceTransactionId": "202608011030001230002",
      "sourceTransactionDateTime": "2026-08-01T10:30:00.123+08:00",
      "arn": "12345678901234567890123"
    }
  ],
  "billingInfo": {
    "labelAmount": 80.0,
    "labelCurrency": "USD",
    "transactionAmount": 80.0,
    "transactionCurrency": "USD",
    "transactionRate": 1.0,
    "settlementRate": 1.0,
    "settlementAmount": 79.7,
    "settlementCurrency": "USD",
    "settlementFeeAmount": 0.3,
    "feeItems": [
      {
        "categories": "PROCESSING_FEE",
        "amount": 0.3,
        "currency": "USD",
        "rate": 1.0
      }
    ]
  }
}
```

#### 7.10.8 查询规则

- 每次新查询使用新的 `orderInfo.orderId` 和 JWT `jti`；同一次查询的网络重试复用原查询 `orderId`，但必须更换 JWT `jti`。
- `transactionInfo.transactionId` 省略时返回订单全部动作，提供时只返回匹配动作；未匹配时返回明确的查询无结果错误。
- 查询响应必须返回首次交易保存的 `payerInfo`；`goodsInfo` 和 `shippingInfo` 在首次请求提供时返回，`billingCardHolderInfo` 返回直接 API 的请求快照或 Hosted Checkout 付款人最终提交的快照。同时返回每个交易动作的完整 `transactionInfo`、累计金额、安全 `threeDSInfo` 子集和平台计算的 `billingInfo`。
- 商户必须以每个动作的 `transactionStatus` 判断状态，不能仅根据 HTTP 200 或外层 `T200` 判断资金结果。

### 7.11 商户交易终态回调

商户可在首次支付、授权或预授权请求的 `transactionInfo.callbackUrl` 中提供 HTTP(S) 服务端地址，环境规则见 4.5。平台只在交易进入 `SUCCESS` 或 `FAILED` 终态后激活通知任务；`PROCESSING`、`PENDING` 等非终态不会作为最终结果通知。

#### 7.11.1 HTTP 协议

平台使用 `POST` 请求，Header 如下：

| Header | 必填 | 规则 |
| --- | --- | --- |
| `Authorization` | M | `Bearer {JWT}`，平台使用该商户的 `merchantKey` 按 HS256 签发 |
| `Content-Type` | M | 固定为 `application/json; charset=UTF-8` |
| `X-Callback-Version` | M | 当前固定为 `v1` |
| `X-Callback-Times` | M | 当前通知任务的第几次投递，从 `1` 开始 |
| `X-Callback-Event-Id` | M | 本次回调事件 ID，必须与 JWT `eventId`、`jti` 一致 |
| `X-OPGS-Notify-Id` | M | 通知任务 ID；自动重试和人工重发仍对应同一通知任务 |
| `X-OPGS-Transaction-Id` | M | 平台交易 ID，必须与 JWT `transactionId` 一致 |

回调 JWT 必须满足：

| Claim | 规则 |
| --- | --- |
| `alg` / `typ` | `HS256` / `JWT` |
| `iss` | `platform` |
| `aud` | 包含 `merchant-callback` |
| `merchantId` | 必须等于商户自己的配置商户号 |
| `eventId` / `jti` | 二者相等，并与 `X-Callback-Event-Id` 一致 |
| `notifyId` | 与 `X-OPGS-Notify-Id` 一致 |
| `transactionId` | 与 `X-OPGS-Transaction-Id` 一致 |
| `payloadSha256` | RequestBody `data` 密文的 SHA-256 小写十六进制摘要，用于阻止 JWT 与密文正文被互换 |
| `callbackTimes` | 与 `X-Callback-Times` 一致 |
| `iat` / `exp` | 有效短时窗口；商户必须校验签名、过期时间和服务器时钟 |

RequestBody 仍使用平台响应加密方案，外层只有 `data`：

```json
{
  "data": "<RSA-OAEP-256 + AES-256-GCM compact ciphertext>"
}
```

商户使用自己的响应私钥解密 `data`。回调明文采用触发本次终态通知的对应业务动作完整响应模型：字段、必返条件和含义分别以 7.2 至 7.9 节该动作的“响应参数”为准，而不是只返回交易 ID 和状态。回调必须返回首次交易保存的 `payerInfo`；`goodsInfo` 和 `shippingInfo` 在首次请求提供时返回，`billingCardHolderInfo` 返回直接 API 的请求快照或 Hosted Checkout 付款人最终提交的快照。`orderInfo` 必须返回五类累计金额，`billingInfo` 必须遵循 7.1.10 的返回规则。仍然禁止返回 `cardInfo`、`riskInfo`、CAVV、完整 PAN、CVV 或渠道原始报文。

```json
{
  "merchantInfo": {
    "merchantId": "<merchant-id>"
  },
  "orderInfo": {
    "orderNo": "ORDER-20260804-001",
    "orderId": "REQUEST-20260804-001",
    "amount": 10.0,
    "currency": "USD",
    "totalAuthorizedAmount": 10.0,
    "totalCapturedAmount": 10.0,
    "totalRefundAmount": 0,
    "totalAuthorizedCancelAmount": 0,
    "totalRefuseAmount": 0
  },
  "goodsInfo": [
    {
      "name": "Example Product",
      "quantity": 1,
      "amount": 10.0,
      "currency": "USD"
    }
  ],
  "billingCardHolderInfo": {
    "firstName": "Jane",
    "lastName": "Doe",
    "phone": "+12025550123",
    "email": "jane.doe@example.com",
    "country": "USA",
    "state": "CA",
    "city": "San Francisco",
    "street": "123 Market Street",
    "postal": "94105"
  },
  "payerInfo": {
    "payerId": "CUSTOMER-10001",
    "firstName": "Jane",
    "lastName": "Doe",
    "phone": "+12025550123",
    "email": "jane.doe@example.com",
    "country": "USA",
    "state": "CA",
    "city": "San Francisco",
    "street": "123 Market Street",
    "postal": "94105",
    "ipAddress": "203.0.113.10",
    "sessionId": "SESSION-10001",
    "browserInfo": {
      "browser": {
        "name": "Chrome",
        "version": "128.0.0.0"
      }
    },
    "userAgent": "Mozilla/5.0"
  },
  "shippingInfo": {
    "firstName": "Jane",
    "lastName": "Doe",
    "phone": "+12025550123",
    "email": "jane.doe@example.com",
    "country": "USA",
    "state": "CA",
    "city": "San Francisco",
    "street": "123 Market Street",
    "postal": "94105"
  },
  "threeDSInfo": {
    "eci": "05",
    "dsTransactionId": "550e8400-e29b-41d4-a716-446655440000",
    "threeDsVersion": "2.2.0",
    "status": "Y",
    "liabilityShifted": true
  },
  "transactionInfo": {
    "code": "T200",
    "message": "Success",
    "transactionId": "<platform-transaction-id>",
    "transactionType": "PAYMENT",
    "transactionStatus": "SUCCESS",
    "transactionDateTime": "2026-08-04T14:30:00.123+08:00",
    "paymentMethod": "BANK_CARD",
    "cardBrand": "VISA",
    "cardBin": "411111****1111",
    "description": "Order ORDER-20260804-001",
    "callbackUrl": "https://merchant.example/openapi/payment/callback",
    "merchantWebsite": "https://shop.merchant.example/checkout",
    "authCode": "123456",
    "arn": "12345678901234567890123"
  },
  "billingInfo": {
    "labelAmount": 10.0,
    "labelCurrency": "USD",
    "transactionAmount": 10.0,
    "transactionCurrency": "USD",
    "transactionRate": 1.0,
    "settlementRate": 1.0,
    "settlementAmount": 9.7,
    "settlementCurrency": "USD",
    "settlementFeeAmount": 0.3,
    "feeItems": [
      {
        "categories": "PROCESSING_FEE",
        "amount": 0.3,
        "currency": "USD",
        "rate": 1.0
      }
    ]
  }
}
```

#### 7.11.2 成功确认和重试

商户只有在本地业务事务已经成功提交后，才返回：

```text
HTTP/1.1 200 OK
Content-Type: text/plain; charset=UTF-8

succeed
```

平台同时要求 HTTP 状态码精确为 `200`，响应正文去除首尾空白后精确等于小写 `succeed`。其他 2xx、3xx、4xx、5xx、超时、网络异常或其他正文均视为失败并进入重试。

平台自动重试同一个通知任务时，`X-Callback-Event-Id` 固定为该任务的 `notifyId`；RocketMQ 重投同一人工重发消息时也保持原事件 ID。管理系统每次重新点击“重发回调”会生成新的事件 ID，但 `notifyId` 不变。商户必须按 `eventId` 持久化幂等，并用 `notifyId` 关联同一通知任务的多次投递和人工操作审计，不能只使用进程内缓存。已处理事件再次到达时，不重复更新订单，直接返回 `200 + succeed`；同一事件仍在处理中时返回非 2xx，使平台稍后重试。

平台在创建通知任务时冻结正式回调载荷，并在交易进入终态时以事务内状态同步更新该快照。审计字段 `payloadJsonMasked` 只用于管理端展示和日志脱敏，绝不会作为商户回调密文的明文来源。

终态更新只能改变交易结果、累计金额、平台计算的 `billingInfo` 和安全 `threeDSInfo` 等平台生成字段；不得用终态更新覆盖首次交易已保存的 `goodsInfo/billingCardHolderInfo/payerInfo/shippingInfo` 快照。Hosted Checkout 的 `billingCardHolderInfo` 以付款人最终提交时保存的值为准。自动重试必须使用同一份冻结后的终态业务载荷，但每次投递会重新生成加密密文和短时效 JWT，因此商户不能按密文字符串做业务幂等。

推荐事件表至少包含：

```sql
CREATE TABLE merchant_callback_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(96) NOT NULL,
    notify_id VARCHAR(64) NOT NULL,
    transaction_id VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(32) NOT NULL,
    callback_times INT NOT NULL,
    process_status VARCHAR(16) NOT NULL,
    processed_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_callback_event_id (event_id),
    KEY idx_merchant_callback_transaction (merchant_id, transaction_id)
) ENGINE=InnoDB;
```

该 SQL 是商户侧示例，商户应按自己的数据库规范评审后执行。业务处理建议在一个本地事务中完成“占用 eventId、校验交易号和商户订单号、按允许状态推进商户订单、标记 PROCESSED”；业务失败时不得返回 `succeed`。

#### 7.11.3 Java SDK 接收方式

Java SDK 提供 `MerchantCallbackProcessor`，负责 Header/JWT 一致性校验、密文解密和事件幂等编排。Controller 必须使用 `POST`，并且只在处理器返回后响应 `200 + succeed`：

```java
@PostMapping(
        value = "/openapi/payment/callback",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.TEXT_PLAIN_VALUE)
public ResponseEntity<String> callback(
        @RequestHeader HttpHeaders headers,
        @RequestBody String encryptedBody) {
    String acknowledgement = callbackApplicationService.handle(
            headers.toSingleValueMap(), encryptedBody);
    return ResponseEntity.ok()
            .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
            .body(acknowledgement);
}
```

ApplicationService 必须使用商户本地数据库事务包裹整个处理器调用：

```java
@Service
public class CallbackApplicationService {
    private final MerchantCallbackProcessor callbackProcessor;
    private final PaymentCallbackService paymentCallbackService;

    @Transactional(rollbackFor = Exception.class)
    public String handle(Map<String, String> headers, String encryptedBody) {
        return callbackProcessor.process(
                headers,
                encryptedBody,
                PaymentCreateResponse.class,
                (context, payload) -> paymentCallbackService.handle(context, payload));
    }
}
```

`MerchantCallbackEventStore` 的生产实现必须依赖数据库 `event_id` 唯一键，并与商户订单仓储使用同一个事务管理器和默认事务传播：`acquire` 插入或 CAS 占用事件，`markProcessed` 在业务成功后标记完成，`release` 在业务事务失败后释放本次占用。Controller 不应自行解析 JWT、记录密文/明文或在返回 `succeed` 后再异步处理业务。

#### 7.11.4 商户订单重试规则

平台当前首次交易规则如下：

- 同一个 `orderInfo.orderId` 只能表示同一个首次交易动作；网络重试必须复用原 `orderId` 和完全相同的业务内容，平台返回已有结果。
- 同一个 `orderInfo.orderNo` 表示同一个商户业务订单，支付、授权和预授权共享该订单生命周期。
- 同一支付流处于 `PROCESSING` 或已经 `SUCCESS` 时，新的 `orderId` 会被拒绝，避免同一商户订单成功多笔。
- 上一笔明确进入 `FAILED` 后，付款人可继续支付；商户或 Hosted Checkout 使用新的 `orderId` 发起新尝试，原失败交易仍完整保留用于审计。
- 渠道结果未知、超时或仍为 `PROCESSING` 不等于失败，不能换 `orderId` 重发资金请求，应使用原交易 ID 查询。

因此，同一个 `orderInfo.orderNo` 可以保留多笔失败尝试，但最多只能有一笔活跃或成功的首次交易，商户不需要为每次付款失败创建新的业务订单号。

## 8. Hosted Checkout

### 8.1 创建收银台会话

商户创建平台托管收银台会话并将返回的 `checkoutUrl` 交给付款人浏览器打开。创建请求不接收卡信息，也不允许商户选择支付方式；页面自动展示当前商户已开通且本次订单可用的全部支付方式。

#### 8.1.1 接口定义

| 项目 | 值 | 必填 | 说明 |
| --- | --- | --- | --- |
| Method | `POST` | M | 固定为 POST |
| Path | `/api/rest/checkout/v1/session` | M | API v1 接口地址 |
| `Content-Type` | `application/json` | M | 商户显式配置 |
| `Authorization` | `Bearer {JWT}` | M | 每次 HTTP 尝试使用新的 JWT `jti` |

**重复请求与重试规则**

同一业务动作的网络重试必须保持明文业务内容和 `orderInfo.orderId` 不变，但每次重试必须重新生成请求密文和 JWT `jti`。

#### 8.1.2 请求参数

##### 主参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息对象 |
| `orderInfo` | Object | M | 收银台订单信息 |
| `goodsInfo` | List<Object> | O | 可选商品或服务明细；提供后作为订单快照保存 |
| `billingCardHolderInfo` | Object | O | 收银台账单表单预填值，字段规则与直接支付一致 |
| `payerInfo` | Object | M | 付款人信息；`ipAddress` 必填，其他子字段可选，可用于页面预填 |
| `shippingInfo` | Object | O | 收货人快照，字段结构与直接支付一致 |
| `riskInfo` | Object | O | 后续版本预留对象；当前不参与风控，响应不返回 |
| `transactionInfo` | Object | O | 收银台跳转、通知和语言参数；没有扩展参数时可省略 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 必须与 JWT `merchantId` 一致 |
| `subMerchantInfo` | Object | O | 子商户信息；不涉及子商户时省略 |


##### subMerchantInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `subId` | String（32） | C | 提供子商户对象时必填 |
| `subName` | String（128） | C | 临时子商户与 `subCompanyName` 至少填写一个 |
| `subCompanyName` | String（128） | C | 临时子商户与 `subName` 至少填写一个 |
| `subCountryCode` | String（3） | C | 临时子商户必填，ISO 3166-1 alpha-3 |
| `subState` | String（3） | O | 州、省或地区代码 |
| `subCity` | String（64） | C | 临时子商户必填 |
| `subStreet` | String（128） | C | 临时子商户必填 |
| `subEmail` | String（64） | O | 子商户联系人邮箱 |
| `subPhone` | String（32） | O | 子商户联系人电话 |
| `subPostal` | String（32） | O | 子商户邮编 |
| `subTaxId` | String（32） | O | 子商户税号，不得包含中文字符 |
| `merchantCategory` | String（4） | C | 临时子商户必填，四位 MCC |
| `intesCode` | String（4） | O | 特定卡组要求时传 3 至 4 位字母或数字 |
| `chargeType` | String（3） | O | 特定卡组要求时传 3 位字母或数字 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 订单金额，必须大于 0 |
| `currency` | String（3） | M | ISO 4217 三位大写币种代码 |
| `orderNo` | String（64） | M | 商户业务订单号 |
| `orderId` | String（64） | M | 创建收银台会话幂等标识 |


##### goodsInfo 元素子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | String（128） | M | 商品或服务名称 |
| `quantity` | Integer | M | 商品数量，必须大于 0 |
| `amount` | BigDecimal（12,2） | M | 商品行总金额，不是商品单价 |
| `currency` | String（3） | M | 必须与 `orderInfo.currency` 一致 |


##### billingCardHolderInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | C | 提供对象时必填 |
| `lastName` | String（32） | C | 提供对象时必填 |
| `phone` | String（32） | C | 提供对象时必填 |
| `email` | String（64） | C | 提供对象时必填 |
| `country` | String（3） | C | 提供对象时必填，ISO 3166-1 alpha-3 |
| `state` | String（3） | C | 提供对象时必填 |
| `city` | String（64） | C | 提供对象时必填 |
| `street` | String（128） | C | 提供对象时必填 |
| `postal` | String（32） | C | 提供对象时必填 |


##### payerInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `payerId` | String（64） | O | 商户侧付款人标识 |
| `firstName` | String（32） | O | 付款人名 |
| `lastName` | String（32） | O | 付款人姓 |
| `phone` | String（32） | O | 付款人电话 |
| `email` | String（64） | O | 付款人邮箱 |
| `country` | String（3） | O | ISO 3166-1 alpha-3 |
| `state` | String（64） | O | 州、省或地区 |
| `city` | String（64） | O | 城市 |
| `street` | String（128） | O | 街道地址 |
| `postal` | String（32） | O | 邮编 |
| `ipAddress` | String（64） | M | 付款人公网 IPv4 或 IPv6 地址；用于 IP AML、黑白名单和国家/地区规则 |
| `sessionId` | String（128） | O | 商户侧浏览器会话标识 |
| `browserInfo` | Object | O | 浏览器环境信息 |
| `userAgent` | String（512） | O | 浏览器 User-Agent |


##### shippingInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | O | 收货人名 |
| `lastName` | String（32） | O | 收货人姓 |
| `phone` | String（32） | O | 收货人电话 |
| `email` | String（64） | O | 收货人邮箱 |
| `country` | String（3） | O | ISO 3166-1 alpha-3 |
| `state` | String（3） | O | 州、省或地区 |
| `city` | String（64） | O | 城市 |
| `street` | String（128） | O | 街道地址 |
| `postal` | String（32） | O | 邮编 |


##### riskInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `customerId` | String（64） | O | 预留客户标识；当前版本不使用 |
| `deviceFingerprint` | String（128） | O | 预留设备指纹；当前版本不使用 |
| `shippingAddress` | String（256） | O | 预留收货地址；当前版本不使用 |
| `shippingPostalCode` | String（32） | O | 预留收货邮编；当前版本不使用 |
| `shippingCountry` | String（3） | O | 预留收货国家；当前版本不使用 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `description` | String（128） | O | 订单或交易描述 |
| `callbackUrl` | String（512） | O | 终态通知地址 |
| `redirectUrl` | String（512） | O | 交易结果页返回商户网站的地址；省略时永久停留平台结果页 |
| `language` | String（8） | O | 收银台显示语言，例如 `zh-CN`、`en-US` |

商户不能在创建会话请求中指定或筛选支付方式、卡品牌、支付渠道、3DS 策略、会话有效期或页面重试次数；这些能力全部由平台根据商户配置和风险策略决定。

#### 8.1.3 请求密文示例

```json
{
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 8.1.4 请求明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 49.97,
    "currency": "USD",
    "orderNo": "M202608010010",
    "orderId": "CHECKOUT202608010001"
  },
  "goodsInfo": [
    {
      "name": "Canvas Backpack",
      "quantity": 1,
      "amount": 49.97,
      "currency": "USD"
    }
  ],
  "payerInfo": {
    "payerId": "CUSTOMER-10001",
    "firstName": "Jessica",
    "lastName": "Miller",
    "phone": "+12025550124",
    "email": "jessica.miller@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001",
    "ipAddress": "203.0.113.10",
    "sessionId": "SESSION-10002",
    "browserInfo": {
      "browser": {
        "name": "Chrome",
        "version": "128.0.0.0"
      }
    },
    "userAgent": "Mozilla/5.0"
  },
  "shippingInfo": {
    "firstName": "Jessica",
    "lastName": "Miller",
    "phone": "+12025550124",
    "email": "jessica.miller@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "transactionInfo": {
    "description": "Order M202608010010",
    "callbackUrl": "https://merchant.example/openapi/payment/callback",
    "redirectUrl": "https://merchant.example/payment/result",
    "language": "en-US"
  }
}
```

#### 8.1.5 响应参数

**加密响应外层参数**

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `code` | String（16） | M | OpenAPI 外层处理结果；成功返回业务密文时为 `T200` |
| `message` | String（128） | M | OpenAPI 外层处理结果描述 |
| `data` | String | M | 五段式加密业务响应；商户使用响应私钥解密 |

**解密后 `data` 参数**

##### 主参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo` | Object | M | 商户信息 |
| `orderInfo` | Object | M | 收银台订单信息 |
| `goodsInfo` | List<Object> | C | 首次请求提供时完整原样返回 |
| `billingCardHolderInfo` | Object | C | 请求提供时原样返回 |
| `payerInfo` | Object | M | 首次请求必传的付款人快照 |
| `shippingInfo` | Object | C | 请求提供时完整原样返回 |
| `transactionInfo` | Object | C | 请求提供任一交易扩展字段时返回对应快照 |
| `checkoutUrl` | String（512） | M | 平台生成的收银台访问地址 |


##### merchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 商户号 |
| `subMerchantInfo` | Object | C | 请求提供子商户信息时完整原样返回 |


##### subMerchantInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `subId` | String（32） | C | 子商户快照存在时返回 |
| `subName` | String（128） | C | 原请求存在时原样返回 |
| `subCompanyName` | String（128） | C | 原请求存在时原样返回 |
| `subCountryCode` | String（3） | C | 原请求存在时原样返回 |
| `subState` | String（3） | C | 原请求存在时原样返回 |
| `subCity` | String（64） | C | 原请求存在时原样返回 |
| `subStreet` | String（128） | C | 原请求存在时原样返回 |
| `subEmail` | String（64） | C | 原请求存在时原样返回 |
| `subPhone` | String（32） | C | 原请求存在时原样返回 |
| `subPostal` | String（32） | C | 原请求存在时原样返回 |
| `subTaxId` | String（32） | C | 原请求存在时原样返回 |
| `merchantCategory` | String（4） | C | 原请求存在时原样返回 |
| `intesCode` | String（4） | C | 原请求存在时原样返回 |
| `chargeType` | String（3） | C | 原请求存在时原样返回 |


##### orderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `amount` | BigDecimal（12,2） | M | 订单金额 |
| `currency` | String（3） | M | 订单币种 |
| `orderNo` | String（64） | M | 商户业务订单号 |
| `orderId` | String（64） | M | 创建会话幂等标识 |


##### goodsInfo 元素子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `name` | String（128） | C | 商品或服务名称 |
| `quantity` | Integer | C | 商品数量，必须大于 0 |
| `amount` | BigDecimal（12,2） | C | 商品行总金额，不是商品单价 |
| `currency` | String（3） | C | 必须与 `orderInfo.currency` 一致 |


##### billingCardHolderInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | C | 原请求值 |
| `lastName` | String（32） | C | 原请求值 |
| `phone` | String（32） | C | 原请求值 |
| `email` | String（64） | C | 原请求值 |
| `country` | String（3） | C | 原请求值 |
| `state` | String（3） | C | 原请求值 |
| `city` | String（64） | C | 原请求值 |
| `street` | String（128） | C | 原请求值 |
| `postal` | String（32） | C | 原请求值 |


##### payerInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `payerId` | String（64） | C | 原请求存在时原样返回 |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（64） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |
| `ipAddress` | String（64） | M | 首次请求必传的付款人公网 IP 原样返回 |
| `sessionId` | String（128） | C | 原请求存在时原样返回 |
| `browserInfo` | Object | C | 原请求存在时原样返回 |
| `userAgent` | String（512） | C | 原请求存在时原样返回 |


##### shippingInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `firstName` | String（32） | C | 原请求存在时原样返回 |
| `lastName` | String（32） | C | 原请求存在时原样返回 |
| `phone` | String（32） | C | 原请求存在时原样返回 |
| `email` | String（64） | C | 原请求存在时原样返回 |
| `country` | String（3） | C | 原请求存在时原样返回 |
| `state` | String（3） | C | 原请求存在时原样返回 |
| `city` | String（64） | C | 原请求存在时原样返回 |
| `street` | String（128） | C | 原请求存在时原样返回 |
| `postal` | String（32） | C | 原请求存在时原样返回 |


##### transactionInfo 子参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `description` | String（128） | C | 请求提供时原样返回 |
| `callbackUrl` | String（512） | C | 请求提供时原样返回 |
| `redirectUrl` | String（512） | C | 请求提供时原样返回 |
| `language` | String（8） | C | 请求提供时原样返回 |

`riskInfo` 是后续版本预留对象，当前不参与风控，也不在创建会话响应中返回。`checkoutUrl` 是平台响应字段，商户不得解析或修改其中的不透明令牌。

#### 8.1.6 响应密文示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 8.1.7 响应明文示例

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 49.97,
    "currency": "USD",
    "orderNo": "M202608010010",
    "orderId": "CHECKOUT202608010001"
  },
  "goodsInfo": [
    {
      "name": "Canvas Backpack",
      "quantity": 1,
      "amount": 49.97,
      "currency": "USD"
    }
  ],
  "payerInfo": {
    "payerId": "CUSTOMER-10001",
    "firstName": "Jessica",
    "lastName": "Miller",
    "phone": "+12025550124",
    "email": "jessica.miller@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001",
    "ipAddress": "203.0.113.10",
    "sessionId": "SESSION-10002",
    "browserInfo": {
      "browser": {
        "name": "Chrome",
        "version": "128.0.0.0"
      }
    },
    "userAgent": "Mozilla/5.0"
  },
  "shippingInfo": {
    "firstName": "Jessica",
    "lastName": "Miller",
    "phone": "+12025550124",
    "email": "jessica.miller@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "transactionInfo": {
    "description": "Order M202608010010",
    "callbackUrl": "https://merchant.example/openapi/payment/callback",
    "redirectUrl": "https://merchant.example/payment/result",
    "language": "en-US"
  },
  "checkoutUrl": "https://{checkout-host}/checkout/{opaque-token}"
}
```

### 8.2 付款人资料预填与修改

- 请求提供 `goodsInfo` 时，收银台按列表展示商品名称、数量、行金额和币种；付款人不能通过页面修改商户商品快照。
- 商户必须提供 `payerInfo`，其中 `ipAddress` 必填，其他子字段可选。该 IP 必须是付款人公网 IP，不是商户服务器调用 OpenAPI 时的出口 IP；平台还可将付款人打开收银台时的浏览器连接 IP 作为独立风控信号进行一致性校验。
- 请求提供 `billingCardHolderInfo` 时，使用整个对象预填账单持卡人表单；`billingCardHolderInfo` 和 `payerInfo` 同时存在时，账单表单始终优先使用 `billingCardHolderInfo`。
- 未提供 `billingCardHolderInfo` 时，使用 `payerInfo` 的同名可选字段预填账单持卡人表单。对应字段仍然缺失时，收银台要求付款人补充所有页面必填资料后才能继续。
- `billingCardHolderInfo` 一旦存在，不再按单个空字段回退到 `payerInfo`；商户需要混合资料时，应在创建会话前组装完整对象。
- 所有预填字段均允许付款人修改。页面最终提交值用于当次付款人资料校验、AML、黑名单和白名单规则，并在渠道协议支持时透传渠道或收单行。
- 页面最终提交值形成交易的 `billingCardHolderInfo` 快照，并在支付响应、查询和终态回调中返回；创建会话时的 `payerInfo` 作为商户原始付款人快照独立保存，不能被页面修改后的值覆盖。
- 请求提供 `shippingInfo` 时，该快照用于收货人和收货地址的 AML、黑名单、白名单及资料一致性校验。
- 卡号、有效期和 CVV 由收银台页面采集；创建会话请求不接收 `cardInfo`，任何商户响应也不返回完整卡数据。

### 8.3 支付方式与结果确认

收银台根据当前商户已开通的支付产品、币种、金额、国家和风险策略显示全部可用支付方式。商户不能通过创建会话请求隐藏、排序或指定支付方式；未开放的选择参数不得上送。

`transactionInfo.redirectUrl` 只用于交易结果页返回商户网站，不是资金成功证明。商户必须通过第 7.10 节交易查询或第 7.11 节终态回调确认最终 `transactionStatus`；页面返回流程见第 8.4 节。

### 8.4 结果页与返回商户网站

#### 8.4.1 页面行为

- 5 秒倒计时只在交易进入 `SUCCESS` 或 `FAILED` 终态并展示结果后启动；`PROCESSING`、`PENDING` 等非终态不启动返回倒计时。
- 商户未提供 `transactionInfo.redirectUrl` 时，交易完成并展示结果后页面永久停留在平台结果页；平台不启动倒计时，不自动跳转，也不显示“返回商户网站”按钮。
- 商户提供 `transactionInfo.redirectUrl` 时，交易完成并展示结果后页面启动 5 秒倒计时，并显示“返回商户网站”按钮。
- 倒计时结束后，付款人浏览器自动使用隐藏表单向 `redirectUrl` 发起 `POST`。
- 付款人在倒计时结束前点击“返回商户网站”时，页面停止倒计时并立即提交同一份表单，不改用 `GET`，也不改变表单参数。
- 页面只触发一次主动提交；但浏览器刷新、返回或网络重试仍可能造成重复 `POST`，商户接收端必须允许幂等重复访问。

#### 8.4.2 表单提交协议

| 项目 | 值 | 说明 |
| --- | --- | --- |
| Method | `POST` | 自动返回和付款人点击按钮均使用 POST |
| Content-Type | `application/x-www-form-urlencoded; charset=UTF-8` | 字段名和值按 UTF-8 进行表单编码 |
| Target | `transactionInfo.redirectUrl` | 使用创建收银台会话时商户提供的原始地址 |
| Request Source | 付款人浏览器 | 不是平台服务端回调 |
| Authorization | 不发送 | 浏览器表单 POST 不携带 OpenAPI `Authorization` Header |
| Encryption | 不使用 OpenAPI `data` 密文 | 表单字段为浏览器可见明文，不包含卡或人员敏感信息 |

**POST Form 参数**

| 字段 | 类型/最大长度 | 必传 | 说明 |
| --- | --- | --- | --- |
| `merchantId` | String（16） | M | 当前收银台会话所属商户号 |
| `orderNo` | String（64） | M | 创建会话请求中的商户业务订单号 |
| `orderId` | String（64） | M | 创建会话请求中的 `orderInfo.orderId` |
| `transactionId` | String（64） | M | 本次收银台支付生成的平台交易 ID |
| `transactionType` | String（32） | M | 当前固定为 `PAYMENT` |
| `transactionStatus` | String（16） | M | 交易完成时的平台终态：`SUCCESS` 或 `FAILED` |
| `transactionDateTime` | String（64） | M | 平台交易时间，ISO-8601 offset datetime |
| `code` | String（16） | M | 当前交易动作结果码 |
| `message` | String（128） | M | 当前交易动作结果描述，仅用于展示或排查 |

表单示例：

```html
<form method="post" action="https://merchant.example/payment/result">
  <input type="hidden" name="merchantId" value="200045">
  <input type="hidden" name="orderNo" value="M202608010010">
  <input type="hidden" name="orderId" value="CHECKOUT202608010001">
  <input type="hidden" name="transactionId" value="202608011500001230010">
  <input type="hidden" name="transactionType" value="PAYMENT">
  <input type="hidden" name="transactionStatus" value="SUCCESS">
  <input type="hidden" name="transactionDateTime" value="2026-08-01T15:00:00.123+08:00">
  <input type="hidden" name="code" value="T200">
  <input type="hidden" name="message" value="Success">
  <button type="submit">返回商户网站</button>
</form>
```

#### 8.4.3 商户接收要求

- `redirectUrl` 应指向能够接收跨站表单 POST 的商户服务端地址，不能只配置为不接受 POST 的静态页面。商户接收成功后可以使用 HTTP `303 See Other` 跳转到自己的结果展示页。
- 浏览器表单没有 OpenAPI JWT、签名或请求体加密，字段可能被付款人修改或重复提交。商户不得仅凭表单中的 `code` 或 `transactionStatus` 更新订单、发货、入账或退款。
- 商户应使用 `orderNo` 和 `transactionId` 定位订单，再通过第 7.10 节交易查询或第 7.11 节终态回调确认最终结果。
- 商户接收端必须兼容相同参数的重复 POST，并对展示 `message` 等外部输入执行 HTML 转义。
- 表单不传 `cardInfo`、`billingCardHolderInfo`、`payerInfo`、`shippingInfo`、`goodsInfo`、`billingInfo`、完整 PAN、CVV 或任何密钥材料。

## 9. 代付接口（规划中）

### 9.1 创建代付（规划中）

本章仅展示代付接口的规划草案，当前尚未正式开放，商户不得发起联调或生产调用。接口路径、字段、校验规则和返回模型在正式发布前均可能调整；开放时平台将提供新的生效版本和接入通知。

规划目标为创建一笔代付请求，并在成功响应中返回平台代付单号。

#### 9.1.1 接口定义

| 项目 | 值 | 必填 | 说明 |
| --- | --- | --- | --- |
| Method | `POST` | M | 固定为 POST |
| Path | `/api/rest/payout/v1/create` | M | 规划中的 API v1 接口地址，当前不可调用 |
| `Content-Type` | `application/json` | M | 商户显式配置 |
| `Authorization` | `Bearer {JWT}` | M | 每次 HTTP 尝试使用新的 JWT `jti` |

**重复请求与重试规则**

同一代付请求的网络重试必须保持全部明文业务内容和 `merchantOrderNo` 不变，但每次重试必须重新生成请求密文和 JWT `jti`。

#### 9.1.2 请求参数

##### 主参数

| 字段 | 类型/最大长度 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantOrderNo` | String（64） | M | 商户代付订单号，商户侧唯一并保持稳定 |
| `currency` | String（3） | M | ISO 4217 三位大写币种代码 |
| `amount` | Integer | M | 对应币种最小单位正整数 |
| `receiverAccountNo` | String | M | 收款账户号；只允许出现在加密前内存和加密后的 `data` 中 |

#### 9.1.3 请求密文示例

```json
{
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 9.1.4 请求明文示例

```json
{
  "merchantOrderNo": "PO-M-202608010001",
  "currency": "USD",
  "amount": 1025,
  "receiverAccountNo": "{sandbox-receiver-account}"
}
```

示例表示 USD 10.25。商户必须先根据币种查询结果换算最小单位，不能固定乘以 100。

零小数币种的 `amount` 必须是正整数主单位金额；三位小数币种的最小单位 `amount` 必须是 `10` 的整数倍，平台不会把非零第三位静默舍入为 0。

#### 9.1.5 响应参数

**加密响应外层参数**

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `code` | String（16） | M | OpenAPI 外层处理结果；成功返回业务密文时为 `T200` |
| `message` | String（128） | M | OpenAPI 外层处理结果描述 |
| `data` | String | M | 五段式加密业务响应；商户使用响应私钥解密 |

**解密后 `data` 参数**

##### 主参数

| 字段 | 类型/最大长度 | 必返 | 说明 |
| --- | --- | --- | --- |
| `解密结果` | String（64） | M | 平台代付单号；不是 JSON 对象 |

#### 9.1.6 响应密文示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.{encryptedKey}.{iv}.{cipherText}.{tag}"
}
```

#### 9.1.7 响应明文示例

```json
"PO202608011230001230001"
```

解密结果是平台代付单号字符串，不是对象。商户必须同时保存 `merchantOrderNo` 和平台代付单号。

#### 9.1.8 业务规则

- `amount` 使用对应币种的最小单位整数；商户必须通过第 6.2 节币种字典取得 `minorUnitMultiplier`，不得固定乘以 100。
- `receiverAccountNo` 是敏感字段，不得写入日志、URL、异常文本、监控标签或未加密消息。
- 同一 `merchantOrderNo` 只能表示同一笔代付业务；重试时不得修改币种、金额或收款账户。

规划草案暂未定义对应的商户查询接口。正式开放时，平台将同步提供结果不确定场景的查询或人工处理流程；在此之前商户不得调用本章路径。

## 10. 附录

### 10.1 响应码

客户端应以 `code` 作为稳定判断依据，`message` 用于展示和排查。参数错误等场景的 `message` 可能附带字段详情，不应对完整文案做字符串等值判断。

**成功、受理和交易结果码**

下表中的 `T200` 既可作为外层 OpenAPI 成功码，也可作为内层当前动作成功码；`T201/T202/T203/F207/F210` 仅用于解密后的 `transactionInfo.code`，不得作为成功 HTTP 响应的外层 `code`。

| code | 标准 message | 说明 | 商户处理 |
| --- | --- | --- | --- |
| `T200` | `Success` | OpenAPI 成功返回数据，或当前交易动作成功 | 外层为该值时解密 `data`；内层为该值时继续检查 `transactionStatus` |
| `T201` | `Accepted` | 已受理，最终结果未确定 | 保存标识并查询 |
| `T202` | `Processing` | 正在处理 | 不重复提交，按建议间隔查询 |
| `T203` | `Pending` | 结果待确认 | 不重复提交，查询或等待已开通的通知 |
| `F207` | `Issuer or acquirer rejected the transaction` | 发卡行、收单行或上游拒绝 | 不自动重复扣款，向付款人展示合适提示 |
| `F210` | 平台商户可见拒绝文案 | 平台、风控或渠道拒绝 | 不披露内部规则；按业务决定是否允许付款人更换方式 |

**鉴权错误码**

| code | 标准 message | 场景 |
| --- | --- | --- |
| `F401` | `Unauthorized` | 通用未授权 |
| `F401001` | `Authorization required` | 缺少或错误提供 `Authorization` Header |
| `F401002` | `Authorization JWT is invalid, HS256 is required` | JWT 格式、类型或算法非法 |
| `F401003` | `Authorization JWT exp is invalid or expired` | `exp` 非法或已过期 |
| `F401004` | `Authorization JWT iat is invalid` | `iat` 非法或时钟偏差过大 |
| `F401005` | `Authorization JWT iss is invalid` | `iss` 不是 `merchant` |
| `F401006` | `Authorization JWT aud is invalid` | `aud` 不包含 `gateway` |
| `F401007` | `Authorization JWT signature verification failed` | HS256 签名失败 |
| `F401008` | `Merchant signing key is not configured` | 商户签名密钥未配置 |
| `F401009` | `Merchant is invalid or unavailable` | 商户不存在、不可用或请求身份不匹配 |

**请求、权限和协议错误码**

| code | 标准 message | 场景 |
| --- | --- | --- |
| `F400` | `Bad request` | 请求不符合协议 |
| `F402001` | `Invalid request parameter` | 参数值或格式非法 |
| `F402002` | `Required request parameter is missing` | 必填参数缺失 |
| `F402003` | `Encrypted request data is invalid` | `data` 缺失、五段格式错误、密钥错误或 GCM 校验失败 |
| `F403` | `Forbidden` | 已认证商户无权访问资源或来源不允许 |
| `F404` | `Not found` | API 路径不存在 |
| `F405` | `Method Not Allowed` | HTTP Method 不支持 |
| `F429` | `Too many requests` | 请求超过频率限制 |

**商户配置和交易业务错误码**

| code | 标准 message | 场景 |
| --- | --- | --- |
| `F409` | `Merchant config not found` | 商户配置或密钥材料不可用 |
| `F410` | `Card not support` | 卡类型不支持 |
| `F411` | `Transaction currency is not supported` | 交易币种不支持 |
| `F412` | `Transaction type is not supported` | 交易类型不支持 |
| `F413` | `Unsupported card brands` | 卡品牌不支持 |
| `F414` | `Original transaction rejected.` | 原交易渠道或 MID 已不可用，关联动作被拒绝 |
| `F510` | `Order already exist` | 商户订单已存在或发生幂等冲突 |
| `F511` | `Order does not exist` | 订单不存在 |
| `F512` | `The search result set is invalid/does not exist` | 查询无结果 |
| `F515` | `transactionId repeat` | 交易标识重复 |

**系统和报文错误码**

| code | 标准 message | 场景 |
| --- | --- | --- |
| `F500` | `The system is busy; please try again later.` | 平台内部异常 |
| `F502` | `Bad gateway` | 上游服务不可用或响应异常 |
| `F503` | `The network is busy, please try again later` | 服务繁忙 |
| `Z605` | `Request parse error` | 请求报文解析失败 |
| `Z606` | `Response parse error` | 响应报文解析失败 |

### 10.2 支付结果处理顺序

商户收到支付类响应后按以下顺序处理：

1. HTTP 非 2xx、连接失败或超时：进入 10.3 节的结果不确定流程。
2. 响应不是合法 JSON：记录脱敏摘要和请求标识，进入结果不确定流程。
3. 外层 `code` 不是 `T200` 时按 OpenAPI 错误处理；`data` 为空时不得尝试解密。
4. 外层 `code=T200` 且 `data` 非空时，使用商户响应私钥解密。
5. 读取解密后的 `transactionInfo.code` 和 `transactionInfo.transactionStatus`。
6. `transactionInfo.code=T200` 且 `transactionStatus=SUCCESS`：当前动作成功。
7. `transactionInfo.code=T201/T202/T203` 或 `transactionStatus=PROCESSING/PENDING`：保存全部交易标识并查询，不要重复提交资金动作。
8. `transactionStatus=FAILED`：当前动作失败；是否允许付款人使用新动作标识再次尝试由商户业务决定。
9. `code` 与 `transactionStatus` 暂时不一致：保存响应并查询，以查询得到的持久化状态为准，不得根据单次文案推断成功。

示意流程：

```text
HTTP/网络结果
  ├─ 失败或超时 -> 查询原订单 / 人工核查
  └─ 收到 JSON
       ├─ 外层 code != T200 或 data 为空 -> 按 OpenAPI 结果处理
       └─ 外层 code == T200 且 data 非空
            └─ 读取 transactionInfo.code + transactionStatus
                 ├─ T200 + SUCCESS -> 当前动作成功
                 ├─ T201/T202/T203 或 PROCESSING/PENDING -> 查询最终结果
                 ├─ transactionStatus == FAILED -> 当前动作失败
                 └─ 二者不一致 -> 保存响应并查询
```

### 10.3 幂等与重试决策

| 场景 | 是否可直接重发 | 正确处理 |
| --- | --- | --- |
| JWT 或参数校验明确失败 | 修正后可发 | 生成新 `jti`；业务幂等标识按原业务语义保持 |
| `F402003` 加密失败 | 否 | 修复算法、密钥或报文格式后再发送 |
| `F429` 限流 | 不应立即重发 | 按平台退避要求等待，使用新 `jti`，保持原业务幂等标识 |
| `F500/F502/F503` | 否 | 支付先查询；确认不存在后才按平台指引重试 |
| 连接失败或读取超时 | 否 | 结果未知；支付先查询，不得换 `orderId` 重复扣款 |
| 内层 `T201/T202/T203` | 否 | 保存 `transactionId`，按间隔查询 |
| 内层 `F207/F210` | 否 | 当前动作已失败，不自动重复提交同一扣款 |
| `F510/F515` | 否 | 查询既有订单或核对幂等参数，不得换号绕过 |
| 查询无结果 | 可再次查询 | 每次查询使用新的查询 `orderId` 和 JWT `jti` |
| IP 或卡 BIN 未命中 | 不应立即重发 | 按 `T200 + matched=false` 处理；需要再次查询时使用新的 JWT `jti` |
| 代付结果不确定 | 否 | 保留 `merchantOrderNo`，联系平台核查；不得换号自动重提 |

重试同一个支付业务动作时，请求业务内容和 `orderInfo.orderId` 必须保持一致，但 JWT `jti` 必须重新生成。相同业务幂等标识对应不同金额、币种、源交易号或收款方，应视为幂等冲突。

### 10.4 常见问题排查

#### 10.4.1 JWT 签名失败

按以下顺序检查：

1. `Authorization` 是否为 `Bearer`、一个空格和完整 JWT。
2. Header 是否为 `typ=JWT`、`alg=HS256`。
3. `merchantKey` 是否属于当前环境和当前 `merchantId`。
4. 平台将 `merchantKey` 文本按 UTF-8 字节作为 HMAC-SHA256 secret；不要擅自进行 Base64 解码。
5. Header 和 Payload 是否分别使用无填充 Base64Url，而不是普通 Base64。
6. 签名原文是否严格为 `headerSegment.payloadSegment`。

#### 10.4.2 JWT 过期或时间非法

- `iat/exp` 必须是秒，不是毫秒。
- `exp` 必须大于 `iat`，有效期不能超过 180 秒。
- 检查商户服务器 NTP 和容器时间。
- 不要预生成并长时间缓存 JWT。

#### 10.4.3 请求被判定为重放

- 每次 HTTP 尝试生成新的随机 `jti`。
- 不要在多个并发请求中共享 JWT。
- `orderId` 可以在同一业务重试中保持不变，但 `jti` 不能复用。

#### 10.4.4 `F402003` 无法解密请求

重点检查：

1. `data` 是否恰好为五段，段之间使用英文句点。
2. 每段是否使用无填充 Base64Url。
3. AES key 是否正好 32 字节，IV 是否 12 字节，Tag 是否 16 字节。
4. 是否错误地把 Tag 留在 `cipherText` 中又单独追加一次。
5. AES-GCM AAD 是否为第一段 Base64Url 文本的 US-ASCII 字节。
6. RSA OAEP Digest 和 MGF1 是否都使用 SHA-256。
7. 是否使用当前商户、当前环境的平台公钥。
8. 明文是否是合法 UTF-8 JSON。

#### 10.4.5 响应无法解密

- 确认使用的是商户响应私钥，不是平台私钥或请求加密公钥。
- 私钥必须为 PKCS#8 格式。
- 确认响应来自与私钥匹配的环境和商户。
- 不要重新序列化第一段受保护头；直接使用密文第一段作为 AAD。
- 检查密文是否在日志、数据库或消息传递过程中被截断或转义。

#### 10.4.6 IP 或来源网址被拒绝

- 核对实际公网出口 IP，而不是容器或内网 IP。
- 多出口、NAT、代理和容灾节点都需要提前登记。
- 不要自行设置 `X-Gateway-Client-Ip`。
- 启用来源网址规则时，在首次交易请求中稳定上送 `transactionInfo.merchantWebsite`；不要接受终端用户输入覆盖该字段。

#### 10.4.7 金额或币种被拒绝

- 币种必须为三位大写代码。
- 支付金额使用主币种单位，代付金额使用最小单位整数。
- 查询币种字典确认小数位和 `minorUnitMultiplier`。
- 后续动作币种必须与源交易一致。
- 请款、退款和预授权完成金额不能超过对应剩余可用金额。

#### 10.4.8 HTTP 200 但交易没有成功

HTTP 200 和外层 `T200` 只表示 OpenAPI 已返回业务数据。解密 `data` 后继续检查 `transactionInfo.code` 和 `transactionInfo.transactionStatus`。`T202/T203` 或 `PROCESSING/PENDING` 需要查询；只有持久化状态为 `SUCCESS` 才能按成功更新商户订单。

#### 10.4.9 Hosted Checkout 页面跳转后状态不一致

`transactionInfo.redirectUrl` 只负责结果页通过浏览器表单 POST 返回商户网站。付款人可以修改或重复提交表单，也可能关闭页面、重复刷新或因网络中断完全不返回；商户服务端必须通过交易查询或已开通的终态回调确认最终结果。

### 10.5 Java SDK 与加解密参考

Java 商户建议优先参考 [acquiring-openapi-java-sdk](https://github.com/wikerx/acquiring-openapi-java-sdk.git) 完成接入。使用 SDK 前应确认版本与本文的 `v1` 接口、JWT、RSA-OAEP-256 和 AES-256-GCM 规则兼容，并通过依赖锁定或校验和固定经过验证的版本。

以下代码用于解释协议算法和协助排查，不替代 SDK。生产代码还应补充密钥版本管理、超时、异常分类、内存敏感数据清理和安全日志策略。

SDK 的诊断日志应默认关闭或只输出长度、是否存在和不可逆摘要。生产环境不得记录完整 `Authorization`、完整请求或响应密文、解密后的业务对象、`billingCardHolderInfo/payerInfo/shippingInfo` 原文、账户号、完整 PAN、CVV、CAVV、密钥或私钥。即使 SDK 提供调试开关，商户也应在上线前通过日志采样确认这些值没有进入应用日志、APM、异常平台或链路追踪标签。

```java
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public final class OpenApiCrypto {

    private static final Base64.Encoder BASE64_URL =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER =
            Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PAYLOAD_HEADER =
            "{\"typ\":\"PAYMENT-PAYLOAD\",\"alg\":\"RSA-OAEP-256\",\"enc\":\"A256GCM\"}";

    private OpenApiCrypto() {
    }

    public static String createJwt(String merchantId,
                                   String merchantKey,
                                   String jti,
                                   long issuedAtSeconds)
            throws GeneralSecurityException {
        String header = "{\"typ\":\"JWT\",\"alg\":\"HS256\"}";
        String payload = "{\"aud\":[\"gateway\"],\"iss\":\"merchant\","
                + "\"jti\":" + quote(jti) + ","
                + "\"iat\":" + issuedAtSeconds + ","
                + "\"exp\":" + (issuedAtSeconds + 120) + ","
                + "\"merchantId\":" + quote(merchantId) + "}";
        String signingInput = base64Url(header.getBytes(StandardCharsets.UTF_8))
                + "." + base64Url(payload.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                merchantKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return signingInput + "."
                + base64Url(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
    }

    public static String encryptRequest(String plainJson, PublicKey platformPublicKey)
            throws GeneralSecurityException {
        byte[] aesKey = randomBytes(32);
        byte[] iv = randomBytes(12);
        String protectedHeader = base64Url(
                PAYLOAD_HEADER.getBytes(StandardCharsets.UTF_8));

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(aesKey, "AES"),
                new GCMParameterSpec(128, iv));
        aes.updateAAD(protectedHeader.getBytes(StandardCharsets.US_ASCII));
        byte[] cipherWithTag = aes.doFinal(
                plainJson.getBytes(StandardCharsets.UTF_8));
        byte[] cipherText = Arrays.copyOf(
                cipherWithTag, cipherWithTag.length - 16);
        byte[] tag = Arrays.copyOfRange(
                cipherWithTag, cipherWithTag.length - 16, cipherWithTag.length);

        Cipher rsa = rsaOaepCipher(Cipher.ENCRYPT_MODE, platformPublicKey);
        byte[] encryptedKey = rsa.doFinal(aesKey);
        return String.join(".",
                protectedHeader,
                base64Url(encryptedKey),
                base64Url(iv),
                base64Url(cipherText),
                base64Url(tag));
    }

    public static String decryptResponse(String compact, PrivateKey responsePrivateKey)
            throws GeneralSecurityException {
        String[] parts = compact.split("\\.", -1);
        if (parts.length != 5) {
            throw new IllegalArgumentException("encrypted data must contain five parts");
        }
        String decodedHeader = new String(
                BASE64_URL_DECODER.decode(parts[0]), StandardCharsets.UTF_8);
        if (!PAYLOAD_HEADER.equals(decodedHeader)) {
            throw new IllegalArgumentException("unsupported protected header");
        }

        Cipher rsa = rsaOaepCipher(Cipher.DECRYPT_MODE, responsePrivateKey);
        byte[] aesKey = rsa.doFinal(BASE64_URL_DECODER.decode(parts[1]));
        byte[] iv = BASE64_URL_DECODER.decode(parts[2]);
        byte[] cipherText = BASE64_URL_DECODER.decode(parts[3]);
        byte[] tag = BASE64_URL_DECODER.decode(parts[4]);
        byte[] cipherWithTag = Arrays.copyOf(
                cipherText, cipherText.length + tag.length);
        System.arraycopy(tag, 0, cipherWithTag,
                cipherText.length, tag.length);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(aesKey, "AES"),
                new GCMParameterSpec(128, iv));
        aes.updateAAD(parts[0].getBytes(StandardCharsets.US_ASCII));
        return new String(aes.doFinal(cipherWithTag), StandardCharsets.UTF_8);
    }

    public static PublicKey readPlatformPublicKey(String pem)
            throws GeneralSecurityException {
        byte[] der = Base64.getDecoder().decode(normalizePem(pem));
        return KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));
    }

    public static PrivateKey readResponsePrivateKey(String pem)
            throws GeneralSecurityException {
        byte[] der = Base64.getDecoder().decode(normalizePem(pem));
        return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static Cipher rsaOaepCipher(int mode, java.security.Key key)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        OAEPParameterSpec spec = new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT);
        cipher.init(mode, key, spec);
        return cipher;
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String base64Url(byte[] bytes) {
        return BASE64_URL.encodeToString(bytes);
    }

    private static String normalizePem(String pem) {
        return pem.replaceAll("-----BEGIN [A-Z0-9 ]+-----", "")
                .replaceAll("-----END [A-Z0-9 ]+-----", "")
                .replaceAll("\\s", "");
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }
}
```

调用示例：

```java
long now = System.currentTimeMillis() / 1000L;
String jwt = OpenApiCrypto.createJwt(
        merchantId, merchantKey, java.util.UUID.randomUUID().toString(), now);
PublicKey platformPublicKey = OpenApiCrypto.readPlatformPublicKey(platformPublicKeyPem);
String encryptedData = OpenApiCrypto.encryptRequest(plainBusinessJson, platformPublicKey);

// HTTP body: {"data":"..."}
// Authorization: Bearer {jwt}

PrivateKey responsePrivateKey = OpenApiCrypto.readResponsePrivateKey(responsePrivateKeyPem);
String responsePlainJson = OpenApiCrypto.decryptResponse(
        encryptedResponseData, responsePrivateKey);
```

参考代码中的 `decryptResponse` 按平台当前固定 Header 文本校验。其他语言实现可以解析 Header JSON 后校验三个字段，但仍必须使用原始第一段文本作为 AAD。

### 10.6 上线检查清单

**安全**

- [ ] Production 使用 HTTPS，证书主机名和信任链校验已开启。
- [ ] `merchantKey`、商户响应私钥只存在于服务端安全存储。
- [ ] 平台公钥来源和指纹已通过受控渠道核对。
- [ ] JWT 有效期不超过 180 秒，服务器已启用 NTP。
- [ ] 每次 HTTP 尝试生成新的 `jti`。
- [ ] AES key 和 IV 每次请求随机生成且不复用。
- [ ] RSA OAEP Digest 和 MGF1 均明确使用 SHA-256。
- [ ] 日志不包含 JWT、密钥、完整卡号、CVV、账户号、完整请求明文或完整密文。
- [ ] 商户出口 IP 和容灾出口 IP 已登记。

**业务**

- [ ] 商户保存 `orderNo`、`orderId`、`transactionId`、`sourceTransactionId` 和 `transactionDateTime` 的对应关系。
- [ ] 网络结果不确定时先查询，不通过换号重复发起资金动作。
- [ ] 支付结果同时判断外层 `code`、内层 `transactionInfo.code` 和 `transactionInfo.transactionStatus`。
- [ ] `T202/T203` 已接入查询轮询和人工兜底。
- [ ] 金额使用十进制或最小单位整数，不使用浮点数。
- [ ] 代付金额按币种字典换算，不固定乘以 100。
- [ ] 请款、退款和增量授权已按源交易状态及剩余可用金额控制。
- [ ] 撤销请求不传金额和币种，平台只执行当前全部可撤销金额的全额撤销。
- [ ] Hosted Checkout 未提供 `transactionInfo.redirectUrl` 时永久停留平台结果页；提供时 5 秒倒计时和“返回商户网站”按钮均提交同一份 POST Form。
- [ ] Hosted Checkout POST Form 接收端允许重复提交，且不把表单中的 `code` 或 `transactionStatus` 作为资金成功依据。
- [ ] Hosted Checkout 创建会话资料已自动预填且保持可编辑，页面提交使用付款人修改后的最终 `billingCardHolderInfo`。
- [ ] 创建会话时的 `payerInfo` 与页面最终 `billingCardHolderInfo` 分别保存，不互相覆盖。
- [ ] 支付、授权、预授权和 Hosted Checkout 创建请求均包含 `payerInfo.ipAddress`；其他 `payerInfo` 子字段缺失时仍可按契约受理。
- [ ] `payerInfo.ipAddress` 使用付款人公网 IP，不误用商户服务器调用 OpenAPI 时的出口 IP。
- [ ] Hosted Checkout 创建请求不包含支付方式、卡品牌、支付渠道、3DS 策略、会话有效期或页面重试次数选择字段。
- [ ] 回调 Controller 只接受 POST，并校验 JWT、全部必填 Header、密文和 `eventId` 唯一键。
- [ ] 本地业务事务提交成功后才返回精确的 `HTTP 200 + succeed`。
- [ ] 同一 `eventId` 重复投递不会重复更新商户订单，不同人工重发事件可独立审计。

**联调**

- [ ] 正确 JWT、过期 JWT、错误签名和重复 `jti` 均已验证。
- [ ] 正确密文、错误公钥、篡改密文、错误 AAD 和错误 Tag 均已验证。
- [ ] 成功响应可使用商户响应私钥解密。
- [ ] 参数缺失、金额边界、币种精度和幂等冲突均已验证。
- [ ] 支付成功、拒绝、处理中、超时后查询、部分退款和重复请求均已验证。
- [ ] Hosted Checkout 已验证 `billingCardHolderInfo` 整对象优先、未提供时使用 `payerInfo` 同名字段预填、缺失资料由付款人补充、页面修改和最终快照返回。
- [ ] 查询和终态回调已验证商品及三类人员快照原样返回，且不包含 `cardInfo`、`riskInfo`、CAVV 或渠道原文。
- [ ] Sandbox 测试数据未进入 Production 配置。

### 10.7 文档版本记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| `v1.0` | 2026-08-14 | 统一 Header、安全、幂等、子商户、金额和汇率规则；将 7.2 至 7.10 及 9.1 重写为逐接口完整请求/响应契约及密文/明文示例；补齐 6.1 至 6.4 加密响应外层参数和终态回调完整响应口径；明确撤销不传金额币种且仅支持全额撤销；后续动作请求不再接收 `sourceTransactionDateTime`；新增可选 `goodsInfo` 商品快照，按主参数/子参数重排字段表；明确 `billingCardHolderInfo/payerInfo/shippingInfo` 的风控用途、`payerInfo.ipAddress` 必传规则及 Hosted Checkout 预填优先级；重写 Hosted Checkout 参数、预填规则及 5 秒倒计时 POST Form 返回协议；将 `riskInfo` 标记为当前预留对象；将代付标记为规划中 |

### 10.8 交易类型

| **枚举值**                | **中文说明** | **英文说明**                 |
| ------------------------- | ------------ | ---------------------------- |
| AUTHORIZATION             | 授权         | Authorization                |
| CAPTURE                   | 请款         | Capture                      |
| PAYMENT                   | 支付         | Payment                      |
| PRE_AUTHORIZATION         | 预授权       | Pre-authorization            |
| PRE_AUTH_COMPLETION       | 预授权完成   | Pre-authorization Completion |
| INCREMENTAL_AUTHORIZATION | 增量授权     | Incremental Authorization    |
| REFUND                    | 退款         | Refund                       |
| VOID                      | 撤销         | Void                         |
| REVERSAL                  | 冲正         | Reversal                     |
| CHARGEBACK                | 拒付         | Chargeback                   |
| REPRESENTMENT             | 拒付再请款   | Representment                |
| RETRIEVAL_REQUEST         | 调单请求     | Retrieval Request            |
| QUERY                     | 查询         | Query                        |
