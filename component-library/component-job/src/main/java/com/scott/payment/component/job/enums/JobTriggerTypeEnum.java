package com.scott.payment.component.job.enums;

/**
 * 任务触发类型枚举
 */
public enum JobTriggerTypeEnum {

    /**
     * 定时扫描触发。
     */
    SCHEDULE,

    /**
     * 管理后台手动执行一次。
     */
    MANUAL,

    /**
     * 失败补偿重试触发。
     */
    RETRY
}
