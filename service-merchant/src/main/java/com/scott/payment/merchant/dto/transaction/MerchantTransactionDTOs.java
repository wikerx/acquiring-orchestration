package com.scott.payment.merchant.dto.transaction;

import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantTransactionDTOs
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户后台交易查询 DTO 集合，位于 service-merchant 接口传输层，仅承载当前登录商户可见的交易查询、详情、统计和后续动作数据。
 * @status : create
 */
public final class MerchantTransactionDTOs {

    private MerchantTransactionDTOs() {
    }

    /**
     * 商户交易查询分页条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TransactionPageQuery extends PageRequest {

        private static final long serialVersionUID = 1L;

        /**
         * 平台商户号，仅允许服务端根据登录上下文覆盖，前端传入值会被忽略。
         */
        private String merchantId;

        /**
         * 商户订单号，可为空；商户号由服务端登录上下文强制补齐。
         */
        private String merchantOrderNo;

        /**
         * 平台交易 ID，可为空。
         */
        private String transactionId;

        /**
         * 原平台交易 ID，可为空。
         */
        private String sourceTransactionId;

        /**
         * 交易类型，对齐 transaction_type 字典。
         */
        private String transactionType;

        /**
         * 交易状态，对齐 transaction_status 字典。
         */
        private String transactionStatus;

        /**
         * 支付方式，例如 BANK_CARD、PAYPAL。
         */
        private String paymentMethod;

        /**
         * 卡品牌或钱包品牌。
         */
        private String paymentBrand;

        /**
         * 渠道订单号，可用于商户对账排查。
         */
        private String channelOrderNo;

        /**
         * 商户侧可见响应码。
         */
        private String merchantResponseCode;

        /**
         * 渠道结果勾兑状态。
         */
        private String channelMatchStatus;

        /**
         * 对账状态。
         */
        private String reconciliationStatus;

        /**
         * 结算状态。
         */
        private String settlementStatus;

        /**
         * 查询开始交易时间。
         */
        private LocalDateTime beginTime;

        /**
         * 查询结束交易时间。
         */
        private LocalDateTime endTime;

        /**
         * 查询时区，支付核心按该时区解释 beginTime/endTime。
         */
        private String queryTimeZone;
    }

    /**
     * 商户交易动作请求。
     */
    @Data
    public static class TransactionActionRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 本次动作唯一请求号；为空时商户后台生成并作为支付核心幂等键组成部分。
         */
        private String merchantOrderId;

        /**
         * 动作金额，退款必填。
         */
        private BigDecimal amount;

        /**
         * 动作币种，默认取原交易币种。
         */
        private String currency;

        /**
         * 商户操作原因，写入交易描述和操作审计。
         */
        private String reason;
    }

    /**
     * 商户交易动作响应。
     */
    @Data
    public static class TransactionActionResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        private String transactionId;

        private String sourceTransactionId;

        private String merchantOrderNo;

        private String merchantOrderId;

        private String transactionType;

        private String status;

        private String merchantResponseCode;

        private String merchantResponseMessage;

        private String processStage;

        private String failReasonCode;

        private String pendingReasonCode;

        private Long amount;

        private String currency;
    }

    /**
     * 商户交易主单列表响应。
     */
    @Data
    public static class TransactionOrderResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        private String operationId;

        private String rootTransactionId;

        private String latestTransactionId;

        private String merchantId;

        private String merchantOrderNo;

        private String merchantOrderId;

        private String paymentMethod;

        private String paymentBrand;

        private String cardBin;

        private String cardNumberMasked;

        private String authCode;

        private String transactionType;

        private String transactionStatus;

        private String lifecycleStatus;

        private String lifecycleStatusMessage;

        private String processStage;

        private String labelCurrency;

        private BigDecimal labelAmount;

        private String transactionCurrency;

        private BigDecimal transactionAmount;

        private BigDecimal currentAmount;

        private String currentCurrency;

        private Integer currencyExponent;

        private BigDecimal transactionRate;

        private Integer dccEnabled;

        private Integer edcEnabled;

        private String merchantResponseCode;

        private String merchantResponseMessage;

        private BigDecimal authorizedAmount;

        private BigDecimal capturedAmount;

        private BigDecimal refundedAmount;

        private BigDecimal availableCaptureAmount;

        private BigDecimal availableRefundAmount;

        private String settlementStatus;

        private String reconciliationStatus;

        private String accountingStatus;

        private String channelMatchStatus;

        private String channelCode;

        private String channelOrderNo;

        private LocalDateTime transactionDateTime;

        private String transactionTimeZone;
    }

    /**
     * 商户交易动作单列表响应。
     */
    @Data
    public static class TransactionOperationResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        private String operationId;

        private String transactionId;

        private String sourceTransactionId;

        private String merchantId;

        private String merchantOrderNo;

        private String merchantOrderId;

        private Integer operationSequence;

        private String transactionType;

        private String transactionStatus;

        private String processStage;

        private String labelCurrency;

        private BigDecimal labelAmount;

        private String transactionCurrency;

        private BigDecimal transactionAmount;

        private Integer currencyExponent;

        private BigDecimal transactionRate;

        private Integer dccEnabled;

        private Integer edcEnabled;

        private String merchantResponseCode;

        private String merchantResponseMessage;

        private String merchantNotificationStatus;

        private BigDecimal authorizedAmount;

        private BigDecimal capturedAmount;

        private BigDecimal refundedAmount;

        private BigDecimal availableCaptureAmount;

        private BigDecimal availableRefundAmount;

        private String paymentMethod;

        private String paymentBrand;

        private String cardBin;

        private String cardNumberMasked;

        private String accessType;

        private String channelCode;

        private String channelOrderNo;

        private String channelTransactionId;

        private String authCode;

        private String acquirerReferenceNo;

        private String settlementStatus;

        private String reconciliationStatus;

        private String accountingStatus;

        private String channelMatchStatus;

        private LocalDateTime transactionDateTime;

        private LocalDateTime operationTime;
    }

    /**
     * 商户交易动作分页和统计响应。
     */
    @Data
    public static class TransactionOperationSearchResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        private PageResult<TransactionOperationResponse> page;

        private TransactionOperationSummaryResponse summary;
    }

    /**
     * 商户交易动作统计响应。
     */
    @Data
    public static class TransactionOperationSummaryResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        private long totalCount;

        private long successCount;

        private long failedCount;

        private List<TransactionAmountSummaryResponse> amountSummaries = Collections.emptyList();

        private List<TransactionAmountSummaryResponse> successAmountSummaries = Collections.emptyList();

        private List<TransactionAmountSummaryResponse> failedAmountSummaries = Collections.emptyList();

        private List<TransactionPaymentMethodSummaryResponse> paymentMethodSummaries = Collections.emptyList();
    }

    /**
     * 商户交易按币种聚合金额。
     */
    @Data
    public static class TransactionAmountSummaryResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        private String currency;

        private BigDecimal amount;

        private Integer currencyExponent;
    }

    /**
     * 商户交易按支付方式聚合统计。
     */
    @Data
    public static class TransactionPaymentMethodSummaryResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        private String paymentMethod;

        private String paymentBrand;

        private long count;

        private List<TransactionAmountSummaryResponse> amountSummaries = Collections.emptyList();
    }

    /**
     * 商户交易聚合详情响应。
     */
    @Data
    public static class TransactionDetailResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        private TransactionOrderResponse order;

        private List<TransactionOperationResponse> operations = Collections.emptyList();

        private List<Map<String, Object>> statusHistory = Collections.emptyList();

        private List<Map<String, Object>> flowEvents = Collections.emptyList();

        private List<Map<String, Object>> amountChanges = Collections.emptyList();

        private List<Map<String, Object>> channelRequests = Collections.emptyList();

        private List<Map<String, Object>> channelInteractionLogs = Collections.emptyList();

        private List<Map<String, Object>> merchantNotifications = Collections.emptyList();

        private List<Map<String, Object>> merchantNotificationLogs = Collections.emptyList();

        private List<Map<String, Object>> merchantApiInteractionLogs = Collections.emptyList();
    }
}
