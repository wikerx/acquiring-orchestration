package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分表建表任务日志响应模型。
 *
 * <p>用于后台展示一次分表预建表批次的计划数量、处理结果和执行摘要。</p>
 */
@Data
public class ShardingTableCreateLogResponse {

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
