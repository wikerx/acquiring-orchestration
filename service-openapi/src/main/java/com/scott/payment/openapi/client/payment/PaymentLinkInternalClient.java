package com.scott.payment.openapi.client.payment;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.openapi.dto.paymentlink.PaymentLinkDTOs.Command;

/** 支付链接内部客户端契约，复用现有 HMAC 传输和服务发现，不接受外部任意目标地址。 */
public interface PaymentLinkInternalClient {
    <T> T executePaymentLink(String action, Command command, TypeReference<CommonResult<T>> responseType);
}
