# OpenAPI 鉴权与加密流程

## 1. 方案目标

本文定义 `service-openapi` 的商户对接安全方案。当前支付框架统一使用下面的组合：

```text
HTTPS + JWT HS256 + RSA-OAEP-256 + AES-256-GCM + 响应 data 强制加密
```

设计目标：

1. 商户身份通过 JWT HS256 认证，JWT 只做认证和防篡改，不承载敏感业务明文。
2. 请求体使用混合加密：AES-256-GCM 加密业务 JSON，RSA-OAEP-256 只加密一次性 AES 会话密钥。
3. 每个商户拥有独立的平台请求体 RSA 密钥对，平台按 `merchantId` 查询密钥，不再依赖 `keyId` 或 `kid`。
4. 响应体 `data` 必须加密，`code` 和 `message` 保持明文，便于商户快速判断结果。
5. AES key 和 IV 每次请求随机生成，不落库、不复用。

## 2. 密钥清单

### 2.1 商户侧保存

| 密钥 | 用途 | 说明 |
| --- | --- | --- |
| `merchantKey` | 生成 JWT HS256 签名 | 平台开户生成后交付商户，只允许保存在商户服务端 |
| 平台公钥 | 加密请求体 AES 会话密钥 | 每个商户独立一把平台公钥，平台按 `merchantId` 关联 |
| 商户响应私钥 | 解密响应体 `data` | 商户独立保存，平台不保存、不接触 |

### 2.2 平台侧保存

| 密钥 | 用途 | 说明 |
| --- | --- | --- |
| `merchantKey` | 验证 JWT HS256 签名 | 按 `merchantId` 查询当前启用密钥 |
| 平台私钥 | 解密请求体 AES 会话密钥 | 与商户侧平台公钥成对，生产建议进入 KMS/HSM |
| 商户响应公钥 | 加密响应体 `data` | 商户提供或平台开户流程生成后交付私钥给商户 |

商户对接时真正要使用的是 3 项：`merchantKey`、平台公钥、商户响应私钥。平台私钥不下发，商户响应公钥由平台保存用于响应加密。

## 3. JWT 请求头

商户请求必须携带 `authorization` 请求头，兼容裸 JWT 和 `Bearer <jwt>`。

```http
Content-Type: application/json
authorization: <jwt-token>
```

JWT Header 固定：

```json
{
  "typ": "JWT",
  "alg": "HS256"
}
```

JWT Payload 固定：

```json
{
  "aud": ["gateway"],
  "iss": "merchant",
  "jti": "20250116140182865587",
  "iat": 1704960018,
  "exp": 1704960198,
  "merchantId": "200045"
}
```

校验规则：

| 字段 | 规则 |
| --- | --- |
| `aud` | 必须包含 `gateway` |
| `iss` | 必须等于 `merchant` |
| `jti` | 必填，建议使用商户订单号；生产接 Redis 后写入防重放 |
| `iat` | 秒级时间戳，不能明显晚于服务器时间 |
| `exp` | 必须大于当前时间，且 `exp - iat <= 180` 秒 |
| `merchantId` | 必填，用于查询商户基础信息、`merchantKey` 和密钥材料 |

服务端使用 Hutool JWT 的 `JWTSignerUtil.hs256(...)` 做标准 HS256 验签，生成的 token 可在 [jwt.io](https://jwt.io/) 使用 HS256 解析和验证。

## 4. 请求体加密

商户请求体统一为：

```json
{
  "data": "<compact encrypted payload>"
}
```

`data` 是五段式 compact 密文：

```text
base64url(protectedHeader).base64url(encryptedKey).base64url(iv).base64url(cipherText).base64url(tag)
```

受保护头只描述算法，不携带 `kid`：

```json
{
  "typ": "PAYMENT-PAYLOAD",
  "alg": "RSA-OAEP-256",
  "enc": "A256GCM"
}
```

五段说明：

| 片段 | 说明 |
| --- | --- |
| `protectedHeader` | 参与 AES-GCM AAD 校验，防止算法头被篡改 |
| `encryptedKey` | 使用该商户的平台公钥加密后的 AES-256 会话密钥 |
| `iv` | AES-GCM 12 字节随机 IV |
| `cipherText` | AES-GCM 加密后的业务 JSON |
| `tag` | AES-GCM 128 bit 认证标签 |

商户端流程：

1. 组装业务 JSON。
2. 随机生成 32 字节 AES key。
3. 随机生成 12 字节 IV。
4. 使用 AES-256-GCM 加密业务 JSON，AAD 使用 `base64url(protectedHeader)`。
5. 使用平台公钥执行 RSA-OAEP-SHA256 加密 AES key。
6. 拼接五段 compact 密文并放入 `data`。

服务端流程：

1. 从 JWT 中解析 `merchantId`。
2. 按 `merchantId` 查询平台私钥。
3. 使用平台私钥解密 AES key。
4. 使用 AES key、IV、AAD 和 tag 解密业务 JSON。
5. 转换为 `@VerificationAndProcessing(dataReceiver = XxxDTO.class)` 指定的 DTO。
6. 执行 Bean Validation 参数校验。

## 5. 响应加密

响应加密默认启用，所有 OpenAPI 控制器返回的 `CommonResult.data` 都会被加密。

响应格式：

```json
{
  "code": "T200",
  "message": "SUCCESS",
  "data": "<compact encrypted response>"
}
```

规则：

1. `code` 和 `message` 保持明文，方便商户快速判断成功或失败。
2. `data` 使用商户响应公钥加密。
3. 商户使用自己的响应私钥解密 `data`。
4. 失败响应如果 `data == null`，不做加密，直接返回错误码和错误说明。
5. 平台不保存商户响应私钥。

## 6. 数据库设计

当前测试 SQL 会创建或补齐以下基础表：

| 表 | 关键字段 | 用途 |
| --- | --- | --- |
| `base_merchant_info` | `merchant_id`、`merchant_name`、`merchant_status`、`country_code`、`gmt_create`、`gmt_modified`、`deleted` | 商户基础资料 |
| `base_merchant_jwt_key` | `merchant_id`、`key_version`、`merchant_key`、`algorithm`、`enabled`、`effective_time`、`expire_time` | 按商户号查询 JWT 验签密钥 |
| `base_platform_payload_key` | `merchant_id`、`public_key_x509_base64`、`private_key_pkcs8_base64`、`algorithm`、`key_size`、`enabled`、`gmt_create`、`gmt_modified`、`deleted` | 每个商户独立的平台请求体 RSA 密钥对 |
| `base_merchant_response_key` | `merchant_id`、`public_key_x509_base64`、`algorithm`、`key_size`、`enabled`、`gmt_create`、`gmt_modified`、`deleted` | 商户响应公钥，平台用于加密响应 `data` |

测试 SQL 位置：

```text
service-openapi/src/test/resources/sql/openapi-merchant-security-schema.sql
```

如果本地数据库曾执行过旧版本脚本，测试 SQL 会在建表后自动删除历史 `platform_key_id`、`response_key_id`、
`platform_payload_key_id` 等无 KeyId 方案不再使用的列。空库场景下直接创建新表即可。

## 7. 开户与密钥生成入口

平台测试开户入口：

```java
MerchantSecurityMaterialDTO material =
        merchantSecurityService.provisionMerchantSecurityMaterial(seedDTO);
```

底层密钥生成入口：

```text
component-library/component-security/src/main/java/com/scott/payment/component/security/key/OpenApiKeyMaterialFactory.java
```

本地测试中，平台开户流程会一次性生成：

1. 商户 JWT 密钥 `merchantKey`。
2. 商户独立的平台请求体 RSA 公私钥。
3. 商户响应 RSA 公私钥。

交付边界：

1. 交付商户：`merchantKey`、平台公钥、商户响应私钥。
2. 平台保留：`merchantKey`、平台私钥、商户响应公钥。
3. 双方关联：全部通过 `merchantId` 关联，不在请求体或响应体中传 `keyId`。

## 8. OpenAPI 授权接口示例

```http
POST /api/rest/payment/v1/authorization
Content-Type: application/json
authorization: <jwt-token>
```

请求体：

```json
{
  "data": "base64url(header).base64url(encryptedKey).base64url(iv).base64url(cipherText).base64url(tag)"
}
```

`data` 解密后的授权明文示例：

```json
{
  "merchantInfo": {
    "merchantId": "200045",
    "subMerchantInfo": {
      "subName": "John",
      "subCompanyName": "JohnCompany",
      "subId": "123456789111111",
      "subPostal": "SW1 1AA",
      "subStreet": "Regent Street",
      "subCity": "London",
      "subState": "AL",
      "subCountryCode": "USA",
      "merchantCategory": "5311"
    }
  },
  "orderInfo": {
    "amount": 12389.45,
    "currency": "USD",
    "tradeNo": "20250116140182865587"
  },
  "billingCardHolderInfo": {
    "firstName": "John",
    "lastName": "Tom",
    "phone": "+55-5058149876",
    "email": "username@example.com",
    "country": "USA",
    "state": "AL",
    "city": "city name",
    "street": "street name",
    "postal": "03400"
  },
  "cardInfo": {
    "cardNo": "5387380678556554",
    "expirationMonth": "03",
    "expirationYear": "2028",
    "securityCode": "123"
  },
  "threeDSInfo": {
    "eci": "212",
    "cavv": "kANiJlhEqL/yaEfVxr/BUoQBicnh",
    "dsTransactionId": "b96c957d-daa1-4b7f-b8b4-373fb9dec47b",
    "threeDsVersion": "2.2.0"
  },
  "transactionInfo": {
    "transactionId": "txn-20250116140182865587",
    "description": "authorize request"
  }
}
```

## 9. 服务端处理链路

```text
OpenApiHeaderInterceptor
  -> MerchantJwtVerifier
  -> OpenApiRequestBodyAdvice
  -> OpenApiPayloadDecoder
  -> OpenApiValidator
  -> OpenApiRequestArgumentResolver
  -> OpenApiPaymentController
  -> PaymentInternalClient
  -> service-payment
  -> OpenApiResponseBodyAdvice
```

关键处理：

1. JWT 缺失、签名错误、过期、`aud/iss/jti/merchantId` 不合法，统一返回 `F401`。
2. Redis 可用时，`OpenApiJwtReplayProtectionService` 会把 `jti` 写入 `payment:openapi:jwt:jti:{merchantId}:{jti}`，重复命中视为重放请求。
3. 请求体缺失、密文格式错误、RSA 解密失败、AES-GCM tag 校验失败，统一返回稳定错误码。
4. DTO 字段不合法，由统一异常处理返回 `CommonResult` JSON。
5. OpenAPI 正常响应会被 `OpenApiResponseBodyAdvice` 加密 `data`。

## 10. cardInfo 是否二次加密

默认不要求商户对 `cardInfo` 再做字段级二次加密。

原因：

1. 整个业务 JSON 已经由 AES-256-GCM 加密，公网传输中不会暴露 PAN/CVV。
2. 字段级二次加密会显著增加 PHP、Go、C、Java 等多语言商户的接入复杂度。
3. 真正关键的是服务端解密后的治理：日志脱敏、CVV 不落库、PAN tokenization 或字段级加密、MQ 和异常中禁止输出敏感字段。

高安全商户后续可以单独支持 `cardInfo` 子信封，但默认授权接口不强制。

## 11. 测试入口

当前测试覆盖了开户、商户材料查询、服务端材料查询、请求加密、JWT 验签、请求解密、接口调用、响应加密、商户响应解密和异常分支。

```text
service-openapi/src/test/java/com/scott/payment/openapi/MerchantClientRequestWithoutDatabaseTests.java
service-openapi/src/test/java/com/scott/payment/openapi/MerchantClientResponseDecryptWithoutDatabaseTests.java
service-openapi/src/test/java/com/scott/payment/openapi/OpenApiSecurityFlowTests.java
service-openapi/src/test/java/com/scott/payment/openapi/MerchantSecurityDatabaseFlowTests.java
service-openapi/src/test/java/com/scott/payment/openapi/MerchantOnboardingFlowTests.java
service-openapi/src/test/java/com/scott/payment/openapi/MerchantKeyCryptoUsageTests.java
service-openapi/src/test/java/com/scott/payment/openapi/MerchantOpenApiEndToEndTests.java
```

重点用例：

1. `MerchantClientRequestWithoutDatabaseTests`：不连接数据库，使用固定商户号、merchantKey、平台公私钥，模拟商户封装 JWT、加密请求体并模拟 OpenAPI 服务端验签解密。
2. `MerchantClientResponseDecryptWithoutDatabaseTests`：不连接数据库，使用固定商户响应公私钥，模拟平台加密响应 `data` 和商户解密响应。
3. `OpenApiSecurityFlowTests`：纯单元方式模拟完整安全闭环，不依赖数据库。
4. `MerchantSecurityDatabaseFlowTests`：连接 MySQL，使用 MyBatisPlus 建表、写入商户和密钥，再走完整闭环。
5. `MerchantOpenApiEndToEndTests`：通过 MockMvc 直接调用 `service-openapi`，验证响应 `data` 已加密并可被商户私钥解密。
6. 异常分支覆盖缺少请求头、错误 `merchantKey`、JWT 过期和请求体密文篡改。

执行：

```bash
mvn -pl service-openapi -am test
```

## 12. 日志与安全要求

1. 禁止打印完整卡号、CVV、CAVV、JWT、`merchantKey`、私钥和完整密文。
2. 只允许打印长度、指纹、商户号、订单号、脱敏 PAN 和错误码。
3. AES key 和 IV 每次请求随机生成，用完即丢弃。
4. JWT `jti` 已接入 Redis 防重放；生产环境必须配置 Redis，否则本地无 Redis 的降级 no-op 不应作为生产形态。
5. 密钥生产环境建议进入 KMS/HSM；测试环境明文表仅用于流程验证。

## 13. 参考标准

1. [RFC 7519 - JSON Web Token](https://www.rfc-editor.org/rfc/rfc7519)
2. [RFC 7518 - JSON Web Algorithms](https://www.rfc-editor.org/rfc/rfc7518)
3. [RFC 8017 - PKCS #1 RSA Cryptography Specifications](https://www.rfc-editor.org/rfc/rfc8017)
4. [NIST SP 800-38D - Galois/Counter Mode](https://csrc.nist.gov/pubs/sp/800/38/d/final)
