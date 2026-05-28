package com.sinopay.payment.openapi.service.impl;

import com.sinopay.payment.openapi.api.rest.v1.dto.body.PaymentCreateRequestDTO;
import com.sinopay.payment.openapi.api.rest.v1.dto.converter.OpenApiRequestConverter;
import com.sinopay.payment.openapi.service.OpenApiPaymentService;
import com.sinopay.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPaymentServiceImpl
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口收单支付服务实现
 * @status : create
 */
@Service
public class OpenApiPaymentServiceImpl implements OpenApiPaymentService {

    private final OpenApiRequestConverter converter = new OpenApiRequestConverter();

    @Override
    public PaymentCreateVO createPayment(PaymentCreateRequestDTO requestDTO) {
        return converter.toPaymentCreateVO(requestDTO);
    }
}

