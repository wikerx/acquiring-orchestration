package com.scott.payment.clearing.domain.state;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransitionOrigin
 * @date : 2026-08-26 08:28
 * @email : scott_x@163.com
 * @description : 标识清分状态推进来源，用于隔离自动消费与经审计的人工重试权限。
 * @status : create
 */
public enum ClearingTransitionOrigin {
    /** MQ、延时消息或系统补偿触发的自动状态推进。 */
    AUTOMATIC,
    /** 经过内部鉴权和人工审计触发的显式重试。 */
    MANUAL_RETRY
}
