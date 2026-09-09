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

    /** 动作财务状态业务号。 */
    private String financeStateId;
    /** 动作交易号。 */
    private String transactionId;
    /** 动作季度分片时间。 */
    private LocalDateTime transactionDateTime;
    /** 冻结时当前有效清分修订。 */
    private Integer clearingRevision;
    /** 冻结时财务状态 CAS 版本。 */
    private Integer financeStateVersion;
    /** 原清分完成 UTC 时间，用于稳定排序。 */
    private LocalDateTime clearingCompleteTime;
    /** 冻结时结算状态；必须为 NOT_SETTLED。 */
    private String settlementStatus;
    /** 当前清分修订关联保证金明细数；大于零时阻断阶梯重放。 */
    private Long reserveDetailCount;
}
