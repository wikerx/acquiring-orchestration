package com.scott.payment.openapi.application.payment;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPreAuthCompletionApplicationService
 * @date : 2026-07-26 00:00
 * @email : scott_x@163.com
 * @description : openAPIpreauthcompletion应用服务，位于 商户开放接口服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 * @status : create
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
