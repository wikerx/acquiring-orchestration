package com.scott.payment.data.model;

/**
 * 商户通知任务状态，名称与交易库持久化值保持一致。
 */
public enum MerchantNotificationStatus {

    /**
     * 等待首次自动投递。
     */
    INIT,

    /**
     * 已被实例通过版本 CAS 抢占，HTTP 结果尚未提交。
     */
    PROCESSING,

    /**
     * 本次失败且仍存在下一次自动投递计划。
     */
    FAILED,

    /**
     * 商户已经按协议确认成功。
     */
    SUCCESS,

    /**
     * 自动计划耗尽或遇到确定不可恢复错误。
     */
    CLOSED
}
