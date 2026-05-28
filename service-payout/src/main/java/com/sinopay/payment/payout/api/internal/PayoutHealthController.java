package com.sinopay.payment.payout.api.internal;

import com.sinopay.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PayoutHealthController {

    @GetMapping("/payout/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-payout");
    }
}
