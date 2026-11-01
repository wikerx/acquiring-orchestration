package com.scott.payment.component.job.enums;

/**
 * 任务调度模式枚举
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
