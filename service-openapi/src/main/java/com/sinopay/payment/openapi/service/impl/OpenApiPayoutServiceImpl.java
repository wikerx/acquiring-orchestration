package com.sinopay.payment.openapi.service.impl;

import com.sinopay.payment.openapi.api.rest.v1.dto.body.PayoutCreateRequestDTO;
import com.sinopay.payment.openapi.service.OpenApiPayoutService;
import org.springframework.stereotype.Service;

@Service
public class OpenApiPayoutServiceImpl implements OpenApiPayoutService {

    @Override
    public String createPayout(PayoutCreateRequestDTO requestDTO) {
        return requestDTO.getMerchantOrderNo();
    }
}

