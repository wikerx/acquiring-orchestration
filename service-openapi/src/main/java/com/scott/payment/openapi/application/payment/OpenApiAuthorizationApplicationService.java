package com.scott.payment.openapi.application.payment;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiAuthorizationApplicationService
 * @date : 2026-07-14 19:20
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 授权交易应用服务，位于 service-openapi 应用编排层，仅编排 authorization 独立外部 API 到内部支付服务的调用。
 * @status : create
 */
@Service
public class OpenApiAuthorizationApplicationService {

    /**
     * 开放接口收单支付业务服务，用于提交授权交易。
     */
    private final PaymentService paymentService;

    /**
     * 创建授权交易应用服务。
     *
     * @param paymentService 开放接口收单支付业务服务
     */
    public OpenApiAuthorizationApplicationService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 受理授权交易请求。
     *
     * @param encryptedData 商户原始密文，仅用于安全指纹
     * @param requestDTO 解密后的授权请求参数
     * @return 授权交易受理响应
     */
    public PaymentCreateVO createAuthorization(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return paymentService.createAuthorization(encryptedData, requestDTO);
    }
}
