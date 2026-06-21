package com.scott.payment.component.db.sharding;

import lombok.Data;

/**
 * 分表物理表结构检查结果。
 *
 * <p>用于 dryRun、建表后复核和管理后台展示，不承载业务交易数据。</p>
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
