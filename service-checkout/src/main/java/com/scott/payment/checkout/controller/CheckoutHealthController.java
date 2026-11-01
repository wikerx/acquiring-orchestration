package com.scott.payment.checkout.controller;

import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 暴露收银台服务进程级健康检查，不承载业务可用性判断。
 */
@RestController
public class CheckoutHealthController {

    /**
     * 收银台服务健康检查入口。
     *
     * @return 当前服务名称
     */
    @GetMapping("/checkout/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-checkout");
    }
}
