# OpenAPI 授权认证规范

## 1. 授权方式

对外 API 统一使用 JWT HS256 作为请求头授权凭证。

```http
Content-Type: application/json
authorization: <jwt-token>
```

兼容 `Bearer <jwt-token>` 格式。

## 2. 商户密钥

`merchantKey` 由平台开户时生成并提供给商户。服务端使用商户号 `merchantId` 查询对应密钥，再校验 JWT 签名。

开发环境默认示例：

```text
hGa8xl/kde6=C=O+
```

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
3. `jti` 必填，用作请求唯一标识，后续接入 Redis 幂等与防重放。
4. `merchantId` 必填，用于查询 `merchantKey`。
5. `exp` 必须大于当前时间。
6. `exp - iat` 不能超过 180 秒。
7. `alg` 只允许 `HS256`。

## 5. 请求体

收单授权接口版本路由示例：

```http
POST /api/rest/co/v2/authorization
```

JWT 负责授权认证；请求体按统一密文信封传递业务数据：

```json
{
  "data": "ciphertext"
}
```

`data` 解密后的明文示例：

```json
{
  "merchantInfo": {
    "merchantId": "200045",
    "subMerchantInfo": {
      "subName": "John",
      "subCompanyName": "JohnCompany",
      "subId": "123456789111111",
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
    "lastName": "tom",
    "phone": "+55-5085149876",
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
    "expirationYear": "2025",
    "securityCode": "123"
  },
  "threeDsInfo": {
    "eci": "212",
    "cavv": "KANiJlHEqL/yaEfVxr/BUoQBicnh",
    "dsTransactionId": "b96c957d-daa1-4b7f-b8b4-373fb9dec47b",
    "threeDsVersion": "2.2.0"
  }
}
```

`service-openapi` 的 `@VerificationAndProcessing(dataReceiver = XxxDTO.class)` 会完成：

1. JWT 请求头校验；
2. `data` 密文请求体解密；
3. DTO 转换；
4. DTO 属性校验；
5. 控制器参数注入。

示例：

```java
@ApiVersion(apiVersion = 2)
@RestController
@RequestMapping("/api/rest/co/{version}")
public class OpenApiPaymentController {

    @VerificationAndProcessing(dataReceiver = ApiMerchantCardOrganizationRequestDTO.class)
    @PostMapping("/authorization")
    public CommonResult<PaymentCreateVO> createPayment(HttpServletRequest request,
                                                       @RequestBody String encydata,
                                                       ApiMerchantCardOrganizationRequestDTO requestDTO) {
        return CommonResult.success(openApiPaymentService.createPayment(encydata, requestDTO));
    }
}
```

## 6. cardInfo 加密策略

默认不要求商户对 `cardInfo` 做二次加密。推荐默认方案是：

1. HTTPS 保护传输层；
2. `authorization` JWT HS256 证明商户身份、请求唯一性和时效；
3. 请求体 `data` 做统一应用层加密；
4. `service-openapi` 解密后只在内存中短暂持有卡数据；
5. 日志、MQ、异常、响应禁止输出完整 PAN 和 CVV；
6. 交易核心侧对 PAN 做令牌化或强加密存储；
7. CVV/CVC 只允许用于本次授权，授权后禁止存储。

这样比强制 `cardInfo` 再套一层加密更容易接入，也更容易统一治理密钥和错误处理。

可以预留高级模式：对安全等级更高、直连卡组织或有特殊合规要求的商户，支持 `cardInfo` 字段级二次加密或独立 JWE 子信封。
