package com.scott.payment.component.job.enums;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobSchedulerModeEnum
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务调度模式枚举
 * @status : create
 */
public enum JobSchedulerModeEnum {

    /**
     * 单机模式，仅当前节点扫描和执行任务。
     */
    STANDALONE,

    /**
     * 分布式模式，多节点扫描但通过锁抢占避免重复执行。
     */
    DISTRIBUTED
}
