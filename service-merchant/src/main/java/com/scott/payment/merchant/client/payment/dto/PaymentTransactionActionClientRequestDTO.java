package com.scott.payment.merchant.client.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionActionClientRequestDTO
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户后台调用 service-payment 发起交易后续动作的内部客户端请求，位于 service-merchant 客户端层，仅传递支付核心所需的退款动作上下文。
 * @status : create
 */
@Data
public class PaymentTransactionActionClientRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前登录商户号，由服务端上下文补齐。
     */
    private String merchantId;

    /**
     * 原商户订单号，用于保持交易生命周期归属。
     */
    private String merchantOrderNo;

    /**
     * 本次商户后台动作唯一请求号，用于支付核心幂等保护。
     */
    private String merchantOrderId;

    /**
     * 交易类型，由 service-payment 内部入口按动作类型强制设置。
     */
    private String transactionType;

    /**
     * 动作金额，主币种单位。
     */
    private BigDecimal amount;

    /**
     * 页面标签币种金额，供支付核心保留商户展示口径。
     */
    private BigDecimal labelAmount;

    /**
     * 页面标签币种，供支付核心保留商户展示口径。
     */
    private String labelCurrency;

    /**
     * 动作币种，ISO 4217 三位代码。
     */
    private String currency;

    /**
     * 动作请求时间，对应交易分表时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime transactionDateTime;

    /**
     * 请求追踪号，当前与 merchantOrderId 保持一致。
     */
    private String requestId;

    /** 内部动作来源，商户后台固定为 MERCHANT_PORTAL。 */
    private String requestSource;

    /** 当前认证商户账号 ID。 */
    private String applicantId;

    /** 当前认证商户账号显示名快照。 */
    private String applicantName;

    /** 商户填写的退款或撤销原因。 */
    private String requestReason;

    /**
     * 交易扩展信息。
     */
    private TransactionInfoDTO transactionInfo;

    /**
     * 交易扩展信息。
     */
    @Data
    public static class TransactionInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 原平台交易 ID，支付核心据此定位原交易。
         */
        private String sourceTransactionId;

        /** 源动作真实分片时间。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime sourceTransactionDateTime;

        /** 生命周期根主单真实分片时间。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime rootTransactionDateTime;

        /**
         * 商户后台操作说明，进入交易描述和审计上下文。
         */
        private String description;
    }
}
