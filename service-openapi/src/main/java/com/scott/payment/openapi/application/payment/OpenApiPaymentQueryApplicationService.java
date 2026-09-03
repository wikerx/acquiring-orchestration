package com.scott.payment.openapi.application.payment;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.vo.payment.PaymentQueryVO;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPaymentQueryApplicationService
 * @date : 2026-07-14 19:20
 * @email : scott_x@163.com
 * @description : openAPI支付查询应用服务，位于 商户开放接口服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 * @status : create
 */
@Service
public class OpenApiPaymentQueryApplicationService {

    /**
     * 开放接口收单支付业务服务，用于查询交易状态。
     */
    private final PaymentService paymentService;

    /**
     * 创建交易查询应用服务。
     *
     * @param paymentService 开放接口收单支付业务服务
     */
    public OpenApiPaymentQueryApplicationService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 受理交易查询请求。
     *
     * @param encryptedData 商户原始密文，仅用于安全指纹
     * @param requestDTO 解密后的查询请求参数
     * @return 交易查询响应
     */
    public PaymentQueryVO query(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return paymentService.queryTransaction(encryptedData, requestDTO);
    }
}
