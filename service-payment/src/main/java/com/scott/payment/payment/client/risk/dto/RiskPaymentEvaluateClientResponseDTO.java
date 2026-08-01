package com.scott.payment.payment.client.risk.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskPaymentEvaluateClientResponseDTO
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : service-risk 支付风控评估响应 DTO，位于 service-payment 客户端 DTO 层，用于承载风控决策和原因码。
 * @status : create
 */
@Data
public class RiskPaymentEvaluateClientResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 风控评估流水号。
     */
    private String riskRecordNo;

    /**
     * 风控决策编码。
     */
    private String decision;

    /**
     * 风控原因码。
     */
    private String reasonCode;

    /**
     * 风控原因描述。
     */
    private String reasonMessage;

    /**
     * 风控评估完成时间。
     */
    private LocalDateTime decisionTime;

    /**
     * 风控是否为本次交易创建了商户累计限额预占。
     */
    private boolean merchantLimitReserved;
}
