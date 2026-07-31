# OpenAPI 商户接入文档

## 1. 文档说明

### 1.1 文档版本记录

| 版本 | 日期 | 作者 | 说明 |
| --- | --- | --- | --- |
| v1.3.5 | 2026-07-29 | scott | 明确商户响应私钥必须外置注入，禁止进入 classpath、SDK/JAR、镜像应用层或代码仓库 |
| v1.3.4 | 2026-07-29 | scott | Java SDK 增加固定 Origin/Referer 配置，确保来源网址风控规则可直接用于服务端接入 |
| v1.3.3 | 2026-07-29 | scott | 补充首次卡交易 riskContext 参数、风控用途、请求示例和缺失字段处理规则 |
| v1.3.2 | 2026-07-29 | scott | 补充客户端 IP 与来源网址传递规则、逐接口风控范围及风险拒绝处理顺序 |
| v1.3.1 | 2026-07-29 | scott | 明确演示密钥边界、内风控 F210 文案和异步受理状态码 |
| v1.3.0 | 2026-07-29 | scott | 对齐当前实现补齐全部公开 API、预授权完成、交易风控边界、逐接口请求规则、代付响应、密钥托管说明和完整错误码 |
| v1.2.4 | 2026-07-27 | scott | 补充 Hosted Checkout 收银台创建接口、SDK 调用示例和收银台前端地址配置来源 |
| v1.2.3 | 2026-07-17 | scott | 明确退款最小请求参数、可选币种和商户订单号校验规则，以及退款响应累计金额口径 |
| v1.2.2 | 2026-07-14 | scott | 补充 MPGS 渠道订单映射、渠道回调终态推进和商户异步通知说明 |
| v1.2.1 | 2026-07-14 | scott | 明确收单交易订单标识、MPGS 订单号映射和后续动作按本次交易时间分表规则 |
| v1.2.0 | 2026-07-14 | scott | 补充收单交易 V1 接口清单，移除早期 V2 测试入口说明，说明 transaction_id 与 transaction_date_time 后续动作定位规则 |
| v1.1.0 | 2026-06-12 | scott | 拆分 ISO 对外 API 控制器，补充响应模型和 HTTP Method 规范 |
| v1.0.0 | 2026-06-03 | scott | 创建商户 OpenAPI 对接文档，包含鉴权、加密、响应解密、ISO 国家地区和币种查询接口 |

### 1.2 适用范围

本文档适用于商户系统通过 OpenAPI 接入支付框架的服务端接口。当前已开放接口：

| 接口分类 | 接口名称 | 接口版本 |
| --- | --- | --- |
| ISO 字典 | 查询国家地区列表 | v1 |
| ISO 字典 | 查询币种列表 | v1 |
| 支付 | 一步支付、授权、预授权、增量授权、预授权完成、请款、退款、撤销、查询 | v1 |
| Hosted Checkout | 创建收银台会话 | v1 |
| 代付 | 创建代付 | v1 |

后续支付、退款、代付、回调等接口均在本文档上继续追加。

### 1.3 对接角色说明

| 角色 | 说明 |
| --- | --- |
| 商户系统 | 调用 OpenAPI 的外部业务系统 |
| 支付平台 | 接收商户请求、完成鉴权、解密、业务处理和响应加密的平台 |

### 1.4 参数必填标识

本文档请求参数表中，“必填”列使用以下标识：

| 标识 | 英文 | 含义 |
| --- | --- | --- |
| M | Mandatory | 必传参数 |
| O | Optional | 可选参数 |
| C | Conditional | 条件必传参数，满足指定业务条件时必须传 |

### 1.5 传输说明

1. 所有接口必须使用 HTTPS。
2. 当前商户对外 OpenAPI 统一使用 `POST`，以便统一承载鉴权、请求体加密和版本兼容规则。
3. 查询接口的业务查询条件加密后放入 JSON 请求体外层 `data` 字段。
4. 请求和响应字符集统一为 UTF-8。
5. 请求头 `Content-Type` 固定为 `application/json`。
6. 请求头 `authorization` 必须使用 `Bearer ` 加一个空格后拼接 JWT。
7. `POST` 查询、交易或变更接口的业务参数必须加密后放入 JSON 请求体外层 `data` 字段。
8. 成功响应中的业务数据同样会加密后放入外层 `data` 字段。

### 1.6 时间说明

JWT 中的 `iat` 和 `exp` 使用 Unix epoch 秒，即从 UTC 1970-01-01 00:00:00 开始计算的秒数。该时间戳不携带时区，商户系统部署在中国大陆、香港、新加坡、欧洲或其他地区都使用同一套 epoch 秒。

商户需要注意：

1. 服务器必须做 NTP 时间同步。
2. `exp - iat` 不能超过 180 秒。
3. 网络延迟较高时，建议 JWT 有效期设置为 120 到 180 秒。
4. 如果商户机器时间明显快于或慢于标准时间，平台会拒绝请求。

## 2. 接入环境

| 环境 | Base URL | 说明 |
| --- | --- | --- |
| dev | `https://dev-api.example.com` | 开发联调环境，实际地址以平台分配为准 |
| test | `https://test-api.example.com` | 测试环境，实际地址以平台分配为准 |
| uat | `https://uat-api.example.com` | 预发环境，实际地址以平台分配为准 |
| prod | `https://api.example.com` | 生产环境，实际地址以平台分配为准 |

请求地址拼接规则：

```text
{Base URL}{接口 Path}
```

示例：

```text
https://api.example.com/api/rest/iso/v1/currencies/query
```

## 3. 商户密钥说明

### 3.1 商户需要保存的密钥

| 名称 | 必须保存 | 是否保密 | 商户用途 | 使用阶段 |
| --- | --- | --- | --- | --- |
| `merchantId` | M | 否 | 标识商户身份 | JWT Payload |
| `merchantKey` | M | 是 | 生成 JWT HS256 签名 | 请求头鉴权 |
| 平台公钥 | M | 否，但必须校验来源 | 加密一次性 AES-256-GCM 会话密钥 | 请求体加密 |
| 商户响应私钥 | M | 是 | 解密平台响应 `data` | 响应体解密 |

重要说明：

1. 平台公钥不是保密密钥，但必须通过平台官方渠道获取，并建议保存指纹用于校验。
2. 平台公钥不直接加密整个业务 JSON。业务 JSON 使用随机 AES-256-GCM 会话密钥加密，平台公钥只加密这个 AES 会话密钥。
3. `merchantKey` 只用于 JWT HS256 签名，不用于请求体加密。
4. 当前平台会为商户生成并托管响应密钥对，同时支持商户在管理端复制或下载响应私钥。商户下载后必须自行安全保存，严禁通过聊天、邮件正文、代码仓库或日志传递。
5. 平台托管私钥属于当前实现的运维能力，不代表商户可以忽略私钥保护。生产环境应对私钥导出实施双人复核、操作审计和最小权限控制，并规划迁移至 KMS/HSM。

### 3.2 平台保存的密钥

| 名称 | 平台用途 | 是否下发商户 |
| --- | --- | --- |
| `merchantKey` | 根据 `merchantId` 查询后校验 JWT 签名 | 是 |
| 平台私钥 | 解密商户请求体中的 AES 会话密钥 | 否 |
| 商户响应公钥 | 加密平台响应 `data` | 平台生成并保存；随接入材料向商户提供对应私钥 |
| 商户响应私钥 | 供管理端受控复制或下载，便于商户解密响应 | 是；仅通过受控渠道下发 |

### 3.3 报文格式演示密钥

以下密钥仅用于本地加解密示例和本文档报文对照，生产环境严禁使用。示例商户号 `260001`
不是当前 dev 环境已开通商户，不能直接调用联调地址；可调用的 Sandbox 商户号、`merchantKey`、平台公钥和商户响应私钥必须以平台单独下发的接入材料为准。

| 名称 | 值 |
| --- | --- |
| `merchantId` | `260001` |
| `merchantKey` | `h6pKuPIVhtWJMRi5K9XZbPII63QoPpZgfZrbEuXU9c4` |
| 平台公钥 X.509 Base64 | `MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtsf3Qe+9efrYU2NqcauP/wOOyjYVoBnmYRmIdgD3Rhtt7BwHMLZaPfxu72qvWZitAPfSDapnxXdXO7+gDz2/ZHMkzyqFGkXVCJZ3C4wtbsvsoZbt0eH5ZOdIAIFgJMEtzMnD6rzUqxSUm/9xsHSTLnJewq+VClrIYY02VJpIGZ8+6hIfkQA8EBDtKD3HIdRvo+33qobrTsKDDUOvKiP63yb6Qamiu54xegccNvhvnIa3LVXk7SgnBb5L8TJPEEsXcYrKnF3MiXk7eSfc1lhmVOfqayPACA93yMka23kZWaozEgvV3hxrqCML3OldH4UkziCF/ToUiOFMJbvKxhQVOQIDAQAB` |
| 商户响应私钥 PKCS#8 Base64 | `MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDN/tkJANLDjZ/BbdaGRNfHuYYotukFzNbsZDHhq7kNu+ylNfzTuvBMolgVhFJQimmzonrkQm76COGe4jV1KkyWfMIH60ivexiQIAmR85fKfNxqIRjnHxO8pZZs9z9JLARfWph6rCYkDTk1i06Dl3kVsOMb+L/XgObtJZPZPbwXt3GqrWf45WbiSibAatpKMpQAWlWg4/uH5yrI3oNdeDkGdO3NXaeRK869TrNiDT+OI63WIZXXTRQDQfbXWRyBKlm3+OG7Ft+eLtLzwZtsuxX8llPevLE2mshKAw3wF55Vun6ICe7+EYCWGDt88tU/79E7E0WHsPEEZYGMfbnuvGgLAgMBAAECggEAIoxk+yETuC93BTp2OcOvCvS/HvH6Z/oga7osMYSa/0Yu3NCOrDYUmk26BzXPlml4a+PKx6Cquy2lJYAb5iAngy++XRSldqTnDDkLUdqwcQn676PIaO7p4QBGl9Tp3MxQmWt42k4oAXDkUOohy2kqqiwmEulnx217jXd5cfxsIO/atGHXm9Zh0Yq4jCYCXYikCylnKBFnsq+dU6F/jWYr1gs76o5Jgg7WQcvg4W1odGHPWqoEtlUTAZg25/bYXjYTnF855DksD1vSqzIAnsKFVXgmONZlMdnaT0LH+Kl5QEoTFZD7SByWx557RuhoVhtu/P4EHBcKksGMO2loKvOA/QKBgQDxhnQoQdZDmBpvOYqNuIvDLm9up6tGn6ff6s8/QPPItj2L4svLdGVfTcAzg+8BnbDyO9SFifwHe0zeuwyflKjmQfRABddDffK+yzzeE019zwGJqNd2Jo6cZ2njvSM6M7hCzJ5EU84c6hZIv//Lis2KMbxi8hUbd6JqMMqmUAL5NwKBgQDaV0mWaR3vezlea2pC6fhL54haDb08gPISXru6x6/zs/UNKo5pSvDjDzkZlE2YQQEvXrD+02gBdsxzrIJUEhMsb+h3kIpysOVm1AoAd5MdFjiT07XtLzWdEFPYouEnr/JLNJzBYg2pAOhpjuwvTGafNIsoo/PJrSIkFmgINfthzQKBgQCj7F1p9UU3G0TVuHgRN++jySBYOfRFOpb1oqiGhc7vqsCa8JLgw18KD/si+6h7sEsoHPNgrwYfDdBeWxV2Oa9ol9rumQhBBnp6g/YLw44UlSq2A6I4znJ8NLPpnbULC49Dxxyjwz1g4n+9YJJ70vktkhQKE8O/oLLa38KqniNmgQKBgQCmG9AlORWIM0Qi/C9cdunqvVvzvw4f8K25og7Ke8715gvhl2W+3z/CTruPJU+fLJ09L5oSVD2FF59VxYFlelbR8NV32SQrOz9baqetUUs/zr7+YAvBRbBRLLHNV6VZ7zazVnSHfxSLZeBrJkuzdDmCl5PjOFBpN2mI8O72iDMWZQKBgQCllpyogwEYVF4TkDqK8cuRsehdfzFU/R0mQycy3kk2WI9ovpOcmeO9g3vFkd9woJmTXPH7EmB1f/Zr1+OPSgYBGShMXY6BsSKV3H9VOJ8F9hWH11/kDH7cUwwDShsDzUvE2oxnfTt5qQAuWftNb3MsbKperFD4NXo8ewWsbairAQ==` |

## 4. 密钥使用时序图

### 4.1 商户请求和平台响应完整流程

```mermaid
sequenceDiagram
    participant M as 商户系统
    participant P as 支付平台
    participant DB as 商户密钥库

    M->>M: 使用 merchantKey 生成 JWT HS256
    M->>M: 随机生成 AES-256 key 和 12字节 IV
    M->>M: 使用 AES-256-GCM 加密业务 JSON
    M->>M: 使用平台公钥 RSA-OAEP-SHA256 加密 AES key
    M->>P: POST OpenAPI，Header携带Bearer JWT，参数或Body携带data密文
    P->>DB: 根据JWT中的merchantId查询merchantKey
    P->>P: 使用merchantKey验签JWT并校验iat/exp/aud/iss/jti
    P->>DB: 根据merchantId查询平台私钥
    P->>P: 使用平台私钥解密AES key
    P->>P: 使用AES key和IV解密业务JSON
    P->>P: 执行业务处理
    P->>DB: 根据merchantId查询商户响应公钥
    P->>P: 使用商户响应公钥加密响应data
    P->>M: 返回code、message和加密data
    M->>M: 使用商户响应私钥解密响应data
```

### 4.2 商户侧密钥使用说明

| 步骤 | 商户动作 | 使用密钥 |
| --- | --- | --- |
| 1 | 生成 JWT 请求头 | `merchantKey` |
| 2 | 生成 AES-256-GCM 随机会话密钥 | 不使用长期密钥 |
| 3 | 加密业务 JSON | AES 随机会话密钥 |
| 4 | 加密 AES 随机会话密钥 | 平台公钥 |
| 5 | 解密平台响应 `data` | 商户响应私钥 |

### 4.3 平台侧密钥使用说明

| 步骤 | 平台动作 | 使用密钥 |
| --- | --- | --- |
| 1 | 验证 JWT 签名 | `merchantKey` |
| 2 | 解密请求中的 AES 会话密钥 | 平台私钥 |
| 3 | 解密业务 JSON | AES 会话密钥 |
| 4 | 加密响应业务 JSON | 新生成的 AES 会话密钥 |
| 5 | 加密响应 AES 会话密钥 | 商户响应公钥 |

## 5. 公共请求头

### 5.1 请求头参数

| Header | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `Content-Type` | M | `application/json` | 固定值 |
| `authorization` | M | `Bearer eyJ0eXAiOiJKV1Qi...` | `Bearer` 加一个空格后拼接 JWT |

### 5.2 请求头示例

```http
Content-Type: application/json
authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOlsiZ2F0ZXdheSJdLCJpc3MiOiJtZXJjaGFudCIsImp0aSI6IjIwMjYwNjAyMDAwMSIsImlhdCI6MTc4MDQ5MjQwNiwiZXhwIjoxNzgwNDkyNTg2LCJtZXJjaGFudElkIjoiMjYwMDAxIn0.1jWLOq2Ni46cABeMrmxKFz87JgSyCELPmamlmE6-Ue0
```

### 5.3 客户端 IP 与来源网址

平台会使用网关确认的客户端出口 IP 执行商户 IP 白名单和交易风控。商户只需要向平台提供调用服务器、NAT 网关或固定代理的公网出口 IP，由平台管理员配置白名单；商户请求不得自行添加或伪造 `X-Gateway-Client-Ip`。该请求头由平台受信网关在清理外部同名头后写入，生产环境的 `service-openapi` 不得绕过网关直接暴露。

一步支付、授权和预授权还会读取以下标准 HTTP 来源头，用于“商户来源网址限定”和 AML 来源网址规则：

| Header | 必填 | 说明 |
| --- | --- | --- |
| `Origin` | C | 优先使用。启用商户来源网址允许清单后必须提供，值为标准来源，例如 `https://merchant.example.com` |
| `Referer` | C | `Origin` 为空时的兼容来源，可包含路径；平台按规范化后的 host 匹配 |

来源网址规则的业务约束：

1. 未启用来源网址规则时，`Origin/Referer` 可不传。
2. 启用来源网址规则后，支付、授权、预授权必须至少传一个；两者同时存在时以 `Origin` 为准。
3. 平台按 URL 的 host 匹配允许清单，不按请求体中的回调地址匹配。
4. 命中允许清单表示该节点通过；未命中或缺失来源信息时交易被内风控拒绝，返回 `F210 / Risk blocked`。
5. `Origin/Referer` 不参与 JWT 签名字段，但会进入风控请求摘要。商户应在自己的服务端 HTTP 客户端中固定配置，不要接受终端用户任意覆盖。

## 6. JWT 鉴权规则

### 6.1 JWT 算法

| 项 | 规则 |
| --- | --- |
| Token 类型 | JWT |
| 签名算法 | HS256 |
| 签名密钥 | `merchantKey` |
| 请求头格式 | `Bearer {jwt}` |
| 最大有效期 | 180 秒 |

### 6.2 JWT Header

```json
{
  "typ": "JWT",
  "alg": "HS256"
}
```

### 6.3 JWT Payload

```json
{
  "aud": [
    "gateway"
  ],
  "iss": "merchant",
  "jti": "202606020001",
  "iat": 1780492406,
  "exp": 1780492586,
  "merchantId": "260001"
}
```

### 6.4 JWT Payload 参数说明

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| `aud` | array | M | `["gateway"]` | 接收方，必须包含 `gateway` |
| `iss` | string | M | `merchant` | 签发方，固定为 `merchant` |
| `jti` | string | M | `202606020001` | 请求唯一标识，建议使用商户订单号或请求流水号 |
| `iat` | integer | M | `1780492406` | 秒级 Unix epoch 签发时间 |
| `exp` | integer | M | `1780492586` | 秒级 Unix epoch 过期时间，必须大于 `iat` |
| `merchantId` | string | M | `260001` | 平台颁发的商户号 |

### 6.5 JWT 完整示例和拆分

请求头完整值：

```text
authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOlsiZ2F0ZXdheSJdLCJpc3MiOiJtZXJjaGFudCIsImp0aSI6IjIwMjYwNjAyMDAwMSIsImlhdCI6MTc4MDQ5MjQwNiwiZXhwIjoxNzgwNDkyNTg2LCJtZXJjaGFudElkIjoiMjYwMDAxIn0.1jWLOq2Ni46cABeMrmxKFz87JgSyCELPmamlmE6-Ue0
```

拆分结果：

| 段位 | 内容 |
| --- | --- |
| Header | `eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9` |
| Payload | `eyJhdWQiOlsiZ2F0ZXdheSJdLCJpc3MiOiJtZXJjaGFudCIsImp0aSI6IjIwMjYwNjAyMDAwMSIsImlhdCI6MTc4MDQ5MjQwNiwiZXhwIjoxNzgwNDkyNTg2LCJtZXJjaGFudElkIjoiMjYwMDAxIn0` |
| Signature | `1jWLOq2Ni46cABeMrmxKFz87JgSyCELPmamlmE6-Ue0` |

解码后的 Header 和 Payload 与 6.2、6.3 一致。商户可使用 `merchantKey` 对该 JWT 做 HS256 验签自测。

### 6.6 平台校验逻辑

平台收到请求后按以下顺序校验：

1. 校验 `authorization` 请求头是否存在。
2. 校验请求头是否符合 `Bearer {jwt}` 格式。
3. 解析 JWT Header，校验 `typ=JWT`、`alg=HS256`。
4. 解析 JWT Payload，读取 `merchantId`。
5. 根据 `merchantId` 查询商户状态和 `merchantKey`。
6. 使用 `merchantKey` 校验 JWT 签名。
7. 校验 `aud`、`iss`、`jti`、`iat`、`exp`。
8. 校验 `exp - iat <= 180` 秒。
9. 使用 `jti` 做防重放控制。

## 7. 请求体加密规则

### 7.1 加密算法

请求体采用混合加密：

```text
RSA-OAEP-SHA256 + AES-256-GCM
```

| 项 | 规则 |
| --- | --- |
| 业务 JSON 加密 | AES-256-GCM |
| AES key 长度 | 32 字节，即 256 bit |
| IV 长度 | 12 字节 |
| GCM Tag 长度 | 16 字节 |
| AES key 加密 | RSA-OAEP-SHA256 |
| RSA 公钥 | 平台公钥 |
| AAD | 第一段 `protectedHeader` 的 Base64Url 字符串 |
| 编码 | Base64Url，无补位 |

### 7.2 外层请求体

商户正式接入统一使用以下 JSON 外层：

```json
{
  "data": "<compact encrypted payload>"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `data` | string | M | 五段式加密报文 |

平台当前兼容直接提交 compact 密文的本地测试形态，但该形态不属于商户对外协议。商户系统和 SDK 必须使用 `{"data":"..."}`，以避免网关、WAF、审计和后续协议扩展出现兼容问题。

### 7.3 五段式密文格式

`data` 使用以下格式：

```text
base64url(protectedHeader).base64url(encryptedKey).base64url(iv).base64url(cipherText).base64url(tag)
```

五段说明：

| 段位 | 名称 | 说明 |
| --- | --- | --- |
| 1 | `protectedHeader` | 加密头，JSON 后 Base64Url 编码 |
| 2 | `encryptedKey` | 使用平台公钥 RSA-OAEP-SHA256 加密后的 AES key |
| 3 | `iv` | AES-GCM IV，12 字节随机数 |
| 4 | `cipherText` | AES-GCM 加密后的业务 JSON 密文 |
| 5 | `tag` | AES-GCM 认证标签，16 字节 |

`protectedHeader` 明文内容：

```json
{
  "typ": "PAYMENT-PAYLOAD",
  "alg": "RSA-OAEP-256",
  "enc": "A256GCM"
}
```

`protectedHeader` Base64Url 后为：

```text
eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0
```

### 7.4 请求加密示例

本示例使用 3.3 中的测试商户和平台公钥。

加密前的明文业务 JSON：

```json
{
  "alphabeticCode": "USD"
}
```

五段式拆分示例：

| 段位 | 值 |
| --- | --- |
| `protectedHeader` | `eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0` |
| `encryptedKey` | `jV7MI29_2bNaurjpW_TNzvPEx-9oJPBIdQkY7aUtsfTP_bE_aHTdO3vpoccunbY6OLdgQCSjBVlQd1DhoVUiv94xBiyyudT7amQiFXNjNsizT1tIBwpzBaOxQmn0HaS0_5-zqZXOW7eQb9UXA4SrfI5yY1MQhjXt7RowAvrXvHlDXkAVreCqU0Vfn9VkIQj5lHy_5OolmyXl2YuBCEx4qGAgWK-5_Qj9_b78gmDyKx6kHMQNH6xm9AO1IctHKb-WZqZ8w7LF8QMoMT8PWQX1nwDUJ4AMKe-U82zFrIOry9mV65TSzPvasW-T1tLOzAn0MoniG4e-5bt35AGm2MkqOA` |
| `iv` | `WCCJIlJMwRR97wtV` |
| `cipherText` | `D2m2Uy4w42ijUm_uW80UibqXQMLtJHt8` |
| `tag` | `lgSRS1mYlk-Z6nX7X4VUZQ` |

最终 `data`：

```text
eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.jV7MI29_2bNaurjpW_TNzvPEx-9oJPBIdQkY7aUtsfTP_bE_aHTdO3vpoccunbY6OLdgQCSjBVlQd1DhoVUiv94xBiyyudT7amQiFXNjNsizT1tIBwpzBaOxQmn0HaS0_5-zqZXOW7eQb9UXA4SrfI5yY1MQhjXt7RowAvrXvHlDXkAVreCqU0Vfn9VkIQj5lHy_5OolmyXl2YuBCEx4qGAgWK-5_Qj9_b78gmDyKx6kHMQNH6xm9AO1IctHKb-WZqZ8w7LF8QMoMT8PWQX1nwDUJ4AMKe-U82zFrIOry9mV65TSzPvasW-T1tLOzAn0MoniG4e-5bt35AGm2MkqOA.WCCJIlJMwRR97wtV.D2m2Uy4w42ijUm_uW80UibqXQMLtJHt8.lgSRS1mYlk-Z6nX7X4VUZQ
```

实际 HTTP 请求体：

```json
{
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.jV7MI29_2bNaurjpW_TNzvPEx-9oJPBIdQkY7aUtsfTP_bE_aHTdO3vpoccunbY6OLdgQCSjBVlQd1DhoVUiv94xBiyyudT7amQiFXNjNsizT1tIBwpzBaOxQmn0HaS0_5-zqZXOW7eQb9UXA4SrfI5yY1MQhjXt7RowAvrXvHlDXkAVreCqU0Vfn9VkIQj5lHy_5OolmyXl2YuBCEx4qGAgWK-5_Qj9_b78gmDyKx6kHMQNH6xm9AO1IctHKb-WZqZ8w7LF8QMoMT8PWQX1nwDUJ4AMKe-U82zFrIOry9mV65TSzPvasW-T1tLOzAn0MoniG4e-5bt35AGm2MkqOA.WCCJIlJMwRR97wtV.D2m2Uy4w42ijUm_uW80UibqXQMLtJHt8.lgSRS1mYlk-Z6nX7X4VUZQ"
}
```

说明：AES key 和 IV 每次请求必须重新生成，所以商户自己加密同一段 JSON 时，最终 `data` 每次都应该不同。

## 8. 公共响应规则

### 8.1 响应结构

```json
{
  "code": "T200",
  "message": "Success",
  "data": "<compact encrypted response>"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `code` | string | M | 业务响应码。`T` 开头表示成功、已受理或处理中；`F` 开头表示商户可感知失败；`Z` 开头表示报文或协议处理异常 |
| `message` | string | M | 稳定的英文响应描述，便于商户系统落库和排查 |
| `data` | string | O | 加密后的业务响应数据。失败且无业务数据时可为空 |

平台实现约束：

1. 商户 OpenAPI 业务接口统一返回 `CommonResult`，使用 `T/F/Z` 业务响应码。
2. 有业务响应数据时必须返回 `success(data)`，平台会在响应写出前统一加密 `data`。
3. 只有无业务数据的操作成功响应才能返回 `success()`。
4. `ApiResult` 不用于商户 OpenAPI 业务接口，只用于健康检查、简单 ACK 等非业务响应。

### 8.2 支付业务状态码语义

支付、退款、代付等交易类接口后续会使用以下稳定语义：

| code | message | 交易含义 | 商户建议处理 |
| --- | --- | --- | --- |
| `T200` | `Success` | 交易已成功完成 | 按成功处理 |
| `T201` | `Accepted` | 请求已受理，但未产生最终交易结果 | 等待回调或主动查询 |
| `T202` | `Processing` | 交易正在处理中 | 不要重复发起同一笔交易，等待回调或查询 |
| `T203` | `Pending` | 交易结果暂不确定，可能需要上游确认 | 等待回调或查询 |
| `T206` | `Partially accepted` | 批量或组合类请求部分受理 | 逐条读取业务明细 |
| `F207` | `Issuer or acquirer rejected the transaction` | 发卡行、收单行、卡组织或上游拒绝 | 按失败处理，可展示上游拒绝原因 |
| `F210` | `The transaction was declined; please contact your card issuer or try again.` | 平台规则、内风控或渠道规则拒绝；该文案是公共枚举默认值，交易 `data.transactionInfo.message` 可返回场景化商户文案 | 按失败处理；不要用相同 `orderId` 重复提交 |

说明：错误码完整定义见本文档最后一章“错误码”。

### 8.3 响应加密规则

| 项 | 规则 |
| --- | --- |
| 业务响应 JSON 加密 | AES-256-GCM |
| AES key 加密 | RSA-OAEP-SHA256 |
| RSA 公钥 | 商户响应公钥 |
| 解密方 | 商户使用商户响应私钥解密 |
| 外层字段 | `code`、`message` 明文，`data` 密文 |

### 8.4 响应加密示例

平台处理成功后的明文业务响应 JSON：

```json
[
  {
    "alphabeticCode": "USD",
    "numericCode": "840",
    "englishName": "US Dollar",
    "chineseName": "美元",
    "defaultFractionDigits": 2,
    "minorUnitMultiplier": 100,
    "minimumAmount": 0.010000,
    "currencySymbol": "$"
  }
]
```

平台使用商户响应公钥加密后，五段式拆分示例：

| 段位 | 值 |
| --- | --- |
| `protectedHeader` | `eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0` |
| `encryptedKey` | `cunn9EPtl9Iz2dC7Z6F8p7b9PGn_bpbhp1dulMXjx4zuSDGTILMxZjByzh7aqcHfj3PyRvqCQZCCTqIvTK6bzP6aTwFs8N9T8SE6ebqI4Z1a650E4uOPD5glFPHOZPLgW6ISmoWZVUO-jYQZwzYxqKd355-8DyFWrbeIFpNx5TOL_H_NgRL4oKqTzIq4APYYeb4qpH9IED2mnQRtFkG3GXuRc_WXyGISAaaUnTwVuvaNwXE_w1nQftHtNgDBzmVJu3e8FAT1_-BU0S_CYeugZgLuv9yEl8AAmqsjw8ghzlamrsXM30WjituYyrD1FW5t1WLHC2l5RVgBRXMPfCeJeQ` |
| `iv` | `QO5cT-t0iHSaKrKP` |
| `cipherText` | `zq6eNoYA9VfkoMcYBr-Y4EktFuEUrj_fdKocliqIiJTB0WiphyTujLXLQUBCafwqc6UOWdkDPLIO6snmBcQQRckkgMotpAP4ruwvzdYBhLPBiyqnDZ4YPkX4rBeVHR-tived3zEEupUcKIvCW3mJWLXIluPQFoJSy0NG-5J_Q55MM_gBxj9O4xNeenphmWOldQzQ8YbLxPLOEsnNhqbd8VItcS4SzyNIpYd-REmXWYwkXn7GExXa_yG-zu0o8UXdgw` |
| `tag` | `Ul7-0Xc16hET61xomUt9YQ` |

平台最终响应：

```json
{
  "code": "T200",
  "message": "Success",
  "data": "eyJ0eXAiOiJQQVlNRU5ULVBBWUxPQUQiLCJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIn0.cunn9EPtl9Iz2dC7Z6F8p7b9PGn_bpbhp1dulMXjx4zuSDGTILMxZjByzh7aqcHfj3PyRvqCQZCCTqIvTK6bzP6aTwFs8N9T8SE6ebqI4Z1a650E4uOPD5glFPHOZPLgW6ISmoWZVUO-jYQZwzYxqKd355-8DyFWrbeIFpNx5TOL_H_NgRL4oKqTzIq4APYYeb4qpH9IED2mnQRtFkG3GXuRc_WXyGISAaaUnTwVuvaNwXE_w1nQftHtNgDBzmVJu3e8FAT1_-BU0S_CYeugZgLuv9yEl8AAmqsjw8ghzlamrsXM30WjituYyrD1FW5t1WLHC2l5RVgBRXMPfCeJeQ.QO5cT-t0iHSaKrKP.zq6eNoYA9VfkoMcYBr-Y4EktFuEUrj_fdKocliqIiJTB0WiphyTujLXLQUBCafwqc6UOWdkDPLIO6snmBcQQRckkgMotpAP4ruwvzdYBhLPBiyqnDZ4YPkX4rBeVHR-tived3zEEupUcKIvCW3mJWLXIluPQFoJSy0NG-5J_Q55MM_gBxj9O4xNeenphmWOldQzQ8YbLxPLOEsnNhqbd8VItcS4SzyNIpYd-REmXWYwkXn7GExXa_yG-zu0o8UXdgw.Ul7-0Xc16hET61xomUt9YQ"
}
```

商户处理响应时：

1. 先判断外层 `code`。`F` 或 `Z` 开头且没有 `data` 时，直接按明文 `code/message` 处理。
2. 外层存在 `data` 时，取出并解密；交易接口还必须读取解密后 `transactionInfo.code/message` 判断资金动作结果。
3. 使用商户响应私钥解密第二段 `encryptedKey` 得到 AES key。
4. 使用 AES key、第三段 `iv`、第四段 `cipherText`、第五段 `tag` 解密出明文业务响应。

失败响应通常不包含业务 `data`，因此不会为了错误信息再次加密。例如：

```json
{
  "code": "F401001",
  "message": "Authorization required"
}
```

## 9. 接口版本规则

### 9.1 URL 版本

当前接口版本为 `v1`，版本号位于 URL 中：

```text
/api/rest/{domain}/{version}/{resource}
```

示例：

```text
/api/rest/iso/v1/countries/query
```

同一业务域下的不同对外 API 必须拆分清楚。国家地区和币种虽然同属 ISO 域，但分别使用独立 Controller 入口，避免一个 Controller 混合多个对外 API。

### 9.2 版本兼容

1. 默认开放接口均为 `v1`。
2. 后续升级 `v2` 时，会保留 `v1` 接口。
3. 商户应按平台分配或文档约定的版本号调用。

### 9.3 HTTP Method 规范

| 场景 | Method | 规则 |
| --- | --- | --- |
| 创建支付、代付、退款等交易 | `POST` | 创建新业务资源或提交交易命令 |
| 加密条件查询 | `POST` | 查询条件加密后放入 JSON 请求体外层 `data` 字段 |
| 整体替换资源 | `PUT` | 请求必须具备幂等性 |
| 局部更新资源 | `PATCH` | 仅更新指定字段 |
| 删除资源 | `DELETE` | 仅用于明确支持删除的资源 |

### 9.4 Java SDK 快速接入

Java 商户推荐使用 `acquiring-openapi-java-sdk`，由 SDK 统一完成 JWT、请求加密、响应解密和敏感日志保护。

Maven 依赖：

```xml
<dependency>
  <groupId>com.scott.openapi</groupId>
  <artifactId>acquiring-openapi-java-sdk</artifactId>
  <version>0.9.0</version>
</dependency>
```

推荐配置：

```properties
merchant.id=<merchant-id>
merchant.jwt.secret=<merchant-jwt-secret>
merchant.platform.public-key-file=classpath:keys/platform-public-key.pem
merchant.response.private-key-file=file:/etc/acquiring/openapi/keys/merchant-response-private-key.pem
merchant.openapi.base-url=https://api.example.com/api/rest/
merchant.source-origin=https://merchant.example.com
# merchant.source-referer=https://merchant.example.com/pay
```

```java
OpenApiClient client = OpenApiClient.create();
```

| SDK 方法 | 对应 API |
| --- | --- |
| `createPayment` | 一步支付 |
| `createAuthorization` / `authorizePayment` | 授权 |
| `createPreAuthorization` | 预授权 |
| `createIncrementalAuthorization` | 增量授权 |
| `preAuthCompletion` | 预授权完成 |
| `capture` | 请款 |
| `refund` | 退款 |
| `voidPayment` | 撤销 |
| `queryPayment` | 交易查询 |
| `createHostedCheckoutSession` | Hosted Checkout 会话 |
| `createPayout` | 创建代付 |
| `queryCountries` | 查询国家地区 |
| `queryCurrencies` | 查询币种 |

SDK 是服务端类库，不得放入浏览器或移动端。`merchant.jwt.secret` 和响应私钥必须存放在密钥管理系统或受控的服务端密钥文件中。生产响应私钥禁止使用 `classpath:`，也禁止进入 Git、SDK/JAR、应用 JAR、容器镜像应用层或日志；推荐通过只读 Secret Volume、KMS/HSM 适配层或权限为 `600` 的外置文件注入。发现私钥泄露或误打包后，必须立即轮换密钥对并在平台登记新公钥。`merchant.source-origin` 必须是仅包含协议、域名和可选端口的 HTTP(S) Origin；`merchant.source-referer` 可包含路径，仅在无法提供 Origin 时使用。SDK 会自动写入标准 `Origin/Referer` Header，商户不要根据终端用户输入动态修改这两个配置。当前 SDK 对交易类调用使用 `orderInfo.orderId`（代付使用 `merchantOrderNo`）作为 JWT `jti`；网络结果不确定时应使用新的查询请求号调用查询接口，不能直接重复发送同一 HTTP 请求。

## 10. 当前开放接口清单

| 接口名称 | Method | Path | 说明 |
| --- | --- | --- | --- |
| 查询国家地区列表 | POST | `/api/rest/iso/v1/countries/query` | 加密条件查询系统支持的 ISO 3166 国家地区 |
| 查询币种列表 | POST | `/api/rest/iso/v1/currencies/query` | 加密条件查询系统支持的 ISO 4217 币种 |
| 创建一步支付 | POST | `/api/rest/payment/v1/payment` | 创建一笔一步支付交易，通常由渠道一次完成授权和请款 |
| 创建收单授权 | POST | `/api/rest/payment/v1/authorization` | 创建一笔收单授权交易 |
| 创建预授权 | POST | `/api/rest/payment/v1/pre-authorization` | 创建一笔预授权交易 |
| 创建增量授权 | POST | `/api/rest/payment/v1/incremental-authorization` | 对同一原始交易追加授权额度 |
| 完成预授权 | POST | `/api/rest/payment/v1/pre-auth-completion` | 对预授权成功交易完成扣款 |
| 发起请款 | POST | `/api/rest/payment/v1/capture` | 对授权或预授权成功交易发起请款 |
| 发起退款 | POST | `/api/rest/payment/v1/refund` | 对成功支付或已请款交易发起退款 |
| 发起撤销 | POST | `/api/rest/payment/v1/void` | 撤销未清算、未请款、未退款的授权或支付动作 |
| 查询交易 | POST | `/api/rest/payment/v1/query` | 按商户订单号查询关联交易动作列表，可选平台交易 ID 精确过滤 |
| 创建 Hosted Checkout 会话 | POST | `/api/rest/checkout/v1/session` | 创建系统收银台会话，返回付款人可打开的 `checkoutUrl` |
| 创建代付 | POST | `/api/rest/payout/v1/create` | 创建一笔代付交易 |

### 10.1 收单交易 V1 接口说明

收单交易接口均走同一套商户 OpenAPI 安全链路：`POST`、JWT 鉴权、请求 `data` 解密、参数校验、`jti` 防重放、成功响应 `data` 加密。商户不要直接调用 `service-payment` 的 `/internal/payment/**` 接口。

商户侧订单标识统一放在 `orderInfo`：`orderInfo.orderNo` 是商户业务订单号，`orderInfo.orderId` 是商户本次 API 请求唯一标识，也是平台资金类请求幂等键。平台侧交易标识统一放在 `transactionInfo`：`transactionInfo.transactionId` 是平台当前交易唯一标识，每一笔授权、请款、退款、撤销都不同，新交易号不带 `TX` 或其他业务前缀；`transactionInfo.sourceTransactionId` 是后续资金动作关联的原平台交易 ID。查询接口允许商户在请求中传 `transactionInfo.transactionId` 作为可选精确过滤条件；不传时平台返回同一 `merchantInfo.merchantId + orderInfo.orderNo` 下的全部关联交易动作。历史 `TX` 前缀交易号仍可用于查询和后续动作兼容。

平台内部还会生成 `operation_id` 关联同一原始交易生命周期，但该字段不返回商户、不要求商户保存、不直接作为渠道请求标识。商户不需要传原交易业务时间；后续资金动作由平台根据 `sourceTransactionId` 定位原交易动作分表，查询接口由平台按商户订单号和可选 `transactionId` 在交易分表中检索。

平台与渠道交互时会继续隔离商户订单号和渠道订单号。以 MPGS 为例，MPGS URL 中的 `orderId` 使用原始授权或一步支付返回的 `transactionInfo.transactionId`，MPGS URL 中的 `transactionId` 使用平台生成并落库的 `channel_transaction_id`。商户无需感知 `channel_transaction_id`，也不会在 OpenAPI 响应中看到内部 `operation_id`。

后续动作通常需要传入：

| 字段 | 位置 | 必填 | 说明 |
| --- | --- | --- | --- |
| `orderInfo.orderNo` | 明文业务 JSON | C | 商户业务订单号。请款、撤销、查询等动作必传；退款可选，传入时必须与原交易商户订单号一致 |
| `orderInfo.orderId` | 明文业务 JSON | M | 商户本次 API 请求唯一标识；创建、请款、退款、撤销、查询均需唯一 |
| `transactionInfo.sourceTransactionId` | 明文业务 JSON | C | 后续资金动作必传，表示原平台交易 ID；查询接口不需要传 |
| `transactionInfo.transactionId` | 明文业务 JSON | O | 查询接口可选平台交易 ID；传入时只返回同一商户订单下命中的单笔交易动作 |

首次类交易包括 `/payment`、`/authorization`、`/pre-authorization`，必须提供 `orderInfo`、`cardInfo` 和 `billingCardHolderInfo`，不要求商户传平台交易 ID，也不要求商户传 `transactionInfo.cardBrand`。平台会根据卡 BIN 库和支付方式能力识别卡品牌，并在响应的 `transactionInfo.cardBrand` 中按统一枚举返回。后续资金动作包括 `/incremental-authorization`、`/pre-auth-completion`、`/capture`、`/refund`、`/void`，平台会先按 `sourceTransactionId` 定位原交易，再校验交易类型、交易状态、币种和可用金额，校验通过后才会请求渠道。

一步支付、授权和预授权都会执行平台内风控，测试交易与真实交易规则一致，卡交易不能绕过已启用规则。完整 PAN 只在加密报文解密后的进程内用于规则匹配，不会写入 Redis、MQ、数据库、日志或响应。风险拒绝仍会生成可查询的失败交易，解密后的 `transactionInfo.code` 为 `F210`、`transactionInfo.message` 为 `Risk blocked`；后续动作不重复执行首次卡交易风控，但仍执行各自动作的状态机、金额、币种和幂等校验。

#### 10.1.1 首次卡交易公共请求参数

以下字段适用于一步支付、授权和预授权。表中长度均按解密后的明文值计算。

| 字段 | 类型 | 必填 | 格式和规则 |
| --- | --- | --- | --- |
| `merchantInfo` | object | M | 商户信息 |
| `merchantInfo.merchantId` | string | M | 6 至 17 位数字，首位为 2 至 9；必须与 JWT `merchantId` 一致 |
| `merchantInfo.subMerchantInfo` | object | O | 子商户资料；传入后按下列子字段校验 |
| `subMerchantInfo.subName` | string | C | 与 `subCompanyName` 至少填写一个，1 至 35 个 ASCII 可打印字符 |
| `subMerchantInfo.subCompanyName` | string | C | 与 `subName` 至少填写一个，1 至 35 个 ASCII 可打印字符 |
| `subMerchantInfo.subId` | string | C | 传入子商户资料时必填，1 至 15 个字符 |
| `subMerchantInfo.subStreet` | string | C | 传入子商户资料时必填，1 至 128 个字符 |
| `subMerchantInfo.subCity` | string | C | 传入子商户资料时必填，1 至 64 个字符 |
| `subMerchantInfo.subState` | string | O | 1 至 3 位字母或数字 |
| `subMerchantInfo.subCountryCode` | string | C | 传入子商户资料时必填，ISO 3166-1 alpha-3 |
| `subMerchantInfo.subEmail` | string | O | 最长 64 个字符的有效邮箱 |
| `subMerchantInfo.subPhone` | string | O | 最长 32 个字符 |
| `subMerchantInfo.subPostal` | string | O | 最长 32 个字符 |
| `subMerchantInfo.subTaxId` | string | O | 最长 32 个非中文字符 |
| `subMerchantInfo.merchantCategory` | string | C | 传入子商户资料时必填，4 位 MCC |
| `subMerchantInfo.intesCode` | string | O | 3 至 4 位字母或数字 |
| `subMerchantInfo.chargeType` | string | O | 3 位字母或数字 |
| `orderInfo` | object | M | 商户订单信息 |
| `orderInfo.amount` | decimal | M | 大于 0，最多 12 位整数和 3 位小数；Java 必须使用 `BigDecimal` |
| `orderInfo.currency` | string | M | ISO 4217 三位大写代码 |
| `orderInfo.orderNo` | string | M | 商户业务订单号，1 至 64 位字母或数字 |
| `orderInfo.orderId` | string | M | 本次 API 请求唯一标识，1 至 64 个可打印字符；作为业务幂等键，建议同时作为 JWT `jti` |
| `billingCardHolderInfo` | object | M | 持卡人账单资料 |
| `billingCardHolderInfo.firstName` | string | M | 1 至 32 个字符 |
| `billingCardHolderInfo.lastName` | string | M | 1 至 32 个字符；与 `firstName` 合计不超过 64 个字符 |
| `billingCardHolderInfo.phone` | string | M | 1 至 32 个字符 |
| `billingCardHolderInfo.email` | string | M | 最长 64 个字符的有效邮箱 |
| `billingCardHolderInfo.country` | string | M | ISO 3166-1 alpha-3 |
| `billingCardHolderInfo.state` | string | O | 2 至 3 个字符 |
| `billingCardHolderInfo.city` | string | M | 1 至 64 个字符 |
| `billingCardHolderInfo.street` | string | M | 1 至 128 个字符 |
| `billingCardHolderInfo.postal` | string | M | 1 至 32 个字符 |
| `cardInfo` | object | M | PCI 高敏感字段，只能出现在加密前的商户进程内和加密后的 `data` 中 |
| `cardInfo.cardNo` | string | M | 11 至 19 位数字 PAN |
| `cardInfo.expirationMonth` | string | M | `01` 至 `12` |
| `cardInfo.expirationYear` | string | M | 4 位年份 |
| `cardInfo.securityCode` | string | M | 3 至 4 位 CVV/CVC；严禁落库或记录日志 |
| `threeDSInfo` | object | O | 商户已完成 3DS 时传入；JSON 字段名固定为 `threeDSInfo` |
| `threeDSInfo.eci` | string | O | 3 位数字 |
| `threeDSInfo.cavv` | string | O | 28 个字符 |
| `threeDSInfo.dsTransactionId` | string | O | 36 个字符 |
| `threeDSInfo.threeDsVersion` | string | O | 例如 `2.2.0` |
| `riskContext` | object | O | 商户可选上送的实时风控上下文；仅用于本次风控，不在交易响应中回显 |
| `riskContext.customerId` | string | O | 商户体系内稳定客户 ID，1 至 64 个 ASCII 非空白字符；用于 Customer ID 名单和频率规则，不得传姓名、邮箱或卡号 |
| `riskContext.deviceFingerprint` | string | O | 商户生成的稳定设备指纹，1 至 128 个 ASCII 非空白字符；用于设备指纹名单和频率规则，不得传原始设备采集报文 |
| `riskContext.shippingAddress` | string | O | 收货街道地址，1 至 256 个 ASCII 可打印字符；用于收货地址名单规则 |
| `riskContext.shippingPostalCode` | string | O | 收货邮编，2 至 20 个字符，只允许字母、数字、空格和短横线 |
| `riskContext.shippingCountry` | string | O | 收货国家或地区，ISO 3166-1 alpha-3 三位大写代码，例如 `USA` |
| `transactionInfo` | object | O | 首次交易扩展信息 |
| `transactionInfo.description` | string | O | 1 至 128 个字符 |
| `transactionInfo.callbackUrl` | string | O | 最长 256 个字符的 HTTP/HTTPS URL |

首次卡交易明文请求示例：

```json
{
  "merchantInfo": {
    "merchantId": "200045",
    "subMerchantInfo": {
      "subCompanyName": "Scott Demo Store",
      "subId": "SUB001",
      "subStreet": "100 Main Street",
      "subCity": "New York",
      "subState": "NY",
      "subCountryCode": "USA",
      "subEmail": "merchant@example.com",
      "subPhone": "12025550123",
      "subPostal": "10001",
      "merchantCategory": "5311"
    }
  },
  "orderInfo": {
    "amount": 10.00,
    "currency": "USD",
    "orderNo": "M202607290001",
    "orderId": "REQ202607290001"
  },
  "billingCardHolderInfo": {
    "firstName": "Scott",
    "lastName": "Demo",
    "phone": "12025550123",
    "email": "cardholder@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "cardInfo": {
    "cardNo": "<sandbox-card-number>",
    "expirationMonth": "12",
    "expirationYear": "2030",
    "securityCode": "<sandbox-cvv>"
  },
  "riskContext": {
    "customerId": "CUSTOMER-20260729-001",
    "deviceFingerprint": "DEVICE-FP-7F97A8B2C1",
    "shippingAddress": "200 Shipping Street",
    "shippingPostalCode": "10003",
    "shippingCountry": "USA"
  },
  "transactionInfo": {
    "description": "Order M202607290001",
    "callbackUrl": "https://merchant.example.com/api/payment/notify"
  }
}
```

该请求分别提交至以下三个接口。每次调用必须使用新的 `orderInfo.orderId` 和 JWT `jti`：

| 接口 | Path | `transactionInfo.transactionType` 响应值 | 业务结果 |
| --- | --- | --- | --- |
| 一步支付 | `/api/rest/payment/v1/payment` | `PAYMENT` | 成功时通常一次完成授权和请款 |
| 授权 | `/api/rest/payment/v1/authorization` | `AUTHORIZATION` | 成功后可请款、增量授权或撤销 |
| 预授权 | `/api/rest/payment/v1/pre-authorization` | `PRE_AUTHORIZATION` | 成功后可预授权完成、请款或撤销 |

#### 10.1.2 后续交易公共请求参数

| 字段 | 类型 | 必填 | 格式和规则 |
| --- | --- | --- | --- |
| `merchantInfo.merchantId` | string | M | 必须与 JWT `merchantId` 和源交易商户一致 |
| `orderInfo.orderId` | string | M | 本次动作唯一标识；同一值不得用于不同请求内容 |
| `orderInfo.orderNo` | string | C | 增量授权、预授权完成、请款、撤销和查询必填；退款可选，传入时必须与源交易一致 |
| `orderInfo.amount` | decimal | C | 增量授权、预授权完成、请款和退款必填，大于 0，最多 12 位整数和 3 位小数 |
| `orderInfo.currency` | string | C | 增量授权、预授权完成和请款必填；退款可选；传入时必须与源交易币种一致 |
| `transactionInfo.sourceTransactionId` | string | C | 增量授权、预授权完成、请款、退款和撤销必填，使用源交易响应的 `transactionId` |
| `transactionInfo.transactionId` | string | O | 仅查询接口用于精确过滤 |
| `transactionInfo.description` | string | O | 本次动作备注，最长 128 个字符 |
| `transactionInfo.callbackUrl` | string | O | 本次动作通知地址，最长 256 个字符的 HTTP/HTTPS URL |

各接口的明文请求和约束如下。

**增量授权**

```http
POST /api/rest/payment/v1/incremental-authorization
```

```json
{
  "merchantInfo": {"merchantId": "200045"},
  "orderInfo": {
    "orderNo": "M202607290001",
    "orderId": "INCR202607290001",
    "amount": 2.50,
    "currency": "USD"
  },
  "transactionInfo": {
    "sourceTransactionId": "202607291030001230001",
    "description": "Increase authorized amount"
  }
}
```

源交易必须是允许追加额度的成功授权类交易。响应 `transactionType` 为 `INCREMENTAL_AUTHORIZATION`，`amount` 表示本次追加金额，累计授权金额读取 `orderInfo.totalAuthorizedAmount`。

**预授权完成**

```http
POST /api/rest/payment/v1/pre-auth-completion
```

```json
{
  "merchantInfo": {"merchantId": "200045"},
  "orderInfo": {
    "orderNo": "M202607290002",
    "orderId": "PAC202607290001",
    "amount": 8.00,
    "currency": "USD"
  },
  "transactionInfo": {
    "sourceTransactionId": "202607291040001230002",
    "description": "Complete pre-authorization"
  }
}
```

源交易必须为状态允许完成的预授权交易，本次金额不得超过源交易可完成金额。响应 `transactionType` 为 `PRE_AUTH_COMPLETION`。

**请款**

```http
POST /api/rest/payment/v1/capture
```

```json
{
  "merchantInfo": {"merchantId": "200045"},
  "orderInfo": {
    "orderNo": "M202607290001",
    "orderId": "CAPTURE202607290001",
    "amount": 10.00,
    "currency": "USD"
  },
  "transactionInfo": {
    "sourceTransactionId": "202607291030001230001",
    "description": "Capture authorized payment"
  }
}
```

源交易必须为可请款的授权或预授权交易，本次金额不得超过剩余可请款金额。响应 `transactionType` 为 `CAPTURE`，累计请款金额读取 `orderInfo.totalCapturedAmount`。

**退款**

```http
POST /api/rest/payment/v1/refund
```

```json
{
  "merchantInfo": {"merchantId": "200045"},
  "orderInfo": {
    "orderNo": "M202607290001",
    "orderId": "REFUND202607290001",
    "amount": 3.00,
    "currency": "USD"
  },
  "transactionInfo": {
    "sourceTransactionId": "202607291100001230003",
    "description": "Partial refund"
  }
}
```

源交易必须存在成功支付或请款金额，本次退款不得超过剩余可退金额。`orderNo` 和 `currency` 可省略；传入时必须与源交易一致。响应 `transactionType` 为 `REFUND`，累计退款金额读取 `orderInfo.totalRefundAmount`。

**撤销**

```http
POST /api/rest/payment/v1/void
```

```json
{
  "merchantInfo": {"merchantId": "200045"},
  "orderInfo": {
    "orderNo": "M202607290001",
    "orderId": "VOID202607290001"
  },
  "transactionInfo": {
    "sourceTransactionId": "202607291030001230001",
    "description": "Cancel authorization"
  }
}
```

源交易必须处于允许撤销的状态，不建议传金额和币种。响应 `transactionType` 为 `VOID`。

**交易查询**

```http
POST /api/rest/payment/v1/query
```

```json
{
  "merchantInfo": {"merchantId": "200045"},
  "orderInfo": {
    "orderNo": "M202607290001",
    "orderId": "QUERY202607290001"
  },
  "transactionInfo": {
    "transactionId": "202607291100001230003"
  }
}
```

`transactionInfo.transactionId` 可省略。省略时返回同一商户订单下的全部交易动作；传入时只返回该订单下匹配的平台交易。查询响应的 `transactionInfo` 为数组，其余交易接口为对象。

#### 10.1.3 交易幂等、状态和风控处理

1. `orderInfo.orderId` 标识一次 API 动作，商户重试同一业务请求时必须保持业务参数不变。
2. JWT `jti` 每次 HTTP 请求必须唯一。SDK 默认使用请求唯一标识生成 `jti`；被防重放拒绝后，应先查询原交易，不得盲目换号重复扣款。
3. HTTP 200 和外层 `code=T200` 表示 OpenAPI 已完成请求处理并返回业务数据，不等于资金动作成功。必须解密 `data` 并检查 `transactionInfo.code`。
4. `transactionInfo.code=T200` 表示当前动作成功；`T201/T202/T203` 表示已受理、处理中或待确认；`F` 开头表示失败。
5. 一步支付、授权和预授权在渠道路由前执行内风控。被风控拒绝的交易仍可通过查询接口检索，且不会继续请求支付渠道。
6. 商户不得将完整 PAN、CVV、JWT、私钥或解密后的完整请求写入日志。平台返回的 `cardBin` 仅为脱敏卡号摘要。
7. `riskContext` 整体可选；但已启用的 Customer ID、设备指纹、收货地址、收货邮编或收货国家规则只有在对应字段存在时才能执行值匹配。字段缺失不会被当作名单命中，平台内部审计会记录该维度缺少可匹配值；商户应对三个受控接口稳定上送可获得的风控上下文。

退款请求使用最小参数集即可：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `merchantInfo.merchantId` | M | 支付平台颁发的商户号，必须与 JWT 中的商户号一致 |
| `orderInfo.amount` | M | 本次退款金额，主币种单位，必须大于 0 且不能超过原交易剩余可退金额 |
| `orderInfo.currency` | O | 退款币种；不传时平台按原交易币种处理，传入时必须与原交易币种一致 |
| `orderInfo.orderId` | M | 商户本次退款请求唯一标识，用作退款幂等键 |
| `orderInfo.orderNo` | O | 商户订单号；传入时必须与原交易商户订单号一致 |
| `transactionInfo.sourceTransactionId` | M | 原平台交易 ID，平台根据该字段定位原交易并判断是否允许退款 |
| `transactionInfo.description` | O | 商户退款备注，平台响应中原样返回；为空时不返回 |

查询请求使用最小参数集：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `merchantInfo.merchantId` | M | 支付平台颁发的商户号，必须与 JWT 中的商户号一致 |
| `orderInfo.orderNo` | M | 商户原订单号，用于查询该订单下所有关联交易动作 |
| `orderInfo.orderId` | M | 商户本次查询请求唯一标识，用于请求防重放和日志排查 |
| `transactionInfo.transactionId` | O | 需要查询的指定平台交易 ID；传入时只返回一条命中记录 |

查询请求明文业务 JSON 示例：

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "orderNo": "M202607140001",
    "orderId": "QUERY202607140001"
  },
  "transactionInfo": {
    "transactionId": "202607141805301230002"
  }
}
```

后续动作的金额规则：

| 接口 | 金额规则 |
| --- | --- |
| `/incremental-authorization` | `orderInfo.amount` 必须大于 0，币种必须与原交易交易币种一致 |
| `/capture` | `orderInfo.amount` 必须大于 0，不能超过原交易可请款金额，币种必须与原交易交易币种一致 |
| `/refund` | `orderInfo.amount` 必须大于 0，不能超过原交易可退金额；`orderInfo.currency` 可选，传入时必须与原交易交易币种一致 |
| `/void` | 不建议传金额；原交易必须未请款、未退款，且状态允许撤销 |

#### 10.1.4 逐接口风控范围与执行顺序

| 接口 | 是否执行交易内风控 | IP 白名单未通过 | 来源网址规则 | 说明 |
| --- | --- | --- | --- | --- |
| `/payment` | 是 | 落失败交易并返回 `F210 / Risk blocked` | 执行 | 真实卡和测试卡规则一致 |
| `/authorization` | 是 | 落失败交易并返回 `F210 / Risk blocked` | 执行 | 真实卡和测试卡规则一致 |
| `/pre-authorization` | 是 | 落失败交易并返回 `F210 / Risk blocked` | 执行 | 真实卡和测试卡规则一致 |
| `/incremental-authorization` | 否 | 安全层返回 `F403` | 不执行 | 执行源交易、金额、币种和状态机校验 |
| `/pre-auth-completion` | 否 | 安全层返回 `F403` | 不执行 | 执行源交易、金额、币种和状态机校验 |
| `/capture` | 否 | 安全层返回 `F403` | 不执行 | 执行可请款金额和状态机校验 |
| `/refund` | 否 | 安全层返回 `F403` | 不执行 | 执行可退款金额和状态机校验 |
| `/void` | 否 | 安全层返回 `F403` | 不执行 | 执行可撤销状态校验 |
| `/query` | 否 | 安全层返回 `F403` | 不执行 | 只查询当前商户可见交易 |
| ISO、Hosted Checkout、代付 | 否 | 安全层返回 `F403` | 不执行 | 仍执行 JWT、IP 白名单和防重放 |

支付、授权和预授权的交易内风控固定按以下顺序执行：

1. 商户 IP 白名单。
2. 商户来源网址允许清单。
3. AML：按已启用规则检查完整卡号、CardBin、发卡国家、账单国家、IP、邮箱、邮箱域名、电话和来源网址等元素。
4. 黑白名单仲裁：强白名单 → A 类黑名单 → 优先白名单 → B 类黑名单 → 弱白名单 → C 类黑名单。
5. 商户交易限额。
6. 交易频率限定。

任一前置节点产生最终拦截后，平台不再执行后续节点，也不会调用支付渠道。已执行节点的通过或拦截明细会累计进入平台内部审计和交易时间轴；某个可选风控字段未上送时，该检查维度记录为缺少可匹配值，不会误记为名单命中。平台不会向商户响应暴露规则 ID、名单内容或内部判定细节。商户只能收到稳定的 `F210 / Risk blocked`，并可使用原 `orderInfo.orderNo` 或响应中的平台交易 ID 查询该笔失败交易。

收单交易通用响应解密后示例：

```json
{
  "merchantInfo": {
    "merchantId": "200045",
    "subMerchantInfo": {
      "subId": "SUB001",
      "subCompanyName": "Scott Demo Store",
      "subStreet": "100 Main Street",
      "subCity": "New York",
      "subState": "NY",
      "subCountryCode": "USA",
      "subEmail": "merchant@example.com",
      "subPhone": "12025550123",
      "subPostal": "10001",
      "merchantCategory": "5311"
    }
  },
  "orderInfo": {
    "orderNo": "M202607140001",
    "orderId": "CAPTURE202607140001",
    "amount": 120.00,
    "currency": "USD",
    "totalAuthorizedAmount": 120.00,
    "totalCapturedAmount": 120.00,
    "totalRefundAmount": 0.00
  },
  "billingCardHolderInfo": {
    "firstName": "Scott",
    "lastName": "Demo",
    "phone": "12025550123",
    "email": "cardholder@example.com",
    "country": "USA",
    "state": "NY",
    "city": "New York",
    "street": "100 Main Street",
    "postal": "10001"
  },
  "transactionInfo": {
    "code": "T200",
    "message": "Success",
    "transactionId": "202607141805301230002",
    "sourceTransactionId": "202607141759001110001",
    "transactionType": "CAPTURE",
    "transactionDateTime": "2026-07-14T18:05:30+08:00",
    "paymentMethod": "BANK_CARD",
    "cardBrand": "MASTERCARD",
    "cardBin": "512345****0008",
    "authCode": "244682"
  },
  "billingInfo": {
    "labelAmount": 120.00,
    "labelCurrency": "USD",
    "transactionAmount": 120.00,
    "transactionCurrency": "USD",
    "transactionRate": 1.00000000,
    "settlementCurrency": "HKD"
  }
}
```

交易查询响应解密后示例：

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "orderNo": "M202607140001",
    "orderId": "QUERY202607140001",
    "amount": 120.00,
    "currency": "USD",
    "totalAuthorizedAmount": 120.00,
    "totalCapturedAmount": 120.00,
    "totalRefundAmount": 22.00
  },
  "transactionInfo": [
    {
      "code": "T200",
      "message": "Success",
      "transactionId": "202607141759001110001",
      "transactionType": "AUTHORIZATION",
      "transactionDateTime": "2026-07-14T17:59:00+08:00",
      "paymentMethod": "BANK_CARD",
      "cardBrand": "MASTERCARD",
      "cardBin": "512345****0008",
      "authCode": "244682"
    },
    {
      "code": "T200",
      "message": "Success",
      "transactionId": "202607141805301230002",
      "sourceTransactionId": "202607141759001110001",
      "transactionType": "CAPTURE",
      "transactionDateTime": "2026-07-14T18:05:30+08:00",
      "paymentMethod": "BANK_CARD",
      "cardBrand": "MASTERCARD",
      "cardBin": "512345****0008"
    },
    {
      "code": "T200",
      "message": "Success",
      "transactionId": "202607141820001230003",
      "sourceTransactionId": "202607141805301230002",
      "transactionType": "REFUND",
      "transactionDateTime": "2026-07-14T18:20:00+08:00",
      "paymentMethod": "BANK_CARD",
      "cardBrand": "MASTERCARD",
      "cardBin": "512345****0008"
    }
  ],
  "billingInfo": {
    "labelAmount": 120.00,
    "labelCurrency": "USD",
    "transactionAmount": 120.00,
    "transactionCurrency": "USD",
    "transactionRate": 1.00000000,
    "settlementCurrency": "HKD"
  }
}
```

说明：响应业务 JSON 中为空的字段不返回。平台不会在商户响应中返回 `cardInfo`、`operationId`、`channel_transaction_id`、渠道原始报文、CVV 或完整卡号，也不会返回顶层兼容字段 `status/currency`、`dccEnabled`、`edcEnabled`、`transactionStatus`、`processStage`、`failReasonCode`、`failReasonMessage`；商户可通过 `transactionInfo.code/message` 判断当前动作商户侧结果，通过 `billingInfo.labelCurrency/labelAmount` 与 `billingInfo.transactionCurrency/transactionAmount` 以及 `transactionRate` 判断是否发生币种转换。

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `merchantInfo.merchantId` | string | 支付平台颁发的商户号 |
| `merchantInfo.subMerchantInfo` | object | 子商户信息，支付接口按商户请求 `merchantInfo.subMerchantInfo` 原样回显；无值时不返回 |
| `orderInfo.orderNo` | string | 商户订单号，原样返回请求中的 `orderInfo.orderNo` |
| `orderInfo.orderId` | string | 商户本次 API 请求唯一标识，原样返回请求中的 `orderInfo.orderId` |
| `orderInfo.amount` | decimal | 商户上送订单金额，主币种单位 |
| `orderInfo.currency` | string | 商户上送订单币种 |
| `orderInfo.totalAuthorizedAmount` | decimal | 当前生命周期累计授权成功金额，平台交易币种单位 |
| `orderInfo.totalCapturedAmount` | decimal | 当前生命周期累计请款成功金额，平台交易币种单位 |
| `orderInfo.totalRefundAmount` | decimal | 当前生命周期累计退款成功金额，平台交易币种单位 |
| `billingCardHolderInfo` | object | 账单持卡人信息，支付接口按商户请求 `billingCardHolderInfo` 原样回显，不包含卡号和 CVV |
| `transactionInfo` | object / array | 创建、授权、请款、退款、撤销等动作返回对象；查询接口返回数组 |
| `transactionInfo.code` | string | 当前交易动作商户响应码，例如 `T200`、`T202`、`T203`、`F210` |
| `transactionInfo.message` | string | 当前交易动作商户响应描述；内风控拒绝返回 `Risk blocked`，其他失败返回平台选定的商户可见文案，不返回内部失败堆栈 |
| `transactionInfo.transactionId` | string | 平台当前交易唯一标识，后续请款、退款、撤销、查询可作为 `sourceTransactionId` 使用 |
| `transactionInfo.sourceTransactionId` | string | 原平台交易 ID；首次类交易通常为空，后续动作返回请求传入值 |
| `transactionInfo.transactionType` | string | 交易类型，对齐平台字典 `transaction_type` |
| `transactionInfo.transactionDateTime` | string | 交易发生时间，按交易业务时区返回，格式为 ISO-8601 offset datetime |
| `transactionInfo.paymentMethod` | string | 支付方式，例如 `BANK_CARD` |
| `transactionInfo.cardBrand` | string | 平台根据卡 BIN 库识别后的卡品牌或支付品牌，例如 `MASTERCARD`、`VISA` |
| `transactionInfo.cardBin` | string | 脱敏卡号摘要，格式为前六位 + `****` + 后四位 |
| `transactionInfo.authCode` | string | 授权码，渠道成功返回时填写 |
| `transactionInfo.arn` | string | ARN 或收单机构参考号，请款或渠道返回时填写 |
| `billingInfo.labelAmount` / `billingInfo.labelCurrency` | decimal / string | 商户上送或页面标签展示的金额和币种 |
| `billingInfo.transactionAmount` / `billingInfo.transactionCurrency` | decimal / string | 平台上送渠道的交易金额和币种 |
| `billingInfo.transactionRate` | decimal | 标签金额转平台交易金额使用的汇率，保留 8 位小数；未换汇时为 `1.00000000` |
| `billingInfo.settlementCurrency` | string | 商户结算币种，来源为商户信息表 `base_merchant_info.settlement_currency` |

商户应以解密后的 `transactionInfo.code/message` 判断当前动作的商户侧结果，并保存 `transactionInfo.transactionId` 作为后续请款、退款、撤销、查询的源交易标识。HTTP 200 或外层 `T200` 只代表平台成功处理请求，不等于渠道资金结果必然成功。内风控拒绝时 `transactionInfo.code/message` 为 `F210 / Risk blocked`；其他失败可能使用公共默认拒绝文案或平台选定的商户响应文案。渠道内部失败字段 `failReasonCode/failReasonMessage`、异常堆栈和原始报文不会返回商户。

退款响应中，`orderInfo.amount` 和 `billingInfo.labelAmount/transactionAmount` 表示本次退款金额；`orderInfo.totalAuthorizedAmount` 和 `orderInfo.totalCapturedAmount` 表示原支付或授权生命周期的成功金额；`orderInfo.totalRefundAmount` 表示包含本次退款在内的累计退款金额。例如一步支付 102 USD 后退款 22 USD，退款响应应展示 `amount=22`、`totalAuthorizedAmount=102`、`totalCapturedAmount=102`、`totalRefundAmount=22`。

当商户在 `transactionInfo.callbackUrl` 或平台商户配置中登记回调地址时，平台会在交易进入终态后创建商户通知任务并按重试策略推送结果。商户通知只包含商户可见字段和平台选定的商户响应文案；渠道内部失败字段、收单原始响应和内部排查信息只在平台后台交易详情、渠道交互日志和渠道回调记录中展示。

### 10.2 Hosted Checkout V1 接口说明

Hosted Checkout 用于商户系统不直接收集卡号、有效期和 CVV 的场景。商户只调用 OpenAPI 创建收银台会话，平台返回 `checkoutUrl`；付款人在平台收银台页面填写银行卡信息并完成 MPGS 卡支付和 3DS 认证。

创建收银台会话仍走商户 OpenAPI 安全链路：`POST`、JWT 鉴权、请求 `data` 解密、参数校验、`jti` 防重放、成功响应 `data` 加密。商户不要直接调用 `service-payment` 的 `/internal/payment/checkout/**` 接口，也不要让付款人浏览器调用商户 OpenAPI。

收银台 URL 由平台拼装，格式为：

```text
{platform.checkout.frontend-base-url}/checkout/{opaqueToken}/{cover}
```

其中 `platform.checkout.frontend-base-url` 来源于系统参数设置表 `sys_config.config_key = 'platform.checkout.frontend-base-url'`，不是 Nacos，也不是商户请求参数。`opaqueToken` 唯一绑定一笔收银台会话，数据库只保存 token hash；`cover` 仅用于遮盖真实 token 形态，不参与查询、支付、状态判断或幂等。

接口地址：

```http
POST /api/rest/checkout/v1/session
```

明文请求示例：

```json
{
  "merchantInfo": {
    "merchantId": "200045",
    "subMerchantInfo": {
      "subId": "SUB001",
      "subCompanyName": "Scott Demo Store",
      "subCountryCode": "USA",
      "merchantCategory": "5311"
    }
  },
  "orderInfo": {
    "orderNo": "M202607270001",
    "orderId": "CHECKOUT202607270001",
    "amount": 49.97,
    "currency": "USD",
    "subject": "Hosted Checkout Order",
    "description": "Order summary for hosted checkout"
  },
  "checkoutInfo": {
    "locale": "en-US",
    "expireMinutes": 30,
    "allowedPaymentMethods": [
      {
        "paymentMethod": "BANK_CARD",
        "channelCode": "MPGS",
        "brands": ["VISA", "MASTERCARD", "AMEX", "JCB"],
        "threeDsMode": "AUTO"
      }
    ],
    "retryAllowed": true,
    "maxAttemptCount": 3,
    "returnUrl": "https://merchant.example.com/payment/result",
    "cancelUrl": "https://merchant.example.com/cart",
    "notifyUrl": "https://merchant.example.com/api/payment/notify"
  },
  "payerInfo": {
    "payerId": "CUST10001",
    "email": "customer@example.com",
    "country": "USA"
  }
}
```

核心字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantInfo.merchantId` | string | M | 平台商户号，必须与 JWT 中的商户号一致 |
| `merchantInfo.subMerchantInfo` | object | O | 子商户快照，字段格式与首次卡交易一致 |
| `orderInfo.orderNo` | string | M | 商户业务订单号，1 至 64 位字母或数字 |
| `orderInfo.orderId` | string | M | 商户本次创建收银台请求唯一标识，SDK 使用该字段作为 JWT `jti` |
| `orderInfo.amount` | decimal | M | 订单金额，主币种单位，必须大于 0 |
| `orderInfo.currency` | string | M | ISO 4217 三位大写币种 |
| `orderInfo.subject` | string | O | 订单主题 |
| `orderInfo.description` | string | O | 订单描述 |
| `orderInfo.items` | array | O | 商品明细；元素可包含 `name/quantity/amount/currency` |
| `checkoutInfo.locale` | string | O | 收银台语言地区，例如 `en-US` |
| `checkoutInfo.expireMinutes` | integer | O | 会话有效分钟数，最终取值受平台配置限制 |
| `checkoutInfo.allowedPaymentMethods` | array | M | 商户允许的支付方式快照，至少提供一个元素 |
| `allowedPaymentMethods[].paymentMethod` | string | M | 支付方式，当前卡支付使用 `BANK_CARD` |
| `allowedPaymentMethods[].channelCode` | string | O | 指定渠道，例如 `MPGS` |
| `allowedPaymentMethods[].brands` | array | O | 允许的卡品牌，例如 `VISA/MASTERCARD/AMEX/JCB` |
| `allowedPaymentMethods[].threeDsMode` | string | O | 3DS 模式，例如 `AUTO` |
| `checkoutInfo.retryAllowed` | boolean | O | 是否允许付款人失败后在会话有效期内重试 |
| `checkoutInfo.maxAttemptCount` | integer | O | 最大尝试次数，最终取值受平台配置限制 |
| `checkoutInfo.returnUrl` | string | M | 付款人点击结果页返回商户网站的 HTTP/HTTPS 地址，最长 256 个字符 |
| `checkoutInfo.cancelUrl` | string | O | 付款人取消时返回商户网站的 HTTP/HTTPS 地址 |
| `checkoutInfo.notifyUrl` | string | O | 商户异步通知地址，终态通知规则与常规支付通知保持一致 |
| `checkoutInfo.checkoutDomain` | string | O | 兼容旧字段；平台生成 `checkoutUrl` 时忽略该字段 |
| `payerInfo.payerId` | string | O | 商户侧付款人标识，不参与会话幂等 |
| `payerInfo.email` | string | O | 付款人邮箱，平台内部只保存掩码和摘要 |
| `payerInfo.country` | string | O | 付款人国家/地区代码 |

明文响应示例：

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "checkoutInfo": {
    "checkoutSessionId": "2607271118051230000017",
    "checkoutUrl": "https://pay.example.com/checkout/7xB5rLQm9kN2sP6vT3wY8zA1cD4eF0hJ/pY4nQ8sT2v",
    "status": "PAYABLE",
    "expireTime": "2026-07-27T11:48:05+08:00",
    "idempotentHit": false
  },
  "orderInfo": {
    "orderNo": "M202607270001",
    "orderId": "CHECKOUT202607270001",
    "amount": 49.97,
    "currency": "USD"
  }
}
```

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `merchantInfo.merchantId` | string | 平台商户号 |
| `checkoutInfo.checkoutSessionId` | string | 收银台会话唯一标识 |
| `checkoutInfo.checkoutUrl` | string | 付款人访问地址；商户应直接跳转，不要解析或改写 token |
| `checkoutInfo.status` | string | 会话状态，例如 `PAYABLE` |
| `checkoutInfo.expireTime` | string | ISO-8601 offset datetime 过期时间 |
| `checkoutInfo.idempotentHit` | boolean | 是否命中同一请求的幂等结果 |
| `orderInfo` | object | 原订单号、请求号、金额和币种摘要 |

幂等规则：

1. 首次请求创建 checkout session、token 和安全事件。
2. 相同 `merchantInfo.merchantId + orderInfo.orderId` 且请求摘要一致时，平台返回同一 `checkoutSessionId` 并重新签发新的 `checkoutUrl`。
3. 相同幂等键但金额、币种、订单号、允许支付方式、回跳地址等核心字段不一致时，平台返回幂等冲突错误。

Java SDK 调用示例：

```java
OpenApiClient client = OpenApiClient.create();

HostedCheckoutCreateRequest request = new HostedCheckoutCreateRequest();
HostedCheckoutCreateRequest.MerchantInfo merchantInfo = new HostedCheckoutCreateRequest.MerchantInfo();
merchantInfo.setMerchantId("200045");
request.setMerchantInfo(merchantInfo);

HostedCheckoutCreateRequest.OrderInfo orderInfo = new HostedCheckoutCreateRequest.OrderInfo();
orderInfo.setOrderNo("M202607270001");
orderInfo.setOrderId("CHECKOUT202607270001");
orderInfo.setAmount(new BigDecimal("49.97"));
orderInfo.setCurrency("USD");
orderInfo.setSubject("Hosted Checkout Order");
request.setOrderInfo(orderInfo);

HostedCheckoutCreateRequest.AllowedPaymentMethod method = new HostedCheckoutCreateRequest.AllowedPaymentMethod();
method.setPaymentMethod("BANK_CARD");
method.setChannelCode("MPGS");
method.setBrands(Arrays.asList("VISA", "MASTERCARD", "AMEX", "JCB"));
method.setThreeDsMode("AUTO");

HostedCheckoutCreateRequest.CheckoutInfo checkoutInfo = new HostedCheckoutCreateRequest.CheckoutInfo();
checkoutInfo.setAllowedPaymentMethods(Collections.singletonList(method));
checkoutInfo.setReturnUrl("https://merchant.example.com/payment/result");
checkoutInfo.setCancelUrl("https://merchant.example.com/cart");
checkoutInfo.setNotifyUrl("https://merchant.example.com/api/payment/notify");
request.setCheckoutInfo(checkoutInfo);

OpenApiResult<HostedCheckoutCreateResponse> result = client.createHostedCheckoutSession(request);
String checkoutUrl = result.getData().getCheckoutInfo().getCheckoutUrl();
```

说明：当前付款人结果页的“返回商户网站”使用 `returnUrl` 原样跳转；如商户需要浏览器回跳携带结果摘要和签名参数，需要后续单独启用 returnUrl 回跳参数规范。资金最终结果仍以平台交易状态、商户异步通知和查询接口为准。

### 10.3 代付创建 V1 接口说明

接口地址：

```http
POST /api/rest/payout/v1/create
```

明文请求参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merchantOrderNo` | string | M | 商户代付订单号，商户侧保持唯一 |
| `currency` | string | M | ISO 4217 三位大写币种代码 |
| `amount` | integer | M | 最小币种单位的正整数；例如 USD 10.00 传 `1000` |
| `receiverAccountNo` | string | M | 收款账户号；必须在商户服务端加密前组装，严禁记录完整账号日志 |

明文请求示例：

```json
{
  "merchantOrderNo": "PO-M-202607290001",
  "currency": "USD",
  "amount": 1000,
  "receiverAccountNo": "<sandbox-receiver-account>"
}
```

当前接口成功响应的解密结果是平台代付单号字符串，不是代付详情对象：

```json
"PO202607291230001230001"
```

实际 HTTP 响应示例：

```json
{
  "code": "T200",
  "message": "Success",
  "data": "<compact encrypted payload>"
}
```

商户使用响应私钥解密 `data` 后得到上述字符串。商户必须同时保存 `merchantOrderNo` 和平台代付单号用于后续对账。当前公开 V1 仅提供创建代付；文档未列出的内部查询、回调或管理接口不得由商户直接调用。

### 10.4 商户异步通知边界

1. 商户在交易请求的 `transactionInfo.callbackUrl`、Hosted Checkout 的 `checkoutInfo.notifyUrl` 或平台商户配置中登记通知地址。
2. 交易进入终态后，平台创建通知任务并按配置的重试策略推送；商户必须按交易 ID 做幂等处理。
3. 商户收到通知后仍应允许通过 `/api/rest/payment/v1/query` 主动核验最终状态。
4. `/openapi/v1/merchant-notifies/retry` 是平台内部重试入口，`/channel/v1/callbacks/**` 是支付渠道回调入口，均不属于商户 OpenAPI，商户不得调用。
5. 商户通知只包含商户可见字段和稳定失败信息，不包含渠道原始报文、内部风控规则明细、完整卡号或 CVV。

## 11. 查询国家地区列表

### 11.1 接口说明

商户查询支付框架当前支持的 ISO 3166 国家地区信息。该接口可用于商户前端下拉框、交易国家校验、账单地址国家校验、风控规则展示等场景。

如果商户不传任何查询条件，即明文业务 JSON 为 `{}`，平台返回全部启用国家地区。

### 11.2 接口地址

```http
POST /api/rest/iso/v1/countries/query
```

完整示例：

```http
POST https://api.example.com/api/rest/iso/v1/countries/query
```

### 11.3 请求头

| Header | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `Content-Type` | M | `application/json` | 固定值 |
| `authorization` | M | `Bearer {jwt}` | 商户 JWT |

### 11.4 明文请求参数

商户加密前的业务 JSON 支持以下字段。多个字段同时传入时，平台按 AND 关系组合过滤。

| 字段 | 类型 | 必填 | 最大长度 | 示例 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `alpha2` | string | O | 2 | `US` | ISO 3166-1 alpha-2 两位字母代码 |
| `alpha3` | string | O | 3 | `USA` | ISO 3166-1 alpha-3 三位字母代码 |
| `numeric` | string | O | 3 | `840` | ISO 3166-1 numeric 三位数字代码 |
| `englishName` | string | O | 128 | `United States of America` | 国家或地区英文全称，支持包含匹配 |
| `shortEnglishName` | string | O | 128 | `United States` | 国家或地区英文简称，支持包含匹配 |
| `chineseName` | string | O | 128 | `美国` | 国家或地区中文名称，支持包含匹配 |
| `continentCode` | string | O | 2 | `NA` | 七大洲代码 |
| `primaryLanguageCode` | string | O | 16 | `en` | 主要语言代码 |
| `currencyAlpha3Code` | string | O | 3 | `USD` | 默认币种 ISO 4217 三位字母代码 |

说明：查询全部国家地区时，商户需要把明文业务 JSON 写为 `{}` 后再加密，不要传空请求体，也不要把字段传为空字符串。

七大洲代码：

| 代码 | 中文名称 | 英文名称 |
| --- | --- | --- |
| `AS` | 亚洲 | Asia |
| `EU` | 欧洲 | Europe |
| `AF` | 非洲 | Africa |
| `NA` | 北美洲 | North America |
| `SA` | 南美洲 | South America |
| `OC` | 大洋洲 | Oceania |
| `AN` | 南极洲 | Antarctica |

### 11.5 明文请求示例

查询全部国家地区：

```json
{}
```

按三位字母代码查询美国：

```json
{
  "alpha3": "USA"
}
```

按三位数字代码查询美国：

```json
{
  "numeric": "840"
}
```

按大洲查询：

```json
{
  "continentCode": "NA"
}
```

按默认币种查询：

```json
{
  "currencyAlpha3Code": "USD"
}
```

组合查询：

```json
{
  "continentCode": "NA",
  "currencyAlpha3Code": "USD"
}
```

### 11.6 实际 HTTP 请求示例

```http
POST /api/rest/iso/v1/countries/query HTTP/1.1
Host: api.example.com
Content-Type: application/json
authorization: Bearer <jwt-token>

{
  "data": "<compact encrypted payload>"
}
```

### 11.7 成功响应

平台响应外层：

```json
{
  "code": "T200",
  "message": "Success",
  "data": "<compact encrypted response>"
}
```

商户使用商户响应私钥解密 `data` 后得到：

```json
[
  {
    "alpha2": "US",
    "alpha3": "USA",
    "numeric": "840",
    "englishName": "United States of America",
    "shortEnglishName": "United States",
    "chineseName": "美国",
    "continentCode": "NA",
    "continentName": "北美洲",
    "flagEmoji": "🇺🇸",
    "primaryLanguageCode": "en",
    "primaryLanguageEnglish": "English",
    "primaryLanguageChinese": "英语",
    "currencyAlpha3Code": "USD"
  }
]
```

响应字段说明：

| 字段 | 类型 | 是否可能为空 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| `alpha2` | string | 否 | `US` | ISO 3166-1 alpha-2 两位字母代码 |
| `alpha3` | string | 否 | `USA` | ISO 3166-1 alpha-3 三位字母代码 |
| `numeric` | string | 否 | `840` | ISO 3166-1 numeric 三位数字代码 |
| `englishName` | string | 否 | `United States of America` | 国家或地区英文全称 |
| `shortEnglishName` | string | 否 | `United States` | 国家或地区英文简称 |
| `chineseName` | string | 否 | `美国` | 国家或地区中文名称 |
| `continentCode` | string | 否 | `NA` | 七大洲代码 |
| `continentName` | string | 否 | `北美洲` | 七大洲中文名称 |
| `flagEmoji` | string | 是 | `🇺🇸` | 国家或地区图标 |
| `primaryLanguageCode` | string | 是 | `en` | 主要语言代码 |
| `primaryLanguageEnglish` | string | 是 | `English` | 主要语言英文名称 |
| `primaryLanguageChinese` | string | 是 | `英语` | 主要语言中文名称 |
| `currencyAlpha3Code` | string | 是 | `USD` | 默认币种 ISO 4217 三位字母代码 |

### 11.8 异常场景

| 场景 | 示例 | code | message |
| --- | --- | --- | --- |
| 缺少 `authorization` 请求头 | 请求头未传 `authorization` | `F401001` | `Authorization required` |
| JWT 签名失败 | 使用错误 `merchantKey` 签名 | `F401007` | `Authorization JWT signature verification failed` |
| 商户不存在或状态不可用 | JWT 中 `merchantId` 不存在 | `F401009` | `Merchant is invalid or unavailable` |
| `data` 缺失或无法解密 | 请求体未传 `data` 或密文被篡改 | `F402003` | `Encrypted request data is invalid` |
| `alpha2` 格式非法 | `{"alpha2":"USA"}` | `F402001` | `Invalid request parameter:alpha2 must be ISO 3166-1 alpha-2 uppercase code` |
| `numeric` 格式非法 | `{"numeric":"84"}` | `F402001` | `Invalid request parameter:numeric must be ISO 3166-1 three-digit code` |
| `continentCode` 格式非法 | `{"continentCode":"XX"}` | `F402001` | `Invalid request parameter:continentCode must be one of AS, EU, AF, NA, SA, OC, AN` |
| 商户密钥配置缺失 | 商户未配置平台密钥或响应公钥 | `F409` | `Merchant config not found` |
| 服务内部异常 | 数据库或系统异常 | `F500` | `Internal Server Error` |

参数错误响应示例：

```json
{
  "code": "F402001",
  "message": "Invalid request parameter:alpha2 must be ISO 3166-1 alpha-2 uppercase code"
}
```

认证失败响应示例：

```json
{
  "code": "F401007",
  "message": "Authorization JWT signature verification failed"
}
```

## 12. 查询币种列表

### 12.1 接口说明

商户查询支付框架当前支持的 ISO 4217 币种信息。该接口可用于交易币种校验、金额小数位校验、最小金额校验、商户后台币种展示等场景。

如果商户不传任何查询条件，即明文业务 JSON 为 `{}`，平台返回全部启用币种。

### 12.2 接口地址

```http
POST /api/rest/iso/v1/currencies/query
```

完整示例：

```http
POST https://api.example.com/api/rest/iso/v1/currencies/query
```

### 12.3 请求头

| Header | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `Content-Type` | M | `application/json` | 固定值 |
| `authorization` | M | `Bearer {jwt}` | 商户 JWT |

### 12.4 明文请求参数

商户加密前的业务 JSON 支持以下字段。多个字段同时传入时，平台按 AND 关系组合过滤。

| 字段 | 类型 | 必填 | 最大长度 | 示例 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `alphabeticCode` | string | O | 3 | `USD` | ISO 4217 三位字母币种代码 |
| `numericCode` | string | O | 3 | `840` | ISO 4217 三位数字币种代码 |
| `englishName` | string | O | 128 | `US Dollar` | 币种英文名称，支持包含匹配 |
| `chineseName` | string | O | 128 | `美元` | 币种中文名称，支持包含匹配 |
| `currencySymbol` | string | O | 16 | `$` | 币种符号或展示图标 |

说明：ISO 4217 不存在标准两位字母币种代码。商户不要使用国家 alpha-2 代码作为币种代码。

说明：查询全部币种时，商户需要把明文业务 JSON 写为 `{}` 后再加密，不要传空请求体，也不要把字段传为空字符串。

### 12.5 明文请求示例

查询全部币种：

```json
{}
```

按三位字母代码查询美元：

```json
{
  "alphabeticCode": "USD"
}
```

按三位数字代码查询美元：

```json
{
  "numericCode": "840"
}
```

按中文名称查询：

```json
{
  "chineseName": "人民币"
}
```

按币种符号查询：

```json
{
  "currencySymbol": "$"
}
```

### 12.6 实际 HTTP 请求示例

```http
POST /api/rest/iso/v1/currencies/query HTTP/1.1
Host: api.example.com
Content-Type: application/json
authorization: Bearer <jwt-token>

{
  "data": "<compact encrypted payload>"
}
```

### 12.7 成功响应

平台响应外层：

```json
{
  "code": "T200",
  "message": "Success",
  "data": "<compact encrypted response>"
}
```

商户使用商户响应私钥解密 `data` 后得到：

```json
[
  {
    "alphabeticCode": "USD",
    "numericCode": "840",
    "englishName": "US Dollar",
    "chineseName": "美元",
    "defaultFractionDigits": 2,
    "minorUnitMultiplier": 100,
    "minimumAmount": 0.010000,
    "currencySymbol": "$"
  }
]
```

响应字段说明：

| 字段 | 类型 | 是否可能为空 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| `alphabeticCode` | string | 否 | `USD` | ISO 4217 三位字母币种代码 |
| `numericCode` | string | 否 | `840` | ISO 4217 三位数字币种代码 |
| `englishName` | string | 否 | `US Dollar` | 币种英文名称 |
| `chineseName` | string | 否 | `美元` | 币种中文名称 |
| `defaultFractionDigits` | integer | 否 | `2` | 默认辅币位。`0` 表示无小数位，如 JPY |
| `minorUnitMultiplier` | integer | 否 | `100` | 主币转换为最小辅币单位的倍数。USD 为 100，JPY 为 1 |
| `minimumAmount` | decimal | 否 | `0.010000` | 最小金额单位 |
| `currencySymbol` | string | 是 | `$` | 币种符号或展示图标 |

### 12.8 完整调用示例

查询 USD 的明文业务参数：

```json
{
  "alphabeticCode": "USD"
}
```

商户处理步骤：

1. 使用 `merchantKey` 生成 JWT，并放入 `authorization: Bearer {jwt}` 请求头。
2. 随机生成 AES-256-GCM 会话密钥和 IV。
3. 使用 AES 会话密钥加密 `{"alphabeticCode":"USD"}`。
4. 使用平台公钥加密 AES 会话密钥。
5. 把五段式密文放入外层 `data` 字段。
6. 平台返回 `T200` 后，商户使用商户响应私钥解密响应 `data`。

HTTP 请求结构：

```http
POST /api/rest/iso/v1/currencies/query HTTP/1.1
Host: api.example.com
Content-Type: application/json
authorization: Bearer <jwt-token>

{
  "data": "<compact encrypted payload>"
}
```

平台响应结构：

```json
{
  "code": "T200",
  "message": "Success",
  "data": "<compact encrypted response>"
}
```

### 12.9 异常场景

| 场景 | 示例 | code | message |
| --- | --- | --- | --- |
| 缺少 `authorization` 请求头 | 请求头未传 `authorization` | `F401001` | `Authorization required` |
| JWT 已过期 | `exp` 早于当前时间 | `F401003` | `Authorization JWT exp is invalid or expired` |
| JWT 签名失败 | 使用错误 `merchantKey` 签名 | `F401007` | `Authorization JWT signature verification failed` |
| `data` 缺失或无法解密 | 请求体未传 `data` 或密文被篡改 | `F402003` | `Encrypted request data is invalid` |
| `alphabeticCode` 格式非法 | `{"alphabeticCode":"US"}` | `F402001` | `Invalid request parameter:alphabeticCode must be ISO 4217 alphabetic code` |
| `numericCode` 格式非法 | `{"numericCode":"84"}` | `F402001` | `Invalid request parameter:numericCode must be ISO 4217 three-digit numeric code` |
| `currencySymbol` 过长 | `{"currencySymbol":"12345678901234567"}` | `F402001` | `Invalid request parameter:currencySymbol length must be less than or equal to 16` |
| 商户密钥配置缺失 | 商户未配置平台密钥或响应公钥 | `F409` | `Merchant config not found` |
| 服务内部异常 | 数据库或系统异常 | `F500` | `Internal Server Error` |

参数错误响应示例：

```json
{
  "code": "F402001",
  "message": "Invalid request parameter:alphabeticCode must be ISO 4217 alphabetic code"
}
```

## 13. 对接检查清单

| 检查项 | 必须 | 说明 |
| --- | --- | --- |
| HTTPS | M | 生产环境必须使用 HTTPS |
| `merchantId` | M | JWT Payload 必须携带 |
| `merchantKey` | M | 只用于 JWT HS256 签名 |
| 平台公钥 | M | 只用于加密 AES 会话密钥 |
| 商户响应私钥 | M | 只用于解密平台响应 `data` |
| JWT 有效期 | M | 不超过 180 秒 |
| `jti` 唯一 | M | 防重放，建议使用订单号或请求号 |
| AES key 随机 | M | 每次请求重新生成 |
| IV 随机 | M | 每次请求重新生成 |
| 敏感日志脱敏 | M | 不打印密钥、JWT、卡号、CVV、密码 |
| 金额类型 | M | 使用十进制类型 |
| 币种代码 | M | 使用 ISO 4217 三位字母代码 |
| 国家代码 | O | 唯一匹配建议使用 ISO 3166 alpha-3 或 numeric |

## 14. 错误码

### 14.1 错误码设计原则

| 前缀 | 含义 | 说明 |
| --- | --- | --- |
| `T` | Transaction accepted or successful | 请求成功、已受理、处理中或待确认。商户不应简单按 HTTP 200 判断交易成功，应以 `code` 为准 |
| `F` | Functional failure | 商户可感知的失败，包括鉴权失败、参数错误、商户配置错误、业务拒绝、系统错误 |
| `Z` | Message or protocol failure | 报文解析、响应解析、渠道协议等技术处理异常 |

### 14.2 状态类响应码

| code | message | 说明 |
| --- | --- | --- |
| `T200` | `Success` | 请求或交易成功 |
| `T201` | `Accepted` | 请求已受理，最终结果以查询或回调为准 |
| `T202` | `Processing` | 请求处理中，商户不要重复提交同一请求 |
| `T203` | `Pending` | 交易结果待确认，最终结果以查询或回调为准 |
| `T206` | `Partially accepted` | 批量或组合请求部分受理 |
| `F207` | `Issuer or acquirer rejected the transaction` | 发卡行、收单行、卡组织或上游拒绝 |
| `F210` | `Risk blocked` / 平台选定的商户可见拒绝文案 | 内风控拒绝固定返回 `Risk blocked`；其他平台或渠道拒绝返回对应商户可见文案 |

### 14.3 鉴权类错误码

| code | message | 场景 |
| --- | --- | --- |
| `F401` | `Unauthorized` | 通用未授权 |
| `F401001` | `Authorization required` | 缺少或未正确提供 `authorization` 请求头 |
| `F401002` | `Authorization JWT is invalid, HS256 is required` | JWT Header 类型或算法非法 |
| `F401003` | `Authorization JWT exp is invalid or expired` | JWT 过期或 `exp` 非法 |
| `F401004` | `Authorization JWT iat is invalid` | JWT `iat` 非法 |
| `F401005` | `Authorization JWT iss is invalid` | JWT `iss` 非法 |
| `F401006` | `Authorization JWT aud is invalid` | JWT `aud` 非法 |
| `F401007` | `Authorization JWT signature verification failed` | JWT 签名验签失败 |
| `F401008` | `Merchant signing key is not configured` | 商户未配置 `merchantKey` |
| `F401009` | `Merchant is invalid or unavailable` | 商户号不存在、状态不可用或与请求不匹配 |

### 14.4 参数和报文类错误码

| code | message | 场景 |
| --- | --- | --- |
| `F400` | `Bad request` | 请求不符合 OpenAPI 协议 |
| `F402001` | `Invalid request parameter` | 请求参数值或格式非法 |
| `F402002` | `Required request parameter is missing` | 必填参数缺失 |
| `F402003` | `Encrypted request data is invalid` | 请求体 `data` 缺失、格式非法、无法解密或 GCM Tag 校验失败 |
| `F403` | `Forbidden` | 已认证商户无权访问当前资源 |
| `F404` | `Not found` | 请求地址不存在 |
| `F405` | `Method Not Allowed` | 请求方法不支持 |
| `F429` | `Too many requests` | 请求频率超过平台限制 |

### 14.5 商户配置和业务类错误码

| code | message | 场景 |
| --- | --- | --- |
| `F409` | `Merchant config not found` | 商户配置或密钥配置不存在 |
| `F410` | `Card not support` | 卡类型或卡品牌不支持 |
| `F411` | `Transaction currency is not supported` | 交易币种不支持 |
| `F412` | `Transaction type is not supported` | 交易类型不支持 |
| `F413` | `Unsupported card brands` | 卡品牌不支持 |
| `F510` | `Order already exist` | 商户订单号已存在 |
| `F511` | `Order does not exist` | 订单不存在 |
| `F512` | `The search result set is invalid/does not exist` | 查询结果不存在 |
| `F515` | `transactionId repeat` | 商户交易号重复 |

### 14.6 系统和协议类错误码

| code | message | 场景 |
| --- | --- | --- |
| `F500` | `Internal Server Error` | 平台内部异常；部分通用异常路径可能返回大小写不同的 `Internal server error`，商户应按 code 处理 |
| `F502` | `Bad gateway` | 上游服务不可用或响应异常 |
| `F503` | `The network is busy, please try again later` | 服务繁忙，可稍后重试 |
| `Z605` | `Request parse error` | 请求报文解析失败 |
| `Z606` | `Response parse error` | 响应报文解析失败 |
