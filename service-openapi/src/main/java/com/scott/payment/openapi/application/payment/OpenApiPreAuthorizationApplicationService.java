package com.scott.payment.openapi.application.payment;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPreAuthorizationApplicationService
 * @date : 2026-07-14 19:20
 * @email : scott_x@163.com
 * @description : openAPIpre授权应用服务，位于 商户开放接口服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 * @status : create
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
