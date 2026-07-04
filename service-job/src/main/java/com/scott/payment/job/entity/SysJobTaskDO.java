package com.scott.payment.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysJobTaskDO
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 系统任务任务数据对象
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysJobTaskDO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Job Task 数据库实体，位于 service-job 的数据实体层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@TableName("sys_job_task")
public class SysJobTaskDO {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务编码。
     */
    private String jobCode;

    /**
     * 任务名称。
     */
    private String jobName;

    /**
     * 任务分组。
     */
    private String jobGroup;

    /**
     * 处理器编码。
     */
    private String handlerCode;

    /**
     * Cron 表达式。
     */
    private String cronExpression;

    /**
     * 调度模式。
     */
    private String schedulerMode;

    /**
     * 触发模式。
     */
    private String triggerMode;

    /**
     * 执行模式。
     */
    private String executeMode;

    /**
     * 路由策略。
     */
    private String routeStrategy;

    /**
     * 错过调度策略。
     */
    private String misfireStrategy;

    /**
     * 超时时间，单位秒。
     */
    private Integer timeoutSeconds;

    /**
     * 重试次数。
     */
    private Integer retryCount;

    /**
     * 重试间隔秒数。
     */
    private Integer retryIntervalSeconds;

    /**
     * 是否允许并发执行。
     */
    private Integer allowConcurrent;

    /**
     * 默认任务参数。
     */
    private String params;

    /**
     * 启停状态。
     */
    private String status;

    /**
     * 任务说明。
     */
    private String description;

    /**
     * 下次触发时间。
     */
    private LocalDateTime nextTriggerTime;

    /**
     * 上次触发时间。
     */
    private LocalDateTime lastTriggerTime;

    /**
     * 上次执行状态。
     */
    private String lastRunStatus;

    /**
     * 当前锁持有节点。
     */
    private String lockOwner;

    /**
     * 锁过期时间。
     */
    private LocalDateTime lockUntil;

    /**
     * 乐观锁版本。
     */
    private Integer version;

    /**
     * 逻辑删除标记。
     */
    private Integer deleted;

    /**
     * 创建人。
     */
    private String createBy;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新人。
     */
    private String updateBy;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
