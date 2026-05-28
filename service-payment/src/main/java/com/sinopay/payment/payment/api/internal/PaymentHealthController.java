package com.sinopay.payment.payment.api.internal;

import com.sinopay.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentHealthController {

    @GetMapping("/payment/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-payment");
    }
}
