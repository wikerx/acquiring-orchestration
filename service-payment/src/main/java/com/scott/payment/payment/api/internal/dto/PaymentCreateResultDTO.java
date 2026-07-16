package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateResultDTO
 * @date : 2026-05-31 21:01
 * @email : scott_x@163.com
 * @description : service-payment 创建收单交易的内部响应参数，返回交易受理后的商户可见结果摘要和内部生命周期关联 ID；operationId 仅内部链路使用，不暴露给商户。
 * @status : create
 */
@Data
public class PaymentCreateResultDTO implements Serializable {

    /**
     * 序列化版本号，用于服务间 JSON 传输兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 平台当前交易唯一标识，对应 transaction_operation.transaction_id；每一笔授权、请款、退款、撤销都不同。
     */
    private String transactionId;

    /**
     * 原平台交易唯一标识，后续动作返回商户请求传入的 sourceTransactionId。
     */
    private String sourceTransactionId;

    /**
     * 平台内部生命周期关联标识，对应 transaction_order.operation_id；不返回商户、不直接上送渠道。
     */
    private String operationId;

    /**
     * 商户订单号，原样返回给 OpenAPI 和商户。
     */
    private String merchantOrderNo;

    /**
     * 商户本次 API 请求唯一标识，来自 orderInfo.orderId。
     */
    private String merchantOrderId;

    /**
     * 支付平台颁发的商户号。
     */
    private String merchantId;

    /**
     * 商户侧子商户信息，用于 OpenAPI 响应回显允许展示的子商户摘要。
     */
    private SubMerchantInfoDTO subMerchantInfo;

    /**
     * 交易类型，对齐字典 transaction_type。
     */
    private String transactionType;

    /**
     * 交易状态，对齐字典 transaction_status。
     */
    private String status;

    /**
     * 当前动作商户响应码，例如 T200、T202、T203、F210。
     */
    private String merchantResponseCode;

    /**
     * 当前动作商户响应描述，面向商户展示。
     */
    private String merchantResponseMessage;

    /**
     * 内部处理阶段，用于说明当前交易处于风控、路由、渠道请求或等待回调等节点。
     */
    private String processStage;

    /**
     * 失败原因码，仅当 status=FAILED 时返回。
     */
    private String failReasonCode;

    /**
     * 商户可见失败原因描述，避免暴露过细渠道规则。
     */
    private String failReasonMessage;

    /**
     * 挂起原因码，仅当 status=PENDING 时返回。
     */
    private String pendingReasonCode;

    /**
     * 交易金额，单位为最小币种单位，例如 USD 分；兼容旧调用方，新接入优先读取 transactionAmount。
     */
    private Long amount;

    /**
     * 交易币种，使用 ISO 4217 三位大写币种代码；兼容旧调用方，新接入优先读取 transactionCurrency。
     */
    private String currency;

    /**
     * 商户上送订单金额，主币种单位。
     */
    private BigDecimal orderAmount;

    /**
     * 商户上送订单币种。
     */
    private String orderCurrency;

    /**
     * 当前生命周期累计授权成功金额，平台交易币种单位。
     */
    private BigDecimal totalAuthorizedAmount;

    /**
     * 当前生命周期累计请款成功金额，平台交易币种单位。
     */
    private BigDecimal totalCapturedAmount;

    /**
     * 当前生命周期累计退款成功金额，平台交易币种单位。
     */
    private BigDecimal totalRefundAmount;

    /**
     * 当前生命周期累计撤销金额，平台交易币种单位。
     */
    private BigDecimal totalVoidAmount;

    /**
     * 当前生命周期累计拒付金额，平台交易币种单位。
     */
    private BigDecimal totalChargebackAmount;

    /**
     * 商户上送或页面标签展示的原始金额，主币种单位。
     */
    private BigDecimal labelAmount;

    /**
     * 商户上送或页面标签展示的原始币种。
     */
    private String labelCurrency;

    /**
     * 平台上送渠道的交易金额，主币种单位。
     */
    private BigDecimal transactionAmount;

    /**
     * 平台上送渠道的交易币种。
     */
    private String transactionCurrency;

    /**
     * 标签金额转平台交易金额使用的汇率。
     */
    private BigDecimal transactionRate;

    /**
     * 汇率来源编码。
     */
    private String rateSource;

    /**
     * 汇率生效或报价时间。
     */
    private LocalDateTime rateTime;

    /**
     * 预计或最终结算金额。
     */
    private BigDecimal settlementAmount;

    /**
     * 预计或最终结算币种。
     */
    private String settlementCurrency;

    /**
     * 交易发生时间。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 交易发生时区。
     */
    private String transactionTimeZone;

    /**
     * 支付方式，如 BANK_CARD。
     */
    private String paymentMethod;

    /**
     * 卡品牌或支付品牌，如 MASTERCARD、VISA。
     */
    private String paymentBrand;

    /**
     * 脱敏卡 BIN，格式为前六位 + **** + 后四位。
     */
    private String cardBin;

    /**
     * 授权码，渠道成功返回时填写。
     */
    private String authCode;

    /**
     * ARN 或收单机构参考号。
     */
    private String acquirerReferenceNo;

    /**
     * 订单备注或描述，商户上送后原样返回。
     */
    private String description;

    /**
     * 商户通知回调地址，商户上送或配置存在时返回。
     */
    private String callbackUrl;

    @Data
    public static class SubMerchantInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String subName;

        private String subCompanyName;

        private String subId;

        private String subStreet;

        private String subCity;

        private String subState;

        private String subCountryCode;

        private String merchantCategory;

        private String intesCode;

        private String chargeType;
    }

}
