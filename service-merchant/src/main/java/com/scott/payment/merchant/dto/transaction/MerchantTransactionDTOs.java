package com.scott.payment.merchant.dto.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.dto.transaction.detail.TransactionDetailResponses.MerchantApiInteractionLogResponse;
import com.scott.payment.merchant.dto.transaction.detail.TransactionDetailResponses.MerchantNotificationDeliveryLogResponse;
import com.scott.payment.merchant.dto.transaction.detail.TransactionDetailResponses.MerchantNotificationResponse;
import com.scott.payment.merchant.dto.transaction.detail.TransactionDetailResponses.TransactionAmountChangeResponse;
import com.scott.payment.merchant.dto.transaction.detail.TransactionDetailResponses.TransactionFlowEventResponse;
import com.scott.payment.merchant.dto.transaction.detail.TransactionDetailResponses.TransactionStatusHistoryResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 商户后台交易查询 DTO 集合，位于 service-merchant 接口传输层，仅承载当前登录商户可见的交易查询、详情、统计和后续动作数据。
 */
public final class MerchantTransactionDTOs {

    /**
     * 禁止实例化商户交易 DTO 聚合类。
     */
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
         * 商户侧可见响应码。
         */
        private String merchantResponseCode;

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

        /**
         * 退款原因稳定编码；新版控制台提交，旧版文本请求可为空。
         */
        private String reasonCode;

        /**
         * 补充退款说明，最多 200 字；OTHER 必填，其他原因选填。
         */
        private String refundDescription;

        /**
         * 被操作交易的真实分片时间，必须来自当前商户交易列表结果。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime transactionDateTime;

        /**
         * 被操作交易所属生命周期根主单的真实分片时间。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime rootTransactionDateTime;
    }

    /**
     * 商户交易动作响应。
     */
    @Data
    public static class TransactionActionResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String transactionId;

        /**
         * 原平台交易号，用于将请款、退款、撤销、增量授权等后续动作关联到原始交易。
         */
        private String sourceTransactionId;

        /**
         * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
         */
        private String merchantOrderNo;

        /**
         * 商户请求订单标识，用于区分同一商户订单下的一次接口提交或后续交易动作。
         */
        private String merchantOrderId;

        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         */
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

        /**
         * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
         */
        private String operationId;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String rootTransactionId;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String latestTransactionId;

        private String merchantId;

        /**
         * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
         */
        private String merchantOrderNo;

        /**
         * 商户请求订单标识，用于区分同一商户订单下的一次接口提交或后续交易动作。
         */
        private String merchantOrderId;

        private String paymentMethod;

        /**
         * 支付品牌编码，用于区分银行卡、钱包或本地支付品牌。
         */
        private String paymentBrand;

        /**
         * 卡 BIN，用于识别发卡行、卡组织、国家地区和风控规则。
         */
        private String cardBin;

        /**
         * 卡编号脱敏，表示银行卡号或脱敏卡号字段。
         */
        private String cardNumberMasked;

        private String authCode;

        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         */
        private String transactionType;

        private String transactionStatus;

        private String lifecycleStatus;

        private String lifecycleStatusMessage;

        private String processStage;

        private String labelCurrency;

        private BigDecimal labelAmount;

        /**
         * 平台上送渠道的交易币种，仅供商户后台动作编排使用，禁止序列化到商户端。
         */
        @JsonIgnore
        private String transactionCurrency;

        /**
         * 平台上送渠道的交易金额，仅供商户后台动作编排使用，禁止序列化到商户端。
         */
        @JsonIgnore
        private BigDecimal transactionAmount;

        private BigDecimal currentAmount;

        private String currentCurrency;

        /**
         * 交易币种的小数位数，用于主币种单位与最小货币单位之间的精确转换。
         */
        private Integer currencyExponent;

        /**
         * 标签金额换算为渠道交易金额的内部汇率，禁止序列化到商户端。
         */
        @JsonIgnore
        private BigDecimal transactionRate;

        /**
         * 是否按 3DS 交易处理：0 否，1 是。
         */
        private Integer threeDsEnabled;

        /**
         * 是否实际启用 DCC：0 未启用，1 启用。
         */
        @JsonIgnore
        private Integer dccEnabled;

        /**
         * 是否实际启用 EDC：0 未启用，1 启用。
         */
        @JsonIgnore
        private Integer edcEnabled;

        private String merchantResponseCode;

        private String merchantResponseMessage;

        /**
         * 所属生命周期累计授权成功金额，商户标签币种单位。
         */
        private BigDecimal authorizedAmount;

        /**
         * 所属生命周期累计请款成功金额，商户标签币种单位。
         */
        private BigDecimal capturedAmount;

        /**
         * 所属生命周期累计退款成功金额，商户标签币种单位。
         */
        private BigDecimal refundedAmount;

        /**
         * 所属生命周期当前可请款金额，商户标签币种单位。
         */
        private BigDecimal availableCaptureAmount;

        /**
         * 所属生命周期当前可退款金额，商户标签币种单位。
         */
        private BigDecimal availableRefundAmount;

        /**
         * 最近真实动作的最终结算币种；未结算时为空。
         */
        private String settlementCurrency;

        /**
         * 最近真实动作的最终有符号结算金额，单位由 settlementCurrency 决定。
         */
        private BigDecimal settlementAmount;

        /**
         * 最近真实动作中 1 单位交易币种兑换的结算币种数量，最多 12 位小数。
         */
        @JsonIgnore
        private BigDecimal settlementRate;

        /**
         * 最近真实动作的结算业务日期；未结算时为空。
         */
        private LocalDate settlementDate;

        /**
         * 最近一次结算或冲正批次号。
         */
        private String settlementBatchNo;

        /**
         * 当前主单结算快照对应的真实动作交易号。
         */
        private String settlementTransactionId;

        /**
         * 当前主单结算快照对应的真实动作分片时间。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime settlementTransactionDateTime;

        private String settlementStatus;

        private String accountingStatus;

        /**
         * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime transactionDateTime;

        /**
         * 生命周期根主单的真实分片时间，动作详情必须与 transactionDateTime 一并传入。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime rootTransactionDateTime;

        /**
         * 交易业务时区，使用 IANA 时区标识解释本地交易时间。
         */
        private String transactionTimeZone;
    }

    /**
     * 商户交易动作单列表响应。
     */
    @Data
    public static class TransactionOperationResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
         */
        private String operationId;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String transactionId;

        /**
         * 原平台交易号，用于将请款、退款、撤销、增量授权等后续动作关联到原始交易。
         */
        private String sourceTransactionId;

        private String merchantId;

        /**
         * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
         */
        private String merchantOrderNo;

        /**
         * 商户请求订单标识，用于区分同一商户订单下的一次接口提交或后续交易动作。
         */
        private String merchantOrderId;

        private Integer operationSequence;

        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         */
        private String transactionType;

        private String transactionStatus;

        private String processStage;

        private String labelCurrency;

        private BigDecimal labelAmount;

        /**
         * 平台上送渠道的交易币种，仅供商户后台动作编排使用，禁止序列化到商户端。
         */
        @JsonIgnore
        private String transactionCurrency;

        /**
         * 平台上送渠道的交易金额，仅供商户后台动作编排使用，禁止序列化到商户端。
         */
        @JsonIgnore
        private BigDecimal transactionAmount;

        /**
         * 交易币种的小数位数，用于主币种单位与最小货币单位之间的精确转换。
         */
        private Integer currencyExponent;

        /**
         * 标签金额换算为渠道交易金额的内部汇率，禁止序列化到商户端。
         */
        @JsonIgnore
        private BigDecimal transactionRate;

        /**
         * 是否按 3DS 交易处理：0 否，1 是。
         */
        private Integer threeDsEnabled;

        /**
         * 是否实际启用 DCC：0 未启用，1 启用。
         */
        @JsonIgnore
        private Integer dccEnabled;

        /**
         * 是否实际启用 EDC：0 未启用，1 启用。
         */
        @JsonIgnore
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

        /**
         * 支付品牌编码，用于区分银行卡、钱包或本地支付品牌。
         */
        private String paymentBrand;

        /**
         * 卡 BIN，用于识别发卡行、卡组织、国家地区和风控规则。
         */
        private String cardBin;

        /**
         * 卡编号脱敏，表示银行卡号或脱敏卡号字段。
         */
        private String cardNumberMasked;

        /**
         * 访问类型，用于区分登录、查询、导出或配置变更等审计场景。
         */
        private String accessType;

        private String authCode;

        /**
         * 当前动作最终结算币种；未结算时为空。
         */
        private String settlementCurrency;

        /**
         * 当前动作最终有符号结算金额，单位由 settlementCurrency 决定。
         */
        private BigDecimal settlementAmount;

        /**
         * 当前动作中 1 单位交易币种兑换的结算币种数量，最多 12 位小数。
         */
        @JsonIgnore
        private BigDecimal settlementRate;

        /**
         * 当前动作最终结算业务日期；未结算时为空。
         */
        private LocalDate settlementDate;

        /**
         * 当前动作最近一次结算或冲正批次号。
         */
        private String settlementBatchNo;

        private String settlementStatus;

        private String accountingStatus;

        /**
         * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime transactionDateTime;

        /**
         * 生命周期根主单的真实分片时间。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime rootTransactionDateTime;

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

        /**
         * 按币种拆分的金额汇总集合，禁止直接跨币种相加。
         */
        private List<TransactionAmountSummaryResponse> amountSummaries = Collections.emptyList();

        /**
         * 按币种拆分的成功金额汇总集合，禁止直接跨币种相加。
         */
        private List<TransactionAmountSummaryResponse> successAmountSummaries = Collections.emptyList();

        /**
         * 按币种拆分的失败金额汇总集合，禁止直接跨币种相加。
         */
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

        /**
         * 交易币种的小数位数，用于主币种单位与最小货币单位之间的精确转换。
         */
        private Integer currencyExponent;
    }

    /**
     * 商户交易按支付方式聚合统计。
     */
    @Data
    public static class TransactionPaymentMethodSummaryResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        private String paymentMethod;

        /**
         * 支付品牌编码，用于区分银行卡、钱包或本地支付品牌。
         */
        private String paymentBrand;


        private long count;

        /**
         * 按币种拆分的金额汇总集合，禁止直接跨币种相加。
         */
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

        /**
         * 交易状态流转历史。
         */
        private List<TransactionStatusHistoryResponse> statusHistory = Collections.emptyList();

        /**
         * 交易流程事件。
         */
        private List<TransactionFlowEventResponse> flowEvents = Collections.emptyList();

        /**
         * 生命周期金额变动记录，金额必须结合各记录币种解释。
         */
        private List<TransactionAmountChangeResponse> amountChanges = Collections.emptyList();

        /**
         * 商户通知任务。
         */
        private List<MerchantNotificationResponse> merchantNotifications = Collections.emptyList();

        /**
         * 商户通知单次投递记录。
         */
        private List<MerchantNotificationDeliveryLogResponse> merchantNotificationLogs = Collections.emptyList();

        /**
         * 不含请求和响应报文的商户 OpenAPI 交互摘要。
         */
        private List<MerchantApiInteractionLogResponse> merchantApiInteractionLogs = Collections.emptyList();
    }
}
