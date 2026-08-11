package com.scott.payment.openapi.api.rest.payment.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.application.payment.OpenApiAuthorizationApplicationService;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import static com.scott.payment.openapi.api.rest.payment.v1.OpenApiPaymentResponseFactory.from;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiAuthorizationController
 * @date : 2026-07-14 16:50
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 授权交易 V1 控制器，仅暴露授权入口并保持统一安全链路。
 * @status : create
 */
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/payment/{version}")
public class OpenApiAuthorizationController {

    /**
     * 授权交易应用服务，负责把授权入口委托给内部支付核心。
     */
    private final OpenApiAuthorizationApplicationService authorizationApplicationService;

    /**
     * 创建授权交易 V1 控制器。
     *
     * @param authorizationApplicationService 授权交易应用服务
     */
    public OpenApiAuthorizationController(OpenApiAuthorizationApplicationService authorizationApplicationService) {
        this.authorizationApplicationService = authorizationApplicationService;
    }

    /**
     * 创建授权交易。
     *
     * @param request Servlet 请求上下文，由安全链路填充和审计
     * @param encryptedData 商户密文请求体
     * @param requestDTO 解密后的授权请求参数
     * @return 授权交易受理响应
     */
    @VerificationAndProcessing(
            dataReceiver = ApiMerchantPaymentRequestDTO.class,
            deferIpWhitelistToRisk = true,
            validationGroups = {
                    ApiMerchantPaymentRequestDTO.Authorization.class,
                    ApiMerchantPaymentRequestDTO.Format.class
            }
    )
    @PostMapping("/authorization")
    public CommonResult<PaymentCreateVO> createAuthorization(HttpServletRequest request,
                                                             @RequestBody String encryptedData,
                                                             ApiMerchantPaymentRequestDTO requestDTO) {
        return from(authorizationApplicationService.createAuthorization(encryptedData, requestDTO));
    }
}
