package com.scott.payment.openapi.application.checkout;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.openapi.client.payment.PaymentLinkInternalClient;
import com.scott.payment.openapi.dto.paymentlink.PaymentLinkDTOs.*;
import org.springframework.stereotype.Service;

/** 付款人持链接凭据访问，不接受商户号/金额/操作者覆盖。 */
@Service
public class OpenApiPaymentLinkApplicationService {
    private final PaymentLinkInternalClient payment;
    public OpenApiPaymentLinkApplicationService(PaymentLinkInternalClient payment) { this.payment = payment; }

    public PublicView resolve(String token) {
        Command command = new Command(); command.setOpaqueToken(token);
        return payment.executePaymentLink("resolve", command, new TypeReference<CommonResult<PublicView>>() { });
    }
    public Started start(String token, String requestId) {
        Command command = new Command(); command.setOpaqueToken(token); command.setRequestId(requestId);
        return payment.executePaymentLink("start", command, new TypeReference<CommonResult<Started>>() { });
    }
}
