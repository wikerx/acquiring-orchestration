package com.scott.payment.openapi.application.payment;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.stereotype.Service;

/**
 * 商户预授权应用入口，将已完成安全校验的冻结额度请求委托支付服务处理。
 */
@Service
public class OpenApiPreAuthorizationApplicationService {

    /**
     * 开放接口收单支付业务服务，用于提交预授权交易。
     */
    private final PaymentService paymentService;

    /**
     * 创建预授权交易应用服务。
     *
     * @param paymentService 开放接口收单支付业务服务
     */
    public OpenApiPreAuthorizationApplicationService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 受理预授权交易请求。
     *
     * @param encryptedData 商户原始密文，仅用于安全指纹
     * @param requestDTO 解密后的预授权请求参数
     * @return 预授权交易受理响应
     */
    public PaymentCreateVO createPreAuthorization(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return paymentService.createPreAuthorization(encryptedData, requestDTO);
    }
}
