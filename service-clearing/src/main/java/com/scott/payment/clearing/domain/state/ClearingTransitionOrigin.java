package com.scott.payment.clearing.domain.state;

/**
 * 标识清分状态推进来源，用于隔离自动消费与经审计的人工重试权限。
 */
public enum ClearingTransitionOrigin {
    /**
     * MQ、延时消息或系统补偿触发的自动状态推进。
     */
    AUTOMATIC,
    /**
     * 经过内部鉴权和人工审计触发的显式重试。
     */
    MANUAL_RETRY
}
