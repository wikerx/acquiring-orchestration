# OpenAPI 授权认证规范

完整的 JWT 鉴权、RSA-OAEP-256/AES-256-GCM 请求加密、响应 `data` 强制加密和防重放流程见 [OpenAPI 鉴权与加密流程](openapi-security-flow.md)。

## 1. 商户需要保存的材料

默认对接时，商户只需要保存下面 3 项：

1. `merchantKey`：用于生成请求头 JWT HS256 签名。
2. 平台公钥：用于加密每次请求随机生成的 AES-256-GCM 会话密钥。
3. 商户响应私钥：用于解密平台响应体中的 `data`。

平台私钥不下发给商户；商户响应私钥不上传给平台。所有密钥查询都基于 `merchantId`，请求和响应中不再携带 `keyId` 或 `kid`。

## 2. 请求头

对外 API 统一使用 JWT HS256 作为请求头授权凭证。

```http
Content-Type: application/json
authorization: <jwt-token>
```

兼容 `Bearer <jwt-token>` 格式。

## 3. JWT Header

```json
{
  "typ": "JWT",
  "alg": "HS256"
}
```

## 4. JWT Payload

```json
{
  "aud": ["gateway"],
  "iss": "merchant",
  "jti": "776865801940893698",
  "iat": 1704960018,
  "exp": 1704960198,
  "merchantId": "6003"
}
```

校验规则：

1. `aud` 必须包含 `gateway`。
2. `iss` 必须等于 `merchant`。
3. `jti` 必填，用作请求唯一标识；Redis 可用时写入防重放 Key，重复请求会被拒绝。
4. `merchantId` 必填，用于查询商户基础信息和当前启用的 `merchantKey`。
5. `exp` 必须大于当前时间。
6. `exp - iat` 不能超过 180 秒。
7. `alg` 只允许 `HS256`。

## 5. 请求体

收单授权接口版本路由示例：

```http
POST /api/rest/payment/v1/authorization
```

版本匹配规则：当前默认实现版本为 `v1`。如果商户请求 `v2` 但系统没有 `v2` 控制器，路由会自动降级到不超过请求版本的最高版本，例如 `v1`。

非法路径、未知接口、请求方法不支持、参数不合法、请求体解析失败等异常，统一返回 `CommonResult` JSON，不返回 HTML 错误页。

JWT 负责授权认证；请求体按统一密文信封传递业务数据。`data` 必须使用 `protectedHeader.encryptedKey.iv.cipherText.tag` 五段式 compact 密文格式：

```json
{
  "data": "base64url(protectedHeader).base64url(encryptedKey).base64url(iv).base64url(cipherText).base64url(tag)"
}
```

`protectedHeader` 不携带密钥编号：

```json
{
  "typ": "PAYMENT-PAYLOAD",
  "alg": "RSA-OAEP-256",
  "enc": "A256GCM"
}
```

`data` 解密后的明文示例：

```json
{
  "merchantInfo": {
    "merchantId": "200045"
  },
  "orderInfo": {
    "amount": 12389.45,
    "currency": "USD",
    "tradeNo": "20250116140182865587"
  },
  "cardInfo": {
    "cardNo": "5387380678556554",
    "expirationMonth": "03",
    "expirationYear": "2028",
    "securityCode": "123"
  }
}
```

## 6. 响应体

OpenAPI 成功响应会强制加密 `data`：

```json
{
  "code": "T200",
  "message": "SUCCESS",
  "data": "<compact encrypted response>"
}
```

商户收到响应后，使用自己的响应私钥解密 `data`。失败响应通常 `data == null`，只返回明文错误码和错误说明。

## 7. 控制器示例

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

`@VerificationAndProcessing` 会完成 JWT 校验、请求体解密、DTO 转换和 Bean Validation。响应加密由 `OpenApiResponseBodyAdvice` 统一处理。
