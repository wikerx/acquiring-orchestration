package com.scott.payment.payout.api.internal.dto;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutCreateResultDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Payout Create Result 数据传输对象，位于 service-payout 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
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
