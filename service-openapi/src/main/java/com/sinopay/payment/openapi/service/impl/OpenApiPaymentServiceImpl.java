package com.sinopay.payment.openapi.service.impl;

import com.sinopay.payment.openapi.api.rest.v1.dto.body.PaymentCreateRequestDTO;
import com.sinopay.payment.openapi.api.rest.v1.dto.converter.OpenApiRequestConverter;
import com.sinopay.payment.openapi.service.OpenApiPaymentService;
import com.sinopay.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.stereotype.Service;

@Service
public class OpenApiPaymentServiceImpl implements OpenApiPaymentService {

    private final OpenApiRequestConverter converter = new OpenApiRequestConverter();

    @Override
    public PaymentCreateVO createPayment(PaymentCreateRequestDTO requestDTO) {
        return converter.toPaymentCreateVO(requestDTO);
    }
}

