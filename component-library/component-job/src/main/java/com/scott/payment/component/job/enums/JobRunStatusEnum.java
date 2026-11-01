package com.scott.payment.component.job.enums;

/**
 * 任务运行状态枚举
 */
public enum JobRunStatusEnum {

    /**
     * 已创建执行记录，等待真正开始执行。
     */
    WAITING,

    /**
     * 任务执行中。
     */
    RUNNING,

    /**
     * 任务执行成功。
     */
    SUCCESS,

    /**
     * 任务执行失败。
     */
    FAILED,

    /**
     * 任务执行超时。
     */
    TIMEOUT,

    /**
     * 任务已取消。
     */
    CANCELLED
}
