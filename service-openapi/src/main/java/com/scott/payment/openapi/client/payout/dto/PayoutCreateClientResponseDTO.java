package com.scott.payment.openapi.client.payout.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * service-payout 创建代付交易的内部响应参数。
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
