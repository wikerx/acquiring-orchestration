package com.sinopay.payment.openapi.service;

import com.sinopay.payment.openapi.api.rest.v1.dto.body.PaymentCreateRequestDTO;
import com.sinopay.payment.openapi.vo.payment.PaymentCreateVO;

public interface OpenApiPaymentService {

    PaymentCreateVO createPayment(PaymentCreateRequestDTO requestDTO);
}

