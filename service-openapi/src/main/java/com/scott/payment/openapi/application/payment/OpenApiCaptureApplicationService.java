package com.scott.payment.openapi.application.payment;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.stereotype.Service;

/**
 * 商户请款应用入口，按原授权冻结汇率和剩余额度委托支付服务计算并执行请款。
 */
@Service
public class OpenApiCaptureApplicationService {

    /**
     * 开放接口收单支付业务服务，用于提交请款交易。
     */
    private final PaymentService paymentService;

    /**
     * 创建请款应用服务。
     *
     * @param paymentService 开放接口收单支付业务服务
     */
    public OpenApiCaptureApplicationService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 受理请款交易请求。
     *
     * @param encryptedData 商户原始密文，仅用于安全指纹
     * @param requestDTO 解密后的请款请求参数
     * @return 请款交易受理响应
     */
    public PaymentCreateVO capture(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return paymentService.capture(encryptedData, requestDTO);
    }
}
