# OpenAPI 鉴权与加密流程

## 1. 目标

本文定义 `service-openapi` 对外接口的统一安全方案，覆盖商户请求认证、业务报文加密、服务端解密校验、接口调用、响应加密和后续密钥治理。

当前推荐方案：

```text
HTTPS + JWT HS256 + RSA-OAEP-256/AES-256-GCM 混合加密
```

说明：

1. HTTPS 负责传输层保护，所有生产请求必须启用。
2. JWT HS256 负责商户身份、请求时效、请求唯一标识和防重放基础数据。
3. AES-256-GCM 负责业务 JSON 正文的机密性和完整性校验。
4. RSA-OAEP-256 只负责加密一次性 AES 会话密钥，不直接加密大业务报文。
5. `service-openapi` 负责验签、解密、参数校验和入口编排，交易核心仍由 `service-payment` 或 `service-payout` 承接。

## 2. 为什么这样设计

JWT 不是报文加密工具，它的 Payload 只是 Base64Url 编码，任何人拿到 token 都可以解析出字段。因此 JWT 只用于认证和防篡改，不用于保护卡号、CVV、账单地址等敏感数据。

单独使用 RSA2048 加密整个请求体也不合适：RSA 有明文长度限制，性能差，不适合支付接口里的大 JSON。更合理的做法是使用随机 AES key 加密正文，再用 RSA-OAEP 包裹 AES key。

单独使用固定 AES key 也不推荐：跨语言接入虽然简单，但密钥分发、泄露影响面、轮换和商户隔离都更难治理。

最终方案采用行业常见的混合加密结构，Java、PHP、Go、C/OpenSSL 都有成熟库支持：

```text
业务 JSON --AES-256-GCM--> ciphertext
AES key  --RSA-OAEP-256--> encryptedKey
```

## 3. 请求头 JWT

商户请求必须携带 `authorization` 请求头，兼容裸 JWT 和 `Bearer <jwt>` 两种格式。

```http
Content-Type: application/json
authorization: <jwt-token>
```

JWT Header 固定如下：

```json
{
  "typ": "JWT",
  "alg": "HS256"
}
```

JWT Payload 固定字段如下：

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

字段规则：

| 字段 | 说明 | 校验规则 |
| --- | --- | --- |
| `aud` | 接收方 | 必须包含 `gateway` |
| `iss` | 签发方 | 必须等于 `merchant` |
| `jti` | 请求唯一标识 | 必填，建议使用商户订单号；后续写入 Redis 做防重放 |
| `iat` | 签发时间 | 秒级时间戳，不能明显晚于服务器时间 |
| `exp` | 过期时间 | 必须大于当前时间，且 `exp - iat <= 180` 秒 |
| `merchantId` | OPGS 商户号 | 必填，用于查询商户 `merchantKey` |

服务端当前使用 Hutool JWT 的 `JWTSignerUtil.hs256(...)` 进行标准 HS256 验签，生成的 token 可被 [jwt.io](https://jwt.io/) 按 HS256 解析和验证。

## 4. 请求体加密信封

商户请求体统一为：

```json
{
  "data": "<compact encrypted payload>"
}
```

`data` 使用五段式 compact 格式：

```text
base64url(protectedHeader).base64url(encryptedKey).base64url(iv).base64url(cipherText).base64url(tag)
```

五段含义：

| 片段 | 含义 |
| --- | --- |
| `protectedHeader` | 受保护头，参与 AES-GCM AAD 校验 |
| `encryptedKey` | 使用 OPGS 平台 RSA 公钥加密后的 AES 会话密钥 |
| `iv` | AES-GCM 12 字节随机 IV |
| `cipherText` | AES-GCM 加密后的业务 JSON |
| `tag` | AES-GCM 128 bit 认证标签 |

受保护头明文示例：

```json
{
  "typ": "OPGS-PAYLOAD",
  "alg": "RSA-OAEP-256",
  "enc": "A256GCM",
  "kid": "opgs-rsa-2026-q2"
}
```

`kid` 是平台 RSA 密钥编号，用于后续无停机轮换平台密钥。

## 5. 商户请求生成流程

1. 商户组装授权交易业务 JSON。
2. 商户生成 JWT，使用开户时获取的 `merchantKey` 按 HS256 签名。
3. 商户获取 OPGS 平台 RSA 公钥和 `kid`。
4. 商户随机生成 32 字节 AES key 和 12 字节 IV。
5. 商户用 AES-256-GCM 加密业务 JSON，AAD 使用 `base64url(protectedHeader)`。
6. 商户用 RSA-OAEP-SHA256 加密 AES key，得到 `encryptedKey`。
7. 商户拼接五段 compact 密文，放入请求体 `data`。
8. 商户发送请求到网关，网关转发到 `service-openapi`。

授权接口示例：

```http
POST /api/rest/payment/v1/authorization
Content-Type: application/json
authorization: <jwt-token>
```

```json
{
  "data": "eyJ0eXAiOiJPU..."
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
      "subTaxId": "ABC-123456789",
      "subEmail": "John@email.com",
      "subPhone": "+55-5058149876",
      "merchantCategory": "5311",
      "intesCode": "1009",
      "chargeType": "310"
    }
  },
  "orderInfo": {
    "amount": 12389.45,
    "currency": "USD",
    "tradeNo": "20250116140182865587"
  },
  "billingCardHolderInfo": {
    "firstName": "John",
    "lastName": "tom",
    "phone": "+55-5058149876",
    "email": "username@liquido.com",
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
    "transactionId": "20250116140182887083",
    "description": "authorize request"
  }
}
```

## 6. 服务端处理流程

`service-openapi` 当前链路：

```text
OpenApiHeaderInterceptor
    -> MerchantJwtVerifier
    -> OpenApiRequestBodyAdvice
    -> OpenApiPayloadDecoder
    -> OpenApiValidator
    -> OpenApiRequestArgumentResolver
    -> OpenApiPaymentController
    -> OpenApiPaymentService
```

处理步骤：

1. 检查请求是否命中开放 API 路径。
2. 从 `authorization` 解析 JWT。
3. 先读取 JWT 中的 `merchantId`，查询商户 `merchantKey`。
4. 校验 JWT Header：`typ=JWT`、`alg=HS256`。
5. 校验 JWT Signature：使用 `merchantKey` 执行 HS256 验签。
6. 校验 JWT Payload：`aud`、`iss`、`jti`、`iat`、`exp`、`merchantId`。
7. 读取请求体 `data`。
8. 解析 compact 受保护头，按 `kid` 获取平台 RSA 私钥。
9. 使用 RSA-OAEP-SHA256 解出 AES key。
10. 使用 AES-256-GCM 解密业务 JSON，同时校验 tag 和 AAD。
11. 将明文 JSON 转换为 `@VerificationAndProcessing(dataReceiver = XxxDTO.class)` 指定的 DTO。
12. 执行 Bean Validation 参数校验。
13. 将 DTO 注入控制器方法参数。
14. 控制器调用 `service-payment` 或 `service-payout` 完成业务编排。

授权接口代码示例：

```java
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/payment/{version}")
public class OpenApiPaymentController {

    @VerificationAndProcessing(dataReceiver = ApiMerchantPaymentRequestDTO.class)
    @PostMapping("/authorization")
    public CommonResult<PaymentCreateVO> createPayment(HttpServletRequest request,
                                                       @RequestBody String encryptedData,
                                                       ApiMerchantPaymentRequestDTO requestDTO) {
        return CommonResult.success(openApiPaymentService.createPayment(encryptedData, requestDTO));
    }
}
```

## 7. 响应加密策略

当前脚手架测试先返回明文 `CommonResult`，用于验证请求链路。生产建议响应也使用同样的混合加密信封：

```json
{
  "code": "T200",
  "message": "Success",
  "data": "<compact encrypted response>"
}
```

响应加密时：

1. `code` 和 `message` 可以保持明文，方便商户快速判断调用结果。
2. `data` 使用商户 RSA 公钥加密返回业务数据。
3. 平台可以在响应 header 或响应体中附加平台签名，便于商户验证响应来自 OPGS。
4. 回调通知同样遵循 JWT 认证和 `data` 加密规则。

## 8. cardInfo 是否二次加密

默认不要求商户对 `cardInfo` 再做二次加密。

原因：

1. 整个业务 JSON 已经由 AES-256-GCM 加密，`cardInfo` 不会以明文穿过公网。
2. 额外字段级加密会显著提高 PHP、C、Go 等商户接入复杂度。
3. 双层加密会带来更多密钥管理、错误定位和兼容性问题。
4. 真正需要强化的是服务端解密后的安全治理，而不是把接入协议做得过重。

生产落地要求：

1. PAN 只允许脱敏日志，例如 `538738******6554`。
2. CVV/CVC 只允许用于本次授权，授权后禁止存储。
3. 交易核心落库必须做 PAN tokenization 或字段级强加密。
4. MQ、异常、审计日志、业务响应禁止输出完整 PAN 和 CVV。
5. 后续可以为高安全商户提供可选的 `cardInfo` 字段级 JWE 子信封。

## 9. 密钥与防重放

生产环境要求：

1. `merchantKey` 由开户流程生成，按商户独立保存，支持启停和轮换。
2. 平台 RSA 私钥不得硬编码，建议放入 KMS；如果使用 Nacos，必须加密存储并限制权限。
3. 平台 RSA 公钥通过商户后台或安全渠道下发，必须带 `kid`。
4. AES key 每次请求随机生成，不复用。
5. AES-GCM IV 每次请求随机生成，不允许同一个 AES key 下复用 IV。
6. JWT `jti` 写入 Redis，过期时间略大于 JWT 有效期，重复请求直接拒绝。
7. 商户密钥轮换时支持新旧 `merchantKey` 短时间并行校验。
8. 平台 RSA 密钥轮换时通过 `kid` 选择私钥，旧密钥保留到所有商户完成切换。

## 10. 当前测试入口

已在 `service-openapi` 增加安全链路测试：

```text
service-openapi/src/test/java/com/scott/payment/openapi/OpenApiSecurityFlowTests.java
```

测试覆盖：

1. `shouldCreateAndVerifyMerchantJwtByHs256`：验证标准 HS256 JWT 生成和验签。
2. `shouldEncryptAndDecryptOpenApiPayload`：验证 RSA-OAEP-256/AES-256-GCM 加密和解密。
3. `shouldCallAuthorizationApiWithJwtAndEncryptedPayload`：验证授权接口完整调用链路。

执行命令：

```bash
mvn -pl service-openapi -am test
```

## 11. 参考标准

1. [RFC 7519 - JSON Web Token](https://www.rfc-editor.org/rfc/rfc7519)
2. [RFC 7518 - JSON Web Algorithms](https://www.rfc-editor.org/rfc/rfc7518)
3. [RFC 7516 - JSON Web Encryption](https://www.rfc-editor.org/rfc/rfc7516)
4. [RFC 8017 - PKCS #1 RSA Cryptography Specifications](https://www.rfc-editor.org/rfc/rfc8017)
5. [NIST SP 800-38D - Galois/Counter Mode](https://csrc.nist.gov/pubs/sp/800/38/d/final)
