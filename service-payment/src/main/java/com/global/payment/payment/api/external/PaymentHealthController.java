package com.global.payment.payment.api.external;

import com.global.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentHealthController {

    @GetMapping("/payment/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-payment");
    }
}

