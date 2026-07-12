package com.scott.payment.component.db.sharding;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTableInspectionResult
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Sharding Table Inspection Result，位于 component-library/component-db 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class ShardingTableInspectionResult {

    /**
     * 表名。
     */
    private String tableName;

    /**
     * 表是否存在。
     */
    private boolean exists;

    /**
     * 表结构创建语句。
     */
    private String createTableSql;

    /**
     * 当前 AUTO_INCREMENT 值。
     */
    private Long autoIncrementCurrent;

    /**
     * 自增主键字段检查结果。
     */
    private boolean idColumnMatched;

    /**
     * 分表字段是否存在。
     */
    private boolean shardingColumnExists;

    /**
     * 与模板表结构是否一致。
     */
    private boolean schemaMatched;

    /**
     * 检查状态：MATCHED/MISMATCHED/SKIPPED/FAILED。
     */
    private String schemaCheckStatus;

    /**
     * 检查说明或失败原因。
     */
    private String message;
}
