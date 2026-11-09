package com.scott.payment.admin.dto.transaction;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端已入账历史标签本金差额调整契约；浏览器模型不接受操作人身份字段。
 */
public final class AdminSettlementPrincipalCorrectionDTOs {

    private AdminSettlementPrincipalCorrectionDTOs() {
    }

    /** 浏览器 Maker 申请。 */
    @Data
    public static class SubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 浏览器生成的 Maker 请求幂等键。 */
        private String requestKey;
        /** 待修复的已入账正式结算批次号。 */
        private String originalBatchNo;
        /** 页面读取到的原批次 CAS 版本。 */
        private Long expectedBatchVersion;
        /** Maker 填写的业务调整原因。 */
        private String reason;
    }

    /** 浏览器 Checker 意见；决策由具体路由固定。 */
    @Data
    public static class DecisionRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 浏览器生成的 Checker 决策幂等键。 */
        private String requestKey;
        /** 页面读取到的调整单 CAS 版本。 */
        private Long expectedVersion;
        /** Checker 复核意见。 */
        private String comment;
    }

    /** service-admin 注入可信 Maker 后的内部申请。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InternalSubmitRequest extends SubmitRequest {
        private static final long serialVersionUID = 1L;
        /** 从可信管理会话注入的 Maker 账号 ID。 */
        private Long operatorId;
        /** Maker 展示名快照。 */
        private String operatorName;
        /** Maker 当前角色集合快照。 */
        private String roleSnapshot;
        /** 通过网关可信头解析的客户端 IP。 */
        private String clientIp;
        /** Maker 浏览器 User-Agent 审计值。 */
        private String userAgent;
        /** service-admin 生成的实际提交时间。 */
        private LocalDateTime operationTime;
    }

    /** service-admin 注入可信 Checker 后的内部决策。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InternalDecisionRequest extends DecisionRequest {
        private static final long serialVersionUID = 1L;
        /** 由批准或拒绝路由固定注入的决策，不接受浏览器自报。 */
        private String decision;
        /** 从可信管理会话注入的 Checker 账号 ID。 */
        private Long operatorId;
        /** Checker 展示名快照。 */
        private String operatorName;
        /** Checker 当前角色集合快照。 */
        private String roleSnapshot;
        /** 通过网关可信头解析的客户端 IP。 */
        private String clientIp;
        /** Checker 浏览器 User-Agent 审计值。 */
        private String userAgent;
        /** service-admin 生成的实际决策时间。 */
        private LocalDateTime operationTime;
    }

    /** 调整申请或复核结果。 */
    @Data
    public static class CommandResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 调整主单号。 */
        private String correctionNo;
        /** PENDING_REVIEW、EXECUTED 或 REJECTED。 */
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
        /** 已完成三层交易投影修正条数。 */
        private Integer projectionCompletedCount;
        /** 调整单最新 CAS 版本。 */
        private Long version;
    }
}
