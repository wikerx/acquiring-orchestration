package com.sinopay.payment.openapi.api.rest.v1.payment;

import com.sinopay.payment.component.core.model.ApiResult;
import com.sinopay.payment.openapi.annotation.v1.VerificationAndProcessing;
import com.sinopay.payment.openapi.api.rest.v1.dto.body.PaymentCreateRequestDTO;
import com.sinopay.payment.openapi.service.OpenApiPaymentService;
import com.sinopay.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/openapi/v1/payments")
public class OpenApiPaymentController {

    private final OpenApiPaymentService openApiPaymentService;

    public OpenApiPaymentController(OpenApiPaymentService openApiPaymentService) {
        this.openApiPaymentService = openApiPaymentService;
    }

    @VerificationAndProcessing
    @PostMapping
    public ApiResult<PaymentCreateVO> createPayment(@RequestBody PaymentCreateRequestDTO requestDTO) {
        return ApiResult.success(openApiPaymentService.createPayment(requestDTO));
    }
}

