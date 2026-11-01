package com.scott.payment.openapi.client.payout.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 代付核心返回的创建受理结果，包含平台代付单号、当前状态和商户订单号。
 */
@Data
public class PayoutCreateClientResponseDTO implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

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
