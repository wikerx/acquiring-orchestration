package com.scott.payment.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分表创建日志持久化模型，记录批次、触发来源、执行状态和审计时间。
 */
@Data
@TableName("sys_sharding_table_create_log")
public class SysShardingTableCreateLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 批次号，用于关联同一次导入、抓取、清分或结算处理的记录。
     */
    private String batchNo;

    /**
     * 触发类型，用于区分手工触发、定时调度、失败重试或系统补偿执行。
     */
    private String triggerType;

    /**
     * 持久化的{@code dryRun}，用于还原当前记录的业务事实。
     */
    private Integer dryRun;

    /**
     * 持久化的目标分表季度，用于还原当前记录的业务事实。
     */
    private String targetQuarters;

    private Integer plannedCount;

    private Integer createdCount;

    private Integer skippedCount;

    private Integer failedCount;

    private Integer schemaMismatchCount;

    private String runStatus;

    /**
     * 持久化的结果汇总，用于还原当前记录的业务事实。
     */
    private String resultSummary;

    /**
     * 内部错误摘要，用于运营排障；禁止包含密钥、卡数据和完整报文。
     */
    private String errorMessage;

    /**
     * 持久化的开始时间，用于还原当前记录的业务事实。
     */
    private LocalDateTime startTime;

    /**
     * 持久化的结束时间，用于还原当前记录的业务事实。
     */
    private LocalDateTime endTime;

    /**
     * 持久化的{@code durationMs}，用于还原当前记录的业务事实。
     */
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
