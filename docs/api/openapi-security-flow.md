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
| `merchantId` | 支付平台商户号 | 必填，用于查询商户 `merchantKey` |

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
| `encryptedKey` | 使用支付平台 RSA 公钥加密后的 AES 会话密钥 |
| `iv` | AES-GCM 12 字节随机 IV |
| `cipherText` | AES-GCM 加密后的业务 JSON |
| `tag` | AES-GCM 128 bit 认证标签 |

受保护头明文示例：

```json
{
  "typ": "PAYMENT-PAYLOAD",
  "alg": "RSA-OAEP-256",
  "enc": "A256GCM",
  "kid": "payment-rsa-2026-q2"
}
```

`kid` 是平台 RSA 密钥编号，用于后续无停机轮换平台密钥。

## 5. 密钥类型与生成入口

OpenAPI 默认接入只让商户保存两类长期材料和一类临时密钥。

| 密钥 | 生成方 | 持有方 | 用途 | 是否下发商户 |
| --- | --- | --- | --- | --- |
| `merchantKey` | 支付平台 | 支付平台 + 商户服务端 | 商户生成 JWT HS256；平台验证 JWT | 是 |
| 平台请求体 RSA 公钥 | 支付平台 | 支付平台 + 商户服务端 | 商户加密请求体 AES 会话密钥 | 是 |
| 平台请求体 RSA 私钥 | 支付平台 | 仅支付平台服务端 | 解密商户请求体中的 AES 会话密钥 | 否 |
| AES-256 会话密钥 | 商户每次请求随机生成 | 只在本次请求内存中短暂存在 | 加密业务 JSON 正文 | 不下发、不落库 |

生产推荐权责：

1. 支付平台生成 `merchantKey`，开户时通过安全渠道交付给商户。
2. 支付平台生成平台 RSA 密钥对，只公开平台公钥和 `kid`，平台私钥进入 KMS 或加密配置。
3. AES key 和 IV 每次请求随机生成，用完即丢弃，不允许复用和落库。
4. 响应加密默认暂不要求商户再生成 RSA 密钥对，避免接入复杂度过高；后续如需要对响应或回调 `data` 加密，可以作为增强能力单独开启。

当前代码提供统一生成入口：

```text
component-library/component-security/src/main/java/com/scott/payment/component/security/key/OpenApiKeyMaterialFactory.java
```

本地联调入口示例：

```java
OpenApiKeyMaterialFactory factory = new OpenApiKeyMaterialFactory();

OpenApiMerchantOnboardingMaterial material = factory.generateDemoOnboardingMaterial(
        "200045",
        "payment-rsa-2026-q2"
);
```

单独生成入口：

```java
RsaKeyMaterial platformKey = factory.generatePlatformPayloadRsaKey("payment-rsa-2026-q2");
MerchantOpenApiCredential credential = factory.generateMerchantCredential("200045", platformKey);
```

当前 `service-openapi` 也提供了数据库初始化入口，测试和后续平台开户流程可以直接调用：

```java
MerchantSecurityMaterialDTO material =
        merchantSecurityService.provisionMerchantSecurityMaterial(seedDTO);
```

该方法会写入商户基础信息、商户 JWT 密钥、平台请求体 RSA 密钥；如果传入 `merchantResponseKeyId`，再额外生成商户响应加密公钥。读请求通过 MyBatisPlus 查询对应表，写请求走 `master`，读请求走 `slave` 读组。当前 dev/test 主从均配置到同一个 MySQL，后续生产可把 `slave_1`、`slave_2` 指向真实从库。

生成后交付与保存：

商户只需要拿到并保存下面 2 项：

1. `credential.merchantKey()`：商户 JWT HS256 签名密钥，只允许保存在商户服务端。
2. `credential.platformPublicKeyPem()` 或 `credential.platformPublicKeyX509Base64()` + `credential.platformPayloadKeyId()`：用于加密请求体 `data`。

因此默认接入不是“三把密钥都让商户维护”。商户默认只维护 `merchantKey` 和平台请求体公钥；平台私钥永远不下发。响应加密增强模式可选开启，开启后商户再维护自己生成的响应私钥，平台只保存商户响应公钥。

平台服务端内部保存下面 2 项，绝对不能交给商户；下面的方法名只用于平台开发人员理解生成对象的字段，不是商户对接步骤：

1. `platformKey.privateKeyPem()` 或 `platformKey.privateKeyPkcs8Base64()`：用于解密商户请求体，生产必须进入 KMS 或加密配置。
2. 商户 `merchantKey` 的加密存储值：用于服务端 JWT 验签和密钥轮换。

日志排查只允许打印长度、`kid` 和 `fingerprint(...)` 结果，不允许打印 `merchantKey`、JWT、私钥、完整密文、完整 PAN、CVV 或 CAVV。

### 5.1 推荐表关系

当前已经按下面几张表拆分存储；测试用例会在 `127.0.0.1:3306/payment_acquiring` 中建表并写入测试商户数据：

| 表 | 主键/唯一键 | 核心字段 | 用途 |
| --- | --- | --- | --- |
| `base_merchant_info` | `merchant_id` | `merchant_name`、`merchant_status`、`merchant_category_code`、`platform_payload_key_id`、`response_key_id`、`country_code` | 查询商户是否存在、是否可用，保存外卡收单常用商户基础资料和默认密钥编号 |
| `base_merchant_jwt_key` | `merchant_id + key_version` | `merchant_key`、`algorithm`、`enabled`、`effective_time`、`expire_time` | 服务端按 `merchantId` 找到 merchantKey，完成 JWT HS256 验签 |
| `base_platform_payload_key` | `platform_key_id` | `public_key_x509_base64`、`private_key_pkcs8_base64`、`enabled` | 商户用公钥加密请求，平台用私钥解密请求 |
| `base_merchant_response_key` | `merchant_id + response_key_id` | `public_key_x509_base64`、`enabled` | 响应加密增强模式下，平台用商户响应公钥加密响应 `data` |

关联关系：

1. `base_merchant_info.merchant_id` 关联 `base_merchant_jwt_key.merchant_id`。
2. 请求体 compact header 的 `kid` 关联 `base_platform_payload_key.platform_key_id`。
3. 响应体 compact header 的 `kid` 关联 `base_merchant_response_key.response_key_id`。
4. `base_platform_payload_key.private_key_pkcs8_base64` 是平台保留材料，不能给商户；测试环境明文保存，生产必须进入 KMS、HSM 或加密配置。
5. `base_merchant_response_key` 只保存商户响应公钥；商户响应私钥由商户自己保存，平台不保存。

## 6. 商户请求生成流程

1. 商户组装授权交易业务 JSON。
2. 商户生成 JWT，使用开户时获取的 `merchantKey` 按 HS256 签名。
3. 商户获取支付平台 RSA 公钥和 `kid`。
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

## 7. 服务端处理流程

`service-openapi` 当前链路：

```text
OpenApiHeaderInterceptor
    -> MerchantJwtVerifier
    -> OpenApiRequestBodyAdvice
    -> OpenApiPayloadDecoder
    -> OpenApiValidator
    -> OpenApiRequestArgumentResolver
    -> OpenApiPaymentController
    -> PaymentService
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

    private final PaymentService paymentService;

    @VerificationAndProcessing(dataReceiver = ApiMerchantPaymentRequestDTO.class)
    @PostMapping("/authorization")
    public CommonResult<PaymentCreateVO> createPayment(HttpServletRequest request,
                                                       @RequestBody String encryptedData,
                                                       ApiMerchantPaymentRequestDTO requestDTO) {
        return CommonResult.success(paymentService.createPayment(encryptedData, requestDTO));
    }
}
```

## 8. 响应加密策略

当前默认方案先返回明文 `CommonResult`，只要响应体不包含 PAN、CVV、CAVV、私钥、token 等敏感字段，就不强制商户再维护一套响应解密密钥。这样 Java、PHP、Go、C 等不同技术栈商户接入时，只需要处理“JWT + 请求体加密”这一条主链路。

如果后续某些响应或回调必须承载敏感业务数据，可以为该接口单独开启响应加密，响应体再使用混合加密信封：

```json
{
  "code": "T200",
  "message": "Success",
  "data": "<compact encrypted response>"
}
```

响应加密增强模式开启后：

1. `code` 和 `message` 可以保持明文，方便商户快速判断调用结果。
2. `data` 使用商户提供的响应公钥加密返回业务数据。
3. 商户响应公私钥由商户自己生成，平台只保存商户响应公钥，绝不要求商户把响应私钥交给平台。
4. 平台可以在响应 header 或响应体中附加平台签名，便于商户验证响应来自当前支付平台。
5. 普通授权、撤销、退款等接口默认不启用响应加密，减少商户对接复杂度。

## 9. cardInfo 是否二次加密

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
4. MQ、异常、审计日志、业务响应禁止输出完整 PAN、CVV 和 CAVV。
5. 后续可以为高安全商户提供可选的 `cardInfo` 字段级 JWE 子信封。

## 10. 密钥与防重放

生产环境要求：

1. `merchantKey` 由开户流程生成，按商户独立保存，支持启停和轮换。
2. 平台 RSA 私钥不得硬编码，建议放入 KMS；如果使用 Nacos，必须加密存储并限制权限。
3. 平台 RSA 公钥通过商户后台或安全渠道下发，必须带 `kid`。
4. AES key 每次请求随机生成，不复用。
5. AES-GCM IV 每次请求随机生成，不允许同一个 AES key 下复用 IV。
6. JWT `jti` 写入 Redis，过期时间略大于 JWT 有效期，重复请求直接拒绝。
7. 商户密钥轮换时支持新旧 `merchantKey` 短时间并行校验。
8. 平台 RSA 密钥轮换时通过 `kid` 选择私钥，旧密钥保留到所有商户完成切换。

## 11. 当前测试入口

已在 `service-openapi` 增加安全链路测试：

```text
service-openapi/src/test/java/com/scott/payment/openapi/OpenApiSecurityFlowTests.java
service-openapi/src/test/java/com/scott/payment/openapi/MerchantSecurityDatabaseFlowTests.java
service-openapi/src/test/java/com/scott/payment/openapi/MerchantOnboardingFlowTests.java
service-openapi/src/test/java/com/scott/payment/openapi/MerchantKeyCryptoUsageTests.java
service-openapi/src/test/java/com/scott/payment/openapi/MerchantOpenApiEndToEndTests.java
service-openapi/src/test/java/com/scott/payment/openapi/support/MerchantOpenApiTestSupport.java
service-openapi/src/test/resources/sql/openapi-merchant-security-schema.sql
```

测试覆盖：

1. `shouldGenerateMerchantSecurityMaterial`：生成商户可见对接材料和平台服务端 RSA 材料，并打印安全摘要。
2. `shouldCreateAndVerifyMerchantJwtByHs256`：验证标准 HS256 JWT 生成和验签。
3. `shouldEncryptAndDecryptOpenApiPayload`：验证 RSA-OAEP-256/AES-256-GCM 加密和解密，并打印脱敏明文。
4. `shouldCallAuthorizationApiWithJwtAndEncryptedPayload`：验证授权接口完整调用链路，并打印接口响应。
5. `shouldCompleteMerchantOpenApiRoundTripWithDatabaseKeysAndEncryptedResponse`：模拟商户表、商户 JWT 密钥表、平台 RSA 密钥表、商户响应公钥表，覆盖商户加密请求、HTTP 调用、服务端成功验签、错误密钥、过期 JWT、密文篡改、DTO 解析、服务端响应加密和商户响应解密。
6. `shouldCompleteMerchantOpenApiRoundTripWithMysqlAndMyBatisPlus`：连接真实 MySQL `payment_acquiring`，创建 OpenAPI 商户与密钥表，写入两个测试商户，通过 MyBatisPlus 查询商户基础信息、merchantKey、平台 RSA 公私钥和商户响应公钥，并完成同一条加解密闭环。
7. `MerchantOnboardingFlowTests`：覆盖商户开户、商户侧材料查询、服务端内部材料查询、所有商户查询、JWT 密钥轮换和密钥迭代记录查询。
8. `MerchantKeyCryptoUsageTests`：覆盖商户查询自身密钥、使用平台公钥加密请求体、平台使用商户响应公钥加密响应、商户使用响应私钥解密响应。
9. `MerchantOpenApiEndToEndTests`：使用 MockMvc 直接调用 `service-openapi`，覆盖成功调用、响应增强加密解密、缺少请求头、错误 merchantKey、JWT 过期和密文篡改等异常分支。

完整闭环用例中的关键步骤：

1. 平台生成 `merchantKey` 和平台请求体 RSA 公私钥，把 `merchantKey` 与平台私钥保存到平台侧表。
2. 平台只把 `merchantKey`、平台 RSA 公钥和 `platformKeyId` 给商户。
3. 如果启用响应加密增强，商户生成响应 RSA 公私钥，把响应公钥和 `responseKeyId` 上传给平台；响应私钥保留在商户侧。默认授权接口可以不启用该步骤。
4. 商户组装业务 JSON，使用平台公钥加密请求体 `data`，使用 `merchantKey` 生成 JWT 请求头。
5. 服务端按 JWT `merchantId` 查 `base_merchant_jwt_key`，完成 JWT Header、Payload、Signature 校验。
6. 服务端按请求体 compact header 的 `kid` 查 `base_platform_payload_key`，用平台私钥解密 `data` 并转换 DTO。
7. 服务端生成响应业务数据；默认可明文返回非敏感字段，增强模式下使用 `base_merchant_response_key` 中的商户响应公钥加密 `data`。
8. 如果启用响应加密增强，商户收到响应后，用自己保存的响应私钥解密响应 `data`。

还需要在生产继续补齐的流程：

1. JWT `jti` 防重放：写入 Redis，TTL 略大于 JWT 有效期。
2. 密钥轮换：`base_merchant_jwt_key` 和 `base_platform_payload_key` 均保留版本/生效/失效时间。
3. 密钥加密存储：`merchant_key`、`private_key_pkcs8_base64` 目前仅用于测试明文链路，生产必须改为 KMS/HSM 或使用配置加密。
4. 审计日志：只输出 kid、长度、指纹和脱敏业务字段。
5. 商户停启用：`merchant_info.status` 和密钥表 `enabled` 需要一起参与校验。

执行命令：

```bash
mvn -pl service-openapi -am test
```

## 12. 参考标准

1. [RFC 7519 - JSON Web Token](https://www.rfc-editor.org/rfc/rfc7519)
2. [RFC 7518 - JSON Web Algorithms](https://www.rfc-editor.org/rfc/rfc7518)
3. [RFC 7516 - JSON Web Encryption](https://www.rfc-editor.org/rfc/rfc7516)
4. [RFC 8017 - PKCS #1 RSA Cryptography Specifications](https://www.rfc-editor.org/rfc/rfc8017)
5. [NIST SP 800-38D - Galois/Counter Mode](https://csrc.nist.gov/pubs/sp/800/38/d/final)
