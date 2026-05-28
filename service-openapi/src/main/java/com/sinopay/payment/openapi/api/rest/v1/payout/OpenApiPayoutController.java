package com.sinopay.payment.openapi.api.rest.v1.payout;

import com.sinopay.payment.component.core.model.ApiResult;
import com.sinopay.payment.openapi.annotation.v1.VerificationAndProcessing;
import com.sinopay.payment.openapi.api.rest.v1.dto.body.PayoutCreateRequestDTO;
import com.sinopay.payment.openapi.service.OpenApiPayoutService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/openapi/v1/payouts")
public class OpenApiPayoutController {

    private final OpenApiPayoutService openApiPayoutService;

    public OpenApiPayoutController(OpenApiPayoutService openApiPayoutService) {
        this.openApiPayoutService = openApiPayoutService;
    }

    @VerificationAndProcessing
    @PostMapping
    public ApiResult<String> createPayout(@RequestBody PayoutCreateRequestDTO requestDTO) {
        return ApiResult.success(openApiPayoutService.createPayout(requestDTO));
    }
}

