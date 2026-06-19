package com.scott.payment.payout.api.internal.dto;

import lombok.Data;

/**
 * 代付内部创建结果。
 */
@Data
public class PayoutCreateResultDTO {

    /**
     * 平台代付单号。
     */
    private String payoutOrderNo;

    /**
     * 商户代付单号。
     */
    private String merchantOrderNo;

    /**
     * 当前状态。
     */
    private String status;
}
