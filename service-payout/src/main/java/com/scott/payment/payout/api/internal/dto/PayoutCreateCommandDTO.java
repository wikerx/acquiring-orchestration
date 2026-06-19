package com.scott.payment.payout.api.internal.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 代付内部创建命令。
 */
@Data
public class PayoutCreateCommandDTO {

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 商户代付单号。
     */
    private String merchantOrderNo;

    /**
     * 代付金额。
     */
    private BigDecimal amount;

    /**
     * 币种。
     */
    private String currency;

    /**
     * 请求时间。
     */
    private LocalDateTime transactionDateTime;
}
