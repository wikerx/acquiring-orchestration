package com.scott.payment.openapi.application.payment;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.stereotype.Service;


@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPaymentApplicationService
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : OpenApiPaymentApplicationService 应用服务，用于编排接口请求、权限上下文、领域服务和外部依赖，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class OpenApiPaymentApplicationService {

    /**
     * 开放接口收单支付业务服务，用于提交一步支付交易。
     */
    private final PaymentService paymentService;

    /**
     * 创建一步支付应用服务。
     *
     * @param paymentService 开放接口收单支付业务服务
     */
    public OpenApiPaymentApplicationService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 受理一步支付交易请求。
     *
     * @param encryptedData 商户原始密文，仅用于安全指纹
     * @param requestDTO    解密后的统一请求参数
     * @return 交易受理响应
     */
    public PaymentCreateVO createPayment(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return paymentService.createPayment(encryptedData, requestDTO);
    }
}
