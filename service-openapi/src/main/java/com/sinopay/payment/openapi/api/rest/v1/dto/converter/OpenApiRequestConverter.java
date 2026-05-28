package com.sinopay.payment.openapi.api.rest.v1.dto.converter;

import com.sinopay.payment.openapi.api.rest.v1.dto.body.PaymentCreateRequestDTO;
import com.sinopay.payment.openapi.vo.payment.PaymentCreateVO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestConverter
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口请求对象转换器
 * @status : create
 */
public class OpenApiRequestConverter {

    public PaymentCreateVO toPaymentCreateVO(PaymentCreateRequestDTO requestDTO) {
        PaymentCreateVO vo = new PaymentCreateVO();
        vo.setMerchantOrderNo(requestDTO.getMerchantOrderNo());
        vo.setCurrency(requestDTO.getCurrency());
        vo.setAmount(requestDTO.getAmount());
        return vo;
    }
}

