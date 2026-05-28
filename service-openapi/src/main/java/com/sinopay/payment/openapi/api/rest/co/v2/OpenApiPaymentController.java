package com.sinopay.payment.openapi.api.rest.co.v2;

import com.sinopay.payment.component.core.model.CommonResult;
import com.sinopay.payment.component.web.version.ApiVersion;
import com.sinopay.payment.openapi.annotation.v1.VerificationAndProcessing;
import com.sinopay.payment.openapi.dto.body.ApiMerchantCardOrganizationRequestDTO;
import com.sinopay.payment.openapi.service.OpenApiPaymentService;
import com.sinopay.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPaymentController
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口收单支付控制器
 * @status : create
 */
@ApiVersion(apiVersion = 2)
@RestController
@RequestMapping("/api/rest/co/{version}")
public class OpenApiPaymentController {

    private final OpenApiPaymentService openApiPaymentService;

    public OpenApiPaymentController(OpenApiPaymentService openApiPaymentService) {
        this.openApiPaymentService = openApiPaymentService;
    }

    /**
     * 创建收单授权交易。
     *
     * @param request    Servlet 请求上下文
     * @param encydata   商户密文请求体
     * @param requestDTO 解密后的统一请求参数
     * @return 收单授权交易响应
     */
    @VerificationAndProcessing(
            dataReceiver = ApiMerchantCardOrganizationRequestDTO.class,
            validationGroups = {
                    ApiMerchantCardOrganizationRequestDTO.Authorization.class,
                    ApiMerchantCardOrganizationRequestDTO.Format.class
            }
    )
    @PostMapping("/authorization")
    public CommonResult<PaymentCreateVO> createPayment(HttpServletRequest request,
                                                       @RequestBody String encydata,
                                                       ApiMerchantCardOrganizationRequestDTO requestDTO) {
        return CommonResult.success(openApiPaymentService.createPayment(encydata, requestDTO));
    }
}
