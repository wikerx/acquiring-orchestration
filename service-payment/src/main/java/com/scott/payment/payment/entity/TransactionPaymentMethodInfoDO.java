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

    /**
     * {@code TransactionPaymentMethodInfoDO} 数据库主键，用于唯一标识当前记录。
     */
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

    /**
     * 支付方式，表示支付方式、通知方式或调用方式。
     */
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

    /**
     * {@code cardholderNameMasked}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
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
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String walletType;

    /**
     * {@code paymentAccountHash}，用于以不可逆摘要关联敏感原文或大报文。
     */
    private String paymentAccountHash;

    /**
     * {@code issuerCountry}，表示国家或地区代码，用于路由、风控、卡 BIN 识别或地域限制。
     */
    private String issuerCountry;

    /**
     * {@code fundingMethod}，表示支付方式、通知方式或调用方式。
     */
    private String fundingMethod;

    /**
     * {@code threeDsIndicator}，表示 3DS 认证状态、版本或结果标识，用于渠道授权和风控判断。
     */
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
