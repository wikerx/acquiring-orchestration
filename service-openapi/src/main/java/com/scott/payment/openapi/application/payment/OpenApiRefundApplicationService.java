package com.scott.payment.openapi.application.payment;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.stereotype.Service;

/**
 * 商户退款应用入口，将原交易关联、退款金额和幂等信息委托支付服务处理。
 */
@Service
public class OpenApiRefundApplicationService {

    /**
     * 开放接口收单支付业务服务，用于提交退款交易。
     */
    private final PaymentService paymentService;

    /**
     * 创建退款应用服务。
     *
     * @param paymentService 开放接口收单支付业务服务
     */
    public OpenApiRefundApplicationService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 受理退款交易请求。
     *
     * @param encryptedData 商户原始密文，仅用于安全指纹
     * @param requestDTO 解密后的退款请求参数
     * @return 退款交易受理响应
     */
    public PaymentCreateVO refund(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return paymentService.refund(encryptedData, requestDTO);
    }
}
