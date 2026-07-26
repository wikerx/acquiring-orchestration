package com.scott.payment.component.job.enums;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTriggerTypeEnum
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务触发类型枚举
 * @status : create
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
