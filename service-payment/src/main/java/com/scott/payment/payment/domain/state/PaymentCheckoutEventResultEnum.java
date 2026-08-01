package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * Hosted Checkout 事件处理结果枚举。
 */
@Getter
public enum PaymentCheckoutEventResultEnum {

    /** 事件对应业务动作执行成功。 */
    SUCCESS("SUCCESS"),
    /** 事件对应业务动作执行失败。 */
    FAILED("FAILED"),
    /** 因幂等、状态冲突或无需处理而忽略事件。 */
    IGNORED("IGNORED");

    /** 持久化和内部协议使用的稳定结果编码。 */
    private final String code;

    PaymentCheckoutEventResultEnum(String code) {
        this.code = code;
    }
}
