package com.scott.payment.openapi.api.rest.payment.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.application.payment.OpenApiIncrementalAuthorizationApplicationService;
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
 * @classname : OpenApiIncrementalAuthorizationController
 * @date : 2026-07-14 16:50
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 增量授权交易 V1 控制器，仅暴露增量授权入口并保持统一安全链路。
 * @status : create
 */
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/payment/{version}")
public class OpenApiIncrementalAuthorizationController {

    /**
     * 增量授权应用服务，负责把增量授权入口委托给内部支付核心。
     */
    private final OpenApiIncrementalAuthorizationApplicationService incrementalAuthorizationApplicationService;

    /**
     * 创建增量授权交易 V1 控制器。
     *
     * @param incrementalAuthorizationApplicationService 增量授权应用服务
     */
    public OpenApiIncrementalAuthorizationController(
            OpenApiIncrementalAuthorizationApplicationService incrementalAuthorizationApplicationService) {
        this.incrementalAuthorizationApplicationService = incrementalAuthorizationApplicationService;
    }

    /**
     * 创建增量授权交易。
     *
     * @param request Servlet 请求上下文，由安全链路填充和审计
     * @param encryptedData 商户密文请求体
     * @param requestDTO 解密后的增量授权请求参数
     * @return 增量授权交易受理响应
     */
    @VerificationAndProcessing(
            dataReceiver = ApiMerchantPaymentRequestDTO.class,
            validationGroups = {
                    ApiMerchantPaymentRequestDTO.IncrementalAuthorization.class,
                    ApiMerchantPaymentRequestDTO.Format.class
            }
    )
    @PostMapping("/incremental-authorization")
    public CommonResult<PaymentCreateVO> createIncrementalAuthorization(HttpServletRequest request,
                                                                        @RequestBody String encryptedData,
                                                                        ApiMerchantPaymentRequestDTO requestDTO) {
        return from(incrementalAuthorizationApplicationService.createIncrementalAuthorization(encryptedData, requestDTO));
    }
}
