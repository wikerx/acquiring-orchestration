package com.sinopay.payment.openapi.api.rest.v1.dto.converter;

import com.sinopay.payment.openapi.api.rest.v1.dto.body.PaymentCreateRequestDTO;
import com.sinopay.payment.openapi.vo.payment.PaymentCreateVO;

public class OpenApiRequestConverter {

    public PaymentCreateVO toPaymentCreateVO(PaymentCreateRequestDTO requestDTO) {
        PaymentCreateVO vo = new PaymentCreateVO();
        vo.setMerchantOrderNo(requestDTO.getMerchantOrderNo());
        vo.setCurrency(requestDTO.getCurrency());
        vo.setAmount(requestDTO.getAmount());
        return vo;
    }
}

