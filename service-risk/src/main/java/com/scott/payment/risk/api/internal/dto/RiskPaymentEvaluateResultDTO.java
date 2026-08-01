package com.scott.payment.risk.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskPaymentEvaluateResultDTO
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单支付实时风控评估结果 DTO，位于 service-risk 内部接口 DTO 层，返回风控决策和原因码，不直接返回交易状态。
 * @status : create
 */
@Data
public class RiskPaymentEvaluateResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 风控评估流水号，用于后续审计、排查和人工复核关联。
     */
    private String riskRecordNo;

    /**
     * 风控决策编码，例如 PASS、REJECT、REVIEW、REQUIRE_3DS。
     */
    private String decision;

    /**
     * 风控原因码，用于区分阻断、复核、3DS 要求等决策来源。
     */
    private String reasonCode;

    /**
     * 风控原因描述，内部可读，返回商户前必须由 payment 或 OpenAPI 做产品化转换。
     */
    private String reasonMessage;

    /**
     * 风控评估完成时间，使用本地业务时区时间点。
     */
    private LocalDateTime decisionTime;

    /**
     * 本次 PASS 是否创建了商户累计限额预占。只暴露生命周期事实，不暴露 Redis 物理 Key。
     */
    private boolean merchantLimitReserved;
}
