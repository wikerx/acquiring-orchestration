package com.scott.payment.component.db.sharding;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTableInspectionResult
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : 分表表inspection协作组件，位于 公共组件库，封装该业务的本地校验、转换或运行时协作入口。
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

    /** 分片时间字段是否为 DATETIME(3)。 */
    private boolean shardingColumnPrecisionMatched;

    /** 表字符集是否为 utf8mb4。 */
    private boolean charsetMatched;

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
