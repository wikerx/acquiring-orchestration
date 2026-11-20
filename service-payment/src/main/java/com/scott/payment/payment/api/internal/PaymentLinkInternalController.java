package com.scott.payment.payment.api.internal;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.payment.api.internal.dto.PaymentLinkDTOs.Command;
import com.scott.payment.payment.application.PaymentLinkApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/** HMAC 鉴权后仍按调用服务限定操作，公开 Checkout 服务不能伪造管理端命令。 */
@RestController
@RequestMapping("/internal/payment/payment-links")
public class PaymentLinkInternalController {
    private static final Set<String> PAYER_ACTIONS = Set.of("resolve", "start");
    private final PaymentLinkApplicationService application;

    public PaymentLinkInternalController(PaymentLinkApplicationService application) {
        this.application = application;
    }

    /** 接收可信内部操作命令，由应用层按动作执行链接授权、查询或付款入口规则。 */
    @PostMapping("/{action}")
    public CommonResult<Object> execute(@PathVariable("action") String action, @RequestBody Command command,
                                        HttpServletRequest request) {
        String caller = request.getHeader(InternalServiceSignature.HEADER_CALLER);
        if (PAYER_ACTIONS.contains(action)) {
            if (!"service-openapi".equals(caller)) { throw new ServiceException(ApiResultEnum.FORBIDDEN); }
        } else {
            String expectedApp = "service-admin".equals(caller) ? "ADMIN"
                    : "service-merchant".equals(caller) ? "MERCHANT" : null;
            if (expectedApp == null || command.getActor() == null
                    || !expectedApp.equals(command.getActor().getAppCode())) {
                throw new ServiceException(ApiResultEnum.FORBIDDEN);
            }
        }
        return CommonResult.success(application.execute(action, command));
    }
}
