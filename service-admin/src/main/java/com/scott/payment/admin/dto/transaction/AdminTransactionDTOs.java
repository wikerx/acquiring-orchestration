package com.scott.payment.admin.dto.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 管理后台交易查询 DTO 集合，位于 service-admin 接口传输层，承接交易主单、动作单、渠道日志、渠道回调和商户通知查询数据。
 */
public final class AdminTransactionDTOs {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
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

        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String transactionId;

        /**
         * 渠道订单号，由渠道返回，用于渠道查询、回调匹配和对账。
         */
        private String channelOrderNo;

        private String requestStatus;

        private String interactionType;

        private LocalDateTime beginTime;

        /**
         * 请求中的结束时间，用于限定本次操作的输入和校验范围。
         */
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

        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String transactionId;

        /**
         * 渠道订单号，由渠道返回，用于渠道查询、回调匹配和对账。
         */
        private String channelOrderNo;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String channelTransactionId;

        private String callbackStatus;

        private LocalDateTime beginTime;

        /**
         * 请求中的结束时间，用于限定本次操作的输入和校验范围。
         */
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

        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String transactionId;

        private String notifyStatus;

        private LocalDateTime beginTime;

        /**
         * 请求中的结束时间，用于限定本次操作的输入和校验范围。
         */
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

        /**
         * 退款原因稳定编码；新版控制台提交，旧版文本请求可为空。
         */
        private String reasonCode;

        /**
         * 补充退款说明，最多 200 字；OTHER 必填，其他原因选填。
         */
        private String refundDescription;

        /**
         * 被操作交易的真实分片时间，必须来自交易列表查询结果，禁止根据交易号推断。
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
     * 管理端单笔渠道勾兑请求。
     */
    @Data
    public static class ChannelMatchRequeryRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 被勾兑交易的真实分片时间，必须来自交易列表，禁止根据交易号推断。
         */
        @NotNull
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime transactionDateTime;
    }

    /**
     * 管理端单笔渠道勾兑结果。
     */
    @Data
    public static class ChannelMatchRequeryResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 本次定位并尝试勾兑的交易数量，单笔接口固定为 1。
         */
        private int scannedCount;

        /**
         * 渠道终态与平台状态一致，或非终态交易成功推进的数量。
         */
        private int matchedCount;

        /**
         * 渠道结果仍不明确或 CAS 未命中的数量。
         */
        private int pendingCount;

        /**
         * 查询异常、身份缺失或渠道与平台结果不一致的数量。
         */
        private int failedCount;
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

        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;

        /**
         * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
         */
        private String merchantOrderNo;

        /**
         * 商户请求订单标识，用于区分同一商户订单下的一次接口提交或后续交易动作。
         */
        private String merchantOrderId;

        /**
         * 商户在首次交易请求中上送的网站地址，仅作为来源审计值，不作为可信身份依据。
         */
        private String merchantWebsite;

        private String paymentMethod;

        /**
         * 支付品牌编码，用于区分银行卡、钱包或本地支付品牌。
         */
        private String paymentBrand;

        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         */
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
         * 是否按 3DS 交易处理：0 否，1 是。
         */
        private Integer threeDsEnabled;

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
         * OpenAPI V1.3 统一交易结果码，与商户和付款人展示使用同一标准定义。
         */
        private String openApiResultCode;

        /**
         * 内部诊断文案；仅由管理端受控接口返回，不得下发到商户或收银台。
         */
        private String internalResultMessage;

        /**
         * 付款人安全文案预览，用于核验收银台展示，不包含渠道原文或内部异常。
         */
        private String payerResultMessage;

        private BigDecimal authorizedAmount;

        private BigDecimal capturedAmount;

        private BigDecimal refundedAmount;

        private BigDecimal availableCaptureAmount;

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

        private String reconciliationStatus;

        private String accountingStatus;

        private String channelMatchStatus;

        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;

        /**
         * 渠道订单号，由渠道返回，用于渠道查询、回调匹配和对账。
         */
        private String channelOrderNo;

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
     * 交易动作单列表响应。
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

        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
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
         * 是否按 3DS 交易处理：0 否，1 是。
         */
        private Integer threeDsEnabled;

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
         * OpenAPI V1.3 统一交易结果码，与商户和付款人展示使用同一标准定义。
         */
        private String openApiResultCode;

        /**
         * 内部诊断文案；可包含受控渠道摘要，但不得包含完整渠道报文或异常栈。
         */
        private String internalResultMessage;

        /**
         * 付款人安全文案预览，用于核验收银台展示边界。
         */
        private String payerResultMessage;

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

        /**
         * 支付品牌编码，用于区分银行卡、钱包或本地支付品牌。
         */
        private String paymentBrand;

        /**
         * 卡 BIN，用于识别发卡行、卡组织、国家地区和风控规则。
         */
        private String cardBin;

        /**
         * 页面展示使用的脱敏卡号，格式优先为前六 + **** + 后四。
         */
        private String cardNumberMasked;

        /**
         * 访问类型，用于区分登录、查询、导出或配置变更等审计场景。
         */
        private String accessType;

        /**
         * 网关认证并规范化后的交易接入客户端 IP，仅用于受控安全审计。
         */
        private String ingressClientIp;

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

        private String channelResponseMessage;

        private String authCode;

        private String acquirerReferenceNo;

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

        private String reconciliationStatus;

        private String accountingStatus;

        private String channelMatchStatus;

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
     * 管理后台交易生命周期主单分页及统计响应。
     */
    @Data
    public static class TransactionOrderSearchResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 当前页生命周期主单；统计信息按完整查询条件计算。
         */
        private PageResult<TransactionOrderResponse> page;

        /**
         * 当前查询条件命中的全部生命周期主单统计。
         */
        private TransactionOrderSummaryResponse summary;
    }

    /**
     * 管理后台交易生命周期主单查询统计。
     */
    @Data
    public static class TransactionOrderSummaryResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 当前查询条件命中的生命周期主单总数。
         */
        private long totalCount;

        /**
         * 当前权威状态为成功的生命周期主单数。
         */
        private long successCount;

        /**
         * 当前权威状态为失败的生命周期主单数。
         */
        private long failedCount;

        /**
         * 当前权威状态为待处理或处理中的生命周期主单数。
         */
        private long processingCount;

        /**
         * 累计请款成功金额大于零的生命周期主单数。
         */
        private long capturedCount;

        /**
         * 全部命中生命周期主单按交易币种汇总的交易金额。
         */
        private List<TransactionAmountSummaryResponse> amountSummaries = Collections.emptyList();

        /**
         * 成功生命周期主单按交易币种汇总的交易金额。
         */
        private List<TransactionAmountSummaryResponse> successAmountSummaries = Collections.emptyList();

        /**
         * 累计请款金额大于零的生命周期主单按交易币种汇总的实际请款金额。
         */
        private List<TransactionAmountSummaryResponse> capturedAmountSummaries = Collections.emptyList();

        /**
         * 失败生命周期主单按交易币种汇总的交易金额。
         */
        private List<TransactionAmountSummaryResponse> failedAmountSummaries = Collections.emptyList();
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
     * 交易账单或收货联系人信息，仅包含管理端允许展示的商户业务字段。
     */
    @Data
    public static class TransactionContactInfoResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 名。
         */
        private String firstName;

        /**
         * 姓。
         */
        private String lastName;

        /**
         * 联系电话明文。
         */
        private String phone;

        /**
         * 联系邮箱明文。
         */
        private String email;

        /**
         * ISO 3166-1 alpha-3 国家或地区代码。
         */
        private String country;

        /**
         * 州、省或地区。
         */
        private String state;

        /**
         * 城市。
         */
        private String city;

        /**
         * 街道地址。
         */
        private String street;

        /**
         * 邮政编码。
         */
        private String postal;
    }

    /**
     * 付款人信息，包含风控排查所需的付款人、网络和浏览器上下文。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TransactionPayerInfoResponse extends TransactionContactInfoResponse {

        private static final long serialVersionUID = 1L;

        /**
         * 商户侧付款人或客户标识。
         */
        private String payerId;

        /**
         * 付款人客户端 IP 明文。
         */
        private String ipAddress;

        /**
         * 商户付款会话标识。
         */
        private String sessionId;

        /**
         * 商户上送的浏览器结构化信息。
         */
        private Map<String, Object> browserInfo;

        /**
         * 付款人客户端 User-Agent。
         */
        private String userAgent;
    }

    /**
     * 管理后台交易状态流转记录。
     *
     * <p>该模型固定状态历史表对管理端暴露的字段，避免数据库新增内部列时自动扩展接口响应。</p>
     */
    @Data
    public static class TransactionStatusHistoryResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 物理记录主键；使用字符串返回，避免浏览器解析 BIGINT 时丢失精度。
         */
        private String id;

        /**
         * 状态历史业务号。
         */
        private String statusHistoryId;

        /**
         * 平台交易号。
         */
        private String transactionId;

        /**
         * 交易动作号。
         */
        private String operationId;

        /**
         * 状态所属对象，例如 ORDER 或 OPERATION。
         */
        private String statusObject;

        /**
         * 流转前状态。
         */
        private String fromStatus;

        /**
         * 流转后状态。
         */
        private String toStatus;

        /**
         * 状态流转触发类型。
         */
        private String triggerType;

        /**
         * 触发对象业务号。
         */
        private String triggerId;

        /**
         * 状态流转执行结果。
         */
        private String transitionResult;

        /**
         * 状态流转失败或忽略原因。
         */
        private String failReason;

        /**
         * 流转前乐观锁版本。
         */
        private Integer versionBefore;

        /**
         * 流转后乐观锁版本。
         */
        private Integer versionAfter;

        /**
         * 状态流转时间。
         */
        private LocalDateTime statusTime;

        /**
         * 原交易分片时间。
         */
        private LocalDateTime transactionDateTime;

        /**
         * 原交易 UTC 时间。
         */
        private LocalDateTime transactionUtcTime;

        /**
         * 原交易 IANA 时区。
         */
        private String transactionTimeZone;

        /**
         * 记录创建时间。
         */
        private LocalDateTime createTime;
    }

    /**
     * 管理后台交易流程事件。
     *
     * <p>事件状态、前后交易状态和关联对象分别保存，管理端不得从事件文案反推交易终态。</p>
     */
    @Data
    public static class TransactionFlowEventResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 物理记录主键；使用字符串返回，避免浏览器解析 BIGINT 时丢失精度。
         */
        private String id;

        /**
         * 流程事件业务号。
         */
        private String flowEventId;

        /**
         * 平台交易号。
         */
        private String transactionId;

        /**
         * 交易动作号。
         */
        private String operationId;

        /**
         * 流程事件类型。
         */
        private String eventType;

        /**
         * 流程事件阶段。
         */
        private String eventStage;

        /**
         * 流程事件状态。
         */
        private String eventStatus;

        /**
         * 流程事件展示名称。
         */
        private String eventName;

        /**
         * 流程事件摘要。
         */
        private String eventContent;

        /**
         * 事件发生前交易状态。
         */
        private String previousStatus;

        /**
         * 事件发生后交易状态。
         */
        private String currentStatus;

        /**
         * 操作者类型。
         */
        private String operatorType;

        /**
         * 操作者标识。
         */
        private String operatorId;

        /**
         * 关联对象类型。
         */
        private String referenceType;

        /**
         * 关联对象业务号。
         */
        private String referenceId;

        /**
         * 事件错误码。
         */
        private String errorCode;

        /**
         * 事件错误摘要。
         */
        private String errorMessage;

        /**
         * 事件发生时间。
         */
        private LocalDateTime eventTime;

        /**
         * 原交易分片时间。
         */
        private LocalDateTime transactionDateTime;

        /**
         * 原交易 UTC 时间。
         */
        private LocalDateTime transactionUtcTime;

        /**
         * 原交易 IANA 时区。
         */
        private String transactionTimeZone;

        /**
         * 记录创建时间。
         */
        private LocalDateTime createTime;
    }

    /**
     * 管理后台交易生命周期金额变动记录。
     *
     * <p>所有金额均使用交易金额币种解释；累计金额与可用金额保持持久化时的业务快照，
     * 不在查询阶段重新计算或换汇。</p>
     */
    @Data
    public static class TransactionAmountChangeResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 物理记录主键；历史补录记录没有物理主键。
         */
        private String id;

        /**
         * 金额变动业务号。
         */
        private String amountChangeId;

        /**
         * 平台交易号。
         */
        private String transactionId;

        /**
         * 触发金额变动的交易动作号。
         */
        private String operationId;

        /**
         * 被影响或引用的源交易号。
         */
        private String sourceTransactionId;

        /**
         * 被影响或引用的源动作号。
         */
        private String sourceOperationId;

        /**
         * 金额变动类型。
         */
        private String changeType;

        /**
         * 查询阶段补录记录的来源；持久化日志不返回该字段。
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String changeSource;

        /**
         * 金额币种。
         */
        private String amountCurrency;

        /**
         * 本次变动金额。
         */
        private BigDecimal changeAmount;

        /**
         * 变动前累计授权金额。
         */
        private BigDecimal authorizedBefore;

        /**
         * 变动后累计授权金额。
         */
        private BigDecimal authorizedAfter;

        /**
         * 变动前累计请款金额。
         */
        private BigDecimal capturedBefore;

        /**
         * 变动后累计请款金额。
         */
        private BigDecimal capturedAfter;

        /**
         * 变动前累计退款金额。
         */
        private BigDecimal refundedBefore;

        /**
         * 变动后累计退款金额。
         */
        private BigDecimal refundedAfter;

        /**
         * 变动前可请款金额。
         */
        private BigDecimal availableCaptureBefore;

        /**
         * 变动后可请款金额。
         */
        private BigDecimal availableCaptureAfter;

        /**
         * 变动前可退款金额。
         */
        private BigDecimal availableRefundBefore;

        /**
         * 变动后可退款金额。
         */
        private BigDecimal availableRefundAfter;

        /**
         * 金额变动原因。
         */
        private String changeReason;

        /**
         * 金额变动时间。
         */
        private LocalDateTime changeTime;

        /**
         * 原交易分片时间。
         */
        private LocalDateTime transactionDateTime;

        /**
         * 原交易 UTC 时间。
         */
        private LocalDateTime transactionUtcTime;

        /**
         * 原交易 IANA 时区。
         */
        private String transactionTimeZone;

        /**
         * 记录创建时间。
         */
        private LocalDateTime createTime;
    }

    /**
     * 管理后台渠道请求业务记录。
     *
     * <p>该模型只返回脱敏地址、渠道响应摘要和平台判断结果，不包含渠道请求或响应原文。</p>
     */
    @Data
    public static class ChannelRequestResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 物理记录主键；使用字符串返回，避免浏览器解析 BIGINT 时丢失精度。
         */
        private String id;

        /**
         * 平台渠道请求号。
         */
        private String requestId;

        /**
         * 平台交易号。
         */
        private String transactionId;

        /**
         * 交易动作号。
         */
        private String operationId;

        /**
         * 渠道信息主键；使用字符串返回避免浏览器精度损失。
         */
        private String channelId;

        /**
         * 渠道编码。
         */
        private String channelCode;

        /**
         * 渠道 MID 配置主键；使用字符串返回避免浏览器精度损失。
         */
        private String channelMidConfigId;

        /**
         * 交易类型。
         */
        private String transactionType;

        /**
         * 渠道请求场景。
         */
        private String requestScene;

        /**
         * 是否为渠道结果勾兑请求。
         */
        private Boolean channelMatchFlag;

        /**
         * 渠道请求状态。
         */
        private String requestStatus;

        /**
         * HTTP 请求方法。
         */
        private String httpMethod;

        /**
         * 已脱敏的渠道请求地址。
         */
        private String requestUrlMasked;

        /**
         * 上送渠道币种。
         */
        private String requestCurrency;

        /**
         * 上送渠道金额。
         */
        private BigDecimal requestAmount;

        /**
         * 渠道订单号。
         */
        private String channelOrderNo;

        /**
         * 渠道交易号。
         */
        private String channelTransactionId;

        /**
         * 渠道网关外层结果。
         */
        private String gatewayResult;

        /**
         * 渠道网关返回码。
         */
        private String gatewayCode;

        /**
         * 收单机构返回码。
         */
        private String acquirerCode;

        /**
         * 收单机构返回描述。
         */
        private String acquirerMessage;

        /**
         * 渠道原始交易状态。
         */
        private String channelStatus;

        /**
         * 平台是否将本次渠道结果判定为成功。
         */
        private Boolean platformSuccess;

        /**
         * 平台统一结果码。
         */
        private String platformResultCode;

        /**
         * 平台失败原因。
         */
        private String platformFailReason;

        /**
         * 渠道请求发起时间。
         */
        private LocalDateTime requestStartTime;

        /**
         * 渠道响应时间。
         */
        private LocalDateTime responseTime;

        /**
         * 渠道请求耗时，单位为毫秒。
         */
        private Integer durationMillis;

        /**
         * 原交易分片时间。
         */
        private LocalDateTime transactionDateTime;

        /**
         * 原交易 UTC 时间。
         */
        private LocalDateTime transactionUtcTime;

        /**
         * 原交易 IANA 时区。
         */
        private String transactionTimeZone;

        /**
         * 乐观锁版本号。
         */
        private Integer version;

        /**
         * 软删除标识。
         */
        private Boolean deleted;

        /**
         * 记录创建时间。
         */
        private LocalDateTime createTime;

        /**
         * 记录更新时间。
         */
        private LocalDateTime updateTime;
    }

    /**
     * 管理后台渠道请求、响应和异常交互日志。
     */
    @Data
    public static class ChannelInteractionLogResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 物理记录主键；使用字符串返回，避免浏览器解析 BIGINT 时丢失精度。
         */
        private String id;

        /**
         * 渠道交互日志业务号。
         */
        private String interactionLogId;

        /**
         * 平台渠道请求号。
         */
        private String requestId;

        /**
         * 平台交易号。
         */
        private String transactionId;

        /**
         * 交易动作号。
         */
        private String operationId;

        /**
         * 渠道编码。
         */
        private String channelCode;

        /**
         * 交互类型，例如 REQUEST、RESPONSE 或 EXCEPTION。
         */
        private String interactionType;

        /**
         * HTTP 请求方法。
         */
        private String httpMethod;

        /**
         * 已脱敏的渠道请求地址。
         */
        private String requestUrlMasked;

        /**
         * 渠道 HTTP 响应状态码。
         */
        private Integer httpStatus;

        /**
         * 已脱敏的请求头 JSON。
         */
        private String requestHeaderJsonMasked;

        /**
         * 已脱敏的请求体 JSON。
         */
        private String requestBodyJsonMasked;

        /**
         * 已脱敏的响应头 JSON。
         */
        private String responseHeaderJsonMasked;

        /**
         * 已脱敏的响应体 JSON。
         */
        private String responseBodyJsonMasked;

        /**
         * 请求异常类型。
         */
        private String exceptionType;

        /**
         * 请求异常摘要。
         */
        private String exceptionMessage;

        /**
         * 渠道交互耗时，单位为毫秒。
         */
        private Integer durationMillis;

        /**
         * 调用链追踪号。
         */
        private String traceId;

        /**
         * 渠道交互发生时间。
         */
        private LocalDateTime interactionTime;

        /**
         * 原交易分片时间。
         */
        private LocalDateTime transactionDateTime;

        /**
         * 原交易 UTC 时间。
         */
        private LocalDateTime transactionUtcTime;

        /**
         * 原交易 IANA 时区。
         */
        private String transactionTimeZone;

        /**
         * 日志创建时间。
         */
        private LocalDateTime createTime;

        /**
         * 交易类型。
         */
        private String transactionType;

        /**
         * 渠道请求状态。
         */
        private String requestStatus;

        /**
         * 网关处理结果。
         */
        private String gatewayResult;

        /**
         * 网关返回码。
         */
        private String gatewayCode;

        /**
         * 收单机构返回码。
         */
        private String acquirerCode;

        /**
         * 收单机构返回描述。
         */
        private String acquirerMessage;

        /**
         * 渠道原始交易状态。
         */
        private String channelTradeStatus;

        /**
         * 渠道订单号。
         */
        private String channelOrderNo;

        /**
         * 渠道交易号。
         */
        private String channelTransactionId;

        /**
         * 平台处理结果码。
         */
        private String platformResultCode;

        /**
         * 平台失败原因。
         */
        private String platformFailReason;

        /**
         * 管理端统一展示的渠道返回码。
         */
        private String channelResponseCode;

        /**
         * 管理端统一展示的渠道返回描述。
         */
        private String channelResponseMessage;
    }

    /**
     * 管理后台渠道回调处理记录。
     */
    @Data
    public static class ChannelCallbackResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 物理记录主键；使用字符串返回，避免浏览器解析 BIGINT 时丢失精度。
         */
        private String id;

        /**
         * 渠道回调业务号。
         */
        private String callbackId;

        /**
         * 渠道回调原文日志号。
         */
        private String callbackLogId;

        /**
         * 平台交易号。
         */
        private String transactionId;

        /**
         * 交易动作号。
         */
        private String operationId;

        /**
         * 渠道编码。
         */
        private String channelCode;

        /**
         * 渠道订单号。
         */
        private String channelOrderNo;

        /**
         * 渠道交易号。
         */
        private String channelTransactionId;

        /**
         * 平台识别的回调类型。
         */
        private String callbackType;

        /**
         * 渠道原始事件类型。
         */
        private String channelEventType;

        /**
         * 回调处理状态。
         */
        private String callbackStatus;

        /**
         * 回调幂等键。
         */
        private String idempotencyKey;

        /**
         * 渠道签名是否校验通过。
         */
        private Boolean signatureValid;

        /**
         * 渠道来源 IP 是否通过白名单校验。
         */
        private Boolean ipAllowed;

        /**
         * 从渠道报文解析的平台交易状态。
         */
        private String parsedTransactionStatus;

        /**
         * 状态推进前的平台交易状态。
         */
        private String previousTransactionStatus;

        /**
         * 状态推进目标值。
         */
        private String targetTransactionStatus;

        /**
         * 回调业务处理结果。
         */
        private String processResult;

        /**
         * 回调处理失败原因。
         */
        private String failReason;

        /**
         * 回调接收时间。
         */
        private LocalDateTime callbackReceivedTime;

        /**
         * 回调处理完成时间。
         */
        private LocalDateTime processedTime;

        /**
         * 原交易分片时间。
         */
        private LocalDateTime transactionDateTime;

        /**
         * 原交易 UTC 时间。
         */
        private LocalDateTime transactionUtcTime;

        /**
         * 原交易 IANA 时区。
         */
        private String transactionTimeZone;

        /**
         * 乐观锁版本号。
         */
        private Integer version;

        /**
         * 软删除标识。
         */
        private Boolean deleted;

        /**
         * 记录创建时间。
         */
        private LocalDateTime createTime;

        /**
         * 记录更新时间。
         */
        private LocalDateTime updateTime;
    }

    /**
     * 管理后台渠道回调原文日志。
     *
     * <p>仅承载已脱敏的回调请求头、请求体和平台响应摘要；验签与来源 IP 校验结果保持独立字段。</p>
     */
    @Data
    public static class ChannelCallbackLogResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 物理记录主键；使用字符串返回，避免浏览器解析 BIGINT 时丢失精度。
         */
        private String id;

        /**
         * 渠道回调日志业务号。
         */
        private String callbackLogId;

        /**
         * 平台交易号。
         */
        private String transactionId;

        /**
         * 交易动作号。
         */
        private String operationId;

        /**
         * 渠道编码。
         */
        private String channelCode;

        /**
         * 平台识别的回调类型。
         */
        private String callbackType;

        /**
         * 渠道订单号。
         */
        private String channelOrderNo;

        /**
         * 渠道交易号。
         */
        private String channelTransactionId;

        /**
         * 回调请求 URI。
         */
        private String requestUri;

        /**
         * HTTP 请求方法。
         */
        private String httpMethod;

        /**
         * 渠道回调来源 IP。
         */
        private String sourceIp;

        /**
         * 已脱敏的回调请求头 JSON。
         */
        private String requestHeaderJsonMasked;

        /**
         * 已脱敏的回调请求体 JSON。
         */
        private String requestBodyJsonMasked;

        /**
         * 渠道签名是否校验通过。
         */
        private Boolean signatureValid;

        /**
         * 渠道来源 IP 是否通过白名单校验。
         */
        private Boolean ipAllowed;

        /**
         * 平台返回给渠道的响应码。
         */
        private String platformResponseCode;

        /**
         * 平台返回给渠道的响应摘要。
         */
        private String platformResponseBody;

        /**
         * 回调接收时间。
         */
        private LocalDateTime callbackReceivedTime;

        /**
         * 原交易分片时间。
         */
        private LocalDateTime transactionDateTime;

        /**
         * 原交易 UTC 时间。
         */
        private LocalDateTime transactionUtcTime;

        /**
         * 原交易 IANA 时区。
         */
        private String transactionTimeZone;

        /**
         * 记录创建时间。
         */
        private LocalDateTime createTime;
    }

    /**
     * 管理后台商户通知任务。
     */
    @Data
    public static class MerchantNotificationResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 物理记录主键；使用字符串返回，避免浏览器解析 BIGINT 时丢失精度。
         */
        private String id;

        /**
         * 商户通知任务号。
         */
        private String notifyId;

        /**
         * 平台交易号。
         */
        private String transactionId;

        /**
         * 交易动作号。
         */
        private String operationId;

        /**
         * 平台商户号。
         */
        private String merchantId;

        /**
         * 商户订单号。
         */
        private String merchantOrderNo;

        /**
         * 通知业务类型。
         */
        private String notifyType;

        /**
         * 触发通知的交易事件类型。
         */
        private String eventType;

        /**
         * 通知任务状态。
         */
        private String notifyStatus;

        /**
         * 商户通知配置版本。
         */
        private String notifyConfigVersion;

        /**
         * 商户回调地址哈希。
         */
        private String targetUrlHash;

        /**
         * 已脱敏的商户回调地址。
         */
        private String targetUrlMasked;

        /**
         * 已脱敏的通知业务载荷。
         */
        private String payloadJsonMasked;

        /**
         * 商户通知签名方式。
         */
        private String signType;

        /**
         * 最近一次通知尝试序号。
         */
        private Integer lastAttemptNo;

        /**
         * 自动通知最大尝试次数。
         */
        private Integer maxRetryCount;

        /**
         * 下一次自动重试时间。
         */
        private LocalDateTime nextRetryTime;

        /**
         * 通知成功时间。
         */
        private LocalDateTime successTime;

        /**
         * 最近一次失败原因。
         */
        private String failReason;

        /**
         * 原交易分片时间。
         */
        private LocalDateTime transactionDateTime;

        /**
         * 原交易 UTC 时间。
         */
        private LocalDateTime transactionUtcTime;

        /**
         * 原交易 IANA 时区。
         */
        private String transactionTimeZone;

        /**
         * 乐观锁版本号。
         */
        private Integer version;

        /**
         * 软删除标识。
         */
        private Boolean deleted;

        /**
         * 任务创建时间。
         */
        private LocalDateTime createTime;

        /**
         * 任务更新时间。
         */
        private LocalDateTime updateTime;
    }

    /**
     * 管理后台商户通知单次投递日志。
     */
    @Data
    public static class MerchantNotificationDeliveryLogResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 物理记录主键；使用字符串返回，避免浏览器解析 BIGINT 时丢失精度。
         */
        private String id;

        /**
         * 商户通知投递日志号。
         */
        private String notifyLogId;

        /**
         * 商户通知任务号。
         */
        private String notifyId;

        /**
         * 人工重发事件号；自动投递时为空。
         */
        private String callbackEventId;

        /**
         * 投递模式，例如 AUTO 或 MANUAL。
         */
        private String deliveryMode;

        /**
         * 平台交易号。
         */
        private String transactionId;

        /**
         * 交易动作号。
         */
        private String operationId;

        /**
         * 平台商户号。
         */
        private String merchantId;

        /**
         * 通知尝试序号。
         */
        private Integer attemptNo;

        /**
         * 商户回调地址哈希。
         */
        private String targetUrlHash;

        /**
         * 商户 HTTP 响应状态码。
         */
        private Integer httpStatus;

        /**
         * 已脱敏的通知请求头 JSON。
         */
        private String requestHeaderJsonMasked;

        /**
         * 已脱敏的通知请求体 JSON。
         */
        private String requestBodyJsonMasked;

        /**
         * 已脱敏的商户响应体 JSON。
         */
        private String responseBodyJsonMasked;

        /**
         * 本次投递是否成功。
         */
        private Boolean success;

        /**
         * 本次投递错误摘要。
         */
        private String errorMessage;

        /**
         * 通知发起时间。
         */
        private LocalDateTime notifyTime;

        /**
         * 通知耗时，单位为毫秒。
         */
        private Integer durationMillis;

        /**
         * 原交易分片时间。
         */
        private LocalDateTime transactionDateTime;

        /**
         * 原交易 UTC 时间。
         */
        private LocalDateTime transactionUtcTime;

        /**
         * 原交易 IANA 时区。
         */
        private String transactionTimeZone;

        /**
         * 投递日志创建时间。
         */
        private LocalDateTime createTime;

        /**
         * 商户通知固定使用的 HTTP 方法。
         */
        private String httpMethod;

        /**
         * HTTP 响应完成时间；投递日志在响应完成后写入，因此与创建时间一致。
         */
        private LocalDateTime responseTime;
    }

    /**
     * 管理后台商户 OpenAPI 交互日志。
     *
     * <p>请求和响应只返回安全摘要、掩码与脱敏明文，不返回 JWT、完整密文或敏感支付资料。</p>
     */
    @Data
    public static class MerchantApiInteractionLogResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 物理记录主键；使用字符串返回，避免浏览器解析 BIGINT 时丢失精度。
         */
        private String id;

        /**
         * 商户 API 交互日志业务号。
         */
        private String apiLogId;

        /**
         * 商户本次请求唯一标识。
         */
        private String requestId;

        /**
         * 平台交易号。
         */
        private String transactionId;

        /**
         * 交易动作号。
         */
        private String operationId;

        /**
         * 平台商户号。
         */
        private String merchantId;

        /**
         * 商户订单号。
         */
        private String merchantOrderNo;

        /**
         * 商户请求号。
         */
        private String merchantOrderId;

        /**
         * 商户 API 交易动作。
         */
        private String apiOperation;

        /**
         * 商户请求路径。
         */
        private String requestPath;

        /**
         * 商户请求进入支付核心的时间。
         */
        private LocalDateTime requestTime;

        /**
         * 请求处理结果。
         */
        private String requestResult;

        /**
         * 商户请求密文安全摘要。
         */
        private String requestCipherDigest;

        /**
         * 商户请求密文掩码。
         */
        private String requestCipherMasked;

        /**
         * 商户请求脱敏明文 JSON。
         */
        private String requestPlainJsonMasked;

        /**
         * 平台响应生成时间。
         */
        private LocalDateTime responseTime;

        /**
         * 平台响应结果。
         */
        private String responseResult;

        /**
         * 商户侧可见响应码。
         */
        private String merchantResponseCode;

        /**
         * 商户侧可见响应描述。
         */
        private String merchantResponseMessage;

        /**
         * 平台返回商户的脱敏明文 JSON。
         */
        private String responsePlainJsonMasked;

        /**
         * 平台响应密文安全摘要。
         */
        private String responseCipherDigest;

        /**
         * 平台响应密文掩码。
         */
        private String responseCipherMasked;

        /**
         * OpenAPI 到支付核心处理耗时，单位为毫秒。
         */
        private Integer durationMillis;

        /**
         * 调用链追踪号。
         */
        private String traceId;

        /**
         * 原交易分片时间。
         */
        private LocalDateTime transactionDateTime;

        /**
         * 原交易 UTC 时间。
         */
        private LocalDateTime transactionUtcTime;

        /**
         * 原交易 IANA 时区。
         */
        private String transactionTimeZone;

        /**
         * 记录创建时间。
         */
        private LocalDateTime createTime;
    }

    /**
     * 商户通知任务及其投递尝试详情。
     */
    @Data
    public static class MerchantNotificationDetailResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 商户通知任务。
         */
        private MerchantNotificationResponse notification;

        /**
         * 按尝试序号升序排列的投递日志。
         */
        private List<MerchantNotificationDeliveryLogResponse> deliveryLogs = Collections.emptyList();
    }

    /**
     * 管理后台交易详情中的风控规则执行节点。
     *
     * <p>该模型只承载风控审计表已经脱敏的命中值，并保持阶段顺序、节点决策和最终决策相互独立，
     * 避免使用动态键值结构导致新增字段或数据库类型变化时静默改变对外响应。</p>
     */
    @Data
    public static class RiskTimelineEventResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 风控命中明细主键。
         */
        private Long riskEventId;

        /**
         * 风控评估记录号。
         */
        private String riskRecordNo;

        /**
         * 平台交易号。
         */
        private String transactionId;

        /**
         * 时间轴事件类型，当前固定为 RISK_RULE_EVALUATED。
         */
        private String eventType;

        /**
         * 时间轴事件阶段，当前固定为 RISK。
         */
        private String eventStage;

        /**
         * 风控功能或阶段展示名称。
         */
        private String eventName;

        /**
         * 管理端统一事件状态：SUCCESS、PENDING、FAILED 或 SKIPPED。
         */
        private String eventStatus;

        /**
         * 风控节点决策说明；不得包含未脱敏命中原文。
         */
        private String eventContent;

        /**
         * 风控节点完成时间。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime eventTime;

        /**
         * 交易时间轴排序序号。
         */
        private Integer timelineSequence;

        /**
         * 风控阶段编码。
         */
        private String stageCode;

        /**
         * 风控阶段名称。
         */
        private String stageName;

        /**
         * 风控阶段原始顺序。
         */
        private Integer stageOrder;

        /**
         * 风控模块类型。
         */
        private String moduleType;

        /**
         * 风控功能编码。
         */
        private String functionCode;

        /**
         * 风控功能名称。
         */
        private String functionName;

        /**
         * 命中的规则主键。
         */
        private Long ruleId;

        /**
         * 参与规则判断的元素名称。
         */
        private String hitElement;

        /**
         * 已脱敏的规则命中值。
         */
        private String hitValueMasked;

        /**
         * 规则匹配结果。
         */
        private String matchResult;

        /**
         * 当前节点决策结果。
         */
        private String decisionResult;

        /**
         * 当前节点对整体决策的影响。
         */
        private String decisionEffect;

        /**
         * 当前节点稳定原因码。
         */
        private String reasonCode;

        /**
         * 整笔风控评估的最终决策。
         */
        private String finalDecision;

        /**
         * 整笔风控评估的最终原因。
         */
        private String finalReason;
    }

    /**
     * 交易聚合详情响应。
     */
    @Data
    public static class TransactionDetailResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        private TransactionOrderResponse order;

        /**
         * 持卡人账单信息；当前交易未保存时为空。
         */
        private TransactionContactInfoResponse billingCardHolderInfo;

        /**
         * 付款人信息；当前交易未保存时为空。
         */
        private TransactionPayerInfoResponse payerInfo;

        /**
         * 收货信息；当前交易未保存时为空。
         */
        private TransactionContactInfoResponse shippingInfo;

        private List<TransactionOperationResponse> operations = Collections.emptyList();

        /**
         * 交易状态流转历史，按交易时间和物理主键升序排列。
         */
        private List<TransactionStatusHistoryResponse> statusHistory = Collections.emptyList();

        /**
         * 交易流程事件，按交易时间和物理主键升序排列。
         */
        private List<TransactionFlowEventResponse> flowEvents = Collections.emptyList();

        /**
         * 风控服务异步审计的实际执行节点，按规则优先级排序。
         */
        private List<RiskTimelineEventResponse> riskEvents = Collections.emptyList();

        /**
         * 生命周期金额变动记录；历史首笔成功动作缺少日志时会补充一条只读展示记录。
         */
        private List<TransactionAmountChangeResponse> amountChanges = Collections.emptyList();

        /**
         * 渠道请求业务记录。
         */
        private List<ChannelRequestResponse> channelRequests = Collections.emptyList();

        /**
         * 已脱敏的渠道请求、响应和异常交互日志。
         */
        private List<ChannelInteractionLogResponse> channelInteractionLogs = Collections.emptyList();

        /**
         * 渠道回调业务处理记录。
         */
        private List<ChannelCallbackResponse> channelCallbacks = Collections.emptyList();

        /**
         * 已脱敏的渠道回调原文日志。
         */
        private List<ChannelCallbackLogResponse> channelCallbackLogs = Collections.emptyList();

        /**
         * 商户通知任务。
         */
        private List<MerchantNotificationResponse> merchantNotifications = Collections.emptyList();

        /**
         * 已脱敏的商户通知投递日志。
         */
        private List<MerchantNotificationDeliveryLogResponse> merchantNotificationLogs = Collections.emptyList();

        /**
         * 已脱敏的商户 OpenAPI 请求和响应日志。
         */
        private List<MerchantApiInteractionLogResponse> merchantApiInteractionLogs = Collections.emptyList();
    }
}
