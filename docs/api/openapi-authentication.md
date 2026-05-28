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

JWT 负责授权认证；请求体仍按接口协议传递密文数据。`service-openapi` 的 `@VerificationAndProcessing(dataReceiver = XxxDTO.class)` 会完成：

1. JWT 请求头校验；
2. 密文请求体解密；
3. DTO 转换；
4. DTO 属性校验；
5. 控制器参数注入。

示例：

```java
@VerificationAndProcessing(dataReceiver = PaymentCreateRequestDTO.class)
@PostMapping
public CommonResult<PaymentCreateVO> createPayment(HttpServletRequest request,
                                                   @RequestBody String encydata,
                                                   PaymentCreateRequestDTO requestDTO) {
    return CommonResult.success(openApiPaymentService.createPayment(encydata, requestDTO));
}
```

说明：如果后续要求更强的应用层报文防篡改，可以在 JWT payload 中增加 `bodyHash`，或将请求体改为认证加密格式。
