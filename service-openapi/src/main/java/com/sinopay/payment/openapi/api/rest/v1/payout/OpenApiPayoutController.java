package com.sinopay.payment.openapi.api.rest.v1.payout;

import com.sinopay.payment.component.core.model.ApiResult;
import com.sinopay.payment.openapi.annotation.v1.VerificationAndProcessing;
import com.sinopay.payment.openapi.api.rest.v1.dto.body.PayoutCreateRequestDTO;
import com.sinopay.payment.openapi.service.OpenApiPayoutService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPayoutController
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口代付控制器
 * @status : create
 */
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

