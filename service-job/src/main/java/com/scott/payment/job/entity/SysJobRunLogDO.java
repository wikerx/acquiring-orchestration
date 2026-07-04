package com.scott.payment.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysJobRunLogDO
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 系统任务运行日志数据对象
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysJobRunLogDO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Job Run Log 数据库实体，位于 service-job 的数据实体层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@TableName("sys_job_run_log")
public class SysJobRunLogDO {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 执行批次号。
     */
    private String runId;

    /**
     * 任务 ID。
     */
    private Long jobId;

    /**
     * 任务编码。
     */
    private String jobCode;

    /**
     * 任务名称。
     */
    private String jobName;

    /**
     * 处理器编码。
     */
    private String handlerCode;

    /**
     * 触发类型。
     */
    private String triggerType;

    /**
     * 调度模式。
     */
    private String schedulerMode;

    /**
     * 执行模式。
     */
    private String executeMode;

    /**
     * 执行节点。
     */
    private String executorNode;

    /**
     * 运行状态。
     */
    private String runStatus;

    /**
     * 开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 结束时间。
     */
    private LocalDateTime endTime;

    /**
     * 执行耗时，毫秒。
     */
    private Long durationMs;

    /**
     * 当前重试序号。
     */
    private Integer retryIndex;

    /**
     * 最大重试次数。
     */
    private Integer maxRetryCount;

    /**
     * 超时时间快照，单位秒。
     */
    private Integer timeoutSeconds;

    /**
     * 参数快照，已脱敏。
     */
    private String paramsSnapshot;

    /**
     * 结果摘要。
     */
    private String resultMessage;

    /**
     * 错误摘要。
     */
    private String errorMessage;

    /**
     * 链路追踪 ID。
     */
    private String traceId;

    /**
     * 操作人 ID。
     */
    private String operatorId;

    /**
     * 操作人名称。
     */
    private String operatorName;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
