package com.scott.payment.settlement.entity;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementOperationalStateDO
 * @date : 2026-08-26 21:10
 * @email : scott_x@163.com
 * @description : 批次投影任务和 Outbox 的只读聚合行，用于运营详情判断异步联动是否收敛。
 * @status : create
 */
@Data
public class SettlementOperationalStateDO {
    private Long projectionTaskCount;
    private Long projectionCompletedCount;
    private Long projectionFailedCount;
    private Long outboxEventCount;
    private Long outboxSentCount;
    private Long outboxFailedCount;
}
