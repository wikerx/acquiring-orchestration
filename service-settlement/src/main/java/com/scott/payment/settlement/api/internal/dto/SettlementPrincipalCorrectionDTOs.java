package com.scott.payment.settlement.api.internal.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 已入账历史标签本金差额调整内部契约；操作人字段只接受 service-admin 可信注入。
 */
public final class SettlementPrincipalCorrectionDTOs {

    private SettlementPrincipalCorrectionDTOs() {
    }

    /** Maker 提交请求。 */
    @Data
    public static class SubmitRequest {
        /** Maker 请求幂等键。 */
        private String requestKey;
        /** 原已入账正式结算批次号。 */
        private String originalBatchNo;
        /** 原批次期望 CAS 版本。 */
        private Long expectedBatchVersion;
        /** 调整原因。 */
        private String reason;
        /** service-admin 注入的 Maker 账号 ID。 */
        private Long operatorId;
        /** Maker 展示名快照。 */
        private String operatorName;
        /** Maker 角色集合快照。 */
        private String roleSnapshot;
        /** Maker 客户端 IP 审计值。 */
        private String clientIp;
        /** Maker 客户端 User-Agent 审计值。 */
        private String userAgent;
        /** Maker 实际提交时间。 */
        private LocalDateTime operationTime;
    }

    /** Checker 决策请求。 */
    @Data
    public static class DecisionRequest {
        /** Checker 决策幂等键。 */
        private String requestKey;
        /** 调整单期望 CAS 版本。 */
        private Long expectedVersion;
        /** APPROVE 或 REJECT。 */
        private String decision;
        /** Checker 复核意见。 */
        private String comment;
        /** service-admin 注入的 Checker 账号 ID。 */
        private Long operatorId;
        /** Checker 展示名快照。 */
        private String operatorName;
        /** Checker 角色集合快照。 */
        private String roleSnapshot;
        /** Checker 客户端 IP 审计值。 */
        private String clientIp;
        /** Checker 客户端 User-Agent 审计值。 */
        private String userAgent;
        /** Checker 实际决策时间。 */
        private LocalDateTime operationTime;
    }

    /** 申请或复核后的调整单摘要。 */
    @Data
    public static class CommandResponse {
        /** 调整主单号。 */
        private String correctionNo;
        /** 调整单权威状态。 */
        private String correctionStatus;
        /** 原已入账结算批次号。 */
        private String originalBatchNo;
        /** 原批次平台商户号。 */
        private String merchantId;
        /** 差额资金 ISO 币种。 */
        private String currency;
        /** 商户余额视角 CREDIT 或 DEBIT。 */
        private String correctionDirection;
        /** 非负差额资金金额。 */
        private BigDecimal correctionAmount;
        /** 冻结异常本金条数。 */
        private Integer itemCount;
        /** 已完成交易投影修正条数。 */
        private Integer projectionCompletedCount;
        /** 调整单最新 CAS 版本。 */
        private Long version;
    }
}
