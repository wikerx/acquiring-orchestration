package com.scott.payment.admin.dto.transaction;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理端退款查询、详情和审批接口模型，保留运营排障所需渠道摘要但不包含渠道凭据或原始报文。
 */
public final class AdminRefundDTOs {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private AdminRefundDTOs() {
    }

    /**
     * 管理端退款查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RefundQuery extends PageRequest {
        private static final long serialVersionUID = 1L;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;
        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String refundTransactionId;
        /**
         * 原平台交易号，用于将请款、退款、撤销、增量授权等后续动作关联到原始交易。
         */
        private String sourceTransactionId;
        /**
         * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
         */
        private String merchantOrderNo;
        /**
         * 请求中的商户动作编号，用于限定本次操作的输入和校验范围。
         */
        private String merchantOperationNo;
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         */
        private String transactionType;
        /**
         * 请求中的退款范围，用于限定本次操作的输入和校验范围。
         */
        private String refundScope;
        private String approvalStatus;
        private String transactionStatus;
        /**
         * 请求中的请求来源，用于限定本次操作的输入和校验范围。
         */
        private String requestSource;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;
        /**
         * 渠道订单号，由渠道返回，用于渠道查询、回调匹配和对账。
         */
        private String channelOrderNo;
        private String acquirerReferenceNo;
        private String paymentMethod;
        /**
         * 支付品牌编码，用于区分银行卡、钱包或本地支付品牌。
         */
        private String paymentBrand;
        private String labelCurrency;
        private String transactionCurrency;
        private BigDecimal minimumTransactionAmount;
        private BigDecimal maximumTransactionAmount;
        private String applicantId;
        private LocalDateTime beginTime;
        /**
         * 请求中的结束时间，用于限定本次操作的输入和校验范围。
         */
        private LocalDateTime endTime;
        private LocalDateTime completeBeginTime;
        private LocalDateTime completeEndTime;
        /**
         * 查询时间时区，使用 IANA 时区标识解释关联的本地日期时间。
         */
        private String queryTimeZone;
    }

    /**
     * 管理端退款列表记录。
     */
    @Data
    public static class RefundRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String refundTransactionId;
        /**
         * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
         */
        private String operationId;
        /**
         * 原平台交易号，用于将请款、退款、撤销、增量授权等后续动作关联到原始交易。
         */
        private String sourceTransactionId;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;
        /**
         * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
         */
        private String merchantOrderNo;
        /**
         * 持久化的商户动作编号，用于还原当前记录的业务事实。
         */
        private String merchantOperationNo;
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         */
        private String transactionType;
        /**
         * 持久化的退款范围，用于还原当前记录的业务事实。
         */
        private String refundScope;
        /**
         * 持久化的请求来源，用于还原当前记录的业务事实。
         */
        private String requestSource;
        /**
         * 持久化的请求原因，用于还原当前记录的业务事实。
         */
        private String requestReason;
        private String applicantType;
        private String applicantId;
        private String applicantName;
        /**
         * 持久化的{@code executionMode}，用于还原当前记录的业务事实。
         */
        private String executionMode;
        private String transactionStatus;
        /**
         * 持久化的{@code processStage}，用于还原当前记录的业务事实。
         */
        private String processStage;
        private String failReasonCode;
        /**
         * 持久化的{@code failReasonMessage}，用于还原当前记录的业务事实。
         */
        private String failReasonMessage;
        private String labelCurrency;
        private BigDecimal labelAmount;
        private String transactionCurrency;
        private BigDecimal transactionAmount;
        /**
         * 交易币种的小数位数，用于主币种单位与最小货币单位之间的精确转换。
         */
        private Integer currencyExponent;
        private String paymentMethod;
        /**
         * 支付品牌编码，用于区分银行卡、钱包或本地支付品牌。
         */
        private String paymentBrand;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;
        /**
         * 渠道订单号，由渠道返回，用于渠道查询、回调匹配和对账。
         */
        private String channelOrderNo;
        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String channelTransactionId;
        private String channelResponseCode;
        /**
         * 持久化的{@code acquirerReferenceNo}，用于还原当前记录的业务事实。
         */
        private String acquirerReferenceNo;
        private String channelMatchStatus;
        private String merchantNotificationStatus;
        /**
         * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
         */
        private LocalDateTime transactionDateTime;
        /**
         * 持久化的{@code rootTransactionDateTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime rootTransactionDateTime;
        /**
         * 持久化的{@code completeTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime completeTime;
        private String approvalId;
        private String approvalStatus;
        private String approvalPolicyCode;
        private String approvalOperatorId;
        private String approvalOperatorName;
        /**
         * 持久化的审批时间，用于还原当前记录的业务事实。
         */
        private LocalDateTime approvalTime;
        /**
         * 持久化的审批原因，用于还原当前记录的业务事实。
         */
        private String approvalReason;
        /**
         * 持久化的审批失效时间，用于还原当前记录的业务事实。
         */
        private LocalDateTime approvalExpireTime;
        private String executionEventId;
        private Integer approvalVersion;
    }

    /**
     * 分币种退款金额统计。
     */
    @Data
    public static class RefundCurrencySummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private String currency;
        /**
         * 该组交易币种的小数位数，必须与金额一同返回以支持零位及高精度币种展示。
         */
        private Integer currencyExponent;
        @JsonSerialize(using = ToStringSerializer.class)
        private BigDecimal totalAmount = BigDecimal.ZERO;
        @JsonSerialize(using = ToStringSerializer.class)
        private BigDecimal pendingApprovalAmount = BigDecimal.ZERO;
        @JsonSerialize(using = ToStringSerializer.class)
        private BigDecimal successfulAmount = BigDecimal.ZERO;
        @JsonSerialize(using = ToStringSerializer.class)
        private BigDecimal pendingAmount = BigDecimal.ZERO;
    }

    /**
     * 退款状态和金额统计。
     */
    @Data
    public static class RefundSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private long totalCount;
        private long pendingApprovalCount;
        private long processingCount;
        private long successCount;
        private long failedOrRejectedCount;
        /**
         * 按币种拆分的金额集合，禁止直接跨币种相加。
         */
        private List<RefundCurrencySummary> currencyAmounts = new ArrayList<>();
    }

    /**
     * 退款分页与统计组合响应。
     */
    @Data
    public static class RefundSearchResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private PageResult<RefundRecord> page;
        private RefundSummary summary;
    }

    /**
     * 退款详情响应。
     */
    @Data
    public static class RefundDetailResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private RefundRecord refund;
        /**
         * 原交易的完整管理端详情，沿用交易详情响应契约。
         */
        private TransactionDetailResponse transactionDetail;
    }

    /**
     * 页面提交的审批决策。
     */
    @Data
    public static class ApprovalDecisionRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String decisionRequestId;
        private Integer expectedVersion;
        /**
         * 请求中的审批原因，用于限定本次操作的输入和校验范围。
         */
        private String approvalReason;
    }

    /**
     * Admin 调用 Payment 的审批请求。
     */
    @Data
    public static class ApprovalClientRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String decisionRequestId;
        private Integer expectedVersion;
        /**
         * 执行本次管理操作的可信登录账号 ID，用于操作审计。
         */
        private String operatorId;
        /**
         * 执行本次管理操作时的账号显示名称快照，用于操作审计。
         */
        private String operatorName;
        /**
         * 请求中的审批原因，用于限定本次操作的输入和校验范围。
         */
        private String approvalReason;
    }

    /**
     * 审批结果。
     */
    @Data
    public static class ApprovalResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private String approvalId;
        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String refundTransactionId;
        private String approvalStatus;
        private String approvalOperatorId;
        private String approvalOperatorName;
        private LocalDateTime approvalTime;
        private String approvalReason;
        private String executionEventId;
        private Integer version;
    }
}
