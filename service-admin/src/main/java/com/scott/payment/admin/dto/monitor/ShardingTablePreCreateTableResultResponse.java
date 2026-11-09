package com.scott.payment.admin.dto.monitor;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 单个物理分表的预创建结果响应。
 */
@Data
public class ShardingTablePreCreateTableResultResponse {

    /**
     * 表族：TRANSACTION 或 RISK_AUDIT。
     */
    private String tableFamily;

    private String logicalTable;

    private String templateTable;

    private String physicalTable;

    private String targetQuarter;

    private String status;

    private String schemaCheckStatus;

    /**
     * 分片时间字段 DATETIME(3) 校验状态。
     */
    private String shardingTimeCheckStatus;

    /**
     * 物理表 utf8mb4 字符集校验状态。
     */
    private String charsetCheckStatus;

    /**
     * 当前 AUTO_INCREMENT 是否处于季度安全号段。
     */
    private String autoIncrementCheckStatus;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long autoIncrementStart;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long autoIncrementCurrent;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long autoIncrementMax;

    private String message;
}
