package com.global.payment.checkout.controller;

import com.global.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckoutHealthController {

    @GetMapping("/checkout/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-checkout");
    }
}

