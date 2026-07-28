package com.scott.payment.component.job.enums;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobMisfireStrategyEnum
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务错过触发Strategy枚举
 * @status : create
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
