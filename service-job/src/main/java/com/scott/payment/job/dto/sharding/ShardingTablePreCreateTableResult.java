package com.scott.payment.job.dto.sharding;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateTableResult
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Sharding Table Pre Create Table Result，位于 service-job 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
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
