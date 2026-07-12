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
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口收单支付控制器
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPaymentController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Payment 管理接口，位于 service-openapi 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/payment/{version}")
public class OpenApiPaymentController {

    /**
     * 开放接口收单支付业务服务，负责创建授权、支付等收单交易。
     */
    private final OpenApiPaymentApplicationService paymentApplicationService;

    /**
     * 创建开放接口收单支付控制器。
     *
     * @param paymentApplicationService 开放接口收单支付应用服务
     */
    public OpenApiPaymentController(OpenApiPaymentApplicationService paymentApplicationService) {
        this.paymentApplicationService = paymentApplicationService;
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
    /**
     * 创建或保存商户 OpenAPI数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param encryptedData 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param requestDTO 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/authorization")
    public CommonResult<PaymentCreateVO> createPayment(HttpServletRequest request,
                                                       @RequestBody String encryptedData,
                                                       ApiMerchantPaymentRequestDTO requestDTO) {
        return success(paymentApplicationService.createAuthorization(encryptedData, requestDTO));
    }
}
