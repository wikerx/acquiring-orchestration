package com.scott.payment.component.db.sharding;

import lombok.Data;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTableInspectionResult
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : ShardingTableInspectionResult Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
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
