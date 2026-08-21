package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutSessionStatusEnum
 * @date : 2026-08-20 20:30
 * @email : scott_x@163.com
 * @description : Hosted Checkout 会话支付状态枚举，只复用平台统一的待处理、处理中、成功和失败四种状态
 * @status : update
 */
@Getter
public enum PaymentCheckoutSessionStatusEnum {

    /** 尚未提交支付，或支付失败后仍处于业务待处理阶段。 */
    PENDING("PENDING"),
    /** 已受理支付提交，正在执行风控、3DS、渠道请求或等待渠道结果。 */
    PROCESSING("PROCESSING"),
    /** 支付成功终态。 */
    SUCCESS("SUCCESS"),
    /** 支付失败；是否允许在截止时间前重试由会话策略单独判断。 */
    FAILED("FAILED");

    /** 持久化和内部协议使用的稳定会话状态编码。 */
    private final String code;

    PaymentCheckoutSessionStatusEnum(String code) {
        this.code = code;
    }
}
