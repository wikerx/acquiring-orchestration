package com.scott.payment.openapi.application.payment;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.stereotype.Service;

/**
 * 开放接口收单支付应用服务。
 * <p>
 * 当前负责衔接接口层与开放接口业务服务，后续可继续收敛商户校验、幂等、风控预检等应用编排逻辑。
 */
@Service
public class OpenApiPaymentApplicationService {

    /**
     * 开放接口收单支付业务服务。
     */
    private final PaymentService paymentService;

    /**
     * 创建开放接口收单支付应用服务。
     *
     * @param paymentService 开放接口收单支付业务服务
     */
    public OpenApiPaymentApplicationService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 受理收单授权交易创建请求。
     *
     * @param encryptedData 商户原始密文
     * @param requestDTO    解密后的统一请求参数
     * @return 创建交易响应
     */
    public PaymentCreateVO createAuthorization(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return paymentService.createPayment(encryptedData, requestDTO);
    }
}
