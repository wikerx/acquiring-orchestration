package com.scott.payment.component.mq.enums;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionEventStatus
 * @date : 2026-08-02 23:00
 * @email : scott_x@163.com
 * @description : 收单交易公共 MQ 事件状态契约，供生产者和消费者统一判断只有成功或失败终态才能驱动商户通知
 * @status : create
 */
@Getter
public enum PaymentTransactionEventStatus {

    /** 交易已成功，可以驱动商户终态通知。 */
    SUCCESS("SUCCESS", true),

    /** 交易已失败，可以驱动商户终态通知。 */
    FAILED("FAILED", true),

    /** 交易等待外部动作或异步结果，不允许驱动商户终态通知。 */
    PENDING("PENDING", false),

    /** 交易仍在处理，不允许驱动商户终态通知。 */
    PROCESSING("PROCESSING", false);

    /** 跨服务消息中传递的稳定状态编码，非敏感且不允许为空。 */
    private final String code;

    /** 是否为允许驱动商户通知的交易终态。 */
    private final boolean terminal;

    /**
     * 创建公共交易事件状态。
     *
     * @param code     跨服务传递的稳定状态编码
     * @param terminal 是否允许驱动商户终态通知
     */
    PaymentTransactionEventStatus(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }

    /**
     * 判断消息状态是否为受支持的交易终态。
     *
     * @param code 消息携带的交易状态编码
     * @return true 表示状态为成功或失败终态
     */
    public static boolean isTerminal(String code) {
        return SUCCESS.code.equals(code) || FAILED.code.equals(code);
    }
}
