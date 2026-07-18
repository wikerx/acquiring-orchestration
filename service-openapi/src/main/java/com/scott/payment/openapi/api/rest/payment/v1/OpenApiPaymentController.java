package com.scott.payment.openapi.api.rest.payment.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.application.payment.OpenApiPaymentApplicationService;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPaymentController
 * @date : 2026-07-14 12:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 一步支付 V1 控制器，仅暴露支付接口并统一走加密、验签和防重放链路。
 * @status : create
 */
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/payment/{version}")
public class OpenApiPaymentController {

    /**
     * 一步支付应用服务，负责把 payment 入口委托给内部支付核心。
     */
    private final OpenApiPaymentApplicationService paymentApplicationService;

    /**
     * 创建一步支付 V1 控制器。
     *
     * @param paymentApplicationService 开放接口收单支付应用服务
     */
    public OpenApiPaymentController(OpenApiPaymentApplicationService paymentApplicationService) {
        this.paymentApplicationService = paymentApplicationService;
    }

    /**
     * 创建一步支付交易。
     *
     * @param request Servlet 请求上下文，由安全链路填充和审计
     * @param encryptedData 商户密文请求体
     * @param requestDTO 解密后的统一请求参数
     * @return 一步支付受理响应
     */
    @VerificationAndProcessing(
            dataReceiver = ApiMerchantPaymentRequestDTO.class,
            validationGroups = {
                    ApiMerchantPaymentRequestDTO.Payment.class,
                    ApiMerchantPaymentRequestDTO.Format.class
            }
    )
    @PostMapping("/payment")
    public CommonResult<PaymentCreateVO> createPayment(HttpServletRequest request,
                                                       @RequestBody String encryptedData,
                                                       ApiMerchantPaymentRequestDTO requestDTO) {
        return success(paymentApplicationService.createPayment(encryptedData, requestDTO));
    }
}
