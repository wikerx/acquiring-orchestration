package com.sinopay.payment.openapi.service;

import com.sinopay.payment.openapi.api.rest.v1.dto.body.PayoutCreateRequestDTO;

public interface OpenApiPayoutService {

    String createPayout(PayoutCreateRequestDTO requestDTO);
}

