package com.scott.payment.checkout.controller;

import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutHealthController
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 收银台服务健康检查控制器
 * @status : create
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
