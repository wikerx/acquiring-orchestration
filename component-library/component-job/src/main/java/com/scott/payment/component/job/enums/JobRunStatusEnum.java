package com.scott.payment.component.job.enums;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobRunStatusEnum
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务运行状态枚举
 * @status : create
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
