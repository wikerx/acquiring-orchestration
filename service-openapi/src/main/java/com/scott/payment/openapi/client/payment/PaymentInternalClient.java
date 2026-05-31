package com.scott.payment.openapi.client.payment;

import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalClient
 * @date : 2026-05-31 21:12
 * @email : scott_x@163.com
 * @description : service-payment 内部调用客户端
 * @status : create
 */
public interface PaymentInternalClient {

    /**
     * 调用 service-payment 创建收单授权交易。
     *
     * @param requestDTO 创建交易内部请求
     * @return 创建交易内部响应
     */
    PaymentCreateClientResponseDTO createAuthorization(PaymentCreateClientRequestDTO requestDTO);
}
