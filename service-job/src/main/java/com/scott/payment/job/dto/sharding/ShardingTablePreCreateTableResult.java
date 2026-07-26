package com.scott.payment.job.dto.sharding;

import lombok.Data;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateTableResult
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Sharding Table Pre Create Table Result 协作组件，位于 调度任务服务，封装 shardingtableprecreatetableresult 相关的校验、转换、持久化访问或运行时协作入口。
 * @status : create
 */
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
