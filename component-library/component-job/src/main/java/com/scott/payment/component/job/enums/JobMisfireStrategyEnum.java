package com.scott.payment.component.job.enums;

/**
 * 任务错过触发Strategy枚举
 */
public enum JobMisfireStrategyEnum {

    /**
     * 忽略错过的触发窗口，直接计算下一次调度时间。
     */
    IGNORE,

    /**
     * 错过调度时立即补执行一次，然后再回到正常 cron 节奏。
     */
    FIRE_ONCE
}
