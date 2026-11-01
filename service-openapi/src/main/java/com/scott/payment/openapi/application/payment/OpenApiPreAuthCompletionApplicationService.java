package com.scott.payment.openapi.application.payment;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.stereotype.Service;

/**
 * 商户预授权完成应用入口，关联原预授权并委托支付服务执行最终资金确认。
 */
@Service
public class OpenApiPreAuthCompletionApplicationService {

    /**
     * 开放接口收单支付业务服务，用于提交预授权完成交易。
     */
    private final PaymentService paymentService;

    /**
     * 创建预授权完成应用服务。
     *
     * @param paymentService 开放接口收单支付业务服务
     */
    public OpenApiPreAuthCompletionApplicationService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 受理预授权完成交易请求。
     *
     * @param encryptedData 商户原始密文，仅用于安全指纹
     * @param requestDTO 解密后的预授权完成请求参数
     * @return 预授权完成交易受理响应
     */
    public PaymentCreateVO preAuthCompletion(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return paymentService.preAuthCompletion(encryptedData, requestDTO);
    }
}
