package com.scott.payment.payment.api.internal;

import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.ApiResult.success;


/**
 * 支付健康检查 HTTP 控制器，位于 支付核心服务，只承接参数、鉴权注解和统一响应，业务编排委托应用服务。
 */
@RestController
public class PaymentHealthController {

    /**
     * 收单支付核心服务健康检查入口。
     *
     * @return 当前服务名称
     */
    @GetMapping("/payment/health")
    public ApiResult<String> health() {
        return success("service-payment");
    }
}
