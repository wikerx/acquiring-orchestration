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

    /**
     * 本次准入决策涉及的标准支付方式键。
     */
    public String itemKey() {
        return itemKey;
    }

    /**
     * 供调用端映射准入拒绝的稳定内部原因。
     */
    public Reason reason() {
        return reason;
    }

    /**
     * 本次判定读取的商户支付授权版本。
     */
    public Long authorizationRevision() {
        return authorizationRevision;
    }

    /**
     * 标记拒绝回执是否来自已有幂等事实。
     */
    public boolean isReplay() {
        return replay;
    }

    /** 原因只用于内部查询和监控，OpenAPI 仍使用标准商户配置不可用错误。 */
    public enum Reason {
        /**
         * 尚未识别可用于判断授权的支付方式。
         */
        METHOD_UNRESOLVED,
        /**
         * 商户无法按当前准入规则继续交易。
         */
        MERCHANT_UNAVAILABLE,
        /**
         * 支付方式已关闭或不处于可用状态。
         */
        METHOD_DISABLED,
        /**
         * 没有与请求相符且当前有效的商户授权。
         */
        AUTHORIZATION_UNAVAILABLE,
        /**
         * 支付方式缺少与交易匹配的已发布费率覆盖。
         */
        FEE_UNAVAILABLE,
        /**
         * 支付方式没有满足当前约束的可执行路由。
         */
        ROUTE_UNAVAILABLE,
        CONFIGURATION_CHANGED
    }
}
