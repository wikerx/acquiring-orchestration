package com.sinopay.payment.payment.api.internal;

import com.sinopay.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentHealthController
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 收单支付服务健康检查控制器
 * @status : create
 */
@RestController
public class PaymentHealthController {

    @GetMapping("/payment/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-payment");
    }
}
