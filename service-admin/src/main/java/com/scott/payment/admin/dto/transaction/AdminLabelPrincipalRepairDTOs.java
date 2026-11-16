package com.scott.payment.admin.dto.transaction;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Admin 历史标签本金修复契约；浏览器模型不得携带可信操作人。
 */
public final class AdminLabelPrincipalRepairDTOs {

    private AdminLabelPrincipalRepairDTOs() {
    }

    @Data
    public static class SubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 浏览器生成的请求幂等键。 */
        private String requestKey;
        /** 待修复动作交易号。 */
        private String transactionId;
        /** 动作季度精确路由时间。 */
        private LocalDateTime transactionDateTime;
        /** 页面读取到的财务状态 CAS 版本。 */
        private Integer expectedFinanceStateVersion;
        /** 页面读取到的原清分修订。 */
        private Integer expectedClearingRevision;
        /** 页面读取到的未认领候选 CAS 版本。 */
        private Long expectedCandidateVersion;
        /** 运营修复原因，不得包含敏感支付数据。 */
        private String reason;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InternalSubmitRequest extends SubmitRequest {
        private static final long serialVersionUID = 1L;
        /** Admin 服务从可信登录上下文注入的 Maker。 */
        private String submitOperator;
    }

    @Data
    public static class ReviewRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 页面读取到的修复申请 CAS 版本。 */
        private Long expectedRequestVersion;
        /** APPROVE 或 REJECT。 */
        private String decision;
        /** Checker 复核意见。 */
        private String reviewComment;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InternalReviewRequest extends ReviewRequest {
        private static final long serialVersionUID = 1L;
        /** Admin 服务从可信登录上下文注入的 Checker。 */
        private String reviewOperator;
    }

    @Data
    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 稳定修复单号。 */
        private String repairNo;
        /** 当前审批或执行状态。 */
        private String status;
        /** 待修复动作交易号。 */
        private String transactionId;
        /** 动作季度精确路由时间。 */
        private LocalDateTime transactionDateTime;
        /** 原清分修订。 */
        private Integer oldClearingRevision;
        /** 批准后生成的新清分修订。 */
        private Integer newClearingRevision;
        /** 批准后生成的新候选号。 */
        private String newCandidateNo;
        /** 当前修复申请 CAS 版本。 */
        private Long version;
    }
}
