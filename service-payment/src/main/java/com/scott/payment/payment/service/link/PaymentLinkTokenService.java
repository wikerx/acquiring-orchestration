package com.scott.payment.payment.service.link;

import com.scott.payment.payment.config.PaymentLinkProperties;

/** 保留 Payment 既有类型供兼容调用；运行时统一使用共享链接凭据服务。 */
public class PaymentLinkTokenService extends com.scott.payment.link.service.PaymentLinkTokenService {
    public PaymentLinkTokenService(PaymentLinkProperties properties) { super(properties); }
}
