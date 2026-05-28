package com.sinopay.payment.openapi.service;

import com.sinopay.payment.openapi.api.rest.v1.dto.body.PaymentCreateRequestDTO;
import com.sinopay.payment.openapi.vo.payment.PaymentCreateVO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPaymentService
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口收单支付服务接口
 * @status : create
 */
public interface OpenApiPaymentService {

    PaymentCreateVO createPayment(PaymentCreateRequestDTO requestDTO);
}

