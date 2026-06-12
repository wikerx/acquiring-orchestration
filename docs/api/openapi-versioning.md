# OpenAPI 版本升级规范

## 路由规则

开放接口路径统一保留当前版本结构：

```java
@ApiVersion(apiVersion = 1)
@RequestMapping("/api/rest/payment/{version}")
```

当商户请求 `/api/rest/payment/v1/authorization` 时，只会匹配 V1 控制器。

当系统新增 V2 控制器后，商户请求 `/api/rest/payment/v2/authorization` 会优先匹配 V2 控制器；如果后续商户请求 `/api/rest/payment/v3/authorization`，但系统还没有 V3 控制器，会自动降级匹配当前最高可用版本 V2。

## 升级步骤

1. 保留原有 `v1` 包和控制器，不修改旧接口行为。
2. 新增对应业务域的 `v2` 包，例如 `api.rest.payment.v2`。
3. 新控制器使用相同 `@RequestMapping` 和接口路径，路径中必须包含 `{version}`。
4. 新控制器标注 `@ApiVersion(apiVersion = 2)`。
5. V2 如需要新增字段，优先新增 V2 DTO；如协议完全兼容，可以先复用 V1 DTO。

## 示例

```java
@ApiVersion(apiVersion = 2)
@RestController
@RequestMapping("/api/rest/payment/{version}")
public class OpenApiPaymentV2Controller {

    @PostMapping("/authorization")
    public CommonResult<PaymentCreateVO> createPayment(...) {
        // V2 业务实现
    }
}
```

注意：Spring 默认 bean 名来自类名，同一个模块中不要让 V1/V2 控制器使用完全相同的简单类名，避免 beanName 冲突。建议类名显式带版本号，例如 `OpenApiPaymentV2Controller`。

## Controller 拆分规则

1. 一个对外 API 入口对应一个清晰 Controller，避免把多个外部资源混入同一个控制器。
2. 示例：`/api/rest/iso/{version}/countries/query` 使用 `OpenApiIsoCountryController`，`/api/rest/iso/{version}/currencies/query` 使用 `OpenApiIsoCurrencyController`。
3. 查询接口使用 `@PostMapping(".../query")`；创建接口使用 `@PostMapping`；整体替换使用 `@PutMapping`；局部更新使用 `@PatchMapping`；删除使用 `@DeleteMapping`。
