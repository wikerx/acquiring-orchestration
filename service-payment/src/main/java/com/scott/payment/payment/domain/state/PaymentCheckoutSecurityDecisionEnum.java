package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutSecurityDecisionEnum
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Hosted Checkout 安全决策枚举。
 * @status : create
 */
@Getter
public enum PaymentCheckoutSecurityDecisionEnum {

    /** 安全校验通过，允许继续请求。 */
    ALLOW("ALLOW"),
    /** 安全校验失败，阻断当前请求。 */
    BLOCK("BLOCK"),
    /** 需要附加验证后才能继续。 */
    CHALLENGE("CHALLENGE"),
    /** 仅记录风险证据，不改变当前请求结果。 */
    LOG_ONLY("LOG_ONLY");

    /** 安全事件持久化使用的稳定决策编码。 */
    private final String code;

    PaymentCheckoutSecurityDecisionEnum(String code) {
        this.code = code;
    }
}
