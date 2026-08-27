package com.scott.payment.clearing.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTierPeriodReplayItemFactsDO
 * @date : 2026-08-26 19:30
 * @email : scott_x@163.com
 * @description : 重放准备阶段读取的动作、结算和保证金门禁事实；仅作事务内冻结输入，不作为新的资金事实。
 * @status : create
 */
@Data
public class ClearingTierPeriodReplayItemFactsDO {

    private String financeStateId;
    private String transactionId;
    private LocalDateTime transactionDateTime;
    private Integer clearingRevision;
    private Integer financeStateVersion;
    private LocalDateTime clearingCompleteTime;
    private String settlementStatus;
    private Long reserveDetailCount;
}
