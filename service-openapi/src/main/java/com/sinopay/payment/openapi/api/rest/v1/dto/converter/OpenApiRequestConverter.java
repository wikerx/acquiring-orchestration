package com.sinopay.payment.openapi.api.rest.v1.dto.converter;

import com.sinopay.payment.openapi.api.rest.v1.dto.body.ApiMerchantCardOrganizationRequestDTO;
import com.sinopay.payment.openapi.api.rest.v1.dto.body.PaymentCreateRequestDTO;
import com.sinopay.payment.openapi.vo.payment.PaymentCreateVO;
import org.mapstruct.Mapper;

import java.math.BigDecimal;

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

    default PaymentCreateVO toPaymentCreateVO(ApiMerchantCardOrganizationRequestDTO requestDTO) {
        PaymentCreateVO vo = new PaymentCreateVO();
        if (requestDTO == null || requestDTO.getOrderInfo() == null) {
            return vo;
        }
        ApiMerchantCardOrganizationRequestDTO.OrderInfoDTO orderInfo = requestDTO.getOrderInfo();
        vo.setMerchantOrderNo(orderInfo.getTradeNo());
        vo.setCurrency(orderInfo.getCurrency());
        vo.setAmount(toMinorAmount(orderInfo.getAmount()));
        return vo;
    }

    default Long toMinorAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.movePointRight(2).longValue();
    }
}
