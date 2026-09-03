package com.scott.payment.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysShardingTableCreateLogDO
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : sys分表表create日志持久化模型，位于 调度任务服务，映射数据库中的业务事实、状态、版本和审计字段，不作为外部接口模型。
 * @status : create
 */
@Data
@TableName("sys_sharding_table_create_log")
public class SysShardingTableCreateLogDO {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务批次号。
     */
    private String batchNo;

    /**
     * 触发方式。
     */
    private String triggerType;

    /**
     * 是否仅预演。
     */
    private Integer dryRun;

    /**
     * 目标季度列表文本。
     */
    private String targetQuarters;

    /**
     * 计划处理数量。
     */
    private Integer plannedCount;

    /**
     * 创建数量。
     */
    private Integer createdCount;

    /**
     * 跳过数量。
     */
    private Integer skippedCount;

    /**
     * 失败数量。
     */
    private Integer failedCount;

    /**
     * 结构不一致数量。
     */
    private Integer schemaMismatchCount;

    /**
     * 执行状态。
     */
    private String runStatus;

    /**
     * 执行摘要 JSON。
     */
    private String resultSummary;

    /**
     * 失败原因。
     */
    private String errorMessage;

    /**
     * 开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 结束时间。
     */
    private LocalDateTime endTime;

    /**
     * 耗时毫秒。
     */
    private Long durationMs;

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
}
