package com.scott.payment.component.job.enums;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobStatusEnum
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务状态枚举
 * @status : create
 */
public enum JobStatusEnum {

    /**
     * 启用状态，允许扫描和手动触发。
     */
    ENABLED,

    /**
     * 停用状态，不参与调度。
     */
    DISABLED
}
