package com.scott.payment.job.dto.sharding;

import lombok.Data;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateTableResult
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : ShardingTablePreCreateTableResult 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 调度任务服务层，输入输出边界由所在包和公开方法契约限定。
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
