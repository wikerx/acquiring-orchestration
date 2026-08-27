package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 保证金聚合的不可变动作幂等记录；HOLD、RETURN、RELEASE 和 REVERSAL 各自只应用一次。 */
@Data
public class MerchantReserveActionDO {
    private Long id;
    private String reserveActionNo;
    private Long reserveItemId;
    private String reserveNo;
    private String settlementBatchNo;
    private Long candidateId;
    private String sourceReserveDetailNo;
    private String actionType;
    private String currency;
    private BigDecimal amount;
    private LocalDateTime actionTime;
    private LocalDateTime createTime;
}
