package com.scott.payment.payment.api.internal;

import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.ApiResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentHealthController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付服务健康检查接口，位于 service-payment 接口层，仅用于服务存活探测。
 * @status : create
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
