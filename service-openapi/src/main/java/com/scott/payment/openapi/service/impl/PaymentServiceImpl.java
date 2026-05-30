package com.scott.payment.openapi.service.impl;

import com.scott.payment.openapi.converter.OpenApiRequestConverter;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentServiceImpl
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口收单支付服务实现
 * @status : create
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    /**
     * OpenAPI 请求转换器，负责把外部公共请求 DTO 转换成当前接口响应或内部服务对象。
     */
    private final OpenApiRequestConverter converter;

    /**
     * 创建开放接口收单支付服务实现。
     *
     * @param converter OpenAPI 请求转换器
     */
    public PaymentServiceImpl(OpenApiRequestConverter converter) {
        this.converter = converter;
    }

    /**
     * 创建收单支付交易。
     *
     * @param encryptedData 商户原始密文
     * @param requestDTO    解密后的统一请求参数
     * @return 创建交易响应
     */
    @Override
    public PaymentCreateVO createPayment(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return converter.toPaymentCreateVO(requestDTO);
    }
}
