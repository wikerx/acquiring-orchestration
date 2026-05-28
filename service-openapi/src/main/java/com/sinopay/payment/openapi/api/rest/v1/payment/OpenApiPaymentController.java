package com.sinopay.payment.openapi.api.rest.v1.payment;

import com.sinopay.payment.component.core.model.CommonResult;
import com.sinopay.payment.openapi.annotation.v1.VerificationAndProcessing;
import com.sinopay.payment.openapi.api.rest.v1.dto.body.ApiMerchantCardOrganizationRequestDTO;
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
@RestController
@RequestMapping("/openapi/v1/payments")
public class OpenApiPaymentController {

    private final OpenApiPaymentService openApiPaymentService;

    public OpenApiPaymentController(OpenApiPaymentService openApiPaymentService) {
        this.openApiPaymentService = openApiPaymentService;
    }

    @VerificationAndProcessing(
            dataReceiver = ApiMerchantCardOrganizationRequestDTO.class,
            validationGroups = {
                    ApiMerchantCardOrganizationRequestDTO.Authorization.class,
                    ApiMerchantCardOrganizationRequestDTO.Format.class
            }
    )
    @PostMapping
    public CommonResult<PaymentCreateVO> createPayment(HttpServletRequest request,
                                                       @RequestBody String encydata,
                                                       ApiMerchantCardOrganizationRequestDTO requestDTO) {
        return CommonResult.success(openApiPaymentService.createPayment(encydata, requestDTO));
    }
}
