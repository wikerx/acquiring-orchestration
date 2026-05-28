package com.sinopay.payment.openapi.service.impl;

import com.sinopay.payment.openapi.converter.OpenApiRequestConverter;
import com.sinopay.payment.openapi.dto.body.ApiMerchantCardOrganizationRequestDTO;
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

    private final OpenApiRequestConverter converter;

    public OpenApiPaymentServiceImpl(OpenApiRequestConverter converter) {
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
    public PaymentCreateVO createPayment(String encryptedData, ApiMerchantCardOrganizationRequestDTO requestDTO) {
        return converter.toPaymentCreateVO(requestDTO);
    }
}
