package com.scott.payment.payout.api.internal.dto;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutCreateResultDTO
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : 代付create响应模型，位于 代付服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
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
