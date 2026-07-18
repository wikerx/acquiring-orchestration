package com.scott.payment.admin.dto.transaction;

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
 * @classname : AdminTransactionDTOs
 * @date : 2026-07-14 23:56
 * @email : scott_x@163.com
 * @description : 管理后台交易查询 DTO 集合，位于 service-admin 接口传输层，承接交易主单、动作单、渠道日志、渠道回调和商户通知查询数据。
 * @status : create
 */
public final class AdminTransactionDTOs {

    private AdminTransactionDTOs() {
    }

    /**
     * 交易主单和动作单通用分页查询请求。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TransactionPageQuery extends PageRequest {

        private static final long serialVersionUID = 1L;

        /**
         * 平台商户号，可为空。
         */
        private String merchantId;

        /**
         * 商户订单号，可为空。
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
         * 交易类型，对齐字典 transaction_type。
         */
        private String transactionType;

        /**
         * 交易状态，对齐字典 transaction_status。
         */
        private String transactionStatus;

        /**
         * 渠道编码，可为空。
         */
        private String channelCode;

        /**
         * 支付方式，可为空。
         */
        private String paymentMethod;

        /**
         * 卡品牌或钱包品牌，可为空。
         */
        private String paymentBrand;

        /**
         * 卡 BIN 前缀，可为空。
         */
        private String cardBin;

        /**
         * 渠道订单号，可为空。
         */
        private String channelOrderNo;

        /**
         * 商户侧可见响应码，可为空；支付核心会映射为交易状态过滤。
         */
        private String merchantResponseCode;

        /**
         * 渠道响应码，可为空。
         */
        private String channelResponseCode;

        /**
         * 授权码，可为空。
         */
        private String authCode;

        /**
         * ARN / 收单机构参考号，可为空。
         */
        private String acquirerReferenceNo;

        /**
         * 渠道结果勾兑状态，可为空。
         */
        private String channelMatchStatus;

        /**
         * 对账状态，可为空。
         */
        private String reconciliationStatus;

        /**
         * 结算状态，可为空。
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
         * 查询时区。后台按该时区解释 beginTime/endTime 后换算交易分表查询范围。
         */
        private String queryTimeZone;
    }

    /**
     * 渠道交互日志分页查询请求。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChannelLogQuery extends PageRequest {

        private static final long serialVersionUID = 1L;

        private String channelCode;

        private String transactionId;

        private String channelOrderNo;

        private String requestStatus;

        private String interactionType;

        private LocalDateTime beginTime;

        private LocalDateTime endTime;

        /**
         * 查询时区。后台按该时区解释 beginTime/endTime 后换算交易分表查询范围。
         */
        private String queryTimeZone;
    }

    /**
     * 渠道回调业务记录分页查询请求。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChannelCallbackQuery extends PageRequest {

        private static final long serialVersionUID = 1L;

        private String channelCode;

        private String transactionId;

        private String channelOrderNo;

        private String channelTransactionId;

        private String callbackStatus;

        private LocalDateTime beginTime;

        private LocalDateTime endTime;

        /**
         * 查询时区。后台按该时区解释 beginTime/endTime 后换算交易分表查询范围。
         */
        private String queryTimeZone;
    }

    /**
     * 商户通知任务分页查询请求。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MerchantNotificationQuery extends PageRequest {

        private static final long serialVersionUID = 1L;

        private String merchantId;

        private String transactionId;

        private String notifyStatus;

        private LocalDateTime beginTime;

        private LocalDateTime endTime;

        /**
         * 查询时区。后台按该时区解释 beginTime/endTime 后换算交易分表查询范围。
         */
        private String queryTimeZone;
    }

    /**
     * 管理后台交易动作请求。
     */
    @Data
    public static class TransactionActionRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 管理端本次动作请求唯一标识；为空时由后台生成，作为支付核心幂等键组成部分。
         */
        private String merchantOrderId;

        /**
         * 动作金额，退款必填，撤销可为空并由支付核心按原交易金额处理。
         */
        private BigDecimal amount;

        /**
         * 动作币种，退款为空时按原交易币种处理。
         */
        private String currency;

        /**
         * 后台操作原因，写入交易描述，便于后续审计和排查。
         */
        private String reason;
    }

    /**
     * 管理后台交易动作响应。
     */
    @Data
    public static class TransactionActionResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 本次动作生成的平台交易 ID。
         */
        private String transactionId;

        /**
         * 原平台交易 ID。
         */
        private String sourceTransactionId;

        /**
         * 商户订单号。
         */
        private String merchantOrderNo;

        /**
         * 管理端动作幂等请求号。
         */
        private String merchantOrderId;

        /**
         * 交易类型。
         */
        private String transactionType;

        /**
         * 交易状态。
         */
        private String status;

        /**
         * 处理阶段。
         */
        private String processStage;

        /**
         * 失败原因码。
         */
        private String failReasonCode;

        /**
         * 挂起原因码。
         */
        private String pendingReasonCode;

        /**
         * 最小币种单位金额。
         */
        private Long amount;

        /**
         * ISO 4217 币种代码。
         */
        private String currency;
    }

    /**
     * 交易生命周期主单列表响应。
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

        private String transactionType;

        private String transactionStatus;

        /**
         * 当前生命周期展示状态，按金额汇总和最新动作推导。
         */
        private String lifecycleStatus;

        /**
         * 当前生命周期展示说明，供后台订单跟踪查询展示完整流程结果。
         */
        private String lifecycleStatusMessage;

        private String processStage;

        private String labelCurrency;

        private BigDecimal labelAmount;

        private String transactionCurrency;

        private BigDecimal transactionAmount;

        /**
         * 当前生命周期展示金额。授权类取累计授权金额，支付类取支付金额，
         * 管理端主单列表优先展示该字段。
         */
        private BigDecimal currentAmount;

        /**
         * 当前生命周期展示金额币种。
         */
        private String currentCurrency;

        /**
         * 交易币种默认小数位，用于后台金额按辅币位展示。
         */
        private Integer currencyExponent;

        /**
         * 标签金额转交易金额使用的汇率，未换汇时返回 1.00000000。
         */
        private BigDecimal transactionRate;

        /**
         * 是否启用 DCC，0 否、1 是。
         */
        private Integer dccEnabled;

        /**
         * 是否启用 EDC，0 否、1 是。
         */
        private Integer edcEnabled;

        /**
         * 商户侧可见响应码，列表用于核验平台实际返回给商户的结果。
         */
        private String merchantResponseCode;

        /**
         * 商户侧可见响应描述，后台悬浮展示，避免列表直接暴露过细渠道失败原因。
         */
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
     * 交易动作单列表响应。
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

        /**
         * 交易币种默认小数位，用于后台金额按辅币位展示。
         */
        private Integer currencyExponent;

        /**
         * 标签金额转交易金额使用的汇率，未换汇时返回 1.00000000。
         */
        private BigDecimal transactionRate;

        /**
         * 是否启用 DCC，0 否、1 是。
         */
        private Integer dccEnabled;

        /**
         * 是否启用 EDC，0 否、1 是。
         */
        private Integer edcEnabled;

        /**
         * 商户侧可见响应码，列表用于核验平台实际返回给商户的结果。
         */
        private String merchantResponseCode;

        /**
         * 商户侧可见响应描述，后台悬浮展示，避免列表直接暴露过细渠道失败原因。
         */
        private String merchantResponseMessage;

        /**
         * 给商户异步通知任务状态；未配置回调时为空。
         */
        private String merchantNotificationStatus;

        /**
         * 所属生命周期累计授权成功金额，交易币种单位。
         */
        private BigDecimal authorizedAmount;

        /**
         * 所属生命周期累计请款成功金额，交易币种单位。
         */
        private BigDecimal capturedAmount;

        /**
         * 所属生命周期累计退款成功金额，交易币种单位。
         */
        private BigDecimal refundedAmount;

        /**
         * 所属生命周期当前可请款金额，交易币种单位。
         */
        private BigDecimal availableCaptureAmount;

        /**
         * 所属生命周期当前可退款金额，交易币种单位。
         */
        private BigDecimal availableRefundAmount;

        private String paymentMethod;

        private String paymentBrand;

        private String cardBin;

        /**
         * 页面展示使用的脱敏卡号，格式优先为前六 + **** + 后四。
         */
        private String cardNumberMasked;

        private String accessType;

        private String channelCode;

        private String channelOrderNo;

        private String channelTransactionId;

        private String channelResponseCode;

        private String channelResponseMessage;

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
     * 管理后台交易动作分页及统计响应。
     */
    @Data
    public static class TransactionOperationSearchResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 当前页交易动作数据；统计信息按完整查询条件计算。
         */
        private PageResult<TransactionOperationResponse> page;

        /**
         * 当前查询条件命中的全部交易动作统计。
         */
        private TransactionOperationSummaryResponse summary;
    }

    /**
     * 管理后台交易动作查询统计响应。
     */
    @Data
    public static class TransactionOperationSummaryResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 当前查询条件命中的交易动作总笔数。
         */
        private long totalCount;

        /**
         * 当前查询条件命中的成功交易动作笔数。
         */
        private long successCount;

        /**
         * 当前查询条件命中的失败交易动作笔数。
         */
        private long failedCount;

        /**
         * 全部命中交易按交易币种汇总的金额。
         */
        private List<TransactionAmountSummaryResponse> amountSummaries = Collections.emptyList();

        /**
         * 成功交易按交易币种汇总的金额。
         */
        private List<TransactionAmountSummaryResponse> successAmountSummaries = Collections.emptyList();

        /**
         * 失败交易按交易币种汇总的金额。
         */
        private List<TransactionAmountSummaryResponse> failedAmountSummaries = Collections.emptyList();

        /**
         * 按支付方式和卡品牌汇总的交易笔数与金额。
         */
        private List<TransactionPaymentMethodSummaryResponse> paymentMethodSummaries = Collections.emptyList();
    }

    /**
     * 管理后台按币种聚合的交易金额。
     */
    @Data
    public static class TransactionAmountSummaryResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * ISO 4217 交易币种代码。
         */
        private String currency;

        /**
         * 当前币种交易金额汇总，主币种单位。
         */
        private BigDecimal amount;

        /**
         * 当前币种默认小数位，用于管理端金额展示。
         */
        private Integer currencyExponent;
    }

    /**
     * 管理后台按支付方式和卡品牌聚合的交易统计。
     */
    @Data
    public static class TransactionPaymentMethodSummaryResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 支付方式，例如 BANK_CARD。
         */
        private String paymentMethod;

        /**
         * 支付品牌或卡品牌，例如 MASTERCARD。
         */
        private String paymentBrand;

        /**
         * 当前支付方式/卡品牌命中的交易动作笔数。
         */
        private long count;

        /**
         * 当前支付方式/卡品牌下按交易币种汇总的金额。
         */
        private List<TransactionAmountSummaryResponse> amountSummaries = Collections.emptyList();
    }

    /**
     * 交易聚合详情响应。
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

        private List<Map<String, Object>> channelCallbacks = Collections.emptyList();

        private List<Map<String, Object>> channelCallbackLogs = Collections.emptyList();

        private List<Map<String, Object>> merchantNotifications = Collections.emptyList();

        private List<Map<String, Object>> merchantNotificationLogs = Collections.emptyList();

        private List<Map<String, Object>> merchantApiInteractionLogs = Collections.emptyList();
    }
}
