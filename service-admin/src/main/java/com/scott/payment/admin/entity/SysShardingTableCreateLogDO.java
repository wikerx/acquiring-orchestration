package com.scott.payment.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分表建表任务日志数据对象。
 *
 * <p>用于管理后台查询 service-job 写入的分表预建表批次结果。</p>
 */
@Data
@TableName("sys_sharding_table_create_log")
public class SysShardingTableCreateLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;

    private String triggerType;

    private Integer dryRun;

    private String targetQuarters;

    private Integer plannedCount;

    private Integer createdCount;

    private Integer skippedCount;

    private Integer failedCount;

    private Integer schemaMismatchCount;

    private String runStatus;

    private String resultSummary;

    private String errorMessage;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationMs;

    private String operatorId;

    private String operatorName;

    private LocalDateTime createTime;
}
