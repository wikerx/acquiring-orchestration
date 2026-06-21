package com.scott.payment.job.dto.sharding;

import lombok.Data;

/**
 * 单张分表物理表预创建结果。
 */
@Data
public class ShardingTablePreCreateTableResult {

    /**
     * 逻辑表名。
     */
    private String logicalTable;

    /**
     * 模板表名。
     */
    private String templateTable;

    /**
     * 物理表名。
     */
    private String physicalTable;

    /**
     * 目标季度文本。
     */
    private String targetQuarter;

    /**
     * 处理状态：CREATED/SKIPPED/FAILED/MISMATCHED/DRY_RUN。
     */
    private String status;

    /**
     * 表结构校验状态。
     */
    private String schemaCheckStatus;

    /**
     * AUTO_INCREMENT 起始值。
     */
    private Long autoIncrementStart;

    /**
     * AUTO_INCREMENT 当前值。
     */
    private Long autoIncrementCurrent;

    /**
     * AUTO_INCREMENT 最大安全值。
     */
    private Long autoIncrementMax;

    /**
     * 说明或失败原因。
     */
    private String message;
}
