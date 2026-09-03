package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutAttemptDO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Hosted Checkout 支付尝试实体。
 * @status : create
 */
@Data
@TableName("payment_checkout_attempt")
public class PaymentCheckoutAttemptDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 平台 Hosted Checkout 支付尝试号。 */
    private String checkoutAttemptId;
    /** 支付尝试所属会话号。 */
    private String checkoutSessionId;
    /** 支付尝试所属商户号。 */
    private String merchantId;
    /** 关联商户订单号。 */
    private String merchantOrderNo;
    /** 当前会话内从 1 开始递增的尝试序号。 */
    private Integer attemptNo;
    /** 付款端尝试请求号，与会话号共同用于数据库幂等。 */
    private String attemptRequestId;
    /** 不包含原始卡数据的请求指纹。 */
    private String requestFingerprint;
    /** 支付尝试状态；终态不得被普通更新覆盖。 */
    private String attemptStatus;
    /** 当前内部处理阶段。 */
    private String processStage;
    /** 付款人选择的支付方式。 */
    private String paymentMethod;
    /** 实际使用的卡品牌或支付品牌。 */
    private String paymentBrand;
    /** 付款页展示金额的 ISO 4217 币种。 */
    private String labelCurrency;
    /** 付款页展示金额，单位为 {@link #labelCurrency} 的主币种单位。 */
    private BigDecimal labelAmount;
    /** 向渠道请求的 ISO 4217 币种。 */
    private String channelRequestCurrency;
    /** 向渠道请求的金额，单位为 {@link #channelRequestCurrency} 的主币种单位。 */
    private BigDecimal channelRequestAmount;
    /** 关联支付动作号。 */
    private String operationId;
    /** 关联平台交易号。 */
    private String transactionId;
    /** 交易业务时间，用于分片交易定位。 */
    private LocalDateTime transactionDateTime;
    /** 命中的渠道编码。 */
    private String channelCode;
    /** 命中的渠道商户配置主键。 */
    private Long channelMidConfigId;
    /** 渠道订单号。 */
    private String channelOrderNo;
    /** 渠道交易号。 */
    private String channelTransactionId;
    /** 渠道请求号，用于回调和查询关联。 */
    private String channelRequestId;
    /** 归一化或原始渠道处理状态。 */
    private String channelStatus;
    /** 渠道响应码。 */
    private String channelResponseCode;
    /** 已脱敏和截断的渠道响应说明。 */
    private String channelResponseMessage;
    /** 脱敏卡 BIN 摘要，不得存储完整 PAN。 */
    private String cardBin;
    /** 卡号末四位；不得与其他字段组合还原完整 PAN。 */
    private String cardLast4;
    /** 符合 PCI 显示规则的脱敏卡号。 */
    private String cardNumberMasked;
    /** 已脱敏的持卡人姓名。 */
    private String cardholderNameMasked;
    /** 支付账户不可逆摘要，用于关联而不保存账号明文。 */
    private String paymentAccountHash;
    /** 是否要求 3DS：0 否，1 是。 */
    private Integer threeDsRequired;
    /** 当前 3DS 认证状态。 */
    private String threeDsStatus;
    /** 3DS 协议版本。 */
    private String threeDsVersion;
    /** 平台或渠道侧统一 3DS 交易标识。 */
    private String threeDsTransactionId;
    /** 3DS Server 交易标识。 */
    private String threeDsServerTransactionId;
    /** ACS 交易标识。 */
    private String acsTransactionId;
    /** Directory Server 交易标识。 */
    private String dsTransactionId;
    /** 电子商务指示码。 */
    private String eci;
    /** 是否发生责任转移：0 否，1 是。 */
    private Integer liabilityShift;
    /** 一次性 3DS 回跳令牌摘要，绝不保存令牌明文。 */
    private String threeDsReturnTokenHash;
    /** 3DS 跳转地址摘要，不保存带敏感参数的完整地址。 */
    private String authenticationRedirectUrlHash;
    /** 3DS 所需浏览器环境 JSON，字段和长度必须受控。 */
    private String browserInfoJson;
    /** 已摘要化的设备环境 JSON。 */
    private String deviceInfoJson;
    /** 归一化失败原因码。 */
    private String failureReasonCode;
    /** 已脱敏的内部失败说明。 */
    private String failureReasonMessage;
    /** 面向付款人的安全失败提示，不包含渠道敏感原文。 */
    private String payerVisibleMessage;
    /** 付款人提交本次尝试的时间。 */
    private LocalDateTime submitTime;
    /** 开始 3DS 认证的时间。 */
    private LocalDateTime authenticationStartTime;
    /** 3DS 认证完成时间。 */
    private LocalDateTime authenticationCompleteTime;
    /** 向支付渠道提交交易的时间。 */
    private LocalDateTime channelSubmitTime;
    /** 本次尝试进入终态的时间。 */
    private LocalDateTime completeTime;
    /** 已脱敏的支付结果快照，不得包含 PAN、CVV、CAVV 或令牌。 */
    private String resultSnapshot;
    /** 乐观锁版本号。 */
    private Integer version;
    /** 逻辑删除标识。 */
    private Integer deleted;
    /** 数据库记录创建时间。 */
    private LocalDateTime createTime;
    /** 数据库记录最近更新时间。 */
    private LocalDateTime updateTime;
}
