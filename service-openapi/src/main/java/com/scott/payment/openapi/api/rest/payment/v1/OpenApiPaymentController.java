package com.scott.payment.openapi.api.rest.payment.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPaymentController
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口收单支付控制器
 * @status : create
 */
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/payment/{version}")
public class OpenApiPaymentController {

    /**
     * 开放接口收单支付业务服务，负责创建授权、支付等收单交易。
     */
    private final PaymentService paymentService;

    /**
     * 创建开放接口收单支付控制器。
     *
     * @param paymentService 开放接口收单支付业务服务
     */
    public OpenApiPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 创建收单授权交易。
     *
     * @param request    Servlet 请求上下文
     * @param encryptedData 商户密文请求体
     * @param requestDTO 解密后的统一请求参数
     * @return 收单授权交易响应
     */
    @VerificationAndProcessing(
            dataReceiver = ApiMerchantPaymentRequestDTO.class,
            validationGroups = {
                    ApiMerchantPaymentRequestDTO.Authorization.class,
                    ApiMerchantPaymentRequestDTO.Format.class
            }
    )
    @PostMapping("/authorization")
    public CommonResult<PaymentCreateVO> createPayment(HttpServletRequest request,
                                                       @RequestBody String encryptedData,
                                                       ApiMerchantPaymentRequestDTO requestDTO) {
        return CommonResult.success(paymentService.createPayment(encryptedData, requestDTO));
    }
}
