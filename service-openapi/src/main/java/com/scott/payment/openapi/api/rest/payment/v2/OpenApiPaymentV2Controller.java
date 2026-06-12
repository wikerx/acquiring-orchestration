package com.scott.payment.openapi.api.rest.payment.v2;

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

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPaymentV2Controller
 * @date : 2026-05-29 00:00
 * @email : scott_x@163.com
 * @description : 开放接口收单支付 V2 控制器
 * @status : create
 */
@ApiVersion(apiVersion = 2)
@RestController
@RequestMapping("/api/rest/payment/{version}")
public class OpenApiPaymentV2Controller {

    /**
     * 开放接口收单支付业务服务，V2 当前复用 V1 的创建链路，后续可在本控制器扩展新增字段和新流程。
     */
    private final PaymentService paymentService;

    /**
     * 创建开放接口收单支付 V2 控制器。
     *
     * @param paymentService 开放接口收单支付业务服务
     */
    public OpenApiPaymentV2Controller(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 创建 V2 收单授权交易。
     * <p>
     * 路由规则：商户请求 /api/rest/payment/v2/authorization 时优先命中本控制器；
     * 请求 /api/rest/payment/v1/authorization 时仍然命中 V1 控制器。
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
        return success(paymentService.createPayment(encryptedData, requestDTO));
    }
}
