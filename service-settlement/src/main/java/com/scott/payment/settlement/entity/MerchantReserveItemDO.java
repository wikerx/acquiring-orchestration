package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 商户保证金聚合；币种始终是原支付标签币种，不参与结算汇率转换。 */
@Data
public class MerchantReserveItemDO {
    private Long id;
    private String reserveNo;
    private Long accountId;
    private String merchantId;
    private String sourceTransactionId;
    private String sourceBusinessNo;
    private String currency;
    private BigDecimal retainedAmount;
    private BigDecimal returnedAmount;
    private BigDecimal releasedAmount;
    private BigDecimal reversedAmount;
    private String reserveStatus;
    private LocalDate expectedReleaseDate;
    private String releaseBatchNo;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
