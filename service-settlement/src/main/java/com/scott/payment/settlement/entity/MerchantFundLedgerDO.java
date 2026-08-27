package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 结算侧不可变资金流水写入模型；一批最多写一条净结算或冲正流水。 */
@Data
public class MerchantFundLedgerDO {
    private Long id;
    private String ledgerNo;
    private String ledgerGroupNo;
    private Long accountId;
    private String merchantId;
    private String businessType;
    private String summary;
    private String businessNo;
    private String settlementBatchNo;
    private String currency;
    private String direction;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private Long accountSequence;
    private String operationMode;
    private String operatorName;
    private LocalDateTime businessTime;
    private LocalDateTime postedTime;
    private String idempotencyKey;
    private Long reversalOfLedgerId;
    private LocalDateTime createTime;
}
