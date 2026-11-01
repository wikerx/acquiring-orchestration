package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 交易支付工具摘要实体，位于 service-payment 持久化层，只保存支付方式、卡品牌、BIN、尾号、脱敏卡号和哈希等合规排查字段，不保存 CVV 或完整 PAN。
 */
@Data
@TableName("transaction_payment_method_info")
public class TransactionPaymentMethodInfoDO implements Serializable {

    private static final long serialVersionUID = 1L;


    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 支付工具信息编号，用于关联交易支付工具摘要与对应交易主单、动作单和支付方式记录。
     */
    private String paymentInfoId;

    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String transactionId;

    /**
     * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
     */
    private String operationId;


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
     * 银行卡尾号，用于交易查询、客服核验和持卡人侧展示。
     */
    private String cardLast4;

    /**
     * 卡编号脱敏，表示银行卡号或脱敏卡号字段。
     */
    private String cardNumberMasked;


    private String cardholderNameMasked;

    /**
     * 银行卡有效期月份，用于渠道授权请求和卡片有效性校验，不应单独作为持卡人认证凭据。
     */
    private String expiryMonth;

    /**
     * 银行卡有效期年份，用于渠道授权请求和卡片有效性校验，不应单独作为持卡人认证凭据。
     */
    private String expiryYear;

    /**
     * 支付令牌编号，用于关联渠道或钱包返回的 token 化支付凭据，不保存原始卡号。
     */
    private String tokenId;

    /**
     * {@code walletType}，用于区分 {@code TransactionPaymentMethodInfoDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String walletType;


    private String paymentAccountHash;


    private String issuerCountry;


    private String fundingMethod;


    private String threeDsIndicator;

    /**
     * {@code cscResult}，表示渠道返回的安全码或地址校验结果，用于交易风险判断和排障展示。
     */
    private String cscResult;

    /**
     * {@code avsResult}，表示渠道返回的安全码或地址校验结果，用于交易风险判断和排障展示。
     */
    private String avsResult;

    /**
     * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 交易受理时刻对应的 UTC 时间，用于跨时区排序和对账。
     */
    private LocalDateTime transactionUtcTime;

    /**
     * 交易业务时区，使用 IANA 时区标识解释本地交易时间。
     */
    private String transactionTimeZone;

    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createTime;

    /**
     * 记录最后更新时间，持久化精度为毫秒。
     */
    private LocalDateTime updateTime;
}
