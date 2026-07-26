package com.scott.payment.component.job.enums;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecuteModeEnum
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务执行模式枚举
 * @status : create
 */
public enum JobExecuteModeEnum {

    /**
     * 同步执行，调度线程等待处理器返回最终结果。
     */
    SYNC,

    /**
     * 异步执行，任务提交后由异步线程继续执行并回写日志。
     */
    ASYNC
}
