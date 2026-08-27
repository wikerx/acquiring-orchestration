package com.scott.payment.clearing.entity;

import lombok.Data;

/** 单季度清分待处理状态聚合投影，仅用于低基数运维指标刷新。 */
@Data
public class ClearingPendingMetricsDO {

    /** 清分状态枚举名。 */
    private String clearingStatus;

    /** 当前季度该状态记录数。 */
    private Long pendingCount;

    /** 当前季度该状态最老记录等待秒数。 */
    private Long oldestPendingSeconds;
}
