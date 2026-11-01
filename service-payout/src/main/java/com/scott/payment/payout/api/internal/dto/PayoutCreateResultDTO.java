package com.scott.payment.payout.api.internal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 服务间创建代付交易的响应模型。
 *
 * <p>仅返回调用方需要的订单标识和平台状态，不暴露渠道或持久化模型。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
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
