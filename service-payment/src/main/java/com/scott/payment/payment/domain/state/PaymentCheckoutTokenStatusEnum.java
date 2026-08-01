package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * Hosted Checkout URL token 状态枚举。
 */
@Getter
public enum PaymentCheckoutTokenStatusEnum {

    /** 令牌在有效期内且未撤销，可用于摘要匹配。 */
    ACTIVE("ACTIVE", true),
    /** 令牌已主动撤销，不再允许访问会话。 */
    REVOKED("REVOKED", false),
    /** 令牌已超过有效期。 */
    EXPIRED("EXPIRED", false);

    /** 持久化使用的稳定令牌状态编码。 */
    private final String code;

    /** 当前状态是否允许令牌参与会话访问校验。 */
    private final boolean usable;

    PaymentCheckoutTokenStatusEnum(String code, boolean usable) {
        this.code = code;
        this.usable = usable;
    }
}
