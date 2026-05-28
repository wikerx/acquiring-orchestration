package com.sinopay.payment.openapi.api.rest.v1.dto.converter;

import com.sinopay.payment.openapi.api.rest.v1.dto.body.PaymentCreateRequestDTO;
import com.sinopay.payment.openapi.vo.payment.PaymentCreateVO;
import org.mapstruct.Mapper;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestConverter
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口请求对象转换器
 * @status : create
 */
@Mapper(componentModel = "spring")
public interface OpenApiRequestConverter {

    PaymentCreateVO toPaymentCreateVO(PaymentCreateRequestDTO requestDTO);
}
