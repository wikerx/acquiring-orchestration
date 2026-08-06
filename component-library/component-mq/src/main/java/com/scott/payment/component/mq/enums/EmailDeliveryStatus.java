package com.scott.payment.component.mq.enums;

import java.util.Arrays;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : EmailDeliveryStatus
 * @date : 2026-08-02 23:40
 * @email : scott_x@163.com
 * @description : 邮件投递持久化状态，约束待处理、处理中、成功、重试等待和关闭终态的统一数值契约
 * @status : create
 */
public enum EmailDeliveryStatus {

    /** 已落库并等待消费者抢占。 */
    PENDING(0, false),
    /** 已由一个消费者通过数据库 CAS 抢占。 */
    SENDING(1, false),
    /** SMTP 已确认发送成功的不可逆终态。 */
    SUCCESS(2, true),
    /** 重试耗尽或不可恢复错误关闭的终态。 */
    CLOSED(3, true),
    /** 已计算下一次执行时间并等待可靠重投。 */
    RETRY_WAIT(4, false),
    /** 人工取消的终态。 */
    CANCELLED(5, true);

    /** 数据库存储值。 */
    private final int code;
    /** 是否为不可自动推进的终态。 */
    private final boolean terminal;

    EmailDeliveryStatus(int code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }

    /**
     * 返回数据库状态值。
     *
     * @return 数值状态码
     */
    public int getCode() {
        return code;
    }

    /**
     * 判断当前状态是否为终态。
     *
     * @return true 表示自动任务不得重新打开该状态
     */
    public boolean isTerminal() {
        return terminal;
    }

    /**
     * 按数据库状态值解析枚举。
     *
     * @param code 数据库存储状态
     * @return 对应状态
     */
    public static EmailDeliveryStatus fromCode(Integer code) {
        return Arrays.stream(values())
                .filter(status -> code != null && status.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown email delivery status"));
    }
}
