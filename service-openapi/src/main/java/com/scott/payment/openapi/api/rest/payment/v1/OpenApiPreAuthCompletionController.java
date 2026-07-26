package com.scott.payment.openapi.api.rest.payment.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.application.payment.OpenApiPreAuthCompletionApplicationService;
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
 * @classname : OpenApiPreAuthCompletionController
 * @date : 2026-07-26 00:00
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 预授权完成 V1 控制器，仅暴露预授权完成入口并保持统一安全链路。
 * @status : create
 */
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/payment/{version}")
public class OpenApiPreAuthCompletionController {

    /**
     * 预授权完成应用服务，负责把外部入口委托给内部支付核心。
     */
    private final OpenApiPreAuthCompletionApplicationService preAuthCompletionApplicationService;

    /**
     * 创建预授权完成 V1 控制器。
     *
     * @param preAuthCompletionApplicationService 预授权完成应用服务
     */
    public OpenApiPreAuthCompletionController(OpenApiPreAuthCompletionApplicationService preAuthCompletionApplicationService) {
        this.preAuthCompletionApplicationService = preAuthCompletionApplicationService;
    }

    /**
     * 发起预授权完成交易。
     *
     * @param request Servlet 请求上下文，由安全链路填充和审计
     * @param encryptedData 商户密文请求体
     * @param requestDTO 解密后的预授权完成请求参数
     * @return 预授权完成交易受理响应
     */
    @VerificationAndProcessing(
            dataReceiver = ApiMerchantPaymentRequestDTO.class,
            validationGroups = {
                    ApiMerchantPaymentRequestDTO.PreAuthCompletion.class,
                    ApiMerchantPaymentRequestDTO.Format.class
            }
    )
    @PostMapping("/pre-auth-completion")
    public CommonResult<PaymentCreateVO> preAuthCompletion(HttpServletRequest request,
                                                           @RequestBody String encryptedData,
                                                           ApiMerchantPaymentRequestDTO requestDTO) {
        return success(preAuthCompletionApplicationService.preAuthCompletion(encryptedData, requestDTO));
    }
}
