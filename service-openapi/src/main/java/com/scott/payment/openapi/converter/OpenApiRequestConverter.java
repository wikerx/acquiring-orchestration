package com.scott.payment.openapi.converter;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.dto.body.PaymentCreateRequestDTO;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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

    /**
     * 当前收单支付基础响应使用两位小数币种的最小单位。
     * <p>
     * 后续接入 JPY、KWD 等特殊币种时，应改为按 ISO 4217 币种精度配置动态转换。
     */
    int DEFAULT_MINOR_UNIT_SCALE = 2;

    /**
     * 将普通收单创建 DTO 转换为创建响应。
     *
     * @param requestDTO 普通收单创建 DTO
     * @return 创建响应
     */
    @Mapping(target = "paymentOrderNo", ignore = true)
    @Mapping(target = "status", ignore = true)
    PaymentCreateVO toPaymentCreateVO(PaymentCreateRequestDTO requestDTO);

    /**
     * 将商户收单支付授权请求 DTO 转换为创建响应。
     *
     * @param requestDTO 商户收单支付授权请求 DTO
     * @return 创建响应
     */
    default PaymentCreateVO toPaymentCreateVO(ApiMerchantPaymentRequestDTO requestDTO) {
        PaymentCreateVO vo = new PaymentCreateVO();
        if (requestDTO == null || requestDTO.getOrderInfo() == null) {
            return vo;
        }
        ApiMerchantPaymentRequestDTO.OrderInfoDTO orderInfo = requestDTO.getOrderInfo();
        vo.setMerchantOrderNo(orderInfo.getTradeNo());
        vo.setCurrency(orderInfo.getCurrency());
        vo.setAmount(toMinorAmount(orderInfo.getAmount()));
        return vo;
    }

    /**
     * 将主单位金额转换为分单位金额。
     *
     * @param amount 主单位金额
     * @return 分单位金额
     */
    default Long toMinorAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.movePointRight(DEFAULT_MINOR_UNIT_SCALE).longValueExact();
    }
}
