package com.scott.payment.payment.service.dto.transaction;

import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionQueryDTOs
 * @date : 2026-07-14 23:06
 * @email : scott_x@163.com
 * @description : 交易管理查询 DTO 集合，位于 service-payment 服务 DTO 层，承载交易主单、动作单、渠道日志、回调和商户通知的后台聚合查询模型。
 * @status : create
 */
public final class TransactionQueryDTOs {

    private TransactionQueryDTOs() {
    }

    /**
     * 交易查询基础分页请求。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TransactionPageQuery extends PageRequest {

        private static final long serialVersionUID = 1L;

        /**
         * 平台商户号。
         */
        private String merchantId;

        /**
         * 商户订单号。
         */
        private String merchantOrderNo;

        /**
         * 平台当前交易 ID。
         */
        private String transactionId;

        /**
         * 原平台交易 ID。
         */
        private String sourceTransactionId;

        /**
         * 交易类型。
         */
        private String transactionType;

        /**
         * 交易状态。
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
         * 卡品牌或支付品牌，可为空。
         */
        private String paymentBrand;

        /**
         * 卡 BIN，可为空。
         */
        private String cardBin;

        /**
         * 渠道订单号，可为空。
         */
        private String channelOrderNo;

        /**
         * 商户侧可见响应码，可为空；查询时会映射为平台交易状态过滤。
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
     * 渠道日志查询请求。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChannelLogQuery extends PageRequest {

        private static final long serialVersionUID = 1L;

        /**
         * 渠道编码，可为空。
         */
        private String channelCode;

        /**
         * 平台交易 ID，可为空。
         */
        private String transactionId;

        /**
         * 渠道订单号，可为空。
         */
        private String channelOrderNo;

        /**
         * 渠道请求状态，可为空，预留给请求表查询。
         */
        private String requestStatus;

        /**
         * 交互类型，如 REQUEST、RESPONSE、CALLBACK。
         */
        private String interactionType;

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
     * 渠道回调记录查询请求。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChannelCallbackQuery extends PageRequest {

        private static final long serialVersionUID = 1L;

        /**
         * 渠道编码，可为空。
         */
        private String channelCode;

        /**
         * 平台交易 ID，可为空。
         */
        private String transactionId;

        /**
         * 渠道订单号，可为空。
         */
        private String channelOrderNo;

        /**
         * 渠道交易 ID，可为空。
         */
        private String channelTransactionId;

        /**
         * 回调处理状态，可为空。
         */
        private String callbackStatus;

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
     * 商户通知查询请求。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MerchantNotificationQuery extends PageRequest {

        private static final long serialVersionUID = 1L;

        /**
         * 平台商户号，可为空。
         */
        private String merchantId;

        /**
         * 平台交易 ID，可为空。
         */
        private String transactionId;

        /**
         * 商户通知状态，可为空。
         */
        private String notifyStatus;

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
     * 交易主单列表响应。
     */
    @Data
    public static class TransactionOrderResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台内部生命周期关联标识，后台用于聚合原始交易及后续动作，不返回商户。
         */
        private String operationId;

        /**
         * 原始授权或支付的平台交易 ID，作为订单跟踪查询主标识。
         */
        private String rootTransactionId;

        /**
         * 当前生命周期最新一笔动作交易 ID。
         */
        private String latestTransactionId;

        /**
         * 平台商户号。
         */
        private String merchantId;

        /**
         * 商户订单号，对应商户请求 orderInfo.orderNo。
         */
        private String merchantOrderNo;

        /**
         * 商户本次请求唯一标识，对应商户请求 orderInfo.orderId。
         */
        private String merchantOrderId;

        /**
         * 支付方式，例如 BANK_CARD。
         */
        private String paymentMethod;

        /**
         * 支付品牌或卡品牌，例如 MASTERCARD。
         */
        private String paymentBrand;

        /**
         * 当前生命周期原始交易类型。
         */
        private String transactionType;

        /**
         * 当前生命周期主状态。
         */
        private String transactionStatus;

        /**
         * 当前生命周期展示状态，按金额汇总和最新动作推导，例如 VOIDED、CAPTURED、PARTIALLY_REFUNDED。
         */
        private String lifecycleStatus;

        /**
         * 当前生命周期展示说明，供后台订单跟踪查询直接展示完整流程结果。
         */
        private String lifecycleStatusMessage;

        /**
         * 当前处理阶段，区分 API、风控、路由、渠道、终态入库等阶段。
         */
        private String processStage;

        /**
         * 商户上送的标签币种。
         */
        private String labelCurrency;

        /**
         * 商户上送的标签金额，主币种单位。
         */
        private BigDecimal labelAmount;

        /**
         * 系统实际上送渠道的交易币种。
         */
        private String transactionCurrency;

        /**
         * 系统实际上送渠道的交易金额，主币种单位。
         */
        private BigDecimal transactionAmount;

        /**
         * 当前生命周期展示金额。授权类取累计授权金额，支付类取支付金额，
         * 用于管理端主单列表避免把增量授权误读为独立交易金额。
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
         * 商户侧可见响应码，后台列表用于核验实际返回给商户的结果层级。
         */
        private String merchantResponseCode;

        /**
         * 商户侧可见响应描述，避免在列表直接暴露过细渠道拒绝原因。
         */
        private String merchantResponseMessage;


        /**
         * 当前生命周期累计授权成功金额，交易币种单位。
         */
        private BigDecimal authorizedAmount;

        /**
         * 当前生命周期累计请款成功金额，交易币种单位。
         */
        private BigDecimal capturedAmount;

        /**
         * 当前生命周期累计退款成功金额，交易币种单位。
         */
        private BigDecimal refundedAmount;

        /**
         * 当前生命周期可请款金额，交易币种单位。
         */
        private BigDecimal availableCaptureAmount;

        /**
         * 当前生命周期可退款金额，交易币种单位。
         */
        private BigDecimal availableRefundAmount;

        /**
         * 结算状态。
         */
        private String settlementStatus;

        /**
         * 对账状态。
         */
        private String reconciliationStatus;

        /**
         * 账务状态。
         */
        private String accountingStatus;

        /**
         * 渠道结果勾兑状态。
         */
        private String channelMatchStatus;

        /**
         * 渠道编码。
         */
        private String channelCode;

        /**
         * 渠道订单号；MPGS 对应 orderId。
         */
        private String channelOrderNo;

        /**
         * 主单交易业务时间。
         */
        private LocalDateTime transactionDateTime;

        /**
         * 主单交易业务时区。
         */
        private String transactionTimeZone;
    }

    /**
     * 交易动作列表响应。
     */
    @Data
    public static class TransactionOperationResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台内部生命周期关联标识，后台用于把同一原交易的动作串起来。
         */
        private String operationId;

        /**
         * 平台当前动作交易 ID。
         */
        private String transactionId;

        /**
         * 原平台交易 ID，后续请款、退款、撤销时用于定位原始授权或支付。
         */
        private String sourceTransactionId;

        /**
         * 平台商户号。
         */
        private String merchantId;

        /**
         * 商户订单号，对应 orderInfo.orderNo。
         */
        private String merchantOrderNo;

        /**
         * 商户本次请求唯一标识，对应 orderInfo.orderId。
         */
        private String merchantOrderId;

        /**
         * 生命周期内动作序号，用于详情页按交易流程排序。
         */
        private Integer operationSequence;

        /**
         * 当前动作交易类型。
         */
        private String transactionType;

        /**
         * 当前动作交易状态。
         */
        private String transactionStatus;

        /**
         * 当前动作处理阶段。
         */
        private String processStage;

        /**
         * 商户上送的标签币种。
         */
        private String labelCurrency;

        /**
         * 商户上送的标签金额，主币种单位。
         */
        private BigDecimal labelAmount;

        /**
         * 系统实际上送渠道的交易币种。
         */
        private String transactionCurrency;

        /**
         * 系统实际上送渠道的交易金额，主币种单位。
         */
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
         * 商户侧可见响应码，后台列表用于核验实际返回给商户的结果层级。
         */
        private String merchantResponseCode;

        /**
         * 商户侧可见响应描述，避免在列表直接暴露过细渠道拒绝原因。
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

        /**
         * 支付方式，例如 BANK_CARD。
         */
        private String paymentMethod;

        /**
         * 支付品牌或卡品牌，例如 MASTERCARD。
         */
        private String paymentBrand;

        /**
         * 卡 BIN 展示值，优先为前六 + **** + 后四。
         */
        private String cardBin;

        /**
         * 页面展示使用的脱敏卡号，格式优先为前六 + **** + 后四。
         */
        private String cardNumberMasked;

        /**
         * 接入类型，例如 Direct API、Hosted Checkout。
         */
        private String accessType;

        /**
         * 渠道编码。
         */
        private String channelCode;

        /**
         * 渠道订单号；MPGS 对应 orderId。
         */
        private String channelOrderNo;

        /**
         * 渠道交易号；MPGS 对应 transactionId，部分渠道可能为空。
         */
        private String channelTransactionId;

        /**
         * 渠道响应码，后台展示真实渠道结果。
         */
        private String channelResponseCode;

        /**
         * 渠道响应描述，后台展示真实渠道结果。
         */
        private String channelResponseMessage;

        /**
         * 授权码，授权或支付成功时可为空。
         */
        private String authCode;

        /**
         * ARN / 收单机构参考号，可为空。
         */
        private String acquirerReferenceNo;

        /**
         * 结算状态。
         */
        private String settlementStatus;

        /**
         * 对账状态。
         */
        private String reconciliationStatus;

        /**
         * 账务状态。
         */
        private String accountingStatus;

        /**
         * 渠道结果勾兑状态。
         */
        private String channelMatchStatus;

        /**
         * 当前动作交易业务时间。
         */
        private LocalDateTime transactionDateTime;

        /**
         * 当前动作发生时间。
         */
        private LocalDateTime operationTime;
    }

    /**
     * 交易动作分页及全量统计响应。
     */
    @Data
    public static class TransactionOperationSearchResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 当前页交易动作数据；统计信息不受该分页范围影响。
         */
        private PageResult<TransactionOperationResponse> page;

        /**
         * 按当前查询条件命中的全部交易动作统计，供管理端列表顶部展示。
         */
        private TransactionOperationSummaryResponse summary;
    }

    /**
     * 交易动作查询统计响应。
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
     * 按币种聚合的交易金额。
     */
    @Data
    public static class TransactionAmountSummaryResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * ISO 4217 交易币种代码；为空数据统一返回 UNKNOWN，避免前端空 key。
         */
        private String currency;

        /**
         * 当前币种交易金额汇总，主币种单位。
         */
        private BigDecimal amount;

        /**
         * 当前币种默认小数位，用于管理端按辅币位展示。
         */
        private Integer currencyExponent;
    }

    /**
     * 按支付方式和卡品牌聚合的交易统计。
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
     * 交易动作统计 Mapper 行模型。
     */
    @Data
    public static class TransactionOperationSummaryRow implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 交易状态，用于区分成功、失败等统计分组。
         */
        private String transactionStatus;

        /**
         * 支付方式，例如 BANK_CARD。
         */
        private String paymentMethod;

        /**
         * 支付品牌或卡品牌，例如 MASTERCARD。
         */
        private String paymentBrand;

        /**
         * 交易币种代码；SQL 已对空值归一为 UNKNOWN。
         */
        private String currency;

        /**
         * 当前分组交易金额汇总，主币种单位。
         */
        private BigDecimal amount;

        /**
         * 当前分组交易币种默认小数位。
         */
        private Integer currencyExponent;

        /**
         * 当前分组命中的交易动作笔数。
         */
        private long count;
    }

    /**
     * 交易详情聚合响应。
     */
    @Data
    public static class TransactionDetailResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 交易生命周期主单摘要。
         */
        private TransactionOrderResponse order;

        /**
         * 同一生命周期下的全部交易动作。
         */
        private List<TransactionOperationResponse> operations = Collections.emptyList();

        /**
         * 状态流转历史。
         */
        private List<?> statusHistory = Collections.emptyList();

        /**
         * 交易流程事件，用于后台时间轴展示。
         */
        private List<?> flowEvents = Collections.emptyList();

        /**
         * 金额变动记录，如增量授权、请款、退款、撤销。
         */
        private List<?> amountChanges = Collections.emptyList();

        /**
         * 渠道请求摘要记录。
         */
        private List<?> channelRequests = Collections.emptyList();

        /**
         * 渠道请求/响应脱敏原文日志。
         */
        private List<?> channelInteractionLogs = Collections.emptyList();

        /**
         * 渠道回调业务记录。
         */
        private List<?> channelCallbacks = Collections.emptyList();

        /**
         * 渠道回调原文和响应日志。
         */
        private List<?> channelCallbackLogs = Collections.emptyList();

        /**
         * 商户通知任务记录。
         */
        private List<?> merchantNotifications = Collections.emptyList();

        /**
         * 商户通知请求和响应日志。
         */
        private List<?> merchantNotificationLogs = Collections.emptyList();

        /**
         * 商户 OpenAPI 请求和平台响应交互日志。
         */
        private List<?> merchantApiInteractionLogs = Collections.emptyList();
    }

    /**
     * 渠道一次请求响应交互日志响应。
     */
    @Data
    public static class ChannelInteractionResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 聚合后的交互日志标识，优先取请求行标识。
         */
        private String interactionLogId;

        /**
         * 请求行日志标识，兼容历史拆分日志。
         */
        private String requestInteractionLogId;

        /**
         * 响应或异常行日志标识，兼容历史拆分日志。
         */
        private String responseInteractionLogId;

        /**
         * 平台渠道请求 ID。
         */
        private String requestId;

        /**
         * 平台当前交易 ID。
         */
        private String transactionId;

        /**
         * 平台内部生命周期关联标识。
         */
        private String operationId;

        /**
         * 交易动作类型，用于后台日志页面区分授权、请款、退款、撤销等渠道交互归属。
         */
        private String transactionType;

        /**
         * 渠道请求发送状态，表示平台是否完成向渠道发起请求，不等同于交易成功。
         */
        private String requestStatus;

        /**
         * 渠道编码。
         */
        private String channelCode;

        /**
         * 渠道订单号；MPGS 对应 orderId。
         */
        private String channelOrderNo;

        /**
         * 渠道交易号；MPGS 对应 transactionId，部分渠道可能为空。
         */
        private String channelTransactionId;

        /**
         * 交互类型，当前优先为 REQUEST_RESPONSE。
         */
        private String interactionType;

        /**
         * 渠道 HTTP 方法。
         */
        private String httpMethod;

        /**
         * 脱敏后的渠道请求 URL。
         */
        private String requestUrlMasked;

        /**
         * 渠道 HTTP 状态码，可为空。
         */
        private Integer httpStatus;

        /**
         * 脱敏后的渠道请求头 JSON。
         */
        private String requestHeaderJsonMasked;

        /**
         * 脱敏后的渠道请求体 JSON。
         */
        private String requestBodyJsonMasked;

        /**
         * 脱敏后的渠道响应头 JSON。
         */
        private String responseHeaderJsonMasked;

        /**
         * 脱敏后的渠道响应体 JSON。
         */
        private String responseBodyJsonMasked;

        /**
         * 渠道调用异常类型。
         */
        private String exceptionType;

        /**
         * 渠道调用异常摘要。
         */
        private String exceptionMessage;

        /**
         * 渠道网关结果，例如 MPGS result，供后台判断渠道交互是否业务失败。
         */
        private String gatewayResult;

        /**
         * 渠道网关响应码，例如 MPGS response.gatewayCode。
         */
        private String gatewayCode;

        /**
         * 收单机构响应码，例如 00 表示收单侧批准。
         */
        private String acquirerCode;

        /**
         * 收单机构响应描述，仅用于后台排查。
         */
        private String acquirerMessage;

        /**
         * 平台映射后的渠道交易状态，例如 SUCCESS、FAILED、PENDING。
         */
        private String channelTradeStatus;

        /**
         * 渠道原始状态，例如 MPGS 的 SUCCESS、ERROR。
         */
        private String rawChannelStatus;

        /**
         * 渠道响应码，失败时用于后台展示真实渠道原因。
         */
        private String channelResponseCode;

        /**
         * 渠道响应描述，失败时用于后台展示真实渠道原因。
         */
        private String channelResponseMessage;

        /**
         * 平台根据渠道交互摘要推导的结果状态，仅用于日志卡片染色和筛查。
         */
        private String platformResultCode;

        /**
         * 平台失败摘要，优先取渠道响应描述或异常信息。
         */
        private String platformFailReason;

        /**
         * 渠道交互耗时，单位毫秒。
         */
        private Integer durationMillis;

        /**
         * 链路追踪 ID，可为空。
         */
        private String traceId;

        /**
         * 请求发送时间。
         */
        private LocalDateTime requestTime;

        /**
         * 响应接收时间。
         */
        private LocalDateTime responseTime;

        /**
         * 日志交互时间，兼容历史单行日志。
         */
        private LocalDateTime interactionTime;

        /**
         * 交易业务时间，用于分表定位。
         */
        private LocalDateTime transactionDateTime;
    }
}
