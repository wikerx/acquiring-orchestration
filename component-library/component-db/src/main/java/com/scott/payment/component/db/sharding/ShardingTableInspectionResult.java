package com.scott.payment.component.db.sharding;

import lombok.Data;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTableInspectionResult
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Sharding Table Inspection Result 协作组件，位于 公共组件库，封装 shardingtableinspectionresult 相关的校验、转换、持久化访问或运行时协作入口。
 * @status : create
 */
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
