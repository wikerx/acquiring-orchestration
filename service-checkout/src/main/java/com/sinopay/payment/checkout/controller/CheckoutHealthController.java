package com.sinopay.payment.checkout.controller;

import com.sinopay.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckoutHealthController {

    @GetMapping("/checkout/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-checkout");
    }
}

