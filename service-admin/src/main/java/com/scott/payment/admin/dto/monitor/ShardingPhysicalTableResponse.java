package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分表物理表响应模型。
 *
 * <p>用于后台展示单张物理表的登记状态、季度范围、自增区间和结构校验结果。</p>
 */
@Data
public class ShardingPhysicalTableResponse {

    private Long id;

    private String logicalTable;

    private String templateTable;

    private String physicalTable;

    private String shardingColumn;

    private String strategy;

    private Integer year;

    private Integer quarter;

    private String quarterSuffix;

    private String dataSource;

    private String tableStatus;

    private Integer autoCreated;

    private Long autoIncrementStart;

    private Long autoIncrementCurrent;

    private Long autoIncrementMax;

    private String schemaCheckStatus;

    private LocalDateTime lastCheckTime;

    private LocalDateTime createdTime;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
