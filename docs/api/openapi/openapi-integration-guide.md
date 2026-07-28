# OpenAPI 商户接入文档

## 1. 文档说明

### 1.1 文档版本记录

| 版本 | 日期 | 作者 | 说明 |
| --- | --- | --- | --- |
| v1.0.0 | 2026-06-03 | scott | 创建商户 OpenAPI 对接文档，包含鉴权、加密、响应解密、ISO 国家地区和币种查询接口 |
| v1.1.0 | 2026-06-12 | scott | 拆分 ISO 对外 API 控制器，补充响应模型和 HTTP Method 规范 |
| v1.2.4 | 2026-07-27 | scott | 补充 Hosted Checkout 收银台创建接口、SDK 调用示例和收银台前端地址配置来源 |
| v1.2.3 | 2026-07-17 | scott | 明确退款最小请求参数、可选币种和商户订单号校验规则，以及退款响应累计金额口径 |
| v1.2.2 | 2026-07-14 | scott | 补充 MPGS 渠道订单映射、渠道回调终态推进和商户异步通知说明 |
| v1.2.1 | 2026-07-14 | scott | 明确收单交易订单标识、MPGS 订单号映射和后续动作按本次交易时间分表规则 |
| v1.2.0 | 2026-07-14 | scott | 补充收单交易 V1 接口清单，移除早期 V2 测试入口说明，说明 transaction_id 与 transaction_date_time 后续动作定位规则 |

### 1.2 适用范围

本文档适用于商户系统通过 OpenAPI 接入支付框架的服务端接口。当前已开放接口：

| 接口分类 | 接口名称 | 接口版本 |
| --- | --- | --- |
| ISO 字典 | 查询国家地区列表 | v1 |
| ISO 字典 | 查询币种列表 | v1 |
| 支付 | 一步支付、授权、预授权、增量授权、请款、退款、撤销、查询 | v1 |
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
4. 商户响应私钥只由商户保存，平台不需要也不应该持有该私钥。

### 3.2 平台保存的密钥

| 名称 | 平台用途 | 是否下发商户 |
| --- | --- | --- |
| `merchantKey` | 根据 `merchantId` 查询后校验 JWT 签名 | 是 |
| 平台私钥 | 解密商户请求体中的 AES 会话密钥 | 否 |
| 商户响应公钥 | 加密平台响应 `data` | 否，商户开户时登记或平台生成后保存 |

### 3.3 测试商户固定密钥

以下密钥仅用于本文档和本地测试对照，生产环境严禁使用。

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

所有 OpenAPI 请求体外层固定为：

```json
{
  "data": "<compact encrypted payload>"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `data` | string | M | 五段式加密报文 |

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
| `F210` | `Rejected` | 平台规则或渠道规则拒绝 | 按失败处理 |

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

1. 先判断 `code` 是否为 `T200`。
2. 如果成功，取出 `data`。
3. 使用商户响应私钥解密第二段 `encryptedKey` 得到 AES key。
4. 使用 AES key、第三段 `iv`、第四段 `cipherText`、第五段 `tag` 解密出明文业务响应。

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

## 10. 当前开放接口清单

| 接口名称 | Method | Path | 说明 |
| --- | --- | --- | --- |
| 查询国家地区列表 | POST | `/api/rest/iso/v1/countries/query` | 加密条件查询系统支持的 ISO 3166 国家地区 |
| 查询币种列表 | POST | `/api/rest/iso/v1/currencies/query` | 加密条件查询系统支持的 ISO 4217 币种 |
| 创建一步支付 | POST | `/api/rest/payment/v1/payment` | 创建一笔一步支付交易，通常由渠道一次完成授权和请款 |
| 创建收单授权 | POST | `/api/rest/payment/v1/authorization` | 创建一笔收单授权交易 |
| 创建预授权 | POST | `/api/rest/payment/v1/pre-authorization` | 创建一笔预授权交易 |
| 创建增量授权 | POST | `/api/rest/payment/v1/incremental-authorization` | 对同一原始交易追加授权额度 |
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

首次类交易包括 `/payment`、`/authorization`、`/pre-authorization`，通常需要 `orderInfo`、`cardInfo` 和 `billingCardHolderInfo`，不要求商户传平台交易 ID，也不要求商户传 `transactionInfo.cardBrand`。平台会根据卡 BIN 库和支付方式能力识别卡品牌，并在响应的 `transactionInfo.cardBrand` 中按统一枚举返回。后续资金动作包括 `/incremental-authorization`、`/capture`、`/refund`、`/void`，平台会先按 `sourceTransactionId` 定位原交易，再校验交易类型、交易状态、币种和可用金额，校验通过后才会请求渠道。

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
| `transactionInfo.message` | string | 当前交易动作商户响应描述；失败时返回平台统一模糊失败原因，不返回渠道真实失败信息 |
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

商户应以解密后的 `transactionInfo.code/message` 判断当前动作的商户侧结果，并保存 `transactionInfo.transactionId` 作为后续请款、退款、撤销、查询的源交易标识。HTTP 200 或外层 `T200` 只代表平台成功处理请求，不等于渠道资金结果必然成功。支付失败时，平台当前统一返回 `transactionInfo.message = "The transaction was declined; please contact your card issuer or try again."`，渠道真实失败码和失败描述仅用于平台后台排查。

退款响应中，`orderInfo.amount` 和 `billingInfo.labelAmount/transactionAmount` 表示本次退款金额；`orderInfo.totalAuthorizedAmount` 和 `orderInfo.totalCapturedAmount` 表示原支付或授权生命周期的成功金额；`orderInfo.totalRefundAmount` 表示包含本次退款在内的累计退款金额。例如一步支付 102 USD 后退款 22 USD，退款响应应展示 `amount=22`、`totalAuthorizedAmount=102`、`totalCapturedAmount=102`、`totalRefundAmount=22`。

当商户在 `transactionInfo.callbackUrl` 或平台商户配置中登记回调地址时，平台会在交易进入终态后创建商户通知任务并按重试策略推送结果。商户通知只包含商户可见字段和模糊失败原因；渠道真实失败码、收单响应和内部排查信息只在平台后台交易详情、渠道交互日志和渠道回调记录中展示。

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

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `merchantInfo.merchantId` | M | 平台商户号，必须与 JWT 中的商户号一致 |
| `orderInfo.orderNo` | M | 商户业务订单号 |
| `orderInfo.orderId` | M | 商户本次创建收银台请求唯一标识，SDK 使用该字段作为 JWT `jti` |
| `orderInfo.amount` | M | 订单金额，主币种单位，必须大于 0 |
| `orderInfo.currency` | M | ISO 4217 三位大写币种 |
| `checkoutInfo.allowedPaymentMethods` | M | 商户允许的支付方式快照，当前 MPGS 收银台以 `BANK_CARD + MPGS` 为例 |
| `checkoutInfo.returnUrl` | M | 付款人点击结果页返回商户网站的地址 |
| `checkoutInfo.cancelUrl` | O | 付款人取消时返回商户网站的地址 |
| `checkoutInfo.notifyUrl` | O | 商户异步通知地址，终态通知规则与常规支付通知保持一致 |
| `checkoutInfo.checkoutDomain` | O | 兼容旧字段；平台生成 `checkoutUrl` 时忽略该字段 |

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
| 缺少 `authorization` 请求头 | 请求头未传 `authorization` | `F401001` | `Authorization header is missing` |
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
| 缺少 `authorization` 请求头 | 请求头未传 `authorization` | `F401001` | `Authorization header is missing` |
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
| `F210` | `Rejected` | 平台规则或渠道规则拒绝 |

### 14.3 鉴权类错误码

| code | message | 场景 |
| --- | --- | --- |
| `F401` | `Unauthorized` | 通用未授权 |
| `F401001` | `Authorization header is missing` | 缺少 `authorization` 请求头 |
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
| `F404` | `Not found` | 请求地址不存在 |
| `F405` | `Method Not Allowed` | 请求方法不支持 |

### 14.5 商户配置和业务类错误码

| code | message | 场景 |
| --- | --- | --- |
| `F409` | `Merchant config not found` | 商户配置或密钥配置不存在 |
| `F410` | `Card not support` | 卡类型或卡品牌不支持 |
| `F411` | `transaction unsupported currency` | 交易币种不支持 |
| `F412` | `transaction unsupported transactionType` | 交易类型不支持 |
| `F413` | `Unsupported card brands` | 卡品牌不支持 |
| `F510` | `Order already exist` | 商户订单号已存在 |
| `F511` | `Order does not exist` | 订单不存在 |
| `F512` | `The search result set is invalid/does not exist` | 查询结果不存在 |
| `F515` | `transactionId repeat` | 商户交易号重复 |

### 14.6 系统和协议类错误码

| code | message | 场景 |
| --- | --- | --- |
| `F500` | `Internal Server Error` | 平台内部异常 |
| `F502` | `Bad gateway` | 上游服务不可用或响应异常 |
| `F503` | `The network is busy, please try again later` | 服务繁忙，可稍后重试 |
| `Z605` | `Request parse error` | 请求报文解析失败 |
| `Z606` | `Response parse error` | 响应报文解析失败 |
