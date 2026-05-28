package com.global.payment.merchant.controller;

import com.global.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MerchantHealthController {

    @GetMapping("/merchant/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-merchant");
    }
}

