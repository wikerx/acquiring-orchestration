package com.scott.payment.payment.model;

import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionEventDeliveryMode
 * @date : 2026-08-25 21:24
 * @email : scott_x@163.com
 * @description : 定义交易 Outbox 的普通、顺序和绝对定时投递模式；AUTO 仅用于兼容历史消息路由规则。
 * @status : create
 */
public enum TransactionEventDeliveryMode {

    /** 按历史 Tag、消息类型和 messageGroup 自动选择。 */
    AUTO,

    /** 普通并发消息。 */
    NORMAL,

    /** 按 messageGroup 选择固定队列的顺序消息。 */
    ORDERLY,

    /** 使用 deliverAt 的 RocketMQ 5.x 绝对定时消息。 */
    SCHEDULED;

    /**
     * 解析数据库投递模式；历史空值按 AUTO 兼容。
     *
     * @param value 数据库存储值
     * @return 标准投递模式
     * @throws IllegalArgumentException 非法模式
     */
    public static TransactionEventDeliveryMode from(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
