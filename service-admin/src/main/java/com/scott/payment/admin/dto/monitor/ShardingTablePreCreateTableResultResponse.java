package com.scott.payment.admin.dto.monitor;

import lombok.Data;

/**
 * 单张分表物理表预创建结果响应。
 */
@Data
public class ShardingTablePreCreateTableResultResponse {

    private String logicalTable;

    private String templateTable;

    private String physicalTable;

    private String targetQuarter;

    private String status;

    private String schemaCheckStatus;

    private Long autoIncrementStart;

    private Long autoIncrementCurrent;

    private Long autoIncrementMax;

    private String message;
}
