package com.scott.payment.clearing.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 历史标签本金修复内部契约；调用方只能提交交易身份和并发前置条件，修复金额由清分事实自行校验。
 */
public final class ClearingLabelPrincipalRepairDTOs {

    private ClearingLabelPrincipalRepairDTOs() {
    }

    /**
     * 提交待双人复核的未结算标签本金修复申请。
     */
    @Data
    public static class SubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 调用方幂等键。 */
        private String requestKey;
        /** 待修复动作交易号。 */
        private String transactionId;
        /** 动作季度精确路由时间。 */
        private LocalDateTime transactionDateTime;
        /** 预期财务状态 CAS 版本。 */
        private Integer expectedFinanceStateVersion;
        /** 预期原清分修订。 */
        private Integer expectedClearingRevision;
        /** 预期未认领候选 CAS 版本。 */
        private Long expectedCandidateVersion;
        /** 修复原因，不得包含敏感支付数据。 */
        private String reason;
        /** service-admin 从登录上下文注入的可信 Maker。 */
        private String submitOperator;
    }

    /**
     * 复核标签本金修复申请；复核人必须与提交人不同。
     */
    @Data
    public static class ReviewRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 预期申请 CAS 版本。 */
        private Long expectedRequestVersion;
        /** APPROVE 或 REJECT。 */
        private String decision;
        /** Checker 复核意见。 */
        private String reviewComment;
        /** service-admin 从登录上下文注入的可信 Checker。 */
        private String reviewOperator;
    }

    /**
     * 标签本金修复申请及执行结果，不返回完整费用或商户资金明细。
     */
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
        /** 批准后生成的新修订；未执行时为空。 */
        private Integer newClearingRevision;
        /** 批准后生成的新候选号；未执行时为空。 */
        private String newCandidateNo;
        /** 当前申请 CAS 版本。 */
        private Long version;
    }
}
