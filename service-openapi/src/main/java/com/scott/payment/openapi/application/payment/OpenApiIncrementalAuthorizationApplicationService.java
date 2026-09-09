package com.scott.payment.openapi.application.payment;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiIncrementalAuthorizationApplicationService
 * @date : 2026-07-14 19:20
 * @email : scott_x@163.com
 * @description : openAPIincremental授权应用服务，位于 商户开放接口服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 * @status : create
 */
@Service
public class OpenApiIncrementalAuthorizationApplicationService {

    /**
     * 开放接口收单支付业务服务，用于提交增量授权交易。
     */
    private final PaymentService paymentService;

    /**
     * 创建增量授权应用服务。
     *
     * @param paymentService 开放接口收单支付业务服务
     */
    public OpenApiIncrementalAuthorizationApplicationService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 受理增量授权交易请求。
     *
     * @param encryptedData 商户原始密文，仅用于安全指纹
     * @param requestDTO 解密后的增量授权请求参数
     * @return 增量授权交易受理响应
     */
    public PaymentCreateVO createIncrementalAuthorization(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return paymentService.createIncrementalAuthorization(encryptedData, requestDTO);
    }
}
