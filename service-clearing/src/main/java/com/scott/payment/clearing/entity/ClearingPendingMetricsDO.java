package com.scott.payment.clearing.entity;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingPendingMetricsDO
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 单季度清分待处理状态聚合投影，仅用于低基数运维指标刷新。
 * @status : update
 */
@Data
public class ClearingPendingMetricsDO {

    /** 清分状态枚举名。 */
    private String clearingStatus;

    /** 当前季度该状态记录数。 */
    private Long pendingCount;

    /** 当前季度该状态最老记录等待秒数。 */
    private Long oldestPendingSeconds;
}
