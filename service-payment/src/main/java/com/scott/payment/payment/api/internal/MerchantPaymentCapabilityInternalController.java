package com.scott.payment.payment.api.internal;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.payment.service.capability.MerchantPaymentCapabilityEvaluator;
import com.scott.payment.payment.service.capability.MerchantPaymentCapabilityEvaluator.Assessment;
import com.scott.payment.payment.service.capability.MerchantPaymentCapabilityEvaluator.Query;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 承接已通过内部签名校验的 Admin 配置诊断命令。 */
@RestController
@RequestMapping("/internal/payment/merchant-capabilities")
public class MerchantPaymentCapabilityInternalController {

    private final MerchantPaymentCapabilityEvaluator evaluator;

    public MerchantPaymentCapabilityInternalController(MerchantPaymentCapabilityEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /** 授权范围由 Admin 归属链路校验，支付服务只接受配置诊断所属调用方。 */
    @PostMapping("/evaluate")
    public CommonResult<Assessment> evaluate(@Valid @RequestBody Query query, HttpServletRequest request) {
        if (!"service-admin".equals(request.getHeader(InternalServiceSignature.HEADER_CALLER))) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        return CommonResult.success(evaluator.evaluate(query));
    }
}
