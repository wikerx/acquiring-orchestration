package com.scott.payment.openapi.api.rest.payment.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.application.payment.OpenApiPaymentQueryApplicationService;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.vo.payment.PaymentQueryVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPaymentQueryController
 * @date : 2026-07-14 16:50
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 交易查询 V1 控制器，仅暴露查询入口并保持统一安全链路。
 * @status : create
 */
@ApiVersion(apiVersion = 1)
@RestController
@RequestMapping("/api/rest/payment/{version}")
public class OpenApiPaymentQueryController {

    /**
     * 交易查询应用服务，负责把 query 入口委托给内部支付核心。
     */
    private final OpenApiPaymentQueryApplicationService paymentQueryApplicationService;

    /**
     * 创建交易查询 V1 控制器。
     *
     * @param paymentQueryApplicationService 交易查询应用服务
     */
    public OpenApiPaymentQueryController(OpenApiPaymentQueryApplicationService paymentQueryApplicationService) {
        this.paymentQueryApplicationService = paymentQueryApplicationService;
    }

    /**
     * 查询交易状态。
     *
     * @param request Servlet 请求上下文，由安全链路填充和审计
     * @param encryptedData 商户密文请求体
     * @param requestDTO 解密后的查询请求参数
     * @return 交易查询响应
     */
    @VerificationAndProcessing(
            dataReceiver = ApiMerchantPaymentRequestDTO.class,
            validationGroups = {
                    ApiMerchantPaymentRequestDTO.Query.class,
                    ApiMerchantPaymentRequestDTO.Format.class
            }
    )
    @PostMapping("/query")
    public CommonResult<PaymentQueryVO> query(HttpServletRequest request,
                                              @RequestBody String encryptedData,
                                              ApiMerchantPaymentRequestDTO requestDTO) {
        return success(paymentQueryApplicationService.query(encryptedData, requestDTO));
    }
}
