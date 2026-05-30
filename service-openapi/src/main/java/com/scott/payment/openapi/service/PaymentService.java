package com.scott.payment.openapi.service;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentService
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口收单支付服务接口
 * @status : create
 */
public interface PaymentService {

    /**
     * 创建收单支付交易。
     *
     * @param encryptedData 商户原始密文
     * @param requestDTO    解密后的统一请求参数
     * @return 创建交易响应
     */
    PaymentCreateVO createPayment(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO);
}
