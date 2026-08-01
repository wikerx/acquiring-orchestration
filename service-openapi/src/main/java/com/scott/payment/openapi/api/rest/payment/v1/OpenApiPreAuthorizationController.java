package com.scott.payment.openapi.api.rest.payment.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.application.payment.OpenApiPreAuthorizationApplicationService;
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
 * @classname : OpenApiPreAuthorizationController
 * @date : 2026-07-14 16:50
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 预授权交易 V1 控制器，仅暴露预授权入口并保持统一安全链路。
 * @status : create
 */
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/payment/{version}")
public class OpenApiPreAuthorizationController {

    /**
     * 预授权应用服务，负责把预授权入口委托给内部支付核心。
     */
    private final OpenApiPreAuthorizationApplicationService preAuthorizationApplicationService;

    /**
     * 创建预授权交易 V1 控制器。
     *
     * @param preAuthorizationApplicationService 预授权应用服务
     */
    public OpenApiPreAuthorizationController(OpenApiPreAuthorizationApplicationService preAuthorizationApplicationService) {
        this.preAuthorizationApplicationService = preAuthorizationApplicationService;
    }

    /**
     * 创建预授权交易。
     *
     * @param request Servlet 请求上下文，由安全链路填充和审计
     * @param encryptedData 商户密文请求体
     * @param requestDTO 解密后的预授权请求参数
     * @return 预授权交易受理响应
     */
    @VerificationAndProcessing(
            dataReceiver = ApiMerchantPaymentRequestDTO.class,
            deferIpWhitelistToRisk = true,
            validationGroups = {
                    ApiMerchantPaymentRequestDTO.PreAuthorization.class,
                    ApiMerchantPaymentRequestDTO.Format.class
            }
    )
    @PostMapping("/pre-authorization")
    public CommonResult<PaymentCreateVO> createPreAuthorization(HttpServletRequest request,
                                                                @RequestBody String encryptedData,
                                                                ApiMerchantPaymentRequestDTO requestDTO) {
        return success(preAuthorizationApplicationService.createPreAuthorization(encryptedData, requestDTO));
    }
}
