package com.scott.payment.admin.dto.monitor;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分表创建日志响应，展示批次执行结果及必要的错误摘要。
 */
@Data
public class ShardingTableCreateLogResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 批次号，用于关联同一次导入、抓取、清分或结算处理的记录。
     */
    private String batchNo;

    /**
     * 触发类型，用于区分手工触发、定时调度、失败重试或系统补偿执行。
     */
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

    /**
     * 内部错误摘要，用于运营排障；禁止包含密钥、卡数据和完整报文。
     */
    private String errorMessage;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationMs;

    /**
     * 执行本次管理操作的可信登录账号 ID，用于操作审计。
     */
    private String operatorId;

    /**
     * 执行本次管理操作时的账号显示名称快照，用于操作审计。
     */
    private String operatorName;

    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createTime;
}
