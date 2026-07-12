package com.scott.payment.openapi.application.payment;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPaymentApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Payment Application 服务契约，位于 service-openapi 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
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
    /**
     * 创建或保存商户 OpenAPI数据，保持请求校验、默认值和审计字段一致。
     * @param encryptedData 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param requestDTO 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public PaymentCreateVO createAuthorization(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return paymentService.createPayment(encryptedData, requestDTO);
    }
}
