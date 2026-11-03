package com.scott.payment.merchant.dto.transaction;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
 * 商户端退款查询模型，只保留当前商户可见的退款事实和统一状态说明，排除渠道及内部审批信息。
 */
public final class MerchantRefundDTOs {

    /**
     * 禁止实例化商户退款 DTO 聚合类。
     */
    private MerchantRefundDTOs() {
    }

    /**
     * 商户退款查询条件；merchantId 仅由服务端认证上下文写入。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RefundQuery extends PageRequest {
        private static final long serialVersionUID = 1L;
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
        private String merchantOperationNo;
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         */
        private String transactionType;
        private String refundScope;
        private String approvalStatus;
        private String transactionStatus;
        private String requestSource;
        private String labelCurrency;
        private String transactionCurrency;
        private BigDecimal minimumTransactionAmount;
        private BigDecimal maximumTransactionAmount;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private LocalDateTime completeBeginTime;
        private LocalDateTime completeEndTime;
        /**
         * 查询时间时区，使用 IANA 时区标识解释关联的本地日期时间。
         */
        private String queryTimeZone;
    }

    /**
     * 商户可见退款记录。
     */
    @Data
    public static class RefundRecord implements Serializable {
        private static final long serialVersionUID = 1L;
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
        private String transactionStatus;
        private String processStage;
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
        private String merchantNotificationStatus;
        /**
         * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
         */
        private LocalDateTime transactionDateTime;
        private LocalDateTime completeTime;
        private String approvalId;
        private String approvalStatus;
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

        private Integer approvalVersion;
        /**
         * 持久化的商户可见说明，用于还原当前记录的业务事实。
         */
        private String merchantVisibleMessage;
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
     * 商户退款状态和金额统计。
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
     * 商户退款分页和统计。
     */
    @Data
    public static class RefundSearchResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private PageResult<RefundRecord> page;
        private RefundSummary summary;
    }

    /**
     * 商户退款详情，仅返回商户可见记录。
     */
    @Data
    public static class RefundDetailResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private RefundRecord refund;
    }
}
