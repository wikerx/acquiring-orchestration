package com.scott.payment.payment.service.capability;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;

import java.util.Objects;

/** 已在创建系统订单前作出确定的支付方式准入拒绝。 */
public final class PaymentAdmissionRejectedException extends ServiceException {
    private static final long serialVersionUID = 1L;

    private final String itemKey;
    private final Reason reason;
    private final Long authorizationRevision;
    private final boolean replay;

    public PaymentAdmissionRejectedException(String itemKey, Reason reason, Long authorizationRevision) {
        this(itemKey, reason, authorizationRevision, false);
    }

    PaymentAdmissionRejectedException(String itemKey, Reason reason, Long authorizationRevision, boolean replay) {
        super(ApiResultEnum.MERCHANT_PAYMENT_CONFIG_UNAVAILABLE);
        this.itemKey = itemKey;
        this.reason = Objects.requireNonNull(reason);
        this.authorizationRevision = authorizationRevision;
        this.replay = replay;
    }

    public String itemKey() {
        return itemKey;
    }

    public Reason reason() {
        return reason;
    }

    public Long authorizationRevision() {
        return authorizationRevision;
    }

    public boolean isReplay() {
        return replay;
    }

    /** 原因只用于内部查询和监控，OpenAPI 仍使用标准商户配置不可用错误。 */
    public enum Reason {
        METHOD_UNRESOLVED,
        MERCHANT_UNAVAILABLE,
        METHOD_DISABLED,
        AUTHORIZATION_UNAVAILABLE,
        FEE_UNAVAILABLE,
        ROUTE_UNAVAILABLE,
        CONFIGURATION_CHANGED
    }
}
